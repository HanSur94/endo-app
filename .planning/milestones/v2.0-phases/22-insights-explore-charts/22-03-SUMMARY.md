---
phase: 22-insights-explore-charts
plan: 03
subsystem: insights-charts
tags: [compose, accessibility, talkback, semantics, chart-honesty, canvas, reusable]

# Dependency graph
requires:
  - phase: 22-insights-explore-charts (Plan 02)
    provides: "TrendChart Canvas + ChartScrubSelection.resolve (gap-honest exact-day resolver) + ChartsCopy (Not logged / Severity n of 10) + TrendChartHonestyTest fixture"
  - phase: 17-insights-engine-port
    provides: "ChartScrubSelection.resolve, InsightPoint, SeriesNormalization.readout"
  - phase: 19-bloom-design-system
    provides: "BloomTheme (Robolectric+Compose harness)"
provides:
  - "ChartSemanticNode: reusable @Immutable semantics-node model (testTag + contentDescription + stateDescription + xFraction)"
  - "ChartSemanticsOverlay: bespoke invisible focusable per-point semantics stack — ≥48dp, chronological traversalIndex, optional worded summary node; no pointerInput (never disturbs the visual scrub)"
  - "trendSemanticNodes: gap-honest per-day node builder resolving through the SAME ChartScrubSelection.resolve as the visual scrub (branch on point==null, never value truthiness)"
  - "TrendChart wired with the additive per-point TalkBack overlay"
