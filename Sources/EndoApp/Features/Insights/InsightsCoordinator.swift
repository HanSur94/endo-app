// Requirement: D-01/D-03/D-04/D-10/D-13/INST-02 — the insights integration brain.
//
// `InsightsCoordinator` is the `@Observable @MainActor` object the dashboard (08-03/08-04) observes:
// it owns `insights` + `isUpdating` and exposes `loadCached()` / `dataChanged()` / `recompute()`.
//
// THE MAKE-OR-BREAK (SC2 — no UI jank): the coordinator reads the `Sendable` snapshots + dismissed set
// on the MAIN actor, then runs the PURE `InsightsEngine.insights(from:asOf:dismissed:)` in a
// `Task.detached(priority: .userInitiated)` — the engine call is the ONLY thing off-main (Pitfall 4:
// NEVER run the engine on `@MainActor`, NEVER use `@ModelActor`). The resulting `[Insight]` is published
// back on the `@MainActor`. Rapid edits are coalesced by a cancellable debounce (`Task.sleep` ~400ms,
// D-01). An input FINGERPRINT (count + latest-edit + dismissed-canonical) drives cache HIT (unchanged →
// skip recompute) / MISS (recompute + `replaceCachedInsights`), so a cold open loads the cache instantly
// (D-04) and only recomputes when the inputs actually changed.
//
// INTERFACE-FIRST (RESEARCH Wave 0 seam): every dependency is an INJECTED closure so unit tests need no
// store, no clock, and no real time — a call-counting `engine`, a driveable `sleep`, explicit
// load/persist closures. `AppDependencies` wires the real `SwiftDataRepository` seams + the default
// engine + the real 400ms sleep.
//
// PRIVACY (T-08-05): the coordinator NEVER logs snapshots, dismissed keys, or insight copy — only
// `Sendable` values cross to `Task.detached`; the engine is Foundation-only and pure (no logging).
import SwiftUI
import EndoInsights

/// The `@Observable @MainActor` insights coordinator (D-13). Mirrors `CheckInRouter`'s class + env-key
/// shape; built once in `AppDependencies` and injected via `\.insightsCoordinator`.
@MainActor
@Observable
final class InsightsCoordinator {

    /// The currently-published gated insights (cache on open, then recompute output). Published on the
    /// main actor only (D-13).
    private(set) var insights: [Insight] = []
    /// `true` while an off-main recompute is in flight (the dashboard shows a subtle updating affordance).
    private(set) var isUpdating = false

    // MARK: - Injected seams (real impls supplied by AppDependencies; tests inject fakes)

    /// Read the per-day snapshots on the MAIN actor (repo.daySnapshots()).
    private let loadSnapshots: () throws -> [DaySnapshot]
    /// Read the dismissed signalKeys on the MAIN actor (repo.dismissedSignalKeys()).
    private let loadDismissed: () throws -> Set<String>
    /// Persist the fresh insights (repo.replaceCachedInsights(_:computedAt:fingerprint:)).
    private let persist: ([Insight], Date, String) -> Void
    /// Load the cached insights instantly on open (repo.loadCachedInsights()).
    private let loadCache: () -> [Insight]
    /// The injected clock (the engine's `asOf` reference + the cache `computedAt`).
    private let now: () -> Date
    /// The PURE engine seam (snapshots, asOf, dismissed) → [Insight]. Defaults to the real
    /// `InsightsEngine`. Captured by the `Task.detached` closure (all inputs are `Sendable` value types).
    private let engine: @Sendable ([DaySnapshot], Date, Set<String>) -> [Insight]
    /// The debounce sleep seam (defaults to a real ~400ms `Task.sleep`). Injected so tests drive it.
    private let sleep: @Sendable () async -> Void

    // MARK: - Private state

    /// The fingerprint of the inputs the last recompute ran on. An unchanged fingerprint ⇒ cache hit.
    private var lastFingerprint: String?
    /// The cancellable debounce task; a new `dataChanged()` cancels and reschedules it (coalesce, D-01).
    private var debounceTask: Task<Void, Never>?

