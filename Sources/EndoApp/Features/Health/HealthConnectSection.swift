// Requirement: DATA-05 / D-05/D-06/D-07/D-08 (Phase 9, Plan 09-03) — the contextual connect affordance.
//
// CONTEXTUAL CONNECT (D-05/D-07): a PASSIVE "Connect Apple Health" button reused in Settings and on
// logging surfaces. It NEVER triggers Apple's system sheet directly — it presents `HealthRationaleView`
// as a sheet first (D-06). There is NO launch mega-prompt anywhere.
//
// GRACEFUL DENIAL / NO READ-STATUS PROBING (D-08, threat T-09-12 / Pitfall 1): HealthKit hides read
// status, so we do NOT branch on `authorizationStatus(for:)` to decide what to show. The ONLY thing we
// remember is a local, non-sensitive flag: has the user gone through the rationale + system sheet at
// least once? Before that → tapping shows the rationale. After that → tapping deep-links to the Settings
// app (Apple does not let an app re-request a denied read in-app, D-08). Either way the affordance is
// passive and never nags. This flag is device-local UI state (like the anchor / SyncMode), never in the
// synced store.
import SwiftUI

/// The passive, reusable connect affordance (D-05/D-07/D-08). Drop it into Settings or any logging
/// surface; it owns the rationale-sheet presentation and the previously-decided deep-link.
struct HealthConnectSection: View {
    /// Whether the user has already gone through connect once (rationale + system sheet). Device-local UI
    /// state — NOT a read-status probe (which Apple hides) and NEVER synced. `false` ⇒ show the rationale;
    /// `true` ⇒ deep-link to Settings (reads can't be re-requested in-app, D-08).
    @AppStorage("health.connect.decided") private var hasDecided = false

    @State private var showingRationale = false

    var body: some View {
        Button {
            if hasDecided {
                // Previously decided — Apple won't let us re-request a denied read in-app, so route to the
                // system Settings app (D-08). Passive, never a nag.
                openSystemSettings()
            } else {
                // First time — show the in-app rationale BEFORE any system sheet (D-06).
                showingRationale = true
            }
        } label: {
            Label(HealthConnectCopy.connectButton, systemImage: "heart.text.square")
        }
        // The id reflects the action so the E2E can assert which path a previously-decided user takes.
        .accessibilityIdentifier(hasDecided ? "health.connect.openSettings" : "health.connect.button")
        .sheet(isPresented: $showingRationale, onDismiss: {
            // The rationale "Continue" went through the system sheet — mark decided so future taps
            // deep-link to Settings (D-08). "Not now" also marks nothing destructive; we only flip on a
            // dismiss that followed the connect flow. To keep it simple and non-nagging we mark decided on
            // any dismiss of the rationale: the user has seen the contextual prompt, so subsequent taps go
            // to Settings rather than re-presenting the rationale (no nag loop, D-08).
            hasDecided = true
        }) {
            HealthRationaleView()
        }
    }

    /// Deep-link to the system Settings app where the user can adjust what Apple Health shares (D-08).
    private func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}
