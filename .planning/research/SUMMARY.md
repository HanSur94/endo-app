# Project Research Summary

**Project:** Endo-App (working name)
**Domain:** Free, privacy-first native iOS endometriosis tracking & on-device insights app
**Researched:** 2026-06-03
**Confidence:** HIGH

## Executive Summary

This is a single-user, no-backend, no-account iOS health-tracking app whose centerpiece is an on-device **insights engine** that turns endometriosis self-tracking into personal understanding (correlations, cycle/trend patterns, heuristic flare warnings, recommendations). Experts build apps like this on an all-first-party Apple stack — **SwiftData with built-in private CloudKit sync, Swift Charts, read-only HealthKit, PDFKit, and plain-Swift statistics (Accelerate) before any ML** — precisely because that stack keeps GDPR Art. 9 special-category data on the user's own devices, costs nothing to run, and needs no login. The recommended minimum deployment target is **iOS 18**.

The architecture has one keystone decision that everything else hangs off: **the insights engine must be an isolated, value-type-consuming, independently testable Swift module** that imports neither SwiftData nor SwiftUI. The Repository converts `@Model` rows into `Sendable` snapshot DTOs; the engine takes snapshots in and returns `Insight` values out, with no I/O. The UI stays lightweight "MV" (SwiftUI views read SwiftData via `@Query`, no per-screen ViewModels), and HealthKit/sync/reporting live behind protocols. This is what makes the differentiator unit-testable against synthetic fixtures without a database, simulator, or iCloud.

The dominant risks are foundational and largely irreversible after launch, so they must be locked in Phase 1. Four pitfalls gate the whole project: (1) the **cycle/bleeding-state data model** must treat cycle phase as derived/optional and model bleeding/treatment state as first-class (endo cycles are irregular or hormonally suppressed); (2) the **CloudKit schema must be append-only, all-optional/defaulted, no `.unique`** because it is effectively immutable post-production; (3) the **App Store 5.1.3(ii) "no health data in iCloud" line** must be interpreted and de-risked early (private CloudKit DB is the accepted pattern, but reviewer-dependent — keep HealthKit-derived samples out of synced storage, add a Privacy Manifest CloudKit reason, and pre-validate via TestFlight with a local-only fallback); and (4) **GDPR explicit consent + non-causal, non-diagnostic insight language** must be designed in from the first screen, not retrofitted. Get these right up front and the rest is conventional Apple-platform engineering.

## Key Findings

### Recommended Stack

All-first-party Apple stack, near-zero third-party dependencies (a deliberate privacy + maintenance choice). No backend, no accounts, no analytics/ad SDKs. Statistics-first insights; Core ML/Create ML deferred until a statistical baseline demonstrably underperforms. See `STACK.md`.

**Core technologies:**
- **SwiftData (iOS 18+) + private CloudKit sync** — persistence + cross-device sync via `ModelConfiguration(cloudKitDatabase:)` — first-party, SwiftUI-native, no backend; private DB only (perfect fit for single-user, no-sharing).
- **Swift Charts** — trend/cycle-phase/correlation visualizations — native, no third-party charting needed.
- **HealthKit (read-only)** — import cycle/sleep/workouts/nutrition to cut double-logging — minimal scope, privacy-gated.
- **PDFKit + Core Graphics + `ImageRenderer`** — doctor-ready vector PDF reports — crisp, text-selectable, clinician-grade.
- **Plain Swift + Accelerate (`vDSP`/`simd`)** — descriptive stats, Pearson/Spearman correlation, threshold/logistic flare heuristics — on-device, fast; do NOT start with ML.
- **Swift Testing + XCUITest**, **Xcode 26**, min target **iOS 18.0**.

**Do NOT use:** any third-party backend (Firebase/Supabase), user accounts/auth, content-capturing analytics/crash SDKs, cloud/LLM inference, third-party charting, raster PDFs, `@Attribute(.unique)` or non-optional CloudKit fields, or diagnostic/medical-device framing.

### Expected Features

Strategic read: **table stakes = Phendo's endo symptom depth + Clue's irregular-tolerant cycle engine; differentiator = Bearable-grade insights but endo-native, free, with the loop closed back to the user; anti-model = Flo's data monetization.** See `FEATURES.md`.

