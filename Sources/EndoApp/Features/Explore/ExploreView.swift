// Requirement: EXPL-01 (the consent-gated third tab's screen) / EXPL-02 (the picker surface) /
// EXPL-06 (range presets) / EXPL-07 (the honest empty + sparse states) — the Explore SCREEN.
//
// The user-facing surface that drives the lanes. Mirrors `InsightsView`'s scaffold but DIVERGES (LOCKED,
// Pitfall 7 / UI-SPEC §A): a `ScrollView`, NOT a `List` — the lanes are a fixed FREE-SCRUB surface, so
// they must not live inside a scrolling `List` (this resolves the Phase-13 scroll-vs-scrub tension by
// construction). Reads `Repository.daySnapshots()` ONCE on appear (mirroring the dashboard's per-view
// `SwiftDataRepository(context:)` seam); BYPASSES `InsightsEngine`/`HonestyGate` entirely (Explore shows
// RAW descriptive data — no confidence pills, no associations, no causal framing).
//
// PRIVACY (T-15-12, LOCKED): owns the `ExploreSelection` in EPHEMERAL view `@State` — never a persistent
// property-wrapper store. Persisting series keys would leak health-interest metadata outside the
// consent-gated store. The grep gate forbids any such persistence wrapper in this file (the wording here
// avoids the literal wrapper tokens so the token-based gate stays clean).
//
// PERFORMANCE (Pitfall 4): the per-series prepared points/days + the day-keyed snapshot map + the shared
// xDomain are computed ONCE per selection/window/snapshots change into a cached `@State` value — NOT in
// `body`, NOT per scrub frame. The scrub (`selectedDate`) drives only the tooltip, never a recompute.
//
// THREE STATES (EXPL-07):
//   • EMPTY  — nothing selected → the warm "pick a series" state (id `explore.empty`).
//   • SPARSE — selected but too few logged days in the window to plot → "not enough data" (id `explore.sparse`).
//   • LANES  — otherwise → `ExploreLanesView` with the prepared data + the shared xDomain + `$selectedDate`.
// Unlogged days stay GAPS in every rendered lane regardless of state (honesty constraint 1).
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import SwiftData
import EndoInsights

struct ExploreView: View {
    /// The per-view repository seam (mirrors `HomeView`/`InsightsView`'s dismiss-path construction): a
    /// `SwiftDataRepository(context: modelContext)` is built on the main actor to read `daySnapshots()`.
    @Environment(\.modelContext) private var modelContext

    /// The EPHEMERAL Explore selection (series Set + window). Plain view `@State`, never a persistent
    /// property-wrapper store — a LOCKED privacy invariant (T-15-12).
    @State private var selection = ExploreSelection()

    /// The ONE shared scrub date driving every lane — owned here, passed by `@Binding` into the lane
    /// stack so a single `RuleMark` crosses every lane at the same day (EXPL-05).
    @State private var selectedDate: Date?

    /// The snapshots read ONCE on appear. The screen's single source of truth for the window's data.
    @State private var snapshots: [DaySnapshot] = []

    /// The prepared lane inputs, recomputed ONCE per selection/window/snapshots change (Pitfall 4) — never
    /// in `body`, never per scrub frame.
    @State private var prepared = PreparedLanes()

    /// Calendar for day-boundary math + the window span. `.current` for display (the user's local dates).
    private var calendar: Calendar { .current }

    /// The suppression flag driving the picker filter — the latest snapshot's `suppressed` (defaults to
    /// `false` when there is no data yet, matching the accessor posture).
    private var suppressed: Bool {
        snapshots.last?.suppressed ?? false
    }

