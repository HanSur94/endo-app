// Requirement: SYMP-02 — record pain location on a body map with severity (D-04, D-02).
//
// CloudKit-safe, append-only event record (mirrors BleedingEvent/SymptomReading exactly, D-17/D-18):
//   - Every stored property optional/defaulted; NO `@Attribute(.unique)`; identity via app-generated
//     `id` UUID (D-20); no relationships (day linkage by `occurredOn`); NO cycle field (D-19).
//
// A1 nesting decision: nested under `extension SchemaV1 { @Model … }` like the existing V1 models;
// listed additively in SchemaV2.models. Entity identity is the class name, so this is lightweight-safe
// (proven by SchemaMigrationTests).
//
// D-02 honesty: `severity` is optional. A `nil` severity is "unspecified" — never "0 = healthy".
import Foundation
import SwiftData

extension SchemaV1 {

    /// A single, timestamped pain point on the body map (append-only event, D-18).
    @Model
    final class PainPoint {
        /// App-generated stable identifier (D-20). NOT `.unique` (CloudKit-safe, D-17).
        var id: UUID = UUID()
        /// The calendar day this pain point belongs to. Defaulted (CloudKit rule).
        var occurredOn: Date = Date()
        /// Optional `BodyRegion` backing string (kept optional for CloudKit-safety).
        var regionRaw: String?
        /// Optional 0...10 severity (D-02). `nil` = unspecified, NEVER "0 = healthy".
        var severity: Int?

        init(id: UUID = UUID(), occurredOn: Date = .now,
             regionRaw: String? = nil, severity: Int? = nil) {
            self.id = id
            self.occurredOn = occurredOn
            self.regionRaw = regionRaw
            self.severity = severity
        }
    }
}
