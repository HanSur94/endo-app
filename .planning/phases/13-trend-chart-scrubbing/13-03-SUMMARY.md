---
phase: 13-trend-chart-scrubbing
plan: 03
subsystem: insights-charts
tags: [xcuitest, launch-arg-seam, environment-key, e2e, accessibility, pdf-protection, regression-gates]

# Dependency graph
requires:
  - phase: 13-trend-chart-scrubbing
    plan: 01
    provides: ChartScrubSelection.resolve (the only raw-date → value path) + audited scrubNotLogged/scrubSeverity copy
  - phase: 13-trend-chart-scrubbing
    plan: 02
    provides: allowsSelection layer + lollipop chrome + tooltip composition + dashboard switch-on
provides:
  - "-uitest-select-day launch-arg seam: triple-gated (uiTestReset pairing + #if DEBUG + nil env default), defensive en_US_POSIX yyyy-MM-dd parse — the reusable deterministic-selection foundation Phases 15–16 consume"
  - "-uitest-seed-scrub fixture: dense severity-6 spine with the offset-3 gap day + offset-2 severity-0 day, all assertion days inside the last-7 window"
  - "\\.uiTestScrubDay EnvironmentKey threaded AppDependencies → EndoAppApp root → InsightTrendChart .onAppear (xDomain.contains guarded)"
  - "ChartScrubUITests — 3 seam-driven E2Es proving CHART-01/02/04 end-to-end (zero coordinates, zero sleeps)"
  - "Tooltip a11y exposure FIXED: overlay proxy with container shield (Swift Charts never exposes .annotation content in the AX tree)"
  - "Phase regression gates closed: ReportPDFRenderer zero-diff since v1.0, ReportFlowUITests 2/2, InsightsDashboardUITests 6/6, EndoInsights 124/124"
affects: [15-explore, 16-scatter]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Triple-gated test seam: runtime -uitest-reset pairing (in-memory store) + #if DEBUG compile-out + nil EnvironmentKey default — three independent layers, each alone sufficient"
    - "Swift Charts .annotation content is NOT exposed in the accessibility tree — a11y elements for annotation chrome must live on an overlay OUTSIDE the Chart"
    - "Container shield against id bleed: a parent .accessibilityIdentifier masks bare descendant element ids, but ids one level inside an explicit .accessibilityElement(children: .contain) survive (the Section→cell→.confidence precedent, now generalized)"
    - "Seam-scoped E2E queries: ENDSWITH '.severity.chart.tooltip' (signalKey-scoped), never a generic '.chart.tooltip' firstMatch — sibling frequency-trend cards carry 0/1 values that would make generic queries lie"

key-files:
  created:
    - Tests/EndoAppUITests/ChartScrubUITests.swift
  modified:
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/Features/Insights/InsightTrendChart.swift

key-decisions:
  - "Tooltip a11y exposure moved to a transparent overlay proxy (Rule 1 fix): Swift Charts does not expose .annotation content in the AX hierarchy, so the 13-02 in-annotation a11y modifiers were unreachable by VoiceOver AND XCUITest; the visual annotation is now .accessibilityHidden(true) to prevent future duplicates"
  - "E2E queries scoped to .severity.chart.tooltip: the scrub seed also emits a pelvicPain FREQUENCY trend card (0/1 values reading 'Severity 1 of 10') rendered FIRST — a generic .chart.tooltip firstMatch would assert the wrong chart"
  - "No real-gesture smoke test (documented in the file header): nil-on-release timing is undocumented (Assumption A1), so lifecycle is the locked phase-gate manual checkpoint; the seam tests assert content only"
  - "xcodegen generate produced a byte-identical .xcodeproj (synchronized file groups) — the new UITest file is picked up with no project diff"

patterns-established:
  - "Annotation-chrome a11y always rides an overlay proxy with a .contain shield — Phases 15–16 multi-series tooltips must clone this, not in-annotation modifiers"

