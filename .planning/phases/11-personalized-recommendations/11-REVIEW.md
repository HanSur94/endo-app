---
phase: 11-personalized-recommendations
reviewed: 2026-06-10T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift
  - EndoInsights/Sources/EndoInsights/CorrelationEngine.swift
  - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
  - EndoInsights/Sources/EndoInsights/HonestyGate.swift
  - EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift
  - Sources/EndoApp/Features/Insights/RecommendationsCopy.swift
  - Sources/EndoApp/Content/ClaimsPolicy.swift
  - Sources/EndoApp/Features/Insights/InsightsView.swift
  - Sources/EndoApp/App/AppDependencies.swift
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-06-10
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Phase 11 re-frames already-gated `CorrelationEngine` survivors into non-prescriptive
recommendation prompts. The core honesty machinery is sound and I could not falsify the
load-bearing safety claims: the engine is pure (no clock/RNG/`UUID()`/force-unwraps), the
compose-not-rederive contract holds (`scoredCandidates` and `insights` share the single
`scoredSurvivors` pipeline, so a finding exists iff the correlation insight exists),
direction is recovered from the SIGNED effect (`Arm.direction`) not from parsed copy, the
suppressed/sparse/flood paths all yield zero recommendations, `topN<=3` is enforced after
collapse, the dismissal `signalKey` is window-independent and value-independent, and the
`-uitest-seed-recommendations` seam is correctly double-gated behind `-uitest-reset`.
The emitted copy (fixed strings plus every production-interpolated factor/symptom label)
scans clean against both the causal and imperative forbidden lists, and the `HonestyGate`
is a genuine fail-closed backstop that would WITHHOLD (not just assert) any copy that ever
collided — so honest absence is preserved even on a future label drift.

However I found one real correctness bug in the per-symptom collapse for `bleeding`
targets (the grouping key is wrong and contradicts its own comment), and three duplication
drift-risks that the codebase's own established "drift guard" convention
(`DaySnapshotMappingTests`) is NOT applied to: the two copy mirrors and the two imperative
lists are duplicated across the app/package boundary with only manual sync and no test.

## Warnings

### WR-01: `dominantPerSymptom` grouping key is wrong for `bleeding` targets — under-collapses and contradicts its own comment

