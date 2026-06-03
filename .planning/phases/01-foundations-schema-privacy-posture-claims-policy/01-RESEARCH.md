# Phase 1: Foundations — Schema, Privacy Posture & Claims Policy - Research

**Researched:** 2026-06-03
**Domain:** Greenfield native iOS 18 SwiftUI app scaffold + CloudKit-safe SwiftData schema + GDPR consent gate + privacy/claims/terminology posture (walking skeleton)
**Confidence:** HIGH (core Apple framework APIs verified against official Apple docs this session; App Store policy text quoted verbatim from Apple; the 5.1.3(ii) *interpretation* remains reviewer-dependent and is flagged MEDIUM)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

The user delegated all four discussion areas ("no preference"); the decisions below are research-backed defaults, **locked for planning**. The planner MUST honor every one.

**Onboarding & Consent**
- **D-01:** First-run is warm, plain-language, short, validating in tone — never clinical. Any education is optional/skippable.
- **D-02:** A single clear up-front consent to process health data **on-device + in private iCloud** is the baseline gate to use the app. No health data is processed until the user opts in (ONBD-01).
- **D-03:** HealthKit + any future optional processing consent is **just-in-time / purpose-specific** (Phase 9), NOT bundled into the launch consent.
- **D-04:** Consent is **withdrawable in Settings**, with a plain explanation of what withdrawal means for existing data.
- **D-05:** "Not medical advice / not a medical device" framing appears in onboarding AND alongside insights later (ONBD-02).

**Inclusive Language & Terminology**
- **D-06:** Non-gendered, non-assumptive language by default everywhere — the app never assumes the user is a woman / uses "she" (ONBD-03).
- **D-07:** An optional, lightweight terminology + pronoun + display-name preference lives in **Settings**. NOT a forced identity wall at onboarding.
- **D-08:** Default terminology is neutral ("bleeding" / "cycle") until the user customizes it.

**App Identity & App Store De-risk**
- **D-09:** Scaffold with provisional display name **"Endo-App"** and placeholder bundle ID (e.g. `com.endoapp.ios`) — both flagged to finalize at enrollment.
- **D-10:** Build **local-first with CloudKit sync behind a feature flag** — development never blocked on an Apple Developer account or App Review.
- **D-11:** App Store **5.1.3(ii) de-risk** (throwaway TestFlight build + pre-submission review note) is planned but **GATED** on Apple Developer Program enrollment. NOT a blocker for Phase 1.
- **D-12:** **Min deployment target iOS 18.** Scaffold a real `.xcodeproj` via **XcodeGen** (`project.yml`) — never an SPM executable target.

