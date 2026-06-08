// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema.
//
// D-18 (append-only event) + D-19 (bleeding is FIRST-CLASS, independent of any "cycle").
//
// CloudKit-safety rules enforced here (D-17, append-only after any future CloudKit push):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — CloudKit has no cross-device uniqueness; identity is the
//     app-generated `id` UUID (D-20), used for idempotent upserts.
//   - No relationships on this model; were any added, they would be optional WITH an explicit
//     inverse and never use a `.deny` delete rule.
//   - NO cycle field of any kind (no `cycleDay`/`cyclePhase`/`cycleLength`, no `= 28` default).
//     Cycle-day/phase is derived/optional/confidence-scored elsewhere — never a stored column
//     on a bleeding record (D-19, the #1 foundational pitfall guard).
import Foundation
import SwiftData

extension SchemaV1 {

    /// A single, timestamped bleeding observation (append-only event record, D-18).
    ///
    /// First-class and independent of any cycle (D-19): a bleeding event stands on its own and is
    /// never derived from, or required by, a cycle model.
    @Model
    final class BleedingEvent {
        /// App-generated stable identifier (D-20). NOT `.unique` (CloudKit-safe, D-17).
        var id: UUID = UUID()
        /// When the bleeding was observed. Defaulted (CloudKit rule).
        var occurredAt: Date = Date()
        /// Optional raw flow level (enum-backing string, kept optional for CloudKit-safety).
        var flowRaw: String?
        /// Optional free-text note.
        var note: String?
        /// SchemaV2 additive growth (CYCL-01, D-07): an optional "clots" flag. Optional + defaulted
        /// (`nil`) so the change is lightweight-migration-safe and CloudKit-safe; absence (`nil`) is
        /// "not recorded", NOT a recorded `false` (honesty principle — absence ≠ fact).
        var clots: Bool? = nil

        init(id: UUID = UUID(), occurredAt: Date = .now, flowRaw: String? = nil,
             note: String? = nil, clots: Bool? = nil) {
            self.id = id
            self.occurredAt = occurredAt
            self.flowRaw = flowRaw
            self.note = note
            self.clots = clots
        }
    }
}
