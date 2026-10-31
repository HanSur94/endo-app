---
phase: 22-insights-explore-charts
plan: 02
subsystem: insights-charts
tags: [compose, canvas, drawscope, chart-honesty, scrub, immutable, copy-audit]

# Dependency graph
requires:
  - phase: 17-insights-engine-port
    provides: "ChartScrubSelection (exact-day resolver, gap→null), SeriesNormalization.readout, InsightPoint, ScaleKind"
  - phase: 19-bloom-design-system
    provides: "BloomColors (plum/inkMuted/card/strokeSubtle), BloomTypography, BloomTheme"
provides:
  - "ChartSeriesGrouping.runs: pure gap-honest run-segmentation (H1 line breaks, no zero-fill, keeps logged 0)"
  - "ChartCoordinateMapper + ChartPrimitives: reusable DrawScope drawing off a plain DrawScope (Phase 24 PDF reuse)"
  - "TrendChart (@Immutable input, draw-phase scrub, lollipop tooltip, day-change haptic seam)"
  - "ChartsCopy (registered): Not logged / Severity n of 10 via SeriesNormalization.readout"
affects: [22-03 chart a11y semantics overlay, 22-04 trend card embed, 22-05 explore lanes/scatter, 24 pdf-report]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Immutable input holder → skippable chart composable (no unstable List param recompose)"
    - "Day-granular raw scrub read in the DRAW phase (drawWithCache/onDrawBehind); gesture de-dupes within-day → no recompose/redraw"
    - "Reusable drawing factored as DrawScope extensions (ChartPrimitives object) off a plain DrawScope — on-screen chart + Phase-24 PDF share the SAME geometry"
    - "Run `Path`s cached in remember(runs, mapper); only the scrub rule/highlight draws per frame"
    - "The ONLY raw-day→value path is ChartScrubSelection.resolve — pinned by a comment-stripped source scan"

key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/ChartSeriesGrouping.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/ChartPrimitives.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/TrendChart.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/content/ChartsCopy.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/charts/ChartSeriesGroupingTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/charts/TrendChartHonestyTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt"

key-decisions:
  - "ChartPrimitives is an `object` of DrawScope member-extensions (drawRuns/drawPoints/drawScrubRule/drawHighlight) + a pure ChartCoordinateMapper — callable via `with(ChartPrimitives){}` from ANY DrawScope, including the Phase-24 PDF page (no Composable-only coupling)"
  - "Raw scrub is a day-granular LocalDate (not a raw pixel): the gesture snaps x→day and only writes state on a day change, so within-day finger movement is fully inert (the performance guarantee that keeps Vico unnecessary)"
  - "The lollipop tooltip is a real Composable overlay (not Canvas drawText) so its testTags live in the semantics tree; it reads a `derivedStateOf` snapping to day granularity → recomposes only on a day CROSSING"
  - "ChartsCopy owns only chart chrome (Not logged, Severity prefix/unit, readout template); the text summary is caller-provided (engine-audited), mirroring iOS where chartSummary is InsightsCopy"

requirements-completed: [AINS-06, AINS-03]

coverage:
  - id: H1
    description: "Gaps stay gaps — an unlogged day is a line BREAK (one Path per contiguous run), never a zero, never interpolation"
    requirement: "AINS-06"
    verification:
      - kind: unit
        ref: "ChartSeriesGroupingTest (twoRuns/consecutive/multipleGaps/noZeroFill)"
        status: pass
      - kind: unit
        ref: "TrendChartHonestyTest.scrubGapDay_readsNotLogged_neverAZero (Robolectric+Compose)"
        status: pass
    human_judgment: false
  - id: H2
    description: "Absence ≠ zero — a logged severity-0 day is a real point at the zero line; a gap has no mark"
    requirement: "AINS-06"
    verification:
      - kind: unit
        ref: "ChartSeriesGroupingTest.loggedSeverityZeroDay_staysARealPoint + mapper_severityZero_mapsToBottomZeroLine"
        status: pass
      - kind: unit
        ref: "TrendChartHonestyTest.scrubLoggedSeverityZeroDay_readsRealZero_andCarriesNoGapMarker"
        status: pass
    human_judgment: false
  - id: SCRUB
    description: "Scrub reads the EXACT day/value via ChartScrubSelection.resolve — no nearest-point / value(atX / interpolation math anywhere"
    requirement: "AINS-03"
    verification:
      - kind: unit
        ref: "TrendChartHonestyTest.source_usesOnlyTheResolver_noNearestPointOrInterpolation_andInputIsImmutable + scrubLoggedNonZeroDay_readsItsRealValue"
        status: pass
    human_judgment: false
  - id: PERF
    description: "@Immutable input + draw-phase-only scrub reads keep Canvas viable (no recomposition storm)"
    requirement: "AINS-03"
    verification:
      - kind: unit
        ref: "source scan asserts @Immutable on TrendChartInput"
        status: pass
      - kind: manual
        ref: "device Layout-Inspector flat-recomposition (22-VALIDATION Manual-Only) — deferred to device pass"
        status: deferred
    human_judgment: true

# Metrics
duration: 42min
completed: 2026-07-09
status: complete
---

# Phase 22 Plan 02: Trend Canvas Chart + Reusable Chart Primitives Summary

**The honest `.trend` line chart rendered with Compose `Canvas` — one `Path` per contiguous logged-day run so the line BREAKS at every gap (H1), a real dot at the zero line for a logged severity-0 (H2), and an exact-day scrub routed ONLY through `ChartScrubSelection.resolve` — plus the pure `ChartSeriesGrouping` run-segmentation and the reusable `ChartPrimitives`/`ChartCoordinateMapper` drawing factored off a plain `DrawScope` for Phase 24's PDF report.**

