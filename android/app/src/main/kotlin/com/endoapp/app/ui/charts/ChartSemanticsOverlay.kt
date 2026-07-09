// The bespoke per-data-point TalkBack semantics overlay — the HIGH-complexity, first-class
// accessibility deliverable (22-CONTEXT §Accessibility, 22-UI-SPEC §Accessibility). The Android port of
// the iOS non-gesture VoiceOver path (`InsightTrendChart.swift`'s AXChartDescriptor / accessibility
// element per data point).
//
// WHY THIS EXISTS (the strongest argument for the owned-Canvas decision): a Compose `Canvas` — including
// any `drawText` — is COMPLETELY INVISIBLE to TalkBack, and Android has NO Audio Graph / `AXChartDescriptor`
// equivalent. A charting library would give TalkBack nothing honesty-aware. So every chart gets a
// hand-built stack of focusable, INVISIBLE semantics nodes — one per rendered/scrubbable day — laid OVER
// the Canvas, each reading its text from the SAME gap-honest `ChartScrubSelection.resolve` the VISUAL
// scrub uses (Plan 02, `TrendChart`). The "not logged vs a real 0" question is therefore answered ONCE,
// in one resolver, and pinned by `ChartSemanticsTest`.
//
// REUSE (the whole point of factoring it here): [ChartSemanticNode] + [ChartSemanticsOverlay] are the
// shared honesty-aware semantics core. The trend chart wires it via [trendSemanticNodes]; Plan 05's
// quantitative lanes reuse the SAME [trendSemanticNodes] (a different domain/describe/hue), and Plan 06's
// scatter builds [ChartSemanticNode]s for its `(x, y)` cells — all announcing through this one overlay,
// so the honesty contract extends into every chart's semantics tree without re-solving gap-vs-0.
package com.endoapp.app.ui.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ChartsCopy
import com.endoapp.insights.ChartScrubSelection
import com.endoapp.insights.InsightPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The Android floor for an interactive / focus target (up from iOS 44pt) — 22-UI-SPEC §Spacing. */
private val MIN_TOUCH_TARGET = 48.dp

/** Month + day label (e.g. "January 6") — the iOS `.month().day()` a11y label. A FORMAT, not audited copy. */
private val A11Y_DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/**
 * One TalkBack-focusable node in a chart's semantics overlay. Presentation-agnostic so lanes (Plan 05)
 * and scatter (Plan 06) produce the SAME node type:
 *  - [contentDescription] is the node's label (a trend/lane day → the date; a scatter cell → the axis pair).
 *  - [stateDescription] is the honesty-carrying value ("Severity 6 of 10" / "Not logged" / "3 days").
 *  - [xFraction] is the node's position along the shared x-axis in `[0,1]`; nodes are emitted in ascending
 *    [xFraction] so both the layout AND the forced `traversalIndex` step TalkBack left→right (chronological
 *    for time charts).
 */
@Immutable
data class ChartSemanticNode(
    val testTag: String,
    val contentDescription: String,
    val stateDescription: String,
    val xFraction: Float,
)

/**
 * Build the per-day semantics nodes for a time-series chart (trend or a quantitative Explore lane),
 * gap-honest THROUGH the resolver. Emits ONE node for EVERY calendar day in the domain
 * `[domainStart, domainEnd]` (so every scrubbable day is TalkBack-reachable, in date order) — a logged day
 * gets `${testTagRoot}.point.<epochDay>` + a real readout; a gap gets `${testTagRoot}.point.<epochDay>.notLogged`
 * + [notLoggedText].
 *
 * The gap-vs-value decision lives ONLY in [resolver] (defaulting to the SAME `ChartScrubSelection.resolve`
 * the visual scrub uses): the branch is on the resolver's `point == null` — NEVER on value truthiness — so a
 * logged severity-0 day (a REAL point, value 0) announces "Severity 0 of 10", not "Not logged" (absence ≠ 0).
 */
