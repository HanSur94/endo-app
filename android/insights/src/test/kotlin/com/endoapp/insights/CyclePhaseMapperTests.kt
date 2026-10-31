// EndoInsights (:insights) — the CyclePhaseMapper honesty spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/CyclePhaseMapperTests.swift` (8 cases).
// Assert the estimated-phase mapper's HONESTY GATES — the ethics are the deliverable. A regular cycle
// yields estimated `CYCLE_PHASE` insights at confidence ≤ `MEDIUM` (NEVER `HIGH`); a suppressed, an
// irregular (spread > 9), and an under-sampled (< 2 cycles) stream each yield ZERO `CYCLE_PHASE` insights
// (no phantom phase, the #1 endo pitfall). Output is deterministic/Equatable. Per Pattern 2 `today` is a
// `LocalDate` (the injected Swift `Calendar` is dropped).
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CyclePhaseMapperTests {

    private fun phases(snapshots: List<DaySnapshot>, todayOffset: Int = 0): List<Insight> =
        CyclePhaseMapper.insights(from = snapshots, asOf = Fixtures.day(todayOffset))

    // MARK: Regular cycle → estimated phases at confidence ≤ MEDIUM (never HIGH).

    @Test
    fun regularCycle_emitsCyclePhaseInsights_neverHighConfidence() {
        val insights = phases(Fixtures.regularCycleFixture())
        assertTrue(insights.isNotEmpty(), "A regular, tight cycle must yield estimated cycle-phase insights.")
        for (i in insights) {
            assertEquals(InsightKind.CYCLE_PHASE, i.kind, "Every emitted insight is a CYCLE_PHASE insight.")
            assertTrue(i.confidence <= InsightConfidence.MEDIUM, "Phase confidence must NEVER exceed MEDIUM (D-02).")
            assertEquals(TrendWindow.ALL_TIME, i.window)
            // The signalKey is the value-independent dismissal key (D-09).
            assertTrue(i.signalKey.startsWith("cyclePhase."))
        }
    }

    @Test
    fun regularCycle_copyIsDescriptive_neverCausal() {
        val insights = phases(Fixtures.regularCycleFixture())
        assertTrue(insights.isNotEmpty())
        val banned = listOf("trigger", "causes", " cause", "will ", "because", "due to", "leads to")
        for (i in insights) {
            val text = (i.headline + " " + i.detail).lowercase()
            for (word in banned) {
                assertFalse(text.contains(word), "Causal word '$word' in: ${i.headline} / ${i.detail}")
            }
        }
    }

    // MARK: SC1 — suppressed stream → ZERO CYCLE_PHASE insights (no phantom phase).

    @Test
    fun suppressedStream_yieldsNoPhases() {
        val insights = phases(Fixtures.suppressedNoBleedingFixture())
        assertTrue(insights.isEmpty(), "A suppressed stream must yield NO cycle-phase insights (SC1).")
    }

    // MARK: D-03 — irregular stream (spread > 9) → ZERO CYCLE_PHASE insights.

    @Test
    fun irregularStream_yieldsNoPhases() {
        val insights = phases(Fixtures.irregularCycleFixture())
        assertTrue(insights.isEmpty(), "An irregular cycle (spread > 9) must yield NO cycle-phase insights (D-03).")
    }

    // MARK: < 2 cycles → ZERO CYCLE_PHASE insights (no phantom phase from one bleed).

    @Test
    fun underSampledStream_yieldsNoPhases() {
        val insights = phases(Fixtures.underSampledCycleFixture())
        assertTrue(insights.isEmpty(), "Fewer than 2 cycles must yield NO cycle-phase insights.")
    }

    // MARK: WR-05 — a short cycle whose estimated windows OVERLAP yields ZERO insights (no order-dependent
    // mis-attribution of overlap days).

    @Test
    fun shortOverlappingCycle_yieldsNoPhases() {
        val insights = phases(Fixtures.shortOverlappingCycleFixture())
        assertTrue(
            insights.isEmpty(),
            "Overlapping phase windows must yield NO cycle-phase insights, not an order-dependent mapping (WR-05).",
        )
    }

    @Test
    fun windowsOverlap_isOrderIndependent() {
        // Two windows that share a day are detected as overlapping regardless of their order in the array.
        val d0 = Fixtures.day(0)
        val d4 = Fixtures.day(4)
        val d2 = Fixtures.day(2)
        val d6 = Fixtures.day(6)
        val menstrual = CyclePhaseMapper.PhaseWindow(phase = CyclePhase.MENSTRUAL, lower = d0, upper = d4)
        val ovulatory = CyclePhaseMapper.PhaseWindow(phase = CyclePhase.OVULATORY, lower = d2, upper = d6) // shares days 2…4
        assertTrue(CyclePhaseMapper.windowsOverlap(listOf(menstrual, ovulatory)))
        assertTrue(CyclePhaseMapper.windowsOverlap(listOf(ovulatory, menstrual))) // order-independent
        // Disjoint windows do not overlap.
        val disjoint = CyclePhaseMapper.PhaseWindow(phase = CyclePhase.LUTEAL, lower = Fixtures.day(10), upper = Fixtures.day(14))
        assertFalse(CyclePhaseMapper.windowsOverlap(listOf(menstrual, disjoint)))
    }

    // MARK: Determinism — same fixture + same injected today → exactly equal [Insight].

    @Test
    fun determinism_sameInputs_equalInsights() {
        val a = phases(Fixtures.regularCycleFixture())
        val b = phases(Fixtures.regularCycleFixture())
        assertEquals(a, b)
    }
}
