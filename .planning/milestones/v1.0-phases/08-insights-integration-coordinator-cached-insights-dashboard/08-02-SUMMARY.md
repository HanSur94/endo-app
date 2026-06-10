---
phase: 08-insights-integration-coordinator-cached-insights-dashboard
plan: 02
subsystem: insights-integration
tags: [swift6, observable, mainactor, task-detached, debounce, cache, fingerprint, environment-injection]

# Dependency graph
requires:
  - phase: 08-01
    provides: "Insight Codable + CachedInsight @Model/SchemaV5 + Repository.loadCachedInsights/replaceCachedInsights (consent-gated CRUD)"
  - phase: 05-insights-engine-keystone
    provides: "InsightsEngine.insights(from:asOf:dismissed:) pure facade + DaySnapshot value contract"
  - phase: 06-insight-honesty-dismissal
    provides: "Repository.daySnapshots()/dismissedSignalKeys() + the dismissal feed-through contract"
provides:
  - "InsightFingerprint.make(snapshots:dismissed:) — pure, launch-stable input fingerprint (count + latest-edit + dismissed-canonical)"
  - "InsightsCoordinator (@Observable @MainActor): debounced off-main recompute, fingerprint cache hit/miss, instant cached load, dismiss feed-through, isUpdating"
  - "\\.insightsCoordinator EnvironmentKey + AppDependencies construction over a live SwiftDataRepository"
  - "-uitest-seed-insights launch arg seeding a threshold-crossing in-memory fixture (gated behind -uitest-reset)"
  - "Insight/InsightPoint/InsightConfidence now Hashable (additive)"
affects: [08-03-card-chart, 08-04-dashboard]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Canonical launch-stable fingerprint string (count|latestDayEpoch|dismissedSorted) — NEVER Set.hashValue (per-process randomized)"
    - "@Observable @MainActor coordinator: main-actor reads → Task.detached(.userInitiated) PURE engine → publish on @MainActor (the only off-main work)"
    - "Cancellable-Task debounce (Task.sleep ~400ms) coalescing rapid dataChanged() into one recompute"
    - "Fully injectable seams (loadSnapshots/loadDismissed/persist/loadCache/now/engine/sleep) so unit tests need no store/clock"

key-files:
  created:
    - "Sources/EndoApp/Features/Insights/InsightFingerprint.swift"
    - "Sources/EndoApp/Features/Insights/InsightsCoordinator.swift"
    - "Tests/EndoAppTests/InsightFingerprintTests.swift"
    - "Tests/EndoAppTests/InsightsCoordinatorTests.swift"
  modified:
    - "Sources/EndoApp/App/AppDependencies.swift"
    - "Sources/EndoApp/App/EndoAppApp.swift"
    - "EndoInsights/Sources/EndoInsights/Insight.swift"

key-decisions:
  - "Fingerprint is a canonical STRING (count|latestDayEpoch|dismissedSorted), not Set.hashValue — launch-stable so a cold open is a cache hit (D-04), and includes the latest-edit timestamp so an edit (not just a count change) flips it (Pitfall 5)"
  - "The PURE engine is the ONLY work in Task.detached(.userInitiated); snapshots/dismissed are Sendable value types so they cross with no friction; insights/isUpdating are assigned back on @MainActor (SC2 no-jank)"
  - "Debounce is a single cancellable Task + injectable sleep seam; a call-counting engine seam + a manually-releasable sleep seam prove N→1 coalescing deterministically with no real time"
  - "-uitest-seed-insights seeds 21 consecutive pelvicPain days (well past minSample(.trend)==3) and is gated behind in-memory -uitest-reset so it can never write a real store (T-08-06)"

requirements-completed: [INST-01, INST-02, INST-03, INST-04]

# Metrics
duration: ~30min
completed: 2026-06-09
---

# Phase 8 Plan 02: Insights Integration Coordinator + Cached-Insights Wiring Summary

