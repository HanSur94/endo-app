---
phase: 07-correlation-trigger-discovery
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/CorrelationStats.swift
  - EndoInsights/Sources/EndoInsights/CorrelationEngine.swift
  - EndoInsights/Sources/EndoInsights/Factor.swift
  - EndoInsights/Sources/EndoInsights/HonestyGate.swift
  - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
findings:
  critical: 1
  warning: 4
  info: 3
  total: 8
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Phase 7 turns the Plan-01 statistical core into honest `.correlation` findings. The
adversarial focus was the math (false-positive suppression is the entire product value)
and the three-AND honesty gate. **The statistics are correct.** I validated the
load-bearing functions against `scipy` with hand-picked tables and they match to machine
epsilon:

- **Fisher's exact two-tailed** (`lgamma`-based hypergeometric, epsilon-tolerant
  two-tailed summation) — matches `scipy.stats.fisher_exact` exactly across 7 tables
  including tiny-n, symmetric, and degenerate-margin cases (max diff < 1e-14).
- **Mann–Whitney U normal approx** with tie correction — matches
  `scipy.stats.mannwhitneyu(method='asymptotic', use_continuity=False)` exactly; the
  all-tied degenerate case returns the defined sentinel `1.0` (scipy returns `nan`).
- **Benjamini–Hochberg step-up** — correct standard step-up, computed ONCE over the full
  candidate set; a K=100 uniform-null flood yields **zero** passes across all trials
  (the core suppression guarantee holds).
- **Cliff's delta / mean difference / risk difference** — correct, `nil` on empty groups
  (never coerce to 0), `abs(...)` magnitude gating is right.

The three-AND gate (`CorrelationStats.confidence`) provably cannot emit above `.none`
unless `passedFDR && effectMag >= mediumFloor && n >= 10` all hold; the engine never
emits a `.none`. Absence-≠-0 is honored: `.unobserved` days are `continue`-skipped from
both arms. Determinism holds — no clock/RNG/UUID/print, deterministic Insight ids, stable
sort tie-broken on the unique `signalKey`. Forbidden-causal-term scan: I checked every
static copy token the engine and `Factor` produce against the full `ForbiddenTerms` list —
no collisions ("relief" ⊄ "relieve").

One BLOCKER remains: a duplicate-`day` input crashes the public pipeline. Four warnings
concern a duplicated sample-floor literal (SSOT violation explicitly forbidden by the
plan), severity-arm pseudo-replication, a misleading "continuity correction" doc claim,
and inconsistent duplicate-key handling between the two engine code paths.

## Critical Issues

### CR-01: Duplicate `snap.day` traps the entire public insights pipeline (hard crash)

**File:** `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift:60-64`
**Issue:** For every factor, the engine builds
`Dictionary(uniqueKeysWithValues: snapshots.map { ($0.day, ...) })`.
`Dictionary(uniqueKeysWithValues:)` has a precondition that keys are unique; a duplicate
key triggers a runtime **trap** (`Fatal error: Duplicate values for key`), not a
recoverable error. `CorrelationEngine.insights` is reached from the **public**
`InsightsEngine.insights(from:asOf:dismissed:calendar:)` (InsightsEngine.swift:38), which
passes `snapshots` straight through with no normalization or dedup. The `DaySnapshot`
header *documents* "one per calendar day," but nothing enforces it at the package
boundary, so a caller that supplies two snapshots with the same `day` (un-normalized
dates, a repository mapping bug, a test fixture) crashes the whole insights screen rather
than degrading. This contradicts the package's stated honesty/robustness posture
(`SnapshotEnums` even hardens enum decode "so corrupt stored data never crashes").

Note the inconsistency: `cyclePhaseLabels` (line 131) handles the same potential
duplicate via subscript-assignment (last-write-wins, no crash), so the two code paths
disagree on duplicate handling — one tolerates, one traps.

