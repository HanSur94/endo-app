// EndoInsights (:insights) — the FlareEngine honesty/absence spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift` (6 cases). Asserts the
// FLARE producer's two highest-stakes HONESTY invariants:
//   1. ABSENCE-BY-TEST (load-bearing, D-05/D-08/SC3): a suppressed / flat-no-rise / insufficient-evidence user
//      gets ZERO non-notice flare warning. The engine must NEVER fabricate a speculative warning.
//   2. A RISING-WITH-EVIDENCE user (>=21 days of personal history + a recent rising-severity spine) surfaces
//      EXACTLY ONE bounded (<= MEDIUM), uncertainty-first, NON-alarmist flare warning.
//
// A flare composes already-gated signals (D-01: compose, NEVER re-derive). Per Pattern 2 `today` is an
// injected `LocalDate`. The forbidden-term scan uses inline banned-substring lists (causal ∪ imperative ∪
// alarmist) — the durable `ForbiddenTerms` scan lands in 17-08.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlareEngineTests {

    /** The producer entry point. Mirrors `RecommendationsEngine.recommendations` signature (Calendar dropped). */
    private fun flareWarning(snapshots: List<DaySnapshot>, todayOffset: Int = 0): List<Insight> =
        FlareEngine.flareWarning(from = snapshots, asOf = Fixtures.day(todayOffset))

    /** Only the `.flare` Insights (the engine routes through the same gate as every kind). */
    private fun flareWarnings(snapshots: List<DaySnapshot>): List<Insight> =
        flareWarning(snapshots).filter { it.kind == InsightKind.FLARE }

    // The forbidden CAUSAL terms a flare warning must NEVER contain (mirrors the package non-causal posture).
    private val forbiddenCausalTerms = listOf(
        "cause", "causes", "trigger", "triggers", "because", "will ", "leads to", "linked to",
        "worsen", "improve", "reduce", "relieve", "aggravate",
    )

    // The forbidden IMPERATIVE substrings (D-04 directive verbs) a flare warning must NEVER contain.
    private val forbiddenImperatives = listOf(
        "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut",
    )

    // The forbidden ALARMIST / DIAGNOSTIC / CERTAINTY substrings — note "warning" is DELIBERATELY ABSENT (the
    // section header is "Flare early-warning", D-07).
    private val forbiddenAlarmist = listOf(
        "attack", "alarm", "alert", "emergency", "urgent", "danger",
        "diagnos", "predict", "imminent", "certain", "definitely",
        "episode", "will flare", "guarantee",
    )

    private fun assertNoForbiddenTerms(insight: Insight) {
        val surface = (insight.headline + " " + insight.detail).lowercase()
        for (term in forbiddenCausalTerms) {
            assertFalse(surface.contains(term), "Flare copy must contain NO causal term \"$term\": \"$surface\".")
        }
        for (verb in forbiddenImperatives) {
            assertFalse(surface.contains(verb), "Flare copy must contain NO imperative substring \"$verb\": \"$surface\".")
        }
        for (term in forbiddenAlarmist) {
            assertFalse(surface.contains(term), "Flare copy must contain NO alarmist/diagnostic term \"$term\": \"$surface\".")
        }
    }

    // MARK: - Absence: suppressed / flat-no-rise → ZERO flare warning (the load-bearing honesty contract).

    @Test
    fun suppressedUser_yieldsZeroFlareWarning() {
        // A suppressed user (insight derivation turned OFF) must NEVER get a fabricated flare warning — the
        // most caution-sensitive surface is the MOST conservative (SC3 / D-08).
        assertTrue(
            flareWarnings(Fixtures.suppressedNoBleedingFixture()).isEmpty(),
            "A suppressed user yields ZERO flare warning (never fabricated).",
        )
    }

    @Test
    fun flatNoRiseUser_yieldsZeroFlareWarning() {
        // Substantial history (>=21 days) but a STEADY severity with NO recent rise → no rising spine → the
        // engine must withhold entirely (a flat history is not a warning).
        assertTrue(
            flareWarnings(Fixtures.flatNoRiseFixture()).isEmpty(),
            "A flat / no-rise user yields ZERO flare warning (no rising spine to honestly warn about).",
        )
    }

    // MARK: - Insufficiency cusp (A3): a recent rise but < 21 days of evidence → [] OR one notice, NO warning.

    @Test
    fun underEvidenceRise_yieldsNoNonNoticeWarning() {
        // A recent rise IS present, but the history is below the `flareMinSample == 21` floor. The honest answer
        // is EITHER nothing OR a single "not enough data yet" insufficiency notice — NEVER a non-notice warning.
        val insights = flareWarnings(Fixtures.risingButUnderEvidenceFixture())
        val nonNoticeWarnings = insights.filter { !it.isInsufficiencyNotice }
        assertTrue(
            nonNoticeWarnings.isEmpty(),
            "An under-21-evidence rise must surface NO non-notice flare warning (only [] or one insufficiency notice).",
        )
        // If anything surfaces at all, it is at most ONE honest insufficiency notice (never a warning pile).
        assertTrue(
            insights.size <= 1,
            "An under-evidence rise surfaces at most ONE honest insufficiency notice, never multiple cards.",
        )
        insights.firstOrNull()?.let { notice ->
            assertTrue(
                notice.isInsufficiencyNotice,
                "The only card an under-evidence rise may surface is an honest insufficiency notice.",
            )
        }
    }

    // MARK: - Surfaced: a rising-with->=21-evidence user → exactly one bounded <= MEDIUM flare warning.

    @Test
    fun risingWithEvidence_surfacesOneBoundedWarning() {
        val warnings = flareWarnings(Fixtures.risingFlareFixture())
        assertEquals(1, warnings.size, "A rising pattern over >=21 days of evidence surfaces EXACTLY ONE flare warning.")
        val warning = warnings.first()
        assertEquals(InsightKind.FLARE, warning.kind)
        assertEquals(TrendWindow.ALL_TIME, warning.window, "A flare warning is window-independent (mirror recommendations/correlations).")
        assertEquals("flare.warning", warning.signalKey, "The flare warning uses the stable, value-independent signalKey \"flare.warning\".")
        // Bounded, uncertainty-first (D-03): never high-confidence, never below the gate floor.
        assertTrue(warning.confidence <= InsightConfidence.MEDIUM, "A flare warning is bounded at <= MEDIUM — uncertainty-first, never a high-confidence alarm (D-03).")
        assertTrue(warning.confidence >= InsightConfidence.LOW, "A surfaced flare warning is at least LOW (gate-eligible); below that it is withheld.")
        assertTrue(warning.points.isEmpty(), "A flare warning is a words-only card (no chart points).")
        assertTrue(warning.sampleSize >= 21, "A flare warning inherits the >=21-day evidence floor (minSample(.flare) == 21).")
        assertFalse(warning.isInsufficiencyNotice, "A real rising-with-evidence warning is NOT an insufficiency notice.")
        assertNoForbiddenTerms(warning)
    }

    // MARK: - WR-03: cycle-phase proximity counts logged-SEVERITY days, never bare presence.

    @Test
    fun proximity_ignoresPresenceWithoutSeverity() {
        val withoutExtraDays = flareWarnings(Fixtures.flareProximityFixture(extraHigherPhaseDays = false))
        val withSeverityNilDays = flareWarnings(Fixtures.flareProximityFixture(extraHigherPhaseDays = true))

        // The severity-nil extra days are invisible to the logged-severity-counting proximity signal, so the
        // engine emits exactly the same warning (same confidence, same copy) in both cases.
        assertEquals(
            withoutExtraDays, withSeverityNilDays,
            "WR-03: cycle-phase proximity must count logged-severity days, not bare presence — adding " +
                "present-without-severity dominant-symptom days must not change the flare output " +
                "(without=${withoutExtraDays.map { it.confidence }}, withSeverityNil=${withSeverityNilDays.map { it.confidence }}).",
        )

        // Both surface exactly one warning, bounded <= MEDIUM (the cap and spine hold regardless).
        val warning = withoutExtraDays.first()
        assertTrue(warning.confidence <= InsightConfidence.MEDIUM)
        assertEquals(1, withSeverityNilDays.size)
    }

    // MARK: - Determinism: the same fixture twice yields exactly equal `[Insight]`.

    @Test
    fun output_isDeterministic() {
        val first = flareWarnings(Fixtures.risingFlareFixture())
        val second = flareWarnings(Fixtures.risingFlareFixture())
        assertEquals(first, second, "Same inputs → exactly equal flare [Insight] (Equatable, no RNG/clock).")
    }
}
