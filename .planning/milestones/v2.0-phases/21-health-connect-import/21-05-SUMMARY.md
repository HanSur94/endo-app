---
phase: 21-health-connect-import
plan: 05
subsystem: health-connect
status: complete
tags: [health-connect, composition-root, availability-gate, permission-contract, foreground-import, consent-by-construction, separation, robolectric]
requires:
  - "21-01 — HealthAvailability (three-state getSdkStatus gate), HealthConnectPermissions.REQUESTED"
  - "21-02 — HealthConnectReader (the real HC adapter over HealthConnectClient) + HealthReader seam"
  - "21-03 — HealthImportCoordinator (consent-gated read→persist→advance-cursor) + ChangesTokenStore + FakeHealthReader"
  - "21-04 — HealthConnectController seam + HealthImportState + HealthConnectSection + HealthImportScreen + FakeHealthConnectController"
  - "20-03 — AppDependencies composition-root pattern (own DataStore files built outside the Room DB); the reminder-stack posture mirrored"
  - "19 — AppGate consent-by-construction (Consented-arm binding); AppGateConsentTest precedent; NotificationSeparationTest"
provides:
  - "RealHealthConnectController — the concrete production HealthConnectController: availability-gated nullable reader, per-session getGrantedPermissions, foreground import via the coordinator, empty-grant/SecurityException → honest Denied"
  - "AppDependencies.healthConnectController — the HC stack constructed at the composition root (own changes-token DataStore, coordinator over the consent-gated repo, availability-gated reader)"
  - "HealthImportHost — the real PermissionController.createRequestPermissionResultContract launcher wired onto HealthImportScreen"
  - "TrackingRoute.HealthImport — the connect/import destination reachable ONLY via the Settings connect entry"
  - "EndoRepository.hasImportedData() — the consent-gated honest empty-vs-data signal"
  - "HealthConnectFlowTest + HealthSeparationTest — the end-to-end wiring proof + the health-package separation guard"
affects:
  - "Phase 22 attaches HealthSourceBadge inline next to imported day-log values (the controller/coordinator already persist provenance)"
tech-stack:
  added: []
  patterns:
    - "Availability-gated nullable reader: getOrCreate is called ONLY when getSdkStatus == AVAILABLE (never blindly); an UNAVAILABLE device yields a null reader → no client work, honest empty state, no crash"
    - "Per-session grant read on every import/grantedHistory (never a cached hcAuthorized flag); empty grant → Denied; SecurityException mid-read → Denied, never a crash"
    - "Consent-by-construction threading: the controller is bound ONLY in AppGate's Consented arm and passed down as a nullable param defaulted null at each hop (repository/reminders posture), so the connect surface is unconstructible pre-consent"
    - "Real permission request via PermissionController.createRequestPermissionResultContract() + rememberLauncherForActivityResult, with a stashed per-request result callback (the launcher's callback is fixed at remember time)"
    - "Foreground-only import trigger (permission result + returning-user visit + manual refresh) — NO WorkManager, NO notification; the reminders package stays the sole notification owner"
key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/HealthImportHost.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/HealthConnectFlowTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/HealthSeparationTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthConnectController.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppGate.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingRoute.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt"
decisions:
  - "The concrete impl is RealHealthConnectController (a new class in HealthConnectController.kt), leaving the Plan-04 interface + FakeHealthConnectController untouched — the controller interface signature is unchanged so all Plan-04 UI tests keep compiling."
  - "hasData is read from the STORE (EndoRepository.hasImportedData()), not from the run's imported count — so a differential refresh that imports no NEW rows still honestly reports the data already present rather than a false empty."
  - "Denial is detected from the per-session granted set (empty ⇒ Denied) plus a SecurityException catch (a revocation race) — no interface change, honest without a cached flag."
  - "The returning-user connect-screen-visit trigger fires only when AVAILABLE && grantedHistory() (an 'already connected' proxy using existing interface methods), preserving the rationale-before-permission gate for first-time users."
  - "The controller is ALWAYS threaded from the composition root (never null in production); the availability-gate nullability lives in the READER, and the Settings section renders the honest per-availability surface on tap (the Plan-04 posture). The nullable controller param stays for isolation/tests."
