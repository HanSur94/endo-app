// Requirement: DATA-05 / D-09 / D-13 (Phase 9, Plan 09-03) — the REAL `HealthDataReading`: the only
// conformer that touches an actual `HKHealthStore`. It is the production counterpart to the 09-02
// `FixtureHealthDataReading` test double; `AppDependencies` selects this one on a normal launch and the
// fixture under `-uitest-health-*`.
//
// SWIFT 6 ISOLATION (RESEARCH Pattern 1 / Pitfall 2 / threat T-09-04+T-09-11): `HKHealthStore` and
// `HKSample` are NOT `Sendable`. They MUST never escape an isolation domain. This file wraps the store
// in a dedicated `@globalActor` (`HealthStoreActor`) so every store touch is serialized on one isolated
// executor, and the anchored query's results handler MAPS `HKSample` → `Sendable` `ImportedSampleValue`
// BEFORE resuming the continuation (Pitfall 2). The only `HK*` value that crosses the seam is the opaque
// `HKQueryAnchor` cursor — exactly as the `HealthDataReading` contract promises.
//
// READ-ONLY (threat T-09-14): `requestAuthorization` passes `toShare: []` — never a write/share set —
// matching the read-only entitlement + the share-only Info.plist usage string. We do NOT request, and
// the app does NOT carry, any HealthKit write capability (write-back is PLAT-01, deferred).
//
// READ-STATUS HONESTY (Pitfall 1, SC3): a successful `requestAuthorization` return does NOT mean
// "granted" for reads — Apple hides read status. We NEVER branch on `authorizationStatus(for:)` for
// reads. We attempt the `pull`; an empty result contributes nothing (every snapshot field stays nil),
// which is NOT a "no symptoms" signal.
//
// AVAILABILITY (RESEARCH): `HKHealthStore.isHealthDataAvailable()` is false on iPad/Mac/unsupported
// devices — there `requestAuthorization` no-ops and `pull` returns empty, so the app stays fully usable.
//
// SDK NOTE (RESEARCH A4): the `requestAuthorization(toShare:read:)` async overload (iOS 15+) and the
// `HKAnchoredObjectQuery` results-handler init are confirmed against the Xcode 26 / iOS 18 SDK at build.
import Foundation
import HealthKit

/// The dedicated isolation domain for every `HKHealthStore` touch (RESEARCH Pattern 1). A `@globalActor`
/// (rather than wrapping the store inside `SystemHealthDataReading` itself as an actor) lets the store be
/// a single, serialized, app-wide resource while `SystemHealthDataReading` stays a plain `Sendable` seam.
@globalActor
actor HealthStoreActor {
    static let shared = HealthStoreActor()
}

/// The real, `HKHealthStore`-backed `HealthDataReading` (DATA-05). `Sendable` so it can be held across
/// isolation domains like the fixture; the non-`Sendable` store lives entirely behind `@HealthStoreActor`.
final class SystemHealthDataReading: HealthDataReading {

    /// The one `HKHealthStore`. Isolated to `@HealthStoreActor` so it is only ever touched on that
    /// actor's executor — it never escapes, satisfying the Swift 6 non-`Sendable` constraint (Pitfall 2).
    @HealthStoreActor private let store = HKHealthStore()

    init() {}

    /// Request READ access for `domain` (D-05/D-06; read-only — `toShare: []`). Called ONLY after the
    /// in-app rationale "Continue" (D-06). On a device with no HealthKit (`isHealthDataAvailable()` false)
    /// this is a no-op so the app stays usable. A successful return does NOT imply a grant (Pitfall 1) —
    /// the caller proceeds to `pull` regardless.
    @HealthStoreActor
    func requestAuthorization(for domain: HealthDomain) async throws {
        guard HKHealthStore.isHealthDataAvailable() else { return }   // iPad/Mac/unsupported → no-op.
        // READ-ONLY (T-09-14): toShare is empty. We never request write access.
        try await store.requestAuthorization(toShare: [], read: domain.readTypes)
    }

