// THROWAWAY PROTOTYPE — Phase 15, Plan 01 (Wave 0). DELETED / superseded by 15-04.
//
// Purpose: resolve RESEARCH Assumption A2 / Pitfall 1 — the CENTRAL design risk of the Explore
// milestone. Sibling `Chart` lanes on DIFFERENT Y scales (0...10 severity, 0...24 sleep hours,
// 0...240 activity minutes) get DIFFERENT plot leading insets, because Swift Charts reserves the
// left gutter for each chart's own Y-axis labels. A 0...240 activity lane reserves more space than
// a 0...10 severity lane, so an IDENTICAL `chartXScale(domain:)` still renders the SAME date at
// DIFFERENT X pixels per lane — and one shared `RuleMark` looks misaligned, breaking the
// "one finger reads every lane" shared-scrub illusion.
//
// This probe forces identical plot leading insets via the RESOLVED DEFAULT mechanism (15-RESEARCH
// "Open Questions (RESOLVED)" item 1): `.chartYAxis(.hidden)` on every quantitative lane + carry the
// unit + honest min/max in the per-lane TITLE, so no lane reserves a numeric-label gutter and all
// plots share the full width. One shared `@State Date?` scrub binding + one fixed-date `RuleMark`
// per lane prove the alignment without scrubbing.
//
// THREE sibling `Chart` bodies are written out INLINE (not factored into one reusable lane view) so
// the structure mirrors the real 15-04 lane stack and each `chartXScale(domain:)` / `chartYScale` /
// `chartYAxis(.hidden)` / `chartXSelection` call is visible per lane.
//
// It is PREVIEW-ONLY: NOT wired into any tab/screen/ConsentedShell, reads NO DaySnapshot/Repository,
// ships NO user-facing copy. Hard-coded synthetic points only (T-15-01 / T-15-02).
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import Charts
import EndoInsights

/// The throwaway 3-lane alignment probe: three sibling Charts on DELIBERATELY different Y scales
/// (max axis-label-width divergence) sharing ONE X domain + ONE scrub binding + ONE fixed RuleMark.
struct _LaneAlignmentProbe: View {
    /// The ONE shared scrub state — a plain `@State Date?` bound to every lane.
    @State private var selectedDate: Date?

    // A fixed ~14-day window so the synthetic data + probe date are deterministic.
    private static let calendar = Calendar.current
    private static let anchor: Date = calendar.startOfDay(for: Date(timeIntervalSince1970: 1_750_000_000))

    private static func day(_ offset: Int) -> Date {
        calendar.date(byAdding: .day, value: offset, to: anchor) ?? anchor
    }

    /// IDENTICAL across all three lanes — the shared absolute-date X domain.
    private var xDomain: ClosedRange<Date> { Self.day(0)...Self.day(13) }
    /// A FIXED probe date inside the window — the shared RuleMark anchor on every lane so
    /// misalignment is visible WITHOUT scrubbing (the activity 0...240 lane's plot must not start
    /// further right than the severity lane).
    private var probeDate: Date { Self.day(7) }

    // Severity 0...10 — narrow Y labels. GAP at day 5 (omitted point → the line breaks there).
    private var severityPoints: [InsightPoint] {
        let vals: [Int: Double] = [0: 0, 1: 2, 2: 4, 3: 3, 4: 6, /* gap @5 */ 6: 7, 7: 5, 8: 9,
                                   9: 8, 10: 10, 11: 6, 12: 4, 13: 7]
        return vals.keys.sorted().map { InsightPoint(date: Self.day($0), value: vals[$0]!) }
    }
    // Sleep 0...24 — medium Y labels. GAP at day 8.
    private var sleepPoints: [InsightPoint] {
        let hours: [Int: Double] = [0: 6, 1: 7.5, 2: 5, 3: 8, 4: 6.5, 5: 7, 6: 9, 7: 6,
                                    /* gap @8 */ 9: 8.5, 10: 7, 11: 6, 12: 22, 13: 8]
        return hours.keys.sorted().map { InsightPoint(date: Self.day($0), value: hours[$0]!) }
    }
    // Activity 0...240 — WIDEST Y labels (the worst-case left gutter). GAP at day 3.
    private var activityPoints: [InsightPoint] {
        let mins: [Int: Double] = [0: 30, 1: 90, 2: 0, /* gap @3 */ 4: 120, 5: 200, 6: 45,
                                   7: 240, 8: 60, 9: 150, 10: 0, 11: 75, 12: 180, 13: 210]
        return mins.keys.sorted().map { InsightPoint(date: Self.day($0), value: mins[$0]!) }
    }