**Built the integration brain: a pure launch-stable `InsightFingerprint` and an `@Observable @MainActor InsightsCoordinator` that reads snapshots+dismissed on the main actor, runs the pure `InsightsEngine` OFF the main thread via `Task.detached`, debounces rapid edits into one recompute, caches by fingerprint (hit skips / miss recomputes+persists), loads the cache instantly on open, and feeds dismissed keys through — then wired it into `AppDependencies` over the live Repository and added the `-uitest-seed-insights` E2E seed.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-06-09
- **Tasks:** 3 (Tasks 1–2 TDD, Task 3 auto)
- **Files:** 4 created (2 sources, 2 tests), 3 modified

## Accomplishments
- `InsightFingerprint.make(snapshots:dismissed:)` — a pure Foundation-only canonical string `count|latestDayEpoch|dismissedSorted`. Launch-stable (no `Set.hashValue`, Pitfall 5), edit-sensitive (latest-day flips it even at constant count), and dismissed-order-independent (canonical sort).
- `InsightsCoordinator` — `@Observable @MainActor final class` with `private(set) insights`/`isUpdating`. Reads snapshots+dismissed on main, runs the PURE engine in `Task.detached(.userInitiated)` (the ONLY off-main work, SC2), publishes `[Insight]` back on `@MainActor`. A ~400ms cancellable-Task debounce coalesces rapid `dataChanged()` into one recompute; the fingerprint drives cache hit (skip) / miss (recompute + `replaceCachedInsights`); `loadCached()` paints instantly from the cache; a dismissed signalKey feeds through into the engine's `dismissed` set. Fully injectable seams + `\.insightsCoordinator` env key.
- `AppDependencies` constructs the coordinator over a live `SwiftDataRepository(context: ModelContext(modelContainer))` (wired to the 08-01 CRUD), injected at the App root via `\.insightsCoordinator`. The `-uitest-seed-insights` arg seeds 21 consecutive `pelvicPain` severity-6 days (crosses `minSample(.trend)==3`), gated behind in-memory `-uitest-reset` (T-08-06).

## Task Commits

1. **Task 1: InsightFingerprint (pure, launch-stable)** — `522d444` (test) → `accc7cd` (feat)
2. **Task 2: InsightsCoordinator (debounce/cache/off-main)** — `7b56532` (test) → `0e5b1e0` (feat)
3. **Task 3: AppDependencies injection + -uitest-seed-insights** — `528797c` (feat)

## Files Created/Modified
- `Sources/EndoApp/Features/Insights/InsightFingerprint.swift` — the pure canonical fingerprint.
- `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift` — the `@Observable @MainActor` coordinator + `\.insightsCoordinator` env key.
- `Sources/EndoApp/App/AppDependencies.swift` — coordinator construction over the live repo + `-uitest-seed-insights` seed.
- `Sources/EndoApp/App/EndoAppApp.swift` — `.environment(\.insightsCoordinator, …)` at the App root.
- `EndoInsights/Sources/EndoInsights/Insight.swift` — additive `Hashable` on `Insight`/`InsightPoint`/`InsightConfidence` (deviation — see below).
- `Tests/EndoAppTests/InsightFingerprintTests.swift` — stability / edit-not-count / order-independence / change-detection.
- `Tests/EndoAppTests/InsightsCoordinatorTests.swift` — instant load, cache hit/miss, off-main+publish-on-main, dismiss feed-through, debounce N→1.

