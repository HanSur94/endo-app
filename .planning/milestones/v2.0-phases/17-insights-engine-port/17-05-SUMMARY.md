---
phase: 17-insights-engine-port
plan: 05
subsystem: testing
tags: [kotlin, jvm, charts, explore-series, scatter, normalization, chart-scrub, honesty, parity]

# Dependency graph
requires:
  - phase: 17-02
    provides: DaySnapshot / SymptomPoint / PainPoint value types, InsightPoint, SnapshotEnums, SnapshotFixtures
provides:
  - "ExploreSeries (ScaleKind, ExploreSeriesField sealed interface, id-only ExploreSeries, ExploreSeriesCatalog.all)"
  - "ExploreSeriesExtraction (kind-gated quantitativePoints -> List<InsightPoint>, occurrenceDays -> List<LocalDate>)"
  - "ScatterPairing (ScatterPoint, count-encoded both-logged pairing, no fitted line / no r-value)"
  - "SeriesNormalization (fixed per-scale domains, clamped normalize/denormalize, honest readout)"
  - "ChartScrubSelection (Resolution, exact-day resolver, gap != zero, no interpolation / no nearest-neighbour)"
  - "SeriesTestSupport.kt chart-prep fixtures + 39 ported parity test cases"
affects: [17-08, 22-explore-charts, 22-scatter]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Calendar dropped for LocalDate identity (startOfDay / isDate(inSameDayAs:) become no-op / ==)"
    - "id-only equals/hashCode on a value class (ExploreSeries) so Set collapses on id"
    - "Swift enum-with-associated-values -> Kotlin sealed interface (ExploreSeriesField)"
    - "whole-number readout via value == floor(value) (avoids kotlin.math.round banker's rounding)"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/ExploreSeries.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/ExploreSeriesExtraction.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/ScatterPairing.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/SeriesNormalization.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/ChartScrubSelection.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/SeriesTestSupport.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ExploreSeriesCatalogTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ExploreSeriesExtractionTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ScatterPairingTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/SeriesNormalizationTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ChartScrubSelectionTests.kt
  modified:
    - android/insights/PARITY.md

key-decisions:
  - "Dropped the injected Calendar parameter throughout (LocalDate is zone-free day identity); ChartScrubSelection 23:59-boundary + DST-spring-forward cases preserved as day-identity pins that now hold by construction."
  - "ScaleKind carries a raw: String mirroring the Swift enum case names, for fidelity (not drift-guarded)."
  - "ExploreSeries is a plain class with explicit id-only equals/hashCode (not a data class) to preserve the Swift id-only conformance."

patterns-established:
  - "Chart data-prep consumes InsightPoint/enums from plan 02; imports only java.time / kotlin.* (BoundaryLint-clean)."
  - "Wrong-kind extractor/axis routing degrades to [] — never a fabricated 0, never a crash."

requirements-completed: [AENG-03]

coverage:
  - id: D1
    description: "ExploreSeries catalog complete with unique ids matching the research contract"
    requirement: AENG-03
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/ExploreSeriesCatalogTests.kt (9 cases)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Kind-gated extractors: quantitativePoints -> List<InsightPoint>, occurrenceDays -> List<LocalDate>; wrong-kind routing degrades to [], absence != 0"
    requirement: AENG-03
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/ExploreSeriesExtractionTests.kt (10 cases)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Scatter pairs both-logged days only, count-encoded, no fitted line / no r-value / no jitter"
    requirement: AENG-03
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/ScatterPairingTests.kt (7 cases)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Series normalization: fixed clamped per-scale domains, honest readout, no silent normalization"
    requirement: AENG-03
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/SeriesNormalizationTests.kt (6 cases)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Scrub resolves exact-day value only — gap day -> no point (never nearest-neighbour / interpolation); logged severity-0 -> real zero"
    requirement: AENG-03
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/ChartScrubSelectionTests.kt (7 cases)"
        status: pass
    human_judgment: false

# Metrics
duration: 12min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 05: Chart Data-Prep Layer Summary

**Ported the ExploreSeries catalog + extraction/scatter/normalization/scrub chart data-prep to pure-Kotlin JVM under the locked chart-honesty rules (gaps stay gaps, no silent normalization, count-encoded scatter with no fitted line/r-value), 39 parity cases green.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-07-09T09:46:xxZ
- **Completed:** 2026-07-09T09:58:26Z
- **Tasks:** 3
- **Files modified:** 12 (11 created + PARITY.md)

## Accomplishments
- Ported the full chart data-prep layer (5 source files) under the locked honesty rules: no dual y-axis, no silent normalization, gap days are gaps (never zeros / interpolation), scatter descriptive-only (no fitted line, no r-value).
- Preserved the exact `ExploreSeries` ids / signalKey fragments (`symptom.<type.raw>.severity`, `pain.<region.raw>.severity`, `food.<tag.raw>`, `sleep.hours`, `activity.minutes`, `stress`, `mood`, `bleeding.intensity`, `bleeding.event`, `clots`, `prn`, `medication.taken`) consumed by `:app` charts in Phase 22.
- 39 parity cases green (Catalog 9 + Extraction 10 + Scatter 7 + Normalization 6 + Scrub 7); full `:insights:test` suite still green (16 test classes).
- PARITY ledger advanced from 56 → 95 of 157 Kotlin cases.

