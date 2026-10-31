// The registered Insights-dashboard CHROME copy — a new `AuditableCopy` source in `:app`, appended to
// [CopyAuditRegistry.sources] in the SAME change (the LOAD-BEARING RULE, CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth (22-UI-SPEC §Copywriting).
//
// SCOPE: this owns ONLY the app-chrome strings the Phase-22 dashboard renders AROUND the insights —
// section headers, the plain-language confidence words, the honest empty/sparse states, the dismiss
// affordance, the window labels, and the words-only evidence line. The insight COPY ITSELF (headline /
// detail / the calm flare + recommendation templates) is engine-generated and ALREADY audited in
// `:insights` (rendered VERBATIM); it is NOT re-registered here.
//
// SUBSTRING-GATE NOTE (Pitfall 2 — the CopyAudit scan is a case-insensitive SUBSTRING match against
// `ClaimsPolicy`, which reuses the drift-guarded `:insights` `ForbiddenTerms`): every string below is
// hand-curated to dodge the forbidden substrings entirely, including the innocent host-word traps —
//   • Direction words use `trending up` / `trending down`, NEVER `higher` / `lower` (⊃ the gendered `her`).
//   • The not-medical-advice framing is NOT duplicated here — it is reused via the exempt
//     [Disclaimer.text] / `DisclaimerBanner`, so this file never restates "diagnose/treat/cure/prevent".
//   • No load-error string is ported: [com.endoapp.app.insights.InsightsUiState] has no error channel
//     (the coordinator swallows a persist failure best-effort), so there is no error surface to render —
//     and the UI-SPEC's load-error copy ("…try again…") would trip the imperative `try` substring anyway.
package com.endoapp.app.content

import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.TrendWindow

/**
 * The fixed dashboard-chrome strings the Insights tab renders. Exposed as named members so each render
 * site references a single source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 */
object InsightsCopy : AuditableCopy {

    /** The presentational subtitle under the "Insights" title (block 1). */
    const val SUBTITLE = "Patterns in your own data, as they build over time."

    // --- The five fixed-order kind-section headers (22-UI-SPEC §A block order) ---

    /** Section 5 — placed FIRST by POSITION only (calm-flare; the ONLY sanctioned "warning" word). */
    const val SECTION_FLARE = "Flare early-warning"
    const val SECTION_TRENDS = "Trends"
    const val SECTION_CYCLE_PHASE = "Cycle phase"

    /** "Possible associations" — deliberately avoids "Correlations"/causation framing. */
    const val SECTION_ASSOCIATIONS = "Possible associations"
    const val SECTION_RECOMMENDATIONS = "Recommendations"

    // --- Plain-language confidence words (H6 — text, NEVER a number, NEVER color alone) ---

    const val CONFIDENCE_LOW = "Low — possible association"
    const val CONFIDENCE_MEDIUM = "Medium — possible association"
    const val CONFIDENCE_HIGH = "High — possible association"

    /** The qualitative confidence explainer (⊂ the info popover) — no threshold numbers, no percent. */
    const val CONFIDENCE_INFO_TITLE = "What does this label mean?"
    const val CONFIDENCE_INFO_BODY =
        "These labels describe how much of your own logged data a pattern rests on. More logged days " +
            "point to a firmer label. It is a plain-language guide for talking with your clinician, " +
            "not a precise number."

    /** The per-card basis note (block 5 of the card anatomy). */
    const val BASIS_NOTE = "Based on your logs."

    // --- Window labels (the trend/evidence supporting line) ---

    const val WINDOW_LAST7 = "last 7 days"
    const val WINDOW_LAST30 = "last 30 days"
    const val WINDOW_LAST90 = "last 90 days"
    const val WINDOW_ALL_TIME = "all time"

    // --- Direction words (NEVER higher/lower — the "her" substring trap) ---

    const val DIRECTION_UP = "trending up"
    const val DIRECTION_DOWN = "trending down"
    const val DIRECTION_STEADY = "steady"

    // --- Non-destructive dismiss (gray/secondary — NEVER red; §Interaction) ---

    const val DISMISS_ACTION = "This isn't right"
    const val DISMISS_CONFIRMATION = "Thanks — we won't show that again."

    /** The subtle non-blocking recompute caption (block 4). */
    const val UPDATING = "Updating…"

    // --- The two honest non-enough states (§A states table) ---

    const val EMPTY_HEADING = "Keep logging — insights appear once you have enough data."
    const val EMPTY_BODY =
        "The more days you log, the more patterns Endo-App can surface from your own data."

    const val SPARSE_TITLE = "Not enough data yet."
    const val SPARSE_SUBTITLE = "Keep logging and this fills in over time."

    // --- At-a-glance stat-strip tile labels (block 2) ---

    const val STAT_TOTAL_LABEL = "patterns surfaced"
    const val STAT_KINDS_LABEL = "insight kinds"

    /** The plain-language confidence word for a level (LOW and NONE share the "Low" label). */
    fun confidenceWord(confidence: InsightConfidence): String = when (confidence) {
        InsightConfidence.HIGH -> CONFIDENCE_HIGH
        InsightConfidence.MEDIUM -> CONFIDENCE_MEDIUM
        InsightConfidence.LOW, InsightConfidence.NONE -> CONFIDENCE_LOW
    }

    /** The absolute-window label for an insight's [TrendWindow]. */
    fun windowLabel(window: TrendWindow): String = when (window) {
        TrendWindow.LAST7 -> WINDOW_LAST7
        TrendWindow.LAST30 -> WINDOW_LAST30
        TrendWindow.LAST90 -> WINDOW_LAST90
        TrendWindow.ALL_TIME -> WINDOW_ALL_TIME
    }

    /** The words-only evidence line — a factual tally (a bare digit is legal; NEVER a coefficient/%). */
    fun seenAcross(loggedDays: Int): String = "Seen across $loggedDays logged days."

    override val auditableStrings: List<String> = listOf(
        SUBTITLE,
        SECTION_FLARE, SECTION_TRENDS, SECTION_CYCLE_PHASE, SECTION_ASSOCIATIONS, SECTION_RECOMMENDATIONS,
        CONFIDENCE_LOW, CONFIDENCE_MEDIUM, CONFIDENCE_HIGH,
        CONFIDENCE_INFO_TITLE, CONFIDENCE_INFO_BODY,
        BASIS_NOTE,
        WINDOW_LAST7, WINDOW_LAST30, WINDOW_LAST90, WINDOW_ALL_TIME,
        DIRECTION_UP, DIRECTION_DOWN, DIRECTION_STEADY,
        DISMISS_ACTION, DISMISS_CONFIRMATION,
        UPDATING,
        EMPTY_HEADING, EMPTY_BODY,
        SPARSE_TITLE, SPARSE_SUBTITLE,
        STAT_TOTAL_LABEL, STAT_KINDS_LABEL,
        // A representative words-only evidence line (the static prefix/suffix is what we audit).
        seenAcross(3),
    )
}
