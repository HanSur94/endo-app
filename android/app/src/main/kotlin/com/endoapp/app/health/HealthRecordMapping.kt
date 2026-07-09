// The ONLY genuinely bespoke logic in the Health Connect import path (Phase 21, AHC-03): the pure
// record→[ImportedSample] value mapping + iOS-parity day-bucketing. The Android analog of the iOS
// `HealthSampleMapping` — a stateless namespace of TOTAL, DEFENSIVE, value-in/value-out functions
// (no HealthConnectClient, no `androidx.health.*` import), so every field map and every bucketing
// vector is unit-testable off plain values with no emulator (see HealthRecordMappingTest).
//
// DEFENSIVE DECODE (T-21-02a / ASVS V5): every decode is TOTAL and never crashes on an unknown
// flow / stage / exerciseType int — a present record is honest (unknown flow → the lightest bucket
// "spotting"; an unknown sleep stage → simply not counted; an unmapped exerciseType → "other").
// Absence is NEVER coerced into a fabricated value (no `?: 0` on a health value).
//
// BUCKETING (T-21-02b): every record buckets by its START instant in the DEVICE's current zone
// (the caller supplies `ZoneId.systemDefault()`), NEVER the record's stored `zoneOffset` — the single
// iOS-parity attribution rule, pinned under DST / travel / midnight-crossing vectors.
package com.endoapp.app.health

import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.insightsbridge.HealthDomain
import com.endoapp.insights.ActivityType
import com.endoapp.insights.FlowIntensity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Pure Health-Connect-record → [ImportedSample] mappings (D-14/D-15/D-16). Stateless `object`.
 *
 * The four field maps take EXTRACTED record values (never an `androidx.health.*` type), so the
 * [HealthConnectReader] adapter is the sole place records are pulled apart; this mapper — and its
 * tests — stay pure. The ASSUMED SDK integer constants (RESEARCH A1/A2/A3) live here as NAMED
 * constants (the single auditable place to correct one) and are CONFIRMED against
 * `androidx.health.connect.client.records.*` (connect-client 1.1.0) — pinned by
 * `HealthRecordMappingTest.sdkConstantsMatchMappersNamedConstants` so a future SDK drift breaks loudly.
 */
object HealthRecordMapping {

    // ---- Domain raws (MUST equal the app-local HealthDomain the SnapshotProjector merge switches on) ----
    private val DOMAIN_CYCLE = HealthDomain.CYCLE.raw
    private val DOMAIN_SLEEP = HealthDomain.SLEEP.raw
    private val DOMAIN_WORKOUTS = HealthDomain.WORKOUTS.raw
    private val DOMAIN_NUTRITION = HealthDomain.NUTRITION.raw

    // ---- A1: MenstruationFlowRecord.flow int constants ----
    // CONFIRMED == androidx.health.connect.client.records.MenstruationFlowRecord.FLOW_* (connect-client
    // 1.1.0). If a future SDK renumbers these, the SDK-parity test fails — fix HERE (single source).
    const val FLOW_UNKNOWN = 0
    const val FLOW_LIGHT = 1
    const val FLOW_MEDIUM = 2
    const val FLOW_HEAVY = 3

    // ---- A2: SleepSessionRecord.Stage.stage int constants ----
    // CONFIRMED == androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_* (1.1.0).
    // Asleep set = {SLEEPING, LIGHT, DEEP, REM}; AWAKE / OUT_OF_BED / AWAKE_IN_BED / UNKNOWN excluded
    // (mirrors iOS `allAsleepValues`).
    const val STAGE_UNKNOWN = 0
    const val STAGE_AWAKE = 1
    const val STAGE_SLEEPING = 2
    const val STAGE_OUT_OF_BED = 3
    const val STAGE_LIGHT = 4
    const val STAGE_DEEP = 5
    const val STAGE_REM = 6
    const val STAGE_AWAKE_IN_BED = 7
    private val ASLEEP_STAGES = setOf(STAGE_SLEEPING, STAGE_LIGHT, STAGE_DEEP, STAGE_REM)

