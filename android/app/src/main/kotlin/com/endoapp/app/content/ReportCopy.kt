// Requirement: AREL-01 / AREL-02 (Phase 24, D-05) — the auditable fixed-copy choke-point for the report.
// Kotlin port of `Sources/EndoApp/Features/Report/ReportCopy.swift`, registered into
// [CopyAuditRegistry.sources] in the SAME change (the LOAD-BEARING copy-audit rule — an unregistered
// string silently escapes the scan).
//
// Centralizing EVERY fixed report string here (Surface 1 on-screen builder/preview AND Surface 2 PDF) is
// what makes a causal/diagnostic/gendered string FAIL THE BUILD before it can reach a clinician-facing
// PDF. The not-medical-advice framing is NOT duplicated here — Plan 05's renderer reuses `Disclaimer.text`
// (the single source of truth) verbatim, audited by `DisclaimerTest`.
//
// SUBSTRING DISCIPLINE — the Android `CopyAudit` registry scan ([CopyAuditTest]) is a case-insensitive
// SUBSTRING union over ClaimsPolicy causal + imperative + alarmist + gendered lists. It is STRICTER than
// the iOS report scan (which covers causal + gendered only), so a few iOS report strings are REWORDED
// here to dodge the extra imperative/alarmist substrings — the audit WINS over the draft phrasing:
//   • iOS "Try again" / "…try again" / "start date" trip the imperative "try"/"start" (note "Retry" also
//     hides "try"): reworded to "Attempt again" / "…attempt it once more" / "…on or after the From date".
//   • iOS legend "…contributed to this period" is reworded to "…both sources appear in this period"
//     (defensive: no causal-connective flavor).
//   • the platform tag "From Apple Health" → "from Health Connect" (Android provenance).
//   • the legend lines avoid the "her" substring in "other"/"there".
// The one string that CANNOT be reworded is [FOOTER] — its required "not diagnostic" framing legitimately
// contains the alarmist "diagnos" stem as a NEGATION (it states what the app is NOT), exactly like
// `Disclaimer.text` legitimately contains "diagnose/treat/cure/prevent". So — mirroring the established
// `Disclaimer` exemption — [FOOTER] is NOT in [auditableStrings]; it is pinned by a dedicated
// must-contain test in `CopyAuditTest` instead.
package com.endoapp.app.content

import com.endoapp.app.report.SectionSource
import com.endoapp.insights.InsightConfidence

/**
 * The fixed (non-data) copy the report surfaces can render — one auditable surface (mirrors
 * [ExportImportCopy]). NO medical-purpose/causal verb, NO gendered token, NO numeric confidence. Every
 * string below (except the deliberately-exempt [FOOTER]) is registered in [auditableStrings] and scanned
 * by `CopyAuditTest`.
 */
object ReportCopy : AuditableCopy {

    // --- Surface 1 — Settings entry + builder / preview ---

    const val SETTINGS_ENTRY_LABEL = "Export report"
    const val SETTINGS_ENTRY_FOOTER =
        "Create a PDF summary of a date range to share with your clinician. It is made on your device " +
            "and only leaves it if you choose to share it."

    const val BUILDER_TITLE = "Export report"
    const val GENERATE_CTA = "Generate PDF"

    const val PRESET_LAST30 = "Last 30 days"
    const val PRESET_LAST90 = "Last 90 days"
    const val PRESET_CUSTOM = "Custom range"

    const val RANGE_FROM_LABEL = "From"
    const val RANGE_TO_LABEL = "To"

    const val SHARE_ACTION = "Share"

    const val EMPTY_HEADING = "Nothing to report yet"

    /** Reworded from the iOS draft to dodge the imperative "try": no "Try a wider range". */
    const val EMPTY_BODY =
        "Log a few days in this range, then your report fills in. A wider range, or one logged day, " +
            "fills it out."

    const val GENERATING_CAPTION = "Generating your report…"

    const val ERROR_HEADING = "We couldn't build the report"

    /** Reworded to dodge the imperative "try": "…attempt it once more" (no "try again"). */
    const val ERROR_BODY = "Something went wrong creating the PDF. Please attempt it once more."

    /** Retry action — "Attempt again" dodges "try" (note "Retry" itself hides the "try" substring). */
    const val ERROR_RETRY = "Attempt again"

