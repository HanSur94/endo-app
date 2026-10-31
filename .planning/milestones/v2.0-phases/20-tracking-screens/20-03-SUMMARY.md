---
phase: 20-tracking-screens
plan: 03
subsystem: ui
tags: [android, compose, tracking, daylog, bodymap, accessibility, talkback, absence-not-zero, copy-audit, robolectric, room, composition-root, bloom]

# Dependency graph
requires:
  - phase: 20-tracking-screens
    plan: 01
    provides: "DayDraft + PURE DayDraftMapper (nil-honest toRows/fromRows), SeverityControl + TrackingSectionRows primitives, TrackingCopy vocabulary (incl. bodyRegionLabels + severity stateDescription builders), TrackingRoute/TrackingBackStack + LogTabHost (DayLog arm to fill)"
  - phase: 20-tracking-screens
    plan: 02
    provides: "CheckIn arm persistence-seam pattern (injected persistDay/loadYesterday) mirrored by the Day Log editor + composition-root deferral this plan closes"
  - phase: 18-room-persistence
    plan: 03
    provides: "consent-gated EndoRepository (per-aggregate upserts, per-day reads, deleteDay cascade) + EndoRepository.from(consent, db)"
  - phase: 18-room-persistence
    plan: 01
    provides: "EndoDatabase.build(context) (Room) + DayEntryDao.observeAll() live day history"
  - phase: 19-app-shell-consent
    plan: 03
    provides: "AppGate consent-by-construction routing + ConsentedShell + AppDependencies composition root (the wiring points this plan threads seams through)"
provides:
  - "DayLogEditorScreen — the per-day editor (Symptoms+severity → accessible BodyMap → Bleeding → factor sections → Note) reusing DayDraft/DayDraftMapper; loads via loadDay, saves via toRows, with a guarded destructive Delete-this-day (AlertDialog → EndoRepository.deleteDay cascade) offered only for a day with data"
  - "BodyMapView + BodySilhouette — the accessible pain map: a Canvas silhouette (200x440) with one FOCUSABLE >=48dp Role.Button hotspot per BodyRegion (contentDescription + severity/not-marked stateDescription + bodymap.<region> testTags), a marked-areas summary, a ModalBottomSheet severity picker + guarded Clear-this-area, and a first-class non-visual 'All areas (list)' fallback that completes the map without the visual (SYMP-02)"
  - "DayLogCopy — the registered Day Log + BodyMap per-screen vocabulary (substring-gate clean)"
  - "LogPersistence — the composition-root row<->repository seams: upsertDay (Check-In MERGE), replaceDay (Day Log authoritative purge-then-write), loadDay (null = fresh day)"
  - "Composition-root wiring — AppDependencies builds EndoDatabase + the consent-gated EndoRepository; AppGate binds the persistence seams ONLY inside the Consented arm; the running app now genuinely persists + reloads a logged day end-to-end (the Plans 20-01/02 emptyFlow/no-op deferral is CLOSED)"
affects: [20-04, 20-05, 22]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Accessible BodyMap as an a11y CONTRACT, not geometry — the silhouette Path is decorative (drawn from a normalized author view-box); every region's meaning lives on a focusable >=48dp Role.Button hotspot + the list fallback, both routing to the SAME DayDraft.pain point; proven on the semantics tree (BodyMapSemanticsTest), so geometry may iterate freely"
    - "Severity by NUMBER + tint, never colour alone — hotspot badge shows the numeral; a rose halo ramps by severity; the stateDescription carries 'severity N of 10' so a screen-reader user never depends on colour"
    - "Two distinct write semantics from one mapper output — Check-In MERGES (upsertDay, never deletes) so an empty confirm records only the marker over an existing day; the Day Log editor REPLACES (replaceDay = atomic delete cascade then write) so a deselected field is honestly gone (no stale rows). Both bound at the composition root; the screens stay DAO-free"
    - "Composition-root persistence wiring bound ONLY inside AppGate's Consented arm — the repository is referenced nowhere else, so consent-by-construction (T-19-01) holds while the app genuinely persists; observeDayEntries() is a cold Flow safe by construction (never collected pre-consent)"
    - "Robolectric drives Dialog/BottomSheet subwindows + below-the-fold controls — scrollTo before a touch on off-screen actions; OnClick semantics-action for controls a small test root cannot lay on-screen; real in-memory Room runs under Robolectric for a runnable persistence round-trip"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/DayLogCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/DayLogEditorScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/BodyMapView.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/BodySilhouette.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogPersistence.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/DayLogEditorTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/BodyMapSemanticsTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/LogPersistenceTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt

