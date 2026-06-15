// EndoInsights — the presentation-agnostic normalization primitive (Phase 14, Plan 04). Foundation
// only — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLintTests enforce this).
//
// The single source of the FIXED, HONEST per-scale-kind domain that Phase 15 composes on to make the
// deferred lanes-vs-labeled-normalization call WITHOUT silently misleading the user:
//   • `domain(for:)` returns a FIXED literal range per ScaleKind — NEVER a per-visible-window min/max
//     (Pitfall 4). A per-window domain would distort a series' shape across window switches and is a
//     Phase-15 PRESENTATION decision, explicitly out of scope here. Presence has NO numeric domain
//     (nil) — an event lane is not a normalized value (Pitfall 1).
//   • `normalize` maps a real value onto the unit interval for shape-only overlay; `denormalize` is its
//     exact inverse, so a normalize→denormalize round-trip recovers the real value (within float
//     tolerance).
//   • `readout` ALWAYS reports the REAL value + unit — NEVER the normalized [0,1] number (honesty;
//     T-14-08). Even when Phase 15 normalizes for visual overlay, every value the user READS stays
//     honest — there is no silent normalization.
//
// Pure namespace (mirrors `ChartScrubSelection`): value-in / value-out, provable in unit tests without
// rendering. The domain literals are FIXED constants, never computed from data (no min/max/reduce).
import Foundation

/// The FIXED honest domain + unit-interval normalization + real-unit readout for catalogued series.
/// Presentation-agnostic: this supplies the primitive only; Phase 15 decides lanes-vs-normalize on top.
public enum SeriesNormalization {

    /// The FIXED honest domain for a scale kind, or `nil` for `.presenceEvent` (an event lane has no
    /// numeric domain). These ranges are FIXED literals per kind — NEVER computed from a visible
    /// window's min/max (Pitfall 4; per-window normalization is a Phase-15 presentation decision):
    ///   • `.ordinal0to10`     → `0...10`  — symptom/pain severity, stress.
    ///   • `.ordinal1to5`      → `1...5`   — day mood.
    ///   • `.ordinalBleeding`  → `1...4`   — FlowIntensity spotting…heavy ordinal rank (RESEARCH A1).
    ///   • `.continuousHours`  → `0...24`  — honest hour ceiling for sleep duration (RESEARCH A2).
    ///   • `.continuousMinutes`→ `0...240` — honest minute ceiling for activity duration (RESEARCH A2).
    ///   • `.presenceEvent`    → `nil`     — no numeric domain (presence/occurrence only).
    public static func domain(for kind: ScaleKind) -> ClosedRange<Double>? {
        switch kind {
        case .ordinal0to10:     return 0...10
        case .ordinal1to5:      return 1...5
        case .ordinalBleeding:  return 1...4
        case .continuousHours:  return 0...24
        case .continuousMinutes: return 0...240
        case .presenceEvent:    return nil
        }
    }

    /// Map a real `value` onto the unit interval `[0,1]` for shape-only overlay, or `nil` for a kind
    /// with no numeric domain (`.presenceEvent`). Linear against the FIXED domain — so a series' shape
    /// stays stable across window switches.
    public static func normalize(_ value: Double, in kind: ScaleKind) -> Double? {
        guard let d = domain(for: kind) else { return nil }
        return (value - d.lowerBound) / (d.upperBound - d.lowerBound)
    }

    /// The exact inverse of `normalize`: map a `unit`-interval position back to its real value, or `nil`
    /// for a kind with no numeric domain. `denormalize(normalize(v)) == v` within float tolerance.
    public static func denormalize(_ unit: Double, in kind: ScaleKind) -> Double? {
        guard let d = domain(for: kind) else { return nil }
        return d.lowerBound + unit * (d.upperBound - d.lowerBound)
    }

    /// The honest readout: ALWAYS the REAL `value` + `unit`, NEVER the normalized [0,1] number (T-14-08).
    /// An empty `unit` (presence) yields the bare real value. Final chrome copy routes through the
    /// Phase-15 ExploreCopy audit; this is the descriptive, honesty-preserving default.
    public static func readout(_ value: Double, kind: ScaleKind, unit: String) -> String {
        unit.isEmpty ? "\(value)" : "\(value) \(unit)"
    }
}
