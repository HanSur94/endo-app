# Architecture Research

**Domain:** Native Android port (Kotlin + Jetpack Compose) of a shipped privacy-first iOS health tracker — v2.0 milestone, `android/` monorepo directory
**Researched:** 2026-07-08
**Confidence:** HIGH (concrete realization of the locked module layout in ANDROID-PORT-PLAN.md §3; grounded in the shipped iOS codebase inventory + current first-party Android docs)

**Scope note:** The module layout (`:app` + pure-Kotlin-JVM `:insights`, one-way dependency, `insightsbridge` seam, manual DI) is **locked** in ANDROID-PORT-PLAN.md §3 and is NOT re-opened here. This document answers only: *how do we realize it concretely?*

---

## Standard Architecture

### System Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│  android/app  (module :app — Android, Compose)                         │
│                                                                        │
│  MainActivity (single activity, setContent { BloomTheme { AppGate } }) │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ AppGate (composable when-switch on ConsentState)                 │  │
│  │  ├─ Unknown      → blank/splash (DataStore first emission)       │  │
│  │  ├─ NotConsented → OnboardingScreen (disclaimer + privacy +      │  │
│  │  │                 explicit consent action)                      │  │
│  │  └─ Consented    → ConsentedShell: Scaffold + NavigationBar      │  │
│  │       tabs: Log · Insights · Explore  (ConsentedTab enum state)  │  │
│  │       — the ONLY branch that composes any health UI              │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ui/ (screens)   designsystem/ (Bloom)   report/ (PdfDocument)         │
│        │                                        │                      │
│        ▼                                        ▼                      │
│  ┌──────────────────┐   ┌──────────────────────────────────────────┐   │
│  │ InsightsCoordi-  │   │ data/ EndoRepository (consent-gated:     │   │
│  │ nator (app-      │◄──│ every health read/write checks consent   │   │
│  │ scoped, State-   │   │ FIRST) → Room DAOs (10 entities)         │   │
│  │ Flow<UiState>)   │   │ ConsentStore → Preferences DataStore     │   │
│  └───────┬──────────┘   │ (consent flag OUTSIDE the health DB)     │   │
│          │              └───────────────┬──────────────────────────┘   │
│          │ withContext(Dispatchers      │                              │
│          │ .Default)  ← Task.detached   │ health/ HealthConnect-       │
│          │ analog                       │ Importer (READ-only client,  │
│          ▼                              │ ImportedSample provenance)   │
│  ┌──────────────────────────────┐       │                              │
│  │ insightsbridge/              │◄──────┘                              │
│  │ SnapshotProjector: Room rows │                                      │
│  │ → List<DaySnapshot>          │  ← the ONLY file(s) where Room       │
│  │ (union-of-days + manual-wins │    entity types and :insights        │
│  │  merge, iOS daySnapshots())  │    domain types both appear          │
│  └──────────┬───────────────────┘                                      │
├─────────────┼──────────────────── one-way Gradle dependency ───────────┤
│             ▼                                                          │
│  android/insights  (module :insights — kotlin("jvm") ONLY)             │
│                                                                        │
│  DaySnapshot · sealed Factor · enums (raw-value parity w/ Swift)       │
│  TrendsEngine · CyclePhaseMapper · CorrelationEngine (BH-FDR, Fisher,  │
│  Mann-Whitney U, Cliff's δ) · RecommendationsEngine · FlareEngine      │
│  HonestyGate (single choke point) · ForbiddenTerms                     │
│  ExploreSeries/Extraction · ScatterPairing · SeriesNormalization ·     │
│  ChartScrubSelection  (chart data-prep, consumed by :app Phase 6)      │
│                                                                        │
│  test/: ported EndoInsights suite (file-for-file, ~157 case parity)    │
│         + boundary lint (no android./androidx. imports)                │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | New/Modified | Responsibility | Concrete Implementation |
|-----------|--------------|----------------|-------------------------|
| `MainActivity` | NEW | Single activity; hosts the whole app; receives reminder-notification deep-link intents and only flips a non-sensitive router flag | `ComponentActivity`, `enableEdgeToEdge()`, `setContent { BloomTheme { AppGate() } }` |
| `AppGate` (composable) | NEW (port of iOS `AppGate.swift`) | Consent-by-construction routing: health composables exist ONLY in the `Consented` branch | Plain `when (consentState)` — no navigation library (see Pattern 1) |
| `ConsentStore` | NEW (port of iOS `ConsentStore` on UserDefaults) | Consent flag persisted OUTSIDE the health DB; exposes `StateFlow<ConsentState>` | Preferences DataStore + `stateIn(appScope)`; 3-state `Unknown/NotConsented/Consented` |
| `EndoRepository` | NEW (port of iOS `Repository.swift`, 36 `assertConsented` call sites) | The ONLY health-data access path; consent check is the first statement of every health read/write; owns `deleteDay` cascade | Kotlin class wrapping Room DAOs; `checkConsented()` throws `IllegalStateException` pre-consent |
| Room schema (10 entities) | NEW | Local persistence mirroring the 10 SwiftData models by name | Room 2.8.x entities + DAOs, `@Transaction` cascade, UUID string PKs (see Room section) |
| `SnapshotProjector` (`insightsbridge/`) | NEW (port of iOS `daySnapshots()` + `DaySnapshotMappingTests`, 24 cases) | Room rows → `List<DaySnapshot>`; union-of-day-buckets; manual-wins/nil-honest merge; suppressed flag stamping | Pure function over fetched row lists; only file importing both Room entities and `:insights` types |
| `InsightsCoordinator` | NEW (port of iOS `InsightsCoordinator.swift`) | Fingerprint-checked recompute of gated insights off the main thread; cache replace-all; `StateFlow<InsightsUiState>` | App-scoped class; engine call inside `withContext(Dispatchers.Default)` (Task.detached analog) |
| `:insights` engines | NEW (verbatim port of 19 Swift files) | All analysis; one public producer behind `HonestyGate` | Kotlin-stdlib-only (`kotlin("jvm")`, `java.time` allowed — it's JDK, not Android) |
| `HealthConnectImporter` (`health/`) | NEW | Read-only Health Connect pulls for 4 record types → `ImportedSample` provenance rows | `androidx.health.connect:connect-client`; runtime availability gating (Android 14 built-in vs installable) |
| `designsystem/` (Bloom) | NEW | Bloom tokens/typography/components over Material 3 | `BloomTheme` wrapping `MaterialTheme` with custom color/type objects |
| `report/` | NEW | Vector PDF + share intent | `android.graphics.pdf.PdfDocument` + `FileProvider` + `ACTION_SEND` |
| `ClaimsPolicy` + `CopyAudit` (app `src/test`) | NEW (port; substring lists copied verbatim from iOS) | Copy gating with same-change registration rule | JVM test over a registered copy catalog + Swift-source drift scan (Pattern 4) |
| `.github/workflows/android.yml` | NEW (repo root — the only file added outside `android/`) | CI for the Android tree only, path-filtered | See CI section |
| iOS tree (`project.yml`, `Sources/`, `EndoInsights/`, `Tests/`) | **UNTOUCHED** | Port SOURCE only; drift tests read it, never write it | — |

---

## Recommended Project Structure

Concrete file-level realization of the locked §3 sketch (locked directories kept verbatim; files below are the recommendation):

```
android/
├── settings.gradle.kts              # include(":app", ":insights")
├── build.gradle.kts                 # root: plugin versions via catalog, no logic
├── gradle/libs.versions.toml        # THE audited dependency list (short by design)
├── gradle.properties                # AndroidX, Kotlin code style, JVM args
├── insights/
│   ├── build.gradle.kts             # plugins { kotlin("jvm") } — NO Android plugin
│   └── src/
│       ├── main/kotlin/com/endoapp/insights/
│       │   ├── DaySnapshot.kt       # + SymptomPoint, PainPoint (data classes)
│       │   ├── SnapshotEnums.kt     # SymptomType(11), BodyRegion(10), FoodTag,
│       │   │                        #   ActivityType, ActivityIntensity,
│       │   │                        #   SleepQuality, FlowIntensity — raw-value parity
│       │   ├── Factor.kt            # sealed interface Factor + FactorPresence + key strings
│       │   ├── Insight.kt           # Insight types + kotlinx-free JSON codec (see note)
│       │   ├── CyclePhase.kt / CyclePhaseMapper.kt
│       │   ├── CorrelationStats.kt  # Fisher exact, Mann-Whitney U, Cliff's δ, BH-FDR
│       │   ├── TrendsEngine.kt / CorrelationEngine.kt
│       │   ├── RecommendationsEngine.kt / FlareEngine.kt
│       │   ├── HonestyGate.kt / ForbiddenTerms.kt
│       │   ├── InsightsEngine.kt    # the ONE public producer function
│       │   ├── ExploreSeries.kt / ExploreSeriesExtraction.kt
│       │   ├── ScatterPairing.kt / SeriesNormalization.kt
│       │   └── ChartScrubSelection.kt
│       └── test/kotlin/…            # file-for-file mirror of EndoInsights/Tests (18 files)
│           ├── SnapshotFixtures.kt / SeriesTestSupport.kt   (fixtures, 0 cases)
│           ├── BoundaryLintTests.kt                          (source scan)
│           ├── EnumRawValueParityTests.kt                    (NEW — drift test, Pattern 4)
│           └── resources/enum-raw-values.golden.json         (generated once from Swift)
└── app/
    ├── build.gradle.kts             # com.android.application + compose + ksp
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # zero WRITE_* health permissions (auditable artifact)
        │   ├── res/xml/             # backup rules: EXCLUDE Room DB, allow DataStore prefs
        │   │   ├── data_extraction_rules.xml
        │   │   └── file_provider_paths.xml
        │   └── kotlin/com/endoapp/android/
        │       ├── App.kt                    # Application: builds AppDependencies
        │       ├── AppDependencies.kt        # manual composition root (iOS mirror)
        │       ├── MainActivity.kt
        │       ├── AppGate.kt                # + ConsentedShell + ConsentedTab enum
        │       ├── CheckInRouter.kt          # deep-link intent flag (non-sensitive)
        │       ├── data/
        │       │   ├── EndoDatabase.kt       # @Database(v1, exportSchema = true)
        │       │   ├── entities/             # 10 entity files, iOS model names
        │       │   ├── daos/                 # per-aggregate DAOs + DeleteDayDao
        │       │   ├── EndoRepository.kt     # consent-gated facade
        │       │   ├── ConsentStore.kt       # DataStore-backed
        │       │   └── ReminderSettingsStore.kt
        │       ├── insightsbridge/
        │       │   ├── SnapshotProjector.kt  # rows → DaySnapshot (daySnapshots() port)
        │       │   └── EnumBridge.kt         # entity raw String ↔ :insights enum
        │       ├── health/                   # HealthConnectImporter, mapping, anchors
        │       ├── ui/
        │       │   ├── onboarding/ home/ checkin/ daylog/ bodymap/
        │       │   ├── insights/  (incl. InsightsCoordinator.kt)
        │       │   ├── explore/ charts/      # Canvas chart layer (honesty rules)
        │       │   └── settings/
        │       ├── designsystem/             # BloomColors/Typography/Components/Theme
        │       ├── report/                   # ReportModel, PdfReportRenderer, ShareReport
        │       └── reminders/                # NotificationScheduler — physically separate
        │                                     #   from ui/insights (flare-scan boundary)
        ├── test/kotlin/…                     # JVM: CopyAuditTests, ClaimsPolicy drift scan,
        │                                     #   FlareNoNotification/NoAlarmVisual scans,
        │                                     #   SnapshotProjector mapping tests (fake rows),
        │                                     #   ReportModel tests, merge tests
        └── androidTest/kotlin/…              # instrumentation: Room migration tests,
                                              #   delete-day cascade on real SQLite,
                                              #   Compose consent-gate tests
```

### Structure Rationale

- **`insightsbridge/` stays tiny (2 files) on purpose** — it is the single Room↔domain meeting point, mirroring the iOS rule that only `Repository.daySnapshots()` touches both worlds. Everything in `ui/` consumes `DaySnapshot`/`Insight`, never entities.
- **`reminders/` physically separated from `ui/insights/`** — the flare no-notification invariant is enforced by a structural source scan (§5 of the port plan); a clean package boundary keeps the scan trivially greppable (scan `ui/insights` + `insightsbridge` + `:insights` for `NotificationManager`/`AlarmManager`/`NotificationCompat` imports).
- **`exportSchema = true` + committed `schemas/` dir from day one** — Room migration tests (`MigrationTestHelper`) need historical schema JSON; iOS learned migration discipline across SchemaV1–V6, Android starts the scaffold in Phase 2 even though v1 ships a single schema version.
- **Package base `com.endoapp.insights` vs `com.endoapp.android`** — the `:insights` namespace carries no platform hint, matching its portability contract; the boundary lint scans the whole `insights/src/main` tree.

---

## Architectural Patterns

### Pattern 1: Consent-by-construction gate WITHOUT a navigation library

**What:** No Navigation-Compose (Nav2) and no Navigation 3 in v1. `AppGate` is a plain composable `when`-switch; the consented shell is a Material 3 `Scaffold` + `NavigationBar` driven by a `ConsentedTab` enum in `rememberSaveable` state; modals (Check-In sheet, Day-Log editor, report sheet) are `ModalBottomSheet`/full-screen composables driven by plain state — exactly the shape of the shipped iOS app (AppGate switch + `TabView(selection:)` + sheets; there is no NavigationStack-heavy routing to port).

**Why this is the right call here (not just dependency minimalism):**
1. **A NavHost graph registers destinations as data.** With Nav2/Nav3 every health screen exists as a reachable route in a graph constructed before consent; consent becomes a *guard condition* you must remember on every destination and deep link. With the `when`-switch, health composables are **not composed and not constructible** outside the `Consented` branch — the invariant holds by construction, same as iOS, and the Phase 3 Compose test (`onNodeWithTag("tab.insights").assertDoesNotExist()` pre-consent) pins it.
2. **The iOS app's entire routing surface is:** gate switch, 3 tabs, a handful of sheets, one programmatic tab jump (scatter deep-link via `ConsentedTab` selection + router payload), one notification deep-link flag. That is state, not a graph.
3. **Deep links land ON the gate, never inside it** (locked invariant): `MainActivity.onNewIntent` sets `CheckInRouter.pendingCheckIn = true`; only `HomeScreen` (composed only post-consent) observes it. Identical to the iOS D-16 mechanism.

**Trade-offs:** No free back-stack save/restore or predictive-back animations between "screens" — irrelevant for a tab shell + sheets (system back dismisses sheets via `ModalBottomSheet`/`BackHandler`). **Escape hatch (record it):** if a future milestone adds real stacked navigation, adopt **Navigation 3** (stable 1.0, Nov 2025 — Compose-first, back stack is a plain observable list you own), *not* Nav2. Nav3's "you own the back-stack list" model composes cleanly with an outer consent switch.

**Example:**

```kotlin
@Composable
fun AppGate(consentStore: ConsentStore) {
    val state by consentStore.state.collectAsStateWithLifecycle()
    when (state) {
        ConsentState.Unknown      -> Box(Modifier.fillMaxSize()) // first DataStore emission (<1 frame)
        ConsentState.NotConsented -> OnboardingScreen()          // router flag deliberately NOT observed
        ConsentState.Consented    -> ConsentedShell()            // the ONLY health-UI branch
    }
}

@Composable
private fun ConsentedShell() {
    var selectedTab by rememberSaveable { mutableStateOf(ConsentedTab.Log) } // ephemeral, never persisted
    Scaffold(bottomBar = {
        NavigationBar {
            ConsentedTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = { Icon(tab.icon, null) },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag(tab.testTag), // "tab.insights" etc. — same ids as XCUITest
                )
            }
        }
    }) { padding ->
        when (selectedTab) {
            ConsentedTab.Log      -> HomeScreen(Modifier.padding(padding))
            ConsentedTab.Insights -> InsightsScreen(onSeeDays = { selectedTab = ConsentedTab.Explore })
            ConsentedTab.Explore  -> ExploreScreen(Modifier.padding(padding))
        }
    }
}
```

### Pattern 2: Room layering — entities → DAOs → consent-gated repository, Flow where it earns its keep

**What:** 10 `@Entity` classes named exactly like the SwiftData models (`DayEntry`, `SymptomReading`, `PainPoint`, `BleedingEvent`, `FactorReading`, `Medication`, `TreatmentState`, `ImportedSample`, `CachedInsight`, `DismissedInsight`); thin DAOs; one `EndoRepository` facade that is the only thing the rest of `:app` sees.

**Entity design rules (carry the iOS lessons):**
- **PK = app-generated UUID string** (`@PrimaryKey val id: String`), even though Room would allow autoincrement — the encrypted export format needs platform-neutral stable identity, and import is an idempotent upsert by this UUID (plan §2).
- **Day columns as `epochDay: Long`** (from `LocalDate.toEpochDay()`), indexed. Lossless, orderable, timezone-proof; convert to ISO-8601 only at the export-JSON boundary. Room `TypeConverter`s for `LocalDate`/`Instant`.
- **Enum-valued columns store the raw `String`** (the shared vocabulary, e.g. `"pelvicPain"`), never ordinals — parity with SwiftData storage and the export schema. Decode defensively via the `:insights` `fromRaw()` sinks (unknown → `OTHER`/null, never crash) at the bridge.
- **Use Room's freedoms deliberately:** non-null columns where iOS was forced optional by CloudKit, `@Index(unique = true)` on `ImportedSample.hcRecordUuid` (the Health Connect dedupe key — iOS had to do upsert-by-uuid in code; Android can let SQLite enforce it and still keep the code-level upsert).
- **No Room `@Relation`/foreign-key graph needed for v1** — the iOS models are flat, day-bucketed rows joined by date at projection time; mirror that. FKs only where they buy the cascade (they don't here: the cascade is by *day*, not by parent row — see below).

**`deleteDay` cascade (GDPR Art. 17, born complete):** one `@Transaction` DAO method that deletes from **every day-scoped table** by `epochDay` (`DayEntry`, `SymptomReading`, `PainPoint`, `BleedingEvent`, `FactorReading`, `ImportedSample` for that day) and leaves definitions (`Medication`, `TreatmentState`) untouched; plus invalidates `CachedInsight` (delete-all — the fingerprint recompute rebuilds). The Phase 2 instrumentation test inserts one row of *all 10 types* and asserts the exact survival set — that test is what makes the cascade "born complete", and it must fail if an 11th day-scoped entity is ever added without joining the cascade (drive the cascade test from a single canonical `DAY_SCOPED_ENTITIES` list asserted against `EndoDatabase.entities`).

**Observation strategy — mirror iOS semantics, don't cargo-cult Flow everywhere:**
- **One-shot suspend reads for projections:** `suspend fun daySnapshots(): List<DaySnapshot>` — the iOS dashboard/Explore/report all read the projection once per appearance; keep that contract so engine inputs are stable value lists.
- **`Flow<List<T>>` DAO queries only for live list UI:** the Home day-history list and the medications list genuinely benefit from Room's invalidation-tracker-backed Flows (`collectAsStateWithLifecycle`).
- **A change ticker for recompute:** `dao.observeDayScopedVersion(): Flow<Long>` (a cheap `SELECT COUNT + MAX(updatedAt)`-style query over day-scoped tables, or simply Room `InvalidationTracker` on those tables) debounced → tells `InsightsCoordinator` to run its fingerprint check. This replaces the iOS "recompute check on view appear" with something better-suited to Android process lifecycles, while the fingerprint short-circuit (unchanged data → no recompute) ports verbatim.

**Consent gate in the repository:** constructor takes `ConsentStore`; every health read/write starts with `checkConsented()` which reads the cached `StateFlow.value` and **throws** (`IllegalStateException`) when not `Consented` — a hard throw, not `assert`, because Android has no debug-only `assert` idiom that survives R8 the way Swift's `assert` works in debug; the JVM unit test for "repository throws pre-consent" ports the iOS `ConsentGateTests` directly.

**Room version choice (nuance, decide at Phase 2 start):** **Room 2.8.x** is the recommendation for this milestone. Room 3.0.0 went stable on **2026-07-01** (one week old today): Kotlin-generated, KSP2-only, coroutines-first, new `androidx.room3` package. Everything this app needs (suspend DAOs, Flow queries, `MigrationTestHelper`, exported schemas) is fully supported in 2.8.x, which has years of production hardening and is where all testing documentation points. Migrating later is mechanical (package rename; the DAOs are already suspend/Flow). Re-check at Phase 2 kickoff: if Room 3 has a patch release or two by then, upgrading the default is reasonable — record whichever is chosen in the phase plan. (2.x is officially in maintenance mode, so Room 3 is the eventual destination either way.)

### Pattern 3: Background insights recompute — `withContext(Dispatchers.Default)` as the `Task.detached` analog

**What:** Port `InsightsCoordinator` with the same shape: read snapshots → fingerprint check → engine compute off-main → gate → cache replace-all → publish UI state.

**Dispatcher mapping (the iOS ↔ Android concurrency Rosetta stone):**

| iOS mechanism | Android analog | Note |
|---|---|---|
| `Task.detached(priority: .userInitiated) { engine.compute(...) }` | `withContext(Dispatchers.Default) { InsightsEngine.compute(...) }` | `Default` = CPU-bound pool; the engine call is the ONLY thing moved off-main, same as iOS Pitfall-4 discipline |
| Main-actor repository reads | Room suspend DAOs (main-safe; Room hops to its own I/O executor) | No manual `Dispatchers.IO` needed around Room suspend calls |
| `Sendable` value types crossing the boundary | Immutable Kotlin data classes / `List` | `DaySnapshot` etc. are `val`-only data classes — safe by construction |
| `@Observable` coordinator state read by SwiftUI | `StateFlow<InsightsUiState>` + `collectAsStateWithLifecycle()` | Coordinator is a plain class, not a ViewModel (see below) |
| Coordinator lifetime = app composition root | `AppDependencies.applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` | Manual-DI mirror; one coordinator instance app-wide |

**Example (the load-bearing 15 lines):**

```kotlin
class InsightsCoordinator(
    private val repo: EndoRepository,
    private val scope: CoroutineScope,               // AppDependencies.applicationScope
) {
    private val _state = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
    val state: StateFlow<InsightsUiState> = _state

    fun refresh() { scope.launch { refreshInner() } }

    private suspend fun refreshInner() {
        val snapshots = repo.daySnapshots()                          // Room-managed I/O, main-safe
        val fingerprint = snapshotFingerprint(snapshots)
        val cached = repo.loadCachedInsights()
        if (fingerprint == repo.cachedFingerprint() && cached.isNotEmpty()) {
            _state.value = InsightsUiState.Ready(cached); return     // short-circuit, iOS-identical
        }
        val dismissed = repo.dismissedSignalKeys()
        val fresh = withContext(Dispatchers.Default) {               // ← Task.detached analog
            InsightsEngine.compute(snapshots, dismissed)             // pure, gated, stdlib-only
        }
        repo.replaceCachedInsights(fresh, computedAt = Instant.now(), fingerprint = fingerprint)
        _state.value = InsightsUiState.Ready(fresh)
    }
}
```

**Why a plain class and not a ViewModel:** manual DI is locked; the coordinator's cache lives in Room so process death costs nothing; per-screen ViewModels would fragment the single-recompute-authority the iOS design proved out. (androidx ViewModel is first-party and *allowed* — use it later only if config-change-scoped state actually appears; the Compose tab shell holds none.)

**Trade-off:** an app-scoped `CoroutineScope` keeps a recompute alive across screen changes (good — matches iOS behavior where the detached task completes) but you must not touch UI from it — publishing through `StateFlow` handles that.

### Pattern 4: The `insightsbridge` seam — projection + enum parity with drift tests

**What:** `SnapshotProjector` is the verbatim port of iOS `Repository.daySnapshots()` (union of start-of-day buckets across all day-scoped tables + imported samples; manual-wins/nil-honest merge; suppressed flag from latest `TreatmentState` stamped on every snapshot; `hasNote` presence-only — free text NEVER crosses into the analysis layer). It takes fetched row lists as parameters (pure function), so its 24-case mapping test suite (port of `DaySnapshotMappingTests`) runs as a **JVM** test with hand-built entity instances — no database, no emulator.

```kotlin
object SnapshotProjector {
    fun project(
        symptoms: List<SymptomReading>, pain: List<PainPoint>,
        bleeding: List<BleedingEvent>, factors: List<FactorReading>,
        entries: List<DayEntry>, imported: List<ImportedSample>,
        suppressed: Boolean,
    ): List<DaySnapshot> { /* union of epochDay keys → one snapshot per day, ascending */ }
}
// EndoRepository.daySnapshots() = fetch all + checkConsented() + SnapshotProjector.project(...)
```

**Enum raw-value parity strategy (the drift-prone spot — treat it as a contract):**
1. **Kotlin enums carry an explicit `raw: String`** matching the Swift `rawValue` **verbatim** (camelCase: `"pelvicPain"`, `"giEndoBelly"`, `"lowerAbdomenPelvis"`, …). Kotlin case names may be idiomatic (`PELVIC_PAIN`) — the *raw string* is the contract, used in Room columns, the export JSON, and `Insight.signalKey` fragments. Each enum ports the defensive decoder: `fun fromRaw(raw: String?): SymptomType` (unknown → `OTHER` sink) / `fromRawOrNull` for the optionals — same never-crash posture as the Swift `init(raw:)`.
2. **Swift `Factor` is an enum with associated values** (`food(FoodTag)`, `activity(ActivityType)`, `cyclePhase(CyclePhase)`, …) → Kotlin **sealed interface** with data classes; its stable `key` strings (`"food.caffeine"`, `"activity.present"`, `"cyclePhase.menstrual"`, …) are part of the parity contract because they are `signalKey` fragments persisted in `DismissedInsight`/`CachedInsight`.
3. **Drift test A (in `:insights` tests, the monorepo advantage):** `EnumRawValueParityTests` **parses the Swift source files directly** — a JVM test walks up to the repo root and regex-extracts the `case` lists from `EndoInsights/Sources/EndoInsights/SnapshotEnums.swift` and `Factor.swift`, then asserts every Kotlin enum's raw set (and case count: SymptomType = 11, BodyRegion = 10, FlowIntensity = 4, ActivityIntensity = 3, ActivityType = 5, FoodTag = 9, …) equals the extracted set. Zero iOS changes; real drift protection in both directions. Guard the test with `Assumptions.assumeTrue(swiftFile.exists())` so `:insights` still passes if the module is ever built outside the monorepo, and back it with **drift test B**: a checked-in `enum-raw-values.golden.json` asserted unconditionally (generated once from the Swift side; regeneration script documented in the test header).
4. **Same two-layer mechanism for ClaimsPolicy substrings:** Kotlin `ClaimsPolicy` copies the forbidden-substring lists verbatim; a JVM drift test scans `Sources/EndoApp/Content/ClaimsPolicy.swift` and asserts the Kotlin lists are a superset (assumption-guarded), plus the unconditional CopyAudit over the registered Android copy catalog. The iOS rule carries over verbatim: **every user-facing string registers for audit in the same change that introduces it.**

**Trade-off:** the Swift-source-scan tests are regex-coupled to iOS source formatting; if iOS refactors, they fail loudly (that is the point) and the golden JSON keeps `:insights` green while the fixture is regenerated deliberately.

### Pattern 5: Consent flag in DataStore, outside the health DB

**What:** Preferences DataStore file (`consent.preferences_pb`) holding `hasConsented: Boolean` (+ consent timestamp), mirroring the iOS UserDefaults split — the consent flag must survive independently of, and never live inside, the health database.

```kotlin
class ConsentStore(private val dataStore: DataStore<Preferences>, appScope: CoroutineScope) {
    val state: StateFlow<ConsentState> = dataStore.data
        .map { prefs -> if (prefs[CONSENTED] == true) ConsentState.Consented else ConsentState.NotConsented }
        .stateIn(appScope, SharingStarted.Eagerly, ConsentState.Unknown)   // Unknown until first read

    suspend fun grant()    = dataStore.edit { it[CONSENTED] = true }
    suspend fun withdraw() = dataStore.edit { it[CONSENTED] = false }      // AppGate recomposes → re-gated
}
```

- The `Unknown` initial state is why AppGate has a three-way switch — DataStore is async-only; never `runBlocking` on the main thread at startup.
- `EndoRepository.checkConsented()` reads `state.value`; `Unknown` counts as not consented (fail closed).
- **Backup rules interlock (Phase 2/8):** `data_extraction_rules.xml` **excludes** the Room database files from Auto Backup / device transfer (no opaque Google-account-tied copies of health data — plan §2) while the DataStore prefs (consent, reminders) may back up. A small app-side JVM/instrumentation check should assert the rules file excludes the DB path — this is an invariant, give it a test like the others.
- Withdrawal semantics port from iOS: withdraw re-gates immediately (Compose test: consented → withdraw → health tags gone); data deletion remains an explicit separate action (delete-day / delete-all), matching the shipped iOS behavior.

### Pattern 6: Test-suite organization for case-count parity

**What:** Mirror the Swift suites file-for-file and case-for-case; make parity itself auditable.

| Swift (shipped) | Android home | Kind | Notes |
|---|---|---|---|
| `EndoInsights/Tests` — 18 test files, **~157 `@Test` cases** (BoundaryLint 1, ChartScrub 7, Contract 7, CorrelationEngine 12, CorrelationStats 26, CyclePhaseMapper 8, ExploreSeriesCatalog 9, ExploreSeriesExtraction 10, FlareEngine 6, HonestyGate 16, InsightCodable 6, InsightsEngine 14, Recommendations 7, ScatterPairing 7, SeriesNormalization 6, Smoke 2, SuppressedFixture 2, Trends 11) | `insights/src/test/kotlin`, same file names `.kt` | JVM (`:insights:test`) | The correctness anchor; Phase 1 exit = case-count parity per file |
| App JVM-able tests: CopyAudit (40), DaySnapshotMapping (24), HealthImportMerge, ReportModel, FlareNoNotification/NoAlarmVisual/CopyDrift scans, CycleMath, Terminology… | `app/src/test/kotlin` | JVM (`:app:testDebugUnitTest`) | Anything that was a pure-logic or source-scan test on iOS stays JVM on Android |
| SwiftData-touching tests (SchemaMigration, SchemaRoundTrip, EditDelete/cascade, StoreRecovery) | `app/src/androidTest` | Instrumentation | Real SQLite; `MigrationTestHelper` + in-memory DB |
| XCUITests (ConsentFlow, CheckInFlow, Logging, Explore, Scatter, Report…) | `app/src/androidTest` Compose UI tests | Instrumentation | Port the *flows*, not the framework idioms; keep the same `testTag` ids as the iOS accessibility ids (`tab.insights`, `tab.explore`) |

**Framework mapping (Swift Testing → JUnit 5):** use **JUnit 5 (Jupiter)** on the JVM modules (`useJUnitPlatform()`), because it is the only mapping that preserves *case counts* for Swift Testing's parameterized tests:

| Swift Testing | JUnit 5 / Kotlin |
|---|---|
| `@Test func x()` | `@Test fun x()` |
| `@Test(arguments: [...])` | `@ParameterizedTest @MethodSource` (each argument = one reported case, same as Swift) |
| `#expect(a == b)` | `assertEquals(b, a)` / `assertTrue(...)` |
| `#require(x)` | `assertNotNull(x)` / `checkNotNull(x)` |
| `@Suite` struct | test class |
| `withKnownIssue` | `@Disabled("reason")` — but prefer porting the fix |

(Plain kotlin.test assertions on top of the Jupiter runner are fine; Kotest is NOT needed — it would be a new dependency for matcher sugar, against the catalog-minimalism posture. Compose/instrumentation tests stay on JUnit4 runners as AndroidX requires — that split is normal and confined to `androidTest`.)

**Parity ledger:** commit `insights/src/test/PARITY.md` mapping each Swift test file → Kotlin file → case count both sides; Phase 1's verifier recounts with `grep -c "@Test"` (Swift) vs the JUnit XML case count (Kotlin). Cheap, honest, auditable.

---

## Data Flow

### Log → recompute → dashboard (the core loop)

```
User taps "Log today" (HomeScreen, consented branch only)
    ↓
CheckIn sheet state → EndoRepository.upsert…()          [checkConsented() first]
    ↓ Room write (suspend, Room executor)
InvalidationTracker → change-ticker Flow (debounced)
    ↓
InsightsCoordinator.refresh()
    ├─ repo.daySnapshots() → SnapshotProjector (rows → List<DaySnapshot>, manual-wins merge)
    ├─ fingerprint unchanged? → serve CachedInsight rows, STOP
    └─ withContext(Dispatchers.Default): InsightsEngine.compute(snapshots, dismissedKeys)
         └─ engines → HonestyGate.apply (single choke point; bias to silence)
    ↓ repo.replaceCachedInsights(...)  [replace-all, never upsert — stale/dismissed can't linger]
StateFlow<InsightsUiState> → InsightsScreen recomposes
```

### Health Connect import

```
User taps import (rationale screen FIRST) → Health Connect permission UI (read-only set)
    ↓ HealthConnectImporter reads 4 record types since anchor/token
ImportedSample provenance rows → repo.replaceOrInsertImported(values, deletedIds)  [consent-gated]
    ↓ change ticker → recompute (imported-only days join the snapshot union;
      manual rows are NEVER mutated — imported values only fill absent fields)
```

### Key data flows

1. **Consent controls everything:** DataStore flag → `AppGate` branch (UI existence) AND `EndoRepository.checkConsented()` (data access) — two independent enforcement layers, both tested, same as iOS.
2. **Free text never reaches analysis:** `DayEntry.note` → `hasNote: Boolean` only, converted inside `SnapshotProjector`.
3. **Report path reads the same projection:** `ReportModel` consumes `repo.daySnapshots()` + provenance labels; `PdfReportRenderer` draws vector text/charts into `PdfDocument`; `FileProvider` grants one-shot URI permission to the user-chosen `ACTION_SEND` target (cache-dir file, cleaned after share).

---

## Suggested Build Order (within the 8 locked phases)

Phase order is **locked** (plan §4). This is the intra-phase sequencing + the dependency edges the roadmapper needs:

| Phase | Builds | Depends on | Concrete ordering inside the phase |
|---|---|---|---|
| **1 — `:insights` port** | `android/` Gradle skeleton (settings + catalog + `:insights` only), then: SnapshotEnums → DaySnapshot/Insight → Factor (sealed) → CorrelationStats → TrendsEngine → CyclePhaseMapper → CorrelationEngine → HonestyGate/ForbiddenTerms → InsightsEngine facade → Recommendations → Flare → chart-prep files (ExploreSeries*, ScatterPairing, SeriesNormalization, ChartScrubSelection). Port each test file with its source file, not at the end. BoundaryLint + EnumRawValueParity land here. | nothing (pure JVM — no SDK, no emulator) | Stats before engines; HonestyGate before the facade; chart-prep last (no consumers until Phase 6 but the suite parity requires them in Phase 1) |
| **2 — Persistence** | `:app` module scaffold + manifest + backup rules; 10 entities + converters → DAOs → `DeleteDayDao` (@Transaction cascade) → `ConsentStore` (DataStore) → `EndoRepository` (consent-gated) → **`insightsbridge/SnapshotProjector` + EnumBridge** (+ 24-case mapping tests, JVM) → migration-test scaffold (`exportSchema`, `MigrationTestHelper`) → cascade/survival instrumentation tests. ClaimsPolicy port + CopyAudit harness also land here (needed before any UI copy exists). | 1 (bridge maps to `DaySnapshot`; enum bridge uses `fromRaw`) | Entities → cascade → consent guard → bridge. The bridge belongs to Phase 2 (it is the persistence projection seam), not Phase 6 |
| **3 — UI shell + consent** | Bloom `designsystem` → `MainActivity`/`App`/`AppDependencies` → `AppGate` + `ConsentedShell` (3 tabs, placeholder screens) → Onboarding/consent flow → withdraw re-gate → Compose gate tests + deep-link-lands-on-gate test | 2 (ConsentStore) | Bloom tokens first (every screen after uses them); gate test is the phase's exit gate |
| **4 — Tracking screens** | CheckIn sheet → DayLog editor → BodyMap → medications/settings → reminders (`reminders/` package, physically separate; POST_NOTIFICATIONS just-in-time) | 2, 3 | Per-screen: build → round-trip test (log-persist-reload; untouched optionals read "not logged", never 0) |
| **5 — Health Connect** | availability gating → rationale-first permission UX → read client (4 record types) → mapping (`HealthSampleMapping` port) → anchor/changes-token store → merge into `ImportedSample` → provenance labels | 2 (ImportedSample, repository), 3 (a settings entry point); independent of 4's screens | Can be built in parallel with late Phase 4 if staffing allows — no shared files beyond settings |
| **6 — Explore + charts** | Canvas chart layer (honesty rules pinned by tests on the **data-prep layer**, which is Phase-1 code: extraction/normalization/pairing) → trend charts + scrub → Insights dashboard rendering (`InsightsCoordinator` + cards + dismissal) → Explore lanes → scatter + deep-link tab jump | 1 (chart-prep), 2 (bridge/coordinator data), 3 (tabs), 4 (real data to render) | Coordinator + dashboard before Explore; scatter last (needs the correlation card affordance). This is where the Canvas-vs-Vico escape hatch gets evaluated — decide by mid-phase |
| **7 — PDF report** | `ReportModel` (port; reads `daySnapshots()`) → `PdfReportRenderer` (vector text; chart pages drawn by the same Canvas chart code onto the PDF canvas — a reuse the iOS `ImageRenderer` path didn't have) → FileProvider + share intent → no-network/no-leak tests | 2, 6 (chart drawing code), 4 (data) | Model → renderer → share; copy audit on report strings in the same change |
| **8 — Play Store prep** | Data safety form, Health apps declaration, HC rationale activity/intent, privacy policy URL, target-API check, store-copy claims audit, pre-launch report | everything | Also: the manifest zero-`WRITE_*` assertion test if not already landed in Phase 5 |

**Critical-path summary:** 1 → 2 → 3 → 4 → 6 → 7 → 8, with 5 parallelizable against 4, and `designsystem` (start of 3) pullable earlier if a second stream exists. Phase 1 and 2 JVM test suites are the CI backbone from day one.

---

## Anti-Patterns

### Anti-Pattern 1: NavHost with a "consented" guard argument

**What people do:** Register all screens in a Navigation graph and check consent in each destination (or a wrapper).
**Why it's wrong:** Inverts consent-by-construction into consent-by-remembering; every new destination and deep link is a new place to forget the guard — precisely the failure mode the iOS AppGate design eliminated (T-01-03).
**Do this instead:** Pattern 1 — the `when`-switch; health composables only exist inside the consented branch. Deep links set flags observed only inside that branch.

### Anti-Pattern 2: Exposing Room entities or `Flow<Entity>` to `ui/`

**What people do:** Collect DAO flows of entities directly in composables.
**Why it's wrong:** Dissolves the `insightsbridge` seam; entity types leak everywhere, the honesty merge (manual-wins, absence ≠ 0) gets reimplemented per screen and drifts.
**Do this instead:** `ui/` consumes `DaySnapshot`, `Insight`, and small screen-state types from the repository/coordinator. Entities appear in `data/` and `insightsbridge/` only — enforceable with a cheap structural JVM test (scan `ui/` sources for `data.entities` imports), in the same spirit as the boundary lint.

### Anti-Pattern 3: Coercing "not logged" to defaults anywhere in the pipeline

**What people do:** `severity ?: 0`, `flow ?: NONE`, interpolating chart gaps, non-null Room columns with `DEFAULT 0` for optional signals.
**Why it's wrong:** Breaks the load-bearing honesty contract (absence ≠ 0) that every engine and chart rule depends on; on Android the temptation is bigger because Room *allows* non-null.
**Do this instead:** Optional signals are nullable columns and nullable `DaySnapshot` fields, end to end. Room's non-null freedom is spent on true invariants (ids, day keys, timestamps), never on health values.

### Anti-Pattern 4: WorkManager/services for insights recompute

**What people do:** Schedule background workers to precompute insights.
**Why it's wrong:** Unnecessary (single-user dataset computes in milliseconds on `Dispatchers.Default`), adds background-execution policy surface, and creates health-data processing outside the consented foreground session.
**Do this instead:** Pattern 3 — recompute on change-ticker/appearance in-process; `CachedInsight` rows cover cold starts.

### Anti-Pattern 5: Letting Auto Backup silently copy the health DB

**What people do:** Ship default backup behavior (full-app backup enabled).
**Why it's wrong:** Places special-category health data in a Google-account-tied backup the user never explicitly consented to — the exact opaqueness plan §2 rejects.
**Do this instead:** `dataExtractionRules`/`fullBackupContent` excluding the Room DB files; the encrypted SAF export is the only backup path for health data; a test pins the rules file.

### Anti-Pattern 6: "Improving" ported engine code during Phase 1

**What people do:** Refactor algorithms, rename thresholds, "idiomize" statistics while porting.
**Why it's wrong:** The Swift suite is the correctness anchor; behavioral drift during porting is undetectable once both sides have "their own" tests. Numerical parity (BH-FDR ordering, Fisher tail sums, Cliff's δ ties) is the whole point.
**Do this instead:** Port mechanically, tests first or alongside, per file; idiomatic cleanup is allowed only where the ported tests pin behavior. Divergences (e.g., `Double` math differences) get documented tolerance assertions, not silent changes.

---

## Integration Points

### External services / platform surfaces

| Surface | Integration Pattern | Notes |
|---------|---------------------|-------|
| Health Connect | `androidx.health.connect:connect-client`, read-only permission set for 4 record types; `getSdkStatus()` runtime gating | Android 14+ built-in; ≤13 installable app; rationale activity/intent is a Play REQUIREMENT (Phase 8 blocker if skipped). Manifest declares zero `WRITE_*` health permissions — auditable by test |
| Notifications (reminders only) | `POST_NOTIFICATIONS` just-in-time (API 33+); exact alarms NOT needed — inexact `AlarmManager`/`setAndAllowWhileIdle` suffices for daily reminders | Keep ALL notification code in `reminders/`; the flare structural scan depends on this boundary |
| Storage Access Framework | `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` for encrypted export/import | Zero storage permissions; user picks destination (plan §2). Export/import ships with Phase 2's UUID identity but the feature itself can ride with Settings (Phase 4) or later — roadmapper should place it explicitly |
| Share (PDF) | `FileProvider` + `ACTION_SEND`, `FLAG_GRANT_READ_URI_PERMISSION`, cache subdir | No file readable by other apps beyond the explicit share target (Phase 7 exit criterion) |
| Google Play | Health apps declaration + Data safety form ("no data collected/shared") + privacy policy URL | Data safety answers must match the manifest and the (empty) network usage — the app should have NO INTERNET permission at all, which makes the form trivially truthful and is itself a strong auditable artifact worth a manifest test |

### Internal boundaries

| Boundary | Communication | Enforcement |
|----------|---------------|-------------|
| `:app` → `:insights` | Gradle `implementation(project(":insights"))`, one-way | `:insights` has `kotlin("jvm")` only — it *cannot* depend on `:app`; boundary lint test additionally forbids `android.`/`androidx.` imports in `:insights` sources |
| `data/` ↔ `insightsbridge/` ↔ `ui/` | Repository facade; entities never cross into `ui/` | Structural source-scan JVM test (Anti-Pattern 2) |
| `reminders/` ↮ flare/insights code | None (no imports either direction) | Flare no-notification structural scan over `ui/insights` + `insightsbridge` + `:insights` sources |
| Kotlin enums ↔ Swift enums (cross-platform vocabulary) | Shared raw-value strings (contract, not code) | Two-layer drift tests: Swift-source scan (assumption-guarded) + golden JSON (unconditional) — Pattern 4 |
| Kotlin `ClaimsPolicy` ↔ Swift `ClaimsPolicy` | Copied substring lists | Same two-layer drift mechanism + CopyAudit registration rule carried verbatim |
| `android/` ↔ iOS tree | NONE at build time (Gradle never sees Xcode files and vice versa); read-only at test time (drift scans) | Path-filtered CI; iOS files untouched |

### CI story (`android/` alongside the Xcode project)

There is **no CI in the repo today** (no `.github/workflows/`; iOS runs locally/Xcode-scheme). Recommendation: add **one** GitHub Actions workflow for Android only, and change nothing for iOS.

```yaml
# .github/workflows/android.yml  (the ONLY repo-root addition)
on:
  pull_request: { paths: ["android/**", ".github/workflows/android.yml"] }
  push: { branches: [main], paths: ["android/**"] }
jobs:
  jvm:                                  # the backbone — fast, no emulator
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: android } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4        # wrapper validation + build cache
      - run: ./gradlew :insights:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  instrumentation:                      # migrations + Compose gate tests; slower
    if: github.event_name == 'push'     # main-only (or nightly/label) — not per-PR
    runs-on: ubuntu-latest              # ubuntu runners support KVM-accelerated emulators
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: reactivecircus/android-emulator-runner@v2
        with: { api-level: 34, arch: x86_64, working-directory: android,
                script: ./gradlew :app:connectedDebugAndroidTest }
```

- **Path filters are the monorepo contract:** iOS-only commits never trigger Gradle; Android commits never touch Xcode. Checkout is full-repo, so the Swift-source drift scans work in CI for free.
- **Note the drift-scan nuance:** the parity tests DO run on iOS-file changes locally but CI won't re-run Android tests when only Swift files change. Acceptable for v2.0 (iOS is frozen during this milestone); if iOS work resumes, add `EndoInsights/Sources/**` and `Sources/EndoApp/Content/ClaimsPolicy.swift` to the Android workflow's path filter — a one-line change, worth recording now.
- The JVM job is the merge gate; instrumentation runs on `main` pushes (or a `run-emulator` label) because emulator jobs cost ~8–15 min. Local `connectedAndroidTest` on a device remains the Phase-2/3 development loop, mirroring how iOS device-gated checks worked.
- Free-tier friendly: ubuntu runners only, no macOS minutes consumed, no signing needed for `assembleDebug`.

---

## Scaling Considerations

Single-user, on-device — scale is data-history depth, not users:

| Scale | Adjustment |
|-------|------------|
| ≤ 2 years of daily logs (~700 snapshots) | Everything above as-is; full-table fetch + projection is sub-10ms; engine compute trivial on `Dispatchers.Default` |
| 5–10 years | Add `WHERE epochDay >= ?` windows to the projection fetches (the engines already window internally); Canvas charts already render windowed series |
| Pathological (imports flood `ImportedSample`) | The unique index on the HC record id caps growth; changes-token incremental reads (already the design) prevent re-import churn |

**First bottleneck honestly expected:** Canvas chart layout cost on low-end devices in Phase 6 — which is exactly the pre-declared Vico escape hatch; the honesty rules live in the (Phase 1) data-prep layer either way, so switching renderers is contained.

---

## Sources

- Shipped iOS codebase (port SOURCE, read directly): `Sources/EndoApp/App/AppGate.swift`, `Sources/EndoApp/Persistence/Repository.swift` (`daySnapshots()`, 36 `assertConsented` sites), `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift` (`Task.detached` pattern), `EndoInsights/Sources` (19 files) + `EndoInsights/Tests` (18 test files, ~157 `@Test` cases), `Tests/EndoAppTests` (40-case CopyAudit, 24-case DaySnapshotMapping, structural scans) — HIGH
- `.planning/research/ANDROID-PORT-PLAN.md` §1–§5 (locked decisions this doc realizes) — HIGH
- [Room releases (2.8.4 stable, 2.x maintenance mode)](https://developer.android.com/jetpack/androidx/releases/room) and [Room 3 releases (3.0.0 stable, 2026-07-01)](https://developer.android.com/jetpack/androidx/releases/room3); [Room 3.0 announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) — HIGH (official)
- [Jetpack Navigation 3 is stable (Nov 2025)](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html), [navigation3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3) — HIGH (official; informs the "no nav library now, Nav3 as escape hatch" call)
- androidx first-party docs (DataStore, Health Connect client, MigrationTestHelper, FileProvider, backup rules) — HIGH; patterns are stable, verify exact artifact versions in `libs.versions.toml` at each phase start
- GitHub Actions Android practice (gradle/actions/setup-gradle, reactivecircus/android-emulator-runner, KVM on ubuntu runners) — MEDIUM (community-standard, widely corroborated)

---
*Architecture research for: Endo-App v2.0 Android port (`android/` monorepo, locked `:app` + `:insights` layout)*
*Researched: 2026-07-08*
