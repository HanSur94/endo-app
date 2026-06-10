---
phase: 04-private-cloudkit-sync
plan: 02
subsystem: settings
tags: [cloudkit, settings, privacy, delete-completeness, swiftdata, data-04, tdd]

# Dependency graph
requires:
  - phase: 04-private-cloudkit-sync
    plan: 01
    provides: "The \\.accountAvailability EnvironmentKey (injected by AppDependencies/EndoAppApp) consumed by the Settings On/Off line"
  - phase: 03-checkins-lifestyle
    provides: "The Phase-3 FactorReading table + factorReadings(on:) Repository helper that deleteDay must now also purge"
provides:
  - "Non-blocking 'iCloud sync: On/Off' Settings line (settings.sync.status) reading the account seam — no spinner/toggle/conflict UI (D-12)"
  - "Private-iCloud privacy footer copy in Settings (D-13)"
  - "deleteDay now removes ALL day-scoped SchemaV3 records incl. FactorReading — the automatable local half of DATA-04 (no orphan mirrors to CloudKit)"
  - "Extended EditDeleteTests: FactorReading-orphan assertion + a guard that Medication definitions survive a day-delete (T-04-08)"
affects: [04-03-human-uat, settings, cloudkit]

# Tech tracking
tech-stack:
  added: []  # zero external packages — first-party Apple frameworks only
  patterns:
    - "Read-only account-seam display in Settings (Bool → On/Off), never surfacing the ubiquityIdentityToken/account id (T-04-07)"
    - "Day-delete completeness: deleteDay enumerates every day-scoped table (incl. FactorReading) but never non-day-scoped definitions (Medication/TreatmentState)"

key-files:
  created:
    - .planning/phases/04-private-cloudkit-sync/04-02-SUMMARY.md
  modified:
    - Sources/EndoApp/Features/Settings/SettingsView.swift
    - Sources/EndoApp/Persistence/Repository.swift
    - Tests/EndoAppTests/EditDeleteTests.swift

key-decisions:
  - "PrivacyPolicyText left UNCHANGED (verify-only): the private-iCloud note + all PrivacyPolicyTests-asserted phrases (on-device, private iCloud, no servers, no third-party, data controller, contact email) were already present. The optional P-1 sign-out sentence was NOT added — the existing copy already satisfies D-13, and adding prose carries needless regression risk against the phrase asserts."
  - "deleteDay deletes FactorReading BEFORE the existing dayEntry deletion and only via factorReadings(on: day) — day-scoped only. Medication/TreatmentState are definitions/state (not day-scoped) and are deliberately untouched; a guard test locks this."

patterns-established:
  - "Read-only sync-status line: a Section showing Label + Spacer + Text(seam ? 'On' : 'Off') with an accessibility id, no blocking UI (D-12)"

requirements-completed: [DATA-04]  # DATA-02 surfacing also delivered; DATA-04 local half closed (device-only cloud purge → 04-03 HUMAN-UAT)

# Metrics
duration: 7min
completed: 2026-06-08
---

# Phase 4 Plan 02: Settings Sync Status + DATA-04 Delete-Completeness Summary

**Surfaced sync state to the user with a subtle, non-blocking "iCloud sync: On/Off" Settings line driven by the 04-01 account seam (plus the private-iCloud privacy footer), and closed the automatable local half of DATA-04 by fixing a real orphan bug — `deleteDay` now purges the Phase-3 `FactorReading` rows it previously left behind, while a guard test proves saved Medication definitions still survive a day-delete.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-06-08T18:09:02Z
- **Completed:** 2026-06-08T18:16:20Z
- **Tasks:** 2 completed
- **Files modified:** 3 (0 created source, 3 modified)

## Accomplishments

