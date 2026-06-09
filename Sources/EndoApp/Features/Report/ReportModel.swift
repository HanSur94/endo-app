// Requirement: RPRT-01 / RPRT-02 (Phase 10, D-04/D-05/D-08) — the PURE, testable report core.
//
// `ReportModel` is the honesty + source-label boundary of the doctor-ready PDF, assembled WITHOUT any
// rendering (Pattern 1). It mirrors the established `Repository.daySnapshots()` projection posture: the
// ONE place where store reads + the LOCKED `EndoInsights` engine become a flat, `Equatable`, `Sendable`
// value model the unit tests assert against — decoupling "is the report correct/honest?" from "does it
// draw right?" (Plan 02 renders this; Plan 03 wires the UI).
//
// HONESTY CONTRACT (the endo pitfall — mirrors CycleSummaryView / TrendsEngine / FactorDay):
//   • RANGE SCOPING (A1): descriptive sections honor the EXACT chosen range (computed directly from
//     range-filtered reads); the insights section surfaces only engine windows whose span is ⊆ the
//     range, anchored at `range.end` (the engine is window-based — Pattern 1).
//   • absence ≠ 0: a lifestyle factor that was NOT logged is `nil` → "Not logged", NEVER 0. A logged
//     level-0 value is a real 0, distinct from absence.
//   • NO phantom cycle: the cycle section is a THREE-STATE enum mirroring `CycleSummaryView` exactly
//     (.suppressed / .notEnoughData / .derived) — a suppressed user shows the off-state, never a
//     fabricated cycle-day/window/phase. Cycle is derived via the PURE `CycleMath`, never re-derived.
//   • sparse → "Not enough data in this range": a sub-threshold range yields the honest empty state
//     per section, never a fabricated normal report.
//   • PER-SECTION SOURCE LABELS (RPRT-02): each data section's source is AGGREGATED from
//     `Repository.daySnapshotSources(on:)` across the range's days (the single merge gate — never
//     recomputed from values, Pitfall 1). No provenance → `.none` (no badge — SC3 honesty).
//
// Pure value type: Foundation only — NO `import SwiftData`, NO `import SwiftUI`, NO networking. The
// engine is INJECTED as a closure so `assemble` stays pure and off any actor, and the package's
// window-based gate (HonestyGate) is reused verbatim rather than re-implemented.
//
// NOTE (Task 1 — Nyquist Wave-0): this file defines ONLY the CONTRACT (types + a stubbed `assemble`
// that throws). Task 2 implements `assemble` and turns the RED `ReportModelTests` green. Fixed copy
// (the renderer's strings) lives in `ReportCopy` (Task 3), never inline here.
import Foundation
import EndoInsights

// MARK: - Source provenance (RPRT-02, Pattern 4)

/// The aggregated, per-section provenance label (RPRT-02). Derived ONLY from the Phase-9
/// `daySnapshotSources` merge gate — never recomputed from values (Pitfall 1). `.none` = no provenance
/// present for the section across the range → no badge (SC3 honesty extends to provenance).
///
/// Rendered as glyph + word (never color) by Plan 02: ● "Self-reported" / ♥ "From Apple Health" /
/// "Mixed". A clinician printing grayscale MUST distinguish them by shape + word, never hue.
enum SectionSource: Equatable, Sendable {
    case selfReported
    case appleHealth
    case mixed
    case none

    /// Aggregate a section's per-day `ValueSource?` provenance into a single label (Pattern 4). The
    /// PURE honesty rule: an empty present-set → `.none`; both manual and appleHealth present → `.mixed`;
    /// else all-manual → `.selfReported`, all-imported → `.appleHealth`. NEVER recomputed from values.
    static func aggregate(_ sources: [ValueSource?]) -> SectionSource {
        let present = sources.compactMap { $0 }
        if present.isEmpty { return .none }                       // no badge — SC3 honesty
        let manual = present.contains(.manual)
        let health = present.contains(.appleHealth)
        if manual && health { return .mixed }
        return manual ? .selfReported : .appleHealth
    }
}

// MARK: - Sections (D-04 order)

/// Section 1 — Header: app title + the exact covered range + the single-source-of-truth disclaimer.
/// The disclaimer PROSE is NOT carried here (Plan 02 reuses `Disclaimer.text` verbatim, never retyped);
/// the model carries only the structured facts the header renders around it.
struct HeaderSection: Equatable, Sendable {
    /// The exact date interval this report covers (descriptive sections honor it precisely).
    var range: DateInterval
    /// When the report was assembled (the "Generated {date}" stamp).
    var generatedOn: Date
}

