// Requirement: D-01/D-03/D-04/D-10/D-13/INST-02 — the integration brain.
//
// The `InsightsCoordinator` is an `@Observable @MainActor` class that reads snapshots + dismissed on
// the main actor, runs the PURE `InsightsEngine` OFF the main thread, publishes `[Insight]` back on the
// main actor, loads the cache instantly on construction, debounces rapid edits into ONE recompute, and
// skips recompute on an unchanged fingerprint (cache hit) / recomputes + persists on a changed one
// (cache miss). It is built with INJECTABLE seams so these unit tests need no store/clock — a
// call-counting engine seam, a driveable sleep seam, and explicit load/persist closures.
import Testing
import Foundation
import EndoInsights
@testable import EndoApp

@Suite("D-01/D-04 InsightsCoordinator — debounce, cache, off-main")
@MainActor
struct InsightsCoordinatorTests {

    // MARK: - Fixtures

    nonisolated static let epoch = Date(timeIntervalSince1970: 1_700_000_000)
    nonisolated private static func day(_ offset: Int) -> Date { epoch.addingTimeInterval(Double(offset) * 86_400) }

    nonisolated private static func snapshot(on day: Date) -> DaySnapshot {
        DaySnapshot(
            day: day, symptoms: [], pain: [], flow: nil, clots: false, foodTags: [],
            activityType: nil, activityMinutes: nil, activityIntensity: nil,
            sleepHours: nil, sleepQuality: nil, medicationsTaken: [], tookPRN: false,
            stress: nil, mood: nil, hasNote: false, suppressed: false)
    }

    nonisolated private static func insight(_ signalKey: String) -> Insight {
        Insight.derived(
            kind: .trend, window: .last30, signalKey: signalKey,
            headline: "H", detail: "D", confidence: .medium,
            points: [InsightPoint(date: epoch, value: 5)], sampleSize: 3)
    }

    /// A mutable test harness capturing engine-call observations, with a manually-releasable sleep seam.
    final class Harness: @unchecked Sendable {
        var snapshots: [DaySnapshot]
        var dismissed: Set<String>
        var cache: [Insight]
        // Engine observations:
        var engineCallCount = 0
        var lastDismissedSeen: Set<String> = []
        var engineRanOffMain = true     // set false if the engine seam ever runs ON the main thread
        var engineResult: [Insight]
        // Persist observations:
        var persistCallCount = 0
        var lastPersistedFingerprint: String?
        // Dismiss-write observations:
        var dismissedWrites: [String] = []
        // Sleep seam control: the coordinator awaits `sleep()`; the test releases pending sleeps.
        private var sleepContinuations: [CheckedContinuation<Void, Never>] = []
        let sleepLock = NSLock()

        init(snapshots: [DaySnapshot], dismissed: Set<String> = [], cache: [Insight] = [],
             engineResult: [Insight] = []) {
            self.snapshots = snapshots
            self.dismissed = dismissed
            self.cache = cache
            self.engineResult = engineResult
        }

        /// Release every pending sleep so the debounce proceeds (deterministic, no real time).
        func releaseSleeps() {
            sleepLock.lock()
            let pending = sleepContinuations
            sleepContinuations = []
            sleepLock.unlock()
            for c in pending { c.resume() }
        }

        /// Synchronous readiness check (no lock held across an `await`) — `true` once a debounce task has
        /// parked in `sleepSeam()`.
        func hasPendingSleep() -> Bool {
            sleepLock.lock()
            defer { sleepLock.unlock() }
            return !sleepContinuations.isEmpty
        }

        /// Wait until a debounce task has actually parked in `sleepSeam()` (its continuation is registered),
        /// THEN release — avoiding the race where `releaseSleeps()` fires before the task reaches the seam
        /// (which would leave the later-registered continuation parked forever and hang the drain).
        func releaseSleepsWhenReady() async {
            while !hasPendingSleep() { await Task.yield() }
            releaseSleeps()
        }

        func sleepSeam() async {
            await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                sleepLock.lock()
                sleepContinuations.append(c)
                sleepLock.unlock()
            }
        }

