// Requirement: LIFE-03 / CHCK-02 — the small user-maintained saved medication list (D-06).
//
// CloudKit-safe model (mirrors SymptomReading's CloudKit-safety rules):
//   - Every stored property optional OR defaulted; NO `@Attribute(.unique)`; app-generated `id` UUID.
//   - Nested under `extension SchemaV1` (A1 discipline — never top-level).
//
// This is a DEFINITION entity, NOT an append-only event. The per-day "taken" log lives in a
// `.medication` `FactorReading` whose `valueRaw` is this med's `id.uuidString`.
//
// SOFT-ARCHIVE, NEVER HARD-DELETE (D-06 / A3): archiving sets `isArchived = true` so the med drops
// out of the active list, but prior `.medication` readings keyed by its id stay valid — days you
// already logged it as taken are not changed.
import Foundation
import SwiftData

extension SchemaV1 {

    /// A saved medication definition (D-06). Soft-archived via `isArchived`, never hard-deleted, so
    /// prior per-day "taken" history keyed by `id` remains valid.
    @Model
    final class Medication {
        /// App-generated stable identifier. NOT `.unique` (CloudKit-safe). Keys the per-day readings.
        var id: UUID = UUID()
        /// Optional medication name (bounded at the Repository seam).
        var name: String?
        /// Optional free-text note/type (bounded at the Repository seam).
        var note: String?
        /// When the med was added. Defaulted (CloudKit rule). Drives the saved-list sort order.
        var createdAt: Date = Date()
        /// Soft-archive flag. `nil`/`false` = active; `true` = archived (hidden, history preserved).
        var isArchived: Bool? = nil

        init(id: UUID = UUID(), name: String? = nil, note: String? = nil,
             createdAt: Date = .now, isArchived: Bool? = nil) {
            self.id = id
            self.name = name
            self.note = note
            self.createdAt = createdAt
            self.isArchived = isArchived
        }
    }
}
