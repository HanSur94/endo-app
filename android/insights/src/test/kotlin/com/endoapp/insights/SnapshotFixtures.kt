// EndoInsights (:insights) — synthetic `List<DaySnapshot>` fixture DSL for the engine suites.
//
// The Kotlin mirror of `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` (0 @Test cases —
// a fixture builder the WHOLE downstream suite reads; ported FIRST in this wave). Per Pattern 2 the
// injected Swift `Calendar` is DROPPED: day identity is `java.time.LocalDate`, `today` is offset 0, and
// `day(offset)` is `anchor.plusDays(offset)`. Every builder returns in-memory value types — no store.
//
// The keystone is `suppressedNoBleedingFixture`: months of suppressed, no-bleeding days that must yield
// NO bleeding/cycle Insight.
package com.endoapp.insights

import java.time.LocalDate
import java.util.UUID

/** Shared fixture helpers for the engine suites. */
internal object Fixtures {

    /** A fixed anchor: 2025-01-01 (matches the Swift `CycleMathTests` anchor; zone-free as LocalDate). */
    val anchor: LocalDate = LocalDate.of(2025, 1, 1)

    /** `anchor + offset` days. LocalDate is already day-identity, so no start-of-day normalization. */
    fun day(offset: Int): LocalDate = anchor.plusDays(offset.toLong())

