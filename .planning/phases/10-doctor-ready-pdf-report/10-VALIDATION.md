---
phase: 10
slug: doctor-ready-pdf-report
status: planned
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-09
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test`/`#expect`) for logic; XCUITest for E2E UI flows |
| **Config file** | `project.yml` (XcodeGen — EndoApp scheme test targets) |
| **Quick run command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | ~60–180 seconds (simulator build + test) |

---

## Sampling Rate

- **After every task commit:** Run the quick command (logic tests for the touched target)
- **After every plan wave:** Run the full suite
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

> Populated by the planner from PLAN.md tasks. Strategy (per RESEARCH §Validation Architecture): split a PURE `ReportModel` (range-scoped assembly — honesty / source labels / three-state cycle, unit-testable over an in-memory Repository) from a thin `@MainActor` renderer. Render verified by page-count smoke test + `PDFDocument.string` substring asserts (disclaimer + source legend) — NOT visual diffing. A no-network source-scan asserts RPRT-03.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-01-T1 | 10-01 | 1 | RPRT-01/02 | T-10-01..03 | Wave-0 scaffold; A3 render-spike transform resolved; RED honesty/source-label suite | unit (smoke) | `-only-testing:EndoAppTests/ReportRenderSpikeTests` | ❌ Wave 0 | ⬜ pending |
| 10-01-T2 | 10-01 | 1 | RPRT-01/02 | T-10-01/02 | Pure assemble: range scoping, sparse/suppressed honesty, absence≠0, source labels from daySnapshotSources | unit | `-only-testing:EndoAppTests/ReportModelTests` | ❌ Wave 0 | ⬜ pending |
| 10-01-T3 | 10-01 | 1 | RPRT-02 | T-10-03 | ReportCopy through ClaimsPolicy (no forbidden verb/gendered/numeric); A1 window helper + invalid-range guard | unit | `-only-testing:EndoAppTests/CopyAuditTests` | ⚠️ extend | ⬜ pending |
| 10-02-T1 | 10-02 | 2 | RPRT-02 | — | Additive inkColor on InsightTrendChart (print black; live dashboard unchanged) | build | `xcodebuild build` | ✅ exists | ⬜ pending |
| 10-02-T2 | 10-02 | 2 | RPRT-01/02 | T-10-05..07 | 8-section CoreText vector PDF + auto-pagination + footer/page-nums + vector chart embed + temp-file write; no inline literals | build | `xcodebuild build` | ❌ Wave 0 | ⬜ pending |
| 10-02-T3 | 10-02 | 2 | RPRT-01/02 | T-10-05/06 | Page-count smoke + PDFDocument.string asserts (Disclaimer.text + legend + both source labels); suppressed off-state not phantom | unit (text-extraction) | `-only-testing:EndoAppTests/ReportRenderTests` | ❌ Wave 0 | ⬜ pending |
| 10-03-T1 | 10-03 | 3 | RPRT-01/03 | T-10-09/11 | Settings entry -> builder -> Generate -> PDFView preview -> ShareLink; invalid-range guard; temp-file cleanup | build | `xcodebuild build` | ❌ Wave 0 | ⬜ pending |
| 10-03-T2 | 10-03 | 3 | RPRT-03 | T-10-10 | Source-scan: Features/Report/ imports no URLSession/URLRequest/dataTask (no-upload invariant) | unit (source scan) | `-only-testing:EndoAppTests/ReportNoNetworkTests` | ❌ Wave 0 | ⬜ pending |
| 10-03-T3 | 10-03 | 3 | RPRT-01/03 | T-10-13 | E2E: Settings -> Export report -> preset -> Generate -> preview -> Share present; invalid custom range keeps Generate disabled | E2E (XCUITest) | `-only-testing:EndoAppUITests/ReportFlowUITests` | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] A 1-chart `ImageRenderer`→PDF `CGContext` render spike (resolve the UIKit-top-left vs CG-bottom-left coordinate transform, RESEARCH A3) — a tiny render smoke test
- [ ] Pure `ReportModel` assembly scaffolding (range-scoped reads → sections) so honesty/source-label asserts are unit-testable without rendering
- [ ] Custom-range → engine-window mapping decision (RESEARCH A1) locked in the plan before the insights section is built

*Existing Swift Testing + XCUITest infrastructure covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Share-sheet visual + actual export target | RPRT-03 | The system share sheet and the chosen destination (Files/AirDrop/Mail draft) are OS-driven; automated tests assert the file URL is presented but not the user's manual export choice | Generate a report, tap Share, confirm the system sheet appears with the PDF and that nothing is uploaded without an explicit tap. |
| PDF visual credibility on a real range of data | RPRT-01/02 | Clinician-credibility (layout legibility, chart rendering, disclaimer prominence) is a human-judgment call best seen on a real device with real data | Generate a report over a populated range; eyeball layout, the prominent "self-tracked, not diagnostic" header, source labels + legend, and chart legibility. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (the render spike + ReportModel scaffold)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
