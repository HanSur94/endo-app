// Requirement: RPRT-01 / RPRT-03 (Phase 10, D-07/D-09) — the PDFKit preview surface.
//
// A tiny `UIViewRepresentable` wrapping a PDFKit `PDFView` (RESEARCH §Architecture Diagram — the preview
// is first-party PDFKit, never a web view / raster snapshot). It loads a `PDFDocument(url:)` from the
// LOCAL temp-file URL the renderer produced (Plan 02's `renderToTempFile`). NO networking — PDFView reads
// the on-device file only; the no-upload invariant (RPRT-03) is asserted by ReportNoNetworkTests.
import SwiftUI
import PDFKit

/// A SwiftUI wrapper around a PDFKit `PDFView` rendering a local on-device PDF (RPRT-01). `autoScales`
/// keeps the page fit-to-width and zoomable; the document is loaded from a `file://` URL only.
struct PDFKitView: UIViewRepresentable {
    /// The LOCAL temp-file URL the renderer wrote (Plan 02). Never a remote URL — see ReportNoNetworkTests.
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.document = PDFDocument(url: url)
        view.accessibilityIdentifier = "report.preview.pdf"
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        // Reload only when the URL actually changed (a fresh generation produces a new temp file).
        if view.document?.documentURL != url {
            view.document = PDFDocument(url: url)
        }
    }
}
