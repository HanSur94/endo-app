---
phase: 10-doctor-ready-pdf-report
plan: 02
subsystem: ui
tags: [pdf, report, renderer, coretext, imagerenderer, pdfkit, pagination, claimspolicy, honesty]

# Dependency graph
requires:
  - phase: 10-doctor-ready-pdf-report
    plan: 01
    provides: "Pure ReportModel + ReportCopy choke-point + the RESOLVED A3 transform (translate-only, no y-flip) reused verbatim"
  - phase: 08-insights-integration
    provides: "InsightTrendChart (extended with an additive inkColor) reused via ImageRenderer for the embedded vector chart"
  - phase: 02-day-log
    provides: "The three-state CycleSection contract the renderer draws verbatim (no phantom cycle)"
provides:
  - "@MainActor ReportPDFRenderer.render(model:) -> Data — 8-section CoreText vector PDF, auto-paginated, per-page footer + Page n of m, prominent disclaimer band, embedded vector chart"
  - "ReportPDFRenderer.renderToTempFile(model:) -> URL — the temp-file URL Plan 03's preview + ShareLink bind to (Plan 03 owns deletion)"
  - "InsightTrendChart.inkColor: Color = .accentColor — additive single-ink-print parameter (default keeps the live dashboard byte-identical)"
  - "ReportCopy section headings + table/lifestyle/cycle-confidence copy (all routed through the ClaimsPolicy audit)"
affects: [10-03-ui, doctor-report, pdf-rendering, share]

# Tech tracking
tech-stack:
  added: []   # ZERO external packages — first-party UIGraphicsPDFRenderer / ImageRenderer / PDFKit / CoreText only
  patterns:
    - "Render layer is THIN: re-derives NO honesty/cycle/insight logic — it lays the pure ReportModel out, nothing more"
    - "Two-pass layout: a dry measure pass discovers the total page count so the footer can stamp 'Page n of m' before page 1"
    - "Y-cursor pagination with NSAttributedString.boundingRect height-measure + a no-orphan heading guard (heading + first body line carry together)"
    - "Vector chart embed via ImageRenderer.render into the live PDF CGContext using Plan 01's A3 transform (translate-only)"
    - "Package enums labelled via the app's AUDITED plainLabel through the same-raw-value bridge (no new copy invented for data labels)"

key-files:
  created:
    - Sources/EndoApp/Features/Report/ReportPDFRenderer.swift
    - Tests/EndoAppTests/ReportRenderTests.swift
  modified:
    - Sources/EndoApp/Features/Insights/InsightTrendChart.swift
    - Sources/EndoApp/Features/Report/ReportCopy.swift

key-decisions:
  - "Two-pass page-count: a context-free dry layout pass computes totalPages so the per-page footer can print an honest 'Page n of m' on every page (including page 1) without a post-hoc rewrite."
  - "Chart points are SYNTHESIZED descriptively from the busiest symptom row's min/max severity across its logged-day count — the chart is illustrative and ALWAYS paired with the exact trend table beside it (the table carries the real evidence count; the chart is never the sole carrier of meaning, D-02/a11y)."
  - "Data labels for the package SymptomType/SleepQuality/ActivityType come from the APP's audited plainLabel via the shared-raw-value bridge, so no new clinician-facing copy is invented in the renderer (Pitfall 3 stays intact)."
  - "Cycle-confidence renders a plain WORD ('Confidence: high/medium/low') added to ReportCopy + the audit array — never a number/percentage (D-11)."

patterns-established:
  - "A pure value-model → @MainActor render boundary: the renderer is verified by PDFDocument text-extraction substring asserts (behavior), NOT visual diffing."
  - "Additive view-parameter for print: inkColor defaults to .accentColor (live screen unchanged), the renderer passes .black for single-ink print."

requirements-completed: [RPRT-01, RPRT-02]

# Metrics
duration: 14min
completed: 2026-06-09
---

