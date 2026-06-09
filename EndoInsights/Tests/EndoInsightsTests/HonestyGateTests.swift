// EndoInsights — the HonestyGate acceptance spec (Phase 6, INST-04 / D-05/D-07/D-08/D-11 / SC1).
//
// TDD: these assert the single choke-point's HONESTY behavior — the ethics ARE the deliverable. The
// gate WITHHOLDS below-floor insights ENTIRELY (never caveats-and-shows), KEEPS the carved-out "not
// enough data yet" signal, EXCLUDES dismissed signalKeys, and — the NON-NEGOTIABLE acceptance — emits
// ZERO forbidden (causal) terms across EVERY fixture's insights.
//
// DURABILITY NOTE: the gate's release-runtime forbidden-term enforcement is a DEBUG assert (a forbidden
// term is a programmer/copy bug, not user data). The TEST below — not the runtime — is the durable
// guarantee. EVERY future insight kind (Phase 7/11/12) MUST add its emitted copy to the fixtures scanned
// by `allEmittedInsights_haveZeroForbiddenTerms`.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-04 HonestyGate — the single insight choke-point")
struct HonestyGateTests {

    // A descriptive, non-causal headline/detail used in synthetic gate-only candidates. `sampleSize`
    // defaults to 5 (>= minSample(.trend)/.cyclePhase) so the candidate clears the min-sample backstop
    // unless a test deliberately under-samples it.
    private func candidate(
        kind: InsightKind,
        signalKey: String,
        confidence: InsightConfidence,
        sampleSize: Int = 5,
        headline: String = "Pelvic pain logged on 3 of 5 days you tracked anything",
        detail: String = "You logged pelvic pain on 3 of the 5 days you tracked anything in the last 7 days."
    ) -> Insight {
        Insight.derived(
            kind: kind, window: .last7, signalKey: signalKey,
            headline: headline, detail: detail, confidence: confidence, points: [], sampleSize: sampleSize
        )
    }

    // MARK: (a) Below-floor candidate WITHHELD entirely (never caveated-and-shown).

    @Test func belowFloorCandidate_isWithheldEntirely() async throws {
        let belowFloor = candidate(kind: .trend, signalKey: "symptom.pelvicPain.frequency", confidence: .none)
        let gated = HonestyGate.apply([belowFloor], dismissed: [])
        #expect(gated.isEmpty, "A below-floor (.none) insight must be WITHHELD entirely (D-07).")
    }

    @Test func atOrAboveFloorCandidate_isKept() async throws {
        let atFloor = candidate(kind: .trend, signalKey: "symptom.pelvicPain.frequency", confidence: .low)
        let gated = HonestyGate.apply([atFloor], dismissed: [])
        #expect(gated.count == 1, "An at-floor (.low) insight must survive the confidence floor.")
    }

    // MARK: (b) The typed `isInsufficiencyNotice` "not enough data yet" carve-out is KEPT below the floor.

    @Test func insufficientCarveOut_isKept_despiteNoneConfidence() async throws {
        // Mirrors TrendsEngine.notEnoughData: typed `isInsufficiencyNotice == true`, confidence .none,
        // sampleSize below every floor — yet the typed carve-out keeps it (WR-01).
        let carveOut = Insight.derived(
            kind: .trend, window: .last7, signalKey: "symptom.fatigue.frequency.insufficient",
            headline: "Fatigue: not enough data yet",
            detail: "Not enough data yet to describe fatigue in the last 7 days — keep logging.",
            confidence: .none, points: [], sampleSize: 1, isInsufficiencyNotice: true
        )
        let gated = HonestyGate.apply([carveOut], dismissed: [])
        #expect(gated.count == 1, "The typed insufficiency carve-out must be KEPT despite .none confidence and sampleSize 1.")
        #expect(gated.first?.isInsufficiencyNotice == true)
    }

