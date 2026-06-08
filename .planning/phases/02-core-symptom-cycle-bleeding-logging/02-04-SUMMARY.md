---
phase: 02-core-symptom-cycle-bleeding-logging
plan: 04
subsystem: logging-ui-cycle-summary
tags: [swiftui, swiftdata, body-map, accessibility, edit-delete, cycle-summary, suppressed-mode, xcuitest, tdd, no-phantom-cycle]

# Dependency graph
requires:
  - phase: 02-core-symptom-cycle-bleeding-logging
    provides: "Repository CRUD incl. setPainPoint/deleteDay + bleedingDays()/isSuppressed() (02-02); pure CycleMath.summarize + CycleSummary (02-03); HomeView/DayLogEditorView/SeverityControl + Domain enums (02-01/02-02)"
provides:
  - "BodyMapView — discrete named-region accessible pain map over BodyRegion.allCases (per-region a11y label+value, >=44pt contentShape, severity number+tint not color-only, front/back toggle, list fallback)"
  - "DayLogEditorView edit/delete: embedded body map, pain-point preload+save, daylog.deleteButton -> deleteDay cascade"
  - "HomeView swipe-to-delete on day rows + CycleSummaryView mounted in the home.cycleSummary slot"
  - "CycleSummaryView — renders CycleMath output: suppressed off-state / not-enough-data / derived (cycle-day + date RANGE + plain confidence word); @Query-driven recompute"
  - "Repository.setSuppressed(_:) + removePainPoint(region:on:); exposed painPoints(on:)/symptomReadings(on:)"
  - "SettingsView settings.suppressedToggle (CYCL-03)"
  - "CopyAuditTests cycle-copy half GREEN; full LoggingFlowUITests (3 methods) GREEN with /tmp/e2e screenshots"
affects: [phase-04-sync, phase-06-insights]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Accessible discrete-region body map: each region is an a11y element (label + severity value), >=44pt contentShape hit target, severity conveyed by NUMBER + tint (never color alone), non-map list fallback — operable without sight/precise tap (Pitfall 4)"
    - "@Query must be CONSUMED in the body-evaluation path to register a SwiftData dependency: CycleSummaryView derives bleedingDays/isSuppressed from @Query arrays directly (not a fresh Repository) so it recomputes after any log/edit/delete/suppressed-flip"
    - "Container a11y identifier on a wrapping VStack with .accessibilityElement(children: .contain) so child state ids (.suppressed/.notEnoughData/.derived) stay individually discoverable to XCUITest"
    - "Next-period rendered ONLY as a Date.FormatStyle RANGE (month.day .. month.day) — never a single hard date; confidence as a plain word"
    - "Append-only suppressed mode: setSuppressed inserts a new timestamped TreatmentState; isSuppressed reads the latest by startedAt"

key-files:
  created:
    - Sources/EndoApp/Features/DayLog/BodyMapView.swift
    - Sources/EndoApp/Features/Home/CycleSummaryView.swift
  modified:
    - Sources/EndoApp/Features/DayLog/DayLogEditorView.swift
    - Sources/EndoApp/Features/Home/HomeView.swift
    - Sources/EndoApp/Features/Settings/SettingsView.swift
    - Sources/EndoApp/Persistence/Repository.swift
    - Tests/EndoAppTests/BodyMapBindingTests.swift
    - Tests/EndoAppTests/EditDeleteTests.swift
    - Tests/EndoAppTests/CopyAuditTests.swift
    - Tests/EndoAppUITests/LoggingFlowUITests.swift

