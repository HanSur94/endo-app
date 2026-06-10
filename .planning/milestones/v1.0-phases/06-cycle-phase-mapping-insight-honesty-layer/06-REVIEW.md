---
phase: 06-cycle-phase-mapping-insight-honesty-layer
reviewed: 2026-06-08T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/HonestyGate.swift
  - EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift
  - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
  - EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift
  - EndoInsights/Sources/EndoInsights/CyclePhase.swift
  - EndoInsights/Sources/EndoInsights/Insight.swift
  - EndoInsights/Sources/EndoInsights/TrendsEngine.swift
  - Sources/EndoApp/Persistence/Models/DismissedInsight.swift
  - Sources/EndoApp/Persistence/SchemaV4.swift
  - Sources/EndoApp/Persistence/MigrationPlan.swift
  - Sources/EndoApp/Persistence/Repository.swift
findings:
  critical: 2
  warning: 5
  info: 4
  total: 11
status: issues_found
---

# Phase 6: Code Review Report

**Reviewed:** 2026-06-08
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 6 delivers the cycle-phase mapper and the insight-honesty layer — the product's ethical core. The architecture is sound: the package stays Foundation-only (SC1 holds), the engine is deterministic (injected `today`/`calendar`, no `Date.now`/randomness, deterministic UUID derivation, fully `Equatable`), and the suppressed/irregular/under-cycle short-circuits in `CyclePhaseMapper` correctly emit `[]` with confidence capped at `.medium`. `DismissedInsight` is CloudKit-safe (all-optional/defaulted, no `.unique`, app-gen UUID, no relationships, opaque category `signalKey`), the V3→V4 migration is lightweight-only, and dismissal CRUD is consent-gated.

However, the adversarial review surfaced **two Critical defects in the honesty gate itself** — the one control this entire phase exists to provide:

1. The `.cyclePhase` producer surfaces n=1 phase/symptom associations to the user, and the gate does NOT filter them (it relies on a producer self-gate that does not exist for sample size). This is exactly the "under-sampled finding reaches the user" failure the gate must prevent.
2. The forbidden-term scan that is supposed to block causal claims runs **only as a debug `assert`** — in release builds it is compiled out entirely, so a causal-term copy bug ships to users with no runtime block. The phase's own documentation calls the test the "durable guarantee," but a test cannot catch a runtime string the producers build at runtime (e.g. interpolated labels).

These are detailed below.

## Critical Issues

### CR-01: cyclePhase associations are emitted on a single co-occurrence (n=1) and the gate never withholds them

**File:** `EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift:224`, `EndoInsights/Sources/EndoInsights/HonestyGate.swift:60-76`

**Issue:** This is an under-sampled-insight bypass — the #1 thing the honesty gate exists to block.

The only sample gate inside `CyclePhaseMapper.insights` is `gaps.count >= 2` (CyclePhaseMapper.swift:49) — i.e. "at least two cycles exist." But that gate is about whether *a regular cycle exists at all*; it says nothing about whether any *specific (phase, symptom) association* is sufficiently sampled. The per-association emit loop gates only on `logged >= 1`:

```swift
let logged = counts[type] ?? 0
guard logged >= 1 else { continue }   // ≥1 co-occurrence; ≥2-cycle floor is the gate's job (Plan 02)
```

So a user who logged "pelvic pain" on a **single day** that happens to fall inside an estimated luteal window gets the insight *"Pelvic pain tended to co-occur with your luteal phase"* with detail *"logged on 1 of 1 of your estimated luteal-phase days"* — at `.low` or even `.medium` confidence. That is a phase-level association asserted from n=1.

The inline comment claims "≥2-cycle floor is the gate's job (Plan 02)," but the gate does no such thing. `HonestyGate.apply` (HonestyGate.swift:60-76) performs exactly three operations: dismissal exclusion (a), confidence floor (c), and a debug term-assert (d). Step (b) — min-sample — is a **deliberate no-op**: the comment at HonestyGate.swift:65-67 says the gate "does NOT re-drop them on any count." `HonestyGate.minSample(for:)` is **defined but never called anywhere in the package** (dead code; see IN-01). The "≥2-cycle floor" the producer comment defers to therefore does not exist in either layer. The under-sampled association reaches the user.

The no-regression rationale for *not* count-dropping `.trend` is valid (a frequency trend legitimately carries 1–2 points after passing `daysWithAnyLog >= 3`). But that reasoning does **not** transfer to `.cyclePhase`: the cyclePhase producer has no per-association sample self-gate equivalent to `daysWithAnyLog >= 3`. The superset-filter contract assumed every producer self-gates its candidate sample before emitting; this producer does not.

