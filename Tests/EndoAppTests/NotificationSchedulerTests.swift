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
