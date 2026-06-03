<!-- GSD:project-start source:PROJECT.md -->

## Project

**Endo-App**

A free, privacy-first **iOS app for people with endometriosis** to track their symptoms, menstrual cycle, nutrition, and physical activity — and turn that data into genuinely useful insights. It is inspired by the official German **Endo-App** (a prescription-only DiGA) but is **free, open to everyone worldwide, and goes deeper on data insights**.

**Core Value:** Turn everyday self-tracking into **understanding** — surfacing correlations, cycle/trend patterns, flare predictions, and personalized recommendations that help people with endometriosis make sense of their condition. If everything else fails, the insights must make the effort of logging feel worth it.

### Constraints

- **Tech stack**: Native iOS, **SwiftUI** — modern Apple-native UI plus first-class access to HealthKit, Swift Charts, and CloudKit.
- **Data/Sync**: On-device storage with **private iCloud (CloudKit) sync**; no custom backend, no user accounts.
- **Budget**: Free app, no monetization in v1 — architecture must avoid recurring server costs.
- **Privacy/Compliance**: Health data stays private (on device + user's private iCloud); GDPR special-category-data handling; clear "not medical advice" framing throughout.
- **Positioning**: Wellness / self-tracking tool, **not** a regulated medical device — no diagnosis claims, informational insights only.
- **Audience**: Global, general-public endometriosis patients — not gated by prescription, region, or clinician.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## TL;DR Decisions

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Swift** | 6.x (Xcode 26 toolchain) | Language | Current; strict concurrency available. Use Swift 6 language mode pragmatically — opt into it per-target once concurrency warnings are tractable. |
| **SwiftUI** | iOS 18+ | UI layer | Mandated by project; first-class bindings to HealthKit/Charts/SwiftData; `@Observable`/`@Query` reduce boilerplate. |
| **SwiftData** | iOS 18+ | On-device persistence + private CloudKit sync | First-party, SwiftUI-native (`@Model`, `@Query`). Built-in iCloud **private-database** sync with near-zero config — exactly this app's need (one user, own devices, no sharing). Matured notably in the 2025 cycle (perf improvements back-ported; model inheritance added). The standard greenfield choice for a single-user synced tracker. **Confidence: HIGH** for the private-sync use case. |
| **CloudKit** (private database) | current | Cross-device sync transport | Apple-hosted, free within generous user-iCloud quotas, **no backend to run or pay for**, no accounts (uses the user's existing iCloud identity). Data lives in the user's *private* database — Apple cannot read it; aligns with GDPR special-category privacy-by-design. Drives the sync layer beneath SwiftData. **Confidence: HIGH** |
| **HealthKit** | iOS 18+ (some APIs iOS 26+) | Import cycle, sleep, workouts, nutrition | The canonical, privacy-gated source for the exact data you want to avoid double-logging. Read-only auth keeps scope minimal. Note iOS 26 brought iPhone-side workout APIs that were previously Watch-only. **Confidence: HIGH** |
| **Swift Charts** | iOS 18+ (3D/`Chart3D` iOS 26+) | Trend, cycle-phase, correlation visualizations | First-party declarative charting that composes directly with SwiftUI; `LineMark`/`BarMark`/`PointMark`/`AreaMark`/`RuleMark` cover every visualization this app needs (trend lines, cycle-phase bands via `RuleMark`/`RectangleMark`, scatter for correlations). No dependency, no maintenance risk. **Confidence: HIGH** |
| **PDFKit** + **Core Graphics** (`UIGraphicsPDFRenderer`) | iOS 18+ | Doctor-ready PDF report | First-party. CG-based rendering yields crisp, vector, text-selectable, small-file PDFs — what clinicians expect — vs. raster-image PDFs. PDFKit also handles preview/share/`PDFView`. **Confidence: HIGH** |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Accelerate** (`vDSP`, `simd`) | system | Fast vectorized stats: means, variance, correlation, rolling windows over symptom/cycle series | Use for the insights engine number-crunching once datasets grow; avoids hand-loops and keeps it on-device & fast. **Confidence: HIGH** |
| **Core ML** | iOS 18+ | On-device inference for flare prediction / pattern models, *if* a statistical model is outgrown | Add in a later phase only. Runs on Neural Engine, data never leaves device. Pair with a **Create ML** training step (offline or on-device personalization). **Confidence: MEDIUM** (warranted, not yet needed) |
| **Create ML** | Xcode tool / framework | Train the baseline flare/pattern model from de-identified or on-device data | Optional, later. Tabular classifiers/regressors fit symptom-trigger prediction well. **Confidence: MEDIUM** |
| **TPPDF** | latest (SPM) | Higher-level PDF document builder (auto-pagination, tables, headers/footers) | Only if PDFKit+CG layout math becomes a meaningful time sink for multi-page reports. Adds a dependency — defer until justified. **Confidence: MEDIUM** |
| **WidgetKit** | iOS 18+ | Optional home-screen "log today" / next-flare widget | Differentiator, post-MVP. **Confidence: HIGH** (capability), LOW (need) |
| **ImageRenderer** | iOS 18+ | Render a SwiftUI chart view into a PDF page / image for the report | Use to embed live SwiftUI charts in the PDF; prefer its `CGContext`/PDF path over raster snapshots for vector quality. **Confidence: HIGH** |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Xcode 26** | IDE / build / SwiftData & CloudKit consoles | Required: App Store submissions must build with the current SDK (Apple enforced iOS 18 SDK from April 2025 onward). |
| **Swift Testing** | Unit/logic tests (insights math, cycle logic, parsers) | Apple's recommended default for new test code; macros (`@Test`, `#expect`), parameterized tests, parallel by default. Ships in Xcode 16+. |
| **XCUITest** (part of XCTest) | The handful of end-to-end UI flows | Still the *only* native UI-automation option; coexists with Swift Testing in separate targets. |
| **CloudKit Dashboard / Console** | Inspect synced records, deploy schema to Production | You MUST deploy the schema from Development → Production before App Store release, or sync silently fails for real users. |
| **Swift Package Manager** | Dependency management | Prefer SPM over CocoaPods/Carthage. Keep dependencies near-zero (privacy + maintenance). |
| **Xcode Cloud** *(or GitHub Actions w/ macOS runners)* | CI for an indie | Xcode Cloud: easiest, free tier for low volume, native signing. GitHub Actions: cheaper at scale, more setup. Either is fine; start with Xcode Cloud. |

## Installation

# Xcode → Signing & Capabilities, add:

#   - HealthKit                (entitlement; read-only data types)

#   - iCloud → CloudKit        (entitlement; one CloudKit container)

#   - Background Modes → Remote notifications  (for CloudKit sync push)

# Info.plist usage-description strings (REQUIRED or the app crashes on access):

#   - NSHealthShareUsageDescription      (HealthKit read)

#   - NSHealthUpdateUsageDescription     (only if you ever write back to Health)

# In code:

# Only if you adopt the optional PDF builder:

# Xcode → Add Package Dependencies → https://github.com/techprimate/TPPDF

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **SwiftData (private CloudKit)** | **Core Data + `NSPersistentCloudKitContainer`** | If you hit a SwiftData wall: you need **public or shared** CloudKit databases, complex migrations, `@Attribute(.unique)` enforcement, or fine-grained sync control. Core Data + NSPersistentCloudKitContainer is more mature and battle-tested. For this single-user private-sync app, you likely won't need it. |
| **SwiftData (private CloudKit)** | **SQLiteData (Point-Free, GRDB-backed)** with CloudKit | If you want raw SQL power, value-type models, and explicit control, and are comfortable with a third-party persistence layer. Strong library, but adds a dependency and diverges from the all-first-party privacy story. Not recommended for v1. |
| **Built-in CloudKit sync (via SwiftData)** | **`CKSyncEngine`** (manual CloudKit) | If you outgrow automatic sync and need custom conflict resolution, partial sync, or to sync non-SwiftData data. Significantly more code. Defer until proven necessary. |
| **PDFKit + Core Graphics** | **TPPDF** | When multi-page tabular reports with auto-pagination would otherwise cost days of CG layout math. |
| **Plain Swift + Accelerate stats** | **Core ML / Create ML** | When descriptive stats + correlation + threshold/logistic flare model demonstrably underperform and you have enough per-user history to train a model. |
| **Swift Testing** | **XCTest** | UI automation (XCUITest), performance tests (`XCTMetric`), or any Objective-C interop. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Any third-party backend** (Firebase, Supabase, custom server) | Recurring cost, an account system, and — critically — moves GDPR Art. 9 special-category health data off-device to a controller you'd have to secure and document. Directly contradicts the privacy-first, free-to-run mandate. | CloudKit private database (data stays in the user's iCloud; you never see it). |
| **User accounts / auth (Sign in with Apple as a login, OAuth, etc.)** | The whole architecture is "no login." Accounts create liability and friction for sensitive data. | The user's existing iCloud identity, used implicitly by CloudKit. |
| **Third-party analytics / crash SDKs that capture content** (Firebase Analytics, Amplitude, Mixpanel, etc.) | Risk of exfiltrating health data or behavioral signals about a stigmatized condition; GDPR consent nightmare. | Apple's privacy-preserving **MetricKit** + opt-in **App Store / Xcode Organizer** crash reports only. No event analytics in v1. |
| **Cloud/LLM inference for insights** (server ML, hosted APIs) | Sends symptom data off-device; defeats privacy-first positioning; adds cost. | On-device stats first, then Core ML on the Neural Engine. |
| **Third-party charting libraries** (Charts/DGCharts, etc.) | Unnecessary dependency and maintenance; Swift Charts covers all needs natively. | Swift Charts. |
| **Raster-image PDFs** (snapshotting views to UIImage → PDFPage) | Large files, blurry on print/zoom, no selectable text — unprofessional for a clinician. | CG/`UIGraphicsPDFRenderer` vector text + `ImageRenderer`'s vector PDF path for charts. |
| **`@Attribute(.unique)` and non-optional model properties** (when CloudKit-synced) | CloudKit rejects unique constraints and requires every property to be optional or defaulted; sync will break. | Optional/defaulted properties; enforce uniqueness in app logic (e.g., upsert by a UUID you generate). |
| **Diagnostic/medical-device framing in copy or model** | Triggers DiGA/medical-device regulation; out of scope. | "Informational insights, not medical advice" framing; no diagnosis output. |

## Stack Patterns by Variant

- Set min target to **iOS 17** (~92% reach) instead of iOS 18 (~88%).
- Because: ~4% more devices. Cost: a larger test matrix and missing the most-matured SwiftData/Charts/HealthKit APIs from the iOS 18/26 cycle. Not worth it for a v1 with a small team — recommend iOS 18.
- Add a **Core ML tabular classifier/regressor**, trained via **Create ML**, for flare prediction; keep inference on-device.
- Because: more nuanced multivariate patterns than hand-tuned thresholds, while preserving the privacy boundary.
- Adopt **TPPDF** for auto-pagination/tables; keep chart pages rendered via `ImageRenderer`.
- Because: avoids manual Core Graphics pagination math.
- Migrate sync from SwiftData's built-in path to **Core Data + NSPersistentCloudKitContainer** (shared DB) or **`CKSyncEngine`**.
- Because: SwiftData currently supports only the **private** database, not shared/public. (Confirm against current docs at that time — Apple may close this gap.)

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| SwiftData (iOS 18+) | CloudKit **private** DB only | Shared/public DBs unsupported as of the iOS 26 cycle. Models must be all-optional/defaulted, no `.unique`, relationships optional with inverses. |
| SwiftData + CloudKit | Background Modes → Remote notifications | Required entitlement for sync to receive push; without it sync is sluggish/manual. |
| HealthKit | **Physical device** for realistic testing | Simulator HealthKit is limited; menstrual/sleep/workout flows need a real device (and Health app sample data). |
| CloudKit | **Schema deployed to Production** | Dev schema must be promoted in CloudKit Console before release or real-user sync fails silently. |
| Swift Testing ⟷ XCTest | Coexist, **not interoperable** | `#expect` and `XCTFail` don't bubble across frameworks; keep them in separate targets/files. |
| Xcode 26 / iOS 18 SDK | App Store submission | Apple mandates building against the current SDK; min deployment target can still be lower. |

## Architecture Constraints These Frameworks Impose

- **Entitlements & capabilities:** HealthKit + iCloud(CloudKit) + Background Modes(remote notifications) must all be enabled; each requires a paid Apple Developer account and provisioning. HealthKit apps need a privacy policy and Info.plist usage strings or they crash on first access.
- **CloudKit container is global per app:** one private DB per user identity; no server-side joins or queries you control — model your data so the *device* does the analysis.
- **Sync is eventually-consistent:** design the insights engine to tolerate partial/late-arriving data; don't assume all devices are in sync at compute time. Generate stable UUIDs app-side for idempotent upserts (since `.unique` is unavailable).
- **HealthKit is read-gated per type:** request the *minimum* set (menstrual flow, sleep analysis, workouts, dietary types you use). Authorization is per-type and the app cannot tell if the user *denied* read access (Apple hides this for privacy) — handle "no data" and "denied" identically.
- **On-device compute budget:** stats over a single user's history is trivial; Core ML inference runs on the Neural Engine cheaply. No scaling concern — but keep heavy computation off the main thread (background `ModelContext` / actor).
- **Simulator gaps:** HealthKit and CloudKit both behave differently/limited in Simulator — budget for real-device testing and at least two devices to validate sync.

## Sources

- `/websites/developer_apple_swiftdata` (Context7) — SwiftData framework reference, CloudKit configuration — HIGH
- `/pointfreeco/sqlite-data` (Context7) — SQLiteData as a SwiftData alternative w/ CloudKit — MEDIUM
- developer.apple.com/documentation/{swiftdata, cloudkit, healthkit, charts, pdfkit} — framework capabilities — HIGH
- fatbobman.com — "Designing Models for CloudKit Sync: rules" / "Swift 2025 maturity" — CloudKit model constraints, SwiftData maturity — MEDIUM (independent expert, corroborated by Apple docs)
- distantjob.com, swiftprogramming.com, mjtsai.com (WWDC25 recap) — SwiftData vs Core Data 2025 state, private-only sync — MEDIUM
- dev.to (WWDC25 Swift Charts 3D) — Chart3D / iOS 26 charting — MEDIUM
- developer.apple.com/forums + Apple "Configuring HealthKit access" — entitlements, simulator limits — HIGH/MEDIUM
- ioscompatibility.com, telemetrydeck.com, macrumors — iOS 17 vs 18 adoption (~88–92%) — MEDIUM
- developer.apple.com (Swift Testing) + multiple 2025 comparisons — Swift Testing as default for new code — HIGH

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
