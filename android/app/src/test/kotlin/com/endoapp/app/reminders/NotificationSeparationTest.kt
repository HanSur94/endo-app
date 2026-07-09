package com.endoapp.app.reminders

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The no-flare-notification SEPARATION guard (CONTEXT §5 / UI-SPEC §4 / threat T-20-02d) — proves the
 * reminders package (`com.endoapp.app.reminders`) is the app's ONLY notification/alarm surface.
 *
 * Scans every `:app` main Kotlin source and asserts that any reference to a notification / alarm /
 * WorkManager / `POST_NOTIFICATIONS` API lives ONLY under `com.endoapp.app.reminders` OR in the single
 * Settings `RemindersSection` that drives it. Every other source — Check-In, Day Log, BodyMap, Settings
 * body, the shell/gate, and any FUTURE flare/insights code (none exists this phase) — must reference
 * none. Introducing a notification/alarm API anywhere else turns this red.
 *
 * This SEEDS the Phase-22 flare structural scan: when flare code lands, it inherits this invariant and
 * cannot grow a notification surface.
 *
 * Two complementary, comment-safe scans:
 *  - IMPORT-line scan for the API packages/types (the CheckInFlowTest precedent — imports carry no
 *    prose, so a doc-comment naming the API never false-trips).
 *  - COMMENT-STRIPPED code scan for the literal `POST_NOTIFICATIONS` token (referenced as
 *    `Manifest.permission.POST_NOTIFICATIONS`, which no import line carries).
 */
class NotificationSeparationTest {

    private companion object {
        /** Forbidden API tokens in an IMPORT line (case-insensitive) outside the reminders surface. */
        val FORBIDDEN_IMPORT_TOKENS = listOf(
            "androidx.work",
            "android.app.notification", // Notification / NotificationManager / NotificationChannel
            "notificationcompat",
            "notificationmanagercompat",
            "alarmmanager",
        )

        /** Forbidden literal in COMMENT-STRIPPED code outside the reminders surface. */
        const val POST_NOTIFICATIONS_TOKEN = "post_notifications"
    }

    /** `true` if [file] is part of the allowlisted reminders surface (the package, or RemindersSection). */
    private fun isReminderSurface(file: File): Boolean {
        val path = file.path.replace('\\', '/')
        return path.contains("/com/endoapp/app/reminders/") || file.name == "RemindersSection.kt"
    }

    private fun mainSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate :app main sources (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun onlyRemindersSurface_importsNotificationOrAlarmApi() {
        for (file in mainSources()) {
            if (isReminderSurface(file)) continue
            val importLines = file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.lowercase() }
            for (line in importLines) {
                val hit = FORBIDDEN_IMPORT_TOKENS.firstOrNull { line.contains(it) }
                assertTrue(
                    "Source ${file.name} (outside com.endoapp.app.reminders) must import no " +
                        "notification/alarm/WorkManager API — found \"$hit\" in \"$line\". The reminder " +
                        "is the ONLY notification surface (T-20-02d).",
                    hit == null,
                )
            }
        }
    }

    @Test
    fun onlyRemindersSurface_referencesPostNotifications() {
        for (file in mainSources()) {
            if (isReminderSurface(file)) continue
            val code = stripComments(file.readText()).lowercase()
            assertTrue(
                "Source ${file.name} (outside com.endoapp.app.reminders) must not reference " +
                    "POST_NOTIFICATIONS — the JIT permission request lives only in RemindersSection.",
                !code.contains(POST_NOTIFICATIONS_TOKEN),
            )
        }
    }

    @Test
    fun scanIsNonVacuous_reminderSurfaceExists() {
        // Guard against a silently-empty scan: the allowlisted reminders surface must actually exist
        // (the package files + RemindersSection) — otherwise a rename could make the guard vacuously green.
        val surface = mainSources().filter { isReminderSurface(it) }
        assertTrue("the reminders surface must exist (package + RemindersSection)", surface.size >= 2)
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
