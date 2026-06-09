// Requirement: RPRT-01 / RPRT-02 (Phase 10, D-05) — the auditable fixed-copy choke-point for the report.
//
// Clones the `CycleSummaryCopy` / `InsightsCopy` posture: an `enum` of fixed static strings + an
// `auditableStrings` array `CopyAuditTests` scans against `ClaimsPolicy.forbiddenVerbs` /
// `forbiddenGenderedTokens` AND a numeric-confidence scan. Centralizing EVERY fixed report string here
// (Surface 1 on-screen builder/preview AND Surface 2 PDF) is what makes a causal/diagnostic/gendered
// string FAIL THE BUILD before it can reach a clinician-facing PDF (the T-10-03 mitigation).
//
// The not-medical-advice framing is NOT duplicated here — Plan 02's renderer reuses `Disclaimer.text`
// (the single source of truth) verbatim, never a retyped copy.
//
// SUBSTRING DISCIPLINE (mirrors InsightsCopy's notes): the audit is a SUBSTRING scan that cannot tell
// the gendered "her" from innocuous "t-her-e"/"ot-her", and flags the forbidden verb "will". Wording is
// chosen so it passes the audit — the audit WINS over the UI-SPEC draft phrasing (UI-SPEC §Copywriting
// Contract explicitly defers final wording to this enum). Reworded vs. the UI-SPEC draft:
//   • empty-state body avoids "there's"/"here's"/"will fill" → "Log a few days in this range, then your
//     report fills in." ("report" / "your" carry no forbidden substring).
//   • legend avoids "other"/"there" ("ot-her"/"t-her-e" trip the "her" scan).
import Foundation

/// The fixed (non-data) copy the report surfaces can render — one auditable surface (mirrors
/// `InsightsCopy`). NO medical-purpose/causal verb, NO gendered token, NO numeric confidence.
enum ReportCopy {

    // MARK: Surface 1 — Settings entry + builder/preview (UI-SPEC §Copywriting Contract)

    static let settingsEntryLabel = "Export report"
    static let settingsEntryFooter =
        "Create a PDF summary of a date range to share with your clinician. It is made on your device and only leaves it if you choose to share it."

    static let builderTitle = "Export report"
    static let generateCTA = "Generate PDF"

    static let presetLast30 = "Last 30 days"
    static let presetLast90 = "Last 90 days"
    static let presetCustom = "Custom range"

    static let rangeFromLabel = "From"
    static let rangeToLabel = "To"

    static let shareAction = "Share"

    static let emptyHeading = "Nothing to report yet"
    /// Reworded from the UI-SPEC draft to pass the substring audit (no "there's"/"here's"/"will fill").
    static let emptyBody =
        "Log a few days in this range, then your report fills in. Try a wider range, or log a day first."

    static let generatingCaption = "Generating your report…"

    static let errorHeading = "We couldn't build the report"
    static let errorBody = "Something went wrong creating the PDF. Please try again."
    static let errorRetry = "Try again"

    /// Invalid-range guard (drives the Generate-disabled state in Plan 03 when "To" precedes "From").
    static let invalidRange = "Pick an end date on or after the start date."

    // MARK: Surface 2 — PDF document copy (UI-SPEC §Copywriting Contract / §Honesty Contract)

    static let pdfTitle = "Endo-App — Self-Tracking Report"

    // The 8 numbered section headings (D-04 order). Plain, descriptive nouns — no causal/diagnostic verb.
    static let sectionHeaderTitle = "1. Overview"
    static let sectionSummaryTitle = "2. Coverage"
    static let sectionSymptomsTitle = "3. Symptoms & pain"
    static let sectionCycleTitle = "4. Cycle & bleeding"
    static let sectionLifestyleTitle = "5. Daily factors"
    static let sectionInsightsTitle = "6. Patterns"
    static let sectionLegendTitle = "7. Source labels"

    // Summary / coverage line scaffolding (the day count is interpolated; this is the fixed wording).
    static func coverageLine(daysLogged: Int) -> String {
        "Logged days in this range: \(daysLogged)"
    }

    // Symptoms table column headers (descriptive, absolute — never cycle-relative).
    static let symptomsColSymptom = "Symptom"
    static let symptomsColRange = "Severity range"
    static let symptomsColDays = "Days logged"
    /// One symptom row's severity-range cell (e.g. "2–6"); a dash when no severity was recorded.
    static func severityRange(min: Int?, max: Int?) -> String {
        guard let min, let max else { return "\u{2014}" }   // em dash = no severity recorded
        return min == max ? "\(min)" : "\(min)\u{2013}\(max)"
    }

    // Lifestyle / daily-factor row labels (absence ≠ 0; a nil field renders `notLogged`).
    static let lifestyleSleep = "Sleep"
    static let lifestyleActivity = "Activity"
    static let lifestyleFood = "Food logged"
    static let lifestyleStress = "Stress"
    static let lifestyleMood = "Mood"
    /// The honest absence marker for an unlogged factor (absence ≠ 0 — distinct from a logged value).
    static let notLogged = "Not logged"
    static let foodYes = "Yes"

