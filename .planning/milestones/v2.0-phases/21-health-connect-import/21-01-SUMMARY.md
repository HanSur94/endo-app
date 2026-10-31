---
phase: 21-health-connect-import
plan: 01
subsystem: health-connect
status: complete
tags: [health-connect, permissions, manifest, availability, copy-audit, read-only]
requires:
  - "Phase 18 ImportedSample/ImportedSignals/HealthDomain (Room) — consumes rows this phase's client will produce"
  - "Phase 19 AuditableCopy/CopyAuditRegistry + ClaimsPolicy/ForbiddenTerms substring gate"
  - "Phase 19 Bloom design system (bloomCard/bloomH2/bloomBody/BloomPrimaryButton/LocalBloomColors)"
  - "Phase 20 ManifestPermissionsTest (extended here)"
provides:
  - "androidx.health.connect:connect-client 1.1.0 in the catalog + :app"
  - "HealthAvailability — three-state getSdkStatus gate (AVAILABLE/PROVIDER_UPDATE_REQUIRED/UNAVAILABLE)"
  - "HealthConnectPermissions — the 4 READ strings + HISTORY (REQUESTED set)"
  - "PermissionsRationaleActivity + HealthRationaleScreen — the manifest rationale target"
  - "HealthConnectCopy — the whole phase's registered, CopyAudit-clean copy surface"
  - "Manifest: 5 health READ perms + dual rationale declarations"
affects:
  - "Plan 04 wires the permission launcher onto HealthRationaleScreen.onContinue + renders import states from HealthConnectCopy"
tech-stack:
  added:
    - "androidx.health.connect:connect-client:1.1.0 (first-party AndroidX Maven)"
  patterns:
    - "Pure Int->enum availability mapper (JVM-testable, fail-closed on unknown)"
    - "DOM-parse comment-safe manifest assertions (activity/activity-alias intent-filters)"
    - "AuditableCopy registration in the same change (load-bearing copy rule)"
key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthConnectPermissions.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthAvailability.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/PermissionsRationaleActivity.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/ui/HealthRationaleScreen.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/content/HealthConnectCopy.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/HealthAvailabilityTest.kt"
  modified:
    - "android/gradle/libs.versions.toml"
    - "android/app/build.gradle.kts"
    - "android/app/src/main/AndroidManifest.xml"
    - "android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt"
decisions:
  - "Requested READ_HEALTH_DATA_HISTORY (AHC-04, ASSUMPTION A6) rather than accepting the silent 30-day truncation — the app's value is longitudinal; UI labels the window honestly (Plan 04)."
  - "Availability mapper is fail-closed: any status outside SDK_AVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED (incl. unknown ints) -> UNAVAILABLE, so an unrecognised provider state can never open a ready surface."
  - "Did NOT add an explicit <queries> for com.google.android.apps.healthdata — the connect-client 1.1.0 AAR merges the provider queries; assembleDebug succeeded and getSdkStatus is device-gated, so no build-time need arose."
requirements: [AHC-01, AHC-02, AHC-04]
metrics:
  duration: "~35m"
  completed: "2026-07-09"
  tasks: 3
  files_created: 6
  files_modified: 5
  app_tests: 187
---

# Phase 21 Plan 01: Read-Only Health Connect Foundation Summary

Landed the auditable read-only Health Connect foundation for Android: added `androidx.health.connect:connect-client` 1.1.0 to the catalog, declared exactly the four health READ permissions plus `READ_HEALTH_DATA_HISTORY` with BOTH Play-mandated rationale surfaces, built the fail-closed three-state `getSdkStatus` availability gate, shipped the Bloom rationale-before-permission screen that is the manifest rationale target, and registered the entire `HealthConnectCopy` surface into the CopyAudit gate — all verified against a real Android SDK build.

## What Was Built

**Task 1 — dependency + permission set + availability gate (TDD)**
- `connect-client` 1.1.0 added to `libs.versions.toml` + `:app`. The coordinate carries no analytics/auth/networking substring, so `VersionCatalogAuditTests` stays green (verified).
- `HealthConnectPermissions` — `READ` (the four exact strings), `HISTORY`, `REQUESTED = READ + HISTORY`. Zero WRITE strings. Documented that `READ_MENSTRUATION` gates both `MenstruationFlowRecord` and `MenstruationPeriodRecord`.
- `HealthAvailability` — enum + pure `availability(Int)` mapper over the verified current constants (`SDK_AVAILABLE` / `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` / else→UNAVAILABLE) + a thin `availability(Context)` wrapper. Never cached; never blocks manual logging (documented invariant).
- `HealthAvailabilityTest` pins all three states + the defensive unknown→UNAVAILABLE fallback.

**Task 2 — rationale screen + activity + copy surface**
- `HealthConnectCopy : AuditableCopy` — the whole phase's copy (entry point, Screen-A rationale, Screen-C import states, provenance badge, denial, three availability-unavailable states), transcribed verbatim from RESEARCH and registered into `CopyAuditRegistry.sources` in the same change.
- `HealthRationaleScreen` — Bloom Screen-A (headline, read-only/private/optional lines, what-it-reads, Continue primary + Not-now dismiss), stable testTags (`hc.rationale.continue`/`hc.rationale.dismiss`), `onContinue`/`onDismiss` defaulted for Plan 04.
- `PermissionsRationaleActivity` — `ComponentActivity` hosting the same screen via `BloomTheme`; the class both rationale manifest declarations target. Imports no notification/WorkManager/alarm API.

