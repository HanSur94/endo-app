// Requirement: INST-01..04 / D-05 / D-07 / D-08 / D-12 / D-04 — the Insights dashboard (the Wave 4
// payoff screen).
//
// Cite UI-SPEC §"Component & Interaction Inventory" A/C: a grouped `List` inside a `NavigationStack`,
// navigation title "Insights", a11y id `insights.screen`. The reused `DisclaimerBanner` (renders
// `Disclaimer.text` verbatim) is PINNED at the top so the not-medical-advice framing is always visible
// with the insights (D-12), a11y id `insights.disclaimer`.
//
// SECTIONS (in order, UI-SPEC §A): Flare early-warning (`.flare`, placed FIRST — most time-sensitive, but
// CALMLY styled, D-07) → Trends (`.trend`) → Cycle phase (`.cyclePhase`) → Possible associations
// (`.correlation`) → Recommendations (`.recommendation`, after the associations they derive from — D-06).
// A section with ZERO gated insights is OMITTED entirely — NOT an empty
// shell. So a suppressed/no-cycle user (the engine emits zero `.cyclePhase`) has NO cycle-phase section
// at all (D-07; mirrors `home.cycleSummary.suppressed`'s no-phantom posture). The E2E asserts this by
// the ABSENCE of `insights.section.cyclePhase`.
//
// THREE HONEST STATES (UI-SPEC §C):
//   • Empty   — zero gated insights → a single warm full-screen empty state (NOT empty sections),
//               a11y id `insights.empty`. No fake/placeholder cards.
//   • Sparse  — a per-signal "Not enough data yet." card (`insight.isInsufficiencyNotice`), rendered by
//               `InsightCardView` (a11y id `insights.card.<signalKey>.insufficient`).
//   • Suppressed — the cycle-phase section is provably absent (see above).
//
// INSTANT LOAD + RECOMPUTE (D-04 / SC2): on appear we call `coordinator.loadCached()` (the screen paints
// instantly from the fingerprint-keyed cache) then `coordinator.dataChanged()` (kick a debounced off-main
// recompute). While `coordinator.isUpdating`, a small NON-BLOCKING "Updating…" caption shows (a11y id
// `insights.updating`) — NEVER a full-screen blocking spinner; cached content stays visible.
//
// DISMISS (D-09/D-10): each card's `onDismiss` builds a `SwiftDataRepository(context: modelContext)` (mirror
// `HomeView`'s seam), calls `dismissInsight(signalKey:)` (persists a Phase-6 `DismissedInsight`, syncs via
// Phase 4, stays gone), then `coordinator.dataChanged()` so the recompute EXCLUDES it.
//
// FOREGROUND (D-01): `@Environment(\.scenePhase)` → on `.active` we call `coordinator.dataChanged()` so a
// return-to-foreground refreshes the insights without a full reload.
//
// MV pattern (D-12): the view observes the injected `@Observable InsightsCoordinator`; no ViewModel.
import SwiftUI
import EndoInsights

struct InsightsView: View {
    @Environment(\.scenePhase) private var scenePhase
    /// The integration brain (08-02): `insights` + `isUpdating`, driven by `loadCached()`/`dataChanged()`.
    @Environment(\.insightsCoordinator) private var coordinator

    /// The cross-tab scatter deep-link router (SCAT-04). The `.correlation` card affordance sets its
    /// `pendingScatter` payload; the consented-branch `ExploreView` consumes it. Observed ONLY here in the
    /// consented shell (consent-by-construction). Wired into the card in Task 2.
    @Environment(\.exploreScatterRouter) private var scatterRouter

    /// The consented shell's active-tab binding (SCAT-04). The `.correlation` card affordance selects
    /// `.explore` through this after setting the router payload, so the deep-link switches to the scatter.
    /// A default `.constant(.insights)` keeps previews/tests that omit it compiling.
    var selectedTab: Binding<ConsentedTab> = .constant(.insights)

