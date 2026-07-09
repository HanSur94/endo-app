// EndoInsights (:insights) — the three-state factor model the CorrelationEngine binarizes a day into.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/Factor.swift`. Pure-JVM. Swift's
// `enum Factor` with associated values becomes a Kotlin `sealed interface` (payload-free cases are
// `data object`, payloaded cases are `data class` — structural equality mirrors Swift `Equatable`).
//
// THE CORE HONESTY INVARIANT: every factor is exactly one of three states on a day —
// `PRESENT` / `ABSENT` / `UNOBSERVED`. `UNOBSERVED` is EXCLUDED from BOTH the present and absent groups
// (a null severity/stress/mood/sleepQuality is "not logged", NEVER a 0). The engine never coerces a
// missing signal to a value; it drops the day from that factor's denominator.
package com.endoapp.insights

/** A day's state for a given factor. `UNOBSERVED` is excluded from both groups (absence ≠ 0). */
internal enum class FactorState {
    PRESENT,
    ABSENT,
    UNOBSERVED,
}

/** A bounded factor a symptom/bleeding target can be correlated against. The payload cases carry the
 *  parametric families (food tag, activity type, cycle phase) so enumeration stays exhaustive and
 *  deterministic. Each case maps to a stable `key` — the `signalKey` fragment the Insight is built on. */
internal sealed interface Factor {
    /** The stable `signalKey` fragment for this factor. Sortable for determinism. */
    val key: String

    /** A human-readable label for the (hand-written, non-causal) Insight copy. */
    val displayLabel: String

    data class Food(val tag: FoodTag) : Factor {
        override val key: String get() = "food.${tag.raw}"
        override val displayLabel: String get() = foodLabel(tag)
    }

    data object ActivityPresent : Factor {
        override val key: String get() = "activity.present"
        override val displayLabel: String get() = "activity"
    }

    data class Activity(val type: ActivityType) : Factor {
        override val key: String get() = "activity.${type.raw}"
        override val displayLabel: String get() = activityLabel(type)
    }

    data object SleepPoor : Factor {
        override val key: String get() = "sleep.poor"
        override val displayLabel: String get() = "poor sleep"
    }

    data object StressHigh : Factor {
        override val key: String get() = "stress.high"
        override val displayLabel: String get() = "high stress"
    }

    data object MoodLow : Factor {
        override val key: String get() = "mood.low"
        override val displayLabel: String get() = "low mood"
    }

    data object MedicationTaken : Factor {
        override val key: String get() = "medication.taken"
        override val displayLabel: String get() = "medication"
    }

    data object PrnTaken : Factor {
        override val key: String get() = "prn.taken"
        override val displayLabel: String get() = "as-needed pain relief"
    }

    data class CyclePhaseFactor(val phase: CyclePhase) : Factor {
        override val key: String get() = "cyclePhase.${phase.raw}"
        override val displayLabel: String get() = "${phaseLabel(phase)} phase"
    }