        @MainActor func makeCoordinator() -> InsightsCoordinator {
            InsightsCoordinator(
                loadSnapshots: { self.snapshots },
                loadDismissed: { self.dismissed },
                persist: { _, _, fp in
                    self.persistCallCount += 1
                    self.lastPersistedFingerprint = fp
                },
                loadCache: { self.cache },
                now: { InsightsCoordinatorTests.epoch },
                persistDismissal: { key in
                    // Mirror the real repo's append-only dismiss: record the write AND make the next
                    // `loadDismissed()` observe it (the same-context visibility the seam guarantees).
                    self.dismissedWrites.append(key)
                    self.dismissed.insert(key)
                },
                engine: { snaps, _, dismissed in
                    self.engineCallCount += 1
                    self.lastDismissedSeen = dismissed
                    if Thread.isMainThread { self.engineRanOffMain = false }
                    return self.engineResult
                },
                sleep: { await self.sleepSeam() })
        }
    }

    // MARK: - Instant cache load (D-04)

    @Test func loadCached_populatesInsightsInstantly_beforeAnyRecompute() {
        let a = Self.insight("a"); let b = Self.insight("b")
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))], cache: [a, b])
        let coordinator = h.makeCoordinator()

        coordinator.loadCached()

        #expect(coordinator.insights == [a, b], "loadCached() must populate insights instantly from the cache.")
        #expect(h.engineCallCount == 0, "loadCached() must NOT run the engine (instant cache, no recompute).")
    }

    // MARK: - Cache miss: a changed fingerprint runs the engine and updates insights (D-04).

    @Test func recompute_cacheMiss_runsEngineAndPublishesInsights() async {
        let fresh = Self.insight("fresh")
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))], engineResult: [fresh])
        let coordinator = h.makeCoordinator()

        await coordinator.recompute()

        #expect(h.engineCallCount == 1, "A first (changed) fingerprint must run the engine (cache miss).")
        #expect(coordinator.insights == [fresh], "Recompute must publish the engine's [Insight] on the main actor.")
        #expect(h.persistCallCount == 1, "A cache miss must persist the fresh insights.")
    }

    // MARK: - Cache hit: an unchanged fingerprint skips the engine (D-04).

    @Test func recompute_cacheHit_skipsEngine() async {
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))], engineResult: [Self.insight("x")])
        let coordinator = h.makeCoordinator()

        await coordinator.recompute()           // miss → runs engine, stamps fingerprint
        let countAfterFirst = h.engineCallCount
        await coordinator.recompute()           // same inputs → fingerprint unchanged → skip

        #expect(countAfterFirst == 1, "The first recompute runs the engine (miss).")
        #expect(h.engineCallCount == 1, "An unchanged fingerprint must SKIP the engine (cache hit, no second call).")
    }

    // MARK: - Off-main + publish-on-main (D-03/D-13).

    @Test func recompute_runsEngineOffMain_andPublishesOnMain() async {
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))], engineResult: [Self.insight("y")])
        let coordinator = h.makeCoordinator()

        await coordinator.recompute()

        #expect(h.engineRanOffMain, "The engine must run OFF the main thread (Task.detached) — never on @MainActor.")
        // `insights` is assigned back on the @MainActor; reading it here (on @MainActor) is correct.
        #expect(coordinator.insights == [Self.insight("y")], "insights must be published on the main actor.")
    }

    // MARK: - Dismiss feed-through (D-10).

    @Test func recompute_feedsDismissedSignalKeyIntoEngine() async {
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))],
                        dismissed: ["symptom.pelvicPain.severity"],
                        engineResult: [])
        let coordinator = h.makeCoordinator()

        await coordinator.recompute()

        #expect(h.lastDismissedSeen.contains("symptom.pelvicPain.severity"),
                "A dismissed signalKey must feed through into the engine's `dismissed` set on recompute (D-10).")
    }

    // MARK: - Dismiss: persist-through-own-context + optimistic removal (D-09/D-10).

    @Test func dismiss_persistsThroughOwnSeam_andOptimisticallyRemovesCard() async {
        let kept = Self.insight("kept")
        let toDismiss = Self.insight("symptom.pelvicPain.frequency")
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))],
                        cache: [kept, toDismiss], engineResult: [kept])
        let coordinator = h.makeCoordinator()
        // Seed the published insights as if a prior recompute had rendered both cards (via the cache seam).
        coordinator.loadCached()
        #expect(coordinator.insights == [kept, toDismiss], "Precondition: both cards are rendered.")

        coordinator.dismiss(signalKey: "symptom.pelvicPain.frequency")

        // The write went through the coordinator's OWN seam (so the next recompute's read observes it),
        // and the card was optimistically removed for an instant UI response (D-09).
        #expect(h.dismissedWrites == ["symptom.pelvicPain.frequency"],
                "dismiss() must persist the dismissal through the coordinator's own context seam (D-09).")
        #expect(coordinator.insights == [kept],
                "dismiss() must optimistically remove the dismissed card for an instant UI response.")

        // Drive the debounced recompute the dismiss scheduled (so no continuation leaks), then verify it
        // fed the dismissed key into the engine and kept the card excluded (D-10). Wait for the debounce
        // task to PARK in the sleep seam before releasing, so the drain can never hang on a race.
        await h.releaseSleepsWhenReady()
        await coordinator.awaitPendingForTests()
        #expect(h.lastDismissedSeen.contains("symptom.pelvicPain.frequency"),
                "The recompute after a dismiss must feed the dismissed key into the engine (D-10).")
        #expect(coordinator.insights == [kept],
                "After the recompute the dismissed card stays excluded (D-09/D-10).")
    }

    // MARK: - Debounce coalesces N→1 (D-01).

    @Test func dataChanged_coalescesRapidCallsIntoOneRecompute() async {
        let h = Harness(snapshots: [Self.snapshot(on: Self.day(0))], engineResult: [Self.insight("z")])
        let coordinator = h.makeCoordinator()

        // Fire many rapid edits; each cancels the prior pending debounce task.
        for _ in 0..<10 { coordinator.dataChanged() }

        // Let the scheduled debounce tasks reach their `await sleep()` seam, then release once.
        await Task.yield()
        h.releaseSleeps()

        // Drain: wait for the surviving recompute to finish.
        await coordinator.awaitPendingForTests()

        #expect(h.engineCallCount == 1,
                "10 rapid dataChanged() calls must coalesce into exactly ONE recompute (debounce, D-01).")
    }
}
