// One event-marker Explore lane — the Android port of `EventLaneChart.swift`. A presence series (food
// tags, clots, PRN, medication-taken, bleeding-as-event) renders as a ROW of date-aligned occurrence
// ticks — ONE mark per logged-present day at a CONSTANT y — NEVER a connected 0/1 line.
//
// STRUCTURAL HONESTY (22-UI-SPEC §Chart Rendering 3): the input is `List<LocalDate>` occurrence days
// (`ExploreSeriesExtraction.occurrenceDays`), which carries NO value field — so a 0/1 line is
// UNREPRESENTABLE by construction. A day with no occurrence has NO tick ("not logged that event", never
// "logged absent"). The lane carries a SUMMARY node ("{name} — logged on {n} days") INSTEAD of a value
// axis (a value axis would imply a "1" the data does not carry). Shares the ONE x-domain + scrub rule.
//
// The ~44dp lane draws its own scrub rule from the shared [scrubDay] in the DRAW phase; the per-tick
// TalkBack nodes reuse the Plan-03 `ChartSemanticsOverlay` (each ≥48dp), with a "Logged" stateDescription.
package com.endoapp.app.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.app.ui.charts.ChartPrimitives.drawScrubRule
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.InsightPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The event lane height (22-UI-SPEC §Spacing). The interactive scrub region is padded to ≥48dp at the
 *  stack level; the visual lane is 44dp. */
private val EVENT_LANE_HEIGHT = 44.dp

/** Month + day a11y label (matches the quantitative overlay's format) — a FORMAT, not audited copy. */
private val EVENT_DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/**
 * One event-marker lane: a name title then a Canvas drawing one tick per [occurrenceDays] entry at a
 * constant y, sharing the [windowStart]…[asOf] x-domain and the ONE shared [scrubDay]. [occurrenceDays]
 * is `List<LocalDate>` — there is no value axis (a 0/1 line is structurally unrepresentable).
 */
@Composable
fun EventLaneChart(
    series: ExploreSeries,
    occurrenceDays: List<LocalDate>,
    windowStart: LocalDate,
    asOf: LocalDate,
    scrubDay: () -> LocalDate?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val laneTag = "explore.lane.${series.id}.event"
    val tickHue = colors.plum // event ticks are plum (22-UI-SPEC §Chart Rendering 2)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = series.displayName,
            style = bloomChipLabel,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkSecondary,
            modifier = Modifier.testTag("$laneTag.title"),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(EVENT_LANE_HEIGHT)
                .testTag(laneTag),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val tickRadius = with(density) { 4.dp.toPx() }
            val ruleWidth = with(density) { 1.dp.toPx() }

            // A date→x mapper only (the y-domain is a dummy 0…1 — an event lane has NO value axis). Ticks
            // sit at a constant y (lane centre).
            val mapper = remember(series.id, windowStart, asOf, widthPx, heightPx) {
                ChartCoordinateMapper(
                    startDate = windowStart,
                    endDate = asOf,
                    valueMin = 0.0,
                    valueMax = 1.0,
                    width = widthPx,
                    height = heightPx,
                )
            }
            val scrubHue = colors.inkMuted.copy(alpha = 0.5f)
            val centreY = heightPx / 2f

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            // One filled tick per logged-present day at the constant centre y — NEVER a
                            // connected line (there is no value to connect).
                            for (day in occurrenceDays) {
                                drawCircle(
                                    color = tickHue,
                                    radius = tickRadius,
                                    center = Offset(mapper.x(day), centreY),
                                )
                            }
                            val day = scrubDay()
                            if (day != null) {
                                with(ChartPrimitives) { drawScrubRule(mapper.x(day), scrubHue, ruleWidth) }
                            }
                        }
                    },
            )

            // Per-tick TalkBack nodes + a summary node ("{name} — logged on {n} days") INSTEAD of a value
            // axis. Reuses the Plan-03 overlay; nodes are `explore.lane.<id>.event.point.<epochDay>` with a
            // "Logged" stateDescription (never a "1"). Only OCCURRENCE days get a node (a non-occurrence day
            // carries no tick, so it announces nothing here — presence-absent ≠ logged-absent).
            val totalDays = maxOf(1L, ChronoUnit.DAYS.between(windowStart, asOf))
            val nodes = remember(series.id, occurrenceDays, windowStart, asOf) {
                occurrenceDays
                    .filter { !it.isBefore(windowStart) && !it.isAfter(asOf) }
                    .sorted()
                    .map { day ->
                        ChartSemanticNode(
                            testTag = "$laneTag.point.${day.toEpochDay()}",
                            contentDescription = day.format(EVENT_DATE_LABEL),
                            stateDescription = ExploreCopy.EVENT_TICK,
                            xFraction = ChronoUnit.DAYS.between(windowStart, day).toFloat() / totalDays.toFloat(),
                        )
                    }
            }
            ChartSemanticsOverlay(
                nodes = nodes,
                modifier = Modifier.matchParentSize(),
                summary = ExploreCopy.eventLaneSummary(series.displayName, occurrenceDays.size),
                summaryTestTag = "$laneTag.summary",
            )
        }
    }
}
