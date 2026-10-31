// Requirement: AREL-01 (Phase 24, D-02/D-04) — the thin renderer that turns a PURE `ReportModel` (Plan 02)
// into a clinician-credible, multi-page, on-device VECTOR PDF. Kotlin port of the iOS
// `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift` layout driver, drawn onto the [ReportCanvas]
// seam instead of a `UIGraphicsPDFRendererContext`.
//
// RENDER LAYER ONLY: it re-derives NO cycle/insight/honesty logic (that lives in the pure `ReportModel` it
// is handed). Its job is purely typographic — lay the 8 D-04 sections out as selectable VECTOR text on
// auto-paginated US-Letter pages, repeat a per-page footer + "Page n of m", draw a PROMINENT disclaimer band
// (`Disclaimer.text` VERBATIM) atop page 1, and embed the reused Phase-22 trend chart as single-ink black
// VECTOR via [ReportChartAdapter].
//
// TWO-PASS PAGINATION (RESEARCH Pattern 2, Pitfall 3): pass 1 lays everything out through a MEASURING
// canvas to learn the TOTAL page count (so the footer can print "of m"); pass 2 redraws with `m` known.
// ONE `layout` routine is parameterized only by the [ReportCanvas], and a FIXED `asOf = model.range.end` is
// threaded so neither pass reads the wall clock — the page count can never drift across midnight.
//
// COPY DISCIPLINE (T-24-07): EVERY fixed string is sourced from `ReportCopy` / `Disclaimer.text` — there is
// NO inline string literal drawn into the PDF (the only interpolated text is the user's own data + dates).
// The enum labels come from the app's AUDITED `TrackingCopy` / `InsightsCopy` vocabularies.
//
// PRIVACY (T-24-06): on any render/write failure the renderer logs only the error TYPE, never health
// content, and returns `null` so the caller routes to an honest error state (never a blank/zero-byte PDF).
package com.endoapp.app.report

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.endoapp.app.content.Disclaimer
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.content.ReportCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightPoint
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The renderer: a pure `ReportModel` → a multi-page, selectable-text, on-device VECTOR PDF. All layout goes
 * through the [ReportCanvas] seam, so the vector/copy/page-count invariants are provable on the JVM lane via
 * [RecordingReportCanvas], while the real [PdfReportCanvas] draws the actual `PdfDocument`.
 */
object ReportPdfRenderer {

    // MARK: - Page geometry (US-Letter; A5 — one constant flips to A4 595 x 842)

    /** US-Letter at 72pt = 1in (matches the shipped iOS report). A4 would be 595 x 842. */
    const val PAGE_W = 612
    const val PAGE_H = 792

    private const val MARGIN = 56f
    private const val SECTION_GAP = 24f
    private const val CHART_HEIGHT = 180f

    private const val CONTENT_WIDTH = PAGE_W - MARGIN * 2
    private const val CONTENT_BOTTOM = PAGE_H - MARGIN

    // MARK: - Near-monochrome ink (shape + text, never hue)

    private const val INK = 0xFF000000.toInt()
    private const val SECONDARY_INK = 0xFF4D4D4D.toInt() // white 0.30

    // MARK: - Type scale (4 fixed sizes / 2 weights, black on white)

    private val TITLE = TextStyle(size = 22f, bold = true, colorArgb = INK)
    private val HEADING = TextStyle(size = 14f, bold = true, colorArgb = INK)
    private val BODY = TextStyle(size = 10f, bold = false, colorArgb = INK)
    private val BODY_SECONDARY = TextStyle(size = 10f, bold = false, colorArgb = SECONDARY_INK)
    private val CAPTION = TextStyle(size = 8f, bold = false, colorArgb = SECONDARY_INK)

    private const val LOG_TAG = "ReportPdfRenderer"

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

    // MARK: - Public API