## Task Commits

Each task was committed atomically:

1. **Task 1: ExploreSeries + catalog + SeriesTestSupport fixtures** - `4d4aac0` (feat)
2. **Task 2: ExploreSeriesExtraction + ScatterPairing** - `f624347` (feat)
3. **Task 3: SeriesNormalization + ChartScrubSelection** - `f58c7a3` (feat)

**PARITY ledger update:** `dbbfb99` (docs)

## Files Created/Modified
- `ExploreSeries.kt` - ScaleKind (6 raw values), ExploreSeriesField sealed interface, id-only ExploreSeries, value accessors (quantitativeValue/isPresent, suppression-gated), ExploreSeriesCatalog.all built from enum `entries`.
- `ExploreSeriesExtraction.kt` - quantitativePoints -> List<InsightPoint>, occurrenceDays -> List<LocalDate>; wrong-kind routing degrades to [].
- `ScatterPairing.kt` - ScatterPoint (x/y/count only), both-logged count-encoded PairKey aggregation, sorted by (x,y); no jitter/fitted-line/r-value.
- `SeriesNormalization.kt` - fixed per-scale ClosedFloatingPointRange domains, clamped normalize/denormalize, honest readout (value == floor(value) whole-number check).
- `ChartScrubSelection.kt` - Resolution data class, exact-day resolver via LocalDate ==, gap -> null point, logged severity-0 -> real 0.
- `SeriesTestSupport.kt` - chart-prep fixtures (0 cases): SeriesFixtures.day + SeriesFixtures.daySnapshot.
- 5 test files (39 cases) + `PARITY.md` incremental Kotlin-column update.

## Decisions Made
- **Calendar dropped for LocalDate identity** (locked decision, consistent with plans 02–04): `calendar.startOfDay(for:)` becomes a no-op and `Calendar.isDate(_:inSameDayAs:)` becomes `LocalDate ==`. Extractor/resolver signatures drop the `calendar:` parameter.
- **ExploreSeries as a plain class** with explicit id-only equals/hashCode (not a Kotlin data class), faithfully reproducing the Swift id-only Equatable/Hashable so `Set<ExploreSeries>` collapses on id.
- **Swift `enum ExploreSeriesField` with associated values → Kotlin sealed interface** (data classes for payload cases, data objects for singletons).
- **Whole-number readout uses `value == floor(value)`** rather than `kotlin.math.round` (which is banker's / ties-to-even), per the plan's explicit instruction.

## Deviations from Plan

### Test-body adaptation (Calendar → LocalDate translation)

**1. [Rule 3 - Blocking translation] ChartScrubSelectionTests 23:59-boundary + DST-spring-forward cases adapted to the LocalDate model**
- **Found during:** Task 3 (ChartScrubSelection port)
- **Issue:** The Swift `lateEveningRawDate_matchesSameDay` (23:59) and `dstSpringForwardDay_resolvesCorrectly` cases pin `Date`→day `startOfDay` bucketing. Under the locked LocalDate identity there is no wall-clock/time component and no DST, so those exact assertions are not directly expressible.
- **Fix:** Preserved both as distinct `@Test` methods that pin the SAME invariant ("never roll into a neighbor") on a late-in-day / DST-transition calendar date — the invariant now holds by construction (LocalDate is zone-free). Case count stays 7/7; the honesty guarantee is unchanged.
- **Files modified:** `ChartScrubSelectionTests.kt`
- **Verification:** `./gradlew :insights:test --tests "...ChartScrubSelectionTests"` → 7/7 pass
- **Committed in:** `f58c7a3` (Task 3 commit)

---

**Total deviations:** 1 (test-body translation, no scope change).
**Impact on plan:** None — the adaptation is inherent to the locked Calendar→LocalDate decision already applied in plans 02–04; parity case count and honesty invariants are fully preserved.

## Issues Encountered
- A transient compile error (invalid `in_ snap` parameter syntax where `in` is a Kotlin keyword) in `ExploreSeries.isPresent` — renamed the parameter to `snap`, recompiled green. Fixed before the Task 1 commit.

## User Setup Required
None - no external service configuration required. Pure JVM module, no emulator, no new dependencies.

## Next Phase Readiness
- Chart data-prep layer complete and parity-tracked (95/157). Ready for 17-06 (CorrelationEngine, 12) and 17-07 (Recommendations/Flare, 13); the 17-08 reconciliation will close the ledger to 157.
- `ExploreSeries` ids / signalKey fragments are stable and available for `:app` Canvas charts in Phase 22.

## Self-Check: PASSED

---
*Phase: 17-insights-engine-port*
*Completed: 2026-07-09*
