// EndoApp (:app) — the reminders package, the app's SOLE notification/alarm surface (CONTEXT §5 /
// UI-SPEC §4 no-flare-notification invariant). Physically separate from any flare/insights code so the
// Phase-22 structural scan (NotificationSeparationTest, seeded this phase) stays clean.
package com.endoapp.app.reminders

/**
 * The PURE quiet-hours predicate — the Kotlin port of the iOS `isInQuietHours`. Value-in / value-out,
 * NO clock, NO Android types, so it is pinned by fast JVM assertions ([QuietHoursTest]) independent of
 * Compose or WorkManager.
 *
 * A quiet-hours window is a `[startMinute, endMinute)` half-open range over minutes-of-day
 * (`0..1439`). It correctly handles the MIDNIGHT-CROSSING case (e.g. 22:00–07:00, where
 * `startMinute > endMinute`): the window then wraps past midnight, so a minute is quiet when it is at
 * or after the start OR strictly before the end.
 *
 * A zero-length window (`startMinute == endMinute`) is treated as "no quiet hours" (never suppresses),
 * so an unconfigured/degenerate window can never silence every reminder.
 */
object QuietHours {

    /**
     * `true` when [minuteOfDay] falls inside the quiet-hours window `[startMinute, endMinute)`.
     *
     * @param minuteOfDay the candidate minute-of-day (`0..1439`).
     * @param startMinute inclusive window start (minute-of-day).
     * @param endMinute exclusive window end (minute-of-day).
     */
    fun isInQuietHours(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false // zero-length window: never quiet.
        return if (startMinute < endMinute) {
            // Same-day window, e.g. 13:00–14:00.
            minuteOfDay in startMinute until endMinute
        } else {
            // Midnight-crossing window, e.g. 22:00–07:00: quiet if at/after start OR before end.
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}
