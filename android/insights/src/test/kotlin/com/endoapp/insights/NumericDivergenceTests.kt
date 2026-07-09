// EndoInsights (:insights) — the AENG-06 numeric-divergence fixture pack (NEW; stats + boundary half).
//
// The classes of Kotlin↔Swift numeric divergence that the Swift fixtures never probed (they ran on one
// runtime). Each test pins a point where the two runtimes' Double/rank/rounding semantics could silently
// disagree, proving the Kotlin port reproduces Swift exactly:
//
//   1. Rounding ties — Swift `.rounded()` is half-AWAY-from-zero; Kotlin `kotlin.math.round` is banker's
//      (half-to-even) and `java.lang.Math.round` is half-UP. They diverge EXACTLY on ±.5 ties. The port's
//      `Double.roundedAwayFromZero()` matches Swift.
//   2. Mann-Whitney tied ranks — `List<Double>.sorted()` tie-grouping + the `w==o` tie count must match
//      Swift's `.sorted()` so the tie-corrected σ_U (and thus p) is identical (Swift-generated goldens).
//   3. NaN / ±0.0 at the DaySnapshot boundary — NaN is unrepresentable (the `of()` factory normalizes
//      non-finite → null) and `-0.0` canonicalizes to `0.0`, so boxed-Double equality behaves as Swift IEEE.
//
// The DST / non-UTC day-boundary half of this pack is DEFERRED to 17-08 (it needs the ported engines that
// consume LocalDate day identity) — a clearly-marked section header is stubbed at the bottom for it.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.round

class NumericDivergenceTests {

    // MARK: - 1. Rounding half-away-from-zero (Swift `.rounded()`) vs banker's / half-up.

    @Test
    fun roundedAwayFromZero_matchesSwiftOnTies_notBankersNorHalfUp() {
        // Swift `.rounded()` semantics: ±.5 ties break AWAY from zero.
        assertEquals(3.0, 2.5.roundedAwayFromZero())
        assertEquals(-3.0, (-2.5).roundedAwayFromZero())
        assertEquals(1.0, 0.5.roundedAwayFromZero())
        assertEquals(-1.0, (-0.5).roundedAwayFromZero())
        assertEquals(4.0, 3.5.roundedAwayFromZero())
        assertEquals(-4.0, (-3.5).roundedAwayFromZero())
    }

    @Test
    fun roundedAwayFromZero_divergesFromKotlinRound_exactlyOnTies() {
        // kotlin.math.round is banker's (round-half-to-even): 2.5→2, −2.5→−2, 0.5→0, 3.5→4.
        assertEquals(2.0, round(2.5))       // sanity: banker's rounds .5 to the even neighbour
        assertNotEquals(round(2.5), 2.5.roundedAwayFromZero())    // 2.0 vs 3.0
        assertNotEquals(round(-2.5), (-2.5).roundedAwayFromZero()) // -2.0 vs -3.0
        assertNotEquals(round(0.5), 0.5.roundedAwayFromZero())     // 0.0 vs 1.0
        // On NON-ties the two agree — the divergence is CONFINED to ties.
        assertEquals(round(2.4), 2.4.roundedAwayFromZero())        // both 2.0
        assertEquals(round(2.6), 2.6.roundedAwayFromZero())        // both 3.0
        assertEquals(round(-2.6), (-2.6).roundedAwayFromZero())    // both -3.0
    }

    @Test
    fun roundedAwayFromZero_divergesFromMathRound_onNegativeTies() {
        // java.lang.Math.round is half-UP (toward +∞): 2.5→3 (agrees) BUT −2.5→−2 and −0.5→0 (diverge).
        assertEquals(3L, Math.round(2.5))
        assertEquals(-2L, Math.round(-2.5))
        assertEquals(0L, Math.round(-0.5))
        assertNotEquals(Math.round(-2.5).toDouble(), (-2.5).roundedAwayFromZero()) // -2.0 vs -3.0
        assertNotEquals(Math.round(-0.5).toDouble(), (-0.5).roundedAwayFromZero()) // 0.0 vs -1.0
    }

    // MARK: - 2. Mann-Whitney tied ranks — tie grouping + w==o counting must match Swift.

    @Test
    fun mannWhitney_allTied_degenerateSigma_isOne() {
        // [3,3,3] vs [3,3,3]: every pair ties → σ_U² collapses to 0 → defined sentinel p = 1.0.
        // (Swift libm cross-check: mannWhitneyP([3,3,3],[3,3,3]) == 1.0.)
        val p = CorrelationStats.mannWhitneyP(present = listOf(3.0, 3.0, 3.0), absent = listOf(3.0, 3.0, 3.0))
        assertEquals(1.0, p, 1e-12)
    }

    @Test
    fun mannWhitney_mixedTies_matchesSwiftGolden() {
        // [5,5,4] vs [4,4,4]: greater=6, ties=3 → U=7.5, μ_U=4.5; pooled [4,4,4,4,5,5] → tieSum=60+6=66;
        // tieTerm=66/30=2.2; σ_U²=0.75·(7−2.2)=3.6; z=3/√3.6=1.5811388; p=2·(1−Φ(z)).
        // Swift/libm authoritative value (throwaway snippet, same tie-corrected formula): 0.11384629800665813.
        val p = CorrelationStats.mannWhitneyP(present = listOf(5.0, 5.0, 4.0), absent = listOf(4.0, 4.0, 4.0))
        assertEquals(0.11384629800665813, p, 1e-6)
    }

    // MARK: - 3. NaN / ±0.0 at the DaySnapshot boundary (Pitfall 4).

