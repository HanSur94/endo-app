// Requirement: DATA-05 (Phase 9) — the Sendable value contracts that cross the `HealthDataReading`
// seam. NOTHING here references an `HK*` type: this file imports ONLY Foundation (RESEARCH Pitfall 2 /
// threat T-09-04). A non-`Sendable` `HKSample`/`HKHealthStore` must NEVER escape the isolation domain —
// the reader maps samples to THESE structs BEFORE resuming its continuation, so only value types,
// UUIDs, and the opaque anchor leave the boundary.
//
// PROVENANCE (D-03): `ValueSource` is the per-field "where did this come from" flag the merge in 09-02
// stamps onto each `DaySnapshot` field so the UI can render the "from Apple Health" badge.
import Foundation

/// Per-field provenance flag (D-03). A `DaySnapshot` field carries this so the UI knows whether a value
/// was logged manually or imported from Apple Health. (`.none` — "neither present" — is modeled at the
/// merge site, not here; the source of a present value is one of these two cases.)
enum ValueSource: String, Sendable {
    case manual
    case appleHealth
}

/// The value-typed payload for one imported sample, one case per `HealthDomain`. Every associated value
/// is a plain `Sendable` value (no `HK*`). `Equatable` so the mapping tests can assert exact outputs.
enum ImportedPayload: Sendable, Equatable {
    /// Cycle (D-14): the `FlowIntensity` raw string (e.g. "light"/"medium"). Stored raw so an
    /// unrecognized HK flow value can be dropped upstream (mapping returns nil) rather than fabricated.
    case cycle(flowRaw: String)
    /// Sleep (D-15): nightly asleep hours + the coarse mapped quality.
    case sleep(hours: Double, quality: SleepQuality)
    /// Workouts (D-15): the mapped `ActivityType` + workout minutes.
    case workout(type: ActivityType, minutes: Double)
    /// Nutrition (D-16): presence-only — `true` means dietary-energy samples existed that day
    /// ("ate that day"). NEVER a fabricated FoodTag or a micronutrient breakdown.
    case nutrition(ateThatDay: Bool)
}

/// One imported sample as it crosses the seam — a pure `Sendable` value (RESEARCH Pitfall 2). The
/// reader produces these; the persistence layer (09-02) upserts them into `ImportedSample` rows keyed
/// by `hkUUID` (the stable `HKSample.uuid` dedupe key — RESEARCH Pitfall 5).
struct ImportedSampleValue: Sendable, Equatable {
    /// The stable `HKSample.uuid` — the dedupe key so re-pulls / cross-device mirroring collapse
    /// harmlessly (RESEARCH Pitfall 5). The only thing tying this value back to a HealthKit sample.
    let hkUUID: UUID
    /// The start-of-day bucket (`Calendar.startOfDay`) the sample belongs to — the merge key.
    let day: Date
    /// Which domain produced this sample.
    let domain: HealthDomain
    /// The value-typed payload (one case per domain).
    let payload: ImportedPayload
}
