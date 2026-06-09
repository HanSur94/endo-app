// EndoInsights — THE single insight-honesty choke-point (Phase 6, INST-04 / D-05/D-06/D-07/D-11/D-16 / SC1).
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1). Pure — value-in/value-out, NO logging/print (V7).
//
// This is the product's ETHICAL CORE: the ONE mandatory filter EVERY present and future insight kind
// (Phases 7/11/12 route through it — the API is LOCKED now, D-16) passes through before it can reach a
// user. The gate WITHHOLDS under-sampled and low-confidence findings ENTIRELY (it never caveats-and-shows),
// EXCLUDES dismissed signals, and ASSERTS (debug) that no emitted copy contains a causal term.
//
// NO-REGRESSION INVARIANT (RESEARCH Pitfall 1 — load-bearing): the gate is a SUPERSET filter over
// self-gating producers. The gate enforces min-sample on the insight's REAL evidence count
// (`Insight.sampleSize`) — NOT on `insight.points.count` (a valid frequency trend can carry only 1–2
// `points` yet be based on `daysWithAnyLog >= 3` days). Because every emitted `.trend` carries
// `sampleSize >= minLoggedDays == 3 == minSample(.trend)`, the gate drops NO valid trend (no-regression
// holds). The `minSample` table below is the LIVE control (CR-01/WR-02/IN-01): an under-sampled
// `.cyclePhase` association (sampleSize 1 < minSample(.cyclePhase) == 2) IS withheld here. Future
// producers (Ph7/11/12) set `sampleSize` to their candidate's evidence count and the gate enforces it.
import Foundation

/// The single mandatory insight choke-point (D-05/D-16). Package-internal — only `InsightsEngine` calls it.
enum HonestyGate {

    /// Per-kind minimum sample size, keyed by `InsightKind` so a future kind extends by adding a case,
    /// not by redesigning the gate. This is the LIVE backstop the gate enforces against each insight's
    /// `sampleSize` (CR-01/WR-02/IN-01) — no longer dead documentation. Producers SHOULD also self-gate,
    /// but the gate now guarantees the floor even if a producer forgets.
    ///
    ///   - `.trend`          → 3  (LOCKED == `TrendsEngine.minLoggedDays`; the no-regression invariant, D-06).
    ///   - `.cyclePhase`     → 2  (mirrors `CyclePhaseMapper`'s `gaps.count >= 2`).
    ///   - `.correlation`    → 10 (Phase 7 LOCKED — D-14/A1; == the engine self-gate floor).
    ///   - `.recommendation` → 14 (Phase 11 placeholder).
    ///   - `.flare`          → 21 (Phase 12 placeholder).
    static func minSample(for kind: InsightKind) -> Int {
        switch kind {
        case .trend:          return 3
        case .cyclePhase:     return 2
        case .correlation:    return 10
        case .recommendation: return 14
        case .flare:          return 21
        }
    }

    /// The confidence floor (D-07): an insight below this is WITHHELD, never ranked — EXCEPT the carved-out
    /// "not enough data yet" signal (`Insight.isInsufficiencyNotice == true`, a TYPED marker — WR-01),
    /// which IS the honest sub-threshold signal and is deliberately KEPT.
    static let confidenceFloor: InsightConfidence = .low

