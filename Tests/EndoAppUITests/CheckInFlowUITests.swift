// Requirement: CHCK-01 — the fast daily check-in flow at the UI level (+ LIFE-03 medication-taken).
//
// GREEN (Plan 03-04): the Daily Check-in sheet (`DailyCheckInView`) and its Home CTA now exist, so
// this E2E drives them end-to-end. It mirrors the Phase-2 `LoggingFlowUITests` harness (`-uitest-reset`
// launch arg, consent grant, a11y-id lookups, `waitForExistence`, scroll-into-view loops, the
// `saveScreenshot` helper writing /tmp/e2e/<name>.png).
//
// Three flows are covered:
//   1. testCheckInHappyPath — consent → Home "Daily check-in" CTA → sheet → log a factor →
//      "Done for today" → the day appears in the Home history.
//   2. testEmptyConfirmRecordsAdherenceMarker — open the check-in, immediately confirm with NOTHING
//      entered, and assert a "checked in today" marker is recorded (the day appears) WITHOUT inventing
//      any symptom/factor data (D-15).
//   3. testMedicationTaken_persistsAcrossReopen — the MANDATORY LIFE-03 medication-taken coverage
//      deferred from Plan 02: add a saved med via Settings → manage-meds, mark it taken in the
//      check-in sheet, confirm, re-open, and assert the taken state persisted.
//
// On-device notification DELIVERY and the system permission prompt are NOT asserted here
// (VALIDATION.md Manual-Only — covered by the Plan 03 human-verify checkpoint).
import XCTest

@MainActor
final class CheckInFlowUITests: XCTestCase {

    /// The a11y identifiers the check-in UI exposes for this E2E to drive.
    private enum ID {
        static let homeCheckInButton = "home.dailyCheckInButton"
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

    // MARK: - 1. Happy path (CHCK-01)

    // consent → Home "Daily check-in" CTA → check-in sheet → log a factor → "Done for today" → the day
    // is recorded and appears in the Home history.
    func testCheckInHappyPath() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        grantConsent(app)

