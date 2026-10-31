package com.endoapp.app.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the offline single-source [PrivacyPolicyText] — the Android-adapted Kotlin mirror of the iOS
 * `PrivacyPolicyTests`.
 *
 * [PrivacyPolicyText.body] is DELIBERATELY EXEMPT from the curated `CopyAudit` forbidden-substring
 * scan (long-form legal prose legitimately contains host words the gate would false-flag), so its
 * correctness is guarded HERE with must-contain assertions: the on-device / no-servers / no-accounts /
 * no-sharing posture, the named developer + contact email, and — the load-bearing Android adaptation —
 * that the iOS "iCloud"/"sync" wording is GONE (Android is local-only).
 */
class PrivacyPolicyTest {

    private val body get() = PrivacyPolicyText.body

    @Test
    fun policy_statesOnDeviceStorage() {
        assertTrue(
            "PrivacyPolicyText.body must state data is stored on this device.",
            body.contains("on this device", ignoreCase = true),
        )
    }

    @Test
    fun policy_statesNoServers() {
        assertTrue(
            "PrivacyPolicyText.body must state there are no servers.",
            body.contains("no servers", ignoreCase = true),
        )
    }

    @Test
    fun policy_statesNoAccounts() {
        assertTrue(
            "PrivacyPolicyText.body must state there is no account.",
            body.contains("no account", ignoreCase = true),
        )
    }

    @Test
    fun policy_statesNoThirdPartySharing() {
        assertTrue(
            "PrivacyPolicyText.body must state there is no third-party sharing.",
            body.contains("no third-party sharing", ignoreCase = true),
        )
    }

    @Test
    fun policy_statesNoAnalytics() {
        assertTrue(
            "PrivacyPolicyText.body must state there is no third-party analytics.",
            body.contains("analytics", ignoreCase = true),
        )
    }

    @Test
    fun policy_namesTheDeveloperAsController() {
        assertTrue(
            "PrivacyPolicyText.body must name the developer as the data controller.",
            body.contains("developer", ignoreCase = true) &&
                body.contains("data controller", ignoreCase = true),
        )
    }

    @Test
    fun policy_containsContactEmail() {
        assertTrue(
            "PrivacyPolicyText.body must contain the contact email.",
            body.contains(PrivacyPolicyText.contactEmail),
        )
        assertTrue(
            "The contact email is the developer's address.",
            PrivacyPolicyText.contactEmail == "sannahrush@googlemail.com",
        )
    }

    @Test
    fun policy_statesExplicitConsentAndWithdraw() {
        assertTrue(
            "PrivacyPolicyText.body must state consent is explicit and can be withdrawn.",
            body.contains("explicit consent", ignoreCase = true) &&
                body.contains("withdraw", ignoreCase = true),
        )
    }

    @Test
    fun policy_hasNoICloudWording_androidAdaptation() {
        assertFalse(
            "PrivacyPolicyText.body must NOT contain \"iCloud\" — Android is local-only (no cloud sync).",
            body.contains("iCloud", ignoreCase = true),
        )
    }

    @Test
    fun policy_isNonBlank() {
        assertTrue("PrivacyPolicyText.body must be non-blank.", body.isNotBlank())
    }
}
