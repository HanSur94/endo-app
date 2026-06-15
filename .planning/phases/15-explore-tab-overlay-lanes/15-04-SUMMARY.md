---
phase: 15-explore-tab-overlay-lanes
plan: 04
subsystem: ui
tags: [swift-charts, swiftui, explore-lanes, shared-scrub, multi-series-tooltip, honest-gaps, a11y-proxy]

# Dependency graph
requires:
  - phase: 15-explore-tab-overlay-lanes
    plan: 01
    provides: "RESOLVED A2 inset mechanism — .chartYAxis(.hidden) + unit-in-lane-title makes sibling Charts on different Y scales share identical plot leading insets"
  - phase: 15-explore-tab-overlay-lanes
    plan: 03
    provides: "ExploreCopy audited chrome copy (notLogged, eventTagged)"
  - phase: 14-explore-pure-layer
    provides: "ExploreSeries/ScaleKind, ExploreSeriesExtraction.{quantitativePoints,occurrenceDays}, SeriesNormalization.{domain,readout}, ChartScrubSelection.resolve, InsightPoint, DaySnapshot"
provides:
  - "ExploreLaneChart: ONE quantitative lane (gap-run LineMarks per series×run, per-day PointMarks, fixed SeriesNormalization.domain Y, shared injected xDomain, shared chartXSelection, ChartScrubSelection.resolve only, per-mark a11y + AXChartDescriptor + haptic, .chartYAxis(.hidden) inset)"
  - "EventLaneChart: ONE event-marker lane (RectangleMark occurrence ticks at constant Y, no connected line / no 0/1 mapping, height 44, shared xDomain + selection, ExploreCopy.eventTagged a11y)"
  - "ExploreLanesView: the VStack lane stack + ONE shared scrub + the single multi-series tooltip + the transparent a11y proxy shielded by .accessibilityElement(children: .contain); reads \\.uiTestScrubDay"
affects: [15-05, 15-06, ExploreView, ExploreUITests]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sibling-lane assembly: N separate Charts, IDENTICAL chartXScale(domain:) + one @Binding Date? bound to every chartXSelection, per-lane chartYScale from SeriesNormalization.domain(for:), .chartYAxis(.hidden) on EVERY lane"
    - "Single multi-series tooltip over the whole stack (not per-lane): quantitative lines via ChartScrubSelection.resolve, event lines via series.isPresent(in: snapshotForDay) keyed by startOfDay (Pitfall 2)"
    - "Transparent allowsHitTesting(false) a11y proxy carrying per-series readout + ids, shielded by .accessibilityElement(children: .contain) so the parent tooltip id can't mask per-series ids"

key-files:
  created:
    - Sources/EndoApp/Features/Explore/ExploreLaneChart.swift
    - Sources/EndoApp/Features/Explore/EventLaneChart.swift
    - Sources/EndoApp/Features/Explore/ExploreLanesView.swift
  modified:
    - Sources/EndoApp/App/EndoAppApp.swift
  deleted:
    - Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift

decisions:
  - "Reworded 'value(atX:)' and 'LineMark' literal substrings out of comments so the honesty grep gates (! grep -q) pass against documentation mentions, not just code"
  - "Color carried per series via .foregroundStyle(by: .value(\"Series\", displayName)) → legend + the colorblind-safe palette, paired with the lane title + per-lane real-unit signal (never hue alone)"
  - "Event-lane gray scrub RuleMark derived directly from startOfDay(selectedDate), NOT via ChartScrubSelection (which is the quantitative [InsightPoint] path; events have no resolver path — Pitfall 2)"
  - "Probe (_LaneAlignmentProbe.swift) + DEBUG -probe-lane-alignment host retired in this plan, superseded by the real ExploreLaneChart/ExploreLanesView stack (per the 15-01 NEXT-PHASE handoff)"

# Metrics
duration: 5min
completed: 2026-06-15
---

# Phase 15 Plan 04: Explore Lane Stack Summary

**The phase's central new assembly — a vertical stack of sibling Swift Charts lanes that share ONE absolute-date X domain and ONE `chartXSelection` scrub binding, with a single multi-series tooltip reading every visible series at once: quantitative series get fixed real-unit per-scale lanes (EXPL-03), presence series get occurrence-tick lanes that never draw a 0/1 line (EXPL-04), one shared scrub reads each lane's exact-day value or a per-series "Not logged" (EXPL-05), and unlogged days stay gaps in every lane (EXPL-07). The 15-01 probe is retired.**

## What Was Built