decisions:
  - "Split persistence into two seams because the semantics differ: Check-In MERGES (upsertDay — an empty confirm must not wipe a day already logged in detail); the Day Log editor REPLACES (replaceDay — an authoritative full-day edit must drop deselected fields). A single upsert seam would leave stale rows on an edit; a single replace seam would let a Check-In empty confirm delete existing data. LogTabHost forwards persistDay (Check-In) and persistDayLog (editor) separately."
  - "Closed the composition-root wiring gap as a first-class deviation (Rule 2 — critical functionality). The plan scoped only the screen files, but the objective required the running app to actually persist; AppDependencies/AppGate/ConsentedShell/MainActivity/EndoRepository were modified with DEFAULTED seams so the Phase-19 shell/gate tests keep compiling and consent-by-construction is preserved (seams bound only in the Consented arm)."
  - "The BodyMap region sheet + the delete confirmation are a real ModalBottomSheet / AlertDialog (per UI-SPEC), driven in Robolectric via scrollTo (delete button below the fold) and the OnClick semantics action (sheet controls in a small test root). Verified both subwindows are captured and operable."
  - "Reused the plan-01/02 shared primitives (SeverityControl, MultiSelect/SingleSelect/Stepper/SegmentedPicker/RevealOnOptIn/Mood/FreeText rows) for the Day Log sections with daylog.* testTags + DayLogCopy headers rather than re-calling the CheckInSections composables (which carry checkin.* tags + CheckInCopy headers) — keeps the two screens' a11y ids distinct."

metrics:
  duration: ~30min
  completed: 2026-07-09
  tasks: 2
  files: 15

status: complete
---

# Phase 20 Plan 03: Day Log Editor + Accessible BodyMap + Composition-Root Persistence Summary

**Ported the iOS Day Log editor and the accessible BodyMap onto the Phase-20 foundation, and closed the composition-root wiring gap so the running app genuinely persists. `DayLogEditorScreen` is a per-day form (Symptoms+severity → the BodyMap → Bleeding → the factor sections → Note) reusing the nil-honest `DayDraft`/`DayDraftMapper` — an untouched section reads "Not logged", a slid-to-0 severity is a real 0 — with a guarded destructive "Delete this day" (an `AlertDialog` → the atomic `EndoRepository.deleteDay` cascade) offered only for a day that already has data. `BodyMapView` + `BodySilhouette` render a Compose Canvas silhouette with one FOCUSABLE ≥48dp `Role.Button` hotspot per `BodyRegion` (neutral non-gendered `contentDescription` + a "severity N of 10"/"not marked" `stateDescription` + `bodymap.<region>` testTags; severity by number + rose halo, never colour alone), a marked-areas summary, a `ModalBottomSheet` severity picker with a guarded Clear-this-area, and a FIRST-CLASS non-visual "All areas (list)" fallback that completes the map without the silhouette — both the hotspot and the list write the SAME `DayDraft.pain` point (SYMP-02). Finally, the composition root now builds the real Room `EndoDatabase` + the consent-gated `EndoRepository` and binds the persistence seams ONLY inside `AppGate`'s `Consented` arm, so a logged day ACTUALLY persists and reloads end-to-end — proven runnably against real in-memory Room under Robolectric. All new copy is registered in `CopyAuditRegistry` and passes the substring gate.**

