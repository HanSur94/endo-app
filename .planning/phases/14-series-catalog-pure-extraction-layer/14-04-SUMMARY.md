---
phase: 14-series-catalog-pure-extraction-layer
plan: 04
subsystem: infra
tags: [swift, swift-testing, foundation, value-types, normalization, honesty-invariants, fixed-domain, endoinsights]

# Dependency graph
requires:
  - phase: 14-series-catalog-pure-extraction-layer (Plan 01)
    provides: "ScaleKind taxonomy (ordinal0to10/ordinal1to5/ordinalBleeding/continuousHours/continuousMinutes/presenceEvent) + ExploreSeries + SeriesTestSupport harness"
provides:
  - "SeriesNormalization enum namespace (pure, Foundation-only, no instances)"
  - "domain(for: ScaleKind) -> ClosedRange<Double>?: FIXED honest range per kind (0...10, 1...5, 1...4, 0...24, 0...240); presence -> nil"
  - "normalize(_:in:) -> Double? / denormalize(_:in:) -> Double?: exact-inverse unit-interval round-trip"
  - "readout(_:kind:unit:) -> String: ALWAYS the real value + unit, never the normalized [0,1] number (honesty)"
  - "Normalization honesty invariants unit-pinned: fixed-domain-per-kind / presence-no-domain / round-trip / unit-interval-mapping / real-unit-readout"
affects: [15-explore-view]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FIXED literal domain per scale kind — never computed from visible-window min/max (Pitfall 4; per-window normalization is a deferred Phase-15 presentation decision)"
    - "Presence kind returns nil domain — an event lane is structurally not a normalized value (Pitfall 1), normalize/denormalize short-circuit to nil"
    - "normalize/denormalize are exact linear inverses against the fixed domain — round-trip recovers the real value within float tolerance"
    - "readout reports ONLY the real value + unit — the normalized number is never surfaced (honesty preserved even when Phase 15 normalizes for overlay)"

key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/SeriesNormalization.swift"
    - "EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift"
  modified: []

key-decisions:
  - "Domain is a FIXED literal per ScaleKind, never a per-visible-window min/max — so a series' shape stays stable across window switches (T-14-09); per-window normalization is an explicit Phase-15 presentation decision, out of scope here (Pitfall 4)"
  - "presenceEvent has NO numeric domain (nil); normalize/denormalize short-circuit to nil so a presence series can never be coerced into a normalized value (Pitfall 1)"
  - "readout ALWAYS reports the real value + unit, never the normalized [0,1] number — even when Phase 15 normalizes for visual overlay, every value the user READS stays honest (T-14-08); fixture asserts the readout string never contains the normalized form (0.7)"
  - "readout keeps the contractually-required kind: parameter in its signature even though the descriptive default does not branch on it — final chrome copy routes through the Phase-15 ExploreCopy audit, which may need the kind"

patterns-established:
  - "Fixed-honest-domain primitive: the presentation-agnostic split point where the value layer (this phase) supplies the honest domain + real-unit readout, and the presentation layer (Phase 15) decides lanes-vs-normalize on top — without either side being able to silently normalize a read value"
  - "normalize/denormalize as exact inverses, unit-pinned by a tolerance-compared round-trip (never == on float round-trip results)"

requirements-completed: []  # Phase 14 carries NO direct requirement IDs. EXPL-03 is a DOWNSTREAM (Phase 15) requirement this plan ENABLES — left Pending; Phase 15 delivers it.

# Metrics
duration: ~5min
completed: 2026-06-15
---

# Phase 14 Plan 04: Pure Normalization Layer Summary

**`SeriesNormalization` — the presentation-agnostic primitive that supplies a FIXED honest domain per `ScaleKind` (0...10, 1...5, 1...4, 0...24, 0...240; presence → nil), an exact-inverse `normalize`/`denormalize` unit-interval round-trip, and a `readout` that ALWAYS reports the REAL value + unit (never the normalized [0,1] number) — so Phase 15 can choose lanes-vs-normalize WITHOUT silently misleading the user.**

## Performance

- **Duration:** ~5 min
- **Tasks:** 2 (TDD: RED → GREEN)
- **Files modified:** 2 (both created)
- **Tests:** 151 total (146 baseline + 5 new), all green

## What Was Built

