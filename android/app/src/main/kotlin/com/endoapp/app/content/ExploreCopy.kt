// The registered Explore-chrome copy — a new `AuditableCopy` source in `:app`, appended to
// [CopyAuditRegistry.sources] in the SAME change (the LOAD-BEARING RULE, CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth. Ports the 22-UI-SPEC
// §Copywriting Explore strings VERBATIM.
//
// SUBSTRING-GATE NOTE (Pitfall 2 — the CopyAudit scan is a case-insensitive SUBSTRING match against
// `ClaimsPolicy` = the drift-guarded `:insights` ForbiddenTerms union). Every string below is curated
// to dodge the innocent host-word collisions the iOS sources already dodge:
//   • cap hint uses "add a new one" NOT "another" (⊃ "her").
//   • sparse body keeps "Unlogged days stay as gaps — a gap is honest, not a zero." (no forbidden token).
//   • event tick reads "Logged", never "1" (a presence has no numeric value — H: events≠0/1).
// These mirror the iOS `ExploreCopy` (LOCKED wording).
package com.endoapp.app.content

/**
 * The fixed Explore-tab chrome strings the screen, series picker, lanes, event lanes, and multi-series
 * tooltip render. Exposed as named members so each render site references a single source, and collected
 * in [auditableStrings] for the `CopyAudit` substring scan. The series DISPLAY names + real-unit readouts
 * are data-derived (the `:insights` catalog + `SeriesNormalization`), rendered verbatim like engine copy.
 */
object ExploreCopy : AuditableCopy {

    /** The serif screen title. */
    const val TITLE = "Explore"

    // --- Mode toggle (Lanes | Scatter) ---
    const val MODE_LANES = "Lanes"
    const val MODE_SCATTER = "Scatter"

    // --- Range segmented control ---
    const val RANGE_30 = "30 days"
    const val RANGE_90 = "90 days"
    const val RANGE_180 = "180 days"

    // --- Series-picker cap + chip a11y ---
    /** Shown when 4 quantitative series are selected (the cap). Uses "add a new one" — NOT "another"
     *  (⊃ the gendered "her"). */
    const val CAP_HINT = "Up to four series at once. Tap a selected chip to remove one, then add a new one."
    /** The a11y action label a SELECTED chip carries (tap removes it from the timeline). */
    const val CHIP_REMOVE = "Remove series"
    /** The a11y action label an UNSELECTED chip carries. */
    const val CHIP_ADD = "Add to the timeline."

    // --- Honest states (Lanes mode) ---
    const val EMPTY_TITLE = "Pick a series to begin."
    const val EMPTY_BODY = "Choose a series to see it on the timeline."
    const val SPARSE_TITLE = "Not enough data yet."
    /** Keeps the honest-gap framing verbatim (mirrors the iOS sparse body). */
    const val SPARSE_BODY =
        "Keep logging and these lanes fill in over time. Unlogged days stay as gaps — a gap is honest, not a zero."

    // --- Event lane + tick ---
    /** The event-tick / event-present tooltip value — a presence, NEVER "1". */
    const val EVENT_TICK = "Logged"

    // --- Scatter mode (Plan 06) ---
    // The honest placeholder is retired now that Scatter mode is real; retained as a member for any
    // fallback caller. The scatter is DESCRIPTIVE-ONLY: no fitted line, no r-value (H5) — the copy below
    // states a factual both-logged tally only, never a statistic.
    const val SCATTER_SOON = "The scatter view is coming soon."

    /** The `.correlation` card deep-link affordance label ("See the days behind this"). Rendered by the
     *  Insights card ONLY when `ScatterDeepLink.resolve` yields two quantitative axes. Uses "behind this"
     *  (no forbidden substring); NEVER re-derives or reads insight prose. */
    const val SCATTER_LINK = "See the days behind this"

    // Scatter axis-picker labels (the two quantitative-only selectors). "First" / "Second" dodge every
    // ClaimsPolicy substring (no gendered / causal / imperative token).
    const val SCATTER_AXIS_X_LABEL = "First factor"
    const val SCATTER_AXIS_Y_LABEL = "Second factor"

    /** The axis-selector placeholder before a factor is chosen. */
    const val SCATTER_AXIS_PLACEHOLDER = "Pick a factor"