**Privacy Policy & Publisher**
- **D-13:** The independent developer is the data controller; the policy names the developer + a contact email (placeholder: the user's email, `sannahrush@googlemail.com`).
- **D-14:** A plain-language **in-app privacy screen is the source of truth** — works offline, no hosting. States: data lives on-device + in the user's private iCloud, no servers, zero third-party sharing or analytics (ONBD-04).
- **D-15:** A hosted privacy-policy URL (same text) is needed at App Store submission time — flagged as a submission-time dependency, not now.

**Data Schema (locked by research — planner MUST follow)**
- **D-16:** SwiftData with a **`VersionedSchema` from v1**.
- **D-17:** **All attributes optional or defaulted; NO `@Attribute(.unique)`; all relationships optional with inverses; no `.deny` delete rules.**
- **D-18:** Model health data as **append-only, timestamped event records**, not mutable daily aggregates.
- **D-19:** **Bleeding state and treatment/suppression state are FIRST-CLASS and independent of any "cycle."** Cycle-day/phase is a **derived, optional, confidence-scored** value — never assume 28 days.
- **D-20:** Use **app-generated UUIDs** as stable identifiers for idempotent upserts.
- **D-21:** **Private CloudKit database only** (`cloudKitDatabase: .private`). No shared/public DB. HealthKit-derived samples NOT copied into synced storage.
- **D-22:** Establish a **composition root + service protocols** now (Repository, sync, consent) for injectable/testable services later.

### Claude's Discretion
- Exact onboarding screen copy, visual design, and the precise SwiftData field list — gray *within* the locked decisions above.
- Final choice of placeholder bundle ID string and project folder layout — Claude's, provided it follows D-09 and D-12.

### Deferred Ideas (OUT OF SCOPE)
- **HealthKit consent UX** → Phase 9 (just-in-time, per D-03).
- **Hosted privacy-policy URL** → App Store submission time (per D-15).
- **Final app name + bundle ID** → at branding finalization / Apple Developer enrollment (per D-09).
- **Turning on CloudKit sync** (vs local-first default) → Phase 4, behind the feature flag established here (per D-10).
- **External dependency (NOT blocking Phase 1):** Apple Developer Program enrollment (~$99/yr) required for device runs, TestFlight, and therefore the 5.1.3(ii) de-risk (D-11). Until then, development proceeds local-first in the Simulator.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **ONBD-01** | User gives explicit, withdrawable consent before any health data is processed (GDPR Art. 9) | Consent-gate pattern (`AppGate` switching on a persisted `ConsentState`); store flag in App Group `UserDefaults` (not in the synced SwiftData store, so it never round-trips through CloudKit); withdraw action in Settings. See *Architecture Patterns → Pattern 3* and *Validation Architecture*. |
| **ONBD-02** | "Not medical advice / not a medical device" framing in onboarding and alongside insights | A single source-of-truth `Disclaimer` constant + a reusable `DisclaimerBanner` view; surfaced on the consent screen and reserved for insight surfaces later. See *Claims & Language Policy*. |
| **ONBD-03** | Inclusive, non-assumptive language; user can customize identity/terminology | `TerminologyPreference` value type (bleeding-term enum, pronouns, optional display name) persisted in `UserDefaults`; neutral defaults ("bleeding"/"cycle"); resolved through a `Terminology` lookup used by all copy. See *Architecture Patterns → Pattern 4*. |
| **ONBD-04** | In-app privacy policy explaining on-device + private iCloud storage and no third-party sharing | Static, offline, plain-language `PrivacyPolicyView` (the source of truth, D-14) reachable from onboarding + Settings; text mirrors the eventual hosted URL. |
| **DATA-01** | Data persists on-device (SwiftData) using a CloudKit-safe, append-only schema | `VersionedSchema` (`SchemaV1`), CloudKit-safe `@Model` types (all optional/defaulted, no `.unique`, optional inverse relationships), bleeding/treatment first-class + cycle-day derived/optional, app-generated UUID, `ModelConfiguration(cloudKitDatabase:)` switchable between `.none` (local-first default) and `.private(_:)` (flag on). See *Standard Stack* + *Architecture Patterns → Pattern 1 & 2*. |
</phase_requirements>

## Summary

This is the irreversible-foundations phase of a greenfield iOS app, executed as the **thinnest end-to-end walking skeleton**: a launchable iOS 18 SwiftUI app (XcodeGen `.xcodeproj`) that shows a consent/onboarding gate, then — once consent is given — persists at least one record to a local SwiftData store using a CloudKit-safe schema and reads it back in the Simulator. Everything Phase 1 locks (the CloudKit schema shape, the consent gate, the claims/terminology posture) is cheap to bake in correctly now and expensive-to-impossible to fix after launch, which is exactly why it is sequenced first.

The decisive technical facts, all verified this session against Apple's docs: SwiftData's `ModelConfiguration.CloudKitDatabase` exposes `.none`, `.automatic`, and `.private(_:)` (all iOS 17+) [CITED: developer.apple.com/documentation/swiftdata/modelconfiguration/cloudkitdatabase-swift.struct]. This single enum *is* the D-10 feature flag — ship `.none` by default (local-only, runs in the Simulator with no entitlements, no Apple Developer account), flip to `.private("iCloud.<container>")` in Phase 4. Under any CloudKit-syncable config the schema must obey hard rules (all attributes optional/defaulted, no `@Attribute(.unique)`, relationships optional with inverses, no `.deny`/ordered relationships) — these are non-negotiable and append-only after a production push, so the schema is designed to those rules from v1 even while sync is off. The GDPR Art. 9 consent gate is a persisted flag *outside* the synced store that blocks all health-data processing until opt-in and is withdrawable in Settings. The App Store 5.1.3(ii) line ("may not store personal health information in iCloud," quoted verbatim from Apple [CITED: developer.apple.com/app-store/review/guidelines]) has no written CloudKit-private-DB carve-out — its mitigation is the architecture (private DB only, no HealthKit data in synced storage, Privacy Manifest + consent copy) plus a TestFlight review-note de-risk that is correctly gated out of this phase.

**Primary recommendation:** Scaffold via XcodeGen (iOS 18, SwiftUI lifecycle), define a `VersionedSchema` `SchemaV1` with CloudKit-safe `@Model` types where **bleeding state and treatment/suppression state are first-class and cycle-day is derived/optional**, wire a `ModelContainer` with `cloudKitDatabase: .none` behind a `SyncMode` flag, gate the app behind a persisted `ConsentState`, and prove the whole loop with Swift Testing against an **in-memory `ModelContainer`** + an `xcodebuild` Simulator build. Treat sync, HealthKit, and TestFlight as explicitly out of this phase.

> **Environment blocker (must address in the plan):** the active Xcode toolchain on this machine is **Command Line Tools only** — `xcodebuild` errors with *"requires Xcode, but active developer directory '/Library/Developer/CommandLineTools' is a command line tools instance."* Full Xcode must be installed and selected (`sudo xcode-select -s /Applications/Xcode.app`) before any Simulator build or `xcodegen`-driven verification can run. `xcodegen` is also not installed (`brew install xcodegen`). See *Environment Availability*. [VERIFIED: ran `xcodebuild -version` and `brew list xcodegen` this session]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| App lifecycle / composition root | App (SwiftUI `@main`) | — | One place wires `ModelContainer`, services, consent state into the environment (D-22). |
| Consent gate (block processing pre-opt-in) | App / Presentation (`AppGate` view) + persisted flag | Service (`ConsentStore` protocol) | Gating is a UI routing decision driven by a persisted flag; the flag store is a service so it's mockable (ONBD-01). |
| Health-data persistence (CloudKit-safe schema) | Data / Persistence (SwiftData `ModelContainer`) | Service (`Repository` protocol) | The store is the single source of truth; the repository is the testable seam (DATA-01, D-22). |
| Cross-device sync (Phase 4, flag OFF now) | Data / Persistence (`cloudKitDatabase`) | — | A `ModelConfiguration` switch; no app-tier logic. Deferred but the seam is reserved. |
| Privacy policy / disclaimer / terminology | Presentation (static views) | Service (`TerminologyProvider`) | Pure content + a small preference lookup; no backend, offline (ONBD-02/03/04). |
| Terminology/pronoun preference | Service (`UserDefaults`-backed) | Presentation (Settings) | A value type persisted outside the synced store; read by all copy. |

## Standard Stack

This is an **all-first-party Apple stack** — there are **no third-party package installs in Phase 1** (XcodeGen is a build-time dev tool, not an app dependency). This is a deliberate privacy + maintenance decision locked by STACK.md and reinforced by the GDPR/App-Store posture.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SwiftUI | iOS 18+ | UI layer, app lifecycle (`App`/`Scene`) | Mandated; native bindings to SwiftData (`@Query`, `@Environment(\.modelContext)`). [CITED: project constraints] |
| SwiftData | iOS 18+ (API iOS 17+) | On-device persistence + (later) private CloudKit sync | First-party `@Model`/`ModelContainer`; `cloudKitDatabase:` is the local-vs-sync flag. [CITED: developer.apple.com/documentation/swiftdata] |
| Foundation (`UserDefaults`) | system | Consent flag + terminology prefs (kept OUT of the synced store) | App-local, never round-trips through CloudKit; right home for control flags. [ASSUMED — standard practice] |
| Swift Testing | Xcode 16+ (Swift 6) | Schema round-trip, consent-gate, terminology unit tests | Apple's default for new test code; `@Test`/`#expect`; runs against an in-memory `ModelContainer` with no Simulator. [CITED: developer.apple.com — Swift Testing] |

### Supporting (build / scaffold tools — not app dependencies)
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| XcodeGen | latest (Homebrew) | Generate `.xcodeproj` from `project.yml` (D-12, GSD iOS rule) | At scaffold time; `project.yml` is committed, `*.xcodeproj` is gitignored and regenerated. [CITED: ios-scaffold.md] |
| Xcode (full) | 26.x | Build/run in Simulator, SwiftData macros, Simulator destinations | Required for `xcodebuild`; **currently NOT the active toolchain** (see Environment Availability). |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SwiftData | Core Data + `NSPersistentCloudKitContainer` | Only if you later need shared/public CloudKit DB, `.unique` enforcement, or custom conflict resolution. Not needed for single-user private sync; adds boilerplate. |
| `cloudKitDatabase: .none` default | `.automatic` default | `.automatic` would try to sync immediately and needs the iCloud entitlement + a paid account; contradicts D-10's local-first-in-Simulator requirement. Use `.none` now. |
| `UserDefaults` consent flag | A `@Model` consent record | A synced consent record would round-trip through CloudKit and couple "may I process data" to the very store it gates — circular and fragile. Keep the gate flag local. |

**Installation (scaffold tools only — no app packages):**
```bash
brew install xcodegen          # generate the .xcodeproj
# Full Xcode required for builds:
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

**Version verification (run during planning/Wave 0):**
```bash
xcodebuild -version            # confirm full Xcode active (currently fails: CLT-only)
xcodebuild -showsdks | grep iphonesimulator   # confirm an iOS 18+ simulator SDK
xcodegen --version             # confirm scaffold tool present
xcrun simctl list devices available | grep iPhone   # pick a Simulator destination
```

## Package Legitimacy Audit

> Phase 1 installs **no third-party application packages** (all-first-party Apple frameworks). The only external tooling is XcodeGen, a build-time scaffold generator, not an app dependency. slopcheck was not run because there are no registry packages to verify; this is the safest possible posture.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| xcodegen | Homebrew (formula) | 8+ yrs (since 2017) | very high (standard iOS tool) | github.com/yonaskolb/XcodeGen | not run (Homebrew, not npm/PyPI) | Approved — build-time dev tool only, never linked into the app |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
**Application dependencies added this phase:** **none** (deliberate — privacy + maintenance posture).

## Architecture Patterns

### System Architecture Diagram

```
                         ┌──────────── App launch ────────────┐
                         │  EndoAppApp (@main, SwiftUI App)    │
                         │  composition root (D-22):           │
                         │   builds ModelContainer + services, │
                         │   injects into Environment          │
                         └─────────────────┬───────────────────┘
                                           │ reads persisted ConsentState
                                           ▼
                                   ┌────────────────┐
                                   │    AppGate     │  ── routing decision
                                   └───┬────────┬───┘
                       not consented   │        │  consented
                                       ▼        ▼
                         ┌──────────────────┐   ┌──────────────────────────┐
                         │  Onboarding /    │   │  Main app shell          │
                         │  Consent flow    │   │  (walking-skeleton:      │
                         │  - warm copy     │   │   "log one record" +     │
                         │  - disclaimer    │   │   "read it back" screen) │
                         │  - privacy link  │   └──────────┬───────────────┘
                         │  - [I consent] ──┼─ writes flag │ @Query / context.insert
                         └──────────────────┘              ▼
                                              ┌────────────────────────────┐
                                              │  Repository (protocol)     │
                                              │  CRUD facade over store    │
                                              └──────────────┬─────────────┘
                                                             ▼
                                  ┌──────────────────────────────────────────────┐
                                  │  SwiftData ModelContainer (SchemaV1)          │
                                  │  ModelConfiguration(cloudKitDatabase:)        │
                                  │   SyncMode.localOnly → .none   (DEFAULT NOW)  │
                                  │   SyncMode.cloudKit  → .private(container)     │
                                  │      (Phase 4 — flag OFF in Phase 1)          │
                                  └──────────────────────────────────────────────┘
       ── side stores, NOT in the synced SwiftData store ──
       UserDefaults: ConsentState (gate flag) · TerminologyPreference · SyncMode flag
       Static content: PrivacyPolicyView · Disclaimer constant (offline, no network)
```

The flag store and policy/disclaimer content sit deliberately *outside* the SwiftData container so they never participate in CloudKit sync. The dotted "Phase 4" path is reserved but inert now.

### Recommended Project Structure
```
endo-app/
├── project.yml                     # XcodeGen spec — COMMIT this
├── .gitignore                      # includes *.xcodeproj
├── Sources/EndoApp/
│   ├── App/
│   │   ├── EndoAppApp.swift         # @main; composition root (D-22)
│   │   ├── AppDependencies.swift    # wires protocols → impls, builds ModelContainer
│   │   └── AppGate.swift            # routes on ConsentState
│   ├── Persistence/
│   │   ├── SchemaV1.swift           # VersionedSchema enum (D-16)
│   │   ├── Models/                  # @Model types (CloudKit-safe)
│   │   │   ├── BleedingEvent.swift      # first-class (D-19)
│   │   │   ├── TreatmentState.swift     # first-class suppression state (D-19)
│   │   │   └── DayEntry.swift           # walking-skeleton record to round-trip
│   │   ├── ModelContainer+Config.swift  # SyncMode flag → cloudKitDatabase (D-10/D-21)
│   │   └── Repository.swift         # protocol + SwiftData impl (D-22)
│   ├── Consent/
│   │   ├── ConsentStore.swift       # protocol + UserDefaults impl (ONBD-01)
│   │   └── ConsentState.swift       # value type (notConsented / consented(date))
│   ├── Terminology/
│   │   ├── TerminologyPreference.swift  # bleeding-term, pronouns, display name (ONBD-03)
│   │   └── Terminology.swift             # neutral-default lookup (D-08)
│   ├── Content/
│   │   ├── Disclaimer.swift          # single source of truth (ONBD-02)
│   │   └── PrivacyPolicyText.swift    # offline policy text (ONBD-04, D-14)
│   ├── Features/
│   │   ├── Onboarding/ConsentView.swift, OnboardingView.swift
│   │   ├── Privacy/PrivacyPolicyView.swift
│   │   ├── Settings/SettingsView.swift   # withdraw consent (D-04), terminology (D-07)
│   │   └── Skeleton/WalkingSkeletonView.swift  # log + read-back proof
│   └── Resources/
│       ├── Info.plist
│       └── PrivacyInfo.xcprivacy      # Privacy Manifest (no-tracking/no-collection)
└── Tests/EndoAppTests/
    ├── SchemaRoundTripTests.swift     # in-memory ModelContainer round-trip (DATA-01)
    ├── ConsentGateTests.swift          # processing blocked until opt-in (ONBD-01)
    └── TerminologyTests.swift          # neutral defaults + customization (ONBD-03)
```

### Pattern 1: CloudKit-safe `@Model` under a `VersionedSchema` (DATA-01)
**What:** Define every persisted type so it is valid the day CloudKit sync is switched on, even though Phase 1 ships local-only. Wrap them in a `VersionedSchema` from v1 so a migration path exists before it is needed (D-16).
**When to use:** Every `@Model` type in the app, forever (the rules never relax).
**Rules (hard, append-only after a production push):**
- Every stored property is **optional or has a default** — no non-optional, default-less fields. [CITED: SwiftData↔CloudKit rules, fatbobman + Apple]
- **No `@Attribute(.unique)`** — CloudKit has no cross-device uniqueness. Enforce identity with an app-generated `UUID` (D-20).
- Every relationship is **optional and has an explicit inverse**; use `.cascade`/`.nullify`, **never `.deny`**; relationships can't be ordered — store an explicit `sortIndex` if order matters.
- Bleeding state and treatment/suppression state are **separate first-class records**, independent of any cycle (D-19).
- Cycle-day/phase is **never a stored required column** — it is derived/optional/confidence-scored (D-19); model it as optional fields or compute it, never a `= 28` default.
```swift
// Source: developer.apple.com/documentation/swiftdata (VersionedSchema, @Model)
//         + CloudKit model rules (fatbobman / Apple forums)
import SwiftData
import Foundation

enum SchemaV1: VersionedSchema {                       // D-16
    static var versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] {
        [BleedingEvent.self, TreatmentState.self, DayEntry.self]
    }

    @Model final class BleedingEvent {                 // D-19 first-class, D-18 append-only event
        var id: UUID = UUID()                          // D-20 app-generated, NO .unique
        var occurredAt: Date = Date()                  // defaulted (CloudKit rule)
        var flowRaw: String?                           // optional enum-backing
        var note: String?
        init(id: UUID = UUID(), occurredAt: Date = .now, flowRaw: String? = nil) {
            self.id = id; self.occurredAt = occurredAt; self.flowRaw = flowRaw
        }
    }

    @Model final class TreatmentState {                // D-19 suppression is first-class & independent of cycle
        var id: UUID = UUID()
        var startedAt: Date = Date()
        var modeRaw: String?        // e.g. "naturallyCycling" | "suppressed" | "unknown"
        init(id: UUID = UUID(), startedAt: Date = .now, modeRaw: String? = nil) {
            self.id = id; self.startedAt = startedAt; self.modeRaw = modeRaw
        }
    }

    @Model final class DayEntry {                      // walking-skeleton record to round-trip
        var id: UUID = UUID()
        var date: Date = Date()
        var note: String?
        init(id: UUID = UUID(), date: Date = .now, note: String? = nil) {
            self.id = id; self.date = date; self.note = note
        }
    }
}
```
*Note: every property above is optional or defaulted, no `.unique`, no required cycle field — this is what makes the schema CloudKit-safe and append-only-ready before sync is ever turned on.*

### Pattern 2: `SyncMode` feature flag → `ModelConfiguration` (D-10, D-21)
**What:** One enum drives whether the container is local-only (default, Simulator-friendly) or CloudKit private-synced (Phase 4). The flag lives in `UserDefaults`, not the synced store.
**Verified API:** `ModelConfiguration.CloudKitDatabase` has `.none`, `.automatic`, `.private(_:)`, all iOS 17+. [CITED: developer.apple.com/documentation/swiftdata/modelconfiguration/cloudkitdatabase-swift.struct]
```swift
// Source: developer.apple.com/documentation/swiftdata/modelconfiguration
import SwiftData

