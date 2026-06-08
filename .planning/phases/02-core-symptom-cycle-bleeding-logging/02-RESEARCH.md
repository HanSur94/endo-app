# Phase 2: Core Symptom & Cycle/Bleeding Logging - Research

**Researched:** 2026-06-08
**Domain:** SwiftUI + SwiftData logging surface (symptoms, pain body-map, bleeding events, derived cycle math) on the existing CloudKit-safe Phase-1 foundation; iOS 18 deployment / Xcode 26.5 (iOS 26.5 SDK) / Swift 6 strict concurrency
**Confidence:** HIGH for SwiftData additive-migration mechanics, the existing-code seams, and CloudKit model rules (all corroborated against live Phase-1 source + Apple docs + multiple independent SwiftData authors); HIGH for the derived-cycle algorithm design (pure-function, no external API needed); MEDIUM-HIGH for the body-map rendering technique (multiple working SwiftUI patterns, recommendation is the simplest robust one).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Curated **endo-specific symptom catalog** (NOT a generic list): pelvic/abdominal pain, dysmenorrhea, dyspareunia, GI/"endo belly" (bloating, nausea, constipation/diarrhea), urinary (dysuria/frequency), fatigue, brain fog, mood/low mood, headache/migraine, back & leg/radiating pain. Modeled as an extensible `enum SymptomType` + a free-text "other". Each item carries a short, plain-language, non-diagnostic label.
- **D-02:** **Severity = 0–10 scale** via slider/stepper, with quick presets (mild=3 / moderate=6 / severe=9). Absence of a symptom = "not logged," never "0/healthy." Each logged symptom = one record tied to a day.
- **D-03:** Logging is organized around a **DayLog** for a calendar date aggregating its symptom records, pain points, bleeding, and a note. Multiple symptoms per day.
- **D-04:** A **tappable body map** (front + back silhouette) with a curated set of **named regions** (lower abdomen/pelvis, L/R lower quadrant, lower back, hips, upper thighs, rectal/bowel, bladder, chest/diaphragm, shoulder). Tap → pain point + severity (D-02 scale). NOT pixel-precise. SwiftUI (vector shapes or asset image + overlay hotspots).
- **D-05:** Free-text **note per day** (length-bounded, plain local string — no injection surface).
- **D-06:** **Full edit & delete** of any past day's log, reflected immediately in the list and derived cycle view. Deletes are real removals; append-only at the event level preserved by modeling edits as new state, but the user-facing affordance is edit/delete of a day.
- **D-07:** **Bleeding/flow is a first-class, standalone event** (extend `BleedingEvent`): per-day flow intensity (spotting/light/medium/heavy) + optional "clots" flag. Independent of any predicted cycle.
- **D-08:** **Cycle is DERIVED from bleeding events**, never assumed. Period = run of consecutive bleeding days; cycle length = median of recent period-start-to-period-start gaps. Next-period estimate = **range + confidence level** (high/medium/low from regularity + cycle count), never a single hard date, never 28 by default.
- **D-09:** **Suppressed / no-cycle mode** (CYCL-03): a clear toggle reusing `TreatmentState`. When on: NO cycle-day, NO phase, NO period prediction anywhere; app stays fully useful. No phantom phase.
- **D-10:** **Cycle-day & phase are derived/optional with a confidence indicator** (CYCL-04). Shown only when enough bleeding history to be meaningful; otherwise hidden / "not enough data yet." Degrades gracefully for irregular data. Calendar-date is the anchor, never an assumed cycle position.
- **D-11:** Extend the existing **CloudKit-safe `VersionedSchema`** with `SchemaV2` ADDING the symptom/pain-point models (additive migration). CloudKit-safe rules still apply: all attributes optional/defaulted, NO `@Attribute(.unique)`, relationships optional + inverse, append-only events, app-generated UUIDs. Reuse Phase 1's `DayEntry`/`BleedingEvent`/`TreatmentState`, extend additively. Provide a SwiftData `SchemaMigrationPlan` (lightweight, additive).
- **D-12:** Follow Phase 1's **MV + Repository** pattern (no per-screen ViewModels; `@Query`/`@Observable`; logic behind `Repository` protocol + composition root). Extend `Repository` with symptom/pain/bleeding/day CRUD + derived-cycle computation as pure, testable functions.
- **D-13:** **Replace `WalkingSkeletonView`** with a real home screen: day list/history (with edit/delete) + a prominent "Log today" entry into the day-log editor + a cycle/bleeding summary area that respects suppressed mode.
- **D-14:** Reuse Phase 1's **`Terminology`** (non-gendered defaults + customization) and **`ClaimsPolicy`** (no diagnostic verbs; informational, non-causal copy). All Phase-2 copy passes the same choke-point.

### Claude's Discretion
- Exact visual design, the body-map rendering technique (vector vs. asset+hotspots), the precise region list, slider vs. segmented severity control, and the day-log editor layout. Gray *within* the decisions above. Review via simulator screenshots + iteration.

