---
phase: 15-explore-tab-overlay-lanes
plan: 05
subsystem: ui
tags: [swiftui, swift-charts, explore, picker, chips, range-presets, empty-sparse-states, tab, consent-gated, ios18]

# Dependency graph
requires:
  - phase: 15-explore-tab-overlay-lanes
    plan: 02
    provides: "ExploreSelection (series Set + window, quantitativeCap=4, quantitativeCount, canAddQuantitative, toggle, offered(suppressed:))"
  - phase: 15-explore-tab-overlay-lanes
    plan: 03
    provides: "ExploreCopy (emptyHeading/Body, sparseHeading/Body, capReached, removeSeries, addToTimeline, windowLabel)"
  - phase: 15-explore-tab-overlay-lanes
    plan: 04
    provides: "ExploreLanesView(quantitativeLanes:eventLanes:snapshotsByDay:xDomain:selectedDate:) — the lane stack + shared scrub + multi-series tooltip"
  - phase: 14-explore-series-catalog
    provides: "ExploreSeriesCatalog.all, ExploreSeries/ScaleKind, ExploreSeriesExtraction.{quantitativePoints,occurrenceDays}"
  - phase: 13-chart-scrub
    provides: "TrendWindow (the range-preset family, EXPL-06)"
  - app-target
    provides: "SwiftDataRepository(context:).daySnapshots(), DisclaimerBanner, ConsentedShell (the tab seam)"
provides:
  - "SeriesPickerView — suppression-filtered toggle-chip picker (capped, color-independent) + TrendWindow segmented range control"
  - "ExploreView — the ScrollView screen owning the ephemeral selection + shared scrub, reading daySnapshots() once, rendering empty/sparse/lanes"
  - "The third Explore tab in ConsentedShell (consent-by-construction), tab.explore on the Label, symbol square.stack.3d.up"
affects: [15-06 ExploreUITests]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ephemeral @State selection (no persistence wrapper) — health-interest metadata never leaves the consent-gated store (T-15-12)"
    - "Prepare-once cache: per-series points/days + day-keyed snapshot map + shared xDomain recomputed only on selection/window change, never per scrub frame (Pitfall 4)"
    - "iOS 16+ Layout-protocol FlowLayout for the wrapping chip cluster (system, zero dependency)"
    - "Color-independent toggle state: fill + symbol (checkmark/plus) + font weight, never hue alone"
    - "Consent-by-construction: the Explore tab lives only inside the .consented ConsentedShell branch (unreachable before opt-in, T-15-10)"

key-files:
  created:
    - Sources/EndoApp/Features/Explore/SeriesPickerView.swift
    - Sources/EndoApp/Features/Explore/ExploreView.swift
  modified:
    - Sources/EndoApp/App/AppGate.swift

key-decisions:
  - "Reworded the privacy comments in ExploreView to avoid the literal @AppStorage/@SceneStorage tokens so the plan's token-based ! grep gate passes (same gate-compliance edit 15-02/15-04 made)"
  - "'Too sparse' defined honestly as fewer than 2 logged points across all selected series within the window — a single isolated point can't read as a lane; gaps stay gaps, never a fabricated value"
  - "xDomain math lifted from InsightTrendChart verbatim (window range even when sparse); .allTime spans the earliest logged point across BOTH quantitative + event selected series to today"
  - "Selected series split + ordered by the catalog's stable display order (ExploreSeriesCatalog.all.filter) so lane order is deterministic, never Set iteration order"
  - "EXPL-01/02/06/07 left Pending — UI shipped + build/grep-proven, but flipped to Complete only when 15-06's E2E passes (per execution-context convention)"

# Metrics
duration: 9min
completed: 2026-06-15
---

# Phase 15 Plan 05: Explore Screen, Series Picker & Third Tab Summary

**The user-facing Explore surface: a suppression-filtered, color-independent toggle-chip picker (4-quantitative cap with the remove-to-add helper) + the dashboard `TrendWindow` range presets, and the `ScrollView`-based `ExploreView` screen that owns the ephemeral selection + the one shared scrub state, reads `daySnapshots()` once, and renders the honest "pick a series" empty / "not enough data" sparse states or the 15-04 lane stack — wired into `ConsentedShell` as the consent-gated third `Explore` tab with `tab.explore` on the Label.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-06-15T21:09:11Z
- **Tasks:** 2 (both `tdd="true"` — see TDD Gate Compliance below)
- **Files:** 2 created, 1 modified

## Accomplishments

