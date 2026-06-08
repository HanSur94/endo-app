# Roadmap: Endo-App

## Overview

The Endo-App is a free, privacy-first iOS app that turns endometriosis self-tracking into genuine understanding. The journey is dependency-driven: first lock the irreversible decisions (a CloudKit-safe append-only schema, a cycle/bleeding model that never assumes a 28-day cycle, GDPR explicit consent, a claims/inclusive-language policy, and an early App Store 5.1.3(ii) de-risk), because these are catastrophic to retrofit. Next, build the manual logging surface that produces real data, then prove private CloudKit sync under the schema rules. With data flowing, build the isolated insights engine (the product's differentiator) against synthetic fixtures — descriptive trends, cycle-phase mapping with a cross-cutting insight-honesty layer, and correlation/trigger discovery — then integrate it into a live dashboard. Only after the pipeline works on manual data do we add HealthKit as an additive read-source, then the late-stage doctor-ready PDF report. Finally, the hardest and riskiest insights compose on the proven engine: personalized recommendations and, built dead last and gated by the honesty layer, a heuristic flare early-warning.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundations — Schema, Privacy Posture & Claims Policy** - Lock the irreversible decisions: CloudKit-safe schema, GDPR consent, privacy/claims/inclusive policy, App Store de-risk (completed 2026-06-08)
- [x] **Phase 2: Core Symptom & Cycle/Bleeding Logging** - Endo-credible symptom + body-map logging and an irregular/suppression-tolerant bleeding & cycle model (completed 2026-06-08)
- [x] **Phase 3: Lifestyle Logging + Fast Daily Check-in & Reminders** - Contextual factor logging plus a fast daily check-in and fatigue-aware reminders (completed 2026-06-08)
- [x] **Phase 4: Private CloudKit Sync** - Cross-device private iCloud sync that survives offline/reinstall and truly purges on delete (completed 2026-06-08)
- [ ] **Phase 5: Snapshot Contract + Descriptive Trends Engine** - The value-type snapshot seam and an isolated engine producing descriptive trend charts from fixtures
- [ ] **Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer** - Symptom-to-cycle-phase mapping plus the cross-cutting honesty layer (confidence, non-causal copy, dismiss, min-sample gate)
- [ ] **Phase 7: Correlation & Trigger Discovery** - The differentiator: statistically gated correlation/trigger discovery across logged factors
- [ ] **Phase 8: Insights Integration — Coordinator, Cached Insights & Dashboard** - Wire persistence to the engine off-main, cache insights, and surface a live Swift Charts insights dashboard
- [ ] **Phase 9: HealthKit Import** - Additive read-only import of cycle/sleep/workouts/nutrition that never breaks manual-only usage
- [ ] **Phase 10: Doctor-Ready PDF Report** - Date-ranged, source-labeled, on-device vector PDF report shared via the system share sheet
- [ ] **Phase 11: Personalized Recommendations** - Data-driven, non-prescriptive recommendations derived from the user's own patterns
- [ ] **Phase 12: Flare Early-Warning** - Built last and gated by the honesty layer: a heuristic, non-diagnostic flare early-warning with confidence

## Phase Details

### Phase 1: Foundations — Schema, Privacy Posture & Claims Policy

**Goal**: Lock every irreversible foundational decision so the rest of the build is conventional Apple-platform engineering — a CloudKit-safe append-only schema, an explicit GDPR consent flow, an in-app privacy policy, a claims/inclusive-language policy, and an early App Store 5.1.3(ii) interpretation de-risk.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: ONBD-01, ONBD-02, ONBD-03, ONBD-04, DATA-01
**Success Criteria** (what must be TRUE):

  1. User is shown an explicit, plain-language consent screen and cannot have any health data processed until they opt in; they can later withdraw consent.
  2. User can read an in-app privacy policy that accurately describes on-device + private-iCloud storage and the absence of servers/third parties.
  3. User sees clear "not medical advice / not a medical device" framing during onboarding, and onboarding language is inclusive, non-gendered, and lets the user customize identity/terminology.
  4. The SwiftData schema is `VersionedSchema`, all-optional/defaulted, has no `@Attribute(.unique)`, models bleeding/treatment state as first-class with cycle-day as an optional derived field, and initializes a private-only CloudKit container.
  5. A throwaway TestFlight build plus a pre-submission review note has been used to validate the private-CloudKit-health-storage interpretation, with a local-only fallback designed in.

**Plans**: 3 plans, 3 waves (strictly sequential)Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Wave 1: env setup (full Xcode + XcodeGen, MANUAL/blocking) + iOS 18 .xcodeproj scaffold + Privacy Manifest + 5 RED requirement tests (Nyquist Wave 0)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Wave 2: CloudKit-safe VersionedSchema + local-only container + consent gate + skeleton write/read (DATA-01, ONBD-01 GREEN — the walking-skeleton happy path)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-03-PLAN.md — Wave 3: warm onboarding + disclaimer + offline privacy policy + neutral terminology + Settings withdraw/customize (ONBD-02/03/04 GREEN; human-verify end-to-end)
  > Note: Success Criterion 5 (TestFlight 5.1.3(ii) de-risk) is GATED on Apple Developer enrollment (D-11) and is a deferred follow-up, NOT in Phase 1's executable scope. The architecture that makes that de-risk winnable IS delivered here.

### Phase 2: Core Symptom & Cycle/Bleeding Logging

**Goal**: Deliver the endo-credible logging surface — deep symptom logging with a pain body map, free-text notes, full edit/delete, and a bleeding & cycle model that treats flow as first-class, tolerates irregular cycles, supports a suppressed/no-cycle mode, and shows cycle-day only as derived with a confidence indicator.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: SYMP-01, SYMP-02, SYMP-03, SYMP-04, CYCL-01, CYCL-02, CYCL-03, CYCL-04
**Success Criteria** (what must be TRUE):

  1. User can log endo-specific symptoms (pain, fatigue, GI/"endo belly", urinary, mood, brain fog, etc.) with severity, mark pain location on a body map, and add free-text notes to a day.
  2. User can edit or delete any past entry, and the change is reflected immediately.
  3. User can log bleeding/flow as a standalone event that exists independently of any predicted cycle.
  4. A user on continuous hormonal treatment can switch on a "suppressed / no cycle" mode and the app stays fully useful, showing no phantom cycle phase.
  5. When a cycle exists, cycle-day appears as a derived/optional value with a confidence indicator and never assumes a 28-day cycle; predictions degrade gracefully for irregular data.

**Plans**: 4 plans, 3 waves
**Wave 1**

- [x] 02-01-PLAN.md — Nyquist Wave 0 (failing E2E + RED unit suites) + additive SchemaV2 (SymptomReading/PainPoint/BleedingEvent+clots) + lightweight V1→V2 migration w/ data-survival test + pure Domain enums (SYMP-01, CYCL-01 at persistence level)

**Wave 2** *(blocked on 02-01; the two plans are file-disjoint and run in parallel)*

- [x] 02-02-PLAN.md — Day-log editor vertical slice: real HomeView (replaces WalkingSkeletonView) + DayLogEditorView (symptom multi-select + severity + bleeding + note) + extended Repository (SYMP-01/03, CYCL-01 GREEN end-to-end)
- [x] 02-03-PLAN.md — Pure value-type CycleMath + CycleSummary (derived median, range+confidence, suppressed-emits-nothing, sparse-not-enough; never 28) with regular/irregular/suppressed/sparse fixtures (CYCL-02/03/04 at logic level)

**Wave 3** *(blocked on 02-02 + 02-03)*

- [x] 02-04-PLAN.md — Accessible discrete-region body map (SYMP-02) + edit/delete with cascade (SYMP-04) + suppressed-mode toggle + home CycleSummaryView wired to CycleMath + copy audit + full E2E GREEN (CYCL-02/03/04 surfaced)

**UI hint**: yes
**Validation**: 02-VALIDATION.md (Nyquist — every req mapped to a Swift Testing suite + the extended XCUITest E2E)

### Phase 3: Lifestyle Logging + Fast Daily Check-in & Reminders

**Goal**: Capture the contextual factors that feed insights (food, activity, sleep, medication, stress, mood) and make daily logging adherence-friendly with a fast check-in and configurable, fatigue-aware reminders.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: LIFE-01, LIFE-02, LIFE-03, CHCK-01, CHCK-02
**Success Criteria** (what must be TRUE):

  1. User can log food/nutrition and physical activity/sport for a day.
  2. User can log contextual factors (sleep, medication, stress, mood) that will feed insights.
  3. User can complete a fast daily check-in that records the day in just a few taps (e.g. with sensible defaults / "same as yesterday").
  4. User can set one or more configurable reminder notifications to log, with controls that avoid notification fatigue (opt-in, minimal, quiet hours respected).

**Plans**: 4 plans, 3 waves

**Wave 1**

- [x] 03-01-PLAN.md — Data foundation + Nyquist Wave 0: Domain enums + FactorReading/Medication @Models + additive SchemaV3 + lightweight V2→V3 migration + Sendable FactorDay snapshot + Repository factor/med CRUD + NotificationScheduler protocol/mock/pure-logic + all RED test scaffolds

**Wave 2** *(blocked on 03-01; the two plans are file-disjoint and run in parallel)*

- [x] 03-02-PLAN.md — Factor logging UI slice: DayLogEditorView factor sections (food/activity/sleep/meds/stress/mood) + LoggingFlowUITests (LIFE-01/02/03 GREEN end-to-end)
- [x] 03-03-PLAN.md — Reminders subsystem + manage-meds: ReminderSettings (UserDefaults, opt-in default-off) + SettingsView reminders & manage-meds sections + NotificationScheduler injection + human-verify checkpoint (CHCK-02, LIFE-03 meds)

**Wave 3** *(blocked on 03-02 + 03-03)*

- [x] 03-04-PLAN.md — Fast Daily check-in sheet + Home CTA + notification deep-link routing + auto-suppress + CheckInFlowUITests GREEN (CHCK-01)

**UI hint**: yes

### Phase 4: Private CloudKit Sync

**Goal**: Turn the local store into a robust private iCloud sync that works across the user's own devices, survives offline edits and reinstall-from-iCloud without data loss, and truly purges the CloudKit private DB on delete.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: DATA-02, DATA-03, DATA-04
**Success Criteria** (what must be TRUE):

  1. User's data syncs across their own Apple devices via the private CloudKit database with no account or login.
  2. Edits made on two devices, including offline edits later reconciled, appear correctly on both with no lost records (last-writer-wins on append-only events verified).
  3. A fresh install on a clean device restores the user's full history from private iCloud.
  4. Deleting data removes it from the CloudKit private DB as well as locally — confirmed by a clean reinstall showing nothing.

**Plans**: 3 plans, 3 waves
Plans:

**Wave 1**

- [x] 04-01-PLAN.md — Ship the CloudKit entitlement + Background Modes (runtime-gated, no signing for CI; A1 de-risk first) + AccountAvailability seam + account-aware SyncMode resolved before container construction (DATA-02 config/account-detection level) ✅ A1 held — Simulator suite GREEN with no team; P-2 guard proven (0 CloudKit mirroring errors)

**Wave 2** *(blocked on 04-01)*

- [x] 04-02-PLAN.md — Settings "iCloud sync: On/Off" line + private-iCloud privacy note + close the DATA-04 local orphan gap (deleteDay now removes FactorReading rows) with extended EditDeleteTests (DATA-02/DATA-04) ✅ Non-blocking sync line live (settings.sync.status); DATA-04 orphan bug fixed TDD RED→GREEN; Medication definitions survive a day-delete; full suite GREEN (84/84)

**Wave 3** *(blocked on 04-01 + 04-02)*

- [x] 04-03-PLAN.md — HUMAN-UAT plan: device-only protocol for the 4 DATA criteria (multi-device sync / offline reconcile / reinstall restore / delete-purge) + manual CloudKit schema-deploy + DEVELOPMENT_TEAM steps; blocking human-verify checkpoint (DATA-02/03/04, human_needed) ✅ 04-HUMAN-UAT.md authored (5 scenarios + Part A setup + P-1 caveat + 5.1.3(ii) review note); no fabricated automated sync tests (D-16); blocking checkpoint auto-approved under AUTO_MODE — device DATA-02/03/04 run surfaced to the phase verification gate

### Phase 5: Snapshot Contract + Descriptive Trends Engine

**Goal**: Establish the single most important architectural seam — a `Sendable` value-type snapshot contract (`DaySnapshot`/`Insight`) — and build an isolated insights package (no SwiftData/SwiftUI imports) that produces descriptive trend results, all unit-tested against synthetic fixtures with no database or UI.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: INST-01
**Success Criteria** (what must be TRUE):

  1. The insights engine is a standalone Swift package importing neither SwiftData nor SwiftUI, consuming value-type snapshots and returning `Insight` values with no I/O.
  2. The engine produces descriptive trends of symptoms and factors over time, anchored on absolute calendar dates (not assumed cycle phase).
  3. The engine's trend logic is covered by Swift Testing against synthetic fixtures, including a "suppressed, no bleeding for months" fixture, with no simulator/iCloud dependency.

**Plans**: 3 plans, 2 waves

**Wave 1**

- [x] 05-01-PLAN.md — De-risk first: package-test-in-scheme SMOKE (create Foundation-only EndoInsights local SPM package + wire project.yml packages/dependency/scheme test target; prove the @Test runs + suite green; framework-target fallback) + the value contract (DaySnapshot + SymptomPoint/PainPoint + Insight + Kind/Window/Confidence/Point + package-local snapshot enums, Option D). Deterministic Insight.id. (SC1) — DONE 2026-06-08 (SPM scheme test-target path proven; fallback not needed; SC1 holds; 9 package tests + 87 app tests green)

**Wave 2** *(blocked on 05-01; the two plans are file-disjoint and run in parallel)*

- [ ] 05-02-PLAN.md — Pure TrendsEngine.trends(from:asOf:) (TDD): 7/30/90/all-time windows on absolute calendar dates, per-symptom/per-factor descriptive stats + direction, min-data threshold, suppressed/no-bleeding SC3 gate, absence≠0 honesty, deterministic Equatable outputs + synthetic fixtures + D-12 boundary lint (INST-01, SC2, SC3)
- [ ] 05-03-PLAN.md — App-side Repository.daySnapshots() store→[DaySnapshot] mapping (TDD): consent-gated, mirrors factorDays/bleedingDays, app-enum→package-enum boundary (Option D) with a parity guard + honesty (nil≠0, flow nil w/o bleeding, suppressed propagates) — in-memory ModelContainer mapping tests (keeps SwiftData out of the package, SC1)

### Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer

**Goal**: Add symptom-to-cycle-phase mapping to the engine and build the cross-cutting insight-honesty layer — minimum-sample gating, a confidence indicator, non-causal language, and dismissal — that every present and future insight must pass through.
**Mode:** mvp
**Depends on**: Phase 5
**Requirements**: INST-02, INST-04
**Success Criteria** (what must be TRUE):

  1. The engine maps symptoms onto cycle phases when a reliable cycle exists and gracefully omits phase mapping for suppressed/irregular users instead of inventing a phase.
  2. Every insight the engine emits carries a confidence indicator and non-causal language ("possible association," never "trigger/causes/will").
  3. Low-confidence or under-sampled findings are withheld rather than ranked, enforced by a shared min-sample gate.
  4. Each insight is dismissable ("this isn't right"), and the dismissal is recorded.

**Plans**: TBD

### Phase 7: Correlation & Trigger Discovery

**Goal**: Build the product's differentiating capability — discovery of correlations/triggers across logged factors (symptoms vs food, sleep, activity, cycle) — with statistical rigor (min-sample, effect size, multiple-comparison awareness) routed entirely through the honesty layer.
**Mode:** mvp
**Depends on**: Phase 6
**Requirements**: INST-03
**Success Criteria** (what must be TRUE):

  1. The engine surfaces correlations/triggers across logged factors using the user's own data.
  2. No correlation is shown below the minimum sample/observation threshold, and findings carry effect size plus a plain-language confidence.
  3. Spurious low-confidence correlations are suppressed rather than displayed, and every shown correlation uses non-causal "possible association" framing inherited from the honesty layer.

**Plans**: TBD

### Phase 8: Insights Integration — Coordinator, Cached Insights & Dashboard

**Goal**: Connect the persistence layer to the isolated engine — the Repository maps `@Model` rows to snapshots, an InsightsCoordinator runs the engine off-main with debounced/windowed recompute and persists `CachedInsight` (which also syncs), and a Swift Charts dashboard surfaces the live insights to the user.
**Mode:** mvp
**Depends on**: Phase 4, Phase 7
**Requirements**: (none — integration phase wiring INST-01..04 into the live app)
**Success Criteria** (what must be TRUE):

  1. User opens an insights dashboard and sees their real logged data rendered as trend, cycle-phase, and correlation insights via Swift Charts.
  2. Insights recompute off the main thread after new data is logged without UI jank, and cached results load instantly on app open.
  3. Charts handle gaps, sparse data, and suppressed periods gracefully (absolute-date axis, accessible labels), and cached insights survive across devices via sync.

**Plans**: TBD
**UI hint**: yes

### Phase 9: HealthKit Import

**Goal**: Add HealthKit as a purely additive read-only source — import cycle, sleep, workouts, and nutrition into `ImportedSample`, funneled through the same snapshot pipeline, with contextual permission prompts and full usability when access is denied.
**Mode:** mvp
**Depends on**: Phase 8
**Requirements**: DATA-05
**Success Criteria** (what must be TRUE):

  1. User can import cycle, sleep, workouts, and nutrition from Apple HealthKit (read-only) and that data flows into the same insights pipeline.
  2. Permission is requested incrementally and contextually (with in-app rationale before the system sheet), not as one mega-prompt at launch.
  3. If HealthKit access is fully denied, the app remains fully usable via manual logging, and empty reads never render as "you have no symptoms."

**Plans**: TBD

### Phase 10: Doctor-Ready PDF Report

**Goal**: Generate a clinician-credible, on-device vector PDF for a user-chosen date range that labels each data source (self-reported vs Apple Health), carries "self-tracked, not diagnostic" framing, and is shared via the system share sheet with no silent upload.
**Mode:** mvp
**Depends on**: Phase 9
**Requirements**: RPRT-01, RPRT-02, RPRT-03
**Success Criteria** (what must be TRUE):

  1. User can generate a structured, dated PDF report for a chosen date range, produced entirely on-device.
  2. The report labels each data point's source (self-reported vs Apple Health) and carries a clear "self-tracked, not diagnostic" note.
  3. User can share/export the PDF via the system share sheet with no automatic cloud upload.

**Plans**: TBD
**UI hint**: yes

### Phase 11: Personalized Recommendations

**Goal**: Compose data-driven, non-prescriptive recommendations on top of the proven correlation engine — framed as discussion prompts about the user's own patterns, routed through the honesty layer, never as medical instructions.
**Mode:** mvp
**Depends on**: Phase 8
**Requirements**: INST-05
**Success Criteria** (what must be TRUE):

  1. User receives personalized recommendations derived from patterns in their own data (e.g. "you tend to feel better when…").
  2. Recommendations are phrased as discussion prompts, never as medical instructions ("take X"/"stop Y"), and inherit the honesty layer's confidence and non-causal framing.
  3. Recommendations are only shown when supported by sufficient data and are dismissable.

**Plans**: TBD
**UI hint**: yes

### Phase 12: Flare Early-Warning

**Goal**: Build the hardest, riskiest capability dead last — a heuristic, probabilistic, non-diagnostic flare early-warning derived from the user's personal history, fully gated by the honesty layer and withheld when confidence is low.
**Mode:** mvp
**Depends on**: Phase 11
**Requirements**: INST-06
**Success Criteria** (what must be TRUE):

  1. User receives a heuristic, non-diagnostic flare early-warning derived from their own history, presented with a confidence indicator and uncertainty-first, non-causal language.
  2. The warning is withheld when confidence/data is insufficient (it can say "not enough data" rather than always producing an answer) and is never delivered as a speculative push notification.
  3. The early-warning passes through the same min-sample gate, confidence, and dismiss controls as every other insight, and carries pervasive "not medical advice" framing.

**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundations — Schema, Privacy Posture & Claims Policy | 3/3 | Complete   | 2026-06-08 |
| 2. Core Symptom & Cycle/Bleeding Logging | 4/4 | Complete   | 2026-06-08 |
| 3. Lifestyle Logging + Fast Daily Check-in & Reminders | 4/4 | Complete   | 2026-06-08 |
| 4. Private CloudKit Sync | 3/3 | Complete   | 2026-06-08 |
| 5. Snapshot Contract + Descriptive Trends Engine | 0/3 | Planned | - |
| 6. Cycle-Phase Mapping + Insight-Honesty Layer | 0/TBD | Not started | - |
| 7. Correlation & Trigger Discovery | 0/TBD | Not started | - |
| 8. Insights Integration — Coordinator, Cached Insights & Dashboard | 0/TBD | Not started | - |
| 9. HealthKit Import | 0/TBD | Not started | - |
| 10. Doctor-Ready PDF Report | 0/TBD | Not started | - |
| 11. Personalized Recommendations | 0/TBD | Not started | - |
| 12. Flare Early-Warning | 0/TBD | Not started | - |
