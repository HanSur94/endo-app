package com.endoapp.app.designsystem

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM guard for the Material-You ANTI-FEATURE (threat T-19-02a, ASHL-02).
 *
 * Bloom tokens are brand- and chart-semantic — wallpaper recoloring would silently repaint them.
 * This test walks up to the committed `designsystem/` MAIN-source directory (the same walk-up
 * precedent as `BackupRulesTest`) and asserts no production `.kt` file CALLS
 * `dynamicLightColorScheme` / `dynamicDarkColorScheme`. Following the `VersionCatalogAuditTests`
 * precedent, comments are stripped before the scan so the theme source may legitimately DOCUMENT
 * the anti-feature by name — only a real call (in code) trips the guard. It scans the main-source
 * design-system files only, never this test's own source, so there is no self-invalidation trap.
 *
 * Red-capable: adding a `dynamicLightColorScheme()` call to any designsystem `.kt` turns this red.
 */
class NoDynamicColorScanTest {

    private companion object {
        val FORBIDDEN_DYNAMIC_COLOR_CALLS = listOf(
            "dynamicLightColorScheme",
            "dynamicDarkColorScheme",
        )
    }

    /**
     * Strip Kotlin block comments (slash-star … star-slash, including KDoc) and line comments
     * (double-slash to end of line) so only actual code is scanned. Deliberately simple — the
     * design-system sources contain no string literals holding comment delimiters, so a full
     * lexer is unnecessary here.
     */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("(?s)/\\*.*?\\*/"), " ")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    /** Walk up from the test working dir until an ancestor holds the committed main designsystem dir. */
    private fun resolveDesignSystemDir(): File? {
        val relativeCandidates = listOf(
            "src/main/kotlin/com/endoapp/app/designsystem",
            "app/src/main/kotlin/com/endoapp/app/designsystem",
            "android/app/src/main/kotlin/com/endoapp/app/designsystem",
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
    fun noDynamicColorSchemeCallInDesignSystem() {
        val dir = resolveDesignSystemDir()
        assertNotNull(
            "Main-source designsystem/ directory not found — the Bloom theme source must be " +
                "committed under com/endoapp/app/designsystem (ASHL-02).",
            dir,
        )

        val sources = dir!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "No .kt source found under $dir — the scan would be vacuously green.",
            sources.isNotEmpty(),
        )

        for (source in sources) {
            val text = stripComments(source.readText())
            for (call in FORBIDDEN_DYNAMIC_COLOR_CALLS) {
                assertTrue(
                    "Material You dynamic color is a locked anti-feature: '$call' found in " +
                        "${source.name} — Bloom tokens must never be wallpaper-recolored (T-19-02a).",
                    !text.contains(call),
                )
            }
        }
    }
}
