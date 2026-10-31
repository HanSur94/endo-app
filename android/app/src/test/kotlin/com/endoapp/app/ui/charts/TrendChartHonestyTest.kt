package com.endoapp.app.ui.charts

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.endoapp.app.content.ChartsCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.InsightPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Pins the RENDER-layer chart-honesty invariants (H1/H2) of [TrendChart] on the JVM Robolectric+Compose
 * lane — the visual scrub reads the EXACT day through the SAME `ChartScrubSelection.resolve` choke-point
 * the data-prep layer uses. Proves:
 *   • A gap day scrubs to "Not logged" (via the `…tooltip.notLogged` node) — never a fabricated 0 (H1).
 *   • A logged severity-0 day scrubs to a REAL "Severity 0 of 10" AND carries NO gap marker (H2 —
 *     absence ≠ zero, answered in ONE tested place).
 *   • The chart carries the one-line text summary node (never the sole carrier of meaning).
 *   • A comment-stripped source scan finds NO nearest-point / interpolation / `value(atX` path — the
 *     resolver is the ONLY raw-day → value route — and the input holder is `@Immutable`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrendChartHonestyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val root = "insights.card.symptom.pelvicPain.severity"

    // A 10-day window (Jan 1 … Jan 11). Logged: Jan 2 (severity 0 — REAL), Jan 3 (5), Jan 9 (7).
    // Jan 6 is UNLOGGED → an honest gap between the [Jan2,Jan3] run and the [Jan9] run.
    private val input = TrendChartInput(
        points = listOf(
            InsightPoint(date = LocalDate.of(2026, 1, 2), value = 0.0),
            InsightPoint(date = LocalDate.of(2026, 1, 3), value = 5.0),
            InsightPoint(date = LocalDate.of(2026, 1, 9), value = 7.0),
        ),
        windowStart = LocalDate.of(2026, 1, 1),
        asOf = LocalDate.of(2026, 1, 11),
        testTagRoot = root,
    )

    private fun setChart(onDayChanged: (LocalDate?) -> Unit = {}) {
        composeRule.setContent {
            BloomTheme {
                TrendChart(
                    input = input,
                    summary = "Pelvic pain severity, last 30 days.",
                    modifier = Modifier.requiredWidth(320.dp),
                    onDayChanged = onDayChanged,
                )
            }
        }
    }

    /** Tap the chart at `fraction` of its width (day = round(fraction × 10 days) from Jan 1). */
    private fun scrubAtFraction(fraction: Float) {
        composeRule.onNodeWithTag("$root.chart").performTouchInput {
            click(Offset(width * fraction, height * 0.5f))
        }
        composeRule.waitForIdle()
    }

    @Test
    fun textSummaryNode_isPresent_soChartIsNeverTheSoleCarrier() {
        setChart()
        composeRule.onNodeWithTag("$root.chart.summary").assertIsDisplayed()
        composeRule.onNodeWithText("Pelvic pain severity, last 30 days.").assertIsDisplayed()
    }

    @Test
    fun scrubGapDay_readsNotLogged_neverAZero() {
        setChart()
        // Fraction 0.5 → round(5.0) = 5 days after Jan 1 → Jan 6 (UNLOGGED gap).
        scrubAtFraction(0.5f)

        composeRule.onNodeWithTag("$root.chart.tooltip").assertExists()
        composeRule.onNodeWithTag("$root.chart.tooltip.notLogged").assertExists()
        composeRule.onNodeWithText(ChartsCopy.NOT_LOGGED).assertExists()
        // A gap is NOT a zero: the "Severity 0 of 10" readout must NOT appear on a gap day.
        composeRule.onNodeWithText(ChartsCopy.severityReadout(0.0)).assertDoesNotExist()
    }

    @Test
    fun scrubLoggedSeverityZeroDay_readsRealZero_andCarriesNoGapMarker() {
        setChart()
        // Fraction 0.1 → round(1.0) = 1 day after Jan 1 → Jan 2 (LOGGED severity 0).
        scrubAtFraction(0.1f)

        composeRule.onNodeWithTag("$root.chart.tooltip").assertExists()
        // The literal logged 0 reads a REAL "Severity 0 of 10" (absence ≠ 0).
        composeRule.onNodeWithText(ChartsCopy.severityReadout(0.0)).assertExists()
        // And it is NOT a gap — the gap marker is absent, proving absence ≠ zero end-to-end.
        composeRule.onNodeWithTag("$root.chart.tooltip.notLogged").assertDoesNotExist()
    }

    @Test
    fun scrubLoggedNonZeroDay_readsItsRealValue() {
        setChart()
        // Fraction 0.2 → round(2.0) = 2 → Jan 3 (LOGGED severity 5).
        scrubAtFraction(0.2f)
        composeRule.onNodeWithText(ChartsCopy.severityReadout(5.0)).assertExists()
    }

    @Test
    fun source_usesOnlyTheResolver_noNearestPointOrInterpolation_andInputIsImmutable() {
        val codeOnly = resolveSource()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { it.substringBefore("//") }

        for (forbidden in listOf("value(atX", "minBy", "minByOrNull", "timeIntervalSince", "interpolat")) {
            assertFalse(
                "TrendChart must route raw-day → value ONLY through ChartScrubSelection.resolve — found " +
                    "a forbidden \"$forbidden\" call (nearest-point / interpolation fabricates data).",
                codeOnly.contains(forbidden),
            )
        }
        assertTrue(
            "TrendChart must resolve the scrubbed value through ChartScrubSelection.resolve.",
            codeOnly.contains("ChartScrubSelection.resolve"),
        )
        assertTrue(
            "TrendChartInput must be @Immutable so the composable is skippable (no recomposition storm).",
            codeOnly.contains("@Immutable"),
        )
    }

    /** Walk up from the test working directory to the `TrendChart.kt` source and read it. */
    private fun resolveSource(): String {
        val rel = "app/src/main/kotlin/com/endoapp/app/ui/charts/TrendChart.kt"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate.readText()
            val local = File(dir, "src/main/kotlin/com/endoapp/app/ui/charts/TrendChart.kt")
            if (local.isFile) return local.readText()
            dir = dir.parentFile
        }
        error("Could not resolve TrendChart.kt source from ${System.getProperty("user.dir")}")
    }
}
