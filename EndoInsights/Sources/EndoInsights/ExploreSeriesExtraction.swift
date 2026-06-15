// EndoInsights — pure series extraction (Phase 14, Plan 02). Foundation only — NO SwiftData/SwiftUI/UIKit (SC1).
//
// THE structural honesty move of the phase: TWO kind-gated entry points returning TWO DIFFERENT types.
//
//   • `quantitativePoints(_:series:calendar:) -> [InsightPoint]` — the QUANTITATIVE path. A `compactMap`
//     over the snapshots: an unlogged field (`quantitativeValue(from:) == nil`) emits NO point for that
//     day (a gap — absence ≠ 0, NEVER a fabricated 0); a logged literal 0 (stress 0, severity 0) emits a
//     REAL `InsightPoint` at value 0 (because the accessor returns `0.0` for it, never dropped). It emits
//     the EXISTING `InsightPoint` so the shipped, gap-breaking `ChartSeriesGrouping` (app target) and
//     `ChartScrubSelection` (scrub resolver) are reused UNCHANGED downstream (LOCKED reuse decision).
//
//   • `occurrenceDays(_:series:calendar:) -> [Date]` — the PRESENCE/EVENT path. A DISTINCT return type
//     with NO `value` field, so "coerce a tag to a 0/1 value" is UNREPRESENTABLE in the type system
//     (RESEARCH Pattern 1 / Pitfall 1) — this success criterion is enforced by CONSTRUCTION, not by a
//     runtime check. A present day maps to its `startOfDay` Date; an absent day maps to nothing.
//
// Both bleeding-derived series gate on `!snap.suppressed` inside the Plan-01 accessors, so a suppressed
// user's bleeding cannot leak (Pattern 3 / Pitfall 4): zero points AND zero occurrences.
//
// Day normalization is calendar-injected (`calendar.startOfDay(for:)`) — NEVER epoch arithmetic
// (DST-safety / Pitfall 6). Pure value-in / value-out, provable in unit tests without rendering.
import Foundation

/// The pure extraction namespace (mirrors `ChartScrubSelection`'s enum-of-static-funcs shape — no
/// instances). Two kind-gated entry points; the quantitative path returns `[InsightPoint]`, the
/// presence path returns `[Date]` (no value field, so 0/1 coercion is structurally unrepresentable).
public enum ExploreSeriesExtraction {

    /// Extract the quantitative series as logged-day `InsightPoint`s. `compactMap` skips an unlogged day
    /// (`quantitativeValue(from:) == nil`) → a gap (absence ≠ 0, never a fabricated 0); a logged literal
    /// 0 is kept as a REAL point at value 0 (the accessor returns `0.0`, never dropped). Suppressed
    /// bleeding yields no points (the accessor gates on `!snap.suppressed`). Emits the existing
    /// `InsightPoint` so the shipped `ChartSeriesGrouping`/`ChartScrubSelection` are reused unchanged.
    ///
    /// - Precondition: `series.scaleKind.isQuantitative` — presence series route via `occurrenceDays`.
    public static func quantitativePoints(_ snapshots: [DaySnapshot], series: ExploreSeries,
                                          calendar: Calendar) -> [InsightPoint] {
        precondition(series.scaleKind.isQuantitative,
                     "quantitativePoints requires a quantitative series; presence routes via occurrenceDays.")
        return snapshots.compactMap { snap in
            guard let value = series.quantitativeValue(from: snap) else { return nil }
            return InsightPoint(date: calendar.startOfDay(for: snap.day), value: value)
        }
    }

    /// Extract the presence/event series as occurrence `Date`s (one `startOfDay` per logged-present day).
    /// This path has NO value field — so "coerce a tag to a 0 or 1" is unrepresentable (Pitfall 1, the
    /// success criterion). `compactMap` maps a present day → its `startOfDay`, an absent day → nil (so a
    /// logged day WITHOUT the thing emits NO occurrence — presence-absent ≠ logged-absent). The
    /// `bleeding.event` accessor already gates on `!snap.suppressed`, so a suppressed user emits nothing.
    ///
    /// - Precondition: `series.scaleKind == .presenceEvent` — quantitative series route via `quantitativePoints`.
    public static func occurrenceDays(_ snapshots: [DaySnapshot], series: ExploreSeries,
                                      calendar: Calendar) -> [Date] {
        precondition(series.scaleKind == .presenceEvent,
                     "occurrenceDays requires a presence/event series; quantitative routes via quantitativePoints.")
        return snapshots.compactMap { snap in
            series.isPresent(in: snap) ? calendar.startOfDay(for: snap.day) : nil
        }
    }
}
