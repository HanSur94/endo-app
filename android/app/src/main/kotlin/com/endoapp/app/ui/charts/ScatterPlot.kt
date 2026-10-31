// The descriptive-only, count-encoded scatter — the Android port of `ScatterPlotView.swift` (22-UI-SPEC
// §Chart Rendering 4, AINS-05). Renders EXACTLY the both-logged `List<ScatterPoint>` from the pure
// `ScatterPairing.pairs`: one dot per occupied (x, y) cell, mark SIZE encoded by the cell's day-count.
//
// THE WELLNESS-VS-MEDICAL-DEVICE LINE (H5, threat T-22-01): this layer emits a FACTUAL tally only. There
// is deliberately NO best-line, NO regression, NO coefficient, NO slope, NO percent, NO connecting line
// anywhere in this file — the descriptive-only contract is enforced by a comment-stripped source scan in
// `ScatterPlotTest`. Density is encoded by mark size (from the count the pure layer already aggregated),
// NEVER by jitter (jitter would fabricate fractional values the user never logged) and NEVER by hue (a
// single accent `plum` throughout). Both axes sit on FIXED real-unit domains from
// `SeriesNormalization.domain(...)`, NEVER a data-driven min/max.
package com.endoapp.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ScatterPairing.ScatterPoint
import com.endoapp.insights.SeriesNormalization
import kotlin.math.floor
import kotlin.math.sqrt

/** The square scatter plot side (22-UI-SPEC §Chart Rendering — 320dp square). */
private val PLOT_SIDE = 320.dp

/** Room for the y tick labels left of the axis and the x tick labels below it. */
private val AXIS_GUTTER_LEFT = 28.dp
private val AXIS_GUTTER_BOTTOM = 22.dp

/** Keeps the largest edge mark fully inside the plot instead of half-clipped by the axis. */
private val PLOT_INSET = 12.dp

/** Start, middle and end of a fixed real-unit domain — the whole tick set (no data-driven ticks). */
private fun axisTicks(domain: ClosedFloatingPointRange<Double>): List<Double> =
    listOf(domain.start, (domain.start + domain.endInclusive) / 2, domain.endInclusive)

/** An axis name with its unit, e.g. "Sleep (h)" / "Pelvic pain (of 10)". */
private fun axisName(series: ExploreSeries): String =
    if (series.unit.isBlank()) series.displayName else "${series.displayName} (${series.unit})"

/** The mark radius (dp) for a cell of the given day-[count] — AREA grows with the count so a denser cell
 *  reads as a visibly bigger dot (H5: density by size). PURE + monotonic non-decreasing in [count] (radius
 *  ∝ √count so AREA ∝ count), so `ScatterPlotTest` can assert a 3-day cell draws a bigger mark than a
 *  1-day cell without inspecting pixels. A `count` below 1 is clamped to the base radius. */
fun scatterMarkRadiusDp(count: Int): Float {
    val base = 4f
    val perDay = 3f
    val safe = if (count < 1) 1 else count
    return base + perDay * (sqrt(safe.toDouble()) - 1.0).toFloat()
}

/** Format an axis value for a cell testTag: a whole number drops the trailing `.0` (`6`, not `6.0`). */
private fun tagValue(value: Double): String =
    if (value == floor(value)) value.toInt().toString() else value.toString()

/**
 * The per-cell TalkBack semantics nodes for the scatter — one [ChartSemanticNode] per both-logged (x, y)
 * cell, reusing the SAME overlay type the trend/lane charts use. Label = "{X name} {x readout}, {Y name}
 * {y readout}"; value = "{count} days" (a factual tally at that exact cell — never a statistic). PURE, so
 * `ScatterPlotTest` asserts the per-cell nodes + their readouts without rendering.
 *
 * [xFraction] positions each node along the X domain (ascending, since the pure layer sorts by (x, y)),
 * so TalkBack steps the cells left→right by the X value.
 */
fun scatterSemanticNodes(
    points: List<ScatterPoint>,
    x: ExploreSeries,
    y: ExploreSeries,
    testTagRoot: String = "explore.scatter",
): List<ChartSemanticNode> {
    val xDomain = SeriesNormalization.domain(for_ = x.scaleKind) ?: return emptyList()
    if (!y.scaleKind.isQuantitative) return emptyList()
    val xSpan = (xDomain.endInclusive - xDomain.start).let { if (it == 0.0) 1.0 else it }
    return points.map { p ->
        val xReadout = SeriesNormalization.readout(p.x, x.scaleKind, x.unit)
        val yReadout = SeriesNormalization.readout(p.y, y.scaleKind, y.unit)
        val fraction = ((p.x - xDomain.start) / xSpan).toFloat().coerceIn(0f, 1f)
        ChartSemanticNode(
            testTag = "$testTagRoot.point.${tagValue(p.x)}x${tagValue(p.y)}",
            contentDescription = ExploreCopy.scatterCellLabel(x.displayName, xReadout, y.displayName, yReadout),
            stateDescription = ExploreCopy.scatterCellDays(p.count),
            xFraction = fraction,
        )
    }
}

