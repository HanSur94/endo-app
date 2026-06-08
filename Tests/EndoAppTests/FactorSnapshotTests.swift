// Requirement: D-07 / D-08 — the factorDays() honesty contract: absence ≠ 0.
//
// GREEN (Plan 03-01 Task 2). The single most important honesty guard of the phase: an UNLOGGED
// stress/mood reads back `nil`, a LOGGED 0 reads back 0 — never coerced. Defensive decode of an
// unknown valueRaw must not crash.
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("D-07/D-08 factor snapshot honesty")
struct FactorSnapshotTests {

    private func makeRepo() throws -> (SwiftDataRepository, ModelContext) {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        return (SwiftDataRepository(context: ctx), ctx)
    }

    // D-07: a day with NO stress reading ⇒ FactorDay.stress == nil (never coerced to 0).
    @Test func unloggedStress_isNil_notZero() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        // Log SOMETHING ELSE so the day exists, but never touch stress.
        try repo.setSleep(hours: 8, quality: .good, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.stress == nil, "Unlogged stress must read back nil — never coerced to 0 (D-07).")
        #expect(fd.mood == nil, "Unlogged mood must read back nil — never coerced (D-08).")
    }

    // D-07: a logged stress of 0 reads back 0 (a real value, distinct from nil).
    @Test func loggedStressZero_isZero_notNil() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setStress(0, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.stress == 0, "A logged stress of 0 must read back 0, never nil (D-07).")
    }

    // D-08: mood clamps to 1...5 and round-trips.
    @Test func mood_clampsAndRoundTrips() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setMood(9, on: day)
        #expect(try repo.factorDays().first?.mood == 5, "Mood must clamp to 1...5 (D-08).")

        try repo.setMood(nil, on: day)
        #expect(try repo.factorDays().isEmpty, "nil mood clears the reading.")
    }

    // CR-02: a whitespace-only food note must NOT fabricate a note row — it collapses to nil, matching
    // setNote/commitNewMed. (Honesty: a blank-space paste never creates a logged food note.)
    @Test func whitespaceOnlyFoodNote_roundTripsToNil() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setFood(tags: [.dairy], freeText: "   \n  ", on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.foodNote == nil, "A whitespace-only food note must collapse to nil (CR-02).")
        #expect(fd.foodTags == [.dairy], "Tags are unaffected by a blank note.")
    }

    // CR-02: a real food note still round-trips, trimmed of surrounding whitespace.
    @Test func foodNote_trimsAndRoundTrips() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setFood(tags: [], freeText: "  late dinner  ", on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.foodNote == "late dinner", "A food note round-trips trimmed (CR-02).")
    }

    // T-03-02: defensive decode of an unknown stored valueRaw does not crash the snapshot.
    @Test func unknownValueRaw_decodesDefensively_noCrash() throws {
        let (repo, ctx) = try makeRepo()
        let day = Calendar.current.startOfDay(for: Date(timeIntervalSince1970: 1_700_000_000))
        // Inject a corrupt food row with an unknown tag raw + an unknown activity type.
        ctx.insert(SchemaV1.FactorReading(
            occurredOn: day, kindRaw: FactorKind.food.rawValue, valueRaw: "not-a-real-tag"))
        ctx.insert(SchemaV1.FactorReading(
            occurredOn: day, kindRaw: FactorKind.activity.rawValue, valueRaw: "not-a-real-type"))
        try ctx.save()

        let fd = try #require(try repo.factorDays().first)
        // Unknown food tag decodes to .other (FactorKind/FoodTag default-decode), never crashes.
        #expect(fd.foodTags == [.other])
        // Unknown activity type decodes to .other.
        #expect(fd.activityType == .other)
    }
}
