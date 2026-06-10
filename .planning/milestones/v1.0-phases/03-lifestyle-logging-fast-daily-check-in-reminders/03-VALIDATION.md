---
phase: 3
slug: lifestyle-logging-fast-daily-check-in-reminders
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `03-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | XCTest (unit) + XCUITest (E2E) — already in repo |
| **Config file** | `project.yml` (XcodeGen) — `xcodegen generate` regenerates the project |
| **Quick run command** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| **Full suite command** | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | ~unit <30s · full incl. UITests ~3–6 min (simulator boot dependent) |

---

## Sampling Rate

- **After every task commit:** Run the quick (unit) command for the touched target.
- **After every plan wave:** Run the full suite (unit + UITests).
- **Before `/gsd-verify-work`:** Full suite must be green.
- **Max feedback latency:** ~30s (unit) / ~6 min (full).

---

## Validation Architecture (layers)

Sourced from `03-RESEARCH.md`. Each Phase-3 capability is tested at the cheapest sufficient layer:

| Layer | What it covers | Why here |
|-------|----------------|----------|
| **Pure unit** | `FactorReading` round-trip; `FactorDay` Sendable snapshot keeps `nil` distinct from `0` (honesty); quiet-hours wrap-around fn; auto-suppress decision fn; "same as yesterday" sticky-context selection | Deterministic, no simulator/system deps |
| **Migration test** | `SchemaMigrationTests` V2→V3 lightweight: prove V1/V2 data survives reopen; no `.custom` stage | Top data-loss risk (A1 discipline) |
| **Repository unit** | factor + medication CRUD via `Calendar`-bucketed upsert/remove; consent assertion; clamp/bounds | Mirrors Phase-2 Repository tests |
| **NotificationScheduler unit (mock)** | schedule/cancel/reschedule logic via protocol seam + mock — fatigue logic is pure, system notifications are NOT asserted directly | XCUITest cannot assert system notifications |
| **XCUITest E2E** | fast check-in flow records a day in a few taps; factor sections in the day editor; reminders settings opt-in/quiet-hours UI; unlogged-vs-zero honesty in controls | User-visible acceptance (LIFE/CHCK criteria) |

---

## Per-Task Verification Map

> Populated during planning — one row per plan task. Each row maps Task ID → Requirement → Test Type → automated command. Filled as PLAN.md task IDs are assigned (Wave 0 = test scaffolding).

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| *(populated during planning)* | | | LIFE-01..03 / CHCK-01..02 | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Migration fixture/test stub for V2→V3 (`SchemaMigrationTests`) — extend existing migration test target.
- [ ] `NotificationScheduler` protocol + mock for fatigue-logic unit tests.
- [ ] XCUITest accessibility identifiers for the new check-in sheet, factor sections, and reminders settings (extend the `-uitest-reset` harness).

*Existing XCTest/XCUITest infrastructure (Phases 1–2) covers the framework; Wave 0 adds the new seams/fixtures above.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| A real local notification fires at the scheduled time and deep-links into the check-in sheet | CHCK-02 | XCUITest cannot reliably trigger/assert system notification delivery | On a simulator/device: enable a reminder ~1–2 min out, background the app, confirm the banner fires and tapping it opens the check-in sheet |
| Notification permission prompt appears just-in-time on first enable; denied state degrades gracefully | CHCK-02 / D-20 | System permission alert is outside the app's UI test surface | Fresh install → enable reminder → confirm system prompt → deny → confirm in-app reminders section shows denied state + path to Settings.app |
| Mood 1–5 valence reads as "overall mood for the day" (not the Phase-2 acute symptom) | LIFE-03 / D-08 | Subjective clarity (CONTEXT-flagged for UAT) | Simulator screenshot review during execution |
