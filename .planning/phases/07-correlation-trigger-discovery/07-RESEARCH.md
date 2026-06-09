# Phase 7: Correlation & Trigger Discovery - Research

**Researched:** 2026-06-09
**Domain:** On-device statistical association discovery (Foundation-only Swift) over sparse, ordinal, self-tracked endometriosis data, routed through an existing honesty gate.
**Confidence:** HIGH for the codebase seams and the integration mechanics (read directly from source); HIGH for the recommended statistical *design* (it is deliberately conservative descriptive statistics, not a claim about a specific library); MEDIUM for the exact tuning constants (min-sample, effect bands) — these are defensible defaults to be confirmed against the fixtures, and are flagged `[ASSUMED]` in the Assumptions Log.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 … D-18 — research THESE, not alternatives)
- **D-01** Effect-size comparison for symptom-severity vs a factor: mean severity on days WITH the factor vs days WITHOUT — the difference is the effect size; confidence from sample size + magnitude. NOT Pearson/regression.
- **D-02** Rate/lift for categorical factor vs symptom-presence: P(symptom | factor) vs P(symptom | no factor), sample-gated.
- **D-03** Same-day co-occurrence only for v1. Lagged/next-day associations DEFERRED.
- **D-04** Cycle phase (from `CyclePhaseMapper`) is one factor; suppressed users have no phase factor → no phase correlations.
- **D-05** A conservative minimum paired-observation threshold per pair, gated by `minSample(.correlation)`. Below it → not tested.
- **D-06** Multiple-comparison awareness — a documented conservative adjustment (raise the bar with the comparison count; BH/Bonferroni-lite). Spurious low-confidence findings suppressed (SC3).
- **D-07** Effect size reported as a plain magnitude + a plain-language `InsightConfidence`. Every correlation carries effect size + confidence.
- **D-08** Confidence = f(sample size, effect magnitude, comparison-count penalty) → `InsightConfidence`. Below floor → withheld by the gate.
- **D-09** Bounded factor×symptom pairs: food tags, activity, poor sleep, high stress, low mood, medication-taken, cycle-phase — each vs each symptom + bleeding.
- **D-10** Surface only the strongest gated correlations (small top-N); gate withholds the rest.
- **D-11** Spurious/under-sampled WITHHELD, not ranked/shown (the gate, SC3). Phase 7 supplies correct `sampleSize` + confidence.
- **D-12** Non-causal framing — "possible association", "tends to co-occur with"; `ForbiddenTerms` enforced fail-closed.
- **D-13** A pure `CorrelationEngine` in `EndoInsights` (Foundation-only, injected `today`) producing candidate `.correlation` Insights. `InsightsEngine` composes it alongside trends + cycle-phase → through the `HonestyGate`.
- **D-14** Lock `minSample(.correlation)` (Phase-6 placeholder was 14) — confirm/tune (~10–14 paired observations) against fixtures.
- **D-15** Swift Testing fixtures: strong-association (surfaced), spurious/under-sampled (withheld), multiple-comparison (no false-positive flood), non-causal-copy guard, suppressed-user (no cycle-phase correlation). Determinism via injected `today`.
- **D-16** Pure, deterministic, no p-hacking; honest effect sizes; multiple-comparison adjustment documented + tested.
- **D-17** Reuse existing seams: `DaySnapshot`, `CyclePhaseMapper`, `HonestyGate` (live `minSample`/`sampleSize`), `InsightsEngine`.
- **D-18** The effect-size + confidence + multiple-comparison logic is the core new IP — pure, tested, honest.

### Claude's Discretion
- The exact effect-size formula, the confidence/multiple-comparison-adjustment math, the `minSample(.correlation)` value, the top-N cap, and the headline copy templates.

### Deferred Ideas (OUT OF SCOPE)
- The dashboard / Swift Charts rendering of correlations + the dismiss affordance → Phase 8.
- Lagged / next-day associations → future.
- Personalized recommendations derived from correlations → Phase 11.
- Flare early-warning → Phase 12.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INST-03 | User can discover correlations/triggers across logged factors, **gated by minimum-sample size and confidence**. | The `CorrelationEngine` design below (binarized factor enumeration → per-pair effect size → confidence with multiple-comparison penalty → emit `.correlation` Insight with `sampleSize`/confidence) supplies exactly the two gates INST-03 names. The gating itself is enforced by the existing `HonestyGate` (`minSample(.correlation)` + confidence floor), which Phase 7 only feeds. |
</phase_requirements>

---

## Summary

This is a **statistics-DESIGN phase, not a library-integration phase**. There is no Swift "correlation engine" package to add — the entire stack is already first-party Foundation, and the locked decisions (D-01/D-02) explicitly forbid Pearson/regression in favour of a deliberately simple, honest, sparse-data-robust approach. The job is to specify the *math* precisely enough that the planner can write tasks against it, and to confirm that the math feeds the existing `HonestyGate` correctly so spurious findings are **withheld by the gate**, not by new logic in the engine. The value of this phase is **suppressing noise**, not generating findings (D-18).

The recommended design has four moving parts, each computable in plain Swift with no third-party stats library:

