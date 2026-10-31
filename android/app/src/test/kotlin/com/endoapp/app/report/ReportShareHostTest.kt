package com.endoapp.app.report

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.report.ui.REPORT_SHARE_MIME
import com.endoapp.app.report.ui.buildShareChooser
import com.endoapp.app.report.ui.buildShareIntent
import com.endoapp.app.report.ui.cleanupReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pins the report SHARE seam (Phase 24-04, AREL-02 / threats T-24-01 / T-24-09 / T-24-10) on the
 * Robolectric JVM lane — the share is USER-chosen, scoped, and cache-cleaned, with NO auto-upload.
 *
 * Off the [com.endoapp.app.report.ui.buildShareIntent] / [buildShareChooser] / [cleanupReports] seams
 * (so the real system chooser is not needed), it proves:
 *  - the share Intent is `ACTION_SEND` + `application/pdf` + a `content://` `EXTRA_STREAM` URI (vended by
 *    the FileProvider, never a raw `file://`) + `FLAG_GRANT_READ_URI_PERMISSION` (scoped, revocable);
 *  - the chooser wraps that send Intent (`ACTION_CHOOSER` → `EXTRA_INTENT`) — the USER picks the target,
 *    the app never auto-sends;
 *  - `cleanupReports` removes a seeded cache PDF, so no stale health artifact lingers (T-24-10).
 *
 * The real OS chooser + the actual no-silent-upload UX is device-gated (surfaced to /gsd-verify-work);
 * the structural no-network posture is pinned by [ReportNoNetworkTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportShareHostTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Seed a fake rendered PDF in the app-private `cacheDir/reports/` — the exact path the renderer uses. */
    private fun seedReportPdf(name: String = "endo-report-20250101-20250131.pdf"): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        return File(dir, name).apply { writeBytes("%PDF-1.4\n".toByteArray()) }
    }

    /**
     * The share intent shape + chooser wrapping are asserted TOGETHER in one method because
     * [FileProvider] caches its parsed `PathStrategy` statically per authority, while Robolectric gives
     * each test method a fresh (different) `cacheDir` — so a second per-method `getUriForFile` call would
     * resolve against the first method's cached root. One `getUriForFile` per class run sidesteps that
     * Robolectric-only quirk; production `cacheDir` is stable so the real app is unaffected.
     */
    @Test
    fun shareIntentIsScopedActionSendPdf_wrappedInAUserChooser() {
        val pdf = seedReportPdf()
        val intent = buildShareIntent(context, pdf)

        assertEquals(
            "The report must be shared via ACTION_SEND (the user-chosen system share sheet, no upload).",
            Intent.ACTION_SEND, intent.action,
        )
        assertEquals(
            "The share mime must be application/pdf.",
            REPORT_SHARE_MIME, intent.type,
        )

        val stream = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertNotNull("The share Intent must carry an EXTRA_STREAM URI.", stream)
        assertEquals(
            "The shared URI must be a FileProvider content:// URI (scoped, revocable) — never a raw " +
                "file:// path. Was: $stream",
            "content", stream!!.scheme,
        )
        assertEquals(
            "The content URI authority must be the report FileProvider (\${applicationId}.fileprovider).",
            context.packageName + ".fileprovider", stream.authority,
        )

        assertTrue(
            "The share Intent must set FLAG_GRANT_READ_URI_PERMISSION so ONLY the chosen target gets a " +
                "scoped, auto-revoked read grant (T-24-09). Flags were: ${intent.flags}",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )

        // The share goes through the OS chooser — the USER picks the target, the app never auto-sends.
        val chooser = buildShareChooser(intent)
        assertEquals(
            "The share must go through the OS chooser (ACTION_CHOOSER) — the USER picks the target app; " +
                "the app never auto-sends or uploads (T-24-01).",
            Intent.ACTION_CHOOSER, chooser.action,
        )
        val wrapped = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("The chooser must wrap the ACTION_SEND intent.", wrapped)
        assertEquals(Intent.ACTION_SEND, wrapped!!.action)
        assertEquals(REPORT_SHARE_MIME, wrapped.type)
    }

    @Test
    fun cleanupReports_deletesSeededCachePdf() {
        val pdf = seedReportPdf("endo-report-20240601-20240630.pdf")
        val dir = pdf.parentFile!!
        assertTrue("precondition: the seeded PDF exists", pdf.exists())

        val deleted = cleanupReports(dir)

        assertTrue("cleanupReports must delete at least the seeded PDF. Deleted=$deleted", deleted >= 1)
        assertFalse(
            "The cache PDF must NOT linger after the chooser returns (T-24-10). Still present: $pdf",
            pdf.exists(),
        )
    }

    @Test
    fun cleanupReports_missingDirIsNoOp() {
        val absent = File(context.cacheDir, "reports-does-not-exist")
        assertEquals("A missing reports dir must be a quiet no-op (0 deleted).", 0, cleanupReports(absent))
    }
}