### Task 1 — `SeriesPickerView.swift` (EXPL-02 / EXPL-06)
- Toggle chips built from `ExploreSelection.offered(suppressed:)` — a suppressed user is offered **no** `bleeding.intensity`/`bleeding.event` chip (T-15-11, the UI half of the Phase-14 accessor gate).
- Tap toggles via `ExploreSelection.toggle` (add-or-remove); selected vs unselected differ by **fill + symbol (`checkmark.circle.fill` / `plus.circle`) + font weight (semibold/regular)** — never hue alone. The accent tint is a redundant cue.
- At the cap (`quantitativeCount >= quantitativeCap`), each **unselected quantitative** chip is `.disabled` (reusing `selection.canAddQuantitative`) and the `ExploreCopy.capReached` helper shows with id `explore.picker.capHint`; **event chips stay enabled** (uncapped).
- The selected chip carries a low-stakes `xmark` remove glyph (a11y label `ExploreCopy.removeSeries`) — never destructive red.
- The range control is a segmented `Picker` over `TrendWindow.allCases` labelled via `ExploreCopy.windowLabel`, bound to `selection.window` (EXPL-06).
- Every chip hit area `minHeight: 44` (HIG). a11y ids: `explore.picker`, `explore.chip.<id>` (value-independent), `explore.chip.<id>.remove`, `explore.range`, `explore.range.<window.rawValue>`.
- A private `FlowLayout` (iOS 16+ `Layout`) wraps the chip cluster so chips reflow at large Dynamic Type without clipping (no third-party dependency).

