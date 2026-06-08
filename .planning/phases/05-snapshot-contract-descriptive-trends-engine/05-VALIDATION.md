---
phase: 5
slug: snapshot-contract-descriptive-trends-engine
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 5 — Validation Strategy

> Per-phase validation contract. Derived from `05-RESEARCH.md` § Validation Architecture.
> This is a **pure-logic** phase — fully automatable (no device/iCloud/UI). The only structural risk is the XcodeGen local-package + package-test-in-scheme wiring, de-risked by a Wave-0 smoke task.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test`/`#expect`) for the `EndoInsights` package + existing XCTest/XCUITest for the app |
| **Config file** | `project.yml` (XcodeGen) + `EndoInsights/Package.swift` |
| **Package tests run via** | `scheme.test.targets: - package: EndoInsights/EndoInsightsTests` → executed by `xcodebuild test -scheme EndoApp` |
| **Quick run** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| **Full suite** | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | package units <10s · full ~3–6 min |

---

## Sampling Rate
- After every task commit: the package unit run (fast, deterministic).
- After every wave: full suite.
- Before `/gsd-verify-work`: full suite green.

---

## Validation Architecture (layers)

| Layer | What it covers | Why here |
|-------|----------------|----------|
| **Wave-0 package-test smoke** | Create `EndoInsights` (Foundation-only `Package.swift`) + wire `packages:`/`dependencies:`/`scheme.test.targets` in project.yml; add ONE trivial `@Test`; confirm it RUNS and the FULL existing suite stays GREEN | De-risks the structural make-or-break (does the package test actually execute in the scheme?) before building the real engine; framework-target fallback if it doesn't |
| **Unit — TrendsEngine (pure)** | `trends(from:asOf:)` descriptive stats (mean, %-days-present, direction) per window (7/30/90/all-time); injected `today`; `Equatable` outputs vs synthetic fixtures | The core deliverable; fully deterministic |
| **Unit — honesty/sparse fixtures** | the **suppressed, no-bleeding-for-months** fixture yields NO bleeding/cycle trend (gate `!suppressed && bleedingDayCount>=1`); sub-threshold windows emit "not enough data"; nil never read as 0 | SC3 + the no-phantom-cycle / absence≠0 invariants |
| **Boundary lint** | a test/grep asserting `EndoInsights/Sources` imports ONLY Foundation (no SwiftData/SwiftUI/UIKit) | SC1 belt-and-suspenders on the compiler boundary |
| **App mapping unit** | `Repository.daySnapshots()` maps @Model rows → `[DaySnapshot]` correctly (symptoms/pain/bleeding/factors/suppressed), consent-gated | the SwiftData→contract seam stays app-side |
| **Regression** | full existing suite (Phases 1–4) green after adding the package + any enum move/re-export | enum sourcing (Option C) touches app-wide references |

---

## Per-Task Verification Map

> Populated during planning. Wave 0 = the package-test smoke + fixtures scaffold.

| Task ID | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|-------------|-----------|-------------------|--------|
| *(populated during planning)* | 0 | INST-01 | smoke | full suite green w/ a running package @Test | ⬜ pending |

---

## Wave 0 Requirements
- [ ] `EndoInsights` package (Foundation-only `Package.swift`) + project.yml wiring; ONE `@Test` proven to run in `xcodebuild test -scheme EndoApp`; full suite GREEN.
- [ ] Synthetic-fixture builders for `[DaySnapshot]` incl. the suppressed/no-bleeding-for-months fixture.
- [ ] Boundary-lint test/grep (package imports only Foundation).

---

## Manual-Only Verifications

*(none — Phase 5 is pure logic, fully automatable. No device/iCloud/UI behavior to verify by hand.)*
