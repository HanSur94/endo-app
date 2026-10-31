# Phase 24: PDF Report - Research

**Researched:** 2026-07-10
**Domain:** On-device vector PDF generation (Android `android.graphics.pdf`), report content modeling, no-network share intent — porting the iOS `ReportModel`/`ReportPDFRenderer` (Phase 10) to Kotlin over the Phase-18 day model + Phase-22 chart geometry
**Confidence:** HIGH (first-party API surface, iOS parity source in-repo, reuse seams already built and confirmed reusable)

## Summary

Phase 24 ports the iOS doctor-ready PDF (Phase 10) to Android with every honesty/privacy invariant carried as an enforcing test. The render engine is **first-party `android.graphics.pdf.PdfDocument`** — VECTOR selectable text via `Canvas.drawText`/`StaticLayout` on the page canvas, multi-page auto-pagination with a per-page footer + "Page n of m", written to a cache file and shared via `FileProvider` + `ACTION_SEND`. **No new dependency** (the version-catalog audit stays green): `PdfDocument`, `FileProvider` (androidx.core, already present), and Compose (already present) cover everything. minSdk 26 is fully supported (`PdfDocument` shipped API 19).

The single hard research finding: **the iOS selectable-text proof does not port directly.** iOS asserts substrings via `PDFDocument.string` (PDFKit extracts text). Android's `PdfRenderer` renders **bitmaps only — no text extraction API exists first-party** `[VERIFIED: developer.android.com PdfRenderer.Page]`, and Skia (which backs `PdfDocument`) subsets fonts and Flate-compresses content streams, so **raw-byte scanning for "not medical advice" will not find the literal string** `[ASSUMED]`. The robust JVM-testable strategy is therefore an architectural pivot: keep a **pure `ReportModel`** (all text + honesty rules, trivially unit-tested), and introduce a thin **draw-instruction seam** (`ReportCanvas` interface) so a recording test spy proves the renderer called `drawText("…expected string…")` and **never** `drawBitmap` for text — proving BOTH selectable-vector AND exact copy on the pure `:app` JVM lane, more robustly than iOS's rasterization-dependent extraction. A `%PDF`-bytes + `PdfRenderer.pageCount` smoke test (instrumented/Robolectric) plus a manual "select text in a real viewer" check close the loop.

