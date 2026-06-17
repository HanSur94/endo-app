# Phase 13: Trend-Chart Scrubbing - Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 11 new/modified files
**Analogs found:** 10 / 11 (the lollipop selection chrome itself has no in-repo analog — RESEARCH Pattern 3 supplies it)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift` (NEW) | utility (pure resolver) | transform | `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` | exact (the playbook CONTEXT names) |
| `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` (NEW) | test (Swift Testing) | transform | `Tests/EndoAppTests/ChartSeriesGroupingTests.swift` | exact |
| `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` (MODIFY, additive) | component | request-response (state → render) | itself — the `inkColor` additive-param precedent (lines 33–38) | exact |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` (MODIFY, one line) | component | request-response | itself — `.trend` branch, lines 154–162 | exact |
| `Sources/EndoApp/Features/Insights/InsightsCopy.swift` (MODIFY) | config (audited copy) | — | itself — `chartSummary` + `auditableStrings`, lines 100–124 | exact |
| `Tests/EndoAppTests/CopyAuditTests.swift` (verify; optional pin) | test | — | itself — `insightsCopy_*` scans, lines 165–205 | exact |
| `Sources/EndoApp/App/AppDependencies.swift` (MODIFY: arg parse + seed) | config (DI/launch seam) | event-driven | itself — `-uitest-seed-*` parsing (lines 53–100) + `seedThresholdCrossingInsights` (lines 298–306) | exact |
| New `EnvironmentKey` `\.uiTestScrubDay` | provider | — | `Sources/EndoApp/App/CheckInRouter.swift` lines 30–45 (`CheckInRouterKey`) | exact |
| `Sources/EndoApp/App/EndoAppApp.swift` (MODIFY, one line) | provider | — | itself — `.environment(...)` stack, lines 36–42 | exact |
| `Tests/EndoAppUITests/ChartScrubUITests.swift` (NEW; needs `xcodegen generate`) | test (XCUITest E2E) | request-response | `Tests/EndoAppUITests/InsightsDashboardUITests.swift` | exact |
| `Features/Report/ReportPDFRenderer.swift` | — | — | **ZERO diff** (acceptance criterion) | n/a |

## Pattern Assignments

### `ChartScrubSelection.swift` (utility, transform) — NEW in EndoInsights package

**Analog:** `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` (the pure-helper playbook; note it lives in the APP target, but the new resolver goes in EndoInsights — `InsightPoint` is a package type, Phases 15–16 reuse it, no xcodegen needed, `BoundaryLintTests` enforces Foundation-only).

**Imports + doc-comment posture** (`ChartSeriesGrouping.swift` lines 1–18):
```swift
// Requirement-citing header comment explaining the honesty invariant up front.
import Foundation
import EndoInsights   // ← NOT needed in the package itself; the resolver imports Foundation only

enum ChartSeriesGrouping {
```
New file must be `public enum ChartScrubSelection` (consumed by the app target). Full target shape is in RESEARCH Pattern 1 (13-RESEARCH.md lines 180–204).

**Core pattern — calendar-injected day math** (lines 71–76):
```swift
private static func areAdjacentDays(_ earlier: Date, _ later: Date, calendar: Calendar) -> Bool {
    let startEarlier = calendar.startOfDay(for: earlier)
    let startLater = calendar.startOfDay(for: later)
    let dayDelta = calendar.dateComponents([.day], from: startEarlier, to: startLater).day
    return dayDelta == 1
}
```
Copy this stance: `calendar.startOfDay` + calendar APIs only (never epoch-seconds math); calendar injected as a parameter; nested `Sendable, Equatable` value struct (`Run`, lines 22–29) is the model for the resolver's `Resolution`.

### `ChartScrubSelectionTests.swift` (test, Swift Testing) — NEW

**Analog:** `Tests/EndoAppTests/ChartSeriesGroupingTests.swift`

