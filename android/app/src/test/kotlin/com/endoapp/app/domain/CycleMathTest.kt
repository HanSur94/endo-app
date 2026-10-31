package com.endoapp.app.domain

import com.endoapp.insights.InsightConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure-JVM parity suite for [CycleMath.summarize] — the three honest cycle states (mirrors the iOS
 * `CycleMathTests` / `CycleSummaryTests`), pinning T-24-05 (no phantom cycle):
 *   • suppressed  → the off-state.
 *   • < 2 cycles  → not-enough-data (no fabricated cycleDay).
 *   • regular     → a derived cycleDay + a next-period RANGE + confidence CAPPED at MEDIUM.
 * No Room, no clock: `today` is injected, all date math is `LocalDate`.
 */
class CycleMathTest {

    private val base: LocalDate = LocalDate.of(2025, 1, 1)

    @Test
    fun suppressed_returnsOffState() {
        val s = CycleMath.summarize(
            bleedingDays = listOf(base, base.plusDays(28)),
            isSuppressed = true,
            today = base.plusDays(30),
        )
        assertEquals(CycleSummary.suppressed, s)
        assertTrue(s.isSuppressed)
        assertNull(s.cycleDay)
        assertNull(s.nextPeriod)
        assertFalse(s.hasEnoughData)
        assertEquals(InsightConfidence.NONE, s.confidence)
    }

    @Test
    fun singleRun_returnsNotEnoughData() {
        // One period (two consecutive bleed days = one run) → 0 start-to-start gaps → not meaningful.
        val s = CycleMath.summarize(
            bleedingDays = listOf(base, base.plusDays(1)),
            isSuppressed = false,
            today = base.plusDays(5),
        )
        assertEquals(CycleSummary.notEnoughData, s)
        assertFalse(s.hasEnoughData)
        assertNull(s.cycleDay)
        assertNull(s.nextPeriod)
    }

    @Test
    fun regularHistory_derivesCycleDayAndRange_confidenceNeverHigh() {
        // Runs at 0, 28, 56, 84 (each a 2-day period) → gaps [28,28,28], median 28, spread 0, 3 cycles.
        val bleedingDays = listOf(0L, 28L, 56L, 84L).flatMap { start ->
            listOf(base.plusDays(start), base.plusDays(start + 1))
        }
        val lastStart = base.plusDays(84)
        val today = lastStart.plusDays(5)

        val s = CycleMath.summarize(bleedingDays = bleedingDays, isSuppressed = false, today = today)

        assertTrue(s.hasEnoughData)
        assertFalse(s.isSuppressed)
        assertEquals(6, s.cycleDay) // 5 days since last start, +1
        val range = s.nextPeriod
        assertNotNull(range)
        // A next-period date RANGE (never a single date): start strictly before end.
        assertTrue("range must be non-degenerate", range!!.start < range.endInclusive)
        // Centered on lastStart + median (28); MEDIUM pad = 3, spread 0 → ±3.
        assertEquals(lastStart.plusDays(28 - 3), range.start)
        assertEquals(lastStart.plusDays(28 + 3), range.endInclusive)
        // A cycle estimate is NEVER HIGH (D-02).
        assertTrue("confidence must never be HIGH", s.confidence <= InsightConfidence.MEDIUM)
        assertEquals(InsightConfidence.MEDIUM, s.confidence)
    }

    @Test
    fun exactlyTwoCycles_isEnoughData() {
        // Boundary: starts at 0, 28, 56 → gaps [28,28] (exactly 2) → derived, MEDIUM (tight).
        val bleedingDays = listOf(base, base.plusDays(28), base.plusDays(56))
        val s = CycleMath.summarize(
            bleedingDays = bleedingDays,
            isSuppressed = false,
            today = base.plusDays(60),
        )
        assertTrue(s.hasEnoughData)
        assertNotNull(s.cycleDay)
        assertEquals(InsightConfidence.MEDIUM, s.confidence)
    }

    @Test
    fun irregularHistory_derivesWithLowConfidence() {
        // Single-day runs at 0, 21, 49, 90 → gaps [21,28,41], spread 41-21 = 20 (> 9) → LOW, per iOS
        // CycleMath (which derives even for wide spread, capped low — never a phantom, just uncertain).
        val bleedingDays = listOf(base, base.plusDays(21), base.plusDays(49), base.plusDays(90))
        val s = CycleMath.summarize(
            bleedingDays = bleedingDays,
            isSuppressed = false,
            today = base.plusDays(100),
        )
        assertTrue(s.hasEnoughData)
        assertEquals(InsightConfidence.LOW, s.confidence)
        assertTrue(s.confidence <= InsightConfidence.MEDIUM)
    }

    @Test
    fun emptyHistory_returnsNotEnoughData() {
        val s = CycleMath.summarize(bleedingDays = emptyList(), isSuppressed = false, today = base)
        assertEquals(CycleSummary.notEnoughData, s)
    }
}
