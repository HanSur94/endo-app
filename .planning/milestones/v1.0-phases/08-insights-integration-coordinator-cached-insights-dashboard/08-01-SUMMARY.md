---
phase: 08-insights-integration-coordinator-cached-insights-dashboard
plan: 01
subsystem: database
tags: [swiftdata, codable, cloudkit, schema-migration, swift6, serialization]

# Dependency graph
requires:
  - phase: 05-insights-engine-keystone
    provides: "EndoInsights.Insight value contract (Sendable/Equatable/Identifiable) + DeterministicUUID id derivation"
  - phase: 06-insight-honesty-dismissal
    provides: "CloudKit-safe @Model + additive-migration pattern (DismissedInsight/SchemaV4/v3toV4) + consent-gated Repository CRUD seam"
provides:
  - "Insight: Codable (additive, package) — id-preserving JSON round-trip"
  - "CachedInsight @Model (CloudKit-safe: flat signalKey/kindRaw/windowRaw + payload Data? blob + computedAt + fingerprint)"
  - "SchemaV5 (additive) + lightweight v4toV5 migration + ModelContainer bumped to SchemaV5"
  - "Repository.loadCachedInsights() / replaceCachedInsights(_:computedAt:fingerprint:) — consent-gated CRUD"
affects: [08-02-coordinator, 08-03-card-chart, 08-04-dashboard]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Additive Codable on a package value type (free synthesis via raw-backed enums + value-type members; NO custom init(from:) so the stored derived id survives decode)"
    - "CachedInsight = flat queryable identity columns + a Codable Data blob carrying the whole Insight (one column round-trips points/confidence/sampleSize/notice)"
    - "Replace-all cache CRUD (delete-then-insert per recompute) so a stale/dismissed insight can never linger"

key-files:
  created:
    - "Sources/EndoApp/Persistence/Models/CachedInsight.swift"
    - "Sources/EndoApp/Persistence/SchemaV5.swift"
    - "EndoInsights/Tests/EndoInsightsTests/InsightCodableTests.swift"
    - "Tests/EndoAppTests/CachedInsightRoundTripTests.swift"
  modified:
    - "EndoInsights/Sources/EndoInsights/Insight.swift"
    - "Sources/EndoApp/Persistence/MigrationPlan.swift"
    - "Sources/EndoApp/Persistence/ModelContainer+Config.swift"
    - "Sources/EndoApp/Persistence/Repository.swift"
    - "Tests/EndoAppTests/SchemaMigrationTests.swift"

key-decisions:
  - "Insight Codable is purely additive with NO custom init(from:) — synthesized decode reads the STORED derived id, so a round-trip preserves it exactly (re-deriving via DeterministicUUID is the forbidden anti-pattern)"
  - "CachedInsight stores the whole Insight as a single Codable Data blob alongside flat identity columns — not 11 flat columns, not one array-blob — keeping the @Model tiny + CloudKit-safe + per-insight addressable"
  - "v4toV5 is .lightweight ONLY (never .custom — custom hooks silently never fire under CloudKit); the cache is rebuilt by recompute so no backfill migration is ever needed"
  - "Both CRUD methods call assertConsented() first, mirroring every sibling read/write; loadCachedInsights compactMap-skips a corrupt blob rather than crashing"

patterns-established:
  - "A1 V(N+1) checklist applied again: new @Model under extension SchemaV1 → SchemaV(N+1) → MigrationPlan schemas+stages+v(N)toV(N+1) → ModelContainer bump → clone the prior survival test +1 version (force cloudKitDatabase:.none on the on-disk seed)"
  - "Package value-type Codable: keep Equatable/Sendable, add Codable, assert decode(encode(x)) == x AND decoded.id == x.id"

requirements-completed: [INST-01, INST-02, INST-03, INST-04]

# Metrics
duration: ~22min
completed: 2026-06-09
---

# Phase 8 Plan 01: Cached-Insights Persistence Foundation Summary

**Made `EndoInsights.Insight` id-preserving Codable, added a CloudKit-safe `CachedInsight` @Model in an additive `SchemaV5` with a lightweight V4→V5 migration, and gave the Repository consent-gated replace-all CRUD that round-trips `[Insight]` through the store.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-06-09T11:28:00Z (approx)
- **Completed:** 2026-06-09T11:50:00Z (approx)
- **Tasks:** 3 (all TDD)
- **Files modified:** 9 (2 created models/schema, 2 created tests, 1 package value, 3 persistence config, 1 test extended)

## Accomplishments
- `Insight` (+ `InsightKind`/`TrendWindow`/`InsightConfidence`/`InsightPoint`) now conform to `Codable`, additively — `decode(encode(insight)) == insight` and the stored derived `id` is preserved on decode (never regenerated).
- New CloudKit-safe `CachedInsight` `@Model` (all-optional/defaulted, no `.unique`, no relationships, app-generated UUID, nested under `extension SchemaV1`) with flat `signalKey`/`kindRaw`/`windowRaw` identity columns + a `Codable` `payload` blob + `computedAt`/`fingerprint`.
- Additive `SchemaV5` + `v4toV5` `.lightweight` migration stage + `ModelContainer` bumped to `SchemaV5`; a V4 on-disk store survives the reopen at V5 with zero data loss and `CachedInsight` is queryable.
- Consent-gated `Repository.loadCachedInsights()` / `replaceCachedInsights(_:computedAt:fingerprint:)` — replace-all, corrupt-blob-tolerant; a save→load round-trips `[Insight]` equal.

## Task Commits

Each task was committed atomically (TDD: test → feat per task):

