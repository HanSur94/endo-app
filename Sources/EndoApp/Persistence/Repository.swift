// Requirement: DATA-01 / D-22 — the testable persistence seam.
//
// `Repository` is the protocol the rest of the app talks to instead of touching `ModelContext`
// directly, so persistence can be injected and mocked in later phases. `SwiftDataRepository` is the
// concrete SwiftData-backed implementation.
//
// CONSENT PRECONDITION (ONBD-01, D-02): no health record may be read or written before
// `ConsentState == .consented`. The gate is ENFORCED upstream in `AppGate` (the only path that
// constructs the health-data UI is the `.consented` branch). The repository documents and, in
// debug builds, asserts that precondition as a defence-in-depth check — it must never be called
// pre-consent.
import Foundation
import SwiftData

/// The persistence seam (D-22). Minimal CRUD the walking skeleton needs; grows in later phases.
protocol Repository {
    /// Persist a new day entry. MUST NOT be called before consent (ONBD-01).
    func add(_ entry: SchemaV1.DayEntry) throws
    /// Fetch all persisted day entries. MUST NOT be called before consent (ONBD-01).
    func allDayEntries() throws -> [SchemaV1.DayEntry]
}

/// SwiftData-backed `Repository` over a `ModelContext`.
struct SwiftDataRepository: Repository {
    private let context: ModelContext

    /// - Parameter consentGuard: a closure returning whether consent has been granted. Used only
    ///   for the debug precondition (defence-in-depth); the real gate is `AppGate`. Defaults to
    ///   `true` so call sites that are already inside the consented branch need no extra wiring.
    private let consentGuard: () -> Bool

    init(context: ModelContext, consentGuard: @escaping () -> Bool = { true }) {
        self.context = context
        self.consentGuard = consentGuard
    }

    func add(_ entry: SchemaV1.DayEntry) throws {
        assertConsented()
        context.insert(entry)
        try context.save()
    }

    func allDayEntries() throws -> [SchemaV1.DayEntry] {
        assertConsented()
        return try context.fetch(FetchDescriptor<SchemaV1.DayEntry>())
    }

    /// Debug-only defence-in-depth: the repository must never be reached before consent (ONBD-01).
    private func assertConsented() {
        assert(consentGuard(), "Repository accessed before consent — health-data processing is gated by AppGate (ONBD-01).")
    }
}
