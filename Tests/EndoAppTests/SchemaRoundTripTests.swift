// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema (round-trip persistence).
// State: RED (Wave 0). Turned GREEN by Plan 03 when SchemaV1 + the @Model types
//        (DayEntry, BleedingEvent, TreatmentState) and makeModelContainer(inMemory:) exist.
//
// These tests intentionally FAIL now: the production symbols they will assert against do not
// exist yet. Each real assertion is kept compiling behind a `#expect(Bool(false), ...)` RED
// placeholder; Plan 03 replaces the placeholder with the in-memory ModelContainer round-trip and
// the suppressed-no-bleeding fixture (the #1 foundational pitfall guard — no phantom cycle field).
import Testing
import Foundation
@testable import EndoApp

@Suite("DATA-01 schema round-trip")
struct SchemaRoundTripTests {

    // (a) A DayEntry round-trips through an in-memory ModelContainer (id/note preserved).
    @Test func dayEntry_roundTrips_throughInMemoryStore() async throws {
        // RED until Plan 03:
        //   let container = try makeModelContainer(inMemory: true)
        //   let ctx = ModelContext(container)
        //   let id = UUID()
        //   ctx.insert(SchemaV1.DayEntry(id: id, note: "hello"))
        //   try ctx.save()
        //   let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        //   #expect(fetched.first?.id == id)
        //   #expect(fetched.first?.note == "hello")
        #expect(Bool(false), "RED — implement DayEntry round-trip in Plan 03")
    }

    // (b) A BleedingEvent round-trips (first-class record, independent of any cycle — D-19).
    @Test func bleedingEvent_roundTrips_throughInMemoryStore() async throws {
        // RED until Plan 03: insert a SchemaV1.BleedingEvent, save, fetch, assert id/occurredAt preserved.
        #expect(Bool(false), "RED — implement BleedingEvent round-trip in Plan 03")
    }

    // (c) A "suppressed, no bleeding for 8 months" fixture persists with NO phantom cycle field.
    //     Guards the 28-day-cycle foundational pitfall: TreatmentState mode "suppressed" is first-class.
    @Test func suppressedFixture_persists_withNoPhantomCycleField() async throws {
        // RED until Plan 03: persist a TreatmentState(modeRaw: "suppressed") with no BleedingEvent
        // for months; assert it round-trips and that no required cycle column exists on the schema.
        #expect(Bool(false), "RED — implement suppressed-no-bleeding fixture in Plan 03")
    }
}
