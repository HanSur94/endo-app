---
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
plan: 04
subsystem: ui
tags: [swiftui, swiftdata, usernotifications, checkin, deeplink, accessibility, xcuitest, consent]

# Dependency graph
requires:
  - phase: 03-01
    provides: "Repository checkInMarker/set*/factorDays/savedMedications; NotificationScheduler.cancelToday; FactorDay snapshot; reserved checkin.* a11y ids"
  - phase: 03-02
    provides: "DayLogEditorView factor-section patterns + SeverityControl-for-stress reveal; daylog.factor.* ids"
  - phase: 03-03
    provides: "EnvironmentValues.notificationScheduler + NotificationSchedulerKey; Settings manage-meds (add/soft-archive); ReminderSettings"
provides:
  - "DailyCheckInView: the fast sectioned check-in sheet (CHCK-01) — symptoms -> 6 factor sections -> bleeding, nothing mandatory, checkin.* a11y ids"
  - "'Same as yesterday' sticky-context prefill (meds/sleep/activity only; never symptoms/bleeding) (D-14)"
  - "Empty-confirm adherence path: checkInMarker only, no invented data (D-15)"
  - "Auto-suppress today's reminder via scheduler.cancelToday() on confirm (D-19b)"
  - "Prominent Home 'Daily check-in' CTA (home.dailyCheckInButton) presenting the sheet"
  - "@Observable CheckInRouter + EnvironmentValues.checkInRouter deep-link routing seam"
  - "UNUserNotificationCenterDelegate (CheckInNotificationDelegate): userInfo[deepLink]==checkin -> MainActor -> router flag, presented ONLY inside AppGate .consented (T-03-14)"
  - "App-root injection of .notificationScheduler + .checkInRouter (the wiring 03-03 deferred)"
  - "CheckInFlowUITests GREEN: happy path + empty-confirm adherence + MANDATORY medication-taken E2E (LIFE-03)"
