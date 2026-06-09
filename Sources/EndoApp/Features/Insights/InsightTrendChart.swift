// Requirement: INST-01 / D-06 / T-08-10 / T-08-11 — the make-or-break Swift Charts trend chart.
//
// THE no-analog view (zero `Chart`/`LineMark` exist elsewhere in the repo — cite UI-SPEC
// §"Swift Charts Specification" + RESEARCH Pattern 5). Honesty constraints baked in:
//   • ABSOLUTE calendar-date X-axis ONLY — never cycle-relative (SC3 / D-06).
//   • Honest gaps: each contiguous logged-day run (from the PURE `ChartSeriesGrouping`) is a DISTINCT
//     `series`, so the `LineMark` BREAKS at every gap (Pitfall 1: a shared/absent series interpolates
//     a straight line across the gap, fabricating data). An unlogged day is NEVER `value: 0`.
//   • A `PointMark` per logged day so a single logged day still shows, and a literal severity-0 day
//     draws a point at the zero line (distinct from a gap, which has no mark at all).
//   • Fixed Y domain `0...10` (matches `SeverityControl`) so windows are visually comparable.
//   • Per-mark `.accessibilityLabel`(date)/`.accessibilityValue`("severity n of 10") so VoiceOver +
//     the Audio Graph can read the series; a one-line TEXT SUMMARY accompanies the chart so it is
//     never the sole carrier of meaning.
//   • Reduce Motion disables chart animation; Dynamic Type scales axis labels.
import SwiftUI
import Charts
import EndoInsights

/// A line chart of one `.trend` (symptom severity over time) on an absolute-date axis with honest gaps.
/// Kept narrow (takes the value pieces it needs, not a whole repo) so the card composes it directly.
struct InsightTrendChart: View {

    /// The logged points (one per day). Sparse is fine — the gap helper + window domain handle it.
    let points: [InsightPoint]
    /// The window the trend is computed over — drives the X domain (absolute dates) + the label.
    let window: TrendWindow
    /// The descriptive direction word ("trending up/down/steady") for the text summary.
    let directionWord: String
    /// The card's headline, reused verbatim in the one-line text summary (already gate-audited).
    let headline: String
    /// The absolute "today" the window ends at (injected for determinism; defaults to now).
    var asOf: Date = .now
    /// The single ink color the line + points draw in. Additive (Phase 10, D-02): the live dashboard
    /// keeps `.accentColor` (byte-identical on screen); the PDF renderer constructs a FRESH chart with
    /// `inkColor: .black` so the embedded vector chart prints in a single print-ink color (UI-SPEC §Color
    /// "chart renders in a single ink color for print"). NOTHING else about the chart changes.
    var inkColor: Color = .accentColor

    /// Calendar for day-boundary math + the window start. `.current` is correct for display (the
    /// user's local dates); the pure grouping helper is unit-tested with a fixed UTC calendar.
    private var calendar: Calendar { .current }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Contiguous logged-day runs — DISTINCT `series` per run so the line breaks at every gap.
    private var runs: [ChartSeriesGrouping.Run] {
        ChartSeriesGrouping.runs(from: points, calendar: calendar)
    }

    /// The absolute-date X domain: the window's span ending at `asOf` (window range even when sparse,
    /// so "3 points in 30 days" reads as sparse-over-a-month, not 3 consecutive days — UI-SPEC).
    private var xDomain: ClosedRange<Date> {
        let end = asOf
        let start: Date
        switch window {
        case .last7:   start = calendar.date(byAdding: .day, value: -7, to: end) ?? end
        case .last30:  start = calendar.date(byAdding: .day, value: -30, to: end) ?? end
        case .last90:  start = calendar.date(byAdding: .day, value: -90, to: end) ?? end
        case .allTime:
            // All-time: span from the earliest logged point (or 30d back if none) to today.
            start = points.map(\.date).min() ?? (calendar.date(byAdding: .day, value: -30, to: end) ?? end)
        }
        return min(start, end) ... end
    }

    /// The text summary so the chart is NEVER the sole carrier of meaning (a11y full-text fallback).
    private var summaryText: String {
        InsightsCopy.chartSummary(headline: headline, window: window, direction: directionWord)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // One-line text summary ABOVE the chart (the full text/list fallback a11y requires).
            Text(summaryText)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            chart
                .frame(height: 200)
                .accessibilityLabel(Text(summaryText))
        }
    }

    private var chart: some View {
        Chart {
            // One LineMark series PER run → the line connects within a run and BREAKS between runs.
            ForEach(runs) { run in
                ForEach(run.points, id: \.date) { point in
                    LineMark(
                        x: .value("Date", point.date),
                        y: .value("Severity", point.value),
                        series: .value("Run", run.id) // ← distinct per run = honest gap (Pitfall 1)
                    )
                    .foregroundStyle(inkColor)
                    .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
                    .accessibilityValue(Text("severity \(Int(point.value.rounded())) of 10"))
                }
            }
            // A PointMark per logged day — so a single day still shows AND a literal severity-0 draws
            // a point at the zero line (distinct from a gap, which has no mark).
            ForEach(points, id: \.date) { point in
                PointMark(
                    x: .value("Date", point.date),
                    y: .value("Severity", point.value)
                )
                .foregroundStyle(inkColor)
                .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
                .accessibilityValue(Text("severity \(Int(point.value.rounded())) of 10"))
            }
        }
        .chartXScale(domain: xDomain)          // absolute-date window range, even when sparse
        .chartYScale(domain: 0...10)           // fixed severity domain (matches SeverityControl)
        .chartXAxis {
            // ABSOLUTE calendar dates ONLY — never cycle-relative (SC3 / D-06). Dynamic Type scales
            // these caption-class labels; Swift Charts thins tick density rather than clipping.
            AxisMarks {
                AxisGridLine()
                AxisTick()
                AxisValueLabel(format: .dateTime.month().day())
            }
        }
        .chartYAxis {
            AxisMarks(values: [0, 5, 10])
        }
        // Reduce Motion: render the final state directly (no animated chart transition).
        .animation(reduceMotion ? nil : .default, value: points)
    }
}
