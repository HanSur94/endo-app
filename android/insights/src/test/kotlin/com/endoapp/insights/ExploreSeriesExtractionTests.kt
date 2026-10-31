// EndoInsights (:insights) — unit-pinned honesty invariants for series extraction.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift`
// (10 cases). These fixtures lock the two structural honesty moves:
//   • absence ≠ 0: an unlogged quantitative field emits NO point (a gap), while a logged literal 0
//     emits a REAL point at value 0.
//   • presence is unrepresentable as 0/1: presence/event series extract as occurrence `List<LocalDate>`
//     — a return type with NO `value` field, so coercing a tag to 0 or 1 is impossible to even write.
//   • no suppression leak: a suppressed snapshot's bleeding produces zero intensity points AND zero
//     event occurrences; the gate only suppresses, never universally drops (the un-suppressed mirror).
//
// Uses the `SeriesFixtures` harness (LocalDate identity; the injected Swift `Calendar` is DROPPED, so
// `startOfDay` normalization is a no-op). Series under test are resolved from the real catalog.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreSeriesExtractionTests {

    // MARK: catalog lookups (resolve the real series under test by id)

    private val stressSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "stress" }
    private val pelvicPainSymptomSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "symptom.pelvicPain.severity" }
    private val dairySeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "food.dairy" }
    private val moodSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "mood" }
    private val bleedingIntensitySeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "bleeding.intensity" }
    private val bleedingEventSeries: ExploreSeries
        get() = ExploreSeriesCatalog.all.first { it.id == "bleeding.event" }

    // MARK: absence ≠ 0 — quantitative path

    /** An unlogged quantitative field (stress null, even though the day logged something else) emits NO
     *  point — null → gap, NEVER a fabricated 0. */
    @Test
    fun unloggedQuantitativeDay_emitsNoPoint() {
        val snap = SeriesFixtures.daySnapshot(
            day = SeriesFixtures.day(2026, 6, 1),
            symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5)),
            stress = null,
        )
        assertTrue(ExploreSeriesExtraction.quantitativePoints(listOf(snap), stressSeries).isEmpty())
    }

    /** A logged literal 0 (stress 0) emits a REAL point at value 0 — provably distinct from a gap. */
    @Test
    fun loggedZero_emitsRealPointAtZero() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 2), stress = 0)
        val points = ExploreSeriesExtraction.quantitativePoints(listOf(snap), stressSeries)
        assertEquals(1, points.size)
        assertEquals(0.0, points.first().value)
        assertEquals(snap.day, points.first().date)
    }

    /** A logged symptom severity 0 emits a REAL point at value 0 for that symptom's series. */
    @Test
    fun loggedZeroSymptomSeverity_emitsRealPointAtZero() {
        val snap = SeriesFixtures.daySnapshot(
            day = SeriesFixtures.day(2026, 6, 3),
            symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 0)),
        )
        val points = ExploreSeriesExtraction.quantitativePoints(listOf(snap), pelvicPainSymptomSeries)
        assertEquals(1, points.size)
        assertEquals(0.0, points.first().value)
        assertEquals(snap.day, points.first().date)
    }

    /** Defensive against a duplicate-type day: the accessor takes the MAX logged severity, never
     *  positional-first, so it can never silently drop the higher logged value. Two pelvicPain entries
     *  (severity 2 and 8) on one day → the extracted point is the max (8). */
    @Test
    fun duplicateTypeDay_keepsMaxSeverity_neverDropsHigher() {
        val snap = SeriesFixtures.daySnapshot(
            day = SeriesFixtures.day(2026, 6, 9),
            symptoms = listOf(
                SymptomPoint(SymptomType.PELVIC_PAIN, 2),
                SymptomPoint(SymptomType.PELVIC_PAIN, 8),
            ),
        )
        val points = ExploreSeriesExtraction.quantitativePoints(listOf(snap), pelvicPainSymptomSeries)
        assertEquals(1, points.size)
        assertEquals(8.0, points.first().value)
    }

    // MARK: presence path returns [LocalDate], unrepresentable as 0/1

    /** A present food tag emits that day's occurrence LocalDate. Type-level note: occurrenceDays returns
     *  `List<LocalDate>` — there is NO `value` field to assert as 0 or 1, so the presence-to-0/1
     *  coercion is unrepresentable in the type system, not merely unchecked here. */
    @Test
    fun presentFoodTag_emitsOccurrenceDate_andReturnsDateArray() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 4), foodTags = listOf(FoodTag.DAIRY))
        val occurrences = ExploreSeriesExtraction.occurrenceDays(listOf(snap), dairySeries)
        assertEquals(listOf(snap.day), occurrences)
    }

    /** A logged day WITHOUT the presence thing (food tags empty, though something else is logged) emits
     *  NO occurrence — presence-absent ≠ logged-absent. */
    @Test
    fun loggedDayWithoutTag_emitsNoOccurrence() {
        val snap = SeriesFixtures.daySnapshot(
            day = SeriesFixtures.day(2026, 6, 5),
            foodTags = emptyList(),
            stress = 3, // logged something else, but NOT dairy
        )
        assertTrue(ExploreSeriesExtraction.occurrenceDays(listOf(snap), dairySeries).isEmpty())
    }

    // MARK: wrong-door routing degrades gracefully, never crashes

    /** A presence series handed to `quantitativePoints` (the wrong door) DEGRADES to an empty result —
     *  never a release crash — EVEN when the underlying presence thing is logged. */
    @Test
    fun presenceSeriesIntoQuantitativePoints_degradesToEmpty_notCrash() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 7), foodTags = listOf(FoodTag.DAIRY))
        assertTrue(ExploreSeriesExtraction.quantitativePoints(listOf(snap), dairySeries).isEmpty())
    }

    /** A quantitative series handed to `occurrenceDays` (the wrong door) DEGRADES to an empty result —
     *  never a release crash — EVEN when the underlying value is logged. */
    @Test
    fun quantitativeSeriesIntoOccurrenceDays_degradesToEmpty_notCrash() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 8), mood = 3)
        assertTrue(ExploreSeriesExtraction.occurrenceDays(listOf(snap), moodSeries).isEmpty())
    }

    // MARK: suppression — no leak, and the gate only suppresses (mirror)

    /** A suppressed snapshot's bleeding produces ZERO intensity points AND ZERO event occurrences — the
     *  suppression gate inside the accessors closes BOTH bleeding-derived paths (no leak). */
    @Test
    fun suppressedBleeding_emitsNoPointsAndNoOccurrences() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 6), flow = FlowIntensity.HEAVY, suppressed = true)
        assertTrue(ExploreSeriesExtraction.quantitativePoints(listOf(snap), bleedingIntensitySeries).isEmpty())
        assertTrue(ExploreSeriesExtraction.occurrenceDays(listOf(snap), bleedingEventSeries).isEmpty())
    }

    /** The same fixture un-suppressed: one intensity point at the honest ordinal rank of `.HEAVY` (4)
     *  AND one event occurrence. Proves the gate only SUPPRESSES — it never universally drops. */
    @Test
    fun unsuppressedBleeding_emitsIntensityPointAndEventOccurrence() {
        val snap = SeriesFixtures.daySnapshot(day = SeriesFixtures.day(2026, 6, 6), flow = FlowIntensity.HEAVY, suppressed = false)
        val points = ExploreSeriesExtraction.quantitativePoints(listOf(snap), bleedingIntensitySeries)
        assertEquals(1, points.size)
        assertEquals(4.0, points.first().value) // spotting=1, light=2, medium=3, heavy=4
        val occurrences = ExploreSeriesExtraction.occurrenceDays(listOf(snap), bleedingEventSeries)
        assertEquals(listOf(snap.day), occurrences)
    }
}
