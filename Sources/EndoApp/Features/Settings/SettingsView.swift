// Requirement: D-04 (withdraw consent in Settings) + D-07 (optional terminology/identity
// customization) + ONBD-04 (privacy reachable from Settings).
//
// Reachable from the main shell. It lets the user:
//   (a) withdraw consent — with a plain explanation of what withdrawal means — which re-gates the
//       app (the `@Observable` ConsentStore flips `state`, so `AppGate` re-routes to onboarding);
//   (b) customize terminology (bleeding term), pronouns, and display name (D-07, optional, never a
//       forced wall);
//   (c) open the offline privacy policy.
import SwiftUI

struct SettingsView: View {
    @Environment(\.consentStore) private var consent
    @Environment(\.terminologyStore) private var terminologyStore

    @State private var showingWithdrawConfirmation = false

    // Local editable copies of the free-text fields, committed back to the store on change.
    @State private var displayName: String = ""
    @State private var pronouns: String = ""

    var body: some View {
        // No own NavigationStack — pushed onto the shell's stack (HomeView's NavigationStack).
        Form {
                // (b) Terminology customization (D-07).
                Section("How Endo-App refers to things") {
                    Picker("Bleeding", selection: bleedingTermBinding) {
                        ForEach(TerminologyPreference.BleedingTerm.allCases, id: \.self) { term in
                            Text(term.noun.capitalized).tag(term)
                        }
                    }
                    TextField("Pronouns (optional)", text: $pronouns)
                        .textInputAutocapitalization(.never)
                    TextField("Display name (optional)", text: $displayName)
                }

                // (c) Privacy reachable from Settings (ONBD-04).
                Section {
                    NavigationLink {
                        PrivacyPolicyView()
                    } label: {
                        Label("Privacy", systemImage: "lock.shield")
                    }
                }

                // (a) Withdraw consent (D-04).
                Section {
                    Button(role: .destructive) {
                        showingWithdrawConfirmation = true
                    } label: {
                        Text("Withdraw consent")
                    }
                    .accessibilityIdentifier("settings.withdrawButton")
                } footer: {
                    Text(
                        "Withdrawing your consent returns the app to the welcome screen and stops "
                        + "any further processing of your health data. Anything you have already "
                        + "logged stays on your device until you choose to delete it; you can give "
                        + "consent again at any time."
                    )
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                displayName = terminologyStore.preference.displayName ?? ""
                pronouns = terminologyStore.preference.pronouns ?? ""
            }
            .onChange(of: displayName) { _, newValue in commitFreeText(displayName: newValue, pronouns: pronouns) }
            .onChange(of: pronouns) { _, newValue in commitFreeText(displayName: displayName, pronouns: newValue) }
            .confirmationDialog(
                "Withdraw consent?",
                isPresented: $showingWithdrawConfirmation,
                titleVisibility: .visible
            ) {
                Button("Withdraw consent", role: .destructive) {
                    // Re-gates the app (D-04): AppGate re-routes to onboarding on the state change.
                    consent.withdraw()
                }
                Button("Keep consent", role: .cancel) {}
            } message: {
                Text(
                    "You'll return to the welcome screen and the app will stop processing your "
                    + "health data. Your existing entries remain on your device until you delete them."
                )
            }
    }

    // MARK: - Bindings / persistence

    private var bleedingTermBinding: Binding<TerminologyPreference.BleedingTerm> {
        Binding(
            get: { terminologyStore.preference.bleedingTerm },
            set: { terminologyStore.preference.bleedingTerm = $0 }
        )
    }

    /// Bounds free-text length (threat T-01-05) and writes back to the store. Empty strings are
    /// stored as `nil` so "unset" is distinguishable from "blank".
    private func commitFreeText(displayName: String, pronouns: String) {
        var pref = terminologyStore.preference
        let boundedName = String(displayName.prefix(TerminologyStore.maxDisplayNameLength))
        let boundedPronouns = String(pronouns.prefix(TerminologyStore.maxDisplayNameLength))
        pref.displayName = boundedName.isEmpty ? nil : boundedName
        pref.pronouns = boundedPronouns.isEmpty ? nil : boundedPronouns
        terminologyStore.preference = pref
    }
}
