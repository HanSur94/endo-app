# Phase 12: Flare Early-Warning - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; recommendations auto-accepted. FINAL milestone phase.

<domain>
## Phase Boundary

Build the hardest, riskiest capability **dead last**: a **heuristic, probabilistic, non-diagnostic flare early-warning** derived from the user's personal history, **fully gated by the honesty layer**, **withheld when confidence is low**, presented **uncertainty-first**, and delivered **in-app only — NEVER as a speculative push notification**.

In scope: INST-06. A `FlareEngine` producer in the Foundation-only `EndoInsights` package emitting `.flare` `Insight`s behind the single `HonestyGate` (`minSample(.flare)==21` already set); a `FlareCopy` surface (uncertainty-first, non-alarmist, non-diagnostic, ClaimsPolicy-audited + a new alarmist/diagnostic-term guard); a calm `.flare` dashboard section; dismissal via the existing path.

Out of scope: any ML model (XADV-03 is v2/deferred — this is a transparent deterministic heuristic); ANY push/local notification of a flare (SC2 — in-app only); clinical/diagnostic claims. Insight infrastructure (gate, coordinator, card, dismiss, cache) is unchanged — Phase 12 adds one more producer + one calm section.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Flare heuristic & definition
- **D-01:** A "flare" is defined against the user's OWN baseline as a **near-term elevated-symptom-burden likelihood**, NOT a clinical diagnosis. A `FlareEngine` (Foundation-only, **NO ML**) scores it by **composing already-available signals**: a recent **rising symptom-severity trend** (TrendsEngine data), **currently-active known triggers** (the gated correlations the user has logged recently), and — if the user has a cyclical pattern — **proximity to a historically higher-symptom cycle phase** (CyclePhaseMapper). Combined into a bounded, transparent confidence score.
- **D-02:** **Compose, don't re-derive raw stats** where possible — reuse the gated trend/correlation/cycle-phase outputs; the flare score is an explainable heuristic combination ("based on your recent rising pattern + a factor you've logged recently"). `minSample(.flare)==21` (ALREADY set) — needs substantial personal history.
- **D-03:** **Probabilistic + bounded:** output a *likelihood* with a plain-language confidence (low/medium/higher), NEVER a binary "you will flare" and NEVER a number/percentage. Withheld entirely when data/confidence is insufficient.

### Area 2 — Warning framing / honesty
- **D-04:** Uncertainty-first, non-causal, non-diagnostic copy via a new auditable **`FlareCopy`** through **`ClaimsPolicy`** + the existing scans (forbidden-verb / gendered / numeric-confidence / imperative) PLUS a **NEW alarmist/diagnostic-term guard** (forbid e.g. "attack", "diagnose", "predict", "will flare", "warning!", "alert", certainty words). Framed calmly: "your recent pattern looks similar to times before higher-symptom days — something to keep an eye on / discuss with your clinician," with the plain-language confidence indicator (word + shape, no number) + pervasive "not medical advice."
- **D-05:** When insufficient, the flare insight is an honest **"not enough data yet"** notice (or omitted) — NEVER a fabricated/speculative warning.

### Area 3 — Delivery constraint (NO push — SC2)
- **D-06:** The flare early-warning is an **IN-APP insight ONLY**. The `FlareEngine` and the flare UI MUST NOT touch `NotificationScheduler` / `UNUserNotification` / any push or local-notification API. A **source-scan guard test** asserts the flare module references no notification API (mirror the Phase-10 `ReportNoNetworkTests` no-upload pattern). No speculative flare push, ever.

### Area 4 — Surface + gating
- **D-07:** A new **calm, non-alarmist** "Flare early-warning" section in `InsightsView` (`.flare` → `insights.section.flare`), placed FIRST among the insight sections (most time-sensitive / discoverable) BUT rendered with the **same calm `InsightCardView` styling** — NO alarm color, NO red, color-independent (word + shape), gray non-destructive dismiss — so it is discoverable without being alarmist. **Omit-when-empty** (a user without a supported warning has NO flare section). Reuse `InsightCardView` (`.flare` words-only).
- **D-08:** Same `HonestyGate` (`minSample(.flare)==21`), confidence, and **dismiss** (`DismissedInsight`) as every other insight (SC3); composed into the LOCKED `InsightsEngine.insights(from:asOf:dismissed:)` behind the single gate; reuse the Phase-8 `InsightsCoordinator` recompute/cache. Suppressed/sparse/insufficient → **NO** flare warning. Foundation-only (no SwiftData/SwiftUI/HealthKit in the package).

