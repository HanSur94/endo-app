# Phase 7: Correlation & Trigger Discovery - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 8 (3 new source, 2 modified source, 3 new/extended test files)
**Analogs found:** 8 / 8 (all in-repo, except the pure-math file which is role-match against RESEARCH formulas)

> **Wave-0, sequence-first item:** `CorrelationStats.swift` (the math) MUST be built and proven against hand-computed known values (`CorrelationStatsTests.swift`) BEFORE `CorrelationEngine.swift` is wired. Every downstream gate (effect floor, FDR, confidence band) is meaningless if the deltas/p-values are wrong. Prove the math first; the engine and fixtures depend on it.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Sources/EndoInsights/CorrelationStats.swift` | utility (pure stats helpers) | transform (value-in/value-out) | RESEARCH §2–§6 formulas + `TrendsEngine` "each helper independently testable" | role-match (no in-repo stats analog) |
| `Sources/EndoInsights/Factor.swift` | model (Factor descriptor + binarization) | transform | `DaySnapshot.swift` (three-state honesty) + `TrendsEngine.factorTrends` thresholds | role-match |
| `Sources/EndoInsights/CorrelationEngine.swift` | service (the producer `enum`) | batch / transform (injected-today) | `CyclePhaseMapper.swift` + `TrendsEngine.swift` | exact (signature + idiom) |
| `Sources/EndoInsights/HonestyGate.swift` (MODIFY) | config (the minSample table) | request-response (gate) | the existing `minSample(for:)` table, line 37 | exact (one-line edit) |
| `Sources/EndoInsights/InsightsEngine.swift` (MODIFY) | service (composition root) | batch | the existing trends + cyclePhase composition, lines 35-38 | exact (one `+` term) |
| `Tests/.../CorrelationStatsTests.swift` (NEW) | test (pure-helper unit) | request-response | `CyclePhaseMapperTests` helper-level asserts | role-match (new suite) |
| `Tests/.../CorrelationEngineTests.swift` (NEW) | test (fixture) | request-response | `CyclePhaseMapperTests.swift` | exact |
| `Tests/.../SnapshotFixtures.swift` (EXTEND) | test (fixture DSL) | — | existing `regularCycleFixture` / `suppressedNoBleedingFixture` builders | exact |
| `Tests/.../HonestyGateTests.swift` (EXTEND) | test | — | `allEmittedInsights_haveZeroForbiddenTerms` (line 135) | exact |
| `Tests/.../InsightsEngineTests.swift` (EXTEND) | test | — | `composition_emitsBothTrendAndCyclePhaseKinds` (line 27) | exact |

---

## Pattern Assignments

### `Sources/EndoInsights/CorrelationStats.swift` (utility, transform) — WAVE 0, BUILD FIRST

**Analog:** No in-repo stats analog. Use the RESEARCH §2–§6 formulas (already supplied as concrete Swift in `07-RESEARCH.md` lines 429–474). The *pattern* to mirror is `TrendsEngine`'s "each helper independently testable" discipline (`TrendsEngine.swift:266` `// MARK: - Pure helpers (each independently testable)`) and its `nil`-not-0 guard style.

**Helper-guard pattern to copy** (`TrendsEngine.swift:269-272` — never coerce empty to 0):
```swift
static func average(_ values: [Double]) -> Double? {
    guard !values.isEmpty else { return nil }   // nil is NEVER coerced to 0
    return values.reduce(0, +) / Double(values.count)
}
```

**Helpers to implement** (signatures from RESEARCH `07-RESEARCH.md:429-474`): `cliffsDelta(present:absent:) -> Double?`, `meanDifference`, `riskDifference(a:b:c:d:) -> Double?`, `riskRatio`, `fisherExactTwoTailed(a:b:c:d:) -> Double`, `mannWhitneyP(present:absent:) -> Double`, `normalCDF(_:) -> Double`, `benjaminiHochbergPasses(_:q:) -> Set<Int>`, `confidence(effectMag:n:mediumFloor:largeFloor:passedFDR:) -> InsightConfidence`.

**LOAD-BEARING — `lgamma` ambiguity (RESEARCH §3A, `07-RESEARCH.md:199-207`):** a bare `lgamma(5.0)` does NOT compile (ambiguous overload). Bind the scalar overload once at file scope:
```swift
private static let lgammaScalar = (lgamma as (Double) -> Double)   // ln(4!) = lgammaScalar(5.0) == 3.17805383...
```
`erf` has NO ambiguity — use it directly for the normal CDF: `Φ(x) = 0.5 * (1 + erf(x / 2.0.squareRoot()))`.

