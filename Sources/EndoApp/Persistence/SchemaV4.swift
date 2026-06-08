// Requirement: INST-04 / D-10 — extend the CloudKit-safe VersionedSchema with an ADDITIVE SchemaV4.
//
// SchemaV4 lists ALL models in the V4 world: the unchanged V3 set (DayEntry, BleedingEvent,
// TreatmentState, SymptomReading, PainPoint, FactorReading, Medication) PLUS the ONE new model
// (DismissedInsight). All are referenced as `SchemaV1.<Name>` — see the A1 note below.
//
// A1 entity-identity decision (the single most important thing to get right): the @Model classes stay
// defined under `extension SchemaV1 { @Model final class … }`. SwiftData keys entity identity on the
// class/ENTITY NAME (e.g. "DismissedInsight"), NOT the Swift enum namespace the VersionedSchema
// provides. Listing the SAME classes under a new VersionedSchema with only an additive change (one new
// model) is therefore a LIGHTWEIGHT-compatible additive migration. SchemaMigrationTests (V3→V4) proves
// V3 data survives the reopen and DismissedInsight is queryable.
import Foundation
import SwiftData

/// The V4 versioned schema (D-10). Purely additive over SchemaV3: one new model (DismissedInsight)
/// for INST-04 durable insight dismissal.
enum SchemaV4: VersionedSchema {
    // `let` (immutable Sendable static) under the Swift 6 language mode, mirroring SchemaV1/V2/V3.
    static let versionIdentifier = Schema.Version(4, 0, 0)

    static var models: [any PersistentModel.Type] {
        // ALL models in the V4 world: the V3 set + one new (DismissedInsight). Referenced as the same
        // `SchemaV1.<Name>` classes (stable entity names) — additive, lightweight-compatible.
        [SchemaV1.DayEntry.self,
         SchemaV1.BleedingEvent.self,
         SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self,
         SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self,
         SchemaV1.Medication.self,
         SchemaV1.DismissedInsight.self]
    }
}
