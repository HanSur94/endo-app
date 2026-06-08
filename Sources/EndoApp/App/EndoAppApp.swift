// Requirement: D-22 composition root + ONBD-01 consent gate as the app root + D-16 deep-link wiring.
import SwiftUI
import UserNotifications

/// The composition root (D-22) of Endo-App.
///
/// Builds `AppDependencies` (the SwiftData `ModelContainer` behind the `SyncMode` flag — `.none`
/// local-only by default — the `ConsentStore`, the `NotificationScheduler`, and the `CheckInRouter`),
/// injects them into the SwiftUI Environment, and renders `AppGate` as the root so the entire app is
/// consent-gated (ONBD-01). No health record is reachable before consent because the logging UI
/// (`HomeView`) lives only in `AppGate`'s `.consented` branch.
///
/// DEEP-LINK (D-16, CHCK-01): the app sets a `UNUserNotificationCenterDelegate` early. When a reminder
/// is tapped, the delegate reads `userInfo["deepLink"] == "checkin"`, hops to the main actor, and flips
/// the injected `CheckInRouter`. The consented-branch UI (`HomeView`) then presents `DailyCheckInView`
/// — so a deep-link can NEVER surface health UI before consent (T-03-14).
@main
struct EndoAppApp: App {
    @State private var dependencies: AppDependencies
    /// The notification delegate — retained for the app's lifetime; set as the UN center delegate at
    /// init so a tap-to-launch deep-link is captured.
    private let notificationDelegate: CheckInNotificationDelegate

    init() {
        let deps = AppDependencies()
        _dependencies = State(initialValue: deps)
        let delegate = CheckInNotificationDelegate(router: deps.checkInRouter)
        self.notificationDelegate = delegate
        // Set the delegate EARLY (composition root) so a notification that launched the app is routed.
        UNUserNotificationCenter.current().delegate = delegate
    }

    var body: some Scene {
        WindowGroup {
            AppGate()
                .environment(\.consentStore, dependencies.consentStore)
                .environment(\.terminologyStore, dependencies.terminologyStore)
                .environment(\.notificationScheduler, dependencies.notificationScheduler)
                .environment(\.checkInRouter, dependencies.checkInRouter)
                .modelContainer(dependencies.modelContainer)
        }
    }
}

/// The `UNUserNotificationCenterDelegate` (D-16). On a reminder tap it reads the non-sensitive
/// `deepLink` token from `userInfo` and flips the `CheckInRouter` so the consented-branch UI presents
/// the check-in sheet. The delegate carries NO health data — only the intent flag (T-03-17).
final class CheckInNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    private let router: CheckInRouter
    /// The today-suppression marker (CR-01). Read at delivery so a day already logged drops today's
    /// banner via `shouldFireToday(...)` — WITHOUT deleting the repeating registration (which would
    /// silently kill reminders forever). Tomorrow's occurrence still fires.
    private let suppressionStore: ReminderSuppressionStore

    init(router: CheckInRouter, suppressionStore: ReminderSuppressionStore = ReminderSuppressionStore()) {
        self.router = router
        self.suppressionStore = suppressionStore
        super.init()
    }

    /// Called when the user taps a notification (nonisolated under Swift 6). We read the deep-link
    /// token off the main actor, then hop to the main actor to mutate the `@MainActor` router state.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let deepLink = response.notification.request.content.userInfo["deepLink"] as? String
        if deepLink == "checkin" {
            // Hop to the main actor: the router is `@MainActor`-isolated and drives SwiftUI state.
            // Capture the router into a local so the task closure does NOT capture task-isolated `self`
            // (Swift 6 sending-self data-race guard) — the `@MainActor` reference crosses safely.
            let router = self.router
            Task { @MainActor in
                router.presentCheckIn = true
            }
        }
        completionHandler()
    }

    /// Present reminders as a banner even while the app is foregrounded (so a foreground reminder is
    /// still visible). No health content is shown — only the gentle ReminderCopy title/body.
    ///
    /// D-19b auto-suppress (CR-01): a reminder for a day ALREADY logged is dropped here
    /// (`completionHandler([])`) instead of removing the repeating registration. The repeating
    /// trigger therefore survives and fires again tomorrow — only TODAY's banner is suppressed.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let isReminder = (notification.request.content.userInfo["deepLink"] as? String) == "checkin"
        if isReminder, !suppressionStore.shouldPresent() {
            completionHandler([])   // today already logged — suppress this occurrence only
            return
        }
        completionHandler([.banner, .sound])
    }
}
