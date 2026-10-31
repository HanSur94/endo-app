---
gsd_state_version: "1.0"
milestone: v2.1
milestone_name: Multilingual
current_phase: 26
current_phase_name: Shared l10n Foundation
status: executing
stopped_at: Completed 26-02-PLAN.md
last_updated: "2026-10-31T14:25:23.495Z"
last_activity: 2026-10-31
last_activity_desc: v2.1 roadmap created (Phases 26–33, 27/27 requirements mapped)
state_head: 3d638ada1bbbfd1e5fa13f63fe4734e08c6276ae
progress:
  total_phases: 8
  completed_phases: 0
  total_plans: 3
  completed_plans: 2
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-31)

**Core value:** Turn everyday self-tracking into understanding — correlations, cycle/trend patterns, flare warnings, and personalized recommendations that help people with endometriosis make sense of their condition.
**Current focus:** Phase 26 — Shared l10n Foundation (milestone v2.1 Multilingual)

## Current Position

Phase: 26 (Shared l10n Foundation) — EXECUTING
Plan: 3 of 3
Status: Ready to execute
Last activity: 2026-10-31 — Completed 26-02 (DE/FR/ES claims matcher, self/schema/parity suites)

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- v2.1 plans completed: 0
- Prior milestones: v1.0 39 plans · v1.1 17 plans · v2.0 42 plans (per-plan durations live in the archived phase summaries)

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

*Updated after each plan completion*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 26 P01 | 9min | 3 tasks | 8 files |
| Phase 26 P02 | 12min | 3 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table. Recent decisions affecting current work:

- [Roadmap v2.1]: Research order kept unchanged — 26 policy/glossary → 27 engine message contract → (28 Android plumbing → 29 Android catalog ∥ 30 iOS spike + plumbing + catalog) → 31 formats/PDF → 32 DE/FR/ES translations → 33 legal/store + human gate. Translation waits for both catalog migrations so strings are translated once, into final keys.
- [Roadmap v2.1]: Branch split — Android on `claude/endo-android-version-e608a7`, iOS on a branch from `main`. Both-platform phases (26, 27, 31, 32, 33) land in two halves; shared `l10n/` files stay byte-identical on both lines; the Swift halves of 26 + 27 must be on the iOS line before Phase 30.
- [Roadmap v2.1]: Cross-platform LANG-03/04/05 + TRNS-05 are owned by Phase 30 (expected to close after Phase 28; its spike may re-word LANG-04's iOS clause). Phase 28 builds the Android half under a **Cross-platform** field; re-open the four if Phase 30 ever completes before Phase 28 verifies.
- [Research v2.1]: Engine contract is ADDITIVE — engines keep byte-identical English `headline`/`detail` and add `InsightMessage`; gate `render(message, en) == headline` on all 157 fixtures; UI + PDF render only from the message; cache stores the message with a fingerprint version bump.
- [Research v2.1]: Android per-app language WITHOUT AppCompat (framework `LocaleManager` on 33+, app-owned controller on 26–32, tag in SharedPreferences read synchronously); fallback AppCompat 1.7.1 if the API 30 check fails. iOS in-app override gated by a 1-day spike (fallback: Settings deep-link + re-word LANG-02); do not write `AppleLanguages` by default.
- [Research v2.1]: Shared dir is `l10n/` (`glossary.json`, `insight-messages.json`, `claims-policy.{de,fr,es}.json`, `golden/`); English forbidden lists stay in code unchanged (the drift test regex-parses the Swift declarations). Register fixed by SAFE-03: DE "du", FR "vous", ES "tú".
- [Carried invariants]: absence ≠ 0, the single `HonestyGate`, the ClaimsPolicy copy choke-point, no-`INTERNET` manifest, read-only Health Connect/HealthKit, zero analytics — all stay green through v2.1. Substring traps are real ("her" ⊂ "here", "cause" ⊂ "because") — the DE/FR/ES lists need allow-lists from day one.
- [Phase 26]: 26-01: DE certainty uses stems vorhersag/vorhergesag/vorherseh so allow token vorher is a pure EN-trap control
- [Phase 26]: 26-01: selfTest first-hit {category, term} fixtures verified against a reference matcher of the README spec; 26-02 Kotlin matcher must reproduce them
- [Phase 26]: 26-01: SAFE-01..03 left unchecked until the failing tests land in 26-02/26-03 (plan delivers data only)
- [Phase 26]: 26-02: No seed-data tuning needed; all DE/FR/ES fixtures green against the Kotlin matcher on first run (policyRevision stays 1)
- [Phase 26]: 26-02: README clarifies policyRevision stays 1 through Phase 26 seed tuning and is bumped on every data change from the 26-03 freeze onward

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 30]: The iOS in-app language override is not a first-class API — the spike decides LANG-02's wording (and LANG-04's iOS clause) before any string moves.
- [Phase 28]: Android API 26–32 controller (cold start, recreation, WorkManager context, channel name) — the API 30 emulator check is the exit gate.
- [Phase 27]: Riskiest refactor — touches the 157-case parity anchor on both engines; stays additive so the anchor is never red mid-refactor.
- [Phase 31]: Changes the iOS `ReportPDFRenderer` — the v1.1 "PDF path byte-identical" guard must be re-baselined per locale, and the deferred host-flaky `ReportFlowUITests` PDF re-run must happen AFTER Phase 31 on a clean host.
- [Phases 29/31]: JVM (JDK CLDR) and device (ICU) date output differ — JVM tests assert locale selection/structure, not exact cross-runtime strings.
- [PORT-01]: iOS has no encrypted importer — the "on the other platform" clause is covered as format/language neutrality only; confirm that reading or re-word PORT-01.
- [Phase 33]: Binding legal language for consent + privacy policy (all equal vs German vs English) needs legal counsel.
- [v2.1 release]: Named native-speaker + legal review per language is human-gated — no test or document may mark it done.
- [v1.0 carried]: App Store 5.1.3(ii) private-CloudKit-health-storage reviewer interpretation remains the one iOS go/no-go.