**Determinism rule (mirror the whole package):** pure `Double` arithmetic, no `Date.now`/`UUID()`/RNG; sort index keys before iterating (the BH helper at `07-RESEARCH.md:455` already does `pValues.indices.sorted { ... }`).

---

### `Sources/EndoInsights/Factor.swift` (model, transform)

**Analog:** `DaySnapshot.swift:9-12` (the absence≠0 honesty contract) + `TrendsEngine.factorTrends` (`TrendsEngine.swift:177-206`, the existing binarization of `sleepQuality == .poor`, `stress`, `mood`).

**Three-state binarization** (RESEARCH §1 table, `07-RESEARCH.md:120-130`): each factor is `present` / `absent` / `unobserved`; `unobserved` is excluded from BOTH groups (Pitfall 2). Thresholds MUST be named, unit-asserted constants (the `TrendsEngine.minLoggedDays` precedent at `TrendsEngine.swift:33`), not buried literals:
- `factor.sleep.poor`: `sleepQuality == .poor` (VERIFIED reuse — `TrendsEngine.swift:195` already does this).
- `factor.stress.high`: `stress >= 7`; `factor.mood.low`: `mood <= 2` (`[ASSUMED]`, A2).
- food tags (per `FoodTag`), activity (per `ActivityType`), `medication.taken`, `prn.taken`, cycle-phase (per `CyclePhase`).

**Existing factor-binarization excerpt to mirror** (`TrendsEngine.swift:193-195`):
```swift
let sleepDays = recent.filter { $0.sleepQuality != nil }.count   // observed denominator (absence ≠ 0)
let poorDays = recent.filter { $0.sleepQuality == .poor }.count
if sleepDays >= minLoggedDays && poorDays >= 1 { ... }
```

---

### `Sources/EndoInsights/CorrelationEngine.swift` (service, batch) — depends on CorrelationStats + Factor

**Analog:** `CyclePhaseMapper.swift` (signature + suppressed short-circuit + descriptive emission) and `TrendsEngine.swift` (per-helper decomposition).

**Public entry-point signature — copy EXACTLY** (`CyclePhaseMapper.swift:31-35`):
```swift
enum CorrelationEngine {
    static func insights(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] { ... }
}
```

**Cycle-phase factor reuse + suppressed-by-construction** (`CyclePhaseMapper.swift:38-65`): call `CyclePhaseMapper.phaseWindows(...)` / `CyclePhaseMapper.phase(for:in:calendar:)` (`CyclePhaseMapper.swift:149,212`) to label each day. Suppressed/irregular streams return `[]` windows → the `factor.cyclePhase.*` family is never `present` → no phase correlation (D-04 satisfied with NO extra suppression code — exactly how `CyclePhaseMapper.swift:40-41` short-circuits).

**Insight emission — copy the `Insight.derived` call shape** (`CyclePhaseMapper.swift:253-267`), setting `sampleSize = pairedDays`, `points: []`, non-causal copy:
```swift
out.append(Insight.derived(
    kind: .correlation,
    window: .allTime,
    signalKey: "correlation.food.dairy.symptom.pelvicPain",  // RESEARCH §7 scheme, 07-RESEARCH.md:289-295
    headline: "Higher pelvic pain possibly associated with logging dairy",
    detail: "On the days you logged dairy, pelvic pain severity averaged ~2 points higher — a descriptive co-occurrence only.",
    confidence: confidence,           // from CorrelationStats.confidence(...), .none for withheld
    points: [],
    sampleSize: pairedDays            // = a+b+c+d (presence) or m+n (severity); the gate re-enforces minSample
))
```

**Orchestration order (RESEARCH diagram `07-RESEARCH.md:303-334`):** (1) binarize factors, (2) enumerate bounded pairs, (3) per pair restrict to observed/paired days + compute effect + p, **(4) collect ALL K p-values → `benjaminiHochbergPasses` ONCE** (never per-pair — anti-pattern `07-RESEARCH.md:369`), (5) hard-gate → confidence band → emit, (6) deterministic sort + cap top-N=5 POST-gate.

**Determinism (mirror `CyclePhaseMapper.associations`, `CyclePhaseMapper.swift:241-245`):** iterate `CyclePhase.allCases` and `keys.sorted(by: { $0.rawValue < $1.rawValue })` — sort every key sequence before emission (Pitfall 4). Final top-N tiebreaker: `signalKey asc` for a total order.

