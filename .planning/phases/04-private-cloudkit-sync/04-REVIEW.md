---
phase: 04-private-cloudkit-sync
reviewed: 2026-06-08T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - Sources/EndoApp/Persistence/AccountAvailability.swift
  - Sources/EndoApp/Persistence/ModelContainer+Config.swift
  - Sources/EndoApp/Persistence/Repository.swift
  - Sources/EndoApp/Features/Settings/SettingsView.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/App/EndoAppApp.swift
  - Sources/EndoApp/Resources/EndoApp.entitlements
  - Sources/EndoApp/Resources/Info.plist
  - project.yml
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-06-08
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Reviewed the Private CloudKit Sync phase against its security mandate: private-database-only sync, an account-aware `SyncMode` seam, the DATA-04 `deleteDay` fix, and unsigned-green simulator builds.

The core security posture is sound and the named traps are correctly closed:

- **CloudKit scope is private-only.** The entitlement declares exactly `iCloud.com.endoapp.ios`, `CloudKit` service only, and the only runtime activation path is `.private(cloudKitContainerIdentifier)` (ModelContainer+Config.swift:58). No `.public`/`.shared`/`.automatic` scope appears anywhere in `Sources` or `Tests`.
- **P-2 trap is closed on every construction path.** The `url:` overload hard-forces `cloudKitDatabase: .none` regardless of `syncMode` (line 69); the in-memory/default path computes `.none` unless the caller resolved `.cloudKit`; and both migration-test seams (`SchemaMigrationTests.swift:37,99`) pass `.none` explicitly. The entitlement cannot silently infer `.automatic` on a local path.
- **Account seam is correct.** `ubiquityIdentityToken` is read solely as a presence check (`!= nil`, AccountAvailability.swift:29); the token value is never logged or persisted. The seam is `Sendable`, synchronous (no `CKContainer.accountStatus` await), and tests/UI-tests pin `.localOnly` deterministically via `StubAccountAvailability` (AppDependencies.swift:52).
- **DATA-04 is fully fixed.** `deleteDay` (Repository.swift:229-242) deletes ALL day-scoped `FactorReading` rows via `factorReadings(on:)`, which fetches every `FactorReading` for the day with NO `kindRaw` filter (line 403-408) — so all `FactorKind`s (food/activity/sleep/medication/stress/mood/other) are removed, not just food. Non-day-scoped `Medication` and `TreatmentState` definitions are correctly NOT touched.
- **No regressions / no exfiltration / no analytics.** No third-party SDKs, no network calls, no `aps-environment: production`. Swift 6 concurrency on the new seams is correct (the delegate captures `router` into a local before the `@MainActor` hop, EndoAppApp.swift:72-77).

The findings below are correctness/maintainability defects and stale-documentation hazards — none rise to a security Critical, but two (WR-01, WR-02) carry real privacy/operational risk for the now-live CloudKit path.

## Warnings

### WR-01: Privacy manifest not updated for live CloudKit — carries its own Phase-4 TODO

