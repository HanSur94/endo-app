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

/// Builds the app's `ModelContainer` for `SchemaV1` behind the `SyncMode` flag.
///
/// - Parameter inMemory: when `true`, the store is in-memory only (the test seam used by the
///   DATA-01 round-trip tests — fast, deterministic, no Simulator/iCloud needed).
func makeModelContainer(inMemory: Bool = false) throws -> ModelContainer {
    let schema = Schema(versionedSchema: SchemaV1.self)

    // The D-10/D-21 switch: `.private(_:)` ONLY when sync is flipped on (Phase 4); `.none`
    // (local-first, no entitlement) is the Phase 1 default.
    let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
        (SyncMode.current == .cloudKit) ? .private(cloudKitContainerIdentifier)
                                        : .none

    let configuration = ModelConfiguration(
        schema: schema,
        isStoredInMemoryOnly: inMemory,
        cloudKitDatabase: cloudKitDatabase
    )

    return try ModelContainer(for: schema, configurations: configuration)
}
