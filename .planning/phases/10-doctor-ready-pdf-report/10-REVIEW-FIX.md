---
phase: 10-doctor-ready-pdf-report
fixed_at: 2026-06-09T23:12:00Z
review_path: .planning/phases/10-doctor-ready-pdf-report/10-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-06-09T23:12:00Z
**Source review:** .planning/phases/10-doctor-ready-pdf-report/10-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope (Critical + Warning): 8 (CR-01, WR-01..WR-07)
- Fixed: 8
- Skipped: 0
- Also addressed (out of strict scope, requested in the fix brief): IN-01, IN-04

**Verification:** XcodeGen-regenerated project, full `build-for-testing` on
`iPhone 16` (iOS 26.5 SDK, Swift 6) → **TEST BUILD SUCCEEDED**. Affected suites
run on the simulator (results below) — all green.

**Locked invariants preserved:** absence≠0 / no-phantom-cycle / sparse→"not enough
data" all still asserted green (ReportModelTests). No networking added — the
`reportModuleImportsNoNetworking` grep invariant (ReportNoNetworkTests) stays
green. Zero new third-party deps (only the first-party `SwiftData` import was
added for the off-main context). Report module still out of the EndoInsights
package; all clinician-facing copy still routed through ReportCopy/Disclaimer.

## Test Results

| Suite | Result |
|-------|--------|
| ReportModelTests (Swift Testing) | 11/11 passed (incl. 2 new WR-03 tests) |
| ReportRenderTests (Swift Testing) | 3/3 passed |
| ReportNoNetworkTests (Swift Testing) | 1/1 passed (no-network invariant intact) |
| ReportFlowUITests (XCUITest) | 2/2 passed (share flow + invalid-range guard) |

Total: 15 unit tests + 2 UI tests, 0 failures.

## Fixed Issues

### CR-01: Temp PDF deleted on preview onDisappear while share sheet is still presenting

**Files modified:** `Sources/EndoApp/Features/Report/ReportPreviewView.swift`
**Commit:** 9e33a94
**Applied fix:** Removed the eager `.onDisappear { removeItem(at: url) }` delete.
The system share sheet reads the Art.9 PDF URL lazily/asynchronously after the
preview disappears, so the delete raced it and could export a zero-byte/half-
written file. Cleanup is now owned solely by the producer — `ReportBuilderView`
deletes any prior temp file before each fresh generation. Header/doc comments
updated to reflect the producer-owned lifecycle. Verified by the green
`testSettingsToPreviewShare` XCUITest which drives Settings → builder → preview →
Share.

### WR-01: `.last30` / `.last90` start boundary off-by-one (first claimed day excluded)

