// The Explore tab — the Android port of `ExploreView.swift` + `ExploreLanesView.swift`, filling the
// Phase-19 Explore placeholder (AINS-04). Explore BYPASSES `InsightsEngine`/`HonestyGate` and shows RAW
// descriptive data (no confidence pills, no associations, no causal framing) read via the consent-gated
// `daySnapshots()` seam; the suppression flag still gates it (hides bleeding).
//
// LANES MODE (this plan): the suppression-filtered series picker (≤4 quantitative cap) →
//   • QUANTITATIVE series as aligned per-scale OVERLAY lanes — each a SEPARATE lane on its OWN fixed
//     real-unit domain (NO shared y-axis, NO dual y-axis, NO silent normalization; H3/H4), then
//   • PRESENCE series as event-marker lanes (occurrence ticks — never a 0/1 line),
//   sharing ONE x (time) domain and ONE scrub with a single multi-series tooltip that reads each lane's
//   EXACT-day value (gap → "Not logged"). Honest empty/sparse states. Scatter mode is a Plan-06 placeholder.
//
// PRIVACY (22-UI-SPEC §Privacy, threat T-22-01i): the selection / mode / range live in EPHEMERAL state
// ONLY — never persisted (persisting a series interest would leak health-interest metadata outside the
// consent-gated store). The screen reads snapshots + suppressed through injected SUSPEND seams so it is
// testable off a fake and consent-by-construction is preserved (it is composed ONLY inside AppGate's
// Consented arm).
package com.endoapp.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.Disclaimer
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.DisclaimerBanner
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.designsystem.bloomTitle
import com.endoapp.app.ui.charts.ChartCoordinateMapper
import com.endoapp.app.ui.charts.EventLaneChart
import com.endoapp.app.ui.charts.ExploreLaneChart
import com.endoapp.insights.ChartScrubSelection
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ExploreSeriesExtraction
import com.endoapp.insights.SeriesNormalization
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The two Explore surfaces. Lanes is the default + the only one this plan fills; Scatter lands in Plan 06. */
enum class ExploreMode(val label: String) {
    Lanes(ExploreCopy.MODE_LANES),
    Scatter(ExploreCopy.MODE_SCATTER),
}

/** The per-lane minimum logged days in the window before the stack is considered non-sparse. */
private const val MIN_LOGGED_DAYS = 2

/** Medium absolute date (e.g. "Jan 6, 2026") for the tooltip header — a FORMAT, not audited copy. */
private val TOOLTIP_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * The Explore tab entry point. [loadSnapshots] / [loadSuppressed] are the consent-gated SUSPEND seams
 * (bound in AppGate's Consented arm to `repository::daySnapshots` / the treatment mode). [today] is the
 * asOf right edge; [initialSelection] / [initialRange] seed the EPHEMERAL selection (a test seam — never
 * persisted).
 */
@Composable
fun ExploreScreen(
    loadSnapshots: suspend () -> List<DaySnapshot>,
    loadSuppressed: suspend () -> Boolean,
    today: LocalDate = LocalDate.now(),
    initialSelection: Set<ExploreSeries> = emptySet(),
    initialRange: ExploreRange = DEFAULT_EXPLORE_RANGE,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current

    var snapshots by remember { mutableStateOf<List<DaySnapshot>>(emptyList()) }
    // Fail-closed: assume suppressed until we know, so a bleeding chip never flashes before the load.
    var suppressed by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(ExploreMode.Lanes) }
    var selected by remember { mutableStateOf(initialSelection) }
    var range by remember { mutableStateOf(initialRange) }

    LaunchedEffect(Unit) {
        suppressed = loadSuppressed()
        snapshots = loadSnapshots()
    }

    val offered = remember(suppressed) { ExploreSelection.offered(suppressed) }
    // Keep the ephemeral selection within the offered set (defense-in-depth if suppression flips).
    val offeredSet = offered.toSet()
    val activeSelection = selected.filter { it in offeredSet }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("explore.screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Serif title.
        Text(
            text = ExploreCopy.TITLE,
            style = bloomTitle,
            color = colors.ink,
            modifier = Modifier.testTag("explore.header"),
        )

        // The pinned not-medical-advice disclaimer (reused; renders Disclaimer.text verbatim).
        DisclaimerBanner(text = Disclaimer.text, modifier = Modifier.testTag("explore.disclaimer"))

        // The Lanes | Scatter mode toggle (default Lanes).
        ExploreSegmentedToggle(
            options = ExploreMode.entries,
            selectedOption = mode,
            onSelect = { mode = it },
            label = { it.label },
            optionTestTag = { if (it == ExploreMode.Lanes) "explore.mode.lanes" else "explore.mode.scatter" },
            containerTestTag = "explore.mode",
        )

        when (mode) {
            ExploreMode.Lanes -> {
                SeriesPicker(
                    offered = offered,
                    selected = activeSelection,
                    onToggle = { series ->
                        selected = if (series in selected) selected - series else selected + series
                    },
                    range = range,
                    onRange = { range = it },
                )
                LanesBody(
                    selection = offered.filter { it in activeSelection }, // stable catalog order
                    snapshots = snapshots,
                    windowStart = today.minusDays(range.days),
                    asOf = today,
                )
            }
            ExploreMode.Scatter ->
                // Honest placeholder — Plan 06 fills Scatter mode + the deep-link into this screen.
                Text(
                    text = ExploreCopy.SCATTER_SOON,
                    style = bloomBody,
                    color = colors.inkSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore.scatter.placeholder"),
                )
        }
    }
}

