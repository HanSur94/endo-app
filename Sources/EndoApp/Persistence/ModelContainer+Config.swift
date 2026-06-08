// Requirement: DATA-01 — local-only SwiftData container behind the SyncMode flag (D-10, D-21).
//
// One enum (`SyncMode`) IS the D-10 feature flag that decides whether the container is local-only
// (default, Simulator-friendly, no entitlement, no Apple Developer account) or private-CloudKit
// synced (Phase 4 only). The flag itself lives in code/`UserDefaults`, never in the synced store.
//
// IMPORTANT (Phase 1 posture): SyncMode.current is `.localOnly`, so the container ships with
// `cloudKitDatabase: .none`. Do NOT add the iCloud/CloudKit or Background-Modes capability in
// Phase 1 — `.none` needs none of it. The `.private(_:)` branch (D-21, private database only) is
// reserved for Phase 4 and is OFF now.
import Foundation
import SwiftData

/// The local-vs-sync feature flag (D-10). `.localOnly` is the Phase 1 default.
enum SyncMode {
    case localOnly
    case cloudKit

    /// Phase 1 default: local-only. Flip to `.cloudKit` only in Phase 4 (requires entitlements).
    static var current: SyncMode { .localOnly }
}

/// The CloudKit container identifier reserved for the private database (D-21). Used only when
/// `SyncMode.current == .cloudKit` — inert in Phase 1.
private let cloudKitContainerIdentifier = "iCloud.com.endoapp.ios"

/// Builds the app's `ModelContainer` for the CURRENT schema (`SchemaV2`) behind the `SyncMode` flag,
/// wiring the additive V1→V2 `EndoMigrationPlan` (D-11) so an existing V1 store migrates forward.
///
/// - Parameters:
///   - inMemory: when `true`, the store is in-memory only (the test seam used by the round-trip
///     tests — fast, deterministic, no Simulator/iCloud needed). Note: in-memory does NOT exercise
///     migration across a reopen; the migration test uses the `url:` overload below.
///   - url: an explicit on-disk store URL (used by the V1→V2 migration test to reopen the SAME
///     file at a newer schema). When `nil`, SwiftData uses its default store location.
func makeModelContainer(inMemory: Bool = false, url: URL? = nil) throws -> ModelContainer {
    // Bump to SchemaV2 (additive). The migration plan upgrades any existing V1 store.
    let schema = Schema(versionedSchema: SchemaV2.self)

    // The D-10/D-21 switch: `.private(_:)` ONLY when sync is flipped on (Phase 4); `.none`
    // (local-first, no entitlement) is the Phase 1/2 default. DO NOT activate CloudKit here.
    let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
        (SyncMode.current == .cloudKit) ? .private(cloudKitContainerIdentifier)
                                        : .none

    let configuration: ModelConfiguration
    if let url {
        // On-disk at an explicit URL (migration test seam). `cloudKitDatabase` is irrelevant here
        // because the migration test runs local-only.
        configuration = ModelConfiguration(schema: schema, url: url)
    } else {
        configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: inMemory,
            cloudKitDatabase: cloudKitDatabase
        )
    }

    return try ModelContainer(
        for: schema,
        migrationPlan: EndoMigrationPlan.self,
        configurations: configuration
    )
}