    /**
     * Render [model] into a multi-page VECTOR PDF file under `outDir/reports/`. Returns the file, or `null`
     * on any render/write failure (logging only the error TYPE — never health content; T-24-06). The file
     * name is a date stamp only — no symptom/health token (T-24-08).
     */
    fun render(model: ReportModel, outDir: File): File? {
        var doc: PdfDocument? = null
        return try {
            // PASS 1 (measure): learn the total page count so the footer can print "Page n of m".
            val measurer = PdfReportCanvas(initialCanvas = null, onNewPage = null)
            val totalPages = layout(model, measurer, totalPages = 0)

            // PASS 2 (draw): stamp the real pages with `m` known.
            doc = PdfDocument()
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            val activeDoc = doc
            val drawer = PdfReportCanvas(
                initialCanvas = page.canvas,
                onNewPage = {
                    activeDoc.finishPage(page)
                    pageNum += 1
                    page = activeDoc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    page.canvas
                },
            )
            layout(model, drawer, totalPages = totalPages)
            doc.finishPage(page)

            val dir = File(outDir, "reports").apply { mkdirs() }
            val file = File(dir, "endo-report-${fileStamp(model.range)}.pdf")
            file.outputStream().use { doc.writeTo(it) }
            file
        } catch (t: Throwable) {
            // Privacy (T-24-06): log the error TYPE ONLY, never the data/content. Surface null so the caller
            // shows an honest error state, never a blank/zero-byte preview.
            Log.e(LOG_TAG, "PDF render/write failed: ${t.javaClass.simpleName}")
            null
        } finally {
            doc?.close()
        }
    }

    // MARK: - Layout driver (shared by the measure pass + the draw pass)

    /** A mutable cursor threaded through the layout. */
    private class Cursor(
        var y: Float,
        var page: Int,
        val totalPages: Int,
        val canvas: ReportCanvas,
    )

    /**
     * Lay every section out, paginating on a running Y cursor, drawing through [canvas]. Returns the page
     * count produced. When [canvas] is a measuring/recording spy it only measures + counts; when it is the
     * real [PdfReportCanvas] it draws. The SAME routine drives both passes (page-count non-drift).
     *
     * The first page is assumed already live (the caller `startPage`d it); `canvas.newPage()` flips to 2+.
     */
    fun layout(model: ReportModel, canvas: ReportCanvas, totalPages: Int): Int {
        val cursor = Cursor(y = MARGIN, page = 1, totalPages = totalPages, canvas = canvas)

        // SECTION 1 — Overview: title + the generated/covered meta + the PROMINENT disclaimer band.
        drawBlock(ReportCopy.PDF_TITLE, TITLE, cursor)
        drawBlock(headerMeta(model), BODY_SECONDARY, cursor)
        drawDisclaimerBand(cursor)

        // SECTION 2 — Coverage.
        drawSectionHeading(ReportCopy.SECTION_SUMMARY_TITLE, model.summary.source, cursor)
        drawBlock(ReportCopy.coverageLine(model.summary.daysLogged), BODY, cursor)

        // SECTION 3 — Symptoms & pain: the descriptive trend table + the embedded VECTOR chart.
        drawSectionHeading(ReportCopy.SECTION_SYMPTOMS_TITLE, model.symptoms.source, cursor)
        drawSymptoms(model, cursor)

        // SECTION 4 — Cycle & bleeding (three-state, NO phantom cycle). The heading tag is the aggregated
        // bleeding/flow provenance, not a hardcoded none.
        drawSectionHeading(ReportCopy.SECTION_CYCLE_TITLE, model.cycleSource, cursor)
        drawCycle(model.cycle, cursor)

        // SECTION 5 — Daily factors (absence ≠ 0; null → "Not logged").
        drawSectionHeading(ReportCopy.SECTION_LIFESTYLE_TITLE, model.lifestyle.source, cursor)
        drawLifestyle(model.lifestyle, cursor)

        // SECTION 6 — Patterns: the model's gated insights for the range (sparse → honest line).
        drawSectionHeading(ReportCopy.SECTION_INSIGHTS_TITLE, SectionSource.NONE, cursor)
        drawInsights(model.insights, cursor)

        // SECTION 7 — Source legend (explains only the labels in use; glyph + word, never color).
        drawSectionHeading(ReportCopy.SECTION_LEGEND_TITLE, SectionSource.NONE, cursor)
        drawLegend(model.legend, cursor)

        // Footer on the final page (earlier pages are stamped on flip).
        drawFooter(cursor)
        return cursor.page
    }

