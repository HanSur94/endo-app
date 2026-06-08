// Requirement: SYMP-03 — free-text notes on a day's entry.
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. Turns GREEN in Plan 02-02 Task 1
// (Repository write seam).
//
// Symbol-free RED body (the note write/length-bound seam arrives in Plan 02-02).
import Testing
import Foundation
@testable import EndoApp

@Suite("SYMP-03 day note")
struct DayNoteTests {

    // RED: turns GREEN in Plan 02-02 Task 1 when the day-note write seam is implemented.
    @Test func dayNote_persists_andIsLengthBounded() async throws {
        #expect(Bool(false),
                "RED: day-note write seam not yet implemented — the day note must persist and be length-bounded at the write seam (empty -> nil). Turns GREEN in Plan 02-02 Task 1.")
    }
}
