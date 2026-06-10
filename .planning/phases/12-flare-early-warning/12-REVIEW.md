---
phase: 12-flare-early-warning
reviewed: 2026-06-10T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/FlareEngine.swift
  - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
  - EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift
  - Sources/EndoApp/Features/Insights/FlareCopy.swift
  - Sources/EndoApp/Content/ClaimsPolicy.swift
  - Sources/EndoApp/Features/Insights/InsightsView.swift
  - Sources/EndoApp/App/AppDependencies.swift
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 12: Code Review Report

**Reviewed:** 2026-06-10
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 12 is the flare early-warning — the highest honesty-stakes capability in the app.
I reviewed it adversarially, with the explicit goal of falsifying the central honesty
contract: that a warning can NEVER be fabricated under suppressed / sparse / flat /
insufficient data (the SC2 violation). I traced every emission path through the engine,
the single HonestyGate, the forbidden-term scan, and the UI render.

**The core honesty spine holds.** I could not construct a path that emits a real flare
warning without (a) a non-suppressed stream, (b) a genuine `.last7`-vs-prior rising
direction on the dominant logged symptom (delta > 0.5 band, requires a non-nil prior
mean), and (c) `>= 21` logged-severity days of evidence. The bounded combine is provably
capped at `.medium` (never `.high`, never a number/percentage), the insufficiency-notice
cusp is a clean pre-`>=21` return with no fall-through to the warning path, the suppressed
short-circuit is first, the engine is Foundation-pure (no clock/UUID/RNG/print), and the
in-card copy is clean against the alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric
union (including the "her"⊂"higher" trap, dodged via "more difficult days"). The flare UI
reuses `InsightCardView` with the identical calm `section(...)` helper, uses no
`.red`/`.orange`/`exclamationmark`/`bell`/`flame`, and touches no notification API. The
alarmist list IS wired into the runtime fail-closed gate (via `ForbiddenTerms.violation`),
so it is enforced, not merely tested. The app↔package copy and alarmist-list mirrors are
currently byte-identical / set-identical and guarded by `FlareDriftGuard` +
`FlareCopyDriftTests`.

No BLOCKER-class honesty or correctness violation was found. The findings below are
robustness / drift-exposure / clarity concerns that should be addressed but do not, on
their own, let a dishonest warning reach a user.

## Warnings

### WR-01: Drift guard does not cover the section-header mirror that "warning" depends on

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:391` and `Sources/EndoApp/Features/Insights/FlareCopy.swift:37`

**Issue:** The whole "warning" word is sanctioned in exactly ONE place — the section header
`sectionFlare = "Flare early-warning"`. The alarmist list deliberately EXCLUDES `"warning"`
(ForbiddenTerms.swift:113-117) precisely so this header is legal. `FlareDriftGuard` does
expose `packageSectionFlare` (line 391), and the app surface includes `sectionFlare` in
`auditableStrings`. However, the app's `sectionFlare` is NOT routed through the HonestyGate
(it is a SwiftUI `Section` title in `InsightsView`, never an `Insight.headline`/`detail`),
so it is covered ONLY by `CopyAuditTests` + `FlareCopyDriftTests`, not by any runtime
backstop. If a future edit changes the header to e.g. "Flare alert" on the app side only,
the drift test would catch the byte mismatch, but nothing at runtime would — and "alert"/
"alarm"/"urgent" in a header would silently ship if the drift test were ever skipped or
the header were sourced elsewhere. This is an exposure, not a live defect.

**Fix:** Keep as-is for now (the drift test is the documented guarantee), but add an
explicit assertion in `FlareCopyDriftTests` that `Set(FlareCopy.sectionFlare scan) ∩
ClaimsPolicy.forbiddenAlarmist == ∅` so the "header is the only sanctioned 'warning'"
invariant is asserted, not just the byte-equality of the two header strings. This closes
the gap where both sides drift together to an alarmist header and stay byte-identical.

### WR-02: `recentlyActiveFactorKeys` silently never matches a cyclePhase-factor correlation

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:178-201`

**Issue:** `activeFactorKeys(in:)` enumerates food / activity / sleep / stress / mood /
medication / PRN factor keys, but never emits a `cyclePhase.<phase>` key. `Factor` DOES
have a `.cyclePhase(p)` case with key `cyclePhase.<rawValue>` (Factor.swift:61), and
`CorrelationEngine` can produce an `.aggravating` finding whose factor IS a cycle phase
(signalKey `correlation.cyclePhase.luteal.…`). `factorKey(fromCorrelationSignalKey:)` will
happily parse `cyclePhase.luteal` from such a key, but `recentlyActiveFactorKeys` can never
contain it, so a genuinely-active aggravating cycle-phase correlation is silently dropped
from the active-trigger count. This is CONSERVATIVE (it only ever fails to RAISE confidence,
never fabricates one — Signal B can only add a notch on top of the required rising spine,
and the result is capped at `.medium` regardless), so it is not a BLOCKER. But it is an
undocumented silent asymmetry: the parser admits a key family the active-set never produces.

