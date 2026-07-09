---
phase: 17-insights-engine-port
plan: 04
subsystem: insights-engine
tags: [kotlin, jvm, trends-engine, cycle-phase-mapper, honesty-by-omission, absence-not-zero, localdate, dst-safe, non-causal-copy, signalkey-parity, android-port, tdd]

# Dependency graph
requires:
  - "DaySnapshot / SymptomPoint / value types + of() factory (DaySnapshot.kt, plan 17-02)"
  - "Insight.derived + InsightKind/TrendWindow/InsightConfidence + DeterministicUUID (Insight.kt, plan 17-02)"
  - "CyclePhase enum + raw values (CyclePhase.kt, plan 17-02)"
  - "SnapshotEnums (SymptomType, FlowIntensity, SleepQuality raw values, plan 17-02)"
  - "Fixtures builder incl. regular/irregular/under-sampled/overlapping cycle + suppressed/rising/subThreshold fixtures (SnapshotFixtures.kt, plan 17-02)"
  - "kotlin(jvm) :insights module + JUnit5 (plan 17-01)"
provides:
  - "TrendsEngine: trends(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> — 7/30/90/all-time descriptive trends"
  - "CyclePhaseMapper: insights(from, asOf) + PhaseWindow + windowsOverlap — estimated phase windows + per-(phase,symptom) associations"
  - "TrendsEngineTests (11) + SuppressedFixtureTests (2 SC3 keystone) + CyclePhaseMapperTests (8)"
