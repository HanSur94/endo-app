// Requirement: A1 schema-migration guard — a V1 store survives the lightweight V1→V2 migration
// with no data loss (closes the A1 nesting-identity risk in RESEARCH §Assumptions Log).
//
// State: GREEN (Plan 02-01 Task 3). This is the gate that PROVES the A1 entity-identity decision:
// the @Model classes stay nested under `extension SchemaV1 { … }` and are listed additively in
// SchemaV2; SwiftData keys entity identity on the class/entity name, so the V1→V2 lightweight
// migration is data-preserving. We build a store at the V1 schema ON DISK (in-memory would NOT
// exercise migration across a reopen), insert V1 rows, close it, then reopen the SAME file at V2 via
// EndoMigrationPlan and assert every V1 row is still present.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("A1 V1→V2 migration data-survival")
struct SchemaMigrationTests {

    @Test func v1Store_survivesLightweightMigrationToV2_withNoDataLoss() async throws {
        // A unique on-disk store URL in the temp dir; cleaned up at the end.
        let dir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("endo-migration-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let storeURL = dir.appendingPathComponent("store.sqlite")
        defer { try? FileManager.default.removeItem(at: dir) }

        // Known fixture values to assert survive the migration.
        let dayID = UUID()
        let bleedID = UUID()
        let bleedAt = Date(timeIntervalSince1970: 1_700_000_000)
        let suppressedStart = Date(timeIntervalSince1970: 1_690_000_000)

        // 1. Open a store at the V1 schema (NO migration plan) and write V1 rows, then close.
        do {
            let v1Schema = Schema(versionedSchema: SchemaV1.self)
            let v1Config = ModelConfiguration(schema: v1Schema, url: storeURL)
            let v1Container = try ModelContainer(for: v1Schema, configurations: v1Config)
            let ctx = ModelContext(v1Container)
            ctx.insert(SchemaV1.DayEntry(id: dayID, note: "v1 day"))
            ctx.insert(SchemaV1.BleedingEvent(id: bleedID, occurredAt: bleedAt, flowRaw: "medium"))
            ctx.insert(SchemaV1.TreatmentState(startedAt: suppressedStart, modeRaw: "suppressed"))
            try ctx.save()
            // v1Container goes out of scope here, releasing the store file.
        }

        // 2. Reopen the SAME store file at SchemaV2 via the lightweight migration plan.
        let v2Container = try makeModelContainer(url: storeURL)
        let ctx = ModelContext(v2Container)

        // 3. Assert ALL V1 rows survived, readable through the V2 schema.
        let days = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        #expect(days.count == 1)
        #expect(days.first?.id == dayID)
        #expect(days.first?.note == "v1 day")

        let bleeds = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(bleeds.count == 1)
        #expect(bleeds.first?.id == bleedID)
        #expect(bleeds.first?.occurredAt == bleedAt)
        #expect(bleeds.first?.flowRaw == "medium")
        // The additive `clots` column is present and defaults to nil for pre-existing V1 rows.
        #expect(bleeds.first?.clots == nil)

        let states = try ctx.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        #expect(states.count == 1)
        #expect(states.first?.modeRaw == "suppressed")
        #expect(states.first?.startedAt == suppressedStart)

        // 4. The new V2-only models are queryable post-migration (empty, but the entities exist).
        let symptoms = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(symptoms.isEmpty)
        let pains = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(pains.isEmpty)
    }
}
