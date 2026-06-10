---
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
reviewed: 2026-06-08T16:12:46Z
depth: standard
files_reviewed: 25
files_reviewed_list:
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/App/AppGate.swift
  - Sources/EndoApp/App/CheckInRouter.swift
  - Sources/EndoApp/App/EndoAppApp.swift
  - Sources/EndoApp/Domain/FactorDay.swift
  - Sources/EndoApp/Domain/FactorKind.swift
  - Sources/EndoApp/Domain/FoodTag.swift
  - Sources/EndoApp/Domain/ActivityType.swift
  - Sources/EndoApp/Domain/ActivityIntensity.swift
  - Sources/EndoApp/Domain/SleepQuality.swift
  - Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift
  - Sources/EndoApp/Features/DayLog/DayLogEditorView.swift
  - Sources/EndoApp/Features/Home/HomeView.swift
  - Sources/EndoApp/Features/Settings/SettingsView.swift
  - Sources/EndoApp/Persistence/MigrationPlan.swift
  - Sources/EndoApp/Persistence/ModelContainer+Config.swift
  - Sources/EndoApp/Persistence/Models/FactorReading.swift
  - Sources/EndoApp/Persistence/Models/Medication.swift
  - Sources/EndoApp/Persistence/Repository.swift
  - Sources/EndoApp/Persistence/SchemaV3.swift
  - Sources/EndoApp/Reminders/NotificationScheduler.swift
  - Sources/EndoApp/Reminders/ReminderSettings.swift
  - Tests/EndoAppTests/FactorSnapshotTests.swift
  - Tests/EndoAppTests/SchemaMigrationTests.swift
  - Tests/EndoAppTests/NotificationSchedulerTests.swift
findings:
  critical: 2
  warning: 7
  info: 5
  total: 14
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-06-08T16:12:46Z
**Depth:** standard
**Files Reviewed:** 25
**Status:** issues_found

## Summary

Reviewed the Phase-3 lifestyle-logging + fast daily check-in + reminders implementation for a privacy-first GDPR Art. 9 health app. The CloudKit-safety posture of the two new `@Model`s (`FactorReading`, `Medication`) is correct — all properties optional/defaulted, no `@Attribute(.unique)`, app-generated UUIDs, no relationships, no cycle fields. The V2→V3 migration is correctly `.lightweight` (never `.custom`), and `SchemaMigrationTests` proves data survival. The consent gate is architecturally sound: `HomeView` (the only health-UI path) is built solely in `AppGate`'s `.consented` branch, and the deep-link router carries only a non-sensitive intent flag. Notification content leaks no health detail. Free-text is bounded at the Repository seam. Swift 6 concurrency seams in the notification delegate are handled correctly.

However, two correctness defects undermine phase-defining requirements:

1. **CR-01 — `cancelToday()` permanently destroys all repeating daily reminders** (D-19b). The auto-suppress-after-logging path removes the repeating `UNCalendarNotificationTrigger` registrations entirely; they do NOT "re-fire tomorrow." After the first check-in, reminders silently stop forever until the user re-arms them in Settings. This defeats the entire reminders feature.

2. **CR-02 — the food free-text "honesty contract" is asymmetric and silently drops data.** The note-bearing `.food` row is round-tripped only via the projection, but a free-text-only edit (no tags) on a day that previously had tags works, while the load path and `hasAnyInput` treat food-note presence inconsistently versus the persisted shape — see the analysis. More importantly, a logged free-text note can be lost on re-save in the check-in path.

The remaining warnings concern reminder-list row identity, quiet-hours UX desync, a misleading reminders footer, and a duplicated bounding constant. None of the test files have reliability defects.

## Critical Issues

### CR-01: `cancelToday()` permanently removes the repeating daily reminders (D-19b broken)

**File:** `Sources/EndoApp/Reminders/NotificationScheduler.swift:144-151`
**Issue:** `reschedule(times:quietHours:)` registers each reminder as a **repeating** trigger: `UNCalendarNotificationTrigger(dateMatching: time, repeats: true)` (line 135). `cancelToday()` then calls `center.removePendingNotificationRequests(withIdentifiers: ids)` over exactly those stable ids (`endo.reminder.0`, `.1`, …). Removing a *repeating* request deletes the registration outright — there is no per-occurrence cancellation in `UNUserNotificationCenter`. The doc comment claims "the repeating daily request re-fires tomorrow on its own" (lines 145-147, 597), but that is false for a repeating `UNCalendarNotificationTrigger`: once removed, it never fires again.

