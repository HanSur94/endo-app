// Requirement: EXPL-03 / EXPL-05 / EXPL-07 — ONE quantitative Explore lane.
//
// The per-lane vocabulary of `InsightTrendChart` (the ONLY `Chart` analog in the repo) composed for
// the Explore lane stack. Each selected quantitative series renders as its OWN lane Chart in its
// real-unit fixed Y domain, all siblings sharing ONE absolute-date X domain (injected) and ONE shared
// `chartXSelection` scrub binding — NO dual-Y, NO normalization (EXPL-03). Honesty constraints, lifted
// verbatim from `InsightTrendChart`:
//   • Honest gaps (EXPL-07): one `LineMark` series per (series × contiguous-run) via the PURE
//     `ChartSeriesGrouping.runs`, so the line BREAKS at every gap (a shared/absent series interpolates
//     a straight line across the gap, fabricating data). An unlogged day is NEVER `value: 0`.
//   • A `PointMark` per logged day so a single logged day still shows AND a literal value-0 day draws a
//     point at the zero line (distinct from a gap, which has no mark at all).
//   • The Y scale is the FIXED honest `SeriesNormalization.domain(for: series.scaleKind)` (real units);
//     the X scale is the injected SHARED `xDomain` (byte-identical across every sibling lane — the
//     alignment property recorded by 15-01).
//   • Lane alignment (15-01 RESOLVED): `.chartYAxis(.hidden)` so NO lane reserves a numeric-label left
//     gutter → all plots share the full width → the shared scrub `RuleMark` aligns. The unit/scale lives
//     in the lane TITLE supplied by `ExploreLanesView` (NOT in this chart).
//   • The ONLY raw-date → value path is the pure `ChartScrubSelection.resolve` (exact-day) — NO
//     position-based chart lookup, NO nearest-point math anywhere (a gap day resolves to
//     `point == nil` = "Not logged", a logged value-0 day to a REAL point with value 0).
//   • Per-mark `.accessibilityLabel`(date)/`.accessibilityValue`(`SeriesNormalization.readout`) +
//     ONE `AXChartDescriptor` per lane (logged points only, gaps omitted, never zeros) so VoiceOver +
//     the Audio Graph read the lane.
//   • Reduce Motion disables chart animation.
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import Charts
import UIKit
import Accessibility
import EndoInsights

/// ONE quantitative Explore lane: gap-run `LineMark`s + per-day `PointMark`s on a FIXED real-unit Y
/// domain, a SHARED absolute-date X domain, and a SHARED scrub binding. Kept narrow (takes the value
/// pieces it needs, not a whole repo) so `ExploreLanesView` composes it directly per selected series.
struct ExploreLaneChart: View {

    /// The catalogued series this lane plots (carries `displayName`/`unit`/`scaleKind` for the readout).
    let series: ExploreSeries
    /// The logged points (one per day), computed ONCE by the caller via
    /// `ExploreSeriesExtraction.quantitativePoints(...)` — NOT re-read or recomputed here (Pitfall 4).
    let points: [InsightPoint]
    /// The SHARED absolute-date X domain — IDENTICAL across every sibling lane (injected by the stack so
    /// the same date renders at the same X pixel and the shared scrub `RuleMark` aligns).
    let xDomain: ClosedRange<Date>
    /// The ONE shared scrub date driving every lane (owned by the screen, passed by binding).
    @Binding var selectedDate: Date?

    /// Calendar for day-boundary math. `.current` is correct for display (the user's local dates); the
    /// pure grouping/resolver helpers are unit-tested with a fixed UTC calendar.
    private var calendar: Calendar { .current }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// ONE selection-feedback generator per view identity (the `InsightTrendChart` CHART-03 precedent —
    /// `UISelectionFeedbackGenerator` from main-actor view code is Swift-6 clean). Simulator no-ops are
    /// expected; device verification is the locked phase-gate manual checkpoint.
    @State private var haptics = UISelectionFeedbackGenerator()

    /// The ONLY raw-date → value path: the pure exact-day resolver (the honesty choke-point). No
    /// position-based chart lookup, no nearest-point math — a gap day resolves to `point == nil`
    /// ("Not logged"), a logged value-0 day to a REAL point with value 0. Uses the SAME calendar the
    /// runs computation uses.
    private var resolvedSelection: ChartScrubSelection.Resolution? {
        ChartScrubSelection.resolve(selectedDate, in: points, calendar: calendar)
    }

