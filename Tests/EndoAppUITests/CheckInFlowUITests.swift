// RED until 03-04 (DailyCheckInView).
//
// Requirement: CHCK-01 — the fast daily check-in flow at the UI level.
//
// Nyquist Wave 0 (Plan 03-01 Task 3): RED end-to-end XCUITest. It mirrors the Phase-2
// LoggingFlowUITests harness (`-uitest-reset` launch arg, consent grant, a11y-id lookups,
// waitForExistence, the saveScreenshot helper writing /tmp/e2e/<name>.png). Because the Daily
// Check-in sheet does not exist yet, the `checkin.*` assertions FAIL by design — correct for Wave 0.
// The flow turns GREEN in Plan 03-04 Task 2 (DailyCheckInView). DO NOT disable this test.
//
// Accessibility identifiers reserved by this E2E (added by the views in Plan 03-04):
//   home.checkInButton, checkin.sheet, checkin.sameAsYesterday,
//   checkin.section.food, checkin.section.activity, checkin.section.sleep,
//   checkin.section.medication, checkin.section.stress, checkin.section.mood,
//   checkin.confirmButton.
import XCTest

@MainActor
final class CheckInFlowUITests: XCTestCase {

    /// The a11y identifiers Plan 03-04's check-in UI must expose for this E2E to drive.
    private enum ID {
        static let homeCheckInButton = "home.checkInButton"
        static let sheet = "checkin.sheet"
        static let sameAsYesterday = "checkin.sameAsYesterday"
        static let sectionFood = "checkin.section.food"
        static let sectionActivity = "checkin.section.activity"
        static let sectionSleep = "checkin.section.sleep"
        static let sectionMedication = "checkin.section.medication"
        static let sectionStress = "checkin.section.stress"
        static let sectionMood = "checkin.section.mood"
        static let confirmButton = "checkin.confirmButton"
    }

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // GREEN in Plan 03-04: consent → Home "Daily check-in" CTA → check-in sheet → confirm → the day
    // is recorded. RED now (the CTA + sheet don't exist yet) — that's the Wave-0 contract.
    func testCheckInHappyPath() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        // Grant consent (reuses the Phase-1 consent gate).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()

        // The prominent Home "Daily check-in" CTA (D-16).
        let checkIn = app.buttons[ID.homeCheckInButton]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 10),
                      "home.checkInButton must appear after consent (RED until 03-04).")
        saveScreenshot(app, named: "checkin-01-home")
        checkIn.tap()

        // The check-in sheet presents.
        let sheet = app.descendants(matching: .any)[ID.sheet]
        XCTAssertTrue(sheet.waitForExistence(timeout: 10),
                      "Tapping the CTA must present the check-in sheet (RED until 03-04).")
        saveScreenshot(app, named: "checkin-02-sheet")

        // "Same as yesterday" prefills sticky context only (meds/sleep/activity), never symptoms (D-14).
        let sameAsYesterday = app.buttons[ID.sameAsYesterday]
        XCTAssertTrue(sameAsYesterday.waitForExistence(timeout: 5),
                      "The sheet must offer 'Same as yesterday' (D-14).")

        // The skippable factor sections are present.
        for section in [ID.sectionFood, ID.sectionActivity, ID.sectionSleep,
                        ID.sectionMedication, ID.sectionStress, ID.sectionMood] {
            let element = app.descendants(matching: .any)[section]
            var scrolls = 0
            while !element.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
            XCTAssertTrue(element.waitForExistence(timeout: 5),
                          "The check-in sheet must expose \(section).")
        }

        // Confirm — an empty confirm still records a "checked in today" adherence marker (D-15).
        let confirm = app.buttons[ID.confirmButton]
        var scrolls = 0
        while !confirm.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(confirm.waitForExistence(timeout: 5),
                      "The check-in sheet must offer a confirm button (RED until 03-04).")
        confirm.tap()

        // After confirm the day appears in the Home history.
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "After confirm the checked-in day must appear as home.dayRow.0.")
        saveScreenshot(app, named: "checkin-03-history")
    }

    // MARK: - Screenshot helper (verbatim from LoggingFlowUITests)

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
