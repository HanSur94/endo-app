// Requirement: SYMP-01 — log endo-specific symptoms with severity (D-01, D-02).
//
// State: GREEN at the persistence level (Plan 02-01 Task 3). A SymptomReading round-trips type +
// 0–10 severity through the in-memory SchemaV2 container, and a reading with `severity: nil`
// persists — proving absence ≠ "0/healthy" (D-02 honesty principle). The UI write path turns the
// E2E GREEN later (Plan 02-02 Task 2).
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("SYMP-01 symptom logging")
struct SymptomLoggingTests {

    // A SymptomReading round-trips type + severity through the in-memory SchemaV2 store.
    @Test func symptomReading_roundTrips_typeAndSeverity() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let id = UUID()
        let occurredOn = Date(timeIntervalSince1970: 1_700_000_000)
        ctx.insert(SchemaV1.SymptomReading(
            id: id, occurredOn: occurredOn, typeRaw: SymptomType.fatigue.rawValue, severity: 6))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(fetched.count == 1)
        #expect(fetched.first?.id == id)
        #expect(fetched.first?.occurredOn == occurredOn)
        #expect(fetched.first?.typeRaw == "fatigue")
        #expect(fetched.first?.severity == 6)
        // The stored raw decodes back to the catalog type defensively.
        #expect(SymptomType(raw: fetched.first?.typeRaw) == .fatigue)
    }

    // D-02 honesty: a reading with NO severity persists with `severity == nil` — absence is NOT "0".
    @Test func symptomReading_absentSeverity_isNil_notZero() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        ctx.insert(SchemaV1.SymptomReading(typeRaw: SymptomType.fatigue.rawValue, severity: nil))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(fetched.count == 1)
        #expect(fetched.first?.severity == nil,
                "Absent severity must persist as nil — never coerced to 0/healthy (D-02).")
    }

    // A PainPoint round-trips region + severity (SYMP-02 persistence; binding tested separately).
    @Test func painPoint_roundTrips_regionAndSeverity() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let id = UUID()
        ctx.insert(SchemaV1.PainPoint(
            id: id, regionRaw: BodyRegion.lowerAbdomenPelvis.rawValue, severity: 4))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(fetched.count == 1)
        #expect(fetched.first?.id == id)
        #expect(fetched.first?.regionRaw == "lowerAbdomenPelvis")
        #expect(fetched.first?.severity == 4)
        #expect(BodyRegion(raw: fetched.first?.regionRaw) == .lowerAbdomenPelvis)
    }
}
