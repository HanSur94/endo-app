---
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
plan: 03
subsystem: ui
tags: [swiftui, swiftdata, usernotifications, userdefaults, settings, reminders, ios]

# Dependency graph
requires:
  - phase: 03-01
    provides: "NotificationScheduler protocol (requestAuthorization/authorizationStatus/reschedule/cancelToday/cancelAll) + SystemNotificationScheduler + MockNotificationScheduler + QuietHours; Repository.addMedication/archiveMedication/savedMedications; ReminderCopy constants"
provides:
  - "ReminderSettings: UserDefaults-backed reminder config (enabled default OFF, times, quiet-hours) stored as minutes-of-day Ints, never in the synced store"
  - "EnvironmentValues.notificationScheduler + NotificationSchedulerKey (EnvironmentKey, MainActor.assumeIsolated default) + AppDependencies.notificationScheduler property"
  - "Settings Reminders section: opt-in master toggle, JIT permission on first enable, add/remove times, quiet hours, graceful denied-state with Open-Settings path"
  - "Settings Manage-meds section: bounded add + soft-archive remove via Repository"
affects: [04-checkin-sheet-deeplink-reminder-wiring, phase-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UserDefaults device-pref store mirroring TerminologyStore (@Observable @MainActor), minutes-of-day Int round-trip for time config"
    - "@MainActor service injected via EnvironmentKey with MainActor.assumeIsolated default (mirrors ConsentStoreKey/TerminologyStoreKey)"
    - "Just-in-time permission: requestAuthorization called ONLY inside the master-toggle enable handler, never at launch/onAppear"

key-files:
  created:
    - Sources/EndoApp/Reminders/ReminderSettings.swift
  modified:
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/Features/Settings/SettingsView.swift

key-decisions:
  - "Reminder config stored as minutes-since-midnight Ints in UserDefaults (clean round-trip, no DateComponents/Date encoding ambiguity); device-pref only, never in the CloudKit-bound store (A2 / T-03-12)."
  - "requestAuthorization confined to enableReminders() (only reachable from master-toggle ON) so permission stays just-in-time (D-20 / T-03-11); onAppear only PASSIVELY reads authorizationStatus (no prompt)."
  - "Reused the landed ReminderCopy constants for the notification body instead of the UI-SPEC literal 'here's your daily check-in' — that literal contains the 'her' substring the CopyAudit forbiddenGenderedTokens guard rejects; 03-01 already resolved this to 'Whenever you have a moment, your daily check-in is ready.'"
  - "AppDependencies declares the scheduler property + EnvironmentKey but does NOT inject .environment(\\.notificationScheduler,…) at the App root — that wiring belongs to Plan 04 (deep-link delegate), keeping 03-03 file-disjoint from the App-root files Plan 04 owns."

patterns-established:
  - "Settings free-text add bounded with SwiftDataRepository.maxNoteLength prefix-clamp at the UI commit AND the Repository seam (defence in depth, T-03-10)"
  - "Destructive med removal routed through .confirmationDialog(presenting:) with soft-archive (archiveMedication), never a hard delete (D-06 / T-03-13)"

requirements-completed: [CHCK-02, LIFE-03]

# Metrics
duration: ~25min
completed: 2026-06-08
---

# Phase 3 Plan 03: Reminders + Manage-Meds Settings Summary

**Opt-in (default-OFF) daily reminders with just-in-time notification permission, add/remove times, quiet hours, a graceful denied-state, and a soft-archive manage-meds section — all driven through the injected NotificationScheduler and a new UserDefaults-backed ReminderSettings.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-06-08T15:01:00Z (approx)
- **Completed:** 2026-06-08T15:27:00Z
- **Tasks:** 2 automatable executed + committed; 1 human-verify checkpoint auto-approved (deferred)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `ReminderSettings` — an `@Observable @MainActor` UserDefaults store: `enabled` (default **false**, D-18), `times: [DateComponents]`, and an optional `quietHours` window, persisted as minutes-of-day `Int`s. Device-pref only; contains no SwiftData/`@Model`/`modelContext` (A2 / T-03-12).
- `NotificationScheduler` is now a composition-root property on `AppDependencies` plus an `EnvironmentValues.notificationScheduler` accessor backed by `NotificationSchedulerKey` (EnvironmentKey with the `MainActor.assumeIsolated { SystemNotificationScheduler() }` default), mirroring the consent/terminology stores exactly.
- Settings **Reminders** section: master `Toggle` (OFF by default + gentle footer), JIT `requestAuthorization` on first enable, add/remove `DatePicker(.hourAndMinute)` time rows (swipe-to-delete), quiet-hours start/end pickers, and idempotent re-arm via `scheduler.reschedule(...)` on every edit. Denied state shows the non-nagging line + an "Open Settings" button (`UIApplication.openSettingsURLString`); the app stays fully usable.
- Settings **Manage meds** section (D-06): lists `savedMedications()`, bounded add via `addMedication` (clamped to `maxNoteLength`), and a soft-archive remove (`archiveMedication`) behind the destructive `.confirmationDialog` ("Days you already logged it as taken are not changed.").
- Build green on the iPhone 16 simulator; `EndoAppTests/CopyAuditTests` and the full `EndoAppTests` unit target pass.

## Task Commits

Each task was committed atomically (hooks run, no `--no-verify`):

1. **Task 1: ReminderSettings (UserDefaults) + inject NotificationScheduler into AppDependencies** - `e5d1c09` (feat)
2. **Task 2: Reminders section + manage-meds section in SettingsView** - `15ad90c` (feat)
3. **Task 3: Human-verify reminders delivery / permission / denied-state / soft-archive** - auto-approved checkpoint (see *Human verification deferred*)

**Plan metadata:** committed separately (docs).

## Files Created/Modified

- `Sources/EndoApp/Reminders/ReminderSettings.swift` (created) - UserDefaults-backed reminder config, default OFF, minutes-of-day Int persistence, device-pref only.
- `Sources/EndoApp/App/AppDependencies.swift` (modified) - `notificationScheduler` property + `NotificationSchedulerKey`/`EnvironmentValues.notificationScheduler` accessor.
- `Sources/EndoApp/Features/Settings/SettingsView.swift` (modified) - Reminders section (opt-in/JIT/quiet-hours/denied-state) + Manage-meds section (bounded add + soft-archive remove).

## Decisions Made

- **Minutes-of-day Int persistence** for reminder times and quiet-hours bounds in UserDefaults — avoids `DateComponents`/`Date` encoding ambiguity and round-trips cleanly.
- **JIT permission discipline:** `requestAuthorization` lives only in `enableReminders()`, reachable solely from the master-toggle ON path. `onAppear` performs a *passive* `authorizationStatus()` read (no prompt) to drive the denied-state UI.
- **Reused `ReminderCopy`** notification constants instead of the UI-SPEC's verbatim "here's your daily check-in" body, because that literal contains the forbidden "her" substring the CopyAudit guard rejects; 03-01 already shipped the audited wording.
- **Environment injection at the App root is intentionally deferred to Plan 04** (deep-link delegate) — 03-03 only declares the key + property to stay file-disjoint from the App-root files Plan 04 owns.

## Deviations from Plan

None requiring auto-fix rules. One intentional copy substitution (noted under Decisions): the gentle notification body uses the already-audited `ReminderCopy.body` ("Whenever you have a moment, your daily check-in is ready.") rather than the UI-SPEC's literal "here's your daily check-in", which fails the CopyAudit `forbiddenGenderedTokens` "her"-substring guard. This is a copy-source decision, not a behavioral deviation — the 03-01 constants are the canonical audited strings.

## Issues Encountered

None. The acceptance-criteria grep for "no SwiftData in ReminderSettings" initially matched an explanatory comment that contained the literal token "SwiftData"; the comment was reworded to "synced persistence store" so the file contains no `SwiftData`/`@Model`/`modelContext` reference at all (intent preserved).

## Human verification deferred

The Task 3 `human-verify` checkpoint was **auto-approved** under AUTO_MODE (it covers on-device system behavior that XCUITest cannot assert — it is **not** a package-legitimacy gate). The following must still be confirmed by a human on a real device/simulator at the phase verification gate. These map to the **Manual-Only** rows in `03-VALIDATION.md`:

1. **Local notification actually fires** — Enable a reminder ~1–2 minutes in the future, background the app, and confirm the banner appears with the gentle copy (`ReminderCopy.title` / `ReminderCopy.body`), with **no** health detail and **no** streak/guilt language. *(VALIDATION row: "A real local notification fires at the scheduled time…", CHCK-02.)*
2. **Just-in-time permission prompt** — On a fresh install, flip the master toggle ON and confirm the **system** notification-permission alert appears just-in-time (NOT at launch). *(VALIDATION row: "Notification permission prompt appears just-in-time on first enable…", CHCK-02 / D-20.)*
3. **Denied-state degrades gracefully** — On a fresh install, enable and **deny** the permission; confirm the reminders section shows the non-nagging denied line + a working "Open Settings" button, and the rest of the app stays fully usable. *(Same VALIDATION row as #2.)*
4. **Quiet-hours suppression** — Confirm a reminder time inside the configured quiet-hours window does not fire (the pure `isInQuietHours` logic is unit-tested in 03-01; on-device delivery suppression is the manual half). *(CHCK-02 / D-19a.)*
5. **Soft-archive removal** — In Manage meds, add a medication then remove it; confirm the "Remove this medication?" dialog and that removal is a soft-archive (the med disappears from the list, prior taken-history unchanged). *(D-06 — the Repository half is unit-tested in `MedicationTests`; the Settings UI path is the manual confirm.)*

Everything automatable IS covered green: opt-in default-OFF is asserted by `ReminderSettings` initializing `enabled` from an absent key (false); JIT permission is asserted structurally (no `requestAuthorization` in `EndoAppApp.swift`/`AppDependencies.swift`, only inside the enable handler); reminder/med copy is audited by `CopyAuditTests`; and med add/archive round-trips in `MedicationTests`. The scheduler protocol + `MockNotificationScheduler` remain available for any further reminder-flow unit tests.

## User Setup Required

None - no external service configuration required. Local notifications need no entitlement/capability (no aps-environment, no Background Modes) — first-party `UserNotifications` only.

## Next Phase Readiness

- `EnvironmentValues.notificationScheduler` is declared and ready for Plan 04 to inject at the App root (`.environment(\.notificationScheduler, deps.notificationScheduler)`) alongside the deep-link delegate.
- `ReminderSettings` is ready to be read by the check-in flow if Plan 04 needs the active reminder config; `cancelToday()` (auto-suppress, D-19b) is available to call once a day is logged.
- No blockers. The deferred on-device verifications above must be surfaced at the phase verification gate.

## Self-Check: PASSED

- Created file present: `Sources/EndoApp/Reminders/ReminderSettings.swift` ✓
- Commits present: `e5d1c09` ✓, `15ad90c` ✓
- Build SUCCEEDED + `EndoAppTests` (incl. `CopyAuditTests`) green ✓

---
*Phase: 03-lifestyle-logging-fast-daily-check-in-reminders*
*Completed: 2026-06-08*
