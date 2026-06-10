---
phase: 09-healthkit-import
plan: 02
subsystem: health-import
tags: [healthkit, anchored-pull, swift6-actor, manual-wins-merge, nil-honest, cloudkit-safe-anchor, provenance]
requires:
  - "09-01 contracts: HealthDataReading seam, ImportedSampleValue/ImportedPayload/ValueSource Sendable value types"
  - "09-01 persistence: SchemaV1.ImportedSample @Model under SchemaV6 (flat all-optional columns, hkSampleUUID dedupe key)"
  - "Phase 8 InsightsCoordinator.dataChanged() recompute seam (D-12)"
  - "Phase 5 EndoInsights.DaySnapshot value contract (nil=unlogged never 0, D-03)"
provides:
  - "HealthAnchorStore — per-HealthDomain HKQueryAnchor persisted outside CloudKit (UserDefaults + NSKeyedArchiver), corrupt-blob-safe"
  - "FixtureHealthDataReading — deterministic in-memory HealthDataReading (seed/empty, two-pull script), never touches HKHealthStore"
  - "HealthImportService — isolated actor: auth + anchored pull + persist + recompute, consent-gated, no-content logging"
  - "Repository.importedSamples CRUD (dedupe-by-uuid upsert, delete-by-uuid, replaceOrInsertImported)"
  - "Manual-wins, per-field, nil-honest merge in daySnapshots() (D-02/D-04/SC3)"
  - "Repository.daySnapshotSources(on:) — per-field provenance (.manual/.appleHealth/nil) for the D-03 badge"
  - "DaySnapshotSource app-side provenance value type"
affects:
  - "Sources/EndoApp/Persistence/Repository.swift (imported CRUD + merge + provenance read)"
  - "Sources/EndoApp/Health/ImportedSampleValue.swift (DaySnapshotSource added)"
tech-stack:
  added: []
  patterns: [actor-isolation-over-sendable-seam, anchor-outside-cloudkit, manual-wins-nil-honest-merge, separate-provenance-read]
key-files:
  created:
    - Sources/EndoApp/Health/HealthAnchorStore.swift
    - Sources/EndoApp/Health/FixtureHealthDataReading.swift
    - Sources/EndoApp/Health/HealthImportService.swift
    - Tests/EndoAppTests/HealthAnchorPullTests.swift
    - Tests/EndoAppTests/HealthImportServiceTests.swift
    - Tests/EndoAppTests/HealthImportMergeTests.swift
  modified:
    - Sources/EndoApp/Persistence/Repository.swift
    - Sources/EndoApp/Health/ImportedSampleValue.swift
decisions:
  - "Anchor persisted in an injectable AnchorKeyValueStore (UserDefaults in prod, InMemoryAnchorStore in tests) keyed health.anchor.<domain> — outside CloudKit (OQ2)"
  - "Provenance surfaced via a SEPARATE app-side daySnapshotSources(on:) read — package DaySnapshot contract left UNCHANGED so EndoInsights + engine tests stay untouched (minimal blast radius)"
  - "Nutrition presence (ateThatDay==true) contributes a single .other FoodTag ONLY when the day has no manual food tags — never a fabricated specific tag"
  - "Imported activity fills type+minutes as one unit only when there is no manual activity type (no grafting imported minutes onto a manual-typed activity)"
  - "Persist BEFORE advancing the anchor; on pull/persist failure discard the anchor (re-backfill) — never silent data loss, never crash (T-09-09/T-09-07)"
metrics:
  duration: "~27 min"
  completed: "2026-06-09"
  tasks: 2
  files: 8
---

# Phase 9 Plan 02: HealthKit Import — Import Service + Manual-Wins Merge Summary

The make-or-break vertical slice: the end-to-end import → merge → recompute path, fully testable on the
Simulator via the `HealthDataReading` fixture seam (no real device). An isolated anchored-pull
`HealthImportService` (dedupe-by-UUID, honor deletions, persist `ImportedSample`, fire the Phase-8
`InsightsCoordinator.dataChanged()` once), per-domain anchor persistence outside CloudKit, the
`FixtureHealthDataReading` test double, and — the load-bearing part — the **manual-wins, per-field,
nil-honest merge** in `Repository.daySnapshots()` (D-02/D-04/SC3) with a separate provenance read for the
D-03 "from Apple Health" badge.

