---
phase: 13-trend-chart-scrubbing
verified: 2026-06-10T20:45:00Z
status: human_needed
score: 7/9 must-haves verified (2 device-gated, awaiting human)
overrides_applied: 0
human_verification:
  - test: "Device haptic (CHART-03): on a physical iPhone, scrub across days on a dashboard trend chart"
    expected: "Exactly one selection tick per day boundary; NO tick on initial touch or release"
    why_human: "Haptics are unverifiable in Simulator/XCUITest (Simulator no-ops); only the firing semantics (old != nil && new != nil && old != new) are code-verified"
  - test: "Scroll-vs-scrub feel + tap/release lifecycle (CHART-01/03): on iPhone 16 sim/device, scrub a dashboard chart, swipe vertically, tap once, release"
    expected: "Tooltip follows the finger while touching; a vertical swipe still scrolls the List; release dismisses the tooltip"
    why_human: "OS-version-volatile gesture arbitration; chartXSelection's nil-on-release timing has no API contract (RESEARCH A1/OQ1) — a mid-press assertion would be a deterministic-test flake; recurring release-checklist item per STATE.md"
  - test: "Exported PDF visually unchanged (PDF guard): generate a report and compare chart pages to a v1.0 export"
    expected: "Visually identical — no rule mark, no tooltip, no selection chrome on any chart page"
    why_human: "Visual identity beyond test assertions; automated gates (renderer zero-diff since v1.0, ReportFlowUITests) passed — this is the eyes-on confirmation"
  - test: "VoiceOver per-mark swipe-through + Audio Graph (CHART-04): with VoiceOver on, swipe through a trend chart's logged days, then play the Audio Graph"
    expected: "Each logged day reads date + 'severity n of 10'; the Audio Graph plays discrete tones with gaps SILENT (never zeros); the selection tooltip readout is reachable as one quiet element"
    why_human: "VoiceOver experience and Audio Graph sonification are not XCUITest-automatable; AX-tree reachability of the tooltip proxy IS proven by the green ChartScrubUITests"
---

# Phase 13: Trend-Chart Scrubbing Verification Report

