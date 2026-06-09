// EndoInsights — CorrelationStats: the pure statistical math the correlation engine stands on
// (Phase 7, Plan 01 / D-01/D-02/D-05/D-07/D-08/D-16/D-18 / SC1 / V7).
//
// THE NEW IP (D-18). A package-internal `enum` of pure, deterministic, Foundation-only helpers — each
// independently unit-tested against HAND-COMPUTED known values (CorrelationStatsTests) BEFORE any insight
// is built on it. Mirrors `TrendsEngine`'s "each helper independently testable" discipline and its
// `guard !values.isEmpty else { return nil }` never-coerce-empty-to-0 honesty invariant.
//
// Determinism (D-16): every function is pure `Double`/`Int` arithmetic over its arguments — NO `Date.now`,
// NO `UUID()`, NO RNG, NO dictionary-iteration-order dependence (the BH sort is keyed by value then index).
// Foundation-only (SC1): the BoundaryLint scans this file; it imports ONLY Foundation. No logging of any
// severity/factor value (V7 / T-07-02) — pure value-in, value-out.
import Foundation

enum CorrelationStats {

    // LOAD-BEARING (07-RESEARCH.md lines 199–207): a bare `lgamma(5.0)` is AMBIGUOUS in Swift 6 — two
    // overloads exist ((Double)->Double and the lgamma_r-style (Double)->(Double,Int)) and it will NOT
    // compile ("ambiguous use of 'lgamma'"). Bind the scalar overload ONCE here and use it everywhere.
    //   lgammaScalar(5.0) ≈ 3.17805383  == ln(24) == ln(4!)   ✓  (verified)
    // A computed property (not `static let`) so Swift 6 strict concurrency doesn't flag a non-Sendable
    // function-typed stored static; the bind is the load-bearing part, not the storage.
    private static var lgammaScalar: (Double) -> Double { (lgamma as (Double) -> Double) }

    // MARK: - Cliff's delta — (#{w>o} − #{w<o}) / (m·n), range [-1,+1], ties contribute 0 (D-01).

    /// Cliff's delta over factor-present severities `W` and factor-absent severities `O`.
    /// O(m·n) brute-force pair scan (fine at this scale). nil on either empty group (never coerce to 0).
    static func cliffsDelta(present W: [Double], absent O: [Double]) -> Double? {
        guard !W.isEmpty, !O.isEmpty else { return nil }
        var greater = 0, less = 0
        for w in W {
            for o in O {
                if w > o { greater += 1 } else if w < o { less += 1 }
            }
        }
        return Double(greater - less) / Double(W.count * O.count)
    }

    // MARK: - Mean difference — mean(W) − mean(O); nil on either empty (D-07 headline).

    static func meanDifference(present W: [Double], absent O: [Double]) -> Double? {
        guard !W.isEmpty, !O.isEmpty else { return nil }
        let meanW = W.reduce(0, +) / Double(W.count)
        let meanO = O.reduce(0, +) / Double(O.count)
        return meanW - meanO
    }

    // MARK: - Risk difference / risk ratio over a 2×2 (D-02). An empty arm is UNOBSERVED, never 0 (Pitfall 2).

    /// P(symptom|factor) − P(symptom|no factor). nil when either arm (a+b or c+d) is unobserved.
    static func riskDifference(a: Int, b: Int, c: Int, d: Int) -> Double? {
        guard a + b > 0, c + d > 0 else { return nil }
        return Double(a) / Double(a + b) - Double(c) / Double(c + d)
    }

    /// [P(symptom|factor)] / [P(symptom|no factor)] — the "lift". nil when the reference rate is 0.
    static func riskRatio(a: Int, b: Int, c: Int, d: Int) -> Double? {
        guard a + b > 0, c + d > 0 else { return nil }
        let reference = Double(c) / Double(c + d)
        guard reference > 0 else { return nil }
        return (Double(a) / Double(a + b)) / reference
    }

    // MARK: - Fisher's exact two-tailed (2×2). Hypergeometric sum via log-factorials (no overflow).

    /// log C(n, k) via the bound scalar lgamma overload.
    private static func lnChoose(_ n: Int, _ k: Int) -> Double {
        guard k >= 0, k <= n else { return -Double.infinity }
        return lgammaScalar(Double(n) + 1) - lgammaScalar(Double(k) + 1) - lgammaScalar(Double(n - k) + 1)
    }

    /// P(table) under the hypergeometric null: C(a+b,a)·C(c+d,c) / C(n, a+c).
    private static func hypergeometricProbability(a: Int, b: Int, c: Int, d: Int) -> Double {
        let n = a + b + c + d
        let lnP = lnChoose(a + b, a) + lnChoose(c + d, c) - lnChoose(n, a + c)
        return exp(lnP)
    }

