# Phase 13: Trend-Chart Scrubbing - Research

**Researched:** 2026-06-10
**Domain:** Swift Charts selection interactivity (iOS 18, in-List dashboard chart) + pure day-resolution honesty layer + selection accessibility + deterministic E2E seam
**Confidence:** HIGH (every load-bearing API verified against Apple docs availability metadata; every integration claim verified directly against the v1.0 codebase this session; the two OS-behavioral claims that cannot be verified without a device are explicitly marked MEDIUM with manual checkpoints already locked in CONTEXT)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Dashboard-Interaktion (CHART-01/03) — accepted**
- Gesture: framework-standard `chartXSelection` — the tooltip lives while touching and dismisses on release (CHART-03 verbatim); vertical List scrolling keeps priority (framework arbitration; no custom gesture fighting)
- Tooltip content: absolute date + value with unit (e.g. "Jun 5 — severity 6 of 10"); on gap days the explicit "Not logged" state
- Tooltip position: pinned at the chart top via RuleMark + `.annotation(overflowResolution: .fit)` so it never clips at window edges and the finger never occludes it (Health pattern)
- Haptic: `UISelectionFeedbackGenerator` fires on day CHANGE only (not on initial touch, not on release)

**Gap-Ehrlichkeit & Resolver (CHART-02) — user had no preference; recommendations stand as Claude's discretion**
- Day resolution: the EXACT calendar day under the finger resolved through a pure resolver (selected raw date → logged-day-or-nil); an unlogged day yields the explicit "Not logged" state — NEVER interpolation, NEVER snap-to-nearest-logged (position-based selection APIs interpolate by default; the resolver is the honesty choke-point Phases 15–16 reuse)
- Gap-day tooltip: shows the date + "Not logged" (the gap is information, not an error)
- Logged severity-0: reads "0 of 10" — visually and textually distinct from "Not logged" (absence ≠ 0)
- Selection state: per-card, view-local `@State`, ephemeral — never persisted

**Accessibility-Alternative (CHART-04) — accepted**
- Mechanism: keep/extend the existing per-mark `accessibilityLabel`/`accessibilityValue` + adopt `AXChartDescriptor` (Audio Graph) so VoiceOver users navigate per-day values WITHOUT the drag gesture
- Announcements: quiet — selection surfaces via accessibilityValue; no `UIAccessibility.post` chatter on every day change

**Test-Seam & PDF-Schutz — accepted**
- E2E determinism: DEBUG-gated `-uitest-select-day` launch-arg seam sets a deterministic selection; XCUITests assert via a11y ids (tooltip id, "Not logged" variant) — no gesture-coordinate flakes
- API shape: additive defaulted parameter `allowsSelection: Bool = false` on `InsightTrendChart` — the dashboard card passes `true`; `ReportPDFRenderer` is NOT edited (default `false` protects the PDF path by construction, mirroring the `inkColor` precedent)
- PDF verification: ReportFlowUITests re-run green + a visual PDF check noted as a manual checkpoint item