## Performance
- **Duration:** ~30 min
- **Completed:** 2026-07-09
- **Tasks:** 2 plan tasks + the composition-root wiring deviation
- **Files:** 15 (8 created, 7 modified)

## Accomplishments
- **`DayLogEditorScreen` + `DayLogCopy` (Task 1):** the per-day editor — a `Scaffold` with a serif day title + a plum `Save` capsule over a single scroll of the §1 sections (Symptoms+`SeverityControl` → Pain areas → Bleeding → Food+footer → Activity → Sleep → Medication → Stress → Mood → Note), unioning `systemBars` + `ime` insets, reusing the plan-01 shared row primitives with `daylog.*` testTags. On open it loads the existing day via the injected `loadDay` seam + `DayDraftMapper.fromRows` (a fresh day starts empty = every section "Not logged"); `Save` maps via `toRows` and persists through the seam then pops. A destructive `Delete this day` appears ONLY when the loaded day has data, gated by an `AlertDialog` → `deleteDay` cascade. `DayLogCopy` is registered in `CopyAuditRegistry.sources` in the same change (every string hand-scanned against the substring traps: `spot`⊅`stop`, `removes`⊅`reduce`/`improve`, `clear`/`undone` clean, `your day`⊅`her`, `Delete`⊅`detect`/`treat`).
- **`BodyMapView` + `BodySilhouette` (Task 2):** the accessible pain map. `BodySilhouette` holds the Canvas `Path` (200×440 author view-box) + the VERBATIM normalized hotspot-center table (10 regions) + the front/back side sets. `BodyMapView` renders: a front/back `SingleChoiceSegmentedButtonRow` (`bodymap.sideToggle`, `Role.RadioButton` options, group labelled "Body side"); the silhouette on a `surfaceSunken` card with one hotspot per current-side region — a ≥48dp `Role.Button` (`bodymap.region.<raw>`) carrying the neutral region label + a "severity N of 10"/"not marked" `stateDescription`, its severity shown as a centered numeral + a rose halo ramped by severity (number + tint, never colour alone); a `bodymap.markedSummary` of region+`N/10` pills; and the non-visual `bodymap.listFallback` disclosure listing ALL ten regions (`bodymap.list.<raw>`, each `Role.Button` + the same semantics). A region tap (hotspot OR list row) opens the SAME `ModalBottomSheet` ("Pain area" + region label + `SeverityControl` + a guarded `Clear this area` disabled-when-unmarked + `Done`), writing the SAME `DayDraft.pain[region]`. Mounted into the editor's Pain areas section.
- **Composition-root persistence wiring (the closed gap):** `AppDependencies` now builds `EndoDatabase.build(context)` (lazy) + `EndoRepository.from(consentStore, database)`. `EndoRepository.observeDayEntries()` exposes the live recent-days `Flow`. `LogPersistence` provides the three row↔repository seams (`upsertDay` merge / `replaceDay` purge-then-write / `loadDay`). `AppGate` gained a defaulted `repository` param and binds `recentDays`/`persistDay`/`persistDayLog`/`loadYesterday`/`loadDay`/`deleteDay`/`today` into `ConsentedShell` → `LogTabHost` ONLY inside the `Consented` arm; `MainActivity` passes `dependencies.repository`. The running app persists a logged day and shows it back on Log-home; a Day Log edit drops deselected fields; delete purges the day.

## Task Commits
1. **Task 1: Day Log editor + guarded delete + DayLogCopy + host wiring** — `6927d18` (feat)
2. **Task 2: accessible BodyMap — Canvas silhouette, hotspots, sheet, list fallback** — `4f4c8c3` (feat)
3. **Deviation: wire composition root to real Room persistence (close the gap)** — `6b0f572` (feat)

_No STATE.md / ROADMAP.md commit — per the objective, the orchestrator owns state updates for this run._

