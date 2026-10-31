package com.endoapp.app.ui.tracking

import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure-JVM honesty proof for the absence ≠ 0 invariant (ATRK-05 / threat T-20-01). Drives
 * [DayDraftMapper] round trips (`toRows` → `fromRows`) with no Room / no emulator, asserting for
 * EVERY UI-SPEC §3 row that:
 *   - an untouched optional field maps to NO row / a null column → reads back nil (never 0), and
 *   - a logged 0 (severity 0, 0.0h with hoursSet, 0 minutes with a type, stress 0 opted-in) reads
 *     back a REAL 0, distinct from nil.
 * Plus the empty-confirm-marker case (only the DayEntry anchor, no fabricated rows) and a
 * fully-populated identity round trip.
 *
 * A structural guard also asserts the mapper never coerces nil→0 (`?: 0` is forbidden on health
 * values). If any future edit reintroduced a coercion, the nil-side assertions below turn RED.
 */
class AbsenceNotZeroTest {

    private val day = 20_100L
    private val idFor: (String) -> String = { it } // identity — the mapper fabricates no ids of its own.

    private fun roundTrip(draft: DayDraft): DayDraft =
        DayDraftMapper.fromRows(DayDraftMapper.toRows(day, draft, idFor))

    // --- Symptom severity (row 1): nil vs real 0 ---

    @Test
    fun symptomSeverity_nilSelected_readsBackNil_notZero() {
        val back = roundTrip(DayDraft(symptoms = mapOf(SymptomType.PELVIC_PAIN to null)))
        assertTrue("symptom must remain selected", back.symptoms.containsKey(SymptomType.PELVIC_PAIN))
        assertNull("selected-but-unspecified severity must read back nil, never 0", back.symptoms[SymptomType.PELVIC_PAIN])
    }

    @Test
    fun symptomSeverity_realZero_readsBackZero() {
        val back = roundTrip(DayDraft(symptoms = mapOf(SymptomType.FATIGUE to 0)))
        assertEquals("a logged severity 0 must read back a real 0", 0, back.symptoms[SymptomType.FATIGUE])
    }

    @Test
    fun symptom_absent_producesNoRow() {
        val rows = DayDraftMapper.toRows(day, DayDraft(), idFor)
        assertTrue("an untouched symptom map produces no SymptomReading", rows.symptoms.isEmpty())
        assertTrue(roundTrip(DayDraft()).symptoms.isEmpty())
    }

    // --- Pain-region severity (row 2): nil vs real 0 ---

    @Test
    fun painSeverity_nilMarked_readsBackNil_notZero() {
        val back = roundTrip(DayDraft(pain = mapOf(BodyRegion.LOWER_BACK to null)))
        assertTrue(back.pain.containsKey(BodyRegion.LOWER_BACK))
        assertNull("marked-but-unspecified pain severity must read back nil", back.pain[BodyRegion.LOWER_BACK])
    }

    @Test
    fun painSeverity_realZero_readsBackZero() {
        val back = roundTrip(DayDraft(pain = mapOf(BodyRegion.HIPS to 0)))
        assertEquals(0, back.pain[BodyRegion.HIPS])
    }

    @Test
    fun pain_absent_producesNoRow() {
        assertTrue(DayDraftMapper.toRows(day, DayDraft(), idFor).pain.isEmpty())
    }

    // --- Bleeding flow (row 3): nil clears; a set flow round-trips ---

    @Test
    fun bleeding_nilFlow_producesNoRow_readsBackNil() {
        val rows = DayDraftMapper.toRows(day, DayDraft(flow = null, clots = null), idFor)
        assertTrue("a nil flow clears the day's bleeding (no row)", rows.bleeding.isEmpty())
        val back = roundTrip(DayDraft())
        assertNull(back.flow)
        assertNull(back.clots)
    }

    @Test
    fun bleeding_setFlowAndClots_roundTrips() {
        val back = roundTrip(DayDraft(flow = FlowIntensity.HEAVY, clots = true))
        assertEquals(FlowIntensity.HEAVY, back.flow)
        assertEquals(true, back.clots)
    }

    // --- Food tags / note (row 4): empty → nil ---

    @Test
    fun food_untouched_producesNoRows_readsBackEmpty() {
        val rows = DayDraftMapper.toRows(day, DayDraft(), idFor)
        assertTrue(rows.factors.none { it.kindRaw == "food" })
        val back = roundTrip(DayDraft())
        assertTrue(back.foodTags.isEmpty())
        assertNull(back.foodNote)
    }

    @Test
    fun food_tagsAndNote_roundTrip() {
        val back = roundTrip(DayDraft(foodTags = setOf(FoodTag.DAIRY, FoodTag.CAFFEINE), foodNote = "extra"))
        assertEquals(setOf(FoodTag.DAIRY, FoodTag.CAFFEINE), back.foodTags)
        assertEquals("extra", back.foodNote)
    }

    // --- Activity type/min/intensity (row 5): nil type vs 0 minutes with a type ---

    @Test
    fun activity_nilType_producesNoRow_minutesAndIntensityNil() {
        val rows = DayDraftMapper.toRows(day, DayDraft(activityType = null, activityMinutes = 30.0), idFor)
        assertTrue("a nil activity type logs nothing", rows.factors.none { it.kindRaw == "activity" })
        val back = roundTrip(DayDraft())
        assertNull(back.activityType)
        assertNull(back.activityMinutes)
        assertNull(back.activityIntensity)
    }

