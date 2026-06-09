// Requirement: INST-01 / D-06 — PROVE "absence ≠ 0" for the Swift Charts trend chart WITHOUT
// rendering (08-VALIDATION "Swift Charts gap logic" layer; RESEARCH Pitfall 1).
//
// `ChartSeriesGrouping` is a PURE Foundation helper: it splits an ordered `[InsightPoint]` (one per
// logged day) into contiguous calendar-day runs. The Swift Charts line BREAKS between runs because
// each run gets a distinct `series` value — so a multi-day gap is an actual gap, never a straight
// interpolated line and never a fabricated `value: 0`. These tests assert the grouping (and the
// no-zero-fabrication guarantee) entirely off-screen.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Chart series grouping (honest gaps, absence ≠ 0)")
struct ChartSeriesGroupingTests {

    /// A deterministic, DST-agnostic calendar fixed to UTC so day-adjacency math is reproducible.
    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }

    /// Build a date at midnight-UTC for a given (year, month, day) so points are on day boundaries.
    private func day(_ year: Int, _ month: Int, _ d: Int) -> Date {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = d
        comps.hour = 12 // midday — robust against any leftover tz skew
        return calendar.date(from: comps)!
    }

    private func point(_ year: Int, _ month: Int, _ d: Int, value: Double) -> InsightPoint {
        InsightPoint(date: day(year, month, d), value: value)
    }

    // 1. A set of consecutive calendar days groups into ONE run.
    @Test func consecutiveDays_formOneRun() {
        let points = [
            point(2026, 6, 1, value: 3),
            point(2026, 6, 2, value: 4),
            point(2026, 6, 3, value: 5),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        #expect(runs.count == 1)
        #expect(runs[0].points.count == 3)
    }

    // 2. A one-day gap between logged days splits them into TWO runs (the line must break).
    @Test func oneDayGap_splitsIntoTwoRuns() {
        let points = [
            point(2026, 6, 1, value: 3),
            point(2026, 6, 2, value: 4),
            // 2026-06-03 NOT logged → gap
            point(2026, 6, 4, value: 6),
            point(2026, 6, 5, value: 7),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        #expect(runs.count == 2)
        #expect(runs[0].points.count == 2)
        #expect(runs[1].points.count == 2)
        // Distinct series ids so Swift Charts breaks the line at the gap (Pitfall 1).
        #expect(runs[0].id != runs[1].id)
    }

    // 3. Multiple gaps produce multiple runs; runs are ordered by date, each a maximal gap-free span.
    @Test func multipleGaps_produceOrderedRuns() {
        let points = [
            point(2026, 6, 1, value: 1),
            // gap (6/2)
            point(2026, 6, 3, value: 2),
            point(2026, 6, 4, value: 3),
            // gap (6/5, 6/6)
            point(2026, 6, 7, value: 4),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        #expect(runs.count == 3)
        #expect(runs[0].points.count == 1)
        #expect(runs[1].points.count == 2)
        #expect(runs[2].points.count == 1)
        // Ordered by date: each run's first date is strictly before the next run's first date.
        #expect(runs[0].points.first!.date < runs[1].points.first!.date)
        #expect(runs[1].points.first!.date < runs[2].points.first!.date)
    }

    // 4. A single logged day forms a run of one point (so it still renders as a PointMark, never dropped).
    @Test func singleLoggedDay_formsRunOfOne() {
        let points = [point(2026, 6, 10, value: 8)]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        #expect(runs.count == 1)
        #expect(runs[0].points.count == 1)
        #expect(runs[0].points[0].value == 8)
    }

    // 5. The helper NEVER fabricates a `value: 0` for an unlogged day — absent days are simply absent.
    @Test func unloggedDays_areNeverFabricatedAsZero() {
        let points = [
            point(2026, 6, 1, value: 5),
            // 6/2 .. 6/9 NOT logged
            point(2026, 6, 10, value: 6),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        let allPoints = runs.flatMap(\.points)
        // Exactly the two logged points survive — no back-filled days at all.
        #expect(allPoints.count == 2)
        // Crucially: no point exists for any of the unlogged dates (no value:0 fabrication).
        for missing in 2...9 {
            let missingDate = day(2026, 6, missing)
            #expect(!allPoints.contains { calendar.isDate($0.date, inSameDayAs: missingDate) },
                    "Unlogged day 6/\(missing) must never appear as a fabricated point.")
        }
    }

    // 6. A literal logged `value: 0` (severity 0) IS retained as a point in its run (distinct from a gap).
    @Test func literalZeroValue_isRetainedAsAPoint() {
        let points = [
            point(2026, 6, 1, value: 0), // a real logged severity-0 day
            point(2026, 6, 2, value: 0),
            point(2026, 6, 3, value: 2),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        #expect(runs.count == 1, "Logged severity-0 days are contiguous, not gaps.")
        #expect(runs[0].points.count == 3)
        #expect(runs[0].points[0].value == 0)
        #expect(runs[0].points[1].value == 0)
    }

    // Robustness: unordered input is sorted by date before grouping (a producer need not pre-sort).
    @Test func unorderedInput_isSortedThenGrouped() {
        let points = [
            point(2026, 6, 4, value: 6),
            point(2026, 6, 1, value: 3),
            point(2026, 6, 2, value: 4),
        ]
        let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
        // 6/1, 6/2 contiguous; 6/3 missing → 6/4 starts a new run.
        #expect(runs.count == 2)
        #expect(runs[0].points.map(\.value) == [3, 4])
        #expect(runs[1].points.map(\.value) == [6])
    }

    // An empty input yields no runs (the chart renders an empty/sparse state, never a crash).
    @Test func emptyInput_yieldsNoRuns() {
        let runs = ChartSeriesGrouping.runs(from: [], calendar: calendar)
        #expect(runs.isEmpty)
    }
}
