# Phase 8: Insights Integration — Coordinator, Cached Insights & Dashboard - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; user accepted all recommendations.

<domain>
## Phase Boundary

The **payoff/integration phase**: connect the persistence layer to the isolated `EndoInsights` engine and surface live insights to the user. An off-main `InsightsCoordinator` runs the pure engine with debounced/windowed recompute and persists a CloudKit-safe `CachedInsight` (loads instantly, syncs via Phase 4); a **Swift Charts dashboard** renders the gated trend / cycle-phase / correlation insights with honest empty/sparse/suppressed states and a working dismiss affordance.

This is an integration phase — it introduces **no new requirement IDs**; it wires INST-01..04 (the Phase 5–7 engine) into the live app. Depends on Phase 4 (sync) + Phase 7 (the full engine). UI hint = **YES** (gets a UI-SPEC before planning). Mode: mvp.

Out of scope: new insight kinds (recommendations Phase 11, flare Phase 12 — but the dashboard should accommodate them as they route through the same `InsightsEngine`/`HonestyGate`); HealthKit (Phase 9); reports (Phase 10).
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — InsightsCoordinator (off-main recompute + caching)
- **D-01:** **Debounced recompute** on data-change (coalesce rapid edits → recompute after a short quiet period) + on app foreground. Off the main thread.
- **D-02:** A CloudKit-safe SwiftData **`CachedInsight` `@Model`** (new **`SchemaV5`**) storing the gated insights (serialized payload: kind, headline, detail, confidence, window, points, signalKey, sampleSize) + `computedAt` + an input **fingerprint**. Loads instantly on open; **syncs** across devices (Phase 4).
- **D-03:** **Off-main execution:** map store → `[DaySnapshot]` (+ `dismissedSignalKeys()`) on the model context, hand the `Sendable` snapshots to the pure `InsightsEngine` in a **background `Task`/detached actor**, write `CachedInsight` back on the main context. The engine is `Sendable`/Foundation-only, so this is safe (SC2 — no UI jank).
- **D-04:** **Cache invalidation by input fingerprint** (log count + latest-edit timestamp + dismissed-set hash). Show cached instantly; recompute when the fingerprint changes; update the UI when recompute lands.

### Area 2 — The dashboard (Swift Charts)
- **D-05:** An **Insights screen** with sections: **Trends** (charts), **Cycle-phase**, **Correlations** — each a card. Only honesty-gated insights shown (the engine already withholds under-sampled/spurious).
- **D-06:** **Swift Charts** — line (symptom severity over time), bar (frequency) — on an **absolute-date x-axis** (SC3, never cycle-relative). Cycle-phase + correlation insights render as text + confidence cards (with optional small viz).
- **D-07:** **Honest states:** empty ("Keep logging — insights appear once there's enough data"), sparse → "not enough data yet", **suppressed → NO cycle-phase section** (no phantom phase). Accessible (Dynamic Type, VoiceOver labels, ≥44pt), color-independent (reuse the Phase 1–3 a11y posture).
- **D-08:** Each insight **card**: headline (non-causal) + plain-language **confidence** indicator + effect/detail + supporting data points + a **dismiss** affordance.

### Area 3 — Dismiss UX + honesty surfacing
- **D-09:** Per-insight **"This isn't right"** action (swipe or context menu) → `Repository.dismissInsight(signalKey:)` → card disappears + **stays dismissed** (syncs via the Phase-6 `DismissedInsight` model).
- **D-10:** **Feedback loop:** the coordinator passes `dismissedSignalKeys()` into the engine → dismissed excluded → recompute after dismiss.
- **D-11:** **Plain-language confidence** ("possible association"; a low/medium/high indicator) — **never a false-precise number/percentage**.
- **D-12:** A persistent **"informational, not medical advice — possible associations from your own data"** disclaimer (reuse `ClaimsPolicy`/`Disclaimer`); non-causal copy throughout (engine-enforced + the UI copy passes the choke-point).

### Area 4 — Integration architecture & testing
- **D-13:** An **`@Observable @MainActor InsightsCoordinator`** holding the cached insights (UI state), injected via `AppDependencies`/Environment; the dashboard observes it. The engine runs **off-main** inside it (a `Task`); results published on the main actor.
- **D-14:** `CachedInsight` in a new additive **`SchemaV5`** + lightweight **V4→V5 migration** + a `SchemaMigrationTests` V4→V5 survival case. Repository CRUD for cached insights (load / replace-all).
- **D-15:** **Coordinator unit tests** (debounce, cache hit/miss, off-main recompute, fingerprint invalidation) with an injected engine + clock; migration survival; **XCUITest E2E** (open dashboard → see rendered insights from seeded data; empty state; dismiss → card gone).
- **D-16:** **Automatable:** coordinator/cache/migration logic + the dashboard E2E (data → rendered insight). **Manual/human_needed:** the Swift Charts **visual quality** (the UI-review step / screenshots) + **on-device `CachedInsight` SYNC** across devices (Phase-4 device dependency).

