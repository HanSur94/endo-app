# Roadmap: Endo-App

## Overview

v1.0 (iOS) built foundations → manual logging → private CloudKit sync → the Foundation-only insights engine → live dashboard → HealthKit import → doctor-ready PDF report → recommendations → flare early-warning; v1.1 made the insight charts interactive and added the Explore overlay/scatter surfaces. **v2.0 ports the app to native Android** — Kotlin + Jetpack Compose + Room + Health Connect inside an `android/` monorepo directory — feature-equivalent to iOS v1.1, with every honesty/privacy invariant carried over as an enforcing test, not a convention. The port order is locked ([research/ANDROID-PORT-PLAN.md](research/ANDROID-PORT-PLAN.md) §4): the pure-JVM `:insights` engine first (the correctness anchor), then persistence, the consent-by-construction shell, tracking screens, Health Connect import, the honest chart surfaces, a research-inserted encrypted export/import slice, the vector PDF report, and finally the Play Store health-app gauntlet. Storage is local-only with passphrase-encrypted SAF export/import — no backend, no accounts, and no `INTERNET` permission at all.

## Milestones

| Version | Status | Phases | Shipped | Archive |
|---------|--------|--------|---------|---------|
| **v1.0** | ✅ Complete (feature-complete; release-gated human verifications pending) | 1–12 (39 plans) | 2026-06-10 | [roadmap](milestones/v1.0-ROADMAP.md) · [audit](milestones/v1.0-MILESTONE-AUDIT.md) · [requirements](milestones/v1.0-REQUIREMENTS.md) |
| **v1.1** | ✅ Complete (feature-complete; device/host-gated verifications pending) | 13–16 (17 plans) | 2026-06-17 | [roadmap](milestones/v1.1-ROADMAP.md) · [audit](v1.1-MILESTONE-AUDIT.md) · [requirements](milestones/v1.1-REQUIREMENTS.md) |
| **v2.0 Android Version (Port)** | 🚧 In progress | 17–25 | — | [requirements](REQUIREMENTS.md) · [port plan](research/ANDROID-PORT-PLAN.md) · [research](research/SUMMARY.md) |

See [MILESTONES.md](MILESTONES.md) for the shipped-version history + key accomplishments.

<details>
<summary>iOS pre-release checklists carried forward (v1.0 + v1.1 — device/release-gated, unaffected by v2.0)</summary>

### v1.0 — Pre-release checklist

Code-complete; inherently device/release-gated (captured in the v1.0 audit + phase HUMAN-UAT/VALIDATION docs):

- **CloudKit:** deploy the schema (incl. the `CachedInsight` record type) Development → Production in the CloudKit Console before App Store release; set a real `DEVELOPMENT_TEAM`; finalize the placeholder bundle id (`com.endoapp.ios`) + display name.
- **On-device / multi-device human verifications:** DATA-02/03/04 multi-device CloudKit sync survival (2 iCloud devices, `04-HUMAN-UAT`), on-device notification delivery (P3), real-device HealthKit read + auth sheet (P9), PDF clinician-credibility + share sheet (P10), recommendation discussion-prompt tone (P11), flare calm/uncertainty-first tone (P12).
- **Polish backlog:** the dismiss optimistic-vs-recompute scope (Phase 8, cosmetic), the midnight-crossing sleep night-attribution (Phase 9 WR-02), the pre-existing `InsightsCoordinatorTests` debounce flake.

### v1.1 — Pre-release checklist

