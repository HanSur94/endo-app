---
phase: 11-personalized-recommendations
verified: 2026-06-10T00:00:00Z
status: human_needed
score: 12/12 must-haves verified
overrides_applied: 0
human_verification:
  - test: "On a populated multi-factor account, open Insights -> Recommendations and read each card."
    expected: "Each reads as a discussion prompt ('you tend to feel better when...', 'something you might explore', 'something to discuss with your clinician'), never an instruction, with a plain-language confidence and a 'based on your own data' framing."
    why_human: "Discussion-prompt TONE on real populated data is partly human-judgment beyond the automated forbidden-imperative/causal/gendered/numeric substring scans. Deferred end-of-phase per 11-VALIDATION.md Manual-Only (human_verify_mode=end-of-phase, same posture as prior phases). Dashboard screenshot saved at /tmp/e2e/insights-06-recommendations.png."
---

# Phase 11: Personalized Recommendations Verification Report

**Phase Goal:** Compose data-driven, non-prescriptive recommendations on top of the proven correlation engine — framed as discussion prompts about the user's own patterns, routed through the honesty layer, never as medical instructions; shown only with sufficient data; dismissable.
**Verified:** 2026-06-10
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

The phase goal decomposes into the three ROADMAP Success Criteria (the INST-05 contract). All three are verified in the actual source AND proven by a GREEN 107-test package run executed by this verifier (`swift test`, exit 0). One end-of-phase human-verify item (tone judgment on a populated dataset) was deliberately deferred by the plan and is surfaced below.

### Observable Truths

| #  | Truth | Status | Evidence |
| -- | ----- | ------ | -------- |
| SC1 | Recommendations derived from the user's OWN patterns — composed, not re-derived | ✓ VERIFIED | `RecommendationsEngine.recommendations(from:asOf:calendar:)` (RecommendationsEngine.swift:50) consumes `CorrelationEngine.scoredCandidates(...)` (line 63) and runs NO statistics of its own; `scoredCandidates` maps the SAME shared `scoredSurvivors` pipeline as `insights(...)` (CorrelationEngine.swift:48-68) → a finding exists iff the correlation insight exists. Composed into the LOCKED `InsightsEngine.insights(from:asOf:dismissed:calendar:)` (InsightsEngine.swift:42) behind the SINGLE `HonestyGate.apply` (line 43). Top-N=3 cap (RecommendationsEngine.swift:36,80). Tests `composition_surfacesRecommendation_overStrongFixture`, `strongAggravating_surfacesRecommendation`, `output_isDeterministic` — all PASS. |
| SC2 | Phrased as discussion prompts, NEVER medical instructions; inherits honesty confidence + non-causal framing | ✓ VERIFIED | Direction-aware copy from curated templates only (RecommendationsCopy.swift:38-52 + byte-identical package mirror RecommendationsEngine.swift:170-185): beneficial = "feel better", aggravating = "more intense ... something you might explore", detail = "a pattern in your own data, not advice". NEW forbidden-imperative scan: `ClaimsPolicy.forbiddenImperatives` (ClaimsPolicy.swift:63) + package `ForbiddenTerms.imperative` (ForbiddenTerms.swift:74) — byte-identical 9-term lists; `ForbiddenTerms.violation(in:)` scans causal + imperative (line 89-93). `CopyAuditTests` has POSITIVE control (`imperativeScan_passesCuratedDiscussionWords`) AND NEGATIVE control (`imperativeScan_flagsDeliberateImperatives`: "you should avoid dairy"/"try cutting gluten") dodging the "use"⊂"because" / "her"⊂"higher" trap. Confidence inherited from correlation (RecommendationsEngine.swift:142); words-only card, no numeric. Test `recommendationEngine_emitsZeroForbiddenTerms` — PASS. |
| SC3 | Shown ONLY with sufficient data + dismissable | ✓ VERIFIED | `minSample(.recommendation) == 10 == minSample(.correlation)` (HonestyGate.swift:39-40), lowered from the 14 placeholder (comment line 31-33; SUMMARY confirms 14->10). Suppressed/sparse/flood → ZERO recommendations (RecommendationsEngine.swift:59,67 + tests `suppressedUser_yieldsZeroRecommendations`, `gate_withholdsRecommendation_forSuppressedAndFlood` PASS). Section omits-when-empty (InsightsView.swift:116-128 `if !kindInsights.isEmpty`). Stable window-independent signalKey `recommendation.<stem>` with `.allTime` (RecommendationsEngine.swift:138-139,151-156) → dismissal survives recompute (test `dismissal_excludesRecommendation_andStaysExcluded` PASS). E2E `testRecommendation_rendersAfterCorrelation` documented GREEN; screenshot at /tmp/e2e/insights-06-recommendations.png exists. |

