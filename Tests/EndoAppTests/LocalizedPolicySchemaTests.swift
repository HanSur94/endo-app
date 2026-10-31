// EndoAppTests — schema, strict-decode and fail-closed proofs for the DE/FR/ES claims policies
// (SAFE-01/SAFE-02/SAFE-04, Pitfall 3 / T-26-19).
//
// Mirror counterpart: `android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySchemaTests.kt`.
// Key sets are walked on the RAW JSON with JSONSerialization: Swift's JSONDecoder silently ignores an
// unknown key, so a mistyped rule-set key would otherwise drop a whole rule class. Missing data fails,
// it never skips. The repo file is read through the same fixed-depth #filePath root as the parity tests.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-01/02 localized policy schema")
struct LocalizedPolicySchemaTests {

    private let topLevelKeys = ["schemaVersion", "policyRevision", "language", "reviewStatus",
                                "folding", "categories", "allow", "framing", "selfTest"]
    private let categories = ["diagnostic", "treatment", "causal", "certainty",
                              "alarmist", "imperative", "gendered", "precision"]
    private let kinds = ["raw", "word", "prefix", "stem", "phrase"]

    // MARK: Raw access

    private func repoRoot() throws -> URL {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        try #require(FileManager.default.fileExists(atPath: root.appendingPathComponent("l10n/README.md").path),
                     "no l10n/README.md at the resolved repo root \(root.path)")
        return root
    }

    private func rawData(_ language: PolicyLanguage) throws -> Data {
        let url = try repoRoot().appendingPathComponent("l10n/claims-policy.\(language.tag).json")
        return try #require(FileManager.default.contents(atPath: url.path), "no repo file at \(url.path)")
    }

    private func rawObject(_ language: PolicyLanguage) throws -> [String: Any] {
        let object = try JSONSerialization.jsonObject(with: rawData(language))
        return try #require(object as? [String: Any], "[\(language.tag)] top level must be an object")
    }

    private func expectKeys(_ expected: [String], _ value: Any?, _ where: String) {
        let actual = (value as? [String: Any]).map { Array($0.keys).sorted() } ?? []
        #expect(actual == expected.sorted(), "exact key set at \(`where`): expected \(expected.sorted()), got \(actual)")
    }

    // MARK: Key sets and header

    @Test(arguments: PolicyLanguage.allCases)
    func exactKeySets(_ language: PolicyLanguage) throws {
        let root = try rawObject(language)
        expectKeys(topLevelKeys, root, "[\(language.tag)] top level")
        expectKeys(["replace"], root["folding"], "[\(language.tag)] folding")
        expectKeys(categories, root["categories"], "[\(language.tag)] categories")

        let categoryObjects = try #require(root["categories"] as? [String: Any])
        for category in categories {
            expectKeys(kinds, categoryObjects[category], "[\(language.tag)] categories.\(category)")
        }

        let allow = try #require(root["allow"] as? [String: Any])
        expectKeys(["tokens", "phrases"], allow, "[\(language.tag)] allow")
        for (index, entry) in (allow["tokens"] as? [[String: Any]] ?? []).enumerated() {
            expectKeys(["token", "reason", "collidesWith"], entry, "[\(language.tag)] allow.tokens[\(index)]")
        }
        for (index, entry) in (allow["phrases"] as? [[String: Any]] ?? []).enumerated() {
            expectKeys(["phrase", "reason", "collidesWith"], entry, "[\(language.tag)] allow.phrases[\(index)]")
        }

        let selfTest = try #require(root["selfTest"] as? [String: Any])
        expectKeys(["normalization", "tokenize", "mustFail", "mustPass"], selfTest, "[\(language.tag)] selfTest")
        for (index, entry) in (selfTest["normalization"] as? [[String: Any]] ?? []).enumerated() {
            expectKeys(["input", "folded"], entry, "[\(language.tag)] selfTest.normalization[\(index)]")
        }
        for (index, entry) in (selfTest["tokenize"] as? [[String: Any]] ?? []).enumerated() {
            expectKeys(["input", "tokens", "sentenceInitial"], entry, "[\(language.tag)] selfTest.tokenize[\(index)]")
        }
        for (index, entry) in (selfTest["mustFail"] as? [[String: Any]] ?? []).enumerated() {
            expectKeys(["text", "category", "term"], entry, "[\(language.tag)] selfTest.mustFail[\(index)]")
        }
        for (index, entry) in (selfTest["mustPass"] as? [Any] ?? []).enumerated() {
            #expect(entry is String, "[\(language.tag)] selfTest.mustPass[\(index)] must be a string")
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func headerValues(_ language: PolicyLanguage) throws {
        let root = try rawObject(language)
        #expect(root["schemaVersion"] as? Int == 1, "[\(language.tag)] schemaVersion")
        #expect((root["policyRevision"] as? Int ?? 0) >= 1, "[\(language.tag)] policyRevision must be >= 1")
        #expect(root["language"] as? String == language.tag, "[\(language.tag)] language must equal the filename tag")
        // Asserted, never written: only the Phase 33 native-speaker and legal review may change it.
        #expect(root["reviewStatus"] as? String == "draft-unreviewed", "[\(language.tag)] reviewStatus")

        let policy = ClaimsPolicy.forLanguage(language)
        #expect(policy.language == language.tag)
        #expect(policy.reviewStatus == "draft-unreviewed")
    }

    // MARK: Data hygiene

    @Test(arguments: PolicyLanguage.allCases)
    func termsArePreFolded(_ language: PolicyLanguage) {
        let policy = ClaimsPolicy.forLanguage(language)
        for category in RuleKinds.categoryOrder {
            let ruleSet = policy.categories.ruleSet(category)
            for kind in RuleKinds.kindOrder {
                for term in ruleSet.terms(kind) {
                    #expect(policy.fold(term) == term, "[\(language.tag)] \(category).\(kind) term \"\(term)\" is not pre-folded")
                }
            }
        }
        for token in policy.allow.tokens {
            #expect(policy.fold(token.token) == token.token, "[\(language.tag)] allow token \"\(token.token)\" is not pre-folded")
        }
        for phrase in policy.allow.phrases {
            #expect(policy.fold(phrase.phrase) == phrase.phrase, "[\(language.tag)] allow phrase \"\(phrase.phrase)\" is not pre-folded")
        }
        for entry in policy.framing {
            #expect(policy.fold(entry) == entry, "[\(language.tag)] framing \"\(entry)\" is not pre-folded")
        }
    }

    @Test(arguments: PolicyLanguage.allCases)
    func termShapes(_ language: PolicyLanguage) {
        let policy = ClaimsPolicy.forLanguage(language)
        for category in RuleKinds.categoryOrder {
            let ruleSet = policy.categories.ruleSet(category)
            for kind in [RuleKinds.word, RuleKinds.prefix, RuleKinds.stem] {
                for term in ruleSet.terms(kind) {
                    let tokens = policy.tokens(term).map(\.folded)
                    #expect(tokens == [term], "[\(language.tag)] \(category).\(kind) term \"\(term)\" must be exactly one token, got \(tokens)")
                }
            }
            for term in ruleSet.phrase {
                let tokens = policy.tokens(term)
                #expect(tokens.count >= 2, "[\(language.tag)] \(category).phrase term \"\(term)\" needs two or more tokens")
            }
        }
    }

    /// The file is NFC, LF-only and keeps the dangerous code points as `\u` escapes so no editor can
    /// silently normalize them away.
    @Test(arguments: PolicyLanguage.allCases)
    func fileIsNfcLfAndEscaped(_ language: PolicyLanguage) throws {
        let text = try #require(String(data: rawData(language), encoding: .utf8))
        #expect(!text.contains("\r"), "[\(language.tag)] file must use LF, not CRLF")
        #expect(text == text.precomposedStringWithCanonicalMapping, "[\(language.tag)] file must be NFC")

        let escaped: [(Character, String)] = [("\u{1E9E}", "1E9E"), ("\u{00A0}", "00A0"), ("\u{202F}", "202F"),
                                             ("\u{2009}", "2009"), ("\u{2007}", "2007"), ("\u{2019}", "2019"),
                                             ("\u{2018}", "2018"), ("\u{02BC}", "02BC")]
        for (character, code) in escaped {
            #expect(!text.contains(character), "[\(language.tag)] U+\(code) must be written as a \\u escape")
        }
    }

    // MARK: Strict decode (red before the strict decoder, green after)

    private func mutated(_ language: PolicyLanguage, _ mutate: (inout [String: Any]) -> Void) throws -> Data {
        var root = try rawObject(language)
        mutate(&root)
        return try JSONSerialization.data(withJSONObject: root, options: [])
    }

    @Test func unknownKeyIsRejected() throws {
        let data = try mutated(.de) { root in
            var categories = root["categories"] as! [String: Any]
            var diagnostic = categories["diagnostic"] as! [String: Any]
            let prefix = diagnostic.removeValue(forKey: "prefix")
            diagnostic["prefx"] = prefix
            categories["diagnostic"] = diagnostic
            root["categories"] = categories
        }
        #expect(throws: L10nPolicyError.unexpectedKeys(path: "categories.diagnostic",
                                                       extra: ["prefx"], missing: ["prefix"])) {
            try LanguageClaimsPolicy.decode(data)
        }
    }

    @Test func missingKindIsRejected() throws {
        let data = try mutated(.fr) { root in
            var categories = root["categories"] as! [String: Any]
            var causal = categories["causal"] as! [String: Any]
            causal.removeValue(forKey: "stem")
            categories["causal"] = causal
            root["categories"] = categories
        }
        #expect(throws: L10nPolicyError.unexpectedKeys(path: "categories.causal",
                                                       extra: [], missing: ["stem"])) {
            try LanguageClaimsPolicy.decode(data)
        }
    }

    @Test func topLevelExtraKeyIsRejected() throws {
        let data = try mutated(.es) { $0["notes"] = "unexpected" }
        #expect(throws: L10nPolicyError.unexpectedKeys(path: "<root>",
                                                       extra: ["notes"], missing: [])) {
            try LanguageClaimsPolicy.decode(data)
        }
    }

    @Test func loaderFailsClosedOnMissingResource() {
        let emptyBundle = Bundle(path: NSTemporaryDirectory()) ?? Bundle.main
        #expect(throws: L10nPolicyError.missingResource("claims-policy.de.json")) {
            try ClaimsPolicy.loadLanguagePolicy(from: emptyBundle, expected: .de)
        }
    }
}
