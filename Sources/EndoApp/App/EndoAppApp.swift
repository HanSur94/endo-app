import SwiftUI

/// The composition root (D-22) of Endo-App.
///
/// Phase 1 (walking skeleton): this entry point only renders a placeholder root to prove the
/// scaffold is a real iOS `.app` bundle that launches in the Simulator. There is intentionally
/// NO `ModelContainer`, NO consent logic, and NO service wiring yet.
///
/// Plan 02 expands this into the real composition root — building the SwiftData `ModelContainer`
/// (behind the `SyncMode` flag, `.none` local-only by default), constructing the `ConsentStore`
/// and other services, and injecting them into the SwiftUI Environment so `AppGate` can route on
/// the persisted `ConsentState`.
@main
struct EndoAppApp: App {
    var body: some Scene {
        WindowGroup {
            // Plan 02 replaces PlaceholderRootView with the real AppGate (consent-gated routing).
            PlaceholderRootView()
        }
    }
}
