---
phase: 05-snapshot-contract-descriptive-trends-engine
plan: 01
subsystem: infra
tags: [swift, spm, xcodegen, swift-testing, foundation, value-types, sendable]

# Dependency graph
requires:
  - phase: 03-data-spine
    provides: FactorDay value-snapshot template + the 7 Domain enums (SymptomType/BodyRegion/FlowIntensity/FoodTag/ActivityType/ActivityIntensity/SleepQuality) whose raw values the package mirrors
  - phase: 02-symptoms-pain-bleeding
    provides: CycleSummary.Confidence enum (lifted into InsightConfidence) + the suppressed/no-cycle honesty posture
provides:
  - EndoInsights local SPM package (Foundation-only, zero deps) — the SC1 compiler boundary
  - project.yml wiring proving the package's Swift Testing target runs inside `xcodebuild test -scheme EndoApp` (SC3 mechanism)
  - DaySnapshot + SymptomPoint + PainPoint value contract (Sendable, Equatable, nil=unlogged honesty, hasNote presence-only, per-day suppressed)
  - Insight + InsightKind (reserved Ph6-12 cases) + TrendWindow + InsightConfidence + InsightPoint with a DETERMINISTIC Insight.id
  - 7 package-local snapshot enums (Option D) with raw values matching the app's verbatim + defensive init(raw:)
affects: [05-02-trends-engine, 05-03-repository-mapping, 06-cycle-phase, 07-correlations, 08-dashboard, 11-recommendations, 12-flare]

# Tech tracking
tech-stack:
  added: [EndoInsights local SPM package (first-party, no registry deps)]
  patterns:
    - "Foundation-only SPM package as a compiler-enforced architectural boundary (SC1)"
    - "Package test target wired into the app scheme via XcodeGen `scheme.testTargets: - package: Name/TestTarget`"
    - "Deterministic Insight.id via a pure-Foundation v5-style namespaced UUID derivation (engine-facing `Insight.derived(...)`, no UUID() reachable)"
    - "Option D enum sourcing — package-local enum copies with verbatim raw values for a passthrough boundary map"

key-files:
  created:
    - EndoInsights/Package.swift
    - EndoInsights/Sources/EndoInsights/SnapshotEnums.swift
    - EndoInsights/Sources/EndoInsights/DaySnapshot.swift
    - EndoInsights/Sources/EndoInsights/Insight.swift
    - EndoInsights/Tests/EndoInsightsTests/SmokeTests.swift
    - EndoInsights/Tests/EndoInsightsTests/ContractTests.swift
  modified:
    - project.yml

key-decisions:
  - "SPM scheme test-target path CONFIRMED — the framework-target fallback was NOT needed (the package @Test executes in xcodebuild test -scheme EndoApp)"
  - "Insight.id is derived deterministically from kind+window+signalKey (pure-Foundation FNV-1a → RFC-4122 v5 UUID); no UUID()-defaulting init is exposed to the engine (Pitfall 4)"
  - "Option D (package-local enums) per D-18 — 17 app files untouched; Plan 03 maps app-enum → package-enum at the daySnapshots() boundary (raw values match for a passthrough)"
  - "Added a D-12 boundary-lint @Test (packageSourcesImportOnlyFoundation) inside EndoInsightsTests as belt-and-suspenders on the compiler boundary"

patterns-established:
  - "Pattern: a real SPM package whose Package.swift declares zero deps IS the no-SwiftData/SwiftUI boundary; a #filePath-based import lint is the second proof"
  - "Pattern: engine-facing value factories derive stable ids so Equatable arrays can be asserted exactly in fixtures"

requirements-completed: [INST-01]

# Metrics
duration: ~22min
completed: 2026-06-08
---

# Phase 5 Plan 01: Snapshot Contract + EndoInsights Package Wiring Summary

**A Foundation-only EndoInsights SPM package whose Swift Testing target provably runs in the app scheme, carrying the Sendable/Equatable DaySnapshot + generic Insight value contract (deterministic ids, nil=unlogged honesty) every downstream insights phase builds on.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-06-08T21:23:00Z (approx)
- **Completed:** 2026-06-08T21:45:00Z (approx)
- **Tasks:** 2
- **Files modified/created:** 7 (6 created, 1 modified)

