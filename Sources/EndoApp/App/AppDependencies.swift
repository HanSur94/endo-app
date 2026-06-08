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

        do {
            self.modelContainer = try makeModelContainer(inMemory: uiTestReset)
        } catch {
            // The expected path on a healthy or migratable store is success. A failure here means the
            // on-disk store could not be opened/migrated at all (e.g. a corrupt or pre-versioning
            // store left by an interrupted run, or an irrecoverable migration error — SwiftData
            // error 134504 "unknown model version"). Rather than hard-crash every launch (which would
            // permanently brick the app for that user), fall back to a fresh in-memory store so the
            // app stays usable. Local-only this phase, so no synced data is at stake; the user's
            // local rows are not silently rewritten — the unreadable file is simply not used.
            //
            // NOTE: do NOT log `error` verbatim if it could carry health values; SwiftDataError here
            // describes the store/migration, not record contents, so the type name is safe to surface.
            assertionFailure("Local ModelContainer failed to open (\(type(of: error))); "
                             + "falling back to in-memory. \(error)")
            do {
                self.modelContainer = try makeModelContainer(inMemory: true)
            } catch {
                // An in-memory container should never fail; if it does the schema itself is invalid.
                fatalError("Failed to build even an in-memory ModelContainer: \(error)")
            }
        }
        self.consentStore = UserDefaultsConsentStore()
        self.terminologyStore = TerminologyStore()
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
