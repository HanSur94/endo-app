// The registered user-facing copy for the Phase-20 Settings surface + medication management — a new
// `AuditableCopy` source in `:app`. Per the LOAD-BEARING RULE (CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), every string here is registered into [CopyAuditRegistry.sources]
// in the SAME change that introduces the Settings screen that renders it, so `CopyAuditTest` scans it
// from birth.
//
// SUBSTRING-GATE NEAR-MISSES (extends the UI-SPEC §7 reword set): the CopyAudit scan is a
// case-insensitive SUBSTRING match against `ClaimsPolicy` (causal / imperative / alarmist / gendered /
// precision). The UI-SPEC §7 reworded three iOS strings (here→dropped, stops→"keeps it out",
// treatment→"hormonal medication") but MISSED that "taken" ⊃ "take" (imperative). The remove-med body
// and the no-meds-in-log line the UI-SPEC specified BOTH carried "taken", so they are reworded here to
// drop the "take" substring (the medication vocabulary is "log it" / "each day", not "taken"):
//   - remove-med body "…logged it as taken stay unchanged." → "…already logged it stay unchanged."
//   - no-meds-in-log  "…to mark it taken each day."         → "…to log it each day."
// The withdraw-consent dialog copy (which legitimately carries "will"/"stop" — the destructive-consent
// legal register) is single-sourced + EXEMPT-scanned as [WithdrawConsentCopy] below, exactly as
// [Disclaimer] / [PrivacyPolicyText] are — NOT added to the curated substring registry (UI-SPEC §7:
// "do NOT introduce a new `stop` into a freshly-curated string").
package com.endoapp.app.content

/**
 * The fixed Settings + manage-meds strings rendered by `SettingsScreen`, `ManageMedsSection`, and
 * `AddMedicationSheet` this phase. Exposed as named constants so each composable references a single
 * source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 *
 * The withdraw-consent BUTTON label is NOT re-declared here — it is already owned + registered by
 * [ShellCopy.WITHDRAW] (Phase 19), and the Settings withdraw control references that. The withdraw
 * DIALOG title/body/keep strings live in the exempt [WithdrawConsentCopy] (the legal register). The
 * reminders section is deliberately NOT here — it is a plan-05 slot, so no notification copy lands this
 * phase.
 */
object SettingsCopy : AuditableCopy {

    /** Settings top-app-bar title. */
    const val TITLE = "Settings"

    // --- Manage meds ---

    /** Manage-meds section header. */
    const val MANAGE_MEDS = "Manage meds"

    /** Add-a-medication action label (opens the add sheet). */
    const val ADD_MED = "Add a medication"

    /** No-saved-meds empty-state title. */
    const val NO_MEDS_TITLE = "No medications saved yet."

    /** No-saved-meds empty-state body. */
    const val NO_MEDS_BODY =
        "Add the medications you want to keep track of, and they appear each day to tick off."

    /** Add-med form: the required name field label. */
    const val MED_NAME = "Name"

    /** Add-med form: the optional type/note field label. */
    const val MED_NOTE = "Type or note (optional)"

    /** Add-med form / remove-dialog: save action. */
    const val SAVE = "Save"

    /** Add-med form / remove-dialog: cancel action. */
    const val CANCEL = "Cancel"

    /** Remove-med confirmation dialog title. */
    const val REMOVE_MED_TITLE = "Remove this medication?"

    /**
     * Remove-med confirmation body. Reworded off the UI-SPEC §7 "…logged it as taken stay unchanged."
     * because "taken" ⊃ the imperative "take" — the soft-archive keeps prior history valid, stated
     * without the forbidden substring.
     */
    const val REMOVE_MED_BODY =
        "This keeps it out of your daily check-in. Days you already logged it stay unchanged."

    /** Remove-med confirm action (destructive; soft-archives, never hard-deletes). */
    const val REMOVE = "Remove"

    /**
     * The line the Day-Log/Check-In medication section shows when no meds are saved yet. Reworded off
     * the UI-SPEC §7 "…mark it taken each day." because "taken" ⊃ "take".
     */
    const val NO_MEDS_IN_LOG = "No saved medications yet. Add one in Settings to log it each day."

    // --- Cycle tracking off (treatment / suppression state) ---

    /** Cycle-tracking-off toggle label. */
    const val CYCLE_OFF = "Cycle tracking is off"

    /**
     * Cycle-off footer. The UI-SPEC §7 reworded form (continuous hormonal medication; "then hides"
     * instead of "stops showing") — substring-clean ("usual" ⊅ "use").
     */
    const val CYCLE_OFF_FOOTER =
        "Turn this on if you don't have a cycle right now — for example on continuous hormonal " +
            "medication. Endo-App then hides any cycle estimate, and you can still log symptoms and " +
            "bleeding as usual."

    // --- Privacy ---

    /** Privacy row label — opens the Phase-19 `PrivacyPolicyScreen`. */
    const val PRIVACY = "Privacy"

    override val auditableStrings: List<String> = listOf(
        TITLE,
        MANAGE_MEDS,
        ADD_MED,
        NO_MEDS_TITLE,
        NO_MEDS_BODY,
        MED_NAME,
        MED_NOTE,
        SAVE,
        CANCEL,
        REMOVE_MED_TITLE,
        REMOVE_MED_BODY,
        REMOVE,
        NO_MEDS_IN_LOG,
        CYCLE_OFF,
        CYCLE_OFF_FOOTER,
        PRIVACY,
    )
}

/**
 * The withdraw-consent DIALOG copy (the destructive-consent legal register) — single-sourced and
 * EXEMPT from the curated `CopyAudit` substring scan (deliberately NOT in [CopyAuditRegistry.sources]),
 * exactly like [Disclaimer.text] / [PrivacyPolicyText.body].
 *
 * [BODY] legitimately CONTAINS the causal/imperative substrings "will" and "stop" — it states, in the
 * plain legal register the GDPR withdraw affordance requires, what the app does when consent is
 * withdrawn. Adding it to the curated registry would false-flag the forbidden-token gate (UI-SPEC §7:
 * "`stop` is only forbidden as a curated-copy substring … do NOT introduce a new `stop` into a
 * freshly-curated string"). Instead it is pinned by its own render + must-contain assertions in
 * `SettingsScreenTest` (mirroring how `DisclaimerTest` pins [Disclaimer]).
 *
 * The confirm-button label reuses [ShellCopy.WITHDRAW] (already registered); only the dialog
 * title / body / keep-action live here.
 */
object WithdrawConsentCopy {

    /** Withdraw-consent confirmation dialog title. */
    const val TITLE = "Withdraw consent?"

    /**
     * The plain-language destructive-consent explanation (GDPR withdraw). Legitimately carries the
     * "will"/"stop" legal register — exempt-scanned, never in the curated registry.
     */
    const val BODY =
        "You'll return to the welcome screen and the app will stop processing your health data. " +
            "Your existing entries stay on this device until you delete them."

    /** The non-destructive cancel action — keep consent, dismiss the dialog. */
    const val KEEP = "Keep consent"
}
