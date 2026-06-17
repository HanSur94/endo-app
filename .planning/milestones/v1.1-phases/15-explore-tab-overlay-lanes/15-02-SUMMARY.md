---
phase: 15-explore-tab-overlay-lanes
plan: 02
subsystem: ui
tags: [swift, swift-testing, explore, value-type, swift-charts, ios18, xcodegen]

# Dependency graph
requires:
  - phase: 14-explore-series-catalog
    provides: ExploreSeriesCatalog.all, ExploreSeries (id-only Hashable), ScaleKind.isQuantitative, bleeding.intensity/bleeding.event ids
  - phase: 13-chart-scrub
    provides: TrendWindow (reused as the shared lane time window)
provides:
  - "ExploreSelection — pure app-target value type holding the chosen series Set + TrendWindow window"
  - "4-quantitative-series cap (event/presence series uncapped)"
  - "toggle(_:) add/remove semantics with cap enforcement on insert only"
  - "static offered(suppressed:) catalog filter that hides both bleeding series when suppressed"
  - "ExploreSelectionTests — Swift Testing unit suite (8 @Tests) proving cap/toggle/suppression with catalog-pulled fixtures"
affects: [15-05 SeriesPickerView, 15-03 ExploreCopy, explore-lanes, suppression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure app-target cap/policy value type (Equatable + Sendable), no persistence — UI consumes it via ephemeral @State"
    - "Swift Testing unit suite with fixtures pulled from the shipped catalog by id (cannot drift)"

key-files:
  created:
    - Sources/EndoApp/Features/Explore/ExploreSelection.swift
    - Tests/EndoAppTests/ExploreSelectionTests.swift
  modified: []

key-decisions:
  - "Cap counts ONLY quantitative series (scaleKind.isQuantitative); presence/event series are an uncapped separate allowance"
  - "canAddQuantitative exempts already-selected series so a re-tap to remove is never blocked at the cap"
  - "offered(suppressed:) is a STATIC catalog filter (not cap-model state) — suppression is a presentation policy applied where chips are built"
  - "No @AppStorage/@SceneStorage and no persistence-wrapper token anywhere in the file (ephemeral privacy invariant T-15-03); even the explanatory comment avoids the token so the grep gate stays clean"

patterns-established:
  - "ExploreSelection cap/toggle pattern (RESEARCH Pattern 4) shipped verbatim for 15-05 to consume"
  - "Catalog-pulled test fixtures (ExploreSeriesCatalog.all.first { $0.id == ... }) — never hand-construct an ExploreSeries"

requirements-completed: []  # EXPL-02 stays Pending — the UI that delivers it lands in 15-05; this is pure logic only.

# Metrics
duration: 4min
completed: 2026-06-15
---

# Phase 15 Plan 02: ExploreSelection Pure Value Type Summary

**Pure app-target `ExploreSelection` value type enforcing the 4-quantitative-series cap, add/remove toggle, and the bleeding-suppression catalog filter — proven by an 8-test Swift Testing suite with catalog-pulled fixtures, with zero persistence (ephemeral privacy invariant).**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-06-15T20:45:24Z
- **Completed:** 2026-06-15T20:48:34Z
- **Tasks:** 1 (TDD: RED → GREEN)
- **Files modified:** 2 (both created)

## Accomplishments
- `ExploreSelection` (Equatable, Sendable) with `private(set) var series: Set<ExploreSeries>`, `var window: TrendWindow = .last30`, `quantitativeCap = 4`, `quantitativeCount`, `canAddQuantitative(_:)`, `toggle(_:)`, and `static offered(suppressed:)`.
- The cap is quantitative-only: a 5th distinct quantitative series is blocked, an already-selected series is never blocked (re-tap removes), and presence/event series add beyond the cap and never count toward it.
- `offered(suppressed:)` removes BOTH `bleeding.intensity` and `bleeding.event` for a suppressed user (defense-in-depth alongside the Phase-14 accessor gate) and returns the full catalog otherwise.
- 8 Swift Testing `@Test`s pass via `-only-testing:EndoAppTests/ExploreSelectionTests`, every `ExploreSeries` fixture pulled from `ExploreSeriesCatalog.all` by id so the suite cannot drift from the shipped catalog.
- Honesty grep gates pass: `struct ExploreSelection` present, `scaleKind.isQuantitative` present, and NO `@AppStorage`/`@SceneStorage` token anywhere in the file.

## Task Commits

TDD task — committed across the RED/GREEN gates:

1. **Task 1 (RED): failing ExploreSelectionTests** - `409f753` (test)
2. **Task 1 (GREEN): ExploreSelection implementation** - `5dd62b0` (feat)

_No REFACTOR commit — the minimal GREEN implementation is already the clean shipped shape (RESEARCH Pattern 4)._

## Files Created/Modified
- `Sources/EndoApp/Features/Explore/ExploreSelection.swift` - Pure value type: series Set + TrendWindow window + cap/toggle rules + the static suppression-filter helper.
- `Tests/EndoAppTests/ExploreSelectionTests.swift` - Swift Testing suite proving cap (5th quant blocked, selected exempt, events uncapped), toggle add-then-remove, remove-to-add, and the suppression filter.

## Decisions Made
- Cap counts only quantitative series; event/presence series are an uncapped separate allowance.
- `canAddQuantitative` returns true for an already-selected series even at the cap, so a re-tap to remove is never blocked.
- `offered(suppressed:)` is a static catalog filter rather than cap-model state — suppression is a presentation policy.
- Removed the literal `@AppStorage`/`@SceneStorage` tokens even from the explanatory comment so the plan's `! grep -q` honesty gate passes (the gate is token-based, not semantic).

## Deviations from Plan

None - plan executed exactly as written.

The one minor adjustment (rewording the privacy comment to avoid the literal `@AppStorage`/`@SceneStorage` substrings) was required to satisfy the plan's own token-based grep gate — it is a gate-compliance edit within the planned task, not a scope deviation. No new functionality, no architectural change.

## Issues Encountered
- The plan's verify gate uses `grep -q "TEST SUCCEEDED\|** TEST SUCCEEDED **"`; the local shell resolves `grep` to `ugrep`, which rejects the `**` alternation as an empty subexpression. Confirmed TEST SUCCEEDED via `/usr/bin/grep` (system grep) instead — the test result itself is unaffected (8/8 `@Test`s pass, `** TEST SUCCEEDED **` emitted).

## TDD Gate Compliance
- RED gate: `test(15-02): add failing ExploreSelectionTests` (`409f753`) — verified failing (`cannot find 'ExploreSelection' in scope`) before implementation.
- GREEN gate: `feat(15-02): add ExploreSelection pure value type` (`5dd62b0`) — 8/8 tests pass.
- REFACTOR: none required.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- 15-05's `SeriesPickerView` can consume `ExploreSelection` + `ExploreSelection.offered(suppressed:)` directly.
- EXPL-02 remains Pending — the requirement is delivered when the picker UI lands in 15-05; this plan ships only the provably-correct pure logic.
- Wave 1 is disjoint from 15-01/15-03; no cross-plan coupling introduced.

## Self-Check: PASSED

- FOUND: Sources/EndoApp/Features/Explore/ExploreSelection.swift
- FOUND: Tests/EndoAppTests/ExploreSelectionTests.swift
- FOUND: .planning/phases/15-explore-tab-overlay-lanes/15-02-SUMMARY.md
- FOUND commit: 409f753 (test — RED)
- FOUND commit: 5dd62b0 (feat — GREEN)

---
*Phase: 15-explore-tab-overlay-lanes*
*Completed: 2026-06-15*
