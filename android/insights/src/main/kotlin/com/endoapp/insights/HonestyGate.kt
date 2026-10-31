// EndoInsights (:insights) — THE single insight-honesty choke-point.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/HonestyGate.swift` (Phase 6, INST-04 /
// D-05/D-06/D-07/D-11/D-16 / SC1 / V7). Pure-JVM — value-in / value-out, NO logging (BoundaryLint is
// the proof). This is the product's ETHICAL CORE: the ONE mandatory filter EVERY insight kind (Trends,
// CyclePhase, Correlation, Recommendation, Flare) passes through before it can reach a user. The gate
// WITHHOLDS under-sampled and low-confidence findings ENTIRELY (it never caveats-and-shows), EXCLUDES
// dismissed signals, and FAIL-CLOSED-DROPS any surviving insight whose copy contains a forbidden term.
//
// NO-REGRESSION INVARIANT (RESEARCH Pitfall 1 — load-bearing): the gate is a SUPERSET filter over
// self-gating producers. It enforces min-sample on the insight's REAL evidence count
// (`Insight.sampleSize`), NOT on `points.count` (a valid frequency trend can carry only 1-2 points yet
// rest on >= 3 logged days). Because every emitted `.trend` carries `sampleSize >= 3 == minSample(TREND)`,
// the gate drops NO valid trend. The [minSample] table is the LIVE backstop (CR-01/WR-02/IN-01): an
// under-sampled `.cyclePhase` association (sampleSize 1 < minSample(CYCLE_PHASE) == 2) IS withheld here.
//
// SINGLE SOURCE OF TRUTH (17-08 wiring): the per-kind floors this gate owns now supersede the local
// mirror constants earlier plans left behind (`CorrelationEngine.correlationMinSample == 10`,
// `FlareEngine.flareMinSample == 21`). Those producers self-gate at the same floor by construction, so
// this gate never count-drops a valid producer output — it is a genuine superset filter.
package com.endoapp.insights

/** The single mandatory insight choke-point (D-05/D-16). Package-internal — only `InsightsEngine`
 *  calls it. */
internal object HonestyGate {

    /** Per-kind minimum sample size, keyed by [InsightKind] so a future kind extends by adding a case,
     *  not by redesigning the gate. This is the LIVE backstop the gate enforces against each insight's
     *  `sampleSize` (CR-01/WR-02/IN-01).
     *
     *   - `TREND`          -> 3  (LOCKED == `TrendsEngine.minLoggedDays`; the no-regression invariant, D-06).
     *   - `CYCLE_PHASE`    -> 2  (mirrors `CyclePhaseMapper`'s `gaps.count >= 2`).
     *   - `CORRELATION`    -> 10 (Phase 7 LOCKED — D-14/A1; == the engine self-gate floor).
     *   - `RECOMMENDATION` -> 10 (Phase 11 LOCKED — inherits minSample(CORRELATION) (D-02): a recommendation
     *                         re-frames an already-gated correlation, so a 14 floor would silently drop valid
     *                         n=10-13 recommendations (Pitfall 3).
     *   - `FLARE`          -> 21 (Phase 12). */
    fun minSample(kind: InsightKind): Int = when (kind) {
        InsightKind.TREND -> 3
        InsightKind.CYCLE_PHASE -> 2
        InsightKind.CORRELATION -> 10
        InsightKind.RECOMMENDATION -> 10
        InsightKind.FLARE -> 21
    }

    /** The confidence floor (D-07): an insight below this is WITHHELD, never ranked — EXCEPT the carved-out
     *  "not enough data yet" signal (`Insight.isInsufficiencyNotice == true`, a TYPED marker — WR-01),
     *  which IS the honest sub-threshold signal and is deliberately KEPT. */
    val confidenceFloor: InsightConfidence = InsightConfidence.LOW

