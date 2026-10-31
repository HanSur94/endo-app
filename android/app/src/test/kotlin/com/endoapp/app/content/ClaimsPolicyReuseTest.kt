package com.endoapp.app.content

import com.endoapp.insights.ForbiddenTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins threat T-18-11 (RESEARCH Pitfall 2): `:app` [ClaimsPolicy] must REUSE the drift-guarded
 * `:insights` [ForbiddenTerms] single source for the shared causal / imperative / alarmist lists —
 * it must NEVER declare a second, divergent copy. A fork would let the two platforms silently enforce
 * different policies on the first iOS update while both suites stayed green.
 *
 * The referential assertions ([assertSame]) prove the app getters return the EXACT SAME list instances
 * as `:insights` — a re-declared `listOf(...)` in `:app` would be a different instance and fail here.
 * The app-only lists (gendered / precision / allowedFraming) are asserted present and non-empty.
 */
class ClaimsPolicyReuseTest {

    @Test
    fun causalIsReusedFromInsights_notForked() {
        assertSame(
            "ClaimsPolicy.causal must BE the :insights ForbiddenTerms.causal instance (no divergent fork).",
            ForbiddenTerms.causal,
            ClaimsPolicy.causal,
        )
    }

    @Test
    fun imperativeIsReusedFromInsights_notForked() {
        assertSame(
            "ClaimsPolicy.imperative must BE the :insights ForbiddenTerms.imperative instance (no fork).",
            ForbiddenTerms.imperative,
            ClaimsPolicy.imperative,
        )
    }

    @Test
    fun alarmistIsReusedFromInsights_notForked() {
        assertSame(
            "ClaimsPolicy.alarmist must BE the :insights ForbiddenTerms.alarmist instance (no fork).",
            ForbiddenTerms.alarmist,
            ClaimsPolicy.alarmist,
        )
    }

    @Test
    fun sharedThreeContentsMatchTheSingleSource() {
        assertEquals(ForbiddenTerms.causal, ClaimsPolicy.causal)
        assertEquals(ForbiddenTerms.imperative, ClaimsPolicy.imperative)
        assertEquals(ForbiddenTerms.alarmist, ClaimsPolicy.alarmist)
    }

    @Test
    fun appOnlyListsArePresentAndNonEmpty() {
        assertTrue("allowedFraming must be present.", ClaimsPolicy.allowedFraming.isNotEmpty())
        assertTrue("forbiddenGenderedTokens must be present.", ClaimsPolicy.forbiddenGenderedTokens.isNotEmpty())
        assertTrue("forbiddenPrecisionTokens must be present.", ClaimsPolicy.forbiddenPrecisionTokens.isNotEmpty())
    }

    @Test
    fun appOnlyListsMatchTheiOSPortSource() {
        // Verbatim from iOS Sources/EndoApp/Content/ClaimsPolicy.swift (RESEARCH lines 403-417).
        assertEquals(
            listOf(
                "informational",
                "patterns in your own data",
                "possible association",
                "worth discussing with your clinician",
                "not medical advice",
                "not a medical device",
                "self-tracking",
            ),
            ClaimsPolicy.allowedFraming,
        )
        assertEquals(
            listOf("she", "her", "women", "woman", "lady", "ladies"),
            ClaimsPolicy.forbiddenGenderedTokens,
        )
    }
}
