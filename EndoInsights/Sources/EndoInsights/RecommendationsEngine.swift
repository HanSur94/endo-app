// EndoInsights — the personalized-recommendations producer (Phase 11, Plan 02, INST-05 / D-01..D-08 / SC1 / V7).
//
// THE substantive honesty surface of the phase: a recommendation that reads as a DISCUSSION PROMPT,
// never an instruction, and is provably ABSENT for users without qualifying data.
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLint is the proof). Pure: an INJECTED
// `today` + `calendar`, NO `Date.now`/`Date()`/`UUID()`/RNG/`print` (V7/D-16). Mirrors `CorrelationEngine`
// / `CyclePhaseMapper` EXACTLY: an `enum` namespace, one `static func recommendations(from:asOf:calendar:)`
// entry, `Equatable` `[Insight]` output — same snapshots + same injected `today` → an equal `[Insight]`.
//
// COMPOSE, NEVER RE-DERIVE (D-01): the engine consumes `CorrelationEngine.scoredCandidates(...)` — the
// typed survivors already gated/sorted/top-N by the correlation pipeline — and runs NO statistics of its
// own. A recommendation exists ONLY for a real surviving correlation candidate; a suppressed / sparse /
// multiple-comparison-flood user yields ZERO recommendations (D-08/SC3 — honest absence by omission).
//
// DIRECTION-AWARE COPY (D-03/D-04): the typed `finding.direction` (recovered from the SIGNED effect in
// Plan 01, NEVER from parsed copy) selects the template — `.beneficial` → the "feel better" prompt,
// `.aggravating` → a descriptive "more intense … something you might explore" prompt. Copy comes ONLY
// from the curated `RecommendationCopy` templates below, never free text.
//
// APP↔PACKAGE COPY MIRROR (mirrors the `ClaimsPolicy`↔`ForbiddenTerms` precedent): the package CANNOT
// import the app (SC1), so the curated template strings live HERE in a package-internal `RecommendationCopy`
// enum, BYTE-IDENTICAL to the app's audited `RecommendationsCopy` (`Sources/EndoApp/Features/Insights/
// RecommendationsCopy.swift`). The app surface is audited by `CopyAuditTests`; this package mirror is proven
// clean by the HonestyGate zero-forbidden-terms scan (incl. the new imperative terms) — keep them in sync.
//
// STABLE SIGNALKEY (D-07): a recommendation's signalKey is `recommendation.<correlation-stem>` with the
// `.allTime` window — value-INDEPENDENT and window-INDEPENDENT (like correlations), so the Phase-6
// dismissal + Phase-8 cache survive recompute. `Insight.derived` yields the stable id from those inputs.
import Foundation

enum RecommendationsEngine {

    /// The recommendation count cap (D-02 / RESEARCH A3) — at most this many `.recommendation` Insights
    /// surface, taken in the deterministic correlation-survivor order.
    static let topN = 3

    /// The package-local prefix on a correlation signalKey, stripped to form the stable recommendation stem.
    private static let correlationPrefix = "correlation."

    // MARK: - The single public entry point (mirrors CorrelationEngine.insights signature EXACTLY).