## Deferred Items

Items carried forward from previous milestone closes (release/device/human-gated, unchanged by v2.1):

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| iOS release setup | Set `DEVELOPMENT_TEAM`, deploy CloudKit schema Development→Production, finalize bundle id (04-HUMAN-UAT Part A) | Pending user (before App Store release) | 04-03 | v1.0 |
| iOS device-UAT | DATA-02/03/04 multi-device sync on ≥2 iCloud devices (04-HUMAN-UAT Part B) + P-1 sign-out acknowledgement | Pending user | 04-03 | v1.0 |
| iOS review | Paste the 5.1.3(ii) review note into App Store Connect | Pending (before submission) | 04-03 | v1.0 |
| iOS device checks | v1.0/v1.1 on-device feel/legibility/VoiceOver checks + host-flaky `ReportFlowUITests` re-run | Pending (before release; re-run after Phase 31) | v1.1 close | v1.1 |
| Android release | Play org account + D-U-N-S, hosted privacy-policy URL, app-identity placeholders, Console forms, pre-launch report on Android 13-/14+ devices (`docs/release/RELEASE-CHECKLIST.md`) | Pending user (before Play submission) | v2.0 close | v2.0 |

## Quick Tasks Completed

| ID | Task | Status | Date |
|----|------|--------|------|
| 260610-sng | Wire the "Bloom" app icon into the iOS app | complete ✓ | 2026-06-10 |
| 260615-erj | Accessible human-silhouette body map on iOS (commit `b535f6c`) | complete ✓ (verified) | 2026-06-15 |
| 260708-u6d | Android port plan doc (`.planning/research/ANDROID-PORT-PLAN.md`) | complete ✓ | 2026-07-08 |

## Session Continuity

Last session: 2026-10-31T14:25:23.470Z
Stopped at: Completed 26-02-PLAN.md
Resume file: None

## Operator Next Steps

- Review and approve the v2.1 roadmap (`.planning/ROADMAP.md`)
- Plan the first phase: `/gsd-plan-phase 26` (research flagged — per-language claims lists)
- Create the iOS branch from `main` before Phase 26's Swift half lands
- After Phase 27: Phases 28–29 (Android) and Phase 30 (iOS) can run in parallel