    // MARK: - Sections

    /** The PROMINENT boxed disclaimer band atop page 1 (NOT footer fine print). `Disclaimer.text` verbatim. */
    private fun drawDisclaimerBand(cursor: Cursor) {
        val text = Disclaimer.text
        val inset = 10f
        val textWidth = CONTENT_WIDTH - inset * 2
        val textHeight = cursor.canvas.measureWrapped(text, textWidth, BODY)
        val bandHeight = textHeight + inset * 2

        ensureRoom(bandHeight + SECTION_GAP, cursor)
        cursor.y += SECTION_GAP / 2

        cursor.canvas.drawRect(
            ReportRect(MARGIN, cursor.y, MARGIN + CONTENT_WIDTH, cursor.y + bandHeight),
            fill = true,
        )
        cursor.canvas.drawWrapped(text, MARGIN + inset, cursor.y + inset, textWidth, BODY)
        cursor.y += bandHeight + SECTION_GAP / 2
    }

    /** Symptoms: the descriptive trend table, then the embedded VECTOR chart (never the sole carrier of
     *  meaning). Sparse → the honest "not enough data" line. */
    private fun drawSymptoms(model: ReportModel, cursor: Cursor) {
        val section = model.symptoms
        if (!section.hasEnoughData || section.rows.isEmpty()) {
            drawBlock(ReportCopy.NOT_ENOUGH_DATA_IN_RANGE, BODY_SECONDARY, cursor)
            return
        }

        // Table header row + each symptom row (absolute severity range + days logged).
        val cols = floatArrayOf(CONTENT_WIDTH * 0.5f, CONTENT_WIDTH * 0.27f, CONTENT_WIDTH * 0.23f)
        drawTableHeader(
            arrayOf(ReportCopy.SYMPTOMS_COL_SYMPTOM, ReportCopy.SYMPTOMS_COL_RANGE, ReportCopy.SYMPTOMS_COL_DAYS),
            cols, cursor,
        )
        for (row in section.rows) {
            drawTableRow(
                arrayOf(
                    symptomLabel(row.type),
                    ReportCopy.severityRange(row.minSeverity, row.maxSeverity),
                    row.daysLogged.toString(),
                ),
                cols, cursor,
            )
        }

        drawChart(model, cursor)
    }

    /** Cycle (three-state, NO phantom cycle): render the model's `CycleSection` verbatim. */
    private fun drawCycle(cycle: CycleSection, cursor: Cursor) {
        when (cycle) {
            CycleSection.Suppressed ->
                drawBlock(ReportCopy.CYCLE_SUPPRESSED, BODY_SECONDARY, cursor)
            CycleSection.NotEnoughData ->
                drawBlock(ReportCopy.NOT_ENOUGH_DATA_IN_RANGE, BODY_SECONDARY, cursor)
            is CycleSection.Derived -> {
                cycle.cycleDay?.let { drawBlock(ReportCopy.cycleDayValue(it), BODY, cursor) }
                cycle.nextWindow?.let { window ->
                    // A DATE RANGE, never a single hard date (D-08).
                    val line = "${ReportCopy.CYCLE_NEXT_WINDOW_LABEL}: " +
                        "${formatDate(window.start)} – ${formatDate(window.endInclusive)}"
                    drawBlock(line, BODY, cursor)
                }
                // A plain confidence WORD — never a number/percentage (D-11).
                drawBlock(ReportCopy.cycleConfidenceWord(cycle.confidence), BODY_SECONDARY, cursor)
            }
        }
    }

