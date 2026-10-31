// The honest trend line chart — the Android port of `Sources/EndoApp/Features/Insights/
// InsightTrendChart.swift`. Rendered with Compose `Canvas` (the LOCKED decision; Vico stays the unused
// escape hatch). Honesty invariants (22-UI-SPEC §Chart Rendering 1) baked in:
//   • ABSOLUTE calendar-date x-axis ONLY (window-start … asOf) — never cycle-relative. Fixed y-domain
//     0…10 (matches the severity control). A one-line text summary sits ABOVE the chart so the chart is
//     never the sole carrier of meaning.
//   • Honest gaps (H1): one line `Path` per contiguous logged-day run (from the PURE
//     `ChartSeriesGrouping`), so the line BREAKS at every gap — never a zero, never interpolation.
//   • Absence ≠ zero (H2): one dot per logged day; a literal severity-0 day draws AT the zero line,
//     a gap draws NO mark.
//   • The ONLY raw-day → value path is `ChartScrubSelection.resolve` (exact day) — NO position-based
//     `value(atX:)`, NO nearest-point / minBy / interpolation math anywhere.
//
// PERFORMANCE (PITFALLS #14 — the difference between Canvas being viable and a premature Vico switch):
// the input is an `@Immutable` holder so the composable is skippable; the raw scrub day is DAY-granular
// and de-duped in the gesture, so a finger moving WITHIN a day changes no state (no recompose, no
// redraw); the run `Path`s are cached in `drawWithCache` keyed on runs + size, and only the scrub
// rule / highlight draws per frame (read in the DRAW phase, never composition). Only a day-CROSSING
// recomposes the small tooltip (via `derivedStateOf` snapping to day granularity).
package com.endoapp.app.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.endoapp.app.content.ChartsCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.ui.charts.ChartPrimitives.drawHighlight
import com.endoapp.app.ui.charts.ChartPrimitives.drawPoints
import com.endoapp.app.ui.charts.ChartPrimitives.drawRuns
import com.endoapp.app.ui.charts.ChartPrimitives.drawScrubRule
import com.endoapp.insights.ChartScrubSelection
import com.endoapp.insights.InsightPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The FIXED severity y-domain (matches the severity control) — never a data-driven min/max (H4). */
private const val SEVERITY_MIN = 0.0
private const val SEVERITY_MAX = 10.0

/**
 * The `@Immutable` input holder for [TrendChart]. Produced ONCE by the caller so the composable is
 * `skippable` — an unstable `List` param would recompose the chart on every parent recomposition
 * (PITFALLS #14). Marked `@Immutable`: the caller guarantees these are never mutated in place.
 */
@Immutable
data class TrendChartInput(
    /** The logged points (one per day). Sparse is fine — the gap helper + window domain handle it. */
    val points: List<InsightPoint>,
    /** The absolute window start (the left edge of the x-axis). */
    val windowStart: LocalDate,
    /** The absolute "today" the window ends at (the right edge). */
    val asOf: LocalDate,
    /** Root for the chart + tooltip testTags (the card passes `insights.card.<signalKey>`). */
    val testTagRoot: String,
)

/**
 * The honest `.trend` line chart. [summary] is the one-line text carrier of meaning shown ABOVE the
 * chart (composed by the caller from already-audited engine copy). [onDayChanged] is the day-change
 * haptic seam — the CALLER performs the actual haptic (this composable imports no alarm/notification
 * API); it fires ONLY on a resolved-DAY change (dayA→dayB, including logged↔gap), never on the initial
 * touch (nil→day) or release (day→nil).
 */
