# Endo-App

> Working name — easily changed later.

## What This Is

A free, privacy-first **iOS app for people with endometriosis** to track their symptoms, menstrual cycle, nutrition, and physical activity — and turn that data into genuinely useful insights. It is inspired by the official German **Endo-App** (a prescription-only DiGA) but is **free, open to everyone worldwide, and goes deeper on data insights**.

## Core Value

Turn everyday self-tracking into **understanding** — surfacing correlations, cycle/trend patterns, flare predictions, and personalized recommendations that help people with endometriosis make sense of their condition. If everything else fails, the insights must make the effort of logging feel worth it.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Insights: trend & cycle-phase mapping with charts — Phase 8 (live Swift Charts dashboard via `tab.insights`; honest absolute-date axes + gap breaks; cycle-phase section omitted for suppressed/no-cycle users. UAT-verified on iOS Simulator + threat-secured.)
- ✓ Apple HealthKit integration (read-only import of cycle/sleep/workouts/nutrition) — Phase 9 (additive `ImportedSample`, manual-wins/nil-honest merge, contextual rationale-before-sheet permission, graceful denial, "from Apple Health" labels; verified + threat-secured. Real-device read deferred to release.)
- ✓ Doctor-ready report export (shareable PDF summary) — Phase 10 (on-device vector PDF for a date range, per-source labels, prominent "self-tracked, not diagnostic" framing, system share sheet with no silent upload; verified + code-reviewed.)
- ✓ Insights: personalized, data-driven recommendations — Phase 11 (deterministic, non-prescriptive discussion prompts composed from the gated correlation engine, behind the single honesty gate; new forbidden-imperative copy scan; dismissable dashboard section; suppressed/sparse → zero. Verified + code-reviewed.)
- ✓ Insights: flare early-warning from personal history — Phase 12 (heuristic, non-diagnostic, uncertainty-first warning composed from a required rising-severity spine + active triggers + cycle-phase proximity; bounded ≤medium, withheld-when-insufficient, calm/non-alarmist card, in-app only — no push; new alarmist/diagnostic copy scan + no-push + no-alarm-visual source guards. Verified + code-reviewed.)

### Active

<!-- Current scope. Building toward these. All are hypotheses until shipped. -->

- [ ] Daily symptom tracking (pain location & severity, bleeding, fatigue, mood, GI/"endo belly", etc.)
- [ ] Menstrual cycle tracking & prediction — cycle-aware but tolerant of irregular or hormonally suppressed cycles
- [ ] Food & nutrition logging
- [ ] Physical activity / sport tracking
- [ ] Insights: correlations & triggers across logged factors (symptoms ↔ food, sleep, activity, cycle) — engine shipped (Phase 7) + rendered in dashboard (Phase 8); pending confirmation with real multi-factor data
- [ ] Private iCloud (CloudKit) sync across the user's own devices — no account/login

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- Education / knowledge-base content library — deferred to v2; v1 focuses on the tracking + insights differentiator
- Guided therapy programs (physio/movement, nutrition, pain-coping courses) — large content + clinical-credibility lift; deferred
- User accounts / custom backend / web app — avoided to stay free to run and privacy-first (private iCloud sync instead)
- Android / cross-platform — iOS-first; revisit after v1 validation
- Medical-device / DiGA certification & diagnostic claims — regulated, expensive path; positioned as a wellness/self-tracking tool, not a medical device
- Community / social features — not core to v1 value
- Monetization / paywall — the app is free for everyone

## Context

- **Reference product:** the official German **Endo-App**, a CE-marked DiGA (Digitale Gesundheitsanwendung) for endometriosis, available on prescription in Germany. It pairs symptom tracking with multimodal therapy modules (movement, nutrition, psychology, education). This project deliberately diverges: **free, global, and tracking-and-insights-focused**.
- **Endometriosis-specific nuances to respect:** cycles are frequently irregular, and many patients are on continuous hormonal treatment that suppresses menstruation — so "cycle prediction" must degrade gracefully and never assume a textbook 28-day cycle. Symptoms commonly extend far beyond pain (fatigue, GI/"endo belly", dyspareunia, mood, etc.).
- **Sensitive data:** health and menstrual data is special-category personal data (GDPR Art. 9). Privacy-by-design is a first-class concern, reinforced by the on-device + private-iCloud architecture. (User is likely EU/German — GDPR awareness assumed.)
- **Apple platform building blocks:** HealthKit (menstrual cycle, sleep, workouts, nutrition), Swift Charts (visualizations), SwiftData/Core Data + CloudKit (local store + private sync).

## Constraints

- **Tech stack**: Native iOS, **SwiftUI** — modern Apple-native UI plus first-class access to HealthKit, Swift Charts, and CloudKit.
- **Data/Sync**: On-device storage with **private iCloud (CloudKit) sync**; no custom backend, no user accounts.
- **Budget**: Free app, no monetization in v1 — architecture must avoid recurring server costs.
- **Privacy/Compliance**: Health data stays private (on device + user's private iCloud); GDPR special-category-data handling; clear "not medical advice" framing throughout.
- **Positioning**: Wellness / self-tracking tool, **not** a regulated medical device — no diagnosis claims, informational insights only.
- **Audience**: Global, general-public endometriosis patients — not gated by prescription, region, or clinician.

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Free & open to everyone (vs. prescription-gated DiGA) | Core motivation — accessibility for all endo patients, not just insured German patients | — Pending |
| v1 = tracking + insights + reports; defer education/guided programs | Nail the insights differentiator first; content/therapy modules are a large, separate lift | — Pending |
| Insights engine as the centerpiece (correlations, trends/cycle mapping, flare prediction, recommendations) | The stated edge over the DiGA and plain trackers | — Pending |
| Native iOS + SwiftUI | Best access to HealthKit, Swift Charts, CloudKit; high-quality UX | — Pending |
| On-device + private iCloud/CloudKit sync, no accounts/backend | Privacy-first for sensitive health data + free to run | — Pending |
| Apple HealthKit integration | Reduce double-logging by importing cycle/sleep/workouts/nutrition | — Pending |
| Position as wellness tool, not a certified medical device | Avoid regulated DiGA/medical-device path; appropriate for a free v1 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-10 after Phase 12 (Flare Early-Warning shipped, verified & reviewed) — v1.0 milestone feature-complete*
