# Phase 5: Snapshot Contract + Descriptive Trends Engine - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; user accepted all recommendations.

<domain>
## Phase Boundary

Establish the **architectural keystone of the entire insights half**: a `Sendable`, Foundation-only value-type **snapshot contract** (`DaySnapshot` + a generic `Insight`) and a **standalone insights package** (`EndoInsights`) that imports neither SwiftData nor SwiftUI, consuming snapshots and returning `Insight` values with no I/O — producing **descriptive trends** of symptoms and factors over time, anchored on **absolute calendar dates**, fully unit-tested against **synthetic fixtures** (including a "suppressed, no bleeding for months" fixture) with no simulator/iCloud dependency.

In scope: INST-01. The package + the contract + the `TrendsEngine` + tests. A new Repository read maps the SwiftData store → `[DaySnapshot]` app-side.

Out of scope: cycle-phase mapping + the formal confidence-gating/withholding honesty layer (Phase 6, INST-02/04); correlations/triggers (Phase 7, INST-03); the dashboard/charts UI that renders these Insights (Phase 8); recommendations (Phase 11); flare (Phase 12). **Phase 5 renders NO UI** (UI hint = no) — it is pure engine + contract + tests.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — The snapshot contract (`DaySnapshot` + `Insight`)
- **D-01:** **`DaySnapshot`** — a flat `Sendable`, **Foundation-only** value type for one calendar day, aggregating: symptoms (`[SymptomPoint(type, severity: Int?)]`), pain points (`[PainPoint(region, severity: Int?)]`), bleeding (flow intensity + clots), the `FactorDay` fields (food tags + note-presence, activity type/minutes/intensity, sleep hours/quality, medications-taken, PRN, stress `Int?`, mood `Int?`), `hasNote: Bool`, and a `suppressed: Bool` flag. The **single read-model** the engine consumes. Lives in the `EndoInsights` package.
- **D-02:** **`Insight`** — a generic `Sendable` value: `id`, `kind` (enum: `.trend` now; `.cyclePhase`/`.correlation`/`.recommendation`/`.flare` reserved for Ph6–12), `headline` (plain, non-causal), `detail`, `window`, `confidence` (descriptive strength), `points` (`[InsightPoint(date, value)]` supporting data). Generic enough for **all** downstream insight phases — get it right now.
- **D-03:** **Honesty carried:** `suppressed` + per-field **nil = unlogged** (mirror `FactorDay`'s absence ≠ 0). The engine NEVER coerces nil → 0.
- **D-04:** Symptom/pain as **@Model-free value structs** (`SymptomPoint`, `PainPoint`) in the package, mapped from the store app-side.

### Area 2 — Trends, windows, sparse-data honesty (INST-01)
- **D-05:** **Trends:** per-symptom severity-over-time (mean + direction) + frequency (days-present); per-factor frequency (food tags, activity days, poor-sleep days) + stress/mood means; bleeding-day frequency. **Descriptive only**, no causal language.
- **D-06:** **Windows:** rolling **7 / 30 / 90-day + all-time**, anchored on **absolute calendar dates** (SC2 — NOT cycle phase; that's Phase 6). The injected `asOf` date defines window ends.
- **D-07:** **Aggregation:** simple descriptive stats — count, mean, %-days-present, **direction** (recent window vs immediately-prior equal-length window). **No regression/smoothing** (avoid false precision).
- **D-08:** **Sparse-data:** a per-trend **min-data threshold** (≥N logged days in window) below which the engine emits an explicit "not enough data" Insight (or omits the trend) rather than a misleading one. The suppressed/no-bleeding fixture (SC3) yields **NO** bleeding/cycle trend.

### Area 3 — Package mechanism (the structural decision)
- **D-09:** A **real local SPM package `EndoInsights`** (Foundation-only), referenced by the app target — the **compiler enforces** the "no SwiftData/SwiftUI/UIKit" boundary (SC1). Research confirms the XcodeGen local-package wiring.
- **D-10:** Package contains: `DaySnapshot`, `Insight` (+ supporting value types `SymptomPoint`/`PainPoint`/`InsightPoint`/window/confidence/kind), the `TrendsEngine`, and their tests. **NOT** the SwiftData mapping (that stays app-side at the Repository boundary).
- **D-11:** **Swift Testing** (`@Test`/`#expect`) against synthetic fixtures, including the **"suppressed, no bleeding for months"** fixture (SC3). No simulator/iCloud.
- **D-12:** A **boundary lint** — a test/grep asserting the package sources import only Foundation (belt-and-suspenders on the compiler boundary).

### Area 4 — Engine API & app integration
- **D-13:** Pure **`TrendsEngine.trends(from snapshots: [DaySnapshot], asOf today: Date) -> [Insight]`** — `today` injected for determinism (mirrors `CycleMath`). All pure functions, `Equatable` outputs, no `Date.now`/randomness inside.
- **D-14:** App maps store → contract via a new Repository read **`daySnapshots() -> [DaySnapshot]`** (pure, `Sendable`, mirrors `factorDays`/`bleedingDays`). The engine **never** touches the store (SC1).
- **D-15:** **No UI in Phase 5.** Engine + contract + tests only. Charts/dashboard are Phase 8. (At most a tiny debug/inspection hook — no user UI.)
- **D-16:** Insight copy **descriptive + non-causal** (no "X causes Y") consistent with `ClaimsPolicy`; the formal confidence-gating/withholding honesty layer is Phase 6 (INST-04). Phase 5 applies only the min-data threshold.

### Architecture & forward-looking
- **D-17:** This package is the seam Phases 6 (cycle-phase + honesty), 7 (correlations), 8 (integration/dashboard), 11 (recommendations), 12 (flare) all build on. `DaySnapshot` + `Insight` must be **generic and stable** now.
- **D-18:** The app's existing value types (`FactorDay`/`BleedingDay`/`CycleSummary`) stay in the app; map app → `DaySnapshot` at the boundary. Do NOT move `CycleMath` into the package yet (Phase 6 may bring cycle-phase logic into the engine).

### Claude's Discretion
- Exact `DaySnapshot`/`Insight` field types (enum vs struct for window/confidence/kind), the precise min-data thresholds, the package folder layout + `project.yml` wiring details, and the trend headline copy are left to research/planning.
</decisions>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 5 req: INST-01
- `.planning/ROADMAP.md` — Phase 5 goal + 3 success criteria

### Research
- `.planning/research/ARCHITECTURE.md` — the insights-engine-as-isolated-package seam, value-type snapshot strategy
- `.planning/research/PITFALLS.md` — honesty (absence ≠ 0, no phantom cycle, no false precision)

### Existing source (the patterns to mirror)
- `Sources/EndoApp/Domain/FactorDay.swift` — **THE Sendable, Foundation-only value-type template** for `DaySnapshot`
- `Sources/EndoApp/Persistence/Repository.swift` — `BleedingDay` snapshot + `factorDays()`/`bleedingDays()` reads → add `daySnapshots()`
- `Sources/EndoApp/Domain/CycleMath.swift` + `CycleSummary.swift` — the **pure, injected-`today`, Equatable engine** template `TrendsEngine` mirrors
- `Sources/EndoApp/Domain/{SymptomType,BodyRegion,FlowIntensity,FoodTag,ActivityType,ActivityIntensity,SleepQuality}.swift` — the enums the snapshot value structs reference
- `project.yml` — XcodeGen spec to add the `EndoInsights` local package + app dependency
- `Sources/EndoApp/Content/ClaimsPolicy.swift` — non-causal copy posture for Insight text
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`FactorDay` / `BleedingDay`** — the exact `Sendable`, Foundation-only value-snapshot template for `DaySnapshot` (absence ≠ 0 honesty already proven).
- **`CycleMath` / `CycleSummary`** — the pure, deterministic, injected-`today`, `Equatable`-output engine template for `TrendsEngine`.
- **`Repository.factorDays()` / `bleedingDays()`** — the store→value-snapshot projection pattern to extend with `daySnapshots()`.
- Domain enums (`SymptomType`, `BodyRegion`, `FlowIntensity`, factor enums) — referenced by the snapshot value structs.
- Build/test: `xcodegen generate && xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 16'` (the package's tests run in this suite).

### Established Patterns
- Pure Foundation-only `Sendable` value types; injected `today`; `Equatable` engine outputs; Swift Testing; honesty (nil = unlogged, suppressed = no cycle).

### Integration Points
- NEW local SPM package `EndoInsights` (Foundation-only) + `project.yml` wiring + app dependency.
- `Repository` → add `daySnapshots() -> [DaySnapshot]` mapping the store to the contract.
- No UI wiring this phase (Phase 8 renders the Insights).
</code_context>

<specifics>
## Specific Ideas
- This is **THE architectural keystone** for every insights phase (6, 7, 8, 11, 12). The package boundary + the `DaySnapshot`/`Insight` contract must be right and generic — a redesign later would ripple across five phases.
- Keep the engine **pure, deterministic, and honest**: injected `today`, the sparse-data threshold, no phantom cycle/bleeding trend under suppression, no false precision.
- The compiler-enforced package boundary (SC1) is the point — don't let a SwiftData/SwiftUI import sneak in.
</specifics>

<deferred>
## Deferred Ideas
- Cycle-phase mapping + the formal honesty/confidence-gating layer → Phase 6 (INST-02/04).
- Correlations & trigger discovery → Phase 7 (INST-03).
- The dashboard / Swift Charts UI that renders these Insights → Phase 8.
- Personalized recommendations → Phase 11; flare early-warning → Phase 12.
</deferred>

---
*Phase: 5 — Snapshot Contract + Descriptive Trends Engine*
*Context gathered: 2026-06-08 (smart discuss, autonomous mode)*
