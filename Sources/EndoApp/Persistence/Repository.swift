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
    /// Write the user's suppressed / no-cycle mode (CYCL-03, D-09). `true` -> "suppressed",
    /// `false` -> "naturallyCycling". A new first-class timestamped `TreatmentState` (append-only).
    func setSuppressed(_ suppressed: Bool) throws

    // MARK: Phase 3 — factor write seam (D-11). Calendar-bucketed; free-text bounded; consent-gated.

    /// Set the day's food tags (LIFE-01). Replaces the day's existing `.food` readings: one row per
    /// tag, plus one note-bearing `.food` row when `freeText` is non-empty (bounded to `maxNoteLength`).
    func setFood(tags: [FoodTag], freeText: String?, on day: Date) throws
    /// Upsert (or clear, when `type == nil`) the day's single `.activity` reading (LIFE-02).
    func setActivity(type: ActivityType?, minutes: Double?, intensity: ActivityIntensity?, on day: Date) throws
    /// Upsert (or clear, when both `hours` and `quality` are nil) the day's single `.sleep` reading (LIFE-03).
    func setSleep(hours: Double?, quality: SleepQuality?, on day: Date) throws
    /// Insert (taken) or remove (not taken) a `.medication` reading keyed by `medID.uuidString` (D-06).
    func setMedicationTaken(medID: UUID, taken: Bool, on day: Date) throws
    /// Insert (took) or remove (didn't) the day's PRN pain-relief marker (`valueRaw == "prn"`).
    func setPRN(_ took: Bool, on day: Date) throws
    /// Set the day's stress level 0...10 (D-07). `nil` CLEARS — absence ≠ 0. Non-nil clamps 0...10.
    func setStress(_ level: Int?, on day: Date) throws
    /// Set the day's mood 1...5 (D-08). `nil` CLEARS — absence ≠ neutral. Non-nil clamps 1...5.
    func setMood(_ level: Int?, on day: Date) throws
    /// Ensure a `DayEntry` anchor exists for the day WITHOUT inventing symptom/factor data (D-15).
    func checkInMarker(on day: Date) throws

    // MARK: Phase 3 — saved-medication CRUD (D-06). Soft-archive only, never hard-delete.

    /// Add a saved medication; returns its new id. Both strings bounded to `maxNoteLength`.
    @discardableResult
    func addMedication(name: String?, note: String?) throws -> UUID
    /// Soft-archive a saved medication (sets `isArchived = true`). Prior `.medication` readings keyed
    /// by its id remain valid — history is never changed (D-06).
    func archiveMedication(_ id: UUID) throws
    /// The non-archived saved medications, sorted by `createdAt` ascending.
    func savedMedications() throws -> [SchemaV1.Medication]

    // MARK: Phase 3 — factor value-snapshot reads (D-11). Consumed by Phases 5–8 insights.

    /// All `FactorReading`s recorded on `day` (the per-day bucket helper, mirrors `symptomReadings`).
    func factorReadings(on day: Date) throws -> [SchemaV1.FactorReading]
    /// One `FactorDay` per calendar day with any factor reading, ascending by day. Defensive decode;
    /// honest nil≠0 for stress/mood (D-07/D-08).
    func factorDays() throws -> [FactorDay]
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

    func setSuppressed(_ suppressed: Bool) throws {
        assertConsented()
        // Append a new first-class, timestamped TreatmentState (D-19). `isSuppressed()` reads the
        // latest by `startedAt`, so the newest write wins without mutating prior history.
        context.insert(SchemaV1.TreatmentState(
            startedAt: .now,
            modeRaw: suppressed ? "suppressed" : "naturallyCycling"))
        try context.save()
    }

    // MARK: - Phase 3 factor write seam (D-11)

    func setFood(tags: [FoodTag], freeText: String?, on day: Date) throws {
        assertConsented()
        let anchor = Calendar.current.startOfDay(for: day)
        // Replace the day's `.food` readings wholesale (tags + free-text are a single edit unit).
        for existing in try factorReadings(on: day) where existing.kindRaw == FactorKind.food.rawValue {
            context.delete(existing)
        }
        for tag in tags {
            context.insert(SchemaV1.FactorReading(
                occurredOn: anchor, kindRaw: FactorKind.food.rawValue, valueRaw: tag.rawValue))
        }
        if let bounded = Self.boundedNote(freeText) {
            context.insert(SchemaV1.FactorReading(
                occurredOn: anchor, kindRaw: FactorKind.food.rawValue, note: bounded))
        }
        try context.save()
    }

    func setActivity(type: ActivityType?, minutes: Double?, intensity: ActivityIntensity?, on day: Date) throws {
        assertConsented()
        let existing = try factorReadings(on: day).filter { $0.kindRaw == FactorKind.activity.rawValue }
        guard let type else {
            // Clear: a nil type means "no activity logged" — remove the day's `.activity` reading(s).
            for reading in existing { context.delete(reading) }
            try context.save()
            return
        }
        let level = intensity.map { Self.intensityLevel($0) }
        if let first = existing.first {
            first.valueRaw = type.rawValue
            first.amount = minutes
            first.level = level
            for extra in existing.dropFirst() { context.delete(extra) }
        } else {
            context.insert(SchemaV1.FactorReading(
                occurredOn: Calendar.current.startOfDay(for: day),
                kindRaw: FactorKind.activity.rawValue,
                valueRaw: type.rawValue, amount: minutes, level: level))
        }
        try context.save()
    }

    func setSleep(hours: Double?, quality: SleepQuality?, on day: Date) throws {
        assertConsented()
        let existing = try factorReadings(on: day).filter { $0.kindRaw == FactorKind.sleep.rawValue }
        guard hours != nil || quality != nil else {
            // Clear: both nil means "not logged" — remove the day's `.sleep` reading(s).
            for reading in existing { context.delete(reading) }
            try context.save()
            return
        }
        if let first = existing.first {
            first.amount = hours
            first.valueRaw = quality?.rawValue
            for extra in existing.dropFirst() { context.delete(extra) }
        } else {
            context.insert(SchemaV1.FactorReading(
                occurredOn: Calendar.current.startOfDay(for: day),
                kindRaw: FactorKind.sleep.rawValue,
                valueRaw: quality?.rawValue, amount: hours))
        }
        try context.save()
    }

    func setMedicationTaken(medID: UUID, taken: Bool, on day: Date) throws {
        assertConsented()
        try setMedicationMarker(valueRaw: medID.uuidString, present: taken, on: day)
    }

    func setPRN(_ took: Bool, on day: Date) throws {
        assertConsented()
        try setMedicationMarker(valueRaw: "prn", present: took, on: day)
    }

    func setStress(_ level: Int?, on day: Date) throws {
        assertConsented()
        try setScaleReading(kind: .stress, level: level.map { Self.clampSeverity($0) }, on: day)
    }

    func setMood(_ level: Int?, on day: Date) throws {
        assertConsented()
        try setScaleReading(kind: .mood, level: level.map { Self.clampMood($0) }, on: day)
    }

    func checkInMarker(on day: Date) throws {
        assertConsented()
        // Ensure a DayEntry anchor exists WITHOUT inventing any symptom/factor data (D-15). The bare
        // anchor records "checked in today" — an adherence marker, not fabricated health data.
        if try dayEntry(on: day) == nil {
            context.insert(SchemaV1.DayEntry(date: Calendar.current.startOfDay(for: day)))
            try context.save()
        }
    }

    // MARK: - Phase 3 saved-medication CRUD (D-06)

    @discardableResult
    func addMedication(name: String?, note: String?) throws -> UUID {
        assertConsented()
        let med = SchemaV1.Medication(
            name: Self.boundedNote(name), note: Self.boundedNote(note))
        context.insert(med)
        try context.save()
        return med.id
    }

    func archiveMedication(_ id: UUID) throws {
        assertConsented()
        // Soft-archive: flip isArchived, NEVER delete — prior `.medication` "taken" readings keyed by
        // this id stay valid so history is unchanged (D-06 / A3).
        let meds = try context.fetch(FetchDescriptor<SchemaV1.Medication>())
        for med in meds where med.id == id { med.isArchived = true }
        try context.save()
    }

    func savedMedications() throws -> [SchemaV1.Medication] {
        assertConsented()
        return try context.fetch(FetchDescriptor<SchemaV1.Medication>())
            .filter { $0.isArchived != true }
            .sorted { $0.createdAt < $1.createdAt }
    }

    // MARK: - Phase 3 factor value-snapshot reads (D-11)

    func factorReadings(on day: Date) throws -> [SchemaV1.FactorReading] {
        assertConsented()   // WR-05: reads health rows — assert the ONBD-01 precondition like its siblings.
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.FactorReading>())
            .filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
    }

    func factorDays() throws -> [FactorDay] {
        assertConsented()
        let cal = Calendar.current
        // Bucket every FactorReading by start-of-day, then project each bucket into the Sendable,
        // store-free FactorDay value (keep the result @Model-free for the insights engine).
        var byDay: [Date: [SchemaV1.FactorReading]] = [:]
        for reading in try context.fetch(FetchDescriptor<SchemaV1.FactorReading>()) {
            byDay[cal.startOfDay(for: reading.occurredOn), default: []].append(reading)
        }
        return byDay.keys.sorted().map { Self.makeFactorDay(day: $0, readings: byDay[$0] ?? []) }
    }

    /// Project one day's raw `FactorReading` rows into the Sendable snapshot. Defensive decode (T-03-02)
    /// throughout; honest nil≠0 for stress/mood (a missing row leaves the field nil; a level-0 row
    /// surfaces as 0).
    private static func makeFactorDay(day: Date, readings: [SchemaV1.FactorReading]) -> FactorDay {
        var foodTags: [FoodTag] = []
        var foodNote: String?
        var activityType: ActivityType?
        var activityMinutes: Double?
        var activityIntensity: ActivityIntensity?
        var sleepHours: Double?
        var sleepQuality: SleepQuality?
        var medicationsTaken: [UUID] = []
        var tookPRN = false
        var stress: Int?
        var mood: Int?

        for reading in readings {
            switch FactorKind(raw: reading.kindRaw) {
            case .food:
                if let note = reading.note {
                    foodNote = note   // the note-bearing `.food` row
                } else if let raw = reading.valueRaw {
                    let tag = FoodTag(raw: raw)
                    foodTags.append(tag)   // defensive: unknown raw -> .other (never dropped silently)
                }
            case .activity:
                activityType = ActivityType(raw: reading.valueRaw)
                activityMinutes = reading.amount
                activityIntensity = intensity(fromLevel: reading.level)
            case .sleep:
                sleepHours = reading.amount
                sleepQuality = SleepQuality(raw: reading.valueRaw)
            case .medication:
                if reading.valueRaw == "prn" {
                    tookPRN = true
                } else if let raw = reading.valueRaw, let uuid = UUID(uuidString: raw) {
                    medicationsTaken.append(uuid)
                }
            case .stress:
                stress = reading.level   // nil stays nil (no reading ⇒ field never set)
            case .mood:
                mood = reading.level
            case .other:
                continue
            }
        }

        return FactorDay(
            day: day, foodTags: foodTags, foodNote: foodNote,
            activityType: activityType, activityMinutes: activityMinutes,
            activityIntensity: activityIntensity,
            sleepHours: sleepHours, sleepQuality: sleepQuality,
            medicationsTaken: medicationsTaken, tookPRN: tookPRN,
            stress: stress, mood: mood)
    }

    // MARK: - Phase 3 private helpers

    /// Insert (present) or remove (absent) a single `.medication` reading keyed by `valueRaw`.
    private func setMedicationMarker(valueRaw: String, present: Bool, on day: Date) throws {
        let existing = try factorReadings(on: day).filter {
            $0.kindRaw == FactorKind.medication.rawValue && $0.valueRaw == valueRaw
        }
        if present {
            if existing.isEmpty {
                context.insert(SchemaV1.FactorReading(
                    occurredOn: Calendar.current.startOfDay(for: day),
                    kindRaw: FactorKind.medication.rawValue, valueRaw: valueRaw))
            }
        } else {
            for reading in existing { context.delete(reading) }
        }
        try context.save()
    }

    /// Upsert (or clear, when `level == nil`) a day's single scale reading (`.stress` / `.mood`).
    /// `nil` CLEARS — mirrors setBleeding's guard-let-else-delete so absence ≠ 0 (D-07/D-08).
    private func setScaleReading(kind: FactorKind, level: Int?, on day: Date) throws {
        let existing = try factorReadings(on: day).filter { $0.kindRaw == kind.rawValue }
        guard let level else {
            for reading in existing { context.delete(reading) }
            try context.save()
            return
        }
        if let first = existing.first {
            first.level = level
            for extra in existing.dropFirst() { context.delete(extra) }
        } else {
            context.insert(SchemaV1.FactorReading(
                occurredOn: Calendar.current.startOfDay(for: day),
                kindRaw: kind.rawValue, level: level))
        }
        try context.save()
    }

    /// Bound a free-text string at the seam (T-03-01 DoS guard); whitespace-only -> nil. Trims
    /// surrounding whitespace/newlines so a blank-space paste collapses to nil, matching `setNote`
    /// and `commitNewMed` (CR-02 — keeps the honesty contract: a blank note never fabricates a row).
    private static func boundedNote(_ text: String?) -> String? {
        guard let text else { return nil }
        let trimmed = String(text.prefix(maxNoteLength)).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Mood clamp 1...5 (D-08). Distinct from `clampSeverity` (0...10).
    private static func clampMood(_ value: Int) -> Int { min(5, max(1, value)) }

    /// ActivityIntensity <-> stored `level` (0 = low, 1 = medium, 2 = high).
    private static func intensityLevel(_ intensity: ActivityIntensity) -> Int {
        switch intensity {
        case .low:    return 0
        case .medium: return 1
        case .high:   return 2
        }
    }

    private static func intensity(fromLevel level: Int?) -> ActivityIntensity? {
        switch level {
        case 0:  return .low
        case 1:  return .medium
        case 2:  return .high
        default: return nil
        }
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
