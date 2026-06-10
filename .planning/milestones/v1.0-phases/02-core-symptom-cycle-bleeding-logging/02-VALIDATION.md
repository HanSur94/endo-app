---
phase: 02-core-symptom-cycle-bleeding-logging
nyquist_compliant: true
source: 02-RESEARCH.md §"Validation Architecture"
generated: 2026-06-08
framework: Swift Testing (unit) + XCTest/XCUITest (E2E), Xcode 26.5 / iOS 26.5 SDK
destination: "platform=iOS Simulator,name=iPhone 16"
---

# Phase 2 Validation Plan (Nyquist)

Every Phase-2 requirement (SYMP-01..04, CYCL-01..04) plus the cross-cutting copy audit and the A1 schema-migration guard maps to a named Swift Testing suite or XCUITest, each created RED in Wave 0 (Plan 02-01 Task 1) and turned GREEN by the owning plan/task. No requirement ships without an automated proof (Nyquist: every `<verify>` has an `<automated>`).

## Test Framework

| Property | Value |
|----------|-------|
| Unit framework | Swift Testing (`@Suite`/`@Test`/`#expect`/`#require`) — `Tests/EndoAppTests/*` |
| E2E framework | XCTest/XCUITest — `Tests/EndoAppUITests/*` (the `-uitest-reset` + a11y-id harness from Phase 1) |
| Config | `project.yml` (XcodeGen); `EndoAppTests` (bundle.unit-test) + `EndoAppUITests` (bundle.ui-testing), both in the EndoApp scheme; sources are folder-globbed (no target edit per new file — A4) |
| Quick run | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -only-testing:EndoAppTests/<Suite> -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Full suite | `xcodegen generate && xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

## Requirement → Test → Plan/Task Map

| Req ID | Behavior under test | Test type | Suite / file | Owning plan·task | Wave-0 RED |
|--------|--------------------|-----------|--------------|------------------|------------|
| SYMP-01 | `SymptomReading` (type + 0–10 severity) round-trips; absence ≠ "0/healthy" | unit | `EndoAppTests/SymptomLoggingTests` | 02-01·T3 (persistence) + 02-02·T2 (UI write) | 02-01·T1 |
| SYMP-02 | Tapping a `BodyRegion` → `PainPoint(region, severity)` (logic-level, no render); update-not-duplicate; unknown raw safe | unit | `EndoAppTests/BodyMapBindingTests` | 02-04·T1 | 02-01·T1 |
| SYMP-03 | Day note persists and is length-bounded at the write seam; empty → nil | unit | `EndoAppTests/DayNoteTests` | 02-02·T1 (Repository seam) | 02-01·T1 |
| SYMP-04 | Edit updates a day's records; delete removes the day + cascades children; cycle recomputes | unit + E2E | `EndoAppTests/EditDeleteTests` + `EndoAppUITests/LoggingFlowUITests` | 02-04·T1 (unit) + 02-04·T2 (E2E) | 02-01·T1 |
| CYCL-01 | `BleedingEvent` (flow + optional `clots`) persists as a first-class event; no cycle field touched | unit | `EndoAppTests/SchemaRoundTripTests` (extended) | 02-01·T3 | 02-01·T1 (extends existing) |
| CYCL-02 | Regular fixture → median length + a sensible RANGE; **irregular** fixture → wider range / lower confidence; never 28 | unit | `EndoAppTests/CycleMathTests` | 02-03 (TDD) → surfaced 02-04·T2 | 02-01·T1 |
| CYCL-03 | Suppressed flag → `isSuppressed`, NO cycleDay/phase/prediction (no-phantom-cycle proof); UI hides all cycle output | unit + E2E | `EndoAppTests/CycleMathTests` (suppressed fixture) + `EndoAppUITests/LoggingFlowUITests/testSuppressedModeShowsNoCycleSummary` | 02-03 (unit) + 02-04·T2 (E2E) | 02-01·T1 |
| CYCL-04 | Sparse (<2 cycles) → `hasEnoughData == false` ("not enough data yet"); enough → optional cycleDay + confidence | unit | `EndoAppTests/CycleMathTests` | 02-03 → surfaced 02-04·T2 | 02-01·T1 |
| (cross) | New copy passes `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens`; no hard single date | unit | `EndoAppTests/CopyAuditTests` | 02-01·T2 (labels) + 02-04·T2 (cycle copy) | 02-01·T1 |
| (A1 guard) | V1 store survives the lightweight **V1→V2 migration** with no data loss; new models + `clots` round-trip | unit | `EndoAppTests/SchemaMigrationTests` (+ `SchemaRoundTripTests`) | 02-01·T3 | 02-01·T1 |
| (E2E happy path) | Consent → Log today → symptom+severity → body region+severity → bleeding → note → save → appears in history → edit → delete → gone | E2E | `EndoAppUITests/LoggingFlowUITests/testLoggingFlow` | 02-02·T2 (early activities) + 02-04·T2 (full GREEN) | 02-01·T1 |

## Required Cycle Fixtures (CycleMathTests — the keystone)

| Fixture | Shape | Asserted outcome |
|---------|-------|------------------|
| `regularCycler_medianLength_sensibleRange` | ≥3 period runs ~28–30 days apart, low spread | `hasEnoughData`, positive `cycleDay`, non-empty `nextPeriod` range, `confidence == .high` |
| `irregularCycler_widerRange_lowerConfidence` | gaps ~24/35/27/40 days | `hasEnoughData`, `nextPeriod` range WIDER than regular, `confidence` strictly < `.high` |
| `suppressed_emitsNothing` | `isSuppressed == true` (any history) | `isSuppressed`, `cycleDay == nil`, `phase == nil`, `nextPeriod == nil`, `confidence == .none`, `hasEnoughData == false` |
| `sparse_underTwoCycles_notEnoughData` | < 2 start-to-start gaps | `hasEnoughData == false`, `cycleDay == nil`, `nextPeriod == nil`, `confidence == .none` |
| edges | empty input; single bleeding day; consecutive days collapse to one run; never-cycled-then-suppressed | guarded paths return `.notEnoughData`/`.suppressed`; no crash; deterministic with injected `today` |

## Sampling Rate

- **Per task commit:** `xcodebuild test … -only-testing:EndoAppTests/<suite>` for the suite the task touches (the per-task `<verify><automated>` in each plan).
- **Per wave merge:** all unit suites `… -only-testing:EndoAppTests` (fast, in-memory).
- **Phase gate:** full suite (all unit + both XCUITest E2E methods) GREEN on the iPhone 16 Simulator before `/gsd-verify-work`; E2E screenshots written to `/tmp/e2e/*.png` via the existing `saveScreenshot` helper.

## Structural Grep Gates (run over `Sources/EndoApp/**`, comments stripped)

| Gate | Rule | Rationale |
|------|------|-----------|
| No phantom cycle | no stored `cycleDay`/`cyclePhase`/`cycleLength` and no `= 28` / `28`-as-cycle-default | Pitfall 1 / T-02-05 / T-02-10 |
| CloudKit-safe schema | no `@Attribute(.unique)` on any `@Model` | Pitfall 2 / T-02-01 |
| Lightweight migration only | no `MigrationStage.custom` in `Persistence/**` | Pitfall 3 / T-02-02 |
| No `.deny` relationships | no `.deny` delete rule introduced | CloudKit rule / RESEARCH Pattern 4 |
| Pure domain | no `import SwiftData` / `import SwiftUI` under `Sources/EndoApp/Domain/**` | CycleMath portability / testability |
| No health-value logging | no `print`/log of symptom/severity/region/flow/note values | Security V7 / T-02-09 / T-02-12 |
| No premature sync | no iCloud/CloudKit/Background-Modes capability added to `project.yml`; `SyncMode.localOnly` unchanged | 5.1.3(ii) / T-02-04 |

## Wave 0 Gaps (created RED in Plan 02-01 Task 1)

- [ ] `Tests/EndoAppTests/CycleMathTests.swift` — regular / irregular / suppressed / sparse + edges (→ GREEN in 02-03)
- [ ] `Tests/EndoAppTests/SymptomLoggingTests.swift` — SYMP-01 round-trip + "absence ≠ 0/healthy" (→ GREEN in 02-01·T3)
- [ ] `Tests/EndoAppTests/BodyMapBindingTests.swift` — SYMP-02 region→PainPoint binding (→ GREEN in 02-04·T1)
- [ ] `Tests/EndoAppTests/DayNoteTests.swift` — SYMP-03 note persistence + length bound (→ GREEN in 02-02·T1)
- [ ] `Tests/EndoAppTests/EditDeleteTests.swift` — SYMP-04 edit/delete + cascade (→ GREEN in 02-04·T1)
- [ ] `Tests/EndoAppTests/CopyAuditTests.swift` — cross-cutting claims/terminology audit (labels GREEN 02-01·T2; cycle copy GREEN 02-04·T2)
- [ ] `Tests/EndoAppTests/SchemaMigrationTests.swift` + extend `SchemaRoundTripTests.swift` — CYCL-01 `clots` + new models + V1→V2 data-survival (→ GREEN in 02-01·T3)
- [ ] `Tests/EndoAppUITests/LoggingFlowUITests.swift` — full E2E + suppressed-mode no-cycle (early activities GREEN 02-02·T2; full GREEN 02-04·T2)
- [ ] No framework install needed — Swift Testing + XCUITest already wired (no Package Legitimacy Gate; first-party only).
