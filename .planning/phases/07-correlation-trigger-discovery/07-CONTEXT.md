# Phase 7: Correlation & Trigger Discovery - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; user accepted all recommendations.

<domain>
## Phase Boundary

Build the product's **differentiating capability**: discovery of correlations/triggers across the user's own logged factors (symptoms vs food, sleep, activity, stress, mood, medication, cycle phase) with **statistical rigor** — minimum-sample gating, effect size, multiple-comparison awareness — **routed entirely through the Phase-6 honesty layer** so spurious findings are suppressed and every shown correlation uses non-causal "possible association" framing.

In scope: INST-03. A pure `CorrelationEngine` in the `EndoInsights` package emitting `.correlation` Insights (the kind was reserved in Phase 5) with effect size + sample size + confidence; the `HonestyGate` (Phase 6) does the withholding + non-causal enforcement. UI hint = **no** — the dashboard that renders these is Phase 8.

Out of scope: the dashboard/Swift Charts UI (Phase 8); lagged/next-day associations (deferred — more multiple-comparison risk); recommendations (Phase 11); flare (Phase 12). No causal claims, ever.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Correlation method
- **D-01:** **Effect-size comparison** for symptom-severity vs a factor: mean symptom severity on days WITH the factor vs days WITHOUT — the difference is the effect size; confidence from sample size + magnitude. NOT Pearson/regression (wrong for sparse, irregular, ordinal self-tracked data).
- **D-02:** **Rate/lift** for categorical factor vs symptom-presence: P(symptom | factor) vs P(symptom | no factor), sample-gated.
- **D-03:** **Same-day co-occurrence** for v1 (factor and symptom on the same calendar day). **Lagged (next-day) associations DEFERRED** (complexity + multiple-comparison risk).
- **D-04:** **Cycle phase** (from the Phase-6 `CyclePhaseMapper`) is included as one factor; suppressed users have no phase factor → no phase correlations for them (honesty).

### Area 2 — Statistical rigor (SC2/SC3)
- **D-05:** A conservative **minimum paired-observation threshold per pair** (≥N paired days where both the factor and the symptom-or-its-absence are observed), gated by `minSample(.correlation)`. Below it → the pair is not tested.
- **D-06:** **Multiple-comparison awareness** — account for the many factor×symptom pairs tested: a documented conservative adjustment (raise the confidence/effect bar with the comparison count; a Benjamini-Hochberg-/Bonferroni-lite approach). Spurious low-confidence findings are **suppressed** (SC3).
- **D-07:** **Effect size** reported as a plain magnitude ("severity averaged ~2 points higher on days you logged dairy") + a plain-language `InsightConfidence` (SC2). Every correlation carries effect size + confidence.
- **D-08:** **Confidence** = f(sample size, effect magnitude, comparison-count penalty) → `InsightConfidence` (none/low/medium/high). Below the floor → **withheld by the `HonestyGate`**.

### Area 3 — Scope & honesty
- **D-09:** **Bounded factor×symptom pairs:** food tags, activity (present/type), poor sleep, high stress, low mood, medication-taken, **cycle-phase** — each vs each symptom + bleeding. Bounded and interpretable.
- **D-10:** Surface only the **strongest gated correlations** (a small top-N); the gate withholds the rest — don't flood.
- **D-11:** Spurious/under-sampled are **WITHHELD, not ranked/shown** (the Phase-6 `HonestyGate`, SC3). Phase 7 supplies correct `sampleSize` + confidence so the gate suppresses them.
- **D-12:** **Non-causal framing** — "possible association", "tends to co-occur with" (inherited from the honesty layer; `ForbiddenTerms` enforced, now fail-closed in release). E.g. "Higher pain possibly associated with logging dairy."

### Area 4 — Engine integration & testing
- **D-13:** A pure **`CorrelationEngine` in `EndoInsights`** (Foundation-only, injected `today`) producing candidate `.correlation` Insights (effect size + `sampleSize` + confidence). `InsightsEngine` composes it alongside trends + cycle-phase → through the `HonestyGate`.
- **D-14:** **Lock `minSample(.correlation)`** (the Phase-6 placeholder was 14) — confirm/tune (~10–14 paired observations) against fixtures.
- **D-15:** **Swift Testing fixtures:** strong-association (surfaced), spurious/under-sampled (withheld), multiple-comparison (no false-positive flood), non-causal-copy guard, suppressed-user (no cycle-phase correlation). Determinism via injected `today`.
- **D-16:** Pure, deterministic, no p-hacking; honest effect sizes; the multiple-comparison adjustment documented + tested.

