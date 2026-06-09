// Requirement: ONBD-01 — the consent gate (RESEARCH §Code Examples, Pattern 3).
//
// `AppGate` is the top-level route for the whole app. It reads the injected consent state and
// shows EITHER the consent prompt (when not consented) OR the main shell (when consented). The
// critical security invariant (threat T-01-03 / T-02-06): `HomeView` — the ONLY path that constructs
// the health-data logging UI — is built exclusively in the `.consented` branch. No health-data
// processing is reachable before opt-in.
//
// The `.notConsented` branch renders the warm `OnboardingView` (disclaimer ONBD-02 + privacy link
// ONBD-04 before the explicit consent action ONBD-01). The `.consented` branch renders the main
// shell (`HomeView` — day history + "Log today" editor, routing to `SettingsView` for withdraw +
// customization). `HomeView` replaced the Phase-1 `WalkingSkeletonView` in Plan 02-02.
//
// DEEP-LINK CONSENT INVARIANT (D-16 / T-03-14): the reminder notification deep-link flips a
// `CheckInRouter` flag, but the check-in sheet is presented by `HomeView` — which AppGate builds ONLY
// in the `.consented` branch below. The router carries a non-sensitive intent flag, not health data,
// and presentation is therefore gated by this switch: a deep-link can never surface the check-in sheet
// before opt-in. In the `.notConsented` branch the router flag is simply not observed (no health UI).
import SwiftUI

struct AppGate: View {
    @Environment(\.consentStore) private var consent

    var body: some View {
        // Reading `consent.state` is the routing decision (ONBD-01). The `@Observable` store
        // re-renders this view when consent is granted/withdrawn.
        switch consent.state {
        case .notConsented:
            // Warm onboarding: disclaimer (ONBD-02) + privacy link (ONBD-04) before consent (ONBD-01).
            // The deep-link CheckInRouter flag is deliberately NOT observed here — no health UI, no
            // check-in sheet can be presented before consent (T-03-14).
            OnboardingView()
        case .consented:
            // The ONLY branch that constructs the health-data logging UI (T-01-03 / T-02-06 / T-08-12).
            // A bottom `TabView` exposes Home + the Insights dashboard — BOTH built solely here, so no
            // health UI (including the Insights dashboard reading health-derived insights) is constructed
            // before explicit consent. `HomeView` still observes `\.checkInRouter` for the reminder
            // deep-link, so deep-link presentation stays consent-gated by construction (D-16).
            ConsentedShell()
        }
    }
}

/// The consented-only main shell (T-08-12): a bottom `TabView` with the "Log" (Home) tab and the
/// "Insights" dashboard tab (`tab.insights`, UI-SPEC §D). Constructed EXCLUSIVELY in AppGate's
/// `.consented` branch — the dashboard, like the logging UI, is never built before opt-in.
private struct ConsentedShell: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem {
                    Label("Log", systemImage: "square.and.pencil")
                }

            InsightsView()
                .tabItem {
                    // The id MUST live on the tab-item label: a `.accessibilityIdentifier` on the tab's
                    // CONTENT view does not propagate to the tab-bar button XCUITest queries (UI-SPEC §D).
                    Label("Insights", systemImage: "chart.line.uptrend.xyaxis")
                        .accessibilityIdentifier("tab.insights")
                }
        }
    }
}