affects: [22-05 explore lanes/scatter (reuse overlay + trendSemanticNodes), 22-06 scatter (reuse overlay with cell nodes)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Bespoke per-data-point semantics overlay OVER an invisible Canvas — one focusable node per scrubbable day, resolved gap-honestly through the SAME resolver as the visual scrub (Canvas is invisible to TalkBack; Android has no AXChartDescriptor/Audio-Graph equivalent)"
    - "Gap-vs-value decision lives ONLY in the resolver — the overlay branches on `resolution.point == null`, NEVER on value truthiness, so a logged severity-0 announces a real 0"
    - "Chronological focus order pinned with a forced `traversalIndex` inside an `isTraversalGroup` (geometry-independent), not left to layout overlap"
    - "Each node is a `mergeDescendants` semantics boundary carrying its own testTag — a container testTag can't mask the per-point ids"
    - "Reusable semantics core (ChartSemanticNode + ChartSemanticsOverlay) decoupled from the node-source builder so lanes (quant → trendSemanticNodes) and scatter (cells → ad-hoc ChartSemanticNode) reuse the SAME honesty-aware overlay"

key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/ChartSemanticsOverlay.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/charts/ChartSemanticsTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/TrendChart.kt"

key-decisions:
  - "The overlay emits ONE node for EVERY calendar day in the domain (windowStart…asOf), not only logged days — so every scrubbable day is TalkBack-reachable in date order and the gap-vs-0 honesty is provable per-day (a gap day gets a `.notLogged` node, a logged day a real-readout node)"
  - "TrendChart passes `summary = null` to the overlay: the VISIBLE summary Text above the chart (Plan 02, testTag `<root>.chart.summary`) already carries the one-line meaning to TalkBack — duplicating it in the overlay would double-announce. The overlay's summary-node capability is retained for charts WITHOUT a visible summary (Plan 05 event lanes: `{name} — logged on {n} days`)"
  - "Overlay nodes have NO pointerInput, so despite being drawn on top of the Canvas they never intercept the visual scrub gesture (Compose hit-testing only targets pointer-input nodes) — the overlay is purely additive"
  - "Chronological focus order is asserted geometrically (node x strictly increases with epochDay) AND enforced semantically via traversalIndex — the two are consistent"

requirements-completed: [AINS-07, AINS-03]

coverage:
  - id: A11Y-PERPOINT
    description: "Every rendered/scrubbable data point has a focusable TalkBack node, stepped in chronological date order"
    requirement: "AINS-07"
    verification:
      - kind: unit
        ref: "ChartSemanticsTest.oneFocusableNode_perScrubbableDay_reachableByTestTag (11 nodes, each by <chartTag>.point.<epochDay> testTag) + focusOrder_isChronological_leftToRightByEpochDay"
        status: pass
    human_judgment: false
  - id: A11Y-GAP-HONEST
    description: "A gap node announces 'Not logged'; a logged severity-0 node announces a real 0 — through the SAME resolver as the visual scrub"
    requirement: "AINS-03"
    verification:
      - kind: unit
        ref: "ChartSemanticsTest.gapDay_announcesNotLogged_neverAZero + loggedSeverityZeroDay_announcesARealZero_notAGap"
        status: pass
    human_judgment: false
  - id: A11Y-SUMMARY
    description: "Each chart carries a one-line worded summary node so the chart is never the sole carrier of meaning"
    requirement: "AINS-07"
    verification:
      - kind: unit
        ref: "ChartSemanticsTest.chartCarriesAWordedSummaryNode"
        status: pass
    human_judgment: false
  - id: A11Y-48DP
    description: "Interactive scrub/reader targets are ≥48dp"
    requirement: "AINS-07"
    verification:
      - kind: unit
        ref: "ChartSemanticsTest.everyReaderNode_isAtLeast48dpFocusTarget"
        status: pass
    human_judgment: false
  - id: A11Y-DEVICE-SWEEP
    description: "Device TalkBack sweep of every chart data point (real screen-reader feel)"
    requirement: "AINS-07"
    verification:
      - kind: manual
        ref: "22-VALIDATION Manual-Only — device TalkBack sweep; deferred to device pass"
        status: deferred
    human_judgment: true

# Metrics
duration: 28min
completed: 2026-07-09
status: complete
---

# Phase 22 Plan 03: Bespoke Per-Data-Point TalkBack Semantics Overlay Summary

**The HIGH-complexity, first-class accessibility deliverable — a hand-built stack of focusable, INVISIBLE semantics nodes laid over the TalkBack-invisible Compose `Canvas`, one per scrubbable day, each reading its text from the SAME gap-honest `ChartScrubSelection.resolve` the visual scrub uses: a gap announces "Not logged", a logged severity-0 announces a real "Severity 0 of 10", the gap-vs-0 question answered ONCE — pinned by a semantics-tree test and factored so Plan 05 lanes + Plan 06 scatter reuse the identical overlay.**

## Performance
- **Duration:** ~28 min
- **Completed:** 2026-07-09
- **Tasks:** 2
- **Files:** 3 (2 created, 1 modified)

## Accomplishments
- **`ChartSemanticNode`** — a presentation-agnostic `@Immutable` node model (`testTag`, `contentDescription`, `stateDescription`, `xFraction`) so trend, quantitative lanes, event lanes, and scatter cells all produce the SAME node type for the SAME overlay.
- **`trendSemanticNodes`** — the gap-honest per-day builder. Emits ONE node for EVERY calendar day in `[windowStart, asOf]` (every scrubbable day is TalkBack-reachable in date order). The gap-vs-value decision lives ONLY in the resolver: the branch is on `resolver(day, points)?.point == null` — NEVER on value truthiness — so a logged severity-0 (a real point, value 0) announces "Severity 0 of 10", a gap announces "Not logged". Generic on `describeValue` / `notLoggedText` / `dateLabel` / `resolver` (default `ChartScrubSelection::resolve`) so Plan-05 quantitative lanes reuse it verbatim with a different domain/readout.
- **`ChartSemanticsOverlay`** — the reusable, invisible overlay composable. Draws nothing visible; emits a transparent focusable node per `ChartSemanticNode`, each `≥48dp` (`requiredSize(48.dp)`), positioned along the x-axis via `xFraction`, carrying its `contentDescription` + `stateDescription`, shielded as a `mergeDescendants` boundary with its own `testTag`. Focus order is pinned chronological with a forced `traversalIndex` inside an `isTraversalGroup` (geometry-independent). Nodes have NO `pointerInput`, so despite drawing on top of the Canvas they never intercept the visual scrub gesture. Optional worded summary node for charts without a visible summary.
- **`TrendChart`** wired additively with `ChartSemanticsOverlay` (`Modifier.matchParentSize()` over the Canvas), point testTags `<root>.chart.point.<epochDay>` / `.notLogged`, `summary = null` (the visible summary Text above already carries the meaning — no double announcement). The Plan-02 visual scrub + tooltip are untouched.

## Task Commits
1. **Task 1: reusable ChartSemanticsOverlay + trendSemanticNodes + wire onto TrendChart** — `1f71c53` (feat)
2. **Task 2: ChartSemanticsTest semantics-tree pin** — `07dbe91` (test)

## REAL Test Results
- `:app:assembleDebug` — **SUCCESSFUL**. `:app:lintDebug` — **SUCCESSFUL**.
- `:app:testDebugUnitTest` — **276/276 pass, 0 failures, 0 skipped** (270 Plan-02 baseline + 6 new `ChartSemanticsTest`).
- `:insights:test` — **176/176 pass** (source untouched).
- `ChartSemanticsTest` (6/6) PROVEN on the Robolectric+Compose semantics tree:
  - `oneFocusableNode_perScrubbableDay_reachableByTestTag` — exactly 11 nodes (one per domain day), each reachable by its `<chartTag>.point.<epochDay>` testTag, un-masked by any container.
  - `focusOrder_isChronological_leftToRightByEpochDay` — each node's `positionInRoot.x` strictly increases with `epochDay`.
  - `gapDay_announcesNotLogged_neverAZero` — the Jan-6 gap node's `stateDescription` == "Not logged"; the non-`.notLogged` variant does NOT exist.
  - `loggedSeverityZeroDay_announcesARealZero_notAGap` — the Jan-2 logged-0 node's `stateDescription` == "Severity 0 of 10"; its `.notLogged` variant is absent (absence ≠ zero, one tested place).
  - `chartCarriesAWordedSummaryNode` — the `<chartTag>.summary` node exists and is worded.
  - `everyReaderNode_isAtLeast48dpFocusTarget` — logged, gap, and severity-0 nodes all ≥48dp wide and tall.

## Reusability confirmation (Plan 05 lanes + Plan 06 scatter)
`ChartSemanticNode` + `ChartSemanticsOverlay` hold no chart-type coupling — they consume a `List<ChartSemanticNode>` (already resolved) + an optional summary. Plan 05 quantitative lanes call the SAME `trendSemanticNodes` with each lane's domain + `SeriesNormalization.readout`-based `describeValue`; Plan 05 event lanes pass a summary node (`{name} — logged on {n} days`) and per-tick nodes with a "Logged" stateDescription; Plan 06 scatter builds `ChartSemanticNode`s per `(x, y)` cell (`explore.scatter.point.<x>x<y>`, value "{count} days") and hands them to the identical overlay. The honesty contract (gap-vs-0 in one resolver) extends into every chart's semantics tree without re-solving it.

## Deviations from Plan
None — the plan executed as written. Two implementation choices worth noting (both within the plan's contract):
1. **`summary = null` on TrendChart's overlay:** TrendChart already renders a visible, TalkBack-readable summary Text above the chart (Plan 02), so the overlay's summary node is left unemitted to avoid a double announcement; the Task-2 "summary node present" assertion targets that existing `<root>.chart.summary` node. The overlay's summary-node capability is retained for Plan-05 charts that lack a visible summary.
2. **Node-per-domain-day (not node-per-logged-day):** emitting a node for every scrubbable day (logged + gap) is what makes the gap-day `.notLogged` node exist and every day TalkBack-reachable in date order — the direct reading of "one focusable node per rendered/scrubbable day" in the plan + UI-SPEC.

## Threat Model
T-22-01 (Tampering / semantics honesty) — MITIGATED: the overlay reads the SAME `ChartScrubSelection.resolve` as the visual scrub; there is no separate gap-vs-0 branch to drift (the only branch is on the resolver's `point == null`). Pinned by `ChartSemanticsTest.gapDay_announcesNotLogged_neverAZero` + `loggedSeverityZeroDay_announcesARealZero_notAGap`.

## Known Stubs
None. The overlay is wired against real `InsightPoint` data and the live `ChartScrubSelection` resolver.

## User Setup Required
None.

## Next Phase Readiness
- Plan 04 embeds `TrendChart` (now visual + accessible) in the `.trend` card.
- Plan 05 (lanes) reuses `ChartSemanticsOverlay` + `trendSemanticNodes` for quantitative lanes and a summary node for event lanes.
- Plan 06 (scatter) reuses `ChartSemanticsOverlay` with per-cell `ChartSemanticNode`s.
- Device TalkBack sweep remains the documented Manual-Only verification (22-VALIDATION).

## Self-Check: PASSED

Both created files (`ChartSemanticsOverlay.kt`, `ChartSemanticsTest.kt`) + the modified `TrendChart.kt` and this SUMMARY exist on disk; both task commits (`1f71c53`, `07dbe91`) are in the git log.

---
*Phase: 22-insights-explore-charts*
*Completed: 2026-07-09*
