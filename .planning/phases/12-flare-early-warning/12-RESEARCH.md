# Phase 12: Flare Early-Warning - Research

**Researched:** 2026-06-10
**Domain:** Heuristic, non-diagnostic flare early-warning — a deterministic composition of three already-gated signals (rising trend + active triggers + cycle-phase proximity) into a bounded, withheld-by-default confidence, behind the single HonestyGate, in-app only (no push), Foundation-only.
**Confidence:** HIGH (this is a one-more-producer phase on a fully-built, source-verified engine; every reusable asset was read this session)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** A "flare" is defined against the user's OWN baseline as a **near-term elevated-symptom-burden likelihood**, NOT a clinical diagnosis. A `FlareEngine` (Foundation-only, **NO ML**) scores it by **composing already-available signals**: a recent **rising symptom-severity trend** (TrendsEngine data), **currently-active known triggers** (the gated correlations the user has logged recently), and — if the user has a cyclical pattern — **proximity to a historically higher-symptom cycle phase** (CyclePhaseMapper). Combined into a bounded, transparent confidence score.
- **D-02:** **Compose, don't re-derive raw stats** where possible — reuse the gated trend/correlation/cycle-phase outputs; the flare score is an explainable heuristic combination ("based on your recent rising pattern + a factor you've logged recently"). `minSample(.flare)==21` (ALREADY set) — needs substantial personal history.
- **D-03:** **Probabilistic + bounded:** output a *likelihood* with a plain-language confidence (low/medium/higher), NEVER a binary "you will flare" and NEVER a number/percentage. Withheld entirely when data/confidence is insufficient.
- **D-04:** Uncertainty-first, non-causal, non-diagnostic copy via a new auditable **`FlareCopy`** through **`ClaimsPolicy`** + the existing scans (forbidden-verb / gendered / numeric-confidence / imperative) PLUS a **NEW alarmist/diagnostic-term guard** (forbid e.g. "attack", "diagnose", "predict", "will flare", "warning!", "alert", certainty words). Framed calmly with the plain-language confidence indicator (word + shape, no number) + pervasive "not medical advice."
- **D-05:** When insufficient, the flare insight is an honest **"not enough data yet"** notice (or omitted) — NEVER a fabricated/speculative warning.
- **D-06:** The flare early-warning is an **IN-APP insight ONLY**. The `FlareEngine` and the flare UI MUST NOT touch `NotificationScheduler` / `UNUserNotification` / any push or local-notification API. A **source-scan guard test** asserts the flare module references no notification API (mirror the Phase-10 `ReportNoNetworkTests`).
- **D-07:** A new **calm, non-alarmist** "Flare early-warning" section in `InsightsView` (`.flare` → `insights.section.flare`), placed FIRST among the insight sections, rendered with the **same calm `InsightCardView` styling** — NO alarm color, color-independent (word + shape), gray dismiss. **Omit-when-empty**. Reuse `InsightCardView` (`.flare` words-only).
- **D-08:** Same `HonestyGate` (`minSample(.flare)==21`), confidence, and **dismiss** (`DismissedInsight`) as every other insight (SC3); composed into the LOCKED `InsightsEngine.insights(from:asOf:dismissed:)` behind the single gate; reuse the Phase-8 `InsightsCoordinator` recompute/cache. Suppressed/sparse/insufficient → **NO** flare warning. Foundation-only.

### Claude's Discretion
- The exact heuristic scoring formula + how the three signals combine into a bounded confidence, the flare `signalKey` scheme (stable + window-independent for dismissal), whether the warning carries a short "what's contributing" explanation, the precise FlareCopy templates (subject to the alarmist guard), and the exact section placement nuance.
- The heuristic stays SIMPLE, transparent, and conservative (bias toward withholding).

### Deferred Ideas (OUT OF SCOPE)
- On-device ML flare model (XADV-03) — v2.
- Push/proactive flare alerts — explicitly OUT (SC2).
- Custom n-of-1 flare experiments (XADV-01) — v2.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INST-06 | User receives a heuristic, non-diagnostic flare early-warning with confidence, built last, gated by the honesty layer. | The `FlareEngine` producer (Standard Stack / Pattern 1–4), composed into the LOCKED `InsightsEngine` behind the single `HonestyGate` (`minSample(.flare)==21` already registered), surfaced through the reused `.flare` `InsightCardView` branch (already present) in a calm omit-when-empty section, audited by a new `FlareCopy` + alarmist scan, and guarded by a no-push source scan. SC1: uncertainty-first/withheld-when-insufficient. SC2: never a push (guard-tested). SC3: same gate/confidence/dismiss + pervasive disclaimer. |