    @Test
    fun activity_zeroMinutesWithType_readsBackRealZero() {
        val back = roundTrip(
            DayDraft(activityType = ActivityType.WALKING, activityMinutes = 0.0, activityIntensity = ActivityIntensity.LOW),
        )
        assertEquals(ActivityType.WALKING, back.activityType)
        assertEquals("0 minutes WITH a type is a real 0", 0.0, back.activityMinutes!!, 0.0)
        assertEquals(ActivityIntensity.LOW, back.activityIntensity)
    }

    // --- Sleep hours / quality (row 6): hoursSet=false → nil; 0.0h with set → real 0 ---

    @Test
    fun sleep_hoursNotSet_readsBackNilHours() {
        val back = roundTrip(DayDraft(sleepHoursSet = false, sleepQuality = SleepQuality.GOOD))
        assertFalse(back.sleepHoursSet)
        assertNull("hours-not-set must read back nil", back.sleepHours)
        assertEquals(SleepQuality.GOOD, back.sleepQuality)
    }

    @Test
    fun sleep_zeroHoursSet_readsBackRealZero() {
        val back = roundTrip(DayDraft(sleepHours = 0.0, sleepHoursSet = true))
        assertTrue(back.sleepHoursSet)
        assertEquals("0.0h WITH hoursSet is a real 0", 0.0, back.sleepHours!!, 0.0)
    }

    @Test
    fun sleep_untouched_producesNoRow() {
        assertTrue(DayDraftMapper.toRows(day, DayDraft(), idFor).factors.none { it.kindRaw == "sleep" })
    }

    // --- Medication (row 7) + PRN (row 8): off = not logged ---

    @Test
    fun medication_untoggled_readsBackEmpty_prnOff() {
        val back = roundTrip(DayDraft())
        assertTrue(back.medsTaken.isEmpty())
        assertFalse(back.tookPRN)
    }

    @Test
    fun medication_takenAndPrn_roundTrip() {
        val back = roundTrip(DayDraft(medsTaken = setOf("med-a", "med-b"), tookPRN = true))
        assertEquals(setOf("med-a", "med-b"), back.medsTaken)
        assertTrue(back.tookPRN)
    }

    // --- Stress (row 9): not opted in → nil; 0 opted in → real 0 ---

    @Test
    fun stress_notOptedIn_producesNoRow_readsBackNil() {
        assertTrue(DayDraftMapper.toRows(day, DayDraft(stress = null), idFor).factors.none { it.kindRaw == "stress" })
        assertNull(roundTrip(DayDraft()).stress)
    }

    @Test
    fun stress_zeroOptedIn_readsBackRealZero() {
        assertEquals("stress 0 after opt-in is a real 0", 0, roundTrip(DayDraft(stress = 0)).stress)
    }

    // --- Mood (row 10): nil → not logged; a value round-trips ---

    @Test
    fun mood_untouched_readsBackNil() {
        assertNull(roundTrip(DayDraft()).mood)
        assertTrue(DayDraftMapper.toRows(day, DayDraft(), idFor).factors.none { it.kindRaw == "mood" })
    }

    @Test
    fun mood_value_roundTrips() {
        assertEquals(4, roundTrip(DayDraft(mood = 4)).mood)
    }

    // --- Day note (row 11): empty/null → nil ---

    @Test
    fun note_untouched_readsBackNil() {
        assertNull(roundTrip(DayDraft()).note)
    }

    @Test
    fun note_value_roundTrips() {
        assertEquals("rough day", roundTrip(DayDraft(note = "rough day")).note)
    }

    // --- Empty confirm: exactly one DayEntry marker row, nothing else ---

    @Test
    fun emptyConfirm_recordsOnlyTheDayEntryMarker() {
        val rows = DayDraftMapper.toRows(day, DayDraft(), idFor)
        assertEquals("the anchor DayEntry is always emitted", day, rows.dayEntry.epochDay)
        assertNull("empty confirm invents no note", rows.dayEntry.note)
        assertTrue(rows.symptoms.isEmpty())
        assertTrue(rows.pain.isEmpty())
        assertTrue(rows.bleeding.isEmpty())
        assertTrue("empty confirm writes zero factor rows", rows.factors.isEmpty())
    }

    // --- Full identity: a fully-populated draft round-trips unchanged ---

    @Test
    fun fullyPopulatedDraft_roundTripsToIdentity() {
        val draft = DayDraft(
            symptoms = mapOf(SymptomType.PELVIC_PAIN to null, SymptomType.HEADACHE to 0, SymptomType.FATIGUE to 7),
            pain = mapOf(BodyRegion.LOWER_BACK to 5, BodyRegion.HIPS to null),
            flow = FlowIntensity.MEDIUM,
            clots = true,
            foodTags = setOf(FoodTag.DAIRY, FoodTag.CAFFEINE),
            foodNote = "late dinner",
            activityType = ActivityType.CARDIO,
            activityMinutes = 45.0,
            activityIntensity = ActivityIntensity.MEDIUM,
            sleepHours = 7.5,
            sleepHoursSet = true,
            sleepQuality = SleepQuality.OKAY,
            medsTaken = setOf("med-x", "med-y"),
            tookPRN = true,
            stress = 3,
            mood = 4,
            note = "a full day",
        )
        assertEquals(draft, roundTrip(draft))
    }
}
