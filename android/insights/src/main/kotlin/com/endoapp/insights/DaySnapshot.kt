// EndoInsights (:insights) — the per-day value snapshot contract.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/DaySnapshot.swift`. THE read-model every
// engine consumes: a flat, pure-JVM value type (no android./androidx.; `java.time.LocalDate` +
// `java.util.UUID` only). The app (Phase 18) maps Room rows → `[DaySnapshot]` at the Repository seam;
// the engine never touches the store.
//
// HONESTY CONTRACT (absence ≠ 0): every nullable field is `null` when that signal was NOT logged that
// day — NEVER coerced to 0/neutral. A `severity` of `null` means "logged-present without a severity",
// never 0. `flow == null` means no bleeding logged that day. The engine excludes null from any
// denominator; it never fabricates a default.
//
// PRIVACY: the snapshot carries `hasNote: Boolean` — note PRESENCE only, never the free-text prose.
//
// NaN UNREPRESENTABLE (Pitfall 4): the `Double?` fields (`activityMinutes`, `sleepHours`) are
// canonicalized at construction — a non-finite value (NaN / ±Infinity) maps to `null` ("not logged"),
// and `-0.0` is canonicalized to `0.0` so boxed data-class equality behaves as Swift IEEE. NaN can
// never enter the read model, so no downstream stat or equality is corrupted by it.
package com.endoapp.insights

import java.time.LocalDate
import java.util.UUID

/** One logged symptom on a day: its catalog type and an optional severity.
 *  `severity == null` ⇒ the symptom was logged present without a severity (absence ≠ 0). */
data class SymptomPoint(
    val type: SymptomType,
    val severity: Int?,
)

/** One marked pain region on a day: its body region and an optional severity.
 *  `severity == null` ⇒ marked without a severity (absence ≠ 0). */
data class PainPoint(
    val region: BodyRegion,
    val severity: Int?,
)

/** A pure-JVM value snapshot of one calendar day.
 *
 *  One `DaySnapshot` per calendar day that carries any logged signal. Nullable fields are `null` when
 *  that signal was NOT logged that day — absence is honest, never a fabricated default.
 *
 *  Constructed via [DaySnapshot.of] (or the private constructor's callers) so the `Double?` fields are
 *  canonicalized NaN-unrepresentable at the boundary. The primary constructor is private; use [of]. */