    /// Contiguous logged-day runs — DISTINCT `series` per run so the line breaks at every gap (EXPL-07).
    private var runs: [ChartSeriesGrouping.Run] {
        ChartSeriesGrouping.runs(from: points, calendar: calendar)
    }

    /// The FIXED honest Y domain for this series' scale kind (real units, never a per-window min/max).
    /// `0...1` only as a safe fallback — a quantitative series always returns a real domain.
    private var yDomain: ClosedRange<Double> {
        SeriesNormalization.domain(for: series.scaleKind) ?? 0...1
    }

    var body: some View {
        chart
            // Bloom mark recolour: map the single series-name domain to its Bloom hue. Keeps the
            // `.foregroundStyle(by: .value("Series", series.displayName))` legend/encoding semantic
            // intact (the `by:` form is NOT removed) — only the assigned colour changes (key fact 3/4).
            .chartForegroundStyleScale(domain: [series.displayName], range: [bloomColor(for: series)])
            .chartXScale(domain: xDomain)            // SHARED absolute-date domain (identical per lane)
            .chartYScale(domain: yDomain)            // per-lane FIXED honest real-unit domain
            .chartYAxis(.hidden)                     // 15-01 inset mechanism: no numeric Y gutter → aligned
            .chartXAxis {
                // ABSOLUTE calendar dates ONLY — never cycle-relative. Dynamic Type scales these
                // caption-class labels; Swift Charts thins tick density rather than clipping.
                AxisMarks {
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel(format: .dateTime.month().day())
                }
            }
            .chartXSelection(value: $selectedDate)   // SHARED scrub binding (same on every lane)
            // Haptic: fires on resolved-DAY change ONLY — nil→day (initial touch) and day→nil (release)
            // fire nothing; dayA→dayB fires one selection tick (naturally including logged↔gap
            // transitions: the day changes even when the point is nil). prepare() keeps the Taptic Engine
            // warm mid-scrub.
            .onChange(of: resolvedSelection?.day) { old, new in
                if old != nil, new != nil, old != new {
                    haptics.selectionChanged()
                }
                if new != nil {
                    haptics.prepare()
                }
            }
            .frame(height: 120)                       // lane-quant-height (UI-SPEC)
            // Reduce Motion: render the final state directly (no animated chart transition).
            .animation(reduceMotion ? nil : .default, value: points)
            // ONE Audio Graph descriptor per lane (the non-gesture VoiceOver path) + a text-summary
            // a11y label so the lane is never the sole carrier of meaning.
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(summaryText))
            .accessibilityChartDescriptor(self)
            .accessibilityIdentifier("explore.lane.\(series.id)")
    }

    /// The feature-local Bloom mark hue for a series (key fact 4) — switches on the explicit `scaleKind`
    /// so it can NEVER drift from the catalog's taxonomy. Severity/pain/stress → rose, sleep → indigo,
    /// bleeding rank → plum, mood → gold, activity → teal, presence/event ticks → plum. Feature-private
    /// (no new BloomColors token introduced).
    private func bloomColor(for series: ExploreSeries) -> Color {
        switch series.scaleKind {
        case .ordinal0to10:     return Color.rose    // symptom/pain severity + stress
        case .continuousHours:  return Color.indigo  // sleep.hours
        case .ordinalBleeding:  return Color.plum    // bleeding.intensity
        case .ordinal1to5:      return Color.gold    // mood
        case .continuousMinutes: return Color.teal   // activity.minutes
        case .presenceEvent:    return Color.plum    // event ticks (food, clots, prn, medication, bleeding.event)
        }
    }

    /// The one-line text summary so the lane is NEVER the sole carrier of meaning (a11y full-text
    /// fallback): "{displayName} — {unit}" (the same scale signal the visible lane title carries).
    private var summaryText: String {
        series.unit.isEmpty ? series.displayName : "\(series.displayName) — \(series.unit)"
    }

    private var chart: some View {
        Chart {
            // One LineMark series PER run → the line connects within a run and BREAKS between runs.
            ForEach(runs) { run in
                ForEach(run.points, id: \.date) { point in
                    LineMark(
                        x: .value("Date", point.date),
                        y: .value(series.displayName, point.value),
                        series: .value("Run", "\(series.id).\(run.id)")  // distinct per (series × run)
                    )
                    // Color carried by the series name → legend + the colorblind-safe palette (never
                    // hue alone; the lane title + per-lane real-unit signal pair with it).
                    .foregroundStyle(by: .value("Series", series.displayName))
                    .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
                    .accessibilityValue(Text(readout(point.value)))
                }
            }
            // A PointMark per logged day — so a single day still shows AND a literal value-0 draws a
            // point at the zero line (distinct from a gap, which has no mark).
            ForEach(points, id: \.date) { point in
                PointMark(
                    x: .value("Date", point.date),
                    y: .value(series.displayName, point.value)
                )
                .foregroundStyle(by: .value("Series", series.displayName))
                .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
                .accessibilityValue(Text(readout(point.value)))
            }
            // Selection chrome — renders iff the resolver returned non-nil (an active scrub).
            if let sel = resolvedSelection {
                selectionMarks(sel)
            }
        }
    }

    /// The honest real-unit readout for a value (never the normalized number) — routed through the
    /// shared `SeriesNormalization.readout` so per-mark a11y and the tooltip share ONE vocabulary.
    private func readout(_ value: Double) -> String {
        SeriesNormalization.readout(value, kind: series.scaleKind, unit: series.unit)
    }

    // MARK: - Selection chrome (the shared gray scrub rule + the logged-day highlight point)

    /// The gray scan line (chrome, NEVER the data hue) drawn BEHIND the data via zIndex, plus the
    /// logged-day highlight point. The multi-series tooltip lives in `ExploreLanesView` (ONE card over
    /// the whole stack), NOT here — a lane draws only its own rule + highlight.
    @ChartContentBuilder
    private func selectionMarks(_ sel: ChartScrubSelection.Resolution) -> some ChartContent {
        RuleMark(x: .value("Selected", sel.day))
            .foregroundStyle(Color.inkMuted.opacity(0.5))   // chrome scrub line — distinct from data hue
            .zIndex(-1)
        // Highlight point — logged day ONLY (drawing one on a gap would fabricate data); on a logged
        // value-0 day it draws AT the zero line (honest absence ≠ 0).
        if let point = sel.point {
            PointMark(
                x: .value("Selected", sel.day),
                y: .value(series.displayName, point.value)
            )
            .foregroundStyle(by: .value("Series", series.displayName))
            .symbolSize(100)
        }
    }
}

