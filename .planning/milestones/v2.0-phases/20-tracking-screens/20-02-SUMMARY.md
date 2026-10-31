---
phase: 20-tracking-screens
plan: 02
subsystem: ui
tags: [android, compose, tracking, check-in, honesty, absence-not-zero, copy-audit, robolectric, bloom, separation-invariant]

# Dependency graph
requires:
  - phase: 20-tracking-screens
    plan: 01
    provides: "DayDraft + PURE DayDraftMapper (nil-honest toRows/fromRows), SeverityControl + TrackingSectionRows primitives, TrackingCopy vocabulary, TrackingRoute + TrackingBackStack + LogTabHost (CheckIn arm to fill), CheckInRouter observation inside the consented Log subtree"
  - phase: 18-room-persistence
    plan: 03
    provides: "consent-gated EndoRepository per-aggregate upserts + per-day reads (the persistence seam target)"
  - phase: 19-app-shell-consent
    plan: 01
    provides: "BloomTheme + Bloom components (bloomCard/BloomPrimaryButton/EyebrowLabel/typography) + Robolectric+Compose JVM harness"
provides:
  - "CheckInCopy — the registered Daily Check-In per-screen vocabulary (Done for today, Same as yesterday + sticky-context subtitle, Checked in for today., the eight section headers, Clots, As-needed pain relief, stress reveal, food placeholder); reuses TrackingCopy title/CTA + Not-logged caption (single-source)"
  - "CheckInSections — the nine section composables (Same-as-yesterday, symptoms+reveal-severity, food, activity, sleep, medication, stress reveal-on-opt-in, mood, bleeding+clots) built on TrackingSectionRows + SeverityControl"
  - "applySameAsYesterday — the PURE sticky-context-only prefill (copies meds/sleep/activity from yesterday; NEVER symptoms/pain/flow/clots)"
  - "DailyCheckInScreen — the single-scroll fast-log form: nothing mandatory, systemBars+ime insets unioned, Done maps DayDraft via DayDraftMapper.toRows → persistDay seam, fires fire-and-forget onDayLogged, dismisses independently; empty confirm records only the DayEntry marker + shows the inline note; imports NO notification/alarm API"
  - "LogTabHost CheckIn arm — renders the real DailyCheckInScreen (persistDay/loadYesterday/today/onDayLogged injected seams; back pops)"
