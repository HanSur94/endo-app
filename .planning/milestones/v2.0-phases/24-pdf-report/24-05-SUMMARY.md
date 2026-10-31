---
phase: 24-pdf-report
plan: 05
subsystem: report builder surface (range picker + generate + share) + Consented-arm composition-root wiring
status: complete
tags: [report, pdf, builder, range-picker, share, settings, consent-by-construction, composition-root, port]
requires:
  - Plan 24-02 pure ReportModel.assemble + ReportRangeMapping (RangePreset/interval/isValid) + ReportCopy
  - Plan 24-03 ReportPdfRenderer.render(model, outDir) → cache PDF File or null
  - Plan 24-04 ReportShareHost (FileProvider + ACTION_SEND chooser + cache cleanup)
  - Plan 24-01 EndoRepository.daySnapshots / daySnapshotSources(epochDays) / latestTreatment / dismissedSignalKeys
  - Phase 23-05 ExportImportHost end-to-end wiring precedent (nullable seam, Consented-arm-only bind)
  - Phase 20-04 Bloom SettingsScreen; :insights InsightsEngine.insights (LOCKED)
provides:
  - com.endoapp.app.report.ui.ReportBuilderViewModel + ReportPhase + ReportBuilderUiState (seam-driven generate)
  - com.endoapp.app.report.ui.ReportBuilderScreen (Bloom range picker + invalid-range guard + generate → confirm/Share)
  - com.endoapp.app.report.ui.ReportBuilderHost (drives the VM + composes ReportShareHost on Share)
  - SettingsScreen "Export report" entry (settings.report) rendered only when the seam is non-null
  - TrackingRoute.Report (+ "RP" token) + LogTabHost/ConsentedShell/AppGate/AppDependencies/MainActivity wiring
affects:
  - Phase 24 is CLOSED — the clinician hand-off use case (pick range → share doctor-ready PDF) reaches the user
tech-stack:
  added: []
  patterns:
    - "Builder ViewModel driven ENTIRELY off injected closures (loadSnapshots/loadSources/loadSuppressed/loadDismissed/render/engine/now) → the whole generate flow is JVM-testable with no Room, no clock, no PDF device"
    - "MVI-ish single UiState carrying the range selection (preset + custom from/to) with DERIVED isGenerateEnabled off ReportRangeMapping.isValid → the invalid-range guard is provable by hand-setting the StateFlow"
    - "Confirmation screen (title + Share), NOT a bitmap PdfRenderer preview → the flow stays fully off-seams testable (RESEARCH Open Question 3)"
    - "Nullable report seam threaded end-to-end, bound ONLY in AppGate's Consented arm (exportImport/healthConnect/insightsCoordinator posture) — consent-by-construction (T-24-11)"
    - "assemble + render run OFF the caller's thread via withContext(dispatcher) — never block the UI thread"
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderViewModel.kt
    - android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderHost.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ui/ReportBuilderScreenTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingRoute.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/tracking/LogTabHost.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt
    - android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/settings/SettingsScreenTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt
decisions:
  - "The range selection lives in the ReportBuilderUiState (preset + custom from/to) with isRangeValid/isGenerateEnabled DERIVED off ReportRangeMapping.isValid — so the invalid-range guard + the disabled Generate stay in lock-step with the model's own validity rule, and the ScreenTest proves it by hand-setting an inverted-range state (no brittle stepper-driving)"
  - "generate() is a suspend fun called by ReportBuilderHost's rememberCoroutineScope (mirrors ExportImportViewModel's runExport posture); the VM test calls it directly in runTest — no injected app scope needed"
  - "Added ReportBuilderHost (mirroring the Phase-23 ExportImportHost) rather than inlining the scope + share-trigger state into LogTabHost's when-arm — keeps ReportBuilderScreen pure/off-seams (it never composes the auto-launching ReportShareHost) and the Share chooser launches ONLY on an explicit Share tap (T-24-01). Sanctioned by CONTEXT §'Claude's Discretion' (package layout)"
  - "ZERO new copy — every builder/settings string already exists in the registered ReportCopy (Plan 02): SETTINGS_ENTRY_LABEL/FOOTER, BUILDER_TITLE, GENERATE_CTA, PRESET_*, RANGE_*_LABEL, SHARE_ACTION, GENERATING_CAPTION, ERROR_*, INVALID_RANGE. CopyAudit stays green with no additions"
  - "render is injected as suspend (ReportModel) -> File?; AppDependencies wires { model -> ReportPdfRenderer.render(model, appContext.cacheDir) } so the renderer writes into cacheDir/reports/ (the Plan-04 share host's FileProvider root)"
