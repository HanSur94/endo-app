# Project Research Summary

**Project:** Endo-App — v2.0 "Android Version (Port)"
**Domain:** Native Android port of a shipped privacy-first iOS health tracker (Kotlin + Compose + Room + Health Connect, local-only, no backend)
**Researched:** 2026-07-08
**Confidence:** MEDIUM-HIGH

## Executive Summary

This milestone ports a shipped, tested iOS app (185 Swift files; SwiftUI + SwiftData/CloudKit + HealthKit + the pure-Swift EndoInsights package, ~157 test cases) to native Android inside an `android/` monorepo directory. Every architectural question is already answered in ANDROID-PORT-PLAN.md — 8 locked phases, Kotlin + Compose, Room, read-only Health Connect, local-only storage with encrypted SAF export/import, `:app` + pure-JVM `:insights` modules with a one-way dependency. The research therefore does not choose an approach; it pins exact versions (Kotlin 2.3.21, AGP 9.2.0, Compose BOM 2026.06.00, Room 2.8.4, Health Connect client 1.1.0, zero-dependency `javax.crypto` for export encryption), specifies Android platform norms the parity feature set must honor, provides concrete realizations of the locked patterns (consent-by-construction `when`-switch without a nav library, `insightsbridge` seam with cross-platform enum drift tests, `withContext(Dispatchers.Default)` as the `Task.detached` analog), and catalogs 15 pitfalls with named prevention tests per phase.

The recommended posture is the same one that shipped on iOS: near-zero dependencies (the version catalog is the audit artifact), every invariant enforced by a red-capable test in the phase that introduces its surface, and honesty-preserving data handling (absence ≠ 0, nullable end-to-end) even where Room's relaxed constraints tempt otherwise. Two findings strengthen the design beyond parity: the app can and should ship with **no `INTERNET` permission at all**, making the "no data collected, no data shared" Data safety form trivially truthful; and the monorepo enables Swift-source drift tests (enum raw values, ClaimsPolicy substrings) that keep the two platforms' shared vocabulary from silently diverging.

The key risks are process-shaped, not code-shaped. Google Play's health-app gauntlet (since the January 2026 policy wave) requires an **organization developer account with D-U-N-S verification** — a weeks-long blocker if discovered late, so it must be checked at milestone kickoff, not Phase 8. Health Connect's ~30-day historical read window can silently truncate a longitudinal cycle history import — a Phase 5 permission-or-label decision. And research surfaced one genuine scheduling gap: the encrypted export/import feature is v1 scope but has **no explicit home among the 8 locked phases** — the roadmap must place it deliberately (format + identity in Phase 2, a dedicated implementation slice before Phase 8).

## Key Findings

### Recommended Stack

All choices are locked; STACK.md pins them to executable versions. The catalog is deliberately short — it doubles as the zero-dependency audit surface, and a JVM test should fail if any analytics/auth/backend coordinate ever appears in it.

**Core pins:**
- **Kotlin 2.3.21** — AGP 9.2's documented pairing; 2.4.0 is weeks old, bump later. AGP 9 has **built-in Kotlin**: do NOT apply `org.jetbrains.kotlin.android` in `:app` (the #1 stale-tutorial trap).
- **AGP 9.2.0 / Gradle 9.4.1+ / JDK 21 toolchain** — current stable; JDK 21 covers JUnit 6 and any future Robolectric need.
- **Compose BOM 2026.06.00 + Material 3** — BOM as the single Compose version knob.
- **Room 2.8.4** — deliberately NOT the week-old `androidx.room3` 3.0.0 (KMP-focused rewrite; Google still recommends 2.x for Android-only). KSP 2.3.9, never kapt. `room-runtime` includes coroutines/Flow — no `room-ktx`.
- **Health Connect client 1.1.0** — first stable; all four mapped record types covered. Three-state runtime availability gating via `getSdkStatus()` (framework module on 14+, installable app on 9–13, impossible on 8.x).
- **kotlinx-serialization 1.11.0** — for the versioned export JSON; plugin version must equal the Kotlin version.
- **`javax.crypto` (platform, zero deps)** for the encrypted export: PBKDF2WithHmacSHA256 ≥600k iterations + AES/GCM/NoPadding, fresh salt+IV per export, self-describing plaintext header — no Tink, no Argon2 native lib. Jetpack `security-crypto` is deprecated; never use it.
- **SDK levels:** compileSdk 36, targetSdk 36 (Play deadline 2026-08-31), minSdk 26.
- **Testing split:** JUnit Jupiter 6.1.1 in pure-JVM `:insights` (parameterized tests preserve Swift case counts); JUnit 4 in `:app` unit tests (AGP-native); androidx.test + Compose ui-test in androidTest.