    /// Apply the honesty gate to a stream of candidate insights, in order:
    ///   (a) DROP if the signal is dismissed (D-11 — match on the stable `signalKey`).
    ///   (b) per-kind MIN-SAMPLE — now a LIVE backstop (CR-01/WR-02, IN-01): DROP any insight whose
    ///       `sampleSize < minSample(for: insight.kind)`, EXCEPT the `isInsufficiencyNotice` carve-out.
    ///       This is genuinely a SUPERSET filter, not a pass-through: every emitted `.trend` carries
    ///       `sampleSize >= minLoggedDays == 3 == minSample(.trend)`, so NO valid trend is dropped
    ///       (the no-regression invariant — Pitfall 1 — still holds). But an under-sampled `.cyclePhase`
    ///       association (e.g. a single-day n=1 co-occurrence vs. `minSample(.cyclePhase) == 2`) is now
    ///       WITHHELD. The gate no longer TRUSTS producers to self-gate — it ENFORCES the floor.
    ///   (c) DROP if confidence is below the floor — UNLESS it is the `isInsufficiencyNotice` carve-out
    ///       (the deliberate honest sub-threshold signal). Keyed on the TYPED marker, never a forgeable
    ///       signalKey suffix (WR-01).
    ///   (d) FAIL-CLOSED non-causal enforcement (CR-02): DROP (WITHHOLD) any insight whose headline or
    ///       detail contains a forbidden (causal) term — in BOTH debug AND release. ADDITIONALLY assert in
    ///       debug so the producer copy bug is LOUD in development. A causal claim must NEVER reach the
    ///       user, even in release, even from runtime-interpolated copy a static test cannot cover.
    ///
    /// Pure + order-preserving for surviving insights.
    static func apply(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        let survivors = sampleAndFloorFilter(candidates, dismissed: dismissed)
        // (d) Assert in debug (loud copy-bug signal) AND drop in every build (fail-closed, CR-02).
        return causalTermFilter(survivors, assertOnViolation: true)
    }

    /// TEST-ONLY: the gate WITHOUT the debug `assertionFailure`, so a test can verify the RELEASE
    /// fail-closed drop (CR-02) — the durable runtime guarantee — without crashing a debug-build test run.
    /// Identical to `apply` in every other respect.
    static func applyReleaseScan(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        let survivors = sampleAndFloorFilter(candidates, dismissed: dismissed)
        return causalTermFilter(survivors, assertOnViolation: false)
    }

    /// Steps (a)–(c): dismissal exclusion, the LIVE min-sample backstop, and the confidence floor.
    private static func sampleAndFloorFilter(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        candidates.filter { insight in
            // (a) dismissal-exclusion on the stable signalKey (D-11).
            if dismissed.contains(insight.signalKey) { return false }

            // The honest "not enough data yet" notice is exempt from BOTH the min-sample drop and the
            // confidence floor — it is the deliberate sub-threshold signal. TYPED marker (WR-01).
            let isInsufficiencyNotice = insight.isInsufficiencyNotice

            // (b) min-sample LIVE backstop (CR-01/WR-02): drop an under-sampled candidate by its real
            //     evidence count. Trends carry sampleSize >= 3 == minSample(.trend) → never dropped
            //     (no-regression); an n=1 cyclePhase association (< minSample(.cyclePhase) == 2) → dropped.
            if !isInsufficiencyNotice && insight.sampleSize < minSample(for: insight.kind) {
                return false
            }

            // (c) confidence floor with the typed insufficiency carve-out (the intended honest signal).
            if insight.confidence < confidenceFloor && !isInsufficiencyNotice {
                return false
            }

            return true
        }
    }

    /// Step (d): FAIL-CLOSED non-causal language enforcement (CR-02). WITHHOLD any insight whose copy
    /// contains a forbidden causal term, in EVERY build configuration. When `assertOnViolation` is true
    /// (production `apply`), assert FIRST so the copy bug is LOUD in debug, THEN drop so it can never
    /// surface in release. `ForbiddenTerms.violation` short-circuits on the first match, so this is cheap.
    private static func causalTermFilter(_ insights: [Insight], assertOnViolation: Bool) -> [Insight] {
        insights.filter { insight in
            if let term = ForbiddenTerms.violation(in: insight.headline)
                ?? ForbiddenTerms.violation(in: insight.detail) {
                if assertOnViolation {
                    assertionFailure(
                        "HonestyGate: forbidden causal term '\(term)' in insight copy "
                            + "(signalKey: \(insight.signalKey)). Fix the producer's copy AND add it to "
                            + "HonestyGateTests' zero-forbidden-terms scan."
                    )
                }
                return false   // release: WITHHOLD — a causal claim must never reach the user (fail-closed).
            }
            return true
        }
    }
}
