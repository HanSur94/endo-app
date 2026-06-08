// Requirement: cross-cutting claims/language audit (Pitfall 5, D-14) — new Phase-2 copy must pass
// ClaimsPolicy.forbiddenVerbs + forbiddenGenderedTokens and use no hard single date.
//
// State: PARTIALLY GREEN. The symptom/region/flow LABEL half is implemented in Plan 02-01 Task 2
// (the three Domain enums' plainLabels). The cycle-copy half is still RED until Plan 02-04 Task 2.
import Testing
import Foundation
@testable import EndoApp

@Suite("Phase-2 copy audit")
struct CopyAuditTests {

    /// Every plain label across the three Domain enums, lowercased for substring auditing.
    private var allDomainLabels: [String] {
        SymptomType.allCases.map(\.plainLabel)
            + BodyRegion.allCases.map(\.plainLabel)
            + FlowIntensity.allCases.map(\.plainLabel)
    }

    // GREEN (Plan 02-01 Task 2): no Domain plainLabel contains a forbidden medical-purpose verb.
    @Test func domainLabels_containNoForbiddenVerb() {
        for label in allDomainLabels {
            let lower = label.lowercased()
            for verb in ClaimsPolicy.forbiddenVerbs {
                #expect(!lower.contains(verb.lowercased()),
                        "Domain label \"\(label)\" must not contain forbidden verb \"\(verb)\".")
            }
        }
    }

    // GREEN (Plan 02-01 Task 2): no Domain plainLabel contains a gendered token.
    @Test func domainLabels_containNoGenderedToken() {
        for label in allDomainLabels {
            let lower = label.lowercased()
            for token in ClaimsPolicy.forbiddenGenderedTokens {
                #expect(!lower.contains(token.lowercased()),
                        "Domain label \"\(label)\" must not contain gendered token \"\(token)\".")
            }
        }
    }

    // Sanity: the audited surface is non-empty (catches an accidentally-empty allCases).
    @Test func domainLabels_areNonEmpty() {
        #expect(!allDomainLabels.isEmpty)
        for label in allDomainLabels {
            #expect(!label.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Every Domain plainLabel must be a non-empty string.")
        }
    }
}
