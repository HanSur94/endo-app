// Requirement: CHART-01 / CHART-02 / CHART-04 — the Phase 13 deterministic seam-driven scrub E2E.
//
// Clones the `InsightsDashboardUITests` harness. Each test is ONE launch with
// `["-uitest-reset", "-uitest-seed-scrub", "-uitest-select-day", <yyyy-MM-dd>]`: the DEBUG-only,
// `-uitest-reset`-paired seam (AppDependencies → `\.uiTestScrubDay` → InsightTrendChart `.onAppear`)
// sets a deterministic selection so the chrome renders with ZERO gestures, ZERO coordinates, ZERO
// sleeps (RESEARCH Pitfall 4). All assertions ride a11y ids + `element.value`:
//   • `…chart.tooltip`           — the ONE quiet tooltip element; its accessibilityValue carries the
//                                  full "{date} — Severity {n} of 10" / "{date} — Not logged" readout
//                                  (CHART-04's non-gesture readout, asserted via `value`).
//   • `…chart.tooltip.notLogged` — the explicit gap-state marker (CHART-02: a gap is information,
//                                  never a value; its ABSENCE on a severity-0 day proves absence ≠ 0).
//
// The `-uitest-seed-scrub` fixture (dense severity-6 pelvicPain spine ending yesterday): offset 3 is
// SKIPPED (the in-window gap day), offset 2 is severity 0 (the logged-zero day), offset 1 is severity 6
// (the logged assertion day) — all three inside the last-7 window so every window-card resolves the
// SAME state and `firstMatch` is unambiguous (RESEARCH Pitfall 6).
//
// Deliberately NO real-gesture test (Claude's-discretion call per the plan): RESEARCH allows "at most
// one" smoke gesture test, but the tooltip's live-while-touching/dismiss-on-release lifecycle cannot
// be asserted deterministically (nil-on-release timing is undocumented — RESEARCH Assumption A1; a
// mid-press assertion is exactly the flake Pitfall 4 forbids). Lifecycle is covered by the locked
// phase-gate manual checkpoint (13-VALIDATION Manual-Only rows). A seam-set selection has no gesture
// to end, so the tooltip persists — these tests assert CONTENT, never lifecycle.
import XCTest

