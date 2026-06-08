// Requirement: ONBD-01 — explicit, withdrawable GDPR consent gate before any health-data processing.
// State: RED (Wave 0). Turned GREEN by Plan 02 when ConsentStore + ConsentState exist.
//
// These tests intentionally FAIL now: the ConsentStore protocol and its UserDefaults-backed
// implementation do not exist yet. Each real assertion is kept compiling behind a
// `#expect(Bool(false), ...)` RED placeholder; Plan 02 wires the real consent state machine.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-01 consent gate")
struct ConsentGateTests {

    // (a) A fresh ConsentStore reports notConsented.
    @Test func freshStore_reportsNotConsented() async throws {
        // RED until Plan 02:
        //   let store = makeTestConsentStore()
        //   #expect(store.state == .notConsented)
        #expect(Bool(false), "RED — implement fresh-store notConsented in Plan 02")
    }

    // (b) After grant(at:) the store reports consented.
    @Test func afterGrant_reportsConsented() async throws {
        // RED until Plan 02: store.grant(at: .now); #expect(store.state == .consented(...))
        #expect(Bool(false), "RED — implement grant -> consented in Plan 02")
    }

    // (c) After withdraw() the store returns to notConsented (D-04 withdrawable in Settings).
    @Test func afterWithdraw_returnsToNotConsented() async throws {
        // RED until Plan 02: store.grant(at: .now); store.withdraw(); #expect(store.state == .notConsented)
        #expect(Bool(false), "RED — implement withdraw -> notConsented in Plan 02")
    }
}