    /// The per-view repository seam (mirrors `ExploreView`/`HomeView`'s construction) for the ONE
    /// suppression read that gates the scatter deep-link affordance (SCAT-04).
    @Environment(\.modelContext) private var modelContext

    /// The bleeding-suppression flag driving the `.correlation` card's scatter-deep-link gate (SCAT-04) —
    /// the latest snapshot's `suppressed` (defaults to `false` when there is no data, matching the accessor
    /// posture). A `(try? …) ?? false` per-view read mirroring `ExploreView.suppressed`; a read failure
    /// degrades to "not suppressed" (the resolver still HIDES a bleeding axis only when suppressed, so a
    /// failure can never EXPOSE a bleeding axis it should hide — it just keeps the normal gate).
    private var suppressed: Bool {
        ((try? SwiftDataRepository(context: modelContext).daySnapshots())?.last?.suppressed) ?? false
    }

    var body: some View {
        NavigationStack {
            List {
                // Serif header + honest subtitle (presentational chrome). The subtitle reuses neutral,
                // ClaimsPolicy-clean vocabulary ("patterns in your own data") — no forbidden verb,
                // gendered token, imperative, or alarmist term.
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Insights").bloomTitle()
                        Text("Patterns in your own data, as they build over time.")
                            .font(.subheadline)
                            .foregroundStyle(Color.inkSecondary)
                    }
                    .listRowBackground(Color.clear)
                }

                // At-a-glance stat strip — presentational chrome derived from the already-loaded
                // coordinator.insights (no new data source, no fabricated stats).
                if !coordinator.insights.isEmpty {
                    Section {
                        statStrip
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets())
                    }
                }

                // Pinned not-medical-advice disclaimer (D-12) — always visible with the insights.
                Section {
                    DisclaimerBanner()
                        .accessibilityIdentifier("insights.disclaimer")
                }

