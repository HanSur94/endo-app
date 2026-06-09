// Requirement: INST-01..04 / D-05 / D-07 / D-09 / D-10 — the Wave 4 dashboard E2E.
//
// Clones the `LoggingFlowUITests` harness (PATTERNS entry 14): the `-uitest-reset` launch arg + the
// `-uitest-seed-insights` seed (08-02) + `waitForExistence`/`waitForNonExistence` + a `saveScreenshot`
// helper writing `/tmp/e2e/<name>.png` (for the human-verify chart-quality checkpoint, D-16).
//
// Four cases mapping the 08-VALIDATION dashboard-E2E layer + the MANDATORY honesty acceptance criteria:
//   1. seededData_rendersInsight       — seed → open Insights → ≥1 `insights.card.*` + a screenshot.
//   2. noData_showsEmptyState          — no seed → open Insights → `insights.empty`, no card.
//   3. suppressed_noCyclePhaseSection  — seed (pelvicPain trend only, no cycle data) → the engine emits
//                                        ZERO `.cyclePhase`, so `insights.section.cyclePhase` does NOT
//                                        exist — proven by ABSENCE (mirrors home.cycleSummary.suppressed).
//   4. dismiss_removesCard_andStaysGone — dismiss one card → it disappears → relaunch with the same seed
//                                        → the dismissed card does NOT reappear (D-09 stays-gone +
//                                        D-10 recompute-excludes).
//
// Deterministic a11y ids ONLY — never copy-string matching (the a11y ids are the stable contract).
//
// A11y identifiers this E2E drives (added by InsightsView / AppGate / InsightCardView in 08-04 + 08-03):
//   tab.insights, insights.screen, insights.disclaimer, insights.empty,
//   insights.section.trends, insights.section.cyclePhase, insights.section.correlations,
//   insights.card.<signalKey>, insights.card.<signalKey>.dismiss.
import XCTest

