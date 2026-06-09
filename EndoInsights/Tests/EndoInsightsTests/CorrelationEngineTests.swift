// EndoInsights — the CorrelationEngine honesty spec (Phase 7, INST-03 / D-04..D-13/D-16 / SC1).
//
// TDD: these assert the .correlation producer's HONESTY GATES — suppression IS the deliverable. A
// strong, well-sampled association surfaces with an honest effect-size headline + non-causal copy; an
// under-sampled (n=9), a spurious (near-zero δ), and a multiple-comparison flood (K≈100+ pure noise)
// each surface ZERO; a suppressed user has NO cycle-phase correlation (D-04 by construction); output is
// deterministic. `today` is injected (no `Date.now`); the fixed-UTC `Fixtures.calendar` stays stable.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-03 CorrelationEngine — surfaced/withheld/suppressed/deterministic")
struct CorrelationEngineTests {

    private func correlations(_ snapshots: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
        CorrelationEngine.insights(from: snapshots, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
    }

    // MARK: Task 1 — named threshold constants are the ONLY judgement calls; assert their locked values.

    @Test func factorThresholds_areLockedConstants() async throws {
        #expect(Factor.highStressThreshold == 7, "High-stress threshold is locked at 7 (07-RESEARCH A2).")
        #expect(Factor.lowMoodThreshold == 2, "Low-mood threshold is locked at 2 (07-RESEARCH A2).")
        #expect(Factor.perGroupMinimum == 3, "Per-group minimum is locked at 3 (07-RESEARCH A5).")
    }

    // MARK: Task 1 — three-state binarization spot-checks (nil is NEVER coerced to 0).

    @Test func stressBinarization_threeState() async throws {
        // A day that logged a symptom but NOT stress → stress is unobserved (excluded from both groups).
        let unobserved = Fixtures.snapshot(offset: 0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)], stress: nil)
        #expect(Factor.state(of: .stressHigh, on: unobserved) == .unobserved)

        // stress == 7 → high-stress PRESENT (>= threshold).
        let present = Fixtures.snapshot(offset: -1, stress: 7)
        #expect(Factor.state(of: .stressHigh, on: present) == .present)

        // stress == 6 → ABSENT (below threshold, but observed).
        let absent = Fixtures.snapshot(offset: -2, stress: 6)
        #expect(Factor.state(of: .stressHigh, on: absent) == .absent)
    }

    @Test func sleepAndMoodBinarization_threeState() async throws {
        let poorSleep = Fixtures.snapshot(offset: 0, sleepQuality: .poor)
        #expect(Factor.state(of: .sleepPoor, on: poorSleep) == .present)
        let goodSleep = Fixtures.snapshot(offset: -1, sleepQuality: .good)
        #expect(Factor.state(of: .sleepPoor, on: goodSleep) == .absent)
        let noSleep = Fixtures.snapshot(offset: -2, symptoms: [SymptomPoint(type: .fatigue, severity: 4)])
        #expect(Factor.state(of: .sleepPoor, on: noSleep) == .unobserved)

        let lowMood = Fixtures.snapshot(offset: -3, mood: 2)
        #expect(Factor.state(of: .moodLow, on: lowMood) == .present)
        let okMood = Fixtures.snapshot(offset: -4, mood: 3)
        #expect(Factor.state(of: .moodLow, on: okMood) == .absent)
        let noMood = Fixtures.snapshot(offset: -5, stress: 4)
        #expect(Factor.state(of: .moodLow, on: noMood) == .unobserved)
    }

    @Test func foodBinarization_emptyOnLoggedDayIsAbsent_trulyEmptyIsUnobserved() async throws {
        // dairy present.
        let dairy = Fixtures.snapshot(offset: 0, foodTags: [.dairy])
        #expect(Factor.state(of: .food(.dairy), on: dairy) == .present)
        // a day with SOME log (a symptom) but no dairy → absent (A7), not unobserved.
        let loggedNoDairy = Fixtures.snapshot(offset: -1, symptoms: [SymptomPoint(type: .pelvicPain, severity: 4)])
        #expect(Factor.state(of: .food(.dairy), on: loggedNoDairy) == .absent)
        // a truly empty day → unobserved.
        let empty = Fixtures.snapshot(offset: -2)
        #expect(Factor.state(of: .food(.dairy), on: empty) == .unobserved)
    }

    // MARK: Task 3 — strong association is SURFACED (effect + confidence + non-causal copy).

    @Test func strongAssociation_isSurfaced() async throws {
        let insights = correlations(Fixtures.strongAssociationFixture())
        let dairyPain = insights.filter { $0.signalKey == "correlation.food.dairy.symptom.pelvicPain" }
        #expect(dairyPain.count == 1, "Exactly one dairy↔pelvic-pain correlation should surface.")
        let insight = try #require(dairyPain.first)
        #expect(insight.kind == .correlation)
        #expect(insight.window == .allTime)
        #expect(insight.confidence >= .medium, "A large effect over n=40 should land at .medium or .high.")
        #expect(insight.sampleSize >= 20, "sampleSize = pairedDays should be ≥ 20.")
        #expect(insight.points.isEmpty)
        // Non-causal copy enforcement: no forbidden causal term in headline or detail.
        #expect(ForbiddenTerms.violation(in: insight.headline) == nil, "Headline must be non-causal: \(insight.headline)")
        #expect(ForbiddenTerms.violation(in: insight.detail) == nil, "Detail must be non-causal: \(insight.detail)")
        // No emitted insight is below the floor (the engine only emits confidence >= .low).
        for i in insights { #expect(i.confidence >= .low) }
    }

    // MARK: Task 3 — under-sampled (n=9) is WITHHELD (engine surfaces zero).

    @Test func underSampled_isWithheld() async throws {
        let insights = correlations(Fixtures.underSampledAssociationFixture())
        #expect(insights.isEmpty, "An n=9 association is below the sample floor → ZERO correlations.")
    }

    // MARK: Task 3 — spurious (near-zero δ) is WITHHELD.

    @Test func spurious_isWithheld() async throws {
        let insights = correlations(Fixtures.spuriousAssociationFixture())
        #expect(insights.isEmpty, "An independent (spurious) pair has near-zero effect → ZERO correlations.")
    }

    // MARK: Task 3 — the multiple-comparison flood surfaces NOTHING (the load-bearing test, BH q=0.10).

    @Test func multipleComparisonFlood_surfacesNothing() async throws {
        let insights = correlations(Fixtures.multipleComparisonFloodFixture())
        #expect(insights.isEmpty, "K≈100+ pure-noise pairs must all be crushed by BH-FDR → ZERO correlations.")
    }

    // MARK: Task 3 — a suppressed user has NO cycle-phase correlation (D-04 by construction).

    @Test func suppressedUser_noCyclePhaseCorrelation() async throws {
        let insights = correlations(Fixtures.suppressedUserCorrelationFixture())
        for i in insights {
            #expect(!i.signalKey.contains("cyclePhase"),
                    "A suppressed user must yield NO cycle-phase correlation (D-04): \(i.signalKey)")
        }
    }

    // MARK: Task 3 — determinism: same fixture + same injected today → exactly equal [Insight].

    @Test func output_isDeterministic() async throws {
        let a = correlations(Fixtures.strongAssociationFixture())
        let b = correlations(Fixtures.strongAssociationFixture())
        #expect(a == b)
        // Also stable for the flood (the empty case).
        #expect(correlations(Fixtures.multipleComparisonFloodFixture())
                == correlations(Fixtures.multipleComparisonFloodFixture()))
    }
}