**Score:** 3/3 success criteria verified · 12/12 plan must-have truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift` | Foundation-only producer, recommendations(from:asOf:calendar:) -> top-N .recommendation Insights via Insight.derived | ✓ VERIFIED | 186 lines, `import Foundation` only, enum namespace, composes scoredCandidates, no stats. |
| `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift` | package-internal scoredCandidates direction seam, typed CorrelationFinding | ✓ VERIFIED | `scoredCandidates` (line 62) + `RecommendationDirection`/`CorrelationFinding` (lines 353-371), direction recovered from SIGNED meanDiff/riskRatio (lines 389-397). |
| `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` | RecommendationsEngine composed into candidate concatenation, signature unchanged | ✓ VERIFIED | Line 42 concatenation before HonestyGate.apply; public signature LOCKED (lines 29-34). |
| `EndoInsights/Sources/EndoInsights/HonestyGate.swift` | minSample(.recommendation) 14->10 | ✓ VERIFIED | Lines 39-40 both return 10. |
| `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` | imperative directive terms in package scan | ✓ VERIFIED | `imperative` list (line 74), scanned by `violation(in:)`. |
| `Sources/EndoApp/Content/ClaimsPolicy.swift` | forbiddenImperatives policy list | ✓ VERIFIED | Line 63, 9 terms, byte-identical to package mirror. |
| `Sources/EndoApp/Features/Insights/RecommendationsCopy.swift` | auditable direction-aware templates + auditableStrings | ✓ VERIFIED | `sectionRecommendations`, beneficial/aggravating headlines, detail, auditableStrings (lines 30-65). |
| `Sources/EndoApp/Features/Insights/InsightsView.swift` | .recommendation section after Possible associations, omit-when-empty | ✓ VERIFIED | Lines 74-77 section after associations; omit-when-empty helper lines 116-128. |
| `Tests/EndoAppTests/CopyAuditTests.swift` | forbidden-imperative scan + positive/negative controls | ✓ VERIFIED | Lines 298-372: positive controls over RecommendationsCopy.auditableStrings + negative control + substring-trap pass. |
| `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift` | honesty contract (surfaced/withheld/suppressed/direction/top-N/determinism) | ✓ VERIFIED | 6 GREEN tests covering every honesty dimension. |
| `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` | minSample==10 + n=10 survives + dismissal | ✓ VERIFIED | `minSampleRecommendation_equalsCorrelationFloor`, `recommendationFromN10Correlation_survivesGate`, `recommendationEngine_emitsZeroForbiddenTerms` PASS. |
| `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` | composition + dismissal + suppressed/flood | ✓ VERIFIED | `composition_surfacesRecommendation...`, `gate_withholdsRecommendation_forSuppressedAndFlood`, `dismissal_excludesRecommendation_andStaysExcluded` PASS. |
| `Tests/EndoAppUITests/InsightsDashboardUITests.swift` | testRecommendation_rendersAfterCorrelation E2E | ✓ VERIFIED (source) | Lines 199-229; documented GREEN, screenshot artifact present. iOS-simulator-only; not runnable in this sandbox — verified at source level + screenshot. |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| RecommendationsEngine.recommendations | CorrelationEngine.scoredCandidates | consumes typed survivors (compose, never re-derive) | ✓ WIRED | RecommendationsEngine.swift:63 |
| RecommendationsEngine emitted copy | RecommendationCopy templates | headline/detail from templates only | ✓ WIRED | RecommendationsEngine.swift:131-141 |
| RecommendationsEngine Insight | Insight.derived | deterministic id from kind|allTime|signalKey | ✓ WIRED | RecommendationsEngine.swift:136-145 |
| InsightsEngine.insights | RecommendationsEngine.recommendations | concatenation before HonestyGate.apply | ✓ WIRED | InsightsEngine.swift:42-43 |
| InsightsView section | coordinator.insights .recommendation | section filtered by .recommendation, omit-when-empty | ✓ WIRED | InsightsView.swift:74-77,116-128 |
| HonestyGate.minSample(.recommendation) | minSample(.correlation) | shared correlation floor (10) | ✓ WIRED | HonestyGate.swift:39-40 |
| CopyAuditTests imperative scan | ClaimsPolicy.forbiddenImperatives | substring audit over recommendation copy | ✓ WIRED | CopyAuditTests.swift:288,299-301 |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| EndoInsights package compiles Foundation-only | `swift build` (EndoInsights) | ok (build complete), exit 0 | ✓ PASS |
| Full package honesty suite (incl. all Phase-11 tests) | `swift test` (EndoInsights) | "Test run with 107 tests in 12 suites passed", exit 0 | ✓ PASS |
| RecommendationsEngine honesty contract | suite "INST-05 RecommendationsEngine ..." | passed | ✓ PASS |
| minSample(.recommendation)==10 + n=10 survives | `minSampleRecommendation_equalsCorrelationFloor`, `recommendationFromN10Correlation_survivesGate` | passed | ✓ PASS |
| Suppressed/flood → ZERO recommendations | `gate_withholdsRecommendation_forSuppressedAndFlood`, `suppressedUser_yieldsZeroRecommendations` | passed | ✓ PASS |
| Foundation-only boundary | suite "D-12 package import boundary" (BoundaryLintTests) | passed | ✓ PASS |
| App-side copy audit + UI E2E | `xcodebuild test ... EndoAppTests/EndoAppUITests` | not run (requires iOS simulator + XcodeGen project) | ? SKIP — verified at source level + screenshot artifact |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| INST-05 | 11-01, 11-02, 11-03 | User receives personalized, data-driven recommendations derived from their own patterns | ✓ SATISFIED | All 3 SCs verified above; RecommendationsEngine composes scoredCandidates into gated .recommendation Insights, non-prescriptive copy audited, shown-with-sufficient-data + dismissable. No orphaned requirements: INST-05 is the sole ID mapped to Phase 11 in REQUIREMENTS.md (line 134), and all three plans declare `requirements: [INST-05]`. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| InsightsView.swift | 18 | "No fake/placeholder cards" | ℹ️ Info | Negative assertion in a comment (the opposite of a stub) — not a debt marker. No action. |

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` debt markers in any phase-modified source. No empty-data stubs: the suppressed/sparse `return []` paths (RecommendationsEngine.swift:59,67) are the INTENDED honest-absence behavior, proven by the absence tests — not unwired stubs.

