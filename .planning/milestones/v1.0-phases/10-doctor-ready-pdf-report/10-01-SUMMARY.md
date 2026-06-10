---
phase: 10-doctor-ready-pdf-report
plan: 01
subsystem: ui
tags: [pdf, report, swiftdata, endoinsights, imagerenderer, pdfkit, claimspolicy, honesty]

# Dependency graph
requires:
  - phase: 09-healthkit-import
    provides: "Repository.daySnapshotSources(on:) per-field provenance seam (RPRT-02 source labels)"
  - phase: 08-insights-integration
    provides: "InsightTrendChart + InsightsEngine (LOCKED window-based gate) reused via ImageRenderer / closure"
  - phase: 02-day-log
    provides: "CycleMath.summarize + three-state CycleSummary (no-phantom-cycle contract mirrored)"
provides:
  - "Pure Equatable/Sendable ReportModel.assemble(range:repository:engine:asOf:) — range-scoped, honest section assembly"
  - "SectionSource + SectionSource.aggregate (per-section provenance from daySnapshotSources, never recomputed)"
  - "ReportCopy auditable fixed-string enum (ClaimsPolicy choke-point for all report copy)"
  - "ReportRangeMapping: RangePreset + interval(for:) + enginewindowsInRange (A1) + isValid guard"
  - "RESOLVED A3 transform: ImageRenderer.render into UIGraphicsPDFRenderer CGContext needs only a translate (no y-flip)"
affects: [10-02-renderer, 10-03-ui, doctor-report, pdf-rendering]

# Tech tracking
tech-stack:
  added: []   # ZERO external packages — first-party PDFKit/ImageRenderer/SwiftUI only (RESEARCH Legitimacy Audit "Not applicable")
  patterns:
    - "Pure range-scoped value-model assembly (Pattern 1) decoupling honesty from rendering"
    - "Engine injected as a closure so the pure model stays off-actor and the LOCKED gate is reused verbatim"
    - "Per-section provenance aggregated from the single merge gate (daySnapshotSources), never recomputed from values"

key-files:
  created:
    - Sources/EndoApp/Features/Report/ReportModel.swift
    - Sources/EndoApp/Features/Report/ReportCopy.swift
    - Sources/EndoApp/Features/Report/ReportRangeMapping.swift
    - Tests/EndoAppTests/ReportModelTests.swift
    - Tests/EndoAppTests/ReportRenderSpikeTests.swift
  modified:
    - Tests/EndoAppTests/CopyAuditTests.swift

key-decisions:
  - "A1 (range → engine window): descriptive sections honor the EXACT range (computed from range-filtered reads); the insights section surfaces only engine windows whose span is ⊆ the range (enginewindowsInRange), anchored at range.end. A window wider than the range is excluded so coverage is never overstated."
  - "A3 (chart coordinate transform): UIGraphicsPDFRenderer installs a UIKit-flipped CTM, the same convention ImageRenderer.render expects, so the chart lands upright with ONLY a translate — NO y-flip needed. Documented verbatim in ReportRenderSpikeTests for Plan 02 to reuse."
  - "Symptoms-section hasEnoughData is gated at the LOCKED engine's HonestyGate.minSample(.trend)==3 distinct logged days so the report's sparse honesty floor never drifts from the engine's (Rule 1 fix — prior !rows.isEmpty was too lenient)."
  - "The report module lives app-side under Sources/EndoApp/Features/Report/ (NOT in the Foundation-only EndoInsights package); the engine was NOT modified."

patterns-established:
  - "Pattern 1 (pure range-scoped assembly): ReportModel is a flat Equatable/Sendable value model assembled from range-filtered Repository reads + the injected engine, with NO rendering — the honesty + source-label boundary unit tests assert against."
  - "Three-state CycleSection mirrors CycleSummaryView EXACTLY via CycleMath.summarize (suppressed / notEnoughData / derived) — no phantom cycle, never re-derived."
  - "ReportCopy is the ClaimsPolicy choke-point: every fixed report string (both surfaces) is one auditable array so a forbidden/gendered/numeric string fails the build."