/// Section 2 — Summary / coverage: an honest count of logged days in range + the sources present.
/// `daysLogged == 0` is the honest "nothing in this range" state, never a fabricated coverage line.
struct SummarySection: Equatable, Sendable {
    /// Distinct calendar days inside the range that carry ANY logged signal (0 = empty range).
    var daysLogged: Int
    /// The aggregate provenance across the whole range (drives the summary's source line).
    var source: SectionSource
}

/// One symptom's descriptive trend row over the range (absolute, never cycle-relative).
struct SymptomRow: Equatable, Sendable {
    /// The symptom catalog type (package enum — same raw value as the app enum).
    var type: EndoInsights.SymptomType
    /// The lowest logged severity in range (`nil` when no severity was ever recorded — absence ≠ 0).
    var minSeverity: Int?
    /// The highest logged severity in range (`nil` when none recorded).
    var maxSeverity: Int?
    /// Distinct calendar days in range this symptom was logged.
    var daysLogged: Int
}

/// Section 3 — Symptoms & pain: the descriptive trend table + chart points for the range. When the
/// range is sub-threshold the section is honestly EMPTY (`rows.isEmpty && hasEnoughData == false`) →
/// the renderer maps it to "Not enough data in this range." — never a fabricated value.
struct SymptomsSection: Equatable, Sendable {
    /// One row per symptom logged in range (empty when nothing logged).
    var rows: [SymptomRow]
    /// `false` → the honest sparse state ("Not enough data in this range").
    var hasEnoughData: Bool
    /// The aggregate provenance for the section (self-reported / Apple Health / mixed / none).
    var source: SectionSource
}

/// Section 4 — Cycle & bleeding. A THREE-STATE enum mirroring `CycleSummaryView` EXACTLY (the
/// no-phantom-cycle contract): a suppressed user is `.suppressed` (off-state, NOTHING cycle-derivable);
/// a sub-threshold user is `.notEnoughData`; only a meaningful history is `.derived` with the optional
/// cycle-day, the next-window date RANGE (never a single date), and the plain confidence word.
enum CycleSection: Equatable, Sendable {
    /// Suppressed / no-cycle mode (D-09) — the off-state. NO cycle-day, window, or phase.
    case suppressed
    /// Fewer than 2 recorded cycles — the honest "not enough data" state.
    case notEnoughData
    /// A meaningful derived estimate (mirrors `CycleSummary`'s derived fields).
    case derived(cycleDay: Int?, nextWindow: ClosedRange<Date>?, confidence: Confidence)
}

/// Section 5 — Lifestyle factors over the range. EVERY field is OPTIONAL so absence ≠ 0: a `nil`
/// field renders "Not logged"; a logged 0/level reads as a real value. The fields summarize the most
/// recent / representative logged value the renderer tabulates (the exact per-day table is Plan 02).
struct LifestyleSection: Equatable, Sendable {
    /// Latest logged sleep hours in range (`nil` = not logged, never 0).
    var sleepHours: Double?
    /// Latest logged sleep quality in range.
    var sleepQuality: EndoInsights.SleepQuality?
    /// Latest logged activity type in range.
    var activityType: EndoInsights.ActivityType?
    /// Latest logged activity minutes in range (`nil` = not logged, never 0).
    var activityMinutes: Double?
    /// Whether any food was logged in range (presence-only, D-16).
    var loggedFood: Bool
    /// Latest logged stress 0...10 in range (`nil` = not logged — distinct from a logged 0).
    var stress: Int?
    /// Latest logged mood 1...5 in range (`nil` = not logged — distinct from a logged value).
    var mood: Int?
    /// The aggregate provenance for the section.
    var source: SectionSource
}

/// Section 7 — Source legend. A structured marker of which sources actually appear in the report, so
/// the renderer explains only the labels in use (glyph + text, never color).
struct SourceLegend: Equatable, Sendable {
    /// Whether any section sourced from manual logging (drives the "Self-reported" legend line).
    var hasSelfReported: Bool
    /// Whether any section sourced from Apple Health (drives the "From Apple Health" legend line).
    var hasAppleHealth: Bool
}

// MARK: - The pure report model (Pattern 1)

