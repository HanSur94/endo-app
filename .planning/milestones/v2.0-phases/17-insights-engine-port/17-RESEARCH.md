# Phase 17: Insights Engine Port - Research

**Researched:** 2026-07-08
**Domain:** Verbatim port of the pure-Swift `EndoInsights` package → pure-Kotlin-JVM `:insights` module (statistics + honesty gate + chart data-prep), proven by case-count parity
**Confidence:** HIGH (port SOURCE read directly, file-by-file; stack/patterns already locked and version-verified in STACK.md/ARCHITECTURE.md)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Workspace & build**
- Monorepo: the Android app lives in `android/` in this repo; iOS sources (`Sources/`, `EndoInsights/`, `project.yml`) stay untouched.
- `:insights` module uses `kotlin("jvm")` ONLY — no Android Gradle plugin, no `android.`/`androidx.` imports (enforced by a boundary lint test).
- Version catalog `android/gradle/libs.versions.toml` is the zero-dependency audit surface. Pins: Kotlin 2.3.21, Gradle 9.4.1+, JDK 21 toolchain, kotlinx-serialization-json 1.11.0, JUnit Jupiter 6.1.1. A test fails if any analytics/auth/backend/networking coordinate appears in the catalog.
- Path-filtered CI (`.github/workflows/android.yml`, `android/**`) runs the `:insights` JVM job as a merge gate. iOS tree stays read-only (drift scans read it, never write).

