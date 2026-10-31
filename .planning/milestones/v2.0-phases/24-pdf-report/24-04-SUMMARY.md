---
phase: 24-pdf-report
plan: 04
subsystem: report share (FileProvider + ACTION_SEND, no silent upload) + structural no-network posture
status: complete
tags: [report, share, fileprovider, action-send, no-network, cache-cleanup, scoped-uri, manifest-guard, port]
requires:
  - Plan 24-03 ReportPdfRenderer.render(model, outDir) — writes the cache PDF to cacheDir/reports/, returns the File or null
  - Phase 23-05 ExportImportHost — the ActivityResult (stash → launch system Intent → consume/clean on return) pattern mirrored here
  - Phase 19/21 ManifestPermissionsTest — the DOM-parse, comment-safe manifest allow/deny-list precedent extended
provides:
  - com.endoapp.app.report.ui.buildShareIntent — FileProvider content:// URI + ACTION_SEND + application/pdf + FLAG_GRANT_READ_URI_PERMISSION
  - com.endoapp.app.report.ui.buildShareChooser — Intent.createChooser wrap (the USER picks the target; the ONLY egress)
  - com.endoapp.app.report.ui.cleanupReports — deletes cacheDir/reports/*.pdf after the chooser returns (no stale artifact)
  - com.endoapp.app.report.ui.ReportShareHost — the Compose share host (launch chooser, clean cache on return)
  - AndroidManifest FileProvider <provider exported=false grantUriPermissions=true> + res/xml/file_paths.xml (<cache-path name=reports>)
affects:
  - Plan 24-05 builder ViewModel / share entry point (calls ReportPdfRenderer.render then ReportShareHost)
tech-stack:
  added: []
  patterns:
    - FileProvider + ACTION_SEND chooser = zero storage permission, per-share scoped/revocable grant, OS-owned target choice (no silent upload)
    - Intent construction + cache cleanup kept behind plain top-level functions (buildShareIntent/buildShareChooser/cleanupReports) so Robolectric asserts the shape without the real chooser
    - Structural no-network source scan (file-walk + comment-strip + token scan, non-vacuous) — the grep-provable complement of the no-INTERNET manifest
    - DOM-parse comment-safe manifest guard extended to a <provider> (element attributes only; prose can never false-trip)
key-files:
  created:
    - android/app/src/main/res/xml/file_paths.xml
    - android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportShareHost.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ReportManifestTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ReportShareHostTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/report/ReportNoNetworkTest.kt
  modified:
    - android/app/src/main/AndroidManifest.xml
decisions:
  - "The FileProvider authority is ${applicationId}.fileprovider, built in code as context.packageName + \".fileprovider\" — matches the manifest and getUriForFile"
  - "file_paths.xml vends ONLY <cache-path name=reports path=reports/> (app-private cache) — NEVER an <external-path>/<files-path>; the manifest test red-fails on any such addition"
  - "The two getUriForFile-dependent Robolectric assertions were merged into ONE test method: FileProvider caches its PathStrategy statically per authority while Robolectric gives each method a fresh cacheDir, so a second per-method getUriForFile call resolved against the first method's cached root; production cacheDir is stable so the app is unaffected"
  - "cleanupReports fires from the StartActivityForResult callback (chooser dismissed = user returned) — the ExportImportHost seam reused for cache cleanup rather than SAF consume"
metrics:
  duration: ~18m
  completed: 2026-07-10
  tasks: 3
  files: 6
  commits: 3
requirements: [AREL-02]
---

# Phase 24 Plan 04: FileProvider + ACTION_SEND share (no silent upload) + no-network scan Summary

The T-24-01 egress mitigation for the doctor-ready PDF: the rendered report (from Plan 24-03's `ReportPdfRenderer.render`, written to app-private `cacheDir/reports/`) leaves the device through **exactly one path** — the OS `ACTION_SEND` chooser, where the USER picks the target. It is vended as a scoped, revocable `content://` URI through an `androidx.core.content.FileProvider` (`exported=false`, `grantUriPermissions=true`, per-Intent `FLAG_GRANT_READ_URI_PERMISSION`), backed by a `file_paths.xml` that exposes ONLY the app-private cache reports subdir — never external storage. The cache PDF is deleted after the chooser returns, so no stale health artifact lingers. There is NO auto-upload, NO silent send, NO network call, and NO new permission (still zero INTERNET). The no-network posture is made grep-provable by a non-vacuous structural scan over `com.endoapp.app.report`, and the manifest guard confirms the provider is present with NO INTERNET added. Zero new dependency (FileProvider is androidx.core, already present).

## What Was Built

### Task 1 — FileProvider `<provider>` + `res/xml/file_paths.xml` + ReportManifestTest (`07a0f99`)
- Added the `androidx.core.content.FileProvider` `<provider>` inside the manifest `<application>`: `android:authorities="${applicationId}.fileprovider"`, `android:exported="false"`, `android:grantUriPermissions="true"`, with the `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />` child. NO `<uses-permission>` added (no INTERNET, no storage — the whole point).
- `res/xml/file_paths.xml`: a single `<cache-path name="reports" path="reports/" />` — the app-private `cacheDir/reports/` subdir only; no `<external-path>`/`<files-path>`.
- `ReportManifestTest` (pure JVM, DOM-parse, comment-safe, mirrors `ManifestPermissionsTest`/`ExportManifestTest`): asserts (a) the provider element exists (non-vacuity) with `exported=false` + `grantUriPermissions=true`; (b) `file_paths.xml` declares a `<cache-path>` and NO external/broad path; (c) the manifest still declares NO INTERNET.

### Task 2 — ReportShareHost (FileProvider + chooser + cache cleanup) + ReportShareHostTest (`69756d5`)
- `report/ui/ReportShareHost.kt` mirroring the Phase-23 `ExportImportHost` ActivityResult pattern, for a SHARE:
  - `buildShareIntent(context, pdfFile)`: `FileProvider.getUriForFile(context, "${packageName}.fileprovider", pdfFile)` → `Intent(ACTION_SEND){ type="application/pdf"; EXTRA_STREAM=uri; addFlags(FLAG_GRANT_READ_URI_PERMISSION) }` — a scoped, revocable grant, never a raw `file://`.
  - `buildShareChooser(send)`: `Intent.createChooser(send, null)` — the USER picks the target.
  - `cleanupReports(reportsDir)`: deletes `*.pdf` under the reports cache dir, returns the count; missing dir = quiet no-op.
  - `@Composable ReportShareHost(pdfFile, onShared)`: `rememberLauncherForActivityResult(StartActivityForResult())` launches the chooser and, on return, cleans the cache then signals `onShared`.
- Imports NO networking/notification/alarm/WorkManager API.
- `ReportShareHostTest` (Robolectric, off the seams): pins the intent shape (ACTION_SEND + application/pdf + a `content://` `EXTRA_STREAM` on the fileprovider authority + `FLAG_GRANT_READ_URI_PERMISSION`), the chooser wrap (`ACTION_CHOOSER` → `EXTRA_INTENT`), the cache cleanup (deletes a seeded PDF), and the missing-dir no-op.

### Task 3 — ReportNoNetworkTest — structural no-network scan (`a425cb9`)
- `ReportNoNetworkTest` (pure JVM, mirrors `ExportSeparationTest`/`NotificationSeparationTest`): walks every `.kt` under `com.endoapp.app.report` (incl. `report/ui`), strips block+line comments, asserts NONE contains a forbidden networking token (`java.net`, `okhttp`, `HttpURLConnection`, `URLConnection`, `Socket`, `android.permission.INTERNET`, `URL(`).
- ANTI-VACUITY: the scanned set is non-empty (`>= 4`) and includes `ReportPdfRenderer.kt` + `ReportShareHost.kt`. Forbidden literals live in this `src/test` file only (no self-invalidation).

## REAL Test Results

All run against the real Android SDK / Gradle on this host.

| Suite | Command | tests / failures / errors |
|-------|---------|---------------------------|
| ReportManifestTest | `:app:testDebugUnitTest --tests "*ReportManifestTest"` | 3 / 0 / 0 |
| ReportShareHostTest | `--tests "*ReportShareHostTest"` (Robolectric) | 3 / 0 / 0 |
| ReportNoNetworkTest | `--tests "*ReportNoNetworkTest"` | 2 / 0 / 0 |
| ManifestPermissionsTest (regression) | `--tests "*ManifestPermissionsTest"` | 5 / 0 / 0 |
| ExportManifestTest (regression) | `--tests "*ExportManifestTest"` | 1 / 0 / 0 |
| NotificationSeparationTest (regression) | `--tests "*NotificationSeparationTest"` | 3 / 0 / 0 |
| `:app` full unit suite (regression) | `:app:testDebugUnitTest` | 438 / 0 / 0 |
| `:insights:test` (regression) | `:insights:test` | 176 / 0 / 0 |
| `:app:assembleDebug` | `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:lintDebug` | `:app:lintDebug` | BUILD SUCCESSFUL |

Proofs (all green):
- **No silent upload — the chooser is the only egress:** the share Intent is `ACTION_SEND` wrapped in `Intent.createChooser`; the USER picks the target app. No auto-send, no target chosen by the app.
- **Scoped, revocable grant:** the shared URI is a FileProvider `content://` on the `${applicationId}.fileprovider` authority with `FLAG_GRANT_READ_URI_PERMISSION`; the provider is `exported=false`, vending only app-private `cacheDir/reports/`.
- **Cache cleaned:** `cleanupReports` deletes the seeded cache PDF (asserted); the host wires it to the chooser-return callback so nothing lingers (T-24-10).
- **No network, provably:** `ReportNoNetworkTest` (non-vacuous, comment-safe) finds NO networking token in any report source; the manifest declares NO INTERNET.
- **Zero new dependency:** version catalog + gradle untouched (T-24-SC "accept" honored).

### Red-capability (inject-and-revert, documented)
- **ReportManifestTest:** injecting `<uses-permission android:name="android.permission.INTERNET" />` turned `manifestStillDeclaresNoInternet` **FAILED**; reverted clean.
- **ReportNoNetworkTest:** injecting `java.net.URL("http://example.com")` into `ReportShareHost.kt` turned `reportPackage_usesNoNetworkingApi` **FAILED**; reverted clean.

## Deviations from Plan

**1. [Rule 3 — Blocking issue] Merged the two `getUriForFile`-dependent Robolectric assertions into one test method**
- **Found during:** Task 2 (`ReportShareHostTest` first run — `chooserWrapsTheSendIntent` failed with `IllegalArgumentException: Failed to find configured root that contains …/cache/reports/…pdf`).
- **Issue:** `FileProvider` caches its parsed `PathStrategy` in a static map keyed by authority. Robolectric gives each test METHOD a fresh (different) temp `cacheDir`, so the second per-method `getUriForFile` call resolved against the FIRST method's cached root and failed. This is a Robolectric+FileProvider test-harness artifact only — production `cacheDir` is stable, so the real app share is unaffected.
- **Fix:** Consolidated the intent-shape and chooser-wrapping assertions into a single method (`shareIntentIsScopedActionSendPdf_wrappedInAUserChooser`) so `getUriForFile` runs once per class run. All four acceptance proofs are still asserted; a documenting comment records why.
- **Files modified:** `android/app/src/test/kotlin/com/endoapp/app/report/ReportShareHostTest.kt`
- **Commit:** `69756d5`

No other deviations — the plan executed as written.

## Threat Model Compliance

- **T-24-01 (info disclosure — no silent upload):** the ONLY egress is the user-chosen `ACTION_SEND` chooser; NO INTERNET permission (ReportManifestTest + ManifestPermissionsTest); the report package is provably network-free (ReportNoNetworkTest, non-vacuous); the cache PDF is deleted after the chooser returns. Mitigated.
- **T-24-09 (EoP — over-broad file URI grant):** FileProvider `exported=false`, `grantUriPermissions=true`, per-Intent `FLAG_GRANT_READ_URI_PERMISSION` (scoped, auto-revoked); `file_paths.xml` vends only app-private `cacheDir/reports/`, never external storage (pinned by ReportManifestTest). Mitigated.
- **T-24-10 (info disclosure — stale cache PDF):** writes only to app-private `cacheDir/reports/`; `cleanupReports` deletes the cache PDF(s) after the chooser returns (pinned by ReportShareHostTest). Mitigated.
- **T-24-SC (supply chain — package installs):** no dependency added — FileProvider is androidx.core (present); version catalog unchanged. Accepted.

## Known Stubs

None. The share seam is fully implemented and tested on the JVM/Robolectric lane. The composable entry point that renders (Plan 24-03) then shows `ReportShareHost` is wired by Plan 24-05 (the builder ViewModel / share button), which this plan provides the share host for.

## Device-Gated Verification

The real OS chooser + the actual no-silent-upload UX (opening the system share sheet, picking a target, confirming the PDF opens in the chosen app and the cache is cleaned) is device-gated — surfaced to `/gsd-verify-work`, mirroring the Plan 24-03 `ReportPdfSmokeTest` precedent. The intent shape, scoped grant, chooser wrapping, and cache cleanup all run fully on the Robolectric JVM lane meanwhile.

## Self-Check: PASSED

- FOUND: android/app/src/main/res/xml/file_paths.xml
- FOUND: android/app/src/main/kotlin/com/endoapp/app/report/ui/ReportShareHost.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ReportManifestTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ReportShareHostTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/report/ReportNoNetworkTest.kt
- FOUND: android/app/src/main/AndroidManifest.xml (FileProvider <provider> added)
- FOUND commit 07a0f99 (Task 1), 69756d5 (Task 2), a425cb9 (Task 3)

---
*Phase: 24-pdf-report*
*Completed: 2026-07-10*