/// The PURE, `Equatable`, `Sendable` report model — the honesty + source-label boundary (RPRT-01/02).
/// Assembled from range-filtered Repository reads + the injected (LOCKED) engine, with NO rendering.
struct ReportModel: Equatable, Sendable {
    /// The exact date interval this report covers.
    let range: DateInterval
    /// When the report was assembled.
    let generatedOn: Date
    /// Section 1 — Header (app title + range + disclaimer chrome).
    let header: HeaderSection
    /// Section 2 — Summary / coverage.
    let summary: SummarySection
    /// Section 3 — Symptoms & pain (trend table; sparse → notEnoughData).
    let symptoms: SymptomsSection
    /// Section 4 — Cycle & bleeding (three-state, NO phantom cycle).
    let cycle: CycleSection
    /// Section 4 provenance (RPRT-02, WR-07): aggregated from the in-range `flow` sources so the report
    /// tells the clinician whether the cycle estimate rests on self-reported vs. imported (Apple Health)
    /// bleeding. `CycleSection` is an enum (no room for a per-case field), so the source rides here.
    let cycleSource: SectionSource
    /// Section 5 — Lifestyle factors (per factor: value OR nil → "Not logged"; absence ≠ 0).
    let lifestyle: LifestyleSection
    /// Section 6 — Insights: the engine output for the range, ALREADY gated (windows ⊆ range, asOf
    /// anchored at range.end). The HonestyGate is reused verbatim — never re-implemented here.
    let insights: [Insight]
    /// Section 7 — Source legend.
    let legend: SourceLegend

    /// An error the stubbed `assemble` throws until Task 2 implements the assembly. Keeps Task 1's
    /// contract compilable + the RED tests honest (an assemble-dependent test stays disabled or expects
    /// the throw) without faking a result.
    enum AssemblyError: Error, Equatable {
        /// `assemble` is not implemented yet (Task 2 owns the body).
        case notImplemented
    }

