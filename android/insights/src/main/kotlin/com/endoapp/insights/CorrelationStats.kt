// EndoInsights (:insights) — CorrelationStats: the pure statistical math the correlation engine stands on.
//
// The 1:1 Kotlin mirror of `EndoInsights/Sources/EndoInsights/CorrelationStats.swift` (Phase 7 / D-01..18).
// A package-internal set of pure, deterministic helpers — each independently unit-tested against
// hand-computed known values (CorrelationStatsTests) BEFORE any insight is built on it. Mirrors Swift's
// "never coerce empty to 0" honesty invariant (`return null` on empty groups / unobserved arms).
//
// Determinism: every function is pure Double/Int arithmetic — NO clock, NO UUID, NO RNG, NO map-iteration
// dependence (the BH sort is keyed by value then original index). The two libm functions Swift reaches for
// (`lgamma`, `erf`) do not exist on Kotlin/JVM; they are hand-rolled in `SpecialFunctions` and golden-gated
// against Swift/libm within a documented tolerance (~1e-9 relative) — so Fisher/Mann-Whitney parity is
// TOLERANCE-based, not bit-exact. See `SpecialFunctionsGoldenTests` + `stats-goldens.json`.
//
// kotlin.math-only: no java.*, no platform runtime (BoundaryLintTests guards the boundary).
package com.endoapp.insights

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Pure statistical helpers for the correlation engine — the Kotlin `object` mirror of Swift's
 * `enum CorrelationStats` (an uninstantiable namespace of static functions).
 */
object CorrelationStats {

    // MARK: - Cliff's delta — (#{w>o} − #{w<o}) / (m·n), range [-1,+1], ties contribute 0 (D-01).

    /** Cliff's delta over factor-present severities [W] and factor-absent severities [O].
     *  O(m·n) brute-force pair scan. `null` on either empty group (never coerce to 0). */
    fun cliffsDelta(present: List<Double>, absent: List<Double>): Double? {
        if (present.isEmpty() || absent.isEmpty()) return null
        var greater = 0
        var less = 0
        for (w in present) {
            for (o in absent) {
                if (w > o) greater += 1 else if (w < o) less += 1
            }
        }
        return (greater - less).toDouble() / (present.size * absent.size).toDouble()
    }

    // MARK: - Mean difference — mean(W) − mean(O); null on either empty (D-07 headline).

    fun meanDifference(present: List<Double>, absent: List<Double>): Double? {
        if (present.isEmpty() || absent.isEmpty()) return null
        val meanW = present.sum() / present.size.toDouble()
        val meanO = absent.sum() / absent.size.toDouble()
        return meanW - meanO
    }

    // MARK: - Risk difference / risk ratio over a 2×2 (D-02). An empty arm is UNOBSERVED, never 0.

    /** P(symptom|factor) − P(symptom|no factor). `null` when either arm (a+b or c+d) is unobserved. */
    fun riskDifference(a: Int, b: Int, c: Int, d: Int): Double? {
        if (a + b <= 0 || c + d <= 0) return null
        return a.toDouble() / (a + b).toDouble() - c.toDouble() / (c + d).toDouble()
    }

    /** [P(symptom|factor)] / [P(symptom|no factor)] — the "lift". `null` when the reference rate is 0. */
    fun riskRatio(a: Int, b: Int, c: Int, d: Int): Double? {
        if (a + b <= 0 || c + d <= 0) return null
        val reference = c.toDouble() / (c + d).toDouble()
        if (reference <= 0.0) return null
        return (a.toDouble() / (a + b).toDouble()) / reference
    }

    // MARK: - Fisher's exact two-tailed (2×2). Hypergeometric sum via log-factorials (no overflow).

    /** log C(n, k) via the hand-rolled `SpecialFunctions.lgamma`. */
    private fun lnChoose(n: Int, k: Int): Double {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY
        return SpecialFunctions.lgamma((n + 1).toDouble()) -
            SpecialFunctions.lgamma((k + 1).toDouble()) -
            SpecialFunctions.lgamma((n - k + 1).toDouble())
    }

    /** P(table) under the hypergeometric null: C(a+b,a)·C(c+d,c) / C(n, a+c). */
    private fun hypergeometricProbability(a: Int, b: Int, c: Int, d: Int): Double {
        val n = a + b + c + d
        val lnP = lnChoose(a + b, a) + lnChoose(c + d, c) - lnChoose(n, a + c)
        return exp(lnP)
    }

    /** Two-tailed p = Σ P(table') over all margin-preserving tables whose probability ≤ P(observed).
     *  Iterates the upper-left cell `a` over its feasible range given fixed margins. */
    fun fisherExactTwoTailed(a: Int, b: Int, c: Int, d: Int): Double {
        val r1 = a + b // row 1 total
        val r2 = c + d // row 2 total
        val c1 = a + c // col 1 total
        if (r1 < 0 || r2 < 0 || c1 < 0) return 1.0

        val observed = hypergeometricProbability(a = a, b = b, c = c, d = d)
        // a ranges over max(0, c1 − r2) … min(r1, c1) so all four cells stay non-negative.
        val aMin = maxOf(0, c1 - r2)
        val aMax = minOf(r1, c1)
        if (aMin > aMax) return 1.0

        var total = 0.0
        for (ai in aMin..aMax) {
            val bi = r1 - ai
            val ci = c1 - ai
            val di = r2 - ci
            val p = hypergeometricProbability(a = ai, b = bi, c = ci, d = di)
            // Tiny epsilon so the observed table's own probability (and equal-probability mirrors) are included.
            if (p <= observed * (1 + 1e-7)) total += p
        }
        return minOf(total, 1.0)
    }