    /** Daily factors: each factor as a compact label/value row; a null field renders "Not logged" (≠ 0). */
    private fun drawLifestyle(life: LifestyleSection, cursor: Cursor) {
        val cols = floatArrayOf(CONTENT_WIDTH * 0.4f, CONTENT_WIDTH * 0.6f)
        fun row(label: String, value: String?) {
            drawTableRow(arrayOf(label, value ?: ReportCopy.NOT_LOGGED), cols, cursor)
        }
        row(ReportCopy.LIFESTYLE_SLEEP, sleepValue(life))
        row(ReportCopy.LIFESTYLE_ACTIVITY, activityValue(life))
        row(ReportCopy.LIFESTYLE_FOOD, if (life.loggedFood) ReportCopy.FOOD_YES else null)
        row(ReportCopy.LIFESTYLE_STRESS, life.stress?.toString()) // a logged 0 prints "0" (≠ absence)
        row(ReportCopy.LIFESTYLE_MOOD, life.mood?.toString())
    }

    /** Patterns: the model's already-gated insights (headline + detail verbatim + a plain confidence word).
     *  A sparse range renders the honest "not enough data" line. */
    private fun drawInsights(insights: List<Insight>, cursor: Cursor) {
        val real = insights.filter { !it.isInsufficiencyNotice }
        if (real.isEmpty()) {
            drawBlock(ReportCopy.INSIGHTS_SPARSE, BODY_SECONDARY, cursor)
            return
        }
        for (insight in real) {
            // Headline + detail are gate-audited engine copy, rendered VERBATIM (never re-authored).
            drawBlock(insight.headline, BODY, cursor)
            drawBlock(insight.detail, BODY_SECONDARY, cursor)
            drawBlock(InsightsCopy.confidenceWord(insight.confidence), CAPTION, cursor)
            cursor.y += 4f
        }
    }

    /** The source legend (section 7): explains ONLY the labels actually in use (glyph + word, never color). */
    private fun drawLegend(legend: SourceLegend, cursor: Cursor) {
        drawBlock(ReportCopy.LEGEND_INTRO, BODY, cursor)
        if (legend.hasSelfReported) {
            drawBlock("● ${ReportCopy.LEGEND_SELF_REPORTED}", BODY, cursor)
        }
        if (legend.hasHealthConnect) {
            drawBlock("♥ ${ReportCopy.LEGEND_HEALTH_CONNECT}", BODY, cursor)
        }
        if (legend.hasSelfReported && legend.hasHealthConnect) {
            drawBlock(ReportCopy.LEGEND_MIXED, BODY, cursor)
        }
    }

    // MARK: - Chart embed (reuse the Phase-22 geometry, single-ink black VECTOR)

    /** Draw the busiest symptom's REAL per-day series as the embedded chart. A symptom logged
     *  present-without-severity carries no honest points → the text fallback (never fabricated data). */
    private fun drawChart(model: ReportModel, cursor: Cursor) {
        val row = model.symptoms.rows.maxByOrNull { it.daysLogged } ?: return
        val points = row.points
        if (points.isEmpty()) {
            drawBlock(ReportCopy.NOT_ENOUGH_DATA_IN_RANGE, BODY_SECONDARY, cursor)
            return
        }
        ensureRoom(CHART_HEIGHT + SECTION_GAP / 2, cursor)
        cursor.canvas.drawChart(
            ReportRect(MARGIN, cursor.y, MARGIN + CONTENT_WIDTH, cursor.y + CHART_HEIGHT),
            points,
        )
        cursor.y += CHART_HEIGHT + SECTION_GAP / 2
    }

    // MARK: - Table primitives

    private fun drawTableHeader(cells: Array<String>, widths: FloatArray, cursor: Cursor) {
        val rowHeight = cursor.canvas.lineHeight(BODY) + 6f
        ensureRoom(rowHeight, cursor)
        cursor.canvas.drawRect(
            ReportRect(MARGIN, cursor.y, MARGIN + CONTENT_WIDTH, cursor.y + rowHeight),
            fill = true,
        )
        drawCells(cells, widths, HEADING, rowHeight, cursor)
    }

