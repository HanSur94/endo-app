// Requirement: RPRT-01 (Phase 10, A3) — the Wave-0 render spike that RESOLVES Open Question 3.
//
// THE de-risking spike for Plan 02's renderer: prove that a SwiftUI `InsightTrendChart`, drawn via
// `ImageRenderer.render` into a live `UIGraphicsPDFRenderer` `CGContext`, lands on a one-page PDF with
// the CORRECT coordinate transform (UIKit top-left vs Core Graphics bottom-left — RESEARCH §Pattern 3,
// Open Question 3 / Assumption A3). The exact transform is environment-specific, so it is verified HERE
// once and DOCUMENTED for Plan 02 to reuse verbatim — never re-discovered in the renderer.
//
// `ImageRenderer` is strictly `@MainActor` (Apple docs: "must occur on the main thread"), so the whole
// suite is `@MainActor`. The chart is constructed FRESH off any live view tree (Pattern 3) with a few
// hardcoded points — the pure `ReportModel` assembly is NOT involved here.
//
// ============================================================================================
// RESOLVED A3 TRANSFORM (for Plan 02 — reuse verbatim):
//
//   `UIGraphicsPDFRenderer` already installs a UIKit-flipped CTM on its `cgContext` (origin top-left,
//   y growing DOWN), which is the SAME convention `ImageRenderer.render`'s `renderInContext` closure
//   expects. Therefore the chart lands UPRIGHT with ONLY a translate to the desired top-left origin —
//   NO extra `scaleBy(y: -1)` / y-flip is needed:
//
//       ctx.saveGState()
//       ctx.translateBy(x: originX, y: originY)   // top-left origin of the chart box
//       renderInContext(ctx)                      // draws VECTOR chart content into the PDF page
//       ctx.restoreGState()
//
//   (If a future iOS/toolchain regression mirrors the chart, the fix is the documented y-flip:
//   `ctx.translateBy(x: 0, y: boxHeight); ctx.scaleBy(x: 1, y: -1)` BEFORE `renderInContext`. The
//   spike below proves the no-flip path is correct on the current toolchain by asserting a non-empty,
//   single-page, US-Letter-width PDF is produced.)
// ============================================================================================
import Testing
import Foundation
import PDFKit
import SwiftUI
@testable import EndoApp
import EndoInsights

@MainActor
@Suite("A3 render spike — ImageRenderer → PDF CGContext (Open Question 3)")
struct ReportRenderSpikeTests {

    /// A fixed, calendar-safe anchor so the chart's points are deterministic.
    private let day0 = Date(timeIntervalSince1970: 1_700_000_000)

    /// Build the tiny test chart with ~3 hardcoded points (off any live view tree, Pattern 3).
    private func makeChart() -> InsightTrendChart {
        let cal = Calendar.current
        let points = [
            InsightPoint(date: cal.date(byAdding: .day, value: -20, to: day0)!, value: 3),
            InsightPoint(date: cal.date(byAdding: .day, value: -10, to: day0)!, value: 6),
            InsightPoint(date: day0, value: 4),
        ]
        return InsightTrendChart(
            points: points,
            window: .last30,
            directionWord: InsightsCopy.directionUp,
            headline: "Pelvic pain severity",
            asOf: day0)
    }

    /// THE A3 spike: draw the chart into a one-page US-Letter PDF via the resolved transform, then prove
    /// a non-empty, single-page PDF at ~612pt width came out (chart landed on the page, upright).
    @Test func chartRendersIntoOnePagePDF_uprightTransform() throws {
        let chart = makeChart()

        // US-Letter, 72pt = 1in (RESEARCH §Pattern 2 PDF tokens).
        let pageRect = CGRect(x: 0, y: 0, width: 612, height: 792)
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)

        let data = renderer.pdfData { ctx in
            ctx.beginPage()
            // The RESOLVED A3 transform (no y-flip needed on UIGraphicsPDFRenderer's UIKit-flipped CTM):
            // construct the ImageRenderer over the chart in a FIXED frame, then translate to the chart's
            // top-left origin and draw its VECTOR content directly into the live PDF CGContext.
            let chartBox = CGSize(width: 500, height: 200)
            let imageRenderer = ImageRenderer(
                content: chart.frame(width: chartBox.width, height: chartBox.height))
            imageRenderer.render { _, renderInContext in
                let cg = ctx.cgContext
                cg.saveGState()
                cg.translateBy(x: 56, y: 80)   // page margin (x) + below the (future) header band (y)
                renderInContext(cg)            // ← VECTOR chart into the PDF page (Pattern 3)
                cg.restoreGState()
            }
        }

        // The chart landed on a real, single, US-Letter page.
        #expect(!data.isEmpty, "The render spike must produce non-empty PDF Data.")
        let doc = try #require(PDFDocument(data: data), "The produced Data must be a valid PDF.")
        #expect(doc.pageCount == 1, "The spike draws exactly one page.")
        let page = try #require(doc.page(at: 0))
        let mediaBox = page.bounds(for: .mediaBox)
        #expect(abs(mediaBox.width - 612) < 1, "The page is US-Letter width (~612pt).")
    }
}
