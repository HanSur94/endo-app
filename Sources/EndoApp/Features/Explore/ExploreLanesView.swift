// Requirement: EXPL-03 / EXPL-04 / EXPL-05 / EXPL-07 — the Explore lane STACK + shared scrub + the
// single multi-series tooltip.
//
// The phase's new SwiftUI assembly: a vertical stack of sibling `Chart` lanes that share ONE
// absolute-date X domain and ONE `chartXSelection` scrub state, with a single multi-series tooltip
// reading every visible series at once. Composed from the lane primitives (`ExploreLaneChart`,
// `EventLaneChart`) using the inset mechanism RECORDED by 15-01 (`.chartYAxis(.hidden)` + unit-in-title).
//
//   • All quantitative lanes (each its own `ExploreLaneChart`, preceded by its `.subheadline` lane
//     TITLE carrying name + unit) THEN all event lanes (each an `EventLaneChart`) stack in one VStack,
//     every lane passed the IDENTICAL shared `xDomain` and the SAME `$selectedDate` binding (EXPL-03).
//   • ONE multi-series tooltip (NOT per-lane): a single card listing the scrubbed day ONCE, then one
//     line per visible series — a real-unit `SeriesNormalization.readout` for a logged day, or
//     "{displayName}: Not logged" for a gap day (EXPL-05/EXPL-07).
//   • A quantitative series resolves via `ChartScrubSelection.resolve` (exact-day, the honesty
//     choke-point). An EVENT series has NO `ChartScrubSelection` path (Pitfall 2) — its line resolves
//     via `series.isPresent(in: snapshotForThatDay)` against the injected day-keyed snapshot map
//     ⇒ `ExploreCopy.eventTagged` / `ExploreCopy.notLogged`, NEVER always-"Not logged".
//   • The visual tooltip card is `.accessibilityHidden(true)`; the per-series readout + ids ride a
//     transparent `.allowsHitTesting(false)` overlay proxy (Swift Charts does NOT expose `.annotation`
//     content to the AX tree), shielded with `.accessibilityElement(children: .contain)` so the
//     card-level id can't mask the per-series ids (the Phase-13 id-bleed fix).
//   • The shared `selectedDate` can be set deterministically by reading `-uitest-select-day`
//     (`\.uiTestScrubDay`) via the keyed `.task(id:)` guard, exactly as `InsightTrendChart` does.
//   • ALL chrome copy routes through `ExploreCopy` (no string literals). Reduce Motion renders the
//     final state (no scrub animation).
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import EndoInsights

/// The Explore lane stack: sibling lanes sharing ONE X domain + ONE scrub date, plus the single
/// multi-series tooltip. Inputs are all PREPARED once by the caller per selection/window change (NOT in
/// `body`, NOT per scrub frame — Pitfall 4); this view only composes + scrubs them.
struct ExploreLanesView: View {

    /// The selected quantitative series, in display order, each paired with its prepared logged points
    /// (`ExploreSeriesExtraction.quantitativePoints(...)`). Prepared once by the caller.
    let quantitativeLanes: [(series: ExploreSeries, points: [InsightPoint])]
    /// The selected presence/event series, in display order, each paired with its prepared occurrence
    /// days (`ExploreSeriesExtraction.occurrenceDays(...)`). Prepared once by the caller.
    let eventLanes: [(series: ExploreSeries, days: [Date])]
    /// A `startOfDay`-keyed snapshot map for the event-lane tooltip lookup (Pitfall 2: event lines
    /// resolve via `series.isPresent(in:)`, NOT `ChartScrubSelection`). Prepared once by the caller.
    let snapshotsByDay: [Date: DaySnapshot]
    /// The SHARED absolute-date X domain — derived ONCE by the caller from the window (the
    /// `InsightTrendChart.xDomain` math, lifted) and passed IDENTICAL into every lane.
    let xDomain: ClosedRange<Date>
    /// The ONE shared scrub date driving every lane (owned by the screen, passed by binding).
    @Binding var selectedDate: Date?

