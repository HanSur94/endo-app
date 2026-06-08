// Requirement: SYMP-02 — named body-map regions for the discrete pain map (D-04).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable + testable).
//
// Backing convention: persisted as an optional `regionRaw: String?` on `SchemaV2.PainPoint`.
//
// Copy posture (D-14 / Pitfall 5): each `plainLabel` is a plain anatomical noun phrase, audited.
//
// Robustness (T-02-03): `init(raw:)` returns `nil` for an unknown/corrupt raw (caller treats nil as
// "not marked"), never crashes the decode.
import Foundation

/// The curated set of named body-map regions relevant to endo (D-04). String-backed.
enum BodyRegion: String, CaseIterable, Sendable {
    case lowerAbdomenPelvis
    case lowerQuadrantLeft
    case lowerQuadrantRight
    case lowerBack
    case hips
    case upperThighs
    case rectalBowel
    case bladder
    case chestDiaphragm
    case shoulder

    /// Plain anatomical name (D-04). Audited for forbidden verbs/gendered tokens by CopyAuditTests.
    var plainLabel: String {
        switch self {
        case .lowerAbdomenPelvis: return "Lower abdomen / pelvis"
        case .lowerQuadrantLeft:  return "Lower left side"
        case .lowerQuadrantRight: return "Lower right side"
        case .lowerBack:          return "Lower back"
        case .hips:               return "Hips"
        case .upperThighs:        return "Upper thighs"
        case .rectalBowel:        return "Rectal / bowel"
        case .bladder:            return "Bladder"
        case .chestDiaphragm:     return "Chest / diaphragm"
        case .shoulder:           return "Shoulder"
        }
    }

    /// Defensive decode (T-02-03): an unknown or `nil` raw returns `nil` (caller: "not marked").
    init?(raw: String?) {
        guard let raw, let value = BodyRegion(rawValue: raw) else { return nil }
        self = value
    }
}
