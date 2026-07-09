// EndoApp (:app) — the per-day imported-sample fold (DATA-05, CR-01 / WR-04).
//
// Kotlin port of `Repository.ImportedSignals` (Sources/EndoApp/Persistence/Repository.swift 793–859).
// Flattens a day's `ImportedSample` rows into the per-day imported signals the SnapshotProjector
// merges (manual-wins) against manual logging. Pure — no Room/Android type inside the fold logic
// beyond the entity params, decodes defensively, never throws.
//
// This shape matters for Phase 21 (Health Connect import) but is defined now so the projector's
// imported-merge inputs exist and are pinned by the parity suite.
package com.endoapp.app.insightsbridge

import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.insights.ActivityType
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.SleepQuality

/**
 * The four additive Health import domains (D-14/D-15/D-16). App-local port of the iOS `HealthDomain`
 * (the Android `:insights` module does not carry it). Raw values equal the Swift case names verbatim.
 */
enum class HealthDomain(val raw: String) {
    CYCLE("cycle"),
    SLEEP("sleep"),
    WORKOUTS("workouts"),
    NUTRITION("nutrition");

    companion object {
        /** Defensive decode: an unknown/corrupt/`null` raw returns `null` → the row contributes nothing. */
        fun fromRawOrNull(raw: String?): HealthDomain? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}

/**
 * The per-day imported signals, folded from a day's [ImportedSample] rows.
 *
 * SLEEP (CR-01): Apple Health / Health Connect record ONE night as MANY segment rows (one per
 * stage/awake transition). A night's asleep hours is therefore the SUM of its rows, not any single
 * row; the coarse [sleepQuality] band is derived from the SUMMED total (never last-writer-wins on a
 * ~minutes-long fragment). A `null` row contributes nothing — [sleepHours] stays `null` until at
 * least one real `.sleep` row arrives (nil ≠ 0).
 *
 * WORKOUTS (WR-04): a day can have MULTIPLE workouts, each its own row. Minutes are SUMMED and the
 * type comes from the LONGEST session (deterministic regardless of fetch order), not an arbitrary
 * last-writer-wins.
 *
 * CYCLE / NUTRITION: single-valued (deduped upstream); last-writer-wins is correct there.
 *
 * Unknown/corrupt domain raw → the row contributes nothing (defensive, never crash — T-18-06).
 */
data class ImportedSignals(
    val flow: FlowIntensity? = null,
    val sleepHours: Double? = null,
    val sleepQuality: SleepQuality? = null,
    val activityType: ActivityType? = null,
    val activityMinutes: Double? = null,
    /** Nutrition presence (D-16). `true` ⇒ an "ate that day" food-presence signal exists. */
    val ateThatDay: Boolean? = null,
) {
    companion object {
        // Documented coarse sleep-quality thresholds (mirrors iOS HealthSampleMapping): a boundary
        // value lands on the UPPER band. [0, 5) → POOR; [5, 7) → OKAY; [7, ∞) → GOOD.
        private const val POOR_HOURS_CEILING = 5.0
        private const val OKAY_HOURS_CEILING = 7.0

        private fun coarseSleepQuality(hours: Double): SleepQuality = when {
            hours < POOR_HOURS_CEILING -> SleepQuality.POOR
            hours < OKAY_HOURS_CEILING -> SleepQuality.OKAY
            else -> SleepQuality.GOOD
        }

        /** Flatten a day's imported rows into per-day signals (the iOS `init(rows:)` port). */
        fun from(rows: List<ImportedSample>): ImportedSignals {
            var flow: FlowIntensity? = null
            var sleepHours: Double? = null
            var activityType: ActivityType? = null
            var ateThatDay: Boolean? = null

            // Workout accumulators (WR-04): sum minutes; the type comes from the longest session.
            var workoutMinutesTotal: Double? = null
            var longestWorkoutMinutes = -1.0

            for (row in rows) {
                when (HealthDomain.fromRawOrNull(row.domainRaw)) {
                    HealthDomain.CYCLE ->
                        // nil/unknown flow raw → nil (never fabricated), mirroring FlowIntensity.fromRawOrNull.
                        flow = FlowIntensity.fromRawOrNull(row.flowRaw)
                    HealthDomain.SLEEP ->
                        // SUM the night's segments (CR-01). A nil row contributes nothing (nil ≠ 0).
                        row.sleepHours?.let { rowHours -> sleepHours = (sleepHours ?: 0.0) + rowHours }
                    HealthDomain.WORKOUTS -> {
                        // SUM minutes; keep the LONGEST session's type (WR-04 deterministic collapse).
                        val minutes = row.activityMinutes
                        if (minutes != null) {
                            workoutMinutesTotal = (workoutMinutesTotal ?: 0.0) + minutes
                            if (minutes > longestWorkoutMinutes) {
                                longestWorkoutMinutes = minutes
                                activityType = row.activityTypeRaw?.let { ActivityType.fromRaw(it) }
                            }
                        } else if (activityType == null) {
                            // A typed-but-minute-less workout row still establishes the type (never
                            // fabricates minutes — those stay nil unless a row carries them).
                            activityType = row.activityTypeRaw?.let { ActivityType.fromRaw(it) }
                        }
                    }
                    HealthDomain.NUTRITION ->
                        ateThatDay = row.ateThatDay
                    null ->
                        continue // unknown/corrupt domain raw → contribute nothing (defensive, never crash).
                }
            }

            // Derive the sleep band from the SUMMED total (CR-01) — not any single fragment's band.
            val sleepQuality = sleepHours?.let { coarseSleepQuality(it) }

            return ImportedSignals(
                flow = flow,
                sleepHours = sleepHours,
                sleepQuality = sleepQuality,
                activityType = activityType,
                activityMinutes = workoutMinutesTotal,
                ateThatDay = ateThatDay,
            )
        }
    }
}
