// Requirement: RPRT-01 / RPRT-03 (Phase 10, D-06/D-07/D-09) — the doctor-ready report E2E.
//
// Clones the `InsightsDashboardUITests` harness (the `-uitest-reset` launch arg + a deterministic seed +
// `waitForExistence` + a `saveScreenshot` writing `/tmp/e2e/<name>.png` for the end-of-phase human
// PDF-credibility / share-sheet check). Launches with `[-uitest-reset, -uitest-seed-report]` so the
// builder has a populated range to render.
//
// Two cases mapping the 10-VALIDATION ReportFlowUITests row:
//   1. settings_to_preview_share — Settings → Export report → preset → Generate → preview appears with a
//      PDF + a Share affordance (RPRT-01 + the explicit-share egress for RPRT-03).
//   2. invalidCustomRange_keepsGenerateDisabled — a Custom range with To before From keeps Generate
//      disabled (the honesty guard; no fabricated range). Driven via `-uitest-report-invalid-range`, which
//      seeds the builder into an inverted custom range so we assert the WIRING deterministically instead
//      of fighting the compact DatePicker calendar (brittle/locale-dependent). The guard MATH is unit-
//      tested separately in `ReportModelTests` via `ReportRangeMapping.isValid`.
//
// Deterministic a11y ids ONLY — never copy-string matching (the ids are the stable contract). We do NOT
// assert the OS share-sheet CONTENTS (OS-driven — the 10-VALIDATION Manual-Only row covers that).
//
// A11y identifiers this E2E drives (added by SettingsView / ReportBuilderView / ReportPreviewView, 10-03):
//   settings.report.entry, report.builder, report.range.preset, report.range.from/.to,
//   report.generate, report.range.invalid, report.preview, report.preview.pdf, report.share.
import XCTest

@MainActor
final class ReportFlowUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Helpers

    /// Launch, grant consent, open Settings, and tap into the report builder. Returns the app on
    /// `report.builder`.
    @discardableResult
    private func launchToReportBuilder(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        // Grant consent (Settings lives ONLY under AppGate's consented branch — V4/T-10-11).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        // Land on the consented shell (Home), then open Settings via the toolbar gear.
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        let settingsButton = app.buttons["home.settingsButton"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 10),
                      "The Settings toolbar button (home.settingsButton) must exist on Home.")
        settingsButton.tap()

        // Tap the Export report row → the report builder. The row sits near the bottom of the long
        // Settings Form (below reminders/meds/health), so scroll it into view (the suite's scroll-into-view
        // pattern, mirroring ConsentFlowUITests' withdraw-button reach).
        let reportEntry = app.descendants(matching: .any)["settings.report.entry"]
        var scrolls = 0
        while !reportEntry.exists && scrolls < 8 { app.swipeUp(); scrolls += 1 }
        XCTAssertTrue(reportEntry.waitForExistence(timeout: 10),
                      "Settings must expose the Export report row (settings.report.entry).")
        reportEntry.tap()

        XCTAssertTrue(app.descendants(matching: .any)["report.builder"].waitForExistence(timeout: 10),
                      "Tapping Export report must push the report builder (report.builder).")
        return app
    }

    // MARK: - 1. Settings → Export report → preset → Generate → preview + Share present

    func testSettingsToPreviewShare() throws {
        let app = launchToReportBuilder(["-uitest-reset", "-uitest-seed-report"])

        // The default preset (Last 30 days) already covers the seeded range; tap it explicitly so the E2E
        // exercises the segmented preset Picker (drives report.range.preset).
        let presetPicker = app.descendants(matching: .any)["report.range.preset"]
        XCTAssertTrue(presetPicker.waitForExistence(timeout: 10),
                      "The range-preset Picker (report.range.preset) must exist in the builder.")
        let last30Segment = app.buttons["Last 30 days"]
        if last30Segment.waitForExistence(timeout: 4) { last30Segment.tap() }

        // Generate the on-device PDF (RPRT-01).
        let generate = app.buttons["report.generate"]
        XCTAssertTrue(generate.waitForExistence(timeout: 10),
                      "The Generate button (report.generate) must exist.")
        XCTAssertTrue(generate.isEnabled,
                      "Generate must be ENABLED for a valid preset range.")
        generate.tap()

        // The preview appears with the PDF + an explicit Share affordance (RPRT-01 + RPRT-03).
        XCTAssertTrue(app.descendants(matching: .any)["report.preview"].waitForExistence(timeout: 20),
                      "After Generate the PDF preview (report.preview) must appear.")
        XCTAssertTrue(app.descendants(matching: .any)["report.preview.pdf"].waitForExistence(timeout: 10),
                      "The preview must render a PDFKit PDFView (report.preview.pdf).")
        XCTAssertTrue(app.descendants(matching: .any)["report.share"].waitForExistence(timeout: 10),
                      "The preview must offer an explicit Share affordance (report.share) — RPRT-03.")

        // Save the rendered preview for the end-of-phase human PDF-credibility + share-sheet check.
        saveScreenshot(app, named: "report-01-preview")
    }

    // MARK: - 2. Invalid custom range keeps Generate disabled

    func testInvalidCustomRange_keepsGenerateDisabled() throws {
        // `-uitest-report-invalid-range` seeds the builder onto the Custom preset with an INVERTED range
        // (To before From), so the inline guard must show and Generate must be disabled on arrival — no
        // DatePicker-calendar driving (that is brittle/locale-dependent in XCUITest). The guard math is
        // unit-tested in ReportModelTests via ReportRangeMapping.isValid; this E2E proves the WIRING.
        let app = launchToReportBuilder(["-uitest-reset", "-uitest-report-invalid-range"])

        let invalidMessage = app.descendants(matching: .any)["report.range.invalid"]
        XCTAssertTrue(invalidMessage.waitForExistence(timeout: 10),
                      "An inverted custom range (To before From) must show the inline invalid-range guard.")

        let generate = app.buttons["report.generate"]
        XCTAssertTrue(generate.waitForExistence(timeout: 10),
                      "Generate must exist in the Custom range section.")
        XCTAssertFalse(generate.isEnabled,
                       "Generate must be DISABLED while the custom range is invalid (To before From).")

        saveScreenshot(app, named: "report-02-invalid-range")
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
