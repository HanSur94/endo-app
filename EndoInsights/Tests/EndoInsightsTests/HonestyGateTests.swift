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

    // A descriptive, non-causal headline/detail used in synthetic gate-only candidates.
    private func candidate(
        kind: InsightKind,
        signalKey: String,
        confidence: InsightConfidence,
        headline: String = "Pelvic pain logged on 3 of 5 days you tracked anything",
        detail: String = "You logged pelvic pain on 3 of the 5 days you tracked anything in the last 7 days."
    ) -> Insight {
        Insight.derived(
            kind: kind, window: .last7, signalKey: signalKey,
            headline: headline, detail: detail, confidence: confidence, points: []
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

    // MARK: (b) The `.insufficient` "not enough data yet" carve-out is KEPT below the floor.

    @Test func insufficientCarveOut_isKept_despiteNoneConfidence() async throws {
        // Mirrors TrendsEngine.notEnoughData: signalKey ends `.insufficient`, confidence .none.
        let carveOut = Insight.derived(
            kind: .trend, window: .last7, signalKey: "symptom.fatigue.frequency.insufficient",
            headline: "Fatigue: not enough data yet",
            detail: "Not enough data yet to describe fatigue in the last 7 days — keep logging.",
            confidence: .none, points: []
        )
        let gated = HonestyGate.apply([carveOut], dismissed: [])
        #expect(gated.count == 1, "The '.insufficient' honest sub-threshold signal must be KEPT (carve-out).")
        #expect(gated.first?.signalKey.hasSuffix(".insufficient") == true)
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

    // MARK: ForbiddenTerms unit behavior.

    @Test func forbiddenTerms_flagsCausal_passesDescriptive() async throws {
        #expect(ForbiddenTerms.violation(in: "this may cause pain") != nil)
        #expect(ForbiddenTerms.violation(in: "tended to co-occur with") == nil)
    }
}
