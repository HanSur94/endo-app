// Requirement: AREL-01 (Phase 24, D-02) — the embedded trend chart, drawn into the PDF page's raw
// `android.graphics.Canvas` with the EXACT SAME honest break geometry as the on-screen chart.
//
// REUSE, NOT REBUILD (RESEARCH Pattern 3 / "Don't Hand-Roll"): this bridges the page's native `Canvas` to a
// Compose `DrawScope` via `CanvasDrawScope().draw(...)`, then calls the Phase-22 `ChartPrimitives` over the
// pure `ChartSeriesGrouping.runs` + a fixed-domain `ChartCoordinateMapper`. There is NO second chart
// implementation — the run-segmentation (gap = line BREAK, H1) and the FIXED 0..10 domain (no dual-y, no
// silent normalization, severity-0 at the zero line, H2) are byte-identical to the live chart. Only the ink
// differs: single print-ink BLACK for the clinician-facing static report (no scrub rule, no highlight).
package com.endoapp.app.report

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.endoapp.app.ui.charts.ChartCoordinateMapper
import com.endoapp.app.ui.charts.ChartPrimitives
import com.endoapp.app.ui.charts.ChartSeriesGrouping
import com.endoapp.insights.InsightPoint

/**
 * Draws the reused Phase-22 trend geometry onto a PDF page's raw `android.graphics.Canvas`, in single-ink
 * black, at the given [box]. Honesty-identical to the on-screen chart by construction (same runs, same
 * fixed 0..10 mapper, same primitives).
 */
object ReportChartAdapter {

    /** Single print-ink stroke width for the run lines (pt). */
    private const val STROKE_WIDTH = 1.5f

    /** Single print-ink radius for the per-day points (pt). */
    private const val POINT_RADIUS = 2f

    /**
     * Draw the [points]' honest run-segmented trend into [box] on the native [canvas]. A no-op for an empty
     * series (the caller draws the honest text fallback instead — never a fabricated chart).
     */
    fun drawTrendChart(canvas: android.graphics.Canvas, box: ReportRect, points: List<InsightPoint>) {
        if (points.isEmpty()) return

        val sorted = points.sortedBy { it.date }
        // SAME pure Phase-22 geometry: contiguous-day runs (gap = break, H1) + a FIXED 0..10 domain mapper
        // (no dual-y, severity-0 pinned to the bottom zero line, H2). NOT re-derived here.
        val runs = ChartSeriesGrouping.runs(sorted)
        val mapper = ChartCoordinateMapper(
            startDate = sorted.first().date,
            endDate = sorted.last().date,
            valueMin = 0.0,
            valueMax = 10.0,
            width = box.width(),
            height = box.height(),
        )

        // Bridge the page's native Canvas → a Compose DrawScope so the SAME ChartPrimitives draw unchanged.
        // Translate into the box first so the mapper's box-local pixels land at the right page position.
        canvas.save()
        canvas.translate(box.left, box.top)
        try {
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = androidx.compose.ui.graphics.Canvas(canvas),
                size = Size(box.width(), box.height()),
            ) {
                with(ChartPrimitives) {
                    drawRuns(runs, mapper, strokeColor = Color.Black, strokeWidth = STROKE_WIDTH)
                    drawPoints(sorted, mapper, color = Color.Black, radius = POINT_RADIUS)
                }
            }
        } finally {
            canvas.restore()
        }
    }
}
