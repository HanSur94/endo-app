---
phase: 04-private-cloudkit-sync
plan: 01
subsystem: database
tags: [cloudkit, swiftdata, icloud, entitlements, sync, account-detection, xcodegen]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: "The SyncMode / makeModelContainer seam with the pre-wired .private(cloudKitContainerIdentifier) branch (D-10/D-21)"
  - phase: 03-checkins-lifestyle
    provides: "The injectable-seam + EnvironmentKey house style (NotificationScheduler triad) mirrored by AccountAvailability"
provides:
  - "Committed private-DB-only CloudKit entitlement (EndoApp.entitlements) wired via CODE_SIGN_ENTITLEMENTS; inert without a Team ID"
  - "UIBackgroundModes remote-notification (D-04) for silent push-driven sync"
  - "AccountAvailability seam (Sendable protocol + SystemAccountAvailability(ubiquityIdentityToken) + StubAccountAvailability)"
  - "Account-aware SyncMode.resolve(_:) + makeModelContainer(syncMode:) caller-supplied parameter (resolve-before-construct, P-2)"
  - "Composition-root wiring: AppDependencies resolves SyncMode before building the container + injects \\.accountAvailability into the Environment"
  - "SyncModeTests (DATA-02 resolve logic + P-2 local-only-builds guard + CI-resolves-localOnly)"
affects: [04-02-settings-sync-status, 04-03-human-uat, cloudkit, settings]

# Tech tracking
tech-stack:
  added: []  # zero external packages — first-party Apple frameworks only (CloudKit/SwiftData/Foundation)
  patterns:
    - "Account-availability seam: Sendable (NOT @MainActor) injectable Bool query gating SyncMode"
    - "Resolve-before-construct: the composition root resolves SyncMode from the seam BEFORE building the container (P-2)"
    - "Explicit cloudKitDatabase: .none on every local-only path so a shipped entitlement cannot infer .automatic mirroring"

key-files:
  created:
    - Sources/EndoApp/Resources/EndoApp.entitlements
    - Sources/EndoApp/Persistence/AccountAvailability.swift
    - Tests/EndoAppTests/SyncModeTests.swift
  modified:
    - project.yml
    - Sources/EndoApp/Resources/Info.plist
    - Sources/EndoApp/Persistence/ModelContainer+Config.swift
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Tests/EndoAppTests/SchemaMigrationTests.swift

key-decisions:
  - "A1 HELD: the CloudKit entitlement does NOT break the unsigned Simulator build — full suite GREEN with no DEVELOPMENT_TEAM. No pivot to per-configuration gating needed."
  - "Account detection uses synchronous FileManager.default.ubiquityIdentityToken (D-03/P-5), NOT async CKContainer.accountStatus — the container builds without awaiting at launch."
  - "AccountAvailability is Sendable, NOT @MainActor (read off-main at container-build time) — so its EnvironmentKey default needs no MainActor.assumeIsolated, unlike the consent/scheduler keys."
  - "P-2 mis-activation was real: shipping the entitlement made ModelConfiguration(schema:url:) infer .automatic CloudKit on the local-only url overload AND in SchemaMigrationTests. Forced cloudKitDatabase: .none explicitly on every local path."

patterns-established:
  - "Account-availability seam (Sendable Bool query) — the fourth instance of the protocol+System+Stub triad after Repository/ConsentStore/NotificationScheduler"
  - "Explicit .none on local-only stores once a CloudKit entitlement ships in the target (P-2 guard)"

requirements-completed: [DATA-02, DATA-03]

# Metrics
duration: 22min
completed: 2026-06-08
---

# Phase 4 Plan 01: Private CloudKit Activation + Account-Aware SyncMode Summary

**Activated the pre-wired private-CloudKit seam: shipped a private-DB-only iCloud entitlement that stays inert without a Team ID (A1 confirmed — Simulator suite GREEN with no signing), made SyncMode account-aware via an injectable ubiquityIdentityToken-based AccountAvailability seam resolved before the container is built, and proved the local-only path stays a genuine .none store even with the entitlement present (P-2).**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-06-08T17:43:01Z
- **Completed:** 2026-06-08T18:04:42Z
- **Tasks:** 3 completed
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments

- **Task 1 — A1 de-risk (the feasibility crux):** Created `EndoApp.entitlements` (private iCloud container `iCloud.com.endoapp.ios` + CloudKit service + ubiquity-kvstore + `aps-environment=development`, **private DB only**), wired `CODE_SIGN_ENTITLEMENTS` + `CODE_SIGN_STYLE: Automatic` in `project.yml` with a **commented** `DEVELOPMENT_TEAM` placeholder, and added `UIBackgroundModes → remote-notification` to `Info.plist`. Ran the FULL existing suite on the Simulator: **GREEN with no team**. The feasibility crux is proven — the entitlement does not break the unsigned build, so no pivot was needed.
- **Task 2 — account-aware SyncMode (TDD RED→GREEN):** Wrote `SyncModeTests` first (confirmed RED — seam + `resolve` absent), then created the `AccountAvailability` seam (Sendable protocol + `SystemAccountAvailability` reading `ubiquityIdentityToken` + `StubAccountAvailability`) and replaced `SyncMode.current` with `SyncMode.resolve(_:)`. Added `syncMode: SyncMode = .localOnly` as the first `makeModelContainer` parameter. All 4 tests GREEN.
- **Task 3 — composition-root wiring:** `AppDependencies` now constructs the seam (`StubAccountAvailability(false)` under `-uitest-reset`, else `SystemAccountAvailability()`), resolves `SyncMode` and threads it into every `makeModelContainer` call (including all three recovery tiers, kept verbatim), exposes a new `\.accountAvailability` EnvironmentKey, and `EndoAppApp` injects it. Full suite GREEN, **zero CloudKit mirroring errors**.

