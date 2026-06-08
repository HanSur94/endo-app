// Requirement: DATA-02/03 — injectable account-availability seam gating local-vs-cloud sync (D-03).
//
// The single Bool query that decides `SyncMode` (D-03 runtime detection). Mirrors the established
// seam triad (`NotificationScheduler`/`ConsentStore`): one protocol, one `System…` impl reading the
// real API, one `Stub…` test double — plus an `\.accountAvailability` EnvironmentKey in
// AppDependencies.swift so SettingsView (04-02) can read it.
//
// ACTOR NOTE: this seam is `Sendable`, NOT `@MainActor`. It is read at container-build time on
// whatever actor the composition root runs (off-main), unlike the `@MainActor` ConsentStore /
// NotificationScheduler. The query is synchronous and launch-safe.
//
// SECURITY (V12 / threat T-04-03): the gate uses `FileManager.default.ubiquityIdentityToken` only as
// a presence check (token != nil). NEVER log the token value or any record contents — if error
// handling is ever added here, log only `type(of: error)` (mirror AppDependencies.swift line 56).
//
// ANTI-PATTERN GUARD (RESEARCH P-5): use the SYNCHRONOUS `ubiquityIdentityToken` for the launch-time
// gate, NOT the async `CKContainer.accountStatus` — the container must be built without awaiting.
import Foundation

/// The account-availability seam (D-03). `true` when an iCloud account is signed in.
protocol AccountAvailability: Sendable {
    /// Synchronous, launch-safe. `true` when an iCloud account is signed in on this device.
    var isICloudAvailable: Bool { get }
}

/// The real seam: an iCloud account is present iff `ubiquityIdentityToken` is non-nil (D-03, P-5).
/// On the Simulator / a signed-out device this is `nil`, so CI deterministically resolves `.localOnly`.
struct SystemAccountAvailability: AccountAvailability {
    var isICloudAvailable: Bool { FileManager.default.ubiquityIdentityToken != nil }
}

/// Test seam: forces a deterministic answer so SyncMode resolution is unit-testable and UI tests can
/// pin `.localOnly` (no iCloud account is touched in CI/XCUITest).
struct StubAccountAvailability: AccountAvailability {
    let isICloudAvailable: Bool
}