**Engine port**
- Port the domain enums verbatim by raw value: SymptomType (11), BodyRegion (10), FactorKind, FoodTag, ActivityType (5), ActivityIntensity (3), SleepQuality, FlowIntensity (4). Kotlin enums carry an explicit `raw: String` matching the Swift `rawValue` exactly.
- Port the value types (`DaySnapshot`, insight types) with the nil-means-unlogged honesty contract intact (absence ≠ 0); make NaN unrepresentable at the `DaySnapshot` boundary.
- Port all six engines behind the single HonestyGate choke point: TrendsEngine, CyclePhaseMapper, CorrelationEngine (BH-FDR, Fisher exact, Mann-Whitney U with tie correction, Cliff's delta), RecommendationsEngine, FlareEngine — plus ForbiddenTerms. One public producer function (InsightsEngine).
- `Factor`'s associated values map to a sealed interface with the same `signalKey` fragments as Swift.

**Testing & parity**
- Test framework: JUnit Jupiter 6.1.1; use `@ParameterizedTest` to preserve Swift Testing's per-argument case counts.
- Port the full Swift test suite (~157 cases across 18 files) file-for-file; track case-count parity in a committed `PARITY.md` ledger (the phase exit criterion).
- Include the honesty acceptance fixtures: suppressed-user silence, under-sample withholding, BH-FDR multiple-comparison flood, absence-vs-zero distinctions.
- Add a numeric-divergence fixture pack: NaN, ±0.0, rounding ties, tied ranks (Mann-Whitney), and DST/non-UTC day-boundary math (run in a non-UTC test zone). `LocalDate`-only day identity.
- Cross-platform drift tests: (a) a JVM test regex-scans the Swift sources (`SnapshotEnums.swift`, `Factor.swift`, `ClaimsPolicy.swift` — assumption-guarded, zero iOS changes) to catch enum-raw-value / ClaimsPolicy-substring divergence, plus (b) an unconditional golden JSON. Drift must be red, not a convention.

**Numeric-parity guardrails**
- Watch Kotlin↔Swift divergence: `Double.equals` (NaN==NaN true, 0.0≠-0.0), total-ordered NaN in `compareTo` (affects Mann-Whitney ranks), unstable primitive `DoubleArray.sort()`, rounding tie-breaks. Ban `String.format`/number formatting without an explicit `Locale`.

### Claude's Discretion
- Exact Gradle module file layout, package names under `com.endoapp.insights`, and test-file organization — at Claude's discretion, guided by the ARCHITECTURE.md realization and the Swift source structure. Prefer file-for-file correspondence with `EndoInsights/Sources/EndoInsights/` to make parity auditable.

### Deferred Ideas (OUT OF SCOPE)
- None — later phases (persistence, UI, Health Connect, charts, export, PDF, Play prep) are already scoped as Phases 18–25.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AENG-01 | Domain enums + value types ported verbatim by raw value; DaySnapshot with nil-means-unlogged intact | Exact raw-value tables below (§Enum Parity Contract); DaySnapshot field inventory (§Swift Source Inventory) — all raw values are Swift case-names (no custom `= "..."`) |
| AENG-02 | All six engines behind the single HonestyGate; one public producer | Engine-by-engine port notes (§Swift Source Inventory); `InsightsEngine.insights(...)` is the one public entry composing Trends+CyclePhase+Correlation+Recommendations+Flare → `HonestyGate.apply` |
| AENG-03 | Ported JVM suite reaches ~157-case parity, tracked in PARITY.md, all green | Full per-file case counts (§Test Inventory); PARITY.md ledger schema (§Validation Architecture) |
| AENG-04 | `:insights` pure Kotlin JVM — boundary lint proves no android/androidx imports | Boundary lint port (§Validation Architecture); `kotlin("jvm")` module only |
| AENG-05 | Cross-platform drift tests — enum raw values + ClaimsPolicy substrings vs Swift sources (scan + golden JSON) | Drift-test A/B design (§Validation Architecture); exact ClaimsPolicy lists (§ClaimsPolicy / ForbiddenTerms Parity) |
| AENG-06 | Numeric-divergence fixture pack green (NaN, ±0.0, rounding ties, tied ranks, DST/non-UTC) | §Numeric Divergence Pitfalls — every divergence enumerated with the exact Swift semantic to reproduce |
| AENG-07 | `android/` Gradle workspace + version catalog zero-dependency audit surface, path-filtered CI green | Catalog + module shape locked in STACK.md/ARCHITECTURE.md (§Standard Stack); catalog-audit test (§Validation Architecture) |
</phase_requirements>

## Summary

This is a **mechanical, byte-behavior-preserving port** of 19 pure-Swift source files (~3,570 LOC) + 18 test files (157 `@Test` cases) into a pure-Kotlin-JVM Gradle module. The engine is Foundation-only on the Swift side (no SwiftData/SwiftUI/UIKit — proven by an in-suite boundary lint), so nothing platform-specific has to be re-architected; the port is a translation exercise where **the Swift test suite is the correctness oracle**. The single most valuable output of this research is the exact enum-raw-value and `signalKey` contract (below) plus a precise map of where Kotlin and Swift numeric semantics diverge — those two things are the whole risk surface.

Two hazards dominate and are NOT optional to handle: (1) **no Kotlin/JVM stdlib `lgamma` or `erf`** — Swift binds the platform libm versions, so Fisher-exact and Mann-Whitney/normal-CDF math depends on hand-rolled special functions that will not be bit-identical to libm and must be tolerance-tested against Swift golden values; (2) **rounding, NaN, ±0.0, sort-stability, and locale-formatting** all differ between the two runtimes exactly where the Swift fixtures never probed. The CONTEXT's numeric-divergence fixture pack (AENG-06) is the defense.

**Primary recommendation:** Port file-for-file into `com.endoapp.insights` preserving the Swift source structure 1:1, translate day identity from Swift `Date`+injected `Calendar` to **`java.time.LocalDate`** (drop the `Calendar` parameter — LocalDate is zone-free and DST-safe by construction), hand-roll `lgamma`/`erf`/half-away-from-zero rounding as pinned helpers, and write the numeric-divergence fixture pack + Swift-source drift scans before declaring parity. All 157 Swift `@Test` cases are plain (non-parameterized), so the JUnit mapping is a straight 1:1 `@Test → @Test`.

## Architectural Responsibility Map

`:insights` is a **single-tier pure-JVM analytics library** — no browser/API/DB/CDN tiers exist in this phase. The "tiers" below are the module's internal layers (the porting order also follows this, dependencies flowing downward):

| Capability | Primary Layer | Secondary Layer | Rationale |
|------------|--------------|-----------------|-----------|
| Vocabulary / raw-value contract | Value types (`SnapshotEnums`, `CyclePhase`, `Factor`, `Insight`) | — | Everything keys off these raw strings; ported first, drift-guarded |
| Per-day read model | `DaySnapshot` (+ `SymptomPoint`, `PainPoint`) | Value types | The honesty boundary (absence ≠ 0, NaN unrepresentable); every engine consumes it |
| Pure statistics | `CorrelationStats` | — | lgamma/erf/Fisher/Mann-Whitney/Cliff/BH-FDR; independently unit-tested before any engine |
| Descriptive producers | `TrendsEngine`, `CyclePhaseMapper`, `CorrelationEngine`, `RecommendationsEngine`, `FlareEngine` | Stats + value types | Each emits candidate `[Insight]`; none is public |
| Ethical choke point | `HonestyGate` + `ForbiddenTerms` | Value types | The one mandatory filter; withholds under-sampled/low-confidence/causal-copy insights |
| Public composition root | `InsightsEngine` | All producers + gate | The ONE public function the app (Phase 18+) calls |
| Chart data-prep | `ExploreSeries*`, `ScatterPairing`, `SeriesNormalization`, `ChartScrubSelection` | Value types | Consumed by `:app` charts in Phase 22; ported here for suite parity |
| Cross-platform parity | drift tests, boundary lint, PARITY ledger | Test source set | The exit-criterion machinery |

## Swift Source Inventory

**Location (read-only port SOURCE):** `EndoInsights/Sources/EndoInsights/` (19 files). Every file is `import Foundation` only. Kotlin package base: `com.endoapp.insights`. Recommend one Kotlin file per Swift file (Claude's Discretion, but PARITY-friendly).

| Swift file | ~LOC | Content | Foundation APIs needing a Kotlin equivalent |
|------------|------|---------|---------------------------------------------|
| `SnapshotEnums.swift` | 142 | **7 public String enums** (SymptomType, BodyRegion, FlowIntensity, FoodTag, ActivityType, ActivityIntensity, SleepQuality) each with a defensive `init(raw:)`/`init?(raw:)` | `String` raw enums → Kotlin enum `raw: String`; `CaseIterable` → `entries`; `init(raw:)` → `fromRaw()`/`fromRawOrNull()` factory (unknown→`.other` sink or null) |
| `CyclePhase.swift` | 36 | Public `CyclePhase` (4 cases) + `PhaseDay` struct (`day: Date`, `phase`) | `Date` → `LocalDate` |
| `Factor.swift` | 198 | `FactorState` enum (present/absent/unobserved); `Factor` **enum with associated values** → Kotlin **sealed interface**; `key`/`displayLabel`; three-state `state(of:on:)` + `state(of:on:phase:)`; `hasAnyLog`; threshold constants | Sealed interface w/ data classes; `Set` on symptom types |
| `DaySnapshot.swift` | 141 | `SymptomPoint`, `PainPoint`, `DaySnapshot` value structs (16 fields, mostly optional = "not logged") | `Date`→`LocalDate`; `[UUID]`→`List<UUID>` (java.util.UUID); make Double fields NaN-unrepresentable at init |
| `Insight.swift` | 192 | `InsightKind`(5), `TrendWindow`(4), `InsightConfidence`(Int, Comparable), `InsightPoint`, `Insight` (Codable, Identifiable, `derived(...)` factory); **`DeterministicUUID`** FNV-1a v5 derivation | `Codable`→kotlinx-serialization `@Serializable`; `UUID` from 16 bytes; **wrapping UInt64 arithmetic** (`&*`/`&+`) → Kotlin `ULong` (wraps by default) |
| `CorrelationStats.swift` | 205 | Pure stats: Cliff's δ, meanDiff, riskDiff/riskRatio, **Fisher exact** (hypergeometric via log-factorials), **normalCDF** (erf), **Mann-Whitney U** (tie-corrected, no continuity correction), **BH-FDR**, confidence bands. Uses `lgamma`, `erf`, `exp`, `squareRoot` | **`lgamma` and `erf` do NOT exist in Kotlin/JVM stdlib — hand-roll** (see §Statistical Algorithms); `exp`→`kotlin.math.exp`; `2.0.squareRoot()`→`sqrt`; `.sorted()` |
| `TrendsEngine.swift` | 357 | Descriptive 7/30/90/all-time trends; window bucketing; symptom severity-mean + frequency; stress/mood means; poor-sleep + bleeding frequency; "not enough data" notice; `direction`/`confidence`/`format` helpers | `Calendar.startOfDay`/`date(byAdding:.day)` → `LocalDate.plusDays`; `String(format:"%.1f")`→`String.format(Locale.ROOT,...)`; `reduce(0,+)` |
| `CyclePhaseMapper.swift` | 300 | Estimated cycle-phase windows (verbatim `CycleMath` day math); periodRuns/gaps/median/spread; phase windows; per-(phase,symptom) associations | `Calendar.dateComponents([.day],from:to:)` → `ChronoUnit.DAYS.between` / `toEpochDay` diff; `startOfDay`; `date(byAdding:.day)` |
| `CorrelationEngine.swift` | 481 | The `.correlation` producer: de-dup snapshots by day, cycle-phase labelling, factor enumeration, severity-arm + presence-arm candidates, single BH-FDR pass, three-AND gate, top-5 deterministic sort; `CorrelationFinding`/direction recovery for Recommendations | `Dictionary(uniquingKeysWith:)`→`associate`+last-wins; `.sorted{}` with multi-key comparator; `.rounded()` (half-away-from-zero); `String(format:"%.1f")` |
| `RecommendationsEngine.swift` | 231 | Re-frames CorrelationEngine survivors into ≤3 direction-aware non-prescriptive recommendations; dominant-per-symptom collapse; `RecommendationCopy` mirror; `RecommendationsDriftGuard` public shim | `String.split(separator:".")`; `hasPrefix`/`dropFirst` → `startsWith`/`removePrefix` |
| `FlareEngine.swift` | 423 | The flare early-warning: rising-severity spine (REQUIRED), active-trigger count, cycle-phase proximity, bounded (≤ .medium) combine; `FlareCopyText` mirror; `FlareDriftGuard` public shim | Same day/String APIs; `InsightConfidence(rawValue:)` reconstruction |
| `ForbiddenTerms.swift` | 130 | Package-local causal/imperative/alarmist substring lists + `violation(in:)` case-insensitive scan | `lowercased()`→`lowercase()` (Locale-independent); `.contains` |
| `HonestyGate.swift` | 128 | The single choke point: `minSample(for:)` table, confidence floor, dismissal exclusion, min-sample backstop, fail-closed causal-term filter; `apply`/`applyReleaseScan` | `assertionFailure` → `assert` (Kotlin `assert`/`error`); `Set<String>` |
| `InsightsEngine.swift` | 52 | The ONE public producer composing all 5 producers + `HonestyGate.apply` | `Calendar = .current` default → drop (LocalDate) |
| `ExploreSeries.swift` | 269 | `ScaleKind`(6), `ExploreSeriesField` enum, `ExploreSeries` struct (id-only Equatable/Hashable), value accessors, `ExploreSeriesCatalog.all` (built from CaseIterable domains) | `ClosedRange`; `.map(Double.init)`; `.compactMap` |
| `ExploreSeriesExtraction.swift` | 66 | Two kind-gated extractors: `quantitativePoints → [InsightPoint]`, `occurrenceDays → [Date]` | `startOfDay` → `LocalDate` identity (mostly a no-op if input is already LocalDate) |
| `ScatterPairing.swift` | 103 | Both-logged-day scatter cells, count-encoded (no jitter, no r-value); `ScatterPoint`; `PairKey` (exact-Double hashing) | `Dictionary` default-increment; tuple `.sorted{ ($0.x,$0.y) < ... }` |
| `SeriesNormalization.swift` | 82 | Fixed per-scale domains, normalize/denormalize (clamped), honest readout | `ClosedRange<Double>`; `value.rounded()==value` whole-number check |
| `ChartScrubSelection.swift` | 53 | Exact-day scrub resolver (never interpolates); `Resolution` | `Calendar.isDate(_:inSameDayAs:)` → `LocalDate` equality; `startOfDay` |

**Not part of this module (do NOT port here):**
- `Sources/EndoApp/Domain/*.swift` — the APP-side enum copies (SymptomType, BodyRegion, FoodTag, ActivityType, ActivityIntensity, SleepQuality, FlowIntensity, **FactorKind**). These have raw values IDENTICAL to `SnapshotEnums.swift` **except `FactorKind` and `CycleMath`/`CycleSummary`/`FactorDay` which live ONLY app-side.** `FactorKind` (food/activity/sleep/medication/stress/mood/other) is a persistence discriminator consumed by Room's `FactorReading` in **Phase 18**, NOT by the engine — the engine uses the `Factor` sealed interface instead. `[ASSUMED — A1]` The CONTEXT lists "FactorKind" among enums to port, but the `:insights` module's port SOURCE (`SnapshotEnums.swift`) does not contain it; recommend porting the 7 SnapshotEnums enums here and treating `FactorKind` as a Phase-18 `:app` enum. Confirm with the planner.
- `Sources/EndoApp/Content/ClaimsPolicy.swift` — app-side; the `:insights` port mirrors its lists inside `ForbiddenTerms`, and a drift test scans it (read-only).

## Enum Parity Contract (AENG-01 / AENG-05)

**Every Swift enum here is `enum X: String` with NO explicit `= "..."` assignments, so each raw value is exactly the case name.** This is the parity contract — Kotlin enums must carry `raw: String` equal to these strings verbatim (Kotlin case names may be idiomatic e.g. `PELVIC_PAIN`, but `raw` is load-bearing: it appears in `signalKey`, Room columns, and export JSON). `[VERIFIED: read from EndoInsights/Sources/EndoInsights/SnapshotEnums.swift + CyclePhase.swift + Insight.swift]`

| Enum | Count | Raw values (verbatim, in declaration order) | Decode sink |
|------|-------|---------------------------------------------|-------------|
| `SymptomType` | 11 | `pelvicPain`, `dysmenorrhea`, `dyspareunia`, `giEndoBelly`, `urinary`, `fatigue`, `brainFog`, `lowMood`, `headache`, `backLegPain`, `other` | unknown/null → `.other` |
| `BodyRegion` | 10 | `lowerAbdomenPelvis`, `lowerQuadrantLeft`, `lowerQuadrantRight`, `lowerBack`, `hips`, `upperThighs`, `rectalBowel`, `bladder`, `chestDiaphragm`, `shoulder` | unknown/null → `null` |
| `FlowIntensity` | 4 | `spotting`, `light`, `medium`, `heavy` | unknown/null → `null` |
| `FoodTag` | 9 | `dairy`, `glutenWheat`, `caffeine`, `alcohol`, `highSugar`, `highFODMAP`, `redProcessedMeat`, `largeMeal`, `other` | unknown/null → `.other` |
| `ActivityType` | 5 | `walking`, `cardio`, `strength`, `yogaStretching`, `other` | unknown/null → `.other` |
| `ActivityIntensity` | 3 | `low`, `medium`, `high` | unknown/null → `null` |
| `SleepQuality` | 3 | `poor`, `okay`, `good` | unknown/null → `null` |
| `CyclePhase` | 4 | `menstrual`, `follicular`, `ovulatory`, `luteal` | (no failable init) |
| `InsightKind` | 5 | `trend`, `cyclePhase`, `correlation`, `recommendation`, `flare` | Codable |
| `TrendWindow` | 4 | `last7`, `last30`, `last90`, `allTime` | Codable |
| `ScaleKind` | 6 | `ordinal0to10`, `ordinal1to5`, `continuousHours`, `continuousMinutes`, `ordinalBleeding`, `presenceEvent` | — |

`InsightConfidence` is `Int`-backed & `Comparable`: `none = 0`, `low = 1`, `medium = 2`, `high = 3` (raw INTEGERS, not strings). Order matters (`none < low < medium < high`).

**`FactorKind` (app-side only, port SOURCE `Sources/EndoApp/Domain/FactorKind.swift`, 7 cases):** `food`, `activity`, `sleep`, `medication`, `stress`, `mood`, `other`. Belongs to Phase 18 unless the planner decides otherwise (see A1).

### `signalKey` / `Factor.key` contract (persisted → parity-critical)

These strings are folded into `Insight.id` (DeterministicUUID) and are the dismissal keys persisted in `DismissedInsight`/`CachedInsight` (Phase 18). Divergence silently breaks dismissal-survival across recompute. `[VERIFIED: read from Factor.swift, TrendsEngine.swift, CorrelationEngine.swift, CyclePhaseMapper.swift, RecommendationsEngine.swift, FlareEngine.swift, ExploreSeries.swift]`

- **`Factor.key`:** `food.<tag.raw>`, `activity.present`, `activity.<type.raw>`, `sleep.poor`, `stress.high`, `mood.low`, `medication.taken`, `prn.taken`, `cyclePhase.<phase.raw>`
- **Trend signalKeys:** `symptom.<type.raw>.frequency`, `symptom.<type.raw>.severity`, `factor.stress.mean`, `factor.mood.mean`, `factor.sleep.poor.frequency`, `bleeding.frequency`; insufficiency notice appends `.insufficient` (e.g. `symptom.pelvicPain.frequency.insufficient`)
- **CyclePhase association signalKey:** `cyclePhase.<phase.raw>.symptom.<type.raw>`
- **Correlation signalKeys:** `correlation.<factor.key>.symptom.<type.raw>` and `correlation.<factor.key>.bleeding`
- **Recommendation signalKey:** `recommendation.<correlation-stem>` (the `correlation.` prefix stripped)
- **Flare signalKey:** `flare.warning` (both the warning AND the insufficiency notice share this one key)
- **ExploreSeries ids:** `symptom.<type.raw>.severity`, `pain.<region.raw>.severity`, `food.<tag.raw>`, `sleep.hours`, `activity.minutes`, `stress`, `mood`, `bleeding.intensity`, `bleeding.event`, `clots`, `prn`, `medication.taken`

### `DeterministicUUID` (parity-critical — Insight `Equatable` fixtures depend on it)

`Insight.id` is derived, never random, so `[Insight]` stays exactly `Equatable` for fixtures. Kotlin must reproduce bit-for-bit `[VERIFIED: read from Insight.swift]`:
- Key string: `"com.endoapp.endoinsights.insight.v1|<kind.raw>|<window.raw>|<signalKey>"`, UTF-8 bytes.
- Two FNV-1a 64-bit lanes: `lane0` offset `0xcbf29ce484222325`, `lane1` offset `0x9e3779b97f4a7c15`, prime `0x00000100000001b3`. `lane0 = (lane0 xor byte) * prime`; `lane1 = (lane1 + byte) * prime` — **wrapping unsigned 64-bit** (Swift `&*`/`&+`). In Kotlin use **`ULong`** (its `*`/`+` wrap silently — exactly the Swift wrapping semantics). Do NOT use `Long` (signed) for the multiply/add without care; ULong is the clean map.
- Emit 16 bytes little-endian (lane0 bytes 0-7, lane1 bytes 8-15), then stamp RFC-4122: `b[6] = (b[6] and 0x0F) or 0x50`; `b[8] = (b[8] and 0x3F) or 0x80`. Build `java.util.UUID` from the 16 bytes (careful: construct via two `Long` halves in the SAME byte order Swift lays them out).

## Statistical Algorithms — bit-parity requirements (AENG-06)

Every function in `CorrelationStats` is pure `Double`/`Int` arithmetic. The following must be reproduced exactly, and the divergence points must be pinned by the fixture pack. `[VERIFIED: read from CorrelationStats.swift + CorrelationEngine.swift]`

1. **`lgamma` — NO Kotlin/JVM stdlib equivalent.** Swift binds `lgamma as (Double)->Double` = C libm `lgamma`. Kotlin/JVM has neither `Math.lgamma` nor `kotlin.math.lgamma`. **Hand-roll a Lanczos approximation** (g=7, standard 9-coefficient set) as a pinned private helper. It will NOT be bit-identical to libm. Used only inside `lnChoose` → `hypergeometricProbability` → `exp` → Fisher p-values. **Assert Fisher/lnChoose against Swift golden values with a tolerance (~1e-9 relative).** Anchor: `lgamma(5.0) ≈ 3.17805383 == ln(24)`. `[CITED: known Lanczos parameters; verify tolerance against Swift-produced golden fixtures]` — **HIGH-risk divergence; treat as its own porting task with golden fixtures generated from Swift.**
2. **`erf` — NO Kotlin/JVM stdlib equivalent.** Swift `erf` = libm. Hand-roll a high-precision rational/Chebyshev approximation (e.g. the Cody/W.J. or Numerical-Recipes `erfc` with ~1e-12 accuracy — NOT the low-accuracy Abramowitz-Stegun 7.1.26, which is only ~1e-7 and would drift Mann-Whitney p's). `normalCDF(x) = 0.5*(1 + erf(x/sqrt(2)))`. Feeds Mann-Whitney p and (indirectly) the confidence bands. Tolerance-test against Swift golden values. **HIGH-risk divergence.**
3. **Cliff's delta** `(#{w>o} − #{w<o})/(m·n)` — pure integer/double loop, **exact and trivially portable.** nil on either empty group.
4. **meanDifference / riskDifference / riskRatio** — pure arithmetic, exact. Watch the `guard reference > 0` in riskRatio (returns null).
5. **Fisher exact two-tailed** — hypergeometric sum over the feasible `a` range `max(0, c1−r2) … min(r1, c1)`; includes tables with `p <= observed * (1 + 1e-7)` (the epsilon is load-bearing — reproduce exactly). Numerically depends on lgamma (see 1).
6. **Mann-Whitney U (tie-corrected, NO continuity correction)** — `U = #{w>o} + 0.5·#{w==o}`; tie term `Σ(t³−t)` over tied groups in `(W+O).sorted()`; `σ_U² = (m·n/12)·((m+n+1) − Σ(t³−t)/((m+n)(m+n−1)))`; degenerate σ → p = 1.0; `z=(U−μ_U)/σ_U`; `p = 2·(1−Φ(|z|))`. **NO `±0.5` continuity term** (matches `scipy mannwhitneyu(method='asymptotic', use_continuity=False)`). Sort & tie-grouping: the values are integers-as-doubles (no NaN in normal flow), but the **tied-ranks fixture (AENG-06)** must confirm Kotlin's `(W+O).sorted()` groups ties identically — use `List<Double>.sorted()` (stable, natural order); avoid `DoubleArray.sort()` if NaN could ever enter (it total-orders NaN differently).
7. **Benjamini-Hochberg** — indices sorted ascending by value, **ties broken by original index** (`p[$0] != p[$1] ? p[$0] < p[$1] : $0 < $1`); largest rank k with `p_(k) ≤ (k/K)·q` (q=0.10); return all indices with rank ≤ k. Kotlin: `indices.sortedWith(compareBy({ pValues[it] }, { it }))` reproduces the stable order. Empty → empty set.
8. **Confidence bands** — `highSampleFloor = 30`, `mediumSampleFloor = 20`; correlation floors `severityMediumFloor = 0.33`, `severityLargeFloor = 0.474`, `presenceMediumFloor = 0.20`, `presenceLargeFloor = 0.35`; `fdrQ = 0.10`, `topN = 5`. `HonestyGate.minSample`: trend 3, cyclePhase 2, correlation 10, recommendation 10, flare 21. `Factor.highStressThreshold = 7`, `lowMoodThreshold = 2`, `perGroupMinimum = 3`. `TrendsEngine.minLoggedDays = 3`, `directionBand = 0.5`. `RecommendationsEngine.topN = 3`.

## Standard Stack

### Core (all pinned in STACK.md — do not re-open)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin (JVM) | 2.3.21 | `:insights` language | Locked; pairs with AGP 9.2 embedded KGP; `kotlin("jvm")` only |
| Gradle | 9.4.1+ (wrapper) | Build | AGP-9.2 minimum; Kotlin DSL + version catalog |
| JDK toolchain | 21 | Build/test JVM | JUnit 6 needs 17+; one toolchain, no drift; `jvmToolchain(21)` |
| JUnit Jupiter | 6.1.1 | `:insights` test framework | `useJUnitPlatform()`; 157 plain `@Test` map 1:1 |
| kotlinx-serialization-json | 1.11.0 | `Insight`/enum `@Serializable` + golden-JSON drift | Replaces Swift `Codable`; first-party-adjacent, compile-time-safe |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlin-serialization plugin | 2.3.21 (= Kotlin) | `@Serializable` codegen | Apply to `:insights` if the Insight golden-JSON/Codable tests use kotlinx-serialization |
| `java.time` | JDK 21 (built-in) | `LocalDate` day identity | **Zero dependency** — JDK, not `android.*`; the boundary lint permits it |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-rolled `lgamma`/`erf` | `org.apache.commons:commons-math3` | Adds a dependency to the zero-dependency audit surface — REJECTED; hand-roll instead |
| JUnit Jupiter | Kotest | New assertion dialect + dependency for no capability the 1:1 port needs — REJECTED |
| kotlinx-serialization for Insight | Manual JSON in tests only | If the only serialization need is the enum-raw-values golden JSON (pure string data), a hand-written JSON compare avoids the plugin — but Insight is `Codable` in Swift (InsightCodableTests, 6 cases) so a real codec is cleaner. Prefer kotlinx-serialization |
| `LocalDate` day identity | `Instant`/epoch math in `:insights` | REJECTED by CONTEXT + Pitfall 11 — epoch math reintroduces DST bugs |

**Installation:** the module needs no new coordinates beyond the STACK.md catalog. `:insights/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)   // only if Insight/golden JSON uses kotlinx-serialization
}
kotlin { jvmToolchain(21) }
dependencies {
    // NO android/androidx; NO project(":app") — one-way arrow
    implementation(libs.kotlinx.serialization.json)   // if used
    testImplementation(libs.junit.jupiter)
}
tasks.test { useJUnitPlatform() }
```

## Package Legitimacy Audit

This phase installs **no new external packages** beyond the coordinates already pinned and version-verified in STACK.md (verified there against developer.android.com / kotlinlang.org / Maven within the research window). All are first-party JetBrains/JUnit Maven artifacts. No npm/PyPI/crates ecosystem is involved; no `package-legitimacy check` seam applies to Maven coordinates and no registry tool was run in this session.

| Package | Registry | Source | Legitimacy | Disposition |
|---------|----------|--------|------------|-------------|
| `org.jetbrains.kotlin:*` 2.3.21 | Maven Central | JetBrains (official) | Trusted first-party | Approved (pinned in catalog) |
| `org.junit.jupiter:junit-jupiter` 6.1.1 | Maven Central | JUnit team (official) | Trusted first-party | Approved |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.11.0 | Maven Central | JetBrains (official) | Trusted first-party | Approved |

**Packages removed due to [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none. `[ASSUMED — A6]` Coordinate legitimacy is inherited from STACK.md's verification (MEDIUM there); the catalog-audit test (AENG-07) is the standing guard that no analytics/auth/backend coordinate is ever added.

## Architecture Patterns

### Module layout (file-for-file, Claude's Discretion realized)
```
android/insights/
├── build.gradle.kts                 # kotlin("jvm") only
└── src/
    ├── main/kotlin/com/endoapp/insights/
    │   ├── SnapshotEnums.kt          # 7 enums, raw-value parity
    │   ├── CyclePhase.kt             # CyclePhase(4) + PhaseDay
    │   ├── Factor.kt                 # sealed interface Factor + FactorState + keys/thresholds
    │   ├── DaySnapshot.kt            # DaySnapshot, SymptomPoint, PainPoint (NaN-unrepresentable)
    │   ├── Insight.kt                # InsightKind/TrendWindow/InsightConfidence/InsightPoint/Insight + DeterministicUUID
    │   ├── CorrelationStats.kt       # + hand-rolled lgamma/erf helpers
    │   ├── TrendsEngine.kt
    │   ├── CyclePhaseMapper.kt
    │   ├── CorrelationEngine.kt
    │   ├── RecommendationsEngine.kt  # + RecommendationCopy + RecommendationsDriftGuard
    │   ├── FlareEngine.kt            # + FlareCopyText + FlareDriftGuard
    │   ├── ForbiddenTerms.kt
    │   ├── HonestyGate.kt
    │   ├── InsightsEngine.kt         # THE one public producer
    │   ├── ExploreSeries.kt          # ScaleKind/ExploreSeriesField/ExploreSeries/Catalog
    │   ├── ExploreSeriesExtraction.kt
    │   ├── ScatterPairing.kt
    │   ├── SeriesNormalization.kt
    │   └── ChartScrubSelection.kt
    └── test/
        ├── kotlin/com/endoapp/insights/   # 18 test files, 1:1 with Swift
        │   ├── SnapshotFixtures.kt / SeriesTestSupport.kt   (fixtures, 0 cases — port FIRST)
        │   ├── BoundaryLintTests.kt
        │   ├── EnumRawValueParityTests.kt  (NEW — drift test A)
        │   ├── ClaimsPolicyDriftTests.kt   (NEW — drift scan of Swift ClaimsPolicy)
        │   ├── NumericDivergenceTests.kt   (NEW — AENG-06 fixture pack)
        │   └── … (per-file mirrors)
        └── resources/
            └── enum-raw-values.golden.json (drift test B — unconditional)
    PARITY.md                          # the ledger (exit criterion)
```

### Pattern 1: `Factor` enum-with-associated-values → Kotlin sealed interface
Swift `Factor` cases carry payloads (`food(FoodTag)`, `activity(ActivityType)`, `cyclePhase(CyclePhase)`) plus payload-free cases. Kotlin realization `[CITED: CONTEXT lock + Factor.swift]`:
```kotlin
sealed interface Factor {
    val key: String
    data class Food(val tag: FoodTag) : Factor { override val key get() = "food.${tag.raw}" }
    data object ActivityPresent : Factor { override val key get() = "activity.present" }
    data class Activity(val type: ActivityType) : Factor { override val key get() = "activity.${type.raw}" }
    data object SleepPoor : Factor { override val key get() = "sleep.poor" }
    data object StressHigh : Factor { override val key get() = "stress.high" }
    data object MoodLow : Factor { override val key get() = "mood.low" }
    data object MedicationTaken : Factor { override val key get() = "medication.taken" }
    data object PrnTaken : Factor { override val key get() = "prn.taken" }
    data class CyclePhaseFactor(val phase: CyclePhase) : Factor { override val key get() = "cyclePhase.${phase.raw}" }
}
```
Keep `state(of:on:)` / `state(of:on:phase:)` / `hasAnyLog` as pure functions (companion object or top-level). `Equatable` in Swift → data class/`data object` structural equality in Kotlin.

### Pattern 2: Day identity is `LocalDate`, injected `Calendar` is dropped
Swift injects `today: Date` + `calendar: Calendar = .current` and buckets via `calendar.startOfDay`. Kotlin uses `LocalDate` end-to-end: signatures become `insights(from: List<DaySnapshot>, asOf today: LocalDate)`. Day arithmetic: `calendar.date(byAdding:.day, value:n)` → `date.plusDays(n.toLong())`; `dateComponents([.day], from:a, to:b).day` → `ChronoUnit.DAYS.between(a, b)` (or `b.toEpochDay() - a.toEpochDay()`); `startOfDay` becomes a no-op (LocalDate is already a day); `isDate(_:inSameDayAs:)` → `==`. This is the idiomatic simplification the CONTEXT mandates and it eliminates the whole DST class of Pitfall 11 — but the **DST/non-UTC fixture (AENG-06) still runs the JVM test in a non-UTC zone** to prove no `Instant`/epoch math leaked in.

### Anti-Patterns to Avoid
- **"Improving" the ported math** — the Swift suite is the oracle; refactoring algorithms during the port makes drift undetectable (ARCHITECTURE Anti-Pattern 6). Port mechanically; idiomatic cleanup only where a ported test pins behavior.
- **`kotlin.math.round` for `.rounded()`** — it is ties-to-even (banker's), Swift `.rounded()` is ties-away-from-zero. Wrong on `.5` ties. Hand-roll (see Pitfalls).
- **Boxed `Double` equality / `distinct()` on Double-bearing types** — `NaN == NaN` is true and `0.0 == -0.0` is false under boxed `.equals`. Keep NaN unrepresentable at `DaySnapshot` and prefer primitive comparisons.
- **`DoubleArray.sort()` in rank/selection code** — dual-pivot quicksort, unstable, total-orders NaN differently from Swift `<`. Use `List<Double>.sorted()` with explicit comparators.
- **`android.`/`androidx.` imports** — boundary-lint failure. `java.time`, `java.util.UUID`, `kotlin.*` only.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Day/DST arithmetic | epoch-millis math (`/86_400_000`, `Instant.plus(DAYS)`) | `java.time.LocalDate` + `ChronoUnit` | Pitfall 11 — DST/nonexistent-midnight/zone bugs; CONTEXT locks LocalDate |
| JSON codec | manual string building | kotlinx-serialization | Replaces Swift `Codable`; drift-guarded golden JSON |
| UUID formatting | custom string layout | `java.util.UUID` from 16 bytes | Only the *derivation* (FNV-1a) is hand-rolled; the container is stdlib |
| Test running / case counting | custom harness | JUnit Jupiter + `useJUnitPlatform()` | JUnit XML gives the per-file case count for the PARITY ledger |

**Must hand-roll (no stdlib exists — the exceptions):** `lgamma`, `erf`, and a Swift-parity `roundHalfAwayFromZero`. These are the only bespoke numeric primitives; everything else uses `kotlin.math`.

**Key insight:** the honesty logic (withholding, absence≠0, non-causal copy) is the product's ethical core and is already fully specified by the Swift source + tests — do NOT re-derive judgement calls (thresholds, floors, copy). Copy them verbatim as pinned constants.

## Common Pitfalls (Numeric Divergence — AENG-06 fixture pack)

### Pitfall 1: `.rounded()` half-away-from-zero vs Kotlin rounding
**Where:** `CorrelationEngine.makeInsight` (`abs(meanDiff).rounded()`, `(rr*10).rounded()/10`), `SeriesNormalization.readout`/`normalize` whole-number checks.
**Swift:** `Double.rounded()` = half **away from zero**. **Kotlin:** `kotlin.math.round` = half-to-**even**; `Math.round` = half-**up** (toward +∞). All three disagree on ties across signs.
**Fix:** pinned helper `fun Double.roundedAwayFromZero() = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)`. Fixture: exact `.5` ties, positive and negative.

### Pitfall 2: `String(format:"%.1f")` locale
**Where:** `TrendsEngine.format`, `CorrelationEngine.formatLift`, `RecommendationsEngine`/Flare copy interpolation.
**Swift:** C-locale (period). **Kotlin:** `String.format("%.1f", x)` uses the **default locale** → German `3,5`. Diverges by device language and corrupts the copy audit + PDF (Phase 22+).
**Fix:** always `String.format(Locale.ROOT, "%.1f", v)`. Structural lint bans `String.format(` without a `Locale` first arg. `%.1f` half-even vs Swift is a secondary concern — pin with a fixture on `x.05` values.

### Pitfall 3: Mann-Whitney tied ranks
**Where:** `CorrelationStats.mannWhitneyP` — `(W+O).sorted()` tie-grouping and `w == o` counting.
**Fix:** finite integer-severities means no NaN in normal flow; still, fixture with tied severities across both groups (e.g. `[3,3,3]` vs `[3,3,3]` → degenerate σ → p=1.0; `[5,5,4]` vs `[4,4,4]`) to confirm identical tie term and U. Use primitive `Double` `==` and `List.sorted()`.

### Pitfall 4: NaN and ±0.0 at the `DaySnapshot` boundary
**Where:** `DaySnapshot` Double fields (`activityMinutes`, `sleepHours`), data-class `equals`.
**Fix (CONTEXT lock):** make NaN **unrepresentable** — reject/normalize NaN in the `DaySnapshot` constructor (or require callers to). Fixture: NaN-in-series asserts the engine filters/rejects at the boundary; `-0.0` vs `0.0` asserted to behave as Swift IEEE (`0.0 == -0.0` true) — note data-class `equals` would make them unequal via boxing, so prefer explicit field comparison or canonicalize `-0.0 → 0.0` on construction.

### Pitfall 5: DST / non-UTC day boundaries
**Where:** all engines (bucketing, period runs, phase windows).
**Fix:** `LocalDate`-only identity removes the bug class; still, run at least one JVM test with `-Duser.timezone=Europe/Berlin` (or set the default zone in-test) and one in `Pacific/Apia`, asserting bucketing/period-run/phase-window outputs are identical to the UTC run — proving no `Instant`/`ZonedDateTime`/epoch math sneaked in.

### Pitfall 6: `lgamma`/`erf` accuracy drift
**Where:** Fisher exact, normalCDF/Mann-Whitney.
**Fix:** generate golden values from the shipped Swift engine for a spread of inputs; assert the Kotlin hand-rolled versions within a documented relative tolerance (~1e-9). This is the one place "exact parity" is replaced by "tolerance parity" — document it in PARITY.md and the fixture header.

## State of the Art

| Old (Swift/iOS) | New (Kotlin/JVM) | Impact |
|-----------------|------------------|--------|
| `Date` + injected `Calendar` day math | `java.time.LocalDate` | Zone-free day identity; DST bug class eliminated by construction |
| `lgamma`/`erf` via platform libm | hand-rolled Lanczos/rational approx | Tolerance parity, not bit parity — golden-fixture gated |
| Swift Testing `@Test`/`#expect` | JUnit Jupiter `@Test`/`assertEquals` | 1:1 (no parameterized cases exist to expand) |
| `Codable`/`JSONEncoder` | kotlinx-serialization | Round-trip tests independent; keep golden JSON to string data (avoid `Date` encoding differences) |

## Test Inventory (AENG-03) — 157 `@Test`, all plain (zero parameterized)

`[VERIFIED: grep of EndoInsights/Tests/EndoInsightsTests — 0 files use @Test(arguments:)]`. **The CONTEXT's "use `@ParameterizedTest` to preserve per-argument counts" is precautionary: there are no argument-based cases, so the mapping is a straight 1:1 `@Test → @Test`.** Two files carry fixtures only (0 cases) and MUST be ported first.

| Swift test file | @Test | Notes / honesty-acceptance role |
|-----------------|-------|--------------------------------|
| `SnapshotFixtures.swift` | 0 | **Fixture builders** (`Fixtures.day`, `Fixtures.calendar`, `suppressedNoBleedingFixture`, …). Port FIRST — the whole suite depends on it |
| `SeriesTestSupport.swift` | 0 | Chart-prep fixtures. Port before chart-prep tests |
| `BoundaryLintTests.swift` | 1 | Scans package sources import only Foundation → Kotlin: no `android.`/`androidx.` (AENG-04) |
| `ChartScrubSelectionTests.swift` | 7 | Exact-day scrub, gap≠zero |
| `ContractTests.swift` | 7 | Cross-engine contract invariants |
| `CorrelationEngineTests.swift` | 12 | **BH-FDR flood** + three-AND gate + top-5 determinism |
| `CorrelationStatsTests.swift` | 26 | **The stats oracle** — hand-computed lgamma/erf/Fisher/Mann-Whitney/Cliff/BH values |
| `CyclePhaseMapperTests.swift` | 8 | Phase windows, irregular short-circuit |
| `ExploreSeriesCatalogTests.swift` | 9 | Catalog completeness, id uniqueness |
| `ExploreSeriesExtractionTests.swift` | 10 | Quantitative vs presence gating, gap≠0 |
| `FlareEngineTests.swift` | 6 | Rising-spine required, bounded ≤ medium, insufficiency notice |
| `HonestyGateTests.swift` | 16 | **Under-sample withholding, confidence floor, fail-closed forbidden-term scan** |
| `InsightCodableTests.swift` | 6 | Codable round-trip → kotlinx-serialization |
| `InsightsEngineTests.swift` | 14 | End-to-end composition + gate (BH-FDR flood at the public boundary) |
| `RecommendationsEngineTests.swift` | 7 | Dominant-per-symptom, ≤3 cap, non-prescriptive copy |
| `ScatterPairingTests.swift` | 7 | Both-logged-only, count-encoded, no r-value |
| `SeriesNormalizationTests.swift` | 6 | Fixed domains, honest readout, clamped round-trip |
| `SmokeTests.swift` | 2 | Trivial sanity |
| `SuppressedFixtureTests.swift` | 2 | **Suppressed-user silence** (no bleeding/cycle trend) |
| `TrendsEngineTests.swift` | 11 | Windows, means (nil excluded), not-enough-data notice |
| **TOTAL** | **157** | |

**Honesty-acceptance fixtures specifically:** `SuppressedFixtureTests` (suppressed silence, 2), `HonestyGateTests` (under-sample withholding + confidence floor + forbidden-term fail-closed, 16), BH-FDR flood inside `CorrelationEngineTests`/`InsightsEngineTests`, absence-vs-zero inside `ExploreSeriesExtractionTests`/`ChartScrubSelectionTests`/`TrendsEngineTests`.

## Validation Architecture

> `nyquist_validation` is enabled (config.json). This is the phase whose ENTIRE deliverable is validation-by-parity.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.1.1 (`useJUnitPlatform()`), JDK 21 toolchain |
| Config file | `android/insights/build.gradle.kts` (`tasks.test { useJUnitPlatform() }`) |
| Quick run command | `./gradlew :insights:test --tests "com.endoapp.insights.<File>"` |
| Full suite command | `./gradlew :insights:test` |

### Phase Requirements → Test Map
| Req | Behavior | Test type | Automated command | Exists? |
|-----|----------|-----------|-------------------|---------|
| AENG-01 | Enum raw values + DaySnapshot honesty | unit | `./gradlew :insights:test --tests "*SnapshotEnums*" --tests "*EnumRawValueParity*"` | ❌ Wave 0 |
| AENG-02 | 6 engines behind one gate | unit | `./gradlew :insights:test --tests "*InsightsEngineTests" --tests "*HonestyGateTests"` | ❌ Wave 0 |
| AENG-03 | 157-case parity | unit | `./gradlew :insights:test` + PARITY.md recount | ❌ Wave 0 |
| AENG-04 | No android/androidx imports | unit (source scan) | `./gradlew :insights:test --tests "*BoundaryLintTests"` | ❌ Wave 0 |
| AENG-05 | Enum + ClaimsPolicy drift red | unit (Swift-source scan + golden JSON) | `./gradlew :insights:test --tests "*DriftTests" --tests "*EnumRawValueParity*"` | ❌ Wave 0 |
| AENG-06 | Numeric-divergence pack green | unit | `./gradlew :insights:test --tests "*NumericDivergenceTests"` | ❌ Wave 0 |
| AENG-07 | Catalog zero-dependency + CI | unit + CI | `./gradlew :insights:test --tests "*VersionCatalogAuditTests"` + `android.yml` | ❌ Wave 0 |

### The five parity mechanisms (the phase's validation spine)
1. **PARITY.md ledger** — table mapping each Swift test file → Kotlin test file → case count on both sides. Verifier recounts: `grep -c "@Test" <swift>` vs the JUnit XML case count. The 157 total is the exit bar.
2. **Golden-JSON drift test (B, unconditional)** — `enum-raw-values.golden.json` committed (generated once from the Swift enums), asserted every run. Keep it to **string data only** (no `Date`) to avoid cross-platform encoding differences.
3. **Swift-source-scan drift test (A, assumption-guarded)** — walks up to repo root, regex-extracts `case` lists from `SnapshotEnums.swift` + `Factor.swift` (+ `CyclePhase.swift`), asserts Kotlin `raw` sets + counts equal. Guard with `Assumptions.assumeTrue(swiftFile.exists())` so `:insights` still passes if built outside the monorepo.
4. **Numeric-divergence fixture pack** — NaN, ±0.0, half ties (±), Mann-Whitney tied ranks, DST/non-UTC day boundaries (run in `Europe/Berlin` + `Pacific/Apia`), lgamma/erf tolerance golden values.
5. **Boundary lint + version-catalog audit** — boundary lint scans `src/main/kotlin` for `android.`/`androidx.`; catalog audit parses `libs.versions.toml` and fails on any analytics/auth/backend/networking coordinate.

### Sampling Rate
- **Per task commit:** `./gradlew :insights:test --tests "<the file(s) touched>"`
- **Per wave merge:** `./gradlew :insights:test` (full module)
- **Phase gate:** full `:insights:test` green + PARITY.md at 157 before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `android/` Gradle skeleton (settings.gradle.kts, root build, `gradle/libs.versions.toml`, wrapper 9.4.1+), `android/`-scoped `.gitignore`, `.github/workflows/android.yml` path-filtered
- [ ] `:insights` module + `build.gradle.kts` (`kotlin("jvm")`, JDK 21, `useJUnitPlatform()`)
- [ ] `SnapshotFixtures.kt` + `SeriesTestSupport.kt` (fixture builders — port before any engine test)
- [ ] Hand-rolled `lgamma`/`erf`/`roundedAwayFromZero` helpers + their golden fixtures (generated from Swift)
- [ ] NEW tests with no Swift counterpart: `EnumRawValueParityTests`, `ClaimsPolicyDriftTests`, `NumericDivergenceTests`, `VersionCatalogAuditTests`, `enum-raw-values.golden.json`
- [ ] `PARITY.md` ledger scaffold

## ClaimsPolicy / ForbiddenTerms Parity (AENG-05)

The Kotlin `ForbiddenTerms` mirrors the app's `ClaimsPolicy` (read-only drift SOURCE `Sources/EndoApp/Content/ClaimsPolicy.swift`). Exact lists to port `[VERIFIED: read from ForbiddenTerms.swift + ClaimsPolicy.swift]`:
- **`ForbiddenTerms.causal`** (18): `trigger, cause, will, diagnose, detect, treat, cure, prevent, because, due to, leads to, leading to, results in, responsible for, contributes to, linked to, worsen, improve, reduce, relieve, aggravate` — this is a **superset** of the app's `ClaimsPolicy.forbiddenVerbs` (`diagnose, detect, treat, cure, prevent, clinically validated, clinically proven, medical-grade, trigger, causes, will`). Drift test asserts app ⊆ package for the causal list, NOT equality.
- **`ForbiddenTerms.imperative`** (9): `take, stop, avoid, start, should, must, use, try, cut` — **byte-equal** to `ClaimsPolicy.forbiddenImperatives`.
- **`ForbiddenTerms.alarmist`** (14): `attack, alarm, alert, emergency, urgent, danger, diagnos, predict, imminent, certain, definitely, episode, will flare, guarantee` — **set-equal** to `ClaimsPolicy.forbiddenAlarmist`.
- `violation(in:)` = case-insensitive substring scan over causal → imperative → alarmist; `lowercased()` → Kotlin `lowercase()` (locale-independent, ASCII-safe here).

The app-side copy mirrors (`RecommendationCopy`, `FlareCopyText`) are also duplicated verbatim in the package and exposed via public `…DriftGuard` shims — port these public shims so a future app-side drift test (Phase 18+) can assert byte identity.

## Security Domain

> `security_enforcement` enabled (ASVS L1). `:insights` is a **pure computation library with no I/O, no network, no persistence, no untrusted deserialization** in this phase — most ASVS categories are structurally N/A. The one security-adjacent surface is the **claims/honesty enforcement** (a regulatory-safety control, not a classic vuln class).

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth surface |
| V3 Session Mgmt | no | No sessions |
| V4 Access Control | no | Consent gating is Phase 18 (`:app`) |
| V5 Input Validation | yes (light) | Defensive `fromRaw()` decoders (unknown→sink, never crash); NaN unrepresentable at `DaySnapshot`; graceful-degrade (`[]`) on wrong-kind chart routing |
| V6 Cryptography | no | `DeterministicUUID` is an identity hash, NOT a security primitive (documented in source) — do not treat as crypto |
| V14 Config | yes | Version-catalog audit test (no analytics/auth/backend coordinates) is the standing supply-chain control |

| Threat pattern | STRIDE | Mitigation |
|----------------|--------|-----------|
| Causal/medical claim reaches a user (regulatory) | Information disclosure / Repudiation | `HonestyGate` fail-closed `ForbiddenTerms` scan (drops in every build) + drift tests keep Kotlin lists ≥ Swift |
| Corrupt stored raw value crashes analysis | Denial of service | Defensive `fromRaw()` decoders → `.other`/null, never throw |
| Silent policy drift between platforms | Tampering | Swift-source drift scan + golden JSON (AENG-05) |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `FactorKind` is app-side (Phase 18), NOT part of the `:insights` port — the port SOURCE `SnapshotEnums.swift` omits it; the engine uses the `Factor` sealed interface | Swift Source Inventory / Enum Parity | Low — if the planner wants `FactorKind` in `:insights` too, add one enum (7 cases: food/activity/sleep/medication/stress/mood/other). Flag for confirmation |
| A2 | Hand-rolled `lgamma` (Lanczos g=7) + high-precision `erf` reach ~1e-9 tolerance vs Swift libm; parity is tolerance-based, not bit-exact, for Fisher/Mann-Whitney | Statistical Algorithms / Pitfall 6 | Medium — if a `CorrelationStatsTests` assertion is exact-equality on a p-value, it must be relaxed to a tolerance; generate golden values from the shipped Swift engine |
| A3 | kotlinx-serialization is used for `Insight` Codable + the golden JSON (vs a hand-written JSON compare) | Standard Stack | Low — either works; kotlinx is cleaner given `InsightCodableTests`. Applying the serialization plugin to `:insights` is fine (pure JVM) |
| A4 | The DST/non-UTC fixture proves LocalDate purity by running the JVM test in `Europe/Berlin` + `Pacific/Apia` | Validation Architecture | Low — standard `TimeZone.setDefault`/`-Duser.timezone` approach |
| A5 | All 157 Swift cases map 1:1 to JUnit `@Test` (no `@ParameterizedTest` expansion needed) | Test Inventory | Low — verified zero `@Test(arguments:)`; if the porter chooses to consolidate multiple `#expect`s into one method, PARITY counts must still resolve to 157 methods |

## Open Questions

1. **`FactorKind` scope (A1).** Port into `:insights` or leave for Phase 18's `:app` enum bridge?
   - Known: engine uses `Factor` sealed interface, not `FactorKind`; `SnapshotEnums.swift` has no `FactorKind`.
   - Recommendation: leave for Phase 18; note the raw values here so the drift test can cover it when it lands.
2. **lgamma/erf tolerance value.** What relative tolerance does `CorrelationStatsTests` need?
   - Known: Swift binds libm; Kotlin must approximate.
   - Recommendation: generate golden p-values from the Swift engine for the exact fixture inputs, pick the smallest tolerance that passes (~1e-9), record in PARITY.md.
3. **Insight `Codable` golden-JSON scope.** Does any golden JSON contain a `Date` (via `InsightPoint`)?
   - Known: Swift `JSONEncoder` encodes `Date` as `secondsSinceReferenceDate` (2001 epoch) by default — not portable.
   - Recommendation: keep the *drift* golden JSON to enum-raw-value string data; let `InsightCodableTests` be a Kotlin-independent round-trip (encode→decode→equal), not a cross-platform byte compare.

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| JDK | `:insights` build/test | assumed via toolchain | 21 (toolchain-pinned) | Gradle toolchain auto-provision |
| Gradle wrapper | build | committed in Wave 0 | 9.4.1+ | — |
| Emulator / device | — | not needed | — | Pure JVM — no emulator ever |

No external services, network, or platform SDKs. This module never touches an emulator (CONTEXT boundary).

## Sources

### Primary (HIGH confidence)
- Port SOURCE read directly: `EndoInsights/Sources/EndoInsights/` (all 19 files) + `EndoInsights/Tests/EndoInsightsTests/` (grep + BoundaryLint/SuppressedFixture read) — exact enum raw values, signalKeys, algorithms, case counts
- `Sources/EndoApp/Domain/*.swift` (8 enums) + `Sources/EndoApp/Content/ClaimsPolicy.swift` — app-side vocabulary + claims lists
- `.planning/phases/17-insights-engine-port/17-CONTEXT.md` — locked decisions
- `.planning/ROADMAP.md` (Phase 17 goal + 5 success criteria), `.planning/REQUIREMENTS.md` (AENG-01..07)

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` — version pins (Kotlin 2.3.21, JUnit Jupiter 6.1.1, kotlinx-serialization 1.11.0, JDK 21, catalog) verified there against developer.android.com/kotlinlang.org/Maven
- `.planning/research/ARCHITECTURE.md` — module layout, test-parity mapping, drift-test patterns
- `.planning/research/PITFALLS.md` — numeric-divergence (Pitfall 10), day/DST math (Pitfall 11), monorepo/CI (Pitfall 15)

### Tertiary (LOW confidence)
- Lanczos `lgamma` / rational `erf` parameters — from numerical-methods knowledge (`[ASSUMED — A2]`); tolerance MUST be pinned against Swift-generated golden values

## Metadata

**Confidence breakdown:**
- Enum / signalKey / stat-constant contract: HIGH — read verbatim from source
- Standard stack: HIGH — pins locked + pre-verified in STACK.md
- Numeric-parity strategy (lgamma/erf/rounding): MEDIUM — divergence points are certain; the exact tolerances need Swift golden values
- Test inventory / parity counts: HIGH — grep-verified 157, zero parameterized
- Architecture / module layout: HIGH — realizes the locked ARCHITECTURE.md layout

**Research date:** 2026-07-08
**Valid until:** ~2026-08-07 (stable — port SOURCE is a frozen iOS tree; stack pins re-checked at Wave 0)
