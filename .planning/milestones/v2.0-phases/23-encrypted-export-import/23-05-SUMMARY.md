---
phase: 23-encrypted-export-import
plan: 05
subsystem: encrypted-export-import
status: complete
tags: [export, import, saf, compose, settings, consent-by-construction, security]
requires:
  - ExportImportViewModel + ExportImportUiState + ExportImportCopy (23-04)
  - ExportCodec/DecodeResult/ExportError taxonomy (23-01)
  - EndoRepository.exportSnapshot/importEnvelope (23-02)
  - DocumentIo + SafDocumentIo + verifyWrite (23-03)
  - Phase-20 SettingsScreen injectable-seam threading; Phase-21 HealthImportHost route pattern
provides:
  - ExportImportScreen (seam-driven passphrase/confirm/warning/spinner/error Bloom surface)
  - ExportImportHost (SAF CreateDocument/OpenDocument launchers → Uri + passphrase → ViewModel)
  - Settings "Your data" section + TrackingRoute.ExportImport + Consented-arm composition-root wiring
  - ExportImportCopy.ENTER_PASSPHRASE (registered import-field label)
affects:
  - com.endoapp.app.export.ui
  - com.endoapp.app.ui.settings / ui.tracking / ui.shell
  - com.endoapp.app (AppGate, AppDependencies, MainActivity)
  - com.endoapp.app.content (ExportImportCopy)
tech-stack:
  added: []
  patterns:
    - "Screen driven ENTIRELY off seams (StateFlow + pick callbacks) so it is Robolectric-testable with no real picker"
    - "Host owns the ActivityResult launcher; passphrase stashed at launch, consumed on the picked Uri (HealthImportHost precedent)"
    - "Nullable seam threaded end-to-end, bound only in AppGate's Consented arm (healthConnect/insightsCoordinator posture) — consent-by-construction"
    - "String passphrase → CharArray at the ViewModel boundary; fields cleared on submit/cancel"
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/export/ui/ExportImportScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ui/ExportImportHost.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ui/ExportImportScreenTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/ExportImportCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingRoute.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt
    - android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt
    - android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/settings/SettingsScreenTest.kt
decisions:
  - "Both flows enter the passphrase IN-SCREEN, then launch the SAF picker (symmetric export/import); the Uri never round-trips through the screen, keeping it fully seam-testable with no picker (import ordering is Claude's discretion per CONTEXT §'Claude's Discretion')"
  - "The screen takes host seams onPickExportDestination(CharArray)/onPickImportSource(CharArray) that merge pick+submit; the host stashes the passphrase, launches the picker, and runs the ViewModel on the picked Uri"
  - "Added ExportImportCopy.ENTER_PASSPHRASE (registered, substring-safe) for the import field rather than misusing the export CHOOSE_PASSPHRASE label"
  - "Both 'Your data' entries (export + import) open the same combined screen (the screen hosts both sub-flows)"
requirements: [APRT-01, APRT-02]
metrics:
  duration_min: 18
  completed: 2026-07-09
  tasks: 2
  files_created: 3
  files_modified: 10
---

# Phase 23 Plan 05: Settings Export/Import Surface + SAF Host + Consented-Arm Wiring Summary

The one deliberate, consent-gated portability surface: a "Your data" Settings section whose export flow
takes a passphrase + confirm behind the honest cannot-recover warning and (on a match) launches the SAF
`ACTION_CREATE_DOCUMENT` picker, and whose import flow takes a passphrase and launches `ACTION_OPEN_DOCUMENT`
— both forwarding the picked `Uri` + passphrase to the 23-04 `ExportImportViewModel` (spinner during the
~600k-KDF stall, the three gentle errors + success rendered verbatim). The seam is threaded end-to-end and
bound ONLY inside AppGate's Consented arm, so export/import are unconstructible pre-consent by construction.

## What was built

### Task 1 — `ExportImportScreen` + `ExportImportHost` + screen test (commit `825af8b`)
- `ExportImportScreen` (`com.endoapp.app.export.ui`): a Bloom surface driven ENTIRELY off seams — it
  collects the `ExportImportViewModel` `StateFlow<ExportImportUiState>` and exposes
  `onPickExportDestination(CharArray)` / `onPickImportSource(CharArray)` pick seams. Renders the "Your data"
  header, the export sub-flow (choose + confirm passphrase fields with a match check that disables the
  Export action, the ALWAYS-visible `CANNOT_RECOVER` warning, a mismatch line), the import sub-flow
  (passphrase field + Import action), and the single status surface (spinner + working message for
  `Working`, the calm success/error banner for `Success`/`Error`). Stable testTags `export.action`,
  `export.passphrase`, `export.passphraseConfirm`, `export.warning`, `import.action`, `import.passphrase`,
  `exportImport.spinner`, `exportImport.status`. Passphrase held as `String` in Compose state only,
  converted to `CharArray` at the pick-seam boundary, and the fields are cleared on submit (T-23-10b).
- `ExportImportHost` (`com.endoapp.app.export.ui`): mirrors `HealthImportHost` —
  `rememberLauncherForActivityResult(CreateDocument("application/octet-stream"))` for export and
  `OpenDocument()` (input `arrayOf("*/*")`) for import. The per-request passphrase is stashed just before
  launch and consumed on the picked `Uri` to run `viewModel.runExport/runImport` as a foreground coroutine;
  a cancelled picker (null `Uri`) zeroes the stashed passphrase. Suggests a neutral, date-stamped default
  filename `endo-export-<yyyy-MM-dd>.enc` (no health terms, T-23-08c). ZERO storage permission (SAF). Imports
  no notification/alarm/WorkManager API.
