package com.endoapp.app.content

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AREL-03 (Phase 25, T-25-02) — the store-listing guards that back the `CopyAudit` substring gate:
 *   1. Play field-length limits: title ≤ 30, short ≤ 80, full ≤ 4000
 *      (VERIFIED: support.google.com/13393723, /9859152).
 *   2. The not-a-medical-device disclaimer sits in the FIRST paragraph of the full description, carrying
 *      the required "does not diagnose, treat, cure, or prevent" wording (VERIFIED: support.google.com/
 *      16679511). Derived via `substringBefore("\n\n")` — this pins the disclaimer to the FIRST
 *      paragraph, not merely "present". Mirrors `DisclaimerTest` / `reportCopy_carriesTheSelfTracked…`.
 *   3. `StoreListingCopy` is registered in [CopyAuditRegistry.sources] — registration IS the enforcement;
 *      an unregistered source escapes the substring scan (the load-bearing copy-audit rule).
 *
 * The disclaimer phrases are asserted PRESENT (positive must-contain), so naming them in this test source
 * is correct and self-consistent (the `DisclaimerTest` precedent) — there is no negative-grep
 * self-invalidation. Pure string assertions: no Robolectric, JUnit4 (as `CopyAuditTest`).
 */
class StoreListingCopyTest {

    @Test
    fun storeListing_fieldLengthsWithinPlayLimits() {
        assertTrue(
            "Title must be ≤ 30 chars (Play limit); is ${StoreListingCopy.title.length}.",
            StoreListingCopy.title.length <= 30,
        )
        assertTrue(
            "Short description must be ≤ 80 chars (Play limit); is ${StoreListingCopy.shortDescription.length}.",
            StoreListingCopy.shortDescription.length <= 80,
        )
        assertTrue(
            "Full description must be ≤ 4000 chars (Play limit); is ${StoreListingCopy.fullDescription.length}.",
            StoreListingCopy.fullDescription.length <= 4000,
        )
    }

    @Test
    fun fullDescription_firstParagraph_carriesTheNotMedicalDeviceDisclaimer() {
        // The FIRST paragraph = everything before the first blank line. It must carry the required
        // non-medical-device wording — pinning the disclaimer FIRST (AREL-03), not merely present.
        val firstParagraph = StoreListingCopy.fullDescription.substringBefore("\n\n").lowercase()
        assertTrue(
            "The not-a-medical-device disclaimer must be in the FIRST paragraph of the full description.",
            firstParagraph.contains("not a medical device"),
        )
        assertTrue(
            "The first paragraph must carry the required 'does not diagnose, treat, cure, or prevent' wording.",
            firstParagraph.contains("does not diagnose, treat, cure, or prevent"),
        )
    }

    @Test
    fun storeListingCopy_isRegisteredInTheAuditedCatalog() {
        // Registration IS the enforcement — an unregistered source escapes the ClaimsPolicy substring gate.
        assertTrue(
            "StoreListingCopy must be registered in CopyAuditRegistry.sources.",
            CopyAuditRegistry.sources.contains(StoreListingCopy),
        )
        assertTrue(
            "The registered catalog must include a StoreListingCopy string (the title).",
            CopyAuditRegistry.allAuditableStrings.contains(StoreListingCopy.title),
        )
    }

    @Test
    fun fullDescriptionBody_isNonEmpty_andEveryParagraphIsNonBlank() {
        assertTrue(
            "The audited full-description body must have at least one paragraph.",
            StoreListingCopy.fullDescriptionBody.isNotEmpty(),
        )
        for (paragraph in StoreListingCopy.fullDescriptionBody) {
            assertTrue("Every full-description body paragraph must be non-blank.", paragraph.isNotBlank())
        }
    }
}
