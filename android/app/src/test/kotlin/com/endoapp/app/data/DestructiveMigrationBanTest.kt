package com.endoapp.app.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural ban on Room's destructive-migration fallback in the `data/` MAIN sources.
 *
 * A destructive-migration fallback would DROP AND RECREATE the database on any schema-version bump
 * whose migration is missing — silently wiping every row of the user's health history (threat
 * T-18-01, RESEARCH Pitfall 3 / Anti-Pattern). This test scans the persistence layer's main source
 * files (the database builder and anything under `data/`) and fails if the fallback method name
 * appears anywhere in them.
 *
 * DISCIPLINE: the forbidden method literal exists in this repository ONLY as this test's search
 * string. The scan is scoped to MAIN sources (never the test tree) so it can never match itself and
 * silently self-invalidate. Adding the call to the builder turns this test red (red-capable).
 */
class DestructiveMigrationBanTest {

    // The banned Room API family (all overloads share this prefix). This literal must live nowhere
    // else in the codebase — main sources naming it verbatim would falsely trip the scan.
    private val forbidden = "fallbackToDestructiveMigration"

    /** Walk up from the test working dir to the persistence-layer MAIN source directory. */
    private fun resolveMainDataDir(): File? {
        val relativeCandidates = listOf(
            "src/main/kotlin/com/endoapp/app/data",
            "app/src/main/kotlin/com/endoapp/app/data",
        )
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relativeCandidates) {
                val candidate = File(dir, rel)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun destructiveMigrationFallbackIsNeverCalledInMainSources() {
        val dataDir = resolveMainDataDir()
        assertNotNull(
            "Could not resolve the data/ main source directory to scan for the migration ban.",
            dataDir,
        )

        val offenders = dataDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(forbidden) }
            .map { it.name }
            .toList()

        assertTrue(
            "Destructive-migration fallback ('$forbidden…') found in main sources: $offenders — a " +
                "schema bump would then silently wipe user health history. Provide a real Migration.",
            offenders.isEmpty(),
        )
    }
}
