# :insights Swift → Kotlin Parity Ledger

**Phase 17 exit criterion.** This ledger maps every Swift test file in
`EndoInsights/Tests/EndoInsightsTests/` to its Kotlin mirror in
`android/insights/src/test/kotlin/com/endoapp/insights/`, tracking the `@Test`
case count on both sides. The phase is "proven equivalent" only when the Kotlin
column reaches the Swift column for every row and the total lands at **157**.

## Parity methodology

- **Count basis:** `grep -c "@Test" <file>` on the Swift side (line-based, the
  verifier's mechanism) vs the JUnit XML `testcase` count on the Kotlin side.
- **All 157 Swift cases are plain `@Test`** (zero `@Test(arguments:)` parameterized
  suites — verified by grep), so the mapping is a straight 1:1 `@Test → @Test`.
- **SmokeTests grep quirk (documented, not a defect):** `SmokeTests.swift` greps as
  **2** because a doc-comment line also contains the string "@Test" (1 real `@Test`
  method + 1 comment mention). The Kotlin `SmokeTests.kt` intentionally carries **2**
  real `@Test` methods so the JUnit XML count matches the ledger's Swift column.
  The real Swift `@Test`-*method* total is **156**; the grep-based parity total is **157**.
- **Tolerance parity (stats):** `lgamma`/`erf` have no Kotlin/JVM stdlib equivalent and
  are hand-rolled, so Fisher-exact and Mann-Whitney parity is **tolerance-based, not
  bit-exact**, gated against Swift/libm golden values. This is documented in **plan 17-03**
  (SpecialFunctions + CorrelationStats) and the `stats-goldens.json` fixture header.

## Ported test files (Swift ⇔ Kotlin) — the 157-case parity target

Two rows are **fixture-only** (0 cases) and must be ported before the files that read them.

| Swift file | Swift @Test | Kotlin file | Kotlin count | Plan | Status |
|------------|------------:|-------------|-------------:|------|--------|
| `SnapshotFixtures.swift` | 0 | `SnapshotFixtures.kt` | — | 17-02 | ✅ done |
| `SeriesTestSupport.swift` | 0 | `SeriesTestSupport.kt` | — | 17-05 | pending |
| `SmokeTests.swift` | 2 | `SmokeTests.kt` | 2 | 17-01 | ✅ done |
| `BoundaryLintTests.swift` | 1 | `BoundaryLintTests.kt` | 1 | 17-01 | ✅ done |
| `InsightCodableTests.swift` | 6 | `InsightCodableTests.kt` | 6 | 17-02 | ✅ done |
| `CorrelationStatsTests.swift` | 26 | `CorrelationStatsTests.kt` | 26 | 17-03 | ✅ done |
| `TrendsEngineTests.swift` | 11 | `TrendsEngineTests.kt` | 11 | 17-04 | ✅ done |
| `CyclePhaseMapperTests.swift` | 8 | `CyclePhaseMapperTests.kt` | 8 | 17-04 | ✅ done |
| `SuppressedFixtureTests.swift` | 2 | `SuppressedFixtureTests.kt` | 2 | 17-04 | ✅ done |
| `ChartScrubSelectionTests.swift` | 7 | `ChartScrubSelectionTests.kt` | pending | 17-05 | pending |
| `ExploreSeriesCatalogTests.swift` | 9 | `ExploreSeriesCatalogTests.kt` | pending | 17-05 | pending |
| `ExploreSeriesExtractionTests.swift` | 10 | `ExploreSeriesExtractionTests.kt` | pending | 17-05 | pending |
| `ScatterPairingTests.swift` | 7 | `ScatterPairingTests.kt` | pending | 17-05 | pending |
| `SeriesNormalizationTests.swift` | 6 | `SeriesNormalizationTests.kt` | pending | 17-05 | pending |
| `CorrelationEngineTests.swift` | 12 | `CorrelationEngineTests.kt` | pending | 17-06 | pending |
| `RecommendationsEngineTests.swift` | 7 | `RecommendationsEngineTests.kt` | pending | 17-07 | pending |
| `FlareEngineTests.swift` | 6 | `FlareEngineTests.kt` | pending | 17-07 | pending |
| `HonestyGateTests.swift` | 16 | `HonestyGateTests.kt` | pending | 17-08 | pending |
| `InsightsEngineTests.swift` | 14 | `InsightsEngineTests.kt` | pending | 17-08 | pending |
| `ContractTests.swift` | 7 | `ContractTests.kt` | pending | 17-08 | pending |
| **TOTAL** | **157** | | **56** | | |

Kotlin count so far: **56** (SmokeTests 2 + BoundaryLintTests 1 + InsightCodableTests 6 + CorrelationStatsTests 26
+ TrendsEngineTests 11 + CyclePhaseMapperTests 8 + SuppressedFixtureTests 2).
SnapshotFixtures.kt (0-case shared builder) is ported and available to plans 03–08.

## Additional guard tests (no Swift mirror)

These Kotlin tests have **no Swift counterpart** and are **NOT** part of the 157 count.
They are standing structural / cross-platform-drift guards.

| Kotlin guard test | Purpose | Req | Plan | Status |
|-------------------|---------|-----|------|--------|
| `VersionCatalogAuditTests.kt` | Fails the build if any analytics/auth/backend/networking coordinate enters `gradle/libs.versions.toml` (zero-dependency supply-chain guard) | AENG-07 | 17-01 | ✅ done |
| `EnumRawValueParityTests.kt` | Swift-source scan (A) + golden JSON (B): fails on enum raw-value divergence vs `SnapshotEnums.swift` / `CyclePhase.swift` (2 tests, both proven red-capable) | AENG-05 | 17-02 | ✅ done |
| `ClaimsPolicyDriftTests.kt` | Scans `ClaimsPolicy.swift`: fails if the app's forbidden-substring lists drift out of the Kotlin `ForbiddenTerms` superset | AENG-05 | 17-08 | pending |
| `NumericDivergenceTests.kt` | NaN / ±0.0 / rounding ties / Mann-Whitney tied ranks (17-03, 7 tests ✅) + DST/non-UTC day boundaries (17-08) | AENG-06 | 17-03, 17-08 | 🟡 stats/boundary half done; DST half pending 17-08 |
| `SpecialFunctionsGoldenTests.kt` | Pins the hand-rolled `lgamma`/`erf`/`normalCDF` (Lanczos g=7 / NR3 Chebyshev) against Swift/libm golden values (`stats-goldens.json`) within relative 1e-9 — the AENG-06 tolerance gate (T-17-07) | AENG-06 | 17-03 | ✅ done |

## Notes

- `FactorKind` is **deliberately not ported** into `:insights` — it is an app-side
  Phase-18 persistence discriminator (research assumption A1). Its 7 raw values
  (`food`, `activity`, `sleep`, `medication`, `stress`, `mood`, `other`) will be
  recorded in the golden-JSON header (plan 17-02) so a Phase-18 app-side drift test
  can adopt it later.
- Day identity is `java.time.LocalDate` throughout (the injected Swift `Calendar` is
  dropped per the locked decision); the DST/non-UTC fixture (17-08) proves no
  `Instant`/epoch math leaked in.
