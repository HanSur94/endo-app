# Phase 2: Core Symptom & Cycle/Bleeding Logging - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Source:** Autonomous (decisions made by Claude, grounded in `.planning/research/FEATURES.md` + Phase 1 foundation; user directed "go on")

<domain>
## Phase Boundary

Replace the Phase-1 walking skeleton's placeholder "Log a day" with the **real endo-credible logging surface**: deep symptom logging (catalog + severity + pain body map + notes), full edit/delete of past days, and a **bleeding & cycle model** where flow is a first-class event, cycles are derived (never assumed 28 days), a suppressed/no-cycle mode keeps the app fully useful, and cycle-day appears only as a derived/optional value with a confidence indicator.

In scope: SYMP-01..04, CYCL-01..04. Out of scope (later phases): lifestyle logging/food/activity (Phase 3), sync (Phase 4), the insights engine / charts / correlations / predictions-as-insight (Phases 5–8), HealthKit (Phase 9), reports (Phase 10).
</domain>

<decisions>
## Implementation Decisions (autonomous, research-backed)

### Symptom logging (SYMP-01)
- **D-01:** Curated **endo-specific symptom catalog** (NOT a generic list), drawn from research FEATURES.md: pelvic/abdominal pain, dysmenorrhea (period pain), dyspareunia (pain with sex), GI / "endo belly" (bloating, nausea, constipation/diarrhea), urinary (dysuria/frequency), fatigue, brain fog, mood/low mood, headache/migraine, back & leg/radiating pain. Modeled as an extensible `enum SymptomType` + a free-text "other". Each catalog item carries a short, plain-language, non-diagnostic label.
- **D-02:** **Severity = 0–10 scale** (clinical-standard pain scale) captured via a slider/stepper, with quick presets (mild=3 / moderate=6 / severe=9). Absence of a symptom means "not logged," never "0/healthy" (a Phase-1 honesty principle). Each logged symptom = one record tied to a day.
- **D-03:** Logging is organized around a **DayLog** for a calendar date: a day aggregates its symptom records, pain points, bleeding, and a note. Multiple symptoms per day.

### Pain body map (SYMP-02)
- **D-04:** A **tappable body map** (front + back silhouette) with a curated set of **named regions** relevant to endo (lower abdomen/pelvis, left/right lower quadrant, lower back, hips, upper thighs, rectal/bowel, bladder, chest/diaphragm for thoracic endo, shoulder for referred pain). Tapping a region marks a pain point with a severity (D-02 scale). NOT pixel-precise free-drawing — discrete regions keep it fast, accessible, and analyzable later. Implemented in SwiftUI (vector shapes or an asset image with an overlay of tappable region hotspots).

### Notes + edit/delete (SYMP-03, SYMP-04)
- **D-05:** Free-text **note per day** (length-bounded, plain local string — no injection surface).
- **D-06:** **Full edit & delete** of any past day's log (symptoms, pain points, bleeding, note), reflected immediately in the list and any derived cycle view. Deletes are real removals (append-only at the event level is preserved by modeling edits as new state, but the user-facing affordance is edit/delete of a day).

