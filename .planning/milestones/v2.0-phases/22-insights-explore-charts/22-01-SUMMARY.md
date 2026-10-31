---
phase: 22-insights-explore-charts
plan: 01
subsystem: insights
tags: [kotlin, coroutines, stateflow, room, kotlinx-serialization, fingerprint-cache, off-main, debounce]

# Dependency graph
requires:
  - phase: 17-insights-engine-port
    provides: "InsightsEngine (one public gated producer), Insight/DaySnapshot value types"
  - phase: 18-room-persistence
    provides: "CachedInsight/DismissedInsight entities + InsightsDao CRUD, SnapshotProjector/daySnapshots(), consent-gated EndoRepository"
provides:
  - "InsightsCoordinator: app-scoped, off-main insight recompute + fingerprint cache + non-destructive dismiss, StateFlow<InsightsUiState> out"
  - "InsightFingerprint: pure, launch-stable cache hit/miss gate (count|latestDayEpoch|dismissedSorted)"
  - "EndoRepository.loadCachedInsights / replaceCachedInsights / dismissedSignalKeys (consent-gated)"
  - "AppDependencies.insightsCoordinator (constructed over live repo seams; threaded into the shell by Plan 04)"
affects: [22-04 dashboard render, 22-05 explore, 22-06 scatter]

# Tech tracking
tech-stack:
  added: ["kotlinx-coroutines-test (testImplementation, virtual-time lane)"]
  patterns:
    - "App-scoped plain coordinator (not a ViewModel) with fully injected suspend/closure seams"
    - "Fingerprint-gated replace-all cache: HIT skips the engine, MISS recomputes + persists"
    - "Off-main compute via withContext(defaultDispatcher); cancel-and-reschedule debounce (N→1)"
    - "Resilient JSON decode (runCatching.getOrNull) — one corrupt cache row is skipped, never fatal"

key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/insights/InsightFingerprint.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/insights/InsightsCoordinator.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/insights/InsightFingerprintTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/insights/InsightsCoordinatorTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/data/RepositoryInsightCacheTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt"
    - "android/gradle/libs.versions.toml"
    - "android/app/build.gradle.kts"

key-decisions:
  - "InsightsCoordinator is a plain app-scoped class (per ARCHITECTURE preference + iOS @Observable analog), not a ViewModel"
  - "Fingerprint formula ported verbatim from iOS: count|latestDayEpoch|dismissedSorted — no hashCode (Pitfall 5)"
  - "Persist is best-effort (runCatching at both the coordinator recompute and the AppDependencies seam)"
  - "Added kotlinx-coroutines-test to drive the debounce/off-main proofs deterministically on the JVM"

patterns-established:
  - "Injected-seam coordinator: every store/clock/engine dependency is a constructor closure for unit-testability"
  - "Consent gate FIRST on every new repo method (fail-closed under Unknown AND NotConsented)"
  - "Privacy boundary pinned by test: the cache payload serializes ONLY the gated Insight, never a DaySnapshot/note field"

requirements-completed: [AINS-01]

coverage:
  - id: D1
    description: "InsightFingerprint — pure, launch-stable cache hit/miss gate (identical inputs → identical string; a same-count latest-day edit or a new dismissed key flips it; no hashCode)"
    requirement: "AINS-01"
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/com/endoapp/app/insights/InsightFingerprintTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "EndoRepository consent-gated cache/dismiss methods — replace-all round-trip preserves ids, corrupt/null payload skipped not fatal, T-22-01 payload carries only the gated Insight, all three fail-closed pre-consent"
    requirement: "AINS-01"
    verification:
      - kind: integration
        ref: "app/src/test/kotlin/com/endoapp/app/data/RepositoryInsightCacheTest.kt (6 tests, Robolectric + in-memory Room)"
        status: pass
    human_judgment: false
  - id: D3
    description: "InsightsCoordinator — loadCached paints before engine, MISS recomputes+persists once, HIT skips engine, debounce N→1, dismiss append-only + excluded next recompute, engine runs off-main on the injected dispatcher"
    requirement: "AINS-01"
    verification:
      - kind: unit
        ref: "app/src/test/kotlin/com/endoapp/app/insights/InsightsCoordinatorTest.kt (6 tests, runTest virtual time + real off-main dispatcher)"
        status: pass
    human_judgment: false
  - id: D4
    description: "AppDependencies.insightsCoordinator constructed over live repository seams; no notification/alarm/WorkManager import added"
    requirement: "AINS-01"
    verification:
      - kind: integration
        ref: ":app:assembleDebug + :app:lintDebug SUCCESSFUL; NotificationSeparationTest 3/3 pass"
        status: pass
    human_judgment: false

