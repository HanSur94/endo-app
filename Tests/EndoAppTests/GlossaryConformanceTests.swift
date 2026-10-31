// EndoAppTests — the glossary is enforced, not advisory (SAFE-03; `l10n/README.md` §Glossary schema).
//
// Mirror counterpart: `android/app/src/test/kotlin/com/endoapp/app/l10n/GlossaryConformanceTest.kt`.
// Reads the REAL l10n/glossary.json and FAILS (never skips) when it is absent. The suite only READS
// reviewStatus — LEGL-03 (native + legal review) stays open until Phase 33.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-03 glossary conformance")
struct GlossaryConformanceTests {

    private func glossary() throws -> Glossary {
        try L10nSourceRegistry.glossary(root: L10nSourceRegistry.repoRoot())
    }

    private func rawGlossaryData() throws -> Data {
        let url = try L10nSourceRegistry.repoRoot().appendingPathComponent("l10n/glossary.json")
        return try #require(FileManager.default.contents(atPath: url.path), "l10n/glossary.json is missing (fail, never skip).")
    }

    // MARK: Shared fixtures

    @Test func selfTestPairsYieldExpectedVerdict() throws {
        let glossary = try glossary()
        for pair in glossary.selfTest.pairs {
            let language = try PolicyLanguage.fromTag(pair.lang)
            let verdict = glossary.conformance(en: pair.en, lang: language, text: pair.text)
            switch pair.expect {
            case "pass":
                #expect(verdict == .pass, "[\(pair.lang)] \"\(pair.text)\" should pass, got \(verdict)")
            case "register":
                #expect(verdict == .register(term: pair.term ?? ""), "[\(pair.lang)] \"\(pair.text)\" should break the register, got \(verdict)")
            case "banned":
                #expect(verdict == .banned(concept: pair.concept ?? "", term: pair.term ?? ""),
                        "[\(pair.lang)] \"\(pair.text)\" should hit banned \(pair.concept ?? "?")/\(pair.term ?? "?"), got \(verdict)")
            default:
                Issue.record("unknown selfTest expectation '\(pair.expect)'")
            }
        }
    }

    /// The register is case-sensitive on the NFC view: formal DE mid-sentence forms fail, the
    /// sentence-initial "Sie" (a question) and the du-register pass.
    @Test func registerEdges() throws {
        let glossary = try glossary()
        let cases: [(PolicyLanguage, String, Bool)] = [
            (.de, "Das könnten Sie mit Ihrem Behandlungsteam besprechen.", true),
            (.de, "Sie haben Schmerzen eingetragen?", false),
            (.de, "Deine Einträge sind gespeichert, du kannst sie später bearbeiten.", false),
            (.fr, "Tu peux modifier tes entrées plus tard.", true),
            (.es, "Usted puede cambiar sus registros más tarde.", true),
        ]
        for (language, text, flagged) in cases {
            let hit = glossary.registerViolation(language, text)
            #expect((hit != nil) == flagged, "[\(language.tag)] \"\(text)\" expected flagged=\(flagged), got \(String(describing: hit))")
        }
    }

    @Test func registerMaskingCanBeDisabled() throws {
        let glossary = try glossary()
        // FR allow phrase "a t il" exists precisely so "Y a-t-il" keeps clear of the register word "t".
        let entry = "a t il"
        let masked = glossary.registerViolations(.fr, entry)
        let unmasked = glossary.registerViolations(.fr, entry, applyAllow: false)
        #expect(masked.isEmpty, "\"\(entry)\" must be clear with the allow applied, got \(masked)")
        #expect(unmasked.contains { $0.term == "t" }, "\"\(entry)\" must hit the register without the allow, got \(unmasked)")
    }

    // MARK: Schema

    @Test func decodesStrictly_andRejectsUnknownKey() throws {
        let data = try rawGlossaryData()
        _ = try Glossary.decode(data)   // control: the unmodified file decodes

        var root = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        var concepts = try #require(root["concepts"] as? [[String: Any]])
        concepts[0]["contxet"] = concepts[0]["context"]
        root["concepts"] = concepts
        let mutated = try JSONSerialization.data(withJSONObject: root)
        #expect(throws: L10nPolicyError.self) { try Glossary.decode(mutated) }
    }

    @Test func missingPairKeyIsRejected() throws {
        var root = try #require(try JSONSerialization.jsonObject(with: try rawGlossaryData()) as? [String: Any])
        var selfTest = try #require(root["selfTest"] as? [String: Any])
        var pairs = try #require(selfTest["pairs"] as? [[String: Any]])
        pairs[0].removeValue(forKey: "term")
        selfTest["pairs"] = pairs
        root["selfTest"] = selfTest
        let mutated = try JSONSerialization.data(withJSONObject: root)
        #expect(throws: L10nPolicyError.self) { try Glossary.decode(mutated) }
    }

    @Test func headerValues() throws {
        let glossary = try glossary()
        #expect(glossary.schemaVersion == 1)
        #expect(glossary.policyRevision >= 1)
        #expect(glossary.reviewStatus == "draft-unreviewed")
        #expect(glossary.register.de.form == "du")
        #expect(glossary.register.fr.form == "vous")
        #expect(glossary.register.es.form == "tú")

        let root = try #require(try JSONSerialization.jsonObject(with: try rawGlossaryData()) as? [String: Any])
        // JSONSerialization loses key order; the strict walk owns order-independent exactness.
        #expect(Set(root.keys) == Set(["schemaVersion", "policyRevision", "reviewStatus", "register",
                                       "doNotTranslate", "concepts", "selfTest"]))
    }

    // MARK: Concepts

    @Test func conceptsCoverAllFourLanguages() throws {
        let glossary = try glossary()
        let ids = glossary.concepts.map(\.id)
        #expect(ids.count >= 30, "The glossary must carry at least 30 concepts (has \(ids.count)).")
        #expect(Set(ids).count == ids.count, "Concept ids must be unique.")
        for concept in glossary.concepts {
            #expect(!concept.en.triggers.isEmpty, "\(concept.id): EN triggers must be non-empty.")
            for trigger in concept.en.triggers {
                #expect(!trigger.trimmingCharacters(in: .whitespaces).isEmpty && trigger == trigger.lowercased(),
                        "\(concept.id): trigger '\(trigger)' must be non-blank lowercase.")
            }
            for language in PolicyLanguage.allCases {
                let approved = concept.forLanguage(language).approved
                #expect(!approved.isEmpty, "\(concept.id).\(language.tag): approved must be non-empty.")
                #expect(approved.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty },
                        "\(concept.id).\(language.tag): approved forms must be non-blank.")
            }
        }
    }

    @Test func approvedNeverMatchesOwnBanned() throws {
        let glossary = try glossary()
        var problems: [String] = []
        for concept in glossary.concepts {
            for language in PolicyLanguage.allCases {
                for form in concept.forLanguage(language).approved {
                    // Masking off: an allow token must not be what keeps an approved form clear of its own ban.
                    let hits = glossary.conceptBannedViolations(concept, language, form, applyAllow: false)
                    if !hits.isEmpty { problems.append("\(concept.id).\(language.tag): approved \"\(form)\" hits own banned \(hits)") }
                }
            }
        }
        #expect(problems.isEmpty, "approved ∩ banned must be empty:\n\(problems.joined(separator: "\n"))")
    }

    @Test func bannedTermsAreCanonical() throws {
        let glossary = try glossary()
        var problems: [String] = []
        for language in PolicyLanguage.allCases {
            let policy = ClaimsPolicy.forLanguage(language)
            var blocks: [(String, BannedRules)] = [(Glossary.registerCategory, glossary.register.forLanguage(language).banned)]
            for concept in glossary.concepts {
                blocks.append((concept.id, concept.forLanguage(language).banned))
            }
            for (whereName, rules) in blocks {
                for kind in BannedRules.kindOrder {
                    for term in rules.terms(kind) {
                        let canonical = BannedRules.casedKinds.contains(kind)
                            ? L10nText.typography(term) == term
                            : policy.fold(term) == term
                        if !canonical { problems.append("\(language.tag) \(whereName).\(kind): \"\(term)\" is not canonical") }
                    }
                }
            }
        }
        #expect(problems.isEmpty, "banned terms must be canonical:\n\(problems.joined(separator: "\n"))")
    }

    /// EN triggers are English copy and must pass the unchanged English scan (SAFE-04).
    @Test func enTriggersPassEnglishScan() throws {
        let glossary = try glossary()
        let english = ClaimsPolicy.forbiddenVerbs + ClaimsPolicy.forbiddenImperatives
            + ClaimsPolicy.forbiddenAlarmist + ClaimsPolicy.forbiddenGenderedTokens
        var problems: [String] = []
        for concept in glossary.concepts {
            for trigger in concept.en.triggers {
                for term in english where trigger.contains(term) {
                    problems.append("\(concept.id): trigger \"\(trigger)\" contains English term \"\(term)\"")
                }
            }
        }
        #expect(problems.isEmpty, "EN triggers must pass the English scan:\n\(problems.joined(separator: "\n"))")
    }
}
