// Requirement: RPRT-01 / RPRT-02 (Phase 10, Plan 02) — the renderer's behavior proof (NOT visual diffing).
//
// Proves `ReportPDFRenderer` turns a POPULATED, honest `ReportModel` into a non-empty, multi-page,
// selectable-text, on-device VECTOR PDF whose extracted text carries the audited surfaces a clinician
// must see — and that a suppressed-user model renders the honest cycle off-state, never a phantom cycle.
//
// Method (RESEARCH §Code Examples): render → `PDFDocument(data:)` → assert `pageCount` + extract
// `PDFDocument.string` and assert STABLE substrings. `ImageRenderer` (the chart embed) is `@MainActor`,
// so the whole suite is `@MainActor`. The fixtures are assembled over an in-memory `SwiftDataRepository`
// (mirrors `ReportModelTests` / `HealthImportMergeTests`) — no CloudKit / network / UI.
import Testing
import Foundation
import PDFKit
import SwiftData
@testable import EndoApp
import EndoInsights

@MainActor
@Suite("Phase-10 ReportPDFRenderer render smoke + extracted-text honesty")
struct ReportRenderTests {

    // A stable, calendar-safe anchor (mirrors ReportModelTests).
    private let day0 = Date(timeIntervalSince1970: 1_700_000_000)

    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    /// A 90-day range ending at `day0` (wide enough to hold a multi-day populated fixture).
    private var range: DateInterval {
        let cal = Calendar.current
        return DateInterval(start: cal.date(byAdding: .day, value: -90, to: day0)!, end: day0)
    }

    private func engine(_ snapshots: [DaySnapshot], _ asOf: Date) -> [Insight] {
        InsightsEngine.insights(from: snapshots, asOf: asOf, dismissed: [])
    }

    private func imported(_ payload: ImportedPayload, domain: HealthDomain, on day: Date) -> ImportedSampleValue {
        ImportedSampleValue(hkUUID: UUID(), day: day, domain: domain, payload: payload)
    }

    /// A POPULATED fixture: MANUAL symptoms across many distinct days + manual lifestyle/bleeding (so the
    /// sections aggregate to Self-reported and the symptoms section clears the 3-day trend floor + renders a
    /// chart), PLUS one IMPORTED sample on a separate day (so the legend surfaces "From Apple Health"). The
    /// breadth of symptom rows + the chart + tables push the document onto a second page.
    private func populatedModel() throws -> ReportModel {
        let repo = try makeRepo()
        let cal = Calendar.current
        func day(_ offset: Int) -> Date { cal.date(byAdding: .day, value: offset, to: day0)! }

        // Many symptom TYPES, each logged on >= 3 distinct days → a meaningful trend + a broad table.
        let types: [EndoApp.SymptomType] = [.pelvicPain, .fatigue, .headache, .lowMood, .backLegPain, .giEndoBelly]
        for (t, type) in types.enumerated() {
            for d in 0..<5 {
                try repo.upsertSymptom(type: type, severity: (t + d) % 9 + 1, on: day(-(d * 3 + 2)))
            }
        }
        // Manual lifestyle across the range (Self-reported provenance + populated daily-factor rows).
        try repo.setSleep(hours: 7, quality: .good, on: day(-5))
        try repo.setActivity(type: .walking, minutes: 30, intensity: .low, on: day(-5))
        try repo.setStress(0, on: day(-5))   // a real logged 0 (absence != 0)
        try repo.setMood(3, on: day(-7))
        try repo.setFood(tags: [.dairy], freeText: nil, on: day(-9))
        // Manual bleeding history → derived cycle (>= 2 cycles).
        for offset in [-2, -3, -30, -31, -58, -59, -86, -87] {
            try repo.setBleeding(intensity: .medium, clots: false, on: day(offset))
        }
        // ONE imported sample on a day with NO manual lifestyle → an appleHealth provenance contribution
        // (so the legend surfaces the "From Apple Health" line, RPRT-02).
        try repo.upsertImportedSample(imported(.sleep(hours: 8, quality: .good), domain: .sleep, on: day(-12)))

        return try ReportModel.assemble(range: range, repository: repo, engine: engine, asOf: day0)
    }