/**
 * The Lanes-mode body: the empty state (nothing selected), the sparse state (nothing clears the per-lane
 * minimum), or the aligned lane stack + shared scrub + multi-series tooltip.
 */
@Composable
private fun LanesBody(
    selection: List<ExploreSeries>,
    snapshots: List<DaySnapshot>,
    windowStart: LocalDate,
    asOf: LocalDate,
) {
    val colors = LocalBloomColors.current

    if (selection.isEmpty()) {
        HonestState(
            title = ExploreCopy.EMPTY_TITLE,
            body = ExploreCopy.EMPTY_BODY,
            testTag = "explore.empty",
        )
        return
    }

    // Window-clipped snapshots feed both the sparse check and the lanes.
    val windowSnapshots = remember(snapshots, windowStart, asOf) {
        snapshots.filter { !it.day.isBefore(windowStart) && !it.day.isAfter(asOf) }
    }

    val quantSeries = selection.filter { it.scaleKind.isQuantitative }
    val eventSeries = selection.filter { !it.scaleKind.isQuantitative }

    val quantPoints = remember(quantSeries, windowSnapshots) {
        quantSeries.associateWith { ExploreSeriesExtraction.quantitativePoints(windowSnapshots, it) }
    }
    val eventDays = remember(eventSeries, windowSnapshots) {
        eventSeries.associateWith { ExploreSeriesExtraction.occurrenceDays(windowSnapshots, it) }
    }

    // Sparse iff NO single lane clears the per-lane minimum (≥2 logged days in the window).
    val hasEnough = quantPoints.values.any { it.size >= MIN_LOGGED_DAYS } ||
        eventDays.values.any { it.size >= MIN_LOGGED_DAYS }
    if (!hasEnough) {
        HonestState(
            title = ExploreCopy.SPARSE_TITLE,
            body = ExploreCopy.SPARSE_BODY,
            testTag = "explore.sparse",
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("explore.lanes"),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        // One shared date→x mapper (a dummy 0…1 y — used only for the scrub x + tooltip placement).
        val xMapper = remember(windowStart, asOf, widthPx) {
            ChartCoordinateMapper(windowStart, asOf, 0.0, 1.0, widthPx, 1f)
        }

        // The ONE shared scrub day — day-granular, ephemeral, reset when the window changes. Each lane
        // reads it in the DRAW phase (no composition churn); the tooltip reads it in composition.
        val scrubState = remember(windowStart, asOf) { mutableStateOf<LocalDate?>(null) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ALL quantitative lanes first (each its own real-unit domain), then ALL event lanes — every
            // lane shares the SAME x-domain + the SAME scrub day.
            quantSeries.forEach { series ->
                ExploreLaneChart(
                    series = series,
                    points = quantPoints[series].orEmpty(),
                    windowStart = windowStart,
                    asOf = asOf,
                    scrubDay = { scrubState.value },
                )
            }
            eventSeries.forEach { series ->
                EventLaneChart(
                    series = series,
                    occurrenceDays = eventDays[series].orEmpty(),
                    windowStart = windowStart,
                    asOf = asOf,
                    scrubDay = { scrubState.value },
                )
            }
        }

        // The ONE shared scrub surface over the whole stack — a single tap/drag maps x→day for every lane.
        // On top so it owns the gesture; the a11y overlays below have no pointerInput so they never fight it.
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier
                .matchParentSize()
                .testTag("explore.lanes.scrub")
                .pointerInput(xMapper) {
                    detectTapGestures { offset ->
                        val day = xMapper.dateAt(offset.x)
                        if (day != scrubState.value) scrubState.value = day
                    }
                }
                .pointerInput(xMapper) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scrubState.value = null },
                        onDragCancel = { scrubState.value = null },
                    ) { change, _ ->
                        val day = xMapper.dateAt(change.position.x)
                        if (day != scrubState.value) scrubState.value = day
                    }
                },
        )

        // ONE multi-series tooltip card over the whole stack (not per-lane). Isolated in its own composable
        // reading `scrubState` so ONLY it recomposes on a day-crossing (the lanes stay put).
        MultiSeriesTooltip(
            scrubState = scrubState,
            quantSeries = quantSeries,
            eventSeries = eventSeries,
            quantPoints = quantPoints,
            snapshots = windowSnapshots,
            xMapper = xMapper,
            widthPx = widthPx,
        )
    }
}

