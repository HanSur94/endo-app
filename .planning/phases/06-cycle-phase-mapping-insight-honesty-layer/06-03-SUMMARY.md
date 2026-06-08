---
phase: 06-cycle-phase-mapping-insight-honesty-layer
plan: 03
subsystem: database
tags: [swiftdata, cloudkit, migration, schema-versioning, dismissal, persistence]

# Dependency graph
requires:
  - phase: 03-cycle-phase-mapping-insight-honesty-layer (Plan 03-01)
    provides: "Additive SchemaV3 + lightweight v2toV3 migration recipe + FactorReading CloudKit-safe @Model template"
  - phase: 04 (CloudKit activation)
    provides: "Private-CloudKit entitlement shipping in target → the P-2 force-`.none` discipline on every local-only store"
  - phase: 06 (Plan 06-01)
    provides: "Insight.signalKey — the value-independent dismissal key DismissedInsight persists"
provides:
  - "CloudKit-safe DismissedInsight @Model (signalKey + dismissedAt only — no health prose)"
  - "Additive SchemaV4 + lightweight V3→V4 migration; ModelContainer bumped to SchemaV4"
  - "Consent-gated Repository.dismissInsight(signalKey:) (append-only) + dismissedSignalKeys() -> Set<String> (dedupe-on-read)"
  - "SchemaMigrationTests V3→V4 survival case + RepositoryDismissalTests round-trip/dedupe proofs"
affects: [phase-08-insights-integration, dismissal-ui, insights-engine]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Additive SchemaV(N+1) + MigrationStage.lightweight (never .custom) — the proven CloudKit-bound migration recipe, now at V4"
    - "Append-only write + dedupe-on-read via Set — cross-device duplicates collapse harmlessly without write-side mutation"

key-files:
  created:
    - Sources/EndoApp/Persistence/Models/DismissedInsight.swift
    - Sources/EndoApp/Persistence/SchemaV4.swift
    - Tests/EndoAppTests/RepositoryDismissalTests.swift
  modified:
    - Sources/EndoApp/Persistence/MigrationPlan.swift
    - Sources/EndoApp/Persistence/ModelContainer+Config.swift
    - Sources/EndoApp/Persistence/Repository.swift
    - Tests/EndoAppTests/SchemaMigrationTests.swift

key-decisions:
  - "DismissedInsight carries ONLY signalKey + dismissedAt (no prose/severities) — the T-06-09 privacy boundary; mirrors FactorReading exactly (app-gen UUID not .unique, all optional/defaulted, no relationships)"
  - "Dismissal is APPEND-ONLY on write; dedupe happens on READ via Set (the CloudKit prefer-append-only-events rule, D-10/D-12) — never mutate/delete on write"
  - "V3→V4 is a lightweight additive stage (one new model) — NEVER .custom (its hooks don't fire under CloudKit, Phase 4)"

patterns-established:
  - "Schema-version bump checklist: new @Model under extension SchemaV1 → SchemaV(N+1) (V(N) set + 1) → MigrationPlan schemas+stages+v(N)toV(N+1) → ModelContainer bump → clone the prior migration-survival test +1 version"
  - "Consent-gated CRUD pair: assertConsented() first, then context.insert/save (write) or Set(context.fetch(...).compactMap(\\.key)) (dedupe read)"

requirements-completed: [INST-04]

# Metrics
duration: ~22min
completed: 2026-06-08
---

# Phase 6 Plan 03: Cycle-Phase-Mapping Insight-Honesty Layer — Dismissal Persistence Summary

**CloudKit-safe DismissedInsight @Model + additive SchemaV4/lightweight V3→V4 migration + consent-gated append-only dismissInsight/dismissedSignalKeys CRUD, proven by a V3→V4 survival test and a round-trip/Set-dedupe test.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-06-08T21:14:00Z
- **Completed:** 2026-06-08T21:37:00Z
- **Tasks:** 3
- **Files modified:** 7 (3 created, 4 modified)

