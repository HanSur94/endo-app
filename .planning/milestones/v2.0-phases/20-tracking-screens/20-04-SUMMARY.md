---
phase: 20-tracking-screens
plan: 04
subsystem: ui
tags: [android, compose, settings, medications, soft-archive, treatment-state, consent-withdraw, copy-audit, robolectric, room, bloom]

# Dependency graph
requires:
  - phase: 20-tracking-screens
    plan: 01
    provides: "TrackingCopy vocabulary + AuditableCopy/CopyAuditRegistry substring gate; the intra-Log rememberSaveable stack + LogTabHost Settings arm to fill; Bloom section-row primitives"
  - phase: 20-tracking-screens
    plan: 03
    provides: "composition-root wiring (AppDependencies + AppGate binds seams in the Consented arm only); the injected-seam screen pattern (screens take suspend seams, never a DAO); LogPersistence; the ConsentedShell temp shell.withdraw affordance to graduate"
  - phase: 18-room-persistence
    plan: 02
    provides: "Medication (soft-archive via isArchived; never day-scoped) + TreatmentState (standing suppression; modeRaw) entities + MedicationDao (observeActive / allMedications / latestTreatment / upserts)"
  - phase: 19-app-shell-consent
    plan: 03
    provides: "PrivacyPolicyScreen (the Phase-19 policy the Privacy row opens); AppGate consent-by-construction routing; ConsentStore.withdraw (the re-gate); ShellCopy.WITHDRAW label"
provides:
  - "SettingsScreen — the Settings surface reached from the Log-home gear: a reserved reminders slot (Plan 05), manage-meds, a cycle-tracking-off toggle bound to TreatmentState, a Privacy row opening the Phase-19 PrivacyPolicyScreen, and a graduated destructive Withdraw-consent control behind a confirmation dialog that calls the real onWithdraw → the app re-gates"
  - "ManageMedsSection — the saved-med list (active only) + Add-a-medication action + per-med SOFT-archive behind a Remove confirmation (never hard-delete, so prior taken-history keyed by id stays valid)"
  - "AddMedicationSheet — the add-med ModalBottomSheet (Name required, Save-gated; Type/note optional); length bounded at the EndoRepository seam, not the UI"
  - "SettingsCopy — the registered Settings + meds vocabulary (substring-safe, incl. two strings reworded off the UI-SPEC's missed 'taken' ⊃ 'take'); WithdrawConsentCopy — the exempt destructive-consent legal register (will/stop), single-sourced like Disclaimer"
  - "EndoRepository med/treatment read seams — observeMedications (safe-by-construction) + gated allMedications + latestTreatment; the med write bounds free-text length at the seam"
  - "LogTabHost Settings arm renders the real SettingsScreen; ConsentedShell → LogTabHost thread the med/treatment seams; the temp shell.withdraw is removed"
affects: [20-05, 22]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Settings writes as injected suspend seams bound ONLY in AppGate's Consented arm (mirroring Plan 03) — SettingsScreen/ManageMedsSection stay DAO-free and testable off fakes; the TreatmentState construction (uuid + timestamp + suppressed/naturallyCycling) lives at the composition root, not the screen"
    - "SOFT-archive as the only removal path for a medication DEFINITION — Remove writes copy(isArchived=true) through upsertMedication; observeActive hides it while allMedications retains it, so a day that logged the med as taken (a FactorReading keyed by the med id) keeps its meaning; there is NO hard-delete seam for meds"
    - "Destructive-consent legal register kept EXEMPT from the curated CopyAudit substring scan — WithdrawConsentCopy (which legitimately carries will/stop) is single-sourced + rendered-and-asserted in the screen test, exactly as Disclaimer/PrivacyPolicyText are exempt with their own tests; the curated SettingsCopy stays substring-clean"
    - "Graduated re-gate control proven end-to-end — AppGateConsentTest drives Log-home gear → Settings → Withdraw consent → confirm → ConsentStore.withdraw and asserts the tab surface leaves composition; the Phase-19 temp shell.withdraw is removed and asserted absent"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/SettingsCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/ManageMedsSection.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/AddMedicationSheet.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/settings/MedicationTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/settings/SettingsScreenTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt

