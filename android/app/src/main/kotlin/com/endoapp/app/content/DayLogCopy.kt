// EndoApp (:app) — the registered Day Log editor + accessible BodyMap copy (ATRK-02 / SYMP-02,
// Plan 20-03). The per-screen `AuditableCopy` source the Day Log editor + BodyMap render, registered
// into [CopyAuditRegistry.sources] in the SAME change that introduces the screen (the LOAD-BEARING
// RULE — CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth.
//
// SCOPE: only the strings the Day Log editor + BodyMap actually render. The shared vocabulary (the
// `Not logged` caption, every enum→label map, the SeverityControl strings, the BodyMap
// `severity N of 10` / `not marked` stateDescriptions + the `N/10` summary) already lives in
// [TrackingCopy] and is referenced there — never re-declared here (single-source).
//
// SUBSTRING-GATE CURATION: every string below was hand-scanned against the `ClaimsPolicy` causal /
// imperative / alarmist / gendered / precision lists (case-insensitive SUBSTRING match). The near
// misses this screen watches all pass: `spot`⊅`stop`, `removes`⊅(`reduce`/`improve`), `clear`⊅any,
// `undone`⊅any, `your day`⊅`her`, `Delete`⊅(`detect`/`treat`). `CopyAuditTest` re-scans them from the
// registry so a future edit that reintroduces a trap goes red.
package com.endoapp.app.content

/**
 * The fixed Day Log editor + BodyMap strings. Named constants so each composable references a single
 * source; collected in [auditableStrings] for the `CopyAudit` substring scan. The `Not logged`
 * caption, enum labels, and BodyMap `stateDescription` builders are reused from [TrackingCopy] and
 * deliberately NOT re-declared here.
 */
object DayLogCopy : AuditableCopy {

    // --- Section headers (order per UI-SPEC §1: Symptoms → Pain areas → Bleeding → Food → Activity
    //     → Sleep → Medication → Stress → Mood → Note) ---

    const val SECTION_SYMPTOMS = "Symptoms"
    const val SECTION_PAIN = "Pain areas"
    const val SECTION_BLEEDING = "Bleeding"
    const val SECTION_FOOD = "Food & drink"
    const val SECTION_ACTIVITY = "Activity"
    const val SECTION_SLEEP = "Sleep"
    const val SECTION_MEDICATION = "Medication"
    const val SECTION_STRESS = "Stress"
    const val SECTION_MOOD = "Overall mood for the day"
    const val SECTION_NOTE = "Note"

    // --- Top bar ---

    /** The plum Save capsule — maps the draft through the mapper and persists it. */
    const val SAVE = "Save"

    /** The cancel / back affordance. */
    const val CANCEL = "Cancel"

    // --- Section-specific control labels ---

    /** Bleeding clots toggle. */
    const val CLOTS = "Clots"

    /** As-needed pain-relief medication flag. `relief`⊅`relieve` (no trailing "ve"). */
    const val PRN = "As-needed pain relief"

    /** Stress reveal-on-opt-in row label. */
    const val STRESS_LOG = "Log stress"

    /** Stress clear affordance (returns stress to not-logged). */
    const val STRESS_CLEAR = "Clear stress"

    /** Food free-text placeholder. */
    const val FOOD_NOTE_PLACEHOLDER = "Anything else?"

    /** Food footer — the "your own data, not a diary" framing. `spot`⊅`stop`. */
    const val FOOD_FOOTER = "Optional tags to spot patterns in your own data — not a calorie diary."

    /** Day-note free-text placeholder. `your day`⊅`her`. */
    const val NOTE_PLACEHOLDER = "How was your day?"

    // --- Destructive delete (guarded by a confirmation dialog) ---

    /** The destructive delete action, shown ONLY for a day that already has data. */
    const val DELETE_DAY = "Delete this day"

    /** The confirmation dialog title. */
    const val DELETE_TITLE = "Delete this day's log?"

    /** The confirmation dialog body. `removes`⊅(`reduce`/`improve`); `undone`⊅any. */
    const val DELETE_BODY =
        "This removes the symptoms, pain areas, bleeding, and note logged for this day. It cannot be undone."

    /** The destructive confirm label. `Delete`⊅(`detect`/`treat`). */
    const val DELETE_CONFIRM = "Delete"

    // --- Accessible BodyMap (front/back toggle + region severity sheet + list fallback) ---

    /** The front/back segmented-toggle group label. */
    const val BODY_SIDE = "Body side"

    /** Front silhouette side. */
    const val FRONT = "Front"

    /** Back silhouette side. */
    const val BACK = "Back"

    /** The non-visual list-fallback disclosure — the fully-operable no-map path (SYMP-02). */
    const val ALL_AREAS = "All areas (list)"

    /** The region severity sheet title. */
    const val PAIN_AREA = "Pain area"

    /** The destructive clear-this-region affordance (disabled when unmarked). */
    const val CLEAR_AREA = "Clear this area"

    /** The sheet dismiss action. */
    const val DONE = "Done"

    override val auditableStrings: List<String> = listOf(
        SECTION_SYMPTOMS,
        SECTION_PAIN,
        SECTION_BLEEDING,
        SECTION_FOOD,
        SECTION_ACTIVITY,
        SECTION_SLEEP,
        SECTION_MEDICATION,
        SECTION_STRESS,
        SECTION_MOOD,
        SECTION_NOTE,
        SAVE,
        CANCEL,
        CLOTS,
        PRN,
        STRESS_LOG,
        STRESS_CLEAR,
        FOOD_NOTE_PLACEHOLDER,
        FOOD_FOOTER,
        NOTE_PLACEHOLDER,
        DELETE_DAY,
        DELETE_TITLE,
        DELETE_BODY,
        DELETE_CONFIRM,
        BODY_SIDE,
        FRONT,
        BACK,
        ALL_AREAS,
        PAIN_AREA,
        CLEAR_AREA,
        DONE,
    )
}
