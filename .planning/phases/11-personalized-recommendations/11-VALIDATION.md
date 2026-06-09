---
phase: 11
slug: personalized-recommendations
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
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
| (planner-filled) | — | — | INST-05 | — | — | — | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `RecommendationsEngineTests` scaffold + synthetic fixtures (beneficial / aggravating / sparse / suppressed) — RED honesty suite first
- [ ] Direction-recovery seam decision locked (RESEARCH OQ1: package-internal typed `scoredCandidates` accessor on `CorrelationEngine`, recommended) before the producer is built
- [ ] Extended copy audit: the NEW forbidden-imperative scan over `RecommendationsCopy` (curated to dodge the substring trap — no "use"⊂"because", "her"⊂"higher")

*Existing Swift Testing + XCUITest infrastructure (incl. the package test target + the dashboard E2E harness) covers the rest. NO SwiftData schema bump this phase.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Recommendation reads as a discussion prompt (human-judgment tone) on real multi-factor data | INST-05 | "Non-prescriptive / discussion-prompt" tone is partly human-judgment beyond the automated imperative scan; needs a real populated dataset | On a populated multi-factor account, open Insights → Recommendations; confirm each reads as a discussion prompt ("you tend to…", "something to discuss with your clinician…"), never an instruction, with a plain-language confidence + "based on your own data" note. Deferred end-of-phase human check (same posture as prior phases). |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (the engine scaffold + direction seam + imperative scan)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
