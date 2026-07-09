// The registered user-facing copy for the Phase-19 app shell + consent CTAs — the first
// `AuditableCopy` source in `:app`. Per the LOAD-BEARING RULE (CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), these strings are registered into
// [CopyAuditRegistry.sources] in the SAME change that introduces the screens that render them, so
// `CopyAuditTest` scans them from birth. Every string here is curated verbatim from the
// 19-UI-SPEC §Copywriting curated table and hand-verified against the ClaimsPolicy substring traps.
package com.endoapp.app.content

/**
 * The fixed shell/consent strings rendered by `OnboardingScreen`, `ConsentedShell`, and the
 * `ConsentedTab` labels this phase. Exposed as named constants so the composables reference a single
 * source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 *
 * Onboarding's richer copy (welcome title, disclaimer, privacy, consent explainer) arrives in
 * Plan 04 and registers its own additions here; this object owns only what Plan 02 actually renders.
 */
object ShellCopy : AuditableCopy {

    /** Log tab label. */
    const val TAB_LOG = "Log"

    /** Insights tab label. */
    const val TAB_INSIGHTS = "Insights"

    /** Explore tab label. */
    const val TAB_EXPLORE = "Explore"

    /** Primary consent CTA (the only grant site). */
    const val CONSENT_GRANT = "I consent"

    /** Low-emphasis decline affordance (explicit, fail-closed no-op). */
    const val CONSENT_DECLINE = "Not now"

    /**
     * Temporary withdraw affordance label rendered by the shell so the withdraw-re-gate invariant is
     * exercisable this phase (the real Settings entry is Phase 20). Registered here — the same change
     * that renders it — so it does not escape the CopyAudit substring gate.
     */
    const val WITHDRAW = "Withdraw consent"

    override val auditableStrings: List<String> = listOf(
        TAB_LOG,
        TAB_INSIGHTS,
        TAB_EXPLORE,
        CONSENT_GRANT,
        CONSENT_DECLINE,
        WITHDRAW,
    )
}
