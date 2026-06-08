---
phase: 06-cycle-phase-mapping-insight-honesty-layer
verified: 2026-06-09T00:25:00Z
status: passed
score: 12/12 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: none
  note: "First verification. 06-REVIEW.md (2 Critical CR-01/CR-02 + warnings WR-01/WR-02) was an adversarial code review, not a prior VERIFICATION.md. The CR-fixes (commits c51f216, 94212ba, 5b5d60e) are confirmed REAL in source + test below."
---

# Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer Verification Report

**Phase Goal:** Add symptom-to-cycle-phase mapping to the engine + build the cross-cutting insight-honesty layer (min-sample gating, confidence indicator, non-causal language, dismissal) that every present and future insight must pass through.
**Verified:** 2026-06-09T00:25:00Z
**Status:** passed
**Re-verification:** No — initial verification (post code-review-fix)

## Goal Achievement

This is a pure-logic + persistence phase; the live dismiss button and rendering are correctly deferred to Phase 8. Every automatable must-have is verified by reading the actual source AND running the test suites in this verifier's own process (not trusting SUMMARY claims). The two Critical honesty-gate bypasses found in 06-REVIEW.md are confirmed FIXED end-to-end.

### Observable Truths

| #  | Truth (ROADMAP SC / plan must-have) | Status | Evidence |
| -- | ----------------------------------- | ------ | -------- |
| 1  | SC1 — engine maps phases when a reliable cycle exists; OMITS phases for suppressed/irregular/under-sampled (no invented phase) | ✓ VERIFIED | `CyclePhaseMapper.swift:41` suppressed `guard` → `[]`; `:49` `gaps.count >= 2` → `[]`; `:56` `phaseConfidence(...) == nil` for spread>9 → `[]`. Fixtures (`SnapshotFixtures.swift:178/195/83`) and `CyclePhaseMapperTests` assert each yields ZERO insights. Suite GREEN. |
| 2  | Phase confidence NEVER exceeds `.medium` | ✓ VERIFIED | `phaseConfidence` (`CyclePhaseMapper.swift:123`) returns only `.medium`/`.low`/`nil` — no `.high` path. `regularCycle_emitsCyclePhaseInsights_neverHighConfidence` asserts `confidence <= .medium`. GREEN. |
| 3  | SC2 — every emitted insight carries a confidence indicator and non-causal language | ✓ VERIFIED | `Insight.confidence: InsightConfidence` is a stored field (`Insight.swift:72`). Copy is "tended to co-occur with" (`CyclePhaseMapper.swift:260`). `allEmittedInsights_haveZeroForbiddenTerms` scans ALL emitted insights across 9 fixtures. GREEN. |
| 4  | SC3 / INST-04 (a) — under-sampled findings WITHHELD by a LIVE shared min-sample gate (CR-01 fix) | ✓ VERIFIED | `HonestyGate.apply` → `sampleAndFloorFilter` drops `insight.sampleSize < minSample(for: insight.kind)` (`HonestyGate.swift:93`). `minSample` is no longer dead code (IN-01 closed). Producer emits `sampleSize: logged` (`CyclePhaseMapper.swift:266`), so an n=1 cyclePhase association (1 < 2) is dropped. `underSampledCyclePhaseAssociation_isWithheldByGate` GREEN. |
| 5  | INST-04 (b) — forbidden-term scan WITHHOLDS causal-term insights in RELEASE, not just debug assert (CR-02 fix) | ✓ VERIFIED | `causalTermFilter` (`HonestyGate.swift:110-125`) returns `false` (drops) in ALL build configs; the `assertionFailure` is additive-only. `runtimeForbiddenTermInDetail_isWithheld_inGatedOutput` exercises `applyReleaseScan` (assert-free path) and asserts the gated output is empty. GREEN. |
| 6  | INST-04 (c) — confidence-floor carve-out keys on a TYPED `isInsufficiencyNotice` marker, not a forgeable string suffix (WR-01 fix) | ✓ VERIFIED | `Insight.isInsufficiencyNotice: Bool` stored (`Insight.swift:89`); carve-out keys on `insight.isInsufficiencyNotice` (`HonestyGate.swift:88,93,98`). `forgedInsufficientSuffix_withoutTypedMarker_doesNotBypassFloor` proves a key ending `.insufficient` WITHOUT the marker is withheld. GREEN. |
| 7  | INST-04 (d) — a non-causal forbidden-term test scans ALL emitted insights | ✓ VERIFIED | `allEmittedInsights_haveZeroForbiddenTerms` (`HonestyGateTests.swift:135-164`) iterates `InsightsEngine.insights(...)` over 9 fixture streams and `#expect`s `ForbiddenTerms.violation(in: headline/detail) == nil` per insight. GREEN. |
| 8  | No Phase-5 regression — no valid trend dropped by the new sampleSize gate; trend sampleSize ≥ minSample(.trend)=3 | ✓ VERIFIED | Every valid `TrendsEngine` insight sets `sampleSize` to a self-gated count `>= minLoggedDays(3)` (frequency `daysWithAnyLog`, severity/scaleMean `count`, sleep `sleepDays`, bleeding `total`). `noRegression_insightsAreSupersetOfRawTrends` asserts `InsightsEngine ⊇ TrendsEngine.trends`; `validTrend_atMinSample_isNotDroppedByGate` (sampleSize 3) survives. GREEN. |
| 9  | SC4 — DismissedInsight CloudKit-safe (optional/defaulted, no `.unique`, app-gen UUID); signalKey carries no health prose | ✓ VERIFIED | `DismissedInsight.swift:30-42`: `id: UUID = UUID()`, `signalKey: String? = nil`, `dismissedAt: Date = Date()`, all defaulted, no `@Attribute(.unique)`, no relationships, only category signalKey + timestamp. |
| 10 | SC4 — SchemaV4 lightweight V3→V4 migration with a survival test | ✓ VERIFIED | `SchemaV4` additive (V3 set + `DismissedInsight.self`); `v3toV4 = MigrationStage.lightweight` (`MigrationPlan.swift:38`); container bumped to `SchemaV4.self` (`ModelContainer+Config.swift:51`). `v3Store_survivesLightweightMigrationToV4_withNoDataLoss` writes real V3 rows, reopens via the plan, asserts every row survives + DismissedInsight queryable. GREEN. |
| 11 | SC4 — consent-gated dismissal CRUD; engine excludes dismissed keys | ✓ VERIFIED | `dismissInsight`/`dismissedSignalKeys` (`Repository.swift:556-569`) both call `assertConsented()` first, append-only insert, dedupe-on-read via `Set`. `HonestyGate` drops `dismissed.contains(insight.signalKey)` (`HonestyGate.swift:84`). `RepositoryDismissalTests` + `dismissal_excludesExactlyThatSignal_andStaysExcluded` GREEN. |
| 12 | Determinism + SC1 — package Foundation-only, deterministic output | ✓ VERIFIED | No non-Foundation import in any package source (grep clean); `BoundaryLintTests` enforces it. Injected `today`/`calendar`, deterministic UUID derivation. `determinism_sameInputs_equalInsights` (both suites) GREEN. |