1. **Enumerate a bounded set of binary factors** per day from `DaySnapshot` + `CyclePhaseMapper` output (food tags, activity present/type, poor sleep, high stress, low mood, med-taken, PRN, each cycle phase).
2. For each (factor × symptom) pair, restrict to **paired days** (days where BOTH the factor's presence/absence AND the symptom signal are observed — honoring "absence ≠ 0"), compute a **robust effect size**: **Cliff's delta** for ordinal severity vs a binary factor, and a **risk difference + risk ratio** for symptom-presence vs a binary factor.
3. Compute a **deterministic exact/permutation significance** with no RNG and no stats library: a **two-tailed Fisher's exact test** for the 2×2 presence tables, and a **Mann–Whitney U with a normal approximation** (or exact enumeration when n is tiny) for the ordinal-severity case. These are O(n) / closed-form and fully reproducible.
4. Map (sample size, effect magnitude, significance, **multiple-comparison penalty**) onto `InsightConfidence` so that under-sampled, small-effect, and multiple-comparison-inflated findings land **below the gate's `.low` floor** and are withheld. The multiple-comparison correction is a **Benjamini–Hochberg FDR step-up** computed over the full candidate set, translated into a per-pair "passed FDR?" boolean that becomes a hard precondition for any confidence ≥ `.low`.

**Primary recommendation:** Build `CorrelationEngine` as a pure `enum` namespace mirroring `TrendsEngine`/`CyclePhaseMapper` (injected `today`/`calendar`, `Equatable` `[Insight]` out). For every (binary factor × symptom) pair with `pairedDays ≥ minSample(.correlation)`, compute **Cliff's delta** (ordinal severity) or **risk difference** (presence), a **deterministic exact/Mann–Whitney significance**, apply a **Benjamini–Hochberg FDR gate**, then map to `InsightConfidence` via the bands in §"Confidence mapping". Set `sampleSize = pairedDays` so the existing `HonestyGate.minSample(.correlation)` re-enforces the floor. **Lock `minSample(.correlation) = 10**. Emit a small **top-N = 5** of the surviving candidates. All copy is hand-written non-causal prose ("Higher pelvic pain possibly associated with logging dairy") that passes `ForbiddenTerms`.

---

## Architectural Responsibility Map

This is a single-tier, pure-logic phase (one local SPM package, no app/UI/store changes). The "tiers" here are the package's internal responsibility layers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Binarize factors from a day | `CorrelationEngine` (factor extraction) | `DaySnapshot` (data source) | The snapshot carries raw optional signals; the engine owns the binarization thresholds (poor sleep, high stress, etc.). |
| Cycle-phase as a factor | `CyclePhaseMapper` (phase windows) | `CorrelationEngine` (labels each day) | Reuse the Phase-6 phase windows verbatim (D-04/D-17); the engine only reads the per-day phase label. Suppressed → no windows → no phase factor (honesty preserved automatically). |
| Effect size + significance | `CorrelationEngine` (pure stats helpers) | — | The new IP (D-18). Pure functions, independently unit-testable. |
| Multiple-comparison correction | `CorrelationEngine` (FDR over the candidate set) | — | Must see ALL candidates at once → lives in the engine's top-level orchestration, not per-pair. |
| Confidence assignment | `CorrelationEngine` | `HonestyGate` (floor) | Engine computes the band; gate enforces the floor + min-sample backstop. |
| Withholding spurious/under-sampled | `HonestyGate` | `CorrelationEngine` (feeds honest `sampleSize`/confidence) | **No new withholding logic** (D-11/D-17). The engine emits below-floor candidates honestly; the gate drops them. |
| Non-causal copy | `CorrelationEngine` (hand-written prose) | `ForbiddenTerms` / `HonestyGate` (fail-closed scan) | Engine writes descriptive copy; gate proves it stays non-causal. |
| Composition + public API | `InsightsEngine` | — | One added `+ CorrelationEngine.insights(...)` term; signature is already LOCKED (D-16). |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Foundation | system (iOS 18 / Swift 6) | Date math, arrays, `Set`, basic arithmetic | The package is Foundation-only by mandate (SC1). All stats below are closed-form arithmetic — no external math needed. `[VERIFIED: codebase — every existing engine imports only Foundation]` |
| Swift Testing | Xcode 26 toolchain | Fixture/unit tests (`@Test`, `#expect`) | Already the package's test framework (see `Tests/EndoInsightsTests/`). `[VERIFIED: codebase]` |

### Supporting

**None.** This phase deliberately adds **zero dependencies**.

- **Accelerate / vDSP** (mentioned in the project STACK) is **NOT** appropriate here: it is for dense vectorized means/variance, but the recommended methods (Cliff's delta, Fisher's exact, Mann–Whitney) are rank/count operations over tiny n (≤ a few hundred days), where a plain Swift loop is clearer, deterministic, and trivially fast. Importing Accelerate would add surface area for no measurable benefit at this scale. `[VERIFIED: PITFALLS performance note — "stats over a single user's history is trivial"]`

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Cliff's delta (ordinal effect) | Standardized mean difference (Cohen's d) | Cohen's d assumes roughly normal, equal-variance data and is distorted by the 0–10 ordinal scale, sparse n, and outliers typical of pain logs. Cliff's delta is **non-parametric, bounded [-1, +1], robust to non-normality and ties**, and reads naturally as "probability a with-factor day scores higher than a without-factor day." **Recommended.** Keep a plain **mean-difference in raw severity points** ALONGSIDE it for the human-readable headline ("~2 points higher", per D-07) — the mean-diff is the copy, Cliff's delta is the gate. |
| Fisher's exact (2×2 significance) | Chi-square test | Chi-square is unreliable for small expected cell counts (< 5), which is the norm here. Fisher's exact is **exact for any n**, closed-form (hypergeometric), and deterministic. **Recommended.** |
| Benjamini–Hochberg FDR | Bonferroni | Bonferroni (divide α by K) is *very* conservative and would suppress almost everything at the candidate counts here (K can be 80–150 pairs), defeating the differentiator. BH-FDR controls the *expected false-discovery proportion*, is the modern default for many-comparison screening, and is a simple sort + step-up. **Recommended.** Bonferroni is the honest fallback if a reviewer wants maximum conservatism — document both, ship BH. |
| Mann–Whitney U normal approx | Exact permutation enumeration | Exact enumeration of all label permutations is O(2^n) and infeasible past n≈20; the **normal approximation with tie correction** is closed-form and accurate for n ≥ ~10 (which is exactly the `minSample` floor). For n below that the pair isn't tested anyway. **Recommended.** |

**Installation:** None — no packages. (No `## Package Legitimacy Audit` section is required: this phase installs **zero** external packages.)

---

## The Concrete Statistical Design (the make-or-break of an honest engine)

> This section answers the seven research questions with defensible, library-free formulas. Every formula below is implementable as a pure Swift function over `[DaySnapshot]`.

### 1. The Factor model — binarization (answers Q5)

Each day yields a set of **binary factor indicators**. A factor is `present` / `absent` / `unobserved` on a day. **`unobserved` days are excluded from that factor's denominator** (absence ≠ 0 — the core honesty contract from `DaySnapshot.swift` lines 9–12 and 91–94).