### Expected Features

WHAT to build is fixed by iOS v1.1 parity; FEATURES.md covers Android-native delivery.

**Must have (table stakes / P1):**
- Health Connect **dual rationale declarations** (Android 13- activity AND 14+ activity-alias) — hard Play requirement; miss one and a whole OS generation gets a dead permission flow.
- HC availability gating + honest "broker may be empty" states + the **30-day history window decision** (request `READ_HEALTH_DATA_HISTORY` or label the window).
- **Edge-to-edge + predictive back built into the Phase 3 shell** — mandatory at targetSdk 36; retrofitting is the expensive path.
- Inexact daily reminder (never `SCHEDULE_EXACT_ALARM` — Play restricts it to alarm-clock apps), just-in-time `POST_NOTIFICATIONS`, one notification channel, reboot rescheduling.
- **Auto Backup exclusion of the Room DB** via backup rules, with an assertion test (lands with Phase 2).
- SAF-based export/import and FileProvider + `ACTION_SEND` PDF share — zero storage permissions.
- **Chart + BodyMap TalkBack semantics** — Compose Canvas is invisible to TalkBack; no Android Audio Graphs equivalent exists, so overlay semantics nodes are real bespoke HIGH-complexity work in Phase 6 (gap days must announce "not logged").
- Truthful Data safety form ("no data collected/shared") + Health apps declaration with per-data-type justifications.

**Should have (post-parity P2):** biometric/PIN app lock (category norm — Clue/Flo ship it; sits outside the consent gate), recents-thumbnail hiding (API 33+, the humane FLAG_SECURE alternative), gentle per-permission import status, "Log today" app shortcut.

**Defer / anti-features:** exact alarms, blanket FLAG_SECURE, background HC sync, Material You dynamic color (would recolor semantically-meaningful Bloom chart tokens), Auto Backup for health data, Google Fit, Drive appDataFolder (locked deferral), widgets/Wear.

### Architecture Approach

ARCHITECTURE.md concretely realizes the locked module layout: single-activity `:app` hosting `BloomTheme { AppGate }` where `AppGate` is a plain three-way `when`-switch on `ConsentState` — **no navigation library** (health composables are unconstructible pre-consent, by construction; Nav3 is the recorded escape hatch if stacked navigation ever appears). `:insights` is `kotlin("jvm")` only, porting the 19 Swift engine files and ~157-case test suite file-for-file, with a boundary lint banning `android.`/`androidx.` imports and two-layer enum/ClaimsPolicy drift tests (Swift-source regex scan + golden JSON).

