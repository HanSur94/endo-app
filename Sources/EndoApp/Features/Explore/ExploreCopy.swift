// Requirement: EXPL-06 / EXPL-07 / RESEARCH Pitfall 3 — the auditable chrome-copy surface for Explore.
//
// Mirrors the `InsightsCopy` posture EXACTLY: an `enum` of fixed `static let` strings + an
// `auditableStrings` array that `CopyAuditTests` scans against `ClaimsPolicy.forbiddenVerbs` /
// `forbiddenGenderedTokens` / numeric-confidence / imperative / alarmist sets. Explore bypasses the
// InsightsEngine/HonestyGate (it shows RAW descriptive data with no confidence pills, no associations,
// no causal framing), so EVERY new chrome string is DESCRIPTIVE-ONLY and must escape NONE of the scans.
//
// LOAD-BEARING (UI-SPEC honesty constraint 5 / RESEARCH Pitfall 3): this enum is REGISTERED in
// `CopyAuditTests` in the SAME change that introduces it, BEFORE any Explore view renders a string —
// an unregistered copy surface silently escapes the ClaimsPolicy substring scans. The lanes/picker/
// states plans (15-04, 15-05) reference these strings + `windowLabel`, NEVER literals.
//
// HONESTY NOTES baked into the wording (all pre-verified against the SUBSTRING audits):
//   • A gap is "Not logged" — never "0", never "No data", never an error tone (reuses the LOCKED
//     dashboard posture `InsightsCopy.scrubNotLogged = "Not logged"`). Absence != zero.
//   • The cap-reached helper says "…then add a new one." — the "another" form was REJECTED because
//     "another" ⊃ "her" (ClaimsPolicy.forbiddenGenderedTokens).
//   • The not-medical-advice framing is NOT duplicated here — it is reused verbatim via
//     `Disclaimer.text` / `DisclaimerBanner`.
//   • `windowLabel` mirrors `InsightsCopy.windowLabel` so the range labels reuse the dashboard
//     vocabulary (EXPL-06).
import Foundation
import EndoInsights

/// The fixed (non-data) chrome copy the Explore screen can render. Centralized as one auditable surface
/// scanned by `CopyAuditTests` (mirrors `InsightsCopy`). Series VALUE readouts are NOT here — they route
/// through `SeriesNormalization.readout(...)` (the real unit, never a normalized number).
enum ExploreCopy {

    // MARK: Empty state — "pick a series" (EXPL-07, the default on first open)

    static let emptyHeading = "Pick a series to begin."
    static let emptyBody = "Choose one or more of your logged series from the chips above to plot them on a shared timeline. Each series keeps its own real scale."

    // MARK: Sparse state — "not enough data" (EXPL-07; matches the dashboard sparse wording, audit-clean)

    static let sparseHeading = "Not enough data yet."
    static let sparseBody = "Keep logging and these lanes fill in over time. Unlogged days stay as gaps — a gap is honest, not a zero."

    // MARK: Picker cap-reached helper (EXPL-02; states the remove-to-add affordance)

    // NB: the "another" form was REJECTED ("another" ⊃ "her" → forbiddenGenderedTokens); the curated
    // "…add a new one." form is substring-clean and states the same remove-to-add affordance.
    static let capReached = "Up to four series at once. Tap a selected chip to remove one, then add a new one."

    // MARK: Chip affordance copy (EXPL-02; a preference, not destruction — no red, no scary confirmation)

    /// The a11y label for the remove-from-timeline affordance on a selected chip.
    static let removeSeries = "Remove series"
    /// The a11y hint for the add-to-timeline affordance on an unselected chip.
    static let addToTimeline = "Add to the timeline."

    // MARK: Scrub tooltip — event lane gap/presence lines (EXPL-05; gap-honest, never "0"/"No data")

    /// The presence-event "logged" value line for the scrubbed day on an event-marker lane.
    static let eventTagged = "Logged"
    /// The gap-day tooltip line — the explicit honest absence, reusing the LOCKED dashboard posture
    /// `InsightsCopy.scrubNotLogged`. Never "0", never "No data", never an error tone. Absence != zero.
    static let notLogged = InsightsCopy.scrubNotLogged

    // MARK: Event-lane a11y summary (WR-03 — the non-gesture VoiceOver path for an event-marker lane)

