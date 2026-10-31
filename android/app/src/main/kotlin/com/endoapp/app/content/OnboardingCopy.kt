// The registered user-facing copy for the Phase-19 Bloom onboarding + consent flow — the third
// `AuditableCopy` source in `:app` (after `ShellCopy` + `PlaceholderCopy`). Per the LOAD-BEARING RULE
// (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`), these strings are registered into
// [CopyAuditRegistry.sources] in the SAME change that introduces the screen that renders them, so
// `CopyAuditTest` scans them from birth. Every string is transcribed VERBATIM from the 19-UI-SPEC
// §Copywriting curated table (pre-verified against the ClaimsPolicy forbidden substrings).
//
// SINGLE-SOURCE NOTE: the "I consent" / "Not now" button labels are NOT re-declared here — they are
// already owned + registered by [ShellCopy.CONSENT_GRANT] / [ShellCopy.CONSENT_DECLINE] (Plan 02),
// and `OnboardingScreen` references those. Duplicating them would invite drift; the audit already
// covers them. The two long-form legal constants (`Disclaimer.text`, `PrivacyPolicyText.body`) are
// likewise NOT here — they are single-sourced + exempt-scanned with their own must-contain tests.
package com.endoapp.app.content

/**
 * The fixed onboarding/consent chrome strings rendered by `OnboardingScreen` this phase: the hero
 * title + welcome body, the privacy link, the consent section heading + explainer, and the
 * consent-write error string. Exposed as named constants so the composable references a single
 * source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 */
object OnboardingCopy : AuditableCopy {

    /** Hero title. */
    const val HERO_TITLE = "Welcome to Endo-App"

    /** Warm, validating, non-clinical welcome body. */
    const val WELCOME_BODY =
        "A calm, private place to track your bleeding, symptoms, and cycle — and to slowly make " +
            "sense of your own patterns, at your own pace."

    /** Privacy link label — opens the offline `PrivacyPolicyScreen` BEFORE the consent action. */
    const val PRIVACY_LINK = "Read how your data stays private"

    /** Consent section heading. */
    const val CONSENT_HEADING = "Ready when you are"

    /** Plain-language consent explainer (on-device, no account, no cloud; withdraw anytime). */
    const val CONSENT_EXPLAINER =
        "Endo-App works only after you consent to it processing the health information you log. " +
            "Everything stays on this device — no account, no cloud. Nothing is processed until you " +
            "tap the button below, and you can withdraw your consent at any time in Settings."

    /** Error shown if persisting the consent choice fails (the consent-write failure path). */
    const val CONSENT_WRITE_ERROR = "Something went wrong saving your choice. Please tap again."

    override val auditableStrings: List<String> = listOf(
        HERO_TITLE,
        WELCOME_BODY,
        PRIVACY_LINK,
        CONSENT_HEADING,
        CONSENT_EXPLAINER,
        CONSENT_WRITE_ERROR,
    )
}
