// The make-or-break honest-gap helper for the Compose `Canvas` trend chart — the Android mirror of the
// iOS `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` (STATE `[08-03]`).
//
// A single connected line over sparse data draws a straight interpolated segment through days the user
// never logged, FABRICATING "data" (RESEARCH Pitfall 1 / PITFALLS #14). The fix is to group contiguous
// logged-day runs into DISTINCT paths: the rendered line then BREAKS at every gap (invariant H1).
//
// This helper is deliberately PURE (NO Compose, NO android./androidx. import) so the "absence ≠ 0"
// guarantee (H2) is provable in a unit test WITHOUT rendering (`ChartSeriesGroupingTest`):
//   • An unlogged day is NEVER fabricated as `value = 0` — it is simply absent from every run.
//   • A literal logged `value = 0` (severity 0) IS retained as a real point (distinct from a gap).
//   • A single logged day forms a run of one (so it still draws as a dot, never dropped).
package com.endoapp.app.ui.charts

import com.endoapp.insights.InsightPoint

/**
 * Groups ordered logged-day [InsightPoint]s into contiguous calendar-day runs so a `Canvas` line breaks
 * honestly at each gap. Pure value-in / value-out — no view dependency, no `value = 0` fabrication.
 */
object ChartSeriesGrouping {

    /**
     * Split [points] into contiguous calendar-day runs. A new run starts whenever the gap to the previous
     * (date-sorted) point is MORE than one calendar day (`prev.date.plusDays(1) != next.date`) — i.e.
     * there is at least one unlogged day between them. The helper NEVER inserts a `value = 0` for a
     * missing day; absence is represented purely by the run boundary (H1). A literal logged severity-0
     * day stays a real point in its run (H2). An isolated logged day is a single-point run.
     *
     * @param points logged days (one per day). Need not be pre-sorted; sorted here for robustness.
     * @return runs ordered by date, each a maximal gap-free span; empty if [points] is empty.
     */
    fun runs(points: List<InsightPoint>): List<List<InsightPoint>> {
        if (points.isEmpty()) return emptyList()

        // Sort by date so adjacency is well-defined regardless of producer ordering.
        val sorted = points.sortedBy { it.date }

        val runs = mutableListOf<List<InsightPoint>>()
        var current = mutableListOf(sorted.first())

        for (point in sorted.drop(1)) {
            val previous = current.last()
            if (previous.date.plusDays(1) == point.date) {
                // Same contiguous run — extend it (this also keeps two logged severity-0 days together).
                current.add(point)
            } else {
                // A gap (one or more unlogged days), OR a same-day duplicate — close the run, start anew.
                // No zero-fill: the break IS the gap.
                runs.add(current)
                current = mutableListOf(point)
            }
        }
        runs.add(current)
        return runs
    }
}
