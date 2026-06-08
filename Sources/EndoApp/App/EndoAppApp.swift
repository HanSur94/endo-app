// Requirement: D-22 composition root + ONBD-01 consent gate as the app root.
import SwiftUI

/// The composition root (D-22) of Endo-App.
///
/// Builds `AppDependencies` (the SwiftData `ModelContainer` behind the `SyncMode` flag — `.none`
/// local-only by default — and the `ConsentStore`), injects them into the SwiftUI Environment, and
/// renders `AppGate` as the root so the entire app is consent-gated (ONBD-01). No health record is
/// reachable before consent because the logging UI (`HomeView`) lives only in `AppGate`'s
/// `.consented` branch.
@main
struct EndoAppApp: App {
    @State private var dependencies = AppDependencies()

    var body: some Scene {
        WindowGroup {
            AppGate()
                .environment(\.consentStore, dependencies.consentStore)
                .environment(\.terminologyStore, dependencies.terminologyStore)
                .modelContainer(dependencies.modelContainer)
        }
    }
}
