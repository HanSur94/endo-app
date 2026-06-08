// Requirement: D-11 — a SwiftData SchemaMigrationPlan with a single LIGHTWEIGHT V1→V2 stage.
//
// The Phase-2 schema delta is purely additive (BleedingEvent gains an optional `clots`; two new
// models are introduced), so `MigrationStage.lightweight` is correct AND sufficient.
//
// Pitfall 3 / T-02-02 — why NOT `.custom`: a custom stage's `willMigrate`/`didMigrate` closures do
// NOT fire once CloudKit is enabled (Phase 4). Any data-transform logic placed there would silently
// never run on synced users. For a CloudKit-bound store the forever-rule is: additive + lightweight,
// never custom. (Apple Developer Forums 742899 / 744491 / 748049.)
import Foundation
import SwiftData

/// The app's schema migration plan: V1 → V2, lightweight (additive only). No custom hooks.
enum EndoMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] {
        [SchemaV1.self, SchemaV2.self]
    }

    static var stages: [MigrationStage] {
        [v1toV2]
    }

    /// Purely additive → lightweight. NEVER `.custom` (its hooks don't fire under CloudKit, Phase 4).
    static let v1toV2 = MigrationStage.lightweight(
        fromVersion: SchemaV1.self,
        toVersion: SchemaV2.self
    )
}
