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

    // The `correlations(_:todayOffset:)` engine helper + the per-fixture surfaced/withheld/suppressed/
    // deterministic tests are added in Task 3 (once `CorrelationEngine` exists). Task 1 covers the Factor
    // model: the named threshold constants and the three-state binarization.

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
}
