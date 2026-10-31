// EndoInsights (:insights) — THE single public insight composition root.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` (Phase 6, INST-04 /
// D-13/D-16 / SC1 / V7). Pure-JVM — value-in / value-out, NO logging (BoundaryLint is the proof). Per
// Pattern 2 the injected Swift `Calendar` is DROPPED (LocalDate is the zone-free day identity).
//
// This is the ONE public entry point the app calls (D-13): it composes the five candidate producers —
// `TrendsEngine` (descriptive trends), `CyclePhaseMapper` (estimated-phase associations),
// `CorrelationEngine` (factor↔symptom associations), `RecommendationsEngine` (re-framed discussion
// prompts), `FlareEngine` (the calm early-warning) — then routes EVERY candidate through the single
// `HonestyGate` choke-point (under-sampled / below-floor WITHHELD, dismissed EXCLUDED, forbidden copy
// fail-closed-dropped). All five producers are package-INTERNAL behind this facade — callers must not
// reach past the gate.
//
// SIGNATURE LOCKED (D-16): this is the stable contract Phase 18's `SnapshotProjector` targets. Additional
// candidate producers compose WITHOUT changing this signature; Phase 18 supplies `dismissed` from the
// repository's dismissed-signalKeys set (the live wiring is out of scope here).
package com.endoapp.insights

import java.time.LocalDate

/** The single PUBLIC insight producer (D-13). Composes all candidate producers and routes them through
 *  the mandatory `HonestyGate` (D-05/D-16). */
object InsightsEngine {

    /** Produce the gated, dismissal-filtered insights for a stream of day snapshots.
     *
     *  @param from      the per-day value snapshots (order-independent).
     *  @param asOf      the injected reference date (never read from the clock inside) -> deterministic.
     *  @param dismissed the set of dismissed `signalKey`s to exclude (D-11). A `Set<String>` keeps the
     *                   engine PURE — no store access in the package (SC1); Phase 18 supplies it.
     *  @return the honesty-gated insights — under-sampled / below-floor withheld, dismissed excluded. */
    fun insights(
        from: List<DaySnapshot>,
        asOf: LocalDate,
        dismissed: Set<String> = emptySet(),
    ): List<Insight> {
        val candidates =
            TrendsEngine.trends(from = from, asOf = asOf) +
                CyclePhaseMapper.insights(from = from, asOf = asOf) +
                CorrelationEngine.insights(from = from, asOf = asOf) +
                // Phase 11 (INST-05, D-08): re-framed associations. Concatenated AFTER the correlations they
                // derive from and routed through the SAME single HonestyGate below — no separate gating path.
                RecommendationsEngine.recommendations(from = from, asOf = asOf) +
                // Phase 12 (INST-06): the composed flare warning, routed through the SAME single HonestyGate.
                // Candidate order does not affect gating; the dashboard places the .flare section first as a
                // pure UI concern (D-06/D-07). minSample(FLARE)==21 + the confidence floor + the typed
                // isInsufficiencyNotice carve-out + the fail-closed forbidden-term scan handle the candidate.
                FlareEngine.flareWarning(from = from, asOf = asOf)
        return HonestyGate.apply(candidates, dismissed)
    }
}
