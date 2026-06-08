// Requirement: SYMP-03 — free-text notes on a day's entry (D-05).
//
// GREEN in Plan 02-02 Task 1: the day-note write seam on the extended `Repository`.
// The note must persist on the day's `DayEntry`, be length-bounded at the write seam
// (truncated to `maxNoteLength`), and store empty as nil (empty -> nil, T-01-05 pattern).
//
// These tests also pin the rest of the Task-1 write seam (symptom upsert, bleeding upsert,
// severity clamp, the `BleedingDay` value snapshot, `isSuppressed`) since they share the
// same Repository surface and day-bucketing.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("SYMP-03 day note")
@MainActor
struct DayNoteTests {

    /// Build an in-memory SwiftData-backed repository for the test seam.
    private func makeRepo() throws -> (SwiftDataRepository, ModelContext) {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        return (SwiftDataRepository(context: ctx), ctx)
    }

    // The note persists on the day's DayEntry and round-trips.
    @Test func dayNote_persists() async throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setNote("cramps today", on: day)

        let entries = try repo.allDayEntries()
        #expect(entries.count == 1)
        #expect(entries.first?.note == "cramps today")
    }

    // The note is length-bounded at the write seam (truncated to maxNoteLength).
    @Test func dayNote_isLengthBounded() async throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        let huge = String(repeating: "x", count: 500)
        try repo.setNote(huge, on: day)

        let note = try repo.allDayEntries().first?.note
        #expect(note?.count == SwiftDataRepository.maxNoteLength,
                "Note must be truncated to maxNoteLength at the write seam (T-02-07).")
    }

    // Empty -> nil (T-01-05 pattern): an empty note stores nil, not "".
    @Test func dayNote_emptyStoresNil() async throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setNote("something", on: day)
        try repo.setNote("", on: day)

        let entries = try repo.allDayEntries()
        #expect(entries.count == 1, "Setting a note must not duplicate the day's entry.")
        #expect(entries.first?.note == nil, "Empty note must store as nil (empty -> nil).")
    }

    // upsertSymptom is idempotent per (day, type): a second call UPDATES, never duplicates.
    @Test func upsertSymptom_updates_notDuplicates() async throws {
        let (repo, ctx) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.upsertSymptom(type: .fatigue, severity: 6, on: day)
        try repo.upsertSymptom(type: .fatigue, severity: 8, on: day)

        let readings = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        let fatigue = readings.filter { $0.typeRaw == SymptomType.fatigue.rawValue }
        #expect(fatigue.count == 1, "One SymptomReading per (day, type).")
        #expect(fatigue.first?.severity == 8, "Re-upsert updates severity in place.")
    }

    // Severity is clamped to 0...10 at the seam (a passed 99 stores as 10).
    @Test func upsertSymptom_clampsSeverity() async throws {
        let (repo, ctx) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.upsertSymptom(type: .fatigue, severity: 99, on: day)
        try repo.upsertSymptom(type: .lowMood, severity: -4, on: day)

        let readings = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        let fatigue = readings.first { $0.typeRaw == SymptomType.fatigue.rawValue }
        let lowMood = readings.first { $0.typeRaw == SymptomType.lowMood.rawValue }
        #expect(fatigue?.severity == 10, "Severity above 10 clamps to 10 (V5 input validation).")
        #expect(lowMood?.severity == 0, "Severity below 0 clamps to 0.")
    }

    // removeSymptom deletes the day's reading for that type.
    @Test func removeSymptom_deletesReading() async throws {
        let (repo, ctx) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.upsertSymptom(type: .fatigue, severity: 6, on: day)
        try repo.removeSymptom(type: .fatigue, on: day)

        let readings = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(readings.filter { $0.typeRaw == SymptomType.fatigue.rawValue }.isEmpty)
    }

    // setBleeding persists a BleedingEvent; setting nil removes it (no phantom record).
    @Test func setBleeding_upsertsAndRemoves() async throws {
        let (repo, ctx) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setBleeding(intensity: .medium, clots: true, on: day)

        var events = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(events.count == 1)
        #expect(events.first?.flowRaw == FlowIntensity.medium.rawValue)
        #expect(events.first?.clots == true)

        // Re-setting updates in place (no duplicate).
        try repo.setBleeding(intensity: .heavy, clots: false, on: day)
        events = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(events.count == 1, "Re-setting the day's bleeding updates in place.")
        #expect(events.first?.flowRaw == FlowIntensity.heavy.rawValue)

        // nil intensity removes the record.
        try repo.setBleeding(intensity: nil, clots: false, on: day)
        events = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(events.isEmpty, "Clearing bleeding removes the day's record (no phantom).")
    }

    // bleedingDays() returns one Sendable BleedingDay per flow-bearing calendar day.
    @Test func bleedingDays_snapshotPerFlowDay() async throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        let day1 = Date(timeIntervalSince1970: 1_700_000_000)
        let day2 = cal.date(byAdding: .day, value: 2, to: day1)!
        try repo.setBleeding(intensity: .light, clots: false, on: day1)
        try repo.setBleeding(intensity: .heavy, clots: false, on: day2)

        let days = try repo.bleedingDays()
        #expect(days.count == 2)
        #expect(days.allSatisfy { $0.hadFlow })
    }

    // isSuppressed reflects the latest TreatmentState.modeRaw == "suppressed".
    @Test func isSuppressed_reflectsLatestTreatmentState() async throws {
        let (repo, ctx) = try makeRepo()
        #expect(try repo.isSuppressed() == false, "No treatment state => not suppressed.")

        ctx.insert(SchemaV1.TreatmentState(
            startedAt: Date(timeIntervalSince1970: 1_700_000_000), modeRaw: "suppressed"))
        try ctx.save()
        #expect(try repo.isSuppressed() == true)
    }
}
