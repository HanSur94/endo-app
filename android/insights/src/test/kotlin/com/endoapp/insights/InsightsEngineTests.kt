// EndoInsights (:insights) — the InsightsEngine composition + no-regression spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` (14 cases).
// `InsightsEngine` is the ONE public producer (D-13). These assert it (a) COMPOSES all five producers,
// (b) does NOT regress the Phase-5 trends — the gate is a SUPERSET filter, so `InsightsEngine.insights
// ⊇ TrendsEngine.trends` over the Phase-5 fixture (Pitfall 1) — and (c) honors dismissal IDEMPOTENTLY.
// `asOf` is injected (no clock); LocalDate is the zone-free day identity (the injected Swift Calendar is
// dropped).
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InsightsEngineTests {

    private fun engine(snapshots: List<DaySnapshot>, dismissed: Set<String> = emptySet()): List<Insight> =
        InsightsEngine.insights(from = snapshots, asOf = Fixtures.day(0), dismissed = dismissed)

    private fun rawTrends(snapshots: List<DaySnapshot>): List<Insight> =
        TrendsEngine.trends(from = snapshots, asOf = Fixtures.day(0))

    // MARK: (a) Composition — both `.trend` and `.cyclePhase` kinds surface over a regular cycle.

    @Test
    fun composition_emitsBothTrendAndCyclePhaseKinds() {
        val kinds = engine(Fixtures.regularCycleFixture()).map { it.kind }.toSet()
        assertTrue(kinds.contains(InsightKind.TREND), "InsightsEngine must compose TrendsEngine trends.")
        assertTrue(kinds.contains(InsightKind.CYCLE_PHASE), "InsightsEngine must compose CyclePhaseMapper associations.")
    }

    // MARK: (b) NO-REGRESSION — the gate is a SUPERSET filter; every raw trend survives (Pitfall 1).

    @Test
    fun noRegression_insightsAreSupersetOfRawTrends() {
        val fixture = Fixtures.risingPelvicPainFixture()
        val gatedIDs = engine(fixture).map { it.id }.toSet()
        val rawIDs = rawTrends(fixture).map { it.id }.toSet()

        assertFalse(rawIDs.isEmpty(), "The Phase-5 fixture must produce raw trends (sanity).")
        val dropped = rawIDs.subtract(gatedIDs)
        assertTrue(dropped.isEmpty(), "Phase-5 regression: ${dropped.size} raw trend(s) dropped by the gate.")
        assertTrue(gatedIDs.containsAll(rawIDs), "InsightsEngine.insights must be a superset of TrendsEngine.trends.")
    }

    // MARK: (a') Phase-7 composition — a strong association surfaces a `.correlation` through the gate.

    @Test
    fun composition_surfacesCorrelation_overStrongFixture() {
        val kinds = engine(Fixtures.strongAssociationFixture()).map { it.kind }.toSet()
        assertTrue(
            kinds.contains(InsightKind.CORRELATION),
            "InsightsEngine must compose CorrelationEngine — a strong association surfaces a .correlation.",
        )
    }

    // MARK: (b') Withholding — the gate drops under-sampled / spurious / flood correlations (the value, D-18).

    @Test
    fun gate_withholdsUnderSampledCorrelation() {
        val insights = engine(Fixtures.underSampledAssociationFixture()) // n=9 < minSample(CORRELATION)==10
        assertFalse(
            insights.any { it.kind == InsightKind.CORRELATION },
            "An under-sampled (n=9) correlation must be WITHHELD through the public engine.",
        )
    }

    @Test
    fun gate_withholdsSpuriousCorrelation() {
        val insights = engine(Fixtures.spuriousAssociationFixture())
        assertFalse(
            insights.any { it.kind == InsightKind.CORRELATION },
            "A spurious (independent) correlation must be WITHHELD through the public engine.",
        )
    }

    @Test
    fun gate_withholdsMultipleComparisonFlood() {
        // The load-bearing acceptance (SC3): K≈100+ pure-noise pairs surface ZERO correlations.
        val insights = engine(Fixtures.multipleComparisonFloodFixture())
        assertFalse(
            insights.any { it.kind == InsightKind.CORRELATION },
            "A multiple-comparison flood of pure noise must surface ZERO correlations (SC3).",
        )
    }

    // MARK: (c') Suppressed user — no cycle-phase correlation ever reaches the public output (D-04).

    @Test
    fun suppressedUser_noCyclePhaseCorrelation_throughGate() {
        val insights = engine(Fixtures.suppressedUserCorrelationFixture())
        assertFalse(
            insights.any { it.signalKey.contains("cyclePhase") },
            "A suppressed user must yield NO cyclePhase correlation through the gate (D-04 by construction).",
        )
    }

    // MARK: (a'') Phase-11 composition — a strong association surfaces a `.recommendation` through the gate.

    @Test
    fun composition_surfacesRecommendation_overStrongFixture() {
        val kinds = engine(Fixtures.strongAssociationFixture()).map { it.kind }.toSet()
        assertTrue(
            kinds.contains(InsightKind.RECOMMENDATION),
            "InsightsEngine must compose RecommendationsEngine — a strong association surfaces a .recommendation.",
        )
    }

    // MARK: (b'') Withholding — suppressed + flood users yield ZERO recommendations through the public engine.

    @Test
    fun gate_withholdsRecommendation_forSuppressedAndFlood() {
        val suppressed = engine(Fixtures.suppressedUserCorrelationFixture())
        assertFalse(
            suppressed.any { it.kind == InsightKind.RECOMMENDATION },
            "A suppressed user must yield ZERO recommendations through the public engine (SC3).",
        )
        val flood = engine(Fixtures.multipleComparisonFloodFixture())
        assertFalse(
            flood.any { it.kind == InsightKind.RECOMMENDATION },
            "A multiple-comparison flood of pure noise must surface ZERO recommendations (SC3).",
        )
    }

    // MARK: (c'') Dismissal-exclusion of a recommendation + idempotence (D-07).

    @Test
    fun dismissal_excludesRecommendation_andStaysExcluded() {
        val fixture = Fixtures.strongAssociationFixture()
        val baseline = engine(fixture)
        val rec = baseline.firstOrNull { it.kind == InsightKind.RECOMMENDATION }
        val target = requireNotNull(rec?.signalKey) { "The strong fixture must surface a recommendation to dismiss." }

        val afterDismiss = engine(fixture, dismissed = setOf(target))
        assertFalse(afterDismiss.any { it.signalKey == target }, "The dismissed recommendation must be excluded.")

        // Idempotence (D-07): a second recompute with the same dismissed set keeps it excluded.
        val afterDismissAgain = engine(fixture, dismissed = setOf(target))
        assertEquals(
            afterDismiss.map { it.id },
            afterDismissAgain.map { it.id },
            "A dismissed recommendation STAYS dismissed across recompute (D-07).",
        )
    }

    // MARK: (a''') Phase-12 composition — a rising-with-≥21-evidence history surfaces ONE `.flare`.

    @Test
    fun composition_surfacesFlare_overRisingFixture() {
        val flares = engine(Fixtures.risingFlareFixture()).filter { it.kind == InsightKind.FLARE && !it.isInsufficiencyNotice }
        assertEquals(
            1,
            flares.size,
            "InsightsEngine must compose FlareEngine — a rising-with-≥21-evidence history surfaces exactly one .flare warning.",
        )
        // Uncertainty-first (D-03): the composed flare warning is bounded at ≤ MEDIUM (never HIGH).
        assertTrue(
            flares.all { it.confidence != InsightConfidence.HIGH },
            "A composed flare warning must be bounded at ≤ MEDIUM (uncertainty-first, D-03).",
        )
    }

    // MARK: (b''') Withholding — suppressed + flat users yield ZERO flare warnings through the public engine.

    @Test
    fun gate_withholdsFlare_forSuppressedAndFlat() {
        val suppressed = engine(Fixtures.suppressedNoBleedingFixture())
        assertFalse(
            suppressed.any { it.kind == InsightKind.FLARE },
            "A suppressed user must yield ZERO flare warnings through the public engine (SC3).",
        )
        val flat = engine(Fixtures.flatNoRiseFixture())
        assertFalse(
            flat.any { it.kind == InsightKind.FLARE && !it.isInsufficiencyNotice },
            "A flat / no-rise history must surface NO non-notice flare warning through the public engine (SC3).",
        )
    }

    // MARK: (c) Dismissal-exclusion + idempotence (D-12: stays dismissed across recomputation).

    @Test
    fun dismissal_excludesExactlyThatSignal_andStaysExcluded() {
        val fixture = Fixtures.regularCycleFixture()
        val baseline = engine(fixture)
        assertFalse(baseline.isEmpty(), "Need at least one emitted insight to dismiss.")

        val target = baseline.first().signalKey
        val afterDismiss = engine(fixture, dismissed = setOf(target))

        // The dismissed signal is absent; everything else is unchanged.
        assertFalse(afterDismiss.any { it.signalKey == target }, "The dismissed signal must be excluded.")
        val expectedRemainder = baseline.filter { it.signalKey != target }.map { it.id }
        assertEquals(expectedRemainder, afterDismiss.map { it.id }, "Only the dismissed signal is removed; order preserved.")

        // Idempotence (D-12): a second recomputation with the same dismissed set yields the same exclusion.
        val afterDismissAgain = engine(fixture, dismissed = setOf(target))
        assertEquals(afterDismiss.map { it.id }, afterDismissAgain.map { it.id }, "A dismissed signal STAYS dismissed (D-12).")
    }

    // MARK: Determinism — same inputs + same injected asOf → exactly equal [Insight].

    @Test
    fun determinism_sameInputs_equalInsights() {
        assertEquals(engine(Fixtures.regularCycleFixture()), engine(Fixtures.regularCycleFixture()))
    }
}
