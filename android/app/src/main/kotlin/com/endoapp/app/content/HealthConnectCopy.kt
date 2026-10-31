// The registered user-facing copy for the WHOLE Phase-21 Health Connect surface — a new
// `AuditableCopy` source in `:app`, the Kotlin analog of the iOS `HealthConnectCopy.swift`. Per the
// LOAD-BEARING RULE (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`), every string here is
// registered into [CopyAuditRegistry.sources] in the SAME change that introduces the connect/import
// screens that render it, so `CopyAuditTest` scans it from birth.
//
// SUBSTRING-GATE RE-VERIFICATION (this change): the CopyAudit scan is a case-insensitive SUBSTRING
// match against `ClaimsPolicy` (causal / imperative / alarmist / gendered / precision). EVERY string
// below was re-checked BY HAND against the forbidden lists — in particular the imperative traps
// should/use/start/stop/take/try/cut, the causal traps treat/will/cause, and the "her" gendered
// substring trap (here/there/other/higher/gather/together). Notable near-misses that were curated
// around rather than tripped:
//   - "Continue" is safe — it ends "…nue", NOT the imperative "use".
//   - "history" is safe — it is h-i-s-t-o-r-y, it does NOT contain the gendered "her" (h-e-r).
//   - the empty-state deliberately says "on this screen" (NOT "here", which is clean, but the iOS
//     source avoided it to sidestep the "her" family entirely) and "when an app writes …" (no "use").
//   - "update"/"add"/"keep logging by hand" carry no forbidden substring.
// The automated `CopyAuditTest` is the enforcing artifact; this hand re-check is the belt.
package com.endoapp.app.content

/**
 * The single copy surface for the entire Health Connect phase (connect entry point → rationale →
 * import states → provenance → denial → availability-unavailable states). Every string is a named
 * constant so each composable references one source, and all are collected in [auditableStrings] for
 * the `CopyAudit` substring scan. Transcribed VERBATIM from 21-RESEARCH.md §Health Connect UX (which
 * pre-checked each string); re-verified by hand here.
 *
 * FRAMING: informational, on-device, READ-ONLY throughout — never a diagnostic or medical claim, and
 * never gendered. Manual logging is the hero path in every availability-unavailable state.
 */
object HealthConnectCopy : AuditableCopy {

    // --- Entry point (Settings and/or Log) ---

    /** The Bloom row/card label that opens the connect flow. */
    const val ENTRY_LABEL = "Connect Health data"

    /** The entry-point caption — what it brings in, read-only, on device. */
    const val ENTRY_CAPTION =
        "Bring in cycle, sleep, workouts, and meals — read-only, on your device."

    // --- Screen A — rationale-before-permission (HealthRationaleScreen, the manifest rationale target) ---

    /** Rationale headline (same label as the entry point). */
    const val RATIONALE_HEADLINE = "Connect Health data"

    /** Read-only line — the app only reads, never writes. */
    const val RATIONALE_READ_ONLY =
        "Endo-App only reads from Health Connect. It never changes or adds anything."

    /** Privacy line — data stays on device. */
    const val RATIONALE_PRIVATE =
        "Your data stays on your device. No one else can see it."

    /** Optional line — skippable and reversible anytime. */
    const val RATIONALE_OPTIONAL =
        "This is optional. You can skip it, and you can turn it off anytime."

    /** What-it-reads line — the four domains, framed as avoiding double logging. */
    const val RATIONALE_WHAT_IT_READS =
        "It can bring in your cycle, sleep, workouts, and meal days so you do not log them twice."

    /** Primary button — the only path to the system permission sheet. */
    const val CONTINUE = "Continue"

    /** Dismiss button — closes the rationale, no nag. */
    const val NOT_NOW = "Not now"

    // --- Screen C — import states (HealthImportScreen, Plan 04) ---

    /** In-progress line while importing. */
    const val IMPORT_IN_PROGRESS = "Bringing in your data…"

    /** Window label when READ_HEALTH_DATA_HISTORY is granted (full history). */
    const val WINDOW_LABEL_HISTORY = "Bringing in your full history from Health Connect."

    /** Window label when history is denied/not requested (honest 30-day window, AHC-04). */
    const val WINDOW_LABEL_30_DAY = "Bringing in the last 30 days from Health Connect."

    /** Done-with-data confirmation. */
    const val IMPORT_DONE = "Connected. Imported data shows a Health Connect badge."

    /** Empty-result state — the common first-run outcome (nil-honest ethos). */
    const val IMPORT_EMPTY =
        "Nothing to bring in yet. When an app writes cycle, sleep, workouts, or meals to " +
            "Health Connect, it can appear on this screen."

    /** Optional per-permission status line — truthful, never a nag. */
    const val PER_PERMISSION_STATUS = "Sleep: connected · Nutrition: not connected"

    /** Manual re-import action. */
    const val REFRESH = "Refresh from Health Connect"

    // --- Provenance badge (HealthSourceBadge) ---

    /** The source chip shown on any imported value — a pure source tag, never a value claim. */
    const val PROVENANCE_BADGE = "from Health Connect"

    // --- Denial (graceful, no nag) ---

    /** Denied banner — manual logging is unchanged. */
    const val DENIED_BANNER = "No data was connected. Manual logging works exactly the same."

    /** Re-open hint — the path stated plainly. */
    const val DENIED_REOPEN_HINT = "You can connect Health data anytime from Settings."

    // --- Availability-unavailable states (honest, per getSdkStatus) ---

    /** PROVIDER_UPDATE_REQUIRED — provider not installed (API 28–33). */
    const val UNAVAILABLE_NOT_INSTALLED =
        "Health Connect is not on this device yet. You can add it from Google Play, or keep " +
            "logging by hand."

    /** Play deep-link button — install the provider. */
    const val GET_ON_PLAY = "Get Health Connect on Google Play"

    /** PROVIDER_UPDATE_REQUIRED — provider stale, needs update. */
    const val UNAVAILABLE_STALE =
        "Health Connect needs an update. You can update it on Google Play, or keep logging by hand."

    /** Play deep-link button — update the provider. */
    const val UPDATE_ON_PLAY = "Update on Google Play"

    /** SDK_UNAVAILABLE — impossible on this device (API 26–27); softened, not an error. */
    const val UNAVAILABLE_IMPOSSIBLE =
        "Health Connect is not available on this device. Manual logging works exactly the same."

    override val auditableStrings: List<String> = listOf(
        ENTRY_LABEL,
        ENTRY_CAPTION,
        RATIONALE_HEADLINE,
        RATIONALE_READ_ONLY,
        RATIONALE_PRIVATE,
        RATIONALE_OPTIONAL,
        RATIONALE_WHAT_IT_READS,
        CONTINUE,
        NOT_NOW,
        IMPORT_IN_PROGRESS,
        WINDOW_LABEL_HISTORY,
        WINDOW_LABEL_30_DAY,
        IMPORT_DONE,
        IMPORT_EMPTY,
        PER_PERMISSION_STATUS,
        REFRESH,
        PROVENANCE_BADGE,
        DENIED_BANNER,
        DENIED_REOPEN_HINT,
        UNAVAILABLE_NOT_INSTALLED,
        GET_ON_PLAY,
        UNAVAILABLE_STALE,
        UPDATE_ON_PLAY,
        UNAVAILABLE_IMPOSSIBLE,
    )
}
