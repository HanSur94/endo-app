// Requirement: SYMP-04 — edit or delete any past entry.
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. Turns GREEN in Plan 02-04 Task 1.
//
// Symbol-free RED body (the edit/delete + cascade seam arrives in Plan 02-04).
import Testing
import Foundation
@testable import EndoApp

@Suite("SYMP-04 edit / delete")
struct EditDeleteTests {

    // RED: turns GREEN in Plan 02-04 Task 1 when edit/delete + child cascade is implemented.
    @Test func edit_updates_delete_removes_withChildCascade() async throws {
        #expect(Bool(false),
                "RED: edit/delete seam not yet implemented — editing updates a day's records and deleting removes the day + cascades children. Turns GREEN in Plan 02-04 Task 1.")
    }
}
