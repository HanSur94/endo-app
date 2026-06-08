// Requirement: INST-04 (D-09/D-10) — durable, CloudKit-safe persistence of a DISMISSED insight.
//
// PRIVACY BOUNDARY (security threat T-06-09): this record carries ONLY a non-identifying,
// engine-generated category `signalKey` (e.g. `cyclePhase.luteal.symptom.pelvicPain`) + a timestamp.
// It holds NO raw health prose, NO symptom text, NO severities. It is a category label, not content.
// When CloudKit is active (Phase 4) only these two fields cross into the user's PRIVATE iCloud DB.
//
// CloudKit-safe, append-only event record (mirrors FactorReading's conventions exactly, D-10):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — identity is the app-generated `id` UUID (idempotent across devices).
//   - No relationships — strictly additive, avoids CloudKit relationship traps.
//
// A1 nesting decision: nested under `extension SchemaV1 { @Model … }` exactly like the existing
// models. SwiftData keys entity identity on the class/entity NAME, not the enum namespace, so listing
// this same class additively under SchemaV4.models is a lightweight-compatible change.
// SchemaMigrationTests (V3→V4) is the gate that proves it.
//
// APPEND-ONLY (D-10/D-12): a dismissal is recorded as a new row; duplicates across devices collapse
// harmlessly on READ via a `Set` (Repository.dismissedSignalKeys), never via a write-side mutation.
import Foundation
import SwiftData

extension SchemaV1 {

    /// A single durable record that the user dismissed the insight identified by `signalKey`
    /// (append-only, INST-04 / D-09). Carries ONLY the stable signalKey + a timestamp — no prose.
    @Model
    final class DismissedInsight {
        /// App-generated stable identifier. NOT `.unique` (CloudKit-safe).
        var id: UUID = UUID()
        /// The STABLE dismissal key (D-09): an engine-generated, value-independent category label
        /// (e.g. `cyclePhase.luteal.symptom.pelvicPain`) — NOT user free-text, NO health prose.
        /// Optional per the CloudKit all-optional/defaulted rule.
        var signalKey: String? = nil
        /// When the dismissal happened. Defaulted (CloudKit rule).
        var dismissedAt: Date = Date()

        init(id: UUID = UUID(), signalKey: String? = nil, dismissedAt: Date = .now) {
            self.id = id
            self.signalKey = signalKey
            self.dismissedAt = dismissedAt
        }
    }
}