**Must have (table stakes):**
- Endo-specific symptom logging + pain body map (the credibility line vs a generic period tracker)
- Bleeding/flow logging and irregular-tolerant cycle tracking with a suppression/no-cycle mode
- Contextual factor logging (food tags, activity, sleep, meds, mood, fatigue, stress) — inputs for insights
- Fast daily check-in + reminders (adherence is the prerequisite to insight quality)
- Trends & charts (descriptive insights), doctor-ready PDF export, HealthKit read integration
- Private CloudKit sync, no account; inclusive, customizable, non-assumptive design

**Should have (the insights differentiator — build in this order):**
1. Descriptive trends (foundation) · 2. Cycle-phase mapping · 3. Correlation/trigger discovery + insight-honesty layer (the centerpiece) · 4. Personalized recommendations · 5. Flare prediction/early warning (hardest, riskiest, build last, heuristic-first) · 6. Insight-honesty layer (data-sufficiency, confidence — cross-cuts 1–5).

**Defer (v1.x / v2+):**
- Personalized recommendations & flare prediction → v1.x once correlation engine is proven; flare prediction needs a dedicated research phase
- Custom experiments, HealthKit write-back, weather factor → v1.x
- Education/knowledge library, guided therapy programs, Android/web, community → v2+ (PROJECT out-of-scope)

### Architecture Approach

**MV (Model–View) UI + a thin Repository/Service layer; NOT MVVM-per-screen and NOT TCA.** SwiftData `@Model` types are already observable and `@Query` binds the store to views, so ViewModels add boilerplate with no testability gain. The heavy logic lives in injectable services behind protocols. The single most important structural decision: **the insights engine consumes value-type snapshots, not `@Model` objects** — this is the testability seam. See `ARCHITECTURE.md`.

**Major components:**
1. **SwiftData store** — single source of truth; local SQLite + automatic private CloudKit sync (`SymptomEntry`, `CycleEvent`, `FoodLog`, `ActivityLog`, `ImportedSample`, `CachedInsight`).
2. **Repository** — CRUD facade + background-context bulk writes; converts `@Model` → `Sendable` `DaySnapshot` DTOs.
3. **Insights engine** — standalone Swift package (no SwiftData/SwiftUI import); pure functions: correlation, trend/cycle-phase aggregation, flare heuristic, recommendations.
4. **Insights Coordinator** — pulls snapshots → runs engine off-main → persists `CachedInsight` (which also syncs).
5. **HealthKit Import Service** — observer + anchored-query pipeline → `ImportedSample` (additive second source, never foundational).
6. **Reporting/PDF Builder** — `ImageRenderer` over a SwiftUI report view → PDF → `ShareLink`.

### Critical Pitfalls

Four are **[FOUNDATIONAL]** — cheap to bake in wrong now, expensive-to-impossible to fix after launch. They gate Phase 1.

1. **28-day cycle assumption [FOUNDATIONAL]** — model bleeding/treatment state as first-class and independent of "cycle"; cycle-day is derived, optional, confidence-scored; predictions degrade gracefully; anchor correlations on calendar time, not assumed phase. Must serve suppressed users (the differentiator must too).
2. **CloudKit schema rigidity & data loss [FOUNDATIONAL]** — production schema is append-only: all attributes optional/defaulted, no `@Attribute(.unique)`, relationships optional + inverse, `VersionedSchema` from v1, append-only event records over mutable daily aggregates (last-writer-wins is uncustomizable). Test two-device + offline + reinstall.
3. **App Store 5.1.3(ii) iCloud-health-storage line [FOUNDATIONAL]** — private CloudKit DB is the accepted pattern but reviewer-dependent: don't copy HealthKit-derived samples into synced storage, add a Privacy Manifest CloudKit reason + explicit consent copy, and **de-risk with a throwaway TestFlight submission + pre-submission review note, with a local-only fallback**.
4. **GDPR Art. 9 consent + over-confident/causal insight language [FOUNDATIONAL]** — explicit, withdrawable consent before any health processing; delete must purge the CloudKit private DB (not just local); insight copy uses "possible association," never "trigger/causes/will"; min-sample gates + confidence + dismiss on every claim. Pervasive "not medical advice" framing keeps the app on the wellness side of the MDR/DiGA line.

Additional cross-cutting pitfalls: HealthKit read-auth opacity (empty ≠ "healthy"; request contextually; manual logging always usable); inclusive/non-gendered language; notification fatigue / no speculative flare pushes; clinician-credible structured PDF export.

