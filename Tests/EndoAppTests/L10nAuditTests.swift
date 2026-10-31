// EndoAppTests — the DE/FR/ES counterpart of the English copy audit (SAFE-01, SC4).
//
// Mirror counterpart: `android/app/src/test/kotlin/com/endoapp/app/l10n/L10nAuditTest.kt`.
// Scans every string `L10nSourceRegistry` extracts from the REAL repo and asserts each one is clean
// against `ClaimsPolicy.forLanguage(lang)` and keeps the language's register (SAFE-03).
//
// NON-VACUITY: a scan over too few strings, or over a helper that never fires, would pass trivially. The
// real scan asserts a floor per language, and the controls feed a seeded bad sentence and MUTATED COPIES of
// the real l10n/ folder through the SAME `auditFindings` helper — if a control turns red, the real scan does.
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-01 / SC4 localized source audit")
struct L10nAuditTests {

    @Test func registeredStrings_passPolicy() throws {
        let root = try L10nSourceRegistry.repoRoot()
        let strings = try L10nSourceRegistry.collect(root: root)
        for language in PolicyLanguage.allCases {
            let count = strings.filter { $0.lang == language }.count
            #expect(count >= 30,
                    "The audit must scan at least 30 \(language.tag) strings (saw \(count); registered sources are missing).")
        }
        #expect(strings.contains { $0.origin.contains("claims-policy.") })
        #expect(strings.contains { $0.origin.contains("glossary.json") })

        let findings = try L10nSourceRegistry.auditFindings(strings, root: root)
        #expect(findings.isEmpty,
                "Registered DE/FR/ES strings must carry no forbidden claim:\n\(findings.joined(separator: "\n"))")
    }

    @Test func audit_catchesSeededBadSentences() throws {
        let seeded = PolicyLanguage.allCases.compactMap { language -> L10nString? in
            guard let first = ClaimsPolicy.forLanguage(language).selfTest.mustFail.first else { return nil }
            return L10nString(lang: language, text: first.text, origin: "seeded:\(language.tag)")
        }
        let findings = try L10nSourceRegistry.auditFindings(seeded)
        #expect(findings.count == 3,
                "Each seeded mustFail sentence must yield exactly one finding (harness is enforcing): \(findings)")
    }

    @Test func audit_catchesMutatedRepoCopy() throws {
        let root = try copyRepoL10n()
        defer { try? FileManager.default.removeItem(at: root) }
        let policyFile = root.appendingPathComponent("l10n/claims-policy.de.json")
        try rewriteJSON(policyFile) { object in
            var framing = object["framing"] as? [String] ?? []
            framing[0] += " erkennt Schübe"
            object["framing"] = framing
        }
        let findings = try L10nSourceRegistry.auditFindings(try L10nSourceRegistry.collect(root: root), root: root)
        #expect(findings.contains { $0.contains("claims-policy.de.json") },
                "A forbidden claim planted in the DE framing must be reported against claims-policy.de.json: \(findings)")
    }

    @Test func audit_catchesMutatedGlossaryApprovedForm() throws {
        let root = try copyRepoL10n()
        defer { try? FileManager.default.removeItem(at: root) }
        try rewriteJSON(root.appendingPathComponent("l10n/glossary.json")) { object in
            var concepts = object["concepts"] as? [[String: Any]] ?? []
            var first = concepts[0]
            var de = first["de"] as? [String: Any] ?? [:]
            var approved = de["approved"] as? [String] ?? []
            approved[0] = "erkennt Schübe"
            de["approved"] = approved
            first["de"] = de
            concepts[0] = first
            object["concepts"] = concepts
        }
        let findings = try L10nSourceRegistry.auditFindings(try L10nSourceRegistry.collect(root: root), root: root)
        #expect(findings.contains { $0.contains("glossary.json") && $0.contains("erkennt Schübe") },
                "A forbidden claim planted as the first DE approved form must be reported against glossary.json: \(findings)")
    }

    @Test func audit_catchesRegisterBreak() throws {
        // The register half of the same helper: a formal DE "Sie" mid-sentence is a finding even though
        // the claims policy itself has no rule for it.
        let strings = [L10nString(lang: .de, text: "Das können Sie später ändern.", origin: "seeded:register")]
        let findings = try L10nSourceRegistry.auditFindings(strings)
        #expect(findings.contains { $0.contains("register/Sie") },
                "A mid-sentence formal 'Sie' must be a register finding: \(findings)")
    }

    // MARK: Helpers

    /// A temp root holding a byte copy of the repo `l10n/` folder.
    private func copyRepoL10n() throws -> URL {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("l10n-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try FileManager.default.copyItem(at: try L10nSourceRegistry.repoRoot().appendingPathComponent("l10n"),
                                         to: root.appendingPathComponent("l10n"))
        return root
    }

    private func rewriteJSON(_ url: URL, _ transform: (inout [String: Any]) -> Void) throws {
        var object = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        transform(&object)
        try JSONSerialization.data(withJSONObject: object).write(to: url)
    }
}
