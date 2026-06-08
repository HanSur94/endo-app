// Requirement: ONBD-01 — explicit, withdrawable GDPR consent gate before any health-data processing.
// State: GREEN (Plan 02). ConsentStore + ConsentState now exist; these assertions exercise the
//        real UserDefaults-backed consent state machine against a clean, isolated test suite so
//        nothing leaks into the standard defaults.
import Testing
import Foundation
@testable import EndoApp

// `@MainActor`: the consent store is main-actor-isolated (it drives SwiftUI routing), so the
// suite runs on the main actor to call grant/withdraw and read `state`.
@Suite("ONBD-01 consent gate")
@MainActor
struct ConsentGateTests {

    /// A fresh consent store over an isolated, empty UserDefaults suite (no cross-test bleed).
    private func makeTestConsentStore() -> UserDefaultsConsentStore {
        let suiteName = "ConsentGateTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        return UserDefaultsConsentStore(defaults: defaults)
    }

    // (a) A fresh ConsentStore reports notConsented.
    @Test func freshStore_reportsNotConsented() async throws {
        let store = makeTestConsentStore()
        #expect(store.state == .notConsented)
    }

    // (b) After grant(at:) the store reports consented.
    @Test func afterGrant_reportsConsented() async throws {
        let store = makeTestConsentStore()
        let when = Date(timeIntervalSince1970: 1_700_000_000)
        store.grant(at: when)
        #expect(store.state == .consented(when))
    }

    // (c) After withdraw() the store returns to notConsented (D-04 withdrawable in Settings).
    @Test func afterWithdraw_returnsToNotConsented() async throws {
        let store = makeTestConsentStore()
        store.grant(at: .now)
        #expect(store.state != .notConsented)   // sanity: consent took effect
        store.withdraw()
        #expect(store.state == .notConsented)
    }

    // (d) Durability: a grant persists across store re-instantiation over the same suite — the gate
    //     flag lives in UserDefaults, not in memory (RESEARCH Pattern 3).
    @Test func grant_persistsAcrossStoreReinstantiation() async throws {
        let suiteName = "ConsentGateTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        let when = Date(timeIntervalSince1970: 1_700_000_000)

        let first = UserDefaultsConsentStore(defaults: defaults)
        first.grant(at: when)

        let reloaded = UserDefaultsConsentStore(defaults: defaults)
        #expect(reloaded.state == .consented(when))

        defaults.removePersistentDomain(forName: suiteName)
    }
}