### Task 1 — `SeriesNormalization.swift` (GREEN: `816b8ff`)

A pure `enum SeriesNormalization` namespace (mirrors `ChartScrubSelection`), Foundation-only:

- `domain(for: ScaleKind) -> ClosedRange<Double>?` — switch returning a **FIXED literal** range per kind: `.ordinal0to10 → 0...10`, `.ordinal1to5 → 1...5`, `.ordinalBleeding → 1...4` (RESEARCH A1), `.continuousHours → 0...24` (A2), `.continuousMinutes → 0...240` (A2), `.presenceEvent → nil`. Never computed from data — no `min/max/reduce`.
- `normalize(_:in:) -> Double?` — linear map onto `[0,1]` against the fixed domain; `nil` for presence.
- `denormalize(_:in:) -> Double?` — exact inverse; `denormalize(normalize(v)) == v` within float tolerance.
- `readout(_:kind:unit:) -> String` — `unit.isEmpty ? "\(value)" : "\(value) \(unit)"`. Always the real value + unit, never the normalized number.

### Task 2 — `SeriesNormalizationTests.swift` (RED: `2aa9d7d`)

`@Suite("Series normalization (fixed honest domain; round-trip; real-unit readout)")` with 5 tests, written FIRST (failed to compile — `SeriesNormalization` not in scope — confirming RED before GREEN):

- `domainPerKindIsFixedAndHonest` — all five quantitative kinds map to their fixed range.
- `presenceKindHasNoDomain` — `.presenceEvent` domain/normalize/denormalize all return nil.
- `normalizeDenormalizeRoundTrips` — five (value, kind) pairs round-trip within `1e-9`.
- `normalizeMapsToUnitInterval` — `0→0`, `10→1`, `5→0.5` for `.ordinal0to10`.
- `readoutReportsRealValueAndUnit_notNormalized` — `readout(7, .ordinal0to10, "of 10") == "7.0 of 10"` AND does not contain `"0.7"` (honesty guard); continuous + presence readouts checked.

## Verification Results

- `swift test --package-path EndoInsights` — **151 tests in 18 suites passed** (146 baseline + 5 new normalization fixtures).
- `BoundaryLintTests` — **green** (`SeriesNormalization.swift` imports only Foundation).
- Honesty grep gates (excluding comments):
  - `grep -nE 'min\(|max\(|reduce' SeriesNormalization.swift` → **empty** (domain is FIXED literals, never computed from data — T-14-09).
  - `grep -n '86400' SeriesNormalization.swift` → **empty** (no epoch arithmetic).
- No compiler warnings.

## Threat Model Coverage

- **T-14-08** (information disclosure — normalized number shown as real): mitigated — `readout` reports only the real value + unit; the `readoutReportsRealValueAndUnit_notNormalized` fixture asserts the readout string never contains the normalized form.
- **T-14-09** (tampering — per-window normalization distorts shape): mitigated — `domain(for:)` is a FIXED literal per kind, never a visible-window min/max; the no-`min/max/reduce` grep gate enforces this structurally.

## Deviations from Plan

None — plan executed exactly as written. The TDD cycle (RED test → GREEN implementation) was applied per the executor's `tdd="true"` flow; no REFACTOR step was needed (implementation is minimal). The `readout` retains its contractually-required `kind:` parameter even though the descriptive default does not branch on it (documented in-source for the Phase-15 ExploreCopy audit) — this matches the plan's specified signature, not a deviation.

## Phase 14 Status

This is Wave 2, the FINAL plan of Phase 14. All four plans (01 catalog/scale taxonomy → 02 extraction → 03 scatter pairing → 04 normalization) are complete. The Foundation-only pure extraction layer is in place; Phases 15 (Explore) and 16 (Scatter) compose on these honest primitives. No EXPL-*/SCAT-* requirement IDs marked complete — Phase 14 only ENABLES them; Phases 15/16 deliver them.

## Self-Check: PASSED

- FOUND: `EndoInsights/Sources/EndoInsights/SeriesNormalization.swift`
- FOUND: `EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift`
- FOUND: `.planning/phases/14-series-catalog-pure-extraction-layer/14-04-SUMMARY.md`
- FOUND commit: `2aa9d7d` (test — RED)
- FOUND commit: `816b8ff` (feat — GREEN)
