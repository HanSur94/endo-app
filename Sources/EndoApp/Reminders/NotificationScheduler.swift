// Requirement: CHCK-02 — opt-in, fatigue-aware local reminders (D-17/18/19/20).
//
// Local notifications via `UNUserNotificationCenter` ONLY (D-17) — no server/push, matching the
// no-backend, privacy-first constraint. The scheduler sits behind a PROTOCOL (mirroring the
// `Repository` seam) so the fatigue DECISIONS are pure, unit-testable functions and the system calls
// are a thin, mockable shell — XCUITest cannot assert system notifications, so the logic must be
// reachable without the notification center.
//
// NO capability/entitlement is added to project.yml: local notifications need none (no
// aps-environment, no Background Modes — those are for remote/push, which this app does not use).
//
// Copy posture (D-21): reminder copy is GENTLE and NON-COERCIVE — no streaks, no guilt. Audited by
// CopyAuditTests (incl. the "her" substring guard — the body avoids "here's").
import Foundation
import UserNotifications

// MARK: - Copy constants (D-21, gentle/non-coercive — audited by CopyAuditTests)

/// User-facing reminder copy. Kept here as constants so CopyAuditTests can read the exact strings.
enum ReminderCopy {
    /// The notification title. Plain, non-diagnostic, non-coercive.
    static let title = "Daily check-in"
    /// The notification body. Gentle framing — no streak/guilt pressure (D-21). Worded to avoid the
    /// "her" substring (e.g. "here's") so it passes the forbiddenGenderedTokens audit.
    static let body = "Whenever you have a moment, your daily check-in is ready."

    /// Everything CopyAuditTests audits for forbidden verbs/gendered tokens.
    static var auditableStrings: [String] { [title, body] }
}

// MARK: - Protocol seam (mirrors the Repository seam — testable/mockable)

/// The notification-scheduling seam (D-17). `@MainActor` because `UNUserNotificationCenter` and the
/// UI that drives it are main-actor-bound; the concrete impl wraps the system center, the mock
/// records calls, and the pure decision logic lives in free functions below.
@MainActor
protocol NotificationScheduler {
    /// Request notification authorization JIT — only on first enable (D-20).
    func requestAuthorization() async -> Bool
    /// The current authorization status (drives the denied-state UI, D-20).
    func authorizationStatus() async -> UNAuthorizationStatus
    /// Replace all pending daily reminders with one repeating request per non-quiet time (D-19a).
    func reschedule(times: [DateComponents], quietHours: QuietHours?) async
    /// Cancel today's pending reminder once the day is logged (auto-suppress, D-19b).
    func cancelToday() async
    /// Cancel every pending reminder (e.g. the master toggle turned off).
    func cancelAll() async
}

// MARK: - QuietHours window
//
// DEVIATION (Rule 1/3, documented in SUMMARY): the plan specified the quiet-hours window as
// `ClosedRange<DateComponents>`, but a MIDNIGHT-CROSSING window (e.g. 22:00–07:00, D-19a / RESEARCH
// Open Question 2) has start > end, which TRAPS the `ClosedRange` invariant (`lowerBound <= upperBound`)
// — even `ClosedRange(uncheckedBounds:)` traps in this toolchain. `ClosedRange` therefore cannot
// represent the locked requirement. We use a tiny `Sendable` value type instead: it carries the literal
// `start`/`end` clock times and lets `isInQuietHours` handle the wrap. This is the foundation plan; no
// downstream plan has consumed the old type yet.

/// A quiet-hours window expressed as literal start/end clock times (hour/minute). Supports both a
/// same-day window (start <= end) and a midnight-crossing one (start > end, e.g. 22:00–07:00).
struct QuietHours: Sendable, Equatable {
    /// The window's start time (inclusive).
    var start: DateComponents
    /// The window's end time (inclusive).
    var end: DateComponents

    init(start: DateComponents, end: DateComponents) {
        self.start = start
        self.end = end
    }
}

// MARK: - Pure decision logic (unit-tested — NO UNUserNotificationCenter)

/// The stable identifier namespace for the app's daily reminders.
private let reminderIdentifierPrefix = "endo.reminder."

