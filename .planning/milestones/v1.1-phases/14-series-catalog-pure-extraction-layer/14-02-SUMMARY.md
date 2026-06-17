---
phase: 14-series-catalog-pure-extraction-layer
plan: 02
subsystem: infra
tags: [swift, swift-testing, foundation, value-types, extraction, honesty-invariants, endoinsights]

# Dependency graph
requires:
  - phase: 14-series-catalog-pure-extraction-layer
    plan: 01
    provides: "ExploreSeries/ScaleKind/ExploreSeriesField + ExploreSeriesCatalog.all + quantitativeValue(from:)/isPresent(in:) accessors + SeriesTestSupport.swift harness"
  - phase: 05-insights-engine-keystone
    provides: "InsightPoint value type (date/value) — emitted unchanged so ChartSeriesGrouping is reused"
  - phase: 13-chart-scrub-foundations
    provides: "ChartScrubSelection pure-helper playbook (calendar-injected, UTC-fixed Swift Testing fixtures)"
provides:
  - "ExploreSeriesExtraction.quantitativePoints(_:series:calendar:) -> [InsightPoint] (nil → gap; logged 0 → real point at 0)"
  - "ExploreSeriesExtraction.occurrenceDays(_:series:calendar:) -> [Date] (presence path; NO value field, 0/1 coercion unrepresentable)"
  - "Seven unit-pinned extraction honesty fixtures (gap / logged-0 / symptom-0 / presence-emitted / presence-absent / suppression no-leak / un-suppressed mirror)"
affects: [14-03-scatter, 14-04-normalization, 15-explore-lanes, 16-scatter-view]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Two-return-type extraction: quantitative → [InsightPoint], presence → [Date] (no value field → 0/1 coercion unrepresentable by construction)"
    - "compactMap honesty: nil → gap (never a fabricated 0), logged literal 0 → a real point (never dropped)"
    - "Pure enum-of-static-funcs namespace mirroring ChartScrubSelection (calendar.startOfDay, never epoch arithmetic)"
    - "Suppression enforced upstream in the Plan-01 accessors → both bleeding-derived paths emit nothing for a suppressed snapshot"

key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift"
    - "EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift"
  modified: []

key-decisions:
  - "Two kind-gated entry points returning two DIFFERENT types — the presence path returns [Date] (no value field), so coercing a tag to 0/1 is unrepresentable in the type system, not merely unchecked (SC3, T-14-03)"
  - "quantitativePoints uses compactMap over series.quantitativeValue(from:): nil → no point (gap, absence ≠ 0); a logged literal 0 returns 0.0 from the accessor → kept as a real InsightPoint (SC2, T-14-01)"
  - "Each entry point asserts its kind via precondition (quantitative.isQuantitative / == .presenceEvent) — a caller-contract, since presence/quantitative route to different funcs"
  - "Emit the existing InsightPoint (date: startOfDay, value) so the shipped ChartSeriesGrouping (app target) and ChartScrubSelection are reused UNCHANGED downstream (SC5)"
  - "Suppression is NOT re-checked here — the Plan-01 accessors already gate bleeding.intensity and bleeding.event on !snap.suppressed, so both extraction paths emit nothing for a suppressed snapshot (no leak, T-14-02)"

patterns-established:
  - "ExploreSeriesExtraction: the two-return-type extraction layer every Phase-15 Explore lane and Phase-16 scatter view composes on"

requirements-completed: [EXPL-03, EXPL-04, EXPL-05, EXPL-07]

# Metrics
duration: ~10min
completed: 2026-06-15
---

# Phase 14 Plan 02: Pure Extraction Layer Summary

**`ExploreSeriesExtraction` — two kind-gated pure static funcs returning two DIFFERENT types: `quantitativePoints(...) -> [InsightPoint]` (nil → gap, logged literal 0 → a real point at 0) and `occurrenceDays(...) -> [Date]` (the presence path with NO value field, so 0/1 coercion is unrepresentable by construction), pinned by seven UTC-fixed honesty fixtures including the suppression no-leak invariant.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-06-15T11:58:00Z
- **Completed:** 2026-06-15T12:03:00Z
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments
- `quantitativePoints(_:series:calendar:) -> [InsightPoint]`: a `compactMap` over `series.quantitativeValue(from:)` — an unlogged field emits NO point (gap, absence ≠ 0, never a fabricated 0); a logged literal 0 (stress 0, symptom severity 0) emits a REAL `InsightPoint` at value 0 (the accessor returns `0.0`, never dropped).
- `occurrenceDays(_:series:calendar:) -> [Date]`: the presence/event path returns a DISTINCT type with NO `value` field — "coerce a tag to 0/1" is unrepresentable in the type system (the central success criterion, SC3 / T-14-03), enforced by construction, not by a runtime check.
- Both bleeding-derived series emit nothing for a suppressed snapshot: the Plan-01 accessors already gate `bleeding.intensity` and `bleeding.event` on `!snap.suppressed`, so the no-leak fixture asserts zero points AND zero occurrences (T-14-02). The un-suppressed mirror fixture proves the gate only suppresses — it never universally drops.
- Day normalization uses `calendar.startOfDay(for:)` ONLY (calendar-injected, DST-safe) — never epoch arithmetic. Emits the existing `InsightPoint` so the shipped `ChartSeriesGrouping` (gap-breaking runs) and `ChartScrubSelection` (scrub resolver) are reused UNCHANGED downstream (SC5).
- Seven fixtures in the `ChartScrubSelectionTests` UTC-fixed style: gap / logged-0 / logged-symptom-0 / presence-emitted / presence-absent / suppression-no-leak / un-suppressed-mirror.