- **Task 1 — Settings sync line (D-12) + privacy verify (D-13):** Added `@Environment(\.accountAvailability)` to `SettingsView` (mirroring the existing `notificationScheduler` read) and a new `Section` placed just before the Privacy section: an `HStack` of `Label("iCloud sync", systemImage: "icloud")` + `Spacer()` + `Text(accountAvailability.isICloudAvailable ? "On" : "Off")` carrying `accessibilityIdentifier("settings.sync.status")`. NON-blocking — no `ProgressView`, no `Toggle`, no conflict UI (D-12). The footer states the private-iCloud posture: *"Endo-App syncs only to your own private iCloud — no third party, no account. When iCloud is off, everything stays on this device."* `PrivacyPolicyText` was VERIFIED unchanged — the private-iCloud note already exists and all `PrivacyPolicyTests` phrases stay green.
- **Task 2 — DATA-04 local orphan gap (TDD RED→GREEN):** Extended `logFullDay` to log a `FactorReading` (sleep + stress) and added a cascade assertion that zero `FactorReading` rows remain after `deleteDay` — confirmed **RED** (the real DATA-04 orphan bug). Added a guard test that a saved `Medication` definition survives the same day-delete (passed pre-fix, confirming `deleteDay` never touched it). Then fixed `Repository.deleteDay` to enumerate `factorReadings(on: day)` and `context.delete` each (hard delete, D-09/D-10), BEFORE the existing `dayEntries` deletion — **GREEN**.

## Verification Evidence

- **Task 1 suites:** `PrivacyPolicyTests` + `CopyAuditTests` → `** TEST SUCCEEDED **` (11 tests, 2 suites). The new footer copy is not in the `CopyAuditTests` audited surface (which audits Domain/cycle/Phase-3 enum labels), but contains no gendered token and no forbidden verb regardless.
- **Task 1 structural greps:** `settings.sync.status` present; `@Environment(\.accountAvailability)` present; `private iCloud` present; `grep -A6 settings.sync.status | grep -Eqi 'ProgressView|Toggle'` returns NON-zero (non-blocking, D-12). All pass.
- **Task 2 RED:** `delete_removesDay_andCascadesChildren` failed precisely on `FetchDescriptor<SchemaV1.FactorReading>().isEmpty` (EditDeleteTests.swift:70) before the fix; the med-survives guard already passed.
- **Task 2 GREEN:** `EditDeleteTests` → 4/4 GREEN.
- **Task 2 structural greps:** `factorReadings(on: day)` present inside `deleteDay`; `deleteDay` body does NOT delete `Medication`/`TreatmentState`; `FactorReading` asserted in the test. All pass.
- **Full suite:** `** TEST SUCCEEDED **` — **84 tests in 17 suites**, zero failures, no regression.

## Deviations from Plan

None — plan executed exactly as written. The optional P-1 sign-out privacy sentence was deliberately not added (verify-only path chosen, see key-decisions); this is an explicitly-sanctioned option in the plan, not a deviation.

## Threat Model Disposition

- **T-04-06 (GDPR Art. 17 / incomplete delete):** `deleteDay` now removes every day-scoped record including `FactorReading` → no orphan survives to mirror to the private CloudKit DB. Locked by the extended `EditDeleteTests` FactorReading-empty assertion. Mitigated.
- **T-04-07 (sync-status disclosure):** the Settings line renders only a `Bool`-derived "On"/"Off" — never the `ubiquityIdentityToken`, account id, or any health value. Mitigated.
- **T-04-08 (over-deletion):** the guard test proves a saved `Medication` definition survives a day-delete; `deleteDay` touches no non-day-scoped definition/state. Mitigated.
- **T-04-SC:** zero package-manager installs this plan; slopcheck N/A. Accepted.

## Known Stubs

None. The Settings line reads the live seam; the deleteDay fix is fully wired.

## TDD Gate Compliance

Task 2 followed the mandatory gate sequence: RED (commit `410b0df`, `test(...)` — FactorReading-orphan assertion fails before any fix) → GREEN (commit `ecc0985`, `fix(...)` — deleteDay deletes FactorReadings, suite passes). No premature pass; no refactor commit needed (the fix was already minimal and clean).

## Notes for Downstream Plans

- **04-03 (HUMAN-UAT):** the *cloud-purge proof* (delete on device A → record gone from private CloudKit → reinstall shows nothing) is device-only and remains routed to 04-03. This plan closes only the LOCAL precondition: a complete local delete is what lets CloudKit mirror the deletion. Until the user sets `DEVELOPMENT_TEAM` + signs into iCloud on ≥2 devices, everything resolves `.localOnly` and the Settings line reads "Off".

## Self-Check: PASSED

Created file `.planning/phases/04-private-cloudkit-sync/04-02-SUMMARY.md` exists. All three task commits present in git history: `50b6276` (Task 1 Settings line), `410b0df` (Task 2 RED test), `ecc0985` (Task 2 GREEN fix). Full suite GREEN (84/84).
