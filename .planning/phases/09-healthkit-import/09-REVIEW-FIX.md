---
phase: 09-healthkit-import
fixed_at: 2026-06-09T18:05:00Z
review_path: .planning/phases/09-healthkit-import/09-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 6
skipped: 1
status: partial
---

# Phase 9: Code Review Fix Report

**Fixed at:** 2026-06-09T18:05:00Z
**Source review:** .planning/phases/09-healthkit-import/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope (Critical + Warning): 7
- Fixed: 6 (CR-01, CR-02, WR-01, WR-03, WR-04, WR-05)
- Skipped: 1 (WR-02 — deferred with rationale; out of safe scope for a per-finding fix)

**Verification:** All fixes built and tested on the iOS Simulator (`name=iPhone 16`,
Swift 6.0 / Xcode 26.5). Test results:
- `HealthImportMergeTests` + `HealthImportServiceTests`: **13/13 passed** (incl. the new
  CR-01, CR-02, WR-04 tests).
- `HealthSampleMappingTests` + `HealthAnchorPullTests` + `RepositoryDismissalTests` +
  `HealthConnectCopyAuditTests`: **30/30 passed** (no regression in the Repository/mapping
  paths the fixes touch).
- `HealthConnectUITests` (E2E, fixture-seam): **4/4 passed** — the connect/rationale flow
  still works after the WR-03 (Continue-only "decided") and WR-05 (single unioned auth)
  changes.

**Locked invariants preserved:** manual-wins / nil≠0 (SC3) — verified by the unchanged,
still-passing `manualWinsOverImported` and `emptyImportLeavesFieldsNil` load-bearing tests;
read-only (`toShare: []`) preserved in the new unioned `requestAuthorization()`; HealthKit
stays out of the EndoInsights package (all edits are app-side EndoApp); consent-gating intact
(`assertConsented()` first statement everywhere); no health-content logging introduced.

## Fixed Issues

### CR-01: Sleep merge overwrites instead of summing — nightly sleep mis-reported

**Files modified:** `Sources/EndoApp/Persistence/Repository.swift`,
`Sources/EndoApp/Health/SystemHealthDataReading.swift`,
`Tests/EndoAppTests/HealthImportMergeTests.swift`
**Commits:** `1e18c10` (sum + comment + test) and `139f56e` (SleepQuality enum-bridge compile fix)
**Status:** fixed
**Applied fix:** Took review option (a). `ImportedSignals.init` now accumulates
`sleepHours = (sleepHours ?? 0) + rowHours` across a day's `.sleep` rows (instead of
last-writer-wins on one ~minutes fragment) and derives the coarse `SleepQuality` band from
the **summed total** via `HealthSampleMapping.sleepQuality(forAsleepHours:)`, bridged to
`EndoInsights.SleepQuality(raw:)` (same-raw passthrough). A `nil` row contributes nothing so
`sleepHours` stays `nil` until a real `.sleep` row arrives (nil≠0, SC3). Corrected the
"the merge sums them" comment in `SystemHealthDataReading` to reflect that the per-segment
`quality` is now advisory (the merge recomputes the band from the night total). Added
`multiSegmentSleepSumsAndBandFollowsTotal`: feeds 6 fragments summing to 7.5h and asserts the
merged `sleepHours == 7.5` and `sleepQuality == .good` (band of the total, not a fragment's
`.poor`).

### CR-02: replaceOrInsertImported non-atomic delete-then-upsert with per-value saves

**Files modified:** `Sources/EndoApp/Persistence/Repository.swift`,
`Tests/EndoAppTests/HealthImportMergeTests.swift`
**Commit:** `c09299b`
**Status:** fixed
**Applied fix:** Extracted a non-saving `applyUpsert(_:)` (stages the dedupe-by-uuid upsert
against the context without saving). `replaceOrInsertImported` now stages **all** deletions
and **all** upserts, then calls `context.save()` **exactly once**, wrapped in `do/catch` with
`context.rollback()` on any throw so a mid-batch failure leaves the store **unchanged**
(all-or-nothing) — matching the "in one consent-gated unit" docstring and the import service's
"contributed nothing ⇒ don't advance the cursor" contract. The single-value public
`upsertImportedSample` keeps its own save (calls `applyUpsert` then `save`). Added
`sameUUIDDeleteThenReAddNetsToPresent` (same-UUID delete+re-add nets to "present") and
`batchAppliesDeletionsAndUpsertsAsOneUnit` (delete + survivor + new insert all land in one
save).

### WR-01: Provenance drift between makeDaySnapshot's activity gate and daySnapshotSources

**Files modified:** `Sources/EndoApp/Persistence/Repository.swift`
**Commit:** `bd75552`
**Status:** fixed
**Applied fix:** `daySnapshotSources(on:)` now computes the activity source with the **same**
gate the merge uses (`useImportedActivity`): `.appleHealth` only when there is no manual
activity **and** an imported activity **type** is present. Imported minutes-only can no longer
claim a badge the value path never grants, so value and provenance cannot drift.