# Metrics
duration: 27min
completed: 2026-07-09
status: complete
---

# Phase 22 Plan 01: InsightsCoordinator Summary

**App-scoped off-main insight recompute with a launch-stable fingerprint cache (HIT skips the engine, MISS recomputes + replace-all persists), non-destructive dismiss, and a StateFlow the Phase-22 dashboard renders — plus the consent-gated repo cache/dismiss seam.**

## Performance

- **Duration:** ~27 min
- **Started:** 2026-07-09T18:30:00Z (approx)
- **Completed:** 2026-07-09T18:56:40Z
- **Tasks:** 2
- **Files modified:** 9 (5 created, 4 modified)

## Accomplishments
- `InsightsCoordinator` (plain app-scoped class): `loadCached()` paints the cache instantly, `dataChanged()` debounces rapid triggers into ONE recompute, private `recompute()` runs the Phase-17 `InsightsEngine` ONLY inside `withContext(defaultDispatcher)` (off-main), gates HIT/MISS on `InsightFingerprint`, and persists replace-all best-effort. `dismiss()` writes an append-only `DismissedInsight` and excludes the key on the next recompute — never deletes logged data.
- `InsightFingerprint` (pure object): the canonical `count|latestDayEpoch|dismissedSorted` string, no `hashCode` (Pitfall 5), ported verbatim from iOS.
- Three consent-gated `EndoRepository` methods over the existing `InsightsDao`: `loadCachedInsights()` (resilient decode), `replaceCachedInsights()` (clear-then-insert, encodes ONLY the gated `Insight` — T-22-01), `dismissedSignalKeys()` (dedupe-on-read).
- Wired `AppDependencies.insightsCoordinator` over the live repo seams; not threaded into the shell (that is Plan 04).

## Task Commits

1. **Task 1: Consent-gated cache/dismiss repo methods + pure InsightFingerprint** — `54abd8c` (feat)
2. **Task 2: InsightsCoordinator + AppDependencies wiring** — `7a3dd7a` (feat)

## Files Created/Modified
- `insights/InsightFingerprint.kt` (created) - Pure launch-stable cache gate.
- `insights/InsightsCoordinator.kt` (created) - Off-main recompute + fingerprint cache + dismiss; `StateFlow<InsightsUiState>`.
- `data/EndoRepository.kt` (modified) - Added `loadCachedInsights`/`replaceCachedInsights`/`dismissedSignalKeys` + the single `Json` boundary.
- `AppDependencies.kt` (modified) - Constructed `insightsCoordinator` over live seams.
- `gradle/libs.versions.toml` + `app/build.gradle.kts` (modified) - Added `kotlinx-coroutines-test` (virtual-time test lane).
- `insights/InsightFingerprintTest.kt` (created, 5) - identical→identical, latest-day-edit flips, new-dismiss flips, empty well-formed, no-hashCode source scan.
- `data/RepositoryInsightCacheTest.kt` (created, 6) - replace-all round-trip + ids, wholesale-not-accretive, corrupt/null payload skipped, T-22-01 payload has no snapshot field, all-three fail-closed pre-consent.
- `insights/InsightsCoordinatorTest.kt` (created, 6) - loadCached-before-engine, MISS recompute+persist, HIT skips, debounce N→1, dismiss append-only+excluded, engine off-main on the injected dispatcher.

