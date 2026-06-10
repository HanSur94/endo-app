---
phase: 11-personalized-recommendations
plan: 01
subsystem: insights
tags: [recommendations, correlation, honesty, copy-audit, tdd, e2e, direction-recovery]
dependency_graph:
  requires:
    - "CorrelationEngine.insights (Phase 7) — the typed survivor pipeline re-framed here"
    - "Insight.derived + InsightConfidence + Factor (Phase 5/7) — the contracts the seam carries"
    - "ClaimsPolicy + CopyAuditTests (Phase 2) — the copy-audit choke-point extended"
    - "InsightsDashboardUITests + AppDependencies seed seams (Phase 8) — the E2E harness mirrored"
  provides:
    - "CorrelationEngine.scoredCandidates(from:asOf:calendar:) -> [CorrelationFinding] (package-internal, typed direction)"
    - "CorrelationEngine.RecommendationDirection (beneficial/aggravating) + CorrelationFinding"
    - "RecommendationsEngineTests (RED honesty spec) + beneficialAssociationFixture"
    - "ClaimsPolicy.forbiddenImperatives + the CopyAuditTests imperative scan (GREEN)"
    - "-uitest-seed-recommendations seam + testRecommendation_rendersAfterCorrelation E2E (RED)"
  affects:
    - "Plan 11-02 (RecommendationsEngine producer consumes scoredCandidates; deletes the RED shim)"
    - "Plan 11-03 (dashboard Recommendations section makes the E2E GREEN)"
tech_stack:
  added: []
  patterns:
    - "Typed direction recovery via a package-internal scoredCandidates accessor (RESEARCH Pattern 2 Option A) — direction from the SIGNED effect, never from audited copy"
    - "One shared private scoredSurvivors pipeline → insights(...) and scoredCandidates(...) stay in lockstep"
    - "Case-insensitive substring imperative scan with curated copy (the scan enforces, curation avoids the substring trap)"
key_files:
  created:
    - "EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift"
    - ".planning/phases/11-personalized-recommendations/11-01-SUMMARY.md"
  modified:
    - "EndoInsights/Sources/EndoInsights/CorrelationEngine.swift"
    - "EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift"
    - "Sources/EndoApp/Content/ClaimsPolicy.swift"
    - "Tests/EndoAppTests/CopyAuditTests.swift"
    - "Sources/EndoApp/App/AppDependencies.swift"
    - "Tests/EndoAppUITests/InsightsDashboardUITests.swift"
decisions:
  - "Direction recovery uses RESEARCH Pattern 2 Option A (package-internal typed accessor), NOT extending the public Insight (Option B rejected)"
  - "presence-arm nil/non-finite risk ratio defaults to .aggravating (the conservative co-occurrence reading); 0/non-finite severity meanDiff also defaults to .aggravating"
  - "beneficialAssociationFixture uses Factor.activityPresent (activity present → LOWER severity → meanDiff < 0 → beneficial) for a clean present/absent split"
  - "RED RecommendationsEngineTests carries a file-local placeholder shim so the package target compiles today; Plan 02 deletes it (Rule 3 auto-fix)"
metrics:
  duration: ~25m
  completed: 2026-06-09
---

# Phase 11 Plan 01: Personalized Recommendations Wave-0 Scaffold Summary

Lands the INST-05 failing-test-first MVP slice: a package-internal typed direction-recovery seam on `CorrelationEngine`, the RED `RecommendationsEngine` honesty suite plus beneficial/aggravating fixtures, the new forbidden-imperative copy policy + scan (GREEN with positive controls), and the failing end-to-end recommendation E2E plus its correlation seed seam — so the Plan-02 producer and Plan-03 dashboard build against locked contracts and a RED suite, never a scavenger hunt.

## What Shipped

### Task 1 — Direction-recovery seam (commit `2c9826c`)
- Refactored `CorrelationEngine.insights(...)` to delegate to a new shared private `scoredSurvivors(...)` pipeline (steps 0–6 extracted verbatim) returning the sorted, gate-filtered (`confidence >= .low`), top-N `[ScoredCandidate]`.
- Added package-internal `RecommendationDirection { beneficial, aggravating }` (Equatable) with semantics from the SIGNED effect: severity arm `beneficial` iff `meanDiff < 0`; presence arm `beneficial` iff `riskRatio < 1`; nil/non-finite → `.aggravating` (conservative).
- Added package-internal `CorrelationFinding` (Equatable) carrying `signalKey/factorLabel/targetLabel/confidence/pairedDays/effectMagnitude/direction`, plus `ScoredCandidate.makeFinding()` and a `direction` computed helper on the private `Arm`.
- Added the package-internal `scoredCandidates(from:asOf:calendar:) -> [CorrelationFinding]` accessor mapping the SAME survivors. `insights(...)` public behavior is byte-identical. No new public symbol.

