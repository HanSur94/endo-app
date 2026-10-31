// EndoApp — the per-language claims policy model and matcher (SAFE-01/SAFE-02/SAFE-04).
//
// Mirror counterparts: `android/insights/src/main/kotlin/com/endoapp/insights/l10n/LanguageClaimsPolicy.kt`
// and `PolicyLanguage.kt`. Contract: the decode target of `l10n/claims-policy.<tag>.json` plus
// `l10n/README.md` §Matching spec steps 4-6. Any change here needs the same change in Kotlin.
//
// English text never goes through this matcher: the English `ClaimsPolicy` path is untouched (SAFE-04).
// Rules are plain strings evaluated by the `RuleKinds` predicates, never regular expressions, and every
// comparison runs on Unicode SCALARS so Swift canonical equivalence and grapheme clustering cannot
// change a verdict the JVM reached (Pitfall 2 / T-26-21).
import Foundation

/// A language with a shared, data-driven claims policy. There is deliberately no English case: the
/// English lists stay in code and are never replaced (SAFE-04).
enum PolicyLanguage: String, CaseIterable, Sendable {
    case de, fr, es

    /// The filename tag (`l10n/claims-policy.<tag>.json`).
    var tag: String { rawValue }

    /// The language for a tag. Throws on an unknown tag: there is no fallback policy.
    static func fromTag(_ tag: String) throws -> PolicyLanguage {
        guard let language = PolicyLanguage(rawValue: tag) else {
            throw L10nPolicyError.unknownLanguageTag(tag)
        }
        return language
    }
}

/// Fail-closed loading errors (T-26-20). No code path turns one of these into an empty policy.
enum L10nPolicyError: Error, Equatable {
    case missingResource(String)
    case languageMismatch(expected: String, found: String)
    case unknownLanguageTag(String)
    case unexpectedKeys(path: String, extra: [String], missing: [String])
}

/// One hit: the category, the rule kind and the matched term.
struct Violation: Hashable, Sendable {
    let category: String
    let kind: String
    let term: String
}

/// The fixed evaluation order and the README step 5 predicates. Shared with the glossary matcher.
enum RuleKinds {
    static let raw = "raw"
    static let word = "word"
    static let prefix = "prefix"
    static let stem = "stem"
    static let phrase = "phrase"

    static let categoryOrder: [String] = [
        "diagnostic", "treatment", "causal", "certainty", "alarmist", "imperative", "gendered", "precision",
    ]

    static let kindOrder: [String] = [raw, word, prefix, stem, phrase]

    /// raw: the folded string contains the term. Ignores masking.
    static func raw(_ folded: String, _ term: String) -> Bool {
        scalarContains(folded, term)
    }

    /// word: an unmasked token's folded form equals the term.
    static func word(_ tokens: [L10nToken], _ masked: [Bool], _ term: String) -> Bool {
        tokens.indices.contains { !masked[$0] && scalarEqual(tokens[$0].folded, term) }
    }

    /// prefix: an unmasked token's folded form starts with the term.
    static func prefix(_ tokens: [L10nToken], _ masked: [Bool], _ term: String) -> Bool {
        tokens.indices.contains { !masked[$0] && scalarStartsWith(tokens[$0].folded, term) }
    }

    /// stem: an unmasked token's folded form contains the term.
    static func stem(_ tokens: [L10nToken], _ masked: [Bool], _ term: String) -> Bool {
        tokens.indices.contains { !masked[$0] && scalarContains(tokens[$0].folded, term) }
    }

    /// phrase: k consecutive unmasked tokens, the first k-1 equal to `termTokens`, the last
    /// prefix-matched (`führt zu` catches zum/zur). An empty term never matches.
    static func phrase(_ tokens: [L10nToken], _ masked: [Bool], _ termTokens: [String]) -> Bool {
        let k = termTokens.count
        guard k > 0, k <= tokens.count else { return false }
        for i in 0...(tokens.count - k) {
            let window = i..<(i + k)
            if window.contains(where: { masked[$0] }) { continue }
            let headEqual = (0..<(k - 1)).allSatisfy { scalarEqual(tokens[i + $0].folded, termTokens[$0]) }
            if headEqual, scalarStartsWith(tokens[i + k - 1].folded, termTokens[k - 1]) { return true }
        }
        return false
    }