## Performance
- **Duration:** ~42 min
- **Completed:** 2026-07-09
- **Tasks:** 2
- **Files:** 7 (6 created, 1 modified)

## Accomplishments
- **`ChartSeriesGrouping`** (pure, no Compose/Android import): `runs(points)` sorts by date and splits into contiguous calendar-day runs — a new run starts whenever `prev.date.plusDays(1) != next.date`. Never fabricates a `value = 0`, keeps a literal logged severity-0 point, returns a single-point run for an isolated day. The Android mirror of the iOS `ChartSeriesGrouping.runs`.
- **`ChartPrimitives` + `ChartCoordinateMapper`** (reusable, no Compose-runtime state): a pure date+value→pixel mapper (absolute-date x, FIXED `0..10` y inverted so the domain floor sits at the bottom zero line, plus `dateAt(x)` for the gesture snap) and `DrawScope` member-extensions `drawRuns` (one `Path` per run — breaks between runs), `drawPoints` (one dot per logged day), `drawScrubRule`, `drawHighlight`. Parameterized on color/size so the on-screen chart passes `plum` and the Phase-24 PDF path can pass a single print-ink color — the SAME drawing code, callable via `with(ChartPrimitives){}` from any bare `DrawScope`.
- **`TrendChart`** (Compose `Canvas`): an `@Immutable TrendChartInput` holder (skippable composable), a one-line text summary above the chart, run-segmented line + per-day points cached in `remember(runs, mapper)`, a day-granular raw scrub read in the DRAW phase (`drawWithCache`/`onDrawBehind`) so within-day finger movement is fully inert, a lollipop tooltip (Bloom `card` + `strokeSubtle` hairline, `RoundedCornerShape(8.dp)`) reading `Severity {n} of 10` (logged) or `Not logged` (gap, de-emphasized), and an `onDayChanged` haptic seam firing on dayA→dayB only. The ONLY raw-day→value path is `ChartScrubSelection.resolve`.
- **`ChartsCopy : AuditableCopy`** (registered in `CopyAuditRegistry.sources` in the same change): `NOT_LOGGED`, the `Severity` prefix + `of 10` unit, and `severityReadout(value)` composed via the honest `SeriesNormalization.readout` (whole numbers render without a trailing `.0`). All strings substring-clean against `ClaimsPolicy`.

## Task Commits
1. **Task 1: pure ChartSeriesGrouping + reusable ChartPrimitives + registered ChartsCopy** — `b62db25` (feat)
2. **Task 2: TrendChart Canvas composable + honesty asserts** — `e9c807e` (feat)

## REAL Test Results
- `:app:testDebugUnitTest` — **270/270 pass, 0 failures, 0 skipped** (254 baseline + 16 new: `ChartSeriesGroupingTest` 11, `TrendChartHonestyTest` 5).
- `:insights:test` — **176/176 pass** (source untouched).
- `NotificationSeparationTest` — **3/3 pass**; it scans `src/main/kotlin/com/endoapp/app` (incl. `ui.charts`), confirming `TrendChart` imports NO notification/alarm API.
- `CopyAuditTest` — green over the enlarged catalog including `ChartsCopy` (substring-clean).
- `:app:assembleDebug` — **SUCCESSFUL**. `:app:lintDebug` — **SUCCESSFUL**.
- Honesty invariants PROVEN: H1 (gap → two runs / line break, no zero-fill) on data-prep + a gap-day scrub reading `Not logged`; H2 (logged severity-0 → real point at the zero line via the mapper) on data-prep + a scrub reading a real `Severity 0 of 10` with NO gap marker; the exact-day resolver is the ONLY value path (comment-stripped source scan finds no `value(atX`/`minBy`/`minByOrNull`/`timeIntervalSince`/`interpolat`); `TrendChartInput` is `@Immutable`.

## Reusability confirmation (Phase 24 PDF)
`ChartPrimitives` and `ChartCoordinateMapper` hold **no** Compose-runtime state — no `remember`, no snapshot state, no `@Composable`. They are `DrawScope` extensions + a pure mapper, invoked with `with(ChartPrimitives){}` and parameterized on stroke/point color + size. Phase 24's `UIGraphicsPDFRenderer`-equivalent can obtain a plain `DrawScope` (or the Android PDF `Canvas`-backed one) and call the identical `drawRuns`/`drawPoints` with a print-ink color — the run-segmentation break geometry is byte-for-byte the same as the live chart.

## Deviations from Plan
None — the plan executed as written. `ChartCoordinateMapper` was added inside `ChartPrimitives.kt` (the plan's "a coordinate mapper" primitive), and its geometry is covered by `ChartSeriesGroupingTest` (the chart data-prep honesty file) rather than a separate test file, keeping to the plan's file list.

## Known Stubs
None. The chart is fully wired against real `InsightPoint` data and the live `ChartScrubSelection` resolver; the caller (Plan 04) supplies the `@Immutable` input + summary and the haptic callback.

## User Setup Required
None.

## Next Phase Readiness
- Plan 03 adds the bespoke per-data-point TalkBack semantics overlay over this SAME `ChartScrubSelection` resolver (this plan delivered the VISUAL chart + visual scrub only).
- Plan 04 embeds `TrendChart` in the `.trend` card (produces the `@Immutable TrendChartInput` + the engine-audited summary + the device haptic on `onDayChanged`).
- Plans 05 (lanes/scatter) and 24 (PDF) reuse `ChartSeriesGrouping` + `ChartPrimitives`.

## Self-Check: PASSED

All 6 created files + the modified `AuditableCopy.kt` and this SUMMARY exist on disk; both task commits (`b62db25`, `e9c807e`) are in the git log.

---
*Phase: 22-insights-explore-charts*
*Completed: 2026-07-09*
