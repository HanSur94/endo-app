---
phase: 02-core-symptom-cycle-bleeding-logging
plan: 02
subsystem: logging-ui
tags: [swiftui, swiftdata, repository, mv-pattern, daylog, xcuitest, tdd, terminology]

# Dependency graph
requires:
  - phase: 02-core-symptom-cycle-bleeding-logging
    provides: "SchemaV2 (SymptomReading/PainPoint/BleedingEvent.clots) + Domain enums (SymptomType/BodyRegion/FlowIntensity) + RED DayNote/E2E scaffold (Plan 02-01)"
provides:
  - "Extended Repository: symptom/pain/bleeding/note/day CRUD (upsertSymptom/removeSymptom/setPainPoint/setBleeding/setNote/deleteDay)"
  - "Sendable BleedingDay value snapshot + bleedingDays()/isSuppressed() value reads (Plan 03 CycleMath / Plan 04 summary seam)"
  - "SwiftDataRepository.maxNoteLength (=280) note bound; severity clamp 0...10 at the seam"
  - "HomeView (replaces WalkingSkeletonView) — @Query reverse-chronological day history + 'Log today' + Settings route + reserved cycleSummary slot"
  - "DayLogEditorView — multi-symptom select + per-symptom SeverityControl + bleeding(+clots) + bounded note; pre-loads existing day state"
  - "SeverityControl — reusable 0-10 slider + mild/moderate/severe presets"
  - "E2E: testLoggingFlow + testLogToday_savesAndAppearsInHistory GREEN"
affects: [02-03, 02-04, phase-04-sync]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "MV (no ViewModel): views build a SwiftDataRepository from the injected modelContext and write through it"
    - "Calendar-based day-bucketing (startOfDay / isDate(_:inSameDayAs:)) — no epoch math"
    - "Value-snapshot read seam: @Model rows -> Sendable BleedingDay so pure CycleMath stays SwiftData-free"
    - "DayEntry as the per-day anchor — setNote always creates it so symptom/bleeding-only days surface in the @Query history"
    - "Severity clamped + note bounded at BOTH the control and the Repository seam (defence-in-depth, T-02-08/T-02-07)"

key-files:
  created:
    - Sources/EndoApp/Features/Home/HomeView.swift
    - Sources/EndoApp/Features/DayLog/DayLogEditorView.swift
    - Sources/EndoApp/Features/DayLog/SeverityControl.swift
  modified:
    - Sources/EndoApp/Persistence/Repository.swift
    - Sources/EndoApp/App/AppGate.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/Persistence/Models/DayEntry.swift
    - Sources/EndoApp/Features/Settings/SettingsView.swift
    - Tests/EndoAppTests/DayNoteTests.swift
    - Tests/EndoAppUITests/LoggingFlowUITests.swift
    - Tests/EndoAppUITests/ConsentFlowUITests.swift
  deleted:
    - Sources/EndoApp/Features/Skeleton/WalkingSkeletonView.swift

key-decisions:
  - "maxNoteLength=280: a day note is a short jot, not a journal — bounded under a paragraph so a pathological 500-char paste truncates at the seam (the plan's behavior example)"
  - "DayEntry is the per-day history anchor; setNote always creates it on save so a symptom/bleeding-only day still appears in the home @Query(DayEntry) history"
  - "Editor routed as a .sheet(item: Date?) wrapping its own NavigationStack (a presented modal, not a nested push into the home stack) — avoids the Phase-1 nested-NavigationStack pitfall"
  - "BleedingDay kept Sendable + SwiftData-free as the Plan 03/04 read seam"

patterns-established:
  - "Views construct SwiftDataRepository(context: modelContext) inline — no ViewModel, no extra injection (D-12)"
  - "Consent-gate UI invariant test re-pointed to the live shell's identifiers when the shell is replaced (ConsentFlowUITests now drives HomeView)"

requirements-completed: [SYMP-01, SYMP-03, CYCL-01]

# Metrics
duration: ~12min
completed: 2026-06-08
---

# Phase 2 Plan 02: Day-Log Editor Vertical Slice Summary

**A consented user opens the real HomeView, taps "Log today", fast-selects endo symptoms with 0–10 severity, records a per-day bleeding flow (+clots) and a length-bounded note — all persisted through an extended Repository — and the day appears in a reverse-chronological history list. WalkingSkeletonView is retired.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-08T09:42:02Z
- **Completed:** 2026-06-08T09:54:18Z
- **Tasks:** 2
- **Files created/modified/deleted:** 3 created, 9 modified, 1 deleted