@MainActor
final class InsightsDashboardUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Helpers

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

    /// The first rendered insight card. A SwiftUI `Section`'s `.accessibilityIdentifier` bleeds onto its
    /// descendant cells, masking the card-ROOT `insights.card.<signalKey>` id — so we detect a rendered
    /// card by its always-present `.confidence` sub-element (a full card) or `.insufficient` (a sparse
    /// card), both of which carry the stable `insights.card.<signalKey>.<suffix>` id verbatim.
    private func firstInsightCard(in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier BEGINSWITH 'insights.card.' AND (identifier ENDSWITH '.confidence' OR identifier ENDSWITH '.insufficient')")
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// Count of rendered insight cards (one `.confidence`/`.insufficient` marker per card).
    private func insightCardCount(in app: XCUIApplication) -> Int {
        let predicate = NSPredicate(format: "identifier BEGINSWITH 'insights.card.' AND (identifier ENDSWITH '.confidence' OR identifier ENDSWITH '.insufficient')")
        return app.descendants(matching: .any).matching(predicate).count
    }

    /// The dismissable swipe row for a card, addressed by the card's signalKey ROOT (derived from a
    /// `.confidence`/`.insufficient` sub-element id by stripping the suffix).
    private func cardSignalRoot(_ subElementID: String) -> String {
        if subElementID.hasSuffix(".confidence") { return String(subElementID.dropLast(".confidence".count)) }
        if subElementID.hasSuffix(".insufficient") { return String(subElementID.dropLast(".insufficient".count)) }
        return subElementID
    }

    // MARK: - 1. Seeded data renders ≥1 insight card

    func testSeededData_rendersInsight() throws {
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-insights"])

        // The seed logs 21 consecutive pelvicPain days (well past minSample(.trend) == 3), so the engine
        // emits ≥1 `.trend` card in the Trends section.
        let card = firstInsightCard(in: app)
        XCTAssertTrue(card.waitForExistence(timeout: 15),
                      "Seeded threshold-crossing data must render at least one insight card.")
        XCTAssertTrue(app.descendants(matching: .any)["insights.section.trends"].exists,
                      "Seeded trend data must render the Trends section.")
        // The persistent disclaimer is always visible with the insights (D-12).
        XCTAssertTrue(app.descendants(matching: .any)["insights.disclaimer"].exists,
                      "The not-medical-advice disclaimer must always be visible on the dashboard.")
        // Save the dashboard for the human-verify chart-quality checkpoint (D-16).
        saveScreenshot(app, named: "insights-01-seeded-dashboard")
    }

    // MARK: - 2. No data shows the empty state

    func testNoData_showsEmptyState() throws {
        let app = launchAndOpenInsights(["-uitest-reset"])   // NO seed.

        XCTAssertTrue(app.descendants(matching: .any)["insights.empty"].waitForExistence(timeout: 15),
                      "With no logged data the dashboard must show the warm empty state (insights.empty).")
        XCTAssertEqual(insightCardCount(in: app), 0,
                       "The empty state must render NO insight cards (no fake/placeholder cards).")
        saveScreenshot(app, named: "insights-02-empty")
    }

    // MARK: - 3. Suppressed → NO cycle-phase section (proven by ABSENCE)

    func testSuppressed_noCyclePhaseSection() throws {
        // The seed logs ONLY pelvicPain severity with NO bleeding/cycle data, so the engine derives no
        // cycle-phase windows → it emits ZERO `.cyclePhase` insights. The cycle-phase section is therefore
        // ABSENT (omitted, not blank) — the no-phantom-phase honesty contract (D-07), mirroring the
        // home.cycleSummary.suppressed no-phantom test. We assert by NON-EXISTENCE.
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-insights"])

        // Wait for the dashboard to have rendered (a trend card exists) before asserting the absence.
        XCTAssertTrue(firstInsightCard(in: app).waitForExistence(timeout: 15),
                      "The seeded dashboard must render (a trend card) before asserting cycle-phase absence.")
        let cyclePhaseSection = app.descendants(matching: .any)["insights.section.cyclePhase"]
        XCTAssertFalse(cyclePhaseSection.exists,
                       "A no-cycle user must have NO insights.section.cyclePhase — the section is omitted, "
                       + "never an empty/phantom placeholder (D-07).")
        saveScreenshot(app, named: "insights-03-no-cyclephase")
    }

    // MARK: - 4. Dismiss removes a card and it stays gone on relaunch

    func testDismiss_removesCard_andStaysGone() throws {
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-insights"])

        let card = firstInsightCard(in: app)
        XCTAssertTrue(card.waitForExistence(timeout: 15),
                      "The seeded dashboard must render a card to dismiss.")
        // Derive the card's stable signalKey root from its `.confidence` sub-element id, so we can target
        // its dismiss affordance and later assert its SPECIFIC (not generic-first) non-existence.
        let signalRoot = cardSignalRoot(card.identifier)            // e.g. insights.card.symptom.pelvicPain.frequency
        // The dismissed card's OWN confidence marker — its non-existence is what proves THIS card is gone.
        // (We must NOT reuse the generic `firstInsightCard` query: after dismiss its `firstMatch` simply
        // resolves to the NEXT card, which still exists — masking the removal.)
        let dismissedMarker = app.descendants(matching: .any)["\(signalRoot).confidence"]

        // Dismiss the card. The swipe action + context menu are both attached to the card's whole row;
        // the long-press context menu is the most robust to trigger via XCUITest, so prefer it, falling
        // back to the trailing swipe. Either reveals the non-destructive "This isn't right" button.
        let dismissButton = app.buttons["\(signalRoot).dismiss"]
        card.press(forDuration: 1.1)                                 // long-press → context menu
        if !dismissButton.waitForExistence(timeout: 4) {
            // Dismiss any presented context menu, then try the trailing swipe action.
            app.tap()
            card.swipeLeft()
            XCTAssertTrue(dismissButton.waitForExistence(timeout: 5),
                          "The card must expose a dismiss affordance (context menu or swipe).")
        }
        dismissButton.tap()

        // The dismissed card disappears (the optimistic removal + the recompute that excludes its
        // signalKey — D-10). Assert on the SPECIFIC dismissed card's `.confidence` marker (its absence is
        // the authoritative proof THIS card is gone; a generic count is flaky across the recompute frame).
        XCTAssertTrue(dismissedMarker.waitForNonExistence(timeout: 10),
                      "After dismiss the dismissed card must disappear from the dashboard.")
        saveScreenshot(app, named: "insights-04-after-dismiss")

        // Stays-gone across a view rebuild + recompute (D-09): leave the Insights tab (to Log) and return.
        // The dismissal lives in the store (a Phase-6 DismissedInsight) and the recompute on re-entry
        // re-reads `dismissedSignalKeys()` and still excludes it, so the card does NOT reappear.
        // (NB: -uitest-reset uses a per-process in-memory store; the cross-PROCESS / cross-DEVICE persistence
        // and CloudKit sync of the dismissal ride the Phase-4 device UAT and the human-verify gate — they
        // cannot be asserted under the in-memory test store. This in-process re-entry is the automatable
        // proof of the stays-gone recompute exclusion.)
        app.buttons["square.and.pencil"].firstMatch.tap()           // Log tab
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "Switching to the Log tab must show Home.")
        var insightsTab = app.tabBars.buttons["tab.insights"]
        if !insightsTab.exists { insightsTab = app.descendants(matching: .any)["tab.insights"].firstMatch }
        insightsTab.tap()
        // The remaining cards render again, but the dismissed card stays gone (recompute still excludes it).
        XCTAssertTrue(firstInsightCard(in: app).waitForExistence(timeout: 15),
                      "On re-entry the dashboard must still render the non-dismissed cards.")
        XCTAssertFalse(app.descendants(matching: .any)["\(signalRoot).confidence"].exists,
                       "The dismissed card must STAY gone after leaving and re-entering the Insights tab (D-09).")
        saveScreenshot(app, named: "insights-05-stays-gone")
    }


    // MARK: - 5. A strong correlation eventually renders the Recommendations section (Plan 11 MVP slice)

    // RED until Plan 11-03 adds the `insights.section.recommendations` section to InsightsView. The
    // `-uitest-seed-recommendations` seed inserts a strong dairy→pelvicPain correlation (Plan 11-01); once
    // the recommendation producer (Plan 02) + the dashboard section (Plan 03) ship, that gated correlation
    // re-frames into a non-prescriptive recommendation card under the new section. This is the MVP
    // end-to-end happy-path test for INST-05.
    func testRecommendation_rendersAfterCorrelation() throws {
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-recommendations"])

        // Wait for the dashboard to render any card first (the seeded correlation/recommendation pipeline).
        XCTAssertTrue(firstInsightCard(in: app).waitForExistence(timeout: 15),
                      "The seeded dashboard must render at least one card before asserting the recommendations section.")

        // The Recommendations section is the LAST section (after Trends + Possible associations), so on a
        // populated dashboard it renders below the fold. SwiftUI's `List` lazily realizes off-screen rows,
        // so scroll the section into view before asserting (the section exists in the model the moment the
        // recompute lands — this scroll merely realizes its lazy cell in the accessibility tree).
        let recommendationsSection = app.descendants(matching: .any)["insights.section.recommendations"]
        let dashboard = app.descendants(matching: .any)["insights.screen"].firstMatch
        var revealed = recommendationsSection.waitForExistence(timeout: 15)
        var scrollAttempts = 0
        while !revealed && scrollAttempts < 6 {
            if dashboard.exists { dashboard.swipeUp() } else { app.swipeUp() }
            revealed = recommendationsSection.waitForExistence(timeout: 3)
            scrollAttempts += 1
        }
        XCTAssertTrue(revealed,
                      "A strong seeded correlation must eventually render the Recommendations section "
                      + "(insights.section.recommendations) — the INST-05 MVP happy path.")
        saveScreenshot(app, named: "insights-06-recommendations")
    }

    // MARK: - Screenshot helper (verbatim from LoggingFlowUITests)

    private func saveScreenshot(_ app: XCUIApplication, named name: String) {
        let screenshot = app.screenshot()

        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        let dir = URL(fileURLWithPath: "/tmp/e2e", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("\(name).png")
        do {
            try screenshot.pngRepresentation.write(to: url)
        } catch {
            XCTFail("Failed to write screenshot \(name): \(error)")
        }
    }
}