### Constraint Verification

- Foundation-only package: ✓ BoundaryLintTests GREEN; RecommendationsEngine imports only Foundation.
- No SwiftData schema bump: ✓ no model/schema files modified; `.recommendation` InsightKind already reserved (Insight.swift:24).
- Zero new third-party deps: ✓ EndoInsights/Package.swift declares ZERO dependencies (line 5,22).
- No LLM / deterministic templates: ✓ curated static template strings; `output_isDeterministic` PASS.

### Human Verification Required

#### 1. Recommendation discussion-prompt tone on a populated multi-factor account

**Test:** On a populated multi-factor account, open Insights → Recommendations and read each card.
**Expected:** Each reads as a discussion prompt ("you tend to feel better on days with...", "something you might explore", "something to discuss with your clinician"), never an instruction, with a plain-language confidence indicator and a "based on your own data" framing.
**Why human:** Discussion-prompt TONE on real populated data is partly human-judgment beyond the automated substring scans (which prove the absence of forbidden imperative/causal/gendered/numeric tokens but not holistic tone). This is the planner-deferred end-of-phase human check (`human_verify_mode=end-of-phase`, 11-VALIDATION.md Manual-Only). Dashboard screenshot already captured at /tmp/e2e/insights-06-recommendations.png.

### Gaps Summary

No gaps. Every must-have truth, artifact, and key link is verified in the actual source, and the entire EndoInsights honesty suite (107 tests, including all Phase-11 coverage and the Foundation-only boundary lint) passes GREEN when executed by the verifier. The phase goal is achieved.

Status is `human_needed` (not `passed`) solely because the plan deliberately deferred one tone-judgment check to end-of-phase human verification — a quality confirmation on a populated dataset, not a functional gap. The documented pre-existing `InsightsCoordinatorTests` debounce flake is logged in deferred-items.md and does NOT touch any Phase-11 file — not a Phase-11 gap.

---

_Verified: 2026-06-10_
_Verifier: Claude (gsd-verifier)_