decisions:
  - "Reworded two UI-SPEC §7 strings the reword pass MISSED: the remove-med body and the no-meds-in-log line both carried 'taken', and 'taken' ⊃ the imperative 'take' — a CopyAudit substring failure (like Plan 01's Shoulder→should / nausea→use). Reworded to drop the substring: '…logged it as taken stay unchanged.' → '…already logged it stay unchanged.'; '…mark it taken each day.' → '…to log it each day.' Documented as a Rule 1 fix."
  - "The withdraw-consent DIALOG body (the UI-SPEC's 'will stop processing your health data' legal register) was placed in an EXEMPT constant (WithdrawConsentCopy), NOT the curated CopyAuditRegistry — mirroring Disclaimer/PrivacyPolicyText. Phase 19 only ever registered the withdraw BUTTON label (ShellCopy.WITHDRAW, clean); the dialog body never existed, and the UI-SPEC §7 explicitly says 'do NOT introduce a new stop into a freshly-curated string'. The body is pinned by a render+display assertion in SettingsScreenTest instead of the substring scan."
  - "Med/treatment reads were added to EndoRepository (observeMedications safe-by-construction like observeDayEntries; gated allMedications + latestTreatment) and the seams bound in AppGate — the plan scoped only the screen files, but a Settings screen that reads meds/treatment cannot exist without them (Rule 2). The free-text length bound the plan asked for 'at the EndoRepository seam' was added to upsertMedication (MAX 200 chars), keeping the UI free of the sole guard."
  - "Cycle-off treatment write lives at the AppGate seam, not the screen — SettingsScreen calls onSetSuppressed(Boolean) and AppGate constructs the timestamped TreatmentState (uuid + now + suppressed/naturallyCycling). Keeps the screen DAO-free and matches the Plan-03 seam posture; the SettingsScreenTest captures the boolean deterministically and proves the identical repo write + reload separately (avoiding an async Room-vs-teardown race)."

metrics:
  duration: ~40min
  completed: 2026-07-09
  tasks: 2
  files: 12

status: complete
---

# Phase 20 Plan 04: Medication Management + Settings + Graduated Withdraw-Consent Summary

**Ported medication management and the Settings surface onto the Phase-20 foundation. `SettingsScreen` (reached from the Log-home gear via the intra-Log stack) hosts, top-to-bottom: a RESERVED reminders slot marked for Plan 05 (this file imports NO notification/WorkManager/alarm API, keeping that surface cleanly separable); the `ManageMedsSection` (the active saved-med list + an "Add a medication" action opening the `AddMedicationSheet` — Name required, Save-gated — and a per-med SOFT-archive behind a "Remove this medication?" confirmation that writes `copy(isArchived=true)`, never a hard delete, so a day that logged the med as taken keeps its meaning); a "Cycle tracking is off" `Role.Switch` bound to the standing `TreatmentState` (suppressed / naturallyCycling, latest wins); a "Privacy" row opening the Phase-19 `PrivacyPolicyScreen`; and a destructive "Withdraw consent" control behind a confirmation dialog that calls the real `onWithdraw` → `ConsentStore.withdraw` → the whole app re-gates. The Phase-19 temporary `shell.withdraw` affordance was REMOVED (graduated here). All new curated copy is registered in `SettingsCopy`; the destructive-consent legal body is single-sourced exempt (`WithdrawConsentCopy`) like `Disclaimer`. Proven runnably against real in-memory Room under Robolectric.**

## Performance
- **Duration:** ~40 min
- **Completed:** 2026-07-09
- **Tasks:** 2
- **Files:** 12 (6 created, 6 modified)

## Accomplishments
- **Task 1 — Manage meds (add + soft-archive) + SettingsCopy:** `AddMedicationSheet` is a Bloom `ModalBottomSheet` with a required `Name` field + an optional `Type or note` field; Save is disabled until the name is non-blank and hands a fresh `Medication` definition (generated id, `isArchived=null`) up to the caller. `ManageMedsSection` renders the active saved-med list off `observeMedications`, an honest "No medications saved yet." empty state, the add action, and a per-med Remove behind a "Remove this medication?" `AlertDialog` that SOFT-archives (`copy(isArchived=true)`). `SettingsCopy` (registered) holds the substring-safe Settings/meds strings; `WithdrawConsentCopy` (exempt) holds the destructive-consent legal register. `EndoRepository` gained `observeMedications` (safe-by-construction), gated `allMedications` + `latestTreatment` reads, and a free-text length bound on `upsertMedication`.
- **Task 2 — SettingsScreen + host/shell/gate wiring:** `SettingsScreen(onWithdraw, onBack, …seams)` — a `Scaffold` ("Settings" top bar + back) over a scroll of the reminders slot → manage meds → cycle-off toggle + footer → Privacy row → Withdraw card, with the withdraw `AlertDialog` and the Privacy overlay composed in the same tree. `LogTabHost` now renders the real `SettingsScreen` (was a placeholder) and threads the med/treatment seams + `onBack = { stack.pop() }`. `ConsentedShell` removed the temp `shell.withdraw` `TextButton` and passes the new seams through. `AppGate` binds them ONLY inside the `Consented` arm — `medications = repository.observeMedications()`, add/archive → `upsertMedication`, `loadSuppressed`/`setSuppressed` reading/writing `TreatmentState` (latest wins) — preserving consent-by-construction.

