// Requirement: CYCL-02 / CYCL-03 / CYCL-04 — derived cycle math (never an assumed 28-day cycle).
//
// Nyquist Wave 0 (Plan 02-01 Task 1): four RED placeholders, one per required fixture. Turns GREEN
// in Plan 02-03 (pure CycleMath, TDD), surfaced end-to-end in Plan 02-04 Task 2.
//
// Symbol-free RED bodies (must not reference CycleMath / CycleSummary, which arrive in Plan 02-03).
import Testing
import Foundation
@testable import EndoApp

@Suite("CYCL-02/03/04 cycle math")
struct CycleMathTests {

    // CYCL-02 — regular cycler: median length + a sensible RANGE (never a single hard date).
    @Test func regularCycler_medianLength_sensibleRange() async throws {
        #expect(Bool(false),
                "RED: CycleMath.summarize(...) not yet implemented — a regular cycler must yield hasEnoughData, positive cycleDay, a non-empty nextPeriod RANGE, confidence == .high. Turns GREEN in Plan 02-03.")
    }

    // CYCL-02 — irregular cycler: wider range / lower confidence.
    @Test func irregularCycler_widerRange_lowerConfidence() async throws {
        #expect(Bool(false),
                "RED: CycleMath.summarize(...) not yet implemented — an irregular cycler must yield a WIDER nextPeriod range and confidence strictly < .high. Turns GREEN in Plan 02-03.")
    }

    // CYCL-03 — suppressed: emits nothing (the no-phantom-cycle proof).
    @Test func suppressed_emitsNothing() async throws {
        #expect(Bool(false),
                "RED: CycleMath.summarize(...) not yet implemented — with isSuppressed == true the summary must emit NOTHING (cycleDay/phase/nextPeriod nil, confidence .none, hasEnoughData false). Turns GREEN in Plan 02-03.")
    }

    // CYCL-04 — sparse (<2 cycles): not enough data yet.
    @Test func sparse_underTwoCycles_notEnoughData() async throws {
        #expect(Bool(false),
                "RED: CycleMath.summarize(...) not yet implemented — fewer than 2 gaps must yield hasEnoughData == false (not enough data yet), cycleDay/nextPeriod nil, confidence .none. Turns GREEN in Plan 02-03.")
    }
}