## What Was Built

### Task 1 — Import service + anchor store + fixture reader (`0c909c8`)
- **`HealthAnchorStore`** — per-`HealthDomain` `HKQueryAnchor` persisted OUTSIDE CloudKit (OQ2) via an
  injectable `AnchorKeyValueStore` (production: `UserDefaults`; tests: `InMemoryAnchorStore`), keyed
  `health.anchor.<domain.rawValue>`. Archives with `NSKeyedArchiver(...requiringSecureCoding: true)`,
  reads back with `NSKeyedUnarchiver.unarchivedObject(ofClass:from:)`. A corrupt/undecodable blob
  returns `nil` (discard-and-re-backfill, T-09-09) — never crashes, never force-unwraps.
- **`FixtureHealthDataReading`** — deterministic in-memory `HealthDataReading`: a `.seed(values:deleted:delta:)`
  payload with a two-pull script (pull #1 = seed; pull #2 = the incremental delta, or — `.none` — the
  same seed replayed for the re-pull dedupe case) and `.empty` (denied == empty, SC3). NEVER constructs
  an `HKHealthStore` or runs a query (T-09-06). Scripted state lives behind an actor (Swift 6 race-free).
- **`HealthImportService`** — an isolated `actor` composing the Sendable seam (so it never touches a
  non-`Sendable` `HKSample`/`HKHealthStore`): load anchor → `pull` → persist values+deletions →
  store new anchor → `recompute()` once (D-12). Consent-gated (`consentGuard`, T-09-08); no-content
  logging (only error type, swallowed gracefully, T-09-07); persists BEFORE advancing the anchor so a
  store failure re-delivers samples next pull.
- **Repository imported CRUD** — `importedSamples(on:)`, `upsertImportedSample` (dedupe-by-`hkSampleUUID`,
  mirrors `setMedicationMarker`'s exists-guard), `deleteImportedSamples(uuids:)`, and the
  `replaceOrInsertImported(values:deletedUUIDs:)` entry the service's persist closure calls.
  Consent-gated; the `apply(_:to:)` mapper flattens `ImportedPayload` → the flat columns (resetting all
  value columns first so an in-place update never carries a stale field).
- **`HealthAnchorPullTests`** (5) + **`HealthImportServiceTests`** (5) GREEN: re-pull does not duplicate;
  a deletion UUID removes the row; `.empty` writes nothing; `recompute` fires exactly once; nothing
  persists pre-consent; the anchor round-trips through the archiver and a corrupt blob degrades to nil.

### Task 2 — Manual-wins, per-field, nil-honest merge + provenance, TDD (`7eb41dd` RED, `6af808d` GREEN)
- **`daySnapshots()`** now buckets `ImportedSample` rows by start-of-day and adds those keys to the day
  union (an imported-only day produces a `DaySnapshot` — SC1, `importedDayJoinsTheUnion`).
- **`makeDaySnapshot`** resolves EACH imported-capable field manual → imported → nil (NEVER 0): flow,
  sleepHours/sleepQuality, activity (type+minutes as one unit), and food-presence. Manual ALWAYS wins
  (D-02); a denied/empty import has no imported values so every field falls through to manual-or-nil
  (`emptyImportLeavesFieldsNil` — the make-or-break SC3 honesty test).
- **`ImportedSignals`** flattens a day's rows into package enums with defensive decode (unknown raws →
  the enum fallback; nil stays nil).
- **`daySnapshotSources(on:)`** + **`DaySnapshotSource`** surface per-field provenance
  (`.manual`/`.appleHealth`/`nil`) for the D-03 badge via a SEPARATE app-side read — the
  `EndoInsights.DaySnapshot` contract is UNCHANGED.
- **`HealthImportMergeTests`** (6) GREEN, including the two non-skippable honesty tests.

## Verification

