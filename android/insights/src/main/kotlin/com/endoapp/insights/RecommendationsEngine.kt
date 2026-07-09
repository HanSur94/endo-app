// EndoInsights (:insights) — the personalized-recommendations producer.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift` (Phase 11, Plan 02,
// INST-05 / D-01..D-08 / SC1 / V7). THE substantive honesty surface of the phase: a recommendation that
// reads as a DISCUSSION PROMPT, never an instruction, and is provably ABSENT for users without qualifying
// data. Pure-JVM (no android./androidx.; BoundaryLint is the proof). Mirrors `CorrelationEngine` /
// `CyclePhaseMapper` EXACTLY: an `object` namespace, one `recommendations(from, asOf)` entry, `Equatable`
// `List<Insight>` output. Per Pattern 2 the injected Swift `Calendar` is DROPPED (LocalDate is day-identity).
//
// COMPOSE, NEVER RE-DERIVE (D-01): the engine consumes `CorrelationEngine.scoredCandidates(...)` — the typed
// survivors already gated/sorted/top-N by the correlation pipeline — and runs NO statistics of its own. A
// recommendation exists ONLY for a real surviving correlation candidate; a suppressed / sparse /
// multiple-comparison-flood user yields ZERO recommendations (D-08/SC3 — honest absence by omission).
//
// DIRECTION-AWARE COPY (D-03/D-04): the typed `finding.direction` (recovered from the SIGNED effect in the
// correlation engine, NEVER from parsed copy) selects the template — `BENEFICIAL` → the "feel better" prompt,
// `AGGRAVATING` → a descriptive "more intense … something you might explore" prompt. Copy comes ONLY from the
// curated `RecommendationCopy` templates below, never free text.
//
// APP↔PACKAGE COPY MIRROR: the package CANNOT import the app (SC1), so the curated template strings live HERE
// in a package-internal `RecommendationCopy`, BYTE-IDENTICAL to the app's audited `RecommendationsCopy`.
// `RecommendationsDriftGuard` (public) forwards those strings so a Phase-18 app-side test can assert byte
// identity. (The `ForbiddenTerms` list forwarders the Swift shim also exposes are deferred to 17-08, where
// `ForbiddenTerms` lands — see the SUMMARY deviation note.)
//
// STABLE SIGNALKEY (D-07): a recommendation's signalKey is `recommendation.<correlation-stem>` (the
// `correlation.` prefix stripped) with the `.allTime` window — value-INDEPENDENT and window-INDEPENDENT (like
// correlations), so dismissal + cache survive recompute.
package com.endoapp.insights

import java.time.LocalDate

/** Pure `.recommendation` producer. No store, no UI, no I/O, no logging — value-in / value-out. */
object RecommendationsEngine {

    /** The recommendation count cap (D-02 / RESEARCH A3) — at most this many `.recommendation` Insights
     *  surface, taken in the deterministic correlation-survivor order. */
    const val topN: Int = 3

    /** The package-local prefix on a correlation signalKey, stripped to form the stable recommendation stem. */
    private const val correlationPrefix: String = "correlation."

    // MARK: - The single public entry point (mirrors CorrelationEngine.insights EXACTLY, minus Calendar).

