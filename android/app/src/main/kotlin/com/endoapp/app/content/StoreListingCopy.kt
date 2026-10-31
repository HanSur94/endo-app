// Requirement: AREL-03 (Phase 25, T-25-02) — the Google Play store listing copy as a REGISTERED,
// auditable copy surface. The store listing is the app's most public claim surface and the most
// rejection-prone artifact for a health app, so it must pass the SAME `CopyAudit` substring gate every
// in-app string passes (no diagnostic/alarmist/gendered/imperative/false-precision language).
//
// THE LOAD-BEARING RULE (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`): this object is appended
// to [CopyAuditRegistry.sources] in the SAME change — an unregistered source silently escapes the scan.
//
// DISCLAIMER-FIRST (VERIFIED: support.google.com/16679511): Play requires the non-medical-device wording
// "not a medical device and does not diagnose, treat, cure, or prevent any condition" in the description.
// The first paragraph of [fullDescription] IS [Disclaimer.text] VERBATIM — the single source of truth
// that already carries that exact phrasing plus the "have better conversations with your clinician"
// reminder. A second, hand-typed disclaimer would drift (RESEARCH Anti-Patterns) — forbidden.
//
// EXEMPT-STRING IDIOM (mirrors `Disclaimer` / `ReportCopy.FOOTER`): the disclaimer paragraph legitimately
// CONTAINS "diagnose/treat/cure/prevent" as NEGATIONS (it states what the app is NOT), so it would
// false-flag the forbidden-token gate. It is therefore DELIBERATELY excluded from [auditableStrings] and
// pinned instead by a dedicated must-contain-in-first-paragraph test (`StoreListingCopyTest`). Every OTHER
// store string IS registered and audited.
package com.endoapp.app.content

/**
 * The Google Play store listing copy (app title, short + full description, what's-new) — a registered
 * [AuditableCopy] source (AREL-03). Every string here (except the deliberately-exempt first paragraph,
 * which is [Disclaimer.text] verbatim) is scanned by `CopyAuditTest` against the ClaimsPolicy
 * substring gate. Field lengths honor Play's limits: title ≤ 30, short ≤ 80, full ≤ 4000
 * (VERIFIED: support.google.com/13393723, /9859152). These strings are the ready-to-paste source for
 * `PLAY-LISTING.md` (Plan 03).
 */
object StoreListingCopy : AuditableCopy {

    /** App title — ≤ 30 characters (Play limit). No diagnostic/medical-claim framing. */
    val title: String = "Endo: Endometriosis Tracker"

    /** Short description — ≤ 80 characters (Play limit). Privacy-first, on-device framing. */
    val shortDescription: String =
        "Private, on-device logging for symptoms, cycle, and daily factors. Yours only."

    /**
     * The EXEMPT first paragraph of the full description — [Disclaimer.text] VERBATIM (single source of
     * truth). It legitimately contains "diagnose/treat/cure/prevent" as negations, so it is NOT in
     * [auditableStrings]; it is pinned by the first-paragraph must-contain test (`StoreListingCopyTest`),
     * exactly as `Disclaimer.text` / `ReportCopy.FOOTER` are exempt-yet-pinned.
     */
    val fullDescriptionFirstParagraph: String = Disclaimer.text

    /**
     * The AUDITED body paragraphs of the full description (registered in [auditableStrings]). Each must
     * pass the ClaimsPolicy substring gate — no causal/imperative/alarmist/gendered/false-precision token.
     */
    val fullDescriptionBody: List<String> = listOf(
        "Endo-App helps you keep a private record of how you feel day to day — pain, bleeding, mood, " +
            "sleep, food, and movement — so patterns in your own data become easier to see over time.",
        "Everything lives on your phone. No account, no sign-in, no cloud, and no third-party tracking. " +
            "Your health data leaves your phone only if you choose to export it yourself.",
        "See gentle, plain-language views of your trends, cycle phases, and possible associations in " +
            "your own data — always informational, worth discussing with your clinician.",
        "Share a clean, printable report with your clinician when it helps.",
    )

    /** The release notes / what's-new line for the first Android release (audited). */
    val whatsNew: String =
        "First release of Endo-App for Android: private, on-device symptom and cycle logging with " +
            "plain-language insights."

    /**
     * The full store description: the exempt disclaimer paragraph FIRST, then the audited body
     * paragraphs, joined by blank lines so the disclaimer is literally the first paragraph
     * (`substringBefore("\n\n")` yields exactly [Disclaimer.text]).
     */
    val fullDescription: String
        get() = (listOf(fullDescriptionFirstParagraph) + fullDescriptionBody).joinToString("\n\n")

    /**
     * Every fixed store string `CopyAuditTest` scans — the title, short description, what's-new, and the
     * audited body paragraphs. The exempt first paragraph ([Disclaimer.text]) is DELIBERATELY omitted
     * (it is pinned by the first-paragraph must-contain test), mirroring the `Disclaimer` / `ReportCopy.
     * FOOTER` exemption.
     */
    override val auditableStrings: List<String> =
        listOf(title, shortDescription, whatsNew) + fullDescriptionBody
}