metrics:
  duration: ~30m
  completed: 2026-07-10
  tasks: 2
  files: 13
  commits: 2
requirements: [AREL-01, AREL-02]
---

# Phase 24 Plan 05: Export Report Builder Surface + Consented-Arm Wiring Summary

The user-facing close of Phase 24: the pure model (02), the vector renderer (03), and the share host (04)
compose into the "Export report" flow. A consented user reaches it from the Phase-20 Settings screen,
picks a range (preset or a custom span with the inline invalid-range guard), taps Generate (which assembles
the `ReportModel` over the consent-gated repository seams + the LOCKED `:insights` engine and renders the
vector PDF off-main), and shares the doctor-ready PDF through the OS `ACTION_SEND` chooser. The whole flow
is provable off seams on the Robolectric/Compose JVM lane (no real chooser, no real PDF device), and the
report surface is bound ONLY inside `AppGate`'s Consented arm — unreachable pre-consent by construction.

## What Was Built

### Task 1 — ReportBuilderViewModel + ReportBuilderScreen + ReportBuilderHost + ReportBuilderScreenTest (`49c1c93`)
- **`ReportBuilderViewModel`** (`com.endoapp.app.report.ui`): driven ENTIRELY off injected closures
  (`loadSnapshots` / `loadSources` / `loadSuppressed` / `loadDismissed` / `render` / `engine` / `now`).
  `generate()` resolves the `ReportRange` via `ReportRangeMapping.interval(preset, from, to, asOf = now())`,
  gathers the pure inputs (all snapshots; the per-in-range-day `daySnapshotSources` map keyed epoch-day →
  `LocalDate`; `suppressed`; `dismissed`), calls `ReportModel.assemble(...)` with the engine injected as the
  closure (`{ snaps, asOf -> engine(snaps, asOf, dismissed) }`, `asOf = range.end`), renders via the injected
  `render`, and transitions to `ReportPhase.ReadyToShare(file)` on a produced file or `ReportPhase.Error` on a
  null/failed render (the honest error — never a blank PDF, never a leaked exception). The assemble + render
  run OFF the caller's thread via `withContext(dispatcher)`. `ReportBuilderUiState` carries the range selection
  (`preset` + `customFrom`/`customTo`) with `isRangeValid`/`isGenerateEnabled` DERIVED off
  `ReportRangeMapping.isValid`.
- **`ReportBuilderScreen`**: a Bloom surface off seams — the preset chips (`report.preset.last30/last90/custom`)
  + the custom From/To ±1-day steppers (`report.range.from`/`report.range.to`), the inline invalid-range
  message (`report.invalidRange`) + a DISABLED Generate (`report.generate`) when `!isRangeValid`, the
  "Generating…" caption + spinner (`report.status`/`report.spinner`) during `Generating`, a CONFIRMATION
  (title + Share, `report.share`) on `ReadyToShare`, and the honest error heading on `Error`. Every string is
  from the registered `ReportCopy`. Ships the confirmation, NOT a bitmap preview (RESEARCH Open Question 3).
- **`ReportBuilderHost`** (mirrors `ExportImportHost`): owns the `rememberCoroutineScope`, drives the VM
  (`onGenerate` → `scope.launch { viewModel.generate() }`), and composes the Plan-04 `ReportShareHost` for the
  ready PDF ONLY after an explicit Share tap — the OS chooser is the SOLE egress (T-24-01); on return it
  cleans the cache and resets the builder.
