// Requirement: ONBD-01 — the consent gate (RESEARCH §Code Examples, Pattern 3).
//
// `AppGate` is the top-level route for the whole app. It reads the injected consent state and
// shows EITHER the consent prompt (when not consented) OR the main shell (when consented). The
// critical security invariant (threat T-01-03): `WalkingSkeletonView` — the ONLY path that writes a
// health record — is constructed exclusively in the `.consented` branch. No health-data processing
// is reachable before opt-in.
//
// The `.notConsented` branch renders the warm `OnboardingView` (disclaimer ONBD-02 + privacy link
// ONBD-04 before the explicit consent action ONBD-01). The `.consented` branch renders the main
// shell (`WalkingSkeletonView`, which routes to `SettingsView` for withdraw + customization).
import SwiftUI

struct AppGate: View {
    @Environment(\.consentStore) private var consent

    var body: some View {
        // Reading `consent.state` is the routing decision (ONBD-01). The `@Observable` store
        // re-renders this view when consent is granted/withdrawn.
        switch consent.state {
        case .notConsented:
            // Warm onboarding: disclaimer (ONBD-02) + privacy link (ONBD-04) before consent (ONBD-01).
            OnboardingView()
        case .consented:
            // The ONLY branch that constructs the health-data write path (T-01-03).
            WalkingSkeletonView()
        }
    }
}
