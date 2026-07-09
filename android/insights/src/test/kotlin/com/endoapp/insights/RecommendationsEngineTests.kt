// EndoInsights (:insights) — the RecommendationsEngine honesty spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift` (7 cases).
// A recommendation RE-FRAMES an already-gated `.correlation` finding (D-01: compose, NEVER re-derive); the
// engine emits `.recommendation` Insights ONLY from real surviving correlation candidates. The make-or-break
// tests are the ABSENCE assertions (suppressed/flood/under-sampled → ZERO) and the DIRECTION assertions
// (flipping the effect flips the template — Pitfall 1). Per Pattern 2 `today` is an injected `LocalDate`.
//
// The non-prescriptive copy is checked with inline banned-substring lists (mirrors `CorrelationEngineTests`);
// the durable `ForbiddenTerms` scan lands in 17-08, so the exhaustive-label audit scans the same inline
// causal ∪ imperative lists here rather than `ForbiddenTerms.violation(in:)`.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationsEngineTests {

    /** The producer entry point. Mirrors `CorrelationEngine.insights` signature (Calendar dropped). */
    private fun recommendations(snapshots: List<DaySnapshot>, todayOffset: Int = 0): List<Insight> =
        RecommendationsEngine.recommendations(from = snapshots, asOf = Fixtures.day(todayOffset))

    /** Only the `.recommendation` Insights (the engine routes through the same gate as every kind). */
    private fun recommendationInsights(snapshots: List<DaySnapshot>): List<Insight> =
        recommendations(snapshots).filter { it.kind == InsightKind.RECOMMENDATION }

    // The forbidden CAUSAL terms a recommendation must NEVER contain (mirrors the package non-causal posture).
    private val forbiddenCausalTerms = listOf(
        "cause", "causes", "trigger", "triggers", "because", "will ", "leads to", "linked to",
        "worsen", "improve", "reduce", "relieve", "aggravate",
    )

    // The forbidden IMPERATIVE substrings (D-04 directive verbs) a recommendation must NEVER contain.
    private val forbiddenImperatives = listOf(
        "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut",
    )

    private fun assertNoForbiddenTerms(insight: Insight) {
        val surface = (insight.headline + " " + insight.detail).lowercase()
        for (term in forbiddenCausalTerms) {
            assertFalse(surface.contains(term), "Recommendation copy must contain NO causal term \"$term\": \"$surface\".")
        }
        for (verb in forbiddenImperatives) {
            assertFalse(surface.contains(verb), "Recommendation copy must contain NO imperative substring \"$verb\": \"$surface\".")
        }
    }

    private fun assertCleanCopy(text: String) {
        val surface = text.lowercase()
        for (term in forbiddenCausalTerms) {
            assertFalse(surface.contains(term), "Copy must contain NO causal term \"$term\": \"$text\".")
        }
        for (verb in forbiddenImperatives) {
            assertFalse(surface.contains(verb), "Copy must contain NO imperative substring \"$verb\": \"$text\".")
        }
    }

    // MARK: - Surfaced: a strong AGGRAVATING association yields exactly one honest recommendation.

    @Test
    fun strongAggravating_surfacesRecommendation() {
        // `strongAssociationFixture` is the AGGRAVATING case: dairy PRESENT → HIGHER pelvic-pain severity.
        val recs = recommendationInsights(Fixtures.strongAssociationFixture())
        assertEquals(1, recs.size, "A strong, well-sampled aggravating association surfaces exactly one recommendation.")
        val rec = recs.first()
        assertEquals(InsightKind.RECOMMENDATION, rec.kind)
        assertEquals(TrendWindow.ALL_TIME, rec.window, "Recommendations are window-independent (mirror correlations).")
        assertTrue(rec.confidence >= InsightConfidence.LOW, "Only gate-eligible (>= LOW) survivors become recommendations.")
        assertTrue(rec.sampleSize >= 10, "A recommendation inherits its correlation's evidence count (>= minSample 10).")
        assertTrue(rec.points.isEmpty(), "A recommendation is a words-only card (no chart points).")
        assertEquals(
            "recommendation.food.dairy.symptom.pelvicPain", rec.signalKey,
            "The recommendation signalKey re-frames the correlation stem deterministically.",
        )
        assertNoForbiddenTerms(rec)
    }

    // MARK: - Direction: flipping the effect flips the template (Pitfall 1 — the make-or-break test).

    @Test
    fun beneficial_usesFeelBetterFraming() {
        // The BENEFICIAL fixture: activity PRESENT → LOWER pelvic-pain severity (meanDiff < 0).
        val beneficial = recommendationInsights(Fixtures.beneficialAssociationFixture()).first()
        assertTrue(
            beneficial.headline.lowercase().contains("feel better"),
            "A beneficial association uses the 'feel better' framing: \"${beneficial.headline}\".",
        )

        // The AGGRAVATING fixture must NOT use the beneficial phrase — flipping the effect flips the template.
        val aggravating = recommendationInsights(Fixtures.strongAssociationFixture()).first()
        assertFalse(
            aggravating.headline.lowercase().contains("feel better"),
            "An aggravating association must NOT use the beneficial 'feel better' framing: \"${aggravating.headline}\".",
        )

        assertNoForbiddenTerms(beneficial)
        assertNoForbiddenTerms(aggravating)
    }

    // MARK: - Withheld: suppressed / sparse / multiple-comparison-flood → ZERO recommendations (SC3, Pitfall 5).

    @Test
    fun suppressedUser_yieldsZeroRecommendations() {
        // A suppressed user with a real non-cyclic co-occurrence: the engine must NEVER fabricate a
        // recommendation outside of real surviving correlation candidates.
        assertTrue(
            recommendationInsights(Fixtures.suppressedUserCorrelationFixture()).isEmpty(),
            "A suppressed user yields ZERO recommendations (never fabricated).",
        )
        // A multiple-comparison flood of pure noise: BH-FDR crushes all candidates → ZERO recommendations.
        assertTrue(
            recommendationInsights(Fixtures.multipleComparisonFloodFixture()).isEmpty(),
            "A multiple-comparison flood yields ZERO recommendations (never fabricated).",
        )
        // An under-sampled association: below the sample floor → no correlation survives → ZERO recommendations.
        assertTrue(
            recommendationInsights(Fixtures.underSampledAssociationFixture()).isEmpty(),
            "An under-sampled association yields ZERO recommendations.",
        )
    }

    // MARK: - Top-N cap: at most 3 recommendations (D-02, RESEARCH A3) in deterministic order.

    @Test
    fun topNCap_isRespected() {
        val recs = recommendationInsights(Fixtures.strongAssociationFixture())
        assertTrue(recs.size <= 3, "The recommendation count is capped at top-N == 3 (D-02).")
    }

    // MARK: - WR-04: EXHAUSTIVE templated-label audit over the ACTUAL interpolation domain.

    /** Every production `Factor.displayLabel` — enumerated across all parametric families (must mirror
     *  `CorrelationEngine.enumerateFactors`). */
    private val allFactorLabels: List<String>
        get() {
            val factors = mutableListOf<Factor>()
            factors += FoodTag.entries.map { Factor.Food(it) }
            factors += Factor.ActivityPresent
            factors += ActivityType.entries.map { Factor.Activity(it) }
            factors += Factor.SleepPoor
            factors += Factor.StressHigh
            factors += Factor.MoodLow
            factors += Factor.MedicationTaken
            factors += Factor.PrnTaken
            factors += CyclePhase.entries.map { Factor.CyclePhaseFactor(it) }
            return factors.map { it.displayLabel }
        }

    /** Every production symptom label as interpolated by the aggravating headline. */
    private val allSymptomLabels: List<String>
        get() = SymptomType.entries.map { CyclePhaseMapper.displayName(it) }

    @Test
    fun everyProductionLabel_rendersCleanRecommendationCopy() {
        assertTrue(allFactorLabels.isNotEmpty(), "The factor-label audit surface must be non-empty.")
        assertTrue(allSymptomLabels.isNotEmpty(), "The symptom-label audit surface must be non-empty.")

        // Beneficial headline: interpolates EVERY factor label.
        for (factor in allFactorLabels) {
            assertCleanCopy(RecommendationCopy.beneficialHeadline(factor = factor))
        }

        // Aggravating headline: interpolates EVERY (symptom × factor) combination — the full domain.
        for (symptom in allSymptomLabels) {
            for (factor in allFactorLabels) {
                assertCleanCopy(RecommendationCopy.aggravatingHeadline(symptom = symptom, factor = factor))
            }
        }

        // The bleeding presence target interpolates the literal "bleeding" label (not a SymptomType).
        assertCleanCopy(RecommendationCopy.aggravatingHeadline(symptom = "bleeding", factor = allFactorLabels[0]))
    }

    // MARK: - Per-target collapse: two factors → the SAME bleeding target collapse to ONE recommendation (WR-01).

    @Test
    fun twoFactorsSameBleedingTarget_collapseToOneRecommendation() {
        val recs = recommendationInsights(Fixtures.twoFactorsSameBleedingTargetFixture())
        // Both factors target `bleeding`; after collapse exactly ONE bleeding recommendation survives.
        val bleedingRecs = recs.filter { it.signalKey.endsWith(".bleeding") }
        assertEquals(
            1, bleedingRecs.size,
            "Two factors on the SAME bleeding target must collapse to ONE recommendation (WR-01), got: ${recs.map { it.signalKey }}.",
        )
        val rec = bleedingRecs.first()
        assertEquals(InsightKind.RECOMMENDATION, rec.kind)
        assertTrue(rec.signalKey.startsWith("recommendation."))
        assertNoForbiddenTerms(rec)
    }

    // MARK: - Determinism: the same fixture twice yields exactly equal `[Insight]`.

    @Test
    fun output_isDeterministic() {
        val first = recommendationInsights(Fixtures.strongAssociationFixture())
        val second = recommendationInsights(Fixtures.strongAssociationFixture())
        assertEquals(first, second, "Same inputs → exactly equal recommendation [Insight] (Equatable, no RNG/clock).")
        assertNotNull(first)
    }
}