# Phase 10 Plan 02: Doctor-Ready Report — @MainActor Vector PDF Renderer Summary

**A thin `@MainActor ReportPDFRenderer` that turns the pure `ReportModel` (Plan 01) into a clinician-credible, multi-page, selectable-text, on-device VECTOR PDF — 8 CoreText sections, a prominent `Disclaimer.text` band atop page 1, a per-page footer + "Page n of m", the embedded `InsightTrendChart` drawn as single-ink-black VECTOR via Plan 01's A3 transform, and a temp-file URL — plus the small additive `inkColor` chart parameter, all proven by page-count + `PDFDocument.string` substring tests (not visual diffing).**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-06-09T19:48:40Z
- **Completed:** 2026-06-09T20:03Z
- **Tasks:** 3 of 3
- **Files modified:** 4 (2 created, 2 extended)

## Accomplishments

- **Task 1 — Additive `inkColor` on `InsightTrendChart`:** Added `inkColor: Color = .accentColor` and replaced the two hardcoded `Color.accentColor` foregroundStyle uses (LineMark + PointMark) with it. The default keeps the live dashboard byte-identical; the renderer constructs a FRESH chart with `inkColor: .black` for single-ink print (D-02). No other behavior (axis, gaps, a11y, animation, text summary) changed. **Commit 05d9820.**
- **Task 2 — `ReportPDFRenderer`:** Built the `@MainActor enum ReportPDFRenderer` with `render(model:) -> Data` and `renderToTempFile(model:) -> URL`. It draws all 8 D-04 sections as selectable CoreText vector text on auto-paginated US-Letter pages (56pt margins, the 22/14/10/8 type scale, near-monochrome palette with `#F2F2F7` band fill + `#D1D1D6` hairlines), a PROMINENT boxed disclaimer band rendering `Disclaimer.text` verbatim atop page 1, a per-page footer + "Page n of m" (via a two-pass page-count), no-orphan section headings, the three-state `CycleSection` verbatim (suppressed off-state / not-enough-data / derived cycle-day + next-window DATE RANGE + plain confidence word — no phantom cycle), nil-honest lifestyle ("Not logged" ≠ 0), gated insights (sparse → honest line), and the embedded chart as VECTOR via `ImageRenderer.render` using Plan 01's A3 transform (translate-only, no y-flip) in single-ink black. Every fixed string comes from `ReportCopy`/`Disclaimer.text`; package-enum data labels come from the app's audited `plainLabel`; logs only the error TYPE on failure; no networking. Extended `ReportCopy` with the 8 section headings + table/lifestyle/cycle-confidence copy and added them to `auditableStrings`. **Commit df82462.**
- **Task 3 — `ReportRenderTests`:** Wrote the `@MainActor @Suite` over an in-memory `SwiftDataRepository` populated with manual symptoms across many distinct days (clearing the 3-day trend floor + a chart) + manual lifestyle/bleeding + one imported sleep sample (so the legend surfaces "From Apple Health"). Three GREEN cases: a non-empty, ≥2-page PDF + a written `.pdf` temp URL; extracted `PDFDocument.string` contains `Disclaimer.text` ("not medical advice") + the legend + BOTH source labels; a suppressed-user model renders the cycle off-state with NO fabricated cycle-day/window. Also wired both new files into `EndoApp.xcodeproj` (see Deviations). **Commit 24cd892.**

## Verification