requirements-completed: [CHART-01, CHART-02, CHART-03, CHART-04]

# Metrics
duration: ~30min
completed: 2026-06-10
---

# Phase 13 Plan 03: Test Seam, Scrub E2E & Regression Gates Summary

**Triple-gated `-uitest-select-day` seam + `-uitest-seed-scrub` fixture threaded via `\.uiTestScrubDay` into the chart's `.onAppear`, three deterministic id-asserted ChartScrubUITests proving logged/gap/severity-0 end-to-end (after fixing Swift Charts' silent non-exposure of annotation a11y), and the phase's closing gates: ReportPDFRenderer zero-diff since v1.0 + all regression suites green**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-06-10T18:10:23Z
- **Completed:** 2026-06-10T18:40:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- **The seam is unreachable in production by THREE independent layers (T-13-02):** the `-uitest-select-day <yyyy-MM-dd>` parse runs only when `-uitest-reset` is present (the in-memory-store pairing every existing seam uses), is wrapped in `#if DEBUG` (compiled out of release entirely), and the `\.uiTestScrubDay` EnvironmentKey defaults to `nil` (a production launch injects nothing). The parse is defensive (T-13-02b, ASVS V5): bounds-checked arg access, strict `en_US_POSIX` `yyyy-MM-dd` in the current time zone, normalized via `Calendar.current.startOfDay` — garbage yields nil, never a crash.
- **`-uitest-seed-scrub` is the deterministic three-state fixture:** a 21-day severity-6 pelvicPain spine ending yesterday, with offset 3 SKIPPED (the in-window gap day) and offset 2 at severity 0 (the logged-zero day) — all three assertion days inside the last-7 window so every window-card resolves the same state (Pitfall 6). `seedThresholdCrossingInsights` is untouched (four existing E2Es depend on it).
- **CHART-01/02/04 proven end-to-end with zero flake surface:** `ChartScrubUITests` launches once per test with the seam args and asserts purely via `waitForExistence` + `element.value` on ids — today-1 reads "Severity 6 of 10" with `.notLogged` absent; today-3 shows the explicit `.notLogged` marker and a readout that says "Not logged" and never "Severity"; today-2 reads "Severity 0 of 10" with `.notLogged` ABSENT (absence ≠ 0, the honesty E2E). Zero `XCUICoordinate`/`normalizedOffset`/`sleep` (grep-gated). Assumption A4 verified: a programmatic `rawSelectedDate` set renders the identical chrome as a gesture.
- **A real CHART-04 bug found and fixed (Rule 1):** Swift Charts does NOT expose `.annotation` content in the accessibility tree — the 13-02 tooltip rendered visually but its a11y element/ids/readout never reached VoiceOver or XCUITest. The readout + `.tooltip`/`.tooltip.notLogged` ids now ride a transparent, non-hit-testable overlay proxy inside the `allowsSelection` branch (the PDF path gains zero modifiers), shielded from the card-level identifier bleed by `.accessibilityElement(children: .contain)`.
- **The phase's fifth success criterion is closed by gate:** `git log v1.0..HEAD -- ReportPDFRenderer.swift` is EMPTY and the working tree is clean for that path (byte-identical PDF render path, by construction AND by gate); ReportFlowUITests 2/2, InsightsDashboardUITests 6/6 (with the full phase landed), and the EndoInsights package 124/124 all green in one closing pass.

## Task Commits

Each task was committed atomically:

1. **Task 1: -uitest-select-day seam + -uitest-seed-scrub fixture + EnvironmentKey thread** - `85dbf9f` (feat)
2. **Task 2a: Tooltip a11y exposure fix (Rule 1 deviation found while writing the E2E)** - `ec79db0` (fix)
3. **Task 2b: ChartScrubUITests — deterministic seam-driven E2E** - `de87884` (test)
4. **Task 3: Phase regression gates** — verification-only, no files changed, no commit

## Files Created/Modified