All v1.1 automated criteria are green; these are device/host-gated (per each phase's VALIDATION Manual-Only table + the v1.1 audit):

- **Device feel/legibility:** trend-chart haptic + scroll-vs-scrub feel + exported-PDF visual identity + VoiceOver Audio Graph (P13); lane alignment + multi-series scrub feel + multi-lane VoiceOver + colorblind-safe lane distinction (P15); scatter count-encoding legibility + descriptive-only tone + colorblind-safe scatter distinction (P16).
- **Host-flaky re-run:** `ReportFlowUITests/testGeneratePDF_fromPopulatedRange_showsPreview` could not run to a clean result on a degraded simulator host — the PDF render path is provably untouched (`git log v1.0..HEAD -- ReportPDFRenderer.swift` empty), so re-run it on a clean host / CI.

</details>

## Phases

**Phase Numbering:** continuous across milestones — v1.1 ended at Phase 16, so v2.0 spans **Phases 17–25**. The 8-phase port order is **locked** (ANDROID-PORT-PLAN.md §4, do not reorder); Phase 23 is the research-mandated encrypted export/import insertion between the charts phase and the release phases (research/SUMMARY.md "Phase 6.5"). Decimal phases (e.g. 22.1) remain available for urgent insertions.

- [x] **Phase 17: Insights Engine Port** - The complete insights engine runs as pure Kotlin JVM with test-case parity against the shipped Swift suite ✅ (verified 5/5; 176 tests green, PARITY=157)
- [x] **Phase 18: Room Persistence** - All 10 models persist consent-gated in Room with the delete-day cascade born complete, migration discipline, backup exclusion, and the copy-audit + export-format contracts in place ✅ (verified 5/5; :app builds, 63 app + 176 insights tests green)
- [x] **Phase 19: App Shell & Consent** - Consent-by-construction gate, Bloom design-system port, and a 3-tab Compose shell that declares no INTERNET permission ✅ (verified 4/4; consent-by-construction proven, Bloom shell, real APK, 99 app + 176 insights tests)
- [x] **Phase 20: Tracking Screens** - Daily Check-In, Day Log editor + BodyMap, medications, and inexact reminders — nothing mandatory, absence ≠ 0 ✅ (verified 5/5; full-day logging persists to Room, accessible BodyMap, inexact reminder, 182 app + 176 insights tests)
- [x] **Phase 21: Health Connect Import** - Read-only import of menstruation/sleep/exercise/nutrition with three-state availability gating, dual rationale declarations, and manual-wins/nil-honest merge ✅ (verified 4/4; read-only manifest+dual rationale, idempotent import, 3-state availability, 237 app + 176 insights tests)
- [x] **Phase 22: Insights Dashboard, Explore & Charts** - Gated insight cards, scrub-to-read trends, Explore lanes + descriptive scatter under the locked chart-honesty rules, TalkBack-accessible per data point ✅ (verified 5/5; honest Canvas charts, TalkBack overlay, flare in-app-only, 319 app + 176 insights tests)
- [ ] **Phase 23: Encrypted Export/Import (INSERTED)** - Passphrase-encrypted SAF export/import (PBKDF2 + AES-GCM) — portability, device migration, and backup in one deliberate surface
- [ ] **Phase 24: PDF Report** - Vector PDF doctor report for a chosen date range with per-source labels and share-intent export
- [ ] **Phase 25: Play Store Prep** - Health apps declaration, truthful Data safety form, claims-audited store copy, targetSdk 36 compliance + pre-launch pass

## Phase Details

### Phase 17: Insights Engine Port
**Goal**: The complete insights engine runs as pure Kotlin JVM in the `android/` monorepo workspace, proven equivalent to the shipped Swift engine
**Depends on**: Nothing (first phase of v2.0; iOS codebase stays untouched)
**Requirements**: AENG-01, AENG-02, AENG-03, AENG-04, AENG-05, AENG-06, AENG-07
**Success Criteria** (what must be TRUE):
  1. The ported JVM test suite reaches case-count parity with the Swift suite (~157 cases), all green, tracked in a committed PARITY.md ledger
  2. One public producer function composes all six engines behind the single HonestyGate choke point, and the ported honesty acceptance fixtures (suppressed-user silence, under-sample withholding, BH-FDR flood, absence-vs-zero) pass
  3. A boundary lint test proves `:insights` contains no `android.`/`androidx.` imports, and cross-platform drift tests turn red when a Kotlin enum raw value or ClaimsPolicy substring diverges from the Swift sources (scan + golden JSON)
  4. The numeric-divergence fixture pack is green — NaN, ±0.0, rounding ties, tied ranks, and DST/non-UTC day boundaries all produce Swift-identical results
  5. The `android/` Gradle workspace builds under path-filtered CI, and a test fails if any analytics/auth/backend coordinate appears in the version catalog
**Plans**: 8 plans
- [ ] 17-01-PLAN.md — Gradle workspace + `:insights` module + boundary-lint/catalog-audit guards + CI + PARITY scaffold
- [ ] 17-02-PLAN.md — Domain enums + value types (Factor/DaySnapshot/Insight+DeterministicUUID) + enum drift scan + golden JSON + fixtures
- [ ] 17-03-PLAN.md — CorrelationStats + hand-rolled lgamma/erf/rounding (Swift-golden gated) + numeric-divergence (stats half)
- [ ] 17-04-PLAN.md — TrendsEngine + CyclePhaseMapper + suppressed-user silence
- [ ] 17-05-PLAN.md — Chart data-prep (ExploreSeries/Extraction/ScatterPairing/Normalization/ScrubSelection)
- [ ] 17-06-PLAN.md — CorrelationEngine (BH-FDR flood → zero, three-AND gate, top-5 determinism)
- [ ] 17-07-PLAN.md — RecommendationsEngine + FlareEngine + copy-drift shims
- [ ] 17-08-PLAN.md — ForbiddenTerms + HonestyGate + InsightsEngine (one public producer) + ClaimsPolicy drift + DST completion + PARITY=157 + CI green

### Phase 18: Room Persistence
**Goal**: All user data persists locally in Room with the iOS data-lifecycle guarantees — consent-gated, delete-complete, migration-disciplined, and backup-excluded from day one
**Depends on**: Phase 17 (SnapshotProjector targets the `:insights` DaySnapshot contract)
**Requirements**: APER-01, APER-02, APER-03, APER-04, APER-05, APER-06, APER-07
**Success Criteria** (what must be TRUE):
  1. All 10 data models (iOS model names, app-generated UUID identity) persist behind the consent-gated repository — health reads/writes fail pre-consent and succeed post-consent, test-proven
  2. Deleting a day purges EVERY day-scoped row in one transaction while non-day-scoped definitions (Medication) survive — cascade + survival tests green (the iOS GDPR Art. 17 lesson born complete)
  3. Room v1 discipline is locked: `exportSchema` on with `schemas/` committed, migration test scaffold green, destructive migration structurally banned, and a backup-rules assertion proves the health database is excluded from Auto Backup (only the consent flag/prefs may back up)
  4. SnapshotProjector is the ONLY persistence↔domain meeting point — ported mapping tests green, and free text never crosses the bridge
  5. The Kotlin ClaimsPolicy port + CopyAudit JVM harness runs green BEFORE any UI copy exists, and the schema-versioned export format contract (platform-neutral JSON + crypto header spec + UUID identity) is committed
**Plans**: 6 plans
- [ ] 18-01-PLAN.md — `:app` module + Room/KSP/DataStore catalog wiring + CI lanes (AGP-9 built-in Kotlin) [wave 1]
- [ ] 18-02-PLAN.md — 10 Room entities + DayScoped marker + DAOs + EndoDatabase v1 (exportSchema, committed schemas/1.json) + migration discipline (MigrationTestHelper, destructive-fallback ban) [wave 2]
- [ ] 18-03-PLAN.md — Delete-day @Transaction cascade (incl. ImportedSample) + reflection completeness meta-test + ConsentStore + consent-gated EndoRepository [wave 3]
- [ ] 18-04-PLAN.md — insightsbridge/SnapshotProjector + FactorKind + ImportedSignals + 24-case parity suite + consent-gated daySnapshots() [wave 4]
- [ ] 18-05-PLAN.md — ClaimsPolicy (reuse :insights ForbiddenTerms) + AuditableCopy/CopyAudit harness before any UI copy [wave 2]
- [ ] 18-06-PLAN.md — Backup exclusion (data_extraction_rules.xml + assertion test) + versioned ExportEnvelope/CryptoHeader format contract [wave 3]

### Phase 19: App Shell & Consent
**Goal**: A user completes onboarding/consent and lands in a Bloom-styled 3-tab shell — health UI cannot exist before consent, by construction
**Depends on**: Phase 18 (consent store + repository; CopyAudit harness must precede UI copy)
**Requirements**: ASHL-01, ASHL-02, ASHL-03, ASHL-04
**Success Criteria** (what must be TRUE):
  1. UI tests prove health surfaces cannot be reached pre-consent (health composables only composed post-consent), withdrawing consent re-gates the app, and deep links land on the gate
  2. The ported Bloom design system (color tokens, typography scale, components, tab chrome) renders on all shell screens
  3. The 3-tab shell (Log / Insights / Explore) handles edge-to-edge and predictive back correctly at targetSdk 36
  4. A manifest assertion test proves the app declares no `INTERNET` permission
**Plans**: 5 plans
- [ ] 19-01-PLAN.md — Compose/Robolectric toolchain + Bloom design-system port (tokens, typography, components, theme) [wave 1]
- [ ] 19-02-PLAN.md — MainActivity + AppGate when-switch + consent-by-construction test (health UI unreachable pre-consent) [wave 2]
- [ ] 19-03-PLAN.md — 3-tab Bloom shell: edge-to-edge insets, predictive-back-to-Log, placeholder cards [wave 3]
- [ ] 19-04-PLAN.md — Onboarding + consent + privacy flow; long-form legal constants (exempt-scanned) [wave 4]
- [ ] 19-05-PLAN.md — No-INTERNET manifest assertion test + phase exit-gate suite [wave 5]
**UI hint**: yes

### Phase 20: Tracking Screens
**Goal**: A user can log a full day — symptoms, pain map, bleeding, factors, medications — as fast as on iOS, with nothing mandatory and absence kept honest
**Depends on**: Phase 19
**Requirements**: ATRK-01, ATRK-02, ATRK-03, ATRK-04, ATRK-05
**Success Criteria** (what must be TRUE):
  1. User completes the fast Daily Check-In flow (symptoms → factor sections → bleeding) with nothing mandatory; an empty confirm records adherence without inventing data
  2. User logs via the Day Log editor including the BodyMap pain map, with per-region TalkBack semantics and ≥48dp touch targets
  3. User adds/archives medications and records treatment state; entries survive a log-persist-reload round trip
  4. The daily check-in reminder uses inexact scheduling only, with just-in-time POST_NOTIFICATIONS, survives reboot, and reminder code stays physically separate from flare code (structural test green)
  5. Untouched optional sections read "not logged" everywhere — never 0 — through full UI round trips
**Plans**: 5 plans
- [ ] 20-01-PLAN.md — Log-home host + intra-Log nav + nil-honest draft/persistence model + shared SeverityControl/section-rows + registered vocabulary [wave 1]
- [ ] 20-02-PLAN.md — Daily Check-In fast-log flow: nothing mandatory, empty-confirm adherence marker, same-as-yesterday sticky-context-only [wave 2]
- [ ] 20-03-PLAN.md — Day Log editor + accessible BodyMap (per-region ≥48dp TalkBack hotspots + list fallback) + severity sheet + guarded delete [wave 3]
- [ ] 20-04-PLAN.md — Medications add/soft-archive + treatment state + Settings (cycle-off, privacy, graduated withdraw) [wave 4]
- [ ] 20-05-PLAN.md — Daily reminder: WorkManager inexact, JIT POST_NOTIFICATIONS, one channel, deep-link-to-gate, manifest + notification-separation guards [wave 5]
**UI hint**: yes

### Phase 21: Health Connect Import
**Goal**: Users who already track in Health Connect avoid double-logging — read-only, honestly gated at every availability tier, never blocking manual logging
**Depends on**: Phase 18, Phase 19 (parallelizable with Phase 20 — no file overlap beyond a settings entry point)
**Requirements**: AHC-01, AHC-02, AHC-03, AHC-04
**Success Criteria** (what must be TRUE):
  1. The app renders the honest state for all three availability tiers (built-in on Android 14+, installable on 9–13, impossible on 8.x), and manual logging is never blocked in any of them
  2. A manifest test proves zero write permissions and BOTH rationale declarations (the Android 13- rationale activity AND the 14+ alias)
  3. User imports menstruation, sleep, exercise, and nutrition records with "from Health Connect" provenance labels; the manual-wins/nil-honest merge holds and re-import is idempotent
  4. The 30-day history window is handled deliberately — either `READ_HEALTH_DATA_HISTORY` is requested with rationale, or the import window is honestly labeled on the import screen
**Plans**: 5 plans
Plans:
- [ ] 21-01-PLAN.md — connect-client + read-only manifest (4 READ + history) + dual rationale + three-state availability gate + rationale screen + registered copy surface (AHC-01/02/04)
- [ ] 21-02-PLAN.md — injectable HealthReader seam + pure record→ImportedSample mapper + iOS-parity day-bucketing (DST/travel/midnight) + real HealthConnectReader adapter (AHC-03)
- [ ] 21-03-PLAN.md — delete-by-hcRecordId DAO + gated repo method + ChangesTokenStore + HealthImportCoordinator + FakeHealthReader (idempotency/deletion/nil-honest/provenance) (AHC-03/04)
- [ ] 21-04-PLAN.md — Bloom connect/import UI off seams: Settings entry + availability states + import screen + honest window label + provenance badge (AHC-01/03/04)
- [ ] 21-05-PLAN.md — composition-root wiring + foreground import trigger + PermissionController contract + end-to-end flow + health-package separation scan (AHC-01/03/04)
**UI hint**: yes

### Phase 22: Insights Dashboard, Explore & Charts
**Goal**: The insights dashboard and Explore surfaces give honest, readable, TalkBack-accessible views of everything logged — under the locked chart-honesty rules
**Depends on**: Phase 17 (engine + chart data-prep), Phase 20 (logged data; Phase 21's imported data flows in through the same projector)
**Requirements**: AINS-01, AINS-02, AINS-03, AINS-04, AINS-05, AINS-06, AINS-07
**Success Criteria** (what must be TRUE):
  1. User sees the gated dashboard sections (trends, cycle-phase, possible-associations, recommendations, flare) with plain-language confidence, honest empty states, a pinned not-medical-advice disclaimer, and non-destructive dismiss
  2. Flare signals surface in-app ONLY — a structural test scanning flare sources for notification/alarm APIs stays green, and no alarm visuals render
  3. User scrubs/taps trend charts to read the exact day + value; a gap day reads "not logged" and a logged severity-0 reads a real zero
  4. User combines logged series in Explore as aligned per-scale overlay lanes + event-marker lanes on a shared time axis, and reaches the descriptive-only count-encoded scatter (no fitted line, no r-value) from a possible-association card
  5. Chart-honesty tests pin no-dual-y-axis, no-silent-normalization, and gaps-stay-gaps (never zeros, never interpolation) on the data-prep layer, and every rendered data point is TalkBack-readable via overlay semantics
**Plans**: 6 plans
- [ ] 22-01-PLAN.md — InsightsCoordinator: off-main recompute + fingerprint CachedInsight cache + non-destructive dismiss + StateFlow (AINS-01) [wave 1]
- [ ] 22-02-PLAN.md — Trend Canvas chart + reusable draw primitives + run-segmentation + chart-honesty tests (gaps=breaks, absence≠0, exact-day scrub) (AINS-06, AINS-03) [wave 1]
- [ ] 22-03-PLAN.md — Bespoke per-data-point TalkBack semantics overlay from the gap-honest resolver + semantics-tree test (AINS-07, AINS-03) [wave 2]
- [ ] 22-04-PLAN.md — Insights dashboard: 5 sections (flare FIRST/calm), plain-language confidence pill, honest states, pinned disclaimer, non-destructive dismiss, flare no-alarm-visual + insights notification-free scans (AINS-01, AINS-02) [wave 3]
- [ ] 22-05-PLAN.md — Explore lanes: suppression-filtered picker + aligned per-scale overlay lanes + event lanes + multi-series scrub (no dual-y, no normalization) (AINS-04) [wave 4]
- [ ] 22-06-PLAN.md — Descriptive-only count-encoded scatter (no fitted line/r-value) + ScatterDeepLink + cross-tab router from a Possible-association card (AINS-05) [wave 5]
**UI hint**: yes

### Phase 23: Encrypted Export/Import (INSERTED)
**Goal**: A user can move or back up their entire history through one deliberate, passphrase-encrypted export/import surface — data portability with zero permissions and zero cloud
**Depends on**: Phase 18 (APER-07 format contract), Phase 22 (research-mandated slice between the charts phase and the release phases, so crypto/SAF work never lands untested in the ship phase)
**Requirements**: APRT-01, APRT-02, APRT-03
**Success Criteria** (what must be TRUE):
  1. User exports all data as a passphrase-encrypted file (PBKDF2 + AES-GCM per the pinned crypto spec) to a destination they pick via SAF — with zero storage permissions declared
  2. User imports an export file: passphrase → decrypt → schema-version validation → idempotent upsert by UUID; re-importing the same file changes nothing
  3. Wrong-passphrase and corrupt-file failures are distinguished and rendered gently
  4. A committed golden fixture pins the format, read-back decrypt verification runs before any success message, and two exports of identical data differ (fresh salt/nonce per export)
**Plans**: 5 plans
Plans:
- [ ] 23-01-PLAN.md — ExportCrypto (PBKDF2 600k + AES-GCM) + ExportCodec (ENDO container, versioned header, GZIP) + fresh-salt/nonce + ordered error taxonomy — pure JVM [wave 1]
- [ ] 23-02-PLAN.md — ExportMapper (9 entity⇄DTO) + ImportDao (@Transaction bulk REPLACE) + consent-gated exportSnapshot()/importEnvelope() + round-trip + idempotency-by-UUID [wave 2]
- [ ] 23-03-PLAN.md — DocumentIo SAF seam (SafDocumentIo "wt" + FakeDocumentIo) + read-back verify + committed golden fixture + zero-storage-permission manifest assertion [wave 2]
- [ ] 23-04-PLAN.md — ExportImportViewModel (off-main KDF, progress, orchestration, gentle no-leak errors) + registered ExportImportCopy + export-package notification-separation scan [wave 3]
- [ ] 23-05-PLAN.md — Settings "Your data" export/import UI (passphrase confirm + cannot-recover warning + spinner) + SAF picker host + Consented-arm-only composition-root wiring [wave 4]

### Phase 24: PDF Report
**Goal**: A user can hand their clinician a clean, honest, vector PDF of a chosen date range — shared on their terms, with no silent upload
**Depends on**: Phase 22 (reuses the Canvas chart drawing code); runs after Phase 23 in the locked sequence
**Requirements**: AREL-01, AREL-02
**Success Criteria** (what must be TRUE):
  1. User generates a vector PDF report (selectable text, not raster) for a chosen date range, with per-source labels and prominent self-tracked-not-diagnostic framing
  2. User shares the report via the system share sheet with no silent upload, and report files are cleaned from cache afterward
  3. All report copy passes the copy audit
**Plans**: TBD
**UI hint**: yes

### Phase 25: Play Store Prep
**Goal**: The app clears Google Play's health-app gauntlet honestly and ships
**Depends on**: Phase 24 (all features shipped and testable). **Kickoff timing note:** AREL-04 (organization-type developer account with D-U-N-S verification) is a milestone-KICKOFF check, not a Phase-25 task — health-category apps cannot ship from individual accounts, and individual→organization recovery takes weeks; start the verification at milestone start, confirm it green here
**Requirements**: AREL-03, AREL-04, AREL-05
**Success Criteria** (what must be TRUE):
  1. The Health apps declaration and Data safety form truthfully state no data collected / no data shared, corroborated by the green no-`INTERNET` manifest test
  2. The privacy policy URL is identical across Play Console, the rationale surface, and the website; store copy passes the claims audit with the disclaimer in the first paragraph of the store description
  3. The Play developer account is verified organization-type (D-U-N-S) — checked at milestone kickoff, confirmed green in this phase
  4. targetSdk 36 compliance holds and the pre-launch report passes on both an Android 13- and an Android 14+ device
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 17 → 18 → 19 → 20 → 21 → 22 → 23 → 24 → 25. Exception: **Phase 21 may run in parallel with Phase 20** (no file overlap beyond a settings entry point — research-confirmed). The locked sequence itself is never reordered.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–12 (archived) | v1.0 | 39/39 | Complete | 2026-06-10 |
| 13–16 (archived) | v1.1 | 17/17 | Complete | 2026-06-17 |
| 17. Insights Engine Port | v2.0 | 8/8 | Complete ✅ | 2026-07-09 |
| 18. Room Persistence | v2.0 | 6/6 | Complete ✅ | 2026-07-09 |
| 19. App Shell & Consent | v2.0 | 5/5 | Complete ✅ | 2026-07-09 |
| 20. Tracking Screens | v2.0 | 5/5 | Complete ✅ | 2026-07-09 |
| 21. Health Connect Import | v2.0 | 5/5 | Complete ✅ | 2026-07-09 |
| 22. Insights Dashboard, Explore & Charts | v2.0 | 6/6 | Complete ✅ | 2026-07-09 |
| 23. Encrypted Export/Import | v2.0 | 0/5 | Not started | - |
| 24. PDF Report | v2.0 | 0/TBD | Not started | - |
| 25. Play Store Prep | v2.0 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-07-08 — v2.0 Android Version (Port), Phases 17–25, 42/42 requirements mapped*