    @Test
    fun daySnapshot_nanDoubleFields_areUnrepresentable() {
        // NaN in a Double? field → canonicalized to null ("not logged"), never entering the read model.
        val snap = DaySnapshot.of(
            day = LocalDate.of(2026, 1, 15),
            activityMinutes = Double.NaN,
            sleepHours = Double.NaN,
        )
        assertNull(snap.activityMinutes, "NaN activityMinutes must normalize to null at the boundary")
        assertNull(snap.sleepHours, "NaN sleepHours must normalize to null at the boundary")

        // ±Infinity is likewise non-finite → null.
        val inf = DaySnapshot.of(
            day = LocalDate.of(2026, 1, 15),
            activityMinutes = Double.POSITIVE_INFINITY,
            sleepHours = Double.NEGATIVE_INFINITY,
        )
        assertNull(inf.activityMinutes)
        assertNull(inf.sleepHours)
    }

    @Test
    fun daySnapshot_negativeZero_canonicalizesToPositiveZero() {
        // -0.0 canonicalizes to 0.0 so boxed-Double equality behaves as Swift IEEE (0.0 == -0.0).
        val day = LocalDate.of(2026, 1, 15)
        val negZero = DaySnapshot.of(day = day, activityMinutes = -0.0, sleepHours = -0.0)
        val posZero = DaySnapshot.of(day = day, activityMinutes = 0.0, sleepHours = 0.0)

        // The stored value's sign bit is normalized: 1.0 / 0.0 == +∞ (not −∞ as it would be for −0.0).
        assertTrue(1.0 / negZero.activityMinutes!! == Double.POSITIVE_INFINITY, "-0.0 must canonicalize to +0.0")
        assertTrue(1.0 / negZero.sleepHours!! == Double.POSITIVE_INFINITY, "-0.0 must canonicalize to +0.0")

        // Data-class-style structural equality holds across the −0.0 / +0.0 construction (Swift IEEE parity).
        assertEquals(posZero, negZero)
        assertEquals(posZero.hashCode(), negZero.hashCode())
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────────
    // MARK: - 4. DST / non-UTC day-boundary divergence (17-08 — LocalDate purity).
    //
    // The remaining half of the numeric-divergence pack: day-boundary math run under a non-UTC default
    // time zone (DST spring-forward / fall-back gaps, the International Date Line). The engines consume
    // `java.time.LocalDate` as the ZONE-FREE day identity (the injected Swift `Calendar` was dropped), so
    // no `Instant` / `ZonedDateTime` / epoch math can leak into day bucketing, period-run detection, or
    // phase windows. These tests PROVE it: the SAME fixture produces BYTE-IDENTICAL engine output whether
    // the JVM default zone is UTC, Europe/Berlin (twice-yearly DST), or Pacific/Apia (a date-line zone
    // that once skipped a whole calendar day). Any leaked epoch/zone math would make one run diverge.
    // ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** The three probe zones: UTC (no DST), Europe/Berlin (DST), Pacific/Apia (date-line / historic
     *  day-skip). Byte-identical output across all three proves LocalDate purity. */
    private val probeZones = listOf("UTC", "Europe/Berlin", "Pacific/Apia")

    /** Run [block] with the JVM default time zone forced to [zoneId], ALWAYS restoring the original zone
     *  in a finally (so one test can never leak a zone into another). */
    private fun <T> withDefaultZone(zoneId: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        return try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zoneId)))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun trendsEngine_outputsAreZoneInvariant_acrossBerlinApiaUtc() {
        val fixture = Fixtures.risingPelvicPainFixture()
        val asOf = Fixtures.day(0)
        val byZone = probeZones.map { zone -> zone to withDefaultZone(zone) { TrendsEngine.trends(from = fixture, asOf = asOf) } }
        val (baseZone, baseline) = byZone.first()
        for ((zone, out) in byZone.drop(1)) {
            assertEquals(baseline, out, "TrendsEngine output diverged between $baseZone and $zone — an epoch/zone leaked past the LocalDate day identity.")
        }
    }

    @Test
    fun cyclePhaseMapper_outputsAreZoneInvariant_acrossBerlinApiaUtc() {
        val fixture = Fixtures.regularCycleFixture()
        val asOf = Fixtures.day(0)
        val byZone = probeZones.map { zone -> zone to withDefaultZone(zone) { CyclePhaseMapper.insights(from = fixture, asOf = asOf) } }
        val (baseZone, baseline) = byZone.first()
        for ((zone, out) in byZone.drop(1)) {
            assertEquals(baseline, out, "CyclePhaseMapper output diverged between $baseZone and $zone — an epoch/zone leaked past the LocalDate day identity.")
        }
    }

    @Test
    fun insightsEngine_compositionIsZoneInvariant_acrossBerlinApiaUtc() {
        // The full public composition (Trends + CyclePhase + Correlation + Recommendations + Flare, gated)
        // over two representative fixtures — a regular cycle (trend + cyclePhase + correlation surface) and a
        // rising-flare history (the flare producer). Byte-identical across all three zones.
        for (fixture in listOf(Fixtures.regularCycleFixture(), Fixtures.risingFlareFixture(), Fixtures.strongAssociationFixture())) {
            val asOf = Fixtures.day(0)
            val byZone = probeZones.map { zone -> zone to withDefaultZone(zone) { InsightsEngine.insights(from = fixture, asOf = asOf) } }
            val (baseZone, baseline) = byZone.first()
            for ((zone, out) in byZone.drop(1)) {
                assertEquals(baseline, out, "InsightsEngine composition diverged between $baseZone and $zone — an epoch/zone leaked past the LocalDate day identity.")
            }
        }
    }
}
