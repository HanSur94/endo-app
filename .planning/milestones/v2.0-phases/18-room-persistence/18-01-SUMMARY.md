---
phase: 18-room-persistence
plan: 01
subsystem: infra
tags: [android, gradle, agp9, room, ksp, datastore, kotlinx-serialization, version-catalog, ci, junit4]

# Dependency graph
requires:
  - phase: 17-insights-engine-port
    provides: ":insights pure-JVM module, the version catalog + VersionCatalogAuditTests, BoundaryLintTests, DaySnapshot + domain enums, android/ Gradle workspace"
provides:
  - "The :app Android Application module (com.android.application under AGP 9 built-in Kotlin), compileSdk 36 / minSdk 26 / targetSdk 36"
  - "Version catalog extended with Room 2.8.4, KSP 2.3.9, DataStore 1.2.0, AGP 9.2.0, JUnit4 4.13.2, androidx-test-ext 1.3.0"
  - "One-way :app -> :insights dependency edge, proven at test-compile time"
  - "Room schema export path (room.schemaLocation) wired for the irreversible v1 schema (Plan 02 entities)"
  - "CI :app JVM lane (test + lint + assemble) + main-only emulator lane"
  - "Minimal zero-permission :app manifest baseline (no INTERNET, no health WRITE)"
affects: [18-02, 18-03, 18-06, 19-ui-shell, 23-export-import]

# Tech tracking
tech-stack:
  added: [androidx.room 2.8.4, com.google.devtools.ksp 2.3.9, androidx.datastore 1.2.0, com.android.application 9.2.0 (AGP), junit 4.13.2, androidx.test.ext:junit 1.3.0]
  patterns: ["AGP 9 built-in Kotlin (org.jetbrains.kotlin.android NOT applied)", "KSP-only Room codegen (no kapt, no room-ktx)", "Testing split: JUnit4 in :app, JUnit Jupiter in :insights", "Version catalog as greppable supply-chain audit surface"]

key-files:
  created:
    - android/app/build.gradle.kts
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/test/kotlin/com/endoapp/app/ModuleSmokeTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/settings.gradle.kts
    - android/build.gradle.kts
    - .github/workflows/android.yml

key-decisions:
  - "AGP 9 built-in Kotlin: :app applies com.android.application only; org.jetbrains.kotlin.android deliberately NOT applied (the #1 stale-doc trap; opt-out removed in AGP 10)"
  - "Room codegen via KSP only (no kapt); room-runtime carries coroutines/Flow so room-ktx is not added"
  - "Emulator instrumentation lane gated push-to-main only; empty connectedDebugAndroidTest passes until Plan 02/03 land MigrationTestHelper tests"
  - "applicationId/namespace com.endoapp.app are placeholders finalizable at release (Phase 25)"

patterns-established:
  - "AGP-9 module shape: built-in Kotlin, jvmToolchain(21), no separate KGP application in :app"
  - "room.schemaLocation KSP arg points at android/app/schemas for the irreversible v1 schema"
  - "CI testing split preserved: :insights Jupiter job unchanged, :app JUnit4 lane added"

requirements-completed: [APER-01]

coverage:
  - id: D1
    description: ":app Android Application module builds under AGP 9.2 built-in Kotlin (kotlin.android NOT applied)"
    requirement: APER-01
    verification:
      - kind: integration
        ref: "cd android && ./gradlew :app:assembleDebug (produces app/build/outputs/apk/debug/app-debug.apk, 3.4MB)"
        status: pass
    human_judgment: false
  - id: D2
    description: "One-way :app -> :insights dependency resolves at test-compile time"
    requirement: APER-01
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/ModuleSmokeTest.kt#insightsTypeIsReachableFromApp (:app:testDebugUnitTest, 1/1 green)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Room/KSP/DataStore/serialization coordinates added without breaking the version-catalog supply-chain audit"
    requirement: APER-01
    verification:
      - kind: unit
        ref: "VersionCatalogAuditTests#catalogContainsNoForbiddenCoordinate (:insights:test, green)"
        status: pass
    human_judgment: false
  - id: D4
    description: "CI runs a :app JVM lane per push/PR and a main-only emulator lane"
    requirement: APER-01
    verification:
      - kind: other
        ref: "python3 yaml assertion over .github/workflows/android.yml — both :app:testDebugUnitTest and connectedDebugAndroidTest lanes present ('CI lanes ok')"
        status: pass
    human_judgment: false

# Metrics
duration: 16min
completed: 2026-07-09
status: complete
---

# Phase 18 Plan 01: Room Persistence Foundation Summary

**Created the `:app` Android Application module under AGP 9.2 built-in Kotlin and catalogued Room 2.8.4 / KSP 2.3.9 / DataStore 1.2.0 / kotlinx-serialization — `:app:assembleDebug` produces a real debug APK, the one-way `:app → :insights` edge is proven, and CI gains a `:app` JVM lane plus a main-only emulator lane, all with the Phase-17 audit tests staying green.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-07-09T11:20Z
- **Completed:** 2026-07-09T11:37Z (commits 11:32–11:36 local)
- **Tasks:** 3
- **Files modified:** 7 (3 created, 4 modified)