    var body: some View {
        NavigationStack {
            ScrollView {   // NOT a List — the lanes are a fixed free-scrub surface (Pitfall 7 / UI-SPEC §A)
                VStack(alignment: .leading, spacing: 24) {   // lg inter-block gaps
                    // Pinned not-medical-advice disclaimer (reused verbatim — never duplicated).
                    DisclaimerBanner()
                        .accessibilityIdentifier("explore.disclaimer")

                    // The control surface: the suppression-filtered chip picker + the range presets.
                    SeriesPickerView(selection: $selection, suppressed: suppressed)

                    // The lanes OR an honest state.
                    if selection.series.isEmpty {
                        emptyState
                    } else if isTooSparse {
                        sparseState
                    } else {
                        ExploreLanesView(
                            quantitativeLanes: prepared.quantitativeLanes,
                            eventLanes: prepared.eventLanes,
                            snapshotsByDay: prepared.snapshotsByDay,
                            xDomain: prepared.xDomain,
                            selectedDate: $selectedDate
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Explore")
            .accessibilityIdentifier("explore.screen")
        }
        .onAppear {
            // Read the data source ONCE (mirror the dashboard's per-view repository seam) then prepare.
            snapshots = loadSnapshots()
            recompute()
        }
        // Recompute the prepared lanes ONCE per selection change (series add/remove or window switch) —
        // NOT per scrub frame. The scrub binding is deliberately NOT a trigger here.
        .onChange(of: selection) { _, _ in
            recompute()
        }
    }

    // MARK: - Data read (mirror InsightsView's per-view repository seam)

    /// Read the per-day snapshots ONCE via a per-view `SwiftDataRepository`. `(try? …) ?? []` so a read
    /// failure degrades to "no data" (the honest empty state), never a crash in a health app.
    private func loadSnapshots() -> [DaySnapshot] {
        (try? SwiftDataRepository(context: modelContext).daySnapshots()) ?? []
    }

    // MARK: - Prepare lane inputs ONCE per selection/window change (Pitfall 4)

    /// Build the prepared per-series points/days + the day-keyed snapshot map + the shared xDomain from
    /// the current selection + snapshots. Called from `onAppear` and on every selection change — NEVER in
    /// `body` and NEVER per scrub frame.
    private func recompute() {
        // Selected series split by kind, preserving the catalog's stable display order.
        let selectedOrdered = ExploreSeriesCatalog.all.filter { selection.series.contains($0) }
        let quantSeries = selectedOrdered.filter { $0.scaleKind.isQuantitative }
        let eventSeries = selectedOrdered.filter { !$0.scaleKind.isQuantitative }

        let quantitativeLanes = quantSeries.map { series in
            (series: series,
             points: ExploreSeriesExtraction.quantitativePoints(snapshots, series: series, calendar: calendar))
        }
        let eventLanes = eventSeries.map { series in
            (series: series,
             days: ExploreSeriesExtraction.occurrenceDays(snapshots, series: series, calendar: calendar))
        }

        // The day-keyed snapshot map for the event-lane tooltip lookup (Pitfall 2: events resolve via
        // `isPresent(in:)`, NOT `ChartScrubSelection`).
        var snapshotsByDay: [Date: DaySnapshot] = [:]
        for snap in snapshots {
            snapshotsByDay[calendar.startOfDay(for: snap.day)] = snap
        }

        prepared = PreparedLanes(
            quantitativeLanes: quantitativeLanes,
            eventLanes: eventLanes,
            snapshotsByDay: snapshotsByDay,
            xDomain: xDomain(for: selection.window,
                             quantitativeLanes: quantitativeLanes,
                             eventLanes: eventLanes)
        )
    }

    /// The SHARED absolute-date X domain — the window's span ending at `today`, derived ONCE here and
    /// passed IDENTICAL into every lane (the `InsightTrendChart.xDomain` math, lifted: window range even
    /// when sparse, so "3 points in 30 days" reads as sparse-over-a-month). `.allTime` spans from the
    /// earliest logged point across ALL selected series (quantitative + event) to today.
    private func xDomain(
        for window: TrendWindow,
        quantitativeLanes: [(series: ExploreSeries, points: [InsightPoint])],
        eventLanes: [(series: ExploreSeries, days: [Date])]
    ) -> ClosedRange<Date> {
        let end = calendar.startOfDay(for: .now)
        let start: Date
        switch window {
        case .last7:   start = calendar.date(byAdding: .day, value: -7, to: end) ?? end
        case .last30:  start = calendar.date(byAdding: .day, value: -30, to: end) ?? end
        case .last90:  start = calendar.date(byAdding: .day, value: -90, to: end) ?? end
        case .allTime:
            let earliestQuant = quantitativeLanes.flatMap { $0.points.map(\.date) }.min()
            let earliestEvent = eventLanes.flatMap { $0.days }.min()
            let earliest = [earliestQuant, earliestEvent].compactMap { $0 }.min()
            start = earliest ?? (calendar.date(byAdding: .day, value: -30, to: end) ?? end)
        }
        return min(start, end) ... end
    }

    /// "Too sparse" (honest, EXPL-07): selected series exist, but fewer than a small threshold of logged
    /// data points across ALL selected series fall in the window — too few to plot a meaningful lane.
    /// Gaps stay gaps; this never fabricates a value. Threshold = 2 (a single isolated point can't read as
    /// a trend; the empty/lanes split is by SELECTION, this split is by logged DENSITY).
    private var isTooSparse: Bool {
        let domain = prepared.xDomain
        let quantLogged = prepared.quantitativeLanes
            .flatMap { $0.points.map(\.date) }
            .filter { domain.contains($0) }
            .count
        let eventLogged = prepared.eventLanes
            .flatMap { $0.days }
            .filter { domain.contains($0) }
            .count
        return (quantLogged + eventLogged) < 2
    }

    // MARK: - The two honest states (EXPL-07 — clone InsightsView.emptyState)

    /// EMPTY: nothing selected → the warm "pick a series" invitation (NOT a fake lane, NOT empty axes).
    private var emptyState: some View {
        honestState(
            heading: ExploreCopy.emptyHeading,
            body: ExploreCopy.emptyBody,
            identifier: "explore.empty"
        )
    }

    /// SPARSE: selected but too few logged days to plot → the honest "not enough data" signal (never a
    /// blank lane read as "0").
    private var sparseState: some View {
        honestState(
            heading: ExploreCopy.sparseHeading,
            body: ExploreCopy.sparseBody,
            identifier: "explore.sparse"
        )
    }

    /// The shared honest-state body (clone of `InsightsView.emptyState`): icon (`square.stack.3d.up`,
    /// `.secondary`) + heading + body, `.fixedSize(…, vertical: true)` so honest framing never truncates,
    /// `.accessibilityElement(children: .combine)` so VoiceOver reads it as one element.
    private func honestState(heading: String, body: String, identifier: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "square.stack.3d.up")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            Text(heading)
                .font(.headline)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(body)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

// MARK: - PreparedLanes (the cached lane inputs — recomputed once per selection/window change)

/// The prepared lane inputs the screen caches and feeds into `ExploreLanesView`. Computed ONCE per
/// selection/window/snapshots change (Pitfall 4), never per scrub frame.
private struct PreparedLanes {
    var quantitativeLanes: [(series: ExploreSeries, points: [InsightPoint])] = []
    var eventLanes: [(series: ExploreSeries, days: [Date])] = []
    var snapshotsByDay: [Date: DaySnapshot] = [:]
    /// A harmless default domain (today…today) before the first `recompute()`; replaced on appear.
    var xDomain: ClosedRange<Date> = {
        let day = Calendar.current.startOfDay(for: .now)
        return day ... day
    }()
}