## Accomplishments
- Extended the `Repository` seam additively (D-12): `upsertSymptom` / `removeSymptom` / `setPainPoint` / `setBleeding` / `setNote` / `deleteDay`, plus the Sendable `BleedingDay` value snapshot and `bleedingDays()` / `isSuppressed()` value reads that Plan 03's pure `CycleMath` and Plan 04's cycle summary consume — kept SwiftData-free.
- Hardened the write seam: severity clamped `0...10` (T-02-08), note bounded to `maxNoteLength` + empty→nil (T-02-07), `assertConsented()` on every method (T-02-06/V4), no health-value logging (T-02-09).
- Replaced `WalkingSkeletonView` with the real `HomeView`: reverse-chronological `@Query` day history (rows with a neutral symptom/bleeding summary line via `Terminology`), a prominent "Log today" entry, the gear→Settings route, and a reserved `home.cycleSummary` slot for Plan 04.
- Built `DayLogEditorView` (multi-symptom chips → per-symptom `SeverityControl`, bleeding flow + clots, bounded note) and the reusable `SeverityControl` (0–10 slider + mild/moderate/severe presets). The editor pre-loads existing day state on appear — the edit affordance Plan 04 reuses.
- Turned `DayNoteTests` GREEN (note persistence + length bound + the rest of the write seam) and drove a real end-to-end UI slice (`testLogToday_savesAndAppearsInHistory`) GREEN: consent → Log today → fatigue@severity + medium bleeding + note → Save → the day appears as `home.dayRow.0`.

## Task Commits

1. **Task 1: Extend Repository with symptom/pain/bleeding/note/day CRUD + value-snapshot reads (D-12)** — `3d44ba1` (feat, TDD)
2. **Task 2: HomeView + SeverityControl + DayLogEditorView wired into AppGate.consented; WalkingSkeletonView retired** — `9f3f2bf` (feat, TDD)

## Files Created/Modified
- `Sources/EndoApp/Persistence/Repository.swift` — additive Phase-2 CRUD + `BleedingDay`/`bleedingDays()`/`isSuppressed()` + `maxNoteLength`; Calendar-based day-bucketing; clamp/bound at the seam.
- `Sources/EndoApp/Features/Home/HomeView.swift` — real home shell (`@Query` history, Log today, Settings, cycleSummary slot).
- `Sources/EndoApp/Features/DayLog/DayLogEditorView.swift` — the fast day-log editor.
- `Sources/EndoApp/Features/DayLog/SeverityControl.swift` — reusable 0–10 severity control.
- `Sources/EndoApp/App/AppGate.swift` — `.consented` now renders `HomeView()`.
- `Sources/EndoApp/App/EndoAppApp.swift` / `Models/DayEntry.swift` / `Features/Settings/SettingsView.swift` — stale `WalkingSkeletonView` comments updated.
- `Tests/EndoAppTests/DayNoteTests.swift` — RED placeholder → real write-seam tests (GREEN).
- `Tests/EndoAppUITests/LoggingFlowUITests.swift` — added the full-slice `testLogToday_savesAndAppearsInHistory`.
- `Tests/EndoAppUITests/ConsentFlowUITests.swift` — re-pointed the consent-gate invariant test to HomeView identifiers.
- `Sources/EndoApp/Features/Skeleton/WalkingSkeletonView.swift` — **deleted** (retired).

## Decisions Made
- **maxNoteLength = 280:** a day note is a short jot; bounding under a paragraph makes the plan's "500-char paste truncates" behavior meaningful and gives a real DoS bound (T-02-07).
- **DayEntry as the per-day anchor:** `setNote` always creates the day's `DayEntry`, so even a symptom/bleeding-only save surfaces in the home `@Query(DayEntry)` history.
- **Editor as a `.sheet` modal** (its own `NavigationStack`) rather than a push into the home stack — a presented modal is not a nested push, so the Phase-1 nested-NavigationStack pitfall is avoided.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Phase-1 ConsentFlowUITests referenced retired `skeleton.*` identifiers**
- **Found during:** Task 2 (after re-pointing AppGate to HomeView and deleting WalkingSkeletonView)
- **Issue:** `ConsentFlowUITests.testConsentGateFlow` (the ONBD-01 consent-gate invariant E2E) asserted against `skeleton.logButton` / `skeleton.countLabel` / `skeleton.settingsButton`, which no longer exist once the skeleton is retired — the test failed (no longer proving the gate invariant).
- **Fix:** Re-pointed it to the live shell's identifiers (`home.logTodayButton` / `home.settingsButton`) and replaced the skeleton's count-increment step with a real "log a day via the editor → it appears in history" step. The invariant under test (no logging UI before consent; re-gate on withdrawal) is unchanged.
- **Files modified:** Tests/EndoAppUITests/ConsentFlowUITests.swift
- **Verification:** ConsentFlowUITests GREEN (1 test, 0 failures).
- **Committed in:** `9f3f2bf` (Task 2 commit)