### Claude's Discretion
- All Area-2 details above (user expressed no preference)
- Exact tooltip copy wording (must register in `InsightsCopy.auditableStrings` BEFORE writing; curate against the substring scans — no "her"/"she"/"will"/"causes" hosts)
- Resolver naming/placement (research suggests pure `ChartScrubSelection` in EndoInsights with unit tests, following the `ChartSeriesGrouping` playbook)
- Haptic generator lifecycle details

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. (Explore/scatter interactivity is already Phases 15/16.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CHART-01 | Touch-and-hold/drag (scrub) on any dashboard trend chart reads the exact date + logged value via a rule-mark + lollipop tooltip | `chartXSelection(value:)` verified iOS 17.0 (`Binding<Date?>`); lollipop = `RuleMark` + `.annotation(position: .top, overflowResolution:)` verified iOS 17.0 (Patterns 2–3); selection chrome renders iff the pure resolver returns non-nil |
| CHART-02 | Scrubbing an unlogged day shows an explicit "Not logged" state — never zero, never interpolated | Pure `ChartScrubSelection` resolver (Pattern 1): `calendar.startOfDay` + exact logged-day match → `InsightPoint?`; unit-tested in EndoInsightsTests with gap/severity-0/DST fixtures; tooltip copy `InsightsCopy.scrubNotLogged` registered in CopyAuditTests before views render it |
| CHART-03 | Selection haptic on day change; tooltip lives while touching, dismisses on release | `UISelectionFeedbackGenerator` verified ([CITED: developer.apple.com/documentation/uikit/uiselectionfeedbackgenerator]); day-change-only firing via `.onChange(of: resolved?.day)` (Pattern 4); release-dismissal is framework behavior (MEDIUM — community-corroborated, manual checkpoint covers it); haptics are Simulator no-ops → device manual checkpoint |
| CHART-04 | VoiceOver users read per-day values without the drag gesture | Existing per-mark `accessibilityLabel`/`accessibilityValue` KEPT (verified present in `InsightTrendChart.swift:97-110`); `AXChartDescriptor` init signature verified iOS 15.0 ([CITED: developer.apple.com/documentation/accessibility/axchartdescriptor]) — discrete series, `isContinuous: false`, logged days only (Pattern 5) |
</phase_requirements>

## Summary

This phase is a small, fully-de-risked slice: every load-bearing API already ships in the iOS 18 SDK (verified in the milestone STACK.md via Apple docs JSON, re-confirmed today), zero new dependencies, zero schema changes, and the integration surface is exactly three production files (`InsightTrendChart.swift` additive, `InsightCardView.swift` one line, `InsightsCopy.swift` two strings) plus one pure new resolver, one launch-arg seam in `AppDependencies`, and new tests. The architecture decisions are all locked in CONTEXT/UI-SPEC; this research verifies them against the actual code and closes the remaining implementation-level unknowns.

Four codebase findings the planner must absorb (each detailed below): (1) **`ChartSeriesGrouping` lives in the APP target** (`Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift`), not in EndoInsights as CONTEXT.md states — the new resolver should still go in EndoInsights (recommended; rationale in Pattern 1) because `InsightPoint` is a package type and Phases 15–16 reuse it; (2) **there is no `#if DEBUG` anywhere in `AppDependencies.swift`** — the established "release source-guard posture" is runtime gating: every seam activates only when paired with `-uitest-reset` (which forces an in-memory store); the new `-uitest-select-day` must follow that exact pairing; (3) **`InsightCardView` does not pass `asOf`** to the chart (it defaults `.now`) — fine for the seam because the seeds are also today-relative; (4) **the existing `-uitest-seed-insights` fixture has no gap day and no severity-0 day** (21 consecutive severity-6 days ending yesterday), so the phase needs a new seed variant whose gap and severity-0 days fall INSIDE the last-7-day window so every window-card resolves the same state.

The one genuinely OS-volatile area is gesture arbitration inside the `List` (documented iOS 18 regressions for *custom* gestures in scroll containers; the framework-standard `chartXSelection` with no custom gestures is the safest known configuration) and the exact nil-on-release timing of the `chartXSelection` binding (not documented by Apple; community-corroborated). Both are MEDIUM-confidence and both are already covered by the manual checkpoint locked in CONTEXT. The design is deliberately robust to the second: selection chrome renders iff the resolved selection is non-nil, so it is correct under any nil-timing semantics.

**Primary recommendation:** Build in three waves — (1) pure `ChartScrubSelection` in EndoInsights + unit tests + the two `InsightsCopy` strings registered in CopyAuditTests; (2) the additive `allowsSelection` layer on `InsightTrendChart` (conditional `chartXSelection` attachment, lollipop chrome, haptic, `AXChartDescriptor`) + the one-line `InsightCardView` change; (3) the `-uitest-select-day` seam + `-uitest-seed-scrub` fixture + seam-driven XCUITests + the ReportFlowUITests regression re-run.

## Project Constraints (from CLAUDE.md)

- **GSD workflow enforcement:** file changes go through `/gsd-execute-phase` (planned phase work) — no direct edits outside GSD.
- **No third-party charting libraries** (Swift Charts only) and **near-zero SPM dependencies** — this phase adds zero.
- **Swift Testing for new unit/logic tests; XCUITest for E2E** — coexist in separate targets, not interoperable (`#expect` vs `XCTAssert`).
- **Privacy-first:** no analytics, no off-device data; health data stays on device/private iCloud. (Selection state is ephemeral view-local `@State` — never persisted, consistent with this.)
- **No diagnostic/medical-device framing in copy** — all new chrome copy passes the ClaimsPolicy scans (the audited choke-point).
- **Xcode 26 toolchain, iOS 18 floor, Swift 6 strict concurrency.**
- New app-target files require `project.yml`/`xcodegen generate` (the `.xcodeproj` is generated).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Raw-date → logged-day resolution (honesty choke-point) | Pure logic (EndoInsights, Foundation-only) | — | Must be provable without rendering (the `ChartSeriesGrouping` playbook); reused by Phases 15–16 |
| Gesture recognition + raw selection value | SwiftUI/Swift Charts framework (`chartXSelection`) | — | Framework owns arbitration with the List; no custom gesture code anywhere |
| Selection chrome (rule mark, highlight point, tooltip) | View (`InsightTrendChart`) | — | Renders purely from resolved selection state; conditional on `allowsSelection` |
| Haptic feedback | View (UIKit `UISelectionFeedbackGenerator` held in view `@State`) | — | UI-side effect keyed on resolved-day change; locked in CONTEXT |
| Accessibility (per-mark values, Audio Graph, tooltip a11y value) | View (`InsightTrendChart`) | — | Render-inert descriptors; per-mark labels already exist |
| Tooltip copy | `InsightsCopy` (audited app-side copy surface) | `CopyAuditTests` | Dashboard chrome belongs to the dashboard's audited surface (UI-SPEC decision) |
| E2E selection seam | `AppDependencies` (launch-arg parse) → EnvironmentKey → view | XCUITest target | Mirrors every existing `-uitest-*` seam; production path reads nil and no-ops |
| PDF protection | By construction (`allowsSelection: Bool = false` default; renderer untouched) | ReportFlowUITests + manual PDF check | The `inkColor` precedent — zero diff in `ReportPDFRenderer.swift` |

## Standard Stack

### Core (all already present — nothing to install)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift Charts | iOS 18 SDK (Xcode 26.5) | `chartXSelection`, `RuleMark`, annotation tooltip, highlight `PointMark` | First-party; already the project's only chart layer; all APIs ≤ iOS 17 at an iOS 18 floor [VERIFIED: milestone STACK.md, Apple docs JSON 2026-06-10] |
| SwiftUI | iOS 18+ | `@State` selection binding, `.onChange` haptic trigger, EnvironmentKey seam | Existing patterns (`\.insightsCoordinator`, `\.checkInRouter` keys) [VERIFIED: codebase] |
| UIKit (`UISelectionFeedbackGenerator`) | iOS 10+ | Selection haptic on day change | Locked in CONTEXT [CITED: developer.apple.com/documentation/uikit/uiselectionfeedbackgenerator] |
| Accessibility framework (`AXChartDescriptor`) | iOS 15+ | Audio Graph / non-gesture per-day value access | `init(title:summary:xAxis:yAxis:additionalAxes:series:)`, `xAxis: any AXDataAxisDescriptor`, `yAxis: AXNumericDataAxisDescriptor?` [CITED: developer.apple.com/documentation/accessibility/axchartdescriptor] |
| EndoInsights (local SPM) | local | Home of the new pure `ChartScrubSelection` + its Swift Testing tests | Foundation-only boundary enforced by `BoundaryLintTests`; `InsightPoint` lives here [VERIFIED: codebase] |

### The exact APIs (availability re-confirmed)

| API | iOS | Note |
|-----|-----|------|
| `chartXSelection(value:)` | 17.0 | `nonisolated func chartXSelection<P>(value: Binding<P?>) -> some View where P: Plottable` — bind `Date?` [CITED: developer.apple.com/documentation/swiftui/view/chartxselection(value:) — signature + availability from docs JSON] |
| `.annotation(position:alignment:spacing:overflowResolution:content:)` | 17.0 | `overflowResolution: .init(x: .fit(to: .chart), y: .disabled)` pins the tooltip inside the plot [VERIFIED: milestone STACK.md, Apple docs JSON] |
| `RuleMark` / `PointMark` / `.zIndex` / `.symbolSize` | 16.0 | Already in the SDK floor; `PointMark` already used in this very chart [VERIFIED: codebase] |
| `AXChartDescriptor` + `AXChartDescriptorRepresentable` + `.accessibilityChartDescriptor(_:)` | 15.0 | Categorical X axis allowed (`xAxis: any AXDataAxisDescriptor`) [CITED: developer.apple.com/documentation/accessibility/axchartdescriptor] |
| `UISelectionFeedbackGenerator.prepare()` / `.selectionChanged()` | 10.0 | Device-only (Simulator no-op); respects system haptic settings [CITED: developer.apple.com/documentation/uikit/uiselectionfeedbackgenerator] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `chartXSelection(value:)` | `chartOverlay` + manual `DragGesture` | Pre-iOS-17 idiom; documented iOS 18 scroll-conflict breakage for custom gestures in scroll containers — explicitly forbidden by UI-SPEC |
| `UISelectionFeedbackGenerator` | SwiftUI `.sensoryFeedback(.selection, trigger:)` (iOS 17+) | Simpler/SwiftUI-native, but CONTEXT **locks** the UIKit generator — do not substitute |
| Resolver in EndoInsights | Resolver in app target (next to `ChartSeriesGrouping`) | App placement needs `xcodegen generate` and re-test wiring; package placement is public-API reusable for Phases 15–16, Swift-Testing-native, and zero project-file churn. Either is honest; package wins (see Pattern 1) |
| New seed variant `-uitest-seed-scrub` | Mutating `seedThresholdCrossingInsights` | Mutating the shared seed changes data under four existing E2E tests; a per-feature seed mirrors the established precedent (`-uitest-seed-report/-recommendations/-flare/-demo`) |

**Installation:** nothing to install — no SPM additions, no `project.yml` package changes. `xcodegen generate` IS required for any new app-target source/test file (e.g. a new XCUITest file); EndoInsights package files need no regeneration.

## Package Legitimacy Audit

Not applicable — this phase installs **zero** external packages (first-party OS frameworks + the existing local EndoInsights package only). No registry lookups required.

## Architecture Patterns

### System Architecture Diagram

```
                 DASHBOARD (live)                                    PDF (protected)
┌──────────────────────────────────────────────────┐   ┌─────────────────────────────────┐
│ InsightsView (List)                              │   │ ReportPDFRenderer (UNTOUCHED)   │
│  └ InsightCardView (.trend branch)               │   │  builds FRESH InsightTrendChart │
│     └ InsightTrendChart(allowsSelection: true) ✎ │   │  (inkColor:.black; allows-      │
│         │                                        │   │   Selection stays default false)│
│         │ touch/drag (framework arbitration:     │   │  → ImageRenderer → vector PDF   │
│         │ vertical movement cedes to List scroll)│   │  chartXSelection NEVER attached │
│         ▼                                        │   └─────────────────────────────────┘
│  .chartXSelection(value: $rawSelectedDate)       │
│   [attached ONLY when allowsSelection == true]   │
│         │ Date? (raw, continuous in-domain)      │
│         ▼                                        │
│  ChartScrubSelection.resolve(raw, points, cal) ★ │◄── pure, EndoInsights, unit-tested
│         │ Resolution(day, point?)? — exact match │    (the honesty choke-point; Phases
│         ▼                ▼                ▼      │     15/16 reuse it unchanged)
│   RuleMark+tooltip   highlight      haptic on    │
│   (annotation .top,  PointMark      day CHANGE   │
│    overflow .fit)    (logged only)  (UIKit gen.) │
│         │                                        │
│         ▼ a11y: tooltip accessibilityValue +     │
│   AXChartDescriptor (Audio Graph, discrete,      │
│   logged days only) + existing per-mark labels   │
└──────────────────────────────────────────────────┘
         ▲
         │ initial selection (E2E only)
  AppDependencies: "-uitest-select-day yyyy-MM-dd" parsed IFF paired with -uitest-reset
         → EnvironmentKey \.uiTestScrubDay (Date?, default nil — production-inert)
```

### File Change Inventory (verified against the codebase)

| File | Change | xcodegen? |
|------|--------|-----------|
| `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift` | NEW — pure resolver | no |
| `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` | NEW — Swift Testing unit tests | no |
| `Sources/EndoApp/Features/Insights/InsightsCopy.swift` | +`scrubNotLogged`, +`scrubSeverity(_:)`, both appended to `auditableStrings` | no (existing file) |
| `Tests/EndoAppTests/CopyAuditTests.swift` | No structural change needed — the four `insightsCopy_*` scans iterate `auditableStrings`, so registration alone extends coverage. Optionally add a pinning `#expect` for the literal "Not logged" | no (existing file) |
| `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` | Additive: `allowsSelection`, `selectionA11yIDRoot` params; `@State rawSelectedDate`; chrome; haptic; `AXChartDescriptor` | no (existing file) |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` | One line: `.trend` branch passes `allowsSelection: true` (+ the a11y id root) | no (existing file) |
| `Sources/EndoApp/App/AppDependencies.swift` | Parse `-uitest-select-day` (paired with `uiTestReset`); new `-uitest-seed-scrub` fixture; inject env value | no (existing file) |
| `Sources/EndoApp/App/EndoAppApp.swift` (or wherever root `.environment` injection lives) | Inject `\.uiTestScrubDay` | no (existing file) |
| New EnvironmentKey (e.g. in `AppDependencies.swift` or a small file) | `UITestScrubDayKey` (default nil) | only if a NEW file |
| `Tests/EndoAppUITests/ChartScrubUITests.swift` | NEW — seam-driven E2E (or extend `InsightsDashboardUITests.swift` to avoid xcodegen) | **yes if new file** |
| `Features/Report/ReportPDFRenderer.swift` | **ZERO diff** (acceptance criterion — verify via `git diff`) | — |

### Pattern 1: The pure resolver — `ChartScrubSelection` (EndoInsights)

**What:** Resolve the raw `Date` from `chartXSelection` to `(day, exact-logged-point-or-nil)`.
**Placement decision (Claude's discretion, resolved):** EndoInsights package. CONTEXT.md says "following the `ChartSeriesGrouping` playbook (EndoInsights)" — note `ChartSeriesGrouping` actually lives in the **app target** (`Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift`, tests in `Tests/EndoAppTests/ChartSeriesGroupingTests.swift`) [VERIFIED: codebase]. The *playbook* (pure Foundation, calendar-injected, unit-tested without rendering) is what's being followed; the package is the better home because `InsightPoint` is a package type, Phases 15–16 consume the resolver, package files need no `xcodegen`, and `BoundaryLintTests` enforces the Foundation-only boundary automatically. Must be `public` (consumed by the app target).

```swift
// EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift  (pattern — follows ChartSeriesGrouping)
import Foundation

/// Resolves a raw chart-selection Date to the EXACT logged calendar day — or an explicit gap.
/// NEVER interpolates, NEVER snaps to the nearest logged day (the honesty choke-point, CHART-02).
public enum ChartScrubSelection {

    public struct Resolution: Sendable, Equatable {
        /// The calendar day under the finger (startOfDay) — always real (the date axis is real).
        public let day: Date
        /// The logged point on EXACTLY that day, or nil = "Not logged" (absence ≠ 0;
        /// a literal severity-0 point is returned as a real point, value 0).
        public let point: InsightPoint?
    }

    public static func resolve(_ rawDate: Date?, in points: [InsightPoint],
                               calendar: Calendar) -> Resolution? {
        guard let rawDate else { return nil }
        let day = calendar.startOfDay(for: rawDate)
        let match = points.first { calendar.isDate($0.date, inSameDayAs: day) }
        return Resolution(day: day, point: match)
    }
}
```

**Unit-test fixtures (mirror `ChartSeriesGroupingTests`, fixed UTC calendar):** raw nil → nil; raw inside a gap → `point == nil` with correct `day`; raw on a logged severity-0 day → `point?.value == 0` (NOT nil); raw at 23:59 of a logged day → same-day match (startOfDay normalization); day-boundary across a DST transition (use a calendar with `TimeZone(identifier: "Europe/Berlin")` around a spring-forward date); unsorted points input.

**Data-shape fact for the tests [VERIFIED: codebase]:** the `.trend` severity insight (`signalKey "symptom.<type>.severity"`) carries `severityPoints` — one `InsightPoint` per logged day, `date = snap.day` (already startOfDay), `value = Double(severity)` including literal 0 (`TrendsEngine.swift:342-346`). Frequency trends carry 0/1-valued points (`TrendsEngine.swift:353`) — see Open Question 2.

### Pattern 2: Additive selection layer on `InsightTrendChart` (the `inkColor` precedent)

**What:** `allowsSelection: Bool = false` defaulted param after `inkColor` (`InsightTrendChart.swift:38`); `chartXSelection` attached ONLY when true; chrome rendered ONLY when the resolver returns non-nil. Since `allowsSelection` is immutable per instance, the conditional branch has stable view identity.

```swift
// InsightTrendChart (additive — pattern):
var allowsSelection: Bool = false                       // after inkColor; PDF keeps default
var selectionA11yIDRoot: String? = nil                  // e.g. "insights.card.<signalKey>.chart"
@State private var rawSelectedDate: Date?
@State private var haptics = UISelectionFeedbackGenerator()
@Environment(\.uiTestScrubDay) private var uiTestScrubDay   // nil in production — inert

private var resolvedSelection: ChartScrubSelection.Resolution? {
    ChartScrubSelection.resolve(rawSelectedDate, in: points, calendar: calendar)
}

private var chart: some View {
    let base = Chart {
        // ... existing runs/points marks BYTE-IDENTICAL ...
        if let sel = resolvedSelection {                 // ChartContentBuilder supports `if`
            selectionMarks(sel)                          // Pattern 3
        }
    }
    // ... existing chartXScale/chartYScale/axes/animation modifiers UNCHANGED ...
    return base
}

var body: some View {
    VStack(alignment: .leading, spacing: 8) {
        Text(summaryText)...                             // unchanged
        Group {
            if allowsSelection {
                chart.chartXSelection(value: $rawSelectedDate)
            } else {
                chart                                    // PDF path: modifier NEVER attached
            }
        }
        .frame(height: 200)
        .accessibilityLabel(Text(summaryText))
    }
    .onChange(of: resolvedSelection?.day) { old, new in  // haptic: day CHANGE only (Pattern 4)
        if old != nil, new != nil, old != new { haptics.selectionChanged() }
        if new != nil { haptics.prepare() }              // keep the engine warm mid-scrub
    }
    .onAppear {
        if allowsSelection, let day = uiTestScrubDay, xDomain.contains(day) {
            rawSelectedDate = day                        // E2E seam initial selection
        }
    }
}
```

Notes:
- `resolvedSelection` recomputes per body evaluation — O(n) over ≤ 90 points, negligible. `runs` also recomputes per scrub frame (pre-existing computed property); ≤ 90 points sorted per frame is fine — do NOT pre-optimize.
- The `xDomain.contains(day)` guard on the seam matters: a programmatic selection outside the card's window would draw an out-of-domain `RuleMark` and mislabel a logged day as "Not logged" on narrow-window cards (the resolver only sees that card's points). Keep selections within the last-7 window in the seed design (see Pattern 6).
- The existing `.animation(reduceMotion ? nil : .default, value: points)` is scoped to `points` [VERIFIED: InsightTrendChart.swift:128] — selection state is not an input, so scrubbing never re-animates the series. Keep it that way (no new `withAnimation`, no transitions on the tooltip).
- Selection chrome must NOT carry the per-mark `accessibilityLabel/Value` the data marks have — the tooltip is the single new a11y element (UI-SPEC).

### Pattern 3: The lollipop chrome (Health pattern, locked)

```swift
// Inside ChartContentBuilder, only when resolvedSelection != nil:
@ChartContentBuilder
private func selectionMarks(_ sel: ChartScrubSelection.Resolution) -> some ChartContent {
    RuleMark(x: .value("Selected", sel.day))
        .foregroundStyle(Color.secondary.opacity(0.5))   // never accent — UI-SPEC
        .zIndex(-1)                                      // behind the data line
        .annotation(position: .top, spacing: 0,
                    overflowResolution: .init(x: .fit(to: .chart), y: .disabled)) {
            tooltip(sel)                                 // a plain SwiftUI view — Pattern below
        }
    if let point = sel.point {                           // logged day ONLY — no point on gaps
        PointMark(x: .value("Selected", sel.day), y: .value("Severity", point.value))
            .foregroundStyle(inkColor)
            .symbolSize(100)
    }
}

// Tooltip view (UI-SPEC-locked layout/typography/colors):
private func tooltip(_ sel: ChartScrubSelection.Resolution) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(sel.day, format: .dateTime.month().day())   // same vocabulary as the axis
            .font(.caption2).foregroundStyle(.secondary)
        if let point = sel.point {
            Text(InsightsCopy.scrubSeverity(Int(point.value.rounded())))
                .font(.caption).fontWeight(.semibold).foregroundStyle(.primary)
        } else {
            Text(InsightsCopy.scrubNotLogged)
                .font(.caption).foregroundStyle(.secondary)
                .accessibilityIdentifier(notLoggedID)    // "<root>.tooltip.notLogged"
        }
    }
    .padding(8)
    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
    .accessibilityElement(children: .ignore)             // ONE element, quiet
    .accessibilityValue(Text(a11yReadout(sel)))          // "Jun 5 — Severity 6 of 10" / "… — Not logged"
    .accessibilityIdentifier(tooltipID)                  // "<root>.tooltip"
}
```

A11y ids: derive from `selectionA11yIDRoot` (the card passes `"insights.card.\(insight.signalKey).chart"`); when nil (PDF/previews) skip ids — they could never render anyway (selection is impossible with `allowsSelection == false`). Known quirk to inherit: a SwiftUI `Section`'s a11y id bleeds onto descendant cells, but sub-element ids (`.confidence` etc.) remain findable via descendant queries [VERIFIED: InsightsDashboardUITests.swift:67-74] — the tooltip ids follow the same sub-element pattern and will be findable the same way.

### Pattern 4: Haptic lifecycle (Claude's discretion, resolved)

- ONE `UISelectionFeedbackGenerator` held in `@State` (survives re-renders; created once per view identity). UIKit class used from main-actor view code — Swift 6 clean.
- Fire `selectionChanged()` ONLY on `old != nil && new != nil && old != new` of the **resolved day** (`Date?`) — this naturally covers logged↔gap transitions (the day changes even when the point is nil) and naturally excludes initial touch (nil→day) and release (day→nil), exactly the CONTEXT table.
- `prepare()` whenever selection is non-nil (keeps the Taptic Engine warm mid-scrub at trivial cost); no explicit release needed — the generator idles when unused [CITED: developer.apple.com/documentation/uikit/uiselectionfeedbackgenerator].
- Simulator is a no-op; device verification is the locked manual checkpoint.

### Pattern 5: `AXChartDescriptor` (CHART-04)

```swift
// Conformance held by the chart (or a small struct it builds):
extension InsightTrendChart: AXChartDescriptorRepresentable {
    func makeChartDescriptor() -> AXChartDescriptor {
        let xAxis = AXCategoricalDataAxisDescriptor(
            title: "Day",
            categoryOrder: sortedPoints.map(dayLabel))    // "Jun 5" — same date vocabulary
        let yAxis = AXNumericDataAxisDescriptor(
            title: "Severity", range: 0...10, gridlinePositions: []) { "\(Int($0)) of 10" }
        let series = AXDataSeriesDescriptor(
            name: headline,
            isContinuous: false,                          // discrete tones — never sonically interpolates a gap
            dataPoints: sortedPoints.map { AXDataPoint(x: dayLabel($0), y: $0.value) })
        return AXChartDescriptor(title: headline, summary: summaryText,
                                 xAxis: xAxis, yAxis: yAxis, additionalAxes: [], series: [series])
    }
}
// attached via: .accessibilityChartDescriptor(self)
```

`AXChartDescriptor` init verified: `xAxis: any AXDataAxisDescriptor` (categorical is legal), `yAxis: AXNumericDataAxisDescriptor?`, `series: [AXDataSeriesDescriptor]`, iOS 15+ [CITED: developer.apple.com/documentation/accessibility/axchartdescriptor]. The exact `AXDataPoint`/axis-descriptor initializer argument labels above are [ASSUMED] (training knowledge — Apple's per-type doc pages were not individually fetched); the compiler resolves them trivially, no design risk. Logged points ONLY in the series (gaps omitted, never zeros). UI-SPEC posture: attach in both modes (render-inert; `ImageRenderer` rasterizes visuals only) — but if PDF re-verification shows ANY diff, gate it behind `allowsSelection` too.

### Pattern 6: The `-uitest-select-day` seam + `-uitest-seed-scrub` fixture

**Established posture [VERIFIED: AppDependencies.swift:47-110]:** there is NO `#if DEBUG` in `AppDependencies` — every seam is a plain `ProcessInfo.processInfo.arguments` check whose effect activates only when `uiTestReset` is also present (which forces the in-memory store), making it unreachable-in-production by pairing. CONTEXT's "DEBUG-gated … covered by the same release source-guard posture" maps to exactly this pairing pattern. Recommended: mirror it precisely (parse `-uitest-select-day` only when `uiTestReset == true`); optionally wrap the parse in `#if DEBUG` for belt-and-suspenders (a one-line addition that satisfies the CONTEXT wording literally without polluting any view code).

