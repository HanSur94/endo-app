# Phase 11: Personalized Recommendations - Research

**Researched:** 2026-06-09
**Domain:** Composing non-prescriptive recommendation `Insight`s on top of the already-gated Phase-7 correlation engine, inside the Foundation-only `EndoInsights` SPM package (Swift 6.0, iOS 18, Xcode 26.5), surfaced through the existing Phase-8 dashboard.
**Confidence:** HIGH — this is an established-pattern phase. Every architectural seam (producer → `HonestyGate` → `InsightsEngine` → `InsightCardView`/`InsightsView` → `DismissedInsight`/`InsightsCoordinator`) already exists and was read directly from source. The only genuinely new surface is recommendation copy + the imperative scan, and one non-obvious data-flow gap (recovering association *direction*) documented below.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** A `RecommendationsEngine` (in `EndoInsights`) derives `.recommendation` `Insight`s by **re-framing the EXISTING gated correlation insights** (Phase 7) — and optionally strong trends — that already passed the `HonestyGate`. **NO new statistics**; it composes proven outputs (reuse, never re-derive).
- **D-02:** Only the **strongest, sufficiently-sampled** associations become recommendations (inherit `minSample(.correlation)==10` + the confidence floor from the honesty layer); **cap to top-N** (e.g. 3–5) to avoid noise/overwhelm. Deterministic ordering (confidence desc, then a stable tiebreak).
- **D-03:** **Direction-aware framing:** a beneficial association → "you tend to feel better when…"; an aggravating one → "your [symptom] tends to be higher on days with [factor] — something you might explore." NEVER causal ("X causes Y"), NEVER prescriptive.
- **D-04:** All recommendation copy is templated through a new auditable **`RecommendationsCopy`** surface routed through **`ClaimsPolicy`**, scanned by a **NEW forbidden-IMPERATIVE scan** (forbid directive verbs: "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut") ON TOP OF the existing forbidden-verb / gendered-token / numeric-confidence scans. Recommendations are **discussion prompts** ("you might explore…", "something to discuss with your clinician…").
- **D-05:** Each recommendation carries the same **plain-language confidence indicator** (word + SF-Symbol shape, NEVER a number) + a "based on your own data" basis note + the not-medical-advice framing — inheriting the dashboard's honesty presentation.
- **D-06:** A new **"Recommendations"** section in `InsightsView` (kind `.recommendation` → `insights.section.recommendations`), placed AFTER "Possible associations". Reuse `InsightCardView`. A **zero-recommendation section is OMITTED**.
- **D-07:** Each recommendation is **DISMISSABLE** via the existing dismiss mechanism (`DismissedInsight` + the swipe / "This isn't right" action); dismissal persists (syncs) and feeds back through the engine (recompute excludes the dismissed `signalKey`).
- **D-08:** The `RecommendationsEngine` plugs into the LOCKED public **`InsightsEngine.insights(from:asOf:dismissed:)`** behind the single **`HonestyGate`** choke-point — inheriting confidence-gating, withholding under insufficient data, dismissal-exclusion, and the forbidden-terms scan. NO separate gating path. A suppressed/sparse user gets **NO** recommendation.
- **D-09:** Reuse the Phase-8 **`InsightsCoordinator`** recompute (`dataChanged()`) + cache. The engine stays **Foundation-only** (no SwiftData/SwiftUI/HealthKit in the package).

### Claude's Discretion
- The exact recommendation `signalKey` scheme (must be stable + window-independent like the trend signals), the precise top-N cap, the recommendation-copy templates (subject to the imperative audit), whether strong trends (not just correlations) also seed recommendations, and the section ordering nuance.

### Deferred Ideas (OUT OF SCOPE)
- LLM/generative natural-language recommendations — out of scope (privacy + honesty + on-device).
- Actionable in-app "interventions" / reminders derived from recommendations — future; v1 surfaces discussion prompts only.
- Flare early-warning (INST-06) — Phase 12.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INST-05 | User receives personalized, data-driven recommendations derived from their own patterns. | The recommendation derivation strategy (re-frame already-gated `.correlation` Insights into `.recommendation` Insights), the `signalKey`/`DeterministicUUID` scheme, the direction-recovery design (the one real engineering decision), the forbidden-imperative scan, the dashboard wiring (all inherited from Phase 8), and the test/honesty contract are all documented below. Every required capability maps to an existing seam plus one new producer + one new copy surface. |
</phase_requirements>

## Summary

Phase 11 is a **pure composition phase**: it adds one new pure-Foundation producer (`RecommendationsEngine`) to the `EndoInsights` package and one new app-side copy surface (`RecommendationsCopy`), then reuses every existing seam unchanged. The architecture for "a new `InsightKind` flows producer → gate → engine → dashboard → dismiss → cache" was built and locked in Phases 5–8 specifically so Phases 11/12 could slot in without new machinery. The `.recommendation` case is already reserved in `InsightKind`, the `HonestyGate.minSample` table already has a `.recommendation` entry (currently `14`, a placeholder), `InsightCardView.supportingData` already routes `.recommendation` to its words-only evidence branch, and `InsightsEngine.insights(...)` is the locked single public entry point that simply concatenates producers before the gate.

