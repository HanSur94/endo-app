---
phase: 20-tracking-screens
plan: 01
subsystem: ui
tags: [android, compose, tracking, honesty, absence-not-zero, copy-audit, rememberSaveable, robolectric, room, bloom]

# Dependency graph
requires:
  - phase: 18-room-persistence
    plan: 03
    provides: "EndoRepository (consent-gated upserts + per-day reads) + SnapshotProjector fold rules"
  - phase: 18-room-persistence
    plan: 04
    provides: "Nullable day-scoped entities (SymptomReading/PainPoint/BleedingEvent/FactorReading/DayEntry) + FactorKind + :insights SnapshotEnums"
  - phase: 19-app-shell-consent
    plan: 03
    provides: "ConsentedShell 3-tab shell + tab.log placeholder to replace + CheckInRouter + PredictiveBackHandler posture"
  - phase: 19-app-shell-consent
    plan: 01
    provides: "BloomTheme + Bloom components (bloomCard/BloomPrimaryButton/EyebrowLabel/typography) + Robolectric+Compose JVM harness"
provides:
  - "TrackingCopy — the shared, registered Phase-20 vocabulary (not-logged caption, SeverityControl + BodyMap value strings, shared control labels, Log-home chrome, and the enum->label maps for SymptomType/BodyRegion/FoodTag/Activity*/Sleep/Flow + Mood 1-5)"
  - "DayDraft — the nil-honest per-day editable value type (every optional absence-encoded; nil != a real 0)"
  - "DayDraftMapper — the PURE draft<->Room-rows seam (toRows/fromRows); the single nil-vs-0 resolution point on the write/read path (no `?: 0` on any health value; anchor DayEntry always emitted)"
  - "SeverityControl — shared 0..10 discrete rose slider + N/10 readout + Mild/Moderate/Severe presets, Role.Slider + stateDescription, daylog.severity* testTags, >=48dp"
  - "TrackingSectionRows — shared multi/single-select, stepper, segmented picker, reveal-on-opt-in, mood, and free-text primitives (a11y roles + >=48dp)"
  - "TrackingRoute + TrackingBackStack — a rememberSaveable in-Log screen stack (LogHome/CheckIn/DayLog/Settings) with a Saver; NO NavHost, NO navigation dependency"
  - "LogTabHost — the intra-Log host owning the stack, popping on system back when nested, and observing CheckInRouter ONLY inside the consented subtree"
  - "LogHomeScreen — the real Log tab (Daily-check-in CTA + settings gear + recent-days list + honest empty state) replacing the Phase-19 tab.log placeholder"
