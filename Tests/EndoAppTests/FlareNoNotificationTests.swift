// Requirement: SC2 / D-06 (Phase 12, T-12-03) — the no-speculative-push source-scan invariant.
//
// The flare early-warning's defining delivery guarantee is that it is an IN-APP insight ONLY — the flare
// module must NEVER reach the OS notification / push system (no speculative flare alert, ever, SC2). To
// keep that guarantee STRUCTURALLY true — not just true today — this test mirrors `ReportNoNetworkTests`'
// `#filePath` source-scan, but pointed at the two flare source files: the package producer
// `EndoInsights/Sources/EndoInsights/FlareEngine.swift` AND the app copy surface
// `Sources/EndoApp/Features/Insights/FlareCopy.swift`. It resolves both relative to this file, scans every
// EXISTING `*.swift` among them, strips comment lines, and asserts NO file references any notification /
// push API. A future `UNUserNotification`/`NotificationScheduler`/`scheduleReminder` call in the flare path
// therefore FAILS THE BUILD.
//
// Two anti-vacuity guards (so the gate can never pass for the wrong reason):
//   1. comment lines are stripped first (a header sentence naming a token cannot self-invalidate it),
//   2. the scanned file set is asserted NON-EMPTY — which makes this test RED NOW (the two flare files do
//      not exist until Plan 02 ships them) and GREEN once Plan 02 creates them. The forbidden token set is
//      LOCKED here, before any producer exists, so SC2 is proven by test FIRST.
import Testing
import Foundation

@Suite("SC2 / D-06 flare module no-notification invariant")
struct FlareNoNotificationTests {

    @Test func flareModuleReferencesNoNotificationAPI() throws {
        // .../Tests/EndoAppTests/FlareNoNotificationTests.swift → project root → the two flare source files.
        let projectRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoAppTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // <project root>

        // The two flare module files (created by Plan 02). Scan whichever already exist.
        let candidatePaths = [
            "EndoInsights/Sources/EndoInsights/FlareEngine.swift",   // the package producer
            "Sources/EndoApp/Features/Insights/FlareCopy.swift",     // the app copy surface
        ]
        let files = candidatePaths
            .map { projectRoot.appendingPathComponent($0) }
            .filter { $0.pathExtension == "swift" && FileManager.default.fileExists(atPath: $0.path) }

        // Anti-vacuity guard 2: the scanned flare file set must be NON-EMPTY. This is RED until Plan 02
        // ships FlareEngine.swift + FlareCopy.swift, and GREEN once it does — so SC2 cannot pass silently
        // by scanning nothing.
        #expect(!files.isEmpty,
                "No-notification scan found no flare sources to scan — expected at least one of \(candidatePaths) (RED until Plan 02 ships the flare module files).")

        // The forbidden notification / push tokens (RESEARCH Pattern 5, exact LOCKED set). Any one in
        // non-comment code fails the build (the structural SC2 guarantee that survives future edits).
        let forbidden = [
            "UNUserNotification",
            "UNNotification",
            "UNMutableNotificationContent",
            "UNNotificationRequest",
            "UNCalendarNotificationTrigger",
            "UNTimeIntervalNotificationTrigger",
            "UNUserNotificationCenter",
            "UserNotifications",
            "NotificationScheduler",
            "requestAuthorization",
            "scheduleNotification",
            "addNotificationRequest",
            "scheduleReminder",
        ]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                // Anti-vacuity guard 1: strip comment lines (the equivalent of `grep -v` on `//`) so a
                // header sentence naming a token cannot trip — or falsely satisfy — the gate.
                guard !line.hasPrefix("//") else { continue }
                for token in forbidden {
                    #expect(!line.contains(token),
                            "Forbidden notification/push token \(token) in \(file.lastPathComponent) — the flare module must be IN-APP only and never reach the OS notification system (SC2 / D-06 no speculative flare push).")
                }
            }
        }
    }
}
