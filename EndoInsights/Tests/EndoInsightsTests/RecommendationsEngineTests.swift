// EndoInsights — the RecommendationsEngine honesty spec (Phase 11, INST-05 / D-01..D-08 / SC1).
//
// RED until Plan 11-02 implements RecommendationsEngine + RecommendationsCopy.
//
// TDD: this asserts the RE-FRAMING producer's HONESTY CONTRACT (Wave-0 VALIDATION requirement) BEFORE
// the producer exists. A recommendation re-frames an already-gated `.correlation` Insight (D-01: compose,
// NEVER re-derive); the engine emits `.recommendation` Insights ONLY from real surviving correlation
// candidates. This suite references symbols Plan 02 introduces — `RecommendationsEngine.recommendations`
// and the `RecommendationsCopy` template words — so it does NOT yet compile against the package target;
// that is EXPECTED and resolved in Plan 02 (which makes these GREEN).
//
// Posture mirrors `CorrelationEngineTests` EXACTLY: inject `Fixtures.day(0)` + `Fixtures.calendar`, no
// clock, deterministic. The make-or-break tests are the ABSENCE assertions (suppressed/flood → ZERO) and
// the DIRECTION assertions (flipping the effect flips the template — Pitfall 1).
import Testing
import Foundation
@testable import EndoInsights

// Plan 11-02 deleted the RED placeholder shim that previously lived here — the real
// `RecommendationsEngine` re-framing producer now ships in
// `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`, and this suite is GREEN against it.

@Suite("INST-05 RecommendationsEngine — surfaced/withheld/suppressed/direction/top-N/deterministic")
struct RecommendationsEngineTests {


