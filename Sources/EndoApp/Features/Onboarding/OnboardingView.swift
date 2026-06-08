// Requirement: D-01 (warm, short onboarding) + ONBD-02 (disclaimer) + ONBD-04 (privacy link) before
// the explicit consent action (ONBD-01, D-02).
//
// This is the `.notConsented` root (replacing the Plan-02 inline prompt). It is warm and plain (not
// clinical), surfaces the `DisclaimerBanner` ("not medical advice / not a medical device", D-05),
// links to the offline `PrivacyPolicyView` BEFORE consent (ONBD-04), explains in plain language that
// the next step is consent to process health data on-device + private iCloud (D-02), and embeds
// `ConsentView` as the final step. All bleeding/cycle copy resolves through `Terminology` (D-06/08).
import SwiftUI

struct OnboardingView: View {
    @Environment(\.terminologyStore) private var terminologyStore
    @State private var showingPrivacy = false

    private var terminology: Terminology { Terminology(preference: terminologyStore.preference) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    VStack(spacing: 10) {
                        Image(systemName: "heart.text.square")
                            .font(.system(size: 44))
                            .foregroundStyle(.tint)
                            .accessibilityHidden(true)
                        Text("Welcome to Endo-App")
                            .font(.title.weight(.bold))
                            .multilineTextAlignment(.center)
                        // Warm, validating, non-clinical (D-01). Copy routes through Terminology.
                        Text(
                            "A calm, private place to track your \(terminology.bleedingNoun), "
                            + "symptoms, and \(terminology.cycleNoun) — and to slowly make sense "
                            + "of your own patterns, at your own pace."
                        )
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                    }
                    .padding(.top, 24)

                    // ONBD-02 / D-05 — the disclaimer is visible before the consent action.
                    DisclaimerBanner()
                        .accessibilityIdentifier("onboarding.disclaimer")

                    // ONBD-04 — privacy reachable before consent.
                    Button {
                        showingPrivacy = true
                    } label: {
                        Label("Read how your data stays private", systemImage: "lock.shield")
                            .font(.subheadline)
                    }
                    .accessibilityIdentifier("onboarding.privacyLink")

                    // Final step: the explicit consent action (ONBD-01, D-02).
                    ConsentView()
                }
                .padding()
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingPrivacy) {
                NavigationStack { PrivacyPolicyView() }
            }
        }
    }
}
