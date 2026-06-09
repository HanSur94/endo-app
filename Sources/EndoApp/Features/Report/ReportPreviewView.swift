// Requirement: RPRT-01 / RPRT-03 (Phase 10, D-07/D-09) — the on-device PDF preview + explicit share.
//
// Presented after a successful generation. It shows the PDF in a PDFKit preview (`PDFKitView`) and offers
// a SINGLE egress: a toolbar `ShareLink(item: url)` bound to the LOCAL temp-file URL — the system share
// sheet, fired ONLY on an explicit tap (RPRT-03/D-09). There is NO automatic upload anywhere.
//
// PRIVACY (T-10-09 / V8): the temp PDF holds GDPR Art.9 special-category health data in
// `FileManager.temporaryDirectory`. Cleanup is owned by the PRODUCER, not this consumer: the builder
// deletes any prior temp file before each fresh generation (`ReportBuilderView.generate()`), so the
// throwaway file is not leaked. We deliberately do NOT delete on `onDisappear` — the system share sheet
// reads the URL lazily/asynchronously (Mail draft, Files write, AirDrop, Print) and is presented OVER
// this preview, so `onDisappear` can fire while the chosen activity is still reading the file; an eager
// delete there races the share and can export a zero-byte/half-written Art.9 PDF (CR-01). No confirmation
// dialog — preview dismissal is non-destructive to the user's logged data.
import SwiftUI

/// The PDF preview + explicit-share surface (RPRT-01/RPRT-03). Binds to the LOCAL temp-file URL the
/// renderer produced. Cleanup of that file is owned by the builder (pre-generation delete), NOT this view,
/// so the system share sheet can finish reading the URL safely (CR-01).
struct ReportPreviewView: View {
    /// The LOCAL temp-file URL (Plan 02's `renderToTempFile`). The preview reads it and `ShareLink`
    /// shares it. The PRODUCER (`ReportBuilderView`) owns its deletion before the next generation.
    let url: URL

    var body: some View {
        PDFKitView(url: url)
            // The a11y id lives on the SwiftUI layer (PDFKit's PDFView does not reliably surface a UIView
            // accessibilityIdentifier to XCUITest — its internal subviews dominate the hierarchy). The
            // SwiftUI representable wrapper carries the stable `report.preview.pdf` contract the E2E drives.
            .accessibilityIdentifier("report.preview.pdf")
            .accessibilityElement(children: .contain)
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(ReportCopy.builderTitle)
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityIdentifier("report.preview")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    // The ONLY egress: an explicit-tap system share sheet bound to the LOCAL file URL.
                    // No auto-share, no upload (RPRT-03/D-09). Accent reserved for Share (UI-SPEC §Color).
                    ShareLink(item: url) {
                        Label(ReportCopy.shareAction, systemImage: "square.and.arrow.up")
                            .foregroundStyle(Color.accentColor)
                    }
                    .accessibilityIdentifier("report.share")
                }
            }
            // CR-01: NO `onDisappear` delete. The builder removes any prior temp PDF before the next
            // generation, so the Art.9 throwaway file is not leaked — and the system share sheet (which
            // reads the URL lazily, often after this view disappears) can complete safely without racing
            // a premature delete.
    }
}
