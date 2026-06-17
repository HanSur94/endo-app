---
phase: 13-trend-chart-scrubbing
reviewed: 2026-06-15T08:43:57Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift
  - EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/App/EndoAppApp.swift
  - Sources/EndoApp/Features/Insights/InsightCardView.swift
  - Sources/EndoApp/Features/Insights/InsightTrendChart.swift
  - Sources/EndoApp/Features/Insights/InsightsCopy.swift
  - Tests/EndoAppTests/CopyAuditTests.swift
  - Tests/EndoAppUITests/ChartScrubUITests.swift
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: fixed
fixed:
  fixed_at: 2026-06-15
  fix_scope: all
  findings_fixed: 5
  findings_deferred: 0
  commits:
    WR-01: 215533d
    WR-02: 3d8a369
    IN-01: 60809ae
    IN-02: 902a622
    IN-03: 06fb8ec
  verification:
    swift_test_endoinsights: pass (124 tests, 14 suites)
    chartscrub_uitests: pass (3/3, a11y value assertions unaffected by WR-01)
    copyaudit_tests: pass (32/32)
    insightsdashboard_uitests: pass when runner launches; host-level simulator
      runner install/launch flakiness on this machine (signal kill/term, Mach -308,
      FBSApplicationLibrary nil) — NOT a code regression. Every test passed in at
      least one run; no assertion failure observed. These tests do not assert on the
      per-mark accessibilityValue casing changed by IN-02.
    pdf_path_untouched: confirmed (ReportPDFRenderer.swift empty in v1.0..HEAD, zero
      working-tree diff)
---

# Phase 13: Code Review Report

**Reviewed:** 2026-06-15T08:43:57Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** findings_found

## Summary

Phase 13 adds an additive chart-scrubbing selection layer to `InsightTrendChart`, a
pure exact-day resolver (`ChartScrubSelection`), two new audited copy strings, a
DEBUG-only deterministic UI-test seam, and an E2E suite. I reviewed against the six
locked-invariant concerns called out for this phase (honesty correctness, PDF
protection, test-seam safety, ClaimsPolicy, Swift 6 concurrency, the a11y overlay
proxy) and traced the changes cross-module into `ChartSeriesGrouping`,
`ReportPDFRenderer`, `TrendsEngine`, and `Repository`.

The implementation is careful and the locked invariants hold:

- **Honesty choke-point is intact.** `ChartScrubSelection.resolve` is the only
  raw-date→value path; it never interpolates and never snaps — `points.first { isDate(_:inSameDayAs:) }`
  returns `nil` for a gap day and a real point (value 0) for a logged severity-0 day.
  No `proxy.value(atX:)` / nearest-neighbor math exists anywhere in the selection
  layer. The highlight `PointMark` uses `sel.day` (startOfDay), which aligns exactly
  with the data marks because `DaySnapshot.day` is startOfDay-bucketed in
  `Repository.daySnapshots()` and `TrendsEngine.severityPoints` carries `snap.day`
  through unchanged — verified end-to-end.
- **PDF protection holds by construction.** `allowsSelection` defaults `false` and
  `selectionA11yIDRoot` defaults `nil`; `ReportPDFRenderer.drawChart` (line 309)
  constructs a fresh chart omitting both, so `chartXSelection`, the haptic
  `onChange`, the `.onAppear` seam, and the overlay proxy are never attached on the
  PDF path. `ReportPDFRenderer` is unmodified by this phase.
- **Test seam is production-inert.** `-uitest-select-day` parsing is gated by both
  `#if DEBUG` AND the `uiTestReset` runtime pairing (AppDependencies 123-134), with
  bounds-checked arg access and a strict POSIX parse that yields `nil` (never a
  crash) on garbage input. `\.uiTestScrubDay` defaults to `nil`.
- **ClaimsPolicy passes.** `scrubNotLogged` ("Not logged") and `scrubSeverity(_:)`
  ("Severity n of 10") are registered in `auditableStrings` and pinned verbatim in
  `CopyAuditTests`; neither hosts a forbidden verb / gendered token, and the
  digit-in-`scrubSeverity` is correctly scoped out of the confidence-only bare-digit scan.
- **Swift 6 concurrency is sound.** `@preconcurrency AXChartDescriptorRepresentable`
  is the compiler-suggested resolution for a MainActor View; `@State` haptics +
  ephemeral `@State rawSelectedDate` introduce no retain cycle (no closures capture
  `self` strongly).

Findings are limited to two robustness Warnings and three Info-level items. No
Critical issues.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Selection-tooltip a11y proxy element exposes a value with no accessibility label

