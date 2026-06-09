---
phase: 11-personalized-recommendations
plan: 02
subsystem: insights
tags: [recommendations, correlation, honesty, copy-audit, tdd, direction-aware, foundation-only]

requires:
  - phase: 11-01
    provides: "CorrelationEngine.scoredCandidates typed direction seam (CorrelationFinding + RecommendationDirection), RED RecommendationsEngineTests + beneficial/aggravating fixtures, ClaimsPolicy.forbiddenImperatives + the CopyAuditTests imperative scan, the RED placeholder shim to delete"
  - phase: 07
    provides: "CorrelationEngine gated/sorted/top-N survivor pipeline the recommendations re-frame"
  - phase: 06
    provides: "HonestyGate single choke-point + ForbiddenTerms causal scan + Insight.derived contract"
provides:
  - "EndoInsights.RecommendationsEngine.recommendations(from:asOf:calendar:) -> top-N (<=3) .recommendation [Insight] (Foundation-only producer, composes scoredCandidates, never re-derives)"
  - "Package-internal RecommendationCopy template mirror (byte-identical to the app RecommendationsCopy)"
  - "App-side audited RecommendationsCopy (direction-aware non-prescriptive templates + auditableStrings)"
  - "ForbiddenTerms.imperative + violation(in:) now scans causal AND imperative (gate catches instructions)"
  - "RecommendationsEngineTests GREEN + HonestyGateTests extended (recommendation streams + direct engine zero-forbidden scan)"
affects:
  - "Plan 11-03 (compose RecommendationsEngine into InsightsEngine, lower HonestyGate.minSample(.recommendation) 14->10, add the Recommendations dashboard section, make the E2E GREEN)"

tech-stack:
  added: []
  patterns:
    - "Compose-never-re-derive: the recommendation producer consumes typed CorrelationEngine.scoredCandidates survivors and runs NO statistics (D-01)"
    - "App<->package copy mirror (mirrors ClaimsPolicy<->ForbiddenTerms): curated direction-aware templates duplicated byte-identical in the app (audited surface) + package (gate-scanned), since the package cannot import the app (SC1)"
    - "One-recommendation-per-symptom collapse: complementary correlation mirrors (beneficial reading + its aggravating mirror) reduce to a single recommendation framed by the dominant direction (tie -> aggravating, the cautionary default)"
    - "Stable window-independent recommendation signalKey recommendation.<correlation-stem> (.allTime) so dismissal survives recompute (D-07)"

key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift"
    - "Sources/EndoApp/Features/Insights/RecommendationsCopy.swift"
    - ".planning/phases/11-personalized-recommendations/11-02-SUMMARY.md"
  modified:
    - "EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift"
    - "Tests/EndoAppTests/CopyAuditTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift"

key-decisions:
  - "Recommendations collapse to ONE per symptom target, framed by the user's DOMINANT direction (more supporting findings wins; tie -> aggravating) — needed to satisfy the RED count==1 / direction-flip contract over complementary 2-group correlation mirrors (D-02 avoid-overwhelm)"
  - "A suppressed user yields ZERO recommendations via an explicit suppressed guard — recommendations are MORE conservative than raw correlations (which only suppress the cycle-phase factor) (D-08/SC3)"
  - "The package owns its own RecommendationCopy template mirror (gate-scanned); the app RecommendationsCopy is the audited surface; the two are byte-identical (SC1 — package cannot import app)"
  - "ForbiddenTerms.violation now scans causal AND imperative so the HonestyGate catches an instruction in any emitted recommendation"

patterns-established:
  - "Direction-aware non-prescriptive recommendation copy: beneficial -> 'You tend to feel better on days with {factor}'; aggravating -> 'Your {symptom} tends to be more intense on days with {factor} — something you might explore' (lexically disjoint, substring-trap-clean)"
  - "Dominant-direction per-symptom dedup for re-framing complementary correlation survivors"

requirements-completed: [INST-05]

duration: ~40min
completed: 2026-06-10
---

# Phase 11 Plan 02: RecommendationsEngine + RecommendationsCopy Summary

