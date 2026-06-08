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

    // GREEN (Plan 02-02): the day-log vertical slice — log today's symptom + severity + bleeding +
    // note, Save, and the day appears in the reverse-chronological history. (Edit/delete + body map
    // land in Plan 04; this asserts only the Plan-02-02 slice.)
    func testLogToday_savesAndAppearsInHistory() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10))
        grantButton.tap()

        let logToday = app.buttons["home.logTodayButton"]
        XCTAssertTrue(logToday.waitForExistence(timeout: 10))
        logToday.tap()

        // Select a symptom -> its SeverityControl appears.
        let fatigue = app.buttons["daylog.symptom.fatigue"]
        XCTAssertTrue(fatigue.waitForExistence(timeout: 10),
                      "The day-log editor must show the symptom chips.")
        fatigue.tap()
        XCTAssertTrue(app.sliders["daylog.severitySlider"].waitForExistence(timeout: 5),
                      "Selecting a symptom reveals its 0–10 SeverityControl.")

        // Record bleeding flow (scroll it into view — the severity control pushes it below the fold).
        let bleeding = app.buttons["daylog.bleeding.medium"]
        var scrolls = 0
        while !bleeding.exists && scrolls < 6 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(bleeding.waitForExistence(timeout: 5),
                      "The bleeding flow control must be reachable in the editor.")
        bleeding.tap()

        // Type a note (scroll it into view if needed).
        let note = app.textFields["daylog.note"]
        scrolls = 0
        while !note.exists && scrolls < 6 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(note.waitForExistence(timeout: 5))
        note.tap()
        note.typeText("rough day")
        saveScreenshot(app, named: "logging-03-editor")

        // Save -> editor dismisses, the day appears as the first history row.
        app.buttons["daylog.saveButton"].tap()
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "After Save the logged day must appear as home.dayRow.0.")
        saveScreenshot(app, named: "logging-04-history")
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