## REAL Test Results
- `:app:testDebugUnitTest` — **254/254 pass, 0 failures, 0 skipped** (237 baseline + 17 new).
- `:insights:test` — **176/176 pass** (source untouched; `VersionCatalogAuditTests` green with the new coroutines-test coordinate).
- `NotificationSeparationTest` — **3/3 pass** (coordinator imports no notification/alarm/WorkManager API).
- `:app:assembleDebug` — **SUCCESSFUL**.
- `:app:lintDebug` — **SUCCESSFUL**.
- Threat coverage proven: T-22-01 (payload carries no snapshot/note field), T-22-01b (dismiss is append-only, key excluded next recompute), T-22-01c (engine ran on a distinctly-named off-main thread, not the caller thread).

## Decisions Made
- Coordinator is a plain app-scoped class, not a ViewModel (ARCHITECTURE preference + iOS `@Observable` analog).
- Fingerprint formula matches iOS exactly (`count|latestDayEpoch|dismissedSorted`); verified against `Sources/.../InsightFingerprint.swift` and `InsightsCoordinator.swift`.
- `persist` wrapped best-effort at both the recompute site and the AppDependencies seam so a cache-write failure never drops fresh insights.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `kotlinx-coroutines-test` dependency**
- **Found during:** Task 2 (InsightsCoordinatorTest)
- **Issue:** The debounce (N→1) and off-main proofs require virtual-time control (`runTest` / `StandardTestDispatcher` / `advanceUntilIdle`), and the artifact was not on the `:app` test classpath.
- **Fix:** Added `kotlinxCoroutinesTest = "1.9.0"` + the `kotlinx-coroutines-test` library to the version catalog (a first-party JetBrains coordinate matching the coroutines-core 1.9.0 the app graph already resolves; no forbidden audit substring) and a `testImplementation` in `app/build.gradle.kts`. NOT a package-manager auto-substitution — an exact, known-good coordinate.
- **Verification:** `VersionCatalogAuditTests` stays green; coordinator suite passes.
- **Committed in:** `7a3dd7a`

**2. [Rule 3 - Blocking] Test scope switched from `backgroundScope` to the `TestScope` receiver**
- **Found during:** Task 2 (InsightsCoordinatorTest first run)
- **Issue:** Coroutines launched on `runTest`'s `backgroundScope` were not advanced by `advanceUntilIdle()` in this coroutines version (engine call count observed 0).
- **Fix:** Passed the `TestScope` (`this`) as the coordinator's scope so `advanceUntilIdle()` deterministically drains every launched job; all launched work completes within each test (no leftover-coroutine failure).
- **Verification:** 6/6 coordinator tests pass.
- **Committed in:** `7a3dd7a`

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking test infrastructure).
**Impact on plan:** Test-harness only. Production coordinator/fingerprint/repo behavior matches the plan exactly. No scope creep.

## Issues Encountered
- Initial coordinator tests failed because `backgroundScope` work was not advanced by `advanceUntilIdle()`; resolved by using the `TestScope` receiver directly (see Deviation 2).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `InsightsCoordinator.state` (`StateFlow<InsightsUiState>`) is ready for Plan 04 to thread into the Insights tab and render; the same `daySnapshots()` seam feeds Plans 05/06 (Explore).
- The consent-gated cache/dismiss repo surface is complete over the existing `InsightsDao`; no schema change was needed.

## Self-Check: PASSED

All 5 created source/test files and the SUMMARY exist on disk; both task commits (`54abd8c`, `7a3dd7a`) are in the git log.

---
*Phase: 22-insights-explore-charts*
*Completed: 2026-07-09*
