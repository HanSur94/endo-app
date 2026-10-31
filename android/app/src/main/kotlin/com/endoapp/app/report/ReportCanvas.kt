// Requirement: AREL-01 (Phase 24, D-02/D-04) — the draw-instruction SEAM that makes "the report text is
// selectable VECTOR, not a raster snapshot" PROVABLE on the plain JVM lane.
//
// WHY A SEAM (RESEARCH Pitfall 1): iOS proves selectable text via `PDFDocument.string` (PDFKit extracts
// text). Android has NO first-party PDF text extractor — `PdfRenderer` renders bitmaps only — and Skia
// subsets fonts + Flate-compresses the content stream, so scanning the written bytes for "not medical
// advice" fails. The robust pivot: the renderer NEVER touches `android.graphics.Canvas` for content
// directly; it draws through this [ReportCanvas] interface. A pure-Kotlin [RecordingReportCanvas] spy then
// asserts the renderer emitted `drawText`/`drawWrapped` for the expected copy AND — by construction — that
// NO raster-text path exists (there is deliberately NO `drawBitmap`-for-text member on this interface).
//
// PURE SPY (RESEARCH Pitfall 2): the real `android.graphics.pdf.PdfDocument` / `Canvas.drawText` /
// `StaticLayout` are native and throw "not mocked" on the plain JVM unit lane. So the vector/copy/page-count
// proof runs ENTIRELY off the [RecordingReportCanvas] (no `android.graphics` import), on the fast JVM lane.
// The real `PdfReportCanvas` adapter (native `Canvas`/`StaticLayout`) lives in `ReportPdfRenderer.kt` and is
// exercised only by the instrumented smoke.
//
// PAGE-COUNT NON-DRIFT (RESEARCH Pitfall 3): the renderer's two layout passes measure through the SAME
// [measureWrapped]/[lineHeight] routine on whichever canvas is passed, so `m` in "Page n of m" can never
// disagree with the actual page count. The spy's measurement is deterministic (same input → same height).
package com.endoapp.app.report

import com.endoapp.insights.InsightPoint

/**
 * A PURE (no `android.graphics`) rectangle for the seam — a band / table-header background or a chart box.
 * Kept off `android.graphics.RectF` so the [RecordingReportCanvas] spy stays plain-Kotlin JVM.
 */
data class ReportRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

/**
 * A text role: the four iOS type roles (title / heading / body / caption) map to a size + weight + a
 * PRINT-INK ARGB color. The color is a plain `Int` (not `android.graphics.Color`) so the spy stays pure;
 * the real adapter maps it onto a `Paint`. Near-monochrome by design (shape + text, never hue — UI-SPEC).
 */
data class TextStyle(
    val size: Float,
    val bold: Boolean,
    val colorArgb: Int,
)

/**
 * The draw-instruction seam the report renderer draws through. It exposes ONLY vector-text + vector-shape
 * primitives — there is deliberately NO `drawBitmap`(...) member, so a raster-text path CANNOT exist by
 * construction (the anti-forgery invariant `ReportRenderTest` pins by reflection).
 *
 * Two implementations:
 *   • [RecordingReportCanvas] (here) — pure-Kotlin test spy: captures every drawn string, counts pages,
 *     returns deterministic measurements → the JVM vector/copy/page-count proof.
 *   • `PdfReportCanvas` (in `ReportPdfRenderer.kt`) — the real `android.graphics.pdf.PdfDocument`-page
 *     adapter: `drawText` via `Canvas.drawText`, `drawWrapped`/`measureWrapped` via `StaticLayout`
 *     (VECTOR — never `drawBitmap` for text).
 */
interface ReportCanvas {
    /** Draw a single line of VECTOR text at the top-left `(x, y)` in `style`. */
    fun drawText(text: String, x: Float, y: Float, style: TextStyle)

    /** Draw WRAPPED VECTOR text in a `width`-wide column at top-left `(x, y)`; returns the drawn height. */
    fun drawWrapped(text: String, x: Float, y: Float, width: Float, style: TextStyle): Float

    /** Measure the wrapped height `[drawWrapped]` would occupy — MUST match `drawWrapped` exactly so both
     *  layout passes agree (no page-count drift). */
    fun measureWrapped(text: String, width: Float, style: TextStyle): Float

    /** The single-line height for a `style` (independent of the text) — drives single-line advances. */
    fun lineHeight(style: TextStyle): Float

    /** Fill or stroke a rectangle (the disclaimer band / table-header background). */
    fun drawRect(rect: ReportRect, fill: Boolean)

    /** Draw a hairline from `(x0, y0)` to `(x1, y1)` (table row rule / footer rule). */
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float)

    /** Draw the embedded trend chart as SINGLE-INK vector geometry (reuses the Phase-22 primitives). A
     *  no-op in the spy (its honesty is proven separately off `ChartSeriesGrouping`/`ChartCoordinateMapper`). */
    fun drawChart(box: ReportRect, points: List<InsightPoint>)

    /** Flip to a new page (pagination). The real adapter `finishPage`s the current + `startPage`s the next;
     *  the spy just counts. */
    fun newPage()
}

/**
 * The pure-Kotlin recording spy — NO `android.graphics` import, so it runs on the fast JVM unit lane. It
 * records every drawn string (in draw order) and counts pages, and returns DETERMINISTIC measurements so
 * the renderer's two layout passes measure byte-identically (page-count non-drift, RESEARCH Pitfall 3).
 */
class RecordingReportCanvas : ReportCanvas {

    /** Every string the renderer drew as VECTOR text, in draw order (the copy + selectability proof). */
    val texts: MutableList<String> = mutableListOf()

    /** The current page count (starts at 1 — the first page is live before any `newPage`). */
    var pages: Int = 1
        private set

    /** How many times a rectangle / line / chart was drawn (available for structural assertions). */
    var rectCount: Int = 0
        private set
    var lineCount: Int = 0
        private set
    var chartCount: Int = 0
        private set

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        texts += text
    }

    override fun drawWrapped(text: String, x: Float, y: Float, width: Float, style: TextStyle): Float {
        texts += text
        return measureWrapped(text, width, style)
    }

    override fun measureWrapped(text: String, width: Float, style: TextStyle): Float {
        // Deterministic wrapped-height estimate: line-height × the number of estimated wrapped lines. A
        // crude but STABLE character-per-line estimate (same input → same height) is all the two passes
        // need to agree — the real adapter uses StaticLayout, but the spy never runs beside it.
        val avgCharWidth = style.size * 0.5f
        val charsPerLine = maxOf(1, (width / avgCharWidth).toInt())
        val lines = maxOf(1, Math.ceil(text.length.toDouble() / charsPerLine).toInt())
        return lineHeight(style) * lines
    }

    override fun lineHeight(style: TextStyle): Float = style.size + 4f

    override fun drawRect(rect: ReportRect, fill: Boolean) {
        rectCount += 1
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        lineCount += 1
    }

    override fun drawChart(box: ReportRect, points: List<InsightPoint>) {
        chartCount += 1
    }

    override fun newPage() {
        pages += 1
    }
}
