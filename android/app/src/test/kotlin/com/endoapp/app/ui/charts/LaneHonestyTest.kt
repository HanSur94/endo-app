package com.endoapp.app.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.ui.explore.ExploreScreen
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ExploreSeriesCatalog
import com.endoapp.insights.SeriesNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pins the RENDER-layer Explore lane-honesty invariants (Plan 05) on the JVM Robolectric+Compose lane —
 * the payoff assertions that "no dual y-axis / no silent normalization" hold at the render layer over the
 * already-tested `:insights` data-prep seam:
 *   • Two lanes of DIFFERENT scales draw on their OWN fixed `SeriesNormalization.domain` — a sleep lane
 *     against 0…24, a stress lane against 0…10, INDEPENDENTLY (no shared/dual y-axis). Proven by the pure
 *     per-lane domain AND by real-unit a11y readouts ("8 h" vs "8 of 10" — a normalized axis could not
 *     produce two different real-unit readouts).
 *   • A GAP day is a line break (H1 — a `.notLogged` node); a logged severity-0 is a REAL point (H2 — a
 *     real "0 of 10" node), in a quantitative lane.
 *   • An EVENT lane renders occurrence ticks only — a summary node "{name} — logged on {n} days" (no value
 *     axis), a per-tick node for a logged-present day, and NO node for a non-occurrence day.
 *   • The ONE multi-series tooltip shows one line per visible series, with "Not logged" for a gap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaneHonestyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun series(id: String): ExploreSeries = ExploreSeriesCatalog.all.first { it.id == id }

    private val today = LocalDate.of(2026, 1, 31)
    private val windowStart = today.minusDays(90)

    // dayA: sleep 8h + stress 8 + clots present. dayB: sleep 8h, stress GAP, clots absent.
    // dayC: sleep GAP, stress logged REAL 0.
    private val dayA = today.minusDays(21)
    private val dayB = today.minusDays(20)
    private val dayC = today.minusDays(19)

    private val sleep = series("sleep.hours")
    private val stress = series("stress")
    private val clots = series("clots")

    private val snaps = listOf(
        DaySnapshot.of(day = dayA, sleepHours = 8.0, stress = 8, clots = true),
        DaySnapshot.of(day = dayB, sleepHours = 8.0, clots = false),
        DaySnapshot.of(day = dayC, stress = 0),
    )

    private fun setScreen() {
        composeRule.setContent {
            BloomTheme {
                ExploreScreen(
                    loadSnapshots = { snaps },
                    loadSuppressed = { false },
                    today = today,
                    initialSelection = setOf(sleep, stress, clots),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun hasState(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun eachLaneDrawsOnItsOwnFixedDomain_noSharedOrNormalizedAxis() {
        // Pure: the per-lane fixed domains are DIFFERENT (0…24 vs 0…10) — no shared axis by construction.
        assertEquals(0.0..24.0, quantLaneDomain(sleep))
        assertEquals(0.0..10.0, quantLaneDomain(stress))
        assertNotEquals(quantLaneDomain(sleep), quantLaneDomain(stress))

        setScreen()

        // Render: the same real value 8 reads its OWN real unit in each lane (a normalized/shared axis
        // could not produce two different real-unit readouts) — no silent normalization.
        composeRule.onNodeWithTag("explore.lane.sleep.hours.point.${dayA.toEpochDay()}")
            .assertExists()
            .assert(hasState(SeriesNormalization.readout(8.0, sleep.scaleKind, sleep.unit))) // "8 h"
        composeRule.onNodeWithTag("explore.lane.stress.point.${dayA.toEpochDay()}")
            .assertExists()
            .assert(hasState(SeriesNormalization.readout(8.0, stress.scaleKind, stress.unit))) // "8 of 10"
    }

    @Test
    fun quantLane_gapIsABreak_andLoggedZeroIsARealPoint() {
        setScreen()

        // A gap day in the sleep lane (dayC — not logged) announces "Not logged" (H1 — never a 0).
        composeRule.onNodeWithTag("explore.lane.sleep.hours.point.${dayC.toEpochDay()}.notLogged")
            .assertExists()

        // A logged severity-0 day in the stress lane (dayC) is a REAL "0 of 10" point (H2 — absence ≠ 0),
        // and is NOT a gap.
        composeRule.onNodeWithTag("explore.lane.stress.point.${dayC.toEpochDay()}")
            .assertExists()
            .assert(hasState(SeriesNormalization.readout(0.0, stress.scaleKind, stress.unit))) // "0 of 10"
        composeRule.onNodeWithTag("explore.lane.stress.point.${dayC.toEpochDay()}.notLogged")
            .assertDoesNotExist()
    }

    @Test
    fun eventLane_rendersOccurrenceTicksAndASummary_notAValueAxis() {
        setScreen()

        // The event lane carries a SUMMARY node ("{name} — logged on {n} days") INSTEAD of a value axis.
        composeRule.onNodeWithTag("explore.lane.clots.event.summary")
            .assertExists()
            .assert(hasContentDescription(ExploreCopy.eventLaneSummary(clots.displayName, 1)))

        // A per-tick node exists for the logged-present day (dayA); a non-occurrence day (dayB) has NONE
        // (presence-absent ≠ logged-absent — a 0/1 line is structurally unrepresentable).
        composeRule.onNodeWithTag("explore.lane.clots.event.point.${dayA.toEpochDay()}").assertExists()
        composeRule.onNodeWithTag("explore.lane.clots.event.point.${dayB.toEpochDay()}").assertDoesNotExist()
    }

    @Test
    fun multiSeriesTooltip_showsOneLinePerSeries_withNotLoggedForAGap() {
        setScreen()

        // Scrub dayB: sleep logged, stress a GAP, clots absent.
        val totalDays = ChronoUnit.DAYS.between(windowStart, today).toFloat()
        val fraction = ChronoUnit.DAYS.between(windowStart, dayB).toFloat() / totalDays

        composeRule.onNodeWithTag("explore.lanes.scrub")
            .performScrollTo()
            .performTouchInput { click(Offset(fraction * width, height / 2f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("explore.scrub.tooltip").assertExists()
        // Sleep logged → a real-unit line; stress gap → "Not logged"; clots absent → "Not logged".
        composeRule.onNodeWithTag("explore.scrub.tooltip.sleep.hours").assertExists()
        composeRule.onNodeWithTag("explore.scrub.tooltip.stress.notLogged").assertExists()
        composeRule.onNodeWithTag("explore.scrub.tooltip.clots.notLogged").assertExists()
    }
}