    // ---- A3: ExerciseSessionRecord.exerciseType family membership ----
    // CONFIRMED == androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_* (1.1.0).
    // Family map mirrors the iOS HealthSampleMapping.activityType(from:) switch; everything unmapped →
    // "other" BY DESIGN (A3: mis-typing is LOW risk — minutes still SUM correctly downstream).
    const val EXERCISE_WALKING = 79
    const val EXERCISE_HIKING = 37
    const val EXERCISE_RUNNING = 56
    const val EXERCISE_RUNNING_TREADMILL = 57
    const val EXERCISE_BIKING = 8
    const val EXERCISE_BIKING_STATIONARY = 9
    const val EXERCISE_SWIMMING_POOL = 74
    const val EXERCISE_SWIMMING_OPEN_WATER = 73
    const val EXERCISE_HIIT = 36
    const val EXERCISE_ROWING = 53
    const val EXERCISE_ROWING_MACHINE = 54
    const val EXERCISE_ELLIPTICAL = 25
    const val EXERCISE_STRENGTH_TRAINING = 70
    const val EXERCISE_WEIGHTLIFTING = 81
    const val EXERCISE_CALISTHENICS = 13
    const val EXERCISE_YOGA = 83
    const val EXERCISE_PILATES = 48
    const val EXERCISE_STRETCHING = 71

    /** GOLF(32) — an intentionally UNMAPPED type, referenced by the test to prove the OTHER fallback. */
    const val EXERCISE_GOLF_UNMAPPED = 32

    private val WALKING_TYPES = setOf(EXERCISE_WALKING, EXERCISE_HIKING)
    private val CARDIO_TYPES = setOf(
        EXERCISE_RUNNING, EXERCISE_RUNNING_TREADMILL, EXERCISE_BIKING, EXERCISE_BIKING_STATIONARY,
        EXERCISE_SWIMMING_POOL, EXERCISE_SWIMMING_OPEN_WATER, EXERCISE_HIIT, EXERCISE_ROWING,
        EXERCISE_ROWING_MACHINE, EXERCISE_ELLIPTICAL,
    )
    private val STRENGTH_TYPES = setOf(EXERCISE_STRENGTH_TRAINING, EXERCISE_WEIGHTLIFTING, EXERCISE_CALISTHENICS)
    private val YOGA_STRETCHING_TYPES = setOf(EXERCISE_YOGA, EXERCISE_PILATES, EXERCISE_STRETCHING)

    // ---- Shared primitives ----

    /**
     * The single iOS-parity day-attribution rule (T-21-02b): bucket by the record's START instant in
     * the DEVICE's current zone. `Instant → LocalDate` is well-defined across DST and the nonexistent
     * spring-forward midnight (the gap only bites `date → instant`, which this never does).
     */
    fun epochDayFor(startTime: Instant, zone: ZoneId): Long =
        startTime.atZone(zone).toLocalDate().toEpochDay()

    /**
     * Deterministic idempotency: a stable UUIDv3-style hash of the HC record id, so re-importing the
     * same record produces a BYTE-IDENTICAL row (the primary-key `id` and the `hcRecordId` unique index
     * both collapse on REPLACE). Mirrors RESEARCH Pattern 5.
     */
    fun stableId(hcRecordId: String): String =
        UUID.nameUUIDFromBytes("hc:$hcRecordId".toByteArray()).toString()

    /**
     * FlowIntensity raw for a `MenstruationFlowRecord.flow` int. A present record = flow logged, so an
     * unknown/garbage int falls to the lightest honest bucket "spotting" (A4, iOS `.unspecified→.spotting`),
     * NEVER null or a fabricated intensity. `raw` values come from the shared [FlowIntensity] catalog.
     */
    fun flowRaw(flow: Int): String = when (flow) {
        FLOW_LIGHT -> FlowIntensity.LIGHT.raw
        FLOW_MEDIUM -> FlowIntensity.MEDIUM.raw
        FLOW_HEAVY -> FlowIntensity.HEAVY.raw
        else -> FlowIntensity.SPOTTING.raw // FLOW_UNKNOWN + any unrecognised int → spotting (defensive)
    }

    /** ActivityType raw for an `ExerciseSessionRecord.exerciseType` int; unmapped → "other" (A3). */
    fun activityTypeRaw(exerciseType: Int): String = when (exerciseType) {
        in WALKING_TYPES -> ActivityType.WALKING.raw
        in CARDIO_TYPES -> ActivityType.CARDIO.raw
        in STRENGTH_TYPES -> ActivityType.STRENGTH.raw
        in YOGA_STRETCHING_TYPES -> ActivityType.YOGA_STRETCHING.raw
        else -> ActivityType.OTHER.raw
    }

