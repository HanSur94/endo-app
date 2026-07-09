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
import androidx.compose.ui.unit.dp
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
                val w = size.width
                val h = size.height
                points.forEach { p ->
                    val fx = ((p.x - xDomain.start) / xSpan).toFloat().coerceIn(0f, 1f)
                    val fy = ((p.y - yDomain.start) / ySpan).toFloat().coerceIn(0f, 1f)
                    // y inverted so the domain floor sits at the bottom.
                    val center = Offset(fx * w, (1f - fy) * h)
                    // Single accent hue — the count is encoded by SIZE + the a11y value, never by colour.
                    drawCircle(
                        color = colors.plum,
                        radius = scatterMarkRadiusDp(p.count).dp.toPx(),
                        center = center,
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

        // The two data-derived axis names (factual only).
        Text(
            text = ExploreCopy.scatterAxisTitles(x.displayName, y.displayName),
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
