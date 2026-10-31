// EndoAppTests — the registered DE/FR/ES string sources (SAFE-01/SAFE-03, SC4;
// `l10n/README.md` §Registering a new string source).
//
// Mirror counterpart: `android/app/src/test/kotlin/com/endoapp/app/l10n/L10nSourceRegistry.kt`.
// THE LOAD-BEARING RULE: every localized file in the repo is REGISTERED here IN THE SAME CHANGE that
// introduces it. Registration IS the enforcement — an unregistered string silently escapes the audit.
// Later phases add one source line each (27 insight messages, 29 Android values-*, 30 xcstrings,
// 33 store copy), never a new mechanism.
import Foundation
@testable import EndoApp

/// One localized string to audit: its language, the text as shipped, and the origin that names the
/// repo-relative file plus the location inside it.
struct L10nString: Equatable, Sendable {
    let lang: PolicyLanguage
    let text: String
    let origin: String
}

/// COPY sources are extracted and audited. DOCUMENTATION sources are claimed (so the tripwire accepts
/// them) but exempt, with a reason.
enum SourceRole: Sendable {
    case copy, documentation
}

/// A registered source. `pathPattern` matches repo-relative paths with `/` separators; `extract` is only
/// called for COPY sources.
struct L10nSource: Sendable {
    let pathPattern: String
    let role: SourceRole
    let reason: String
    let extract: @Sendable (URL) throws -> [L10nString]
}

enum L10nSourceRegistry {

    /// Directory names never walked: VCS and tool state, planning notes, build output. A directory below
    /// the root that holds its own `.git` (nested worktree or checkout) is skipped too.
    static let excludedDirs: Set<String> = [
        ".git", ".planning", ".claude", "build", ".gradle", ".kotlin", ".idea", "DerivedData", ".build",
        ".swiftpm", "node_modules",
    ]

    private static let tags = "de|fr|es"

    private static let policyFile = #"l10n/claims-policy\.(de|fr|es)\.json"#
    private static let glossaryPath = "l10n/glossary.json"

    /// Every registered source. A new localized file needs a line here in the same change.
    static let sources: [L10nSource] = [
        L10nSource(
            pathPattern: policyFile,
            role: .copy,
            reason: "Only the `framing` entries are copy (approved disclaimer strings). The rules, allow-list "
                + "and selfTest fixtures are policy data that legitimately CONTAIN forbidden terms, the same "
                + "exemption the English ForbiddenTerms/ClaimsPolicy lists have.",
            extract: extractPolicyFraming
        ),
        L10nSource(
            pathPattern: #"l10n/glossary\.json"#,
            role: .copy,
            reason: "Every de/fr/es `approved` form is copy and must pass its policy and the register. The "
                + "`banned` lists and selfTest pairs are fixtures that CONTAIN the forms they ban. The "
                + "`doNotTranslate` brand names (Health Connect, Endo-App) are kept verbatim in every language, "
                + "so they are not DE/FR/ES text and are not extracted.",
            extract: extractGlossaryApproved
        ),
        L10nSource(
            pathPattern: #"l10n/README\.md"#,
            role: .documentation,
            reason: "Translator documentation and the normative matching spec. It is never shipped, and it "
                + "quotes forbidden terms on purpose to explain the rules.",
            extract: { _ in [] }
        ),
    ]

    /// Android resource types that can carry a locale qualifier.
    private static let androidResTypes =
        "values|drawable|mipmap|layout|raw|xml|menu|font|anim|animator|color|navigation|transition|interpolator"

    /// An Android resource directory with a de/fr/es qualifier: `values-de`, `values-es-rUS`,
    /// `values-night-fr`, BCP-47 `values-b+es+419`.
    private static let androidResDir = #"(\#(androidResTypes))(-[A-Za-z0-9]+)*-(de|fr|es)(-r[A-Z]{2})?(-[A-Za-z0-9]+)*|(\#(androidResTypes))(-[A-Za-z0-9]+)*-(b\+(de|fr|es)(\+[A-Za-z0-9]+)*)(-[A-Za-z0-9]+)*"#

    /// An Apple localization directory: `de.lproj`, `fr-CA.lproj`, `es_419.lproj`.
    private static let lprojDir = #"(de|fr|es)([-_][A-Za-z0-9]+)*\.lproj"#

    /// A store-metadata locale directory: `de-DE`, `fr-FR`, `es-ES`, `es-419`.
    private static let regionDir = #"(de|fr|es)-([A-Z]{2}|419)"#

    /// A file name tagged with a language: `store-copy.es.txt`, `release-fr.md`.
    private static let taggedFile = #".+[.-](de|fr|es)\.[^/]+"#

    /// The source claiming `relPath` (repo-relative, `/` separators), or nil when none does.
    static func claim(_ relPath: String) -> L10nSource? {
        sources.first { wholeMatch($0.pathPattern, relPath) }
    }

    /// Whole-string match of `pattern` against `text` (Foundation regex, no `Regex` value to store).
    static func wholeMatch(_ pattern: String, _ text: String) -> Bool {
        guard let range = text.range(of: pattern, options: [.regularExpression]) else { return false }
        return range.lowerBound == text.startIndex && range.upperBound == text.endIndex
    }

    /// Every string extracted from the COPY sources found under `root`.
    static func collect(root: URL) throws -> [L10nString] {
        try repoFiles(root: root).flatMap { rel -> [L10nString] in
            guard let source = claim(rel), source.role == .copy else { return [] }
            return try source.extract(root.appendingPathComponent(rel))
        }
    }