    /** Re-frame the typed `CorrelationEngine.scoredCandidates(...)` survivors into top-N (<= [topN])
     *  direction-aware, non-prescriptive `.recommendation` Insights. NO re-derived statistics (D-01). */
    fun recommendations(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> {
        // HONEST ABSENCE FOR A SUPPRESSED USER (D-08/SC3): a user who has turned insight derivation OFF (any
        // `suppressed` snapshot) gets ZERO recommendations — the most prescriptive-adjacent surface is MORE
        // conservative than raw correlations. Never fabricate a "do this" for a suppressed user.
        if (from.any { it.suppressed }) return emptyList()

        // The typed survivors — ALREADY gate-filtered, deterministically sorted, top-N-by-correlation. The
        // recommendation engine runs NO statistics; it only re-frames these (D-01).
        val findings = CorrelationEngine.scoredCandidates(from = from, asOf = asOf)

        // Honest absence by omission: an Insight is ONLY ever produced by mapping a REAL surviving finding —
        // never appended outside this. Sparse/flood → no findings → [].
        if (findings.isEmpty()) return emptyList()

        // ONE RECOMMENDATION PER SYMPTOM TARGET (D-02): collapse per symptom target to a SINGLE recommendation,
        // framed by the user's DOMINANT direction for that symptom.
        val chosen = dominantPerSymptom(findings)

        return chosen
            .take(topN)
            .map { makeRecommendation(it) }
    }

    /** Collapse the survivors to ONE per symptom target (D-02). For each target (keyed by the post-factor
     *  signalKey stem), pick the direction with MORE supporting findings (tie → `AGGRAVATING`, the cautionary
     *  default), then keep that target's FIRST finding of the chosen direction in the incoming deterministic
     *  order. Returns the kept findings in incoming order — so `.take(topN)` stays deterministic. */
    private fun dominantPerSymptom(
        findings: List<CorrelationEngine.CorrelationFinding>,
    ): List<CorrelationEngine.CorrelationFinding> {
        // Group by the actual TARGET (symptom type or bleeding), keyed by the signalKey with its leading
        // `correlation.<factor.key>.` stem stripped — NOT a fixed trailing-suffix length. `<factor.key>` is
        // ALWAYS two dot-components; the target fragment is `symptom.<type>` (two) OR `bleeding` (one). Drop
        // the leading `correlation` + the two factor components so the key is the TARGET identity regardless
        // of factor or fragment length (WR-01). Fall back to the whole signalKey if the shape is unexpected.
        fun targetKey(f: CorrelationEngine.CorrelationFinding): String {
            val parts = f.signalKey.split(".")
            if (parts.size < 4) return f.signalKey // correlation.<a>.<b>.<target…>
            return parts.drop(3).joinToString(".") // "symptom.pelvicPain" or "bleeding"
        }

        val orderedTargets = mutableListOf<String>()
        val byTarget = HashMap<String, MutableList<CorrelationEngine.CorrelationFinding>>()
        for (f in findings) {
            val key = targetKey(f)
            if (!byTarget.containsKey(key)) orderedTargets.add(key)
            byTarget.getOrPut(key) { mutableListOf() }.add(f)
        }

        val kept = mutableListOf<CorrelationEngine.CorrelationFinding>()
        for (target in orderedTargets) {
            val group = byTarget[target] ?: emptyList()
            val beneficialCount = group.count { it.direction == CorrelationEngine.RecommendationDirection.BENEFICIAL }
            val aggravatingCount = group.size - beneficialCount
            // Dominant direction: more findings wins; a tie defers to the cautionary aggravating framing.
            val dominant: CorrelationEngine.RecommendationDirection =
                if (beneficialCount > aggravatingCount) {
                    CorrelationEngine.RecommendationDirection.BENEFICIAL
                } else {
                    CorrelationEngine.RecommendationDirection.AGGRAVATING
                }
            // Keep the first finding of the dominant direction in the incoming deterministic order.
            group.firstOrNull { it.direction == dominant }?.let { kept.add(it) }
        }
        return kept
    }

    // MARK: - Re-framing one survivor → a `.recommendation` Insight (templated copy, stable signalKey).

    private fun makeRecommendation(finding: CorrelationEngine.CorrelationFinding): Insight {
        val headline: String = when (finding.direction) {
            CorrelationEngine.RecommendationDirection.BENEFICIAL ->
                RecommendationCopy.beneficialHeadline(factor = finding.factorLabel)
            CorrelationEngine.RecommendationDirection.AGGRAVATING ->
                RecommendationCopy.aggravatingHeadline(symptom = finding.targetLabel, factor = finding.factorLabel)
        }

        return Insight.derived(
            kind = InsightKind.RECOMMENDATION,
            window = TrendWindow.ALL_TIME, // window-independent, like correlations (D-07).
            signalKey = recommendationSignalKey(finding.signalKey),
            headline = headline,
            detail = RecommendationCopy.detail,
            confidence = finding.confidence, // inherited from the correlation (D-02/D-05).
            points = emptyList(), // words-only card — no chart.
            sampleSize = finding.pairedDays, // gate enforces >= minSample(.recommendation).
        )
    }

    /** `recommendation.<correlation-stem>` — strip the `correlation.` prefix so the key is stable,
     *  value-independent, and window-independent. If a finding's key unexpectedly lacks the prefix, fall
     *  back to prefixing the whole key (never crash). */
    private fun recommendationSignalKey(correlationSignalKey: String): String =
        if (correlationSignalKey.startsWith(correlationPrefix)) {
            "recommendation." + correlationSignalKey.removePrefix(correlationPrefix)
        } else {
            "recommendation.$correlationSignalKey"
        }
}

// MARK: - Package-internal curated copy mirror (BYTE-IDENTICAL to the app's audited RecommendationsCopy).

/** PACKAGE-LOCAL MIRROR of the app's `RecommendationsCopy`: the package CANNOT import the app target (SC1),
 *  so the SAME curated, direction-aware, NON-prescriptive template strings are duplicated here as data and
 *  MUST stay byte-identical with the app surface.
 *
 *  SUBSTRING TRAP: every word is CURATED to avoid the forbidden substrings ENTIRELY — never "higher"/"lower"
 *  ("her" ⊂ both, gendered), never "use"/"start"/"cut"/"avoid"/"should" (imperatives), never
 *  "worsen"/"improve"/"reduce" (causal). The gate (17-08) enforces; the curated copy avoids. */
internal object RecommendationCopy {

