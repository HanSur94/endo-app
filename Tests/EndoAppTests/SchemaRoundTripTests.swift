// Requirement: DATA-01 — CloudKit-safe, append-only SwiftData schema (round-trip persistence).
// State: GREEN (Plan 02). The production symbols (SchemaV1 + the @Model types DayEntry,
//        BleedingEvent, TreatmentState, and makeModelContainer(inMemory:)) now exist, so these
//        assertions exercise the real in-memory ModelContainer round-trip and the suppressed,
//        no-bleeding fixture (the #1 foundational pitfall guard — no phantom cycle field).
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("DATA-01 schema round-trip")
struct SchemaRoundTripTests {

    // (a) A DayEntry round-trips through an in-memory ModelContainer (id/note preserved).
    @Test func dayEntry_roundTrips_throughInMemoryStore() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let id = UUID()
        ctx.insert(SchemaV1.DayEntry(id: id, note: "hello"))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        #expect(fetched.count == 1)
        #expect(fetched.first?.id == id)
        #expect(fetched.first?.note == "hello")
    }

    // (b) A BleedingEvent round-trips (first-class record, independent of any cycle — D-19).
    @Test func bleedingEvent_roundTrips_throughInMemoryStore() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        let id = UUID()
        let occurredAt = Date(timeIntervalSince1970: 1_700_000_000)
        ctx.insert(SchemaV1.BleedingEvent(id: id, occurredAt: occurredAt, flowRaw: "medium"))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(fetched.count == 1)
        #expect(fetched.first?.id == id)
        #expect(fetched.first?.occurredAt == occurredAt)
        #expect(fetched.first?.flowRaw == "medium")
    }

    // (c) A "suppressed, no bleeding for 8 months" fixture persists with NO phantom cycle field.
    //     Guards the 28-day-cycle foundational pitfall: TreatmentState mode "suppressed" is
    //     first-class and coexists with ZERO BleedingEvents (D-19).
    @Test func suppressedFixture_persists_withNoPhantomCycleField() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)

        // A user suppressed for ~8 months: a first-class "suppressed" TreatmentState and NO
        // BleedingEvent for the period. This must persist without any cycle requirement.
        let startedAt = Calendar.current.date(byAdding: .month, value: -8, to: .now)!
        ctx.insert(SchemaV1.TreatmentState(startedAt: startedAt, modeRaw: "suppressed"))
        try ctx.save()

        let states = try ctx.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        #expect(states.count == 1)
        #expect(states.first?.modeRaw == "suppressed")

        // No phantom/implicit bleeding was created by persisting a suppression state.
        let bleeds = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(bleeds.isEmpty)

        // Structural guard: the model exposes no required cycle column — the only stored members of
        // TreatmentState are id / startedAt / modeRaw. (A `cycleDay`/`cyclePhase`/`cycleLength`
        // field would force a value here; its absence is what makes the suppressed fixture
        // representable. Enforced additionally by the grep acceptance check on the source.)
        let state = try #require(states.first)
        #expect(state.id != UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
        #expect(state.startedAt == startedAt)
    }

    // (d) CYCL-01: BleedingEvent additively carries an optional `clots` flag. It round-trips with
    //     `clots: true` AND with `clots: nil` (absence ≠ recorded false). flowRaw still round-trips.
    @Test func bleedingEvent_clots_roundTrips_trueAndNil() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)

        let withClots = UUID()
        let withoutClots = UUID()
        ctx.insert(SchemaV1.BleedingEvent(id: withClots, flowRaw: "heavy", clots: true))
        ctx.insert(SchemaV1.BleedingEvent(id: withoutClots, flowRaw: "spotting", clots: nil))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(fetched.count == 2)
        let a = try #require(fetched.first { $0.id == withClots })
        #expect(a.flowRaw == "heavy")
        #expect(a.clots == true)
        let b = try #require(fetched.first { $0.id == withoutClots })
        #expect(b.flowRaw == "spotting")
        #expect(b.clots == nil, "Absent clots must persist as nil — never coerced to false.")
    }

    // (e) CYCL-01/SYMP-01/02: the two new SchemaV2 models round-trip through the in-memory store.
    @Test func symptomReading_and_painPoint_roundTrip() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)

        let sID = UUID()
        let pID = UUID()
        ctx.insert(SchemaV1.SymptomReading(id: sID, typeRaw: "pelvicPain", severity: 7))
        ctx.insert(SchemaV1.PainPoint(id: pID, regionRaw: "lowerBack", severity: 3))
        try ctx.save()

        let symptoms = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(symptoms.count == 1)
        #expect(symptoms.first?.id == sID)
        #expect(symptoms.first?.typeRaw == "pelvicPain")
        #expect(symptoms.first?.severity == 7)

        let pains = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(pains.count == 1)
        #expect(pains.first?.id == pID)
        #expect(pains.first?.regionRaw == "lowerBack")
        #expect(pains.first?.severity == 3)
    }
}
