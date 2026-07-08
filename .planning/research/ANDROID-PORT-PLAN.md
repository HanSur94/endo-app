# Android Port Plan — Endo-App

**Written:** 2026-07-08
**Status:** Decision-complete — ready for `/gsd-new-milestone` consumption

This document is the decision-complete Android port plan for the Endo-App. It exists so that a future `/gsd-new-milestone` run can scope an Android milestone directly from pre-made decisions — stack, sync strategy, repository/module layout, port order, and invariant carry-over — instead of re-opening architectural questions. Every recommendation below is written as a **decision** (Choice + Rationale + Alternatives considered), not a menu of options. Everything here is informational planning for a wellness / self-tracking app; nothing in this plan changes the project's positioning — the Android port is **not** a medical device and makes no diagnostic claims.

> **Source of truth:** The decisions in this document were derived from the shipped iOS v1.1 codebase inventory (185 Swift files; SwiftUI + SwiftData SchemaV1–V6 + private CloudKit + HealthKit + Swift Charts; the pure-Swift `EndoInsights` package with ~3,570 LOC across 19 source files and ~20 test files) and the binding project constraints in `PROJECT.md` / `CLAUDE.md` (privacy-first, no custom backend, no accounts, no third-party analytics, free to run). Where a decision mirrors an iOS lesson learned in production, the lesson is cited inline.

---

## 1. Stack Decisions

The guiding principle mirrors the iOS stack: **all-first-party (or first-party-adjacent) with near-zero dependencies**, because every dependency is a privacy audit surface and a maintenance liability for a free app handling GDPR special-category health data.

