package com.endoapp.app.ui.charts

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.endoapp.app.ExploreScatterRouter
import com.endoapp.app.PendingScatter
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.ui.explore.ExploreScreen
import com.endoapp.app.ui.explore.ExploreSelection
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.ExploreSeriesCatalog
import com.endoapp.insights.ScatterPairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Pins the descriptive-only, count-encoded scatter (Plan 06, AINS-05) on the JVM Robolectric+Compose lane:
 *
 *  • COUNT-ENCODING: a 3-day cell draws a bigger mark than a 1-day cell ([scatterMarkRadiusDp] monotonic),
 *    and the per-cell a11y node carries "3 days" — one node per both-logged (x, y) cell.
 *  • DESCRIPTIVE-ONLY (H5): a comment-stripped source scan of `ScatterPlot.kt` finds NO fitted-line /
 *    regression / r-value / slope / percent construct.
 *  • The caption/summary render the audited both-logged strings.
 *  • SUPPRESSION: a suppressed user is offered NO bleeding axis (pure filter + the accessor gate).
 *  • Honest states: scatter empty (<2 axes) and sparse (<5 overlapping days).
 *  • DEEP-LINK: a `pendingScatter` payload switches ExploreScreen to Scatter mode with the typed axes
 *    prefilled, then clears the payload (re-trigger safe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScatterPlotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun series(id: String) = ExploreSeriesCatalog.all.first { it.id == id }

    private val stress = series("stress")
    private val sleep = series("sleep.hours")

    /** Days logging BOTH stress and sleep: 3 at (6, 8h), 2 at (4, 7h) → both-logged tally = 5. */
    private fun overlappingSnaps(): List<DaySnapshot> {
        val at68 = (0..2).map { DaySnapshot.of(day = TODAY.minusDays(it.toLong()), stress = 6, sleepHours = 8.0) }
        val at47 = (3..4).map { DaySnapshot.of(day = TODAY.minusDays(it.toLong()), stress = 4, sleepHours = 7.0) }
        return at68 + at47
    }

    // --- COUNT-ENCODING: size by count + one a11y node per cell (pure) ---

    @Test
    fun countEncoding_biggerMarkAndCellDays_forADenserCell() {
        // Radius grows with the count (AREA ∝ count) — a 3-day cell is a bigger mark than a 1-day cell.
        assertTrue(scatterMarkRadiusDp(3) > scatterMarkRadiusDp(1))
        assertTrue(scatterMarkRadiusDp(5) > scatterMarkRadiusDp(3))

        val points = ScatterPairing.pairs(overlappingSnaps(), stress, sleep)
        // Both-logged cells only, count-aggregated: (4,7) count 2, (6,8) count 3.
        val nodes = scatterSemanticNodes(points, stress, sleep)
        // ONE node per both-logged cell.
        assertEquals(points.size, nodes.size)
        val denseCell = nodes.first { it.testTag == "explore.scatter.point.6x8" }
        assertEquals("3 days", denseCell.stateDescription)
        assertTrue(denseCell.contentDescription.contains("Stress 6 of 10"))
        assertTrue(denseCell.contentDescription.contains("Sleep 8 h"))
    }

    // --- DESCRIPTIVE-ONLY (H5): no fitted line / r-value in the source ---

    @Test
    fun scatterSource_hasNoFittedLineOrRValueConstruct() {
        val src = stripComments(readScatterPlotSource()).lowercase()
        val forbidden = listOf(
            "fitted", "bestfit", "best-fit", "regression", "rvalue", "r-value",
            "slope", "coefficient", "trendline", "linearfit", "%",
        )
        for (token in forbidden) {
            assertFalse(
                "ScatterPlot.kt must contain NO descriptive-breaking construct — found \"$token\" (H5).",
                src.contains(token),
            )
        }
    }

    // --- Caption + summary render the audited both-logged strings ---

    @Test
    fun deepLinkPrefill_rendersPlotWithBothLoggedCaptionAndSummary() {
        val router = ExploreScatterRouter().apply { pendingScatter = PendingScatter(stress, sleep) }
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { overlappingSnaps() },
                    loadSuppressed = { false },
                    today = TODAY,
                    scatterRouter = router,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.scatter.plot").assertExists()
        composeRule.onNodeWithTag("explore.scatter.caption").assertTextContains(
            "Based on 5 days when both were logged.",
        )
        composeRule.onNodeWithTag("explore.scatter.summary").assertTextContains(
            "Stress compared with Sleep, 5 days when both were logged.",
        )
        // A per-cell reader node exists for the dense (6, 8) cell.
        composeRule.onNodeWithTag("explore.scatter.point.6x8", useUnmergedTree = true).assertExists()
    }

    // --- DEEP-LINK: switches to Scatter mode, prefills the typed axes, then clears the payload ---

    @Test
    fun pendingScatter_switchesToScatterMode_prefillsAxes_thenClears() {
        val router = ExploreScatterRouter().apply { pendingScatter = PendingScatter(stress, sleep) }
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { overlappingSnaps() },
                    loadSuppressed = { false },
                    today = TODAY,
                    scatterRouter = router,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.mode.scatter").assertIsSelected()
        composeRule.onNodeWithTag("explore.scatter.axisX").assertTextContains("Stress")
        composeRule.onNodeWithTag("explore.scatter.axisY").assertTextContains("Sleep")
        // Consume-and-clear: the payload is reset so an identical later tap re-triggers.
        assertNull(router.pendingScatter)
    }

    // --- Honest states: empty (<2 axes) + sparse (<5 overlapping days) ---

    @Test
    fun scatterEmpty_showsWhenFewerThanTwoAxesChosen() {
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { emptyList() },
                    loadSuppressed = { false },
                    today = TODAY,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore.mode.scatter").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.scatter.empty").assertExists()
    }

    @Test
    fun scatterSparse_showsWhenTooFewOverlappingDays() {
        // Both axes prefilled, but only 3 both-logged days (< the overlap floor of 5) → sparse.
        val sparseSnaps = (0..2).map { DaySnapshot.of(day = TODAY.minusDays(it.toLong()), stress = 6, sleepHours = 8.0) }
        val router = ExploreScatterRouter().apply { pendingScatter = PendingScatter(stress, sleep) }
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { sparseSnaps },
                    loadSuppressed = { false },
                    today = TODAY,
                    scatterRouter = router,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.scatter.sparse").assertExists()
    }

    // --- SUPPRESSION: a suppressed user is offered no bleeding axis ---

    @Test
    fun suppressedUser_isOfferedNoBleedingAxis() {
        // Pure: the quantitative-only offered axes under suppression carry no bleeding intensity.
        val quantOffered = ExploreSelection.offered(suppressed = true).filter { it.scaleKind.isQuantitative }
        assertFalse(quantOffered.any { it.id == "bleeding.intensity" })
        // The unsuppressed offered axes DO include it (the filter is the only difference).
        val unsuppressed = ExploreSelection.offered(suppressed = false).filter { it.scaleKind.isQuantitative }
        assertTrue(unsuppressed.any { it.id == "bleeding.intensity" })

        // Render: a suppressed user in Scatter mode opens the X-axis menu — no bleeding item is offered.
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { emptyList() },
                    loadSuppressed = { true },
                    today = TODAY,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore.mode.scatter").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore.scatter.axisX").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.scatter.axisX.bleeding.intensity", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // --- Source helpers ---

    private fun readScatterPlotSource(): String {
        val rel = "src/main/kotlin/com/endoapp/app/ui/charts/ScatterPlot.kt"
        val candidate = listOf(
            File(rel),
            File("android/app/$rel"),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "app/$rel"),
        ).firstOrNull { it.isFile }
            ?: error("Could not locate ScatterPlot.kt (cwd=${System.getProperty("user.dir")})")
        return candidate.readText()
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 1, 31)
    }
}