## Verification Evidence

- **A1 gate (Task 1):** `xcodebuild test` full suite → `** TEST SUCCEEDED **` with no `DEVELOPMENT_TEAM`. Confirmed the unsigned Simulator build is unaffected by the entitlement.
- **SyncModeTests (Task 2):** 4/4 GREEN — `resolve(available)==.cloudKit`, `resolve(unavailable)==.localOnly`, `localOnly_buildsContainer` (P-2 guard — `.none` store usable with the entitlement present), `systemAvailability_inCI_resolvesLocalOnly` (real seam → `.localOnly`).
- **Full suite (Task 3):** `** TEST SUCCEEDED **`; `grep -Ec "134407|PFCloudKitSetupAssistant|NSCloudKitMirroringDelegate"` → **0** after the P-2 fixes (was 14 before).
- **Structural grep gates:** all Task 1–3 acceptance greps pass. Two greps had benign false positives explained below.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 / P-2 — Bug] Shipped entitlement silently mis-activated CloudKit on local-only stores**
- **Found during:** Task 3 (full-suite run surfaced `NSCloudKitMirroringDelegate` / `PFCloudKitSetupAssistant` error 134407 noise).
- **Issue:** Once Task 1 added the CloudKit entitlement to the target, `ModelConfiguration(schema:url:)` (the `url:` overload in `makeModelContainer`, used by `SchemaMigrationTests`) and the two direct `ModelConfiguration(schema:v1/v2Schema, url:)` constructions inside `SchemaMigrationTests` began inferring `.automatic` CloudKit mirroring from the entitlement — the exact P-2 mis-activation the plan warns about. Tests still passed, but the local-only migration stores were attempting CloudKit setup.
- **Fix:** Passed `cloudKitDatabase: .none` EXPLICITLY in the `url:` branch of `makeModelContainer` and in both `SchemaMigrationTests` `ModelConfiguration(...)` calls. This is what "KEEP the `url:` overload genuinely local" required once the entitlement ships.
- **Files modified:** `Sources/EndoApp/Persistence/ModelContainer+Config.swift`, `Tests/EndoAppTests/SchemaMigrationTests.swift`
- **Commit:** 21f044c
- **Result:** CloudKit mirroring error lines dropped from 14 → 0; the P-2 guard now holds across every local-only path.

### Acceptance-grep false positives (no code change warranted)

- **Task 1 `grep -Eqi 'public|shared'` (entitlements):** matched the mandatory plist DOCTYPE keyword `PUBLIC` (`<!DOCTYPE plist PUBLIC ...>`), NOT a CloudKit scope. Removing the DTD would produce a malformed plist. The real T-04-01 intent (private-DB-only) is satisfied: no `icloud-container-environment`/public/shared-database scope exists. Verified with a targeted grep for actual public/shared CloudKit scope keys (no match).
- **Task 2 `@MainActor` grep + Task 3 `assumeIsolated` grep:** both matched explanatory comment prose ("Sendable, NOT `@MainActor`" / "does NOT need `MainActor.assumeIsolated`"), not actual attributes/code. Confirmed no `@MainActor` attribute on any `AccountAvailability` declaration and the `AccountAvailabilityKey.defaultValue` constructs `SystemAccountAvailability()` directly.

## Threat Model Disposition

- **T-04-01 (private-DB-only):** Entitlement ships CloudKit service + private container only; no public/shared scope. Held.
- **T-04-03 (no token logging):** `AccountAvailability` uses `ubiquityIdentityToken` as a `!= nil` presence check only; never logs the token or record contents. Held.
- **T-04-04 (SyncMode not in synced store):** `SyncMode`/account state lives only in code/launch-args (composition root), never persisted to the store. Held.
- **T-04-05 / T-04-SC:** Zero external packages installed; no analytics/SDKs; slopcheck N/A. Held.

## Known Stubs

None. The only intentional placeholder is the **commented `DEVELOPMENT_TEAM`** in `project.yml` — a deliberate D-02 user-setup item (the user fills a real Team ID to activate device sync), not a code stub. The seam and resolution logic are fully wired.

## Notes for Downstream Plans

- **04-02 (Settings sync status):** read `\.accountAvailability` from the Environment (already injected) for the "iCloud sync: On/Off" line — the seam is in place; no new wiring needed.
- **04-03 (HUMAN-UAT):** real multi-device sync, offline reconcile, reinstall restore, and delete-purge are device-only. Activation requires the user to (a) uncomment + set `DEVELOPMENT_TEAM` in `project.yml`, (b) sign into iCloud on ≥2 devices, and (c) deploy the CloudKit schema (D-08). Until then everything resolves `.localOnly` and the app is fully usable with no account.
- **TDD gate:** Task 2 followed RED (commit 5bd666f, `test(...)`) → GREEN (commit 0d7e68f, `feat(...)`). Gate sequence satisfied.

## Self-Check: PASSED

All created files exist (EndoApp.entitlements, AccountAvailability.swift, SyncModeTests.swift, 04-01-SUMMARY.md) and all 4 task commits (94cfc32, 5bd666f, 0d7e68f, 21f044c) are present in git history.