**Fix:** Either (a) document explicitly that cycle-phase correlation factors are
intentionally excluded from the active-trigger count (Signal C already handles cycle-phase
proximity, so double-counting would be wrong), or (b) filter cyclePhase keys out in
`factorKey(...)` so the asymmetry is explicit at the parse site rather than emergent from
an enumeration that quietly omits one factor family. Prefer (a) with a one-line comment at
line 169, since the current behavior is the desired one — make the intent legible so a
future maintainer does not "fix" it by adding cyclePhase keys to `activeFactorKeys` and
accidentally double-count proximity.

### WR-03: Secondary-signal proximity counts symptom *presence*, evidence floor counts *logged severity* — a subtle inconsistency

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:227` vs `144`

**Issue:** The required rising spine and the 21-day evidence floor are both measured in
*logged-severity* days (`$0.severity != nil`, lines 128/144). But the cycle-phase proximity
secondary signal counts `snap.symptoms.contains(where: { $0.type == rising.symptom })`
(line 227) — i.e. symptom *presence*, with NO `severity != nil` filter. So a day where the
dominant symptom was logged as present-without-severity counts toward "historically
higher-symptom phase" but would NOT count toward the rise or the evidence floor. The two
notions of "a day with this symptom" diverge. Because this only affects the +1 proximity
notch (capped at `.medium`), it cannot fabricate a warning — but it makes the proximity
signal subtly more permissive than the spine it is supposed to corroborate, which is the
opposite of the bias-to-withhold posture everywhere else in the engine.

**Fix:** Make line 227 consistent with the spine's severity-logged definition:
```swift
if snap.symptoms.contains(where: { $0.type == rising.symptom && $0.severity != nil }) {
    perPhaseSymptomDays[phase, default: 0] += 1
}
```
This keeps the corroborating signal at least as conservative as the spine it strengthens.

## Info

### IN-01: Dead defensive fallback in `boundedConfidence`

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:269`

**Issue:** `InsightConfidence(rawValue: capped) ?? .low` — `capped = min(notches, 2)` is
always 1 or 2 (notches starts at 1), so `InsightConfidence(rawValue:)` can never return
nil and the `?? .low` branch is unreachable. Similarly the `guard confidence >= .low`
defensive check at line 87 can never fail (floor is `.low`, confidence is always `.low` or
`.medium`). Both are harmless defensive code, correctly biased toward withholding.

**Fix:** Leave as-is (intentional defense-in-depth on the highest-stakes path), or add a
brief comment marking them as deliberately-unreachable guards so a future reader does not
hunt for the failing case.

### IN-02: `.first!` force-unwrap on the dominant-symptom sort

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:136`

**Issue:** `typesLogged.sorted { … }.first!` force-unwraps. It is SAFE — `typesLogged` is
guarded non-empty at line 125, and sorting a non-empty set yields a non-empty array — but a
force-unwrap on the single riskiest engine in the app is a readability/robustness smell.

**Fix:** Replace with a `guard let dominant = …sorted{…}.first else { return nil }` so the
no-warning fallback is explicit and a future refactor that weakens the line-125 guard fails
closed instead of trapping.

### IN-03: Lexicographic tie-breaks read as numeric in the comments

**File:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift:135, 233`

**Issue:** Both tie-breaks use `a.rawValue < b.rawValue` and the comments say "lowest
rawValue". `SymptomType` and `CyclePhase` are both `String`-backed (SnapshotEnums.swift:20,
CyclePhase.swift:15), so these are *lexicographic string* comparisons, not numeric ordering.
The result is fully deterministic (the actual requirement), so this is not a bug — but the
"lowest rawValue" phrasing implies a numeric order that does not exist and could mislead a
maintainer reasoning about which symptom/phase wins a tie.

**Fix:** Reword the comments to "tie → lexicographically-lowest rawValue (deterministic)"
so the determinism guarantee is stated without implying a numeric ordering.

### IN-04: Flare seed pairs `-uitest-seed-flare` with `-uitest-reset`, but the guard is the AND, not the reset alone

**File:** `Sources/EndoApp/App/AppDependencies.swift:190-192, 343-353`

**Issue:** `seedFlareWarning` is correctly gated behind `seedFlare && uiTestReset` (line 190),
so the fixture can only ever land in the in-memory store and never the user's real
local/CloudKit store — this matches the established `-uitest-seed-*` precedent and is the
correct, safe posture. Noting it only to confirm the seam was reviewed: the `-uitest-seed-flare`
arg in isolation (without `-uitest-reset`) is a no-op, which is the intended fail-safe. No
change needed; flagged as INFO for traceability of the test-seam review.

**Fix:** None required. Optionally add a one-line comment at line 90 mirroring the seed
seams above noting that the arg is inert without `-uitest-reset`, for symmetry.

---

_Reviewed: 2026-06-10_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
