// Requirement: DATA-05 / D-02 / D-04 / SC3 (Phase 9) — the make-or-break manual-wins, per-field,
// nil-honest merge in `Repository.daySnapshots()`. These are the LOAD-BEARING honesty tests:
// `manualWinsOverImported` and `emptyImportLeavesFieldsNil` are non-skippable — manual entry ALWAYS
// wins, imported fills only ABSENT fields, and a denied/empty import NEVER reads as "no symptoms".
//
// The store is an in-memory SchemaV6 container (fast, deterministic, no Simulator HealthKit / iCloud);
// imported rows are written via the Phase-9 CRUD and merged into the SAME `[DaySnapshot]` the engine
// consumes — proving SC1 (imported data reaches the engine) and SC3 (empty/denied contributes nothing).
import Testing
import Foundation
import SwiftData
@testable import EndoApp
import EndoInsights

@Suite("DATA-05 manual-wins, per-field, nil-honest merge (D-02/D-04/SC3)")
struct HealthImportMergeTests {

    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    // A stable, calendar-safe anchor.
    private let day0 = Date(timeIntervalSince1970: 1_700_000_000)

    private func imported(_ payload: ImportedPayload, domain: HealthDomain, on day: Date) -> ImportedSampleValue {
        ImportedSampleValue(hkUUID: UUID(), day: day, domain: domain, payload: payload)
    }

    // MARK: - importedFillsAbsentManualFields (SC1)

