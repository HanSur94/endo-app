// Store + README screenshots: one UI test that walks the shipped shell and attaches a screenshot per
// screen. Not a test of behaviour — every assertion is the minimum needed to know the screen is the
// one we photographed, so a broken build fails here instead of publishing a stale image.
//
// Run:  xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp \
//         -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' \
//         -only-testing:EndoAppUITests/StoreScreenshotTour \
//         -resultBundlePath /tmp/store-shots.xcresult
// Then: xcrun xcresulttool export attachments --path /tmp/store-shots.xcresult --output-path /tmp/shots
import XCTest

@MainActor
final class StoreScreenshotTour: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testCaptureScreens() throws {
        let app = XCUIApplication()
        // Seeded in-memory demo data so the screenshots show a lived-in app, never a patient's data.
        app.launchArguments = [
            "-uitest-reset",
            "-uitest-seed-demo",
            "-uitest-seed-insights",
            "-uitest-seed-report",
            "-uitest-seed-recommendations",
        ]
        app.launch()

        let grant = app.buttons["consent.grantButton"]
        XCTAssertTrue(grant.waitForExistence(timeout: 20), "consent.grantButton")
        shot(app, "00-consent")
        grant.tap()

        XCTAssertTrue(app.buttons["home.dailyCheckInButton"].waitForExistence(timeout: 20), "home after consent")
        sleep(1)
        shot(app, "01-home")

        app.buttons["home.dailyCheckInButton"].tap()
        if app.descendants(matching: .any)["checkin.sheet"].waitForExistence(timeout: 10) {
            sleep(1)
            shot(app, "02-check-in")
            app.swipeUp()
            shot(app, "03-check-in-factors")
            app.swipeDown()
            // Leave without recording anything: the seeds are the data we want, not our synthetic taps.
            if app.buttons["checkin.cancelButton"].exists { app.buttons["checkin.cancelButton"].tap() }
            else { app.swipeDown(velocity: .fast) }
        }

        openInsights(app)
        if app.descendants(matching: .any)["insights.screen"].waitForExistence(timeout: 30) {
            sleep(2)
            shot(app, "04-insights")
            app.swipeUp()
            sleep(1)
            shot(app, "05-insights-cards")
        }

        tab(app, "tab.explore").tap()
        sleep(3)
        shot(app, "08-explore")

        // Lanes: the same data as a per-signal timeline, one swipe down.
        app.swipeUp()
        sleep(2)
        shot(app, "09-explore-lanes")

        // Scatter: the mode toggle sits at the top of Explore, so scroll back up and switch.
        app.swipeDown()
        app.swipeDown()
        let scatter = app.descendants(matching: .any)["explore.mode.scatter"].firstMatch
        if scatter.waitForExistence(timeout: 8) {
            scatter.tap()
            sleep(2)
            // Two axes must be chosen before any dot is drawn; pick the first option for X and the
            // second for Y so the shot is a real spread rather than the self-vs-self diagonal.
            pickAxis(app, "explore.scatter.axisX", option: 0)
            pickAxis(app, "explore.scatter.axisY", option: 1)
            sleep(3)
            shot(app, "10-explore-scatter")
        }

        // Report preview: Settings → Export report → generate. Best effort — a missing preview must not
        // fail the tour, the other screens are already captured.
        // The Home tab carries no a11y id — its label is "Log".
        tab(app, "Log").tap()
        if app.buttons["home.settingsButton"].waitForExistence(timeout: 10) {
            app.buttons["home.settingsButton"].tap()
            var scrolls = 0
            let entry = app.descendants(matching: .any)["settings.report.entry"]
            while !entry.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
            if entry.waitForExistence(timeout: 5) {
                entry.tap()
                if app.descendants(matching: .any)["report.builder"].waitForExistence(timeout: 10) {
                    let generate = app.buttons["report.generate"]
                    if generate.waitForExistence(timeout: 5) {
                        generate.tap()
                        if app.descendants(matching: .any)["report.preview"].waitForExistence(timeout: 25) {
                            sleep(2)
                            shot(app, "07-report")
                        }
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    /// Tap the Insights tab, retrying once: on a cold simulator the first tap can land before the tab
    /// bar finishes laying out, and the seeded dashboard needs a moment to compute.
    private func openInsights(_ app: XCUIApplication) {
        for attempt in 0..<2 {
            tab(app, "tab.insights").tap()
            if app.descendants(matching: .any)["insights.screen"].waitForExistence(timeout: attempt == 0 ? 20 : 30) {
                return
            }
        }
    }

    private func tab(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        let inBar = app.tabBars.buttons[id]
        return inBar.exists ? inBar : app.descendants(matching: .any)[id].firstMatch
    }

    /// Opens one scatter axis menu and taps an option by position (the option ids embed the series id,
    /// so position is the only stable selector available to the tour).
    private func pickAxis(_ app: XCUIApplication, _ stem: String, option: Int) {
        let menu = app.descendants(matching: .any)[stem].firstMatch
        guard menu.waitForExistence(timeout: 8) else { return }
        menu.tap()
        let options = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", stem + "."))
        guard options.count > option else { return }
        options.element(boundBy: option).tap()
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let screenshot = app.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
