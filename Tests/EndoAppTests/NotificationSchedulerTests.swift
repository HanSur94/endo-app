// Requirement: CHCK-02 — fatigue-aware reminder DECISIONS are pure + unit-tested (D-19).
//
// GREEN (Plan 03-01 Task 3). isInQuietHours handles same-day AND midnight-crossing windows;
// shouldFireToday auto-suppresses a day already logged; MockNotificationScheduler records calls.
import Testing
import Foundation
import UserNotifications
@testable import EndoApp

@Suite("CHCK-02 notification fatigue logic")
struct NotificationSchedulerTests {

    private func dc(_ h: Int, _ m: Int = 0) -> DateComponents { DateComponents(hour: h, minute: m) }

    // D-19a same-day quiet window (13:00–14:00).
    @Test func quietHours_sameDayWindow() {
        let window = QuietHours(start: dc(13), end: dc(14))
        #expect(isInQuietHours(dc(13, 30), quietHours: window))   // inside
        #expect(isInQuietHours(dc(13), quietHours: window))       // inclusive lower
        #expect(isInQuietHours(dc(14), quietHours: window))       // inclusive upper
        #expect(!isInQuietHours(dc(12, 59), quietHours: window))  // just before
        #expect(!isInQuietHours(dc(15), quietHours: window))      // after
    }

    // D-19a MIDNIGHT-CROSSING quiet window (22:00–07:00) — the Open-Question-2 case.
    @Test func quietHours_midnightCrossingWindow() {
        let window = QuietHours(start: dc(22), end: dc(7))
        #expect(isInQuietHours(dc(23), quietHours: window))       // late evening (after lower)
        #expect(isInQuietHours(dc(2), quietHours: window))        // small hours (before upper)
        #expect(isInQuietHours(dc(22), quietHours: window))       // inclusive lower
        #expect(isInQuietHours(dc(7), quietHours: window))        // inclusive upper
        #expect(!isInQuietHours(dc(12), quietHours: window))      // midday — not quiet
        #expect(!isInQuietHours(dc(21, 59), quietHours: window))  // just before the window opens
        #expect(!isInQuietHours(dc(7, 1), quietHours: window))    // just after it closes
    }

    // No quiet hours configured ⇒ never quiet.
    @Test func quietHours_nilWindow_neverQuiet() {
        #expect(!isInQuietHours(dc(3), quietHours: nil))
    }

    // D-19b auto-suppress: a day already logged today ⇒ don't fire.
    @Test func shouldFireToday_loggedToday_isFalse() {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let earlierSameDay = now.addingTimeInterval(-3600)
        #expect(!shouldFireToday(lastLoggedDay: earlierSameDay, now: now),
                "A day already logged must auto-suppress today's reminder (D-19b).")
    }

    // Not logged today (or ever) ⇒ fire.
    @Test func shouldFireToday_notLogged_isTrue() {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let yesterday = now.addingTimeInterval(-86_400)
        #expect(shouldFireToday(lastLoggedDay: yesterday, now: now))
        #expect(shouldFireToday(lastLoggedDay: nil, now: now))
    }

    // CR-01: the today-suppression mechanism must SUPPRESS today WITHOUT deleting the repeating
    // registration. We can't drive the real UNUserNotificationCenter in a unit test, so we assert the
    // delivery-time decision the delegate makes: once `cancelToday()` stamps today's marker, a reminder
    // delivered today is suppressed, but the repeating registration is untouched (the scheduler never
    // calls `removePendingNotificationRequests`), so TOMORROW still presents.
    @Test func suppressionStore_suppressesToday_butFiresTomorrow() {
        let defaults = UserDefaults(suiteName: "test.reminders.\(UUID().uuidString)")!
        let store = ReminderSuppressionStore(defaults: defaults)
        let now = Date(timeIntervalSince1970: 1_700_000_000)

        // Before any check-in: a reminder presents normally.
        #expect(store.shouldPresent(now: now), "With no logged day, today's reminder must present.")

        // After logging today (what cancelToday() records): today's banner is suppressed…
        store.lastLoggedDay = now
        #expect(!store.shouldPresent(now: now), "A day already logged must suppress today's reminder (D-19b).")

        // …but tomorrow's occurrence still presents — the repeating registration was never removed.
        let tomorrow = now.addingTimeInterval(86_400)
        #expect(store.shouldPresent(now: tomorrow),
                "Tomorrow's reminder must still fire — cancelToday() must NOT delete the repeating registration (CR-01).")
    }

    // CR-01: the suppression marker round-trips through UserDefaults (it's a local date stamp, not a
    // health record) and clears back to nil.
    @Test func suppressionStore_marker_roundTripsAndClears() {
        let defaults = UserDefaults(suiteName: "test.reminders.\(UUID().uuidString)")!
        let store = ReminderSuppressionStore(defaults: defaults)
        #expect(store.lastLoggedDay == nil)

        let day = Date(timeIntervalSince1970: 1_700_000_000)
        store.lastLoggedDay = day
        #expect(store.lastLoggedDay.map { Int($0.timeIntervalSince1970) } == Int(day.timeIntervalSince1970))

        store.lastLoggedDay = nil
        #expect(store.lastLoggedDay == nil)
    }

    // MockNotificationScheduler records reschedule/cancel calls (no system calls).
    @MainActor
    @Test func mock_recordsCalls() async {
        let mock = MockNotificationScheduler()
        let times = [dc(20)]
        await mock.reschedule(times: times, quietHours: QuietHours(start: dc(22), end: dc(7)))
        await mock.cancelToday()
        await mock.cancelAll()
        _ = await mock.requestAuthorization()

        #expect(mock.rescheduleCalls.count == 1)
        #expect(mock.rescheduleCalls.first?.times == times)
        #expect(mock.cancelTodayCallCount == 1)
        #expect(mock.cancelAllCallCount == 1)
        #expect(mock.requestAuthorizationCallCount == 1)
    }
}