    /// Pull new/changed samples for `domain` since `anchor` (D-09 incremental). `anchor == nil` ⇒ the
    /// D-13 first-grant 365-day bounded backfill; otherwise incremental from the cursor. The anchored
    /// query's results handler maps every `HKSample` → `Sendable` `ImportedSampleValue` and the deletions
    /// to their UUIDs BEFORE resuming the continuation, so NO `HKSample`/`HKHealthStore` ever escapes
    /// (Pitfall 2). Returns the Sendable triple the seam contract promises.
    @HealthStoreActor
    func pull(
        _ domain: HealthDomain,
        since anchor: HKQueryAnchor?
    ) async throws -> (values: [ImportedSampleValue], deleted: [UUID], newAnchor: HKQueryAnchor?) {
        // No HealthKit on this device → contribute nothing (app stays usable, SC3). Carry the anchor
        // through unchanged so we never fabricate a cursor advance.
        guard HKHealthStore.isHealthDataAvailable() else { return ([], [], anchor) }

        let sampleType = domain.sampleType
        // D-13 first-grant backfill window (last 365 days) when there is no anchor; else D-09 incremental
        // (a nil predicate lets the anchor define the delta). The store + samples never leave this domain.
        let predicate: NSPredicate? = anchor == nil
            ? HKQuery.predicateForSamples(
                withStart: Calendar.current.date(byAdding: .day, value: -365, to: .now),
                end: nil,
                options: [])
            : nil

        let healthStore = store   // bound on @HealthStoreActor; the query executes on this isolated store.
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKAnchoredObjectQuery(
                type: sampleType,
                predicate: predicate,
                anchor: anchor,
                limit: HKObjectQueryNoLimit
            ) { _, samples, deleted, newAnchor, error in
                if let error {
                    // T-09-07: surface the error to the caller (which swallows it gracefully); no contents.
                    continuation.resume(throwing: error)
                    return
                }
                // MAP TO VALUE TYPES HERE — inside the handler, before resuming. No HKSample escapes
                // (Pitfall 2). `compactMap` drops any sample that maps to nothing (e.g. a "no flow" value),
                // so absence is never coerced into a fabricated value (SC3 honesty at the mapping edge).
                let values = (samples ?? []).compactMap { Self.value(from: $0, domain: domain) }
                // Pitfall 5: honor HealthKit-reported deletions by their stable UUIDs.
                let deletedUUIDs = (deleted ?? []).map(\.uuid)
                continuation.resume(returning: (values, deletedUUIDs, newAnchor))
            }
            healthStore.execute(query)
        }
    }

    // MARK: - Pure HKSample → ImportedSampleValue mapping (runs INSIDE the isolation domain)

    /// Map one `HKSample` to a `Sendable` `ImportedSampleValue` for `domain`, or `nil` when the sample
    /// carries nothing this domain represents (e.g. a "no flow" menstrual value, or a wrong-type sample).
    /// Delegates the value decoding to the pure 09-01 `HealthSampleMapping` tables (D-14/D-15/D-16). This
    /// runs on `@HealthStoreActor` inside the query handler — it is the LAST place an `HKSample` is read.
    private static func value(from sample: HKSample, domain: HealthDomain) -> ImportedSampleValue? {
        let day = Calendar.current.startOfDay(for: sample.startDate)

        switch domain {
        case .cycle:
            // Menstrual flow (D-14): a category sample whose value maps to a FlowIntensity (or nil for
            // "no flow"/unknown — never fabricated).
            guard let category = sample as? HKCategorySample else { return nil }
            guard let flow = HealthSampleMapping.flowIntensity(fromMenstrualFlowValue: category.value)
            else { return nil }
            return ImportedSampleValue(
                hkUUID: sample.uuid, day: day, domain: .cycle,
                payload: .cycle(flowRaw: flow.rawValue))

        case .sleep:
            // Sleep (D-15): one sleep-analysis segment → nightly asleep hours + coarse quality. Each
            // asleep segment contributes its own duration; in-bed/awake segments map to 0 asleep hours and
            // are dropped (they are not "asleep"). The per-day rollup across segments is the merge's job
            // (the dedupe-by-uuid store keeps each segment row; the merge sums them) — here we surface the
            // single segment's asleep hours, mirroring the FixtureHealthDataReading's per-value shape.
            guard let category = sample as? HKCategorySample else { return nil }
            let segment = SleepSegmentValue(
                value: category.value,
                duration: category.endDate.timeIntervalSince(category.startDate))
            let hours = HealthSampleMapping.nightlyAsleepHours([segment])
            // A non-asleep segment (inBed/awake/unknown) sums to 0 asleep hours — contribute nothing so we
            // never report a fabricated "0 hours asleep" (absence, not a logged zero, SC3).
            guard hours > 0 else { return nil }
            return ImportedSampleValue(
                hkUUID: sample.uuid, day: day, domain: .sleep,
                payload: .sleep(hours: hours,
                                quality: HealthSampleMapping.sleepQuality(forAsleepHours: hours)))

        case .workouts:
            // Workouts (D-15): HKWorkout → ActivityType + minutes (duration / 60).
            guard let workout = sample as? HKWorkout else { return nil }
            let type = HealthSampleMapping.activityType(from: workout.workoutActivityType)
            let minutes = workout.duration / 60.0
            return ImportedSampleValue(
                hkUUID: sample.uuid, day: day, domain: .workouts,
                payload: .workout(type: type, minutes: minutes))

        case .nutrition:
            // Nutrition (D-16): presence-only "ate that day". A dietary-energy sample's mere EXISTENCE is
            // the signal — one sample ⇒ ateThatDay == true. The per-day presence rollup (any sample that
            // day ⇒ true) is the merge's job; here each sample is a positive presence marker. NEVER a
            // fabricated FoodTag or micronutrient claim.
            guard sample is HKQuantitySample else { return nil }
            return ImportedSampleValue(
                hkUUID: sample.uuid, day: day, domain: .nutrition,
                payload: .nutrition(ateThatDay: HealthSampleMapping.ateThatDay(dietaryEnergySampleCount: 1)))
        }
    }
}
