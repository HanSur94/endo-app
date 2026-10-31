// EndoApp (:app) — the registered Daily Check-In copy (ATRK-01, Plan 20-02). The per-screen
// `AuditableCopy` source the fast-log flow renders, registered into [CopyAuditRegistry.sources] in the
// SAME change that introduces the screen (the LOAD-BEARING RULE — CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth.
//
// SCOPE: only the strings the Daily Check-In screen actually renders. The shared vocabulary (the
// `Not logged` caption, the `Daily check-in` title/CTA, every enum→label map, the SeverityControl
// strings) already lives in [TrackingCopy] and is referenced there — never re-declared here
// (single-source). Day Log / Settings / reminder copy lands in their own later plans' `…Copy` objects.
//
// SUBSTRING-GATE CURATION: every string below was hand-scanned against the `ClaimsPolicy` causal /
// imperative / alarmist / gendered / precision lists (case-insensitive SUBSTRING match). The classic
// near-misses this phase watches — `usual`⊅`use`, `never`⊅`her`, `carried over`⊅`her`,
// `relief`⊅`relieve`, `stress`⊅`treat` — all pass; `CheckInCopyAuditTest` re-scans them from the
// registry so a future edit that reintroduces a trap goes red.
package com.endoapp.app.content

/**
 * The fixed Daily Check-In strings. Named constants so each composable references a single source;
 * collected in [auditableStrings] for the `CopyAudit` substring scan. The title/CTA + not-logged
 * caption are reused from [TrackingCopy] and deliberately NOT re-declared here.
 */
object CheckInCopy : AuditableCopy {

    // --- Top bar ---

    /** The confirm action — commits touched sections and dismisses (fast-log, nothing mandatory). */
    const val DONE = "Done for today"

    /** The cancel / back affordance (dismisses without recording anything). */
    const val CANCEL = "Cancel"

    // --- Same as yesterday (sticky CONTEXT only — never symptoms/bleeding) ---

    /** The prefill affordance — brings back yesterday's sticky context. */
    const val SAME_AS_YESTERDAY = "Same as yesterday"

    /** The subtitle making the sticky-context-only contract explicit. `usual`⊅`use`; `never`⊅`her`. */
    const val SAME_AS_YESTERDAY_SUBTITLE =
        "Brings back your usual meds, sleep and activity. Symptoms and bleeding are never carried over."

    // --- Empty-confirm adherence note (records the marker, invents nothing) ---

    /** The inline note shown after an empty confirm — an honest adherence acknowledgement. */
    const val CHECKED_IN_NOTE = "Checked in for today."

    // --- Section headers (order per UI-SPEC §1) ---

    const val SECTION_SYMPTOMS = "Symptoms"
    const val SECTION_FOOD = "Food & drink"
    const val SECTION_ACTIVITY = "Activity"
    const val SECTION_SLEEP = "Sleep"
    const val SECTION_MEDICATION = "Medication"
    const val SECTION_STRESS = "Stress"
    const val SECTION_MOOD = "Overall mood for the day"
    const val SECTION_BLEEDING = "Bleeding"

    // --- Section-specific control labels ---

    /** Bleeding clots toggle. */
    const val CLOTS = "Clots"

    /** As-needed pain-relief medication flag. `relief`⊅`relieve` (no trailing "ve"). */
    const val PRN = "As-needed pain relief"

    /** Stress reveal-on-opt-in row label. `stress`⊅`treat`. */
    const val STRESS_LOG = "Log stress"

    /** Stress clear affordance (returns stress to not-logged). */
    const val STRESS_CLEAR = "Clear stress"

    /** Food free-text placeholder. */
    const val FOOD_NOTE_PLACEHOLDER = "Anything else?"

    override val auditableStrings: List<String> = listOf(
        DONE,
        CANCEL,
        SAME_AS_YESTERDAY,
        SAME_AS_YESTERDAY_SUBTITLE,
        CHECKED_IN_NOTE,
        SECTION_SYMPTOMS,
        SECTION_FOOD,
        SECTION_ACTIVITY,
        SECTION_SLEEP,
        SECTION_MEDICATION,
        SECTION_STRESS,
        SECTION_MOOD,
        SECTION_BLEEDING,
        CLOTS,
        PRN,
        STRESS_LOG,
        STRESS_CLEAR,
        FOOD_NOTE_PLACEHOLDER,
    )
}
