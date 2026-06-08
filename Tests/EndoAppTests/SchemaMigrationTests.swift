// Requirement: A1 schema-migration guard — a V1 store survives the lightweight V1→V2 migration
// with no data loss (closes the A1 nesting-identity risk in RESEARCH §Assumptions Log).
//
// Nyquist Wave 0 (Plan 02-01 Task 1): RED placeholder. Turns GREEN in Plan 02-01 Task 3, where the
// on-disk V1→V2 round-trip migration test is implemented.
//
// Symbol-free RED body (must not reference SchemaV2 / EndoMigrationPlan, which arrive in Task 3).
import Testing
import Foundation
@testable import EndoApp

@Suite("A1 V1→V2 migration data-survival")
struct SchemaMigrationTests {

    // RED: turns GREEN in Plan 02-01 Task 3 when the on-disk V1→V2 lightweight migration is wired.
    @Test func v1Store_survivesLightweightMigrationToV2_withNoDataLoss() async throws {
        #expect(Bool(false),
                "RED: EndoMigrationPlan (V1->V2 lightweight) not yet implemented — a V1 store must survive the lightweight V1->V2 migration with no data loss (on-disk reopen). Turns GREEN in Plan 02-01 Task 3.")
    }
}
