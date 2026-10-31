---
phase: 24-pdf-report
plan: 01
subsystem: data + domain (report inputs)
status: complete
tags: [provenance, cycle, honesty, consent-gate, port]
requires:
  - Phase 18 EndoRepository consent gate + ImportedSignals fold + SnapshotProjector
  - :insights CyclePhaseMapper (run-segmentation / median / spread arithmetic)
  - :insights InsightConfidence, FlowIntensity, ActivityType
provides:
  - com.endoapp.app.data.ValueSource / DaySnapshotSource
  - EndoRepository.daySnapshotSources(epochDay) + (epochDays) — consent-gated, store-derived provenance
  - com.endoapp.app.domain.CycleMath.summarize + CycleSummary (three honest cycle states)
affects:
  - Plan 24-02 ReportModel (per-section SOURCE labels + no-phantom-cycle section)
  - Plan 24-05 report caller (batch provenance over a range)
tech-stack:
  added: []
  patterns:
    - Store-derived provenance (row presence), never recomputed from merged values
    - Reuse of :insights CyclePhaseMapper arithmetic (no second cycle algorithm)
    - Injected `today` (deterministic) + LocalDate-only date math (DST-safe, epoch-clean)
key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/data/ValueSource.kt
    - android/app/src/main/kotlin/com/endoapp/app/domain/CycleMath.kt
    - android/app/src/main/kotlin/com/endoapp/app/domain/CycleSummary.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/DaySnapshotSourcesTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/domain/CycleMathTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt
decisions:
  - Reused :insights InsightConfidence as CycleSummary.confidence (NONE/LOW/MEDIUM/HIGH), capped at MEDIUM
  - Ported iOS confidenceLevel (which can return HIGH) then coerced HIGH → MEDIUM per D-02 / plan
  - Kept iOS derive-even-for-wide-spread behavior (LOW confidence) rather than gating irregular → notEnoughData
  - Omitted the iOS CycleSummary.phase field (not consumed by the report cycle section)
metrics:
  duration: ~15m
  completed: 2026-07-10
  tasks: 2
  files: 7
  commits: 2
---

# Phase 24 Plan 01: Report Store/Domain Inputs Summary

Consent-gated store-derived per-day provenance read (`DaySnapshotSource`, the A3 gap) plus a faithful Kotlin port of the iOS `CycleMath.summarize`/`CycleSummary` three-state cycle engine — the two report inputs (per-section SOURCE labels and the no-phantom-cycle section) that had no Android home.

## What Was Built

### Task 1 — provenance read (A3 gap)
- `data/ValueSource.kt`: `enum ValueSource { MANUAL, HEALTH_CONNECT }` (iOS `.appleHealth` → `HEALTH_CONNECT`) + `DaySnapshotSource(flow, sleep, activity, food)` — all fields nullable so a denied/empty day fabricates no badge.
- `EndoRepository.daySnapshotSources(epochDay)` + a batch `(epochDays): Map<Long, DaySnapshotSource>` convenience for the Plan-05 range caller. Both `checkConsented()` first. Provenance is **store-derived** from row presence (manual `FactorReading`/`BleedingEvent` vs `ImportedSample` via `ImportedSignals.from`), never recomputed from merged values. Manual always wins (private `source(manual, imported)`). Imported activity claims the badge **only** on a typed workout with no manual activity (`importedActivityWins`, WR-01) — minutes-only imports never claim it.

### Task 2 — cycle engine
- `domain/CycleSummary.kt`: Equatable data class with the three honest states (`suppressed`, `notEnoughData`, derived); `nextPeriod` is a `ClosedRange<LocalDate>` (never a single date). Reuses `:insights` `InsightConfidence`.
- `domain/CycleMath.summarize(bleedingDays, isSuppressed, today)`: mirrors the iOS gate order exactly (suppressed short-circuit → runs → start-to-start gaps → `<2`-cycle notEnoughData → median/spread → `cycleDay` + next-period range). Reuses `CyclePhaseMapper.periodRuns/consecutiveGaps/medianInt/spread` verbatim (no second algorithm). Confidence ports the iOS `confidenceLevel` then caps HIGH → MEDIUM (D-02). All date math via `LocalDate.plusDays` / `ChronoUnit.DAYS.between` — DST-safe, epoch-clean.