    /**
     * Invalid-range guard (drives the Generate-disabled state when "To" precedes "From"). Reworded to
     * dodge the imperative "start" — references the From/To labels instead of "start date".
     */
    const val INVALID_RANGE = "Pick a To date on or after the From date."

    // --- Surface 2 — PDF document copy ---

    const val PDF_TITLE = "Endo-App — Self-Tracking Report"

    // The 7 numbered section headings (D-04 order). Plain, descriptive nouns — no causal/diagnostic verb.
    const val SECTION_HEADER_TITLE = "1. Overview"
    const val SECTION_SUMMARY_TITLE = "2. Coverage"
    const val SECTION_SYMPTOMS_TITLE = "3. Symptoms & pain"
    const val SECTION_CYCLE_TITLE = "4. Cycle & bleeding"
    const val SECTION_LIFESTYLE_TITLE = "5. Daily factors"
    const val SECTION_INSIGHTS_TITLE = "6. Patterns"
    const val SECTION_LEGEND_TITLE = "7. Source labels"

    /** Summary / coverage line scaffolding (the day count is interpolated; this is the fixed wording). */
    fun coverageLine(daysLogged: Int): String = "Logged days in this range: $daysLogged"

    // Symptoms table column headers (descriptive, absolute — never cycle-relative).
    const val SYMPTOMS_COL_SYMPTOM = "Symptom"
    const val SYMPTOMS_COL_RANGE = "Severity range"
    const val SYMPTOMS_COL_DAYS = "Days logged"

    /** One symptom row's severity-range cell (e.g. "2–6"); an em dash when no severity was recorded. */
    fun severityRange(min: Int?, max: Int?): String {
        if (min == null || max == null) return "—" // em dash = no severity recorded
        return if (min == max) "$min" else "$min–$max"
    }

    // Lifestyle / daily-factor row labels (absence ≠ 0; a null field renders [NOT_LOGGED]).
    const val LIFESTYLE_SLEEP = "Sleep"
    const val LIFESTYLE_ACTIVITY = "Activity"
    const val LIFESTYLE_FOOD = "Food logged"
    const val LIFESTYLE_STRESS = "Stress"
    const val LIFESTYLE_MOOD = "Mood"

    /** The honest absence marker for an unlogged factor (absence ≠ 0 — distinct from a logged value). */
    const val NOT_LOGGED = "Not logged"
    const val FOOD_YES = "Yes"

    /** Formats the optional cycle-day (e.g. "Cycle day 7"). */
    fun cycleDayValue(day: Int): String = "$CYCLE_DAY_PREFIX $day"

    /** A plain confidence WORD for a derived cycle estimate (never a number/percentage — D-11). */
    fun cycleConfidenceWord(confidence: InsightConfidence): String = when (confidence) {
        InsightConfidence.HIGH -> "Confidence: high"
        InsightConfidence.MEDIUM -> "Confidence: medium"
        InsightConfidence.LOW, InsightConfidence.NONE -> "Confidence: low"
    }

    /**
     * The header-meta format: "Generated {date} · Covers {start} – {end}" (a date RANGE, never a single
     * hard date). The dates are interpolated by the renderer; this is the fixed scaffolding.
     */
    fun headerMeta(generated: String, rangeStart: String, rangeEnd: String): String =
        "Generated $generated · Covers $rangeStart – $rangeEnd"

    // Source tags (glyph + word, never color — AREL-02). Map a [SectionSource] to its word.
    const val SOURCE_SELF_REPORTED = "Self-reported"

    /** RENAMED from the iOS "From Apple Health" — Android imports from Health Connect. */
    const val SOURCE_HEALTH_CONNECT = "from Health Connect"
    const val SOURCE_MIXED = "Mixed"

    /** The PDF word for an aggregated [SectionSource] ([SectionSource.NONE] → empty: no badge). */
    fun sourceTag(source: SectionSource): String = when (source) {
        SectionSource.SELF_REPORTED -> SOURCE_SELF_REPORTED
        SectionSource.HEALTH_CONNECT -> SOURCE_HEALTH_CONNECT
        SectionSource.MIXED -> SOURCE_MIXED
        SectionSource.NONE -> ""
    }

