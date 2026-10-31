---
phase: 18-room-persistence
plan: 04
subsystem: insightsbridge
tags: [android, insightsbridge, snapshot-projector, pure-function, parity-suite, consent-gate, privacy, nil-honest, junit4]

# Dependency graph
requires:
  - phase: 18-room-persistence
    plan: 03
    provides: "consent-gated EndoRepository facade (checkConsented first), DAOs with all*/latestTreatment reads, RepositoryConsentTest fakes"
  - phase: 18-room-persistence
    plan: 02
    provides: "10 Room entities (SymptomReading/PainPoint/BleedingEvent/FactorReading/ImportedSample/DayEntry/TreatmentState) with nullable raw+health-value columns, epochDay Long keys"
  - phase: 17-insights-engine-port
    plan: "*"
    provides: ":insights DaySnapshot + SymptomPoint/PainPoint + the 7 catalog enums with fromRaw/fromRawOrNull"
provides:
  - "insightsbridge/SnapshotProjector — the ONLY persistence↔domain seam: pure Room rows -> List<DaySnapshot> fold (union-of-days via LocalDate, manual-wins/nil-honest merge, hasNote presence-only, suppressed stamping)"
  - "insightsbridge/FactorKind — app-local 7-raw enum (food/activity/sleep/medication/stress/mood/other) with defensive fromRaw -> OTHER"
  - "insightsbridge/ImportedSignals + HealthDomain — per-day imported fold (sleep SUM->coarse band, workouts SUM minutes + longest-session type, cycle/nutrition single-valued, unknown domain -> nothing)"
  - "EndoRepository.daySnapshots() — the consent-gated projection read path (APER-05)"
  - "SnapshotProjectorTest — 33 pure-JVM cases (24-case iOS parity port + imported-merge/ImportedSignals/FactorKind)"
affects: [21-health-import, 22-insights-ui, 23-export-import]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SnapshotProjector is a pure object (no DB, no coroutines): Room entity params in, :insights DaySnapshot out — the single seam where persistence + domain meet"
    - "Day bucketing via entity epochDay -> LocalDate.ofEpochDay (never epoch-millis division; Pitfall 5)"
    - "Manual-wins/nil-honest merge: manual present -> manual; else imported present -> imported; else null (activity type+minutes one unit)"
    - "Strict symptom/pain decode (drop on unknown/nil raw) vs OTHER-sink food/activity (fromRaw); optional flow/region/intensity/sleep via fromRawOrNull"
    - "Free text never crosses: DayEntry.note -> hasNote:Boolean; note-bearing .food row dropped"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/insightsbridge/FactorKind.kt
    - android/app/src/main/kotlin/com/endoapp/app/insightsbridge/ImportedSignals.kt
    - android/app/src/main/kotlin/com/endoapp/app/insightsbridge/SnapshotProjector.kt
    - android/app/src/test/kotlin/com/endoapp/app/insightsbridge/SnapshotProjectorTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt

key-decisions:
  - "HealthDomain ported app-locally in ImportedSignals.kt (the Android :insights module does not carry it; iOS HealthDomain imports HealthKit) — 4 raws cycle/sleep/workouts/nutrition, defensive fromRawOrNull -> null (row contributes nothing)"
  - "Coarse sleep-quality thresholds inlined from iOS HealthSampleMapping ([0,5)->POOR, [5,7)->OKAY, [7,inf)->GOOD) since :insights carries no HealthSampleMapping"
  - "enumParity_* cases re-expressed as Android round-trip guards (every catalog raw decodes back to itself via the projector's own decoder) since :app reuses the single :insights catalog (no second enum set to diff; the :app<->:insights raw drift is already Phase-17-guarded)"
  - "SnapshotProjectorTest kept as ONE named file at 33 @Test: the 24-case iOS parity port + 9 imported-merge/ImportedSignals/FactorKind cases that pin the Phase-21 merge inputs now"