**A Foundation-only RecommendationsEngine that re-frames the typed CorrelationEngine survivors into top-N (<=3) non-prescriptive, direction-aware `.recommendation` Insights — one per symptom framed by the dominant direction, zero for suppressed/sparse users — with a byte-identical app/package copy mirror and a package ForbiddenTerms scan that now catches imperatives.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-06-09T23:48:00Z
- **Completed:** 2026-06-10T00:05:00Z
- **Tasks:** 2 (both TDD)
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- `RecommendationsEngine.recommendations(from:asOf:calendar:)` — a Foundation-only `enum` producer that consumes `CorrelationEngine.scoredCandidates` typed survivors (compose, never re-derive, D-01) and re-frames them into top-N (<=3) `.recommendation` Insights via `Insight.derived`: stable window-independent `recommendation.<correlation-stem>` signalKey, `.allTime` window, inherited confidence + sampleSize, empty points (words-only card).
- Direction-aware, non-prescriptive copy: beneficial reads "you tend to feel better…"; aggravating reads a descriptive "…more intense… something you might explore" prompt — emitted ONLY from the curated `RecommendationCopy` package mirror, never free text (D-03/D-04).
- Honest absence: a suppressed user yields ZERO recommendations (explicit suppressed guard); a sparse/multiple-comparison-flood user yields zero by omission (no findings -> []) (D-08/SC3, Pitfall 5).
- App-side audited `RecommendationsCopy` (mirrors `InsightsCopy`'s `auditableStrings` posture); `CopyAuditTests.allRecommendationsCopy` re-pointed from the Plan-01 inline list to the real `RecommendationsCopy.auditableStrings`.
- Package `ForbiddenTerms` extended with the imperative directive list (mirror of `ClaimsPolicy.forbiddenImperatives`); `violation(in:)` now scans causal AND imperative so the HonestyGate catches an instruction in any emitted recommendation (Pitfall 4).
- Deleted the Plan-01 RED placeholder shim; extended `HonestyGateTests` with the recommendation streams + a dedicated direct-engine zero-forbidden-terms scan.

## Task Commits

1. **Task 1: RecommendationsCopy + ForbiddenTerms imperative extension** — `4ba89ac` (feat)
2. **Task 2: RecommendationsEngine producer — re-frame typed survivors** — `8bfcea6` (feat)

_Both tasks were `tdd="true"` plan-level slices turning the Plan-01 RED suites GREEN; the RED tests already existed (Plan 01), so each task is a single GREEN-making `feat` commit rather than a separate test->feat pair._

## Files Created/Modified

- `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift` (created) - the Foundation-only producer + the package-internal `RecommendationCopy` template mirror.
- `Sources/EndoApp/Features/Insights/RecommendationsCopy.swift` (created) - the app-side audited direction-aware copy surface + `auditableStrings`.
- `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` (modified) - added `imperative` list; `violation(in:)` scans causal + imperative.
- `Tests/EndoAppTests/CopyAuditTests.swift` (modified) - re-pointed `allRecommendationsCopy` at `RecommendationsCopy.auditableStrings`.
- `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift` (modified) - deleted the RED placeholder shim.
- `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` (modified) - added the recommendation streams + the direct `recommendationEngine_emitsZeroForbiddenTerms` scan.

## Decisions Made

- **One recommendation per symptom target, framed by the dominant direction.** The correlation pipeline surfaces COMPLEMENTARY findings for the same symptom (a 2-group severity split yields both a beneficial reading for the present-factor AND its aggravating mirror for the complementary factor). Surfacing both as separate recommendations is redundant. The engine collapses per symptom target to a single recommendation, choosing the direction with more supporting findings (tie -> aggravating, the cautionary non-over-promising default, D-03). This is the rule that satisfies the RED honesty contract (`strongAggravating_surfacesRecommendation` count==1 = dairy; `beneficial_usesFeelBetterFraming` first = the beneficial activity finding).
- **Suppressed -> zero recommendations** via an explicit `snapshots.contains { $0.suppressed }` guard. Raw correlations only suppress the cycle-phase factor (food correlations still surface for a suppressed user), but a recommendation is the most prescriptive-adjacent surface, so it is deliberately MORE conservative (D-08/SC3 — never a fabricated "do this").
- **App/package copy mirror** (mirrors the `ClaimsPolicy`<->`ForbiddenTerms` precedent): the package cannot import the app (SC1), so the curated templates live byte-identical in both; the app surface is `CopyAuditTests`-audited, the package mirror is HonestyGate-scanned.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan's literal "straight re-frame + top-N" emitted redundant/duplicate recommendations, violating the RED honesty contract**
- **Found during:** Task 2 (running `RecommendationsEngineTests`).
- **Issue:** The plan's Task-2 action specified a straight `findings.prefix(topN).map { ... }`. But `CorrelationEngine.scoredCandidates` returns COMPLEMENTARY mirror findings for a 2-group fixture — e.g. the `strongAssociationFixture` (dairy/sev7 vs caffeine/sev2) yields BOTH `food.dairy` (aggravating) AND `food.caffeine` (beneficial) at equal confidence/effect/n. A straight re-frame surfaced 2 recommendations (caffeine sorting first by signalKey), failing the RED `strongAggravating_surfacesRecommendation` (expects exactly 1 = dairy) and `beneficial_usesFeelBetterFraming` (the aggravating fixture's first rec must NOT read "feel better"). The plan also omitted any `suppressed` handling, so the `suppressedUserCorrelationFixture` (which still produces surviving food correlations) yielded recommendations, failing `suppressedUser_yieldsZeroRecommendations`.
- **Fix:** Added (a) an explicit suppressed guard (suppressed user -> `[]`), and (b) a `dominantPerSymptom` collapse — one recommendation per symptom target, framed by the direction with more supporting findings (tie -> aggravating). This is the minimal principled rule that satisfies ALL the RED assertions (count==1 / direction-flip / suppressed-zero / top-N<=3 / deterministic) and matches D-02 (avoid noise/overwhelm) + D-08/SC3.
- **Files modified:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`
- **Verification:** `RecommendationsEngineTests` 5/5 GREEN; `HonestyGateTests` GREEN; full `EndoInsightsTests` 102 GREEN.
- **Committed in:** `8bfcea6` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug).
**Impact on plan:** The auto-fix is essential to satisfy the Plan-01 RED honesty contract over the locked fixtures; it adds a principled, documented selection rule (dominant-per-symptom + suppressed guard) rather than the plan's under-specified straight re-frame. No scope creep — still a Foundation-only top-N producer.

## Issues Encountered

- **The RED contract vs. the documented straight-re-frame were in tension** over the complementary 2-group correlation mirrors. Resolved analytically by dumping `scoredCandidates` for each fixture (strong: caffeine-beneficial + dairy-aggravating; beneficial: 2 activity-beneficial + 1 caffeine-aggravating; suppressed: 2 survivors; flood: 0) and deriving the dominant-direction-per-symptom rule that uniquely satisfies every assertion. Verified empirically on the iPhone 16 simulator.
- The `EndoApp.xcodeproj` is gitignored and regenerated by XcodeGen from `project.yml` (which globs `Sources/EndoApp` + the local EndoInsights SPM package). The new app file is picked up automatically on `xcodegen generate`; no `project.yml` change was needed. The pbxproj was regenerated cleanly and is not committed (gitignored).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 11-03 (Wave 3) is unblocked: compose `RecommendationsEngine` into `InsightsEngine.insights(...)` behind the HonestyGate, lower `HonestyGate.minSample(.recommendation)` 14 -> 10 (Plan 03's edit — NOT done here), add the `.recommendation` "Recommendations" section to `InsightsView` (placed after "Possible associations"), and turn the Plan-01 `testRecommendation_rendersAfterCorrelation` E2E GREEN.
- The package stays Foundation-only (BoundaryLintTests GREEN). The app `RecommendationsCopy` and the package `RecommendationCopy` MUST stay byte-identical if either is edited.

## Verification

- `RecommendationsEngineTests` 5/5 GREEN (surfaced/withheld/suppressed/direction-flip/top-N/deterministic).
- `CopyAuditTests` 23/23 GREEN (recommendation causal/imperative/gendered/numeric scans over the real `RecommendationsCopy.auditableStrings`).
- `HonestyGateTests` GREEN (extended zero-forbidden-terms streams + the direct `recommendationEngine_emitsZeroForbiddenTerms` scan; the new imperative scan trips NO existing emitted copy).
- `BoundaryLintTests` + `CorrelationEngineTests` + `InsightsEngineTests` GREEN (package still Foundation-only; correlation behavior byte-identical).
- Full `EndoInsightsTests` target: 102 tests in 12 suites GREEN.

## Self-Check: PASSED

All created/modified files present on disk; both task commits (`4ba89ac`, `8bfcea6`) present in git history. Deliverable suites GREEN on the iPhone 16 simulator: RecommendationsEngineTests 5/5, HonestyGateTests (extended), CopyAuditTests 23/23, BoundaryLint + CorrelationEngine + InsightsEngine, and the full EndoInsightsTests target (102 tests). One PRE-EXISTING flaky app-target test (`InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute` — a debounce/timing coalescing test, unrelated to this plan's files) intermittently crashes/times out in the full scheme run; it does not touch RecommendationsEngine/RecommendationsCopy/ForbiddenTerms and is out of scope (logged for the phase, not introduced here).

---
*Phase: 11-personalized-recommendations*
*Completed: 2026-06-10*
