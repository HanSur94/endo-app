---
phase: 21-health-connect-import
plan: 04
subsystem: health-connect
status: complete
tags: [health-connect, connect-ui, availability-states, window-label, provenance-badge, denial, copy-audit, bloom, compose]
requires:
  - "21-01 — HealthAvailability (three-state), HealthRationaleScreen (rationale-before-permission), HealthConnectCopy (the registered CopyAudit-clean surface)"
  - "21-03 — HealthImportCoordinator/ImportOutcome (fullHistoryGranted for AHC-04); provenance proven at row-identity level"
  - "20-04 — SettingsScreen + the nullable-controller injectable-seam threading pattern (reminders posture)"
  - "19 — Bloom design system (bloomCard/bloomBody/bloomButtonLabel/bloomH2/BloomChip/LocalBloomColors), CopyAudit + NotificationSeparation guards"
provides:
  - "HealthConnectController — the UI-facing seam (availability / grantedHistory / importNow / refresh + an observable HealthImportState); concrete impl lands in Plan 05"
  - "HealthImportState — sealed Idle/InProgress/Done(hasData,grantedPermissions)/Denied surface the import screen renders"
  - "HealthConnectSection — the 'Connect Health data' Settings entry + honest availability routing (AVAILABLE / PROVIDER_UPDATE_REQUIRED / UNAVAILABLE)"
  - "HealthImportScreen — the rationale→request→import-state flow: progress + honest window label (AHC-04) + done/empty + gentle denial"
  - "HealthSourceBadge — the 'from Health Connect' provenance chip (testTag hc.sourceBadge)"
  - "rememberOpenPlayForHealthConnect — the real Play market/web deep-link for the HC provider"
  - "FakeHealthConnectController — the scripted JVM double the connect/import UI tests drive"
affects:
  - "Plan 05 builds the concrete HealthConnectController (getSdkStatus + getGrantedPermissions + the Plan-03 coordinator), wires the PermissionController launcher onto onRequestPermissions, and routes SettingsScreen.onConnectHealth → HealthImportScreen"
  - "Phase 22 attaches HealthSourceBadge next to imported values in the day log"
tech-stack:
  added: []
  patterns:
    - "UI-facing seam interface (HealthConnectController) + StateFlow<HealthImportState> so the whole Bloom surface is provable off a fake with no emulator (the ReminderController posture)"
    - "Per-session availability resolution on tap (never cached) → honest three-state routing; every unavailable tier reads as normal, not an error"
    - "Honest window label derived reactively from grantedHistory() via produceState keyed on import state (AHC-04)"
    - "Nullable seam omission: SettingsScreen renders the connect section ONLY when a controller is threaded (inert in isolation — existing Settings tests untouched)"
    - "All rendered strings are registered HealthConnectCopy constants (no new user-facing copy introduced) — CopyAudit stays green with no registration change"
key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthConnectController.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/HealthConnectSection.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/HealthImportScreen.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/HealthSourceBadge.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/FakeHealthConnectController.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/ui/HealthConnectSectionTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/ui/HealthImportScreenTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt"
decisions:
  - "HealthConnectController.importNow() (not import()) — `import` is a Kotlin hard keyword; mirrors the coordinator's importAll() keyword-avoidance."
  - "PROVIDER_UPDATE_REQUIRED renders the install variant (UNAVAILABLE_NOT_INSTALLED + GET_ON_PLAY): getSdkStatus cannot distinguish provider-absent from provider-stale, so the honest install/Get default is shown. UNAVAILABLE_STALE/UPDATE_ON_PLAY stay registered for a future finer signal."
  - "The connect section is OMITTED entirely when no controller is threaded (not a default-OFF render) — the plan's inert-by-default posture; keeps SettingsScreen tests unchanged and manual logging ungated."
  - "The done-with-data panel shows a standalone HealthSourceBadge (no invented demo value string) so ALL rendered text is audited HealthConnectCopy; PER_PERMISSION_STATUS was intentionally NOT rendered (a static example string would imply a false per-permission status — Plan 05 can make it dynamic)."
requirements: [AHC-01, AHC-03, AHC-04]
metrics:
  duration: "~40m"
  completed: "2026-07-09"
  tasks: 2
  files_created: 7
  files_modified: 1
  app_tests: 228
---

# Phase 21 Plan 04: Health Connect Connect/Import UI Summary

