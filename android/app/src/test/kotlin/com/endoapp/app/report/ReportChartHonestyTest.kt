// Requirement: AREL-01 (Phase 24, D-02) — the embedded PDF chart carries the SAME honesty as the on-screen
// chart because it reuses the SAME Phase-22 geometry (`ChartSeriesGrouping.runs` + `ChartCoordinateMapper`).
// This pins the chart's INPUTS on the pure JVM lane: a gapped series stays a line BREAK (H1, ≥2 runs, never
// interpolation) and a logged severity-0 pins to the bottom zero line on the FIXED 0..10 domain (H2, no
// dual-y). The drawing itself (native `Canvas`/`CanvasDrawScope`) is exercised by the instrumented smoke.
package com.endoapp.app.report

import com.endoapp.app.ui.charts.ChartCoordinateMapper
import com.endoapp.app.ui.charts.ChartSeriesGrouping
import com.endoapp.insights.InsightPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportChartHonestyTest {

    private fun d(day: Int) = LocalDate.of(2026, 6, day)

    @Test
    fun gappedSeries_yieldsAtLeastTwoRuns_aLineBreakNotAnInterpolatedSegment() {
        val points = listOf(
            InsightPoint(d(1), 3.0),
            InsightPoint(d(2), 0.0), // a logged severity-0 — a REAL point in the first run (H2)
            InsightPoint(d(6), 5.0), // days 3–5 unlogged → a NEW run (a break, not interpolation) (H1)
        )
        val runs = ChartSeriesGrouping.runs(points)
        assertTrue("a gap is a line break, never a single interpolated segment", runs.size >= 2)
        // The break is between the contiguous [d1, d2] run and the isolated [d6] run.
        assertEquals(2, runs.size)
        assertEquals(2, runs.first().size)
        assertEquals(1, runs.last().size)
    }

    @Test
    fun mapper_pinsSeverityZeroToTheBottomZeroLine_onTheFixed0to10Domain() {
        // The SAME mapper the report chart builds (fixed 0..10, no data-driven min/max, no dual-y).
        val mapper = ChartCoordinateMapper(
            startDate = d(1), endDate = d(6),
            valueMin = 0.0, valueMax = 10.0,
            width = 100f, height = 200f,
        )
        assertEquals("severity 0 sits at the bottom zero line", 200f, mapper.y(0.0), 0.001f)
        assertEquals("severity 10 sits at the top", 0f, mapper.y(10.0), 0.001f)
        assertEquals("severity 5 sits at the middle", 100f, mapper.y(5.0), 0.001f)
    }

    @Test
    fun loggedSeverityZeroDay_staysARealPointInItsRun_notDroppedAsAGap() {
        val points = listOf(InsightPoint(d(3), 0.0)) // absence ≠ 0: a logged 0 is a real point
        val runs = ChartSeriesGrouping.runs(points)
        assertEquals(1, runs.size)
        assertEquals("the logged severity-0 day is retained", 0.0, runs.single().single().value, 0.0)
    }
}