## Accomplishments

- **Structural de-risk PROVEN (Task 1):** the `EndoInsights` local SPM package is wired into `project.yml` (top-level `packages:`, app `dependencies: - package: EndoInsights`, scheme `- package: EndoInsights/EndoInsightsTests`), and its `@Test` **executes inside the single `xcodebuild test -scheme EndoApp` command** — confirmed by the log: `Suite "EndoInsights wiring smoke" passed` / `Test packageTestTargetRunsInScheme() passed`. The framework-target fallback was **not** needed.
- **The value contract (Task 2):** `DaySnapshot` (+ `SymptomPoint`/`PainPoint`), `Insight` (+ `InsightKind`/`TrendWindow`/`InsightConfidence`/`InsightPoint`), and the 7 package-local snapshot enums — all `public`, `Sendable`, `Equatable`, Foundation-only.
- **Deterministic `Insight.id`:** derived from `kind`+`window`+`signalKey` via a pure-Foundation namespaced derivation (no `UUID()` reachable by the engine), so `Insight` stays exactly `Equatable` for Plan 02 fixtures.
- **SC1 boundary holds:** no `import SwiftData/SwiftUI/UIKit/CoreData` anywhere under `EndoInsights/Sources/` (compiler + a `#filePath` boundary-lint `@Test`).
- **No regression:** full suite GREEN — app Swift Testing suite (87 tests / 18 suites) + 8 UI tests + the new EndoInsights suite (9 tests / 2 suites).

## Task Commits

1. **Task 1: Package-test-in-scheme SMOKE — create EndoInsights, wire project.yml, prove the @Test runs** — `b43ec25` (feat)
2. **Task 2: The value contract — DaySnapshot, Insight, package-local snapshot enums (Option D)** — `0eac34b` (feat)

_Task 2's commit also intentionally deletes the Task-1 placeholder (`_Placeholder.swift`) now that `Sources/EndoInsights` carries the real contract._

## Files Created/Modified

- `EndoInsights/Package.swift` — Foundation-only SPM manifest, zero deps (the SC1 compiler boundary), `platforms: [.iOS(.v18)]`
- `EndoInsights/Sources/EndoInsights/SnapshotEnums.swift` — 7 package-local enums (Option D) with verbatim raw values + defensive `init(raw:)`
- `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` — `DaySnapshot` + `SymptomPoint` + `PainPoint` (Sendable, Equatable, nil-honesty, `hasNote` presence-only, `suppressed`)
- `EndoInsights/Sources/EndoInsights/Insight.swift` — `Insight` + kind/window/confidence/point enums & structs + `DeterministicUUID` derivation + `Insight.derived(...)`
- `EndoInsights/Tests/EndoInsightsTests/SmokeTests.swift` — the distinctive wiring-smoke `@Test`
- `EndoInsights/Tests/EndoInsightsTests/ContractTests.swift` — deterministic-id, Equatable, nil-honesty, defensive-decode, D-12 boundary lint
- `project.yml` — `packages:` + app `dependencies:` + scheme package test-target

## Final contract shapes (for Plan 02 / Plan 03)

- `DaySnapshot(day, symptoms:[SymptomPoint], pain:[PainPoint], flow:FlowIntensity?, clots:Bool, foodTags:[FoodTag], activityType:ActivityType?, activityMinutes:Double?, activityIntensity:ActivityIntensity?, sleepHours:Double?, sleepQuality:SleepQuality?, medicationsTaken:[UUID], tookPRN:Bool, stress:Int?, mood:Int?, hasNote:Bool, suppressed:Bool)` — all `let`, explicit `public init`.
- `SymptomPoint(type:SymptomType, severity:Int?)`, `PainPoint(region:BodyRegion, severity:Int?)` — `severity == nil` ⇒ present-without-severity (≠ 0).
- `Insight(id:UUID, kind:InsightKind, headline:String, detail:String, window:TrendWindow, confidence:InsightConfidence, points:[InsightPoint])` — construct via `Insight.derived(kind:window:signalKey:headline:detail:confidence:points:)`.
- `InsightKind`: `.trend` (Ph5) + reserved `.cyclePhase/.correlation/.recommendation/.flare`. `TrendWindow`: `.last7/.last30/.last90/.allTime`. `InsightConfidence`: Int-backed `.none(0)/.low(1)/.medium(2)/.high(3)`, `Comparable`.

