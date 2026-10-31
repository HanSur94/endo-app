---
phase: 17-insights-engine-port
plan: 02
subsystem: insights-engine
tags: [kotlin, jvm, domain-enums, value-types, sealed-interface, deterministic-uuid, fnv-1a, kotlinx-serialization, drift-test, parity, android-port]

# Dependency graph
requires:
  - ":insights pure-Kotlin-JVM module (kotlin(jvm), jvmToolchain(21), useJUnitPlatform())"
  - "Pinned zero-dependency version catalog (kotlinx-serialization-json 1.11.0, JUnit Jupiter 6.1.1)"
  - "BoundaryLintTests + VersionCatalogAuditTests standing guards"
provides:
  - "8 domain enums with verbatim raw:String (SymptomType 11, BodyRegion 10, FlowIntensity 4, FoodTag 9, ActivityType 5, ActivityIntensity 3, SleepQuality 3, CyclePhase 4) + PhaseDay"
  - "Defensive decoders (fromRaw sink→OTHER / fromRawOrNull sink→null) — never throw (ASVS V5)"
  - "Factor sealed interface (9 key fragments) + FactorState + stateOf resolvers + hasAnyLog + thresholds"
  - "DaySnapshot + SymptomPoint + PainPoint (LocalDate identity, NaN-unrepresentable boundary)"
  - "Insight + InsightKind/TrendWindow/InsightConfidence/InsightPoint + DeterministicUUID (FNV-1a, ULong wrapping, RFC-4122 v5)"
  - "SnapshotFixtures.kt — the shared fixture builder the whole downstream suite reads"
  - "EnumRawValueParityTests (drift A Swift-scan + drift B golden JSON) + enum-raw-values.golden.json"
affects: [17-03, 17-04, 17-05, 17-06, 17-07, 17-08, 18-persistence]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Swift String enum → Kotlin enum with idiomatic UPPER_SNAKE names + load-bearing raw:String"
    - "Swift enum-with-associated-values → Kotlin sealed interface (data object / data class)"
    - "NaN-unrepresentable factory: private ctor + companion of() canonicalizes non-finite→null, -0.0→0.0"
    - "Swift &*/&+ wrapping arithmetic → Kotlin ULong (silent wrap) for bit-for-bit DeterministicUUID"
    - "Cross-platform drift = code: Swift-source regex scan (assumption-guarded) + unconditional golden JSON"
    - "Custom KSerializer<UUID>/<LocalDate> (kotlinx has no built-in) for round-trip-stable storage"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/SnapshotEnums.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/CyclePhase.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/Factor.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/DaySnapshot.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/Insight.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/SnapshotFixtures.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/InsightCodableTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/EnumRawValueParityTests.kt
    - android/insights/src/test/resources/enum-raw-values.golden.json
  modified:
    - android/insights/PARITY.md

key-decisions:
  - "DaySnapshot uses a private constructor + companion of() factory (not a data class) so NaN is unrepresentable at the boundary; equals/hashCode written by hand over all 17 fields"
  - "DeterministicUUID lanes use Kotlin ULong (not signed Long) to reproduce Swift's wrapping &*/&+ bit-for-bit; UUID built preserving Swift's b0..b15 byte order (msb=b0..b7, lsb=b8..b15)"
  - "InsightPoint.date ported as LocalDate (not a timestamp) — consistent with the locked LocalDate day-identity; InsightCodableTests are Kotlin-independent round-trips (OQ3), not cross-platform byte compares"
  - "FactorKind NOT ported (research A1 / orchestrator scope) — its 7 raw values recorded in the golden-JSON header for the future Phase-18 drift test"
  - "Factor/FactorState/DeterministicUUID kept internal (mirror Swift's non-public access); enums + DaySnapshot + Insight are public"

requirements-completed: []  # AENG-01 + AENG-05 mechanisms landed for the vocabulary/read-model layer, but both are phase-spanning (full 157-parity + all engines remain); orchestrator/verifier owns final marking.

