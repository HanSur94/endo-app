package com.endoapp.app.reminders

import kotlinx.coroutines.flow.Flow

/**
 * The single reminder facade the Settings reminders section drives — bundles the three reminder
 * collaborators ([ReminderSettings], [ReminderScheduler], [ReminderNotifier]) so ONE object threads
 * from the composition root through the shell to the UI (mirroring how the med/treatment seams thread
 * in Plan 04). Every operation persists the prefs change AND re-registers the (inexact) schedule so the
 * two never drift.
 *
 * Enabling seeds a single default 20:00 time when none exist (the JIT-enable flow) and creates the one
 * notification channel; disabling cancels the schedule. This class owns NO Android UI and NO permission
 * logic — the just-in-time `POST_NOTIFICATIONS` request lives only in the Settings section's enable
 * handler.
 */
class ReminderController(
    private val settings: ReminderSettings,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {

    /** The live reminder prefs the section renders from. */
    val prefs: Flow<ReminderPrefs> = settings.prefs

    /** Turn reminders ON: create the channel, seed a default time if none, persist, and schedule. */
    suspend fun enable() {
        notifier.ensureChannel()
        val existing = settings.current()
        if (existing.times.isEmpty()) {
            settings.setTimes(listOf(ReminderPrefs.DEFAULT_TIME))
        }
        settings.setEnabled(true)
        scheduler.reschedule(settings.current())
    }

    /** Turn reminders OFF: persist disabled and cancel every scheduled reminder work. */
    suspend fun disable() {
        settings.setEnabled(false)
        scheduler.cancelAll()
    }

    /** Replace the reminder time(s) with a single [minute]-of-day and reschedule idempotently. */
    suspend fun setTime(minute: Int) {
        settings.setTimes(listOf(minute))
        scheduler.reschedule(settings.current())
    }

    /** Update the quiet-hours window (on/off + start/end minutes) and reschedule idempotently. */
    suspend fun setQuietHours(enabled: Boolean, start: Int, end: Int) {
        settings.setQuietHours(enabled, start, end)
        scheduler.reschedule(settings.current())
    }
}
