// EndoInsights (:insights) — shared chart-prep test harness (OWNED by this wave, plan 17-05).
//
// The Kotlin mirror of `EndoInsights/Tests/EndoInsightsTests/SeriesTestSupport.swift` (0 @Test cases —
// a fixture builder the FOUR sibling chart-prep test files read; ported FIRST in this wave). Per the
// locked decision the injected Swift `Calendar` is DROPPED: day identity is `java.time.LocalDate`, so
// `day(year, month, d)` is `LocalDate.of(...)` and `startOfDay` normalization is a no-op.
//
// Mirrors the Swift harness verbatim: a `day(...)` builder for absolute calendar days + a
// `daySnapshot(...)` builder that seeds ONLY the fields a test cares about (everything else honestly
// null/empty/false, so absence ≠ 0 holds in every fixture).
package com.endoapp.insights

import java.time.LocalDate
import java.util.UUID

/** Shared chart-prep fixture helpers for the series/scatter/normalization/scrub suites. */
internal object SeriesFixtures {

    /** Build a concrete `LocalDate` — zone-free day identity, never `LocalDate.now()` (determinism). */
    fun day(year: Int, month: Int, d: Int): LocalDate = LocalDate.of(year, month, d)

    /** Build a `DaySnapshot` seeding ONLY the fields a test cares about. Every unseeded field is honestly
     *  `null`/empty/`false` (absence ≠ 0), so a fixture never silently fabricates a logged signal. */
    fun daySnapshot(
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
    ): DaySnapshot = DaySnapshot.of(
        day = day,
        symptoms = symptoms,
        pain = pain,
        flow = flow,
        clots = clots,
        foodTags = foodTags,
        activityType = activityType,
        activityMinutes = activityMinutes,
        activityIntensity = activityIntensity,
        sleepHours = sleepHours,
        sleepQuality = sleepQuality,
        medicationsTaken = medicationsTaken,
        tookPRN = tookPRN,
        stress = stress,
        mood = mood,
        hasNote = hasNote,
        suppressed = suppressed,
    )
}