requirements: [AHC-01, AHC-03, AHC-04]
metrics:
  duration: "~50m"
  completed: "2026-07-09"
  tasks: 2
  files_created: 3
  files_modified: 8
  app_tests: 237
---

# Phase 21 Plan 05: Health Connect Composition-Root Wiring + Foreground Import Summary

Closed the phase end-to-end: built the concrete `RealHealthConnectController` over the Plan 01–03 seams (the availability gate, the real `HealthConnectReader`, the `HealthImportCoordinator`), constructed the whole Health Connect stack at the composition root in `AppDependencies` (its own changes-token DataStore outside the health DB, an availability-gated NULLABLE reader that only calls `getOrCreate` when the provider is AVAILABLE, and the controller the shell reads), threaded the controller through `AppGate`'s Consented arm → `ConsentedShell` → `LogTabHost` → the Settings connect entry, and wired the REAL `PermissionController` request contract onto the import screen so the import runs as a foreground coroutine (permission result + returning-user visit + manual refresh) with NO WorkManager and NO notification. The running app can now genuinely connect and import — consent-by-construction — with imported rows flowing through the same Phase-18 repository/merge the dashboard consumes. Proven runnably on the JVM lane against a scripted `FakeHealthReader` + a real in-memory Room repository, with a new `HealthSeparationTest` pinning the health package's notification-free purity.

## What Was Built

**Task 1 — concrete `RealHealthConnectController` + the `AppDependencies` HC stack**
- `RealHealthConnectController` (added to `HealthConnectController.kt`) — the production impl over injected seams: `availabilityProvider` (per session), `readerProvider` (a `HealthReader?` — null unless AVAILABLE), `coordinatorFor` (builds a fresh `HealthImportCoordinator` over the reader), and `hasImportedData` (the honest empty-vs-data signal). `importNow()`/`refresh()` resolve the reader, read the granted set per session (empty ⇒ `Denied`), run the coordinator on a background dispatcher, and land `Done(hasData, grantedPermissions)`; a null reader (unavailable) does NO client work and reports the honest empty terminal; a `SecurityException` mid-read is caught → `Denied`. No notification/alarm/WorkManager import.
- `AppDependencies` — constructs the HC stack mirroring the reminder stack: a `changesTokenDataStore` (its OWN `preferencesDataStoreFile`, built OUTSIDE the Room health DB) → `ChangesTokenStore`; the availability-gated reader factory (`getOrCreate` only when `HealthAvailability.availability(context) == AVAILABLE`); the coordinator over the consent-gated `repository`; and the `healthConnectController` the shell reads. No `androidx.work`/notification import added here.
- `EndoRepository.hasImportedData()` — consent-gated `allImported().isNotEmpty()`, the honest "the store holds imported rows" query.

