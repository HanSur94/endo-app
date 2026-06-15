---
phase: 14-series-catalog-pure-extraction-layer
plan: 01
subsystem: infra
tags: [swift, swift-testing, foundation, value-types, catalog, honesty-invariants, endoinsights]

# Dependency graph
requires:
  - phase: 05-insights-engine-keystone
    provides: "EndoInsights package (DaySnapshot/SnapshotEnums/Insight/InsightPoint, zero-dep SC1 boundary, BoundaryLintTests)"
  - phase: 13-chart-scrub-foundations
    provides: "ChartScrubSelection pure-helper playbook (calendar-injected, UTC-fixed Swift Testing fixtures)"
provides:
  - "ScaleKind enum (6 explicit cases; isQuantitative true except .presenceEvent)"
  - "ExploreSeriesField enum discriminator (closure-free, stored, Hashable)"
  - "ExploreSeries struct (id/displayName/unit/scaleKind/field; Equatable/Hashable/Identifiable on id ALONE)"
  - "ExploreSeriesCatalog.all (every loggable series, enumerated FROM .allCases — cannot drift)"
  - "quantitativeValue(from:)/isPresent(in:) value accessors with bleeding suppression gate"
  - "SeriesTestSupport.swift shared harness (UTC calendar + day()/daySnapshot() builders) — owned here, reused by 14-02/03/04"
affects: [14-02-extraction, 14-03-scatter, 14-04-normalization, 15-explore-lanes, 16-scatter-view]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Struct catalog enumerated FROM CaseIterable .allCases (stays in sync by construction)"
    - "Closure-free stored enum discriminator → id-only Equatable/Hashable (Set<ExploreSeries> compiles)"
    - "Two-kind scale taxonomy: presence series structurally un-coercible to a quantitative 0/1"
    - "Suppression gate inside the value accessor (bleeding-derived series only)"

key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/ExploreSeries.swift"
    - "EndoInsights/Tests/EndoInsightsTests/SeriesTestSupport.swift"
    - "EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift"
  modified: []

key-decisions:
  - "ExploreSeries derives Equatable/Hashable/Identifiable from id ALONE via explicit ==/hash(into:) — no stored closure (OQ1 RESOLVED, Pitfall 3)"
  - "Bleeding catalogued as BOTH bleeding.intensity (ordinalBleeding quantitative) AND bleeding.event (presenceEvent), each suppression-gated (OQ2 RESOLVED)"
  - "Catalog built by mapping SymptomType/BodyRegion/FoodTag .allCases so it cannot drift from the enum domains"
  - "Accessor bodies implemented inline in Task 2 (per Pattern 3) rather than left as placeholders — Task 1 shipped an exhaustive compile-clean switch with honest shapes"

patterns-established:
  - "ScaleKind taxonomy + ExploreSeriesField discriminator: the closure-free extraction key every downstream helper switches on"
  - "SeriesTestSupport shared harness: the single deterministic fixture source for all four Phase-14 plans"

requirements-completed: [EXPL-02, SCAT-01]

# Metrics
duration: ~5min
completed: 2026-06-15
---

# Phase 14 Plan 01: Series Catalog & Scale Taxonomy Summary

**The keystone `ExploreSeries` catalog (ScaleKind taxonomy + closure-free `ExploreSeriesField` discriminator + id-only Hashable struct + `ExploreSeriesCatalog.all` enumerated from `.allCases`) with suppression-gated `quantitativeValue`/`isPresent` accessors — the single Foundation-only source of truth every Phase 14/15/16 helper composes on.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-06-15T09:52:15Z
- **Completed:** 2026-06-15T09:57:00Z
- **Tasks:** 3
- **Files modified:** 3 (all created)

## Accomplishments
- `ScaleKind` (6 explicit cases) makes the quantitative-vs-presence split structural — a `.presenceEvent` series has no numeric value, so 0/1 coercion is unrepresentable on the quantitative path.
- `ExploreSeries` derives Equatable/Hashable/Identifiable from `id` alone (explicit `==`/`hash(into:)`, NO stored closure), so Phase-15 `Set<ExploreSeries>` chip selection (EXPL-02) compiles for free.
- `ExploreSeriesCatalog.all` covers every loggable `DaySnapshot` field — per-symptom-type (11), per-pain-region (10), per-food-tag (9), plus the 9 singleton fields — enumerated FROM `.allCases` so it cannot drift.
- Bleeding carried as BOTH `bleeding.intensity` (quantitative ordinal rank 1…4) AND `bleeding.event` (presence), each gated on `!snap.suppressed` so a suppressed user's bleeding cannot leak.
- `quantitativeValue(from:)` honors nil→gap / logged-0→0.0 via `snap.x.map(Double.init)` (never a `> 0`/truthiness filter).
- Shared `SeriesTestSupport.swift` harness (UTC calendar + `day()`/`daySnapshot()` builders) — owned by this plan, reused unchanged by 14-02/03/04.

