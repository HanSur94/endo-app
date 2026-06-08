// Requirement: ONBD-03 — inclusive, non-assumptive language with optional terminology customization.
// State: RED (Wave 0). Turned GREEN by Plan 02 when Terminology + TerminologyPreference exist.
//
// These tests intentionally FAIL now: the Terminology lookup and TerminologyPreference value type
// do not exist yet. Each real assertion is kept compiling behind a `#expect(Bool(false), ...)` RED
// placeholder; Plan 02 implements the neutral-default lookup (D-06/07/08) all copy resolves through.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-03 neutral terminology")
struct TerminologyTests {

    // (a) Default Terminology returns neutral "bleeding"/"cycle".
    @Test func defaults_areNeutral() async throws {
        // RED until Plan 02:
        //   let t = Terminology(preference: .init())
        //   #expect(t.bleedingNoun == "bleeding")
        //   #expect(t.cycleNoun == "cycle")
        #expect(Bool(false), "RED — implement neutral terminology defaults in Plan 02")
    }

    // (b) Setting bleedingTerm = .period changes the resolved copy.
    @Test func customizingBleedingTerm_changesResolvedCopy() async throws {
        // RED until Plan 02: var pref = TerminologyPreference(); pref.bleedingTerm = .period
        //   let t = Terminology(preference: pref); #expect(t.bleedingNoun == "period")
        #expect(Bool(false), "RED — implement bleeding-term customization in Plan 02")
    }

    // (c) No default string equals a gendered token ("she"/"women"/"lady") — copy-audit choke-point.
    @Test func defaults_containNoGenderedTokens() async throws {
        // RED until Plan 02: assert the resolved default strings contain none of "she"/"women"/"lady".
        #expect(Bool(false), "RED — implement gendered-token audit in Plan 02")
    }
}
