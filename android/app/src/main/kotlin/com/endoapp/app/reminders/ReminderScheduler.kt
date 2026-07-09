package com.endoapp.app.reminders

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily check-in reminder via WorkManager **INEXACT periodic work** — the app's only
 * scheduling mechanism. Minutes of drift are acceptable (UI-SPEC §4); this DELIBERATELY never uses an
 * exact-alarm API and never `setExact*` <!-- setExact --> (Play restricts `SCHEDULE_EXACT_ALARM` to
 * alarm-clock apps; a health tracker requesting it invites review friction — threat T-20-02b).
 *
 * WorkManager persists its work across process death AND reboot on its own, so there is NO
 * `RECEIVE_BOOT_COMPLETED` receiver of ours and no boot handling to write.
 *
 * Rescheduling is IDEMPOTENT: [reschedule] cancels every prior reminder work (by [WORK_TAG]) and then
 * re-enqueues a unique periodic work per configured time with [ExistingPeriodicWorkPolicy.UPDATE], so
 * every Settings edit re-registers cleanly with no duplication. The per-time uniqueness key is derived
 * from the minute-of-day, so removing a time drops exactly its work.
 *
 * The [WorkManager] instance is resolved from [context] internally (via [WorkManager.getInstance]) so
 * NO `androidx.work` import leaks into the composition root — keeping every scheduling API inside this
 * reminders package (the separation invariant).
 */
class ReminderScheduler(
    private val workManager: WorkManager,
    private val now: () -> LocalTime = { LocalTime.now() },
) {

    constructor(context: Context) : this(resolveWorkManager(context))

    /**
     * Re-register the reminder schedule from [prefs]. Cancels all prior reminder work, then — only if
     * reminders are enabled — enqueues one INEXACT periodic work per configured time, each anchored to
     * that time via an initial delay. Disabled or empty prefs leave nothing scheduled.
     */
    fun reschedule(prefs: ReminderPrefs) {
        workManager.cancelAllWorkByTag(WORK_TAG)
        if (!prefs.enabled) return
        for (minute in prefs.times.toSet()) {
            val request = PeriodicWorkRequestBuilder<CheckInReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMinutes(minute), TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                uniqueName(minute),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    /** Cancel every reminder work (used when reminders are turned off). */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    /** Minutes from "now" until the next occurrence of [targetMinute] (today or tomorrow). */
    private fun initialDelayMinutes(targetMinute: Int): Long {
        val current = now()
        val nowMinute = current.hour * 60 + current.minute
        val diff = targetMinute - nowMinute
        return (if (diff <= 0) diff + MINUTES_PER_DAY else diff).toLong()
    }

    companion object {
        /** The tag every reminder work carries — the single cancellation handle. */
        const val WORK_TAG: String = "com.endoapp.app.reminders.daily-check-in"

        private const val MINUTES_PER_DAY = 24 * 60

        /** The per-time unique work name — one periodic work per configured minute-of-day. */
        fun uniqueName(minute: Int): String = "checkin-reminder-$minute"

        /**
         * Resolve the process [WorkManager], initializing it ON DEMAND if the default androidx.startup
         * initializer is disabled (it is — see the manifest, so no `androidx.work` init leaks into the
         * composition root). Guarded by [WorkManager.isInitialized] so it is a no-op when already up
         * (e.g. the `work-testing` lane initialized it first).
         */
        private fun resolveWorkManager(context: Context): WorkManager {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
            }
            return WorkManager.getInstance(context.applicationContext)
        }
    }
}