enum SyncMode { case localOnly, cloudKit            // D-10 feature flag
    static var current: SyncMode { .localOnly }      // DEFAULT in Phase 1 — runs in Simulator, no account
}

func makeModelContainer(inMemory: Bool = false) throws -> ModelContainer {
    let schema = Schema(versionedSchema: SchemaV1.self)
    let cloudKit: ModelConfiguration.CloudKitDatabase =
        (SyncMode.current == .cloudKit) ? .private("iCloud.com.endoapp.ios")  // D-21 private only
                                        : .none                              // local-first default
    let config = ModelConfiguration(schema: schema,
                                    isStoredInMemoryOnly: inMemory,          // test seam
                                    cloudKitDatabase: cloudKit)
    return try ModelContainer(for: schema, configurations: config)
}
```
**Trade-offs:** `.none` needs **no iCloud entitlement and no paid account**, so the walking skeleton builds and runs in the Simulator. Flipping to `.cloudKit` (Phase 4) requires the iCloud + CloudKit capability and Background Modes → Remote notifications, and only then does the schema's CloudKit-safety actually matter — but it is enforced from day one so the later flip is safe.

### Pattern 3: Consent gate outside the synced store (ONBD-01)
**What:** A persisted `ConsentState` flag (in `UserDefaults`, behind a `ConsentStore` protocol) that `AppGate` reads to decide whether the app shows onboarding/consent or the main shell. No `@Model` health record is read or written until consent is `consented`.
**When to use:** As the top-level route for the entire app, and as a precondition the Repository can assert in debug.
```swift
protocol ConsentStore {                              // D-22 injectable/testable
    var state: ConsentState { get }
    func grant(at: Date)
    func withdraw()                                  // D-04 withdrawable in Settings
}
enum ConsentState: Equatable { case notConsented, consented(Date) }
```
**Trade-offs:** + Gate is trivially unit-testable (no UI, no store). + Survives reinstall logic stays simple. − The flag is device-local (not synced) — acceptable and arguably correct: consent is a per-device, withdrawable control, not user health data.

### Pattern 4: Neutral-default terminology lookup (ONBD-03, D-06/07/08)
**What:** A `TerminologyPreference` value type (bleeding-term enum, pronoun set, optional display name) persisted in `UserDefaults`, resolved through a `Terminology` lookup that *all* user-facing copy goes through. Defaults are neutral ("bleeding"/"cycle"); customization lives in Settings, never as a forced onboarding wall.
```swift
struct TerminologyPreference: Codable, Equatable {
    enum BleedingTerm: String, CaseIterable, Codable { case bleeding, period, menstruation }
    var bleedingTerm: BleedingTerm = .bleeding       // D-08 neutral default
    var pronouns: String? = nil                       // non-assumptive (D-06)
    var displayName: String? = nil
}
```
**Trade-offs:** + One choke-point makes a copy audit (no "she", no "women", no "lady cycle") enforceable and testable. − Requires discipline: every string must resolve through it, not be hard-coded.

### Anti-Patterns to Avoid
- **`@Attribute(.unique)` or non-optional/default-less fields on any `@Model`:** silently breaks CloudKit sync and forces an unsafe post-launch schema change. Enforce identity in code via app-generated `UUID`.
- **A required `cycleDay`/`cyclePhase` column or `cycleLength = 28`:** the #1 foundational pitfall; breaks for irregular/suppressed users and can't be retrofitted. Cycle-day is derived/optional only.
- **Storing the consent flag (or terminology) as a synced `@Model`:** couples control flags to the very store they gate and pushes them through CloudKit. Keep in `UserDefaults`.
- **`cloudKitDatabase: .automatic` in Phase 1:** tries to sync immediately, needs entitlements + a paid account, blocks Simulator runs. Use `.none`.
- **Gendered/period-assuming default copy** ("women", "she", "lady cycle"): alienates trans/non-binary/suppressed users. Route all copy through neutral `Terminology`.
- **Diagnostic/medical-purpose verbs anywhere** ("diagnose", "detect", "treat", "clinically proven"): risks crossing the MDR/DiGA medical-device line. Use the claims policy below.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cross-device sync | Custom CloudKit `CKRecord` plumbing | SwiftData `cloudKitDatabase: .private` (Phase 4) | Apple handles conflict/merge/push; hand-rolling is a multi-month trap. |
| Local persistence | Hand-written SQLite / file store | SwiftData `ModelContainer` | First-party, observable, sync-ready, migration-aware. |
| Unique IDs / dedup | DB unique constraints | App-generated `UUID` + upsert-by-id | `.unique` is forbidden under CloudKit; UUID is the documented substitute. |
| `.xcodeproj` generation | Hand-edited pbxproj / SPM executable | XcodeGen `project.yml` | SPM executables produce macOS CLI binaries, not iOS `.app` bundles (GSD anti-pattern #28). |
| Test database | Spinning up a real store / Simulator for logic tests | In-memory `ModelConfiguration(isStoredInMemoryOnly: true)` | Fast, deterministic, no Simulator/iCloud needed. |
| Privacy policy hosting | A web backend now | Offline `PrivacyPolicyView` (D-14), hosted URL deferred to submission | No server cost; offline source of truth; matches privacy story. |

**Key insight:** In this domain the most expensive mistakes are *schema-shape* and *claims-language* mistakes — both are effectively irreversible (CloudKit append-only; reputational/regulatory for language). Spend effort getting the model rules and the copy choke-point right; let Apple's frameworks own everything mechanical.

## Runtime State Inventory

> Greenfield phase — there is **no existing app, store, service, or registration** to migrate. This section is included only to record that the foundations being laid here are the things *future* rename/refactor phases will have to inventory.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no prior SwiftData store, no CloudKit container provisioned yet. | None. (Phase 1 *creates* `SchemaV1`; design it append-only so future phases never need a destructive migration.) |
| Live service config | None — no CloudKit container, no TestFlight, no App Store Connect record yet (gated on enrollment, D-11). | None now. |
| OS-registered state | None — no app installed, no background modes registered. | None. |
| Secrets/env vars | None — no paid account, no provisioning profile, no API keys (app has no backend by design). | None. |
| Build artifacts | None — no `.xcodeproj` exists yet; XcodeGen will generate it (gitignored). | None. (Ensure `*.xcodeproj` is gitignored so it's always regenerated.) |

**Nothing found in every category** — verified: the project tree contains only `.planning/` docs and `CLAUDE.md`; no Swift sources, no `.xcodeproj`, no provisioned container.

## Common Pitfalls

### Pitfall 1: 28-day cycle assumption baked into the schema **[FOUNDATIONAL]**
**What goes wrong:** A non-optional `cycleDay`/`cyclePhase` or `cycleLength = 28` field; data hangs off an assumed cycle.
**Why it happens:** Every tutorial models the textbook cycle; it demos fine on regular fake data.
**How to avoid:** Bleeding + treatment/suppression states are first-class records (D-19); cycle-day is derived/optional/confidence-scored, never required. Include a "suppressed, no bleeding for 8 months" test fixture even in Phase 1's schema tests.
**Warning signs:** any non-optional cycle field; any `= 28` default; fixtures only contain regular cyclers.

### Pitfall 2: CloudKit schema rigidity / unsafe model **[FOUNDATIONAL]**
**What goes wrong:** `.unique`, non-optional fields, `.deny` rules, or missing inverses make the model invalid for CloudKit; after a production push the schema is append-only and can't be cleanly fixed → silent data loss.
**Why it happens:** SwiftData "just works" locally, hiding the CloudKit constraints until sync is on.
**How to avoid:** Apply Pattern 1's rules from v1 *even though sync is off in Phase 1*. Wrap in `VersionedSchema`. Treat the model as append-only forever.
**Warning signs:** any `.unique`; any non-optional default-less property; relationship without an inverse.

### Pitfall 3: App Store 5.1.3(ii) "no health data in iCloud" line **[FOUNDATIONAL, MEDIUM certainty]**
**What goes wrong:** Apple's verbatim text — *"Apps must not write false or inaccurate data into HealthKit or any other medical research or health management apps, and may not store personal health information in iCloud."* [CITED: developer.apple.com/app-store/review/guidelines] — has **no written CloudKit-private-DB exception**. A literal reviewer reading could reject the sync architecture.
**Why it happens:** The rule targets dumping HealthKit data into *Apple-readable* general iCloud (Drive/KVS); the private CloudKit DB (account-scoped, app-private) is the accepted pattern Apple's own Health app uses — but the line is reviewer-dependent and not written down as an exception.
**How to avoid (Phase 1 scope):** (a) keep sync **off** in Phase 1 (local-only, no exposure); (b) design so HealthKit-derived samples are **never** copied into the synced store (D-21, relevant Phase 9); (c) ship the **Privacy Manifest** + explicit in-app consent copy; (d) plan — but **gate on enrollment (D-11)** — a throwaway TestFlight build + a pre-submission App Review note describing the private-encrypted-CloudKit design, with a **local-only fallback** if rejected. The de-risk *step* is out of this phase; the *architecture that makes it winnable* is in this phase.
**Warning signs:** any code path that would write HealthKit-derived values into the synced store; use of public/shared CloudKit DB; no Privacy Manifest; no in-app storage-location explanation.

### Pitfall 4: GDPR Art. 9 special-category data treated as ordinary **[FOUNDATIONAL]**
**What goes wrong:** Health/menstrual/symptom data is special-category under GDPR Art. 9; the realistic lawful basis is **explicit, specific, withdrawable consent**. Shipping without it (even on-device) is legal exposure.
**Why it happens:** "It never leaves the device, so GDPR doesn't apply" is a common but wrong assumption.
**How to avoid (Phase 1):** Explicit opt-in consent **before** any health-data processing (ONBD-01, the gate); withdrawable in Settings (D-04) with a plain explanation; a genuine in-app privacy policy (ONBD-04); zero analytics/ad SDKs (none added — verified). Real delete-purges-cloud is a Phase 4 verification; the consent + policy posture is here.
**Warning signs:** onboarding asks for data before any consent screen; any analytics/crash SDK touching health data; placeholder privacy policy.

### Pitfall 5: Medical-device / MDR-DiGA line via claims language **[FOUNDATIONAL cross-cutting]**
**What goes wrong:** Medical-purpose verbs ("diagnose", "detect [disease]", "treat", "predict flares", "clinically validated") can pull the app across the EU MDR line into a regulated medical device — the exact DiGA path the project avoids.
**How to avoid (Phase 1):** Establish a **claims/language policy** now (below), surface "not medical advice / not a medical device" in onboarding (ONBD-02, D-05), and route all copy through the neutral `Terminology` choke-point so a copy audit is enforceable.
**Warning signs:** any medical-purpose verb in copy or model names; insights framed as instructions or clinical alerts (relevant later, but the policy is set now).

## Claims & Language Policy (set in Phase 1, enforced cross-cutting)

This is a deliverable, not just guidance — encode it as a constant + a reviewable checklist.

**Allowed framing:** "informational", "patterns in *your own* data", "possible association", "worth discussing with your clinician", "not medical advice", "not a medical device", "self-tracking".
**Forbidden verbs/claims:** "diagnose", "detect [disease]", "treat", "cure", "prevent", "clinically validated/proven", "medical-grade", "trigger/causes/will" (causal), instructions ("take X / stop Y").
**Inclusive-language rules (ONBD-03, D-06):** never assume the user is a woman; no "she/women/ladies/lady cycle" in core flows; symptom-first, "you"-framed; neutral defaults ("bleeding"/"cycle"); a path for "I don't currently menstruate" (suppressed) exists.
**Single source of truth:** `Disclaimer` constant (ONBD-02) + neutral `Terminology` lookup (ONBD-03). All user-facing strings resolve through these.

## Code Examples

### In-memory `ModelContainer` for tests (the DATA-01 test seam)
```swift
// Source: developer.apple.com/documentation/swiftdata/modelconfiguration (isStoredInMemoryOnly)
import SwiftData
import Testing

