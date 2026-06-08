// EndoInsights — the per-day value snapshot contract (Phase 5, D-01/D-03/D-04).
//
// THE read-model the TrendsEngine consumes. Mirrors the app's `FactorDay` in spirit: a flat,
// `Sendable`, Foundation-only value type — NO `import SwiftData`, NO `import SwiftUI` (SC1). It
// crosses actor/concurrency boundaries freely and is unit-testable without a store. The app maps
// SwiftData `@Model` rows → `[DaySnapshot]` app-side at the Repository seam (Plan 03); the package
// never touches the store.
//
// HONESTY CONTRACT (D-03, mirrors `FactorDay`'s "absence ≠ 0"): every optional field is `nil` when
// that signal was NOT logged that day — NEVER coerced to 0/neutral. A `severity` of `nil` means
// "logged-present without a severity", never 0. `flow == nil` means no bleeding logged that day.
// The engine excludes nil from any denominator; it never fabricates a default.
//
// PRIVACY (D-01, Art.9 posture): the snapshot carries `hasNote: Bool` — note PRESENCE only, never
// the free-text prose. No health free-text crosses into the analysis layer.
import Foundation

/// One logged symptom on a day: its catalog type and an optional severity.
///
/// `severity == nil` ⇒ the symptom was logged present without a severity (absence ≠ 0).
public struct SymptomPoint: Sendable, Equatable {
    public let type: SymptomType
    public let severity: Int?

    public init(type: SymptomType, severity: Int?) {
        self.type = type
        self.severity = severity
    }
}

/// One marked pain region on a day: its body region and an optional severity.
///
/// Package-local value type — DISTINCT from the app's `SchemaV1.PainPoint` `@Model` (different
/// module; the app mapping fully-qualifies `EndoInsights.PainPoint`). `severity == nil` ⇒ marked
/// without a severity (absence ≠ 0).
public struct PainPoint: Sendable, Equatable {
    public let region: BodyRegion
    public let severity: Int?

    public init(region: BodyRegion, severity: Int?) {
        self.region = region
        self.severity = severity
    }
}

/// A `Sendable`, Foundation-only value snapshot of one calendar day (D-01).
///
/// One `DaySnapshot` per calendar day that carries any logged signal. Optional fields are `nil` when
/// that signal was NOT logged that day — absence is honest, never a fabricated default (D-03).
public struct DaySnapshot: Sendable, Equatable {
    /// The calendar day (start-of-day) this snapshot represents (the Repository normalizes).
    public let day: Date

    // MARK: Symptoms / pain (SYMP-01 / SYMP-02)
    /// The symptoms logged for the day (may be empty).
    public let symptoms: [SymptomPoint]
    /// The pain regions marked for the day (may be empty).
    public let pain: [PainPoint]

    // MARK: Bleeding (CYCL-01)
    /// Flow intensity for the day. `nil` = no bleeding logged that day (frequency uses `flow != nil`).
    public let flow: FlowIntensity?
    /// Whether clots were logged that day.
    public let clots: Bool

    // MARK: Food (LIFE-01)
    /// The food/drink category tags logged for the day (may be empty).
    public let foodTags: [FoodTag]

    // MARK: Activity (LIFE-02)
    /// The activity type, if logged.
    public let activityType: ActivityType?
    /// Activity duration in minutes, if logged.
    public let activityMinutes: Double?
    /// Perceived activity intensity, if logged.
    public let activityIntensity: ActivityIntensity?

    // MARK: Sleep (LIFE-03)
    /// Sleep duration in hours, if logged.
    public let sleepHours: Double?
    /// Subjective sleep quality, if logged.
    public let sleepQuality: SleepQuality?

    // MARK: Medication (LIFE-03)
    /// The saved-medication ids logged as taken that day.
    public let medicationsTaken: [UUID]
    /// Whether a PRN (as-needed) pain-relief marker was logged that day.
    public let tookPRN: Bool

    // MARK: Context scales (LIFE-03)
    /// Stress on a 0...10 scale. `nil` = NOT logged (absence ≠ 0, D-03).
    public let stress: Int?
    /// Overall day mood on a 1...5 valence scale. `nil` = NOT logged (absence ≠ 0/neutral, D-03).
    public let mood: Int?

    // MARK: Note presence / suppression
    /// Whether the day carried a free-text note — PRESENCE only, never the prose (D-01 privacy).
    public let hasNote: Bool
    /// Per-day suppressed flag (D-03). Under suppression the engine emits NO bleeding/cycle trend (SC3).
    public let suppressed: Bool

    /// Explicit public memberwise init (a package struct needs one for cross-module construction).
    public init(
        day: Date,
        symptoms: [SymptomPoint],
        pain: [PainPoint],
        flow: FlowIntensity?,
        clots: Bool,
        foodTags: [FoodTag],
        activityType: ActivityType?,
        activityMinutes: Double?,
        activityIntensity: ActivityIntensity?,
        sleepHours: Double?,
        sleepQuality: SleepQuality?,
        medicationsTaken: [UUID],
        tookPRN: Bool,
        stress: Int?,
        mood: Int?,
        hasNote: Bool,
        suppressed: Bool
    ) {
        self.day = day
        self.symptoms = symptoms
        self.pain = pain
        self.flow = flow
        self.clots = clots
        self.foodTags = foodTags
        self.activityType = activityType
        self.activityMinutes = activityMinutes
        self.activityIntensity = activityIntensity
        self.sleepHours = sleepHours
        self.sleepQuality = sleepQuality
        self.medicationsTaken = medicationsTaken
        self.tookPRN = tookPRN
        self.stress = stress
        self.mood = mood
        self.hasNote = hasNote
        self.suppressed = suppressed
    }
}