### Task 2 — `ExploreView.swift` + `AppGate.swift` (EXPL-01 / EXPL-07)
- `NavigationStack { ScrollView { … } }` (NOT a `List` — the lanes are a fixed free-scrub surface, Pitfall 7), title "Explore", a11y id `explore.screen`. `DisclaimerBanner()` pinned (`explore.disclaimer`, reused verbatim).
- Owns the ephemeral `@State ExploreSelection` (no persistence wrapper — T-15-12) + the shared `@State selectedDate: Date?` passed by `$binding` into the lane stack.
- Reads `SwiftDataRepository(context: modelContext).daySnapshots()` **once on appear** (`(try? …) ?? []`); the suppressed flag = `snapshots.last?.suppressed` drives the picker filter.
- Prepares the per-series points (`quantitativePoints`) / days (`occurrenceDays`) + the `startOfDay`-keyed snapshot map + the shared `xDomain` **once per selection/window change** (`onAppear` + `onChange(of: selection)`), cached in `PreparedLanes` — never in `body`, never per scrub frame (Pitfall 4).
- Three states: **empty** "pick a series" (`explore.empty`, nothing selected), **sparse** "not enough data" (`explore.sparse`, selected but <2 logged points in the window), else `ExploreLanesView(...)`. Both honest states clone `InsightsView.emptyState` (icon `square.stack.3d.up`, `.fixedSize(…, vertical: true)`, `.accessibilityElement(children: .combine)`). All copy via `ExploreCopy`.
- `AppGate` / `ConsentedShell`: a third `.tabItem` for `ExploreView()` in the `.consented` branch only (consent-by-construction, T-15-10), `tab.explore` id **on the `Label`** (Pitfall 5), symbol `square.stack.3d.up` (distinct from Insights' `chart.line.uptrend.xyaxis`). The pre-consent branch is untouched.

## How It Works

- **Suppression filter (T-15-11):** `SeriesPickerView` builds chips from `ExploreSelection.offered(suppressed:)`; `suppressed` flows from the latest snapshot. A suppressed user never even sees a bleeding chip — defense-in-depth with the Phase-14 `!snap.suppressed` accessor gate.
- **Cap (EXPL-02):** the value type's `canAddQuantitative` is the single source of truth — an unselected new quantitative chip is the only thing disabled at the cap; already-selected and event chips stay tappable, so remove-to-add is never blocked.
- **Consent gate (T-15-10):** `ExploreView()` is constructed only inside `ConsentedShell`, which `AppGate` builds only in the `.consented` branch — the tab is structurally unreachable before opt-in.
- **Privacy (T-15-12):** the selection is plain view `@State`; no persistence wrapper exists in either file (token-based grep gate enforced).
- **Honest states (T-15-13 / EXPL-07):** empty and sparse are explicit `ExploreCopy` signals, never a blank lane read as "0"; gaps stay gaps in every rendered lane (inherited from the 15-04 lane primitives).

## Verification

- `xcodegen generate` — succeeded (both new files picked up by the `Sources/EndoApp` glob; no `project.yml` edit).
- `xcodebuild build -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` — **BUILD SUCCEEDED**, Swift 6 strict concurrency clean (both task gates).
- Grep gates (all PASS): `SeriesPickerView` uses `ExploreSelection.offered` + `capHint`/`capReached` + `windowLabel`, no `@AppStorage`/`@SceneStorage`; `ExploreView` uses `ScrollView` + `daySnapshots()` + `explore.empty`/`explore.sparse`, no `@AppStorage`/`@SceneStorage`; `AppGate` has `ExploreView()` + `tab.explore` (on the `Label`); copy via `ExploreCopy` in both views; `ExploreView()` confirmed inside the `.consented` `ConsentedShell` branch.
- Task gates `PICKER_OK` (Task 1) and `SCREEN_OK` (Task 2) both satisfied.
- Full E2E proof (tab present, chips, suppression-hides-bleeding, range switch, empty/sparse, multi-series tooltip) is DEFERRED to 15-06's `ExploreUITests` per plan scope.

## TDD Gate Compliance

Both tasks are `tdd="true"`, but the plan supplies NO XCTest/Swift-Testing target for these SwiftUI views — the `<verify>` block is the structural grep + build gate, and the executable E2E proof is explicitly scoped to 15-06 (`ExploreUITests`, via the `tab.explore` / `explore.*` a11y ids and the `\.uiTestScrubDay` seam wired in 15-04). This mirrors 15-04's posture exactly. The behavioral contracts (suppression hides bleeding, cap disables unselected quantitative chips, color-independent selected state, honest empty/sparse, consent-by-construction) are enforced at this stage by the honesty grep gates over the source, which the build was driven against. No `test(...)`-prefixed RED commit applies; the GREEN `feat(...)` commits carry the verified implementation.

## Task Commits

1. **Task 1 (GREEN): SeriesPickerView** — `140db5a` (feat)
2. **Task 2 (GREEN): ExploreView + third tab** — `43f69e6` (feat)

## Files Created/Modified

- `Sources/EndoApp/Features/Explore/SeriesPickerView.swift` (created) — the suppression-filtered toggle-chip picker (capped, color-independent) + the `TrendWindow` segmented range control + a private `FlowLayout`.
- `Sources/EndoApp/Features/Explore/ExploreView.swift` (created) — the `ScrollView` screen: ephemeral selection + shared scrub, one `daySnapshots()` read, prepare-once cache, empty/sparse/lanes.
- `Sources/EndoApp/App/AppGate.swift` (modified) — the third `Explore` tab in the `.consented` `ConsentedShell` branch, `tab.explore` on the `Label`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reworded the privacy comments to avoid the literal `@AppStorage`/`@SceneStorage` tokens**
- **Found during:** Task 2
- **Issue:** The plan's privacy honesty gate is `! grep -q "@AppStorage\|@SceneStorage"`. My first-draft privacy comments in `ExploreView` documented the LOCKED invariant by NAMING the forbidden wrappers ("owns the selection in EPHEMERAL @State — NEVER @AppStorage/@SceneStorage"). A literal token in a comment trips the token-based gate exactly as code would (`g5 FAIL`).
- **Fix:** Reworded the two comments to "plain view `@State`, never a persistent property-wrapper store" — preserving the privacy intent without the forbidden substrings. The code never used either wrapper. This is the identical gate-compliance edit 15-02 and 15-04 documented (the gate is token-based, not semantic).
- **Files modified:** `Sources/EndoApp/Features/Explore/ExploreView.swift`
- **Verification:** `g5 no-persist OK` after the edit; build + full grep suite all PASS.
- **Committed in:** `43f69e6`

**Total deviations:** 1 (Rule 3 — gate-compliance, within the planned task). No scope creep, no new dependencies, no new health-data surface, no architectural change.

## Known Stubs

None. Both views are pure presentation over the live `daySnapshots()` read + the injected `ExploreSelection`/`ExploreLanesView` contracts. The empty-collection initializers (`@State snapshots = []`, `PreparedLanes` defaults, local accumulators) are legitimate initial state populated from the real repository read on appear — not hardcoded empties flowing to UI as stand-ins. The empty/sparse states are HONEST signals (the plan's explicit EXPL-07 design), not stubs.

## Requirements

EXPL-01 / EXPL-02 / EXPL-06 / EXPL-07 are **delivered in UI** by this plan (the consent-gated third tab, the chip picker + cap + suppression filter, the range presets, the empty/sparse states) but left **Pending** in REQUIREMENTS.md per the execution-context convention — they flip to Complete only when 15-06's `ExploreUITests` E2E proves them end-to-end on a device/simulator. Recorded here as **E2E-proven in 15-06**.

## Next Phase Readiness

- **15-06** can write `ExploreUITests` against the shipped a11y ids: `tab.explore`, `explore.screen`, `explore.disclaimer`, `explore.picker`, `explore.chip.<id>`(`.remove`), `explore.picker.capHint`, `explore.range`(`.<window>`), `explore.empty`, `explore.sparse`, plus the 15-04 `explore.lanes` / `explore.scrub.tooltip.*` ids — driven by the `-uitest-seed-explore` (to be added in 15-06's seam plan) + the existing `-uitest-select-day` / `\.uiTestScrubDay` machinery.
- The Wave-3 surface is complete: the screen wires the data source into the Wave-2 lane stack; the suppression/cap/consent invariants are enforced by composition of the shipped 15-02 value type, the 15-03 copy, and the 15-04 lanes.

## Self-Check: PASSED

- FOUND: Sources/EndoApp/Features/Explore/SeriesPickerView.swift
- FOUND: Sources/EndoApp/Features/Explore/ExploreView.swift
- FOUND: Sources/EndoApp/App/AppGate.swift (tab.explore present)
- FOUND: .planning/phases/15-explore-tab-overlay-lanes/15-05-SUMMARY.md
- FOUND commit: 140db5a (feat — SeriesPickerView)
- FOUND commit: 43f69e6 (feat — ExploreView + third tab)

---
*Phase: 15-explore-tab-overlay-lanes*
*Completed: 2026-06-15*