@Test func dayEntry_roundTrips_throughInMemoryStore() async throws {
    let container = try makeModelContainer(inMemory: true)   // .none + isStoredInMemoryOnly
    let ctx = ModelContext(container)
    let id = UUID()
    ctx.insert(SchemaV1.DayEntry(id: id, note: "hello"))
    try ctx.save()
    let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.DayEntry>())
    #expect(fetched.count == 1)
    #expect(fetched.first?.id == id)
    #expect(fetched.first?.note == "hello")
}
```

### Consent-gate routing (ONBD-01)
```swift
struct AppGate: View {
    @Environment(\.consentStore) private var consent      // injected (D-22)
    var body: some View {
        switch consent.state {
        case .notConsented:           OnboardingView()     // shows disclaimer + privacy link
        case .consented:              WalkingSkeletonView() // only here can health records be written
        }
    }
}
```

### Privacy Manifest (`PrivacyInfo.xcprivacy`) — no tracking, no collection
```xml
<!-- Source: developer.apple.com/documentation/bundleresources (privacy manifest keys) -->
<plist version="1.0"><dict>
  <key>NSPrivacyTracking</key><false/>
  <key>NSPrivacyTrackingDomains</key><array/>
  <key>NSPrivacyCollectedDataTypes</key><array/>   <!-- empty: app collects no data off-device -->
  <key>NSPrivacyAccessedAPITypes</key>
  <array>
    <dict>
      <key>NSPrivacyAccessedAPIType</key>
      <string>NSPrivacyAccessedAPITypeUserDefaults</string>   <!-- consent + terminology flags -->
      <key>NSPrivacyAccessedAPITypeReasons</key>
      <array><string>CA92.1</string></array>                  <!-- access app's own values -->
    </dict>
  </array>
