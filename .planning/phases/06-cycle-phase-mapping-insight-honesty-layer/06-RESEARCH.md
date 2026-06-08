# Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer - Research

**Researched:** 2026-06-08
**Domain:** On-device, Foundation-only insights engine (cycle-phase windowing + cross-cutting honesty gate) + CloudKit-safe SwiftData additive migration for dismissal
**Confidence:** HIGH (the entire phase is a closed-world port/extension of existing, read code — `CycleMath`, `TrendsEngine`, `Insight`, the SchemaV1→V3 additive-migration pattern; no new third-party packages; toolchain verified present)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
**Area 1 — Cycle-phase model (INST-02)**
- **D-01:** A small **honest phase set** — `CyclePhase` enum: menstrual / follicular / ovulatory(estimated) / luteal — as **estimated windows**, populated ONLY when a reliable cycle exists. Phases are estimates, not precise; never assumes a textbook 28-day cycle.
- **D-02:** **Derived** from period runs (consecutive bleeding days) + median start-to-start cycle length: bucket each day relative to the last period start; ovulation ≈ 14 days **before the next predicted period** with a **wide uncertainty band**; luteal = post-ovulation→next period. Phase confidence (low/medium) scales with cycle regularity + recorded-cycle count.
- **D-03:** **Suppressed → NO phase mapping** (reuse the `CycleMath` suppressed short-circuit). **Irregular** (high start-to-start variance / too few cycles) → omit phase mapping OR very-low confidence, gated on regularity. **Never invent a phase** (SC1).
- **D-04:** Cycle-phase insights = **descriptive, non-causal associations** ("symptom X tends to co-occur with the luteal phase"), gated by min-sample + confidence (per-phase symptom frequency/severity). `kind == .cyclePhase`.

**Area 2 — The honesty gate (INST-04 — the cross-cutting core)**
- **D-05:** A shared **`HonestyGate` in `EndoInsights`** that ALL insight producers pass candidate insights through — the **single choke-point**. The engine's public API returns only gated insights.
- **D-06:** A **per-kind minimum-sample threshold** (≥N days / ≥N cycles by kind) below which the insight is **WITHHELD ENTIRELY** (not shown with a caveat) — SC3.
- **D-07:** A **confidence floor** below which insights are **withheld, not ranked**. Confidence = sample size + effect strength + (regularity, for cycle-phase).
- **D-08:** **Non-causal language ENFORCED** — a forbidden-term check ("causes", "trigger", "will", "because", "due to", …) on every Insight headline/detail (extends the `ClaimsPolicy` posture; package-local since `EndoInsights` is Foundation-only). Insights use "possible association", "tends to co-occur with", "may relate to". A test asserts **zero** forbidden terms across all emitted insights.

**Area 3 — Dismissal (INST-04 SC4)**
- **D-09:** User dismisses an insight; recorded by the insight's **stable signal key** (not the full id) so the same recurring insight stays dismissed across recomputation.
- **D-10:** Persisted as a **CloudKit-safe SwiftData `@Model DismissedInsight { id: UUID, signalKey: String?, dismissedAt: Date }`** (append-only; all properties optional/defaulted; NO `.unique`; app-generated UUID). New additive **`SchemaV4`** + lightweight V3→V4 migration + a `SchemaMigrationTests` V3→V4 survival case. Repository CRUD: `dismissInsight(signalKey:)`, `dismissedSignalKeys() -> Set<String>`.
- **D-11:** Filtered by passing **`dismissed: Set<String>`** into the engine's public API → excluded at the **same choke-point** as withholding.
- **D-12:** A dismissed signal **stays dismissed**. (Resurface-if-stronger deferred.)

**Area 4 — Engine API & testing**
- **D-13:** A single public **`InsightsEngine.insights(from snapshots: [DaySnapshot], asOf today: Date, dismissed: Set<String>) -> [Insight]`**. `TrendsEngine` becomes **package-internal**.
- **D-14:** A **`CyclePhaseMapper` in `EndoInsights`** deriving cycle phases from the **bleeding data already in `DaySnapshot`** (Foundation-only, self-contained — does NOT import app-side `CycleMath`). Re-derives period runs + median length, mirroring `CycleMath`'s pure approach.
- **D-15:** **Swift Testing fixtures:** regular-cycle / suppressed / irregular / under-sampled / causal-language guard / dismissal-exclusion. Determinism via injected `today`.
- **D-16:** The `HonestyGate` is the **mandatory choke-point** for all future insight kinds (Ph7/11/12) — lock the API now.

**Architecture & migration**
- **D-17:** Cycle-phase derivation is **ported into the package** as pure Foundation-only code mirroring `CycleMath`. The app's `CycleMath` stays for the Home cycle summary. The slight duplication is acceptable for SC1 isolation.
- **D-18:** New additive **`SchemaV4`** for `DismissedInsight` + lightweight V3→V4 migration + `SchemaMigrationTests` V3→V4. Repository extends with dismissal CRUD (consent-gated).

### Claude's Discretion
Exact `CyclePhase` window boundaries, the confidence formula + numeric thresholds, the forbidden-term list, the `InsightsEngine` internal structure, and the `DismissedInsight` model shape are left to research/planning.

### Deferred Ideas (OUT OF SCOPE)
- Correlations & trigger discovery → Phase 7 (INST-03).
- The dashboard / Swift Charts UI + the dismiss affordance rendering → Phase 8.
- Recommendations → Phase 11; flare → Phase 12.
- Insight re-emergence (resurface a dismissed signal if much stronger) → future.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **INST-02** | User can see how symptoms map onto their cycle phases (cycle-phase mapping) | The `CyclePhaseMapper` (Standard Stack §1; Phase-window math §Architecture Pattern 1) re-derives period runs + median length from `[DaySnapshot]` and labels each day's `CyclePhase`, then produces per-phase symptom-association `.cyclePhase` insights (Pattern 4). Exactly mirrors `CycleMath`'s suppressed short-circuit + `<2`-cycle guard so suppressed/irregular users get NO phantom phase (SC1). |
| **INST-04** | Every insight carries an honesty layer — non-causal language, a confidence indicator, dismissal, low-confidence withheld | The `HonestyGate` choke-point (Pattern 2) enforces per-kind min-sample (D-06), confidence floor (D-07), and a package-local forbidden-term scan (D-08). `DismissedInsight` `@Model` + SchemaV4 additive migration (Pattern 3) + `Insight.signalKey` surfaced (the load-bearing gap, §Open Question 1) implement durable, stable-key dismissal (SC4). |
</phase_requirements>

## Summary

