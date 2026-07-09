// EndoInsights (:insights) — the two special functions Kotlin/JVM lacks, plus a Swift-parity rounding.
//
// THE HIGH-RISK PORT (Phase 17-03 / AENG-06). Swift's `CorrelationStats` reaches straight into C libm for
// `lgamma` (log-gamma) and `erf` (error function). Kotlin/JVM ships NEITHER — there is no `Math.lgamma`
// and no `kotlin.math.erf`. So we hand-roll both here and pin them against golden values generated from
// the shipped Swift/libm engine (`src/test/resources/stats-goldens.json`, produced by a throwaway
// standalone Swift snippet — the iOS tree was never modified). These are NOT bit-identical to libm; the
// contract is TOLERANCE parity (relative ~1e-9), which is what the downstream Fisher / Mann-Whitney
// p-values need (their own assertions carry the Swift suite's 1e-3 / 1e-6 tolerances).
//
// Foundation-only discipline mirror: this file imports ONLY `kotlin.math` — no java.*, no platform runtime
// (BoundaryLintTests guards the android./androidx. boundary; the kotlin.math-only choice keeps the numeric
// core portable and audit-clean).
package com.endoapp.insights

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * Pure, deterministic special-function helpers hand-rolled to stand in for the C libm functions Kotlin/JVM
 * does not expose. Pinned against Swift/libm golden values (see `stats-goldens.json`) within a documented
 * relative tolerance (~1e-9). Value-in, value-out; no state, no I/O, no RNG.
 */
internal object SpecialFunctions {

    // MARK: - lgamma — log|Γ(x)| via the Lanczos approximation (g=7, the standard 9-coefficient set).
    //
    // NOT bit-identical to libm's `lgamma`, but accurate to ~1e-14 relative across the domain the Fisher
    // `lnChoose(n, k)` call sites need (n up to ~24 in the test tables; ln(n+1) etc. are all x ≥ 1). Anchor:
    // `lgamma(5.0) ≈ 3.17805383 == ln(24) == ln(4!)`.

    private const val LANCZOS_G = 7.0

    // Lanczos g=7 coefficients (n=9). These are the canonical published constants.
    private val LANCZOS_COEF = doubleArrayOf(
        0.99999999999980993,
        676.5203681218851,
        -1259.1392167224028,
        771.32342877765313,
        -176.61502916214059,
        12.507343278686905,
        -0.13857109526572012,
        9.9843695780195716e-6,
        1.5056327351493116e-7,
    )

    private val HALF_LOG_TWO_PI = 0.5 * ln(2.0 * PI)

    /**
     * log of the absolute value of the Gamma function, matching C `lgamma(x)`.
     *
     * For `x >= 0.5` this is the direct Lanczos series. For `x < 0.5` the Euler reflection
     * `Γ(x)·Γ(1−x) = π / sin(πx)` is used so `lgamma` is defined on the whole line (the Fisher call sites
     * only ever pass `x ≥ 1`, but the reflection keeps the helper total and safe to reuse).
     */
    fun lgamma(x: Double): Double {
        if (x < 0.5) {
            // Reflection: lgamma(x) = ln(π / |sin(πx)|) − lgamma(1 − x).
            val sinPiX = sin(PI * x)
            return ln(PI / kotlin.math.abs(sinPiX)) - lgamma(1.0 - x)
        }
        val z = x - 1.0
        var a = LANCZOS_COEF[0]
        val t = z + LANCZOS_G + 0.5
        for (i in 1 until LANCZOS_COEF.size) {
            a += LANCZOS_COEF[i] / (z + i)
        }
        return HALF_LOG_TWO_PI + (z + 0.5) * ln(t) - t + ln(a)
    }

    // MARK: - erf / erfc — the Gauss error function via the Numerical-Recipes Chebyshev fit.
    //
    // 28-term Chebyshev fit of `erfc` (NR3 `erfccheb`), accurate to ~1e-15 — deliberately NOT the low-order
    // Abramowitz-Stegun 7.1.26 rational (~1e-7), which would drift the Mann-Whitney p-values past their
    // 1e-6 parity tolerance. `normalCDF` is built on `erf` exactly as Swift's `CorrelationStats.normalCDF`.

    private val ERFC_CHEB_COEF = doubleArrayOf(
        -1.3026537197817094, 6.4196979235649026e-1, 1.9476473204185836e-2,
        -9.561514786808631e-3, -9.46595344482036e-4, 3.66839497852761e-4,
        4.2523324806907e-5, -2.0278578112534e-5, -1.624290004647e-6,
        1.303655835580e-6, 1.5626441722e-8, -8.5238095915e-8,
        6.529054439e-9, 5.059343495e-9, -9.91364156e-10,
        -2.27365122e-10, 9.6467911e-11, 2.394038e-12,
        -6.886027e-12, 8.94487e-13, 3.13092e-13,
        -1.12708e-13, 3.81e-16, 7.106e-15,
        -1.523e-15, -9.4e-17, 1.21e-16, -2.8e-17,
    )

    /** `erfc` for a non-negative argument via the Chebyshev fit (NR3). */
    private fun erfcCheb(z: Double): Double {
        val t = 2.0 / (2.0 + z)
        val ty = 4.0 * t - 2.0
        var d = 0.0
        var dd = 0.0
        for (j in ERFC_CHEB_COEF.size - 1 downTo 1) {
            val tmp = d
            d = ty * d - dd + ERFC_CHEB_COEF[j]
            dd = tmp
        }
        return t * exp(-z * z + 0.5 * (ERFC_CHEB_COEF[0] + ty * d) - dd)
    }

    /** The Gauss error function, matching C `erf(x)`. Odd: `erf(−x) = −erf(x)`. */
    fun erf(x: Double): Double =
        if (x >= 0.0) 1.0 - erfcCheb(x) else erfcCheb(-x) - 1.0

    /** The complementary error function, matching C `erfc(x)`. */
    fun erfc(x: Double): Double =
        if (x >= 0.0) erfcCheb(x) else 2.0 - erfcCheb(-x)

    // MARK: - normalCDF — Φ(x) = 0.5·(1 + erf(x/√2)); verbatim mirror of CorrelationStats.normalCDF.

    private val SQRT_2 = kotlin.math.sqrt(2.0)

    /** The standard-normal cumulative distribution Φ(x). Φ(0) == 0.5 exactly. */
    fun normalCDF(x: Double): Double = 0.5 * (1.0 + erf(x / SQRT_2))
}

// MARK: - roundedAwayFromZero — Swift `.rounded()` parity (half-AWAY-from-zero), NOT banker's/half-up.
//
// Swift's `Double.rounded()` (`.toNearestOrAwayFromZero`) breaks .5 ties AWAY from zero: 2.5→3, −2.5→−3,
// 0.5→1, −0.5→−1. Kotlin's `kotlin.math.round` is banker's (round-half-to-even: 2.5→2, −2.5→−2) and
// `java.lang.Math.round` is half-UP (2.5→3 but −2.5→−2) — both diverge from Swift exactly on ties. This
// extension reproduces Swift's semantics so any rounding on the parity path matches bit-for-bit.
internal fun Double.roundedAwayFromZero(): Double =
    if (this < 0.0) -floor(-this + 0.5) else floor(this + 0.5)
