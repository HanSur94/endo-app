---
phase: 16-scatter-view-correlation-deep-link
plan: 02
subsystem: ui
tags: [swift, swiftui, swift-charts, scatter, pointmark, count-encoding, accessibility, ios]

# Dependency graph
requires:
  - phase: 14-explore-data-foundation
    provides: ScatterPairing.pairs -> [ScatterPoint] / SeriesNormalization.domain+readout / ExploreSeries+ScaleKind.isQuantitative — the pure both-logged pairing + fixed-domain layer, consumed UNCHANGED
  - phase: 16-scatter-view-correlation-deep-link/01
    provides: audited ExploreCopy scatter chrome (modeLanes/modeScatter, scatterAxisPlaceholder, scatterCaption, scatterSummary, scatterEmpty/Sparse) — the descriptive-only strings this view renders
provides:
  - "ScatterAxisPicker: two quantitative-only X/Y axis selectors (suppression hides bleeding), value-independent a11y ids, no persistence (SCAT-01)"
  - "ScatterPlotView: count-encoded PointMark scatter over fixed real-unit domains + neutral caption + worded a11y summary, no fitted line / regression / jitter (SCAT-01/02/03)"
  - "ExploreView Lanes|Scatter mode toggle + scatter branch (axis picker + empty/sparse/plot split), all selection ephemeral @State (SCAT-01/03)"
affects: [16-03-router-pending-scatter, 16-04-card-affordance-deep-link-e2e]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Count-encoded scatter: one PointMark per ScatterPoint cell, .symbolSize(by:count) — density via the pure layer's integer-exact count, never Double.random/jitter"
    - "Fixed real-unit chart domains via SeriesNormalization.domain(for:scaleKind) on both axes — never a data-driven min/max"
    - "Mode/axis selection as ephemeral view @State (no @AppStorage/@SceneStorage/UserDefaults) — the LOCKED Explore privacy posture extended to the scatter mode"
    - "Lanes content lifted verbatim into a @ViewBuilder property + a mode branch — the shipped path is byte-identical, the regression suite proves it"

key-files:
  created:
    - Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift
    - Sources/EndoApp/Features/Explore/ScatterPlotView.swift
  modified:
    - Sources/EndoApp/Features/Explore/ExploreView.swift

key-decisions:
  - "Axis selectors are Menus (single-select per axis) over ExploreSelection.offered(suppressed:).filter(isQuantitative) — presence series never reach an axis, bleeding hidden under suppression (defense-in-depth)"
  - "Sparse threshold = count-sum < minScatterDays (5), computed from [ScatterPoint] (a pure, testable check mirroring the Phase-15 per-lane minimum); empty = X and/or Y nil"
  - "Scatter cells recomputed from snapshots + X/Y only (a let inside scatterContent) — NO scrub coupling (the scatter has no scrub), so no per-frame recompute path is introduced"
  - "SCAT-01/02/03 left Pending per execution context — the full scatter E2E proof lands in 16-04"

requirements-completed: []  # SCAT-01/02/03 left Pending (E2E proof in 16-04) per execution context

# Metrics
duration: 7min
completed: 2026-06-16
---

# Phase 16 Plan 02: Scatter View (Mode Toggle + Axis Picker + Count-Encoded Plot) Summary

**A `Lanes | Scatter` mode toggle, a quantitative-only two-axis (X/Y) picker, and a `ScatterPlotView` rendering one count-encoded `PointMark` per both-logged `ScatterPoint` over fixed real-unit domains — neutral caption, worded a11y summary, honest empty/sparse states, zero stats/jitter, all selection ephemeral.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-06-15T23:21:33Z
- **Completed:** 2026-06-16 (this session)
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- **SCAT-01 axis picker (`ScatterAxisPicker`):** two single-select `Menu`s (X and Y) over `ExploreSelection.offered(suppressed:).filter { $0.scaleKind.isQuantitative }`. Presence/event series (food, clots, PRN, medication, bleeding-event) are NEVER offered as an axis; `bleeding.intensity` is absent under suppression (T-16-07 defense-in-depth). Same-series-on-both-axes is allowed (no special-casing). Value-independent ids `explore.scatter.axisX`/`axisY` + per-option `<stem>.<series.id>`, ≥44pt touch floor, zero persistence.
- **SCAT-01/02/03 plot (`ScatterPlotView`):** a square 320pt `Chart` with one `PointMark` per `ScatterPoint`, `.symbolSize(by: .value("Days", count))` so identical day-pairs stack by COUNT (the pure layer already aggregated them — never jitter). Both axes use FIXED domains via `SeriesNormalization.domain(for:)` (never data-driven). Single accent hue; count is size + worded readout, never hue. A neutral caption (`scatterCaption(days:)`), a worded primary-a11y summary (`scatterSummary(x:y:days:)`, id `explore.scatter.summary`), and per-mark `accessibilityLabel`/`Value` (real-unit readouts via `SeriesNormalization.readout` + "N days"). NO `LineMark`, NO regression, NO r-value/coefficient, NO `Double.random`/jitter. Reduce Motion respected verbatim from the `InsightTrendChart` recipe.
- **SCAT-01/03 wiring (`ExploreView`):** a segmented `Picker` (`explore.mode`, default `.lanes`) pinned above the picker block, with `square.stack.3d.up`/`chart.dots.scatter` symbols and `ExploreCopy.modeLanes`/`modeScatter` labels. The lanes block is lifted verbatim into a `lanesContent` property; a new `scatterContent` renders the axis picker then the empty (`explore.scatter.empty`, X/Y nil) / sparse (`explore.scatter.sparse`, count-sum < 5) / `ScatterPlotView` split. `ExploreMode` enum + `mode`/`scatterX`/`scatterY` are ephemeral `@State` (T-16-06 — no persistence wrapper).
- **Proven:** clean Swift 6 build after `xcodegen generate` (three app-target files); all 8 `ExploreUITests` (the lanes regression) green; every honesty + privacy grep gate clean.

