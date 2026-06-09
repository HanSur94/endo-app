// Requirement: DATA-05 / SC2 / SC3 / D-03 / D-08 / D-10 (Phase 9, Plan 09-03) — the HealthKit connect
// E2E, proven on the Simulator via the `-uitest-health-*` fixture seam (NO real device, NO real Health
// data, NO real system auth sheet — those are the deferred human-verify item, Task 4).
//
// Clones the `InsightsDashboardUITests` / `LoggingFlowUITests` harness: the `-uitest-reset` launch arg
// (in-memory store + fresh consent gate) PAIRED with a `-uitest-health-*` arg (the fixture reader,
// T-09-06) + `waitForExistence` / `waitForNonExistence` + the `/tmp/e2e/<name>.png` screenshot helper.
//
// Four behaviors:
//   1. rationalePrecedesSystemSheet  — tapping the contextual connect shows `health.rationale.screen`
//                                       BEFORE any system sheet; only "Continue" proceeds (D-06/SC2).
//   2. deniedHealthKeepsAppUsable    — `-uitest-health-denied`: manual logging + insights stay fully
//                                       usable; no-data insights show `insights.empty`, NEVER "no
//                                       symptoms"; the connect affordance stays passive (SC3/D-08).
//   3. importedValueShowsAppleHealthBadge — `-uitest-health-seed`: an imported value renders the
//                                       `health.source.badge` "from Apple Health" label (D-03).
//   4. refreshFromHealthPullsAndRecomputes — `-uitest-health-seed`: `settings.health.refresh` triggers a
//                                       pull + recompute and the seeded data is reflected (D-10/D-12).
//
// The system auth sheet is fixture-stubbed (FixtureHealthDataReading.requestAuthorization is a no-op), so
// these assert the STRUCTURAL flow (rationale ordering, denied-usable, badge, refresh) — not the OS sheet.
import XCTest