    // Scatter honest states.
    /** Fewer than two axes chosen. */
    const val SCATTER_EMPTY_TITLE = "Pick two factors to compare."
    /** The empty-state body: "each dot is one day when both were logged" — days missing a factor are left
     *  out, never counted as zero (H: absence ≠ 0). "missing a factor" dodges "either" (⊃ "her"); "when
     *  both were logged" dodges "where" (⊃ "her"). */
    const val SCATTER_EMPTY_BODY =
        "Choose a factor for each axis. Each dot is one day when both were logged — days missing a factor are left out, never counted as zero."
    /** Both axes chosen but the both-logged tally is below the sparse floor. */
    const val SCATTER_SPARSE_TITLE = "Not enough overlapping days yet."
    /** The sparse body: "on too few of the same days" dodges "together" (⊃ "her"). */
    const val SCATTER_SPARSE_BODY =
        "These two factors were logged on too few of the same days to plot. Keep logging and this fills in over time."

    /** The neutral factual caption under the plot: "Based on {n} days when both were logged." A factual
     *  both-logged tally, NEVER a statistic (H5). */
    fun scatterCaption(days: Int): String = "Based on $days days when both were logged."

    /** The worded summary: "{X} compared with {Y}, {n} days when both were logged." The X/Y names are
     *  data-derived (catalog). "compared with" dodges "against" (which reads cause→effect). */
    fun scatterSummary(xName: String, yName: String, days: Int): String =
        "$xName compared with $yName, $days days when both were logged."

    /** The axis-titles line under the plot — the two data-derived series names, factual only. */
    fun scatterAxisTitles(xName: String, yName: String): String = "$xName · $yName"

    /** A per-cell a11y label: "{X name} {x readout}, {Y name} {y readout}" (names + readouts data-derived). */
    fun scatterCellLabel(xName: String, xReadout: String, yName: String, yReadout: String): String =
        "$xName $xReadout, $yName $yReadout"

    /** A per-cell a11y value: "{count} days" — a factual tally at that exact (x, y), never a statistic. */
    fun scatterCellDays(count: Int): String = "$count days"

    /** The event-lane summary node text, e.g. "Clots — logged on 3 days" — carried INSTEAD of a value
     *  axis (a value axis would imply a "1" the presence data does not carry). */
    fun eventLaneSummary(name: String, days: Int): String = "$name — logged on $days days"

    /** One multi-series tooltip / lane-title line: "{name} — {unit}" (unit omitted when empty). The name +
     *  unit are data-derived (catalog); this only composes the separator. */
    fun laneTitle(name: String, unit: String): String = if (unit.isEmpty()) name else "$name — $unit"

    /** A logged tooltip line: "{name}: {readout}" (the readout is the honest real-unit value). */
    fun seriesReadoutLine(name: String, readout: String): String = "$name: $readout"

    /** A gap tooltip line: "{name}: Not logged" (reuses the audited [ChartsCopy.NOT_LOGGED]). */
    fun seriesNotLoggedLine(name: String): String = "$name: ${ChartsCopy.NOT_LOGGED}"

    /** An event-present tooltip line: "{name}: Logged". */
    fun seriesLoggedLine(name: String): String = "$name: $EVENT_TICK"

    override val auditableStrings: List<String> = listOf(
        TITLE,
        MODE_LANES,
        MODE_SCATTER,
        RANGE_30,
        RANGE_90,
        RANGE_180,
        CAP_HINT,
        CHIP_REMOVE,
        CHIP_ADD,
        EMPTY_TITLE,
        EMPTY_BODY,
        SPARSE_TITLE,
        SPARSE_BODY,
        EVENT_TICK,
        SCATTER_SOON,
        // Scatter mode chrome (Plan 06) — registered in the SAME change that introduces it.
        SCATTER_LINK,
        SCATTER_AXIS_X_LABEL,
        SCATTER_AXIS_Y_LABEL,
        SCATTER_AXIS_PLACEHOLDER,
        SCATTER_EMPTY_TITLE,
        SCATTER_EMPTY_BODY,
        SCATTER_SPARSE_TITLE,
        SCATTER_SPARSE_BODY,
        // Representative composed lines (the static prefixes/separators are what we audit; registered in
        // the SAME change that introduces them — Pitfall 5). Names shown are clean catalog examples.
        eventLaneSummary("Clots", 3),
        laneTitle("Sleep", "h"),
        seriesReadoutLine("Sleep", "8 h"),
        seriesNotLoggedLine("Sleep"),
        seriesLoggedLine("Clots"),
        scatterCaption(12),
        scatterSummary("Stress", "Sleep", 12),
        scatterAxisTitles("Stress", "Sleep"),
        scatterCellLabel("Stress", "6 of 10", "Sleep", "8 h"),
        scatterCellDays(3),
    )
}
