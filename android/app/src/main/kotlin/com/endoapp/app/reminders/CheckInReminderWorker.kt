package com.endoapp.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.endoapp.app.App
import java.time.LocalDate
import java.time.LocalTime

/**
 * The [CoroutineWorker] WorkManager fires (inexactly) for the daily check-in reminder. At delivery it
 * reads the reminder prefs, applies quiet-hours + already-logged suppression, and posts the generic
 * notification — WITHOUT ever cancelling its own repeating registration (a suppressed day simply
 * re-fires tomorrow).
 *
 * The suppression DECISION is factored into the pure/seam-driven [CheckInReminderLogic.fire] so it is
 * proven behaviorally off fakes ([com.endoapp.app.reminders.ReminderWorkerSuppressTest]) without
 * standing up WorkManager's worker factory. [doWork] only wires the real collaborators from the
 * process [App] and delegates.
 */
class CheckInReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = (applicationContext as App).dependencies
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        CheckInReminderLogic.fire(
            prefs = deps.reminderSettings.current(),
            nowMinuteOfDay = nowMinute,
            loggedToday = {
                // The consent-gated repository throws pre-consent; a today-marker means already logged.
                deps.repository.dayEntriesForDay(LocalDate.now().toEpochDay()).isNotEmpty()
            },
            postReminder = { deps.reminderNotifier.postReminder() },
        )
        // Always success: a suppressed fire is a normal outcome, not a failure, and the periodic work
        // must keep its schedule for tomorrow.
        return Result.success()
    }
}

/**
 * The pure suppression gate for a reminder fire — value/seam-in, effect-out, no Android types. Returns
 * `true` iff it posted. Order: disabled → quiet hours → pre-consent (fail safe) → already logged →
 * post.
 */
object CheckInReminderLogic {

    /**
     * Decide-and-fire. [loggedToday] may throw [IllegalStateException] when the repository is accessed
     * pre-consent; that is caught and treated as "do not post" (fail safe — never a notification while
     * unconsented). [postReminder] runs only when the day is enabled, outside quiet hours, and not yet
     * logged.
     *
     * @return whether the reminder was posted.
     */
    suspend fun fire(
        prefs: ReminderPrefs,
        nowMinuteOfDay: Int,
        loggedToday: suspend () -> Boolean,
        postReminder: () -> Unit,
    ): Boolean {
        if (!prefs.enabled) return false
        if (prefs.quietHoursEnabled &&
            QuietHours.isInQuietHours(nowMinuteOfDay, prefs.quietStart, prefs.quietEnd)
        ) {
            return false
        }
        val logged = try {
            loggedToday()
        } catch (_: IllegalStateException) {
            return false // pre-consent: fail safe, no notification.
        }
        if (logged) return false
        postReminder()
        return true
    }
}
