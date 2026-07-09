// EndoInsights (:insights) — the HonestyGate acceptance spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` (16 cases). These
// assert the single choke-point's HONESTY behavior — the ethics ARE the deliverable. The gate WITHHOLDS
// below-floor insights ENTIRELY (never caveats-and-shows), KEEPS the carved-out "not enough data yet"
// signal, EXCLUDES dismissed signalKeys, and — the NON-NEGOTIABLE acceptance — emits ZERO forbidden
// terms across EVERY fixture's insights (scanned through the public InsightsEngine, plus a direct
// recommendation-engine scan).
//
// DURABILITY NOTE: the gate's production forbidden-term path makes a violation LOUD (error) AND drops it;
// `applyReleaseScan` drops silently so a test can prove the release fail-closed drop without throwing.
// The `ForbiddenTerms` scan here — not the runtime — is the durable guarantee; every future insight kind
// MUST add its emitted copy to `allEmittedInsights_haveZeroForbiddenTerms`.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HonestyGateTests {

    // A descriptive, non-causal headline/detail used in synthetic gate-only candidates. `sampleSize`
    // defaults to 5 (>= minSample(TREND)/CYCLE_PHASE) so the candidate clears the min-sample backstop
    // unless a test deliberately under-samples it.
    private fun candidate(
        kind: InsightKind,
        signalKey: String,
        confidence: InsightConfidence,
        sampleSize: Int = 5,
        headline: String = "Pelvic pain logged on 3 of 5 days you tracked anything",
        detail: String = "You logged pelvic pain on 3 of the 5 days you tracked anything in the last 7 days.",
    ): Insight = Insight.derived(
        kind = kind, window = TrendWindow.LAST7, signalKey = signalKey,
        headline = headline, detail = detail, confidence = confidence, points = emptyList(), sampleSize = sampleSize,
    )

    // MARK: (a) Below-floor candidate WITHHELD entirely (never caveated-and-shown).

    @Test
    fun belowFloorCandidate_isWithheldEntirely() {
        val belowFloor = candidate(kind = InsightKind.TREND, signalKey = "symptom.pelvicPain.frequency", confidence = InsightConfidence.NONE)
        val gated = HonestyGate.apply(listOf(belowFloor), dismissed = emptySet())
        assertTrue(gated.isEmpty(), "A below-floor (NONE) insight must be WITHHELD entirely (D-07).")
    }

    @Test
    fun atOrAboveFloorCandidate_isKept() {
        val atFloor = candidate(kind = InsightKind.TREND, signalKey = "symptom.pelvicPain.frequency", confidence = InsightConfidence.LOW)
        val gated = HonestyGate.apply(listOf(atFloor), dismissed = emptySet())
        assertEquals(1, gated.size, "An at-floor (LOW) insight must survive the confidence floor.")
    }

    // MARK: (b) The typed `isInsufficiencyNotice` "not enough data yet" carve-out is KEPT below the floor.

    @Test
    fun insufficientCarveOut_isKept_despiteNoneConfidence() {
        // Mirrors TrendsEngine.notEnoughData: typed `isInsufficiencyNotice == true`, confidence NONE,
        // sampleSize below every floor — yet the typed carve-out keeps it (WR-01).
        val carveOut = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST7, signalKey = "symptom.fatigue.frequency.insufficient",
            headline = "Fatigue: not enough data yet",
            detail = "Not enough data yet to describe fatigue in the last 7 days — keep logging.",
            confidence = InsightConfidence.NONE, points = emptyList(), sampleSize = 1, isInsufficiencyNotice = true,
        )
        val gated = HonestyGate.apply(listOf(carveOut), dismissed = emptySet())
        assertEquals(1, gated.size, "The typed insufficiency carve-out must be KEPT despite NONE confidence and sampleSize 1.")
        assertTrue(gated.first().isInsufficiencyNotice)
    }

    // WR-01: the carve-out is keyed on the TYPED marker, NOT the signalKey suffix. A forged signalKey
    // ending `.insufficient` WITHOUT the marker does NOT bypass the confidence floor.
    @Test
    fun forgedInsufficientSuffix_withoutTypedMarker_doesNotBypassFloor() {
        val forged = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST7, signalKey = "factor.bogus.frequency.insufficient",
            headline = "Bogus signal", detail = "A low-confidence signal whose key merely ends insufficient.",
            confidence = InsightConfidence.NONE, points = emptyList(), sampleSize = 5, isInsufficiencyNotice = false,
        )
        val gated = HonestyGate.apply(listOf(forged), dismissed = emptySet())
        assertTrue(gated.isEmpty(), "A signalKey ending '.insufficient' must NOT bypass the floor without the typed marker (WR-01).")
    }

    // MARK: (c) Dismissal-exclusion by signalKey (D-11).

    @Test
    fun dismissedSignalKey_isExcluded() {
        val a = candidate(kind = InsightKind.TREND, signalKey = "symptom.pelvicPain.frequency", confidence = InsightConfidence.LOW)
        val b = candidate(kind = InsightKind.TREND, signalKey = "symptom.fatigue.frequency", confidence = InsightConfidence.LOW)
        val gated = HonestyGate.apply(listOf(a, b), dismissed = setOf("symptom.pelvicPain.frequency"))
        assertEquals(1, gated.size, "A dismissed signalKey must be excluded at the choke-point (D-11).")
        assertEquals("symptom.fatigue.frequency", gated.first().signalKey)
        assertFalse(gated.any { it.signalKey == "symptom.pelvicPain.frequency" })
    }

    // MARK: Order-preserving for survivors.

    @Test
    fun gate_isOrderPreserving_forSurvivors() {
        val a = candidate(kind = InsightKind.TREND, signalKey = "symptom.a.frequency", confidence = InsightConfidence.LOW)
        val b = candidate(kind = InsightKind.TREND, signalKey = "symptom.b.frequency", confidence = InsightConfidence.LOW)
        val c = candidate(kind = InsightKind.TREND, signalKey = "symptom.c.frequency", confidence = InsightConfidence.LOW)
        val gated = HonestyGate.apply(listOf(a, b, c), dismissed = emptySet())
        assertEquals(listOf("symptom.a.frequency", "symptom.b.frequency", "symptom.c.frequency"), gated.map { it.signalKey })
    }

    // MARK: (b') CR-01/WR-02 — the min-sample backstop is LIVE: under-sampled candidates are WITHHELD.

    @Test
    fun underSampledCyclePhaseAssociation_isWithheldByGate() {
        // A single-day (n=1) cyclePhase co-occurrence at sufficient confidence — sampleSize 1 <
        // minSample(CYCLE_PHASE) == 2. The gate MUST withhold it (the #1 under-sampled bypass, CR-01).
        val n1 = candidate(
            kind = InsightKind.CYCLE_PHASE, signalKey = "cyclePhase.luteal.symptom.pelvicPain",
            confidence = InsightConfidence.MEDIUM, sampleSize = 1,
            headline = "Pelvic pain tended to co-occur with your luteal phase",
            detail = "Pelvic pain was logged on 1 of 1 of your estimated luteal-phase days — a descriptive co-occurrence only.",
        )
        val gated = HonestyGate.apply(listOf(n1), dismissed = emptySet())
        assertTrue(gated.isEmpty(), "An n=1 cyclePhase association (sampleSize < minSample(CYCLE_PHASE)) must be WITHHELD (CR-01).")
    }

    @Test
    fun wellSampledCyclePhaseAssociation_survivesGate() {
        // The same association logged on >= 2 in-phase days at sufficient confidence survives.
        val n2 = candidate(
            kind = InsightKind.CYCLE_PHASE, signalKey = "cyclePhase.luteal.symptom.pelvicPain",
            confidence = InsightConfidence.MEDIUM, sampleSize = 2,
            headline = "Pelvic pain tended to co-occur with your luteal phase",
            detail = "Pelvic pain was logged on 2 of 4 of your estimated luteal-phase days — a descriptive co-occurrence only.",
        )
        val gated = HonestyGate.apply(listOf(n2), dismissed = emptySet())
        assertEquals(1, gated.size, "A cyclePhase association at sampleSize >= minSample(CYCLE_PHASE) must survive.")
    }

    @Test
    fun validTrend_atMinSample_isNotDroppedByGate() {
        // No-regression: a trend at sampleSize == minSample(TREND) == 3 must NOT be dropped.
        val trend = candidate(kind = InsightKind.TREND, signalKey = "symptom.pelvicPain.frequency", confidence = InsightConfidence.LOW, sampleSize = 3)
        val gated = HonestyGate.apply(listOf(trend), dismissed = emptySet())
        assertEquals(1, gated.size, "A trend at sampleSize == minSample(TREND) must survive (no-regression, Pitfall 1).")
    }

    // MARK: (b''') Phase 11 — minSample(RECOMMENDATION) inherits the correlation floor (Pitfall 3).

    @Test
    fun minSampleRecommendation_equalsCorrelationFloor() {
        assertEquals(
            HonestyGate.minSample(InsightKind.CORRELATION),
            HonestyGate.minSample(InsightKind.RECOMMENDATION),
            "minSample(RECOMMENDATION) must equal minSample(CORRELATION) (D-02 — a recommendation re-frames a gated correlation).",
        )
        assertEquals(
            10,
            HonestyGate.minSample(InsightKind.RECOMMENDATION),
            "minSample(RECOMMENDATION) must be 10 — a 14 floor silently drops valid n=10-13 recommendations (Pitfall 3).",
        )
    }

    @Test
    fun recommendationFromN10Correlation_survivesGate() {
        // A recommendation re-framed from an n=10 correlation (sampleSize == minSample(CORRELATION) == 10),
        // clean non-prescriptive copy, MEDIUM confidence -> MUST survive the gate (Pitfall 3 — not dropped).
        val n10 = candidate(
            kind = InsightKind.RECOMMENDATION, signalKey = "recommendation.dairy.symptom.pelvicPain",
            confidence = InsightConfidence.MEDIUM, sampleSize = 10,
            headline = "Your pelvic pain tends to be more intense on days with dairy — something you might explore.",
            detail = "This is a pattern in your own data, not advice — something to discuss with your clinician.",
        )
        val gated = HonestyGate.apply(listOf(n10), dismissed = emptySet())
        assertEquals(1, gated.size, "A recommendation at sampleSize == 10 == minSample(RECOMMENDATION) must survive the gate (Pitfall 3).")
    }

    // MARK: (d) MANDATORY — ZERO forbidden terms across EVERY fixture's emitted insights (D-08).

    @Test
    fun allEmittedInsights_haveZeroForbiddenTerms() {
        // Every fixture stream the package exercises. As future kinds land, ADD their fixtures.
        val streams: List<Pair<String, List<DaySnapshot>>> = listOf(
            "risingPelvicPain" to Fixtures.risingPelvicPainFixture(),
            "subThreshold" to Fixtures.subThresholdFixture(),
            "loggedZeroStress" to Fixtures.loggedZeroStressFixture(),
            "unloggedStress" to Fixtures.unloggedStressFixture(),
            "bleeding" to Fixtures.bleedingFixture(),
            "suppressedNoBleeding" to Fixtures.suppressedNoBleedingFixture(),
            "regularCycle" to Fixtures.regularCycleFixture(),
            "irregularCycle" to Fixtures.irregularCycleFixture(),
            "underSampledCycle" to Fixtures.underSampledCycleFixture(),
            // Phase 7 (.correlation).
            "strongAssociation" to Fixtures.strongAssociationFixture(),
            "multipleComparisonFlood" to Fixtures.multipleComparisonFloodFixture(),
            "suppressedUserCorrelation" to Fixtures.suppressedUserCorrelationFixture(),
            // Phase 11 (.recommendation).
            "beneficialAssociation" to Fixtures.beneficialAssociationFixture(),
            // Phase 12 (.flare) — the flare streams exercise the FULL forbidden union (causal + imperative +
            // alarmist) through the public InsightsEngine.
            "risingFlare" to Fixtures.risingFlareFixture(),
            "risingButUnderEvidence" to Fixtures.risingButUnderEvidenceFixture(),
            "flatNoRise" to Fixtures.flatNoRiseFixture(),
        )

        for ((name, snapshots) in streams) {
            val insights = InsightsEngine.insights(from = snapshots, asOf = Fixtures.day(0), dismissed = emptySet())
            for (insight in insights) {
                assertNull(
                    ForbiddenTerms.violation(insight.headline),
                    "Forbidden term in headline ($name): ${insight.headline}",
                )
                assertNull(
                    ForbiddenTerms.violation(insight.detail),
                    "Forbidden term in detail ($name): ${insight.detail}",
                )
            }
        }
    }

    // MARK: (d'') Phase 11 — the `.recommendation` emitted copy is clean DIRECTLY out of the producer.

    @Test
    fun recommendationEngine_emitsZeroForbiddenTerms() {
        val streams: List<Pair<String, List<DaySnapshot>>> = listOf(
            "beneficialAssociation" to Fixtures.beneficialAssociationFixture(),
            "strongAssociation" to Fixtures.strongAssociationFixture(), // aggravating
            "suppressedUserCorrelation" to Fixtures.suppressedUserCorrelationFixture(),
            "multipleComparisonFlood" to Fixtures.multipleComparisonFloodFixture(),
        )
        for ((name, snapshots) in streams) {
            val recs = RecommendationsEngine.recommendations(from = snapshots, asOf = Fixtures.day(0))
            for (rec in recs) {
                assertNull(
                    ForbiddenTerms.violation(rec.headline),
                    "Forbidden term in recommendation headline ($name): ${rec.headline}",
                )
                assertNull(
                    ForbiddenTerms.violation(rec.detail),
                    "Forbidden term in recommendation detail ($name): ${rec.detail}",
                )
            }
        }
    }

    // MARK: (d') CR-02 — fail-closed: a runtime-interpolated forbidden term is WITHHELD in the gated output.

    @Test
    fun runtimeForbiddenTermInDetail_isWithheld_inGatedOutput() {
        // A producer copy bug a STATIC fixture cannot cover: a forbidden causal term lands in a
        // runtime-interpolated detail. The gate must WITHHOLD it in release (fail-closed), not surface it.
        // `applyReleaseScan` verifies the RELEASE drop behavior (the durable guarantee) without throwing.
        val causal = Insight.derived(
            kind = InsightKind.CYCLE_PHASE, window = TrendWindow.ALL_TIME,
            signalKey = "cyclePhase.luteal.symptom.pelvicPain",
            headline = "Pelvic pain tended to co-occur with your luteal phase",
            detail = "Your luteal phase triggers pelvic pain.", // runtime-interpolated causal claim
            confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 5,
        )
        // Sanity: this copy genuinely violates the policy.
        assertNotNull(ForbiddenTerms.violation(causal.detail))
        val gated = HonestyGate.applyReleaseScan(listOf(causal), dismissed = emptySet())
        assertTrue(gated.isEmpty(), "A causal-term insight must be WITHHELD in the gated output, even in release (CR-02).")
    }

    // MARK: ForbiddenTerms unit behavior.

    @Test
    fun forbiddenTerms_flagsCausal_passesDescriptive() {
        assertNotNull(ForbiddenTerms.violation("this may cause pain"))
        assertNull(ForbiddenTerms.violation("tended to co-occur with"))
    }

    // WR-03: the expanded list catches causal inflections/connectives it previously false-negated on.
    @Test
    fun forbiddenTerms_catchesExpandedCausalInflections() {
        assertNotNull(ForbiddenTerms.violation("stress worsens your luteal phase")) // was a false-negative
        assertNotNull(ForbiddenTerms.violation("this caused the flare")) // "caused" contains "cause"
        assertNotNull(ForbiddenTerms.violation("this is triggering pain")) // "triggering" contains "trigger"
        assertNotNull(ForbiddenTerms.violation("exercise improves your mood"))
        assertNotNull(ForbiddenTerms.violation("rest reduces pelvic pain"))
        assertNotNull(ForbiddenTerms.violation("poor sleep linked to fatigue"))
        assertNotNull(ForbiddenTerms.violation("leading to more flares"))
        // A descriptive co-occurrence (the producer's actual posture) still passes clean.
        assertNull(ForbiddenTerms.violation("Fatigue tended to co-occur with your follicular phase"))
    }
}