| Decision | Choice | Rationale | Alternatives considered |
|----------|--------|-----------|-------------------------|
| **Language** | Kotlin (current stable 2.x), coroutines + Flow for async | The Android analog of the all-first-party Swift/SwiftUI philosophy; Google's sole recommended language; coroutines/Flow are the platform-native async idiom (the counterpart of Swift concurrency). | Java (rejected — legacy, no benefit for greenfield); Kotlin Multiplatform sharing with iOS (rejected for v1 — the iOS app is shipped and pure Swift; retrofitting KMP would destabilize a working product for speculative sharing). |
| **UI** | Jetpack Compose + Material 3 base, themed by a ported **Bloom** design system | Compose is the declarative first-party UI toolkit — the direct counterpart of SwiftUI. Material 3 provides the component base; the Bloom token set (canvas / surface / card / ink / rose / plum / gold / petal / teal / indigo), the Bloom typography scale, and the component styles port into a `designsystem` package so both platforms read as the same product. | XML Views (rejected — legacy, no benefit for greenfield); third-party design systems (rejected — Bloom already exists and is the brand). |
| **Persistence** | Room (androidx.room) with entities mirroring the 10 SwiftData models by name: DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, Medication, TreatmentState, ImportedSample, CachedInsight, DismissedInsight | First-party-adjacent (androidx), compile-time-checked SQL, first-class coroutines/Flow support, robust migration story. **Freedom gained vs. SwiftData+CloudKit:** Room permits non-null columns and unique indices (CloudKit forbade both). **BUT:** keep app-generated UUID identity columns anyway — the export format stays stable and a record's identity survives export/import round-trips (see Section 2). | SQLDelight (fine, but Room is the first-party-adjacent default and the team has no SQL-DSL preference to justify the divergence); Jetpack DataStore (prefs only — used for the consent flag and reminder settings, mirroring the iOS split where consent lives in UserDefaults OUTSIDE the synced store). |
| **Health integration** | Health Connect (androidx.health.connect client), **READ-ONLY** | The Android counterpart of HealthKit and the only sanctioned health-data broker going forward. Read-only keeps the permission surface minimal and preserves the iOS posture. Record-type mapping from the four iOS HealthKit reads: menstrualFlow → `MenstruationFlowRecord` (+ `MenstruationPeriodRecord`), sleepAnalysis → `SleepSessionRecord`, workouts → `ExerciseSessionRecord`, dietaryEnergyConsumed → `NutritionRecord` (energy field). | Direct integrations with Google Fit APIs (rejected — deprecated in favor of Health Connect); per-vendor SDKs (rejected — dependency and privacy surface). |
| **Charts** | Custom Compose **Canvas** chart layer (owned code) — **Confidence: MEDIUM** | There is no first-party Compose equivalent of Swift Charts. The app's locked honesty rendering rules — gap days rendered as true gaps never interpolated, absence ≠ 0, aligned per-scale lanes never dual y-axis, count-encoded scatter with no fitted line/r-value — are bespoke logic that dominates the work regardless of library, so a small owned chart layer keeps the zero-dependency privacy/maintenance posture. | **Vico** (capable Compose-native charting; the explicit **escape hatch** if Canvas layout cost balloons — this is the one consciously revisitable call in this section, record it as such); MPAndroidChart (rejected — View-based interop, poor Compose fit). |
| **PDF report** | `android.graphics.pdf.PdfDocument` + Canvas drawing; share via `FileProvider` + `ACTION_SEND` Intent | The counterpart of `UIGraphicsPDFRenderer`: vector text/graphics, crisp on print/zoom, small files — what clinicians expect. Intent-based share is the counterpart of the iOS share sheet: the user chooses the destination, no silent upload. | Third-party PDF builders (rejected for v1 — dependency; revisit only if multi-page layout math becomes a measured time sink, mirroring the iOS TPPDF stance). |
| **SDK levels** | minSdk 26; targetSdk = current Play-required level at build time; compileSdk = latest stable | minSdk 26 is the Health Connect client floor and gives very high device reach. targetSdk must track Play policy anyway (annual requirement); pinning it here would just go stale. | Higher minSdk (rejected — no API need justifies cutting reach); lower minSdk (impossible — below the Health Connect client floor). |
| **DI** | Manual composition root — an `AppDependencies` mirror of the iOS composition root | Matches the near-zero-dependency philosophy; the iOS app proved a hand-wired root scales fine at this app size, and it keeps object graphs greppable. | Hilt (rejected for v1 — annotation-processor weight and framework coupling for no gain at this scale); Koin (rejected — runtime service locator, dependency). |
| **Testing** | JUnit + kotlin.test for the pure `:insights` JVM module (the ported EndoInsights suite runs as plain fast JVM tests); Room instrumentation tests for migrations; Compose UI tests for the consent gate and critical flows | Mirrors the iOS split: Swift Testing for the package, XCUITest for flows. The `:insights` tests being plain JVM tests (no emulator) keeps the correctness anchor fast, exactly like the EndoInsights package tests today. | Robolectric everywhere (rejected as the default — real instrumentation for migrations and real Compose tests for the gate are worth the runtime). |
| **Build** | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) | Current standard; the catalog is the single greppable place where the (deliberately short) dependency list lives — the audit artifact for the zero-dependency posture. | Groovy DSL (rejected — legacy); Bazel/Buck (rejected — massive overkill). |

**Notes on rows that need nuance:**

- **Health Connect availability must be runtime-gated.** Health Connect is built into Android 14+; on Android 13 and below it is a separate installable app. The import feature must detect availability at runtime and degrade gracefully (manual logging is always the primary path — Health Connect is additive, exactly as HealthKit is on iOS).
- **Health Connect exposes per-permission grant state — unlike HealthKit.** On iOS, the app cannot distinguish "denied" from "no data" and treats them identically. On Android the UI *may* know "denied", but the product decision is to keep the same graceful-denial UX as iOS: render denial gently, never nag, never block manual logging. The extra information is used at most to avoid pointless re-reads, never to pressure the user.
- **Room's relaxed constraints do not relax identity discipline.** The iOS models carry app-generated UUIDs because CloudKit forbade `.unique`; Android keeps app-generated UUIDs because the encrypted export format (Section 2) needs stable, platform-neutral record identity. Same column, different (better) reason — do not drop it.
- **Charts confidence tag.** Canvas-vs-Vico is tagged **Confidence: MEDIUM** deliberately: the honesty-rule logic is owned code either way, but the raw layout/measure cost of Canvas is the one estimate that could be wrong. Vico is the named escape hatch; switching to it later changes rendering plumbing, not the honesty rules.