## REAL Test Results

All run against the real Android SDK / Gradle (Robolectric + in-memory Room for the store tests, pure JVM for the cycle tests).

| Suite | Command | tests / failures |
|-------|---------|------------------|
| DaySnapshotSourcesTest | `:app:testDebugUnitTest --tests "*DaySnapshotSourcesTest"` | 10 / 0 |
| RepositoryConsentTest | `--tests "*RepositoryConsentTest"` | 3 / 0 |
| CycleMathTest | `--tests "*CycleMathTest"` | 6 / 0 |
| `:insights:test` (regression) | `:insights:test` | BUILD SUCCESSFUL (no regression) |
| `:app:assembleDebug` | `:app:assembleDebug` | BUILD SUCCESSFUL |

Provenance proofs (store-derived + manual-wins): manual-sleep-only → `sleep == MANUAL`, others null; imported-sleep-only → `HEALTH_CONNECT`; manual+imported sleep → `MANUAL` (manual wins); imported activity minutes without a type → `activity == null` (minutes alone never claim the badge); imported typed workout → `HEALTH_CONNECT`; manual activity beats a typed imported workout; imported nutrition → food `HEALTH_CONNECT`; empty day → `DaySnapshotSource()` all-null; batch keys by epochDay. Consent: `daySnapshotSources` (per-day + batch) throws under `Unknown`/`NotConsented`, succeeds under `Consented`.

CycleMath parity: suppressed → off-state; single-run/empty → notEnoughData (no fabricated cycleDay); regular 28-day history → `cycleDay == 6`, a non-degenerate next-period range `[lastStart+25 … lastStart+31]`, confidence `MEDIUM` (never HIGH); exactly-2-cycles boundary → derived + MEDIUM; irregular (spread 20) → derived with `LOW` confidence. Epoch-clean grep of `domain/` passed.

Version catalog + gradle files untouched — **zero new dependency** (threat T-24-SC "accept" honored).

## Deviations from Plan

**1. [Rule 3 - Blocking] Edit tool required a Read-tool read of EndoRepository.kt**
- **Found during:** Task 1
- **Issue:** The file was inspected via Bash `cat`, but the Edit tool requires a prior Read-tool read.
- **Fix:** Read the file header via the Read tool, then applied the edits. No code impact.

Otherwise executed as written. Two design calls the plan left to discretion: (a) reused `:insights InsightConfidence` (rather than a new domain `Confidence`) as `CycleSummary.confidence`; (b) kept the iOS derive-even-for-wide-spread behavior (irregular → `LOW`, `hasEnoughData=true`) rather than gating irregular to `notEnoughData` — both are explicitly sanctioned by the plan's acceptance criteria ("notEnoughData or capped-low per the iOS behavior"). The iOS `CycleSummary.phase` field was omitted (the report cycle section consumes only the five fields the plan key-links specify).

## Threat Model Compliance

- **T-24-04 (info disclosure):** `daySnapshotSources` gates first; returns only per-field `ValueSource` enums (never health values or note prose). Pinned by `RepositoryConsentTest`.
- **T-24-05 (tampering of meaning):** provenance is store-derived (row presence), never recomputed; `CycleMath` preserves the no-phantom-cycle gate; confidence capped at MEDIUM. Pinned by `DaySnapshotSourcesTest` + `CycleMathTest`.
- **T-24-SC (supply chain):** no package added; version catalog unchanged.

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: android/app/src/main/kotlin/com/endoapp/app/data/ValueSource.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/domain/CycleMath.kt
- FOUND: android/app/src/main/kotlin/com/endoapp/app/domain/CycleSummary.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/data/DaySnapshotSourcesTest.kt
- FOUND: android/app/src/test/kotlin/com/endoapp/app/domain/CycleMathTest.kt
- FOUND commit 65f2dcf (Task 1), 6a8e555 (Task 2)
