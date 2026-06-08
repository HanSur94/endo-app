// Requirement: LIFE-03 / D-06 — saved-medication CRUD + per-day "taken" log + soft-archive history.
//
// GREEN (Plan 03-01 Task 2). A saved med can be added, listed, and soft-archived; archiving hides it
// from the active list but does NOT erase prior `.medication` "taken" readings keyed by its id.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("LIFE-03 / D-06 medication")
struct MedicationTests {

    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    // addMedication -> savedMedications lists it.
    @Test func addMedication_appearsInSavedList() throws {
        let repo = try makeRepo()
        let id = try repo.addMedication(name: "Ibuprofen", note: "200mg")

        let meds = try repo.savedMedications()
        #expect(meds.count == 1)
        #expect(meds.first?.id == id)
        #expect(meds.first?.name == "Ibuprofen")
        #expect(meds.first?.note == "200mg")
    }

    // archiveMedication hides it from savedMedications, but prior `taken` history survives.
    @Test func archiveMedication_hidesFromList_butPreservesHistory() throws {
        let repo = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        let id = try repo.addMedication(name: "Ibuprofen", note: nil)
        try repo.setMedicationTaken(medID: id, taken: true, on: day)

        // Sanity: it was logged as taken.
        let before = try #require(try repo.factorDays().first)
        #expect(before.medicationsTaken.contains(id))

        try repo.archiveMedication(id)

        // It is gone from the active saved list…
        #expect(try repo.savedMedications().isEmpty)
        // …but the prior per-day "taken" reading keyed by its id is UNCHANGED (D-06).
        let after = try #require(try repo.factorDays().first)
        #expect(after.medicationsTaken.contains(id))
    }

    // PRN marker round-trips and can be toggled off.
    @Test func prn_marker_toggles() throws {
        let repo = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setPRN(true, on: day)
        #expect(try repo.factorDays().first?.tookPRN == true)

        try repo.setPRN(false, on: day)
        #expect(try repo.factorDays().isEmpty)
    }

    // Untaking a med removes only that med's reading.
    @Test func setMedicationTaken_false_removesOnlyThatReading() throws {
        let repo = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        let a = try repo.addMedication(name: "A", note: nil)
        let b = try repo.addMedication(name: "B", note: nil)
        try repo.setMedicationTaken(medID: a, taken: true, on: day)
        try repo.setMedicationTaken(medID: b, taken: true, on: day)
        try repo.setMedicationTaken(medID: a, taken: false, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(!fd.medicationsTaken.contains(a))
        #expect(fd.medicationsTaken.contains(b))
    }
}