key-decisions:
  - "Body map shipped as a discrete labelled-tile grid + front/back toggle + list fallback (Open Question 1 recommendation): the region MODEL + accessibility contract are what's load-bearing; silhouette geometry can iterate via screenshots later with no contract change"
  - "Confidence words are 'strong/medium/low confidence' — 'higher'/'lower' were REJECTED because they contain the gendered substring 'her' (caught by the cycle CopyAudit, same trap that hit 'Other' in 02-01)"
  - "CycleSummaryView consumes the @Query arrays directly (derives bleedingDays/isSuppressed inline) rather than calling a fresh Repository in a computed property — otherwise SwiftUI registers NO @Query dependency and the summary never refreshes on a suppressed-flip/log/delete"
  - "Delete affordance is dual: a daylog.deleteButton (only shown when the day already has logged data) AND swipe-to-delete on home rows — both call Repository.deleteDay (explicit child cascade, no .deny)"
  - "home.cycleSummary container id moved onto a wrapping VStack (children: .contain) so the state-specific child ids remain queryable by the E2E"

requirements-completed: [SYMP-02, SYMP-04, CYCL-02, CYCL-03, CYCL-04]

# Metrics
duration: ~22min
completed: 2026-06-08
---

# Phase 2 Plan 04: Body Map + Edit/Delete + Cycle Summary Summary

**The final MVP slice: a consented user marks pain on an accessible discrete-region body map (per-region label + severity value, >=44pt targets, severity not color-only, list fallback), edits and deletes any past day with an immediate list + cycle recompute, and sees an honest derived cycle on Home — a cycle-day + a next-period date RANGE + a plain-language confidence word — that vanishes entirely under a suppressed-mode toggle and reads "not enough data yet" when sparse. The full phase E2E (incl. the suppressed-mode "no cycle summary" assertion) is GREEN; all 8 Phase-2 requirements are delivered.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-06-08T10:04:38Z
- **Completed:** 2026-06-08T10:26:17Z
- **Tasks:** 2 (TDD)
- **Files created/modified:** 2 created, 8 modified

## TDD Gate Sequence

| Gate | Commit | What |
| ---- | ------ | ---- |
| RED   | `06b1bc2` | `test(02-04)`: real BodyMapBindingTests + EditDeleteTests referencing repo.painPoints/symptomReadings (then private) -> fails to COMPILE = RED |
| GREEN | `26938a6` | `feat(02-04)`: BodyMapView + editor/home edit-delete wiring + Repository exposure/removePainPoint -> 8 tests GREEN |
| GREEN | `2a0e403` | `feat(02-04)`: suppressed toggle + CycleSummaryView + cycle CopyAudit + full LoggingFlowUITests -> full suite GREEN |

## Accomplishments
- **SYMP-02 accessible body map:** `BodyMapView` renders `BodyRegion.allCases` as discrete tappable tiles (front/back toggle), each an accessibility element with `plainLabel` + a "severity N of 10"/"not marked" value, a `.contentShape` >=44pt hit target, a severity conveyed by NUMBER + tint (never color alone, Pitfall 4), a `bodymap.region.<rawValue>` id, and a non-map `bodymap.list.<rawValue>` LIST fallback setting the same `PainPoint`. Tapping presents a `SeverityControl` sheet writing via `Repository.setPainPoint`. Embedded in `DayLogEditorView`, preloading existing pain points.
- **SYMP-04 edit/delete with cascade:** HomeView day rows re-open the editor preloaded (edit) and expose swipe-to-delete; the editor adds a confirmed `daylog.deleteButton`. Both call `Repository.deleteDay`, which explicitly cascades the day's SymptomReading/PainPoint/BleedingEvent/DayEntry (no `.deny`), so `bleedingDays()` recomputes from a clean set and the `@Query`-driven list + cycle summary refresh immediately (D-06).
- **CYCL-03 suppressed mode:** `settings.suppressedToggle` writes a first-class append-only `TreatmentState` via the new `Repository.setSuppressed(_:)` (consent-gated); `isSuppressed()` reads the latest.
- **CYCL-02/04 cycle summary:** `CycleSummaryView` feeds the `@Query` bleeding/treatment records into the pure `CycleMath.summarize(...today: .now)` and renders exactly three states — suppressed -> ONLY a neutral off-state line (NO cycle-day/range/phase); `!hasEnoughData` -> "not enough data yet"; else -> cycle-day + a next-period date RANGE + a plain confidence word. Never a single hard date, never a 28-based default.
- **Copy audit + full E2E GREEN:** completed the cycle half of `CopyAuditTests` (no forbidden verb/gendered token/`predict`; off-state + "not enough data" framing asserted) and turned the full `LoggingFlowUITests` GREEN — `testLoggingFlow` (consent -> log symptom+severity -> body region+severity -> bleeding -> note -> save -> appears -> edit -> delete -> gone) and `testSuppressedModeShowsNoCycleSummary` (toggle on -> only the suppressed line, no cycle-day/range/not-enough-data). 10 screenshots written to `/tmp/e2e`.