| Check | Command | Result |
|-------|---------|--------|
| Task 1 suites | `-only-testing:.../HealthAnchorPullTests .../HealthImportServiceTests` | GREEN (10 tests) |
| Task 2 merge (RED→GREEN) | `-only-testing:.../HealthImportMergeTests` | GREEN (6 tests) |
| No app regression | `.../DaySnapshotMappingTests .../FactorSnapshotTests` | GREEN |
| Package contract intact | `-only-testing:EndoInsightsTests` | GREEN (DaySnapshot unchanged) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `@Sendable` persist closure cannot capture the non-Sendable `SwiftDataRepository`**
- **Found during:** Task 1 (first test build)
- **Issue:** `HealthImportService.persist` is `@Sendable` (the actor requires Sendable seams), but
  `SwiftDataRepository` holds a non-Sendable `ModelContext`, so `HealthAnchorPullTests` could not capture
  the repo directly: "Capture of 'repo' with non-Sendable type ... in a '@Sendable' closure."
- **Fix:** Wrapped the repo in a test-local `RepoBox: @unchecked Sendable`. The test single-threads every
  `await importNew(...)` (no real concurrent access), and this mirrors how `AppDependencies` will hop the
  persist onto the repo's isolation in 09-03. The production seam shape (`@Sendable persist`) is unchanged.
- **Files modified:** Tests/EndoAppTests/HealthAnchorPullTests.swift
- **Commit:** 0c909c8 (folded into the Task-1 commit)

**2. [Rule 1 - Correctness] `FlowIntensity` ambiguity in the merge test**
- **Found during:** Task 2 (RED build)
- **Issue:** `FlowIntensity.medium`/`.heavy` are ambiguous in the test (both `EndoApp` and `EndoInsights`
  declare `FlowIntensity`).
- **Fix:** Qualified the raw-value references as `EndoApp.FlowIntensity` (the app-side enum whose raw
  matches the persisted `flowRaw` vocabulary).
- **Files modified:** Tests/EndoAppTests/HealthImportMergeTests.swift
- **Commit:** 7eb41dd (corrected before the RED commit)

## TDD Gate Compliance

Task 2 followed RED → GREEN. RED commit `7eb41dd` (`test(09-02): add failing manual-wins ...`, confirmed
failing: "value of type 'SwiftDataRepository' has no member 'daySnapshotSources'"). GREEN commit `6af808d`
(`feat(09-02): manual-wins, per-field, nil-honest import merge ...`, 6 tests passing). No REFACTOR commit
needed — the merge logic landed clean. Task 1 was `type="auto"` (not TDD-gated) but its two suites were
written alongside the implementation and are GREEN.

## Known Stubs

None that block the plan goal. By design (plan scope), the real `HKHealthStore`-backed `HealthDataReading`
conformer, the permission/rationale UX, the entitlement + Info.plist string, the D-03 badge UI, and the
`-uitest-health-*` launch-arg fixture selection are DEFERRED to 09-03. This plan delivers the full
fixture-driven import→merge→recompute slice; those are documented downstream deferrals, not stubs. The
nutrition→food-presence merge intentionally contributes a `.other` FoodTag (presence-only, D-16) only when
no manual food exists — documented above, not a stub.

## Threat Surface Scan

No new security surface beyond the plan's `<threat_model>`. Mitigations landed as specified: T-09-06
(fixture never constructs `HKHealthStore`), T-09-07 (no-content logging — only swallowed error type),
T-09-08 (`consentGuard` in the service + `assertConsented()` on all imported CRUD), T-09-09 (corrupt
anchor → nil, round-trip + corruption tests), T-09-10 (`emptyImportLeavesFieldsNil` proves empty ≠ "no
symptoms"), T-09-11 (separate `ImportedSample` store, manual-wins single blend point, per-field source
flag — `manualWinsOverImported` + `sourceFlagged`). No package installs (T-09-SC).

## Self-Check: PASSED

All 6 created source/test files + the SUMMARY present on disk; all 3 task commits (0c909c8, 7eb41dd,
6af808d) present in git history.
