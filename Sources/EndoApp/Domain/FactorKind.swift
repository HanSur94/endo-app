// Requirement: LIFE-01/02/03 — the discriminator for the unified FactorReading model (D-01, D-02).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI`. This keeps the
// vocabulary portable to the future insights engine (Phases 5–8) and unit-testable without a store.
//
// Backing convention: persisted as the optional `kindRaw: String?` on `SchemaV1.FactorReading`
// (mirrors the `typeRaw`/`flowRaw` optional-backing-string convention, CloudKit-safe).
//
// Copy posture (D-21 / Pitfall 5): each `plainLabel` is a short, non-diagnostic descriptive noun
// phrase audited against `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens` by CopyAuditTests.
// Notably it must NOT contain the verb "trigger" — neutral framing only.
//
// Robustness (T-03-02): `init(raw:)` decodes defensively — an unknown/corrupt raw maps to `.other`,
// never crashes the decode.
import Foundation

/// The kind of lifestyle/context factor a `FactorReading` carries (D-01). String-backed for
/// CloudKit-safe optional persistence.
enum FactorKind: String, CaseIterable, Sendable {
    case food
    case activity
    case sleep
    case medication
    case stress
    case mood
    case other

    /// Short, plain-language, non-diagnostic label. Audited for forbidden verbs/gendered tokens by
    /// CopyAuditTests (no "trigger"/"causes"/"will").
    var plainLabel: String {
        switch self {
        case .food:       return "Food & drink"
        case .activity:   return "Activity"
        case .sleep:      return "Sleep"
        case .medication: return "Medication"
        case .stress:     return "Stress"
        case .mood:       return "Mood"
        case .other:      return "Something else"
        }
    }

    /// Defensive decode (T-03-02): an unknown or `nil` raw maps to `.other` rather than crashing.
    init(raw: String?) {
        if let raw, let value = FactorKind(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}
