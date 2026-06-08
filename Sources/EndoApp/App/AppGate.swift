// Requirement: ONBD-01 — the consent gate (RESEARCH §Code Examples, Pattern 3).
//
// `AppGate` is the top-level route for the whole app. It reads the injected consent state and
// shows EITHER the consent prompt (when not consented) OR the main shell (when consented). The
// critical security invariant (threat T-01-03): `WalkingSkeletonView` — the ONLY path that writes a
// health record — is constructed exclusively in the `.consented` branch. No health-data processing
// is reachable before opt-in.
//
// For this plan the `.notConsented` branch shows a minimal inline consent prompt. Plan 03 replaces
// it with the warm onboarding flow + disclaimer (ONBD-02) + privacy link (ONBD-04).
import SwiftUI

struct AppGate: View {
    @Environment(\.consentStore) private var consent

    var body: some View {
        // Reading `consent.state` is the routing decision (ONBD-01). The `@Observable` store
        // re-renders this view when consent is granted/withdrawn.
        switch consent.state {
        case .notConsented:
            // Minimal inline consent prompt (Plan 03 replaces with warm onboarding + disclaimer).
            ConsentPromptView(onConsent: { consent.grant(at: .now) })
        case .consented:
            // The ONLY branch that constructs the health-data write path (T-01-03).
            WalkingSkeletonView()
        }
    }
}

/// Minimal up-front consent prompt (D-02). Placeholder for the Plan 03 onboarding flow.
private struct ConsentPromptView: View {
    let onConsent: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.shield")
                .font(.largeTitle)
                .foregroundStyle(.tint)
            Text("Your data stays private")
                .font(.title2.weight(.semibold))
            Text("Endo-App keeps everything on your device. Nothing is processed until you agree.")
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button("I consent", action: onConsent)
                .buttonStyle(.borderedProminent)
            // ONBD-02 / ONBD-04 surfaces (full disclaimer + privacy link) land in Plan 03.
        }
        .padding()
    }
}
