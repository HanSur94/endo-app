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
import UIKit
import Accessibility
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
    /// Whether the scrub-selection gesture + chrome are attached. Additive (Phase 13, CHART-01..03 —
    /// the `inkColor` precedent): the dashboard card passes `true`; the PDF renderer constructs a
    /// FRESH chart and keeps this default `false`, so the `chartXSelection` modifier is NEVER even
    /// attached on the PDF path (protected by construction — `ReportPDFRenderer` is NOT edited).
    var allowsSelection: Bool = false
    /// Root for the selection-tooltip a11y identifiers (the card passes
    /// "insights.card.<signalKey>.chart"). `nil` (default) skips the ids entirely — with
    /// `allowsSelection == false` the chrome can never render anyway.
    var selectionA11yIDRoot: String? = nil

    /// Calendar for day-boundary math + the window start. `.current` is correct for display (the
    /// user's local dates); the pure grouping helper is unit-tested with a fixed UTC calendar.
    private var calendar: Calendar { .current }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Test-only deterministic selection day (Phase 13 E2E seam, Plan 13-03). `nil` on every
    /// production launch (the env default; only a DEBUG `-uitest-reset`-paired launch injects a
    /// value), so the `.onAppear` seam read below is a no-op outside UI tests by construction.
    @Environment(\.uiTestScrubDay) private var uiTestScrubDay

    /// The raw selection date from `chartXSelection` — per-card, view-local, EPHEMERAL (never
    /// persisted, never in any shared Observable; a scrub frame must not invalidate the List).
    @State private var rawSelectedDate: Date?

    /// ONE selection-feedback generator per view identity (CHART-03 — `UISelectionFeedbackGenerator`
    /// is LOCKED in CONTEXT; UIKit class from main-actor view code is Swift-6 clean). Simulator
    /// no-ops are expected — device verification is the locked phase-gate manual checkpoint.
    @State private var haptics = UISelectionFeedbackGenerator()

    /// The ONLY raw-date → value path: the pure exact-day resolver (CHART-02 honesty choke-point).
    /// No `proxy.value(atX:)`, no nearest-point math anywhere — a gap day resolves to `point == nil`
    /// ("Not logged"), a logged severity-0 day to a REAL point with value 0. Uses the SAME calendar
    /// the runs/grouping computation uses.
    private var resolvedSelection: ChartScrubSelection.Resolution? {
        ChartScrubSelection.resolve(rawSelectedDate, in: points, calendar: calendar)
    }

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

            // Conditional gesture attachment (PDF-Schutz): when `allowsSelection == false` the
            // `chartXSelection` modifier is NOT attached at all (not merely inert) — the rendered
            // output and modifier set stay IDENTICAL to v1.0. `allowsSelection` is immutable per
            // instance, so the branch has stable view identity.
            Group {
                if allowsSelection {
                    chart
                        .chartXSelection(value: $rawSelectedDate)
                        // Haptic (CHART-03): fires on resolved-DAY change ONLY — nil→day (initial
                        // touch) and day→nil (release) fire nothing; dayA→dayB fires one selection
                        // tick (naturally including logged↔gap transitions: the day changes even
                        // when the point is nil). prepare() keeps the Taptic Engine warm mid-scrub;
                        // no explicit release is needed (the generator idles when unused).
                        .onChange(of: resolvedSelection?.day) { old, new in
                            if old != nil, new != nil, old != new {
                                haptics.selectionChanged()
                            }
                            if new != nil {
                                haptics.prepare()
                            }
                        }
                        // E2E seam (Plan 13-03): a DEBUG `-uitest-reset`-paired launch can inject a
                        // deterministic selection day so XCUITests assert the chrome via a11y ids with
                        // zero gesture coordinates. The `xDomain.contains` guard is LOAD-BEARING
                        // (RESEARCH Pitfall 6): a day outside THIS card's window must not draw an
                        // out-of-domain RuleMark. Production: the env value is nil (default), so this
                        // branch never runs; the PDF path additionally never has allowsSelection==true.
                        .onAppear {
                            if let day = uiTestScrubDay, xDomain.contains(day) {
                                rawSelectedDate = day
                            }
                        }
                        // The tooltip's a11y element lives HERE, not on the annotation view: Swift
                        // Charts does NOT expose `.annotation` content in the accessibility tree
                        // (verified on the iOS 18 simulator — the visual tooltip renders but never
                        // appears in the AX hierarchy), so the readout + ids ride a transparent
                        // overlay proxy instead (CHART-04; the Plan-03 E2E asserts these ids).
                        // Branch-local: the PDF path gains zero new modifiers.
                        .overlay(alignment: .top) {
                            if let sel = resolvedSelection {
                                selectionAccessibilityProxy(sel)
                            }
                        }
                } else {
                    chart
                }
            }
            .frame(height: 200)
            .accessibilityLabel(Text(summaryText))
            // Audio Graph (CHART-04): attached in BOTH modes — render-inert (`ImageRenderer`
            // rasterizes visuals only, so it never reaches the PDF). Escape hatch (UI-SPEC): if
            // Plan 03's PDF re-verification shows ANY diff, gate this behind `allowsSelection` too.
            .accessibilityChartDescriptor(self)
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
            // Selection chrome (CHART-01/02) — renders iff the resolver returned non-nil. With
            // `allowsSelection == false` the binding can never become non-nil, so this is unreachable
            // on the PDF path by construction.
            if let sel = resolvedSelection {
                selectionMarks(sel)
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
        // Selection state is deliberately NOT an input here — scrubbing never re-animates the
        // series, and the chrome moves un-animated by construction (no Reduce-Motion gate needed).
        .animation(reduceMotion ? nil : .default, value: points)
    }

    // MARK: - Selection chrome (CHART-01/02 — the Health-pattern lollipop, UI-SPEC stack order)

    /// The rule mark + pinned-top tooltip + (logged days only) the highlight point. Selection marks
    /// carry NO per-mark accessibilityLabel/Value — the tooltip is the single new a11y element.
    @ChartContentBuilder
    private func selectionMarks(_ sel: ChartScrubSelection.Resolution) -> some ChartContent {
        // Vertical scan line — secondary gray, NEVER accent (it would compete with the data line),
        // drawn BEHIND the data via zIndex. The annotation pins the tooltip at the chart top so it
        // never clips at window edges and the finger never occludes it (the Health pattern).
        RuleMark(x: .value("Selected", sel.day))
            .foregroundStyle(Color.secondary.opacity(0.5))
            .zIndex(-1)
            .annotation(
                position: .top, spacing: 0,
                overflowResolution: .init(x: .fit(to: .chart), y: .disabled)
            ) {
                tooltip(sel)
            }
        // Highlight point — logged day ONLY (drawing one on a gap would fabricate data); on a
        // logged severity-0 day it draws AT the zero line (honest absence ≠ 0).
        if let point = sel.point {
            PointMark(
                x: .value("Selected", sel.day),
                y: .value("Severity", point.value)
            )
            .foregroundStyle(inkColor)
            .symbolSize(100)
        }
    }

    // MARK: - Tooltip (UI-SPEC card layout — audited copy only, no string literals)

    private func tooltip(_ sel: ChartScrubSelection.Resolution) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            // Line 1: the absolute date — the SAME format vocabulary as the axis labels.
            Text(sel.day, format: .dateTime.month().day())
                .font(.caption2)
                .foregroundStyle(.secondary)
            if let point = sel.point {
                // Logged day (incl. literal severity-0): the payload, semibold primary.
                Text(InsightsCopy.scrubSeverity(Int(point.value.rounded())))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.primary)
            } else {
                // Gap day: de-emphasized — the gap is information, not an error (never bold/red).
                Text(InsightsCopy.scrubNotLogged)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(8)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
        // Hidden from the a11y tree: Swift Charts never exposes annotation content anyway (verified),
        // and the readout/ids live on `selectionAccessibilityProxy` — hiding this PREVENTS a duplicate
        // element if a future OS starts exposing annotations.
        .accessibilityHidden(true)
    }

    /// The tooltip's a11y representation (CHART-04): ONE quiet element whose `accessibilityValue`
    /// carries the full "{date} — Severity {n} of 10" / "{date} — Not logged" readout, plus (gap days
    /// only) the explicit `.notLogged` marker element the honesty E2E asserts. Lives as a transparent,
    /// non-hit-testable overlay because Swift Charts does not expose `.annotation` content in the
    /// accessibility tree. NO `UIAccessibility.post` — the quiet-selection posture is unchanged.
    private func selectionAccessibilityProxy(_ sel: ChartScrubSelection.Resolution) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .frame(width: 8, height: 8)
                .accessibilityElement(children: .ignore)
                // WR-01: an element carrying only `.accessibilityValue` reads malformed to live
                // VoiceOver ("label, value" with an empty label slot). Give it an explicit label
                // (the selected date) so VoiceOver announces label + value coherently. The `.value`
                // slot keeps the FULL combined readout unchanged, so ChartScrubUITests — which assert
                // on `.value` by identifier — stay byte-identical.
                .accessibilityLabel(Text(sel.day.formatted(.dateTime.month().day())))
                .accessibilityValue(Text(selectionReadout(sel)))
                .scrubAccessibilityID(selectionA11yIDRoot.map { "\($0).tooltip" })
            if sel.point == nil {
                // The explicit gap-state marker (CHART-02): exists ONLY when the selected day is
                // unlogged — its ABSENCE on a logged severity-0 day proves absence ≠ 0 end-to-end.
                Color.clear
                    .frame(width: 8, height: 8)
                    .accessibilityElement(children: .ignore)
                    // WR-01: same rationale — a label-less, value-only element reads malformed.
                    .accessibilityLabel(Text(sel.day.formatted(.dateTime.month().day())))
                    .accessibilityValue(Text(InsightsCopy.scrubNotLogged))
                    .scrubAccessibilityID(selectionA11yIDRoot.map { "\($0).tooltip.notLogged" })
            }
        }
        // Container boundary (the Section→cell→`.confidence` precedent): the card's outer
        // `.accessibilityIdentifier("\(idRoot).chart")` bleeds down and MASKS bare descendant
        // element ids (verified in the AX hierarchy), but ids one level inside an explicit
        // container survive — so the proxy ids are shielded behind `.contain`.
        .accessibilityElement(children: .contain)
        .allowsHitTesting(false)
    }

    /// "{date} — Severity {n} of 10" / "{date} — Not logged" — composed from the audited strings
    /// plus the same date format the axis uses (a format, not a copy string).
    private func selectionReadout(_ sel: ChartScrubSelection.Resolution) -> String {
        let date = sel.day.formatted(.dateTime.month().day())
        if let point = sel.point {
            return "\(date) — \(InsightsCopy.scrubSeverity(Int(point.value.rounded())))"
        }
        return "\(date) — \(InsightsCopy.scrubNotLogged)"
    }
}

