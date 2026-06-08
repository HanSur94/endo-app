// Requirement: INST-04 (D-09/D-10) — the dismissal CRUD round-trip + append-only/dedupe-on-read proof.
//
// Proves Repository.dismissInsight(signalKey:) durably persists a dismissal and dismissedSignalKeys()
// reads it back as a deduped Set<String>. The store is an in-memory SchemaV4 container (fast,
// deterministic, no Simulator/iCloud) — the round-trip seam the migration test does not exercise.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("INST-04 dismissal CRUD round-trip")
struct RepositoryDismissalTests {

    /// Build an in-memory SchemaV4 container + a consent-defaulted repository (consentGuard defaults true).
    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    @Test func dismissThenRead_returnsTheKey() async throws {
        let repo = try makeRepo()
        let key = "cyclePhase.luteal.symptom.pelvicPain"

        try repo.dismissInsight(signalKey: key)

        #expect(try repo.dismissedSignalKeys().contains(key))
    }

    @Test func dismissingSameKeyTwice_collapsesToOneOnRead() async throws {
        let repo = try makeRepo()
        let key = "cyclePhase.luteal.symptom.pelvicPain"

        // Append-only writes (two rows); the read-side Set must collapse them to ONE key (D-10/D-12).
        try repo.dismissInsight(signalKey: key)
        try repo.dismissInsight(signalKey: key)

        let keys = try repo.dismissedSignalKeys()
        #expect(keys.filter { $0 == key }.count == 1)
        #expect(keys.contains(key))
    }

    @Test func distinctKeys_bothPresentInTheSet() async throws {
        let repo = try makeRepo()
        let a = "cyclePhase.luteal.symptom.pelvicPain"
        let b = "cyclePhase.follicular.symptom.fatigue"

        try repo.dismissInsight(signalKey: a)
        try repo.dismissInsight(signalKey: b)

        let keys = try repo.dismissedSignalKeys()
        #expect(keys.contains(a))
        #expect(keys.contains(b))
        #expect(keys.count == 2)
    }
}