**Threading (the no-production-pollution path):** parse in `AppDependencies` → a new `EnvironmentValues.uiTestScrubDay: Date?` key (default `nil`) injected at the App root exactly like `\.insightsCoordinator` / `\.checkInRouter` [VERIFIED: codebase patterns] → `InsightTrendChart` reads it in `.onAppear` (Pattern 2). Production launch: arg absent → value nil → zero behavior. Every `allowsSelection: true` chart gets the same initial selection — fine: the E2E asserts via `firstMatch`/specific signalKey-rooted ids.

```swift
// AppDependencies (pattern):
var uiTestScrubDay: Date? = nil
if uiTestReset,
   let argIndex = ProcessInfo.processInfo.arguments.firstIndex(of: "-uitest-select-day"),
   ProcessInfo.processInfo.arguments.indices.contains(argIndex + 1) {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = .current                        // must match Calendar.current in the chart
    formatter.dateFormat = "yyyy-MM-dd"
    uiTestScrubDay = formatter.date(from: ProcessInfo.processInfo.arguments[argIndex + 1])
        .map { Calendar.current.startOfDay(for: $0) }    // defensive: garbage → nil, never a crash
}
```

**Seed design (`-uitest-seed-scrub`, new variant — do NOT mutate `seedThresholdCrossingInsights`):** the existing insights seed is 21 consecutive severity-6 days ending **yesterday** with no gap and no severity-0 [VERIFIED: AppDependencies.swift:298-306]. Because trend cards exist per window and a signalKey's id root is shared across them, the selected day MUST fall inside the **last-7** window so every window-card resolves the same state. Recommended shape (all offsets from `Calendar.current.startOfDay(for: Date())`):

