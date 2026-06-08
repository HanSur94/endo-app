// Requirement: cross-cutting claims/language audit (Pitfall 5, D-14) — new Phase-2 copy must pass
// ClaimsPolicy.forbiddenVerbs + forbiddenGenderedTokens and use no hard single date.
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. The symptom/region-label half turns GREEN in
// Plan 02-01 Task 2 (Domain enum plainLabels); the cycle-copy half turns GREEN in Plan 02-04 Task 2.
//
// Symbol-free RED body (must not reference SymptomType/BodyRegion/FlowIntensity, which arrive in T2).
import Testing
import Foundation
@testable import EndoApp

@Suite("Phase-2 copy audit")
struct CopyAuditTests {

    // RED: turns GREEN in Plan 02-01 Task 2 (labels) + Plan 02-04 Task 2 (cycle copy).
    @Test func phase2Copy_passesClaimsAndTerminologyAudit() async throws {
        #expect(Bool(false),
                "RED: Phase-2 copy audit not yet implemented — new Domain plainLabels and cycle-summary strings must contain no forbidden verb/gendered token and no hard date. Turns GREEN in Plan 02-01 Task 2 + Plan 02-04 Task 2.")
    }
}