- **`ExploreLaneChart.swift`** — ONE quantitative lane, mirroring `InsightTrendChart`'s chart body verbatim. One `LineMark` series per `(series × ChartSeriesGrouping.runs)` so the line BREAKS at every gap; a `PointMark` per logged day so a single day shows and a literal value-0 day draws a point at the zero line (distinct from a gap, which draws nothing). `.chartYScale(domain: SeriesNormalization.domain(for: series.scaleKind))` (fixed real units, no dual-Y, no normalization), `.chartXScale(domain: xDomain)` (the SHARED injected domain), `.chartYAxis(.hidden)` (the 15-01 inset mechanism), `.chartXSelection(value: $selectedDate)` (the SHARED scrub). Scrub resolved via `ChartScrubSelection.resolve` ONLY — no position-based lookup. Per-mark `.accessibilityLabel`(date)/`.accessibilityValue`(`SeriesNormalization.readout`), ONE `@preconcurrency AXChartDescriptorRepresentable` per lane (logged points only, gaps omitted), and the `UISelectionFeedbackGenerator` haptic guard (fires on resolved-DAY change only). `.frame(height: 120)`; a11y id `explore.lane.<series.id>`.

- **`EventLaneChart.swift`** — ONE presence/event lane (RESEARCH Pattern 2). A `RectangleMark` occurrence tick per `ExploreSeriesExtraction.occurrenceDays` date at a constant mid-lane Y on a hidden `0...1` scale — NO connected line, NO 0/1 value mapping (the extraction path returns `[Date]`, so a 0/1 line is structurally unrepresentable). Shares the IDENTICAL `xDomain` + the SAME `$selectedDate`; the gray scrub `RuleMark` is derived from `startOfDay(selectedDate)` directly (events have no `ChartScrubSelection` path — Pitfall 2). Per-tick `.accessibilityValue(ExploreCopy.eventTagged)` ("Logged", never "1"). `.frame(height: 44)` (the ≥44pt touch floor); a11y id `explore.lane.<series.id>.event`.

- **`ExploreLanesView.swift`** — the stack: a `VStack` rendering all quantitative `ExploreLaneChart`s (each preceded by its `.subheadline` semibold lane TITLE carrying name + unit) then all `EventLaneChart`s, every lane passed the IDENTICAL `xDomain` + the SAME `$selectedDate`. ONE multi-series tooltip over the whole stack (not per-lane): the scrubbed date once, then one `.caption` line per visible series — quantitative via `ChartScrubSelection.resolve` (`SeriesNormalization.readout` for a logged day / `ExploreCopy.notLogged` for a gap), events via `series.isPresent(in: snapshotsByDay[startOfDay(day)])` (`ExploreCopy.eventTagged` / `ExploreCopy.notLogged`). The visual card is `.accessibilityHidden(true)`; the per-series readout + ids ride a transparent `.allowsHitTesting(false)` overlay proxy carrying `explore.scrub.tooltip.<series.id>` (`.notLogged` suffix on a gap/absent day), shielded by `.accessibilityElement(children: .contain)`. Reads `\.uiTestScrubDay` via the keyed `.task(id:)` guard (the `xDomain.contains` + `selectedDate == nil` guards are load-bearing). All copy via `ExploreCopy`; a11y id `explore.lanes`.

- **Probe retired** — deleted the throwaway `_LaneAlignmentProbe.swift` and removed the DEBUG `-probe-lane-alignment` host from `EndoAppApp.swift` (superseded by the real lane stack, per the 15-01 handoff).

## How It Works

The honesty contracts are enforced by composition of shipped Phase-13/14 primitives reused UNCHANGED:
- **Honest gaps (EXPL-07):** `ChartSeriesGrouping.runs` keys one `LineMark` series per contiguous run, so the line breaks at gaps; an unlogged day is never `value: 0`.
- **Exact-day scrub (EXPL-05):** the ONLY raw-date→value path is `ChartScrubSelection.resolve` (no interpolation, no nearest-point) — a gap resolves to `point == nil` → "Not logged", a logged 0 to a real point. Event lines bypass the resolver and use `isPresent(in:)` against the day-keyed snapshot map.
- **Aligned per-scale lanes (EXPL-03):** every lane hides its numeric Y axis (the 15-01 mechanism) and carries the unit in its title, so all plots share the full width and the one shared `RuleMark` crosses every lane at the same date.
- **Event ticks not 0/1 lines (EXPL-04):** `RectangleMark` ticks on `[Date]` occurrence days; no connected line is even representable on the presence path.

## Verification

- `xcodegen generate` — succeeded (all three files picked up by the `Sources/EndoApp` glob; no `project.yml` edit needed).
- `xcodebuild build -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` — **BUILD SUCCEEDED**, Swift 6 strict concurrency clean, with the probe deleted and the host removed.
- Plan grep gates (all PASS): `ChartSeriesGrouping.runs` + `ChartScrubSelection.resolve` + `SeriesNormalization.domain` present in `ExploreLaneChart`; NO `value(atX:)` in any lane file; NO `LineMark` in `EventLaneChart`; `ExploreLanesView` uses `isPresent(in:)` + `ExploreCopy.notLogged`/`eventTagged` + `.accessibilityElement(children: .contain)` + `uiTestScrubDay`.
- Probe deletion confirmed: `_LaneAlignmentProbe.swift` removed; no `-probe-lane-alignment`/`_LaneAlignmentProbe` reference in `EndoAppApp.swift`.
- Task 1 gate `LANES_OK` and Task 2 gate `LANESVIEW_OK` both emitted.
- Full E2E proof (lanes/tooltip/gaps/event ticks via a11y ids in `ExploreUITests`) is DEFERRED to 15-06 per plan scope — this plan's gate is build + grep + the probe retirement.

