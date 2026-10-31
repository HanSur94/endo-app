// EndoInsights (:insights) — unit-pinned honesty invariants for scatter pairing.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift` (7 cases).
// These fixtures lock the scatter honesty invariants:
//   • both-logged-only: a scatter point is a day where BOTH factors were logged; a day missing EITHER
//     factor produces NO pair (never imputed/0-substituted).
//   • count-encoded, never jittered: identical (x,y) day-pairs aggregate into ONE ScatterPoint whose
//     `count` is a FACTUAL tally — never jitter fabricating non-integer values the user never logged.
//   • logged-0 is real: a day logging a literal 0 on both axes is a REAL pair, never a gap.
//   • suppression carries through: a suppressed bleeding axis contributes no pair.
//   • determinism: distinct pairs produce distinct points, sorted by (x, y) for stable fixtures.
//
// Uses the `SeriesFixtures` harness (LocalDate identity; the injected Swift `Calendar` is DROPPED).
// Series under test are resolved from the real catalog.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScatterPairingTests {

    // MARK: catalog lookups (resolve the real series under test by id)

    private val stressSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "stress" }
    private val moodSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "mood" }
    private val pelvicPainSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "symptom.pelvicPain.severity" }
    private val bleedingIntensitySeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "bleeding.intensity" }
    private val dairySeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "food.dairy" }

    // MARK: wrong-axis routing degrades gracefully, never crashes

    /** A presence series passed as a scatter axis (the wrong door) DEGRADES to an empty result — never
     *  a release crash — EVEN when both factors are logged. Covers both the x-axis and y-axis positions. */
    @Test
    fun presenceAxis_degradesToEmpty_notCrash() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 1), foodTags = listOf(FoodTag.DAIRY), stress = 5)
        assertTrue(ScatterPairing.pairs(listOf(snap), x = dairySeries, y = stressSeries).isEmpty())
        assertTrue(ScatterPairing.pairs(listOf(snap), x = stressSeries, y = dairySeries).isEmpty())
    }

    // MARK: count-aggregation, no jitter

    /** 10 distinct days all at (stress 7, mood 2) aggregate into EXACTLY one ScatterPoint with
     *  count == 10 — never 10 jittered points fabricating values the user never logged. */
    @Test
    fun identicalPairs_aggregateIntoOnePointWithCount() {
        val snaps = (1..10).map { d ->
            SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, d), stress = 7, mood = 2)
        }
        val points = ScatterPairing.pairs(snaps, x = stressSeries, y = moodSeries)
        assertEquals(listOf(ScatterPairing.ScatterPoint(x = 7.0, y = 2.0, count = 10)), points)
    }

    // MARK: both-logged-only, never imputed

    /** A day with stress logged but mood null produces NO pair — either factor null excludes the day,
     *  never imputed/0-substituted. */
    @Test
    fun dayWithOneFactorMissing_producesNoPair() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 1), stress = 7, mood = null)
        assertTrue(ScatterPairing.pairs(listOf(snap), x = stressSeries, y = moodSeries).isEmpty())
    }

    /** 3 both-logged days at (5,3), 2 days with mood null, 1 day with stress null → the single
     *  ScatterPoint has count == 3 (the missing-factor days are excluded). */
    @Test
    fun mixedDays_onlyBothLoggedDaysCounted() {
        val snaps = mutableListOf<DaySnapshot>()
        for (d in 1..3) snaps.add(SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, d), stress = 5, mood = 3))
        for (d in 4..5) snaps.add(SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, d), stress = 5, mood = null))
        snaps.add(SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 6), stress = null, mood = 3))
        val points = ScatterPairing.pairs(snaps, x = stressSeries, y = moodSeries)
        assertEquals(listOf(ScatterPairing.ScatterPoint(x = 5.0, y = 3.0, count = 3)), points)
    }

    // MARK: determinism — distinct pairs → distinct sorted points

    /** Days at (1,1), (3,2), (2,5) → exactly those three points sorted by (x,y): (1,1),(2,5),(3,2). */
    @Test
    fun distinctPairs_produceDistinctSortedPoints() {
        val snaps = listOf(
            SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 1), stress = 1, mood = 1),
            SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 2), stress = 3, mood = 2),
            SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 3), stress = 2, mood = 5),
        )
        val points = ScatterPairing.pairs(snaps, x = stressSeries, y = moodSeries)
        assertEquals(
            listOf(
                ScatterPairing.ScatterPoint(x = 1.0, y = 1.0, count = 1),
                ScatterPairing.ScatterPoint(x = 2.0, y = 5.0, count = 1),
                ScatterPairing.ScatterPoint(x = 3.0, y = 2.0, count = 1),
            ),
            points,
        )
    }

    // MARK: logged-0 is a real pair, not excluded

    /** A day with stress 0 and pelvic-pain severity 0 — both literal logged 0s on 0…10 axes — is a
     *  REAL pair at (0, 0) with count 1, never excluded as a gap (logged 0 ≠ absence). */
    @Test
    fun loggedZeroBothFactors_isARealPair_notExcluded() {
        val snap = SeriesFixtures.daySnapshot(
            day = SeriesFixtures.day(2026, 6, 1),
            symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 0)),
            stress = 0,
        )
        val points = ScatterPairing.pairs(listOf(snap), x = stressSeries, y = pelvicPainSeries)
        assertEquals(listOf(ScatterPairing.ScatterPoint(x = 0.0, y = 0.0, count = 1)), points)
    }

    // MARK: suppression carries through

    /** A suppressed bleeding-intensity axis contributes no pair — the suppression gate inside
     *  `quantitativeValue` carries through, so even with stress logged the day yields nothing. */
    @Test
    fun suppressedBleedingAxis_contributesNoPair() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 1), flow = FlowIntensity.MEDIUM, stress = 5, suppressed = true)
        val points = ScatterPairing.pairs(listOf(snap), x = bleedingIntensitySeries, y = stressSeries)
        assertTrue(points.isEmpty())
    }
}