    // WR-01: the carve-out is keyed on the TYPED marker, NOT the signalKey suffix. A forged signalKey
    // ending `.insufficient` WITHOUT the marker does NOT bypass the confidence floor.
    @Test func forgedInsufficientSuffix_withoutTypedMarker_doesNotBypassFloor() async throws {
        let forged = Insight.derived(
            kind: .trend, window: .last7, signalKey: "factor.bogus.frequency.insufficient",
            headline: "Bogus signal", detail: "A low-confidence signal whose key merely ends insufficient.",
            confidence: .none, points: [], sampleSize: 5, isInsufficiencyNotice: false
        )
        let gated = HonestyGate.apply([forged], dismissed: [])
        #expect(gated.isEmpty, "A signalKey ending '.insufficient' must NOT bypass the floor without the typed marker (WR-01).")
    }

    // MARK: (c) Dismissal-exclusion by signalKey (D-11).

    @Test func dismissedSignalKey_isExcluded() async throws {
        let a = candidate(kind: .trend, signalKey: "symptom.pelvicPain.frequency", confidence: .low)
        let b = candidate(kind: .trend, signalKey: "symptom.fatigue.frequency", confidence: .low)
        let gated = HonestyGate.apply([a, b], dismissed: ["symptom.pelvicPain.frequency"])
        #expect(gated.count == 1, "A dismissed signalKey must be excluded at the choke-point (D-11).")
        #expect(gated.first?.signalKey == "symptom.fatigue.frequency")
        #expect(!gated.contains { $0.signalKey == "symptom.pelvicPain.frequency" })
    }

    // MARK: Order-preserving for survivors.

    @Test func gate_isOrderPreserving_forSurvivors() async throws {
        let a = candidate(kind: .trend, signalKey: "symptom.a.frequency", confidence: .low)
        let b = candidate(kind: .trend, signalKey: "symptom.b.frequency", confidence: .low)
        let c = candidate(kind: .trend, signalKey: "symptom.c.frequency", confidence: .low)
        let gated = HonestyGate.apply([a, b, c], dismissed: [])
        #expect(gated.map(\.signalKey) == ["symptom.a.frequency", "symptom.b.frequency", "symptom.c.frequency"])
    }

    // MARK: (b') CR-01/WR-02 — the min-sample backstop is LIVE: under-sampled candidates are WITHHELD.

    @Test func underSampledCyclePhaseAssociation_isWithheldByGate() async throws {
        // A single-day (n=1) cyclePhase co-occurrence at sufficient confidence — sampleSize 1 <
        // minSample(.cyclePhase) == 2. The gate MUST withhold it (the #1 under-sampled bypass, CR-01).
        let n1 = candidate(
            kind: .cyclePhase, signalKey: "cyclePhase.luteal.symptom.pelvicPain",
            confidence: .medium, sampleSize: 1,
            headline: "Pelvic pain tended to co-occur with your luteal phase",
            detail: "Pelvic pain was logged on 1 of 1 of your estimated luteal-phase days — a descriptive co-occurrence only."
        )
        let gated = HonestyGate.apply([n1], dismissed: [])
        #expect(gated.isEmpty, "An n=1 cyclePhase association (sampleSize < minSample(.cyclePhase)) must be WITHHELD (CR-01).")
    }

    @Test func wellSampledCyclePhaseAssociation_survivesGate() async throws {
        // The same association logged on >= 2 in-phase days at sufficient confidence survives.
        let n2 = candidate(
            kind: .cyclePhase, signalKey: "cyclePhase.luteal.symptom.pelvicPain",
            confidence: .medium, sampleSize: 2,
            headline: "Pelvic pain tended to co-occur with your luteal phase",
            detail: "Pelvic pain was logged on 2 of 4 of your estimated luteal-phase days — a descriptive co-occurrence only."
        )
        let gated = HonestyGate.apply([n2], dismissed: [])
        #expect(gated.count == 1, "A cyclePhase association at sampleSize >= minSample(.cyclePhase) must survive.")
    }

    @Test func validTrend_atMinSample_isNotDroppedByGate() async throws {
        // No-regression: a trend at sampleSize == minSample(.trend) == 3 must NOT be dropped.
        let trend = candidate(kind: .trend, signalKey: "symptom.pelvicPain.frequency", confidence: .low, sampleSize: 3)
        let gated = HonestyGate.apply([trend], dismissed: [])
        #expect(gated.count == 1, "A trend at sampleSize == minSample(.trend) must survive (no-regression, Pitfall 1).")
    }

