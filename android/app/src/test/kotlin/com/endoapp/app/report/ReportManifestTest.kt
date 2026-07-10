package com.endoapp.app.report

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM guard for the report SHARE posture (Phase 24-04, AREL-02 / threats T-24-01 / T-24-09 / T-24-10).
 *
 * The doctor-report PDF leaves the device ONLY through the user-chosen `ACTION_SEND` chooser, fed a
 * scoped `content://` URI by a `FileProvider`. This test pins the manifest + `res/xml/file_paths.xml`
 * infrastructure that makes that egress safe:
 *  - a `<provider>` for `androidx.core.content.FileProvider` that is `exported="false"` (not callable by
 *    other apps) and `grantUriPermissions="true"` (per-Intent scoped grant);
 *  - `file_paths.xml` vends ONLY a `<cache-path>` (app-private cache), never external storage (T-24-09);
 *  - the manifest STILL declares NO `android.permission.INTERNET` — adding the provider adds no network
 *    path (the no-silent-upload anchor, T-24-01), the complement of [com.endoapp.app.ManifestPermissionsTest].
 *
 * COMMENT-SAFE BY CONSTRUCTION: the manifest carries explanatory prose that names "INTERNET" and
 * "FileProvider". This test PARSES the XML with [DocumentBuilderFactory] and inspects only ELEMENT
 * attributes — comments carry no attributes, so prose can never false-trip (the Phase-18 grep-vs-parse
 * lesson). Mirrors [com.endoapp.app.ManifestPermissionsTest] / [com.endoapp.app.export.ExportManifestTest].
 *
 * NON-VACUOUS: asserts the provider element was actually found BEFORE asserting its attributes, and that
 * `file_paths.xml` resolves + parses — so a missing provider or wrong path can never pass silently.
 *
 * Red-capable: removing the `<provider>`, flipping `exported` to `true`, dropping the `<cache-path>`, or
 * adding `<uses-permission android:name="android.permission.INTERNET" />` turns this red.
 */
class ReportManifestTest {

    private companion object {
        const val FILE_PROVIDER_NAME = "androidx.core.content.FileProvider"
        const val INTERNET_PERMISSION = "android.permission.INTERNET"

        val SOURCE_MANIFEST_CANDIDATES = listOf(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "android/app/src/main/AndroidManifest.xml",
        )
        val FILE_PATHS_CANDIDATES = listOf(
            "src/main/res/xml/file_paths.xml",
            "app/src/main/res/xml/file_paths.xml",
            "android/app/src/main/res/xml/file_paths.xml",
        )
    }

    /** Walk up from the test working dir until an ancestor holds one of [relatives]. */
    private fun resolveUpwards(relatives: List<String>): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relatives) {
                val candidate = File(dir, rel)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun parse(file: File) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)

    /** The `<provider>` element (if any) whose `android:name` is the [FILE_PROVIDER_NAME]. */
    private fun fileProviderElement(manifest: File): Element? {
        val nodes = parse(manifest).getElementsByTagName("provider")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("android:name") == FILE_PROVIDER_NAME) return el
        }
        return null
    }

    private fun declaredPermissions(manifest: File): Set<String> {
        val nodes = parse(manifest).getElementsByTagName("uses-permission")
        val names = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val name = (nodes.item(i) as Element).getAttribute("android:name")
            if (name.isNotEmpty()) names += name
        }
        return names
    }

    @Test
    fun manifestDeclaresScopedFileProvider() {
        val manifest = resolveUpwards(SOURCE_MANIFEST_CANDIDATES)
        assertNotNull(
            "app/src/main/AndroidManifest.xml not found — the FileProvider share posture must be " +
                "committed and auditable (AREL-02).",
            manifest,
        )

        // NON-VACUITY: the provider element must actually exist before its attributes are meaningful.
        val provider = fileProviderElement(manifest!!)
        assertNotNull(
            "The manifest must declare a <provider android:name=\"$FILE_PROVIDER_NAME\"> so the report " +
                "PDF can be vended as a scoped content:// URI to the ACTION_SEND chooser (AREL-02).",
            provider,
        )

        assertTrue(
            "The FileProvider <provider> must be android:exported=\"false\" — it is NOT callable by " +
                "other apps; access is granted per-share via FLAG_GRANT_READ_URI_PERMISSION (T-24-09). " +
                "android:exported was \"${provider!!.getAttribute("android:exported")}\".",
            provider.getAttribute("android:exported") == "false",
        )

        assertTrue(
            "The FileProvider <provider> must be android:grantUriPermissions=\"true\" so the sender's " +
                "per-Intent read grant is honored (T-24-09). android:grantUriPermissions was " +
                "\"${provider.getAttribute("android:grantUriPermissions")}\".",
            provider.getAttribute("android:grantUriPermissions") == "true",
        )
    }

    @Test
    fun filePathsVendsOnlyAppPrivateCache() {
        val filePaths = resolveUpwards(FILE_PATHS_CANDIDATES)
        assertNotNull(
            "res/xml/file_paths.xml not found — the FileProvider must declare which paths it vends " +
                "(AREL-02).",
            filePaths,
        )

        val doc = parse(filePaths!!)

        // The vended path must be a <cache-path> (app-private cache), so a granted URI can never reach
        // external storage or another app-private file (T-24-09).
        val cachePaths = doc.getElementsByTagName("cache-path")
        assertTrue(
            "file_paths.xml must declare a <cache-path> (app-private cacheDir) — the report PDF lives " +
                "in cacheDir/reports/ (T-24-09/T-24-10). Found ${cachePaths.length} <cache-path>.",
            cachePaths.length >= 1,
        )

        // And it must vend NO external / broad path — an <external-path> / <external-cache-path> /
        // <files-path> would over-share beyond the app-private cache.
        for (forbidden in listOf("external-path", "external-cache-path", "external-files-path", "files-path")) {
            val nodes = doc.getElementsByTagName(forbidden)
            assertTrue(
                "file_paths.xml must NOT declare a <$forbidden> — the report FileProvider vends ONLY " +
                    "the app-private cache reports subdir (T-24-09). Found ${nodes.length}.",
                nodes.length == 0,
            )
        }
    }

    @Test
    fun manifestStillDeclaresNoInternet() {
        val manifest = resolveUpwards(SOURCE_MANIFEST_CANDIDATES)
        assertNotNull("source AndroidManifest.xml not found.", manifest)
        val declared = declaredPermissions(manifest!!)

        // NON-VACUITY: the manifest parses and declares SOMETHING (health READs + POST_NOTIFICATIONS),
        // so the deny-list below is meaningful, not a silently-empty parse.
        assertTrue(
            "The manifest declared NO permissions at all — the parse likely failed; the no-INTERNET " +
                "assertion would be vacuous. Parsed: $declared",
            declared.isNotEmpty(),
        )

        assertTrue(
            "Adding the FileProvider must NOT add a network path — the manifest must STILL declare NO " +
                "'$INTERNET_PERMISSION' (the no-silent-upload anchor, T-24-01). Declared: $declared",
            !declared.contains(INTERNET_PERMISSION),
        )
    }
}