| Offsets | Data |
|---------|------|
| 21…1 | pelvicPain severity 6 (the dense spine — trend self-gate ≥ 3 holds) |
| **3** | **SKIPPED** — the in-window gap day (select `today-3` → "Not logged") |
| **2** | **severity 0** — the logged-zero day (select `today-2` → "Severity 0 of 10") |
| logged day to assert | `today-1` (severity 6 → "Severity 6 of 10") |

The XCUITest computes each `yyyy-MM-dd` string immediately before `app.launch()` (same midnight-race class as every existing today-relative seed — accepted). Three launches, one assertion target each: tooltip `accessibilityValue` contains the expected readout; `.notLogged` existence/absence. Zero coordinate gestures, zero sleeps. At most ONE smoke real-gesture test (tap plot center on the dense seed; assert via ids only).

### Anti-Patterns to Avoid

- **Any custom gesture on the in-List chart** (`DragGesture`, `simultaneousGesture`, `highPriorityGesture`, `chartGesture`): documented iOS 18 breakage of scroll coexistence for custom gestures in scroll containers [CITED: developer.apple.com/forums/thread/760035]; locked out by UI-SPEC.
- **Nearest-point snapping / `proxy.value(atX:)` lookups without exact day-equality:** fabricates values on gap days (Pitfall P1) — the resolver is the only legal path from raw date to value.
- **Selection state in any shared `@Observable`:** a scrub frame must invalidate only the chart body, never the List (perf trap, locked: per-card `@State`).
- **Editing `ReportPDFRenderer.swift` at all:** zero-diff is the acceptance criterion; the default param protects it by construction.
- **Writing tooltip strings inline in the view:** every chrome string goes through `InsightsCopy` + `auditableStrings` registration FIRST (an unregistered string silently escapes the scans).
- **`UIAccessibility.post` on day change:** locked out (quiet selection — `accessibilityValue` carries the readout).
- **Animating the selection chrome:** keep selection out of `.animation(..., value: points)` inputs; no tooltip transitions (Reduce Motion then needs no new gate).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gesture recognition + scroll arbitration | Custom drag/tap recognizers with manual hit-testing | `chartXSelection(value:)` | The framework owns List-vs-chart arbitration; custom gestures are the documented iOS 18 breakage vector |
| Tooltip edge-clipping math | Manual `GeometryReader` position clamping in `chartOverlay` | `.annotation(overflowResolution: .init(x: .fit(to: .chart), y: .disabled))` | First-party overflow resolution, verified iOS 17 |
| Coordinate→value conversion | `ChartProxy.value(atX:)` + pixel math | The `chartXSelection` binding | The binding already delivers the plotted-domain value |
| VoiceOver chart navigation | A custom rotor/adjustable-element day stepper | `AXChartDescriptor` (Audio Graph) + existing per-mark labels | First-party, already half-adopted (per-mark labels shipped in v1.0) |
| Day-equality math | Epoch-seconds / 86 400-multiples comparisons | `calendar.startOfDay` + `calendar.isDate(_:inSameDayAs:)` | DST-safe; the `ChartSeriesGrouping` precedent uses exactly this |
| Haptics | Core Haptics engine setup | `UISelectionFeedbackGenerator` | Locked in CONTEXT; one-class solution |

