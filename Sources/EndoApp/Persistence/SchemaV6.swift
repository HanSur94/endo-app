// Requirement: DATA-05 / D-01 (Phase 9) — extend the CloudKit-safe VersionedSchema with an ADDITIVE
// SchemaV6.
//
// SchemaV6 lists ALL models in the V6 world: the unchanged V5 set (DayEntry, BleedingEvent,
// TreatmentState, SymptomReading, PainPoint, FactorReading, Medication, DismissedInsight, CachedInsight)
// PLUS the ONE new model (ImportedSample). All are referenced as `SchemaV1.<Name>` — see the A1 note.
//
// A1 entity-identity decision (the single most important thing to get right): the @Model classes stay
// defined under `extension SchemaV1 { @Model final class … }`. SwiftData keys entity identity on the
// class/ENTITY NAME (e.g. "ImportedSample"), NOT the Swift enum namespace the VersionedSchema provides.
// Listing the SAME classes under a new VersionedSchema with only an additive change (one new model) is
// therefore a LIGHTWEIGHT-compatible additive migration. SchemaMigrationTests (V5→V6) proves V5 data
// survives the reopen and ImportedSample is queryable.
import Foundation
import SwiftData

/// The V6 versioned schema (D-01). Purely additive over SchemaV5: one new model (ImportedSample) for
/// DATA-05 durable, CloudKit-safe imported-Apple-Health-sample persistence.
enum SchemaV6: VersionedSchema {
    // `let` (immutable Sendable static) under the Swift 6 language mode, mirroring SchemaV1..V5.
    static let versionIdentifier = Schema.Version(6, 0, 0)

    static var models: [any PersistentModel.Type] {
        // ALL models in the V6 world: the V5 set + one new (ImportedSample). Referenced as the same
        // `SchemaV1.<Name>` classes (stable entity names) — additive, lightweight-compatible.
        [SchemaV1.DayEntry.self,
         SchemaV1.BleedingEvent.self,
         SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self,
         SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self,
         SchemaV1.Medication.self,
         SchemaV1.DismissedInsight.self,
         SchemaV1.CachedInsight.self,
         SchemaV1.ImportedSample.self]
    }
}