    /// The per-lane fixed honest domain (never a per-window min/max). `0...1` only as a safe fallback
    /// — every kind used here returns a real domain.
    private func yDomain(_ kind: ScaleKind) -> ClosedRange<Double> {
        SeriesNormalization.domain(for: kind) ?? 0...1
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {

                // ── Lane 1: Severity (0...10) ───────────────────────────────────────────────
                VStack(alignment: .leading, spacing: 4) {
                    Text("Severity — of 10")            // unit-in-title (RESOLVED-DEFAULT substitute for the hidden Y axis)
                        .font(.subheadline).foregroundStyle(.secondary)
                    Chart {
                        ForEach(severityPoints, id: \.date) { p in
                            LineMark(x: .value("Date", p.date), y: .value("Severity", p.value))
                                .foregroundStyle(.blue)
                            PointMark(x: .value("Date", p.date), y: .value("Severity", p.value))
                                .foregroundStyle(.blue)
                        }
                        RuleMark(x: .value("Selected", probeDate))      // SHARED fixed-date scrub rule
                            .foregroundStyle(.secondary.opacity(0.5)).zIndex(-1)
                    }
                    .chartXScale(domain: xDomain)                       // IDENTICAL shared X domain
                    .chartYScale(domain: yDomain(.ordinal0to10))        // per-lane fixed honest domain
                    .chartYAxis(.hidden)                                // RESOLVED-DEFAULT: identical inset
                    .chartXSelection(value: $selectedDate)              // SHARED scrub binding
                    .frame(height: 120)
                }

                // ── Lane 2: Sleep (0...24) ──────────────────────────────────────────────────
                VStack(alignment: .leading, spacing: 4) {
                    Text("Sleep — h")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Chart {
                        ForEach(sleepPoints, id: \.date) { p in
                            LineMark(x: .value("Date", p.date), y: .value("Sleep", p.value))
                                .foregroundStyle(.green)
                            PointMark(x: .value("Date", p.date), y: .value("Sleep", p.value))
                                .foregroundStyle(.green)
                        }
                        RuleMark(x: .value("Selected", probeDate))
                            .foregroundStyle(.secondary.opacity(0.5)).zIndex(-1)
                    }
                    .chartXScale(domain: xDomain)
                    .chartYScale(domain: yDomain(.continuousHours))
                    .chartYAxis(.hidden)
                    .chartXSelection(value: $selectedDate)
                    .frame(height: 120)
                }

                // ── Lane 3: Activity (0...240) — widest Y labels, the worst case ─────────────
                VStack(alignment: .leading, spacing: 4) {
                    Text("Activity — min")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Chart {
                        ForEach(activityPoints, id: \.date) { p in
                            LineMark(x: .value("Date", p.date), y: .value("Activity", p.value))
                                .foregroundStyle(.orange)
                            PointMark(x: .value("Date", p.date), y: .value("Activity", p.value))
                                .foregroundStyle(.orange)
                        }
                        RuleMark(x: .value("Selected", probeDate))
                            .foregroundStyle(.secondary.opacity(0.5)).zIndex(-1)
                    }
                    .chartXScale(domain: xDomain)
                    .chartYScale(domain: yDomain(.continuousMinutes))
                    .chartYAxis(.hidden)
                    .chartXSelection(value: $selectedDate)
                    .frame(height: 120)
                }
            }
            .padding()
        }
    }
}

#Preview {
    _LaneAlignmentProbe()
}
