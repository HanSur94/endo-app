---
phase: 23-encrypted-export-import
plan: 04
subsystem: encrypted-export-import
status: complete
tags: [export, import, viewmodel, coroutines, copy-audit, separation, security]
requires:
  - ExportCodec.encode/decode + ExportError taxonomy (23-01)
  - EndoRepository.exportSnapshot/importEnvelope (23-02)
  - DocumentIo seam + verifyWrite + FakeDocumentIo (23-03)
provides:
  - ExportImportViewModel (foreground-coroutine orchestration, off-main KDF, progress StateFlow)
  - ExportImportCopy (registered AuditableCopy) + messageForError taxonomy mapper
  - ExportSeparationTest (export-package notification/alarm/WorkManager guard)
affects:
  - com.endoapp.app.export
  - com.endoapp.app.content (CopyAuditRegistry.sources)
tech-stack:
  added: []
  patterns:
    - "Plain state-holder class with injected seams (RealHealthConnectController precedent) over a DI framework"
    - "withContext(dispatcher) around the KDF-heavy encode/decode so the UI thread never blocks"
    - "Typed-error -> registered-copy mapping (messageForError); no exception ever reaches UI state"
    - "Per-package focused separation guard mirroring NotificationSeparationTest/HealthSeparationTest"
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/ExportImportCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportImportViewModel.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportImportViewModelTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportSeparationTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
decisions:
  - "ExportImportViewModel is a plain class with injected suspend seams (not androidx.lifecycle.ViewModel) so the whole export/import brain runs pure-JVM off fakes, matching the manual-composition convention"
  - "Passphrase CharArray is zeroed to '\\u0000' in a finally on every path; test asserts no character of the secret survives"
  - "messageForError lives in ExportImportCopy.kt as a pure top-level fun; ViewModel calls it — the taxonomy->copy contract is single-sourced and CopyAudit-scanned"
requirements: [APRT-01, APRT-02]
metrics:
  duration_min: 139
  completed: 2026-07-09
  tasks: 2
  files_created: 4
  files_modified: 1
---

# Phase 23 Plan 04: Export/Import ViewModel + Registered Copy + Separation Guard Summary

Foreground-coroutine orchestration (`ExportImportViewModel`) that runs the ~600k PBKDF2 KDF off the main thread, publishes an indeterminate progress state, wires read-back-verified export and consent-gated idempotent import, and maps every `ExportError` to one calm registered `ExportImportCopy` string with zero stack-trace leak — plus the export-package notification-separation guard.

## What was built

### Task 1 — `ExportImportCopy` (registered) + `messageForError` (commit `d0c30be`)
- `object ExportImportCopy : AuditableCopy` in `com.endoapp.app.content`: section/entry labels, choose/confirm passphrase labels, passphrase-mismatch line, the honest cannot-recover warning, the two in-progress spinner labels, the two success lines, the read-back-failed line, and the three gentle import-error messages — 15 named `const val` strings, all in `auditableStrings`.
- Registered in `CopyAuditRegistry.sources` in the same change (audited from birth per the load-bearing copy-audit rule).
- Pure `fun messageForError(error: ExportError): String` maps `NotAnExport`/`WrongPassphrase`/`Unsupported` 1:1 to the three registered strings (exhaustive `when` over the sealed taxonomy).
- Every string hand-verified and machine-verified against the `ClaimsPolicy`/`ForbiddenTerms` substring lists (causal/imperative/alarmist/gendered/precision), including the `her` trap (no `here`/`where` host words) and the `use` trap.

### Task 2 — `ExportImportViewModel` + tests + separation guard (commit `410428d`)
- `ExportImportViewModel` (plain class, injected seams): `state: StateFlow<ExportImportUiState>` with `Idle`/`Working`/`Success`/`Error`.
  - `runExport`: `Working` -> `exportSnapshot()` -> `encode` (off-main) -> `documentIo.write` -> `verifyWrite` (off-main) -> `Success(EXPORT_SUCCESS)` on a verified write, else `Error(READ_BACK_FAILED)` — never a premature success.
  - `runImport`: `Working` -> `documentIo.read` -> `decode` (off-main) -> on `Success` `importEnvelope()` -> `Success(IMPORT_SUCCESS)`; on `Failure(error)` -> `Error(messageForError(error))`.
  - KDF-heavy encode/decode/verify wrapped in `withContext(dispatcher)`; passphrase `CharArray` zeroed in a `finally`; no exception/class/byte ever reaches the state.
- `ExportImportViewModelTest` (Robolectric + coroutines-test, off `FakeDocumentIo` + real `ExportCodec`/`verifyWrite`): export verify-before-success (+ Working emitted + blob decodable), truncated-write -> read-back failure with no Success, the three import taxonomies -> three distinct gentle messages, import happy-path upsert, a no-leak scan (no state carries `exception`/`java.`/`kotlin.`/`com.endoapp`/stack-trace markers), an off-main proof (encode runs on the injected named dispatcher thread, not the caller thread), and passphrase-cleared on both paths.
- `ExportSeparationTest` (pure JVM): the export package imports no `androidx.work`/notification/alarm API and references no `POST_NOTIFICATIONS`, with a non-vacuity guard (>=5 sources incl. `ExportImportViewModel.kt` + `ExportCodec.kt`).

## Real test results

- `./gradlew :app:testDebugUnitTest --tests "*CopyAuditTest"` — BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest --tests "*ExportImportViewModelTest" --tests "*ExportSeparationTest"` — BUILD SUCCESSFUL (13 ViewModel tests + 3 separation tests green): export verify-before-success, error->gentle-message no-leak (all three taxonomies), off-main-KDF proof, and the separation guard all pass.
- `./gradlew :app:testDebugUnitTest --tests "*NotificationSeparationTest"` — whole-app scan still green (export package adds no notification surface).
- `./gradlew :app:testDebugUnitTest` (full module) + `:app:assembleDebug` — BUILD SUCCESSFUL, no regressions.
- `./gradlew :insights:test` — BUILD SUCCESSFUL (no regression).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] NUL byte in the passphrase-zeroing char literal**
- **Found during:** Task 2 verification (two passphrase-hygiene tests failed; `file`/git flagged `ExportImportViewModel.kt` as binary).
- **Issue:** The passphrase-zeroing `fill(...)` char literal was written with an embedded NUL byte (0x00) by the file-write pipeline, so the source contained a raw control byte — git treated the file as binary and diffs were suppressed. Behaviour was actually correct (the passphrase was zeroed), but a NUL byte in a source file is fragile and un-reviewable.
- **Fix:** Replaced both occurrences with the explicit six-character unicode escape for U+0000 (pure ASCII, unambiguous, and the standard secret-zeroing sentinel). File is now `ASCII text`; git stores it as a normal text diff.
- **Files modified:** `ExportImportViewModel.kt`
- **Commit:** `410428d` (amended into the Task 2 commit)
- **Test adjustment:** the two passphrase-hygiene assertions now check `passphrase.none { it in PASSPHRASE }` (robust to any zeroing sentinel and directly asserts no secret character survives) rather than pinning a specific fill char.

## Known Stubs

None. `ExportImportViewModel` composes real seams; the UI screen that renders `ExportImportUiState`/`ExportImportCopy` and wires the ViewModel at the composition root is a later plan's slot, not a stub introduced here.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/content/ExportImportCopy.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/export/ExportImportViewModel.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/export/ExportImportViewModelTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/export/ExportSeparationTest.kt
- FOUND commit: d0c30be (Task 1)
- FOUND commit: 410428d (Task 2)
