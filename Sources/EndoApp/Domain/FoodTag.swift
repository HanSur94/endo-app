// Requirement: LIFE-01 — multi-select endo-relevant food/drink categories (D-03).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Food (LIFE-01) is curated category TAGS + optional free-text — NOT a macro/calorie diary (D-03).
// Built for symptom *correlation* + adherence; HealthKit nutrition import (Phase 9) adds depth later.
//
// Backing convention: each selected tag persists as one `.food` `FactorReading` whose `valueRaw` is
// the tag's `rawValue` (many per day); free-text rides on a separate `.food` reading's `note`.
//
// Copy posture (D-21): labels are neutral nouns — they MUST NOT use the verb "trigger"
// (ClaimsPolicy.forbiddenVerbs); these are "tags", not "triggers".
//
// Robustness (T-03-02): `init(raw:)` decodes defensively — an unknown/corrupt raw maps to `.other`.
import Foundation

/// The curated endo-relevant food/drink categories (D-03). String-backed, CloudKit-safe.
enum FoodTag: String, CaseIterable, Sendable {
    case dairy
    case glutenWheat
    case caffeine
    case alcohol
    case highSugar
    case highFODMAP
    case redProcessedMeat
    case largeMeal
    case other

    /// Short, plain-language, non-diagnostic label. Audited by CopyAuditTests (no "trigger").
    var plainLabel: String {
        switch self {
        case .dairy:            return "Dairy"
        case .glutenWheat:      return "Gluten / wheat"
        case .caffeine:         return "Caffeine"
        case .alcohol:          return "Alcohol"
        case .highSugar:        return "High sugar"
        case .highFODMAP:       return "High FODMAP"
        case .redProcessedMeat: return "Red / processed meat"
        case .largeMeal:        return "Large meal"
        case .other:            return "Something else"
        }
    }

    /// Defensive decode (T-03-02): an unknown or `nil` raw maps to `.other`, never crashes.
    init(raw: String?) {
        if let raw, let value = FoodTag(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}