## Test Results (REAL, run on this host — Android SDK installed)
- `:app:testDebugUnitTest` — **148/148 passed, 0 failures, 0 errors** (was 138/138 after Plan 02; +10). New this plan:
  - `DayLogEditorTest` **4/4** — the editor renders the sections + Save; an untouched section reads "Not logged" (never 0); a fresh day offers NO delete; a day WITH data offers the guarded delete whose confirm (scrolled into view, real `AlertDialog`) delegates to the `deleteDay` cascade for THIS day and dismisses; Save persists the mapped rows (anchor records the day) and dismisses.
  - `BodyMapSemanticsTest` **3/3** — ALL ten `BodyRegion`s expose a focusable ≥48dp `Role.Button` hotspot with a non-empty contentDescription + a "not marked" stateDescription + `bodymap.region.<raw>` (front 7 + back 3 via the side toggle); the `bodymap.listFallback` lists all ten (`bodymap.list.<raw>`) as buttons with the same semantics; completing the map THROUGH the list (open sheet → "Moderate") sets the SAME `DayDraft.pain` point (6) and flips that row's stateDescription to "severity 6 of 10".
  - `LogPersistenceTest` **3/3** (Robolectric + REAL in-memory Room) — `upsertDay`→`loadDay` round-trips nil-vs-0 through the consent-gated repository (a slid-0 stays 0; a nil-severity symptom stays present-with-null, never coerced); `replaceDay` drops a deselected symptom on reload (honest edit, no stale rows); `loadDay` returns `null` for a never-logged day.
  - Unregressed: `CopyAuditTest` **7/7** (DayLogCopy passes the substring scan), `LogHomeTest` **4/4**, `ConsentedShellTest` **4/4**, `AppGateConsentTest` **5/5** (consent-by-construction preserved with the new `repository` param defaulted), `CheckInFlowTest` **3/3**, `SameAsYesterdayTest` **4/4**, `AbsenceNotZeroTest` **25/25**.
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real `app-debug.apk` = 12.9 MB).
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL** (`DayLogRoundTripTest` + `DeleteDayCascadeTest` still compile on the emulator lane).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).

## Deviations from Plan

### Auto-added critical functionality

**1. [Rule 2 - Missing critical functionality] Closed the composition-root persistence wiring gap**
- **Found during:** Task 1 (the plan scoped only the screen files, but the objective + the plan-01/02 Known Stubs flagged that `LogTabHost`/`ConsentedShell`/`AppGate`/`AppDependencies` still defaulted persistence to `emptyFlow()`/no-op — the running app did not persist a day end-to-end).
- **Change:** `AppDependencies` builds the real `EndoDatabase` + the consent-gated `EndoRepository`; `EndoRepository.observeDayEntries()` was added; `LogPersistence` provides the row↔repository seams; `AppGate` binds them ONLY inside the `Consented` arm; `MainActivity` passes the repository; `ConsentedShell` + `LogTabHost` thread the seams. All new shell/gate params are DEFAULTED so `ConsentedShellTest` / `AppGateConsentTest` keep compiling, and the repository is referenced only in the `Consented` arm so consent-by-construction (T-19-01) is preserved.
- **Files:** `AppDependencies.kt`, `AppGate.kt`, `MainActivity.kt`, `ConsentedShell.kt`, `LogTabHost.kt`, `EndoRepository.kt`, `LogPersistence.kt`. **Commit:** `6b0f572`.
- **Proof:** `LogPersistenceTest` (runnable Robolectric + real Room) proves the wired seams persist + reload with nil-vs-0 intact and drop deselected fields on an edit.