                if coordinator.insights.isEmpty {
                    // Warm empty state (NOT empty sections) — nothing gated yet.
                    emptyState
                } else {
                    // The kind-sections, in order. Each is omitted entirely when it has zero
                    // insights (so suppressed → NO cycle-phase section — D-07).
                    // Flare early-warning is placed FIRST (D-07 / UI-SPEC §A): the most time-sensitive
                    // section sits at the top so it is the most discoverable — BUT it is rendered with the
                    // IDENTICAL calm `section(...)` helper + reused InsightCardView (NO alarm color, NO
                    // badge, NO exclamation): time-sensitivity is conveyed by POSITION only, never by
                    // alarm visuals (the "warning that must not alarm" contract). Omit-when-empty is
                    // inherited from the helper, so a suppressed/sparse/insufficient user has NO flare
                    // section at all (absence-by-omission honesty — proven by the E2E + the package gate).
                    section(
                        title: FlareCopy.sectionFlare,
                        identifier: "insights.section.flare",
                        kind: .flare)
                    section(
                        title: InsightsCopy.sectionTrends,
                        identifier: "insights.section.trends",
                        kind: .trend)
                    section(
                        title: InsightsCopy.sectionCyclePhase,
                        identifier: "insights.section.cyclePhase",
                        kind: .cyclePhase)
                    section(
                        title: InsightsCopy.sectionCorrelations,
                        identifier: "insights.section.correlations",
                        kind: .correlation)
                    // Recommendations read AFTER the associations they derive from (D-06). Omit-when-empty
                    // is inherited from the helper, so a suppressed/sparse user has NO recommendations
                    // section (D-06/D-08/SC3). Reuses the kind-agnostic InsightCardView + dismiss path.
                    section(
                        title: RecommendationsCopy.sectionRecommendations,
                        identifier: "insights.section.recommendations",
                        kind: .recommendation)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.canvas)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .accessibilityIdentifier("insights.screen")
            .toolbar {
                // Non-blocking recompute affordance (SC2): a subtle "Updating…" caption, NEVER a
                // full-screen blocking spinner. Cached content stays on screen during a recompute.
                ToolbarItem(placement: .topBarTrailing) {
                    if coordinator.isUpdating {
                        HStack(spacing: 6) {
                            ProgressView().controlSize(.small)
                            Text(InsightsCopy.updating)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityIdentifier("insights.updating")
                    }
                }
            }
        }
        .onAppear {
            // Instant cache load (D-04), then kick a debounced off-main recompute (08-02).
            coordinator.loadCached()
            coordinator.dataChanged()
        }
        // Foreground recompute (D-01): a return-to-active refreshes the insights non-blockingly.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                coordinator.dataChanged()
            }
        }
    }

    // MARK: - Section (omitted when it has zero insights — D-07)

    /// A grouped `Section` of insight cards for ONE kind, or `EmptyView` when that kind has no gated
    /// insights (so the section never renders as an empty shell — the suppressed cycle-phase case).
    @ViewBuilder
    private func section(title: String, identifier: String, kind: InsightKind) -> some View {
        let kindInsights = coordinator.insights.filter { $0.kind == kind }
        if !kindInsights.isEmpty {
            // Read the suppression flag ONCE per section render (not per card) — the same value gates every
            // card's scatter affordance (SCAT-04).
            let isSuppressed = suppressed
            Section(title) {
                ForEach(kindInsights) { insight in
                    InsightCardView(
                        insight: insight,
                        onDismiss: { dismiss(insight) },
                        suppressed: isSuppressed,
                        onOpenScatter: { pending in
                            // SCAT-04: carry the resolver's TYPED axes to the router, then switch to the
                            // Explore tab so the consented-branch `ExploreView` consumes the payload (prefill
                            // + mode=.scatter). Order: set payload BEFORE selecting the tab so the value is
                            // present the moment ExploreView observes the router on appear/onChange.
                            scatterRouter.pendingScatter = pending
                            selectedTab.wrappedValue = .explore
                        }
                    )
                }
            }
            .accessibilityIdentifier(identifier)
        }
    }

    // MARK: - At-a-glance stat strip (presentational; derived from coordinator.insights)

    /// A small strip of derived counts (total surfaced + distinct kinds). Purely presentational chrome
    /// over the already-loaded `coordinator.insights` — no new data source, no fabricated values.
    private var statStrip: some View {
        let total = coordinator.insights.filter { !$0.isInsufficiencyNotice }.count
        let kinds = Set(coordinator.insights.filter { !$0.isInsufficiencyNotice }.map(\.kind)).count
        return HStack(spacing: 12) {
            statTile(value: "\(total)", label: total == 1 ? "Insight" : "Insights")
            statTile(value: "\(kinds)", label: kinds == 1 ? "Category" : "Categories")
        }
        .padding(.vertical, 4)
    }

    private func statTile(value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 26, weight: .semibold))
                .fontDesign(.serif)
                .foregroundStyle(Color.plum)
            Text(label)
                .font(.caption)
                .foregroundStyle(Color.inkSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .bloomCard(padding: 14)
    }

    // MARK: - Empty state (UI-SPEC §C — warm, full-screen, NOT empty sections)

    private var emptyState: some View {
        Section {
            VStack(spacing: 12) {
                Image(systemName: "chart.line.uptrend.xyaxis")
                    .font(.largeTitle)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                Text(InsightsCopy.emptyHeading)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text(InsightsCopy.emptyBody)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            // One contained VoiceOver element reading the heading + body.
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("insights.empty")
        }
    }

    // MARK: - Dismiss (D-09/D-10 — persist + recompute-excludes)

    /// Hand the dismissal to the coordinator (D-09/D-10): it persists the Phase-6 `DismissedInsight`
    /// THROUGH ITS OWN context (syncs via Phase 4 — stays gone across sessions and devices) and triggers a
    /// recompute that EXCLUDES the signalKey. Routing the write through the coordinator (NOT the view's
    /// separate `modelContext`) guarantees the recompute's `dismissedSignalKeys()` read observes it — the
    /// card stays gone, not just until the next recompute re-adds it.
    private func dismiss(_ insight: Insight) {
        coordinator.dismiss(signalKey: insight.signalKey)
    }
}
