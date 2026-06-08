---
phase: 05-snapshot-contract-descriptive-trends-engine
reviewed: 2026-06-08T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/DaySnapshot.swift
  - EndoInsights/Sources/EndoInsights/Insight.swift
  - EndoInsights/Sources/EndoInsights/SnapshotEnums.swift
  - EndoInsights/Sources/EndoInsights/TrendsEngine.swift
  - EndoInsights/Package.swift
  - Sources/EndoApp/Persistence/Repository.swift
  - project.yml
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-06-08T00:00:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 5 delivers the keystone insights contract (`DaySnapshot`, `Insight`) and a pure
descriptive `TrendsEngine`, plus the app-side `Repository.daySnapshots()` mapping seam. I reviewed
all seven files against the six focus areas: honesty invariants, trend-math correctness,
determinism, the SC1 Foundation-only boundary, mapping fidelity, and Swift 6 concurrency.

**The load-bearing invariants hold.** I traced each one and could NOT find a path that fabricates
data or a false trend:

- **absence != 0:** every mean is built with `compactMap` over the optional severity/stress/mood,
  so `nil` is excluded from both numerator and denominator (`average` returns `nil` for an empty
  set and is never coerced to 0). Verified at `TrendsEngine.swift:131-133`, `188`, `233-236`,
  `238-240`.
- **suppressed / no-bleeding gate:** `bleedingTrend` short-circuits on
  `!anySuppressed && bleedingDayCount >= 1` (`TrendsEngine.swift:206-208`); a suppressed user (the
  flag is stamped on every snapshot at `Repository.swift:522`) gets no bleeding/cycle trend.
- **sub-threshold honesty:** symptom-frequency below `minLoggedDays` emits an explicit "not enough
  data" Insight (`:117/:125-127`); means below threshold emit nothing (honest silence, not a false
  trend).
- **SC1 boundary:** the package imports only Foundation (verified by grep across all four package
  files); `Package.swift` declares zero dependencies; `daySnapshots()` is the sole `@Model ->
  DaySnapshot` map and is consent-gated (`assertConsented()` is the first statement, `:494`).
- **Determinism:** no `Date()`/`Date.now`/`UUID()`/randomness anywhere in the package; `today` and
  `calendar` are injected; `Insight.id` is a deterministic FNV-1a derivation, so `[Insight]` is
  stably `Equatable`.
- **Enum raw-value parity:** I diffed all seven package enums against their app `Domain/*`
  counterparts case-by-case — every raw value matches verbatim, so the seam is a true passthrough.
- **Swift 6 concurrency:** all contract types are `Sendable` value types over `Sendable` members.

No Critical issues. The findings below are correctness/robustness/consistency concerns worth
fixing, none of which fabricate health data.

## Warnings

### WR-01: Corrupt/nil-`typeRaw` symptom rows are silently projected as a fabricated `.other` symptom

**File:** `Sources/EndoApp/Persistence/Repository.swift:557-559`
**Issue:** `SchemaV1.SymptomReading.typeRaw` is `String?` (nullable in the schema). The snapshot
mapping does `SymptomPoint(type: EndoInsights.SymptomType(raw: $0.typeRaw), ...)`, and
`SymptomType(raw:)` maps `nil`/unknown -> `.other` (`SnapshotEnums.swift:34-40`). So a
`SymptomReading` whose `typeRaw` is `nil` (e.g. a future note-only symptom row, or a corrupt/partial
CloudKit-synced row) is materialized as a real `.other` symptom point — `symptomTrends` would then
count it toward "Other symptom" frequency/severity. That is a fabricated signal crossing the seam.
Today no code path writes a nil-`typeRaw` row (`upsertSymptom` always sets it), so this is latent,
not live — hence Warning, not Critical. But the defensive decode that protects every other field
here actively *invents* data for symptoms.
**Fix:** drop, rather than coerce, symptom rows that have no decodable type — mirror the pain-point
handling immediately below it (`:563-566`), which `compactMap`s and drops a row whose region decodes
to `nil`:
```swift
let symptomPoints: [SymptomPoint] = symptoms.compactMap { r in
    guard let raw = r.typeRaw, let type = EndoInsights.SymptomType(rawValue: raw) else { return nil }
    return SymptomPoint(type: type, severity: r.severity)
}
```
(If `.other` for an unknown-but-present raw is desired, keep the `.other` sink only when
`r.typeRaw != nil` and just guard the nil case.)

### WR-02: Frequency denominator conflates "days the symptom was tracked" with "days anything was logged"

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:108,117-124`
**Issue:** symptom-frequency uses `daysWithAnyLog = recent.count` (days carrying *any* signal) as
the denominator and renders it as "logged on X of Y logged days" / "of the Y days you tracked." A
day where the user only logged sleep counts as a day the symptom was "absent," dragging the ratio
down. For a symptom checklist the user does not engage every day, "2 of 30 logged days" reads as a
near-absent symptom even if they only opened the symptom screen twice and marked it both times. This
is a presentation-level honesty risk (it understates frequency), distinct from the absence!=0 mean
rule which is correctly handled. Bleeding (`:210`) has the same denominator shape but is arguably
defensible there since bleeding is expected daily.
**Fix:** either (a) make the copy precise — "logged on X of Y days you tracked anything" — so the
denominator's meaning is explicit and not read as "days you considered this symptom," or (b)
denominate against a per-signal "engaged" count if/when the snapshot can distinguish "symptom screen
opened" from "any log." At minimum, align the headline wording with the actual denominator so it is
not misread as a per-symptom adherence ratio.

### WR-03: "Not enough data" is emitted for symptom frequency only — never for severity, stress, mood, sleep, or bleeding

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:117-128,134,173,189,208`
**Issue:** the explicit "not enough data yet" honesty Insight is produced in exactly one place
(symptom frequency, `:126`). Every other sub-threshold path returns silently: severity-mean
(`:134` guard with no else), stress/mood (`scaleMeanTrend` `:189` returns `nil`), poor-sleep
(`:173`), bleeding (`:208`). Silence is honest (it is not a false trend), so this is not a Critical
honesty break — but it is an internal inconsistency: a user with 2 days of stress logs sees nothing
for stress, while a user with 2 days of a logged symptom sees an explicit "not enough data" card.
That asymmetry will read as a bug to users and makes the "below threshold -> explicit not-enough-data"
contract (stated in the file header, `:11-13`) only half-true.
**Fix:** decide one policy and apply it uniformly. Either route all sub-threshold means/frequencies
through `notEnoughData(...)`, or document that "not enough data" is intentionally scoped to symptom
frequency only and remove the broader claim from the header comment so the contract matches the code.