affects: [20-03, 20-04, 20-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Persistence + yesterday-read injected as function seams (persistDay: suspend (DayLogRows)->Unit, loadYesterday: suspend ()->DayDraft?) so the screen imports no DAO and is testable off a fake — mirrors the Plan 20-01 posture of threading the repository from the composition root in a later wiring step"
    - "Dismissal decoupled from side effects — Done launches persistDay in a scope (never awaited) and calls onDayLogged() as a plain callback, so a slow/failed write and the reminder auto-suppress never block dismissal (threat T-20-02c); proven by a never-completing suspendCancellableCoroutine persist stub"
    - "Empty-confirm honesty read straight off the mapper output — recordsOnlyMarker = symptoms/pain/bleeding/factors all empty; the DayEntry anchor DayDraftMapper always emits is the only row (no phantom zeros)"
    - "Sticky-context prefill as a PURE DayDraft->DayDraft copy (applySameAsYesterday) — the honesty contract is pinned by fast JVM assertions independent of Compose"
    - "Structural separation guard — a test scans the check-in sources' import lines for notification/alarm/WorkManager tokens (comment mentions ignored), seeding the Phase-22 no-flare-notification invariant"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/CheckInCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/CheckInSections.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/DailyCheckInScreen.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/CheckInFlowTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/SameAsYesterdayTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt

decisions:
  - "Persistence threaded as injected seams on LogTabHost/DailyCheckInScreen (defaulted), NOT via a composition-root Room build — kept strictly to the plan's file scope (LogTabHost is the integration point; AppDependencies/AppGate/ConsentedShell are not) and mirrors Plan 20-01's deliberate emptyFlow deferral. The row→EndoRepository write path is a thin per-aggregate upsert loop the composition root will supply; the honesty behaviors are all test-proven through the seam."
  - "Medication section renders only the As-needed (PRN) flag this plan — per-med 'taken' toggles need saved Medication definitions, which cannot exist until Settings/manage-meds lands (Plan 04). Avoids introducing the 'mark it taken' Settings copy (which trips the imperative gate on 'take'⊂'taken') into this change."
  - "DayDraft held in plain remember{mutableStateOf} (not rememberSaveable) — a Saver over the enum/map-bearing draft is heavy and config-change draft survival is not a plan must-have; the in-Log TrackingBackStack already survives config change. Noted for a later Saver if desired."
  - "A selected-but-unrated symptom keeps severity null (honest: selected, no severity); the revealed SeverityControl rests at 0 visually but only writes a real value on interaction — persisted honesty (null vs real 0) is intact via the mapper."

metrics:
  duration: ~40min
  completed: 2026-07-09
  tasks: 2
  files: 7

status: complete
---

# Phase 20 Plan 02: Daily Check-In Fast-Log Flow Summary

**Ported the iOS fast Daily Check-In onto the Phase-20 foundation: a single-scroll Bloom form (Same as yesterday → Symptoms → Food & drink → Activity → Sleep → Medication → Stress → Overall mood → Bleeding) where NOTHING is mandatory. "Done for today" maps the nil-honest `DayDraft` through the pure `DayDraftMapper` and persists it via an injected consent-gated seam, fires the fire-and-forget reminder auto-suppress (`onDayLogged`), and dismisses immediately — dismissal is never gated on persistence or the reminder side effect. An empty confirm records ONLY the `DayEntry` adherence marker (zero symptom/factor/bleeding rows — no invented data) and shows the honest inline "Checked in for today." note. "Same as yesterday" prefills sticky CONTEXT only (meds, sleep, activity) via the pure `applySameAsYesterday` and NEVER carries symptoms or bleeding. The deep-link entry still lands on the gate first (the consented `LogTabHost` observes `CheckInRouter` and pushes the real screen). All new copy is registered in `CopyAuditRegistry` and passes the substring gate; the check-in sources import no notification/alarm API.**

## Performance
- **Duration:** ~40 min
- **Completed:** 2026-07-09
- **Tasks:** 2
- **Files:** 7 (5 created, 2 modified)

## Accomplishments
- **`CheckInCopy` (registered from birth):** the per-screen check-in strings — `Done for today`, `Cancel`, `Same as yesterday` + its sticky-context-only subtitle, the empty-confirm note `Checked in for today.`, the eight section headers, `Clots`, `As-needed pain relief`, `Log stress`/`Clear stress`, and the food placeholder — appended to `CopyAuditRegistry.sources` in the same change. The title/CTA (`Daily check-in`) and the `Not logged` caption are reused from `TrackingCopy` (single-source, never re-declared). Every string hand-scanned against the substring traps (`usual`⊅`use`, `never`/`carried over`⊅`her`, `relief`⊅`relieve`, `stress`⊅`treat`); `CopyAuditTest` green over the enlarged catalog.
- **`CheckInSections`:** the nine section composables built on the Plan-01 primitives (`MultiSelectRow`/`SingleSelectRow`/`StepperRow`/`SegmentedPickerRow`/`RevealOnOptInRow`/`MoodRow`/`FreeTextRow`) + `SeverityControl` — symptoms multi-select with a per-symptom severity reveal, the six factor sections (food chips + free-text, activity type→duration/intensity, sleep hours/quality, medication PRN, stress reveal-on-opt-in, mood), and bleeding single-select + clots. Nothing is validated/required; every untouched optional stays absence-encoded. Plus the **pure `applySameAsYesterday`** — the sticky-context-only prefill seam.
- **`DailyCheckInScreen`:** a `Scaffold` + `TopAppBar` (title `Daily check-in`, a `Done for today` confirm action, a Cancel/back), a single scrolling `Column` over the sections in the exact §1 order (Same as yesterday first), unioning `systemBars` + `ime` insets. Holds the editable `DayDraft`; `Done` maps it via `DayDraftMapper.toRows(today, draft, idFor)`, persists through the injected seam (launched, never awaited), fires `onDayLogged()`, and dismisses. An empty confirm shows the inline note; a non-empty confirm dismisses immediately. Imports **zero** notification/alarm/WorkManager API.
- **`LogTabHost` CheckIn arm:** replaced the Plan-01 placeholder with the real `DailyCheckInScreen`, wired to injected `persistDay`/`loadYesterday`/`today`/`onDayLogged` seams (defaulted so the Phase-19 shell/gate call sites keep compiling); back pops the stack. The `log.route.checkIn` testTag is preserved so `LogHomeTest` stays green.

## Task Commits
1. **Task 1: Daily Check-In screen + CheckInCopy + host wiring** — `3153005` (feat)
2. **Task 2: empty-confirm + same-as-yesterday honesty tests** — `b6867d1` (test)

_No STATE.md / ROADMAP.md commit — per the objective, the orchestrator owns state updates for this run._

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **138/138 passed, 0 failures, 0 errors** (was 131/131 after Plan 01; +7). New this plan:
  - `CheckInFlowTest` **3/3** — empty confirm persists exactly the DayEntry marker (epochDay 20000) with zero symptom/pain/bleeding/factor rows AND shows `checkin.confirmNote`; confirm fires `onDayLogged` exactly once and dismisses even when `persistDay` is a never-completing `suspendCancellableCoroutine`; the check-in sources' import lines contain no notification/alarm/WorkManager token.
  - `SameAsYesterdayTest` **4/4** — `applySameAsYesterday` prefills meds/prn/sleep/activity, never carries symptoms/pain/flow/clots, preserves today's already-touched symptoms; the screen-driven prefill→Done persists sticky factor rows with zero symptom/bleeding rows.
  - `CopyAuditTest` **7/7** — green over the enlarged catalog (CheckInCopy passes the ClaimsPolicy substring scan).
  - Unregressed: `LogHomeTest` **4/4** (the CheckIn arm still resolves `log.route.checkIn`), `ConsentedShellTest` **4/4**, `AppGateConsentTest` **5/5** (consent-by-construction preserved), `AbsenceNotZeroTest` **25/25**.
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real `app-debug.apk` = 12.9 MB / 13,516,381 bytes).

