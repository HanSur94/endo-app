package com.endoapp.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.endoapp.app.EXTRA_REMINDER_CHECK_IN
import com.endoapp.app.MainActivity
import com.endoapp.app.content.ReminderCopy

/**
 * The reminder's notification surface — creates EXACTLY ONE [NotificationChannel] and posts the single
 * generic daily-check-in notification. This is the ONLY place in the app that touches a notification
 * API (the no-flare-notification invariant, CONTEXT §5 / threat T-20-02d).
 *
 * The notification content is DELIBERATELY generic (title/body from [ReminderCopy], audited): no
 * symptom / flare / streak wording, because a lock screen is semi-public (threat T-20-02c). Its
 * `contentIntent` carries only the non-sensitive [EXTRA_REMINDER_CHECK_IN] flag to [MainActivity],
 * which flips the [com.endoapp.app.CheckInRouter] flag — so a tap lands on the consent gate first and
 * can never construct health UI pre-consent (threat T-20-02).
 *
 * Uses the platform [Notification.Builder] (minSdk 26 guarantees the channel API), so no extra
 * dependency and no `NotificationCompat` is needed.
 */
class ReminderNotifier(private val context: Context) {

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /** Create the single reminder channel if it does not exist yet (idempotent — safe on every fire). */
    fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ReminderCopy.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = ReminderCopy.CHANNEL_DESCRIPTION }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Post the generic daily-check-in notification. On Android 13+ this is a no-op if
     * `POST_NOTIFICATIONS` was denied (the system drops it) — fail-safe, never a crash.
     */
    fun postReminder() {
        ensureChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(ReminderCopy.NOTIFICATION_TITLE)
            .setContentText(ReminderCopy.NOTIFICATION_BODY)
            .setContentIntent(deepLinkIntent())
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** The tap target — [MainActivity] carrying the non-sensitive reminder flag (lands on the gate). */
    private fun deepLinkIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_REMINDER_CHECK_IN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /** The single reminder channel id — the ONLY channel in the app. */
        const val CHANNEL_ID: String = "daily-check-in"

        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE = 20_05
    }
}