    /// Assemble the pure report for a date interval (Pattern 1). PURE: reads the injected `repository`
    /// and calls the injected `engine` closure ONLY — no actor, no clock, no I/O, no networking.
    ///
    /// - Parameters:
    ///   - range: the exact interval the descriptive sections honor.
    ///   - repository: the persistence seam (range-filtered reads + provenance).
    ///   - engine: the LOCKED `EndoInsights` gate, injected as a closure so the package stays unreferenced
    ///     in tests and the call site can supply `dismissed` from the store. Window-based — it receives the
    ///     RANGE-FILTERED snapshots and the `asOf` anchor, never an arbitrary range.
    ///   - asOf: the anchor the engine's windows measure back from (the call site passes `range.end`).
    /// - Returns: the assembled, honest `ReportModel`.
    ///
    /// - Note: STUBBED in Task 1 — throws `AssemblyError.notImplemented`. Task 2 implements the body and
    ///   flips the RED `ReportModelTests` green.
    static func assemble(
        range: DateInterval,
        repository: Repository,
        engine: ([DaySnapshot], Date) -> [Insight],
        asOf: Date
    ) throws -> ReportModel {
        let cal = Calendar.current

        // RANGE SCOPING (A1, explicit): read the projected snapshots ONCE, then FILTER to the range
        // INCLUSIVE of range.end's day (the engine is window-based, so we filter the array rather than
        // pass a range). Every descriptive section is computed from THIS filtered set so it honors the
        // EXACT range independent of the engine's windows.
        let allSnapshots = try repository.daySnapshots()
        let inRange = allSnapshots.filter { Self.dayInRange($0.day, range: range, calendar: cal) }

        // SECTION 6 — Insights (A1): hand the RANGE-FILTERED snapshots to the LOCKED engine anchored at
        // range.end (`asOf`). The engine's windows measure back from asOf, so only windows ⊆ the range
        // contribute; the HonestyGate (confidence floor, min-sample, non-causal) is reused verbatim.
        let insights = engine(inRange, asOf)

        // SECTION 1 — Header.
        let header = HeaderSection(range: range, generatedOn: generatedOn(asOf: asOf, range: range))

        // SECTION 4 — Cycle (NO phantom cycle): feed the RANGE-FILTERED flow days + isSuppressed() into
        // the PURE CycleMath (never re-derive), then map the CycleSummary onto the three-state enum
        // EXACTLY as CycleSummaryView does (suppressed / !hasEnoughData → notEnoughData / else derived).
        let suppressed = try repository.isSuppressed()
        let bleedingInRange = try repository.bleedingDays()
            .filter { $0.hadFlow && Self.dayInRange($0.day, range: range, calendar: cal) }
            .map(\.day)
        let cycleSummary = CycleMath.summarize(
            bleedingDays: bleedingInRange, isSuppressed: suppressed, today: range.end, calendar: cal)
        let cycle = cycleSection(from: cycleSummary)

        // Per-section PROVENANCE (RPRT-02, Pitfall 1): collect the relevant `ValueSource?` from
        // daySnapshotSources(on:) across the in-range days, then aggregate via SectionSource.aggregate.
        // NEVER recompute provenance from values — the merge gate is the single source of truth.
        let sourcesByDay = try inRange.map { try repository.daySnapshotSources(on: $0.day) }

        // SECTION 4 — Cycle provenance (RPRT-02, WR-07): the cycle estimate is derived from bleeding/flow
        // days, so its source aggregates the in-range `flow` provenance (which can be .appleHealth for
        // imported menstrual flow or .manual). NEVER hardcoded — consistent with the other sections.
        let cycleSource = SectionSource.aggregate(sourcesByDay.map(\.flow))

        // SECTION 3 — Symptoms (descriptive trend table; absolute, never cycle-relative). One row per
        // symptom logged in range; min/max severity nil-honest (a present-without-severity stays nil).
        let symptoms = symptomsSection(inRange: inRange, sources: sourcesByDay)

        // SECTION 5 — Lifestyle (absence ≠ 0): each field is the latest logged value in range or nil
        // ("Not logged"). Read package-typed fields straight off the snapshots — no enum re-bridging.
        let lifestyle = lifestyleSection(inRange: inRange, sources: sourcesByDay)

        // SECTION 2 — Summary / coverage: count of distinct in-range logged days + the aggregate source.
        // WR-02: include symptom provenance so the legend explains the "Self-reported" badge the symptoms
        // section draws. Symptoms have no dedicated DaySnapshotSource field (manual-only today), so a day
        // with ANY logged symptom contributes `.manual` — mirroring symptomsSection's own aggregation.
        // Without this, a symptoms-only range gets a Self-reported badge on section 3 that the legend
        // never defines (the clinician sees an undefined badge).
        let symptomFieldSources: [ValueSource?] = inRange.map { $0.symptoms.isEmpty ? nil : .manual }
        let allFieldSources = sourcesByDay.flatMap { [$0.flow, $0.sleep, $0.activity, $0.food] }
            + symptomFieldSources
        let summary = SummarySection(
            daysLogged: inRange.count,
            source: SectionSource.aggregate(allFieldSources))

        // SECTION 7 — Legend: surface only the labels actually in use across the report.
        let presentSources = allFieldSources.compactMap { $0 }
        let legend = SourceLegend(
            hasSelfReported: presentSources.contains(.manual),
            hasAppleHealth: presentSources.contains(.appleHealth))

        return ReportModel(
            range: range,
            generatedOn: header.generatedOn,
            header: header,
            summary: summary,
            symptoms: symptoms,
            cycle: cycle,
            cycleSource: cycleSource,
            lifestyle: lifestyle,
            insights: insights,
            legend: legend)
    }

    // MARK: - Pure assembly helpers

    /// A day is in range when it falls within `[start, end]` INCLUSIVE of `end`'s calendar day. The
    /// half-open `DateInterval.contains` excludes the exact `end`, so the report explicitly admits the
    /// final day (a report "through today" must include today's logs).
    private static func dayInRange(_ day: Date, range: DateInterval, calendar: Calendar) -> Bool {
        range.contains(day) || calendar.isDate(day, inSameDayAs: range.end)
    }

    /// The "Generated" stamp. `asOf` is the injected anchor (deterministic); a real call passes the
    /// range's end so the stamp and the covered range are consistent.
    private static func generatedOn(asOf: Date, range: DateInterval) -> Date { asOf }

    /// Map a `CycleSummary` onto the three-state `CycleSection` EXACTLY as `CycleSummaryView` does — the
    /// no-phantom-cycle contract (suppressed → off-state; !hasEnoughData → notEnoughData; else derived).
    private static func cycleSection(from summary: CycleSummary) -> CycleSection {
        if summary.isSuppressed { return .suppressed }
        if !summary.hasEnoughData { return .notEnoughData }
        return .derived(
            cycleDay: summary.cycleDay,
            nextWindow: summary.nextPeriod,
            confidence: summary.confidence)
    }

