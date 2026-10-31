package com.endoapp.app.ui.insights

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The CALM-FLARE no-alarm-visual guard (22-UI-SPEC §Calm Flare / threat T-22-01e) — proves the Insights
 * render path (`com.endoapp.app.ui.insights`) is calm BY CONSTRUCTION: the flare card uses the IDENTICAL
 * kind-agnostic [InsightCard] as every other kind, with NO alarm color, NO alarm glyph, and NO badge.
 *
 * A comment-stripped source scan (mirrors `NotificationSeparationTest.stripComments`, so a KDoc naming a
 * token never false-trips) asserts NO source in the package references:
 *   • an alarm/destructive COLOR — the Bloom `roseDeep` destructive token or a raw red `Color(...)`
 *     literal (`Color.Red`, the `roseDeep` hex `0xFFDC4F73`, or a pure-red `0xFFFF0000`),
 *   • an alarm GLYPH — a `warning` / `exclamation` / `priorityHigh` / `errorOutline` icon, the `⚠`/`❗`
 *     glyphs, or a quoted `"!"` exclamation string,
 *   • a `badge` construct.
 *
 * RED-CAPABLE: adding a `roseDeep` fill or a warning glyph to the insights UI turns this red. Non-vacuous:
 * the insights UI package must exist with the card + screen sources (a rename can't make it vacuously green).
 */
class FlareNoAlarmVisualTest {

    private companion object {
        /** Alarm/destructive COLOR tokens (comment-stripped, case-insensitive). */
        val FORBIDDEN_COLOR_TOKENS = listOf(
            "rosedeep",       // the Bloom destructive token — never used in the calm insights surface
            "color.red",      // a raw Compose red
            "0xffdc4f73",     // the roseDeep hex, even if spelled out
            "0xffff0000",     // a pure-red literal
        )

        /** Alarm GLYPH tokens (comment-stripped, case-insensitive). The quoted `"!"` catches an
         *  exclamation used as a string glyph WITHOUT tripping the `!`/`!=` boolean operators. */
        val FORBIDDEN_GLYPH_TOKENS = listOf(
            "warning",
            "exclamation",
            "priorityhigh",
            "erroroutline",
            "⚠",         // ⚠
            "❗",         // ❗
            "\"!\"",          // a literal "!" string
        )

        /** A visual BADGE construct. */
        const val BADGE_TOKEN = "badge"
    }

    private fun insightsUiSources(): List<File> {
        val base = "src/main/kotlin/com/endoapp/app/ui/insights"
        val dir = listOf(File(base), File("android/app/$base"), File(System.getProperty("user.dir"), base))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate the insights UI package sources (cwd=${System.getProperty("user.dir")})")
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun insightsUi_usesNoAlarmColorToken() {
        for (file in insightsUiSources()) {
            val code = stripComments(file.readText()).lowercase()
            val hit = FORBIDDEN_COLOR_TOKENS.firstOrNull { code.contains(it) }
            assertTrue(
                "Insights UI source ${file.name} must use NO alarm/destructive color — found \"$hit\". The " +
                    "flare card is calm by construction (same neutral card fill as a trend card; T-22-01e).",
                hit == null,
            )
        }
    }

    @Test
    fun insightsUi_usesNoAlarmGlyphOrBadge() {
        for (file in insightsUiSources()) {
            val code = stripComments(file.readText()).lowercase()
            val glyph = FORBIDDEN_GLYPH_TOKENS.firstOrNull { code.contains(it.lowercase()) }
            assertTrue(
                "Insights UI source ${file.name} must use NO alarm glyph (warning/exclamation/⚠) — found " +
                    "\"$glyph\". The only sanctioned \"warning\" word is the section header, in content copy.",
                glyph == null,
            )
            assertTrue(
                "Insights UI source ${file.name} must render NO badge — the flare card carries no alarm badge.",
                !code.contains(BADGE_TOKEN),
            )
        }
    }

    @Test
    fun scanIsNonVacuous_insightsUiPackageExists() {
        val sources = insightsUiSources()
        assertTrue("the insights UI package must exist with multiple sources", sources.size >= 3)
        assertTrue("the kind-agnostic card must be scanned", sources.any { it.name == "InsightCard.kt" })
        assertTrue("the dashboard screen must be scanned", sources.any { it.name == "InsightsScreen.kt" })
    }

    /** Strip block comments (incl. KDoc) and line comments so only real code is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }
}
