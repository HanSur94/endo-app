// EndoInsights — the estimated menstrual-cycle phase enum (Phase 6, D-01 / INST-02).
//
// Foundation only — NO SwiftData/SwiftUI/UIKit (SC1). Mirrors the `public enum` shape of
// `InsightKind`/`TrendWindow` in `Insight.swift` (String-backed, Sendable/Equatable/CaseIterable,
// doc-commented per case).
//
// HONESTY (D-01/D-02): these are ESTIMATED windows derived from recorded bleeding only — there is no
// ovulation test or hormone signal here. The `CyclePhaseMapper` emits NOTHING for suppressed,
// irregular, or under-sampled streams rather than fabricating a phase (the #1 endo pitfall, SC1), and
// caps confidence at `.medium` because a phase boundary is an estimate even at best.
import Foundation

/// The four named phases of an estimated menstrual cycle (D-01). Derived from recorded bleeding runs +
/// the median cycle length — never an assumed 28-day default and never an external hormone signal.
public enum CyclePhase: String, Sendable, Equatable, CaseIterable {
    /// The recorded bleeding days of the current cycle (the actual logged run — never a fixed count).
    case menstrual
    /// End-of-bleeding up to the pre-ovulation uncertainty band.
    case follicular
    /// The ovulation uncertainty band, centered ~14 days BEFORE the next predicted period start.
    case ovulatory
    /// The post-ovulation window up to the next predicted period start (the biologically stable anchor).
    case luteal
}

/// One labelled calendar day with its estimated `CyclePhase` (the `InsightPoint`-shape result type).
public struct PhaseDay: Sendable, Equatable {
    public let day: Date
    public let phase: CyclePhase

    public init(day: Date, phase: CyclePhase) {
        self.day = day
        self.phase = phase
    }
}
