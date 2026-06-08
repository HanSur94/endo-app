// Requirement: CYCL-02 / CYCL-03 / CYCL-04 — the home cycle-summary slot (D-08/D-09/D-10).
//
// Fills HomeView's reserved `home.cycleSummary` slot. It reads `Repository.bleedingDays()` +
// `isSuppressed()`, feeds them into the PURE `CycleMath.summarize(...today: .now)` (Plan 03), and
// renders exactly three states — NEVER a single hard date, NEVER a 28-based default (the
// no-phantom-cycle contract):
//
//   1. suppressed         → ONLY a neutral "cycle tracking is off" line (`home.cycleSummary.suppressed`);
//                            no cycle-day, no range, no phase.
//   2. !hasEnoughData     → "not enough data yet" (`home.cycleSummary.notEnoughData`).
//   3. derived            → cycle-day + a next-period date RANGE + a plain-language confidence word.
//
// Copy posture (D-14 / Pitfall 5): the cycle noun resolves through `Terminology`; every fixed string
// lives in the auditable `CycleSummaryCopy` surface, scanned by CopyAuditTests against
// `ClaimsPolicy.forbiddenVerbs` / `forbiddenGenderedTokens` and for hard single-date phrasing. The
// next-period estimate is formatted as a DATE RANGE (never a single date) and confidence as a plain
// word ("strong/medium/low confidence" — never "predicted").
//
// MV pattern (D-12): NO ViewModel — the view builds a SwiftDataRepository from the injected context.
import SwiftUI
import SwiftData

/// The fixed (non-data) copy this view can render. Centralized so CopyAuditTests can scan it as one
/// auditable surface (mirrors the Domain `plainLabel` audit). NO medical-purpose verb, NO gendered
/// token, NO hard single-date phrasing.
enum CycleSummaryCopy {
    static let suppressed = "Cycle tracking is off."
    static let notEnoughData = "Not enough data yet to estimate your cycle."
    static let cycleDayPrefix = "Cycle day"
    static let nextWindowLabel = "Estimated next window"
    static let basisNote = "Based on your logs."

    static func confidenceWord(_ confidence: Confidence) -> String {
        // NB: avoid "higher"/"lower" — they contain the gendered substring "her" (ClaimsPolicy).
        switch confidence {
        case .high:   return "strong confidence"
        case .medium: return "medium confidence"
        case .low:    return "low confidence"
        case .none:   return "low confidence"
        }
    }

    /// Every fixed string this view can show — the surface CopyAuditTests scans.
    static var auditableStrings: [String] {
        [suppressed, notEnoughData, cycleDayPrefix, nextWindowLabel, basisNote]
            + Confidence.allCases.map(confidenceWord)
    }
}

struct CycleSummaryView: View {
    /// Observe the underlying records DIRECTLY so SwiftUI establishes a dependency: the summary then
    /// recomputes automatically after any log / edit / delete OR a suppressed-mode flip (D-06). The
    /// derivation below reads these `@Query` results (not a fresh Repository) so the dependency is
    /// actually registered during `body` evaluation.
    @Query private var bleedingEvents: [SchemaV1.BleedingEvent]
    @Query private var treatmentStates: [SchemaV1.TreatmentState]

    private var summary: CycleSummary {
        let cal = Calendar.current
        // Distinct calendar days that carry a flow-bearing bleeding event.
        let days = Set(
            bleedingEvents
                .filter { FlowIntensity(raw: $0.flowRaw) != nil }
                .map { cal.startOfDay(for: $0.occurredAt) }
        )
        // Latest treatment state decides suppressed / no-cycle mode (CYCL-03).
        let suppressed = treatmentStates
            .max(by: { $0.startedAt < $1.startedAt })?
            .modeRaw == "suppressed"
        return CycleMath.summarize(
            bleedingDays: Array(days), isSuppressed: suppressed, today: .now)
    }

    var body: some View {
        // The container carries the `home.cycleSummary` slot id; children keep their own ids
        // (`.suppressed` / `.notEnoughData` / `.derived`) so the E2E can assert each state distinctly.
        VStack(alignment: .leading, spacing: 4) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("home.cycleSummary")
    }

    @ViewBuilder
    private var content: some View {
        let summary = self.summary
        if summary.isSuppressed {
            // 1. Suppressed: emit NOTHING cycle-related — only the neutral off-state line (D-09).
            Text(CycleSummaryCopy.suppressed)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("home.cycleSummary.suppressed")
        } else if !summary.hasEnoughData {
            // 2. Sparse: honest "not enough data yet" — never a fabricated value (CYCL-04).
            Text(CycleSummaryCopy.notEnoughData)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("home.cycleSummary.notEnoughData")
        } else {
            // 3. Derived: cycle-day + a next-period RANGE + a confidence word (D-08/D-10).
            derivedSummary(summary)
                .accessibilityIdentifier("home.cycleSummary.derived")
        }
    }

    private func derivedSummary(_ summary: CycleSummary) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let cycleDay = summary.cycleDay {
                Text("\(CycleSummaryCopy.cycleDayPrefix) \(cycleDay)")
                    .font(.headline)
                    .accessibilityIdentifier("home.cycleSummary.cycleDay")
            }
            if let range = summary.nextPeriod {
                // A DATE RANGE — never a single hard date (D-08).
                Text("\(CycleSummaryCopy.nextWindowLabel): \(formattedRange(range))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("home.cycleSummary.nextWindow")
            }
            Text("\(CycleSummaryCopy.confidenceWord(summary.confidence)) \u{00B7} \(CycleSummaryCopy.basisNote)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("home.cycleSummary.confidence")
        }
    }

    /// Format a next-period estimate as a date RANGE (e.g. "Jun 5 – Jun 9"), never a single date.
    private func formattedRange(_ range: ClosedRange<Date>) -> String {
        let style = Date.FormatStyle.dateTime.month(.abbreviated).day()
        return "\(range.lowerBound.formatted(style)) \u{2013} \(range.upperBound.formatted(style))"
    }
}
