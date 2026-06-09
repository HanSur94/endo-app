// Requirement: DATA-01/02/03 — SwiftData container behind the account-aware SyncMode flag (D-10, D-21, D-03).
//
// One enum (`SyncMode`) IS the D-10 feature flag that decides whether the container is local-only
// (Simulator-friendly, no signing, no account) or private-CloudKit synced. The flag lives in
// code/`UserDefaults`, NEVER in the synced store (D-15 / threat T-04-04).
//
// PHASE 4 POSTURE (D-03): `SyncMode` is now ACCOUNT-AWARE. `SyncMode.resolve(_:)` consults the
// injectable `AccountAvailability` seam (NOT Foundation directly) and returns `.cloudKit` only when an
// iCloud account is signed in — otherwise `.localOnly`. The CALLER (composition root, AppDependencies)
// resolves the mode and passes it into `makeModelContainer(syncMode:)` BEFORE construction
// (RESEARCH Pattern 2 / Pitfall P-2). The default `.localOnly` keeps every existing test caller
// compiling unchanged and the local path builds a genuinely `.none` store even with the entitlement
// shipping in the target (P-2 guard, asserted by SyncModeTests).
import Foundation
import SwiftData

/// The local-vs-sync feature flag (D-10). `.localOnly` is the safe default (no entitlement needed).
enum SyncMode {
    case localOnly
    case cloudKit

    /// Account-aware resolution (D-03): `.cloudKit` iff the injected seam reports an iCloud account is
    /// available, else `.localOnly`. The seam (`AccountAvailability`) is mockable so resolution is
    /// unit-tested and CI/UI tests pin `.localOnly` deterministically.
    static func resolve(_ availability: AccountAvailability) -> SyncMode {
        availability.isICloudAvailable ? .cloudKit : .localOnly
    }
}

/// The CloudKit container identifier for the private database (D-02/D-21). MUST match the
/// `com.apple.developer.icloud-container-identifiers` value in EndoApp.entitlements. Used only when
/// the caller resolved `syncMode == .cloudKit`.
private let cloudKitContainerIdentifier = "iCloud.com.endoapp.ios"

/// Builds the app's `ModelContainer` for the CURRENT schema (`SchemaV6`) behind the `SyncMode` flag,
/// wiring the additive `EndoMigrationPlan` (V1→V2→V3→V4→V5→V6, D-11/D-10/D-14/D-01) so an existing
/// V1/V2/V3/V4/V5 store migrates forward. SchemaV6 adds the CloudKit-safe `ImportedSample` model
/// (DATA-05/D-01) for imported Apple Health samples.
///
/// - Parameters:
///   - inMemory: when `true`, the store is in-memory only (the test seam used by the round-trip
///     tests — fast, deterministic, no Simulator/iCloud needed). Note: in-memory does NOT exercise
///     migration across a reopen; the migration test uses the `url:` overload below.
///   - url: an explicit on-disk store URL (used by the V1→V2 migration test to reopen the SAME
///     file at a newer schema). When `nil`, SwiftData uses its default store location.
func makeModelContainer(
    syncMode: SyncMode = .localOnly,   // resolved by the caller (composition root) BEFORE construction (P-2)
    inMemory: Bool = false,
    url: URL? = nil
) throws -> ModelContainer {
    // Bump to SchemaV6 (additive). The migration plan upgrades any existing V1/V2/V3/V4/V5 store.
    let schema = Schema(versionedSchema: SchemaV6.self)

    // The D-10/D-21 switch: `.private(_:)` ONLY when the CALLER resolved `.cloudKit` (an iCloud account
    // is signed in AND a Team ID is set); `.none` (local-first, no entitlement needed) otherwise. The
    // decision is supplied as a parameter — NOT read from a global — so the local path is genuinely
    // `.none` even with the entitlement shipping in the target (P-2 guard).
    let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
        (syncMode == .cloudKit) ? .private(cloudKitContainerIdentifier)
                                : .none

    let configuration: ModelConfiguration
    if let url {
        // On-disk at an explicit URL (migration test seam). MUST stay genuinely local: pass
        // `cloudKitDatabase: .none` EXPLICITLY (P-2 guard). Since Phase 4 ships the CloudKit
        // entitlement in the target, the `ModelConfiguration(schema:url:)` initializer would
        // otherwise INFER `.automatic` CloudKit mirroring from the entitlement and silently activate
        // sync on this local-only migration path — the exact P-2 mis-activation. The url overload is
        // local-only by contract, so force `.none` regardless of `syncMode`.
        configuration = ModelConfiguration(schema: schema, url: url, cloudKitDatabase: .none)
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
