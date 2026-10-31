package com.endoapp.app.ui.insights

import com.endoapp.insights.ExploreSeriesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the ported [ScatterDeepLink] resolver (SCAT-04) — PURE, no rendering. The `.correlation` card
 * affordance depends on this: it consumes ONLY the STRUCTURAL `signalKey` (never headline/detail prose —
 * T-22-01j) and maps it to two TYPED quantitative axes, or null (affordance HIDDEN).
 *
 * Proven here:
 *  • `correlation.stress.high.symptom.pelvicPain` → (stress, symptom.pelvicPain.severity).
 *  • `correlation.mood.low.symptom.fatigue`       → (mood, symptom.fatigue.severity).
 *  • `correlation.stress.high.bleeding` unsuppressed → (stress, bleeding.intensity); suppressed → null
 *    (the suppression gate, T-22-01k — a suppressed user never reaches a bleeding axis via the deep-link).
 *  • a presence-only factor (`food.dairy`), `sleep.poor` (no quality→hours bridge), and malformed keys → null.
 *  • a multi-dot key is segmented by KNOWN SUFFIX, never mis-split by a naive `split(".")`.
 */
class ScatterDeepLinkTest {

    private fun series(id: String) = ExploreSeriesCatalog.all.first { it.id == id }

    // --- The mapping path: factor + symptom target → typed axes ---

    @Test
    fun stressHighSymptom_resolvesToStressAndSymptomSeverityAxes() {
        val axes = ScatterDeepLink.resolve("correlation.stress.high.symptom.pelvicPain", suppressed = false)
        assertEquals(series("stress") to series("symptom.pelvicPain.severity"), axes)
    }

    @Test
    fun moodLowSymptom_resolvesToMoodAndSymptomSeverityAxes() {
        val axes = ScatterDeepLink.resolve("correlation.mood.low.symptom.fatigue", suppressed = false)
        assertEquals(series("mood") to series("symptom.fatigue.severity"), axes)
    }

    // --- The bleeding suppression gate (T-22-01k) ---

    @Test
    fun stressHighBleeding_unsuppressed_resolvesToBleedingIntensity() {
        val axes = ScatterDeepLink.resolve("correlation.stress.high.bleeding", suppressed = false)
        assertEquals(series("stress") to series("bleeding.intensity"), axes)
    }

    @Test
    fun stressHighBleeding_suppressed_resolvesToNull() {
        assertNull(ScatterDeepLink.resolve("correlation.stress.high.bleeding", suppressed = true))
    }

    // --- Presence-only factor / sleep-quality → null (affordance HIDDEN) ---

    @Test
    fun presenceOnlyFactor_food_resolvesToNull() {
        // food.dairy is a presence factor — no quantitative axis (RESOLVED-Q1).
        assertNull(ScatterDeepLink.resolve("correlation.food.dairy.symptom.pelvicPain", suppressed = false))
    }

    @Test
    fun sleepPoor_resolvesToNull() {
        // sleep.poor has no quality→hours bridge in v1.1 — the affordance is hidden.
        assertNull(ScatterDeepLink.resolve("correlation.sleep.poor.symptom.pelvicPain", suppressed = false))
    }

    // --- Defensive parse: malformed / unrecognized keys → null, never a crash ---

    @Test
    fun malformedKeys_resolveToNull() {
        val bad = listOf(
            "",
            "totally.bogus",
            "correlation.",
            "correlation.stress.high",                       // no target suffix
            "correlation.stress.high.symptom.notAType",      // unknown SymptomType raw
            "correlation..bleeding",                         // empty factor key
            "stress.high.symptom.pelvicPain",                // missing the correlation. prefix
        )
        bad.forEach { key ->
            assertNull("expected null for \"$key\"", ScatterDeepLink.resolve(key, suppressed = false))
        }
    }

    // --- Multi-dot factor.key is segmented by KNOWN SUFFIX, never mis-split ---

    @Test
    fun multiDotFactorKey_isSegmentedByKnownSuffix_notMisSplit() {
        // The factor.key `stress.high` is itself dotted; a naive split(".") would mis-segment. The known
        // `.symptom.<raw>` suffix keeps the factor whole → the stress axis resolves correctly.
        val axes = ScatterDeepLink.resolve("correlation.stress.high.symptom.backLegPain", suppressed = false)
        assertEquals(series("stress") to series("symptom.backLegPain.severity"), axes)
    }
}