### WR-04: Severity-mean `points` append a synthetic mean point that duplicates a real calendar date

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:146`
**Issue:** the severity insight's `points` are
`severityPoints(recent, type:) + [InsightPoint(date: lastDay(recent), value: mean)]`. `lastDay`
returns the max snapshot `day` — which is also the date of the last real `severityPoints` entry
whenever the symptom was logged on the latest tracked day. The result is two `InsightPoint`s on the
same date with different values (the raw last reading and the window mean) appended to a series the
header says is "supporting data points ... for the Phase 8 chart" (`Insight.swift:52`). A chart that
plots this gets a vertical discontinuity / duplicate x-value at the right edge, and the meaning of
the trailing point (a mean, not a daily reading) is indistinguishable from the daily series. Stress/
mood means avoid this by returning *only* the single mean point (`:198`), so the two code paths are
also inconsistent.
**Fix:** keep the daily series and the summary statistic separate. Drop the appended mean point from
the `points` array (let `points` be the honest per-day series, `severityPoints(recent, type:)`),
since the mean is already in the headline/detail. If a summary marker is genuinely wanted, model it
distinctly rather than splicing it into the daily series at a colliding date.

## Info

### IN-01: `Insight.derived` is the documented contract but the public memberwise init can still set a non-derived id

**File:** `EndoInsights/Sources/EndoInsights/Insight.swift:77-93`
**Issue:** the determinism contract relies on the engine using `derived(...)`. The public `init`
that takes an explicit `id` is left `public` "for cross-module construction," but nothing prevents a
future caller (or test) from passing a random `UUID()` and breaking `[Insight]` Equatable-by-value
fixtures. The engine itself correctly uses only `derived(...)`.
**Fix:** consider making the explicit-`id` memberwise init `internal` (or `package`) so external
callers must go through `derived(...)`; keep `public` only on `derived`.

### IN-02: `lastDay` fallback to epoch 0 can stamp a real point at 1970-01-01

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:302-304,146,198`
**Issue:** `lastDay` returns `Date(timeIntervalSince1970: 0)` for an empty input. The mean-point
appenders (`:146`, `:198`) only run after a `>= minLoggedDays` guard, so `recent` is non-empty in
practice and the fallback is unreachable today. It is a latent footgun: any future caller that
reaches `lastDay` on an empty slice would emit a 1970 chart point rather than failing visibly.
**Fix:** make the empty case explicit (return an optional and skip the point), or assert non-empty
at the call sites; avoid the silent epoch-0 sentinel.

### IN-03: `Calendar = .current` default makes the engine locale/timezone-dependent unless callers always inject

**File:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift:43`
**Issue:** `trends(... calendar: Calendar = .current)` is convenient, but `.current` reads device
timezone/locale at call time — so the "determinism" guarantee only holds when the caller injects a
fixed calendar. The day-math itself is DST-safe (`startOfDay` + `date(byAdding:.day)`), so this is
not a correctness bug, just a determinism caveat. Fixtures must inject an explicit calendar.
**Fix:** optional — document on the parameter that reproducible output requires an injected calendar,
or drop the default so callers are forced to choose.

### IN-04: `factorReadings(on:)` re-asserts consent while its callers already assert it

**File:** `Sources/EndoApp/Persistence/Repository.swift:417,493-494,529`
**Issue:** `daySnapshots()` asserts consent first (`:494`) then calls `factorReadings`-style fetches
inline; `factorReadings(on:)` also asserts (`:417`). Harmless double-assert (debug-only), but the
mixed pattern (some private fetch helpers assert, some do not) is easy to get wrong later.
**Fix:** standardize — keep the assert at the public entry points only, or on every fetch helper, but
not a mix.

### IN-05: `clots` widening from `Bool?` to `Bool` quietly maps "unknown" to "no clots"

**File:** `Sources/EndoApp/Persistence/Repository.swift:571`
**Issue:** `SchemaV1.BleedingEvent.clots` is `Bool?` (nil = not recorded). The snapshot stores
`clots: Bool` via `flowEvent?.clots ?? false`, collapsing "clots unknown" and "clots explicitly
false" into the same value. `DaySnapshot.clots` is consumed nowhere in this phase's engine, so there
is no live consequence; flagged for parity with the project's otherwise-strict absence!=0 posture
should a future insight read `clots`.
**Fix:** if any future trend reads `clots`, model it as `Bool?` on the snapshot to preserve "not
recorded"; otherwise document that `clots` is intentionally presence-collapsed.

---

_Reviewed: 2026-06-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
