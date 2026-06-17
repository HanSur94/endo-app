---
phase: 16-scatter-view-correlation-deep-link
reviewed: 2026-06-17T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - Sources/EndoApp/Features/Insights/ScatterDeepLink.swift
  - Sources/EndoApp/Features/Explore/ScatterPlotView.swift
  - Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift
  - Sources/EndoApp/App/ExploreScatterRouter.swift
  - Sources/EndoApp/Features/Explore/ExploreView.swift
  - Sources/EndoApp/Features/Explore/ExploreCopy.swift
  - Sources/EndoApp/Features/Insights/InsightCardView.swift
  - Sources/EndoApp/Features/Insights/InsightsView.swift
  - Sources/EndoApp/App/AppGate.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/App/EndoAppApp.swift
  - Tests/EndoAppTests/ScatterDeepLinkTests.swift
  - Tests/EndoAppUITests/ScatterUITests.swift
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: fixed
fix_commits:
  WR-01: c6880f2  # route scatter markLabel through audited ExploreCopy.scatterMarkLabel
  WR-02: 6f5b884  # cache scatter cells in @State (also resolves IN-01)
  WR-03: cc08959  # qualify resolver to EndoInsights.SymptomType.allCases
  IN-01: 6f5b884  # drop force-unwraps via if let x,y (folded into WR-02 rewrite)
  IN-02: cfddbdd  # skip scatter chart on nil domain instead of guessing 0...10
  IN-03: d5c6f39  # align scatter-axes seam bounds-check with sibling idiom
fixed_at: 2026-06-17
---

# Phase 16: Code Review Report

**Reviewed:** 2026-06-17
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Reviewed the Phase 16 Scatter View + correlation deep-link work end to end, cross-referencing the
consumed `EndoInsights` layers (`ScatterPairing`, `ExploreSeries`/`ExploreSeriesCatalog`,
`SeriesNormalization`), the `ClaimsPolicy`/`ForbiddenTerms` substring scans, and the app/package
`SymptomType` drift guard.

The locked honesty invariants hold. The scatter renders `ScatterPairing.pairs` verbatim with
`symbolSize(by:)` count-encoding and a single accent hue — no jitter, no `Double.random`, no fitted
line / regression / r-value / coefficient anywhere. Both axes use `SeriesNormalization.domain(for:)`
fixed real-unit ranges, never a data-driven min/max; mood (1…5) and stress (0…10) are repository-clamped
to their domains, so no logged value clips off-axis and a logged 0 stays a real point. The deep-link
resolver parses the structural `signalKey` by known suffix (never a naive `.split`), never reads
headline/detail prose, gates bleeding under suppression, looks both ids up in the catalog, enforces
`.isQuantitative` on both, and degrades every malformed key to `nil` without a trap. The router is an
ephemeral `@MainActor @Observable` carrying only public catalog ids with no persistence wrapper; the
`-uitest-*` seams are DEBUG + `-uitest-reset`-paired and garbage axis ids resolve to no preset without
crashing. The new `ExploreCopy` scatter strings are registered in `auditableStrings` and pass the
gendered-token / forbidden-verb / imperative / alarmist / numeric-precision substring scans I re-ran by
hand (`"compared with"`, `"when both were logged"`, `"missing a factor"`, `"a new one"` are all clean).
The `.evidence` id is correctly scoped to the text-only inner `VStack` so it no longer masks the
`.scatterLink` button id.

No blockers. Three warnings concern an un-audited inline copy string that duplicates (and orphans) an
audited helper, a per-render recompute that breaks the locked caching discipline, and a resolver/test
enum mismatch that leaves the production code path's enum indirectly guarded rather than directly tested.

## Warnings

### WR-01: `markLabel` builds an un-audited inline copy string and orphans the audited `scatterPointReadout`

**File:** `Sources/EndoApp/Features/Explore/ScatterPlotView.swift:124-128`
**Issue:** `ExploreCopy.scatterPointReadout(days:xName:xReadout:yName:yReadout:)` exists, is registered in
`ExploreCopy.auditableStrings` (`ExploreCopy.swift:172`), and is scanned by `CopyAuditTests`. But it is
**never called** — `grep` confirms the only references are its definition and its audit registration.
Instead, `ScatterPlotView.markLabel` assembles an equivalent per-mark a11y string inline:
`"\(xSeries.displayName) \(xReadout), \(ySeries.displayName) \(yReadout)"`, embedding the `", "` and `" "`
connectives as raw string literals in the view. This violates the locked "no string literals in the
scatter views; copy via ExploreCopy only (except a bare X/Y axis letter)" contract: the actually-rendered
VoiceOver string never passes through the audited surface, while the audited function it should use is
dead code. The two also disagree on shape (the audited form leads with the day count
`"\(days) days — …"`; the inline form omits the count and carries it only via `.accessibilityValue`), so
the audit is proving a string the app does not render.
**Fix:** Route `markLabel` through the audited helper and drop the inline literals — either call
`ExploreCopy.scatterPointReadout(...)` (and move the count into the label to match), or, if the count
must stay in `.accessibilityValue`, add an audited 2-readout `ExploreCopy.scatterMarkLabel(xName:xReadout:yName:yReadout:)`
and register it. Do not keep an audited function that nothing renders next to an un-audited string that does.

### WR-02: Scatter pairs are recomputed in `body` on every render, breaking the locked Pitfall-4 caching discipline