**Primary recommendation:** Port `ReportModel` as a pure `Equatable`/`data class` value model (over `EndoRepository.daySnapshots()` + a new per-day provenance read); render it through a `ReportPdfRenderer` that draws onto a `ReportCanvas` seam (real adapter = `PdfDocument` page `Canvas`; test adapter = recording spy); reuse the Phase-22 `ChartSeriesGrouping` + `ChartCoordinateMapper` + `ChartPrimitives` for the embedded chart by bridging the page's `android.graphics.Canvas` → a Compose `DrawScope` via `CanvasDrawScope().draw(...)`, drawn in single-ink black; share via `FileProvider`/`ACTION_SEND` with cache cleanup and a `com.endoapp.app.report` no-network source scan.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Vector PDF, first-party `android.graphics.pdf.PdfDocument`** — NO new dependency (version-catalog audit stays green; do NOT adopt TPPDF/iText/PdfBox). VECTOR text via `Canvas.drawText` on the page canvas — SELECTABLE, small file, crisp on print/zoom (NOT a raster snapshot — raster PDFs are explicitly forbidden). Multi-page auto-pagination with a per-page footer + "Page n of m".
- **Reuse the Phase-22 `ChartPrimitives`/`ChartCoordinateMapper`.** The PDF page uses a raw `android.graphics.Canvas`, not a Compose `DrawScope`. Factor the chart geometry so BOTH paths draw the SAME honest break geometry (gap = line break, no dual-y) in single-ink black. Keep honesty rules identical to the on-screen chart.
- **Port the iOS `ReportModel`** → Kotlin: honest sections with absence≠0, no phantom cycle, sparse → "not enough data" per section, per-section SOURCE labels (manual vs imported/"from Health Connect"). Then a renderer drawing onto `PdfDocument` pages: the sections, a prominent `Disclaimer` band atop page 1 (reuse the Phase-19 `Disclaimer` text), the embedded trend chart as single-ink-black VECTOR via the shared geometry, per-page footer. Prove with page-count + PDF text-substring tests (NOT visual diffing).
- **Range selection:** a report builder (preset ranges + a custom range with an inline invalid-range guard) → assemble the `ReportModel` for the range → render → preview + share. Mirror iOS `ReportBuilderView` + `ReportRangeMapping`.
- **Range → data:** read logged (Phase 20) + imported (Phase 21) data uniformly via the Phase-18 `SnapshotProjector`/`daySnapshots()` (consent-gated) for the chosen range. Honest "Not logged"/"not enough data" states. Per-source labels derive from `ImportedSample` provenance (any `ImportedSample`-derived value → "from Health Connect", else manual).
- **Share (no silent upload):** preview the PDF then share via the system share sheet — `FileProvider` + `ACTION_SEND` with the PDF mime. The user explicitly chooses the target; NO auto-upload, NO network. Clean the temp PDF from cache after sharing. A no-network source-scan invariant (mirror iOS RPRT-03). Notification-free (reminders package sole owner).
- **Copy discipline:** every new user-facing string registered in CopyAudit in the same change; dodge ClaimsPolicy substrings (should/use/her/start/treat/stop/take/here/where). The prominent self-tracked-not-diagnostic framing must be present (a must-contain test). Clinician-credible but NON-diagnostic.
- Adds an "Export report" entry to the Phase-20 Settings screen.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.report`.
- The `PdfDocument` page layout math / typography sizing.
- The preview approach (`PdfRenderer` bitmap preview vs a confirmation screen).
- Whether the chart-geometry adapter is a new small class or an extension.
- The FileProvider cache strategy.
- Keep VECTOR selectable text + the honesty rules + no-network non-negotiable.

### Deferred Ideas (OUT OF SCOPE)
- Play Store prep (Health apps declaration, Data safety form, store copy) → Phase 25.
- Any clinician-credibility visual/print judgment → device-gated manual verification (mirror the iOS P10 device check).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AREL-01 | Generate a vector PDF report (selectable text) for a chosen date range with per-source labels and prominent self-tracked-not-diagnostic framing | `PdfDocument` + `Canvas.drawText`/`StaticLayout` vector text (Standard Stack, Pattern 1); pure `ReportModel` port (ReportModel Port map); range selection via `ReportRangeMapping` port; per-source labels from `ImportedSample` provenance; `Disclaimer` band reuse |
| AREL-02 | Share the report via the system share sheet with no silent upload; report files cleaned from cache; report copy passes the copy audit | `FileProvider` + `ACTION_SEND` (Pattern 4); cache-dir strategy + cleanup; `com.endoapp.app.report` no-network source scan (mirrors iOS `ReportNoNetworkTests`); CopyAudit registration of every new string |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Range → honest report content model | Domain / pure Kotlin (`ReportModel`) | Persistence (`EndoRepository.daySnapshots()`) | Mirrors the iOS "pure model" boundary; the ONLY place store reads + the locked engine become an `Equatable` value — decouples "is it honest?" from "does it draw?" |
| Per-day provenance (manual vs Health Connect) | Persistence (`EndoRepository`) | Domain (`SectionSource.aggregate`) | Provenance is a store fact (`ImportedSample` presence), never recomputed from values (Pitfall 1) |
| Vector PDF layout + pagination | Render (`ReportPdfRenderer` over `ReportCanvas`) | Platform (`PdfDocument`) | Pure typographic layer; re-derives no honesty logic |
| Chart geometry (run-segmentation + coordinate mapping) | Domain / pure Kotlin (Phase-22 `ChartSeriesGrouping` + `ChartCoordinateMapper`) | Render (`ChartPrimitives` on a bridged `DrawScope`) | Already pure + renderer-agnostic (confirmed Phase 22-02) — the PDF chart is honesty-identical to on-screen |
| PDF bytes → file | Platform (`PdfDocument.writeTo`) | I/O (app `cacheDir/reports/`) | First-party; file lives in an app-private cache subdir FileProvider can vend |
| Share (user-chosen target, no upload) | Android framework (`FileProvider` + `ACTION_SEND` chooser) | UI host (mirror `ExportImportHost`) | The OS chooser is the only egress; the app never touches the network |
| Range selection UI + invalid-range guard | UI (`ReportBuilderScreen`) | Domain (`ReportRangeMapping`) | Pure mapping value-in/value-out; UI just disables Generate on an invalid custom range |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `android.graphics.pdf.PdfDocument` | framework (API 19+) | Create the multi-page VECTOR PDF; draw selectable text + vector chart on each page's `Canvas` | First-party, zero-dependency, minSdk-26-safe. `Canvas.drawText`/`StaticLayout` emit vector text (selectable, crisp, small file) — the exact iOS `UIGraphicsPDFRenderer` analog `[VERIFIED: developer.android.com/reference/android/graphics/pdf/PdfDocument]` |
| `android.text.StaticLayout` | framework (API 1+; `Builder` API 23+) | Measure + draw WRAPPED multi-line vector text (the Android analog of `NSAttributedString.boundingRect`/`.draw`) | First-party wrapped-text layout; `getHeight()` drives auto-pagination measurement; `draw(canvas)` emits vector glyphs `[CITED: developer.android.com/reference/android/text/StaticLayout]` |
| `android.graphics.pdf.PdfRenderer` | framework (API 21+) | Optional bitmap PREVIEW of a generated page; the smoke-test `pageCount` read | First-party. Renders a page to a `Bitmap` for an in-app preview. **Note: bitmap only — NO text extraction** `[VERIFIED: developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page]` |
| `androidx.core.content.FileProvider` | androidx.core (already on the classpath) | Vend a `content://` URI for the cache PDF to the share chooser without a storage permission | First-party, standard Android share pattern; grants per-share read access via `FLAG_GRANT_READ_URI_PERMISSION` `[CITED: developer.android.com/reference/androidx/core/content/FileProvider]` |
| Phase-22 `ChartSeriesGrouping` + `ChartCoordinateMapper` + `ChartPrimitives` | in-repo | The embedded trend chart's run-segmentation + coordinate mapping + `DrawScope` drawing, reused verbatim in single-ink black | Confirmed pure + reusable off a bare `DrawScope` (22-02-SUMMARY "Reusability confirmation") — keeps the PDF chart honesty-identical to on-screen |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.compose.ui.graphics.drawscope.CanvasDrawScope` | compose (present) | Bridge the PDF page's `android.graphics.Canvas` → a Compose `DrawScope` so `ChartPrimitives` draw the chart unchanged | Only for the embedded chart region. `CanvasDrawScope().draw(density, layoutDirection, androidx.compose.ui.graphics.Canvas(nativeCanvas), size) { with(ChartPrimitives){ … } }` `[CITED: developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/CanvasDrawScope]` |
| `androidx.activity.compose.rememberLauncherForActivityResult` | activity-compose (present) | Host the share `Intent` (and optionally learn when the chooser returns, to clean cache) | Mirror the Phase-21 `HealthImportHost` / Phase-23 `ExportImportHost` ActivityResult pattern for the share launch |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `android.graphics.pdf.PdfDocument` | TPPDF-equivalent / iText / PdfBox (Android ports) | Adds a dependency (fails the zero-dep version-catalog audit), and pulls a large surface for what is a single multi-page text report. iText is AGPL/commercial. **Rejected — locked out by CONTEXT + the audit.** |
| `StaticLayout` for wrapped text | raw `Canvas.drawText(x, y)` per line with manual line-breaking | `drawText` is fine for single-line rows (table cells, footer); `StaticLayout` is needed for the wrapping disclaimer band + any multi-line body so measurement (`getHeight()`) and drawing agree. Use both — single-line via `drawText`, wrapping via `StaticLayout`. |
| Bitmap `PdfRenderer` preview | A plain confirmation screen (no visual preview) | Preview is Claude's discretion. A confirmation screen avoids the `PdfRenderer` native dependency on the JVM test lane; a bitmap preview is nicer UX. Either is acceptable — recommend a lightweight confirmation + share, deferring a bitmap preview if it complicates the JVM lane. |

**Installation:** No package installation. All APIs are framework (`android.graphics.pdf`, `android.text`) or already-present androidx (`core`, `compose`). The `libs.versions.toml` audit surface is unchanged — **this phase adds zero coordinates.**