**Key insight:** every "hard" part of this phase (gesture arbitration, tooltip overflow, audio graphs, haptics) has a first-party single-API answer at the iOS 18 floor; the ONLY custom logic that should exist is the 10-line pure resolver — which is custom precisely because its honesty semantics (exact-match, never-snap) are the opposite of what generic libraries do.

## Common Pitfalls

### Pitfall 1: The tooltip lies on gap days (interpolation/snap) **[INVARIANT-BREAKING — milestone P1]**
**What goes wrong:** `chartXSelection` returns a continuous Date for ANY in-domain position, including gaps; tutorial code snaps to the nearest point or interpolates.
**Why it happens:** the selection API is position-based; every public example demonstrates nearest-point snapping.
**How to avoid:** ALL value lookups go through `ChartScrubSelection.resolve` (exact day match); the highlight `PointMark` renders only when `point != nil`; unit tests pin gap→nil and severity-0→0.
**Warning signs:** any `min(by: distance)` over points; a scrub across the seeded gap that shows a continuously moving value.

### Pitfall 2: Scroll-vs-scrub arbitration regresses per OS version **[milestone P7]**
**What goes wrong:** the chart lives in a `List` (`InsightsView.swift:47`); iOS 18 broke scroll coexistence for custom chart gestures, iOS 26 betas regressed further [CITED: developer.apple.com/forums/thread/760035, /thread/794212].
**How to avoid:** framework-standard `chartXSelection` ONLY, no custom gestures anywhere near the chart; the manual scroll-vs-scrub OS-matrix check is a locked checkpoint and a recurring release-checklist item (already in STATE.md Blockers).
**Warning signs:** any gesture modifier in the diff; QA note "Insights screen sticks when I swipe up over a chart".