    // MARK: Scalar-wise comparison (never Character-based, never case-insensitive APIs)

    static func scalarEqual(_ lhs: String, _ rhs: String) -> Bool {
        lhs.unicodeScalars.elementsEqual(rhs.unicodeScalars)
    }

    static func scalarStartsWith(_ text: String, _ prefix: String) -> Bool {
        let textScalars = Array(text.unicodeScalars)
        let prefixScalars = Array(prefix.unicodeScalars)
        guard prefixScalars.count <= textScalars.count else { return false }
        return Array(textScalars.prefix(prefixScalars.count)) == prefixScalars
    }

    static func scalarContains(_ text: String, _ term: String) -> Bool {
        let textScalars = Array(text.unicodeScalars)
        let termScalars = Array(term.unicodeScalars)
        guard !termScalars.isEmpty, termScalars.count <= textScalars.count else {
            return termScalars.isEmpty
        }
        for start in 0...(textScalars.count - termScalars.count)
        where Array(textScalars[start..<(start + termScalars.count)]) == termScalars {
            return true
        }
        return false
    }
}

/// The full policy file. Top-level keys in file order (see `l10n/README.md` §Policy file schema).
struct LanguageClaimsPolicy: Codable, Equatable, Sendable {
    let schemaVersion: Int
    let policyRevision: Int
    let language: String
    let reviewStatus: String
    let folding: Folding
    let categories: Categories
    let allow: AllowList
    let framing: [String]
    let selfTest: SelfTest

    /// README step 2: the folded string of `text`.
    func fold(_ text: String) -> String {
        L10nText.foldString(text, replace: folding.replace)
    }

    /// README step 3: the tokens of `text` with this policy's replace pairs.
    func tokens(_ text: String) -> [L10nToken] {
        L10nText.tokenize(text, replace: folding.replace)
    }

    /// README step 4: true where a token is masked by an allow token (folded equality) or by an
    /// occurrence of an allow phrase (exact folded-token sequence).
    func mask(_ tokens: [L10nToken]) -> [Bool] {
        let allowTokens = Set(allow.tokens.map(\.token))
        var masked = tokens.map { allowTokens.contains($0.folded) }
        for entry in allow.phrases {
            let sequence = self.tokens(entry.phrase).map(\.folded)
            let k = sequence.count
            guard k > 0, k <= tokens.count else { continue }
            for i in 0...(tokens.count - k) {
                let matches = (0..<k).allSatisfy {
                    RuleKinds.scalarEqual(tokens[i + $0].folded, sequence[$0])
                }
                if matches { for j in i..<(i + k) { masked[j] = true } }
            }
        }
        return masked
    }

    /// Every hit in fixed category -> kind -> list order (README step 6), deduplicated.
    /// `applyAllow == false` disables masking (used by the allow-collision proof).
    func violations(_ text: String, applyAllow: Bool = true) -> [Violation] {
        scan(text, applyAllow: applyAllow, firstOnly: false)
    }

    /// The first hit in rule order, or nil when `text` is clean.
    func violation(_ text: String, applyAllow: Bool = true) -> Violation? {
        scan(text, applyAllow: applyAllow, firstOnly: true).first
    }