**Version verification:** `PdfDocument` / `PdfRenderer` / `StaticLayout` / `FileProvider` are framework and androidx-core APIs already resolved by the existing build; no `npm view`/registry step applies. `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36` `[VERIFIED: android/app/build.gradle.kts]` — all four APIs are available at API 26.

## Package Legitimacy Audit

> This phase installs **no external packages** — the render engine is first-party framework (`android.graphics.pdf`, `android.text`), share is first-party androidx (`FileProvider`), and the chart reuse is already-present Compose. The version-catalog zero-dependency audit stays green with no change.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| *(none — no dependency added)* | — | — | — | — | OK | No install; audit unchanged |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                          ┌─────────────────────────────────────────────┐
  User picks range  ──▶   │  ReportBuilderScreen (preset | custom range) │
  (Settings ▸ Export      │  ReportRangeMapping.isValid → Generate gate  │
   report)                └───────────────┬─────────────────────────────┘
                                          │ RangePreset + from/to + asOf(range.end)
                                          ▼
                          ┌─────────────────────────────────────────────┐
   Phase-18 store  ──▶    │  ReportModel.assemble(range, repo, engine)   │  PURE (no PdfDocument,
   daySnapshots()         │   • filter snapshots to EXACT range          │   no Canvas, no Compose,
   + per-day provenance   │   • absence≠0 / no phantom cycle / sparse    │   no networking)
   + locked :insights ─▶  │     → "not enough data" per section          │
   engine (HonestyGate)   │   • per-section SectionSource (manual /      │
                          │     "from Health Connect" / mixed / none)    │
                          └───────────────┬─────────────────────────────┘
                                          │ ReportModel (Equatable data class)
                                          ▼
                          ┌─────────────────────────────────────────────┐
                          │  ReportPdfRenderer.render(model) → File      │
                          │   two-pass layout on a Cursor:               │
                          │   pass 1 (measure) → total page count (m)    │
                          │   pass 2 (draw) → footer "Page n of m"       │
                          │   draws onto a  ReportCanvas  seam ───────────┐
                          └───────────────┬──────────────────────────────┼──▶ RecordingReportCanvas
                                          │                               │    (TEST spy: captures
                        real adapter      │        PdfReportCanvas         │     drawText(str), asserts
                                          ▼        (android.graphics.      │     NO drawBitmap for text)
                          ┌───────────────────────────┐   pdf.PdfDocument │
                          │ PdfDocument                │   page.canvas)    │
                          │  startPage(PageInfo)       │◀──────────────────┘
                          │  page.canvas.drawText /    │
                          │   StaticLayout.draw (VECTOR selectable text)  │
                          │  embedded chart region:                       │
                          │   CanvasDrawScope().draw(Canvas(nativeCanvas)) │
                          │    → with(ChartPrimitives){drawRuns/drawPoints}│  single-ink black,
                          │      over Phase-22 ChartSeriesGrouping.runs    │  gap=break, no dual-y
                          │  finishPage → writeTo(cacheDir/reports/*.pdf)  │
                          └───────────────┬───────────────────────────────┘
                                          │ File in app cacheDir/reports/
                                          ▼
                          ┌─────────────────────────────────────────────┐
                          │  ReportShareHost (mirror ExportImportHost)   │
                          │   FileProvider.getUriForFile(authority,file) │
                          │   Intent(ACTION_SEND, "application/pdf")     │
                          │    + FLAG_GRANT_READ_URI_PERMISSION          │
                          │   startActivity(createChooser(...))          │  USER picks target.
                          │   on return → delete cache file(s)           │  NO network, NO INTERNET perm.
                          └─────────────────────────────────────────────┘
```

### Component Responsibilities
| Component | File (suggested) | Responsibility |
|-----------|------------------|----------------|
| `ReportModel` (+ section types, `SectionSource`) | `report/ReportModel.kt` | PURE honesty + source-label boundary; `assemble(range, repository, engine, asOf)` |
| `ReportRangeMapping` (+ `RangePreset`) | `report/ReportRangeMapping.kt` | preset→`DateInterval`-equivalent, engine-windows-in-range, `isValid` guard |
| `ReportCopy` | `content/ReportCopy.kt` | every fixed string; registered in `CopyAuditRegistry.sources` |
| `ReportCanvas` (interface) + `PdfReportCanvas` + `RecordingReportCanvas` | `report/ReportCanvas.kt` | the draw-instruction seam (drawText/drawWrapped/drawLine/drawRect/drawChart) — real vs recording |
| `ReportPdfRenderer` | `report/ReportPdfRenderer.kt` | two-pass layout/pagination; draws the model onto a `ReportCanvas`; produces the `PdfDocument` file |
| `ReportChartAdapter` | `report/ReportChartAdapter.kt` | bridges the page `Canvas` → `DrawScope`, draws the reused `ChartPrimitives` in single-ink black |
| `ReportBuilderScreen` + `ReportBuilderViewModel` | `report/ui/…` | range picker, invalid-range guard, generate → share |
| `ReportShareHost` | `report/ui/ReportShareHost.kt` | `FileProvider` + `ACTION_SEND` chooser + cache cleanup (mirror `ExportImportHost`) |

### Pattern 1: First-party vector PDF with `PdfDocument`
**What:** Build the document, one `PageInfo`/`Page` per page, draw vector text + shapes on `page.canvas`, `writeTo` a file, `close`.
**When to use:** The whole report render.
**Example:**
```kotlin
// Source: developer.android.com/reference/android/graphics/pdf/PdfDocument (API surface)
// US-Letter at 72pt = 1in → 612 x 792 points. (A4 = 595 x 842 points.) iOS Phase 10 used US-Letter.
private const val PAGE_W = 612
private const val PAGE_H = 792

fun render(model: ReportModel, outDir: File): File {
    val doc = PdfDocument()
    // Two-pass: pass 1 counts pages so the footer can print "Page n of m".
    val totalPages = layoutPass(model, canvas = MeasuringReportCanvas(), totalPages = 0)

    var pageNum = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
    val real = PdfReportCanvas(
        canvas = page.canvas,
        onNewPage = {                       // pagination callback from the Cursor
            doc.finishPage(page)
            pageNum += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            page.canvas
        }
    )
    layoutPass(model, canvas = real, totalPages = totalPages)
    doc.finishPage(page)

    val file = File(outDir, "endo-report-${fileStamp(model.range)}.pdf")
    file.outputStream().use { doc.writeTo(it) }   // writeTo(OutputStream)
    doc.close()
    return file
}
```
Text is drawn vector: single-line rows via `canvas.drawText(text, x, y, paint)`; wrapping blocks via
`StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width).build().draw(canvas)` after
`canvas.translate(x, y)`. **Never** `canvas.drawBitmap(...)` for text — that would raster it (forbidden).

### Pattern 2: Two-pass pagination + no-orphan cursor (port iOS `layoutPass`/`Cursor`/`ensureRoom`)
**What:** A mutable `Cursor(y, page)` threaded through the layout; `ensureRoom(height)` flips the page (stamping the footer) when content would cross the bottom margin. Pass 1 measures with a no-op canvas to learn `m`; pass 2 draws with `m` known so the footer reads "Page n of m".
**When to use:** All section layout.
**Why two passes:** the footer's "of m" needs the total before the first page is stamped — the iOS renderer resolves this exactly this way (`ReportPDFRenderer.render` → `layoutPass(context: nil)` then real). Measurement uses `StaticLayout.getHeight()` (wrapping) / `Paint.getFontMetrics()` (single line) so both passes are byte-identical. Thread a FIXED `asOf = range.end` so neither pass reads the wall clock (iOS WR-06 — prevents a midnight page-count drift).

### Pattern 3: Reuse the Phase-22 chart geometry on the raw PDF Canvas
**What:** The embedded trend chart draws the SAME run-segmented, gap-honest geometry as the live chart, in single-ink black.
**When to use:** Section 3 (Symptoms & pain) embedded chart.
**Example:**
```kotlin
// Bridge the PDF page's android.graphics.Canvas → a Compose DrawScope so ChartPrimitives run unchanged.
// Source: androidx.compose.ui.graphics.drawscope.CanvasDrawScope (Android reference)
fun drawTrendChart(nativeCanvas: android.graphics.Canvas, box: Rect, points: List<InsightPoint>) {
    val runs = ChartSeriesGrouping.runs(points)                       // pure, Phase 22 — gap = break
    val mapper = ChartCoordinateMapper(                               // pure, fixed 0..10 domain (no dual-y)
        startDate = points.first().date, endDate = points.last().date,
        valueMin = 0.0, valueMax = 10.0,
        width = box.width().toFloat(), height = box.height().toFloat(),
    )
    nativeCanvas.save(); nativeCanvas.translate(box.left.toFloat(), box.top.toFloat())
    CanvasDrawScope().draw(
        density = Density(1f), layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(nativeCanvas),
        size = Size(box.width().toFloat(), box.height().toFloat()),
    ) {
        with(ChartPrimitives) {
            drawRuns(runs, mapper, strokeColor = Color.Black, strokeWidth = 1.5f)   // single ink
            drawPoints(points, mapper, color = Color.Black, radius = 2f)
        }
    }
    nativeCanvas.restore()
}
```
No `drawScrubRule`/`drawHighlight` (static report). Fixed `0..10` domain = no silent normalization; one
`Path` per run = gaps stay breaks. The break geometry is byte-identical to on-screen (22-02 confirmed the
primitives hold no Compose-runtime state).

### Pattern 4: No-network share via `FileProvider` + `ACTION_SEND`
**What:** Write the PDF to an app-private cache subdir, vend it as a `content://` URI, hand it to the OS chooser, clean up after.
**When to use:** The share step (AREL-02).
**Example:**
```kotlin
// Manifest (inside <application>):
// <provider
//     android:name="androidx.core.content.FileProvider"
//     android:authorities="${applicationId}.fileprovider"
//     android:exported="false"
//     android:grantUriPermissions="true">
//   <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
//              android:resource="@xml/file_paths" />
// </provider>
//
// res/xml/file_paths.xml:
// <paths><cache-path name="reports" path="reports/" /></paths>

val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
val send = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(send, /* title */ null))
// NO INTERNET permission is required or declared. Delete cacheDir/reports/*.pdf after the chooser returns.
```

### Anti-Patterns to Avoid
- **Rastering text** (`drawBitmap` of a rendered view, or `PdfRenderer`-bitmap-into-PdfDocument): produces a blurry, unselectable, huge PDF. Explicitly forbidden. The recording-canvas test asserts NO `drawBitmap` for text content.
- **Asserting selectable text by scanning raw PDF bytes for the string:** Skia subsets fonts + Flate-compresses streams, so the literal string is not in the bytes `[ASSUMED]`. Use the recording seam instead (Pitfall 1).
- **Re-deriving cycle/insight/honesty in the renderer:** all honesty lives in the pure `ReportModel`; the renderer is typographic only (iOS invariant).
- **Reading `.now`/`LocalDate.now()` inside the layout passes:** breaks the two-pass page count across midnight. Thread `asOf = range.end`.
- **A phantom cycle for a suppressed user:** the `CycleSection` is a three-state enum; suppressed → off-state line, never a fabricated cycle day/window (iOS T-10-06).
- **Reaching for `INTERNET`, `okhttp`, `java.net.URL`, `HttpURLConnection`, `Socket` anywhere under `com.endoapp.app.report`:** the no-network scan fails the build.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Vector PDF emission | A custom PDF byte writer / object-stream serializer | `android.graphics.pdf.PdfDocument` | First-party, correct xref/trailer, font embedding, Flate compression — hand-rolling PDF structure is a multi-week trap |
| Wrapped-text measurement + drawing | Manual glyph-width line-breaking | `android.text.StaticLayout` | Handles word-wrap, bidi, font metrics; `getHeight()` measurement matches the draw exactly (pagination correctness) |
| Sharing a file to another app | Copying to external storage + a storage permission | `FileProvider` + `ACTION_SEND` | Zero storage permission, per-share scoped grant, OS-owned chooser (no silent upload) |
| Chart run-segmentation + coordinate mapping | A second PDF-only chart implementation | Phase-22 `ChartSeriesGrouping` + `ChartCoordinateMapper` + `ChartPrimitives` | Guarantees the PDF chart is honesty-identical to on-screen; the geometry is already pure + tested |
| Range preset → interval + engine-window fitting | Ad-hoc date math in the ViewModel | Port `ReportRangeMapping` | DST-safe `Calendar`/`LocalDate` math; the "windows ⊆ range" honesty rule is already worked out on iOS |
| Report honesty rules (absence≠0, sparse gate, no phantom cycle) | Inline logic in the renderer | Port the pure `ReportModel` | The renderer must stay typographic; honesty is unit-tested on the model in isolation |

**Key insight:** Every deceptively-hard piece here (PDF structure, text layout, share security, chart honesty) already has a first-party or in-repo owner. The phase's real work is porting the iOS *model + copy + range mapping* faithfully and building the *draw-instruction seam* that makes selectable-vector text provable on the JVM.

## Runtime State Inventory

> Not a rename/refactor/migration phase — this is a greenfield feature (new `com.endoapp.app.report` package). No pre-existing runtime state is renamed or migrated. One net-new manifest entry is added (the `FileProvider` `<provider>`), and one net-new cache subdir (`cacheDir/reports/`) is created and self-cleaned. **None — verified by: the phase creates a new package + new manifest provider + new cache dir; it stores no keys, touches no existing datastore, and registers no OS-level state.**

## Common Pitfalls

### Pitfall 1: "Prove the text is selectable" does not port from iOS
**What goes wrong:** The team copies the iOS approach (`PDFDocument(data:).string` → assert substrings) and finds Android has no equivalent — `PdfRenderer` returns only a `Bitmap` `[VERIFIED: developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page]`. They then try scanning the written PDF bytes for `"not medical advice"` and it fails, because Skia subsets fonts and Flate-compresses the content stream so the literal ASCII is absent `[ASSUMED]`.
**Why it happens:** iOS PDFKit ships a text-extraction API; Android's first-party PDF stack is render-only. There is no first-party JVM PDF text extractor, and PdfBox/iText are forbidden dependencies.
**How to avoid:** Pivot the proof to a **draw-instruction seam**. Draw through a `ReportCanvas` interface; in tests use a `RecordingReportCanvas` spy that records every `drawText`/`drawWrapped(text)` call. Assert (a) the expected substrings were drawn as TEXT, and (b) `drawBitmap` was called ZERO times for text content. This proves selectable-vector AND exact copy on the pure `:app` JVM lane — strictly more robust than iOS's rasterization-dependent extraction. Add one instrumented/manual check that a real viewer can select the text (platform guarantee, verified once).
**Warning signs:** A test that inflates/greps PDF bytes; a `Robolectric` test that calls `PdfDocument.writeTo` and asserts on the byte array content.

### Pitfall 2: `PdfDocument`/`PdfRenderer`/`Canvas.drawText` are native → "not mocked" on the plain JVM unit lane
**What goes wrong:** A `testDebugUnitTest` that constructs a real `PdfDocument` or calls `page.canvas.drawText` throws `RuntimeException: Method … not mocked` (the stubbed `android.jar`), or a Robolectric test finds `writeTo` unimplemented.
**Why it happens:** The `android.graphics.pdf` + `Canvas` text APIs are backed by native Skia, absent from the unit-test `android.jar`.
**How to avoid:** Keep the **content + vector proof entirely off the seam** — the `RecordingReportCanvas` is a pure Kotlin object (no `android.graphics` calls), so the pagination + drawText assertions run on the fast JVM lane with no Robolectric. Confine anything touching the real `PdfDocument`/`PdfRenderer` (the `%PDF`-bytes + `pageCount` smoke) to an **instrumented `androidTest`** (or a Robolectric-shadowed test if the shadow supports `writeTo`; treat that as device-gated if it doesn't).
**Warning signs:** "Method not mocked" in a unit test; a green Robolectric PDF test that never actually asserts bytes.

### Pitfall 3: Two-pass page count drifts (footer says "Page 2 of 2" on a 3-page doc)
**What goes wrong:** Pass 1 measures differently than pass 2 draws, so `m` is wrong, or a section reads `LocalDate.now()` and the two passes straddle midnight.
**Why it happens:** Measurement (`StaticLayout.getHeight()`) and drawing must use identical text/paint/width; and any wall-clock read makes the passes diverge.
**How to avoid:** Use ONE layout routine parameterized by the `ReportCanvas` (measuring vs drawing); thread a FIXED `asOf = model.range.end` so nothing reads `.now`. A page-count test asserts the recording pass and the reported footer total agree.
**Warning signs:** Off-by-one footer totals; flaky page counts near midnight.

### Pitfall 4: Provenance recomputed from values instead of read from the store
**What goes wrong:** The section source label ("From Health Connect" vs "Self-reported") is inferred from the value shape, mislabeling manual data that happens to match an imported range, or vice-versa.
**Why it happens:** Skipping the store's provenance record (`ImportedSample` presence) as the single source of truth.
**How to avoid:** Add an `EndoRepository` per-day provenance read (the Android analog of iOS `daySnapshotSources(on:)`) that returns per-field `ValueSource?` derived from `ImportedSample`; aggregate via `SectionSource.aggregate` exactly as iOS (empty→none/no badge, both→mixed, else self-reported/health). NEVER recompute from values (iOS Pitfall 1). **This read may not exist yet — the Phase-18 `SnapshotProjector` merges provenance into the `DaySnapshot` but does not expose a per-day source map; plan a small repository addition.**
**Warning signs:** A badge with no matching legend line; a label derived from `snapshot.sleepHours != null`.

### Pitfall 5: The empty/sparse report reads as a fabricated normal report
**What goes wrong:** A range with 0–2 logged days renders a symptom trend / cycle estimate anyway.
**Why it happens:** Missing the honesty gates the model encodes.
**How to avoid:** Port the model's gates verbatim — symptoms `hasEnoughData = busiestSymptomDays >= 3` (kept EQUAL to the engine's `minSample(.trend)`), cycle three-state, insights filter `!isInsufficiencyNotice`, lifestyle `nil → "Not logged"`. Test each empty state.
**Warning signs:** A one-point "trend"; a cycle day for a two-day range.

## Code Examples

Verified/derived patterns (framework API + iOS parity source in-repo):

### Draw-instruction seam (the selectable-text proof enabler)
```kotlin
// Renderer draws through this; NEVER touches android.graphics.Canvas directly for content.
interface ReportCanvas {
    fun drawText(text: String, x: Float, y: Float, style: TextStyle)      // single line (vector)
    fun drawWrapped(text: String, x: Float, y: Float, width: Float, style: TextStyle): Float // returns height
    fun drawRect(rect: RectF, fill: Boolean)                              // band / table header bg
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)              // hairline
    fun drawChart(box: RectF, points: List<InsightPoint>)                 // single-ink black vector chart
    fun measureWrapped(text: String, width: Float, style: TextStyle): Float
    fun newPage()                                                          // pagination
    // NOTE: there is deliberately NO drawBitmap(...) for text — a raster path cannot exist by construction.
}