# Metrics
duration: 15min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 02: Domain Vocabulary + Read-Model Layer Summary

**The 8 domain enums, Factor sealed interface, DaySnapshot (NaN-unrepresentable), and Insight (with bit-for-bit DeterministicUUID) ported verbatim from the Swift EndoInsights sources — locked against drift by a Swift-source scan plus an unconditional golden JSON, with the shared SnapshotFixtures builder the rest of the suite reads.**

## Performance

- **Duration:** ~15 min (first task commit 09:09:52, last 09:18:07)
- **Completed:** 2026-07-09
- **Tasks:** 3 (all atomic-committed) + 1 ledger commit
- **Files created:** 9; modified: 1 (PARITY.md)

## Accomplishments

- **8 domain enums** (`SnapshotEnums.kt` 7 + `CyclePhase.kt`) with idiomatic UPPER_SNAKE case names but **verbatim `raw:String`** values matching the Swift case names exactly (the parity contract). Defensive decoders per the parity table: `fromRaw` sinks unknown/null → `OTHER` for SymptomType/FoodTag/ActivityType; `fromRawOrNull` returns `null` for BodyRegion/FlowIntensity/ActivityIntensity/SleepQuality/CyclePhase — **never throw** (ASVS V5). `PhaseDay(day: LocalDate, phase)` ported.
- **`Factor` sealed interface** with all 9 `key` fragments matching the signalKey contract (`food.<raw>`, `activity.present`, `activity.<raw>`, `sleep.poor`, `stress.high`, `mood.low`, `medication.taken`, `prn.taken`, `cyclePhase.<raw>`); `FactorState` three-state; both `stateOf` resolvers (with/without phase); `hasAnyLog`; thresholds copied **verbatim** as `const` (`highStressThreshold=7`, `lowMoodThreshold=2`, `perGroupMinimum=3`).
- **`DaySnapshot`** (+ `SymptomPoint`, `PainPoint`) — LocalDate day identity, `List<UUID>`, ~17 fields with the absence≠0 honesty contract. **NaN is unrepresentable** at the boundary: the `of()` factory canonicalizes non-finite `Double?` → `null` and `-0.0` → `0.0`; hand-written `equals`/`hashCode` dodge the boxed-Double equality pitfalls.
- **`Insight`** + `InsightKind`(5) + `TrendWindow`(4) + `InsightConfidence`(Int-backed, ordinal==value Comparable) + `InsightPoint`, all `@Serializable`. **`DeterministicUUID`** reproduces Swift's FNV-1a two-lane derivation **bit-for-bit** using `ULong` wrapping arithmetic and RFC-4122 v5/variant stamping, building `java.util.UUID` in Swift's exact byte order. Custom `UuidSerializer`/`LocalDateSerializer` give round-trip-stable JSON.
- **`SnapshotFixtures.kt`** — the full 0-case shared builder (21 fixture functions incl. the SC3 suppressed keystone, BH-FDR flood with ULong splitmix, cycle/flare/correlation fixtures) ported with `LocalDate` (the injected Swift `Calendar` dropped per the locked decision). Available to plans 03–08.
- **Drift is code, not convention:** `EnumRawValueParityTests` runs drift **A** (regex-scans `SnapshotEnums.swift` + `CyclePhase.swift`, asserts Kotlin `raw` lists equal the Swift `case` lists in declaration order, assumption-guarded) and drift **B** (`enum-raw-values.golden.json`, unconditional). **Both proven red-capable** (mutating one raw value fails both).

## Task Commits

1. **Task 1: 7 domain enums + CyclePhase + enum drift guards** — `6c6401c` (feat)
2. **Task 2: Factor sealed interface + DaySnapshot (NaN-unrepresentable)** — `aa4e930` (feat)
3. **Task 3: Insight + DeterministicUUID + Codable round-trip + fixtures** — `2c89fcf` (feat)
4. **PARITY ledger update (Kotlin 3 → 9)** — `07395f7` (docs)