    // MARK: (b''') Phase 11 — minSample(.recommendation) inherits the correlation floor (Pitfall 3).

    @Test func minSampleRecommendation_equalsCorrelationFloor() async throws {
        #expect(HonestyGate.minSample(for: .recommendation) == HonestyGate.minSample(for: .correlation),
                "minSample(.recommendation) must equal minSample(.correlation) (D-02 — a recommendation re-frames a gated correlation).")
        #expect(HonestyGate.minSample(for: .recommendation) == 10,
                "minSample(.recommendation) must be 10 — a 14 floor silently drops valid n=10-13 recommendations (Pitfall 3).")
    }

    @Test func recommendationFromN10Correlation_survivesGate() async throws {
        // A recommendation re-framed from an n=10 correlation (sampleSize == minSample(.correlation) == 10),
        // clean non-prescriptive copy, .medium confidence → MUST survive the gate (Pitfall 3 — not dropped).
        let n10 = candidate(
            kind: .recommendation, signalKey: "recommendation.dairy.symptom.pelvicPain",
            confidence: .medium, sampleSize: 10,
            headline: "Your pelvic pain tends to be more intense on days with dairy — something you might explore.",
            detail: "This is a pattern in your own data, not advice — something to discuss with your clinician."
        )
        let gated = HonestyGate.apply([n10], dismissed: [])
        #expect(gated.count == 1,
                "A recommendation at sampleSize == 10 == minSample(.recommendation) must survive the gate (Pitfall 3).")
    }

    // MARK: (d) MANDATORY — ZERO forbidden (causal) terms across EVERY fixture's emitted insights (D-08).

    @Test func allEmittedInsights_haveZeroForbiddenTerms() async throws {
        // Every fixture stream the package exercises. As future kinds (Ph7/11/12) land, ADD their fixtures.
        let streams: [(String, [DaySnapshot])] = [
            ("risingPelvicPain", Fixtures.risingPelvicPainFixture()),
            ("subThreshold", Fixtures.subThresholdFixture()),
            ("loggedZeroStress", Fixtures.loggedZeroStressFixture()),
            ("unloggedStress", Fixtures.unloggedStressFixture()),
            ("bleeding", Fixtures.bleedingFixture()),
            ("suppressedNoBleeding", Fixtures.suppressedNoBleedingFixture()),
            ("regularCycle", Fixtures.regularCycleFixture()),
            ("irregularCycle", Fixtures.irregularCycleFixture()),
            ("underSampledCycle", Fixtures.underSampledCycleFixture()),
            // Phase 7 (.correlation) — the file's own contract mandates new kinds add their fixtures here.
            ("strongAssociation", Fixtures.strongAssociationFixture()),
            ("multipleComparisonFlood", Fixtures.multipleComparisonFloodFixture()),
            ("suppressedUserCorrelation", Fixtures.suppressedUserCorrelationFixture()),
            // Phase 11 (.recommendation) — MANDATORY per the file's contract (Pitfall 4). The
            // recommendation streams are scanned through the public `InsightsEngine` here; the
            // recommendation-specific emitted copy is additionally proven clean by the DIRECT engine scan
            // (`recommendationEngine_emitsZeroForbiddenTerms`) below, independent of the Plan-03 composition.
            ("beneficialAssociation", Fixtures.beneficialAssociationFixture()),
        ]

        for (name, snapshots) in streams {
            let insights = InsightsEngine.insights(
                from: snapshots, asOf: Fixtures.day(0), dismissed: [], calendar: Fixtures.calendar
            )
            for insight in insights {
                #expect(
                    ForbiddenTerms.violation(in: insight.headline) == nil,
                    "Forbidden term in headline (\(name)): \(insight.headline)"
                )
                #expect(
                    ForbiddenTerms.violation(in: insight.detail) == nil,
                    "Forbidden term in detail (\(name)): \(insight.detail)"
                )
            }
        }
    }

    // MARK: (d'') Phase 11 — the `.recommendation` emitted copy is clean DIRECTLY out of the producer.

    /// `RecommendationsEngine` is not composed into the public `InsightsEngine` until Plan 03, so the
    /// stream scan above cannot exercise its emitted copy yet. Prove the package-emitted recommendation
    /// copy contains ZERO forbidden terms (causal AND imperative, D-04) by scanning the engine output
    /// DIRECTLY over the recommendation fixtures — independent of the Plan-03 composition.
    @Test func recommendationEngine_emitsZeroForbiddenTerms() async throws {
        let streams: [(String, [DaySnapshot])] = [
            ("beneficialAssociation", Fixtures.beneficialAssociationFixture()),
            ("strongAssociation", Fixtures.strongAssociationFixture()),          // aggravating
            ("suppressedUserCorrelation", Fixtures.suppressedUserCorrelationFixture()),
            ("multipleComparisonFlood", Fixtures.multipleComparisonFloodFixture()),
        ]
        for (name, snapshots) in streams {
            let recs = RecommendationsEngine.recommendations(
                from: snapshots, asOf: Fixtures.day(0), calendar: Fixtures.calendar
            )
            for rec in recs {
                #expect(
                    ForbiddenTerms.violation(in: rec.headline) == nil,
                    "Forbidden term in recommendation headline (\(name)): \(rec.headline)"
                )
                #expect(
                    ForbiddenTerms.violation(in: rec.detail) == nil,
                    "Forbidden term in recommendation detail (\(name)): \(rec.detail)"
                )
            }
        }
    }

    // MARK: (d') CR-02 — fail-closed: a runtime-interpolated forbidden term is WITHHELD in the gated output.

    @Test func runtimeForbiddenTermInDetail_isWithheld_inGatedOutput() async throws {
        // A producer copy bug a STATIC fixture cannot cover: a forbidden causal term lands in a
        // runtime-interpolated detail. The gate must WITHHOLD it in release (fail-closed), not surface it.
        // (Built via the internal memberwise init to bypass the debug `assertionFailure`, so this test
        // verifies the RELEASE drop behavior, which is the durable guarantee — not the debug assert.)
        let causal = Insight(
            id: DeterministicUUID.make(kind: .cyclePhase, window: .allTime, signalKey: "cyclePhase.luteal.symptom.pelvicPain"),
            kind: .cyclePhase,
            headline: "Pelvic pain tended to co-occur with your luteal phase",
            detail: "Your luteal phase triggers pelvic pain.",   // runtime-interpolated causal claim
            window: .allTime, confidence: .medium, points: [],
            signalKey: "cyclePhase.luteal.symptom.pelvicPain", sampleSize: 5, isInsufficiencyNotice: false
        )
        // Sanity: this copy genuinely violates the policy.
        #expect(ForbiddenTerms.violation(in: causal.detail) != nil)
        let gated = HonestyGate.applyReleaseScan([causal], dismissed: [])
        #expect(gated.isEmpty, "A causal-term insight must be WITHHELD in the gated output, even in release (CR-02).")
    }

    // MARK: ForbiddenTerms unit behavior.

    @Test func forbiddenTerms_flagsCausal_passesDescriptive() async throws {
        #expect(ForbiddenTerms.violation(in: "this may cause pain") != nil)
        #expect(ForbiddenTerms.violation(in: "tended to co-occur with") == nil)
    }

    // WR-03: the expanded list catches causal inflections/connectives it previously false-negated on.
    @Test func forbiddenTerms_catchesExpandedCausalInflections() async throws {
        #expect(ForbiddenTerms.violation(in: "stress worsens your luteal phase") != nil)   // was a false-negative
        #expect(ForbiddenTerms.violation(in: "this caused the flare") != nil)              // "caused" ⊂ "cause"
        #expect(ForbiddenTerms.violation(in: "this is triggering pain") != nil)            // "triggering" ⊂ "trigger"
        #expect(ForbiddenTerms.violation(in: "exercise improves your mood") != nil)
        #expect(ForbiddenTerms.violation(in: "rest reduces pelvic pain") != nil)
        #expect(ForbiddenTerms.violation(in: "poor sleep linked to fatigue") != nil)
        #expect(ForbiddenTerms.violation(in: "leading to more flares") != nil)
        // A descriptive co-occurrence (the producer's actual posture) still passes clean.
        #expect(ForbiddenTerms.violation(in: "Fatigue tended to co-occur with your follicular phase") == nil)
    }
}