**Task 3 — manifest declarations + extended ManifestPermissionsTest (TDD)**
- Manifest: 5 `<uses-permission>` READ declarations + the dual rationale (`<activity>` for `ACTION_SHOW_PERMISSIONS_RATIONALE`; `<activity-alias>` for `VIEW_PERMISSION_USAGE`+`HEALTH_PERMISSIONS`, guarded by `START_VIEW_PERMISSION_USAGE`). No WRITE, no background read, no INTERNET.
- `ManifestPermissionsTest` extended: allow-list now the six-element source set; new deny for `READ_HEALTH_DATA_IN_BACKGROUND`; two new DOM-parse (comment-safe) assertions for BOTH rationale surfaces; merged allow-superset extended with the health READs.

## Real Test Results (Android SDK platform 36, JDK 21)

- `:app:testDebugUnitTest` — **GREEN, 187 tests** (182 baseline + 4 new `HealthAvailabilityTest` + 1 new rationale assertion). No regression.
- `:insights:test` — GREEN, including `VersionCatalogAuditTests` (connect-client is first-party, no forbidden coordinate).
- `:app:assembleDebug` — GREEN (merged manifest builds with the READ perms + dual rationale + the rationale Activity class).
- `:app:lintDebug` — GREEN.
- **Merged manifest independently verified clean:** exactly `POST_NOTIFICATIONS` + the 4 health READs + history + the known-benign WorkManager transitives (`ACCESS_NETWORK_STATE`/`FOREGROUND_SERVICE`/`RECEIVE_BOOT_COMPLETED`/`WAKE_LOCK`). connect-client injected NO WRITE, NO background read, NO INTERNET.

### Red-capability proven (not fabricated)
- **HealthAvailabilityTest** — RED first: `HealthConnectClient.SDK_AVAILABLE`/`SDK_UNAVAILABLE`/`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` all resolved (dependency fetched, constant names verified) while `HealthAvailability` was the only unresolved reference; GREEN after implementing the mapper.
- **ManifestPermissionsTest** — RED first: with the pre-Phase-21 manifest, exactly the two NEW source assertions failed (`sourceManifestDeclaresExactlyTheAllowList`, `sourceManifestDeclaresBothRationaleSurfaces`) while the deny assertions still passed; GREEN after adding the manifest declarations. This is the inject-and-revert equivalent — the test was authored red against the real prior manifest, not retrofitted green.

## Copy Audit

Every one of the 24 `HealthConnectCopy` strings was re-verified BY HAND against the exact `ClaimsPolicy`/`ForbiddenTerms` forbidden substrings (causal / imperative should·use·start·stop·take·try·cut / alarmist / gendered her-family / precision) before writing, then enforced automatically by `CopyAuditTest` (GREEN). Notable near-misses curated around rather than tripped:
- `"Continue"` is safe — ends `…nue`, NOT the imperative `use`.
- `"history"` is safe — does NOT contain the gendered `her`.
- The empty-state says `"on this screen"` and `"when an app writes…"` (no `here`/`use`); `"update"`/`"keep logging by hand"` carry no forbidden substring.

No RESEARCH copy required rewording — every string was already substring-clean as transcribed.

## Assumptions Surfaced (not silently baked)
- **A6** — requesting `READ_HEALTH_DATA_HISTORY` is the chosen AHC-04 approach (documented in `HealthConnectPermissions.HISTORY` KDoc + the manifest comment); the honest window label lands in Plan 04. Current 30-day/history semantics to be re-confirmed against developer.android.com at Plan 04 read time.
- Constant name: used the VERIFIED current `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` (the stale `SDK_STATUS_UPDATE_REQUIRED` from PITFALLS.md was avoided) — confirmed by the RED run resolving the constant.
- `<queries>` for the HC provider package was NOT added explicitly; the AAR merges it and assembleDebug succeeded. If a device shows `getSdkStatus` UNAVAILABLE unexpectedly on API 30+, add the `<queries>` element then (device-gated, deferred).

## Deviations from Plan

**Task 1 ordering (TDD infra coupling):** the RED commit for `HealthAvailabilityTest` necessarily included the catalog + `build.gradle` dependency wiring, because the test references `HealthConnectClient` status constants that only resolve once the dependency is present. The test still failed RED for the right reason (only `HealthAvailability` unresolved). Not a scope change — noted for transparency.

Otherwise: plan executed exactly as written. No Rule 1–4 deviations; no auth gates.

## Known Stubs
None. `HealthRationaleScreen`'s `onContinue`/`onDismiss` default to no-ops by design (Plan 04 wires the permission launcher) — documented in the plan, not a data stub.

## Threat Flags
None. No security surface beyond the plan's threat_model (T-21-01 read-only manifest, T-21-01b dual rationale, T-21-SC first-party supply chain) was introduced; all three are mitigated and test-pinned.

## Self-Check: PASSED
- All 6 created files exist on disk; all 5 modified files carry the changes.
- All five commits present in `git log` (4126dec, 4f6da70, bf09a13, 9db6226, 8afebc2).
- Real build/test results captured above (not fabricated).
