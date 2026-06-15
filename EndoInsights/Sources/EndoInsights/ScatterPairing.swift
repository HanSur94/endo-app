// EndoInsights — pure scatter pairing (Phase 14, Plan 03). Foundation only — NO SwiftData/SwiftUI/UIKit (SC1).
//
// THE both-logged-day honesty move of the scatter view (the Phase-16 enabler). A scatter point is a
// day where BOTH chosen factors were LOGGED — nothing else:
//
//   • both-logged-only: `guard let xv, let yv else { continue }` — a day missing EITHER factor
//     produces NO pair. The day is NEVER imputed, NEVER 0-substituted (`?? 0`/lastKnown is a lie
//     about the user's own health data — Pitfall 1/5 / T-14-05). Bleeding suppression already lives
//     inside `quantitativeValue(from:)` (Plan 01), so a suppressed bleeding axis contributes nothing
//     (T-14-02) — the gate carries through here for free.
//
//   • count-encoded, never jittered: identical `(x, y)` day-pairs aggregate into ONE `ScatterPoint`
//     whose `count` is a FACTUAL tally of how many days landed on that exact cell. We NEVER add
//     `Double.random`/jitter to spread overlapping points — that would fabricate fractional values
//     the user never logged (Pitfall 5 / T-14-06 / RESEARCH OQ-RESOLVED: count-encoding only).
//
//   • the wellness-vs-medical-device line: `ScatterPoint` carries ONLY x / y / count — NO fitted
//     line, NO r-value, NO coefficient, NO regression. The layer emits a factual tally, never a
//     statistic (T-14-07). This is enforced by the type's shape, not a runtime check.
//
// Scatter axes are QUANTITATIVE-ONLY (RESEARCH OQ3 RESOLVED): a presence factor has no numeric value,
// so it cannot be a scatter axis. Enforced at the API boundary via a precondition on `isQuantitative`.
// Pure value-in / value-out, provable in unit tests without rendering — mirrors `ExploreSeriesExtraction`.
import Foundation

/// The pure scatter-pairing namespace (mirrors `ExploreSeriesExtraction`'s enum-of-static-funcs shape —
/// no instances). The single legal path from `[DaySnapshot]` to the both-logged scatter cells.
public enum ScatterPairing {

    /// One scatter cell: an exact `(x, y)` value pair plus how many days landed on it.
    ///
    /// `count` is a FACTUAL tally — the number of distinct logged days at this exact `(x, y)` — NOT a
    /// statistic. There is deliberately NO fitted line, NO r-value, NO coefficient, NO slope on this
    /// type: the scatter layer reports what the user logged, never a derived correlation claim (the
    /// wellness-vs-medical-device line / T-14-07).
    public struct ScatterPoint: Sendable, Equatable, Hashable {
        /// The x-axis value (a real logged value on a quantitative axis — never imputed).
        public let x: Double
        /// The y-axis value (a real logged value on a quantitative axis — never imputed).
        public let y: Double
        /// How many days were logged at EXACTLY this `(x, y)` — a factual tally, never jitter.
        public let count: Int

        public init(x: Double, y: Double, count: Int) {
            self.x = x
            self.y = y
            self.count = count
        }
    }

    /// Pair two quantitative series into both-logged-day scatter cells with count-aggregation.
    ///
    /// A day where EITHER factor is unlogged (`quantitativeValue(from:) == nil`) contributes NO pair —
    /// never imputed, never 0-substituted (T-14-05 / Pitfall 1/5). A logged literal 0 on both axes IS a
    /// real pair (the accessor returns `0.0`, never dropped — Pitfall 2). Identical `(x, y)` days
    /// aggregate into ONE `ScatterPoint` with `count` incremented — NEVER jittered (T-14-06). Bleeding
    /// suppression carries through from the Plan-01 accessor (a suppressed bleeding axis yields no pair).
    /// Output is sorted by `(x, y)` for deterministic fixtures.
    ///
    /// - Precondition: BOTH axes are quantitative (`scaleKind.isQuantitative`) — a presence factor has
    ///   no numeric value and cannot be a scatter axis (RESEARCH OQ3).
    public static func pairs(_ snapshots: [DaySnapshot], x: ExploreSeries, y: ExploreSeries,
                             calendar: Calendar) -> [ScatterPoint] {
        precondition(x.scaleKind.isQuantitative && y.scaleKind.isQuantitative,
                     "Scatter axes are quantitative-only; a presence factor has no numeric value (RESEARCH OQ3).")

        // Bit-pattern-exact key: integer-valued ordinals (0…10, 1…5, 1…4, continuous hours/minutes)
        // aggregate exactly. NO jitter, NO rounding, NO fractional fabrication (RESEARCH Code Examples).
        var counts: [PairKey: Int] = [:]
        for snap in snapshots {
            // EITHER factor nil → the day is excluded, NEVER imputed/0-substituted (T-14-05).
            // The bleeding suppression gate already lives inside quantitativeValue (Plan 01) and
            // carries through here: a suppressed bleeding axis returns nil → no pair (T-14-02).
            guard let xv = x.quantitativeValue(from: snap),
                  let yv = y.quantitativeValue(from: snap) else { continue }
            counts[PairKey(x: xv, y: yv), default: 0] += 1
        }

        return counts
            .map { ScatterPoint(x: $0.key.x, y: $0.key.y, count: $0.value) }
            .sorted { ($0.x, $0.y) < ($1.x, $1.y) }
    }

    /// Aggregation key for identical `(x, y)` day-pairs — Hashable by the exact value pair so identical
    /// logged ordinals collapse to ONE cell (count-encoded). No jitter, no rounding (RESEARCH).
    private struct PairKey: Hashable {
        let x: Double
        let y: Double
    }
}
