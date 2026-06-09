// Requirement: D-04 / INST-02 — the pure, launch-stable input fingerprint that drives the
// coordinator's cache hit/miss decision.
//
// The fingerprint is a CANONICAL string built from (log count + latest-edit timestamp +
// dismissed-set canonical) — NEVER `Set.hashValue` (per-process randomized, RESEARCH Pitfall 5).
// Identical inputs MUST produce the identical string across two calls (and would across launches);
// changing the count, the latest `day`, or the dismissed set MUST flip it; the dismissed-set ORDER
// MUST NOT affect it (canonical sort).
import Testing
import Foundation
import EndoInsights
@testable import EndoApp

@Suite("D-04 InsightFingerprint — pure, launch-stable, edit-sensitive")
struct InsightFingerprintTests {

    /// A deterministic, minimal `DaySnapshot` on `day` (only the `day` participates in the fingerprint).
    private func snapshot(on day: Date) -> DaySnapshot {
        DaySnapshot(
            day: day,
            symptoms: [],
            pain: [],
            flow: nil,
            clots: false,
            foodTags: [],
            activityType: nil,
            activityMinutes: nil,
            activityIntensity: nil,
            sleepHours: nil,
            sleepQuality: nil,
            medicationsTaken: [],
            tookPRN: false,
            stress: nil,
            mood: nil,
            hasNote: false,
            suppressed: false)
    }

    private let epoch = Date(timeIntervalSince1970: 1_700_000_000)
    private func day(_ offset: Int) -> Date { epoch.addingTimeInterval(Double(offset) * 86_400) }

    // MARK: Stability — same inputs ⇒ same fingerprint across two separate calls.

    @Test func stability_identicalInputsProduceIdenticalFingerprint() {
        let snaps = [snapshot(on: day(0)), snapshot(on: day(1)), snapshot(on: day(2))]
        let dismissed: Set<String> = ["symptom.pelvicPain.severity", "factor.sleep.poor.frequency"]

        let a = InsightFingerprint.make(snapshots: snaps, dismissed: dismissed)
        let b = InsightFingerprint.make(snapshots: snaps, dismissed: dismissed)

        #expect(a == b, "Identical (snapshots, dismissed) inputs must yield the identical fingerprint.")
    }

    // MARK: Change-detection — count flips it.

    @Test func changingSnapshotCount_flipsFingerprint() {
        let base = [snapshot(on: day(0)), snapshot(on: day(1))]
        let more = base + [snapshot(on: day(2))]

        #expect(
            InsightFingerprint.make(snapshots: base, dismissed: []) !=
                InsightFingerprint.make(snapshots: more, dismissed: []),
            "Adding a snapshot (count change) must flip the fingerprint.")
    }

    // MARK: Change-detection — latest `day` flips it (the edit-not-count guard, Pitfall 5).

    @Test func sameCountDifferentLatestDay_flipsFingerprint() {
        // Two sets with the SAME count but a different LATEST day — a count-only fingerprint would miss
        // this edit (Pitfall 5). The fingerprint MUST include the latest-edit timestamp.
        let earlier = [snapshot(on: day(0)), snapshot(on: day(1))]
        let later = [snapshot(on: day(0)), snapshot(on: day(5))]

        #expect(earlier.count == later.count, "Precondition: the two sets have equal counts.")
        #expect(
            InsightFingerprint.make(snapshots: earlier, dismissed: []) !=
                InsightFingerprint.make(snapshots: later, dismissed: []),
            "Same count but a later latest-day must flip the fingerprint (edit-not-count, Pitfall 5).")
    }

    // MARK: Change-detection — adding/removing a dismissed key flips it.

    @Test func changingDismissedSet_flipsFingerprint() {
        let snaps = [snapshot(on: day(0))]
        let before: Set<String> = ["a"]
        let after: Set<String> = ["a", "b"]

        #expect(
            InsightFingerprint.make(snapshots: snaps, dismissed: before) !=
                InsightFingerprint.make(snapshots: snaps, dismissed: after),
            "Adding a dismissed key must flip the fingerprint (a dismiss → cache miss → recompute).")
    }

    // MARK: Order-independence — {"a","b"} and {"b","a"} produce the SAME fingerprint.

    @Test func dismissedSetOrderDoesNotAffectFingerprint() {
        let snaps = [snapshot(on: day(0))]
        // Two Sets built in different insertion orders are equal as Sets, but the test asserts the
        // CANONICAL (sorted) join — guarding against any accidental order-sensitive serialization.
        let ab: Set<String> = Set(["a", "b"])
        let ba: Set<String> = Set(["b", "a"])

        #expect(
            InsightFingerprint.make(snapshots: snaps, dismissed: ab) ==
                InsightFingerprint.make(snapshots: snaps, dismissed: ba),
            "Dismissed-set order must not affect the fingerprint (canonical sort).")
    }

    // MARK: Empty inputs are stable and well-formed (no crash on `.max()` of an empty array).

    @Test func emptyInputs_areStableAndWellFormed() {
        let a = InsightFingerprint.make(snapshots: [], dismissed: [])
        let b = InsightFingerprint.make(snapshots: [], dismissed: [])
        #expect(a == b, "Empty inputs must produce a stable, well-formed fingerprint (no crash).")
        #expect(!a.isEmpty, "The fingerprint string is never empty even for empty inputs.")
    }
}
