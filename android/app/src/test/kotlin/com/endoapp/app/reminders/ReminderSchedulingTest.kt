package com.endoapp.app.reminders

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.endoapp.app.content.ReminderCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.ui.settings.RemindersSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Proves the daily check-in reminder schedules via WorkManager **INEXACT periodic** work (never an
 * exact alarm), reschedules idempotently, creates EXACTLY ONE notification channel, AND that the
 * Settings section requests `POST_NOTIFICATIONS` **just-in-time** (only in the enable handler, never at
 * launch), handles denial gracefully, and schedules on grant — on the JVM Robolectric + `work-testing`
 * lane (threats T-20-02b/-02c/-02e).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val time2000 = ReminderPrefs.DEFAULT_TIME // 1200 = 20:00
    private val enabledPrefs = ReminderPrefs(enabled = true, times = listOf(time2000))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // WorkManager's default androidx.startup initializer is DISABLED in the manifest (so no
        // androidx.work init code leaks outside the reminders package). Initialize the test WorkManager
        // here — the work-testing lane's driver-backed instance (no double-init conflict).
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        dsScope.cancel()
    }

    private fun scheduler() = ReminderScheduler(workManager, now = { LocalTime.of(10, 0) })

    private fun buildController(): ReminderController {
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            context.preferencesDataStoreFile("reminders_test_${System.nanoTime()}")
        }
        return ReminderController(
            settings = ReminderSettings(dataStore),
            scheduler = scheduler(),
            notifier = ReminderNotifier(context),
        )
    }

    /** A recording permission-request fake — the injected JIT seam. */
    private class FakeRequester(var grant: Boolean) {
        var invocations = 0
        val requester: ((Boolean) -> Unit) -> Unit = { onResult ->
            invocations++
            onResult(grant)
        }
    }

    // --- Scheduling (Task 1) ---

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

    // --- Settings section JIT permission + denial (Task 2) ---

    @Test
    fun section_requestsPermission_justInTime_notAtLaunch() {
        val fake = FakeRequester(grant = true)
        val controller = buildController()
        composeRule.setContent {
            BloomTheme {
                RemindersSection(
                    controller = controller,
                    requestNotificationPermission = fake.requester,
                    openAppNotificationSettings = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("no POST_NOTIFICATIONS request at launch/onStart", 0, fake.invocations)

        composeRule.onNodeWithTag("reminders.toggle").performClick()
        composeRule.waitForIdle()
        assertEquals("enabling requests POST_NOTIFICATIONS just-in-time", 1, fake.invocations)
    }

    @Test
    fun section_grant_seedsDefaultTimeAndSchedules() {
        val fake = FakeRequester(grant = true)
        val controller = buildController()
        composeRule.setContent {
            BloomTheme {
                RemindersSection(
                    controller = controller,
                    requestNotificationPermission = fake.requester,
                    openAppNotificationSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag("reminders.toggle").performClick()

        // Grant seeds the 20:00 default + schedules the inexact periodic work.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            workManager.getWorkInfosForUniqueWork(ReminderScheduler.uniqueName(time2000)).get()
                .any { it.state == WorkInfo.State.ENQUEUED }
        }
        composeRule.onNodeWithText(ReminderCopy.REMINDERS_ON_FOOTER).assertExists()
    }

    @Test
    fun section_denial_showsCalmCopyAndOpenSettings_toggleStaysOff() {
        val fake = FakeRequester(grant = false)
        val controller = buildController()
        var openedSettings = 0
        composeRule.setContent {
            BloomTheme {
                RemindersSection(
                    controller = controller,
                    requestNotificationPermission = fake.requester,
                    openAppNotificationSettings = { openedSettings++ },
                )
            }
        }
        composeRule.onNodeWithTag("reminders.toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ReminderCopy.DENIED).assertExists()
        composeRule.onNodeWithTag("reminders.openSettings").assertExists()
        composeRule.onNodeWithTag("reminders.toggle").assertIsOff()

        composeRule.onNodeWithTag("reminders.openSettings").performClick()
        assertEquals("Open Settings deep-links to the OS notification settings", 1, openedSettings)
    }

    @Test
    fun remindersSection_hasNoLaunchTimeSideEffect() {
        // Structural JIT proof: the section has NO LaunchedEffect/onStart, so the permission request can
        // only fire from a user toggle. Comment-stripped so a doc-comment naming it never trips.
        val src = remindersSectionSource()
        val code = stripComments(src.readText())
        assertFalse(
            "RemindersSection must have NO LaunchedEffect (no launch-time permission request).",
            code.contains("LaunchedEffect"),
        )
        assertTrue(
            "RemindersSection must invoke the JIT permission requester (in the enable handler).",
            code.contains("requestNotificationPermission"),
        )
    }

    // --- source-scan helpers ---

    private fun remindersSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/reminders"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate reminders package (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun remindersSectionSource(): File {
        val rel = "src/main/kotlin/com/endoapp/app/ui/settings/RemindersSection.kt"
        return listOf(File(rel), File("android/app/$rel"), File(System.getProperty("user.dir"), rel))
            .firstOrNull { it.isFile }
            ?: error("Could not locate RemindersSection.kt (cwd=${System.getProperty("user.dir")})")
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