    /// The a11y text-summary for an event-marker lane. A presence series has NO numeric value, so an
    /// Audio Graph (which implies a value axis) would be LESS honest than a plain count — the summary
    /// plus the per-tick date labels ARE the non-gesture parity with the quantitative lane's Audio
    /// Graph. `{name}` is a catalog `displayName` (already audit-safe); the connective is substring-clean
    /// ("logged on … days" hosts no forbidden token). The count digit is a factual tally, legal exactly
    /// like `InsightsCopy.supportingEvidence`'s "Seen across {n} logged days".
    static func eventLaneSummary(name: String, occurrences: Int) -> String {
        "\(name) — logged on \(occurrences) days"
    }

    // MARK: Scatter chrome (Phase 16 / SCAT-01..04 — the second Explore mode + the deep-link affordance)
    //
    // LOAD-BEARING (UI-SPEC §Copywriting Contract / RESEARCH Pitfall 3): every string here is registered in
    // `auditableStrings` in the SAME change, BEFORE any scatter view renders it — an unregistered scatter
    // surface silently escapes the ClaimsPolicy substring scans. The scatter is the MOST causality-sensitive
    // copy of the milestone, so every string is DESCRIPTIVE-ONLY: no fitted line, no correlation value, no
    // percent-impact, no coefficient (the gated correlation engine remains the ONLY surface allowed to say
    // a possible association; the scatter shows raw points + a factual count caption and nothing more).
    //
    // SUBSTRING TRAPS (pre-verified token-by-token against the ClaimsPolicy scans — UI-SPEC §Copywriting):
    //   • "where" was REJECTED (w-her-e ⊃ "her", forbiddenGenderedTokens) → "when both were logged".
    //   • "either" was REJECTED (eit-her ⊃ "her") → "missing a factor".
    //   • "together" was REJECTED (to·get·her ⊃ "her") → "on too few of the same days".
    //   • "compared with" / "behind" / "overlapping" host NO "her"/"she"/"women" run (verified).
    // The {N}/{days} count digit is a FACTUAL tally (legal exactly like `eventLaneSummary` /
    // `InsightsCopy.supportingEvidence`) — never a statistic.

    /// The `Lanes` segment of the `Lanes | Scatter` mode toggle (SCAT-01).
    static let modeLanes = "Lanes"
    /// The `Scatter` segment of the mode toggle (SCAT-01).
    static let modeScatter = "Scatter"
    /// The placeholder shown on an axis selector before a series is chosen (SCAT-01).
    static let scatterAxisPlaceholder = "Pick a factor"
    /// The scatter empty-state heading — neither axis chosen yet (SCAT-03).
    static let scatterEmptyHeading = "Pick two factors to compare."
    /// The scatter empty-state body — states the both-logged-day basis + absence ≠ zero (SCAT-03).
    static let scatterEmptyBody = "Choose a factor for each axis. Each dot is one day when both were logged — days missing a factor are left out, never counted as zero."
    /// The scatter sparse-state heading — too few both-logged days to plot (SCAT-03).
    static let scatterSparseHeading = "Not enough overlapping days yet."
    /// The scatter sparse-state body — honest "keep logging" framing (SCAT-03). NB: "together" was rejected
    /// (⊃ "her"); the FINAL wording is "on too few of the same days" (substring-clean).
    static let scatterSparseBody = "These two factors were logged on too few of the same days to plot. Keep logging and this fills in over time."
    /// The correlation-card deep-link affordance label (SCAT-04). Non-causal — points at the raw evidence
    /// days, never restates or strengthens the association.
    static let scatterLinkLabel = "See the days behind this"

    /// The neutral caption beneath the plot (SCAT-03). `{N}` is the both-logged-day tally (a factual count,
    /// same posture as `eventLaneSummary`), NEVER a statistic. "when both were logged" hosts no forbidden
    /// token (the "where" form was rejected: w-her-e ⊃ "her").
    static func scatterCaption(days: Int) -> String {
        "Based on \(days) days when both were logged."
    }