        // The prominent Home "Daily check-in" CTA (D-16).
        let checkIn = app.buttons[ID.homeCheckInButton]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 10),
                      "home.dailyCheckInButton must appear after consent.")
        saveScreenshot(app, named: "checkin-01-home")
        checkIn.tap()

        // The check-in sheet presents.
        let sheet = app.descendants(matching: .any)[ID.sheet]
        XCTAssertTrue(sheet.waitForExistence(timeout: 10),
                      "Tapping the CTA must present the check-in sheet.")
        saveScreenshot(app, named: "checkin-02-sheet")

        // "Same as yesterday" prefills sticky context only (meds/sleep/activity), never symptoms (D-14).
        let sameAsYesterday = app.buttons[ID.sameAsYesterday]
        XCTAssertTrue(sameAsYesterday.waitForExistence(timeout: 5),
                      "The sheet must offer 'Same as yesterday' (D-14).")

        // The skippable factor sections are present (scroll through the whole sheet to confirm each).
        for section in [ID.sectionFood, ID.sectionActivity, ID.sectionSleep,
                        ID.sectionMedication, ID.sectionStress, ID.sectionMood] {
            let element = app.descendants(matching: .any)[section]
            scrollToExistence(app, element)
            XCTAssertTrue(element.exists, "The check-in sheet must expose \(section).")
        }

        // Log a factor: select a food tag (LIFE-01) so the day carries real data. The food section is
        // near the top — scroll back up to reach it after the section scan walked to the bottom.
        let dairy = app.buttons["checkin.food.dairy"]
        scrollUpToExistence(app, dairy)
        XCTAssertTrue(dairy.exists, "The food section must expose a tappable tag row.")
        dairy.tap()
        XCTAssertTrue(dairy.isSelected, "A tapped food tag must read as selected.")

        // Confirm — "Done for today" records the day (the confirm button is a fixed toolbar item).
        let confirm = app.buttons[ID.confirmButton]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5),
                      "The check-in sheet must offer a confirm button.")
        confirm.tap()

        // After confirm the day appears in the Home history.
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "After confirm the checked-in day must appear as home.dayRow.0.")
        saveScreenshot(app, named: "checkin-03-history")
    }

    // MARK: - 2. Empty-confirm adherence marker (D-15)

    // Open the check-in, immediately tap "Done for today" without entering anything, and assert a
    // "checked in today" marker is recorded (the day appears) WITHOUT inventing any symptom/factor data.
    func testEmptyConfirmRecordsAdherenceMarker() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        grantConsent(app)

        let checkIn = app.buttons[ID.homeCheckInButton]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 10),
                      "home.dailyCheckInButton must appear after consent.")
        checkIn.tap()

        let sheet = app.descendants(matching: .any)[ID.sheet]
        XCTAssertTrue(sheet.waitForExistence(timeout: 10),
                      "Tapping the CTA must present the check-in sheet.")

        // Immediately confirm — nothing entered (D-15 empty-confirm path).
        let confirm = app.buttons[ID.confirmButton]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5),
                      "The check-in sheet must offer a confirm button.")
        confirm.tap()

        // The adherence marker is recorded: the day appears in the Home history as a bare "Note only"
        // anchor — no symptom/bleeding data invented (D-15).
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "An empty confirm must still record a checked-in day (home.dayRow.0).")
        saveScreenshot(app, named: "checkin-04-empty-confirm")

        // Re-open the recorded day in the detailed editor and assert NO symptom was fabricated: the
        // 'fatigue' symptom row must read UNselected (the empty confirm invented nothing, D-15).
        firstRow.tap()
        let fatigue = app.buttons["daylog.symptom.fatigue"]
        XCTAssertTrue(fatigue.waitForExistence(timeout: 10),
                      "Re-opening the checked-in day must show the editor symptom chips.")
        XCTAssertFalse(fatigue.isSelected,
                       "An empty check-in must NOT invent any symptom data (D-15).")
        saveScreenshot(app, named: "checkin-05-no-invented-data")
    }

    // MARK: - 3. MANDATORY medication-taken E2E (LIFE-03 — deferred from Plan 02)

    // Add a saved medication via Settings → manage-meds, mark it taken in the check-in sheet, confirm,
    // re-open the check-in, and assert the taken state persisted across the re-open.
    func testMedicationTaken_persistsAcrossReopen() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        grantConsent(app)

        // (a) Add a saved medication in Settings → Manage meds (Plan 03-03).
        let settings = app.buttons["home.settingsButton"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "home.settingsButton must exist.")
        settings.tap()

        let addMed = app.buttons["settings.meds.add"]
        scrollToExistence(app, addMed)
        XCTAssertTrue(addMed.exists, "Settings must expose 'Add a medication' (settings.meds.add).")
        addMed.tap()

        // The add-medication sheet: type a name and save.
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5),
                      "The add-medication sheet must present a Name field.")
        nameField.tap()
        nameField.typeText("Ibuprofen")
        app.buttons["Save"].tap()

        // The saved med now appears in the manage-meds list.
        let medRow = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'settings.meds.row.'"))
            .firstMatch
        XCTAssertTrue(medRow.waitForExistence(timeout: 10),
                      "The added medication must appear in the manage-meds list.")
        saveScreenshot(app, named: "checkin-06-saved-med")

        // Back to Home.
        let back = app.navigationBars["Settings"].buttons.element(boundBy: 0)
        XCTAssertTrue(back.waitForExistence(timeout: 5), "Settings back button must exist.")
        back.tap()

        // (b) Open the check-in and mark the saved med taken.
        let checkIn = app.buttons[ID.homeCheckInButton]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 10),
                      "home.dailyCheckInButton must appear on Home.")
        checkIn.tap()

        XCTAssertTrue(app.descendants(matching: .any)[ID.sheet].waitForExistence(timeout: 10),
                      "The check-in sheet must present.")

        // The med "taken" toggle is keyed by the med id (checkin.medication.<uuid>).
        let medToggle = app.switches
            .matching(NSPredicate(format: "identifier BEGINSWITH 'checkin.medication.'"))
            .firstMatch
        scrollToExistence(app, medToggle)
        XCTAssertTrue(medToggle.exists,
                      "The check-in medication section must expose the saved med's taken toggle.")
        // A SwiftUI Toggle in a Form can swallow a centre tap, so tap the trailing edge where the
        // switch thumb sits, and retry until the value reads ON (mirrors LoggingFlowUITests).
        var toggleTries = 0
        while (medToggle.value as? String) != "1" && toggleTries < 4 {
            medToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
            toggleTries += 1
        }
        XCTAssertEqual(medToggle.value as? String, "1",
                       "The medication taken toggle must read ON after tapping (LIFE-03).")
        saveScreenshot(app, named: "checkin-07-med-taken")

        // Confirm — commits the taken state.
        let confirm = app.buttons[ID.confirmButton]
        scrollToExistence(app, confirm)
        XCTAssertTrue(confirm.exists, "The check-in sheet must offer a confirm button.")
        confirm.tap()

        // The day appears in history.
        let firstRow = app.descendants(matching: .any)["home.dayRow.0"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 10),
                      "After confirm the day must appear as home.dayRow.0.")

        // (c) Re-open the check-in → the taken state must have persisted.
        checkIn.tap()
        XCTAssertTrue(app.descendants(matching: .any)[ID.sheet].waitForExistence(timeout: 10),
                      "Re-opening the check-in must present the sheet again.")
        let medToggleAgain = app.switches
            .matching(NSPredicate(format: "identifier BEGINSWITH 'checkin.medication.'"))
            .firstMatch
        scrollToExistence(app, medToggleAgain)
        XCTAssertTrue(medToggleAgain.exists,
                      "Re-opening must show the saved med's taken toggle.")
        XCTAssertEqual(medToggleAgain.value as? String, "1",
                       "The medication taken state must persist across a re-open (LIFE-03).")
        saveScreenshot(app, named: "checkin-08-med-persisted")
    }

    // MARK: - Helpers

    private func grantConsent(_ app: XCUIApplication) {
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on fresh launch")
        grantButton.tap()
    }

    /// Scroll DOWN through content (swipeUp) to bring a lower `element` into the hierarchy (mirrors the
    /// LoggingFlowUITests pattern).
    private func scrollToExistence(_ app: XCUIApplication, _ element: XCUIElement, maxScrolls: Int = 10) {
        var scrolls = 0
        while !element.exists && scrolls < maxScrolls { app.swipeUp(); scrolls += 1 }
        _ = element.waitForExistence(timeout: 5)
    }

    /// Scroll UP toward the top (swipeDown) to bring an earlier/upper `element` into the hierarchy.
    private func scrollUpToExistence(_ app: XCUIApplication, _ element: XCUIElement, maxScrolls: Int = 10) {
        var scrolls = 0
        while !element.exists && scrolls < maxScrolls { app.swipeDown(); scrolls += 1 }
        _ = element.waitForExistence(timeout: 5)
    }

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