- `ExportImportCopy.ENTER_PASSPHRASE = "Enter your passphrase"` added + registered in `auditableStrings`
  (substring-verified against the causal/imperative/alarmist/gendered/precision lists).
- `ExportImportScreenTest` (Robolectric + Compose, off a hand-set `MutableStateFlow` + captured seams): the
  cannot-recover warning is always visible; the Export action is disabled on a mismatch and enabled on a
  match; a match forwards the entered passphrase to the export seam; the import action forwards its
  passphrase; a `Working` state shows the spinner + in-progress message; `WrongPassphrase` renders "That
  passphrase didn't match.", `NotAnExport` renders "This doesn't look like an Endo export.", and `Success`
  renders the success line — the picker never appears.

### Task 2 — Settings "Your data" section + Consented-arm composition-root wiring (commit `a075d75`)
- `SettingsScreen`: a new nullable `exportImport: ExportImportViewModel?` seam + `onOpenExportImport`
  callback. A "Your data" section (`settings.yourData` header + `settings.export` + `settings.import` rows,
  both opening the combined screen) renders ONLY when the seam is non-null — the exact `HealthConnectSection`
  null-guard, so Settings stays inert in isolation.
- `TrackingRoute.ExportImport` added (with its `"EX"` save/restore token); `LogTabHost` threads the seam,
  renders `TrackingRoute.ExportImport -> exportImport?.let { ExportImportHost(...) }` (guarded on non-null),
  and pushes the route from the Settings entry.
- `ConsentedShell` threads the nullable seam down to `LogTabHost`; `AppGate` binds
  `exportImport = exportImport` into `ConsentedShell` ONLY inside the `is ConsentState.Consented ->` arm
  (referenced in no other arm — T-23-10). `AppDependencies` constructs `ExportImportViewModel` over
  `repository::exportSnapshot`, `repository::importEnvelope`, and `SafDocumentIo(appContext.contentResolver)`;
  `MainActivity` passes `dependencies.exportImportViewModel` into `AppGate`. No notification/alarm/WorkManager
  import leaks into any of these files.
- `AppGateConsentTest`: with the seam THREADED, the `settings.export`/`settings.import` entries are
  unreachable while `NotConsented` (consent-by-construction) and render once `Consented` (gear → Settings).
  `SettingsScreenTest`: the "Your data" section is ABSENT when the seam is null and present + navigable when
  threaded (tapping an entry invokes `onOpenExportImport`).

## Real test results

- `./gradlew :app:testDebugUnitTest --tests "*ExportImportScreenTest" --tests "*CopyAuditTest"` — BUILD
  SUCCESSFUL (15 screen tests + the CopyAudit catalog scan green; the new `ENTER_PASSPHRASE` string passes
  the substring gate).
- `./gradlew :app:testDebugUnitTest --tests "*AppGateConsentTest" --tests "*SettingsScreenTest"` — BUILD
  SUCCESSFUL (consent-by-construction preserved incl. the two new export/import gating tests; the two new
  "Your data" present/absent tests green).
- `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (real APK + androidTest
  lane compile).
- `./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — BUILD SUCCESSFUL in
  6m 25s (full JVM suite green incl. the golden fixture, ExportSeparationTest, the whole-app
  NotificationSeparationTest, and the version-catalog audit — no new dependency; lintDebug clean).

## Device-gated (surfaced to /gsd-verify-work)

- The real SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` chooser UX (the OS picker cannot be driven
  on the JVM/Robolectric lane — the screen is proven off seams; the host's launcher binding is compile-checked
  and mirrors the shipping `HealthImportHost` pattern).
- The ~600k-PBKDF2 spinner wall-clock on a low-end API-26 device.

## Deviations from Plan

### Design choices (within CONTEXT §"Claude's Discretion": SAF seam design + passphrase UX)

**1. Symmetric passphrase-first flow for both export and import**
- The plan sketched import as pick-file-THEN-passphrase; both flows instead enter the passphrase in-screen,
  then launch the picker. This keeps the `Uri` out of the screen entirely, so the whole surface is provable
  off seams with no picker round-trip — and satisfies every must-have (cannot-recover warning always visible,
  mismatch disables Export, spinner in Working, three gentle errors + success verbatim, host launches
  CreateDocument/OpenDocument forwarding the Uri + passphrase, zero storage permission). No behavior lost.

**2. [Rule 2] Added `ExportImportCopy.ENTER_PASSPHRASE` (registered)**
- **Found during:** Task 1 (the import field needed a label; the export `CHOOSE_PASSPHRASE` label — "Choose a
  passphrase" — is misleading for import, where the user enters an EXISTING passphrase).
- **Fix:** Added `ENTER_PASSPHRASE = "Enter your passphrase"` to `ExportImportCopy` and its `auditableStrings`
  list, hand- and machine-verified against the ClaimsPolicy substring lists (no causal/imperative/alarmist/
  gendered/precision hit). Registered from birth per the load-bearing copy-audit rule.
- **Commit:** `825af8b`

## Known Stubs

None. `ExportImportViewModel` is constructed over the real consent-gated repository + `SafDocumentIo`; the
screen and host compose real seams. The SAF picker chooser itself is OS-owned and device-gated (noted above),
not a stub.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/export/ui/ExportImportScreen.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/export/ui/ExportImportHost.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/export/ui/ExportImportScreenTest.kt
- FOUND commit: 825af8b (Task 1)
- FOUND commit: a075d75 (Task 2)
