---
phase: 16-scatter-view-correlation-deep-link
verified: 2026-06-17T01:25:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Scatter readability + count-encoding legibility on device (SCAT-01/02)"
    expected: "On iPhone 16, pick two factors: denser day-pairs read as larger dots; no overlap confusion; the count is legible as size + the worded readout, never hue."
    why_human: "Visual density perception beyond a11y-id assertions — automation proves the count is encoded and worded, not that a sighted user can DISTINGUISH the densities at a glance."
  - test: "Descriptive-only tone / no implied causation (SCAT-03)"
    expected: "The caption + axis-title row read as 'raw days where both were logged', never 'X causes/affects Y'. No fitted line, no number that reads as a strength claim."
    why_human: "Subjective tone review — the substring scans prove forbidden tokens are absent, but 'reads as causal' is a human judgement."
  - test: "Colorblind-safe / not-the-sole-signal distinction (SCAT-01)"
    expected: "Under a grayscale filter, dots remain distinguishable by size/position and the count is surfaced in the worded readout, not hue; the scatter is never the sole carrier of meaning."
    why_human: "Visual perception under a color-vision filter — beyond programmatic reach."
  - test: "testGeneratePDF_fromPopulatedRange_showsPreview re-run on a non-degraded host/CI"
    expected: "The PDF preview test passes on a stable host (it could not reach a clean result here after ~15 attempts incl. a sim erase — a post-midnight host runner-crash flake, never a code assertion)."
    why_human: "Host-runner instability, NOT a Phase-16 code path — the PDF render path is provably zero-diff since v1.0 (git log v1.0..HEAD -- ReportPDFRenderer.swift is EMPTY). Carried as a flaky-host re-run item, not a phase gap."
---

# Phase 16: Scatter View & Correlation Deep-Link Verification Report