The single genuinely new design problem is **direction recovery (beneficial vs aggravating)**. The public `Insight` value carries no signed effect field — the sign of Cliff's δ / risk-difference lives only inside `CorrelationEngine`'s private `Candidate` struct and is never surfaced on the emitted `.correlation` Insight (the correlation copy says "Higher … severity possibly associated with …" but D-03's direction-aware framing needs a reliable, non-string signal). **The recommended approach is therefore D-01's "re-frame correlations" composed *inside the package* by having `RecommendationsEngine` call `CorrelationEngine` and read its candidates' direction — NOT by parsing the public `[Insight]` text downstream.** This keeps direction a typed value, never a fragile substring match. See "Architecture Patterns → Pattern 2".

The honesty work is the substantive surface: recommendation copy must read as a *discussion prompt*, never an instruction. This is enforced by a new forbidden-imperative scan layered onto the existing `ClaimsPolicy.forbiddenVerbs` / `forbiddenGenderedTokens` / numeric-confidence scans in `CopyAuditTests`, plus the package-internal `ForbiddenTerms` causal scan that the `HonestyGate` already runs on every emitted insight. Because the engine's `headline`/`detail` are produced from `RecommendationsCopy` templates (not free text), the same scan that guards the static copy surface also guards what the engine emits.

**Primary recommendation:** Build `RecommendationsEngine` as a Foundation-only `enum` producer that internally invokes `CorrelationEngine` (and optionally `TrendsEngine`), reads each surviving candidate's *typed* direction + confidence + sample size, emits up to top-N `.recommendation` `Insight`s (signalKey `recommendation.<correlation-signalKey-stem>`, window `.allTime`) via `Insight.derived`, with `RecommendationsCopy`-templated direction-aware copy; compose it into `InsightsEngine.insights(...)` exactly like `CorrelationEngine`; lower `HonestyGate.minSample(.recommendation)` to inherit the correlation floor (10) since recommendations re-frame already-gated correlations; add the new `.recommendation` section in `InsightsView`; and extend `CopyAuditTests` + `HonestyGateTests` with the imperative scan and recommendation fixtures.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Derive recommendations from patterns | `EndoInsights` package (`RecommendationsEngine`, Foundation-only) | — | All insight logic lives in the pure package (SC1 boundary); the app never computes insights. |
| Route recommendations through honesty layer | `EndoInsights` package (`HonestyGate` via `InsightsEngine`) | — | The single mandatory choke-point every kind passes through (D-08). |
| Recommendation copy + imperative audit data | App target (`RecommendationsCopy`) for the auditable surface; `ForbiddenTerms` (package) for the engine-emitted-copy scan | — | Static chrome copy is app-side and audited by `CopyAuditTests`; the engine's emitted headline/detail are scanned package-internally by `HonestyGate`/`ForbiddenTerms`. The new imperative list must exist in BOTH places (or be a shared enforced rule), because the package cannot import the app. |
| Render the Recommendations section + cards | App target (`InsightsView`, `InsightCardView`) | — | UI is SwiftUI in the app; the card is kind-agnostic and already handles `.recommendation`. |
| Dismiss + persist + recompute | App target (`InsightsCoordinator`, `DismissedInsight`, `SwiftDataRepository`) | CloudKit (sync of `DismissedInsight`) | Existing Phase-6/8 path; `.recommendation` dismissal is keyed on `signalKey` identically. |
| Cache + off-main recompute | App target (`InsightsCoordinator`, `CachedInsight`) | CloudKit (sync of `CachedInsight`) | Existing Phase-8 coordinator; `.recommendation` Insights are `Codable` and cache like any other (D-09). |

## Standard Stack

This phase adds **zero external dependencies**. It uses only the project's existing first-party stack and the existing in-repo `EndoInsights` package.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Foundation | system (iOS 18 / Swift 6.0) | The ONLY import permitted in `EndoInsights` sources (SC1 boundary, enforced by `BoundaryLintTests`). | `RecommendationsEngine` must stay pure value-in/value-out; Foundation supplies `Date`/`Calendar`/`String` only. `[VERIFIED: BoundaryLintTests.swift]` |
| Swift Testing | Xcode 26 toolchain | `@Test`/`@Suite`/`#expect` unit tests for the engine + honesty contract. | The project default for new test code; every `EndoInsights` test suite uses it. `[VERIFIED: HonestyGateTests.swift, InsightsEngineTests.swift]` |
| SwiftUI | iOS 18+ | The new `.recommendation` `Section` in `InsightsView` (reuses `InsightCardView`). | Existing UI layer; no new view types required (UI-SPEC). `[CITED: 11-UI-SPEC.md]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SwiftData | iOS 18+ | Persists `DismissedInsight` + `CachedInsight` (app target only — NEVER the package). | Already wired in `SwiftDataRepository` + `InsightsCoordinator`; `.recommendation` flows through unchanged. `[VERIFIED: InsightsView.swift dismiss path]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `RecommendationsEngine` calls `CorrelationEngine` internally and reads typed candidate direction | Parse the public `[Insight]` correlation `headline`/`detail` strings downstream to infer direction | REJECTED — fragile substring matching on copy that is itself audited/changeable; couples two layers by text; breaks the "typed marker not forgeable string" posture established by `isInsufficiencyNotice` (WR-01). Keep direction a typed value inside the package. |
| Compose only correlations | Also seed recommendations from strong `.trend` insights | Claude's discretion (D-01/D-09 note). A trend has no factor pairing, so it cannot produce a "feel better when {factor}" prompt — at most a "your {symptom} has been trending up" discussion prompt. Recommend **correlations-only for v1** (cleanest direction-aware framing) and defer trend-seeding unless planning decides otherwise. |
| Lower `minSample(.recommendation)` to 10 | Keep the `14` placeholder | Since recommendations RE-FRAME already-gated correlations (each carries `sampleSize == its correlation's pairedDays >= 10`), a `14` floor would silently drop valid recommendations whose underlying correlation passed at n=10–13. Set `minSample(.recommendation) == minSample(.correlation) == 10` so the re-framed insight inherits exactly the correlation gate (D-02/D-08). |

**Installation:** None. No `npm`/`pip`/`cargo`/SPM dependency is added. (Package-legitimacy audit therefore N/A — see below.)

## Package Legitimacy Audit

**Not applicable.** This phase installs **zero** external packages. The `RecommendationsEngine` is a new source file inside the existing in-repo `EndoInsights` SPM package (Foundation-only); the UI reuses existing project-owned SwiftUI views. No registry dependency is introduced. (Confirmed against 11-UI-SPEC §Registry Safety and CONTEXT D-09.)

## Architecture Patterns

### System Architecture Diagram

```
  [DaySnapshot]  (per-day value snapshots, order-independent)
        │
        ▼
  InsightsEngine.insights(from:asOf:dismissed:calendar:)   ── THE single public entry (LOCKED, D-08/D-16)
        │
        │  concatenates candidate producers:
        ├──► TrendsEngine.trends(...)            → [.trend]
        ├──► CyclePhaseMapper.insights(...)      → [.cyclePhase]
        ├──► CorrelationEngine.insights(...)     → [.correlation]   ◄────────────┐
        └──► RecommendationsEngine.recommendations(...)  → [.recommendation]     │ NEW producer
        │            │                                                           │
        │            └── internally calls CorrelationEngine (+ reads TYPED       │ re-uses, never
        │                direction/confidence/sampleSize of surviving candidates)┘ re-derives (D-01)
        ▼
  HonestyGate.apply(candidates, dismissed:)   ── the ONE choke-point (D-08)
        │   (a) drop dismissed signalKeys
        │   (b) drop sampleSize < minSample(kind)   [.recommendation → 10, see below]
        │   (c) drop confidence < .low (except typed insufficiency notice)
        │   (d) FAIL-CLOSED: drop any headline/detail containing a ForbiddenTerms causal term
        ▼
  [Insight]  (gated, dismissal-filtered, deterministic, Equatable, Codable)
        │
        ▼  (app target)
  InsightsCoordinator  ── off-main recompute (dataChanged) + fingerprint-keyed CachedInsight cache (D-09)
        │
        ▼
  InsightsView  ── grouped List sections: Trends → Cycle phase → Possible associations → Recommendations
        │            (each section OMITTED when it has zero insights — honest absence, D-06)
        ▼
  InsightCardView (kind-agnostic)  ── headline + confidence pill + detail + words-only evidence + basis
        │
        └── dismiss ("This isn't right") → coordinator.dismiss(signalKey:) → DismissedInsight (syncs) → recompute excludes
```

### Component Responsibilities

| File (new or edited) | Tier | Responsibility |
|----------------------|------|----------------|
| `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift` **(NEW)** | package | Pure `enum` producer: calls `CorrelationEngine` (+ optionally `TrendsEngine`), reads typed direction/confidence/sampleSize, emits top-N `.recommendation` `Insight`s with `RecommendationsCopy`-templated copy via `Insight.derived`. Foundation-only. |
| `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift` **(EDIT, additive)** | package | Likely needs to expose its surviving candidates' **typed direction** (sign of δ/RD) to `RecommendationsEngine` without changing its public `.correlation` output. Options in Pattern 2 below. |
| `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` **(EDIT, +1 line)** | package | Append `+ RecommendationsEngine.recommendations(...)` to the candidate concatenation. Signature stays LOCKED. |
| `EndoInsights/Sources/EndoInsights/HonestyGate.swift` **(EDIT, 1 line)** | package | Change `.recommendation` in `minSample(for:)` from `14` → `10` (inherit the correlation floor, D-02). |
| `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` **(EDIT, additive)** | package | Add the imperative directive terms so the `HonestyGate` scan catches an imperative in any engine-emitted recommendation copy (see Pitfall 3 caution on substring traps). |
| `Sources/EndoApp/Features/Insights/RecommendationsCopy.swift` **(NEW)** | app | Auditable fixed-string surface (mirrors `InsightsCopy`): section header, direction-aware templates, `auditableStrings`. Both the static strings AND the engine's interpolated output route through these templates. |
| `Sources/EndoApp/Features/Insights/InsightsView.swift` **(EDIT, +1 section)** | app | Add a 4th `section(...)` call for `.recommendation` after "Possible associations". |
| `Sources/EndoApp/Content/ClaimsPolicy.swift` **(EDIT, additive — optional)** | app | Optionally add `forbiddenImperatives` list so the app-side audit reads from one policy source. |
| `Tests/EndoAppTests/CopyAuditTests.swift` **(EDIT)** | app | Add `recommendationsCopy_*` scans incl. the new imperative scan. |
| `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` **(EDIT)** | package | Add recommendation fixtures to `allEmittedInsights_haveZeroForbiddenTerms`; add imperative-scan assertions. |
| `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift` **(NEW)** | package | Engine unit spec (surfaced/withheld/suppressed/top-N/deterministic) mirroring `CorrelationEngineTests`. |

### Pattern 1: The producer → gate → engine composition (mirror exactly)

**What:** Every insight kind is a pure `enum` producer with one static entry `static func X(from snapshots:asOf today:calendar:) -> [Insight]`, composed into `InsightsEngine.insights(...)` by concatenation, then gated once.

**When to use:** For `RecommendationsEngine` — mirror `CorrelationEngine.insights` and `CyclePhaseMapper.insights` signatures verbatim.

**Example (the locked composition root — add one line):**
```swift
// Source: EndoInsights/Sources/EndoInsights/InsightsEngine.swift (verbatim, +1 producer)
let candidates =
    TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
    + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
    + CorrelationEngine.insights(from: snapshots, asOf: today, calendar: calendar)
    + RecommendationsEngine.recommendations(from: snapshots, asOf: today, calendar: calendar)  // NEW
return HonestyGate.apply(candidates, dismissed: dismissed)
```

**Example (the `Insight.derived` factory the producer MUST use — deterministic id):**
```swift
// Source: EndoInsights/Sources/EndoInsights/Insight.swift (the only id-stable construction path)
Insight.derived(
    kind: .recommendation,
    window: .allTime,                 // window-independent, like correlations — see Pattern 3
    signalKey: "recommendation.\(stableStem)",
    headline: RecommendationsCopy.beneficialHeadline(...),   // templated, NOT free text
    detail:   RecommendationsCopy.detail(...),
    confidence: candidate.confidence,  // inherited from the underlying correlation
    points: [],                        // words-only card; no chart for .recommendation
    sampleSize: candidate.pairedDays   // inherited evidence count → gate enforces >= 10
)
```

### Pattern 2: Direction recovery (the one real engineering decision)

**Problem:** D-03 needs *beneficial vs aggravating*. The public `Insight` has no signed-effect field; the sign of Cliff's δ / risk-difference is private to `CorrelationEngine.Candidate` and is **not** recoverable from the public `.correlation` Insight except by parsing its copy ("Higher … severity possibly associated with …"), which is fragile and audited.

**Recommended approach (typed, in-package):** Have `RecommendationsEngine` obtain direction as a **typed value from inside the package**, not from downstream string parsing. Two viable shapes (planner picks one):

- **Option A — internal "scored candidate" accessor.** Add a package-internal `CorrelationEngine` function (e.g. `static func scoredCandidates(from:asOf:calendar:) -> [CorrelationFinding]`) that returns the same survivors `insights(...)` emits, but as a small typed struct carrying `signalKey`, `confidence`, `pairedDays`, `effectMagnitude`, and a typed `direction: Direction` (beneficial/aggravating derived from the *sign* of meanDiff/δ or RD). `RecommendationsEngine` consumes those; `CorrelationEngine.insights` is unchanged for the `.correlation` section. This is the cleanest reuse-not-rederive (D-01): the math runs once, the recommendation re-frames the typed result.
- **Option B — add a typed `direction` to the `.correlation` Insight itself.** Additively extend `Insight` with an optional `direction` enum (defaulted `nil` for kinds that have none). More invasive (touches the public value + `Codable` + cache); only do this if the dashboard also wants to show direction on correlations. **Option A is preferred** — it keeps the change package-internal and avoids touching the cached `Insight` schema.

**Direction semantics:** For the severity arm, beneficial = the factor's group has *lower* symptom severity (meanDiff < 0); aggravating = higher (meanDiff > 0). For the presence arm, aggravating = the symptom/bleeding is logged *more* often on factor-present days (RD/RR > 1); a "beneficial" presence finding (symptom logged *less* often) maps to "you tend to feel better on days with {factor}". Encode this as a typed `enum RecommendationDirection { case beneficial, aggravating }` in the package.

### Pattern 3: Stable, window-independent `signalKey` + `DeterministicUUID`

**What:** The recommendation `signalKey` must survive recompute so dismissal sticks (D-07). Correlations already use `window: .allTime` and a value-independent key (`"correlation.<factor>.<target>"`). The recommendation key should derive deterministically from the underlying correlation's stem so the same logical recommendation always has the same key + id.

**Recommendation:** `signalKey = "recommendation.\(correlation.signalKey.dropping("correlation.") )"` → e.g. `recommendation.food.dairy.symptom.giEndoBelly`. Use `window: .allTime`. `Insight.derived` then derives `id` from `recommendation|allTime|<signalKey>` via `DeterministicUUID.make` — stable across sessions/devices, which is exactly what the Phase-6 dismissal + Phase-8 cache rely on.

```swift
// Source: EndoInsights/Sources/EndoInsights/Insight.swift — DeterministicUUID.make
// id = FNV-1a over "com.endoapp.endoinsights.insight.v1|recommendation|allTime|<signalKey>"
// SAME inputs → SAME UUID. NEVER UUID(). This is why signalKey must be value-independent.
```

### Pattern 4: Top-N deterministic ordering (mirror CorrelationEngine)

`CorrelationEngine` already sorts `confidence desc, effectMag desc, pairedDays desc, signalKey asc` then `.prefix(topN)`. `RecommendationsEngine` should do the same over its candidates (recommend `topN = 3` for recommendations — a tighter cap than correlations' 5, per D-02 "avoid overwhelm"). The tiebreak MUST end with `signalKey asc` so ordering is fully deterministic for fixture assertions.

### Anti-Patterns to Avoid
- **Re-running statistics in `RecommendationsEngine`** — forbidden by D-01. The engine composes the proven correlation output; it does not compute δ/RD/p-values.
- **Inferring direction by substring-matching the public correlation copy** — fragile, couples to audited copy. Use a typed package-internal accessor (Pattern 2 Option A).
- **Free-text engine copy** — the engine's `headline`/`detail` MUST come from `RecommendationsCopy` templates so the same audit guards them (D-04). Never interpolate user/factor strings into a sentence that could form a forbidden imperative.
- **A `.recommendation`-specific gate or "soft" floor** — there is ONE gate (D-08). Route everything through `HonestyGate.apply`.
- **Emitting a placeholder "no recommendations yet" card** — honest absence is non-existence; the section is omitted when empty (D-06), exactly like the cycle-phase suppressed case.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Deterministic insight identity | A `UUID()` or hash of the whole insight | `Insight.derived(...)` → `DeterministicUUID.make(kind:window:signalKey:)` | Already the only id-stable path; random ids break `Equatable` fixtures + dismissal + cache. `[VERIFIED: Insight.swift]` |
| Min-sample / confidence / dismissal / causal-copy gating | A new filter in `RecommendationsEngine` | `HonestyGate.apply(...)` via `InsightsEngine` | The single choke-point; a parallel gate would be a compliance hole. `[VERIFIED: HonestyGate.swift]` |
| Dismiss persistence + sync + recompute-exclusion | New dismiss plumbing | `coordinator.dismiss(signalKey:)` → `DismissedInsight` | Kind-agnostic; `.recommendation` keys flow through unchanged. `[VERIFIED: InsightsView.swift]` |
| Off-main recompute + cache | A new recompute trigger | `InsightsCoordinator.dataChanged()` + `CachedInsight` | Phase-8 machinery; `.recommendation` Insights are `Codable` and cache automatically. `[CITED: 11-UI-SPEC.md §A]` |
| Confidence pill / evidence line / card layout | A new recommendation card view | `InsightCardView` (kind-agnostic; already routes `.recommendation`) | Its `supportingData` switch already has `.recommendation` in the words-only branch. `[VERIFIED: InsightCardView.swift:128]` |
| Empty-section omission | A custom "show if non-empty" check | `InsightsView.section(title:identifier:kind:)` | Already filters by kind and renders `EmptyView` when zero. `[VERIFIED: InsightsView.swift:108]` |
| Correlation statistics | Re-deriving δ/RD/p in the recommendation engine | `CorrelationEngine` output (typed candidates) | D-01: compose, never re-derive. `[VERIFIED: CorrelationEngine.swift]` |

**Key insight:** In this codebase the honesty/identity/dismissal/cache machinery is the product's ethical core and is deliberately centralized. A correct Phase 11 adds *one producer + one copy surface* and edits ~3 single lines (compose, minSample, section); everything else is reuse. Any task that proposes new gating, new id derivation, or a new card type is a red flag.

## Common Pitfalls

### Pitfall 1: Direction lost / mis-signed
**What goes wrong:** A recommendation says "you tend to feel better on days with {factor}" when the data actually shows the symptom is *worse* on those days (or vice-versa), because direction was guessed or sign-flipped.
**Why it happens:** The public `.correlation` Insight carries no typed sign; the presence arm vs severity arm have different "beneficial" conventions (lower severity vs lower presence-frequency).
**How to avoid:** Use Pattern 2 Option A (typed package-internal direction). Unit-test BOTH a beneficial fixture (factor present → lower severity) and an aggravating fixture (factor present → higher severity / more frequent) and assert the emitted headline uses the correct template.
**Warning signs:** A fixture where flipping the factor's effect does not flip the template.

### Pitfall 2: The imperative substring trap (mirror the existing "cause" ⊂ "because" caution)
**What goes wrong:** A naive imperative substring scan flags innocent host words: `"use"` ⊂ `"because"`/`"cause"`/`"caused"`; `"try"` ⊂ no common host but `"start"` ⊂ `"started"`; `"cut"` ⊂ `"executed"`? (no, but `"cut"` ⊂ `"cute"`/`"discuss"`? — "discuss" contains no "cut", but verify each); `"stop"` ⊂ `"stopover"`. Conversely, an over-narrow scan misses inflections ("avoiding", "trying").
**Why it happens:** `ForbiddenTerms.violation` is a case-insensitive **substring** scan (documented sharp edge in `ForbiddenTerms.swift`).
**How to avoid:** Follow the file's locked posture: **curate the copy to avoid forbidden substrings entirely** and let the scan enforce. Critically, the recommendation copy uses "something you might **explore**", "something to **discuss** with your clinician" — verify these chosen words contain NONE of the imperative substrings (e.g. confirm "discuss" does not contain "cut"/"use" — it does not; "explore" does not contain "start"/"stop"/"try"/"use"). Add a unit test asserting each chosen template word is clean against the imperative list, and add deliberately-imperative negative fixtures ("you should avoid dairy", "try cutting gluten") that MUST be flagged.
**Warning signs:** The imperative scan flags an existing clean string (false positive) or passes "avoid"/"try" inflections (false negative).

### Pitfall 3: `minSample(.recommendation)` placeholder silently drops valid recommendations
**What goes wrong:** The `HonestyGate.minSample` table currently returns `14` for `.recommendation` (a Phase-11 placeholder). A recommendation re-frames a correlation that passed at n=10–13 → its `sampleSize` is 10–13 < 14 → the gate silently drops it and the Recommendations section is mysteriously empty for a user who clearly has gated correlations.
**Why it happens:** The placeholder was a guess; recommendations inherit the correlation's evidence count, not a stricter one.
**How to avoid:** Set `minSample(.recommendation) == minSample(.correlation) == 10` (D-02 "inherit the correlation floor"). Add a test asserting a recommendation built from an n=10 correlation survives the gate, and that `minSample(.recommendation) == minSample(.correlation)`.
**Warning signs:** A fixture with a strong n=10–13 correlation surfaces a `.correlation` but no `.recommendation`.

### Pitfall 4: New emitted copy not added to the zero-forbidden-terms scan
**What goes wrong:** `HonestyGate`'s release forbidden-term enforcement is a DEBUG `assertionFailure` — the durable guarantee is the TEST `allEmittedInsights_haveZeroForbiddenTerms`. If Phase-11 recommendation fixtures are not added to that test's `streams` array, a causal/imperative term could ship in release.
**Why it happens:** The contract (documented in `HonestyGateTests.swift` and `ForbiddenTerms.swift`) explicitly requires every new kind to add its fixtures; it is easy to forget.
**How to avoid:** Add beneficial/aggravating/suppressed recommendation fixtures to the `streams` array in `allEmittedInsights_haveZeroForbiddenTerms`, and extend the scan to also check the imperative list (not just causal). This is a mandatory acceptance, not optional.
**Warning signs:** New fixtures exist in `RecommendationsEngineTests` but the `streams` array in `HonestyGateTests` is unchanged.

### Pitfall 5: Suppressed/sparse user gets a fabricated recommendation (the make-or-break honesty failure)
**What goes wrong:** A user with no qualifying correlations (suppressed, sparse, or all-noise) sees a "do this" recommendation invented to fill the section.
**Why it happens:** A producer that emits a default/placeholder when it has no real survivors.
**How to avoid:** `RecommendationsEngine` emits ONLY from real surviving correlation candidates; if `CorrelationEngine` yields zero survivors, the engine returns `[]` and the section is omitted (D-06/D-08/SC3). Mirror `CorrelationEngineTests.suppressedUserCorrelation` / `multipleComparisonFlood` with a recommendation-level assertion of ABSENCE.
**Warning signs:** Any code path in `RecommendationsEngine` that appends an `Insight` outside the loop over real candidates.

## Code Examples

### Verified producer signature to mirror
```swift
// Source: EndoInsights/Sources/EndoInsights/CorrelationEngine.swift (the template to copy)
enum CorrelationEngine {
    static let topN = 5
    static func insights(
        from rawSnapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] { /* ... → top-N gated [Insight] ... */ }
}
// RecommendationsEngine mirrors this exactly, but composes CorrelationEngine's typed survivors.
```

### Verified words-only card branch (already handles `.recommendation`)
```swift
// Source: Sources/EndoApp/Features/Insights/InsightCardView.swift:128
case .cyclePhase, .correlation, .recommendation, .flare:
    VStack(alignment: .leading, spacing: 4) {
        Text(InsightsCopy.supportingEvidence(loggedDays: insight.sampleSize)) // "Seen across {n} logged days."
        Text(InsightsCopy.windowLabel(insight.window))
    }
// No new rendering work — .recommendation already routes here (no chart, no coefficient).
```

### Verified auditable copy surface posture to mirror for `RecommendationsCopy`
```swift
// Source: Sources/EndoApp/Features/Insights/InsightsCopy.swift (mirror enum + auditableStrings)
enum RecommendationsCopy {
    static let sectionRecommendations = "Recommendations"
    // direction-aware, NON-imperative, NON-causal templates...
    static func beneficialHeadline(factor: String) -> String { /* "You tend to feel better on days with \(factor)." */ }
    static func aggravatingHeadline(symptom: String, factor: String) -> String {
        // "Your \(symptom) tends to be higher on days with \(factor) — something you might explore."
        // NOTE: "higher" contains the gendered "her" substring trap — resolve via existing direction wording.
    }
    static let detail = "This is a pattern in your own data, not advice — something to discuss with your clinician."
    static var auditableStrings: [String] { /* all fixed strings + representative templated outputs */ [] }
}
```
> **Copy caution (verified against `ClaimsPolicy`):** "higher"/"lower" contain the gendered substring **"her"** (`ClaimsPolicy.forbiddenGenderedTokens`), which the existing `InsightsCopy` deliberately avoids by using "trending up/down". The aggravating template must avoid "higher" — phrase descriptively (e.g. "tends to be more intense on days with {factor}") and verify against the gendered scan. `[VERIFIED: InsightsCopy.swift:14-16, ClaimsPolicy.swift:43-50]`

## Runtime State Inventory

Not applicable — Phase 11 is **additive/greenfield** within the engine and dashboard. No rename/refactor/migration. No new SwiftData `@Model` is introduced (the new `.recommendation` Insights reuse the existing `CachedInsight`/`DismissedInsight` models and `signalKey` string scheme), so **no schema version bump or migration is required** (unlike Phases 6/8 which added models). Verified: dismissal keys on `signalKey: String`, cache stores `Codable` `Insight` — both kind-agnostic.

- **Stored data:** None new. `CachedInsight` (Codable `Insight`) + `DismissedInsight` (signalKey+timestamp) already store any kind. `.recommendation` signalKeys are new *values* in an existing column, not a new schema. (Verified by `InsightsView` dismiss path + Phase-8 cache contract.)
- **Live service config:** None — no external services; the engine is pure Foundation.
- **OS-registered state:** None.
- **Secrets/env vars:** None.
- **Build artifacts:** None — adding source files to an existing SPM package; no package rename.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`@Suite`/`#expect`), Xcode 26 toolchain |
| Config file | none — SPM package tests + app test target via `xcodebuild test -scheme EndoApp` (project.yml-driven; no XCTest config file) |
| Quick run command | `xcodebuild test -scheme EndoApp -only-testing:EndoInsightsTests/RecommendationsEngineTests` (engine spec, fast, no simulator UI) |
| Full suite command | `xcodebuild test -scheme EndoApp` (package suites + app `CopyAuditTests` + UI tests) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INST-05 | A strong, well-sampled correlation surfaces a `.recommendation` through the public engine | unit | `xcodebuild test -scheme EndoApp -only-testing:EndoInsightsTests/RecommendationsEngineTests` | ❌ Wave 0 (`RecommendationsEngineTests.swift`) |
| INST-05 | Direction-aware: beneficial fixture → "feel better" template; aggravating fixture → descriptive template | unit | same suite | ❌ Wave 0 |
| INST-05 | Suppressed / sparse / multiple-comparison-flood → ZERO recommendations (absence) | unit | same suite | ❌ Wave 0 |
| INST-05 | Top-N cap (≤3) + deterministic ordering (same inputs → equal `[Insight]`) | unit | same suite | ❌ Wave 0 |
| INST-05 / D-07 | A dismissed recommendation `signalKey` is excluded and stays excluded across recompute | unit | `-only-testing:EndoInsightsTests/InsightsEngineTests` (extend) | ✅ extend existing |
| INST-05 / D-08 | `minSample(.recommendation) == minSample(.correlation) == 10`; an n=10-sourced recommendation survives the gate | unit | `-only-testing:EndoInsightsTests/HonestyGateTests` (extend) | ✅ extend existing |
| INST-05 / D-04 | Zero forbidden CAUSAL terms across all recommendation fixtures (add to `streams`) | unit | `HonestyGateTests/allEmittedInsights_haveZeroForbiddenTerms` | ✅ extend existing |
| INST-05 / D-04 | Zero forbidden IMPERATIVE terms in `RecommendationsCopy.auditableStrings` + engine output; deliberate imperatives are flagged | unit | `Tests/EndoAppTests/CopyAuditTests` (new `recommendationsCopy_*`) | ✅ extend existing |
| INST-05 / D-05 | No numeric confidence anywhere in recommendation copy (words only) | unit | `CopyAuditTests` (mirror `insightsCopy_hasNoNumericConfidence`) | ✅ extend existing |
| INST-05 / D-06 | The `.recommendation` section is present when supported and provably ABSENT when unsupported | UI (XCUITest) | `xcodebuild test -scheme EndoApp -only-testing:EndoAppUITests/InsightsDashboardUITests` (extend) | ✅ extend existing |
| SC1 | Package stays Foundation-only after adding `RecommendationsEngine` | unit | `EndoInsightsTests/BoundaryLintTests` (auto-covers new file) | ✅ existing |

### Sampling Rate
- **Per task commit:** `xcodebuild test -scheme EndoApp -only-testing:EndoInsightsTests/RecommendationsEngineTests` (+ the edited honesty/copy suite under change).
- **Per wave merge:** the full `EndoInsightsTests` suite + `CopyAuditTests`.
- **Phase gate:** full `xcodebuild test -scheme EndoApp` green (incl. UI dashboard E2E) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift` — surfaced / withheld / suppressed / direction / top-N / determinism (covers INST-05).
- [ ] Recommendation fixtures in `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` (beneficial, aggravating, suppressed, flood) — mirror the correlation fixtures; reuse `Fixtures.calendar` + `Fixtures.day(_:)`.
- [ ] Extend `HonestyGateTests.allEmittedInsights_haveZeroForbiddenTerms` `streams` array with the new fixtures (mandatory per the file's own contract).
- [ ] Extend `CopyAuditTests` with `recommendationsCopy_*` scans incl. the new imperative scan + deliberate-imperative negative cases.
- [ ] No framework install needed — Swift Testing + the app/package test targets already exist.

## Security Domain

`security_enforcement` is ON (ASVS L1). The threat surface for this phase is **modest** — the engine is pure Foundation with no I/O, no network, no new persistence — and is dominated by *honesty/compliance* rather than classic injection/authz.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | The Foundation-only package boundary (`BoundaryLintTests`) is the architectural control: insight logic cannot reach data/network. No new tier or trust boundary is introduced. |
| V2 Authentication | no | No accounts/auth (CloudKit uses the user's implicit iCloud identity). |
| V3 Session Management | no | No sessions. |
| V4 Access Control | no | Single-user, on-device; no multi-tenant access. (Dismissal integrity is a data-integrity concern, below, not access control.) |
| V5 Input Validation | yes (light) | Inputs are typed `DaySnapshot` value types, not untrusted external bytes. The producer already defends against duplicate-day snapshots (see `CorrelationEngine` step 0); `RecommendationsEngine` consumes the same de-duplicated stream via `CorrelationEngine`, so no new validation is needed. |
| V6 Cryptography | no | `DeterministicUUID` is explicitly an identity derivation, NOT a security primitive (documented in `Insight.swift`); a non-cryptographic FNV-1a hash is appropriate and must not be "upgraded" to imply security. |
| V7 Error Handling / Logging | yes | The package emits NO logs/print (V7 of the project's own rules); recommendation copy must never log symptom/factor content. No PII leaves the device. |
| V9 Communications | yes (by absence) | No new data egress — the engine is pure Foundation; there is no network call to audit. This is the strongest privacy posture and must be preserved (no new imports). |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| A recommendation reads as a medical instruction ("take/stop/avoid X") | Tampering (of meaning) / compliance | The NEW forbidden-imperative scan in `CopyAuditTests` + the package `ForbiddenTerms`/`HonestyGate` causal scan — the choke-point that WINS over any template wording (D-04). This is the primary control. |
| A fabricated recommendation for a sparse/suppressed user | Spoofing (of evidence) | Emit only from real surviving correlation candidates; honest absence by omission (Pitfall 5). |
| Causal claim ("X causes Y") in runtime-interpolated copy | Tampering / compliance | `HonestyGate` fail-closed causal scan (drops in release, asserts in debug); templated copy via `RecommendationsCopy`. |
| Dismissal integrity (a dismissed recommendation reappears) | Tampering | Reuse the proven `DismissedInsight` (signalKey-keyed, syncs, idempotent recompute-exclusion). Stable value-independent `signalKey` is the load-bearing requirement (Pattern 3). |
| Health-data egress via a new dependency/import | Information Disclosure | `BoundaryLintTests` forbids any import beyond Foundation; zero new dependencies (Package Legitimacy N/A). |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Recommendations as ML/LLM-generated text | Deterministic, template-based, on-device re-framing of gated stats | Locked in CONTEXT (D-01) + PROJECT "What NOT to Use" (no cloud/LLM inference) | No model, no egress, fully testable + auditable copy. |
| Confidence as a number/percentage | Three plain-language words + SF-Symbol shape | Phase 6/8 honesty layer (inherited) | No false precision; color never the sole signal. |
| `minSample(.recommendation) == 14` (placeholder) | `== 10` (inherit correlation floor) | This phase | Prevents silent drop of valid n=10–13 recommendations (Pitfall 3). |

**Deprecated/outdated:** Nothing to remove. The `14` placeholder in `HonestyGate.minSample` should be corrected to `10`, not deleted (the case must remain).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Direction (beneficial/aggravating) is best recovered via a typed package-internal accessor on `CorrelationEngine` (Pattern 2 Option A), not by extending the public `Insight` or parsing copy. | Architecture Patterns → Pattern 2 | If planning prefers showing direction on correlations too, Option B (additive `Insight.direction`) may be chosen instead — both are valid; A1 is a recommendation, not a constraint. Low risk: both keep direction typed. |
| A2 | Recommendations should be **correlations-only** for v1 (not also trend-seeded). | Alternatives Considered | CONTEXT leaves trend-seeding to discretion; if the planner wants trend-based prompts, an additional (simpler) trend path is added. Low risk. |
| A3 | `topN == 3` for recommendations (tighter than correlations' 5). | Pattern 4 | D-02 says "3–5"; the exact cap is discretion. Trivially tunable constant. |
| A4 | No SwiftData schema bump is needed (existing `CachedInsight`/`DismissedInsight` are kind-agnostic). | Runtime State Inventory | If a future requirement needs to persist recommendation-specific metadata, a schema bump would be required — but for v1 the string `signalKey` + Codable `Insight` suffice. Verified against the Phase-8 cache contract; low risk. |
| A5 | The chosen discussion-prompt words ("explore", "discuss") are clean against the imperative substring scan. | Pitfall 2 | Must be unit-asserted; a substring collision would force a wording change. Low risk but MUST be tested. |

## Open Questions

1. **Direction recovery shape (Option A vs B).**
   - What we know: direction is typed inside `CorrelationEngine.Candidate` but absent from the public `Insight`.
   - What's unclear: whether the dashboard also wants direction shown on `.correlation` cards (which would favor Option B).
   - Recommendation: default to Option A (package-internal typed accessor) unless the UI-SPEC for correlations is revisited; defer to planning.

2. **Trend-seeded recommendations (yes/no).**
   - What we know: D-01/D-09 allow strong trends to optionally seed recommendations; a trend lacks a factor pairing for "feel better when {factor}".
   - What's unclear: product appetite for a "your {symptom} has been trending up — something you might explore" prompt.
   - Recommendation: correlations-only for v1; revisit in Phase 12 if useful.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode 26 toolchain | build + Swift Testing | assumed ✓ (project baseline) | 26.5 | — |
| `EndoInsights` SPM package | the new producer | ✓ (in-repo) | local | — |
| iOS 18 Simulator | UI dashboard E2E | assumed ✓ | 18+ | engine/copy unit suites run without UI |

**Missing dependencies with no fallback:** None identified — this phase adds only source files to existing targets.
**Missing dependencies with fallback:** UI E2E needs a simulator; the substantive engine + honesty + copy contracts are simulator-free unit tests.

## Sources

### Primary (HIGH confidence)
- `EndoInsights/Sources/EndoInsights/{Insight,InsightsEngine,HonestyGate,CorrelationEngine,CyclePhaseMapper,TrendsEngine,ForbiddenTerms}.swift` — the exact composition/gate/id/producer patterns to mirror (read directly).
- `EndoInsights/Tests/EndoInsightsTests/{HonestyGateTests,InsightsEngineTests,CorrelationEngineTests,BoundaryLintTests}.swift` — the honesty/fixture/boundary test contracts to extend.
- `Sources/EndoApp/Features/Insights/{InsightsView,InsightCardView,InsightsCopy}.swift` — the dashboard section + kind-agnostic card + auditable-copy patterns.
- `Sources/EndoApp/Content/ClaimsPolicy.swift` + `Tests/EndoAppTests/CopyAuditTests.swift` — the copy-audit policy + scans to layer the imperative scan onto.
- `.planning/phases/11-personalized-recommendations/{11-CONTEXT.md,11-UI-SPEC.md}` — locked decisions D-01..D-09 + the UI contract.
- `.planning/REQUIREMENTS.md` (INST-05), `.planning/ROADMAP.md` (Phase 11 goal + 3 success criteria), `CLAUDE.md`/PROJECT stack.

### Secondary (MEDIUM confidence)
- `.planning/config.json` — `nyquist_validation: true` (Validation Architecture section required), `security_enforcement` posture inferred ON.

### Tertiary (LOW confidence)
- None. This phase required no external/web research; all findings are from in-repo source.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies; all first-party + in-repo, verified against source and PROJECT "What NOT to Use".
- Architecture: HIGH — every seam read directly; the only non-trivial decision (direction recovery) is documented with two concrete typed options.
- Pitfalls: HIGH — each pitfall is grounded in a specific source comment (the `minSample(.recommendation)==14` placeholder, the `ForbiddenTerms` substring trap, the `HonestyGateTests` "add your fixtures" contract, the gendered "her"-in-"higher" trap).

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (stable — in-repo patterns; revisit only if the `EndoInsights` public API or `HonestyGate` table changes).
