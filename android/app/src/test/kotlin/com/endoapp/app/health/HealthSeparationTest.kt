package com.endoapp.app.health

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The health-package SEPARATION guard (Phase 21 / CONTEXT §5 / threat T-21-05c) — proves the Health
 * Connect import package (`com.endoapp.app.health`, incl. its `ui` subpackage) adds NO
 * notification / alarm / WorkManager surface. The reminders package stays the app's SOLE notification
 * owner; import is a plain FOREGROUND coroutine (no WorkManager, no background read).
 *
 * The complement of [com.endoapp.app.reminders.NotificationSeparationTest]: that scan asserts the whole
 * app OUTSIDE reminders is clean; this one pins the health package specifically and red-capably, so a
 * future edit that reaches for `androidx.work` / a `Notification` / an `AlarmManager` /
 * `POST_NOTIFICATIONS` to drive the import turns this red on its own.
 *
 * Two comment-safe scans (mirroring the reminders precedent):
 *  - IMPORT-line scan for the forbidden API packages/types (imports carry no prose — a KDoc naming an
 *    API never false-trips).
 *  - COMMENT-STRIPPED code scan for the literal `POST_NOTIFICATIONS` token.
 */
class HealthSeparationTest {

    private companion object {
        /** Forbidden API tokens in an IMPORT line (case-insensitive) anywhere in the health package. */
        val FORBIDDEN_IMPORT_TOKENS = listOf(
            "androidx.work",
            "android.app.notification", // Notification / NotificationManager / NotificationChannel
            "notificationcompat",
            "notificationmanagercompat",
            "alarmmanager",
        )

        /** Forbidden literal in COMMENT-STRIPPED code (referenced as `Manifest.permission.POST_NOTIFICATIONS`). */
        const val POST_NOTIFICATIONS_TOKEN = "post_notifications"
    }

    /** Every `.kt` under `com/endoapp/app/health` (main sources) — the package this guard pins. */
    private fun healthSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/health"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate the health package sources (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun healthPackage_importsNoNotificationOrAlarmOrWorkManagerApi() {
        for (file in healthSources()) {
            val importLines = file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.lowercase() }
            for (line in importLines) {
                val hit = FORBIDDEN_IMPORT_TOKENS.firstOrNull { line.contains(it) }
                assertTrue(
                    "Health source ${file.name} must import no notification/alarm/WorkManager API — " +
                        "found \"$hit\" in \"$line\". Import is a foreground coroutine; the reminders " +
                        "package is the ONLY notification surface (T-21-05c).",
                    hit == null,
                )
            }
        }
    }

    @Test
    fun healthPackage_referencesNoPostNotifications() {
        for (file in healthSources()) {
            val code = stripComments(file.readText()).lowercase()
            assertTrue(
                "Health source ${file.name} must not reference POST_NOTIFICATIONS — the JIT permission " +
                    "request lives only in the reminders RemindersSection.",
                !code.contains(POST_NOTIFICATIONS_TOKEN),
            )
        }
    }

    @Test
    fun scanIsNonVacuous_healthPackageExists() {
        // Guard against a silently-empty scan: the health package must actually exist and carry the
        // real import stack (the controller, the reader, the coordinator, the connect UI) — otherwise a
        // rename could make this guard vacuously green.
        val sources = healthSources()
        assertTrue("the health package must exist with multiple sources", sources.size >= 4)
        assertTrue(
            "the concrete controller must be scanned",
            sources.any { it.name == "HealthConnectController.kt" },
        )
        assertTrue(
            "the connect/import UI host must be scanned",
            sources.any { it.name == "HealthImportHost.kt" },
        )
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