    /** Apply the honesty gate to a stream of candidate insights, in order:
     *   (a) DROP if the signal is dismissed (D-11 — match on the stable `signalKey`).
     *   (b) per-kind MIN-SAMPLE — a LIVE backstop (CR-01/WR-02, IN-01): DROP any insight whose
     *       `sampleSize < minSample(insight.kind)`, EXCEPT the `isInsufficiencyNotice` carve-out. A genuine
     *       SUPERSET filter: every emitted `.trend` carries `sampleSize >= 3 == minSample(TREND)`, so NO
     *       valid trend is dropped (no-regression — Pitfall 1), but an under-sampled `.cyclePhase`
     *       association (n=1 < minSample(CYCLE_PHASE) == 2) is WITHHELD.
     *   (c) DROP if confidence is below the floor — UNLESS it is the `isInsufficiencyNotice` carve-out.
     *       Keyed on the TYPED marker, never a forgeable signalKey suffix (WR-01).
     *   (d) FAIL-CLOSED non-causal enforcement (CR-02): DROP any surviving insight whose headline or detail
     *       contains a forbidden term. A causal/imperative/alarmist claim must NEVER reach the user. The
     *       production path additionally makes the copy bug LOUD via [error] (the JVM analogue of Swift's
     *       debug `assertionFailure`) — a forbidden term is a producer copy bug, not user data.
     *
     *  Pure + order-preserving for surviving insights. */
    fun apply(candidates: List<Insight>, dismissed: Set<String>): List<Insight> {
        val survivors = sampleAndFloorFilter(candidates, dismissed)
        // (d) Loud copy-bug signal (error) on violation AND drop in every build (fail-closed, CR-02).
        return causalTermFilter(survivors, throwOnViolation = true)
    }

    /** TEST-ONLY: the gate WITHOUT the loud [error] on a violation, so a test can verify the RELEASE
     *  fail-closed drop (CR-02) — the durable runtime guarantee — without throwing. Identical to [apply]
     *  in every other respect. */
    fun applyReleaseScan(candidates: List<Insight>, dismissed: Set<String>): List<Insight> {
        val survivors = sampleAndFloorFilter(candidates, dismissed)
        return causalTermFilter(survivors, throwOnViolation = false)
    }

    /** Steps (a)-(c): dismissal exclusion, the LIVE min-sample backstop, and the confidence floor. */
    private fun sampleAndFloorFilter(candidates: List<Insight>, dismissed: Set<String>): List<Insight> =
        candidates.filter { insight ->
            // (a) dismissal-exclusion on the stable signalKey (D-11).
            if (dismissed.contains(insight.signalKey)) return@filter false

            // The honest "not enough data yet" notice is exempt from BOTH the min-sample drop and the
            // confidence floor — it is the deliberate sub-threshold signal. TYPED marker (WR-01).
            val isInsufficiencyNotice = insight.isInsufficiencyNotice

            // (b) min-sample LIVE backstop (CR-01/WR-02): drop an under-sampled candidate by its real
            //     evidence count. Trends carry sampleSize >= 3 == minSample(TREND) -> never dropped
            //     (no-regression); an n=1 cyclePhase association (< minSample(CYCLE_PHASE) == 2) -> dropped.
            if (!isInsufficiencyNotice && insight.sampleSize < minSample(insight.kind)) {
                return@filter false
            }

            // (c) confidence floor with the typed insufficiency carve-out (the intended honest signal).
            if (insight.confidence < confidenceFloor && !isInsufficiencyNotice) {
                return@filter false
            }

            true
        }

    /** Step (d): FAIL-CLOSED non-causal language enforcement (CR-02). WITHHOLD any insight whose copy
     *  contains a forbidden term, in EVERY build. When [throwOnViolation] is true (production [apply]),
     *  make the copy bug LOUD first (the JVM analogue of Swift's debug `assertionFailure`); otherwise drop
     *  silently so a test can verify the release drop. `ForbiddenTerms.violation` short-circuits on the
     *  first match, so this is cheap. */
    private fun causalTermFilter(insights: List<Insight>, throwOnViolation: Boolean): List<Insight> =
        insights.filter { insight ->
            val term = ForbiddenTerms.violation(insight.headline)
                ?: ForbiddenTerms.violation(insight.detail)
            if (term != null) {
                if (throwOnViolation) {
                    error(
                        "HonestyGate: forbidden term '$term' in insight copy " +
                            "(signalKey: ${insight.signalKey}). Fix the producer's copy AND add it to " +
                            "HonestyGateTests' zero-forbidden-terms scan.",
                    )
                }
                return@filter false // release: WITHHOLD — a causal claim must never reach the user (fail-closed).
            }
            true
        }
}
