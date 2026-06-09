// Requirement: D-04 / INST-02 — the pure, launch-stable input fingerprint that drives the
// `InsightsCoordinator`'s cache hit/miss decision.
//
// The coordinator recomputes insights only when its INPUTS change. This fingerprint is the canonical,
// launch-stable summary of those inputs: the snapshot COUNT + the LATEST logged `day` (the edit
// timestamp) + the dismissed-set canonical. An unchanged fingerprint ⇒ cache HIT (load cached, no
// recompute); a changed fingerprint ⇒ cache MISS (recompute + persist).
//
// PITFALL 5 (load-bearing): the fingerprint MUST NOT use `Set.hashValue` / `Array.hashValue` — Swift
// seeds `Hashable` with per-process randomness, so a hash-based fingerprint would differ across launches
// and FORCE a recompute on every cold start (defeating the instant cached-load, D-04). It also MUST
// include the latest-edit timestamp, not just the count: editing the most recent day's severity does NOT
// change the count, yet the insights must recompute. We therefore build a CANONICAL string from stable,
// value-derived components only.
//
// PURITY (SC1 posture): Foundation-only, no I/O, no store access, no logging (T-08-05) — a pure value-in
// / string-out function. It takes the already-mapped `[DaySnapshot]` (the package value contract) so it
// never touches SwiftData.
import Foundation
import EndoInsights

/// The pure, launch-stable input fingerprint (D-04). `make` is a deterministic function of
/// `(snapshots, dismissed)` only — identical inputs always yield the identical string, across calls and
/// across launches.
enum InsightFingerprint {

    /// Build the canonical fingerprint string from the coordinator's inputs.
    ///
    /// Components (per D-04): `count | latestDayEpoch | dismissedCanonical`
    ///   - `count`            — the number of day snapshots (a new/removed day flips it).
    ///   - `latestDayEpoch`   — the MAX `day` as a `timeIntervalSince1970` (an edit to the most recent
    ///                          logged day flips it even when the count is unchanged — Pitfall 5).
    ///   - `dismissedCanonical` — the dismissed signalKeys SORTED and comma-joined, so set ORDER never
    ///                          affects the fingerprint and a dismiss/un-dismiss always flips it.
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent; only `count` + the max `day` matter).
    ///   - dismissed: the dismissed signalKeys to canonicalize into the fingerprint.
    /// - Returns: a stable, non-empty canonical string suitable for equality comparison against a cached
    ///   fingerprint.
    static func make(snapshots: [DaySnapshot], dismissed: Set<String>) -> String {
        // `.max()` of an empty array is nil → `.distantPast` keeps the empty case well-formed (never a
        // crash, never an unstable value).
        let latestDay = snapshots.map(\.day).max() ?? .distantPast
        // Canonical (sorted) join — NOT `Set.hashValue` (per-process randomized, Pitfall 5). Sorting makes
        // the component order-independent so {"a","b"} and {"b","a"} are identical.
        let dismissedCanonical = dismissed.sorted().joined(separator: ",")
        return "\(snapshots.count)|\(latestDay.timeIntervalSince1970)|\(dismissedCanonical)"
    }
}
