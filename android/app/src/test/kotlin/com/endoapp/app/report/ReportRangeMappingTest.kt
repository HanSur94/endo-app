package com.endoapp.app.report

import com.endoapp.insights.TrendWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Kotlin port of the iOS `ReportRangeMappingTests` (Phase 24, A1). Pure-JVM — no store, no clock:
 * every `asOf`/`from`/`to` is injected, so the mapping is deterministic.
 *
 * Proves the three honesty rules of the range mapping:
 *   • preset → interval spans (LAST30/LAST90 anchored at `asOf`; CUSTOM spans the two picked days),
 *   • the window-⊆-range rule ([ReportRangeMapping.enginewindowsInRange] excludes a window WIDER than
 *     the range, widest-first order),
 *   • the invalid-range guard (a custom `to` before `from` is rejected; presets always valid).
 */
class ReportRangeMappingTest {

    private val asOf = LocalDate.of(2026, 6, 9)

    // --- preset → interval ---

    @Test
    fun last30_spans30InclusiveDaysEndingAtAsOf() {
        val range = ReportRangeMapping.interval(RangePreset.LAST30, from = asOf, to = asOf, asOf = asOf)
        assertEquals("LAST30 end is asOf", asOf, range.end)
        assertEquals("LAST30 start is asOf - 30 days", asOf.minusDays(30), range.start)
    }

    @Test
    fun last90_spans90InclusiveDaysEndingAtAsOf() {
        val range = ReportRangeMapping.interval(RangePreset.LAST90, from = asOf, to = asOf, asOf = asOf)
        assertEquals(asOf, range.end)
        assertEquals(asOf.minusDays(90), range.start)
    }

    @Test
    fun custom_spansTheTwoPickedDaysInclusive() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 5, 20)
        val range = ReportRangeMapping.interval(RangePreset.CUSTOM, from = from, to = to, asOf = asOf)
        assertEquals(from, range.start)
        assertEquals(to, range.end)
    }

    @Test
    fun custom_isNeverInverted_evenWhenToPrecedesFrom() {
        // The mapping is defensive: even an inverted pair yields an ordered (non-inverted) range.
        val from = LocalDate.of(2026, 5, 20)
        val to = LocalDate.of(2026, 5, 1)
        val range = ReportRangeMapping.interval(RangePreset.CUSTOM, from = from, to = to, asOf = asOf)
        assertEquals(LocalDate.of(2026, 5, 1), range.start)
        assertEquals(LocalDate.of(2026, 5, 20), range.end)
    }

    // --- window ⊆ range (enginewindowsInRange) ---

    @Test
    fun engineWindows_forA46DayRange_areLast30ThenLast7PlusAllTime_excludesLast90() {
        // A 46-day inclusive range: [May 1 … Jun 15] = 46 days. LAST90 (90d) is WIDER than the range and
        // must be excluded; LAST30 + LAST7 fit; ALL_TIME always fits. Widest-first order.
        val range = ReportRange(start = LocalDate.of(2026, 5, 1), end = LocalDate.of(2026, 6, 15))
        val windows = ReportRangeMapping.enginewindowsInRange(range)

        assertFalse("LAST90 is wider than a 46-day range — excluded", windows.contains(TrendWindow.LAST90))
        assertTrue(windows.contains(TrendWindow.LAST30))
        assertTrue(windows.contains(TrendWindow.LAST7))
        assertTrue("ALL_TIME ⊆ the range by definition", windows.contains(TrendWindow.ALL_TIME))
        // Widest-first: LAST30 before LAST7; ALL_TIME (no fixed span) sorts last.
        assertEquals(
            listOf(TrendWindow.LAST30, TrendWindow.LAST7, TrendWindow.ALL_TIME),
            windows,
        )
    }

    @Test
    fun engineWindows_forALast90Range_includeEveryFixedWindow_widestFirst() {
        val range = ReportRangeMapping.interval(RangePreset.LAST90, from = asOf, to = asOf, asOf = asOf)
        val windows = ReportRangeMapping.enginewindowsInRange(range)
        // 91 inclusive days ≥ 90, so LAST90 fits too; widest-first with ALL_TIME last.
        assertEquals(
            listOf(TrendWindow.LAST90, TrendWindow.LAST30, TrendWindow.LAST7, TrendWindow.ALL_TIME),
            windows,
        )
    }

    @Test
    fun engineWindows_forATinyRange_onlyAllTimeFits() {
        val range = ReportRange(start = asOf, end = asOf) // 1 inclusive day
        val windows = ReportRangeMapping.enginewindowsInRange(range)
        assertEquals(listOf(TrendWindow.ALL_TIME), windows)
    }

    // --- invalid-range guard ---

    @Test
    fun isValid_isFalse_whenCustomToPrecedesFrom() {
        val from = LocalDate.of(2026, 5, 20)
        val to = LocalDate.of(2026, 5, 1)
        assertFalse(ReportRangeMapping.isValid(RangePreset.CUSTOM, from = from, to = to))
    }

    @Test
    fun isValid_isTrue_forEqualCustomDatesAndAllPresets() {
        val day = LocalDate.of(2026, 5, 10)
        assertTrue("equal custom dates are valid", ReportRangeMapping.isValid(RangePreset.CUSTOM, day, day))
        assertTrue(ReportRangeMapping.isValid(RangePreset.CUSTOM, day, day.plusDays(1)))
        // Presets are always valid regardless of from/to.
        assertTrue(ReportRangeMapping.isValid(RangePreset.LAST30, day.plusDays(5), day))
        assertTrue(ReportRangeMapping.isValid(RangePreset.LAST90, day.plusDays(5), day))
    }
}