## Real Test Results

`cd android && ./gradlew clean :insights:test` — **BUILD SUCCESSFUL**, 12 tests, 0 skipped, 0 failures:

| Test file | tests | notes |
|-----------|------:|-------|
| `InsightCodableTests` | 6 | round-trip equality + stored-id preserved (never re-derived on decode) |
| `EnumRawValueParityTests` | 2 | drift A (Swift-source scan, resolved & ran — not skipped) + drift B (golden JSON) |
| `SmokeTests` | 2 | (17-01) |
| `BoundaryLintTests` | 1 | (17-01) still green — new main sources import only `java.time`/`java.util.UUID`/`kotlin.*`/`kotlinx.serialization` |
| `VersionCatalogAuditTests` | 1 | (17-01) |

Red-capability confirmed for the AENG-05 drift guard: temporarily changing `PELVIC_PAIN("pelvicPain")` → `("pelvicPainBROKEN")` failed BOTH `kotlinRawValuesMatchSwiftSources()` and `liveEnumsMatchCommittedGoldenJson()`, then restored to green.

## PARITY.md Updates

- `SnapshotFixtures.swift` → `SnapshotFixtures.kt`: **✅ done** (0-case builder)
- `InsightCodableTests.swift` (6) → `InsightCodableTests.kt` (6): **✅ done**
- Guard `EnumRawValueParityTests.kt` (drift A+B, 2 tests, red-capable): **✅ done**
- Kotlin running total **3 → 9** of 157.

## Decisions Made

- **`DaySnapshot` is a hand-written class, not a `data class`** — a private constructor + `companion of()` factory is the only way to enforce NaN-unrepresentable at the boundary; `equals`/`hashCode`/`toString` are written over all 17 fields.
- **`ULong` for DeterministicUUID** — Swift `&*`/`&+` wrap silently; Kotlin `ULong` `*`/`+` wrap identically, so the derivation is bit-for-bit. Signed `Long` would diverge. The `java.util.UUID` is assembled preserving Swift's `UUID(uuid:)` b0..b15 layout (msb=b0..b7, lsb=b8..b15, big-endian), so `toString()` matches Swift's `uuidString` — parity-critical for Phase-18 dismissal survival.
- **`InsightPoint.date: LocalDate`** — consistent with the locked zone-free day identity; the codable tests assert round-trip equality only (OQ3: Swift's 2001-epoch Date encoding is not portable, so no cross-platform byte compare).
- **Enum name style** — Kotlin cases are idiomatic UPPER_SNAKE (`PELVIC_PAIN`), decoupled from the load-bearing `raw` ("pelvicPain"); the drift tests guard the `raw`, not the Kotlin name.

## Deviations from Plan

### Documentation adjustment

**1. [Orchestrator directive] Updated PARITY.md's Kotlin column this plan**
- **Plan text said:** "PARITY.md untouched this plan (reconciled in 17-08); note coverage in the SUMMARY."
- **Orchestrator objective said:** "Update the Kotlin column of PARITY.md for the test files this plan lands."
- **Resolution:** Followed the orchestrator's explicit, more-recent directive — marked the three 17-02 rows done and bumped the Kotlin total 3 → 9 (commit `07395f7`). This is additive and does not touch the Swift column or the 157 target; full reconciliation still lands in 17-08.

No Rule 1/2/3 auto-fixes were needed — the plan's verify steps ran as written against the 17-01 scaffold. No architectural changes, no scope creep. No auth gates.

## Known Stubs

None. All ported symbols are fully implemented (no TODO/FIXME/placeholder markers in the new sources).

## Self-Check: PASSED

All 9 created files + this SUMMARY exist on disk; all 4 commits (`6c6401c`, `aa4e930`, `2c89fcf`, `07395f7`) are present in git history; `./gradlew clean :insights:test` is green (12/12).

---
*Phase: 17-insights-engine-port*
*Completed: 2026-07-09*