**2. [Rule 3 - Blocking] E2E full-slice test needed scroll-into-view for off-screen controls**
- **Found during:** Task 2 (running `testLogToday_savesAndAppearsInHistory`)
- **Issue:** Selecting a symptom expands its `SeverityControl`, pushing `daylog.bleeding.medium` and `daylog.note` below the fold; XCUITest's `.tap()` failed with "No matches found".
- **Fix:** Added a bounded `swipeUp()` loop to bring each control into view before interacting — a test-harness fix, not a product change.
- **Files modified:** Tests/EndoAppUITests/LoggingFlowUITests.swift
- **Verification:** testLogToday_savesAndAppearsInHistory GREEN; screenshots logging-03-editor / logging-04-history captured.
- **Committed in:** `9f3f2bf` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking). No architectural changes; no scope creep.

## Threat-Model Compliance
- **T-02-06 (logging UI before consent):** `HomeView` constructed only in `AppGate.consented`; `assertConsented()` on every Repository method (11 call sites). Verified by grep + ConsentFlowUITests.
- **T-02-07 (unbounded note):** `setNote` bounds to `maxNoteLength` and stores empty as nil. Verified by `DayNoteTests.dayNote_isLengthBounded` / `_emptyStoresNil`.
- **T-02-08 (severity out of range):** clamped `0...10` in both `SeverityControl` and the Repository seam. Verified by `DayNoteTests.upsertSymptom_clampsSeverity`.
- **T-02-09 (health-value leakage via logs):** grep confirms no `print`/`os_log`/`Logger`/`NSLog` in the editor/repository write paths.
- **Copy posture (D-14):** bleeding noun resolves through `Terminology`; no hard-coded "period"/gendered tokens in the new views (grep clean; the only "period" match is a comment forbidding it).

## Test Results
- `EndoAppTests/DayNoteTests` — **GREEN** (9 tests).
- `EndoAppTests/SymptomLoggingTests`, `SchemaMigrationTests`, `CopyAuditTests`, `SchemaRoundTripTests` — **GREEN** (unchanged).
- `EndoAppUITests/LoggingFlowUITests/testLoggingFlow` — **GREEN**.
- `EndoAppUITests/LoggingFlowUITests/testLogToday_savesAndAppearsInHistory` — **GREEN** (new full-slice E2E).
- `EndoAppUITests/ConsentFlowUITests` — **GREEN** (re-pointed).
- `xcodebuild build` (iPhone 16) — **BUILD SUCCEEDED**.
- Still RED by design (downstream): `CycleMathTests` (Plan 02-03), `BodyMapBindingTests` / `EditDeleteTests` and the `testSuppressedModeShowsNoCycleSummary` E2E + the cycle-copy half of `CopyAuditTests` (Plan 02-04).

## Known Stubs
- `home.cycleSummary` is an intentional empty placeholder slot — Plan 02-04 fills it with the suppressed / derived-cycle summary (documented in the plan; not a goal-blocking stub for this slice).
- `setPainPoint` / `deleteDay` are implemented and tested at the Repository level but not yet wired to UI — consumed by Plan 02-04 (body map + edit/delete). Intentional, per plan.

## Next Phase Readiness
- The Repository write seam + the Sendable `BleedingDay` / `isSuppressed` reads are in place for Plan 02-03 (pure `CycleMath`) and Plan 02-04 (body map, edit/delete, cycle summary).
- `DayLogEditorView` pre-loads existing day state — the edit host Plan 04 reuses.
- No blockers.

## Self-Check: PASSED

All three created files exist on disk; `WalkingSkeletonView.swift` is deleted; both task commits (`3d44ba1`, `9f3f2bf`) are present in git history.

---
*Phase: 02-core-symptom-cycle-bleeding-logging*
*Completed: 2026-06-08*