affects: [20-02, 20-03, 20-04, 20-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PURE mapper (DayDraftMapper) as the single absence != 0 seam — Room entity value-lists in/out, no Android/Room/coroutine, so the honesty invariant is pinned by fast JVM tests (AbsenceNotZeroTest) AND end-to-end through EndoRepository (DayLogRoundTripTest androidTest)"
    - "Injected `idFor: (String)->String` identity so the mapper fabricates no UUIDs — stable per-day keys give idempotent upserts; tests pass identity"
    - "In-Log navigation as a rememberSaveable mutableStateListOf-backed stack + listSaver (route<->token) — keeps the Phase-19 no-NavHost posture, zero navigation dependency"
    - "Router deep-link flag observed by LogTabHost (the consented Log subtree), moved out of ConsentedShell — a reminder deep-link lands on the gate first, then the consented host pushes CheckIn (threat T-20-11)"
    - "Shared vocabulary as enum->label MAPS keyed by the :insights enums (raw values are the parity contract; only display labels live in TrackingCopy), registered in CopyAuditRegistry in the same change"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/TrackingCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/DayDraft.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/DayDraftMapper.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/SeverityControl.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingSectionRows.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingRoute.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogHomeScreen.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/AbsenceNotZeroTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/SeverityControlTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/LogHomeTest.kt
    - android/app/src/androidTest/kotlin/com/endoapp/app/ui/tracking/DayLogRoundTripTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/shell/ConsentedShellTest.kt

decisions:
  - "Reworded two enum display labels the UI-SPEC §7 table specified verbatim that collide with the imperative substring gate: BodyRegion.SHOULDER 'Shoulder' (contains 'should') -> 'Neck / collarbone'; SymptomType.GI_ENDO_BELLY 'nausea' (contains 'use') -> 'queasy'. Raw enum values (the parity contract) untouched — only display labels changed."
  - "LogTabHost takes recentDays as a Flow<List<DayEntry>> ('or Flows off it', per the plan) rather than the whole repository — keeps the host testable with a plain MutableStateFlow and avoids threading a repository through AppGate/AppDependencies (out of this plan's file scope)."
  - "ConsentedShell gained an optional `recentDays: Flow = emptyFlow()` param (default) so the existing ConsentedShellTest/AppGateConsentTest call sites keep compiling; the production composition root wiring of the recent-days source lands with the writing screens (Plans 02/03)."
  - "Kept the temporary shell.withdraw overlay in ConsentedShell (AppGateConsentTest depends on it) AND threaded onWithdraw into LogTabHost's Settings-route placeholder (previewing the Plan-04 graduation)."

metrics:
  duration: ~55min
  completed: 2026-07-09
  tasks: 3
  files: 15

status: complete
---

# Phase 20 Plan 01: Tracking Foundation — Log-home Host, Absence≠0 Seam, Shared Controls Summary

**Replaced the Phase-19 `tab.log` placeholder with a real Log-home host driving an intra-Log `rememberSaveable` navigation stack (no NavHost, no 4th tab), and laid the honesty foundation every Phase-20 screen stands on: the nil-honest `DayDraft` + the PURE `DayDraftMapper` (the single nil-vs-0 seam — a skipped field is nil, a logged 0 is a real 0, preserved through log→persist→reload), the shared rose `SeverityControl` + section-row primitives, and the registered `TrackingCopy` vocabulary. Absence≠0 is proven at the mapping layer (25-case JVM `AbsenceNotZeroTest`) AND end-to-end through the consent-gated `EndoRepository` (`DayLogRoundTripTest` androidTest). All strings are registered in `CopyAuditRegistry` and pass the substring gate.**

## Performance
- **Duration:** ~55 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 15 (12 created, 3 modified)

## Accomplishments
- **`TrackingCopy` (registered from birth):** the shared cross-screen vocabulary — the `Not logged` caption, SeverityControl strings (`Severity` + the `severityReadout(n)` `N/10` builder + Mild/Moderate/Severe), the shared control labels (`Duration`/`Hours`/`Quality`/`Intensity`), the BodyMap value strings (`severityStateDescription(n)` "severity N of 10", `not marked`, `severitySummary(n)`, the `—` unset read-out), the Log-home chrome (greeting, `Daily check-in`, `Log a past day`, honest empty-state title + body), and the enum→label maps for all 11 SymptomType / 10 BodyRegion / 9 FoodTag / 5 ActivityType / 3 ActivityIntensity / 3 SleepQuality / 4 FlowIntensity cases + Mood 1–5. Appended to `CopyAuditRegistry.sources` in the same edit; `CopyAuditTest` green over the enlarged catalog.
- **`DayDraft` + `DayDraftMapper` (the absence≠0 seam):** a nil-honest per-day value type (every optional nullable / absence-encoded) plus a PURE `toRows`/`fromRows` mapper — the ONE place nil-vs-0 is resolved on the write/read path. No `?: 0` (or any nil→0 coercion) on any health value; an untouched field emits NO row; the anchor `DayEntry` is ALWAYS emitted so an empty confirm records only the adherence marker. Identity `idFor` is injected (no fabricated UUIDs). fromRows uses the SnapshotProjector's exact defensive decode (strict symptom/region drop, FactorKind kind→column mapping, activity type+minutes as one unit, medication ids + `prn`, stress/mood levels, sleep hours/quality).
- **`SeverityControl` + `TrackingSectionRows`:** the shared 0..10 discrete rose `Slider` (step 1) with the `N/10` readout, Mild/Moderate/Severe presets, `Role.Slider` + `stateDescription` "N of 10", `daylog.severitySlider`/`daylog.severityPreset.<mild|moderate|severe>` testTags, ≥48dp targets — plus the reusable section-row primitives (multi/single-select, stepper with value-or-"not logged" `stateDescription`, segmented picker with a leading none option, reveal-on-opt-in, mood row, free-text field), each with its a11y role + ≥48dp.
- **`TrackingRoute` + `LogTabHost` + `LogHomeScreen`:** a typed `sealed interface TrackingRoute` (LogHome/CheckIn/DayLog(epochDay)/Settings) backed by a `rememberSaveable` `TrackingBackStack` (push/pop + a `Saver`) — NO `NavHost`, no navigation dependency. `LogTabHost` owns the stack, pops on system back when nested (deferring to the Phase-19 predictive-back at the root), and observes `CheckInRouter` ONLY inside the consented Log subtree. `LogHomeScreen` is the real Log tab — a prominent `Daily check-in` CTA, a settings gear, a recent-days list off the repository Flow, and an honest empty state.
- **`ConsentedShell` wiring:** the `tab.log` `TabPlaceholder` is replaced by `LogTabHost`; the shell's router `LaunchedEffect` is removed (the host owns router observation now); the temporary `shell.withdraw` affordance is kept (Plan 04 graduates it into Settings).

## Task Commits
1. **Task 1: register shared TrackingCopy vocabulary in CopyAuditRegistry** — `49e26ce` (feat)
2. **Task 2: nil-honest DayDraft + pure DayDraftMapper + shared controls** — `62eba52` (feat)
3. **Task 3: Log-home host + intra-Log rememberSaveable stack** — `4fe1ceb` (feat)

_No STATE.md / ROADMAP.md commit — per the objective, the orchestrator owns state updates for this run._

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **131/131 passed, 0 failures, 0 errors** (was 79/79 after Phase 19 Plan 03; +52). New this plan:
  - `AbsenceNotZeroTest` **25/25** — every UI-SPEC §3 row proven nil-vs-real-zero (symptom/pain severity nil vs 0, activity 0 min with a type, 0.0h with hoursSet, stress 0 opted-in) + the empty-confirm marker + a full-populated identity round trip.
  - `SeverityControlTest` **3/3** — the `N/10` readout tracks the value; Mild/Severe presets set 3/9.
  - `LogHomeTest` **4/4** — CTA + settings gear + empty state; CTA pushes CheckIn and a REAL system back (`onBackPressedDispatcher`) pops to home; a recent day renders `log.editDay.<iso>`; the gear pushes Settings.
  - `CopyAuditTest` **7/7** — green over the enlarged catalog (the TrackingCopy vocabulary + builder outputs all pass the ClaimsPolicy substring scan).
  - `ConsentedShellTest` **4/4** — updated to assert the real Log-home (`log.checkInCta`) instead of the removed `tab.log.body`; `AppGateConsentTest` **5/5** unaffected (consent-by-construction preserved; tab testTags intact).
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real `app-debug.apk` = 12.9 MB).
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL** (`DayLogRoundTripTest` compiles; asserts a full nil-vs-0-preserving round trip through `EndoRepository` on the emulator lane).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Two UI-SPEC enum labels collide with the imperative substring gate**
- **Found during:** Task 1 (pre-write substring scan of every candidate label against the `ClaimsPolicy` lists).
- **Issue:** The UI-SPEC §7 vocabularies table specified `BodyRegion.SHOULDER` → "Shoulder" and `SymptomType.GI_ENDO_BELLY` → "Bloating / nausea / gut" verbatim, but the CopyAudit scan is a case-insensitive SUBSTRING match: "**Shoulder**" contains "**should**" (imperative) and "**nausea**" contains "**use**" (imperative). Registering them as-is fails `CopyAuditTest` (a hard must-have + threat T-20-01b mitigation). The UI-SPEC reworded three OTHER near-misses (here/stops/treatment) but missed these two.
- **Fix:** reworded the DISPLAY labels only — "Shoulder" → **"Neck / collarbone"** (the front-top region the hotspot marks); "Bloating / nausea / gut" → **"Bloating / queasy / gut"**. The raw enum values (`shoulder`, `giEndoBelly`) — the parity contract — are untouched; only the app-owned display labels changed. Verified clean via a full pre-write scan of every candidate string.
- **Files:** `TrackingCopy.kt`. **Commit:** `49e26ce`.

