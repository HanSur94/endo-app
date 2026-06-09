// Requirement: DATA-05 / D-09 (Phase 9) — the anchored-pull end-to-end behaviour, driven entirely
// through the `FixtureHealthDataReading` seam against a real in-memory SchemaV6 store. Proves the
// load-bearing Pitfall-5 contracts (dedupe-by-uuid across re-pull, honor HealthKit deletions) and the
// SC3 honesty contract (an empty/denied import writes NOTHING), plus the anchor round-trip + corruption
// safety (T-09-09). No real `HKHealthStore`, no Simulator HealthKit grant.
import Testing
import Foundation
import SwiftData
import HealthKit
@testable import EndoApp

@Suite("DATA-05 anchored pull: dedupe, deletions, empty-safe, anchor round-trip")
struct HealthAnchorPullTests {

    /// An in-memory SchemaV6 container + consent-defaulted repository (consentGuard defaults true).
    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    private func sleepValue(uuid: UUID, day: Date, hours: Double) -> ImportedSampleValue {
        ImportedSampleValue(hkUUID: uuid, day: day, domain: .sleep,
                            payload: .sleep(hours: hours, quality: .good))
    }

    /// Wraps the non-Sendable `SwiftDataRepository` so the `@Sendable` persist closure can reach it. The
    /// test single-threads every `await importNew(...)`, so there is no real concurrent access — this
    /// mirrors how `AppDependencies` will hop the persist onto the repo's isolation in 09-03.
    private final class RepoBox: @unchecked Sendable {
        let repo: SwiftDataRepository
        init(_ repo: SwiftDataRepository) { self.repo = repo }
    }

    /// Build a service wired to a real repo via the production persist seam (the CRUD entry).
    private func makeService(reader: FixtureHealthDataReading, repo: SwiftDataRepository) -> HealthImportService {
        let box = RepoBox(repo)
        return HealthImportService(
            reader: reader,
            anchorStore: HealthAnchorStore(kv: InMemoryAnchorStore()),
            persist: { values, deleted in
                try box.repo.replaceOrInsertImported(values: values, deletedUUIDs: deleted)
            },
            recompute: {})
    }

    // MARK: - Pitfall 5: dedupe across re-pull

    @Test func rePullingSameSamples_doesNotDuplicate() async throws {
        let repo = try makeRepo()
        let day = Date()
        let uuid = UUID()
        // A seed whose second pull replays the SAME sample (delta == .none) — the canonical re-pull case.
        let reader = FixtureHealthDataReading(.seed(
            values: [sleepValue(uuid: uuid, day: day, hours: 7)],
            deleted: [],
            delta: .none))
        let service = makeService(reader: reader, repo: repo)

        await service.importNew(for: .sleep)
        await service.importNew(for: .sleep)   // re-pull the same hkUUID

        let rows = try repo.importedSamples(on: day)
        #expect(rows.count == 1)                       // deduped — NOT two rows (Pitfall 5)
        #expect(rows.first?.hkSampleUUID == uuid.uuidString)
    }

    // MARK: - Pitfall 5: honor deletions

    @Test func deletionUUID_removesTheMatchingRow() async throws {
        let repo = try makeRepo()
        let day = Date()
        let uuid = UUID()
        // Pull #1 seeds one sample; pull #2's delta DELETES it.
        let reader = FixtureHealthDataReading(.seed(
            values: [sleepValue(uuid: uuid, day: day, hours: 7)],
            deleted: [],
            delta: .init(values: [], deleted: [uuid])))
        let service = makeService(reader: reader, repo: repo)

        await service.importNew(for: .sleep)
        #expect(try repo.importedSamples(on: day).count == 1)   // present after the seed

        await service.importNew(for: .sleep)                    // the delta deletes it
        #expect(try repo.importedSamples(on: day).isEmpty)      // deletion honored
    }

    // MARK: - SC3: empty/denied writes nothing

    @Test func emptyFixture_writesNothing() async throws {
        let repo = try makeRepo()
        let reader = FixtureHealthDataReading(.empty)
        let service = makeService(reader: reader, repo: repo)

        await service.importNew(for: .sleep)

        let all = try repo.importedSamples(on: Date())
        #expect(all.isEmpty)   // a denied/empty read contributes NOTHING (SC3 / Pitfall 1)
    }

    // MARK: - T-09-09: anchor round-trip + corruption safety

    @Test func anchor_roundTripsThroughArchiver() async throws {
        let store = HealthAnchorStore(kv: InMemoryAnchorStore())
        #expect(store.anchor(for: .sleep) == nil)   // nothing stored yet

        let anchor = HKQueryAnchor(fromValue: 42)
        store.setAnchor(anchor, for: .sleep)

        #expect(store.anchor(for: .sleep) == anchor)   // survives NSKeyedArchiver → unarchive
        #expect(store.anchor(for: .cycle) == nil)      // per-domain isolation: cycle untouched
    }

    @Test func corruptAnchorBlob_degradesToNilWithoutCrashing() async throws {
        let kv = InMemoryAnchorStore()
        // Write a garbage blob under the sleep anchor key (simulates a corrupt archive, T-09-09).
        kv.set(Data([0x00, 0x01, 0x02, 0x03]), forKey: "health.anchor.sleep")
        let store = HealthAnchorStore(kv: kv)

        #expect(store.anchor(for: .sleep) == nil)   // discard-and-re-backfill — never crashes
    }
}
