---
phase: 13-trend-chart-scrubbing
plan: 02
subsystem: insights-charts
tags: [swiftui, swift-charts, chartXSelection, lollipop-tooltip, haptics, ax-chart-descriptor, accessibility, pdf-protection]

# Dependency graph
requires:
  - phase: 13-trend-chart-scrubbing
    plan: 01
    provides: ChartScrubSelection.resolve (the only raw-date → value path) + audited InsightsCopy.scrubNotLogged/scrubSeverity
  - phase: 08-insights-integration-coordinator-cached-insights-dashboard
    provides: InsightTrendChart (the repo's only Chart), InsightCardView .trend branch, per-mark a11y labels
provides:
  - InsightTrendChart additive selection layer — allowsSelection/selectionA11yIDRoot defaulted params, conditional chartXSelection, Health-pattern lollipop chrome (RuleMark + pinned-top tooltip + logged-only highlight point), day-change-only haptic, AXChartDescriptor Audio Graph
  - InsightCardView .trend branch switch-on (allowsSelection: true + selectionA11yIDRoot) — CHART-01..04 live on the dashboard
  - Tooltip a11y ids ("<root>.tooltip", "<root>.tooltip.notLogged") Plan 03's seam-driven E2E asserts against
affects: [13-03, 15-explore, 16-scatter]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Conditional modifier attachment for path protection: Group { if allowsSelection { chart.chartXSelection(...) } else { chart } } — the modifier is ABSENT (not inert) when false, so the PDF path's modifier set is identical to v1.0"
    - "Day-change-only haptic via .onChange(of: resolvedSelection?.day): old != nil && new != nil && old != new — naturally excludes touch/release, naturally includes logged↔gap"
    - "@preconcurrency AXChartDescriptorRepresentable conformance — Swift 6 main-actor View vs nonisolated protocol requirement (compiler-suggested resolution)"
    - "Skip-entirely conditional a11y ids via a private @ViewBuilder View extension (no empty-string identifiers)"

key-files:
  created: []
  modified:
    - Sources/EndoApp/Features/Insights/InsightTrendChart.swift
    - Sources/EndoApp/Features/Insights/InsightCardView.swift

key-decisions:
  - "Haptic .onChange attached INSIDE the allowsSelection branch (not on the outer body) — strictly stronger PDF protection than RESEARCH Pattern 2's sketch: the false branch carries zero new modifiers"
  - "AXChartDescriptorRepresentable conformance via @preconcurrency (Swift 6: the View is implicitly MainActor, the protocol requirement is nonisolated; SwiftUI invokes makeChartDescriptor on the main actor)"
  - ".accessibilityChartDescriptor(self) attached in BOTH modes per UI-SPEC (render-inert; ImageRenderer rasterizes visuals only) — escape hatch documented in-source for Plan 03's PDF re-verification"

patterns-established:
  - "Selection chrome renders iff resolvedSelection != nil — correct under ANY chartXSelection nil-timing semantics (RESEARCH A1 invariance)"

requirements-completed: [CHART-01, CHART-02, CHART-03, CHART-04]

# Metrics
duration: 9min
completed: 2026-06-10
---

# Phase 13 Plan 02: Trend-Chart Selection Layer Summary

**Additive `allowsSelection` layer on `InsightTrendChart` — framework-standard `chartXSelection` (conditionally absent on the PDF path), Health-pattern lollipop with the resolver-honest gap/severity-0 tooltip, day-change-only `UISelectionFeedbackGenerator`, discrete logged-only `AXChartDescriptor` Audio Graph — switched on by one `InsightCardView` call site**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-06-10T17:58:04Z
- **Completed:** 2026-06-10T18:06:57Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- **CHART-01 chrome live:** touching a dashboard trend chart shows a secondary-gray `RuleMark` (zIndex -1, behind the data line) carrying a pinned-top `.annotation(position: .top, overflowResolution: .init(x: .fit(to: .chart), y: .disabled))` tooltip — date line (`.caption2` secondary, the axis's own date format) + "Severity n of 10" (`.caption` semibold primary) — plus an `inkColor` `.symbolSize(100)` highlight point.
- **CHART-02 honest in pixels AND text:** every value lookup routes through `ChartScrubSelection.resolve` (the Plan-01 choke-point; zero `proxy.value(atX:)`/nearest-point math, grep-gated). A gap day renders the rule mark + de-emphasized "Not logged" (`.caption` regular secondary — information, not an error) and NO highlight point; a logged severity-0 day renders the point AT the zero line + "Severity 0 of 10". No string literals in the view — `InsightsCopy.scrubSeverity`/`scrubNotLogged` only.
- **CHART-03 haptic semantics exact:** ONE `UISelectionFeedbackGenerator` per view identity; `.onChange(of: resolvedSelection?.day)` fires `selectionChanged()` only on `old != nil && new != nil && old != new` — initial touch (nil→day) and release (day→nil) fire nothing; logged↔gap transitions tick (the day changes even when the point is nil). `prepare()` keeps the Taptic Engine warm mid-scrub. Simulator no-ops expected; device verification is the locked phase-gate manual checkpoint.
- **CHART-04 dual path:** the existing per-mark `accessibilityLabel`/`accessibilityValue` are byte-untouched, AND `InsightTrendChart` now conforms to `AXChartDescriptorRepresentable` — categorical day axis (same "Jun 5" vocabulary), 0…10 "Severity" numeric axis reading "{n} of 10", ONE `isContinuous: false` series of LOGGED points only sorted by date (the Audio Graph plays discrete tones and never sonically interpolates a gap). The tooltip is ONE quiet a11y element (`children: .ignore`) whose `accessibilityValue` composes "{date} — Severity {n} of 10" / "{date} — Not logged"; zero `UIAccessibility.post`.
- **PDF protected by construction:** `allowsSelection: Bool = false` default; when false the `chartXSelection` modifier (and the haptic `.onChange`) are NOT attached at all — the branch carries the v1.0 chart verbatim. `ReportPDFRenderer.swift` has zero diff (verified via `git status --porcelain`). Selection state is per-card view-local `@State`, ephemeral, never persisted.
- **Dashboard switch-on is one call site:** `InsightCardView`'s `.trend` branch passes `allowsSelection: true, selectionA11yIDRoot: "\(idRoot).chart"`; the outer chart id, confidence pill, dismiss affordance, and sparse state are untouched — proven by the full `InsightsDashboardUITests` suite staying green with selection live.

## Task Commits

Each task was committed atomically:

1. **Task 1: Additive allowsSelection layer + lollipop selection chrome** - `0314aab` (feat)
2. **Task 2: Day-change haptic + AXChartDescriptor Audio Graph + dashboard switch-on** - `74f654f` (feat)

## Files Created/Modified

- `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` - Additive selection layer: params, `@State rawSelectedDate`/`haptics`, `resolvedSelection` via the Plan-01 resolver, conditional `chartXSelection` + haptic `.onChange`, `selectionMarks`/`tooltip`/`selectionReadout`, `@preconcurrency AXChartDescriptorRepresentable` extension, skip-entirely a11y-id helper
- `Sources/EndoApp/Features/Insights/InsightCardView.swift` - `.trend` branch passes `allowsSelection: true` + `selectionA11yIDRoot` (one call site)

## Decisions Made

- **Haptic `.onChange` lives inside the `allowsSelection` branch**, not on the outer body as RESEARCH Pattern 2 sketched — strictly stronger PDF protection (the false branch gains zero modifiers) with identical behavior (the binding can never become non-nil without the gesture).
- **`@preconcurrency` conformance for `AXChartDescriptorRepresentable`** — under Swift 6 the View struct is implicitly `@MainActor` while the protocol's requirements are nonisolated; the compiler-suggested `@preconcurrency` conformance is correct because SwiftUI invokes `makeChartDescriptor()` on the main actor (Assumption A2's "compiler-resolved" allowance).
- **`.accessibilityChartDescriptor(self)` attached in both modes** per the UI-SPEC posture (render-inert; `ImageRenderer` rasterizes visuals only), with the escape hatch (gate behind `allowsSelection` if Plan 03's PDF re-verification shows any diff) documented in-source.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Swift 6 actor-isolation error on the AXChartDescriptorRepresentable conformance**
- **Found during:** Task 2 (first build after adding the extension)
- **Issue:** `conformance of 'InsightTrendChart' to protocol 'AXChartDescriptorRepresentable' crosses into main actor-isolated code` — the iOS 18 SDK's `View` protocol is `@MainActor`, so the struct's members are main-actor-isolated while the protocol requirement is nonisolated
- **Fix:** `extension InsightTrendChart: @preconcurrency AXChartDescriptorRepresentable` (the compiler-suggested resolution; main-actor-safe at runtime since SwiftUI calls the requirement on the main actor) — within the plan's Assumption A2 "adjust to the SDK, do not fight the sketch" allowance
- **Files modified:** Sources/EndoApp/Features/Insights/InsightTrendChart.swift
- **Commit:** `74f654f`

No other deviations — plan executed as written.

## Issues Encountered

None. (`gsd-tools` CLI unavailable in this environment; STATE.md/ROADMAP.md/REQUIREMENTS.md updates applied by direct edit, matching prior-phase practice. The two build warnings — `HKCategoryValueMenstrualFlow` deprecation, unused `try?` in SettingsView — are pre-existing in unrelated files, out of scope.)

## Verification Results

- `xcodebuild build` (iPhone 16 simulator) — **BUILD SUCCEEDED** after each task
- `xcodebuild test … -only-testing:EndoAppUITests/InsightsDashboardUITests` — **6/6 green, TEST SUCCEEDED** with `allowsSelection: true` live on the dashboard (dismiss, flare-first ordering, empty state, recommendation ordering, seeded render, suppressed-section absence all unaffected; the plan anticipated 4 tests — the suite has grown to 6, all pass)
- Grep gates (non-comment lines of InsightTrendChart.swift): `DragGesture|highPriorityGesture|simultaneousGesture|chartGesture|value(atX` → **0**; `"Not logged"` literal → **0**; `UIAccessibility.post` → **0**
- `chartXSelection` appears in code exactly once — inside the `allowsSelection` branch
- `git status --porcelain -- Sources/EndoApp/Features/Report/ReportPDFRenderer.swift` — **empty (zero-diff held)**
- Full `-only-testing:EndoAppTests` deliberately NOT run (known >15-min stall in this environment; targeted suites are the established pattern)

## Next Phase Readiness

- Plan 13-03 can build the `-uitest-select-day` seam + `-uitest-seed-scrub` fixture + `ChartScrubUITests` against the now-live tooltip ids (`insights.card.<signalKey>.chart.tooltip` / `.tooltip.notLogged`) and re-verify the PDF path (ReportFlowUITests + visual check vs the v1.0 tag).
- Device-gated manual checkpoints remain for the phase gate: haptic feel (Simulator no-op), scroll-vs-scrub arbitration in the List, VoiceOver Audio Graph playback.

## Self-Check: PASSED

- Both modified files exist on disk (incl. this SUMMARY)
- Both task commits (`0314aab`, `74f654f`) present in git log
- `allowsSelection` present in InsightTrendChart.swift; `allowsSelection: true` present in InsightCardView.swift
- Key links verified: `ChartScrubSelection.resolve` (1 code occurrence), `InsightsCopy.scrub*` (4 occurrences), `ReportPDFRenderer.swift` zero-diff

---
*Phase: 13-trend-chart-scrubbing*
*Completed: 2026-06-10*
