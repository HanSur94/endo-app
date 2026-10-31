---
phase: 18-room-persistence
plan: 05
subsystem: content/copy-policy
tags: [claims-policy, copy-audit, honesty, port, reuse]
requires:
  - ":insights ForbiddenTerms (Phase 17 — causal/imperative/alarmist, drift-guarded)"
provides:
  - ":insights ForbiddenTerms widened to public (single source, no fork)"
  - ":app ClaimsPolicy (reuse of shared three + app-only gendered/precision/allowedFraming lists)"
  - ":app AuditableCopy interface + CopyAuditRegistry (empty-but-present)"
  - "CopyAudit JVM harness (registered-catalog scan, red-capable self-test)"
  - "ClaimsPolicyReuseTest (proves no divergent fork of the shared lists)"
affects:
  - "Phase 19 UI copy — every screen's first strings register into CopyAuditRegistry and are audited from birth"
tech-stack:
  added: []
  patterns:
    - "Single-source reuse via getters delegating to :insights ForbiddenTerms (no re-declaration)"
    - "Registered-catalog copy audit with a red-capable non-vacuity self-test"
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicy.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/ClaimsPolicyReuseTest.kt
  modified:
    - android/insights/src/main/kotlin/com/endoapp/insights/ForbiddenTerms.kt
decisions:
  - "Widen ForbiddenTerms internal->public rather than fork; getters in :app return the exact :insights instances (assertSame-provable)"
  - "CopyAuditRegistry ships empty-but-present so the audit mechanism predates the first UI copy (Phase 19)"
  - "Non-vacuity is proven by feeding deliberately-forbidden strings through the SAME scan helpers used on the real catalog"
metrics:
  duration: ~15m
  completed: 2026-07-09
  tasks: 2
  files_created: 4
  files_modified: 1
status: complete
---

# Phase 18 Plan 05: ClaimsPolicy + CopyAudit Harness Summary

Widened `:insights` `ForbiddenTerms` to `public` as the single source for the shared causal/imperative/alarmist substring lists, added the `:app` `ClaimsPolicy` (reusing those three via delegating getters + adding only the app-only gendered/false-precision/allowedFraming lists), and stood up the `AuditableCopy` registry + `CopyAudit` JVM harness — so all user-facing copy is audited before any UI copy exists (Phase 19).

## What Was Built

**Task 1 — Single source + reuse.** `ForbiddenTerms` changed from `internal object` to `public object` (doc comment updated to record the reuse contract), keeping it the drift-guarded single source. `:app` `content/ClaimsPolicy.kt` exposes `causal`/`imperative`/`alarmist` as getters delegating to `com.endoapp.insights.ForbiddenTerms.*` (no re-declaration) and adds only the app-only lists: `allowedFraming` (7), `forbiddenGenderedTokens` (6), `forbiddenPrecisionTokens` (7), verbatim from the iOS `ClaimsPolicy.swift` port source.

**Task 2 — Registry + harness.** `AuditableCopy` interface (`val auditableStrings: List<String>`) + `CopyAuditRegistry` (empty-but-present; Phase 19 appends its first `FooCopy` source in the same change that introduces the screen). `CopyAuditTest` scans the registered catalog for causal/imperative/alarmist/gendered + false-precision + percent tokens, and — the load-bearing part — proves non-vacuity via `scanCatchesAForbiddenString` / `precisionScanCatchesAFalsePrecisionString`, which feed deliberately-forbidden strings through the SAME scan helpers, plus `scanPassesCuratedCleanCopy` proving the scan is not over-eager. `ClaimsPolicyReuseTest` uses `assertSame` to prove the app getters return the exact `:insights` list instances (a fork would be a different instance and fail), and asserts the app-only lists match the iOS source.

## Real Test Results

- `./gradlew :insights:test` — **176 tests, 0 failures** (no drift/boundary/version-catalog regression from the visibility widen).
- `./gradlew :app:testDebugUnitTest --tests "*CopyAuditTest" --tests "*ClaimsPolicyReuseTest"` — **CopyAuditTest 7/7 green, ClaimsPolicyReuseTest 6/6 green**.
- Full `:app:testDebugUnitTest` — **54 tests, 0 failures** (no regression across the Phase 18 data-layer suite).
- Combined `:insights:test :app:testDebugUnitTest --tests "*CopyAuditTest" --tests "*ClaimsPolicyReuseTest"` — **BUILD SUCCESSFUL**.

## Deviations from Plan

None — plan executed exactly as written. Rules 1-4 not triggered; no auth gates; no architectural changes.

## Threat Mitigations

- **T-18-11 (policy drift / divergent fork):** `:app` `ClaimsPolicy` references the `:insights` `ForbiddenTerms` single source (widened to public); `ClaimsPolicyReuseTest` `assertSame` proves no second copy.
- **T-18-12 (unregistered string escaping the audit):** `CopyAudit` enforces the registered-catalog scan; the red-capable self-test proves the scan is enforcing, not vacuous on the currently-empty catalog.

## Notes for Phase 19

Every new user-facing string must (1) live in a copy-owning `object FooCopy : AuditableCopy`, (2) be listed in `auditableStrings`, and (3) be added to `CopyAuditRegistry.sources` — all IN THE SAME CHANGE (`endo-copy-audit-substring-gate`). The harness then audits it automatically from birth.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicy.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/content/ClaimsPolicyReuseTest.kt
- FOUND: commit 1651270 (Task 1), commit 7a5d993 (Task 2)