    /** BENEFICIAL framing: the factor's present-group fares BETTER → the "feel better" discussion prompt. */
    fun beneficialHeadline(factor: String): String =
        "You tend to feel better on days with $factor — a pattern in your own data."

    /** AGGRAVATING framing: the factor's present-group fares WORSE → a DESCRIPTIVE prompt. "more intense"
     *  (NOT "higher"/"lower" → the "her" trap), "something you might explore" (NOT "avoid"/"cut"). */
    fun aggravatingHeadline(symptom: String, factor: String): String =
        "Your ${symptom.lowercase()} tends to be more intense on days with $factor — something you might explore."

    /** The shared detail line — the honest "your own data, not advice" framing (matches `ClaimsPolicy`). */
    const val detail: String =
        "This is a pattern in your own data, not advice — something to discuss with your clinician."
}

// MARK: - Cross-boundary drift-guard surface — the ONLY public window onto the copy mirror.

/** PUBLIC, TEST-SUPPORT-ONLY window onto the package-internal `RecommendationCopy` mirror the APP duplicates
 *  and must keep in sync. The app test target sees ONLY the package's PUBLIC API (it imports `:insights` as a
 *  release product), so the package-internal `RecommendationCopy` strings are invisible to it. This shim
 *  forwards those mirrored strings — and NOTHING else — so an app-side test can assert byte-identity and a
 *  future edit to ONE side without the other fails a test. The forwarded values are pure data with no
 *  behavior; exposing them does NOT widen the engine's API or the Foundation-only boundary.
 *
 *  NOTE (17-08): the Swift `RecommendationsDriftGuard` also forwards `ForbiddenTerms.imperative`/`.causal`.
 *  Those lists land in 17-08 (`ForbiddenTerms.kt`); the forwarders are added there so this file compiles
 *  before `ForbiddenTerms` exists. */
object RecommendationsDriftGuard {

    /** The package-internal `RecommendationCopy.beneficialHeadline` mirror, for byte-identity comparison
     *  against the app's audited `RecommendationsCopy.beneficialHeadline`. */
    fun packageBeneficialHeadline(factor: String): String = RecommendationCopy.beneficialHeadline(factor)

    /** The package-internal `RecommendationCopy.aggravatingHeadline` mirror. */
    fun packageAggravatingHeadline(symptom: String, factor: String): String =
        RecommendationCopy.aggravatingHeadline(symptom, factor)

    /** The package-internal `RecommendationCopy.detail` mirror. */
    val packageDetail: String get() = RecommendationCopy.detail
}