    /// Re-frame the typed `CorrelationEngine.scoredCandidates(...)` survivors into top-N (<= `topN`)
    /// direction-aware, non-prescriptive `.recommendation` Insights. NO re-derived statistics (D-01).
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent).
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    static func recommendations(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        // HONEST ABSENCE FOR A SUPPRESSED USER (D-08/SC3, Pitfall 5): a user who has turned cycle/insight
        // derivation OFF (any `suppressed` snapshot) gets ZERO recommendations — recommendations are the
        // most prescriptive-adjacent surface, so they are MORE conservative than raw correlations (which
        // only suppress the cycle-phase factor). Never fabricate a "do this" for a suppressed user.
        guard !snapshots.contains(where: { $0.suppressed }) else { return [] }

        // The typed survivors — ALREADY gate-filtered, deterministically sorted, top-N-by-correlation
        // (Plan 01). The recommendation engine runs NO statistics; it only re-frames these (D-01).
        let findings = CorrelationEngine.scoredCandidates(from: snapshots, asOf: today, calendar: calendar)

        // Honest absence by omission (Pitfall 5): an Insight is ONLY ever produced by mapping a REAL
        // surviving finding — never appended outside this. Sparse/flood → no findings → [].
        guard !findings.isEmpty else { return [] }

        // ONE RECOMMENDATION PER SYMPTOM TARGET (D-02 "avoid noise/overwhelm"): the correlation pipeline
        // surfaces COMPLEMENTARY findings for the same symptom — e.g. a 2-group severity split yields both
        // a beneficial reading (factor A present → lower) AND its aggravating mirror (factor B present →
        // higher). Surfacing both as separate recommendations is redundant and confusing. Collapse per
        // symptom target to a SINGLE recommendation, framed by the user's DOMINANT direction for that
        // symptom (more supporting findings wins; a tie → the cautionary aggravating framing, D-03's
        // non-over-promising default). The findings arrive in the deterministic correlation order, so the
        // first survivor of the chosen direction is itself deterministic.
        let chosen = dominantPerSymptom(findings)

        return chosen
            .prefix(topN)
            .map { makeRecommendation(from: $0) }
    }

    /// Collapse the survivors to ONE per symptom target (D-02). For each target (keyed by the post-factor
    /// signalKey stem, e.g. `symptom.pelvicPain`), pick the direction with MORE supporting findings (tie →
    /// `.aggravating`, the cautionary default), then keep that target's FIRST finding of the chosen
    /// direction in the incoming deterministic order. Returns the kept findings in incoming order — so the
    /// overall ordering (and `.prefix(topN)`) stays deterministic and matches the correlation ranking.
    private static func dominantPerSymptom(
        _ findings: [CorrelationEngine.CorrelationFinding]
    ) -> [CorrelationEngine.CorrelationFinding] {
        // Group by the actual TARGET (symptom type or bleeding), keyed by the signalKey with its leading
        // `correlation.<factor.key>.` stem stripped — NOT a fixed trailing-suffix length.
        func targetKey(_ f: CorrelationEngine.CorrelationFinding) -> String {
            // signalKey is `correlation.<factor.key>.<target-fragment>` where `<factor.key>` is ALWAYS two
            // dot-components (`food.dairy`, `sleep.poor`, `prn.taken`, `cyclePhase.luteal`, …) and the
            // target fragment is either `symptom.<type>` (two components) OR `bleeding` (one). Taking a
            // FIXED `suffix(2)` is wrong for the one-component `bleeding` target — it pulls the trailing
            // factor fragment into the key (`dairy.bleeding` vs `poor.bleeding`), so two factors on the
            // SAME bleeding target land in SEPARATE groups and are NOT collapsed (WR-01). Instead drop the
            // leading `correlation` + the two factor components deterministically, so the key is the TARGET
            // identity — `symptom.pelvicPain` or `bleeding` — regardless of factor or fragment length. Fall
            // back to the whole signalKey if the shape is unexpected (never crash).
            let parts = f.signalKey.split(separator: ".")
            guard parts.count >= 4 else { return f.signalKey }   // correlation.<a>.<b>.<target…>
            return parts.dropFirst(3).joined(separator: ".")     // "symptom.pelvicPain" or "bleeding"
        }

        var orderedTargets: [String] = []
        var byTarget: [String: [CorrelationEngine.CorrelationFinding]] = [:]
        for f in findings {
            let key = targetKey(f)
            if byTarget[key] == nil { orderedTargets.append(key) }
            byTarget[key, default: []].append(f)
        }

        var kept: [CorrelationEngine.CorrelationFinding] = []
        for target in orderedTargets {
            let group = byTarget[target] ?? []
            let beneficialCount = group.filter { $0.direction == .beneficial }.count
            let aggravatingCount = group.count - beneficialCount
            // Dominant direction: more findings wins; a tie defers to the cautionary aggravating framing.
            let dominant: CorrelationEngine.RecommendationDirection =
                beneficialCount > aggravatingCount ? .beneficial : .aggravating
            // Keep the first finding of the dominant direction in the incoming deterministic order.
            if let pick = group.first(where: { $0.direction == dominant }) {
                kept.append(pick)
            }
        }
        return kept
    }

    // MARK: - Re-framing one survivor → a `.recommendation` Insight (templated copy, stable signalKey).

    private static func makeRecommendation(from finding: CorrelationEngine.CorrelationFinding) -> Insight {
        let headline: String
        switch finding.direction {
        case .beneficial:
            headline = RecommendationCopy.beneficialHeadline(factor: finding.factorLabel)
        case .aggravating:
            headline = RecommendationCopy.aggravatingHeadline(symptom: finding.targetLabel, factor: finding.factorLabel)
        }

        return Insight.derived(
            kind: .recommendation,
            window: .allTime,                                 // window-independent, like correlations (D-07).
            signalKey: recommendationSignalKey(from: finding.signalKey),
            headline: headline,
            detail: RecommendationCopy.detail,
            confidence: finding.confidence,                   // inherited from the correlation (D-02/D-05).
            points: [],                                       // words-only card — no chart.
            sampleSize: finding.pairedDays                    // gate enforces >= minSample(.recommendation).
        )
    }

    /// `recommendation.<correlation-stem>` — strip the `correlation.` prefix so the key is stable,
    /// value-independent, and window-independent (RESEARCH Pattern 3). If a finding's key unexpectedly
    /// lacks the prefix, fall back to prefixing the whole key (never crash).
    private static func recommendationSignalKey(from correlationSignalKey: String) -> String {
        if correlationSignalKey.hasPrefix(correlationPrefix) {
            return "recommendation." + correlationSignalKey.dropFirst(correlationPrefix.count)
        }
        return "recommendation." + correlationSignalKey
    }
}

