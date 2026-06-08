// EndoInsights — the CyclePhaseMapper honesty spec (Phase 6, INST-02 / D-01/D-02/D-03 / SC1).
//
// TDD: these assert the estimated-phase mapper's HONESTY GATES — the ethics are the deliverable. A
// regular cycle yields estimated `.cyclePhase` insights at confidence ≤ `.medium` (NEVER `.high`); a
// suppressed, an irregular (spread > 9), and an under-sampled (< 2 cycles) stream each yield ZERO
// `.cyclePhase` insights (no phantom phase, the #1 endo pitfall). Output is deterministic/Equatable.
// `today` is injected (no `Date.now`); the fixed-UTC `Fixtures.calendar` keeps day-bucketing stable.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-02 CyclePhaseMapper — estimated phases with honesty gates")
struct CyclePhaseMapperTests {

    private func phases(_ snapshots: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
        CyclePhaseMapper.insights(from: snapshots, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
    }

    // MARK: Regular cycle → estimated phases at confidence ≤ .medium (never .high).

    @Test func regularCycle_emitsCyclePhaseInsights_neverHighConfidence() async throws {
        let insights = phases(Fixtures.regularCycleFixture())
        #expect(!insights.isEmpty, "A regular, tight cycle must yield estimated cycle-phase insights.")
        for i in insights {
            #expect(i.kind == .cyclePhase, "Every emitted insight is a .cyclePhase insight.")
            #expect(i.confidence <= .medium, "Phase confidence must NEVER exceed .medium (D-02).")
            #expect(i.window == .allTime)
            // The signalKey is the value-independent dismissal key (D-09).
            #expect(i.signalKey.hasPrefix("cyclePhase."))
        }
    }

    @Test func regularCycle_copyIsDescriptive_neverCausal() async throws {
        let insights = phases(Fixtures.regularCycleFixture())
        #expect(!insights.isEmpty)
        let banned = ["trigger", "causes", " cause", "will ", "because", "due to", "leads to"]
        for i in insights {
            let text = (i.headline + " " + i.detail).lowercased()
            for word in banned {
                #expect(!text.contains(word), "Causal word '\(word)' in: \(i.headline) / \(i.detail)")
            }
        }
    }

    // MARK: SC1 — suppressed stream → ZERO .cyclePhase insights (no phantom phase).

    @Test func suppressedStream_yieldsNoPhases() async throws {
        let insights = phases(Fixtures.suppressedNoBleedingFixture())
        #expect(insights.isEmpty, "A suppressed stream must yield NO cycle-phase insights (SC1).")
    }

    // MARK: D-03 — irregular stream (spread > 9) → ZERO .cyclePhase insights.

    @Test func irregularStream_yieldsNoPhases() async throws {
        let insights = phases(Fixtures.irregularCycleFixture())
        #expect(insights.isEmpty, "An irregular cycle (spread > 9) must yield NO cycle-phase insights (D-03).")
    }

    // MARK: < 2 cycles → ZERO .cyclePhase insights (no phantom phase from one bleed).

    @Test func underSampledStream_yieldsNoPhases() async throws {
        let insights = phases(Fixtures.underSampledCycleFixture())
        #expect(insights.isEmpty, "Fewer than 2 cycles must yield NO cycle-phase insights.")
    }

    // MARK: Determinism — same fixture + same injected today → exactly equal [Insight].

    @Test func determinism_sameInputs_equalInsights() async throws {
        let a = phases(Fixtures.regularCycleFixture())
        let b = phases(Fixtures.regularCycleFixture())
        #expect(a == b)
    }
}