### Architecture
- **D-17:** Reuse: `Repository.daySnapshots()` + `dismissedSignalKeys()` + `dismissInsight(signalKey:)`; the public `InsightsEngine.insights(from:asOf:dismissed:)`; the `DismissedInsight` model (Phase 6); the CloudKit-safe `@Model` + additive-migration pattern (→ `SchemaV5`); the a11y/`ClaimsPolicy` posture; `HomeView` (add an Insights entry/tab).
- **D-18:** The engine returns `[Insight]` (`Sendable`); the coordinator **serializes** them into `CachedInsight` rows for persistence/sync and **deserializes** for the dashboard — the serialization must round-trip the `Insight` value (a round-trip test).

### Claude's Discretion
- The exact debounce interval, the `CachedInsight` payload encoding (per-row vs one blob; `Codable` vs flat columns — keep CloudKit-safe), the dashboard layout + chart styling, the fingerprint formula, and the navigation entry point are left to UI-SPEC + research/planning.
</decisions>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 8 wires INST-01..04 into the live app (no new IDs)
- `.planning/ROADMAP.md` — Phase 8 goal + 3 success criteria

### Existing source (the seams to integrate)
- `EndoInsights/Sources/EndoInsights/{InsightsEngine,Insight}.swift` — the public engine + the `Insight` value to render/serialize
- `Sources/EndoApp/Persistence/Repository.swift` — `daySnapshots()`, `dismissedSignalKeys()`, `dismissInsight(signalKey:)` (Phases 5–6) + add `CachedInsight` CRUD
- `Sources/EndoApp/Persistence/{SchemaV4.swift,Models/DismissedInsight.swift,MigrationPlan.swift,ModelContainer+Config.swift}` — the CloudKit-safe `@Model` + additive-migration pattern to clone for `CachedInsight`/`SchemaV5`
- `Sources/EndoApp/App/AppDependencies.swift` — inject the `InsightsCoordinator`
- `Sources/EndoApp/Features/Home/HomeView.swift` — add the Insights entry point
- `Sources/EndoApp/Content/{ClaimsPolicy,Disclaimer}.swift` — the not-medical-advice disclaimer + non-causal copy choke-point
- `Sources/EndoApp/Features/DayLog/SeverityControl.swift` + the Phase 1–3 views — the a11y posture (Dynamic Type, VoiceOver, color-independence) for the charts/cards
- `Tests/EndoAppUITests/` — the `-uitest-reset` + a11y-id E2E harness to extend for the dashboard

### UI
- A UI-SPEC will be generated next (this is a frontend phase): the dashboard layout, chart specs (absolute-date axis), card design, empty/sparse/suppressed states, dismiss affordance, and the disclaimer placement.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`InsightsEngine.insights(from:asOf:dismissed:)`** — the single public, `Sendable`, gated producer the coordinator runs off-main.
- **`Repository.daySnapshots()` / `dismissedSignalKeys()` / `dismissInsight(signalKey:)`** — the mapping + dismissal seams (Phases 5–6).
- **`DismissedInsight` + the additive schema/migration pattern** — clone for `CachedInsight` / `SchemaV5` (V4→V5 lightweight).
- **CloudKit-safe `@Model` rules** + Phase-4 sync — `CachedInsight` rides the existing sync.
- **`AppDependencies` injection** (consentStore/notificationScheduler/accountAvailability) — add the coordinator.
- **The a11y posture + `ClaimsPolicy`/`Disclaimer`** — for the charts/cards + the not-medical-advice framing.
- Build/test: `xcodegen generate && xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 16'`.

### Established Patterns
- MV + Repository; `@Observable` state; `@MainActor` UI; off-main pure-engine compute; CloudKit-safe append-only/additive models; Swift Testing + XCUITest; honesty (suppressed → no phase, sparse → withheld).

### Integration Points
- NEW `InsightsCoordinator` (`@Observable @MainActor`) + Insights dashboard views + Swift Charts; inject via `AppDependencies`.
- `Repository` → `CachedInsight` CRUD; schema bump to **SchemaV5** (CachedInsight) + V4→V5 migration.
- `HomeView` → Insights entry point (tab or CTA).
</code_context>

<specifics>
## Specific Ideas
- This is **the payoff** — the dashboard is what makes the logging effort worth it (PROJECT core value). It must render the user's REAL data honestly: no phantom phases, no spurious correlations, no false precision, plain-language confidence, and a working "this isn't right" dismiss.
- **No UI jank** (SC2): the pure engine runs off-main; cached insights load instantly; recompute is debounced.
- The honesty work is already in the engine — the dashboard must not re-introduce causal language or surface withheld insights; reuse the gate's output verbatim + the ClaimsPolicy choke-point for UI copy.
</specifics>

<deferred>
## Deferred Ideas
- Personalized recommendations (Phase 11) + flare early-warning (Phase 12) — new insight kinds that route through the SAME engine/gate and render in this dashboard.
- HealthKit import (Phase 9); doctor-ready PDF report (Phase 10).
- Advanced chart interactions (drill-down, custom ranges) → future.
</deferred>

---
*Phase: 8 — Insights Integration — Coordinator, Cached Insights & Dashboard*
*Context gathered: 2026-06-09 (smart discuss, autonomous mode)*
