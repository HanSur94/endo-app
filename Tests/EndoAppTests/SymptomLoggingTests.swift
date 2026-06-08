// Requirement: SYMP-01 — log endo-specific symptoms with severity.
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. Turns GREEN in Plan 02-01 Task 3
// (persistence: SymptomReading round-trip) and Plan 02-02 Task 2 (UI write).
//
// The body is a symbol-free RED placeholder so the suite COMPILES today (it must not reference
// SymptomReading / SchemaV2, which do not yet exist) while failing by design.
import Testing
import Foundation
@testable import EndoApp

@Suite("SYMP-01 symptom logging")
struct SymptomLoggingTests {

    // RED: turns GREEN in Plan 02-01 Task 3 when SchemaV2.SymptomReading round-trips.
    @Test func symptomReading_roundTrips_typeAndSeverity_absenceIsNotZero() async throws {
        #expect(Bool(false),
                "RED: SchemaV2.SymptomReading not yet implemented — must round-trip type+severity and absence of severity must NOT mean 0/healthy. Turns GREEN in Plan 02-01 Task 3.")
    }
}
