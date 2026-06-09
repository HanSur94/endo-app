---
phase: 11-personalized-recommendations
plan: 03
subsystem: ui
tags: [swift, swiftui, swift-testing, xcuitest, insights, recommendations, honesty-gate]

# Dependency graph
requires:
  - phase: 11-01
    provides: "CorrelationEngine.scoredCandidates(...) direction seam (signed-effect RecommendationDirection) + the -uitest-seed-recommendations seed + the RED testRecommendation_rendersAfterCorrelation E2E"
  - phase: 11-02
    provides: "RecommendationsEngine.recommendations(...) producer (compose-never-re-derive, dominant-per-symptom collapse, suppressed guard) + audited RecommendationsCopy + ForbiddenTerms imperative scan"
provides:
  - "RecommendationsEngine composed into the LOCKED InsightsEngine.insights(...) behind the single HonestyGate (no separate gating path, D-08)"
  - "HonestyGate.minSample(.recommendation) lowered 14->10 (== minSample(.correlation)) — re-framed n=10-13 correlations no longer silently dropped (Pitfall 3)"
  - "The omit-when-empty .recommendation dashboard section in InsightsView after Possible associations (D-06)"
  - "INST-05 delivered end-to-end: a data-driven, non-prescriptive recommendation card renders, is dismissable, and is honestly absent when unsupported"
affects: [phase-12-flare-early-warning, end-of-phase-human-verify]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One-more-producer composition: a new candidate term is concatenated into InsightsEngine and inherits the single HonestyGate — the LOCKED public signature (D-16) never changes"
    - "Per-kind minSample table: a new gated kind tunes its floor by editing one switch case, never the gate's structure"
    - "Omit-when-empty section: a new dashboard kind is one section(title:identifier:kind:) call; honest absence is inherited free"

key-files:
  created: []
  modified:
    - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
    - EndoInsights/Sources/EndoInsights/HonestyGate.swift
    - EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift
    - EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift
    - Sources/EndoApp/Features/Insights/InsightsView.swift
    - Tests/EndoAppUITests/InsightsDashboardUITests.swift

key-decisions:
  - "minSample(.recommendation) == minSample(.correlation) == 10: a recommendation re-frames an already-gated correlation (sampleSize == pairedDays >= 10), so a 14 floor would silently drop valid n=10-13 recommendations"
  - "Recommendations are concatenated AFTER CorrelationEngine (they derive from associations) — matching the dashboard section order (D-06)"
  - "Scroll the bottom-most Recommendations section into view in the E2E (lazy List realization), rather than re-ordering the dashboard or adding a non-lazy container"

patterns-established:
  - "Single-gate composition: new insight producers route through HonestyGate.apply, never a bespoke filter — the ethics stay in one choke-point"
  - "E2E robustness for below-the-fold lazy List rows: bounded swipe-up until the target section's accessibility id is realized"

requirements-completed: [INST-05]

# Metrics
duration: ~12min
completed: 2026-06-10
---

# Phase 11 Plan 03: Personalized Recommendations Integration/Payoff Summary

**Composed the RecommendationsEngine into the LOCKED InsightsEngine behind the single HonestyGate, lowered minSample(.recommendation) 14->10, and shipped the omit-when-empty Recommendations dashboard section — turning the recommendation E2E GREEN and delivering INST-05 end-to-end.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-10T00:28:00Z
- **Completed:** 2026-06-10T00:40:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- `RecommendationsEngine.recommendations(...)` composed as ONE more candidate term in `InsightsEngine.insights(...)`, after `CorrelationEngine`, routed through the unchanged single `HonestyGate` (no separate gating path, D-08; public signature stays LOCKED, D-16).
- `HonestyGate.minSample(.recommendation)` lowered 14 -> 10 (== `minSample(.correlation)`), so a recommendation re-framed from an n=10-13 correlation is no longer silently dropped (Pitfall 3); locked by two new gate tests.
- The `.recommendation` section added to `InsightsView` after the `.correlation` (Possible associations) section, identifier `insights.section.recommendations`, via the existing omit-when-empty `section(title:identifier:kind:)` helper — reusing the kind-agnostic `InsightCardView` and the existing dismiss/coordinator pipeline. Zero new view/token/dismiss/cache machinery.
- `testRecommendation_rendersAfterCorrelation` (RED since Plan 01) is now GREEN; the dashboard screenshot is saved to `/tmp/e2e/insights-06-recommendations.png` for the end-of-phase human-verify.
- INST-05 delivered: a data-driven, non-prescriptive recommendation card renders, is dismissable, and is provably absent (section omitted) for suppressed/sparse users.

## Task Commits

Each task was committed atomically:

