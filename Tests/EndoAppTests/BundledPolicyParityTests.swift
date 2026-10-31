// EndoAppTests — the bundled policy bytes must equal the reviewed repo bytes (SAFE-01/SAFE-02, Pitfall 8).
//
// Mirror counterpart: `android/insights/src/test/kotlin/com/endoapp/insights/l10n/BundledPolicyParityTests.kt`.
// A stale bundle after a JSON or project.yml change is a real failure here, never a skip (T-26-22).
// The repo root resolves from #filePath with a FIXED depth (3 × deletingLastPathComponent) because this
// worktree is nested inside the main checkout — walking up parents would find the wrong l10n/ (Pitfall 6).
import Foundation
import Testing
@testable import EndoApp

@Suite("SAFE-01/02 bundled policy parity")
struct BundledPolicyParityTests {

    /// `<repo>/l10n`, proven by requiring README.md to exist there.
    private func repoRoot() throws -> URL {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // Tests/EndoAppTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // repo root
        let readme = root.appendingPathComponent("l10n/README.md")
        try #require(FileManager.default.fileExists(atPath: readme.path),
                     "no l10n/README.md at the resolved repo root \(root.path)")
        return root
    }

    @Test(arguments: PolicyLanguage.allCases)
    func bundleBytesEqualRepoBytes(_ language: PolicyLanguage) throws {
        let url = try #require(ClaimsPolicy.bundledPolicyURL(for: language),
                               "claims-policy.\(language.tag).json is not in the app bundle — re-run xcodegen generate")
        let repoFile = try repoRoot().appendingPathComponent("l10n/claims-policy.\(language.tag).json")
        let repoData = try #require(FileManager.default.contents(atPath: repoFile.path),
                                    "no repo file at \(repoFile.path)")

        let bundledData = try Data(contentsOf: url)
        #expect(bundledData == repoData,
                "[\(language.tag)] bundled policy differs from \(repoFile.path) — re-run xcodegen generate")

        let bundledPolicy = try LanguageClaimsPolicy.decode(bundledData)
        let repoPolicy = try LanguageClaimsPolicy.decode(repoData)
        #expect(bundledPolicy.policyRevision == repoPolicy.policyRevision,
                "[\(language.tag)] bundled policyRevision \(bundledPolicy.policyRevision) != repo \(repoPolicy.policyRevision)")
    }

    @Test func bundleCarriesOnlyPolicyFiles() {
        let bundle = Bundle(for: L10nBundleAnchor.self)
        let jsonNames = Set((bundle.urls(forResourcesWithExtension: "json", subdirectory: nil) ?? [])
            .map(\.lastPathComponent))
        let expected = Set(PolicyLanguage.allCases.map { "claims-policy.\($0.tag).json" })
        #expect(expected.isSubset(of: jsonNames), "missing bundled policies; bundle has \(jsonNames.sorted())")
        #expect(!jsonNames.contains("glossary.json"), "glossary.json is test-only and must not ship")
        #expect(!jsonNames.contains("README.md"), "l10n/README.md is documentation and must not ship")
    }
}