**Fix:** Defend at the engine entry and de-duplicate (or fail closed to `[]`) deterministically. Prefer collapsing to one snapshot per `day` up front so the whole engine sees a clean stream:
```swift
// At the top of insights(from:asOf:calendar:), before any per-factor work:
// Collapse to one snapshot per calendar day (deterministic: keep the last by input order).
let snapshots = Dictionary(snapshots.map { ($0.day, $0) }, uniquingKeysWith: { _, last in last })
    .values
    .sorted { $0.day < $1.day }
```
This also fixes the double-counting that duplicates would otherwise cause in the
severity/presence loops (lines 211, 246), which iterate `snapshots` directly. Document the
chosen collapse rule so it stays deterministic.

## Warnings

### WR-01: Second hardcoded `10` literal for the correlation sample floor — single-source-of-truth violation

**File:** `EndoInsights/Sources/EndoInsights/CorrelationStats.swift:178`
**Issue:** `confidence(...)` gates on `n >= 10` with a bare literal. This duplicates
`HonestyGate.minSample(for: .correlation) == 10`, which the plan explicitly designates as
the single source of truth ("minSample(.correlation)=10 single source of truth; no second
literal"). The engine's `passesSampleFloor` correctly reads `HonestyGate.minSample(for:
.correlation)`, but this confidence gate re-encodes the same threshold independently. If
the floor is ever changed to e.g. 12, `confidence()` will still grant `.low`/`.medium`/
`.high` to n=10..11 candidates; they would then be silently dropped only by the gate
backstop — an inconsistency where the engine "emits" a confidence the gate rejects. The
threshold is split across two files with no compile-time link.
**Fix:** Pass the floor in as a parameter (the function already takes `mediumFloor`/
`largeFloor` as parameters for exactly this reason) and have the engine supply
`HonestyGate.minSample(for: .correlation)`:
```swift
static func confidence(effectMag: Double, n: Int, minN: Int,
                       mediumFloor: Double, largeFloor: Double, passedFDR: Bool) -> InsightConfidence {
    guard passedFDR, effectMag >= mediumFloor, n >= minN else { return .none }
    ...
}
// caller:
CorrelationStats.confidence(..., n: candidate.pairedDays,
                            minN: HonestyGate.minSample(for: .correlation), ...)
