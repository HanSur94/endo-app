// Requirement: D-22 — composition root wiring the ModelContainer + services into the Environment.
//
// `AppDependencies` is the single place that constructs the SwiftData `ModelContainer` (via
// `makeModelContainer()`, behind the SyncMode flag) and the `ConsentStore`, and exposes them for
// injection into the SwiftUI Environment. Later phases add more services here (sync, insights,
// HealthKit import) without touching the views.
//
// The consent store is exposed through a custom `EnvironmentKey` (`\.consentStore`) so `AppGate`
// can read it; the model container is injected via SwiftUI's built-in `.modelContainer(_:)`.
import SwiftUI
import SwiftData

/// The app's composition root (D-22). Built once at launch by `EndoAppApp`.
@MainActor
final class AppDependencies {
    /// The SwiftData container (local-only `.none` in Phase 1, behind the SyncMode flag).
    let modelContainer: ModelContainer
    /// The consent gate's persistence seam (ONBD-01). Concrete UserDefaults-backed impl.
    let consentStore: UserDefaultsConsentStore
    /// The terminology/identity preference store (ONBD-03, D-07). UserDefaults-backed, outside the
    /// synced store.
    let terminologyStore: TerminologyStore
    /// The local-notification scheduling seam (CHCK-02, D-17). Concrete `UNUserNotificationCenter`
    /// wrapper; the Settings reminders section drives it, and `EndoAppApp` injects it at the App root
    /// (Plan 04) alongside the deep-link delegate.
    let notificationScheduler: any NotificationScheduler
    /// The notification deep-link router (D-16). The `UNUserNotificationCenterDelegate` flips its flag
    /// on a reminder tap; the consented-branch UI presents `DailyCheckInView`. Injected at the App root.
    let checkInRouter: CheckInRouter
    /// The account-availability seam (DATA-02/03, D-03). Resolved once at launch to decide local-vs-cloud
    /// sync BEFORE the container is built (P-2); also injected into the Environment so SettingsView (04-02)
    /// can show the iCloud sync status. `Sendable` (NOT `@MainActor`) — read off-main at build time.
    let accountAvailability: any AccountAvailability

    init() {
        // Test-only, launch-arg-guarded deterministic reset (used by EndoAppUITests). When the
        // `-uitest-reset` argument is present we (a) clear the persisted consent flag so every UI
        // run starts at the consent gate, and (b) build the SwiftData store in-memory so no entries
        // leak between runs. This branch is unreachable in normal app launches, so it cannot change
        // production behavior.
        let uiTestReset = ProcessInfo.processInfo.arguments.contains("-uitest-reset")
        if uiTestReset {
            UserDefaults.standard.removeObject(forKey: "consent.grantedAt")
        }

        // Resolve account availability FIRST, then derive `SyncMode` BEFORE building the container
        // (RESEARCH Pattern 2 / Pitfall P-2 — the local-only path must be a genuine `.none` store).
        // `uiTestReset` forces deterministic local-only so UI tests never touch a real iCloud account.
        // The resolved `SyncMode`/account state lives ONLY here in code, NEVER in the synced store
        // (D-15 / threat T-04-04).
        let availability: any AccountAvailability =
            uiTestReset ? StubAccountAvailability(isICloudAvailable: false) : SystemAccountAvailability()
        self.accountAvailability = availability
        let syncMode = SyncMode.resolve(availability)

        do {
            self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: uiTestReset)
        } catch {
            // The expected path on a healthy or migratable store is success. A failure here means the
            // on-disk store could not be opened/migrated at all (e.g. a corrupt or pre-versioning
            // store left by an interrupted run, or an irrecoverable migration error — SwiftData
            // error 134504 "unknown model version"). We must NEVER hard-crash here: an
            // `assertionFailure` traps in DEBUG builds and would brick the app for that user — the
            // very crash this fallback is meant to prevent. Instead, recover: first discard the
            // unreadable on-disk store and recreate a FRESH on-disk store (so logging still
            // persists), and only as a last resort fall back to in-memory.
            //
            // NOTE: only the error TYPE is logged (never `error` verbatim) — SwiftDataError describes
            // the store/migration, not record contents, so no health values are surfaced.
            print("⚠️ Endo-App: local ModelContainer failed to open (\(type(of: error))); recovering.")
            Self.discardUnreadableDefaultStore()
            do {
                // Retry on-disk against a clean store so future data still persists across launches.
                self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: uiTestReset)
            } catch {
                print("⚠️ Endo-App: on-disk recovery failed (\(type(of: error))); using in-memory store.")
                do {
                    self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: true)
                } catch {
                    // An in-memory container should never fail; if it does the schema itself is invalid.
                    fatalError("Failed to build even an in-memory ModelContainer: \(type(of: error))")
                }
            }
        }
        self.consentStore = UserDefaultsConsentStore()
        self.terminologyStore = TerminologyStore()
        self.notificationScheduler = SystemNotificationScheduler()
        self.checkInRouter = CheckInRouter()
    }

    /// Recovery helper (D-22 resilience): delete an unreadable default SwiftData store (and its
    /// WAL/SHM sidecars) so a fresh on-disk store can be created after an unrecoverable open/migration
    /// failure. Local-only this phase, so no synced data is at risk and the file being removed is
    /// already unopenable. Best-effort — failures are ignored.
    private static func discardUnreadableDefaultStore() {
        let fm = FileManager.default
        guard let appSupport = try? fm.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                           appropriateFor: nil, create: false) else { return }
        for name in ["default.store", "default.store-shm", "default.store-wal"] {
            try? fm.removeItem(at: appSupport.appending(path: name))
        }
    }
}