**Phase Goal:** Users can read the exact day and value behind any dashboard trend chart by touch — gap-honest, haptic, and accessible — while the PDF report render path stays byte-identical
**Verified:** 2026-06-10T20:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1 | SC1: Scrub on a dashboard trend chart shows rule mark + lollipop tooltip with exact date + value | ✓ VERIFIED | `InsightTrendChart.swift`: `chartXSelection(value: $rawSelectedDate)` (line 120) → `resolvedSelection` via `ChartScrubSelection.resolve` (line 75–77, the ONLY raw-date→value path) → `selectionMarks` renders `RuleMark` + `.annotation(position: .top)` tooltip + `PointMark` highlight (lines 227–250). E2E `testScrub_loggedDay_showsSeverityTooltip` re-run by verifier: PASSED (readout "{date} — Severity 6 of 10") |
| 2 | SC2: Unlogged day shows explicit "Not logged" — never zero/interpolated; logged severity-0 reads 0 | ✓ VERIFIED | Resolver: exact `isDate(_:inSameDayAs:)` match or `point: nil` (no nearest-neighbor constructs, grep gate = 0). Unit tests 3 & 4 pin gap→nil and severity-0→real point. Pixels: highlight `PointMark` only `if let point = sel.point` (line 242). E2Es `testScrub_gapDay_showsNotLogged` (asserts `.notLogged` marker exists, readout contains "Not logged", NOT "Severity") and `testScrub_severityZeroDay_readsZeroNotNotLogged` (asserts "Severity 0 of 10" AND `.notLogged` ABSENT) — both re-run by verifier: PASSED |
| 3 | SC3: Day-change haptic; tooltip lives while touching, dismisses on release | ? UNCERTAIN (human) | Code semantics verified: `.onChange(of: resolvedSelection?.day)` fires `haptics.selectionChanged()` only when `old != nil && new != nil && old != new` (lines 126–133) — excludes initial touch and release by construction, covers logged↔gap. Actual haptic feel and release-dismiss timing are device/OS-gated — human checkpoint 1 & 2 |
| 4 | SC4: VoiceOver reads per-day values without the drag gesture | ? UNCERTAIN (human) | Per-mark `accessibilityLabel`/`accessibilityValue` KEPT on Line/Point marks (lines 180–181, 192–193); `AXChartDescriptorRepresentable` conformance with `isContinuous: false`, logged-only sorted data points (lines 327–360); tooltip a11y rides overlay proxy (Swift Charts drops in-annotation a11y — Rule 1 fix, 13-03) with `.contain` shield; AX-tree reachability PROVEN by green E2Es asserting `accessibilityValue` via `element.value`. VoiceOver/Audio Graph experience itself — human checkpoint 4 |
| 5 | SC5: Exported PDF renders unchanged — interactivity never reaches ReportPDFRenderer → InsightTrendChart | ✓ VERIFIED | Re-run by verifier: `git log v1.0..HEAD -- ReportPDFRenderer.swift` EMPTY; `git status --porcelain` for that path EMPTY. `ReportPDFRenderer.swift:309` constructs `InsightTrendChart(points:window:directionWord:headline:asOf:inkColor:)` — no `allowsSelection` arg → default `false` → `chartXSelection` modifier NOT attached (conditional `Group` branch, lines 117–158). Visual compare = confirmatory human checkpoint 3 |
| 6 | Resolver honesty unit-pinned (exact match, gap→nil, severity-0→0, DST-safe, unsorted-safe) | ✓ VERIFIED | `ChartScrubSelectionTests.swift`: 7 tests covering nil input, exact match, gap, severity-0, 23:59 boundary, Europe/Berlin DST, unsorted input. `swift test --package-path EndoInsights` re-run by verifier: 124 tests / 14 suites PASSED |
| 7 | Tooltip copy audited + pinned BEFORE any view renders it | ✓ VERIFIED | `InsightsCopy.swift:110,117`: `scrubNotLogged = "Not logged"`, `scrubSeverity(_:) = "Severity \(n) of 10"`; BOTH in `auditableStrings` (lines 133, 141). `CopyAuditTests.swift:220–229`: verbatim pin tests + registration assertions. Zero `"Not logged"` literals in the view (grep gate = 0) — tooltip uses `InsightsCopy.scrubNotLogged`/`.scrubSeverity` only |
| 8 | Test seam production-inert (triple-gated) | ✓ VERIFIED | `AppDependencies.swift:122–135`: parse inside `#if DEBUG` AND `if uiTestReset` pairing, defensive `firstIndex`+bounds-checked `en_US_POSIX yyyy-MM-dd` parse (garbage→nil); `UITestScrubDayKey.defaultValue = nil` (line 643–645); chart reads it only inside the `allowsSelection` branch with the `xDomain.contains` guard (lines 140–144). Seed fixture `seedScrubFixture` (lines 362–371): offset-3 skipped (gap), offset-2 severity 0, gated `seedScrub && uiTestReset` (line 249); `seedThresholdCrossingInsights` untouched |
| 9 | Deterministic seam-driven E2Es prove CHART-01/02/04 end-to-end | ✓ VERIFIED | `ChartScrubUITests.swift` (171 lines, 3 tests): severity-scoped `ENDSWITH '.severity.chart.tooltip'` queries, zero `XCUICoordinate`/`normalizedOffset`/`sleep(` (grep gate = 0). Re-run by verifier on iPhone 16 simulator: **3/3 PASSED, TEST SUCCEEDED** (gap 28.4s, logged 25.0s, severity-0 25.2s) |

