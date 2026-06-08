// Requirement: ONBD-01 end-to-end proof — a real XCUITest that drives the running app and proves
// the GDPR consent gate behaves: gate shown on a fresh launch, no logging UI reachable before
// consent, grant -> walking skeleton, log increments, withdraw -> re-gated.
//
// The `-uitest-reset` launch argument (handled in AppDependencies) makes every run deterministic:
// the consent flag is cleared and the SwiftData store is in-memory, so the app always starts at
// the consent gate with an empty store.
//
// Each step attaches a screenshot (kept always) AND writes the PNG to /tmp/e2e/<name>.png so the
// screenshots are displayable outside the xcresult bundle.
import XCTest

@MainActor
final class ConsentFlowUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testConsentGateFlow() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-reset"]
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        // The disclaimer banner combines its children into one accessibility element, which can
        // surface as a static text OR other element depending on the OS — match by identifier
        // across any element type.
        let disclaimer = app.descendants(matching: .any)["onboarding.disclaimer"]
        let logButton = app.buttons["skeleton.logButton"]
        let countLabel = app.staticTexts["skeleton.countLabel"]
        let settingsButton = app.buttons["skeleton.settingsButton"]
        let withdrawButton = app.buttons["settings.withdrawButton"]

        // 1. CONSENT GATE shown on a fresh launch.
        XCTContext.runActivity(named: "01 - consent gate shown") { _ in
            XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                          "Consent grant button must exist on fresh launch")
            XCTAssertTrue(grantButton.isHittable, "Consent grant button must be tappable")
            XCTAssertTrue(disclaimer.waitForExistence(timeout: 5),
                          "Disclaimer must be visible before consent")
            // No logging UI before consent (no processing before opt-in).
            XCTAssertFalse(logButton.exists, "Log button must NOT exist before consent")
            saveScreenshot(app, named: "01-consent-gate")
        }

        // 2. Grant consent -> walking skeleton, count starts at 0.
        XCTContext.runActivity(named: "02 - after consent") { _ in
            grantButton.tap()
            XCTAssertTrue(logButton.waitForExistence(timeout: 10),
                          "Log button must appear after consent")
            XCTAssertTrue(countLabel.waitForExistence(timeout: 5))
            XCTAssertEqual(countLabel.label, "0 day(s) logged",
                           "Count must start at 0 on a fresh in-memory store")
            saveScreenshot(app, named: "02-after-consent")
        }

        // 3. Log a day -> count increments to 1.
        XCTContext.runActivity(named: "03 - after log") { _ in
            logButton.tap()
            let oneLogged = countLabel.label == "1 day(s) logged"
            let predicate = NSPredicate(format: "label == %@", "1 day(s) logged")
            if !oneLogged {
                expectation(for: predicate, evaluatedWith: countLabel)
                waitForExpectations(timeout: 5)
            }
            XCTAssertEqual(countLabel.label, "1 day(s) logged",
                           "Count must increment to 1 after logging a day")
            saveScreenshot(app, named: "03-after-log")
        }

        // 4. Withdraw consent -> back at the consent gate, no logging UI.
        XCTContext.runActivity(named: "04 - after withdraw") { _ in
            settingsButton.tap()
            XCTAssertTrue(withdrawButton.waitForExistence(timeout: 10),
                          "Withdraw button must exist in Settings")
            withdrawButton.tap()
            // Confirm in the destructive confirmation dialog. Both the Settings trigger button and
            // the dialog's confirm button carry the label "Withdraw consent"; disambiguate by
            // scoping to the presented sheet so we tap the dialog's confirm action.
            let dialog = app.sheets["Withdraw consent?"]
            XCTAssertTrue(dialog.waitForExistence(timeout: 5),
                          "Withdraw confirmation dialog must be presented")
            let confirm = dialog.buttons["Withdraw consent"]
            XCTAssertTrue(confirm.waitForExistence(timeout: 5),
                          "Confirmation dialog must present a Withdraw consent action")
            confirm.tap()

            XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                          "App must be re-gated at the consent prompt after withdrawal")
            XCTAssertFalse(logButton.exists,
                           "Log button must be gone after withdrawal (no processing)")
            saveScreenshot(app, named: "04-after-withdraw")
        }
    }

    // MARK: - Screenshot helper

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
