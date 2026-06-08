// Requirement: D-11 — the Sendable, SwiftData-free per-day factor snapshot (the insights seam).
//
// THE most important forward-looking deliverable of Phase 3. This value type is what Phases 5–8
// consume to reason about lifestyle factors WITHOUT touching a `ModelContext` — so it carries NO
// `@Model` reference and can cross actor/concurrency boundaries. It mirrors `BleedingDay`
// (Persistence/Repository.swift) exactly in spirit: fetch @Model rows, decode defensively, project
// into a plain Sendable struct.
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI`.
//
// HONESTY CONTRACT (D-07 / D-08 / Pitfall "absence ≠ 0"): `stress` and `mood` are `Int?`. A day with
// NO stress reading reads back `stress == nil` — it is NEVER coerced to 0. A logged stress of 0 reads
// back `stress == 0`. The same applies to `mood` (1...5). The factorDays() projection in the
// Repository is unit-proven against this contract in FactorSnapshotTests.
import Foundation

/// A Sendable, SwiftData-free value snapshot of one calendar day's lifestyle factors (D-11).
///
/// One `FactorDay` per calendar day that carries any factor reading. Optional fields are `nil` when
/// that factor was NOT logged that day — absence is honest, never a fabricated default.
struct FactorDay: Sendable, Equatable {
    /// The calendar day (start-of-day) this snapshot represents.
    var day: Date

    // MARK: Food (LIFE-01)
    /// The food/drink category tags logged for the day (may be empty).
    var foodTags: [FoodTag]
    /// Optional free-text food note (bounded at the Repository seam; empty ⇒ nil).
    var foodNote: String?

    // MARK: Activity (LIFE-02)
    /// The activity type, if logged.
    var activityType: ActivityType?
    /// Activity duration in minutes, if logged.
    var activityMinutes: Double?
    /// Perceived activity intensity, if logged.
    var activityIntensity: ActivityIntensity?

    // MARK: Sleep (LIFE-03)
    /// Sleep duration in hours, if logged.
    var sleepHours: Double?
    /// Subjective sleep quality, if logged.
    var sleepQuality: SleepQuality?

    // MARK: Medication (LIFE-03 / D-06)
    /// The saved-medication ids logged as taken that day.
    var medicationsTaken: [UUID]
    /// Whether a PRN (as-needed) pain-relief marker was logged that day.
    var tookPRN: Bool

    // MARK: Context scales (LIFE-03)
    /// Stress on a 0...10 scale. `nil` = NOT logged (absence ≠ 0, D-07).
    var stress: Int?
    /// Overall day mood on a 1...5 valence scale. `nil` = NOT logged (absence ≠ 0/neutral, D-08).
    var mood: Int?
}
