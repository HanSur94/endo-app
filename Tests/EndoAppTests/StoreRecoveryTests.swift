// Requirement: D-22 resilience — store-recovery safety once CloudKit private sync is live (WR-02).
//
// `AppDependencies.discardUnreadableStore` performs the DESTRUCTIVE on-disk store wipe used when a
// ModelContainer fails to open. With Phase 4 CloudKit sync active, the failing store may be mid-sync,
// so the wipe MUST be gated to `.localOnly` (no synced copy at risk). These tests pin that gate:
// `.cloudKit` and in-memory return `false` (skip wipe → caller falls back to in-memory and surfaces
// it), while `.localOnly` returns `true` (a clean on-disk store may be re-established).
import Testing
import Foundation
@testable import EndoApp

@MainActor
@Suite("WR-02 store-recovery safety")
struct StoreRecoveryTests {

    // Local-only: no synced copy, so a destructive wipe + on-disk retry is safe.
    @Test func discard_localOnly_clearsStore() {
        #expect(AppDependencies.discardUnreadableStore(syncMode: .localOnly, inMemory: false) == true)
    }

    // CloudKit: the store may be mid-sync — never silently wipe; signal the caller to fall back.
    @Test func discard_cloudKit_skipsWipe() {
        #expect(AppDependencies.discardUnreadableStore(syncMode: .cloudKit, inMemory: false) == false)
    }

    // In-memory (UI-test) store: nothing on disk to clear.
    @Test func discard_inMemory_skipsWipe() {
        #expect(AppDependencies.discardUnreadableStore(syncMode: .localOnly, inMemory: true) == false)
    }
}
