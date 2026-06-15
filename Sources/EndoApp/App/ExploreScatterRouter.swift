// Requirement: SCAT-04 — the cross-tab scatter deep-link routing seam.
//
// A tiny `@Observable` router the `.correlation` card affordance (InsightsView, Plan 16-03 Task 2) sets
// when the user taps "See the days behind this". `ExploreView` — built ONLY inside `AppGate`'s
// `.consented` branch — observes it and prefills the scatter X/Y axes + switches to scatter mode. The
// router is injected via an `EnvironmentKey` exactly like `checkInRouter` / `consentStore`.
//
// SECURITY INVARIANT (T-16-11 / T-16-12, mirrors CheckInRouter's T-03-14 posture): the router only carries
// a NON-SENSITIVE intent value — a `PendingScatter` wrapping two `ExploreSeries` (catalog series ids, which
// are already public, non-sensitive identifiers). It carries NO health data and NEVER presents UI itself —
// presentation happens inside the consented branch (`ExploreView`), so a deep-link can never surface health
// UI before consent. The payload is EPHEMERAL: there is NO persistence wrapper (no app-storage,
// user-defaults, or scene-storage property wrapper) — persisting a scatter-interest would leak
// health-interest metadata outside the consent-gated store. The payload is reset to `nil` once consumed so a
// later tap re-triggers. (The wording here avoids the literal wrapper tokens so the token-based gate stays
// clean, mirroring the ExploreView privacy-invariant comment.)
import SwiftUI
import EndoInsights

/// The two scatter axes a `.correlation` deep-link carries: an `(x, y)` pair of quantitative
/// `ExploreSeries`. A small `Equatable` wrapper (NOT a bare tuple) so `.onChange(of:)` compiles —
/// `ExploreSeries` is `Equatable` by id, so two `PendingScatter` compare equal iff both axes match.
/// Carries only catalog series ids (public, non-sensitive) — never health data.
struct PendingScatter: Equatable {
    /// The X axis the scatter prefills (a quantitative `ExploreSeries`).
    let x: ExploreSeries
    /// The Y axis the scatter prefills (a quantitative `ExploreSeries`).
    let y: ExploreSeries
}

/// The `@Observable` cross-tab scatter deep-link router (SCAT-04). The `.correlation` card affordance sets
/// `pendingScatter` on the main actor; the consented-branch `ExploreView` reads it to prefill the scatter.
@MainActor
@Observable
final class ExploreScatterRouter {
    /// When non-`nil`, the consented-branch `ExploreView` should prefill the scatter with this `(x, y)` pair
    /// and switch to scatter mode, then reset this to `nil`. Ephemeral — never persisted (T-16-11).
    var pendingScatter: PendingScatter? = nil

    init() {}
}

// MARK: - Environment injection (mirrors CheckInRouterKey / ConsentStoreKey)

/// Environment key carrying the app's `ExploreScatterRouter` (SCAT-04 injection seam).
private struct ExploreScatterRouterKey: EnvironmentKey {
    // The router is `@MainActor`-isolated and SwiftUI reads environment values on the main actor, so
    // constructing the default under `assumeIsolated` is safe and satisfies the nonisolated
    // `EnvironmentKey.defaultValue` requirement under Swift 6 (same pattern as the other service keys).
    static var defaultValue: ExploreScatterRouter {
        MainActor.assumeIsolated { ExploreScatterRouter() }
    }
}

extension EnvironmentValues {
    /// The injected cross-tab scatter deep-link router the consented-branch `ExploreView` prefills from
    /// (SCAT-04). Observed ONLY inside `AppGate`'s `.consented` branch (consent-by-construction).
    var exploreScatterRouter: ExploreScatterRouter {
        get { self[ExploreScatterRouterKey.self] }
        set { self[ExploreScatterRouterKey.self] = newValue }
    }
}
