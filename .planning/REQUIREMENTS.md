# Requirements: Endo-App

**Defined:** 2026-06-03
**Core Value:** Turn everyday self-tracking into understanding — correlations, cycle/trend patterns, flare warnings, and personalized recommendations that help people with endometriosis make sense of their condition.

## v1 Requirements

Requirements for the initial release. Each maps to a roadmap phase.

### Onboarding & Privacy

- [x] **ONBD-01**: User gives explicit, withdrawable consent before any health data is processed (GDPR Art. 9)
- [x] **ONBD-02**: App surfaces clear "not medical advice / not a medical device" framing in onboarding and alongside insights
- [x] **ONBD-03**: App uses inclusive, non-assumptive language and lets the user customize identity/terminology
- [x] **ONBD-04**: User can read an in-app privacy policy explaining on-device + private iCloud storage and no third-party sharing

### Symptom Tracking

- [x] **SYMP-01**: User can log endo-specific symptoms (pain, fatigue, GI/"endo belly", urinary, mood, brain fog, etc.) with severity
- [x] **SYMP-02**: User can record pain location on a body map with severity
- [x] **SYMP-03**: User can add free-text notes to a day's entry
- [x] **SYMP-04**: User can edit or delete any past entry

### Cycle & Bleeding

- [x] **CYCL-01**: User can log bleeding/flow as a first-class event, independent of any predicted cycle
- [x] **CYCL-02**: User can track their menstrual cycle with predictions that tolerate irregular cycles
- [x] **CYCL-03**: User on continuous hormonal treatment can use a "suppressed / no cycle" mode and the app stays fully useful
- [x] **CYCL-04**: Cycle-day is shown as derived/optional with a confidence indicator (never assumes a 28-day cycle)

### Lifestyle Logging

- [x] **LIFE-01**: User can log food/nutrition for a day
- [x] **LIFE-02**: User can log physical activity / sport
- [x] **LIFE-03**: User can log contextual factors that feed insights (sleep, medication, stress, mood)

### Daily Check-in

- [x] **CHCK-01**: User can complete a fast daily check-in that logs the day quickly
- [x] **CHCK-02**: User can set configurable reminder notification(s) to log (designed to avoid notification fatigue)

### Insights

- [ ] **INST-01**: User can see descriptive trend charts of symptoms and factors over time
- [ ] **INST-02**: User can see how symptoms map onto their cycle phases (cycle-phase mapping)
- [ ] **INST-03**: User can discover correlations/triggers across logged factors, gated by minimum-sample size and confidence
- [ ] **INST-04**: Every insight carries an honesty layer — non-causal language, a confidence indicator, dismissal, and low-confidence insights are withheld
- [ ] **INST-05**: User receives personalized, data-driven recommendations derived from their own patterns
- [ ] **INST-06**: User receives a heuristic, non-diagnostic flare early-warning with confidence (built last, gated by the honesty layer)

### Reports

- [ ] **RPRT-01**: User can generate a doctor-ready PDF report for a chosen date range
- [ ] **RPRT-02**: Report labels each data source (self-reported vs Apple Health) and carries "self-tracked, not diagnostic" framing
- [ ] **RPRT-03**: User can share/export the PDF via the system share sheet

### Data, Sync & Platform

- [x] **DATA-01**: Data persists on-device (SwiftData) using a CloudKit-safe, append-only schema
- [ ] **DATA-02**: Data syncs across the user's own Apple devices via private iCloud (CloudKit), with no account/login
- [ ] **DATA-03**: Sync survives multi-device, offline, and reinstall scenarios without data loss
- [ ] **DATA-04**: Deleting data removes it from the private CloudKit store too, not just locally
- [ ] **DATA-05**: User can import cycle/sleep/workouts/nutrition from Apple HealthKit (read-only); the app stays fully usable if access is denied

## v2 Requirements

Acknowledged but deferred — not in the current roadmap.

### Content & Guidance

- **EDU-01**: Educational knowledge base about endometriosis
- **PROG-01**: Guided therapy programs (movement, nutrition, pain-coping)

### Advanced Insights

- **XADV-01**: Custom n-of-1 experiments (test a hypothesis over time)
- **XADV-02**: Environmental/weather factor correlation
- **XADV-03**: On-device ML flare model once the statistical baseline is outgrown

### Platform & Sharing