    // Cycle section derived framing. `cycleDayValue` formats the optional cycle-day; the next-window is a
    // DATE RANGE the renderer interpolates around `cycleNextWindowLabel`.
    static func cycleDayValue(_ day: Int) -> String { "\(cycleDayPrefix) \(day)" }
    /// A plain confidence WORD for a derived cycle estimate (never a number/percentage — D-11).
    static func cycleConfidenceWord(_ confidence: Confidence) -> String {
        switch confidence {
        case .high:   return "Confidence: high"
        case .medium: return "Confidence: medium"
        case .low:    return "Confidence: low"
        case .none:   return "Confidence: low"
        }
    }
    /// The header-meta format: "Generated {date} · Covers {start} – {end}" (a date RANGE, never a single
    /// hard date). The dates are interpolated by the renderer; this is the fixed scaffolding.
    static func headerMeta(generated: String, rangeStart: String, rangeEnd: String) -> String {
        "Generated \(generated) \u{00B7} Covers \(rangeStart) \u{2013} \(rangeEnd)"
    }

    // Source tags (glyph + word, never color — RPRT-02). Map a SectionSource to its word.
    static let sourceSelfReported = "Self-reported"
    static let sourceAppleHealth = "From Apple Health"
    static let sourceMixed = "Mixed"

    /// The PDF word for an aggregated `SectionSource` (`.none` → empty: no badge, SC3 honesty).
    static func sourceTag(_ source: SectionSource) -> String {
        switch source {
        case .selfReported: return sourceSelfReported
        case .appleHealth:  return sourceAppleHealth
        case .mixed:        return sourceMixed
        case .none:         return ""
        }
    }

    // Source legend (section 7). Reworded to avoid the "her" substring in "other"/"there".
    static let legendIntro = "Source labels:"
    static let legendSelfReported = "Self-reported — you logged this in Endo-App."
    static let legendAppleHealth = "From Apple Health — imported read-only from Apple Health."
    static let legendMixed = "Mixed — both sources contributed to this period."

    /// The honest per-section sparse line (mirrors `CycleSummaryCopy.notEnoughData`; absence ≠ 0).
    static let notEnoughDataInRange = "Not enough data in this range."

    // Cycle section (mirrors CycleSummaryCopy — the no-phantom-cycle off-state + derived framing).
    static let cycleSuppressed = "Cycle tracking is off."
    static let cycleDayPrefix = "Cycle day"
    static let cycleNextWindowLabel = "Estimated next window"

    /// The insights-section sparse line (the engine surfaces nothing fabricated for a sparse range).
    static let insightsSparse = "Not enough data to show insights for this range."

    /// The page footer framing, repeated on every page so it survives a single detached printed page.
    static let footer = "Self-tracked, not diagnostic \u{00B7} Endo-App"
    /// The page-number format (e.g. "Page 2 of 5"); digits are interpolated by the renderer.
    static func pageNumber(_ n: Int, of total: Int) -> String { "Page \(n) of \(total)" }

    // MARK: - Auditable surface

    /// Every fixed string the report surfaces can show — the array `CopyAuditTests` scans. (The
    /// disclaimer prose is NOT here; Plan 02 reuses `Disclaimer.text` verbatim and it is audited by
    /// `DisclaimerTests`.)
    static var auditableStrings: [String] {
        [
            // Surface 1
            settingsEntryLabel, settingsEntryFooter, builderTitle, generateCTA,
            presetLast30, presetLast90, presetCustom,
            rangeFromLabel, rangeToLabel, shareAction,
            emptyHeading, emptyBody, generatingCaption,
            errorHeading, errorBody, errorRetry, invalidRange,
            // Surface 2
            pdfTitle,
            headerMeta(generated: "Jun 9, 2026", rangeStart: "May 10", rangeEnd: "Jun 9"),
            sectionHeaderTitle, sectionSummaryTitle, sectionSymptomsTitle, sectionCycleTitle,
            sectionLifestyleTitle, sectionInsightsTitle, sectionLegendTitle,
            coverageLine(daysLogged: 12),
            symptomsColSymptom, symptomsColRange, symptomsColDays, severityRange(min: 2, max: 6),
            lifestyleSleep, lifestyleActivity, lifestyleFood, lifestyleStress, lifestyleMood,
            notLogged, foodYes,
            cycleDayValue(7),
            sourceSelfReported, sourceAppleHealth, sourceMixed,
            legendIntro, legendSelfReported, legendAppleHealth, legendMixed,
            notEnoughDataInRange,
            cycleSuppressed, cycleDayPrefix, cycleNextWindowLabel,
            insightsSparse,
            footer, pageNumber(2, of: 5),
        ]
        + Confidence.allCases.map(cycleConfidenceWord)
    }
}
