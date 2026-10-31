---
phase: 24-pdf-report
plan: 03
subsystem: report renderer (vector PDF + draw-instruction seam + chart bridge)
status: complete
tags: [report, pdf, vector-text, draw-seam, pagination, chart-reuse, port, jvm-proof]
requires:
  - Plan 24-02 pure ReportModel / SectionSource / ReportRange / ReportCopy + Disclaimer
  - Phase 22-02 ChartSeriesGrouping.runs + ChartCoordinateMapper + ChartPrimitives (pure geometry + DrawScope)
  - :insights DaySnapshot / Insight / InsightPoint / SymptomType; app TrackingCopy + InsightsCopy label vocabularies
provides:
  - com.endoapp.app.report.ReportCanvas (interface) + TextStyle + ReportRect — the vector-only draw seam
  - com.endoapp.app.report.RecordingReportCanvas — pure-Kotlin recording spy (the JVM selectable-text proof)
  - com.endoapp.app.report.PdfReportCanvas — real android.graphics.pdf.PdfDocument-page adapter (drawText/StaticLayout)
  - com.endoapp.app.report.ReportPdfRenderer.layout / render — two-pass VECTOR layout → PdfDocument file
  - com.endoapp.app.report.ReportChartAdapter — Canvas→DrawScope bridge reusing the Phase-22 chart geometry
affects:
  - Plan 24-04/05 share host + builder ViewModel (calls ReportPdfRenderer.render, routes the null failure to an honest error state)
tech-stack:
  added: []
  patterns:
    - Draw-instruction seam (ReportCanvas) with NO drawBitmap-for-text member → a raster text path cannot exist by construction
    - Pure-Kotlin recording spy off android.graphics → the vector/copy/page-count proof runs on the fast JVM lane
    - Two-pass layout driven by ONE routine parameterized only by the ReportCanvas; FIXED asOf=range.end so neither pass reads the clock
    - CanvasDrawScope().draw(Canvas(nativeCanvas)) bridges the PDF page's native Canvas to a Compose DrawScope → the Phase-22 primitives draw unchanged
    - Every fixed string traces to ReportCopy/Disclaimer; enum labels from the audited TrackingCopy/InsightsCopy (no inline literal in the PDF)
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/report/ReportCanvas.kt
    - android/app/src/main/kotlin/com/endoapp/app/report/ReportChartAdapter.kt
    - android/app/src/main/kotlin/com/endoapp/app/report/ReportPdfRenderer.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ReportRenderTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ReportChartHonestyTest.kt
    - android/app/src/androidTest/kotlin/com/endoapp/app/report/ReportPdfSmokeTest.kt
  modified: []
decisions:
  - The A2 CanvasDrawScope bridge RESOLVED and compiled — no raw-Canvas fallback needed; the PDF chart reuses the Phase-22 primitives verbatim
  - Kept the seam's ReportRect + TextStyle PURE (plain Float + an ARGB Int) so RecordingReportCanvas imports no android.graphics and runs on the JVM lane
  - The page-count proof runs the layout twice against fresh RecordingReportCanvas instances and asserts every footer "of m" == the produced page count
  - PdfReportCanvas has a measure-only mode (initialCanvas=null) that still measures via StaticLayout, so pass 1 and pass 2 use the identical measurement routine (no drift)
  - Bridged package SymptomType/SleepQuality/ActivityType → the app's audited TrackingCopy label maps (the Android analog of iOS plainLabel)
metrics:
  duration: ~13m
  completed: 2026-07-10
  tasks: 3
  files: 6
  commits: 3
requirements: [AREL-01]
---

# Phase 24 Plan 03: ReportCanvas seam + ReportPdfRenderer (two-pass VECTOR) + ReportChartAdapter Summary

The doctor-ready PDF render layer, ported from the iOS `ReportPDFRenderer` onto a draw-instruction **seam** that makes "the text is selectable VECTOR, not a raster snapshot" PROVABLE on the fast JVM lane. Because Android ships no first-party PDF text extractor (and Skia subsets fonts + Flate-compresses the stream, so byte-scanning fails), the proof pivots to a `ReportCanvas` interface with NO `drawBitmap`-for-text member: a pure-Kotlin `RecordingReportCanvas` spy asserts the renderer drew the expected copy via `drawText`/`drawWrapped` and — structurally — that a raster text path cannot exist. The real `PdfReportCanvas` draws selectable vector text via `Canvas.drawText`/`StaticLayout` onto `PdfDocument` pages in two passes ("Page n of m"), and the embedded trend chart reuses the Phase-22 geometry byte-for-byte via a `Canvas → DrawScope` bridge, single-ink black.