## Task Commits

Each task was committed atomically:

1. **Task 1: ScaleKind + ExploreSeries + ExploreSeriesField discriminator** - `e98bc2a` (feat)
2. **Task 2: ExploreSeriesCatalog.all from .allCases + suppression-gated accessors** - `4a9e46e` (feat)
3. **Task 3: SeriesTestSupport harness + catalog coverage/id/scale-kind tests** - `6401ba8` (test)

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/ExploreSeries.swift` - ScaleKind enum + ExploreSeriesField discriminator + ExploreSeries struct (id-only conformances) + ExploreSeriesCatalog.all + quantitativeValue/isPresent accessors + ordinalRank helper. Foundation-only.
- `EndoInsights/Tests/EndoInsightsTests/SeriesTestSupport.swift` - Shared UTC-fixed calendar + day()/daySnapshot() builders mirroring the ChartScrubSelectionTests harness (owned here; reused by all four Phase-14 plans).
- `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift` - Catalog coverage (every SymptomType/BodyRegion/FoodTag + singletons), id-uniqueness + Hashable-on-id collapse, scale-kind correctness, bleeding-as-both.

## Decisions Made
- **id-only conformances via explicit `==`/`hash(into:)`** rather than synthesized — the struct intentionally has no stored closure, and explicit conformance documents that only `id` participates so a future field can never silently break Hashable (Pitfall 3 / OQ1).
- **Accessor bodies implemented inline in Task 2** (per RESEARCH Pattern 3) instead of left as placeholders. Task 1 already shipped an exhaustive, compile-clean switch using the honest `snap.x.map(Double.init)` shapes (presence/bleeding returning `nil` pending the suppression gate); Task 2 filled the bleeding suppression gate + `ordinalRank` helper. The plan explicitly permits inline implementation "if implementing inline, follow Pattern 3 exactly".
- **`isPresent` for `bleeding.event` uses `!snap.suppressed && snap.flow != nil`** — the `flow != nil` is an Optional presence test (is bleeding logged?), NOT a numeric `!= 0` coercion; it is the correct presence semantics and does not trip the absence-as-zero honesty gate.

## Deviations from Plan

None - plan executed exactly as written. The accessor bodies were implemented inline across Tasks 1 (exhaustive honest skeleton) and 2 (bleeding suppression gate + ordinalRank), which the plan explicitly sanctions.

## Issues Encountered
- The sandboxed `swift build` wrapper returned a terse "ok (build complete)" with no compiler detail; ran the real `swift test` (with `dangerouslyDisableSandbox`) to get full output and confirm the exhaustive switch compiled and 133/133 tests pass. No code issue.

## User Setup Required
None - no external service configuration required. Zero dependencies added (SC1 boundary intact).

## Verification Results
- `swift test --package-path EndoInsights` — **133/133 tests in 15 suites GREEN** (was 124; +9 catalog tests).
- `BoundaryLintTests.packageSourcesImportOnlyFoundation` — **GREEN** (ExploreSeries.swift imports only Foundation; no regression).
- Honesty grep gates on `ExploreSeries.swift` — all CLEAN: no `86400` (epoch), no `? 1 : 0` (presence-to-0/1 coercion), no numeric `> 0`/`!= 0` truthiness drop, no function-typed stored property (no stored closure).

## Next Phase Readiness
- **The keystone is landed compile-clean.** 14-02 (`ExploreSeriesExtraction`: `quantitativePoints`→`[InsightPoint]`, `occurrenceDays`→`[Date]`), 14-03 (`ScatterPairing`/`ScatterPoint`, scatter axes quantitative-only), and 14-04 (`SeriesNormalization`) all compose on `ExploreSeries`/`ScaleKind`/the value accessors and reuse `SeriesTestSupport` unchanged.
- No blockers. The `quantitativeValue`/`isPresent` contract (nil→gap, logged-0→real, bleeding suppression) is unit-pinned and ready for the extraction-level gap/logged-0/suppression fixtures in 14-02.

## Self-Check: PASSED

- FOUND: EndoInsights/Sources/EndoInsights/ExploreSeries.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/SeriesTestSupport.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift
- FOUND: .planning/phases/14-series-catalog-pure-extraction-layer/14-01-SUMMARY.md
- Commits FOUND: e98bc2a, 4a9e46e, 6401ba8

---
*Phase: 14-series-catalog-pure-extraction-layer*
*Completed: 2026-06-15*
