// Requirement: LIFE-01/02/03 — typed factor data round-trips through the Repository (D-11).
//
// GREEN at the persistence level (Plan 03-01 Task 2). Food tags + free-text, activity type/minutes/
// intensity, and sleep hours/quality round-trip through an in-memory SchemaV3 store via factorDays().
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("LIFE-01/02/03 factor logging")
struct FactorLoggingTests {

    private func makeRepo() throws -> (SwiftDataRepository, ModelContext) {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        return (SwiftDataRepository(context: ctx), ctx)
    }

    // LIFE-01: food tags + free-text round-trip via factorDays().
    @Test func food_tagsAndFreeText_roundTrip() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setFood(tags: [.dairy, .caffeine], freeText: "big lunch", on: day)

        let days = try repo.factorDays()
        #expect(days.count == 1)
        let fd = try #require(days.first)
        #expect(Set(fd.foodTags) == Set([.dairy, .caffeine]))
        #expect(fd.foodNote == "big lunch")
    }

    // LIFE-01: re-saving food replaces the prior day's tags (no accumulation).
    @Test func food_reSave_replacesPriorTags() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setFood(tags: [.dairy, .alcohol], freeText: nil, on: day)
        try repo.setFood(tags: [.caffeine], freeText: nil, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.foodTags == [.caffeine])
        #expect(fd.foodNote == nil)
    }

    // LIFE-01: a pathological free-text paste is truncated at the seam (T-03-01 DoS guard).
    @Test func food_freeText_isBounded() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setFood(tags: [], freeText: String(repeating: "x", count: 500), on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect((fd.foodNote?.count ?? 0) <= SwiftDataRepository.maxNoteLength)
    }

    // LIFE-02: activity type + minutes + intensity round-trip.
    @Test func activity_typeMinutesIntensity_roundTrip() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setActivity(type: .cardio, minutes: 30, intensity: .high, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.activityType == .cardio)
        #expect(fd.activityMinutes == 30)
        #expect(fd.activityIntensity == .high)
    }

    // LIFE-02: clearing activity (nil type) removes the reading.
    @Test func activity_nilType_clears() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setActivity(type: .walking, minutes: 20, intensity: .low, on: day)
        try repo.setActivity(type: nil, minutes: nil, intensity: nil, on: day)

        #expect(try repo.factorDays().isEmpty)
    }

    // LIFE-03: sleep hours + quality round-trip.
    @Test func sleep_hoursAndQuality_roundTrip() throws {
        let (repo, _) = try makeRepo()
        let day = Date(timeIntervalSince1970: 1_700_000_000)
        try repo.setSleep(hours: 7.5, quality: .good, on: day)

        let fd = try #require(try repo.factorDays().first)
        #expect(fd.sleepHours == 7.5)
        #expect(fd.sleepQuality == .good)
    }
}
