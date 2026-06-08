// Requirement: D-16 — the notification deep-link routing seam (CHCK-01 / CHCK-02).
//
// A tiny `@Observable` flag the `UNUserNotificationCenterDelegate` (composition root, Plan 03-04
// Task 2) sets when a reminder is tapped. `HomeView` — built ONLY inside `AppGate`'s `.consented`
// branch — observes it and presents `DailyCheckInView`. The router is injected via an `EnvironmentKey`
// exactly like `consentStore` / `terminologyStore` / `notificationScheduler`.
//
// SECURITY INVARIANT (T-03-14): the router only carries a non-sensitive intent flag. It NEVER presents
// health UI itself — presentation happens inside the consented branch (HomeView), so a deep-link can
// never surface the check-in sheet before consent. The flag is reset to `false` once consumed so a
// later tap re-triggers.
import SwiftUI

/// The `@Observable` deep-link router (D-16). The notification delegate flips `presentCheckIn` on the
/// main actor; the consented-branch UI reads it to present the fast check-in sheet.
@MainActor
@Observable
final class CheckInRouter {
    /// When `true`, the consented-branch UI should present the daily check-in sheet, then reset this.
    var presentCheckIn = false

    init(presentCheckIn: Bool = false) {
        self.presentCheckIn = presentCheckIn
    }
}

// MARK: - Environment injection (mirrors ConsentStoreKey / NotificationSchedulerKey)

/// Environment key carrying the app's `CheckInRouter` (D-16 injection seam).
private struct CheckInRouterKey: EnvironmentKey {
    // The router is `@MainActor`-isolated and SwiftUI reads environment values on the main actor, so
    // constructing the default under `assumeIsolated` is safe and satisfies the nonisolated
    // `EnvironmentKey.defaultValue` requirement under Swift 6 (same pattern as the other service keys).
    static var defaultValue: CheckInRouter {
        MainActor.assumeIsolated { CheckInRouter() }
    }
}

extension EnvironmentValues {
    /// The injected deep-link router the consented-branch UI presents the check-in sheet from (D-16).
    var checkInRouter: CheckInRouter {
        get { self[CheckInRouterKey.self] }
        set { self[CheckInRouterKey.self] = newValue }
    }
}
