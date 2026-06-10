# Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; user accepted all recommendations.

<domain>
## Phase Boundary

Two deliverables: (1) **symptom-to-cycle-phase mapping** in the `EndoInsights` engine (INST-02) — estimated phase windows derived ONLY when a reliable cycle exists, never an invented phase for suppressed/irregular users; and (2) the **cross-cutting insight-honesty layer** (INST-04) — a shared `HonestyGate` that **every** insight (present and future) passes through: per-kind minimum-sample gating, a confidence indicator, enforced non-causal language, and dismissal. The honesty gate is the product's ethical core and the mandatory choke-point for Phases 7 (correlations), 11 (recommendations), 12 (flare).

In scope: INST-02, INST-04. Built on Phase 5's `EndoInsights` package (`DaySnapshot`/`Insight`/`InsightConfidence`/`.cyclePhase` kind already reserved; `CycleSummary.phase` is a `String?` held nil "for Phase 6 to populate"). UI hint = **no** — the dashboard that renders these is Phase 8.

Out of scope: correlations/triggers (Phase 7, INST-03); the dashboard/Swift Charts UI (Phase 8); recommendations (Phase 11); flare (Phase 12). No textbook-28-day assumptions ever.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Cycle-phase model (INST-02)
- **D-01:** A small **honest phase set** — `CyclePhase` enum: menstrual / follicular / ovulatory(estimated) / luteal — as **estimated windows**, populated ONLY when a reliable cycle exists. Phases are estimates, not precise; never assumes a textbook 28-day cycle.
- **D-02:** **Derived** from period runs (consecutive bleeding days) + median start-to-start cycle length: bucket each day relative to the last period start; ovulation ≈ 14 days **before the next predicted period** with a **wide uncertainty band**; luteal = post-ovulation→next period. Phase confidence (low/medium) scales with cycle regularity + recorded-cycle count.
- **D-03:** **Suppressed → NO phase mapping** (reuse the `CycleMath` suppressed short-circuit). **Irregular** (high start-to-start variance / too few cycles) → omit phase mapping OR very-low confidence, gated on regularity. **Never invent a phase** (SC1).
- **D-04:** Cycle-phase insights = **descriptive, non-causal associations** ("symptom X tends to co-occur with the luteal phase"), gated by min-sample + confidence (per-phase symptom frequency/severity). `kind == .cyclePhase`.

### Area 2 — The honesty gate (INST-04 — the cross-cutting core)
- **D-05:** A shared **`HonestyGate` in `EndoInsights`** that ALL insight producers (trends now; correlations Ph7, recommendations Ph11, flare Ph12) pass candidate insights through — the **single choke-point**. The engine's public API returns only gated insights.
- **D-06:** A **per-kind minimum-sample threshold** (≥N days / ≥N cycles by kind) below which the insight is **WITHHELD ENTIRELY** (not shown with a caveat) — SC3.
- **D-07:** A **confidence floor** below which insights are **withheld, not ranked**. Confidence = sample size + effect strength + (regularity, for cycle-phase).
- **D-08:** **Non-causal language ENFORCED** — a forbidden-term check ("causes", "trigger", "will", "because", "due to", …) on every Insight headline/detail (extends the `ClaimsPolicy` posture; package-local since `EndoInsights` is Foundation-only). Insights use "possible association", "tends to co-occur with", "may relate to". A test asserts **zero** forbidden terms across all emitted insights.

### Area 3 — Dismissal (INST-04 SC4)
- **D-09:** User dismisses an insight ("this isn't right"); recorded by the insight's **stable signal key** (not the full id) so the same recurring insight stays dismissed across recomputation.
- **D-10:** Persisted as a **CloudKit-safe SwiftData `@Model DismissedInsight { id: UUID, signalKey: String?, dismissedAt: Date }`** (append-only; all properties optional/defaulted; NO `.unique`; app-generated UUID — the CloudKit-safe rules; syncs across devices via Phase 4). Introduced in a new additive **`SchemaV4`** with a lightweight V3→V4 migration + a `SchemaMigrationTests` V3→V4 survival case. Repository CRUD: `dismissInsight(signalKey:)`, `dismissedSignalKeys() -> Set<String>`.
- **D-11:** Filtered by passing **`dismissed: Set<String>`** into the engine's public API → excluded at the **same choke-point** as withholding (engine stays pure/Foundation-only — a `Set<String>` param is fine).
- **D-12:** A dismissed signal **stays dismissed**. (Optional later: resurface if much stronger — deferred.)

### Area 4 — Engine API & testing
- **D-13:** A single public **`InsightsEngine.insights(from snapshots: [DaySnapshot], asOf today: Date, dismissed: Set<String>) -> [Insight]`** running trends + cycle-phase, applying the `HonestyGate`, excluding dismissed — the ONE entry point the app calls (Phase 8 renders its output). `TrendsEngine` becomes **package-internal**.
- **D-14:** A **`CyclePhaseMapper` in `EndoInsights`** deriving cycle phases from the **bleeding data already in `DaySnapshot`** (Foundation-only, self-contained — does NOT import app-side `CycleMath`, preserving SC1). It re-derives period runs + median length from the snapshots, mirroring `CycleMath`'s pure approach.
- **D-15:** **Swift Testing fixtures:** regular-cycle (phases mapped), suppressed (no phases), irregular (no/low-confidence phases), under-sampled (withheld), causal-language guard (no forbidden terms), dismissal-exclusion (dismissed key excluded). Determinism via injected `today`.
- **D-16:** The `HonestyGate` is the **mandatory choke-point** for all future insight kinds (Ph7/11/12) — lock the API now.

