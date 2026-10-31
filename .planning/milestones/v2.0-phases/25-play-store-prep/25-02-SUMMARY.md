---
phase: 25-play-store-prep
plan: 02
subsystem: content
tags: [android, play-store, data-safety, target-sdk, privacy-policy, cross-check, arel-05, arel-03, kotlin, junit4, tdd]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 05
    provides: "ManifestPermissionsTest — the comment-safe DOM `<uses-permission>` `android:name` parse (no-INTERNET / no-health-WRITE anchor) REUSED by the Data-safety cross-check"
  - phase: 19-app-shell-consent
    plan: 04
    provides: "PrivacyPolicyText (offline single-source body + contactEmail) — the natural home for the new PRIVACY_POLICY_URL constant"
  - phase: 17-jvm-insights-core
    plan: XX
    provides: "VersionCatalogAuditTests — the comment-stripped forbidden-coordinate substring scan of libs.versions.toml, REUSED for the no-analytics corroboration"
  - phase: 21-health-connect-import
    plan: XX
    provides: "read-only health manifest posture (no health WRITE) — the third Data-safety corroborating fact"
  - phase: 25-play-store-prep
    plan: 01
    provides: "StoreListingCopy — the other public claim surface; the URL single-source guard scans it for divergent https literals"
provides:
  - "DataSafetyDeclaration — committed machine-checkable all-'No' Play Data-safety answers (collectsData=false, sharesData=false, empty type lists), the single source for DATA-SAFETY.md (Plan 03)"
  - "DataSafetyManifestCrossCheckTest — binds the all-'No' declaration to the no-INTERNET manifest + no-analytics catalog + no-health-WRITE posture via a conditional binding (red-capable both directions); the form can never silently contradict the code"
  - "TargetSdkComplianceTest — static build-script scan asserting targetSdk/compileSdk==36, minSdk==26, and no `isDebuggable = true` (non-debuggable release)"
  - "PRIVACY_POLICY_URL — single-source placeholder constant in PrivacyPolicyText, plus PrivacyPolicyUrlConsistencyTest asserting well-formed https / non-PDF / single-literal-in-content-package"
