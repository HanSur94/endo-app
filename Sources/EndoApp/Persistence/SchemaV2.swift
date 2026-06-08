// Requirement: D-11 — extend the CloudKit-safe VersionedSchema with an ADDITIVE SchemaV2.
//
// SchemaV2 lists ALL models in the V2 world: the unchanged V1 models (DayEntry, TreatmentState),
// the additively-grown BleedingEvent (now carries an optional `clots`), and the two NEW models
// (SymptomReading, PainPoint). All are referenced as `SchemaV1.<Name>` — see the A1 note below.
//
// A1 entity-identity decision (RESEARCH §Assumptions Log, the single most important thing to get
// right): the @Model classes stay defined under `extension SchemaV1 { @Model final class … }`.
// SwiftData keys entity identity on the class/ENTITY NAME (e.g. "BleedingEvent"), NOT the Swift enum
// namespace that the VersionedSchema enum provides. Listing the SAME classes under a new
// VersionedSchema with only additive changes (new optional field, new models) is therefore a
// LIGHTWEIGHT-compatible additive migration. SchemaMigrationTests proves V1 data survives the
// V1→V2 reopen, closing A1 before Phase 4 ever flips CloudKit on.
import Foundation
import SwiftData

/// The V2 versioned schema (D-11). Purely additive over SchemaV1: BleedingEvent gains an optional
/// `clots` flag and two new models (SymptomReading, PainPoint) are introduced.
enum SchemaV2: VersionedSchema {
    // `let` (immutable Sendable static) under the Swift 6 language mode, mirroring SchemaV1.
    static let versionIdentifier = Schema.Version(2, 0, 0)

    static var models: [any PersistentModel.Type] {
        // ALL models in the V2 world: unchanged + additively grown + new. Referenced as the same
        // `SchemaV1.<Name>` classes (stable entity names) — additive, lightweight-compatible.
        [SchemaV1.DayEntry.self,
         SchemaV1.BleedingEvent.self,
         SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self,
         SchemaV1.PainPoint.self]
    }
}