    /// The repo root from `#filePath`, three levels up, proven by `l10n/README.md`. Missing means FAIL.
    static func repoRoot() throws -> URL {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // Tests/EndoAppTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // repo root
        let readme = root.appendingPathComponent("l10n/README.md")
        guard FileManager.default.fileExists(atPath: readme.path) else {
            throw L10nRegistryError.message("no l10n/README.md at the resolved repo root \(root.path)")
        }
        return root
    }

    /// The strictly decoded glossary under `root`. Missing means FAIL, never skip.
    static func glossary(root: URL) throws -> Glossary {
        let file = root.appendingPathComponent(glossaryPath)
        guard let data = FileManager.default.contents(atPath: file.path) else {
            throw L10nRegistryError.message("\(glossaryPath) is missing under '\(root.path)' (fail, never skip).")
        }
        return try Glossary.decode(data)
    }

    /// THE audit helper. Every real scan and every red-capable control goes through it, so a control that
    /// turns red proves the real scan would too. Returns one line per policy hit and per register hit; the
    /// register comes from the glossary under the SAME root the strings were collected from.
    static func auditFindings(_ strings: [L10nString], root: URL? = nil) throws -> [String] {
        let rootURL: URL
        if let root { rootURL = root } else { rootURL = try repoRoot() }
        let glossary = try glossary(root: rootURL)
        return strings.flatMap { string -> [String] in
            let policyHit = ClaimsPolicy.forLanguage(string.lang).violation(string.text)
            let registerHit = glossary.registerViolation(string.lang, string.text)
            return [policyHit, registerHit].compactMap { $0 }.map { violation in
                "\(string.origin): \"\(string.text)\" → \(violation.category)/\(violation.term)"
            }
        }
    }

    /// True when `relPath` looks like a localized file.
    static func looksLocalized(_ relPath: String) -> Bool {
        let segments = relPath.split(separator: "/").map(String.init)
        guard let name = segments.last else { return false }
        let dirs = segments.dropLast()
        if relPath.hasPrefix("l10n/") { return true }
        if name.hasSuffix(".xcstrings") { return true }
        if wholeMatch(taggedFile, name) { return true }
        return dirs.contains { dir in
            wholeMatch(androidResDir, dir) || wholeMatch(lprojDir, dir) || wholeMatch(regionDir, dir)
        }
    }

    /// Every localized-looking file under `root`, walked top-down with the exclusions.
    static func localizedCandidates(root: URL) throws -> [String] {
        try repoFiles(root: root).filter(looksLocalized)
    }

    /// The localized-looking files under `root` that no registered source claims.
    static func unregisteredLocalizedFiles(root: URL) throws -> [String] {
        try localizedCandidates(root: root).filter { claim($0) == nil }
    }

    /// Repo-relative paths (`/` separators) of every file under `root`, skipping `excludedDirs`, any
    /// nested checkout below the root, and generated Xcode project bundles.
    static func repoFiles(root: URL) throws -> [String] {
        var out: [String] = []
        let manager = FileManager.default

        func walk(_ dir: URL, _ prefix: String) {
            let children = (try? manager.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.isDirectoryKey],
                                                            options: [.skipsHiddenFiles])) ?? []
            for child in children.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) {
                let name = child.lastPathComponent
                let rel = prefix.isEmpty ? name : "\(prefix)/\(name)"
                let isDirectory = (try? child.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory ?? false
                if isDirectory {
                    let nestedCheckout = manager.fileExists(atPath: child.appendingPathComponent(".git").path)
                    let generatedProject = name.hasSuffix(".xcodeproj") || name.hasSuffix(".xcworkspace")
                    if excludedDirs.contains(name) || nestedCheckout || generatedProject { continue }
                    walk(child, rel)
                } else {
                    out.append(rel)
                }
            }
        }

        walk(root, "")
        return out
    }

    // MARK: Extractors

    private static func extractPolicyFraming(_ url: URL) throws -> [L10nString] {
        let name = url.lastPathComponent
        let tag = url.deletingPathExtension().lastPathComponent.split(separator: ".").last.map(String.init)
        guard let tag, let language = try? PolicyLanguage.fromTag(tag) else {
            throw L10nRegistryError.message("Policy file name '\(name)' carries no de/fr/es tag.")
        }
        guard let data = FileManager.default.contents(atPath: url.path),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let framing = object["framing"] as? [String] else {
            throw L10nRegistryError.message("policy file \(name) has no framing array")
        }
        return framing.enumerated().map { index, entry in
            L10nString(lang: language, text: entry, origin: "l10n/\(name)#framing[\(index)]")
        }
    }

    private static func extractGlossaryApproved(_ url: URL) throws -> [L10nString] {
        guard let data = FileManager.default.contents(atPath: url.path) else {
            throw L10nRegistryError.message("glossary.json is missing at \(url.path)")
        }
        let glossary = try Glossary.decode(data)
        return glossary.concepts.flatMap { concept in
            PolicyLanguage.allCases.flatMap { language in
                concept.forLanguage(language).approved.map { form in
                    L10nString(lang: language, text: form,
                               origin: "\(glossaryPath)#\(concept.id).\(language.tag)")
                }
            }
        }
    }
}

enum L10nRegistryError: Error, CustomStringConvertible {
    case message(String)
    var description: String {
        switch self { case .message(let text): return text }
    }
}
