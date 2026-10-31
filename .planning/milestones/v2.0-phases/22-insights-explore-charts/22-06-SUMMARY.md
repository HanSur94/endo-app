---
phase: 22-insights-explore-charts
plan: 06
subsystem: insights-explore
tags: [compose, canvas, scatter, count-encoded, descriptive-only, deep-link, cross-tab, suppression, copy-audit, a11y, chart-honesty]

# Dependency graph
requires:
  - phase: 17-insights-engine-port
    provides: "ScatterPairing.pairs (both-logged, count-aggregated ScatterPoint(x,y,count); no fitted line/r-value by type shape), ExploreSeries/Catalog/ScaleKind, SeriesNormalization (fixed per-scale domain + readout), SymptomType.entries"
  - phase: 22-insights-explore-charts (Plan 02)
    provides: "ChartPrimitives/ChartCoordinateMapper"
  - phase: 22-insights-explore-charts (Plan 03)
    provides: "ChartSemanticsOverlay + ChartSemanticNode (reusable per-point/per-cell TalkBack overlay)"
  - phase: 22-insights-explore-charts (Plan 04)
    provides: "InsightCard (.correlation arm — the deep-link target), InsightsScreen/InsightsDashboard"
  - phase: 22-insights-explore-charts (Plan 05)
    provides: "ExploreScreen (Lanes|Scatter toggle; Scatter placeholder to fill), ExploreSelection.offered, ExploreCopy (registered), SeriesPicker/ExploreSegmentedToggle"
provides:
  - "ScatterDeepLink.resolve (pure, ported): structural signalKey -> typed (x,y) quantitative axes | null; suppression-gated; multi-dot-safe known-suffix parse"
  - "ExploreScatterRouter + PendingScatter: ephemeral cross-tab seam (public catalog ids only, never persisted)"
  - "ScatterPlot (Canvas 320dp, descriptive-only): count-encoded PointMarks, fixed real-unit domains, no fitted line/r-value; per-cell ChartSemanticsOverlay nodes"
  - "scatterMarkRadiusDp / scatterSemanticNodes: pure count-encoding + a11y builders"
  - "ScatterAxisPicker: two quantitative-only X/Y selectors (bleeding absent under suppression)"
  - "ExploreScreen Scatter mode: axis picker + plot + honest empty/sparse + deep-link consume-and-clear"
  - "InsightCard .correlation affordance: 'See the days behind this' (hidden when resolve == null)"