**Fixture pattern** (lines 9–34):
```swift
import Testing
import Foundation
@testable import EndoApp        // → for the package test: @testable import EndoInsights
import EndoInsights

@Suite("Chart series grouping (honest gaps, absence ≠ 0)")
struct ChartSeriesGroupingTests {
    /// A deterministic, DST-agnostic calendar fixed to UTC so day-adjacency math is reproducible.
    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }
    private func day(_ year: Int, _ month: Int, _ d: Int) -> Date {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = d
        comps.hour = 12 // midday — robust against any leftover tz skew
        return calendar.date(from: comps)!
    }
    private func point(_ year: Int, _ month: Int, _ d: Int, value: Double) -> InsightPoint {
        InsightPoint(date: day(year, month, d), value: value)
    }
```

**Test case pattern** (lines 37–47):
```swift
@Test func consecutiveDays_formOneRun() {
    let points = [ point(2026, 6, 1, value: 3), point(2026, 6, 2, value: 4), point(2026, 6, 3, value: 5) ]
    let runs = ChartSeriesGrouping.runs(from: points, calendar: calendar)
    #expect(runs.count == 1)
    #expect(runs[0].points.count == 3)
}
```
Required fixtures (RESEARCH line 206): raw nil → nil; gap day → `point == nil` with correct `day`; severity-0 day → `point?.value == 0` (NOT nil); 23:59 same-day match; DST boundary with `TimeZone(identifier: "Europe/Berlin")`; unsorted input. Run via `swift test --package-path EndoInsights --filter ChartScrubSelectionTests`.

### `InsightTrendChart.swift` (component) — MODIFY, additive only

**Analog:** itself — the `inkColor` precedent for additive defaulted params (lines 33–38):
```swift
/// The absolute "today" the window ends at (injected for determinism; defaults to now).
var asOf: Date = .now
/// The single ink color the line + points draw in. Additive (Phase 10, D-02): the live dashboard
/// keeps `.accentColor` (byte-identical on screen); the PDF renderer constructs a FRESH chart with
/// `inkColor: .black` ... NOTHING else about the chart changes.
var inkColor: Color = .accentColor
```
Add `var allowsSelection: Bool = false` and `var selectionA11yIDRoot: String? = nil` after `inkColor` in exactly this style (doc comment naming the protected default).

**Per-mark a11y vocabulary to mirror in the tooltip** (lines 103–111 — KEEP these untouched, CHART-04):
```swift
ForEach(points, id: \.date) { point in
    PointMark(x: .value("Date", point.date), y: .value("Severity", point.value))
        .foregroundStyle(inkColor)
        .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
        .accessibilityValue(Text("severity \(Int(point.value.rounded())) of 10"))
}
```

**Animation seam that must NOT gain selection as an input** (line 128):
```swift
.animation(reduceMotion ? nil : .default, value: points)
```

