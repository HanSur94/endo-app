// The registered user-facing copy for the Phase-20 daily check-in reminder — a new `AuditableCopy`
// source in `:app`, appended to [CopyAuditRegistry.sources] in the SAME change (the LOAD-BEARING RULE,
// CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`), so `CopyAuditTest` scans it from birth.
//
// SUBSTRING-GATE NEAR-MISS (extends the Plan-04 "taken" ⊃ "take" reword): the CopyAudit scan is a
// case-insensitive SUBSTRING match against `ClaimsPolicy`. The UI-SPEC §7 specified the quiet-hours
// bound labels as "Quiet hours start" / "Quiet hours end" — but "start" is a forbidden IMPERATIVE
// token ("Quiet hours start" ⊃ "start"), so it would fail `CopyAuditTest`. They are reworded here to
// the range register "Quiet hours from" / "Quiet hours until" (both substring-clean), which reads
// naturally for a start/end time range. Documented as a Rule-1 deviation in the SUMMARY.
//
// Every remaining string is transcribed VERBATIM from UI-SPEC §7 and re-scanned clean (no causal /
// imperative / alarmist / gendered / precision substring). The notification content is DELIBERATELY
// generic — no symptom / flare / streak wording (a lock screen is semi-public, threat T-20-02c).
package com.endoapp.app.content

/**
 * The fixed reminder strings rendered by the Settings reminders section, the notification, and the one
 * notification channel. Exposed as named constants so each site references a single source, and
 * collected in [auditableStrings] for the `CopyAudit` substring scan.
 */
object ReminderCopy : AuditableCopy {

    // --- Settings reminders section ---

    /** Reminders section header. */
    const val REMINDERS = "Reminders"

    /** Master toggle label. */
    const val DAILY_REMINDERS = "Daily reminders"

    /** Reminder-time row label. */
    const val REMINDER_TIME = "Reminder time"

    /** Add-a-time action label. */
    const val ADD_A_TIME = "Add a time"

    /** The time-picker confirm — the picker always SETS (replaces) a time, so "Add" would mislead. */
    const val SAVE_TIME = "Save"

    /** Quiet-hours toggle label. */
    const val QUIET_HOURS = "Quiet hours"

    /**
     * Quiet-hours window START picker label. Reworded off the UI-SPEC §7 "Quiet hours start" because
     * "start" ⊃ the forbidden imperative "start"; "from" carries the same meaning, substring-clean.
     */
    const val QUIET_HOURS_FROM = "Quiet hours from"

    /**
     * Quiet-hours window END picker label. Reworded off the UI-SPEC §7 "Quiet hours end" for parallel
     * range wording with [QUIET_HOURS_FROM]; "until" is substring-clean.
     */
    const val QUIET_HOURS_UNTIL = "Quiet hours until"

    /** Footer shown when reminders are ON. */
    const val REMINDERS_ON_FOOTER =
        "Reminders are on. A gentle nudge to check in, always respecting your quiet hours."

    /** Footer shown when reminders are OFF. */
    const val REMINDERS_OFF_FOOTER =
        "Reminders are off. For a gentle nudge to check in, turn them on above — they always respect " +
            "your quiet hours."

    /** The calm line shown when notification permission was denied. */
    const val DENIED =
        "Notifications are turned off for Endo-App. You can turn them on anytime in Settings."

    /** The action that deep-links to the app's OS notification settings (the denied-state escape). */
    const val OPEN_SETTINGS = "Open Settings"

    // --- Notification + channel (the only notification surface) ---

    /** Notification title (generic; no health wording). */
    const val NOTIFICATION_TITLE = "Daily check-in"

    /** Notification body (generic; no health wording). */
    const val NOTIFICATION_BODY = "Whenever you have a moment, your daily check-in is ready."

    /** The single notification channel's user-visible name. */
    const val CHANNEL_NAME = "Daily check-in"

    /** The single notification channel's user-visible description. */
    const val CHANNEL_DESCRIPTION = "A gentle daily nudge to log your day."

    override val auditableStrings: List<String> = listOf(
        REMINDERS,
        DAILY_REMINDERS,
        REMINDER_TIME,
        ADD_A_TIME,
        SAVE_TIME,
        QUIET_HOURS,
        QUIET_HOURS_FROM,
        QUIET_HOURS_UNTIL,
        REMINDERS_ON_FOOTER,
        REMINDERS_OFF_FOOTER,
        DENIED,
        OPEN_SETTINGS,
        NOTIFICATION_TITLE,
        NOTIFICATION_BODY,
        CHANNEL_NAME,
        CHANNEL_DESCRIPTION,
    )
}