- `ReportRenderTests` — 3/3 GREEN (`generatesNonEmptyMultiPagePDF`, `pdfTextContainsDisclaimerAndLegend`, `suppressedRendersOffStateNotPhantom`).
- Report + chart regression set — 21 tests / 4 suites GREEN (`ReportModelTests`, `ReportRenderSpikeTests`, `ReportRenderTests`, `ChartSeriesGroupingTests`).
- `CopyAuditTests` + `DisclaimerTests` — 18/18 GREEN, including the new `reportCopy_*` scans (no forbidden verb, no gendered token, no numeric confidence) over the section headings + cycle-confidence words added this plan.
- `xcodebuild build` — BUILD SUCCEEDED on iPhone 16 simulator after the renderer was actually compiled (see Deviations).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] New source + test files were not in the Xcode project (renderer was not being compiled)**
- **Found during:** Task 3 — `-only-testing:EndoAppTests/ReportRenderTests` executed 0 tests, and the prior Task-2 build "SUCCEEDED" only because `ReportPDFRenderer.swift` had never been added to the `EndoApp` target's Sources phase, so it was silently absent from the build.
- **Issue:** This project is a classic Xcode project with EXPLICIT file references (no `PBXFileSystemSynchronizedRootGroup`). A `Write`-created `.swift` file is invisible to the build until it is wired into `project.pbxproj` (PBXBuildFile + PBXFileReference + group child + Sources build-phase entry — the same 4-entry pattern used by `ReportModelTests.swift`).
- **Fix:** Added all four entries for `ReportPDFRenderer.swift` (EndoApp target) and `ReportRenderTests.swift` (EndoAppTests target) with fresh unique IDs, then re-ran: the renderer genuinely compiled and the 3 tests went GREEN. A follow-on `SymptomType is ambiguous` compile error (both `EndoApp.SymptomType` and `EndoInsights.SymptomType` in scope) was resolved by qualifying the fixture array as `[EndoApp.SymptomType]`.
- **Files modified:** `EndoApp.xcodeproj/project.pbxproj` (local-only — gitignored, see Notes), `Tests/EndoAppTests/ReportRenderTests.swift`.
- **Commit:** 24cd892 (the `.swift` change; the gitignored pbxproj edit is local).

## Threat Surface

All plan threat-register mitigations are satisfied:

- **T-10-05 (Tampering, renderer copy):** every fixed PDF string is sourced from `ReportCopy`/`Disclaimer.text`; the new section/cycle-confidence strings are in `auditableStrings` and pass `CopyAuditTests`. Asserted end-to-end by `pdfTextContainsDisclaimerAndLegend` (disclaimer + legend + both labels appear in the extracted text).
- **T-10-06 (Tampering, cycle section):** the three-state `CycleSection` is rendered verbatim; a suppressed user shows the off-state with no fabricated cycle-day/window — asserted by `suppressedRendersOffStateNotPhantom`.
- **T-10-07 (Info Disclosure, render error):** the temp-file write logs only `type(of: error)`, never health content.
- **T-10-08 (Info Disclosure, imports):** the renderer adds NO `URLSession`/`URLRequest`/`dataTask` (the dedicated grep invariant is Plan 03's `ReportNoNetworkTests`).
- **T-10-SC (installs):** ZERO external packages added.

No new threat surface beyond the plan's register.

## Notes for Downstream (Plan 03)

- **Bind to the URL:** `ReportPDFRenderer.renderToTempFile(model:)` returns a `FileManager.default.temporaryDirectory` `.pdf` URL named `EndoReport-<yyyyMMdd-yyyyMMdd>.pdf` (no health content in the name). Plan 03's `PDFView` preview + `ShareLink` bind to this URL and OWN its deletion.
- **Main-actor:** `ReportPDFRenderer` is `@MainActor` (ImageRenderer requirement) — call it from the main actor; the pure `ReportModel.assemble` can run off-actor first, then hand the value model in.
- **No-network test (Plan 03):** the renderer module adds no networking; the grep-invariant `ReportNoNetworkTests` is still Plan 03's to add.
- **Project membership:** `EndoApp.xcodeproj/project.pbxproj` is gitignored in this repo; new `.swift` files must be wired into the project locally (4 pbxproj entries) for the build/tests to see them — the `.swift` sources themselves are the tracked artifacts.

## Self-Check: PASSED
