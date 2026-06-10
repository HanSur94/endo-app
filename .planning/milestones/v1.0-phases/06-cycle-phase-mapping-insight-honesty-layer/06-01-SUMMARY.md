---
phase: 06-cycle-phase-mapping-insight-honesty-layer
plan: 01
subsystem: EndoInsights (cycle-phase mapping)
tags: [insights, cycle-phase, honesty, INST-02, foundation-only, tdd]
requires:
  - "EndoInsights.Insight / Insight.derived (Phase 5)"
  - "EndoInsights.DaySnapshot + package-local snapshot enums (Phase 5)"
  - "Sources/EndoApp/Domain/CycleMath.swift (math ported verbatim, D-17)"
provides:
  - "Insight.signalKey (stored public let — the downstream dismissal key)"
  - "CyclePhase enum + PhaseDay (D-01)"
  - "CyclePhaseMapper.insights(from:asOf:calendar:) (Foundation-only, honesty-gated)"
  - "regular/irregular/under-sampled cycle test fixtures"
affects:
  - "Plan 06-02 (HonestyGate/InsightsEngine consume signalKey + CyclePhaseMapper)"
  - "Plan 06-03 (DismissedInsight matches on signalKey)"
tech-stack:
  added: []
  patterns:
    - "Foundation-only pure engine (enum namespace, injected today+calendar, value-in/value-out)"
    - "Deterministic Insight construction via Insight.derived (no UUID())"
    - "Honesty-by-omission: suppressed/<2-cycle/irregular -> []"
key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/CyclePhase.swift"
    - "EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift"
    - "EndoInsights/Tests/EndoInsightsTests/CyclePhaseMapperTests.swift"
  modified:
    - "EndoInsights/Sources/EndoInsights/Insight.swift"
    - "EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift"
decisions:
  - "signalKey surfaced purely additively — id derivation byte-identical, Phase-5 Equatable fixtures stay green"
  - "phaseConfidence caps at .medium (never .high); nil = irregular -> no phases"
  - "Mapper copy must itself avoid forbidden substrings ('cause' in 'because') — dropped 'not a cause'"
metrics:
  duration: ~10 min
  completed: 2026-06-08
  tasks: 3
  files: 5
---

# Phase 6 Plan 01: Cycle-Phase Mapping Summary

Surfaced `Insight.signalKey` as a stored `public let` and built the Foundation-only `CyclePhaseMapper` that derives estimated cycle-phase windows from recorded bleeding and emits descriptive, non-causal per-(phase, symptom) co-occurrence insights — returning `[]` (no phantom phase) for suppressed, irregular (spread > 9), and under-sampled (< 2 cycle) streams, with confidence capped at `.medium`.

## What Was Built

- **`Insight.signalKey` (additive).** Added `public let signalKey: String` to the `Insight` struct, the internal memberwise init, and threaded the already-in-scope `signalKey` argument through `derived(...)`. `DeterministicUUID.make` and the id derivation are untouched, so `id` stays byte-identical and the Phase-5 `TrendsEngineTests` Equatable/id assertions stay green (11 tests confirmed). This is the value-independent dismissal key every downstream honesty/dismissal layer (Plans 02/03) matches on.
- **`CyclePhase` enum + `PhaseDay`.** `public enum CyclePhase: String, Sendable, Equatable, CaseIterable { menstrual, follicular, ovulatory, luteal }` (D-01), mirroring the `InsightKind`/`TrendWindow` enum shape, plus an `InsightPoint`-shaped `PhaseDay` result type.
- **`CyclePhaseMapper` (Foundation-only).** Ports `CycleMath`'s `periodRuns`/`consecutiveGaps`/`medianInt`/`spread` VERBATIM (D-17). Gate order: (1) `guard !anySuppressed` → `[]`; (2) runs → starts → gaps; (3) `guard gaps.count >= 2` → `[]`; (4) median + spread; (5) `guard let confidence = phaseConfidence(...)` → `[]` for irregular. Phase windows are built relative to the last period start `S`, median `M`, and `band = max(2, spread/2)`: ovulation centered 14 days before the next predicted start `S+M` (luteal = the stable ~14-day anchor, A7), all via DST-safe `calendar.date(byAdding:.day)` + `startOfDay`. In-range days are labelled by phase, then one descriptive `.cyclePhase` insight per (phase, symptom) co-occurrence is emitted via `Insight.derived(kind:.cyclePhase, window:.allTime, signalKey:"cyclePhase.<phase>.symptom.<type>")`.
- **Fixtures + tests.** `regularCycleFixture` (4 runs, gaps 28/28/28, spread 0 → `.medium`), `irregularCycleFixture` (spread 25 > 9), `underSampledCycleFixture` (single run). `CyclePhaseMapperTests`: regular → non-empty at confidence ≤ `.medium`, suppressed/irregular/under-sampled → empty, descriptive non-causal copy, deterministic/Equatable output.

## How To Verify

```
xcodegen generate && xcodebuild test -scheme EndoApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests
```

Result: 28 tests in 6 suites pass (was 22 + 6 new CyclePhaseMapperTests). `BoundaryLintTests` green (EndoInsights still imports only Foundation). `TrendsEngineTests` (11) green — no Phase-5 regression from `signalKey`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Mapper copy itself tripped the non-causal term scan**
- **Found during:** Task 3 (RED on `regularCycle_copyIsDescriptive_neverCausal`).
- **Issue:** The emitted detail copy ended with "…, not a cause." The substring "cause" is a forbidden causal token (RESEARCH Pitfall 2: "cause" ⊂ "because"), so the engine's own honesty disclaimer violated the non-causal scan it is meant to satisfy. The HonestyGate term scan in Plan 02 would reject these insights.
- **Fix:** Rephrased the detail to "… — a descriptive co-occurrence only." (no forbidden substring), and added an inline comment flagging the substring sharp edge for future copy edits.
- **Files modified:** `EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift`
- **Commit:** 38a7608 (committed with the Task 3 fixtures/tests that prove it)

## Threat Surface

No new threat surface beyond the plan's `<threat_model>`. The mapper output is category-label signalKeys (`cyclePhase.<phase>.symptom.<type>`) + descriptive counts only — no free-text health prose (T-06-01). The honesty controls (suppressed/<2-cycle/irregular → `[]`, confidence ≤ `.medium`) are each test-enforced (T-06-02/T-06-03). signalKeys are namespaced value-independent strings (T-06-04). Zero new packages — EndoInsights stays Foundation-only (T-06-SC, BoundaryLintTests green).

## Known Stubs

None. The mapper is fully wired against synthetic fixtures; integration with the public `InsightsEngine` + `HonestyGate` is Plan 06-02 (per the phase plan), and dismissal persistence is Plan 06-03.

## Self-Check: PASSED

- FOUND: EndoInsights/Sources/EndoInsights/CyclePhase.swift
- FOUND: EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/CyclePhaseMapperTests.swift
- FOUND: `public let signalKey` in Insight.swift
- FOUND commit 0f3f51b (Task 1), c2b895c (Task 2), 38a7608 (Task 3)