### Pitfall 3: The PDF path inherits selection chrome **[milestone P8]**
**What goes wrong:** `ReportPDFRenderer.drawChart` builds a fresh `InsightTrendChart` (`ReportPDFRenderer.swift:309-315`) — any non-defaulted behavior change leaks into the verified vector PDF.
**How to avoid:** `allowsSelection = false` default + the modifier conditionally NOT attached (not merely inert); `ReportPDFRenderer` zero-diff verified by `git diff`; ReportFlowUITests re-run; manual PDF export check. If `AXChartDescriptor` shows ANY render diff, gate it behind `allowsSelection` too.
**Warning signs:** `ReportPDFRenderer` appears in the diff; a rule mark in an exported PDF.

### Pitfall 4: Gesture-coordinate E2Es flake forever **[milestone P9]**
**How to avoid:** honesty invariants live in the pure unit tests; E2E uses the `-uitest-select-day` seam + a11y ids + `waitForExistence` only; at most one smoke gesture test. Note: a seam-set selection has no gesture to end, so the tooltip persists — assert content, never lifecycle, via the seam; lifecycle (dismiss-on-release) is the smoke test / manual checkpoint.
**Warning signs:** `XCUICoordinate`, `normalizedOffset`, `sleep` in new tests.

### Pitfall 5: New copy ships unscanned or trips a substring landmine **[milestone P3-class]**
**What goes wrong:** strings not in `auditableStrings` are never scanned; the scans are case-insensitive SUBSTRING scans (`"her" ⊂ "higher/there/other"`, `"will"`, `"causes"`, `"treat"`, `"use" ⊂ "because"` for surfaces with the imperative scan).
**How to avoid:** register `scrubNotLogged` + `scrubSeverity(6)` in `auditableStrings` in the SAME task that introduces them, BEFORE any view renders them. Verified clean this session: "Not logged" hosts no forbidden substring; "Severity {n} of 10" — "Severity" contains no "her"/"she"/"will"; digits are legal because the bare-digit scan is scoped to the three confidence words only and the whole-surface scan checks `%/p=/r=` tokens only [VERIFIED: CopyAuditTests.swift:176-198, the `supportingEvidence(loggedDays: 12)` precedent].
**Warning signs:** a string literal in `InsightTrendChart.swift`'s tooltip; `auditableStrings` unchanged in the copy task's diff.

