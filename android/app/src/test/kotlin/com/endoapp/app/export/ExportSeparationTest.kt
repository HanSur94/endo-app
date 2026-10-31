package com.endoapp.app.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The export-package SEPARATION guard (Phase 23 / CONTEXT §"foreground coroutines only" / threat
 * T-23-09) — proves the encrypted export/import package (`com.endoapp.app.export`) adds NO
 * notification / alarm / WorkManager surface. Export and import are plain FOREGROUND coroutines (the
 * off-main KDF runs via `withContext(Dispatchers.Default)`, driven by a `Working` spinner state); the
 * reminders package stays the app's SOLE notification owner.
 *
 * The focused complement of [com.endoapp.app.reminders.NotificationSeparationTest] (whole-app scan) and
 * [com.endoapp.app.health.HealthSeparationTest] (health package) — this one pins the export package
 * specifically and red-capably: a future edit that reaches for `androidx.work` / a `Notification` / an
 * `AlarmManager` / `POST_NOTIFICATIONS` to drive an export turns THIS red on its own.
 *
 * Two comment-safe scans (mirroring the reminders/health precedent):
 *  - IMPORT-line scan for the forbidden API packages/types (imports carry no prose — a KDoc naming an
 *    API never false-trips).
 *  - COMMENT-STRIPPED code scan for the literal `POST_NOTIFICATIONS` token.
 */
class ExportSeparationTest {

    private companion object {
        /** Forbidden API tokens in an IMPORT line (case-insensitive) anywhere in the export package. */
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

    /** Every `.kt` under `com/endoapp/app/export` (main sources) — the package this guard pins. */
    private fun exportSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/export"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate the export package sources (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun exportPackage_importsNoNotificationOrAlarmOrWorkManagerApi() {
        for (file in exportSources()) {
            val importLines = file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.lowercase() }
            for (line in importLines) {
                val hit = FORBIDDEN_IMPORT_TOKENS.firstOrNull { line.contains(it) }
                assertTrue(
                    "Export source ${file.name} must import no notification/alarm/WorkManager API — " +
                        "found \"$hit\" in \"$line\". Export/import is a foreground coroutine; the " +
                        "reminders package is the ONLY notification surface (T-23-09).",
                    hit == null,
                )
            }
        }
    }

    @Test
    fun exportPackage_referencesNoPostNotifications() {
        for (file in exportSources()) {
            val code = stripComments(file.readText()).lowercase()
            assertTrue(
                "Export source ${file.name} must not reference POST_NOTIFICATIONS — the JIT permission " +
                    "request lives only in the reminders RemindersSection.",
                !code.contains(POST_NOTIFICATIONS_TOKEN),
            )
        }
    }

    @Test
    fun scanIsNonVacuous_exportPackageExists() {
        // Guard against a silently-empty scan: the export package must actually exist and carry the real
        // stack (the codec, the crypto, the SAF seam, the orchestration ViewModel) — otherwise a rename
        // could make this guard vacuously green.
        val sources = exportSources()
        assertTrue("the export package must exist with multiple sources", sources.size >= 5)
        assertTrue(
            "the orchestration ViewModel must be scanned",
            sources.any { it.name == "ExportImportViewModel.kt" },
        )
        assertTrue(
            "the container codec must be scanned",
            sources.any { it.name == "ExportCodec.kt" },
        )
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