/**
 * The single multi-series scrub tooltip: the scrubbed date once, then one line per visible series — a
 * quantitative line resolves via [ChartScrubSelection.resolve] (real-unit readout or "Not logged"); an
 * event line resolves via `series.isPresent` on that day's snapshot ("Logged" / "Not logged"). The visual
 * card is `clearAndSetSemantics` (hidden) and each line carries its OWN testTag un-masked by the card, so
 * the per-series ids ride an un-shadowed semantics surface (22-UI-SPEC §Accessibility multi-series proxy).
 */
@Composable
private fun BoxScope.MultiSeriesTooltip(
    scrubState: MutableState<LocalDate?>,
    quantSeries: List<ExploreSeries>,
    eventSeries: List<ExploreSeries>,
    quantPoints: Map<ExploreSeries, List<com.endoapp.insights.InsightPoint>>,
    snapshots: List<DaySnapshot>,
    xMapper: ChartCoordinateMapper,
    widthPx: Float,
) {
    val colors = LocalBloomColors.current
    val day = scrubState.value ?: return
    val snapForDay = snapshots.firstOrNull { it.day == day }

    var tooltipWidthPx by remember { mutableStateOf(0) }
    val scrubX = xMapper.x(day)
    val leftPx = scrubX.toInt().coerceIn(0, (widthPx.toInt() - tooltipWidthPx).coerceAtLeast(0))

    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(leftPx, 0) }
            .onSizeChanged { tooltipWidthPx = it.width }
            .background(colors.card, RoundedCornerShape(8.dp))
            .border(1.dp, colors.strokeSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("explore.scrub.tooltip"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // The scrubbed date once.
        Text(
            text = day.format(TOOLTIP_DATE),
            style = bloomBody,
            color = colors.inkSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )

        // Quantitative lines — exact-day resolve; a gap reads "Not logged".
        quantSeries.forEach { series ->
            val point = ChartScrubSelection.resolve(day, quantPoints[series].orEmpty())?.point
            if (point != null) {
                val readout = SeriesNormalization.readout(point.value, series.scaleKind, series.unit)
                Text(
                    text = ExploreCopy.seriesReadoutLine(series.displayName, readout),
                    style = bloomBody,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    modifier = Modifier.testTag("explore.scrub.tooltip.${series.id}"),
                )
            } else {
                Text(
                    text = ExploreCopy.seriesNotLoggedLine(series.displayName),
                    style = bloomBody,
                    color = colors.inkMuted,
                    modifier = Modifier.testTag("explore.scrub.tooltip.${series.id}.notLogged"),
                )
            }
        }

        // Event lines — presence on that day's snapshot (events have NO scrub-resolver path, Pitfall 2).
        eventSeries.forEach { series ->
            val present = snapForDay != null && series.isPresent(snapForDay)
            if (present) {
                Text(
                    text = ExploreCopy.seriesLoggedLine(series.displayName),
                    style = bloomBody,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    modifier = Modifier.testTag("explore.scrub.tooltip.${series.id}"),
                )
            } else {
                Text(
                    text = ExploreCopy.seriesNotLoggedLine(series.displayName),
                    style = bloomBody,
                    color = colors.inkMuted,
                    modifier = Modifier.testTag("explore.scrub.tooltip.${series.id}.notLogged"),
                )
            }
        }
    }
}

/** A warm centered empty/sparse state (title + body). */
@Composable
private fun HonestState(title: String, body: String, testTag: String) {
    val colors = LocalBloomColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = bloomH3, color = colors.ink, textAlign = TextAlign.Center)
        Text(text = body, style = bloomBody, color = colors.inkSecondary, textAlign = TextAlign.Center)
    }
}