### Deferred Ideas (OUT OF SCOPE)
- Food/activity/sleep/med/mood-as-factor logging → Phase 3.
- Charts / trends / correlations / cycle-phase *insights* / flare prediction → Phases 5–8 (Phase 2's derived cycle is a minimal, honest indicator only, not an insight engine).
- HealthKit import of cycle data → Phase 9.
- Reminders / fast "same as yesterday" check-in → Phase 3 (CHCK).
- Private CloudKit sync → Phase 4 (`SyncMode.current` stays `.localOnly`; do NOT add iCloud/Background-Modes capability).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SYMP-01 | Log endo-specific symptoms with severity | `enum SymptomType` catalog (D-01) + 0–10 severity (D-02); `SymptomReading` append-event `@Model` under `SchemaV2`; entry UI = fast multi-select + per-symptom severity. §Standard Stack, §Data Model, §Code Examples. |
| SYMP-02 | Record pain location on a body map with severity | Tappable named-region body map (§Body Map); `PainPoint` `@Model` (regionRaw + severity); logic-level binding testable without rendering. |
| SYMP-03 | Free-text notes on a day's entry | Reuse `DayEntry.note` (already exists, length-bounded via the `TerminologyStore.maxDisplayNameLength` pattern). No new model needed for notes. |
| SYMP-04 | Edit or delete any past entry | `@Query` list + `modelContext.delete` + edit re-opens the day-log editor; immediate refresh via SwiftData observation (§Pattern Edit/Delete). |
| CYCL-01 | Log bleeding/flow as a first-class event, independent of prediction | Extend `BleedingEvent` additively (add `clots` flag; keep `flowRaw`); it already exists standalone (D-19). §Data Model. |
| CYCL-02 | Cycle tracking with predictions tolerating irregular cycles | Pure `CycleMath` functions: period runs → median gap → range estimate; irregular widens the range / lowers confidence. §Derived Cycle. |
| CYCL-03 | Suppressed / no-cycle mode keeps app fully useful | `TreatmentState` mode "suppressed"; `CycleMath` returns `.suppressed` → UI hides ALL cycle output. §Derived Cycle, §Validation. |
| CYCL-04 | Cycle-day shown as derived/optional with confidence indicator, never 28-day | `CycleSummary` value type with optional `cycleDay`/`phase` + `Confidence`; UI shows "not enough data yet" until meaningful. §Derived Cycle. |
</phase_requirements>

## Summary

This phase is overwhelmingly an **additive extension of an already-correct foundation**, not new infrastructure. Phase 1 already shipped exactly the seams Phase 2 needs: a `VersionedSchema` (`SchemaV1`) versioned from day one, CloudKit-safe `@Model` types (`DayEntry`, `BleedingEvent`, `TreatmentState`) with all-optional/defaulted attributes and app-generated UUIDs, a `Repository` protocol, a composition root (`AppDependencies`), the `Terminology`/`ClaimsPolicy` copy choke-points, an in-memory `ModelContainer` test seam, and an extensible XCUITest harness keyed on `-uitest-reset` + accessibility identifiers. The work is: (1) introduce `SchemaV2` that ADDS `SymptomReading` + `PainPoint` (and additively grows `BleedingEvent` with a `clots` flag), wired through a **lightweight** `SchemaMigrationPlan`; (2) build the day-log editor + home screen replacing `WalkingSkeletonView`; (3) build the **pure, value-type `CycleMath`** functions; (4) extend the test + E2E suites.

Two things carry real risk and are the endo-credibility signals: the **body map** (keep it discrete named regions, fully VoiceOver/Dynamic-Type accessible) and the **derived cycle that never assumes 28 days** (pure functions over bleeding events, returning an optional range + confidence, with a first-class suppressed mode that emits *nothing*). Both are achievable with first-party SwiftUI/Foundation only — **zero new third-party dependencies** are required or recommended (consistent with the project's privacy-first, near-zero-dependency mandate). Because `SyncMode.current` is `.localOnly` in this phase, the migration is a pure local SwiftData lightweight migration — but the schema MUST still obey every CloudKit rule now, because it becomes effectively immutable the moment sync flips on in Phase 4.

**Primary recommendation:** Add `SchemaV2` (additive: new `SymptomReading` + `PainPoint` models, `BleedingEvent` gains an optional `clots` flag) behind a `MigrationStage.lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)`; build a fast multi-symptom day-log editor (MV, no ViewModel) and a discrete-region accessible body map; implement the cycle as pure `CycleMath` functions returning `CycleSummary { phase?, cycleDay?, nextPeriod: ClosedRange<Date>?, confidence }` with a hard `.suppressed` short-circuit; replace `WalkingSkeletonView` with the real home screen; extend the Swift Testing + XCUITest suites with the new fixtures and accessibility ids.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Symptom/pain/bleeding persistence | SwiftData store (`SchemaV2` `@Model`) | Repository | Single source of truth; append-event records sync-safely later |
| Symptom/pain/note CRUD | Repository protocol | SwiftUI views via `@Query`/`modelContext` | D-12: logic behind protocol; simple writes can go direct via `modelContext` (MV) |
| Day-log editor UI / body map / severity controls | SwiftUI (MV) | — | Pure presentation; reads/writes the store via `@Query` + `modelContext`/Repository |
| Derived cycle math (runs, median, range, confidence, phase, suppression) | **Pure Swift `CycleMath` (no SwiftData, no SwiftUI)** | Repository (maps `@Model` → value snapshots in) | The testability seam; seed of the future insights engine; deterministic, time injected |
| Suppressed-mode gating | `TreatmentState` (store) → `CycleMath` → SwiftUI | — | First-class state short-circuits the math; UI never renders phantom cycle |
| Copy / labels (symptoms, cycle, confidence) | `Terminology` + `ClaimsPolicy` choke-points | SwiftUI | D-14: all user-facing strings audited for gendered tokens + medical-purpose verbs |
| Home screen routing | `AppGate.consented` branch → new home view | — | D-13: re-point the `.consented` branch from `WalkingSkeletonView` to the real home |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SwiftData | iOS 18 SDK (Xcode 26.5) | `@Model`, `@Query`, `VersionedSchema`, `SchemaMigrationPlan`, `MigrationStage` | First-party; already the project's persistence layer; additive migration is exactly the supported path. `[CITED: developer.apple.com/documentation/swiftdata]` |
| SwiftUI | iOS 18 | Day-log editor, body map (`Shape`/`Path`/`contentShape`), severity controls (`Slider`/`Stepper`), `@Query` lists, accessibility modifiers | First-party; mandated by project; body map + severity entry are all native primitives. `[CITED: developer.apple.com/documentation/swiftui]` |
| Foundation | system | `Calendar`/`Date` math for period runs, gaps, median cycle length, next-period range | Pure, deterministic, testable; no library needed for median-of-N gap math. `[VERIFIED: live Phase-1 source uses Foundation Calendar already]` |
| Swift Testing | Xcode 26.5 | Unit tests over in-memory `ModelContainer` + pure `CycleMath` fixtures | Already the project's unit-test framework (`Tests/EndoAppTests/*`). `[VERIFIED: live test source]` |
| XCTest / XCUITest | Xcode 26.5 | Extend the `-uitest-reset` E2E for the new logging flow | Already the project's E2E harness (`Tests/EndoAppUITests/ConsentFlowUITests.swift`). `[VERIFIED: live test source]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (none) | — | — | **No new third-party dependency is needed or recommended for this phase.** Body map, severity, cycle math, and persistence are all first-party. Adding a dependency would violate the project's near-zero-dependency / privacy mandate (PROJECT.md "What NOT to Use"). |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Discrete vector-`Shape` named-region body map | An asset image (front/back PNG/PDF) + overlaid invisible tappable region rectangles/shapes | Asset image is faster to make "look good" but harder to keep crisp at Dynamic Type / large accessibility sizes and harder to recolor for severity; vector `Shape`s recolor + scale + expose accessibility per-region cleanly. **Recommendation: vector `Shape`/`Path` hotspots** for accessibility + severity tinting. Asset-image overlay is an acceptable fallback if vector art is too costly — keep the region *model* identical either way. |
| Third-party `MuscleMap`-style SPM package | — | Real packages exist (e.g. melihcolpan/MuscleMap) but they are muscle-anatomy oriented, add a dependency, and bring UIKit interop the project doesn't want. Roll the (small) discrete-region map in-house. `[ASSUMED]` |
| `MigrationStage.custom` (willMigrate/didMigrate) | `MigrationStage.lightweight` | This phase is purely additive (new models + a new optional field) → **lightweight is correct and sufficient.** Custom is both unnecessary AND a future trap: `willMigrate`/`didMigrate` are **not called when CloudKit is enabled** (Phase 4). Stay lightweight. `[VERIFIED: Apple Developer Forums threads 742899/744491/748049 + multiple authors]` |

**Installation:** None. No package additions. (Do NOT add the iCloud/CloudKit or Background-Modes capability in this phase — `SyncMode.current` stays `.localOnly`.)

**Version verification:** SwiftData / SwiftUI / Swift Testing / XCUITest ship with the active toolchain (Xcode 26.5, build 17F42, verified in Phase 1 Plan 01 SUMMARY). No registry packages to verify; the Package Legitimacy Audit below is therefore N/A.

## Package Legitimacy Audit

> N/A for this phase. **No external packages are installed.** All capabilities use first-party Apple frameworks bundled with the Xcode 26.5 toolchain. No npm/PyPI/SPM registry fetch occurs, so there is no slopsquatting surface. If the planner later proposes any SPM dependency, gate it behind a `checkpoint:human-verify` task and run slopcheck against the SPM/GitHub source before adding it.

## Architecture Patterns

### System Architecture Diagram

```
[User taps "Log today" / taps a past day to edit]
        │
        ▼
  Day-Log Editor (SwiftUI, MV — no ViewModel)
   ├── Symptom multi-select ──► severity slider/preset per chosen symptom
   ├── Body Map (front/back) ──► tap named region ──► severity ──► PainPoint
   ├── Bleeding control ──────► flow intensity + clots flag ──► BleedingEvent
   └── Note field (bounded) ──► DayEntry.note
        │  writes via modelContext.insert / Repository
        ▼
  SwiftData ModelContext  (SchemaV2, local-only — SyncMode.localOnly / .none)
        │   @Model: DayEntry, BleedingEvent(+clots), TreatmentState,
        │           SymptomReading(new), PainPoint(new)
        ▼
  Local SQLite (in-memory under -uitest-reset)
        │
        ├─► @Query ──► Home screen: day history list (edit/delete), "Log today"
        │
        └─► Repository maps @Model → value snapshots
                 │   [BleedingDay] / [DaySnapshot]   (Sendable, no SwiftData)
                 ▼
            CycleMath  (PURE functions — no SwiftData, no SwiftUI, time injected)
                 │   periodRuns → medianCycleLength → nextPeriodRange + Confidence
                 │   suppressed? → short-circuit → emit NOTHING
                 ▼
            CycleSummary { phase?, cycleDay?, nextPeriod: ClosedRange<Date>?, confidence }
                 │
                 ▼
        Cycle/Bleeding summary area on Home (respects suppressed mode — D-09)
```

### Recommended Project Structure (additive to existing layout)
```
Sources/EndoApp/
├── Persistence/
│   ├── SchemaV1.swift                 # EXISTS — keep, frozen
│   ├── SchemaV2.swift                 # NEW — adds SymptomReading + PainPoint; lists all V2 models
│   ├── MigrationPlan.swift            # NEW — SchemaMigrationPlan, V1→V2 lightweight stage
│   ├── ModelContainer+Config.swift    # EDIT — Schema(versionedSchema: SchemaV2.self) + migrationPlan:
│   ├── Repository.swift               # EDIT — add symptom/pain/bleeding/day CRUD + snapshot reads
│   └── Models/
│       ├── DayEntry.swift             # EXISTS — reuse note (SYMP-03); maybe add inverse relations
│       ├── BleedingEvent.swift        # EDIT (additive) — add optional `clots: Bool?`
│       ├── TreatmentState.swift       # EXISTS — reuse "suppressed" mode (CYCL-03)
│       ├── SymptomReading.swift       # NEW — @Model (SchemaV2): type + 0–10 severity + day link
│       └── PainPoint.swift            # NEW — @Model (SchemaV2): body region + severity + day link
├── Domain/                            # NEW — pure value types + math (no SwiftData/SwiftUI import)
│   ├── SymptomType.swift             # enum catalog (D-01) + plain-language labels
│   ├── BodyRegion.swift             # enum named regions (D-04)
│   ├── FlowIntensity.swift          # enum spotting/light/medium/heavy
│   ├── CycleMath.swift             # PURE: runs, median, range, confidence, phase, suppression
│   └── CycleSummary.swift          # value type the UI renders
└── Features/
    ├── Home/
    │   └── HomeView.swift            # NEW — replaces WalkingSkeletonView (D-13)
    ├── DayLog/
    │   ├── DayLogEditorView.swift    # NEW — fast multi-symptom entry + note + bleeding
    │   ├── BodyMapView.swift         # NEW — discrete-region tappable map (SYMP-02)
    │   └── SeverityControl.swift     # NEW — 0–10 slider/preset (D-02), reusable
    └── Skeleton/
        └── WalkingSkeletonView.swift # DELETE/REPLACE (re-point AppGate.consented → HomeView)
```

### Pattern 1: Additive SchemaV2 + lightweight migration (D-11)
**What:** A new `VersionedSchema` (`SchemaV2`) that lists *all* models for the version — the unchanged ones (`DayEntry`, `TreatmentState`) plus the additively-grown `BleedingEvent` and the two new types. A `SchemaMigrationPlan` declares the version order and a single `MigrationStage.lightweight` V1→V2.
**When to use:** Any purely additive schema change (new models, new optional/defaulted fields). This is the entire Phase-2 schema delta.
**CloudKit/additive constraints (carry from Phase 1, non-negotiable):**
- Every new attribute optional or defaulted; NO `@Attribute(.unique)`.
- New relationships (if any) optional + explicit inverse; never `.deny`.
- App-generated `id: UUID = UUID()` for identity/idempotent upsert (no DB uniqueness).
- Lightweight only — do NOT use `MigrationStage.custom`; its `willMigrate`/`didMigrate` won't fire once CloudKit is on (Phase 4), so relying on it now would silently break later. `[VERIFIED: Apple Developer Forums 742899/744491/748049]`

```swift
// Source: developer.apple.com/documentation/swiftdata (VersionedSchema / SchemaMigrationPlan),
// adapted to the existing SchemaV1 nesting convention in this repo.

enum SchemaV2: VersionedSchema {
    static let versionIdentifier = Schema.Version(2, 0, 0)
    static var models: [any PersistentModel.Type] {
        // ALL models in the V2 world (unchanged + additively grown + new).
        [DayEntry.self, BleedingEvent.self, TreatmentState.self,
         SymptomReading.self, PainPoint.self]
    }
}

enum EndoMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self, SchemaV2.self] }
    static var stages: [MigrationStage] { [v1toV2] }
    // Purely additive → lightweight. (NOT .custom — willMigrate/didMigrate don't fire under CloudKit.)
    static let v1toV2 = MigrationStage.lightweight(
        fromVersion: SchemaV1.self,
        toVersion: SchemaV2.self
    )
}

// ModelContainer+Config.swift — wire the plan + bump to SchemaV2:
let schema = Schema(versionedSchema: SchemaV2.self)
let container = try ModelContainer(
    for: schema,
    migrationPlan: EndoMigrationPlan.self,
    configurations: configuration            // local-only .none in this phase
)
```

> **Repo nesting note:** Phase 1 nests `@Model` types as `SchemaV1.DayEntry` etc. (the models live in `extension SchemaV1 { @Model final class … }`). SwiftData's canonical pattern nests them *inside the VersionedSchema enum*. Because the same Swift class cannot belong to two enum namespaces, the cleanest additive path is: keep the existing class definitions, and have `SchemaV2.models` reference the *same* classes (they are additively compatible — adding an optional field to `BleedingEvent` keeps it the same model identity). Verify at plan time whether the existing nesting forces the V2 models to be defined as distinct nested types vs. shared top-level classes; the lightweight migration works either way as long as entity/attribute names are stable and changes are additive. This is the single most important thing to get right — flagged in Assumptions Log (A1).

### Pattern 2: Pure value-type cycle engine (seed of the insights engine)
**What:** `CycleMath` takes value-type inputs (`[BleedingDay]`, the suppressed flag, and an injected `today: Date`) and returns a `CycleSummary` value type. No SwiftData, no SwiftUI, no hidden `Date.now`.
**When to use:** All cycle derivation (CYCL-02/03/04). Keeps it deterministic and unit-testable against fixtures, exactly per ARCHITECTURE.md's "engine consumes value snapshots, not @Model" keystone.
**Trade-offs:** + Fully testable without a store/UI; portable to the future insights engine. − Requires a mapping step `@Model` → value snapshot in the Repository (a feature, not a cost).

```swift
// Source: derived from ARCHITECTURE.md Pattern 2 + the CYCL decisions. PURE.
public enum Confidence: Sendable { case none, low, medium, high }

public struct CycleSummary: Equatable, Sendable {
    public var isSuppressed: Bool          // D-09: when true, UI shows NOTHING cycle-related
    public var cycleDay: Int?              // optional/derived (CYCL-04) — nil unless meaningful
    public var nextPeriod: ClosedRange<Date>?   // a RANGE, never a single date (D-08)
    public var confidence: Confidence      // high/medium/low/none (CYCL-04)
    public var hasEnoughData: Bool         // false → "not enough data yet"
}

public enum CycleMath {
    /// PURE. `today` injected for determinism. `bleedingDays` are calendar days with any flow.
    public static func summarize(
        bleedingDays: [Date],
        isSuppressed: Bool,
        today: Date,
        calendar: Calendar = .current
    ) -> CycleSummary {
        // D-09 hard short-circuit: suppressed → emit nothing derivable.
        if isSuppressed {
            return CycleSummary(isSuppressed: true, cycleDay: nil,
                                nextPeriod: nil, confidence: .none, hasEnoughData: false)
        }
        let runs = periodRuns(bleedingDays, calendar: calendar)     // consecutive-day runs
        let starts = runs.map(\.first!)                              // period-start dates
        let gaps = consecutiveGaps(starts, calendar: calendar)      // start→start day gaps
        // < 2 cycles → not meaningful (CYCL-04 "not enough data yet")
        guard gaps.count >= 2 else {
            return CycleSummary(isSuppressed: false, cycleDay: nil,
                                nextPeriod: nil, confidence: .none, hasEnoughData: false)
        }
        let median = medianInt(gaps)
        let spread = interquartileSpread(gaps)                       // regularity measure
        let confidence = confidenceLevel(cycleCount: gaps.count, spread: spread)
        // Range, never a hard date: center on lastStart+median, widen by spread + (lower conf → wider).
        let lastStart = starts.last!
        let nextRange = predictRange(lastStart: lastStart, median: median,
                                     spread: spread, confidence: confidence, calendar: calendar)
        let cycleDay = calendar.dateComponents([.day], from: lastStart, to: today).day.map { $0 + 1 }
        return CycleSummary(isSuppressed: false, cycleDay: cycleDay,
                            nextPeriod: nextRange, confidence: confidence, hasEnoughData: true)
    }
    // periodRuns / consecutiveGaps / medianInt / interquartileSpread / confidenceLevel /
    // predictRange are small private pure helpers — each independently unit-tested.
}
```

### Pattern 3: Fast multi-symptom day-log entry (MV, no ViewModel)
**What:** The editor reads/writes the store directly (`@Query` + `modelContext`), per D-12. Symptoms are a multi-select grid of catalog chips; tapping one reveals a 0–10 severity control with mild/moderate/severe presets (D-02). One `SymptomReading` per (day, symptom). Keep it few-taps (the adherence requirement).
**Anti-pattern avoided:** A per-screen `ObservableObject` ViewModel re-implementing what `@Query` already does (ARCHITECTURE Anti-Pattern 2).

```swift
// Source: ARCHITECTURE.md Pattern 1 (MV) + live WalkingSkeletonView.swift conventions.
struct DayLogEditorView: View {
    @Environment(\.modelContext) private var context
    let day: Date
    @Query private var existingForDay: [SymptomReading]   // predicate-filtered to `day`
    // multi-select chips → on select, insert/update a SymptomReading(type, severity, day)
    // write directly; no ViewModel.
}
```

### Pattern 4: Edit/delete with immediate refresh (SYMP-04, D-06)
**What:** The home history list is `@Query(sort: \DayEntry.date, order: .reverse)`. Delete = `modelContext.delete(entry)` (or repository) → SwiftData observation re-renders the list AND the derived cycle summary automatically (no manual refresh). Edit = navigate back into `DayLogEditorView` for that day. Cascade-delete a day's `SymptomReading`/`PainPoint` (model edits as new state; user affordance is edit/delete of a day, D-06).
**Trade-off:** Because relationships can't use `.deny` and must be optional+inverse (CloudKit rule), use `.cascade` or `.nullify` deliberately and delete child events explicitly in the Repository to guarantee the cycle math recomputes from a clean event set.

### Anti-Patterns to Avoid
- **`MigrationStage.custom` for this additive change** — unnecessary and a Phase-4 trap (hooks don't fire under CloudKit). Use `.lightweight`.
- **Any `@Attribute(.unique)` or non-optional/default-less field on the new models** — breaks CloudKit sync silently the moment Phase 4 flips it on; the schema is immutable post-launch.
- **A stored `cycleDay`/`cyclePhase`/`cycleLength` column or a `= 28` default** — the #1 foundational pitfall. Cycle is derived in `CycleMath`, never persisted as a required column (Phase 1 already enforces this; do not regress).
- **Computing cycle math inside a SwiftUI `body`/`.onAppear`** — non-deterministic, re-runs per render. Compute in `CycleMath` and feed the view a `CycleSummary`.
- **Per-screen MVVM ViewModels** wrapping SwiftData (D-12 / ARCHITECTURE Anti-Pattern 2).
- **A free-draw pixel-precise pain map** — D-04 says discrete named regions (faster, accessible, analyzable later).
- **Treating "no symptom logged" as "severity 0 / healthy"** — D-02: absence = not logged. Never write a 0 record to mean "fine."

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Schema evolution V1→V2 | A manual "detect old store, copy rows, rewrite" routine | `SchemaMigrationPlan` + `MigrationStage.lightweight` | First-party, additive-safe, the only CloudKit-compatible path; manual copy risks data loss |
| List ↔ store sync after edit/delete | Manual reload / notification plumbing | `@Query` (SwiftData observation auto-refreshes) | Already the project's pattern; zero boilerplate |
| Tappable region hit area | Manual `GeometryReader` point-in-polygon math | `Shape` + `.contentShape(...)` + `.onTapGesture` per region | `contentShape` defines the hit-test region cleanly; native, accessible `[CITED: hackingwithswift.com/quick-start/swiftui/how-to-control-the-tappable-area-of-a-view-using-contentshape]` |
| Median / gap stats | A stats dependency (Accelerate is overkill for N≈3–12 gaps) | Plain Swift sort + middle element | Trivial pure code; Accelerate is for later large-history insights, not N≈dozen gaps |
| Date/day-bucket math | Manual epoch arithmetic | `Calendar.dateComponents` / `startOfDay` / `isDate(_:inSameDayAs:)` | Handles DST/timezone/locale; live code already uses `Calendar` |
| Length-bounding the note | Ad-hoc truncation scattered in views | The existing `TerminologyStore.maxDisplayNameLength`-style bound at the write seam | Pattern already exists in `SettingsView.commitFreeText` (threat T-01-05) |

**Key insight:** Phase 2 introduces *no* problem that lacks a first-party or already-in-repo solution. The only genuinely new code is the small pure `CycleMath` and the discrete body-map geometry — both deliberately hand-rolled because they are tiny, domain-specific, and dependency-free by design.

## Runtime State Inventory

> This is an **additive greenfield-within-an-app** phase (new models + new UI), not a rename/refactor/migration of existing strings or external state. The only "migration" is the SwiftData V1→V2 schema bump, fully covered by Pattern 1. For completeness:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Local SwiftData store only (`SyncMode.localOnly` → `.none`). No CloudKit records exist yet (Phase 4). New `SymptomReading`/`PainPoint` rows + a `clots` field on `BleedingEvent`. | Lightweight additive migration (Pattern 1); no data backfill needed |
| Live service config | **None** — no external services, no CloudKit container active, no HealthKit (Phase 9). | None |
| OS-registered state | **None** — no Task Scheduler/launchd/notifications in this phase (reminders are Phase 3). | None |
| Secrets/env vars | **None** — no secrets; `cloudKitContainerIdentifier` exists but is inert (`SyncMode.localOnly`). | None — do NOT activate it this phase |
| Build artifacts | The XcodeGen-generated `*.xcodeproj` is regenerated (`xcodegen generate`); new source files must land under `Sources/EndoApp/**` which `project.yml` already globs. New test files under `Tests/EndoAppTests` / `Tests/EndoAppUITests` (also globbed). | Run `xcodegen generate` after adding files; no `project.yml` target change required (sources are folder-globbed). Verify at plan time. |

**Nothing found in 3 of 5 categories** — verified by: no CloudKit/HealthKit capability in `project.yml`, `SyncMode.current == .localOnly`, no notification/scheduler code in `Sources/EndoApp`.

## Common Pitfalls

### Pitfall 1: Re-introducing a 28-day / phantom-cycle assumption
**What goes wrong:** A `cycleDay`/`cyclePhase` stored column, a `cycleLength = 28` default, or a code path that always produces a "next period" date or a "phase" even for suppressed/sparse users.
**Why it happens:** Every tutorial/competitor models the textbook cycle; it demos well with regular fake data.
**How to avoid:** Cycle stays **derived** in pure `CycleMath`. `CycleSummary.cycleDay`/`phase`/`nextPeriod` are all **optional**. Suppressed short-circuits to emit nothing. < 2 recorded cycles → `hasEnoughData = false` → UI shows "not enough data yet," never a fabricated value. Prediction is always a **range + confidence**, widening as regularity drops.
**Warning signs:** A non-optional cycle field; a divide-by-cycle-length; a test suite with only regular cyclers; a UI that shows a phase for the suppressed fixture.

### Pitfall 2: A non-CloudKit-safe new model that silently breaks Phase 4 sync
**What goes wrong:** Adding `@Attribute(.unique)`, a non-optional/default-less field, an ordered or `.deny` relationship to the new `SymptomReading`/`PainPoint` — invisible while `SyncMode.localOnly`, catastrophic when sync flips on (schema is immutable post-launch).
**Why it happens:** Habitual "clean model" instincts; the rules don't bite locally.
**How to avoid:** Mirror the existing `BleedingEvent`/`DayEntry` exactly — every property optional/defaulted, `id: UUID = UUID()`, no `.unique`, any relationship optional + inverse, enums stored as optional backing `String` (`flowRaw`/`modeRaw` precedent). The Phase-1 `SchemaRoundTripTests` style + a grep acceptance check guard this.
**Warning signs:** `.unique` anywhere; a non-optional `var` without `=`; an enum stored directly instead of via a `…Raw: String?`.

### Pitfall 3: Choosing `MigrationStage.custom` and relying on its hooks
**What goes wrong:** Using `.custom(...willMigrate:didMigrate:)` — fine locally now, but those closures **do not execute when CloudKit is enabled** (Phase 4), so any logic placed there silently never runs on synced users.
**How to avoid:** This change is additive → `.lightweight` is correct and sufficient. Never put data-transform logic in migration hooks for a CloudKit-bound store.
**Warning signs:** Any `MigrationStage.custom` in `MigrationPlan.swift`.

### Pitfall 4: An inaccessible body map (excludes the exact worst-day users)
**What goes wrong:** Tap-only regions with no VoiceOver labels, fixed tiny text, low-contrast severity tints — locks out users in pain/migraine/fatigue (PITFALLS UX row).
**How to avoid:** Each region is an accessibility element with a plain-language label + the current severity as its value, a ≥44pt effective hit target via `.contentShape`, Dynamic-Type-safe labels, and a non-color-only severity indication (number + tint, not tint alone). Provide a non-map list fallback to set the same `PainPoint` so the feature is operable without the visual map. The body-map *binding* (region → severity → `PainPoint`) is testable at the logic level without rendering.
**Warning signs:** A region with no `.accessibilityLabel`/`.accessibilityValue`; severity conveyed by color only; hit targets < 44pt.

### Pitfall 5: Medical-purpose or gendered copy slipping into the new surfaces
**What goes wrong:** New labels ("predict," "trigger," "your period is late," "women") bypass the Phase-1 choke-points.
**How to avoid:** Every user-facing string in the day-log editor, body map, and cycle summary resolves bleeding/cycle nouns through `Terminology` and is audited against `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens`. Confidence/prediction copy uses informational framing ("estimated range," "based on your logs," "not enough data yet"). Add a copy-audit test mirroring `TerminologyTests`.
**Warning signs:** Hard-coded "period"/"woman" strings; "predict"/"trigger"/"will" in cycle copy; a single hard date instead of a range.

## Code Examples

### Discrete-region accessible body map hotspot (SYMP-02, D-04)
```swift
// Source: contentShape hit-testing — hackingwithswift.com/quick-start/swiftui/how-to-control-the-tappable-area-of-a-view-using-contentshape (CITED)
// One region = one Shape + an accessibility element. Severity tints fill; value spoken by VoiceOver.
struct BodyRegionHotspot: View {
    let region: BodyRegion
    let severity: Int?                     // nil = not marked
    let onTap: (BodyRegion) -> Void

    var body: some View {
        region.path                        // a Path in the silhouette's coordinate space
            .fill(tint(for: severity))
            .contentShape(region.path)     // hit-test == the visible region (≥44pt effective)
            .onTapGesture { onTap(region) }
            .accessibilityElement()
            .accessibilityLabel(Text(region.plainLabel))         // e.g. "Lower abdomen"
            .accessibilityValue(Text(severity.map { "severity \($0) of 10" } ?? "not marked"))
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier("bodymap.region.\(region.rawValue)")  // E2E hook
    }
}
```

### One symptom reading per (day, symptom), 0–10 severity (SYMP-01, D-02)
```swift
// Source: live BleedingEvent.swift CloudKit-safe conventions, mirrored for the new model.
extension SchemaV2 {                       // (or top-level; see Pattern 1 nesting note)
    @Model final class SymptomReading {
        var id: UUID = UUID()              // app-generated, NOT .unique (CloudKit-safe)
        var occurredOn: Date = Date()      // the calendar day this reading belongs to
        var typeRaw: String?               // SymptomType backing string (optional, CloudKit-safe)
        var severity: Int?                 // 0...10 (D-02); nil = unspecified, never "0 = healthy"
        var note: String?
        init(id: UUID = UUID(), occurredOn: Date = .now,
             typeRaw: String? = nil, severity: Int? = nil, note: String? = nil) {
            self.id = id; self.occurredOn = occurredOn
            self.typeRaw = typeRaw; self.severity = severity; self.note = note
        }
    }
}
```

### Additive growth of BleedingEvent — add `clots`, keep everything else (CYCL-01, D-07)
```swift
// EDIT BleedingEvent.swift — purely additive, optional, defaulted (lightweight-migration-safe).
var clots: Bool? = nil                     // optional flag; absence ≠ false-as-fact
// flowRaw already exists: "spotting" | "light" | "medium" | "heavy" (FlowIntensity backing string)
```

### Extending the XCUITest E2E for the new logging flow
```swift
// Source: live ConsentFlowUITests.swift harness — same -uitest-reset + a11y-id pattern, new ids.
// New identifiers to add in the views: "home.logTodayButton", "daylog.symptom.<type>",
// "daylog.severitySlider", "bodymap.region.<region>", "home.dayRow.<index>",
// "home.cycleSummary" (and a "home.cycleSummary.suppressed"/"...notEnoughData" state).
// Flow: launch with -uitest-reset → grant consent → tap "home.logTodayButton" →
//        select a symptom → set severity → tap a body region → set severity → save →
//        assert the day appears in history → edit it → delete it → assert it's gone.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single mutable "today" record per day | Append-only event records (one per symptom/pain/bleed) | CloudKit LWW constraint | Two devices editing different symptoms don't clobber; already the Phase-1 posture |
| `MigrationStage.custom` for any schema change | `.lightweight` for additive; reserve custom for non-CloudKit local-only transforms | Known CloudKit hook limitation (ongoing) | Additive-only is the safe forever-rule for this app |
| MVVM ViewModel per screen | MV (views read `@Query`, write `modelContext`) for data screens | SwiftUI/SwiftData 2024–25 consensus | Less boilerplate; the testable logic lives in `CycleMath`, not a VM |

**Deprecated/outdated:** Nothing in the Phase-1 stack is deprecated for this phase. (Note: `Chart3D`/Swift Charts and HealthKit iOS-26 APIs exist but are out of scope here.)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The existing `extension SchemaV1 { @Model … }` nesting can be extended to a `SchemaV2` that references the *same* additively-grown classes without SwiftData treating them as a different entity (entity name = class name stays stable). | Pattern 1 | If SwiftData ties entity identity to the enum namespace, a naive V2 could be seen as a rename → data loss. **Plan must verify with a round-trip test that V1 data survives the V2 lightweight migration** before relying on it. Mitigated by `SyncMode.localOnly` (no production CloudKit data exists yet), so the cost of getting it wrong now is low — but the pattern must be correct before Phase 4. |
| A2 | A discrete vector `Shape`/`Path` body map is feasible within the phase's effort budget vs. an asset-image overlay. | Stack alternatives, Body Map | If vector art proves too costly, fall back to asset-image + overlaid invisible region shapes; the `BodyRegion` model and accessibility contract are identical, so only the rendering changes. Low risk. |
| A3 | `< 2` recorded cycles is the right "not enough data" threshold and median-of-gaps + IQR spread is an adequate confidence proxy for this minimal indicator. | Pattern 2, Derived Cycle | This is a *minimal honest indicator*, not the insights engine (deferred). If the threshold/heuristic is suboptimal, it only affects when the summary appears/how wide the range is — never correctness or safety. Tunable later. The suppressed + sparse-data fixtures lock the *behavior contract* (emit nothing / "not enough data"), which is what matters. |
| A4 | No `project.yml` target edit is needed because sources are folder-globbed (`Sources/EndoApp`, `Tests/EndoAppTests`, `Tests/EndoAppUITests`). | Runtime State Inventory | If globbing misses a new subfolder, `xcodegen generate` simply won't include it → compile error caught immediately. Zero silent-failure risk. |

## Open Questions

1. **Exact `BodyRegion` set + front/back layout fidelity**
   - What we know: D-04 lists the regions (lower abdomen/pelvis, L/R lower quadrant, lower back, hips, upper thighs, rectal/bowel, bladder, chest/diaphragm, shoulder); discrete, not pixel-precise.
   - What's unclear: precise `Path` geometry and whether front+back are two silhouettes or a segmented control toggling one. (Claude's discretion per CONTEXT.)
   - Recommendation: Start with a single front silhouette + a front/back toggle; ship the region *enum* and accessibility contract first, iterate visuals via simulator screenshots.

2. **Phase representation in `CycleSummary`**
   - What we know: CYCL-04 wants an optional derived phase with confidence, shown only when meaningful.
   - What's unclear: whether Phase 2 surfaces a named phase (e.g. "menstrual/follicular/…") at all, or only cycle-day + next-period range. Naming phases risks over-claiming this early.
   - Recommendation: For this minimal indicator, surface **cycle-day + next-period range + confidence** and HOLD named multi-phase mapping for Phase 6 (INST-02). Keep `phase` optional in the type so it can be populated later without a schema/contract change.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode toolchain (SwiftData/SwiftUI/Swift Testing/XCUITest) | Entire phase | ✓ | Xcode 26.5 (17F42), iOS 26.5 SDK | — |
| XcodeGen | Project regeneration after adding files | ✓ | 2.45.4 (verified Phase 1) | — |
| iPhone 16 Simulator (iOS 26.5 runtime) | Build/test/E2E destination | ✓ | iOS 26.5 | Any booted iOS 18+ iPhone sim |
| Apple Developer account / CloudKit | **NOT required this phase** | n/a | — | `SyncMode.localOnly` — all tests run in-memory, no paid account |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None — the entire phase is provable on the free local-only path (in-memory `ModelContainer` + simulator), exactly as the validation requirement demands.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (unit) + XCTest/XCUITest (E2E), Xcode 26.5 |
| Config file | `project.yml` (XcodeGen) — `EndoAppTests` (bundle.unit-test) + `EndoAppUITests` (bundle.ui-testing), both in the EndoApp scheme |
| Quick run command | `xcodebuild test -scheme EndoApp -only-testing:EndoAppTests -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SYMP-01 | A `SymptomReading` (type + 0–10 severity) round-trips; absence ≠ "0/healthy" | unit | `… -only-testing:EndoAppTests/SymptomLoggingTests` | ❌ Wave 0 |
| SYMP-02 | Body-map binding: tapping `BodyRegion` → `PainPoint(region, severity)` (logic-level, no render) | unit | `… -only-testing:EndoAppTests/BodyMapBindingTests` | ❌ Wave 0 |
| SYMP-03 | Day note persists and is length-bounded | unit | `… -only-testing:EndoAppTests/DayNoteTests` | ❌ Wave 0 |
| SYMP-04 | Edit updates / delete removes a day's records; `@Query` list reflects it; cycle recomputes | unit + E2E | `… -only-testing:EndoAppTests/EditDeleteTests` ; E2E flow | ❌ Wave 0 |
| CYCL-01 | `BleedingEvent` (flow + `clots`) persists as a first-class event, no cycle field touched | unit | `… -only-testing:EndoAppTests/BleedingEventTests` (extend `SchemaRoundTripTests`) | ⚠️ extend existing |
| CYCL-02 | `CycleMath` on a regular fixture → median length + a sensible range; on an **irregular** fixture → wider range / lower confidence | unit | `… -only-testing:EndoAppTests/CycleMathTests` | ❌ Wave 0 |
| CYCL-03 | `CycleMath` with suppressed flag → `isSuppressed`, NO cycleDay/phase/prediction (the no-phantom-cycle proof) | unit | `… -only-testing:EndoAppTests/CycleMathTests` (suppressed fixture) | ❌ Wave 0 |
| CYCL-04 | Sparse fixture (<2 cycles) → `hasEnoughData == false` ("not enough data yet"); enough → optional cycleDay + confidence | unit | `… -only-testing:EndoAppTests/CycleMathTests` | ❌ Wave 0 |
| (cross) | New copy passes `ClaimsPolicy`/`Terminology` audit (no medical verbs, no gendered tokens, no hard date) | unit | `… -only-testing:EndoAppTests/CopyAuditTests` | ❌ Wave 0 (mirror `TerminologyTests`) |
| (E2E) | Consent → Log today → symptom+severity → body region+severity → bleeding → save → appears in history → edit → delete → gone; suppressed mode shows no cycle summary | E2E | `… -only-testing:EndoAppUITests` (extend `ConsentFlowUITests` or add `LoggingFlowUITests`) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `xcodebuild test -only-testing:EndoAppTests/<suite> …` (the suite touched).
- **Per wave merge:** `xcodebuild test -only-testing:EndoAppTests …` (all unit suites; fast, in-memory).
- **Phase gate:** Full suite (unit + XCUITest E2E) green on the iPhone 16 sim before `/gsd-verify-work`; E2E screenshots written to `/tmp/e2e/*.png` per the existing helper.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/CycleMathTests.swift` — CYCL-02/03/04: regular, **irregular**, **suppressed**, and **sparse (<2 cycles)** fixtures (the suppressed fixture is the no-phantom-cycle proof).
- [ ] `Tests/EndoAppTests/SymptomLoggingTests.swift` — SYMP-01 round-trip + "absence ≠ 0/healthy".
- [ ] `Tests/EndoAppTests/BodyMapBindingTests.swift` — SYMP-02 region→PainPoint binding at logic level.
- [ ] `Tests/EndoAppTests/DayNoteTests.swift` — SYMP-03 note persistence + length bound.
- [ ] `Tests/EndoAppTests/EditDeleteTests.swift` — SYMP-04 edit/delete + cascade of child records.
- [ ] `Tests/EndoAppTests/CopyAuditTests.swift` — cross-cutting `ClaimsPolicy`/`Terminology` audit of new copy.
- [ ] Extend `Tests/EndoAppTests/SchemaRoundTripTests.swift` — CYCL-01 `BleedingEvent.clots` + the new models round-trip + **V1→V2 lightweight migration preserves V1 data** (A1 guard).
- [ ] `Tests/EndoAppUITests/LoggingFlowUITests.swift` (or extend `ConsentFlowUITests`) — the full E2E logging flow + a suppressed-mode "no cycle summary" assertion.
- [ ] No framework install needed — Swift Testing + XCUITest already wired.

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1`. This phase remains single-user, on-device, no network, no CloudKit active, no HealthKit. Surface area is minimal.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login (by design); consent gate is the only access control and is unchanged from Phase 1 |
| V3 Session Management | no | No sessions |
| V4 Access Control | yes (carried) | Health-data UI reachable only from `AppGate.consented`; re-point `WalkingSkeletonView` → `HomeView` inside the SAME `.consented` branch (do not weaken T-01-03). Repository keeps its debug `assertConsented()` defence-in-depth. |
| V5 Input Validation | yes | Bound the day-note length at the write seam (existing `commitFreeText` pattern, T-01-05); clamp severity to `0...10`; treat all free-text as plain local strings (no injection surface — no web view, no SQL string interpolation; SwiftData parameterizes). |
| V6 Cryptography | no | No crypto introduced; local SQLite at rest under iOS file protection (unchanged) |
| V7 Error/Logging | yes | **Never log health values** (symptom types, severities, pain regions, flow, notes) to console/crash output (PITFALLS Security row). Audit any `print`/log added in this phase. |

### Known Threat Patterns for SwiftUI + SwiftData on-device
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health UI reachable before consent | Elevation / Info disclosure | Construct logging UI only in `AppGate.consented`; repository `assertConsented()` |
| Unbounded free-text (note) | DoS / resource | Length-bound at write seam (T-01-05), as `SettingsView` already does |
| Health values leaked via logs/crash output | Information disclosure | No health values in `print`/logging; redact; no crash SDKs (none present) |
| Premature CloudKit/HealthKit activation | Info disclosure / policy (5.1.3(ii)) | Do NOT add iCloud/CloudKit/Background-Modes/HealthKit capability this phase; `SyncMode.localOnly` stays |
| Severity out of range / enum-decode of unknown raw | Tampering / robustness | Clamp severity `0...10`; decode `…Raw` enums defensively (unknown → "other"/nil, never crash) |

## Sources

### Primary (HIGH confidence)
- Live Phase-1 source: `Sources/EndoApp/Persistence/{SchemaV1,Repository,ModelContainer+Config}.swift`, `Models/{DayEntry,BleedingEvent,TreatmentState}.swift`, `App/{AppGate,AppDependencies}.swift`, `Content/ClaimsPolicy.swift`, `Terminology/{Terminology,TerminologyPreference}.swift`, `Features/{Skeleton/WalkingSkeletonView,Settings/SettingsView}.swift` — the exact seams Phase 2 extends.
- Live tests: `Tests/EndoAppTests/SchemaRoundTripTests.swift`, `Tests/EndoAppUITests/ConsentFlowUITests.swift` — the test + E2E patterns to extend.
- `project.yml`, `.planning/config.json` — build config, nyquist + security flags.
- developer.apple.com/documentation/swiftdata — VersionedSchema, SchemaMigrationPlan, MigrationStage (lightweight/custom).
- `.planning/research/{ARCHITECTURE,PITFALLS,FEATURES}.md` + Phase-1 SUMMARYs — corroborate every constraint.

### Secondary (MEDIUM confidence)
- Apple Developer Forums threads 742899 / 744491 / 748049 — custom `willMigrate`/`didMigrate` **not called when CloudKit enabled** (corroborated across multiple threads + independent authors). Drives the "lightweight-only" rule.
- hackingwithswift.com — `contentShape()` hit-testing for tappable regions (CITED for the body-map pattern).
- tanaschita.com / azamsharp.com — SwiftData VersionedSchema/migration walkthroughs (corroborating syntax).

### Tertiary (LOW confidence)
- github.com/melihcolpan/MuscleMap — exists as a SwiftUI interactive-body-map reference (NOT adopted; informs the in-house approach only). `[ASSUMED]`

## Metadata

**Confidence breakdown:**
- Standard stack / migration mechanics: HIGH — first-party, additive, corroborated against live source + Apple docs + multiple SwiftData authors.
- Architecture / seams: HIGH — read directly from the live Phase-1 code; Phase 2 is a clean additive extension.
- Derived-cycle design: HIGH for the contract (pure, optional, range+confidence, suppressed short-circuit); MEDIUM for the specific confidence heuristic (tunable, behavior-contract is what's locked).
- Body map: MEDIUM-HIGH — multiple working SwiftUI patterns; recommendation is the simplest accessible one; exact geometry is design discretion.
- Pitfalls: HIGH — directly traceable to `.planning/research/PITFALLS.md` + the Phase-1 guards already in place.

**Research date:** 2026-06-08
**Valid until:** ~2026-07-08 (30 days — stable first-party stack; re-verify SwiftData migration behavior if the Xcode toolchain bumps).
