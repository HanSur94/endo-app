// One quantitative Explore overlay lane — the Android port of `ExploreLaneChart.swift`. Rendered with
// Compose `Canvas`, reusing the Plan-02 `ChartPrimitives`/`ChartCoordinateMapper`/`ChartSeriesGrouping`
// (NO re-implemented drawing/segmentation) and the Plan-03 `ChartSemanticsOverlay`/`trendSemanticNodes`
// (NO re-implemented semantics).
//
// THE HONESTY MOVE (invariants H3/H4, 22-UI-SPEC §Chart Rendering 2): each lane draws on its OWN FIXED
// real-unit domain from `SeriesNormalization.domain(for: series.scaleKind)` — a sleep lane maps against
// `0…24`, a stress lane against `0…10`, INDEPENDENTLY. There is NO shared y-axis and NO dual y-axis: the
// numeric y-axis is HIDDEN (no left gutter) so every lane shares the full width and the ONE shared scrub
// rule aligns across the stack; the scale lives in the lane TITLE ("Sleep — h"), never in a shared gutter.
// The mark hue is semantic per `scaleKind` and REDUNDANT with the title + real-unit readout (colorblind-
// safe — never hue alone).
//
// Gaps stay gaps (H1: one `Path` per contiguous logged-day run → the line BREAKS at every gap); a logged
// literal 0 is a real dot at the domain floor (H2). The lane reads the ONE shared scrub day in the DRAW
// phase (via [scrubDay]) so a finger movement invalidates DRAW, not composition (PITFALLS #14).
package com.endoapp.app.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.app.ui.charts.ChartPrimitives.drawHighlight
import com.endoapp.app.ui.charts.ChartPrimitives.drawPoints
import com.endoapp.app.ui.charts.ChartPrimitives.drawRuns
import com.endoapp.app.ui.charts.ChartPrimitives.drawScrubRule
import com.endoapp.insights.ChartScrubSelection
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.ScaleKind
import com.endoapp.insights.SeriesNormalization
import java.time.LocalDate

/** The quantitative lane height (22-UI-SPEC §Spacing — verbatim from iOS). */
private val LANE_HEIGHT = 120.dp

/**
 * The FIXED real-unit y-domain a quantitative lane draws against — the SINGLE source is
 * [SeriesNormalization.domain] (H3/H4). A presence series has no numeric domain; it must never reach a
 * quantitative lane, so the `null` case degrades to a harmless `0…1` rather than trapping (a routing bug
 * in a must-not-crash health app yields an empty lane, never a crash). Exposed (not private) so the lane
 * domain is unit-provable.
 */
fun quantLaneDomain(series: ExploreSeries): ClosedFloatingPointRange<Double> =
    SeriesNormalization.domain(for_ = series.scaleKind) ?: 0.0..1.0

/** The semantic mark hue for a scale kind (22-UI-SPEC §Chart Rendering 2 — paired with the title +
 *  readout, NEVER hue alone). */
private fun laneHue(kind: ScaleKind, colors: com.endoapp.app.designsystem.BloomColors): Color = when (kind) {
    ScaleKind.ORDINAL_0_TO_10 -> colors.rose // symptom / pain / stress severity
    ScaleKind.ORDINAL_1_TO_5 -> colors.gold // mood
    ScaleKind.CONTINUOUS_HOURS -> colors.indigo // sleep hours
    ScaleKind.CONTINUOUS_MINUTES -> colors.teal // activity minutes
    ScaleKind.ORDINAL_BLEEDING -> colors.plum // bleeding intensity
    ScaleKind.PRESENCE_EVENT -> colors.plum // unreachable here (presence routes to the event lane)
}

/** The honest real-unit readout for a lane point (never the normalized 0…1 number, H4). */
private fun laneReadout(series: ExploreSeries, value: Double): String =
    SeriesNormalization.readout(value, kind = series.scaleKind, unit = series.unit)

/**
 * One quantitative overlay lane: a name+unit title then a Canvas plotting [points] on the series' OWN
 * fixed real-unit domain, sharing the [windowStart]…[asOf] x-domain and the ONE shared [scrubDay] with
 * every sibling lane. [scrubDay] is read in the DRAW phase only.
 */
@Composable
fun ExploreLaneChart(
    series: ExploreSeries,
    points: List<InsightPoint>,
    windowStart: LocalDate,
    asOf: LocalDate,
    scrubDay: () -> LocalDate?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val domain = quantLaneDomain(series)
    val hue = laneHue(series.scaleKind, colors)
    val laneTag = "explore.lane.${series.id}"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The scale lives in the TITLE (name + unit) — NEVER a shared/dual y-axis gutter.
        Text(
            text = ExploreCopy.laneTitle(series.displayName, series.unit),
            style = bloomChipLabel,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkSecondary,
            modifier = Modifier.testTag("$laneTag.title"),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(LANE_HEIGHT)
                .testTag(laneTag),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val strokeWidth = with(density) { 2.dp.toPx() }
            val pointRadius = with(density) { 3.dp.toPx() }
            val highlightRadius = with(density) { 5.dp.toPx() }
            val ruleWidth = with(density) { 1.dp.toPx() }

            val mapper = remember(series.id, windowStart, asOf, widthPx, heightPx) {
                ChartCoordinateMapper(
                    startDate = windowStart,
                    endDate = asOf,
                    valueMin = domain.start,
                    valueMax = domain.endInclusive,
                    width = widthPx,
                    height = heightPx,
                )
            }
            val runs = remember(points) { ChartSeriesGrouping.runs(points) }
            val scrubHue = colors.inkMuted.copy(alpha = 0.5f)

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            with(ChartPrimitives) {
                                drawRuns(runs, mapper, hue, strokeWidth)
                                drawPoints(points, mapper, hue, pointRadius)
                                // The ONE shared scrub day, read in the DRAW phase (draw-only invalidation).
                                val day = scrubDay()
                                if (day != null) {
                                    drawScrubRule(mapper.x(day), scrubHue, ruleWidth)
                                    val logged = ChartScrubSelection.resolve(day, points)?.point
                                    if (logged != null) drawHighlight(logged, mapper, hue, highlightRadius)
                                }
                            }
                        }
                    },
            )

            // The bespoke per-point TalkBack overlay (Plan 03) — one focusable node per scrubbable day,
            // resolved gap-honestly through the SAME `ChartScrubSelection.resolve` as the visual scrub, with
            // this lane's own real-unit readout (a sleep node reads "8 h", a stress node "8 of 10"). Its
            // testTagRoot is the lane tag, so nodes are `explore.lane.<id>.point.<epochDay>` / `.notLogged`.
            val semanticNodes = remember(series.id, points, windowStart, asOf) {
                trendSemanticNodes(
                    domainStart = windowStart,
                    domainEnd = asOf,
                    points = points,
                    testTagRoot = laneTag,
                    describeValue = { laneReadout(series, it.value) },
                )
            }
            ChartSemanticsOverlay(
                nodes = semanticNodes,
                modifier = Modifier.matchParentSize(),
            )
        }

        // The shared x-domain endpoints, so the lane reads as a dated window (not a bare squiggle).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LANE_DATE_FORMAT.format(windowStart), style = bloomChipLabel, fontWeight = FontWeight.Normal, color = colors.inkMuted)
            Text(LANE_DATE_FORMAT.format(asOf), style = bloomChipLabel, fontWeight = FontWeight.Normal, color = colors.inkMuted)
        }
    }
}

private val LANE_DATE_FORMAT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d")