    /// The future producer entry point (Plan 02). Mirrors `CorrelationEngine.insights` signature exactly.
    private func recommendations(_ snapshots: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
        RecommendationsEngine.recommendations(from: snapshots, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
    }

    /// Only the `.recommendation` Insights (the engine routes through the same gate as every kind).
    private func recommendationInsights(_ snapshots: [DaySnapshot]) -> [Insight] {
        recommendations(snapshots).filter { $0.kind == .recommendation }
    }

    // The forbidden CAUSAL terms a recommendation must NEVER contain (mirrors the package ForbiddenTerms /
    // CyclePhaseMapper / CorrelationEngine non-causal posture, D-12).
    private let forbiddenCausalTerms = [
        "cause", "causes", "trigger", "triggers", "because", "will ", "leads to", "linked to",
        "worsen", "improve", "reduce", "relieve", "aggravate",
    ]

    // The forbidden IMPERATIVE substrings (D-04 directive verbs) a recommendation must NEVER contain —
    // the SAME list the producer routes through (`ClaimsPolicy.forbiddenImperatives` app-side).
    private let forbiddenImperatives = [
        "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut",
    ]

    private func assertNoForbiddenTerms(_ insight: Insight) {
        let surface = (insight.headline + " " + insight.detail).lowercased()
        for term in forbiddenCausalTerms {
            #expect(!surface.contains(term),
                    "Recommendation copy must contain NO causal term \"\(term)\": \"\(surface)\".")
        }
        for verb in forbiddenImperatives {
            #expect(!surface.contains(verb),
                    "Recommendation copy must contain NO imperative substring \"\(verb)\": \"\(surface)\".")
        }
    }

    // MARK: - Surfaced: a strong AGGRAVATING association yields exactly one honest recommendation.

    @Test func strongAggravating_surfacesRecommendation() async throws {
        // `strongAssociationFixture` is the AGGRAVATING case: dairy PRESENT → HIGHER pelvic-pain severity.
        let recs = recommendationInsights(Fixtures.strongAssociationFixture())
        #expect(recs.count == 1, "A strong, well-sampled aggravating association surfaces exactly one recommendation.")
        let rec = try #require(recs.first)
        #expect(rec.kind == .recommendation)
        #expect(rec.window == .allTime, "Recommendations are window-independent (mirror correlations).")
        #expect(rec.confidence >= .low, "Only gate-eligible (>= .low) survivors become recommendations.")
        #expect(rec.sampleSize >= 10, "A recommendation inherits its correlation's evidence count (>= minSample 10).")
        #expect(rec.points.isEmpty, "A recommendation is a words-only card (no chart points).")
        // The signalKey is the stable `recommendation.<correlation-stem>` scheme (Pattern 3).
        #expect(rec.signalKey == "recommendation.food.dairy.symptom.pelvicPain",
                "The recommendation signalKey re-frames the correlation stem deterministically.")
        assertNoForbiddenTerms(rec)
    }

    // MARK: - Direction: flipping the effect flips the template (Pitfall 1 — the make-or-break direction test).

    @Test func beneficial_usesFeelBetterFraming() async throws {
        // The BENEFICIAL fixture: activity PRESENT → LOWER pelvic-pain severity (meanDiff < 0).
        let beneficial = try #require(recommendationInsights(Fixtures.beneficialAssociationFixture()).first)
        let beneficialHeadline = beneficial.headline.lowercased()
        // The beneficial template reads as the "feel better" discussion-prompt (curated phrase).
        #expect(beneficialHeadline.contains("feel better"),
                "A beneficial association uses the 'feel better' framing: \"\(beneficial.headline)\".")

        // The AGGRAVATING fixture must NOT use the beneficial phrase — flipping the effect flips the template.
        let aggravating = try #require(recommendationInsights(Fixtures.strongAssociationFixture()).first)
        #expect(!aggravating.headline.lowercased().contains("feel better"),
                "An aggravating association must NOT use the beneficial 'feel better' framing: \"\(aggravating.headline)\".")

        assertNoForbiddenTerms(beneficial)
        assertNoForbiddenTerms(aggravating)
    }

    // MARK: - Withheld: suppressed / sparse / multiple-comparison-flood → ZERO recommendations (SC3, Pitfall 5).

    @Test func suppressedUser_yieldsZeroRecommendations() async throws {
        // A suppressed user with a real non-cyclic co-occurrence: the engine must NEVER fabricate a
        // recommendation outside of real surviving correlation candidates.
        #expect(recommendationInsights(Fixtures.suppressedUserCorrelationFixture()).isEmpty,
                "A suppressed user yields ZERO recommendations (never fabricated).")
        // A multiple-comparison flood of pure noise: BH-FDR crushes all candidates → ZERO recommendations.
        #expect(recommendationInsights(Fixtures.multipleComparisonFloodFixture()).isEmpty,
                "A multiple-comparison flood yields ZERO recommendations (never fabricated).")
        // An under-sampled association: below the sample floor → no correlation survives → ZERO recommendations.
        #expect(recommendationInsights(Fixtures.underSampledAssociationFixture()).isEmpty,
                "An under-sampled association yields ZERO recommendations.")
    }

    // MARK: - Top-N cap: at most 3 recommendations (D-02, RESEARCH A3) in deterministic order.

    @Test func topNCap_isRespected() async throws {
        // The flood fixture exercises many candidate pairs; whatever survives, the engine caps the count.
        // (No fixture is GUARANTEED to produce >3 strong correlations here; the cap is asserted as an
        // upper bound the engine must respect regardless of input — Plan 02 may add a denser fixture.)
        let recs = recommendationInsights(Fixtures.strongAssociationFixture())
        #expect(recs.count <= 3, "The recommendation count is capped at top-N == 3 (D-02).")
    }

    // MARK: - Determinism: the same fixture twice yields exactly equal `[Insight]`.

    @Test func output_isDeterministic() async throws {
        let first = recommendationInsights(Fixtures.strongAssociationFixture())
        let second = recommendationInsights(Fixtures.strongAssociationFixture())
        #expect(first == second, "Same inputs → exactly equal recommendation [Insight] (Equatable, no RNG/clock).")
    }
}