// TEST spy — pure Kotlin, runs on the fast :app JVM lane, no android.graphics:
class RecordingReportCanvas : ReportCanvas {
    val texts = mutableListOf<String>()
    var pages = 1
    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) { texts += text }
    override fun drawWrapped(text: String, x: Float, y: Float, width: Float, style: TextStyle): Float {
        texts += text; return 12f
    }
    override fun newPage() { pages += 1 }
    // …measure returns deterministic heights; drawRect/drawLine/drawChart are no-ops or counters.
}
// Assertion (pure JVM):
//   val rec = RecordingReportCanvas(); ReportPdfRenderer.layout(model, rec, totalPages = 0)
//   assertTrue(rec.texts.any { it.contains("not medical advice") })   // Disclaimer band (must-contain)
//   assertTrue(rec.texts.contains(ReportCopy.legendSelfReported))
//   assertTrue(rec.texts.contains(ReportCopy.legendHealthConnect))
//   // and a structural test: ReportCanvas has no drawBitmap member (vector-only by construction).
```

### `SectionSource` aggregation (port verbatim; "Apple Health" → "from Health Connect")
```kotlin
// Source: iOS Sources/EndoApp/Features/Report/ReportModel.swift (SectionSource.aggregate)
enum class SectionSource { SelfReported, HealthConnect, Mixed, None;
  companion object {
    fun aggregate(sources: List<ValueSource?>): SectionSource {
        val present = sources.filterNotNull()
        if (present.isEmpty()) return None
        val manual = present.contains(ValueSource.Manual)
        val imported = present.contains(ValueSource.Imported)   // any ImportedSample-derived value
        if (manual && imported) return Mixed
        return if (manual) SelfReported else HealthConnect
    }
  }
}
```

### No-network source scan (port `ReportNoNetworkTests`)
```kotlin
// Mirrors Tests/EndoAppTests/ReportNoNetworkTests.swift, pointed at the Kotlin report package.
// Scan every .kt under com.endoapp.app.report, strip comment lines, assert none contains a networking token.
val forbidden = listOf("java.net", "okhttp", "HttpURLConnection", "URLConnection", "Socket", "INTERNET", "URL(")
// Anti-vacuity: assert the scanned file set is NON-EMPTY. Mirror the existing HealthSeparationTest /
// NotificationSeparationTest structural-scan pattern already in the repo (Phase 21/22).
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| iOS `UIGraphicsPDFRenderer` + `NSAttributedString.draw` | Android `PdfDocument` + `Canvas.drawText`/`StaticLayout.draw` | This port | Same vector-text outcome; different API names, same two-pass layout |
| iOS `PDFDocument.string` text extraction to prove selectability | Android draw-instruction recording seam (no first-party extractor exists) | This port | The proof pivots from output-extraction to draw-call capture (more robust) |
| iOS `ImageRenderer.render` for the vector chart embed | `CanvasDrawScope().draw(Canvas(nativeCanvas))` bridging to `ChartPrimitives` | This port | Reuses the live chart's exact geometry in the PDF |
| iOS "From Apple Health" label | "from Health Connect" label | This port | Android provenance is Health Connect, not HealthKit |

