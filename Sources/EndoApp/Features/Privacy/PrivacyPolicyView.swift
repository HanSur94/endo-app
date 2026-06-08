// Requirement: ONBD-04 — the offline in-app privacy screen (D-14).
//
// Renders `PrivacyPolicyText.body` in a scrollable screen. Fully offline — no network. Reachable
// from BOTH onboarding (before consent) and Settings. This is the user-facing surface of the
// privacy source of truth.
import SwiftUI

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Privacy")
                    .font(.largeTitle.weight(.bold))
                Text(PrivacyPolicyText.body)
                    .font(.body)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
    }
}
