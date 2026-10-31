// Requirement: ONBD-02 — "not medical advice / not a medical device" framing (single source of truth).
// The Kotlin port of the iOS `Sources/EndoApp/Content/Disclaimer.swift`.
//
// `Disclaimer.text` is the ONE place this framing lives. Onboarding surfaces it BEFORE the consent
// action (D-05, GDPR informed consent), rendered by the Bloom `DisclaimerBanner`. Keeping it in a
// single constant makes the claim auditable and impossible to drift across screens.
//
// EXEMPT from the curated `CopyAudit` substring scan (it is deliberately NOT in
// [com.endoapp.app.content.CopyAuditRegistry.sources]): this text legitimately CONTAINS the words
// "diagnose, treat, cure, prevent" — it states what the app does NOT do — and would false-flag the
// forbidden-token gate. Instead it is pinned by its own must-contain test (`DisclaimerTest`), exactly
// as iOS pins it with `DisclaimerTests`.
package com.endoapp.app.content

/**
 * The app's "not medical advice / not a medical device" framing (ONBD-02, D-05) — the single source
 * of truth. Every surface renders THIS text, never a hand-typed copy. MUST contain both
 * "not medical advice" and "not a medical device" (the auditable claim, enforced by `DisclaimerTest`).
 */
object Disclaimer {
    /** Plain-language, warm-but-honest framing — ported verbatim from the iOS `Disclaimer.text`. */
    val text: String =
        "Endo-App is an informational self-tracking tool. It is not medical advice and " +
            "not a medical device. It does not diagnose, treat, cure, or prevent any condition. " +
            "The patterns it surfaces come from your own data and are meant to help you understand " +
            "your body and have better conversations with your clinician — not to replace " +
            "professional care."
}
