// Requirement: LIFE-03 — sleep quality (D-05).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Sleep (LIFE-03) is duration (hours) + quality (poor/ok/good); both feed insights. HealthKit sleep
// import later (Phase 9). This enum is the quality axis; hours ride on the same `.sleep` reading.
//
// Mirrors `FlowIntensity`'s failable-decode pattern: an unset quality is "not logged" ⇒ `nil`.
//
// Robustness (T-03-02): `init?(raw:)` returns `nil` for an unknown/corrupt raw, never crashes.
import Foundation

/// Subjective sleep quality (D-05). String-backed, CloudKit-safe; text-labelled.
enum SleepQuality: String, CaseIterable, Sendable {
    case poor
    case okay
    case good

    /// Plain descriptive label. Audited for forbidden verbs/gendered tokens by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .poor: return "Poor"
        case .okay: return "Okay"
        case .good: return "Good"
        }
    }

    /// Defensive decode (T-03-02): an unknown or `nil` raw returns `nil`, never crashes.
    init?(raw: String?) {
        guard let raw, let value = SleepQuality(rawValue: raw) else { return nil }
        self = value
    }
}
