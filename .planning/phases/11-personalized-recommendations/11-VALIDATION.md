---
phase: 11
slug: personalized-recommendations
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-09
planned: 2026-06-09
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test`/`#expect`) — package (`EndoInsights`) logic + app honesty/copy + an XCUITest dashboard E2E |
| **Config file** | `project.yml` (XcodeGen — EndoApp scheme test targets incl. `EndoInsights/EndoInsightsTests`) |
| **Quick run command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | ~60–180 seconds (simulator build + test) |

---

## Sampling Rate

- **After every task commit:** Run the quick command (package logic tests)
- **After every plan wave:** Run the full suite
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

> Populated by the planner. Strategy (per RESEARCH §Validation Architecture): the `RecommendationsEngine` is a PURE producer composing the already-gated correlation insights — unit-testable over synthetic fixtures with NO store/clock. Honesty is enforced by extending the copy audits (the NEW forbidden-imperative scan + the existing forbidden-verb/gendered/numeric scans over `RecommendationsCopy`) and by acceptance tests through the PUBLIC `InsightsEngine` API (suppressed/sparse → ZERO recommendations; strong association → a non-prescriptive prompt; dismissal-exclusion; top-N cap deterministic order). The Foundation-only boundary is held by `BoundaryLintTests`.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 11-01-T1 | 11-01 | 1 | INST-05 | T-11-02/03 | Typed beneficial/aggravating direction recovered from the SIGNED effect via a package-internal `scoredCandidates`; no public surface, package stays Foundation-only | unit | `xcodebuild test … -only-testing:EndoInsightsTests/CorrelationEngineTests -only-testing:EndoInsightsTests/InsightsEngineTests` | ✅ extend `CorrelationEngine.swift` | ⬜ pending |
| 11-01-T2 | 11-01 | 1 | INST-05 | T-11-02 | RED honesty spec (surfaced/withheld/suppressed/direction/top-N/determinism) + beneficial/aggravating fixtures | unit (RED) | `test -f …/RecommendationsEngineTests.swift && grep -q beneficialAssociationFixture …` (compiles GREEN in Plan 02) | ❌ Wave 0 (`RecommendationsEngineTests.swift`, `beneficialAssociationFixture`) | ⬜ pending |
| 11-01-T3 | 11-01 | 1 | INST-05 | T-11-01 | Forbidden-imperative policy + scan flags deliberate imperatives, passes curated discussion-prompt words (substring trap both ways); failing recommendation E2E + correlation seed seam | unit + UI (RED E2E) | `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests/imperativeScan_flagsDeliberateImperatives -only-testing:EndoAppTests/CopyAuditTests/imperativeScan_passesCuratedDiscussionWords` | ✅ extend `ClaimsPolicy.swift`/`CopyAuditTests.swift`/`AppDependencies.swift`/`InsightsDashboardUITests.swift` | ⬜ pending |
| 11-02-T1 | 11-02 | 2 | INST-05 | T-11-01 | `RecommendationsCopy` direction-aware templates clean against causal/imperative/gendered/numeric scans; package `ForbiddenTerms` now catches imperatives | unit | `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` | ✅ NEW `RecommendationsCopy.swift` + extend `ForbiddenTerms.swift` | ⬜ pending |
| 11-02-T2 | 11-02 | 2 | INST-05 | T-11-04/05/06 | Producer re-frames typed survivors into top-N (≤3) `.recommendation` Insights (stable `recommendation.<stem>` signalKey, `.allTime`, inherited confidence/sampleSize); suppressed/flood → ZERO; deterministic; zero forbidden terms | unit | `xcodebuild test … -only-testing:EndoInsightsTests/RecommendationsEngineTests -only-testing:EndoInsightsTests/HonestyGateTests` | ✅ NEW `RecommendationsEngine.swift` + extend `HonestyGateTests.swift` | ⬜ pending |
| 11-03-T1 | 11-03 | 3 | INST-05 | T-11-01/04/05/07 | Composed behind the single `HonestyGate` via the LOCKED `InsightsEngine` signature; `minSample(.recommendation)==10==minSample(.correlation)`; suppressed/flood → ZERO; dismissal idempotent | unit | `xcodebuild test … -only-testing:EndoInsightsTests` | ✅ extend `InsightsEngine.swift`/`HonestyGate.swift`/`InsightsEngineTests.swift`/`HonestyGateTests.swift` | ⬜ pending |
| 11-03-T2 | 11-03 | 3 | INST-05 / D-06 | T-11-04 | Recommendations section after "Possible associations", omit-when-empty (no `insights.section.recommendations` for an unsupported user); reused `InsightCardView` + dismiss path | UI (XCUITest) | `xcodebuild test … -only-testing:EndoAppUITests/InsightsDashboardUITests/testRecommendation_rendersAfterCorrelation` | ✅ extend `InsightsView.swift` (turns the Plan-01 RED E2E GREEN) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `RecommendationsEngineTests` scaffold + synthetic fixtures (beneficial / aggravating / sparse / suppressed) — RED honesty suite first → **Plan 11-01 Task 2**
- [x] Direction-recovery seam decision locked (RESEARCH OQ1: package-internal typed `scoredCandidates` accessor on `CorrelationEngine`, recommended) before the producer is built → **Plan 11-01 Task 1** (Option A chosen)
- [x] Extended copy audit: the NEW forbidden-imperative scan over `RecommendationsCopy` (curated to dodge the substring trap — no "use"⊂"because", "her"⊂"higher") → **Plan 11-01 Task 3** (policy + scan + positive controls) / **Plan 11-02 Task 1** (the audited `RecommendationsCopy` surface)

*Existing Swift Testing + XCUITest infrastructure (incl. the package test target + the dashboard E2E harness) covers the rest. NO SwiftData schema bump this phase.*

> Wave-0 is Plan 11-01 (the engine scaffold + the direction seam + the imperative scan + the failing E2E). `wave_0_complete` flips to `true` once Plan 11-01 lands (the RED suite + seam + scan exist; the engine suite compiles GREEN in Plan 11-02).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Recommendation reads as a discussion prompt (human-judgment tone) on real multi-factor data | INST-05 | "Non-prescriptive / discussion-prompt" tone is partly human-judgment beyond the automated imperative scan; needs a real populated dataset | On a populated multi-factor account, open Insights → Recommendations; confirm each reads as a discussion prompt ("you tend to…", "something to discuss with your clinician…"), never an instruction, with a plain-language confidence + "based on your own data" note. Deferred end-of-phase human check (`human_verify_mode=end-of-phase`, same posture as prior phases; screenshot saved to /tmp/e2e by Plan 11-03 Task 2). |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (the engine scaffold + direction seam + imperative scan)
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved (planner)