## What Was Built

### Task 1 — ReportCanvas draw-instruction seam + RecordingReportCanvas spy (`ef947be`)
- `interface ReportCanvas` exposing only vector primitives: `drawText` / `drawWrapped` / `measureWrapped` / `lineHeight` / `drawRect` / `drawLine` / `drawChart` / `newPage` — and DELIBERATELY no `drawBitmap`-for-text member (a raster text path is structurally impossible).
- Pure-Kotlin `ReportRect` + `TextStyle` (plain Float + an ARGB `Int`) so the spy imports no `android.graphics`.
- `RecordingReportCanvas` (pure JVM): records every drawn string in order, counts pages (starts at 1), and returns DETERMINISTIC `measureWrapped`/`lineHeight` (same input → same height) so the renderer's two layout passes measure identically.

### Task 2 — ReportPdfRenderer (two-pass) + PdfReportCanvas + ReportChartAdapter (`e24fee1`)
- `ReportPdfRenderer.layout(model, canvas, totalPages)` ports the iOS layout driver onto the seam: the 8 D-04 sections in order (Overview title + header-meta + the PROMINENT `Disclaimer.text` band, Coverage, Symptoms table + embedded chart, three-state Cycle, Daily factors with null→"Not logged", gated Patterns, Source legend) + a per-page footer ("Self-tracked, not diagnostic" + "Page n of m"). US-Letter geometry (612×792) behind a single named constant (A4 = one line). No-orphan section headings; ensureRoom flips pages stamping the footer.
- `render(model, outDir)`: pass 1 measures against a measure-only `PdfReportCanvas` to learn `m`; pass 2 draws the real `PdfDocument` pages with `m` known; `writeTo`s `cacheDir/reports/endo-report-<yyyyMMdd>-<yyyyMMdd>.pdf` (date stamp only, no health token); on failure logs the error TYPE only and returns `null`. A FIXED `asOf = model.range.end` is threaded so neither pass reads `LocalDate.now()`.
- `PdfReportCanvas`: the real adapter — `drawText` via `Canvas.drawText` (baseline offset by the ascent), `drawWrapped`/`measureWrapped` via `StaticLayout` (VECTOR — never `drawBitmap` for text), `drawRect`/`drawLine` via native `Paint`s, `newPage()` via the `finishPage`/`startPage` callback. A measure-only mode (`initialCanvas = null`) still measures via `StaticLayout`, so both passes share the identical routine.
- `ReportChartAdapter.drawTrendChart`: bridges the page's native `Canvas` → a Compose `DrawScope` via `CanvasDrawScope().draw(Density(1f), Ltr, Canvas(nativeCanvas), Size(...))` and calls the Phase-22 `ChartSeriesGrouping.runs` + a FIXED-0..10 `ChartCoordinateMapper` + `ChartPrimitives.drawRuns`/`drawPoints` in single-ink black — the SAME honest break geometry as on-screen, no second chart implementation.

### Task 3 — recording-seam proof + chart honesty + instrumented smoke (`517d837`)
- `ReportRenderTest` (pure JVM, `RecordingReportCanvas`): a mixed-provenance ≥2-page fixture proves (a) "not medical advice" drawn verbatim as VECTOR text, (b) the exact `ReportCopy` section headings + BOTH "Self-reported" and "from Health Connect" labels, (c) STRUCTURAL anti-forgery — `ReportCanvas` has `drawText`/`drawWrapped` and NO `drawBitmap` member (reflection), (d) two-pass page-count agreement — every footer "of m" equals the produced page count, ≥2 pages.
- `ReportChartHonestyTest` (pure JVM): a gapped fixture → ≥2 runs (a line break, not interpolation); the fixed 0..10 mapper pins severity-0 to the bottom zero line; a logged severity-0 stays a real point.
- `ReportPdfSmokeTest` (instrumented `androidTest`, device-gated): `render` writes a `%PDF-`-magic file with `PdfRenderer.pageCount >= 2`.

## REAL Test Results

All run against the real Android SDK / Gradle.

