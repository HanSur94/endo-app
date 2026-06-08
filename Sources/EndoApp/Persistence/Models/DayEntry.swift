// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema.
//
// The per-day anchor record: the simplest @Model that proves the end-to-end persistence loop
// (write one record → read it back). Phase 2's `HomeView` queries it for the day history, and the
// day-log editor writes the day's free-text note onto it.
//
// CloudKit-safety rules enforced here (D-17): every stored property is optional/defaulted, NO
// `@Attribute(.unique)`, identity via an app-generated `id` UUID (D-20). No cycle field of any
// kind (D-19).
import Foundation
import SwiftData

extension SchemaV1 {

    /// A minimal day record used to prove the persistence round-trip (DATA-01).
    @Model
    final class DayEntry {
        /// App-generated stable identifier (D-20). NOT `.unique` (CloudKit-safe, D-17).
        var id: UUID = UUID()
        /// The day this entry refers to. Defaulted (CloudKit rule).
        var date: Date = Date()
        /// Optional free-text note.
        var note: String?

        init(id: UUID = UUID(), date: Date = .now, note: String? = nil) {
            self.id = id
            self.date = date
            self.note = note
        }
    }
}
