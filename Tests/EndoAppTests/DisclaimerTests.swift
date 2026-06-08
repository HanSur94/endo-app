// Requirement: ONBD-02 — "not medical advice / not a medical device" framing (single source of truth).
// State: RED (Wave 0). Turned GREEN by Plan 02 when the Disclaimer constant exists.
//
// These tests intentionally FAIL now: the Disclaimer content constant does not exist yet.
// The real assertion is kept compiling behind a `#expect(Bool(false), ...)` RED placeholder;
// Plan 02 introduces the Disclaimer constant surfaced on the consent screen (and reused on
// insight surfaces later, D-05).
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-02 disclaimer presence")
struct DisclaimerTests {

    // (a) The Disclaimer constant is non-empty and contains "not medical advice" + "not a medical device".
    @Test func disclaimer_containsRequiredFraming() async throws {
        // RED until Plan 02:
        //   #expect(!Disclaimer.text.isEmpty)
        //   #expect(Disclaimer.text.localizedCaseInsensitiveContains("not medical advice"))
        //   #expect(Disclaimer.text.localizedCaseInsensitiveContains("not a medical device"))
        #expect(Bool(false), "RED — implement Disclaimer constant in Plan 02")
    }
}
