// Requirement: LIFE-02 — physical activity logging: type + duration + perceived intensity (D-04).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Activity (LIFE-02) is a small TYPE set + duration (minutes) + intensity (low/med/high). HealthKit
// workouts import later (Phase 9). This enum is just the type axis; minutes + intensity ride
// alongside on the same `.activity` FactorReading.
//
// Copy posture (D-21): neutral, non-diagnostic labels audited by CopyAuditTests.
//
// Robustness (T-03-02): `init(raw:)` decodes defensively — an unknown/corrupt raw maps to `.other`.
import Foundation

/// The small curated set of activity types (D-04). String-backed, CloudKit-safe.
enum ActivityType: String, CaseIterable, Sendable {
    case walking
    case cardio
    case strength
    case yogaStretching
    case other

    /// Short, plain-language, non-diagnostic label. Audited by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .walking:        return "Walking"
        case .cardio:         return "Cardio"
        case .strength:       return "Strength"
        case .yogaStretching: return "Yoga / stretching"
        case .other:          return "Something else"
        }
    }

    /// Defensive decode (T-03-02): an unknown or `nil` raw maps to `.other`, never crashes.
    init(raw: String?) {
        if let raw, let value = ActivityType(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}