- **`ReportBuilderScreenTest`** (Robolectric + Compose, off a hand-set `MutableStateFlow` + captured seams,
  plus the real VM over fakes): the invalid custom range (to<from) disables Generate + shows the message; a
  valid range enables Generate + a tap invokes the generate seam; presets are offered + selectable; ReadyToShare
  surfaces the Share action forwarding to the share seam; Generating shows the caption — the real chooser never
  appears. The VM over injected closures: `generate()` → ReadyToShare on a produced file, Error on a null render,
  no-op on an invalid range (render never called).

### Task 2 — Settings "Export report" entry + TrackingRoute.Report + Consented-arm wiring (`979fdfc`)
- **`SettingsScreen`**: a new nullable `report: ReportBuilderViewModel?` seam + `onOpenReport` callback; an
  `ExportReportSection` (`settings.report` row via `ReportCopy.SETTINGS_ENTRY_LABEL` + the on-device
  `SETTINGS_ENTRY_FOOTER`) rendered ONLY when the seam is non-null — the exact `HealthConnect`/`ExportImport`
  null-guard, so Settings stays inert in isolation.
- **`TrackingRoute.Report`** (+ its `"RP"` save/restore token); `LogTabHost` threads the seam, renders
  `TrackingRoute.Report -> report?.let { ReportBuilderHost(...) }` (guarded on non-null), and pushes the route
  from the Settings entry.
- **`ConsentedShell`** threads the nullable seam down to `LogTabHost`; **`AppGate`** binds `report = report`
  into `ConsentedShell` ONLY inside the `is ConsentState.Consented ->` arm (referenced in NO other arm —
  consent-by-construction, T-24-11 mirror of T-23-10). **`AppDependencies`** constructs
  `ReportBuilderViewModel` over `repository::daySnapshots`, `repository::daySnapshotSources`, the latest-
  treatment `suppressed`, `repository::dismissedSignalKeys`, `InsightsEngine::insights` (default), and
  `{ model -> ReportPdfRenderer.render(model, appContext.cacheDir) }`; **`MainActivity`** passes
  `dependencies.reportBuilderViewModel` into `AppGate`. No notification/alarm/WorkManager/networking import
  leaks into any wired file.
- **`SettingsScreenTest`**: the "Export report" entry is ABSENT when the seam is null and present + navigable
  (tapping invokes `onOpenReport`) when threaded. **`AppGateConsentTest`**: with the seam THREADED,
  `settings.report` is unreachable while `NotConsented` (consent-by-construction) and reachable once
  `Consented`.

## REAL Test Results

All run against the real Android SDK / Gradle on this host.

| Suite | Command | tests / failures / errors |
|-------|---------|---------------------------|
| ReportBuilderScreenTest | `:app:testDebugUnitTest --tests "*ReportBuilderScreenTest"` (Robolectric) | 10 / 0 / 0 |
| CopyAuditTest (regression, no new copy) | `--tests "*CopyAuditTest"` | 10 / 0 / 0 |
| SettingsScreenTest (+2 report tests) | `--tests "*SettingsScreenTest"` | 9 / 0 / 0 |
| AppGateConsentTest (+2 report gating tests) | `--tests "*AppGateConsentTest"` | 9 / 0 / 0 |
| `:app` full unit suite (regression) | `:app:testDebugUnitTest` | 452 / 0 / 0 (1 pre-existing skip) |
| `:insights:test` (regression) | `:insights:test` | 176 / 0 / 0 |
| `:app:assembleDebug` | `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:lintDebug` | `:app:lintDebug` | BUILD SUCCESSFUL |

Proofs (all green):
- **Range picker offers presets + custom, invalid range guarded:** LAST30/LAST90/CUSTOM chips render; an
  inverted custom range (To before From) disables Generate and shows `ReportCopy.INVALID_RANGE`; a valid
  range enables it (T-24-12).
- **Generate → share reachable:** a valid-range Generate tap invokes the generate seam; ReadyToShare surfaces
  the Share action forwarding to the share seam; the whole flow is provable off seams with NO real chooser.
- **Consent-gated by construction:** `settings.report` is unreachable while `NotConsented` even with the seam
  threaded, and reachable once `Consented` (T-24-11) — the seam is referenced ONLY in AppGate's Consented arm.
