---
phase: 7
slug: correlation-trigger-discovery
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 7 — Validation Strategy

> Derived from `07-RESEARCH.md` § Validation Architecture. Fully automatable (no device/iCloud/UI). The deliverable's value is SUPPRESSING noise — so the spurious/flood/under-sampled fixtures are the load-bearing tests.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`EndoInsights` package); runs in the EndoApp scheme |
| **Config** | `project.yml` + `EndoInsights/Package.swift` |
| **Quick run** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| **Full suite** | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

---

## Sampling Rate
- After every task commit: the package unit run.
- After every wave: full suite.
- Before `/gsd-verify-work`: full suite green.

---

## Validation Architecture (layers)

| Layer | What it covers | Why here |
|-------|----------------|----------|
| **Wave-0 — CorrelationStats math** | Cliff's delta, mean-difference, risk difference/ratio (2×2), Fisher's exact p, Mann–Whitney U normal-approx p, Benjamini–Hochberg FDR — each unit-tested against hand-computed known values; `erf` + `(lgamma as (Double)->Double)` binding compiles | The math MUST be right and deterministic before any insight is built on it |
| **CorrelationEngine (INST-03)** | factor enumeration from DaySnapshot + phaseWindows; same-day pairing; `unobserved` excluded (absence≠0); produces `.correlation` Insights with honest effect size + sampleSize + confidence | the producer |
| **Honesty — spurious/under-sampled/flood (SC3)** | a strong association is surfaced; an under-sampled pair (n<10) is WITHHELD; a spurious pair (small effect / fails FDR) is WITHHELD; a K≈100+ flood fixture surfaces NO false positives (BH at q=0.10 holds) | the ethical core — the value is suppression |
| **Non-causal copy** | every emitted correlation passes `ForbiddenTerms` (fail-closed); "possible association" framing | SC2/honesty |
| **Suppressed user** | a suppressed-mode fixture yields NO cycle-phase correlation (phaseWindows empty) | D-04 by construction |
| **minSample + no regression** | `minSample(.correlation)=10` single source of truth; the gate withholds below it; Phase-5/6 trends + cycle-phase insights unaffected; package still Foundation-only | one-line gate change, no regression |

---

## Per-Task Verification Map

> Populated during planning. Wave 0 = CorrelationStats math + fixtures.

| Task ID | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|-------------|-----------|-------------------|--------|
| *(populated during planning)* | 0 | INST-03 | unit | CorrelationStatsTests + fixtures green | ⬜ pending |

---

## Wave 0 Requirements
- [ ] `CorrelationStatsTests` proving Cliff's delta / risk diff+ratio / Fisher exact / Mann–Whitney / BH-FDR against known values (the math first).
- [ ] Fixtures: strong (surfaced), under-sampled (withheld), spurious (withheld), multiple-comparison flood (no false positives), non-causal-copy guard, suppressed-user (no phase correlation).

---

## Manual-Only Verifications

*(none — fully automatable. Rendering + the dismiss action are Phase 8.)*
