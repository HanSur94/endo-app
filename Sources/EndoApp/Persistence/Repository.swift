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

/// A Sendable, SwiftData-free value snapshot of a calendar day's bleeding (D-12 seam).
///
/// This is the value type Plan 03's pure `CycleMath` and Plan 04's cycle summary consume — it
/// deliberately carries NO `@Model` reference so it can cross actor/concurrency boundaries and be
/// reasoned about without a store. `hadFlow` is `true` when the day has a flow-bearing
/// `BleedingEvent` (a recorded `FlowIntensity`).
struct BleedingDay: Sendable, Equatable {
    /// The calendar day (start-of-day) this snapshot represents.
    var day: Date
    /// Whether the day carries a flow-bearing bleeding event.
    var hadFlow: Bool
}

/// The persistence seam (D-22). Minimal CRUD the walking skeleton needs; grows in later phases.
///
/// Phase 2 (D-12) extends this additively with the symptom / pain-point / bleeding / note / day
/// CRUD the day-log editor writes through, plus the Sendable value-snapshot reads (`bleedingDays`,
/// `isSuppressed`) the derived-cycle math (Plan 03) and cycle summary (Plan 04) consume. Every
/// method MUST NOT be called before consent (ONBD-01); the concrete impl asserts it (V4).
protocol Repository {
    /// Persist a new day entry. MUST NOT be called before consent (ONBD-01).
    func add(_ entry: SchemaV1.DayEntry) throws
    /// Fetch all persisted day entries. MUST NOT be called before consent (ONBD-01).
    func allDayEntries() throws -> [SchemaV1.DayEntry]

    // MARK: Phase 2 — day-log write seam (D-12). Severity clamped 0...10; note bounded; consent-gated.

    /// Insert or update the day's reading for `type` (one reading per (day, type)). Severity is
    /// clamped to `0...10` at the seam (V5 input validation).
    func upsertSymptom(type: SymptomType, severity: Int, on day: Date) throws
    /// Remove the day's reading for `type`, if any.
    func removeSymptom(type: SymptomType, on day: Date) throws
    /// Insert or update the day's pain point for `region` (consumed by Plan 04's body map).
    /// Severity clamped `0...10`.
    func setPainPoint(region: BodyRegion, severity: Int, on day: Date) throws
    /// Remove the day's pain point for `region`, if any (an unmarked body-map region).
    func removePainPoint(region: BodyRegion, on day: Date) throws
    /// Upsert (when `intensity != nil`) or remove (when `intensity == nil`) the day's bleeding event.
    func setBleeding(intensity: FlowIntensity?, clots: Bool, on day: Date) throws
    /// Set the day's free-text note on its `DayEntry` (created if absent). Bounded to `maxNoteLength`;
    /// empty -> nil (T-01-05 pattern, T-02-07 DoS guard).
    func setNote(_ text: String?, on day: Date) throws
    /// Delete a whole day's log — symptoms, pain points, bleeding, and the `DayEntry` (Plan 04).
    func deleteDay(_ day: Date) throws

    // MARK: Phase 2 — value-snapshot reads (D-12). Consumed by Plan 03 CycleMath / Plan 04 summary.

    /// One `BleedingDay` per calendar day that has a flow-bearing bleeding event, ascending by day.
    func bleedingDays() throws -> [BleedingDay]
    /// `true` when the latest `TreatmentState` is "suppressed" (CYCL-03 no-cycle mode).
    func isSuppressed() throws -> Bool
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

    // MARK: - Phase 2 write seam (D-12)

    /// Free-text day-note length bound (T-02-07 DoS guard). Mirrors the
    /// `TerminologyStore.maxDisplayNameLength` pattern: a plain local string, no injection surface.
    /// A day note is a short jot, not a journal — bounded well under a paragraph so a pathological
    /// 500-char paste is truncated at the seam (the plan's behavior example).
    static let maxNoteLength = 280

    func upsertSymptom(type: SymptomType, severity: Int, on day: Date) throws {
        assertConsented()
        let clamped = Self.clampSeverity(severity)
        let readings = try symptomReadings(on: day)
        if let existing = readings.first(where: { $0.typeRaw == type.rawValue }) {
            existing.severity = clamped
        } else {
            context.insert(SchemaV1.SymptomReading(
                occurredOn: Calendar.current.startOfDay(for: day),
                typeRaw: type.rawValue,
                severity: clamped))
        }
        try context.save()
    }

    func removeSymptom(type: SymptomType, on day: Date) throws {
        assertConsented()
        for reading in try symptomReadings(on: day) where reading.typeRaw == type.rawValue {
            context.delete(reading)
        }
        try context.save()
    }