    private fun drawTableRow(cells: Array<String>, widths: FloatArray, cursor: Cursor) {
        val rowHeight = cursor.canvas.lineHeight(BODY) + 4f
        ensureRoom(rowHeight, cursor)
        drawCells(cells, widths, BODY, rowHeight, cursor)
        cursor.canvas.drawLine(MARGIN, cursor.y, MARGIN + CONTENT_WIDTH, cursor.y)
    }

    private fun drawCells(
        cells: Array<String>,
        widths: FloatArray,
        style: TextStyle,
        height: Float,
        cursor: Cursor,
    ) {
        var x = MARGIN
        for (i in cells.indices) {
            cursor.canvas.drawText(cells[i], x + 2f, cursor.y + 2f, style)
            x += widths[i]
        }
        cursor.y += height
    }

    // MARK: - Text primitives (VECTOR — never raster)

    /** Draw a wrapping text block, paginating first if it would overflow. Advances the cursor by its height. */
    private fun drawBlock(string: String, style: TextStyle, cursor: Cursor) {
        val height = cursor.canvas.measureWrapped(string, CONTENT_WIDTH, style)
        ensureRoom(height, cursor)
        cursor.canvas.drawWrapped(string, MARGIN, cursor.y, CONTENT_WIDTH, style)
        cursor.y += height + 2f
    }

    /** A numbered section heading. Never ORPHANS: demands room for the heading AND a first body line, so a
     *  heading at a page bottom carries to the next page with its content. Optionally prints a source tag. */
    private fun drawSectionHeading(title: String, source: SectionSource, cursor: Cursor) {
        val headingHeight = cursor.canvas.measureWrapped(title, CONTENT_WIDTH, HEADING)
        ensureRoom(SECTION_GAP + headingHeight + cursor.canvas.lineHeight(BODY), cursor)
        cursor.y += SECTION_GAP
        cursor.canvas.drawWrapped(title, MARGIN, cursor.y, CONTENT_WIDTH, HEADING)
        val tag = ReportCopy.sourceTag(source)
        if (tag.isNotEmpty()) {
            // Source tag = glyph + word (never hue), on the right of the heading row.
            cursor.canvas.drawText(
                "● $tag",
                MARGIN + CONTENT_WIDTH * 0.55f,
                cursor.y + 4f,
                CAPTION,
            )
        }
        cursor.y += headingHeight + 4f
    }

    /** The per-page footer (EVERY page): the framing (left) + "Page n of m" (right). */
    private fun drawFooter(cursor: Cursor) {
        val y = CONTENT_BOTTOM + 8f
        val total = if (cursor.totalPages > 0) cursor.totalPages else cursor.page
        cursor.canvas.drawText(ReportCopy.FOOTER, MARGIN, y, CAPTION)
        cursor.canvas.drawText(
            ReportCopy.pageNumber(cursor.page, total),
            MARGIN + CONTENT_WIDTH * 0.75f,
            y,
            CAPTION,
        )
        cursor.canvas.drawLine(MARGIN, y - 4f, MARGIN + CONTENT_WIDTH, y - 4f)
    }

    // MARK: - Pagination

    /** Stamp the current page's footer and flip to a new page when [height] would cross the bottom margin. */
    private fun ensureRoom(height: Float, cursor: Cursor) {
        if (cursor.y + height > CONTENT_BOTTOM) {
            drawFooter(cursor)
            cursor.canvas.newPage()
            cursor.page += 1
            cursor.y = MARGIN
        }
    }

    // MARK: - Formatting + label bridges

    private fun headerMeta(model: ReportModel): String =
        ReportCopy.headerMeta(
            generated = formatDate(model.generatedOn),
            rangeStart = formatDate(model.range.start),
            rangeEnd = formatDate(model.range.end),
        )

    private fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    /** A short, deterministic file-name stamp for the range (NO health content in the name; T-24-08). */
    private fun fileStamp(range: ReportRange): String =
        "${range.start.format(stampFormatter)}-${range.end.format(stampFormatter)}"

    /** Bridge a package `SymptomType` to the app's AUDITED label vocabulary (`TrackingCopy`). */
    private fun symptomLabel(type: com.endoapp.insights.SymptomType): String =
        TrackingCopy.symptomLabels[type] ?: TrackingCopy.symptomLabels.getValue(com.endoapp.insights.SymptomType.OTHER)

