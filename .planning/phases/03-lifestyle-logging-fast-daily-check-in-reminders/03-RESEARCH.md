# Phase 3: Lifestyle Logging + Fast Daily Check-in & Reminders - Research

**Researched:** 2026-06-08
**Domain:** SwiftUI / SwiftData (CloudKit-safe schema) factor logging + local notifications (UNUserNotificationCenter), Swift 6 strict concurrency, MV + Repository
**Confidence:** HIGH — the dominant deliverables (FactorReading model, additive V2→V3 migration, Sendable snapshots, Repository extension, check-in sheet) are direct, well-proven extensions of shipped Phase 1–2 patterns I read in source. Notification scheduling is HIGH on the API and MEDIUM on the testable-seam design (a design recommendation, not a verified fact).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

> These are LOCKED. Research answers HOW to implement them, never WHETHER. D-01..D-21 are the spec.

### Locked Decisions

**Area 1 — Factor data model & granularity (LIFE-01/02/03)**
- **D-01:** ONE unified, append-only `FactorReading` `@Model`, mirroring `SymptomReading` exactly (nested under `extension SchemaV1`, app-generated `id: UUID` NOT `.unique`, `occurredOn: Date` day-anchor, no relationships, all stored properties optional/defaulted, no cycle fields). Discriminated by `kindRaw: String?` (`FactorKind`: food, activity, sleep, medication, stress, mood) with a few optional CloudKit-safe payload columns (`valueRaw: String?`, `amount: Double?`, `level: Int?`, `note: String?`). Exact column shape is Claude's discretion within this decision.
- **D-02:** Factor enums live in `Domain/`: `FactorKind` + value enums (`FoodTag`, `ActivityType`, `SleepQuality`), each with plain-language, non-diagnostic labels and an extensible "other"/free-text escape. Mood/stress are integer scales, not enums.
- **D-03:** Food (LIFE-01) = multi-select trigger-category tags + optional free-text — NOT a macro/calorie diary. Final list at planning, grounded in `research/FEATURES.md`.
- **D-04:** Activity (LIFE-02) = type + duration + perceived intensity (walking, cardio, strength, yoga/stretching, other) + minutes + low/med/high.
- **D-05:** Sleep (LIFE-03) = duration (hours) + quality (poor/ok/good).