## Implications for Roadmap

Build order is dependency-driven: the insights engine is the product, but it is useless without data and a clean snapshot contract — so build the schema and contract early, the engine against synthetic data early, and integrate the real data sources last.

### Phase 1: Foundations — Schema, Privacy Posture & Claims Policy
**Rationale:** Everything irreversible lives here. The CloudKit schema is append-only post-launch, the cycle/bleeding data model can't be safely retrofitted, the App Store policy line must be resolved before sync is built, and consent + non-causal-language policy is cheap now / catastrophic later.
**Delivers:** CloudKit-safe `VersionedSchema` `@Model` types (bleeding/treatment state first-class, cycle-day optional/derived); composition root + service protocols; explicit GDPR consent flow; privacy policy; claims/inclusive-language policy; an early TestFlight/App-Review de-risk of the private-CloudKit-health storage interpretation.
**Addresses:** Private no-account data ownership; inclusive/customizable design.
**Avoids:** Pitfalls 1, 2, 3, 4 (all four FOUNDATIONAL), plus 6 (MDR line) and 8 (inclusive language).

### Phase 2: Manual Logging UI + Private CloudKit Sync
**Rationale:** Produces real data to design insights against; proves the sync layer under the schema rules.
**Delivers:** Symptom (+ body map), bleeding/flow, cycle, food-tag, activity, meds, mood/fatigue/stress logging via MV/`@Query`; fast daily check-in + reminders; working private CloudKit sync; delete-purges-cloud verified.
**Uses:** SwiftData + CloudKit, SwiftUI MV.
**Implements:** SwiftData store + Repository.
**Avoids:** Pitfall 5 (verify two-device/offline/reinstall matrix), notification fatigue.

### Phase 3: Snapshot Contract + Insights Engine (descriptive → correlation)
**Rationale:** Parallelizable with Phase 2; the value-type snapshot contract is the single most important boundary and must precede heavy engine work. Built against synthetic fixtures — no DB/UI dependency.
**Delivers:** `DaySnapshot`/`Insight` value types; engine for descriptive trends → cycle-phase mapping → correlation/trigger discovery, with the insight-honesty layer (min-sample gate, confidence, non-causal copy, dismiss) built in from the first release.
**Uses:** Plain Swift + Accelerate; Swift Testing against fixtures.
**Implements:** Standalone Insights package.
**Avoids:** Pitfall 3 (over-confident insights) and 6 (medical-device framing).

### Phase 4: Integration — Coordinator, Cached Insights & Dashboard
**Rationale:** The seam that connects persistence (1/2) to the engine (3).
**Delivers:** Repository `@Model`→Snapshot mapping; InsightsCoordinator (off-main, debounced/windowed recompute); `CachedInsight` persistence; Swift Charts insights dashboard.
**Implements:** Coordinator + reporting-adjacent aggregation.

### Phase 5: HealthKit Import
**Rationale:** Deliberately after the pipeline works on manual data, so HealthKit is additive, never foundational.
**Delivers:** Observer + anchored-query import of cycle/sleep/workouts/nutrition into `ImportedSample`, funneled through the same snapshot pipeline; contextual permission prompts.
**Avoids:** Pitfall 7 (read-auth opacity; app fully usable if all reads denied; empty ≠ "healthy").

### Phase 6: Doctor-Ready PDF Report
**Rationale:** Depends on everything upstream (rich data + charts); last in the chain.
**Delivers:** Date-ranged, source-labeled (self-reported vs HealthKit), on-device vector PDF via `ImageRenderer`, shared via `ShareLink`; "self-tracked, not diagnostic" framing.
**Avoids:** Pitfall 10 (report credibility / safe export).

### Phase 7 (v1.x): Recommendations & Flare Prediction
**Rationale:** Compose on top of a proven correlation engine; flare prediction is the hardest/riskiest and needs longitudinal validation.
**Delivers:** Personalized ("you tend to feel better when…") recommendations; heuristic, probabilistic, non-diagnostic flare early-warning with confidence.

