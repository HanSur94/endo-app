// Requirement: DATA-05 (Phase 9) — the injectable HealthKit boundary seam. EVERY HealthKit touch in
// the app goes through this protocol; the real `HKHealthStore`-backed conformer (and its actor
// isolation) lands in 09-02, and a deterministic fixture conformer behind a `-uitest-health-*` launch
// arg makes the whole import path E2E-testable on the Simulator (RESEARCH Pattern 2).
//
// SWIFT 6 SEAM CONTRACT (RESEARCH Pitfall 2 / threat T-09-04): the protocol is `Sendable`, and the only
// `HK*` type it references is `HKQueryAnchor` — an OPAQUE, resumable cursor (NSSecureCoding) treated as
// a black box by callers. Every ESCAPING position is a value type: `[ImportedSampleValue]` (Sendable
// value structs), `[UUID]` (deletions), and the opaque anchor. No `HKSample`/`HKHealthStore` ever
// crosses this boundary — the real conformer maps samples to values INSIDE its isolation domain before
// returning.
//
// READ-STATUS HONESTY CONTRACT (RESEARCH Pitfall 1, the SC3 anchor): a successful return from
// `requestAuthorization(for:)` does NOT mean "granted" for READ types — Apple deliberately hides read
// authorization status so an app cannot probe what health data a user has. "Denied", "not determined",
// and "no data" are INDISTINGUISHABLE for reads. Callers therefore MUST NOT branch on
// `authorizationStatus(for:)` to decide whether to show data: they attempt the `pull`, and if it
// returns nothing they contribute nothing (leave `DaySnapshot` fields nil — NEVER coerce absence to 0).
// A denied/empty read and a granted-but-empty read are handled IDENTICALLY.
import Foundation
import HealthKit

/// The app-side HealthKit boundary (DATA-05). Injected like `Repository`/`NotificationScheduler`; the
/// real conformer wraps a non-`Sendable` `HKHealthStore` inside an isolation domain (09-02), and a
/// fixture conformer feeds deterministic samples for tests (09-02 / UI tests).
///
/// `Sendable` so the seam can be held across isolation domains. The opaque `HKQueryAnchor?` is the only
/// `HK*` type that appears — a black-box cursor, never inspected by callers.
protocol HealthDataReading: Sendable {

    /// Requests READ access for the given domain's types (D-05/D-06; read-only — `toShare: []`). Called
    /// ONLY after the in-app rationale "Continue" (D-06). A successful return does NOT imply the user
    /// granted reads — read status is hidden (Pitfall 1). Callers attempt `pull` next regardless.
    func requestAuthorization(for domain: HealthDomain) async throws

    /// Requests READ access for the UNION of EVERY domain's read types in ONE call (WR-05; read-only —
    /// `toShare: []`). This is the Apple-recommended pattern: a single `requestAuthorization` for all read
    /// types presents at most ONE system sheet, instead of up to four sequential per-domain prompts. Called
    /// ONLY after the in-app rationale "Continue" (D-06); a successful return does NOT imply a grant
    /// (Pitfall 1). Callers then loop `importNew(for:)` per domain.
    func requestAuthorization() async throws

    /// Pulls new/changed samples for `domain` since `anchor` (D-09 incremental; `anchor == nil` ⇒ the
    /// D-13 first-grant 365-day backfill, decided inside the conformer). Returns the Sendable triple:
    ///   - `values`:    mapped `ImportedSampleValue` structs (NO `HKSample` escapes — Pitfall 2)
    ///   - `deleted`:   the `HKSample.uuid`s of samples HealthKit reports as deleted (Pitfall 5)
    ///   - `newAnchor`: the resumable cursor to persist for the next incremental pull (D-09)
    /// An empty `values` means "contribute nothing" — it is NOT a signal of "no symptoms" (Pitfall 1).
    func pull(
        _ domain: HealthDomain,
        since anchor: HKQueryAnchor?
    ) async throws -> (values: [ImportedSampleValue], deleted: [UUID], newAnchor: HKQueryAnchor?)
}