affects: [17-05, 17-06, 17-07, 17-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pattern 2: injected Swift Calendar DROPPED; day identity is java.time.LocalDate end-to-end; startOfDay is a no-op; date(byAdding:.day) → plusDays; dateComponents([.day]) → ChronoUnit.DAYS.between — eliminates the whole DST bug class"
    - "Swift enum-namespace producer → Kotlin object (uninstantiable); Swift nested struct → Kotlin nested data class (CyclePhaseMapper.PhaseWindow)"
    - "Honesty-by-omission ported verbatim: absence ≠ 0 (null excluded from every mean denominator), suppressed short-circuit gates bleeding/cycle output, sub-threshold symptom-frequency emits a typed insufficiency notice, non-causal copy constants copied verbatim"
    - "Number formatting via String.format(Locale.ROOT, \"%.1f\", v) (Pitfall 2) — never the default-locale overload"
    - "signalKeys reproduced verbatim (symptom.<type.raw>.severity/.frequency, factor.stress/mood.mean, factor.sleep.poor.frequency, bleeding.frequency, cyclePhase.<phase.raw>.symptom.<type.raw>) — feed DeterministicUUID for dismissal parity"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/TrendsEngine.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/CyclePhaseMapper.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/TrendsEngineTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/CyclePhaseMapperTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/SuppressedFixtureTests.kt
  modified:
    - android/insights/PARITY.md

key-decisions:
  - "Both engines take asOf: LocalDate (no Calendar param) per the locked Pattern-2 decision; all day math is LocalDate.plusDays/minusDays + ChronoUnit.DAYS.between — no Calendar, no epoch/Instant arithmetic"
  - "TrendsEngine.format uses Locale.ROOT so '6.0'/'4.0' render locale-independent (Pitfall 2); the severity-mean test asserts the exact '6.0' substring"
  - "CyclePhaseMapper.PhaseWindow + windowsOverlap are public (the Swift statics were package-internal) because CyclePhaseMapperTests reaches PhaseWindow/windowsOverlap directly — a test-visibility widen, no behavior change"
  - "Copy constants copied verbatim from the Swift sources — the cycle-phase disclaimer keeps the Swift rephrase '…a descriptive co-occurrence only.' that dodges the 'cause' ⊂ 'because' substring trap; no re-wording"
  - "Severity/stress/mood means encoded so the mean is the TRAILING point value only for scale means (stress/mood: single trailing InsightPoint(lastDay, mean)); per-symptom severity keeps the honest per-day reading series (no spliced mean point) exactly as the Swift WR-04 contract"
  - "PARITY.md updated incrementally (3 rows 17-04 done, Kotlin 35→56) per the 17-02/17-03 precedent; full reconciliation lands in 17-08"

requirements-completed: []  # AENG-02 is partial — two of the six producers ported; the single public InsightsEngine producer + HonestyGate composing them lands in 17-08. Orchestrator/verifier owns final AENG-02 marking.

# Metrics
duration: 11min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 04: TrendsEngine + CyclePhaseMapper Summary

Ported the two descriptive `[Insight]` producers that depend only on the value-type layer — `TrendsEngine` (7/30/90/all-time descriptive trends) and `CyclePhaseMapper` (estimated cycle-phase windows + per-(phase,symptom) associations) — from Swift to pure-JVM Kotlin, verbatim math and copy, with honesty-by-omission intact and the injected `Calendar` dropped for `java.time.LocalDate` day identity (Pattern 2).

## What was built

- **TrendsEngine.kt** — `object` with `trends(from: List<DaySnapshot>, asOf: LocalDate): List<Insight>`. Per-symptom severity-mean (null severities excluded from the denominator) + frequency (days-present / days-with-any-log, gated on `daysWithAnyLog >= minLoggedDays=3`), stress/mood means, poor-sleep + bleeding-day frequency. Windows anchored on absolute `LocalDate` ranges `[today-(N-1)..today]` inclusive; all-time omits direction. Sub-threshold symptom frequency emits a typed `isInsufficiencyNotice` "not enough data yet" Insight. Bleeding trend short-circuits under `anySuppressed || bleedingDayCount < 1`.
- **CyclePhaseMapper.kt** — `object` with `insights(from, asOf)`, public nested `PhaseWindow` + `windowsOverlap`. Verbatim `CycleMath` day math (`periodRuns` / `consecutiveGaps` / `medianInt` / `spread`). Gate order: suppressed → `[]`; `< 2` start-to-start gaps → `[]`; `phaseConfidence` null (spread > 9 irregular) → `[]`; any window overlap → `[]`. `phaseConfidence` caps at `MEDIUM`, never `HIGH`. Ovulation band `max(2, spread/2)` anchored 14 days before the next predicted start. Emits one non-causal `CYCLE_PHASE` insight per (phase, symptom) co-occurrence.
- **Tests** — TrendsEngineTests (11), SuppressedFixtureTests (2 — the SC3 no-phantom-bleeding-under-suppression keystone), CyclePhaseMapperTests (8), all ported 1:1 from the Swift oracle against the existing `SnapshotFixtures.kt` builder.

## Real test results

`cd android && ./gradlew :insights:test` — **BUILD SUCCESSFUL**, full module **69/69 green** (0 failures, 0 errors).

| Suite | Cases | Result |
|-------|------:|--------|
| TrendsEngineTests | 11 | ✅ 11/11 |
| SuppressedFixtureTests | 2 | ✅ 2/2 |
| CyclePhaseMapperTests | 8 | ✅ 8/8 |
| **This plan** | **21** | **✅ 21/21** |
| BoundaryLintTests | 1 | ✅ (no android./androidx. leaked) |
| (prior suites unchanged) | 47 | ✅ |

Case counts read from the JUnit XML (`insights/build/test-results/test/TEST-*.xml`), not asserted by hand.

## Verification against plan

- signalKeys match the research contract verbatim (`symptom.<type.raw>.severity/.frequency`, `factor.stress.mean`, `factor.mood.mean`, `factor.sleep.poor.frequency`, `bleeding.frequency`, `.insufficient` suffix on the notice; `cyclePhase.<phase.raw>.symptom.<type.raw>`).
- No Calendar / epoch / Instant math — all day arithmetic is `LocalDate` + `ChronoUnit.DAYS.between`. BoundaryLintTests stays green.
- Honesty invariants pinned: absence ≠ 0 (`stressMean_unloggedDaysExcluded_differsFromLoggedZero` = 4.0 vs 0.0), suppressed silence (SuppressedFixtureTests + `suppressedStream_yieldsNoPhases`), confidence cap (`regularCycle_..._neverHighConfidence`), irregular/under-sampled/overlapping → `[]`.

## Deviations from Plan

**1. [Rule 3 - Blocking] Widened `PhaseWindow` + `windowsOverlap` visibility to public.** The Swift `PhaseWindow` struct and `windowsOverlap` were package-internal statics; the ported `CyclePhaseMapperTests.windowsOverlap_isOrderIndependent` constructs `CyclePhaseMapper.PhaseWindow` and calls `CyclePhaseMapper.windowsOverlap` directly (same-package Kotlin test), so both are public members of the `object`. This is a test-visibility widen with no behavior change (Kotlin has no Swift `@testable import` equivalent; same-module `internal` would also have worked but public matches the direct `CyclePhaseMapper.PhaseWindow(...)` call site cleanly).

No other deviations — the two engines and their three test suites were ported mechanically; math, gate order, thresholds, and copy constants are verbatim.

## PARITY ledger

Updated `android/insights/PARITY.md` incrementally (per the 17-02/17-03 precedent): the three 17-04 rows (TrendsEngineTests 11, CyclePhaseMapperTests 8, SuppressedFixtureTests 2) marked ✅ done; Kotlin parity total **35 → 56** of the 157 target. Full reconciliation remains for 17-08.

## Known Stubs

None. Both producers are fully wired against the real value-type layer and fixtures; the `sampleSize` values are emitted at their real evidence counts (the `HonestyGate` n-floor enforcement lands in 17-08, as noted in the ported source comments — an intentional cross-plan seam, not a stub).

## Self-Check: PASSED

- Created files verified present: TrendsEngine.kt, CyclePhaseMapper.kt, TrendsEngineTests.kt, CyclePhaseMapperTests.kt, SuppressedFixtureTests.kt.
- Commits verified in `git log`: 6b82927 (test), b3d07de (feat TrendsEngine), 97483f6 (test), 7088e4f (feat CyclePhaseMapper), 6532f62 (docs PARITY).
- Full `:insights` suite green (69/69) after both ports.
