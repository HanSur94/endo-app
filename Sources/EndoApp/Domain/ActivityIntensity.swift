// Requirement: LIFE-02 — perceived activity intensity (D-04).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Mirrors `FlowIntensity`'s failable-decode pattern: an UNSET intensity is "not logged", so the
// defensive decode returns `nil` (NOT a coerced default). Persisted as the `level` Int on the
// `.activity` FactorReading (0 = low, 1 = medium, 2 = high) — see FactorReading's mapping doc-comment.
//
// UI-SPEC: intensity is ALWAYS text-labelled, never color-only.
//
// Robustness (T-03-02): `init?(raw:)` returns `nil` for an unknown/corrupt raw, never crashes.
import Foundation

/// Perceived activity intensity (D-04). String-backed, CloudKit-safe; text-labelled (never color-only).
enum ActivityIntensity: String, CaseIterable, Sendable {
    case low
    case medium
    case high

    /// Plain descriptive label. Audited for forbidden verbs/gendered tokens by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .low:    return "Low"
        case .medium: return "Medium"
        case .high:   return "High"
        }
    }

    /// Defensive decode (T-03-02): an unknown or `nil` raw returns `nil`, never crashes.
    init?(raw: String?) {
        guard let raw, let value = ActivityIntensity(rawValue: raw) else { return nil }
        self = value
    }
}