    func setPainPoint(region: BodyRegion, severity: Int, on day: Date) throws {
        assertConsented()
        let clamped = Self.clampSeverity(severity)
        let points = try painPoints(on: day)
        if let existing = points.first(where: { $0.regionRaw == region.rawValue }) {
            existing.severity = clamped
        } else {
            context.insert(SchemaV1.PainPoint(
                occurredOn: Calendar.current.startOfDay(for: day),
                regionRaw: region.rawValue,
                severity: clamped))
        }
        try context.save()
    }

    func removePainPoint(region: BodyRegion, on day: Date) throws {
        assertConsented()
        for point in try painPoints(on: day) where point.regionRaw == region.rawValue {
            context.delete(point)
        }
        try context.save()
    }

    func setBleeding(intensity: FlowIntensity?, clots: Bool, on day: Date) throws {
        assertConsented()
        let existing = try bleedingEvents(on: day)
        guard let intensity else {
            // Clear: remove every bleeding record for the day (no phantom record left behind).
            for event in existing { context.delete(event) }
            try context.save()
            return
        }
        if let first = existing.first {
            first.flowRaw = intensity.rawValue
            first.clots = clots
            // Collapse any accidental duplicates onto a single record for the day.
            for extra in existing.dropFirst() { context.delete(extra) }
        } else {
            context.insert(SchemaV1.BleedingEvent(
                occurredAt: Calendar.current.startOfDay(for: day),
                flowRaw: intensity.rawValue,
                clots: clots))
        }
        try context.save()
    }

    func setNote(_ text: String?, on day: Date) throws {
        assertConsented()
        // Bound the string at the seam (T-02-07) and collapse empty -> nil (T-01-05 pattern).
        let bounded: String? = {
            guard let text else { return nil }
            let clipped = String(text.prefix(Self.maxNoteLength))
            return clipped.isEmpty ? nil : clipped
        }()
        let entry = try dayEntry(on: day) ?? {
            let created = SchemaV1.DayEntry(date: Calendar.current.startOfDay(for: day))
            context.insert(created)
            return created
        }()
        entry.note = bounded
        try context.save()
    }

    func deleteDay(_ day: Date) throws {
        assertConsented()
        for reading in try symptomReadings(on: day) { context.delete(reading) }
        for point in try painPoints(on: day) { context.delete(point) }
        for event in try bleedingEvents(on: day) { context.delete(event) }
        for entry in try dayEntries(on: day) { context.delete(entry) }
        try context.save()
    }

    // MARK: - Phase 2 value-snapshot reads (D-12)

    func bleedingDays() throws -> [BleedingDay] {
        assertConsented()
        let cal = Calendar.current
        let events = try context.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        // A day "had flow" when it carries a decodable FlowIntensity. Bucket by start-of-day and
        // map @Model rows -> the Sendable BleedingDay value snapshot (keep the result SwiftData-free).
        var byDay: [Date: Bool] = [:]
        for event in events where FlowIntensity(raw: event.flowRaw) != nil {
            let key = cal.startOfDay(for: event.occurredAt)
            byDay[key] = true
        }
        return byDay.keys.sorted().map { BleedingDay(day: $0, hadFlow: byDay[$0] ?? false) }
    }

    func isSuppressed() throws -> Bool {
        assertConsented()
        let states = try context.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        guard let latest = states.max(by: { $0.startedAt < $1.startedAt }) else { return false }
        return latest.modeRaw == "suppressed"
    }

    // MARK: - Day-bucketing helpers (Calendar-based; no epoch math — research §Don't Hand-Roll)

    private static func clampSeverity(_ value: Int) -> Int { min(10, max(0, value)) }

    /// All symptom readings recorded on `day` (consumed by the editor preload + edit/delete tests).
    func symptomReadings(on day: Date) throws -> [SchemaV1.SymptomReading] {
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
            .filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
    }

    /// All pain points recorded on `day` (consumed by the body-map preload + SYMP-02 binding tests).
    func painPoints(on day: Date) throws -> [SchemaV1.PainPoint] {
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.PainPoint>())
            .filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
    }

    private func bleedingEvents(on day: Date) throws -> [SchemaV1.BleedingEvent] {
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
            .filter { cal.isDate($0.occurredAt, inSameDayAs: day) }
    }

    private func dayEntries(on day: Date) throws -> [SchemaV1.DayEntry] {
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.DayEntry>())
            .filter { cal.isDate($0.date, inSameDayAs: day) }
    }

    private func dayEntry(on day: Date) throws -> SchemaV1.DayEntry? {
        try dayEntries(on: day).first
    }

    /// Debug-only defence-in-depth: the repository must never be reached before consent (ONBD-01).
    private func assertConsented() {
        assert(consentGuard(), "Repository accessed before consent — health-data processing is gated by AppGate (ONBD-01).")
    }
}
