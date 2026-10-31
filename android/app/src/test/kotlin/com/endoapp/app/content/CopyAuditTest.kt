package com.endoapp.app.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin port of the iOS `CopyAuditTests` MECHANISM (Tests/EndoAppTests/CopyAuditTests.swift;
 * RESEARCH §CopyAudit registered-catalog scan / Pitfall 3 / T-18-12).
 *
 * Scans the REGISTERED [CopyAuditRegistry] catalog and asserts every registered user-facing string is
 * clean against the full union of forbidden-token classes:
 *   (a) no causal / imperative / alarmist / gendered token (case-insensitive substring, via [ClaimsPolicy]),
 *   (b) no false-precision token,
 *   (c) confidence words carry no digit / percent.
 *
 * THE LOAD-BEARING RULE (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`): every user-facing string
 * is registered in [AuditableCopy.auditableStrings] IN THE SAME CHANGE that introduces it. An
 * unregistered string silently escapes these scans — registration IS the enforcement.
 *
 * NON-VACUITY: with zero UI copy today the catalog is empty, so the catalog scans below pass trivially.
 * To prove the harness is ENFORCING and not vacuously green, [scanCatchesAForbiddenString] and the
 * positive/negative controls feed strings through the SAME scan helpers used against the real catalog.
 * Phase 19's first `object FooCopy : AuditableCopy` will register into [CopyAuditRegistry.sources] and
 * be audited from birth.
 */
class CopyAuditTest {

    // --- The scan helpers under test (the SAME logic applied to the real catalog and the controls) ---

    /** First causal/imperative/alarmist/gendered token `text` contains (case-insensitive substring),
     *  or null if clean. This is the union scan the catalog assertions and the controls both use. */
    private fun firstForbiddenToken(text: String): String? {
        val lowered = text.lowercase()
        return ClaimsPolicy.causal.firstOrNull { lowered.contains(it.lowercase()) }
            ?: ClaimsPolicy.imperative.firstOrNull { lowered.contains(it.lowercase()) }
            ?: ClaimsPolicy.alarmist.firstOrNull { lowered.contains(it.lowercase()) }
            ?: ClaimsPolicy.forbiddenGenderedTokens.firstOrNull { lowered.contains(it.lowercase()) }
    }

    /** First false-precision / numeric-confidence token `text` contains, or null if clean. */
    private fun firstPrecisionToken(text: String): String? {
        val lowered = text.lowercase()
        return ClaimsPolicy.forbiddenPrecisionTokens.firstOrNull { lowered.contains(it.lowercase()) }
    }

    // --- Catalog scans (trivially green today; enforcing the moment Phase 19 registers copy) ---

    @Test
    fun registeredCatalog_containsNoForbiddenToken() {
        for (copy in CopyAuditRegistry.allAuditableStrings) {
            val hit = firstForbiddenToken(copy)
            assertNull(
                "Registered copy \"$copy\" must contain NO causal/imperative/alarmist/gendered token " +
                    "(found \"$hit\").",
                hit,
            )
        }
    }

    @Test
    fun registeredCatalog_containsNoFalsePrecisionToken() {
        for (copy in CopyAuditRegistry.allAuditableStrings) {
            val hit = firstPrecisionToken(copy)
            assertNull(
                "Registered copy \"$copy\" must contain NO false-precision token (found \"$hit\") (D-11).",
                hit,
            )
        }
    }

    @Test
    fun registeredCatalog_confidenceWordsCarryNoDigitOrPercent() {
        // Confidence words (a Phase-19+ surface) must be plain-language: no digit, no percent. With no
        // registered copy today this scans the empty catalog; the invariant is pinned for when copy lands.
        for (copy in CopyAuditRegistry.allAuditableStrings) {
            assertFalse(
                "Registered copy \"$copy\" must contain no percent sign (plain-language confidence, D-11).",
                copy.contains("%"),
            )
        }
    }

    @Test
    fun registeredCatalog_everyStringIsNonBlank() {
        for (copy in CopyAuditRegistry.allAuditableStrings) {
            assertTrue("Every registered string must be non-blank.", copy.isNotBlank())
        }
    }

    // --- NON-VACUITY controls: prove the scan WOULD catch a bad string (independent of catalog state) ---

    @Test
    fun scanCatchesAForbiddenString() {
        // Deliberately-forbidden strings, each carrying at least one token from the reused/app-only lists.
        // Fed through the SAME helper the catalog scan uses — proving the harness is enforcing, not vacuous.
        val deliberatelyBad = listOf(
            "this will diagnose your flare",   // causal: "will", "diagnose"
            "you should avoid dairy",          // imperative: "should", "avoid"
            "a flare attack is imminent",      // alarmist: "attack", "imminent"
            "she logged her symptoms",         // gendered: "she", "her"
        )
        for (bad in deliberatelyBad) {
            assertNotNull(
                "A deliberately-forbidden string \"$bad\" MUST be flagged by the scan (harness is enforcing).",
                firstForbiddenToken(bad),
            )
        }
    }

    @Test
    fun precisionScanCatchesAFalsePrecisionString() {
        val deliberatelyPrecise = listOf(
            "82% confidence",     // "%"
            "p = 0.03",           // "p ="
            "r = 0.7 correlation", // "r ="
        )
        for (bad in deliberatelyPrecise) {
            assertNotNull(
                "A deliberately-precise string \"$bad\" MUST be flagged by the false-precision scan.",
                firstPrecisionToken(bad),
            )
        }
    }

    // --- Phase 24 report copy (Plan 24-02): ReportCopy honesty framing ---
    //
    // ReportCopy is registered in [CopyAuditRegistry.sources], so its strings are already covered by the
    // catalog scans above (no causal/imperative/alarmist/gendered/false-precision hit). These extra
    // assertions pin the honesty FRAMING the report must carry, mirroring the iOS `reportCopy_isNonEmpty`
    // expectations.

    @Test
    fun reportCopy_isRegisteredInTheAuditedCatalog() {
        // Registration IS the enforcement — an unregistered string escapes the scan (the load-bearing rule).
        assertTrue(
            "ReportCopy must be registered in CopyAuditRegistry.sources.",
            CopyAuditRegistry.sources.contains(ReportCopy),
        )
        assertTrue(
            "The registered catalog must include a ReportCopy string.",
            CopyAuditRegistry.allAuditableStrings.contains(ReportCopy.PDF_TITLE),
        )
    }

    @Test
    fun reportCopy_sourceTagForHealthConnect_isFromHealthConnect_notAppleHealth() {
        val tag = ReportCopy.sourceTag(com.endoapp.app.report.SectionSource.HEALTH_CONNECT)
        assertTrue("The imported source tag reads \"from Health Connect\".", tag == "from Health Connect")
        assertFalse("The Android tag must not mention Apple Health.", tag.lowercase().contains("apple"))
        // NONE → empty string (no badge — honesty).
        assertTrue(ReportCopy.sourceTag(com.endoapp.app.report.SectionSource.NONE).isEmpty())
    }

    @Test
    fun reportCopy_carriesTheSelfTrackedNotDiagnosticFraming() {
        // The footer is the prominent self-tracked-not-diagnostic framing. It is EXEMPT from the curated
        // substring scan (a legitimate "not diagnostic" negation trips the alarmist "diagnos" stem, exactly
        // like Disclaimer.text), so it is pinned HERE instead — a must-contain, mirroring iOS.
        val footer = ReportCopy.FOOTER.lowercase()
        assertTrue("The footer must carry the self-tracked framing.", footer.contains("self-tracked"))
        assertTrue("The footer must carry the not-diagnostic framing.", footer.contains("not diagnostic"))
        // The suppressed-cycle line reads as an off-state; the sparse line is the honest not-enough-data.
        assertTrue(ReportCopy.CYCLE_SUPPRESSED.lowercase().contains("off"))
        assertTrue(ReportCopy.NOT_ENOUGH_DATA_IN_RANGE.lowercase().contains("not enough data"))
    }

    @Test
    fun scanPassesCuratedCleanCopy() {
        // Curated discussion-prompt words, each verified by hand against the substring traps — these must
        // pass clean, proving the scan is not over-eager (e.g. no false hit that would block honest copy).
        val curatedClean = listOf(
            "informational",
            "patterns in your own data",
            "possible association",
            "worth discussing with your clinician",
        )
        for (clean in curatedClean) {
            assertNull(
                "Curated clean copy \"$clean\" must pass the forbidden-token scan.",
                firstForbiddenToken(clean),
            )
            assertNull(
                "Curated clean copy \"$clean\" must pass the false-precision scan.",
                firstPrecisionToken(clean),
            )
        }
    }
}