**Deprecated/outdated:**
- `PdfDocument.PageInfo` note: `setContentRect` is optional; default content rect = full page. Not deprecated, just not needed here.
- Nothing in this stack is deprecated at compileSdk 36.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Skia-backed `PdfDocument` subsets fonts + Flate-compresses content streams, so the literal report strings are NOT present as contiguous bytes in the written PDF | Pitfall 1, Summary | If wrong, a byte-scan token test would work — but the recording-seam strategy is still the recommended, more-robust proof, so low downside |
| A2 | `androidx.compose.ui.graphics.Canvas(nativeCanvas)` + `CanvasDrawScope().draw(...)` correctly bridges the PDF page's `android.graphics.Canvas` to a `DrawScope` for `ChartPrimitives` | Pattern 3 | If the exact factory/signature differs, fall back to a small pure raw-`Canvas` adapter using `ChartCoordinateMapper` + `canvas.drawPath`/`drawCircle` (CONTEXT explicitly allows this) — chart still honesty-identical |
| A3 | The Phase-18 `SnapshotProjector`/`EndoRepository` does NOT yet expose a per-day `ValueSource?` provenance map; a small repository read must be added for per-section source labels | Pitfall 4, ReportModel Port | If a provenance read already exists, the addition is skipped; if missing and not added, per-source labels can't be honest → plan must include it |
| A4 | Real `PdfDocument.writeTo` / `PdfRenderer.pageCount` are not available on the plain JVM unit lane (native), so the bytes/pageCount smoke is instrumented/Robolectric-gated | Pitfall 2, Validation Architecture | If Robolectric shadows `writeTo`, the smoke can run on the JVM lane — a bonus, not a blocker |
| A5 | US-Letter (612×792 pt) is the right page size to match the shipped iOS report; A4 (595×842) is the alternative if a EU-clinician default is preferred | Pattern 1 | Cosmetic; either is a valid vector page. iOS shipped US-Letter — match it unless the team wants A4 for the German-inspired audience (discuss). |
| A6 | `ACTION_SEND` with a `FileProvider` `content://` URI + `FLAG_GRANT_READ_URI_PERMISSION` needs no `INTERNET` permission and no storage permission | Pattern 4, Summary | Very low — this is the canonical Android share pattern; the app already ships zero-INTERNET |

