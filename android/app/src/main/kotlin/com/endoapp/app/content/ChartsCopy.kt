// The registered chart-chrome copy — a new `AuditableCopy` source in `:app`, appended to
// [CopyAuditRegistry.sources] in the SAME change (the LOAD-BEARING RULE, CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth.
//
// This owns ONLY the fixed strings the trend chart's scrub TOOLTIP renders. The engine
// headline/detail are gate-audited in `:insights` (rendered verbatim) and are NOT re-registered here.
//
// SUBSTRING-GATE NOTE (Pitfall 2 — the CopyAudit scan is a case-insensitive SUBSTRING match against
// `ClaimsPolicy`): every string below is re-scanned clean.
//   • "Not logged" — no causal / imperative / alarmist / gendered / precision substring.
//   • "Severity" — s-e-v-e-r-i-t-y hosts no forbidden substring (notably NOT the gendered "her").
//   • "Severity {n} of 10" — a factual readout; digits in a factual line are legal (the bare-digit
//     scan is scoped to the three confidence words, D-11).
// These mirror the iOS `InsightsCopy.scrubNotLogged` / `scrubSeverity(_:)` (LOCKED wording).
package com.endoapp.app.content

import com.endoapp.insights.ScaleKind
import com.endoapp.insights.SeriesNormalization

/**
 * The fixed chart-scrub tooltip strings the trend chart renders. Exposed as named members so each render
 * site references a single source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 */
object ChartsCopy : AuditableCopy {

    /** The gap-day tooltip line — the explicit honest absence (CONTEXT "Gap-Ehrlichkeit"): never "0",
     *  never "No data", never an error tone. The gap is information, not an error. Mirrors the iOS
     *  `InsightsCopy.scrubNotLogged`. */
    const val NOT_LOGGED = "Not logged"

    /** The severity readout prefix. The value + unit ("{n} of 10") is composed via the honest
     *  [SeriesNormalization.readout] so a whole number renders without a trailing ".0". */
    const val SEVERITY = "Severity"

    /** The fixed real-unit suffix for the `0…10` severity domain. */
    const val OF_TEN_UNIT = "of 10"

    /**
     * The logged-day tooltip value line, e.g. "Severity 6 of 10". The REAL value + unit routes through
     * the honest [SeriesNormalization.readout] (never the normalized 0…1 number, H4); a literal
     * severity-0 day reads "Severity 0 of 10" — textually distinct from [NOT_LOGGED] (absence ≠ 0, H2).
     */
    fun severityReadout(value: Double): String =
        "$SEVERITY ${SeriesNormalization.readout(value, kind = ScaleKind.ORDINAL_0_TO_10, unit = OF_TEN_UNIT)}"

    override val auditableStrings: List<String> = listOf(
        NOT_LOGGED,
        SEVERITY,
        OF_TEN_UNIT,
        // A representative logged readout (the static prefix/suffix is what we audit; registered in the
        // SAME change that introduces the string — Pitfall 5).
        severityReadout(6.0),
    )
}
