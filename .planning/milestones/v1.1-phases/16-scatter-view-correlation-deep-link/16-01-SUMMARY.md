---
phase: 16-scatter-view-correlation-deep-link
plan: 01
subsystem: ui
tags: [swift, swiftui, swift-charts, scatter, deep-link, copy-audit, claims-policy, ios]

# Dependency graph
requires:
  - phase: 14-explore-data-foundation
    provides: ExploreSeriesCatalog / ExploreSeries / ScaleKind.isQuantitative — the catalog the resolver looks ids up in
  - phase: 15-explore-screen
    provides: ExploreCopy + auditableStrings + CopyAuditTests — the audited copy choke-point the scatter chrome joins
provides:
  - "Pure ScatterDeepLink resolver: .correlation signalKey -> optional (xSeries, ySeries) quantitative-axis pair (SCAT-04 gating mechanic)"
  - "Audited ExploreCopy scatter chrome strings (mode toggle, axis placeholder, caption, summary, per-point readout, empty/sparse states, deep-link label) registered in CopyAuditTests (SCAT-03)"
  - "ScatterDeepLinkTests: every factor x target mapping + every nil/malformed case unit-proven"
affects: [16-02-scatter-plot-view, 16-03-router-pending-scatter, 16-04-card-affordance-deep-link-e2e]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Structural-signalKey resolver (parse the machine identity key by KNOWN suffix, never audited prose, never a naive split)"
    - "Copy-first: register every new ExploreCopy string in auditableStrings in the SAME change that introduces it"

key-files:
  created:
    - Sources/EndoApp/Features/Insights/ScatterDeepLink.swift
    - Tests/EndoAppTests/ScatterDeepLinkTests.swift
  modified:
    - Sources/EndoApp/Features/Explore/ExploreCopy.swift

key-decisions:
  - "Resolver maps ONLY stress.high->stress and mood.low->mood; sleep.poor + all presence factors -> nil (RESOLVED-Q1, no quality->hours bridge in v1.1)"
  - "A bleeding target under suppression resolves to nil (suppression gate carried into the deep-link, threat T-16-02)"
  - "Parse the signalKey by KNOWN suffix (.bleeding / .symptom.<known rawValue>) enumerated from SymptomType.allCases — never a naive .split('.') that would mis-segment the dotted factor.key"
  - "Qualify EndoInsights.SymptomType in the test (the app target defines a same-rawValue SymptomType; unqualified name is ambiguous under @testable import EndoApp)"

patterns-established:
  - "Pure app-target value resolver over the shipped catalog (mirrors ExploreSelection.offered): static func, default catalog param, inline suppression gate, looks ids up — never constructs ExploreSeries"
  - "Defensive structural-key parse (ASVS V5): malformed/garbage key -> nil, never a crash"

requirements-completed: []  # SCAT-03/SCAT-04 are foundations only here; the full scatter ships across 16-02..04 — left Pending per execution context

# Metrics
duration: 7min
completed: 2026-06-16
---

# Phase 16 Plan 01: Scatter Foundations (Resolver + Audited Copy) Summary

**Pure `ScatterDeepLink` resolver mapping a `.correlation` `signalKey` to an optional quantitative `(x, y)` axis pair, plus the audited `ExploreCopy` scatter chrome registered in `CopyAuditTests` — the two UI-less foundations the scatter view and deep-link depend on.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-06-15T23:10:51Z
- **Completed:** 2026-06-16T (this session)
- **Tasks:** 2
- **Files modified:** 3 (1 modified, 2 created)

## Accomplishments
- **SCAT-04 resolver:** `ScatterDeepLink.resolve(signalKey:suppressed:catalog:)` maps `correlation.<factor.key>.symptom.<raw>` / `.bleeding` to an optional pair of quantitative `ExploreSeries`, looked up by id in the shipped catalog (never constructed). Only `stress.high`/`mood.low` map; `sleep.poor`, all presence-only factors, and suppressed bleeding resolve to `nil` (affordance hidden). Malformed/garbage keys resolve to `nil` without crashing.
- **SCAT-03 audited copy:** added the scatter mode-toggle (`modeLanes`/`modeScatter`), `scatterAxisPlaceholder`, `scatterCaption(days:)`, `scatterSummary(x:y:days:)`, `scatterPointReadout(...)`, `scatterEmptyHeading`/`Body`, `scatterSparseHeading`/`Body`, and `scatterLinkLabel` to `ExploreCopy`, all registered in `auditableStrings` (with representative instances for the interpolated forms) in the same change.
- **Proven:** 14 `ScatterDeepLinkTests` (every factor×target + every nil/malformed case) and all 6 `exploreCopy_*` `CopyAuditTests` scans pass over the new strings.

## Task Commits

1. **Task 1: ExploreCopy scatter strings + CopyAuditTests registration (SCAT-03)** — `aaff78e` (feat)
2. **Task 2: ScatterDeepLink pure resolver + ScatterDeepLinkTests (SCAT-04)** — `0035b33` (feat)

_Both tasks are `tdd="true"`. Task 1's TDD gate is the pre-existing `CopyAuditTests` scans staying green over the newly-registered strings (no new test method needed — `allExploreCopy` auto-scans the array). Task 2 shipped the resolver and its full unit suite together._