**Fix:** Add a per-association sample floor in the producer so a phase/symptom association requires a minimum co-occurrence count across at least 2 cycles, mirroring the `.cyclePhase → 2` contract `minSample` already declares. Minimal version:

```swift
// Require the association to be observed on >= minSample(.cyclePhase) in-phase days,
// not a single coincidental day, before asserting a phase-level co-occurrence.
let minCoOccurrence = HonestyGate.minSample(for: .cyclePhase)   // 2
guard logged >= minCoOccurrence else { continue }
```

(And update the misleading comment.) Alternatively, make `HonestyGate.apply` actually enforce `minSample` for `.cyclePhase` by carrying the candidate sample count on the insight so the gate can drop it — but the cleaner fix is the producer floor, since the gate cannot see the co-occurrence count today.

### CR-02: forbidden-term (causal-claim) enforcement is debug-only; release builds ship causal copy unblocked

**File:** `EndoInsights/Sources/EndoInsights/HonestyGate.swift:78-86`

**Issue:** The forbidden-term scan — the control that proves no emitted insight contains a causal/medical-claim term (`trigger`, `causes`, `will`, `treat`, `because`, ...) — runs only inside `assert(...)`, which is **compiled out in release builds** (`-O` strips `assert`). In a shipping app the loop at HonestyGate.swift:79-86 does nothing: a causal-term copy bug surfaces to the user with zero runtime block.

The file's own header (lines 78, 54-57) and `ForbiddenTerms.swift:19-21` acknowledge this and lean on "the durable guarantee is the zero-forbidden-terms TEST." But a static test cannot cover **runtime-interpolated** copy. `CyclePhaseMapper.associations` builds headline/detail by interpolating `displayName(type)` and `phaseName(phase)` (CyclePhaseMapper.swift:234-235), and future Phase 7/11/12 producers will interpolate user-derived labels (factor names, food tags). If any interpolated token ever contains a forbidden substring (e.g. a localized label, a future free-text-derived factor name, or simply a copy edit a test fixture didn't cover), the assert path catches it only in debug — release ships it. For the product's *ethical core*, "the linter runs only in debug" is not an adequate control.

Worse, even in debug the policy is "assert, never drop" — so the intended release behavior is explicitly *surface the causal-claim insight anyway*. There is no release path that withholds a causal-term insight.

**Fix:** Make the term scan a real release-mode filter, not just a debug assert. The gate should WITHHOLD any insight whose headline or detail contains a forbidden term, in all build configurations, and may *additionally* assert in debug to flag the producer bug loudly:

```swift
let clean = survivors.filter { insight in
    if let term = ForbiddenTerms.violation(in: insight.headline)
        ?? ForbiddenTerms.violation(in: insight.detail) {
        assertionFailure(
            "HonestyGate: forbidden causal term \(term) in insight copy "
            + "(signalKey: \(insight.signalKey)). Fix the producer's copy.")
        return false   // release: WITHHOLD — never surface a causal claim.
    }
    return true
}
return clean
```

Withholding (fail-closed) is the correct posture for a safety control: a copy bug should hide the insight, not emit an unvetted causal claim. (Note: `ForbiddenTerms.violation` short-circuits on the first match, so this is cheap.)

## Warnings

### WR-01: `.insufficient` carve-out is a raw string-suffix bypass of the confidence floor — any signalKey ending `.insufficient` skips the floor

**File:** `EndoInsights/Sources/EndoInsights/HonestyGate.swift:70-73`

**Issue:** The confidence floor is the withhold control for low-confidence findings, but it is bypassed by `insight.signalKey.hasSuffix(".insufficient")`. Today only `TrendsEngine.notEnoughData` (TrendsEngine.swift:240) produces that suffix, so the carve-out is correctly scoped. But the carve-out is keyed on an unstructured *string suffix*, not on a typed marker. Any present or future producer (Ph7/11/12, locked to route through this gate) that names a signalKey ending in `.insufficient` — for any reason — silently inherits a `.none`-confidence bypass of the floor. The gate cannot distinguish "the intended honest not-enough-data signal" from "an arbitrary low-confidence insight whose key happens to end that way." Given this is the safety choke-point with a LOCKED API that future kinds extend, the bypass key should be unforgeable-by-accident.

