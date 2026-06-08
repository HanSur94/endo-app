// Requirement: SYMP-04 — edit or delete any past entry (D-06).
//
// Edit updates a day's records in place (no duplicate). Delete removes the DayEntry AND cascades its
// child SymptomReading / PainPoint / BleedingEvent so a subsequent bleedingDays() no longer includes
// the day (the cycle math recomputes from a clean event set, D-06 / RESEARCH Pattern 4).
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("SYMP-04 edit / delete")
struct EditDeleteTests {

    /// Log a full day (symptom + pain point + bleeding + note + a Phase-3 factor) through the
    /// Repository. The factor row (sleep + stress) exercises the SchemaV3 `FactorReading` table so the
    /// delete-completeness test can prove DATA-04 closes the local orphan gap.
    private func logFullDay(_ repo: SwiftDataRepository, on day: Date) throws {
        try repo.upsertSymptom(type: .fatigue, severity: 5, on: day)
        try repo.setPainPoint(region: .lowerAbdomenPelvis, severity: 6, on: day)
        try repo.setBleeding(intensity: .medium, clots: false, on: day)
        try repo.setNote("rough day", on: day)
        // Phase-3 day-scoped factor rows (FactorReading). A day-delete must remove these too.
        try repo.setSleep(hours: 6, quality: .poor, on: day)
        try repo.setStress(4, on: day)
    }

    // Re-opening a logged day and changing the symptom severity UPDATES the SymptomReading (no dup).
    @Test func edit_updatesSymptomSeverity_noDuplicate() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try logFullDay(repo, on: day)
        // Edit: same (day, symptom), new severity.
        try repo.upsertSymptom(type: .fatigue, severity: 9, on: day)

        let readings = try repo.symptomReadings(on: day)
        let fatigue = readings.filter { $0.typeRaw == SymptomType.fatigue.rawValue }
        #expect(fatigue.count == 1, "Editing a symptom must update, not duplicate.")
        #expect(fatigue.first?.severity == 9)
    }

    // Delete removes the DayEntry AND cascades its child records (symptom/pain/bleeding).
    @Test func delete_removesDay_andCascadesChildren() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let repo = SwiftDataRepository(context: ctx)
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try logFullDay(repo, on: day)
        // Sanity: everything exists before delete.
        #expect(try repo.symptomReadings(on: day).count == 1)
        #expect(try repo.painPoints(on: day).count == 1)
        #expect(!(try repo.factorReadings(on: day)).isEmpty,
                "Precondition: the day carries FactorReading rows before delete.")

        try repo.deleteDay(day)

        // Every child record AND the DayEntry are gone (full cascade).
        #expect(try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>()).isEmpty,
                "deleteDay must cascade-delete SymptomReadings.")
        #expect(try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>()).isEmpty,
                "deleteDay must cascade-delete PainPoints.")
        #expect(try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>()).isEmpty,
                "deleteDay must cascade-delete BleedingEvents.")
        #expect(try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>()).isEmpty,
                "deleteDay must remove the DayEntry.")
        // DATA-04 local orphan gap: every Phase-3 FactorReading for the day must be gone too, or a
        // surviving row would still mirror to the user's private CloudKit store after a delete.
        #expect(try ctx.fetch(FetchDescriptor<SchemaV1.FactorReading>()).isEmpty,
                "deleteDay must remove FactorReadings for the day (DATA-04 — no orphan to mirror).")
    }

    // Guard (DATA-04 / T-04-08): a day-delete must NOT over-delete non-day-scoped definitions. A
    // saved Medication definition created before the delete still exists afterward.
    @Test func delete_doesNotRemoveMedicationDefinitions() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let repo = SwiftDataRepository(context: ctx)
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        // A saved med definition (NOT day-scoped) and a full logged day.
        let medID = try repo.addMedication(name: "Ibuprofen", note: nil)
        try logFullDay(repo, on: day)
        #expect(try repo.savedMedications().contains { $0.id == medID })

        try repo.deleteDay(day)

        // The med DEFINITION survives a day-delete — only day-scoped records are removed.
        #expect(try repo.savedMedications().contains { $0.id == medID },
                "A day-delete must NOT remove saved Medication definitions (not day-scoped).")
        #expect(!(try ctx.fetch(FetchDescriptor<SchemaV1.Medication>())).isEmpty,
                "Medication definitions must survive a day-delete.")
    }

    // After deleting a bleeding day, bleedingDays() no longer includes it — the cycle recomputes
    // from a clean event set (D-06).
    @Test func delete_removesDayFromBleedingDays_cycleRecomputes() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let cal = Calendar.current
        let dayA = cal.startOfDay(for: Date(timeIntervalSince1970: 1_700_000_000))
        let dayB = cal.date(byAdding: .day, value: 1, to: dayA)!

        try repo.setBleeding(intensity: .medium, clots: false, on: dayA)
        try repo.setBleeding(intensity: .light, clots: false, on: dayB)
        #expect(try repo.bleedingDays().count == 2)

        try repo.deleteDay(dayA)

        let remaining = try repo.bleedingDays()
        #expect(remaining.count == 1, "A deleted bleeding day must drop out of bleedingDays().")
        #expect(!remaining.contains { cal.isDate($0.day, inSameDayAs: dayA) })
        #expect(remaining.contains { cal.isDate($0.day, inSameDayAs: dayB) })
    }
}
