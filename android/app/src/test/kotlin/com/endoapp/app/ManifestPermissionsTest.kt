package com.endoapp.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM guard for the NO-NETWORK / NO-HEALTH-WRITE manifest INVARIANT (ASHL-04, threat T-19-02).
 *
 * The Data-safety form's strongest artifact: the `:app` manifest must declare NEITHER
 * `android.permission.INTERNET` NOR any health WRITE permission (this app never talks to the
 * network and never writes health data back — read-only Health Connect arrives Phase 21).
 *
 * COMMENT-SAFE BY CONSTRUCTION: the source `AndroidManifest.xml` contains an explanatory XML
 * comment that literally names "INTERNET" and "WRITE_*". A raw text grep would false-positive on
 * that prose. This test instead parses the XML with [DocumentBuilderFactory] and inspects only the
 * `android:name` attribute of each `<uses-permission>` ELEMENT — comments never carry attributes,
 * so they can never trip it. Mirrors the [com.endoapp.app.data.BackupRulesTest] file-walk +
 * DOM-parse pattern.
 *
 * The forbidden permission names are kept as literals in THIS test source — safe, since the scan
 * targets the manifest file (a different file), not the test's own source (the
 * `VersionCatalogAuditTests` precedent — no self-invalidation trap).
 *
 * DETERMINISTIC: always asserts against the committed source manifest (present on a clean
 * checkout). If a merged manifest has been built (`build/intermediates/**/AndroidManifest.xml`),
 * it is ALSO asserted — a stronger transitive check that catches a permission injected by a
 * dependency — but its absence never fails the test (assumption-guarded).
 *
 * Red-capable: adding `<uses-permission android:name="android.permission.INTERNET" />` (or any
 * health WRITE permission) to the manifest turns this red. Verified in this plan by an
 * inject-and-revert (documented in the SUMMARY).
 */
class ManifestPermissionsTest {