### Architecture
- **D-17:** Reuse the existing seams: `DaySnapshot` (carries all factors + symptoms + bleeding), the `CyclePhaseMapper` (cycle phase as a factor), the `HonestyGate` (withholding + non-causal enforcement + the now-live `minSample`/`sampleSize` mechanism), and the `InsightsEngine` composition. The correlation engine adds the `.correlation` producer; the gate already handles withholding/copy.
- **D-18:** The effect-size + confidence + multiple-comparison logic is the core new IP — keep it pure, tested, and honest (the value is suppressing noise, not generating findings).

### Claude's Discretion
- The exact effect-size formula, the confidence/multiple-comparison-adjustment math, the `minSample(.correlation)` value, the top-N cap, and the headline copy templates are left to research/planning.
</decisions>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 7 req: INST-03
- `.planning/ROADMAP.md` — Phase 7 goal + 3 success criteria

### Research
- `.planning/research/FEATURES.md` — correlations/triggers as the stated differentiator over plain trackers
- `.planning/research/PITFALLS.md` — multiple-comparison / spurious-correlation danger; honesty; non-causal claims

### Existing source (the seams to extend)
- `EndoInsights/Sources/EndoInsights/Insight.swift` — `.correlation` kind (reserved), `InsightConfidence`, the now-stored `sampleSize` + `signalKey`
- `EndoInsights/Sources/EndoInsights/HonestyGate.swift` — the choke-point (live `minSample(for:)` + `sampleSize` drop, confidence floor, ForbiddenTerms fail-closed) — `minSample(.correlation)` lives here
- `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` — compose the new `CorrelationEngine` here (trends + cycle-phase + correlation → gate)
- `EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift` — cycle phase as a correlation factor
- `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` — the per-day factors + symptoms + bleeding the engine correlates
- `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` — the pure injected-`today` engine pattern to mirror for `CorrelationEngine`
- `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` — non-causal copy enforcement
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`DaySnapshot`** — carries every factor + symptom + bleeding per day; the correlation engine pairs them same-day.
- **`HonestyGate`** (Phase 6, hardened) — the now-live `minSample(for:)`/`sampleSize` drop + confidence floor + fail-closed `ForbiddenTerms`. The correlation engine just supplies correct `sampleSize`/confidence and the gate suppresses spurious findings.
- **`CyclePhaseMapper`** — cycle phase as a factor (suppressed → no phase factor).
- **`InsightsEngine`** — the single public composition root to add the correlation producer to.
- **`TrendsEngine`** — the pure, injected-`today`, `Equatable`-output engine pattern to mirror.
- Build/test: `xcodegen generate && xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 16'` (package tests run in the scheme).

### Established Patterns
- Pure Foundation-only engine; injected `today`; honesty via the gate (withhold spurious, non-causal copy); Swift Testing fixtures; absence ≠ 0; suppressed → no cycle.

### Integration Points
- `EndoInsights` → add `CorrelationEngine` (+ supporting value types: a Factor descriptor, an effect-size/confidence calc); `InsightsEngine` composes it; `HonestyGate.minSample(.correlation)` locked.
- No app/UI changes this phase (Phase 8 renders + the dismiss action).
</code_context>

<specifics>
## Specific Ideas
- This is the **stated differentiator** over plain period/symptom trackers — but its value is **suppressing noise**, not generating findings. The multiple-comparison discipline (D-06) is what makes a surfaced correlation trustworthy.
- Everything routes through the Phase-6 honesty gate — Phase 7 supplies honest `sampleSize`/effect/confidence and lets the gate withhold the spurious. No new withholding logic, no causal copy.
- Keep it pure + deterministic + fixture-tested; a spurious-correlation fixture that the gate withholds is the key acceptance test.
</specifics>

<deferred>
## Deferred Ideas
- The dashboard / Swift Charts rendering of correlations + the dismiss affordance → Phase 8.
- Lagged / next-day associations → future (deferred for multiple-comparison safety).
- Personalized recommendations derived from correlations → Phase 11.
- Flare early-warning → Phase 12.
</deferred>

---
*Phase: 7 — Correlation & Trigger Discovery*
*Context gathered: 2026-06-09 (smart discuss, autonomous mode)*
