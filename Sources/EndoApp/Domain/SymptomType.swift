// Requirement: SYMP-01 — the curated, endo-specific symptom catalog (D-01).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI`. This keeps the
// vocabulary portable to the future insights engine and unit-testable without a store (D-12 seam).
//
// Backing convention: persisted as an optional `typeRaw: String?` on `SchemaV2.SymptomReading`
// (mirrors the existing `flowRaw`/`modeRaw` optional-backing-string convention, CloudKit-safe).
//
// Copy posture (D-14 / Pitfall 5): each `plainLabel` is a short, non-diagnostic descriptive noun
// phrase audited against `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens` by CopyAuditTests.
//
// Robustness (T-02-03): `init(raw:)` decodes defensively — an unknown/corrupt raw maps to `.other`,
// never crashes the decode.
import Foundation

/// The endo-specific symptom catalog (D-01). String-backed for CloudKit-safe optional persistence.
enum SymptomType: String, CaseIterable, Sendable {
    case pelvicPain
    case dysmenorrhea
    case dyspareunia
    case giEndoBelly
    case urinary
    case fatigue
    case brainFog
    case lowMood
    case headache
    case backLegPain
    case other

    /// Short, plain-language, non-diagnostic label (D-01). Audited for forbidden verbs/gendered
    /// tokens by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .pelvicPain:  return "Pelvic / abdominal pain"
        case .dysmenorrhea: return "Pain during bleeding"
        case .dyspareunia: return "Pain during intimacy"
        case .giEndoBelly: return "Bloating / nausea / gut"
        case .urinary:     return "Urinary discomfort"
        case .fatigue:     return "Fatigue"
        case .brainFog:    return "Brain fog"
        case .lowMood:     return "Low mood"
        case .headache:    return "Headache"
        case .backLegPain: return "Back / leg pain"
        case .other:       return "Something else"
        }
    }

    /// Defensive decode (T-02-03): an unknown or `nil` raw maps to `.other` rather than crashing.
    /// `.other` is the safe sink because the catalog already carries a free-text "other" bucket.
    init(raw: String?) {
        if let raw, let value = SymptomType(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}