**File:** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift:286-309`
**Issue:** `selectionAccessibilityProxy` builds the tooltip's accessibility element
from a `Color.clear` view carrying only `.accessibilityValue(...)` (plus an identifier),
with no `.accessibilityLabel`. XCUITest queries by identifier and reads `.value`, so
the E2E passes — but for a live VoiceOver user, an element with an empty label and a
value-only payload reads awkwardly (VoiceOver announces "label, value"; an empty label
produces a bare value or, on some focus paths, is skipped). The phase's own goal
(CHART-04: "expose the tooltip to VoiceOver") is only half-met — XCUITest sees it, but
the VoiceOver readout quality is unverified by any automated check and depends on the
manual phase-gate. The full "{date} — Severity n of 10" string is packed into the
*value* slot rather than being split label/value, which is not how VoiceOver expects a
labeled data readout.
**Fix:** Give the proxy element an explicit label so the readout is well-formed, e.g.:
```swift
Color.clear
    .frame(width: 8, height: 8)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(sel.day.formatted(.dateTime.month().day())))
    .accessibilityValue(Text(valueOnlyReadout(sel)))   // "Severity n of 10" / "Not logged"
    .scrubAccessibilityID(selectionA11yIDRoot.map { "\($0).tooltip" })
```
If the combined-string `.value` form must stay (to keep the existing E2E `value`
assertions byte-identical), add at minimum an `.accessibilityLabel` so VoiceOver does
not focus a label-less element. Confirm the VoiceOver readout in the manual checkpoint.

### WR-02: `.onAppear` seam re-fires on row recycling and can silently drop the deterministic selection

**File:** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift:140-144`
**Issue:** The seam sets `rawSelectedDate = day` only inside `.onAppear` and only when
`xDomain.contains(day)`. `xDomain` is derived from `asOf = .now` evaluated lazily at
body time. Inside a `List`, rows are lazily realized and `.onAppear`/`.onDisappear`
fire repeatedly as the row scrolls in and out of the realized window. On a *logged
severity-0* card and the *gap* card the selection survives (state persists), but the
guard means: (a) if the card first appears while `day` is momentarily out of an
edge-of-window `xDomain` (e.g. a 7-day window whose `.now` advanced past midnight
between launch-arg computation and first appear), the assignment is skipped and never
retried, leaving the chrome unrendered; and (b) the E2E's `revealSeverityTooltip`
scroll loop (ChartScrubUITests 102-112) relies on the tooltip persisting across the
swipe-driven recycle — which works only because `rawSelectedDate` is never reset, an
implicit dependency that is not asserted. This is the same midnight-race class the code
acknowledges elsewhere, but here a miss yields a *silently empty* selection rather than
a test failure.
**Fix:** Make the seam idempotent and recompute-safe by also keying it off the value
and guarding the already-set case, and prefer `.task(id:)` over `.onAppear` so it is
not tied to view re-appearance:
```swift
.task(id: uiTestScrubDay) {
    if let day = uiTestScrubDay, xDomain.contains(day), rawSelectedDate == nil {
        rawSelectedDate = day
    }
}
```
At minimum, document and (ideally) assert in the E2E that the tooltip must survive the
reveal-scroll, so a future `rawSelectedDate`-reset regression is caught.

## Info

### IN-01: Resolver comments reference a type that does not exist in the EndoInsights package

**File:** `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift:12,14`
**Issue:** The header doc references "the `ChartSeriesGrouping` playbook," but
`ChartSeriesGrouping` lives in the app target
(`Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift`), not in the
EndoInsights package this file belongs to. It is only a doc reference (no compile
dependency), but it implies a package-level relationship that does not exist and could
mislead a future maintainer into thinking the package depends on app-target code.
**Fix:** Reword to "the v1.0 grouping-helper playbook (`ChartSeriesGrouping`, app
target)" or drop the cross-target name to avoid implying a package dependency.

### IN-02: Per-mark accessibility value and tooltip use diverging "severity" capitalization

**File:** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift:181,193` vs `InsightsCopy.swift:117`
**Issue:** The per-mark `.accessibilityValue` emits lowercase `"severity \(n) of 10"`
(lines 181, 193), while the tooltip readout and `scrubSeverity` emit capitalized
`"Severity \(n) of 10"`. A VoiceOver user scanning the Audio-Graph/per-mark path hears
one casing and the scrub tooltip another for the identical datum. The `scrubSeverity`
doc comment even claims it "matches the shipped per-mark accessibilityValue vocabulary"
— it matches the words but not the casing. Purely cosmetic (no audit/honesty impact),
but it is an inconsistency the comment explicitly asserts is absent.
**Fix:** Route the per-mark value through `InsightsCopy.scrubSeverity(...)` so there is
one source of truth, or align the casing in both places.

### IN-03: `scrubSeverity` rounds before display, masking the float→Int collapse for any future fractional value

**File:** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift:262,316`
**Issue:** `InsightsCopy.scrubSeverity(Int(point.value.rounded()))` rounds the `Double`
value to an `Int`. Today every severity point is an integer 0...10 (TrendsEngine
emits `Double(sev)` and `0/1` frequency), so this is lossless. But if a future series
ever carries a fractional value (e.g. a smoothed/averaged trend), the tooltip would
silently round (e.g. 6.5 → "Severity 7 of 10") with no indication of approximation —
a soft honesty risk in a codebase whose whole premise is "never fabricate a value."
Not a current bug; flagging because the rounding is invisible at the call site.
**Fix:** No change needed while the contract guarantees integer severities. If
fractional values become possible, surface them honestly (e.g. one decimal) rather
than rounding, or assert the integer contract at the boundary.

---

_Reviewed: 2026-06-15T08:43:57Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