**Phase Goal:** A descriptive-only factor-vs-factor scatter where each point is one both-logged day, reachable directly from a "Possible association" card (SCAT-01..04).
**Verified:** 2026-06-17T01:25:00Z
**Status:** human_needed
**Re-verification:** No — initial verification (post-REVIEW: all 6 findings fixed)

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP Success Criterion) | Status | Evidence |
|---|------------------------------------|--------|----------|
| 1 | SCAT-01 — switch Explore to a scatter view, two quantitative axes, each point = one both-logged day | ✓ VERIFIED | `ExploreView` segmented `Picker($mode)` `.lanes`/`.scatter` (ll. 94-103); `scatterContent` renders `ScatterAxisPicker` + `ScatterPlotView` over `ScatterPairing.pairs(snapshots,x:,y:)` cached in `scatterPrepared` @State; axis options = `ExploreSelection.offered(suppressed:).filter{ $0.scaleKind.isQuantitative }` (ScatterAxisPicker l.42) so presence series and suppressed-bleeding are never axes. UI: `testScatterModeRendersWithTwoAxes`, `testSuppressedUserHasNoBleedingAxis` exist + read. `testScatterCaptionIsNeutral` (which renders the plot) PASSED live (13s, clean). |
| 2 | SCAT-02 — identical day-pairs stack via count-encoding, never jitter | ✓ VERIFIED | `ScatterPlotView` renders one `PointMark` per `ScatterPairing.ScatterPoint`, `.symbolSize(by: .value("Days", point.count))` (l.102). Pure layer pre-aggregates collisions into `count`. Grep over scatter files: NO `Double.random`/`jitter`/`LinearRegression`/`rValue`/`coefficient`/`LineMark` in code (only `//` comments stating their absence). EndoInsights suite "Scatter pairing (both-logged-only; count-encoded, never jittered or imputed)" green; package 156/156. `testScatterCountEncodedDenseCell` exists + asserts "22" both-logged days (counted, not 22 fractional points). |
| 3 | SCAT-03 — descriptive-only: no fitted line/r-value, neutral caption, honest sparse state | ✓ VERIFIED | No regression/trend line / r-value / % / coefficient anywhere in code (grep clean). Neutral caption via `ExploreCopy.scatterCaption(days:)` ("Based on N days when both were logged"); sparse state `scatterContent` keys off `scatterPrepared.totalDays < minScatterDays(5)`; empty state when an axis is nil. All chrome via audited `ExploreCopy`, registered in `auditableStrings` and scanned by CopyAuditTests (54/54 green). Live: `testScatterCaptionIsNeutral` PASSED — caption contains "when both were logged", carries no %, no "r =", no "correlation". `testSparseStateWhenTooFewDays` + `testScatterEmptyStateBeforeAxes` exist + read. |
| 4 | SCAT-04 — open a prefilled scatter from a "Possible association" card | ✓ VERIFIED | `ScatterDeepLink.resolve(signalKey:suppressed:)` parses the STRUCTURAL signalKey by known suffix (never headline/detail prose), maps only stress.high/mood.low, gates bleeding under suppression, every malformed key → nil without crash, iterates `EndoInsights.SymptomType.allCases` (WR-03). `InsightCardView` shows the `.scatterLink` Button only `if let axes = scatterAxes` (typed payload). `ExploreScatterRouter` (@Observable, ephemeral, no persistence) carries `PendingScatter`; `AppGate` `TabView(selection:$selectedTab)` + `.tag`; `ExploreView.consumePendingScatter()` prefills + clears. ScatterDeepLinkTests 54/54 green. Live: `testCorrelationCardDeepLinkPrefillsScatter` PASSED (79s, clean attempt 1) — tap card → Explore in scatter mode, prefilled, plot rendered, no empty state. |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift` | Pure resolver, structural key, suppression gate | ✓ VERIFIED | 145 ll.; defensive parse by known suffix; `EndoInsights.SymptomType.allCases` (WR-03 fix, l.110); only stress.high/mood.low map; presence/sleep-quality/suppressed-bleeding → nil. |
| `Sources/EndoApp/Features/Explore/ScatterPlotView.swift` | Count-encoded PointMark, fixed domains, no stats | ✓ VERIFIED | `.symbolSize(by:count)`; 2× `SeriesNormalization.domain`; IN-02 skip-on-nil-domain (ll.92-93); markLabel via audited `ExploreCopy.scatterMarkLabel` (WR-01 fix). |
| `Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift` | Quantitative-only X/Y, suppression-filtered, no persistence | ✓ VERIFIED | options from `ExploreSelection.offered(suppressed:).filter(isQuantitative)`; value-independent ids; no @AppStorage. |
| `Sources/EndoApp/Features/Explore/ExploreView.swift` | Mode toggle + scatter branch + router read + cached cells | ✓ VERIFIED | WR-02 fix: `scatterPrepared` @State recomputed via `recomputeScatter()` on appear/onChange(scatterX/Y), read in body — not recomputed in body. IN-01 fix: `if let x,y` (no force-unwrap). `consumePendingScatter()` prefill+clear. |
| `Sources/EndoApp/App/ExploreScatterRouter.swift` | Ephemeral @Observable router, public catalog ids | ✓ VERIFIED | `@MainActor @Observable`, `PendingScatter(x,y)`, EnvironmentKey w/ assumeIsolated default, no persistence wrapper. |
| `Sources/EndoApp/App/AppGate.swift` | TabView(selection:) + ConsentedTab + .tag | ✓ VERIFIED | `TabView(selection:$selectedTab)`, `ConsentedTab{log,insights,explore}`, per-tab `.tag`, `selectedTab` threaded into InsightsView. |
| `Sources/EndoApp/App/AppDependencies.swift` | scatter-link seed + scatter-axes seam + router | ✓ VERIFIED | `-uitest-seed-scatter-link` (stress.high→pelvicPain, DEBUG + `-uitest-reset`); `-uitest-scatter-axes` seam uses `indices.contains` (IN-03 fix, l.259); `exploreScatterRouter` constructed. |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` | Resolver-gated affordance, scoped evidence id | ✓ VERIFIED | `if let axes = scatterAxes` Button w/ typed payload; `.evidence` id scoped to inner text-only VStack (ll.194-202), `.scatterLink` on the button (l.221) — the test-id fix. |
| `Sources/EndoApp/Features/Explore/ExploreCopy.swift` | Scatter strings registered + substring-clean | ✓ VERIFIED | scatterCaption/Summary/MarkLabel/LinkLabel/Empty/Sparse all in `auditableStrings`; CopyAuditTests 54/54 green. |
| `Tests/EndoAppTests/ScatterDeepLinkTests.swift` | Resolver unit suite | ✓ VERIFIED | 18 @Tests; every mapping + nil/malformed case; qualifies `EndoInsights.SymptomType`. All green. |
| `Tests/EndoAppUITests/ScatterUITests.swift` | Seeded E2E for each SCAT | ✓ VERIFIED | 8 gesture-free a11y-id tests covering SCAT-01..04 + negatives; 2 spot-run PASSED clean. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `.correlation` card | `ScatterDeepLink.resolve` | `scatterAxes` computed property | ✓ WIRED | Affordance shown iff resolve != nil; typed axes, never copy. |
| `InsightCardView` | `ExploreScatterRouter` | `onOpenScatter(PendingScatter)` closure → `InsightsView` sets payload + selects `.explore` | ✓ WIRED | Live `testCorrelationCardDeepLinkPrefillsScatter` PASSED. |
| `ExploreScatterRouter` | `ExploreView` scatter @State | `consumePendingScatter()` on appear/onChange → mode=.scatter, prefill X/Y, clear | ✓ WIRED | Live deep-link test lands on prefilled plot. |
| `ScatterPlotView` | `ScatterPairing.pairs` | `scatterPrepared.points` cached @State | ✓ WIRED | Consumed unchanged; WR-02 caching applied. |
| both axes | `SeriesNormalization.domain` | `.chartXScale`/`.chartYScale` | ✓ WIRED | Fixed real-unit domains, IN-02 skip-on-nil. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ScatterPlotView` | `points: [ScatterPoint]` | `recomputeScatter()` → `ScatterPairing.pairs(loadSnapshots(), x:, y:)` from `SwiftDataRepository.daySnapshots()` | ✓ Yes (real repository read, both-logged intersection) | ✓ FLOWING |
| `InsightCardView` scatter-link | `scatterAxes` | `ScatterDeepLink.resolve(insight.signalKey, suppressed:)` over `ExploreSeriesCatalog.all` | ✓ Yes (gated engine signalKey → catalog lookup) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| ScatterPairing/SeriesNormalization pure logic | `swift test --package-path EndoInsights` | 156/156 in 18 suites passed | ✓ PASS |
| Resolver + copy audit | `xcodebuild test -only-testing:.../ScatterDeepLinkTests -only-testing:.../CopyAuditTests` | 54/54 (Swift Testing) passed | ✓ PASS |
| Neutral caption (live UI) | `…/ScatterUITests/testScatterCaptionIsNeutral` | passed 13.0s, clean | ✓ PASS |
| Full SCAT-04 deep-link prefill (live UI) | `…/ScatterUITests/testCorrelationCardDeepLinkPrefillsScatter` | passed 79.3s, clean attempt 1 | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` declared or present for this phase (Swift app, not a migration/CLI phase). Probe step N/A.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SCAT-01 | 16-02 | Scatter view, two quantitative axes, each point = one both-logged day | ✓ SATISFIED | Truth 1; ScatterAxisPicker/ScatterPlotView/ExploreView; live render test. |
| SCAT-02 | 16-02 | Count-encoded stacking, never jitter | ✓ SATISFIED | Truth 2; `.symbolSize(by:count)`, no-jitter grep, package suite. |
| SCAT-03 | 16-01/16-02 | Descriptive-only: no fitted line/number, neutral caption, sparse state | ✓ SATISFIED | Truth 3; live `testScatterCaptionIsNeutral`, audited copy. |
| SCAT-04 | 16-01/16-03/16-04 | Prefilled scatter from a "Possible association" card | ✓ SATISFIED | Truth 4; resolver 54/54, live deep-link test passed. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (scatter source set) | — | TBD/FIXME/XXX debt markers | — | NONE — grep clean across all 10 modified files. |
| ScatterPlotView/ScatterDeepLink | comments only | "no jitter"/"no regression"/"headline/detail" token mentions | ℹ️ Info | Tokens appear ONLY in `//` documentation asserting their absence; zero code occurrences. Not stubs. |

