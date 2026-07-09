package com.endoapp.app.ui.charts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ChartsCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.InsightPoint
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pins the bespoke per-data-point TalkBack semantics overlay (Plan 03) on the JVM Robolectric+Compose
 * lane — the ONLY accessible surface for the otherwise-invisible Canvas. Proves the honesty contract
 * extends into the semantics TREE, resolved through the SAME `ChartScrubSelection.resolve` the VISUAL
 * scrub uses (Plan 02):
 *   • ONE focusable node per scrubbable day, in chronological (date) order (left→right by epochDay).
 *   • A GAP day announces "Not logged" (via `…point.<epochDay>.notLogged`) — never a fabricated 0.
 *   • A logged severity-0 day announces a REAL "Severity 0 of 10" (via `…point.<epochDay>`, no
 *     `.notLogged`) — absence ≠ zero, answered in this ONE tested place.
 *   • The chart carries a worded summary node (never the sole carrier of meaning).
 *   • Every reader node is a ≥48dp focus target.
 *
 * Reuses the Plan-02 fixture (logged spine + a gap + a logged severity-0) over the SAME `TrendChart`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChartSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val root = "insights.card.symptom.pelvicPain.severity"
    private val chartTag = "$root.chart"
    private val pointPrefix = "$chartTag.point."

    // A11y date label mirrors the overlay's `MMMM d` format (iOS `.month().day()`).
    private val dateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
    private val summary = "Pelvic pain severity, last 30 days."

    // A 10-day window (Jan 1 … Jan 11 → 11 domain days). Logged: Jan 2 (severity 0 — REAL), Jan 3 (5),
    // Jan 9 (7). All other days are honest gaps → 11 total scrubbable-day nodes.
    private val windowStart = LocalDate.of(2026, 1, 1)
    private val asOf = LocalDate.of(2026, 1, 11)
    private val loggedZeroDay = LocalDate.of(2026, 1, 2)
    private val gapDay = LocalDate.of(2026, 1, 6)
    private val loggedNonZeroDay = LocalDate.of(2026, 1, 9)

    private val input = TrendChartInput(
        points = listOf(
            InsightPoint(date = loggedZeroDay, value = 0.0),
            InsightPoint(date = LocalDate.of(2026, 1, 3), value = 5.0),
            InsightPoint(date = loggedNonZeroDay, value = 7.0),
        ),
        windowStart = windowStart,
        asOf = asOf,
        testTagRoot = root,
    )

    private fun setChart() {
        composeRule.setContent {
            BloomTheme {
                TrendChart(
                    input = input,
                    summary = summary,
                    modifier = Modifier.requiredWidth(320.dp),
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Every domain day, in chronological order — the expected 11 nodes. */
    private fun domainDays(): List<LocalDate> =
        generateSequence(windowStart) { it.plusDays(1) }.takeWhile { !it.isAfter(asOf) }.toList()

    /** The testTag the overlay assigns a given day (gap days carry the `.notLogged` suffix). */
    private fun tagFor(day: LocalDate): String {
        val base = "$pointPrefix${day.toEpochDay()}"
        val isLogged = input.points.any { it.date == day }
        return if (isLogged) base else "$base.notLogged"
    }

    private fun hasState(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun oneFocusableNode_perScrubbableDay_reachableByTestTag() {
        setChart()

        // Exactly one node per domain day (11), all carrying the `<chartTag>.point.` prefix.
        composeRule.onAllNodes(
            SemanticsMatcher("testTag startsWith $pointPrefix") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(pointPrefix) == true
            },
        ).assertCountEquals(domainDays().size)

        // Each day is individually reachable by its exact per-point testTag (no container masking).
        domainDays().forEach { day ->
            composeRule.onNodeWithTag(tagFor(day)).assertExists()
        }
    }

    @Test
    fun focusOrder_isChronological_leftToRightByEpochDay() {
        setChart()

        // The x-position of each day's node increases strictly with the date → TalkBack steps them in
        // chronological order (also pinned by the forced traversalIndex in the overlay).
        val xs = domainDays().map { day ->
            composeRule.onNodeWithTag(tagFor(day)).fetchSemanticsNode().positionInRoot.x
        }
        xs.zipWithNext().forEach { (earlier, later) ->
            assertTrue(
                "Per-point semantics nodes must be laid out in chronological (left→right) order; " +
                    "found x=$earlier not < x=$later.",
                earlier < later,
            )
        }
    }

    @Test
    fun gapDay_announcesNotLogged_neverAZero() {
        setChart()

        // The gap node reads "Not logged" (its stateDescription) + the date label — never a fabricated 0.
        composeRule.onNodeWithTag(tagFor(gapDay))
            .assertExists()
            .assert(hasContentDescription(gapDay.format(dateLabel)))
            .assert(hasState(ChartsCopy.NOT_LOGGED))

        // A gap is NOT a real point: the non-`.notLogged` variant must NOT exist for the gap day.
        composeRule.onNodeWithTag("$pointPrefix${gapDay.toEpochDay()}").assertDoesNotExist()
    }

    @Test
    fun loggedSeverityZeroDay_announcesARealZero_notAGap() {
        setChart()

        // The logged severity-0 day reads a REAL "Severity 0 of 10" (absence ≠ zero) — the SAME resolver
        // that drives the visual scrub drives this node, so the gap-vs-0 answer is identical.
        composeRule.onNodeWithTag(tagFor(loggedZeroDay))
            .assertExists()
            .assert(hasContentDescription(loggedZeroDay.format(dateLabel)))
            .assert(hasState(ChartsCopy.severityReadout(0.0)))

        // And it is NOT a gap — the `.notLogged` variant is absent for the logged-0 day.
        composeRule.onNodeWithTag("$pointPrefix${loggedZeroDay.toEpochDay()}.notLogged").assertDoesNotExist()
    }

    @Test
    fun chartCarriesAWordedSummaryNode() {
        setChart()

        // The chart is never the sole carrier of meaning: a worded summary node is present.
        composeRule.onNodeWithTag("$chartTag.summary")
            .assertExists()
            .assert(hasText(summary))
    }

    @Test
    fun everyReaderNode_isAtLeast48dpFocusTarget() {
        setChart()

        // The ≥48dp Android touch/focus floor, asserted on a logged, a gap, and the severity-0 node.
        listOf(loggedNonZeroDay, gapDay, loggedZeroDay).forEach { day ->
            composeRule.onNodeWithTag(tagFor(day))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }
}