## Deviations from Plan
None affecting behavior — the plan executed as written. Two scoping choices worth flagging (documented under Known Stubs, not code changes):
- Persistence is threaded as injected seams on `LogTabHost`/`DailyCheckInScreen` rather than through a composition-root Room build — this keeps the change within the plan's stated file scope (LogTabHost as the integration point) and mirrors Plan 20-01's deliberate `emptyFlow()` deferral. See Known Stubs.
- The Medication section renders only the As-needed (PRN) flag this plan; per-med "taken" toggles arrive with Settings/manage-meds in Plan 04 (no saved medications can exist before then).

## Threat Model Compliance
- **T-20-01 (Tampering — empty confirm / Same-as-yesterday, high — mitigate):** met. Empty confirm writes ONLY the DayEntry marker (`CheckInFlowTest` asserts zero symptom/pain/bleeding/factor rows), reading `recordsOnlyMarker` straight off the mapper output — no invented data. `applySameAsYesterday` copies only meds/sleep/activity and leaves symptoms/pain/flow/clots at their `current` values (`SameAsYesterdayTest`).
- **T-20-02b (Spoofing — CheckInRouter deep-link, high — mitigate):** met. Router observation is unchanged from Plan 01 — only `LogTabHost` (the consented Log subtree) observes `CheckInRouter.pendingCheckIn`; a pre-consent flag lands on the gate. `AppGateConsentTest` 5/5 unregressed.
- **T-20-01c (Information disclosure — CheckInCopy, medium — mitigate):** met. Every check-in string is registered in `CopyAuditRegistry.sources` in the same change; `CopyAuditTest` green (neutral, non-gendered, non-diagnostic; the substring traps transcribed, never reintroduced).
- **T-20-02c (Tampering — Check-In ↔ reminder isolation, medium — mitigate):** met. Save-suppress is the fire-and-forget `onDayLogged()` callback; dismissal is never blocked on it (proven with a never-completing persist stub). `CheckInFlowTest.checkInSources_importNoNotificationOrAlarmApi` grep-asserts the check-in sources import no notification/alarm/WorkManager API — separation preserved for the Phase-22 structural scan.

## Known Stubs
- **Composition-root persistence wiring:** `DailyCheckInScreen`/`LogTabHost` take `persistDay`/`loadYesterday`/`today` as injected seams, defaulted to a no-op / `null` / `LocalDate.now()`. In the running app `MainActivity → AppGate → ConsentedShell → LogTabHost` still passes the defaults (no `EndoDatabase`/`EndoRepository` is constructed in `AppDependencies` yet), so a live "Done for today" dismisses without persisting and "Same as yesterday" is a no-op — EXACTLY the posture Plan 20-01 shipped (recent-days wired to `emptyFlow()`), which review accepted. The row→repository write path is a thin per-aggregate upsert loop (`upsertDayEntry` + `upsertSymptom`/`upsertPain`/`upsertBleeding`/`upsertFactor` over `DayLogRows`) and the yesterday-read is `factorsForDay(today-1)` + `DayDraftMapper.fromRows`; wiring the Room database into the composition root and threading these seams (a defaulted param on `ConsentedShell`, mirroring how `recentDays` was added) is the remaining step, landing with the writing screens' composition-root wiring (Plan 03/04). All load-bearing honesty behaviors are test-proven through the seam today.
- **Per-med "taken" toggles:** the Medication section shows only the PRN flag until saved `Medication` definitions become creatable in Settings/manage-meds (Plan 04).

## Self-Check: PASSED
- All 5 created files + 2 modified files exist on disk.
- Both task commits present in git history: `3153005`, `b6867d1`.
- Real build/test evidence captured above (`:app` 138/138 incl. `CheckInFlowTest` 3/3 + `SameAsYesterdayTest` 4/4 + `CopyAuditTest` 7/7; `LogHomeTest`/`ConsentedShellTest`/`AppGateConsentTest` unregressed; `:insights` 176/176; `assembleDebug` 12.9 MB APK).

---
*Phase: 20-tracking-screens*
*Completed: 2026-07-09*
