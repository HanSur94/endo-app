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
// EndoInsights is the Foundation-only insights package (SC1). The Repository — and ONLY the
// Repository — imports BOTH SwiftData and EndoInsights so it can project `@Model` rows → the
// package's `DaySnapshot` value contract. The package itself never imports SwiftData (D-10/D-14).
import EndoInsights

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

    // MARK: Phase 5 — the @Model → DaySnapshot mapping seam (D-10/D-14, INST-01).

    /// One `EndoInsights.DaySnapshot` per calendar day that carries any logged signal, ascending by
    /// day. This is the ONLY place SwiftData `@Model` rows become the package's value contract (SC1):
    /// symptoms/pain/bleeding/factors → the snapshot, app-enum → package-enum (same raw value), honest
    /// nil≠0, `hasNote` presence-only, the current suppressed flag stamped onto every snapshot. The
    /// insights engine consumes only `[DaySnapshot]` and never touches the store. Consent-gated.
    func daySnapshots() throws -> [DaySnapshot]

    // MARK: Phase 6 — dismissal CRUD (INST-04, D-09/D-10). Consent-gated; append-only; dedupe-on-read.

    /// Durably record that the user dismissed the insight identified by `signalKey` (D-09). APPEND-ONLY:
    /// inserts ONE `DismissedInsight` row — never mutates/dedupes on write (cross-device duplicates
    /// collapse harmlessly on read). MUST NOT be called before consent (ONBD-01).
    func dismissInsight(signalKey: String) throws
    /// The deduped set of all dismissed signalKeys (D-10/D-12). Read-side `Set` dedupe collapses any
    /// append-only duplicates. The insights engine consumes this `Set` in Phase 8. Consent-gated.
    func dismissedSignalKeys() throws -> Set<String>

    // MARK: Phase 8 — cached-insight CRUD (D-02/D-14/D-18). Consent-gated; replace-all on recompute.

    /// Load the cached gated insights (D-02/D-04) — decodes each `CachedInsight.payload` blob back into
    /// an `Insight`. A `nil`/corrupt/undecodable blob is SKIPPED (compactMap), never fatal (T-08-04): a
    /// corrupt cache degrades to "recompute", never a crash. MUST NOT be called before consent (ONBD-01).
    func loadCachedInsights() throws -> [Insight]
    /// Replace the WHOLE cache (D-04 / D-14): delete every existing `CachedInsight` row, then persist one
    /// row per insight (flat `signalKey`/`kindRaw`/`windowRaw` identity columns + a `Codable` `payload`
    /// blob carrying the WHOLE `Insight`). Replace-all (not upsert) so a stale or dismissed insight can
    /// never linger. The blob encodes ONLY the gated `Insight`, never a `DaySnapshot` (T-08-01).
    /// MUST NOT be called before consent (ONBD-01).
    func replaceCachedInsights(_ insights: [Insight], computedAt: Date, fingerprint: String) throws

    // MARK: Phase 9 — imported-sample CRUD (DATA-05, D-01/D-02). Consent-gated; dedupe-by-uuid upsert.

    /// All `ImportedSample` rows recorded on `day` (day-bucketed, mirrors `factorReadings(on:)`).
    /// Consent-gated. MUST NOT be called before consent (ONBD-01).
    func importedSamples(on day: Date) throws -> [SchemaV1.ImportedSample]
    /// Upsert one imported value, deduped by `hkSampleUUID` (Pitfall 5): an existing row with the same
    /// UUID is updated in place; otherwise a new row is inserted. NEVER mutates manual-logging rows (D-01).
    func upsertImportedSample(_ value: ImportedSampleValue) throws
    /// Remove every `ImportedSample` whose `hkSampleUUID` is in `uuids` (honor HealthKit deletions).
    func deleteImportedSamples(uuids: [String]) throws
    /// The single entry the import service's persist closure calls: upsert each value (dedupe-by-uuid)
    /// and delete the rows HealthKit reported deleted, in one consent-gated unit (D-09 / Pitfall 5).
    func replaceOrInsertImported(values: [ImportedSampleValue], deletedUUIDs: [UUID]) throws
    /// Per-field provenance for `day` (D-03 badge): `.manual` when manual logging supplied the winning
    /// value, `.appleHealth` when an imported sample did, `nil` when neither — the merge's source half.
    func daySnapshotSources(on day: Date) throws -> DaySnapshotSource
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
        // Phase-3 factor rows (food/activity/sleep/meds-taken/stress/mood) are day-scoped too — delete
        // them so a deleted day leaves ZERO local orphans (DATA-04). An orphaned FactorReading would
        // otherwise survive and still mirror to the user's private CloudKit store after a delete.
        // Hard delete only (D-09/D-10 — no tombstone). NOT touched: Medication / TreatmentState
        // definitions, which are NOT day-scoped and must survive a day-delete.
        for reading in try factorReadings(on: day) { context.delete(reading) }
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
        try setScaleReading(kind: .stress, level: level.map { Self.clampStress($0) }, on: day)
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

    // MARK: - Phase 5 @Model → DaySnapshot mapping seam (D-10/D-14, INST-01)

    func daySnapshots() throws -> [DaySnapshot] {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling read.
        let cal = Calendar.current

        // Bucket every day-scoped @Model type by start-of-day (mirrors factorDays). The union of all
        // day keys drives one DaySnapshot per day that carries ANY logged signal.
        var symptomsByDay: [Date: [SchemaV1.SymptomReading]] = [:]
        for r in try context.fetch(FetchDescriptor<SchemaV1.SymptomReading>()) {
            symptomsByDay[cal.startOfDay(for: r.occurredOn), default: []].append(r)
        }
        var painByDay: [Date: [SchemaV1.PainPoint]] = [:]
        for p in try context.fetch(FetchDescriptor<SchemaV1.PainPoint>()) {
            painByDay[cal.startOfDay(for: p.occurredOn), default: []].append(p)
        }
        var bleedingByDay: [Date: [SchemaV1.BleedingEvent]] = [:]
        for e in try context.fetch(FetchDescriptor<SchemaV1.BleedingEvent>()) {
            bleedingByDay[cal.startOfDay(for: e.occurredAt), default: []].append(e)
        }
        var factorsByDay: [Date: [SchemaV1.FactorReading]] = [:]
        for f in try context.fetch(FetchDescriptor<SchemaV1.FactorReading>()) {
            factorsByDay[cal.startOfDay(for: f.occurredOn), default: []].append(f)
        }
        var noteDays: Set<Date> = []
        for entry in try context.fetch(FetchDescriptor<SchemaV1.DayEntry>()) where entry.note != nil {
            noteDays.insert(cal.startOfDay(for: entry.date))
        }
        // Phase 9 (D-02/D-04): bucket imported samples by start-of-day too, so an imported-only day
        // joins the union (importedDayJoinsTheUnion) and each day's imported rows feed the manual-wins
        // merge. Imported rows NEVER mutate manual rows (D-01) — they only fill ABSENT fields below.
        var importedByDay: [Date: [SchemaV1.ImportedSample]] = [:]
        for sample in try context.fetch(FetchDescriptor<SchemaV1.ImportedSample>()) {
            importedByDay[cal.startOfDay(for: sample.day), default: []].append(sample)
        }

        // The suppressed flag is a single current value (latest TreatmentState, matches CycleMath);
        // stamp it onto every snapshot so the per-day DaySnapshot shape (D-03) is preserved.
        let suppressed = try isSuppressed()

        // Union of all day keys across every signal type, sorted ascending.
        var dayKeys = Set<Date>()
        dayKeys.formUnion(symptomsByDay.keys)
        dayKeys.formUnion(painByDay.keys)
        dayKeys.formUnion(bleedingByDay.keys)
        dayKeys.formUnion(factorsByDay.keys)
        dayKeys.formUnion(noteDays)
        dayKeys.formUnion(importedByDay.keys)

        return dayKeys.sorted().map { key in
            Self.makeDaySnapshot(
                day: key,
                symptoms: symptomsByDay[key] ?? [],
                pain: painByDay[key] ?? [],
                bleeding: bleedingByDay[key] ?? [],
                factors: factorsByDay[key] ?? [],
                imported: ImportedSignals(rows: importedByDay[key] ?? []),
                hasNote: noteDays.contains(key),
                suppressed: suppressed)
        }
    }

    /// Per-field provenance for a single day (D-03 badge). The SINGLE merge decision (`MergeResolution`)
    /// is the source of truth for BOTH the value and its source — this read just surfaces the source half.
    /// A field with NEITHER a manual nor an imported value reports `nil` (no badge — SC3 honesty).
    func daySnapshotSources(on day: Date) throws -> DaySnapshotSource {
        assertConsented()
        let manualFactors = try factorReadings(on: day)
        let manualBleeding = try bleedingEvents(on: day)
        let manualFood = manualFactors.contains {
            FactorKind(raw: $0.kindRaw) == .food
        }
        let manualSleep = manualFactors.contains {
            FactorKind(raw: $0.kindRaw) == .sleep
        }
        let manualActivity = manualFactors.contains {
            FactorKind(raw: $0.kindRaw) == .activity
        }
        let manualFlow = manualBleeding.contains { EndoInsights.FlowIntensity(raw: $0.flowRaw) != nil }

        let imported = ImportedSignals(rows: try importedSamples(on: day))
        return DaySnapshotSource(
            flow: Self.source(manual: manualFlow, imported: imported.flow != nil),
            sleep: Self.source(manual: manualSleep, imported: imported.sleepHours != nil || imported.sleepQuality != nil),
            activity: Self.source(manual: manualActivity, imported: imported.activityType != nil || imported.activityMinutes != nil),
            food: Self.source(manual: manualFood, imported: imported.ateThatDay == true))
    }

    /// Resolve a field's source: manual present → `.manual` (manual ALWAYS wins, D-02); else imported
    /// present → `.appleHealth`; else `nil` (neither — no fabricated badge).
    private static func source(manual: Bool, imported: Bool) -> ValueSource? {
        if manual { return .manual }
        if imported { return .appleHealth }
        return nil
    }

    // MARK: - Phase 6 dismissal CRUD (INST-04, D-09/D-10)

    func dismissInsight(signalKey: String) throws {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling write.
        // APPEND-ONLY: a dismissal is a new event row (the CloudKit "prefer append-only events" rule).
        // Cross-device duplicates collapse harmlessly on read via `Set` (dismissedSignalKeys) — we NEVER
        // dedupe or delete on write.
        context.insert(SchemaV1.DismissedInsight(signalKey: signalKey))
        try context.save()
    }

    func dismissedSignalKeys() throws -> Set<String> {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling read.
        // Dedupe-on-read: a `Set` collapses any append-only duplicates; drop nil keys (CloudKit-optional).
        return Set(try context.fetch(FetchDescriptor<SchemaV1.DismissedInsight>()).compactMap(\.signalKey))
    }

    // MARK: - Phase 8 cached-insight CRUD (D-02/D-14/D-18)

    func loadCachedInsights() throws -> [Insight] {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling read (T-08-02).
        // Decode each row's payload blob back into an Insight. A nil/corrupt blob is compactMap-SKIPPED
        // (T-08-04 / V5 input validation): a corrupt cache degrades to "recompute", never a fatal read.
        return try context.fetch(FetchDescriptor<SchemaV1.CachedInsight>())
            .compactMap(\.payload)
            .compactMap { try? JSONDecoder().decode(Insight.self, from: $0) }
    }

    func replaceCachedInsights(_ insights: [Insight], computedAt: Date, fingerprint: String) throws {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling write (T-08-02).
        // Replace-all (D-04/D-14): delete every existing cache row first so a stale/dismissed insight
        // can never linger, then re-insert one row per insight.
        for old in try context.fetch(FetchDescriptor<SchemaV1.CachedInsight>()) { context.delete(old) }
        for insight in insights {
            // T-08-01: encode ONLY the gated `Insight` (non-causal copy + category signalKey + numeric
            // points) — NEVER a `DaySnapshot` and never note prose.
            let payload = try JSONEncoder().encode(insight)
            context.insert(SchemaV1.CachedInsight(
                signalKey: insight.signalKey,
                kindRaw: insight.kind.rawValue,
                windowRaw: insight.window.rawValue,
                payload: payload,
                computedAt: computedAt,
                fingerprint: fingerprint))
        }
        try context.save()
    }

    // MARK: - Phase 9 imported-sample CRUD (DATA-05, D-01/D-02)

    func importedSamples(on day: Date) throws -> [SchemaV1.ImportedSample] {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling read.
        let cal = Calendar.current
        return try context.fetch(FetchDescriptor<SchemaV1.ImportedSample>())
            .filter { cal.isDate($0.day, inSameDayAs: day) }
    }

    func upsertImportedSample(_ value: ImportedSampleValue) throws {
        assertConsented()   // ONBD-01 / V4 — first statement, like every sibling write.
        let uuidString = value.hkUUID.uuidString
        // Dedupe-by-uuid (Pitfall 5): an existing row with the same hkSampleUUID is updated in place,
        // mirroring `setMedicationMarker`'s exists-guard, so a re-pull / cross-device mirror of the same
        // sample collapses harmlessly instead of inserting a duplicate.
        let existing = try context.fetch(FetchDescriptor<SchemaV1.ImportedSample>())
            .filter { $0.hkSampleUUID == uuidString }
        let row = existing.first ?? {
            let created = SchemaV1.ImportedSample(hkSampleUUID: uuidString)
            context.insert(created)
            return created
        }()
        // Collapse any accidental duplicate rows for this UUID onto the single survivor.
        for extra in existing.dropFirst() { context.delete(extra) }
        Self.apply(value, to: row)
        try context.save()
    }

    func deleteImportedSamples(uuids: [String]) throws {
        assertConsented()
        guard !uuids.isEmpty else { return }
        let keys = Set(uuids)
        for row in try context.fetch(FetchDescriptor<SchemaV1.ImportedSample>())
        where row.hkSampleUUID.map(keys.contains) == true {
            context.delete(row)
        }
        try context.save()
    }

    func replaceOrInsertImported(values: [ImportedSampleValue], deletedUUIDs: [UUID]) throws {
        assertConsented()
        // Apply deletions first so a same-pull delete-then-reinsert of the same UUID nets correctly.
        for uuid in deletedUUIDs {
            let key = uuid.uuidString
            for row in try context.fetch(FetchDescriptor<SchemaV1.ImportedSample>())
            where row.hkSampleUUID == key {
                context.delete(row)
            }
        }
        for value in values {
            try upsertImportedSample(value)   // dedupe-by-uuid upsert (saves per value).
        }
        try context.save()
    }

    /// Map an `ImportedSampleValue` (its day, domain, and one-of-four payload) onto the flat
    /// `ImportedSample` columns. Only the columns relevant to the row's domain are populated; the rest
    /// stay nil (CloudKit-safe all-optional shape, mirrors the @Model contract). Reuses the existing
    /// app-enum raw values so the merge in `daySnapshots()` decodes them with the same defensive helpers.
    private static func apply(_ value: ImportedSampleValue, to row: SchemaV1.ImportedSample) {
        row.day = Calendar.current.startOfDay(for: value.day)
        row.domainRaw = value.domain.rawValue
        // Reset every value column so an in-place update never carries a stale field from a prior domain.
        row.flowRaw = nil
        row.sleepHours = nil
        row.sleepQualityRaw = nil
        row.activityTypeRaw = nil
        row.activityMinutes = nil
        row.ateThatDay = nil
        switch value.payload {
        case let .cycle(flowRaw):
            row.flowRaw = flowRaw
        case let .sleep(hours, quality):
            row.sleepHours = hours
            row.sleepQualityRaw = quality.rawValue
        case let .workout(type, minutes):
            row.activityTypeRaw = type.rawValue
            row.activityMinutes = minutes
        case let .nutrition(ateThatDay):
            row.ateThatDay = ateThatDay
        }
        row.importedAt = .now
    }

    /// The decoded, package-facing imported signals for ONE day — flattened from that day's
    /// `ImportedSample` rows (one row per domain, defensively decoded). This is the imported HALF of the
    /// manual-wins merge: `makeDaySnapshot` uses each field ONLY where the manual value is absent (D-02).
    /// Defensive decode throughout (unknown raws → the enum's defensive fallback; nil stays nil — never 0).
    struct ImportedSignals {
        var flow: EndoInsights.FlowIntensity?
        var sleepHours: Double?
        var sleepQuality: EndoInsights.SleepQuality?
        var activityType: EndoInsights.ActivityType?
        var activityMinutes: Double?
        /// Nutrition presence (D-16). `true` ⇒ an "ate that day" food-presence signal exists.
        var ateThatDay: Bool?

        /// Flatten a day's rows into per-day signals.
        ///
        /// SLEEP (CR-01): Apple Health records ONE night as MANY sleep-analysis segments (one per
        /// stage/awake transition), and each segment is its own `ImportedSample` row (distinct hkUUID).
        /// So a night's asleep hours is the SUM of its rows, not any single row. We accumulate
        /// `sleepHours` across the day's `.sleep` rows and derive the coarse `sleepQuality` band from the
        /// running TOTAL via the same `HealthSampleMapping` thresholds the reader used per-segment — never
        /// last-writer-wins on a single ~minutes-long fragment. The per-row `sleepQualityRaw` is advisory.
        ///
        /// WORKOUTS (WR-04): a day can have MULTIPLE workouts, each its own row. We sum the minutes and
        /// keep the LONGEST session's type (deterministic regardless of fetch order), instead of an
        /// arbitrary last-writer-wins on whichever row iterated last.
        ///
        /// CYCLE / NUTRITION: a day's flow / "ate that day" presence is single-valued (rows are deduped
        /// by hkUUID upstream); last-writer-wins is correct there.
        init(rows: [SchemaV1.ImportedSample]) {
            // Workout accumulators (WR-04): sum minutes; the type comes from the longest session.
            var workoutMinutesTotal: Double?
            var longestWorkoutMinutes = -1.0
            for row in rows {
                switch HealthDomain(rawValue: row.domainRaw ?? "") {
                case .cycle:
                    // nil/unknown flow raw → nil (never fabricated), mirroring FlowIntensity(raw:).
                    flow = EndoInsights.FlowIntensity(raw: row.flowRaw)
                case .sleep:
                    // SUM the night's segments (CR-01). A nil row contributes nothing; `sleepHours` stays
                    // nil until at least one real `.sleep` row arrives (nil≠0, SC3 honesty).
                    if let rowHours = row.sleepHours {
                        sleepHours = (sleepHours ?? 0) + rowHours
                    }
                case .workouts:
                    // SUM minutes; keep the LONGEST session's type (WR-04 deterministic collapse).
                    if let minutes = row.activityMinutes {
                        workoutMinutesTotal = (workoutMinutesTotal ?? 0) + minutes
                        if minutes > longestWorkoutMinutes {
                            longestWorkoutMinutes = minutes
                            activityType = row.activityTypeRaw.map { EndoInsights.ActivityType(raw: $0) }
                        }
                    } else if activityType == nil {
                        // A typed-but-minute-less workout row still establishes the type (never fabricates
                        // minutes — those stay nil unless a row carries them).
                        activityType = row.activityTypeRaw.map { EndoInsights.ActivityType(raw: $0) }
                    }
                case .nutrition:
                    ateThatDay = row.ateThatDay
                case .none:
                    continue   // unknown/corrupt domain raw → contribute nothing (defensive, never crash).
                }
            }
            // Derive the sleep band from the SUMMED total (CR-01) — not any single fragment's band.
            sleepQuality = sleepHours.map { HealthSampleMapping.sleepQuality(forAsleepHours: $0) }
            activityMinutes = workoutMinutesTotal
        }
    }

    /// Project one day's raw `@Model` rows into the Sendable `EndoInsights.DaySnapshot`. Defensive
    /// decode throughout (never force-unwrap a decoded raw); honest nil≠0 for stress/mood; flow nil
    /// when no flow-bearing bleeding event. App-enum → package-enum is a same-raw-value passthrough.
    private static func makeDaySnapshot(
        day: Date,
        symptoms: [SchemaV1.SymptomReading],
        pain: [SchemaV1.PainPoint],
        bleeding: [SchemaV1.BleedingEvent],
        factors: [SchemaV1.FactorReading],
        imported: ImportedSignals,
        hasNote: Bool,
        suppressed: Bool
    ) -> DaySnapshot {
        // Symptoms → package SymptomPoint. A row whose typeRaw is nil/undecodable is DROPPED (never
        // coerced to a real .other), mirroring the pain-point defensive decode below — so a corrupt or
        // future note-only symptom row can NEVER fabricate an "Other symptom" signal (honesty: WR-01).
        // nil severity stays nil (≠ 0).
        let symptomPoints: [SymptomPoint] = symptoms.compactMap { r in
            guard let raw = r.typeRaw, let type = EndoInsights.SymptomType(rawValue: raw) else { return nil }
            return SymptomPoint(type: type, severity: r.severity)
        }

        // Pain → fully-qualified EndoInsights.PainPoint (name-clash with SchemaV1.PainPoint @Model).
        // A point whose region decodes to nil (unknown) is DROPPED, never crashed.
        let painPoints: [EndoInsights.PainPoint] = pain.compactMap { p in
            guard let region = EndoInsights.BodyRegion(raw: p.regionRaw) else { return nil }
            return EndoInsights.PainPoint(region: region, severity: p.severity)
        }

        // Bleeding → the day's flow-bearing event (no event ⇒ manual flow nil; nil clots ⇒ false).
        let flowEvent = bleeding.first { EndoInsights.FlowIntensity(raw: $0.flowRaw) != nil }
        let manualFlow = EndoInsights.FlowIntensity(raw: flowEvent?.flowRaw)
        let clots = flowEvent?.clots ?? false

        // Factor fields — reuse the shared FactorKind projection, mapped to the package enums.
        let f = projectFactors(factors)

        // MANUAL-WINS, PER-FIELD, NIL-HONEST MERGE (D-02/D-04/SC3 — the make-or-break). For each
        // imported-capable field: manual value present ⇒ use manual; else imported value present ⇒ use
        // imported; else leave nil (NEVER 0, NEVER a fabricated reading). A denied/empty import has no
        // imported values, so every field falls through to the manual value (or nil) — SC3 honesty.
        //
        // flow / sleep / activity (type+minutes): imported fills ONLY when the manual side is absent.
        let flow = manualFlow ?? imported.flow
        let sleepHours = f.sleepHours ?? imported.sleepHours
        let sleepQuality = f.sleepQuality ?? imported.sleepQuality
        // Activity type+minutes are one logical unit: imported fills both ONLY when there is no manual
        // activity type (so an imported workout never grafts minutes onto a manual-typed activity).
        let useImportedActivity = (f.activityType == nil) && (imported.activityType != nil)
        let activityType = useImportedActivity ? imported.activityType : f.activityType
        let activityMinutes = useImportedActivity ? imported.activityMinutes : f.activityMinutes

        // Food: nutrition is presence-only (D-16). Imported "ate that day" contributes a single
        // food-presence FoodTag (.other) ONLY when the day has NO manual food tags (manual wins, D-02).
        // It is NEVER a fabricated specific tag and NEVER added when manual food exists.
        var foodTags = f.foodTags
        if foodTags.isEmpty, imported.ateThatDay == true {
            foodTags = [.other]
        }

        return DaySnapshot(
            day: day,
            symptoms: symptomPoints,
            pain: painPoints,
            flow: flow,
            clots: clots,
            foodTags: foodTags,
            activityType: activityType,
            activityMinutes: activityMinutes,
            activityIntensity: f.activityIntensity,
            sleepHours: sleepHours,
            sleepQuality: sleepQuality,
            medicationsTaken: f.medicationsTaken,
            tookPRN: f.tookPRN,
            stress: f.stress,
            mood: f.mood,
            hasNote: hasNote,
            suppressed: suppressed)
    }

    /// Shared per-day factor projection mapped to the EndoInsights package enums (Option-D boundary).
    /// Mirrors `makeFactorDay`'s FactorKind switch exactly so the two projections cannot drift, but
    /// emits package-facing enum values. Defensive decode (unknown catalog raw → `.other`; optional
    /// raws → nil); honest nil≠0 for stress/mood.
    private static func projectFactors(_ readings: [SchemaV1.FactorReading]) -> (
        foodTags: [EndoInsights.FoodTag],
        activityType: EndoInsights.ActivityType?,
        activityMinutes: Double?,
        activityIntensity: EndoInsights.ActivityIntensity?,
        sleepHours: Double?,
        sleepQuality: EndoInsights.SleepQuality?,
        medicationsTaken: [UUID],
        tookPRN: Bool,
        stress: Int?,
        mood: Int?
    ) {
        var foodTags: [EndoInsights.FoodTag] = []
        var activityType: EndoInsights.ActivityType?
        var activityMinutes: Double?
        var activityIntensity: EndoInsights.ActivityIntensity?
        var sleepHours: Double?
        var sleepQuality: EndoInsights.SleepQuality?
        var medicationsTaken: [UUID] = []
        var tookPRN = false
        var stress: Int?
        var mood: Int?

        for reading in readings {
            switch FactorKind(raw: reading.kindRaw) {
            case .food:
                if reading.note != nil {
                    continue   // note-bearing `.food` row carries free text — NOT carried into the snapshot (D-01 privacy).
                } else if let raw = reading.valueRaw {
                    foodTags.append(EndoInsights.FoodTag(raw: raw))   // unknown raw → .other (never dropped silently).
                }
            case .activity:
                activityType = EndoInsights.ActivityType(raw: reading.valueRaw)
                activityMinutes = reading.amount
                activityIntensity = intensity(packageFromLevel: reading.level)
            case .sleep:
                sleepHours = reading.amount
                sleepQuality = EndoInsights.SleepQuality(raw: reading.valueRaw)
            case .medication:
                if reading.valueRaw == "prn" {
                    tookPRN = true
                } else if let raw = reading.valueRaw, let uuid = UUID(uuidString: raw) {
                    medicationsTaken.append(uuid)
                }
            case .stress:
                stress = reading.level   // nil stays nil (no reading ⇒ field never set).
            case .mood:
                mood = reading.level
            case .other:
                continue
            }
        }

        return (foodTags, activityType, activityMinutes, activityIntensity,
                sleepHours, sleepQuality, medicationsTaken, tookPRN, stress, mood)
    }

    /// Stored `level` → package `ActivityIntensity` (0 = low, 1 = medium, 2 = high; else nil).
    private static func intensity(packageFromLevel level: Int?) -> EndoInsights.ActivityIntensity? {
        switch level {
        case 0:  return .low
        case 1:  return .medium
        case 2:  return .high
        default: return nil
        }
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

    /// Stress clamp 0...10 (FactorDay.stress scale). Dedicated helper (WR-02 sibling fix WR-04) so the
    /// stress range is INDEPENDENT of symptom-severity changes: it shares the 0...10 range with
    /// `clampSeverity` today, but a future change to the symptom-severity range must NOT silently
    /// re-clamp stress.
    private static func clampStress(_ value: Int) -> Int { min(10, max(0, value)) }

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