### Task 2 — RED honesty suite + fixtures (commit `2e8689f`)
- Added `Fixtures.beneficialAssociationFixture()` (activity present → pelvicPain severity 2; no-activity → severity 7), the mirror of the aggravating `strongAssociationFixture`.
- Created `RecommendationsEngineTests` asserting surfaced/withheld/suppressed/direction/top-N/determinism against the synthetic fixtures, referencing the Plan-02 `RecommendationsEngine.recommendations(...)` + `RecommendationsCopy` template words.

### Task 3 — Imperative policy + scan + failing E2E (commit `eeeeeea`, tdd)
- Added `ClaimsPolicy.forbiddenImperatives` (take/stop/avoid/start/should/must/use/try/cut) with a substring-trap doc-comment.
- Extended `CopyAuditTests` with the imperative scan + `recommendationsCopy_*` causal/gendered/numeric scans over an inline representative surface, plus the two positive controls — `imperativeScan_flagsDeliberateImperatives` ("you should avoid dairy" / "try cutting gluten" flagged) and `imperativeScan_passesCuratedDiscussionWords` ("explore"/"discuss"/"tend to feel better"/"a pattern in your own data" pass clean). All GREEN.
- Added the `-uitest-seed-recommendations` seam + `seedRecommendationCorrelation(into:)` (22 days, dairy/severity-7 vs caffeine/severity-2 by parity) to `AppDependencies`, mirroring the existing seed guards.
- Added the failing `testRecommendation_rendersAfterCorrelation` E2E asserting `insights.section.recommendations` eventually exists (RED until Plan 03).

## Verification

- Task 1: `CorrelationEngineTests` + `InsightsEngineTests` — 21 tests GREEN (public behavior unchanged).
- Task 2: scaffold grep — `SCAFFOLD_OK` (file present; `beneficialAssociationFixture` + `RecommendationsEngine` referenced).
- Task 3: full `CopyAuditTests` suite — 23 tests GREEN, including all 6 new recommendation/imperative tests and both positive controls.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] RED package-test file broke the shared scheme build**
- **Found during:** Task 3 (running the positive-control verify).
- **Issue:** `RecommendationsEngineTests.swift` references the not-yet-existing `RecommendationsEngine` (intended RED per Task 2). Because the `EndoInsightsTests` package target compiles all its files together under the single `EndoApp` scheme, the unresolved symbol failed the whole scheme build — blocking even the unrelated `EndoAppTests` positive-control tests Task 3's verify must run GREEN.
- **Fix:** Added a clearly-marked file-local `private enum RecommendationsEngine` placeholder shim returning `[]`, so the file COMPILES today while the suite stays RED at RUNTIME (the surfaced/direction assertions fail on the empty result). Plan 02 deletes the shim when it provides the real producer (an "invalid redeclaration" error there is the intended removal signal).
- **Files modified:** `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift`
- **Commit:** `eeeeeea`

**2. [Plan discretion] `recommendationsCopy_*` scans point at an inline surface (not yet `RecommendationsCopy`)**
- **Found during:** Task 3.
- **Issue:** The real `RecommendationsCopy.auditableStrings` ships in Plan 02; referencing it now would break the `EndoAppTests` target compilation.
- **Fix:** The `recommendationsCopy_*` scans run over a small inline representative recommendation-copy surface with a documented `// Plan 02: swap → RecommendationsCopy.auditableStrings` marker — making the scan LOGIC real and testable immediately, trivially re-pointed in Plan 02. This matches the plan's own "gate the audited-surface tests on a small inline word list now" instruction.
- **Files modified:** `Tests/EndoAppTests/CopyAuditTests.swift`

## Known Stubs

- **`RecommendationsEngine` placeholder shim** (`RecommendationsEngineTests.swift`) — returns `[]`. Intentional RED scaffold; Plan 11-02 replaces it with the real re-framing producer.
- **Inline `allRecommendationsCopy` surface** (`CopyAuditTests.swift`) — representative recommendation strings. Intentional; Plan 11-02 re-points the scans at `RecommendationsCopy.auditableStrings`.

Both stubs are by design for the failing-test-first MVP slice and are documented for the resolving plans; neither prevents this plan's goal (establishing the seam + RED suite + GREEN imperative scan).

## Notes for Plan 02 / 03

- Consume `CorrelationEngine.scoredCandidates(...)` for typed direction; do NOT parse the public correlation copy.
- Delete the RED placeholder shim in `RecommendationsEngineTests.swift` and re-point `CopyAuditTests.allRecommendationsCopy` at `RecommendationsCopy.auditableStrings`.
- Lower `HonestyGate.minSample(.recommendation)` 14 → 10 (RESEARCH Pitfall 3) — NOT done here (out of scope for Wave 1).
- The aggravating headline must avoid "higher" (the "her" gendered-substring trap) — use "more intense" (the inline fixture already models this).

## Self-Check: PASSED

All created/modified files present on disk; all three task commits (`2c9826c`, `2e8689f`, `eeeeeea`) present in git history.
