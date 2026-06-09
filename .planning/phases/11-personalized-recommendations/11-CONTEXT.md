# Phase 11: Personalized Recommendations - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; recommendations auto-accepted.

<domain>
## Phase Boundary

Compose **data-driven, non-prescriptive recommendations** on top of the proven Phase-7 correlation engine — framed as **discussion prompts** about the user's own patterns, routed through the **honesty layer**, **never** as medical instructions. Surfaced as a dismissable dashboard section, shown only when supported by sufficient data.

In scope: INST-05. A `RecommendationsEngine` in the Foundation-only `EndoInsights` package that produces `.recommendation` `Insight`s from the already-gated correlation (and optionally trend) outputs, behind the LOCKED `HonestyGate`; a `RecommendationsCopy` surface (non-prescriptive, ClaimsPolicy-audited); a new `Recommendations` section in `InsightsView`; dismissal via the existing `DismissedInsight` path.

Out of scope: any NEW statistical computation (recommendations COMPOSE the proven correlation outputs — they do not re-derive); prescriptive/medical instructions; flare prediction (Phase 12); LLM/generative text (this is deterministic, template-based, on-device — NO model). The engine stays Foundation-only.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Recommendation derivation
- **D-01:** A `RecommendationsEngine` (in `EndoInsights`) derives `.recommendation` `Insight`s by **re-framing the EXISTING gated correlation insights** (Phase 7) — and optionally strong trends — that already passed the `HonestyGate`. **NO new statistics**; it composes proven outputs (reuse, never re-derive).
- **D-02:** Only the **strongest, sufficiently-sampled** associations become recommendations (inherit `minSample(.correlation)==10` + the confidence floor from the honesty layer); **cap to top-N** (e.g. 3–5) to avoid noise/overwhelm. Deterministic ordering (confidence desc, then a stable tiebreak).
- **D-03:** **Direction-aware framing:** a beneficial association → "you tend to feel better when…"; an aggravating one → "your [symptom] tends to be higher on days with [factor] — something you might explore." NEVER causal ("X causes Y"), NEVER prescriptive.

### Area 2 — Phrasing / honesty (non-prescriptive)
- **D-04:** All recommendation copy is templated through a new auditable **`RecommendationsCopy`** surface routed through **`ClaimsPolicy`**, scanned by a **NEW forbidden-IMPERATIVE scan** (forbid directive verbs: "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut") ON TOP OF the existing forbidden-verb / gendered-token / numeric-confidence scans. Recommendations are **discussion prompts** ("you might explore…", "something to discuss with your clinician…").
- **D-05:** Each recommendation carries the same **plain-language confidence indicator** (word + SF-Symbol shape, NEVER a number) + a "based on your own data" basis note + the not-medical-advice framing — inheriting the dashboard's honesty presentation.

### Area 3 — Surface (dashboard)
- **D-06:** A new **"Recommendations"** section in `InsightsView` (kind `.recommendation` → `insights.section.recommendations`), placed AFTER "Possible associations" (recommendations derive from associations). Reuse the kind-agnostic `InsightCardView`. A **zero-recommendation section is OMITTED** (honest absence-by-non-existence), like the other sections.
- **D-07:** Each recommendation is **DISMISSABLE** via the existing dismiss mechanism (`DismissedInsight` + the swipe / "This isn't right" action); dismissal persists (syncs) and feeds back through the engine (recompute excludes the dismissed `signalKey`) — identical to other insights.

### Area 4 — Gating + integration
- **D-08:** The `RecommendationsEngine` plugs into the LOCKED public **`InsightsEngine.insights(from:asOf:dismissed:)`** behind the single **`HonestyGate`** choke-point — inheriting confidence-gating, withholding under insufficient data, dismissal-exclusion, and the forbidden-terms scan. NO separate gating path. SC3-style honesty: a suppressed/sparse user gets **NO** recommendation (never a fabricated one).
- **D-09:** Reuse the Phase-8 **`InsightsCoordinator`** recompute (`dataChanged()`) + cache — recommendations recompute + cache like every other insight, no new machinery. The engine stays **Foundation-only** (no SwiftData/SwiftUI/HealthKit in the package).

### Claude's Discretion
- The exact recommendation `signalKey` scheme (must be stable + window-independent like the trend signals), the precise top-N cap, the recommendation-copy templates (subject to the imperative audit), whether strong trends (not just correlations) also seed recommendations, and the section ordering nuance are left to research/planning + the UI-SPEC.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`CorrelationEngine`** (`EndoInsights/Sources/EndoInsights/CorrelationEngine.swift`) — the gated correlation `Insight`s recommendations re-frame.
- **`HonestyGate`** (`HonestyGate.swift`) — the single confidence-floor / min-sample / dismissal / forbidden-terms choke-point; recommendations route through it.
- **`InsightsEngine`** (`InsightsEngine.swift`) — the LOCKED public composition API `insights(from:asOf:dismissed:)`; add recommendations as another producer behind the gate.
- **`Insight` + `InsightKind.recommendation`** (`Insight.swift:24`) — the kind is ALREADY reserved; no enum change needed.
- **`ForbiddenTerms` / `ClaimsPolicy`** — the copy-audit data; extend with the imperative scan.
- **`InsightsView` section pattern** (`Sources/EndoApp/Features/Insights/InsightsView.swift:58-112`) — `section(title:identifier:kind:)` omits empty sections; add a `.recommendation` section.
- **`InsightCardView`** (kind-agnostic card), **`InsightsCopy`**, **dismiss path** (`DismissedInsight` + coordinator), **`InsightsCoordinator.dataChanged()`** recompute/cache — all reused.

### Established Patterns
- Foundation-only `EndoInsights` package (compiler-enforced boundary); honesty layer is the single choke-point; non-causal + confidence-gated + dismissable insights; copy through ClaimsPolicy; absence-by-omission; CachedInsight recompute/cache.

### Integration Points
- New `RecommendationsEngine` in the package → composed into `InsightsEngine`.
- New `.recommendation` section in `InsightsView`; reuse the dismiss + coordinator pipeline.
</code_context>

<specifics>
## Specific Ideas
- Recommendations MUST read as discussion prompts, never instructions — the new imperative scan is the honesty choke-point and wins over any template wording.
- Suppressed/sparse users get NO recommendations (SC3) — never a fabricated "do this."
- Deterministic, on-device, template-based — NO LLM/generative text.
</specifics>

<deferred>
## Deferred Ideas
- LLM/generative natural-language recommendations — out of scope (privacy + honesty + on-device).
- Actionable in-app "interventions" / reminders derived from recommendations — future; v1 surfaces discussion prompts only.
- Flare early-warning (INST-06) — Phase 12.
</deferred>

<canonical_refs>
## Canonical References
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 11 req: INST-05
- `.planning/ROADMAP.md` — Phase 11 goal + 3 success criteria
- `.planning/phases/07-correlation-trigger-discovery/07-CONTEXT.md` — the correlation engine recommendations build on
- `.planning/phases/06-cycle-phase-mapping-insight-honesty-layer/06-CONTEXT.md` — the HonestyGate + DismissedInsight + ClaimsPolicy honesty layer
- `.planning/phases/08-insights-integration-coordinator-cached-insights-dashboard/08-CONTEXT.md` — the dashboard section pattern + coordinator recompute/cache + dismiss path
- `EndoInsights/Sources/EndoInsights/{CorrelationEngine,HonestyGate,InsightsEngine,Insight,ForbiddenTerms}.swift`
- `Sources/EndoApp/Features/Insights/InsightsView.swift`
</canonical_refs>