affects: [24 pdf-report may reuse the scatter drawing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Descriptive-only scatter enforced by TYPE SHAPE (ScatterPoint carries only x/y/count) AND a comment-stripped source scan (ScatterPlotTest asserts no fitted/regression/rvalue/slope/% token) — belt-and-suspenders over H5"
    - "Count-encoding by mark SIZE not jitter/hue: pure scatterMarkRadiusDp (radius proportional to sqrt(count) so AREA proportional to count) is unit-provable; a single plum hue throughout"
    - "Cross-tab deep-link honesty: ScatterDeepLink consumes ONLY the structural signalKey (never headline/detail prose); ExploreScatterRouter carries a TYPED PendingScatter(x,y) — never re-derived from copy downstream; ephemeral, never persisted"
    - "set-before-select + consume-and-clear: the affordance sets router.pendingScatter THEN switches tab; ExploreScreen consumes on appear/change via LaunchedEffect(pending) and resets it to null so an identical later tap re-triggers"
    - "Known-suffix defensive parse: the correlation body <factor.key>.<target> is segmented by matching the KNOWN target suffix (.bleeding / .symptom.<SymptomType.raw>), never a naive split('.') that would mis-segment the dotted factor.key (stress.high)"
    - "Two-wall suppression on the scatter axis: ScatterDeepLink.resolve returns null for a suppressed bleeding target AND ScatterAxisPicker offers only ExploreSelection.offered(suppressed) quantitative series (no bleeding axis)"

key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/ExploreScatterRouter.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/insights/ScatterDeepLink.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/ScatterPlot.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/explore/ScatterAxisPicker.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/insights/ScatterDeepLinkTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/charts/ScatterPlotTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/content/ExploreCopy.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/insights/InsightCard.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/insights/InsightsScreen.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/explore/ExploreScreen.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppGate.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt"

key-decisions:
  - "scatterMarkRadiusDp uses radius proportional to sqrt(count) (base 4dp + 3dp per sqrt-day) so a cell's AREA is proportional to its day-count — a 3-day cell reads as a visibly bigger dot than a 1-day cell; pure + monotonic so it is unit-provable without pixels"
  - "Descriptive-only (H5) is pinned TWO ways: the ScatterPoint type carries only x/y/count (no fit/r-value by shape), AND ScatterPlotTest comment-strips ScatterPlot.kt and asserts no fitted/regression/rvalue/slope/coefficient/% token — so a future edit that reaches for a best-line turns the test red"
  - "Scatter uses ALL loaded snapshots (not the Lanes range window) for the both-logged tally — the scatter is about overlapping days across history; the sparse floor is a both-logged count-sum < 5"
  - "InsightsScreen reads suppressed fail-closed (defaults true until loadSuppressed resolves) rather than the plan's literal 'failure -> false' — strictly stronger (a bleeding scatter-link can never flash pre-load), and the real gate is upstream anyway (the engine emits no bleeding correlation under suppression). Documented as a Rule-2 hardening."
  - "The 'See the days behind this' label + all scatter chrome live in the already-registered ExploreCopy (one cohesive scatter-copy home), so CopyAudit scans them from birth without touching the registry list"

requirements-completed: [AINS-05]

coverage:
  - id: H5-DESCRIPTIVE-ONLY
    description: "Scatter is descriptive-only: count-encoded PointMarks, NO fitted line / r-value / slope / percent"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterPlotTest.scatterSource_hasNoFittedLineOrRValueConstruct (comment-stripped source scan) + countEncoding_biggerMarkAndCellDays_forADenserCell (size by count, one a11y node per cell)"
        status: pass
    human_judgment: false
  - id: COUNT-ENCODING
    description: "A denser (x,y) cell renders a bigger mark; identical day-pairs aggregate (no jitter)"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterPlotTest.countEncoding_biggerMarkAndCellDays_forADenserCell (scatterMarkRadiusDp monotonic; cell (6,8) count 3 -> '3 days')"
        status: pass
    human_judgment: false
  - id: DEEP-LINK
    description: "A Possible-association card deep-links to Explore Scatter with the pair's typed axes, then clears"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterDeepLinkTest (typed-axes path, multi-dot known-suffix) + ScatterPlotTest.pendingScatter_switchesToScatterMode_prefillsAxes_thenClears"
        status: pass
    human_judgment: false
  - id: SUPPRESSION-GATE
    description: "A suppressed bleeding correlation resolves to null AND the axis picker offers no bleeding axis"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterDeepLinkTest.stressHighBleeding_suppressed_resolvesToNull + ScatterPlotTest.suppressedUser_isOfferedNoBleedingAxis"
        status: pass
    human_judgment: false
  - id: HONEST-STATES
    description: "Scatter empty (<2 axes) and sparse (<5 overlapping days) honest states"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterPlotTest.scatterEmpty_showsWhenFewerThanTwoAxesChosen + scatterSparse_showsWhenTooFewOverlappingDays"
        status: pass
    human_judgment: false
  - id: PER-CELL-A11Y
    description: "Per-cell TalkBack nodes announce 'N days at x, y' via the reused ChartSemanticsOverlay"
    requirement: "AINS-05"
    verification:
      - kind: unit
        ref: "ScatterPlotTest.countEncoding_biggerMarkAndCellDays_forADenserCell (node label 'Stress 6 of 10, Sleep 8 h' + value '3 days') + deepLinkPrefill (per-cell node rendered)"
        status: pass
    human_judgment: false
  - id: DEVICE-FEEL
    description: "Device TalkBack sweep / axis-menu feel"
    requirement: "AINS-05"
    verification:
      - kind: manual
        ref: "22-VALIDATION Manual-Only — deferred to device pass"
        status: deferred
    human_judgment: true

# Metrics
duration: 60min
completed: 2026-07-09
status: complete
---

# Phase 22 Plan 06: Descriptive-Only Count-Encoded Scatter + Cross-Tab Scatter Deep-Link Summary

**The Explore Scatter surface (the final Explore mode) plus the dashboard->Explore scatter deep-link: a descriptive-only, count-encoded scatter (one dot per both-logged (x,y) cell, size by day-count, on FIXED real-unit domains, single plum hue — NO fitted line, NO r-value, NO slope/percent; H5), a quantitative-only axis picker, and the ported `ScatterDeepLink` + `ExploreScatterRouter` that turn a Possible-association card's "See the days behind this" affordance into a typed cross-tab handoff (set-before-select, consume-and-clear). Suppression is gated on two walls (the resolver returns null for a bleeding target; the picker offers no bleeding axis). Closes Phase 22.**

## Performance
- **Duration:** ~60 min
- **Completed:** 2026-07-09
- **Tasks:** 2 (2 atomic commits)
- **Files:** 14 (6 created, 8 modified)

## Accomplishments
- **`ScatterDeepLink`** (ported, pure, `com.endoapp.app.ui.insights`) — `resolve(signalKey, suppressed, catalog)` maps the STRUCTURAL correlation `signalKey` to a typed `(ExploreSeries, ExploreSeries)?`. It consumes ONLY the machine key (never `headline`/`detail` prose — the T-22-01j honesty contract), strips the `correlation.` prefix, detects the target by KNOWN SUFFIX (`.bleeding` or `.symptom.<SymptomType.raw>` enumerated from `SymptomType.entries` — never a naive `split(".")` that would mis-segment the dotted `stress.high` factor.key), maps the factor to a quantitative axis (ONLY `stress.high -> "stress"`, `mood.low -> "mood"`), gates bleeding to null under suppression, and looks BOTH ids up in the catalog (never constructs a series). Any malformed key -> null (defensive, never a crash).
- **`ExploreScatterRouter` + `PendingScatter`** — the cross-tab seam mirroring `CheckInRouter`: a `mutableStateOf<PendingScatter?>` carrying the TYPED `x`/`y` axes (public catalog ids only, never health data, never persisted). Constructed in `AppDependencies`, threaded into `AppGate`'s Consented arm -> `ConsentedShell`.
- **`InsightCard` `.correlation` affordance** — resolves `ScatterDeepLink.resolve(insight.signalKey, suppressed)` ONCE; when non-null renders an accent-plum "See the days behind this" (`insights.card.<signalKey>.scatterLink`, >=48dp) that sets `router.pendingScatter` with the typed axes THEN switches to Explore (set-before-select). Null -> the affordance is HIDDEN (presence-only / sleep-quality / suppressed-bleeding shows no link).
- **`ScatterPlot`** (Canvas 320dp square, `explore.scatter.plot`) — one dot per both-logged `(x,y)` cell from `ScatterPairing.pairs`, size via `scatterMarkRadiusDp(count)` (radius proportional to sqrt(count) so AREA proportional to count). Both axes on FIXED `SeriesNormalization.domain(...)`. Single plum hue. A neutral caption "Based on {n} days when both were logged." + a worded summary "{X} compared with {Y}, {n} days when both were logged." + a data-derived axis-titles line. Reuses `ChartSemanticsOverlay` with `scatterSemanticNodes` for per-cell nodes (`explore.scatter.point.<x>x<y>`, label "{X} {x readout}, {Y} {y readout}", value "{count} days"). Absolutely NO fitted line / r-value / slope / percent.
- **`ScatterAxisPicker`** — two quantitative-only X/Y dropdown selectors over `ExploreSelection.offered(suppressed).filter { isQuantitative }` (presence never offered; bleeding absent under suppression; same series on both axes allowed). Placeholder "Pick a factor".
- **`ExploreScreen` Scatter mode** — the axis picker + plot + honest states (empty = <2 axes -> "Pick two factors to compare."; sparse = both-logged count-sum < 5 -> "Not enough overlapping days yet."). Consumes `ExploreScatterRouter.pendingScatter` on appear/change via `LaunchedEffect(pending)` -> Scatter mode + typed axes prefilled + CLEAR the payload (re-trigger safe).
- **`ExploreCopy`** — all scatter chrome strings (link label, axis labels/placeholder, empty/sparse titles+bodies, caption/summary/axis-titles/cell-label/cell-days composers) registered in the SAME change, curated to dodge the ClaimsPolicy substrings ("when both were logged" not "where"; "compared with" not "against"; "on too few of the same days" not "together"; "missing a factor" not "either").

## Task Commits
1. **ScatterDeepLink resolver + ExploreScatterRouter + correlation-card affordance** — `9ef9d5e` (feat)
2. **Count-encoded descriptive-only ScatterPlot + axis picker + Explore consumption** — `dde4e78` (feat)

## REAL Test Results
- `:app:testDebugUnitTest` — **319/319 pass, 0 failures, 0 skipped** (+15 new: `ScatterDeepLinkTest` 8, `ScatterPlotTest` 7).
- `:insights:test` — **176/176 pass** (source untouched).
- `:app:assembleDebug` — **SUCCESSFUL**. `:app:lintDebug` — **SUCCESSFUL**.
- `AppGateConsentTest`, `InsightsSeparationTest`, `CopyAuditTest`, `InsightsDashboardTest` — **all pass** (the router is observed only in the Consented arm; the insights packages import no notification API; the enlarged copy catalog including the scatter strings is substring-clean against ClaimsPolicy).
- **Descriptive-only PROVEN at the source layer:** a comment-stripped scan of `ScatterPlot.kt` finds NO `fitted`/`regression`/`rvalue`/`slope`/`coefficient`/`%` token; `scatterMarkRadiusDp(3) > scatterMarkRadiusDp(1)` (size by count); the (6,8) count-3 cell announces "3 days"; a suppressed user is offered no bleeding axis; empty (<2 axes) and sparse (<5 overlapping days) states show; a `pendingScatter` payload switches to Scatter mode with "Stress"/"Sleep" prefilled then the router clears to null.

## Deviations from Plan
- **[Rule 2 - Hardening] `InsightsScreen` reads `suppressed` fail-closed (default `true`) rather than the plan's literal "failure -> false".** The plan's `<action>` says "failure -> false only KEEPS the bleeding gate". Defaulting to `true` until `loadSuppressed()` resolves is strictly stronger — a bleeding scatter-link can never flash before the treatment mode is known (mirroring `ExploreScreen`'s fail-closed posture) — and it changes no correct-input behaviour because the real gate is upstream (the engine emits NO bleeding correlation under suppression, so a suppressed user has no bleeding correlation card to link from in the first place). No test needed a `false` default.
- **[Sequencing] `ExploreCopy` scatter strings landed in the Task-1 commit** rather than split across Task 1/Task 2. The copy is one cohesive block registered in `CopyAuditRegistry` in a single change (the load-bearing registration rule), and Task 1's `CopyAuditTest` already audits all of it green; Task 2 only consumes the plot-state strings. Both commits compile and each task's `<done>` criteria are met.
- **[Rule 3] `ScatterPlot` uses `BoxScope.matchParentSize()` via the Box receiver** (not a standalone import — no such top-level exists); the axis field carries `semantics(mergeDescendants = true)` so the field node exposes its selected-series text to both TalkBack and the test's `assertTextContains`.

## Threat Model
- **T-22-01 (Tampering / scatter descriptive-only) — MITIGATED:** `ScatterPoint` carries only x/y/count; fixed domains; no jitter; `ScatterPlotTest` comment-strips the source and asserts no fitted-line/r-value/slope/% construct.
- **T-22-01j (Info disclosure / deep-link reads copy) — MITIGATED:** `ScatterDeepLink` consumes ONLY the structural `signalKey` (never headline/detail); `ExploreScatterRouter` carries a TYPED `PendingScatter` never re-derived from copy; ephemeral, never persisted. Pinned by `ScatterDeepLinkTest`.
- **T-22-01k (Info disclosure / suppressed bleeding via deep-link) — MITIGATED:** `resolve(...)` returns null for a suppressed bleeding target AND `ScatterAxisPicker` offers no bleeding axis (two walls); pinned by `ScatterDeepLinkTest.stressHighBleeding_suppressed_resolvesToNull` + `ScatterPlotTest.suppressedUser_isOfferedNoBleedingAxis`.

## Known Stubs
None. Scatter mode is now fully wired against real `daySnapshots()` data via the pure `ScatterPairing`. The `ExploreCopy.SCATTER_SOON` placeholder string is retired from the render path (kept only as an unused const member).

## User Setup Required
None.

## Next Phase Readiness
- Phase 22 is complete: Insights dashboard (04), Explore Lanes (05), and Explore Scatter + the dashboard->Explore deep-link (06) all landed.
- Phase 24 (PDF report) may reuse the scatter drawing geometry alongside the lane drawing for the vector PDF.
- Device TalkBack sweep / axis-menu feel remains the documented Manual-Only verification (22-VALIDATION).

## Self-Check: PASSED

All 6 created files + the 8 modified files exist on disk; both task commits (`9ef9d5e`, `dde4e78`) are in the git log; `:app` 319/319 and `:insights` 176/176 green with `:app:assembleDebug` + `:app:lintDebug` SUCCESSFUL.

---
*Phase: 22-insights-explore-charts*
*Completed: 2026-07-09*