// MARK: - Audio Graph (CHART-04 — the non-gesture VoiceOver path, alongside the KEPT per-mark labels)

// `@preconcurrency`: the protocol's requirements are nonisolated but this View is implicitly
// MainActor (Swift 6); SwiftUI invokes `makeChartDescriptor()` on the main actor, so the
// conformance is main-actor-safe at runtime (the compiler-suggested resolution).
extension InsightTrendChart: @preconcurrency AXChartDescriptorRepresentable {
    /// Discrete tones for LOGGED days ONLY, sorted by date — gaps are OMITTED (never zeros), and
    /// `isContinuous: false` means the Audio Graph never sonically interpolates across a gap
    /// (the audio equivalent of the broken line). Same date vocabulary as the axis labels.
    func makeChartDescriptor() -> AXChartDescriptor {
        let sorted = points.sorted { $0.date < $1.date }
        let xAxis = AXCategoricalDataAxisDescriptor(
            title: "Day",
            categoryOrder: sorted.map { $0.date.formatted(.dateTime.month().day()) }
        )
        let yAxis = AXNumericDataAxisDescriptor(
            title: "Severity",
            range: 0...10,
            gridlinePositions: []
        ) { value in
            "\(Int(value)) of 10"
        }
        let series = AXDataSeriesDescriptor(
            name: headline,
            isContinuous: false,
            dataPoints: sorted.map { point in
                AXDataPoint(x: point.date.formatted(.dateTime.month().day()), y: point.value)
            }
        )
        return AXChartDescriptor(
            title: headline,
            summary: summaryText,
            xAxis: xAxis,
            yAxis: yAxis,
            additionalAxes: [],
            series: [series]
        )
    }
}

// MARK: - Conditional a11y-id helper (skip ids ENTIRELY when no root is provided)

private extension View {
    /// Applies `.accessibilityIdentifier` only when an id is provided — when
    /// `selectionA11yIDRoot == nil` (PDF/previews) no identifier is attached at all.
    @ViewBuilder
    func scrubAccessibilityID(_ id: String?) -> some View {
        if let id {
            accessibilityIdentifier(id)
        } else {
            self
        }
    }
}
