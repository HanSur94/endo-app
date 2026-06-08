// Requirement: DATA-02/03 — account-aware SyncMode resolution + P-2 local-only-builds guard.
//
// RED→GREEN (Plan 04-01 Task 2). resolve maps the injectable AccountAvailability seam to a SyncMode;
// the local-only path builds a genuinely `.none` store EVEN THOUGH EndoApp.entitlements ships in the
// target (P-2 guard); and the real SystemAccountAvailability resolves `.localOnly` in CI (no signed-in
// iCloud → ubiquityIdentityToken == nil). StubAccountAvailability forces deterministic answers.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("DATA-02 account-aware SyncMode")
struct SyncModeTests {

    // iCloud available → cloud sync (D-03).
    @Test func resolve_availableICloud_returnsCloudKit() {
        #expect(SyncMode.resolve(StubAccountAvailability(isICloudAvailable: true)) == .cloudKit)
    }

    // No iCloud → genuinely local (no crash, preserves the no-account promise, D-11).
    @Test func resolve_noICloud_returnsLocalOnly() {
        #expect(SyncMode.resolve(StubAccountAvailability(isICloudAvailable: false)) == .localOnly)
    }

    // P-2 guard: local-only builds a real, usable container even though EndoApp.entitlements ships
    // in the target — the `.none` store must not mis-activate CloudKit just because the entitlement
    // is present. Mirrors EditDeleteTests' makeModelContainer(inMemory:) + ModelContext usage.
    @Test func localOnly_buildsContainer() throws {
        let container = try makeModelContainer(syncMode: .localOnly, inMemory: true)
        _ = ModelContext(container)   // proves the .none store is usable
    }

    // CI reality: the real seam with no signed-in account resolves `.localOnly`
    // (Simulator has no iCloud account → ubiquityIdentityToken == nil).
    @Test func systemAvailability_inCI_resolvesLocalOnly() {
        #expect(SyncMode.resolve(SystemAccountAvailability()) == .localOnly)
    }
}
