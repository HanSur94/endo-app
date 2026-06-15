// Requirement: SCAT-01 / SCAT-02 / SCAT-03 — the Scatter mode's count-encoded PointMark cloud.
//
// THE most causality-sensitive surface of the milestone. It renders EXACTLY the both-logged `[ScatterPoint]`
// the pure `ScatterPairing.pairs` layer returns (Phase 14, consumed UNCHANGED) — one `PointMark` per
// occupied `(x, y)` cell, sized by the cell's `count` (how many both-logged days landed there). The honesty
// contract is LOCKED (UI-SPEC §Scatter Specification):
//
//   • COUNT ENCODING, NEVER JITTER: identical day-pairs are already aggregated into ONE cell by the pure
//     layer; size encodes density via `.symbolSize(by: .value("Days", count))`. We NEVER add a random
//     offset to spread overlapping points — that fabricates coordinates the user never logged.
//   • FIXED REAL-UNIT DOMAINS: both axes use `SeriesNormalization.domain(for:)` (the FIXED literal ranges
//     0…10 / 1…5 / 1…4 / 0…24 / 0…240), NEVER a data-driven min/max (which would distort the cloud and hide
//     that, e.g., severity never exceeded 6).
//   • DESCRIPTIVE-ONLY CHROME: NO fitted/trend line, NO regression, NO r-value / coefficient / slope /
//     percent-impact anywhere. A NEUTRAL caption only (a factual both-logged-day tally), plus a worded
//     a11y summary. The gated correlation engine remains the ONLY surface allowed to say "possible
//     association"; the scatter shows raw points + the count caption and nothing more.
//   • NEVER THE SOLE CARRIER OF MEANING: a one-line worded summary (`explore.scatter.summary`) gives a
//     VoiceOver user the count + both axis names + the both-logged basis WITHOUT the plot; per-mark labels
//     read each occupied cell. Count is size + text, never hue (a single accent hue throughout).
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import Charts
import EndoInsights

/// The Scatter mode plot: a square `Chart` of count-encoded `PointMark`s over fixed real-unit X/Y domains,
/// with worded axis titles, a neutral caption, and a worded VoiceOver summary. Pure presentation over the
/// injected both-logged cells — owns NO state, computes NO statistic.
struct ScatterPlotView: View {

    /// The both-logged cells from `ScatterPairing.pairs` — one occupied `(x, y)` cell each, count-encoded.
    /// Rendered EXACTLY as given: never synthesized, never imputed, a logged literal `(0,0)` is plotted.
    let points: [ScatterPairing.ScatterPoint]

    /// The X-axis series (quantitative — the picker offers quantitative-only). Drives the fixed X domain,
    /// the X axis title, and the per-mark X readout.
    let xSeries: ExploreSeries

    /// The Y-axis series (quantitative). Drives the fixed Y domain, the Y axis title, and the Y readout.
    let ySeries: ExploreSeries

    /// Reduce Motion: render the final state directly (no animated point transition) — UI-SPEC a11y.
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The both-logged-day total = the sum of every cell's count. A FACTUAL tally (legal exactly like
    /// `InsightsCopy.supportingEvidence`'s "Seen across {n} logged days") — NEVER a statistic.
    private var totalDays: Int {
        points.reduce(0) { $0 + $1.count }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            chart

            // Worded axis-title row — each series displayName + unit (real units, neutral framing: the row
            // names both axes, never implies X causes/affects Y).
            Text(axisTitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("explore.scatter.axisTitles")

            // The NEUTRAL caption — a factual both-logged-day count, never a statistic.
            Text(ExploreCopy.scatterCaption(days: totalDays))
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("explore.scatter.caption")

            // The worded text-summary — the PRIMARY non-gesture a11y path (UI-SPEC §Chart accessibility):
            // the point count + both axis names + the both-logged basis WITHOUT the plot. A 2-axis cell
            // cloud has no single value axis, so a plain summary is MORE honest than an Audio Graph.
            Text(ExploreCopy.scatterSummary(x: xSeries.displayName, y: ySeries.displayName, days: totalDays))
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("explore.scatter.summary")
        }
    }

    // MARK: - The square count-encoded scatter (the InsightTrendChart PointMark recipe, adapted)

    private var chart: some View {
        Chart {
            // ONE PointMark per occupied cell — size encodes the cell's both-logged-day count (NEVER
            // jitter, NEVER hue). Per-mark a11y label = the two real-unit readouts; value = the day count.
            ForEach(points, id: \.self) { point in
                PointMark(
                    x: .value(xSeries.displayName, point.x),
                    y: .value(ySeries.displayName, point.y)
                )
                .symbolSize(by: .value("Days", point.count))
                .foregroundStyle(Color.accentColor)   // single accent hue — count is size, never color
                .accessibilityLabel(Text(markLabel(point)))
                .accessibilityValue(Text("\(point.count) days"))
            }
        }
        // FIXED real-unit domains per scale kind — NEVER data-driven (the `?? 0...10` is an inert fallback;
        // a quantitative series always returns a non-nil domain, so it never fires in practice).
        .chartXScale(domain: SeriesNormalization.domain(for: xSeries.scaleKind) ?? 0...10)
        .chartYScale(domain: SeriesNormalization.domain(for: ySeries.scaleKind) ?? 0...10)
        .chartXAxis { AxisMarks() }
        .chartYAxis { AxisMarks() }
        .chartPlotStyle { $0.padding(8) }            // chart-plot-inset (UI-SPEC §Spacing)
        .frame(height: 320)                          // scatter-plot-height — square (UI-SPEC §Spacing)
        // Reduce Motion: render the final state directly (no animated point transition) — verbatim from
        // the InsightTrendChart recipe.
        .animation(reduceMotion ? nil : .default, value: points)
        .accessibilityIdentifier("explore.scatter.plot")
    }

    // MARK: - Worded copy helpers (real units via SeriesNormalization.readout — never a normalized number)

    /// The worded axis-title row: each series `displayName` + its real unit (via `SeriesNormalization`'s
    /// unit on the series). Neutral — names both axes, never implies a direction.
    private var axisTitle: String {
        let x = xSeries.unit.isEmpty ? xSeries.displayName : "\(xSeries.displayName) (\(xSeries.unit))"
        let y = ySeries.unit.isEmpty ? ySeries.displayName : "\(ySeries.displayName) (\(ySeries.unit))"
        return "X: \(x)  •  Y: \(y)"
    }

    /// One cell's per-mark a11y label: the two REAL-unit readouts (via `SeriesNormalization.readout` — the
    /// real value + unit, NEVER the normalized number). The count is carried in `.accessibilityValue`.
    private func markLabel(_ point: ScatterPairing.ScatterPoint) -> String {
        let xReadout = SeriesNormalization.readout(point.x, kind: xSeries.scaleKind, unit: xSeries.unit)
        let yReadout = SeriesNormalization.readout(point.y, kind: ySeries.scaleKind, unit: ySeries.unit)
        return "\(xSeries.displayName) \(xReadout), \(ySeries.displayName) \(yReadout)"
    }
}
