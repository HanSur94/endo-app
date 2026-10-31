---
phase: 20-tracking-screens
plan: 05
subsystem: reminders
tags: [android, workmanager, notifications, post-notifications, jit-permission, quiet-hours, datastore, manifest, separation-invariant, copy-audit, robolectric, compose, bloom]

# Dependency graph
requires:
  - phase: 20-tracking-screens
    plan: 04
    provides: "SettingsScreen with the reserved reminders slot to fill; SettingsCopy/CopyAudit substring gate; the injected-seam composition-root posture (AppGate binds seams in the Consented arm only); Bloom section-row primitives"
  - phase: 20-tracking-screens
    plan: 02
    provides: "DayEntry today-marker (dayEntriesForDay) the worker consults for already-logged auto-suppress; the check-in separation-scan precedent (CheckInFlowTest import scan)"
  - phase: 19-app-shell-consent
    plan: 05
    provides: "ManifestPermissionsTest (DOM-parse allow/deny guard to extend); the zero-permission manifest baseline"
  - phase: 19-app-shell-consent
    plan: 02
    provides: "CheckInRouter deep-link flag + MainActivity EXTRA_REMINDER_CHECK_IN (the notification tap target that lands on the gate)"
provides:
  - "com.endoapp.app.reminders package — the app's ONLY notification/alarm surface: ReminderSettings (own DataStore, outside health DB), QuietHours (pure midnight-crossing predicate), ReminderScheduler (WorkManager INEXACT periodic, idempotent reschedule, never setExact), ReminderNotifier (one channel + generic deep-link notification), CheckInReminderWorker + CheckInReminderLogic (quiet-hours + already-logged + pre-consent fail-safe suppression), ReminderController facade"
  - "SettingsScreen RemindersSection — master Daily-reminders toggle whose ENABLE handler is the sole JIT POST_NOTIFICATIONS request site; graceful denial (calm copy + Open Settings, no nag loop); reminder time + quiet-hours from/until pickers; every edit reschedules idempotently"
  - "ReminderCopy — the registered reminder/notification/channel strings (substring-clean; 'Quiet hours start' reworded to 'from')"
  - "Manifest declares exactly POST_NOTIFICATIONS; WorkManager default startup initializer disabled so all androidx.work init stays in the reminders package"
  - "Extended ManifestPermissionsTest (allow {POST_NOTIFICATIONS} + deny INTERNET/SCHEDULE_EXACT_ALARM/health-WRITE, source-exact + merged-subset) and NotificationSeparationTest (seeds the Phase-22 flare structural scan)"
affects: [22]

# Tech tracking
tech-stack:
  added:
    - "androidx.work (WorkManager) 2.10.5 — work-runtime (impl) + work-testing (testImpl); first-party androidx, VersionCatalogAuditTests-clean"
  patterns:
    - "On-demand WorkManager initialization inside the reminders package (ReminderScheduler.resolveWorkManager guarded by WorkManager.isInitialized) with the default androidx.startup initializer DISABLED in the manifest — keeps every androidx.work import out of the composition root (the no-flare-notification separation invariant) AND lets WorkManagerTestInitHelper initialize without a double-init conflict"
    - "The reminder wired as a single ReminderController facade threaded from the composition root (AppGate Consented arm → ConsentedShell → LogTabHost → SettingsScreen → RemindersSection), mirroring the Plan-04 med/treatment seam posture; its FQN carries no notification/alarm token so importing it never trips the separation scan"
    - "JIT permission as an injected seam: RemindersSection takes requestNotificationPermission (default = a real RequestPermission launcher) so the just-in-time behavior is proven behaviorally off a fake; the section has NO LaunchedEffect, so the request can only fire from a user toggle (structural JIT proof)"
    - "Worker suppression factored into the pure/seam-driven CheckInReminderLogic.fire (disabled → quiet hours → pre-consent fail-safe → already-logged → post) so a logged-day / quiet-hours / pre-consent suppression is proven without standing up WorkManager's worker factory"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/reminders/QuietHours.kt
    - android/app/src/main/kotlin/com/endoapp/app/reminders/ReminderSettings.kt
    - android/app/src/main/kotlin/com/endoapp/app/reminders/ReminderScheduler.kt
    - android/app/src/main/kotlin/com/endoapp/app/reminders/ReminderNotifier.kt
    - android/app/src/main/kotlin/com/endoapp/app/reminders/CheckInReminderWorker.kt
    - android/app/src/main/kotlin/com/endoapp/app/reminders/ReminderController.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/ReminderCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/RemindersSection.kt
    - android/app/src/test/kotlin/com/endoapp/app/reminders/QuietHoursTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/reminders/ReminderSchedulingTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/reminders/ReminderWorkerSuppressTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/reminders/NotificationSeparationTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt
    - android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/tracking/SeverityControlTest.kt

