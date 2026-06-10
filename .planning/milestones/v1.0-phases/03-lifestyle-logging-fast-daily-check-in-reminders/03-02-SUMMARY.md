---
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
plan: 02
subsystem: ui
tags: [swiftui, swiftdata, day-editor, factor-logging, honesty, accessibility, xcuitest]

# Dependency graph
requires:
  - phase: 03-lifestyle-logging-fast-daily-check-in-reminders
    plan: 01
    provides: "Domain factor enums (FoodTag/ActivityType/ActivityIntensity/SleepQuality), Sendable FactorDay snapshot, Repository factor + saved-med CRUD (setFood/setActivity/setSleep/setStress/setMood/setMedicationTaken/setPRN/savedMedications/factorDays), SeverityControl reusable for stress"
  - phase: 02-core-symptom-cycle-bleeding-logging
    provides: "DayLogEditorView Form-section pattern, SeverityControl (0–10), LoggingFlowUITests XCUITest harness, ClaimsPolicy copy choke-point"
provides:
  - "Six lifestyle-factor Sections in DayLogEditorView: food (multi-select tags + free-text), activity (type/duration/intensity), sleep (hours/quality), medication (per-day taken toggles + PRN), stress (reveal-on-opt-in SeverityControl), overall day-mood (1–5)"
  - "nil!=0 honesty surfaced in the UI: stress/mood drafts are Int?, untouched sections read 'Not logged', never a fabricated 0"
  - "Factor a11y identifiers daylog.factor.* the check-in/insights surfaces can reuse"
  - "LoggingFlowUITests.testFactorLogging_selectsAndPersists — LIFE-01/02/03 proven end-to-end (select + nil!=0 reveal + persistence across re-open)"