@MainActor
final class HealthConnectUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Helpers

    /// Launch with `arguments`, grant consent, and land on Home (the consented shell — health UI is built
    /// ONLY here, T-09-13). Returns the launched app on Home.
    @discardableResult
    private func launchAndConsent(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        return app
    }

    /// Open Settings from Home (the `home.settingsButton` toolbar `NavigationLink`). Settings is PUSHED
    /// onto Home's NavigationStack.
    private func openSettings(_ app: XCUIApplication) {
        let settingsButton = app.buttons["home.settingsButton"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 10),
                      "Home must expose a Settings affordance.")
        settingsButton.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 10),
                      "Tapping Settings must push the Settings screen.")
    }

    /// Pop the pushed Settings screen back to Home via the navigation back button.
    private func popBackToHome(_ app: XCUIApplication) {
        // The pushed Settings screen's nav bar shows the standard back button (titled with the parent
        // "Endo-App"). Tapping the first nav-bar button pops back to Home.
        let backButton = app.navigationBars["Settings"].buttons.firstMatch
        if backButton.exists { backButton.tap() }
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "Popping Settings must return to Home (home.logTodayButton present).")
    }

    /// Scroll an element into view (Settings is a long Form; the Apple Health rows sit mid-list).
    private func scrollToElement(_ app: XCUIApplication, _ element: XCUIElement, maxSwipes: Int = 8) {
        var swipes = 0
        while !element.exists && swipes < maxSwipes { app.swipeUp(); swipes += 1 }
    }

    /// Log a single symptom for TODAY so a `DayEntry` exists (the imported sleep/workout/nutrition values
    /// then surface the provenance badge on that day's row, since none of those fields was logged
    /// manually — manual-wins leaves them sourced `.appleHealth`).
    private func logASymptomToday(_ app: XCUIApplication) {
        app.buttons["home.logTodayButton"].tap()
        let fatigue = app.buttons["daylog.symptom.fatigue"]
        XCTAssertTrue(fatigue.waitForExistence(timeout: 10),
                      "The day-log editor must show the symptom chips.")
        fatigue.tap()
        XCTAssertTrue(app.sliders["daylog.severitySlider"].waitForExistence(timeout: 5),
                      "Selecting a symptom reveals its severity control.")
        let save = app.buttons["daylog.saveButton"]
        scrollToElement(app, save)
        XCTAssertTrue(save.waitForExistence(timeout: 5), "The editor must offer a Save action.")
        save.tap()
        XCTAssertTrue(app.descendants(matching: .any)["home.dayRow.0"].waitForExistence(timeout: 10),
                      "After Save the logged day must appear as home.dayRow.0.")
    }

    /// Switch to the Insights tab from the consented shell.
    private func openInsights(_ app: XCUIApplication) {
        var insightsTab = app.tabBars.buttons["tab.insights"]
        if !insightsTab.waitForExistence(timeout: 10) {
            insightsTab = app.descendants(matching: .any)["tab.insights"].firstMatch
        }
        XCTAssertTrue(insightsTab.waitForExistence(timeout: 10),
                      "A top-level Insights tab (tab.insights) must exist in the consented shell.")
        insightsTab.tap()
    }

    // MARK: - 1. Rationale precedes any system sheet (D-06 / SC2)

    func testRationalePrecedesSystemSheet() throws {
        let app = launchAndConsent(["-uitest-reset", "-uitest-health-seed"])
        openSettings(app)

        // The passive connect affordance (first decision → shows the rationale, not the system sheet).
        let connect = app.buttons["health.connect.button"]
        scrollToElement(app, connect)
        XCTAssertTrue(connect.waitForExistence(timeout: 10),
                      "Settings must show a passive 'Connect Apple Health' affordance (D-05/D-07).")
        connect.tap()

        // The IN-APP rationale screen appears BEFORE any system sheet (D-06). The system sheet is
        // fixture-stubbed, so we assert the rationale's presence + that Continue is the only proceed path.
        XCTAssertTrue(app.descendants(matching: .any)["health.rationale.screen"].waitForExistence(timeout: 10),
                      "Tapping connect must present the in-app rationale BEFORE any system sheet (D-06).")
        XCTAssertTrue(app.buttons["health.rationale.continue"].waitForExistence(timeout: 5),
                      "The rationale must offer a 'Continue' button — the ONLY path that proceeds (D-06).")
        XCTAssertTrue(app.buttons["health.rationale.cancel"].exists,
                      "The rationale must offer a non-nagging 'Not now' dismiss (D-08).")
        saveScreenshot(app, named: "health-01-rationale")

        // Continue proceeds (fixture auth is a no-op); the rationale dismisses and the app stays usable.
        app.buttons["health.rationale.continue"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["health.rationale.screen"].waitForNonExistence(timeout: 10),
                      "After Continue the rationale dismisses (the import runs behind the fixture seam).")
    }

    // MARK: - 2. Denied Health keeps the app fully usable; insights never read "no symptoms" (SC3 / D-08)

    func testDeniedHealthKeepsAppUsable() throws {
        let app = launchAndConsent(["-uitest-reset", "-uitest-health-denied"])

        // With a denied/empty Health read AND no manual data, the insights dashboard shows the WARM empty
        // state — NEVER a fabricated "no symptoms" reading (the SC3 honesty anchor). A denied import
        // contributes nothing, so the empty state is genuine absence, not a coerced zero.
        openInsights(app)
        XCTAssertTrue(app.descendants(matching: .any)["insights.empty"].waitForExistence(timeout: 15),
                      "A denied/empty Health read must leave insights at the warm empty state, never 'no symptoms' (SC3).")
        saveScreenshot(app, named: "health-02-denied-empty")

        // Manual logging remains fully usable — the app never depends on a Health grant (SC3). Log a
        // symptom and confirm it lands in the history.
        app.buttons["square.and.pencil"].firstMatch.tap()   // back to the Log tab
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "Manual logging must stay reachable after a denied Health read (SC3).")
        logASymptomToday(app)

        // The connect affordance stays passive (reachable in Settings, no nag loop, D-08).
        openSettings(app)
        let connect = app.buttons["health.connect.button"]
        scrollToElement(app, connect)
        XCTAssertTrue(connect.waitForExistence(timeout: 10),
                      "After a denied read the passive connect affordance must still be present (no nag, D-08).")
    }

    // MARK: - 3. An imported value shows the 'from Apple Health' badge (D-03)

    func testImportedValueShowsAppleHealthBadge() throws {
        let app = launchAndConsent(["-uitest-reset", "-uitest-health-seed"])

        // Create a DayEntry for today (a manual symptom — NOT sleep/workout/food), so the seeded imported
        // sleep/workout/nutrition values surface as `.appleHealth` on that day's row (manual-wins leaves
        // those fields imported-sourced).
        logASymptomToday(app)

        // Pull the seeded imported values (Refresh from Health) so they are persisted + merged.
        openSettings(app)
        let refresh = app.buttons["settings.health.refresh"]
        scrollToElement(app, refresh)
        XCTAssertTrue(refresh.waitForExistence(timeout: 10), "Settings must offer 'Refresh from Health' (D-10).")
        refresh.tap()

        // Back on Home, the today row carries the 'from Apple Health' provenance badge (D-03).
        popBackToHome(app)
        let badge = app.descendants(matching: .any)["health.source.badge"]
        XCTAssertTrue(badge.waitForExistence(timeout: 15),
                      "An imported value must render the 'from Apple Health' provenance badge (D-03).")
        saveScreenshot(app, named: "health-03-provenance-badge")
    }

    // MARK: - 4. Refresh from Health pulls + recomputes (D-10 / D-12)

    func testRefreshFromHealthPullsAndRecomputes() throws {
        let app = launchAndConsent(["-uitest-reset", "-uitest-health-seed"])

        // A manual day exists so the imported values have a row to surface on.
        logASymptomToday(app)

        openSettings(app)
        let refresh = app.buttons["settings.health.refresh"]
        scrollToElement(app, refresh)
        XCTAssertTrue(refresh.waitForExistence(timeout: 10),
                      "Settings must offer a 'Refresh from Health' action (D-10).")
        refresh.tap()
        saveScreenshot(app, named: "health-04-after-refresh")

        // The pull persisted + merged the seeded imported data: the provenance badge now reflects it on the
        // day's row (the recompute/merge ran — D-10/D-12). Proven by the badge's presence after refresh.
        popBackToHome(app)
        let badge = app.descendants(matching: .any)["health.source.badge"]
        XCTAssertTrue(badge.waitForExistence(timeout: 15),
                      "After 'Refresh from Health' the seeded imported data must be reflected (D-10/D-12).")
    }

    // MARK: - Screenshot helper (verbatim from InsightsDashboardUITests)

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