decisions:
  - "Reworded UI-SPEC §7 'Quiet hours start' → 'Quiet hours from' (and 'end' → 'until' for parallel range wording): 'start' ⊃ the forbidden imperative 'take/start' substring, so the verbatim label would fail CopyAuditTest (same class as Plan 04's 'taken' ⊃ 'take'). Documented as a Rule-1 fix; every other reminder string is transcribed verbatim and re-scanned clean."
  - "WorkManager's default androidx.startup initializer is DISABLED in the manifest (tools:node=remove on the WorkManagerInitializer meta-data), and WorkManager is initialized ON DEMAND inside the reminders package (ReminderScheduler.resolveWorkManager, guarded by WorkManager.isInitialized). This keeps every androidx.work import out of AppDependencies/App/AppGate (the separation invariant — App implementing Configuration.Provider would have leaked androidx.work into the composition root and tripped NotificationSeparationTest) AND resolves the WorkManagerTestInitHelper double-init that the default startup provider caused under Robolectric."
  - "The MERGED-manifest allow assertion is NOT 'exactly {POST_NOTIFICATIONS}' (as UI-SPEC §7 literally stated) because WorkManager transitively merges four BENIGN permissions (WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE) — none a network data path, exact alarm, or health write. The SOURCE manifest is asserted EXACTLY {POST_NOTIFICATIONS}; the merged manifest is asserted to (a) allow POST_NOTIFICATIONS, (b) forbid INTERNET/SCHEDULE_EXACT_ALARM/health-WRITE, and (c) contain only the known benign WorkManager transitives + the app dynamic-receiver permission — so an unexpected injected permission still turns it red. RECEIVE_BOOT_COMPLETED (via WorkManager) is exactly the reboot-reschedule mechanism CONTEXT §Manifest anticipated; we declare no receiver of our own."
  - "Reminder collaborators + a single ReminderController facade were added and threaded through AppGate/ConsentedShell/LogTabHost/MainActivity/AppDependencies (beyond the plan's stated per-task file scope) — a Settings reminders section cannot drive scheduling/permission without them (Rule 2), mirroring Plan 04 threading its med/treatment seams through the same chain. The controller is bound in AppGate's Consented arm; the worker reaches its collaborators via App.dependencies."
  - "Notification built with the platform Notification.Builder (minSdk 26 guarantees the channel API) rather than NotificationCompat — avoids an extra androidx.core surface; small icon uses the framework android.R.drawable.ic_popup_reminder so no new drawable resource is needed."

metrics:
  duration: ~90min
  completed: 2026-07-09
  tasks: 3
  files: 24

status: complete
---

# Phase 20 Plan 05: Daily Check-In Reminder (WorkManager, JIT POST_NOTIFICATIONS, No-Flare-Notification Separation) Summary

**Added the daily check-in reminder as the app's ONLY notification surface, physically isolated in `com.endoapp.app.reminders`. Scheduling is WorkManager INEXACT periodic work only (never `SCHEDULE_EXACT_ALARM`, never `setExact*`) — idempotently rescheduled on every edit and reboot-surviving via WorkManager's own persistence (no receiver of ours). `POST_NOTIFICATIONS` is requested JUST-IN-TIME, only in the Settings master-toggle enable handler (never at launch), with graceful denial (calm copy + Open Settings, no nag loop). Exactly one notification channel; the notification is generic (no health/flare/streak wording) and its tap carries only the non-sensitive `EXTRA_REMINDER_CHECK_IN` flag → MainActivity → CheckInRouter → lands on the consent gate first. The worker auto-suppresses quiet hours (incl. the midnight-crossing 22:00–07:00 window) and a day already logged (consulting the DayEntry marker), and fails safe pre-consent — all without cancelling the repeating registration. The manifest adds only `POST_NOTIFICATIONS`; `ManifestPermissionsTest` now pins the allow-list {POST_NOTIFICATIONS} AND the deny-list (no INTERNET / exact-alarm / health-WRITE), and `NotificationSeparationTest` proves the reminders package/RemindersSection is the sole notification/alarm owner — seeding the Phase-22 flare structural scan.**

