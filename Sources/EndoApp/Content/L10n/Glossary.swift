// EndoApp — the shared l10n glossary: approved terms, banned variants and the du / vous / tú register
// (SAFE-03, SAFE-01).
//
// Mirror counterpart: `android/insights/src/main/kotlin/com/endoapp/insights/l10n/Glossary.kt`.
// Contract: the strict decode target of `l10n/glossary.json` plus `l10n/README.md` §Glossary schema.
// glossary.json is TEST-ONLY data: it is never bundled and never loaded at runtime — tests read it by
// path (`L10nSourceRegistry.glossary`) and call `Glossary.decode`. Banned rules reuse the language
// policy's tokens, fold and allow mask plus the 26-04 `RuleKinds` predicates (never duplicated). The two
// cased kinds compare the NFC typography view exactly, because a register check must never case-fold.
import Foundation

/// The full glossary file. Top-level keys in file order. Every field is required.
struct Glossary: Codable, Equatable, Sendable {
    let schemaVersion: Int
    let policyRevision: Int
    let reviewStatus: String
    let register: RegisterByLanguage
    let doNotTranslate: [String]
    let concepts: [Concept]
    let selfTest: GlossarySelfTest

    /// The category every register hit reports.
    static let registerCategory = "register"

    /// Every register hit for `text` in `lang`, in kind then list order, deduplicated.
    func registerViolations(_ language: PolicyLanguage, _ text: String, applyAllow: Bool = true) -> [Violation] {
        bannedHits(register.forLanguage(language).banned,
                   policy: ClaimsPolicy.forLanguage(language),
                   text: text, applyAllow: applyAllow,
                   category: Glossary.registerCategory, firstOnly: false)
    }

    /// The first register hit, or nil when `text` keeps the language's register.
    func registerViolation(_ language: PolicyLanguage, _ text: String, applyAllow: Bool = true) -> Violation? {
        bannedHits(register.forLanguage(language).banned,
                   policy: ClaimsPolicy.forLanguage(language),
                   text: text, applyAllow: applyAllow,
                   category: Glossary.registerCategory, firstOnly: true).first
    }

    /// Every banned hit of `concept` in `lang` for `text`. Category is the concept id.
    func conceptBannedViolations(_ concept: Concept, _ language: PolicyLanguage, _ text: String,
                                 applyAllow: Bool = true) -> [Violation] {
        bannedHits(concept.forLanguage(language).banned,
                   policy: ClaimsPolicy.forLanguage(language),
                   text: text, applyAllow: applyAllow,
                   category: concept.id, firstOnly: false)
    }

    /// The conformance verdict of translation `text` in `lang` for EN source `en`. Fixed order:
    /// 1. register, unconditional; 2. for the concepts whose EN trigger phrase-matches `en` (concept
    /// order), the first banned hit with the allow mask applied; 3. the first of those concepts with no
    /// approved form phrase-matching `text` (approved matching IGNORES the mask, because an approved form
    /// such as FR "entraînement" can itself be an allow token).
    func conformance(en: String, lang: PolicyLanguage, text: String) -> GlossaryVerdict {
        if let hit = registerViolation(lang, text) { return .register(term: hit.term) }

        let policy = ClaimsPolicy.forLanguage(lang)
        let enTokens = L10nText.tokenize(en, replace: [])
        let enUnmasked = [Bool](repeating: false, count: enTokens.count)
        let triggered = concepts.filter { concept in
            concept.en.triggers.contains { trigger in
                RuleKinds.phrase(enTokens, enUnmasked, L10nText.tokenize(trigger, replace: []).map(\.folded))
            }
        }
        for concept in triggered {
            if let hit = conceptBannedViolations(concept, lang, text).first {
                return .banned(concept: concept.id, term: hit.term)
            }
        }
        let textTokens = policy.tokens(text)
        let textUnmasked = [Bool](repeating: false, count: textTokens.count)
        for concept in triggered {
            let hasApproved = concept.forLanguage(lang).approved.contains { form in
                RuleKinds.phrase(textTokens, textUnmasked, policy.tokens(form).map(\.folded))
            }
            if !hasApproved { return .missingApproved(concept: concept.id) }
        }
        return .pass
    }