Built the thin Bloom connect/import UI off the Plan 01–03 seams — a `HealthConnectController` interface (availability / grantedHistory / importNow / refresh + an observable `HealthImportState`), the "Connect Health data" Settings entry that resolves availability per session and routes to the honest surface, the import screen (rationale → request → progress → honest window label → done/empty/denial), and the "from Health Connect" provenance chip. Every rendered string is a registered `HealthConnectCopy` constant, the whole surface is proven against a `FakeHealthConnectController` on the Robolectric+Compose JVM lane, and no health-UI file imports a notification/WorkManager API. Composition-root wiring + the live import trigger land in Plan 05.

## What Was Built

**Task 1 — controller seam + Settings entry + availability rendering + provenance badge**
- `HealthConnectController` — the UI-facing seam (interface): `availability()`, `grantedHistory()` (the AHC-04 window-label switch), `importNow()`, `refresh()`, and `importState: StateFlow<HealthImportState>`. `HealthImportState` is a sealed `Idle / InProgress / Done(hasData, grantedPermissions) / Denied`. Imports no notification/WorkManager API. The concrete impl is Plan 05.
- `HealthConnectSection` — the Bloom "Connect Health data" entry (≥48dp row, label + caption from `HealthConnectCopy`). On tap it resolves `availability()` for the session and routes: **AVAILABLE** → `onConnect` (host opens the import screen); **PROVIDER_UPDATE_REQUIRED** → the honest install line + a "Get Health Connect on Google Play" button (`hc.section.getOnPlay`) firing the Play deep link; **UNAVAILABLE** → the softened "not available … manual logging works the same" line, no action. Stable testTags `hc.section.connect` / `hc.section.updateRequired` / `hc.section.unavailable`.
- `HealthSourceBadge` — the `PROVENANCE_BADGE` ("from Health Connect") chip off `BloomChip`, one merged a11y node, testTag `hc.sourceBadge`.
- `rememberOpenPlayForHealthConnect()` — the real `market://details?id=com.google.android.apps.healthdata` deep link with an `https://play.google.com` fallback.
- `SettingsScreen` threads a nullable `healthConnect: HealthConnectController? = null` + `onConnectHealth`, rendering the section between cycle-off and Privacy ONLY when a controller is present.

**Task 2 — HealthImportScreen: rationale→import states + honest window label + denial**
- `HealthImportScreen` composes off the controller: **Idle** → the Plan-01 `HealthRationaleScreen` (Continue → `onRequestPermissions`, whose result launches `importNow()`; Not-now → `onClose`); **InProgress** → the progress copy + a spinner + the HONEST window label (`WINDOW_LABEL_HISTORY` vs `WINDOW_LABEL_30_DAY`, read reactively from `grantedHistory()`, AHC-04); **Done** → the connected copy + a provenance chip (has data) or the honest empty copy (no data), plus a "Refresh from Health Connect" action; **Denied** → the calm banner + "connect anytime from Settings" hint, NO re-prompt loop, no refresh affordance. Stable testTags `hc.import.progress` / `hc.import.windowLabel` / `hc.import.empty` / `hc.import.denied` / `hc.import.refresh`. Imports no notification/WorkManager API.

## Real Test Results (this host, Android SDK platform 36, JDK 21)