### Phase Ordering Rationale
- **Dependencies:** snapshot contract → engine → integration → data sources → report; flare prediction composes correlation + cycle-phase, so it cannot come first.
- **Architecture grouping:** the engine is firewalled as a pure package; UI is MV; HealthKit/sync/report are protocol-bounded services that plug into the snapshot pipeline.
- **Pitfall avoidance:** all four FOUNDATIONAL pitfalls are concentrated in Phase 1 (irreversible), with the multi-device/sync verification gate in Phase 2.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** App Store 5.1.3(ii) interpretation is reviewer-dependent — research/de-risk via TestFlight + review note (HIGH-impact, MEDIUM-certainty); GDPR Art. 9 consent specifics.
- **Phase 3:** correlation statistical rigor (multiple-comparisons correction, confounding, data-sufficiency thresholds) — MEDIUM confidence; design carefully.
- **Phase 7:** flare prediction is genuinely hard, noisy, and brushes the MDR line — dedicated research phase before building.

Phases with standard patterns (skip research-phase):
- **Phase 2:** SwiftData + `@Query` MV logging and private CloudKit sync are well-documented (though the multi-device test matrix is a hard gate, not research).
- **Phase 6:** `ImageRenderer` → PDF is a well-trodden, documented pattern.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core Apple frameworks verified against current docs + 2025–26 ecosystem; on-device-insights design is MEDIUM (recommendation, not a single library). |
| Features | HIGH | Table stakes & endo symptom model from multiple clinical + product sources; insights sub-capabilities & flare prediction MEDIUM (competitor inference + genuinely hard). |
| Architecture | HIGH | SwiftData/CloudKit constraints, HealthKit query model, and MV-vs-MVVM guidance corroborated by official docs + multiple credible sources; conflict-resolution detail MEDIUM. |
| Pitfalls | HIGH | Apple-policy/HealthKit/CloudKit and regulatory (MDR/GDPR Art. 9) pitfalls strongly sourced; insights-trust/clinical-UX pitfalls MEDIUM (survey evidence). |

**Overall confidence:** HIGH

### Gaps to Address
- **App Store 5.1.3(ii) private-CloudKit health storage:** the one genuine go/no-go uncertainty — resolve with an early throwaway TestFlight submission + pre-submission review note; keep a local-only sync fallback designed in. Handle in Phase 1.
- **Correlation statistical rigor:** thresholds for min-sample, confidence, and multiple-comparison correction are design decisions, not settled — prototype and validate against synthetic + early real data in Phase 3.
- **Flare prediction efficacy & MDR boundary:** unproven on real longitudinal data and language-sensitive — gate behind a dedicated v1.x research phase.
- **SwiftData private-DB-only limitation:** if partner/family sharing ever enters scope, migration to Core Data + `NSPersistentCloudKitContainer` or `CKSyncEngine` is required — out of scope for v1, but note it.

## Sources

### Primary (HIGH confidence)
- developer.apple.com — SwiftData, CloudKit, HealthKit, Swift Charts, PDFKit, Swift Testing, App Store Review Guidelines (5.1.2(vi), 5.1.3(i)/(ii)), HealthKit privacy/authorization
- Context7 `/websites/developer_apple_swiftdata` — SwiftData reference + CloudKit configuration
- EU MDR / MDCG borderline guidance (2019-11 rev.1, 2025-4), BfArM, European Commission; GDPR Art. 9 + explicit-consent (gdpr-info.eu, ICO)
- Phendo phenotypes paper (npj Digital Medicine 2020); endo clinical references (MSD Manual, internationalendo, endo-belly review)

### Secondary (MEDIUM confidence)
- fatbobman, Hacking with Swift, Alex Logan — SwiftData↔CloudKit model rules, ImageRenderer→PDF, SwiftData maturity
- Apple Developer Forums — CloudKit conflict resolution & migration-hook limits; MV-vs-MVVM for SwiftUI
- iTwenty, topolog, WWDC20 — HealthKit observer/anchored-query pipeline
- Endo-App (DiGA), Bearable, Clue, Luna product analysis; endo mHealth systematic review (PMC11845897)
- Broad/Biswakarma/Harper 2022 & Worsfold 2021 — period-tracker prediction accuracy / user frustration
- EndoFound, Endometriosis Network Canada, Clue — inclusive language

### Tertiary (LOW confidence)
- MobiHealthNews — context on the iCloud-storage rule (corroborated by primary Apple guideline)
- iOS 17-vs-18 adoption estimates (~88–92%) — ioscompatibility, telemetrydeck, macrumors

---
*Research completed: 2026-06-03*
*Ready for roadmap: yes*