// MARK: - Audio Graph (the non-gesture VoiceOver path, alongside the KEPT per-mark labels)

// `@preconcurrency`: the protocol's requirements are nonisolated but this View is implicitly
// MainActor (Swift 6); SwiftUI invokes `makeChartDescriptor()` on the main actor, so the
// conformance is main-actor-safe at runtime (the compiler-suggested resolution).
extension ExploreLaneChart: @preconcurrency AXChartDescriptorRepresentable {
    /// Discrete tones for LOGGED days ONLY, sorted by date — gaps are OMITTED (never zeros), and
    /// `isContinuous: false` means the Audio Graph never sonically interpolates across a gap (the audio
    /// equivalent of the broken line). The Y axis is this series' FIXED honest domain in real units.
    func makeChartDescriptor() -> AXChartDescriptor {
        let sorted = points.sorted { $0.date < $1.date }
        let xAxis = AXCategoricalDataAxisDescriptor(
            title: "Day",
            categoryOrder: sorted.map { $0.date.formatted(.dateTime.month().day()) }
        )
        let domain = yDomain
        let yAxis = AXNumericDataAxisDescriptor(
            title: series.displayName,
            range: domain.lowerBound...domain.upperBound,
            gridlinePositions: []
        ) { value in
            SeriesNormalization.readout(value, kind: series.scaleKind, unit: series.unit)
        }
        let axisSeries = AXDataSeriesDescriptor(
            name: series.displayName,
            isContinuous: false,
            dataPoints: sorted.map { point in
                AXDataPoint(x: point.date.formatted(.dateTime.month().day()), y: point.value)
            }
        )
        return AXChartDescriptor(
            title: series.displayName,
            summary: summaryText,
            xAxis: xAxis,
            yAxis: yAxis,
            additionalAxes: [],
            series: [axisSeries]
        )
    }
}