    private companion object {
        /** The network permission that must NEVER appear — the no-network anchor. */
        const val INTERNET_PERMISSION = "android.permission.INTERNET"

        /**
         * The exact-alarm permission that must NEVER appear (Phase 20-05 / threat T-20-02b): the daily
         * reminder uses WorkManager INEXACT scheduling only. Play restricts `SCHEDULE_EXACT_ALARM` to
         * alarm-clock apps; a health tracker requesting it invites review friction.
         */
        const val SCHEDULE_EXACT_ALARM_PERMISSION = "android.permission.SCHEDULE_EXACT_ALARM"

        /**
         * Health Connect write permissions all live under this prefix
         * (e.g. `android.permission.health.WRITE_HEART_RATE`). No name may start with it.
         */
        const val HEALTH_WRITE_PREFIX = "android.permission.health.WRITE_"

        /** The ONE runtime permission the app declares (Phase 20-05, the daily check-in reminder). */
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        /**
         * WorkManager (androidx.work) transitively merges these BENIGN permissions into the MERGED
         * manifest. None is a network data path, an exact alarm, or a health write, so all pass the
         * deny-list. RECEIVE_BOOT_COMPLETED is exactly how WorkManager reschedules the reminder across
         * reboot (we declare no receiver of our own). ACCESS_NETWORK_STATE is NOT INTERNET.
         */
        val WORKMANAGER_TRANSITIVE = setOf(
            "android.permission.WAKE_LOCK",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
        )

        /** The exact set of permissions the SOURCE manifest may declare (allow-list). */
        val SOURCE_ALLOWED = setOf(POST_NOTIFICATIONS_PERMISSION)

        /** The source manifest, relative to any ancestor working dir. */
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
     * A built merged manifest, if one exists (assumption-guarded transitive check). Located next to
     * the resolved source manifest under the module's `build/intermediates`.
     */
    private fun resolveMergedManifest(source: File): File? {
        // source = <module>/src/main/AndroidManifest.xml -> module = parentFile x3
        val moduleDir = source.parentFile?.parentFile?.parentFile ?: return null
        val intermediates = File(moduleDir, "build/intermediates")
        if (!intermediates.isDirectory) return null
        return intermediates.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { it.path.contains("merged_manifest") || it.path.contains("merged-manifest") }
            .firstOrNull { it.path.contains("debug", ignoreCase = true) }
            ?: intermediates.walkTopDown()
                .filter { it.isFile && it.name == "AndroidManifest.xml" }
                .firstOrNull {
                    it.path.contains("merged_manifest") || it.path.contains("merged-manifest")
                }
    }

    /**
     * The set of every `<uses-permission>` element's `android:name` attribute value.
     * Reads ELEMENT ATTRIBUTES only — XML comments (which name INTERNET/WRITE_* in prose) are
     * structurally invisible here, so they can never false-trip the assertions.
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
            // Non-namespace-aware parse: the attribute is literally "android:name".
            val name = el.getAttribute("android:name")
            if (name.isNotEmpty()) names += name
        }
        return names
    }

    private fun assertNoForbiddenPermissions(manifest: File, label: String) {
        val permissions = declaredPermissions(manifest)

        assertTrue(
            "The $label manifest must declare NO '$INTERNET_PERMISSION' — the app has no network " +
                "path (ASHL-04 / T-19-02). Declared permissions were: $permissions",
            !permissions.contains(INTERNET_PERMISSION),
        )

        assertTrue(
            "The $label manifest must declare NO '$SCHEDULE_EXACT_ALARM_PERMISSION' — the daily " +
                "reminder uses WorkManager INEXACT scheduling only (T-20-02b). Declared permissions " +
                "were: $permissions",
            !permissions.contains(SCHEDULE_EXACT_ALARM_PERMISSION),
        )

        val healthWrites = permissions.filter { it.startsWith(HEALTH_WRITE_PREFIX) }
        assertTrue(
            "The $label manifest must declare NO health WRITE permission (prefix " +
                "'$HEALTH_WRITE_PREFIX') — the app never writes health data back (ASHL-04). " +
                "Offending permissions were: $healthWrites",
            healthWrites.isEmpty(),
        )
    }

    @Test
    fun sourceManifestDeclaresNoInternetAndNoHealthWrite() {
        val source = resolveSourceManifest()
        assertNotNull(
            "app/src/main/AndroidManifest.xml not found — the no-network manifest posture must be " +
                "committed and auditable (ASHL-04).",
            source,
        )
        assertNoForbiddenPermissions(source!!, "source")
    }

    @Test
    fun sourceManifestDeclaresExactlyPostNotifications() {
        val source = resolveSourceManifest()
        assertNotNull("source AndroidManifest.xml not found.", source)
        val declared = declaredPermissions(source!!)
        // ALLOW-LIST: the source declares exactly {POST_NOTIFICATIONS} — no more, no less. Removing it
        // (or adding any other source permission, e.g. an exact alarm) turns this red. WorkManager's
        // transitive permissions live only in the MERGED manifest, so they never appear here.
        assertTrue(
            "The source manifest must declare EXACTLY {$POST_NOTIFICATIONS_PERMISSION} (Phase 20-05). " +
                "Declared permissions were: $declared",
            declared == SOURCE_ALLOWED,
        )
    }

    @Test
    fun mergedManifestAllowsPostNotifications_andOnlyBenignTransitives_ifBuilt() {
        val source = resolveSourceManifest() ?: return
        val merged = resolveMergedManifest(source)
        org.junit.Assume.assumeTrue(
            "No merged manifest built yet — skipping the transitive allow-check.",
            merged != null,
        )
        val declared = declaredPermissions(merged!!)

        // POST_NOTIFICATIONS survives the merge (the reminder can post).
        assertTrue(
            "The merged manifest must allow '$POST_NOTIFICATIONS_PERMISSION'. Declared: $declared",
            declared.contains(POST_NOTIFICATIONS_PERMISSION),
        )

        // Every merged permission is either ours (POST_NOTIFICATIONS), a KNOWN benign WorkManager
        // transitive, or the app-scoped AGP dynamic-receiver permission. An UNEXPECTED injected
        // permission (e.g. a dependency pulling in INTERNET) turns this red.
        val allowedSuperset = SOURCE_ALLOWED + WORKMANAGER_TRANSITIVE
        val unexpected = declared.filterNot { perm ->
            perm in allowedSuperset ||
                perm.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        }
        assertTrue(
            "The merged manifest declares an UNEXPECTED permission (not POST_NOTIFICATIONS, a benign " +
                "WorkManager transitive, or the app dynamic-receiver permission): $unexpected",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun mergedManifestDeclaresNoInternetAndNoHealthWrite_ifBuilt() {
        val source = resolveSourceManifest() ?: return
        val merged = resolveMergedManifest(source)
        // Assumption-guarded: on a clean checkout no merged manifest exists yet — that is fine,
        // the source-manifest assertion above is the deterministic guard. When a build HAS run,
        // this catches a permission a dependency might inject transitively.
        org.junit.Assume.assumeTrue(
            "No merged manifest built yet — skipping the transitive check (source guard covers it).",
            merged != null,
        )
        assertNoForbiddenPermissions(merged!!, "merged")
    }
}
