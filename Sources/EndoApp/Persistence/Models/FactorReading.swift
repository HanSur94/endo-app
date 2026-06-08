// Requirement: LIFE-01/02/03 — the ONE unified, append-only lifestyle-factor record (D-01).
//
// CloudKit-safe, append-only event record (mirrors SymptomReading's conventions exactly, D-01):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — identity is the app-generated `id` UUID, idempotent upsert.
//   - No relationships (day linkage is by `occurredOn` calendar day) — strictly additive, avoids
//     CloudKit relationship traps.
//   - NO cycle field of any kind.
//
// A1 nesting decision: nested under `extension SchemaV1 { @Model … }` exactly like the existing
// models. SwiftData keys entity identity on the class/entity NAME, not the enum namespace, so listing
// this same class additively under SchemaV3.models is a lightweight-compatible change.
// SchemaMigrationTests (v2→v3) is the gate that proves it.
//
// ─────────────────────────────────────────────────────────────────────────────────────────────────
// KIND → COLUMN MAPPING (D-01, locked). One model discriminated by `kindRaw` (FactorKind):
//
//   food (LIFE-01)       valueRaw = FoodTag.rawValue          (MANY per day, one row per tag)
//                        note     = free-text                 (ONE extra .food row holds free-text)
//   activity (LIFE-02)   valueRaw = ActivityType.rawValue     (ONE per day)
//                        amount   = duration in minutes
//                        level    = ActivityIntensity 0/1/2 (low/medium/high)
//   sleep (LIFE-03)      valueRaw = SleepQuality.rawValue      (optional; ONE per day)
//                        amount   = duration in hours
//   medication (LIFE-03) valueRaw = saved-med id.uuidString   (ONE per taken med)
//                        valueRaw = "prn"                      (the PRN pain-relief marker)
//   stress (LIFE-03)     level    = 0...10                     (ONE per day)
//   mood (LIFE-03)       level    = 1...5                      (ONE per day)
//
// HONESTY RULE (D-07/D-08): absence ≠ 0. A day with no `.stress` row has NO stress value at all; a
// `.stress` row with `level == 0` means stress was logged AS zero. Never coerce nil → 0.
// ─────────────────────────────────────────────────────────────────────────────────────────────────
import Foundation
import SwiftData

extension SchemaV1 {

    /// A single, day-anchored lifestyle-factor reading (append-only event, D-01). The `kindRaw`
    /// discriminator + the generic payload columns encode every factor kind per the mapping above.
    @Model
    final class FactorReading {
        /// App-generated stable identifier. NOT `.unique` (CloudKit-safe).
        var id: UUID = UUID()
        /// The calendar day this reading belongs to. Defaulted (CloudKit rule).
        var occurredOn: Date = Date()
        /// Optional `FactorKind` backing string (kept optional for CloudKit-safety).
        var kindRaw: String?
        /// Optional category/type/id backing string (FoodTag / ActivityType / SleepQuality / med-id / "prn").
        var valueRaw: String?
        /// Optional numeric amount: activity minutes, sleep hours.
        var amount: Double?
        /// Optional integer scale: activity intensity 0/1/2, stress 0...10, mood 1...5.
        var level: Int?
        /// Optional free-text note (food free-text). Bounded at the Repository seam.
        var note: String?

        init(id: UUID = UUID(), occurredOn: Date = .now,
             kindRaw: String? = nil, valueRaw: String? = nil,
             amount: Double? = nil, level: Int? = nil, note: String? = nil) {
            self.id = id
            self.occurredOn = occurredOn
            self.kindRaw = kindRaw
            self.valueRaw = valueRaw
            self.amount = amount
            self.level = level
            self.note = note
        }
    }
}
