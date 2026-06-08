// Requirement: SYMP-01 — log endo-specific symptoms with severity (D-01, D-02).
//
// CloudKit-safe, append-only event record (mirrors BleedingEvent's conventions exactly, D-17/D-18):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — identity is the app-generated `id` UUID (D-20), idempotent upsert.
//   - No relationships (day linkage is by `occurredOn` calendar day) — keeps it strictly additive and
//     avoids `.deny`/ordered-relationship CloudKit traps (RESEARCH Pattern 4).
//   - NO cycle field of any kind (no `cycleDay`/`cyclePhase`/`cycleLength`, no `= 28`) — D-19.
//
// A1 nesting decision: this class is nested under `extension SchemaV1 { @Model … }` exactly like the
// existing V1 models. SwiftData keys entity identity on the class/entity NAME, not the enum
// namespace, so listing this same class under SchemaV2.models is a lightweight-compatible ADDITIVE
// change. SchemaMigrationTests is the gate that proves it (closes A1 in RESEARCH §Assumptions Log).
//
// D-02 honesty: `severity` is optional. A `nil` severity is "unspecified" — it is NEVER "0 = healthy".
import Foundation
import SwiftData

extension SchemaV1 {

    /// A single, timestamped symptom reading (append-only event, D-18). One per (day, symptom).
    @Model
    final class SymptomReading {
        /// App-generated stable identifier (D-20). NOT `.unique` (CloudKit-safe, D-17).
        var id: UUID = UUID()
        /// The calendar day this reading belongs to. Defaulted (CloudKit rule).
        var occurredOn: Date = Date()
        /// Optional `SymptomType` backing string (kept optional for CloudKit-safety).
        var typeRaw: String?
        /// Optional 0...10 severity (D-02). `nil` = unspecified, NEVER "0 = healthy".
        var severity: Int?
        /// Optional free-text note.
        var note: String?

        init(id: UUID = UUID(), occurredOn: Date = .now,
             typeRaw: String? = nil, severity: Int? = nil, note: String? = nil) {
            self.id = id
            self.occurredOn = occurredOn
            self.typeRaw = typeRaw
            self.severity = severity
            self.note = note
        }
    }
}