## Files Created/Modified
- `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift` — pure resolver: structural `signalKey` -> optional quantitative `(x, y)` axis pair, suppression-gated, defensively parsed.
- `Tests/EndoAppTests/ScatterDeepLinkTests.swift` — 14 `@Test`s: every positive mapping, every SymptomType target, the suppression gate, all presence/sleep-quality nil cases, all malformed/garbage cases, and the catalog-lookup invariant.
- `Sources/EndoApp/Features/Explore/ExploreCopy.swift` — added the scatter chrome `static let`s + interpolated funcs and registered every one in `auditableStrings`.

## Decisions Made
- **Resolver mapping scope (RESOLVED-Q1):** only `stress.high`/`mood.low` bridge to a quantitative axis; `sleep.poor` (categorical, no honest quality->hours bridge in v1.1) and every presence-only factor resolve to `nil`. The deep-link affordance is HIDDEN rather than showing a coerced axis.
- **Parse by known suffix, not split:** the factor.key is itself dotted (`stress.high`, `food.dairy`), so the resolver detects the target by a trailing `.bleeding` or `.symptom.<known rawValue>` (enumerated from `SymptomType.allCases`) and treats the remaining prefix as the factor.key — a naive `.split(".")` would mis-segment multi-dot keys.
- **Suppression gate in the resolver:** a `bleeding` target under `suppressed` returns `nil` (threat T-16-02), mirroring the Phase-14 accessor gate so a suppressed user can never reach a bleeding axis via the deep-link.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Qualified `EndoInsights.SymptomType` in the test to resolve a name ambiguity**
- **Found during:** Task 2 (ScatterDeepLinkTests build)
- **Issue:** The app target defines its own `SymptomType` (same rawValues, `Sources/EndoApp/Domain/SymptomType.swift`). Under `@testable import EndoApp` + `import EndoInsights` the unqualified `SymptomType.allCases` was ambiguous → `Ambiguous use of 'allCases'` + two `Generic parameter 'some StringProtocol' could not be inferred` compile errors. (The resolver itself imports only EndoInsights, so it compiled clean — the ambiguity is test-only.)
- **Fix:** Qualified the iteration as `EndoInsights.SymptomType.allCases` in the catalog-parity test; the resolver and catalog key on the EndoInsights enum.
- **Files modified:** `Tests/EndoAppTests/ScatterDeepLinkTests.swift`
- **Verification:** `ScatterDeepLinkTests` build succeeds; all 14 tests pass.
- **Committed in:** `0035b33` (Task 2 commit)

**2. [Rule 1 - Bug] Rephrased two doc comments so the acceptance grep gates read clean**
- **Found during:** Task 1 (stats-token gate) and Task 2 (`.headline`/`.detail` gate)
- **Issue:** The plan's acceptance grep gates use broad regexes whose `grep -v '^#'` only excludes shell-style `#` comments, not Swift `//` comments. My honesty-documenting comments quoted the forbidden tokens (`"% impact"`, `insight.headline`/`insight.detail`) verbatim to state the code never uses them — producing false-positive grep matches with zero actual occurrences in code.
- **Fix:** Rephrased both comments to describe the forbidden tokens in prose ("percent-impact", "headline or detail copy fields") instead of embedding the literal tokens. No code/logic changed; the string literals and resolver behavior are untouched.
- **Files modified:** `Sources/EndoApp/Features/Explore/ExploreCopy.swift`, `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift`
- **Verification:** Both grep gates now return 0; both test suites still pass.
- **Committed in:** `aaff78e` (Task 1) and `0035b33` (Task 2)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 cosmetic/gate-hygiene)
**Impact on plan:** Both necessary — one to compile the test against the correct enum, one to make the honesty grep gates verifiably clean. No behavior change, no scope creep.

## Issues Encountered
- The host simulator did NOT flake this run; both suites ran clean on the first non-error build. No per-method fallback needed.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **Ready for 16-02 (scatter plot view):** the audited `ExploreCopy` scatter chrome (caption, summary, empty/sparse, mode labels) is registered and scan-clean — the view can render these strings without escaping the ClaimsPolicy scans.
- **Ready for 16-03/16-04 (router + card affordance):** `ScatterDeepLink.resolve(...)` is the proven gating mechanic — `InsightCardView` can compute it once to decide whether to show the affordance, and the router can carry the resolved typed `(xSeries, ySeries)` value.
- **Requirements:** SCAT-03/SCAT-04 left Pending (per execution context) — the full scatter ships across 16-02..04; this plan delivers only the foundations.
- **No UI, persistence, engine, or PDF changes** were made (per success criteria).

## Self-Check: PASSED

- FOUND: `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift`
- FOUND: `Tests/EndoAppTests/ScatterDeepLinkTests.swift`
- FOUND: `Sources/EndoApp/Features/Explore/ExploreCopy.swift`
- FOUND: `.planning/phases/16-scatter-view-correlation-deep-link/16-01-SUMMARY.md`
- FOUND commit: `aaff78e` (Task 1)
- FOUND commit: `0035b33` (Task 2)

---
*Phase: 16-scatter-view-correlation-deep-link*
*Completed: 2026-06-16*
