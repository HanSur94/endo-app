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
// self-gating producers. `TrendsEngine` (its `minLoggedDays >= 3` self-gate) and `CyclePhaseMapper`
// (its `gaps.count >= 2` / spread / suppressed self-gates) ALREADY decided what is sample-worthy. The
// gate MUST NOT re-drop a valid producer output on a count. In particular it MUST NOT use
// `insight.points.count` for `.trend`: a valid frequency trend can carry only 1–2 `points` (the days a
// symptom was PRESENT) yet have passed the producer's `daysWithAnyLog >= 3` self-gate. The `minSample`
// table below is therefore an API CONTRACT future producers (Ph7/11/12) check against THEIR OWN candidate
// sample BEFORE emitting — the gate performs NO count-based drop in Phase 6.
import Foundation

/// The single mandatory insight choke-point (D-05/D-16). Package-internal — only `InsightsEngine` calls it.
enum HonestyGate {

    /// Per-kind minimum sample size, keyed by `InsightKind` so a future kind extends by adding a case,
    /// not by redesigning the gate. This is an API CONTRACT producers self-check against BEFORE emitting
    /// (see the no-regression note above); the gate does not count-drop `.trend`/`.cyclePhase` in Phase 6.
    ///
    ///   - `.trend`          → 3  (LOCKED == `TrendsEngine.minLoggedDays`; the no-regression invariant, D-06).
    ///   - `.cyclePhase`     → 2  (mirrors `CyclePhaseMapper`'s `gaps.count >= 2`).
    ///   - `.correlation`    → 14 (Phase 7 placeholder — set in its own phase, A3).
    ///   - `.recommendation` → 14 (Phase 11 placeholder).
    ///   - `.flare`          → 21 (Phase 12 placeholder).
    static func minSample(for kind: InsightKind) -> Int {
        switch kind {
        case .trend:          return 3
        case .cyclePhase:     return 2
        case .correlation:    return 14
        case .recommendation: return 14
        case .flare:          return 21
        }
    }

    /// The confidence floor (D-07): an insight below this is WITHHELD, never ranked — EXCEPT the carved-out
    /// "not enough data yet" trend signal (its signalKey ends `.insufficient`), which IS the honest
    /// sub-threshold signal and is deliberately KEPT.
    static let confidenceFloor: InsightConfidence = .low

    /// Apply the honesty gate to a stream of candidate insights, in order:
    ///   (a) DROP if the signal is dismissed (D-11 — match on the stable `signalKey`).
    ///   (b) per-kind min-sample: a SUPERSET filter that MUST NOT drop a valid producer output.
    ///       `.trend`/`.cyclePhase` are already self-gated by their producers, so the gate treats any
    ///       non-`.none` such insight as min-sample-satisfied (Pitfall 1 — it does NOT re-drop on a count).
    ///   (c) DROP if confidence is below the floor — UNLESS it is the `.insufficient` carve-out (the
    ///       deliberate honest sub-threshold signal, TrendsEngine line 236).
    ///   (d) DEBUG-assert no surviving insight's copy contains a forbidden (causal) term. A forbidden term
    ///       is a PROGRAMMER error (a copy bug), not user data — so it asserts in debug and NEVER silently
    ///       drops in release (mirrors `Repository.assertConsented()`). The durable guarantee is the
    ///       zero-forbidden-terms TEST (`HonestyGateTests`), which every future kind MUST extend.
    ///
    /// Pure + order-preserving for surviving insights.
    static func apply(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        let survivors = candidates.filter { insight in
            // (a) dismissal-exclusion on the stable signalKey (D-11).
            if dismissed.contains(insight.signalKey) { return false }

            // (b) min-sample SUPERSET filter (Pitfall 1): `.trend`/`.cyclePhase` are producer-self-gated.
            //     The gate does NOT re-drop them on any count — it only relies on the producers' gates.
            //     (Other kinds will self-gate against `minSample(for:)` in their own phases before emitting.)

            // (c) confidence floor with the `.insufficient` carve-out (the intended honest signal).
            let isInsufficientCarveOut = insight.signalKey.hasSuffix(".insufficient")
            if insight.confidence < confidenceFloor && !isInsufficientCarveOut {
                return false
            }

            return true
        }

        // (d) DEBUG enforcement of non-causal language — never a silent release drop.
        for insight in survivors {
            assert(
                ForbiddenTerms.violation(in: insight.headline) == nil
                    && ForbiddenTerms.violation(in: insight.detail) == nil,
                "HonestyGate: forbidden causal term in insight copy (signalKey: \(insight.signalKey)). "
                    + "Fix the producer's copy AND add it to HonestyGateTests' zero-forbidden-terms scan."
            )
        }

        return survivors
    }
}
