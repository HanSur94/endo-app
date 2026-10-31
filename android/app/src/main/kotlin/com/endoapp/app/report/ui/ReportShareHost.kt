// The report SHARE host (Phase 24-04, AREL-02 / threats T-24-01 / T-24-09 / T-24-10) — the thin Compose
// layer that hands a rendered, on-device PDF to the OS `ACTION_SEND` chooser. It is the SOLE egress for
// the report: the USER picks the target app in the system chooser, so there is NO auto-upload, NO silent
// send, and — critically — NO network path anywhere (the report package imports no networking API, pinned
// by ReportNoNetworkTest; the manifest declares no INTERNET, pinned by ReportManifestTest).
//
// The PDF is vended through a `FileProvider` `content://` URI (authority `${packageName}.fileprovider`)
// with a per-Intent `FLAG_GRANT_READ_URI_PERMISSION` — a scoped, revocable grant to only the chosen app,
// backed by `res/xml/file_paths.xml` which exposes ONLY the app-private `cacheDir/reports/` subdir. After
// the chooser returns, the cache PDF is DELETED so no stale health artifact lingers (T-24-10).
//
// This mirrors the Phase-23 [com.endoapp.app.export.ui.ExportImportHost] ActivityResult pattern (stash a
// request → launch a system Intent → consume/clean on return), but for a SHARE rather than a SAF picker.
//
// FOREGROUND-ONLY / NO NOTIFICATION: this file imports NO androidx.work / notification / alarm API (the
// reminders package stays the app's SOLE notification owner — NotificationSeparationTest) and NO
// networking API (java.net / okhttp / HttpURLConnection / Socket — ReportNoNetworkTest). This `ui/` file
// legitimately imports android/androidx (it IS the share seam), exactly like ExportImportHost.
package com.endoapp.app.report.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** The FileProvider authority suffix — must match the manifest `${applicationId}.fileprovider`. */
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

/** The PDF mime the report is shared as. */
const val REPORT_SHARE_MIME = "application/pdf"

/**
 * Build the scoped share `Intent` for a rendered report [pdfFile] living in `cacheDir/reports/`.
 *
 * Vends the file as a `content://` URI through the [FileProvider] (never a raw `file://` path, which
 * would `FileUriExposedException` on modern Android and over-share), and grants ONLY the chosen target a
 * per-Intent read via `FLAG_GRANT_READ_URI_PERMISSION`. The result is `ACTION_SEND` +
 * [REPORT_SHARE_MIME] + an `EXTRA_STREAM` content URI — no network, no auto-target.
 *
 * Kept as a plain function (off the composable) so a Robolectric test can assert the exact Intent shape
 * without launching the real chooser.
 */
fun buildShareIntent(context: Context, pdfFile: File): Intent {
    val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
    val uri = FileProvider.getUriForFile(context, authority, pdfFile)
    return Intent(Intent.ACTION_SEND).apply {
        type = REPORT_SHARE_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        // Scoped, auto-revoked grant to ONLY the app the user picks in the chooser (T-24-09).
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * Wrap [shareIntent] in the OS chooser so the USER picks the destination app — the app never chooses a
 * target and never uploads. The chooser is the ONLY egress (T-24-01).
 */
fun buildShareChooser(shareIntent: Intent): Intent =
    Intent.createChooser(shareIntent, /* title */ null)

/**
 * Delete the report cache PDF(s) so no stale health artifact lingers after the share (T-24-10). Removes
 * every `*.pdf` under [reportsDir] (the `cacheDir/reports/` subdir). Idempotent and quiet: a missing dir
 * is a no-op. Returns the number of files deleted (so a test can pin the cleanup).
 */
fun cleanupReports(reportsDir: File): Int {
    if (!reportsDir.isDirectory) return 0
    var deleted = 0
    reportsDir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", ignoreCase = true) }
        ?.forEach { if (it.delete()) deleted++ }
    return deleted
}

/**
 * Hosts the report share: launches the `ACTION_SEND` chooser for [pdfFile], and DELETES the cache PDF
 * when the chooser returns (the user has either shared or dismissed) before signalling [onShared].
 *
 * Mirrors [com.endoapp.app.export.ui.ExportImportHost]: an [ActivityResultLauncher] whose callback is
 * fixed at `remember` time fires when the chooser dismisses, which is the seam used to clean the cache —
 * a stale PDF must never outlive the share (T-24-10). The chooser result itself is ignored (a share
 * target reports no meaningful result); the callback matters only as the "user returned" signal.
 *
 * @param pdfFile the rendered report in `cacheDir/reports/` to share.
 * @param onShared invoked after the chooser returns and the cache is cleaned (pops the share off the UI).
 */
@Composable
fun ReportShareHost(
    pdfFile: File,
    onShared: () -> Unit = {},
) {
    val context = LocalContext.current

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The chooser dismissed (shared or cancelled) — delete the cache PDF(s), then notify.
        cleanupReports(pdfFile.parentFile ?: File(context.cacheDir, "reports"))
        onShared()
    }

    // Launch the chooser once for this file (keyed on the file's path so re-share of a new file re-fires).
    LaunchedEffect(pdfFile.path) {
        shareLauncher.launch(buildShareChooser(buildShareIntent(context, pdfFile)))
    }
}