**If this table is empty:** it is not — A1–A6 need confirmation. A1 and A2 are the load-bearing technical assumptions; both have documented fallbacks (recording seam is robust regardless of A1; a raw-Canvas adapter covers A2). A3 is a concrete planning input (add a provenance read). A5 is a quick product choice.

## Open Questions

1. **Page size: US-Letter vs A4?**
   - What we know: iOS shipped US-Letter (612×792 pt); the app's inspiration is a German DiGA and the audience is global.
   - What's unclear: whether clinicians in the target markets expect A4.
   - Recommendation: default to **US-Letter to match iOS parity**; make the constant a single named value so switching to A4 (595×842) is one line. Not worth blocking.

2. **Does an `EndoRepository` per-day provenance read already exist (A3)?**
   - What we know: Phase-18 `SnapshotProjector` folds provenance into the merge (manual-wins/nil-honest) but the summary shows no per-field source map surfaced to callers; `hasImportedData()` exists (Phase 21) but is boolean.
   - What's unclear: whether a per-day `ValueSource?`-per-field read is available.
   - Recommendation: **plan a small consent-gated `daySnapshotSources(range)` addition** mirroring iOS `DaySnapshotSource`; derive each field's source from `ImportedSample` presence for that day. Verify against the current `EndoRepository` at plan time.