1. **Task 1: Compose RecommendationsEngine into InsightsEngine + lower minSample(.recommendation) to 10** - `c5fcebf` (feat)
2. **Task 2: Add the Recommendations dashboard section + make the recommendation E2E GREEN** - `814b947` (feat)

**Plan metadata:** (this docs commit)

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` - Appended `RecommendationsEngine.recommendations(...)` to the candidate concatenation, after `CorrelationEngine`, through the unchanged single gate.
- `EndoInsights/Sources/EndoInsights/HonestyGate.swift` - `minSample(.recommendation)` 14 -> 10; doc-comment updated from "Phase 11 placeholder" to the LOCKED inherits-correlation-floor rationale.
- `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` - Added composition (`.recommendation` surfaces over the aggravating fixture), suppressed+flood withholding, and recommendation dismissal-idempotence tests.
- `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` - Added `minSampleRecommendation_equalsCorrelationFloor` (==10) and `recommendationFromN10Correlation_survivesGate`.
- `Sources/EndoApp/Features/Insights/InsightsView.swift` - Added the 4th `section(...)` call (`.recommendation`) after `.correlation`; updated the section-order doc comment.
- `Tests/EndoAppUITests/InsightsDashboardUITests.swift` - Scroll the bottom-most Recommendations section into view before asserting its existence (lazy List realization).

## Decisions Made
- **minSample(.recommendation) == 10:** a recommendation re-frames an already-gated correlation, whose `sampleSize == pairedDays >= 10`; a 14 floor would drop valid n=10-13 recommendations (Pitfall 3). Equality with the correlation floor is now locked by a test.
- **Composition order:** recommendations are concatenated after `CorrelationEngine` (they derive from associations), matching the dashboard section order (D-06).
- **E2E scroll over re-layout:** the Recommendations section is the last, below-the-fold section on a populated dashboard; rather than re-order the dashboard or force a non-lazy container, the E2E scrolls the section into view (the model contains it the moment the recompute lands).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Scroll the below-the-fold Recommendations section into view in the E2E**
- **Found during:** Task 2 (Add the Recommendations dashboard section + make the recommendation E2E GREEN)
- **Issue:** With Trends + Possible associations above it, the Recommendations section renders below the fold on a populated dashboard. SwiftUI's `List` lazily realizes off-screen rows, so the section's `insights.section.recommendations` accessibility id was never in the tree and `waitForExistence` timed out — even though the engine correctly produced the recommendation (verified by a throwaway diagnostic test over the exact seed shape: `corr=2 rec=1`, `recommendation.food.dairy.symptom.pelvicPain`).
- **Fix:** Added a bounded swipe-up loop on the dashboard before asserting, so the lazy cell is realized. No production behavior change — the section exists in the model the moment the recompute lands; the swipe only brings its cell into the accessibility tree.
- **Files modified:** Tests/EndoAppUITests/InsightsDashboardUITests.swift
- **Verification:** `testRecommendation_rendersAfterCorrelation` GREEN; full `InsightsDashboardUITests` 5/5 GREEN; screenshot saved.
- **Committed in:** 814b947 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The auto-fix was confined to the test harness (lazy-List realization); production code matched the plan exactly (one composition line, one minSample edit, one section call). No scope creep.

## Issues Encountered
- The recommendation E2E initially failed at the section-existence assertion. A diagnostic test replicating the seed's exact alternating-parity 22-day shape confirmed the engine and view were correct (a recommendation was produced and added to `coordinator.insights`), isolating the cause to the lazy off-screen List row — resolved by the E2E scroll (see Deviations). The diagnostic test was removed after use.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 11 (Personalized Recommendations) COMPLETE (3/3 plans); INST-05 marked complete in REQUIREMENTS.md.
- Phase 12 (Flare Early-Warning, INST-06) is next — it adds a `.flare` producer behind the SAME single `HonestyGate` (its `minSample(.flare) == 21` placeholder case is already in the table), following the one-more-producer composition pattern this plan exercised for `.recommendation`.
- Deferred (end-of-phase human-verify): on a populated multi-factor account, confirm each Recommendations card reads as a discussion prompt with a plain-language confidence + "based on your own data" framing (11-VALIDATION Manual-Only); dashboard screenshot at `/tmp/e2e/insights-06-recommendations.png`.
- Known pre-existing flake (NOT introduced here): `InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute` (debounce timing) — logged in `deferred-items.md`.

## Self-Check: PASSED

- Files: InsightsEngine.swift, HonestyGate.swift, InsightsView.swift, 11-03-SUMMARY.md all present.
- Commits: c5fcebf (Task 1), 814b947 (Task 2) both in history.

---
*Phase: 11-personalized-recommendations*
*Completed: 2026-06-10*