requirements-completed: [APER-05]

# Metrics
duration: ~35min
completed: 2026-07-09
status: complete
---

# Phase 18 Plan 04: SnapshotProjector Bridge + Parity Suite Summary

**Built the ONLY persistence↔domain seam — `insightsbridge/SnapshotProjector`, a pure function folding Room row lists into `:insights` `List<DaySnapshot>` exactly like the iOS `daySnapshots()`: union-of-days by `epochDay` (via `LocalDate`, never epoch-millis math), symptom/pain rows dropped on unknown/nil raw (never fabricated OTHER), the manual-wins / nil-honest / absence≠0 merge per imported-capable field, `hasNote` presence-only (free-text prose NEVER crosses — the note-bearing `.food` row is dropped), and `suppressed` stamped on every snapshot. Added the app-local `FactorKind` (7 raws) + `ImportedSignals`/`HealthDomain` per-day fold (sleep SUM→coarse band, workouts SUM minutes keeping the longest session's type). Proven by a 33-case pure-JVM parity suite (the 24 iOS `DaySnapshotMappingTests` case-for-case + imported-merge cases) and wired behind the consent gate as `EndoRepository.daySnapshots()`. `:app:testDebugUnitTest` (41 tests, 0 failures) and `:app:assembleDebug` are green for real on this host.**

## Performance
- **Duration:** ~35 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 4 created, 2 modified

## Accomplishments
- **`SnapshotProjector` (pure seam):** `object SnapshotProjector.project(symptoms, pain, bleeding, factors, entries, imported, suppressed): List<DaySnapshot>` — row lists in, `DaySnapshot` out, no DB / no coroutine / no Android type in the fold. Union of `epochDay` keys across all day-scoped types + `noteDays` (DayEntry.note != null), sorted ascending, one snapshot per day via `LocalDate.ofEpochDay(key)`. Every iOS fold rule ported EXACTLY (makeDaySnapshot + projectFactors): symptoms `mapNotNull` with a STRICT decode (unknown/nil `typeRaw` DROPPED, never OTHER), pain via `BodyRegion.fromRawOrNull` (unknown DROPPED), flow via first `FlowIntensity.fromRawOrNull`-bearing event then manual-then-imported, `clots ?: false`, food via `FoodTag.fromRaw` (note-bearing `.food` row NOT carried; empty + imported `ateThatDay==true` → `[OTHER]`), activity type/minutes/intensity (level 0/1/2→low/med/high), sleep hours/quality, medications (guarded UUID parse, skip `"prn"`), `tookPRN`, stress/mood with nil≠0, `hasNote` presence-only, suppressed stamped.
- **Manual-wins / nil-honest merge:** per imported-capable field (flow, sleepHours, sleepQuality, activity type+minutes, food-presence) — manual present ⇒ manual; else imported present ⇒ imported; else null. Activity type+minutes are one unit (imported fills both only when manual type is absent); imported food contributes a single `[OTHER]` only when there are no manual tags.
- **`FactorKind` (app-local, 7 raws):** `food/activity/sleep/medication/stress/mood/other`, verbatim from the iOS `FactorKind.swift`; `fromRaw(null|unknown) → OTHER`; each raw round-trips. Lives in `:app`, cross-checked by the Phase-17 golden-JSON header.
- **`ImportedSignals` + `HealthDomain` (per-day imported fold):** SLEEP = SUM of the night's segment rows, then coarse `SleepQuality` from the SUMMED total ([0,5)→POOR, [5,7)→OKAY, [7,∞)→GOOD); WORKOUTS = SUM minutes keeping the LONGEST session's `ActivityType` (deterministic); CYCLE/NUTRITION single-valued; unknown `HealthDomain` raw contributes nothing (defensive, never crashes). Shape matters for Phase 21 but is defined + pinned now.
- **`EndoRepository.daySnapshots()` (APER-05):** `checkConsented()` FIRST, then fetch all day-scoped row lists (`allSymptoms/allPain/allBleeding/allFactors/all()/allImported`), derive `suppressed` from `latestTreatment()?.modeRaw == "suppressed"`, and delegate to `SnapshotProjector.project`. Room entities never escape — only `DaySnapshot` values cross out.
- **33-case parity suite (`SnapshotProjectorTest`):** the 24 iOS `DaySnapshotMappingTests` cases ported case-for-case with hand-built entity instances (no DB) — 2 bucketing, 2 symptom, 2 pain, 3 bleeding, 2 stress/mood honesty, 1 factor-fields, 1 hasNote, 1 suppressed, 1 defensive-decode, 2 symptom-drop, 7 enum-parity — plus 9 imported-merge / ImportedSignals (sleep-SUM, workout-longest, unknown-domain) / FactorKind / medication / note-drop cases.

## Task Commits
1. **Task 1: FactorKind (7 raws) + ImportedSignals per-day fold** — `c761fcc` (feat)
2. **Task 2: SnapshotProjector pure fold + parity suite (33 cases)** — `81115c2` (feat)
3. **Task 3: consent-gated EndoRepository.daySnapshots() + W1 test fold-in** — `91de29a` (feat)

_No STATE.md / ROADMAP.md metadata commit — the orchestrator owns state updates for this run (per objective)._

## Real Build/Test Results (this host, Android SDK platform 36 present)
- `./gradlew :app:testDebugUnitTest --tests "*SnapshotProjectorTest"` — BUILD SUCCESSFUL.
- Case-count gate: `python3 … re.findall('@Test')` → **35** (33 real `@Test` methods + 2 `@Test` mentions in the class KDoc); assertion `n>=24` passes.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL. Full unit suite, 41 tests, 0 failures:
  - `SnapshotProjectorTest` **33/33** (0 failures)
  - `RepositoryConsentTest` **3/3** — now also pins `daySnapshots()` throwing under `ConsentState.Unknown` AND `NotConsented`, not throwing under `Consented` (W1 fold-in)
  - Plan-02/03 tests still green: `CascadeCompletenessTest` 2/2, `DestructiveMigrationBanTest` 1/1, `SchemaExportTest` 1/1, `ModuleSmokeTest` 1/1 (no regression).
- `grep -q "checkConsented" EndoRepository.kt` → present (daySnapshots gates first).

## Deviations from Plan

### Auto-fixed / design adaptations

**1. [Rule 3 - Missing referenced type] `HealthDomain` ported app-locally into `ImportedSignals.kt`**
- **Found during:** Task 1.
- **Issue:** The plan's read-list points at the iOS `HealthDomain.swift` and the `:insights` `SnapshotEnums.kt`, but `:insights` does NOT carry `HealthDomain` (the iOS enum imports HealthKit and vends `HK*` read types — platform-specific, deliberately not in the pure engine module). The `ImportedSignals` fold switches on it.
- **Fix:** Added a minimal app-local `HealthDomain` enum (4 raws `cycle/sleep/workouts/nutrition`, raw values verbatim from the Swift case names) with a defensive `fromRawOrNull` in `ImportedSignals.kt`. Only the 4 raw discriminators are ported — the `HK*` read-type sets are Phase-21 concerns and are omitted here.
- **Files:** `ImportedSignals.kt`.

**2. [Rule 3 - Missing referenced type] Coarse sleep-quality thresholds inlined**
- **Issue:** iOS `ImportedSignals` derives the band via `HealthSampleMapping.sleepQuality(forAsleepHours:)`, which `:insights` does not carry.
- **Fix:** Inlined the documented iOS thresholds verbatim ([0,5)→POOR, [5,7)→OKAY, [7,∞)→GOOD, boundary lands on the upper band) as private constants in `ImportedSignals`, pinned by `importedSignals_sleepSumsSegments_andBandsFromTotal`.
- **Files:** `ImportedSignals.kt`.

**3. [Discretion] `enumParity_*` re-expressed as Android round-trip guards**
- **Issue:** The iOS `enumParity_X` cases diff the `EndoApp.X` catalog raws against the `EndoInsights.X` raws (two enum sets). On Android `:app` REUSES the single `:insights` catalog — there is no second set to diff, and the `:app`↔`:insights` raw-drift concern is already guarded by Phase-17's `EnumRawValueParityTests` + golden JSON.
- **Fix:** Each of the 7 `enumParity_X` cases asserts that every catalog entry's raw decodes back to itself via the SAME decoder the projector uses (`fromRaw` for the OTHER-sink catalogs, `fromRawOrNull` for the optional ones) — a meaningful faithfulness guard that a future case whose raw does not round-trip would fail.
- **Files:** `SnapshotProjectorTest.kt`.

**4. [Discretion] 9 extra cases beyond the 24 fold into the one named suite**
- Added imported-only-day, manual-wins-over-imported, imported `ateThatDay`→OTHER (only when no manual food), ImportedSignals sleep-SUM/workout-longest/unknown-domain, FactorKind defensive round-trip, medication UUID+prn, and note-bearing-food-row-dropped cases to `SnapshotProjectorTest` (total 33). This exercises `FactorKind` + `ImportedSignals` behaviorally (Task 1's TDD coverage) inside the projector suite rather than a separate file; the `n>=24` gate still passes.

## Threat Model Compliance
- **T-18-09 (Info disclosure — free-text prose crossing into analysis, high):** mitigated — the projector carries `hasNote: Boolean` only; `noteBearingFoodRow_isDropped_prosNeverCrosses` + `hasNote_reflectsPresenceOnly` pin that `DayEntry.note` and the note-bearing `.food` row never cross (only presence).
- **T-18-06 (DoS — unknown/corrupt raw crashing the projection, medium):** mitigated — every decode is defensive (`SymptomType` strict-drop, `BodyRegion/FlowIntensity/.../fromRawOrNull`, `FoodTag/ActivityType/FactorKind.fromRaw` OTHER-sink, guarded `UUID.fromString`, `HealthDomain.fromRawOrNull`→null); `unknownRaws_decodeDefensively_noCrash`, `unknownSymptomType_isDropped_notFabricatedAsOther`, `importedSignals_unknownDomain_contributesNothing`, `factorKind_defensiveDecode_andRoundTrip` pin it. No throw path.
- **T-18-10 (Tampering — nil→0 coercion in the fold, high):** mitigated — no `?: 0` on health values; day bucketing via `LocalDate.ofEpochDay`, never epoch math; `unloggedStressAndMood_areNil_notZero` + `loggedStressZero_isZero_notNil` pin absence≠0 (missing stress/mood → null, logged 0 → 0).

## Next Plan Readiness
- Phase 21 (Health import) consumes `ImportedSignals`/`HealthDomain` (the per-day fold shape defined here) and fills `ImportedSample.hcRecordId`/`domainRaw`.
- Phase 22 (insights UI) consumes `EndoRepository.daySnapshots()` → `:insights` engine.
- Phase 23 (export/import) round-trips the same entity identity behind the same consent gate.
- No blockers.

## Self-Check: PASSED
All 4 created files (`FactorKind.kt`, `ImportedSignals.kt`, `SnapshotProjector.kt`, `SnapshotProjectorTest.kt`) and the 2 modified files (`EndoRepository.kt`, `RepositoryConsentTest.kt`) exist on disk; the 3 task commits (`c761fcc`, `81115c2`, `91de29a`) are in git history; `:app:testDebugUnitTest` (41 tests, 0 failures incl. `SnapshotProjectorTest` 33/33 and `RepositoryConsentTest` 3/3) and `:app:assembleDebug` verified green.

---
*Phase: 18-room-persistence*
*Completed: 2026-07-09*