## Performance
- **Duration:** ~90 min
- **Completed:** 2026-07-09
- **Tasks:** 3 (+ one Rule-3 lint-unblock deviation)
- **Files:** 24 (12 created, 12 modified)

## Accomplishments
- **Task 1 — reminders package + inexact scheduler + one channel + worker + quiet hours (`0a53a8c`):** WorkManager 2.10.5 added to the catalog (first-party androidx, VersionCatalogAuditTests-clean). `ReminderSettings` (own Preferences DataStore, outside the health DB), pure `QuietHours.isInQuietHours` (midnight-crossing), `ReminderScheduler` (WorkManager INEXACT periodic, `enqueueUniquePeriodicWork` UPDATE, cancel-by-tag → idempotent reschedule, never an exact-alarm API), `ReminderNotifier` (exactly one `NotificationChannel`, generic `Notification.Builder` + the deep-link `PendingIntent`), `CheckInReminderWorker` + the pure `CheckInReminderLogic.fire` suppression gate, and the `ReminderController` facade. `ReminderCopy` registered in CopyAudit. `AppDependencies` wires the collaborators (no androidx.work leak); the manifest disables WorkManager's default startup initializer so init stays in the reminders package (on-demand).
- **Task 2 — Settings Reminders section, JIT permission, denied state, time + quiet-hours rows (`14c70de`):** `RemindersSection` fills the plan-04 reserved slot. The master `Daily reminders` toggle's ENABLE handler is the ONLY `POST_NOTIFICATIONS` request site (Android 13+; below 13 implicit) — on grant it seeds a 20:00 default + schedules; on denial the toggle stays OFF with the calm denied line + an `Open Settings` deep-link (no nag loop). Reminder-time + quiet-hours from/until time pickers reschedule idempotently on every edit. The `ReminderController` is threaded from the composition root through AppGate → ConsentedShell → LogTabHost → SettingsScreen.
- **Task 3 — manifest + extended guard + separation scan (`a54c216`):** Manifest adds only `POST_NOTIFICATIONS` (no RECEIVE_BOOT_COMPLETED of ours, no exact-alarm, no INTERNET). `ManifestPermissionsTest` extended (comment-safe DOM parse): source declares EXACTLY {POST_NOTIFICATIONS}; source + merged forbid INTERNET / SCHEDULE_EXACT_ALARM / health-WRITE; merged allows only the benign WorkManager transitives. `NotificationSeparationTest` scans all `:app` main sources and asserts every notification/alarm/WorkManager/`POST_NOTIFICATIONS` reference lives only under the reminders surface — seeding the Phase-22 flare scan.

## Task Commits
1. **Task 1: daily check-in reminder — WorkManager inexact scheduler + worker suppression** — `0a53a8c` (feat)
2. **Task 2: Settings reminders section — JIT POST_NOTIFICATIONS + quiet hours** — `14c70de` (feat)
3. **Task 3: manifest allow/deny guard + notification-separation scan** — `a54c216` (test)
4. **Rule-3 deviation: remember mutableIntStateOf in pre-existing SeverityControlTest (unblock lintDebug)** — `37379c1` (fix)

_No STATE.md / ROADMAP.md commit — per the objective, the orchestrator owns state updates for this run._

