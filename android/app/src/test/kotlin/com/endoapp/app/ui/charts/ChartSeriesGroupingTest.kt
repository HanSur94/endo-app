package com.endoapp.app.ui.charts

import com.endoapp.insights.InsightPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins chart-honesty invariants H1 (gaps stay gaps) and H2 (absence ≠ zero) on the PURE data-prep layer
 * — WITHOUT rendering — plus the [ChartCoordinateMapper] geometry that carries H2/H4 into pixels.
 *
 * The Android mirror of the iOS `ChartSeriesGroupingTests`. `ChartSeriesGrouping` is the seam that
 * turns sparse logged days into DISTINCT runs so the rendered line breaks at every gap and never
 * interpolates a straight segment through unlogged days (RESEARCH Pitfall 1 / PITFALLS #14). An unlogged
 * day is NEVER fabricated as `value = 0`; a literal logged severity-0 day stays a real point.
 */
class ChartSeriesGroupingTest {

    private fun d(day: Int) = LocalDate.of(2026, 1, day)
    private fun p(day: Int, value: Double) = InsightPoint(date = d(day), value = value)

    // --- H1: gaps stay gaps (line BREAKS) ---

    @Test
    fun twoRunsSeparatedByAGap_yieldTwoRuns_soTheLineBreaks() {
        // Jan 1,2,3 … GAP (4,5 unlogged) … Jan 6,7 — one break, two runs.
        val runs = ChartSeriesGrouping.runs(
            listOf(p(1, 5.0), p(2, 6.0), p(3, 4.0), p(6, 7.0), p(7, 8.0)),
        )
        assertEquals("a single gap splits the series into exactly two runs (one line break)", 2, runs.size)
        assertEquals(listOf(d(1), d(2), d(3)), runs[0].map { it.date })
        assertEquals(listOf(d(6), d(7)), runs[1].map { it.date })
    }

    @Test
    fun consecutiveDays_stayOneRun_noSpuriousBreak() {
        val runs = ChartSeriesGrouping.runs(listOf(p(1, 1.0), p(2, 2.0), p(3, 3.0), p(4, 4.0)))
        assertEquals("four consecutive days are ONE contiguous run", 1, runs.size)
        assertEquals(4, runs[0].size)
    }

    @Test
    fun multipleGaps_produceARunPerContiguousSpan() {
        // 1 | gap | 3,4 | gap | 8 — three runs.
        val runs = ChartSeriesGrouping.runs(listOf(p(1, 1.0), p(3, 3.0), p(4, 4.0), p(8, 8.0)))
        assertEquals(3, runs.size)
        assertEquals(listOf(d(1)), runs[0].map { it.date })
        assertEquals(listOf(d(3), d(4)), runs[1].map { it.date })
        assertEquals(listOf(d(8)), runs[2].map { it.date })
    }

    // --- H2: absence ≠ zero ---

    @Test
    fun loggedSeverityZeroDay_staysARealPoint_neverDroppedNeverAGap() {
        // Jan 1 (sev 3), Jan 2 (sev 0 — REAL), Jan 3 (sev 5): all consecutive → one run, three points,
        // and the middle real 0 is retained (not treated as a gap, not dropped).
        val runs = ChartSeriesGrouping.runs(listOf(p(1, 3.0), p(2, 0.0), p(3, 5.0)))
        assertEquals(1, runs.size)
        assertEquals(3, runs[0].size)
        assertEquals("the literal severity-0 day is a real point in the run", 0.0, runs[0][1].value, 0.0)
        assertTrue("the real 0 is present on its exact day", runs[0].any { it.date == d(2) && it.value == 0.0 })
    }

    @Test
    fun aGap_neverFabricatesAZeroFillPoint() {
        val runs = ChartSeriesGrouping.runs(listOf(p(1, 5.0), p(4, 6.0)))
        val allDates = runs.flatten().map { it.date }
        assertEquals("only the two logged days exist — no zero-fill for Jan 2/3", listOf(d(1), d(4)), allDates)
    }

    // --- Robustness ---

    @Test
    fun isolatedLoggedDay_isASinglePointRun() {
        val runs = ChartSeriesGrouping.runs(listOf(p(5, 7.0)))
        assertEquals(1, runs.size)
        assertEquals(1, runs[0].size)
        assertEquals(d(5), runs[0][0].date)
    }

    @Test
    fun unsortedInput_isSortedBeforeSegmenting() {
        val runs = ChartSeriesGrouping.runs(listOf(p(3, 3.0), p(1, 1.0), p(2, 2.0)))
        assertEquals("unsorted consecutive days collapse into one run after sorting", 1, runs.size)
        assertEquals(listOf(d(1), d(2), d(3)), runs[0].map { it.date })
    }

    @Test
    fun emptyInput_yieldsNoRuns_notACrash() {
        assertTrue(ChartSeriesGrouping.runs(emptyList()).isEmpty())
    }

    // --- H2/H4 carried into pixels: the mapper places severity 0 at the bottom zero line ---

    @Test
    fun mapper_severityZero_mapsToBottomZeroLine_notOffChart() {
        val mapper = ChartCoordinateMapper(
            startDate = d(1), endDate = d(11), valueMin = 0.0, valueMax = 10.0,
            width = 300f, height = 200f,
        )
        // value 0 (domain floor) → BOTTOM of the plot (y == height), a real point at the zero line.
        assertEquals(200f, mapper.y(0.0), 0.001f)
        // value 10 (domain ceiling) → TOP (y == 0).
        assertEquals(0f, mapper.y(10.0), 0.001f)
        // value 5 (midpoint) → vertical centre.
        assertEquals(100f, mapper.y(5.0), 0.001f)
    }

    @Test
    fun mapper_dateAxis_isAbsolute_startAtLeftEndAtRight() {
        val mapper = ChartCoordinateMapper(
            startDate = d(1), endDate = d(11), valueMin = 0.0, valueMax = 10.0,
            width = 300f, height = 200f,
        )
        assertEquals(0f, mapper.x(d(1)), 0.001f) // window start at the left edge
        assertEquals(300f, mapper.x(d(11)), 0.001f) // asOf at the right edge
        assertEquals(150f, mapper.x(d(6)), 0.001f) // the midpoint day at the centre
    }

    @Test
    fun mapper_dateAt_snapsRawXToTheNearestExactDay() {
        val mapper = ChartCoordinateMapper(
            startDate = d(1), endDate = d(11), valueMin = 0.0, valueMax = 10.0,
            width = 300f, height = 200f,
        )
        assertEquals(d(1), mapper.dateAt(0f))
        assertEquals(d(11), mapper.dateAt(300f))
        assertEquals(d(6), mapper.dateAt(150f))
        // A raw x that is clamped past the right edge still snaps to the last day, never past it.
        assertEquals(d(11), mapper.dateAt(9999f))
    }
}
