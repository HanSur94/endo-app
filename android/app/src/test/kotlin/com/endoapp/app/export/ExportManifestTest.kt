package com.endoapp.app.export

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM guard for the ZERO-STORAGE-PERMISSION export posture (APRT-01/03, threat T-23-07).
 *
 * Encrypted export/import writes and reads through the Storage Access Framework — the user picks the
 * destination at the document picker and SAF grants per-document access, so the app declares NO
 * broad storage permission whatsoever: no `READ_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`, no
 * `MANAGE_EXTERNAL_STORAGE`. It also stays network-free (no `INTERNET`) — export is all on-device.
 *
 * COMMENT-SAFE BY CONSTRUCTION: the manifest carries explanatory prose. This test PARSES the XML with
 * [DocumentBuilderFactory] and inspects only the `android:name` attribute of each `<uses-permission>`
 * ELEMENT — comments carry no attributes, so an explanatory comment naming a storage permission can
 * never false-trip the assertion (the Phase-18-06 grep-vs-parse lesson). Mirrors
 * [com.endoapp.app.data.BackupRulesTest] + [com.endoapp.app.ManifestPermissionsTest].
 *
 * Non-vacuity: asserts the manifest resolves, parses, and declares at least one permission (so a
 * silently-empty or unparseable manifest cannot make the deny-list assertion pass vacuously).
 *
 * Red-capable: adding `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />`
 * (or any storage / INTERNET permission) turns this red.
 */
class ExportManifestTest {

    private companion object {
        /** The broad storage permissions SAF makes unnecessary — none may be declared. */
        val FORBIDDEN_STORAGE_PERMISSIONS = setOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        )

        /** The network permission — export/import is entirely on-device. */
        const val INTERNET_PERMISSION = "android.permission.INTERNET"

        val SOURCE_MANIFEST_CANDIDATES = listOf(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "android/app/src/main/AndroidManifest.xml",
        )
    }

    /** Walk up from the test working dir until an ancestor holds the committed source manifest. */
    private fun resolveSourceManifest(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in SOURCE_MANIFEST_CANDIDATES) {
                val candidate = File(dir, rel)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    /**
     * The set of every `<uses-permission>` element's `android:name` attribute value. Reads ELEMENT
     * ATTRIBUTES only — XML comments are structurally invisible here, so prose can never false-trip.
     */
    private fun declaredPermissions(manifest: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
        val nodes = doc.getElementsByTagName("uses-permission")
        val names = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("android:name")
            if (name.isNotEmpty()) names += name
        }
        return names
    }

    @Test
    fun manifestDeclaresNoStoragePermissionAndNoInternet() {
        val manifest = resolveSourceManifest()
        assertNotNull(
            "app/src/main/AndroidManifest.xml not found — the zero-storage-permission SAF posture " +
                "must be committed and auditable (APRT-01/03).",
            manifest,
        )

        val declared = declaredPermissions(manifest!!)

        // Non-vacuity: the manifest parses and declares SOMETHING, so the deny-list below is meaningful.
        assertTrue(
            "The manifest declared NO permissions at all — the parse likely failed or the manifest is " +
                "empty; the zero-storage assertion would be vacuous. Parsed: $declared",
            declared.isNotEmpty(),
        )

        val storageLeaks = declared.intersect(FORBIDDEN_STORAGE_PERMISSIONS)
        assertTrue(
            "Export/import uses the Storage Access Framework (per-document access) — the manifest must " +
                "declare NONE of $FORBIDDEN_STORAGE_PERMISSIONS (APRT-01/03, T-23-07). Offending: " +
                "$storageLeaks. All declared: $declared",
            storageLeaks.isEmpty(),
        )

        assertTrue(
            "Export is on-device — the manifest must declare NO '$INTERNET_PERMISSION'. Declared: " +
                "$declared",
            !declared.contains(INTERNET_PERMISSION),
        )
    }
}
