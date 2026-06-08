// Requirement: CYCL-03 / CYCL-04 — the value type the UI renders for a person's cycle state.
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI` (portable to the
// future insights engine + unit-testable). It is the OUTPUT of `CycleMath.summarize` and the input
// to Plan 04's home cycle-summary slot.
//
// The no-phantom-cycle contract (D-08/D-09/D-10):
// - `isSuppressed == true`  → emit NOTHING cycle-related (`.suppressed`): no cycleDay, no phase,
//   no nextPeriod, confidence `.none`, hasEnoughData false.
// - `< 2` recorded cycles   → `.notEnoughData`: "not enough data yet".
// - Every cycle field (`cycleDay`, `phase`, `nextPeriod`) is OPTIONAL and derived — never a stored
//   column, never a fabricated default. `phase` is held nil this phase (Phase 6 populates it —
//   Open Question 2); the field exists so naming phases later needs no contract change.
import Foundation

/// Confidence in a derived cycle estimate (CYCL-04). `Int`-backed so callers can order it
/// (`none < low < medium < high`) and so it degrades gracefully for irregular data.
enum Confidence: Int, Sendable, Comparable, CaseIterable {
    case none = 0
    case low = 1
    case medium = 2
    case high = 3

    static func < (lhs: Confidence, rhs: Confidence) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

/// A snapshot of derived cycle state. Equatable + Sendable for deterministic tests and actor crossing.
struct CycleSummary: Equatable, Sendable {
    /// When true, the user is in suppressed / no-cycle mode (D-09): the UI shows NOTHING cycle-related.
    var isSuppressed: Bool
    /// Derived day-of-cycle (days since the last period start, +1). `nil` unless meaningful (CYCL-04).
    var cycleDay: Int?
    /// Derived named phase. Held `nil` this phase — Phase 6 (INST-02) populates it (Open Question 2).
    var phase: String?
    /// Next-period estimate as a date RANGE (D-08) — never a single hard date. `nil` unless meaningful.
    var nextPeriod: ClosedRange<Date>?
    /// Confidence in the estimate (high/medium/low), or `.none` when nothing is emitted.
    var confidence: Confidence
    /// `false` → "not enough data yet" (CYCL-04): hide the cycle summary entirely.
    var hasEnoughData: Bool

    /// Suppressed / no-cycle mode — emits NOTHING derivable (the no-phantom-cycle proof, D-09).
    static let suppressed = CycleSummary(
        isSuppressed: true, cycleDay: nil, phase: nil,
        nextPeriod: nil, confidence: .none, hasEnoughData: false
    )

    /// Fewer than 2 recorded cycles — "not enough data yet" (CYCL-04).
    static let notEnoughData = CycleSummary(
        isSuppressed: false, cycleDay: nil, phase: nil,
        nextPeriod: nil, confidence: .none, hasEnoughData: false
    )
}