    /// Build the descriptive symptoms section from the range-filtered snapshots. One row per symptom
    /// type logged in range, with nil-honest min/max severity (present-without-severity stays nil). A
    /// range with NO logged symptoms is honestly empty (`hasEnoughData == false`) → the renderer maps it
    /// to "Not enough data in this range." (mirrors TrendsEngine — never a fabricated value).
    private static func symptomsSection(
        inRange: [DaySnapshot], sources: [DaySnapshotSource]
    ) -> SymptomsSection {
        // Group every logged symptom point by type across the in-range days.
        var byType: [EndoInsights.SymptomType: (min: Int?, max: Int?, days: Int)] = [:]
        for snap in inRange {
            // A symptom type counts ONCE per day toward `daysLogged`, even if it appears multiple times.
            var seenToday = Set<EndoInsights.SymptomType>()
            for point in snap.symptoms {
                var entry = byType[point.type] ?? (min: nil, max: nil, days: 0)
                if let sev = point.severity {
                    entry.min = entry.min.map { Swift.min($0, sev) } ?? sev
                    entry.max = entry.max.map { Swift.max($0, sev) } ?? sev
                }
                if seenToday.insert(point.type).inserted { entry.days += 1 }
                byType[point.type] = entry
            }
        }
        let rows = byType
            .map { SymptomRow(type: $0.key, minSeverity: $0.value.min,
                              maxSeverity: $0.value.max, daysLogged: $0.value.days) }
            .sorted { $0.type.rawValue < $1.type.rawValue }   // deterministic order for Equatable fixtures

        // HONEST SPARSE GATE: a descriptive trend table is only meaningful with the SAME floor the LOCKED
        // engine uses (`HonestyGate.minSample(.trend) == 3` distinct logged days). A range whose busiest
        // symptom is logged on fewer than 3 days reads as the honest "not enough data in this range"
        // state (`hasEnoughData == false`) — never a fabricated trend from one or two points (mirrors
        // TrendsEngine; absence ≠ a fabricated value).
        let busiestSymptomDays = rows.map(\.daysLogged).max() ?? 0
        let hasEnoughData = busiestSymptomDays >= Self.minSymptomTrendDays

        // Symptom provenance has no dedicated field in DaySnapshotSource (which tracks the imported-capable
        // flow/sleep/activity/food fields). Symptoms are manual-only today, so a day with ANY logged
        // symptom contributes `.manual`; aggregate honestly (empty → .none, no badge).
        let symptomSources: [ValueSource?] = inRange.map { $0.symptoms.isEmpty ? nil : .manual }

        return SymptomsSection(
            rows: rows,
            hasEnoughData: hasEnoughData,
            source: SectionSource.aggregate(symptomSources))
    }

    /// The minimum distinct logged days a symptom needs for the descriptive trend section to read as
    /// "enough data" — kept EQUAL to the LOCKED engine's `HonestyGate.minSample(.trend) == 3` so the
    /// report's honesty floor and the engine's never drift (a sub-threshold range is honestly sparse).
    private static let minSymptomTrendDays = 3

    /// Build the lifestyle section (absence ≠ 0). Each field is the LATEST logged value across the
    /// in-range days (snapshots are sorted ascending by day, so the last non-nil wins) or nil ("Not
    /// logged"). A logged 0/level is a real value, distinct from a nil absence.
    private static func lifestyleSection(
        inRange: [DaySnapshot], sources: [DaySnapshotSource]
    ) -> LifestyleSection {
        var sleepHours: Double?
        var sleepQuality: EndoInsights.SleepQuality?
        var activityType: EndoInsights.ActivityType?
        var activityMinutes: Double?
        var loggedFood = false
        var stress: Int?
        var mood: Int?

        // Ascending by day (Repository.daySnapshots() guarantees this): the last non-nil is the latest.
        for snap in inRange {
            if let v = snap.sleepHours { sleepHours = v }
            if let v = snap.sleepQuality { sleepQuality = v }
            if let v = snap.activityType { activityType = v }
            if let v = snap.activityMinutes { activityMinutes = v }
            if !snap.foodTags.isEmpty { loggedFood = true }
            if let v = snap.stress { stress = v }           // a logged 0 sets a real 0 (absence ≠ 0)
            if let v = snap.mood { mood = v }
        }

        // Aggregate the lifestyle provenance from the imported-capable fields (sleep/activity/food).
        let lifestyleSources = sources.flatMap { [$0.sleep, $0.activity, $0.food] }

        return LifestyleSection(
            sleepHours: sleepHours,
            sleepQuality: sleepQuality,
            activityType: activityType,
            activityMinutes: activityMinutes,
            loggedFood: loggedFood,
            stress: stress,
            mood: mood,
            source: SectionSource.aggregate(lifestyleSources))
    }
}
