package com.endoapp.app.reminders

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the worker's fire decision ([CheckInReminderLogic.fire]) suppresses correctly — the behavioral
 * core of the daily reminder without standing up WorkManager's worker factory. Order of suppression:
 * disabled → quiet hours → pre-consent (fail safe) → already logged → post.
 */
class ReminderWorkerSuppressTest {

    private fun fire(
        prefs: ReminderPrefs,
        nowMinute: Int,
        loggedToday: suspend () -> Boolean,
    ): Boolean {
        var posted = 0
        val didPost = runBlocking {
            CheckInReminderLogic.fire(
                prefs = prefs,
                nowMinuteOfDay = nowMinute,
                loggedToday = loggedToday,
                postReminder = { posted++ },
            )
        }
        // Return value and side effect must agree.
        assertEquals(if (didPost) 1 else 0, posted)
        return didPost
    }

    @Test
    fun loggedDay_suppresses_noNotification() {
        val posted = fire(
            prefs = ReminderPrefs(enabled = true, times = listOf(ReminderPrefs.DEFAULT_TIME)),
            nowMinute = 20 * 60,
            loggedToday = { true }, // today is already logged.
        )
        assertFalse("a day already logged suppresses today's reminder", posted)
    }

    @Test
    fun clearEnabledDay_posts() {
        val posted = fire(
            prefs = ReminderPrefs(enabled = true, times = listOf(ReminderPrefs.DEFAULT_TIME)),
            nowMinute = 20 * 60,
            loggedToday = { false },
        )
        assertTrue("an enabled, unlogged, non-quiet day posts", posted)
    }

    @Test
    fun disabled_neverPosts() {
        val posted = fire(
            prefs = ReminderPrefs(enabled = false, times = listOf(ReminderPrefs.DEFAULT_TIME)),
            nowMinute = 20 * 60,
            loggedToday = { false },
        )
        assertFalse(posted)
    }

    @Test
    fun quietHours_suppresses() {
        val posted = fire(
            prefs = ReminderPrefs(
                enabled = true,
                times = listOf(23 * 60),
                quietHoursEnabled = true,
                quietStart = 22 * 60,
                quietEnd = 7 * 60,
            ),
            nowMinute = 23 * 60, // inside the 22:00–07:00 quiet window.
            loggedToday = { false },
        )
        assertFalse("a fire inside quiet hours is suppressed", posted)
    }

    @Test
    fun preConsent_failsSafe_noNotification() {
        val posted = fire(
            prefs = ReminderPrefs(enabled = true, times = listOf(ReminderPrefs.DEFAULT_TIME)),
            nowMinute = 20 * 60,
            loggedToday = { throw IllegalStateException("Repository accessed before consent (ONBD-01).") },
        )
        assertFalse("pre-consent repository access fails safe — never a notification", posted)
    }
}