**File:** `Sources/EndoApp/Resources/PrivacyInfo.xcprivacy:9-10`
**Issue:** The privacy manifest contains an explicit self-imposed instruction: "Revisit in Phase 4 when CloudKit sync flips on (declare the CloudKit reason; reconfirm empty collected-types is still truthful)." Phase 4 is this phase and CloudKit is now runtime-active (`.private(...)`), yet the manifest is unchanged — `NSPrivacyAccessedAPITypes` still lists only `UserDefaults` (CA92.1). For a GDPR Art. 9 health app, shipping a CloudKit-active build with a manifest that the authors themselves flagged as stale is a compliance/App-Store-review hazard. (This file is in the Phase-4 target but outside the explicit review list; flagged because it is directly germane to the phase's privacy mandate.)
**Fix:** Reconcile the manifest with the live CloudKit posture before this ships: confirm whether any additional required-reason API entry is needed for the iCloud/ubiquity-token read, and either remove the now-satisfied "Revisit in Phase 4" note or replace it with a dated, resolved note documenting that empty `NSPrivacyCollectedDataTypes` remains truthful (private CloudKit = user's own iCloud, not collection). Do not ship the build while the file still says "Revisit in Phase 4."

### WR-02: Store-recovery deletes a hardcoded filename that will not match a CloudKit-mirrored store

**File:** `Sources/EndoApp/App/AppDependencies.swift:99`
**Issue:** `discardUnreadableDefaultStore()` hardcodes `["default.store", "default.store-shm", "default.store-wal"]`. The doc comment defends this as safe because "Local-only this phase, so no synced data is at risk" — but Phase 4 makes the container CloudKit-mirrored whenever an account is signed in. If a future `cloudKit` store opens under a different filename (or the default store name diverges), the recovery path silently deletes nothing, the retry on line 74 re-hits the same corrupt store, and the app drops to in-memory (data not persisted across launches) — a latent data-loss/availability bug. The "no synced data is at risk" justification is now stale: a `.cloudKit` open failure is exactly when this runs.
**Fix:** Either (a) discover the actual store URL from the failed `ModelConfiguration`/`ModelContainer` rather than assuming `default.store`, or (b) on the recovery retry, build with `inMemory: true` for the `.cloudKit` mode (never silently wipe a store that may be mid-sync), and update the comment to reflect that CloudKit is now live. At minimum, narrow the destructive removal to the `.localOnly` path.

### WR-03: Stale entitlement comment in NotificationScheduler now contradicts the shipped build

**File:** `Sources/EndoApp/Reminders/NotificationScheduler.swift:9-10`
**Issue:** The header states: "NO capability/entitlement is added to project.yml: local notifications need none (no aps-environment, no Background Modes — those are for remote/push, which this app does not use)." Phase 4 now ships BOTH `aps-environment` (entitlements line 11) and `UIBackgroundModes: remote-notification` (Info.plist line 41). A maintainer reading this comment would conclude push is not configured and could mis-reason about the security surface or remove the now-required CloudKit push capability. (File outside the explicit review list but the contradiction is introduced by this phase.)
**Fix:** Update the comment to: local notifications still need no entitlement, but Phase 4 separately added `aps-environment`/Background-Modes for CloudKit silent push — those are unrelated to the local reminder scheduler.

### WR-04: `setStress` clamps to 0...10 via `clampSeverity` while the field is documented 0...10 but routed through a severity-named helper — verify range intent

**File:** `Sources/EndoApp/Persistence/Repository.swift:353-356`
**Issue:** `setStress` clamps with `Self.clampSeverity` (0...10), and `FactorDay.stress` is documented as a 0...10 scale, so the numeric range is correct. The concern is naming/coupling: stress shares `clampSeverity` with symptom severity, so a future change to the symptom severity range (e.g., to 0...5) would silently and incorrectly re-clamp stress. This is a latent coupling defect, not a current bug.
**Fix:** Introduce a dedicated `clampStress` (mirroring the dedicated `clampMood` at line 527) so the stress range is independent of symptom-severity changes, or add a comment at line 355 documenting the intentional shared 0...10 range.

## Info

### IN-01: Environment default for `accountAvailability` reads the real iCloud token in previews/tests

**File:** `Sources/EndoApp/App/AppDependencies.swift:165`
**Issue:** `AccountAvailabilityKey.defaultValue = SystemAccountAvailability()` means any view rendered WITHOUT the App-root injection (SwiftUI previews, isolated view tests) will read `FileManager.default.ubiquityIdentityToken` and render a non-deterministic "On"/"Off". The production path and UI tests inject the stub correctly, so this is preview-determinism only, not a shipping bug.
**Fix:** Default to `StubAccountAvailability(isICloudAvailable: false)` so unconfigured contexts deterministically show "Off" (matching the safe `.localOnly` default), consistent with the other seams' conservative defaults.

### IN-02: `Info.plist` comment cites the wrong decision id for Background Modes

**File:** `Sources/EndoApp/Resources/Info.plist:6`
**Issue:** The comment says "UIBackgroundModes (remote-notification, **D-04**)" but D-04 is the withdraw-consent decision (per SettingsView.swift:1 and the withdraw flow). Background remote-notification for CloudKit push is a sync decision (D-01/D-10/D-21 per project.yml:9). Wrong cross-reference; could misdirect a maintainer.
**Fix:** Replace `D-04` with the correct CloudKit-sync decision id (e.g., D-01/D-21).

### IN-03: `medicationsTaken` does not dedupe; defensive but worth noting

**File:** `Sources/EndoApp/Persistence/Repository.swift:457-459`
**Issue:** `makeFactorDay` appends every valid medication UUID without deduping. `setMedicationMarker` guards against inserting duplicate (day, medID) rows (line 486), so duplicates should not arise in practice — but a CloudKit merge of two devices could produce two identical `.medication` rows for the same day, surfacing a duplicate UUID in `medicationsTaken`. Low impact (consumers likely treat it as a set), but now reachable once sync is live.
**Fix:** Project into a `Set<UUID>` or dedupe before constructing `FactorDay` to make the snapshot merge-idempotent.

### IN-04: `project.yml` placeholder team is an intentional, commented-out value — confirmed clean

**File:** `project.yml:33`
**Issue:** Not a defect — recorded for completeness. `DEVELOPMENT_TEAM` is correctly a commented-out placeholder (`# DEVELOPMENT_TEAM: ABCDE12345`), so no stray real team id leaks and the simulator build stays unsigned-green. `CODE_SIGN_ENTITLEMENTS` is wired (line 31) and `CODE_SIGN_STYLE: Automatic` is set. This matches the phase requirement.
**Fix:** None required. Ensure the obviously-fake `ABCDE12345` example is never accidentally uncommented in CI.

---

_Reviewed: 2026-06-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