## Task Commits

1. **Task 1: ScatterAxisPicker (quantitative-only X/Y selectors, SCAT-01)** — `b4bb4a3` (feat)
2. **Task 2: ScatterPlotView (count-encoded PointMark scatter + caption + a11y summary, SCAT-01/02/03)** — `bc2bf11` (feat)
3. **Task 3: ExploreView mode toggle + scatter branch (ephemeral state, SCAT-01/03)** — `6eb669c` (feat)

_All three tasks are `tdd="true"`; their `<verify>` blocks are build + the honesty/privacy grep gates + the lanes UI regression (the gate IS the verification for these SwiftUI Views — no separate unit-test method, mirroring 16-01 Task 1). The full scatter behavioral E2E lands in 16-04 per the execution context._

## Files Created/Modified
- `Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift` — two quantitative-only X/Y `Menu` selectors, suppression-filtered, value-independent a11y ids, ≥44pt, no persistence.
- `Sources/EndoApp/Features/Explore/ScatterPlotView.swift` — count-encoded `PointMark` scatter over fixed real-unit domains, neutral caption + worded a11y summary + per-mark readouts, no stats/jitter token, Reduce Motion respected.
- `Sources/EndoApp/Features/Explore/ExploreView.swift` — added `ExploreMode` enum, ephemeral `mode`/`scatterX`/`scatterY` `@State`, the `static let minScatterDays = 5`, the segmented mode `Picker`, and the `lanesContent` (verbatim) / `scatterContent` (axis picker + empty/sparse/plot split) branch.

## Decisions Made
- **Quantitative-only axes, suppression carried through:** options = `ExploreSelection.offered(suppressed:).filter(isQuantitative)` — the single source of truth, so a presence factor can never be a scatter axis and a suppressed user is never offered bleeding (defense-in-depth alongside the Phase-14 accessor gate, T-16-07).
- **Count-sum sparse check (threshold 5):** the honest sparse split keys off `points.reduce(0){ $0 + $1.count } < minScatterDays`, a pure testable check from the `[ScatterPoint]` cloud (UI-SPEC §D), mirroring the Phase-15 per-lane minimum. Empty = X and/or Y not yet chosen.
- **No scrub coupling in scatter mode:** the both-logged cells are recomputed inside `scatterContent` from `snapshots` + the chosen X/Y only — the scatter has no scrub, so no per-frame recompute path is introduced; the lanes `recompute()`/`onAppear` seam is untouched.
- **Lanes path lifted verbatim:** the shipped lanes block moved into a `@ViewBuilder lanesContent` with no logic change — the `ExploreUITests` regression staying green proves the lanes behavior is byte-identical.
- **SCAT-01/02/03 left Pending:** per the execution context, the requirement checkboxes flip only when the full scatter E2E ships in 16-04; this plan delivers the UI and its honesty/privacy gates.

## Deviations from Plan

None - plan executed exactly as written. (No bugs, missing functionality, or blocking issues surfaced; the host simulator did not flake — the lanes regression ran clean on the first attempt.)

## Known Stubs

None. The empty-list literals in `scatterContent` (`return []` when an axis is nil; the `?? 0...10` domain fallback) are honest degradation paths, NOT stubs: the `[]` drives the genuine empty state (no axis chosen → "pick two factors"), and the `?? 0...10` is an inert fallback that never fires for a quantitative series (which always returns a non-nil domain). Both are intentional must-not-crash defaults, not unwired data.

## Issues Encountered
- None. Clean build on each task; the `ExploreUITests` suite passed 8/8 on the first run (no per-method flake fallback needed).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **Ready for 16-03 (router + pending scatter):** `ExploreView` now owns the ephemeral `mode`/`scatterX`/`scatterY` state the deep-link router will set; the scatter branch renders whatever X/Y the router prefills. The router read is a later edit to the same file (Wave 3).
- **Ready for 16-04 (card affordance + E2E):** the `explore.mode`/`explore.mode.scatter`/`explore.scatter.axisX|Y`/`explore.scatter.plot`/`explore.scatter.empty`/`explore.scatter.sparse`/`explore.scatter.summary` a11y ids are in place for the end-to-end deep-link proof.
- **PDF / Insights / lanes paths untouched;** mode + X/Y stay ephemeral (LOCKED privacy invariant).
- **Requirements:** SCAT-01/02/03 left Pending (E2E proof in 16-04) per the execution context.

## Self-Check: PASSED

- FOUND: `Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift`
- FOUND: `Sources/EndoApp/Features/Explore/ScatterPlotView.swift`
- FOUND: `.planning/phases/16-scatter-view-correlation-deep-link/16-02-SUMMARY.md`
- FOUND commit: `b4bb4a3` (Task 1)
- FOUND commit: `bc2bf11` (Task 2)
- FOUND commit: `6eb669c` (Task 3)

---
*Phase: 16-scatter-view-correlation-deep-link*
*Completed: 2026-06-16*