### Claude's Discretion
- The exact heuristic scoring formula + how the three signals combine into a bounded confidence, the flare `signalKey` scheme (stable + window-independent for dismissal), whether the warning carries a short "what's contributing" explanation, the precise FlareCopy templates (subject to the alarmist guard), and the exact section placement nuance are left to research/planning + the UI-SPEC. The heuristic stays SIMPLE, transparent, and conservative (bias toward withholding).
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`TrendsEngine` / `CorrelationEngine` / `CyclePhaseMapper`** (`EndoInsights/`) — the gated signals the flare heuristic composes.
- **`RecommendationsEngine`** (Phase 11) — the closest analog producer ("compose gated signals into a new kind"); mirror its structure + signalKey conventions + the byte-identical app↔package copy drift-guard pattern.
- **`HonestyGate`** — `minSample(.flare)==21` is ALREADY registered; the single confidence/min-sample/dismissal/forbidden-terms choke-point.
- **`InsightsEngine.insights(from:asOf:dismissed:)`** (LOCKED) — add the FlareEngine producer behind the gate (one-more-producer).
- **`Insight` + `InsightKind.flare`** (`Insight.swift:26`) — kind ALREADY reserved.
- **`ForbiddenTerms` / `ClaimsPolicy`** — extend with the alarmist/diagnostic guard (mirror the Phase-11 imperative-scan pattern; dodge the substring trap).
- **`InsightsView` section pattern** + **`InsightCardView`** + **dismiss path** + **`InsightsCoordinator`** — all reused; add the calm `.flare` section.
- **`NotificationScheduler`** (`Sources/EndoApp/Reminders/NotificationScheduler.swift`) — the system the flare module must NOT touch (SC2); the guard test scans for its absence in the flare module.

### Established Patterns
- Foundation-only package boundary (BoundaryLintTests); honesty layer single choke-point; non-causal + confidence-gated + dismissable + absence-by-omission; copy through ClaimsPolicy; one-more-producer composition; app↔package copy/forbidden-list drift guards (Phase 11).

### Integration Points
- New `FlareEngine` in the package → composed into `InsightsEngine`.
- New calm `.flare` section in `InsightsView`; reuse dismiss + coordinator.
- A source-scan guard asserting the flare module touches no notification API (NEW; mirrors ReportNoNetworkTests).
</code_context>

<specifics>
## Specific Ideas
- NEVER a push notification of a flare (SC2) — in-app only, guard-tested.
- Uncertainty-first, calm, non-alarmist — bias toward WITHHOLDING; a low-confidence/insufficient state says "not enough data," never a speculative alarm.
- Pervasive "not medical advice" + non-diagnostic framing; no certainty/diagnostic words (new alarmist guard).
</specifics>

<deferred>
## Deferred Ideas
- On-device ML flare model (XADV-03) — v2, once the heuristic baseline is outgrown.
- Push/proactive flare alerts — explicitly OUT (SC2).
- Custom n-of-1 flare experiments (XADV-01) — v2.
</deferred>

<canonical_refs>
## Canonical References
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 12 req: INST-06
- `.planning/ROADMAP.md` — Phase 12 goal + 3 success criteria
- `.planning/phases/11-personalized-recommendations/11-CONTEXT.md` — the one-more-producer + copy-scan + drift-guard pattern this phase mirrors
- `.planning/phases/06-cycle-phase-mapping-insight-honesty-layer/06-CONTEXT.md` — the HonestyGate + DismissedInsight + ClaimsPolicy honesty layer
- `.planning/phases/08-insights-integration-coordinator-cached-insights-dashboard/08-CONTEXT.md` — dashboard section + coordinator recompute/cache + dismiss
- `.planning/phases/10-doctor-ready-pdf-report/10-CONTEXT.md` — the no-network source-scan guard pattern to mirror for the no-push guard
- `EndoInsights/Sources/EndoInsights/{TrendsEngine,CorrelationEngine,CyclePhaseMapper,RecommendationsEngine,HonestyGate,InsightsEngine,Insight,ForbiddenTerms}.swift`
- `Sources/EndoApp/Features/Insights/InsightsView.swift` · `Sources/EndoApp/Reminders/NotificationScheduler.swift` (must-NOT-touch)
</canonical_refs>
