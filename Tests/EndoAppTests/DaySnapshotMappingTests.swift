// Requirement: INST-01 / D-10 / D-14 — the app-side @Model → DaySnapshot mapping seam.
//
// `Repository.daySnapshots()` is the ONLY place SwiftData `@Model` rows become `EndoInsights.DaySnapshot`
// value snapshots (SC1: the package never touches the store). This suite proves the mapping is honest
// (absence ≠ 0), consent-gated, Calendar-bucketed, defensive on corrupt raws, and — the Option-D drift
// guard — that every app-domain enum case has a package-enum case with the SAME raw value (and vice
// versa). It mirrors FactorSnapshotTests' in-memory ModelContainer setup; no CloudKit / network / UI.
import Testing
import Foundation
import SwiftData
@testable import EndoApp
import EndoInsights

@Suite("D-10/D-14 DaySnapshot mapping honesty + Option-D enum parity")
struct DaySnapshotMappingTests {

    private func makeRepo() throws -> (SwiftDataRepository, ModelContext) {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        return (SwiftDataRepository(context: ctx), ctx)
    }

    // A stable, calendar-safe anchor (mirrors FactorSnapshotTests).
    private let day0 = Date(timeIntervalSince1970: 1_700_000_000)

    // MARK: - Bucketing

    // Readings/events/factors on the SAME calendar day collapse into ONE snapshot keyed by start-of-day.
    @Test func sameDay_collapsesIntoOneSnapshot() throws {
        let (repo, _) = try makeRepo()
        try repo.upsertSymptom(type: .fatigue, severity: 3, on: day0)
        try repo.setPainPoint(region: .lowerBack, severity: 5, on: day0)
        try repo.setBleeding(intensity: .light, clots: false, on: day0)
        try repo.setStress(4, on: day0)

        let snaps = try repo.daySnapshots()
        #expect(snaps.count == 1, "All signals on one calendar day collapse into a single DaySnapshot.")
        let snap = try #require(snaps.first)
        #expect(snap.day == Calendar.current.startOfDay(for: day0), "Snapshot is keyed by start-of-day.")
    }

    // Different days → distinct snapshots, sorted ascending by day.
    @Test func differentDays_distinctSnapshots_sortedAscending() throws {
        let (repo, _) = try makeRepo()
        let dayLater = day0.addingTimeInterval(3 * 24 * 60 * 60)   // +3 days
        try repo.setStress(2, on: dayLater)
        try repo.setStress(7, on: day0)

        let snaps = try repo.daySnapshots()
        #expect(snaps.count == 2, "Two distinct calendar days produce two snapshots.")
        #expect(snaps.map(\.day) == snaps.map(\.day).sorted(), "Snapshots are sorted ascending by day.")
        #expect(snaps.first?.stress == 7, "Earliest day comes first.")
        #expect(snaps.last?.stress == 2, "Latest day comes last.")
    }

    // MARK: - Symptoms

