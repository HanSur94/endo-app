// EndoInsights — the INST-03 CorrelationStats proof suite (Phase 7, Plan 01 / D-01/D-02/D-05/D-07/D-08/D-16/D-18 / SC1).
//
// TDD RED→GREEN: this is the MATH FOUNDATION the entire correlation engine stands on. Every #expect
// asserts a helper against a value computed BY HAND (cited in-comment), per D-16 "no p-hacking; the math
// documented + tested". Nothing downstream (effect floor, FDR gate, confidence band, emitted Insights) is
// trustworthy unless the deltas/p-values here are proven correct first. No RNG — every assertion is
// exact / closed-form, so the suite is deterministic and Equatable-stable.
//
// Header mirrors CyclePhaseMapperTests (lines 8–13): Testing + Foundation + @testable import EndoInsights.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-03 CorrelationStats — pure stats helpers vs known values")
struct CorrelationStatsTests {

    // MARK: - Cliff's delta — (#{w>o} − #{w<o}) / (m·n), range [-1,+1], ties contribute 0.

    @Test func cliffsDelta_allPresentGreater_isPlusOne() {
        // present all 7, absent all 2 → every pair w>o → greater=9, less=0 → 9/9 = +1.
        #expect(CorrelationStats.cliffsDelta(present: [7, 7, 7], absent: [2, 2, 2]) == 1.0)
    }

    @Test func cliffsDelta_allPresentLess_isMinusOne() {
        // present all 2, absent all 7 → every pair w<o → greater=0, less=9 → −9/9 = −1.
        #expect(CorrelationStats.cliffsDelta(present: [2, 2, 2], absent: [7, 7, 7]) == -1.0)
    }

    @Test func cliffsDelta_allTies_isZero() {
        // present [5,5], absent [5,5] → all 4 pairs tie → greater=0, less=0 → 0/4 = 0.
        #expect(CorrelationStats.cliffsDelta(present: [5, 5], absent: [5, 5]) == 0.0)
    }

    @Test func cliffsDelta_mixed_handComputed() {
        // present [3,4], absent [1,5]. Pairs: (3,1)>, (3,5)<, (4,1)>, (4,5)< → greater=2, less=2 → 0/4 = 0.
        #expect(CorrelationStats.cliffsDelta(present: [3, 4], absent: [1, 5]) == 0.0)
        // A clearly-positive mixed case: present [3,4], absent [1,2]. All 4 pairs w>o → greater=4 → +1.
        #expect(CorrelationStats.cliffsDelta(present: [3, 4], absent: [1, 2]) == 1.0)
        // Asymmetric hand case: present [4,6], absent [5,5]. (4,5)<,(4,5)<,(6,5)>,(6,5)> → greater=2,less=2 → 0.
        #expect(CorrelationStats.cliffsDelta(present: [4, 6], absent: [5, 5]) == 0.0)
        // present [6,6,6], absent [4,5,7]: w=6 vs {4,5,7} → 2 greater,1 less each; ×3 → greater=6,less=3 → 3/9 = 1/3.
        let d = CorrelationStats.cliffsDelta(present: [6, 6, 6], absent: [4, 5, 7])
        #expect(d != nil && abs(d! - (1.0 / 3.0)) < 1e-12)
    }

    @Test func cliffsDelta_emptyGroup_isNil() {
        #expect(CorrelationStats.cliffsDelta(present: [], absent: [1, 2]) == nil)
        #expect(CorrelationStats.cliffsDelta(present: [1, 2], absent: []) == nil)
    }

    // MARK: - Mean difference — mean(W) − mean(O); nil on empty (never coerce empty to 0).

    @Test func meanDifference_handComputed() {
        // mean([6,6])=6, mean([2,2])=2 → 4.
        #expect(CorrelationStats.meanDifference(present: [6, 6], absent: [2, 2]) == 4.0)
        // mean([1,2,3])=2, mean([4,6])=5 → 2−5 = −3.
        #expect(CorrelationStats.meanDifference(present: [1, 2, 3], absent: [4, 6]) == -3.0)
    }

    @Test func meanDifference_emptyGroup_isNil() {
        #expect(CorrelationStats.meanDifference(present: [], absent: [2]) == nil)
        #expect(CorrelationStats.meanDifference(present: [2], absent: []) == nil)
    }

    // MARK: - Risk difference — a/(a+b) − c/(c+d); nil when either arm unobserved (D-02 / Pitfall 2).

    @Test func riskDifference_handComputed() {
        // a:8,b:2 → 0.8 ; c:2,d:8 → 0.2 → 0.8−0.2 = 0.6.
        let rd = CorrelationStats.riskDifference(a: 8, b: 2, c: 2, d: 8)
        #expect(rd != nil && abs(rd! - 0.6) < 1e-12)
    }

