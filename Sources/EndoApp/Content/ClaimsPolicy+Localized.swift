// EndoApp — `ClaimsPolicy.forLanguage(lang)`: the DE/FR/ES claims policies loaded from the app bundle
// (SAFE-01/SAFE-02/SAFE-04).
//
// Mirror counterparts: `android/insights/src/main/kotlin/com/endoapp/insights/l10n/ForbiddenTermsLocalized.kt`
// and `ClaimsPolicyLocalized.kt`. This is an EXTENSION in a new file on purpose: the English
// `ClaimsPolicy.swift` stays byte-identical (SAFE-04), and no `static let …: [String] = [` list literal
// may ever be added there (the Android drift test regex-parses that file).
//
// Loading is fail-closed: a missing bundle resource, a language mismatch or a decode error traps with
// the file name and the fix, so a broken policy can never degrade into "no checks".
import Foundation

/// Anchor for `Bundle(for:)` — resolves to the app bundle both in the app and in the hosted
/// `EndoAppTests` target (test bundles asking for the *main* bundle would find nothing).
final class L10nBundleAnchor: NSObject {}

extension ClaimsPolicy {

    /// The cached claims policy for `language`. Returns the same instance on every call.
    static func forLanguage(_ language: PolicyLanguage) -> LanguageClaimsPolicy {
        localizedPolicies[language]!
    }

    /// Strictly loads the policy for `expected` from `bundle`.
    static func loadLanguagePolicy(from bundle: Bundle, expected: PolicyLanguage) throws -> LanguageClaimsPolicy {
        let name = "claims-policy.\(expected.tag)"
        guard let url = bundle.url(forResource: name, withExtension: "json") else {
            throw L10nPolicyError.missingResource("\(name).json")
        }
        let policy = try LanguageClaimsPolicy.decode(try Data(contentsOf: url))
        guard policy.language == expected.tag else {
            throw L10nPolicyError.languageMismatch(expected: expected.tag, found: policy.language)
        }
        return policy
    }

    /// The bundled file URL for `language`, or nil when it did not make it into the bundle.
    static func bundledPolicyURL(for language: PolicyLanguage) -> URL? {
        Bundle(for: L10nBundleAnchor.self).url(forResource: "claims-policy.\(language.tag)", withExtension: "json")
    }

    /// All three policies, decoded once from the app bundle on first use.
    private static let localizedPolicies: [PolicyLanguage: LanguageClaimsPolicy] = {
        var map: [PolicyLanguage: LanguageClaimsPolicy] = [:]
        for language in PolicyLanguage.allCases {
            do {
                map[language] = try loadLanguagePolicy(from: Bundle(for: L10nBundleAnchor.self),
                                                       expected: language)
            } catch {
                fatalError("""
                    l10n claims policy 'claims-policy.\(language.tag).json' failed to load: \(error).
                    Re-run `xcodegen generate` so the file is bundled (l10n is a resources source in
                    project.yml). Fail-closed: no policy means no claims check.
                    """)
            }
        }
        return map
    }()
}

extension LanguageClaimsPolicy {
    /// Strict JSON entry point. `JSONDecoder` alone silently ignores an unknown key, which would drop a
    /// whole rule class from the shipped policy (T-26-19); the key-set walk runs first and throws
    /// `L10nPolicyError.unexpectedKeys` at the first object whose keys differ.
    static func decode(_ data: Data) throws -> LanguageClaimsPolicy {
        try StrictPolicySchema.validate(data)
        return try JSONDecoder().decode(LanguageClaimsPolicy.self, from: data)
    }
}

/// The exact key sets of `l10n/README.md` §Policy file schema, walked on the raw JSON — the counterpart
/// of kotlinx's strict decoding on Android.
enum StrictPolicySchema {
    static let topLevel = ["schemaVersion", "policyRevision", "language", "reviewStatus",
                           "folding", "categories", "allow", "framing", "selfTest"]

    static func validate(_ data: Data) throws {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw L10nPolicyError.unexpectedKeys(path: "<root>", extra: [], missing: [])
        }
        try expectKeys(root, topLevel, "<root>")

        if let folding = root["folding"] as? [String: Any] {
            try expectKeys(folding, ["replace"], "folding")
        }
        if let categories = root["categories"] as? [String: Any] {
            try expectKeys(categories, RuleKinds.categoryOrder, "categories")
            for category in RuleKinds.categoryOrder {
                if let ruleSet = categories[category] as? [String: Any] {
                    try expectKeys(ruleSet, RuleKinds.kindOrder, "categories.\(category)")
                }
            }
        }
        if let allow = root["allow"] as? [String: Any] {
            try expectKeys(allow, ["tokens", "phrases"], "allow")
            for (index, entry) in (allow["tokens"] as? [[String: Any]] ?? []).enumerated() {
                try expectKeys(entry, ["token", "reason", "collidesWith"], "allow.tokens[\(index)]")
            }
            for (index, entry) in (allow["phrases"] as? [[String: Any]] ?? []).enumerated() {
                try expectKeys(entry, ["phrase", "reason", "collidesWith"], "allow.phrases[\(index)]")
            }
        }
        if let selfTest = root["selfTest"] as? [String: Any] {
            try expectKeys(selfTest, ["normalization", "tokenize", "mustFail", "mustPass"], "selfTest")
            let fixtures: [(String, [String])] = [
                ("normalization", ["input", "folded"]),
                ("tokenize", ["input", "tokens", "sentenceInitial"]),
                ("mustFail", ["text", "category", "term"]),
            ]
            for (name, keys) in fixtures {
                for (index, entry) in (selfTest[name] as? [[String: Any]] ?? []).enumerated() {
                    try expectKeys(entry, keys, "selfTest.\(name)[\(index)]")
                }
            }
        }
    }

    private static func expectKeys(_ object: [String: Any], _ expected: [String], _ path: String) throws {
        let actual = Set(object.keys)
        let wanted = Set(expected)
        guard actual == wanted else {
            throw L10nPolicyError.unexpectedKeys(path: path,
                                                 extra: actual.subtracting(wanted).sorted(),
                                                 missing: wanted.subtracting(actual).sorted())
        }
    }
}