---

### `Sources/EndoInsights/HonestyGate.swift` (config — MODIFY, single-line)

**Analog:** the existing `minSample(for:)` table — the single source of truth.

**Exact edit** (`HonestyGate.swift:37`): change the `.correlation` case from `14` → `10` (RESEARCH §4 locks `minSample(.correlation) = 10`, `07-RESEARCH.md:227,237`):
```swift
case .correlation:    return 10   // Phase 7 LOCKED (was 14 placeholder); == the engine's self-gate floor
```
Also update the doc-comment at `HonestyGate.swift:30` (`→ 14 (Phase 7 placeholder)`). The engine self-gate MUST read `HonestyGate.minSample(for: .correlation)` — no second `10` literal (`07-RESEARCH.md:384`). **No new withholding logic** — the gate's existing steps (a)–(d) (`HonestyGate.swift:81-104`) already drop `.none`-confidence and `sampleSize < 10` correlations.

---

### `Sources/EndoInsights/InsightsEngine.swift` (service — MODIFY, one term)

**Analog:** the existing composition (`InsightsEngine.swift:35-38`).

**Exact edit** — add one `+ CorrelationEngine.insights(...)` term to the `candidates` (signature is LOCKED, `InsightsEngine.swift:11`):
```swift
let candidates =
    TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
    + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
    + CorrelationEngine.insights(from: snapshots, asOf: today, calendar: calendar)   // NEW
return HonestyGate.apply(candidates, dismissed: dismissed)
```

---

### `Tests/.../CorrelationStatsTests.swift` (NEW) — WAVE 0, prove the math first

**Analog:** the helper-level asserts in `CyclePhaseMapperTests.swift:74-84` (`windowsOverlap_isOrderIndependent` — testing a pure helper against known inputs).

Assert each helper against **hand-computed known values** (`07-RESEARCH.md:533`): `cliffsDelta([7,7,7],[2,2,2]) == 1.0`; ties → 0; `lgammaScalar(5.0) ≈ 3.17805`; `normalCDF(0) == 0.5`, `normalCDF(1/√2) ≈ 0.6826894921`; a Fisher 2×2 with a textbook p; a BH set with a worked example. This suite gates every downstream wave.

**Suite/`@Test` idiom — copy** (`CyclePhaseMapperTests.swift:8-13`):
```swift
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-03 CorrelationStats — pure stats helpers vs known values")
struct CorrelationStatsTests { @Test func cliffsDelta_allGreater_isOne() async throws { ... } }
```

---

### `Tests/.../CorrelationEngineTests.swift` (NEW)

**Analog:** `CyclePhaseMapperTests.swift` (the whole file — honesty-gate-per-fixture structure, lines 12-92).

**Private helper + per-fixture asserts — copy the shape** (`CyclePhaseMapperTests.swift:15-17`):
```swift
private func correlations(_ s: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
    CorrelationEngine.insights(from: s, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
}
```

Test map (RESEARCH `07-RESEARCH.md:507-513`): `strongAssociation_isSurfaced`, `underSampled_isWithheld` (n=9), `spurious_isWithheld`, `multipleComparisonFlood_surfacesNothing` (≥100 noise pairs → ZERO), `suppressedUser_noCyclePhaseCorrelation`, `output_isDeterministic` (mirror `CyclePhaseMapperTests.swift:88-92`).

---

### `Tests/.../SnapshotFixtures.swift` (EXTEND)

**Analog:** existing builders `regularCycleFixture` (`SnapshotFixtures.swift:161-176`) and `suppressedNoBleedingFixture` (`SnapshotFixtures.swift:83-93`) — same offset-builder + `Fixtures.snapshot(offset:...)` DSL (`SnapshotFixtures.swift:34-72`, which already exposes `foodTags`, `activityType`, `stress`, `mood`, `sleepQuality`, `tookPRN`, `suppressed`).

Add (RESEARCH `07-RESEARCH.md:516-525`): `strongAssociationFixture`, `underSampledAssociationFixture` (9 paired days), `spuriousAssociationFixture` (independent assignment), `multipleComparisonFloodFixture` (~60 days, all factors at ~50%), `suppressedUserCorrelationFixture` (build on `suppressedNoBleedingFixture` + dairy/symptom co-occurrence). **Reuse the existing `snapshot(offset:foodTags:symptoms:...)` builder verbatim** — do not add a new DSL.

---

### `Tests/.../HonestyGateTests.swift` (EXTEND)