    /// Evaluates `rules` on `text` in `BannedRules.kindOrder` then list order, with the policy's fold,
    /// tokens and (when `applyAllow`) allow mask.
    private func bannedHits(_ rules: BannedRules, policy: LanguageClaimsPolicy, text: String,
                            applyAllow: Bool, category: String, firstOnly: Bool) -> [Violation] {
        let folded = policy.fold(text)
        let toks = policy.tokens(text)
        let masked = applyAllow ? policy.mask(toks) : [Bool](repeating: false, count: toks.count)
        var hits: [Violation] = []
        var seen = Set<Violation>()

        for kind in BannedRules.kindOrder {
            for term in rules.terms(kind) {
                let hit: Bool
                switch kind {
                case RuleKinds.raw: hit = RuleKinds.raw(folded, term)
                case RuleKinds.word: hit = RuleKinds.word(toks, masked, term)
                case RuleKinds.prefix: hit = RuleKinds.prefix(toks, masked, term)
                case RuleKinds.stem: hit = RuleKinds.stem(toks, masked, term)
                case RuleKinds.phrase: hit = RuleKinds.phrase(toks, masked, policy.tokens(term).map(\.folded))
                case BannedRules.wordCased:
                    hit = toks.indices.contains { !masked[$0] && RuleKinds.scalarEqual(toks[$0].text, term) }
                case BannedRules.wordCasedMidSentence:
                    hit = toks.indices.contains {
                        !masked[$0] && !toks[$0].sentenceInitial && RuleKinds.scalarEqual(toks[$0].text, term)
                    }
                default: preconditionFailure("Unknown glossary rule kind '\(kind)' (fail-closed)")
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
        return hits
    }

    /// Strict decode: unknown and missing required keys both throw, reusing the 26-04 key-set walk
    /// (T-26-31). `BannedRules` accepts any SUBSET of the seven kinds; selfTest pair keys are required
    /// even when their value is null.
    static func decode(_ data: Data) throws -> Glossary {
        try StrictGlossarySchema.validate(data)
        return try JSONDecoder().decode(Glossary.self, from: data)
    }
}

/// The conformance verdict (README §Glossary schema, Semantics).
enum GlossaryVerdict: Equatable, Sendable {
    /// Register kept, no banned variant, every triggered concept has an approved form.
    case pass
    /// The text breaks the language's register with `term`.
    case register(term: String)
    /// The text uses `term`, a banned variant of a triggered `concept`.
    case banned(concept: String, term: String)
    /// A triggered `concept` has no approved form in the text.
    case missingApproved(concept: String)
}

struct RegisterByLanguage: Codable, Equatable, Sendable {
    let de: RegisterBlock
    let fr: RegisterBlock
    let es: RegisterBlock

    func forLanguage(_ language: PolicyLanguage) -> RegisterBlock {
        switch language {
        case .de: return de
        case .fr: return fr
        case .es: return es
        }
    }
}

/// One language's register: the address `form` (du / vous / tú) and the `banned` forms.
struct RegisterBlock: Codable, Equatable, Sendable {
    let form: String
    let banned: BannedRules
}

/// Banned forms in any subset of the seven kinds; absent kinds decode to empty. Folded kinds hold
/// pre-folded terms, the cased kinds hold exact-case NFC terms.
struct BannedRules: Codable, Equatable, Sendable {
    let raw: [String]
    let word: [String]
    let prefix: [String]
    let stem: [String]
    let phrase: [String]
    let wordCased: [String]
    let wordCasedMidSentence: [String]

    static let wordCased = "wordCased"
    static let wordCasedMidSentence = "wordCasedMidSentence"

    /// The five folded kinds, then the two cased kinds.
    static let kindOrder: [String] = RuleKinds.kindOrder + [wordCased, wordCasedMidSentence]

    /// The kinds compared on the cased NFC view instead of the folded form.
    static let casedKinds: Set<String> = [wordCased, wordCasedMidSentence]

    init(raw: [String] = [], word: [String] = [], prefix: [String] = [], stem: [String] = [],
         phrase: [String] = [], wordCased: [String] = [], wordCasedMidSentence: [String] = []) {
        self.raw = raw
        self.word = word
        self.prefix = prefix
        self.stem = stem
        self.phrase = phrase
        self.wordCased = wordCased
        self.wordCasedMidSentence = wordCasedMidSentence
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        raw = try container.decodeIfPresent([String].self, forKey: .raw) ?? []
        word = try container.decodeIfPresent([String].self, forKey: .word) ?? []
        prefix = try container.decodeIfPresent([String].self, forKey: .prefix) ?? []
        stem = try container.decodeIfPresent([String].self, forKey: .stem) ?? []
        phrase = try container.decodeIfPresent([String].self, forKey: .phrase) ?? []
        wordCased = try container.decodeIfPresent([String].self, forKey: .wordCased) ?? []
        wordCasedMidSentence = try container.decodeIfPresent([String].self, forKey: .wordCasedMidSentence) ?? []
    }

    /// The terms for a kind name from `kindOrder`. Traps on an unknown name.
    func terms(_ kind: String) -> [String] {
        switch kind {
        case RuleKinds.raw: return raw
        case RuleKinds.word: return word
        case RuleKinds.prefix: return prefix
        case RuleKinds.stem: return stem
        case RuleKinds.phrase: return phrase
        case BannedRules.wordCased: return wordCased
        case BannedRules.wordCasedMidSentence: return wordCasedMidSentence
        default: preconditionFailure("Unknown glossary rule kind '\(kind)' (fail-closed)")
        }
    }
}

/// One concept: its EN triggers and the per-language approved and banned forms.
struct Concept: Codable, Equatable, Sendable {
    let id: String
    let context: String
    let en: EnTriggers
    let de: ConceptTerms
    let fr: ConceptTerms
    let es: ConceptTerms

    func forLanguage(_ language: PolicyLanguage) -> ConceptTerms {
        switch language {
        case .de: return de
        case .fr: return fr
        case .es: return es
        }
    }
}

struct EnTriggers: Codable, Equatable, Sendable {
    let triggers: [String]
}

struct ConceptTerms: Codable, Equatable, Sendable {
    let approved: [String]
    let banned: BannedRules
}

struct GlossarySelfTest: Codable, Equatable, Sendable {
    let pairs: [GlossaryPair]
}

/// A shared conformance fixture. `concept` and `term` are nullable but REQUIRED keys.
struct GlossaryPair: Codable, Equatable, Sendable {
    let en: String
    let lang: String
    let text: String
    let expect: String
    let concept: String?
    let term: String?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        en = try container.decode(String.self, forKey: .en)
        lang = try container.decode(String.self, forKey: .lang)
        text = try container.decode(String.self, forKey: .text)
        expect = try container.decode(String.self, forKey: .expect)
        // `decode` on an Optional throws when the key is absent and returns nil for an explicit null.
        concept = try container.decode(String?.self, forKey: .concept)
        term = try container.decode(String?.self, forKey: .term)
    }
}

/// The exact key sets of `l10n/README.md` §Glossary schema, walked on the raw JSON. `BannedRules`
/// accepts any SUBSET of the seven kinds; every other object is exact.
enum StrictGlossarySchema {
    static let topLevel = ["schemaVersion", "policyRevision", "reviewStatus", "register",
                           "doNotTranslate", "concepts", "selfTest"]
    static let conceptKeys = ["id", "context", "en", "de", "fr", "es"]
    static let pairKeys = ["en", "lang", "text", "expect", "concept", "term"]

    static func validate(_ data: Data) throws {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw L10nPolicyError.unexpectedKeys(path: "<root>", extra: [], missing: [])
        }
        try expectExact(root, topLevel, "<root>")

        if let register = root["register"] as? [String: Any] {
            try expectExact(register, ["de", "fr", "es"], "register")
            for tag in ["de", "fr", "es"] {
                if let block = register[tag] as? [String: Any] {
                    try expectExact(block, ["form", "banned"], "register.\(tag)")
                    if let banned = block["banned"] as? [String: Any] {
                        try expectSubset(banned, Set(BannedRules.kindOrder), "register.\(tag).banned")
                    }
                }
            }
        }

        for (index, entry) in (root["concepts"] as? [[String: Any]] ?? []).enumerated() {
            try expectExact(entry, conceptKeys, "concepts[\(index)]")
            if let en = entry["en"] as? [String: Any] {
                try expectExact(en, ["triggers"], "concepts[\(index)].en")
            }
            for tag in ["de", "fr", "es"] {
                if let terms = entry[tag] as? [String: Any] {
                    try expectExact(terms, ["approved", "banned"], "concepts[\(index)].\(tag)")
                    if let banned = terms["banned"] as? [String: Any] {
                        try expectSubset(banned, Set(BannedRules.kindOrder), "concepts[\(index)].\(tag).banned")
                    }
                }
            }
        }

        if let selfTest = root["selfTest"] as? [String: Any] {
            try expectExact(selfTest, ["pairs"], "selfTest")
            for (index, pair) in (selfTest["pairs"] as? [[String: Any]] ?? []).enumerated() {
                try expectExact(pair, pairKeys, "selfTest.pairs[\(index)]")
            }
        }
    }

    private static func expectExact(_ object: [String: Any], _ expected: [String], _ path: String) throws {
        let actual = Set(object.keys)
        let wanted = Set(expected)
        guard actual == wanted else {
            throw L10nPolicyError.unexpectedKeys(path: path,
                                                 extra: actual.subtracting(wanted).sorted(),
                                                 missing: wanted.subtracting(actual).sorted())
        }
    }

    private static func expectSubset(_ object: [String: Any], _ allowed: Set<String>, _ path: String) throws {
        let extra = Set(object.keys).subtracting(allowed)
        guard extra.isEmpty else {
            throw L10nPolicyError.unexpectedKeys(path: path, extra: extra.sorted(), missing: [])
        }
    }
}