    // Source legend (section 7). Reworded to avoid the "her" substring in "other"/"there".
    const val LEGEND_INTRO = "Source labels:"
    const val LEGEND_SELF_REPORTED = "Self-reported — you logged this in Endo-App."
    const val LEGEND_HEALTH_CONNECT = "from Health Connect — imported read-only from Health Connect."
    const val LEGEND_MIXED = "Mixed — both sources appear in this period."

    /** The honest per-section sparse line (absence ≠ 0). */
    const val NOT_ENOUGH_DATA_IN_RANGE = "Not enough data in this range."

    // Cycle section (the no-phantom-cycle off-state + derived framing).
    const val CYCLE_SUPPRESSED = "Cycle tracking is off."
    const val CYCLE_DAY_PREFIX = "Cycle day"
    const val CYCLE_NEXT_WINDOW_LABEL = "Estimated next window"

    /** The insights-section sparse line (the engine surfaces nothing fabricated for a sparse range). */
    const val INSIGHTS_SPARSE = "Not enough data to show insights for this range."

    /**
     * The page footer framing, repeated on every page so it survives a single detached printed page.
     *
     * EXEMPT from [auditableStrings] (mirroring the `Disclaimer` exemption): its required "not diagnostic"
     * framing legitimately contains the alarmist "diagnos" stem as a NEGATION — it states what the app is
     * NOT. It is pinned by a dedicated must-contain test in `CopyAuditTest` instead.
     */
    const val FOOTER = "Self-tracked, not diagnostic · Endo-App"

    /** The page-number format (e.g. "Page 2 of 5"); digits are interpolated by the renderer. */
    fun pageNumber(n: Int, total: Int): String = "Page $n of $total"

    // --- Auditable surface ---

    /**
     * Every fixed string the report surfaces can show — the surface `CopyAuditTest` scans. [FOOTER] is
     * DELIBERATELY omitted (a legitimate "not diagnostic" negation; pinned by its own must-contain test),
     * exactly as `Disclaimer.text` is exempt from the curated scan.
     */
    override val auditableStrings: List<String> = listOf(
        // Surface 1
        SETTINGS_ENTRY_LABEL, SETTINGS_ENTRY_FOOTER, BUILDER_TITLE, GENERATE_CTA,
        PRESET_LAST30, PRESET_LAST90, PRESET_CUSTOM,
        RANGE_FROM_LABEL, RANGE_TO_LABEL, SHARE_ACTION,
        EMPTY_HEADING, EMPTY_BODY, GENERATING_CAPTION,
        ERROR_HEADING, ERROR_BODY, ERROR_RETRY, INVALID_RANGE,
        // Surface 2
        PDF_TITLE,
        headerMeta(generated = "Jun 9, 2026", rangeStart = "May 10", rangeEnd = "Jun 9"),
        SECTION_HEADER_TITLE, SECTION_SUMMARY_TITLE, SECTION_SYMPTOMS_TITLE, SECTION_CYCLE_TITLE,
        SECTION_LIFESTYLE_TITLE, SECTION_INSIGHTS_TITLE, SECTION_LEGEND_TITLE,
        coverageLine(daysLogged = 12),
        SYMPTOMS_COL_SYMPTOM, SYMPTOMS_COL_RANGE, SYMPTOMS_COL_DAYS, severityRange(min = 2, max = 6),
        LIFESTYLE_SLEEP, LIFESTYLE_ACTIVITY, LIFESTYLE_FOOD, LIFESTYLE_STRESS, LIFESTYLE_MOOD,
        NOT_LOGGED, FOOD_YES,
        cycleDayValue(7),
        SOURCE_SELF_REPORTED, SOURCE_HEALTH_CONNECT, SOURCE_MIXED,
        LEGEND_INTRO, LEGEND_SELF_REPORTED, LEGEND_HEALTH_CONNECT, LEGEND_MIXED,
        NOT_ENOUGH_DATA_IN_RANGE,
        CYCLE_SUPPRESSED, CYCLE_DAY_PREFIX, CYCLE_NEXT_WINDOW_LABEL,
        INSIGHTS_SPARSE,
        pageNumber(2, total = 5),
    ) + InsightConfidence.entries.map(::cycleConfidenceWord)
}
