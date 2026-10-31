---
phase: 22-insights-explore-charts
verified: 2026-07-09T00:00:00Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
requirements_verified: [AINS-01, AINS-02, AINS-03, AINS-04, AINS-05, AINS-06, AINS-07]
test_results:
  insights_test: "176/176 pass, 0 failures, 0 skipped"
  app_testDebugUnitTest: "319/319 pass, 0 failures, 0 skipped"
  app_assembleDebug: "SUCCESSFUL"
  app_lintDebug: "SUCCESSFUL"
device_gated_manual: # documented Manual-Only verifications (22-VALIDATION §Manual-Only) — NOT gaps
  - "Chart pixel legibility + colorblind-safe marks + scrub haptic feel (AINS-03/04)"
  - "TalkBack device sweep of every chart data point + dashboard cards (AINS-07)"
  - "Recompute smoothness / no-jank on a large real dataset + Layout-Inspector flat-recomposition (AINS-01)"
---

# Phase 22: Insights Dashboard, Explore & Charts — Verification Report

**Phase Goal:** The insights dashboard and Explore surfaces give honest, readable, TalkBack-accessible views of everything logged — under the locked chart-honesty rules; flare surfaces in-app only; the insights package is notification-free.
**Verified:** 2026-07-09
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

Verification RAN the full suite (`:insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks`, JDK 21 / Android SDK 36). Result: **BUILD SUCCESSFUL** — `:app` **319/319** JVM tests, `:insights` **176/176**, 0 failures, 0 skipped; assembleDebug + lintDebug green. Counts match the SUMMARY claims exactly. Honesty-critical sources were read directly (not trusted from SUMMARY) and the structural guard tests were confirmed non-vacuous and red-capable.

### Observable Truths

| # | Truth (ROADMAP Success Criteria) | Status | Evidence |
|---|----------------------------------|--------|----------|
| 1 | Gated dashboard: 5 sections (trends, cycle-phase, possible-associations, recommendations, flare) with plain-language confidence, honest empty states, pinned not-medical-advice disclaimer, non-destructive dismiss | ✓ VERIFIED | `InsightsDashboardTest` (10) proves flare-FIRST order via pure `insightSections`, `insights.empty` on total-empty, suppressed cycle-phase provably ABSENT, sparse card with no pill, pinned disclaimer, dismiss via both visual affordance + TalkBack action routing an append-only `DismissedInsight` (deletes no logged data). `ConfidencePillTest` (2): word + shape icon, no digit/%/p=/r=. `InsightsCoordinatorTest` (6): off-main recompute, HIT skips engine, MISS recomputes+persists, debounce N→1, dismiss excluded next recompute. Source `ConfidencePill.kt` renders no numeric. All green. |
| 2 | Flare surfaces in-app ONLY — structural test scanning flare sources for notification/alarm APIs stays green; no alarm visuals | ✓ VERIFIED | `InsightsSeparationTest` (3): import-scan + comment-stripped `POST_NOTIFICATIONS` scan over BOTH `com.endoapp.app.insights` + `com.endoapp.app.ui.insights`; non-vacuous guard asserts coordinator+screen+card scanned; red-capable. `FlareNoAlarmVisualTest` (3): comment-stripped scan bans `roseDeep`/raw-red/warning/exclamation/badge; non-vacuous. Flare uses the IDENTICAL kind-agnostic `InsightCard`. Reminders package remains sole notification owner. All green. |
| 3 | Scrub/tap trend charts read exact day+value; gap reads "not logged"; logged severity-0 reads a real zero | ✓ VERIFIED | `ChartSeriesGrouping.runs` (source read): new run when `prev.date.plusDays(1) != next.date`, never fabricates value=0, keeps logged-0. `ChartSeriesGroupingTest` (11) + `TrendChartHonestyTest` (5): gap→line break/`Not logged`, logged-0→real `Severity 0 of 10` point with no gap marker. Source `TrendChart.kt`: the ONLY raw-day→value path is `ChartScrubSelection.resolve` (no minBy/nearest/interpolation); `@Immutable TrendChartInput`; draw-phase scrub. All green. |
| 4 | Explore combines series as aligned per-scale overlay lanes + event-marker lanes on a shared time axis, and reaches the descriptive-only count-encoded scatter (no fitted line/r-value) from a possible-association card | ✓ VERIFIED | `LaneHonestyTest` (4): sleep lane 0..24 vs stress lane 0..10 on independent fixed domains, real-unit readouts "8 h" vs "8 of 10" (a normalized axis could not), event lane = occurrence ticks + summary (no 0/1 line), one shared scrub tooltip. `ExploreLanesTest` + `ScatterPlotTest` (7) + `ScatterDeepLinkTest` (8): source `ScatterDeepLink.resolve` consumes only the structural signalKey → typed axes, `InsightCard` renders "See the days behind this" (hidden when null), `ExploreScatterRouter` threaded into AppGate Consented arm, consume-and-clear proven. All green. |
| 5 | Chart-honesty tests pin no-dual-y-axis, no-silent-normalization, gaps-stay-gaps on the data-prep layer, and every rendered data point is TalkBack-readable via overlay semantics | ✓ VERIFIED | Data-prep honesty on pure `ChartSeriesGrouping` (no Compose/Android import). No-dual-y/no-normalization pinned by `LaneHonestyTest` (distinct real-unit readouts) + `quantLaneDomain`. `ScatterPlotTest.scatterSource_hasNoFittedLineOrRValueConstruct` scans for fitted/regression/rvalue/slope/coefficient/%. `ChartSemanticsTest`: one focusable node per scrubbable day via `trendSemanticNodes` through the SAME resolver (gap→`Not logged`, logged-0→real 0), chronological `traversalIndex`, ≥48dp, reused across trend/lanes/scatter. All green. |