| Factor key | Present when | Absent when | Unobserved when |
|------------|--------------|-------------|------------------|
| `factor.food.<tag>` (one per `FoodTag`) | `foodTags.contains(tag)` | day has ≥1 food log but not this tag, OR is a tracked day | `foodTags == []` **and** the day logged nothing food-related — treat empty food as *absent* only if the user logged *something else* that day (the day exists), else `unobserved`. **Recommendation:** treat an empty `foodTags` on a day-with-any-log as **absent** (the user logged but didn't log this food) — this matches how `TrendsEngine` treats frequency denominators (days-with-any-log). `[ASSUMED — confirm against the "spurious flood" fixture]` |
| `factor.activity.present` | `activityType != nil` | day-with-any-log, `activityType == nil` | (rare — a day with truly nothing) |
| `factor.activity.<type>` (per `ActivityType`) | `activityType == that type` | other type or nil on a logged day | — |
| `factor.sleep.poor` | `sleepQuality == .poor` | `sleepQuality == .okay`/`.good` | `sleepQuality == nil` |
| `factor.stress.high` | `stress != nil && stress >= 7` | `stress != nil && stress < 7` | `stress == nil` |
| `factor.mood.low` | `mood != nil && mood <= 2` | `mood != nil && mood >= 3` | `mood == nil` |
| `factor.medication.taken` | `!medicationsTaken.isEmpty` | day-with-any-log, empty | — |
| `factor.prn.taken` | `tookPRN == true` | day-with-any-log, false | — |
| `factor.cyclePhase.<phase>` (per `CyclePhase`) | day's `CyclePhaseMapper` label == that phase | day labelled a different phase | day outside any window OR **suppressed user** (no windows → factor never present → no phase correlation, satisfying D-04 by construction) |

**Binarization thresholds (the discretionary numbers — `[ASSUMED]`, confirm against fixtures):**
- **High stress:** `stress >= 7` on the 0–10 scale (top ~third). `[ASSUMED]`
- **Low mood:** `mood <= 2` on the 1–5 valence scale (bottom two). `[ASSUMED]`
- **Poor sleep:** the existing `SleepQuality.poor` case — no numeric threshold needed (already categorical). `[VERIFIED: codebase — TrendsEngine already uses `sleepQuality == .poor`]`

**Why binarize even ordinal context scales:** D-01/D-02 lock a present/absent factor model. Binarizing stress/mood keeps every factor on the same 2×2 / two-group footing and avoids re-introducing the regression the decisions forbid. The thresholds are the only judgement calls and MUST be unit-asserted constants (no buried magic numbers — the `TrendsEngine.minLoggedDays` precedent).

**Symptom side** (the thing a factor is correlated against), per (factor × symptom) pair:
- **Severity arm (D-01):** the per-day `severity` for that `SymptomType` (nil severities excluded — absence ≠ 0). Two groups: severities on factor-present days vs factor-absent days.
- **Presence arm (D-02):** symptom *logged that day* (`symptoms.contains { $0.type == type }`) as a 0/1, plus **bleeding** as a symptom-like target (`flow != nil`) per D-09.

### 2. Effect size — exact formulas (answers Q1)

#### A) Ordinal severity vs a binary factor → **Cliff's delta** + raw mean-difference

Let `W = [w_1 … w_m]` be the logged severities on **factor-present** days and `O = [o_1 … o_n]` on **factor-absent** days (both with non-nil severity; same-day pairing — each value belongs to exactly one group by that day's factor state).

**Cliff's delta:**

```
δ = ( #{(i,j) : w_i > o_j} − #{(i,j) : w_i < o_j} ) / (m · n)
```

- Range `[-1, +1]`. `δ = +1`: every present-day severity exceeds every absent-day severity. `δ = 0`: no tendency. Ties contribute 0 to the numerator (handled naturally).
- O(m·n) brute force is fine at this scale (m, n ≤ a few hundred). Deterministic, no RNG.
- **Magnitude bands** (Romano et al. conventions, widely used): `|δ| < 0.147` negligible, `0.147–0.33` small, `0.33–0.474` medium, `≥ 0.474` large. `[CITED: standard Cliff's-delta interpretation thresholds]`

**Raw mean-difference (for the human headline, D-07):**

```
meanDiff = mean(W) − mean(O)          // in raw severity points, e.g. +2.1
```

Report `meanDiff` in copy ("severity averaged ~2 points higher on days you logged dairy"); use `δ` (sign + magnitude) for the gate. The two always agree in sign.

#### B) Symptom-presence (or bleeding) vs a binary factor → **risk difference + risk ratio**

Build the 2×2 over **paired days** (the factor observed AND the symptom-target evaluable):

|              | symptom present | symptom absent | row total |
|--------------|-----------------|----------------|-----------|
| factor present | a | b | a+b |
| factor absent  | c | d | c+d |

```
P(symptom | factor)    = a / (a + b)
P(symptom | no factor) = c / (c + d)

riskDifference (RD) = a/(a+b) − c/(c+d)          // primary effect size, range [-1, +1]
riskRatio      (RR) = [a/(a+b)] / [c/(c+d)]        // the "lift"; report when c/(c+d) > 0
```

- **Effect size for the gate = `|RD|`** (bounded, no divide-by-zero pathology). Report **RR/lift in copy** when the denominators allow ("logged ~2× as often on dairy days").
- **Magnitude bands for RD:** `< 0.10` negligible, `0.10–0.20` small, `0.20–0.35` medium, `≥ 0.35` large. `[ASSUMED — defensible defaults for self-tracked rates; confirm against fixtures]`

### 3. Deterministic significance without a library (answers Q6)

The effect size says *how big*; significance says *how surprising under chance*. Both are needed so the multiple-comparison FDR step (Q2) has a p-value to operate on. **Everything below is closed-form / exact-enumeration — no RNG, no permutation sampling, no stats package** → fully deterministic and `Equatable`-stable.

#### A) Presence 2×2 → **Fisher's exact test** (two-tailed)

The probability of a specific table under the hypergeometric null is

```
P(table) = C(a+b, a) · C(c+d, c) / C(n, a+c)      where n = a+b+c+d
```

Two-tailed p = sum of `P(table')` over all tables `table'` (with the same margins) whose probability `≤ P(observed)`. Iterate `a` over its feasible range given fixed margins; this is O(n) in the smaller margin. Use `Double` log-factorials via `lgamma` to avoid overflow. **Verified this session (Xcode 26 / Swift 6, `swift` run):** `import Foundation` exposes `lgamma`, BUT a bare `lgamma(5.0)` is **ambiguous** — two overloads exist (`(Double) -> Double` and the `lgamma_r`-style `(Double) -> (Double, Int)`) and it fails to compile (`ambiguous use of 'lgamma'`). **Disambiguate by binding the scalar overload once:**

```swift
private static let lgammaScalar = (lgamma as (Double) -> Double)   // bind the (Double)->Double overload
// lgammaScalar(5.0) == 3.17805383...  == ln(24) == ln(4!)   ✓  (verified this session)
// alternative: let (v, _): (Double, Int) = lgamma(5.0); use v
```

`erf` has NO such ambiguity and compiles directly. `[VERIFIED: compiled & ran this session — erf direct; lgamma needs the one-line overload bind above]`

#### B) Ordinal severity two-group → **Mann–Whitney U** with normal approximation + tie correction

```
U = #{(i,j) : w_i > o_j} + 0.5 · #{(i,j) : w_i == o_j}     // reuse the Cliff's-delta pair scan
μ_U = m·n / 2
σ_U = sqrt( (m·n / 12) · ((m + n + 1) − tieCorrection) )    // tieCorrection from tied-rank groups
z = (U − μ_U) / σ_U
p = 2 · (1 − Φ(|z|))                                         // Φ = standard normal CDF
```

- `Φ` is computable in closed form from `erf` (Foundation, no ambiguity): `Φ(x) = 0.5 · (1 + erf(x / √2))`, i.e. `erf(x / 2.0.squareRoot())`. `[VERIFIED: compiled & ran this session — `erf(1/√2)=0.6826894921...`]`
- The U pair-counts are the SAME counts Cliff's delta already computed (`δ = (U⁺ − U⁻)/(m·n)`), so this adds almost no work.
- For very small groups (either group < ~8) the normal approximation degrades — but the `minSample` floor (≥10 paired days total, and we additionally require **each group ≥ a small minimum**, see §4) keeps us in the valid regime. Below that, the pair isn't tested.

**Determinism guarantees (Q6):** all three computations are pure arithmetic over the input arrays with no `Date.now`, no `UUID()`, no RNG, no dictionary-iteration-order dependence (sort keys before iterating). Same `[DaySnapshot]` + same injected `today` → identical `Double`s → identical `InsightConfidence` → identical `Equatable` `[Insight]`. This mirrors the existing engines exactly.

### 4. minSample(.correlation) and how it composes (answers Q4)

**Lock `minSample(.correlation) = 10.** Rationale:
- The Phase-6 placeholder was 14 (conservative). 14 is defensible but, combined with the FDR gate + effect-size floor + the per-group minimum, would suppress nearly everything for early users and starve the differentiator. **10 paired observations** is the conventional small-sample floor where the Mann–Whitney normal approximation becomes usable and where a 2×2 has a chance of a non-degenerate Fisher p. `[ASSUMED — D-14 explicitly says "confirm/tune ~10–14 against fixtures"; 10 is the recommended starting value, the under-sampled fixture must use n=9 to prove it's withheld]`

**Two layers of sample gating (both required):**
1. **Pair-level floor (`pairedDays`):** the number of days where the factor is observed (present or absent, NOT unobserved) AND the symptom target is evaluable. `pairedDays = a+b+c+d` (presence arm) or `m+n` (severity arm). This becomes `Insight.sampleSize`.
2. **Per-group minimum (engine-internal, NOT in the gate):** require **each** of the two groups (present/absent, or W/O) to have at least **3** observations. A 10-paired split of 9 vs 1 is statistically empty on one side — the engine drops it *before* emitting (self-gating), so it never reaches the gate. `[ASSUMED — 3 mirrors `TrendsEngine.minLoggedDays`; confirm against fixtures]`

**Composition with the existing gate mechanism (Phase 6, now live):**
- The engine sets `sampleSize = pairedDays` on every emitted `.correlation` Insight.
- `HonestyGate.minSample(for: .correlation)` (currently 14, **change to 10**) re-enforces the floor as a SUPERSET backstop: any candidate the engine forgot to self-gate with `sampleSize < 10` is dropped at the gate (`HonestyGate.swift` lines 90–95). This is the "live `sampleSize` drop" Phase 6 made real.
- **Critical wiring fact:** `minSample(for:)` is the single source of truth. **Change the `.correlation` case in `HonestyGate.minSample(for:)` from `14` → `10`** (line 39) AND use the same constant in the engine's self-gate (expose it, e.g. `HonestyGate.minSample(for: .correlation)`, so there is no second magic number). `[VERIFIED: HonestyGate.swift lines 33–41]`

### 5. Multiple-comparison correction → withholding (answers Q2)

**The honest rule a non-statistician can trust — three hard ANDs, then a confidence band:**

> A correlation is only eligible to be shown (confidence ≥ `.low`) if ALL of:
> 1. **Enough data:** `pairedDays ≥ 10` AND each group ≥ 3.
> 2. **Real effect:** `|Cliff's δ| ≥ 0.33` (severity) **or** `|RD| ≥ 0.20` (presence) — i.e. at least a *medium* effect, not a hair's-breadth difference.
> 3. **Survives multiple-comparison correction:** its Fisher/Mann–Whitney p-value passes a **Benjamini–Hochberg FDR step-up at q = 0.10** computed over **the full set of tested pairs this run**.
>
> Anything failing any of the three is emitted at confidence **below the floor** (`.none`/`.low-but-flagged`) so the `HonestyGate` **withholds** it. Nothing spurious is "ranked then hidden" — it simply never clears the gate.

**Benjamini–Hochberg, concretely (no library):**
1. Collect the p-values `p_(1) ≤ p_(2) ≤ … ≤ p_(K)` of all K tested pairs (sorted ascending; K = number of pairs that passed the sample floor).
2. Find the largest rank `k` such that `p_(k) ≤ (k / K) · q`, with `q = 0.10`.
3. All pairs with rank ≤ `k` "pass FDR"; the rest fail. (Equivalently compute BH-adjusted p = `min over j≥rank of (p_(j)·K/j)` and pass if ≤ q.)
4. `K` is the comparison count — **this is exactly how D-06 "raise the bar by K" is realized**: a single nominally-significant pair among 120 tested needs a much smaller raw p to survive than if only 3 were tested.

**How it maps onto withholding (since the engine emits confidence, not p — Q2):** the BH result is a **per-pair boolean `passedFDR`**. `passedFDR == false` **caps confidence at `.none`** regardless of effect size → the gate withholds it. This is the mechanism that turns "120 noise pairs flood in" into "0 shown" (the multiple-comparison fixture).

**Why not Bonferroni:** at K=120, Bonferroni demands raw p ≤ 0.10/120 ≈ 0.00083, which almost no honest self-tracked pair will reach — the differentiator would show nothing for most users. BH at q=0.10 is the defensible middle: it still crushes the noise flood (the fixture proves it) while letting a genuinely strong, well-sampled association through. Document Bonferroni as the "maximum paranoia" alternative.

### 6. Confidence mapping → InsightConfidence — the exact bands (answers Q3)

Compute confidence **only for pairs that pass all three hard gates above**; everything else is `.none` (→ withheld). For passing pairs:

```
effectMag = |Cliff's δ|   (severity arm)   OR   |RD| (presence arm)
n         = pairedDays
passedFDR = true (precondition)
```

| Band | Condition | Result | Gate outcome |
|------|-----------|--------|--------------|
| **high** | `n ≥ 30` AND `effectMag ≥ large` (δ ≥ 0.474 / RD ≥ 0.35) AND `passedFDR` | `.high` | shown |
| **medium** | `n ≥ 20` AND `effectMag ≥ medium` (δ ≥ 0.33 / RD ≥ 0.20) AND `passedFDR` | `.medium` | shown |
| **low** | `n ≥ 10` AND each group ≥ 3 AND `effectMag ≥ medium` AND `passedFDR` | `.low` | shown (at floor) |
| **none (withheld)** | any of: `n < 10`, a group < 3, `effectMag < medium`, `!passedFDR` | `.none` | **WITHHELD by `HonestyGate`** |

**Confirming spurious/under-sampled fall below the floor (the SC3 acceptance):**
- **Under-sampled** (`n = 9`): fails band 1 of the hard gate → `.none` → withheld. ✓
- **Spurious** (large-looking δ by chance, but `!passedFDR` because it doesn't survive FDR among K comparisons): → `.none` → withheld. ✓
- **Tiny real-but-trivial** effect (`|δ| = 0.1`): fails the medium-effect floor → `.none` → withheld. ✓
- **Multiple-comparison flood**: BH controls FDR; essentially all of K random pairs get `passedFDR == false` → all `.none` → none shown. ✓

The confidence floor in the gate is `.low` (`HonestyGate.confidenceFloor`), and `.none` is below it, so `.none` correlations are dropped at step (c) of `sampleAndFloorFilter` — **no new withholding code needed** (D-11). `[VERIFIED: HonestyGate.swift lines 46, 98–100]`

### 7. Top-N and ordering (D-10)

After the gate, the engine should emit a **small top-N = 5** strongest survivors to avoid flooding (D-10). Rank survivors by a **deterministic key**: `(confidence desc, effectMag desc, n desc, signalKey asc)` — the final `signalKey` tiebreaker guarantees a total order so the output is `Equatable`-stable. `[ASSUMED — N=5 is a reasonable default; confirm with the team. Note: top-N capping can live in the engine OR be left to Phase-8 rendering; recommend the engine caps so the contract is honest at the source. Capping pre-gate vs post-gate matters: cap POST-gate so a withheld spurious never occupies a slot.]`

**signalKey scheme** (stable, dismissal-survivable, deterministic-id source — mirrors `CyclePhaseMapper`'s `"cyclePhase.<phase>.symptom.<type>"`):
```
"correlation.<factorKey>.<symptomTarget>"
  e.g. "correlation.food.dairy.symptom.pelvicPain"
       "correlation.sleep.poor.bleeding"
       "correlation.cyclePhase.luteal.symptom.fatigue"
```

---

## Architecture Patterns

### System Architecture Diagram

```
[DaySnapshot]  ─┐
                │  (1) factor extraction + binarization
                ▼
        per-day binary factor set  ◄──── CyclePhaseMapper.phaseWindows  (cycle-phase factor; suppressed → none)
                │
                │  (2) enumerate bounded (factor × symptomTarget) pairs (D-09)
                ▼
        for each pair: restrict to PAIRED, OBSERVED days (absence ≠ 0)
                │
                ├──(severity arm)─► Cliff's δ + meanDiff + Mann–Whitney p
                └──(presence arm)─► RD + RR + Fisher's exact p
                │
                │  (3) collect all K p-values → Benjamini–Hochberg FDR (q=0.10) → passedFDR per pair
                ▼
        (4) hard gate (n, per-group, effect floor, passedFDR) → InsightConfidence band
                │
                │  build .correlation Insight (signalKey, sampleSize=pairedDays, confidence,
                │  hand-written NON-CAUSAL copy, points=[])
                ▼
        [candidate .correlation Insight]
                │
                │  (5) deterministic sort, cap top-N=5 (post-gate)
                ▼
   InsightsEngine.insights():  trends + cyclePhase + CORRELATION  ──►  HonestyGate.apply(...)
                                                                          │ dismissed-exclusion
                                                                          │ minSample(.correlation)=10 backstop
                                                                          │ confidence floor (.low)
                                                                          │ ForbiddenTerms fail-closed scan
                                                                          ▼
                                                                  gated [Insight]  → (Phase 8 renders)
```

### Recommended Project Structure (additive — package-internal `enum`s mirroring existing files)

```
EndoInsights/Sources/EndoInsights/
├── CorrelationEngine.swift     # NEW — the public-from-InsightsEngine entry `enum`, injected today/calendar
├── Factor.swift                # NEW — Factor descriptor + binarization (thresholds as asserted constants)
├── CorrelationStats.swift      # NEW — pure helpers: cliffsDelta, riskDifference, fisherExact, mannWhitneyP, benjaminiHochberg, normalCDF
├── HonestyGate.swift           # EDIT — change .correlation case 14 → 10 (the single source of truth)
└── InsightsEngine.swift        # EDIT — one added `+ CorrelationEngine.insights(...)` term
```
(Splitting stats into `CorrelationStats.swift` keeps each helper independently unit-testable — the established "each helper independently testable" pattern from `TrendsEngine`.)

### Pattern 1: Pure injected-`today` engine `enum` (mirror `CyclePhaseMapper`)
**What:** `enum CorrelationEngine` with a single `static func insights(from: [DaySnapshot], asOf today: Date, calendar: Calendar = .current) -> [Insight]`, package-internal, no store/UI/clock/RNG.
**When to use:** always — it is the locked contract (D-13/D-17).
**Example:**
```swift
// Mirrors CyclePhaseMapper.insights signature exactly (CyclePhaseMapper.swift:31).
enum CorrelationEngine {
    static func insights(from snapshots: [DaySnapshot], asOf today: Date, calendar: Calendar = .current) -> [Insight] {
        // 1. suppressed short-circuit for the cycle-phase factor is automatic:
        //    CyclePhaseMapper returns [] windows under suppression → phase factor never present.
        // 2..5 as in the diagram. Sort keys before iterating (determinism).
    }
}
```

### Pattern 2: Reuse `CyclePhaseMapper` phase windows verbatim (D-04/D-17)
**What:** call the existing `CyclePhaseMapper.phaseWindows(...)` / `phase(for:in:)` (already package-internal, `static`) to label each day with its `CyclePhase`, then treat each phase as a binary factor. Suppressed/irregular/under-sampled streams return no windows → no phase factor → no phase correlation, satisfying D-04 **by construction** (no extra suppression code).
**When to use:** for the `factor.cyclePhase.*` family only.

### Anti-Patterns to Avoid
- **Re-introducing regression/Pearson** (explicitly forbidden by D-01/D-02). The whole point is robustness on sparse ordinal data.
- **Computing FDR per-pair instead of over the whole candidate set** — FDR is meaningless without K. Collect all p-values first.
- **Capping top-N before the gate** — a withheld spurious must not occupy a top-N slot. Cap post-gate.
- **Coercing `nil` to 0** — a nil severity/stress/mood is `unobserved`, excluded from the group, never a 0. (The single most important honesty invariant; `DaySnapshot.swift:9–12`.)
- **Dictionary-iteration-order dependence** — always `.sorted()` keys before building output, or `[Insight]` Equatability breaks (Pitfall 4 / `Insight.swift:8–11`).
- **Causal copy** — no "trigger/cause/will/worsen/linked to/…". Hand-write "possibly associated with" / "tended to co-occur with"; the gate fail-closes on a violation.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Deterministic `Insight.id` | A new UUID scheme | `Insight.derived(kind:window:signalKey:…)` | The deterministic FNV id derivation already exists (`Insight.swift:163–191`); just supply a stable `signalKey`. |
| Cycle-phase labelling | Re-derive phase windows | `CyclePhaseMapper.phaseWindows` / `phase(for:in:)` | Verbatim reuse (D-17); also inherits the suppressed/irregular honesty gates for free. |
| Withholding spurious findings | New filter logic | `HonestyGate.apply(...)` | The gate already does min-sample + floor + dismissal + causal-scan (D-11). Feed it honest `sampleSize`/confidence. |
| Min-sample constant | A second `14`/`10` literal in the engine | `HonestyGate.minSample(for: .correlation)` | Single source of truth — change it once (line 39). |
| Non-causal copy enforcement | A new term list | `ForbiddenTerms` (via the gate) | Already mirrors the app's `ClaimsPolicy`, fail-closed in release. |
| Normal CDF / erf / lgamma | A polynomial approximation | `import Foundation` → `erf` (direct), `lgamma` (bind the `(Double)->Double` overload) | Available through Foundation's C math; deterministic; keeps it library-free. `lgamma` MUST be disambiguated (see Fisher §) — a bare call won't compile. |

**Key insight:** Phase 7 adds **one producer and the stats math**, and *nothing else* — withholding, ids, copy enforcement, cycle phases, and the min-sample mechanism are all existing seams. The temptation to build a parallel "correlation honesty layer" is the anti-pattern; the gate is the choke-point (D-11/D-17).

---

## Common Pitfalls

### Pitfall 1: The multiple-comparison false-positive flood (THE phase pitfall)
**What goes wrong:** scanning ~9 factor families × 11 symptoms + bleeding × multiple food/activity sub-tags = **80–150 pairs**; with no correction, ~5–10% will look "significant" by chance, and the app confidently shows garbage — the exact trust-collapse Pitfall 3 in PITFALLS.md warns about.
**Why it happens:** naive per-pair thresholds ignore K.
**How to avoid:** the BH-FDR gate over the full candidate set (§5). The `multipleComparisonFloodFixture` (random, independent factors/symptoms) MUST yield **zero** shown correlations.
**Warning signs:** a fixture of pure-noise data surfaces any correlation; confidence assigned before FDR.

### Pitfall 2: `nil` coerced to 0 corrupts the effect size
**What goes wrong:** treating an unlogged severity/stress/mood as 0 inflates or deflates a group mean and fabricates an association.
**Why it happens:** `compactMap` forgotten; an unobserved factor day counted as "absent".
**How to avoid:** strict three-state factor (`present`/`absent`/`unobserved`); exclude `unobserved` from BOTH groups; `compactMap` severities. (The existing `TrendsEngine` already models this — mirror it.)
**Warning signs:** a fixture where a symptom is logged but the factor was never logged still produces a correlation.

### Pitfall 3: Substring causal-term self-collision in copy
**What goes wrong:** hand-written copy contains a forbidden substring (e.g. "**cause**" inside "be**cause**", or "linked to", or "worsen"), and the gate fail-closes → the correlation is silently withheld even though the stats were valid.
**Why it happens:** the `ForbiddenTerms` scan is a case-insensitive substring match (`ForbiddenTerms.swift:14–21`).
**How to avoid:** write copy as plain co-occurrence ("possibly associated with", "tended to co-occur with", "logged more often on … days"); **add every emitted correlation headline+detail to the `allEmittedInsights_haveZeroForbiddenTerms` scan in `HonestyGateTests`** (mandated by the file's own comment, lines 9–11 of `HonestyGateTests.swift`).
**Warning signs:** a valid strong-association fixture emits nothing; a copy template uses "reduce/improve/linked".

### Pitfall 4: Non-determinism via dictionary order or float drift
**What goes wrong:** building `[Insight]` by iterating a `Dictionary` (or a `Set`) yields a different order per run → `Equatable` array fixtures flake.
**How to avoid:** sort every key sequence (`factorKey`, `symptomType.rawValue`) before emission; final top-N sort has a total-order tiebreaker (`signalKey`); all math is `Double` arithmetic with no RNG (Q6).
**Warning signs:** a fixture passes intermittently; ids differ across runs.

### Pitfall 5: Cycle-phase factor leaks for suppressed users
**What goes wrong:** a `factor.cyclePhase.*` correlation appears for a suppressed user, violating D-04 and the #1 endo honesty pitfall.
**How to avoid:** derive the phase label **only** from `CyclePhaseMapper.phaseWindows`, which returns `[]` under suppression/irregularity — so the phase factor is never `present` and no phase pair is ever tested. The `suppressedUserFixture` MUST yield zero `factor.cyclePhase.*` correlations.
**Warning signs:** any `.correlation` insight with a `cyclePhase` signalKey from a suppressed fixture.

---

## Code Examples

> No external "source" URLs — these are the in-repo patterns the new code must mirror. Citations are file:line into the existing package.

### Cliff's delta (pure, deterministic)
```swift
// O(m·n); ties contribute 0 to the numerator. Pattern: a pure helper in CorrelationStats.swift,
// independently unit-tested (mirrors TrendsEngine's "each helper independently testable").
static func cliffsDelta(present W: [Double], absent O: [Double]) -> Double? {
    guard !W.isEmpty, !O.isEmpty else { return nil }   // never coerce empty to 0
    var greater = 0, less = 0
    for w in W { for o in O { if w > o { greater += 1 } else if w < o { less += 1 } } }
    return Double(greater - less) / Double(W.count * O.count)   // [-1, +1]
}
```

### Risk difference (2×2, paired/observed days only)
```swift
static func riskDifference(a: Int, b: Int, c: Int, d: Int) -> Double? {
    guard a + b > 0, c + d > 0 else { return nil }     // an empty arm is unobserved, not 0
    return Double(a) / Double(a + b) - Double(c) / Double(c + d)
}
```

### Benjamini–Hochberg pass set (over ALL tested pairs)
```swift
// Returns the set of indices whose p passes FDR at q. Pattern mirrors the deterministic,
// sort-before-iterate discipline used everywhere in the package.
static func benjaminiHochbergPasses(_ pValues: [Double], q: Double = 0.10) -> Set<Int> {
    let k = pValues.count
    guard k > 0 else { return [] }
    let order = pValues.indices.sorted { pValues[$0] < pValues[$1] }  // ascending, stable by index
    var maxRank = -1
    for (rankZeroBased, idx) in order.enumerated() {
        let rank = rankZeroBased + 1
        if pValues[idx] <= (Double(rank) / Double(k)) * q { maxRank = rankZeroBased }
    }
    guard maxRank >= 0 else { return [] }
    return Set(order.prefix(maxRank + 1))
}
```

### Confidence mapping (post-hard-gate)
```swift
static func confidence(effectMag: Double, n: Int, mediumFloor: Double, largeFloor: Double, passedFDR: Bool) -> InsightConfidence {
    guard passedFDR, effectMag >= mediumFloor, n >= 10 else { return .none }  // → HonestyGate withholds
    if n >= 30 && effectMag >= largeFloor { return .high }
    if n >= 20 { return .medium }
    return .low
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Pearson r / linear regression over symptom×factor | Non-parametric effect size (Cliff's δ / RD) + exact significance + FDR | locked by D-01/D-02 | Robust on sparse, ordinal, non-normal self-tracked data; avoids false precision. |
| Bonferroni for many comparisons | Benjamini–Hochberg FDR (q=0.10) | modern multiple-testing default | Controls expected false-discovery proportion without crushing the differentiator. |
| Show-all-then-rank correlations | Withhold below-gate findings entirely (the gate) | Phase 6 honesty layer | Spurious findings never reach the user (SC3). |

**Deprecated/outdated for this codebase:**
- Any `import Accelerate` for these stats — unnecessary at single-user scale; keep it Foundation-only.
- The `minSample(.correlation) = 14` placeholder — **replace with 10** (this phase locks it).

---

## Validation Architecture

> nyquist_validation is enabled (`config.json: "nyquist_validation": true`).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`), Xcode 26 toolchain |
| Config file | `EndoInsights/Package.swift` (test target `EndoInsightsTests`); run via the `EndoApp` scheme |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests/CorrelationEngineTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INST-03 | Strong, well-sampled association is surfaced with effect size + confidence | unit (fixture) | `…-only-testing:EndoInsightsTests/CorrelationEngineTests/strongAssociation_isSurfaced` | ❌ Wave 0 |
| INST-03 | Under-sampled pair (n=9 < 10) is withheld | unit | `…/underSampled_isWithheld` | ❌ Wave 0 |
| INST-03 | Spurious (random) pair is withheld (fails FDR/effect) | unit | `…/spurious_isWithheld` | ❌ Wave 0 |
| INST-03 | Multiple-comparison flood (≥100 random pairs) surfaces ZERO | unit | `…/multipleComparisonFlood_surfacesNothing` | ❌ Wave 0 |
| INST-03 | Suppressed user → zero `factor.cyclePhase.*` correlations | unit | `…/suppressedUser_noCyclePhaseCorrelation` | ❌ Wave 0 |
| INST-04 | Every emitted correlation headline+detail has zero forbidden causal terms | unit | extend `HonestyGateTests/allEmittedInsights_haveZeroForbiddenTerms` | ⚠️ extend existing |
| D-16 | Determinism: same snapshots + same `today` → equal `[Insight]` | unit | `…/output_isDeterministic` | ❌ Wave 0 |
| D-13 | `InsightsEngine` composes correlation behind the gate | unit | extend `InsightsEngineTests` | ⚠️ extend existing |

### Required fixtures (D-15) — to add to `SnapshotFixtures.swift`
| Fixture | Construction | Asserts |
|---------|--------------|---------|
| `strongAssociationFixture` | ~40 days; on the ~20 days with `foodTags:[.dairy]`, log `pelvicPain` severity ~7; on the ~20 without dairy, severity ~2. | One `.correlation` emitted, `.medium`/`.high`, `sampleSize ≥ 20`, signalKey `correlation.food.dairy.symptom.pelvicPain`, copy non-causal. |
| `underSampledAssociationFixture` | Same shape but only **9** paired days. | Zero correlations (n < 10 → `.none` → gate withholds). |
| `spuriousAssociationFixture` | ~30 days; dairy and pelvicPain assigned **independently** (e.g. dairy on even offsets, pain on prime offsets) → near-zero true δ. | Zero correlations (effect floor / FDR). |
| `multipleComparisonFloodFixture` | ~60 days, every factor + symptom present at independent ~50% rates (≥100 tested pairs of pure noise). | **Zero** correlations surfaced (BH-FDR crushes the flood). |
| `nonCausalCopyFixture` | reuse `strongAssociationFixture` | `ForbiddenTerms.violation` is `nil` for every emitted headline+detail. |
| `suppressedUserCorrelationFixture` | `suppressedNoBleedingFixture` (exists) + dairy/symptom co-occurrence on those days. | Zero `factor.cyclePhase.*` correlations; (non-phase factor correlations MAY still emit — suppression only kills the cycle factor, D-04). |
| `nonCausalReverseFixture` (optional) | a "feeling bad → logged more food" shape | documents that same-day co-occurrence is symmetric/non-causal (the copy must not imply direction). |

### Sampling Rate
- **Per task commit:** `…-only-testing:EndoInsightsTests/CorrelationEngineTests` (the new suite) + `…/CorrelationStatsTests` (the pure-helper suite).
- **Per wave merge:** `…-only-testing:EndoInsightsTests` (full package suite — exercises the gate + composition).
- **Phase gate:** full `xcodegen generate && xcodebuild test … -scheme EndoApp` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `Tests/EndoInsightsTests/CorrelationStatsTests.swift` — unit tests for `cliffsDelta`, `riskDifference`, `fisherExact`, `mannWhitneyP`/`normalCDF`, `benjaminiHochbergPasses` against hand-computed expected values (the most important Wave-0 work — prove the math BEFORE wiring it).
- [ ] `Tests/EndoInsightsTests/CorrelationEngineTests.swift` — the fixture suite above.
- [ ] Extend `SnapshotFixtures.swift` with the 6–7 new fixtures.
- [ ] Extend `HonestyGateTests.allEmittedInsights_haveZeroForbiddenTerms` to scan the correlation fixtures (mandated by the file's own contract).
- [ ] Extend `InsightsEngineTests` to assert the composition includes correlations and routes them through the gate.
- [ ] No framework install needed — Swift Testing + the fixture DSL already exist.

---

## Security Domain

> `security_enforcement: true`. This is a pure, offline, Foundation-only computation phase over already-on-device data — no network, no auth, no new storage, no new input boundary. The relevant "security" surface is **data-honesty / privacy-of-inference**, not classic appsec.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in the package (no accounts anywhere — project constraint). |
| V3 Session Management | no | No sessions. |
| V4 Access Control | no | Pure value-in/value-out; no resource access. |
| V5 Input Validation | yes (light) | `DaySnapshot` enums already defensive-decode unknown raws to `.other`/`nil` (`SnapshotEnums.swift`); the engine must tolerate empty/degenerate inputs (empty arrays, single-group splits) without crashing — guard `guard !W.isEmpty` etc. |
| V6 Cryptography | no | None. The `DeterministicUUID` FNV hash is an identity derivation, explicitly **not** a security primitive (documented in `Insight.swift:160–162`) — do not "harden" it. |
| V7 Error/Logging | yes | **No logging of health values** (PITFALLS security table): the package is already `NO logging/print (V7)`. The engine must not `print`/log any severity/factor data. |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health data leaked to logs/crash reports | Information disclosure | Package contract forbids logging (V7); keep it. No severity/factor values in any string except the user-facing rounded headline. |
| A confident *causal* claim reaching the user (regulatory/MDR + trust) | Tampering/Repudiation (claim integrity) | `ForbiddenTerms` fail-closed scan in the gate (release + debug); mandatory zero-forbidden-terms test. |
| Spurious correlation shown as real (trust erosion, PITFALLS Pitfall 3) | Integrity of inference | The three-AND hard gate + BH-FDR + withholding (the core of this phase). |
| Privacy-of-inference: a correlation re-exposes a sensitive note's content | Information disclosure | The snapshot carries `hasNote: Bool` only, never prose (`DaySnapshot.swift:14–15`); the engine never touches free text — preserve that. |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `minSample(.correlation) = 10` (down from placeholder 14). | §4, Standard Stack | Too low → admits noisier pairs (mitigated by FDR + effect floor); too high → starves the differentiator. D-14 explicitly asks to tune ~10–14 against fixtures. **Confirm via the under-sampled fixture (n=9 withheld, n=10+ eligible).** |
| A2 | High-stress threshold `stress >= 7` (0–10); low-mood `mood <= 2` (1–5). | §1 | Wrong thresholds shift which days count as factor-present → could weaken/strengthen associations. Tune against the strong-association fixture; assert as named constants. |
| A3 | Cliff's δ medium floor `0.33`; RD medium floor `0.20`. | §2, §6 | The effect floor is the main "is it worth showing" lever. Too low → flood risk; too high → nothing shows. Confirm bands against strong vs spurious fixtures. |
| A4 | BH-FDR at `q = 0.10`. | §5 | q is the false-discovery tolerance. 0.10 is a defensible screening default; 0.05 is stricter. Confirm the flood fixture surfaces zero at the chosen q. |
| A5 | Per-group minimum = 3 observations. | §4 | Prevents a 9-vs-1 split passing on sample alone. Mirrors `minLoggedDays`; confirm against fixtures. |
| A6 | Top-N = 5, capped post-gate. | §7 | UX/flood lever (D-10). Easily tunable; not load-bearing for honesty. |
| A7 | Empty `foodTags`/`medicationsTaken` on a day-with-any-log counts as **absent** (not unobserved). | §1 | The denominator definition for "did you eat dairy today". Affects RD denominators. Confirm against the spurious/flood fixtures (the safer-but-stricter alternative is to treat empty as unobserved). |
| A8 | RD magnitude bands (`0.10/0.20/0.35`). | §2 | Presence-arm effect interpretation. Defensible defaults; confirm against fixtures. |

---

## Open Questions

1. **Should the engine cap top-N, or should Phase 8 rendering?**
   - What we know: D-10 says "surface only the strongest"; the engine is the honest source of truth.
   - What's unclear: whether Phase 8 wants the full gated set to offer its own "show more".
   - Recommendation: **engine caps post-gate at N=5** for an honest contract; Phase 8 can request a larger N later via a parameter if needed (additive, non-breaking).

2. **Food sub-tag explosion vs. a single `factor.food.any`?**
   - What we know: D-09 lists "food tags" as a factor; 8 tags × 12 symptom-targets ≈ 96 pairs from food alone.
   - What's unclear: whether testing every tag individually inflates K beyond what FDR can keep honest with early-user data.
   - Recommendation: test **per-tag** (the user wants "dairy" not "food"), accept the larger K — BH is designed for exactly this, and the flood fixture proves it holds. Revisit only if real data shows starvation.

3. **Bleeding as a symptom-target — severity or presence only?**
   - What we know: bleeding has `FlowIntensity` (ordinal-ish) + presence (`flow != nil`).
   - Recommendation: **presence arm only** for bleeding in v1 (RD/Fisher); flow-intensity-as-ordinal is a future refinement. Keeps the design uniform.

---

## Sources

### Primary (HIGH confidence)
- In-repo source (read directly this session): `EndoInsights/Sources/EndoInsights/{DaySnapshot,Insight,HonestyGate,CyclePhaseMapper,TrendsEngine,InsightsEngine,ForbiddenTerms,SnapshotEnums,CyclePhase}.swift`; `Tests/EndoInsightsTests/{SnapshotFixtures,HonestyGateTests}.swift` — the seams, contracts, and patterns Phase 7 extends.
- `.planning/phases/07-correlation-trigger-discovery/07-CONTEXT.md` — locked decisions D-01…D-18.
- `.planning/research/PITFALLS.md` — Pitfall 3 (over-confident correlation→causation), the multiple-comparison / spurious-correlation danger, the "every insight gated by sample size + confidence + non-causal copy" acceptance.
- `.planning/REQUIREMENTS.md` / `.planning/ROADMAP.md` — INST-03 + Phase 7 success criteria.

### Secondary (MEDIUM confidence — standard statistical method, training knowledge)
- Cliff's delta definition + magnitude conventions (Cliff 1993; Romano et al. interpretation thresholds 0.147/0.33/0.474) — standard non-parametric effect size. `[CITED: established statistics]`
- Benjamini & Hochberg (1995) FDR step-up procedure — the standard many-comparison control. `[CITED: established statistics]`
- Fisher's exact test (hypergeometric) and Mann–Whitney U normal approximation with tie correction — standard exact/closed-form tests. `[CITED: established statistics]`
- `erf`/`lgamma` availability via Foundation's C math import — `[VERIFIED: compiled & ran a probe this session on the Xcode 26 / Swift 6 toolchain. `erf` direct; `lgamma` reachable but ambiguous — bind `(lgamma as (Double)->Double)`. Deterministic 7-term erf polynomial remains a fallback only if a future toolchain regresses.]`

### Tertiary (LOW confidence)
- All specific tuning constants (min-sample 10, effect floors, q=0.10, thresholds) — `[ASSUMED]`, to be confirmed against the Wave-0 fixtures per D-14/D-16. See Assumptions Log.

---

## Metadata

**Confidence breakdown:**
- Codebase seams / integration (which files to touch, how the gate composes, signalKey scheme, sampleSize wiring): **HIGH** — read directly from source.
- Statistical design (Cliff's δ / RD / Fisher / Mann–Whitney / BH-FDR, the three-AND gate, confidence bands): **HIGH** for the *approach* (deliberately conservative, library-free, matches D-01/D-02), **MEDIUM** for exact constants (flagged `[ASSUMED]`).
- `erf`/`lgamma` via Foundation: **HIGH** — compiled & ran a probe this session; `erf` direct, `lgamma` needs the documented overload bind.
- Pitfalls / validation architecture: **HIGH** — derived from the existing test patterns and PITFALLS.md.

**Research date:** 2026-06-09
**Valid until:** ~2026-09-09 (stable — pure Foundation + textbook statistics; no fast-moving dependency. `erf`/`lgamma` availability already verified this session.)