    /// Two-tailed p = Σ P(table') over all margin-preserving tables whose probability ≤ P(observed).
    /// Iterates the upper-left cell `a` over its feasible range given fixed margins.
    static func fisherExactTwoTailed(a: Int, b: Int, c: Int, d: Int) -> Double {
        let r1 = a + b          // row 1 total
        let r2 = c + d          // row 2 total
        let c1 = a + c          // col 1 total
        guard r1 >= 0, r2 >= 0, c1 >= 0 else { return 1.0 }

        let observed = hypergeometricProbability(a: a, b: b, c: c, d: d)
        // a ranges over max(0, c1 − r2) ... min(r1, c1) so all four cells stay non-negative.
        let aMin = max(0, c1 - r2)
        let aMax = min(r1, c1)
        guard aMin <= aMax else { return 1.0 }

        var total = 0.0
        for ai in aMin...aMax {
            let bi = r1 - ai
            let ci = c1 - ai
            let di = r2 - ci
            let p = hypergeometricProbability(a: ai, b: bi, c: ci, d: di)
            // Tiny epsilon so the observed table's own probability (and equal-probability mirrors) are included.
            if p <= observed * (1 + 1e-7) { total += p }
        }
        return min(total, 1.0)
    }

    // MARK: - Standard-normal CDF — Φ(x) = 0.5·(1 + erf(x/√2)). `erf` is direct through Foundation.

    static func normalCDF(_ x: Double) -> Double {
        0.5 * (1 + erf(x / 2.0.squareRoot()))
    }

    // MARK: - Mann–Whitney U normal-approx p (with tie correction). Reuses the Cliff's-delta pair scan.

    /// U = #{w>o} + 0.5·#{w==o}; z = (U − μ_U)/σ_U with tie-corrected σ_U; p = 2·(1 − Φ(|z|)).
    /// Degenerate σ_U (e.g. all-tied pooled values) → defined sentinel p = 1.0.
    static func mannWhitneyP(present W: [Double], absent O: [Double]) -> Double {
        let m = W.count, n = O.count
        guard m > 0, n > 0 else { return 1.0 }

        var greater = 0, ties = 0
        for w in W {
            for o in O {
                if w > o { greater += 1 } else if w == o { ties += 1 }
            }
        }
        let U = Double(greater) + 0.5 * Double(ties)
        let muU = Double(m * n) / 2.0

        // Tie correction Σ(t³ − t) over tied-rank groups across the POOLED values (sorted, so the
        // grouping is deterministic and order-independent).
        let pooled = (W + O).sorted()
        var tieSum = 0.0
        var i = 0
        while i < pooled.count {
            var j = i + 1
            while j < pooled.count, pooled[j] == pooled[i] { j += 1 }
            let t = Double(j - i)
            tieSum += t * t * t - t
            i = j
        }

        let total = Double(m + n)
        // σ_U² = (m·n/12)·((m+n+1) − Σ(t³−t)/((m+n)(m+n−1)))
        let tieTerm = total > 1 ? tieSum / (total * (total - 1)) : 0.0
        let varianceU = (Double(m * n) / 12.0) * ((total + 1) - tieTerm)
        guard varianceU > 0 else { return 1.0 }

        let sigmaU = varianceU.squareRoot()
        let z = (U - muU) / sigmaU
        return 2 * (1 - normalCDF(abs(z)))
    }

    // MARK: - Benjamini–Hochberg step-up at q — the FDR pass set over the FULL p-value set (D-06).

    /// Indices whose p passes BH-FDR at `q`. Sort ascending (stable by index), find the largest rank k
    /// with p_(k) ≤ (k/K)·q, return all indices with rank ≤ k. Empty input → empty set.
    static func benjaminiHochbergPasses(_ pValues: [Double], q: Double = 0.10) -> Set<Int> {
        let k = pValues.count
        guard k > 0 else { return [] }
        // Ascending by value; ties broken by original index for a stable, deterministic order.
        let order = pValues.indices.sorted {
            pValues[$0] != pValues[$1] ? pValues[$0] < pValues[$1] : $0 < $1
        }
        var maxRank = -1
        for (rankZeroBased, idx) in order.enumerated() {
            let rank = rankZeroBased + 1
            if pValues[idx] <= (Double(rank) / Double(k)) * q { maxRank = rankZeroBased }
        }
        guard maxRank >= 0 else { return [] }
        return Set(order.prefix(maxRank + 1))
    }

    // MARK: - Confidence band (post-hard-gate). Floors are PARAMETERS — no literals leak to callers (D-05/D-08).

    /// `.none` unless passedFDR && effectMag ≥ mediumFloor && n ≥ `minN`. `.high` needs n ≥ `highSampleFloor`
    /// & a large effect; else `.medium` (n ≥ `mediumSampleFloor`) / `.low`. Return type is the existing
    /// public `InsightConfidence`.
    ///
    /// SINGLE SOURCE OF TRUTH (WR-01): the gate floor `minN` is a PARAMETER — the caller supplies
    /// `HonestyGate.minSample(for: .correlation)` so there is exactly ONE literal `10`, in `HonestyGate`.
    /// No second copy here that could silently drift from the gate. The band floors `highSampleFloor`/
    /// `mediumSampleFloor` are likewise named parameters (IN-01) so no numeric literal is buried in the body.
    static func confidence(
        effectMag: Double,
        n: Int,
        minN: Int,
        mediumFloor: Double,
        largeFloor: Double,
        passedFDR: Bool,
        highSampleFloor: Int = 30,
        mediumSampleFloor: Int = 20
    ) -> InsightConfidence {
        guard passedFDR, effectMag >= mediumFloor, n >= minN else { return .none }
        if n >= highSampleFloor && effectMag >= largeFloor { return .high }
        if n >= mediumSampleFloor { return .medium }
        return .low
    }
}
