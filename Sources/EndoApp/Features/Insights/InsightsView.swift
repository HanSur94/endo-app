// Requirement: INST-01..04 / D-05 / D-07 / D-08 / D-12 / D-04 — the Insights dashboard (the Wave 4
// payoff screen).
//
// Cite UI-SPEC §"Component & Interaction Inventory" A/C: a grouped `List` inside a `NavigationStack`,
// navigation title "Insights", a11y id `insights.screen`. The reused `DisclaimerBanner` (renders
// `Disclaimer.text` verbatim) is PINNED at the top so the not-medical-advice framing is always visible
// with the insights (D-12), a11y id `insights.disclaimer`.
//
// SECTIONS (in order, UI-SPEC §A): Trends (`.trend`) → Cycle phase (`.cyclePhase`) → Possible
// associations (`.correlation`). A section with ZERO gated insights is OMITTED entirely — NOT an empty
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
import SwiftData
import EndoInsights

struct InsightsView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    /// The integration brain (08-02): `insights` + `isUpdating`, driven by `loadCached()`/`dataChanged()`.
    @Environment(\.insightsCoordinator) private var coordinator

    var body: some View {
        NavigationStack {
            List {
                // Pinned not-medical-advice disclaimer (D-12) — always visible with the insights.
                Section {
                    DisclaimerBanner()
                        .accessibilityIdentifier("insights.disclaimer")
                }

                if coordinator.insights.isEmpty {
                    // Warm empty state (NOT empty sections) — nothing gated yet.
                    emptyState
                } else {
                    // The three kind-sections, in order. Each is omitted entirely when it has zero
                    // insights (so suppressed → NO cycle-phase section — D-07).
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
                }
            }
            .navigationTitle("Insights")
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
            Section(title) {
                ForEach(kindInsights) { insight in
                    InsightCardView(insight: insight) {
                        dismiss(insight)
                    }
                }
            }
            .accessibilityIdentifier(identifier)
        }
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

    /// Persist the dismissal (Phase-6 `DismissedInsight`, syncs via Phase 4 — stays gone across sessions
    /// and devices) and trigger a recompute that EXCLUDES the dismissed signalKey (D-10). The view owns
    /// the Repository seam (mirrors `HomeView`); the card only signals intent via its `onDismiss` closure.
    private func dismiss(_ insight: Insight) {
        try? SwiftDataRepository(context: modelContext).dismissInsight(signalKey: insight.signalKey)
        coordinator.dataChanged()
    }
}
