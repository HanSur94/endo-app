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
        ]
        strings += TrendWindow.allCases.map(windowLabel)
        return strings
    }
}
