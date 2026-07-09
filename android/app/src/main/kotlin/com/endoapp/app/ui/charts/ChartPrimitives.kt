// Reusable `DrawScope` drawing primitives for every Bloom chart — factored so BOTH the on-screen
// Compose chart AND Phase 24's PDF report renderer can call them against a PLAIN `DrawScope`
// (a `Canvas`/`androidx.compose.ui.graphics.Canvas`-backed `DrawScope`, or the PDF's own). They hold
// NO Compose-runtime state (no `remember`, no snapshot state, no `@Composable`) — value-in / draw-out —
// so the print-ink PDF path reuses the SAME line-break + point + scrub-rule geometry as the live chart.
//
// Honesty is structural here: [drawRuns] draws ONE `Path` per run (a break between runs = a gap, H1);
// [drawPoints] draws one dot per logged point INCLUDING a literal severity-0 at the zero line (H2). The
// caller passes the run-segmented data from the pure [ChartSeriesGrouping]; these helpers never
// interpolate, never snap, never fabricate a value.
package com.endoapp.app.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.endoapp.insights.InsightPoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A PURE date+value → pixel mapper. Holds only plain geometry (no Compose-runtime state), so it is
 * reusable off a bare `DrawScope` by both the live chart and the Phase-24 PDF renderer.
 *
 * The x-axis is ABSOLUTE calendar dates (`startDate … endDate`); the y-axis is a FIXED value domain
 * (`valueMin … valueMax`, e.g. `0 … 10` for severity — never a data-driven min/max, H4). y is inverted
 * so `valueMax` sits at the top and `valueMin` (the zero line) sits at the bottom.
 */
class ChartCoordinateMapper(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val valueMin: Double,
    val valueMax: Double,
    val width: Float,
    val height: Float,
) {
    /** At least 1 so a single-day / same-day window never divides by zero. */
    private val dateSpanDays: Long = maxOf(1L, ChronoUnit.DAYS.between(startDate, endDate))

    /** At least a tiny epsilon so a degenerate domain never divides by zero. */
    private val valueSpan: Double = (valueMax - valueMin).let { if (it == 0.0) 1.0 else it }

    /** The x pixel for a calendar [date] (fraction of the absolute-date span × width). */
    fun x(date: LocalDate): Float {
        val days = ChronoUnit.DAYS.between(startDate, date).toDouble()
        return (days / dateSpanDays).toFloat() * width
    }

    /** The y pixel for a real [value] on the FIXED domain — inverted so max is at the top, and the
     *  domain floor ([valueMin], e.g. severity 0) maps to the BOTTOM zero line (H2). */
    fun y(value: Double): Float {
        val t = (value - valueMin) / valueSpan
        return ((1.0 - t) * height).toFloat()
    }

    /** The pixel [Offset] for a logged point. */
    fun offset(date: LocalDate, value: Double): Offset = Offset(x(date), y(value))

    /** The nearest calendar day under a raw x pixel — the gesture's x→day snap (day granularity). This
     *  is a pure axis inversion; the raw day still routes through `ChartScrubSelection.resolve` for the
     *  VALUE (this never reads a value, so it can never fabricate one). */
    fun dateAt(x: Float): LocalDate {
        val frac = (x / width).coerceIn(0f, 1f).toDouble()
        val day = Math.round(frac * dateSpanDays)
        return startDate.plusDays(day)
    }
}

/**
 * The reusable `DrawScope` chart primitives. Members are `DrawScope` extensions so a caller draws with
 * `with(ChartPrimitives) { drawRuns(...) }` from any `DrawScope` — the live Canvas or the PDF page.
 */
object ChartPrimitives {

    /** Draw ONE stroked [Path] per run — the line connects WITHIN a run and BREAKS between runs (H1).
     *  An empty run and a single-point run draw no stroke (the point layer shows the isolated day). */
    fun DrawScope.drawRuns(
        runs: List<List<InsightPoint>>,
        mapper: ChartCoordinateMapper,
        strokeColor: Color,
        strokeWidth: Float,
    ) {
        for (run in runs) {
            if (run.size < 2) continue // a lone point is drawn by drawPoints; a run never interpolates a gap
            val path = buildRunPath(run, mapper)
            drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }

    /** Build the connected [Path] for a single contiguous run (pure geometry — also handy to cache). */
    fun buildRunPath(run: List<InsightPoint>, mapper: ChartCoordinateMapper): Path {
        val path = Path()
        run.forEachIndexed { index, point ->
            val o = mapper.offset(point.date, point.value)
            if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        return path
    }

    /** Draw one filled dot per logged point — so a single logged day still shows and a literal
     *  severity-0 day draws a real point AT the zero line (H2). A gap has no point here (absence ≠ 0). */
    fun DrawScope.drawPoints(
        points: List<InsightPoint>,
        mapper: ChartCoordinateMapper,
        color: Color,
        radius: Float,
    ) {
        for (point in points) {
            drawCircle(color = color, radius = radius, center = mapper.offset(point.date, point.value))
        }
    }

    /** Draw the vertical scrub scan line at pixel [x] — a secondary/muted hue drawn BEHIND the data,
     *  NEVER the accent (it must not compete with the data line). */
    fun DrawScope.drawScrubRule(x: Float, color: Color, strokeWidth: Float) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth,
        )
    }

    /** Draw the enlarged highlight dot at a scrubbed LOGGED day (drawing one on a gap would fabricate
     *  data — the caller passes only a resolved logged point). On a severity-0 day it sits at the zero
     *  line (H2). */
    fun DrawScope.drawHighlight(
        point: InsightPoint,
        mapper: ChartCoordinateMapper,
        color: Color,
        radius: Float,
    ) {
        drawCircle(color = color, radius = radius, center = mapper.offset(point.date, point.value))
    }
}
