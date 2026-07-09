---
phase: 17-insights-engine-port
plan: 06
subsystem: insights-engine
tags: [kotlin, jvm, correlation-engine, benjamini-hochberg, three-and-gate, cliffs-delta, mann-whitney, fisher-exact, risk-difference, non-causal-copy, signalkey-parity, honesty-by-omission, android-port, tdd]

# Dependency graph
requires:
  - "CorrelationStats (cliffsDelta, meanDifference, riskDifference/riskRatio, fisherExactTwoTailed, mannWhitneyP, benjaminiHochbergPasses, confidence) + Double.roundedAwayFromZero (plan 17-03)"
  - "CyclePhaseMapper (periodRuns/consecutiveGaps/medianInt/spread/phaseConfidence/phaseWindows/phase/displayName) (plan 17-04)"
  - "Factor sealed interface + FactorState + stateOf(factor, day, phase) + threshold constants (plan 17-02)"
  - "DaySnapshot / SymptomPoint value types + Insight.derived / InsightKind / TrendWindow / InsightConfidence (plan 17-02)"
  - "SnapshotFixtures correlation fixtures incl. multipleComparisonFloodFixture independent-hash builder (plan 17-02)"
provides:
  - "CorrelationEngine: insights(from, asOf): List<Insight> — the .correlation producer (three-AND gate + single BH-FDR pass + deterministic top-5)"
  - "CorrelationEngine.scoredCandidates(from, asOf): List<CorrelationFinding> — package-internal typed accessor for RecommendationsEngine (plan 07)"
  - "CorrelationEngine.CorrelationFinding + RecommendationDirection (signed-effect direction recovery)"
  - "CorrelationEngineTests (12) — flood/gate/determinism honesty spec"