3. **Preview: bitmap `PdfRenderer` vs confirmation screen?**
   - What we know: CONTEXT leaves this to Claude's discretion; `PdfRenderer` is native (JVM-lane-hostile).
   - Recommendation: ship a **confirmation screen** (title, range, "Share PDF") first — keeps the flow fully JVM-testable off seams like `ExportImportScreen`; add a bitmap preview later only if desired (device-gated).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `android.graphics.pdf.PdfDocument` | vector PDF render | ✓ (framework, API 19+) | minSdk 26 | — |
| `android.text.StaticLayout` | wrapped vector text | ✓ (framework) | minSdk 26 | `Canvas.drawText` per manually-broken line |
| `android.graphics.pdf.PdfRenderer` | optional preview + smoke pageCount | ✓ (framework, API 21+) | minSdk 26 | confirmation screen (no bitmap preview) |
| `androidx.core.content.FileProvider` | share URI | ✓ (androidx.core, on classpath) | present | — |
| Compose `CanvasDrawScope` | chart bridge | ✓ (compose, on classpath) | present | raw-`Canvas` adapter via `ChartCoordinateMapper` |
| Phase-22 chart geometry | embedded chart | ✓ (in-repo) | Phase 22 | — |
| Android SDK / `./gradlew :app:…` | build + JVM tests | ✓ (confirmed prior phases) | SDK 36 | — |
| Emulator/device for instrumented PDF-bytes smoke | AREL-01 smoke | ✗ on JVM lane | — | recording-seam JVM proof covers content; smoke is `androidTest`/device-gated |

**Missing dependencies with no fallback:** none — every render/share API is first-party or already present.
**Missing dependencies with fallback:** the real-`PdfDocument`-bytes / `PdfRenderer.pageCount` smoke needs an emulator/device (instrumented) — the pure recording-seam tests cover content + pagination on the JVM lane meanwhile.

## Validation Architecture

> `.planning/config.json` was not found to explicitly disable `nyquist_validation`; the prior phases all shipped enforcing tests, so this section is included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + Robolectric (Compose JVM lane) for UI/off-seam; pure JUnit4 for model/renderer-seam/scan tests; `androidTest` (instrumented) for the real-PDF smoke `[VERIFIED: prior-phase SUMMARYs — 22/23 use exactly this]` |
| Config file | `android/app/build.gradle.kts` (existing test setup; no new config) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*Report*"` |
| Full suite command | `./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AREL-01 | `ReportModel.assemble` honesty: absence≠0, sparse→"not enough data", no phantom cycle (suppressed→off-state), range scoping, per-section `SectionSource` | unit (pure JVM) | `pytest`-equiv → `./gradlew :app:testDebugUnitTest --tests "*ReportModelTest"` | ❌ Wave 0 |
| AREL-01 | Selectable-VECTOR proof: renderer draws expected strings via `drawText`/`drawWrapped`, and `ReportCanvas` has NO `drawBitmap`-for-text path | unit (recording seam, pure JVM) | `… --tests "*ReportRenderTest"` | ❌ Wave 0 |
| AREL-01 | Disclaimer must-contain: the prominent band's text contains "not medical advice" (reuse `Disclaimer.text`) | unit (recording seam) | `… --tests "*ReportRenderTest"` | ❌ Wave 0 |
| AREL-01 | Per-source labels present: "Self-reported" AND "from Health Connect" render for a mixed fixture | unit (recording seam) | `… --tests "*ReportRenderTest"` | ❌ Wave 0 |
| AREL-01 | Page count: reported footer total == recording pass page count (two-pass agreement) | unit (recording seam) | `… --tests "*ReportRenderTest"` | ❌ Wave 0 |
| AREL-01 | Range mapping: preset→interval, custom `isValid` guard (to<from invalid), engine-windows-in-range | unit (pure JVM) | `… --tests "*ReportRangeMappingTest"` | ❌ Wave 0 |
| AREL-01 | Real PDF smoke: `writeTo` produces bytes starting `%PDF-`/ending `%%EOF`; `PdfRenderer.pageCount >= 2` for the populated fixture | instrumented (androidTest) | `./gradlew :app:connectedDebugAndroidTest --tests "*ReportPdfSmokeTest"` | ❌ Wave 0 (device-gated) |
| AREL-02 | No-network scan: no networking token under `com.endoapp.app.report` (comment-stripped, non-vacuous) | unit (structural scan) | `… --tests "*ReportNoNetworkTest"` | ❌ Wave 0 |
| AREL-02 | Manifest: `FileProvider` `<provider>` present; still NO `INTERNET` permission (extend `ManifestPermissionsTest`) | unit (Robolectric/DOM) | `… --tests "*ManifestPermissionsTest"` | ✅ exists — extend |
| AREL-02 | Share host builds `ACTION_SEND` + `application/pdf` + `FLAG_GRANT_READ_URI_PERMISSION`; cache cleaned after | Robolectric (off seams) | `… --tests "*ReportShareHostTest"` or intent assertion | ❌ Wave 0 |
| AREL-02 | Copy audit: every new `ReportCopy` string registered + substring-clean (ClaimsPolicy) | unit | `… --tests "*CopyAuditTest"` | ✅ exists — extend registry |
| AREL-02 | Report package imports no notification/alarm API (reminders sole owner) | unit (structural scan) | `… --tests "*NotificationSeparationTest"` | ✅ exists — auto-covers new package |
| AREL-01/02 | Builder flow: invalid custom range disables Generate; generate→share path off seams | Robolectric + Compose | `… --tests "*ReportBuilderScreenTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*Report*" --tests "*CopyAuditTest"`
- **Per wave merge:** `./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- **Phase gate:** Full suite green + the instrumented `ReportPdfSmokeTest` run on an emulator (or explicitly surfaced as device-gated to `/gsd-verify-work`) before completion.