## Task Commits

1. **Task 1: Accessible body map (SYMP-02) + edit/delete with cascade (SYMP-04)** — RED `06b1bc2` (test), GREEN `26938a6` (feat, TDD)
2. **Task 2: Suppressed toggle + CycleSummaryView wired to CycleMath; copy audit + full E2E GREEN (CYCL-02/03/04)** — `2a0e403` (feat, TDD)

## Files Created/Modified
- `Sources/EndoApp/Features/DayLog/BodyMapView.swift` — **new** discrete-region accessible pain map (label+value, >=44pt, number+tint, front/back toggle, list fallback, severity sheet).
- `Sources/EndoApp/Features/Home/CycleSummaryView.swift` — **new** cycle summary: @Query -> CycleMath -> suppressed/not-enough-data/derived (date RANGE + confidence word).
- `Sources/EndoApp/Features/DayLog/DayLogEditorView.swift` — body-map section, pain-point preload+save, confirmed delete button.
- `Sources/EndoApp/Features/Home/HomeView.swift` — mount CycleSummaryView in the slot; swipe-to-delete on rows.
- `Sources/EndoApp/Features/Settings/SettingsView.swift` — `settings.suppressedToggle`.
- `Sources/EndoApp/Persistence/Repository.swift` — `setSuppressed(_:)`, `removePainPoint(region:on:)`; exposed `painPoints(on:)`/`symptomReadings(on:)`.
- `Tests/EndoAppTests/BodyMapBindingTests.swift` / `EditDeleteTests.swift` — RED placeholders -> real GREEN tests.
- `Tests/EndoAppTests/CopyAuditTests.swift` — added the cycle-copy audit (GREEN).
- `Tests/EndoAppUITests/LoggingFlowUITests.swift` — full happy-path + suppressed-mode E2E (GREEN).

## Decisions Made
- **Body-map fidelity (Open Question 1):** shipped the region model + accessibility contract as labelled tiles + a front/back toggle + list fallback; silhouette geometry can iterate later with zero contract change.
- **Confidence wording:** "strong/medium/low confidence" — "higher"/"lower" rejected for the embedded gendered substring "her".
- **@Query dependency:** CycleSummaryView derives its inputs from the `@Query` arrays directly so SwiftUI actually registers the dependency and recomputes (a fresh-Repository computed property does NOT).
- **Container a11y:** `home.cycleSummary` on a wrapping VStack with `children: .contain` so the per-state child ids stay queryable.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Confidence words contained the gendered substring "her"**
- **Found during:** Task 2 (running the new cycle CopyAuditTests)
- **Issue:** "higher confidence"/"lower confidence" contain "her" (a `ClaimsPolicy.forbiddenGenderedTokens` entry) — the same trap that hit "Other" in Plan 02-01. The copy audit failed.
- **Fix:** Re-worded to "strong confidence"/"medium confidence"/"low confidence".
- **Files modified:** Sources/EndoApp/Features/Home/CycleSummaryView.swift
- **Verification:** CopyAuditTests GREEN.
- **Committed in:** `2a0e403`

