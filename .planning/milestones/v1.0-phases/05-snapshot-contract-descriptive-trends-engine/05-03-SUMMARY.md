---
phase: 05-snapshot-contract-descriptive-trends-engine
plan: 03
subsystem: persistence
tags: [swift, swiftdata, swift-testing, endoinsights, value-mapping, option-d, consent-gate]

# Dependency graph
requires:
  - phase: 05-snapshot-contract-descriptive-trends-engine
    plan: 01
    provides: EndoInsights.DaySnapshot value contract + 7 package-local snapshot enums (Option D, verbatim raw values)
  - phase: 03-data-spine
    provides: FactorReading projection (makeFactorDay) + the 7 app domain enums + factorDays()/bleedingDays()/isSuppressed() read pattern
provides:
  - "Repository.daySnapshots() -> [EndoInsights.DaySnapshot] — the live, consent-gated, app-side @Model → value-contract seam (the ONLY such projection, SC1)"
  - "App-enum → package-enum same-raw-value passthrough map + a 7-enum parity test suite guarding Option-D drift"
  - "Shared projectFactors() helper mirroring makeFactorDay's FactorKind switch, emitting package enums"
affects: [08-dashboard, 06-cycle-phase, 07-correlations, 11-recommendations, 12-flare]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Repository as the SOLE importer of both SwiftData and EndoInsights — the one-directional store→value seam (the package never imports SwiftData)"
    - "Option-D enum parity test: Set(app.allCases.rawValue) == Set(package.allCases.rawValue) for all 7 enums, both directions, so a case added on one side fails the suite"
    - "Cross-module name-clash resolved by fully-qualifying EndoApp.* / EndoInsights.* at every ambiguous reference (both modules define SymptomType/BodyRegion/FlowIntensity/FoodTag/ActivityType/ActivityIntensity/SleepQuality)"

key-files:
  created:
    - Tests/EndoAppTests/DaySnapshotMappingTests.swift
  modified:
    - Sources/EndoApp/Persistence/Repository.swift

key-decisions:
  - "Factor projection EXTRACTED into a shared projectFactors() helper (not duplicated) — it mirrors makeFactorDay's FactorKind switch exactly but emits package enums, so app FactorDay and package DaySnapshot projections cannot silently drift"
  - "suppressed resolved as a single current value (latest TreatmentState via isSuppressed()) stamped onto every snapshot — matches CycleMath's single-value usage; the per-day DaySnapshot.suppressed field preserves the D-03 per-day shape"
  - "The note-bearing .food FactorReading row is SKIPPED in projectFactors() (continue) — free-text food prose never crosses into the value contract (D-01 privacy / T-05-08); only foodTags + hasNote presence cross the seam"

requirements-completed: [INST-01]

# Metrics
duration: ~14min
completed: 2026-06-08
---

# Phase 5 Plan 03: App-side Repository.daySnapshots() Store→DaySnapshot Mapping Summary

**`Repository.daySnapshots() -> [EndoInsights.DaySnapshot]` — the live, consent-gated, app-side seam that maps every day-scoped SwiftData `@Model` row into the package value contract (the ONLY such projection, keeping SwiftData out of the package), with a 7-enum parity test slamming the Option-D drift trap shut.**

## Performance

- **Duration:** ~14 min
- **Tasks:** 2 (TDD RED → GREEN)
- **Files modified/created:** 2 (1 created, 1 modified)

## Accomplishments

- **The mapping seam (Task 2 / GREEN):** `daySnapshots()` is on the `Repository` protocol and implemented on `SwiftDataRepository`, mirroring `factorDays()`: `assertConsented()` first, fetch each day-scoped `@Model` type, bucket by `Calendar.startOfDay`, build the union of day keys ascending, and project each day → `EndoInsights.DaySnapshot`. It is the sole `@Model → DaySnapshot` projection (SC1) — the engine never touches the store.
- **Honesty preserved through the projection (D-03):** an unlogged stress/mood reads back `nil`; a logged `0` reads back `0`; a logged-present symptom with `nil` severity stays `nil` (never coerced to 0); a day with no flow-bearing bleeding event reads back `flow == nil`; `nil` stored clots coalesces to `false`.
- **Option-D drift trap closed (Task 1):** for all 7 app domain enums, the parity tests assert `Set(app raw values) == Set(package raw values)` (both directions) — a future case added on one side without the other now fails the suite. The boundary map is a verified same-raw-value passthrough.
- **Defensive decode throughout (T-05-09):** every enum decode uses the package `init(raw:)`/`init?(raw:)` path (unknown symptom/food → `.other`; unknown region/flow/sleep/activity-intensity → dropped or nil); no force-unwrap of a decoded raw. An unknown-region pain point is dropped (not crashed); an unknown food/symptom raw degrades to `.other`.
- **Privacy boundary held (T-05-08):** the note-bearing `.food` row is skipped and `hasNote` carries note PRESENCE only — no free-text prose crosses into the value contract.
- **Architecture integrity (T-05-10):** `Repository.swift` now imports `EndoInsights` (sole importer of both SwiftData and EndoInsights); the package still imports no SwiftData/SwiftUI (verified — the only matches are inside comments).
- **No regression:** full suite GREEN — 109 app tests / 19 suites + 22 tests / 5 suites (UI + EndoInsights package), all passing in the single `xcodebuild test -scheme EndoApp` run.

