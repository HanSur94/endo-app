// Requirement: SYMP-02 — record pain location on a body map with severity.
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. Turns GREEN in Plan 02-04 Task 1.
//
// Symbol-free RED body (must not reference PainPoint / BodyRegion bindings that arrive later).
import Testing
import Foundation
@testable import EndoApp

@Suite("SYMP-02 body-map binding")
struct BodyMapBindingTests {

    // RED: turns GREEN in Plan 02-04 Task 1 when the body-map binding is implemented.
    @Test func tappingRegion_producesPainPoint_atLogicLevel_noRender() async throws {
        #expect(Bool(false),
                "RED: body-map binding not yet implemented — tapping a BodyRegion must produce a PainPoint(region, severity) at the logic level. Turns GREEN in Plan 02-04 Task 1.")
    }
}
