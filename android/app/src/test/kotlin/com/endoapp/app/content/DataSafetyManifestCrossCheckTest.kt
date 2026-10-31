package com.endoapp.app.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Binds the committed all-"No" [DataSafetyDeclaration] to the codebase so the Play Data-safety form
 * can NEVER silently contradict what the app actually does (AREL-03, threat T-25-01).
 *
 * Play runs an automated form-vs-reality diff on the Data safety form. This test IS that diff, run in
 * CI: given the declaration claims NO data collected AND NO data shared, it asserts the three
 * corroborating codebase facts that make the all-"No" answer TRUE:
 *   (a) the source `AndroidManifest.xml` declares NO `android.permission.INTERNET` — nothing can
 *       transmit off-device (the strongest single artifact);
 *   (b) `gradle/libs.versions.toml` carries NO analytics / auth / backend / networking coordinate —
 *       nothing can phone home;
 *   (c) the manifest declares NO `android.permission.health.WRITE_*` — the app never writes health
 *       data back (read-only Health Connect posture, Phase 21);
 * and that BOTH data-type lists are empty (consistent with all-"No").
 *
 * CONDITIONAL BINDING (red-capable both directions):
 *   - If a future `android.permission.INTERNET` (or a health WRITE, or an analytics coordinate) is
 *     added, the corroboration assertions inside the all-"No" branch go RED.
 *   - If someone flips `collectsData`/`sharesData` to `true` WITHOUT the app actually gaining a
 *     collection path, control falls into the `else` branch and [fail]s — the declaration would then
 *     contradict the codebase (all-"No" is the corroborated truth for a local-only app).
 *
 * REUSE, not re-implementation:
 *   - The manifest facts come from a [DocumentBuilderFactory] (isNamespaceAware=false)
 *     `<uses-permission>` `android:name` parse — the exact comment-safe DOM approach of
 *     [com.endoapp.app.ManifestPermissionsTest] (XML comments carry no attributes, so the manifest's
 *     explanatory prose that names INTERNET/WRITE_* can never false-trip it).
 *   - The catalog fact comes from the comment-stripped (`substringBefore("#")`) forbidden-coordinate
 *     substring scan of [com.endoapp.insights.VersionCatalogAuditTests].
 * Both scans target OTHER files (the manifest, the `.toml`), never this test's own source — so naming
 * the forbidden literals here is self-consistent (no self-invalidation trap).
 */
class DataSafetyManifestCrossCheckTest {

    private companion object {
        const val INTERNET_PERMISSION = "android.permission.INTERNET"
        const val HEALTH_WRITE_PREFIX = "android.permission.health.WRITE_"

        /** Same forbidden-coordinate policy as [com.endoapp.insights.VersionCatalogAuditTests]. */
        val FORBIDDEN_COORDINATE_SUBSTRINGS = listOf(
            // analytics / telemetry
            "analytics", "firebase", "crashlytics", "amplitude", "mixpanel", "segment",
            // auth / identity
            "oauth", "sign-in", "credential-manager", "auth",
            // networking / backend clients
            "retrofit", "okhttp", "ktor-client", "grpc", "apollo",
        )

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

    /** Walk up from the test working dir until an ancestor holds `gradle/libs.versions.toml`. */
    private fun resolveCatalog(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle/libs.versions.toml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    /**
     * The set of every `<uses-permission>` element's `android:name` — ELEMENT ATTRIBUTES only, so the
     * manifest's explanatory comments (which name INTERNET/WRITE_* in prose) are structurally invisible.
     */
    private fun declaredPermissions(manifest: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
        val nodes = doc.getElementsByTagName("uses-permission")
        val names = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val name = (nodes.item(i) as Element).getAttribute("android:name")
            if (name.isNotEmpty()) names += name
        }
        return names
    }

    @Test
    fun dataSafetyAllNo_isCorroboratedByManifestAndCatalog() {
        // --- Codebase facts (computed independently of the declaration) ---
        val manifest = resolveSourceManifest()
        assertNotNull(
            "app/src/main/AndroidManifest.xml not found — the no-network posture that corroborates " +
                "the all-\"No\" Data-safety declaration must be committed and auditable (T-25-01).",
            manifest,
        )
        val permissions = declaredPermissions(manifest!!)
        val hasInternet = permissions.contains(INTERNET_PERMISSION)
        val healthWrites = permissions.filter { it.startsWith(HEALTH_WRITE_PREFIX) }

        val catalog = resolveCatalog()
        assertNotNull(
            "gradle/libs.versions.toml not found — the no-analytics/backend catalog corroborates the " +
                "all-\"No\" declaration (T-25-01).",
            catalog,
        )
        val catalogCodeOnly = catalog!!.readLines()
            .joinToString("\n") { it.substringBefore("#") }
            .lowercase()
        val forbiddenCoordinates =
            FORBIDDEN_COORDINATE_SUBSTRINGS.filter { catalogCodeOnly.contains(it) }

        // --- Conditional binding: the declaration DRIVES which facts must hold ---
        if (!DataSafetyDeclaration.collectsData && !DataSafetyDeclaration.sharesData) {
            assertFalse(
                "DataSafetyDeclaration claims NO data collected/shared, but the manifest declares " +
                    "'$INTERNET_PERMISSION' — the form would contradict the code (T-25-01). " +
                    "Declared permissions: $permissions",
                hasInternet,
            )
            assertTrue(
                "DataSafetyDeclaration claims NO data collected/shared, but the version catalog " +
                    "carries analytics/auth/backend coordinate(s) $forbiddenCoordinates — the app " +
                    "could phone home, contradicting the all-\"No\" form (T-25-01).",
                forbiddenCoordinates.isEmpty(),
            )
            assertTrue(
                "DataSafetyDeclaration claims NO data collected/shared, but the manifest declares " +
                    "health WRITE permission(s) $healthWrites — the read-only posture that backs the " +
                    "form would be violated (T-25-01).",
                healthWrites.isEmpty(),
            )
            assertTrue(
                "The all-\"No\" declaration must carry an EMPTY dataTypesCollected list (no data-type " +
                    "to enumerate). Was: ${DataSafetyDeclaration.dataTypesCollected}",
                DataSafetyDeclaration.dataTypesCollected.isEmpty(),
            )
            assertTrue(
                "The all-\"No\" declaration must carry an EMPTY dataTypesShared list. Was: " +
                    "${DataSafetyDeclaration.dataTypesShared}",
                DataSafetyDeclaration.dataTypesShared.isEmpty(),
            )
        } else {
            fail(
                "DataSafetyDeclaration claims data collection/sharing (collectsData=" +
                    "${DataSafetyDeclaration.collectsData}, sharesData=${DataSafetyDeclaration.sharesData}), " +
                    "but this is a local-only app with no INTERNET permission and no analytics/backend " +
                    "coordinate — the declaration contradicts the codebase. The corroborated truth for " +
                    "Endo-App is all-\"No\" (on-device-only access/processing is NOT \"collection\" per " +
                    "Play — support.google.com/10787469).",
            )
        }
    }
}