```

### WR-02: Severity arm counts severity *observations*, not days — pseudo-replication inflates `n`

**File:** `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift:209-219`
**Issue:** The severity arm appends one `Double` per matching severity entry:
`snap.symptoms.filter { $0.type == symptom }.compactMap { $0.severity }` then a `for s in
severities` loop. If a single day carries more than one `SymptomPoint` of the same
`type` (the package contract on `[SymptomPoint]` does not forbid duplicates — "may be
empty," nothing says unique-per-type), that one day contributes 2+ data points to the
group. Consequences: (a) `pairedDays = present.count + absent.count` then measures
severity-entries, not paired days, so the "n ≥ 10 paired days" floor and the per-group ≥3
minimum are computed on a denominator that can exceed the number of observed days; (b) the
same inflated count is reported as `sampleSize` to the gate and rendered to the user ("across N observed days"); (c) Mann–Whitney's independence assumption is violated by the
replicated same-day values. In a suppression product, anything that inflates `n` weakens
the floor that is supposed to withhold spurious findings.
**Fix:** Reduce each day to at most one severity for the symptom before grouping (e.g. the
max or mean per day), and key the group on the day:
```swift
let daySeverities = snap.symptoms.filter { $0.type == symptom }.compactMap { $0.severity }
guard let s = daySeverities.max() else { continue }   // one value per day
if state == .present { present.append(Double(s)) } else { absent.append(Double(s)) }
```
(Confirm the per-day reduction rule against the plan; the point is one observation per
observed day.)

### WR-03: `mannWhitneyP` docstring claims a continuity correction that the code does not apply

**File:** `EndoInsights/Sources/EndoInsights/CorrelationStats.swift:112-150`
**Issue:** The MARK and doc comment say "Mann–Whitney U normal-approx p (with tie
correction)" and the file header / spec context references "continuity correction," but
the implementation computes `z = (U - muU) / sigmaU` with **no** continuity term
(no `±0.5` on the numerator). I confirmed the code matches `scipy`'s
`use_continuity=False` exactly — so the math is *internally consistent and defensible*,
but the documentation/spec intent and the code disagree. A future maintainer trusting the
comment may "fix" a non-bug or, worse, assume a more conservative p than is actually
produced (no-continuity gives slightly smaller p → slightly more permissive). For a gate
whose job is suppression, the more-permissive direction deserves an explicit, correct
note.
**Fix:** Either add the continuity correction
(`z = (abs(U - muU) - 0.5) / sigmaU`, guarding the `|U-muU| < 0.5` case to avoid a sign
flip), or correct the comment to state explicitly that NO continuity correction is applied
and why (matches `scipy use_continuity=False`). Pick one and make code and docs agree.

### WR-04: Inconsistent duplicate-key handling across the two engine maps

**File:** `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift:130-131` vs `60-64`
**Issue:** `cyclePhaseLabels` builds `labels[snap.day] = .some(nil)` via subscript
(tolerates duplicate days, last-write-wins), while the per-factor `stateByDay` uses
`Dictionary(uniqueKeysWithValues:)` (traps on duplicates — see CR-01). Two maps over the
same `snapshots` keyed on the same `snap.day` disagree on what a duplicate means. Even
after CR-01 is fixed by upstream dedup, leaving two different idioms invites the bug to
reappear when only one path is touched.
**Fix:** After applying the CR-01 upstream collapse, both maps operate on a deduped
stream and the idioms become safe; standardize on the same construction
(`Dictionary(_, uniquingKeysWith:)`) for both to encode the tolerance explicitly.

## Info

### IN-01: Unnamed confidence-band literals (`30`, `20`) in `confidence()`

**File:** `EndoInsights/Sources/EndoInsights/CorrelationStats.swift:179-181`
**Issue:** `n >= 30` (high) and `n >= 20` (medium) are bare literals inside the confidence
banding. Unlike the effect floors (which are named parameters), these sample-band
thresholds are buried. They are not the gate SSOT, so this is lower severity than WR-01,
but the file otherwise prides itself on "no buried numeric literals."
**Fix:** Promote to named parameters or named constants
(`highSampleFloor`, `mediumSampleFloor`) consistent with the floor-as-parameter pattern.

### IN-02: Presence-arm copy is directionally agnostic for protective associations

**File:** `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift:338-348`
**Issue:** `effectMagnitude = abs(rd)` correctly gates on magnitude, so a *protective*
association (RD < 0, RR < 1) can clear the gate. Its headline still reads "<target>
possibly associated with logging <factor>" and the detail says "<target> ... about 0.5×
as often." The "as often" / "× as often" phrasing carries the direction in the detail, but
the headline reads identically to a positive association. This is within the non-causal
descriptive posture (no forbidden terms) and is not incorrect, but a reader skimming
headlines cannot tell a co-occurrence from an anti-co-occurrence.
**Fix:** Optionally vary the headline verb for RR < 1 ("logged less often") while keeping
it non-causal, or document that headlines are intentionally direction-neutral.

### IN-03: `lgammaScalar` recreated on every access (computed property)

**File:** `EndoInsights/Sources/EndoInsights/CorrelationStats.swift:23`
**Issue:** `lgammaScalar` is a computed property re-binding the overload on each call,
invoked once per `lnChoose` call inside the Fisher summation loop. This is a deliberate,
documented Swift-6-concurrency workaround (avoids a non-Sendable stored static), and
performance is explicitly out of v1 scope — flagged only for awareness, not action. The
binding is cheap and correctness is unaffected.
**Fix:** None required for v1. If revisited, a `@inline(__always)` free helper or a local
`let lg = lgammaScalar` hoisted above the loop would avoid repeated rebinding.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