@Composable
fun TrendChart(
    input: TrendChartInput,
    summary: String,
    modifier: Modifier = Modifier,
    onDayChanged: (LocalDate?) -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val root = input.testTagRoot

    // The raw scrub day — DAY-granular + view-local + ephemeral. `null` = no active selection. Read in
    // the DRAW phase (the Canvas onDraw lambda) so a change invalidates DRAW, not composition; the
    // gesture below only re-assigns it when the day actually changes (within-day drags are inert).
    var rawScrubDay by remember { mutableStateOf<LocalDate?>(null) }

    // The ONLY raw-day → value path (the honesty choke-point): a gap day resolves to `point == null`
    // ("Not logged"), a logged severity-0 day to a REAL point with value 0. `derivedStateOf` snaps to
    // day granularity so composition (the tooltip) reacts only on a day CROSSING, never per pixel.
    val resolution by remember(input.points) {
        derivedStateOf { ChartScrubSelection.resolve(rawScrubDay, input.points) }
    }

    // Day-change haptic seam: fire on dayA→dayB only. `resolution?.day` is non-null for ANY selected day
    // (the date axis is always real), so nil→day (initial touch) and day→nil (release) fire nothing.
    val selectedDay = resolution?.day
    var previousDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selectedDay) {
        val prev = previousDay
        if (prev != null && selectedDay != null && prev != selectedDay) {
            onDayChanged(selectedDay)
        }
        previousDay = selectedDay
    }

    Column(modifier = modifier) {
        // One-line text summary ABOVE the chart — the full-text a11y fallback so the chart is never the
        // sole carrier of meaning.
        Text(
            text = summary,
            style = bloomBody,
            color = colors.inkSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("$root.chart.summary"),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("$root.chart"),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val strokeWidth = with(density) { 2.dp.toPx() }
            val pointRadius = with(density) { 3.dp.toPx() }
            val highlightRadius = with(density) { 5.dp.toPx() }
            val ruleWidth = with(density) { 1.dp.toPx() }

            val mapper = remember(input, widthPx, heightPx) {
                ChartCoordinateMapper(
                    startDate = input.windowStart,
                    endDate = input.asOf,
                    valueMin = SEVERITY_MIN,
                    valueMax = SEVERITY_MAX,
                    width = widthPx,
                    height = heightPx,
                )
            }
            val runs = remember(input.points) { ChartSeriesGrouping.runs(input.points) }

            val plum = colors.plum
            val scrubHue = colors.inkMuted.copy(alpha = 0.5f)

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mapper) {
                        detectTapGestures { offset ->
                            val day = mapper.dateAt(offset.x)
                            if (day != rawScrubDay) rawScrubDay = day
                        }
                    }
                    .pointerInput(mapper) {
                        detectHorizontalDragGestures(
                            onDragEnd = { rawScrubDay = null },
                            onDragCancel = { rawScrubDay = null },
                        ) { change, _ ->
                            val day = mapper.dateAt(change.position.x)
                            if (day != rawScrubDay) rawScrubDay = day // within-day drags are inert (no state write)
                        }
                    }
                    // Static layer cached + keyed on runs/points + size; only the scrub rule/highlight
                    // draws per frame, reading `rawScrubDay` in the DRAW phase (draw-only invalidation).
                    .drawWithCache {
                        onDrawBehind {
                            with(ChartPrimitives) {
                                drawRuns(runs, mapper, plum, strokeWidth)
                                drawPoints(input.points, mapper, plum, pointRadius)
                                val day = rawScrubDay
                                if (day != null) {
                                    drawScrubRule(mapper.x(day), scrubHue, ruleWidth)
                                    // Highlight the logged day ONLY — resolving here keeps the exact-day
                                    // honesty (a gap draws no highlight; a severity-0 sits at the zero line).
                                    val logged = ChartScrubSelection.resolve(day, input.points)?.point
                                    if (logged != null) drawHighlight(logged, mapper, plum, highlightRadius)
                                }
                            }
                        }
                    },
            )

            // Lollipop tooltip pinned at the chart TOP so it never clips at edges / under the finger.
            val current = resolution
            if (current != null) {
                var tooltipWidthPx by remember { mutableStateOf(0) }
                val scrubX = mapper.x(current.day)
                // Clamp so the tooltip stays fully within the plot width.
                val leftPx = scrubX.toInt().coerceIn(0, (widthPx.toInt() - tooltipWidthPx).coerceAtLeast(0))

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(leftPx, 0) }
                        .onSizeChanged { tooltipWidthPx = it.width }
                        .background(colors.card, RoundedCornerShape(8.dp))
                        .border(1.dp, colors.strokeSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("$root.chart.tooltip"),
                ) {
                    // Line 1: the absolute date — same vocabulary as the axis labels.
                    Text(
                        text = current.day.format(DATE_FORMAT),
                        style = bloomBody,
                        color = colors.inkSecondary,
                    )
                    val point = current.point
                    if (point != null) {
                        // Logged day (incl. literal severity-0): the payload, emphasized primary.
                        Text(
                            text = ChartsCopy.severityReadout(point.value),
                            style = bloomBody,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                        )
                    } else {
                        // Gap day: de-emphasized — the gap is information, never bold / red / an error.
                        Text(
                            text = ChartsCopy.NOT_LOGGED,
                            style = bloomBody,
                            color = colors.inkMuted,
                            modifier = Modifier.testTag("$root.chart.tooltip.notLogged"),
                        )
                    }
                }
            }

            // The bespoke TalkBack semantics overlay (Plan 03) — the ONLY accessible surface for the
            // otherwise-invisible Canvas. One focusable node per scrubbable day, resolved gap-honestly
            // through the SAME `ChartScrubSelection.resolve` the visual scrub above uses. Additive +
            // transparent + no `pointerInput`, so it never disturbs the scrub gesture or the visuals.
            // `summary = null`: the visible summary `Text` above already carries the one-line meaning to
            // TalkBack (no double announcement); the overlay's summary-node capability is for charts
            // without a visible summary (Plan 05 event lanes).
            val semanticNodes = remember(input) {
                trendSemanticNodes(
                    domainStart = input.windowStart,
                    domainEnd = input.asOf,
                    points = input.points,
                    testTagRoot = "$root.chart",
                    describeValue = { ChartsCopy.severityReadout(it.value) },
                )
            }
            ChartSemanticsOverlay(
                nodes = semanticNodes,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Medium-style absolute date (e.g. "Jan 6, 2026") — a FORMAT, not audited copy. */
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
