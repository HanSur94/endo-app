// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema.
//
// D-19 — treatment/suppression state is FIRST-CLASS and INDEPENDENT of any "cycle."
//
// This model exists precisely so a user who is hormonally suppressed (e.g. "suppressed, no
// bleeding for 8 months") is representable WITHOUT any phantom cycle field. There is deliberately
// no `cycleDay`/`cyclePhase`/`cycleLength` and no `= 28` default anywhere — suppression is a
// first-class, timestamped state, not the absence of a cycle (the #1 foundational pitfall guard).
//
// CloudKit-safety rules enforced here (D-17): every property optional/defaulted, NO
// `@Attribute(.unique)`, identity via app-generated `id` UUID (D-20).
import Foundation
import SwiftData

extension SchemaV1 {

    /// The user's current treatment / suppression mode as a first-class, timestamped record (D-19).
    ///
    /// `modeRaw` is an optional enum-backing string, e.g. "naturallyCycling" | "suppressed" |
    /// "unknown". It is intentionally NOT tied to a cycle: a "suppressed" state is valid on its own
    /// and coexists with having no `BleedingEvent` for months.
    @Model
    final class TreatmentState {
        /// App-generated stable identifier (D-20). NOT `.unique` (CloudKit-safe, D-17).
        var id: UUID = UUID()
        /// When this state began. Defaulted (CloudKit rule).
        var startedAt: Date = Date()
        /// Optional raw mode, e.g. "naturallyCycling" | "suppressed" | "unknown".
        var modeRaw: String?

        init(id: UUID = UUID(), startedAt: Date = .now, modeRaw: String? = nil) {
            self.id = id
            self.startedAt = startedAt
            self.modeRaw = modeRaw
        }
    }
}
