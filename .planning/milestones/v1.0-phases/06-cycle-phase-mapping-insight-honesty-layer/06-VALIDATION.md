---
phase: 6
slug: cycle-phase-mapping-insight-honesty-layer
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 6 — Validation Strategy

> Derived from `06-RESEARCH.md` § Validation Architecture. Fully automatable (no device/iCloud/UI) — the honesty layer's correctness IS the product ethics, so it is heavily test-gated.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`EndoInsights` package + app `EndoAppTests`); migration via XCTest |
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
| **Insight.signalKey surfacing** | `Insight` gains `public let signalKey`; existing equality/id tests stay green | Load-bearing for dismissal (RESEARCH Open Q1) |
| **CyclePhaseMapper (INST-02)** | regular cycle → estimated phases (confidence ≤ .medium); **suppressed → NO phases**; **irregular (spread>9) → NO phases**; ovulation band = max(2, spread/2) | SC1 no-phantom-phase is the #1 pitfall |
| **HonestyGate (INST-04)** | per-kind min-sample → **withhold** under-sampled; confidence floor → withhold (NOT rank); the `.none` "not enough data" insight carved out; non-causal forbidden-term check over ALL emitted insights | the ethical core (SC2/SC3) |
| **No-Phase-5-regression** | trends routed through the gate still produce the Phase-5 expected insights (gate trend threshold == minLoggedDays 3) | the gate must not silently drop valid trends |
| **Dismissal (SC4)** | `DismissedInsight` SchemaV4 additive + lightweight V3→V4 migration survival; `Repository.dismissInsight/dismissedSignalKeys`; engine excludes dismissed signalKeys; a dismissed signal stays gone across recomputation | persistence + the engine choke-point |
| **InsightsEngine public API** | `insights(from:asOf:dismissed:)` composes trends + cycle-phase → gate → dismissal filter; `TrendsEngine` is internal; output Equatable/deterministic | the single Phase-8 entry point |
| **Boundary lint + regression** | package still imports only Foundation; full Phase 1–5 suite green | SC1 + no app regression |

---

## Per-Task Verification Map

> Populated during planning. Wave 0 = signalKey surfacing + fixtures + migration-test scaffold.

| Task ID | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|-------------|-----------|-------------------|--------|
| *(populated during planning)* | 0 | INST-02/04 | unit | package + migration tests green | ⬜ pending |

---

## Wave 0 Requirements
- [ ] `Insight.signalKey` surfaced (additive); existing id/equality tests green.
- [ ] Swift Testing fixtures: regular / suppressed / irregular / under-sampled / causal-language / dismissal-exclusion.
- [ ] `SchemaMigrationTests` V3→V4 survival scaffold (clone the V2→V3 case).

---

## Manual-Only Verifications

*(none — fully automatable. The live dismiss-button UI + rendering is Phase 8, not Phase 6.)*