### Architecture & migration
- **D-17:** Cycle-phase derivation is **ported into the package** (`CyclePhaseMapper`) as pure Foundation-only code mirroring `CycleMath`'s period-run + median-gap logic. The app's `CycleMath` stays for the Home cycle summary (Phase 2); the package re-derives from snapshots for phase mapping (no app dependency). The slight duplication is acceptable for SC1 isolation (they serve different purposes — app: next-period UI; package: phase windows for insights).
- **D-18:** New additive **`SchemaV4`** for `DismissedInsight` + lightweight V3→V4 migration + `SchemaMigrationTests` V3→V4. Repository extends with dismissal CRUD (consent-gated like all reads/writes).

### Claude's Discretion
- Exact `CyclePhase` window boundaries, the confidence formula + numeric thresholds, the forbidden-term list, the `InsightsEngine` internal structure, and the `DismissedInsight` model shape are left to research/planning.
</decisions>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 6 reqs: INST-02, INST-04
- `.planning/ROADMAP.md` — Phase 6 goal + 4 success criteria

### Research
- `.planning/research/PITFALLS.md` — no phantom cycle, irregular/suppressed tolerance, honesty (the #1 pitfall); non-causal claims
- `.planning/research/ARCHITECTURE.md` — the insights-package seam

### Existing source (the patterns to extend)
- `EndoInsights/Sources/EndoInsights/Insight.swift` — `Insight` (carries `confidence`, `kind` with `.cyclePhase` reserved), `InsightConfidence`, the deterministic-id derivation
- `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` — becomes internal; routes through the new `HonestyGate`
- `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` — carries the bleeding data the `CyclePhaseMapper` derives phases from
- `Sources/EndoApp/Domain/CycleMath.swift` + `CycleSummary.swift` — the period-run + median-gap + suppressed-short-circuit logic to mirror in the package; `CycleSummary.phase` (String?) held nil for this phase
- `Sources/EndoApp/Persistence/{Repository.swift,SchemaV3.swift,MigrationPlan.swift}` — extend with the `DismissedInsight` model (SchemaV4), the V3→V4 migration, and dismissal CRUD
- `Sources/EndoApp/Content/ClaimsPolicy.swift` — the non-causal copy posture the package-local forbidden-term check mirrors
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`Insight` + `InsightConfidence` + `.cyclePhase` kind** (Phase 5) — already designed to carry confidence; this phase populates cycle-phase insights + the gate.
- **`TrendsEngine`** — the existing producer that now routes through the `HonestyGate` (becomes internal behind `InsightsEngine`).
- **`CycleMath` / `CycleSummary`** — the period-run + median-gap + suppressed-short-circuit logic to port (pure) into the package's `CyclePhaseMapper`.
- **`DaySnapshot`** — carries per-day bleeding, so the package derives phases self-contained (SC1).
- **CloudKit-safe `@Model` template** (`SymptomReading`/`FactorReading`) + additive schema/migration (`SchemaV3`→`SchemaV4`) — for `DismissedInsight`.
- **`ClaimsPolicy`** — the non-causal posture the package-local forbidden-term check mirrors.

### Established Patterns
- Pure Foundation-only engine; injected `today`; `Equatable` outputs; honesty (suppressed = no cycle, absence ≠ 0, sparse = withheld); CloudKit-safe append-only models; Swift Testing.

### Integration Points
- `EndoInsights` → add `HonestyGate`, `CyclePhaseMapper`, `CyclePhase`, the public `InsightsEngine`; make `TrendsEngine` internal.
- `Repository` → `dismissInsight(signalKey:)` + `dismissedSignalKeys()`; bump schema to `SchemaV4` (DismissedInsight) + V3→V4 migration.
- The app passes `dismissedSignalKeys()` into `InsightsEngine.insights(...)` (Phase 8 wires the rendering + the dismiss action).
</code_context>

<specifics>
## Specific Ideas
- The **honesty gate is the ethical core** — under-sampled/low-confidence insights are WITHHELD, never caveated-and-shown; non-causal language is enforced by a test, not trusted. This is what separates the app from a noise-generating "insights" tracker.
- **No phantom cycle phase**: suppressed → no phase; irregular → omit/low-confidence. The #1 endo pitfall.
- Lock the `HonestyGate` + `InsightsEngine` API now — Phases 7/11/12 all route through it; a redesign later ripples across three phases.
</specifics>

<deferred>
## Deferred Ideas
- Correlations & trigger discovery → Phase 7 (INST-03) — routes through this gate.
- The dashboard / Swift Charts UI + the dismiss affordance rendering → Phase 8.
- Recommendations → Phase 11; flare → Phase 12 — both route through this gate.
- Insight re-emergence (resurface a dismissed signal if much stronger) → future.
</deferred>

---
*Phase: 6 — Cycle-Phase Mapping + Insight-Honesty Layer*
*Context gathered: 2026-06-08 (smart discuss, autonomous mode)*
