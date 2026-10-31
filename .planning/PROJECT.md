# Endo-App

> Working name — easily changed later.

## Current State

**v2.0 Android Version (Port) — feature-complete (2026-07-10).** A native Android port of Endo-App, feature-equivalent to iOS v1.1, now lives in an `android/` monorepo directory alongside the **untouched** iOS app — Kotlin + Jetpack Compose + Room + Health Connect, local-only storage with passphrase-encrypted export/import, no backend, no accounts, no `INTERNET` permission. All 9 phases (17–25) verified; milestone audit PASSED (**42/42 requirements**, 636 tests green as one system — 460 `:app` + 176 `:insights`, real ~16 MB APK, lint clean; ~230 commits, +39,507 LOC across 294 Kotlin files). The insights engine was ported first as the correctness anchor (157-case parity against the Swift suite), and every honesty/privacy invariant carried over as a live enforcing test: consent-by-construction, honesty gate / absence ≠ 0, the delete-day cascade (more complete than iOS — includes ImportedSample), no-flare-notification separation, read-only Health Connect, no-INTERNET manifest, ClaimsPolicy copy audit (incl. store copy), chart honesty, enum/ClaimsPolicy drift guards, and a zero-analytics catalog. Remaining before Play submission = the documented **device/Console/human-gated checklist** (`docs/release/RELEASE-CHECKLIST.md`): organization account + D-U-N-S (start early), hosting the privacy policy + the URL, finalizing app-identity placeholders, the Console form submissions, and the pre-launch report on real Android 13-/14+ devices — plus the per-phase device manual checks. See [milestones/v2.0-ROADMAP.md](milestones/v2.0-ROADMAP.md), [v2.0-MILESTONE-AUDIT.md](v2.0-MILESTONE-AUDIT.md), [research/ANDROID-PORT-PLAN.md](research/ANDROID-PORT-PLAN.md).

<details>
<summary>v1.1 — feature-complete (shipped 2026-06-17)</summary>

**v1.1 — feature-complete (shipped 2026-06-17).** Builds on the feature-complete v1.0. The insight charts are now interactive (scrub-to-read on every dashboard trend chart, gap-honest, with a non-gesture VoiceOver path) and a new consent-gated **Explore** tab lets users combine any logged series into one honest plot: aligned per-scale **overlay lanes** + event-marker lanes, and a descriptive-only factor-vs-factor **scatter** reachable from a "Possible association" card. All 15/15 v1.1 requirements satisfied (CHART-01..04, EXPL-01..07, SCAT-01..04); audit PASSED (7/7 cross-phase integration, 3/3 E2E flows); 25 code-review findings across 4 phases fixed (0 criticals). Built on plain Swift Charts with **zero new dependencies, zero schema/engine changes** — the v1.0 honesty stack (absence ≠ 0, no dual-y/no normalization, descriptive-only, audited ClaimsPolicy copy) carried over intact, and the v1.0 PDF report path stayed byte-identical. Remaining before App Store submission = the documented **device/host-gated checklists** for both v1.0 (CloudKit Dev→Prod, real Developer team, multi-device sync + on-device verifications) and v1.1 (device feel/legibility/VoiceOver, one host-flaky PDF test re-run) — see [ROADMAP.md](ROADMAP.md), [milestones/v1.1-MILESTONE-AUDIT.md](v1.1-MILESTONE-AUDIT.md). Next milestone: run `/gsd-new-milestone`.
</details>

<details>
<summary>v1.0 — feature-complete (shipped 2026-06-10)</summary>

All 12 phases built, goal-verified, and code-reviewed; cross-phase integration intact (7/7 seams); the full insights engine (trends · cycle-phase · correlations · honesty layer · recommendations · flare) ships behind a single honesty gate, with HealthKit import, a doctor-ready PDF report, and private-CloudKit sync wired. 28/31 requirements automatable-complete. Release/device-gated checklist (CloudKit Dev→Prod deploy + a real Developer team; 6 on-device/multi-device human verifications incl. DATA-02/03/04 sync survival) carried forward — see [milestones/v1.0-MILESTONE-AUDIT.md](milestones/v1.0-MILESTONE-AUDIT.md).
</details>

## What This Is

A free, privacy-first **iOS app for people with endometriosis** to track their symptoms, menstrual cycle, nutrition, and physical activity — and turn that data into genuinely useful insights. It is inspired by the official German **Endo-App** (a prescription-only DiGA) but is **free, open to everyone worldwide, and goes deeper on data insights**.

## Core Value

Turn everyday self-tracking into **understanding** — surfacing correlations, cycle/trend patterns, flare predictions, and personalized recommendations that help people with endometriosis make sense of their condition. If everything else fails, the insights must make the effort of logging feel worth it.

## Current Milestone: v2.1 Multilingual

**Goal:** Every screen, insight sentence and PDF report reads in the user's language — English, German, French or Spanish — chosen inside the app, on both Android and iOS.

**Target features:**
- In-app language picker (default: follow the system language), on Android and iOS
- All UI copy translated into DE/FR/ES (~700 strings per platform), English stays the source
- Insight-engine generated sentences (trends, correlations, recommendations, flare) translated
- Locale-aware dates, numbers and units in the app and in the PDF doctor report
- ClaimsPolicy / CopyAudit enforced per language — forbidden medical/causal/alarmist term lists for DE/FR/ES
- One shared cross-platform glossary so Android and iOS use identical terms
- Store listing copy (Play + App Store) in all four languages