affects: ["Phase 4 CloudKit", "Phases 5-8 insights engine"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Observable router injected via EnvironmentKey (mirrors ConsentStoreKey) as a non-sensitive deep-link intent flag; presentation gated by AppGate .consented"
    - "nonisolated UNUserNotificationCenterDelegate hops to @MainActor (Task { @MainActor }) capturing a local router ref to avoid Swift 6 sending-self data race"
    - "Section a11y id on the Section HEADER Text (not the Section) so the section is discoverable without collapsing child controls"

key-files:
  created:
    - Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift
    - Sources/EndoApp/App/CheckInRouter.swift
  modified:
    - Sources/EndoApp/Features/Home/HomeView.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/App/AppGate.swift
    - Sources/EndoApp/App/AppDependencies.swift
    - Tests/EndoAppUITests/CheckInFlowUITests.swift
    - Tests/EndoAppUITests/ConsentFlowUITests.swift
    - Tests/EndoAppTests/CopyAuditTests.swift

key-decisions:
  - "Home CTA id standardized to home.dailyCheckInButton (plan frontmatter) — the Plan 01 scaffold's home.checkInButton was superseded; the E2E was rewritten to the frontmatter id."
  - "checkin.section.<factor> ids live on the Section HEADER Text, not the Section itself — a Section-level a11y id collapsed the child controls (food tags / med toggle) into one element, making them undrivable. This is the Plan 02 a11y learning applied to the check-in sheet."
  - "CheckInRouter is its own @Observable seam injected via EnvironmentKey; the notification delegate only flips a non-sensitive intent flag, and HomeView (built solely in AppGate .consented) presents the sheet — so a deep-link can never surface health UI before consent (T-03-14)."
  - "EndoAppApp builds AppDependencies once in init() (delegate needs the router ref) and seeds @State via State(initialValue:) — the inline default initializer was removed to avoid constructing a second ModelContainer."

requirements-completed: [CHCK-01, CHCK-02, LIFE-03]

# Metrics
duration: 30min
completed: 2026-06-08
---

# Phase 3 Plan 04: Fast Daily Check-in + Notification Deep-link Summary

**The Phase-3 loop is closed: a single fast `DailyCheckInView` sheet (symptoms -> six factor sections -> bleeding, nothing mandatory) reachable from a prominent Home CTA or a reminder notification deep-link; "Same as yesterday" prefills sticky context only (never symptoms/bleeding), an empty confirm records a checked-in adherence marker without inventing data, logging auto-suppresses today's reminder, and the whole flow — including the mandatory medication-taken E2E — is proven GREEN end-to-end while the consent invariant holds.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-06-08T15:31:03Z
- **Completed:** 2026-06-08T18:01:00Z (approx)
- **Tasks:** 3
- **Files modified:** 9 (2 created, 7 modified)

## Accomplishments

- `DailyCheckInView` — a sectioned `Form` in a `.sheet` (via `NavigationStack`): quick symptom chips -> food / activity / sleep / medication / stress / mood factor sections -> bleeding, every section skippable, nothing mandatory (D-13/15). Reuses the editor's MV structure (`SwiftDataRepository(context:)`, `loadExistingState()` on appear, commit-on-confirm), `SeverityControl` for stress (reveal-on-opt-in), and the `nil != 0` honesty contract.
- **"Same as yesterday"** (`checkin.sameAsYesterday`) reads yesterday's `FactorDay` and prefills STICKY CONTEXT ONLY (meds taken, sleep hours/quality, activity type/minutes/intensity). The handler provably touches NO symptom/bleeding draft state (T-03-15) — symptoms and bleeding are events that must not be carried forward.
- **Empty confirm** (D-15): confirming with nothing entered writes only `checkInMarker(on:)` (a bare `DayEntry` anchor) and surfaces "Checked in for today." — no symptom/factor data invented. Proven by the E2E re-opening the day and asserting the fatigue symptom is unselected.
- **Auto-suppress** (D-19b): a successful confirm calls `scheduler.cancelToday()` (read via `@Environment(\.notificationScheduler)`) so today's reminder won't nag; the repeating daily trigger re-fires tomorrow on its own.
- **Home CTA**: a prominent accent-tinted "Daily check-in" `Section`/`Button` (`home.dailyCheckInButton`, SF Symbol `checkmark.circle`) above "Log today", presenting the sheet.
- **Deep-link** (D-16): a `CheckInNotificationDelegate` (`UNUserNotificationCenterDelegate`) set early in the composition root reads `userInfo["deepLink"] == "checkin"`, hops to the main actor, and flips the injected `@Observable CheckInRouter`. `HomeView` — built ONLY inside `AppGate`'s `.consented` branch — observes the flag and presents the sheet, so the deep-link can never surface health UI before consent (T-03-14). `.environment(\.notificationScheduler,…)` + `.environment(\.checkInRouter,…)` are now injected at the App root (the wiring 03-03 deferred).
- **E2E**: `CheckInFlowUITests` turned GREEN with three flows — happy path (CTA -> sheet -> log a food tag -> "Done for today" -> day appears), empty-confirm adherence (no invented data), and the MANDATORY medication-taken path (add a med in Settings manage-meds -> mark taken in the check-in -> persists across a re-open).
- **Full Phase-3 suite GREEN** on the iPhone 16 simulator (unit + all UI E2E), including a fixed pre-existing consent-gate regression.

## Task Commits

Each task was committed atomically (hooks run, no `--no-verify`):

1. **Task 1: DailyCheckInView fast check-in sheet + Home CTA (CHCK-01)** — `b56c458` (feat)
2. **Task 2: notification deep-link delegate + scheduler/router injection (D-16)** — `3bcf571` (feat)
3. **Task 3: turn CheckInFlowUITests GREEN incl. mandatory med-taken (CHCK-01/LIFE-03)** — `ea71070` (test) — folds in the `checkin.section.*` header-id a11y correctness fix to `DailyCheckInView`.
4. **Integration fix: scroll withdraw button into view in ConsentFlowUITests** — `8214d53` (fix) — see Deviations.

## Files Created/Modified

- `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift` (created) — the fast sectioned check-in sheet + `CheckInCopy` constants.
- `Sources/EndoApp/App/CheckInRouter.swift` (created) — `@Observable CheckInRouter` + `EnvironmentValues.checkInRouter` deep-link seam.
- `Sources/EndoApp/Features/Home/HomeView.swift` — "Daily check-in" CTA, check-in `.sheet`, router observation (`onChange`/`onAppear`).
- `Sources/EndoApp/App/EndoAppApp.swift` — `CheckInNotificationDelegate`, delegate set in `init`, root injection of scheduler + router.
- `Sources/EndoApp/App/AppGate.swift` — documents/enforces the deep-link consent invariant in the `.consented` branch.
- `Sources/EndoApp/App/AppDependencies.swift` — `checkInRouter` composition-root property.
- `Tests/EndoAppUITests/CheckInFlowUITests.swift` — three GREEN flows (happy / empty-confirm / medication-taken).
- `Tests/EndoAppUITests/ConsentFlowUITests.swift` — scroll-into-view fix for the withdraw button.
- `Tests/EndoAppTests/CopyAuditTests.swift` — audit `CheckInCopy.auditableStrings`.

## Decisions Made

- **Home CTA id = `home.dailyCheckInButton`** (plan frontmatter), superseding the Plan 01 scaffold's `home.checkInButton`. The E2E was rewritten to the frontmatter id.
- **Section a11y id on the header `Text`, not the `Section`.** A `Section`-level `.accessibilityIdentifier` collapses the section's child controls into a single accessibility element, so `checkin.food.dairy` and the per-med taken toggle became undrivable. Moving each `checkin.section.<factor>` id onto the section's header `Text` keeps the section discoverable while leaving child controls individually addressable (the exact Plan 02 mood-row learning).
- **CheckInRouter as a dedicated `@Observable` Environment seam** rather than a flag on an existing store — keeps the deep-link intent isolated, carries no health data, and makes the consent-gated presentation explicit.
- **Single `AppDependencies` construction in `EndoAppApp.init()`** (the delegate needs the router reference); the inline `@State` default initializer was removed so a second `ModelContainer` is never built.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `checkin.section.*` ids on the `Section` collapsed child controls — moved to section headers**
- **Found during:** Task 3 (the happy-path E2E could not find `checkin.food.dairy`; the med E2E could not find the taken toggle).
- **Issue:** Putting `.accessibilityIdentifier("checkin.section.<factor>")` on the `Section` itself wrapped each section as one accessibility element, swallowing the tappable food-tag buttons and the medication toggle — the same a11y collapse Plan 02 hit on the mood row.
- **Fix:** Moved every `checkin.section.<factor>` id onto the section's header `Text`. The section stays discoverable by id; child controls are individually addressable again.
- **Files modified:** `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift`
- **Verification:** all three `CheckInFlowUITests` GREEN.
- **Committed in:** `ea71070` (Task 3, with the E2E that proves it).

**2. [Rule 1 - Bug] Swift 6 sending-self data race in the notification delegate**
- **Found during:** Task 2 (build error: "sending 'self' risks causing data races").
- **Issue:** `Task { @MainActor in router.presentCheckIn = true }` inside the nonisolated delegate captured task-isolated `self` to reach `self.router`.
- **Fix:** Bound `let router = self.router` before the `Task`; the `@MainActor` class reference crosses safely.
- **Files modified:** `Sources/EndoApp/App/EndoAppApp.swift`
- **Verification:** build SUCCEEDED.
- **Committed in:** `3bcf571` (Task 2).

**3. [Rule 3 - Blocking, integration] ConsentFlowUITests withdraw button below the fold (pre-existing regression surfaced by the full-suite gate)**
- **Found during:** the final full-phase suite run (this plan's explicit success gate).
- **Issue:** Plan 03-03 added the reminders + manage-meds sections to Settings (commit `15ad90c`), pushing the withdraw button below the fold. `ConsentFlowUITests.testConsentGateFlow` taps Settings then asserts the withdraw button exists WITHOUT scrolling, so it failed. Plan 03-03 never ran the full UI suite (only CopyAuditTests + the unit target), so the regression slipped through. This plan's success criteria require the full integrating suite to be GREEN.
- **Fix:** Added a scroll-into-view loop before the withdraw assertion (mirrors the suite's established pattern). No production change — `SettingsView`/`HomeView` were not touched by this fix.
- **Files modified:** `Tests/EndoAppUITests/ConsentFlowUITests.swift`
- **Verification:** `ConsentFlowUITests` GREEN; full suite GREEN.
- **Committed in:** `8214d53`.

---

**Total deviations:** 3 auto-fixed (1 a11y-collapse blocker, 1 Swift 6 concurrency bug, 1 cross-plan integration test fix required by the full-suite gate). No architectural changes; no API-shape deviations from the plan.

## Authentication Gates

None — no external service or login involved. Local notifications need no entitlement/capability.

## Known Stubs

None — every check-in control is wired to a Plan 01 Repository setter on confirm and hydrated from the `FactorDay` snapshot / saved-med list on appear. The empty-default draft state is the intentional, honest "not logged" state (D-15), not a stub.

## Threat Flags

None — no security surface beyond the plan's `<threat_model>`. The deep-link consent invariant (T-03-14), sticky-context-only prefill (T-03-15), empty-confirm-marker-only (T-03-16), and non-sensitive `deepLink` payload (T-03-17) are all implemented as planned.

## Verification Evidence

- `xcodebuild build` — **BUILD SUCCEEDED** (iPhone 16 simulator, Swift 6 language mode).
- `EndoAppUITests/CheckInFlowUITests` — 3/3 GREEN (happy path + empty-confirm adherence + mandatory medication-taken persistence).
- `EndoAppTests/CopyAuditTests` — GREEN (CheckInCopy contains no forbidden verb / gendered token).
- Full Phase-3 suite — **TEST SUCCEEDED**: all `EndoAppUITests` (Consent / Logging / CheckIn) GREEN, all `EndoAppTests` Swift Testing suites pass (Phase-2 copy audit, LIFE-01/02/03 factor logging, factor snapshot honesty, medication, notification fatigue, V1->V2 migration).

## Manual / Deferred Verifications (carried to the phase verification gate)

On-device notification DELIVERY, the system permission prompt, the denied-state, quiet-hours suppression, and soft-archive UI remain the Plan 03 human-verify items (VALIDATION.md Manual-Only) — XCUITest cannot assert system notifications. The deep-link routing logic itself is proven structurally (delegate reads `deepLink`, flips the router, presentation gated by `.consented`); only the actual OS notification tap is manual.

## Next Phase Readiness

- The Phase-3 loop is complete: fast check-in from Home or a reminder, with full honesty discipline and the consent invariant intact. CHCK-01 / CHCK-02 / LIFE-03 are proven end-to-end.
- The `FactorDay` insights seam is now written by both the detailed editor and the fast check-in — ready for Phases 5–8.
- No blockers introduced. The Phase-3 human-verify (on-device notifications) is the only open item for the phase verification gate.

## Self-Check: PASSED

- Created files present: `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift`, `Sources/EndoApp/App/CheckInRouter.swift`.
- Commits verified in `git log`: `b56c458`, `3bcf571`, `ea71070`, `8214d53`.
- Build SUCCEEDED; full Phase-3 test suite GREEN.

---
*Phase: 03-lifestyle-logging-fast-daily-check-in-reminders*
*Completed: 2026-06-08*