This phase is a **closed-world port-and-extend**: every pattern it needs already exists and has been read. There is no new third-party dependency, no new framework surface, and no novel algorithm — the work is (a) lifting `CycleMath`'s pure period-run + median-gap + suppressed-short-circuit logic into a Foundation-only `CyclePhaseMapper` and adding phase-window bucketing on top; (b) building a `HonestyGate` that mirrors the thresholding `TrendsEngine` already does inline, hoisted into one reusable choke-point with an added forbidden-term scan modelled on `ClaimsPolicy`; (c) wrapping both behind a single public `InsightsEngine.insights(from:asOf:dismissed:)` and demoting `TrendsEngine` to `internal`; and (d) one additive SwiftData `SchemaV4` for a `DismissedInsight` `@Model`, following the exact SchemaV1→V2→V3 additive-migration recipe already proven by `SchemaMigrationTests`.

The single most important **discovered gap**: `Insight` does **not** currently surface `signalKey` as a stored property — it is consumed only inside `Insight.derived(...)` to derive the deterministic id and then discarded. Dismissal (D-09) is keyed on a *stable signal key*, so this phase **must add `public let signalKey: String` to `Insight`** (set inside `derived`). Without it there is no stable dismissal key on the emitted value, and the dismissal filter can only match on the full `id` (which is fine today but violates D-09's "stable signal key, not the full id" intent and breaks the moment copy changes). This is low-risk (additive, the value stays `Equatable`/`Sendable`) but load-bearing — call it out in the plan as task 1.

Two safety facts de-risk the API change: (1) `TrendsEngine` has **zero call sites outside the package** (the app's `Repository` imports `EndoInsights` only to build `DaySnapshot`s; nothing calls `.trends(...)`), and every package test uses `@testable import EndoInsights`, so demoting `TrendsEngine` to `internal` breaks **no** existing test. (2) Routing `TrendsEngine`'s output through a `HonestyGate` that is *at least as permissive* as `TrendsEngine`'s existing inline gates causes **no Phase-5 behaviour regression** — the gate must be a superset filter, and the planner should make the gate's trend thresholds equal to `TrendsEngine`'s current ones so existing fixtures stay green (see Pitfall 1).

**Primary recommendation:** Port `CycleMath` → `CyclePhaseMapper` (re-deriving from `DaySnapshot.flow`/`suppressed`); add `signalKey` to `Insight`; build a `HonestyGate` whose trend thresholds match `TrendsEngine`'s current behaviour exactly; compose everything behind `InsightsEngine.insights(from:asOf:dismissed:)`; make `TrendsEngine` `internal`; add an additive `SchemaV4` + `DismissedInsight` `@Model` + V3→V4 lightweight migration + a survival test, exactly mirroring the existing V2→V3 case.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Derive cycle phases from bleeding | `EndoInsights` package (pure Foundation) | — | SC1: phase math must be store-free, deterministic, unit-testable; mirrors `CycleMath` |
| Per-phase symptom-association insights | `EndoInsights` package | — | Pure value-in/value-out over `[DaySnapshot]`; same tier as `TrendsEngine` |
| Honesty gating (min-sample, confidence floor, language) | `EndoInsights` package | — | D-05: the single choke-point lives where all producers (trends now, Ph7/11/12 later) live |
| Dismissal *filtering* | `EndoInsights` package | — | D-11: a `Set<String>` param keeps the engine pure; filter at the same choke-point |
| Dismissal *persistence* | App `Persistence` tier (SwiftData `@Model` + Repository) | CloudKit (sync transport) | D-10: durable, CloudKit-safe append-only record; the package never touches the store |
| Schema versioning + migration | App `Persistence` tier | — | SwiftData `VersionedSchema`/`SchemaMigrationPlan` are SwiftData-only — app-side by definition |
| Mapping `@Model` → `dismissedSignalKeys()` and into the engine | App `Repository` seam | — | The ONE place that imports both SwiftData and `EndoInsights` (already established Phase 5) |

## Standard Stack

This phase adds **no new packages.** It uses only the already-present first-party frameworks and the existing local SPM package.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `EndoInsights` (local SPM, Foundation-only) | in-repo (Phase 5) | Houses `CyclePhaseMapper`, `HonestyGate`, `CyclePhase`, public `InsightsEngine` | `[VERIFIED: EndoInsights/Package.swift]` Zero-dependency manifest = SC1 boundary enforced by the linker |
| `Foundation` (`Calendar`/`Date`/`DateComponents`) | iOS 18 SDK | Day-bucketing, period runs, median gaps — DST-safe via `Calendar`, never epoch math | `[VERIFIED: Sources/EndoApp/Domain/CycleMath.swift]` Exact pattern already in use |
| `SwiftData` (`@Model`, `VersionedSchema`, `MigrationStage.lightweight`) | iOS 18 SDK | `DismissedInsight` model + additive `SchemaV4` + V3→V4 migration | `[VERIFIED: Sources/EndoApp/Persistence/SchemaV3.swift, MigrationPlan.swift]` Additive-only recipe proven by `SchemaMigrationTests` |
| `Swift Testing` (`@Test`/`@Suite`/`#expect`) | Xcode 26 / Swift 6.3 | All new package + migration fixtures | `[VERIFIED: EndoInsights/Tests/...]` Already the suite's test framework |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `XcodeGen` | 2.45.4 (verified on PATH) | Regenerate the project after adding `SchemaV4.swift`/new package files | After any new source file; `xcodegen generate` before build |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `CyclePhaseMapper` re-deriving from `DaySnapshot` (D-14, self-contained) | Port `CycleSummary` as a value type and pass it in | Rejected by D-14/D-17: would re-introduce a cross-module value dependency and a second `Confidence` enum boundary. Re-deriving from `[DaySnapshot]` keeps the package self-contained and SC1-clean. The slight duplication is explicitly accepted (D-17). |
| Package-local forbidden-term constant (D-08) | Import `ClaimsPolicy` from the app | Impossible — `ClaimsPolicy` lives in the app target; `EndoInsights` is Foundation-only and cannot import it (SC1). Mirror it as a package-local `static let`. |
| `MigrationStage.custom` for V3→V4 | — | **Forbidden** for this codebase: custom hooks (`willMigrate`/`didMigrate`) do NOT fire once CloudKit is enabled (Phase 4 shipped it). Always `.lightweight` + additive. `[VERIFIED: Sources/EndoApp/Persistence/MigrationPlan.swift]` |

**Installation:** None. No `swift package add`, no Xcode package add. Verify after adding files:
```bash
xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'
```

**Version verification:** `[VERIFIED: local toolchain]` Xcode 26.5 / Apple Swift 6.3.2 / xcodegen 2.45.4 / iPhone 16 simulator booted — all present 2026-06-08.

## Package Legitimacy Audit

**Not applicable.** This phase installs **zero** external packages. All code uses first-party Apple frameworks (`Foundation`, `SwiftData`, `Swift Testing`) bundled with the iOS 18 SDK / Xcode 26 toolchain, plus the existing in-repo `EndoInsights` local SPM package (Phase 5, zero-dependency). No npm/PyPI/crates/SPM-remote registry is touched, so there is no slopsquatting surface. The `EndoInsights` SC1 boundary is actively enforced by `BoundaryLintTests` (fails the suite on any non-`Foundation` import) `[VERIFIED: EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift]`.

## Architecture Patterns

### System Architecture Diagram

```
                          ┌────────────────────────────────────────────────────────┐
   App (Phase 8 wires)    │  Repository.daySnapshots()  →  [DaySnapshot]            │
   ─ SwiftData @Model ───►│  Repository.dismissedSignalKeys()  →  Set<String>      │
                          └───────────────┬────────────────────────────────────────┘
                                          │  (both passed in — engine stays pure, SC1)
                                          ▼
   ┌──────────────────────────────────────────────────────────────────────────────────┐
   │  EndoInsights (Foundation-only)                                                    │
   │                                                                                    │
   │   InsightsEngine.insights(from: [DaySnapshot], asOf: Date, dismissed: Set<String>) │
   │            │                                                                       │
   │            ├──► TrendsEngine.trends(...)        ─┐                                  │
   │            │      (now INTERNAL)                 │  candidate                       │
   │            ├──► CyclePhaseMapper.insights(...)  ─┤  [Insight]                       │
   │            │      • suppressed?  → []  (SC1)     │                                  │
   │            │      • <2 cycles?   → []            │                                  │
   │            │      • else: label each day's       │                                  │
   │            │        CyclePhase, emit per-phase   │                                  │
   │            │        symptom associations         │                                  │
   │            │                                      ▼                                 │
   │            └──────────────────────────►  HonestyGate.apply(candidates:, dismissed:)│
   │                                              │  1. drop kind == in dismissed set    │
   │                                              │     (match on Insight.signalKey)     │
   │                                              │  2. drop below per-kind min-sample   │
   │                                              │  3. drop below confidence floor      │
   │                                              │  4. ASSERT zero forbidden terms      │
   │                                              ▼                                       │
   │                                       gated, dismissal-filtered [Insight]           │
   └──────────────────────────────────────────────┬───────────────────────────────────┘
                                                   ▼
                                  returned to caller (Phase 8 renders; dismiss action
                                  → Repository.dismissInsight(signalKey:) → SchemaV4)
```

### Recommended Project Structure
```
EndoInsights/Sources/EndoInsights/
├── Insight.swift            # EXTEND: add `public let signalKey: String` (load-bearing, OQ1)
├── DaySnapshot.swift        # unchanged (CyclePhaseMapper reads .flow/.suppressed/.symptoms)
├── TrendsEngine.swift       # CHANGE: `public enum` → `enum` (internal), no logic change
├── CyclePhase.swift         # NEW: the CyclePhase enum + per-day labelling result type
├── CyclePhaseMapper.swift   # NEW: ported CycleMath math + phase-window bucketing + insights
├── HonestyGate.swift        # NEW: the single choke-point (thresholds + forbidden-term scan)
├── ForbiddenTerms.swift     # NEW (or inside HonestyGate): package-local ClaimsPolicy mirror
└── InsightsEngine.swift     # NEW: public composition root insights(from:asOf:dismissed:)

EndoInsights/Tests/EndoInsightsTests/
├── CyclePhaseMapperTests.swift   # NEW: regular / suppressed / irregular / under-sampled
├── HonestyGateTests.swift        # NEW: min-sample, confidence floor, forbidden-term ZERO assertion
├── InsightsEngineTests.swift     # NEW: composition + dismissal-exclusion + Phase-5 no-regression
└── SnapshotFixtures.swift        # EXTEND: regularCycleFixture / irregularCycleFixture

Sources/EndoApp/Persistence/
├── Models/DismissedInsight.swift # NEW: CloudKit-safe @Model under `extension SchemaV1`
├── SchemaV4.swift                # NEW: additive VersionedSchema (V3 set + DismissedInsight)
├── MigrationPlan.swift           # EXTEND: add v3toV4 lightweight stage
└── Repository.swift              # EXTEND: dismissInsight(signalKey:) / dismissedSignalKeys()

Tests/EndoAppTests/
└── SchemaMigrationTests.swift    # EXTEND: v3Store_survivesLightweightMigrationToV4 case
```

### Pattern 1: `CyclePhaseMapper` — phase-window math (the concrete answer to INST-02)

**What:** Re-derive period runs + median start-to-start length from `[DaySnapshot]` (mirroring `CycleMath`), then label each in-range day with a `CyclePhase` using windows anchored on the **last period start** and the **median cycle length** — never a textbook 28.

**The exact gate (mirror `CycleMath.summarize` precisely):**
```swift
// Source pattern: Sources/EndoApp/Domain/CycleMath.swift (ported, Foundation-only)
public enum CyclePhase: String, Sendable, Equatable, CaseIterable {
    case menstrual, follicular, ovulatory, luteal
}

enum CyclePhaseMapper {
    // 1. SUPPRESSED short-circuit — ANY suppressed day in the stream → NO phases (SC1).
    //    Mirrors TrendsEngine.bleedingTrend's `anySuppressed` AND CycleMath's `if isSuppressed`.
    // 2. periodRuns(bleedingDays)  → starts = run.first   (PORT verbatim from CycleMath)
    // 3. consecutiveGaps(starts)   → start-to-start gaps  (PORT verbatim)
    // 4. guard gaps.count >= 2     → else return []        ("<2 cycles ⇒ no phase", CYCL-04 spirit)
    // 5. median = medianInt(gaps); spread = spread(gaps)   (PORT verbatim)
    // 6. confidence = phaseConfidence(cycleCount: gaps.count, spread:)
    // 7. IRREGULAR gate: if spread too wide (recommend > 9 days → confidence .none) → return []
    //    (D-03: "omit phase mapping OR very-low confidence, gated on regularity")
}
```
`bleedingDays` is computed inside the mapper as `snapshots.filter { $0.flow != nil }.map(\.day)` — directly answering "re-derive from `DaySnapshot` bleeding" (D-14). Pass the same injected `today` + `calendar` as `TrendsEngine`.

**Concrete phase-window boundaries** (relative to `lastStart`, length `= median`, `[ASSUMED]` — Claude's discretion per CONTEXT, needs confirmation in discuss/plan):

| Phase | Window (days from last period start `S`, where `M = median cycle length`) | Notes |
|-------|--------------------------------------------------------------------------|-------|
| **menstrual** | the actual recorded bleeding-run days of the current cycle (not a fixed count) | Derived from the run, never assumed length — honest |
| **follicular** | end-of-bleeding `… → (S + M − 14 − band)` | The "everything before the ovulatory band" remainder |
| **ovulatory (estimated)** | `(S + M − 14 − band) … (S + M − 14 + band)` | **Centered 14 days BEFORE the next predicted start** (`S + M`), per D-02. `band` WIDE: recommend `band = max(2, spread/2)` so irregular cycles widen the band |
| **luteal** | `(S + M − 14 + band) … (S + M)` | Post-ovulation → next predicted period (D-02) |

Rationale for "14 before next period": the luteal phase length is the most biologically stable (~14 days); follicular length absorbs cycle-length variance. This is why ovulation is anchored to `next_start − 14`, never `start + 14`. `[CITED: D-02]` (the locked decision encodes exactly this); the 14-day luteal heuristic itself is `[ASSUMED]` (clinical common knowledge, not verified against a source this session).

**Marking confidence by regularity + cycle count** — port `CycleMath.confidenceLevel` but the mapper's contract is *only ever low/medium* for phases (D-01/D-02: "Phase confidence (low/medium)"), because phase *windows* are estimates even at best. Recommended mapping (`[ASSUMED]`, discretion):
```swift
// cycleCount = gaps.count, spread = max-min gap
switch (cycleCount, spread) {
case let (c, s) where c >= 3 && s <= 3:  return .medium   // tight, ≥3 cycles → best a phase gets
case let (_, s) where s <= 9:            return .low      // usable but wide
default:                                 return nil        // too irregular → NO phases (D-03)
}
```
Note this caps at `.medium`, deliberately diverging from `CycleMath`'s `.high` — phases never claim high confidence.

### Pattern 2: `HonestyGate` — the single choke-point (the concrete answer to INST-04)

**What:** One pure function every producer's candidate insights pass through. Three filters + one assertion, in order: dismissal-exclusion, per-kind min-sample, confidence floor, forbidden-term scan.

```swift
// Foundation-only. The mandatory choke-point (D-05/D-16) — lock this signature now.
enum HonestyGate {
    /// Per-kind minimum sample (D-06). Below this the insight is WITHHELD ENTIRELY.
    /// Keyed by InsightKind so Ph7/11/12 extend by adding a case, not redesigning.
    static func minSample(for kind: InsightKind) -> Int {
        switch kind {
        case .trend:          return 3   // == TrendsEngine.minLoggedDays — NO Phase-5 regression
        case .cyclePhase:     return 2   // ≥2 recorded cycles (mirrors CycleMath's gaps.count >= 2)
        case .correlation:    return 14  // [ASSUMED] Ph7 placeholder — confirm in Phase 7
        case .recommendation: return 14  // [ASSUMED] Ph11 placeholder
        case .flare:          return 21  // [ASSUMED] Ph12 placeholder
        }
    }

    /// Confidence floor (D-07). Default withholds `.none`; the "not enough data yet" trend
    /// Insight is the ONE deliberate exception (it IS the honest signal) — see Pitfall 1.
    static let confidenceFloor: InsightConfidence = .low

    static func apply(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        candidates.filter { insight in
            !dismissed.contains(insight.signalKey)              // D-11 dismissal, stable key (OQ1)
            // && passes min-sample (carried via confidence/points or an explicit sampleCount)
            // && insight.confidence >= confidenceFloor  (with the not-enough-data carve-out)
            // && containsNoForbiddenTerm(insight)        (assert, see below)
        }
    }
}
```

**Non-causal enforcement (D-08)** — a package-local mirror of `ClaimsPolicy.forbiddenVerbs` plus the causal connectives D-08 names:
```swift
// Package-local (cannot import the app's ClaimsPolicy — SC1). Mirror + extend.
enum ForbiddenTerms {
    static let causal: [String] = [
        // from ClaimsPolicy.forbiddenVerbs:
        "trigger", "causes", "cause", "will",
        "diagnose", "detect", "treat", "cure", "prevent",
        // D-08 adds the causal connectives:
        "because", "due to", "leads to", "results in", "responsible for",
    ]
    static func violation(in text: String) -> String? {
        let lower = text.lowercased()
        return causal.first { lower.contains($0) }   // substring, case-insensitive
    }
}
```
**Enforcement mechanism (D-08 "asserts zero forbidden terms"):** the gate calls `ForbiddenTerms.violation(in:)` on each insight's `headline` + `detail`. Two complementary uses:
1. **Defence-in-depth in the gate:** `assert(violation == nil, ...)` in debug builds (mirrors `Repository.assertConsented()`'s debug-assert pattern). Do NOT silently drop in release — a forbidden term is a *programmer error*, not user data.
2. **The required test (D-08):** a Swift Testing case runs `InsightsEngine.insights(...)` over EVERY fixture and `#expect` that no emitted `headline`/`detail` contains any `ForbiddenTerms.causal` substring — asserting **zero** across all emitted insights.

⚠ **Substring pitfall:** "cause"/"will" are substrings of innocent words ("because" contains "cause"; no obvious "will" trap but watch "willing"). Order matters and the planner should either (a) use word-boundary matching, or (b) accept that the *engine's own copy* is fully controlled and curated to avoid even substring collisions (recommended — the copy is hand-written, finite, and tested). Document this as a known sharp edge.

### Pattern 3: `DismissedInsight` `@Model` + additive `SchemaV4` (the concrete answer to INST-04 SC4 / D-10)

**What:** Exactly the FactorReading/SchemaV3 recipe, one more time.

```swift
// Sources/EndoApp/Persistence/Models/DismissedInsight.swift
// CloudKit-safe, append-only (D-10): every property optional/defaulted, NO @Attribute(.unique),
// app-generated UUID. Nested under `extension SchemaV1` so the entity NAME is stable (A1 rule).
extension SchemaV1 {
    @Model
    final class DismissedInsight {
        var id: UUID = UUID()                 // app-generated, NOT .unique (CloudKit rule)
        var signalKey: String? = nil          // the STABLE dismissal key (D-09); optional (CloudKit rule)
        var dismissedAt: Date = Date()        // defaulted (CloudKit rule)

        init(id: UUID = UUID(), signalKey: String? = nil, dismissedAt: Date = .now) {
            self.id = id; self.signalKey = signalKey; self.dismissedAt = dismissedAt
        }
    }
}
```

```swift
// Sources/EndoApp/Persistence/SchemaV4.swift — ADDITIVE over V3 (one new model).
enum SchemaV4: VersionedSchema {
    static let versionIdentifier = Schema.Version(4, 0, 0)
    static var models: [any PersistentModel.Type] {
        [SchemaV1.DayEntry.self, SchemaV1.BleedingEvent.self, SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self, SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self, SchemaV1.Medication.self,
         SchemaV1.DismissedInsight.self]   // <-- the only delta vs V3
    }
}
```

```swift
// MigrationPlan.swift — add the lightweight stage (NEVER .custom — fires-not-under-CloudKit rule).
static var schemas: [any VersionedSchema.Type] { [SchemaV1.self, SchemaV2.self, SchemaV3.self, SchemaV4.self] }
static var stages: [MigrationStage] { [v1toV2, v2toV3, v3toV4] }
static let v3toV4 = MigrationStage.lightweight(fromVersion: SchemaV3.self, toVersion: SchemaV4.self)
```

**Repository CRUD (consent-gated, like every sibling — `assertConsented()` first):**
```swift
func dismissInsight(signalKey: String) throws {
    assertConsented()
    context.insert(SchemaV1.DismissedInsight(signalKey: signalKey))  // append-only, never mutate/dedupe
    try context.save()
}
func dismissedSignalKeys() throws -> Set<String> {
    assertConsented()
    return Set(try context.fetch(FetchDescriptor<SchemaV1.DismissedInsight>()).compactMap(\.signalKey))
}
```
Append-only (D-10/D-12): never dedupe or delete on dismiss — duplicates across devices collapse harmlessly because `dismissedSignalKeys()` returns a `Set`. This is the CloudKit "prefer append-only events over a mutable record" rule (Pitfall 5).

**ModelContainer wiring:** the app's container is built from `EndoMigrationPlan` and the current schema; bumping the plan + adding `SchemaV4` to the container's schema list is the only wiring change. `[VERIFIED: Sources/EndoApp/Persistence/ModelContainer+Config.swift exists]` — the planner must confirm the container references the latest schema/plan (it already does for V3).

### Pattern 4: Per-phase symptom-association insights (`kind == .cyclePhase`)

**What:** After labelling each in-range day with a `CyclePhase`, compute per-(phase, symptom) frequency/severity and emit **descriptive, non-causal** associations (D-04): e.g. *"Pelvic pain tended to co-occur with your luteal phase (logged on 6 of 9 luteal days)."* Gate by min-sample (≥2 cycles, via HonestyGate) and confidence. `signalKey` recommendation: `"cyclePhase.luteal.symptom.pelvicPain"` so dismissal is per-(phase, symptom) and stable across recomputation.

**`window` field:** every `Insight` carries a `TrendWindow` (required). Cycle-phase insights are **not** rolling-window trends, so use `window = .allTime` for the id derivation and signalKey stability. `[ASSUMED]` — confirm in plan; the alternative is adding a `.cycle` case to `TrendWindow`, which is a wider contract change and not recommended.

### Anti-Patterns to Avoid
- **Re-deriving a `.high` phase confidence:** phases are estimates; cap at `.medium` (Pattern 1). A "high confidence ovulation window" is exactly the false-precision Pitfall 1 warns against.
- **Silently dropping a forbidden-term insight in release:** that hides a copy bug. Assert in debug; the copy is curated and tested, so release should never hit it.
- **Mutating/deduping `DismissedInsight` on write:** breaks the append-only CloudKit-safe contract (Pitfall 5). Always insert; dedupe on read via `Set`.
- **`MigrationStage.custom` for V3→V4:** custom hooks don't fire under CloudKit (shipped Phase 4). Additive + lightweight only.
- **Phantom phase for suppressed/irregular:** the #1 endo pitfall. The mapper's first two guards (suppressed short-circuit, `<2` cycles) must return `[]`, proven by fixtures.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Period-run grouping, median gap, spread, confidence | A fresh cycle algorithm | **Port `CycleMath.periodRuns/consecutiveGaps/medianInt/spread/confidenceLevel` verbatim** | Already written, unit-tested, DST-safe; D-17 explicitly mandates mirroring it |
| Deterministic insight id | A new id scheme | The existing `DeterministicUUID.make(kind:window:signalKey:)` | Already the Phase-5 contract; reuse for `.cyclePhase` ids unchanged |
| Day-bucketing / date math | Epoch arithmetic (`timeIntervalSince`) | `Calendar.dateComponents([.day]...)` + `startOfDay` | DST/timezone-safe; the whole codebase uses this. Epoch math silently drifts across DST |
| Schema migration | A custom data-transform stage | `MigrationStage.lightweight` + additive model | Custom hooks don't fire under CloudKit; the proven recipe is additive-only |
| Non-causal copy enforcement | Trusting reviewers to catch causal words | The `ForbiddenTerms` scan + the zero-terms test | D-08: "enforced by a test, not trusted" |

**Key insight:** Nothing in this phase is novel. Every primitive exists; the work is composition + one additive model. Resist the urge to "improve" `CycleMath`'s math while porting — port it identically so the two stay reasoned-about-together, and add phase-windowing as a *new layer on top*.

## Runtime State Inventory

This is a feature/extension phase, **not** a rename/refactor/migration of existing identifiers — but it DOES introduce one new persisted entity, so the relevant runtime-state question is "what stored state does this phase create, and does it need a data migration?"

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **NEW:** `DismissedInsight` rows (signalKey + dismissedAt) in the SwiftData store, mirrored to the user's private CloudKit DB. No *existing* records are renamed or re-keyed. | Additive schema only — no data migration of existing rows. V3→V4 is purely additive (new entity). |
| Live service config | None — CloudKit schema for `DismissedInsight` is deployed via the normal SwiftData→CloudKit auto-schema path (same as V3 models). | Phase 4's deploy-schema-to-Production discipline applies at ship time (not this phase's code). |
| OS-registered state | None. | None. |
| Secrets/env vars | None. | None. |
| Build artifacts | New `.swift` files (`SchemaV4.swift`, `DismissedInsight.swift`, package files) require `xcodegen generate` to enter the Xcode project; the SPM package picks up new files in `Sources/EndoInsights/` automatically. | Run `xcodegen generate` before building. |

**The stable-signalKey re-derivation question (D-09):** A dismissed insight must stay dismissed across recomputation. This works **only if** `signalKey` is (a) surfaced on `Insight` (currently it is NOT — see Open Question 1) and (b) deterministically reproduced each compute. For trends the signalKey is already stable (e.g. `"symptom.pelvicPain.severity"`, independent of values). For cycle-phase, recommend `"cyclePhase.<phase>.symptom.<type>"` — independent of cycle counts/dates so it survives new logging. **Verified by:** reading `Insight.swift` (signalKey flows into `DeterministicUUID` but is discarded) + `TrendsEngine.swift` (signalKeys are value-independent strings).

## Common Pitfalls

### Pitfall 1: Routing TrendsEngine through the gate regresses Phase-5 fixtures
**What goes wrong:** The `HonestyGate`'s confidence floor or min-sample is stricter than `TrendsEngine`'s existing inline thresholds, so insights Phase-5 tests expect now get withheld → existing `TrendsEngineTests` fixtures fail.
**Why it happens:** `TrendsEngine` already emits a deliberate `.none`-confidence "not enough data yet" Insight (its honest signal). A naive `confidence >= .low` floor would withhold exactly that.
**How to avoid:** (1) Make `minSample(for: .trend) == TrendsEngine.minLoggedDays (3)`. (2) Carve out the "not enough data yet" insight from the confidence floor — it is the *intended* sub-threshold signal (its signalKey ends in `.insufficient`). (3) **Verification step:** run the full existing `EndoInsightsTests` suite after wiring `InsightsEngine` and confirm `TrendsEngineTests` + `SuppressedFixtureTests` stay green; add an explicit `InsightsEngineTests` case asserting `InsightsEngine.insights(...)` over a Phase-5 fixture returns a superset-or-equal of `TrendsEngine.trends(...)` (minus dismissed).
**Warning signs:** any Phase-5 fixture turns red after introducing the gate.

### Pitfall 2: Forbidden-term substring false positives
**What goes wrong:** `"cause"` matches inside `"because"`; a future "because"-free but "cause"-containing innocent phrase trips the test, or worse, the test passes because the engine never emits that word but a Phase-7 producer later does.
**Why it happens:** Case-insensitive `contains` is a substring match, not word-boundary.
**How to avoid:** Curate the engine's hand-written copy to avoid even substring collisions (the copy is finite and controlled), OR use word-boundary matching. Keep the `ForbiddenTerms` list and the copy reviewed together. Document the chosen approach so Phase 7/11/12 producers follow it.
**Warning signs:** the zero-terms test fails on copy that is semantically non-causal.

### Pitfall 3: Phantom phase for an irregular user
**What goes wrong:** A user with 2 wildly-spaced period starts (spread 40 days) gets a confidently-windowed phase map → false precision, the #1 endo pitfall.
**Why it happens:** `gaps.count >= 2` alone passes the gate; spread isn't checked.
**How to avoid:** The mapper's irregular gate (D-03) must return `[]` (no phases) when spread exceeds the threshold (recommend `spread > 9` → `nil` confidence → no phases). Fixture: `irregularCycleFixture` with starts at day 0, −20, −65 (gaps 20, 45; spread 25) must yield zero `.cyclePhase` insights.
**Warning signs:** the irregular fixture emits any `.cyclePhase` insight.

### Pitfall 4: Making `TrendsEngine` internal breaks a test
**What goes wrong:** Demoting `public enum TrendsEngine` → `enum TrendsEngine` makes it invisible to a non-`@testable` importer.
**Why it happens:** A test imports `EndoInsights` (not `@testable`).
**How to avoid:** **Verified non-issue** — every package test uses `@testable import EndoInsights` (grep-confirmed: `SuppressedFixtureTests`, `ContractTests`, `TrendsEngineTests`, `SnapshotFixtures`), and the app-side `Tests/EndoAppTests/DaySnapshotMappingTests.swift` imports `EndoInsights` only for `DaySnapshot`, never `TrendsEngine`. The change is safe. Still: keep the existing `TrendsEngineTests` as-is (they `@testable`-reach the now-internal type).
**Warning signs:** a compile error referencing `TrendsEngine` from a plain `import EndoInsights` site.

## Code Examples

### Re-deriving bleeding days inside the mapper (the D-14 self-contained pattern)
```swift
// Source pattern: mirrors Repository.daySnapshots() flow extraction + CycleMath.periodRuns
let bleedingDays = snapshots.filter { $0.flow != nil }.map(\.day)
let anySuppressed = snapshots.contains { $0.suppressed }
guard !anySuppressed else { return [] }            // SC1 suppressed short-circuit
let runs   = periodRuns(bleedingDays, calendar: calendar)   // PORTED verbatim from CycleMath
let starts = runs.compactMap(\.first)
let gaps   = consecutiveGaps(starts, calendar: calendar)
guard gaps.count >= 2, let lastStart = starts.last else { return [] }   // <2 cycles → no phases
```

### The public composition root
```swift
// Source: NEW InsightsEngine.swift — the ONE entry point (D-13). Lock this signature.
public enum InsightsEngine {
    public static func insights(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        dismissed: Set<String>,
        calendar: Calendar = .current
    ) -> [Insight] {
        let candidates =
            TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
          + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
        return HonestyGate.apply(candidates, dismissed: dismissed)
    }
}
```

### The migration survival test (extend SchemaMigrationTests, mirror V2→V3 exactly)
```swift
// Source pattern: Tests/EndoAppTests/SchemaMigrationTests.swift v2→v3 case, +1 version.
@Test func v3Store_survivesLightweightMigrationToV4_withNoDataLoss() async throws {
    // 1. Open at SchemaV3 (cloudKitDatabase: .none), insert a row across V3 models, close.
    // 2. Reopen the SAME file via makeModelContainer(url:) (uses EndoMigrationPlan → V4).
    // 3. #expect every V3 row survived.
    // 4. #expect FetchDescriptor<SchemaV1.DismissedInsight>() is queryable + empty.
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `TrendsEngine` public, called directly | `InsightsEngine` is the single public entry; `TrendsEngine` internal | This phase (D-13) | App calls one API; future kinds route through the same gate |
| `signalKey` consumed-and-discarded in `derived(...)` | `signalKey` a stored `public let` on `Insight` | This phase (OQ1) | Enables stable-key dismissal; the value stays `Equatable`/`Sendable` |
| Per-producer inline thresholds (TrendsEngine) | Centralized `HonestyGate` thresholds keyed by `InsightKind` | This phase (D-05/D-16) | One place to reason about withholding for Ph7/11/12 |
| SchemaV3 (7 models) | SchemaV4 (+ `DismissedInsight`) | This phase (D-18) | Additive; lightweight V3→V4 migration |

**Deprecated/outdated:** Nothing removed. `CycleMath`/`CycleSummary` stay app-side for the Home cycle summary (D-17) — the package re-derives independently; the duplication is intentional, not debt.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Phase-window boundaries (menstrual = recorded run; ovulatory centered at `next_start − 14` with `band = max(2, spread/2)`; luteal = post-ovulation → next start) | Pattern 1 | If boundaries are off, phase labels mislead — but D-02 *locks* the "14 before next predicted period, wide band" shape, so only the exact band width is discretionary. Low risk; tune in plan. |
| A2 | Phase confidence caps at `.medium` (never `.high`), mapping `(c≥3,s≤3)→.medium`, `(s≤9)→.low`, else no phases | Pattern 1 | Too-loose → phantom phases for irregular users (Pitfall 3). Mitigated by the irregular fixture. Tune thresholds in plan. |
| A3 | `minSample(.cyclePhase) = 2` cycles; `.trend = 3` (== current); Ph7/11/12 placeholders 14/14/21 | Pattern 2 | Trend value is verified-safe (matches `TrendsEngine.minLoggedDays`). cyclePhase=2 mirrors `CycleMath`. Future-kind numbers are placeholders to be set in their own phases. |
| A4 | `confidenceFloor = .low`, with a carve-out for the `.none` "not enough data yet" trend insight | Pattern 2 / Pitfall 1 | Without the carve-out, Phase-5 fixtures regress. The carve-out is the mitigation; verify with the existing suite. |
| A5 | Forbidden-term list = `ClaimsPolicy.forbiddenVerbs` + causal connectives ("because", "due to", "leads to", "results in", "responsible for") | Pattern 2 | A missing term lets a causal phrase through; an over-broad substring causes false positives (Pitfall 2). Curate copy + list together. |
| A6 | cycle-phase insights use `window = .allTime` (rather than adding a `TrendWindow.cycle` case) | Pattern 4 | If a `.cycle` window is later wanted for Phase 8 rendering, that's an additive enum case — low risk. |
| A7 | The 14-day luteal-phase heuristic is clinically standard | Pattern 1 | Clinical common knowledge but not source-verified this session. D-02 already encodes it as a locked decision, so the risk is owned by the decision, not the research. |

**Note:** A1–A7 are all within "Claude's Discretion" per CONTEXT.md §Claude's Discretion (window boundaries, confidence formula, thresholds, forbidden-term list, engine structure, model shape). They are recommendations for the planner to lock, not unverified facts presented as truth.

## Open Questions

1. **`Insight.signalKey` is not currently a stored property — must this phase add it?** (HIGH importance, load-bearing)
   - What we know: `signalKey` flows into `Insight.derived(...)` → `DeterministicUUID.make(...)` and is then **discarded**; `Insight` exposes `id`, `kind`, `headline`, `detail`, `window`, `confidence`, `points` — but no `signalKey`. `[VERIFIED: EndoInsights/Sources/EndoInsights/Insight.swift]`
   - What's unclear: nothing, really — D-09 explicitly says dismissal is keyed on the **stable signal key, not the full id**. That key has to live on the emitted value.
   - Recommendation: **Add `public let signalKey: String` to `Insight`, set inside `derived(...)`.** This is task 1 of the phase. Additive, keeps `Equatable`/`Sendable`. Without it the dismissal filter has no stable key to match and D-09 cannot be satisfied.

2. **Does `TrendWindow` need a `.cycle` case for cycle-phase insights?**
   - What we know: every `Insight` carries a required `TrendWindow`; cycle-phase mapping isn't a rolling window.
   - Recommendation: use `.allTime` (A6). Defer adding a `.cycle` case unless Phase 8 rendering needs the distinction — it's an additive change either way.

3. **Where does `dismissedSignalKeys()` get called?**
   - What we know: D-11/D-13 pass `dismissed: Set<String>` *into* the engine; Phase 8 wires the rendering + dismiss action.
   - Recommendation: this phase ships `Repository.dismissInsight/dismissedSignalKeys` + the engine `dismissed:` param + their tests, but the *live UI wiring* (call `dismissedSignalKeys()`, render, fire `dismissInsight`) is **Phase 8** (out of scope — CONTEXT §Deferred). Verify the engine param + repository CRUD are independently testable without UI.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode / iOS 18 SDK | All compilation, SwiftData, Swift Testing | ✓ | Xcode 26.5 (17F42) | — |
| Swift | Language (Swift 6 mode) | ✓ | Apple Swift 6.3.2 | — |
| XcodeGen | Regenerate project after new files | ✓ | 2.45.4 | — |
| iPhone 16 Simulator | `xcodebuild test` destination | ✓ (booted) | iOS Simulator | — |
| `EndoInsights` local SPM package | The package targets | ✓ (in-repo) | Phase 5 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None. The phase is fully buildable/testable on the current machine; no device-only paths (unlike Phase 4 CloudKit UAT — the additive migration is proven on-disk in the simulator/host, exactly as the V1→V2/V2→V3 cases already are).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`@Suite`/`#expect`), Xcode 26 / Swift 6.3 |
| Config file | none — XcodeGen `project.yml`; package tests run via `scheme.testTargets: - package: EndoInsights/EndoInsightsTests` `[VERIFIED: project.yml]` |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INST-02 | Regular cycle → phases mapped (low/medium confidence) | unit | `-only-testing:EndoInsightsTests/CyclePhaseMapperTests` | ❌ Wave 0 |
| INST-02 | Suppressed → ZERO `.cyclePhase` insights (SC1) | unit | same suite | ❌ Wave 0 |
| INST-02 | Irregular (wide spread) → ZERO phases | unit | same suite | ❌ Wave 0 |
| INST-02 | <2 cycles → ZERO phases | unit | same suite | ❌ Wave 0 |
| INST-04 | Under-sampled insight WITHHELD entirely (min-sample) | unit | `-only-testing:EndoInsightsTests/HonestyGateTests` | ❌ Wave 0 |
| INST-04 | Below confidence floor withheld (not ranked) | unit | same suite | ❌ Wave 0 |
| INST-04 | **ZERO forbidden terms** across all emitted insights | unit | `-only-testing:EndoInsightsTests/HonestyGateTests` | ❌ Wave 0 |
| INST-04 | Dismissed signalKey excluded from output | unit | `-only-testing:EndoInsightsTests/InsightsEngineTests` | ❌ Wave 0 |
| INST-04 | Phase-5 trends not regressed by the gate | unit (no-regression) | `-only-testing:EndoInsightsTests/InsightsEngineTests` + existing `TrendsEngineTests` | ⚠ existing TrendsEngineTests stay; new composition test ❌ Wave 0 |
| INST-04 | V3 store survives lightweight V3→V4 migration; `DismissedInsight` queryable | integration | `-only-testing:EndoAppTests/SchemaMigrationTests` | ⚠ file exists; add v3→v4 case |
| INST-04 | `dismissInsight`/`dismissedSignalKeys` round-trip (append-only, Set-deduped) | integration | `-only-testing:EndoAppTests` (new RepositoryDismissalTests) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `-only-testing:EndoInsightsTests` (package logic only — fast, no simulator boot needed beyond the destination).
- **Per wave merge:** full `xcodegen generate && xcodebuild test -scheme EndoApp ...` (includes the SwiftData migration + repository integration tests).
- **Phase gate:** full suite green before `/gsd-verify-work`; in particular the forbidden-terms test and the suppressed/irregular zero-phase tests must pass.

### Wave 0 Gaps
- [ ] `EndoInsights/Tests/EndoInsightsTests/CyclePhaseMapperTests.swift` — INST-02 (regular/suppressed/irregular/under-sampled)
- [ ] `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` — INST-04 (min-sample, confidence floor, ZERO forbidden terms)
- [ ] `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` — INST-04 (composition, dismissal-exclusion, Phase-5 no-regression)
- [ ] `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` — EXTEND: `regularCycleFixture`, `irregularCycleFixture`, `underSampledCycleFixture` (reuse the fixed-UTC-calendar + day-offset DSL already there)
- [ ] `Tests/EndoAppTests/SchemaMigrationTests.swift` — EXTEND: `v3Store_survivesLightweightMigrationToV4_withNoDataLoss`
- [ ] `Tests/EndoAppTests/` — NEW `RepositoryDismissalTests` for dismiss/read round-trip
- [ ] Framework install: none — Swift Testing already wired.

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1` (config). This phase processes **special-category health data (GDPR Art. 9)** on-device and writes a new synced record, so the relevant controls are data-handling and privacy, not network/auth (there is no network, no auth, no user input parsing of untrusted data).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login (architecture mandate) |
| V3 Session Management | no | No sessions |
| V4 Access Control | yes | **Consent gate (ONBD-01):** every new Repository method (`dismissInsight`/`dismissedSignalKeys`) MUST call `assertConsented()` first, like every existing sibling `[VERIFIED: Repository.swift]` |
| V5 Input Validation | partial | `signalKey` is engine-generated (not user free-text), so no injection surface; still, the model field is `String?` optional/defaulted (CloudKit-safe), and the engine never carries free-text health prose into insights (DaySnapshot is presence-only) |
| V6 Cryptography | no (delegated) | At-rest/in-transit encryption is CloudKit private-DB's responsibility; this phase introduces no crypto and must not hand-roll any |
| V7 Error Handling / Logging | yes | **Never log health values** — the engine has no logging (value-in/value-out, like `TrendsEngine`/`CycleMath`); keep it that way. No `print`/logger in the package. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health data leaking into a synced store that's Apple-readable | Information Disclosure | `DismissedInsight` carries only an engine-generated `signalKey` (e.g. `"cyclePhase.luteal.symptom.pelvicPain"`) + a timestamp — a category label, not raw health values; stored in the **private** CloudKit DB only (Phase 4 entitlement). No free-text, no severities. |
| Dismissal record written pre-consent | Elevation / Repudiation | `assertConsented()` defence-in-depth on both new Repository methods (V4 pattern) |
| Data-loss on migration (catastrophic for a health log) | Tampering / DoS | Additive `.lightweight` V3→V4 + a survival test proving every V3 row persists (Pitfall 5 mitigation; mirrors the proven V2→V3 case) |
| Causal/false-precision claim surfaced to a vulnerable user | (trust/regulatory, MDR line) | The `HonestyGate` forbidden-term scan + the zero-terms test + the suppressed/irregular zero-phase guards — this IS the security control for the "honesty = ethical core" requirement |
| `signalKey` collision causing wrong dismissal | Tampering | signalKeys are namespaced, value-independent strings; the deterministic-id derivation already namespaces with `com.endoapp.endoinsights.insight.v1` `[VERIFIED: Insight.swift]` |

**security_block_on: high** — none of the above are open high findings; all have a concrete in-phase mitigation that the plan must include as verification steps.

## Sources

### Primary (HIGH confidence) — all read this session
- `Sources/EndoApp/Domain/CycleMath.swift` + `CycleSummary.swift` — period-run/median/spread/confidence + suppressed short-circuit (the port source)
- `EndoInsights/Sources/EndoInsights/{Insight.swift, TrendsEngine.swift, DaySnapshot.swift, SnapshotEnums.swift}` — the package contract, the deterministic-id derivation, the signalKey discard, the inline thresholds
- `EndoInsights/Package.swift` + `EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift` — the SC1 zero-dependency boundary
- `Sources/EndoApp/Persistence/{SchemaV2.swift, SchemaV3.swift, MigrationPlan.swift, Models/FactorReading.swift, Repository.swift}` — the additive-migration + CloudKit-safe `@Model` + consent-gated CRUD recipe
- `Sources/EndoApp/Content/ClaimsPolicy.swift` — the forbidden-verb posture to mirror package-local
- `Tests/EndoAppTests/SchemaMigrationTests.swift` — the V1→V2 + V2→V3 survival-test template to extend
- `.planning/research/PITFALLS.md` — Pitfalls 1 (no phantom cycle), 3 (over-confident insights), 5 (CloudKit schema rigidity)
- `.planning/REQUIREMENTS.md` — INST-02/INST-04 + Phase-5-complete status
- `project.yml` + local toolchain probe — XcodeGen package/test wiring; Xcode 26.5 / Swift 6.3.2 / xcodegen 2.45.4 / iPhone 16 booted
- Grep verification: `TrendsEngine` has zero app-side call sites; all package tests `@testable import EndoInsights` (so demoting to internal is safe); `signalKey` is not a stored `Insight` property

### Secondary (MEDIUM confidence)
- None needed — closed-world phase.

### Tertiary (LOW confidence)
- The 14-day luteal-phase clinical heuristic (A7) — common knowledge, not source-verified this session; already locked by D-02.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all first-party + existing local package, toolchain verified present.
- Architecture/patterns: HIGH — every pattern is a read, existing-in-repo recipe (port `CycleMath`, mirror `SchemaV3`/`FactorReading`, mirror `ClaimsPolicy`, reuse `DeterministicUUID`).
- Cycle-phase window math: MEDIUM — the *shape* is locked by D-02; exact band/threshold numbers are discretionary recommendations (A1/A2), to be tuned in plan against fixtures.
- Pitfalls: HIGH — derived directly from read code (the no-regression/internal-demotion/substring/phantom-phase risks are concrete and verified).

**Research date:** 2026-06-08
**Valid until:** 2026-07-08 (stable — no external dependencies that move; re-check only if the EndoInsights contract or SwiftData migration story changes)