affects: [03-03, 03-04, "Phases 5-8 insights engine"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reveal-on-opt-in factor control (stress): the SeverityControl appears only after a 'Log stress' tap so the draft stays nil (absence != 0)"
    - "Optional-tagged segmented Picker (intensity/quality) with a '—' not-set tag so nil is representable in a SwiftUI Picker"
    - ".accessibilityElement(children: .contain) on the mood row so a container id coexists with per-value child ids (VoiceOver + E2E addressable)"

key-files:
  created:
    - .planning/phases/03-lifestyle-logging-fast-daily-check-in-reminders/03-02-SUMMARY.md
  modified:
    - Sources/EndoApp/Features/DayLog/DayLogEditorView.swift
    - Tests/EndoAppUITests/LoggingFlowUITests.swift

key-decisions:
  - "Stress reveal uses a 'Log stress' row that swaps for the SeverityControl + 'Clear stress' on opt-in; the Int? draft only becomes non-nil on tap (T-03-06 honesty held in the binding)"
  - "Segmented Pickers (activity intensity, sleep quality) carry an explicit '—' not-set tag so 'not logged' is a real Picker state, not a coerced default"
  - "Sleep 'logged 0h' vs 'not logged' distinguished by a sleepHoursSet flag (the Double can't be nil in a Stepper) — only a touched Stepper or a set quality counts as logged"
  - "Mood uses a 1–5 symbol+text row labelled 'Overall mood for the day', kept distinct from the Phase-2 'Low mood' acute symptom (D-08)"

requirements-completed: [LIFE-01, LIFE-02, LIFE-03]

# Metrics
duration: 16min
completed: 2026-06-08
---

# Phase 3 Plan 02: Lifestyle Factor Logging UI Slice Summary

**The detailed day editor now logs every Phase-3 lifestyle factor end-to-end — food tags + free-text, activity type/duration/intensity, sleep hours/quality, per-day medication (taken + PRN), reveal-on-opt-in stress, and an overall 1–5 day-mood — all wired to the Plan 01 Repository setters, with the nil!=0 honesty contract surfaced in the UI and proven by a new LIFE-01/02/03 XCUITest.**

## Performance

- **Duration:** ~16 min
- **Tasks:** 2
- **Files modified:** 2 (1 SUMMARY created)

## Accomplishments
- Added six factor `Section`s to `DayLogEditorView`'s existing `Form`, following the symptom/bleeding patterns (SF-Symbol selection state, revealed sub-controls, `daylog.factor.*` a11y ids).
- Extended `loadExistingState()` to hydrate the factor drafts from `repo.factorDays()` (today's `FactorDay`) and `repo.savedMedications()`, and `save()` to commit every factor via the Plan 01 setters.
- Held the honesty discipline in the bindings: stress + mood drafts are `Int?`; an untouched section renders a `.secondary` "Not logged" placeholder and an unset control, never a coerced 0.
- All controls are text/symbol/numeric (never color-only): activity intensity, sleep quality, and mood are text-labelled; duration/hours use `.monospacedDigit()`; mood carries SF Symbols + ≥44pt targets.
- Extended `LoggingFlowUITests` with `testFactorLogging_selectsAndPersists`, asserting factor selection, the stress nil!=0 reveal (no control before opt-in; control shown after), and persistence of the food tag + logged stress across an editor re-open.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add lifestyle factor sections to DayLogEditorView** — `76271b9` (feat)
2. **Task 2: Extend LoggingFlowUITests to assert factor logging persists (LIFE-01/02/03 E2E)** — `c2bac70` (test)

## Files Created/Modified
- `Sources/EndoApp/Features/DayLog/DayLogEditorView.swift` — six factor Sections + factor draft `@State` + `medTakenBinding`/`stressBinding` + extended `loadExistingState()`/`save()`.
- `Tests/EndoAppUITests/LoggingFlowUITests.swift` — `testFactorLogging_selectsAndPersists` E2E + reserved-id doc-comment.

## Decisions Made
- **Stress reveal-on-opt-in:** a "Log stress" row (`daylog.factor.stress.log`) that, only on tap, swaps for the reused `SeverityControl` (`daylog.factor.stress`) plus a "Clear stress" button. The `stress: Int?` draft stays nil until tapped, so an untouched section never implies "0 = no stress" (T-03-06).
- **Optional-tagged segmented Pickers:** activity intensity and sleep quality are `.segmented` Pickers whose selection is `ActivityIntensity?` / `SleepQuality?`, with an explicit `Text("—").tag(.none)` so "not set" is a real Picker state rather than a coerced default.
- **Sleep logged-zero vs not-logged:** a `Stepper`'s bound `Double` can't be nil, so a `sleepHoursSet` flag (flipped by `.onChange`) distinguishes a deliberately logged "0.0 h" from "not logged"; `save()` passes `hours: nil` when neither hours nor quality were touched.
- **Mood distinct from the symptom:** a 1–5 symbol+text valence row headed "Overall mood for the day", semantically separated from the Phase-2 "Low mood" acute symptom (D-08).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Mood per-value buttons were not individually addressable**
- **Found during:** Task 2 (the E2E could not find `daylog.factor.mood.4`).
- **Issue:** The mood row carried `.accessibilityIdentifier("daylog.factor.mood")` on the `HStack`, which collapsed its five child buttons into a single accessibility element — breaking both VoiceOver per-value selection and the E2E lookup. The plan requires both the container id `daylog.factor.mood` AND per-value ids.
- **Fix:** Added `.accessibilityElement(children: .contain)` before the container id so the row stays addressable as `daylog.factor.mood` without swallowing the per-value `daylog.factor.mood.<value>` buttons.
- **Files modified:** `Sources/EndoApp/Features/DayLog/DayLogEditorView.swift`
- **Verification:** `testFactorLogging_selectsAndPersists` GREEN; full `LoggingFlowUITests` 4/4 GREEN.
- **Committed in:** `76271b9` (amended into the Task 1 view commit — it is a correctness fix to that file).

---

**Total deviations:** 1 auto-fixed (a11y addressability bug in this plan's own view code).
**Impact on plan:** Required for the mood control to be operable by VoiceOver and drivable by the E2E. No API or scope change — both factor ids the plan reserved are present.

## E2E / Test-harness notes (not deviations)
- The stress + mood SeverityControl/reveal elements are queried via their stable ids (`daylog.factor.stress.log` / `daylog.factor.stress.clear`) rather than the inner `daylog.severitySlider`, because the SeverityControl's outer `daylog.factor.stress` identifier supersedes the inner slider id in the accessibility tree. The reveal/persistence contract is asserted through the presence/absence of the "Log stress" vs "Clear stress" rows, which is the user-visible honesty signal.
- Segmented intensity is tapped via its text segment label ("Medium") — SwiftUI exposes `.segmented` Picker options as buttons.
- Medication-taken is intentionally NOT asserted in this suite: a fresh `-uitest-reset` launch has no saved medications, and saved-med management lands in Plan 03-03. Per the plan, that E2E is a mandatory assertion in the Plan 03-04 check-in suite.

## Known Stubs
None — every factor control is wired to a Plan 01 Repository setter on save and hydrated from the `FactorDay` snapshot on load. The empty-default draft state is the intentional, honest "not logged" state, not a stub.

## Issues Encountered
- Two iterations were needed on the E2E element lookups: the segmented intensity control is exposed as buttons (not a `segmentedControl` by id), and the stress SeverityControl's inner slider id is masked by the wrapper's `daylog.factor.stress` id. Both were resolved by querying user-visible, stably-identified elements; no production code change was needed beyond the mood a11y fix.

## Verification Evidence
- `xcodebuild build` — **BUILD SUCCEEDED**.
- `EndoAppTests/CopyAuditTests` — 9/9 GREEN (Phase-3 factor copy contains no forbidden verb/gendered token; the food section uses neutral "tags", never "trigger").
- `EndoAppUITests/LoggingFlowUITests` — 4/4 GREEN, including the new `testFactorLogging_selectsAndPersists` (factor select + nil!=0 stress reveal + persistence across re-open).

## Next Phase Readiness
- The primary lifestyle-logging surface is live and persists; Plans 03-03 (reminders + manage-meds in Settings) and 03-04 (fast check-in sheet) can build on the factor sections and the reserved `daylog.factor.*` ids.
- Once Settings manage-meds lands (03-03), the saved-med "taken today" toggles in this editor become exercisable, and the medication-taken E2E is delivered in the 03-04 check-in suite.

## Self-Check: PASSED

- `Sources/EndoApp/Features/DayLog/DayLogEditorView.swift` present; contains all `daylog.factor.*` ids, `var stress: Int?`, and the Plan 01 setters in `save()`.
- `Tests/EndoAppUITests/LoggingFlowUITests.swift` present; references `daylog.factor.food`, `daylog.factor.stress`, `daylog.factor.mood`.
- Commits `76271b9` (Task 1) and `c2bac70` (Task 2) verified in `git log`.
- Build + CopyAuditTests + LoggingFlowUITests all GREEN.

---
*Phase: 03-lifestyle-logging-fast-daily-check-in-reminders*
*Completed: 2026-06-08*
