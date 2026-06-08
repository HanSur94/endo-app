// Requirement: ONBD-03 — inclusive, non-assumptive language with optional terminology customization.
// State: GREEN (Plan 03). `Terminology` + `TerminologyPreference` now exist.
//
// These tests prove: neutral defaults ("bleeding"/"cycle"), bleeding-term customization, and — the
// enforceable copy-audit choke-point — that NO default resolved string contains a gendered token
// from `ClaimsPolicy.forbiddenGenderedTokens`.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-03 neutral terminology")
struct TerminologyTests {

    // (a) Default Terminology returns neutral "bleeding"/"cycle".
    @Test func defaults_areNeutral() async throws {
        let t = Terminology(preference: .init())
        #expect(t.bleedingNoun == "bleeding")
        #expect(t.cycleNoun == "cycle")
    }

    // (b) Setting bleedingTerm = .period changes the resolved copy.
    @Test func customizingBleedingTerm_changesResolvedCopy() async throws {
        var pref = TerminologyPreference()
        pref.bleedingTerm = .period
        let t = Terminology(preference: pref)
        #expect(t.bleedingNoun == "period")
        // The cycle noun stays neutral regardless of the bleeding term.
        #expect(t.cycleNoun == "cycle")
    }

    // (c) No default string equals a gendered token ("she"/"women"/"lady") — copy-audit choke-point.
    @Test func defaults_containNoGenderedTokens() async throws {
        let t = Terminology(preference: .init())
        // Audit every default string this lookup can produce against the forbidden set.
        for string in t.auditableDefaultStrings {
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(
                    !string.localizedCaseInsensitiveContains(token),
                    "Default terminology string \"\(string)\" must not contain gendered token \"\(token)\""
                )
            }
        }
    }
}