**Score:** 7/9 truths verified — 2 UNCERTAIN are inherently device-gated (haptic feel, VoiceOver experience), with their automatable halves fully verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift` | Pure exact-day resolver, ≥25 lines, exports `ChartScrubSelection`/`Resolution`/`resolve` | ✓ VERIFIED | 51 lines; `public enum` + `Sendable, Equatable` `Resolution` + `resolve(_:in:calendar:)`; `import Foundation` only; no nearest-neighbor/epoch-math constructs (grep = 0) |
| `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` | ≥60 lines, all 7 invariants | ✓ VERIFIED | 122 lines, 7 `@Test` cases, fixed-UTC + Berlin-DST fixtures; green in package run |
| `Sources/EndoApp/Features/Insights/InsightsCopy.swift` | `scrubNotLogged` + `scrubSeverity(_:)` registered in `auditableStrings` | ✓ VERIFIED | Both strings present and registered in the same enum; representative `scrubSeverity(6)` appended |
| `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` | Additive `allowsSelection` layer: conditional `chartXSelection`, lollipop chrome, haptic, AXChartDescriptor, a11y proxy, seam read | ✓ VERIFIED | All present; `allowsSelection: Bool = false` default; chrome/haptic/seam/proxy all branch-local to `allowsSelection == true` |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` | `.trend` branch passes `allowsSelection: true` + `selectionA11yIDRoot` | ✓ VERIFIED | Lines 161–162: `allowsSelection: true, selectionA11yIDRoot: "\(idRoot).chart"` — one call site |
| `Sources/EndoApp/App/AppDependencies.swift` | Seam parse + seed fixture + EnvironmentKey | ✓ VERIFIED | All three present with the triple gating described in truth 8 |
| `Sources/EndoApp/App/EndoAppApp.swift` | Root injection of `\.uiTestScrubDay` | ✓ VERIFIED | Line 43: `.environment(\.uiTestScrubDay, dependencies.uiTestScrubDay)` |
| `Tests/EndoAppUITests/ChartScrubUITests.swift` | ≥60 lines, 3 seam-driven E2Es | ✓ VERIFIED | 171 lines, 3 tests, re-run green |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| InsightsCopy.swift | `auditableStrings` | same-edit registration | ✓ WIRED | `scrubNotLogged` (line 133) + `scrubSeverity(6)` (line 141) in the array; CopyAuditTests asserts containment |
| ChartScrubSelectionTests | `ChartScrubSelection.resolve` | Swift Testing fixtures | ✓ WIRED | Every test calls `ChartScrubSelection.resolve`; no tautologies |
| InsightTrendChart | `ChartScrubSelection.resolve` | computed `resolvedSelection` — the ONLY raw-date→value path | ✓ WIRED | Line 76; grep confirms zero `value(atX:)`/proxy lookups |
| InsightTrendChart | `InsightsCopy.scrubNotLogged`/`scrubSeverity` | tooltip lines | ✓ WIRED | Lines 262, 268, 299, 316, 318; zero tooltip string literals in view |
| InsightCardView | InsightTrendChart | `.trend` branch construction | ✓ WIRED | `allowsSelection: true` at the single `.trend` call site |
| AppDependencies | InsightTrendChart | `\.uiTestScrubDay` EnvironmentKey through App root | ✓ WIRED | Key defined (AppDependencies:643), injected (EndoAppApp:43), read behind `allowsSelection` + `xDomain.contains` (InsightTrendChart:140–144) |
| ChartScrubUITests | tooltip a11y ids | `ENDSWITH` predicate descendant queries | ✓ WIRED | `.severity.chart.tooltip` / `.severity.chart.tooltip.notLogged` queries; E2Es green |
| ReportPDFRenderer | InsightTrendChart | default `allowsSelection` (zero-diff gate vs v1.0) | ✓ WIRED | Construction omits `allowsSelection` (line 309); `git log v1.0..HEAD` empty; working tree clean |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| InsightTrendChart tooltip | `resolvedSelection` | `rawSelectedDate` (`chartXSelection` binding or seam) → `ChartScrubSelection.resolve` over `points` | Yes — E2E asserts real seeded values ("Severity 6 of 10" / "Not logged" / "Severity 0 of 10") render through the full stack | ✓ FLOWING |
| Audio Graph descriptor | `points` (sorted, logged-only) | Same `points` prop the chart renders | Yes — same data source as the visual marks | ✓ FLOWING |
| Seam selection | `uiTestScrubDay` | Launch-arg parse → env injection → `.onAppear` | Yes under test args; `nil` in production (default) — intentional | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Resolver invariants + package boundary | `swift test --package-path EndoInsights` | 124 tests / 14 suites passed | ✓ PASS |
| CHART-01/02/04 end-to-end (logged / gap / severity-0) | `xcodebuild test … -only-testing:EndoAppUITests/ChartScrubUITests` | 3/3 passed, TEST SUCCEEDED, exit 0 | ✓ PASS |
| PDF renderer untouched since v1.0 | `git log --oneline v1.0..HEAD -- …/ReportPDFRenderer.swift` + `git status --porcelain` | Both EMPTY | ✓ PASS |
| No custom gestures / proxy lookups / tooltip literals / `UIAccessibility.post` in chart | grep gates (non-comment) | All 0 | ✓ PASS |
| No nearest-neighbor/epoch math in resolver | grep gate (non-comment) | 0 | ✓ PASS |
| No coordinates/sleeps in E2E file | grep gate (non-comment) | 0 | ✓ PASS |

(ReportFlowUITests + InsightsDashboardUITests were green in the 13-03 closing pass per SUMMARY; not re-run here — the PDF integrity claim is independently re-proven by the git gates plus the construction-site check, and the dashboard path is exercised by the re-run ChartScrubUITests harness.)

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| CHART-01 | 13-01, 13-02, 13-03 | Scrub to read exact date + value via rule mark + tooltip | ✓ SATISFIED (feel → human) | Truths 1, 6, 9 |
| CHART-02 | 13-01, 13-02, 13-03 | Explicit "not logged" on gaps — never zero/interpolated; severity-0 reads 0 | ✓ SATISFIED | Truths 2, 6, 9 — unit-pinned AND E2E-proven |
| CHART-03 | 13-02 | Day-change selection haptic; tooltip live-while-touching/dismiss-on-release | ? NEEDS HUMAN | Truth 3 — firing semantics code-verified; device haptic + release lifecycle are checkpoints 1–2 |
| CHART-04 | 13-02, 13-03 | VoiceOver non-gesture per-day readout | ? NEEDS HUMAN | Truth 4 — per-mark labels + Audio Graph + reachable tooltip proxy code-verified and AX-tree-proven; VoiceOver experience is checkpoint 4 |