- `Sources/EndoApp/App/AppDependencies.swift` - `-uitest-select-day` defensive parse (DEBUG + uiTestReset paired), `-uitest-seed-scrub` flag + `seedScrubFixture`, `uiTestScrubDay: Date?` property, `UITestScrubDayKey` EnvironmentKey + `\.uiTestScrubDay`
- `Sources/EndoApp/App/EndoAppApp.swift` - Root injection `.environment(\.uiTestScrubDay, dependencies.uiTestScrubDay)`
- `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` - `.onAppear` seam read (xDomain.contains guarded) + the overlay a11y proxy (`selectionAccessibilityProxy`) replacing the unreachable in-annotation a11y modifiers
- `Tests/EndoAppUITests/ChartScrubUITests.swift` - Three seam-driven E2Es (logged / gap / severity-0), severity-card-scoped queries, realize-by-scroll fallback

## Decisions Made

- **Overlay proxy for annotation a11y** (forced by framework behavior, verified empirically in the AX hierarchy dump): annotation content never appears in the accessibility tree, so the only correct exposure point is a plain SwiftUI overlay outside the `Chart`. The visual annotation is `.accessibilityHidden(true)` so a future OS that starts exposing annotations cannot create duplicate elements.
- **Container shield for the proxy ids**: the card's outer `.accessibilityIdentifier("\(idRoot).chart")` bleeds down and masks bare descendant element ids (empirically verified — the proxy initially surfaced with the chart's id). Wrapping the proxy in `.accessibilityElement(children: .contain)` stops the bleed one level down, exactly like the Section→cell→`.confidence` structure the dashboard harness documents.
- **Severity-scoped E2E queries**: the seed emits BOTH a severity trend card and a frequency trend card for pelvicPain; the frequency card renders first and its 0/1 values read "Severity 1 of 10" — a generic `.chart.tooltip` `firstMatch` would assert the wrong chart. Queries use `ENDSWITH '.severity.chart.tooltip'`.
- **No real-gesture smoke test** (Claude's-discretion call the plan delegates, documented in the test-file header): the live-while-touching/dismiss-on-release lifecycle cannot be asserted deterministically; it is covered by the locked phase-gate manual checkpoint.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Scrub tooltip invisible to the accessibility tree (CHART-04 regression in 13-02's design)**
- **Found during:** Task 2 (first ChartScrubUITests run — all 3 tests failed; the AX hierarchy dump showed the tooltip rendering visually but absent from the tree)
- **Issue:** Swift Charts does not expose `.annotation` content as accessibility elements, so the 13-02 in-annotation `.accessibilityElement/.accessibilityValue/.accessibilityIdentifier` modifiers were unreachable by VoiceOver AND XCUITest — the CHART-04 quiet readout did not actually work
- **Fix:** Moved the readout + ids to a transparent overlay proxy (`selectionAccessibilityProxy`) attached inside the `allowsSelection` branch, with `.accessibilityElement(children: .contain)` shielding the ids from the card-level identifier bleed; the visual annotation is now `.accessibilityHidden(true)`
- **Files modified:** Sources/EndoApp/Features/Insights/InsightTrendChart.swift
- **Commit:** `ec79db0`

**2. [Rule 1 - Bug] Generic tooltip query would assert the wrong card**
- **Found during:** Task 2 (the AX dump showed the pelvicPain FREQUENCY trend card rendering first, its tooltip reading "Severity 1 of 10")
- **Issue:** The plan's `ENDSWITH '.chart.tooltip'` + `firstMatch` assumption (RESEARCH Pattern 6) did not anticipate a sibling frequency-trend card whose 0/1 values alias the severity vocabulary
- **Fix:** Scoped all queries to `'.severity.chart.tooltip'` / `'.severity.chart.tooltip.notLogged'` (still id-driven and deterministic) + a realize-by-scroll fallback for the below-the-fold severity card (plain vertical swipes, the same lazy-row realization scroll the dashboard suite uses)
- **Files modified:** Tests/EndoAppUITests/ChartScrubUITests.swift
- **Commit:** `de87884`