**Major components:**
1. **`:insights` engines** — verbatim port (TrendsEngine, CyclePhaseMapper, CorrelationEngine, Recommendations, Flare, HonestyGate single choke point, chart data-prep); one public producer function.
2. **`EndoRepository` + Room (10 entities, iOS model names)** — consent-gated facade (throws pre-consent), UUID string PKs, `epochDay: Long` day identity, raw-string enum columns, single `@Transaction` delete-day cascade with a reflection meta-test.
3. **`insightsbridge/SnapshotProjector`** — the ONLY Room↔domain meeting point; pure function, 24-case JVM mapping tests; free text never crosses (hasNote only).
4. **`InsightsCoordinator`** — app-scoped plain class (not ViewModel), fingerprint short-circuit, engine compute in `withContext(Dispatchers.Default)`, cache replace-all, `StateFlow` out.
5. **`health/` HealthConnectImporter** — read-only, changes-token incremental sync, `ImportedSample` provenance rows with unique-indexed HC record id.
6. **`designsystem/` (Bloom port), `report/` (vector PdfDocument), `reminders/`** — reminders physically separated from flare/insights code so the no-flare-notification structural scan stays clean.
7. **CI:** one path-filtered GitHub Actions workflow (`android/**`), JVM job as merge gate, emulator job on main pushes only; iOS-untouched guard.

Critical path within the locked order: 1 → 2 → 3 → 4 → 6 → 7 → 8, with Phase 5 parallelizable against Phase 4.

### Critical Pitfalls

Top items from the 15-pitfall catalog (full phase mapping in PITFALLS.md):

1. **Play health gauntlet is a kickoff item, not a Phase 8 form** — org account + D-U-N-S required for health apps since Jan 2026 (weeks of latency); disclaimer in first paragraph of store description; every HC permission must map to a screencast-able feature. Check the account type at milestone start.
2. **Dual HC rationale declarations** — declare both the 13- rationale activity and the 14+ `VIEW_PERMISSION_USAGE` alias; add a manifest structural test; verify on both OS generations.
3. **30-day HC history window** — silently truncates longitudinal imports; test with >30-day-old seeded records; decide permission-vs-label in Phase 5.
4. **Room v1 discipline is irreversible** — `exportSchema = true` and committed `schemas/` from the first `@Database`, or migration tests are impossible forever; structurally ban `fallbackToDestructiveMigration` (it wipes user health history).
5. **Numeric divergence beyond case-count parity** — Swift and Kotlin disagree on NaN ordering/equality, ±0.0, rounding ties, primitive sort stability, and locale formatting exactly where ported fixtures are thin. Add a divergence fixture pack in Phase 1; make NaN unrepresentable at the `DaySnapshot` boundary; ban `String.format` without a Locale.
6. **Export crypto/SAF traps** — fresh salt+nonce per export, self-describing header, `openOutputStream(uri, "wt")` truncation mode, read-back decrypt verification before the success message, committed golden fixture.
7. **Chart recomposition storms** — unstable `List` params + scrub state read in composition look like "Canvas is too slow" and would trigger the Vico escape hatch prematurely; use `@Immutable` holders, draw-phase-only scrub reads, `drawWithCache`, and Layout Inspector evidence before any renderer switch.

## Implications for Roadmap

**The 8-phase order is locked (ANDROID-PORT-PLAN.md §4) — do not reorder.** Research adds intra-phase content, enforcement tests, and one scheduling insertion.

### Phase 1: `:insights` engine port
**Rationale:** Zero platform dependencies; the ported test suite is the correctness anchor for everything downstream.
**Delivers:** Gradle skeleton + catalog, all engines/enums/value types, ~157-case parity suite, boundary lint, enum drift tests (Swift scan + golden JSON), `PARITY.md` ledger.
**Research adds:** cross-platform **divergence fixture pack** (NaN/±0.0/.5 ties/tied ranks — Pitfall 10); `LocalDate`-only day identity with DST test vectors run in non-UTC zones (Pitfall 11); monorepo hygiene lands here too — path-filtered CI, android-scoped `.gitignore`, ClaimsPolicy single-source-or-cross-check (Pitfall 15).
**Avoids:** silent engine divergence; monorepo/CI drift.