**Score:** 12/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `EndoInsights/.../Insight.swift` | stored `signalKey` + `sampleSize` + typed `isInsufficiencyNotice` | ✓ VERIFIED | All three are `public let` stored fields, threaded through `derived(...)`; not folded into id derivation (Pitfall 4 preserved). |
| `EndoInsights/.../CyclePhase.swift` | CyclePhase enum | ✓ VERIFIED | `public enum CyclePhase: String, Sendable, Equatable, CaseIterable` (4 cases). |
| `EndoInsights/.../CyclePhaseMapper.swift` | suppressed/<2-cycle/irregular gates, sampleSize emission | ✓ VERIFIED | All gates return `[]`; emits `sampleSize: logged`; WR-05 overlap guard returns `[]`. |
| `EndoInsights/.../HonestyGate.swift` | LIVE min-sample + fail-closed forbidden-term + typed carve-out | ✓ VERIFIED | `minSample` now called; `causalTermFilter` drops in all builds; carve-out on typed marker. |
| `EndoInsights/.../ForbiddenTerms.swift` | causal-verb mirror + connectives | ✓ VERIFIED | Mirrors ClaimsPolicy verbs + D-08 connectives + WR-03 inflections. |
| `EndoInsights/.../InsightsEngine.swift` | single public composition root → gate | ✓ VERIFIED | `public enum InsightsEngine`; composes trends + cyclePhase, routes through `HonestyGate.apply`. |
| `EndoInsights/.../TrendsEngine.swift` | demoted to internal, sampleSize on every emit | ✓ VERIFIED | `enum TrendsEngine` (no `public`); every emit sets self-gated `sampleSize >= 3`; notEnoughData sets `isInsufficiencyNotice: true`. |
| `Sources/.../DismissedInsight.swift` | CloudKit-safe @Model | ✓ VERIFIED | All-optional/defaulted, no `.unique`, app-gen UUID. |
| `Sources/.../SchemaV4.swift` | additive VersionedSchema | ✓ VERIFIED | V3 set + DismissedInsight only delta. |
| `Sources/.../MigrationPlan.swift` | lightweight v3toV4 | ✓ VERIFIED | `MigrationStage.lightweight`, never `.custom`. |
| `Sources/.../Repository.swift` | consent-gated dismissal CRUD | ✓ VERIFIED | Both methods assertConsented-first, append-only, dedupe-on-read. |
| Tests (cyclePhase/honesty/engine/migration/dismissal) | INST-02/INST-04 acceptance | ✓ VERIFIED | 47 package tests + 6 app dismissal/migration tests GREEN (run by verifier). |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `InsightsEngine.insights` | `HonestyGate.apply` | mandatory choke-point | ✓ WIRED | `InsightsEngine.swift:38`. |
| `InsightsEngine.insights` | `CyclePhaseMapper.insights` | composition | ✓ WIRED | `InsightsEngine.swift:37`. |
| `HonestyGate.apply` | `minSample(for:)` | LIVE min-sample backstop (CR-01) | ✓ WIRED | `HonestyGate.swift:93` — no longer dead code. |
| `HonestyGate.apply` | `ForbiddenTerms.violation` | fail-closed drop (CR-02) | ✓ WIRED | `HonestyGate.swift:112` drops in all builds. |
| `HonestyGate.apply` | `Insight.signalKey` | dismissal-exclusion | ✓ WIRED | `HonestyGate.swift:84` `dismissed.contains`. |
| `CyclePhaseMapper.associations` | `Insight.derived(sampleSize:)` | per-association evidence count | ✓ WIRED | `CyclePhaseMapper.swift:266` `sampleSize: logged`. |
| `Repository.dismissInsight` | `context.insert(SchemaV1.DismissedInsight)` | append-only write | ✓ WIRED | `Repository.swift:561`. |
| `makeModelContainer` | `SchemaV4` | current-schema bump | ✓ WIRED | `ModelContainer+Config.swift:51`. |

