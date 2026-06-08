// Requirement: ONBD-02 — "not medical advice / not a medical device" framing (single source of truth).
// State: GREEN (Plan 03). The `Disclaimer.text` constant now exists.
//
// These assertions prove the auditable claim lives in ONE place and contains both required phrases.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-02 disclaimer presence")
struct DisclaimerTests {

    // (a) The Disclaimer constant is non-empty and contains "not medical advice" + "not a medical device".
    @Test func disclaimer_containsRequiredFraming() async throws {
        #expect(!Disclaimer.text.isEmpty)
        #expect(Disclaimer.text.localizedCaseInsensitiveContains("not medical advice"))
        #expect(Disclaimer.text.localizedCaseInsensitiveContains("not a medical device"))
    }
}
