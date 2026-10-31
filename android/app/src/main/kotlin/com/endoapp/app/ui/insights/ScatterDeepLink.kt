// SCAT-04 — the pure, app-target resolver that maps a `.correlation` Insight's PUBLIC `signalKey` to an
// OPTIONAL pair of quantitative scatter axes (or null → the deep-link affordance is HIDDEN). The Android
// port of `ScatterDeepLink.swift`, verbatim in intent.
//
// LOAD-BEARING HONESTY CONTRACT (T-22-01j / the T-16-14 honesty discipline):
//   • The INPUT is the STRUCTURAL `signalKey` — a typed-by-convention machine key
//     (`correlation.<factor.key>.symptom.<SymptomType.raw>` or `correlation.<factor.key>.bleeding`), NOT
//     audited human prose. This resolver NEVER reads the Insight's headline or detail copy; it consumes the
//     stable identity key ONLY. A reviewer should NOT flag this as copy-parsing.
//   • The OUTPUT is a TYPED (ExploreSeries, ExploreSeries) value looked up in the catalog — the deep-link
//     carries this value; it does not re-derive axes downstream.
//
// DEFENSIVE PARSE (ASVS V5 / T-22-01): any malformed / unrecognized key yields null, NEVER a crash. The key
// contains multiple dots (the factor.key is itself dotted, e.g. `stress.high`, `food.dairy`; the target too,
// `symptom.pelvicPain`), so parsing is by KNOWN SUFFIX (`.bleeding` / `.symptom.<known raw>`), NEVER a naive
// `split(".")` that would mis-segment a multi-dot key.
//
// SUPPRESSION GATE (T-22-01k): a `bleeding` target under `suppressed` resolves to null — a suppressed user
// can never reach a bleeding axis via the deep-link (mirrors the accessor gate + the picker filter).
//
// RESOLVED-Q1: ONLY `stress.high → "stress"` and `mood.low → "mood"` map to a quantitative axis.
// `sleep.poor` (no quality→hours bridge in v1.1) and ALL presence-only factors (food / activity /
// medication / prn / cyclePhase) resolve to null → the affordance is HIDDEN.
package com.endoapp.app.ui.insights

import com.endoapp.insights.ExploreSeries
import com.endoapp.insights.ExploreSeriesCatalog
import com.endoapp.insights.SymptomType

/**
 * Maps a `.correlation` `Insight.signalKey` to two quantitative scatter axes, or null (HIDE the affordance).
 * A PURE object — fully unit-testable WITHOUT rendering (mirrors the `ExploreSelection.offered` pure-value
 * posture).
 */
object ScatterDeepLink {

    /** The `correlation.` prefix every correlation signalKey carries (CorrelationEngine). */
    private const val CORRELATION_PREFIX = "correlation."

    /** The literal bleeding-target fragment (CorrelationEngine `PresenceTarget.keyFragment`). */
    private const val BLEEDING_FRAGMENT = "bleeding"

    /** A parsed target: either the bleeding axis, or a symptom-severity axis for a known SymptomType. */
    private sealed interface Target {
        data object Bleeding : Target
        data class Symptom(val raw: String) : Target
    }

    /**
     * Resolve a `.correlation` [signalKey] to an (x, y) pair of quantitative [ExploreSeries], or null.
     *
     * Returns a non-null pair ONLY when BOTH the factor and the target map to a catalogued series AND both
     * of those series are quantitative. Every other input — a presence-only factor, `sleep.poor`, a
     * suppressed bleeding target, or any malformed/unrecognized key — yields null (affordance hidden).
     *
     * @param signalKey the STRUCTURAL public key (never audited prose).
     * @param suppressed the bleeding-suppression flag; a `bleeding` target under suppression → null.
     * @param catalog the series catalog to look ids up in (default the shipped catalog; injectable for tests).
     */
    fun resolve(
        signalKey: String,
        suppressed: Boolean,
        catalog: List<ExploreSeries> = ExploreSeriesCatalog.all,
    ): Pair<ExploreSeries, ExploreSeries>? {
        // 1. Must be a correlation key; strip the prefix. Anything else → null.
        if (!signalKey.startsWith(CORRELATION_PREFIX)) return null
        val body = signalKey.removePrefix(CORRELATION_PREFIX)
        if (body.isEmpty()) return null

        // 2. Detect the TARGET by KNOWN suffix (never a naive split — the factor.key is itself dotted).
        val (factorKey, target) = splitFactorAndTarget(body) ?: return null

        // 3. factor.key → optional quantitative X series id (RESOLVED-Q1: only stress.high / mood.low map).
        val xId = quantitativeFactorAxisId(factorKey) ?: return null

        // 4. target → optional quantitative Y series id (bleeding gated by suppression).
        val yId = quantitativeTargetAxisId(target, suppressed) ?: return null

        // 5. Look BOTH ids up in the catalog (never construct an ExploreSeries) — a catalog edit can't drift.
        val x = catalog.firstOrNull { it.id == xId } ?: return null
        val y = catalog.firstOrNull { it.id == yId } ?: return null
        if (!x.scaleKind.isQuantitative || !y.scaleKind.isQuantitative) return null

        return x to y
    }

    /**
     * Split the prefix-stripped body `<factor.key>.<target>` into (factorKey, target) by recognizing the
     * target as a KNOWN suffix. Returns null if the tail is neither `.bleeding` nor `.symptom.<known raw>`,
     * or if no non-empty factor.key precedes it.
     */
    private fun splitFactorAndTarget(body: String): Pair<String, Target>? {
        // Bleeding target: the body ends in `.bleeding`.
        val bleedingSuffix = ".$BLEEDING_FRAGMENT"
        if (body.endsWith(bleedingSuffix)) {
            val factorKey = body.removeSuffix(bleedingSuffix)
            if (factorKey.isEmpty()) return null
            return factorKey to Target.Bleeding
        }

        // Symptom target: the body ends in `.symptom.<raw>` for a KNOWN SymptomType raw. Enumerate from
        // SymptomType.entries so a multi-dot factor.key can never mis-segment (the catalog ids derive from
        // the SAME enum, so parse + catalog share one source of truth).
        for (type in SymptomType.entries) {
            val symptomSuffix = ".symptom.${type.raw}"
            if (body.endsWith(symptomSuffix)) {
                val factorKey = body.removeSuffix(symptomSuffix)
                if (factorKey.isEmpty()) return null
                return factorKey to Target.Symptom(type.raw)
            }
        }

        return null
    }

    /**
     * Map a `factor.key` to its quantitative axis catalog id, or null (presence-only / sleep-quality /
     * unrecognized → HIDE affordance). ONLY `stress.high` and `mood.low` map in v1.1 (RESOLVED-Q1).
     */
    private fun quantitativeFactorAxisId(factorKey: String): String? = when (factorKey) {
        "stress.high" -> "stress"
        "mood.low" -> "mood"
        // sleep.poor (no quality→hours bridge), food.*, activity.*, medication.taken, prn.taken,
        // cyclePhase.* — all presence/categorical, no quantitative axis.
        else -> null
    }

    /** Map a parsed target to its quantitative axis catalog id. A `bleeding` target under suppression → null. */
    private fun quantitativeTargetAxisId(target: Target, suppressed: Boolean): String? = when (target) {
        is Target.Symptom -> "symptom.${target.raw}.severity"
        Target.Bleeding -> if (suppressed) null else "bleeding.intensity"
    }
}
