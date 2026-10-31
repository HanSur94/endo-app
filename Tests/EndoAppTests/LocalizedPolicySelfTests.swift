// EndoAppTests — shared-fixture model for the DE/FR/ES claims policies (SAFE-01/SAFE-02).
//
// Mirror counterpart: `android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySelfTests.kt`.
// The 26-04 tracer runs the same German fixtures the Kotlin suite runs, so a port bug in L10nText.swift
// or LanguageClaimsPolicy.swift shows up here instead of in a store review.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-01/02 localized claims policy self-tests")
struct LocalizedPolicySelfTests {

    /// The tracer slice end to end: the bundled German policy reports the full Violation, kind
    /// included, and an allow token masks a whole host word in any case.
    @Test func germanTracerVerdict() {
        let de = ClaimsPolicy.forLanguage(.de)
        #expect(de.language == "de")
        #expect(de.policyRevision == 1)
        #expect(de.violation("Die App erkennt Schübe frühzeitig.")
                == Violation(category: "diagnostic", kind: "stem", term: "erkenn"))
        #expect(de.violation("Behandlungsteam") == nil)
        #expect(de.violation("BEHANDLUNGSTEAM") == nil)
        #expect(de.tokens("Schub-Frühwarnung").map(\.folded) == ["schub", "frühwarnung"])
        #expect(de.fold("STRAẞE") == "strasse")
    }
}
