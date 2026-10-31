package com.endoapp.app.report

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The report-package NO-NETWORK guard (Phase 24-04, AREL-02 / threat T-24-01) — the Kotlin port of the
 * iOS `ReportNoNetworkTests`. Proves the report subsystem (`com.endoapp.app.report`, incl. the `report.ui`
 * share host) reaches for NO networking API whatsoever: the doctor-report is rendered + shared entirely
 * on-device, and its ONLY egress is the user-chosen `ACTION_SEND` chooser (no auto-upload, no silent send).
 *
 * This is the structural complement of [ReportManifestTest] (the manifest declares NO INTERNET): even if a
 * permission were somehow present, no report source could USE the network. Mirrors the existing
 * [com.endoapp.app.export.ExportSeparationTest] / [com.endoapp.app.reminders.NotificationSeparationTest]
 * file-walk + comment-strip structural-scan pattern already in the repo.
 *
 * COMMENT-SAFE BY CONSTRUCTION: block comments (KDoc) and line comments are stripped BEFORE the token
 * scan, so an explanatory comment naming a networking API (this file's own docs do) can never false-trip
 * (the Phase-18 grep-vs-parse lesson). The forbidden-token literals live in THIS test source only — a
 * different file (src/test) from the scanned package (src/main) — so the guard can never invalidate
 * itself (the VersionCatalogAudit precedent).
 *
 * ANTI-VACUITY: asserts the scanned `.kt` set is NON-EMPTY and includes the known report files
 * (ReportPdfRenderer, ReportShareHost) — a wrong path can never make the scan pass vacuously.
 *
 * Red-capable: adding a `java.net.URL(...)` / `HttpURLConnection` / `Socket` to ANY report source turns
 * this red.
 */
class ReportNoNetworkTest {

    private companion object {
        /**
         * Forbidden networking tokens (case-insensitive) — none may appear in comment-stripped report
         * code. `URL(` catches a `java.net.URL(...)` construction while never matching `Uri`/`URI`.
         */
        val FORBIDDEN_NETWORK_TOKENS = listOf(
            "java.net",
            "okhttp",
            "httpurlconnection",
            "urlconnection",
            "socket",
            "android.permission.internet",
            "url(",
        )
    }

    /** Every `.kt` under `com/endoapp/app/report` (main sources), incl. the `report/ui` subpackage. */
    private fun reportSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/report"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate the report package sources (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun reportPackage_usesNoNetworkingApi() {
        for (file in reportSources()) {
            val code = stripComments(file.readText()).lowercase()
            val hit = FORBIDDEN_NETWORK_TOKENS.firstOrNull { code.contains(it) }
            assertTrue(
                "Report source ${file.name} must use NO networking API — found \"$hit\" in " +
                    "comment-stripped code. The report is on-device only; its ONLY egress is the " +
                    "user-chosen ACTION_SEND chooser (AREL-02 / T-24-01).",
                hit == null,
            )
        }
    }

    @Test
    fun scanIsNonVacuous_reportPackageExistsWithKnownFiles() {
        // Guard against a silently-empty scan: the report package must actually exist and carry the real
        // render + share stack — otherwise a wrong path could make the no-network guard vacuously green.
        val sources = reportSources()
        assertTrue(
            "the report package must exist with multiple sources (found ${sources.size})",
            sources.size >= 4,
        )
        assertTrue(
            "the renderer must be scanned (the on-device PDF writer)",
            sources.any { it.name == "ReportPdfRenderer.kt" },
        )
        assertTrue(
            "the share host must be scanned (the ONLY egress — must be provably network-free)",
            sources.any { it.name == "ReportShareHost.kt" },
        )
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