- `./gradlew :app:testDebugUnitTest --tests "*HealthConnectSectionTest" --tests "*HealthImportScreenTest" --tests "*SettingsScreenTest" --tests "*CopyAuditTest" --tests "*NotificationSeparationTest"` — **GREEN**.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :insights:test` — **BUILD SUCCESSFUL**; full `:app` unit suite **228 tests, 0 failures / 0 errors** (215 from 21-03 + 13 new); `:insights` **176 tests, 0 failures**; `assembleDebug` SUCCESSFUL. No regression.
- Separation invariant: `NotificationSeparationTest` GREEN — grep of `app/src/main/kotlin/com/endoapp/app/health/` for `androidx.work` / notification / alarm / `POST_NOTIFICATIONS` is **empty** (the new health UI adds no notification surface).

### New coverage (13 tests)
- **HealthConnectSectionTest (6):** AVAILABLE routes onward (no error surface); PROVIDER_UPDATE_REQUIRED shows the honest install line + Play button (and does NOT route); UNAVAILABLE reads as normal with no action / no Play button; the provenance badge renders its copy; the section is omitted with no controller (inert) and rendered with one — manual-logging sections present alongside it in both.
- **HealthImportScreenTest (7):** history-granted → full-history label; history-absent → last-30-days label; done-with-data → connected copy + provenance badge; empty → honest empty copy; denial → gentle banner + hint, 0 re-requests, no refresh; refresh → 1 re-import on the controller; rationale Continue → 1 permission request then 1 import.

## Deviations from Plan

**1. [Rule 3 — Kotlin keyword] `importNow()` instead of `import()`.**
- The plan's seam sketch names the method `import()`. `import` is a Kotlin hard keyword (illegal as an identifier without backticks), so the method is `importNow()` — mirroring the Plan-03 coordinator's `importAll()` keyword-avoidance. No behavioral change.

**2. [Design decision — honest default] PROVIDER_UPDATE_REQUIRED renders the install variant.**
- `HealthAvailability` has a single `PROVIDER_UPDATE_REQUIRED` state, and `getSdkStatus()` returns the same constant for provider-absent and provider-stale — it cannot distinguish them. The section renders the honest install variant (`UNAVAILABLE_NOT_INSTALLED` + `GET_ON_PLAY`) as the default. `UNAVAILABLE_STALE` / `UPDATE_ON_PLAY` remain registered in `HealthConnectCopy` for a future finer signal (e.g. a version probe in Plan 05).

**3. [Scope — supporting test double] Added `FakeHealthConnectController.kt`.**
- A shared scripted `HealthConnectController` double (the connect/import analog of Plan 03's `FakeHealthReader`), so both UI tests drive the same seam with no emulator. Not in the plan's file list; a standard test-support addition.

Otherwise the plan executed as written. No auth gates. No package installs.

## Copy Audit

No NEW user-facing strings were introduced — every rendered string is an existing registered `HealthConnectCopy` constant (transcribed and hand-verified against the ClaimsPolicy substring gate in Plan 01). `CopyAuditTest` stays green with no registration change. The done panel deliberately shows a standalone provenance chip rather than an invented demo value, and `PER_PERMISSION_STATUS` is intentionally not rendered (a static example line would imply a false per-permission status), so no unaudited copy can reach the screen.

## Known Stubs

None as data stubs. The screen defaults (`onRequestPermissions = {}`, `onClose = {}`, `onConnect = {}`, `SettingsScreen.onConnectHealth = {}`) are no-ops BY DESIGN — Plan 05 wires the real `PermissionController` launcher, the composition-root `HealthConnectController`, and the SettingsScreen→ImportScreen navigation. `HealthConnectController` is an interface (the concrete impl is Plan 05); the UI is fully proven against the fake.

## Threat Model Compliance
- **T-21-04a (Information disclosure / misrepresentation — window label, medium):** mitigated — the window label reads `grantedHistory()` and renders the honest `WINDOW_LABEL_HISTORY` vs `WINDOW_LABEL_30_DAY` copy (AHC-04); every string is a registered `HealthConnectCopy` constant scanned by `CopyAuditTest`, so no unaudited claim can render. `windowLabel_historyGranted_*` / `windowLabel_historyAbsent_*` pin both branches.
- **T-21-04b (Denial of Service / UX — denial + unavailable states, low):** mitigated — denial and every unavailable tier read as normal (manual logging is the hero path, and the section is a plain Settings row that never gates logging); the denial state fires no re-request and offers no refresh loop; AVAILABLE is the only path to the system sheet, gated behind the rationale screen. `denied_showsGentleBanner_andHint_firesNoRequest` + `unavailable_readsAsNormal_noActionAffordance` pin it.
- **T-21-01/02 (upstream, read-only + import integrity):** unchanged — this UI adds no health-write or persistence path; it renders off the Plan-03 seams only.

## Threat Flags
None. No new security surface beyond the plan's threat_model — no network, no new permission, no persistence path, no notification/WorkManager API. The only new outbound intent is the Google Play deep link (a public store URL for the HC provider package).

## Self-Check: PASSED
- All 7 created files exist on disk; `SettingsScreen.kt` carries the nullable-seam + section wiring.
- Both task commits present: `639f46b` (Task 1), `80d1575` (Task 2).
- Real build/test results captured above (app 228 / 0 failures, insights 176 / 0, `assembleDebug` SUCCESSFUL, NotificationSeparation grep clean) — not fabricated.
