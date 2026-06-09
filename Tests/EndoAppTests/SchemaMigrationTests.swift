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
            // Force `.none`: Phase 4 ships the CloudKit entitlement, so an unspecified cloudKitDatabase
            // would infer `.automatic` and mis-activate sync on this local-only migration store (P-2).
            let v1Config = ModelConfiguration(schema: v1Schema, url: storeURL, cloudKitDatabase: .none)
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

    // Phase 3 (Plan 03-01): the V2→V3 additive migration is data-preserving — a V2 store survives the
    // lightweight reopen at V3 with ZERO data loss, and the new FactorReading/Medication entities are
    // queryable (empty). This closes the A1 discipline for Phase-3 models before Phase 4 flips CloudKit.
    @Test func v2Store_survivesLightweightMigrationToV3_withNoDataLoss() async throws {
        let dir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("endo-migration-v3-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let storeURL = dir.appendingPathComponent("store.sqlite")
        defer { try? FileManager.default.removeItem(at: dir) }

        // Known V2 fixture values to assert survive the migration to V3.
        let dayID = UUID()
        let bleedID = UUID()
        let bleedAt = Date(timeIntervalSince1970: 1_700_000_000)
        let symptomID = UUID()
        let painID = UUID()
        let suppressedStart = Date(timeIntervalSince1970: 1_690_000_000)

        // 1. Open a store at the V2 schema (NO migration plan) and write V2 rows across every V2 model.
        do {
            let v2Schema = Schema(versionedSchema: SchemaV2.self)
            // Force `.none` (see V1 note above) — local-only migration store, no CloudKit mirroring (P-2).
            let v2Config = ModelConfiguration(schema: v2Schema, url: storeURL, cloudKitDatabase: .none)
            let v2Container = try ModelContainer(for: v2Schema, configurations: v2Config)
            let ctx = ModelContext(v2Container)
            ctx.insert(SchemaV1.DayEntry(id: dayID, note: "v2 day"))
            ctx.insert(SchemaV1.BleedingEvent(id: bleedID, occurredAt: bleedAt, flowRaw: "light", clots: true))
            ctx.insert(SchemaV1.TreatmentState(startedAt: suppressedStart, modeRaw: "naturallyCycling"))
            ctx.insert(SchemaV1.SymptomReading(id: symptomID, typeRaw: "fatigue", severity: 5))
            ctx.insert(SchemaV1.PainPoint(id: painID, regionRaw: "lowerAbdomenPelvis", severity: 3))
            try ctx.save()
            // v2Container goes out of scope here, releasing the store file.
        }

        // 2. Reopen the SAME store file at SchemaV3 via the lightweight migration plan (V2→V3).
        let v3Container = try makeModelContainer(url: storeURL)
        let ctx = ModelContext(v3Container)

        // 3. Assert ALL V2 rows survived, readable through the V3 schema.
        let days = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        #expect(days.count == 1)
        #expect(days.first?.id == dayID)
        #expect(days.first?.note == "v2 day")

        let bleeds = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(bleeds.count == 1)
        #expect(bleeds.first?.id == bleedID)
        #expect(bleeds.first?.occurredAt == bleedAt)
        #expect(bleeds.first?.flowRaw == "light")
        #expect(bleeds.first?.clots == true)

        let states = try ctx.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        #expect(states.count == 1)
        #expect(states.first?.modeRaw == "naturallyCycling")

        let symptoms = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(symptoms.count == 1)
        #expect(symptoms.first?.id == symptomID)
        #expect(symptoms.first?.severity == 5)

        let pains = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(pains.count == 1)
        #expect(pains.first?.id == painID)

        // 4. The new V3-only models are queryable post-migration (empty, but the entities exist).
        let factors = try ctx.fetch(FetchDescriptor<SchemaV1.FactorReading>())
        #expect(factors.isEmpty, "FactorReading must be queryable (empty) after V2→V3 migration.")
        let meds = try ctx.fetch(FetchDescriptor<SchemaV1.Medication>())
        #expect(meds.isEmpty, "Medication must be queryable (empty) after V2→V3 migration.")
    }

    // Phase 6 (Plan 06-03): the V3→V4 additive migration is data-preserving — a V3 store survives the
    // lightweight reopen at V4 with ZERO data loss (T-06-11), and the new DismissedInsight entity is
    // queryable (empty). Closes the A1 discipline for the Phase-6 model. Clones the V2→V3 case +1 version.
    @Test func v3Store_survivesLightweightMigrationToV4_withNoDataLoss() async throws {
        let dir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("endo-migration-v4-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let storeURL = dir.appendingPathComponent("store.sqlite")
        defer { try? FileManager.default.removeItem(at: dir) }

        // Known V3 fixture values to assert survive the migration to V4 (covers the V3-new models too).
        let dayID = UUID()
        let bleedID = UUID()
        let bleedAt = Date(timeIntervalSince1970: 1_700_000_000)
        let symptomID = UUID()
        let painID = UUID()
        let factorID = UUID()
        let factorAt = Date(timeIntervalSince1970: 1_695_000_000)
        let medID = UUID()
        let suppressedStart = Date(timeIntervalSince1970: 1_690_000_000)

        // 1. Open a store at the V3 schema (NO migration plan) and write V3 rows across every V3 model.
        do {
            let v3Schema = Schema(versionedSchema: SchemaV3.self)
            // Force `.none` (see V1 note above) — local-only migration store, no CloudKit mirroring (P-2).
            let v3Config = ModelConfiguration(schema: v3Schema, url: storeURL, cloudKitDatabase: .none)
            let v3Container = try ModelContainer(for: v3Schema, configurations: v3Config)
            let ctx = ModelContext(v3Container)
            ctx.insert(SchemaV1.DayEntry(id: dayID, note: "v3 day"))
            ctx.insert(SchemaV1.BleedingEvent(id: bleedID, occurredAt: bleedAt, flowRaw: "heavy", clots: false))
            ctx.insert(SchemaV1.TreatmentState(startedAt: suppressedStart, modeRaw: "naturallyCycling"))
            ctx.insert(SchemaV1.SymptomReading(id: symptomID, typeRaw: "fatigue", severity: 7))
            ctx.insert(SchemaV1.PainPoint(id: painID, regionRaw: "lowerAbdomenPelvis", severity: 4))
            ctx.insert(SchemaV1.FactorReading(id: factorID, occurredOn: factorAt, kindRaw: "stress", level: 6))
            ctx.insert(SchemaV1.Medication(id: medID, name: "Ibuprofen"))
            try ctx.save()
            // v3Container goes out of scope here, releasing the store file.
        }

        // 2. Reopen the SAME store file at SchemaV4 via the lightweight migration plan (V3→V4).
        let v4Container = try makeModelContainer(url: storeURL)
        let ctx = ModelContext(v4Container)

        // 3. Assert ALL V3 rows survived, readable through the V4 schema.
        let days = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        #expect(days.count == 1)
        #expect(days.first?.id == dayID)
        #expect(days.first?.note == "v3 day")

        let bleeds = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(bleeds.count == 1)
        #expect(bleeds.first?.id == bleedID)
        #expect(bleeds.first?.occurredAt == bleedAt)
        #expect(bleeds.first?.flowRaw == "heavy")
        #expect(bleeds.first?.clots == false)

        let states = try ctx.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        #expect(states.count == 1)
        #expect(states.first?.modeRaw == "naturallyCycling")

        let symptoms = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(symptoms.count == 1)
        #expect(symptoms.first?.id == symptomID)
        #expect(symptoms.first?.severity == 7)

        let pains = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(pains.count == 1)
        #expect(pains.first?.id == painID)

        let factors = try ctx.fetch(FetchDescriptor<SchemaV1.FactorReading>())
        #expect(factors.count == 1)
        #expect(factors.first?.id == factorID)
        #expect(factors.first?.kindRaw == "stress")
        #expect(factors.first?.level == 6)

        let meds = try ctx.fetch(FetchDescriptor<SchemaV1.Medication>())
        #expect(meds.count == 1)
        #expect(meds.first?.id == medID)

        // 4. The new V4-only model is queryable post-migration (empty, but the entity exists).
        let dismissed = try ctx.fetch(FetchDescriptor<SchemaV1.DismissedInsight>())
        #expect(dismissed.isEmpty, "DismissedInsight must be queryable (empty) after V3→V4 migration.")
    }

    // Phase 8 (Plan 08-01): the V4→V5 additive migration is data-preserving — a V4 store survives the
    // lightweight reopen at V5 with ZERO data loss (T-08-03), and the new CachedInsight entity is
    // queryable (empty). Closes the A1 discipline for the Phase-8 model. Clones the V3→V4 case +1 version.
    @Test func v4Store_survivesLightweightMigrationToV5_withNoDataLoss() async throws {
        let dir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("endo-migration-v5-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let storeURL = dir.appendingPathComponent("store.sqlite")
        defer { try? FileManager.default.removeItem(at: dir) }

        // Known V4 fixture values to assert survive the migration to V5 (covers the V4-new model too).
        let dayID = UUID()
        let bleedID = UUID()
        let bleedAt = Date(timeIntervalSince1970: 1_700_000_000)
        let symptomID = UUID()
        let painID = UUID()
        let factorID = UUID()
        let factorAt = Date(timeIntervalSince1970: 1_695_000_000)
        let medID = UUID()
        let dismissedID = UUID()
        let suppressedStart = Date(timeIntervalSince1970: 1_690_000_000)

        // 1. Open a store at the V4 schema (NO migration plan) and write V4 rows across every V4 model
        //    (INCLUDING a DismissedInsight row, per the plan's behavior spec).
        do {
            let v4Schema = Schema(versionedSchema: SchemaV4.self)
            // Force `.none` (see V1 note above) — local-only migration store, no CloudKit mirroring (P-2).
            let v4Config = ModelConfiguration(schema: v4Schema, url: storeURL, cloudKitDatabase: .none)
            let v4Container = try ModelContainer(for: v4Schema, configurations: v4Config)
            let ctx = ModelContext(v4Container)
            ctx.insert(SchemaV1.DayEntry(id: dayID, note: "v4 day"))
            ctx.insert(SchemaV1.BleedingEvent(id: bleedID, occurredAt: bleedAt, flowRaw: "medium", clots: true))
            ctx.insert(SchemaV1.TreatmentState(startedAt: suppressedStart, modeRaw: "suppressed"))
            ctx.insert(SchemaV1.SymptomReading(id: symptomID, typeRaw: "fatigue", severity: 8))
            ctx.insert(SchemaV1.PainPoint(id: painID, regionRaw: "lowerAbdomenPelvis", severity: 5))
            ctx.insert(SchemaV1.FactorReading(id: factorID, occurredOn: factorAt, kindRaw: "stress", level: 4))
            ctx.insert(SchemaV1.Medication(id: medID, name: "Ibuprofen"))
            ctx.insert(SchemaV1.DismissedInsight(id: dismissedID, signalKey: "cyclePhase.luteal.symptom.pelvicPain"))
            try ctx.save()
            // v4Container goes out of scope here, releasing the store file.
        }

        // 2. Reopen the SAME store file at SchemaV5 via the lightweight migration plan (V4→V5).
        let v5Container = try makeModelContainer(url: storeURL)
        let ctx = ModelContext(v5Container)

        // 3. Assert ALL V4 rows survived, readable through the V5 schema.
        let days = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
        #expect(days.count == 1)
        #expect(days.first?.id == dayID)
        #expect(days.first?.note == "v4 day")

        let bleeds = try ctx.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        #expect(bleeds.count == 1)
        #expect(bleeds.first?.id == bleedID)
        #expect(bleeds.first?.occurredAt == bleedAt)
        #expect(bleeds.first?.flowRaw == "medium")
        #expect(bleeds.first?.clots == true)

        let states = try ctx.fetch(FetchDescriptor<SchemaV1.TreatmentState>())
        #expect(states.count == 1)
        #expect(states.first?.modeRaw == "suppressed")

        let symptoms = try ctx.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        #expect(symptoms.count == 1)
        #expect(symptoms.first?.id == symptomID)
        #expect(symptoms.first?.severity == 8)

        let pains = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(pains.count == 1)
        #expect(pains.first?.id == painID)

        let factors = try ctx.fetch(FetchDescriptor<SchemaV1.FactorReading>())
        #expect(factors.count == 1)
        #expect(factors.first?.id == factorID)
        #expect(factors.first?.kindRaw == "stress")
        #expect(factors.first?.level == 4)

        let meds = try ctx.fetch(FetchDescriptor<SchemaV1.Medication>())
        #expect(meds.count == 1)
        #expect(meds.first?.id == medID)

        let dismissed = try ctx.fetch(FetchDescriptor<SchemaV1.DismissedInsight>())
        #expect(dismissed.count == 1, "The V4 DismissedInsight row must survive the V4→V5 migration.")
        #expect(dismissed.first?.id == dismissedID)
        #expect(dismissed.first?.signalKey == "cyclePhase.luteal.symptom.pelvicPain")

        // 4. The new V5-only model is queryable post-migration (empty, but the entity exists).
        let cached = try ctx.fetch(FetchDescriptor<SchemaV1.CachedInsight>())
        #expect(cached.isEmpty, "CachedInsight must be queryable (empty) after V4→V5 migration.")
    }
}
