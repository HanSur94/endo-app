// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema.
//
// D-16 — SwiftData with a `VersionedSchema` from v1, so a migration path exists before it is ever
// needed. The concrete `@Model` types are nested under `SchemaV1` (see Models/*.swift) so they are
// referenced as `SchemaV1.DayEntry`, `SchemaV1.BleedingEvent`, `SchemaV1.TreatmentState` — versioned
// from day one.
//
// Schema-shape rules (D-17, hard and append-only after any future CloudKit push):
//   - every stored property optional or defaulted; NO `@Attribute(.unique)`;
//   - any relationship optional WITH an explicit inverse, never `.deny` (none required for these
//     three models, but the rule is documented here and in each model);
//   - bleeding + treatment/suppression state are FIRST-CLASS and independent of any cycle (D-19);
//   - cycle-day/phase is derived/optional/confidence-scored — NEVER a required stored column.
import Foundation
import SwiftData

/// The versioned SwiftData schema (D-16). All persisted `@Model` types are listed in `models` and
/// declared as nested types in the `Models/` folder.
enum SchemaV1: VersionedSchema {
    // `let` (not `var`) so the static is immutable Sendable state under the Swift 6 language mode
    // (a `static var` here is flagged as nonisolated global shared mutable state).
    static let versionIdentifier = Schema.Version(1, 0, 0)

    static var models: [any PersistentModel.Type] {
        [BleedingEvent.self, TreatmentState.self, DayEntry.self]
    }
}