### ROADMAP Success Criteria → Research Map
1. **Heuristic, non-diagnostic warning from own history, confidence indicator, uncertainty-first non-causal language** → Architecture Pattern 1 (compose-don't-re-derive scoring) + Pattern 4 (FlareCopy templates) + the alarmist scan (Pattern 3).
2. **Withheld when insufficient (can say "not enough data"), never a speculative push** → the `minSample(.flare)==21` gate + the `isInsufficiencyNotice` carve-out (Pattern 2) + the no-push source-scan guard (Pattern 5 / Security).
3. **Same min-sample gate, confidence, dismiss as every insight + pervasive "not medical advice"** → composed into the single `HonestyGate`/`InsightsEngine`, reusing `DismissedInsight` + the pinned `DisclaimerBanner` (Pattern 2 / Architecture Map).
</phase_requirements>

## Summary

Phase 12 is the **easiest-shaped, highest-stakes** phase in the build. Structurally it is the *fourth* repetition of an established pattern: add one Foundation-only producer to the `EndoInsights` package, concatenate it into the LOCKED `InsightsEngine.insights(from:asOf:dismissed:)` before the single `HonestyGate`, mirror its curated copy across the app↔package boundary with a drift guard, render it through the already-`.flare`-capable `InsightCardView`, and add a new omit-when-empty section to `InsightsView`. `RecommendationsEngine` (Phase 11) is the exact template — same `enum` namespace, same `static func ...(from:asOf:calendar:) -> [Insight]`, same `Insight.derived(...)` factory, same package-internal copy mirror + `…DriftGuard` shim, same suppressed→`[]` short-circuit. The honesty infrastructure (`minSample(.flare)==21`, the confidence floor, the `isInsufficiencyNotice` carve-out, dismissal-exclusion, the fail-closed forbidden-term filter), the `.flare` `InsightKind`, and the `.flare` render branch in `InsightCardView.supportingData`/`confidenceSymbol` are **all already in place** — verified by reading the source this session.

What makes this phase *hard* is not the wiring; it is the **two genuinely new design problems** and the **two highest-stakes honesty invariants in the entire app.** The new problems: (1) a deterministic, ML-free, false-precision-free **scoring heuristic** that composes a rising-severity trend + currently-active triggers + cycle-phase proximity into a *bounded* `InsightConfidence` that **biases toward withholding**; and (2) a **new alarmist/diagnostic-term scan** layered onto the existing causal/imperative/gendered/numeric scans, navigating the documented substring trap. The two highest-stakes invariants: a flare card must **never read as diagnostic/alarmist/certain** (a "warning" rendered with zero alarm visuals, honesty carried entirely by copy), and the app must **never fabricate a flare warning** under sparse/suppressed/insufficient/dismissed data (silent absence is the honest answer — there is deliberately no "all clear"). Plus the no-push invariant (SC2), guard-tested by a source scan mirroring `ReportNoNetworkTests`.

**Primary recommendation:** Mirror `RecommendationsEngine` exactly. Build `FlareEngine.flareWarning(from:asOf:calendar:) -> [Insight]` that (a) short-circuits to `[]` on any suppressed snapshot; (b) reads the three gated signals via *internal* composition (reuse `TrendsEngine`'s recent-vs-prior severity direction, `CorrelationEngine.scoredCandidates` for currently-active triggers, `CyclePhaseMapper`'s windows for phase proximity) — never re-deriving raw stats; (c) requires at least the rising-trend signal AND `pairedDays/loggedDays >= 21` evidence, emitting **at most one** `.flare` insight whose `confidence` is a conservative floor of the composed signals (cap at `.medium`, bias down); (d) names a stable, window-independent `signalKey` = `"flare.warning"`; (e) emits an optional `isInsufficiencyNotice` "not enough data yet" card *only* when the user is on the cusp (substantial history but sub-threshold confidence), else stays silent. Curate every word in `FlareCopy` to dodge the substring trap. Add the alarmist list to `ForbiddenTerms` + `ClaimsPolicy` and wire it into `HonestyGate.violation(in:)` and `CopyAuditTests`. Add a `FlareNoPushTests` source scan over the flare files. Do TDD/Nyquist: write the honesty acceptance suite first (RED), then the engine (GREEN).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Flare scoring heuristic (compose 3 signals → bounded confidence) | `EndoInsights` package (`FlareEngine`, Foundation-only) | — | Pure value-in/value-out math; no SwiftData/SwiftUI/HealthKit (SC1; BoundaryLint proves it). Mirrors `RecommendationsEngine`. |
| Honesty gating (min-sample 21, confidence floor, withhold, dismissal-exclusion, fail-closed copy scan) | `EndoInsights` package (`HonestyGate`, already built) | `InsightsEngine` (single compose root) | The product's ethical core; one choke-point. `.flare` already has `minSample==21` registered. |
| Composition into the public API | `EndoInsights` (`InsightsEngine.insights`, LOCKED signature) | — | One more `+ FlareEngine.flareWarning(...)` term before the gate (mirrors the Phase-11 `+ RecommendationsEngine.recommendations(...)` line). |
| Alarmist/diagnostic + causal/imperative/gendered/numeric copy enforcement | `EndoInsights` (`ForbiddenTerms` + `HonestyGate`) | App (`ClaimsPolicy` + `CopyAuditTests`) | Engine-emitted copy scanned by the gate; app-surface `FlareCopy` audited by `CopyAuditTests`; the two mirrors kept in sync by a drift guard. |
| Flare card rendering (calm, no-alarm, words-only, confidence pill, dismiss) | App SwiftUI (`InsightCardView`, already `.flare`-capable) | — | Kind-agnostic card; `.flare` already routes to the words-only evidence branch + `confidenceSymbol`. No new view. |
| The calm "Flare early-warning" section (first, omit-when-empty) | App SwiftUI (`InsightsView.section(...)`) | — | Reuse the existing omit-when-empty `section(title:identifier:kind:)` helper; add one call placed first among insight sections. |
| Recompute / cache / dismissal persistence | App (`InsightsCoordinator`, `DismissedInsight`, Phase-8) | CloudKit (sync) | Unchanged Phase-8 machinery; `.flare` rides it like every other kind (D-08). |
| No-push invariant | App + package source-scan test (`FlareNoPushTests`) | — | Structural guarantee: the flare module must reference no `UNUserNotification`/`NotificationScheduler`/push API (SC2 / D-06). |

## Standard Stack

This phase introduces **zero new dependencies.** It is built entirely on first-party Apple frameworks already in use and the project's own `EndoInsights` package. There is **no package install step** and therefore **no Package Legitimacy Audit is required** (see that section). The "stack" is the existing internal API surface.

### Core (existing, reused — all VERIFIED by reading source this session)
| Component | Where | Purpose | Why Standard (for this phase) |
|-----------|-------|---------|--------------|
| `InsightsEngine.insights(from:asOf:dismissed:calendar:)` | `EndoInsights/Sources/.../InsightsEngine.swift` | The single LOCKED public compose root. | `[VERIFIED: source]` Signature explicitly documented LOCKED for "Phases 7/11/12 add more candidate producers WITHOUT changing this signature." Add `+ FlareEngine.flareWarning(...)` before `HonestyGate.apply`. |
| `HonestyGate` + `minSample(.flare)==21` | `.../HonestyGate.swift` | Single honesty choke-point. | `[VERIFIED: source]` `.flare` case already returns `21`; confidence floor `.low`; `isInsufficiencyNotice` carve-out; dismissal-exclusion; fail-closed `ForbiddenTerms` scan all in place. |
| `Insight.derived(kind:window:signalKey:headline:detail:confidence:points:sampleSize:isInsufficiencyNotice:)` | `.../Insight.swift` | The deterministic insight factory. | `[VERIFIED: source]` `InsightKind.flare` reserved; `DeterministicUUID` derives `id` from kind+window+signalKey; never `UUID()`. |
| `TrendsEngine` (`severityPoints`, `direction`, recent-vs-prior, `confidence(forLoggedDays:)`) | `.../TrendsEngine.swift` | The recent rising-severity signal source. | `[VERIFIED: source]` Package-internal; `FlareEngine` (same module) can call its statics directly. Provides the "recent vs prior" severity direction without re-deriving. |
| `CorrelationEngine.scoredCandidates(...) -> [CorrelationFinding]` | `.../CorrelationEngine.swift` | The "currently-active known triggers" signal source. | `[VERIFIED: source]` Package-internal typed survivors carrying `signalKey`, `factorLabel`, `confidence`, `direction` (`.aggravating`/`.beneficial`). Already the Phase-11 composition seam — reuse it. |
| `CyclePhaseMapper` (`phaseWindows`, `phase(for:in:)`, `phaseConfidence`) | `.../CyclePhaseMapper.swift` | The "cycle-phase proximity" signal source. | `[VERIFIED: source]` Package-internal; emits `[]` for suppressed/irregular/<2-cycle (no phantom phase). The flare phase-proximity signal must inherit that omission. |
| `ForbiddenTerms` (`causal`, `imperative`, `violation(in:)`) | `.../ForbiddenTerms.swift` | The engine-side copy scan. | `[VERIFIED: source]` Extend with a new `alarmist` list; add it to `violation(in:)`. Already scanned fail-closed by `HonestyGate`. |
| `InsightCardView` (`.flare` branch present) | `Sources/EndoApp/Features/Insights/InsightCardView.swift` | The calm, kind-agnostic card. | `[VERIFIED: source]` `supportingData` already has `case .cyclePhase, .correlation, .recommendation, .flare:` (words-only) and `confidenceSymbol` covers all levels. No view change needed. |
| `InsightsView.section(title:identifier:kind:)` | `.../InsightsView.swift` | The omit-when-empty section helper. | `[VERIFIED: source]` Filters by kind, renders `Section` only when non-empty. Add one `section(...)` call placed FIRST. |
| `InsightsCoordinator` + `DismissedInsight` | Phase-8 / Phase-6 | Recompute/cache + dismissal persistence. | `[VERIFIED: ROADMAP/source]` Unchanged. `.flare` rides the same path (D-08). |

### Supporting (existing, reused)
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `RecommendationsCopy` / `RecommendationCopy` + `RecommendationsDriftGuard` | The app↔package copy-mirror + drift-guard precedent. | Clone as `FlareCopy` (app, audited) + package-internal `FlareCopyText` (mirror) + `FlareDriftGuard` (public shim). |
| `RecommendationsCopyDriftTests` | The byte-identity + forbidden-list parity test pattern. | Clone as `FlareCopyDriftTests`. |
| `ReportNoNetworkTests` | The `#filePath`-anchored source-scan invariant pattern. | Clone as `FlareNoPushTests` (forbids notification tokens). |
| `CopyAuditTests` | The app-side copy scan harness. | Add `FlareCopy.auditableStrings` + a new alarmist-scan test block. |
| `HonestyGateTests` (`allEmittedInsights_haveZeroForbiddenTerms`) | The package-side zero-forbidden-terms acceptance. | Add the flare fixtures' emitted copy to this scan. |
| `InsightsCopy` | Section/confidence/evidence/window/basis chrome (already audited). | Reuse verbatim; `FlareCopy` only adds the section header + the flare headline/detail/insufficiency templates. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Compose `TrendsEngine`/`CorrelationEngine`/`CyclePhaseMapper` outputs | Re-derive raw severity/correlation stats inside `FlareEngine` | REJECTED by D-01/D-02 ("compose, don't re-derive"). Re-deriving duplicates math, risks drift, and is harder to explain. Reuse the gated outputs. |
| A weighted-sum/threshold heuristic capped at `.medium` | Logistic regression / Core ML model | REJECTED — XADV-03 is explicitly v2/deferred; this phase is a transparent deterministic heuristic (D-01). ML adds false precision and opacity. |
| `signalKey = "flare.warning"` (single, window-independent) | A value/window-encoded key | REQUIRED window-independent + value-independent so dismissal survives recompute (D-07/Pattern 3). A composed warning has no single window; use `.allTime` like recommendations. |
| Emit at most one `.flare` card | Top-N flare cards | Bias toward calm/withholding (D-03/D-07): one composed warning, not a list. The section is kind-agnostic and would render more if surfaced, but the engine emits ≤1. |

**Installation:** None. No `Package.swift` change, no SPM dependency, no entitlement. `[VERIFIED: source]` `EndoInsights/Package.swift` is Foundation-only; this phase adds only `.swift` files to the existing package + app targets.

**Version verification:** N/A — no external packages. The only "versions" are the Apple SDK (iOS 18 / Swift 6 / Xcode 26) already locked in CLAUDE.md/STACK.md and unchanged by this phase.

## Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** `[VERIFIED: source]` Confirmed by reading `EndoInsights/Package.swift` (Foundation-only, no dependencies) and the phase scope (adds internal `.swift` files only). No npm/PyPI/crates/SPM dependency is added. There is no slopsquatting / hallucinated-package surface in this phase.

**Packages removed due to [SLOP] verdict:** none (no packages).
**Packages flagged as suspicious [SUS]:** none (no packages).

## Architecture Patterns

### System Architecture Diagram

```
                        ┌─────────────────────────── DaySnapshot[] (value, Sendable) ───────────────────────────┐
                        │  (already projected from the store by Repository.daySnapshots(); HealthKit-merged)    │
                        └──────────────────────────────────────────────┬───────────────────────────────────────┘
                                                                        │  asOf today (injected), calendar
                                                                        ▼
   InsightsEngine.insights(from:asOf:dismissed:calendar:)  ── LOCKED public compose root ──┐
        │                                                                                   │
        ├── TrendsEngine.trends(...)            ─► [.trend]                                  │
        ├── CyclePhaseMapper.insights(...)      ─► [.cyclePhase]                             │
        ├── CorrelationEngine.insights(...)     ─► [.correlation]                           │
        ├── RecommendationsEngine.recommendations(...) ─► [.recommendation]                 │
        │                                                                                   │
        └── FlareEngine.flareWarning(...)       ─► [.flare]  ◄── NEW (this phase)            │
                  │                                                                         │
                  │  reads (composes, never re-derives — D-01/D-02):                        │
                  │   ┌─ rising recent-vs-prior SEVERITY direction  ◄ TrendsEngine helpers  │
                  │   ├─ currently-active TRIGGERS (aggravating)    ◄ CorrelationEngine     │
                  │   │                                               .scoredCandidates(...) │
                  │   └─ cycle-phase PROXIMITY (if cyclical)         ◄ CyclePhaseMapper      │
                  │                                                    .phaseWindows(...)    │
                  │  guards: any .suppressed snapshot → []  (no phantom warning)             │
                  │  requires: rising-trend signal present AND evidence >= 21                │
                  │  outputs: ≤1 .flare Insight, confidence = conservative floor (≤ .medium),│
                  │           signalKey "flare.warning", window .allTime,                    │
                  │           copy ONLY from FlareCopyText templates (alarmist-scan-clean);  │
                  │           OR a single isInsufficiencyNotice "not enough data yet" card   │
                  ▼                                                                          │
            all candidates  ────────────────────────────────────────────────────────────────┘
                  │
                  ▼
   HonestyGate.apply(candidates, dismissed:)   ── the ONE choke-point ──
        (a) drop dismissed signalKeys (DismissedInsight)
        (b) drop sampleSize < minSample(.flare)==21   [except isInsufficiencyNotice]
        (c) drop confidence < .low                    [except isInsufficiencyNotice]
        (d) FAIL-CLOSED: drop any copy containing a ForbiddenTerms hit
            (causal ∪ imperative ∪ NEW alarmist/diagnostic)  + debug assert
                  │
                  ▼  gated [Insight]  (≤1 .flare survives, or none)
   ──────────────────────────────────────────────────────────────────────────────
        APP (SwiftUI)
   InsightsCoordinator  ── off-main recompute + fingerprint cache + dismiss feed-through (Phase 8)
                  │
                  ▼
   InsightsView  ── pinned DisclaimerBanner (always) ──► section(.flare) FIRST (omit-when-empty)
                  │                                       → Trends → Cycle phase → Possible assoc → Recommendations
                  ▼
   InsightCardView (kind-agnostic, .flare branch already present):
        calm headline (uncertainty-first) · confidence pill (word + shape, NO number, NO alarm hue)
        · detail (non-causal "keep an eye on / discuss") · words-only evidence · "Based on your logs."
        · gray non-destructive dismiss (swipe/contextMenu/VoiceOver) → coordinator.dismiss(signalKey:)

   ╳ NO PATH to NotificationScheduler / UNUserNotification (SC2) — enforced by FlareNoPushTests source scan
```

### Recommended Project Structure
```
EndoInsights/Sources/EndoInsights/
├── FlareEngine.swift          # NEW — the producer + package-internal FlareCopyText mirror + FlareDriftGuard shim
├── ForbiddenTerms.swift       # EDIT — add `alarmist: [String]` + include it in violation(in:)
├── InsightsEngine.swift       # EDIT — one line: + FlareEngine.flareWarning(...) before HonestyGate.apply
└── (TrendsEngine/CorrelationEngine/CyclePhaseMapper/HonestyGate/Insight — read-only reuse)

EndoInsights/Tests/EndoInsightsTests/
├── FlareEngineTests.swift     # NEW — honesty acceptance: withhold/insufficiency/bounded/deterministic/suppressed→0
└── HonestyGateTests.swift     # EDIT — add flare fixtures to allEmittedInsights_haveZeroForbiddenTerms

Sources/EndoApp/
├── Features/Insights/FlareCopy.swift   # NEW — app-side audited copy surface (byte-identical to FlareCopyText)
├── Features/Insights/InsightsView.swift # EDIT — add section(.flare) placed FIRST among insight sections
└── Content/ClaimsPolicy.swift          # EDIT — add `forbiddenAlarmist: [String]`

Tests/EndoAppTests/
├── CopyAuditTests.swift        # EDIT — add FlareCopy.auditableStrings + the alarmist scan block
├── FlareCopyDriftTests.swift   # NEW — app↔package byte-identity + alarmist-list parity (clone of Recommendations…)
└── FlareNoPushTests.swift      # NEW — source scan: flare files reference no notification/push API (clone of ReportNoNetwork…)
```
(Note: `InsightCardView.swift` needs **no edit** — the `.flare` branch already exists. This is the single biggest risk-reducer in the phase.)

### Pattern 1: Compose-don't-re-derive flare scoring (the one genuinely-new design)
**What:** A pure, deterministic heuristic that reads three already-gated signals and folds them into a bounded `InsightConfidence`, biased toward withholding. NOT statistics — a transparent, explainable combination.
**When to use:** The single `FlareEngine.flareWarning(...)` entry, after the suppressed short-circuit.
**Recommended shape (conservative, withhold-by-default):**

```swift
// Source: derived from RecommendationsEngine.swift + TrendsEngine/CorrelationEngine/CyclePhaseMapper (this session)
enum FlareEngine {
    static func flareWarning(from snapshots: [DaySnapshot],
                             asOf today: Date,
                             calendar: Calendar = .current) -> [Insight] {
        // 1. NO PHANTOM WARNING for a suppressed user (D-08/SC3) — the most conservative gate first.
        guard !snapshots.contains(where: { $0.suppressed }) else { return [] }

        // 2. Signal A — a RECENT RISING symptom-severity trend (compose TrendsEngine, do NOT re-derive).
        //    Reuse TrendsEngine.bucket/priorBucket/direction over a near-term window (last7 or last30).
        //    "Rising" == direction == .up for the user's dominant logged symptom(s). Carry the
        //    loggedDays count as the EVIDENCE basis (this is what the 21 floor is measured against).
        guard let rising = risingSeveritySignal(snapshots, asOf: today, calendar: calendar) else {
            // No rising trend → no warning at all (NOT an insufficiency notice — silence is honest).
            return []
        }

        // 3. Signal B — CURRENTLY-ACTIVE known triggers (compose CorrelationEngine survivors).
        //    A trigger is "active" iff it is an .aggravating CorrelationFinding AND the user logged that
        //    factor on a recent day. Reuse scoredCandidates(...); count active aggravating findings.
        let activeTriggers = activeAggravatingTriggers(snapshots, asOf: today, calendar: calendar)

        // 4. Signal C — CYCLE-PHASE PROXIMITY (only if a reliable cyclical pattern exists).
        //    Reuse CyclePhaseMapper.phaseWindows; nil/[] for suppressed/irregular/<2-cycle (inherits omission).
        //    "Proximate" iff today (or the near-term window) falls in a phase historically higher-symptom.
        let nearHigherPhase = cyclePhaseProximity(snapshots, asOf: today, calendar: calendar)

        // 5. CONSERVATIVE COMBINE → bounded confidence (cap .medium, bias DOWN). The rising trend is the
        //    REQUIRED spine; the other two only RAISE confidence by one notch each, never invent a warning.
        //    Evidence floor: rising.loggedDays must be >= minSample(.flare)==21 to surface a real warning.
        let evidence = rising.loggedDays
        if evidence < HonestyGate.minSample(for: .flare) {
            // Substantial-but-sub-threshold → the honest "not enough data yet" notice (D-05), NOT a warning.
            // (Discretion: emit this notice only when the user has *some* recent signal; otherwise stay silent.)
            return [insufficiencyNotice(window: .allTime)]
        }
        let confidence = boundedConfidence(rising: rising,
                                           activeTriggerCount: activeTriggers.count,
                                           nearHigherPhase: nearHigherPhase)   // ≤ .medium, floored at .low
        guard confidence >= .low else { return [] }   // bias toward withholding

        return [makeFlareInsight(confidence: confidence,
                                 contributing: contributingNote(activeTriggers, nearHigherPhase),
                                 evidence: evidence)]
    }
}
```

**Scoring guidance (keep SIMPLE + transparent — Claude's discretion, conservative default):**
- Start at `.low` *only when the rising-severity spine exists*. Never start a warning from triggers or phase alone.
- `+1 notch` if `activeTriggerCount >= 1`; `+1 notch` if `nearHigherPhase`. Cap at `.medium` (never `.high` — a heuristic flare is an estimate even at best, exactly as `CyclePhaseMapper` caps phase confidence at `.medium`).
- The 21-day evidence floor is the `minSample(.flare)` already locked. Measure it against the rising signal's `loggedDays` (the real per-signal evidence count), set as the insight's `sampleSize`, so the gate's backstop re-enforces it.
- Emit **at most one** `.flare` insight.

### Pattern 2: One-more-producer composition + the insufficiency carve-out
**What:** Concatenate the new producer into the LOCKED compose root before the single gate.
**Example:**
```swift
// Source: InsightsEngine.swift (this session) — mirror the Phase-11 RecommendationsEngine line exactly.
let candidates =
    TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
    + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
    + CorrelationEngine.insights(from: snapshots, asOf: today, calendar: calendar)
    + RecommendationsEngine.recommendations(from: snapshots, asOf: today, calendar: calendar)
    + FlareEngine.flareWarning(from: snapshots, asOf: today, calendar: calendar)   // NEW (this phase)
return HonestyGate.apply(candidates, dismissed: dismissed)
```
The `isInsufficiencyNotice == true` "not enough data yet" `.flare` card is exempt from BOTH the min-sample drop and the confidence floor (`[VERIFIED: source]` `HonestyGate.sampleAndFloorFilter`), so it survives to render as the calm sparse state. It must be keyed on the **typed marker**, never a `.insufficient` signalKey suffix (WR-01 — the gate explicitly rejects a forged suffix).

### Pattern 3: Stable, window-independent flare signalKey
**What:** A single dismissal key that survives recompute.
**Recommendation:** `signalKey = "flare.warning"`, `window = .allTime`. A composed warning has no single window; using `.allTime` + a fixed string matches the recommendation/correlation precedent (value-independent, window-independent), so `Insight.derived` yields a stable `id` and dismissal persists across sessions/devices.
**Anti-pattern:** encoding the contributing factors or a window into the key — then a changed trigger set would mint a NEW key and resurrect a dismissed warning.

### Pattern 4: The app↔package copy mirror + drift guard (clone Phase-11 verbatim)
**What:** The package can't import the app (SC1), so curated flare templates live in BOTH a package-internal `FlareCopyText` (the engine emits from it) and the app's audited `FlareCopy` — byte-identical, pinned by a drift test through a public `FlareDriftGuard` shim.
**Example:**
```swift
// Source: RecommendationsEngine.swift RecommendationCopy + RecommendationsDriftGuard (this session)
enum FlareCopyText {  // package-internal mirror — every word curated against ALL scans
    static let headline = "Your recent pattern looks similar to times before higher-symptom days."
    static let detail   = "This is a pattern in your own data, not a prediction — something to keep an eye on and discuss with your clinician."
    // contributing note (D-01, optional, descriptive + non-causal):
    static let contributing = "Based on a recent rising pattern and a factor you've logged recently."
}
public enum FlareDriftGuard {                 // the only public window onto the mirror (test-support only)
    public static var packageHeadline: String { FlareCopyText.headline }
    public static var packageDetail: String { FlareCopyText.detail }
    public static var packageContributing: String { FlareCopyText.contributing }
    public static var packageForbiddenAlarmist: [String] { ForbiddenTerms.alarmist }
}
```
**CRITICAL substring-trap curation (Pitfall 2 — see below):** the headline above uses **"higher-symptom"** which contains **"her"** (gendered token, already forbidden). It is shown here as the UI-SPEC's illustrative wording, but it **must be reworded** to dodge the scan — e.g. *"…before days when your symptoms have run heavier"* also fails ("her" ⊂ nothing there — but verify), or restructure to *"…before more difficult days for you."* The planner MUST require `FlareCopy` to pass the existing gendered scan (the `InsightsCopy` precedent reworded "higher"/"lower" → "trending up/down" for exactly this reason). Do not ship the UI-SPEC's literal "higher-symptom" string without re-verifying it against the "her" substring scan.

### Pattern 5: No-push source-scan guard (clone `ReportNoNetworkTests`)
**What:** A `#filePath`-anchored test that scans the flare source files and asserts none reference notification/push API.
**Forbidden tokens (identify exactly):** `UNUserNotification`, `UNNotification`, `UNMutableNotificationContent`, `UNNotificationRequest`, `UNCalendarNotificationTrigger`, `UNTimeIntervalNotificationTrigger`, `UNUserNotificationCenter`, `UserNotifications` (the import), `NotificationScheduler`, `requestAuthorization`, `aps-environment`, `remote-notification`, `scheduleNotification`, `addNotificationRequest`. Scan: `EndoInsights/Sources/EndoInsights/FlareEngine.swift` AND `Sources/EndoApp/Features/Insights/FlareCopy.swift` (and, defensively, the whole `Features/Insights/` directory for files this phase touches). Reuse the two anti-vacuity guards from `ReportNoNetworkTests` (strip comment lines; assert the scanned file set is non-empty).

### Anti-Patterns to Avoid
- **Re-deriving raw severity/correlation stats inside `FlareEngine`** — violates D-01/D-02; duplicates math, risks drift. Compose the existing engine outputs.
- **Starting a warning from triggers or cycle-phase alone** — the rising-severity spine is REQUIRED. A factor logged recently, or a luteal-phase day, is not by itself a flare signal. Bias toward withholding.
- **Emitting `.high` confidence** — cap at `.medium`. A heuristic flare is always an estimate (mirror `CyclePhaseMapper.phaseConfidence`'s `.medium` cap).
- **Any number/percentage in copy** — confidence is one of three plain-language words (`InsightsCopy.confidenceWord`); evidence is "Seen across {n} logged days." No "70%", no "likelihood 0.7".
- **An "all clear" / "no warning right now" message** — an "all clear" is itself a claim. Absence is shown by the section NOT rendering (omit-when-empty). `[CITED: 12-UI-SPEC.md §Copywriting]`
- **Any alarm visual** — no red/amber, no badge, no `exclamationmark`/`bell`/`flame` glyph, no animation, no haptic. The card is the same neutral `InsightCardView`; the section header glyph (if any) must be calm (`waveform.path.ecg` / `chart.line.uptrend.xyaxis.circle`). `[CITED: 12-UI-SPEC.md §Color, §Icon library]`
- **Parsing the audited copy to recover "what's contributing"** — derive the contributing note from the TYPED signals (the `CorrelationFinding.factorLabel`, the rising direction), never by re-reading the emitted strings (the Phase-11 direction-recovery anti-pattern).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Rising-severity detection | A fresh windowed mean/delta loop | `TrendsEngine.bucket`/`priorBucket`/`direction`/`severityPoints` | `[VERIFIED: source]` Already DST-safe day math, absence≠0, banded direction. Re-implementing risks drift + a second source of truth. |
| Active-trigger detection | A new factor×symptom correlation pass | `CorrelationEngine.scoredCandidates(...)` | `[VERIFIED: source]` Already gated (three-AND + BH-FDR + top-N) and typed with `direction`. This is the documented Phase-11 composition seam. |
| Cycle-phase proximity | A new phase-window estimator | `CyclePhaseMapper.phaseWindows`/`phase(for:in:)` | `[VERIFIED: source]` Already returns `[]` for suppressed/irregular/<2-cycle (no phantom). Reuse so the flare inherits that honesty for free. |
| Deterministic insight identity | `UUID()` or a hashing scheme | `Insight.derived(...)` → `DeterministicUUID` | `[VERIFIED: source]` Keeps `[Insight]` Equatable for fixtures + stable dismissal id. There is deliberately no `UUID()` initializer to reach for. |
| Min-sample / confidence-floor / withholding | A bespoke `if confidence < x` in the engine | `HonestyGate` (`minSample(.flare)==21`, floor, carve-out) | `[VERIFIED: source]` One choke-point. The engine sets `sampleSize`; the gate enforces. |
| Copy enforcement | Reviewing copy by eye | `ForbiddenTerms.violation` (fail-closed in gate) + `CopyAuditTests` | `[VERIFIED: source]` The scan wins over template wording; the test is the durable guarantee (runtime enforcement is a debug assert). |
| No-push proof | Trusting that nobody imports `UserNotifications` | `FlareNoPushTests` source scan (clone `ReportNoNetworkTests`) | `[VERIFIED: source]` Structural guarantee that survives future edits. |

**Key insight:** In this phase, the ONLY genuinely new *logic* is (a) the scoring combine in Pattern 1 and (b) the alarmist word list + its scan. Everything else is reuse. A plan that writes more than ~one new engine file + the copy/scan/guard test files is over-building.

## Runtime State Inventory

> This is a greenfield-feature phase (a new producer + a new section), NOT a rename/refactor/migration. No stored data, live-service config, OS-registered state, secrets, or build artifacts carry a string that changes. The one persistence-adjacent concern is the new `signalKey "flare.warning"`, addressed below.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None that changes.** `DismissedInsight` and `CachedInsight` are existing CloudKit-safe `@Model`s; a dismissed flare warning persists a row keyed by the new `signalKey "flare.warning"`. No schema change (the schema already stores arbitrary signalKeys/Insight blobs). | None — verified by reading `InsightsView.dismiss` → `coordinator.dismiss(signalKey:)` and the Phase-8 `CachedInsight`/`DismissedInsight` design. The new signalKey is just data in the existing columns. |
| Live service config | None — no n8n/Datadog/Tailscale/external service. The app is on-device + private CloudKit only. | None — verified by PROJECT.md (no backend, no third party). |
| OS-registered state | None — no Task Scheduler/pm2/launchd/systemd; and explicitly NO notification registration (SC2 forbids it). | None — and `FlareNoPushTests` proves the flare module registers nothing. |
| Secrets/env vars | None — no key references the flare feature. | None — verified by scope (pure logic + UI). |
| Build artifacts | **One additive concern:** `EndoInsights` gains new source files; the package + app schemes must compile them. No stale artifact, but the package test target must include `FlareEngineTests.swift`. | Add the new files to the existing package/app targets (the established pattern — Phase 11 did the same). No `egg-info`/binary equivalent on iOS. |

**Nothing found in categories Stored-data-change / Live-service / OS-state / Secrets:** stated explicitly above — verified by reading the persistence path and PROJECT.md.

## Common Pitfalls

### Pitfall 1: The substring trap (the #1 copy hazard, now compounded by the alarmist list)
**What goes wrong:** The copy scans are case-insensitive **substring** scans. A new alarmist term like `"alert"` matches inside innocent words; conversely a benign template word can contain a forbidden substring. The UI-SPEC's own illustrative headline ("…before **higher**-symptom days") contains `"her"` (a forbidden gendered token) — it would FAIL the existing `forbiddenGenderedTokens` scan.
**Why it happens:** `ForbiddenTerms.violation` / `ClaimsPolicy` scans use `lowered.contains(term)`. `[VERIFIED: source]`
**How to avoid:** Curate every `FlareCopy` word by hand against the FULL union (causal ∪ imperative ∪ gendered ∪ numeric ∪ NEW alarmist), exactly as `InsightsCopy` reworded "higher/lower" → "trending up/down". For the new alarmist list, pick STEMS that don't collide with benign copy, and verify each forbidden term is NOT a substring of any allowed `FlareCopy` word. Add positive-control AND negative-control tests (like `imperativeScan_flagsDeliberateImperatives` / `imperativeScan_passesCuratedDiscussionWords`).
**Warning signs:** A green build with copy that "feels" fine but was never run through the union scan; a forbidden term chosen as a whole word that silently matches a benign substring (e.g. `"urgent"` ⊂ nothing common, but `"alert"` ⊂ "alerted"; check the inverse too).

### Pitfall 2: Fabricating a warning under sparse/suppressed/insufficient data (the highest-stakes failure in the app)
**What goes wrong:** The engine invents or over-states a flare warning when it should be silent — the single worst outcome (alarming a user about their health on thin data).
**Why it happens:** Starting the score from triggers/phase alone; forgetting the suppressed short-circuit; not measuring the 21-day evidence floor; emitting an "all clear" that reads as a claim.
**How to avoid:** Rising-severity spine REQUIRED; `guard !suppressed → []` first; `sampleSize = rising.loggedDays` and let the gate enforce `>= 21`; bias the combine downward; emit ≤1 card; omit-when-empty (no "all clear"). Prove ABSENCE by test (mirror the cycle-phase-suppressed/recommendations-absent acceptance tests).
**Warning signs:** A flare card appearing for a fixture with <21 logged days, a suppressed fixture, or a sparse/flood fixture; any non-empty output where the correlation/recommendation engines correctly emit nothing.

### Pitfall 3: A "warning" rendered with alarm visuals
**What goes wrong:** Someone interprets "early-WARNING" and reaches for red, a badge, an `exclamationmark.triangle`, a bigger/bolder headline, or a haptic.
**Why it happens:** The word "warning" pulls toward urgency styling.
**How to avoid:** Reuse `InsightCardView` UNCHANGED (it has no alarm affordances); the section header glyph must be calm; honesty is carried entirely by COPY. `[CITED: 12-UI-SPEC.md §Color, §Typography, §Accessibility]` The card must look as calm as a trend card.
**Warning signs:** Any `.red`/`.orange`/`.yellow`, any `exclamationmark`/`bell`/`flame` SF Symbol, any enlarged/bold flare headline, any `.sensoryFeedback`/haptic in the flare path.

### Pitfall 4: The min-sample mismatch (the Phase-11 trap, re-armed)
**What goes wrong:** The engine self-gates on one evidence count but sets `sampleSize` to a different (smaller) one, so the gate's `>= 21` backstop silently drops a valid warning — or vice-versa, a warning slips through under-evidenced.
**Why it happens:** `minSample(.flare)==21` is enforced against `Insight.sampleSize` `[VERIFIED: source]`. If the composed warning's `sampleSize` isn't the real per-signal evidence count, the floor mis-fires.
**How to avoid:** Set `sampleSize` to the rising signal's `loggedDays` (the actual evidence the warning rests on), and self-gate on the SAME number, so the gate re-enforces without re-dropping (the documented no-regression invariant). The `isInsufficiencyNotice` card is exempt and carries its small count.
**Warning signs:** A test where a 21+-day rising fixture yields no flare card (floor dropped it), or a <21-day fixture yields one (floor didn't catch it).

### Pitfall 5: Drift between the app `FlareCopy` and the package `FlareCopyText`
**What goes wrong:** Editing one mirror without the other — the app surface is audited by `CopyAuditTests`, the package mirror by the gate scan; a one-sided edit ships unaudited or mismatched copy.
**Why it happens:** SC1 forces duplication across the boundary with only manual sync.
**How to avoid:** Clone `RecommendationsCopyDriftTests` → `FlareCopyDriftTests`: byte-identity of every rendered string + set-equality of the alarmist list across `FlareDriftGuard`. `[VERIFIED: source]`
**Warning signs:** A `FlareCopy` edit with no corresponding `FlareCopyText` edit (or vice-versa) passing CI — impossible once the drift guard exists.

## Code Examples

### Composing the rising-severity signal (reuse, don't re-derive)
```swift
// Source: TrendsEngine.swift bucket/priorBucket/direction/confidence (this session)
// Inside FlareEngine — package-internal, so TrendsEngine statics are directly callable.
private struct RisingSignal { let symptom: SymptomType; let loggedDays: Int }
private static func risingSeveritySignal(_ snaps: [DaySnapshot], asOf today: Date,
                                         calendar: Calendar) -> RisingSignal? {
    let today0 = calendar.startOfDay(for: today)
    let recent = TrendsEngine.bucket(snaps, window: .last30, endingAt: today0, calendar: calendar)
    let prior  = TrendsEngine.priorBucket(snaps, window: .last30, endingAt: today0, calendar: calendar)
    // For the user's dominant logged symptom, compute recent vs prior mean severity and ask for .up.
    // Reuse TrendsEngine.meanSeverity + TrendsEngine.direction (banded — avoids false precision).
    // Return the symptom + the days-with-any-log in `recent` as the evidence count, or nil if not rising.
    // (Full body left to planning; the point is: NO new stats — only TrendsEngine helpers.)
    return nil
}
```

### The emitted flare insight (templated copy, stable key, capped confidence)
```swift
// Source: RecommendationsEngine.makeRecommendation + Insight.derived (this session)
private static func makeFlareInsight(confidence: InsightConfidence,
                                     contributing: String?,
                                     evidence: Int) -> Insight {
    let detail = contributing.map { "\(FlareCopyText.detail) \($0)" } ?? FlareCopyText.detail
    return Insight.derived(
        kind: .flare,
        window: .allTime,                 // window-independent → stable dismissal id (Pattern 3)
        signalKey: "flare.warning",
        headline: FlareCopyText.headline, // uncertainty-first, alarmist-scan-clean
        detail: detail,
        confidence: confidence,           // ≤ .medium, floored at .low
        points: [],                       // words-only card — no chart
        sampleSize: evidence              // == rising.loggedDays; gate enforces >= 21 (Pitfall 4)
    )
}
```

### The new alarmist/diagnostic list + its scan wiring
```swift
// Source: ForbiddenTerms.swift (this session) — add the list and include it in violation(in:)
static let alarmist: [String] = [
    "attack", "alarm", "alert", "emergency", "urgent", "danger",
    "diagnose", "diagnosis", "predict", "prediction", "imminent",
    "certain", "definitely", "episode", "will flare", "flare-up!",
]   // WHOLE-WORD-curated against benign FlareCopy; verify no benign template contains any as a substring.
static func violation(in text: String) -> String? {
    let lowered = text.lowercased()
    return causal.first { lowered.contains($0) }
        ?? imperative.first { lowered.contains($0) }
        ?? alarmist.first { lowered.contains($0) }   // NEW
}
```
(The app's `ClaimsPolicy.forbiddenAlarmist` mirrors this list; `FlareCopyDriftTests` asserts set-equality; `CopyAuditTests` scans `FlareCopy.auditableStrings` against it with positive/negative controls.)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Separate gating path per insight kind | Single `HonestyGate` choke-point; producers concatenated before one `apply` | Phase 6, locked through Phase 12 | `.flare` adds nothing to the gate except its already-registered `minSample==21`. |
| `UUID()`-based insight ids | `DeterministicUUID` from kind+window+signalKey | Phase 5 | Stable dismissal + Equatable fixtures; the flare card is dismissable across recompute by `"flare.warning"`. |
| Free-text engine copy | Curated template mirrors + fail-closed scan + drift guard | Phases 6/11 | `FlareCopy` follows the exact pattern; the alarmist scan is an additive layer. |
| Bespoke per-kind copy review | `ForbiddenTerms.violation` union scan in the gate | Phase 6, extended Phase 11 (imperatives) | Phase 12 extends it again (alarmist) — one mechanism, three lists. |

**Deprecated/outdated:**
- **On-device ML for flares (Core ML / Create ML):** mentioned in STACK.md as a *future* possibility, explicitly **deferred to XADV-03 / v2**. Do NOT introduce it here. `[CITED: REQUIREMENTS.md XADV-03; 12-CONTEXT.md Deferred]`
- **Any push/proactive flare alert:** explicitly OUT (SC2). `[CITED: ROADMAP SC2; 12-CONTEXT.md D-06]`

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The conservative scoring shape (rising-spine REQUIRED; +1 notch per active-trigger/near-phase; cap `.medium`; floor `.low`) is the right calibration. | Architecture Pattern 1 | LOW — it is Claude's discretion (D-01 says "simple, conservative, bias toward withholding"); the planner/discuss can tune notches. The structure (compose 3 gated signals, cap `.medium`) is locked by D-01/D-02. |
| A2 | The near-term window for "recent rising trend" should be `.last30` (with `.last7` as a faster-moving option). | Pattern 1 / Code Examples | LOW — discretionary; `.last30` balances responsiveness vs. noise. Either is defensible; the engine reuses `TrendsEngine` windows either way. |
| A3 | Emitting a single `isInsufficiencyNotice` "not enough data yet" `.flare` card (vs. always staying silent) when the user has substantial-but-sub-threshold history is desirable. | Pattern 1 / Pattern 2 | LOW — D-05 explicitly permits EITHER ("an honest 'not enough data yet' notice OR omitted"). UI-SPEC leaves it to the planner. Default to the notice only on the cusp; silence otherwise. |
| A4 | The illustrative alarmist list (attack/alert/diagnose/predict/imminent/certain/episode/...) is the right set; "episode" and "attack" are the endo-specific additions worth forbidding. | Code Examples / Pattern 4 | MEDIUM — the exact list is the substantive new copy-policy decision. Some terms (e.g. "episode") may be debated. The planner should confirm the final list; the SCAN MECHANISM is not at risk, only membership. |
| A5 | `signalKey = "flare.warning"` with `window = .allTime` is the right stable key. | Pattern 3 | LOW — matches the recommendation/correlation window-independent precedent; Claude's discretion per CONTEXT. |
| A6 | The exact forbidden no-push token list in Pattern 5 is complete. | Pattern 5 / Security | LOW — additive; extra tokens only make the guard stricter. `UserNotifications`/`UNUserNotification*`/`NotificationScheduler` are the load-bearing ones (verified against the real `NotificationScheduler.swift`). |

**If this table is empty:** it is not — but every entry is LOW/MEDIUM and confined to Claude's-discretion calibration, not to any locked decision. The discuss-phase or planner should confirm A4 (the alarmist word list).

## Open Questions

1. **Final alarmist/diagnostic word list (A4).**
   - What we know: the categories to forbid (alarm/urgency/diagnostic/prediction/certainty) and the substring-trap discipline.
   - What's unclear: the exact membership (e.g. include "episode"? "spike"? "flare-up"?), and whether any benign `FlareCopy` word collides as a substring.
   - Recommendation: planner proposes the final list; add positive/negative-control tests; verify each term against the curated copy. This is the one item worth a quick human confirm.

2. **Insufficiency notice vs. pure silence on the cusp (A3).**
   - What we know: D-05 permits either.
   - What's unclear: whether a near-threshold user benefits from a calm "not enough data yet" flare card or whether absence is cleaner.
   - Recommendation: emit the notice ONLY when there is a real recent signal but evidence < 21; stay silent otherwise. Cheap to change later.

3. **"What's contributing" note — include it? (D-01 discretionary).**
   - What we know: it must be descriptive + non-causal, derived from the TYPED signals (never parsed from copy).
   - What's unclear: whether it adds clarity or risks over-explaining.
   - Recommendation: include a short, generic note ("Based on a recent rising pattern and a factor you've logged recently.") that names NO specific factor unless it is itself an already-audited `factorLabel`; keep it scan-clean.

## Environment Availability

> This phase has no external tools/services/runtimes beyond the Apple SDK already in use. It is pure Swift logic + SwiftUI + Swift Testing, all first-party.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode + iOS 18 SDK / Swift 6 | Build + Swift Testing + XCUITest | ✓ (project baseline) | Xcode 26 / iOS 18 | — |
| `EndoInsights` SPM package (local, Foundation-only) | The `FlareEngine` producer | ✓ (exists) | local | — |
| Swift Testing | `FlareEngineTests` / `HonestyGateTests` / `CopyAuditTests` / drift + no-push guards | ✓ (used throughout) | Xcode 26 | — |
| XCUITest | The dashboard E2E (flare section renders / absent) | ✓ (`InsightsDashboardUITests` exists) | Xcode 26 | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Validation Architecture

> nyquist_validation is enabled (config.json `workflow.nyquist_validation: true`). This section drives 12-VALIDATION.md.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`@Suite`, `#expect`) for package + app unit tests; XCUITest for the E2E. `[VERIFIED: source]` |
| Config file | none — SPM scheme test target (`EndoInsightsTests`) + app test target (`EndoAppTests`), per STACK.md. |
| Quick run command | `xcodebuild test -scheme EndoInsights -destination 'platform=iOS Simulator,name=iPhone 15'` (package honesty suite — fast, no simulator UI) |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 15'` (app unit + drift + no-push + E2E) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INST-06 / SC1 | A 21+-day rising fixture surfaces ONE calm `.flare` card, uncertainty-first, confidence ≤ `.medium`, deterministic (same inputs → equal `[Insight]`). | unit (pkg) | `FlareEngineTests.flare_surfacesForRisingFixture_boundedAndDeterministic` | ❌ Wave 0 |
| INST-06 / SC1 | Insufficient (substantial-but-sub-21) → an `isInsufficiencyNotice` "not enough data yet" card (or omission), NEVER a warning. | unit (pkg) | `FlareEngineTests.flare_insufficient_isNoticeOrOmitted_neverWarning` | ❌ Wave 0 |
| INST-06 / SC1 | Suppressed / sparse / no-rising-trend / correlation-flood → ZERO `.flare` insights (honest absence). | unit (pkg) | `FlareEngineTests.flare_suppressedSparseFlood_emitsZero` | ❌ Wave 0 |
| INST-06 / SC1 | No flare copy contains a causal / imperative / gendered / numeric / **alarmist** term (incl. emitted headline+detail). | unit (pkg) | `HonestyGateTests.allEmittedInsights_haveZeroForbiddenTerms` (add flare fixtures) | ✅ (extend) |
| INST-06 / SC1 | `FlareCopy.auditableStrings` passes the alarmist scan; positive/negative controls prove the scan works. | unit (app) | `CopyAuditTests.flareCopy_containsNoAlarmistTerm` + controls | ✅ (extend) |
| INST-06 / SC2 | The flare module references NO notification/push API. | unit (app) | `FlareNoPushTests.flareModuleImportsNoNotifications` | ❌ Wave 0 |
| INST-06 / SC3 | A dismissed `"flare.warning"` stays gone across recompute (dismissal-exclusion). | unit (pkg) | `FlareEngineTests.flare_dismissed_survivesRecompute` (via `InsightsEngine`+`HonestyGate`) | ❌ Wave 0 |
| INST-06 / SC3 | The min-sample 21 floor is enforced (21+ surfaces; 20 withheld) — the Pitfall-4 no-mismatch guard. | unit (pkg) | `FlareEngineTests.flare_minSample21_boundaryHolds` | ❌ Wave 0 |
| INST-06 | App `FlareCopy` ⟷ package `FlareCopyText` byte-identical; alarmist lists set-equal. | unit (app) | `FlareCopyDriftTests` (clone of `RecommendationsCopyDriftTests`) | ❌ Wave 0 |
| INST-06 / D-07 | The `.flare` section renders FIRST + is OMITTED (provably absent) for an unsupported/suppressed user. | e2e (XCUITest) | `InsightsDashboardUITests.flareSection_rendersFirst_andAbsentWhenUnsupported` | ✅ (extend) |

### Sampling Rate
- **Per task commit:** the package honesty suite (`EndoInsightsTests`, fast, no simulator UI).
- **Per wave merge:** the full app suite incl. drift + no-push + the dashboard E2E.
- **Phase gate:** full suite green before `/gsd-verify-work`; the no-fabricated-warning + no-push + alarmist-scan acceptances are the non-negotiable gates.

### Wave 0 Gaps
- [ ] `EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift` — covers INST-06 SC1/SC3 (surfacing, insufficiency, absence, boundary, determinism, dismissal).
- [ ] `Tests/EndoAppTests/FlareNoPushTests.swift` — covers SC2 (no-push source scan).
- [ ] `Tests/EndoAppTests/FlareCopyDriftTests.swift` — covers the app↔package mirror + alarmist-list parity.
- [ ] Extend `HonestyGateTests.allEmittedInsights_haveZeroForbiddenTerms` with flare fixtures (alarmist included).
- [ ] Extend `CopyAuditTests` with `FlareCopy.auditableStrings` + the alarmist scan + positive/negative controls.
- [ ] Extend `InsightsDashboardUITests` with the first-placement + provable-absence flare assertions.
- [ ] Reuse existing fixtures (`SnapshotFixtures`, the suppressed/sparse/flood fixtures from the correlation suite); add a 21+-day rising-severity fixture + a 20-day boundary fixture.

## Security Domain

> `security_enforcement: true`, ASVS L1, `security_block_on: high` (config.json). For this phase the "security" surface is **honesty/compliance integrity**, not classic auth/crypto — the highest-stakes absence in the app.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in this app (no accounts; iCloud identity is implicit). |
| V3 Session Management | no | None. |
| V4 Access Control | no | On-device, single-user; no server-side access surface. |
| V5 Input Validation | yes (light) | The engine is pure value-in/value-out over `DaySnapshot` (already validated/projected by the Repository); `FlareEngine` defends the package boundary by inheriting the de-dup-by-day idiom if it reaches raw snapshots. No untrusted external input. |
| V6 Cryptography | no | None hand-rolled; `DeterministicUUID` is an identity derivation, explicitly NOT a security primitive (documented in source). |
| V7 Error Handling / Logging | yes | Engine is pure — NO logging/print of health data (V7 convention, BoundaryLint-adjacent). The flare path must not log the warning or contributing factors. |
| V8 Data Protection (privacy) | yes | Health data stays on-device + private CloudKit (GDPR Art. 9). The flare warning is computed on-device, never transmitted, never pushed (SC2). No analytics/telemetry on the flare signal. |

### Known Threat Patterns for {Foundation-only insight engine + privacy-first health app}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| **Fabricated/over-stated flare warning under thin data** (the top threat — a false alarm about the user's health) | Spoofing/Tampering (of the honesty contract) | Rising-spine REQUIRED + suppressed→`[]` + `minSample(.flare)==21` floor + bias-down combine + ≤1 card + omit-when-empty + acceptance tests proving ABSENCE. The gate is the choke-point. |
| **Diagnostic/alarmist/certain language** (a wellness tool reading as a medical device / DiGA — regulatory + trust) | Repudiation/Information-disclosure (mis-claim) | The NEW alarmist/diagnostic scan layered on causal/imperative/gendered/numeric, fail-closed in `HonestyGate`, audited in `CopyAuditTests`, mirrored + drift-guarded. The scan WINS over template wording. `[CITED: 12-UI-SPEC.md §Copywriting]` |
| **Speculative push of a flare** (proactive alarm — SC2) | Tampering (of the in-app-only invariant) | `FlareNoPushTests` source scan over the flare files (no `UNUserNotification`/`NotificationScheduler`/`UserNotifications`); the module imports nothing notification-related. |
| **Dismissal integrity** (a dismissed warning resurrecting) | Tampering | Stable window-independent `signalKey "flare.warning"` → stable `DeterministicUUID` → dismissal persists via `DismissedInsight` (CloudKit-safe) and the gate excludes it on recompute. |
| **Health-data leakage via logs/telemetry** | Information disclosure | Pure engine, no logging/print; no analytics SDKs (PROJECT.md "What NOT to Use"); on-device only. |
| **False precision** (a number/percentage implying clinical certainty) | Information disclosure | No numeric confidence anywhere — three plain-language words + SF-Symbol shape; numeric-confidence scan already enforced. `[VERIFIED: source]` |

**Security posture summary:** there is no classic injection/auth/crypto attack surface added. The L1 controls that MATTER here are the **honesty invariants enforced as tests**: no-fabrication, no-alarmist-language, no-push, dismissal-integrity, no-false-precision. Each maps to a concrete gate or guard test above. `security_block_on: high` → the no-fabricated-warning and no-push acceptances are blocking.

## Sources

### Primary (HIGH confidence — source read this session)
- `EndoInsights/Sources/EndoInsights/{InsightsEngine,HonestyGate,Insight,TrendsEngine,CyclePhaseMapper,CorrelationEngine,RecommendationsEngine,ForbiddenTerms}.swift` — the engine, gate, factory, three signals, the analog producer, the copy scan.
- `Sources/EndoApp/Features/Insights/{InsightsView,InsightCardView,InsightsCopy,RecommendationsCopy}.swift` — the dashboard section helper, the kind-agnostic card (with the existing `.flare` branch), the audited chrome copy.
- `Sources/EndoApp/Content/ClaimsPolicy.swift` + `Sources/EndoApp/Reminders/NotificationScheduler.swift` — the app copy policy + the must-NOT-touch notification subsystem (token source for the no-push guard).
- `Tests/EndoAppTests/{RecommendationsCopyDriftTests,ReportNoNetworkTests,CopyAuditTests}.swift` + `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` — the drift-guard, source-scan, copy-audit, and zero-forbidden-terms patterns to clone/extend.
- `.planning/phases/12-flare-early-warning/{12-CONTEXT.md,12-UI-SPEC.md}` + `.planning/{REQUIREMENTS.md,ROADMAP.md}` + `CLAUDE.md`/STACK.md — locked decisions, UI contract, requirement + success criteria, project constraints.

### Secondary (MEDIUM confidence)
- (none — all findings are grounded in read source or the locked planning docs.)

### Tertiary (LOW confidence)
- (none.)

## Project Constraints (from CLAUDE.md / STACK.md)

- **Work only through a GSD command** (`/gsd-execute-phase` for this planned phase) — no direct edits outside the workflow. `[CITED: CLAUDE.md GSD Workflow Enforcement]`
- **Foundation-only `EndoInsights`** — NO SwiftData/SwiftUI/UIKit/HealthKit/UserNotifications in the package (BoundaryLint proves it). `[CITED: STACK.md; source headers]`
- **First-party only** — no third-party charting/ML/analytics/networking; no new SPM dependency. `[CITED: STACK.md "What NOT to Use"]`
- **No diagnostic/medical-device framing** in copy or model; "informational, not medical advice" throughout (MDR/DiGA line). `[CITED: STACK.md; ClaimsPolicy]`
- **No analytics/crash SDKs on health data; no off-device inference.** `[CITED: STACK.md; PROJECT.md]`
- **Swift Testing for new unit code; XCUITest only for E2E** (coexist, not interoperable). `[CITED: STACK.md]`
- **Deterministic, pure engines** — injected `today`+`calendar`, no `Date.now`/`UUID()`/RNG/`print` (V7). `[CITED: source headers]`

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every reused component read from source this session; zero new dependencies.
- Architecture: HIGH — the one-more-producer + single-gate + copy-mirror + drift-guard + source-scan patterns are directly observed in Phases 6/8/10/11 and the `.flare` render branch already exists.
- Pitfalls: HIGH — each is grounded in a documented prior pitfall (substring trap, min-sample mismatch, fabrication, alarm visuals, drift) verified against source comments.
- The scoring heuristic calibration: MEDIUM — the STRUCTURE (compose 3 gated signals, cap `.medium`, bias down) is locked by D-01/D-02; the exact notch weights are Claude's discretion (A1) and the alarmist word list (A4) is the one item worth a human confirm.

**Research date:** 2026-06-10
**Valid until:** 2026-07-10 (stable — internal-API + first-party only; no fast-moving external dependency).