- **No-regression:** the whole `:app` (452) + `:insights` (176) suites stay green, including
  `ReportNoNetworkTest` (which now also scans the new `report/ui` builder files — still network-free),
  `NotificationSeparationTest`, and the version-catalog audit; `assembleDebug` + `lintDebug` clean.
- **Zero new dependency:** version catalog + gradle untouched (T-24-SC "accept" honored).

## Deviations from Plan

**1. [Rule 3 — Blocking / discretion] Added `ReportBuilderHost` (not in the plan's explicit file list)**
- **Found during:** Task 1/2. The plan sketched the route as
  `report?.let { ReportBuilderScreen(...) + ReportShareHost(...) }` inline in `LogTabHost`.
- **Issue:** `ReportShareHost` auto-launches the chooser via a `LaunchedEffect` keyed on the file path; composing
  it directly inside `ReportBuilderScreen` (or inlining scope + share-trigger state into `LogTabHost`'s when-arm)
  would either (a) launch the chooser automatically on any ReadyToShare (not on an explicit Share tap) or
  (b) make `ReportBuilderScreen` non-pure/un-testable off seams.
- **Fix:** Introduced `ReportBuilderHost` (a direct mirror of the Phase-23 `ExportImportHost` the plan told me to
  mirror): it owns the `rememberCoroutineScope`, drives the VM, and composes `ReportShareHost` ONLY after an
  explicit Share tap. `ReportBuilderScreen` stays pure and off-seams testable; the Share chooser is the sole
  egress launched on user action. Sanctioned by CONTEXT §"Claude's Discretion" (package layout / preview approach).
- **Files:** `android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderHost.kt`
- **Commit:** `49c1c93`

No other deviations — zero new copy was needed (every string is in the registered `ReportCopy` from Plan 02),
so the CopyAudit gate required no additions.

## Threat Model Compliance

- **T-24-11 (EoP — pre-consent report access):** the `report` seam is bound ONLY in `AppGate`'s Consented arm
  (referenced in no other arm); `AppGateConsentTest` pins `settings.report` unreachable while `NotConsented` and
  reachable once `Consented`; `SettingsScreen` renders the entry only when the seam is non-null. Mitigated.
- **T-24-12 (input validation — custom range guard):** `ReportRangeMapping.isValid` disables Generate on an
  inverted custom range and the `report.invalidRange` message is shown; the VM's `generate()` is additionally a
  no-op on an invalid range (render never called). Pinned by `ReportBuilderScreenTest`. Mitigated.
- **T-24-01 (info disclosure — share flow):** the Share action routes through the Plan-04 `ReportShareHost`
  (user-chosen `ACTION_SEND` chooser, cache-cleaned, no network), composed ONLY after an explicit Share tap.
  Reaffirmed at the UI seam. Mitigated.
- **T-24-SC (supply chain — package installs):** no dependency added — all UI is Compose (present); version
  catalog unchanged. Accepted.

## Known Stubs

None. `ReportBuilderViewModel` is constructed over the real consent-gated repository seams + `InsightsEngine`
+ `ReportPdfRenderer`; the screen + host compose real seams; the entry is wired end-to-end and consent-gated.

## Device-Gated Verification

The real OS share chooser UX and the on-device visual/selectable-text credibility of a device-generated PDF
(opening the system share sheet, picking a target, confirming the PDF opens in the chosen app) are device-gated
— surfaced to `/gsd-verify-work`, mirroring the Plan 24-03/24-04 precedent. The range picker, invalid-range
guard, generate → share reachability, and consent gating all run fully on the Robolectric/Compose JVM lane.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderViewModel.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderScreen.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportBuilderHost.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ui/ReportBuilderScreenTest.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/ui/settings/SettingsScreen.kt (Export report entry)
- FOUND: android/app/src/main/kotlin/com/endoapp/app/ui/tracking/TrackingRoute.kt (Report + "RP")
- FOUND commit 49c1c93 (Task 1), 979fdfc (Task 2)

---
*Phase: 24-pdf-report*
*Completed: 2026-07-10*
