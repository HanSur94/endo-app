// Requirement: INST-01 / D-06 — the make-or-break honest-gap helper for the Swift Charts trend chart.
//
// Swift Charts connects EVERY `LineMark` that shares a `series` value, regardless of date gaps
// (RESEARCH Pitfall 1) — so a single series over sparse data draws a straight interpolated line
// through days the user never logged, fabricating "data". The fix is to group contiguous logged-day
// runs into DISTINCT `series` values: the line then BREAKS at every gap.
//
// This helper is deliberately PURE Foundation (NO SwiftUI / NO Charts import) so the
// "absence ≠ 0" guarantee is provable in a unit test WITHOUT rendering (ChartSeriesGroupingTests):
//   • An unlogged day is NEVER fabricated as `value: 0` — it is simply absent from every run.
//   • A literal logged `value: 0` (severity 0) IS retained as a point (distinct from a gap).
//   • A single logged day forms a run of one (so it still draws as a PointMark, never dropped).
import Foundation
import EndoInsights

/// Groups an ordered set of logged-day `InsightPoint`s into contiguous calendar-day runs so a Swift
/// Charts line breaks honestly at each gap. Pure value-in / value-out — no view dependency.
enum ChartSeriesGrouping {

    /// A maximal gap-free span of consecutive logged calendar days. Each run becomes one distinct
    /// `series` value in the chart, so the line connects WITHIN a run and breaks BETWEEN runs.
    struct Run: Identifiable, Equatable {
        /// Stable within a single grouping pass (the run's ordinal). Used only as the chart `series`
        /// discriminator — distinct per run is all Swift Charts needs to break the line.
        let id: Int
        /// The logged points in this run, in ascending date order. Never contains a fabricated zero;
        /// a literal logged severity-0 day is a real point and stays.
        let points: [InsightPoint]
    }

    /// Split `points` into contiguous calendar-day runs using `calendar` for day-adjacency.
    ///
    /// Two consecutive (date-sorted) points start a NEW run whenever they are not adjacent calendar
    /// days (i.e. there is at least one unlogged day between them). The helper NEVER inserts a
    /// `value: 0` for a missing day — absence is represented purely by the run boundary.
    ///
    /// - Parameters:
    ///   - points: logged days (one per day). Need not be pre-sorted; sorted here for robustness.
    ///   - calendar: the calendar used for day-boundary + adjacency math (inject a fixed UTC calendar
    ///     in tests for determinism).
    /// - Returns: runs ordered by date, each a maximal gap-free span; empty if `points` is empty.
    static func runs(from points: [InsightPoint], calendar: Calendar) -> [Run] {
        guard !points.isEmpty else { return [] }

        // Sort by date so adjacency is well-defined regardless of producer ordering.
        let sorted = points.sorted { $0.date < $1.date }

        var runs: [Run] = []
        var current: [InsightPoint] = [sorted[0]]
        var runIndex = 0

        for point in sorted.dropFirst() {
            let previous = current[current.count - 1]
            if areAdjacentDays(previous.date, point.date, calendar: calendar) {
                // Same contiguous run — extend it (this also keeps two logged severity-0 days together).
                current.append(point)
            } else {
                // A gap (one or more unlogged days) — close the run and start a new one. No zero-fill.
                runs.append(Run(id: runIndex, points: current))
                runIndex += 1
                current = [point]
            }
        }
        runs.append(Run(id: runIndex, points: current))
        return runs
    }

    /// `true` iff `later` falls on the calendar day immediately after `earlier`'s day. Same-day or any
    /// larger separation is NOT adjacent (a repeated same-day point would not extend the run; producers
    /// emit one point per day). Uses `dateComponents` day-difference, which is DST-safe.
    private static func areAdjacentDays(_ earlier: Date, _ later: Date, calendar: Calendar) -> Bool {
        let startEarlier = calendar.startOfDay(for: earlier)
        let startLater = calendar.startOfDay(for: later)
        let dayDelta = calendar.dateComponents([.day], from: startEarlier, to: startLater).day
        return dayDelta == 1
    }
}
