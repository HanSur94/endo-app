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

    /// The cross-tab scatter deep-link router (SCAT-04). The Insights `.correlation` card affordance sets a
    /// non-sensitive `PendingScatter(x, y)` payload; this screen consumes it on appear / on change to prefill
    /// the scatter X/Y + switch to scatter mode, then CLEARS it (so a later identical tap re-triggers). The
    /// payload lands in the existing EPHEMERAL `mode`/`scatterX`/`scatterY` `@State` — never persisted.
    @Environment(\.exploreScatterRouter) private var scatterRouter

    /// The EPHEMERAL Explore selection (series Set + window). Plain view `@State`, never a persistent
    /// property-wrapper store — a LOCKED privacy invariant (T-15-12).
    @State private var selection = ExploreSelection()

    /// The EPHEMERAL view mode — `.lanes` (the shipped Explore behavior, the default) or `.scatter` (the
    /// Phase-16 second mode). Plain view `@State`, NEVER persisted — persisting a Scatter-interest would
    /// leak health-interest metadata outside the consent-gated store (T-16-06, the same LOCKED privacy
    /// invariant as `selection`).
    @State private var mode: ExploreMode = .lanes

    /// The EPHEMERAL scatter X / Y axis selection (a quantitative `ExploreSeries?` each). Plain view
    /// `@State`, NEVER persisted (same privacy invariant — T-16-06).
    @State private var scatterX: ExploreSeries?
    @State private var scatterY: ExploreSeries?

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

                    // The `Lanes | Scatter` mode toggle, pinned at the TOP of the content column (above the
                    // picker block). Default `.lanes` so the shipped Explore behavior is unchanged until the
                    // user opts into Scatter. Mode is EPHEMERAL view `@State`, never persisted (T-16-06).
                    Picker("Mode", selection: $mode) {
                        Label(ExploreCopy.modeLanes, systemImage: "square.stack.3d.up")
                            .tag(ExploreMode.lanes)
                            .accessibilityIdentifier("explore.mode.lanes")
                        Label(ExploreCopy.modeScatter, systemImage: "chart.dots.scatter")
                            .tag(ExploreMode.scatter)
                            .accessibilityIdentifier("explore.mode.scatter")
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("explore.mode")

                    if mode == .lanes {
                        lanesContent
                    } else {
                        scatterContent
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
            // SCAT-04: a deep-link that lands the user on the Explore tab BEFORE this view observes the
            // router change still prefills — consume any pending payload already present on appear.
            consumePendingScatter()
        }
        // Recompute the prepared lanes ONCE per selection change (series add/remove or window switch) —
        // NOT per scrub frame. The scrub binding is deliberately NOT a trigger here.
        .onChange(of: selection) { _, _ in
            recompute()
        }
        // SCAT-04: when the Insights `.correlation` affordance sets a non-nil `PendingScatter`, prefill the
        // scatter (mode + X/Y) and CLEAR the payload so an identical later tap re-triggers.
        .onChange(of: scatterRouter.pendingScatter) { _, _ in
            consumePendingScatter()
        }
    }

    // MARK: - Scatter deep-link consume (SCAT-04 — prefill + switch to scatter, then clear)

    /// Consume a pending scatter deep-link (SCAT-04): if the router carries a non-`nil` `PendingScatter`,
    /// switch to scatter mode, prefill the X/Y axes from the typed payload, then CLEAR the payload so a
    /// later identical tap (same x/y) re-triggers. The payload lands ONLY in the existing ephemeral
    /// `mode`/`scatterX`/`scatterY` `@State` — never persisted. A no-op when there is no pending payload
    /// (so the default open stays `.lanes`).
    private func consumePendingScatter() {
        guard let pending = scatterRouter.pendingScatter else { return }
        mode = .scatter
        scatterX = pending.x
        scatterY = pending.y
        scatterRouter.pendingScatter = nil
    }

    // MARK: - Lanes mode content (the shipped Explore behavior — UNCHANGED, just lifted into a property)

    /// The Lanes-mode body: the suppression-filtered chip picker + the range presets, then the lanes OR an
    /// honest state. EMPTY keys off what actually RENDERS (the prepared lanes), NOT the raw selection — so a
    /// suppressed user who carries only a (filtered) bleeding id sees the "pick a series" invitation, never
    /// an empty lane (WR-01). This is the Phase-15 block verbatim — the scatter mode does not touch it.
    @ViewBuilder
    private var lanesContent: some View {
        // The control surface: the suppression-filtered chip picker + the range presets.
        SeriesPickerView(selection: $selection, suppressed: suppressed)

        if prepared.quantitativeLanes.isEmpty && prepared.eventLanes.isEmpty {
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

    // MARK: - Scatter mode content (SCAT-01/03 — the axis picker + the empty/sparse/plot split)

    /// The minimum BOTH-logged days the scatter needs to plot meaningfully. Below this the cloud is too
    /// few dots to read as anything but noise, so the honest sparse state shows instead (UI-SPEC §D — the
    /// count-sum sparse check, mirroring the Phase-15 per-lane minimum).
    private static let minScatterDays = 5

    /// The Scatter-mode body: the two quantitative-only axis selectors, then the empty / sparse / plot
    /// split. The both-logged cells are recomputed from `snapshots` + X/Y ONLY (no scrub coupling — the
    /// scatter has no scrub) — never per scrub frame.
    @ViewBuilder
    private var scatterContent: some View {
        ScatterAxisPicker(xSeries: $scatterX, ySeries: $scatterY, suppressed: suppressed)

        // The both-logged cells — recomputed from snapshots + the chosen X/Y only (empty until both chosen).
        let scatterPoints: [ScatterPairing.ScatterPoint] = {
            guard let x = scatterX, let y = scatterY else { return [] }
            return ScatterPairing.pairs(snapshots, x: x, y: y, calendar: calendar)
        }()

        if scatterX == nil || scatterY == nil {
            // EMPTY — neither (or only one) axis chosen yet → "pick two factors" (never empty axes).
            honestState(
                heading: ExploreCopy.scatterEmptyHeading,
                body: ExploreCopy.scatterEmptyBody,
                identifier: "explore.scatter.empty"
            )
        } else if scatterPoints.reduce(0, { $0 + $1.count }) < Self.minScatterDays {
            // SPARSE — both chosen, but too few both-logged days to plot meaningfully (count-sum check).
            honestState(
                heading: ExploreCopy.scatterSparseHeading,
                body: ExploreCopy.scatterSparseBody,
                identifier: "explore.scatter.sparse"
            )
        } else {
            ScatterPlotView(points: scatterPoints, xSeries: scatterX!, ySeries: scatterY!)
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
        // The series the picker is currently ALLOWED to offer (suppression removes both bleeding series).
        // WR-01: a suppressed user who still carries a bleeding id in the selection set must NOT get an
        // empty titled lane — prune the selection to the offered set BEFORE building lanes, reusing the
        // single source of truth (`ExploreSelection.offered`). The Phase-14 accessor gates the DATA to
        // empty; this gates the LANE so it never renders at all.
        let offered = Set(ExploreSelection.offered(suppressed: suppressed).map(\.id))
        // Selected series split by kind, preserving the catalog's stable display order; suppressed-gated
        // series are dropped from what actually renders.
        let selectedOrdered = ExploreSeriesCatalog.all.filter {
            selection.series.contains($0) && offered.contains($0.id)
        }
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
        // IN-04: a guaranteed 30-day-back date that NEVER collapses to `end` — used as the fallback so a
        // degenerate `calendar.date(byAdding:)` (never with `.current`) can't produce a single-day
        // `end…end` domain that would render nonsense. 30 days × 86400s is the honest default window span.
        let safeFallbackStart = end.addingTimeInterval(-30 * 86_400)
        let start: Date
        switch window {
        case .last7:   start = calendar.date(byAdding: .day, value: -7, to: end) ?? end.addingTimeInterval(-7 * 86_400)
        case .last30:  start = calendar.date(byAdding: .day, value: -30, to: end) ?? safeFallbackStart
        case .last90:  start = calendar.date(byAdding: .day, value: -90, to: end) ?? end.addingTimeInterval(-90 * 86_400)
        case .allTime:
            let earliestQuant = quantitativeLanes.flatMap { $0.points.map(\.date) }.min()
            let earliestEvent = eventLanes.flatMap { $0.days }.min()
            let earliest = [earliestQuant, earliestEvent].compactMap { $0 }.min()
            start = earliest ?? safeFallbackStart
        }
        return min(start, end) ... end
    }

    /// The minimum logged points a SINGLE lane needs to be more than an isolated dot. A lane with one
    /// point in the window reads as a lone floating mark, not a series — below this, the lane is "sparse".
    private static let minPointsPerLane = 2

    /// "Too sparse" (honest, EXPL-07): renderable series exist, but NO single lane has enough logged days
    /// in the window to be more than an isolated dot — so every lane would be a lone floating mark.
    /// WR-02: this is a PER-LANE test, not a global sum — 2 lanes with 1 point each (global sum 2) is
    /// still two isolated dots and IS sparse; one lane with ≥2 points is enough to render meaningfully.
    /// Gaps stay gaps; this never fabricates a value. (The empty/lanes split is by what RENDERS; this
    /// split is by per-lane logged density.)
    private var isTooSparse: Bool {
        let domain = prepared.xDomain
        let quantHasEnough = prepared.quantitativeLanes.contains { lane in
            lane.points.filter { domain.contains($0.date) }.count >= Self.minPointsPerLane
        }
        let eventHasEnough = prepared.eventLanes.contains { lane in
            lane.days.filter { domain.contains($0) }.count >= Self.minPointsPerLane
        }
        // Sparse when NO lane clears the per-lane minimum (every lane would be ≤1 isolated mark).
        return !(quantHasEnough || eventHasEnough)
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

// MARK: - ExploreMode (the ephemeral Lanes|Scatter view mode — SCAT-01)

/// The Explore view mode: `.lanes` (the shipped overlay-lanes behavior, the default) or `.scatter` (the
/// Phase-16 two-axis scatter). Held in EPHEMERAL view `@State` — NEVER persisted (T-16-06, the LOCKED
/// privacy invariant: persisting a Scatter-interest would leak health-interest metadata outside the
/// consent-gated store). `String`-raw + `CaseIterable` mirrors the `TrendWindow` posture for stable tags.
private enum ExploreMode: String, CaseIterable {
    case lanes
    case scatter
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
