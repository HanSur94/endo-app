package com.endoapp.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JVM guard for the backup-exclusion PRIVACY INVARIANT (APER-04, threat T-18-03a).
 *
 * Parses `res/xml/data_extraction_rules.xml` and asserts the Room health database
 * ([EndoDatabase.DB_NAME]) is in the `<exclude domain="database">` set of BOTH `<cloud-backup>`
 * and `<device-transfer>` — so Android can never silently copy the health DB to a
 * Google-account-tied backup or a device-to-device transfer. Also asserts the DataStore
 * preferences file is NOT excluded (consent flag / prefs may back up; they carry no health rows).
 *
 * Red-capable: deleting the `endo.db` cloud-backup or device-transfer `<exclude>` turns this red.
 *
 * `EndoDatabase.DB_NAME` is a compile-time `const val`, so it is inlined here and this pure-JVM
 * test never loads the Android-dependent `EndoDatabase` class at runtime.
 */
class BackupRulesTest {

    private val dbName = EndoDatabase.DB_NAME // "endo.db" (inlined const)

    /** A DataStore prefs file MUST stay backup-eligible — it must never appear in an exclude set. */
    private val prefsFileHint = "consent" // DataStore prefs live under a "*.preferences_pb" file

    /** Walk up from the test working dir until an ancestor holds the committed rules XML. */
    private fun resolveRules(): File? {
        val relativeCandidates = listOf(
            "src/main/res/xml/data_extraction_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
            "android/app/src/main/res/xml/data_extraction_rules.xml",
        )
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relativeCandidates) {
                val candidate = File(dir, rel)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    /** All `path` values of `<exclude domain="database">` under the given section tag. */
    private fun excludedDatabasePaths(rules: File, sectionTag: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(rules)
        val sections = doc.getElementsByTagName(sectionTag)
        val paths = mutableSetOf<String>()
        for (s in 0 until sections.length) {
            val section = sections.item(s) as Element
            val excludes = section.getElementsByTagName("exclude")
            for (e in 0 until excludes.length) {
                val ex = excludes.item(e) as Element
                if (ex.getAttribute("domain") == "database") {
                    paths += ex.getAttribute("path")
                }
            }
        }
        return paths
    }

    @Test
    fun healthDatabaseIsExcludedFromCloudBackupAndDeviceTransfer() {
        val rules = resolveRules()
        assertNotNull(
            "res/xml/data_extraction_rules.xml not found — the backup-exclusion rules must be " +
                "committed and referenced from the manifest (APER-04).",
            rules,
        )

        val cloud = excludedDatabasePaths(rules!!, "cloud-backup")
        val transfer = excludedDatabasePaths(rules, "device-transfer")

        assertTrue(
            "Room health DB '$dbName' must be excluded from cloud-backup; excluded db paths were $cloud.",
            cloud.contains(dbName),
        )
        assertTrue(
            "Room health DB '$dbName' must be excluded from device-transfer; excluded db paths were $transfer.",
            transfer.contains(dbName),
        )

        // WAL/SHM sidecars can hold un-checkpointed health rows — they must be excluded too.
        for (sidecar in listOf("$dbName-wal", "$dbName-shm")) {
            assertTrue(
                "Room WAL/SHM sidecar '$sidecar' must be excluded from cloud-backup; was $cloud.",
                cloud.contains(sidecar),
            )
            assertTrue(
                "Room WAL/SHM sidecar '$sidecar' must be excluded from device-transfer; was $transfer.",
                transfer.contains(sidecar),
            )
        }
    }

    @Test
    fun consentPrefsAreNotExcluded() {
        val rules = resolveRules()!!
        val allExcluded = excludedDatabasePaths(rules, "cloud-backup") +
            excludedDatabasePaths(rules, "device-transfer")

        // The consent flag / reminder prefs (DataStore) MAY back up — nothing consent-related
        // should ever appear in an exclude set, and only `database`-domain paths are excluded here.
        val leaked = allExcluded.filter { it.contains(prefsFileHint, ignoreCase = true) }
        assertTrue(
            "The consent/prefs DataStore file must remain backup-eligible, but these excluded " +
                "paths reference it: $leaked",
            leaked.isEmpty(),
        )
        // Only the health DB family may be excluded — assert every excluded db path is DB-derived.
        val unexpected = allExcluded.filterNot { it == dbName || it.startsWith("$dbName-") }
        assertFalse(
            "Only the Room health DB (+ WAL/SHM) may be excluded; unexpected excluded paths: $unexpected",
            unexpected.isNotEmpty(),
        )
    }
}