affects: [25-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Conditional cross-check binding: the committed declaration DRIVES which codebase facts must hold (if all-'No' → assert no-INTERNET/no-analytics/no-health-WRITE; else fail) — red-capable in BOTH directions (inject a network path OR flip the flag), so a Play form-vs-reality diff can never drift"
    - "Honest static build-script scan over Robolectric: `Build.VERSION` reports the EMULATED SDK, not the ship targetSdk, so the release-config guard reads app/build.gradle.kts text (targetSdk/compileSdk/minSdk pins + no `isDebuggable = true`)"
    - "Single-source URL guard: exactly one quoted `https://` string literal may appear across the content MAIN package (the PRIVACY_POLICY_URL) — a second hard-coded URL turns it red; comment prose (unquoted URLs) is ignored"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/DataSafetyDeclaration.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/DataSafetyManifestCrossCheckTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/TargetSdkComplianceTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/PrivacyPolicyUrlConsistencyTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/PrivacyPolicyText.kt

key-decisions:
  - "Cross-check uses a conditional binding (if collectsData==false && sharesData==false → assert corroborating facts; else fail) so BOTH failure modes are red-capable: injecting an INTERNET permission fails the all-'No' branch, flipping collectsData=true falls into the else-branch and fails (a local-only app with no network path contradicts a 'Yes')"
  - "targetSdk stays at 36 (satisfies ANY Play floor ≤ 36; ASSUMED A1 — never lowered); the non-debuggable-release assertion is a negative check (`isDebuggable = true` must be absent) because the script declares no explicit release buildType, so it inherits AGP's non-debuggable default"
  - "PRIVACY_POLICY_URL ships as a clearly-marked placeholder with a `replace-me` host — well-formed https (passes the format guard) but unmistakably NOT a live URL; the user hosts a page mirroring PrivacyPolicyText.body and replaces it before submission (ASSUMED A4, surfaced in Plan 03)"
  - "The URL single-source guard matches only DOUBLE-QUOTED https literals, so bare URLs in `//` comment prose (e.g. support.google.com references) never false-trip it"

requirements-completed: [AREL-03, AREL-05]

# Metrics
duration: ~30min
completed: 2026-07-10
status: complete
---

# Phase 25 Plan 02: Data-Safety Cross-Check, targetSdk Compliance, and Single-Source Privacy URL Summary

**Made three of the most rejection-prone Play declarations mechanically TRUE and drift-proof against the codebase: a committed all-"No" `DataSafetyDeclaration` bound (via a red-capable conditional cross-check) to the no-INTERNET manifest + no-analytics catalog + no-health-WRITE posture so the Data-safety form can never silently contradict the code; a `TargetSdkComplianceTest` asserting targetSdk/compileSdk==36, minSdk==26, and a non-debuggable release; and a single-source `PRIVACY_POLICY_URL` placeholder guarded for well-formed-https / non-PDF / single-literal-in-content. Proven REAL on the JVM lane: the 4 new tests pass, `:app` 460/460 (+4), `:insights` 176/176, real 16.0 MB debug APK, `:app:lintDebug` clean — and every guard's red-capability demonstrated by inject-and-revert.**

## Performance
- **Duration:** ~30 min (includes 3 inject-and-revert red-capability proofs + the full wave gate)
- **Started:** 2026-07-10T01:40:14Z
- **Tasks:** 3 (Task 1 via TDD RED→GREEN)
- **Files:** 5 (4 created, 1 modified)

## Accomplishments
- **Data-safety ↔ codebase binding (AREL-03, T-25-01):** `DataSafetyDeclaration` encodes the exact Play answers — `collectsData=false`, `sharesData=false`, `dataTypesCollected/Shared = emptyList()` — with the WHY documented (on-device-only access/processing is NOT "collection" per Play, support.google.com/10787469; the only egress is a user-initiated encrypted export). `DataSafetyManifestCrossCheckTest` is a CONDITIONAL binding: given the all-"No" claim it asserts (a) no `android.permission.INTERNET` in the source manifest, (b) no analytics/auth/backend coordinate in `libs.versions.toml`, (c) no `android.permission.health.WRITE_*`; and both type lists empty. It REUSES the Phase-19 comment-safe `DocumentBuilderFactory` `<uses-permission>` `android:name` parse and the Phase-17 comment-stripped catalog scan — no raw-text grep.
- **targetSdk/release compliance (AREL-05, T-25-03):** `TargetSdkComplianceTest` statically scans `app/build.gradle.kts` (the honest source; Robolectric would report the emulated SDK) and asserts `targetSdk = 36`, `compileSdk = 36`, `minSdk = 26`, and NO `isDebuggable = true` anywhere. The `resolveAppBuildScript()` walk requires the application-plugin marker so it can't accidentally match `:insights` or the root script.
- **Single-source privacy URL (AREL-05, T-25-04):** added `const val PRIVACY_POLICY_URL` to `PrivacyPolicyText` (the natural single-source home beside `contactEmail`/`body`) as a clearly-marked `replace-me` placeholder. `PrivacyPolicyUrlConsistencyTest` asserts it is non-blank, whitespace-free, `https://`, non-PDF, AND the ONLY quoted https literal across the content main package (so a divergent URL in `StoreListingCopy.kt` or elsewhere turns it red).
- **Red-capability PROVEN by inject-and-revert (not just claimed):**
  - Injected `android.permission.INTERNET` into the manifest → cross-check RED with "the form would contradict the code (T-25-01)"; manifest reverted clean.
  - Flipped `collectsData = true` → cross-check RED via the else-branch "contradicts the codebase … the corroborated truth for Endo-App is all-No"; declaration restored.
  - Injected `buildTypes { release { isDebuggable = true } }` → `TargetSdkComplianceTest` RED (via `--rerun-tasks`, since build.gradle.kts is not a Gradle test-input); script reverted clean.
  - Injected a second `"https://evil-divergent.example.com/privacy"` literal into `StoreListingCopy.kt` → URL guard RED "must declare EXACTLY ONE quoted https URL literal"; reverted clean.

## Task Commits
1. **Task 1 (TDD RED): failing Data-safety cross-check** — `f198479` (test)
2. **Task 1 (TDD GREEN): machine-checkable all-"No" DataSafetyDeclaration** — `b975dd9` (feat)
3. **Task 2: TargetSdkComplianceTest — SDK pins + non-debuggable release** — `50f1f83` (test)
4. **Task 3: single-source PRIVACY_POLICY_URL + consistency guard** — `a4faba2` (feat)

_No STATE.md / ROADMAP.md commit — per the objective, this run does NOT update STATE.md or ROADMAP.md (the orchestrator owns state)._

## Files Created/Modified
- `content/DataSafetyDeclaration.kt` (created) — committed all-"No" Play Data-safety answers; single source for DATA-SAFETY.md (Plan 03).
- `content/DataSafetyManifestCrossCheckTest.kt` (created) — conditional binding of the declaration to the no-INTERNET manifest + no-analytics catalog + no-health-WRITE posture (red-capable).
- `content/TargetSdkComplianceTest.kt` (created) — static build-script guard: targetSdk/compileSdk==36, minSdk==26, non-debuggable release.
- `content/PrivacyPolicyUrlConsistencyTest.kt` (created) — well-formed-https / non-PDF / single-literal-in-content guard.
- `content/PrivacyPolicyText.kt` (modified) — added the single-source `PRIVACY_POLICY_URL` placeholder constant.

## Test Results (REAL, run on this host)
- **New guards** (`:app:testDebugUnitTest`): `DataSafetyManifestCrossCheckTest` 1/1, `TargetSdkComplianceTest` 1/1, `PrivacyPolicyUrlConsistencyTest` 2/2 — all GREEN, 0 failures, 0 errors.
- **No-regression wave gate** `./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL**:
  - `:app:testDebugUnitTest` — **460/460 passed, 0 failures** (+4 over the 456 baseline from the four new test methods).
  - `:insights:test` — **176/176 passed, 0 failures** (untouched; catalog audit still green).
  - `:app:assembleDebug` — **BUILD SUCCESSFUL**, real 16.0 MB (16,001,604-byte) debug APK.
  - `:app:lintDebug` — **BUILD SUCCESSFUL**, no new lint findings.
- **Red-capability** — all four injections verified RED with the exact assertion message, all four sources reverted clean (see Accomplishments).

## Deviations from Plan
None — plan executed exactly as written. One implementation note worth recording: because `build.gradle.kts` is not a Gradle test-task INPUT, `testDebugUnitTest` is cached across a pure build-script edit — so the `isDebuggable = true` red-capability proof required `--rerun-tasks` to force re-execution. This does not affect the shipped guard: a fresh checkout / CI run executes the test once against the committed script (which pins 36/26 and declares no debuggable release), so it correctly guards the real ship config.

## Threat Model Compliance
- **T-25-01 (Repudiation / Information disclosure — DataSafetyDeclaration vs manifest/catalog, high):** mitigated — the all-"No" declaration is bound to the no-INTERNET manifest + no-analytics catalog + no-health-WRITE posture via a conditional cross-check that is red-capable in both directions (proven). The Play form-vs-reality diff can never silently pass a drifted form.
- **T-25-03 (Tampering — release build config, medium):** mitigated — `TargetSdkComplianceTest` pins targetSdk/compileSdk==36, minSdk==26, and asserts no `isDebuggable = true` in the build script (proven red-capable).
- **T-25-04 (Information disclosure — privacy-policy URL, medium):** mitigated — one `PRIVACY_POLICY_URL` source + a guard asserting non-blank / https / non-PDF and that it is the only privacy-policy https literal in the content package (proven red-capable against a divergent literal).
- **T-25-SC (Tampering — package installs, low/accept):** honored — zero package installs this plan; no new version-catalog coordinate (`VersionCatalogAuditTests` stays green).

## Known Stubs
- **`PRIVACY_POLICY_URL` is an intentional placeholder** (`https://replace-me.github.io/endo-app/privacy-policy`). This is DELIBERATE and documented (ASSUMED A4): the repo has no hosted privacy page, and fabricating a "live" URL would be dishonest. The user must host a public, non-geofenced HTML page mirroring `PrivacyPolicyText.body` and replace this value (entering the SAME URL in Play Console) before submission. This is a required pre-submission human step surfaced in Plan 03's RELEASE-CHECKLIST. The constant's SHAPE (well-formed https, single-source) is guarded now; only its hosted value is user-supplied later. No other stubs.

## Self-Check: PASSED
- All four created files + the modified file exist on disk (Write/Edit succeeded; harness-tracked):
  - `content/DataSafetyDeclaration.kt` — FOUND
  - `content/DataSafetyManifestCrossCheckTest.kt` — FOUND
  - `content/TargetSdkComplianceTest.kt` — FOUND
  - `content/PrivacyPolicyUrlConsistencyTest.kt` — FOUND
  - `content/PrivacyPolicyText.kt` — FOUND (PRIVACY_POLICY_URL added)
- All four task commits present in git history: `f198479` (test/RED), `b975dd9` (feat/GREEN), `50f1f83` (test), `a4faba2` (feat).
- Real build/test evidence captured above (4 new tests green, `:app` 460/460, `:insights` 176/176, `assembleDebug` 16.0 MB APK, `lintDebug` clean, 4 red-capability proofs).

## TDD Gate Compliance
Task 1 followed the RED→GREEN cycle with distinct commits: `f198479` (RED — `test(...)`, failing cross-check with unresolved `DataSafetyDeclaration` reference) preceded `b975dd9` (GREEN — `feat(...)`, the declaration turning it green). No REFACTOR commit was needed. Tasks 2 and 3 are guard-test + config artifacts (not behavior-adding source), committed test-first / constant-with-guard.

---
*Phase: 25-play-store-prep*
*Completed: 2026-07-10*
