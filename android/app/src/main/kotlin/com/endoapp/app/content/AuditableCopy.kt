// The registered-copy audit surface — the Kotlin port of the iOS `auditableStrings` mechanism
// (Tests/EndoAppTests/CopyAuditTests.swift; RESEARCH Pitfall 3 / T-18-12).
//
// THE LOAD-BEARING RULE (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`):
//   Every user-facing string is REGISTERED for audit IN THE SAME CHANGE that introduces it.
// An unregistered string silently escapes the `CopyAudit` substring scans, so registration is the
// enforcement — not a convenience. This harness ships NOW (Phase 18-05), before any UI copy exists,
// so Phase 19's first screen copy is audited from birth.
package com.endoapp.app.content

/**
 * Implemented by every copy-owning type in `:app`. Each exposes the fixed strings it can render so the
 * [CopyAuditRegistry] can hand the whole user-facing surface to the `CopyAudit` scans.
 *
 * A copy-owning type (typically a Phase-19+ `object FooCopy : AuditableCopy`) declares its strings in
 * [auditableStrings] AND adds itself to [CopyAuditRegistry.sources] in the SAME change — the two-step
 * registration is what makes the audit non-bypassable.
 */
interface AuditableCopy {
    /** Every fixed user-facing string this type can render, for substring auditing. */
    val auditableStrings: List<String>
}

/**
 * The central registry of every [AuditableCopy] source in `:app`. `CopyAuditTest` scans the union of
 * every source's [AuditableCopy.auditableStrings].
 *
 * TODAY (Phase 18-05) the registry is EMPTY-BUT-PRESENT: no UI copy exists yet, so [sources] is empty.
 * The mechanism ships now so Phase 19's first `object FooCopy : AuditableCopy` registers into [sources]
 * in the same change that introduces the screen — the copy is audited from birth. The `CopyAudit`
 * harness proves it is enforcing (not vacuously green on an empty catalog) via a red-capable self-test
 * that feeds a deliberately-forbidden string through the SAME scan helper.
 */
object CopyAuditRegistry {
    /**
     * Every registered copy source. Phase 19+ APPENDS its `FooCopy` here in the same change that
     * introduces the screen's copy. [ShellCopy] (Plan 02) is the first source — the app-shell + consent
     * CTA strings — registered alongside the screens that render them.
     */
    val sources: List<AuditableCopy> = listOf(
        ShellCopy,
        PlaceholderCopy,
        OnboardingCopy,
        TrackingCopy,
        CheckInCopy,
        DayLogCopy,
        SettingsCopy,
        ReminderCopy,
        HealthConnectCopy,
        ChartsCopy,
        InsightsCopy,
        ExploreCopy,
        ExportImportCopy,
        ReportCopy,
        StoreListingCopy,
    )

    /** The union of every registered source's auditable strings — the surface `CopyAudit` scans. */
    val allAuditableStrings: List<String>
        get() = sources.flatMap { it.auditableStrings }
}