## Accomplishments
- `DismissedInsight` CloudKit-safe `@Model` under `extension SchemaV1` — `id: UUID` (app-gen, NOT `.unique`), `signalKey: String?`, `dismissedAt: Date`, all optional/defaulted, no relationships, no health prose (T-06-09 privacy boundary documented in the header).
- Additive `SchemaV4` (the V3 set + `DismissedInsight`, the only delta); lightweight `v3toV4` migration stage wired into `EndoMigrationPlan`; `makeModelContainer` bumped to `SchemaV4`.
- Consent-gated `Repository.dismissInsight(signalKey:)` (append-only `context.insert`) + `dismissedSignalKeys() -> Set<String>` (dedupe-on-read via `Set`), declared on the protocol and implemented on `SwiftDataRepository`.
- `SchemaMigrationTests.v3Store_survivesLightweightMigrationToV4_withNoDataLoss` (every V3 row across all 7 models survives; `DismissedInsight` queryable/empty) + `RepositoryDismissalTests` (round-trip, twice-dismiss Set-dedupe, distinct keys). Existing V1→V2 / V2→V3 cases untouched and green.

## Task Commits

Each task was committed atomically:

1. **Task 1: DismissedInsight @Model + SchemaV4 + v3toV4 migration + container bump** — `deb1a79` (feat)
2. **Task 2: Repository dismissal CRUD (consent-gated, append-only)** — `50363fc` (feat)
3. **Task 3: V3→V4 migration survival case + RepositoryDismissalTests** — `3f7c3d6` (test)

_Note: Tasks 1 and 2 are scaffolding/wiring whose behavioral proof lands in Task 3's tests (RED→GREEN at the plan level: the `test(...)` commit follows the two `feat(...)` commits and all assertions pass)._

## Files Created/Modified
- `Sources/EndoApp/Persistence/Models/DismissedInsight.swift` (created) — CloudKit-safe `@Model` mirroring `FactorReading`; signalKey + dismissedAt only.
- `Sources/EndoApp/Persistence/SchemaV4.swift` (created) — additive VersionedSchema (V3 set + DismissedInsight).
- `Sources/EndoApp/Persistence/MigrationPlan.swift` (modified) — `SchemaV4.self` in schemas; `v3toV4` lightweight stage in stages.
- `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (modified) — `Schema(versionedSchema: SchemaV4.self)` + doc-comment bump.
- `Sources/EndoApp/Persistence/Repository.swift` (modified) — `dismissInsight(signalKey:)` + `dismissedSignalKeys()` on protocol + impl.
- `Tests/EndoAppTests/SchemaMigrationTests.swift` (modified) — V3→V4 survival case cloned from V2→V3.
- `Tests/EndoAppTests/RepositoryDismissalTests.swift` (created) — round-trip, Set-dedupe, distinct-keys cases.

## Decisions Made
None beyond the plan — executed exactly as specified. The plan's locked decisions (signalKey-only payload for T-06-09, append-only/dedupe-on-read, lightweight-never-custom migration) were followed verbatim.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- **Flaky pre-existing UI test (out of scope).** The full-suite regression run surfaced one intermittent failure: `CheckInFlowUITests.testMedicationTaken_persistsAcrossReopen` (a medication-toggle read-back timing race, LIFE-03). It passes on isolated re-run. This plan touches no UI / no medication / no check-in code — purely additive persistence — so it is not a regression. Logged to `.planning/phases/06-cycle-phase-mapping-insight-honesty-layer/deferred-items.md` per the scope boundary and NOT fixed. The same flake was already documented under Plans 05-01 and 05-02.

## Verification
- `xcodebuild test ... -only-testing:EndoAppTests/SchemaMigrationTests -only-testing:EndoAppTests/RepositoryDismissalTests` → **6/6 GREEN** (V1→V2, V2→V3, V3→V4 survival + 3 dismissal cases).
- Full suite: **115 app unit tests + 28 EndoInsights package tests GREEN**; ConsentFlow/LoggingFlow UI suites green; only the known med-toggle UI flake (passes in isolation).
- App builds clean for the Simulator destination after each task.

## Known Stubs
None — every artifact is fully wired and behaviorally proven. The live dismiss-button UI and wiring `dismissedSignalKeys()` into `InsightsEngine` are explicitly Phase 8 (out of this plan's scope, per the objective).

## Next Phase Readiness
- INST-04 dismissal **persistence** is complete and proven; Phase 8 can consume `Repository.dismissedSignalKeys()` (a deduped `Set<String>`) in the `InsightsEngine` and add the dismiss-button UI.
- Schema is at V4; the V(N+1) additive-migration checklist (pattern above) is established for any future model.
- No blockers introduced.

## Self-Check: PASSED

- All created files exist on disk (DismissedInsight.swift, SchemaV4.swift, RepositoryDismissalTests.swift, 06-03-SUMMARY.md).
- All task commits present in git history (deb1a79, 50363fc, 3f7c3d6).

---
*Phase: 06-cycle-phase-mapping-insight-honesty-layer*
*Completed: 2026-06-08*
