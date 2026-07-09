// EndoInsights (:insights) — the CorrelationEngine honesty spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/CorrelationEngineTests.swift` (12 cases).
// These assert the `.correlation` producer's HONESTY GATES — suppression IS the deliverable. A strong,
// well-sampled association surfaces with an honest effect-size headline + non-causal copy; an under-sampled
// (n=9), a spurious (near-zero δ), and a multiple-comparison flood (K≈100+ pure noise) each surface ZERO;
// a suppressed user has NO cycle-phase correlation (D-04 by construction); output is deterministic. Per
// Pattern 2 `today` is an injected `LocalDate` (the Swift `Calendar` is dropped).
//
// Two ports differ from Swift by NECESSITY, not choice (both land fully in 17-08):
//   • Non-causal copy is checked with an inline banned-substring list (mirrors `CyclePhaseMapperTests`) —
//     `ForbiddenTerms` + `ClaimsPolicyDriftTests` are ported in 17-08.
//   • `duplicateDay_*` exercises the DIRECT `CorrelationEngine.insights` path only — the `InsightsEngine`
//     public-pipeline half is added in 17-08 when that producer lands.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorrelationEngineTests {

    private fun correlations(snapshots: List<DaySnapshot>, todayOffset: Int = 0): List<Insight> =
        CorrelationEngine.insights(from = snapshots, asOf = Fixtures.day(todayOffset))

    /** Causal / medical-purpose substrings that must NEVER appear in an emitted `.correlation` copy. Mirrors
     *  the `CyclePhaseMapperTests` inline gate (the durable `ForbiddenTerms` scan lands in 17-08). */
    private val bannedCausalTerms = listOf(
        "trigger", "causes", " cause", "will ", "because", "due to", "leads to",
        "linked to", "worsen", "improve", "reduce", "relieve", "aggravate",
        "treat", "cure", "prevent", "diagnose", "detect",
    )

    private fun assertNonCausal(insight: Insight) {
        val text = (insight.headline + " " + insight.detail).lowercase()
        for (word in bannedCausalTerms) {
            assertFalse(text.contains(word), "Causal word '$word' in: ${insight.headline} / ${insight.detail}")
        }
    }

    // MARK: Task 1 — named threshold constants are the ONLY judgement calls; assert their locked values.

    @Test
    fun factorThresholds_areLockedConstants() {
        assertEquals(7, Factor.highStressThreshold, "High-stress threshold is locked at 7 (07-RESEARCH A2).")
        assertEquals(2, Factor.lowMoodThreshold, "Low-mood threshold is locked at 2 (07-RESEARCH A2).")
        assertEquals(3, Factor.perGroupMinimum, "Per-group minimum is locked at 3 (07-RESEARCH A5).")
    }

    // MARK: Task 1 — three-state binarization spot-checks (null is NEVER coerced to 0).

    @Test
    fun stressBinarization_threeState() {
        // A day that logged a symptom but NOT stress → stress is unobserved (excluded from both groups).
        val unobserved = Fixtures.snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3)), stress = null)
        assertEquals(FactorState.UNOBSERVED, Factor.stateOf(Factor.StressHigh, unobserved))

        // stress == 7 → high-stress PRESENT (>= threshold).
        val present = Fixtures.snapshot(offset = -1, stress = 7)
        assertEquals(FactorState.PRESENT, Factor.stateOf(Factor.StressHigh, present))

        // stress == 6 → ABSENT (below threshold, but observed).
        val absent = Fixtures.snapshot(offset = -2, stress = 6)
        assertEquals(FactorState.ABSENT, Factor.stateOf(Factor.StressHigh, absent))
    }

    @Test
    fun sleepAndMoodBinarization_threeState() {
        val poorSleep = Fixtures.snapshot(offset = 0, sleepQuality = SleepQuality.POOR)
        assertEquals(FactorState.PRESENT, Factor.stateOf(Factor.SleepPoor, poorSleep))
        val goodSleep = Fixtures.snapshot(offset = -1, sleepQuality = SleepQuality.GOOD)
        assertEquals(FactorState.ABSENT, Factor.stateOf(Factor.SleepPoor, goodSleep))
        val noSleep = Fixtures.snapshot(offset = -2, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 4)))
        assertEquals(FactorState.UNOBSERVED, Factor.stateOf(Factor.SleepPoor, noSleep))

        val lowMood = Fixtures.snapshot(offset = -3, mood = 2)
        assertEquals(FactorState.PRESENT, Factor.stateOf(Factor.MoodLow, lowMood))
        val okMood = Fixtures.snapshot(offset = -4, mood = 3)
        assertEquals(FactorState.ABSENT, Factor.stateOf(Factor.MoodLow, okMood))
        val noMood = Fixtures.snapshot(offset = -5, stress = 4)
        assertEquals(FactorState.UNOBSERVED, Factor.stateOf(Factor.MoodLow, noMood))
    }

    @Test
    fun foodBinarization_emptyOnLoggedDayIsAbsent_trulyEmptyIsUnobserved() {
        // dairy present.
        val dairy = Fixtures.snapshot(offset = 0, foodTags = listOf(FoodTag.DAIRY))
        assertEquals(FactorState.PRESENT, Factor.stateOf(Factor.Food(FoodTag.DAIRY), dairy))
        // a day with SOME log (a symptom) but no dairy → absent (A7), not unobserved.
        val loggedNoDairy = Fixtures.snapshot(offset = -1, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 4)))
        assertEquals(FactorState.ABSENT, Factor.stateOf(Factor.Food(FoodTag.DAIRY), loggedNoDairy))
        // a truly empty day → unobserved.
        val empty = Fixtures.snapshot(offset = -2)
        assertEquals(FactorState.UNOBSERVED, Factor.stateOf(Factor.Food(FoodTag.DAIRY), empty))
    }

    // MARK: Task 3 — strong association is SURFACED (effect + confidence + non-causal copy).

    @Test
    fun strongAssociation_isSurfaced() {
        val insights = correlations(Fixtures.strongAssociationFixture())
        val dairyPain = insights.filter { it.signalKey == "correlation.food.dairy.symptom.pelvicPain" }
        assertEquals(1, dairyPain.size, "Exactly one dairy↔pelvic-pain correlation should surface.")
        val insight = dairyPain.first()
        assertEquals(InsightKind.CORRELATION, insight.kind)
        assertEquals(TrendWindow.ALL_TIME, insight.window)
        assertTrue(insight.confidence >= InsightConfidence.MEDIUM, "A large effect over n=40 should land at MEDIUM or HIGH.")
        assertTrue(insight.sampleSize >= 20, "sampleSize = pairedDays should be ≥ 20.")
        assertTrue(insight.points.isEmpty())
        // Non-causal copy enforcement: no forbidden causal term in headline or detail.
        assertNonCausal(insight)
        // No emitted insight is below the floor (the engine only emits confidence >= LOW).
        for (i in insights) assertTrue(i.confidence >= InsightConfidence.LOW)
    }

    // MARK: Task 3 — under-sampled (n=9) is WITHHELD (engine surfaces zero).

    @Test
    fun underSampled_isWithheld() {
        val insights = correlations(Fixtures.underSampledAssociationFixture())
        assertTrue(insights.isEmpty(), "An n=9 association is below the sample floor → ZERO correlations.")
    }

    // MARK: Task 3 — spurious (near-zero δ) is WITHHELD.

    @Test
    fun spurious_isWithheld() {
        val insights = correlations(Fixtures.spuriousAssociationFixture())
        assertTrue(insights.isEmpty(), "An independent (spurious) pair has near-zero effect → ZERO correlations.")
    }

    // MARK: Task 3 — the multiple-comparison flood surfaces NOTHING (the load-bearing test, BH q=0.10).

    @Test
    fun multipleComparisonFlood_surfacesNothing() {
        val insights = correlations(Fixtures.multipleComparisonFloodFixture())
        assertTrue(insights.isEmpty(), "K≈100+ pure-noise pairs must all be crushed by BH-FDR → ZERO correlations.")
    }

    // MARK: Task 3 — a suppressed user has NO cycle-phase correlation (D-04 by construction).

    @Test
    fun suppressedUser_noCyclePhaseCorrelation() {
        val insights = correlations(Fixtures.suppressedUserCorrelationFixture())
        for (i in insights) {
            assertFalse(
                i.signalKey.contains("cyclePhase"),
                "A suppressed user must yield NO cycle-phase correlation (D-04): ${i.signalKey}",
            )
        }
    }

    // MARK: CR-01 — a duplicate calendar-day input must NOT crash the public pipeline.

    @Test
    fun duplicateDay_doesNotCrash_andProducesSensibleResult() {
        // Two snapshots on the SAME calendar day (offset 0) — the engine must collapse to one day (last-wins)
        // and degrade gracefully instead of crashing. Reaching the assertion at all proves no trap.
        val snapshots = listOf(
            Fixtures.snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3)), foodTags = listOf(FoodTag.DAIRY)),
            Fixtures.snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 7)), foodTags = listOf(FoodTag.CAFFEINE)),
            Fixtures.snapshot(offset = -1, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 4)), stress = 5),
        )
        // Direct engine entry (CorrelationEngine.insights) — must not trap. The InsightsEngine public-pipeline
        // half of the Swift test is added in 17-08 when that producer is ported.
        val direct = correlations(snapshots)
        assertTrue(direct.isEmpty(), "Three days (one a duplicate) is far below the floor → ZERO correlations, no crash.")
    }

    // MARK: WR-02 — the severity arm counts DAYS, not severity observations (one obs per day).

    @Test
    fun severityArm_countsDaysNotObservations() {
        // A strong dairy↔pelvicPain association where EVERY dairy day carries TWO pelvicPain SymptomPoints.
        // Post-fix, each day reduces to one value (max), so sampleSize == day count.
        val days = mutableListOf<DaySnapshot>()
        for (i in 0 until 20) { // dairy days, DUPLICATE same-day pelvicPain points (severities 6 and 7)
            days.add(
                Fixtures.snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 6), SymptomPoint(SymptomType.PELVIC_PAIN, 7)),
                    foodTags = listOf(FoodTag.DAIRY),
                ),
            )
        }
        for (i in 20 until 40) { // non-dairy days, single low-severity point
            days.add(
                Fixtures.snapshot(
                    offset = -i,
                    symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 2)),
                    foodTags = listOf(FoodTag.CAFFEINE),
                ),
            )
        }
        val insights = correlations(days)
        val dairyPain = insights.firstOrNull { it.signalKey == "correlation.food.dairy.symptom.pelvicPain" }
        assertNotNull(dairyPain, "The strong association should still surface.")
        // 40 observed days → sampleSize must be EXACTLY 40 (days), not 60 (20×2 + 20 observations).
        assertEquals(
            40, dairyPain!!.sampleSize,
            "sampleSize must count the 40 observed DAYS, not the 60 severity observations (WR-02): got ${dairyPain.sampleSize}.",
        )
    }

    // MARK: Task 3 — determinism: same fixture + same injected today → exactly equal [Insight].

    @Test
    fun output_isDeterministic() {
        val a = correlations(Fixtures.strongAssociationFixture())
        val b = correlations(Fixtures.strongAssociationFixture())
        assertEquals(a, b)
        // Also stable for the flood (the empty case).
        assertEquals(
            correlations(Fixtures.multipleComparisonFloodFixture()),
            correlations(Fixtures.multipleComparisonFloodFixture()),
        )
    }
}