- **PLAT-01**: HealthKit write-back (export logged data to Apple Health)
- **PLAT-02**: Partner/family sharing (requires moving off SwiftData's private-DB-only sync)
- **PLAT-03**: Android / web companion

## Out of Scope

Explicitly excluded — documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Medical-device / DiGA certification & diagnostic claims | Regulated, expensive path; positioned as a wellness/self-tracking tool, not a medical device |
| User accounts / custom backend | Stays free to run and privacy-first; private iCloud sync replaces it |
| Analytics / ad / crash SDKs on health data | GDPR Art. 9 + App Store policy + user trust; zero third-party data exfiltration |
| Community / social feed | Not core to v1 value |
| Monetization / paywall | The app is free for everyone |
| Sharing data with researchers (Phendo-style) | Not core; conflicts with strict privacy-first posture |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ONBD-01 | Phase 1 — Foundations: Schema, Privacy Posture & Claims Policy | Complete (01-02) |
| ONBD-02 | Phase 1 — Foundations: Schema, Privacy Posture & Claims Policy | Complete |
| ONBD-03 | Phase 1 — Foundations: Schema, Privacy Posture & Claims Policy | Complete |
| ONBD-04 | Phase 1 — Foundations: Schema, Privacy Posture & Claims Policy | Complete |
| DATA-01 | Phase 1 — Foundations: Schema, Privacy Posture & Claims Policy | Complete (01-02) |
| SYMP-01 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| SYMP-02 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| SYMP-03 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| SYMP-04 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| CYCL-01 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| CYCL-02 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| CYCL-03 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| CYCL-04 | Phase 2 — Core Symptom & Cycle/Bleeding Logging | Complete |
| LIFE-01 | Phase 3 — Lifestyle Logging + Fast Daily Check-in & Reminders | Complete (03-01 data + 03-02 UI; E2E green) |
| LIFE-02 | Phase 3 — Lifestyle Logging + Fast Daily Check-in & Reminders | Complete (03-01 data + 03-02 UI; E2E green) |
| LIFE-03 | Phase 3 — Lifestyle Logging + Fast Daily Check-in & Reminders | Complete (03-01 data + 03-02 UI; meds-taken E2E proven in 03-04 testMedicationTaken_persistsAcrossReopen) |
| CHCK-01 | Phase 3 — Lifestyle Logging + Fast Daily Check-in & Reminders | Complete (03-04 DailyCheckInView fast sheet + Home CTA + notification deep-link + auto-suppress; CheckInFlowUITests GREEN incl. empty-confirm adherence) |
| CHCK-02 | Phase 3 — Lifestyle Logging + Fast Daily Check-in & Reminders | Complete (03-01 scheduler seam + fatigue logic; 03-03 opt-in UI/JIT-permission/quiet-hours/denied-state) — on-device delivery + system prompt are deferred human-verify at the phase gate (03-VALIDATION Manual-Only) |
| DATA-02 | Phase 4 — Private CloudKit Sync | Code-complete + HUMAN-UAT pending (04-01 private-DB-only entitlement + account-aware SyncMode.resolve + composition-root wiring, A1 held / P-2 guard proven; 04-02 non-blocking Settings On/Off line). Device-only multi-device proof captured in 04-HUMAN-UAT (04-03) Part B Scenario 1 — human-gated at the phase verification gate. |
| DATA-03 | Phase 4 — Private CloudKit Sync | Code-complete + HUMAN-UAT pending (04-01 runtime gate + account seam; offline/reinstall/last-writer-wins ride NSPersistentCloudKitContainer defaults). Device-only offline-reconcile + reinstall-restore proof captured in 04-HUMAN-UAT (04-03) Part B Scenarios 2–3 — human-gated. |
| DATA-04 | Phase 4 — Private CloudKit Sync | Code-complete + HUMAN-UAT pending (04-02 closes the automatable LOCAL half: deleteDay removes every day-scoped record incl. FactorReading — no orphan survives to mirror; locked by EditDeleteTests + a med-survives guard, TDD RED→GREEN). Device-only cloud-purge proof (delete→reinstall shows nothing) captured in 04-HUMAN-UAT (04-03) Part B Scenario 4 — human-gated. |
| INST-01 | Phase 5 — Snapshot Contract + Descriptive Trends Engine | Engine + live data path complete (05-01 contract + 05-02 TrendsEngine + 05-03 Repository.daySnapshots() store→DaySnapshot mapping, Option-D parity-guarded); user-visible charts render in Phase 8 |
| INST-02 | Phase 6 — Cycle-Phase Mapping + Insight-Honesty Layer | Pending |
| INST-04 | Phase 6 — Cycle-Phase Mapping + Insight-Honesty Layer | Pending |
| INST-03 | Phase 7 — Correlation & Trigger Discovery | Pending |
| DATA-05 | Phase 9 — HealthKit Import | Pending |
| RPRT-01 | Phase 10 — Doctor-Ready PDF Report | Pending |
| RPRT-02 | Phase 10 — Doctor-Ready PDF Report | Pending |
| RPRT-03 | Phase 10 — Doctor-Ready PDF Report | Pending |
| INST-05 | Phase 11 — Personalized Recommendations | Pending |
| INST-06 | Phase 12 — Flare Early-Warning | Pending |

**Note:** Phase 8 (Insights Integration — Coordinator, Cached Insights & Dashboard) is a pure integration phase that wires the Phase 5–7 engine (INST-01..04) into the live app; it introduces no new requirement IDs of its own.

**Coverage:**

- v1 requirements: 31 total (ONBD ×4, SYMP ×4, CYCL ×4, LIFE ×3, CHCK ×2, INST ×6, RPRT ×3, DATA ×5)
- Mapped to phases: 31 (100%) ✓
- Unmapped: 0 ✓

> Count note: the project brief referenced "27 requirements," but this document enumerates 31 distinct v1 REQ-IDs. All 31 are mapped here, each to exactly one phase.

---
*Requirements defined: 2026-06-03*
*Last updated: 2026-06-03 after roadmap creation (traceability populated, 100% coverage)*