    private fun sleepValue(life: LifestyleSection): String? {
        if (life.sleepHours == null && life.sleepQuality == null) return null
        val parts = mutableListOf<String>()
        life.sleepHours?.let { parts += String.format(Locale.US, "%.1f h", it) }
        life.sleepQuality?.let { parts += (TrackingCopy.sleepQualityLabels[it] ?: ReportCopy.NOT_LOGGED) }
        return parts.joinToString(" · ")
    }

    private fun activityValue(life: LifestyleSection): String? {
        if (life.activityType == null && life.activityMinutes == null) return null
        val parts = mutableListOf<String>()
        life.activityType?.let { parts += (TrackingCopy.activityTypeLabels[it] ?: ReportCopy.NOT_LOGGED) }
        life.activityMinutes?.let { parts += String.format(Locale.US, "%.0f min", it) }
        return parts.joinToString(" · ")
    }
}

/**
 * The REAL [ReportCanvas] adapter over an `android.graphics.pdf.PdfDocument` page's native `Canvas`. Draws
 * SELECTABLE VECTOR text (`Canvas.drawText` / `StaticLayout` — NEVER `drawBitmap` for text), shapes via the
 * native canvas, and the embedded chart via [ReportChartAdapter].
 *
 * MEASURE MODE (pass 1): constructed with `initialCanvas = null` + `onNewPage = null`, all draw calls are
 * no-ops but [measureWrapped]/[lineHeight] still compute via `StaticLayout`/`Paint` — the IDENTICAL routine
 * pass 2 draws with, so the page count cannot drift.
 */
class PdfReportCanvas(
    initialCanvas: android.graphics.Canvas?,
    private val onNewPage: (() -> android.graphics.Canvas)?,
) : ReportCanvas {

    private var canvas: android.graphics.Canvas? = initialCanvas
    private val textPaints = HashMap<TextStyle, TextPaint>()

    private val bandFill = Paint().apply { color = 0xFFF2F2F7.toInt(); style = Paint.Style.FILL }
    private val hairline = Paint().apply {
        color = 0xFFD1D1D6.toInt()
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    private fun paintFor(style: TextStyle): TextPaint = textPaints.getOrPut(style) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.colorArgb
            textSize = style.size
            isFakeBoldText = style.bold
        }
    }

    @Suppress("DEPRECATION")
    private fun layoutFor(text: String, width: Float, style: TextStyle): StaticLayout {
        val paint = paintFor(style)
        val w = maxOf(1, width.toInt())
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, w)
            .setIncludePad(false)
            .build()
    }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle) {
        val c = canvas ?: return
        val paint = paintFor(style)
        // `y` is the TOP; drawText draws at the baseline, so offset by the ascent (negative).
        c.drawText(text, x, y - paint.fontMetrics.ascent, paint)
    }

    override fun drawWrapped(text: String, x: Float, y: Float, width: Float, style: TextStyle): Float {
        val layout = layoutFor(text, width, style)
        val c = canvas
        if (c != null) {
            c.save()
            c.translate(x, y)
            layout.draw(c)
            c.restore()
        }
        return layout.height.toFloat()
    }

    override fun measureWrapped(text: String, width: Float, style: TextStyle): Float =
        layoutFor(text, width, style).height.toFloat()

    override fun lineHeight(style: TextStyle): Float = paintFor(style).fontSpacing

    override fun drawRect(rect: ReportRect, fill: Boolean) {
        val c = canvas ?: return
        c.drawRect(rect.left, rect.top, rect.right, rect.bottom, if (fill) bandFill else hairline)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        canvas?.drawLine(x0, y0, x1, y1, hairline)
    }

    override fun drawChart(box: ReportRect, points: List<InsightPoint>) {
        val c = canvas ?: return
        ReportChartAdapter.drawTrendChart(c, box, points)
    }

    override fun newPage() {
        val next = onNewPage?.invoke() ?: return // measure mode: no page to flip
        canvas = next
    }
}
