// The registered "arrives soon" placeholder copy for the three consented-shell tabs — the second
// `AuditableCopy` source in `:app` (after `ShellCopy`). Per the LOAD-BEARING RULE (CLAUDE.md /
// MEMORY `endo-copy-audit-substring-gate`), these nine strings are registered into
// [CopyAuditRegistry.sources] in the SAME change that introduces the placeholders that render them,
// so `CopyAuditTest` scans them from birth. Every string is transcribed verbatim from the
// 19-UI-SPEC §Copywriting curated table and hand-verified against the ClaimsPolicy substring traps
// (notably: "update"/"coming"/"arrives" are clean; no "start"/"use"/"her" host words).
package com.endoapp.app.content

/**
 * The honest placeholder strings the Log / Insights / Explore tabs render THIS phase — an eyebrow +
 * heading + body per tab, marking each tab as "arrives soon" until the owning phase fills it. The
 * real content is deferred per the CONTEXT/UI-SPEC:
 *
 *  - Log      → **Phase 20** (daily tracking).
 *  - Insights → **Phase 22** (insights over the user's own data).
 *  - Explore  → **Phase 22** (charts + overlays).
 *
 * Registered in [CopyAuditRegistry.sources] in THIS change so the placeholder copy is audited from
 * birth. Curated to dodge the ClaimsPolicy forbidden substrings (no causal / imperative / alarmist /
 * gendered / false-precision token).
 */
object PlaceholderCopy : AuditableCopy {

    // --- Log tab (filled in Phase 20) ---

    /** Log placeholder eyebrow. */
    const val LOG_EYEBROW = "Log"

    /** Log placeholder heading. */
    const val LOG_HEADING = "Daily log"

    /** Log placeholder body. */
    const val LOG_BODY = "Your daily tracking arrives in a coming update."

    // --- Insights tab (filled in Phase 22) ---

    /** Insights placeholder eyebrow. */
    const val INSIGHTS_EYEBROW = "Insights"

    /** Insights placeholder heading. */
    const val INSIGHTS_HEADING = "Your patterns"

    /** Insights placeholder body. */
    const val INSIGHTS_BODY = "Insights from your own data appear once you have logged a few days."

    // --- Explore tab (filled in Phase 22) ---

    /** Explore placeholder eyebrow. */
    const val EXPLORE_EYEBROW = "Explore"

    /** Explore placeholder heading. */
    const val EXPLORE_HEADING = "Explore your data"

    /** Explore placeholder body. */
    const val EXPLORE_BODY = "Charts and overlays for your logged data arrive in a coming update."

    override val auditableStrings: List<String> = listOf(
        LOG_EYEBROW,
        LOG_HEADING,
        LOG_BODY,
        INSIGHTS_EYEBROW,
        INSIGHTS_HEADING,
        INSIGHTS_BODY,
        EXPLORE_EYEBROW,
        EXPLORE_HEADING,
        EXPLORE_BODY,
    )
}
