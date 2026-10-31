// EndoAppTests — every allow entry is a proven, justified collision, never a way to smuggle a claim
// (SAFE-02, T-26-30).
//
// Mirror counterpart: `android/app/src/test/kotlin/com/endoapp/app/l10n/AllowListHygieneTest.kt`.
// A `collidesWith` term is real when at least one of: (a) the language's own policy hits it in the entry
// with masking OFF, (b) the glossary register hits it there with masking OFF, or (c) it is an English
// code-list term the folded entry contains (DE `pause` ⊃ `use`, `vorher` ⊃ `her`). An entry needs a
// non-blank reason and at least one real collision, and with the allow applied it must pass both checks.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-02 allow-list hygiene")
struct AllowListHygieneTests {

    private struct Entry {
        let text: String
        let reason: String
        let collidesWith: [String]
    }

    private func glossary() throws -> Glossary {
        try L10nSourceRegistry.glossary(root: L10nSourceRegistry.repoRoot())
    }

    private var englishTerms: [String] {
        ClaimsPolicy.forbiddenVerbs + ClaimsPolicy.forbiddenImperatives
            + ClaimsPolicy.forbiddenAlarmist + ClaimsPolicy.forbiddenGenderedTokens
    }

    private func entries(_ language: PolicyLanguage) -> [Entry] {
        let allow = ClaimsPolicy.forLanguage(language).allow
        return allow.tokens.map { Entry(text: $0.token, reason: $0.reason, collidesWith: $0.collidesWith) }
            + allow.phrases.map { Entry(text: $0.phrase, reason: $0.reason, collidesWith: $0.collidesWith) }
    }

    /// Which proofs hold for `term` in `entry`: "policy", "register", "english". Empty means not real.
    private func realCollisions(_ language: PolicyLanguage, _ entry: String, _ term: String) throws -> [String] {
        let policy = ClaimsPolicy.forLanguage(language)
        let glossary = try glossary()
        var proofs: [String] = []
        if policy.violations(entry, applyAllow: false).contains(where: { $0.term == term }) { proofs.append("policy") }
        if glossary.registerViolations(language, entry, applyAllow: false).contains(where: { $0.term == term }) {
            proofs.append("register")
        }
        if englishTerms.contains(term), policy.fold(entry).contains(term) { proofs.append("english") }
        return proofs
    }

    /// Every hygiene problem of one allow `entry`. Empty means the entry is justified.
    private func allowProblems(_ language: PolicyLanguage, _ entry: Entry) throws -> [String] {
        let whereName = "\(language.tag) allow \"\(entry.text)\""
        var problems: [String] = []
        if entry.reason.trimmingCharacters(in: .whitespaces).isEmpty { problems.append("\(whereName): reason is blank") }
        let real = try entry.collidesWith.filter { try !realCollisions(language, entry.text, $0).isEmpty }
        for term in Set(entry.collidesWith).subtracting(real) {
            problems.append("\(whereName): collidesWith \"\(term)\" is not a real collision (no policy, register or English hit)")
        }
        if real.isEmpty { problems.append("\(whereName): dead entry (no real collision)") }
        let policyHits = ClaimsPolicy.forLanguage(language).violations(entry.text)
        let registerHits = try glossary().registerViolations(language, entry.text)
        if !policyHits.isEmpty || !registerHits.isEmpty {
            problems.append("\(whereName): still fails with the allow applied (\(policyHits) \(registerHits))")
        }
        return problems
    }

    @Test func everyAllowEntryIsARealJustifiedCollision() throws {
        let problems = try PolicyLanguage.allCases.flatMap { language in
            try entries(language).flatMap { try allowProblems(language, $0) }
        }
        #expect(problems.isEmpty, "Allow-list hygiene:\n\(problems.joined(separator: "\n"))")
    }

    @Test func allowListsAreNotVacuous() {
        let total = PolicyLanguage.allCases.reduce(0) { $0 + entries($1).count }
        #expect(total >= 1, "The allow lists must hold entries to check (saw \(total)).")
    }

    @Test func englishTrapControlsAreRealEnglishCollisions() throws {
        #expect(try realCollisions(.de, "pause", "use") == ["english"])
        #expect(try realCollisions(.de, "vorher", "her") == ["english"])
    }

    @Test func frenchEuphonicTIsARealRegisterCollision() throws {
        #expect(try realCollisions(.fr, "a t il", "t") == ["register"])
    }

    @Test func germanCareTeamIsARealPolicyCollision() throws {
        #expect(try realCollisions(.de, "behandlungsteam", "behandl") == ["policy"])
    }

    @Test func checkCatchesDeadAndUnjustifiedEntries() throws {
        let de = PolicyLanguage.de
        let unjustified = try allowProblems(de, Entry(text: "pause", reason: "noun 'break'", collidesWith: ["behandl"]))
        #expect(unjustified.contains { $0.contains("not a real collision") },
                "A collidesWith term the entry does not hit must fail: \(unjustified)")
        let dead = try allowProblems(de, Entry(text: "hallo", reason: "greeting", collidesWith: []))
        #expect(dead.contains { $0.contains("dead entry") }, "An entry with no collision at all is dead: \(dead)")
        let blank = try allowProblems(de, Entry(text: "vorher", reason: " ", collidesWith: ["her"]))
        #expect(blank.contains { $0.contains("reason is blank") }, "An entry without a reason must fail: \(blank)")
        let smuggling = try allowProblems(de, Entry(text: "erkennt", reason: "smuggled claim", collidesWith: ["erkenn"]))
        #expect(smuggling.contains { $0.contains("still fails") },
                "An entry that still fails with the allow applied must fail: \(smuggling)")
    }
}
