// EndoInsights — THE single public insight composition root (Phase 6, INST-04 / D-13/D-16 / SC1).
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1). Pure — value-in/value-out, NO logging/print (V7).
//
// This is the ONE public entry point the app calls (D-13): it composes the descriptive `TrendsEngine`
// trends with the `CyclePhaseMapper` estimated-phase associations, then routes EVERY candidate through
// the single `HonestyGate` choke-point (under-sampled / below-floor WITHHELD, dismissed EXCLUDED,
// non-causal copy debug-asserted). `TrendsEngine` and `CyclePhaseMapper` are package-INTERNAL behind this
// facade — callers must not reach past the gate.
//
// SIGNATURE LOCKED (D-16): Phases 7/11/12 add more candidate producers to the composition WITHOUT
// changing this signature; Phase 8 renders the output and supplies `dismissed` from
// `Repository.dismissedSignalKeys()` (the live wiring is Phase 8 — out of scope here).
import Foundation

/// The single PUBLIC insight producer (D-13). Composes all candidate producers and routes them through
/// the mandatory `HonestyGate` (D-05/D-16).
public enum InsightsEngine {

    /// Produce the gated, dismissal-filtered insights for a stream of day snapshots.
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent).
    ///   - today:     injected reference date (never read from the clock inside) → deterministic.
    ///   - dismissed: the set of dismissed `signalKey`s to exclude (D-11). A `Set<String>` keeps the
    ///                engine PURE — no store access in the package (SC1); Phase 8 supplies it from the store.
    ///   - calendar:  injected calendar for day-bucketing (defaults to `.current`).
    /// - Returns: the honesty-gated `[Insight]` — under-sampled/below-floor withheld, dismissed excluded.
    public static func insights(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        dismissed: Set<String>,
        calendar: Calendar = .current
    ) -> [Insight] {
        let candidates =
            TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
            + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
            + CorrelationEngine.insights(from: snapshots, asOf: today, calendar: calendar)
            // Phase 11 (INST-05, D-08): re-framed associations. Concatenated AFTER the correlations they
            // derive from (matches the dashboard section order, D-06) and routed through the SAME single
            // HonestyGate below — no separate gating path. The public signature stays LOCKED (D-16).
            + RecommendationsEngine.recommendations(from: snapshots, asOf: today, calendar: calendar)
            // Phase 12 (INST-06): the composed flare warning, routed through the SAME single HonestyGate —
            // no separate gating path; the public signature stays LOCKED. Concatenated LAST (additive — the
            // candidate order does not affect gating; the dashboard places the .flare section FIRST as a
            // pure UI concern, D-06/D-07). The gate's minSample(.flare)==21 + the confidence floor + the
            // typed isInsufficiencyNotice carve-out + the fail-closed forbidden-term scan (now incl.
            // alarmist) already handle the flare candidate — NO new withholding logic here (D-08).
            + FlareEngine.flareWarning(from: snapshots, asOf: today, calendar: calendar)
        return HonestyGate.apply(candidates, dismissed: dismissed)
    }
}