    /// A SUPPRESSED-user fixture (plenty of bleeding history, but suppression is on) → the cycle off-state.
    private func suppressedModel() throws -> ReportModel {
        let repo = try makeRepo()
        let cal = Calendar.current
        func day(_ offset: Int) -> Date { cal.date(byAdding: .day, value: offset, to: day0)! }
        for offset in [-2, -3, -30, -31, -58, -59] {
            try repo.setBleeding(intensity: .medium, clots: false, on: day(offset))
        }
        try repo.setSuppressed(true)
        return try ReportModel.assemble(range: range, repository: repo, engine: engine, asOf: day0)
    }

    // MARK: - RPRT-01: a non-empty, multi-page vector PDF

    @Test func generatesNonEmptyMultiPagePDF() throws {
        let model = try populatedModel()
        let data = ReportPDFRenderer.render(model: model)

        #expect(!data.isEmpty, "The renderer must produce non-empty PDF Data (RPRT-01).")
        let doc = try #require(PDFDocument(data: data), "The produced Data must be a valid PDF.")
        #expect(doc.pageCount >= 1, "A report always has at least one page.")
        #expect(doc.pageCount >= 2,
                "The populated fixture overflows onto a second page (multi-page pagination).")

        // renderToTempFile writes a real file URL Plan 03 can bind to (WR-05: nil only on write failure).
        let url = try #require(ReportPDFRenderer.renderToTempFile(model: model),
                               "A successful render writes a temp file and returns its URL (never nil).")
        #expect(url.pathExtension == "pdf", "The temp file is a .pdf URL.")
        #expect(FileManager.default.fileExists(atPath: url.path), "The PDF is written to disk.")
        try? FileManager.default.removeItem(at: url)
    }

    // MARK: - RPRT-02: the disclaimer + legend + both source labels are in the extracted text

    @Test func pdfTextContainsDisclaimerAndLegend() throws {
        let model = try populatedModel()
        let doc = try #require(PDFDocument(data: ReportPDFRenderer.render(model: model)))
        let text = try #require(doc.string, "A vector PDF exposes selectable text.")

        // A stable substring of Disclaimer.text (the prominent band renders it VERBATIM).
        #expect(text.contains("not medical advice"),
                "The prominent disclaimer band renders Disclaimer.text verbatim (RPRT-02).")
        // The source legend.
        #expect(text.contains(ReportCopy.legendIntro), "The source legend is present.")
        // BOTH source labels (manual + imported provenance both appear in the fixture).
        #expect(text.contains(ReportCopy.sourceSelfReported),
                "The 'Self-reported' label is present (manual provenance).")
        #expect(text.contains(ReportCopy.sourceAppleHealth),
                "The 'From Apple Health' label is present (imported provenance).")
    }

    // MARK: - T-10-06: a suppressed user renders the off-state, NEVER a phantom cycle

    @Test func suppressedRendersOffStateNotPhantom() throws {
        let model = try suppressedModel()
        #expect(model.cycle == .suppressed, "Fixture sanity: the model is the suppressed off-state.")

        let doc = try #require(PDFDocument(data: ReportPDFRenderer.render(model: model)))
        let text = try #require(doc.string)

        #expect(text.contains(ReportCopy.cycleSuppressed),
                "A suppressed user renders the cycle off-state line (no-phantom-cycle).")
        #expect(!text.contains(ReportCopy.cycleDayPrefix),
                "No fabricated cycle-day is rendered for a suppressed user (T-10-06).")
        #expect(!text.contains(ReportCopy.cycleNextWindowLabel),
                "No fabricated next-window is rendered for a suppressed user.")
    }
}