/// Whether a candidate reminder `time` falls inside the user's `quietHours` window (D-19a).
///
/// Handles BOTH a same-day window (e.g. 13:00–14:00, start <= end) AND a MIDNIGHT-CROSSING window
/// (e.g. 22:00–07:00, start > end). A `nil` window means "no quiet hours" ⇒ never quiet. Comparison is
/// on minutes-of-day; bounds are inclusive.
func isInQuietHours(_ time: DateComponents, quietHours: QuietHours?) -> Bool {
    guard let quietHours else { return false }
    let t = minutesOfDay(time)
    let lo = minutesOfDay(quietHours.start)
    let hi = minutesOfDay(quietHours.end)
    if lo <= hi {
        // Same-day window: quiet when lo <= t <= hi.
        return t >= lo && t <= hi
    } else {
        // Midnight-crossing window (e.g. 22:00–07:00): quiet when t >= lo OR t <= hi.
        return t >= lo || t <= hi
    }
}

/// Whether the daily reminder should fire TODAY (D-19b auto-suppress). Returns `false` when the user
/// has already logged today (so we never nag a day that's done); `true` otherwise.
func shouldFireToday(lastLoggedDay: Date?, now: Date) -> Bool {
    guard let lastLoggedDay else { return true }   // never logged ⇒ fire
    return !Calendar.current.isDate(lastLoggedDay, inSameDayAs: now)
}

/// Minutes since midnight for a `DateComponents` (hour/minute). Missing components count as 0.
private func minutesOfDay(_ components: DateComponents) -> Int {
    (components.hour ?? 0) * 60 + (components.minute ?? 0)
}

// MARK: - System implementation (the thin, untested shell)

/// Concrete `NotificationScheduler` over `UNUserNotificationCenter.current()`. The pure decisions
/// above keep this layer thin: it only translates decisions into UN requests.
struct SystemNotificationScheduler: NotificationScheduler {
    private var center: UNUserNotificationCenter { .current() }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func reschedule(times: [DateComponents], quietHours: QuietHours?) async {
        // Replace all pending daily reminders, then re-add one repeating request per non-quiet time.
        center.removeAllPendingNotificationRequests()
        let content = UNMutableNotificationContent()
        content.title = ReminderCopy.title
        content.body = ReminderCopy.body
        content.userInfo = ["deepLink": "checkin"]   // D-16: tap deep-links into the check-in sheet

        var index = 0
        for time in times where !isInQuietHours(time, quietHours: quietHours) {
            let trigger = UNCalendarNotificationTrigger(dateMatching: time, repeats: true)
            let request = UNNotificationRequest(
                identifier: "\(reminderIdentifierPrefix)\(index)",
                content: content, trigger: trigger)
            try? await center.add(request)
            index += 1
        }
    }

    func cancelToday() async {
        // The repeating requests are keyed by stable ids; "today" is handled by cancelling the
        // pending deliveries — for a single-fire-per-day repeating trigger this prevents today's
        // nag once the day is logged (D-19b). A full reschedule re-arms tomorrow.
        let pending = await center.pendingNotificationRequests()
        let ids = pending.map(\.identifier).filter { $0.hasPrefix(reminderIdentifierPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    func cancelAll() async {
        center.removeAllPendingNotificationRequests()
    }
}

// MARK: - Mock (test double — no system calls)

/// A `NotificationScheduler` test double that RECORDS calls instead of touching the system center.
final class MockNotificationScheduler: NotificationScheduler {
    /// The authorization result `requestAuthorization()` returns (test-configurable).
    var authorizationToReturn = true
    /// The status `authorizationStatus()` returns (test-configurable).
    var statusToReturn: UNAuthorizationStatus = .authorized

    private(set) var requestAuthorizationCallCount = 0
    private(set) var rescheduleCalls: [(times: [DateComponents], quietHours: QuietHours?)] = []
    private(set) var cancelTodayCallCount = 0
    private(set) var cancelAllCallCount = 0

    func requestAuthorization() async -> Bool {
        requestAuthorizationCallCount += 1
        return authorizationToReturn
    }

    func authorizationStatus() async -> UNAuthorizationStatus { statusToReturn }

    func reschedule(times: [DateComponents], quietHours: QuietHours?) async {
        rescheduleCalls.append((times, quietHours))
    }

    func cancelToday() async { cancelTodayCallCount += 1 }
    func cancelAll() async { cancelAllCallCount += 1 }
}