## Decisions Made
- **Canonical string fingerprint, not a hash** — `count|latestDayEpoch|dismissedSorted` is launch-stable (so a cold open is a cache HIT) and includes the latest-edit timestamp so an edit at constant count still flips it. `Set.hashValue` would be per-process randomized (Pitfall 5) and would force a recompute on every launch.
- **Pure engine only in `Task.detached`** — `snapshots`/`dismissed` are `Sendable` value types crossing in cleanly; `insights`/`isUpdating` are assigned on `@MainActor`. This is the SC2 no-jank guarantee.
- **Injectable engine + sleep seams** — a call-counting engine and a manually-releasable sleep seam prove debounce coalescing (10→1), cache hit/miss, off-main execution, and dismiss feed-through deterministically with no store and no real time.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added additive `Hashable` to `Insight`/`InsightPoint`/`InsightConfidence`**
- **Found during:** Task 1 (first whole-target compile).
- **Issue:** The 08-01 test `CachedInsightRoundTripTests.swift:56` uses `Set(loaded) == Set([a, b])`, which requires `Insight: Hashable` — but `Insight` was only `Equatable`/`Codable`/`Identifiable`. The EndoAppTests target did NOT compile (verified by removing my new files and reproducing the same `'Set' requires that 'Insight' conform to 'Hashable'` error). This pre-existing breakage from 08-01 blocked verification of every task in this plan, so it had to be fixed.
- **Fix:** Added `Hashable` to `Insight`, `InsightPoint`, and `InsightConfidence` (additive; `InsightKind`/`TrendWindow` are String-raw enums that synthesize `Hashable` automatically). Free synthesis — all stored members are already Hashable.
- **Files modified:** `EndoInsights/Sources/EndoInsights/Insight.swift`.
- **Commit:** `accc7cd` (folded into the Task 1 feat commit).
- **Impact:** Additive only — `Insight` stays `Equatable`/`Sendable`/`Codable`/`Identifiable`. The EndoInsights package suite (96/96) and EndoAppTests both go green; the 08-01 round-trip test now compiles and passes as its author intended.

**Total deviations:** 1 (a blocking pre-existing compile break in 08-01's test, fixed additively in the package).

## Issues Encountered
- **One flaky simulator run:** the first whole-`InsightsCoordinatorTests` run reported only 5 ✔ then a duplicate suite-pass block and `TEST FAILED` (a parallel-runner/simulator hiccup). Re-running the suite cleanly showed all 6 tests pass, including `dataChanged_coalescesRapidCallsIntoOneRecompute`. No code change was needed — the debounce test is deterministic via the injected sleep seam.
- **Pre-existing medication UI flake (out of scope):** the known `EndoAppUITests` medication-toggle flake lives in a separate UI-test target and is not exercised by `EndoAppTests`; this plan touched no check-in/medication/view code.

## Verification
- `xcodebuild test … -only-testing:EndoAppTests/InsightFingerprintTests` — 6/6 green (stable, edit-sensitive, order-independent).
- `xcodebuild test … -only-testing:EndoAppTests/InsightsCoordinatorTests` — 6/6 green (instant load, cache hit/miss, off-main + publish-on-main, dismiss feed-through, debounce N→1).
- `xcodebuild test … -only-testing:EndoAppTests` — full suite green (`Test run with 39 tests in 11 suites passed`, Swift Testing count), no regression from the AppDependencies/Insight wiring.
- `cd EndoInsights && swift test` — 96/96 green after the additive `Hashable`.

## Known Stubs
None — the coordinator is fully wired to the live `SwiftDataRepository` CRUD; no placeholder/empty-value stubs. No dashboard view renders yet (that is 08-03/08-04 by design — the coordinator publishes `insights`/`isUpdating` they will consume).

## Threat Flags
None — no new network endpoints, auth paths, or trust-boundary schema changes beyond the plan's `<threat_model>`. The off-main compute logs no health content (T-08-05); the seed is in-memory-only behind `-uitest-reset` (T-08-06); the dismiss→fingerprint→recompute path honors dismissals (T-08-07); all store access is via the consent-gated Repository seams (T-08-08).

## Next Phase Readiness
- 08-03 / 08-04 consume `coordinator.insights` + `coordinator.isUpdating`, call `coordinator.loadCached()` on appear and `coordinator.dataChanged()` on edits/foreground, and read the `\.insightsCoordinator` env key. The `-uitest-seed-insights` arg is ready for the dashboard E2E.

## Self-Check: PASSED

All four created files exist on disk; all five task commit hashes (`522d444`, `accc7cd`, `7b56532`, `0e5b1e0`, `528797c`) are present in git history.

---
*Phase: 08-insights-integration-coordinator-cached-insights-dashboard*
*Completed: 2026-06-09*
