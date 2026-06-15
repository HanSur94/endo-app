// Requirement: EXPL-04 / EXPL-05 — ONE presence/event-marker Explore lane.
//
// A presence series (`scaleKind == .presenceEvent`: food tags, clots, PRN, medication-taken,
// bleeding-as-event) renders as a ROW of date-aligned occurrence ticks — one mark per logged-present
// day — at a constant Y, NEVER a connected 0/1 line (RESEARCH Pattern 2 / Pitfall 1; T-15-08). A day with
// NO occurrence has NO tick: that is "not logged that event", never "logged absent". There is no value
// mapping at all — the extraction path returns `[Date]` (no value field), so a 0/1 line is
// structurally unrepresentable.
//
// Honesty constraints, mirrored from the quantitative lane:
//   • Shares the IDENTICAL injected `xDomain` (absolute dates) and the SAME `chartXSelection` scrub
//     binding as every sibling lane → the shared scrub `RuleMark` crosses this lane at the same date.
//   • `.chartYScale(domain: 0...1)` + `.chartYAxis(.hidden)`: there is no numeric domain for an event
//     lane (it inherits the same identical-inset alignment property for free — 15-01 caveat 5).
//   • Per-tick `.accessibilityValue(ExploreCopy.eventTagged)` — "Logged", never "1".
//   • Frame height 44 (`lane-event-height`, also the ≥44pt touch floor).
//
// Pitfall 2 (LOAD-BEARING): the event lane has NO `ChartScrubSelection` path — the resolver works on
// `[InsightPoint]`, not `[Date]`. Its tooltip contribution is resolved by the CALLER
// (`ExploreLanesView`) via `series.isPresent(in: snapshotForThatDay)`; this view does NOT try to
// resolve occurrence days through `ChartScrubSelection`.
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import Charts
import EndoInsights

/// ONE presence/event-marker Explore lane: a row of date-aligned occurrence ticks (one per logged day),
/// sharing the stack's absolute-date X domain + scrub binding. No connected line, no 0/1 value mapping.
struct EventLaneChart: View {

    /// The catalogued presence series this lane plots (carries `displayName` for a11y).
    let series: ExploreSeries
    /// The occurrence days, computed ONCE by the caller via `ExploreSeriesExtraction.occurrenceDays(...)`
    /// — each a `startOfDay` Date on which the event was logged present. NOT re-read or recomputed here.
    let days: [Date]
    /// The SHARED absolute-date X domain — IDENTICAL across every sibling lane (injected by the stack).
    let xDomain: ClosedRange<Date>
    /// The ONE shared scrub date driving every lane (owned by the screen, passed by binding).
    @Binding var selectedDate: Date?

    /// Calendar for day-boundary math. `.current` for display; the scrub day comes from the stack's
    /// resolver, so this lane only needs `startOfDay` for the shared rule mark's date.
    private var calendar: Calendar { .current }

    /// The shared scrub day (startOfDay) for THIS lane's gray rule mark — derived directly from the
    /// shared `selectedDate` (NOT via `ChartScrubSelection`, which is the quantitative path). The tick
    /// for that day either exists (occurrence) or does not; the rule mark crosses regardless.
    private var scrubDay: Date? {
        selectedDate.map { calendar.startOfDay(for: $0) }
    }

    var body: some View {
        Chart {
            // One occurrence tick per logged-present day — a day with NO occurrence has NO mark
            // (never a 0/1 line). Drawn at a constant mid-lane Y (0.5) on the hidden 0...1 scale.
            ForEach(days, id: \.self) { day in
                RectangleMark(
                    x: .value("Date", day),
                    y: .value("Logged", 0.5),
                    width: .fixed(6)
                )
                .foregroundStyle(by: .value("Series", series.displayName))
                .accessibilityLabel(Text(day, format: .dateTime.month().day()))
                .accessibilityValue(Text(ExploreCopy.eventTagged))   // "Logged", never "1"
            }
            // The shared gray scrub rule — chrome, NEVER the data hue; same date the sibling lanes use.
            if let day = scrubDay {
                RuleMark(x: .value("Selected", day))
                    .foregroundStyle(Color.secondary.opacity(0.5))
                    .zIndex(-1)
            }
        }
        .chartXScale(domain: xDomain)            // SHARED absolute-date domain (identical per lane)
        .chartYScale(domain: 0...1)              // no numeric domain — a constant-Y tick row
        .chartYAxis(.hidden)                     // event lanes inherit the identical-inset alignment
        .chartXAxis {
            AxisMarks {
                AxisGridLine()
                AxisTick()
                AxisValueLabel(format: .dateTime.month().day())
            }
        }
        .chartXSelection(value: $selectedDate)   // SHARED scrub binding (same on every lane)
        .frame(height: 44)                       // lane-event-height / ≥44pt touch floor (UI-SPEC)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(series.displayName))
        .accessibilityIdentifier("explore.lane.\(series.id).event")
    }
}