    /// Calendar for day-boundary math + the resolver. `.current` for display (the user's local dates).
    private var calendar: Calendar { .current }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Test-only deterministic selection day (the Phase-13 E2E seam). `nil` on every production launch
    /// (the env default), so the `.task` seam below is a no-op outside UI tests by construction.
    @Environment(\.uiTestScrubDay) private var uiTestScrubDay

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {   // md spacing between lanes
            // Quantitative lanes FIRST — each preceded by its lane TITLE (name + unit; the recorded
            // inset mechanism's scale signal now that the numeric Y axis is hidden).
            ForEach(quantitativeLanes, id: \.series.id) { lane in
                VStack(alignment: .leading, spacing: 4) {   // xs gap title↔lane
                    Text(laneTitle(lane.series))
                        .font(.subheadline).fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                    ExploreLaneChart(
                        series: lane.series,
                        points: lane.points,
                        xDomain: xDomain,
                        selectedDate: $selectedDate
                    )
                }
            }
            // Event lanes AFTER — each preceded by its (unit-less) name title.
            ForEach(eventLanes, id: \.series.id) { lane in
                VStack(alignment: .leading, spacing: 4) {
                    Text(lane.series.displayName)
                        .font(.subheadline).fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                    EventLaneChart(
                        series: lane.series,
                        days: lane.days,
                        xDomain: xDomain,
                        selectedDate: $selectedDate
                    )
                }
            }
        }
        // Container boundary: the `explore.lanes` id bleeds down and MASKS each lane's
        // `explore.lane.<series.id>`(`.event`) id (every lane Chart would otherwise resolve to
        // `explore.lanes` in the a11y tree), making the per-lane ids untargetable by the E2E. `.contain`
        // shields the descendant lane ids so they survive — the SAME id-bleed fix the scrub proxy below
        // applies (the Phase-13 discovery).
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("explore.lanes")
        // The SINGLE multi-series tooltip — ONE card over the whole stack (not per-lane), shown only
        // while scrubbing. Pinned at the top so it never clips at window edges / the finger.
        .overlay(alignment: .top) {
            if let day = scrubDay {
                multiSeriesTooltip(day)
            }
        }
        // E2E seam: a DEBUG `-uitest-reset`-paired launch can inject a deterministic selection day so
        // XCUITests assert the tooltip via a11y ids with zero gesture coordinates. The
        // `xDomain.contains(day)` + `selectedDate == nil` guards are LOAD-BEARING (a day outside the
        // window must not seed an out-of-domain scrub; the nil guard keeps it from clobbering a live
        // scrub). Keyed `.task(id:)` over `.onAppear` so the seed is idempotent + recompute-safe.
        // Production: the env value is nil, so this branch never runs.
        .task(id: uiTestScrubDay) {
            if let day = uiTestScrubDay, xDomain.contains(day), selectedDate == nil {
                selectedDate = day
            }
        }
        .animation(reduceMotion ? nil : .default, value: selectedDate)
    }

    /// The lane title carrying name + unit ("Sleep — h", "Stress — of 10") — the recorded inset
    /// mechanism's scale signal. Unit-less series (none on the quantitative path here) fall back to name.
    private func laneTitle(_ series: ExploreSeries) -> String {
        series.unit.isEmpty ? series.displayName : "\(series.displayName) — \(series.unit)"
    }

    /// The shared scrub day (startOfDay) — `nil` when no active scrub. The single source the tooltip
    /// and every lane's rule mark agree on.
    private var scrubDay: Date? {
        selectedDate.map { calendar.startOfDay(for: $0) }
    }

    // MARK: - The single multi-series tooltip (RESEARCH Pattern 3 — one card, every visible series)

    /// ONE card: the scrubbed date ONCE, then one `.caption` line per visible series. The visual card is
    /// `.accessibilityHidden(true)`; the readout + ids ride the transparent proxy below.
    private func multiSeriesTooltip(_ day: Date) -> some View {
        VStack(alignment: .leading, spacing: 4) {   // xs line spacing
            // Date header — once, semibold (the SAME format vocabulary as the axis labels).
            Text(day, format: .dateTime.month().day())
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
            // One line per quantitative series (resolved exact-day) …
            ForEach(quantitativeLanes, id: \.series.id) { lane in
                Text(quantitativeLine(lane.series, points: lane.points, day: day))
                    .font(.caption)
                    .foregroundStyle(.primary)
            }
            // … then one line per event series (resolved via isPresent, Pitfall 2).
            ForEach(eventLanes, id: \.series.id) { lane in
                Text(eventLine(lane.series, day: day))
                    .font(.caption)
                    .foregroundStyle(.primary)
            }
        }
        .padding(8)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
        // Hidden from the a11y tree: Swift Charts never exposes annotation content anyway, and the
        // readout/ids live on the proxy — hiding this PREVENTS a duplicate element.
        .accessibilityHidden(true)
        // The a11y proxy: a transparent, non-hit-testable overlay carrying the per-series readout + ids
        // (the gesture-free path the honesty E2E asserts).
        .overlay { scrubAccessibilityProxy(day) }
    }

    /// "{displayName}: {real-unit readout}" for a logged day, or "{displayName}: Not logged" for a gap
    /// day — resolved via the exact-day `ChartScrubSelection.resolve` (the ONLY raw-date→value path).
    private func quantitativeLine(_ series: ExploreSeries, points: [InsightPoint], day: Date) -> String {
        let resolution = ChartScrubSelection.resolve(day, in: points, calendar: calendar)
        if let value = resolution?.point?.value {
            return "\(series.displayName): \(SeriesNormalization.readout(value, kind: series.scaleKind, unit: series.unit))"
        }
        return "\(series.displayName): \(ExploreCopy.notLogged)"
    }

    /// "{displayName}: Logged" when the event was present that day, else "{displayName}: Not logged" —
    /// resolved via `series.isPresent(in: snapshotForThatDay)` (Pitfall 2: NO `ChartScrubSelection`
    /// path for events; a missing snapshot = the day was never logged = Not logged).
    private func eventLine(_ series: ExploreSeries, day: Date) -> String {
        let present = snapshotsByDay[calendar.startOfDay(for: day)].map { series.isPresent(in: $0) } ?? false
        let value = present ? ExploreCopy.eventTagged : ExploreCopy.notLogged
        return "\(series.displayName): \(value)"
    }

    // MARK: - The transparent a11y proxy (the gesture-free readout path; the id-bleed shield)

    /// A transparent, non-hit-testable overlay carrying ONE quiet element per visible series whose
    /// `.accessibilityValue` is the full per-series readout, plus (gap/absent days) a `.notLogged`-suffixed
    /// marker id the honesty E2E asserts. Shielded with `.accessibilityElement(children: .contain)` so a
    /// parent `explore.scrub.tooltip` id can't mask the per-series ids.
    private func scrubAccessibilityProxy(_ day: Date) -> some View {
        HStack(spacing: 0) {
            ForEach(quantitativeLanes, id: \.series.id) { lane in
                let resolution = ChartScrubSelection.resolve(day, in: lane.points, calendar: calendar)
                let logged = resolution?.point != nil
                Color.clear
                    .frame(width: 8, height: 8)
                    .accessibilityElement(children: .ignore)
                    // A value-only element reads malformed to VoiceOver — give it the date as the label
                    // so it announces label + value coherently; the value carries the full readout.
                    .accessibilityLabel(Text(day.formatted(.dateTime.month().day())))
                    .accessibilityValue(Text(quantitativeLine(lane.series, points: lane.points, day: day)))
                    .accessibilityIdentifier(logged
                        ? "explore.scrub.tooltip.\(lane.series.id)"
                        : "explore.scrub.tooltip.\(lane.series.id).notLogged")
            }
            ForEach(eventLanes, id: \.series.id) { lane in
                let present = snapshotsByDay[calendar.startOfDay(for: day)].map { lane.series.isPresent(in: $0) } ?? false
                Color.clear
                    .frame(width: 8, height: 8)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(day.formatted(.dateTime.month().day())))
                    .accessibilityValue(Text(eventLine(lane.series, day: day)))
                    .accessibilityIdentifier(present
                        ? "explore.scrub.tooltip.\(lane.series.id)"
                        : "explore.scrub.tooltip.\(lane.series.id).notLogged")
            }
        }
        // Container boundary: the parent `explore.scrub.tooltip` id bleeds down and MASKS bare
        // descendant ids; ids one level inside an explicit container survive — so the proxy ids are
        // shielded behind `.contain`.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("explore.scrub.tooltip")
        .allowsHitTesting(false)
    }
}
