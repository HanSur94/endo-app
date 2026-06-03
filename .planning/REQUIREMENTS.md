# Requirements: Endo-App

**Defined:** 2026-06-03
**Core Value:** Turn everyday self-tracking into understanding — correlations, cycle/trend patterns, flare warnings, and personalized recommendations that help people with endometriosis make sense of their condition.

## v1 Requirements

Requirements for the initial release. Each maps to a roadmap phase.

### Onboarding & Privacy

- [ ] **ONBD-01**: User gives explicit, withdrawable consent before any health data is processed (GDPR Art. 9)
- [ ] **ONBD-02**: App surfaces clear "not medical advice / not a medical device" framing in onboarding and alongside insights
- [ ] **ONBD-03**: App uses inclusive, non-assumptive language and lets the user customize identity/terminology
- [ ] **ONBD-04**: User can read an in-app privacy policy explaining on-device + private iCloud storage and no third-party sharing

### Symptom Tracking

- [ ] **SYMP-01**: User can log endo-specific symptoms (pain, fatigue, GI/"endo belly", urinary, mood, brain fog, etc.) with severity
- [ ] **SYMP-02**: User can record pain location on a body map with severity
- [ ] **SYMP-03**: User can add free-text notes to a day's entry
- [ ] **SYMP-04**: User can edit or delete any past entry

### Cycle & Bleeding

- [ ] **CYCL-01**: User can log bleeding/flow as a first-class event, independent of any predicted cycle
- [ ] **CYCL-02**: User can track their menstrual cycle with predictions that tolerate irregular cycles
- [ ] **CYCL-03**: User on continuous hormonal treatment can use a "suppressed / no cycle" mode and the app stays fully useful
- [ ] **CYCL-04**: Cycle-day is shown as derived/optional with a confidence indicator (never assumes a 28-day cycle)

### Lifestyle Logging

- [ ] **LIFE-01**: User can log food/nutrition for a day
- [ ] **LIFE-02**: User can log physical activity / sport
- [ ] **LIFE-03**: User can log contextual factors that feed insights (sleep, medication, stress, mood)

### Daily Check-in

- [ ] **CHCK-01**: User can complete a fast daily check-in that logs the day quickly
- [ ] **CHCK-02**: User can set configurable reminder notification(s) to log (designed to avoid notification fatigue)

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

- [ ] **DATA-01**: Data persists on-device (SwiftData) using a CloudKit-safe, append-only schema
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
| (to be filled by roadmap) | — | Pending |

**Coverage:**
- v1 requirements: 27 total
- Mapped to phases: 0 (pending roadmap)
- Unmapped: 27 ⚠️

---
*Requirements defined: 2026-06-03*
*Last updated: 2026-06-03 after initial definition*
