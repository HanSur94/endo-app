# Phase 3: Lifestyle Logging + Fast Daily Check-in & Reminders - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas presented as proposal tables; user accepted all recommendations.

<domain>
## Phase Boundary

Add the **lifestyle + contextual factor logging** that feeds the future insights engine (food, activity, sleep, medication, stress, mood), plus a **fast daily check-in** that records a day in a few taps, plus **opt-in, fatigue-aware local reminders**. The defining constraint: factor data must be **structured, typed, and insights-ready** (not free-text), because Phases 5–8 consume it. Adherence is the prerequisite for the whole insights thesis, so logging must be fast.

In scope: LIFE-01 (food/nutrition), LIFE-02 (activity/sport), LIFE-03 (sleep, medication, stress, mood), CHCK-01 (fast check-in), CHCK-02 (configurable fatigue-aware reminders).

Out of scope (later phases): the insights engine / charts / correlations on these factors (Phases 5–8 — this phase only *captures* the data); private CloudKit sync (Phase 4 — the schema is built CloudKit-safe so it rides along); HealthKit import of sleep/workouts/nutrition (Phase 9 — reduces double-logging later); reports (Phase 10).
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Factor data model & granularity (LIFE-01/02/03 foundation)
- **D-01:** **One unified, append-only `FactorReading` `@Model`**, mirroring `SymptomReading` exactly (nested under `extension SchemaV1`, app-generated `id: UUID` NOT `.unique`, `occurredOn: Date` day-anchor with **no relationships**, all stored properties optional/defaulted, no cycle fields). Discriminated by a `kindRaw: String?` (`FactorKind` enum: food, activity, sleep, medication, stress, mood) with a few optional CloudKit-safe payload columns (e.g. `valueRaw: String?` for tag/category/type, `amount: Double?` for duration/count, `level: Int?` for scale values, `note: String?`). Exact column shape is Claude's discretion within this decision (generic columns vs. light per-kind encoding behind the one model).
- **D-02:** Factor enums live in `Domain/` alongside `SymptomType`/`BodyRegion`/`FlowIntensity`: `FactorKind` + value enums (`FoodTag`, `ActivityType`, `SleepQuality`), each with plain-language, non-diagnostic labels and an extensible "other"/free-text escape. Mood/stress are integer scales (below), not enums.
- **D-03:** **Food (LIFE-01) = multi-select trigger-category tags + optional free-text** — NOT a macro/calorie diary. Curated endo-relevant categories (e.g. dairy, gluten/wheat, caffeine, alcohol, high-sugar, high-FODMAP, red/processed meat…); final list at planning, grounded in `research/FEATURES.md`. Rationale: built for symptom *correlation* + adherence; HealthKit nutrition import (Phase 9) adds depth later.
- **D-04:** **Activity (LIFE-02) = type + duration + perceived intensity.** Small type set (walking, cardio, strength, yoga/stretching, other) + duration (minutes) + intensity (low/med/high). HealthKit workouts import later (Phase 9).
- **D-05:** **Sleep (LIFE-03) = duration (hours) + quality (poor/ok/good).** Both feed insights; HealthKit sleep import later (Phase 9).

