package com.endoapp.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.endoapp.app.health.HealthRecordMapping.SleepStageValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM guard for the ONLY genuinely bespoke logic in the Health Connect import path: the
 * record→[com.endoapp.app.data.entities.ImportedSample] value mapping + iOS-parity day-bucketing
 * ([HealthRecordMapping], the Android analog of iOS `HealthSampleMapping`). No emulator, no real HC
 * provider, no `androidx.health.*` record instance is constructed — the mapper is value-in/value-out,
 * so the four field maps, the defensive decode fallbacks, deterministic `stableId`, and the
 * DST/travel/midnight bucketing vectors are all provable off plain values.
 *
 * The ASSUMED SDK integer constants (RESEARCH A1 flow / A2 sleep-stage / A3 exerciseType) are pinned
 * against the REAL `androidx.health.connect.client.records.*` constants by [sdkConstantsMatchMappersNamedConstants]
 * — turning each "assumption" into a test-enforced fact that a future SDK bump would break loudly.
 */
class HealthRecordMappingTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val apia = ZoneId.of("Pacific/Apia")
    private val fixedClock: () -> Long = { 1_700_000_000_000L }

    // ---- Cycle / menstruation field map (A1 constants, A4 FLOW_UNKNOWN→spotting) ----

    @Test
    fun menstruationFieldMap_flowIntsMapToRaws() {
        val start = Instant.parse("2026-06-15T08:00:00Z")
        fun rawFor(flow: Int) =
            HealthRecordMapping.menstruationSample("m1", start, flow, berlin, fixedClock).flowRaw

        assertEquals("light", rawFor(HealthRecordMapping.FLOW_LIGHT))
        assertEquals("medium", rawFor(HealthRecordMapping.FLOW_MEDIUM))
        assertEquals("heavy", rawFor(HealthRecordMapping.FLOW_HEAVY))
        // A4: FLOW_UNKNOWN → "spotting" (iOS .unspecified→.spotting parity — a present record = flow
        // logged, intensity unknown → the lightest honest bucket, NEVER null/fabricated).
        assertEquals("spotting", rawFor(HealthRecordMapping.FLOW_UNKNOWN))
    }

    @Test
    fun menstruationFieldMap_setsOnlyCycleColumns() {
        val start = Instant.parse("2026-06-15T08:00:00Z")
        val row = HealthRecordMapping.menstruationSample("m1", start, HealthRecordMapping.FLOW_MEDIUM, berlin, fixedClock)

        assertEquals("cycle", row.domainRaw)
        assertEquals("medium", row.flowRaw)
        assertEquals("m1", row.hcRecordId)
        assertEquals(HealthRecordMapping.stableId("m1"), row.id)
        assertEquals(LocalDate.of(2026, 6, 15).toEpochDay(), row.epochDay)
        assertEquals(1_700_000_000_000L, row.importedAtMillis)
        // Every other health-value column stays null (absence ≠ 0).
        assertNull(row.sleepHours)
        assertNull(row.sleepQualityRaw)
        assertNull(row.activityTypeRaw)
        assertNull(row.activityMinutes)
        assertNull(row.ateThatDay)
    }

    @Test
    fun menstruationFieldMap_defensiveUnknownFlowIntFallsToSpotting() {
        val start = Instant.parse("2026-06-15T08:00:00Z")
        // An int outside {0,1,2,3} (a future/garbage provider value) must never crash or fabricate —
        // a present record is honest flow, so it falls to the lightest bucket "spotting" (T-21-02a).
        for (garbage in listOf(-1, 4, 99, Int.MAX_VALUE)) {
            assertEquals(
                "Unknown flow int $garbage must map defensively to spotting.",
                "spotting",
                HealthRecordMapping.menstruationSample("m1", start, garbage, berlin, fixedClock).flowRaw,
            )
        }
    }

    // ---- Sleep field map (A2 asleep-stage set, A7 stage-less full duration) ----

    @Test
    fun sleepFieldMap_sumsAsleepStagesExcludingAwake() {
        // A night: 1h light + 2h deep + 1h REM asleep (=4h) with a 30m AWAKE and 15m OUT_OF_BED that
        // must be EXCLUDED (mirrors iOS allAsleepValues); sessionStart/End are the outer bounds.
        val base = Instant.parse("2026-06-15T22:00:00Z")
        fun at(minutes: Long) = base.plusSeconds(minutes * 60)
        val stages = listOf(
            SleepStageValue(HealthRecordMapping.STAGE_LIGHT, at(0), at(60)),
            SleepStageValue(HealthRecordMapping.STAGE_AWAKE, at(60), at(90)),        // excluded
            SleepStageValue(HealthRecordMapping.STAGE_DEEP, at(90), at(210)),        // 2h
            SleepStageValue(HealthRecordMapping.STAGE_OUT_OF_BED, at(210), at(225)), // excluded
            SleepStageValue(HealthRecordMapping.STAGE_REM, at(225), at(285)),        // 1h
        )
        val row = HealthRecordMapping.sleepSample("s1", at(0), at(285), stages, berlin, fixedClock)

        assertEquals("sleep", row.domainRaw)
        assertEquals(4.0, row.sleepHours!!, 1e-9)
        // Band derivation is ImportedSignals' job over the SUMMED nightly total — the row leaves it null.
        assertNull(row.sleepQualityRaw)
        assertEquals(HealthRecordMapping.stableId("s1"), row.id)
    }

    @Test
    fun sleepFieldMap_stageLessSessionUsesFullDuration_A7() {
        // A7: a provider that emits no stages → best-effort full session duration as asleep hours.
        val start = Instant.parse("2026-06-15T23:00:00Z")
        val end = start.plusSeconds(8 * 3600) // 8h
        val row = HealthRecordMapping.sleepSample("s2", start, end, emptyList(), berlin, fixedClock)

        assertEquals(8.0, row.sleepHours!!, 1e-9)
    }

    @Test
    fun sleepFieldMap_ignoresUnknownStageIntsDefensively() {
        val start = Instant.parse("2026-06-15T22:00:00Z")
        val stages = listOf(
            SleepStageValue(HealthRecordMapping.STAGE_SLEEPING, start, start.plusSeconds(3600)), // 1h asleep
            SleepStageValue(999, start.plusSeconds(3600), start.plusSeconds(7200)),              // unknown → excluded
        )
        val row = HealthRecordMapping.sleepSample("s3", start, start.plusSeconds(7200), stages, berlin, fixedClock)
        assertEquals(1.0, row.sleepHours!!, 1e-9)
    }

    // ---- Exercise field map (A3 family membership + OTHER fallback) ----

    @Test
    fun exerciseFieldMap_familyMapAndMinutes() {
        val start = Instant.parse("2026-06-15T09:00:00Z")
        val end = start.plusSeconds(45 * 60) // 45 min
        fun typeRaw(ex: Int) =
            HealthRecordMapping.exerciseSample("e", start, end, ex, berlin, fixedClock).activityTypeRaw

        assertEquals("walking", typeRaw(HealthRecordMapping.EXERCISE_WALKING))
        assertEquals("walking", typeRaw(HealthRecordMapping.EXERCISE_HIKING))
        assertEquals("cardio", typeRaw(HealthRecordMapping.EXERCISE_RUNNING))
        assertEquals("cardio", typeRaw(HealthRecordMapping.EXERCISE_BIKING))
        assertEquals("cardio", typeRaw(HealthRecordMapping.EXERCISE_ELLIPTICAL))
        assertEquals("strength", typeRaw(HealthRecordMapping.EXERCISE_STRENGTH_TRAINING))
        assertEquals("strength", typeRaw(HealthRecordMapping.EXERCISE_WEIGHTLIFTING))
        assertEquals("yogaStretching", typeRaw(HealthRecordMapping.EXERCISE_YOGA))
        assertEquals("yogaStretching", typeRaw(HealthRecordMapping.EXERCISE_PILATES))

        val row = HealthRecordMapping.exerciseSample("e1", start, end, HealthRecordMapping.EXERCISE_RUNNING, berlin, fixedClock)
        assertEquals("workouts", row.domainRaw)
        assertEquals(45.0, row.activityMinutes!!, 1e-9)
        assertNull(row.flowRaw)
    }

    @Test
    fun exerciseFieldMap_unmappedTypeFallsToOther_A3() {
        val start = Instant.parse("2026-06-15T09:00:00Z")
        val end = start.plusSeconds(1800)
        // GOLF(32), an unmapped-family type, and a garbage int both fall to "other" by design (never crash).
        for (ex in listOf(HealthRecordMapping.EXERCISE_GOLF_UNMAPPED, 12345, -7)) {
            assertEquals(
                "Unmapped exerciseType $ex must fall to other.",
                "other",
                HealthRecordMapping.exerciseSample("e", start, end, ex, berlin, fixedClock).activityTypeRaw,
            )
        }
    }

    // ---- Nutrition field map (presence-only) ----

    @Test
    fun nutritionFieldMap_presenceOnlyAteThatDay() {
        val start = Instant.parse("2026-06-15T12:30:00Z")
        val row = HealthRecordMapping.nutritionSample("n1", start, berlin, fixedClock)

        assertEquals("nutrition", row.domainRaw)
        assertEquals(true, row.ateThatDay)
        // Never a FoodTag, never a micronutrient — presence only.
        assertNull(row.flowRaw)
        assertNull(row.sleepHours)
        assertNull(row.activityTypeRaw)
        assertEquals(LocalDate.of(2026, 6, 15).toEpochDay(), row.epochDay)
    }

    // ---- Deterministic idempotency ----

    @Test
    fun stableId_isDeterministicAndDistinct() {
        assertEquals(HealthRecordMapping.stableId("abc"), HealthRecordMapping.stableId("abc"))
        assertNotEquals(HealthRecordMapping.stableId("abc"), HealthRecordMapping.stableId("abd"))
        // A re-import of the same record produces a byte-identical row (id + all columns equal).
        val start = Instant.parse("2026-06-15T08:00:00Z")
        val a = HealthRecordMapping.menstruationSample("dup", start, HealthRecordMapping.FLOW_HEAVY, berlin, fixedClock)
        val b = HealthRecordMapping.menstruationSample("dup", start, HealthRecordMapping.FLOW_HEAVY, berlin, fixedClock)
        assertEquals(a, b)
    }

    // ---- Bucketing vectors: DST / travel / midnight (iOS Calendar.startOfDay parity) ----

    @Test
    fun bucketing_dstSpringForwardDayStillBucketsToThatCalendarDay() {
        // 2026-03-29 is the Europe/Berlin spring-forward day (02:00→03:00 skips). A record at local
        // midday still buckets to 2026-03-29 — Instant→LocalDate is well-defined across the 23h day.
        val midday = LocalDateTime.of(2026, 3, 29, 12, 0).atZone(berlin).toInstant()
        assertEquals(
            LocalDate.of(2026, 3, 29).toEpochDay(),
            HealthRecordMapping.epochDayFor(midday, berlin),
        )
    }

    @Test
    fun bucketing_isByDeviceZoneNotUtc_travelVector() {
        // The SAME instant lands on DIFFERENT calendar days in Berlin vs Pacific/Apia (UTC+13). The
        // mapper MUST bucket by the supplied device zone, never UTC and never the record's zoneOffset.
        val instant = Instant.parse("2026-06-15T12:00:00Z")
        assertEquals(LocalDate.of(2026, 6, 15).toEpochDay(), HealthRecordMapping.epochDayFor(instant, berlin))
        assertEquals(LocalDate.of(2026, 6, 16).toEpochDay(), HealthRecordMapping.epochDayFor(instant, apia))
        assertNotEquals(
            HealthRecordMapping.epochDayFor(instant, berlin),
            HealthRecordMapping.epochDayFor(instant, apia),
        )
    }

    @Test
    fun bucketing_midnightSpanningSleepLandsOnStartDay() {
        // A sleep session 23:00 → 07:00 next day (Berlin) lands ENTIRELY on its START day — mirrors iOS
        // midnight-crossing attribution (WR-02). The whole 8h is one row keyed to the start day.
        val start = LocalDateTime.of(2026, 6, 15, 23, 0).atZone(berlin).toInstant()
        val end = LocalDateTime.of(2026, 6, 16, 7, 0).atZone(berlin).toInstant()
        val row = HealthRecordMapping.sleepSample("mid", start, end, emptyList(), berlin, fixedClock)

        assertEquals(LocalDate.of(2026, 6, 15).toEpochDay(), row.epochDay)
        assertEquals(8.0, row.sleepHours!!, 1e-9)
    }

    // ---- Assumption confirmation: named constants == the REAL SDK constants ----

    @Test
    fun sdkConstantsMatchMappersNamedConstants() {
        // A1 — MenstruationFlowRecord flow ints.
        assertEquals(MenstruationFlowRecord.FLOW_UNKNOWN, HealthRecordMapping.FLOW_UNKNOWN)
        assertEquals(MenstruationFlowRecord.FLOW_LIGHT, HealthRecordMapping.FLOW_LIGHT)
        assertEquals(MenstruationFlowRecord.FLOW_MEDIUM, HealthRecordMapping.FLOW_MEDIUM)
        assertEquals(MenstruationFlowRecord.FLOW_HEAVY, HealthRecordMapping.FLOW_HEAVY)

        // A2 — SleepSessionRecord stage ints (asleep set + the excluded ones).
        assertEquals(SleepSessionRecord.STAGE_TYPE_SLEEPING, HealthRecordMapping.STAGE_SLEEPING)
        assertEquals(SleepSessionRecord.STAGE_TYPE_LIGHT, HealthRecordMapping.STAGE_LIGHT)
        assertEquals(SleepSessionRecord.STAGE_TYPE_DEEP, HealthRecordMapping.STAGE_DEEP)
        assertEquals(SleepSessionRecord.STAGE_TYPE_REM, HealthRecordMapping.STAGE_REM)
        assertEquals(SleepSessionRecord.STAGE_TYPE_AWAKE, HealthRecordMapping.STAGE_AWAKE)
        assertEquals(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, HealthRecordMapping.STAGE_OUT_OF_BED)
        assertEquals(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, HealthRecordMapping.STAGE_AWAKE_IN_BED)
        assertEquals(SleepSessionRecord.STAGE_TYPE_UNKNOWN, HealthRecordMapping.STAGE_UNKNOWN)

        // A3 — a representative slice of the ExerciseSessionRecord family constants.
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, HealthRecordMapping.EXERCISE_WALKING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_HIKING, HealthRecordMapping.EXERCISE_HIKING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, HealthRecordMapping.EXERCISE_RUNNING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, HealthRecordMapping.EXERCISE_BIKING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL, HealthRecordMapping.EXERCISE_ELLIPTICAL)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, HealthRecordMapping.EXERCISE_STRENGTH_TRAINING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, HealthRecordMapping.EXERCISE_WEIGHTLIFTING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, HealthRecordMapping.EXERCISE_YOGA)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_PILATES, HealthRecordMapping.EXERCISE_PILATES)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_GOLF, HealthRecordMapping.EXERCISE_GOLF_UNMAPPED)
    }

    // ---- Domain raws align with the app-local HealthDomain the merge switches on ----

    @Test
    fun domainRaws_matchHealthDomain() {
        val start = Instant.parse("2026-06-15T08:00:00Z")
        assertTrue(
            HealthRecordMapping.menstruationSample("m", start, 1, berlin, fixedClock).domainRaw ==
                com.endoapp.app.insightsbridge.HealthDomain.CYCLE.raw,
        )
        assertTrue(
            HealthRecordMapping.sleepSample("s", start, start.plusSeconds(3600), emptyList(), berlin, fixedClock).domainRaw ==
                com.endoapp.app.insightsbridge.HealthDomain.SLEEP.raw,
        )
        assertTrue(
            HealthRecordMapping.exerciseSample("e", start, start.plusSeconds(3600), 56, berlin, fixedClock).domainRaw ==
                com.endoapp.app.insightsbridge.HealthDomain.WORKOUTS.raw,
        )
        assertTrue(
            HealthRecordMapping.nutritionSample("n", start, berlin, fixedClock).domainRaw ==
                com.endoapp.app.insightsbridge.HealthDomain.NUTRITION.raw,
        )
    }
}