### Phase 2: Persistence
**Rationale:** All downstream phases consume the repository; discipline set here is irreversible.
**Delivers:** 10 Room entities, consent-gated repository, DataStore ConsentStore, single-`@Transaction` delete-day cascade with reflection meta-test, migration scaffold (`exportSchema`, committed `schemas/`, `MigrationTestHelper`), backup-rules DB exclusion + test, `insightsbridge/SnapshotProjector` + 24-case JVM mapping tests, ClaimsPolicy port + CopyAudit harness (before any UI copy exists).
**Research adds:** the **export format definition** (schema-versioned JSON, crypto header spec, UUID identity) belongs here even if implementation lands later; destructive-migration ban test.
**Avoids:** Pitfalls 8, 9; orphaned-row GDPR bug class from iOS.

### Phase 3: UI shell + consent
**Rationale:** Consent-by-construction must exist before any health screen; edge-to-edge/predictive back are cheap now, expensive later.
**Delivers:** Bloom designsystem, `AppGate` when-switch, 3-tab M3 `NavigationBar` shell, onboarding/consent, withdraw re-gate test, deep-link-lands-on-gate test.
**Research adds:** edge-to-edge + predictive back from day one (targetSdk 36 mandates); **no-`INTERNET` manifest test** born here (Pitfall 4's strongest audit artifact).

### Phase 4: Tracking screens
**Delivers:** Check-In sheet, Day Log + BodyMap (with per-region TalkBack semantics, 48dp targets), medications/settings, reminders.
**Research adds:** inexact scheduling only, just-in-time POST_NOTIFICATIONS, one channel, `BOOT_COMPLETED` rescheduling; reminders package physically separate from flare code.

### Phase 5: Health Connect import (parallelizable with late Phase 4)
**Delivers:** three-state availability gating, dual rationale declarations + manifest test, read-only client for 4 types, changes-token incremental sync, `ImportedSample` provenance with unique-indexed `hcRecordId`, re-import idempotency + deletion-propagation tests.
**Research adds decisions due this phase:** the **30-day-history permission-or-label decision**; per-session `getGrantedPermissions()` (never cache grants); draft the Play declaration answers + store description NOW while rationale copy is written; instant→LocalDate bucketing rule with travel/DST vectors.

### Phase 6: Explore + charts
**Delivers:** Canvas chart layer with honesty rules pinned on the (Phase 1) data-prep layer, scrub, lanes, scatter, insights dashboard.
**Research adds:** `@Immutable` chart inputs, draw-phase scrub state, `drawWithCache` paths; **TalkBack overlay semantics per data point** (HIGH complexity — the biggest a11y work item in the port, pinned by a semantics-tree test); the Canvas-vs-Vico escape hatch is evaluated mid-phase with Layout Inspector evidence only.

### Phase 6.5 (INSERT): Encrypted export/import slice
**Rationale:** The research's one scheduling gap — export/import is v1 scope (GDPR Art. 20 portability + device migration + backup) but has no home among the 8 phases. Placing it after Phase 6 / before Phase 8 keeps it from becoming an untested afterthought; it can also ride with Phase 4 settings if the roadmapper prefers — either way, place it explicitly.
**Delivers:** PBKDF2+AES-GCM implementation per the STACK.md crypto spec, SAF write with `"wt"` mode, read-back verification, wrong-passphrase vs corrupt-file UX, golden-fixture format contract test, two-exports-differ test.
**Avoids:** Pitfalls 12, 13.

### Phase 7: PDF report
**Delivers:** vector `PdfDocument` report reusing the Canvas chart drawing code, per-source labels from `dataOrigin`, FileProvider share + SAF save, cache cleanup, copy audit on report strings.

### Phase 8: Play Store prep
**Delivers:** Health apps declaration, Data safety form (no data collected/shared — corroborated by no-INTERNET manifest), privacy policy URL identical across Console/rationale/website, disclaimer in first store-description paragraph (allow-listed in ClaimsPolicy), pre-launch report on Android 13 AND 14+ devices.
**Kickoff dependency:** the **organization-account + D-U-N-S check happens at milestone start**, not here.

### Phase Ordering Rationale

- Insights-first is load-bearing: core value, zero platform deps, ported tests anchor every later phase (locked plan rationale — carried).
- Persistence before UI because consent gating and cascade discipline must be born complete; shell before screens because Bloom tokens + insets + gate tests are cross-cutting.
- Phase 5 has no file overlap with Phase 4 beyond a settings entry point — genuine parallelization opportunity.
- The export slice insertion prevents crypto/SAF work from landing untested in the ship phase.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 5:** verify the exact HC history-window semantics and `READ_HEALTH_DATA_HISTORY` behavior against current docs (flagged MEDIUM in FEATURES.md); extract the iOS instant→day bucketing rule precisely from the Swift source.
- **Phase 6:** Canvas chart layer is Confidence: MEDIUM by locked decision — the one estimate that could be wrong; budget the mid-phase Vico evaluation.
- **Phase 8:** re-verify Jan-2026 Play health policy details (org-account rule, disclaimer wording) against Play Console at submission time — the secondary source is LOW-MEDIUM.

Phases with standard patterns (skip research-phase):
- **Phases 1–4, 7:** first-party docs + the shipped iOS code are complete references; ARCHITECTURE.md already provides file-level realization.
- **Export slice:** the crypto spec in STACK.md is complete and zero-dependency; implementation is mechanical.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Every load-bearing pin verified against official docs within a day of writing; Kotlin 2.4/AGP interplay and week-old Room 3 deliberately avoided rather than risked |
| Features | MEDIUM | Play/HC policy items verified against official pages; HC history window and recents-thumbnail behavior flagged for implementation-time verification |
| Architecture | HIGH | Grounded in the shipped iOS codebase (read directly) + locked plan; patterns are concrete realizations, not speculation |
| Pitfalls | MEDIUM | Official-doc cross-checked for Play/HC/Room/crypto; numeric-divergence findings are language-spec-level (HIGH); Jan-2026 policy specifics rest partly on a secondary source |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Export/import phase placement** — the roadmap must give it an explicit home (recommended: format in Phase 2, implementation slice before Phase 8). Do not let it default into Phase 8.
- **Play org-account status** — unknown; verify at kickoff. If the account is individual, recovery is weeks (D-U-N-S + transfer) and gates the whole milestone's ship date.
- **HC 30-day window exact semantics** — verify current docs in Phase 5 before the permission decision.
- **iOS instant→day bucketing rule for imported spans** — must be extracted from the Swift source and written down with shared test vectors (Phase 5) so both platforms bucket identically.
- **Room 2.8.x vs 3.0** — re-check at Phase 2 kickoff; if Room 3 has patch releases by then, upgrading the default is reasonable (record the choice in the phase plan).

## Sources

### Primary (HIGH confidence)
- Shipped iOS codebase (port source, read directly): `Sources/EndoApp/…`, `EndoInsights/` (19 source + 18 test files, ~157 cases), `Tests/EndoAppTests`
- `.planning/research/ANDROID-PORT-PLAN.md` — the locked decisions all research serves
- Language-spec-level numeric semantics (JLS / Kotlin / Swift specs)

### Secondary (MEDIUM confidence)
- developer.android.com — AGP/Room/DataStore/Health Connect/Compose BOM release pages, HC get-started/read-data/rate-limiting, Room migration docs, Android 16 behavior changes
- kotlinlang.org / blog.jetbrains.com — Kotlin 2.3.21/2.4.0, KSP versioning
- Play Console Help — Health Content and Services, Data safety, Android Health Permissions FAQ
- OWASP password-storage guidance; cross-platform AES-GCM/PBKDF2 references
- docs.junit.org (Jupiter 6.1.1); Google IssueTracker (SAF overwrite bug); Compose performance write-ups cross-checked with official docs

### Tertiary (LOW confidence — validate before relying)
- myappmonitor.com Jan-2026 Play health-policy recap (org-account deadline, disclaimer placement) — cross-check in Play Console before Phase 8
- Practitioner monorepo-CI write-ups; FLAG_SECURE "debate" framing

---
*Research completed: 2026-07-08*
*Ready for roadmap: yes*