Concretely: a user enables reminders, does their first daily check-in, `DailyCheckInView.confirm()` → `autoSuppressAndDismiss()` → `scheduler.cancelToday()` wipes every reminder. Tomorrow and every day after, **no reminder fires** until the user goes back into Settings and toggles/edits to trigger `reschedule`. The phase's core "gentle daily nudge" requirement (CHCK-02 / D-19b) is silently dead after day one.

**Fix:** Do not cancel the repeating registration to suppress a single day. Either (a) deliver reminders as non-repeating, re-armed daily, or (b) keep the repeating trigger and suppress today by checking `shouldFireToday(lastLoggedDay:now:)` at delivery via `willPresent` / a fresh re-schedule that skips today. A minimal correct `cancelToday` that preserves tomorrow:

```swift
func cancelToday() async {
    // Removing a repeating request kills it forever — instead, leave the repeating
    // registration intact and rely on shouldFireToday() at presentation, OR re-schedule
    // a one-shot suppression. Do NOT removePendingNotificationRequests on repeating ids here.
    // If a same-day delivered notification must be cleared, remove DELIVERED (not pending):
    let delivered = await center.deliveredNotifications()
    let ids = delivered.map(\.request.identifier).filter { $0.hasPrefix(reminderIdentifierPrefix) }
    center.removeDeliveredNotifications(withIdentifiers: ids)
}
```

Whichever approach is chosen, add a unit/integration assertion that after `cancelToday()` the repeating requests are still pending so the regression cannot silently return. The existing `shouldFireToday` pure function is unused by the system scheduler — wire it in.

### CR-02: Food free-text note can be silently lost on re-save (honesty/data-loss)

**File:** `Sources/EndoApp/Persistence/Repository.swift:273-289` (`setFood`) and `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift:566-569`, `DayLogEditorView.swift:552-555`
**Issue:** `setFood(tags:freeText:on:)` deletes ALL `.food` rows for the day, then re-inserts one row per tag plus, only when `boundedNote(freeText) != nil`, one note-bearing row. The editors compute `freeText: foodNote.isEmpty ? nil : foodNote`. The data-loss window: on load, `foodNote` is populated from `factorDay.foodNote` (DayLogEditorView:505, DailyCheckInView:516). But `factorDays()` projection (Repository.swift:433-439) reads the note from the FIRST `.food` row whose `note != nil`. A food row carrying a tag has `note == nil`; the dedicated note row has `valueRaw == nil, note == set`. So far consistent.

The real defect is the interaction with whitespace bounding: `boundedNote` (line 511-515) returns nil for an all-whitespace note, but the view's `foodNote.isEmpty` check (false for `"  "`) passes `freeText: "  "`. `boundedNote("  ")` → `clipped = "  "`, `clipped.isEmpty == false` → returns `"  "` (whitespace is NOT trimmed here, unlike `commitNewMed` which trims). So a whitespace-only food note IS persisted as a meaningless note row — inconsistent with the med path and with `setNote`. More seriously, because `setFood` reuses the food `note` row to ALSO carry tags conceptually, any future code that inserts a tag row with a non-nil `note` would be misread; the projection's "first note-bearing row wins" silently drops a second note row.

**Impact:** Honesty contract states "unlogged factor must never collapse / fabricate"; here a blank-space paste fabricates a logged food note, and the asymmetric trimming between `setFood` and `commitNewMed`/`setNote` is an inconsistency that will surface as "I cleared the note but it's still there."

**Fix:** Make `boundedNote` trim whitespace and collapse blank → nil, exactly like `setNote` and `commitNewMed`, and have `setFood` treat a whitespace-only note as no note:

```swift
private static func boundedNote(_ text: String?) -> String? {
    guard let text else { return nil }
    let trimmed = String(text.prefix(maxNoteLength)).trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? nil : trimmed
}
```

Also assert in `FactorSnapshotTests` that a whitespace-only food note round-trips to `foodNote == nil`.

## Warnings

### WR-01: Reminder-time `ForEach` keyed by `.offset` corrupts row identity on delete/edit

