package com.endoapp.app.ui.explore

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ExploreSeriesCatalog
import com.endoapp.insights.ScaleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pins the Explore Lanes-mode shell (Plan 05) on the JVM Robolectric+Compose lane:
 *   • `ExploreSelection.offered(suppressed)` excludes BOTH bleeding series for a suppressed user (pure).
 *   • Default mode is Lanes; nothing selected → the honest `explore.empty` state.
 *   • A suppressed user is offered NO bleeding chip (defense-in-depth over the accessor gate), while a
 *     non-bleeding chip (clots) remains.
 *   • At the 4-quantitative cap, an UNSELECTED quantitative chip disables + the cap hint shows, while an
 *     event chip stays enabled (events are uncapped).
 *   • ONE shared scrub: a single tap over the lane stack reads EVERY visible lane's value into the ONE
 *     multi-series tooltip (both a sleep lane and a stress lane resolve on the same scrubbed day).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExploreLanesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun series(id: String): ExploreSeries = ExploreSeriesCatalog.all.first { it.id == id }

    // --- Pure suppression filter (no render) ---

    @Test
    fun offered_underSuppression_excludesBothBleedingSeries() {
        val suppressed = ExploreSelection.offered(suppressed = true)
        assertFalse(suppressed.any { it.id == "bleeding.intensity" })
        assertFalse(suppressed.any { it.id == "bleeding.event" })
        // A non-bleeding series is still offered.
        assertTrue(suppressed.any { it.id == "clots" })

        val unsuppressed = ExploreSelection.offered(suppressed = false)
        assertTrue(unsuppressed.any { it.id == "bleeding.intensity" })
        assertTrue(unsuppressed.any { it.id == "bleeding.event" })
    }

    // --- Pure cap arithmetic (no render) ---

    @Test
    fun capArithmetic_isQuantitativeOnly() {
        val fourQuant = setOf(
            series("stress"), series("sleep.hours"), series("activity.minutes"), series("mood"),
        )
        assertTrue(quantitativeSelectionAtCap(fourQuant))
        // An unselected quantitative chip is disabled at the cap; an event chip is not; a selected one isn't.
        val anotherQuant = series(firstSymptomSeverityId())
        assertFalse(chipEnabled(anotherQuant, fourQuant))
        assertTrue(chipEnabled(series("clots"), fourQuant))
        assertTrue(chipEnabled(series("stress"), fourQuant))
        // Three quant is below the cap.
        assertFalse(quantitativeSelectionAtCap(fourQuant - series("mood")))
    }

    // --- Render: default mode + empty state ---

    @Test
    fun defaultModeIsLanes_andEmptyStateShows() {
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

        composeRule.onNodeWithTag("explore.mode.lanes").assertIsSelected()
        composeRule.onNodeWithTag("explore.empty").assertExists()
    }

    // --- Render: suppression hides bleeding chips ---

    @Test
    fun suppressedUser_isOfferedNoBleedingChip() {
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

        composeRule.onNodeWithTag("explore.chip.bleeding.intensity").assertDoesNotExist()
        composeRule.onNodeWithTag("explore.chip.bleeding.event").assertDoesNotExist()
        // A non-bleeding chip remains.
        composeRule.onNodeWithTag("explore.chip.clots").assertExists()
    }

    // --- Render: the quantitative cap disables unselected quant chips; event chips stay enabled ---

    @Test
    fun atQuantitativeCap_unselectedQuantChipsDisable_eventChipsStayEnabled() {
        val quantFour = listOf("stress", "sleep.hours", "activity.minutes", "mood").map(::series)
        val fifthQuantId = firstSymptomSeverityId()
        val offered = quantFour + series(fifthQuantId) + series("clots")

        composeRule.setContent {
            BloomTheme {
                SeriesPicker(
                    offered = offered,
                    selected = quantFour.toSet(),
                    onToggle = {},
                    range = DEFAULT_EXPLORE_RANGE,
                    onRange = {},
                    modifier = Modifier.requiredWidth(360.dp),
                )
            }
        }
        composeRule.waitForIdle()

        // The 5th (unselected) quantitative chip is disabled; the event chip is not; the hint shows.
        composeRule.onNodeWithTag("explore.chip.$fifthQuantId").assertIsNotEnabled()
        composeRule.onNodeWithTag("explore.chip.clots").assertIsEnabled()
        composeRule.onNodeWithTag("explore.picker.capHint").assertExists()
    }

    // --- Render: ONE shared scrub reads every visible lane ---

    @Test
    fun oneSharedScrub_readsEveryVisibleLane() {
        val sleep = series("sleep.hours")
        val stress = series("stress")
        val loggedDays = listOf(TODAY.minusDays(21), TODAY.minusDays(20), TODAY.minusDays(19))
        val snaps = loggedDays.map { day ->
            DaySnapshot.of(day = day, sleepHours = 8.0, stress = 6)
        }
        val target = loggedDays[1] // logged in BOTH lanes

        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { snaps },
                    loadSuppressed = { false },
                    today = TODAY,
                    initialSelection = setOf(sleep, stress),
                )
            }
        }
        composeRule.waitForIdle()

        // Scrub once over the shared stack, at the target day's x-fraction.
        val windowStart = TODAY.minusDays(DEFAULT_EXPLORE_RANGE.days)
        val totalDays = ChronoUnit.DAYS.between(windowStart, TODAY).toFloat()
        val fraction = ChronoUnit.DAYS.between(windowStart, target).toFloat() / totalDays

        composeRule.onNodeWithTag("explore.lanes.scrub")
            .performScrollTo()
            .performTouchInput { click(Offset(fraction * width, height / 2f)) }
        composeRule.waitForIdle()

        // The ONE tooltip carries a line for BOTH lanes on the SAME scrubbed day (real-unit readouts).
        composeRule.onNodeWithTag("explore.scrub.tooltip").assertExists()
        composeRule.onNodeWithTag("explore.scrub.tooltip.sleep.hours").assertExists()
        composeRule.onNodeWithTag("explore.scrub.tooltip.stress").assertExists()
    }

    private fun firstSymptomSeverityId(): String =
        ExploreSeriesCatalog.all.first {
            it.scaleKind == ScaleKind.ORDINAL_0_TO_10 && it.id.startsWith("symptom.")
        }.id

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 1, 31)
    }
}