## Decisions Made

- **Package mechanism = real SPM package (not the framework fallback).** The SPM scheme test-target path was proven on this toolchain (XcodeGen 2.45.4, Xcode 26.5 simulator), so D-09's literal "real local SPM package" is honored. Evidence captured in `/tmp/e2e/phase5-smoke.log` and `/tmp/e2e/phase5-full.log`.
- **Deterministic id derivation = pure Foundation.** Used an FNV-1a two-lane fold over `"namespace|kind|window|signalKey"` stamped to RFC-4122 v5/variant bits rather than CryptoKit, to keep the dependency surface to Foundation only and avoid any import beyond the contract's needs. It is an identity derivation, not a security primitive, so a non-cryptographic hash is appropriate. Verified stable-for-same-inputs and distinct-for-different-inputs in `ContractTests`.
- **Option D enum sourcing (per D-18).** Package owns data-only copies (no `plainLabel`) with raw values matching the app verbatim. Plan 03's `daySnapshots()` maps app-enum → package-enum at the boundary; a Plan-03 parity test guards drift.

## Deviations from Plan

### Auto-added (within plan scope)

**1. [Rule 2 - Missing critical test coverage] Added `ContractTests.swift` (incl. the D-12 boundary lint)**
- **Found during:** Task 2 (the contract).
- **Issue:** Task 2's `done` requires the deterministic-id guarantee and the Foundation-only boundary to be real, but the plan's Task-2 file list named no test file beyond the Task-1 smoke. Shipping the contract without asserting determinism + the import boundary would leave Pitfall 4 and SC1 unproven in the suite.
- **Fix:** Added `ContractTests.swift` with 8 `@Test`s: deterministic-id stability/uniqueness/well-formedness, `Insight`/`DaySnapshot` Equatable, nil≠0 honesty, defensive enum decode, and the D-12 `#filePath` import-boundary lint (RESEARCH Form A).
- **Files modified:** `EndoInsights/Tests/EndoInsightsTests/ContractTests.swift`
- **Verification:** all 8 pass in the suite (`/tmp/e2e/phase5-contract.log`, `/tmp/e2e/phase5-full.log`).
- **Committed in:** `0eac34b` (Task 2 commit).

---

**Total deviations:** 1 auto-added (Rule 2 — missing critical test coverage).
**Impact on plan:** Strengthens the plan's own `done` criteria (deterministic id + SC1 boundary) with in-suite proof. No scope creep — no new production behavior beyond the named contract.

## Issues Encountered

- **Flaky UI test on the first full-suite run (NOT a regression).** `CheckInFlowUITests.testMedicationTaken_persistsAcrossReopen` failed once under full-suite load (a medication-toggle XCUITest tap/read timing race at `CheckInFlowUITests.swift:211`), then **passed in isolation on retry** and **passed on the subsequent full-suite run**. This plan touches no app logic (only a new package + scheme wiring), so it cannot be the cause. Logged to `deferred-items.md` as pre-existing UI-test stability, out of scope.

## User Setup Required

None — no external service configuration. `EndoInsights` is a first-party local SPM package with zero registry dependencies (no Package Legitimacy Audit needed, per the threat register T-05-03/T-05-SC).

## Next Phase Readiness

- **Plan 02 (TrendsEngine):** the `[DaySnapshot] → [Insight]` contract is stable and deterministic; build the pure engine via `Insight.derived(...)` and assert exact `[Insight]` arrays with a fixed UTC calendar + injected `today`.
- **Plan 03 (Repository.daySnapshots() mapping):** map app-enum → package-enum (raw values match for a passthrough) and `EndoInsights.PainPoint` (fully-qualified) vs `SchemaV1.PainPoint`; add the enum-parity round-trip test to guard the Option-D drift risk.
- No blockers.

## Self-Check: PASSED

All 6 created files + `project.yml` + the SUMMARY exist on disk; the Task-1 placeholder is removed; both task commits (`b43ec25`, `0eac34b`) are in the git log.

---
*Phase: 05-snapshot-contract-descriptive-trends-engine*
*Completed: 2026-06-08*