## Task Commits
1. **Task 1: manage meds — add + soft-archive + SettingsCopy** — `0455762` (feat)
2. **Task 2: SettingsScreen — cycle-off, privacy, graduated withdraw** — `1e6ec92` (feat)

_No STATE.md / ROADMAP.md commit — per the objective, the orchestrator owns state updates for this run._

## Test Results (REAL, run on this host — Android SDK installed)
- `:app:testDebugUnitTest` — **156/156 passed, 0 failures, 0 errors** (was 148/148 after Plan 03; +8). New this plan:
  - `MedicationTest` **3/3** (Robolectric + REAL in-memory Room + the Compose lane) — adding a med persists it and it appears in `observeMedications`; archiving hides it from the active list while `allMedications` still holds the definition with `isArchived=true` (round-trip: taken-history stays valid, no hard delete); the add sheet's Save is disabled for a blank name, enabled once a name is entered, and hands up a `Medication` carrying it.
  - `SettingsScreenTest` **5/5** — the five sections render (reminders slot, meds, cycle-off, privacy, withdraw); a saved med renders in manage-meds (no empty state); toggling cycle-off signals suppression and the AppGate-equivalent repo write + reload reads `suppressed` back (latest wins); the withdraw confirm calls `onWithdraw` and the dialog renders the exempt `WithdrawConsentCopy.BODY`; Privacy opens the Phase-19 `privacyPolicy.body`.
  - Unregressed: `CopyAuditTest` **8/8** (SettingsCopy passes the substring scan; the reworded strings clear "take"), `AppGateConsentTest` **5/5** (re-gate now driven through the graduated Settings withdraw; `shell.withdraw` asserted absent), `ConsentedShellTest` **4/4** (temp withdraw removal did not touch the tab surface), `LogHomeTest` **4/4** (the gear still pushes `log.route.settings` — now the real screen), `DayLogEditorTest` **4/4**, `BodyMapSemanticsTest` **3/3**, `LogPersistenceTest` **3/3**, `AbsenceNotZeroTest` **25/25**.
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real `app-debug.apk`).
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL** (the emulator lane still compiles after the shell/gate signature changes).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Two UI-SPEC §7 strings carried "taken" (⊃ imperative "take") — CopyAudit failure**
- **Found during:** Task 1 (pre-write substring scan of the SettingsCopy candidates against the `:insights` `ForbiddenTerms` imperative list, which contains `take`).
- **Issue:** The UI-SPEC §7 (and the plan's Task 1 action) specified the remove-med body "…logged it as **taken** stay unchanged." and the no-meds-in-log line "…mark it **taken** each day." as "substring-safe reworded versions" — but "taken" CONTAINS "take". Registering them as-is fails `CopyAuditTest` (a hard must-have + threat T-20-01f). The UI-SPEC reword pass caught here/stops/treatment but missed this pair (same class as Plan 01's Shoulder→should / nausea→use).
- **Fix:** reworded to drop the substring — "…already logged it stay unchanged." and "…to log it each day." (the medication vocabulary reads "log it", never "taken"). Verified clean via a full re-scan of every SettingsCopy string.
- **Files:** `SettingsCopy.kt`. **Commit:** `0455762`.

### Auto-added critical functionality

**2. [Rule 2 - Missing critical functionality] Med/treatment READ seams on EndoRepository + the length bound**
- **Found during:** Task 1 (the Settings screen must read the saved-med list and the current suppression state, but `EndoRepository` exposed only the WRITES — `upsertMedication` / `upsertTreatment` — and `daySnapshots` consumed `latestTreatment` internally).
- **Change:** added `observeMedications()` (a cold `Flow`, safe-by-construction like `observeDayEntries` — wired only in the Consented arm), gated `allMedications()` (the archive-survival proof surface) and `latestTreatment()` (the cycle-off initial value); and bounded the med free-text name/note length to 200 chars INSIDE `upsertMedication` (the plan asked for the bound "at the EndoRepository seam, not the UI", so the store is the single guard).
- **Files:** `EndoRepository.kt`. **Commit:** `0455762`.

### Test updated for the graduated control