    @Test func riskDifference_emptyArm_isNil() {
        // a+b == 0 → factor-present arm unobserved → nil (NOT 0).
        #expect(CorrelationStats.riskDifference(a: 0, b: 0, c: 2, d: 8) == nil)
        // c+d == 0 → factor-absent arm unobserved → nil.
        #expect(CorrelationStats.riskDifference(a: 8, b: 2, c: 0, d: 0) == nil)
    }

    // MARK: - Risk ratio — [a/(a+b)] / [c/(c+d)]; nil when the reference rate is 0.

    @Test func riskRatio_handComputed() {
        // 0.8 / 0.2 = 4.0.
        let rr = CorrelationStats.riskRatio(a: 8, b: 2, c: 2, d: 8)
        #expect(rr != nil && abs(rr! - 4.0) < 1e-12)
    }

    @Test func riskRatio_zeroReferenceRate_isNil() {
        // c/(c+d) == 0 → division undefined → nil.
        #expect(CorrelationStats.riskRatio(a: 8, b: 2, c: 0, d: 8) == nil)
        // c+d == 0 → reference arm unobserved → nil.
        #expect(CorrelationStats.riskRatio(a: 8, b: 2, c: 0, d: 0) == nil)
    }

    // MARK: - Fisher's exact two-tailed — proves the lgamma binding + the hypergeometric sum.

    @Test func fisherExact_textbookTable_handComputed() {
        // Table a:1,b:9,c:11,d:3 (margins r1=10,r2=14,c1=12,c2=12,n=24). Two-tailed p = sum of P(table')
        // over all tables with these margins whose probability ≤ P(observed). Computed by hand via
        // log-factorials (Python lgamma cross-check this session): p ≈ 0.0027594562.
        let p = CorrelationStats.fisherExactTwoTailed(a: 1, b: 9, c: 11, d: 3)
        #expect(abs(p - 0.0027594562) < 1e-3)
    }

    @Test func fisherExact_independentTable_pIsOne() {
        // A perfectly symmetric/independent table a:5,b:5,c:5,d:5 has the observed table as the most likely;
        // the two-tailed sum over all margin-preserving tables = 1.0 (every table's P ≤ P(observed) is false
        // only for none — full mass sums to 1). p ≈ 1.0.
        let p = CorrelationStats.fisherExactTwoTailed(a: 5, b: 5, c: 5, d: 5)
        #expect(abs(p - 1.0) < 1e-3)
    }

    // MARK: - normalCDF — Φ(x) = 0.5·(1 + erf(x/√2)); erf is direct through Foundation.

    @Test func normalCDF_knownValues() {
        // Φ(0) = 0.5 exactly.
        #expect(CorrelationStats.normalCDF(0) == 0.5)
        // Φ(1) = 0.8413447461 (standard-normal table value; = 0.5·(1+erf(1/√2))). NOTE: the plan's prose
        // quoted 0.6826894921 for Φ(1/√2), but 0.6826894921 is erf(1/√2) (= the ±1σ mass), NOT a CDF value.
        // Φ(1/√2) ≈ 0.7602499389. We assert the mathematically-correct CDF known values here (Rule 1 fix).
        #expect(abs(CorrelationStats.normalCDF(1) - 0.8413447461) < 1e-6)
        // Φ(1/√2) ≈ 0.7602499389 (closed-form cross-check this session).
        #expect(abs(CorrelationStats.normalCDF(1.0 / 2.0.squareRoot()) - 0.7602499389) < 1e-6)
    }

    @Test func normalCDF_symmetry() {
        // Φ(−x) + Φ(x) = 1 for all x.
        #expect(abs(CorrelationStats.normalCDF(-1) + CorrelationStats.normalCDF(1) - 1.0) < 1e-9)
        #expect(abs(CorrelationStats.normalCDF(-2.5) + CorrelationStats.normalCDF(2.5) - 1.0) < 1e-9)
    }

    // MARK: - Mann–Whitney U normal-approx p — reuses the Cliff's-delta pair scan.

