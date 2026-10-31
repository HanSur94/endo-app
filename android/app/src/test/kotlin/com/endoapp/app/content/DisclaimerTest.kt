package com.endoapp.app.content

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the single-source [Disclaimer.text] framing — the Kotlin mirror of the iOS `DisclaimerTests`.
 *
 * [Disclaimer.text] is DELIBERATELY EXEMPT from the curated `CopyAudit` forbidden-substring scan
 * (it legitimately contains "diagnose/treat/cure/prevent" while stating what the app does NOT do),
 * so its correctness is guarded HERE with must-contain assertions instead: the auditable ONBD-02
 * claim is that the text carries both "not medical advice" AND "not a medical device".
 */
class DisclaimerTest {

    @Test
    fun disclaimer_containsNotMedicalAdvice() {
        assertTrue(
            "Disclaimer.text must contain \"not medical advice\" (ONBD-02 auditable claim).",
            Disclaimer.text.contains("not medical advice", ignoreCase = true),
        )
    }

    @Test
    fun disclaimer_containsNotAMedicalDevice() {
        assertTrue(
            "Disclaimer.text must contain \"not a medical device\" (ONBD-02 auditable claim).",
            Disclaimer.text.contains("not a medical device", ignoreCase = true),
        )
    }

    @Test
    fun disclaimer_isNonBlank() {
        assertTrue("Disclaimer.text must be non-blank.", Disclaimer.text.isNotBlank())
    }
}
