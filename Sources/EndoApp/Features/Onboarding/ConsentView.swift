// Requirement: ONBD-01 / D-02 — the explicit consent action (GDPR Art. 9 "explicit, specific").
//
// `ConsentView` is the FINAL step of onboarding: a plain-language summary of exactly what is being
// consented to (processing health data on-device + in private iCloud) and an explicit "I consent"
// button that calls the injected `consentStore.grant(at:)`. No health data is processed before this
// action — the gate (`AppGate`) only constructs the write path after `grant`.
import SwiftUI

struct ConsentView: View {
    @Environment(\.consentStore) private var consent

    var body: some View {
        VStack(spacing: 16) {
            Text("Ready when you are")
                .bloomH2()

            Text(
                "To use Endo-App, you consent to it processing the health information you log "
                + "on this device, and — if you turn on sync — in your own private iCloud. "
                + "Nothing is processed until you tap the button below, and you can withdraw "
                + "your consent at any time in Settings."
            )
            .font(.body)
            .multilineTextAlignment(.center)
            .foregroundStyle(Color.inkSecondary)

            Button("I consent") {
                // The ONLY place consent is granted (ONBD-01, D-02). No health data before this.
                consent.grant(at: .now)
            }
            .bloomPrimaryButton()
            .accessibilityIdentifier("consent.grantButton")
        }
        .padding()
    }
}
