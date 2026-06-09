// Requirement: D-02/D-14 (Phase 8) — extend the CloudKit-safe VersionedSchema with an ADDITIVE SchemaV5.
//
// SchemaV5 lists ALL models in the V5 world: the unchanged V4 set (DayEntry, BleedingEvent,
// TreatmentState, SymptomReading, PainPoint, FactorReading, Medication, DismissedInsight) PLUS the ONE
// new model (CachedInsight). All are referenced as `SchemaV1.<Name>` — see the A1 note below.
//
// A1 entity-identity decision (the single most important thing to get right): the @Model classes stay
// defined under `extension SchemaV1 { @Model final class … }`. SwiftData keys entity identity on the
// class/ENTITY NAME (e.g. "CachedInsight"), NOT the Swift enum namespace the VersionedSchema provides.
// Listing the SAME classes under a new VersionedSchema with only an additive change (one new model) is
// therefore a LIGHTWEIGHT-compatible additive migration. SchemaMigrationTests (V4→V5) proves V4 data
// survives the reopen and CachedInsight is queryable.
import Foundation
import SwiftData

/// The V5 versioned schema (D-14). Purely additive over SchemaV4: one new model (CachedInsight) for
/// D-02 durable, CloudKit-safe cached-insight persistence.
enum SchemaV5: VersionedSchema {
    // `let` (immutable Sendable static) under the Swift 6 language mode, mirroring SchemaV1..V4.
    static let versionIdentifier = Schema.Version(5, 0, 0)

    static var models: [any PersistentModel.Type] {
        // ALL models in the V5 world: the V4 set + one new (CachedInsight). Referenced as the same
        // `SchemaV1.<Name>` classes (stable entity names) — additive, lightweight-compatible.
        [SchemaV1.DayEntry.self,
         SchemaV1.BleedingEvent.self,
         SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self,
         SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self,
         SchemaV1.Medication.self,
         SchemaV1.DismissedInsight.self,
         SchemaV1.CachedInsight.self]
    }
}