requirements-completed: [RPRT-01, RPRT-02]

# Metrics
duration: 24min
completed: 2026-06-09
---

# Phase 10 Plan 01: Doctor-Ready Report — Pure Core + A1/A3 De-risk Summary

**A pure, Equatable, range-scoped `ReportModel` (honesty: absence≠0, no phantom cycle, sparse→"not enough data", per-section source labels from `daySnapshotSources`) plus an auditable `ReportCopy` choke-point, the A1 custom-range→engine-window mapping, and a passing A3 render spike that resolves the `ImageRenderer`→PDF `CGContext` transform (no y-flip) for Plan 02 to reuse.**

## Performance

- **Duration:** ~24 min
- **Started:** 2026-06-09T19:16:21Z
- **Completed:** 2026-06-09T19:40Z
- **Tasks:** 3 of 3
- **Files modified:** 6 (5 created, 1 extended)

## Accomplishments

- **Task 1 — Wave-0 scaffold (Nyquist):** Defined the pure `ReportModel` CONTRACT (Equatable/Sendable struct with header/summary/symptoms/three-state cycle/lifestyle/insights/legend sections, `SectionSource` + `SectionSource.aggregate`) with a stubbed throwing `assemble`. Added the A3 render spike (`ReportRenderSpikeTests`) — a one-page US-Letter PDF with an `InsightTrendChart` drawn via `ImageRenderer.render` into the PDF `CGContext`; it passes, **resolving the A3 transform** (translate-only, no y-flip, documented in-file for Plan 02). Added the RED `ReportModelTests` with the `SectionSource.aggregate` cases enabled and the 5 assemble-dependent cases disabled.
- **Task 2 — Pure `assemble` (TDD GREEN):** Implemented range scoping (filter `daySnapshots()` to the range inclusive of `range.end`'s day; anchor the engine at `range.end`), the three-state cycle via `CycleMath.summarize` (no phantom cycle, never re-derived), nil-honest lifestyle fields (absence≠0; a logged 0 stays 0), and per-section provenance aggregated from `daySnapshotSources(on:)` only. Flipped all 5 RED tests → GREEN (9/9 in the suite).
- **Task 3 — Copy + range mapping:** Built `ReportCopy` (every fixed report string for both surfaces, routed through the ClaimsPolicy audit) and `ReportRangeMapping` (`RangePreset`, DST-safe `interval(for:)`, the A1 `enginewindowsInRange` helper, the `isValid` invalid-range guard). Extended `CopyAuditTests` with 4 Phase-10 scans (forbidden-verb / gendered-token / numeric-confidence / non-empty + off-state/sparse/self-tracked assertions) — all GREEN.

## Verification

- `ReportModelTests` — 9/9 GREEN (scopesToRange, sparseRangeIsHonest, suppressedNoPhantomCycle, absenceIsNotZero, sourceLabelsMatchProvenance + 4 aggregate cases).
- `ReportRenderSpikeTests` — GREEN (non-empty 1-page PDF, ~612pt US-Letter width, upright chart). **A3 resolved.**
- `CopyAuditTests` Phase-10 section — 4/4 GREEN; `ReportCopy.auditableStrings` non-empty.
- Full Swift Testing suites: **200 + 96 tests, 0 failures.**
- No-network invariant (T-10-04): `grep` confirms NO `URLSession`/`URLRequest`/`dataTask` in `Sources/EndoApp/Features/Report/`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Symptoms-section sparse honesty floor was too lenient**
- **Found during:** Task 2 (`sparseRangeIsHonest` failed — `hasEnoughData` was `true` for one logged symptom day).
- **Issue:** The initial `hasEnoughData = !rows.isEmpty` let a single logged day read as a meaningful trend, violating the honesty contract (a sub-threshold range must read "Not enough data in this range").
- **Fix:** Gated `hasEnoughData` on the busiest symptom being logged on `>= HonestyGate.minSample(.trend) == 3` distinct days (kept equal to the LOCKED engine's floor so the report and engine never drift). Added `minSymptomTrendDays = 3`.
- **Files modified:** `Sources/EndoApp/Features/Report/ReportModel.swift`
- **Commit:** 0c5683e

**2. [Test correction] Sparse-insights assertion was inverted**
- **Found during:** Task 2 (`sparseRangeIsHonest` second assertion failed).
- **Issue:** The RED test asserted insights must NOT contain an `isInsufficiencyNotice` — but that notice IS the engine's honest "not enough data yet" sparse marker, not a fabrication. The honest invariant is that a sparse range fabricates no REAL insight; only the insufficiency notice may appear.
- **Fix:** Changed the assertion to `model.insights.allSatisfy { $0.isInsufficiencyNotice }` (an empty array trivially satisfies it).
- **Files modified:** `Tests/EndoAppTests/ReportModelTests.swift`
- **Commit:** 0c5683e

## Deferred Issues (out of scope — SCOPE BOUNDARY)

- A pre-existing, flaky XCUITest E2E (`EndoAppUITests.CheckInFlowUITests.testMedicationTaken_persistsAcrossReopen`) fails on a medication-toggle UI timing issue. It is in an unrelated UI-test target; Plan 10-01 adds no UI and touches no medication flow. Logged in `.planning/phases/10-doctor-ready-pdf-report/deferred-items.md`; NOT fixed.

## Threat Surface

All plan threat-register mitigations are satisfied:

- **T-10-01 (Tampering, cycle/lifestyle assembly):** three-state cycle via `CycleMath` (no phantom), nil-honest lifestyle — asserted by `suppressedNoPhantomCycle` / `absenceIsNotZero`.
- **T-10-02 (Spoofing, source labels):** aggregated from `daySnapshotSources` only (Pitfall 1), no provenance → `.none` — asserted by `sourceLabelsMatchProvenance`.
- **T-10-03 (Tampering, copy):** all fixed copy centralized in `ReportCopy`; `CopyAuditTests` Phase-10 section fails the build on a forbidden/gendered/numeric string.
- **T-10-04 (Info Disclosure, imports):** NO `URLSession`/`URLRequest`/`dataTask` added (grep-verified); a dedicated no-network test is Plan 03's job.
- **T-10-SC (installs):** ZERO external packages added.

No new threat surface beyond the plan's register.

## Notes for Downstream (Plans 02 / 03)

- **A1 mapping:** descriptive sections (symptoms/cycle/lifestyle) honor the exact `DateInterval`; the insights section should label only `ReportRangeMapping.enginewindowsInRange(range)` (widest-first). `interval(for:from:to:asOf:)` resolves presets; `isValid(preset:from:to:)` drives the Generate-disabled guard.
- **A3 transform (reuse verbatim):** inside `UIGraphicsPDFRenderer.pdfData { ctx in ... }`, after `ctx.beginPage()`, draw a chart with `ImageRenderer(content: chart.frame(width:height:)).render { _, renderInContext in cg.saveGState(); cg.translateBy(x: originX, y: originY); renderInContext(cg); cg.restoreGState() }`. NO `scaleBy(y: -1)` is needed. (Fallback if a toolchain regression mirrors the chart: `translateBy(x:0,y:boxHeight); scaleBy(x:1,y:-1)` before `renderInContext`.)
- **Copy reuse:** Plan 02 maps `CycleSection` / `SymptomsSection.hasEnoughData` / `SectionSource` onto `ReportCopy` strings (e.g. `ReportCopy.sourceTag(_:)`, `ReportCopy.notEnoughDataInRange`, `ReportCopy.cycleSuppressed`) and reuses `Disclaimer.text` verbatim — never inline copy.

## Self-Check: PASSED