    /// - Parameters mirror the seams above. The `engine`/`sleep` defaults are the real impls so production
    ///   construction only supplies the store/clock closures.
    init(
        loadSnapshots: @escaping () throws -> [DaySnapshot],
        loadDismissed: @escaping () throws -> Set<String>,
        persist: @escaping ([Insight], Date, String) -> Void,
        loadCache: @escaping () -> [Insight],
        now: @escaping () -> Date,
        engine: @escaping @Sendable ([DaySnapshot], Date, Set<String>) -> [Insight] = { snaps, asOf, dismissed in
            InsightsEngine.insights(from: snaps, asOf: asOf, dismissed: dismissed)
        },
        sleep: @escaping @Sendable () async -> Void = {
            try? await Task.sleep(for: .milliseconds(400))
        }
    ) {
        self.loadSnapshots = loadSnapshots
        self.loadDismissed = loadDismissed
        self.persist = persist
        self.loadCache = loadCache
        self.now = now
        self.engine = engine
        self.sleep = sleep
    }

    // MARK: - API the dashboard drives

    /// Load the cached insights INSTANTLY (D-04) — no engine run, no off-main hop. The dashboard calls
    /// this on appear so the screen paints immediately, then drives an initial `dataChanged()`.
    func loadCached() {
        insights = loadCache()
    }

    /// Signal that the underlying data changed (a logging edit, a dismiss, or a foreground). Coalesces
    /// rapid calls into ONE recompute after a quiet period (D-01): cancel-and-reschedule the debounce.
    func dataChanged() {
        debounceTask?.cancel()
        debounceTask = Task { @MainActor [weak self] in
            await self?.sleep()
            guard !Task.isCancelled else { return }
            await self?.recompute()
        }
    }

    /// Recompute the insights: read inputs on the main actor, compute the fingerprint, SKIP on a cache hit,
    /// else run the PURE engine OFF the main thread and publish + persist on the main actor.
    func recompute() async {
        // MAIN-actor reads (the only place the store is touched).
        guard let snapshots = try? loadSnapshots(),
              let dismissed = try? loadDismissed() else { return }

        let fingerprint = InsightFingerprint.make(snapshots: snapshots, dismissed: dismissed)
        // Cache HIT: the inputs are identical to the last recompute → skip the engine entirely (D-04).
        guard fingerprint != lastFingerprint else { return }

        isUpdating = true
        let asOf = now()
        let engine = self.engine   // capture the Sendable seam (not self) for the detached task.

        // OFF-MAIN (SC2 / Pitfall 4): snapshots + dismissed are Sendable value types and cross into the
        // detached task with no friction; the engine is pure. This is the ONLY work off the main actor.
        let fresh = await Task.detached(priority: .userInitiated) {
            engine(snapshots, asOf, dismissed)
        }.value

        // Back on the @MainActor: publish + stamp the fingerprint + persist + clear the flag (D-13).
        lastFingerprint = fingerprint
        insights = fresh
        persist(fresh, asOf, fingerprint)
        isUpdating = false
    }

    /// TEST-ONLY: await the in-flight debounce task so a test can deterministically observe the single
    /// coalesced recompute without sleeping in real time.
    func awaitPendingForTests() async {
        await debounceTask?.value
    }
}

// MARK: - Environment injection (mirrors CheckInRouterKey)

/// Environment key carrying the app's `InsightsCoordinator` (D-13 injection seam).
private struct InsightsCoordinatorKey: EnvironmentKey {
    // The coordinator is `@MainActor`-isolated and SwiftUI reads environment values on the main actor, so
    // constructing the default under `assumeIsolated` is safe and satisfies the nonisolated
    // `EnvironmentKey.defaultValue` requirement under Swift 6 (same pattern as CheckInRouterKey). The
    // default is an inert coordinator (empty store seams) for previews/tests that don't inject one.
    static var defaultValue: InsightsCoordinator {
        MainActor.assumeIsolated {
            InsightsCoordinator(
                loadSnapshots: { [] },
                loadDismissed: { [] },
                persist: { _, _, _ in },
                loadCache: { [] },
                now: { Date() })
        }
    }
}

extension EnvironmentValues {
    /// The injected insights coordinator the dashboard observes (D-13).
    var insightsCoordinator: InsightsCoordinator {
        get { self[InsightsCoordinatorKey.self] }
        set { self[InsightsCoordinatorKey.self] = newValue }
    }
}
