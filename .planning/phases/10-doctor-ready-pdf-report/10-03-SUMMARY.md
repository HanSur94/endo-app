---
phase: 10-doctor-ready-pdf-report
plan: 03
subsystem: ui
tags: [swiftui, pdfkit, sharelink, settings, xcuitest, report, privacy, no-upload]

# Dependency graph
requires:
  - phase: 10-01
    provides: "Pure ReportModel.assemble + ReportCopy + ReportRangeMapping (A1 + isValid guard)"
  - phase: 10-02
    provides: "@MainActor ReportPDFRenderer.renderToTempFile(model:) -> URL (vector 8-section PDF)"
provides:
  - "Settings 'Export report' entry (settings.report.entry) → ReportBuilderView"
  - "ReportBuilderView — preset (30/90/custom) + DatePickers + invalid-range guard + Generate → on-device assemble→render→preview"
  - "ReportPreviewView — PDFKit PDFView preview + ShareLink(item: localFileURL) (RPRT-03, explicit-action egress, no auto-upload)"
  - "PDFKitView (UIViewRepresentable) with report.preview.pdf a11y id on the SwiftUI wrapper"
  - "ReportNoNetworkTests — RPRT-03 no-upload invariant (source scan of Features/Report/)"
  - "ReportFlowUITests — 2 E2E cases (full flow + invalid-range guard) via -uitest-seed-report / -uitest-report-invalid-range"
affects: [phase-verification-gate]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ShareLink(item: localFileURL) — explicit-action share, no automatic upload (RPRT-03)"
    - "Source-scan invariant test (#filePath over Features/Report/, comment-stripped, anti-vacuity) locks the no-network contract — mirrors BoundaryLintTests"
    - "Test-only -uitest-report-invalid-range seam seeds an inverted custom range so the guard E2E is deterministic (no compact-DatePicker-calendar driving)"

key-files:
  created:
    - "Sources/EndoApp/Features/Report/ReportBuilderView.swift"
    - "Sources/EndoApp/Features/Report/ReportPreviewView.swift"
    - "Sources/EndoApp/Features/Report/PDFKitView.swift"
    - "Tests/EndoAppTests/ReportNoNetworkTests.swift"
    - "Tests/EndoAppUITests/ReportFlowUITests.swift"
  modified:
    - "Sources/EndoApp/Features/Settings/SettingsView.swift"
    - "Sources/EndoApp/App/AppDependencies.swift"

requirements-completed: [RPRT-01, RPRT-02, RPRT-03]

# Metrics
duration: spanned a platform Bash outage (see note)
completed: 2026-06-09
---

# Phase 10 Plan 03: Report Builder + Preview + Share Summary

**The payoff slice: a Settings "Export report" entry → `ReportBuilderView` (preset / custom-range with an inline invalid-range guard) → on-device assemble + render → `ReportPreviewView` (PDFKit preview + `ShareLink` to the system share sheet, no auto-upload), with a no-network source-scan invariant (RPRT-03) and a 2-case XCUITest E2E — both green.**

## Commits
- `6125e7a` feat(10-03): wire Settings → report builder → PDFKit preview → ShareLink
- `92733f6` test(10-03): lock RPRT-03 no-upload invariant via report-module source scan
- `f69acf8` test(10-03): ReportFlowUITests E2E + -uitest-seed-report fixture

## Accomplishments
- **Settings entry** (`settings.report.entry`) opens `ReportBuilderView`: a `Form` with the segmented preset Picker (Last 30 / Last 90 / Custom), conditional From/To `DatePicker`s, the inline `report.range.invalid` guard, and a Generate CTA disabled whenever the custom range is invalid (`ReportRangeMapping.isValid`). Generate assembles the pure `ReportModel` from a range-filtered `SwiftDataRepository` + the locked engine, then hops to `@MainActor` to render to a LOCAL temp file and push the preview.
- **`ReportPreviewView`** renders the PDF via a `PDFKitView` (`UIViewRepresentable` over `PDFView`, `report.preview.pdf` a11y id on the SwiftUI wrapper) and exposes a `ShareLink(item: url)` (`report.share`) — the ONLY egress, explicit-action, no automatic upload (RPRT-03). The temp file is deleted on `onDisappear`.
- **`ReportNoNetworkTests`** — a `#filePath`-relative source scan asserting `Features/Report/` imports no `URLSession`/`URLRequest`/`dataTask` (comment-stripped, non-empty anti-vacuity guard). Locks the no-silent-upload invariant.
- **`ReportFlowUITests`** — both cases GREEN: (1) Settings → Export report → preset → Generate → preview (PDF + Share present); (2) inverted custom range → guard shown + Generate disabled (driven via `-uitest-report-invalid-range`). `-uitest-seed-report` (gated behind in-memory `-uitest-reset`) seeds 21 symptom+sleep days + 2 bleeding days.

## Verification (iPhone 16 Simulator)
- App `xcodebuild build` (after `xcodegen generate`): SUCCEEDED.
- `ReportModelTests` / `ReportRenderTests` / `ReportNoNetworkTests` / `ReportRenderSpikeTests`: **14/14 GREEN**.
- `ReportFlowUITests`: **2/2 GREEN**.

## Notes / deviations
- **Platform Bash-classifier outage:** the original 10-03 executor implemented all files but a session-wide Bash safety-classifier outage (under a different session model) blocked every build/test/commit. Tasks 1–2 had been committed before the outage; this continuation (back on a healthy classifier) ran `xcodegen generate` + the full build + both test suites, fixed one brittle E2E (the invalid-range case was fighting the compact DatePicker calendar — replaced with the deterministic `-uitest-report-invalid-range` seam), and committed Task 3.
- **End-of-phase human-verify (deferred, per `human_verify_mode=end-of-phase`):** PDF visual credibility on a real populated range + the system share-sheet confirmation (`/tmp/e2e/report-01-preview.png`, `report-02-invalid-range.png` saved). Same posture as prior phases' deferred human checks.
- Pre-existing unrelated flake (`CheckInFlowUITests` medication-toggle) remains logged in `deferred-items.md` — not touched.