// MARK: - Environment injection

/// Environment key carrying the app's `ConsentStore` (D-22 injection seam).
private struct ConsentStoreKey: EnvironmentKey {
    // Default is a fresh UserDefaults-backed store so previews/tests have a sane value. The store is
    // `@MainActor`-isolated; SwiftUI only ever reads environment values on the main actor, so
    // constructing the default under `assumeIsolated` is safe and satisfies the nonisolated
    // `EnvironmentKey.defaultValue` requirement under Swift 6.
    static var defaultValue: any ConsentStore {
        MainActor.assumeIsolated { UserDefaultsConsentStore() }
    }
}

extension EnvironmentValues {
    /// The injected consent store the gate routes on (ONBD-01).
    var consentStore: any ConsentStore {
        get { self[ConsentStoreKey.self] }
        set { self[ConsentStoreKey.self] = newValue }
    }
}

/// Environment key carrying the app's `TerminologyStore` (ONBD-03 / D-07 injection seam).
private struct TerminologyStoreKey: EnvironmentKey {
    // Same main-actor-safe default pattern as the consent store.
    static var defaultValue: TerminologyStore {
        MainActor.assumeIsolated { TerminologyStore() }
    }
}

extension EnvironmentValues {
    /// The injected terminology store Settings edits and copy reads through (ONBD-03, D-07).
    var terminologyStore: TerminologyStore {
        get { self[TerminologyStoreKey.self] }
        set { self[TerminologyStoreKey.self] = newValue }
    }
}

/// Environment key carrying the app's `NotificationScheduler` (CHCK-02 / D-17 injection seam).
private struct NotificationSchedulerKey: EnvironmentKey {
    // Same main-actor-safe default pattern as the consent store: the scheduler is `@MainActor`-isolated
    // and SwiftUI reads environment values on the main actor, so constructing the default under
    // `assumeIsolated` is safe and satisfies the nonisolated `EnvironmentKey.defaultValue` requirement
    // under Swift 6.
    static var defaultValue: any NotificationScheduler {
        MainActor.assumeIsolated { SystemNotificationScheduler() }
    }
}

extension EnvironmentValues {
    /// The injected notification scheduler the reminders settings section drives (CHCK-02, D-17).
    var notificationScheduler: any NotificationScheduler {
        get { self[NotificationSchedulerKey.self] }
        set { self[NotificationSchedulerKey.self] = newValue }
    }
}

/// Environment key carrying the app's `AccountAvailability` seam (DATA-02/03, D-03 injection seam).
private struct AccountAvailabilityKey: EnvironmentKey {
    // Unlike the consent/terminology/scheduler keys, `AccountAvailability` is `Sendable` (NOT
    // `@MainActor`), so the default does NOT need `MainActor.assumeIsolated` — it constructs directly.
    static let defaultValue: any AccountAvailability = SystemAccountAvailability()
}

extension EnvironmentValues {
    /// The injected account-availability seam SettingsView reads for the iCloud sync status (04-02).
    var accountAvailability: any AccountAvailability {
        get { self[AccountAvailabilityKey.self] }
        set { self[AccountAvailabilityKey.self] = newValue }
    }
}
