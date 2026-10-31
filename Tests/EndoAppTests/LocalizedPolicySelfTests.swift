// EndoAppTests — the shared-fixture verdict tests for the DE/FR/ES claims policies (SAFE-01/SAFE-02/SAFE-04).
//
// Mirror counterpart: `android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySelfTests.kt`.
// Contract: every mustFail sentence yields exactly its expected {category, term} as the FIRST hit, every
// mustPass entry yields none, and the normalization/tokenize fixtures reproduce exactly. A red fixture is
// fixed in the l10n data on the Android branch, never by loosening these assertions.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-01/02 localized claims policy self-tests")
struct LocalizedPolicySelfTests {

    private func policy(_ language: PolicyLanguage) -> LanguageClaimsPolicy {
        ClaimsPolicy.forLanguage(language)
    }

    private func expectMustFail(_ language: PolicyLanguage, _ context: String = "") {
        let policy = policy(language)
        for fixture in policy.selfTest.mustFail {
            let actual = policy.violation(fixture.text)
            #expect(actual?.category == fixture.category && actual?.term == fixture.term,
                    """
                    [\(language.tag)\(context)] mustFail "\(fixture.text)": expected first hit \
                    {\(fixture.category), \(fixture.term)}, got \
                    \(actual.map { "{\($0.category), \($0.term)} (\($0.kind))" } ?? "no violation")
                    """)
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func mustFailYieldsExpectedFirstHit(_ language: PolicyLanguage) {
        expectMustFail(language)
    }

    @Test(arguments: PolicyLanguage.allCases)
    func mustPassHasNoViolation(_ language: PolicyLanguage) {
        let policy = policy(language)
        for text in policy.selfTest.mustPass {
            let hits = policy.violations(text)
            #expect(hits.isEmpty, "[\(language.tag)] mustPass \"\(text)\" must be clean, but hit \(hits)")
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func normalizationFixturesFold(_ language: PolicyLanguage) {
        let policy = policy(language)
        for fixture in policy.selfTest.normalization {
            let actual = policy.fold(fixture.input)
            #expect(actual == fixture.folded,
                    "[\(language.tag)] normalization of \"\(fixture.input)\" (\(codePoints(fixture.input))) gave \"\(actual)\"")
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func tokenizeFixturesMatch(_ language: PolicyLanguage) {
        let policy = policy(language)
        for fixture in policy.selfTest.tokenize {
            let tokens = policy.tokens(fixture.input)
            #expect(tokens.map(\.folded) == fixture.tokens, "[\(language.tag)] folded tokens of \"\(fixture.input)\"")
            #expect(tokens.map(\.sentenceInitial) == fixture.sentenceInitial,
                    "[\(language.tag)] sentenceInitial flags of \"\(fixture.input)\"")
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func fixturesAreNotVacuous(_ language: PolicyLanguage) {
        let selfTest = policy(language).selfTest
        #expect(selfTest.mustFail.count >= 8, "[\(language.tag)] needs at least 8 mustFail fixtures, has \(selfTest.mustFail.count)")
        #expect(!selfTest.mustPass.isEmpty, "[\(language.tag)] needs at least one mustPass fixture")
        #expect(!selfTest.normalization.isEmpty, "[\(language.tag)] needs at least one normalization fixture")
        #expect(!selfTest.tokenize.isEmpty, "[\(language.tag)] needs at least one tokenize fixture")
    }

    @Test(arguments: PolicyLanguage.allCases)
    func forLanguageIsStable(_ language: PolicyLanguage) {
        #expect(policy(language) == policy(language), "[\(language.tag)] forLanguage must return the cached instance")
    }

    /// Swift counterpart of the Kotlin `verdictsIgnoreDefaultLocale`: lowercasing must be the Unicode
    /// default, never a Turkish/locale-aware mapping (DIAGNOSE → dıagnose would miss the ASCII rules).
    @Test func foldIgnoresLocale() {
        #expect(L10nText.foldString("I", replace: []) == "i")
        #expect(L10nText.foldString("İ", replace: []) == "i\u{0307}")
    }

    /// The tracer slice end to end: the bundled German policy reports the full Violation, kind
    /// included, and an allow token masks a whole host word in any case.
    @Test func germanTracerVerdict() {
        let de = policy(.de)
        #expect(de.language == "de")
        #expect(de.policyRevision == 1)
        #expect(de.violation("Die App erkennt Schübe frühzeitig.")
                == Violation(category: "diagnostic", kind: "stem", term: "erkenn"))
        #expect(de.violation("Behandlungsteam") == nil)
        #expect(de.violation("BEHANDLUNGSTEAM") == nil)
        #expect(de.tokens("Schub-Frühwarnung").map(\.folded) == ["schub", "frühwarnung"])
        #expect(de.fold("STRAẞE") == "strasse")
    }

    /// French: an elided causal verb is caught behind U+2019, the host word entraînement passes in every
    /// spelling and case, and a decomposed (NFD) accent cannot dodge the diagnostic stem.
    @Test func frenchVerdicts() {
        let fr = policy(.fr)
        #expect(fr.violation("Le café l\u{2019}entraîne.")
                == Violation(category: "causal", kind: "prefix", term: "entraîn"))
        #expect(fr.violation("entraînement") == nil)
        #expect(fr.violation("entrainement") == nil)
        #expect(fr.violation("ENTRAÎNEMENT") == nil)

        let nfd = "L'application de\u{0301}tecte vos pousse\u{0301}es."
        // Scalar comparison, not String equality: Swift's == is canonical-equivalence based and would
        // call the NFD fixture equal to its NFC form (the JVM compares code units).
        #expect(!nfd.unicodeScalars.elementsEqual(nfd.precomposedStringWithCanonicalMapping.unicodeScalars),
                "fixture must really be NFD")
        #expect(fr.violation(nfd)?.category == "diagnostic")
        #expect(fr.violation(nfd)?.term == "détect")
    }

    /// Spanish: everyday phrases that contain a forbidden-looking word stay clean, and the diagnostic
    /// stem is caught in any case.
    @Test func spanishVerdicts() {
        let es = policy(.es)
        for clean in ["Se trata de tus registros", "Mejor día", "Datos seguros", "Por cierto"] {
            let hits = es.violations(clean)
            #expect(hits.isEmpty, "[ES] \"\(clean)\" must be clean, but hit \(hits)")
        }
        for text in ["La app detecta tus brotes.", "LA APP DETECTA TUS BROTES."] {
            let violation = es.violation(text)
            #expect(violation?.category == "diagnostic", "[ES] \"\(text)\"")
            #expect(violation?.term == "detect", "[ES] \"\(text)\"")
        }
    }

    /// Every category that holds rules is exercised by at least one mustFail fixture, per language.
    @Test(arguments: PolicyLanguage.allCases)
    func everyCategoryHasAMustFail(_ language: PolicyLanguage) {
        let covered = Set(policy(language).selfTest.mustFail.map(\.category))
        for category in RuleKinds.categoryOrder {
            #expect(covered.contains(category), "[\(language.tag)] no mustFail fixture for category \(category)")
        }
    }

    @Test func fromTagRejectsUnknownTag() {
        #expect((try? PolicyLanguage.fromTag("fr")) == .fr)
        #expect(throws: L10nPolicyError.unknownLanguageTag("en")) { try PolicyLanguage.fromTag("en") }
        #expect(throws: L10nPolicyError.unknownLanguageTag("DE")) { try PolicyLanguage.fromTag("DE") }
    }

    private func codePoints(_ text: String) -> String {
        text.unicodeScalars.map { String(format: "U+%04X", $0.value) }.joined(separator: " ")
    }
}