    /// The one-line worded text-summary that gives a VoiceOver user the point count + both axis names + the
    /// both-logged basis WITHOUT the plot (SCAT-03 a11y — the primary non-gesture path). "compared with" is
    /// descriptive, non-causal (never "{X} against {Y}", which would read cause→effect). `{X}`/`{Y}` are
    /// catalog `displayName`s (already audit-safe); the count digit is a factual tally.
    static func scatterSummary(x: String, y: String, days: Int) -> String {
        "\(x) compared with \(y), \(days) days when both were logged."
    }

    /// The per-point (per-cell) readout for one occupied `(x, y)` cell (SCAT-02 a11y). `{N}` is the cell's
    /// both-logged-day count (a factual tally); the value parts come from `SeriesNormalization.readout` at
    /// the call site (real unit, never a normalized number / statistic).
    static func scatterPointReadout(days: Int, xName: String, xReadout: String, yName: String, yReadout: String) -> String {
        "\(days) days — \(xName) \(xReadout), \(yName) \(yReadout)"
    }

    /// The per-mark VoiceOver `.accessibilityLabel` for one occupied `(x, y)` cell (SCAT-02 a11y). Carries
    /// ONLY the two real-unit readouts; the cell's both-logged-day count rides separately in the mark's
    /// `.accessibilityValue` (`"{N} days"`), so this label form omits the count by design. `{xName}`/`{yName}`
    /// are catalog `displayName`s (already audit-safe) and the readouts come from `SeriesNormalization.readout`
    /// at the call site (real unit, never a normalized number / statistic). The `", "` / `" "` connectives
    /// host no forbidden token — descriptive-only, no coefficient, no percent-impact. Replaces the previously
    /// inline `ScatterPlotView.markLabel` literals so the actually-rendered string passes the ClaimsPolicy
    /// audit instead of bypassing it.
    static func scatterMarkLabel(xName: String, xReadout: String, yName: String, yReadout: String) -> String {
        "\(xName) \(xReadout), \(yName) \(yReadout)"
    }

    // MARK: Range-preset labels (EXPL-06 — maps TrendWindow, reusing the dashboard vocabulary)

    /// Map a `TrendWindow` to its plain-language label. Mirrors `InsightsCopy.windowLabel` EXACTLY so the
    /// range-preset control reuses the dashboard wording (EXPL-06).
    static func windowLabel(_ window: TrendWindow) -> String {
        switch window {
        case .last7:   return "last 7 days"
        case .last30:  return "last 30 days"
        case .last90:  return "last 90 days"
        case .allTime: return "all time"
        }
    }

    /// Every fixed string this surface can show — the array `CopyAuditTests` scans. Series value
    /// readouts are NOT here (they come from `SeriesNormalization.readout(...)`, real unit). Flattens
    /// every fixed chrome string PLUS the four `windowLabel`s (registered in the SAME change that
    /// introduces the enum — RESEARCH Pitfall 3).
    static var auditableStrings: [String] {
        var strings: [String] = [
            emptyHeading, emptyBody,
            sparseHeading, sparseBody,
            capReached,
            removeSeries, addToTimeline,
            eventTagged, notLogged,
            // Phase 16 scatter chrome (SCAT-01..04) — registered in the SAME change that introduces it
            // (RESEARCH Pitfall 3): every fixed scatter string is scanned by the six allExploreCopy scans.
            modeLanes, modeScatter,
            scatterAxisPlaceholder,
            scatterEmptyHeading, scatterEmptyBody,
            scatterSparseHeading, scatterSparseBody,
            scatterLinkLabel,
        ]
        strings += TrendWindow.allCases.map(windowLabel)
        // A representative event-lane summary (the static prefix/suffix is what we audit; the name +
        // count are interpolated — same posture as InsightsCopy.supportingEvidence).
        strings.append(eventLaneSummary(name: "Series", occurrences: 3))
        // Representative instances of the interpolated scatter forms — the static prefix/suffix is what we
        // audit (the name + count are interpolated), same posture as `eventLaneSummary`.
        strings.append(scatterCaption(days: 3))
        strings.append(scatterSummary(x: "Stress", y: "Pain", days: 3))
        strings.append(scatterPointReadout(days: 3, xName: "Stress", xReadout: "5 of 10",
                                           yName: "Pain", yReadout: "7 of 10"))
        strings.append(scatterMarkLabel(xName: "Stress", xReadout: "5 of 10",
                                        yName: "Pain", yReadout: "7 of 10"))
        return strings
    }
}
