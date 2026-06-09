// Requirement: RPRT-01 (Phase 10, A1) — the range-preset → DateInterval + custom-range → engine-window
// mapping, plus the invalid-range guard.
//
// THE A1 decision made code (RESEARCH Open Question 1): the `EndoInsights` engine is window-based
// (7/30/90/all-time, anchored at `asOf`) and has NO range parameter. So the report:
//   • resolves a `RangePreset` to a concrete `DateInterval` (descriptive sections honor it EXACTLY), and
//   • for the Insights section, surfaces ONLY the engine windows whose span is a SUBSET of the chosen
//     range (`enginewindowsInRange`) so a custom range like 46 days shows e.g. the "last 30 days"
//     window labeled honestly — never overstating coverage (a window WIDER than the range is excluded).
//
// DST-safe: every interval is computed via `Calendar.date(byAdding:)`, never epoch arithmetic (mirrors
// `CycleMath` / the Repository's day-bucketing — RESEARCH §Don't Hand-Roll).
import Foundation
import EndoInsights

/// The date-range presets the builder offers (UI-SPEC Surface 1). `.custom` carries two user dates.
enum RangePreset: Equatable, Sendable, CaseIterable {
    case last30
    case last90
    case custom
}

/// Pure range mapping (A1). No store, no UI — value-in/value-out so it is unit-testable and reusable by
/// both the builder (Plan 03) and the renderer (Plan 02).
enum ReportRangeMapping {

    /// The day-span of each engine `TrendWindow` (used to decide which windows fit inside a range).
    /// `.allTime` has no fixed span — it is `nil` and handled explicitly in `enginewindowsInRange`.
    static func windowDays(_ window: TrendWindow) -> Int? {
        switch window {
        case .last7:   return 7
        case .last30:  return 30
        case .last90:  return 90
        case .allTime: return nil
        }
    }

    /// Resolve a preset to a concrete `DateInterval` (A1). `.last30`/`.last90` measure back from `asOf`
    /// (so `asOf == range.end` makes them map cleanly onto the engine's same-named windows). `.custom`
    /// spans `startOfDay(from) ... startOfDay(to)` — the EXACT range the descriptive sections honor.
    ///
    /// DST-safe `Calendar.date(byAdding:)` math; `from`/`to` are ignored for the non-custom presets.
    static func interval(
        for preset: RangePreset, from: Date, to: Date, asOf: Date, calendar: Calendar = .current
    ) -> DateInterval {
        switch preset {
        case .last30:
            // WR-01: anchor the START to start-of-day so it aligns with a snapshot day key (snapshots'
            // `day` is start-of-day 00:00). `asOf` carries today's wall-clock time, so a naive
            // `asOf - 30 days` start sits at e.g. 14:00, leaving the earliest snapshot day (00:00) BEFORE
            // `range.start` → `DateInterval.contains` (half-open `[start, end)`) drops the first claimed
            // day. Keep `end` at `asOf` so today's logs stay in via the inclusive-end posture.
            let endDay = calendar.startOfDay(for: asOf)
            let start = calendar.date(byAdding: .day, value: -30, to: endDay) ?? endDay
            return DateInterval(start: start, end: asOf)
        case .last90:
            let endDay = calendar.startOfDay(for: asOf)
            let start = calendar.date(byAdding: .day, value: -90, to: endDay) ?? endDay
            return DateInterval(start: start, end: asOf)
        case .custom:
            let start = calendar.startOfDay(for: from)
            let end = calendar.startOfDay(for: to)
            // Defensive: never construct an inverted interval (the guard below disables Generate first).
            return start <= end
                ? DateInterval(start: start, end: end)
                : DateInterval(start: end, end: start)
        }
    }

    /// The A1 helper: the engine windows whose span is a SUBSET of `range` (so the Insights section
    /// labels only windows the range can honestly support). A window WIDER than the range is EXCLUDED
    /// (e.g. a 46-day range yields `.last7` + `.last30`, never `.last90`). `.allTime` is included because,
    /// once the snapshots are filtered to the range, "all time" IS the range — it can never overstate it.
    /// Returned widest-first (descending span) so the renderer can prefer the broadest fitting window.
    static func enginewindowsInRange(_ range: DateInterval, calendar: Calendar = .current) -> [TrendWindow] {
        // The range's day-span, inclusive of the end day (mirrors ReportModel's inclusive-end posture).
        let spanDays = (calendar.dateComponents([.day], from: range.start, to: range.end).day ?? 0) + 1

        let fitting = TrendWindow.allCases.filter { window in
            guard let days = windowDays(window) else { return true }   // .allTime ⊆ the range by definition
            return days <= spanDays
        }
        // Widest-first: larger fixed span first; .allTime (no fixed span) sorts last.
        return fitting.sorted { lhs, rhs in
            (windowDays(lhs) ?? -1) > (windowDays(rhs) ?? -1)
        }
    }

    /// The invalid-range guard (drives the Generate-disabled state in Plan 03). Only the custom path can
    /// be invalid — a custom `to` that precedes `from` is rejected; the presets are always valid.
    static func isValid(preset: RangePreset, from: Date, to: Date, calendar: Calendar = .current) -> Bool {
        guard preset == .custom else { return true }
        return calendar.startOfDay(for: from) <= calendar.startOfDay(for: to)
    }
}