## Task Commits

Each task was committed atomically:

1. **Task 1: ExploreSeriesExtraction — the two-return-type honesty move** - `c328a60` (feat)
2. **Task 2: Honesty fixtures (gap / logged-0 / presence / presence-absent / suppression no-leak)** - `4ddec11` (test)

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift` - `public enum ExploreSeriesExtraction` (pure namespace) with `quantitativePoints` (→ `[InsightPoint]`, compactMap nil→gap / logged-0→real) and `occurrenceDays` (→ `[Date]`, presence path with no value field). Foundation-only.
- `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift` - `@Suite` with seven fixtures resolving series from `ExploreSeriesCatalog.all`, using the Plan-01 `SeriesTestSupport` helpers (UTC calendar + day()/daySnapshot()) — never a real `Date()`.

## Decisions Made
- **Two return types, not one with a kind flag** — the presence path returns `[Date]` with no value field, so the 0/1 coercion is impossible to even write. This is the structural honesty move (SC3); a single `[InsightPoint]` return would have made `present ? 1 : 0` expressible.
- **`precondition` on the series kind at each entry point** — `quantitativePoints` requires `series.scaleKind.isQuantitative`, `occurrenceDays` requires `== .presenceEvent`. A caller-contract: presence and quantitative series route to different funcs by design.
- **Suppression not re-checked here** — the Plan-01 accessors already gate both bleeding series on `!snap.suppressed`, so re-gating would be redundant and risk a second, drift-prone copy of the rule. The no-leak fixture proves the upstream gate closes both extraction paths.

## Deviations from Plan
None - plan executed exactly as written. Both tasks shipped as specified; all seven fixtures (the plan's five named invariants plus the logged-symptom-0 and un-suppressed-mirror reinforcements the plan also lists) pass.

## Issues Encountered
- The sandboxed `swift test` wrapper returns terse output; ran the real `swift test --package-path EndoInsights` (with `dangerouslyDisableSandbox`) to get full per-test detail and confirm the 7 new fixtures and BoundaryLint pass. No code issue.

## User Setup Required
None - no external service configuration required. Zero dependencies added (SC1 boundary intact).

## Verification Results
- `swift test --package-path EndoInsights` — **140/140 tests in 16 suites GREEN** (was 133; +7 extraction fixtures).
- `BoundaryLintTests.packageSourcesImportOnlyFoundation` — **GREEN** (`ExploreSeriesExtraction.swift` imports ONLY Foundation; no regression).
- Honesty grep gates on `ExploreSeriesExtraction.swift` — all CLEAN: no `86400` (epoch); no `? 1 : 0`/`? 1.0 : 0`/`present ? 1` (presence-to-0/1 coercion); no `> 0`/`!= 0` (truthiness drop of logged-0); the `value` token appears in code ONLY inside `quantitativePoints` (the `InsightPoint` construction, lines 41-42) — NEVER inside `occurrenceDays`.

## Next Phase Readiness
- **The extraction layer is landed compile-clean and unit-pinned.** 14-03 (`ScatterPairing`/`ScatterPoint`, scatter axes quantitative-only) and 14-04 (`SeriesNormalization`) compose on these two entry points and reuse `SeriesTestSupport` unchanged.
- No blockers. The absence-≠-0 (SC2), presence-unrepresentable-as-0/1 (SC3), and suppression-no-leak invariants are unit-pinned; the emitted `InsightPoint` keeps `ChartSeriesGrouping`/`ChartScrubSelection` reusable for Phase 15.

## Self-Check: PASSED

- FOUND: EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift
- FOUND: .planning/phases/14-series-catalog-pure-extraction-layer/14-02-SUMMARY.md
- Commits FOUND: c328a60, 4ddec11

---
*Phase: 14-series-catalog-pure-extraction-layer*
*Completed: 2026-06-15*
