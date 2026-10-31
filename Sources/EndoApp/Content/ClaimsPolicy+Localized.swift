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
    /// JSON decoding entry point. Task 26-04/03 makes this strict (exact key sets) — see the plan.
    static func decode(_ data: Data) throws -> LanguageClaimsPolicy {
        try JSONDecoder().decode(LanguageClaimsPolicy.self, from: data)
    }
}