**File:** `Sources/EndoApp/Features/Settings/SettingsView.swift:170-181`
**Issue:** `ForEach(Array(reminders.times.enumerated()), id: \.offset)` identifies each `DatePicker` by its array index. With `.onDelete` (line 178) and "Add a time" appending (line 388), SwiftUI reuses the offset identity across element changes: deleting time #1 makes former #2 inherit #1's view state, and an in-flight `DatePicker` edit can write through `reminderTimeBinding(index)` to the wrong slot after a deletion. Index-based identity with mutable, deletable rows is a known SwiftUI correctness trap.

**Fix:** Give `DateComponents` rows a stable identity. Wrap each reminder time in a small `Identifiable` struct (`struct ReminderTime: Identifiable { let id = UUID(); var components: DateComponents }`) and `ForEach` over those, or at minimum key by a value-derived id. Update `reminderTimeBinding` to resolve by id, not index.

### WR-02: Quiet-hours start/end binding silently fabricates a zero-width or inverted window

**File:** `Sources/EndoApp/Features/Settings/SettingsView.swift:413-436`
**Issue:** Editing quiet-hours START when no end exists sets `end = start` (line 418), producing a zero-width window where `isInQuietHours` is true only for that exact minute — effectively "no quiet hours" while the UI shows a configured window. Worse, a user who sets start=22:00 then end=07:00 gets the intended midnight-crossing window, but a user who sets only one bound gets a silently meaningless window with no feedback. There is also no way to CLEAR quiet hours back to `nil` from this UI once both bounds are set, so a user cannot turn quiet hours off again.

**Fix:** Either require both bounds before constructing a `QuietHours`, or add an explicit "Quiet hours" enable toggle that sets `reminders.quietHours = nil` when off. Default both pickers to a sensible window (e.g. 22:00–07:00) when first enabled rather than collapsing to start==end.

### WR-03: Reminders footer copy contradicts the enabled state ("Reminders are off.")

**File:** `Sources/EndoApp/Features/Settings/SettingsView.swift:221-227`
**Issue:** The section footer is the static string "Reminders are off. If you'd like a gentle nudge…". This text is shown even when `reminders.enabled == true` and times are configured, so an enabled user reads "Reminders are off." This is a correctness/UX defect (misinforms the user about whether their health-reminder is active) in a section whose accuracy matters for a privacy-sensitive opt-in feature.

**Fix:** Make the footer state-dependent, e.g. show the "off" guidance only when `!reminders.enabled`, and an "on / respects quiet hours" line when enabled.

### WR-04: `applySameAsYesterday` cannot un-set sticky context, only add it

**File:** `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift:467-489`
**Issue:** "Same as yesterday" assigns `sleepHours`/`activityMinutes` only when yesterday's value is non-nil (lines 477, 484), but writes `activityType`, `sleepQuality`, `activityIntensity`, `medsTaken`, `tookPRN` unconditionally. If the user first tapped "Same as yesterday" (prefilling meds) and then taps it again after editing, or if today already had draft state from `loadExistingState`, the partial-assignment leaves a mix of yesterday's and today's values that maps to neither day honestly. Example: yesterday had sleepHours=8 but no quality; tapping sets `sleepHours=8`, `sleepQuality=nil` — but if today's draft already had a quality from `loadExistingState`, sleepQuality is overwritten to nil while hours stays. The prefill is not atomic.