| Suite | Command | tests / failures / errors |
|-------|---------|---------------------------|
| ReportRenderTest | `:app:testDebugUnitTest --tests "*ReportRenderTest"` | 6 / 0 / 0 |
| ReportChartHonestyTest | `--tests "*ReportChartHonestyTest"` | 3 / 0 / 0 |
| `:app` full unit suite (regression) | `:app:testDebugUnitTest` | 430 / 0 / 0 |
| `:insights:test` (regression) | `:insights:test` | 176 / 0 / 0 |
| `:app:assembleDebug` | `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:compileDebugAndroidTestKotlin` | (smoke compiles for the device lane) | BUILD SUCCESSFUL |
| `:app:lintDebug` | `:app:lintDebug` | BUILD SUCCESSFUL |

Proofs:
- **Selectable VECTOR + exact copy:** the renderer drew `Disclaimer.text` verbatim (contains "not medical advice"), every `ReportCopy` section heading, and both per-source legend labels — all through `drawText`/`drawWrapped`, never a raster path.
- **No raster-text by construction:** `ReportCanvas` reflection shows `drawtext`/`drawwrapped` and NO `*bitmap*` member — a fabricated raster text path cannot compile.
- **Two-pass page count agrees:** pass-1 total == pass-2 produced (≥2 pages), and every footer "Page n of m" carries m == the produced page count (no off-by-one drift).
- **Chart honesty-identical:** the embedded chart reuses `ChartSeriesGrouping.runs` (gap = ≥2 runs = line break) + the fixed 0..10 `ChartCoordinateMapper` (severity-0 at the zero line) + `ChartPrimitives` in single-ink black — the same geometry as the live chart, drawn via the resolved `CanvasDrawScope` bridge.

Version catalog + gradle files untouched — **zero new dependency** (threat T-24-SC "accept" honored).

## Deviations from Plan

None — the plan executed exactly as written. The only planning contingency the plan flagged (Assumption A2 — "if the `CanvasDrawScope`/`Canvas(nativeCanvas)` bridge does not resolve, fall back to a raw-Canvas adapter") did NOT trigger: the bridge compiled and assembled cleanly, so the Phase-22 primitives are reused verbatim with no fallback.

## Threat Model Compliance

- **T-24-06 (info disclosure — render/write failure logging):** `render` logs only `t.javaClass.simpleName` on failure and returns `null` (never a blank/zero-byte PDF, never health content). Mitigated.
- **T-24-07 (tampering — raster-text / fabricated chart):** `ReportCanvas` has no raster-text member (vector-only by construction), pinned by `ReportRenderTest`'s reflection assertion; the chart reuses the Phase-22 gap-honest geometry (one Path per run, fixed 0..10), pinned by `ReportChartHonestyTest`. Mitigated.
- **T-24-08 (info disclosure — PDF filename):** the file name is `endo-report-<yyyyMMdd>-<yyyyMMdd>.pdf` — a date stamp only, no symptom/health token. Mitigated.
- **T-24-SC (supply chain — package installs):** no dependency added; `PdfDocument`/`StaticLayout`/Compose are first-party/present; version catalog unchanged. Accepted.

## Known Stubs

None. `ReportPdfRenderer.render` is fully implemented and produces a real multi-page PDF; the share host + builder ViewModel that call it are intentionally deferred to Plan 24-04/05 (this plan is the render engine they consume). Neither layout pass reads the wall clock.

## Device-Gated Verification

`ReportPdfSmokeTest` (the real `%PDF`-bytes + `PdfRenderer.pageCount >= 2` proof) compiles on this host but must run on an emulator/device (`:app:connectedDebugAndroidTest --tests "*ReportPdfSmokeTest"`) — surfaced to `/gsd-verify-work` as device-gated, mirroring the `DeleteDayCascadeTest` androidTest precedent. The vector/copy/page-count content proof runs fully on the JVM lane meanwhile.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ReportCanvas.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ReportChartAdapter.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ReportPdfRenderer.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ReportRenderTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ReportChartHonestyTest.kt
- FOUND: android/app/src/androidTest/kotlin/com/endoapp/app/report/ReportPdfSmokeTest.kt
- FOUND commit ef947be (Task 1), e24fee1 (Task 2), 517d837 (Task 3)

---
*Phase: 24-pdf-report*
*Completed: 2026-07-10*