**Body structure to extend** (lines 72–84): `VStack { Text(summaryText)...; chart.frame(height: 200).accessibilityLabel(...) }` — wrap `chart` in the conditional `if allowsSelection { chart.chartXSelection(value: $rawSelectedDate) } else { chart }`. Selection chrome, haptic `.onChange`, seam `.onAppear`, and `AXChartDescriptor` follow RESEARCH Patterns 2–5 verbatim (no in-repo analog for the lollipop — this is the repo's only Chart).

`xDomain` (lines 53–65) already exists — reuse it for the seam's `xDomain.contains(day)` guard.

### `InsightCardView.swift` (component) — MODIFY, one call site

**Analog:** itself, `.trend` branch (lines 153–162):
```swift
case .trend:
    InsightTrendChart(
        points: insight.points,
        window: insight.window,
        directionWord: directionWord,
        headline: insight.headline
    )
    .accessibilityIdentifier("\(idRoot).chart")
```
Add `allowsSelection: true, selectionA11yIDRoot: "\(idRoot).chart"`; leave the outer `.accessibilityIdentifier` unchanged.

### `InsightsCopy.swift` (config, audited copy) — MODIFY

**Analog:** itself — parameterized-string + registration pattern (lines 100–124):
```swift
/// A one-line chart text summary so a chart is NEVER the sole carrier of meaning (chart a11y).
static func chartSummary(headline: String, window: TrendWindow, direction: String) -> String {
    "\(headline), \(windowLabel(window)) — \(direction)."
}

static var auditableStrings: [String] {
    var strings: [String] = [ confidenceLow, ..., loadError, updating, ]
    strings += InsightConfidence.allCases.map(confidenceWord)
    strings += TrendWindow.allCases.map(windowLabel)
    // A representative supporting-evidence line (the static prefix/suffix is what we audit).
    strings.append(supportingEvidence(loggedDays: 12))
    return strings
}
```
New: `static let scrubNotLogged = "Not logged"` and `static func scrubSeverity(_ value: Int) -> String { "Severity \(value) of 10" }`; register BOTH in `auditableStrings` in the same change — the parameterized one as a representative call (`scrubSeverity(6)`), mirroring the `supportingEvidence(loggedDays: 12)` precedent.

### `CopyAuditTests.swift` (test) — no structural change required

**Analog:** itself — the scans iterate `auditableStrings`, so registration alone extends coverage. Scan mechanics to curate copy against (lines 165–197): case-insensitive SUBSTRING checks for gendered tokens and false-precision tokens (`%`, `p=`, `r=`); the bare-digit scan is scoped to confidence words only, so "Severity 6 of 10" digits are legal. Optionally add a pinning `#expect` for the literal "Not logged" following the existing `@Test func insightsCopy_*` style.

### `AppDependencies.swift` (config, launch seam + seed) — MODIFY

**Analog:** itself.

**Arg-parse + pairing posture** (lines 53, 66, 298–306) — NO `#if DEBUG` exists anywhere in this file; the gate is runtime pairing with `-uitest-reset` (which forces the in-memory store):
```swift
let uiTestReset = ProcessInfo.processInfo.arguments.contains("-uitest-reset")
let seedInsights = ProcessInfo.processInfo.arguments.contains("-uitest-seed-insights")
```

**Seed-fixture pattern** (`seedThresholdCrossingInsights`, lines 298–306):
```swift
private static func seedThresholdCrossingInsights(into repo: SwiftDataRepository) {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    // 21 consecutive days ending today, ascending — a steady mid-high severity so a trend is emitted.
    for offset in stride(from: 21, through: 1, by: -1) {
        guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
        try? repo.upsertSymptom(type: .pelvicPain, severity: 6, on: day)
    }
}
```
New `-uitest-seed-scrub` clones this shape (best-effort `try?`, doc comment citing the pairing safety) with the RESEARCH Pattern 6 offsets: dense severity-6 spine, SKIP offset 3 (gap), severity 0 at offset 2. Do NOT mutate the existing seed. The `-uitest-select-day` value-arg parse (defensive `en_US_POSIX yyyy-MM-dd`, garbage → nil) is in RESEARCH Pattern 6 lines 350–362.

### New `EnvironmentKey` `\.uiTestScrubDay` (provider)

**Analog:** `Sources/EndoApp/App/CheckInRouter.swift` lines 30–45:
```swift
private struct CheckInRouterKey: EnvironmentKey {
    static var defaultValue: CheckInRouter {
        MainActor.assumeIsolated { CheckInRouter() }
    }
}
extension EnvironmentValues {
    var checkInRouter: CheckInRouter {
        get { self[CheckInRouterKey.self] }
        set { self[CheckInRouterKey.self] = newValue }
    }
}
```
Simpler here: `Date?` is `Sendable`, so `static let defaultValue: Date? = nil` (no `assumeIsolated` dance). Place inside `AppDependencies.swift` (no new app-target file → no xcodegen) next to the existing private keys at lines 485+.

### `EndoAppApp.swift` (provider) — MODIFY, one line

**Analog:** itself, the injection stack (lines 36–42):
```swift
.environment(\.consentStore, dependencies.consentStore)
.environment(\.terminologyStore, dependencies.terminologyStore)
...
.environment(\.insightsCoordinator, dependencies.insightsCoordinator)
```
Append `.environment(\.uiTestScrubDay, dependencies.uiTestScrubDay)`.

### `ChartScrubUITests.swift` (test, XCUITest) — NEW (requires `xcodegen generate`; alternative: extend `InsightsDashboardUITests.swift` to avoid regeneration)

**Analog:** `Tests/EndoAppUITests/InsightsDashboardUITests.swift`

**Harness pattern** (lines 25–66) — clone `launchAndOpenInsights(_ arguments: [String])` verbatim: consent grant via `consent.grantButton`, tab via `tab.insights` with the descendant-query fallback, `insights.screen` wait. `continueAfterFailure = false` in `setUp`, `@MainActor final class`.

**Id-query pattern around the Section id-bleed quirk** (lines 69–75):
```swift
/// ...a SwiftUI `Section`'s `.accessibilityIdentifier` bleeds onto its descendant cells, masking the
/// card-ROOT id — so we detect by an always-present sub-element id.
let predicate = NSPredicate(format: "identifier BEGINSWITH 'insights.card.' AND (identifier ENDSWITH '.confidence' OR identifier ENDSWITH '.insufficient')")
return app.descendants(matching: .any).matching(predicate).firstMatch
```
The tooltip ids (`...chart.tooltip`, `...chart.tooltip.notLogged`) are sub-element ids and are findable the same way (`ENDSWITH` predicate + `firstMatch`). Compute the `-uitest-select-day` `yyyy-MM-dd` strings immediately before `app.launch()` (same midnight-race class as existing today-relative seeds). Assertions: `waitForExistence` + `element.value` only — zero coordinates, zero sleeps (≤1 smoke gesture test).

## Shared Patterns

### Honesty invariant (absence ≠ 0)
**Source:** `ChartSeriesGrouping.swift` header + `InsightTrendChart.swift` lines 5–10
**Apply to:** resolver, chrome, copy, AXChartDescriptor — exact day match only; gap renders "Not logged", never a value, never a snap; logged severity-0 is a real point reading "Severity 0 of 10".

### Audited-copy choke point
**Source:** `InsightsCopy.auditableStrings` (lines 108–124) + `CopyAuditTests` scans (lines 165–197)
**Apply to:** every new chrome string — register in `auditableStrings` in the SAME task that introduces it; no string literals in view files.

### Test-seam pairing (unreachable-in-production)
**Source:** `AppDependencies.swift` lines 53–100 + seed safety comments at 178–205
**Apply to:** `-uitest-select-day` and `-uitest-seed-scrub` — effect activates ONLY when `uiTestReset` is also present (in-memory store).

### Deterministic a11y ids, never copy-matching
**Source:** `InsightsDashboardUITests.swift` header + helpers
**Apply to:** all new E2E assertions — ids rooted at `insights.card.<signalKey>.chart`.

### PDF protection by defaulted param
**Source:** `InsightTrendChart.swift` lines 33–38 (`inkColor` doc comment) + `ReportPDFRenderer.swift:309-315` (fresh-chart construction, per RESEARCH)
**Apply to:** `allowsSelection: Bool = false`; `ReportPDFRenderer.swift` zero-diff verified via `git diff`; the `chartXSelection` modifier conditionally NOT attached (not merely inert).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Selection chrome (RuleMark lollipop, `chartXSelection`, haptic, `AXChartDescriptor`) inside `InsightTrendChart` | component sub-pattern | request-response | `InsightTrendChart` is the repo's ONLY Chart; no selection exists anywhere. Use RESEARCH Patterns 2–5 (13-RESEARCH.md lines 210–341) as the authoritative pattern source. |

## Metadata

**Analog search scope:** `Sources/EndoApp/Features/Insights/`, `Sources/EndoApp/App/`, `Tests/EndoAppTests/`, `Tests/EndoAppUITests/`, `EndoInsights/`
**Files scanned:** 9 read directly (chart, grouping + tests, card view, copy, copy-audit, dependencies, router key, dashboard UITests, app root)
**Pattern extraction date:** 2026-06-10
**CONTEXT corrections inherited from RESEARCH:** `ChartSeriesGrouping` lives in the app target (not EndoInsights); seams are runtime-paired with `-uitest-reset` (no `#if DEBUG` in `AppDependencies`).