**Analog:** `allEmittedInsights_haveZeroForbiddenTerms` (`HonestyGateTests.swift:135-164`) — the file's own contract (line 8-11) MANDATES every new kind adds its fixtures here.

**Exact edit:** append the new correlation fixtures to the `streams` array (`HonestyGateTests.swift:137-147`):
```swift
("strongAssociation", Fixtures.strongAssociationFixture()),
("multipleComparisonFlood", Fixtures.multipleComparisonFloodFixture()),
("suppressedUserCorrelation", Fixtures.suppressedUserCorrelationFixture()),
```
The loop already scans `headline`/`detail` for `ForbiddenTerms.violation`. **Copy-collision warning (Pitfall 3):** "cause" ⊂ "be**cause**" — phrase correlation copy as "possibly associated with" / "tended to co-occur with" only.

---

### `Tests/.../InsightsEngineTests.swift` (EXTEND)

**Analog:** `composition_emitsBothTrendAndCyclePhaseKinds` (`InsightsEngineTests.swift:27-32`).

Extend the kinds assertion to require `.correlation` surfaces over `strongAssociationFixture`, and assert it routes through the gate (the no-regression superset pattern at `InsightsEngineTests.swift:36-40`).

---

## Shared Patterns

### Pure injected-`today` engine `enum`
**Source:** `CyclePhaseMapper.swift:31-35`, `TrendsEngine.swift:48-52`
**Apply to:** `CorrelationEngine.swift`
Single `static func insights(from:asOf:calendar:) -> [Insight]`; Foundation-only; no store/UI/clock/RNG; package-internal behind `InsightsEngine`.

### Absence ≠ 0 (the #1 honesty invariant)
**Source:** `DaySnapshot.swift:9-12`, `TrendsEngine.swift:269-272` (`guard !values.isEmpty`), `TrendsEngine.swift:145-147` (`compactMap` severities)
**Apply to:** `CorrelationStats.swift`, `Factor.swift`, `CorrelationEngine.swift`
A `nil` severity/stress/mood is `unobserved` — excluded from both groups, never a 0. `guard !W.isEmpty, !O.isEmpty` before any delta.

### Deterministic emission (no dictionary/Set order leakage)
**Source:** `CyclePhaseMapper.swift:241-245` (`CyclePhase.allCases` + `keys.sorted`)
**Apply to:** `CorrelationEngine.swift` (sort factor keys + symptom rawValues; top-N tiebreaker `signalKey asc`)

### `Insight.derived` factory + stable `signalKey`
**Source:** `Insight.swift:130-153`, `CyclePhaseMapper.swift:253-267`
**Apply to:** `CorrelationEngine.swift`
`signalKey` scheme: `"correlation.<factorKey>.<symptomTarget>"` (`07-RESEARCH.md:289-295`); `sampleSize = pairedDays`; `points: []`; non-causal copy.

### The gate is the only withholding seam
**Source:** `HonestyGate.swift:81-104` (steps a–d), `InsightsEngine.swift:38`
**Apply to:** all of Phase 7
Emit below-floor candidates at `confidence: .none` honestly; the gate drops them. No parallel "correlation honesty layer" (anti-pattern, `07-RESEARCH.md:388`).

### Named, unit-asserted threshold constants
**Source:** `TrendsEngine.swift:33` (`minLoggedDays = 3`), `HonestyGate.minSample(for:)`
**Apply to:** `Factor.swift` (stress≥7, mood≤2), `CorrelationStats.swift` / `CorrelationEngine.swift` (effect floors 0.33/0.20, q=0.10, per-group min 3, top-N 5) — no buried literals.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `CorrelationStats.swift` | utility | transform | No statistics code exists anywhere in the package — this is the new IP (D-18). Use the closed-form formulas in `07-RESEARCH.md` §2–§6 (already given as Swift). Prove against known values BEFORE the engine consumes it. The `lgamma` overload bind and `erf`-based normal CDF are the load-bearing, easy-to-get-wrong details. |

---

## Metadata

**Analog search scope:** `EndoInsights/Sources/EndoInsights/` (all 9 source files), `EndoInsights/Tests/EndoInsightsTests/` (all 8 test files)
**Files scanned:** 17 read (4 source in full, 1 fixtures + 2 test files in full, targeted reads of `Insight.swift`, `HonestyGateTests.swift`, `InsightsEngineTests.swift`, `DaySnapshot.swift`, enum greps)
**Pattern extraction date:** 2026-06-09