    /** A single-day `DaySnapshot` builder defaulting EVERY field to absent/null/empty.
     *  Pass `offset` (days from `anchor`) and only the signals under test; everything else stays
     *  honestly unlogged (`null`/`[]`/`false`). */
    fun snapshot(
        offset: Int,
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
        day = day(offset),
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

    // MARK: SC3 — the suppressed / no-bleeding-for-months keystone fixture.

    /** Months of `suppressed == true`, `flow == null` days ending at `today` (offset 0). Every day carries
     *  SOME log (a symptom + stress) so the days exist, but NO bleeding is ever logged. */
    fun suppressedNoBleedingFixture(monthsBack: Int = 4): List<DaySnapshot> {
        val totalDays = monthsBack * 30
        return (0 until totalDays).map { i ->
            snapshot(
                offset = -i,
                symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 4)),
                stress = 5,
                suppressed = true,
            )
        }
    }

    // MARK: A regular symptom fixture clearing the threshold, with a recent-vs-prior delta.

    /** A symptom logged densely enough to clear the threshold in 7/30/90 windows, with a HIGHER recent
     *  severity than the immediately-prior window so `direction == up` is testable. */
    fun risingPelvicPainFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0..6) { // recent 7-day window — high severity
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 6))))
        }
        for (i in 7..13) { // immediately-prior 7-day window — low severity
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 2))))
        }
        for (i in 14..89) { // earlier history — mid severity (populates last30/last90/allTime)
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))))
        }
        return days
    }

    // MARK: Phase-12 flare fixtures (the honesty/absence contract for FlareEngine).

    /** A RISING-WITH-EVIDENCE flare fixture: 30 consecutive days, recent 10 (0…-9) at severity 7 (the
     *  rising spine), prior 20 (-10…-29) at severity 3 — clears the 21-day floor with a real recent rise. */
    fun risingFlareFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0..9) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7))))
        }
        for (i in 10..29) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))))
        }
        return days
    }

    /** A FLAT / NO-RISE flare fixture: 30 days, offsets 0…-29, ALL at severity 4. Clears the 21-day floor
     *  but has no rising spine, so a FlareEngine MUST yield ZERO flare warning (absence is honest). */
    fun flatNoRiseFixture(): List<DaySnapshot> =
        (0..29).map { snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 4))) }

    /** An UNDER-EVIDENCE rising flare fixture: a recent rise IS present, but only 14 logged days (< 21
     *  floor). A FlareEngine MUST yield EITHER empty OR a single honest insufficiency notice — never a
     *  non-notice flare warning. Recent 5 (0…-4) at severity 7, prior 9 (-5…-13) at severity 3. */
    fun risingButUnderEvidenceFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0..4) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7))))
        }
        for (i in 5..13) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))))
        }
        return days
    }

    // MARK: A sub-threshold fixture (< minLoggedDays) for the not-enough-data case.

    /** Exactly TWO logged days of a symptom — below the `minLoggedDays` (3) threshold in every window. */
    fun subThresholdFixture(): List<DaySnapshot> = listOf(
        snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 5))),
        snapshot(offset = -2, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 5))),
    )

    // MARK: A logged-0-vs-unlogged-null pair for the honesty mean test (absence ≠ 0).

    /** Three days each logging stress 0 → mean stress 0. */
    fun loggedZeroStressFixture(): List<DaySnapshot> =
        (0..2).map { snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3)), stress = 0) }

    /** Three days logging stress 4, plus three days that NEVER log stress (null) but DO log a symptom so the
     *  days exist. The null days must NOT pull the stress mean toward 0 (excluded from the denominator). */
    fun unloggedStressFixture(): List<DaySnapshot> {
        val days = (0..2).map {
            snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3)), stress = 4)
        }.toMutableList()
        days += (3..5).map {
            snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3)), stress = null)
        }
        return days
    }

    /** A fixture with one in-window bleeding day and NO suppression — the bleeding trend SHOULD emit. */
    fun bleedingFixture(): List<DaySnapshot> = listOf(
        snapshot(offset = 0, flow = FlowIntensity.MEDIUM),
        snapshot(offset = -1, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))),
        snapshot(offset = -2, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))),
    )

    // MARK: Phase-6 cycle fixtures (estimated phase mapping + honesty gates).

    /** A REGULAR cycle: 4 period runs at tight, equal gaps (~28 days, spread 0). Current run bleeds on
     *  offsets 0,1,2; luteal-window symptoms on +17…+21, follicular-window on +4…+7. */
    fun regularCycleFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (start in listOf(-84, -56, -28, 0)) {
            for (d in 0..2) days.add(snapshot(offset = start + d, flow = FlowIntensity.MEDIUM))
        }
        for (off in 17..21) {
            days.add(snapshot(offset = off, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5))))
        }
        for (off in 4..7) {
            days.add(snapshot(offset = off, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 4))))
        }
        return days
    }

    /** An IRREGULAR cycle: starts at offsets 0, -20, -65 → gaps 20, 45 → spread 25 > 9. MUST yield ZERO
     *  `.cyclePhase` insights (the irregular gate); symptom logs present so emptiness is the spread gate. */
    fun irregularCycleFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (start in listOf(0, -20, -65)) {
            for (d in 0..1) days.add(snapshot(offset = start - d, flow = FlowIntensity.MEDIUM))
        }
        for (off in -1 downTo -64 step 7) {
            days.add(snapshot(offset = off, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5))))
        }
        return days
    }

    /** An UNDER-SAMPLED cycle: a SINGLE period run (fewer than 2 start-to-start gaps). MUST yield ZERO
     *  `.cyclePhase` insights (no phantom phase from one bleed). */
    fun underSampledCycleFixture(): List<DaySnapshot> {
        val days = (0..2).map { snapshot(offset = -it, flow = FlowIntensity.MEDIUM) }.toMutableList()
        days += (3..9).map { snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5))) }
        return days
    }

    // MARK: Phase-7 correlation fixtures (surfaced/withheld/suppressed/deterministic).

    /** A STRONG, well-sampled severity association: ~40 days. 20 dairy days at pelvicPain severity 7; 20
     *  non-dairy (caffeine) days at severity 2 → large Cliff's δ over n=40 → ONE `.correlation` surfaces. */
    fun strongAssociationFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 20) {
            days.add(
                snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7)),
                    foodTags = listOf(FoodTag.DAIRY),
                ),
            )
        }
        for (i in 20 until 40) {
            days.add(
                snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 2)),
                    foodTags = listOf(FoodTag.CAFFEINE),
                ),
            )
        }
        return days
    }

    /** A STRONG, well-sampled BENEFICIAL association (meanDiff < 0): 20 activity days (walking) at
     *  pelvicPain severity 2; 20 no-activity (caffeine-logged) days at severity 7. Present-group fares
     *  BETTER → beneficial. */
    fun beneficialAssociationFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 20) {
            days.add(
                snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 2)),
                    activityType = ActivityType.WALKING,
                    activityMinutes = 30.0,
                ),
            )
        }
        for (i in 20 until 40) {
            days.add(
                snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7)),
                    foodTags = listOf(FoodTag.CAFFEINE),
                ),
            )
        }
        return days
    }

    /** An UNDER-SAMPLED association: the SAME strong shape but only 9 paired days (< minSample) → ZERO
     *  correlations. 5 dairy + 4 non-dairy days. */
    fun underSampledAssociationFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 5) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7)), foodTags = listOf(FoodTag.DAIRY)))
        }
        for (i in 5 until 9) {
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 2)), foodTags = listOf(FoodTag.CAFFEINE)))
        }
        return days
    }

    /** A SPURIOUS association: ~30 days where dairy and pelvicPain severity are assigned INDEPENDENTLY —
     *  dairy on EVEN offsets, high-vs-low severity keyed off `i % 3` → near-zero δ → ZERO correlations. */
    fun spuriousAssociationFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 30) {
            val hasDairy = (i % 2 == 0)
            val severity = if (i % 3 == 0) 6 else 3
            days.add(
                snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, severity)),
                    foodTags = if (hasDairy) listOf(FoodTag.DAIRY) else listOf(FoodTag.CAFFEINE),
                ),
            )
        }
        return days
    }

    /** A MULTIPLE-COMPARISON FLOOD: ~60 days where every factor and symptom is present at a deterministic
     *  ~50% rate, each driven by an INDEPENDENT per-(signal, day) splitmix hash → ≥100 tested pairs of pure
     *  noise → BH-FDR (q=0.10) MUST crush ALL → ZERO correlations. Fully deterministic (ULong wrapping). */
    fun multipleComparisonFloodFixture(): List<DaySnapshot> {
        // A deterministic, well-mixed bit for signal `salt` on day `i` — ~50% on, independent across salts.
        fun bit(salt: ULong, i: Int): Boolean {
            var x = i.toLong().toULong() + salt * 0x9E3779B97F4A7C15uL
            x = (x xor (x shr 30)) * 0xBF58476D1CE4E5B9uL
            x = (x xor (x shr 27)) * 0x94D049BB133111EBuL
            x = x xor (x shr 31)
            return (x and 1uL) == 1uL
        }

        return (0 until 60).map { i ->
            var foods = mutableListOf<FoodTag>()
            if (bit(1uL, i)) foods.add(FoodTag.DAIRY)
            if (bit(2uL, i)) foods.add(FoodTag.GLUTEN_WHEAT)
            if (bit(3uL, i)) foods.add(FoodTag.CAFFEINE)
            if (foods.isEmpty()) foods = mutableListOf(FoodTag.LARGE_MEAL) // keep the day a logged food day

            val symptoms = mutableListOf<SymptomPoint>()
            if (bit(11uL, i)) symptoms.add(SymptomPoint(SymptomType.PELVIC_PAIN, if (bit(21uL, i)) 5 else 2))
            if (bit(12uL, i)) symptoms.add(SymptomPoint(SymptomType.FATIGUE, if (bit(22uL, i)) 5 else 2))
            if (bit(13uL, i)) symptoms.add(SymptomPoint(SymptomType.HEADACHE, if (bit(23uL, i)) 5 else 2))
            if (symptoms.isEmpty()) symptoms.add(SymptomPoint(SymptomType.OTHER, 1))

            snapshot(
                offset = -i,
                symptoms = symptoms,
                foodTags = foods,
                sleepQuality = if (bit(31uL, i)) SleepQuality.POOR else SleepQuality.GOOD,
                tookPRN = bit(32uL, i),
                stress = if (bit(33uL, i)) 8 else 3,
                mood = if (bit(34uL, i)) 1 else 4,
            )
        }
    }

    /** A SUPPRESSED user with a real non-cyclic co-occurrence: suppressed days where ~half log dairy + high
     *  pelvicPain severity and ~half log caffeine + low severity. `CyclePhaseMapper` yields NO windows (all
     *  suppressed) so no `factor.cyclePhase.*` pair is tested; non-phase correlations MAY still surface. */
    fun suppressedUserCorrelationFixture(monthsBack: Int = 2): List<DaySnapshot> {
        val totalDays = monthsBack * 30
        return (0 until totalDays).map { i ->
            val hasDairy = (i % 2 == 0)
            val severity = if (hasDairy) 7 else 2
            snapshot(
                offset = -i,
                symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, severity)),
                foodTags = if (hasDairy) listOf(FoodTag.DAIRY) else listOf(FoodTag.CAFFEINE),
                suppressed = true,
            )
        }
    }

    /** TWO different factors both correlating with the SAME `bleeding` target, so the per-target collapse
     *  MUST fold them into ONE bleeding recommendation. 20 days dairy + poor sleep + bleeding; 20 days
     *  caffeine + good sleep + no bleeding. Both `food.dairy` and `sleep.poor` vs the SAME bleeding target. */
    fun twoFactorsSameBleedingTargetFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 20) {
            days.add(
                snapshot(
                    offset = -i,
                    flow = FlowIntensity.MEDIUM,
                    foodTags = listOf(FoodTag.DAIRY),
                    sleepQuality = SleepQuality.POOR,
                ),
            )
        }
        for (i in 20 until 40) {
            days.add(
                snapshot(
                    offset = -i,
                    foodTags = listOf(FoodTag.CAFFEINE),
                    sleepQuality = SleepQuality.GOOD,
                ),
            )
        }
        return days
    }

    /** A SHORT but REGULAR cycle whose estimated phase windows OVERLAP. Starts at offsets -48, -32, -16, 0
     *  → gaps 16/16/16 (spread 0). With median 16 and a 5-day bleed, the menstrual and ovulatory windows
     *  overlap → the mapper MUST yield ZERO `.cyclePhase` insights (overlap guard). */
    fun shortOverlappingCycleFixture(): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()
        for (start in listOf(-48, -32, -16, 0)) {
            for (d in 0..4) days.add(snapshot(offset = start + d, flow = FlowIntensity.MEDIUM))
        }
        for (off in 0..4) {
            days.add(snapshot(offset = off, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5))))
        }
        return days
    }

    // MARK: Phase-12 WR-03 — cycle-phase proximity must count logged-SEVERITY days, not bare presence.

    /** A flare fixture exercising Signal C (cycle-phase proximity). A regular bleeding cadence + a rising
     *  pelvicPain spine over ≥21 logged-severity days. When `extraHigherPhaseDays` is true, adds extra
     *  historical pelvicPain days in the projected windows logged present-WITHOUT-severity (`severity = null`)
     *  — WR-03 makes proximity count only logged-SEVERITY days, so these must be INVISIBLE to Signal C. */
    fun flareProximityFixture(extraHigherPhaseDays: Boolean): List<DaySnapshot> {
        val days = mutableListOf<DaySnapshot>()

        for (start in listOf(-84, -56, -28, 0)) {
            for (d in 0..2) days.add(snapshot(offset = start + d, flow = FlowIntensity.MEDIUM))
        }

        for (i in 0..11) { // recent rising spine — high severity
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7))))
        }
        for (i in 12..35) { // prior calmer baseline — lower severity
            days.add(snapshot(offset = -i, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))))
        }

        if (extraHigherPhaseDays) {
            for (off in 5..25) {
                days.add(snapshot(offset = off, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, null))))
            }
        }

        return days
    }
}
