package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.ForbiddenTerms
import com.endoapp.insights.l10n.PolicyLanguage
import com.endoapp.insights.l10n.forLanguage
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins threat T-26-14: `:app` [ClaimsPolicy.forLanguage] must REUSE the `:insights`
 * [ForbiddenTerms.forLanguage] instance for every DE/FR/ES policy. It must NEVER load or declare a second,
 * divergent copy. A fork would let the app and the engine enforce different policies while both suites
 * stayed green.
 *
 * The referential assertions ([assertSame]) prove the app returns the EXACT SAME cached instance as
 * `:insights`. A second decode in `:app` would be a different instance and fail here. The English
 * [ClaimsPolicyReuseTest][com.endoapp.app.content.ClaimsPolicyReuseTest] stays untouched (SAFE-04).
 */
class ClaimsPolicyLocalizedReuseTest {

    @Test
    fun everyLanguageIsReusedFromInsights_notForked() {
        assertTrue("PolicyLanguage must list DE, FR and ES.", PolicyLanguage.entries.size == 3)
        for (lang in PolicyLanguage.entries) {
            assertSame(
                "ClaimsPolicy.forLanguage($lang) must BE the :insights ForbiddenTerms.forLanguage($lang) " +
                    "instance (no divergent fork).",
                ForbiddenTerms.forLanguage(lang),
                ClaimsPolicy.forLanguage(lang),
            )
        }
    }

    @Test
    fun germanIsReusedFromInsights_notForked() {
        assertSame(ForbiddenTerms.forLanguage(PolicyLanguage.DE), ClaimsPolicy.forLanguage(PolicyLanguage.DE))
    }

    @Test
    fun frenchIsReusedFromInsights_notForked() {
        assertSame(ForbiddenTerms.forLanguage(PolicyLanguage.FR), ClaimsPolicy.forLanguage(PolicyLanguage.FR))
    }

    @Test
    fun spanishIsReusedFromInsights_notForked() {
        assertSame(ForbiddenTerms.forLanguage(PolicyLanguage.ES), ClaimsPolicy.forLanguage(PolicyLanguage.ES))
    }
}