**2. [Rule 1 - Bug] CycleSummaryView did not recompute on a suppressed-mode flip (no @Query dependency)**
- **Found during:** Task 2 (testSuppressedModeShowsNoCycleSummary — Home kept showing "not enough data yet" with the toggle ON; diagnostic confirmed states:1 supp:true was computable but the view wasn't re-evaluating)
- **Issue:** The view's `summary` was computed from a freshly-built `SwiftDataRepository`, so SwiftUI registered NO dependency on the declared `@Query` arrays — the view never refreshed when a new TreatmentState/bleeding record landed.
- **Fix:** Derive `bleedingDays`/`isSuppressed` from the `@Query` arrays directly in `summary`, so the dependency is registered during `body` evaluation and the summary recomputes after any log/edit/delete/suppressed-flip.
- **Files modified:** Sources/EndoApp/Features/Home/CycleSummaryView.swift
- **Verification:** suppressed E2E GREEN; the home summary flips to the off-state immediately.
- **Committed in:** `2a0e403`

**3. [Rule 3 - Blocking] Child a11y ids masked by the container's identifier**
- **Found during:** Task 2 (the suppressed line rendered, with supp:true, but XCUITest could not find `home.cycleSummary.suppressed`)
- **Issue:** Putting `.accessibilityIdentifier("home.cycleSummary")` on the same element that, in the suppressed case, was the single `Text` carrying `home.cycleSummary.suppressed` made the outer id win and masked the child.
- **Fix:** Moved the container id onto a wrapping VStack with `.accessibilityElement(children: .contain)` so the per-state child ids stay individually discoverable.
- **Files modified:** Sources/EndoApp/Features/Home/CycleSummaryView.swift
- **Verification:** suppressed E2E GREEN.
- **Committed in:** `2a0e403`

**4. [Rule 3 - Blocking] XCUITest harness fixes for the new flow (body-map tap, toggle flip, confirm-dialog dedup)**
- **Found during:** Task 2 (running the full E2E)
- **Issue:** (a) the body-map region tile swallowed a centre tap until styled `.buttonStyle(.plain)`; (b) a SwiftUI Toggle in a Form did not flip on a centre `.tap()` — needed a trailing-edge coordinate tap with a value-check retry; (c) the `daylog.deleteConfirm` button matched multiple elements in the confirmationDialog (used `.firstMatch`); (d) the Settings back button needed scoping to `navigationBars["Settings"]`.
- **Fix:** Applied the four harness adjustments (and `.buttonStyle(.plain)` on the region tile — a minor product change that also improves tap reliability).
- **Files modified:** Tests/EndoAppUITests/LoggingFlowUITests.swift, Sources/EndoApp/Features/DayLog/BodyMapView.swift
- **Verification:** full LoggingFlowUITests GREEN (3 methods).
- **Committed in:** `2a0e403`

---

**Total deviations:** 4 auto-fixed (2 bugs, 2 blocking). No architectural changes; no scope creep. No package installs.

## Threat-Model Compliance
- **T-02-13 (unknown regionRaw decode):** `BodyRegion(raw:)` nil-safe; `BodyMapBindingTests.unknownRegionRaw_decodesNilSafely_noCrash` GREEN (no crash, decodes to nil).
- **T-02-14 (severity out of range):** clamped `0...10` at `SeverityControl` and the `setPainPoint` seam (carried from 02-02).
- **T-02-15 (over-claim copy / hard date):** cycle copy via the centralized `CycleSummaryCopy` surface, audited GREEN against forbidden verbs/gendered tokens and `predict`; next-period rendered ONLY as a `Date.FormatStyle` RANGE (grep confirms no single-date cycle formatter).
- **T-02-16 (cycle output for a suppressed user):** `CycleSummaryView` short-circuits to the off-state line when `isSuppressed`; `testSuppressedModeShowsNoCycleSummary` asserts no cycle-day/range/not-enough-data shows.
- **T-02-17 (accessibility exclusion):** per-region a11y label+value, >=44pt `.contentShape` targets, severity number+tint (not color-only), non-map list fallback.
- **V4 / consent:** `setSuppressed` (and all new Repository methods) call `assertConsented()`; the body map / cycle summary / toggle are reachable only from `AppGate.consented`.
- **No package installs** (first-party only — T-02-SC accept).

## Test Results
- **Full unit suite:** `Test run with 51 tests in 12 suites passed` — incl. `BodyMapBindingTests` (5, GREEN this plan), `EditDeleteTests` (3, GREEN this plan), `CopyAuditTests` (6, cycle half GREEN this plan), `CycleMathTests` (9), `SymptomLoggingTests`, `DayNoteTests`, `SchemaMigrationTests`, `SchemaRoundTripTests`, etc.
- **XCUITest E2E (the phase gate):** all 4 methods GREEN — `ConsentFlowUITests.testConsentGateFlow`, `LoggingFlowUITests.testLoggingFlow` (full body-map+edit+delete flow), `testLogToday_savesAndAppearsInHistory`, `testSuppressedModeShowsNoCycleSummary`.
- **Full `xcodebuild test` (unit + UI):** `** TEST SUCCEEDED **` on the iPhone 16 Simulator.
- **Suites turned GREEN this plan:** BodyMapBindingTests, EditDeleteTests, the cycle half of CopyAuditTests, and the full LoggingFlowUITests (testLoggingFlow + testSuppressedModeShowsNoCycleSummary).
- **Screenshots:** 10 PNGs in `/tmp/e2e` (consent flow 01–04, logging 01–06 incl. suppressed-home).
- **Grep guards:** no `.deny` delete rule; no single-hard-date cycle formatter (only the RANGE `Date.FormatStyle`); no forbidden verb/gendered token in audited cycle copy; `assertConsented()` on `setSuppressed`.

## Known Stubs
- `CycleSummary.phase` remains intentionally `nil` (Phase 6 / INST-02 populates named phases — Open Question 2). The summary surfaces cycle-day + next-period range + confidence without it; not goal-blocking and documented in 02-03.
- The derived-cycle rendering (cycle-day + date RANGE + confidence) is proven by `CycleMathTests` at the logic level and by the `CycleSummaryView` three-state structure; the E2E exercises the suppressed and sparse states (the two honesty-critical paths). The derived path is reachable in-app once >=2 cycles of bleeding history exist (human-check on the simulator).

## Human-Check (phase-gate visual review)
Per the plan's `human_verify_mode=end-of-phase`, review `/tmp/e2e/*.png`: logging-03-editor (body map + bleeding + note), logging-04-history, logging-05-after-delete (row gone), logging-02-suppressed (toggle ON), logging-06-suppressed-home (cycle summary shows only "cycle tracking is off"). With >=2 cycles of bleeding history logged, Home shows a cycle-day + a next-period date RANGE + a confidence word (never a single 28-based date); with <2 cycles it shows "not enough data yet".

## Next Phase Readiness
- All 8 Phase-2 requirements GREEN end-to-end: SYMP-01/02/03/04 + CYCL-01/02/03/04. The two endo-credibility signals — the accessible discrete-region body map and the no-phantom-cycle summary — are delivered.
- The phase E2E (incl. suppressed-mode) is GREEN with screenshots. Phase 2 is feature-complete; ready for `/gsd-verify-work` and Phase 4 (sync) seams remain untouched (`SyncMode.localOnly`).
- No blockers.

## Self-Check: PASSED

Both created files exist on disk (`Sources/EndoApp/Features/DayLog/BodyMapView.swift`, `Sources/EndoApp/Features/Home/CycleSummaryView.swift`); all three task commits (`06b1bc2`, `26938a6`, `2a0e403`) are present in git history; the full `xcodebuild test` suite (51 unit + 4 E2E) is GREEN.

---
*Phase: 02-core-symptom-cycle-bleeding-logging*
*Completed: 2026-06-08*
