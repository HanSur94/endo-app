// EndoInsights (:insights) — the INST-03 CorrelationStats proof suite (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/CorrelationStatsTests.swift` (26 cases).
// This is the MATH FOUNDATION the whole correlation engine stands on: every assertion pins a helper
// against a value computed BY HAND (the Swift in-comment derivations carry over verbatim).
//
// TOLERANCE PARITY (AENG-06): the pure integer/double helpers (Cliff's δ, meanDiff, RD/RR, BH index sets)
// keep the Swift suite's EXACT / 1e-12 assertions. The p-values that flow through the hand-rolled
// lgamma/erf (Fisher, Mann-Whitney, normalCDF) keep the Swift suite's OWN tolerances (Fisher 1e-3,
// Mann-Whitney/normalCDF 1e-6, symmetry 1e-9) — those tolerances already absorb the libm↔Lanczos/Chebyshev
// approximation gap, which the golden gate (SpecialFunctionsGoldenTests) proves is ≤ 1e-9 relative.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CorrelationStatsTests {

    // SSOT note: Swift sources `minN` from `HonestyGate.minSample(for: .correlation)` (== 10, the single
    // correlation floor). HonestyGate is not ported yet (a later 17-x plan); the tests pin the same literal
    // 10 the gate resolves to, and the engine wiring will pass `HonestyGate.minSample(.correlation)` once it
    // lands — keeping exactly one source of truth then.
    private val minN = 10

    // MARK: - Cliff's delta — (#{w>o} − #{w<o}) / (m·n), range [-1,+1], ties contribute 0.

    @Test
    fun cliffsDelta_allPresentGreater_isPlusOne() {
        // present all 7, absent all 2 → every pair w>o → greater=9, less=0 → 9/9 = +1.
        assertEquals(1.0, CorrelationStats.cliffsDelta(present = listOf(7.0, 7.0, 7.0), absent = listOf(2.0, 2.0, 2.0)))
    }

    @Test
    fun cliffsDelta_allPresentLess_isMinusOne() {
        // present all 2, absent all 7 → every pair w<o → greater=0, less=9 → −9/9 = −1.
        assertEquals(-1.0, CorrelationStats.cliffsDelta(present = listOf(2.0, 2.0, 2.0), absent = listOf(7.0, 7.0, 7.0)))
    }

    @Test
    fun cliffsDelta_allTies_isZero() {
        // present [5,5], absent [5,5] → all 4 pairs tie → greater=0, less=0 → 0/4 = 0.
        assertEquals(0.0, CorrelationStats.cliffsDelta(present = listOf(5.0, 5.0), absent = listOf(5.0, 5.0)))
    }

    @Test
    fun cliffsDelta_mixed_handComputed() {
        // present [3,4], absent [1,5]. Pairs: (3,1)>, (3,5)<, (4,1)>, (4,5)< → greater=2, less=2 → 0/4 = 0.
        assertEquals(0.0, CorrelationStats.cliffsDelta(present = listOf(3.0, 4.0), absent = listOf(1.0, 5.0)))
        // present [3,4], absent [1,2]. All 4 pairs w>o → greater=4 → +1.
        assertEquals(1.0, CorrelationStats.cliffsDelta(present = listOf(3.0, 4.0), absent = listOf(1.0, 2.0)))
        // present [4,6], absent [5,5]. (4,5)<,(4,5)<,(6,5)>,(6,5)> → greater=2,less=2 → 0.
        assertEquals(0.0, CorrelationStats.cliffsDelta(present = listOf(4.0, 6.0), absent = listOf(5.0, 5.0)))
        // present [6,6,6], absent [4,5,7]: greater=6,less=3 → 3/9 = 1/3.
        val d = CorrelationStats.cliffsDelta(present = listOf(6.0, 6.0, 6.0), absent = listOf(4.0, 5.0, 7.0))
        assertNotNull(d)
        assertEquals(1.0 / 3.0, d!!, 1e-12)
    }

    @Test
    fun cliffsDelta_emptyGroup_isNil() {
        assertNull(CorrelationStats.cliffsDelta(present = emptyList(), absent = listOf(1.0, 2.0)))
        assertNull(CorrelationStats.cliffsDelta(present = listOf(1.0, 2.0), absent = emptyList()))
    }

    // MARK: - Mean difference — mean(W) − mean(O); nil on empty (never coerce empty to 0).

    @Test
    fun meanDifference_handComputed() {
        // mean([6,6])=6, mean([2,2])=2 → 4.
        assertEquals(4.0, CorrelationStats.meanDifference(present = listOf(6.0, 6.0), absent = listOf(2.0, 2.0)))
        // mean([1,2,3])=2, mean([4,6])=5 → 2−5 = −3.
        assertEquals(-3.0, CorrelationStats.meanDifference(present = listOf(1.0, 2.0, 3.0), absent = listOf(4.0, 6.0)))
    }

    @Test
    fun meanDifference_emptyGroup_isNil() {
        assertNull(CorrelationStats.meanDifference(present = emptyList(), absent = listOf(2.0)))
        assertNull(CorrelationStats.meanDifference(present = listOf(2.0), absent = emptyList()))
    }

    // MARK: - Risk difference — a/(a+b) − c/(c+d); nil when either arm unobserved.

    @Test
    fun riskDifference_handComputed() {
        // a:8,b:2 → 0.8 ; c:2,d:8 → 0.2 → 0.8−0.2 = 0.6.
        val rd = CorrelationStats.riskDifference(a = 8, b = 2, c = 2, d = 8)
        assertNotNull(rd)
        assertEquals(0.6, rd!!, 1e-12)
    }

    @Test
    fun riskDifference_emptyArm_isNil() {
        // a+b == 0 → factor-present arm unobserved → nil (NOT 0).
        assertNull(CorrelationStats.riskDifference(a = 0, b = 0, c = 2, d = 8))
        // c+d == 0 → factor-absent arm unobserved → nil.
        assertNull(CorrelationStats.riskDifference(a = 8, b = 2, c = 0, d = 0))
    }

    // MARK: - Risk ratio — [a/(a+b)] / [c/(c+d)]; nil when the reference rate is 0.

    @Test
    fun riskRatio_handComputed() {
        // 0.8 / 0.2 = 4.0.
        val rr = CorrelationStats.riskRatio(a = 8, b = 2, c = 2, d = 8)
        assertNotNull(rr)
        assertEquals(4.0, rr!!, 1e-12)
    }

    @Test
    fun riskRatio_zeroReferenceRate_isNil() {
        // c/(c+d) == 0 → division undefined → nil.
        assertNull(CorrelationStats.riskRatio(a = 8, b = 2, c = 0, d = 8))
        // c+d == 0 → reference arm unobserved → nil.
        assertNull(CorrelationStats.riskRatio(a = 8, b = 2, c = 0, d = 0))
    }

    // MARK: - Fisher's exact two-tailed — proves the lgamma binding + the hypergeometric sum.

    @Test
    fun fisherExact_textbookTable_handComputed() {
        // Table a:1,b:9,c:11,d:3 (margins r1=10,r2=14,c1=12,n=24). Two-tailed p ≈ 0.0027594562.
        val p = CorrelationStats.fisherExactTwoTailed(a = 1, b = 9, c = 11, d = 3)
        assertEquals(0.0027594562, p, 1e-3)
    }

    @Test
    fun fisherExact_independentTable_pIsOne() {
        // Symmetric/independent table a:5,b:5,c:5,d:5 → full mass sums to 1.0.
        val p = CorrelationStats.fisherExactTwoTailed(a = 5, b = 5, c = 5, d = 5)
        assertEquals(1.0, p, 1e-3)
    }

    // MARK: - normalCDF — Φ(x) = 0.5·(1 + erf(x/√2)).

    @Test
    fun normalCDF_knownValues() {
        // Φ(0) = 0.5 exactly.
        assertEquals(0.5, CorrelationStats.normalCDF(0.0))
        // Φ(1) = 0.8413447461.
        assertEquals(0.8413447461, CorrelationStats.normalCDF(1.0), 1e-6)
        // Φ(1/√2) ≈ 0.7602499389.
        assertEquals(0.7602499389, CorrelationStats.normalCDF(1.0 / sqrt(2.0)), 1e-6)
    }

    @Test
    fun normalCDF_symmetry() {
        // Φ(−x) + Φ(x) = 1 for all x.
        assertEquals(1.0, CorrelationStats.normalCDF(-1.0) + CorrelationStats.normalCDF(1.0), 1e-9)
        assertEquals(1.0, CorrelationStats.normalCDF(-2.5) + CorrelationStats.normalCDF(2.5), 1e-9)
    }

    // MARK: - Mann–Whitney U normal-approx p — reuses the Cliff's-delta pair scan.

    @Test
    fun mannWhitneyP_cleanSeparation_isSmall() {
        // present [8,8,8,8] vs absent [1,1,1,1]: U=16, μ_U=8, tie-corrected σ_U=3.0237157,
        // z=2.6457513, p = 2·(1−Φ(z)) ≈ 0.0081509716.
        val p = CorrelationStats.mannWhitneyP(present = listOf(8.0, 8.0, 8.0, 8.0), absent = listOf(1.0, 1.0, 1.0, 1.0))
        assertTrue(p < 0.05, "expected p < 0.05, got $p")
        assertEquals(0.0081509716, p, 1e-6)
    }

    @Test
    fun mannWhitneyP_identicalGroups_isOne() {
        // present [5,5,5] vs absent [5,5,5]: all pairs tie → degenerate σ_U → sentinel p=1.0.
        val p = CorrelationStats.mannWhitneyP(present = listOf(5.0, 5.0, 5.0), absent = listOf(5.0, 5.0, 5.0))
        assertEquals(1.0, p, 1e-9)
    }

    // MARK: - Benjamini–Hochberg step-up at q — passes the EXACT index set the worked example admits.

    @Test
    fun benjaminiHochberg_workedExample_exactSet() {
        // p = [0.001, 0.5, 0.9, 0.01, 0.7], K=5, q=0.10. Largest passing rank k=2 → origIdx {0, 3}.
        assertEquals(
            setOf(0, 3),
            CorrelationStats.benjaminiHochbergPasses(listOf(0.001, 0.5, 0.9, 0.01, 0.7), q = 0.10),
        )
    }

    @Test
    fun benjaminiHochberg_empty_isEmpty() {
        assertEquals(emptySet<Int>(), CorrelationStats.benjaminiHochbergPasses(emptyList(), q = 0.10))
    }

    @Test
    fun benjaminiHochberg_allLargeP_isEmpty() {
        // p = [0.6, 0.7, 0.8, 0.9]: even rank1 threshold 0.025 fails → empty.
        assertEquals(emptySet<Int>(), CorrelationStats.benjaminiHochbergPasses(listOf(0.6, 0.7, 0.8, 0.9), q = 0.10))
    }

    // MARK: - Confidence band — guard passedFDR && effectMag≥mediumFloor && n≥minN; else NONE.

    @Test
    fun confidence_high_needsLargeEffectAndN30() {
        // effectMag 0.5 ≥ largeFloor 0.474, n 35 ≥ 30, passedFDR → HIGH.
        assertEquals(
            InsightConfidence.HIGH,
            CorrelationStats.confidence(effectMag = 0.5, n = 35, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = true),
        )
    }

    @Test
    fun confidence_medium_n20MediumEffect() {
        // effectMag 0.4 ≥ medium 0.33, n 22 ≥ 20 but < 30 → MEDIUM.
        assertEquals(
            InsightConfidence.MEDIUM,
            CorrelationStats.confidence(effectMag = 0.4, n = 22, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = true),
        )
    }

    @Test
    fun confidence_low_n10to19() {
        // effectMag 0.4 ≥ medium, n 12 ≥ 10 but < 20 → LOW.
        assertEquals(
            InsightConfidence.LOW,
            CorrelationStats.confidence(effectMag = 0.4, n = 12, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = true),
        )
    }

    @Test
    fun confidence_belowMediumFloor_isNone() {
        // effectMag 0.2 < medium floor 0.33 → NONE regardless of n.
        assertEquals(
            InsightConfidence.NONE,
            CorrelationStats.confidence(effectMag = 0.2, n = 35, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = true),
        )
    }

    @Test
    fun confidence_underSampled_isNone() {
        // n 9 < minN (10) → NONE even with a large effect (under-sampled withholding).
        assertEquals(
            InsightConfidence.NONE,
            CorrelationStats.confidence(effectMag = 0.5, n = 9, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = true),
        )
    }

    @Test
    fun confidence_failedFDR_capsAtNone() {
        // passedFDR == false caps confidence at NONE regardless of effect/sample (FDR withholding).
        assertEquals(
            InsightConfidence.NONE,
            CorrelationStats.confidence(effectMag = 0.5, n = 35, minN = minN, mediumFloor = 0.33, largeFloor = 0.474, passedFDR = false),
        )
    }
}
