// EndoInsights (:insights) — the AENG-06 golden gate for the hand-rolled special functions (T-17-07).
//
// Kotlin/JVM has no stdlib `lgamma`/`erf`, so `SpecialFunctions` hand-rolls them (Lanczos g=7 / NR3
// Chebyshev). This guard pins those approximations against golden values generated from the shipped
// Swift/libm engine — `src/test/resources/stats-goldens.json`, produced by a throwaway standalone Swift
// snippet (Foundation/libm only; the iOS tree was NOT modified). It is the standing proof that the
// tolerance story downstream (Fisher / Mann-Whitney p parity) is real, not asserted.
//
// This is a Kotlin-side guard with NO Swift counterpart (Swift calls libm directly and needs no golden
// file), so it is intentionally OUTSIDE the 26-case CorrelationStatsTests parity mirror.
package com.endoapp.insights

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

class SpecialFunctionsGoldenTests {

    // Tolerance recorded in stats-goldens.json._tolerance: relative 1e-9, absolute floor 1e-12 near zero.
    private val relTol = 1e-9
    private val absFloor = 1e-12

    private val goldens by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("stats-goldens.json")
            ?: error("stats-goldens.json not found on the test classpath")
        Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
    }

    /** Assert `actual` matches the libm golden `expected` within relative [relTol] (absolute [absFloor]
     *  near zero, where relative error is meaningless). */
    private fun assertGolden(name: String, x: Double, expected: Double, actual: Double) {
        val tol = max(absFloor, relTol * abs(expected))
        assertTrue(
            abs(actual - expected) <= tol,
            "$name($x): hand-rolled $actual diverged from Swift/libm golden $expected by " +
                "${abs(actual - expected)} (> tol $tol)",
        )
    }

    private fun rows(key: String): List<Pair<Double, Double>> =
        goldens[key]!!.jsonArray.map { el ->
            val o = el.jsonObject
            o["x"]!!.jsonPrimitive.double to o["y"]!!.jsonPrimitive.double
        }

    @Test
    fun lgammaMatchesSwiftLibmGoldens() {
        for ((x, y) in rows("lgamma")) {
            assertGolden("lgamma", x, y, SpecialFunctions.lgamma(x))
        }
    }

    @Test
    fun erfMatchesSwiftLibmGoldens() {
        for ((x, y) in rows("erf")) {
            assertGolden("erf", x, y, SpecialFunctions.erf(x))
        }
    }

    @Test
    fun normalCDFMatchesSwiftLibmGoldens() {
        for ((x, y) in rows("normalCDF")) {
            assertGolden("normalCDF", x, y, SpecialFunctions.normalCDF(x))
        }
    }
}
