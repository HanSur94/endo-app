// Requirement: INST-01..04 / D-11 / T-08-09 — the auditable chrome-copy surface for the dashboard.
//
// Clones the `CycleSummaryCopy` posture: an `enum` of fixed static strings + an `auditableStrings`
// array that `CopyAuditTests` scans against `ClaimsPolicy.forbiddenVerbs` / `forbiddenGenderedTokens`
// AND a numeric-confidence scan (no "%", "p =", "r =", or bare digit-as-confidence — D-11).
//
// ALL strings here are the CHROME the UI adds around the engine's already-gated `headline`/`detail`
// (Phase 6 `ForbiddenTerms`). The not-medical-advice framing is NOT duplicated here — it is reused
// verbatim via `Disclaimer.text` / `DisclaimerBanner`.
//
// HONESTY NOTES baked into the wording:
//   • Confidence is one of THREE plain-language words only — never a number/percentage (D-11).
//   • Direction uses "trending up/down/steady" — deliberately NOT "higher"/"lower", which contain the
//     gendered substring "her" (ClaimsPolicy.forbiddenGenderedTokens).
//   • The Correlations section reads "Possible associations" (avoids "Correlations" implying causation;
//     matches ClaimsPolicy.allowedFraming "possible association").
import Foundation
import EndoInsights

/// The fixed (non-engine) copy the Insights dashboard chrome can render. Centralized as one auditable
/// surface scanned by `CopyAuditTests` (mirrors `CycleSummaryCopy`).
enum InsightsCopy {

    // MARK: Confidence (plain-language ONLY — D-11, never a number)

    static let confidenceLow = "Low — possible association"
    static let confidenceMedium = "Medium — possible association"
    static let confidenceHigh = "High — possible association"

    /// Map an engine `InsightConfidence` to its plain-language pill word. `.none` collapses to the low
    /// wording (the gate never surfaces a `.none`-confidence card, but the mapping stays total).
    static func confidenceWord(_ confidence: InsightConfidence) -> String {
        switch confidence {
        case .high:   return confidenceHigh
        case .medium: return confidenceMedium
        case .low:    return confidenceLow
        case .none:   return confidenceLow
        }
    }

    // MARK: Confidence explainer (the ⓘ popover on the pill — in-app transparency for the levels)

    // NB: curated against the SUBSTRING audits — no "her" host words (other/clearer/whether…), no
    // "causes"/"will"/"treat…", no %/p=/r= tokens. Deliberately QUALITATIVE (no threshold numbers),
    // so the copy can never drift from the engine's gate constants.
    static let confidenceInfoTitle = "What does this label mean?"
    static let confidenceInfoBody = "Low, Medium, and High describe how much of your own logged data supports a pattern. More logged days — and, for possible associations, a bigger difference between days — move a label from Low toward High. When your data is too sparse, a pattern is not shown at all. The label is descriptive only: it points to a possible association in your logs. It is not proof of a cause, and it is not medical advice."

    // MARK: Section headers (UI-SPEC Component Inventory A / Copywriting Contract)

    static let sectionTrends = "Trends"
    static let sectionCyclePhase = "Cycle phase"
    static let sectionCorrelations = "Possible associations"

    // MARK: Empty / sparse honest states (D-07)

    // NB: the UI-SPEC wording is adjusted so it passes the ClaimsPolicy SUBSTRING audit, which cannot
    // distinguish the gendered "her" from innocuous substrings like "t-her-e" / "her-e", and flags the
    // forbidden verb "will". Meaning is preserved; the audit is the honesty choke-point that wins.
    static let emptyHeading = "Keep logging — insights appear once you have enough data."
    static let emptyBody = "As you log your symptoms and daily factors, the app looks for patterns in your own data and shows them on this screen. Nothing to see yet — keep going."
    static let sparseTitle = "Not enough data yet."
    static let sparseSubtitle = "Keep logging and this fills in over time."

    // MARK: Supporting evidence + window labels (words only — never a coefficient/percentage)

    /// Plain-words evidence line built from `sampleSize` (e.g. "Seen across 12 logged days.").
    static func supportingEvidence(loggedDays: Int) -> String {
        "Seen across \(loggedDays) logged days."
    }

    /// Map a `TrendWindow` to its plain-language label.
    static func windowLabel(_ window: TrendWindow) -> String {
        switch window {
        case .last7:   return "last 7 days"
        case .last30:  return "last 30 days"
        case .last90:  return "last 90 days"
        case .allTime: return "all time"
        }
    }

    static let basisNote = "Based on your logs."

    // MARK: Direction words (descriptive — NEVER "higher"/"lower" → the "her" trap)

    static let directionUp = "trending up"
    static let directionDown = "trending down"
    static let directionSteady = "steady"

    // MARK: Dismiss affordance (a preference, not destruction)

    static let dismissAction = "This isn't right"
    static let dismissConfirmation = "Thanks — we won't show that again."

    // MARK: Load/recompute states

    static let loadError = "We couldn't update your insights just now. Your logs are safe — pull to refresh or try again in a moment."
    static let updating = "Updating…"

    /// A one-line chart text summary so a chart is NEVER the sole carrier of meaning (chart a11y).
    /// e.g. "Pelvic pain severity, last 30 days — trending up."
    static func chartSummary(headline: String, window: TrendWindow, direction: String) -> String {
        "\(headline), \(windowLabel(window)) — \(direction)."
    }

    /// Every fixed string this surface can show — the array `CopyAuditTests` scans. (Engine
    /// headline/detail are NOT here; they are gate-audited in Phase 6 and rendered verbatim.)
    static var auditableStrings: [String] {
        var strings: [String] = [
            confidenceLow, confidenceMedium, confidenceHigh,
            confidenceInfoTitle, confidenceInfoBody,
            sectionTrends, sectionCyclePhase, sectionCorrelations,
            emptyHeading, emptyBody, sparseTitle, sparseSubtitle,
            basisNote,
            directionUp, directionDown, directionSteady,
            dismissAction, dismissConfirmation,
            loadError, updating,
        ]
        strings += InsightConfidence.allCases.map(confidenceWord)
        strings += TrendWindow.allCases.map(windowLabel)
        // A representative supporting-evidence line (the static prefix/suffix is what we audit).
        strings.append(supportingEvidence(loggedDays: 12))
        return strings
    }
}
