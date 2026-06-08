// Requirement: CYCL-01 — flow intensity for first-class bleeding events (D-07).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Backing convention: the raw strings MATCH the existing `BleedingEvent.flowRaw` vocabulary
// ("spotting" | "light" | "medium" | "heavy"), so this enum is the typed view over that field.
//
// Robustness (T-02-03): `init(raw:)` returns `nil` for an unknown/corrupt raw, never crashes.
import Foundation

/// Bleeding/flow intensity (D-07). Raw strings match the existing `flowRaw` vocabulary.
enum FlowIntensity: String, CaseIterable, Sendable {
    case spotting
    case light
    case medium
    case heavy

    /// Plain descriptive label. Audited for forbidden verbs/gendered tokens by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .spotting: return "Spotting"
        case .light:    return "Light"
        case .medium:   return "Medium"
        case .heavy:    return "Heavy"
        }
    }

    /// Defensive decode (T-02-03): an unknown or `nil` raw returns `nil`, never crashes.
    init?(raw: String?) {
        guard let raw, let value = FlowIntensity(rawValue: raw) else { return nil }
        self = value
    }
}
