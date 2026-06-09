// Requirement: DATA-05 (Phase 9) — the deterministic `HealthDataReading` test double. This is the seam
// that makes the WHOLE import path testable on the Simulator with no real device, no HealthKit grant,
// and no real `HKHealthStore` (RESEARCH Pattern 2). 09-03 selects it under a `-uitest-health-*` launch
// arg for the E2E tests; 09-02 unit tests drive it directly.
//
// PRIVACY/ISOLATION BOUNDARY (threat T-09-06): this fixture NEVER constructs an `HKHealthStore` and
// NEVER runs a real query. It returns ONLY in-memory `ImportedSampleValue`s scripted by the test. It is
// the only `HealthDataReading` conformer that can run on a Simulator with no Health data, so it can
// never leak real health data into a store — it has none.
//
// READ-STATUS HONESTY (RESEARCH Pitfall 1, SC3): `.empty` returns NO values — modeling a denied OR a
// granted-but-empty read IDENTICALLY (the two are indistinguishable for reads). A `.empty` import must
// therefore leave every `DaySnapshot` field nil downstream — never fabricate a "no symptoms" reading.
//
// SWIFT 6: `HealthDataReading` is `Sendable`; this conformer holds its scripted state behind an actor so
// the two-pull script (pull #1 = seed, pull #2 = incremental delta) is observed deterministically across
// `await`s without data races. No `HK*` type is produced except the opaque synthetic anchor.
import Foundation
import HealthKit

/// A deterministic, in-memory `HealthDataReading` for tests (RESEARCH Pattern 2). Returns scripted
/// `ImportedSampleValue`s and NEVER touches `HKHealthStore` (T-09-06).
final class FixtureHealthDataReading: HealthDataReading {

    /// The fixture's pull script.
    enum Fixture: Sendable {
        /// Seed: pull #1 returns `values` + `deleted`; subsequent pulls return the (optional) incremental
        /// `delta`. This drives the dedupe-across-re-pull assertion (Pitfall 5) and the deletion case.
        case seed(values: [ImportedSampleValue], deleted: [UUID], delta: Delta)
        /// Empty/denied (SC3 / Pitfall 1): every pull returns NOTHING. Models a denied OR empty read.
        case empty
    }

    /// The incremental second-pull payload (what HealthKit reports as *new/changed* since the anchor).
    struct Delta: Sendable {
        let values: [ImportedSampleValue]
        let deleted: [UUID]
        /// A `.none` delta means "the second pull returns the SAME seed again" — the canonical
        /// re-pull-the-same-samples dedupe case (Pitfall 5). `.some` returns a genuine incremental set.
        static let none = Delta(values: [], deleted: [])
    }

    /// The scripted state lives behind an actor so the call count + script advance race-free under Swift 6.
    private actor State {
        let fixture: Fixture
        var pullCount = 0
        var authorizationCalls = 0
        init(_ fixture: Fixture) { self.fixture = fixture }

        func recordAuthorization() { authorizationCalls += 1 }

        /// Advance the pull script. First pull = seed; subsequent pulls = the incremental delta (or, for
        /// `.none`, the same seed again to exercise re-pull dedupe). `.empty` always returns nothing.
        func nextPull() -> (values: [ImportedSampleValue], deleted: [UUID]) {
            pullCount += 1
            switch fixture {
            case .empty:
                return ([], [])
            case let .seed(values, deleted, delta):
                if pullCount == 1 {
                    return (values, deleted)
                }
                // Re-pull: a `.none` delta replays the seed (same hkUUIDs → must dedupe); otherwise the
                // genuine incremental set.
                if delta.values.isEmpty && delta.deleted.isEmpty {
                    return (values, [])   // same samples re-delivered → dedupe (Pitfall 5)
                }
                return (delta.values, delta.deleted)
            }
        }

        func count() -> Int { pullCount }
        func authCount() -> Int { authorizationCalls }
    }

    private let state: State

    init(_ fixture: Fixture) {
        self.state = State(fixture)
    }

    /// No-op authorization that only RECORDS it was called (read status is hidden — Pitfall 1; the
    /// fixture never grants/denies, it just scripts the subsequent pull).
    func requestAuthorization(for domain: HealthDomain) async throws {
        await state.recordAuthorization()
    }

    /// Unioned no-op authorization (WR-05) that only RECORDS it was called — the fixture never grants or
    /// denies; read status is hidden (Pitfall 1).
    func requestAuthorization() async throws {
        await state.recordAuthorization()
    }

    /// Returns the scripted values + a synthetic non-nil anchor (the cursor the service persists). The
    /// anchor is a fresh opaque `HKQueryAnchor` — callers treat it as a black box.
    func pull(
        _ domain: HealthDomain,
        since anchor: HKQueryAnchor?
    ) async throws -> (values: [ImportedSampleValue], deleted: [UUID], newAnchor: HKQueryAnchor?) {
        let (values, deleted) = await state.nextPull()
        // A synthetic, non-nil anchor — exercises the persist-the-new-anchor path without a real query.
        let newAnchor = HKQueryAnchor(fromValue: await state.count())
        return (values, deleted, newAnchor)
    }

    // MARK: - Test introspection

    /// How many times `pull` has been invoked (for re-pull assertions).
    func pullCountForTests() async -> Int { await state.count() }
    /// How many times `requestAuthorization` was called.
    func authorizationCountForTests() async -> Int { await state.authCount() }
}
