---
phase: 8
slug: insights-integration-coordinator-cached-insights-dashboard
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 8 — Validation Strategy

> Derived from `08-RESEARCH.md` § Validation Architecture. Mostly automatable (coordinator + cache + migration + dashboard E2E); the Swift Charts VISUAL quality → UI-review, on-device CachedInsight SYNC → human_needed (Phase-4 device dependency).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`EndoInsights` + `EndoAppTests` units) + XCUITest (dashboard E2E) |
| **Config** | `project.yml` + `EndoInsights/Package.swift` |
| **Quick run** | `xcodebuild test … -only-testing:EndoAppTests` (+ `-only-testing:EndoInsightsTests`) |
| **Full suite** | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **E2E seeding** | a new `-uitest-seed-insights` launch arg inserting a threshold-crossing fixture (UI-built data can't cross the honesty thresholds) |

---

## Sampling Rate
- After every task commit: the touched target's unit run.
- After every wave: full suite.
- Before `/gsd-verify-work`: full suite green.

---

## Validation Architecture (layers)

| Layer | What it covers | Why here |
|-------|----------------|----------|
| **Insight Codable round-trip** | `Insight` becomes `Codable` (additive, package); encode→decode is identity incl. points/confidence/kind/sampleSize/isInsufficiencyNotice | prerequisite for CachedInsight serialization |
| **CachedInsight (CloudKit-safe)** | flat identity columns + `payload: Data?` blob; optional/defaulted, no `.unique`, app-gen UUID, no relationships; `Repository` round-trip (save→load == original `[Insight]`) | persistence + sync correctness |
| **SchemaV5 migration** | additive `SchemaV5` (adds CachedInsight); lightweight V4→V5 (never `.custom`); `SchemaMigrationTests` V4→V5 survival (clone V3→V4) | top data-loss risk |
| **InsightsCoordinator (off-main)** | injected engine + clock: debounce coalesces rapid `dataChanged()`; cache hit (fingerprint unchanged → no recompute) / miss (recompute); recompute runs OFF main; publishes on `@MainActor`; dismissed keys fed in | SC2 no jank + correctness |
| **Swift Charts gap logic** | the pure series-grouping helper (contiguous logged runs → distinct `series`; unlogged days never `value: 0`) is unit-tested | absence ≠ 0 on the line chart (SC3) |
| **Dashboard E2E (XCUITest)** | seed via `-uitest-seed-insights` → open Insights → assert ≥1 rendered insight card (a11y id); empty state with no data; **suppressed → `insights.section.cyclePhase` does NOT exist**; dismiss ("This isn't right") → card gone + stays gone on reopen | the user-visible payoff + honesty |
| **Copy audit** | dashboard copy passes ClaimsPolicy (no causal terms, no numeric confidence) | honesty surfacing |

---

## Per-Task Verification Map

> Populated during planning. Wave 0 = Insight Codable + CachedInsight/SchemaV5 + the seed fixture.

| Task ID | Wave | Test Type | Automated Command | Status |
|---------|------|-----------|-------------------|--------|
| *(populated during planning)* | 0 | unit | Codable + CachedInsight round-trip + V4→V5 survival green | ⬜ pending |

---

## Wave 0 Requirements
- [ ] `Insight: Codable` (additive, package; identity round-trip test).
- [ ] `CachedInsight` @Model + `SchemaV5` + V4→V5 migration survival scaffold.
- [ ] `-uitest-seed-insights` launch arg + a threshold-crossing fixture sized against `HonestyGate.minSample` / `TrendsEngine.minLoggedDays`.

---

## Manual-Only Verifications (human_needed)

| Behavior | Why Manual | Test Instructions |
|----------|------------|-------------------|
| Swift Charts visual quality (legibility, gap rendering, Dynamic Type, dark mode) | Visual judgement — automated tests assert structure/a11y ids, not pixel quality | Simulator screenshots across Dynamic Type sizes + dark mode; the `/gsd-ui-review` step |
| `CachedInsight` syncs across the user's devices | Real iCloud + ≥2 devices + a set DEVELOPMENT_TEAM (Phase-4 dependency) | Log on device A, open Insights on device B (same iCloud) → the cached insights appear (rides the Phase-4 sync UAT) |