## Task Commits

1. **Task 1: RED mapping-honesty + enum-parity tests** — `cc4f128` (test)
2. **Task 2: Implement Repository.daySnapshots() (GREEN) + test ordering fix** — `ce378b7` (feat)

## Files Created/Modified

- `Tests/EndoAppTests/DaySnapshotMappingTests.swift` (created) — 22 `@Test`s: bucketing (collapse + sort), symptom/pain/bleeding/factor mapping, absence≠0 (stress/mood/severity), nil-clots→false, no-bleeding→flow nil, hasNote presence-only, suppressed propagation, defensive decode, and 7-enum Option-D parity. In-memory `ModelContainer` only (no CloudKit/network/UI).
- `Sources/EndoApp/Persistence/Repository.swift` (modified) — `import EndoInsights`; `daySnapshots()` added to the `Repository` protocol + `SwiftDataRepository`; private `makeDaySnapshot(...)` projection; shared `projectFactors(...)` helper (mirrors `makeFactorDay`'s switch, emits package enums); `intensity(packageFromLevel:)` level→package-enum mapping.

## Decisions Made (per the plan's output spec)

- **Factor projection EXTRACTED, not duplicated.** `projectFactors()` mirrors `makeFactorDay`'s `FactorKind` switch case-for-case but emits the EndoInsights package enums. Keeping it as one shared helper means the app `FactorDay` projection and the package `DaySnapshot` projection share the same decode logic and cannot drift independently.
- **`suppressed` resolved as a single current value.** `isSuppressed()` (latest `TreatmentState` by `startedAt`) is read once and stamped onto every snapshot — matching CycleMath's single-value usage. The per-day `DaySnapshot.suppressed` field still preserves the D-03 per-day shape for the engine.
- **Defensive-decode edge encountered: the cross-module name clash.** Both `EndoApp` and `EndoInsights` define `SymptomType`/`BodyRegion`/`FlowIntensity`/`FoodTag`/`ActivityType`/`ActivityIntensity`/`SleepQuality`. In the mapping, `EndoInsights.PainPoint` is fully-qualified against the `SchemaV1.PainPoint` `@Model`; in the test, every standalone app-enum reference (raw-value reads, parity `allCases`) is qualified `EndoApp.*` and every package reference `EndoInsights.*` to resolve ambiguity. Leading-dot argument syntax (e.g. `setFood(tags: [.dairy])`) resolves unambiguously from the setter's app-enum parameter type and needs no qualification.
- **Enum-parity test guards Option D against drift — confirmed.** All 7 parity `@Test`s pass; they fail loudly if either side adds/removes/renames a raw value.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Food-tag ordering assertion was over-specified**
- **Found during:** Task 2 (first GREEN run).
- **Issue:** `factorFields_mapToPackageEnums` asserted `snap.foodTags == [.dairy, .caffeine]` (array equality), but `setFood` stores one `FactorReading` row per tag and the bulk fetch returns them in unguaranteed order — the projection produced `[.caffeine, .dairy]`. Tag order within a day is not a contract guarantee.
- **Fix:** Compare as a set: `Set(snap.foodTags) == [.dairy, .caffeine]` (order-independent, the honest assertion). No production change — the mapping is correct; the test assumption was wrong.
- **Files modified:** `Tests/EndoAppTests/DaySnapshotMappingTests.swift`
- **Commit:** `ce378b7` (folded into the Task-2 GREEN commit).

---

**Total deviations:** 1 auto-fixed (Rule 1 — test ordering assumption). No production-behavior deviations.
**Impact on plan:** None — the mapping implementation matches the Task-1 spec exactly; only an over-specified test assertion was corrected.

## Issues Encountered

None beyond the food-tag ordering fix above. RED was confirmed cleanly (only the missing `daySnapshots()` member + its cascade), GREEN passed on the second run after the set-comparison fix, full suite green with no Phase 1–4 regression.

## User Setup Required

None — no external service configuration, no package-manager installs (T-05-SC: accept, none in this plan).

## Next Phase Readiness

- **Phase 8 (Dashboard):** the live data path is complete — `Repository.daySnapshots()` feeds `[DaySnapshot]` into `TrendsEngine.trends(from:asOf:)` (Plan 02). The mapping is honest, consent-gated, and Calendar-bucketed against a proven in-memory store.
- The Option-D boundary is drift-guarded; any future enum case must be added on both sides or the parity suite fails.
- No blockers.

## Self-Check: PASSED

- `Tests/EndoAppTests/DaySnapshotMappingTests.swift` exists on disk.
- `Sources/EndoApp/Persistence/Repository.swift` contains `func daySnapshots` and `import EndoInsights`.
- Both task commits (`cc4f128`, `ce378b7`) are in the git log.
- Full suite GREEN (109 + 22 tests); package boundary intact (no real SwiftData/SwiftUI import under `EndoInsights/Sources/`).

---
*Phase: 05-snapshot-contract-descriptive-trends-engine*
*Completed: 2026-06-08*