## TDD Gate Compliance

Both tasks are `tdd="true"`, but the plan supplies NO XCTest/Swift-Testing target for these SwiftUI `Chart` views — its `<verify>` block is the structural grep + build gate, and the executable E2E proof is explicitly scoped to 15-06 (`ExploreUITests`, via the deterministic `-uitest-select-day` / `\.uiTestScrubDay` seam wired here). The behavioral contracts (honest gaps, exact-day scrub, no 0/1 event line, per-series Not-logged) are enforced at this stage by the honesty grep gates over the source, which the build was driven against. No `test(...)`-prefixed RED commit applies; the GREEN `feat(...)` commits carry the verified implementation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reworded `value(atX:)` / `LineMark` literal substrings out of comments**
- **Found during:** Task 1
- **Issue:** The plan's honesty grep gates are `! grep -q "value(atX:"` (lane files) and a `LineMark` absence check (`EventLaneChart`). My first-draft documentation comments mentioned `proxy.value(atX:)` and `NEVER a 0/1 \`LineMark\`` to explain WHY those are forbidden — but a literal substring in a comment trips the gate exactly as code would, failing the verification.
- **Fix:** Reworded the comments to "position-based chart lookup" and "connected 0/1 line" / "connected line" — preserving the explanatory intent without the forbidden substrings. The code never used either API.
- **Files modified:** `ExploreLaneChart.swift`, `EventLaneChart.swift`
- **Verification:** `LANES_OK` (Task 1 gate) + the full grep suite (all PASS).
- **Committed in:** `94efef2`

**2. [Rule 3 - Blocking, pre-authorized] Retired the 15-01 probe + DEBUG host**
- **Found during:** Task 2
- **Issue:** The 15-01 `_LaneAlignmentProbe.swift` + the `-probe-lane-alignment` host in `EndoAppApp.swift` are throwaway artifacts the 15-01 summary explicitly handed off for deletion "when 15-04 builds the real `ExploreLaneChart`/`ExploreLanesView`". Leaving them would ship dead DEBUG code + an unused launch-arg branch.
- **Fix:** `git rm`'d the probe file and removed the `#if DEBUG` launch-arg branch from `EndoAppApp.body`, collapsing it back to the plain `WindowGroup { AppGate()… }`. Build clean after deletion.
- **Files modified:** `EndoAppApp.swift` (host removed); `_LaneAlignmentProbe.swift` (deleted)
- **Verification:** `BUILD SUCCEEDED` post-deletion; no probe reference remains.
- **Committed in:** `217b684`

**Total deviations:** 2 (both Rule 3 — blocking; deviation 2 was pre-authorized by the 15-01 handoff and the execution context). No scope creep, no new dependencies, no new health-data surface.

## Known Stubs

None. The three lane files are pure presentation over caller-injected, already-consented data — no hardcoded empty values flow to UI as stand-ins. The lanes/tooltip render whatever the caller passes; the screen that wires the data source (`ExploreView`) and the picker/range land in 15-05, and the E2E proof in 15-06. This is documented plan sequencing, not a stub: `ExploreLanesView`'s inputs are typed parameters, not defaulted empties.

## Requirements

EXPL-03/04/05/07 are referenced by this plan but left **Pending** — the lane primitives + stack are built, but the requirements are not deliverable until the Explore screen wires the data source (15-05) and the E2E proof passes (15-06), per the execution context ("Leave EXPL checkboxes Pending").

## Next Phase Readiness

- **15-05** can build `ExploreView` (the screen) + `SeriesPickerView` + range control against a ready `ExploreLanesView`: prepare `(series, points)` / `(series, days)` / `snapshotsByDay` / `xDomain` once per selection-window change and pass the shared `$selectedDate` binding in.
- **15-06** can write `ExploreUITests` against the a11y ids shipped here: `explore.lanes`, `explore.lane.<id>`, `explore.lane.<id>.event`, `explore.scrub.tooltip`, `explore.scrub.tooltip.<id>`(`.notLogged`), driven by the `-uitest-select-day` / `\.uiTestScrubDay` seam already read.

---
*Phase: 15-explore-tab-overlay-lanes*
*Completed: 2026-06-15*

## Self-Check: PASSED

- FOUND: Sources/EndoApp/Features/Explore/ExploreLaneChart.swift
- FOUND: Sources/EndoApp/Features/Explore/EventLaneChart.swift
- FOUND: Sources/EndoApp/Features/Explore/ExploreLanesView.swift
- FOUND: .planning/phases/15-explore-tab-overlay-lanes/15-04-SUMMARY.md
- CONFIRMED DELETED: Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift
- FOUND commit: 94efef2 (Task 1 — lane primitives)
- FOUND commit: 217b684 (Task 2 — lane stack + probe retirement)