**Area 2 — Context factors: medication, stress, mood (LIFE-03)**
- **D-06:** Medication = a small user-maintained saved med list (name [+ optional note/type], bounded strings, reuse `maxNoteLength`-style guard); per day the user logs which meds were taken (taken toggle) + optional PRN pain-relief marker. No scheduled-regimen engine in v1.
- **D-07:** Stress = the 0–10 severity scale (reuse Phase 2's `SeverityControl`). `nil` = unlogged, NEVER "0 = no stress".
- **D-08:** Mood = a day-level overall mood factor on a 1–5 valence scale, kept DISTINCT from Phase 2's acute "low mood" `SymptomType`. Most-likely-to-need-a-second-look decision (revisit in UAT).
- **D-09:** Factors extend the existing `DayLog`/`DayEntry` day aggregation. One calendar day holds symptoms + pain + bleeding + factors + note. No separate decoupled factor store.

**Architecture & data model (carried)**
- **D-10:** New `SchemaV3: VersionedSchema`, purely ADDITIVE over SchemaV2 (adds `FactorReading` + any saved-`Medication` model). Same CloudKit-safe rules. Extend `MigrationPlan` with a lightweight V2→V3 stage + a `SchemaMigrationTests` case proving V2 data survives reopen (the "A1" discipline) — closes before Phase 4 flips CloudKit on.
- **D-11:** Extend `Repository` additively with factor + medication CRUD (upsert/remove by (day, kind), `Calendar`-bucketed, consent-gated, `save()`-after-write). Add `Sendable`, SwiftData-free value-snapshot reads (a `FactorDay`/per-kind snapshot mirroring `BleedingDay`) so Phases 5–8 consume factors WITHOUT touching the store. **The most important forward-looking deliverable.**
- **D-12:** MV + Repository (no per-screen ViewModels; `@Query`/`@Observable`; logic behind protocols). Swift 6 strict concurrency; notification-scheduling / UI types `@MainActor` as needed.

**Area 3 — Fast daily check-in (CHCK-01)**
- **D-13:** A single fast, sectioned "Daily check-in" sheet (one scrollable screen, smart defaults), writing through the same `Repository`. Full `DayLogEditorView` remains for detailed editing.
- **D-14:** "Same as yesterday" prefills STICKY CONTEXT ONLY (meds taken, typical sleep, activity type). NEVER auto-carries symptoms or bleeding (events; carrying them forward fabricates data).
- **D-15:** Nothing is mandatory. An empty confirm still records a lightweight "checked in today" adherence marker without inventing symptom data.
- **D-16:** Two entry points: a prominent Home "Daily check-in" CTA AND the reminder notification deep-links into the check-in sheet.

**Area 4 — Reminders & notification fatigue (CHCK-02)**
- **D-17:** Local notifications via `UNUserNotificationCenter` only (no server/push). A small `NotificationScheduler` behind a protocol (testable/mockable seam like `Repository`).
- **D-18:** Opt-in, DEFAULT OFF. User configures one or more daily reminder times in Settings (suggest a single sensible evening default when first enabling).
- **D-19:** Fatigue controls: (a) respect user-set quiet hours; (b) auto-suppress the day's reminder if already checked in/logged that day (cancel/reschedule the pending request once the day is logged); (c) no repeat-nagging within a day.
- **D-20:** Just-in-time permission — request authorization only when the user first enables a reminder (not at launch/onboarding). If denied, degrade gracefully (in-app only); reminders settings reflect the denied state with a path to Settings.app.

**Inclusive language & claims (carried)**
- **D-21:** Reuse the `Terminology` + `ClaimsPolicy` choke-points for all Phase 3 copy. Reminder copy is gentle and non-coercive — no guilt/streak-pressure framing.

### Claude's Discretion

Exact check-in sheet & factor-control layout, the precise food-tag / activity-type lists (ground in `research/FEATURES.md`), the `FactorReading` column design, the saved-medication model shape, the reminder-settings UI, and all copy — gray WITHIN the decisions above. Review via simulator screenshots + iteration.

### Deferred Ideas (OUT OF SCOPE)

- Full food diary / macros / calorie tracking → HealthKit nutrition import (Phase 9) + possible v2.
- HealthKit import of sleep / workouts / nutrition → Phase 9.
- Charts / trends / correlations / cycle-phase mapping ON these factors → Phases 5–8 (this phase only CAPTURES the data).
- Scheduled-medication regimen engine / "time to take your meds" reminders → out of v1 (this phase reminds to LOG, not to take meds).
- CloudKit sync of the new factor records → Phase 4 (schema built CloudKit-safe so it rides along).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LIFE-01 | User can log food/nutrition for a day | `FactorKind.food` rows: multi-select `FoodTag` (one `FactorReading` per selected tag) + optional free-text via a `.food`-kind note. Taxonomy in §Food-Trigger Taxonomy. Repository `setFood(tags:freeText:on:)`. |
| LIFE-02 | User can log physical activity / sport | `FactorKind.activity`: `ActivityType` in `valueRaw`, minutes in `amount`, intensity in `level` (0/1/2). One reading per (day, activity). Repository `setActivity(...)`. |
| LIFE-03 | Sleep, medication, stress, mood | Sleep (`.sleep`: hours in `amount`, `SleepQuality` in `valueRaw`); medication (saved `Medication` `@Model` + per-day `.medication` "taken" readings keyed by med id in `valueRaw`); stress (`.stress`: 0–10 in `level`, reuse `SeverityControl`); mood (`.mood`: 1–5 in `level`). All honesty-gated: `nil`/absence ≠ 0. |
| CHCK-01 | Fast daily check-in | New `DailyCheckInView` — single sectioned `Form` in a `.sheet`; "Same as yesterday" sticky-context prefill; empty-confirm writes a "checked in" adherence marker (a `DayEntry` for the day, no invented data). Writes through the same `Repository`. |
| CHCK-02 | Configurable fatigue-aware reminders | `NotificationScheduler` protocol + `UNUserNotificationCenter` impl; `UNCalendarNotificationTrigger(repeats: true)`; quiet-hours filtering; auto-suppress on log; JIT auth; deep-link via `UNUserNotificationCenterDelegate`. Settings UI per UI-SPEC §C. |
</phase_requirements>

## Summary

Phase 3 is overwhelmingly an **additive extension of patterns that are already shipped and proven in this codebase**, plus exactly one genuinely new subsystem (local notifications). The factor model, schema migration, Sendable snapshots, Repository CRUD, and the check-in sheet are all direct mirror-images of `SymptomReading` / `SchemaV2` / `EndoMigrationPlan` / `BleedingDay` / `SwiftDataRepository` / `DayLogEditorView` — I read each one and the new work is "do the same thing again, additively, for factors." The single biggest risk in the data-model work is the same A1 entity-identity discipline Phase 2 already closed: keep the new `@Model` classes nested under `extension SchemaV1 { … }`, list them additively in a new `SchemaV3`, use `MigrationStage.lightweight` ONLY (never `.custom` — its hooks don't fire under CloudKit in Phase 4), and add a `SchemaMigrationTests` case proving V2 data survives the V2→V3 reopen.

The **insights-ready Sendable snapshot seam (D-11) is the phase's most important forward-looking deliverable.** Phases 5–8 must consume factors as plain `Sendable` value types without ever touching `ModelContext`, exactly as `CycleMath` consumes `BleedingDay`. Get the snapshot shape (`FactorDay` per calendar day, carrying typed per-kind values) right now, because changing it later means re-touching every downstream insights phase.

The **only new external API surface is `UNUserNotificationCenter`** (first-party, no dependency, no server — matches the privacy-first, no-backend mandate). The verified pattern is `UNCalendarNotificationTrigger(dateMatching: DateComponents(hour:minute:), repeats: true)` per reminder time, requests keyed by stable identifiers so they can be cancelled/rescheduled (the D-19 auto-suppress lever), just-in-time authorization only on first enable, and a `UNUserNotificationCenterDelegate` that routes a notification tap into the check-in sheet. Because XCUITest cannot assert system notifications, the testable design is a `NotificationScheduler` **protocol** with a real `UNUserNotificationCenter`-backed impl and a mock — unit-test the scheduling/quiet-hours/suppress LOGIC against the mock, exactly as `Repository` is mocked.

**Primary recommendation:** Mirror `SymptomReading`→`FactorReading`, `SchemaV2`→`SchemaV3` (lightweight, additive, with a migration test), `BleedingDay`→`FactorDay`, and the `Repository`/`DayLogEditorView` seams; build the `NotificationScheduler` behind a protocol and unit-test the fatigue logic against a mock; route all copy through `Terminology`/`ClaimsPolicy`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Factor persistence (`FactorReading`, `Medication`) | Database / SwiftData store | — | Same tier `SymptomReading`/`DayEntry` live in; one additive schema. |
| Factor CRUD + day-bucketing + clamps | Repository (persistence seam) | — | `Calendar.isDate(_:inSameDayAs:)` bucketing, severity clamp, string bounds all already live here. Extend additively. |
| Insights-ready value snapshots (`FactorDay`) | Repository (Sendable read seam) | Domain (pure value types) | `Sendable`, SwiftData-free; consumed by Phases 5–8 off the store. Mirrors `BleedingDay`. |
| Factor enums + taxonomy (`FactorKind`, `FoodTag`, …) | Domain (pure Foundation-only value types) | — | Foundation-only, no SwiftData/SwiftUI import — portable + unit-testable. Mirrors `SymptomType`. |
| Factor controls + check-in sheet | View (SwiftUI, MV no ViewModel) | Repository | `@Environment(\.modelContext)` → `SwiftDataRepository`, draft `@State`, commit on save. Mirrors `DayLogEditorView`. |
| Local notification scheduling | Service (`NotificationScheduler`, `@MainActor`) | App composition root | Behind a protocol (injected via Environment like `consentStore`); `UNUserNotificationCenter` is main-actor-affine. |
| Reminder settings + meds management | View (SwiftUI) | Service + Repository | New `Section`s in `SettingsView`; writes reminder config (UserDefaults) + saved meds (store). |
| Notification → check-in deep-link | App composition root (`UNUserNotificationCenterDelegate`) | View | Delegate sets app routing state that presents `DailyCheckInView`. |
| Reminder config persistence | UserDefaults (NOT the synced store) | — | Reminder times/quiet-hours are device prefs, not health data — keep out of the CloudKit-bound store (mirrors consent flag + terminology prefs). |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **SwiftData** | iOS 18 SDK (deployment 18.0) | `FactorReading` + `Medication` `@Model`s, additive `SchemaV3`, lightweight V2→V3 migration | Already the app's persistence layer; CloudKit-safe rules established. `[VERIFIED: codebase — SchemaV2.swift, MigrationPlan.swift]` |
| **UserNotifications** (`UNUserNotificationCenter`) | iOS 18 SDK | Local repeating daily reminders, JIT auth, deep-link delegate | First-party, no server/push, matches no-backend privacy mandate (D-17). `[VERIFIED: Apple docs]` |
| **SwiftUI** | iOS 18 SDK | Check-in `.sheet`, factor `Form` sections, reminder settings | App UI layer; MV pattern, system controls only (per UI-SPEC). `[VERIFIED: codebase]` |
| **Swift Testing** (`@Test`/`#expect`) | Xcode 26 | Model round-trip, migration, Repository, factor-snapshot, notification-logic unit tests | The project's unit-test framework (`Tests/EndoAppTests/*`). `[VERIFIED: codebase]` |
| **XCTest / XCUITest** | Xcode 26 | E2E check-in + factor-logging flow (`-uitest-reset` + a11y-ids) | The project's E2E harness (`Tests/EndoAppUITests/*`). `[VERIFIED: codebase]` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Foundation** (`Calendar`, `DateComponents`, `UUID`) | system | Day bucketing, `UNCalendarNotificationTrigger` time components, ids | Reuse the existing `Calendar.current.startOfDay(for:)` / `isDate(_:inSameDayAs:)` discipline — never epoch math. `[VERIFIED: codebase — Repository.swift]` |
| **Terminology / ClaimsPolicy** (project) | n/a | Copy choke-point for factor labels, check-in, reminder text (D-21) | All user-facing strings; `CopyAuditTests` audits `plainLabel`s against `forbiddenVerbs`/`forbiddenGenderedTokens`. `[VERIFIED: codebase — ClaimsPolicy.swift]` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One unified `FactorReading` | Six per-kind `@Model`s | D-01 LOCKS one unified model. Six models = six migration-stage entities, six snapshot mappers — more CloudKit surface for no gain. Don't. |
| `UNCalendarNotificationTrigger(repeats:true)` | `UNTimeIntervalNotificationTrigger` + manual reschedule | Calendar trigger handles DST/wall-clock correctly and repeats natively; time-interval needs manual daily re-arming. Use calendar. `[CITED: developer.apple.com/documentation/usernotifications]` |
| `NotificationScheduler` protocol + mock | Direct `UNUserNotificationCenter` calls in views | Direct calls are untestable (XCUITest can't assert system notifications) and violate the established protocol-seam pattern. Use the protocol. `[VERIFIED: codebase — Repository protocol seam]` |
| Reminder config in UserDefaults | Reminder config in the SwiftData store | Reminder times are device prefs, not health data — keep them out of the CloudKit-bound store (mirrors consent flag + terminology). Don't sync them. |

**Installation:** No new packages. All first-party Apple frameworks already available. `UserNotifications` is imported per-file (`import UserNotifications`); no entitlement is required for *local* notifications (unlike Phase 4's CloudKit/Background-Modes). Do NOT add capabilities to `project.yml` for this phase.

**Version verification:** No third-party packages to verify. SwiftData / SwiftUI / UserNotifications / XCTest / Swift Testing all ship with the Xcode 26 / iOS 18 SDK the project already builds against `[VERIFIED: codebase — project.yml deploymentTarget iOS 18.0, SWIFT_VERSION 6.0]`.

## Package Legitimacy Audit

> Not applicable — this phase installs **zero** external packages. All capabilities use first-party Apple frameworks (SwiftData, SwiftUI, UserNotifications, XCTest, Swift Testing) that ship with the Xcode 26 / iOS 18 SDK already in use. No npm/PyPI/crates/SPM dependency is added. slopcheck / registry verification is inapplicable.

## Architecture Patterns

### System Architecture Diagram

```
                          ┌─────────────────────────────────────────────┐
   Notification tap ─────►│  UNUserNotificationCenterDelegate            │
   (deep link, D-16)      │  (App composition root, @MainActor)          │
                          └───────────────┬─────────────────────────────┘
                                          │ sets routing state
                                          ▼
   Home "Daily check-in" ──────►  DailyCheckInView (.sheet, MV)  ◄──── DayLogEditorView
   CTA (D-16)                            │  draft @State                  (full editor, factor
                                          │  "Same as yesterday" prefill    Sections added, D-09)
                                          │  (sticky context only, D-14)
                                          ▼ commit on "Done for today"
                          ┌─────────────────────────────────────────────┐
                          │  Repository (protocol seam, consent-gated)   │
                          │   setFood / setActivity / setSleep /         │
                          │   setMedicationTaken / setStress / setMood / │
                          │   checkInMarker(on:)  +  saved-med CRUD       │
                          │   ── on write: cancel today's pending ───────┼──► NotificationScheduler
                          │      reminder if logged (D-19b) ─────────────┘    (protocol)
                          └───────────────┬───────────────┬─────────────┘         │
                  write @Model            │               │ Sendable read         │ UNCalendar
                                          ▼               ▼ (FactorDay)            ▼ Trigger(repeats)
                          ┌──────────────────────────┐  ┌──────────────┐  ┌──────────────────────┐
                          │ SwiftData store (V3)     │  │ FactorDay[]  │  │ UNUserNotification-   │
                          │  FactorReading           │  │ (Sendable,   │  │ Center (system)       │
                          │  Medication              │  │ store-free)  │  │  pending requests by  │
                          │  + V2 models (additive)  │  │  ──► Ph 5–8  │  │  stable identifier    │
                          └──────────────────────────┘  └──────────────┘  └──────────────────────┘

   Settings ──► Reminders Section (master toggle OFF default, JIT auth, times, quiet hours, D-18/19/20)
            ──► Manage Meds Section (saved-med CRUD, D-06)
   Reminder config (times, quiet hours, enabled) ──► UserDefaults (device pref, NOT synced store)
```

### Recommended Project Structure

```
Sources/EndoApp/
├── Domain/
│   ├── FactorKind.swift          # NEW: food|activity|sleep|medication|stress|mood (mirror SymptomType)
│   ├── FoodTag.swift             # NEW: curated endo trigger tags + .other
│   ├── ActivityType.swift        # NEW: walking|cardio|strength|yogaStretching|other
│   ├── ActivityIntensity.swift   # NEW: low|medium|high (text-labelled, never color-only)
│   ├── SleepQuality.swift        # NEW: poor|okay|good
│   └── FactorDay.swift           # NEW: Sendable, SwiftData-free per-day snapshot (mirror BleedingDay)
├── Persistence/
│   ├── Models/
│   │   ├── FactorReading.swift   # NEW @Model under extension SchemaV1 (mirror SymptomReading)
│   │   └── Medication.swift      # NEW @Model saved-med list (CloudKit-safe, bounded strings)
│   ├── SchemaV3.swift            # NEW VersionedSchema, additive over V2
│   ├── MigrationPlan.swift       # EXTEND: add v2toV3 lightweight stage
│   ├── ModelContainer+Config.swift # EXTEND: Schema(versionedSchema: SchemaV3.self)
│   └── Repository.swift          # EXTEND: factor + med CRUD + FactorDay reads (additive)
├── Reminders/                    # NEW folder
│   ├── NotificationScheduler.swift   # protocol + UNUserNotificationCenter impl
│   └── ReminderSettings.swift        # UserDefaults-backed times/quiet-hours/enabled config
├── Features/
│   ├── CheckIn/
│   │   └── DailyCheckInView.swift # NEW: fast sectioned sheet (CHCK-01)
│   ├── DayLog/DayLogEditorView.swift # EXTEND: add factor Sections (D-09)
│   ├── Home/HomeView.swift        # EXTEND: add "Daily check-in" CTA (D-16)
│   └── Settings/SettingsView.swift # EXTEND: reminders Section + manage-meds Section
└── App/
    ├── AppDependencies.swift      # EXTEND: bump to SchemaV3, inject NotificationScheduler
    └── EndoAppApp.swift / AppGate.swift # EXTEND: notification delegate + check-in routing
```

### Pattern 1: Unified `FactorReading` @Model (D-01) — mirror `SymptomReading`

**What:** One append-only `@Model` discriminated by `kindRaw`, with generic optional payload columns.
**When to use:** Every factor write except saved-med definitions.

```swift
// Source: mirror of Sources/EndoApp/Persistence/Models/SymptomReading.swift (codebase)
// CloudKit-safe (D-01/D-10): app-gen UUID NOT .unique, day-anchored occurredOn, ALL optional/defaulted,
// no relationships, no cycle fields. Nested under extension SchemaV1 (A1 entity-identity discipline).
import Foundation
import SwiftData

extension SchemaV1 {
    /// One typed contextual-factor reading (append-only event, D-01). Discriminated by `kindRaw`.
    @Model
    final class FactorReading {
        var id: UUID = UUID()                 // app-generated, NOT .unique (CloudKit-safe)
        var occurredOn: Date = Date()         // calendar-day anchor, defaulted
        var kindRaw: String?                  // FactorKind: food|activity|sleep|medication|stress|mood
        var valueRaw: String?                 // FoodTag / ActivityType / SleepQuality / med-id token
        var amount: Double?                   // duration (minutes for activity, hours for sleep)
        var level: Int?                       // stress 0...10 | mood 1...5 | activity intensity 0/1/2
        var note: String?                     // free-text escape (bounded at the seam)

        init(id: UUID = UUID(), occurredOn: Date = .now, kindRaw: String? = nil,
             valueRaw: String? = nil, amount: Double? = nil, level: Int? = nil, note: String? = nil) {
            self.id = id; self.occurredOn = occurredOn; self.kindRaw = kindRaw
            self.valueRaw = valueRaw; self.amount = amount; self.level = level; self.note = note
        }
    }
}
```

Column-mapping convention (recommend the planner lock this in the model doc-comment so it is unambiguous downstream):

| FactorKind | `valueRaw` | `amount` | `level` | `note` | Cardinality per day |
|------------|-----------|----------|---------|--------|---------------------|
| `.food` | `FoodTag.rawValue` | — | — | (one extra `.food` reading holds free-text) | many (one per selected tag) |
| `.activity` | `ActivityType.rawValue` | minutes | intensity 0/1/2 | — | one |
| `.sleep` | `SleepQuality.rawValue` (optional) | hours | — | — | one |
| `.medication` | saved-med id token (or "prn") | — | — | — | many (one per taken med + PRN marker) |
| `.stress` | — | — | 0...10 (clamped) | — | one |
| `.mood` | — | — | 1...5 (clamped) | — | one |

> **Honesty rule (D-07/D-02/UI-SPEC):** absence of a reading = "not logged". A `.stress` reading with `level == 0` is a *logged* zero; NO `.stress` reading is *unlogged*. The check-in/editor must never write a zero-valued reading to mean "skipped".

### Pattern 2: Saved `Medication` @Model + per-day "taken" reconciliation (D-06)

**What:** A small user-maintained list of meds (definitions), separate from append-only "taken today" events.
**When to use:** The saved list powers the check-in's med toggles; "taken" is a `.medication` `FactorReading`.

```swift
// Source: mirror of SymptomReading CloudKit-safe conventions (codebase)
extension SchemaV1 {
    /// A user-saved medication definition (NOT an event). Powers the check-in's per-day "taken" toggles.
    @Model
    final class Medication {
        var id: UUID = UUID()        // stable token written into FactorReading.valueRaw for "taken" events
        var name: String?            // bounded at the seam (maxNoteLength-style guard, D-06)
        var note: String?            // optional type/note, bounded
        var createdAt: Date = Date()
        var isArchived: Bool? = nil  // soft-remove (append-only friendly; history stays intact, UI-SPEC copy)

        init(id: UUID = UUID(), name: String? = nil, note: String? = nil,
             createdAt: Date = .now, isArchived: Bool? = nil) {
            self.id = id; self.name = name; self.note = note
            self.createdAt = createdAt; self.isArchived = isArchived
        }
    }
}
```

**Reconciliation:** the saved-list entity (`Medication`) is the *definition*; the per-day "taken" log is a `.medication` `FactorReading` whose `valueRaw` is the med's `id.uuidString`. Removing a med = set `isArchived` (NOT delete) so the UI-SPEC promise "Days you already logged it as taken are not changed" holds and append-only history is preserved. The check-in shows non-archived meds. PRN pain-relief is a `.medication` reading with `valueRaw == "prn"`.

### Pattern 3: Additive `SchemaV3` + lightweight V2→V3 migration (D-10) — mirror `SchemaV2`

```swift
// Source: mirror of Sources/EndoApp/Persistence/{SchemaV2.swift, MigrationPlan.swift} (codebase)
enum SchemaV3: VersionedSchema {
    static let versionIdentifier = Schema.Version(3, 0, 0)
    static var models: [any PersistentModel.Type] {
        // ALL V3 models = unchanged V2 set + the two NEW additive models. Same SchemaV1.<Name> classes.
        [SchemaV1.DayEntry.self, SchemaV1.BleedingEvent.self, SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self, SchemaV1.PainPoint.self,
         SchemaV1.FactorReading.self, SchemaV1.Medication.self]   // NEW
    }
}

enum EndoMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self, SchemaV2.self, SchemaV3.self] }
    static var stages: [MigrationStage] { [v1toV2, v2toV3] }
    static let v1toV2 = MigrationStage.lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)
    // Purely additive (two new models) → lightweight. NEVER .custom (hooks don't fire under CloudKit, Phase 4).
    static let v2toV3 = MigrationStage.lightweight(fromVersion: SchemaV2.self, toVersion: SchemaV3.self)
}
```

And bump the container: `let schema = Schema(versionedSchema: SchemaV3.self)` in `makeModelContainer` (`ModelContainer+Config.swift`).

### Pattern 4: Sendable, store-free `FactorDay` snapshot (D-11) — mirror `BleedingDay`

**What:** The most important forward-looking seam. A `Sendable` per-day value type Phases 5–8 consume without a `ModelContext`.
**When to use:** Every insights read of factors.

```swift
// Source: mirror of BleedingDay in Sources/EndoApp/Persistence/Repository.swift (codebase)
// Carries NO @Model reference so it crosses actor/concurrency boundaries and is reasoned about store-free.
struct FactorDay: Sendable, Equatable {
    var day: Date                       // start-of-day
    var foodTags: [FoodTag]             // tags logged that day (typed, decoded defensively)
    var foodNote: String?
    var activityType: ActivityType?
    var activityMinutes: Double?
    var activityIntensity: ActivityIntensity?
    var sleepHours: Double?
    var sleepQuality: SleepQuality?
    var medicationsTaken: [UUID]        // saved-med ids marked taken
    var tookPRN: Bool
    var stress: Int?                    // 0...10, nil = NOT logged (never 0-as-absence)
    var mood: Int?                      // 1...5, nil = NOT logged
}
// Repository: func factorDays() throws -> [FactorDay]  — bucket FactorReading rows by start-of-day,
// decode raws defensively, return ascending. Pure value output (keep SwiftData-free), like bleedingDays().
```

> Recommend the planner add a unit test that round-trips a written factor through `factorDays()` and asserts `nil` distinctness (unlogged stress ≠ stress 0) — this is the honesty contract Phases 5–8 depend on.

### Pattern 5: `NotificationScheduler` protocol + `UNUserNotificationCenter` impl (D-17) — mirror the `Repository` seam

**What:** A testable/mockable notification seam; the only new external API.
**When to use:** All reminder scheduling, cancelling, and authorization.

```swift
// Source: protocol-seam pattern mirrors Repository (codebase); UN API per Apple docs.
import UserNotifications

@MainActor
protocol NotificationScheduler {
    func requestAuthorization() async -> Bool                  // JIT, only on first enable (D-20)
    func authorizationStatus() async -> UNAuthorizationStatus  // drives denied-state UI (D-20)
    func reschedule(times: [DateComponents], quietHours: ClosedRange<DateComponents>?) async
    func cancelToday() async                                   // auto-suppress once logged (D-19b)
    func cancelAll() async
}

// Concrete impl keys each daily reminder by a STABLE identifier so it can be cancelled/rescheduled.
struct SystemNotificationScheduler: NotificationScheduler {
    private let center = UNUserNotificationCenter.current()

    func reschedule(times: [DateComponents], quietHours: ClosedRange<DateComponents>?) async {
        center.removeAllPendingNotificationRequests()          // idempotent re-arm
        for (i, t) in times.enumerated() where !isInQuietHours(t, quietHours) {  // D-19a
            var comps = DateComponents(); comps.hour = t.hour; comps.minute = t.minute
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)  // daily
            let content = UNMutableNotificationContent()
            content.title = "Endo-App"                         // copy via ClaimsPolicy/Terminology (D-21)
            content.body  = "Whenever you have a moment, here's your daily check-in."  // gentle, D-21
            content.userInfo = ["deepLink": "checkin"]          // routes the tap into the check-in sheet
            let req = UNNotificationRequest(identifier: "endo.reminder.\(i)", content: content, trigger: trigger)
            try? await center.add(req)
        }
    }
    func cancelToday() async { center.removeAllPendingNotificationRequests() /* re-armed next launch/log */ }
    // requestAuthorization / authorizationStatus wrap center.requestAuthorization(options:) etc.
    func isInQuietHours(_ t: DateComponents, _ q: ClosedRange<DateComponents>?) -> Bool { /* pure, unit-tested */ }
}
```

> **Auto-suppress (D-19b):** because a `UNCalendarNotificationTrigger(repeats:true)` fires daily, "suppress *today only*" means: on a log/check-in for today, cancel pending requests, then re-arm them (so tomorrow still fires). A clean way is to recompute pending requests against "is today already logged?" — keep this LOGIC in a pure function (`shouldFireToday(lastLoggedDay:now:quietHours:)`) and unit-test it; the `UNUserNotificationCenter` calls are the thin untested shell.

### Pattern 6: Notification deep-link into the check-in sheet (D-16)

`UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:withCompletionHandler:)` reads `response.notification.request.content.userInfo["deepLink"]` and sets an `@Observable`/`@State` routing flag (e.g. on `AppGate` or a small router injected via Environment) that presents `DailyCheckInView`. Set the delegate early (in the composition root). `[CITED: developer.apple.com/documentation/usernotifications]`

> **Swift 6 concurrency note:** `UNUserNotificationCenterDelegate` methods are nonisolated; hop to the main actor (`Task { @MainActor in router.presentCheckIn() }` or `MainActor.assumeIsolated` where SwiftUI guarantees main-thread) before touching UI state — mirrors the `MainActor.assumeIsolated` discipline already used in `AppDependencies` EnvironmentKey defaults.

### Anti-Patterns to Avoid

- **`MigrationStage.custom` for V2→V3.** Its `willMigrate`/`didMigrate` hooks DON'T fire once CloudKit is enabled (Phase 4) — any logic there silently never runs on synced users. The codebase already documents this forever-rule. Use `.lightweight`. `[VERIFIED: codebase — MigrationPlan.swift; Apple forums 742899/744491/748049]`
- **Defining `FactorReading` OUTSIDE `extension SchemaV1`.** SwiftData keys entity identity on the class/entity NAME, not the enum namespace. A new top-level class name would be a non-additive change. Keep it nested (A1 discipline). `[VERIFIED: codebase — SchemaV2 A1 note]`
- **Writing a zero-valued reading to mean "skipped/absent".** Violates the honesty principle (absence ≠ 0). `nil`/no-reading = unlogged; a logged 0 is a deliberate value. `[VERIFIED: codebase — SymptomReading honesty doc, PITFALLS.md]`
- **Putting reminder times / quiet-hours in the synced SwiftData store.** They are device prefs, not health data — keep them in UserDefaults (mirrors consent flag + terminology). Syncing them is wrong scope.
- **Requesting notification authorization at launch/onboarding.** D-20 mandates JIT on first enable. A launch prompt causes permission fatigue (PITFALLS §UX) and contradicts the opt-in-default-off posture.
- **Carrying symptoms/bleeding forward in "Same as yesterday".** D-14: STICKY CONTEXT ONLY (meds/sleep/activity). Carrying events fabricates data.
- **Causal/streak copy.** No "trigger/causes/will" (ClaimsPolicy.forbiddenVerbs), no streaks/guilt (D-21).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Daily repeating reminder at a wall-clock time | A timer/`Date`-math reschedule loop | `UNCalendarNotificationTrigger(dateMatching:repeats:true)` | Handles DST, wall-clock, background firing natively. `[CITED: Apple docs]` |
| Day bucketing of factor rows | `timeIntervalSince1970 / 86400` epoch math | `Calendar.current.startOfDay(for:)` + `isDate(_:inSameDayAs:)` | Already the codebase rule; epoch math breaks across DST/timezones. `[VERIFIED: codebase — Repository.swift]` |
| Severity/mood/intensity input | A custom slider/segmented control | Reuse `SeverityControl` (stress 0–10); system `Picker(.segmented)` (intensity, sleep quality, mood) | `SeverityControl` is shipped + a11y-correct; D-07 reuses it verbatim. `[VERIFIED: codebase — SeverityControl.swift]` |
| Schema versioning/migration | A hand-rolled "version flag + manual reopen" | `VersionedSchema` + `SchemaMigrationPlan.lightweight` | First-party, CloudKit-compatible, already wired. `[VERIFIED: codebase]` |
| String length DoS guard | A new bounds checker | The existing `maxNoteLength` (280) prefix-clamp pattern | Already proven for notes/med-name/free-text. `[VERIFIED: codebase — Repository.swift, SettingsView.swift]` |
| Mockable persistence/notification | Concrete calls sprinkled in views | The `Repository` / `NotificationScheduler` protocol seams | Enables deterministic unit tests; XCUITest can't assert system notifications. `[VERIFIED: codebase — Repository protocol]` |
| Defensive enum decode | `force-unwrap rawValue` | `init(raw:)` mapping unknown→`.other`/`nil` | Corrupt/forward-compatible raws must never crash (T-02-03). `[VERIFIED: codebase — SymptomType/FlowIntensity]` |

**Key insight:** Phase 3 has essentially no novel infrastructure to build — the persistence, migration, snapshot, control, copy-audit, and test seams all exist and were read in source. The only first-build subsystem is the notification scheduler, and even that mirrors the existing protocol-seam pattern. Custom solutions here would be strictly worse than the shipped patterns.

## Runtime State Inventory

> This phase is primarily greenfield-additive, but it touches three runtime-state categories. Inventory below.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | SwiftData store at `default.store` (+ `-shm`/`-wal`). Adding `FactorReading` + `Medication` is a V2→V3 lightweight additive migration — existing V2 rows survive untouched. | Code: new `@Model`s + `SchemaV3` + `v2toV3` stage. Data migration: NONE (additive only; no backfill). A `SchemaMigrationTests` case proves V2 data survives. |
| Live service config | iOS notification authorization status + pending notification requests live in the **system** (UNUserNotificationCenter), NOT in the app's store or git. A user who had no reminders has none; enabling schedules them. | None to migrate (no prior reminders exist — this is the first reminders feature). On every relevant config change, re-arm pending requests idempotently (`removeAllPendingNotificationRequests` then re-add). |
| OS-registered state | No OS-registered state (no Task Scheduler / launchd / pm2 — iOS app). Notification requests are the only OS-registered artifact, created at enable-time. | Re-register (reschedule) on settings change / app launch if reminders enabled. |
| Secrets/env vars | None — no secrets, no env vars. Local notifications need no entitlement, no key, no server. | None. |
| Build artifacts | `xcodegen generate` regenerates `EndoApp.xcodeproj` (gitignored). New folders (`Reminders/`, `Features/CheckIn/`) under `Sources/EndoApp` are auto-picked-up by the `sources: [Sources/EndoApp]` glob — no `project.yml` edit needed. | Run `xcodegen generate` after adding files (already the build ritual). NO new capability/entitlement in `project.yml` this phase. |

**Reminder config persistence:** reminder times, quiet hours, enabled-flag will be NEW UserDefaults keys (e.g. `reminders.enabled`, `reminders.times`, `reminders.quietStart/End`). These are device prefs (NOT synced) — nothing pre-exists to migrate. State explicitly: **None found that requires migration — verified by grepping the codebase; no prior reminder/notification code exists.**

## Common Pitfalls

### Pitfall 1: V2→V3 migration silently loses data because the model isn't nested / a `.custom` stage is used
**What goes wrong:** Defining `FactorReading` as a top-level class (new entity name) or using `MigrationStage.custom` makes the migration non-lightweight or runs hooks that won't fire under CloudKit — risking data loss after Phase 4.
**Why it happens:** Natural instinct is to put new models at top level and to reach for `.custom` when "doing something on migrate."
**How to avoid:** Nest under `extension SchemaV1 { @Model … }`; list additively in `SchemaV3`; use `.lightweight` ONLY; add a `SchemaMigrationTests` case writing V2 rows to an on-disk store, reopening at V3, asserting all survive AND the new entities are queryable (empty). `[VERIFIED: codebase — exact pattern in SchemaMigrationTests.swift]`
**Warning signs:** `FactorReading` not under `extension SchemaV1`; `MigrationStage.custom` anywhere; no V2→V3 migration test.

### Pitfall 2: Unlogged factor renders/persists as a logged zero (honesty violation)
**What goes wrong:** A stress section defaulting to `0`, or a mood prefilled to `1`, writes a reading that downstream insights read as a real low value — fabricating data and corrupting correlations (the whole insights thesis).
**Why it happens:** SwiftUI controls want a non-optional binding; easiest default is the scale's low end.
**How to avoid:** Use optional bindings (`Int?`); the control appears only after the user opts to log (stress reveal pattern from `DayLogEditorView`); `FactorDay.stress`/`.mood` stay `nil` when unlogged. Unit-test that no-reading ⇒ `nil`, and a logged `0`/`1` ⇒ that value. `[VERIFIED: codebase — SymptomReading absent-severity test; PITFALLS.md absence≠0]`
**Warning signs:** Non-optional `@State var stress = 0`; a `.stress` reading written on an untouched section; `FactorDay` coercing nil→0.

### Pitfall 3: Notification logic is untestable because it's wired directly to `UNUserNotificationCenter`
**What goes wrong:** Quiet-hours filtering and auto-suppress logic live inside `UNUserNotificationCenter` calls, so XCUITest (which can't assert system notifications) and unit tests can't verify them — the fatigue guarantees ship unverified.
**Why it happens:** It's quicker to call the system API inline in the settings view.
**How to avoid:** Put scheduling behind the `NotificationScheduler` protocol; extract pure decision functions (`isInQuietHours(_:_:)`, `shouldFireToday(lastLoggedDay:now:)`) and unit-test THOSE; the `UNUserNotificationCenter`-backed impl is a thin shell tested manually/by mock. `[VERIFIED: codebase — Repository protocol seam established this pattern]`
**Warning signs:** `UNUserNotificationCenter.current()` called from a `View`; no `NotificationScheduler` protocol; no quiet-hours unit test.

### Pitfall 4: Authorization requested too early / denied-state not handled
**What goes wrong:** Requesting at launch causes blanket denial; not reflecting denied state leaves users stuck with a dead toggle.
**Why it happens:** Tutorials request in `AppDelegate`/`init`.
**How to avoid:** JIT request only when the master toggle is first flipped on (D-20); query `authorizationStatus()` to render the denied-state line + a button to open `UIApplication.openSettingsURLString`. App stays fully usable in-app if denied. `[CITED: Apple docs; PITFALLS.md §HealthKit auth opacity — same incremental-permission discipline]`
**Warning signs:** `requestAuthorization` in app init/onboarding; no denied-state UI; toggle silently no-ops when permission is off.

### Pitfall 5: "Same as yesterday" carries forward events, fabricating symptom/bleeding data
**What goes wrong:** Prefilling the check-in from yesterday copies symptoms/bleeding, inventing health events the user didn't have today.
**Why it happens:** A naive "copy yesterday's whole day" is the easy implementation.
**How to avoid:** Prefill STICKY CONTEXT ONLY — meds taken, typical sleep, activity type (D-14). Read yesterday's `FactorDay`, apply only those fields as clearly-marked editable prefill; never touch symptom/bleeding draft state. `[VERIFIED: CONTEXT D-14; PITFALLS.md absence≠0]`
**Warning signs:** Check-in prefill reads `SymptomReading`/`BleedingEvent`; prefilled values not marked/editable.

## Code Examples

### Migration test for V2→V3 (mirror `SchemaMigrationTests`)
```swift
// Source: mirror of Tests/EndoAppTests/SchemaMigrationTests.swift (codebase)
@Test func v2Store_survivesLightweightMigrationToV3_withNoDataLoss() async throws {
    // 1. open at V2 on disk, write V2 rows (DayEntry, SymptomReading, BleedingEvent, …), close.
    // 2. reopen SAME file via makeModelContainer(url:) (now Schema(versionedSchema: SchemaV3.self)).
    // 3. #expect all V2 rows survive AND FactorReading/Medication entities are queryable (empty).
}
```

### Factor Section in the editor (mirror `DayLogEditorView` symptom section)
```swift
// Source: mirror of symptomSection / bleedingSection in DayLogEditorView.swift (codebase)
private var stressSection: some View {
    Section("Stress") {
        if let _ = stress {                       // logged → show the control + value
            SeverityControl(severity: Binding(get: { stress ?? 0 }, set: { stress = $0 }))
        } else {                                  // unlogged → "Not logged" + a reveal row
            Button("Log stress") { stress = 5 }   // explicit opt-in; nil stays nil until tapped (D-07)
                .accessibilityIdentifier("daylog.factor.stress.log")
            Text("Not logged").foregroundStyle(.secondary)
        }
    }
}
```

### Just-in-time authorization on first enable (D-20)
```swift
// Source: Apple docs (UNUserNotificationCenter) + project Environment-injection pattern.
@MainActor func enableReminders() async {
    let granted = await scheduler.requestAuthorization()   // FIRST request happens here, not at launch
    if granted {
        await scheduler.reschedule(times: reminderSettings.times, quietHours: reminderSettings.quietHours)
    } else {
        deniedState = true   // render the "turn on in Settings" line + openSettingsURLString button
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `UILocalNotification` | `UNUserNotificationCenter` + `UNNotificationRequest` | iOS 10 | Use the UN framework exclusively; `UILocalNotification` is long-deprecated. `[CITED: Apple docs]` |
| Core Data `NSManagedObject` migrations | SwiftData `VersionedSchema` + `SchemaMigrationPlan` | iOS 17/18 | Already the app's approach. `[VERIFIED: codebase]` |
| Completion-handler `requestAuthorization(options:)` | `async` `requestAuthorization(options:)` | iOS 15+ | Prefer the async form under Swift 6 concurrency. `[CITED: Apple docs]` |

**Deprecated/outdated:** `UILocalNotification` (don't use). Avoid `UNTimeIntervalNotificationTrigger` for daily wall-clock reminders (use `UNCalendarNotificationTrigger`).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Recommended `FactorReading` column layout (`valueRaw`/`amount`/`level`/`note`) and the kind→column mapping table | Pattern 1 | LOW — D-01 explicitly leaves column shape to Claude's discretion. Locked by the planner; any consistent CloudKit-safe shape works. Insights (Ph5–8) consume via `FactorDay`, insulating them from column choices. |
| A2 | Reminder config (times/quiet-hours/enabled) stored in UserDefaults, NOT the synced store | Stack / Inventory | LOW — consistent with how consent flag + terminology prefs are handled; reminder times are not health data. Confirm with planner if any reason to sync them (none apparent). |
| A3 | Saved-med removal = soft-archive (`isArchived`) rather than hard delete, to preserve append-only "taken" history | Pattern 2 | MEDIUM — UI-SPEC says "Days you already logged it as taken are not changed," which soft-archive satisfies cleanly; a hard delete would orphan `valueRaw` med-id tokens in past `.medication` readings. Recommend soft-archive; planner should confirm. |
| A4 | Auto-suppress-today is implemented by re-arming all daily repeating requests against an "is today logged?" check rather than per-occurrence cancellation | Pattern 5 | MEDIUM — `UNCalendarNotificationTrigger(repeats:true)` has no native "skip just today." The pure `shouldFireToday(...)` + re-arm approach is the standard workaround; verify the chosen mechanism in planning. |
| A5 | Mood 1–5 valence stored in `level`; distinct from Phase 2 `.lowMood` symptom | Pattern 1 / req map | LOW on storage; the D-08 *product* distinction is flagged by CONTEXT itself as the one most likely to need UAT review. Storage is unaffected by the UX call. |

## Open Questions

1. **PRN pain-relief representation**
   - What we know: D-06 wants a PRN marker alongside saved-med "taken" toggles.
   - What's unclear: whether PRN is a reserved `.medication` reading (`valueRaw == "prn"`) or a separate column/flag.
   - Recommendation: reserved-token `.medication` reading — no schema change, fits the unified model. Lock in planning.

2. **Quiet-hours window crossing midnight**
   - What we know: D-19a wants a start/end window where no reminder fires.
   - What's unclear: whether the window can span midnight (e.g. 22:00–07:00).
   - Recommendation: support midnight-crossing in the pure `isInQuietHours` function and unit-test both cases (same-day window and wrap-around). Cheap to get right, easy to get wrong.

3. **Mood 1–5 scale direction & labels (D-08)**
   - What we know: 1–5 valence, text-labelled (UI-SPEC), distinct from `.lowMood` symptom.
   - What's unclear: exact labels and whether 1 = worst or best.
   - Recommendation: defer to UI-SPEC iteration / simulator review (Claude's discretion); store the raw 1–5 in `level`. The CONTEXT itself flags this for UAT.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode 26 / iOS 18 SDK | All build/test | ✓ (project builds on it) | 26.x | — |
| iOS 18 Simulator (iPhone 16) | E2E + unit tests | ✓ (used by Phase 1–2 E2E) | iOS 18 | — |
| `xcodegen` | Project generation | ✓ (build ritual, project.yml present) | — | — |
| UserNotifications framework | Reminders | ✓ (system framework, no entitlement for local) | iOS 18 SDK | — |
| Notification *delivery* in Simulator | Manual reminder verification | Partial | — | Unit-test the scheduling LOGIC via the mock; manual on-device/Simulator spot-check for actual delivery. |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** Simulator notification *delivery* is unreliable for automated assertions — fall back to unit-testing the pure scheduling/quiet-hours/suppress logic (the design already isolates it behind the protocol).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`) for units; XCTest/XCUITest for E2E |
| Config file | `project.yml` (XcodeGen) — `EndoAppTests` (unit) + `EndoAppUITests` (E2E) targets |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LIFE-01 | Food tags + free-text round-trip; `factorDays()` returns tags | unit | `-only-testing:EndoAppTests/FactorLoggingTests` | ❌ Wave 0 |
| LIFE-02 | Activity type+minutes+intensity round-trip via Repository | unit | `-only-testing:EndoAppTests/FactorLoggingTests` | ❌ Wave 0 |
| LIFE-03 | Sleep hours/quality; stress 0–10 (nil≠0); mood 1–5; med saved-list + per-day taken | unit | `-only-testing:EndoAppTests/FactorLoggingTests` + `MedicationTests` | ❌ Wave 0 |
| D-10 | V2 store survives V2→V3 lightweight migration; new entities queryable | unit | `-only-testing:EndoAppTests/SchemaMigrationTests` (extend) | ✅ extend existing file |
| D-11 | `FactorDay` snapshot: unlogged stress ⇒ `nil`, logged 0 ⇒ 0 (honesty) | unit | `-only-testing:EndoAppTests/FactorSnapshotTests` | ❌ Wave 0 |
| CHCK-02 | `isInQuietHours` (incl. midnight wrap) + `shouldFireToday` pure logic; mock scheduler reschedule/cancel | unit | `-only-testing:EndoAppTests/NotificationSchedulerTests` | ❌ Wave 0 |
| CHCK-01 | Check-in sheet: open → log factor → "Done for today" → day appears; empty-confirm marks checked-in | E2E | `-only-testing:EndoAppUITests/CheckInFlowUITests` | ❌ Wave 0 |
| LIFE-01/02/03 (UI) | Factor sections appear in editor; select/deselect persists | E2E | extend `EndoAppUITests/LoggingFlowUITests` | ✅ extend |
| D-21 | New factor/check-in/reminder copy passes `ClaimsPolicy` audit (no forbidden verbs/gendered tokens) | unit | `-only-testing:EndoAppTests/CopyAuditTests` (extend) | ✅ extend |

### Sampling Rate
- **Per task commit:** `xcodebuild test … -only-testing:EndoAppTests` (fast unit suite — model, migration, snapshot, notification-logic, copy-audit).
- **Per wave merge:** full `xcodegen generate && xcodebuild test …` (units + E2E).
- **Phase gate:** full suite green (incl. the V2→V3 migration test and the check-in E2E) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/FactorLoggingTests.swift` — LIFE-01/02/03 Repository round-trips (mirror `SymptomLoggingTests`).
- [ ] `Tests/EndoAppTests/MedicationTests.swift` — saved-med CRUD + per-day taken reconciliation + soft-archive history preservation.
- [ ] `Tests/EndoAppTests/FactorSnapshotTests.swift` — `factorDays()` honesty (nil≠0) + defensive decode.
- [ ] `Tests/EndoAppTests/NotificationSchedulerTests.swift` — pure `isInQuietHours`/`shouldFireToday` + mock-scheduler reschedule/cancel.
- [ ] `Tests/EndoAppTests/SchemaMigrationTests.swift` — ADD a V2→V3 case (file exists; extend).
- [ ] `Tests/EndoAppUITests/CheckInFlowUITests.swift` — CHCK-01 E2E (mirror `LoggingFlowUITests`, `-uitest-reset`, a11y-ids `checkin.*`).
- [ ] `Tests/EndoAppTests/CopyAuditTests.swift` — ADD new factor/reminder copy to the audit (file exists; extend).
- [ ] A `MockNotificationScheduler` test double (no install — a struct conforming to the protocol).

## Security Domain

> `security_enforcement: true`, ASVS level 1. This phase processes GDPR Art. 9 special-category health data (factors are health context) entirely on-device, no new network surface.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login (project mandate); no new auth surface. |
| V3 Session Management | no | No sessions. |
| V4 Access Control | yes | Consent gate (ONBD-01): every factor read/write goes through the consent-gated `Repository.assertConsented()` defence-in-depth — the new factor/med methods MUST call it, like every existing method. Notification scheduling itself touches no health record, but the check-in it deep-links to is reachable only inside `AppGate`'s `.consented` branch. `[VERIFIED: codebase — Repository.assertConsented, AppGate]` |
| V5 Input Validation | yes | Bound free-text/med-name at the seam via the `maxNoteLength` prefix-clamp; clamp stress `0...10` and mood `1...5`; defensive enum decode (unknown→`.other`/`nil`). `[VERIFIED: codebase patterns]` |
| V6 Cryptography | no | No new crypto; local store + (Phase 4) private-CloudKit-at-rest. No hand-rolled crypto. |
| V7 Error/Logging | yes | NEVER log factor values / med names / notification body content to console or crash reports (PITFALLS §Security — no PII in logs). Mirror `AppDependencies` which logs only error TYPE, never contents. `[VERIFIED: codebase]` |
| V8 Data Protection | yes | Reminder config (device pref) in UserDefaults; health factors in the on-device store only. No factor/med/notification content leaves the device (no analytics SDK — project mandate). `[VERIFIED: REQUIREMENTS Out-of-Scope]` |

### Known Threat Patterns for SwiftUI + SwiftData + UserNotifications

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unbounded free-text / med-name (DoS via huge paste) | Denial of Service | `maxNoteLength` prefix-clamp at the Repository seam (T-02-07 pattern). |
| Health context (factor values, med names) in notification body or logs | Information Disclosure | Generic, non-specific reminder copy ("here's your daily check-in" — never "log your dairy intake"); never log health values; redact (PITFALLS §Security). |
| Out-of-range / corrupt enum raw crashing decode | Tampering / DoS | Clamp numeric scales; `init(raw:)` maps unknown→`.other`/`nil`, never crashes (T-02-03). |
| Factor write before consent | Elevation of Privilege (data-processing without lawful basis) | `assertConsented()` on every new Repository factor/med method; UI reachable only via `.consented` branch (ONBD-01 / GDPR Art. 9). |
| `.custom` migration silently dropping data under CloudKit (Phase 4) | Tampering / data loss | `.lightweight` additive migration only + a V2→V3 migration-survival test. |

## Sources

### Primary (HIGH confidence)
- Codebase (read in full this session): `Persistence/Models/SymptomReading.swift`, `BleedingEvent.swift`, `DayEntry.swift`; `Persistence/{Repository.swift, SchemaV1.swift, SchemaV2.swift, MigrationPlan.swift, ModelContainer+Config.swift}`; `Domain/{SymptomType, BodyRegion, FlowIntensity}.swift`; `Features/DayLog/{DayLogEditorView, SeverityControl}.swift`; `Features/{Home/HomeView, Settings/SettingsView}.swift`; `App/{AppDependencies, EndoAppApp}.swift`; `Content/ClaimsPolicy.swift`; `Tests/EndoAppTests/{SchemaMigrationTests, SymptomLoggingTests}.swift`; `Tests/EndoAppUITests/LoggingFlowUITests.swift`; `project.yml` — the authoritative templates this phase mirrors.
- `.planning/phases/03-…/03-CONTEXT.md` (D-01..D-21) + `03-UI-SPEC.md` — locked decisions + UI contract.
- `.planning/research/{FEATURES.md, PITFALLS.md, ARCHITECTURE.md}` — factor taxonomy, honesty/fatigue principles, CloudKit-safe rules.
- developer.apple.com/documentation/usernotifications (scheduling local notifications, `UNCalendarNotificationTrigger`, `requestAuthorization`, `removePendingNotificationRequests`, `UNUserNotificationCenterDelegate`) — HIGH on API surface.

### Secondary (MEDIUM confidence)
- Apple Developer Forums 742899 / 744491 / 748049 (via codebase MigrationPlan doc-comment) — SwiftData custom-migration hooks don't fire under CloudKit.

### Tertiary (LOW confidence)
- None — all claims trace to codebase or Apple docs; design recommendations (snapshot shape, scheduler seam, soft-archive) are flagged in the Assumptions Log.

## Food-Trigger Taxonomy + Activity/Sleep Enums (grounded in research/FEATURES.md)

> Final lists are Claude's discretion within D-03/D-04/D-05; the planner locks them. These are grounded in FEATURES.md §"Contextual factors" / §"Food / nutrition logging" (the curated, correlation-oriented set — NOT a calorie DB).

**`FoodTag` (LIFE-01, D-03) — curated endo trigger categories** (from FEATURES.md: "dairy, gluten, caffeine, alcohol, high-FODMAP, processed, large meal"):
`dairy`, `glutenWheat`, `caffeine`, `alcohol`, `highSugar`, `highFODMAP`, `redProcessedMeat`, `largeMeal`, `other` (free-text escape, D-02). Each `plainLabel` non-diagnostic ("Dairy", "Gluten / wheat", …) and copy-audited.

**`ActivityType` (LIFE-02, D-04):** `walking`, `cardio`, `strength`, `yogaStretching`, `other`. Plus `ActivityIntensity`: `low`, `medium`, `high` (text-labelled, never color-only per UI-SPEC).

**`SleepQuality` (LIFE-03, D-05):** `poor`, `okay`, `good`.

**Stress (D-07):** integer 0–10 (reuse `SeverityControl`), `nil` = unlogged. **Mood (D-08):** integer 1–5 valence, `nil` = unlogged. Neither is an enum (per D-02).

All labels route through the `Terminology`/`ClaimsPolicy` choke-point and must pass `CopyAuditTests` (no `forbiddenVerbs` — notably NOT "trigger"/"causes" — and no `forbiddenGenderedTokens`). Note: "trigger-category" is internal naming; user-facing copy must avoid the verb "trigger" (ClaimsPolicy.forbiddenVerbs) — use neutral framing like "Food & drink" / "Tags".

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all first-party, all already in use; zero new packages.
- Architecture / data model / migration / snapshots: HIGH — direct mirror of shipped, source-read Phase 1–2 patterns; A1 discipline already proven.
- Notifications: HIGH on API (verified against Apple docs), MEDIUM on the testable-seam + auto-suppress design (a recommendation, flagged A4).
- Pitfalls: HIGH — drawn from the project's own PITFALLS.md + the exact honesty/migration patterns in source.

**Research date:** 2026-06-08
**Valid until:** 2026-07-08 (stable first-party SDK + internal patterns; re-verify only if Xcode/iOS SDK major bumps).
