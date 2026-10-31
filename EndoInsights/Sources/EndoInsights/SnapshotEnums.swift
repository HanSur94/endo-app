// EndoInsights — the package-local snapshot-facing enums (Phase 5, Option D / D-18).
//
// Foundation only — NO SwiftData/SwiftUI/UIKit (SC1). These are package-local copies of the app's
// domain enums; the `String` raw values MATCH the app's verbatim so the app-side boundary map
// (Plan 03, `Repository.daySnapshots()`) is a same-raw-value passthrough (app-enum → package-enum).
//
// Why Option D (package owns its own copies) rather than moving the 7 app enums into the package:
// the app enums are referenced across 17 app files; relocating them is the highest-blast-radius
// change in the phase. Self-contained package copies keep SC1 true by construction and leave all
// 17 app files untouched. Drift risk (the Option-B trap) is mitigated by a Plan-03 round-trip test
// asserting every app-enum case maps to a package-enum case with the same raw value.
//
// Data-only: NO `plainLabel` UI copy — the snapshot enums carry data, not display strings. Insight
// copy is descriptive prose the app renders (Phase 8). Each enum carries the SAME defensive
// `init(raw:)` decode as its app counterpart (unknown/nil → `.other` for catalogs with an `.other`
// sink; `nil` for the optional ones) so corrupt stored data never crashes the projection.
import Foundation

/// The endo-specific symptom catalog (mirrors app `SymptomType`, raw values verbatim).
public enum SymptomType: String, CaseIterable, Sendable {
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

    /// Human label for UI surfaces (Explore lane titles). Mirrors the app `SymptomType.plainLabel`
    /// verbatim so a lane never renders a camelCase raw value such as "backLegPain severity".
    public var displayName: String {
        switch self {
        case .pelvicPain:   return "Pelvic / abdominal pain"
        case .dysmenorrhea: return "Pain during bleeding"
        case .dyspareunia:  return "Pain during intimacy"
        case .giEndoBelly:  return "Bloating / nausea / gut"
        case .urinary:      return "Urinary discomfort"
        case .fatigue:      return "Fatigue"
        case .brainFog:     return "Brain fog"
        case .lowMood:      return "Low mood"
        case .headache:     return "Headache"
        case .backLegPain:  return "Back / leg pain"
        case .other:        return "Something else"
        }
    }

    /// Defensive decode: an unknown or `nil` raw maps to `.other` (the free-text sink), never crashes.
    public init(raw: String?) {
        if let raw, let value = SymptomType(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}

/// The curated named body-map regions (mirrors app `BodyRegion`, raw values verbatim).
public enum BodyRegion: String, CaseIterable, Sendable {
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

    /// Human label for UI surfaces; mirrors the app `BodyRegion` label copy verbatim.
    public var displayName: String {
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

    /// Defensive decode: an unknown or `nil` raw returns `nil` (caller: "not marked"), never crashes.
    public init?(raw: String?) {
        guard let raw, let value = BodyRegion(rawValue: raw) else { return nil }
        self = value
    }
}

/// Bleeding/flow intensity (mirrors app `FlowIntensity`, raw values verbatim).
public enum FlowIntensity: String, CaseIterable, Sendable {
    case spotting
    case light
    case medium
    case heavy

    /// Defensive decode: an unknown or `nil` raw returns `nil`, never crashes.
    public init?(raw: String?) {
        guard let raw, let value = FlowIntensity(rawValue: raw) else { return nil }
        self = value
    }
}

/// The curated food/drink category tags (mirrors app `FoodTag`, raw values verbatim).
public enum FoodTag: String, CaseIterable, Sendable {
    case dairy
    case glutenWheat
    case caffeine
    case alcohol
    case highSugar
    case highFODMAP
    case redProcessedMeat
    case largeMeal
    case other

    /// Defensive decode: an unknown or `nil` raw maps to `.other` (the free-text sink), never crashes.
    public init(raw: String?) {
        if let raw, let value = FoodTag(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}

/// The curated activity types (mirrors app `ActivityType`, raw values verbatim).
public enum ActivityType: String, CaseIterable, Sendable {
    case walking
    case cardio
    case strength
    case yogaStretching
    case other

    /// Defensive decode: an unknown or `nil` raw maps to `.other` (the free-text sink), never crashes.
    public init(raw: String?) {
        if let raw, let value = ActivityType(rawValue: raw) {
            self = value
        } else {
            self = .other
        }
    }
}

/// Perceived activity intensity (mirrors app `ActivityIntensity`, raw values verbatim).
public enum ActivityIntensity: String, CaseIterable, Sendable {
    case low
    case medium
    case high

    /// Defensive decode: an unknown or `nil` raw returns `nil`, never crashes.
    public init?(raw: String?) {
        guard let raw, let value = ActivityIntensity(rawValue: raw) else { return nil }
        self = value
    }
}

/// Subjective sleep quality (mirrors app `SleepQuality`, raw values verbatim).
public enum SleepQuality: String, CaseIterable, Sendable {
    case poor
    case okay
    case good

    /// Defensive decode: an unknown or `nil` raw returns `nil`, never crashes.
    public init?(raw: String?) {
        guard let raw, let value = SleepQuality(rawValue: raw) else { return nil }
        self = value
    }
}