class DaySnapshot private constructor(
    /** The calendar day this snapshot represents (zone-free identity; the Repository normalizes). */
    val day: LocalDate,
    // Symptoms / pain
    val symptoms: List<SymptomPoint>,
    val pain: List<PainPoint>,
    // Bleeding
    val flow: FlowIntensity?,
    val clots: Boolean,
    // Food
    val foodTags: List<FoodTag>,
    // Activity
    val activityType: ActivityType?,
    /** Activity duration in minutes, if logged. Canonicalized finite-or-null at construction. */
    val activityMinutes: Double?,
    val activityIntensity: ActivityIntensity?,
    // Sleep
    /** Sleep duration in hours, if logged. Canonicalized finite-or-null at construction. */
    val sleepHours: Double?,
    val sleepQuality: SleepQuality?,
    // Medication
    val medicationsTaken: List<UUID>,
    val tookPRN: Boolean,
    // Context scales
    /** Stress on a 0…10 scale. `null` = NOT logged (absence ≠ 0). */
    val stress: Int?,
    /** Overall day mood on a 1…5 valence scale. `null` = NOT logged (absence ≠ 0/neutral). */
    val mood: Int?,
    // Note presence / suppression
    /** Whether the day carried a free-text note — PRESENCE only, never the prose. */
    val hasNote: Boolean,
    /** Per-day suppressed flag. Under suppression the engine emits NO bleeding/cycle trend. */
    val suppressed: Boolean,
) {
    companion object {
        /** Canonicalize a `Double?` at the boundary: non-finite (NaN/±Infinity) → `null` ("not logged");
         *  `-0.0` → `0.0` so boxed equality matches Swift IEEE `0.0 == -0.0`. */
        private fun canonicalize(value: Double?): Double? {
            if (value == null || !value.isFinite()) return null
            return if (value == 0.0) 0.0 else value // `-0.0 == 0.0` is true, so this normalizes the sign
        }

        /** The public factory — the only way to build a `DaySnapshot`. Enforces NaN-unrepresentable. */
        fun of(
            day: LocalDate,
            symptoms: List<SymptomPoint> = emptyList(),
            pain: List<PainPoint> = emptyList(),
            flow: FlowIntensity? = null,
            clots: Boolean = false,
            foodTags: List<FoodTag> = emptyList(),
            activityType: ActivityType? = null,
            activityMinutes: Double? = null,
            activityIntensity: ActivityIntensity? = null,
            sleepHours: Double? = null,
            sleepQuality: SleepQuality? = null,
            medicationsTaken: List<UUID> = emptyList(),
            tookPRN: Boolean = false,
            stress: Int? = null,
            mood: Int? = null,
            hasNote: Boolean = false,
            suppressed: Boolean = false,
        ): DaySnapshot = DaySnapshot(
            day = day,
            symptoms = symptoms,
            pain = pain,
            flow = flow,
            clots = clots,
            foodTags = foodTags,
            activityType = activityType,
            activityMinutes = canonicalize(activityMinutes),
            activityIntensity = activityIntensity,
            sleepHours = canonicalize(sleepHours),
            sleepQuality = sleepQuality,
            medicationsTaken = medicationsTaken,
            tookPRN = tookPRN,
            stress = stress,
            mood = mood,
            hasNote = hasNote,
            suppressed = suppressed,
        )
    }

    // Structural equality/hashCode over all fields (Swift `Equatable`). Because NaN is unrepresentable
    // and `-0.0` is canonicalized at construction, the boxed-Double equality pitfalls cannot bite here.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DaySnapshot) return false
        return day == other.day &&
            symptoms == other.symptoms &&
            pain == other.pain &&
            flow == other.flow &&
            clots == other.clots &&
            foodTags == other.foodTags &&
            activityType == other.activityType &&
            activityMinutes == other.activityMinutes &&
            activityIntensity == other.activityIntensity &&
            sleepHours == other.sleepHours &&
            sleepQuality == other.sleepQuality &&
            medicationsTaken == other.medicationsTaken &&
            tookPRN == other.tookPRN &&
            stress == other.stress &&
            mood == other.mood &&
            hasNote == other.hasNote &&
            suppressed == other.suppressed
    }

    override fun hashCode(): Int {
        var result = day.hashCode()
        result = 31 * result + symptoms.hashCode()
        result = 31 * result + pain.hashCode()
        result = 31 * result + (flow?.hashCode() ?: 0)
        result = 31 * result + clots.hashCode()
        result = 31 * result + foodTags.hashCode()
        result = 31 * result + (activityType?.hashCode() ?: 0)
        result = 31 * result + (activityMinutes?.hashCode() ?: 0)
        result = 31 * result + (activityIntensity?.hashCode() ?: 0)
        result = 31 * result + (sleepHours?.hashCode() ?: 0)
        result = 31 * result + (sleepQuality?.hashCode() ?: 0)
        result = 31 * result + medicationsTaken.hashCode()
        result = 31 * result + tookPRN.hashCode()
        result = 31 * result + (stress ?: 0)
        result = 31 * result + (mood ?: 0)
        result = 31 * result + hasNote.hashCode()
        result = 31 * result + suppressed.hashCode()
        return result
    }

    override fun toString(): String =
        "DaySnapshot(day=$day, symptoms=$symptoms, pain=$pain, flow=$flow, clots=$clots, " +
            "foodTags=$foodTags, activityType=$activityType, activityMinutes=$activityMinutes, " +
            "activityIntensity=$activityIntensity, sleepHours=$sleepHours, sleepQuality=$sleepQuality, " +
            "medicationsTaken=$medicationsTaken, tookPRN=$tookPRN, stress=$stress, mood=$mood, " +
            "hasNote=$hasNote, suppressed=$suppressed)"
}