### Behavioral Spot-Checks (run by verifier)

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Full package honesty/cyclephase/no-regression suite | `xcodebuild test -only-testing:EndoInsightsTests` | "Test run with 47 tests in 8 suites passed" | ✓ PASS |
| Cycle-phase honesty gates (INST-02) | suite "INST-02 CyclePhaseMapper" | passed | ✓ PASS |
| Honesty gate CR-fixes (INST-04) | suite "INST-04 HonestyGate" | passed (incl. under-sampled withhold, release fail-closed, typed carve-out, zero-forbidden-terms) | ✓ PASS |
| Composition + Phase-5 no-regression + dismissal | suite "INST-04 InsightsEngine" | passed | ✓ PASS |
| SC1 boundary | suite "D-12 package import boundary" | passed | ✓ PASS |
| V3→V4 migration survival + dismissal round-trip | `xcodebuild test -only-testing:EndoAppTests/SchemaMigrationTests -only-testing:EndoAppTests/RepositoryDismissalTests` | "Test run with 6 tests in 2 suites passed" | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| INST-02 | 06-01 | User can see how symptoms map onto cycle phases | ✓ SATISFIED | CyclePhaseMapper + CyclePhaseMapperTests; rendering is Phase 8 (correctly out of scope). Engine complete. |
| INST-04 | 06-02, 06-03 | Every insight carries an honesty layer — non-causal language, confidence indicator, dismissal, low-confidence withheld | ✓ SATISFIED | HonestyGate (LIVE min-sample, fail-closed term scan, confidence floor, dismissal-exclusion) + DismissedInsight persistence + consent-gated CRUD. Live dismiss button/wiring is Phase 8. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | No TBD/FIXME/XXX in any Phase-6 source file | — | — |
| `HonestyGate.swift` | 30-32 | "placeholder" in doc comment | ℹ️ Info | Documents the A3 future-phase `minSample` values (`.correlation`/`.recommendation`/`.flare`); the enum cases return real, used integers — not stub code. Not a debt marker. |

### Outstanding Review Items (non-blocking)

The 2 Critical bypasses (CR-01, CR-02) and the two backing warnings (WR-01 typed marker, WR-02 sampleSize backstop) from 06-REVIEW.md are all FIXED and test-proven (truths 4-6, 8). Remaining 06-REVIEW items are advisory and do not block the phase goal:

- **WR-04** (entity-name assertion): the V3→V4 survival test round-trips real V3 data through a reopen and asserts every row survives — if the namespace had orphaned V3 rows into a distinct entity, those assertions would FAIL. The stricter explicit entity-name assertion is a hardening nicety, not a correctness gap. Data-survival is genuinely proven.
- **WR-03, WR-05** are implemented (expanded causal list with tests; explicit order-independent overlap guard with `windowsOverlap` + `shortOverlappingCycleFixture` test). Verified present.
- **IN-01** (dead `minSample`) is now CLOSED — `minSample` is the live backstop (truth 4).

### Human Verification Required

None. This is a pure-logic + persistence phase with no device/UI behavior in scope. The live dismiss button and insight rendering are explicitly Phase 8. All in-scope behavior is deterministically test-covered and was executed by the verifier.

### Gaps Summary

No gaps. All 4 ROADMAP Success Criteria and both requirement IDs (INST-02, INST-04) are satisfied in the actual codebase. The honesty gate — the safety control this phase exists for — was adversarially confirmed: (a) the min-sample backstop is live and withholds n=1 cyclePhase associations, (b) the forbidden-term scan fails closed in release, (c) the confidence-floor carve-out keys on a typed marker, (d) the zero-forbidden-terms test scans all emitted insights. No Phase-5 regression (superset test + sampleSize≥3 confirmed in source and test). Dismissal persistence is CloudKit-safe, consent-gated, with a real V3→V4 survival test. Package stays Foundation-only and deterministic. 47 package tests + 6 app dismissal/migration tests GREEN, run in this verifier's own process.

---

_Verified: 2026-06-09T00:25:00Z_
_Verifier: Claude (gsd-verifier)_