    companion object {
        // MARK: - Named threshold constants — the ONLY judgement calls. Ported VERBATIM (do NOT re-derive).

        /** High-stress present when `stress >= 7` on the 0…10 scale (top ~third). */
        const val highStressThreshold: Int = 7

        /** Low-mood present when `mood <= 2` on the 1…5 valence scale (bottom two). */
        const val lowMoodThreshold: Int = 2

        /** Each correlation group (present/absent, W/O) needs at least this many observations before the
         *  engine will test the pair (mirrors `TrendsEngine.minLoggedDays`). */
        const val perGroupMinimum: Int = 3

        // MARK: - Three-state binarization (nil is NEVER coerced to 0).

        /** The factor's state on a day. `UNOBSERVED` for a null context scale / null sleepQuality / a truly
         *  empty day for the "any-log" factors; `ABSENT` for a day-with-any-log that simply lacks the signal.
         *  The cycle-phase factor needs the engine-supplied per-day label (see [stateOf] with `phase`). */
        fun stateOf(factor: Factor, day: DaySnapshot): FactorState = when (factor) {
            is Food -> {
                // Empty foodTags on a day-with-any-log → absent; a truly empty day → unobserved.
                if (day.foodTags.contains(factor.tag)) FactorState.PRESENT
                else if (hasAnyLog(day)) FactorState.ABSENT else FactorState.UNOBSERVED
            }

            is ActivityPresent -> {
                if (day.activityType != null) FactorState.PRESENT
                else if (hasAnyLog(day)) FactorState.ABSENT else FactorState.UNOBSERVED
            }

            is Activity -> {
                if (day.activityType == factor.type) FactorState.PRESENT
                else if (day.activityType != null) FactorState.ABSENT // a different activity logged → absent
                else if (hasAnyLog(day)) FactorState.ABSENT else FactorState.UNOBSERVED
            }

            is SleepPoor -> {
                val quality = day.sleepQuality
                if (quality == null) FactorState.UNOBSERVED // null sleep → unobserved
                else if (quality == SleepQuality.POOR) FactorState.PRESENT else FactorState.ABSENT
            }

            is StressHigh -> {
                val stress = day.stress
                if (stress == null) FactorState.UNOBSERVED // null stress → unobserved
                else if (stress >= highStressThreshold) FactorState.PRESENT else FactorState.ABSENT
            }

            is MoodLow -> {
                val mood = day.mood
                if (mood == null) FactorState.UNOBSERVED // null mood → unobserved
                else if (mood <= lowMoodThreshold) FactorState.PRESENT else FactorState.ABSENT
            }

            is MedicationTaken -> {
                if (day.medicationsTaken.isNotEmpty()) FactorState.PRESENT
                else if (hasAnyLog(day)) FactorState.ABSENT else FactorState.UNOBSERVED
            }

            is PrnTaken -> {
                if (day.tookPRN) FactorState.PRESENT
                else if (hasAnyLog(day)) FactorState.ABSENT else FactorState.UNOBSERVED
            }

            is CyclePhaseFactor -> {
                // Cannot be evaluated without the engine-supplied label; the engine calls the phase overload.
                FactorState.UNOBSERVED
            }
        }

        /** Cycle-phase-aware state. `phase` is the engine-supplied `CyclePhaseMapper` label for this day (or
         *  null if the day is outside any estimated window / the user is suppressed → no windows). For a
         *  `CyclePhaseFactor(target)`: `PRESENT` iff `phase == target`, `ABSENT` iff the day is labelled a
         *  DIFFERENT phase, `UNOBSERVED` iff the day has no phase label. Non-phase factors delegate. */
        fun stateOf(factor: Factor, day: DaySnapshot, phase: CyclePhase?): FactorState {
            if (factor !is CyclePhaseFactor) return stateOf(factor, day)
            if (phase == null) return FactorState.UNOBSERVED // no window label → unobserved (suppressed)
            return if (phase == factor.phase) FactorState.PRESENT else FactorState.ABSENT
        }

        // MARK: - "Day has any log" — the absent-vs-unobserved decision.

        /** True if the day carries ANY logged signal (so an empty food/med/activity is honestly `ABSENT`,
         *  not `UNOBSERVED`). Mirrors how `TrendsEngine` decides a day exists in the stream. */
        fun hasAnyLog(day: DaySnapshot): Boolean =
            day.symptoms.isNotEmpty() ||
                day.pain.isNotEmpty() ||
                day.flow != null ||
                day.clots ||
                day.foodTags.isNotEmpty() ||
                day.activityType != null ||
                day.activityMinutes != null ||
                day.activityIntensity != null ||
                day.sleepHours != null ||
                day.sleepQuality != null ||
                day.medicationsTaken.isNotEmpty() ||
                day.tookPRN ||
                day.stress != null ||
                day.mood != null ||
                day.hasNote
    }
}

// MARK: - Labels (package-local; mirrors CyclePhaseMapper.displayName / phaseName).

private fun foodLabel(tag: FoodTag): String = when (tag) {
    FoodTag.DAIRY -> "dairy"
    FoodTag.GLUTEN_WHEAT -> "gluten / wheat"
    FoodTag.CAFFEINE -> "caffeine"
    FoodTag.ALCOHOL -> "alcohol"
    FoodTag.HIGH_SUGAR -> "high-sugar food"
    FoodTag.HIGH_FODMAP -> "high-FODMAP food"
    FoodTag.RED_PROCESSED_MEAT -> "red / processed meat"
    FoodTag.LARGE_MEAL -> "a large meal"
    FoodTag.OTHER -> "other food"
}

private fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.WALKING -> "walking"
    ActivityType.CARDIO -> "cardio"
    ActivityType.STRENGTH -> "strength training"
    ActivityType.YOGA_STRETCHING -> "yoga / stretching"
    ActivityType.OTHER -> "other activity"
}

private fun phaseLabel(phase: CyclePhase): String = when (phase) {
    CyclePhase.MENSTRUAL -> "menstrual"
    CyclePhase.FOLLICULAR -> "follicular"
    CyclePhase.OVULATORY -> "ovulatory"
    CyclePhase.LUTEAL -> "luteal"
}