</dict></plist>
```
*`CA92.1` is the required-reason code for an app accessing its own `UserDefaults` values.* [CITED: developer.apple.com/documentation/bundleresources — required-reason APIs]
> When sync is turned on in Phase 4, revisit the manifest: declare the CloudKit reason and confirm `NSPrivacyCollectedDataTypes` still truthfully reflects "no off-device collection" (private CloudKit is the user's own iCloud, not third-party collection — but verify against the current Apple guidance at that time).

### XcodeGen `project.yml` (iOS 18, SwiftUI, test target)
```yaml
# Source: ~/.claude/get-shit-done/references/ios-scaffold.md (adapted to iOS 18)
name: EndoApp
options:
  bundleIdPrefix: com.endoapp
  deploymentTarget: { iOS: "18.0" }            # D-12
settings:
  SWIFT_VERSION: "6.0"
  IPHONEOS_DEPLOYMENT_TARGET: "18.0"
targets:
  EndoApp:
    type: application
    platform: iOS
    sources: [Sources/EndoApp]
    settings:
      PRODUCT_BUNDLE_IDENTIFIER: com.endoapp.ios   # D-09 placeholder, flagged to finalize
      INFOPLIST_FILE: Sources/EndoApp/Resources/Info.plist
      # NOTE: do NOT add the iCloud/CloudKit or Background-Modes capability in Phase 1 —
      # SyncMode.localOnly (.none) needs none of it; added in Phase 4 when sync flips on.
    scheme:
      testTargets: [EndoAppTests]
  EndoAppTests:
    type: bundle.unit-test
    platform: iOS
    sources: [Tests/EndoAppTests]
    dependencies: [{ target: EndoApp }]