**Fix:** Make the prefill fully replace the sticky-context draft from the snapshot (assign every sticky field, using the snapshot's nil as nil, including resetting `sleepHoursSet`/`activityMinutes` to the snapshot), so the result equals yesterday's sticky context exactly, never a blend.

### WR-05: `factorReadings(on:)` does not assert consent though it reads health data

**File:** `Sources/EndoApp/Persistence/Repository.swift:397-401`
**Issue:** Every other read/write in `SwiftDataRepository` calls `assertConsented()` as defence-in-depth (the documented ONBD-01 invariant), but the public `factorReadings(on:)` does not. It is `internal`/`public` on the protocol surface and fetches `FactorReading` health rows directly. The omission weakens the stated "repository documents and asserts the consent precondition" guarantee and is an inconsistency a future caller could exploit to read health data without tripping the debug guard.

**Fix:** Add `assertConsented()` as the first line of `factorReadings(on:)`, matching `symptomReadings`/`painPoints` siblings (note those two are also missing it — see IN-04).

### WR-06: `cancelToday()` failure on first check-in is swallowed and untestable via the system path

**File:** `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift:598-603`
**Issue:** `autoSuppressAndDismiss()` fires a detached `Task { await notificationScheduler.cancelToday(); dismiss() }`. `dismiss()` is called from a non-`@MainActor`-guaranteed continuation after an `await` on a `@MainActor` protocol method; while `NotificationScheduler` is `@MainActor`, the `Task` here inherits the view's actor context so this is likely fine, but the dismissal is sequenced AFTER the scheduler call, meaning a slow/hanging notification-center call delays the sheet dismissal with no timeout. Combined with CR-01, this path also actively destroys the user's reminders. Independently of CR-01, dismissal should not be gated on a best-effort notification side effect.

**Fix:** Dismiss immediately and fire the suppression as a fully independent side effect:

```swift
private func autoSuppressAndDismiss() {
    let scheduler = notificationScheduler
    Task { await scheduler.cancelToday() }
    dismiss()
}
```

### WR-07: `HomeView.summaryLine` performs an unbounded full-table fetch per row render

**File:** `Sources/EndoApp/Features/Home/HomeView.swift:155-172`
**Issue:** `summaryLine(for:)` is called from `dayRow` for every visible history row and fetches the ENTIRE `SymptomReading` and `BleedingEvent` tables each time, then filters in memory. This is invoked during `body` evaluation on the main actor for each row. (Per scope rules pure O(n) performance is out of scope, but this is also a correctness/robustness concern:) any fetch error is swallowed to `[]`, so a transient store error makes a day with logged symptoms silently render "Note only" — misreporting the user's own health data.

**Fix:** Fetch once per render pass (or via a `@Query`/predicate scoped to the day) and surface, rather than silently hide, a fetch failure. At minimum, pull the two fetches out of the per-row path.

## Info

### IN-01: Duplicate free-text bound constant / clamp logic across seams

**File:** `Sources/EndoApp/Persistence/Repository.swift:141,511-515` and `Sources/EndoApp/Features/Settings/SettingsView.swift:454-455,331-332`
**Issue:** `maxNoteLength` bounding is re-implemented at the view layer (`commitNewMed` prefix-clamps again "defence in depth") and `commitFreeText` uses a separate `TerminologyStore.maxDisplayNameLength`. The whitespace-trim behavior differs between `setNote`/`commitNewMed` (trim) and `boundedNote` (no trim) — see CR-02. Consolidate the bound+trim into a single helper to prevent drift.

### IN-02: `Date: @retroactive Identifiable` keys on `timeIntervalSince1970` — collision-prone

**File:** `Sources/EndoApp/Features/Home/HomeView.swift:176-178`
**Issue:** Conforming `Date` to `Identifiable` app-wide via a retroactive extension is a smell: any other `.sheet(item:)`/`ForEach` over `Date` now silently adopts this identity, and two distinct `Date`s representing the same instant collide. It works for the single `editingDay` use today but is a footgun. Prefer a dedicated wrapper type for the sheet item.

### IN-03: `loggingStress && stress != nil` honesty guard is duplicated and fragile

**File:** `Sources/EndoApp/Features/CheckIn/DailyCheckInView.swift:320,543,583` and `DayLogEditorView.swift:337,569`
**Issue:** The stress honesty rule ("only persist when opted in") is expressed three ways: the section visibility (`loggingStress, stress != nil`), `hasAnyInput` (`loggingStress && stress != nil`), and the commit (`setStress(loggingStress ? stress : nil ...)`). If `loggingStress == true` but `stress == nil` (reachable transiently), the commit writes `setStress(nil)` which clears — correct, but the triple-encoding invites divergence. Consider a single computed `effectiveStress: Int?` used everywhere.

### IN-04: `symptomReadings`/`painPoints` read helpers also omit `assertConsented()`

**File:** `Sources/EndoApp/Persistence/Repository.swift:543-554`
**Issue:** Pre-existing from Phase 2, but in scope as a consistency note alongside WR-05: these public-on-the-struct fetch helpers read health rows without the consent assertion the writes carry. Low risk (callers are inside the consented branch) but worth aligning.

### IN-05: `discardUnreadableDefaultStore` hardcodes `default.store` filenames

**File:** `Sources/EndoApp/App/AppDependencies.swift:81-88`
**Issue:** The recovery helper deletes `default.store`/`-shm`/`-wal` by literal name. If SwiftData's default store filename ever differs (custom configuration name, future iOS change), recovery silently no-ops and the app falls through to in-memory, losing on-disk persistence without signal. Acceptable for now; consider deriving the URL from the `ModelConfiguration` instead of hardcoding.

---

_Reviewed: 2026-06-08T16:12:46Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
