# Requirements: Endo-App v2.0 — Android Version (Port)

**Defined:** 2026-07-08
**Core Value:** Turn everyday self-tracking into understanding — the Android port delivers the same honest insights experience to Android users, feature-equivalent to iOS v1.1.

All architectural decisions are locked in [research/ANDROID-PORT-PLAN.md](research/ANDROID-PORT-PLAN.md); research pins are in [research/SUMMARY.md](research/SUMMARY.md). Requirements below define "done" for the port.

## v2.0 Requirements

### Engine (`:insights` port)

- [ ] **AENG-01**: Domain enums and value types ported verbatim by raw value (SymptomType 11, BodyRegion 10, FactorKind, FoodTag, ActivityType, ActivityIntensity, SleepQuality, FlowIntensity; DaySnapshot with nil-means-unlogged intact)
- [ ] **AENG-02**: All six engines ported behind the single HonestyGate choke point (Trends, CyclePhaseMapper, Correlation incl. BH-FDR/Fisher/Mann-Whitney/Cliff's delta, Recommendations, Flare) plus ForbiddenTerms — one public producer function
- [ ] **AENG-03**: Ported JVM test suite reaches case-count parity with the Swift suite (~157 cases), tracked in a committed PARITY.md ledger, all green
- [ ] **AENG-04**: `:insights` is pure Kotlin JVM — boundary lint test proves no `android.`/`androidx.` imports
- [ ] **AENG-05**: Cross-platform drift tests — Kotlin enum raw values and ClaimsPolicy substring lists checked against the Swift sources (scan + golden JSON); drift is red, not a convention
- [ ] **AENG-06**: Numeric-divergence fixture pack green (NaN, ±0.0, rounding ties, tied ranks, DST/non-UTC day boundaries)
- [ ] **AENG-07**: `android/` Gradle workspace with version catalog as the zero-dependency audit surface (no analytics/auth/backend coordinates — enforced by test) and path-filtered CI green

### Persistence

- [ ] **APER-01**: All 10 data models persist in Room (iOS model names, app-generated UUID identity) behind a consent-gated repository (health reads/writes fail pre-consent)
- [ ] **APER-02**: User can delete a day and EVERY day-scoped row is purged in one transaction, while non-day-scoped definitions (Medication) survive — proven by cascade + survival tests (iOS GDPR lesson born complete)
- [ ] **APER-03**: Room migration discipline from schema v1 — `exportSchema` on, `schemas/` committed, migration test scaffold in place, destructive migration structurally banned
- [ ] **APER-04**: Health database excluded from Android Auto Backup via backup rules, with an assertion test; only the consent flag/prefs may back up
- [ ] **APER-05**: SnapshotProjector bridge maps Room rows → `:insights` DaySnapshot as the ONLY persistence↔domain meeting point, with ported mapping tests (free text never crosses)
- [ ] **APER-06**: Kotlin ClaimsPolicy port + CopyAudit JVM harness exists BEFORE any UI copy — every user-facing string registers for audit in the change that introduces it
- [ ] **APER-07**: Export format defined — schema-versioned, platform-neutral JSON with crypto header spec and UUID identity (format contract precedes implementation)

### App shell + consent

- [ ] **ASHL-01**: User completes onboarding/consent before any health UI can exist — consent-by-construction gate (health composables only composed post-consent), withdrawing consent re-gates, deep links land on the gate — proven by UI tests
- [ ] **ASHL-02**: Bloom design system ported (color tokens, typography scale, components, tab chrome) and rendering on all shell screens
- [ ] **ASHL-03**: 3-tab shell (Log / Insights / Explore) with edge-to-edge and predictive back handled from day one (targetSdk 36)
- [ ] **ASHL-04**: App declares NO `INTERNET` permission — manifest assertion test green (the Data safety form's strongest artifact)

### Tracking

- [ ] **ATRK-01**: User can complete the fast Daily Check-In flow (symptoms → factor sections → bleeding, nothing mandatory; empty confirm records adherence without inventing data)
- [ ] **ATRK-02**: User can log via the Day Log editor incl. the BodyMap pain map with per-region TalkBack semantics and ≥48dp targets
- [ ] **ATRK-03**: User can manage medications (add, archive) and record treatment state
- [ ] **ATRK-04**: User can enable a daily check-in reminder — inexact scheduling only, just-in-time POST_NOTIFICATIONS, survives reboot; reminder code physically separate from flare code
- [ ] **ATRK-05**: Untouched optional sections read as "not logged" everywhere — never 0 (absence ≠ 0 honesty carried through UI round trips)

### Health Connect import

- [ ] **AHC-01**: App gates on the three Health Connect availability states (built-in 14+ / installable 9–13 / impossible on 8.x) with honest empty/unavailable states; manual logging never blocked
- [ ] **AHC-02**: Read-only access with BOTH rationale surfaces declared (Android 13- activity AND 14+ alias), manifest test proves zero write permissions and both declarations
- [ ] **AHC-03**: User can import menstruation, sleep, exercise, and nutrition records with provenance rows ("from Health Connect" labels), manual-wins/nil-honest merge, idempotent re-import
- [ ] **AHC-04**: The 30-day history window is handled deliberately — either `READ_HEALTH_DATA_HISTORY` requested with rationale or the import window honestly labeled

### Insights + Explore surfaces

- [ ] **AINS-01**: User sees the insights dashboard — trends, cycle-phase, possible-associations, recommendations, flare sections of gated cards with plain-language confidence, honest empty states, pinned not-medical-advice disclaimer, non-destructive dismiss
- [ ] **AINS-02**: Flare signals surface in-app ONLY — structural test scans flare sources for notification/alarm APIs; no alarm visuals
- [ ] **AINS-03**: User can scrub/tap trend charts to read exact day + value; gap days read "not logged"; severity-0 reads as a real zero
- [ ] **AINS-04**: User can combine logged series in Explore — aligned per-scale overlay lanes + event-marker lanes on a shared time axis
- [ ] **AINS-05**: User can view a descriptive-only factor-vs-factor scatter (count-encoded, no fitted line/r-value) reachable from a possible-association card
- [ ] **AINS-06**: Chart honesty rules pinned by tests on the data-prep layer — no dual y-axis, no silent normalization, gaps stay gaps (never zeros, never interpolation)
- [ ] **AINS-07**: Charts are TalkBack-accessible via per-data-point overlay semantics (the non-gesture path — Android has no Audio Graph equivalent)

### Data portability

- [ ] **APRT-01**: User can export all data as a passphrase-encrypted file (PBKDF2 + AES-GCM per the pinned crypto spec) to a destination they pick via SAF — zero storage permissions
- [ ] **APRT-02**: User can import an export file — passphrase → decrypt → schema-version validation → idempotent upsert by UUID; wrong-passphrase and corrupt-file failures are distinguished gently
- [ ] **APRT-03**: Export format proven by a committed golden fixture + read-back decrypt verification before any success message; two exports of identical data differ (fresh salt/nonce)

### Report + Release

- [ ] **AREL-01**: User can generate a vector PDF report (selectable text) for a chosen date range with per-source labels and prominent self-tracked-not-diagnostic framing
- [ ] **AREL-02**: User can share the report via the system share sheet with no silent upload; report files cleaned from cache; report copy passes the copy audit
- [ ] **AREL-03**: Play release artifacts complete and truthful — Health apps declaration, Data safety form (no data collected/shared), privacy policy URL consistent everywhere, store copy passes the claims audit with the disclaimer in the first paragraph
- [ ] **AREL-04**: Play developer account verified as organization-type (D-U-N-S) at milestone kickoff — health-category apps cannot ship from individual accounts
- [ ] **AREL-05**: targetSdk 36 compliance and a pre-launch report pass on both an Android 13- and an Android 14+ device

## Future Requirements (post-parity)

### Privacy comfort

- **APRV-01**: User can enable an optional biometric/PIN app lock (outside the consent gate)
- **APRV-02**: App hides its recents thumbnail (API 33+ humane FLAG_SECURE alternative)

### Convenience

- **ACNV-01**: "Log today" static app shortcut
- **ACNV-02**: Google Drive appDataFolder sync — revisit ONLY on real user demand for Android multi-device sync (locked deferral)

## Out of Scope

| Feature | Reason |
|---------|--------|
| iOS↔Android live cross-device sync | No CloudKit client on Android; no-backend constraint forbids a bridge — out of scope by decision, not omission |
| Custom backend / accounts / sign-in | Architecture is "no login" on both platforms; GDPR liability + cost |
| Third-party analytics / crash SDKs | Privacy posture; Play vitals only |
| Exact alarms (`SCHEDULE_EXACT_ALARM`) | Play restricts to alarm-clock apps; reminders are inexact by design |
| Blanket FLAG_SECURE | Users legitimately screenshot their own charts for doctors |
| Material You dynamic color | Would recolor semantically-meaningful Bloom chart tokens |
| Widgets / Wear OS | Not core to parity; post-parity candidates |
| Kotlin Multiplatform sharing with iOS | iOS app is shipped pure Swift; retrofitting KMP destabilizes a working product |
| Medical-device framing / diagnosis claims | Wellness positioning, carried from iOS |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| — | — | Populated by roadmapper |

**Coverage:**
- v2.0 requirements: 40 total
- Mapped to phases: 0 (pending roadmap)
- Unmapped: 40 ⚠️ (pending roadmap)

---
*Requirements defined: 2026-07-08*
*Last updated: 2026-07-08 after initial definition*