**Files modified:** `Sources/EndoApp/Features/Report/ReportRangeMapping.swift`
**Commit:** 25b4e67
**Applied fix:** Anchor the preset start to `calendar.startOfDay(for: asOf)` before
subtracting 30/90 days, so `range.start` aligns with a snapshot day key (00:00).
The end stays at `asOf` to keep the inclusive-end posture (today's logs stay in).
The earliest claimed day is no longer dropped by the half-open
`DateInterval.contains`.

### WR-02: Source legend omits "Self-reported" for a symptoms-only report

**Files modified:** `Sources/EndoApp/Features/Report/ReportModel.swift`
**Commit:** 4ff510c
**Applied fix:** Fold symptom provenance (`inRange.map { $0.symptoms.isEmpty ? nil
: .manual }`) into `allFieldSources`, which drives both `summary.source` and the
`SourceLegend`. A symptoms-only range now defines the Self-reported legend line
that matches the badge the symptoms section already draws.

### WR-03: Chart embedded fabricated per-day dates + interpolated severities (HONESTY)

**Files modified:** `Sources/EndoApp/Features/Report/ReportModel.swift`,
`Sources/EndoApp/Features/Report/ReportPDFRenderer.swift`,
`Tests/EndoAppTests/ReportModelTests.swift`
**Commit:** 0e99f01
**Applied fix:** Added a real `points: [InsightPoint]` series to `SymptomRow`,
populated in `symptomsSection` from the actual in-range snapshots — one point per
day the symptom was logged WITH a severity, at the snapshot's start-of-day date and
that day's max recorded severity. Days logged without a severity (and unlogged days)
are simply absent, so the embedded `InsightTrendChart` honors honest gaps (absence
is a break, never an interpolated value — same contract the dashboard uses). The
renderer now feeds `row.points` to the chart and deletes the old `chartPoints`
synthesizer entirely. If the busiest symptom has no severity-bearing points, the
renderer draws the honest text fallback instead of inventing data. Added two
ReportModelTests asserting the plotted dates are the real logged days (no fabricated
every-other-day dates), the values are the real recorded severities (no min→max
ramp), and severity-less days are omitted (honest gaps).

### WR-04: Report assembly ran on the main actor (blocked the UI on large ranges)

**Files modified:** `Sources/EndoApp/Features/Report/ReportBuilderView.swift`
**Commit:** 353509a (combined with WR-05 — both edits live in `generate()`)
**Applied fix:** The assemble (full `daySnapshots()` fetch + one
`daySnapshotSources(on:)` round-trip per in-range day) now runs genuinely off-main
on a `Task.detached(priority: .userInitiated)` with a dedicated, confined
`ModelContext` built off the same `ModelContainer` and held in an `@unchecked
Sendable RepoBox` (the same pattern AppDependencies uses for the import service,
since `ModelContext`/`SwiftDataRepository` is not `Sendable`). Only the brief
`ImageRenderer` draw hops to `@MainActor`. Added `import SwiftData` for the
context/container types.
**Note:** requires human verification — this changes the concurrency/isolation
shape (a dedicated off-main context replacing the view's main-actor context). The
build is clean under Swift 6 strict concurrency and all suites (incl. the share
XCUITest that exercises real generation) pass, but please confirm the off-main
context behaves correctly under real-device SwiftData/CloudKit load.

### WR-05: `renderToTempFile` swallowed write failure, returned a missing-file URL

**Files modified:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift`,
`Sources/EndoApp/Features/Report/ReportBuilderView.swift`,
`Tests/EndoAppTests/ReportRenderTests.swift`
**Commit:** 353509a (combined with WR-04)
**Applied fix:** `renderToTempFile` now returns `URL?` and returns `nil` on a write
failure (logging only the error type — no health content). `generate()` routes a
`nil` to `phase = .failed` (the honest error state) instead of pushing a blank
preview / sharing a missing file. ReportRenderTests `#require`-unwraps the success
URL.

### WR-06: Two-pass paginator read `.now` inside the draw pass (could drift page count)

**Files modified:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift`
**Commit:** 0e99f01 (combined with WR-03 — same `drawChart` rewrite)
**Applied fix:** The chart's `asOf` is now a fixed value threaded from the model
(`model.range.end`) instead of `points.map(\.date).max() ?? .now`. Neither layout
pass reads wall-clock `.now`, so both passes are byte-identical and "Page n of m"
can never drift across midnight.

### WR-07: Cycle section hardcoded `source: .none`, hiding bleeding provenance

**Files modified:** `Sources/EndoApp/Features/Report/ReportModel.swift`,
`Sources/EndoApp/Features/Report/ReportPDFRenderer.swift`
**Commit:** a751abc
**Applied fix:** Added `cycleSource: SectionSource` to `ReportModel`, aggregated from
the in-range `flow` provenance (`.appleHealth` for imported menstrual flow or
`.manual`). `CycleSection` is an enum (no room for a per-case field), so the source
rides on the model. The renderer's Cycle heading now passes `model.cycleSource`
instead of `.none`, consistent with the symptoms/lifestyle sections.

## Also addressed (Info — requested in the fix brief)

### IN-01: Stale "Task 1 stub" header comment + dead AssemblyError

**Files modified:** `Sources/EndoApp/Features/Report/ReportModel.swift`
**Commit:** 8290003
**Applied fix:** Rewrote the file header/doc to describe the implemented assembly
(which only rethrows repository errors) and deleted the now-dead `AssemblyError`
enum after verifying it is unreferenced in sources and tests.

### IN-04: `directionWord` derived from fabricated chart points

**Commit:** 0e99f01 (resolved as a side effect of WR-03)
**Applied fix:** With WR-03, `directionWord(points)` now receives the real logged
series, so the chart caption's trend word is computed from genuine data rather than
synthesized points. No separate change needed.

## Not addressed (out of scope)

- **IN-02** (`Confidence.none` mapped to "Confidence: low") and **IN-03**
  (`PDFKitView.updateUIView` reload guard) were Info-tier and not in the
  Critical+Warning scope or the fix brief. Left for a follow-up; both are
  low-impact and documented in REVIEW.md.

---

_Fixed: 2026-06-09T23:12:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
