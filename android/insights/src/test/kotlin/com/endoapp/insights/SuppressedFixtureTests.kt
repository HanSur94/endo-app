// EndoInsights (:insights) — SC3 keystone: no bleeding/cycle trend under suppression (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/SuppressedFixtureTests.swift` (2 cases).
// The single most important honesty proof of the engine, mirroring `CycleMath.summarize`'s
// `if isSuppressed { return .suppressed }` short-circuit. Over a `[DaySnapshot]` where every day is
// `suppressed == true` and `flow == null` for MONTHS, `TrendsEngine.trends` MUST emit:
//   • NO Insight whose headline mentions bleeding/cycle/period, AND
//   • NO Insight with `kind == CYCLE_PHASE`.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuppressedFixtureTests {

    private fun trends(snapshots: List<DaySnapshot>): List<Insight> =
        TrendsEngine.trends(from = snapshots, asOf = Fixtures.day(0))

    @Test
    fun suppressedNoBleeding_emitsNoBleedingOrCycleTrend() {
        val insights = trends(Fixtures.suppressedNoBleedingFixture(monthsBack = 4))

        // No headline mentions bleeding/period/cycle.
        assertFalse(insights.any { it.headline.contains("bleed", ignoreCase = true) })
        assertFalse(insights.any { it.headline.contains("period", ignoreCase = true) })
        assertFalse(insights.any { it.headline.contains("cycle", ignoreCase = true) })

        // No CYCLE_PHASE kind (TrendsEngine emits only TREND anyway; this guards the gate explicitly).
        assertFalse(insights.any { it.kind == InsightKind.CYCLE_PHASE })
    }

    @Test
    fun suppressedNoBleeding_stillEmitsNonBleedingTrends() {
        // The fixture logs pelvicPain + stress every day, so NON-bleeding descriptive trends are fine —
        // suppression only gates the bleeding/cycle trend, not symptom/factor trends.
        val insights = trends(Fixtures.suppressedNoBleedingFixture(monthsBack = 4))
        assertTrue(insights.any { it.headline.contains("pelvic", ignoreCase = true) })
    }
}