    @Test func importedFillsAbsentManualFields() throws {
        let repo = try makeRepo()
        // No manual sleep/activity/flow/food on the day — only imported values.
        try repo.upsertImportedSample(imported(.sleep(hours: 7.5, quality: .good), domain: .sleep, on: day0))
        try repo.upsertImportedSample(imported(.workout(type: .cardio, minutes: 30), domain: .workouts, on: day0))
        try repo.upsertImportedSample(imported(.cycle(flowRaw: EndoApp.FlowIntensity.medium.rawValue), domain: .cycle, on: day0))
        try repo.upsertImportedSample(imported(.nutrition(ateThatDay: true), domain: .nutrition, on: day0))

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.sleepHours == 7.5)
        #expect(snap.sleepQuality == .good)
        #expect(snap.activityType == .cardio)
        #expect(snap.activityMinutes == 30)
        #expect(snap.flow == .medium)
        #expect(!snap.foodTags.isEmpty)   // nutrition presence → a food-presence signal
    }

    // MARK: - manualWinsOverImported (D-02, load-bearing)

    @Test func manualWinsOverImported() throws {
        let repo = try makeRepo()
        // Manual entries across sleep, activity, flow, food.
        try repo.setSleep(hours: 6, quality: .poor, on: day0)
        try repo.setActivity(type: .walking, minutes: 15, intensity: .low, on: day0)
        try repo.setBleeding(intensity: .light, clots: false, on: day0)
        try repo.setFood(tags: [.dairy], freeText: nil, on: day0)
        // Conflicting imported values on the SAME day — must be IGNORED (manual wins, D-02).
        try repo.upsertImportedSample(imported(.sleep(hours: 9, quality: .good), domain: .sleep, on: day0))
        try repo.upsertImportedSample(imported(.workout(type: .strength, minutes: 90), domain: .workouts, on: day0))
        try repo.upsertImportedSample(imported(.cycle(flowRaw: EndoApp.FlowIntensity.heavy.rawValue), domain: .cycle, on: day0))
        try repo.upsertImportedSample(imported(.nutrition(ateThatDay: true), domain: .nutrition, on: day0))

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.sleepHours == 6)              // MANUAL, not 9
        #expect(snap.sleepQuality == .poor)        // MANUAL, not .good
        #expect(snap.activityType == .walking)     // MANUAL, not .strength
        #expect(snap.activityMinutes == 15)        // MANUAL, not 90
        #expect(snap.flow == .light)               // MANUAL, not .heavy
        #expect(snap.foodTags == [.dairy])         // MANUAL only, no imported food signal added

        // Every winning field is sourced .manual in the provenance read.
        let sources = try #require(try repo.daySnapshotSources(on: day0))
        #expect(sources.sleep == .manual)
        #expect(sources.activity == .manual)
        #expect(sources.flow == .manual)
        #expect(sources.food == .manual)
    }

    // MARK: - emptyImportLeavesFieldsNil (SC3, the make-or-break)

    @Test func emptyImportLeavesFieldsNil() throws {
        let repo = try makeRepo()
        // A manual-only baseline: only a stress reading on the day, nothing else.
        try repo.setStress(4, on: day0)
        let baseline = try #require(try repo.daySnapshots().first)

        // A denied/empty import = ZERO ImportedSample rows. It must change NOTHING.
        try repo.replaceOrInsertImported(values: [], deletedUUIDs: [])

        let after = try #require(try repo.daySnapshots().first)
        // Every field that was nil stays nil — never 0, never a fabricated reading.
        #expect(after.sleepHours == nil)
        #expect(after.sleepQuality == nil)
        #expect(after.activityType == nil)
        #expect(after.activityMinutes == nil)
        #expect(after.flow == nil)
        #expect(after.foodTags.isEmpty)
        #expect(after == baseline)   // the empty import is a no-op against the manual baseline
    }

    // MARK: - importedDayJoinsTheUnion (SC1)

    @Test func importedDayJoinsTheUnion() throws {
        let repo = try makeRepo()
        let manualDay = day0
        let importedOnlyDay = day0.addingTimeInterval(5 * 24 * 60 * 60)   // +5 days, no manual signal
        try repo.setStress(3, on: manualDay)
        try repo.upsertImportedSample(imported(.sleep(hours: 8, quality: .okay), domain: .sleep, on: importedOnlyDay))

        let snaps = try repo.daySnapshots()
        #expect(snaps.count == 2, "An imported-only day still produces a DaySnapshot (SC1).")
        let importedSnap = try #require(snaps.first { Calendar.current.isDate($0.day, inSameDayAs: importedOnlyDay) })
        #expect(importedSnap.sleepHours == 8)
    }

    // MARK: - sourceFlagged (D-03)

    @Test func sourceFlagged() throws {
        let repo = try makeRepo()
        // Imported-only sleep — the field must be distinguishable as .appleHealth for the badge.
        try repo.upsertImportedSample(imported(.sleep(hours: 7, quality: .good), domain: .sleep, on: day0))

        let sources = try #require(try repo.daySnapshotSources(on: day0))
        #expect(sources.sleep == .appleHealth)
        // A field with neither manual nor imported data has no source (nil) — never fabricated.
        #expect(sources.flow == nil)
    }

    // MARK: - multiSegmentSleepSums (CR-01, load-bearing honesty)

    @Test func multiSegmentSleepSumsAndBandFollowsTotal() throws {
        let repo = try makeRepo()
        // Apple Watch records ONE night as MANY short sleep-analysis segments, each its own row
        // (distinct hkUUID). Six fragments summing to 7.5h must merge to 7.5h / .good — NOT a single
        // ~0.4h fragment read as .poor (the CR-01 regression). Quality is the band of the SUMMED total.
        let segments: [Double] = [1.5, 1.0, 2.0, 0.5, 1.5, 1.0]   // sums to 7.5h
        #expect(segments.reduce(0, +) == 7.5)
        for hours in segments {
            // The per-segment `quality` is advisory (each fragment alone would be .poor); the merge
            // recomputes the band from the night total, so we pass the per-fragment band the reader would.
            let fragmentBand = hours < 5 ? SleepQuality.poor : (hours < 7 ? .okay : .good)
            try repo.upsertImportedSample(
                imported(.sleep(hours: hours, quality: fragmentBand), domain: .sleep, on: day0))
        }

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.sleepHours == 7.5)        // SUMMED, not last-writer-wins on one fragment
        #expect(snap.sleepQuality == .good)    // band of the 7.5h TOTAL (>= 7.0 ceiling), not a fragment's .poor
    }

    // MARK: - replaceOrInsertImported batch (CR-02, atomic delete+upsert)

    @Test func sameUUIDDeleteThenReAddNetsToPresent() throws {
        let repo = try makeRepo()
        // Seed a sleep row with a known UUID.
        let uuid = UUID()
        let seed = ImportedSampleValue(hkUUID: uuid, day: day0, domain: .sleep,
                                       payload: .sleep(hours: 7.5, quality: .good))
        try repo.upsertImportedSample(seed)

        // A pull that reports the SAME UUID as both deleted AND re-added (the degenerate same-UUID case).
        // The single atomic transaction must net to "present" — the upsert re-creates the row.
        let readd = ImportedSampleValue(hkUUID: uuid, day: day0, domain: .sleep,
                                        payload: .sleep(hours: 8.0, quality: .good))
        try repo.replaceOrInsertImported(values: [readd], deletedUUIDs: [uuid])

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.sleepHours == 8.0)   // present, with the re-added value (not dropped by delete-first)
    }

    @Test func batchAppliesDeletionsAndUpsertsAsOneUnit() throws {
        let repo = try makeRepo()
        // Two existing rows; one will be deleted in the next pull, one will survive, plus a new insert.
        let keep = UUID(), drop = UUID(), add = UUID()
        try repo.upsertImportedSample(
            ImportedSampleValue(hkUUID: keep, day: day0, domain: .nutrition, payload: .nutrition(ateThatDay: true)))
        try repo.upsertImportedSample(
            ImportedSampleValue(hkUUID: drop, day: day0, domain: .workouts, payload: .workout(type: .cardio, minutes: 30)))

        try repo.replaceOrInsertImported(
            values: [ImportedSampleValue(hkUUID: add, day: day0, domain: .sleep, payload: .sleep(hours: 7, quality: .good))],
            deletedUUIDs: [drop])

        let rows = try repo.importedSamples(on: day0)
        let uuids = Set(rows.compactMap(\.hkSampleUUID))
        #expect(uuids.contains(keep.uuidString))     // untouched survivor
        #expect(!uuids.contains(drop.uuidString))    // honored deletion
        #expect(uuids.contains(add.uuidString))      // new insert — all in one atomic save
    }

    // MARK: - multiWorkoutCollapse (WR-04, deterministic)

    @Test func multiWorkoutSumsMinutesAndKeepsLongestType() throws {
        let repo = try makeRepo()
        // Two workouts on one day: a 20-min walk and a 45-min strength session. The day's imported
        // activity must be deterministic: minutes SUM to 65, type is the LONGEST session's (.strength) —
        // never an arbitrary last-writer-wins on whichever row iterates last.
        try repo.upsertImportedSample(imported(.workout(type: .walking, minutes: 20), domain: .workouts, on: day0))
        try repo.upsertImportedSample(imported(.workout(type: .strength, minutes: 45), domain: .workouts, on: day0))

        let snap = try #require(try repo.daySnapshots().first)
        #expect(snap.activityMinutes == 65)      // SUM of both sessions
        #expect(snap.activityType == .strength)  // the LONGEST session's type (deterministic)
    }
}
