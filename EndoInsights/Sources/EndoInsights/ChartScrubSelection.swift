// Requirement: CHART-02 (CHART-01 day identity) — the honesty choke-point for chart scrubbing.
//
// Position-based chart-selection APIs interpolate by default: a finger between two logged days
// would otherwise read a fabricated in-between value, lying to the user about their own health
// data. This resolver is the ONLY legal path from a raw gesture `Date` to a displayed value
// (CONTEXT "Gap-Ehrlichkeit & Resolver"):
//   • It NEVER interpolates and NEVER snaps to the nearest logged day — the match is the EXACT
//     calendar day under the finger, or nothing.
//   • An unlogged day resolves to `(day, nil)` — the explicit "Not logged" gap, never a zero.
//   • A literal logged severity-0 day resolves to a REAL point with value 0 (absence ≠ 0).
//
// Pure Foundation (the v1.0 grouping-helper playbook — `ChartSeriesGrouping`, which lives in the
// app target, NOT this package; named here only as a design precedent, no compile dependency):
// calendar-injected day math
// (`startOfDay` + `isDate(_:inSameDayAs:)`, never epoch arithmetic), value-in / value-out,
// provable in unit tests without rendering. Phases 15–16 reuse this resolver unchanged.
import Foundation

/// Resolves a raw chart-selection `Date` to the EXACT logged calendar day — or an explicit gap.
/// NEVER interpolates, NEVER snaps to the nearest logged day (the honesty choke-point, CHART-02).
public enum ChartScrubSelection {

    /// The outcome of resolving a raw scrub date against a chart's logged points.
    public struct Resolution: Sendable, Equatable {
        /// The calendar day under the finger (startOfDay) — always real (the date axis is real).
        public let day: Date
        /// The logged point on EXACTLY that day, or nil = "Not logged" (absence ≠ 0;
        /// a literal severity-0 point is returned as a real point, value 0).
        public let point: InsightPoint?

        public init(day: Date, point: InsightPoint?) {
            self.day = day
            self.point = point
        }
    }

    /// Resolve `rawDate` to the exact logged day under the finger, or the explicit gap state.
    ///
    /// - Parameters:
    ///   - rawDate: the raw selection date from the chart gesture; `nil` = no active selection.
    ///   - points: the chart's logged-day points (one per day; order-independent).
    ///   - calendar: the calendar used for day-boundary math (inject a fixed calendar in tests).
    /// - Returns: `nil` when there is no selection; otherwise the day (startOfDay) plus that day's
    ///   exact point — or `point == nil` for an unlogged day (the honest gap, never a neighbor).
    public static func resolve(_ rawDate: Date?, in points: [InsightPoint],
                               calendar: Calendar) -> Resolution? {
        guard let rawDate else { return nil }
        let day = calendar.startOfDay(for: rawDate)
        let match = points.first { calendar.isDate($0.date, inSameDayAs: day) }
        return Resolution(day: day, point: match)
    }
}