### Area 2 — Context factors: medication, stress, mood (LIFE-03)
- **D-06:** **Medication = a small user-maintained saved med list** (name [+ optional note/type], bounded plain strings — reuse the `maxNoteLength`-style DoS guard); per day the user logs **which meds were taken** (taken toggle) + an optional **PRN pain-relief** marker. The saved list powers the fast check-in. No scheduled-regimen engine in v1.
- **D-07:** **Stress = the 0–10 severity scale** (reuse Phase 2's `SeverityControl`), for consistency. `nil` = unlogged, **never** "0 = no stress/healthy" (honesty: absence ≠ 0).
- **D-08:** **Mood = a day-level overall mood factor on a 1–5 valence scale**, kept **distinct** from Phase 2's acute "low mood" `SymptomType`. Documented distinction: Ph2 mood = a symptom occurrence (0–10 severity); Ph3 mood = the day's overall mood valence. The future insights engine treats them as separate signals. (Accepted grey-area call — revisit in UAT if it proves confusing.)
- **D-09:** **Factors extend the existing `DayLog`/`DayEntry` day aggregation** (Phase 2 D-03/D-13). One calendar day holds symptoms + pain points + bleeding + **factors** + note; the day editor and the check-in operate on the same day. No separate decoupled factor store.

### Architecture & data model (carried patterns)
- **D-10:** **New `SchemaV3: VersionedSchema`, purely ADDITIVE over SchemaV2** (adds `FactorReading` + any saved-`Medication` model). Same CloudKit-safe rules (Phase 1 D-17/18/19/20). Extend `MigrationPlan` with a lightweight V2→V3 stage and add a `SchemaMigrationTests` case proving V2 data survives the reopen (the "A1" discipline) — closes before Phase 4 flips CloudKit on.
- **D-11:** **Extend `Repository` additively** with factor + medication CRUD (upsert/remove by (day, kind), `Calendar`-bucketed, consent-gated `assertConsented()`, `save()`-after-write — mirroring the Phase 2 seam). **Add `Sendable`, SwiftData-free value-snapshot reads** (e.g. a `FactorDay`/per-kind snapshot, mirroring `BleedingDay`) so the future insights engine (Phases 5–8) consumes factors **without touching the store**. This insights-ready seam is the most important forward-looking deliverable of the phase.
- **D-12:** **MV + Repository** pattern (no per-screen ViewModels; `@Query`/`@Observable`; logic behind protocols). Swift 6 strict concurrency; notification-scheduling / UI types `@MainActor` as needed.

### Area 3 — Fast daily check-in (CHCK-01)
- **D-13:** A **single fast, sectioned "Daily check-in" sheet** (one scrollable screen, smart defaults) — quick symptom chips, factor sections, bleeding — writing through the same `Repository`. The full `DayLogEditorView` (Phase 2) remains for detailed editing. Few-taps is the adherence lever.
- **D-14:** **"Same as yesterday" prefills STICKY CONTEXT ONLY** (meds taken, typical sleep, activity type) for one-tap confirm. **Never** auto-carries symptoms or bleeding — those are events, and carrying them forward fabricates data (Phase 1/2 honesty principle: absence ≠ 0/healthy).
- **D-15:** **Nothing is mandatory.** The user can confirm/skip each section; an empty confirm still records a lightweight **"checked in today" adherence marker** without inventing symptom data.
- **D-16:** **Two entry points:** a prominent Home "Daily check-in" CTA **and** the reminder notification deep-links straight into the check-in sheet.

### Area 4 — Reminders & notification fatigue (CHCK-02)
- **D-17:** **Local notifications via `UNUserNotificationCenter` only** (no server/push — matches the no-backend, privacy-first constraint). A small `NotificationScheduler` behind a protocol (testable/mockable seam like `Repository`).
- **D-18:** **Opt-in, DEFAULT OFF.** User configures one or more daily reminder times in Settings (suggest a single sensible evening default when first enabling). Minimal by default.
- **D-19:** **Fatigue controls:** (a) respect user-set **quiet hours** (no reminders in the window); (b) **auto-suppress the day's reminder if the user already checked in/logged that day** — cancel/reschedule the pending request once the day is logged; (c) no repeat-nagging within a day.
- **D-20:** **Just-in-time permission** — request notification authorization only when the user first enables a reminder (not at launch/onboarding). If denied, degrade gracefully (in-app only, no nagging); the reminders settings reflect the denied state with a path to Settings.app.

### Inclusive language & claims (carried from Phase 1/2)
- **D-21:** Reuse the **`Terminology` + `ClaimsPolicy`** choke-points for all Phase 3 copy (factor labels, check-in, reminder text) — non-gendered, non-diagnostic, non-causal. **Reminder copy is gentle and non-coercive** — no guilt/streak-pressure framing, consistent with the fatigue-aware, wellness posture.

### Claude's Discretion
- Exact check-in sheet & factor-control layout, the precise food-tag / activity-type lists (ground in `research/FEATURES.md`), the `FactorReading` column design, the saved-medication model shape, the reminder-settings UI, and all copy are left to UI design (UI-SPEC) + planning — gray **within** the decisions above. Review via simulator screenshots + iteration.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 3 reqs: LIFE-01..03, CHCK-01..02
- `.planning/ROADMAP.md` — Phase 3 goal + 4 success criteria

### Research
- `.planning/research/FEATURES.md` — endo factor model; food-trigger categories; table-stakes vs. differentiators
- `.planning/research/ARCHITECTURE.md` — MV + Repository, SwiftData/CloudKit schema constraints
- `.planning/research/PITFALLS.md` — honesty principles (absence ≠ 0), adherence/fatigue considerations

### Phase 1 + 2 (the foundation this builds on)
- `.planning/phases/02-core-symptom-cycle-bleeding-logging/02-0{1,2,3,4}-SUMMARY.md`
- `Sources/EndoApp/Persistence/Models/SymptomReading.swift` — **the event-record template to mirror for `FactorReading`**
- `Sources/EndoApp/Persistence/{Repository.swift,SchemaV2.swift,MigrationPlan.swift,ModelContainer+Config.swift}` — the seam + additive schema/migration pattern (extend to V3)
- `Sources/EndoApp/Persistence/Repository.swift` — `BleedingDay` is the **`Sendable` value-snapshot template** for the insights-ready factor reads
- `Sources/EndoApp/Domain/{SymptomType,BodyRegion,FlowIntensity}.swift` — enum + plain-label pattern for new factor enums
- `Sources/EndoApp/Features/DayLog/{DayLogEditorView,SeverityControl}.swift` — logging UI + reusable 0–10 control (stress)
- `Sources/EndoApp/Features/Home/HomeView.swift` — add the "Daily check-in" CTA
- `Sources/EndoApp/Features/Settings/SettingsView.swift` — add the reminders section
- `Sources/EndoApp/App/AppDependencies.swift` — composition root (inject `NotificationScheduler`; bump schema to V3)
- `Sources/EndoApp/{Terminology/Terminology.swift,Content/ClaimsPolicy.swift}` — copy choke-points (reuse)
- `Tests/EndoAppUITests/` — XCUITest E2E harness (`-uitest-reset` launch arg + a11y-id pattern) to extend for factor logging, check-in, and reminders
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (from Phases 1–2)
- **`SymptomReading`** — exact event-record template for `FactorReading` (CloudKit-safe `@Model`, app-gen UUID, day-anchored, optional/defaulted, no relationships).
- **`Repository` protocol + `SwiftDataRepository`** — `Calendar.isDate(_:inSameDayAs:)` bucketing, severity clamp `0...10`, `maxNoteLength` (280) string bound, `assertConsented()` defence-in-depth, `save()`-after-write. Extend additively.
- **`BleedingDay`** — the `Sendable`, store-free value-snapshot pattern to replicate for insights-ready factor reads.
- **`SeverityControl`** (0–10) — reuse for the stress factor.
- **`SchemaV2` + `MigrationPlan`** — additive VersionedSchema + lightweight migration; `SchemaMigrationTests` discipline to extend for V3.
- **Domain enums** (`SymptomType`, `BodyRegion`, `FlowIntensity`) — pattern for `FactorKind`/`FoodTag`/`ActivityType`/`SleepQuality`.
- **`Terminology` + `ClaimsPolicy`** — inclusive/non-diagnostic copy choke-point.
- **XCUITest E2E harness** — `-uitest-reset` + a11y-id convention.
- Build/test: `xcodegen generate && xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 16'`.

### Established Patterns
- MV (no ViewModels), `@Query`/`@Observable`, services behind protocols; CloudKit-safe model rules; Swift 6 strict concurrency (UI/consent/terminology `@MainActor`).

### Integration Points
- `HomeView` → add a prominent "Daily check-in" CTA (D-16).
- `SettingsView` → add the reminders configuration section (D-18/19/20).
- `DayLogEditorView` → add factor sections (food/activity/sleep/meds/stress/mood) alongside symptoms/bleeding.
- `AppDependencies.modelContainer` → bump to **SchemaV3**; inject a `NotificationScheduler`.
- Notification tap → deep-link into the check-in sheet (D-16).
</code_context>

<specifics>
## Specific Ideas
- **Insights-readiness is the real deliverable.** Typed factors + `Sendable` per-day snapshots are what make Phases 5–8 possible — a free-text factor blob would block them. Get the `FactorReading` model + snapshot seam right.
- **Keep the check-in genuinely fast** — adherence is the prerequisite for every downstream insight. "Same as yesterday" + sensible defaults are the adherence levers.
- **Gentle reminders, no streak-shaming.** Fatigue-aware means the copy and cadence never guilt the user.
- The mood-as-factor vs. mood-as-symptom distinction (D-08) is the one decision most likely to need a second look in UAT.
</specifics>

<deferred>
## Deferred Ideas
- Full food diary / macros / calorie tracking → rely on HealthKit nutrition import (Phase 9) + possible v2; out of scope here.
- HealthKit import of sleep / workouts / nutrition (reduce double-logging) → Phase 9.
- Charts / trends / correlations / cycle-phase mapping **on** these factors → Phases 5–8 (this phase only captures the data).
- Scheduled-medication regimen engine / "time to take your meds" reminders → out of v1 scope (this phase reminds to **log**, not to take meds).
- CloudKit sync of the new factor records → Phase 4 (schema is built CloudKit-safe so it rides along, no rework).
</deferred>

---
*Phase: 3 — Lifestyle Logging + Fast Daily Check-in & Reminders*
*Context gathered: 2026-06-08 (smart discuss, autonomous mode)*