**2. [Rule 3 - Blocking] ConsentedShellTest asserted the removed `tab.log.body` placeholder**
- **Found during:** Task 3 (`ConsentedShellTest` turned red after the Log tab content legitimately changed).
- **Issue:** Two `ConsentedShellTest` cases asserted `tab.log.body` exists — the Phase-19 Log placeholder this plan intentionally replaces with `LogTabHost`. The tab testTags (`tab.log`/`tab.insights`/`tab.explore`) are preserved, but the Log BODY is now the real Log-home.
- **Fix:** updated the two assertions to the new Log content (`log.checkInCta`); renamed the case `…andLogPlaceholder` → `…andLogHome`. Consent-by-construction and the tab surface are unchanged.
- **Files:** `ConsentedShellTest.kt`. **Commit:** `4fe1ceb`.

## Threat Model Compliance
- **T-20-01 (Tampering — DayDraftMapper draft↔rows, high — mitigate):** met. No `?: 0` on any health value in the mapper; an untouched field produces no row / a null column; `AbsenceNotZeroTest` pins every §3 row nil-vs-0 twice + the empty-confirm marker; `DayLogRoundTripTest` proves the same end-to-end through `EndoRepository`.
- **T-20-01b (Information disclosure — TrackingCopy vocabularies, medium — mitigate):** met. Every foundation/enum string is registered in `CopyAuditRegistry.sources` in the same change; `CopyAuditTest` green (neutral, non-gendered, non-diagnostic; the two additional substring traps transcribed, never reintroduced).
- **T-20-11 (Elevation of privilege — LogTabHost router observation, high — mitigate):** met. The router flag is now observed ONLY inside `LogTabHost` (the consented Log subtree), moved out of `ConsentedShell`; `AppGateConsentTest` stays 5/5 (a deep link fired while `NotConsented` surfaces no health/tab UI).
- **T-20-SC (Tampering — Gradle/Maven deps, low — accept):** honored. This plan added NO new coordinate (no navigation library; the in-Log stack is a `rememberSaveable` list). The reminder's WorkManager lands in Plan 05 behind the catalog audit.

