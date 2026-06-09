// Requirement: RPRT-03 (Phase 10, D-09 / T-10-10) — the no-silent-upload source-scan invariant.
//
// The report module's defining privacy guarantee is that the PDF NEVER leaves the device except through
// the user's explicit `ShareLink` tap (a local file URL bound to the system share sheet). To keep that
// guarantee structurally true — not just true today — this test mirrors `BoundaryLintTests`' `#filePath`
// source-scan, but pointed at `Sources/EndoApp/Features/Report/`: it resolves that directory relative to
// this file, scans every `*.swift`, strips comment lines, and asserts NO file contains `URLSession`,
// `URLRequest`, or `dataTask`. A future networking call in the report path therefore FAILS THE BUILD.
//
// Two anti-vacuity guards (so the gate can never pass for the wrong reason):
//   1. comment lines are stripped first (a header sentence mentioning the token cannot self-invalidate it),
//   2. the scanned file set is asserted NON-EMPTY (an empty/relocated directory never passes silently).
import Testing
import Foundation

@Suite("RPRT-03 report module no-network invariant")
struct ReportNoNetworkTests {

    @Test func reportModuleImportsNoNetworking() throws {
        // .../Tests/EndoAppTests/ReportNoNetworkTests.swift → project root → Sources/EndoApp/Features/Report
        let projectRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoAppTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // <project root>
        let reportSources = projectRoot
            .appendingPathComponent("Sources/EndoApp/Features/Report")

        let files = try FileManager.default
            .contentsOfDirectory(at: reportSources, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "swift" }
        // Anti-vacuity guard 2: a relocated/empty Report directory must NOT pass silently.
        #expect(!files.isEmpty,
                "No-network scan found no report sources to scan at \(reportSources.path)")

        // The forbidden no-silent-upload tokens (RPRT-03 / D-09). Any one in non-comment code fails.
        let forbidden = ["URLSession", "URLRequest", "dataTask"]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                // Anti-vacuity guard 1: strip comment lines (the equivalent of `grep -v` on `//`) so a
                // header sentence naming the token cannot trip — or falsely satisfy — the gate.
                guard !line.hasPrefix("//") else { continue }
                for token in forbidden {
                    #expect(!line.contains(token),
                            "Forbidden networking token \(token) in \(file.lastPathComponent) — the report module must never reach the network (RPRT-03 no-silent-upload).")
                }
            }
        }
    }
}