### Pitfall 6: The seam selects a day outside a card's window
**What goes wrong:** trend cards exist per window (last7/30/90/allTime) sharing the signalKey-rooted a11y id; a `today-9` selection is a LOGGED day for the 30-day card but resolves "Not logged" on the last7 card (the resolver only sees that card's points) — ambiguous assertions, and an out-of-domain `RuleMark` on narrow cards.
**How to avoid:** seed-design rule — all three assertion days (`logged`, `gap`, `severity-0`) inside the last-7 window; plus the defensive `xDomain.contains(day)` guard in `.onAppear` (Pattern 2).
**Warning signs:** an E2E that selects an offset > 6 days back; tooltip assertions using ambiguous `firstMatch` across differing window states.

### Pitfall 7: Stale assumptions from CONTEXT.md
**What goes wrong:** two CONTEXT statements don't match the code: `ChartSeriesGrouping` is in the app target (not EndoInsights), and the seams are runtime-paired (not `#if DEBUG`-gated).
**How to avoid:** the plan should cite the verified locations/postures from this document, not CONTEXT's shorthand. Neither changes any locked decision — only where the new code goes and how the gate is spelled.

## Code Examples

All primary patterns are in Architecture Patterns above (Patterns 1–6) with sources inline. Supplementary:

### Card-side change (one line + id root)
```swift
// InsightCardView.supportingData, .trend case [VERIFIED: InsightCardView.swift:154-162]:
InsightTrendChart(
    points: insight.points,
    window: insight.window,
    directionWord: directionWord,
    headline: insight.headline,
    allowsSelection: true,                                   // ← the phase's dashboard switch
    selectionA11yIDRoot: "\(idRoot).chart"                   // tooltip ids hang off this
)
.accessibilityIdentifier("\(idRoot).chart")                  // unchanged
```

### New copy (audited surface)
```swift
// InsightsCopy (pattern — register in auditableStrings in the SAME change):
/// Tooltip value line — matches the per-mark accessibilityValue vocabulary ("severity n of 10").
static func scrubSeverity(_ value: Int) -> String { "Severity \(value) of 10" }
/// The LOCKED gap wording (never "0", never "No data", never an error tone).
static let scrubNotLogged = "Not logged"
// auditableStrings: append scrubNotLogged and scrubSeverity(6) (the supportingEvidence(loggedDays: 12) precedent)
```

### Seam-driven E2E (pattern, clones the InsightsDashboardUITests harness)
```swift
// Compute the arg IMMEDIATELY before launch (today-relative, same as the seeds):
let formatter = DateFormatter()
formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.dateFormat = "yyyy-MM-dd"
let gapDay = formatter.string(from: Calendar.current.date(byAdding: .day, value: -3, to: .now)!)
let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-scrub", "-uitest-select-day", gapDay])

let notLogged = app.descendants(matching: .any)
    .matching(NSPredicate(format: "identifier ENDSWITH '.chart.tooltip.notLogged'")).firstMatch
XCTAssertTrue(notLogged.waitForExistence(timeout: 15),
              "Selecting the seeded gap day must show the explicit Not-logged tooltip (CHART-02).")
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `chartOverlay` + manual `DragGesture` + `ChartProxy.value(atX:)` | `chartXSelection(value:)` | iOS 17 (WWDC23) | Framework owns gesture + arbitration; the old idiom is the iOS 18 scroll-breakage vector |
| `plotAreaFrame` | `plotFrame` | iOS 17 | Not needed in this phase (no overlay math at all) |
| UIKit-only haptics wiring | `.sensoryFeedback(.selection, trigger:)` exists (iOS 17+) | WWDC23 | NOT used — CONTEXT locks `UISelectionFeedbackGenerator`; noted for awareness only |

**Deprecated/outdated:** nothing this phase touches; all chosen APIs are WWDC22–23 vintage and stable through the iOS 26 SDK.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `chartXSelection` sets the binding to nil on finger release (tooltip auto-dismisses) — Apple docs are silent; community-corroborated [ASSUMED, cross-checked MEDIUM] | Patterns 2–3 / CHART-03 | Low: chrome renders iff selection non-nil, so it is correct under any nil-timing; if a tap leaves a sticky selection on iOS 18, the smoke test/manual checkpoint catches it and a `.onTapGesture`-free mitigation (e.g. clearing on scroll) would be a follow-up — no architectural change |
| A2 | Exact initializer argument labels for `AXCategoricalDataAxisDescriptor`/`AXNumericDataAxisDescriptor`/`AXDataPoint` as sketched in Pattern 5 [ASSUMED] | Pattern 5 | Trivial: compiler-resolved at implementation time; the verified part (the `AXChartDescriptor` init shape, categorical-x legality, iOS 15 floor) is CITED |
| A3 | `ChartContentBuilder` supports `if let` for the conditional selection marks [ASSUMED — standard result-builder behavior, widely used in Apple samples] | Pattern 3 | Trivial: if a builder limitation surfaced, wrap marks in `.opacity` gating — compile-time discovery |
| A4 | Setting `rawSelectedDate` programmatically (the seam) renders the same chrome as a gesture-driven selection [ASSUMED — chrome derives purely from state, no gesture dependence in the design] | Pattern 6 | Low: it's plain `@State`-driven rendering; verified the moment the E2E first runs |

## Open Questions

1. **Does tap (vs drag) leave a persistent selection on iOS 18?**
   - What we know: drag tracks continuously and clears on release (community consensus); Apple docs don't specify tap semantics.
   - What's unclear: whether a single tap toggles a sticky selection on some OS versions.
   - Recommendation: design is already invariant (chrome ⇔ non-nil binding); fold into the existing scroll-vs-scrub manual checkpoint ("tap, observe dismiss behavior").

2. **Frequency-trend cards get the same tooltip vocabulary.**
   - What we know: `.trend` frequency insights carry 0/1-valued points [VERIFIED: TrendsEngine.swift:353]; the shipped per-mark a11y already reads "severity 1 of 10" on those charts — a pre-existing vocabulary imprecision.
   - What's unclear: whether the tooltip should special-case frequency charts.
   - Recommendation: mirror the shipped per-mark vocabulary (consistent, in-scope, locked copy); a vocabulary fix for frequency charts is a separate, pre-existing concern — defer (do not silently expand scope).

3. **Multiple window-cards share a signalKey-rooted tooltip id.**
   - What we know: trend insights exist per window with one signalKey; sub-element ids are findable via descendant queries.
   - Recommendation: seed/selection design keeps all assertion days inside last-7 so every card shows the SAME state, making `firstMatch` unambiguous (Pitfall 6); no id-scheme change needed.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode / xcodebuild | build + all tests | ✓ | Xcode 26.5 | — |
| Swift toolchain | Swift 6 strict concurrency | ✓ | 6.3.2 | — |
| XcodeGen | new app-target/UITest files | ✓ | 2.45.4 | — |
| iPhone 16 Simulator | test destination | ✓ (booted) | — | — |
| git repo | commits | ✓ (branch `master`) | — | — |
| Physical device | haptic + scroll-feel verification | ✗ (not probed; CI-external) | — | Locked manual checkpoint at the phase gate (Simulator no-ops haptics by design) |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** physical device → end-of-phase human-verify checkpoint (already locked in CONTEXT).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (EndoInsightsTests, EndoAppTests) + XCTest/XCUITest (EndoAppUITests) — coexisting targets, not interoperable |
| Config file | `project.yml` (XcodeGen; scheme `EndoApp` includes `EndoInsights/EndoInsightsTests`) |
| Quick run command | `swift test --package-path EndoInsights --filter ChartScrubSelectionTests` (Foundation-only, seconds) |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` — **known issue:** the full `-only-testing:EndoAppTests` target has stalled (>15 min) in this environment repeatedly (STATE.md 08-03/08-04); the established verification pattern is targeted suites + the relevant UI suites |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CHART-01 | Raw date resolves to exact logged day + value | unit | `swift test --package-path EndoInsights --filter ChartScrubSelectionTests` | ❌ Wave 0 |
| CHART-01 | Tooltip renders date + "Severity n of 10" on a seeded logged day | e2e (seam) | `xcodebuild test … -only-testing:EndoAppUITests/ChartScrubUITests` | ❌ Wave 0 |
| CHART-02 | Gap day → `point == nil`; severity-0 day → literal 0 point | unit | same ChartScrubSelectionTests filter | ❌ Wave 0 |
| CHART-02 | Gap day shows `.notLogged` id; severity-0 shows "Severity 0 of 10" and NO `.notLogged` | e2e (seam) | same ChartScrubUITests | ❌ Wave 0 |
| CHART-03 | Haptic on day change only | **manual-only** — `UISelectionFeedbackGenerator` is a Simulator no-op; device checkpoint | — | — |
| CHART-03 | Tooltip lives while touching / dismisses on release | smoke gesture test (≤1, id-asserted) + manual checkpoint | optional in ChartScrubUITests | ❌ Wave 0 |
| CHART-04 | Tooltip `accessibilityValue` carries the full readout | e2e (seam) | same ChartScrubUITests (`element.value`) | ❌ Wave 0 |
| CHART-04 | Audio Graph plays discrete logged-day tones; per-mark swipe-through intact | **manual-only** — VoiceOver/Audio Graph not XCUITest-automatable | — | — |
| Copy gate | New strings pass all four `insightsCopy_*` scans | unit | `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` | ✅ (registration extends scans automatically) |
| PDF gate | Report flow unchanged | e2e regression | `xcodebuild test … -only-testing:EndoAppUITests/ReportFlowUITests` | ✅ existing |
| PDF gate | Exported chart page visually unchanged; `ReportPDFRenderer.swift` zero-diff | manual + `git diff --stat` check | — | — |
| Dashboard regression | Existing dashboard E2Es stay green | e2e regression | `xcodebuild test … -only-testing:EndoAppUITests/InsightsDashboardUITests` | ✅ existing |

### Sampling Rate
- **Per task commit:** `swift test --package-path EndoInsights --filter ChartScrubSelectionTests` (package tasks) / `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` (copy task) / `xcodebuild build` (view tasks)
- **Per wave merge:** targeted suites touched by the wave + `-only-testing:EndoAppUITests/InsightsDashboardUITests`
- **Phase gate:** EndoInsights full package + CopyAuditTests + InsightsDashboardUITests + ReportFlowUITests + ChartScrubUITests all green; manual checkpoints (device haptic, scroll-vs-scrub OS check, PDF visual, VoiceOver/Audio Graph) surfaced to `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` — covers CHART-01/02 invariants (no xcodegen)
- [ ] `Tests/EndoAppUITests/ChartScrubUITests.swift` — covers CHART-01/02/04 E2E via the seam (**requires `xcodegen generate`**; alternatively extend `InsightsDashboardUITests.swift` to avoid regeneration)
- [ ] Framework install: none — all harnesses exist

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts (project posture) |
| V3 Session Management | no | — |
| V4 Access Control | no | Consent gating unchanged (chart lives inside `ConsentedShell` already) |
| V5 Input Validation | yes | Defensive `-uitest-select-day` parsing: fixed `en_US_POSIX yyyy-MM-dd` formatter, garbage → nil (never a crash), parse reachable ONLY under `-uitest-reset` pairing |
| V6 Cryptography | no | — |
| V14 Configuration | yes | Test-seam exposure: the new arg follows the established unreachable-in-production pairing (in-memory store gate); optional `#if DEBUG` belt-and-suspenders |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hidden state-manipulation seam in a shipping health app | Elevation of privilege | `-uitest-select-day` effect gated on `uiTestReset` pairing (mirrors all five existing seams [VERIFIED: AppDependencies.swift]); selection is ephemeral view state — even if triggered it touches no stored data |
| Health-interest metadata persisted outside the consent-gated store | Information disclosure | Selection state is per-card `@State`, never persisted (locked); no `@AppStorage`/`@SceneStorage` anywhere in the phase |
| E2E screenshots with personal data in `/tmp/e2e` | Information disclosure | Existing posture: Simulator + seed fixtures only; the new tests reuse the same harness |
| Numeric/statistical framing leaking into UI | Repudiation of wellness positioning | Tooltip copy through `InsightsCopy` + the four CopyAuditTests scans; counts/values are factual readouts (the `loggedDays: 12` precedent), never confidence |

## Sources

### Primary (HIGH confidence)
- Codebase, read directly this session: `InsightTrendChart.swift` (params, animation seam, a11y, axis contract), `ChartSeriesGrouping.swift` (app-target location, pure-helper playbook), `InsightCardView.swift` (`.trend` branch, idRoot), `InsightsCopy.swift` + `CopyAuditTests.swift` (scan mechanics, digit-scan scoping), `ClaimsPolicy.swift` (substring lists), `AppDependencies.swift` (seam posture — no `#if DEBUG`; seed shapes; EnvironmentKey precedent), `ReportPDFRenderer.swift:285-333` (fresh-chart construction), `InsightsView.swift` (List structure), `InsightsDashboardUITests.swift` (harness, id-bleed quirk), `TrendsEngine.swift:342-353` (trend point shapes), `Insight.swift` (`InsightPoint`)
- `.planning/research/STACK.md` (2026-06-10) — all Swift Charts selection API signatures + availability verified via Apple docs JSON endpoints
- developer.apple.com docs JSON (fetched this session): `chartxselection(value:)` (signature, iOS 17.0), `accessibility/axchartdescriptor` (full init signatures, iOS 15.0)

### Secondary (MEDIUM confidence)
- developer.apple.com/documentation/uikit/uiselectionfeedbackgenerator — prepare/selectionChanged lifecycle, device-only behavior (fetched via summarizing model)
- [Apple Developer Forums thread 760035](https://developer.apple.com/forums/thread/760035) — iOS 18 gestures-in-ScrollView scroll breakage; [thread 794212](https://developer.apple.com/forums/thread/794212) — iOS 26 beta chart-scroll regression
- [Swift with Majid — Mastering charts: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/) — lollipop pattern; implies framework-managed nil handling vs the manual pre-iOS-17 `onEnded { selectedIndex = nil }`

### Tertiary (LOW confidence)
- Community consensus on exact nil-on-release timing of `chartXSelection` (no single authoritative statement; design made invariant to it — Assumption A1)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every API verified against Apple docs availability metadata (this session + milestone STACK.md, same day); zero new dependencies
- Architecture: HIGH — every integration point read directly from the v1.0 source this session; the four CONTEXT-vs-code discrepancies are documented with corrections
- Pitfalls: HIGH for codebase-integration pitfalls (verified); MEDIUM for the two OS-behavioral items (nil-on-release timing, List arbitration) — both covered by locked manual checkpoints and an invariant-by-design chrome

**Research date:** 2026-06-10
**Valid until:** ~2026-07-10 for the API/codebase claims (stable); the gesture-arbitration behavior is flagged for re-verification at EVERY OS/SDK update (existing STATE.md release-checklist item)