    private func scan(_ text: String, applyAllow: Bool, firstOnly: Bool) -> [Violation] {
        let folded = fold(text)
        let toks = tokens(text)
        let masked = applyAllow ? mask(toks) : [Bool](repeating: false, count: toks.count)
        var hits: [Violation] = []
        var seen = Set<Violation>()

        for category in RuleKinds.categoryOrder {
            let ruleSet = categories.ruleSet(category)
            for kind in RuleKinds.kindOrder {
                for term in ruleSet.terms(kind) {
                    let hit: Bool
                    switch kind {
                    case RuleKinds.raw: hit = RuleKinds.raw(folded, term)
                    case RuleKinds.word: hit = RuleKinds.word(toks, masked, term)
                    case RuleKinds.prefix: hit = RuleKinds.prefix(toks, masked, term)
                    case RuleKinds.stem: hit = RuleKinds.stem(toks, masked, term)
                    case RuleKinds.phrase: hit = RuleKinds.phrase(toks, masked, phraseTermTokens[term] ?? [])
                    default: preconditionFailure("Unknown rule kind '\(kind)' (fail-closed)")
                    }
                    if hit {
                        let violation = Violation(category: category, kind: kind, term: term)
                        if seen.insert(violation).inserted {
                            hits.append(violation)
                            if firstOnly { return hits }
                        }
                    }
                }
            }
        }
        return hits
    }

    /// Every phrase rule term tokenized once, as folded-token sequences.
    private var phraseTermTokens: [String: [String]] {
        var map: [String: [String]] = [:]
        for category in RuleKinds.categoryOrder {
            for term in categories.ruleSet(category).phrase where map[term] == nil {
                map[term] = tokens(term).map(\.folded)
            }
        }
        return map
    }
}

/// `folding.replace`: `[from, to]` pairs applied in array order after lowercasing.
struct Folding: Codable, Equatable, Sendable {
    let replace: [[String]]
}

/// The 8 categories in the fixed evaluation order.
struct Categories: Codable, Equatable, Sendable {
    let diagnostic: RuleSet
    let treatment: RuleSet
    let causal: RuleSet
    let certainty: RuleSet
    let alarmist: RuleSet
    let imperative: RuleSet
    let gendered: RuleSet
    let precision: RuleSet

    /// The rule set for a category name from `RuleKinds.categoryOrder`. Traps on an unknown name,
    /// like the Kotlin `throw` — the name list is closed.
    func ruleSet(_ category: String) -> RuleSet {
        switch category {
        case "diagnostic": return diagnostic
        case "treatment": return treatment
        case "causal": return causal
        case "certainty": return certainty
        case "alarmist": return alarmist
        case "imperative": return imperative
        case "gendered": return gendered
        case "precision": return precision
        default: preconditionFailure("Unknown claims category '\(category)' (fail-closed)")
        }
    }
}

/// The 5 rule kinds of one category. Arrays may be empty but every key must be present.
struct RuleSet: Codable, Equatable, Sendable {
    let raw: [String]
    let word: [String]
    let prefix: [String]
    let stem: [String]
    let phrase: [String]

    /// The terms for a kind name from `RuleKinds.kindOrder`. Traps on an unknown name.
    func terms(_ kind: String) -> [String] {
        switch kind {
        case RuleKinds.raw: return raw
        case RuleKinds.word: return word
        case RuleKinds.prefix: return prefix
        case RuleKinds.stem: return stem
        case RuleKinds.phrase: return phrase
        default: preconditionFailure("Unknown rule kind '\(kind)' (fail-closed)")
        }
    }
}

struct AllowList: Codable, Equatable, Sendable {
    let tokens: [AllowToken]
    let phrases: [AllowPhrase]
}

struct AllowToken: Codable, Equatable, Sendable {
    let token: String
    let reason: String
    let collidesWith: [String]
}

struct AllowPhrase: Codable, Equatable, Sendable {
    let phrase: String
    let reason: String
    let collidesWith: [String]
}

/// The shared fixtures both platforms must reproduce verdict for verdict.
struct SelfTest: Codable, Equatable, Sendable {
    let normalization: [NormalizationCase]
    let tokenize: [TokenizeCase]
    let mustFail: [MustFailCase]
    let mustPass: [String]
}

struct NormalizationCase: Codable, Equatable, Sendable {
    let input: String
    let folded: String
}

struct TokenizeCase: Codable, Equatable, Sendable {
    let input: String
    let tokens: [String]
    let sentenceInitial: [Bool]
}

struct MustFailCase: Codable, Equatable, Sendable {
    let text: String
    let category: String
    let term: String
}