    /**
     * Asleep hours for a session = Σ of the asleep-stage segment durations (excluding AWAKE / OUT_OF_BED
     * / AWAKE_IN_BED / UNKNOWN, mirroring iOS `nightlyAsleepHours`). A stage-less session (A7) → the full
     * session duration as a best-effort. Unknown stage ints are simply not counted (defensive). Never
     * negative (a malformed end<start segment coerces to 0).
     */
    fun asleepHours(stages: List<SleepStageValue>, sessionStart: Instant, sessionEnd: Instant): Double {
        if (stages.isEmpty()) return durationHours(sessionStart, sessionEnd)
        return stages
            .filter { it.stage in ASLEEP_STAGES }
            .sumOf { durationHours(it.startTime, it.endTime) }
    }

    private fun durationHours(start: Instant, end: Instant): Double =
        (Duration.between(start, end).toMillis() / 3_600_000.0).coerceAtLeast(0.0)

    private fun durationMinutes(start: Instant, end: Instant): Double =
        (Duration.between(start, end).toMillis() / 60_000.0).coerceAtLeast(0.0)

    // ---- The four field maps (value-in → ImportedSample) ----

    /** `MenstruationFlowRecord` → a `cycle` row carrying only `flowRaw`. */
    fun menstruationSample(
        hcRecordId: String,
        startTime: Instant,
        flow: Int,
        zone: ZoneId,
        clock: () -> Long = System::currentTimeMillis,
    ): ImportedSample = ImportedSample(
        id = stableId(hcRecordId),
        hcRecordId = hcRecordId,
        epochDay = epochDayFor(startTime, zone),
        domainRaw = DOMAIN_CYCLE,
        flowRaw = flowRaw(flow),
        importedAtMillis = clock(),
    )

    /**
     * `SleepSessionRecord` → one `sleep` row per session carrying `sleepHours` = Σ asleep-stage
     * durations. `sleepQualityRaw` is left null — the coarse band is derived by `ImportedSignals` over
     * the SUMMED nightly total, never per-fragment.
     */
    fun sleepSample(
        hcRecordId: String,
        startTime: Instant,
        endTime: Instant,
        stages: List<SleepStageValue>,
        zone: ZoneId,
        clock: () -> Long = System::currentTimeMillis,
    ): ImportedSample = ImportedSample(
        id = stableId(hcRecordId),
        hcRecordId = hcRecordId,
        epochDay = epochDayFor(startTime, zone),
        domainRaw = DOMAIN_SLEEP,
        sleepHours = asleepHours(stages, startTime, endTime),
        importedAtMillis = clock(),
    )

    /** `ExerciseSessionRecord` → a `workouts` row carrying `activityTypeRaw` + `activityMinutes`. */
    fun exerciseSample(
        hcRecordId: String,
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int,
        zone: ZoneId,
        clock: () -> Long = System::currentTimeMillis,
    ): ImportedSample = ImportedSample(
        id = stableId(hcRecordId),
        hcRecordId = hcRecordId,
        epochDay = epochDayFor(startTime, zone),
        domainRaw = DOMAIN_WORKOUTS,
        activityTypeRaw = activityTypeRaw(exerciseType),
        activityMinutes = durationMinutes(startTime, endTime),
        importedAtMillis = clock(),
    )

    /**
     * `NutritionRecord` → a `nutrition` row carrying `ateThatDay = true`. Presence-ONLY: never a
     * FoodTag, never a micronutrient (D-16 privacy boundary — a record with dietary energy = "ate that day").
     */
    fun nutritionSample(
        hcRecordId: String,
        startTime: Instant,
        zone: ZoneId,
        clock: () -> Long = System::currentTimeMillis,
    ): ImportedSample = ImportedSample(
        id = stableId(hcRecordId),
        hcRecordId = hcRecordId,
        epochDay = epochDayFor(startTime, zone),
        domainRaw = DOMAIN_NUTRITION,
        ateThatDay = true,
        importedAtMillis = clock(),
    )

    /**
     * A pure sleep-stage segment value (start / end / raw stage int) — lets `asleepHours` be unit-tested
     * without constructing a `SleepSessionRecord.Stage`. The adapter builds these from `record.stages`.
     */
    data class SleepStageValue(val stage: Int, val startTime: Instant, val endTime: Instant)
}
