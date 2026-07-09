package com.endoapp.app.reminders

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Proves the daily check-in reminder schedules via WorkManager **INEXACT periodic** work (never an
 * exact alarm), reschedules idempotently, and creates EXACTLY ONE notification channel — on the JVM
 * Robolectric + `work-testing` lane (threats T-20-02b/-02c).
 *
 * (Plan-05 Task 2 extends this file with the Settings section's just-in-time `POST_NOTIFICATIONS`
 * behavior.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulingTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    private val time2000 = ReminderPrefs.DEFAULT_TIME // 1200 = 20:00
    private val enabledPrefs = ReminderPrefs(enabled = true, times = listOf(time2000))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // WorkManager's default androidx.startup initializer is DISABLED in the manifest (so no
        // androidx.work init code leaks outside the reminders package). Initialize the test WorkManager
        // here — the work-testing lane's synchronous, driver-backed instance (no double-init conflict).
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun scheduler() = ReminderScheduler(workManager, now = { LocalTime.of(10, 0) })

    @Test
    fun enabling_schedulesInexactPeriodicUniqueWork() {
        scheduler().reschedule(enabledPrefs)

        val infos = workManager
            .getWorkInfosForUniqueWork(ReminderScheduler.uniqueName(time2000))
            .get()
        assertEquals("exactly one reminder work is enqueued for the configured time", 1, infos.size)

        val info = infos.single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue("carries the reminder cancellation tag", info.tags.contains(ReminderScheduler.WORK_TAG))

        // PERIODIC (inexact) — not a one-time/exact fire. WorkInfo exposes the periodicity of periodic work.
        val periodicity = info.periodicityInfo
        assertNotNull("reminder work must be PERIODIC (inexact), not one-time", periodicity)
        assertEquals(
            "24h repeat interval",
            TimeUnit.HOURS.toMillis(24),
            periodicity!!.repeatIntervalMillis,
        )
    }

    @Test
    fun reschedule_isIdempotent_noDuplication() {
        val s = scheduler()
        s.reschedule(enabledPrefs)
        s.reschedule(enabledPrefs)
        s.reschedule(enabledPrefs)

        val infos = workManager
            .getWorkInfosForUniqueWork(ReminderScheduler.uniqueName(time2000))
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals("re-registering never duplicates the unique periodic work", 1, infos.size)
    }

    @Test
    fun disabling_leavesNoActiveReminderWork() {
        val s = scheduler()
        s.reschedule(enabledPrefs)
        s.reschedule(enabledPrefs.copy(enabled = false))

        val active = workManager.getWorkInfosByTag(ReminderScheduler.WORK_TAG).get()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        assertTrue("no active reminder work remains after disabling", active.isEmpty())
    }

    @Test
    fun notifier_createsExactlyOneChannel_idempotently() {
        val notifier = ReminderNotifier(context)
        notifier.ensureChannel()
        notifier.ensureChannel() // idempotent — must not create a second channel.

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals("exactly one notification channel exists", 1, manager.notificationChannels.size)
        assertEquals(ReminderNotifier.CHANNEL_ID, manager.notificationChannels.single().id)
    }

    @Test
    fun remindersPackage_usesNoExactAlarmApi() {
        // COMMENT-STRIPPED source scan (the VersionCatalogAuditTests precedent): a doc-comment may name
        // the anti-pattern in prose (e.g. "never setExact*"), but only a REAL call would trip this.
        val forbidden = listOf("setexact", "alarmmanager", "schedule_exact_alarm", "setalarmclock")
        for (file in remindersSources()) {
            val code = stripComments(file.readText()).lowercase()
            val hit = forbidden.firstOrNull { code.contains(it) }
            assertTrue(
                "Reminders source ${file.name} must use no exact-alarm API (found \"$hit\").",
                hit == null,
            )
        }
    }

    private fun remindersSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/reminders"
        val candidates = listOf(
            File(base),
            File("android/app/$base"),
            File(System.getProperty("user.dir"), "$base"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate reminders package (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