/**
 * The descriptive-only scatter surface: a worded summary node, the square count-encoded dot cloud on FIXED
 * real-unit domains, an axis-titles line, and a neutral factual caption — plus the reusable per-cell
 * [ChartSemanticsOverlay]. NO connecting line, NO best-line, NO coefficient anywhere (H5).
 *
 * [points] is the both-logged `ScatterPairing.pairs(snapshots, x, y)` output (already count-aggregated, no
 * jitter). [x] / [y] MUST be quantitative; a presence axis yields an empty domain and the plot no-ops.
 */
@Composable
fun ScatterPlot(
    x: ExploreSeries,
    y: ExploreSeries,
    points: List<ScatterPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val xDomain = SeriesNormalization.domain(for_ = x.scaleKind) ?: return
    val yDomain = SeriesNormalization.domain(for_ = y.scaleKind) ?: return
    val xSpan = (xDomain.endInclusive - xDomain.start).let { if (it == 0.0) 1.0 else it }
    val ySpan = (yDomain.endInclusive - yDomain.start).let { if (it == 0.0) 1.0 else it }

    // The both-logged day tally = the sum of the per-cell counts (each dot is one-or-more days at a cell).
    val bothLoggedDays = points.sumOf { it.count }
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = bloomBody.copy(color = colors.inkSecondary, fontSize = 11.sp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("explore.scatter"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A worded summary sits ABOVE the plot so the chart is never the sole carrier of meaning
        // (TalkBack-readable). The a11y overlay passes summary = null to avoid a double announcement.
        Text(
            text = ExploreCopy.scatterSummary(x.displayName, y.displayName, bothLoggedDays),
            style = bloomBody,
            color = colors.inkSecondary,
            modifier = Modifier.testTag("explore.scatter.summary"),
        )

        Box(
            modifier = Modifier
                .size(PLOT_SIDE)
                .align(Alignment.CenterHorizontally),
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .testTag("explore.scatter.plot"),
            ) {
                // Reserve gutters for the tick labels, and inset the data area so edge marks are never
                // clipped. The semantics overlay keeps its own full-size layout (reader order only).
                val left = AXIS_GUTTER_LEFT.toPx()
                val bottom = size.height - AXIS_GUTTER_BOTTOM.toPx()
                val inset = PLOT_INSET.toPx()
                val plotW = size.width - left - 2 * inset
                val plotH = bottom - 2 * inset
                fun px(v: Double) = left + inset + ((v - xDomain.start) / xSpan).toFloat().coerceIn(0f, 1f) * plotW
                // y inverted so the domain floor sits at the bottom.
                fun py(v: Double) = inset + (1f - ((v - yDomain.start) / ySpan).toFloat().coerceIn(0f, 1f)) * plotH

                // Axis lines + the fixed-domain ticks (start / middle / end) in real units.
                val axisStroke = 1.dp.toPx()
                drawLine(colors.inkMuted, Offset(left, 0f), Offset(left, bottom), axisStroke)
                drawLine(colors.inkMuted, Offset(left, bottom), Offset(size.width, bottom), axisStroke)
                axisTicks(xDomain).forEach { v ->
                    val label = textMeasurer.measure(tagValue(v), tickStyle)
                    val cx = px(v).coerceIn(left + label.size.width / 2f, size.width - label.size.width / 2f)
                    drawText(label, topLeft = Offset(cx - label.size.width / 2f, bottom + 4.dp.toPx()))
                }
                axisTicks(yDomain).forEach { v ->
                    val label = textMeasurer.measure(tagValue(v), tickStyle)
                    val cy = py(v).coerceIn(label.size.height / 2f, bottom - label.size.height / 2f)
                    drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), cy - label.size.height / 2f))
                }

                points.forEach { p ->
                    // Single accent hue — the count is encoded by SIZE + the a11y value, never by colour.
                    drawCircle(
                        color = colors.plum,
                        radius = scatterMarkRadiusDp(p.count).dp.toPx(),
                        center = Offset(px(p.x), py(p.y)),
                    )
                }
            }

            // The reusable per-cell reader overlay (summary = null: the visible worded summary above is
            // already TalkBack-readable, so this avoids a double announcement — the TrendChart pattern).
            ChartSemanticsOverlay(
                nodes = scatterSemanticNodes(points, x, y),
                modifier = Modifier.matchParentSize(),
                summary = null,
            )
        }

        // The two data-derived axis names (factual only), each with its real unit.
        Text(
            text = ExploreCopy.scatterAxisTitles(axisName(x), axisName(y)),
            style = bloomBody,
            color = colors.ink,
            modifier = Modifier.testTag("explore.scatter.axisTitles"),
        )

        // The neutral factual caption — a both-logged tally, never a statistic.
        Text(
            text = ExploreCopy.scatterCaption(bothLoggedDays),
            style = bloomBody,
            color = colors.inkSecondary,
            modifier = Modifier.testTag("explore.scatter.caption"),
        )
    }
}