**Task 2 — threading + the real permission request + foreground trigger + flow/separation tests**
- Threading: `healthConnect: HealthConnectController?` bound in `AppGate`'s `Consented` arm ONLY, passed through `ConsentedShell` → `LogTabHost` (nullable, defaulted null at each hop so the Phase-19/20 call sites + tests keep compiling) → `SettingsScreen` (already accepted it from Plan 04). `MainActivity` wires `dependencies.healthConnectController` at the composition root.
- `HealthImportHost` — the Compose host that binds the REAL `PermissionController.createRequestPermissionResultContract()` launcher onto `HealthImportScreen.onRequestPermissions`, stashing the in-flight result callback (an `ActivityResultLauncher`'s callback is fixed at `remember` time) and invoking it with the granted set on return. On a returning-user visit (AVAILABLE && history granted) it kicks the foreground import immediately; a not-yet-granted visit shows the rationale (rationale-before-permission preserved). Reachable only via `TrackingRoute.HealthImport`, pushed from the Settings connect entry.
- `TrackingRoute.HealthImport` — the new intra-Log destination (encode/decode `"HC"`), rendered guarded on a non-null controller.
- `HealthConnectFlowTest` (Robolectric + Compose + real in-memory Room) and `HealthSeparationTest` (structural source-scan of the health package).

## Real Test Results (this host, Android SDK platform 36, JDK 21)

- `./gradlew :app:testDebugUnitTest --tests "*HealthConnectFlowTest" --tests "*HealthSeparationTest" --tests "*NotificationSeparationTest" --tests "*AppGateConsentTest"` — **BUILD SUCCESSFUL** (all green).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :insights:test` — **BUILD SUCCESSFUL**. Full `:app` unit suite **237 tests, 0 failures / 0 errors** (228 from 21-04 + 9 new); `:insights` **176 tests, 0 failures**; `assembleDebug`, `lintDebug`, and the androidTest Kotlin compile all SUCCESSFUL. No regression.
- **Manifest still read-only** (merged manifest independently verified): exactly the 4 health READ perms + `READ_HEALTH_DATA_HISTORY` + the known-benign WorkManager transitives (`ACCESS_NETWORK_STATE`/`FOREGROUND_SERVICE`/`RECEIVE_BOOT_COMPLETED`/`WAKE_LOCK`) + `POST_NOTIFICATIONS` (reminders). **NO `INTERNET`. NO health `WRITE_*` in any `<uses-permission>`** ("WRITE_*" appears only in manifest comments). The wiring added no permission.
- **Separation invariant:** `HealthSeparationTest` green — the health package (incl. `ui`) imports no notification/alarm/WorkManager API and references no `POST_NOTIFICATIONS`; `NotificationSeparationTest` stays green.

### New coverage (9 tests)
- **HealthConnectFlowTest (6):** wired import persists an `ImportedSample` row through controller → coordinator → repo (Done, hasData=true, granted surfaced); UNAVAILABLE → no client work (reader never touched, nothing imported, honest empty, no crash, no history); empty grant → `Denied`, nothing imported; end-to-end UI (rationale → request callback → import states render + row persisted) against the wired controller; the Settings connect entry routes onward when AVAILABLE; the connect surface is unreachable pre-consent (no `settings.healthConnectSlot`/`hc.section.connect`) and reachable after granting via Log-home → Settings.
- **HealthSeparationTest (3):** the health package imports no notification/alarm/WorkManager API; references no `POST_NOTIFICATIONS`; a non-vacuity guard (the package exists with ≥4 sources incl. the concrete controller + the import host).

## Deviations from Plan

**1. [Rule 2 — honest empty-vs-data state] Added `EndoRepository.hasImportedData()`.**
- **Found during:** Task 1, wiring the controller's `Done(hasData, …)` transition.
- **Issue:** The controller must decide `Done.hasData` honestly. The coordinator's `ImportOutcome` reports granted permissions, not whether the store holds data; deriving hasData from a run's imported count would falsely report "empty" on a differential refresh that imports no NEW rows (the data already lives in the store).
- **Fix:** Added a small consent-gated `EndoRepository.hasImportedData()` (`allImported().isNotEmpty()`, reusing the existing DAO query — no schema/DAO change) so hasData reflects actual persisted state. Files: `EndoRepository.kt`. Commit `5a9733c`.

**2. [Rule 3 — supporting Compose host] Added `HealthImportHost.kt`.**
- The real `PermissionController` launcher must live in the Compose layer (`rememberLauncherForActivityResult`), and the launcher's result callback is fixed at `remember` time, so a thin host is needed to bridge `HealthImportScreen.onRequestPermissions` to the real contract. Not in the plan's file list; a standard Compose-layer addition that keeps all HC specifics inside the health package. Commit `75fbb42`.

**3. [Rule 3 — composition-root wiring point] Modified `MainActivity.kt` + `TrackingRoute.kt`.**
- `MainActivity` is the `AppGate` call site, so `healthConnect = dependencies.healthConnectController` is wired there (not listed in the plan's files but the necessary composition-root binding). `TrackingRoute.HealthImport` was added so the Settings connect entry can push the import screen (the plan's threading requires a reachable destination). Commit `75fbb42`.

**4. [Scope — no change needed] `SettingsScreen.kt` was NOT modified.**
- The plan lists `SettingsScreen.kt` in Task 2's files, but Plan 04 already threaded `healthConnect` + `onConnectHealth` and rendered `HealthConnectSection`. LogTabHost simply passes them through; SettingsScreen needed no edit.

Otherwise the plan executed as written. No auth gates. No package installs.

## Copy Audit

No NEW user-facing strings were introduced — all rendered copy remains the registered `HealthConnectCopy` constants from Plan 01/04 (the connect entry, rationale, import states, window label, denial). `CopyAuditTest` stays green with no registration change. `HealthImportHost` and the wiring add no user-visible text.

## Known Stubs

None. The controller, the composition-root construction, and the real permission launcher are compiled and behaviorally proven on the JVM lane. The real HC permission dialog, real provider data, and the two rationale intents resolving on-device are device-gated manual verification (per CONTEXT Deferred / RESEARCH Environment Availability, ASSUMPTION A6) — the JVM flow test covers the state rendering and the wired persistence.

## Threat Model Compliance
- **T-21-05a (Elevation of Privilege — HC controller binding, high):** mitigated — the controller is bound ONLY in `AppGate`'s Consented arm (referenced nowhere else), threaded as a nullable param defaulted null at each hop; the connect surface is unconstructible pre-consent. `connectSurface_isUnreachable_preConsent_andReachable_whenConsented` pins it (and `AppGateConsentTest` stays green).
- **T-21-05b (Denial of Service — availability-gated client construction, medium):** mitigated — `getOrCreate` is called ONLY when `getSdkStatus == AVAILABLE`; the nullable reader + honest empty state mean an UNAVAILABLE/UPDATE_REQUIRED device never crashes on a blind client construction. `controllerImport_whenUnavailable_doesNoClientWork_persistsNothing_noCrash` pins it (reader never touched).
- **T-21-05c (Elevation / separation — health package purity, high):** mitigated — `HealthSeparationTest` (structural source-scan, non-vacuous) asserts no notification/alarm/WorkManager API and no `POST_NOTIFICATIONS` under `com.endoapp.app.health`; reminders remains the sole notification owner; `NotificationSeparationTest` stays green.
- **T-21-03 / Pitfall 3 (Denial of Service — cached grant state, medium):** mitigated — `getGrantedPermissions()` is read per session on every import/`grantedHistory` (never a cached `hcAuthorized` boolean); an empty grant → `Denied`, a `SecurityException` (a revocation race) → `Denied`, never a crash. `controllerImport_withEmptyGrant_rendersDenied_persistsNothing` pins the denial path.

## Threat Flags
None. No new security surface beyond the plan's threat_model — no network, no new permission (the manifest is unchanged and read-only), no schema change, no notification/WorkManager API. The only outbound intents remain the Plan-04 Google Play deep link and the OS-owned HC permission sheet.

## Self-Check: PASSED
- All 3 created files exist on disk (`HealthImportHost.kt`, `HealthConnectFlowTest.kt`, `HealthSeparationTest.kt`); all 8 modified files carry the changes.
- Both task commits present: `5a9733c` (Task 1), `75fbb42` (Task 2).
- Real build/test results captured above (app 237 / 0 failures, insights 176 / 0, assembleDebug + lintDebug + compileDebugAndroidTestKotlin SUCCESSFUL, manifest read-only with no INTERNET / no health WRITE, HealthSeparationTest green) — not fabricated.
