// Requirement: RPRT-01 / RPRT-03 (Phase 10, D-07/D-09) — the on-device PDF preview + explicit share.
//
// Presented after a successful generation. It shows the PDF in a PDFKit preview (`PDFKitView`) and offers
// a SINGLE egress: a toolbar `ShareLink(item: url)` bound to the LOCAL temp-file URL — the system share
// sheet, fired ONLY on an explicit tap (RPRT-03/D-09). There is NO automatic upload anywhere.
//
// PRIVACY (T-10-09 / V8): the temp PDF holds GDPR Art.9 special-category health data in
// `FileManager.temporaryDirectory`. We DELETE it on dismiss (`onDisappear`) so it does not linger after
// the user is done. The builder also deletes any prior temp file before a fresh generation (belt + braces:
// both the producer and the consumer clean up). No confirmation dialog — preview dismissal is
// non-destructive to the user's logged data (only the throwaway file is removed).
import SwiftUI

/// The PDF preview + explicit-share surface (RPRT-01/RPRT-03). Binds to the LOCAL temp-file URL the
/// renderer produced; deletes that file on dismiss (the temp PDF is health data — T-10-09 / V8).
struct ReportPreviewView: View {
    /// The LOCAL temp-file URL (Plan 02's `renderToTempFile`). The preview reads it and `ShareLink`
    /// shares it; this view OWNS its deletion on dismiss.
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
            // Delete the temp PDF (Art.9 data) once the user leaves the preview (T-10-09 / V8 / Pitfall 5).
            .onDisappear {
                try? FileManager.default.removeItem(at: url)
            }
    }
}
