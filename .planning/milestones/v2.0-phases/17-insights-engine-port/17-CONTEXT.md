# Phase 17: Insights Engine Port - Context

**Gathered:** 2026-07-08
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure/engine-port phase — decisions pre-locked in ANDROID-PORT-PLAN.md)

<domain>
## Phase Boundary

Establish the `android/` Gradle monorepo workspace and port the pure-Swift `EndoInsights` package to a pure-Kotlin-JVM `:insights` module, proven equivalent to the shipped Swift engine by a case-count-parity test suite. Delivers AENG-01..07. No Android/Compose/Room code, no UI — this module never touches an emulator.

</domain>

<decisions>
## Implementation Decisions

All architectural choices are LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §1 & §3 and pinned to versions in [.planning/research/STACK.md](../../research/STACK.md) / [.planning/research/ARCHITECTURE.md](../../research/ARCHITECTURE.md). Do not re-open them. Key locked decisions for this phase:

### Workspace & build
- Monorepo: the Android app lives in `android/` in this repo; iOS sources (`Sources/`, `EndoInsights/`, `project.yml`) stay untouched.
- `:insights` module uses `kotlin("jvm")` ONLY — no Android Gradle plugin, no `android.`/`androidx.` imports (enforced by a boundary lint test).
- Version catalog `android/gradle/libs.versions.toml` is the zero-dependency audit surface. Pins: Kotlin 2.3.21, Gradle 9.4.1+, JDK 21 toolchain, kotlinx-serialization-json 1.11.0, JUnit Jupiter 6.1.1. A test fails if any analytics/auth/backend/networking coordinate appears in the catalog.
- Path-filtered CI (`.github/workflows/android.yml`, `android/**`) runs the `:insights` JVM job as a merge gate. iOS tree stays read-only (drift scans read it, never write).

### Engine port
- Port the domain enums verbatim by raw value: SymptomType (11), BodyRegion (10), FactorKind, FoodTag, ActivityType (5), ActivityIntensity (3), SleepQuality, FlowIntensity (4). Kotlin enums carry an explicit `raw: String` matching the Swift `rawValue` exactly.
- Port the value types (`DaySnapshot`, insight types) with the nil-means-unlogged honesty contract intact (absence ≠ 0); make NaN unrepresentable at the `DaySnapshot` boundary.
- Port all six engines behind the single HonestyGate choke point: TrendsEngine, CyclePhaseMapper, CorrelationEngine (BH-FDR, Fisher exact, Mann-Whitney U with tie correction, Cliff's delta), RecommendationsEngine, FlareEngine — plus ForbiddenTerms. One public producer function (InsightsEngine).
- `Factor`'s associated values map to a sealed interface with the same `signalKey` fragments as Swift.

### Testing & parity
- Test framework: JUnit Jupiter 6.1.1; use `@ParameterizedTest` to preserve Swift Testing's per-argument case counts.
- Port the full Swift test suite (~157 cases across 18 files) file-for-file; track case-count parity in a committed `PARITY.md` ledger (the phase exit criterion).
- Include the honesty acceptance fixtures: suppressed-user silence, under-sample withholding, BH-FDR multiple-comparison flood, absence-vs-zero distinctions.
- Add a numeric-divergence fixture pack (Pitfall from research): NaN, ±0.0, rounding ties, tied ranks (Mann-Whitney), and DST/non-UTC day-boundary math (run in a non-UTC test zone). `LocalDate`-only day identity.
- Cross-platform drift tests: (a) a JVM test regex-scans the Swift sources (`SnapshotEnums.swift`, `Factor.swift`, `ClaimsPolicy.swift` — assumption-guarded, zero iOS changes) to catch enum-raw-value / ClaimsPolicy-substring divergence, plus (b) an unconditional golden JSON. Drift must be red, not a convention.

### Numeric-parity guardrails (research Pitfall)
- Watch Kotlin↔Swift divergence: `Double.equals` (NaN==NaN true, 0.0≠-0.0), total-ordered NaN in `compareTo` (affects Mann-Whitney ranks), unstable primitive `DoubleArray.sort()`, rounding tie-breaks. Ban `String.format`/number formatting without an explicit `Locale`.

### Claude's Discretion
- Exact Gradle module file layout, package names under `com.endoapp.insights`, and test-file organization — at Claude's discretion, guided by the ARCHITECTURE.md realization and the Swift source structure. Prefer file-for-file correspondence with `EndoInsights/Sources/EndoInsights/` to make parity auditable.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- `EndoInsights/Sources/EndoInsights/` — 19 Swift source files (~3,570 LOC): the six engines, HonestyGate, ForbiddenTerms, Insight/Factor/DaySnapshot/SnapshotEnums value types, CyclePhase, ExploreSeries*, SeriesNormalization, ScatterPairing, ChartScrubSelection.
- `EndoInsights/Tests/EndoInsightsTests/` — ~18 Swift test files (~157 cases) incl. honesty acceptance fixtures and the BH-FDR flood test.
- Domain enums live in `Sources/EndoApp/Domain/` (SymptomType, FactorKind, FoodTag, ActivityType, ActivityIntensity, SleepQuality, FlowIntensity, BodyRegion) and `EndoInsights` `SnapshotEnums.swift`; `Content/ClaimsPolicy.swift` holds the forbidden-substring lists.

### Established patterns to mirror
- One-way dependency: `:app` → `:insights`, never reverse. `:insights` knows nothing of Android.
- Single HonestyGate choke point + one public producer — the "bias to silence" discipline is architecturally central.

### Integration points
- Downstream Phase 18 `SnapshotProjector` will target this module's `DaySnapshot` contract — keep it stable and platform-neutral.

</code_context>

<specifics>
## Specific Ideas

Mirror the Swift source file structure file-for-file so the PARITY.md ledger maps 1:1 (Swift file → Kotlin file → test file → case count). The exit bar is countable parity, not "looks equivalent."

</specifics>

<deferred>
## Deferred Ideas

None — later phases (persistence, UI, Health Connect, charts, export, PDF, Play prep) are already scoped as Phases 18–25.

</deferred>