### Human Verification Required

Four device-gated / host-flaky items carried from 16-VALIDATION's Manual-Only table and 16-04-SUMMARY (none block the goal — all code paths are proven; these are perceptual or host-environment checks):

1. **Scatter readability + count-encoding legibility on device (SCAT-01/02)** — pick two factors on iPhone 16; confirm denser day-pairs read as larger dots without overlap confusion.
2. **Descriptive-only tone / no implied causation (SCAT-03)** — the caption + axis row read as "raw days", never "X causes Y".
3. **Colorblind-safe distinction (SCAT-01)** — under grayscale, dots distinguishable by size/position; count in the worded readout, not hue.
4. **`testGeneratePDF_fromPopulatedRange_showsPreview` re-run on a stable host** — could not reach a clean result here (post-midnight host runner crash, ~15 attempts incl. sim erase); PDF render path is provably zero-diff since v1.0 (`git log v1.0..HEAD -- ReportPDFRenderer.swift` EMPTY) — a flaky-host re-run item, not a Phase-16 regression.

### Gaps Summary

No gaps. All four SCAT success criteria are delivered in code and independently re-proven: EndoInsights package 156/156, resolver + copy audit 54/54, and two ScatterUITests spot-runs (neutral caption + full cross-tab deep-link prefill) passed clean. All six REVIEW findings (WR-01 audited markLabel, WR-02 @State scatter caching, WR-03 EndoInsights.SymptomType, IN-01 if-let unwrap, IN-02 skip-on-nil-domain, IN-03 indices.contains) are verified applied at source. The honesty invariants hold structurally: count-encoding via symbolSize with no jitter/regression/r-value anywhere; fixed real-unit domains; the resolver parses the structural signalKey (never prose), gates suppression, degrades malformed keys to nil; the router is ephemeral with no persistence; the `.evidence` id is scoped off the `.scatterLink` button; the PDF path is untouched.

Status is `human_needed` (not `passed`) solely because four perceptual/host-environment items require a human/device — the automated goal is fully achieved.

---

_Verified: 2026-06-17T01:25:00Z_
_Verifier: Claude (gsd-verifier)_