**File:** `Sources/EndoApp/Features/Explore/ExploreView.swift:186-189`
**Issue:** The file header and the lanes path enforce a LOCKED invariant: the prepared data is "computed
ONCE per selection/window/snapshots change into a cached `@State` value — NOT in `body`." The scatter
branch breaks this — `scatterPoints` is an immediately-invoked closure inside the `@ViewBuilder`
`scatterContent`, so `ScatterPairing.pairs(snapshots, x:, y:, calendar:)` re-runs on every body
evaluation of the scatter branch (any `@State`/environment change while in scatter mode, e.g. the
confidence popover, reduce-motion, or an unrelated observable tick). It is recomputed a second time
inside the same property for the `reduce` sparse check. This is not a correctness defect (the scatter has
no scrub, and the result is deterministic), but it diverges from the locked discipline the rest of the
screen advertises and is the kind of in-`body` work the invariant was written to forbid.
**Fix:** Cache the scatter cells the same way the lanes are cached — recompute `scatterPoints` into a
`@State` value from `.onAppear` / `.onChange(of: scatterX)` / `.onChange(of: scatterY)` /
`.onChange(of: snapshots)` (mirroring `recompute()`), and read the cached value (plus its precomputed
count-sum) in `body`. At minimum, compute the pairs once and reuse them for both the sparse check and the
plot rather than letting the closure run per render.

### WR-03: The resolver iterates the app-target `SymptomType`; its unit test iterates `EndoInsights.SymptomType`

**File:** `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift:105` (and `Tests/EndoAppTests/ScatterDeepLinkTests.swift:63`)
**Issue:** `ScatterDeepLink.splitFactorAndTarget` iterates unqualified `SymptomType.allCases`. Because the
file lives in the app target and the app target defines its own `SymptomType`
(`Sources/EndoApp/Domain/SymptomType.swift`), same-module name resolution binds this to
**`EndoApp.SymptomType`** — not the `EndoInsights.SymptomType` that `ExploreSeriesCatalog` is built from
(catalog ids are `symptom.<EndoInsights rawValue>.severity`). The resolver therefore matches the suffix
against one enum's rawValues and then looks up an id derived from the *other* enum's rawValues. This works
today only because `DaySnapshotMappingTests.swift:257-259` asserts the two rawValue sets are equal — but
that coupling is invisible at the resolver and the catalog. Worse, `ScatterDeepLinkTests` deliberately
qualifies `EndoInsights.SymptomType` (line 63, with a comment explaining the ambiguity), so the
catalog-parity test exercises a *different* enum than the production resolver iterates — the actual
production code path's enum is never directly covered by the suite. A future case added to one enum but
not the other would silently drop (or mis-resolve) that symptom's deep-link, caught only by the unrelated
mapping test.
**Fix:** Qualify the resolver to the same source of truth as the catalog:
`for type in EndoInsights.SymptomType.allCases { … }`. This makes the parse and the catalog id derive
from one enum, and makes `ScatterDeepLinkTests`' qualified iteration cover the real path.

## Info

### IN-01: Force-unwraps `scatterX!` / `scatterY!` in the plot branch

**File:** `Sources/EndoApp/Features/Explore/ExploreView.swift:206`
**Issue:** `ScatterPlotView(points: scatterPoints, xSeries: scatterX!, ySeries: scatterY!)` force-unwraps
both axes. The branch is guarded by `!(scatterX == nil || scatterY == nil)`, so it is safe today, but a
force-unwrap in a must-not-crash health app is a fragile pattern if the guard is ever refactored.
**Fix:** Bind once with `if let x = scatterX, let y = scatterY` (reused for both the `pairs` call and the
plot) instead of re-testing for nil and then force-unwrapping.

### IN-02: Inert `?? 0...10` domain fallback would mis-domain a non-0…10 axis if it ever fired

**File:** `Sources/EndoApp/Features/Explore/ScatterPlotView.swift:100-101`
**Issue:** `SeriesNormalization.domain(for:)` only returns `nil` for `.presenceEvent`, which the
quantitative-only axis filter already excludes, so the `?? 0...10` never fires (the comment says as much).
But the fallback constant `0...10` is the *wrong* domain for a `.ordinal1to5` (mood) or `.ordinalBleeding`
axis — if a future routing bug ever delivered a nil-domain axis here, the plot would silently render over
a fabricated 0…10 axis rather than failing visibly.
**Fix:** Since the case is unreachable, prefer rendering nothing (or an honest empty state) over a guessed
domain — e.g. guard `domain(for:)` non-nil and otherwise skip the chart — so a future regression surfaces
instead of plotting on an invented scale.

### IN-03: `-uitest-scatter-axes` parse duplicates the bounds-check idiom inconsistently with the sibling seam

**File:** `Sources/EndoApp/App/AppDependencies.swift:256-267`
**Issue:** The new scatter-axes seam bounds-checks with `axesIndex + 1 < arguments.count`, while the
adjacent `-uitest-select-day` seam (lines 164-166) uses `arguments.indices.contains(argIndex + 1)`. Both
are correct, but the two DEBUG-only arg-parsing seams sitting feet apart use different idioms for the same
guard. Purely a consistency/readability nit (test-only code), not a defect.
**Fix:** Standardize on one idiom (`indices.contains(_:)`) across both seams.

---

_Reviewed: 2026-06-17_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