```
> The `.xcprivacy` and `Info.plist` live under `Sources/EndoApp/Resources/`; ensure `project.yml` includes them as resources of the app target.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Core Data + `NSPersistentCloudKitContainer` + hand-written CloudKit | SwiftData `@Model` + `cloudKitDatabase:` | iOS 17 (2023), matured iOS 18/26 cycle | Far less boilerplate for single-user private sync; same CloudKit constraints still apply. |
| Info.plist-only privacy disclosure | **Privacy Manifest** (`PrivacyInfo.xcprivacy`) + required-reason APIs | Enforced 2024 | Apps using `UserDefaults` etc. must declare reasons; ship the manifest from v1. |
| XCTest for new logic | **Swift Testing** (`@Test`/`#expect`) | Xcode 16 (2024) | Cleaner, parallel, parameterized tests; coexists with XCTest in separate targets. |
| MVVM-per-screen in SwiftUI | **MV + thin services behind protocols** | 2024–25 SwiftUI consensus | Less boilerplate; testability lives in services, not view models. |

**Deprecated/outdated:**
- Hand-rolled SQLite/file persistence for a SwiftUI app — superseded by SwiftData.
- SPM `.executableTarget` to "scaffold" an iOS app — produces a macOS CLI binary; never use (GSD anti-pattern #28).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Storing the consent flag + terminology in `UserDefaults` (not the synced store) is the right home. | Stack / Pattern 3/4 | Low — standard practice; if the user wanted consent to sync across devices, revisit (but consent-per-device is defensible). |
| A2 | An empty `NSPrivacyCollectedDataTypes` is truthful given private-CloudKit (user's own iCloud) is not "collection". | Privacy Manifest | Medium — confirm against current Apple guidance when sync flips on in Phase 4; private DB is account-scoped, not third-party collection. |
| A3 | Swift 6.0 language mode is acceptable for the scaffold (toolchain shows Swift 6.3.2). | project.yml | Low — can pin `SWIFT_VERSION` to whatever the installed Xcode supports; verify in Wave 0. |
| A4 | iOS 18 Simulator SDK is available once full Xcode is installed. | Environment | Medium — `xcodebuild -showsdks` couldn't run (CLT-only); verify after `xcode-select` to full Xcode. |
| A5 | XcodeGen on Homebrew is current and resolves the `*.xcodeproj` correctly for iOS 18. | Scaffold | Low — XcodeGen is a long-standing standard tool; `xcodegen --version` in Wave 0 confirms. |

## Open Questions

1. **Is the 5.1.3(ii) private-CloudKit interpretation safe?**
   - What we know: Apple's text has no written exception; private CloudKit DB is the de-facto accepted pattern (Apple's own Health app); the rule's intent targets *Apple-readable* general iCloud.
   - What's unclear: reviewer-dependent outcome — genuinely uncertain until a real submission.
   - Recommendation: out of scope for Phase 1 (sync off). De-risk via TestFlight + review note in a later, enrollment-gated step (D-11), with a permanent local-only fallback already in place.
2. **Should consent state sync across the user's devices?**
   - What we know: it's currently device-local (`UserDefaults`).
   - What's unclear: whether the user expects "I consented on my iPhone" to carry to their iPad.
   - Recommendation: keep device-local for Phase 1 (simpler, correct for a withdrawable per-device control); flag for the user to confirm in discuss/plan.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Full Xcode (active toolchain) | `xcodebuild` Simulator build + SwiftData macros | ✗ | active dir is **Command Line Tools only** | Install Xcode + `sudo xcode-select -s /Applications/Xcode.app/...` — no fallback; required to build/run. |
| `xcodegen` | Generating the `.xcodeproj` (D-12) | ✗ | — | `brew install xcodegen` — no fallback for the required scaffold pattern. |
| Swift toolchain | Compiling Swift | ✓ | 6.3.2 (macOS arm64, target macosx26.0) | — |
| iOS 18+ Simulator SDK | Simulator build/run | ? unverified | — | `xcodebuild -showsdks` after full Xcode is active; ensure an iOS 18+ runtime + an iPhone simulator are installed. |
| Apple Developer account (paid) | CloudKit sync, device runs, TestFlight, 5.1.3(ii) de-risk | ✗ (by design, D-11) | — | **Not needed for Phase 1** — local-only (`.none`) runs in the Simulator with no account. Gated dependency for later phases. |

**Missing dependencies with no fallback (planner MUST add a Wave 0 / setup step):**
- Full Xcode not the active toolchain (`xcode-select`).
- `xcodegen` not installed.

**Missing dependencies with fallback:**
- Apple Developer account — intentionally deferred; Phase 1 runs entirely local-only in the Simulator.

## Validation Architecture

> `workflow.nyquist_validation: true` in config — this section is required. Every Phase-1 requirement is provable **without a paid Apple account**: schema/consent/terminology via Swift Testing against an in-memory `ModelContainer`, plus an `xcodebuild` Simulator build for the launchable-skeleton proof.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`), Xcode 16+/Swift 6 |
| Config file | none — test target declared in `project.yml` (`EndoAppTests`); created in Wave 0 |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Build-only check | `xcodebuild -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' build` |

> Substitute a real available Simulator name from `xcrun simctl list devices available`. All commands require full Xcode active (see Environment Availability).

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-01 | A `DayEntry` (and `BleedingEvent`) round-trips through an in-memory `ModelContainer`; schema is CloudKit-safe (all optional/defaulted, no `.unique`, inverses present) | unit | `xcodebuild test ... -only-testing:EndoAppTests/SchemaRoundTripTests` | ❌ Wave 0 |
| DATA-01 | "Suppressed, no bleeding for months" fixture persists with no phantom cycle field | unit | same file, dedicated `@Test` | ❌ Wave 0 |
| ONBD-01 | Repository/processing path is blocked until `ConsentState == .consented`; `withdraw()` returns to gated | unit | `... -only-testing:EndoAppTests/ConsentGateTests` | ❌ Wave 0 |
| ONBD-03 | `Terminology` returns neutral defaults ("bleeding"/"cycle"); customization changes resolved copy; no gendered default strings | unit | `... -only-testing:EndoAppTests/TerminologyTests` | ❌ Wave 0 |
| ONBD-02 | `Disclaimer` constant is present and surfaced by `OnboardingView` (snapshot/string-presence assertion) | unit | `... -only-testing:EndoAppTests/DisclaimerTests` | ❌ Wave 0 |
| ONBD-04 | `PrivacyPolicyText` is non-empty, mentions on-device + private iCloud + no third-party sharing; reachable from onboarding + Settings | unit | `... -only-testing:EndoAppTests/PrivacyPolicyTests` | ❌ Wave 0 |
| (skeleton) | App builds and launches for the Simulator destination | build | `xcodebuild ... build` (exit 0) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the relevant `-only-testing:` quick run for the file touched.
- **Per wave merge:** full `xcodebuild test` suite green.
- **Phase gate:** full suite green + a successful `xcodebuild ... build` for the Simulator destination before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Install/select full Xcode (`xcode-select`) and `brew install xcodegen` — **blocking** all build/test commands.
- [ ] Generate `.xcodeproj` from `project.yml` (`xcodegen generate`).
- [ ] `Tests/EndoAppTests/SchemaRoundTripTests.swift` — covers DATA-01 (incl. suppressed fixture).
- [ ] `Tests/EndoAppTests/ConsentGateTests.swift` — covers ONBD-01.
- [ ] `Tests/EndoAppTests/TerminologyTests.swift` — covers ONBD-03.
- [ ] `Tests/EndoAppTests/DisclaimerTests.swift` + `PrivacyPolicyTests.swift` — covers ONBD-02/04.
- [ ] Confirm an iOS 18+ Simulator runtime + iPhone device exist (`xcrun simctl list`).

**Definition of done for Phase 1:** `xcodebuild ... build` exits 0 for the Simulator; the app launches showing the consent gate; after granting consent, the skeleton screen writes one record and reads it back; all six unit-test files pass; the schema contains no `.unique`, no non-optional/default-less field, and no required cycle column.

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high`. Applicable to a single-user, no-account, no-network, on-device app — most network/auth categories are N/A by architecture.

### Applicable ASVS Categories (Level 1)
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login by design; iCloud identity is implicit (Phase 4). Optional Face ID app-lock is a later UX item, not Phase 1. |
| V3 Session Management | no | No sessions/tokens; no backend. |
| V4 Access Control | partial | The **consent gate** is the only access-control surface: no health-data processing until opt-in (ONBD-01). Enforce in `AppGate` + assertable in Repository. |
| V5 Input Validation | yes | Onboarding free-text (display name, notes) — treat as plain local strings; no injection surface (no SQL/HTML/network). Bound lengths to avoid pathological input. |
| V6 Cryptography | no (Phase 1) | No crypto in Phase 1 (local store, no sync). CloudKit private DB is Apple-encrypted at rest/in transit — relevant Phase 4; **never hand-roll crypto**. |
| V7 Error/Logging | yes | **Never log health values** to console/crash output (Pitfall). Redact; no PII in any logging path; no crash/analytics SDK present (verified — none added). |
| V8 Data Protection | yes | Special-category data minimization: stays on-device; consent before processing; in-app privacy policy; no third-party SDKs. Consider enabling Data Protection (file encryption) — default on iOS; no extra work needed. |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health data exfiltrated by an analytics/crash/ad SDK | Information Disclosure | **Zero such SDKs** (verified none added); truthful Privacy Manifest (`NSPrivacyTracking false`, empty collected types). |
| Health values leaked via logs / crash reports | Information Disclosure | Never log health values; redact; Apple-only privacy-preserving diagnostics (MetricKit) if any. |
| Processing health data before consent (GDPR Art. 9) | Repudiation / compliance | Consent gate blocks all processing until explicit opt-in; withdrawable (ONBD-01, D-04). |
| Shoulder-surfing on a shared device | Information Disclosure | Optional Face ID/passcode app-lock — noted for a later UX phase, not Phase 1. |
| Storing health data where Apple/third parties can read it | Information Disclosure / policy | Private CloudKit DB only (Phase 4, D-21); HealthKit data never copied into synced storage; 5.1.3(ii) mitigations. |

**Security block assessment:** No HIGH-severity issues in Phase 1's scope (no network, no auth, no crypto, no third-party code, no health processing before consent). The dominant security control *is* the consent gate + the no-third-party-SDK posture — both delivered in this phase.

## Sources

### Primary (HIGH confidence)
- developer.apple.com/documentation/swiftdata/modelconfiguration/cloudkitdatabase-swift.struct — `.none` / `.automatic` / `.private(_:)` cases + iOS 17 availability (verified via WebFetch this session)
- developer.apple.com/app-store/review/guidelines — exact verbatim text of 5.1.1, 5.1.2, 5.1.3(i)/(ii)/(iii) (verified via WebFetch this session)
- developer.apple.com/documentation/bundleresources — Privacy Manifest keys; UserDefaults required-reason `CA92.1` (verified via WebFetch this session)
- ~/.claude/get-shit-done/references/ios-scaffold.md — required XcodeGen `project.yml` → `.xcodeproj` pattern; SPM-executable prohibition (read this session)
- Local environment probes — `xcodebuild` CLT-only error; `swift --version` 6.3.2; `brew list xcodegen` (not installed); `.planning/config.json` flags (run this session)

### Secondary (MEDIUM confidence)
- .planning/research/{STACK,ARCHITECTURE,PITFALLS,SUMMARY}.md — prior project research (SwiftData↔CloudKit rules, MV architecture, the four foundational pitfalls) corroborated against Apple docs above
- fatbobman.com / Hacking with Swift (referenced in prior research) — CloudKit model-adaptation rules (optional/no-`.unique`/inverses) — corroborated by Apple forum guidance

### Tertiary (LOW confidence / needs validation)
- The 5.1.3(ii) private-CloudKit *interpretation* (no written exception found) — reviewer-dependent; validate only via a real TestFlight submission (gated, D-11)
- iOS 18 Simulator SDK availability — unverified locally (CLT-only blocked `xcodebuild -showsdks`); confirm in Wave 0

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all-first-party APIs verified against Apple docs this session.
- Architecture / schema rules: HIGH — CloudKit-safe model rules corroborated by Apple docs + prior research.
- App Store 5.1.3(ii): MEDIUM — guideline text is verbatim/HIGH, but the interpretation is genuinely reviewer-dependent (correctly deferred).
- Environment: HIGH on the blocker (CLT-only, xcodegen missing — directly observed); MEDIUM on Simulator SDK (unverified).

**Research date:** 2026-06-03
**Valid until:** ~2026-07-03 (stable Apple frameworks; re-verify Privacy Manifest + 5.1.3 guideline text near submission, and SwiftData CloudKit behavior when sync flips on in Phase 4)