No orphaned requirements: REQUIREMENTS.md maps exactly CHART-01..04 to Phase 13; all four are claimed by the plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | No TBD/FIXME/XXX/TODO/HACK/placeholder markers in any phase-modified file | — | — |

Notable (Info): 13-03 found and fixed a real CHART-04 bug from 13-02's design — Swift Charts silently drops `.annotation` a11y content; the readout/ids were moved to an overlay proxy (`selectionAccessibilityProxy`, commit `ec79db0`) with the visual annotation `.accessibilityHidden(true)`. Verified in current source; the fix is what the green E2Es exercise.

### Human Verification Required

#### 1. Device haptic (CHART-03)

**Test:** On a physical iPhone, scrub across days on a dashboard trend chart.
**Expected:** Exactly one selection tick per day boundary; no tick on initial touch or release.
**Why human:** Haptics no-op in the Simulator and are unverifiable in XCUITest; only the `onChange` firing semantics are code-verified.

#### 2. Scroll-vs-scrub feel + tap/release lifecycle (CHART-01/03)

**Test:** On an iPhone 16 simulator/device: scrub a chart, swipe vertically, tap once, then release.
**Expected:** Tooltip follows the finger while touching; vertical swipe still scrolls the List; release dismisses the tooltip.
**Why human:** OS-version-volatile gesture arbitration; `chartXSelection`'s nil-on-release timing has no API contract — a deterministic assertion would flake. Recurring release-checklist item per STATE.md.

#### 3. Exported PDF visually unchanged (PDF guard)

**Test:** Generate a report and compare the chart pages to a v1.0 export.
**Expected:** Visually identical — no rule mark, tooltip, or selection chrome.
**Why human:** Visual identity beyond test assertions; the renderer zero-diff and behavioral gates passed — this is the eyes-on confirmation.

#### 4. VoiceOver per-mark swipe-through + Audio Graph (CHART-04)

**Test:** With VoiceOver on, swipe through a trend chart's logged days; play the Audio Graph; reach the selection tooltip.
**Expected:** Each logged day reads date + "severity n of 10"; Audio Graph plays discrete tones with gaps silent (never zeros); tooltip readout reachable as one quiet element.
**Why human:** VoiceOver/Audio Graph sonification are not XCUITest-automatable; AX-tree reachability of the tooltip proxy is already proven by the green E2Es.

### Gaps Summary

No gaps. All nine merged must-haves resolve to VERIFIED or device-gated UNCERTAIN; every automated claim in the SUMMARYs was independently re-verified against the codebase and by re-running the package suite, the three ChartScrubUITests, the PDF git zero-diff gates, and all grep gates. The four remaining items are inherently human (device haptics, gesture feel, visual PDF identity, VoiceOver experience) and were deliberately catalogued as manual-only in 13-VALIDATION from the start.

---

_Verified: 2026-06-10T20:45:00Z_
_Verifier: Claude (gsd-verifier)_

---

## Post-review automated re-confirmation (2026-06-15, after code-review fixes)

After the 5 code-review findings were applied (commits `215533d`/`3d8a369`/`60809ae`/`902a622`/`06fb8ec`, fast-forwarded at `8565c68`), the full automated gate was re-run on the iPhone 16 simulator:

- **EndoInsights package:** 124/124 green (resolver invariants intact).
- **ChartScrubUITests:** 3/3 green — the WR-01 `.accessibilityLabel` addition did not disturb the `.value`-by-identifier assertions; gap vs. severity-0 still distinguishable end-to-end.
- **CopyAuditTests:** 32/32 green — IN-02 unification (per-mark a11y value now routes through `InsightsCopy.scrubSeverity`) left the copy audit clean.
- **InsightsDashboardUITests:** 6/6 green — confirmed test-by-test (`testFlare_rendersFirst` in isolation; the other five across full-suite runs). The intermittent bare "TEST FAILED" with no `Test Case … failed` line was a simulator-runner crash (signal-kill / runner-install), the known host flake logged in STATE.md — never an assertion failure.
- **PDF protection:** `git log v1.0..HEAD -- …/ReportPDFRenderer.swift` empty; zero working-tree diff — byte-identical guarantee holds after the fixes.

Automated coverage is fully green. The 4 device-gated items below remain the only open verification (carried to the pre-release checklist, consistent with the v1.0 device-gated deferrals — they cannot run in this environment).