**What NOT to use** (mirrors the iOS stack doc's spirit):

- **No Firebase or any backend SDK** — recurring cost, account coupling, and it moves GDPR Art. 9 special-category health data to a controller we'd have to secure and document. Directly contradicts the privacy-first, free-to-run mandate.
- **No analytics SDKs** (Firebase Analytics, Amplitude, Mixpanel, …) — risk of exfiltrating behavioral signals about a stigmatized condition. Play vitals / Play Console crash reporting only (the Android counterpart of the MetricKit-only iOS stance).
- **No auth / accounts SDKs** (Google Sign-In, Credential Manager as a login, OAuth) — the architecture is "no login," on both platforms.
- **No third-party charting by default** — Vico is a recorded escape hatch, not a starting dependency.
- **No cloud/LLM inference for insights** — all insight computation stays on-device, exactly like iOS.

---

## 2. Sync & Backup Strategy

This is the **biggest architectural divergence from iOS.** The iOS app syncs through the user's private CloudKit database (`iCloud.com.endoapp.ios`) — Apple-hosted, free, no accounts, unreadable by us. **CloudKit has no Android client for private databases**, and the project constraint forbidding a custom backend is hard (cost, GDPR controller liability, accounts). So the Android app cannot have the iOS sync story, and no bridge is permitted.

### Decision (v1): local-only storage + user-initiated encrypted export/import

- **Storage is local-only.** The Room database lives on-device. No background sync of any kind in v1.
- **Export** is a **versioned, platform-neutral JSON document** of all user data, encrypted with a key derived from a **user-chosen passphrase** (AES-GCM; key derivation via a standard, well-reviewed KDF), written through the **Storage Access Framework** — the user picks the destination (local file, their own Drive, wherever), and the app needs **no storage permissions**.
- **Import** is the inverse: SAF file pick → passphrase → decrypt → validate schema version → idempotent upsert by the app-generated UUIDs (this is why Section 1 keeps UUID identity columns).
- **This one mechanism answers three questions at once:** GDPR Art. 20 data portability, device migration (old phone → new phone), and user-controlled backup. One deliberate consent surface, no opaque background copies of health data.
- **Platform-neutral by design (a note, not a commitment):** the export JSON schema should be defined without Android-isms (ISO-8601 dates, enum raw values matching the shared domain vocabulary, explicit schema version field) so a future iOS exporter/importer **could** interoperate. No iOS work is being scheduled or promised here — the schema is simply written so that door stays open.

### Alternatives evaluated

| Alternative | Assessment | Verdict |
|-------------|-----------|---------|
| **Android Auto Backup** | Zero code; encrypted with the device lockscreen secret on Android 9+. But: opaque to the user, size-capped, tied to the user's Google account, and **not a deliberate consent surface** for special-category health data — the user never explicitly chooses to place their symptom history in a Google-account-tied backup. | Enable **at most for the consent flag / preferences**; **exclude the health database via backup rules** (`dataExtractionRules` / `fullBackupContent`). Do NOT rely on it as the health-data story. |
| **Google Drive `appDataFolder`** | The closest analog to the CloudKit private DB — app-private data living in the user's own Drive quota, invisible to us. But it requires Google Sign-In / OAuth consent flows and Play Services coupling, which collides head-on with the "no accounts, no sign-in surface" posture. **Confidence: MEDIUM** on the deferral — this is the second consciously revisitable call in this doc. | **Defer.** Revisit as a v2 candidate **only if** real users demand multi-device Android sync. Not in the v1 scope. |

### Explicitly Out of Scope: iOS-Android Cross-Device Sync

**Live cross-platform sync is out of scope for the Android port entirely.** The iOS app's data lives in the user's private CloudKit database, which is unreachable from Android (no CloudKit private-DB client exists for Android), and the project's no-backend constraint forbids building any bridge service — a bridge would be a custom backend holding special-category health data, the exact thing the architecture exists to avoid. Anyone scoping the Android milestone must treat "my iPhone and my Android phone show the same data live" as **not a goal**.

The only contemplated cross-platform path is a **one-time data handoff** via the encrypted export format described above — and only if the iOS app later ships a matching exporter/importer, which is not scheduled and not promised by this plan.

---

## 3. Repository & Module Layout

**Decision: the Android app lives in an `android/` directory in THIS repository (monorepo).**

Rationale: shared planning artifacts (`.planning/`), a shared copy-policy vocabulary (the ClaimsPolicy substring lists must not drift between platforms), and one issue tracker. The iOS side is untouched — the XcodeGen `project.yml` and all Swift sources stay exactly where they are; Gradle and Xcode never see each other's files.

```
endo-app/
├── project.yml                      # iOS XcodeGen — untouched
├── EndoApp/ …                       # iOS sources — untouched
├── EndoInsights/ …                  # iOS insights package — untouched (the port SOURCE)
└── android/
    ├── settings.gradle.kts          # includes :app, :insights
    ├── gradle/
    │   └── libs.versions.toml       # version catalog — the audited dependency list
    ├── insights/                    # module :insights — pure Kotlin JVM
    │   └── src/
    │       ├── main/kotlin/         # domain values, enums, engines
    │       └── test/kotlin/         # ported EndoInsights test suite + boundary lint
    └── app/                         # module :app — the Compose application
        └── src/
            ├── main/kotlin/
            │   ├── data/            # Room entities + repository + consent store
            │   ├── health/          # Health Connect import
            │   ├── insightsbridge/  # Room rows -> :insights DaySnapshot mapping
            │   ├── ui/              # screens by feature
            │   ├── designsystem/    # Bloom port (colors, typography, components)
            │   └── report/          # PDF
            ├── test/                # JVM tests (copy audit, structural scans)
            └── androidTest/         # Room migrations, Compose UI tests
```

### Module `:insights` — pure Kotlin JVM

The mirror of the `EndoInsights` Swift package, and the reason the port order in Section 4 starts here.

- **Plugin:** `kotlin("jvm")` only — **NO Android plugin**. This module never touches an emulator.
- **Contents:** the domain value types (`DaySnapshot` etc.), the domain enums (SymptomType — 11 cases, FactorKind, FoodTag, ActivityType, ActivityIntensity, SleepQuality, FlowIntensity, BodyRegion — 10 cases), and the engines: TrendsEngine, CyclePhaseMapper, CorrelationEngine (BH-FDR, Fisher exact, Mann-Whitney U, Cliff's delta), RecommendationsEngine, FlareEngine, HonestyGate, ForbiddenTerms.
- **Boundary rule: Kotlin stdlib only.** Enforced by a lint test mirroring the iOS `BoundaryLintTests` — a source scan over the module asserting no `android.` / `androidx.` imports appear anywhere in `:insights`. The boundary is a test, not a convention.

### Module `:app` — the Compose application

Package sketch:

- `data/` — Room entities + repository + consent store (consent flag in DataStore, outside the health database, mirroring the iOS UserDefaults split).
- `health/` — Health Connect import (read-only client, provenance rows).
- `insightsbridge/` — maps Room rows → `:insights` `DaySnapshot` values at the boundary, mirroring the iOS `daySnapshots()` seam. This is the ONLY place platform persistence types and pure domain types meet.
- `ui/` — screens grouped by feature (onboarding/consent, home/log, insights, explore, settings).
- `designsystem/` — the Bloom port: color tokens, typography scale, component styles, tab bar chrome.
- `report/` — PDF generation and share.

### Dependency rule

**`:app` depends on `:insights` — never the reverse.** `:insights` knows nothing about Android, Room, Compose, or Health Connect. This is the same one-way arrow that made the iOS EndoInsights package portable enough to be the anchor of this whole plan.
