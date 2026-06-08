# Phase 5: Snapshot Contract + Descriptive Trends Engine — Research

**Researched:** 2026-06-08
**Domain:** Swift 6 value-type contract design + a standalone Foundation-only local SPM package (`EndoInsights`) wired via XcodeGen, with Swift Testing against synthetic fixtures
**Confidence:** HIGH (XcodeGen local-package + package-test-in-scheme verified against installed tool version 2.45.4 and the official ProjectSpec; engine/contract design grounded in the existing `FactorDay`/`CycleMath` templates)

## Summary

Phase 5 is the architectural keystone of the insights half. It delivers (a) a `Sendable`, Foundation-only **value contract** (`DaySnapshot` + a generic `Insight` and supporting value types) and (b) a **standalone local SPM package `EndoInsights`** containing the contract + a pure, deterministic `TrendsEngine`, unit-tested with Swift Testing against synthetic fixtures — and **only** the app-side `Repository.daySnapshots()` mapping stays outside the package. The compiler-enforced package boundary (the package's `Package.swift` declares only the `EndoInsights` target with no SwiftData/SwiftUI dependency) is the mechanism that makes SC1 ("no SwiftData/SwiftUI can sneak in") real rather than aspirational.

The make-or-break structural question — *can the package's Swift Testing tests run inside `xcodebuild test -scheme EndoApp`?* — resolves cleanly: **yes**, via XcodeGen's `scheme.test.targets: - package: EndoInsights/EndoInsightsTests` syntax. The installed XcodeGen is **2.45.4**; local-package test-target-in-scheme support landed in **2.27.0** ([VERIFIED: XcodeGen CHANGELOG]). This is strictly better than the fallback (a `framework` project target) because it keeps the package a *real* SPM package whose `Package.swift` is the compiler boundary, while still surfacing its tests in the single `xcodebuild test -scheme EndoApp` command the project already uses.

**Primary recommendation:** Create `EndoInsights/Package.swift` (a real SPM package, `// swift-tools-version:6.0`, one `EndoInsights` library + one `EndoInsightsTests` test target, Foundation-only, no dependencies). Wire it in `project.yml` via top-level `packages:` (local `path: EndoInsights`), make the app target depend on it (`dependencies: - package: EndoInsights`), and add the package's test target to the app scheme (`scheme.test.targets: - package: EndoInsights/EndoInsightsTests`). Model `DaySnapshot` as a flat struct mirroring `FactorDay`+symptoms+pain+bleeding+`suppressed`; model `Insight` as a struct with enum `kind`/`window`/`confidence`. Build `TrendsEngine.trends(from:asOf:)` as a pure, injected-`today`, `Equatable`-output function mirroring `CycleMath`. Enforce the boundary with both the compiler and a grep/Swift-Testing lint over the package sources.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `DaySnapshot`/`Insight` value contract | EndoInsights package (pure Swift) | — | The portable, store-free read-model + output contract; consumed by Phases 6/7/8/11/12 |
| `TrendsEngine` descriptive stats | EndoInsights package (pure Swift) | — | Pure value-in/value-out; no I/O, injected `today` |
| Store `@Model` → `DaySnapshot` mapping | App (Persistence/Repository) | — | Knows SwiftData; MUST stay out of the package (D-10/D-14, SC1) |
| Reading the suppressed flag / bleeding / symptoms / factors | App (Repository) | — | Reads `SchemaV*` rows + `TreatmentState`; projects to value types |
| Rendering Insights (charts/dashboard) | App UI (Phase 8) | — | OUT OF SCOPE this phase (D-15: no UI) |
| Build/test wiring | XcodeGen `project.yml` + package `Package.swift` | — | `project.yml` references the local package; `Package.swift` is the compiler boundary |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift | 6.0 (`SWIFT_VERSION: "6.0"` in `project.yml`) | Language + strict concurrency | Already the project mode; `Sendable` enforced by the compiler [VERIFIED: project.yml] |
| Foundation | system | `Date`, `Calendar`, `DateComponents`, `UUID` for the contract + window math | The ONLY import allowed in the package (SC1); mirrors `FactorDay`/`CycleMath` [VERIFIED: codebase] |
| Swift Testing | bundled with Xcode 16+ toolchain | `@Test`/`#expect` unit tests against synthetic fixtures | Project default for new test code; already used across `Tests/EndoAppTests` (18 files `import Testing`) [VERIFIED: codebase grep] |
| Swift Package Manager (local package) | via `Package.swift`, swift-tools-version 6.0 | Defines `EndoInsights` library + test target; the compiler boundary | First-party; XcodeGen references it as a local package [VERIFIED: XcodeGen ProjectSpec] |
| XcodeGen | 2.45.4 (installed) | Generates the `.xcodeproj`, wires the local package + scheme test target | Already the project's build-spec tool; local-package + package-test-in-scheme supported since 2.15.0 / 2.27.0 [VERIFIED: `xcodegen --version` + CHANGELOG] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `Accelerate`/`vDSP` | system | Vectorized means/variance over long series | DEFER — STACK.md lists it for "once datasets grow"; Phase 5 stats are trivial (`reduce`), do NOT add it now [CITED: .planning research STACK.md] |

**No third-party packages are introduced this phase.** The "Package Legitimacy Audit" is therefore N/A (no external registry packages installed). `EndoInsights` is a first-party local package, not a registry dependency.

### Alternatives Considered (package mechanism)
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Real local SPM package (`Package.swift`) + scheme `package:` test target | An XcodeGen `framework` project **target** named `EndoInsights` + a companion `bundle.unit-test` target | The framework approach also gives a compiler boundary (the framework target simply has no SwiftData/SwiftUI link), and its tests run trivially in the scheme. BUT it's NOT a real SPM package — D-09 explicitly says "a **real local SPM package**." Use the framework target ONLY as a fallback if the `package:` scheme test target fails to run on this toolchain (see Pitfall 1). The installed XcodeGen (2.45.4 > 2.27.0) supports the SPM path, so the SPM package is the recommendation. |
| Package tests via `xcodebuild test -scheme EndoApp` | A second invocation `swift test` against the package | A separate `swift test` run for an iOS-only package is awkward (SPM defaults to the host/macOS destination unless given a simulator destination) and means SC3 ("tests run in the suite") needs two commands. Folding the package test target into the app scheme keeps the single existing command. Avoid. |

**Installation / scaffolding (no `npm`/`pip` — this is a local SPM package):**
```bash
# 1. Create the package skeleton (folder + Package.swift + Sources/Tests dirs)
mkdir -p EndoInsights/Sources/EndoInsights EndoInsights/Tests/EndoInsightsTests
# 2. Write EndoInsights/Package.swift (see Code Examples)
# 3. Regenerate the Xcode project so the package + scheme wiring takes effect
xcodegen generate
# 4. Build + run the whole suite (app tests + package tests together)
xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'
```

**Version verification:**
```
$ xcodegen --version  →  Version: 2.45.4   [VERIFIED 2026-06-08]
```
Local Swift Package support: XcodeGen **2.15.0** ("Add support for local Swift Packages in `packages` using `path`"). Package **test target** in a scheme: XcodeGen **2.27.0** ("Support test target for local Swift Package"). Installed 2.45.4 covers both [VERIFIED: XcodeGen CHANGELOG via raw.githubusercontent.com].

## Package Legitimacy Audit

> N/A — this phase installs **no external registry packages**. `EndoInsights` is a first-party local SPM package (its `Package.swift` declares zero `.package(...)` dependencies; only Foundation, which is implicit/system). No npm/PyPI/crates package is added, so slopcheck/registry verification does not apply. The only tools involved (XcodeGen 2.45.4, Swift Testing, SwiftPM) are already in the project's verified toolchain.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────┐
   SwiftData store       │            APP  (Sources/EndoApp)           │
   (SchemaV1/V2/V3       │                                             │
    @Model rows)         │   Repository.daySnapshots()  ← THE SEAM     │
        │                │     reads: SymptomReading, PainPoint,       │
        │  fetch          │            BleedingEvent, FactorReading,    │
        ▼                │            TreatmentState(suppressed)        │
  [@Model rows] ─────────┼──► bucket by start-of-day                   │
                         │    project each bucket ───────┐             │
                         │   (imports SwiftData — stays   │             │
                         │    app-side, NEVER in package) │             │
                         └────────────────────────────────┼────────────┘
                                                           │ [DaySnapshot] (Sendable value array)
                                                           ▼
                         ┌─────────────────────────────────────────────┐
                         │   PACKAGE  EndoInsights  (Foundation ONLY)  │
                         │                                             │
                         │   TrendsEngine.trends(                      │
                         │       from: [DaySnapshot],                  │
                         │       asOf today: Date) -> [Insight]        │
                         │                                             │
                         │   per window (7/30/90/all-time, anchored    │
                         │   on absolute calendar dates ending at      │
                         │   `today`):                                 │
                         │     • bucket days into [window-start, today]│
                         │     • per-trend min-data threshold gate     │
                         │     • count / mean / %-days-present         │
                         │     • direction = recent vs prior equal win │
                         │     • suppressed ⇒ NO bleeding/cycle trend  │
                         └───────────────────────┬─────────────────────┘
                                                 │ [Insight] (Equatable, Sendable)
                                                 ▼
                         (Phase 8 dashboard renders these — OUT OF SCOPE here)
```

The diagram's load-bearing arrow: `@Model` rows never cross into the package. The Repository performs the *only* SwiftData→value projection, identical in spirit to the existing `factorDays()`/`bleedingDays()` reads. The engine sees only `[DaySnapshot]` + an injected `today`.

### Recommended Package + Repo Layout
```
EndoInsights/                          # NEW local SPM package (sibling of Sources/, Tests/)
├── Package.swift                      # swift-tools-version:6.0, Foundation-only, 2 targets
├── Sources/
│   └── EndoInsights/
│       ├── DaySnapshot.swift          # the read-model value contract (D-01)
│       ├── SymptomPoint.swift         # value struct (D-04)  [or fold into DaySnapshot.swift]
│       ├── PainPoint.swift            # value struct (D-04)  ⚠ name-clash note below
│       ├── Insight.swift              # generic Insight + InsightPoint (D-02)
│       ├── InsightKind.swift          # enum: .trend + reserved cases (D-02)
│       ├── TrendWindow.swift          # enum: .last7/.last30/.last90/.allTime (D-06)
│       ├── InsightConfidence.swift    # enum: descriptive strength (D-02)
│       └── TrendsEngine.swift         # pure engine (D-13)
└── Tests/
    └── EndoInsightsTests/
        ├── SnapshotFixtures.swift     # the fixture DSL/builders (D-11)
        ├── TrendsEngineTests.swift    # window/direction/threshold tests
        ├── SuppressedFixtureTests.swift  # SC3: no bleeding/cycle trend
        └── BoundaryLintTests.swift    # D-12: package imports only Foundation

Sources/EndoApp/
└── Persistence/
    └── Repository.swift               # ADD: daySnapshots() -> [DaySnapshot] (D-14)
                                       #   + `import EndoInsights` (the only consumer)
project.yml                            # ADD: packages: + app dependency + scheme test target
```

> ⚠ **Name-clash note:** the app already has `SchemaV*.PainPoint` (`@Model`) AND a domain concept; the package's `PainPoint` value struct is a *different* type in a *different* module (`EndoInsights.PainPoint` vs `EndoApp`/`SchemaV1.PainPoint`). This is fine — they're in separate modules — but the Repository mapping file will have both in scope, so qualify (`EndoInsights.PainPoint(...)`) or, to avoid all confusion, name the package value struct `PainSeverity` or `PainPointSnapshot`. **Recommend `PainPoint` inside the package but mapping code uses the fully-qualified form.** Flag this to the planner as a Claude's-discretion naming call. [ASSUMED — naming preference, not a hard constraint]

### Pattern 1: Pure engine over value snapshots, injected `today` (mirror `CycleMath`)
**What:** `enum TrendsEngine { static func trends(from snapshots: [DaySnapshot], asOf today: Date, calendar: Calendar = .current) -> [Insight] }`. No `Date.now`/`Date()`/randomness inside; `today` defines window ends. `Insight` is `Equatable` + `Sendable` so tests assert exact output.
**When to use:** All descriptive-trend computation.
**Example:**
```swift
// Pattern mirrors Sources/EndoApp/Domain/CycleMath.swift (injected today, pure, Equatable out)
public enum TrendsEngine {
    public static func trends(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        let today0 = calendar.startOfDay(for: today)
        // ... per-window bucketing + per-trend threshold gate ...
    }
}
```

### Pattern 2: Window bucketing on absolute calendar dates (D-06)
**What:** For window `N` days, the window is `[today - (N-1) days … today]` inclusive, using `calendar.startOfDay` + `date(byAdding: .day, value:)` — NOT epoch arithmetic (DST-safe, mirrors `CycleMath.periodRuns`/`consecutiveGaps`). All-time = every snapshot.
**When to use:** Every trend; "direction" compares the recent window against the immediately-prior equal-length window (`[today-2N+1 … today-N]`).

### Pattern 3: Per-trend min-data threshold gate (D-08)
**What:** Before emitting a trend, count *logged* days in the window for that signal (a day counts only if the relevant field is non-nil / the signal is present). If `< threshold`, either omit the trend or emit an explicit `.trend` Insight whose copy says "not enough data yet" (descriptive, non-causal). The suppressed/no-bleeding fixture yields zero bleeding-day counts → bleeding trend is gated out entirely (SC3).
**When to use:** Every trend, every window.

### Anti-Patterns to Avoid
- **`import SwiftData`/`import SwiftUI`/`import UIKit` in the package:** defeats SC1. The package's `Package.swift` has no such dependency, so the compiler rejects it — but the boundary lint (D-12) is the belt-and-suspenders.
- **Coercing nil → 0 in a mean:** breaks the honesty contract (D-03). A nil stress/mood/severity is *excluded from the denominator*, never summed as 0. Mirror `FactorDay`'s "absence ≠ 0."
- **Regression/smoothing/trendlines:** D-07 forbids them (false precision). "Direction" is a coarse recent-vs-prior comparison only.
- **Phantom bleeding/cycle trend under suppression:** mirror `CycleMath`'s suppressed short-circuit. A `suppressed` snapshot stream with no bleeding days must produce NO bleeding/cycle Insight (SC3).
- **`Date.now` inside the engine:** non-deterministic; `today` is injected (mirror `CycleMath`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Day bucketing / window edges | Epoch-second math (`timeIntervalSince1970 / 86400`) | `Calendar.startOfDay` + `date(byAdding: .day, value:)` + `dateComponents([.day], from:to:)` | DST/leap-second correctness; the existing `CycleMath`/`Repository` already use exactly this — be consistent [VERIFIED: codebase] |
| Median (if used for any trend) | A fresh sort+index helper | Reuse the `CycleMath.medianInt` pattern (or lift a small shared helper) | Already proven + unit-tested; avoid a second subtly-different implementation |
| Compiler-enforced module boundary | A custom build-phase script that greps for forbidden imports | A real `Package.swift` with no SwiftData/SwiftUI dependency (the linker/compiler IS the boundary) | The whole point of D-09: the package mechanism gives the boundary for free; the grep lint is *additional* defense, not the primary mechanism |
| Test scaffolding | A bespoke fixture-injection framework | Swift Testing `@Test`/`@Suite` + plain Swift builder funcs for `[DaySnapshot]` | Already the project standard; parameterized `@Test(arguments:)` covers window/threshold matrices |

**Key insight:** The package *is* the architecture. The single most valuable thing this phase ships is the `Package.swift` that cannot link SwiftData/SwiftUI — everything else (engine, contract) is ordinary pure Swift the project already knows how to write (`FactorDay`, `CycleMath`).

## Concrete Contract Design (Claude's-discretion, D-02 reserved for planning)

### `DaySnapshot` (D-01) — flat, Sendable, Foundation-only; mirrors `FactorDay` + symptoms/pain/bleeding
```swift
import Foundation

public struct SymptomPoint: Sendable, Equatable {
    public let type: SymptomType          // ⚠ see "enum sourcing" below
    public let severity: Int?             // nil = logged-present-without-severity / unlogged (honesty)
}
public struct PainPoint: Sendable, Equatable {        // package-local value type (≠ SchemaV1.PainPoint)
    public let region: BodyRegion
    public let severity: Int?
}

public struct DaySnapshot: Sendable, Equatable {
    public let day: Date                  // start-of-day (the Repository normalizes)
    public let symptoms: [SymptomPoint]
    public let pain: [PainPoint]
    // Bleeding (CYCL-01): present iff the day had a flow-bearing event
    public let flow: FlowIntensity?       // nil = no bleeding logged that day
    public let clots: Bool
    // FactorDay fields, carried verbatim (LIFE-01..03) — absence ≠ 0 honesty preserved
    public let foodTags: [FoodTag]
    public let activityType: ActivityType?
    public let activityMinutes: Double?
    public let activityIntensity: ActivityIntensity?
    public let sleepHours: Double?
    public let sleepQuality: SleepQuality?
    public let medicationsTaken: [UUID]
    public let tookPRN: Bool
    public let stress: Int?               // nil = unlogged (NEVER 0)
    public let mood: Int?                 // nil = unlogged (NEVER neutral)
    public let hasNote: Bool              // note-presence only (no free text in the snapshot — privacy + the engine never reads prose)
    public let suppressed: Bool           // D-03: per-day suppressed flag (no-cycle mode)
}
```
**Recommendations / rationale:**
- **`hasNote: Bool`, not the note text** — the engine never needs prose; carrying only presence keeps the snapshot minimal and avoids leaking free-text into the analysis layer. [ASSUMED — D-01 says "hasNote: Bool", confirming presence-only]
- **`flow: FlowIntensity?`** (not a `BleedingDay`-style `hadFlow: Bool`) — richer for future per-intensity trends (Phase 7+) while `flow != nil` cleanly answers "had bleeding." Frequency trends use `flow != nil`.
- **`suppressed` is per-day** (D-03). In practice every snapshot in a stream will share the same value (the user is suppressed or not), but per-day is the most general shape and costs nothing — it also future-proofs a user who toggles suppression mid-history.

### Enum sourcing — the one real design fork
`SymptomType`, `BodyRegion`, `FlowIntensity`, `FoodTag`, `ActivityType`, `ActivityIntensity`, `SleepQuality` currently live in `Sources/EndoApp/Domain/*` (all already `Sendable`, Foundation-only, `import Foundation`). The package needs them. Three options:

| Option | What | Tradeoff | Recommendation |
|--------|------|----------|----------------|
| **A. Move the 7 enums into the package** | Cut them from `Sources/EndoApp/Domain/`, add to `EndoInsights`, app `import EndoInsights` | Cleanest single source of truth; but touches Schema-mapping + UI + copy-audit call sites across the whole app (these enums are referenced widely) | Highest blast radius — only if planning accepts a wide edit |
| **B. Duplicate minimal raw-value enums in the package** | Package declares its own `SymptomType` etc.; Repository maps app-enum → package-enum | Zero app churn; but two definitions to keep in sync (a real maintenance hazard given they share `rawValue`) | Avoid — drift risk contradicts D-17 "stable contract" |
| **C. Move the enums into the package AND re-export** | Move to package; in the app add thin `typealias`es or `@_exported import` so existing `EndoApp` call sites still compile unchanged | Single source of truth + minimal app churn | **RECOMMENDED** — but verify `@_exported import` behavior / typealias ergonomics during planning. [ASSUMED — needs a small spike in the plan] |

> This is the **highest-uncertainty design decision in the phase** and should be an explicit planned task with a verification step (whole-suite build green after the move). The enums are Foundation-only already, so they are *eligible* to live in the package; the only question is the mechanical cost of relocating them. Flag for the planner. [ASSUMED]

### `Insight` (D-02) — generic, stable for Phases 6/7/8/11/12
```swift
public enum InsightKind: String, Sendable, Equatable, CaseIterable {
    case trend                  // Phase 5
    case cyclePhase             // reserved — Phase 6 (INST-02)
    case correlation            // reserved — Phase 7 (INST-03)
    case recommendation         // reserved — Phase 11 (INST-05)
    case flare                  // reserved — Phase 12 (INST-06)
}
public enum TrendWindow: String, Sendable, Equatable, CaseIterable {
    case last7, last30, last90, allTime
}
public enum InsightConfidence: Int, Sendable, Equatable, Comparable, CaseIterable {
    case none = 0, low = 1, medium = 2, high = 3   // mirror `Confidence` in CycleSummary.swift
    public static func < (l: Self, r: Self) -> Bool { l.rawValue < r.rawValue }
}
public struct InsightPoint: Sendable, Equatable {
    public let date: Date
    public let value: Double
}
public struct Insight: Sendable, Equatable, Identifiable {
    public let id: UUID
    public let kind: InsightKind
    public let headline: String         // plain, non-causal (ClaimsPolicy posture)
    public let detail: String
    public let window: TrendWindow
    public let confidence: InsightConfidence
    public let points: [InsightPoint]   // supporting data for the future chart (Phase 8)
}
```
**Recommendations:**
- **`kind`, `window`, `confidence` are enums** (not strings/structs) — typed, exhaustive, `Equatable` for deterministic tests, and the reserved cases make the Phase-6..12 contract visible *now* (D-17) without a later contract change.
- **`confidence` mirrors the existing `Confidence` enum** (`Int`-backed, `Comparable`) in `CycleSummary.swift` — consistency across the codebase. Phase 5 sets it descriptively (e.g. more logged days → higher); the *formal* gating/withholding layer is Phase 6 (D-16), so Phase 5 just populates the field honestly.
- **`id: UUID`** — but note: a fresh `UUID()` would break `Equatable`-determinism in tests. **Derive `id` deterministically** (e.g. a stable UUID from `kind`+`window`+a signal key, or expose it but exclude it from the equality used in assertions). Recommend a deterministic id (hash-derived) so `Insight` stays fully `Equatable` for fixture assertions. [ASSUMED — implementation detail; flag to planner]
- **`headline`/`detail` copy** must pass `ClaimsPolicy.forbiddenVerbs` (no "trigger"/"causes"/"will"/"detect") — there is an existing `CopyAuditTests` pattern to mirror; consider a package-side copy lint or keep copy-audit app-side. Phase 5 copy is purely descriptive ("X logged on N of the last 30 days").

## Trend Math (concrete definitions, D-05/D-07/D-08)

For each `TrendWindow` (`last7`=7d, `last30`=30d, `last90`=90d, `allTime`), with window end = injected `today` (start-of-day), window = `[end - (N-1) days … end]` inclusive:

| Trend | Definition | "Present" / denominator | Min-data threshold (recommended) |
|-------|------------|--------------------------|----------------------------------|
| Per-symptom severity | mean of non-nil `severity` for that `SymptomType` over days where the symptom is logged | denominator = days the symptom was logged with a severity | ≥ 3 logged days in the window [ASSUMED] |
| Per-symptom frequency | %-days-present = (days symptom logged) / (days in window with *any* log) | "any log" = a day that appears in the snapshot stream | ≥ 3 logged days [ASSUMED] |
| Per-food-tag frequency | %-days the tag appears | days with any factor log | ≥ 3 logged days [ASSUMED] |
| Activity-day frequency | %-days `activityType != nil` | days with any log | ≥ 3 logged days [ASSUMED] |
| Poor-sleep-day frequency | %-days `sleepQuality == .poor` | days `sleepQuality != nil` | ≥ 3 logged sleep days [ASSUMED] |
| Stress mean / Mood mean | mean of non-nil `stress`/`mood` | days the scale was logged (nil excluded) | ≥ 3 logged readings [ASSUMED] |
| Bleeding-day frequency | %-days `flow != nil` | days in window | ≥ 1 bleeding day AND not suppressed (else GATED OUT — SC3) |

**Direction (D-07):** compare the recent window `[today-(N-1) … today]` mean/frequency against the immediately-prior equal-length window `[today-(2N-1) … today-N]`. Emit a coarse `.up`/`.down`/`.steady` (encode in `detail`/an enum field, planner's discretion) — **steady** when the delta is within a small band to avoid false precision. `allTime` has no prior window → no direction (or compare first-half vs second-half; recommend: omit direction for `allTime`). [ASSUMED — exact band + allTime handling is Claude's discretion per D-07]

**Thresholds:** the recommended `≥ 3 logged days` is a starting value (mirrors `CycleMath`'s "≥ 2 cycles" honesty spirit); the exact per-trend numbers are explicitly Claude's discretion (D-08 says "values to research/planning"). The plan should make the threshold a named constant per trend so it's tunable and unit-asserted.

**Suppressed / no-bleeding fixture (SC3):** a `[DaySnapshot]` where every day has `suppressed == true` and `flow == nil` for months MUST yield **no** bleeding-frequency Insight and **no** cycle-related Insight. Implementation: gate the bleeding trend on `!suppressed && bleedingDayCount >= 1`. This mirrors `CycleMath.summarize`'s `if isSuppressed { return .suppressed }` short-circuit — proven pattern.

## Determinism + Swift Testing (D-11)

- **Injected `today`** + `Calendar = .current` default (override-able in tests with a fixed UTC calendar for stability). `Insight` is `Equatable` so tests assert exact arrays.
- **Fixture DSL / builders** (`SnapshotFixtures.swift`): small Swift builder funcs, e.g.
  ```swift
  func day(_ offset: Int, from anchor: Date, symptoms: [SymptomPoint] = [], flow: FlowIntensity? = nil,
           stress: Int? = nil, suppressed: Bool = false, cal: Calendar = .utc) -> DaySnapshot
  func suppressedNoBleedingFixture(monthsBack: Int, anchor: Date) -> [DaySnapshot]  // SC3
  func regularSymptomFixture(...) -> [DaySnapshot]
  ```
  Use `@Test(arguments:)` parameterized tests for the window × threshold matrix. The existing `Tests/EndoAppTests/CycleMathTests.swift` is the template for fixture-driven engine tests (injected `today`, exact-equality assertions).
- **No simulator/iCloud dependency:** the package tests touch no SwiftData/`ModelContext`/network — they build `[DaySnapshot]` in memory. (The existing `FactorSnapshotTests` *does* spin up an in-memory `ModelContainer` because it tests the Repository mapping; the *package* tests must NOT — that separation is the point.)

## The Repository Mapping Seam (D-10/D-14, stays app-side)

Add to `Repository` protocol + `SwiftDataRepository`:
```swift
// in the EndoApp module (Persistence/Repository.swift) — imports SwiftData AND EndoInsights
import EndoInsights
func daySnapshots() throws -> [DaySnapshot]
```
**Mapping recipe (mirrors `factorDays()`):**
1. `assertConsented()` (ONBD-01 defense-in-depth, like every sibling read).
2. Fetch + bucket by `Calendar.startOfDay` across the day-scoped row types: `SymptomReading`, `PainPoint`, `BleedingEvent`, `FactorReading` (reuse the exact `factorDays()` bucketing + `makeFactorDay` projection logic for the factor fields).
3. Resolve `suppressed` from the latest `TreatmentState` (reuse `isSuppressed()` — it's a single value applied to every snapshot, OR computed per-day if treatment history is later modeled; for Phase 5 a single current value is fine and matches `CycleMath`'s usage).
4. Map symptoms → `[EndoInsights.SymptomPoint]`, pain → `[EndoInsights.PainPoint]`, the bleeding event → `flow`/`clots`, and the `FactorDay` fields → the corresponding `DaySnapshot` fields. `hasNote` from the day's `DayEntry.note != nil`.
5. Defensive decode throughout (unknown raws → `.other`/`nil`), exactly as `makeFactorDay` already does.
6. **Confirm the engine never touches the store (SC1):** the mapping is the ONLY place `@Model` rows become `DaySnapshot`; `TrendsEngine` receives the array. This keeps SwiftData out of the package by construction.

Unit-test the mapping app-side in `Tests/EndoAppTests` with an in-memory `ModelContainer` (mirror `FactorSnapshotTests`), asserting the honesty contract (unlogged stress → `nil`, logged 0 → 0; suppressed flag propagates; a day with no bleeding → `flow == nil`).

## Boundary Lint (D-12) — belt-and-suspenders on the compiler

The compiler is the *primary* boundary (the package's `Package.swift` can't link SwiftData/SwiftUI). The lint is a cheap second proof. Two viable forms:

**Form A — Swift Testing test inside the package** (preferred; runs in the suite, no shell):
```swift
import Testing
import Foundation

@Suite("D-12 package import boundary")
struct BoundaryLintTests {
    @Test func packageSourcesImportOnlyFoundation() throws {
        // Resolve the package Sources dir relative to #filePath, scan *.swift,
        // assert every `import X` line has X == "Foundation".
        let here = URL(fileURLWithPath: #filePath)                 // .../Tests/EndoInsightsTests/BoundaryLintTests.swift
        let sources = here.deletingLastPathComponent()             // EndoInsightsTests/
            .deletingLastPathComponent().deletingLastPathComponent() // EndoInsights/ (package root)
            .appendingPathComponent("Sources/EndoInsights")
        let files = try FileManager.default.contentsOfDirectory(at: sources, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "swift" }
        let forbidden = ["SwiftData", "SwiftUI", "UIKit", "CoreData"]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for line in text.split(separator: "\n") where line.trimmingCharacters(in: .whitespaces).hasPrefix("import ") {
                let module = line.replacingOccurrences(of: "import ", with: "").trimmingCharacters(in: .whitespaces)
                #expect(!forbidden.contains(module), "Forbidden import \(module) in \(file.lastPathComponent)")
            }
        }
    }
}
```
*Caveat:* relies on `#filePath` resolving to the source tree at test time (true for local SPM packages built from source). Verify during planning; if flaky, fall back to Form B.

**Form B — grep lint** (CI/script, complements A):
```bash
# Fails if any package source imports a forbidden module. Run after xcodegen generate.
! grep -REn 'import[[:space:]]+(SwiftData|SwiftUI|UIKit|CoreData)' EndoInsights/Sources/
```

Recommend **Form A** (runs inside `xcodebuild test -scheme EndoApp`, satisfies SC3's "in the suite") with Form B available as a CI guard.

## The XcodeGen Recipe (THE structural crux — concrete + verified)

### `EndoInsights/Package.swift`
```swift
// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "EndoInsights",
    platforms: [.iOS(.v18)],                     // match the app's deployment target
    products: [
        .library(name: "EndoInsights", targets: ["EndoInsights"]),
    ],
    targets: [
        .target(
            name: "EndoInsights"
            // NO dependencies → cannot link SwiftData/SwiftUI. THIS is the SC1 boundary.
        ),
        .testTarget(
            name: "EndoInsightsTests",
            dependencies: ["EndoInsights"]
        ),
    ]
)
```

### `project.yml` additions (verified syntax)
```yaml
# 1. Top-level: register the local package by path (dir containing Package.swift).
#    [VERIFIED: XcodeGen ProjectSpec — packages: <name>: path: <dir-with-Package.swift>]
packages:
  EndoInsights:
    path: EndoInsights

targets:
  EndoApp:
    # ...existing...
    dependencies:
      # 2. App depends on the package library product.
      #    product: defaults to the package name; EndoInsights lib == package name, so it's optional.
      #    [VERIFIED: XcodeGen ProjectSpec — dependencies: - package: <name> [product: <lib>]]
      - package: EndoInsights
    scheme:
      testTargets:
        - EndoAppTests
        - EndoAppUITests
        # 3. THE KEY LINE — run the package's Swift Testing tests in the app scheme.
        #    Syntax: package: <local-package-name>/<test-target-name>
        #    [VERIFIED: XcodeGen ProjectSpec + CHANGELOG 2.27.0 "Support test target for local Swift Package";
        #     installed XcodeGen 2.45.4 supports it]
        - package: EndoInsights/EndoInsightsTests
```

### How the package tests run in `xcodebuild test -scheme EndoApp` — the answer to the crux

- **They run via the app scheme.** XcodeGen 2.27.0+ generates the scheme's test action with the package's `EndoInsightsTests` target included (the `package: EndoInsights/EndoInsightsTests` entry). The installed tool is 2.45.4, so this is supported [VERIFIED: CHANGELOG]. `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` then compiles and runs `EndoAppTests`, `EndoAppUITests`, **and** `EndoInsightsTests` in one invocation — satisfying SC3 with the project's existing command, unchanged.
- **The package's `iOS(.v18)` platform + a Simulator destination matters:** because the test runs through the iOS app scheme on a Simulator destination, the package builds for iOS (not macOS), so no host-platform mismatch. Keep `platforms: [.iOS(.v18)]` in `Package.swift`.
- **SC1 compiler boundary holds regardless:** the `EndoInsights` target has no SwiftData/SwiftUI dependency in `Package.swift`, so any such import fails to compile — independent of how tests are scheduled.

### If the `package:` scheme test target does NOT run (the fallback — recommend keeping in the plan)
A known history of friction exists ([XcodeGen #1050](https://github.com/yonaskolb/XcodeGen/issues/1050), [#983](https://github.com/yonaskolb/XcodeGen/issues/983)); older `package: + product:` forms didn't include the tests. The installed 2.45.4 should be fine with the `package: Name/TestTarget` string form, but the plan should include a **verification task**: after `xcodegen generate`, run `xcodebuild test -scheme EndoApp ...` and confirm `EndoInsightsTests` cases actually execute (grep the test log for the suite name).

**Cleanest fallback that still satisfies SC1 + SC3** if the SPM scheme test target won't run on this toolchain:
- Add `EndoInsights` as an XcodeGen **`framework`** project target (Foundation-only, no SwiftData/SwiftUI in its `sources` → the link boundary is preserved) plus a companion **`bundle.unit-test`** target `EndoInsightsTests` listed in the app scheme's `testTargets` (exactly like `EndoAppTests`). The app target depends on the framework. This is the *most reliable* way to guarantee SC3, at the cost of `EndoInsights` not being a literal SPM package (a soft deviation from D-09's wording, but it preserves D-09's *intent* — a compiler-enforced boundary). Treat this strictly as the fallback; the real SPM package is the recommendation given the supported tool version.

## Project Constraints (from CLAUDE.md / config)
- **GSD workflow enforcement:** all file changes go through a GSD command (this is a planning-research artifact, compliant).
- **`security_enforcement: true`, ASVS level 1, block on high** (config.json) — see Security Domain below.
- **`nyquist_validation: true`** — Validation Architecture section required (below).
- **Privacy/claims posture (PROJECT.md + ClaimsPolicy):** Insight copy must be descriptive, non-causal, non-gendered; pass `ClaimsPolicy.forbiddenVerbs`/`forbiddenGenderedTokens`. No third-party packages (privacy + maintenance constraint) — honored (no registry deps added).
- **No SwiftData/SwiftUI in the package** (D-09, SC1) — compiler-enforced + lint.

## Runtime State Inventory

> Phase 5 is greenfield code (a new package + one new Repository method) — NOT a rename/refactor/migration. This section is included only to confirm no hidden runtime state is implicated.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no schema change; `daySnapshots()` is a read-only projection over existing `SchemaV1/V2/V3` rows. No new `@Model`, no migration. | None |
| Live service config | None — no CloudKit/n8n/external config touched. | None |
| OS-registered state | None. | None |
| Secrets/env vars | None. | None |
| Build artifacts | The generated `*.xcodeproj` (gitignored, regenerated by `xcodegen generate`) gains the package reference + scheme test target. Re-run `xcodegen generate` after editing `project.yml`. SPM may create `EndoInsights/.build` / a resolved-package cache (gitignore it). | Re-run `xcodegen generate`; add `EndoInsights/.build` to `.gitignore` |

**Verified:** no data migration, no CloudKit schema change, no runtime registration. The only "state" is the regenerated project file + an SPM build cache.

## Common Pitfalls

### Pitfall 1: Package test target silently not running in `xcodebuild test -scheme EndoApp`
**What goes wrong:** the scheme generates but `EndoInsightsTests` cases never execute (so SC3 looks green-by-omission). Historic XcodeGen friction with package test targets ([#1050](https://github.com/yonaskolb/XcodeGen/issues/1050)).
**Why it happens:** wrong syntax (`product:` instead of `Name/TestTarget`), an older XcodeGen, or a `.xctestplan` that doesn't reference the package target.
**How to avoid:** use the `- package: EndoInsights/EndoInsightsTests` string form; confirm `xcodegen --version` ≥ 2.27.0 (it's 2.45.4); add a verification task that greps the test log for the `EndoInsightsTests`/`@Suite` name and asserts at least one case ran. Fallback: the framework-target approach.
**Warning signs:** test count doesn't increase after adding the package; "Module not compiled for testing" errors.

### Pitfall 2: `nil → 0` coercion in means (honesty violation, D-03)
**What goes wrong:** an unlogged stress/severity gets summed as 0, dragging the mean down and fabricating data.
**How to avoid:** every mean's denominator counts only non-nil readings; mirror `FactorDay`'s "absence ≠ 0." Add a fixture with logged-0 vs unlogged-nil and assert they produce different means.
**Warning signs:** a day with no stress reading changes the stress mean.

### Pitfall 3: Phantom bleeding/cycle trend under suppression (SC3)
**What goes wrong:** the engine emits a "0% bleeding days" trend for a suppressed user — a phantom cycle signal the whole product is supposed to avoid (PITFALLS Pitfall 1, FOUNDATIONAL).
**How to avoid:** gate the bleeding trend on `!suppressed && bleedingDayCount >= 1`; the SC3 fixture asserts zero bleeding/cycle Insights. Mirror `CycleMath.summarize`'s suppressed short-circuit.
**Warning signs:** the suppressed fixture produces any Insight with a bleeding/cycle signal.

### Pitfall 4: Non-deterministic `Insight.id` breaking `Equatable` fixtures
**What goes wrong:** `id = UUID()` makes every run produce different ids → exact-equality assertions fail or get weakened.
**How to avoid:** derive `id` deterministically (hash of `kind`+`window`+signal key) OR assert on a projection that excludes `id`. Recommend deterministic ids so the whole `Insight` stays `Equatable`.

### Pitfall 5: Enum duplication drift (the Option-B trap)
**What goes wrong:** if the package duplicates `SymptomType`/etc. instead of sharing the app's, the two drift and the Repository mapping silently mismaps a case.
**How to avoid:** share one definition (Option C — move to package + re-export). Make the enum-sourcing decision an explicit, verified planning task (whole-suite build green after the move).

## Code Examples

### Engine signature + window helper (mirrors CycleMath)
```swift
// Source: pattern from Sources/EndoApp/Domain/CycleMath.swift (VERIFIED: codebase)
public enum TrendsEngine {
    public static func trends(from snapshots: [DaySnapshot], asOf today: Date,
                              calendar: Calendar = .current) -> [Insight] { ... }

    static func window(_ days: Int, endingAt today: Date, calendar: Calendar) -> ClosedRange<Date> {
        let end = calendar.startOfDay(for: today)
        let start = calendar.date(byAdding: .day, value: -(days - 1), to: end)!
        return start...end
    }
}
```

### `@Test` against the suppressed fixture (SC3)
```swift
// Source: pattern from Tests/EndoAppTests/CycleMathTests.swift (VERIFIED: codebase)
@Suite("SC3 suppressed / no-bleeding yields no cycle trend")
struct SuppressedFixtureTests {
    @Test func suppressedNoBleeding_emitsNoBleedingTrend() {
        let anchor = Date(timeIntervalSince1970: 1_700_000_000)
        let days = suppressedNoBleedingFixture(monthsBack: 8, anchor: anchor)
        let insights = TrendsEngine.trends(from: days, asOf: anchor)
        #expect(!insights.contains { $0.headline.localizedCaseInsensitiveContains("bleed") })
        #expect(!insights.contains { $0.kind == .cyclePhase })
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Insights engine over `@Model` objects / `ModelContext` | Pure engine over `Sendable` value snapshots | Project's ARCHITECTURE.md decision (2026-06-03) | The testability seam; SC1 depends on it |
| XCTest | Swift Testing (`@Test`/`#expect`) | Xcode 16+ | Project default; all package tests use it |
| Package test target unrunnable in app scheme (XcodeGen <2.27.0) | `scheme.test.targets: - package: Pkg/PkgTests` | XcodeGen 2.27.0 (installed 2.45.4) | SC3 satisfiable in the existing single test command |

**Deprecated/outdated:** the older XcodeGen `testTargets: - package: Lib product: LibTests` form ([#1050](https://github.com/yonaskolb/XcodeGen/issues/1050)) — superseded by the `Name/TestTarget` string form.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `package: EndoInsights/EndoInsightsTests` in the app scheme runs the package tests under `xcodebuild test -scheme EndoApp` on this toolchain | XcodeGen Recipe | If wrong, SC3 needs the framework-target fallback (documented). Mitigated by a planned verification task. Tool version (2.45.4 ≥ 2.27.0) strongly supports it. |
| A2 | Move-the-7-enums-into-the-package (Option C, re-export) is the cleanest sharing approach | Enum sourcing | If `@_exported import`/typealias is awkward, app churn rises; needs a small spike. |
| A3 | `≥ 3 logged days` per-trend min-data threshold | Trend Math | Explicitly Claude's discretion (D-08); a different value is fine — just make it a tunable named constant. |
| A4 | `hasNote: Bool` (presence only), not note text, in `DaySnapshot` | Contract Design | D-01 says "hasNote: Bool" — low risk; confirms presence-only. |
| A5 | Deterministic `Insight.id` (vs random UUID) | Pitfall 4 | If a random id is required for some downstream reason, equality assertions must exclude `id`. |
| A6 | `PainPoint` value-struct name in the package despite the `SchemaV1.PainPoint` `@Model` name clash (different modules) | Layout note | Low risk (separate modules); a rename (`PainPointSnapshot`) is a trivial alternative. |
| A7 | `suppressed`/bleeding gating: `!suppressed && bleedingDayCount >= 1` | Trend Math / SC3 | Core SC3 mechanism; mirrors proven `CycleMath` short-circuit. Low risk. |
| A8 | `direction` omitted for `allTime` (no prior window) | Trend Math | Claude's discretion (D-07); first-half/second-half is an alternative. |

## Open Questions (RESOLVED)

Both resolved during planning (plans 05-01..05-03): Q1 → 05-01 Task 1 package-test-in-scheme smoke + framework fallback; Q2 → Option D chosen (package-local enums + boundary mapping + enum-parity test), Option C not pursued.

1. **Does `package: EndoInsights/EndoInsightsTests` actually execute under `xcodebuild test` on Xcode 26 / this exact toolchain?**
   - What we know: XcodeGen 2.45.4 supports the syntax (CHANGELOG 2.27.0); the spec documents it.
   - What's unclear: end-to-end execution on this machine's Xcode hasn't been run in this research session (no build performed).
   - Recommendation: make the *first* engine task a thin walking-skeleton (one trivial `@Test` in `EndoInsightsTests`) + a verification step that the test log shows it ran. If it doesn't, switch to the framework-target fallback before building the real engine.

2. **Enum sourcing mechanics (Option C).**
   - What we know: the 7 enums are Foundation-only and eligible to move.
   - What's unclear: the cleanest way to keep existing `EndoApp` call sites compiling after the move (`@_exported import` vs typealiases vs just `import EndoInsights` everywhere).
   - Recommendation: a small dedicated task with a whole-suite-green gate.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| XcodeGen | Generating the project + wiring the package/scheme | ✓ | 2.45.4 | — (none needed; ≥ 2.27.0) |
| SwiftPM (swift-tools 6.0) | `EndoInsights/Package.swift` | ✓ (Xcode 26 toolchain, Swift 6.0 per project.yml) | 6.0 | — |
| Swift Testing | Package + app tests | ✓ (in toolchain; 18 test files already use it) | Xcode 16+ | — |
| iOS 18 Simulator (iPhone 16) | `xcodebuild test` destination | Assumed present (project's standard destination) | iOS 18 | Any installed iOS 18 simulator |

**Missing dependencies with no fallback:** none identified.
**Missing dependencies with fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`/`@Suite`) — bundled with the Xcode 16+/26 toolchain |
| Config file | none — XcodeGen-generated scheme; package test target wired via `project.yml` `scheme.test.targets` |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INST-01 | `TrendsEngine` emits per-symptom/per-factor descriptive trends over 7/30/90/all-time windows | unit | `xcodebuild test -scheme EndoApp -destination '…iPhone 16' -only-testing:EndoInsightsTests/TrendsEngineTests` | ❌ Wave 0 |
| INST-01 (SC1) | Package imports only Foundation — no SwiftData/SwiftUI (compiler + lint) | unit/lint | `-only-testing:EndoInsightsTests/BoundaryLintTests` (+ grep guard) | ❌ Wave 0 |
| INST-01 (SC2) | Windows anchored on absolute calendar dates, not cycle phase | unit | `-only-testing:EndoInsightsTests/TrendsEngineTests` (window-edge cases) | ❌ Wave 0 |
| INST-01 (SC3) | Suppressed/no-bleeding fixture yields no bleeding/cycle trend; runs in the suite | unit | `-only-testing:EndoInsightsTests/SuppressedFixtureTests` | ❌ Wave 0 |
| INST-01 (mapping) | `Repository.daySnapshots()` maps store → `[DaySnapshot]` honestly (nil≠0, suppressed propagates) | unit (in-memory ModelContainer) | `-only-testing:EndoAppTests/DaySnapshotMappingTests` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `xcodebuild test -scheme EndoApp -destination '…iPhone 16' -only-testing:EndoInsightsTests` (package-only, fast)
- **Per wave merge:** full suite `xcodegen generate && xcodebuild test -scheme EndoApp -destination '…iPhone 16'`
- **Phase gate:** full suite green (app + package tests) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `EndoInsights/Package.swift` — the package + the compiler boundary (SC1)
- [ ] `project.yml` package wiring + `scheme.test.targets: - package: EndoInsights/EndoInsightsTests` — so package tests run in the suite (SC3)
- [ ] **Verification spike:** one trivial `@Test` in `EndoInsightsTests` + confirm it executes under `xcodebuild test -scheme EndoApp` (de-risks Open Question 1)
- [ ] `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` — fixture builders incl. `suppressedNoBleedingFixture` (SC3)
- [ ] `EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift` — D-12 import lint
- [ ] `Tests/EndoAppTests/DaySnapshotMappingTests.swift` — app-side mapping honesty (in-memory `ModelContainer`, mirror `FactorSnapshotTests`)

## Security Domain

> `security_enforcement: true`, ASVS level 1, block on high (config.json). Phase 5 is a pure in-memory computation library + a read-only store projection — no auth, no network, no new persistence, no user input parsing beyond already-validated stored values. The attack surface is minimal but the input-validation + privacy posture still apply.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in this phase (the app has no accounts by design) |
| V3 Session Management | no | N/A |
| V4 Access Control | yes (light) | `daySnapshots()` MUST `assertConsented()` like every sibling Repository read (ONBD-01 defense-in-depth) — health data not read pre-consent |
| V5 Input Validation | yes | The engine consumes already-clamped/validated stored values; defensive decode of raw enum strings → `.other`/`nil` (mirror existing `init(raw:)`); the boundary-lint test reads files defensively |
| V6 Cryptography | no | No crypto; never hand-roll (none needed) |

### Known Threat Patterns for a pure on-device Swift insights package
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health data (Art. 9) leaking into logs / the analysis layer | Information Disclosure | `DaySnapshot` carries `hasNote: Bool` (no free-text prose); the engine never logs values (V7 no-logging posture already in `CycleMath`); no `print`/health values in output |
| Health data processed before consent | Elevation / policy | `daySnapshots()` asserts consent (V4), matching every existing Repository read |
| Malformed/corrupt stored raw values crashing the projection | Denial of Service | Defensive decode (`init(raw:)` → `.other`/`nil`) — never force-unwrap a decoded enum, mirroring `makeFactorDay` |
| A future SwiftData/SwiftUI import smuggling store/UI coupling into the "pure" layer | Tampering (architecture integrity) | Compiler boundary (package has no such dependency) + import lint (D-12) |

**Block-on-high check:** no high-severity security findings for this phase — it adds no network, no auth, no new persistence, no untrusted input. The privacy-relevant controls (consent gate on the new read, no prose in the snapshot, no health values logged) are carried forward from established patterns.

## Sources

### Primary (HIGH confidence)
- Codebase (VERIFIED via Read/grep): `Sources/EndoApp/Domain/{FactorDay,CycleMath,CycleSummary}.swift`, `Sources/EndoApp/Persistence/Repository.swift`, the 7 domain enums, `Sources/EndoApp/Content/ClaimsPolicy.swift`, `project.yml`, `Tests/EndoAppTests/*` (18 files, Swift Testing) — the contract/engine/mapping templates + test conventions
- `xcodegen --version` → 2.45.4 (VERIFIED locally 2026-06-08)
- XcodeGen `ProjectSpec.md` (master) — `packages: path:`, `dependencies: - package: product:`, `scheme.test.targets: - package: Name/TestTarget` [VERIFIED]: https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md
- XcodeGen CHANGELOG — 2.15.0 local packages, 2.27.0 "Support test target for local Swift Package" [VERIFIED]: https://raw.githubusercontent.com/yonaskolb/XcodeGen/master/CHANGELOG.md
- `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`, `.planning/REQUIREMENTS.md`, `.planning/phases/05-*/05-CONTEXT.md` — the locked decisions + foundational pitfalls (no phantom cycle, absence≠0, honesty)

### Secondary (MEDIUM confidence)
- XcodeGen issue #1050 (package test target friction history) — informs the fallback + verification task: https://github.com/yonaskolb/XcodeGen/issues/1050
- XcodeGen issue #983 (test target from a Swift package in a scheme): https://github.com/yonaskolb/XcodeGen/issues/983
- Apple Developer Forums — Swift package tests in test plans (testability/ENABLE_TESTABILITY caveats): https://developer.apple.com/forums/thread/710374

### Tertiary (LOW confidence)
- General "test local Swift package via xcodebuild" community posts (Jesse Squires, belief-driven-design) — corroborating the test-host/scheme approach, not load-bearing.

## Metadata

**Confidence breakdown:**
- Standard stack (Swift/Foundation/Swift Testing/SwiftPM/XcodeGen): HIGH — all first-party, versions verified locally
- XcodeGen local-package + package-test-in-scheme recipe: HIGH — syntax verified against official ProjectSpec + CHANGELOG; installed version (2.45.4) exceeds the 2.27.0 requirement; one residual end-to-end-execution check deferred to a planned verification task (Open Question 1)
- Contract/engine design (`DaySnapshot`/`Insight`/`TrendsEngine`): HIGH — directly mirrors the proven `FactorDay`/`CycleMath`/`CycleSummary` templates in the codebase
- Enum-sourcing mechanics (Option C re-export): MEDIUM — eligible and recommended, but the cleanest re-export form needs a small spike
- Trend thresholds/direction details: MEDIUM — explicitly Claude's discretion (D-07/D-08); recommended starting values given

**Research date:** 2026-06-08
**Valid until:** ~2026-07-08 (stable; XcodeGen + Swift toolchain move slowly. Re-verify the `package:` scheme test-target execution if the Xcode/XcodeGen version changes.)