    @Test func symptom_mapsTypeAndSeverity() throws {
        let (repo, _) = try makeRepo()
        try repo.upsertSymptom(type: .brainFog, severity: 6, on: day0)

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.symptoms.count == 1)
        let point = try #require(snap.symptoms.first)
        #expect(point.type == EndoInsights.SymptomType.brainFog, "Symptom type maps to the package enum.")
        #expect(point.severity == 6, "Severity round-trips.")
    }

    // A logged symptom with no severity stays nil — present-without-severity, never 0.
    @Test func symptom_nilSeverity_staysNil() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        // Inject a reading with a nil severity directly (the upsert setter always clamps to a value).
        ctx.insert(SchemaV1.SymptomReading(occurredOn: anchor, typeRaw: EndoApp.SymptomType.fatigue.rawValue, severity: nil))
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        let point = try #require(snap.symptoms.first)
        #expect(point.severity == nil, "A logged-present symptom with no severity reads back nil, never 0.")
    }

    // MARK: - Pain

    @Test func pain_mapsRegionAndSeverity() throws {
        let (repo, _) = try makeRepo()
        try repo.setPainPoint(region: .bladder, severity: 8, on: day0)

        let snap = try #require(try repo.daySnapshots().first)
        let point = try #require(snap.pain.first)
        #expect(point.region == EndoInsights.BodyRegion.bladder, "Pain region maps to the package enum.")
        #expect(point.severity == 8)
    }

    // An unknown/unmapped region is DROPPED, never crashed (BodyRegion.init?(raw:) → nil).
    @Test func pain_unknownRegion_isDropped_noCrash() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        ctx.insert(SchemaV1.PainPoint(occurredOn: anchor, regionRaw: "not-a-real-region", severity: 4))
        ctx.insert(SchemaV1.PainPoint(occurredOn: anchor, regionRaw: EndoApp.BodyRegion.hips.rawValue, severity: 2))
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.pain.count == 1, "The unknown-region point is dropped; the valid one survives.")
        #expect(snap.pain.first?.region == EndoInsights.BodyRegion.hips)
    }

    // MARK: - Bleeding

    @Test func bleeding_flowAndClots_mapped() throws {
        let (repo, _) = try makeRepo()
        try repo.setBleeding(intensity: .heavy, clots: true, on: day0)

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.flow == EndoInsights.FlowIntensity.heavy, "Flow maps to the package enum.")
        #expect(snap.clots == true, "Clots reflect the event.")
    }

    // nil clots on the stored event coalesce to false (event injected directly with clots == nil).
    @Test func bleeding_nilClots_coalescesToFalse() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        ctx.insert(SchemaV1.BleedingEvent(occurredAt: anchor, flowRaw: EndoApp.FlowIntensity.light.rawValue, clots: nil))
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.flow == EndoInsights.FlowIntensity.light)
        #expect(snap.clots == false, "A nil stored clots flag coalesces to false.")
    }

    // A day with NO bleeding event → flow == nil (honest absence).
    @Test func bleeding_noEvent_flowIsNil() throws {
        let (repo, _) = try makeRepo()
        try repo.setStress(3, on: day0)   // the day exists, but no bleeding logged

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.flow == nil, "No bleeding event ⇒ flow nil (frequency uses flow != nil).")
        #expect(snap.clots == false)
    }

    // MARK: - Factors honesty (absence ≠ 0) — lifted from FactorSnapshotTests onto DaySnapshot fields.

    @Test func unloggedStressAndMood_areNil_notZero() throws {
        let (repo, _) = try makeRepo()
        try repo.setSleep(hours: 8, quality: .good, on: day0)   // log something else

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.stress == nil, "Unlogged stress reads back nil — never coerced to 0 (D-07).")
        #expect(snap.mood == nil, "Unlogged mood reads back nil — never coerced (D-08).")
        #expect(snap.sleepHours == 8)
        #expect(snap.sleepQuality == EndoInsights.SleepQuality.good)
    }

    @Test func loggedStressZero_isZero_notNil() throws {
        let (repo, _) = try makeRepo()
        try repo.setStress(0, on: day0)

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.stress == 0, "A logged stress of 0 reads back 0, never nil (D-07).")
    }

    @Test func factorFields_mapToPackageEnums() throws {
        let (repo, _) = try makeRepo()
        try repo.setFood(tags: [.dairy, .caffeine], freeText: nil, on: day0)
        try repo.setActivity(type: .cardio, minutes: 30, intensity: .high, on: day0)

        let snap = try #require(try repo.daySnapshots().first)
        // Tag order within a day is not a contract guarantee (rows fetch unordered) — compare as a set.
        #expect(Set(snap.foodTags) == [EndoInsights.FoodTag.dairy, EndoInsights.FoodTag.caffeine])
        #expect(snap.activityType == EndoInsights.ActivityType.cardio)
        #expect(snap.activityMinutes == 30)
        #expect(snap.activityIntensity == EndoInsights.ActivityIntensity.high)
    }

    // MARK: - hasNote (presence only — never the prose)

    @Test func hasNote_reflectsPresenceOnly() throws {
        let (repo, _) = try makeRepo()
        try repo.setNote("a private jot", on: day0)
        try repo.setStress(1, on: day0.addingTimeInterval(24 * 60 * 60))   // a second day, no note

        let snaps = try repo.daySnapshots()
        #expect(snaps.first?.hasNote == true, "A day with a note → hasNote true.")
        #expect(snaps.last?.hasNote == false, "A day without a note → hasNote false.")
    }

    // MARK: - Suppressed propagation

    @Test func suppressed_propagatesOntoEverySnapshot() throws {
        let (repo, _) = try makeRepo()
        try repo.setStress(2, on: day0)
        try repo.setStress(5, on: day0.addingTimeInterval(24 * 60 * 60))

        try repo.setSuppressed(true)
        for snap in try repo.daySnapshots() {
            #expect(snap.suppressed == true, "After setSuppressed(true), every snapshot is suppressed.")
        }

        try repo.setSuppressed(false)
        for snap in try repo.daySnapshots() {
            #expect(snap.suppressed == false, "After setSuppressed(false), every snapshot is not suppressed.")
        }
    }

    // MARK: - Defensive decode

    @Test func unknownRaws_decodeDefensively_noCrash() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        // A factor with an unknown value sinks to .other (a present-but-uncategorized factor is real);
        // an unknown SYMPTOM type is DROPPED (see unknownSymptomType_isDropped_notFabricatedAsOther).
        // Log a known symptom alongside so a snapshot exists for the day.
        try repo.upsertSymptom(type: .fatigue, severity: 3, on: anchor)
        ctx.insert(SchemaV1.FactorReading(occurredOn: anchor, kindRaw: FactorKind.food.rawValue, valueRaw: "not-a-real-tag"))
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.foodTags == [EndoInsights.FoodTag.other], "Unknown food tag decodes to .other.")
    }

    // WR-01: a symptom row whose typeRaw is unknown/undecodable is DROPPED, NOT fabricated as a real
    // `.other` symptom (mirrors the pain-point unknown-region drop). Never invent a symptom signal.
    @Test func unknownSymptomType_isDropped_notFabricatedAsOther() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        // One undecodable-type row + one valid symptom on the same day.
        ctx.insert(SchemaV1.SymptomReading(occurredOn: anchor, typeRaw: "not-a-real-symptom", severity: 2))
        ctx.insert(SchemaV1.SymptomReading(occurredOn: anchor, typeRaw: EndoApp.SymptomType.fatigue.rawValue, severity: 4))
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.symptoms.count == 1, "The undecodable-type symptom row is dropped; the valid one survives.")
        #expect(snap.symptoms.first?.type == EndoInsights.SymptomType.fatigue)
        #expect(!snap.symptoms.contains { $0.type == .other },
                "An unknown typeRaw must NEVER be coerced to a fabricated .other symptom (WR-01).")
    }

    // WR-01: a symptom row with a nil typeRaw (e.g. a future note-only/partial CloudKit row) is DROPPED,
    // never materialized as a real `.other` symptom point.
    @Test func nilSymptomType_isDropped_notFabricatedAsOther() throws {
        let (repo, ctx) = try makeRepo()
        let anchor = Calendar.current.startOfDay(for: day0)
        ctx.insert(SchemaV1.SymptomReading(occurredOn: anchor, typeRaw: nil, severity: 2))
        // Keep the day alive with an unrelated signal.
        try repo.setStress(3, on: anchor)
        try ctx.save()

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.symptoms.isEmpty, "A nil-typeRaw symptom row is dropped, not fabricated as .other (WR-01).")
    }

    // MARK: - Option-D enum parity (no drift)
    //
    // For EACH app domain enum, every case's rawValue has a corresponding package-enum case with the
    // SAME rawValue — both directions. A future case added on one side without the other fails here.

    @Test func enumParity_symptomType() {
        let app = Set(EndoApp.SymptomType.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.SymptomType.allCases.map(\.rawValue))
        #expect(app == pkg, "SymptomType raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_bodyRegion() {
        let app = Set(EndoApp.BodyRegion.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.BodyRegion.allCases.map(\.rawValue))
        #expect(app == pkg, "BodyRegion raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_flowIntensity() {
        let app = Set(EndoApp.FlowIntensity.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.FlowIntensity.allCases.map(\.rawValue))
        #expect(app == pkg, "FlowIntensity raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_foodTag() {
        let app = Set(EndoApp.FoodTag.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.FoodTag.allCases.map(\.rawValue))
        #expect(app == pkg, "FoodTag raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_activityType() {
        let app = Set(EndoApp.ActivityType.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.ActivityType.allCases.map(\.rawValue))
        #expect(app == pkg, "ActivityType raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_activityIntensity() {
        let app = Set(EndoApp.ActivityIntensity.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.ActivityIntensity.allCases.map(\.rawValue))
        #expect(app == pkg, "ActivityIntensity raw values drifted between app and package (Option-D guard).")
    }

    @Test func enumParity_sleepQuality() {
        let app = Set(EndoApp.SleepQuality.allCases.map(\.rawValue))
        let pkg = Set(EndoInsights.SleepQuality.allCases.map(\.rawValue))
        #expect(app == pkg, "SleepQuality raw values drifted between app and package (Option-D guard).")
    }
}