    // MARK: - Standard-normal CDF — Φ(x) = 0.5·(1 + erf(x/√2)); erf via SpecialFunctions.

    fun normalCDF(x: Double): Double = SpecialFunctions.normalCDF(x)

    // MARK: - Mann–Whitney U normal-approx p (tie-corrected σ_U, NO continuity correction).

    /** U = #{w>o} + 0.5·#{w==o}; z = (U − μ_U)/σ_U with tie-corrected σ_U; p = 2·(1 − Φ(|z|)).
     *  Degenerate σ_U (e.g. all-tied pooled values) → defined sentinel p = 1.0.
     *
     *  NO CONTINUITY CORRECTION (WR-03): the numerator is `U − μ_U` with no `±0.5` term. This matches
     *  `scipy.stats.mannwhitneyu(method='asymptotic', use_continuity=False)`. The no-continuity form gives a
     *  slightly SMALLER p (slightly more permissive); the downstream three-AND gate is the real backstop. */
    fun mannWhitneyP(present: List<Double>, absent: List<Double>): Double {
        val m = present.size
        val n = absent.size
        if (m <= 0 || n <= 0) return 1.0

        var greater = 0
        var ties = 0
        for (w in present) {
            for (o in absent) {
                if (w > o) greater += 1 else if (w == o) ties += 1
            }
        }
        val u = greater.toDouble() + 0.5 * ties.toDouble()
        val muU = (m * n).toDouble() / 2.0

        // Tie correction Σ(t³ − t) over tied-rank groups across the POOLED values. `List<Double>.sorted()`
        // is a stable natural-order sort (NOT DoubleArray.sort(), which is unstable and total-orders NaN
        // differently) — deterministic, order-independent tie grouping identical to Swift's `.sorted()`.
        val pooled = (present + absent).sorted()
        var tieSum = 0.0
        var i = 0
        while (i < pooled.size) {
            var j = i + 1
            while (j < pooled.size && pooled[j] == pooled[i]) j += 1
            val t = (j - i).toDouble()
            tieSum += t * t * t - t
            i = j
        }

        val total = (m + n).toDouble()
        // σ_U² = (m·n/12)·((m+n+1) − Σ(t³−t)/((m+n)(m+n−1)))
        val tieTerm = if (total > 1) tieSum / (total * (total - 1)) else 0.0
        val varianceU = ((m * n).toDouble() / 12.0) * ((total + 1) - tieTerm)
        if (varianceU <= 0.0) return 1.0

        val sigmaU = sqrt(varianceU)
        val z = (u - muU) / sigmaU
        return 2 * (1 - normalCDF(abs(z)))
    }

    // MARK: - Benjamini–Hochberg step-up at q — the FDR pass set over the FULL p-value set (D-06).

    /** Indices whose p passes BH-FDR at [q]. Sort ascending (stable by original index), find the largest
     *  rank k with p_(k) ≤ (k/K)·q, return all indices with rank ≤ k. Empty input → empty set. */
    fun benjaminiHochbergPasses(pValues: List<Double>, q: Double = 0.10): Set<Int> {
        val k = pValues.size
        if (k <= 0) return emptySet()
        // Ascending by value; ties broken by original index — a stable, deterministic order that reproduces
        // Swift's `p[$0] != p[$1] ? p[$0] < p[$1] : $0 < $1`.
        val order = pValues.indices.sortedWith(compareBy({ pValues[it] }, { it }))
        var maxRank = -1
        for ((rankZeroBased, idx) in order.withIndex()) {
            val rank = rankZeroBased + 1
            if (pValues[idx] <= (rank.toDouble() / k.toDouble()) * q) maxRank = rankZeroBased
        }
        if (maxRank < 0) return emptySet()
        return order.take(maxRank + 1).toSet()
    }

    // MARK: - Confidence band (post-hard-gate). Floors are PARAMETERS — no literals leak to callers.

    /** NONE unless passedFDR && effectMag ≥ mediumFloor && n ≥ [minN]. HIGH needs n ≥ [highSampleFloor]
     *  & a large effect; else MEDIUM (n ≥ [mediumSampleFloor]) / LOW. Returns the public [InsightConfidence].
     *
     *  SSOT (WR-01): the gate floor [minN] is a PARAMETER — the caller supplies
     *  `HonestyGate.minSample(for: .correlation)` so there is exactly ONE literal `10`, in HonestyGate.
     *  The band floors are likewise named parameters so no numeric literal is buried in the body. */
    fun confidence(
        effectMag: Double,
        n: Int,
        minN: Int,
        mediumFloor: Double,
        largeFloor: Double,
        passedFDR: Boolean,
        highSampleFloor: Int = 30,
        mediumSampleFloor: Int = 20,
    ): InsightConfidence {
        if (!(passedFDR && effectMag >= mediumFloor && n >= minN)) return InsightConfidence.NONE
        if (n >= highSampleFloor && effectMag >= largeFloor) return InsightConfidence.HIGH
        if (n >= mediumSampleFloor) return InsightConfidence.MEDIUM
        return InsightConfidence.LOW
    }
}