**Fix:** Represent the honest sub-threshold signal as a typed property on `Insight` (e.g. `let isInsufficiencyNotice: Bool`, defaulted `false`, set only by `notEnoughData`) and carve out on that, not on a string suffix. Then no producer can accidentally cross the floor by naming a key.

### WR-02: gate trusts producer self-gating but one producer (cyclePhase) does not self-gate sample — the "superset filter" invariant is unenforced

**File:** `EndoInsights/Sources/EndoInsights/HonestyGate.swift:65-67`

**Issue:** The whole no-count-drop design (the superset-filter invariant) rests on the documented assumption that *every* producer self-gates its candidate against `minSample(for:)` before emitting. CR-01 shows `CyclePhaseMapper` violates that assumption. Independent of the CR-01 fix, the gate has **no mechanism to verify** the assumption holds — it is a comment, not a check. As Ph7/11/12 add producers, a producer that forgets to self-gate has no backstop, because the gate deliberately performs no count drop. The single most safety-critical contract in the package is enforced by convention only.

**Fix:** Give the gate something to enforce. Carry the candidate sample size on `Insight` (e.g. `sampleSize: Int`) and have `HonestyGate.apply` drop any insight where `sampleSize < minSample(for: kind)`. That turns `minSample` from dead documentation (IN-01) into the actual backstop, and makes the gate genuinely a superset filter rather than a pass-through that trusts each producer.

### WR-03: ForbiddenTerms substring scan can false-negative on word boundaries it does not anticipate (and false-positive risk is unmitigated for future producers)

**File:** `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift:33-57`

**Issue:** The scan is a case-insensitive *substring* match. Two problems compound CR-02:
- **False-positive land mines** for future copy: `"will"` is a substring of `goodwill`, `willing`, `unwillingly`; `"cause"` ⊂ `because` (acknowledged). The mitigation is "curate copy to avoid the host word," which is fragile across localization and future producers and is enforced only by the debug assert / test (see CR-02).
- **False-negatives:** the list omits common causal forms the policy clearly intends to block — `"caused"`, `"causing"`, `"triggers"`, `"triggered"`, `"triggering"`, `"leading to"`, `"contributes to"`, `"linked to"`, `"associated with"` (a causal reading), `"worsen"`/`"worsens"`, `"improve"`/`"reduces"`. `"causes"` and `"cause"` are listed but `"caused"`/`"causing"` only match via the `cause` substring (OK), yet `"triggers"` matches `trigger` (OK) while `"worsens"` matches nothing. A producer writing "stress worsens your luteal phase" would pass the scan.

**Fix:** (1) Expand the causal list to cover the inflections/connectives the policy intends. (2) For robustness against host-word false positives, consider matching on word boundaries for the short ambiguous verbs (`will`, `cause`) via a tokenized scan rather than naive `contains`. At minimum, document the list as non-exhaustive and require the (now release-mode, per CR-02) scan plus a reviewed copy checklist.

### WR-04: `DismissedInsight` is nested under `SchemaV1` but introduced in V4 — entity-history correctness depends on an unverifiable naming assumption

**File:** `Sources/EndoApp/Persistence/Models/DismissedInsight.swift:23`, `Sources/EndoApp/Persistence/SchemaV4.swift:24-33`

**Issue:** The new model is declared `extension SchemaV1 { @Model final class DismissedInsight }` and then listed in `SchemaV4.models` as `SchemaV1.DismissedInsight.self`. The correctness argument (SchemaV4.swift:8-12, DismissedInsight.swift:13-16) is that SwiftData keys entity identity on the *class/entity name* ("DismissedInsight"), not the Swift enum namespace, so listing the same class additively is lightweight-compatible. This is plausible and likely correct, but it is load-bearing for migration safety and rests on a SwiftData internal behavior, not an API guarantee. If SwiftData ever incorporates the namespace into the entity name (or a future model name collides), a *new entity is created in V4* and a lightweight migration silently drops or fails to map data. There is no in-code assertion of the resolved entity name.

**Fix:** This is acceptable IF `SchemaMigrationTests (V3→V4)` actually round-trips real V3 data through a reopen and asserts `DismissedInsight` is queryable AND that the entity name is "DismissedInsight" (not "SchemaV1.DismissedInsight" or "SchemaV4.DismissedInsight"). Confirm that test exists and asserts the entity name explicitly; if it only checks "is queryable" it will not catch a name drift that orphans V3 rows. (Test file is out of the reviewed scope — flag for the verifier.)

### WR-05: `phaseWindows` overlapping/degenerate ranges can double-count a day or silently drop phases for short cycles