**Score:** 5/5 truths verified (0 present, behavior-unverified). Every behavior-dependent truth (off-main recompute, cache HIT/MISS transition, dismiss-exclusion, scrub day-change, deep-link consume-and-clear) is exercised by a passing named behavioral test — none rest on symbol presence alone.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `insights/InsightsCoordinator.kt` + `InsightFingerprint.kt` | Off-main recompute + fingerprint cache + non-destructive dismiss | ✓ VERIFIED | Wired in `AppDependencies`, threaded to Consented shell; `InsightsCoordinatorTest` 6 green |
| `ui/charts/ChartSeriesGrouping.kt` | Pure gap-honest run-segmentation | ✓ VERIFIED | No Compose/Android import; 11 tests green |
| `ui/charts/ChartPrimitives.kt` | Reusable DrawScope drawing (Phase 24 PDF reuse) | ✓ VERIFIED | `object` of DrawScope member-extensions + pure `ChartCoordinateMapper`, no Compose-runtime state — callable `with(ChartPrimitives){}` from any bare DrawScope |
| `ui/charts/TrendChart.kt` | Compose Canvas, @Immutable, resolver-only scrub | ✓ VERIFIED | Source confirms single resolver path; honesty test green |
| `ui/charts/ChartSemanticsOverlay.kt` | Reusable per-point TalkBack overlay | ✓ VERIFIED | `trendSemanticNodes` branches on `point == null` (never value truthiness); reused by lanes + scatter |
| `ui/insights/InsightsScreen.kt` + `InsightCard.kt` + `ConfidencePill.kt` | Gated dashboard, kind-agnostic card, plain-language pill | ✓ VERIFIED | 5 sections flare-first, no numeric confidence, scatter affordance |
| `ui/explore/ExploreScreen.kt` + `SeriesPicker.kt` + `ExploreSelection.kt` | Lanes: suppression-filtered picker, per-scale lanes, one scrub | ✓ VERIFIED | Separate fixed domains, ≤4 cap, suppression hides bleeding |
| `ui/charts/ExploreLaneChart.kt` + `EventLaneChart.kt` | Per-scale quant lane + occurrence-tick event lane | ✓ VERIFIED | Event lane structurally cannot draw a 0/1 line |
| `ui/charts/ScatterPlot.kt` + `ui/explore/ScatterAxisPicker.kt` | Descriptive-only count-encoded scatter | ✓ VERIFIED | Source scan finds no fitted/rvalue/slope/%; size by sqrt(count) |
| `ui/insights/ScatterDeepLink.kt` + `ExploreScatterRouter.kt` | Cross-tab handoff carrying typed axes | ✓ VERIFIED | Consumes only signalKey; ephemeral typed PendingScatter; consume-and-clear |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| InsightsCoordinator | EndoRepository.daySnapshots → InsightsEngine → CachedInsight replace-all | fingerprint-gated recompute | ✓ WIRED (`InsightsCoordinatorTest`) |
| coordinator.state (StateFlow) | InsightsScreen dashboard | AppGate Consented → ConsentedShell | ✓ WIRED (`AppGate.kt` threads `insightsCoordinator`) |
| List<InsightPoint> | per-run Path (line break at gaps) → Canvas | ChartSeriesGrouping.runs | ✓ WIRED |
| raw scrub day | tooltip readout (gap='Not logged' / logged-0='Severity 0 of 10') | ChartScrubSelection.resolve | ✓ WIRED (only value path) |
| correlation Insight.signalKey | typed (x,y) axes → ExploreScatterRouter → Explore Scatter prefilled | ScatterDeepLink.resolve | ✓ WIRED (`ScatterDeepLinkTest` + `ScatterPlotTest.pendingScatter…`) |
| rendered day | semantics contentDescription/stateDescription | same resolver as visual scrub | ✓ WIRED (`ChartSemanticsTest`) |

### Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| AINS-01 | 22-01, 22-04 | ✓ SATISFIED | Coordinator (off-main + fingerprint + dismiss) + dashboard 5 sections/pill/states/disclaimer |
| AINS-02 | 22-04 | ✓ SATISFIED | InsightsSeparationTest + FlareNoAlarmVisualTest (both non-vacuous, red-capable) |
| AINS-03 | 22-02, 22-03 | ✓ SATISFIED | Exact-day scrub via resolver; gap="Not logged"; logged-0=real 0 |
| AINS-04 | 22-05 | ✓ SATISFIED | Per-scale lanes (0..24 vs 0..10), event lanes, one shared scrub |
| AINS-05 | 22-06 | ✓ SATISFIED | Descriptive-only count-encoded scatter reached from association card |
| AINS-06 | 22-02 | ✓ SATISFIED | no-dual-y / no-normalization / gaps-stay-gaps on pure data-prep + render |
| AINS-07 | 22-03 | ✓ SATISFIED | Per-data-point TalkBack overlay from the gap-honest resolver, reused across all charts |

No orphaned requirements — all 7 AINS ids mapped to this phase appear in plan `requirements` fields and are satisfied.

### Anti-Patterns Found

None blocking. No unreferenced TBD/FIXME/XXX debt markers in phase-modified files. The `ExploreCopy.SCATTER_SOON` placeholder const is retired from the render path (unused member only) per 22-06 SUMMARY — not on any live path. Structural scans are non-vacuous by explicit guard assertions.

### Scope / Isolation

- iOS tree **UNMODIFIED**: `git diff d7088d5^..HEAD` shows 0 files outside `android/` + `.planning/` (no `Sources/`, `EndoInsights/`, `project.yml` touched).
- `:insights` module **no regression** (176/176, source untouched); `VersionCatalogAuditTests` + `BoundaryLintTests` present and green.
- `ChartPrimitives` confirmed reusable off a plain DrawScope (Phase 24 PDF prerequisite) — no `remember`/snapshot state/`@Composable`.

### Device-Gated Manual Verifications (documented, NOT gaps)

Per 22-VALIDATION §Manual-Only, three experiential items are device-gated and cannot be JVM-automated. They are documented manual verifications, not verification gaps — every JVM-automatable behavior is green:

1. Chart pixel legibility + colorblind-safe marks + scrub haptic feel (AINS-03/04)
2. TalkBack device sweep of every chart data point + dashboard cards (AINS-07) — automated per-point semantics presence + gap-honest labels are already proven on the JVM; only the live screen-reader UX is device-gated
3. Recompute smoothness / no-jank on a large real dataset + Layout-Inspector flat-recomposition (AINS-01)

### Gaps Summary

None. All 5 ROADMAP success criteria are observably true in the codebase, all 7 requirements satisfied, all key links wired, structural honesty guards non-vacuous and red-capable, full suite green, iOS tree untouched.

---

_Verified: 2026-07-09_
_Verifier: Claude (gsd-verifier)_
