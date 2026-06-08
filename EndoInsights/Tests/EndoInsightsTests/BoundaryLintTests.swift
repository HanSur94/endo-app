// EndoInsights — the D-12 import-boundary lint (Phase 5, SC1; RESEARCH Form A).
//
// Belt-and-suspenders on the COMPILER boundary (the package's zero-dep `Package.swift` already makes a
// SwiftData/SwiftUI import un-linkable). This test resolves `Sources/EndoInsights` relative to
// `#filePath`, scans every `*.swift`, and asserts that every `import X` line imports ONLY Foundation —
// failing on SwiftData/SwiftUI/UIKit/CoreData/AppKit. It runs inside `xcodebuild test -scheme EndoApp`
// so the boundary is proven in the suite, not just by a CI grep (Form B).
//
// Mitigates T-05-04 (a future SwiftData/SwiftUI import in any package source).
import Testing
import Foundation

@Suite("D-12 package import boundary")
struct BoundaryLintTests {

    @Test func packageSourcesImportOnlyFoundation() throws {
        // .../EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift → .../EndoInsights/
        let packageRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoInsightsTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // EndoInsights/ (package root)
        let sources = packageRoot.appendingPathComponent("Sources/EndoInsights")

        let files = try FileManager.default
            .contentsOfDirectory(at: sources, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "swift" }
        #expect(!files.isEmpty, "Boundary lint found no package sources to scan at \(sources.path)")

        let forbidden: Set<String> = ["SwiftData", "SwiftUI", "UIKit", "CoreData", "AppKit"]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for rawLine in text.split(separator: "\n") {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                guard line.hasPrefix("import ") else { continue }
                let module = line
                    .replacingOccurrences(of: "import ", with: "")
                    .split(separator: " ").first.map(String.init) ?? ""
                #expect(!forbidden.contains(module),
                        "Forbidden import \(module) in \(file.lastPathComponent) — SC1 boundary violated")
                #expect(module == "Foundation",
                        "Unexpected import \(module) in \(file.lastPathComponent) — package sources must import only Foundation")
            }
        }
    }
}