### Wave 0 Gaps
- [ ] `report/ReportModelTest.kt` — covers AREL-01 honesty (port iOS `ReportModelTests`)
- [ ] `report/ReportRenderTest.kt` — recording-seam vector/copy/page-count proof (the iOS `ReportRenderTests` analog, re-architected)
- [ ] `report/ReportRangeMappingTest.kt` — covers AREL-01 range mapping (port iOS mapping tests)
- [ ] `report/ReportNoNetworkTest.kt` — covers AREL-02 no-network scan (port `ReportNoNetworkTests`)
- [ ] `report/ReportShareHostTest.kt` — covers AREL-02 share intent + cache cleanup
- [ ] `report/ui/ReportBuilderScreenTest.kt` — covers the builder flow off seams
- [ ] `androidTest/report/ReportPdfSmokeTest.kt` — real `%PDF`-bytes + `pageCount` (instrumented; device-gated)
- [ ] Extend `ManifestPermissionsTest` (assert `FileProvider` present, still no `INTERNET`) and `CopyAuditRegistry.sources` (add `ReportCopy`)
- [ ] `res/xml/file_paths.xml` + manifest `<provider>` — infrastructure, not a test but a Wave-0 artifact

## Security Domain

> `security_enforcement` not explicitly `false` in config → included. This phase handles GDPR Art. 9 special-category health data leaving the honesty boundary as a shareable document, so the security surface is real despite zero network.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture / data flow | yes | The report is on-device only; the ONLY egress is the user-initiated `ACTION_SEND` chooser. No-network source scan enforces it structurally. |
| V4 Access Control | yes | `FileProvider` with `android:exported="false"` + per-share `FLAG_GRANT_READ_URI_PERMISSION` (scoped, revocable). No world-readable file, no storage permission. |
| V5 Input Validation | yes | Custom-range guard (`isValid`); defensive enum decodes inherited from the pure model/projector; free text never crosses (Phase-18 invariant — the report reads `DaySnapshot`, not prose). |
| V6 Cryptography | no | The report is a shareable clinician document (not an encrypted backup — that's Phase 23). No crypto here by design. |
| V7 Errors / Logging | yes | On a write/render failure, log the error TYPE only, never health content (iOS T-10-07). Surface an honest error state; never share a zero-byte/blank PDF. |
| V8 Data Protection | yes | Cache PDF in app-private `cacheDir/reports/`; delete after share; excluded from Auto Backup posture (cache is not backed up). Filename carries NO health content (date stamp only, mirror Phase-23 `endo-export-<date>`). |

### Known Threat Patterns for {Android on-device PDF + share}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Silent upload of health data | Information disclosure | No `INTERNET` permission; no-network source scan over `com.endoapp.app.report`; the ONLY egress is the user-chosen chooser target |
| Over-broad file URI grant | Elevation of privilege | `FileProvider` `exported=false`, `grantUriPermissions=true`, per-`Intent` `FLAG_GRANT_READ_URI_PERMISSION` (scoped, auto-revoked) |
| Health content in logs / filename | Information disclosure | Log error TYPE only; filename is a date stamp, no symptom/health tokens |
| Stale cache PDF readable later | Information disclosure | Delete cache file(s) after the chooser returns; write only to app-private cacheDir |
| Fabricated cycle/insight in a clinician-facing doc | Tampering (of meaning) | Honesty gates in the pure `ReportModel` (three-state cycle, sparse gates, absence≠0); renderer re-derives nothing |
| Copy drift into a diagnostic claim | Tampering | Every string via `ReportCopy`, registered in `CopyAudit`, substring-scanned against `ClaimsPolicy`; `Disclaimer.text` must-contain test |

## Sources

### Primary (HIGH confidence)
- In-repo iOS parity source: `Sources/EndoApp/Features/Report/{ReportModel,ReportPDFRenderer,ReportRangeMapping,ReportCopy}.swift`; `Tests/EndoAppTests/{ReportRenderTests,ReportNoNetworkTests,ReportRenderSpikeTests}.swift` — the exact behavior to port
- In-repo reuse seams: `android/app/src/main/kotlin/com/endoapp/app/ui/charts/ChartPrimitives.kt`, `ChartSeriesGrouping.kt` (Phase 22-02); `insightsbridge/SnapshotProjector.kt` + `EndoRepository.daySnapshots()` (Phase 18-04); `content/Disclaimer.kt` (Phase 19-04); `export/ui/ExportImportHost.kt` (Phase 23-05)
- `developer.android.com/reference/android/graphics/pdf/PdfDocument` and `/PdfDocument.Page` — vector PDF API surface, minSdk
- `developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page` — confirms bitmap-only render (no text extraction)
- `developer.android.com/reference/androidx/core/content/FileProvider` — share URI pattern

### Secondary (MEDIUM confidence)
- `developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/CanvasDrawScope` + Compose graphics overview — `nativeCanvas` / `drawIntoCanvas` bridge (WebSearch corroborated)
- `developer.android.com/reference/android/text/StaticLayout` — wrapped vector text measurement/draw

### Tertiary (LOW confidence)
- WebSearch on Skia font-subsetting/compression of `PdfDocument` output (A1) — inferred from Skia PDF behavior, not a single authoritative page; drives the recording-seam recommendation regardless

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all first-party/already-present; API surface + minSdk verified; iOS parity source in-repo
- Architecture: HIGH — the pure-model + draw-seam split is the iOS pattern adapted to Android's missing text-extraction API; chart reuse confirmed by Phase 22
- Pitfalls: HIGH for 2–5 (framework behavior + iOS invariants); MEDIUM/LOW for the byte-scan claim in Pitfall 1 (A1) — but the recommendation holds either way
- Validation: HIGH — the test lanes and structural-scan patterns are already established in Phases 21–23

**Research date:** 2026-07-10
**Valid until:** 2026-08-09 (stable first-party APIs; ~30 days)