    @Test func mannWhitneyP_cleanSeparation_isSmall() {
        // present [8,8,8,8] vs absent [1,1,1,1]: U=16, μ_U=8. The pooled set has TIES (four 8s, four 1s),
        // so Task 2's tie-corrected σ_U applies (NOT the no-tie 3.4641 the plan's prose mistakenly quoted):
        // tieSum = (4³−4)·2 = 120, tieTerm = 120/(8·7) = 2.142857, σ_U² = (16/12)·(9 − 2.142857) = 9.142857,
        // σ_U = 3.0237157, z = (16−8)/3.0237157 = 2.6457513, p = 2·(1−Φ(2.6457513)) ≈ 0.0081509716.
        // (Cross-checked this session in Python AND isolated Swift; both give 0.0081509716.) Rule 1 fix:
        // the plan's Task-1 prose value 0.0209 omitted the tie correction Task 2 mandates — that is wrong.
        let p = CorrelationStats.mannWhitneyP(present: [8, 8, 8, 8], absent: [1, 1, 1, 1])
        #expect(p < 0.05)
        #expect(abs(p - 0.0081509716) < 1e-6)
    }

    @Test func mannWhitneyP_identicalGroups_isOne() {
        // present [5,5,5] vs absent [5,5,5]: all pairs tie → U = 0.5·9 = 4.5 = μ_U → z=0 → p=1.0.
        // Also the tie correction drives σ_U; degenerate → defined sentinel p=1.0.
        let p = CorrelationStats.mannWhitneyP(present: [5, 5, 5], absent: [5, 5, 5])
        #expect(abs(p - 1.0) < 1e-9)
    }

    // MARK: - Benjamini–Hochberg step-up at q — passes the EXACT index set the worked example admits.

    @Test func benjaminiHochberg_workedExample_exactSet() {
        // p = [0.001, 0.5, 0.9, 0.01, 0.7], K=5, q=0.10.
        // Sorted ascending (value@origIdx): 0.001@0, 0.01@3, 0.5@1, 0.7@4, 0.9@2.
        // Thresholds (k/K)·q: k1=0.02, k2=0.04, k3=0.06, k4=0.08, k5=0.10.
        //  rank1: 0.001 ≤ 0.02 ✓ ; rank2: 0.01 ≤ 0.04 ✓ ; rank3: 0.5 ≤ 0.06 ✗ ; rank4 ✗ ; rank5 ✗.
        // Largest passing rank k=2 → indices with rank ≤ 2 are origIdx {0, 3}.
        #expect(CorrelationStats.benjaminiHochbergPasses([0.001, 0.5, 0.9, 0.01, 0.7], q: 0.10) == Set([0, 3]))
    }

    @Test func benjaminiHochberg_empty_isEmpty() {
        #expect(CorrelationStats.benjaminiHochbergPasses([], q: 0.10) == Set<Int>())
    }

    @Test func benjaminiHochberg_allLargeP_isEmpty() {
        // p = [0.6, 0.7, 0.8, 0.9]: even rank1 threshold 0.025 fails → no rank passes → empty.
        #expect(CorrelationStats.benjaminiHochbergPasses([0.6, 0.7, 0.8, 0.9], q: 0.10) == Set<Int>())
    }

    // MARK: - Confidence band — guard passedFDR && effectMag≥mediumFloor && n≥10; else .none (D-05/D-07/D-08).

    @Test func confidence_high_needsLargeEffectAndN30() {
        // effectMag 0.5 ≥ largeFloor 0.474, n 35 ≥ 30, passedFDR → .high.
        #expect(CorrelationStats.confidence(effectMag: 0.5, n: 35, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: true) == .high)
    }

    @Test func confidence_medium_n20MediumEffect() {
        // effectMag 0.4 ≥ medium 0.33, n 22 ≥ 20 but < 30 (or effect < large) → .medium.
        #expect(CorrelationStats.confidence(effectMag: 0.4, n: 22, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: true) == .medium)
    }

    @Test func confidence_low_n10to19() {
        // effectMag 0.4 ≥ medium, n 12 ≥ 10 but < 20 → .low.
        #expect(CorrelationStats.confidence(effectMag: 0.4, n: 12, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: true) == .low)
    }

    @Test func confidence_belowMediumFloor_isNone() {
        // effectMag 0.2 < medium floor 0.33 → .none regardless of n.
        #expect(CorrelationStats.confidence(effectMag: 0.2, n: 35, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: true) == .none)
    }

    @Test func confidence_underSampled_isNone() {
        // n 9 < 10 → .none even with a large effect (the under-sampled withholding case).
        #expect(CorrelationStats.confidence(effectMag: 0.5, n: 9, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: true) == .none)
    }

    @Test func confidence_failedFDR_capsAtNone() {
        // passedFDR == false caps confidence at .none regardless of effect/sample (the FDR withholding case).
        #expect(CorrelationStats.confidence(effectMag: 0.5, n: 35, mediumFloor: 0.33, largeFloor: 0.474, passedFDR: false) == .none)
    }
}
