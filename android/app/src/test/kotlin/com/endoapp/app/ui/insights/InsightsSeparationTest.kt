package com.endoapp.app.ui.insights

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The insights-package SEPARATION guard (22-CONTEXT §5 / threat T-22-01) — proves BOTH insights packages
 * add NO notification / alarm / WorkManager surface even after the flare surface lands:
 *   • `com.endoapp.app.insights`     — the coordinator (StateFlow + dismiss),
 *   • `com.endoapp.app.ui.insights`  — the dashboard (screen + card + confidence pill).
 *
 * Flare is IN-APP ONLY: the reminders package stays the app's SOLE notification owner. The complement of
 * the global [com.endoapp.app.reminders.NotificationSeparationTest] and the focused
 * [com.endoapp.app.health.HealthSeparationTest]: this one pins the insights packages specifically and
 * red-capably, so a future edit that reaches for `androidx.work` / a `Notification` / an `AlarmManager` /
 * `POST_NOTIFICATIONS` to drive the flare surface turns this red on its own.
 *
 * Two comment-safe scans (mirroring the reminders/health precedent):
 *  - IMPORT-line scan for the forbidden API packages/types (imports carry no prose — a KDoc naming an
 *    API never false-trips).
 *  - COMMENT-STRIPPED code scan for the literal `POST_NOTIFICATIONS` token.
 */
class InsightsSeparationTest {

    private companion object {
        /** Forbidden API tokens in an IMPORT line (case-insensitive) anywhere in the insights packages. */
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

    /** Every `.kt` under the two insights packages (main sources) — the surface this guard pins. */
    private fun insightsSources(): List<File> {
        val bases = listOf(
            "src/main/kotlin/com/endoapp/app/insights",
            "src/main/kotlin/com/endoapp/app/ui/insights",
        )
        return bases.flatMap { base ->
            val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
                .firstOrNull { it.isDirectory }
                ?: error("Could not locate insights sources at $base (cwd=${System.getProperty("user.dir")})")
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
    }

    @Test
    fun insightsPackages_importNoNotificationOrAlarmOrWorkManagerApi() {
        for (file in insightsSources()) {
            val importLines = file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.lowercase() }
            for (line in importLines) {
                val hit = FORBIDDEN_IMPORT_TOKENS.firstOrNull { line.contains(it) }
                assertTrue(
                    "Insights source ${file.name} must import no notification/alarm/WorkManager API — " +
                        "found \"$hit\" in \"$line\". Flare is in-app only; the reminders package is the " +
                        "ONLY notification surface (T-22-01).",
                    hit == null,
                )
            }
        }
    }

    @Test
    fun insightsPackages_referenceNoPostNotifications() {
        for (file in insightsSources()) {
            val code = stripComments(file.readText()).lowercase()
            assertTrue(
                "Insights source ${file.name} must not reference POST_NOTIFICATIONS — the JIT permission " +
                    "request lives only in the reminders RemindersSection.",
                !code.contains(POST_NOTIFICATIONS_TOKEN),
            )
        }
    }

    @Test
    fun scanIsNonVacuous_bothInsightsPackagesExist() {
        // Guard against a silently-empty scan: both packages must exist with their real sources — the
        // coordinator AND the dashboard — otherwise a rename could make this guard vacuously green.
        val sources = insightsSources()
        assertTrue("the insights packages must exist with multiple sources", sources.size >= 4)
        assertTrue("the coordinator must be scanned", sources.any { it.name == "InsightsCoordinator.kt" })
        assertTrue("the dashboard screen must be scanned", sources.any { it.name == "InsightsScreen.kt" })
        assertTrue("the kind-agnostic card must be scanned", sources.any { it.name == "InsightCard.kt" })
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