**Key context:** Translations ship inside the app (no network, no translation service — the no-`INTERNET` invariant holds). Claude drafts DE/FR/ES; a native-speaker review is a human-gated release step, not faked. Android work continues on `claude/endo-android-version-e608a7`; iOS work starts from `main`. v2.0 (Android port) is feature-complete — its emulator pass (2026-10-31) fixed check-in persistence, an Insights crash, body-map hit-testing and parity gaps.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Insights: trend & cycle-phase mapping with charts — Phase 8 (live Swift Charts dashboard via `tab.insights`; honest absolute-date axes + gap breaks; cycle-phase section omitted for suppressed/no-cycle users. UAT-verified on iOS Simulator + threat-secured.)
- ✓ Apple HealthKit integration (read-only import of cycle/sleep/workouts/nutrition) — Phase 9 (additive `ImportedSample`, manual-wins/nil-honest merge, contextual rationale-before-sheet permission, graceful denial, "from Apple Health" labels; verified + threat-secured. Real-device read deferred to release.)
- ✓ Doctor-ready report export (shareable PDF summary) — Phase 10 (on-device vector PDF for a date range, per-source labels, prominent "self-tracked, not diagnostic" framing, system share sheet with no silent upload; verified + code-reviewed.)
- ✓ Insights: personalized, data-driven recommendations — Phase 11 (deterministic, non-prescriptive discussion prompts composed from the gated correlation engine, behind the single honesty gate; new forbidden-imperative copy scan; dismissable dashboard section; suppressed/sparse → zero. Verified + code-reviewed.)
- ✓ Insights: flare early-warning from personal history — Phase 12 (heuristic, non-diagnostic, uncertainty-first warning composed from a required rising-severity spine + active triggers + cycle-phase proximity; bounded ≤medium, withheld-when-insufficient, calm/non-alarmist card, in-app only — no push; new alarmist/diagnostic copy scan + no-push + no-alarm-visual source guards. Verified + code-reviewed.)
- ✓ Interactive trend charts — scrub/tap to read the exact day + value (v1.1, Phase 13: gap-honest `ChartScrubSelection` resolver behind an additive `allowsSelection` layer; rule-mark + lollipop tooltip, day-change haptic, `AXChartDescriptor` Audio Graph; PDF report path byte-identical. Verified + reviewed.)
- ✓ Explore screen — combine multiple logged series in one overlay plot on a shared time axis (v1.1, Phases 14–15: a consent-gated tab with aligned per-scale lanes + event-marker lanes, multi-series scrub tooltip, suppression-hides-bleeding, honest empty/sparse states, over the Foundation-only series catalog/extraction layer. Verified + reviewed.)
- ✓ Scatter view — one factor against another, each point = one both-logged day (v1.1, Phase 16: descriptive-only count-encoded `PointMark` scatter — no fitted line/r-value — reachable via a "See the days behind this" deep-link from a "Possible association" card. Verified + reviewed.)

### Active

<!-- Current scope. Building toward these. All are hypotheses until shipped. -->

- [ ] Daily symptom tracking (pain location & severity, bleeding, fatigue, mood, GI/"endo belly", etc.)
- [ ] Menstrual cycle tracking & prediction — cycle-aware but tolerant of irregular or hormonally suppressed cycles
- [ ] Food & nutrition logging
- [ ] Physical activity / sport tracking
- [ ] Insights: correlations & triggers across logged factors (symptoms ↔ food, sleep, activity, cycle) — engine shipped (Phase 7) + rendered in dashboard (Phase 8); pending confirmation with real multi-factor data
- [ ] Private iCloud (CloudKit) sync across the user's own devices — no account/login
- [ ] App language selectable in-app: English, German, French, Spanish (Android + iOS) — v2.1

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- Education / knowledge-base content library — deferred to v2; v1 focuses on the tracking + insights differentiator
- Guided therapy programs (physio/movement, nutrition, pain-coping courses) — large content + clinical-credibility lift; deferred
- User accounts / custom backend / web app — avoided to stay free to run and privacy-first (private iCloud sync instead)
- ~~Android / cross-platform — iOS-first; revisit after v1 validation~~ — **promoted to v2.0 scope** (native Android port; see Current Milestone)
- iOS↔Android live cross-device sync — CloudKit has no Android private-DB client and the no-backend constraint forbids a bridge; only a one-time encrypted export/import handoff is contemplated (not scheduled)
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
| v2.1: EN/DE/FR/ES on both platforms, in-app picker, per-language claims audit, Claude-drafted translations + native-speaker review | Global audience + German home market; a translated health app must not slip into medical claims, so the safety audit has to be per language | — Pending |
| v2.0 Android port: native Kotlin + Compose, `android/` monorepo dir, `:insights` ported first, local-only v1 + encrypted export/import (no backend, no accounts) | Mirrors the all-first-party iOS philosophy; CloudKit has no Android client and the no-backend constraint is hard; the pure insights engine is the core value and the safest anchor to port first (decisions locked in research/ANDROID-PORT-PLAN.md) | — Pending |

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
*Last updated: 2026-10-31 — milestone v2.1 Multilingual started (v2.0 Android port feature-complete; audit PASSED 42/42).*