**2. [Rule 1 - Correctness] Distinct MERGE vs REPLACE write semantics**
- **Found during:** the wiring (a single persist seam would be wrong for one of the two screens).
- **Issue:** the Check-In fast-log must MERGE (an empty confirm must not wipe a day already logged in detail); the Day Log editor must REPLACE (an authoritative edit must drop deselected fields, else stale rows survive).
- **Fix:** `LogPersistence.upsertDay` (merge) for Check-In vs `LogPersistence.replaceDay` (atomic delete cascade then write) for the editor; `LogTabHost` forwards `persistDay` and `persistDayLog` separately.
- **Files:** `LogPersistence.kt`, `LogTabHost.kt`, `AppGate.kt`. **Commit:** `6b0f572`. Proven by `LogPersistenceTest.replaceDay_dropsDeselectedRows_onReload`.

_No copy reword was needed this plan — the Day Log / BodyMap strings cleared the substring gate as written (unlike plan-01's Shoulder/nausea traps)._

## Threat Model Compliance
- **T-20-01d (Tampering — Day-log editor honesty, high — mitigate):** met. The editor loads/saves through the plan-01 nil-honest mapper; `DayLogEditorTest` asserts an untouched section reads "Not logged" and Save persists via `toRows`; `LogPersistenceTest` proves nil-vs-0 survives a REAL Room round trip and the plan-01 `AbsenceNotZeroTest` (25/25) still pins the mapping.
- **T-20-03 (Accessibility exclusion — BodyMap non-visual path, high — mitigate):** met. Every one of the ten regions is a focusable ≥48dp `Role.Button` with contentDescription + a "severity N of 10"/"not marked" stateDescription; the "All areas (list)" fallback completes the map without the visual — `BodyMapSemanticsTest` proves all ten hotspots + all ten list rows on the semantics tree AND that a list-driven set writes the same draft point. Severity is number + tint, never colour alone.
- **T-20-04b (Repudiation — Delete-this-day, medium — mitigate):** met. Delete is gated by an `AlertDialog` confirmation and delegates to the atomic `EndoRepository.deleteDay` cascade (`DayLogEditorTest` drives the confirm → `deleteDay(day)` + dismissal; the Phase-18 `DeleteDayCascadeTest` proves the cascade's completeness).
- **T-20-01e (Information disclosure — DayLogCopy / region labels, medium — mitigate):** met. All Day Log + BodyMap strings are registered in `CopyAuditRegistry.sources` in the same change; region labels are neutral/non-gendered (the plan-01 Shoulder→"Neck / collarbone" reword carries through); `CopyAuditTest` green.
- **T-18-02 / T-19-01 (consent-by-construction, preserved):** the new persistence path is bound ONLY inside `AppGate`'s `Consented` arm; the repository fails closed pre-consent; `AppGateConsentTest` 5/5 unregressed.

## Known Stubs
- **Settings route:** `LogTabHost`'s Settings arm remains the temporary placeholder (`log.route.settings` + the withdraw affordance) — Plan 04 graduates it into the real Settings/manage-meds screen. Not a blocker for this plan's goal.
- **Per-med "taken" toggles:** the Medication section renders only the As-needed (PRN) flag until saved `Medication` definitions become creatable in Settings (Plan 04) — carried over from Plan 02, unchanged.
- **BodyMap silhouette geometry:** the Canvas `Path` is a deliberately simple androgynous blob (geometry is explicitly non-load-bearing per UI-SPEC §2); the hotspot centers + a11y contract are the parity surface and are complete. Geometry may iterate later with zero contract change.

## Self-Check: PASSED
- All 8 created files + 7 modified files exist on disk.
- All 3 task/deviation commits present in git history: `6927d18`, `4f4c8c3`, `6b0f572`.
- Real build/test evidence captured above (`:app` 148/148 incl. `DayLogEditorTest` 4/4 + `BodyMapSemanticsTest` 3/3 + `LogPersistenceTest` 3/3 over REAL in-memory Room; `CopyAuditTest`/`AppGateConsentTest`/`ConsentedShellTest`/`LogHomeTest` unregressed; `:insights` 176/176; `assembleDebug` 12.9 MB APK; `compileDebugAndroidTestKotlin` SUCCESSFUL).

---
*Phase: 20-tracking-screens*
*Completed: 2026-07-09*
