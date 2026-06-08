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

    // GREEN (Plan 02-04): the full happy-path logging flow — consent → log today (symptom+severity →
    // body region+severity → bleeding → note) → save → the day appears → edit → delete → row gone.
    func testLoggingFlow() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        // Grant consent (reuses the Phase-1 consent gate).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()

        let logToday = app.buttons["home.logTodayButton"]
        XCTAssertTrue(logToday.waitForExistence(timeout: 10),
                      "home.logTodayButton must appear after consent.")
        saveScreenshot(app, named: "logging-01-home")
        logToday.tap()

        // Symptom + severity.
        let fatigue = app.buttons["daylog.symptom.fatigue"]
        XCTAssertTrue(fatigue.waitForExistence(timeout: 10),
                      "The day-log editor must show the symptom chips.")
        fatigue.tap()
        XCTAssertTrue(app.sliders["daylog.severitySlider"].waitForExistence(timeout: 5),
                      "Selecting a symptom reveals its 0–10 SeverityControl.")

        // Body region + severity (the accessible body map, SYMP-02).
        let region = app.descendants(matching: .any)["bodymap.region.lowerAbdomenPelvis"]
        var scrolls = 0
        while !region.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(region.waitForExistence(timeout: 5),
                      "The body map must expose a tappable lowerAbdomenPelvis region.")
        region.tap()
        // The region severity sheet presents — set a value and dismiss.
        let regionDone = app.buttons["bodymap.region.lowerAbdomenPelvis.done"]
        XCTAssertTrue(regionDone.waitForExistence(timeout: 5),
                      "Tapping a region presents its severity sheet.")
        regionDone.tap()

        // Bleeding flow (scroll into view — controls below the fold).
        let bleeding = app.buttons["daylog.bleeding.medium"]
        scrolls = 0
        while !bleeding.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(bleeding.waitForExistence(timeout: 5),
                      "The bleeding flow control must be reachable in the editor.")
        bleeding.tap()

        // Note.
        let note = app.textFields["daylog.note"]
        scrolls = 0
        while !note.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(note.waitForExistence(timeout: 5))
        note.tap()
        note.typeText("rough day")
        saveScreenshot(app, named: "logging-03-editor")

        // Save → the day appears as the first history row.
        app.buttons["daylog.saveButton"].tap()
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "After Save the logged day must appear as home.dayRow.0.")
        saveScreenshot(app, named: "logging-04-history")

        // Edit → the editor re-opens preloaded → delete the day.
        firstRow.tap()
        let deleteButton = app.buttons["daylog.deleteButton"]
        scrolls = 0
        while !deleteButton.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 10),
                      "Re-opening a logged day must offer a delete affordance (SYMP-04).")
        deleteButton.tap()
        let confirm = app.buttons["daylog.deleteConfirm"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 5),
                      "Delete must require confirmation.")
        confirm.tap()

        // The row is gone — delete cascaded and the @Query refreshed (D-06).
        XCTAssertTrue(firstRow.waitForNonExistence(timeout: 10),
                      "After delete the day must disappear from history (home.dayRow.0 gone).")
        saveScreenshot(app, named: "logging-05-after-delete")
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

    // GREEN (Plan 02-04): with suppressed mode on, the home cycle summary shows ONLY the suppressed
    // off-state and never a cycle-day / "not enough data yet" value (CYCL-03, the no-phantom-cycle E2E).
    func testSuppressedModeShowsNoCycleSummary() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()

        // Open Settings and turn suppressed mode on.
        let settings = app.buttons["home.settingsButton"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()

        let suppressedToggle = app.switches["settings.suppressedToggle"]
        var scrolls = 0
        while !suppressedToggle.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(suppressedToggle.waitForExistence(timeout: 10),
                      "settings.suppressedToggle must exist.")
        // Flip the switch ON. A SwiftUI Toggle in a Form can swallow a centre tap, so tap the
        // trailing edge where the switch thumb sits, and retry until the value reads ON.
        var toggleTries = 0
        while (suppressedToggle.value as? String) != "1" && toggleTries < 4 {
            suppressedToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            toggleTries += 1
        }
        XCTAssertEqual(suppressedToggle.value as? String, "1",
                       "The suppressed toggle must read ON after tapping.")
        saveScreenshot(app, named: "logging-02-suppressed")

        // Back to Home (the Settings back chevron lives in the navigation bar).
        let back = app.navigationBars["Settings"].buttons.element(boundBy: 0)
        XCTAssertTrue(back.waitForExistence(timeout: 5), "Settings back button must exist.")
        back.tap()

        // The cycle summary shows the suppressed off-state — and NOTHING cycle-derivable.
        _ = app.buttons["home.logTodayButton"].waitForExistence(timeout: 10)   // confirm we're Home
        saveScreenshot(app, named: "logging-06-suppressed-home")
        let suppressedLine = app.descendants(matching: .any)["home.cycleSummary.suppressed"]
        XCTAssertTrue(suppressedLine.waitForExistence(timeout: 10),
                      "Suppressed mode must show the cycle-tracking-off line.")
        XCTAssertFalse(app.descendants(matching: .any)["home.cycleSummary.notEnoughData"].exists,
                       "Suppressed mode must NOT show a 'not enough data' value.")
        XCTAssertFalse(app.descendants(matching: .any)["home.cycleSummary.cycleDay"].exists,
                       "Suppressed mode must NOT show a cycle-day.")
        XCTAssertFalse(app.descendants(matching: .any)["home.cycleSummary.nextWindow"].exists,
                       "Suppressed mode must NOT show a next-period window.")
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