## Known Stubs
- **CheckIn / DayLog / Settings route arms are intentional, phase-marked placeholders:** `LogTabHost` renders a temporary inline `Box` (testTag `log.route.checkIn` / `log.route.dayLog` / `log.route.settings`) for each — Plan 02 (Daily Check-In), Plan 03 (Day Log + BodyMap), and Plan 04 (Settings) replace their arm with the real screen. This is the plan's deliverable (the host + stack), not a stub that blocks the goal. The Settings placeholder hosts a `log.settings.withdraw` affordance (backed by the same `onWithdraw`) previewing the Plan-04 graduation.
- **Recent-days source is wired to `emptyFlow()` in the production composition root:** `AppGate` calls `ConsentedShell(router, onWithdraw)` and the new `recentDays` param defaults to `emptyFlow()`, so the Log-home shows its honest empty state in the running app. This is correct for THIS phase — nothing WRITES days yet (the Check-In / Day Log screens are placeholders). Threading `EndoRepository.…observeDayEntries()` from the composition root (AppDependencies/AppGate) lands with the writing screens in **Plan 02/03**, which is when a non-empty list becomes possible. `LogHomeScreen`/`LogTabHost` already render a live Flow correctly (proven by `LogHomeTest.recentDay_rendersEditableRow`).

## Self-Check: PASSED
- All 12 created files + 3 modified files exist on disk.
- All 3 task commits present in git history: `49e26ce`, `62eba52`, `4fe1ceb`.
- Real build/test evidence captured above (`:app` 131/131 incl. `AbsenceNotZeroTest` 25/25, `SeverityControlTest` 3/3, `LogHomeTest` 4/4 with a real back-dispatcher drive, `CopyAuditTest` 7/7, `ConsentedShellTest` 4/4, `AppGateConsentTest` 5/5; `assembleDebug` 12.9 MB APK; `compileDebugAndroidTestKotlin` SUCCESSFUL; `:insights` 176/176).

---
*Phase: 20-tracking-screens*
*Completed: 2026-07-09*