1. **Task 1: Make Insight Codable** — `1cf611d` (test) → `9a2ace5` (feat)
2. **Task 2: CachedInsight @Model + SchemaV5 + V4→V5 migration + container bump** — `3202762` (test) → `8fd619d` (feat)
3. **Task 3: Repository CachedInsight CRUD** — `8575704` (test) → `a555afd` (feat)

_No REFACTOR commits were needed — GREEN implementations were already minimal/clean._

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/Insight.swift` — added `Codable` to all five public types (additive).
- `EndoInsights/Tests/EndoInsightsTests/InsightCodableTests.swift` — round-trip + id-preservation + per-member tests.
- `Sources/EndoApp/Persistence/Models/CachedInsight.swift` — CloudKit-safe cache `@Model`.
- `Sources/EndoApp/Persistence/SchemaV5.swift` — additive V5 versioned schema.
- `Sources/EndoApp/Persistence/MigrationPlan.swift` — added `SchemaV5` + `v4toV5` lightweight stage.
- `Sources/EndoApp/Persistence/ModelContainer+Config.swift` — bumped `Schema(versionedSchema: SchemaV5.self)`.
- `Sources/EndoApp/Persistence/Repository.swift` — added consent-gated `loadCachedInsights`/`replaceCachedInsights`.
- `Tests/EndoAppTests/SchemaMigrationTests.swift` — added `v4Store_survivesLightweightMigrationToV5_withNoDataLoss`.
- `Tests/EndoAppTests/CachedInsightRoundTripTests.swift` — replace→load round-trip, replace-all, empty-replace, corrupt-blob-skip.

## Decisions Made
- **No custom `Codable.init(from:)`** — synthesized decode reads the stored derived `id` from the blob, which is exactly correct; a custom initializer re-deriving via `DeterministicUUID` would be the forbidden anti-pattern.
- **Single Codable blob per insight + flat identity columns** (per RESEARCH Pattern 3 / D-18) — keeps the model CloudKit-safe and the whole value (incl. `points`) round-trips in one `Data` column.
- **`.lightweight` migration only** — additive (one new model) ⇒ lightweight is correct and sufficient; `.custom` hooks silently never fire under CloudKit (Phase 4 live).

## Deviations from Plan

### Behavior adjustment (not auto-fixed code — test posture)

**1. Consent-gate behavior tested structurally, not as a catchable throw**
- **Found during:** Task 3 (Repository CRUD).
- **Issue:** The plan's behavior spec says the methods "throw/refuse … mirror the sibling `assertConsented()` behavior — assert the same error the other reads throw." In this codebase `assertConsented()` is a debug-only `assert()` (a death-test), NOT a thrown error — no sibling read throws on consent withdrawal, and `RepositoryDismissalTests` likewise does not test the consent crash.
- **Resolution:** Mirrored the siblings EXACTLY (both new methods open with `assertConsented()`), and did not write a catchable-throw consent test (none is possible without a death-test harness, and inventing a thrown error would diverge from every existing Repository method). The gate is proven structurally. The other three behaviors (round-trip identity, replace-all, corrupt-blob skip) are fully covered.
- **Files modified:** `Sources/EndoApp/Persistence/Repository.swift`, `Tests/EndoAppTests/CachedInsightRoundTripTests.swift`.

---

**Total deviations:** 1 (test-posture alignment to the existing consent-gate convention).
**Impact on plan:** No scope creep, no code behavior change vs the plan's CRUD contract — only the consent assertion is expressed via the codebase's established `assert()` convention rather than a (non-existent) thrown error.

## Issues Encountered

- **Pre-existing out-of-scope UI-test failure (NOT fixed — SCOPE BOUNDARY):** `CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen` fails (`Optional("0") != Optional("1")`) — a Phase 3 (LIFE-03) medication-toggle UI-interaction test whose own code comments "a SwiftUI Toggle in a Form can swallow a centre tap." `git diff --name-only` confirms this plan touched NO UI / check-in / medication / view code (only the Insight value, persistence schema/migration/repo, and tests). Logged to `08-…/deferred-items.md`. `EndoAppTests` (116/116) and `EndoInsights` (96/96) are fully green; this is the only red and it is a UI-automation interaction issue, not a data-layer regression.

## Verification

- `cd EndoInsights && swift test --filter InsightCodableTests` — 6/6 green; full package suite 96/96 green.
- `xcodebuild test … -only-testing:EndoAppTests/SchemaMigrationTests` — 4/4 green (incl. the new V4→V5 survival case).
- `xcodebuild test … -only-testing:EndoAppTests` — 116/116 green (incl. the new `CachedInsightRoundTripTests` suite, 20 suites total).
- Phase-gate full run: `EndoAppTests` 116/116 + `EndoInsights` 96/96 green; the only failure is the unrelated pre-existing UI flake above.

## Known Stubs

None — this plan delivers fully-wired persistence (no placeholder/empty-value stubs; the cache is read/written through real Repository CRUD).

## User Setup Required

None — no new external service configuration. (The Phase-4 CloudKit `CachedInsight` record-type Dev→Prod deployment is a release-time step already tracked in the phase's runtime-state inventory and the existing Phase-4 deferred items; not a dev blocker.)

## Next Phase Readiness
- `Insight: Codable` and the `CachedInsight`/`SchemaV5` store + Repository CRUD are the contract+storage the Wave-2 off-main `InsightsCoordinator` (08-02) and the Wave-3/4 dashboard (08-03/08-04) depend on. Nothing here renders UI.
- The `InsightFingerprint`, coordinator debounce/cache logic, and the dashboard views remain for downstream plans.

## Self-Check: PASSED

All created files exist on disk; all six task commit hashes (`1cf611d`, `9a2ace5`, `3202762`, `8fd619d`, `8575704`, `a555afd`) are present in git history.

---
*Phase: 08-insights-integration-coordinator-cached-insights-dashboard*
*Completed: 2026-06-09*