### WR-03: HealthConnectSection marks "decided" on any dismissal, including "Not now"

**Files modified:** `Sources/EndoApp/Features/Health/HealthConnectSection.swift`,
`Sources/EndoApp/Features/Health/HealthRationaleView.swift`
**Commit:** `3c6659f`
**Status:** fixed
**Applied fix:** Replaced the `.sheet(onDismiss:)` unconditional `hasDecided = true` with an
`onProceed` closure on `HealthRationaleView` that is invoked **only** from the Continue path
(`proceed()`), before dismiss. "Not now" now leaves the affordance in its first-run state (the
user can reach the in-app rationale again) instead of permanently routing to Settings.app for
a request that never happened. The E2E `testRationalePrecedesSystemSheet` (Continue dismisses
the rationale) still passes.

### WR-04: Multiple workouts per day collapse non-deterministically (last-writer-wins)

**Files modified:** `Sources/EndoApp/Persistence/Repository.swift`,
`Tests/EndoAppTests/HealthImportMergeTests.swift`
**Commit:** `1e18c10` (same `ImportedSignals.init` edit as CR-01)
**Status:** fixed
**Applied fix:** The `.workouts` case in `ImportedSignals.init` now **sums** minutes across the
day's workout rows and keeps the **longest** session's type (deterministic regardless of fetch
order), instead of arbitrary last-writer-wins. A typed-but-minute-less row still establishes
the type without fabricating minutes. Added `multiWorkoutSumsMinutesAndKeepsLongestType`
(20-min walk + 45-min strength ⇒ 65 min, `.strength`). Committed with CR-01 because both are
inseparable edits to the same `ImportedSignals.init` method/test file.

### WR-05: requestAuthorization runs once per domain (up to four sequential system sheets)

**Files modified:** `Sources/EndoApp/Health/HealthDataReading.swift`,
`Sources/EndoApp/Health/SystemHealthDataReading.swift`,
`Sources/EndoApp/Health/FixtureHealthDataReading.swift`,
`Sources/EndoApp/Health/HealthImportService.swift`,
`Sources/EndoApp/Features/Health/HealthRationaleView.swift`
**Commit:** `2e6cfdc`
**Status:** fixed
**Applied fix:** Added `requestAuthorization()` (no domain) to the `HealthDataReading` seam,
the service, and both conformers. The real reader issues **one**
`HKHealthStore.requestAuthorization(toShare: [], read:)` over
`Set(HealthDomain.allCases.flatMap { $0.readTypes })` — at most one system sheet
(Apple-recommended). `HealthRationaleView.proceed()` calls it **once** before looping
`importNew(for:)` per domain, replacing the up-to-four sequential per-domain prompts. The
per-domain `requestAuthorization(for:)` remains on the seam (still used by
`HealthImportServiceTests`). Read-only (`toShare: []`) preserved; consent-gating preserved in
the service wrapper.

## Skipped Issues

### WR-02: Sleep that crosses midnight is bucketed into the start day, fragmenting a night

**File:** `Sources/EndoApp/Health/SystemHealthDataReading.swift:115,128-145`
**Reason:** skipped — deferred with rationale (risky / out of safe per-finding scope).
**Original issue:** `day = Calendar.current.startOfDay(for: sample.startDate)` buckets a
segment starting at 23:40 and one at 00:20 of the same night into two different calendar days.

**Why deferred (not forced):** A correct fix requires (a) **choosing and documenting** a
night-attribution rule (e.g. attribute a whole sleep session to the calendar day it ENDS, or a
noon-to-noon "sleep day") — a product/spec decision, not a mechanical edit — and (b)
**restructuring** the reader so segments are rolled up into one sleep session under a single
day key **before** emitting. The current mapping seam `value(from:)` is pure and sees exactly
**one** `HKSample` at a time, so it cannot aggregate sessions; the rollup would have to move
into `pull` (which today maps each sample independently inside the anchored-query handler).
That is a structural change to the HealthKit-facing reader with its own Swift-6 isolation and
anchored-query-delta implications, and it interacts with the CR-01 per-day summing (changing
the day key changes which segments sum together). Forcing a partial fix here risked either an
incorrect attribution rule or a regression in the freshly-fixed CR-01 summing, with no safe
unit-level proof on the simulator (it needs real multi-day session fixtures).

**Mitigation applied (no behavior change):** Documented the limitation explicitly in
`SystemHealthDataReading.swift` (the `.sleep` case now carries a "NIGHT ATTRIBUTION (WR-02,
known limitation)" note pointing at the needed `pull`-level session rollup) so a future reader
does not mistake the start-day bucketing for intended behavior. This documentation rode in on
the CR-01 commit (`1e18c10`). Recommend a dedicated follow-up task: define the
night-attribution rule, roll segments into sessions inside `pull`, and add a
midnight-crossing fixture test.

---

_Fixed: 2026-06-09T18:05:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