## Accomplishments
- `:app` module builds under AGP 9.2 built-in Kotlin — `com.android.application` applied WITHOUT `org.jetbrains.kotlin.android` (the locked AGP-9 trap avoided); `:app:assembleDebug` genuinely produced `app-debug.apk` (3.4 MB) on this host (Android SDK platform 36 / build-tools 36.0.0 installed).
- Version catalog extended with all Phase-18 data-layer coordinates (Room, KSP plugin, DataStore, AGP, JUnit4, androidx-test-ext) — all first-party Google/JetBrains Maven; `VersionCatalogAuditTests` stays green (no analytics/auth/backend/networking coordinate slipped in).
- One-way `:app → :insights` dependency wired and proven: `ModuleSmokeTest` constructs an `:insights` `DaySnapshot` and runs green in `:app:testDebugUnitTest` (1/1).
- Room `room.schemaLocation` KSP arg wired to `$projectDir/schemas` so the irreversible v1 schema export lands correctly when Plan 02 adds entities.
- Minimal zero-permission manifest (no INTERNET, no health WRITE, no `<activity>`) — a clean baseline for Phase 19's no-INTERNET assertion; backup rules deferred to Plan 06.
- CI extended: a `:app` JVM merge-gate lane (`:app:testDebugUnitTest` + `lintDebug` + `assembleDebug`) on push/PR, plus a push-to-main-only emulator lane (`reactivecircus/android-emulator-runner@v2`, api-level 34, `:app:connectedDebugAndroidTest`). The `:insights` job is untouched.
- No regression: `:insights` full suite still 176/176 green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Version catalog + settings + root build + :app build.gradle.kts** - `6769c35` (feat)
2. **Task 2: Minimal manifest + module compiles + JVM smoke test** - `8dead26` (feat)
3. **Task 3: Extend CI with :app JVM lane and main-only emulator lane** - `3cbffcd` (ci)

_No STATE.md / ROADMAP.md metadata commit — the orchestrator owns state updates for this run (per objective)._

## Files Created/Modified
- `android/app/build.gradle.kts` - Created. `com.android.application` (built-in Kotlin) + serialization + ksp; compileSdk 36 / minSdk 26 / targetSdk 36; `jvmToolchain(21)`; `room.schemaLocation` arg; one-way `implementation(project(":insights"))` + Room/DataStore/serialization/JUnit4/androidTest deps.
- `android/app/src/main/AndroidManifest.xml` - Created. Bare manifest, plain `<application>`, ZERO permissions.
- `android/app/src/test/kotlin/com/endoapp/app/ModuleSmokeTest.kt` - Created. JUnit4 smoke test referencing `:insights` `DaySnapshot`.
- `android/gradle/libs.versions.toml` - Modified. Added agp/room/ksp/datastore/junit4/androidx-test-ext versions, libraries, and android-application + ksp plugins; header updated for phases 18+.
- `android/settings.gradle.kts` - Modified. `include(":app", ":insights")`; stale Phase-19 comment removed.
- `android/build.gradle.kts` - Modified. Added `android.application` + `ksp` apply-false aliases.
- `.github/workflows/android.yml` - Modified. Added `app-jvm` and `app-instrumentation` jobs; `:insights` job + path filters preserved.

## Decisions Made
- **Emulator lane gate:** used `github.event_name == 'push' && github.ref == 'refs/heads/main'` to honor the plan's "main-only" intent precisely (rather than any-branch push), since the workflow's path filter fires on push to any branch. The plan's verify assertion (lane present) still passes.
- **Manifest label:** inlined `android:label="Endo"` rather than introducing a `strings.xml` resource, keeping the Phase-18 manifest genuinely minimal (a real string resource arrives with the Phase-19 UI shell).
- **`allowBackup="true"` retained** on the bare `<application>` (AGP default) — the health-DB backup EXCLUSION rules are explicitly owned by Plan 06, so no `dataExtractionRules`/`fullBackupContent` added here per the plan.

## Deviations from Plan
None - plan executed exactly as written. No Rule 1–4 deviations were required; the SDK was present so every build/test ran for real.

## Issues Encountered
- The `:app:stripDebugDebugSymbols` task emitted a benign notice ("Unable to strip libdatastore_shared_counter.so, packaging as-is") from the DataStore native lib — this is expected on a host without the matching NDK strip tooling and does not affect the debug APK or any test; no action taken.

## Threat Model Compliance
- **T-18-SC (supply chain):** all added coordinates are first-party Google/JetBrains Maven; `VersionCatalogAuditTests` re-run green after the additions — no forbidden (analytics/auth/backend/networking) substring entered the catalog.
- **T-18-04 (reverse dependency):** `:app → :insights` is one-way; `:insights` remains `kotlin("jvm")` with no `project(":app")` edge; its BoundaryLintTests + 176-test suite stay green.

## User Setup Required
None - the Android SDK (platform 36, build-tools 36.0.0), JDK 21, and Gradle 9.6.1 are already provisioned on this host via `android/local.properties` (gitignored). CI provisions JDK 21 Temurin itself.

## Next Phase Readiness
- Plan 02 (Room entities + DAOs + MigrationTestHelper) can proceed: the module compiles, KSP is wired, and `room.schemaLocation` is set for the irreversible v1 schema export.
- The `:app` androidTest source set + `room-testing` dependency are catalogued and ready; the emulator lane will exercise them once instrumentation tests land.
- No blockers.

## Self-Check: PASSED

All 3 created files, 2 modified catalog/CI files, and the SUMMARY exist on disk; all 3 task commits (`6769c35`, `8dead26`, `3cbffcd`) are present in git history.

---
*Phase: 18-room-persistence*
*Completed: 2026-07-09*