### Bleeding & cycle (CYCL-01..04)
- **D-07:** **Bleeding/flow is a first-class, standalone event** (extend Phase 1's `BleedingEvent`): per-day flow intensity (spotting / light / medium / heavy) + optional "clots" flag. It exists independently of any predicted cycle (D-19 from Phase 1).
- **D-08:** **Cycle is DERIVED from bleeding events**, never assumed. A "period" = a run of consecutive bleeding days; cycle length = median of recent period-start-to-period-start gaps. Next-period estimate is shown as a **range with a confidence level** (high/medium/low based on regularity + number of recorded cycles), never a single hard date, and **never 28 by default**.
- **D-09:** **Suppressed / no-cycle mode** (CYCL-03): a clear toggle (reuses Phase 1's `TreatmentState`) — "I'm on continuous treatment / I don't have a cycle." When on: NO cycle-day, NO phase, NO period prediction is shown anywhere; the app stays fully useful for symptom + bleeding logging. No phantom phase.
- **D-10:** **Cycle-day & phase are derived/optional with a confidence indicator** (CYCL-04). Shown only when there is enough bleeding history to be meaningful; otherwise hidden or shown as "not enough data yet." Degrades gracefully for irregular data (wider range, lower confidence). Calendar-date is always the anchor, not an assumed cycle position.

### Data model & architecture
- **D-11:** Extend the existing **CloudKit-safe `VersionedSchema`** with a new schema version (`SchemaV2`) ADDING the symptom/pain-point models (additive migration — CloudKit-safe rules from Phase 1 still apply: all attributes optional/defaulted, NO `@Attribute(.unique)`, relationships optional + inverse, append-only event records, app-generated UUIDs). Reuse Phase 1's `DayEntry`, `BleedingEvent`, `TreatmentState`; extend as needed additively. Provide a SwiftData `SchemaMigrationPlan` (lightweight, additive).
- **D-12:** Follow Phase 1's **MV + Repository** pattern (no per-screen ViewModels; `@Query`/`@Observable`; logic behind the `Repository` protocol + composition root). Extend `Repository` with symptom/pain/bleeding/day CRUD + the derived-cycle computation (kept as pure, testable functions — they are the seed of the future Insights engine but stay simple here).
- **D-13:** **Replace `WalkingSkeletonView`** with a real home screen: a day list/history (with edit/delete) + a prominent "Log today" entry into the day-log editor, and a cycle/bleeding summary area that respects suppressed mode.

### Inclusive language & claims (carried from Phase 1)
- **D-14:** Reuse Phase 1's **`Terminology`** (non-gendered defaults + user customization; bleeding term respects preference) and **`ClaimsPolicy`** (no diagnostic verbs; informational, non-causal copy). All Phase-2 copy passes the same choke-point.

### Claude's Discretion
- Exact visual design, the body-map rendering technique (vector vs. asset+hotspots), the precise region list, slider vs. segmented severity control, and the day-log editor layout are left to UI design + planning — gray *within* the decisions above. Review will be via simulator screenshots and iteration.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 2 reqs: SYMP-01..04, CYCL-01..04
- `.planning/ROADMAP.md` — Phase 2 goal + 5 success criteria

### Research
- `.planning/research/FEATURES.md` — the concrete endo symptom model + table-stakes vs. differentiators
- `.planning/research/ARCHITECTURE.md` — MV + Repository, SwiftData/CloudKit schema constraints
- `.planning/research/PITFALLS.md` — the 28-day-cycle assumption + irregular/suppressed tolerance (the #1 pitfall)

### Phase 1 (the foundation this builds on — read the SUMMARYs + key source)
- `.planning/phases/01-foundations-schema-privacy-posture-claims-policy/01-0{1,2,3}-SUMMARY.md`
- `Sources/EndoApp/Persistence/` (`SchemaV1.swift`, `Models/{BleedingEvent,TreatmentState,DayEntry}.swift`, `ModelContainer+Config.swift`, `Repository.swift`)
- `Sources/EndoApp/App/{AppGate,AppDependencies,EndoAppApp}.swift` (consent gate + composition root to extend)
- `Sources/EndoApp/Content/{Terminology,ClaimsPolicy}` + `Sources/EndoApp/Terminology/` (reuse)
- `Sources/EndoApp/Features/Skeleton/WalkingSkeletonView.swift` (to be replaced)
- `Tests/EndoAppUITests/ConsentFlowUITests.swift` (E2E pattern to extend for the new flow)
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (from Phase 1)
- `SchemaV1` + `BleedingEvent`/`TreatmentState`/`DayEntry` models (extend additively into SchemaV2)
- `Repository` protocol + composition root (`AppDependencies`) — extend, don't replace
- `Terminology` + `ClaimsPolicy` (inclusive/non-diagnostic copy choke-point)
- The XCUITest E2E harness (`-uitest-reset` launch arg + a11y-id pattern) — extend with new identifiers for symptom/bleeding/cycle UI
- Test/build: `xcodegen generate && xcodebuild test ... -destination 'platform=iOS Simulator,name=iPhone 16'`

### Established Patterns
- MV (no ViewModels), `@Query`/`@Observable`, services behind protocols; CloudKit-safe model rules; Swift 6 strict concurrency (UI/consent/terminology types are `@MainActor`).

### Integration Points
- `AppGate`'s `.consented` branch currently renders `WalkingSkeletonView` → re-point to the new home screen.
- `AppDependencies.modelContainer` schema → bump to SchemaV2 with a migration plan.
</code_context>

<specifics>
## Specific Ideas
- The body map and the "no assumed 28-day cycle" handling are the two endo-credibility signals that separate this from a generic period tracker — get them right.
- Keep daily logging FAST (few taps) — adherence is the prerequisite for the future insights engine.
</specifics>

<deferred>
## Deferred Ideas
- Food/activity/sleep/med/mood-as-factor logging → Phase 3.
- Charts / trends / correlations / cycle-phase *insights* / flare prediction → Phases 5–8 (Phase 2's derived cycle is a minimal, honest indicator only, not an insight engine).
- HealthKit import of cycle data → Phase 9.
- Reminders / fast "same as yesterday" check-in → Phase 3 (CHCK).
</deferred>

---
*Phase: 2-Core Symptom & Cycle/Bleeding Logging*
*Context gathered: 2026-06-08 (autonomous)*
