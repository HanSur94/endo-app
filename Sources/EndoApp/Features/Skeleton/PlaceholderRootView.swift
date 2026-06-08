import SwiftUI

/// Trivial placeholder root for the Phase 1 walking skeleton.
///
/// This view exists only to prove the app launches as a real iOS bundle. Plan 02 replaces the
/// root of the app with the real `AppGate` (which routes between the onboarding/consent flow and
/// the main shell based on the persisted `ConsentState`). No production behavior lives here.
struct PlaceholderRootView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.seal")
                .font(.largeTitle)
                .foregroundStyle(.tint)
            Text("Endo-App — scaffold OK")
                .font(.headline)
        }
        .padding()
    }
}

#Preview {
    PlaceholderRootView()
}