## Test Results (REAL, run on this host — Android SDK installed)
- `:app:testDebugUnitTest` — **182/182 passed, 0 failures, 0 errors** (was 156/156 after Plan 04; +26). New/extended this plan:
  - `QuietHoursTest` **7/7** — in-window / out-of-window / midnight-crossing (22:00–07:00) start-inclusive + end-exclusive boundaries, same-day window, zero-length window never quiet.
  - `ReminderSchedulingTest` **9/9** (Robolectric + `work-testing`) — enabling enqueues a PERIODIC (24h, inexact) unique work carrying the reminder tag; reschedule is idempotent (no duplication); disabling leaves no active reminder work; exactly one notification channel (idempotent); a comment-stripped reminders-package scan finds no exact-alarm/`setExact` call; **JIT** POST_NOTIFICATIONS requested only on toggle-enable (never at launch); grant seeds the 20:00 default + schedules + shows the reminders-on footer; denial shows the calm copy + Open Settings + leaves the toggle OFF; a source scan proves the section has no LaunchedEffect (no launch-time request).
  - `ReminderWorkerSuppressTest` **5/5** — a logged day suppresses (no notification); an enabled, unlogged, non-quiet day posts; disabled never posts; a fire inside quiet hours is suppressed; pre-consent repository access fails safe (no notification).
  - `NotificationSeparationTest` **3/3** — no source outside `com.endoapp.app.reminders`/`RemindersSection` imports a notification/alarm/WorkManager API or references POST_NOTIFICATIONS; non-vacuity guard pins the reminders surface exists.
  - `ManifestPermissionsTest` **4/4** (was 2/2; +2) — source declares exactly {POST_NOTIFICATIONS}; source + merged forbid INTERNET/exact-alarm/health-WRITE; merged allows only benign WorkManager transitives + the app dynamic-receiver permission.
  - Unregressed: `CopyAuditTest` green over the enlarged catalog (ReminderCopy passes the substring scan; the reworded 'Quiet hours from/until' clear 'start'), `VersionCatalogAuditTests` green (androidx.work is first-party), `AppGateConsentTest`, `SettingsScreenTest`, `CheckInFlowTest`, `SameAsYesterdayTest`, `MedicationTest`, `AbsenceNotZeroTest` all green.
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real `app-debug.apk`).
- `:app:lintDebug` — **BUILD SUCCESSFUL, 0 errors** (2 pre-existing `UnrememberedMutableState` errors in Plan-20-01's `SeverityControlTest` — the only errors, unrelated to this plan — fixed minimally; see Deviations).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - CopyAudit substring] 'Quiet hours start' ⊃ the forbidden imperative 'start'**
- **Found during:** Task 1 (pre-write substring scan of the ReminderCopy candidates against the ClaimsPolicy/ForbiddenTerms imperative list, which contains `start`).
- **Issue:** UI-SPEC §7 specified the quiet-hours bound labels verbatim as `Quiet hours start` / `Quiet hours end`, but "start" is a forbidden imperative token — registering it as-is fails `CopyAuditTest` (a hard must-have + threat T-20-02c). Same near-miss class as Plan 04's "taken" ⊃ "take".
- **Fix:** reworded to the range register `Quiet hours from` / `Quiet hours until` (both substring-clean, natural for a start/end range). Every other reminder string transcribed verbatim + re-scanned clean.
- **Files:** `ReminderCopy.kt`. **Commit:** `0a53a8c`.

**2. [Rule 3 - Blocking] Pre-existing lint errors failing the mandated `:app:lintDebug` gate**
- **Found during:** Task 3 verification (the plan's `:app:lintDebug` step).
- **Issue:** Two `UnrememberedMutableState` lint errors in `SeverityControlTest.kt:44,64` (introduced in Plan 20-01, an ancestor of this plan; my commits never touched the file) were the ONLY errors failing `lintDebug`, which this plan's verification + success criteria explicitly require green. Prior plans (20-01..04) did not run `lintDebug`, so the error was latent.
- **Fix:** wrapped the test-harness `mutableIntStateOf(...)` in `remember { }` (the lint rule is legitimately correct; no behavior change — `SeverityControlTest` still passes). Minimal, out-of-scope-but-gating fix per Rule 3, committed separately for transparency.
- **Files:** `SeverityControlTest.kt`. **Commit:** `37379c1`.

### Auto-added critical functionality

**3. [Rule 2 - Missing critical functionality] ReminderController facade + composition-root wiring**
- **Found during:** Task 2 (the Settings reminders section cannot drive scheduling/permission/prefs without the collaborators + a way to reach them from the running app).
- **Change:** added the `ReminderController` facade (bundling ReminderSettings/Scheduler/Notifier) and threaded a single `reminders: ReminderController?` param through `AppGate` (bound in the Consented arm), `ConsentedShell`, `LogTabHost`, and `SettingsScreen`, with `MainActivity` passing `dependencies.reminderController` and `AppDependencies` constructing the reminder collaborators. Beyond the plan's stated per-task file scope, but mirrors exactly how Plan 04 threaded its med/treatment seams through the same chain. The controller's FQN carries no notification/alarm token, so importing it never trips the separation scan.
- **Files:** `ReminderController.kt`, `AppDependencies.kt`, `AppGate.kt`, `MainActivity.kt`, `ConsentedShell.kt`, `LogTabHost.kt`, `SettingsScreen.kt`. **Commits:** `0a53a8c` (controller + deps) / `14c70de` (UI threading).

### Design decisions worth flagging

**4. WorkManager on-demand init + disabled default startup initializer (separation-preserving).**
- WorkManager's default `androidx.startup` `WorkManagerInitializer` is disabled in the manifest, and WorkManager is initialized on demand inside the reminders package (`ReminderScheduler.resolveWorkManager`, guarded by `WorkManager.isInitialized`). Implementing `Configuration.Provider` on `App` (the usual on-demand path) would have imported `androidx.work` into the composition root and tripped `NotificationSeparationTest`; on-demand init inside the package avoids that AND resolves the `WorkManagerTestInitHelper` double-init the default startup provider caused under Robolectric.

**5. Merged-manifest allow-set is a bounded superset, not exactly {POST_NOTIFICATIONS}.**
- WorkManager transitively merges WAKE_LOCK / ACCESS_NETWORK_STATE / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE (all benign — none a network data path, exact alarm, or health write). The source manifest is asserted exactly {POST_NOTIFICATIONS}; the merged manifest is asserted to allow POST_NOTIFICATIONS, forbid INTERNET/exact-alarm/health-WRITE, and contain only the known benign transitives + the app dynamic-receiver permission (an unexpected injected permission still turns it red). RECEIVE_BOOT_COMPLETED via WorkManager is the reboot-reschedule mechanism CONTEXT §Manifest anticipated.

## Threat Model Compliance
- **T-20-02 (Spoofing/EoP — notification deep-link, high — mitigate):** met. The `PendingIntent` carries only the non-sensitive `EXTRA_REMINDER_CHECK_IN` flag → MainActivity flips CheckInRouter → observed only in AppGate's Consented arm → lands on the gate. `AppGateConsentTest` green (a pre-consent flag lands on the gate).
- **T-20-02b (Tampering — exact-alarm avoidance, high — mitigate):** met. WorkManager INEXACT periodic only; `ManifestPermissionsTest` denies SCHEDULE_EXACT_ALARM (source + merged); `ReminderSchedulingTest` proves PERIODIC enqueue and a comment-stripped scan forbids exact-alarm/`setExact` in the reminders package.
- **T-20-02c (Information disclosure — notification content, medium — mitigate):** met. Generic audited copy (no symptom/flare/streak wording); exactly one channel; all strings registered + `CopyAuditTest` green.
- **T-20-02d (EoP — notification separation, high — mitigate):** met. `NotificationSeparationTest` proves the reminders package/RemindersSection is the sole notification/alarm owner (red-capable), seeding the Phase-22 flare structural scan; no flare code added; androidx.work init stays inside the package.
- **T-20-02e (Spoofing — POST_NOTIFICATIONS scope, medium — mitigate):** met. JIT request only in the enable handler (behavioral + no-LaunchedEffect scan); source manifest allow-list is exactly {POST_NOTIFICATIONS}.
- **T-20-SC (Tampering — WorkManager install, low — accept):** met. WorkManager is first-party androidx via the audited catalog (Maven, not npm/pip/cargo); `VersionCatalogAuditTests` green — no legitimacy checkpoint needed.

## Known Stubs
- None that block the plan's goal. The reminder is wired end-to-end (composition root → controller → scheduler/notifier/worker) and all load-bearing behaviors are test-proven on the JVM lane. Actual on-device notification *delivery* + timing and the lock-screen appearance can only be eyeballed on a physical device (Robolectric renders no real notification) — a device-verification follow-up, not a code stub.

## Self-Check: PASSED
- All 12 created files + 12 modified files exist on disk.
- All four commits present in git history: `0a53a8c`, `14c70de`, `a54c216`, `37379c1`.
- Real build/test evidence captured above (`:app` 182/182 incl. QuietHoursTest 7/7, ReminderSchedulingTest 9/9, ReminderWorkerSuppressTest 5/5, NotificationSeparationTest 3/3, ManifestPermissionsTest 4/4; CopyAudit/VersionCatalogAudit/AppGateConsent/SettingsScreen unregressed; `:insights` 176/176; `assembleDebug` SUCCESSFUL; `lintDebug` SUCCESSFUL 0 errors).

---
*Phase: 20-tracking-screens*
*Completed: 2026-07-09*
