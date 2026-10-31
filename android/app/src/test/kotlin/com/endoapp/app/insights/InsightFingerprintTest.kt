package com.endoapp.app.insights

import com.endoapp.insights.DaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Pure-JVM proof (no Room, no Android context) that [InsightFingerprint] is the launch-stable cache
 * hit/miss gate the coordinator relies on (D-04 / Pitfall 5):
 *
 *  1. Identical `(snapshots, dismissed)` inputs → the identical string (a cache HIT across cold opens).
 *  2. A same-COUNT edit that changes the latest logged day flips it (the count alone is not enough —
 *     an edit to the most recent day must recompute).
 *  3. A new dismissed key flips it; dismissed-set ORDER never affects it (sorted canonical).
 *  4. The source carries NO `hashCode` — a per-process-randomized hash would force a needless recompute
 *     on every cold start (Pitfall 5), so its absence is pinned structurally.
 */
class InsightFingerprintTest {

    private fun snapshot(epochDay: Long): DaySnapshot =
        DaySnapshot.of(day = LocalDate.ofEpochDay(epochDay))

    @Test
    fun identicalInputs_produceIdenticalString() {
        val a = InsightFingerprint.make(
            snapshots = listOf(snapshot(100), snapshot(200)),
            dismissed = setOf("symptom.pelvicPain", "factor.sleep"),
        )
        // Freshly built equal-but-distinct list + set — must still fingerprint identically.
        val b = InsightFingerprint.make(
            snapshots = listOf(snapshot(100), snapshot(200)),
            dismissed = setOf("factor.sleep", "symptom.pelvicPain"),
        )
        assertEquals("identical inputs (any set order) → identical fingerprint (cache HIT)", a, b)
    }

    @Test
    fun sameCountEdit_changingLatestDay_flipsTheFingerprint() {
        val before = InsightFingerprint.make(
            snapshots = listOf(snapshot(100), snapshot(200)),
            dismissed = emptySet(),
        )
        // Same COUNT (2), but the latest logged day moved 200 → 201 (an edit to the most recent day).
        val after = InsightFingerprint.make(
            snapshots = listOf(snapshot(100), snapshot(201)),
            dismissed = emptySet(),
        )
        assertNotEquals("a same-count edit to the latest day must flip the fingerprint (MISS)", before, after)
    }

    @Test
    fun newDismissedKey_flipsTheFingerprint() {
        val snaps = listOf(snapshot(100), snapshot(200))
        val before = InsightFingerprint.make(snapshots = snaps, dismissed = setOf("a"))
        val after = InsightFingerprint.make(snapshots = snaps, dismissed = setOf("a", "b"))
        assertNotEquals("a newly dismissed key must flip the fingerprint (recompute excludes it)", before, after)
    }

    @Test
    fun emptyInputs_areWellFormed_notACrash() {
        val fp = InsightFingerprint.make(snapshots = emptyList(), dismissed = emptySet())
        // count=0, latestDayEpoch=-1 (the empty sentinel), empty dismissed canonical.
        assertEquals("0|-1|", fp)
    }

    @Test
    fun source_usesNoHashCode_pitfall5() {
        val source = resolveSource()
        // Strip block comments (/** ... */) and // line comments so the prose that legitimately NAMES
        // hashCode (the Pitfall-5 warning) never trips the guard — only real code is inspected.
        val codeOnly = source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { it.substringBefore("//") }
        assertTrue(
            "InsightFingerprint must not use hashCode() — a per-process-randomized hash would force a " +
                "needless recompute on every cold start (Pitfall 5).",
            !codeOnly.contains("hashCode"),
        )
    }

    /** Walk up from the test working directory to the `InsightFingerprint.kt` source and read it. */
    private fun resolveSource(): String {
        val rel = "app/src/main/kotlin/com/endoapp/app/insights/InsightFingerprint.kt"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate.readText()
            // Also handle user.dir already being the :app module dir.
            val local = File(dir, "src/main/kotlin/com/endoapp/app/insights/InsightFingerprint.kt")
            if (local.isFile) return local.readText()
            dir = dir.parentFile
        }
        error("Could not resolve InsightFingerprint.kt source from ${System.getProperty("user.dir")}")
    }
}