**File:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift:93-99`

**Issue:** `targetKey(_:)` extracts the per-symptom grouping key with
`f.signalKey.split(separator: ".").suffix(2).joined(separator: ".")`. The comment (line 98)
asserts this yields `"symptom.pelvicPain"` **or** `"bleeding"`. It does not. Every factor
key is exactly two dot-components (`food.dairy`, `sleep.poor`, `prn.taken`,
`cyclePhase.luteal`, `activity.present`, …), so:

- A symptom target signalKey `correlation.food.dairy.symptom.pelvicPain` (5 parts) →
  `suffix(2)` = `symptom.pelvicPain` ✓ (collapses correctly across factors).
- A bleeding target signalKey `correlation.food.dairy.bleeding` (4 parts) →
  `suffix(2)` = `dairy.bleeding`, and `correlation.sleep.poor.bleeding` →
  `poor.bleeding`. The trailing **factor** fragment is pulled into the key, so two
  different factors that both correlate with bleeding land in SEPARATE groups and are
  NOT collapsed.

Result: the documented "one recommendation per symptom target" invariant (D-02) holds for
symptom targets but silently breaks for `bleeding` — multiple bleeding recommendations from
different factors can co-exist, and the comment claiming the key is `"bleeding"` is false.
The `.prefix(topN)` cap still bounds the total at 3, so this is not a flood, but it is a
real behavioral inconsistency between symptom and bleeding targets and a wrong invariant.
It is untested (`RecommendationsEngineTests` has no multi-target / bleeding collapse case;
`topNCap_isRespected` even notes no fixture produces >3 survivors).

**Fix:** Strip the leading `correlation.<factor.key>.` deterministically instead of taking a
fixed suffix. Since every factor key is exactly two components, drop the first three
components (`correlation` + 2 factor parts):
```swift
func targetKey(_ f: CorrelationEngine.CorrelationFinding) -> String {
    let parts = f.signalKey.split(separator: ".")
    guard parts.count >= 4 else { return f.signalKey }   // correlation.<a>.<b>.<target…>
    return parts.dropFirst(3).joined(separator: ".")      // "symptom.pelvicPain" or "bleeding"
}
```
Add a fixture exercising two factors → same bleeding target and assert a single collapsed
recommendation, plus two factors → same symptom target.

### WR-02: Recommendation copy is duplicated app↔package with no byte-identity drift guard

**File:** `Sources/EndoApp/Features/Insights/RecommendationsCopy.swift:38-52` and
`EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift:173-184`

**Issue:** The curated recommendation templates are duplicated in two files — the audited
app surface (`RecommendationsCopy`) and the package-internal mirror (`RecommendationCopy`)
— and both headers state they "MUST stay BYTE-IDENTICAL". They are currently identical, but
nothing enforces it. The app's `CopyAuditTests` only scans `RecommendationsCopy.auditableStrings`;
the package's `HonestyGate` zero-forbidden-terms test only scans the package copy. A change
to one file (e.g. softening the aggravating wording in the audited app surface) would leave
the OTHER file's string unaudited and shipping unchanged — exactly the "silent drift" the
review brief asks about. The codebase already established the fix-pattern for this class of
risk: `DaySnapshotMappingTests` lines 251-289 cross-check app↔package enum raw values with
explicit "drifted between app and package" guards.

**Fix:** Add a drift-guard test in the app test target (which imports both `EndoApp` and
`EndoInsights`). The package `RecommendationCopy` is `internal`, but the engine OUTPUT is
observable: assert that `RecommendationsEngine`'s emitted headline/detail for a known fixture
equals the app-side `RecommendationsCopy.beneficialHeadline(...)` /
`aggravatingHeadline(...)` / `detail` for the same interpolation values. That pins the two
template strings byte-for-byte through the only surface that can see both.

### WR-03: Imperative forbidden-list duplicated app↔package with no parity guard

**File:** `Sources/EndoApp/Content/ClaimsPolicy.swift:63-73` and
`EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift:74-84`

**Issue:** `ClaimsPolicy.forbiddenImperatives` (app) and `ForbiddenTerms.imperative`
(package) are the two halves of the Phase-11 imperative scan and are documented as a mirror
that "MUST stay in sync". They are currently identical (9 entries each:
`take/stop/avoid/start/should/must/use/try/cut`), but there is no test asserting parity.
If one list gains a new directive verb and the other does not, the two enforcement surfaces
(the app `CopyAuditTests` imperative scan vs. the package `HonestyGate` runtime scan) silently
diverge — a recommendation could pass the package gate while the app audit would have caught
it, or vice-versa. This is the same drift class as WR-02 and the same established convention
(`DaySnapshotMappingTests` enum-parity guards) applies.

**Fix:** Add an app-test parity assertion. The package list is `internal`, so expose it to
`@testable import EndoInsights` (the app test target already imports `EndoInsights`; switch
to `@testable` or add a test-visible accessor) and assert
`ClaimsPolicy.forbiddenImperatives == ForbiddenTerms.imperative`, mirroring the existing
"…drifted between app and package" guards. Do the same for the causal list vs.
`ClaimsPolicy.forbiddenVerbs` if a parity claim is intended there too (note: those two are
intentionally NOT identical — the package list extends with connectives — so guard a subset
relation, not equality).

### WR-04: Templated recommendation copy interpolates domain labels that the audit only spot-checks

**File:** `Sources/EndoApp/Features/Insights/RecommendationsCopy.swift:58-65` and
`EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift:131-133`

**Issue:** Both headlines interpolate `factor` (and the aggravating one, `symptom`) from the
full domain label set (`Factor.displayLabel`, `CyclePhaseMapper.displayName`).
`auditableStrings` only covers three representative interpolations
(`"gentle movement"`, `"pelvic pain"`, `"poor sleep"`), so the audit does not prove the
ACTUAL production labels are clean. I scanned all 24 factor labels and 11 symptom labels
against the causal+imperative lists and every current combination is clean, so this is not a
live violation — and the `HonestyGate` fail-closed drop is a real backstop. But the audit
gives false coverage confidence: a future label (e.g. a new factor whose label contains a
forbidden substring like "start"/"use"/"her") would pass `CopyAuditTests` untouched and only
be caught by silently DROPPING the recommendation at runtime (honest absence, but an invisible
loss of a valid recommendation).

**Fix:** Have `auditableStrings` (or a dedicated test) enumerate the real label sources —
e.g. emit `beneficialHeadline(factor:)` / `aggravatingHeadline(symptom:factor:)` for every
`Factor.displayLabel` and every `CyclePhaseMapper.displayName(_:)` — and assert each scans
clean. This turns the spot-check into exhaustive proof over the actual interpolation domain.

## Info

### IN-01: `CorrelationFinding.effectMagnitude` is carried but never consumed by the recommendation engine

**File:** `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift:370` /
`EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`

**Issue:** `RecommendationsEngine` reads `signalKey`, `factorLabel`, `targetLabel`,
`confidence`, `pairedDays`, and `direction` from each `CorrelationFinding`, but never
`effectMagnitude` (ordering is inherited from the correlation survivor order, not re-derived).
The field is harmless and keeps parity with the emitted Insight, but it reads as a hook that
implies the recommendation engine ranks on effect size when it does not.

**Fix:** Either drop `effectMagnitude` from `CorrelationFinding` (the engine already trusts
the incoming deterministic order) or add a one-line comment that it is carried for
parity/diagnostics only and is intentionally not used for recommendation ordering.

### IN-02: Bleeding aggravating framing reads slightly oddly ("more intense bleeding")

**File:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift:133,180`

**Issue:** For a `bleeding` presence target in the aggravating direction the headline renders
`"Your bleeding tends to be more intense on days with <factor> — something you might explore."`
"more intense bleeding" is non-causal and non-prescriptive (compliant), but "intense" is a
severity word and bleeding here is a presence (logged-more-often) signal, so the wording is a
mild semantic mismatch versus the symptom-severity case it was written for.

**Fix:** Optional copy polish — consider a presence-specific phrasing (e.g. "tends to come up
more often"), keeping it curated-clean against both forbidden lists. Not a blocker; the copy
is honest as-is.

### IN-03: `topNCap_isRespected` asserts an upper bound no fixture actually reaches

**File:** `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift:116-122`

**Issue:** The test comment concedes "No fixture is GUARANTEED to produce >3 strong
correlations here", so `recs.count <= 3` passes trivially with a single recommendation. The
deterministic top-N ordering, the per-symptom collapse, and the WR-01 bleeding-grouping path
are therefore unexercised.

**Fix:** Add a denser fixture producing >3 surviving correlations across multiple symptom
targets (and at least two factors → the same bleeding target) so the cap, the stable-tiebreak
ordering, and the collapse are actually proven.

---

_Reviewed: 2026-06-10_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