@MainActor
final class ChartScrubUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Helpers (cloned from InsightsDashboardUITests)

    /// Launch, grant consent, and open the Insights tab. Returns the launched app on the dashboard.
    @discardableResult
    private func launchAndOpenInsights(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        // Grant consent (the dashboard is built ONLY in the consented branch — T-08-12).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        // Confirm we landed on the consented shell, then switch to the Insights tab.
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        // The tab-bar button carries the `tab.insights` id (set on the tab-item Label). Query the tab bar
        // first, then any descendant, so the lookup is robust across XCUITest's tab-bar element model.
        var insightsTab = app.tabBars.buttons["tab.insights"]
        if !insightsTab.waitForExistence(timeout: 10) {
            insightsTab = app.descendants(matching: .any)["tab.insights"].firstMatch
        }
        XCTAssertTrue(insightsTab.waitForExistence(timeout: 10),
                      "A top-level Insights tab (tab.insights) must exist in the consented shell.")
        insightsTab.tap()

        XCTAssertTrue(app.otherElements["insights.screen"].waitForExistence(timeout: 10)
                      || app.collectionViews["insights.screen"].waitForExistence(timeout: 2)
                      || app.descendants(matching: .any)["insights.screen"].waitForExistence(timeout: 2),
                      "The Insights dashboard (insights.screen) must appear after tapping the tab.")
        return app
    }

    /// The `yyyy-MM-dd` launch-arg string for `today - daysAgo`, computed IMMEDIATELY before
    /// `app.launch()` (same midnight-race class as every existing today-relative seed — accepted).
    private func dayArg(daysAgo: Int) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let day = Calendar.current.date(byAdding: .day, value: -daysAgo,
                                        to: Calendar.current.startOfDay(for: Date()))!
        return formatter.string(from: day)
    }

    /// The SEVERITY trend card's selection tooltip. The tooltip id is a SUB-element id
    /// (`insights.card.symptom.pelvicPain.severity.chart.tooltip`), findable via an ENDSWITH
    /// predicate + `firstMatch` (the same id-bleed-robust pattern `firstInsightCard` uses in
    /// InsightsDashboardUITests). Scoped to `.severity.chart.tooltip` — NOT a generic
    /// `.chart.tooltip` — because the seed ALSO emits a pelvicPain FREQUENCY trend card whose
    /// values are 0/1 (its tooltip reads "Severity 1 of 10" on every logged day), which would make
    /// a generic `firstMatch` assert against the wrong chart.
    private func severityTooltip(in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier ENDSWITH '.severity.chart.tooltip'")
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// The severity card's explicit gap-state marker (`….severity.chart.tooltip.notLogged`).
    private func severityNotLoggedMarker(in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier ENDSWITH '.severity.chart.tooltip.notLogged'")
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// Wait for the severity card's tooltip, scrolling the List (plain vertical swipes — vertical
    /// scrolling keeps priority over the chart by framework arbitration, and this is the SAME
    /// realize-lazy-rows scroll the dashboard suite uses) in case the severity card sits below the
    /// fold. Returns whether the tooltip exists.
    private func revealSeverityTooltip(in app: XCUIApplication) -> Bool {
        let tip = severityTooltip(in: app)
        var revealed = tip.waitForExistence(timeout: 15)
        var scrollAttempts = 0
        while !revealed && scrollAttempts < 4 {
            app.swipeUp()
            revealed = tip.waitForExistence(timeout: 3)
            scrollAttempts += 1
        }
        return revealed
    }

    // MARK: - 1. Logged day → severity tooltip (CHART-01 + the CHART-04 a11y readout)

    func testScrub_loggedDay_showsSeverityTooltip() throws {
        // today-1: the seeded severity-6 logged day.
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-scrub",
                                         "-uitest-select-day", dayArg(daysAgo: 1)])

        XCTAssertTrue(revealSeverityTooltip(in: app),
                      "The seam-selected logged day must render the selection tooltip "
                      + "(….severity.chart.tooltip).")
        // CHART-04: the tooltip is ONE quiet a11y element whose accessibilityValue carries the FULL
        // readout — "{date} — Severity 6 of 10". XCUIElement.value surfaces accessibilityValue.
        let readout = severityTooltip(in: app).value as? String ?? ""
        XCTAssertTrue(readout.contains("Severity 6 of 10"),
                      "A logged severity-6 day must read 'Severity 6 of 10' (got: '\(readout)').")
        // CHART-02 inverse: a LOGGED day must never carry the gap marker.
        XCTAssertFalse(severityNotLoggedMarker(in: app).exists,
                       "A logged day must NOT show the .notLogged gap marker.")
    }

    // MARK: - 2. Gap day → explicit "Not logged" (CHART-02: the gap is information, never a value)

    func testScrub_gapDay_showsNotLogged() throws {
        // today-3: the seeded SKIPPED day — the in-window gap.
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-scrub",
                                         "-uitest-select-day", dayArg(daysAgo: 3)])

        XCTAssertTrue(revealSeverityTooltip(in: app),
                      "The gap-day tooltip element must render (the gap is shown, never skipped).")
        XCTAssertTrue(severityNotLoggedMarker(in: app).waitForExistence(timeout: 10),
                      "The seam-selected gap day must render the explicit .notLogged marker — the gap "
                      + "is shown as information, NEVER a fabricated/interpolated value (CHART-02).")
        // The tooltip's readout says "Not logged" (never a number).
        let readout = severityTooltip(in: app).value as? String ?? ""
        XCTAssertTrue(readout.contains("Not logged"),
                      "A gap day's readout must say 'Not logged' (got: '\(readout)').")
        XCTAssertFalse(readout.contains("Severity"),
                       "A gap day must never read a severity value (got: '\(readout)').")
    }

    // MARK: - 3. Severity-0 day → "Severity 0 of 10", NOT "Not logged" (CHART-02: absence ≠ 0)

    func testScrub_severityZeroDay_readsZeroNotNotLogged() throws {
        // today-2: the seeded LOGGED severity-0 day.
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-scrub",
                                         "-uitest-select-day", dayArg(daysAgo: 2)])

        XCTAssertTrue(revealSeverityTooltip(in: app),
                      "The seam-selected severity-0 day must render the selection tooltip.")
        let readout = severityTooltip(in: app).value as? String ?? ""
        XCTAssertTrue(readout.contains("Severity 0 of 10"),
                      "A LOGGED severity-0 day must read 'Severity 0 of 10' — a real zero, visually and "
                      + "textually distinct from a gap (got: '\(readout)').")
        // The honesty E2E: zero is a VALUE; the gap marker must be ABSENT (absence ≠ 0, end-to-end).
        XCTAssertFalse(severityNotLoggedMarker(in: app).exists,
                       "A logged severity-0 day must NOT show the .notLogged gap marker (absence ≠ 0).")
    }
}
