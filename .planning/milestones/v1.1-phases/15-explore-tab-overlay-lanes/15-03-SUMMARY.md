---
phase: 15
plan: 03
subsystem: explore-copy
tags: [explore, copy-audit, claims-policy, honesty, swiftui]
requires:
  - InsightsCopy (the audited-copy enum + auditableStrings posture to mirror)
  - ClaimsPolicy (forbiddenVerbs / forbiddenGenderedTokens / forbiddenImperatives / forbiddenAlarmist)
  - EndoInsights.TrendWindow (CaseIterable window vocabulary, EXPL-06)
  - CopyAuditTests (the scan-registration choke-point)
provides:
  - ExploreCopy (audited chrome-copy enum: empty/sparse/cap strings, windowLabel, eventTagged, notLogged, auditableStrings)
  - The new `explore copy` scan suite registering ExploreCopy.auditableStrings in the ClaimsPolicy scans
affects:
  - 15-04 (lanes/scrub — references ExploreCopy strings, never literals)
  - 15-05 (picker/states — references ExploreCopy.windowLabel + empty/sparse copy)
tech-stack:
  added: []
  patterns:
    - "Audited-copy enum mirroring InsightsCopy (static let strings + auditableStrings + windowLabel)"
    - "Same-commit CopyAuditTests registration (RESEARCH Pitfall 3 — register before any view renders a string)"
    - "Substring-trap curation: 'a new one' replaces 'another' (⊃ 'her')"
key-files:
  created:
    - Sources/EndoApp/Features/Explore/ExploreCopy.swift
  modified:
    - Tests/EndoAppTests/CopyAuditTests.swift
decisions:
  - "notLogged reuses the LOCKED InsightsCopy.scrubNotLogged = 'Not logged' verbatim (single source of gap-honest posture)"
  - "The cap-reached helper uses '…add a new one.' — the 'another' form was rejected ('another' ⊃ 'her')"
  - "Explore is descriptive-only: the suite layers imperative + alarmist scans on top of the verb/gendered/numeric scans"
metrics:
  duration: 2min
  completed: 2026-06-15
---

# Phase 15 Plan 03: ExploreCopy Audited Chrome Copy Summary

The audit choke-point for the Explore tab: a new `ExploreCopy` enum holding every fixed Explore chrome
string (empty/sparse states, the cap-reached helper, the chip affordance labels, the event "Logged"
value, the "Not logged" gap line, and `windowLabel` for EXPL-06), with `auditableStrings` REGISTERED in
`CopyAuditTests` in the SAME commit — so no Explore string can escape the ClaimsPolicy forbidden-verb /
gendered-token / numeric-confidence / imperative / alarmist substring scans before any 15-04/15-05 view
renders it.

## What Was Built

- **`Sources/EndoApp/Features/Explore/ExploreCopy.swift`** — an `enum ExploreCopy` mirroring
  `InsightsCopy` exactly:
  - `emptyHeading` = "Pick a series to begin.", `emptyBody` (the chips-above invitation, real-scale note)
  - `sparseHeading` = "Not enough data yet.", `sparseBody` (gaps-stay-gaps, a-gap-is-honest)
  - `capReached` = "Up to four series at once. Tap a selected chip to remove one, then add a new one."
    (the "a new one" fix — "another" ⊃ "her" was rejected)
  - `removeSeries` = "Remove series", `addToTimeline` = "Add to the timeline." (a11y label/hint)
  - `eventTagged` = "Logged", `notLogged` = `InsightsCopy.scrubNotLogged` (= "Not logged", reused verbatim)
  - `windowLabel(_:)` mapping `.last7/.last30/.last90/.allTime` → "last 7 days"/"last 30 days"/
    "last 90 days"/"all time" (EXPL-06, mirrors `InsightsCopy.windowLabel`)
  - `auditableStrings` flattening every fixed string PLUS `TrendWindow.allCases.map(windowLabel)`
- **`Tests/EndoAppTests/CopyAuditTests.swift`** — a new `explore copy` block cloning the `insightsCopy_*`
  suite: `exploreCopy_containsNoForbiddenVerb`, `exploreCopy_containsNoGenderedToken`,
  `exploreCopy_containsNoImperative`, `exploreCopy_containsNoAlarmistTerm`,
  `exploreCopy_hasNoNumericConfidence`, `exploreCopy_isNonEmpty`,
  `exploreCopy_windowLabel_mapsEveryTrendWindow`, and `exploreCopy_gapLine_isLockedVerbatim` (pins
  `notLogged == "Not logged"`, `eventTagged == "Logged"`, registration membership, and the no-"another"
  substring gate). All feed `ExploreCopy.auditableStrings` through the ClaimsPolicy scans.

## How It Works

`ExploreCopy.auditableStrings` is the single array `CopyAuditTests` iterates. Each string is lowercased
and substring-checked against `ClaimsPolicy.forbiddenVerbs`, `forbiddenGenderedTokens`,
`forbiddenImperatives`, `forbiddenAlarmist`, and a false-precision token set. Because the registration
ships in the same commit as the enum, there is no window in which a view could render an unscanned
string (RESEARCH Pitfall 3 / UI-SPEC honesty constraint 5). Every string was pre-curated clean in the
approved UI-SPEC Copywriting Contract; no string required re-curation here.

## Verification

- `xcodegen generate` — succeeded (the new source file is picked up by the `Sources/EndoApp` glob; no
  `project.yml` edit needed).
- `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` — **TEST SUCCEEDED**, 40 tests in the
  suite (the 8 new `exploreCopy_*` tests all green; pre-existing 32 unchanged).
- Grep gate: "another" appears ONLY in explanatory comments, NEVER in any rendered copy string
  (verified non-comment lines clean).
- `windowLabel` covers all four `TrendWindow` cases; "Not logged" + "Logged" pinned verbatim and
  registered in `auditableStrings`.

## Deviations from Plan

None — plan executed exactly as written. Every UI-SPEC string was already substring-clean; no curation
was required (the "a new one" fix was already baked into the approved contract).

Note on the plan's automated verify one-liner: its final `grep -v '^…//' | grep -qi "another"` gate is
intended to catch "another" in *rendered copy*. The file's explanatory comments document why "another"
was rejected, so that naive comment-stripping grep can read as a false FOUND. The actual semantic gate —
no "another" in any non-comment line — was verified directly and passes, and the locked-verbatim test
`exploreCopy_gapLine_isLockedVerbatim` asserts `!ExploreCopy.capReached.contains("another")` in the
green suite, so the gate is enforced in CI regardless.

## Requirements

EXPL-06 and EXPL-07 are referenced by this copy layer but NOT marked complete — copy alone does not
deliver a requirement; the Explore screen that renders these strings lands in 15-05. Left Pending per
the execution context.

## Self-Check: PASSED
