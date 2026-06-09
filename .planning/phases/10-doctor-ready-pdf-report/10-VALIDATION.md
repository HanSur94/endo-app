---
phase: 10
slug: doctor-ready-pdf-report
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| (planner-filled) | — | — | RPRT-01/02/03 | — | — | — | — | — | ⬜ pending |

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
