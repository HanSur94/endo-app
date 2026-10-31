# Phase 24: PDF Report - Context

**Gathered:** 2026-07-10
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md §4; the iOS ReportModel/ReportPDFRenderer is the parity source; Phase-22 ChartPrimitives are reused)

<domain>
## Phase Boundary

A user can hand their clinician a clean, honest, vector PDF of a chosen date range — shared on their terms, with no silent upload. Delivers AREL-01, AREL-02: generate a vector PDF report (selectable text, not raster) for a chosen date range with per-source labels (manual vs "from Health Connect") and prominent self-tracked-not-diagnostic framing; share via the system share sheet with no silent upload, report files cleaned from cache; report copy passes the copy audit. Adds a "Export report" entry to the Phase-20 Settings screen. This is the doctor-ready summary — clinician-credible, on-device only.

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §4 (Phase 7) — first-party `android.graphics.pdf.PdfDocument`; the iOS `ReportModel`/`ReportPDFRenderer` (built in iOS Phase 10) is the parity source. Reuse Phases 17–23. Do not re-open. Key decisions:

### Vector PDF (android.graphics.pdf, first-party)
- Render with `android.graphics.pdf.PdfDocument` (first-party — NO new dependency; the version-catalog audit stays green; do NOT adopt TPPDF/iText unless a later phase proves CG-layout math a real time sink, which it isn't here). VECTOR text via `Canvas.drawText` on the PdfDocument page canvas — SELECTABLE text, small file, crisp on print/zoom (NOT a raster snapshot of a view — raster PDFs are explicitly forbidden by the project). Multi-page with auto-pagination (a per-page footer + "Page n of m").
- Reuse the Phase-22 `ChartPrimitives`/`ChartCoordinateMapper` (factored to run off a plain `DrawScope` — but the PDF page uses a raw `android.graphics.Canvas`, not a Compose DrawScope). Decision: factor the chart geometry so BOTH the Compose DrawScope path (Phase 22) AND the raw-Canvas PDF path draw the SAME honest break geometry with a print-ink color (single-ink black). If ChartPrimitives is Compose-DrawScope-coupled, extract the pure geometry (run-segmentation + coordinate mapping — already pure from Phase 22) and add a thin raw-Canvas drawing adapter for the PDF; keep the honesty rules (gap = break, no dual-y) identical to the on-screen chart.

### Report content (parity with iOS ReportModel)
- Port the iOS `ReportModel` (pure, range-scoped, Equatable) → a Kotlin `ReportModel`: honest sections with absence≠0, no phantom cycle, sparse → "not enough data" per section, per-section SOURCE labels (`daySnapshotSources` — manual vs imported/"from Health Connect"). Then `ReportRenderer` (@MainActor equivalent → a renderer that draws the model onto PdfDocument pages): the CoreText-equivalent sections, a prominent `Disclaimer` band atop page 1 (self-tracked, not diagnostic — reuse the Phase-19 Disclaimer text), the embedded trend chart drawn as single-ink-black VECTOR via the shared geometry, per-page footer. Prove with page-count + PDF text-substring tests (NOT visual diffing) — mirror iOS ReportRenderTests/ReportModelTests.
- Range selection: a report builder (preset ranges + a custom range with an inline invalid-range guard) → assemble the ReportModel for the range → render → preview + share. Mirror iOS ReportBuilderView + ReportRangeMapping (custom-range → engine window).

### Range → data
- The ReportModel reads logged (Phase 20) + imported (Phase 21) data uniformly via the Phase-18 `SnapshotProjector`/`daySnapshots()` (consent-gated) for the chosen range. Honest "Not logged"/"not enough data" states. Per-source labels derive from the ImportedSample provenance (any ImportedSample-derived value → "from Health Connect", else manual).

### Share (no silent upload)
- Preview the PDF (a PdfRenderer-backed preview, or an inline confirmation) then share via the system share sheet: `FileProvider` + `ACTION_SEND` with the PDF mime — the user explicitly chooses the target; NO auto-upload, NO network. Clean the temp PDF from cache after sharing (or use cache dir + FileProvider + cleanup). A no-network source-scan invariant (mirror iOS RPRT-03) — the report path imports no networking API. Still notification-free (reminders package sole owner).

### Copy discipline
- Every new user-facing string (report title, section headers, the disclaimer band, source labels, range-picker labels, share/preview chrome, invalid-range message) registered in CopyAudit in the same change; dodge ClaimsPolicy substrings (should/use/her/start/treat/stop/take/here/where). The prominent self-tracked-not-diagnostic framing must be present (a must-contain test, like the iOS Disclaimer). Clinician-credible but NON-diagnostic.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.report`, the PdfDocument page layout math / typography sizing, the preview approach (PdfRenderer bitmap preview vs a confirmation screen), whether the chart-geometry adapter is a new small class or an extension, and the FileProvider cache strategy — Claude's discretion guided by the iOS ReportModel/ReportPDFRenderer + PdfDocument norms. Keep VECTOR selectable text + the honesty rules + no-network non-negotiable.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- iOS Report (Phase 10): `Features/Report/ReportModel.swift` (pure model), `ReportPDFRenderer.swift` (UIGraphicsPDFRenderer, vector, multi-page, selectable text, Disclaimer band, embedded chart), `ReportBuilderView.swift` + `ReportRangeMapping.swift` (range selection), `ReportPreviewView.swift` + `PDFKitView.swift` (preview + ShareLink), `ReportCopy.swift`. Tests: `ReportRenderTests`, `ReportModelTests`, `ReportNoNetworkTests`, `ReportFlowUITests`.

### Established from Phases 17–23 (reuse — DO NOT rebuild)
- Phase 22 `ChartPrimitives`/`ChartCoordinateMapper` + `ChartSeriesGrouping` (pure run-segmentation + coordinate mapping — reuse the geometry for the PDF chart) + the honest-break rules. Phase 18 `SnapshotProjector`/`daySnapshots()` (manual + imported, consent-gated) + ImportedSample provenance. Phase 19 `Disclaimer`/`PrivacyPolicyText` (reuse the disclaimer framing), Bloom Settings screen (add the report entry). The CopyAudit harness, the composition-root injectable-seam threading, the version-catalog audit + boundary-lint, the Phase-21 HealthImportHost / Phase-23 ExportImportHost ActivityResult pattern (for the share intent). Android SDK installed; `./gradlew :app:...` builds real; Robolectric+Compose JVM lane.

### Integration points
- Reads the same day model as Insights/Explore (Phase 22) via SnapshotProjector. The Settings "Export report" entry sits alongside the Phase-21 Health Connect + Phase-23 export/import entries. Reuses the Phase-22 chart geometry. Feeds the clinician hand-off use case. Notification-free.

</code_context>

<specifics>
## Specific Ideas

Signature invariants: (1) VECTOR selectable-text PDF (android.graphics.pdf, drawText — NOT a raster snapshot), proven by a PDF text-substring test not visual diffing; (2) honesty carried into the report — absence≠0, per-section "not enough data", per-source labels (manual vs "from Health Connect"), the same honest chart-break geometry as on-screen; (3) prominent self-tracked-not-diagnostic disclaimer (must-contain test) + no-network source scan + no silent upload (FileProvider + ACTION_SEND, user picks target, cache cleaned). Reuse the Phase-22 chart geometry + Phase-18 day model.

</specifics>

<deferred>
## Deferred Ideas

- Play Store prep (Health apps declaration, Data safety form, store copy) → Phase 25.
- Any clinician-credibility visual/print judgment → device-gated manual verification (mirror the iOS P10 device check).
</deferred>
