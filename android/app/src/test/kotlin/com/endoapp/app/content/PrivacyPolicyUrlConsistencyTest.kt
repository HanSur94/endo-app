package com.endoapp.app.content

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the SINGLE-SOURCE privacy-policy URL invariant (AREL-05, threat T-25-04).
 *
 * Play requires the SAME privacy-policy URL across Console, the in-app Health Connect rationale
 * surface, and the hosted website; a divergent or malformed URL is a rejection trigger. This test
 * asserts two things:
 *
 *   1. FORMAT — [PrivacyPolicyText.PRIVACY_POLICY_URL] is non-blank, has no leading/trailing
 *      whitespace, and is a well-formed `https://` URL (Play requires a public, non-PDF, https page —
 *      VERIFIED: support.google.com store-listing / User Data policy). NOTE: the value is a committed
 *      PLACEHOLDER (`replace-me` host) the user replaces before submission — this test guards its
 *      SHAPE, never its (not-yet-real) hosted value.
 *
 *   2. SINGLE SOURCE — walking the `content` MAIN source directory, the ONLY quoted `https://` string
 *      literal that appears in ANY Kotlin file is exactly [PrivacyPolicyText.PRIVACY_POLICY_URL]. If a
 *      second privacy-policy URL literal is ever hard-coded elsewhere (e.g. in `StoreListingCopy.kt`),
 *      this turns RED — the URL cannot silently diverge from its single source.
 *
 * The scan reads OTHER source FILES for quoted https literals (bare URLs in `//` prose comments are
 * ignored — they are not quoted string literals), never this test's own source, so it never
 * self-invalidates (the [com.endoapp.insights.VersionCatalogAuditTests] precedent).
 */
class PrivacyPolicyUrlConsistencyTest {

    private companion object {
        /** Matches a double-quoted https URL string LITERAL (comment prose is unquoted, so ignored). */
        val QUOTED_HTTPS_LITERAL = Regex(""""(https://[^"\s]+)"""")

        val CONTENT_DIR_CANDIDATES = listOf(
            "src/main/kotlin/com/endoapp/app/content",
            "app/src/main/kotlin/com/endoapp/app/content",
            "android/app/src/main/kotlin/com/endoapp/app/content",
        )
    }

    /** Walk up from the test working dir until an ancestor holds the content MAIN source directory. */
    private fun resolveContentDir(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in CONTENT_DIR_CANDIDATES) {
                val candidate = File(dir, rel)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun privacyPolicyUrl_isWellFormedHttps() {
        val url = PrivacyPolicyText.PRIVACY_POLICY_URL
        assertTrue("PRIVACY_POLICY_URL must be non-blank.", url.isNotBlank())
        assertTrue(
            "PRIVACY_POLICY_URL must have no leading/trailing whitespace. Was: '$url'",
            url == url.trim(),
        )
        assertTrue(
            "PRIVACY_POLICY_URL must be a well-formed https URL (Play requires a public, non-PDF https " +
                "page — support.google.com store-listing / User Data). Was: '$url'",
            url.startsWith("https://") && url.length > "https://".length,
        )
        assertTrue(
            "PRIVACY_POLICY_URL must not be a PDF (Play rejects PDF privacy policies). Was: '$url'",
            !url.endsWith(".pdf", ignoreCase = true),
        )
    }

    @Test
    fun privacyPolicyUrl_isTheSingleSourceInContentPackage() {
        val contentDir = resolveContentDir()
        assertNotNull(
            "content main source dir not found — the single-source privacy-policy URL guard needs it " +
                "(T-25-04).",
            contentDir,
        )
        val single = PrivacyPolicyText.PRIVACY_POLICY_URL

        // Collect every distinct quoted https literal declared across the content package sources, and
        // WHERE each one lives (for a helpful red message).
        val literalsByFile = mutableMapOf<String, MutableSet<String>>()
        contentDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                QUOTED_HTTPS_LITERAL.findAll(file.readText()).forEach { m ->
                    literalsByFile.getOrPut(file.name) { mutableSetOf() } += m.groupValues[1]
                }
            }
        val distinct = literalsByFile.values.flatten().toSet()

        assertTrue(
            "The content package must declare EXACTLY ONE quoted https URL literal — the single-source " +
                "PRIVACY_POLICY_URL ('$single'). A second hard-coded URL would let the privacy-policy " +
                "URL silently diverge (T-25-04). Found: $literalsByFile",
            distinct == setOf(single),
        )
    }
}