affects: [17-07, 17-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Swift enum-namespace producer → Kotlin object; Swift private enum with associated values → private sealed interface (PresenceTarget, Arm); Swift nested struct → private data class (Candidate, ScoredCandidate)"
    - "De-dup snapshots by day via associateBy { it.day } (last-wins on key collision, mirroring Swift Dictionary(uniquingKeysWith: last)) then sortedBy day — a duplicate-day input can never crash the pipeline (CR-01/WR-04)"
    - "ONE Benjamini-Hochberg pass (q=0.10) over the FULL candidate p-value set (never per-pair) — the load-bearing SC3 no-false-positive guard"
    - "Deterministic ordering via compareByDescending(confidence).thenByDescending(effectMag).thenByDescending(pairedDays).thenBy(signalKey) — enum InsightConfidence is Comparable by ordinal==value, matching the Swift Int-backed Comparable"
    - "Swift .rounded() (half-away-from-zero) mirrored by Double.roundedAwayFromZero(); String(format:) → String.format(Locale.ROOT, ...); Swift .capitalized mirrored by an ASCII-faithful capitalizedLikeSwift() helper"
    - "correlationMinSample named constant is the SSOT correlation floor until HonestyGate.minSample(.correlation) is wired in 17-08 (exactly one literal 10)"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/CorrelationEngine.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/CorrelationEngineTests.kt
  modified:
    - android/insights/PARITY.md

key-decisions:
  - "correlationMinSample=10 is a private const mirroring HonestyGate.minSample(.correlation); HonestyGate is not yet ported (17-08), so this named constant is the single correlation floor (one literal), to be replaced by the HonestyGate call once wired — follows the 17-03 precedent exactly"
  - "Non-causal copy is asserted with an inline banned-substring list (mirroring CyclePhaseMapperTests); ForbiddenTerms + ClaimsPolicyDriftTests are ported in 17-08. Same enforcement intent, no premature dependency"
  - "duplicateDay_* exercises the DIRECT CorrelationEngine.insights path only (no-crash + empty); the InsightsEngine public-pipeline half of the Swift test is deferred to 17-08 when InsightsEngine lands — the direct path fully proves the CR-01 duplicate-day no-crash contract this plan owns"
  - "Swift .capitalized mirrored by an ASCII-faithful capitalizedLikeSwift() (title-case after any non-letter, lowercase the rest) — the presence-arm headline is not exact-asserted by the 12 cases, but the helper keeps byte-parity for the label vocabulary; final reconciliation in 17-08"
  - "PARITY.md updated incrementally (CorrelationEngineTests done, Kotlin 95 → 107) per the 17-02/03/04/05 precedent; full reconciliation lands in 17-08"

requirements-completed: []  # AENG-02 is partial — the .correlation producer is ported; the single public InsightsEngine producer + HonestyGate composing all six producers lands in 17-08. Orchestrator/verifier owns final AENG-02 marking.

# Metrics
duration: 12min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 06: CorrelationEngine Port Summary

**Ported the statistically-intricate `.correlation` producer — de-dup snapshots by day, reuse CyclePhaseMapper for cycle-phase labelling, bounded factor × (symptom-severity / symptom-presence / bleeding-presence) enumeration over observed days, ONE Benjamini-Hochberg pass (q=0.10) over the whole candidate set, the three-AND hard gate, and the deterministic top-5 sort — verbatim from Swift to pure-JVM Kotlin, standing on the proven CorrelationStats math (plan 03). Its acceptance guarantee is ZERO false positives under a multiple-comparison flood, and it holds.**

## What was built

- **CorrelationEngine.kt** — a Kotlin `object` mirroring `CyclePhaseMapper`'s shape. Public `insights(from, asOf: LocalDate)` and package-internal `scoredCandidates(from, asOf): List<CorrelationFinding>` both drain ONE private `scoredSurvivors` pipeline (steps 0–6), so a `CorrelationFinding` exists iff the matching `.correlation` Insight exists, with identical `signalKey`/`confidence`/`pairedDays` — the math runs once.
  - **(0) De-dup by day** — `from.associateBy { it.day }.values.sortedBy { it.day }` (last-wins on a key collision, mirroring Swift `Dictionary(uniquingKeysWith: last)`) so a duplicate-day input degrades gracefully instead of crashing (CR-01/WR-04).
  - **(1) Cycle-phase labelling** — reuses `CyclePhaseMapper.periodRuns/consecutiveGaps/medianInt/spread/phaseConfidence/phaseWindows/phase` VERBATIM. A suppressed/irregular/under-sampled stream → `[]` windows → every label `null` → no `factor.cyclePhase.*` pair is ever tested (D-04 **by construction**, no extra suppression code).
  - **(2) Bounded factor enumeration** — foods, activity-present + activity types, poor sleep, high stress, low mood, medication, PRN, plus cycle-phase factors ONLY when some day carries a non-null phase label; whole set sorted by stable `key` (Pitfall 4).
  - **(3) Two arms** — severity arm (Cliff's δ + mean-diff + Mann-Whitney; one observation per day = max severity, WR-02) and presence arm (2×2 → RD + RR + Fisher), each self-gated on the sample floor (n ≥ 10 AND each group ≥ `perGroupMinimum`=3).
  - **(3.5) ONE BH-FDR pass** — `CorrelationStats.benjaminiHochbergPasses(pValues, q=0.10)` over the FULL candidate set, never per-pair (the load-bearing SC3 guard).
  - **(4) three-AND gate** — `CorrelationStats.confidence(effectMag, n, minN=correlationMinSample, mediumFloor, largeFloor, passedFDR)`.
  - **(5) deterministic order** — confidence desc, effectMag desc, pairedDays desc, signalKey asc.
  - **(6) emit** — only the top-5 survivors with confidence ≥ LOW; below-gate `NONE` pairs are never emitted, so a withheld pair never occupies a top-N slot.
  - **Copy** — hand-written NON-CAUSAL copy ("possibly associated with logging …", "— a descriptive co-occurrence only.") ported verbatim; rounding via `roundedAwayFromZero`; lift formatting via `String.format(Locale.ROOT, "%.1f", …)`.
  - **Direction recovery** — `CorrelationFinding` + `RecommendationDirection` recovered from the SIGNED effect (`meanDiff`/`riskRatio`), NEVER by parsing the audited public copy — the typed seam `RecommendationsEngine` (plan 07) consumes.
- **CorrelationEngineTests.kt (12)** — the honesty spec ported 1:1 against the existing `SnapshotFixtures.kt`: Factor threshold + three-state binarization spot-checks; strong association surfaces (effect + confidence ≥ MEDIUM + non-causal copy + sampleSize ≥ 20); under-sampled (n=9), spurious (near-zero δ), and the **BH-FDR flood (K≈100+ independent-hash noise) each → ZERO**; suppressed user has no cyclePhase correlation (D-04); severity arm counts DAYS not observations (WR-02, sampleSize == 40); duplicate-day no-crash; and output determinism.

## Real test results

`cd android && ./gradlew :insights:test` — **BUILD SUCCESSFUL**, full module **120/120 green** (0 failures, 0 errors). Counts read from the JUnit XML (`insights/build/test-results/test/TEST-*.xml`), not fabricated.

| Suite | Cases | Result |
|-------|------:|--------|
| `CorrelationEngineTests` (this plan) | 12 | ✅ 12/12 |
| `BoundaryLintTests` | 1 | ✅ (no android./androidx. leaked; new sources import only kotlin.math + java.time + java.util.Locale) |
| (prior suites unchanged) | 107 | ✅ |
| **Total** | **120** | **✅ 120/120** |

The load-bearing `multipleComparisonFlood_surfacesNothing` passes: the fixture drives each signal off an INDEPENDENT per-(signal, day) splitmix hash (already built in `SnapshotFixtures.kt`, per the STATE [07-02] lesson — NOT small modular toggles that manufacture real anti-correlations), and the single BH-FDR pass crushes all K≈100+ noise pairs to zero.

## Verification against plan

- signalKeys match the contract verbatim: `correlation.<factor.key>.symptom.<type.raw>` and `correlation.<factor.key>.bleeding` (asserted via `correlation.food.dairy.symptom.pelvicPain`).
- ONE BH-FDR pass over the full candidate set (never per-pair); three-AND gate; deterministic top-5 — all present.
- Zero false positives under flood/under-sample/spurious; suppressed-user cyclePhase suppression proven; output deterministic.
- `compileKotlin` clean; `BoundaryLintTests` stays green (no android./androidx.).
- The read-only iOS tree (`EndoInsights/`, `Sources/`) is unmodified (`git status` empty for those paths).

## Deviations from Plan

**1. [Rule 3 - Blocking dependency not yet ported] `HonestyGate.minSample(.correlation)` → named constant `correlationMinSample=10`.** `HonestyGate` is not ported until 17-08, so the per-pair floor and the `confidence(minN:)` call read a private `const val correlationMinSample = 10` documented as the SSOT mirror of `HonestyGate.minSample(for: .correlation)`, to be replaced by the `HonestyGate` call when wired. Follows the 17-03 precedent exactly (that plan used the same literal-10 approach in `CorrelationStatsTests`). No behavior change — the floor value is identical.

**2. [Rule 3 - Blocking dependency not yet ported] Non-causal copy asserted inline; `InsightsEngine` half of `duplicateDay_*` deferred.** `ForbiddenTerms`/`ClaimsPolicyDriftTests` and `InsightsEngine` land in 17-08. The strong-association test uses an inline banned-substring list (mirroring `CyclePhaseMapperTests`), and `duplicateDay_doesNotCrash_andProducesSensibleResult` exercises the DIRECT `CorrelationEngine.insights` path only. The direct path fully proves the CR-01 duplicate-day no-crash contract this plan owns; the InsightsEngine public-pipeline assertion is a 17-08 addition. Both are documented at the call sites and in the test-file header.

**3. [Additive parity helper] `capitalizedLikeSwift()` for Swift `String.capitalized`.** Kotlin has no direct `.capitalized`; a small ASCII-faithful helper (title-case after any non-letter, lowercase the rest) reproduces Foundation's behavior for the label vocabulary. The presence-arm headline is not exact-asserted by the 12 cases, but the helper keeps byte-parity; final reconciliation is in 17-08.

No Rule 1 bugs, no Rule 4 architectural changes, no auth gates. The engine and its test suite were ported mechanically; math, gate order, thresholds, signalKeys, and copy constants are verbatim.

## PARITY ledger

Updated `android/insights/PARITY.md` incrementally (per the 17-02/03/04/05 precedent): `CorrelationEngineTests.swift` (12) → `CorrelationEngineTests.kt` (12) marked ✅ done; Kotlin parity total **95 → 107** of the 157 target; remaining pending 17-07 (13), 17-08 (37) → 107 + 50 = 157. Full reconciliation (and the `ForbiddenTerms`/`ClaimsPolicyDriftTests`/`InsightsEngine`/DST-half guard rows) still lands in 17-08.

## Threat mitigations (from this plan's threat_model)

- **T-17-16 (multiple-comparison false positives):** ONE BH-FDR pass (q=0.10) over the full candidate set + the three-AND gate; `multipleComparisonFlood_surfacesNothing` pins ZERO surfaced under K≈100+ independent-hash noise — green.
- **T-17-17 (cyclePhase factor for a suppressed user):** phase labelling reuses `CyclePhaseMapper` empty-window behavior (D-04 by construction); `suppressedUser_noCyclePhaseCorrelation` pins no cyclePhase correlation — green.
- **T-17-18 (correlation signalKeys):** signalKeys reproduced verbatim (`correlation.<factor.key>.symptom.<type.raw>` / `.bleeding`) for dismissal parity in Phase 18 — asserted via the dairy↔pelvicPain key.

## Known Stubs

None in production code. Two test-side seams are intentional cross-plan deferrals (documented above and at the call sites), both owned by 17-08: the inline banned-substring copy gate (→ `ForbiddenTerms`) and the `InsightsEngine` public-pipeline half of `duplicateDay_*`. The engine itself is fully wired against the real CorrelationStats/CyclePhaseMapper/Factor layers; `correlationMinSample` is a real, enforced floor (not a placeholder).

## Self-Check: PASSED

- Created files verified present: `CorrelationEngine.kt`, `CorrelationEngineTests.kt`; modified `PARITY.md`.
- Commits verified in `git log`: `64564ab` (feat CorrelationEngine), `d205bc9` (test CorrelationEngineTests), `6391c95` (docs PARITY).
- `./gradlew :insights:test` green (120/120); `CorrelationEngineTests` 12/12 (JUnit XML).
- The read-only iOS tree (`EndoInsights/`, `Sources/`) is unmodified.

---
*Phase: 17-insights-engine-port*
*Completed: 2026-07-09*