fun trendSemanticNodes(
    domainStart: LocalDate,
    domainEnd: LocalDate,
    points: List<InsightPoint>,
    testTagRoot: String,
    describeValue: (InsightPoint) -> String,
    notLoggedText: String = ChartsCopy.NOT_LOGGED,
    dateLabel: (LocalDate) -> String = { it.format(A11Y_DATE_LABEL) },
    resolver: (LocalDate, List<InsightPoint>) -> ChartScrubSelection.Resolution? = ChartScrubSelection::resolve,
): List<ChartSemanticNode> {
    val totalDays = maxOf(1L, ChronoUnit.DAYS.between(domainStart, domainEnd))
    val nodes = ArrayList<ChartSemanticNode>()
    var day = domainStart
    while (!day.isAfter(domainEnd)) {
        val point = resolver(day, points)?.point // gap-vs-value is the resolver's call, never a value test
        val epochDay = day.toEpochDay()
        val fraction = ChronoUnit.DAYS.between(domainStart, day).toFloat() / totalDays.toFloat()
        nodes += if (point == null) {
            ChartSemanticNode(
                testTag = "$testTagRoot.point.$epochDay.notLogged",
                contentDescription = dateLabel(day),
                stateDescription = notLoggedText,
                xFraction = fraction,
            )
        } else {
            ChartSemanticNode(
                testTag = "$testTagRoot.point.$epochDay",
                contentDescription = dateLabel(day),
                stateDescription = describeValue(point),
                xFraction = fraction,
            )
        }
        day = day.plusDays(1)
    }
    return nodes
}

/**
 * The reusable, INVISIBLE semantics overlay. Draws NOTHING visible; it emits a transparent focusable
 * semantics node per [ChartSemanticNode], positioned along the x-axis and sized to the ≥48dp focus floor,
 * plus an OPTIONAL chart-level worded summary node.
 *
 * Honesty & focus contract:
 *  - Each node is `≥48dp` and carries `contentDescription` + `stateDescription` from its [ChartSemanticNode]
 *    (already resolved gap-honestly by the builder). It has NO `pointerInput`, so it never intercepts the
 *    underlying visual scrub gesture — it is purely a reader surface.
 *  - Nodes are emitted in ascending [ChartSemanticNode.xFraction] and pinned with a forced `traversalIndex`
 *    inside an `isTraversalGroup`, so TalkBack steps them in chronological (left→right) order regardless of
 *    geometry overlap.
 *  - Each node is a `mergeDescendants` semantics boundary carrying its own `testTag`, so a container testTag
 *    can never mask the per-point ids (22-UI-SPEC).
 *
 * [summary] (when non-null) emits a `${summaryTestTag}` node — the chart is never the sole carrier of
 * meaning. NOTE: `TrendChart` renders a VISIBLE summary `Text` above the chart (already TalkBack-readable),
 * so it passes `summary = null` here to avoid a double announcement; charts WITHOUT a visible summary
 * (e.g. Plan 05 event lanes: "{name} — logged on {n} days") pass their summary string.
 */
@Composable
fun ChartSemanticsOverlay(
    nodes: List<ChartSemanticNode>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryTestTag: String? = null,
) {
    BoxWithConstraints(modifier.semantics { isTraversalGroup = true }) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val halfTargetPx = with(density) { (MIN_TOUCH_TARGET / 2).toPx() }

        if (summary != null && summaryTestTag != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .requiredHeight(MIN_TOUCH_TARGET)
                    .testTag(summaryTestTag)
                    .semantics(mergeDescendants = true) {
                        contentDescription = summary
                        traversalIndex = -1f // read the summary BEFORE stepping the per-point nodes
                    },
            )
        }

        nodes.forEachIndexed { index, node ->
            val dx = (node.xFraction * widthPx - halfTargetPx).roundToInt()
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(dx, 0) }
                    .requiredSize(MIN_TOUCH_TARGET)
                    .testTag(node.testTag)
                    .semantics(mergeDescendants = true) {
                        contentDescription = node.contentDescription
                        stateDescription = node.stateDescription
                        traversalIndex = index.toFloat() // chronological focus order, pinned
                    },
            )
        }
    }
}
