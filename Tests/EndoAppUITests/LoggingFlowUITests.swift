// Requirement: end-to-end logging flow (SYMP-01..04 + CYCL-01/03 at the UI level).
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED end-to-end XCUITest. It mirrors the Phase-1
// ConsentFlowUITests harness (`-uitest-reset` launch arg, a11y-id lookups, waitForExistence, the
// saveScreenshot helper writing /tmp/e2e/<name>.png). Because the new home screen does not exist
// yet, the `home.logTodayButton` assertion FAILS by design — correct for Wave 0. Early activities
// turn GREEN in Plan 02-02 Task 2; the full flow turns GREEN in Plan 02-04 Task 2.
//
// Accessibility identifiers reserved by this E2E (added by the views in Plans 02/04):
//   home.logTodayButton, home.dayRow.<index>, home.cycleSummary,
//   home.cycleSummary.suppressed, home.cycleSummary.notEnoughData,
//   daylog.symptom.<type>, daylog.severitySlider, daylog.note, daylog.saveButton,
//   daylog.bleeding.<intensity>, daylog.deleteButton, bodymap.region.<region>,
//   settings.suppressedToggle.
import XCTest

@MainActor
final class LoggingFlowUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // RED (Wave 0): the happy-path logging flow. Asserts the home "Log today" entry point exists
    // after consent — which fails until the real home screen ships (Plans 02/04).
    func testLoggingFlow() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        // Grant consent (reuses the Phase-1 consent gate).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()

        // RED: the real home screen with a "Log today" entry does not exist yet.
        let logToday = app.buttons["home.logTodayButton"]
        XCTAssertTrue(logToday.waitForExistence(timeout: 10),
                      "RED: home.logTodayButton must appear after consent — ships in Plan 02-02/04.")
        saveScreenshot(app, named: "logging-01-home")

        // Plan 04 turns GREEN: the full intended flow (documented; not yet executable):
        //   logToday.tap()
        //   tap daylog.symptom.fatigue → set daylog.severitySlider
        //   tap bodymap.region.lowerAbdomenPelvis → set its severity
        //   set daylog.bleeding.medium
        //   enter daylog.note
        //   tap daylog.saveButton
        //   assert app.descendants(matching: .any)["home.dayRow.0"].exists
        //   tap home.dayRow.0 to edit → tap daylog.deleteButton
        //   assert the row is gone (home.dayRow.0 no longer exists)
    }

    // RED (Wave 0): with suppressed mode on, the home cycle summary must show the suppressed state
    // and never a cycle-day / "not enough data" value. Fails until Plan 04 ships the home + toggle.
    func testSuppressedModeShowsNoCycleSummary() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()

        // RED: the suppressed toggle + cycle summary do not exist yet (ship in Plan 02-04).
        let suppressedToggle = app.switches["settings.suppressedToggle"]
        XCTAssertTrue(suppressedToggle.waitForExistence(timeout: 10),
                      "RED: settings.suppressedToggle must exist — ships in Plan 02-04.")
        saveScreenshot(app, named: "logging-02-suppressed")

        // Plan 04 turns GREEN: with the toggle on,
        //   assert app.descendants(matching: .any)["home.cycleSummary.suppressed"].exists
        //   assert NOT app.descendants(matching: .any)["home.cycleSummary.notEnoughData"].exists
        //   assert no cycle-day text is shown anywhere.
    }

    // MARK: - Screenshot helper (verbatim from ConsentFlowUITests)

    /// Attaches the screenshot to the result bundle (kept always) AND writes a displayable PNG to
    /// /tmp/e2e/<name>.png for out-of-band inspection.
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