**3. [Rule 3 - Blocking] AppGateConsentTest asserted the removed `shell.withdraw`**
- **Found during:** Task 2 (removing the temp `shell.withdraw` `TextButton` — a plan requirement — left `AppGateConsentTest.withdrawingConsent_reGatesToOnboarding` clicking a node that no longer exists).
- **Fix:** renamed the case `…_viaSettings_reGatesToOnboarding` and rewrote it to drive the re-gate through the graduated path — assert `shell.withdraw` is GONE, then Log-home gear → Settings → `settings.withdraw` (scrolled into view) → `settings.withdrawConfirm` → the tabs leave composition + the consent CTA returns. The withdraw-re-gate invariant (threat T-20-04) is preserved, now proven end-to-end through the real Settings control.
- **Files:** `AppGateConsentTest.kt`. **Commit:** `1e6ec92`.

## Threat Model Compliance
- **T-20-04 (Elevation of privilege — Withdraw-consent graduation, high — mitigate):** met. The Settings withdraw is behind a confirmation `AlertDialog` and its confirm calls the real `onWithdraw` → `ConsentStore.withdraw`; `AppGateConsentTest` (5/5) drives gear → Settings → withdraw → confirm and asserts the tab surface leaves composition and the consent CTA returns. The temp `shell.withdraw` is removed and asserted absent.
- **T-20-05 (Tampering — Medication soft-archive, medium — mitigate):** met. Remove writes `copy(isArchived=true)` through `upsertMedication` (there is NO hard-delete seam for meds); `MedicationTest` proves the archived med leaves `observeMedications` while `allMedications` still holds the definition (`isArchived=true`), so prior 'taken' history keyed by id stays valid.
- **T-20-05b (Tampering — Treatment-state latest-wins, medium — mitigate):** met. Cycle-off writes a fresh timestamped `TreatmentState` (uuid + now + `suppressed`/`naturallyCycling`) at the AppGate seam; `latestTreatment` reads it back; `SettingsScreenTest` proves the toggle signals suppression and the repo write + reload reads `suppressed` (Phase-18 non-day-scoped, so it survives a day delete).
- **T-20-01f (Information disclosure — SettingsCopy, medium — mitigate):** met. All Settings/meds strings are registered in `CopyAuditRegistry.sources` in the same change with the substring-safe forms (the two 'taken' near-misses reworded); the destructive-consent body reuses the exempt legal register (`WithdrawConsentCopy`, single-sourced like `Disclaimer`) rather than introducing a new `stop` into the curated scan; `CopyAuditTest` green (8/8).
- **T-18-02 / T-19-01 (consent-by-construction, preserved):** the med/treatment read + write seams are bound ONLY inside `AppGate`'s `Consented` arm; `observeMedications` is a cold Flow never collected pre-consent; the gated reads/writes fail closed; `AppGateConsentTest` 5/5 unregressed.
- **§4 no-flare / reminder-separability invariant:** `SettingsScreen` and the manage-meds/add-med files import NO notification / WorkManager / alarm API — the reminders section is an empty reserved slot (`settings.remindersSlot`) for Plan 05, so the reminder surface stays cleanly separable for the Phase-22 structural scan.

## Known Stubs
- **Reminders section:** a RESERVED, clearly-marked empty slot (`settings.remindersSlot`) in `SettingsScreen` — Plan 05 fills it with the daily-reminder toggle + time rows + quiet hours (and the only notification/WorkManager surface in the app). Deliberately empty this phase (no notification API lands here) per the plan and the §4 separability invariant. Not a blocker for this plan's goal.
- **Per-med "taken" toggles in the Day-Log/Check-In medication section:** the medication section still renders only the As-needed (PRN) flag (carried from Plan 02/03). Now that saved `Medication` definitions are creatable in Settings, wiring per-med taken toggles into the logging screens is a follow-up (the `NO_MEDS_IN_LOG` copy for the empty case is registered here, ready for that wiring). Not required by ATRK-03/05.

## Self-Check: PASSED
- All 6 created files + 6 modified files exist on disk.
- Both task commits present in git history: `0455762`, `1e6ec92`.
- Real build/test evidence captured above (`:app` 156/156 incl. `MedicationTest` 3/3 over REAL in-memory Room + `SettingsScreenTest` 5/5; `CopyAuditTest` 8/8, `AppGateConsentTest` 5/5, `ConsentedShellTest` 4/4, `LogHomeTest` 4/4 unregressed; `assembleDebug` SUCCESSFUL; `compileDebugAndroidTestKotlin` SUCCESSFUL; `:insights` 176/176).

---
*Phase: 20-tracking-screens*
*Completed: 2026-07-09*