No other deviations — Tasks 1 and 3 executed exactly as written.

## Issues Encountered

None beyond the deviations above. (`gsd-tools` CLI unavailable in this environment; STATE.md/ROADMAP.md updates applied by direct edit, matching prior-phase practice. `xcodegen generate` produced a byte-identical project — the UITest target uses synchronized file groups, so the new test file required no project diff.)

## Verification Results

- `xcodebuild build` (iPhone 16 simulator) — **BUILD SUCCEEDED** after Task 1
- `xcodebuild test … -only-testing:EndoAppUITests/ChartScrubUITests` — **3/3 green, TEST SUCCEEDED** (logged-day readout, explicit gap state, severity-0 ≠ gap)
- Grep gates: `uitest-select-day` present in AppDependencies (non-comment), `xDomain.contains` present in InsightTrendChart (non-comment); `XCUICoordinate|normalizedOffset|sleep(` in ChartScrubUITests → **0**
- `git log --oneline v1.0..HEAD -- Sources/EndoApp/Features/Report/ReportPDFRenderer.swift` — **EMPTY (zero commits since v1.0)**
- `git status --porcelain -- …/ReportPDFRenderer.swift` — **EMPTY (zero working-tree changes)**
- `xcodebuild test … -only-testing:EndoAppUITests/ReportFlowUITests -only-testing:EndoAppUITests/InsightsDashboardUITests` — **8/8 green, TEST SUCCEEDED** (2 report-flow + 6 dashboard)
- `swift test --package-path EndoInsights` — **124 tests / 14 suites green**
- Full `-only-testing:EndoAppTests` deliberately NOT run (known >15-min stall in this environment — STATE.md 08-03/08-04; targeted suites are the established pattern)

## Manual Checkpoints Surfaced to the Phase Verification Gate (/gsd-verify-work)

Per 13-VALIDATION Manual-Only rows — device/visual-only by nature, NOT blocking this plan:

1. **Device haptic (CHART-03):** scrub across days on a physical iPhone — one selection tick per day boundary only (no tick on initial touch or release); Simulator no-ops are expected.
2. **Scroll-vs-scrub feel + tap/release lifecycle (CHART-01/03, OS-volatile):** on an iPhone 16 — tooltip follows the finger, a vertical swipe still scrolls the List, release dismisses the tooltip. Also a recurring release-checklist item per STATE.md Blockers.
3. **PDF visual compare (PDF guard):** export a report and compare the chart pages against a v1.0 export — visually identical (the zero-diff + ReportFlowUITests gates passed; this is the eyes-on confirmation).
4. **VoiceOver + Audio Graph (CHART-04):** per-mark swipe-through reads dates/severities; the Audio Graph plays discrete tones with gaps omitted (never zeros); the selection tooltip readout is reachable as one quiet element.

## Next Phase Readiness

- The `-uitest-select-day` seam, `-uitest-seed-scrub` fixture, and `\.uiTestScrubDay` EnvironmentKey are the reusable deterministic-E2E foundation Phases 15–16 consume unchanged.
- Phase 13 is now 3/3 plans complete — ready for `/gsd-verify-work` (four manual checkpoints listed above).
- Pattern carried forward for Phases 15–16: annotation-chrome a11y must ride an overlay proxy with a `.contain` shield — in-annotation a11y modifiers are silently dropped by Swift Charts.

## Self-Check: PASSED

- All 4 created/modified files exist on disk (incl. this SUMMARY)
- All three task commits (`85dbf9f`, `ec79db0`, `de87884`) present in git log
- Key links verified: `uiTestScrubDay` in AppDependencies + EndoAppApp + InsightTrendChart; `chart.tooltip` ENDSWITH queries in ChartScrubUITests; ReportPDFRenderer zero-diff

---
*Phase: 13-trend-chart-scrubbing*
*Completed: 2026-06-10*
