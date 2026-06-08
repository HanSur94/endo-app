// Requirement: D-10 — extend the CloudKit-safe VersionedSchema with an ADDITIVE SchemaV3.
//
// SchemaV3 lists ALL models in the V3 world: the unchanged V2 set (DayEntry, BleedingEvent,
// TreatmentState, SymptomReading, PainPoint) PLUS the two NEW models (FactorReading, Medication).
// All are referenced as `SchemaV1.<Name>` — see the A1 note below.
//
// A1 entity-identity decision (the single most important thing to get right): the @Model classes stay
// defined under `extension SchemaV1 { @Model final class … }`. SwiftData keys entity identity on the
// class/ENTITY NAME (e.g. "FactorReading"), NOT the Swift enum namespace the VersionedSchema provides.
// Listing the SAME classes under a new VersionedSchema with only additive changes (two new models) is
// therefore a LIGHTWEIGHT-compatible additive migration. SchemaMigrationTests (v2→v3) proves V2 data
// survives the reopen, closing A1 before Phase 4 ever flips CloudKit on.
import Foundation
import SwiftData

/// The V3 versioned schema (D-10). Purely additive over SchemaV2: two new models (FactorReading,
/// Medication) for Phase-3 lifestyle/context factor logging.
enum SchemaV3: VersionedSchema {
    // `let` (immutable Sendable static) under the Swift 6 language mode, mirroring SchemaV1/V2.
    static let versionIdentifier = Schema.Version(3, 0, 0)

    static var models: [any PersistentModel.Type] {
        // ALL models in the V3 world: the V2 set + two new. Referenced as the same `SchemaV1.<Name>`
        // classes (stable entity names) — additive, lightweight-compatible.
        [SchemaV1.DayEntry.self,
         SchemaV1.BleedingEvent.self,
         SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self,
         SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self,
         SchemaV1.Medication.self]
    }
}