**File:** `EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift:178-191`

**Issue:** For short median cycles or a wide `band`, the windows can overlap or invert:
- `phase(for:in:)` (line 189-191) returns the **first** matching window via `windows.first { ... }`. The window array order is `[menstrual, ovulatory, (follicular), (luteal)]` (note: ovulatory is appended before follicular/luteal, lines 178-184). If `bleedEnd >= ovLower` (a short cycle where bleeding runs into the ovulation band) the menstrual and ovulatory windows overlap, and a day in the overlap is silently attributed to whichever appears first in the array — menstrual — regardless of biological intent. The phase a day is assigned to depends on array insertion order, which is not an honest, defensible mapping.
- The follicular/luteal degenerate guards (`folLower <= folUpper`, `lutLower <= lutUpper`, lines 183-184) silently *drop* those phases for very short cycles. A user can then receive ovulatory/menstrual associations but never follicular/luteal, with no signal that the windows were degenerate. This is not strictly a phantom phase, but it is an inconsistent, hard-to-explain mapping that undermines the "honest estimate" posture.

**Fix:** Make overlap handling explicit rather than order-dependent: detect when `bleedEnd >= ovLower` (or any window pair overlaps) and treat the cycle as too short to phase-map honestly (return `[]` for that cycle, consistent with the irregular short-circuit), rather than attributing overlap days by array position. At minimum, assert non-overlap of the constructed windows in debug so the order-dependence cannot hide.

## Info

### IN-01: `HonestyGate.minSample(for:)` is dead code — defined, documented as the API contract, never called

**File:** `EndoInsights/Sources/EndoInsights/HonestyGate.swift:32-40`

**Issue:** `minSample(for:)` is never invoked anywhere in the package (verified). It is presented as "an API CONTRACT producers self-check against," but no producer calls it (TrendsEngine hard-codes `minLoggedDays = 3`; CyclePhaseMapper hard-codes `gaps.count >= 2` and `logged >= 1`). It is documentation masquerading as code, and its existence makes the gate *look* like it enforces a sample floor when it does not (the root of CR-01/WR-02).

**Fix:** Either wire it into the gate as the real backstop (see WR-02) or, if producer self-gating is truly the design, demote it to a documented constant table the producers actually reference, so the duplication (`3` appears in both `minSample(.trend)` and `TrendsEngine.minLoggedDays`) cannot drift.

### IN-02: `DismissedInsight.dismissedAt` and `id` use `Date()`/`UUID()` defaults — non-deterministic, but acceptable for a persisted event row

**File:** `Sources/EndoApp/Persistence/Models/DismissedInsight.swift:31, 36, 38`

**Issue:** The model defaults `id = UUID()` and `dismissedAt = Date()`/`.now`. The package took great care to be deterministic; this app-side `@Model` is not (by design — it is a persisted event, not a pure computation). Worth noting only so the determinism boundary is explicit: non-determinism is confined to the persistence layer, never the package. No fix required, but ensure no test asserts on `dismissedAt`/`id` equality.

### IN-03: duplicated factor-projection logic (`makeFactorDay` vs `projectFactors`) invites drift

**File:** `Sources/EndoApp/Persistence/Repository.swift:448-499` and `631-686`

**Issue:** `makeFactorDay` (app enums) and `projectFactors` (package enums) are near-identical FactorKind switches. The comment at line 628-630 acknowledges this and asks they "cannot drift," but enforces it only by convention. One subtle existing divergence: `makeFactorDay` stores `foodNote` (line 465) while `projectFactors` deliberately drops the note (line 657-658, privacy). That intended difference is correct, but the structural duplication means a future field added to one switch can be forgotten in the other. Consider a shared decode that returns a neutral intermediate both projections map from. Out of v1 scope (no correctness defect today); noted for maintainability.

### IN-04: `lastDay` empty-fallback returns epoch 0, which can produce a misleading InsightPoint date

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:319-321`, `201-216`

**Issue:** `lastDay` returns `Date(timeIntervalSince1970: 0)` (1970) for an empty snapshot list, and `scaleMeanTrend` uses it as the x-value for the mean point (line 215). The guard `readings.count >= minLoggedDays` (line 206) means `recent` is non-empty whenever this path runs, so `lastDay(recent)` cannot actually hit the empty fallback in production — but the fallback is a latent trap if the helper is ever reused without that guard. Low priority; consider returning the injected `today` or making the function require non-empty input.

---

_Reviewed: 2026-06-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
