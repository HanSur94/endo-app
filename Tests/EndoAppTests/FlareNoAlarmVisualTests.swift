// Requirement: D-07 / T-12-07 (Phase 12, checker WR-4) — the "a warning that must not alarm" structural guard.
//
// The flare early-warning's single most subtle contract is that it is rendered as CALM as any other
// insight: time-sensitivity is conveyed by POSITION (the section placed first) ONLY — NEVER by an alarm
// affordance. Plan 01 already locked the COPY side of this (the alarmist forbidden-term scan over
// `FlareCopy.auditableStrings`). This test locks the VISUAL side: it mirrors the `FlareNoNotificationTests`
// `#filePath` source-scan, pointed at the dashboard view files that render the flare section
// (`Sources/EndoApp/Features/Insights/InsightsView.swift` — which adds the `insights.section.flare` section
// — AND `InsightCardView.swift`, which renders the `.flare` card). It strips comment lines and asserts NO
// alarm-visual affordance appears in the rendering code: no `.red` / `Color.red` / `.orange` alarm tint, no
// `exclamationmark` / `bell` / `flame` / generic `warning` SF Symbol, no badge. A future edit that paints
// the flare path red or hangs an alarm glyph on it therefore FAILS THE BUILD.
//
// Two anti-vacuity guards (so the gate can never pass for the wrong reason):
//   1. comment lines are stripped first (a header sentence naming a token cannot self-invalidate it),
//   2. the scanned file set is asserted NON-EMPTY *and* the InsightsView source is asserted to actually
//      contain the `insights.section.flare` anchor — so the scan is provably running over the real flare
//      rendering code, not silently passing by scanning the wrong/empty file.
import Testing
import Foundation

@Suite("D-07 / T-12-07 flare dashboard no-alarm-visual invariant")
struct FlareNoAlarmVisualTests {

    @Test func flareDashboardRenderingHasNoAlarmAffordance() throws {
        // .../Tests/EndoAppTests/FlareNoAlarmVisualTests.swift → project root → the dashboard view files.
        let projectRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoAppTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // <project root>

        // The two view files that render the flare section + the flare card.
        let candidatePaths = [
            "Sources/EndoApp/Features/Insights/InsightsView.swift",   // adds the insights.section.flare section
            "Sources/EndoApp/Features/Insights/InsightCardView.swift", // renders the .flare card branch
        ]
        let files = candidatePaths
            .map { projectRoot.appendingPathComponent($0) }
            .filter { $0.pathExtension == "swift" && FileManager.default.fileExists(atPath: $0.path) }

        // Anti-vacuity guard 2a: the scanned file set must be NON-EMPTY.
        #expect(!files.isEmpty,
                "No-alarm-visual scan found no flare dashboard sources to scan — expected at least one of \(candidatePaths).")

        // Anti-vacuity guard 2b: InsightsView must actually contain the flare-section anchor, proving the
        // scan is running over the real flare rendering code (not silently passing over an unrelated file).
        let insightsViewURL = projectRoot.appendingPathComponent("Sources/EndoApp/Features/Insights/InsightsView.swift")
        let insightsViewText = try String(contentsOf: insightsViewURL, encoding: .utf8)
        #expect(insightsViewText.contains("insights.section.flare"),
                "InsightsView must add the insights.section.flare section — the scan anchors on it to prove it is scanning the flare path.")

        // The forbidden alarm-VISUAL tokens (case-insensitive substring). Any one in non-comment rendering
        // code fails the build — the calm-non-alarmist contract that survives future edits (D-07/T-12-07):
        //   • colour alarm tints (.red / Color.red / .orange) — the flare card must read like any trend card,
        //   • alarm SF Symbols (exclamationmark / bell / flame / a generic "warning" glyph),
        //   • a badge affordance.
        let forbidden = [
            ".red",
            "color.red",
            ".orange",
            "color.orange",
            "exclamationmark",
            "systemimage: \"bell",
            "systemname: \"bell",
            "flame",
            "systemimage: \"warning",
            "systemname: \"warning",
            ".badge",
        ]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                // Anti-vacuity guard 1: strip comment lines so a header sentence naming a token cannot trip
                // — or falsely satisfy — the gate.
                guard !line.hasPrefix("//") else { continue }
                let lower = line.lowercased()
                for token in forbidden {
                    #expect(!lower.contains(token),
                            "Forbidden alarm-visual token \(token) in \(file.lastPathComponent) — the flare warning must be rendered as CALM as any insight (no red/orange tint, no exclamation/bell/flame glyph, no badge); time-sensitivity is conveyed by POSITION only (D-07 / T-12-07).")
                }
            }
        }
    }
}