// MARK: - Package-internal curated copy mirror (BYTE-IDENTICAL to the app's audited RecommendationsCopy).

/// PACKAGE-LOCAL MIRROR of the app's `RecommendationsCopy` (`Sources/EndoApp/Features/Insights/
/// RecommendationsCopy.swift`): the package CANNOT import the app target (SC1), so the SAME curated,
/// direction-aware, NON-prescriptive template strings are duplicated here as data and MUST stay
/// byte-identical with the app surface. The app surface is audited by `CopyAuditTests`; THIS mirror is
/// proven clean by the HonestyGate zero-forbidden-terms scan (causal + imperative, D-04/Pitfall 4).
///
/// SUBSTRING TRAP (Pitfall 2): every word is CURATED to avoid the forbidden substrings ENTIRELY — never
/// "higher"/"lower" ("her" ⊂ both, gendered), never "use"/"start"/"cut"/"avoid"/"should" (imperatives),
/// never "worsen"/"improve"/"reduce" (causal). The gate enforces; the curated copy avoids.
enum RecommendationCopy {

    /// BENEFICIAL framing: the factor's present-group fares BETTER → the "feel better" discussion prompt.
    static func beneficialHeadline(factor: String) -> String {
        "You tend to feel better on days with \(factor) — a pattern in your own data."
    }

    /// AGGRAVATING framing: the factor's present-group fares WORSE → a DESCRIPTIVE prompt. "more intense"
    /// (NOT "higher"/"lower" → the "her" trap), "something you might explore" (NOT "avoid"/"cut").
    static func aggravatingHeadline(symptom: String, factor: String) -> String {
        "Your \(symptom.lowercased()) tends to be more intense on days with \(factor) — something you might explore."
    }

    /// The shared detail line — the honest "your own data, not advice" framing (matches `ClaimsPolicy`).
    static let detail = "This is a pattern in your own data, not advice — something to discuss with your clinician."
}
