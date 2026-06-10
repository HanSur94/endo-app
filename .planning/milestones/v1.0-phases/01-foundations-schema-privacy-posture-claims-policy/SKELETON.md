# Walking Skeleton — Endo-App

**Phase:** 1
**Generated:** 2026-06-03

## Capability Proven End-to-End

A first-run user is blocked by a warm, plain-language consent gate that surfaces the "not medical advice / not a medical device" disclaimer and an offline privacy policy; after explicitly consenting, they reach a screen that writes one record to the local SwiftData store and reads it back — and they can later withdraw consent in Settings, which re-gates the app.

This is the thinnest slice that exercises the full stack: iOS app launch → SwiftUI routing on a persisted consent flag → SwiftData write/read through a CloudKit-safe schema → a real UI interaction (consent + log) → a Simulator run, with **no Apple Developer account, no network, and no sync** required.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Scaffold / build system | XcodeGen `project.yml` → real `.xcodeproj` (`*.xcodeproj` gitignored, regenerated) | GSD iOS rule + D-12: an SPM `.executableTarget` produces a macOS CLI binary, not an iOS `.app` (anti-pattern #28). `project.yml` is the committed source of truth. |
| Platform / language | iOS 18 minimum, SwiftUI app lifecycle, Swift 6 (pin `SWIFT_VERSION` to installed Xcode if needed) | D-12; matured SwiftData/Charts/HealthKit APIs from the iOS 18 cycle; first-party bindings. |
| UI architecture | MV (no per-screen ViewModels) + thin services behind protocols | 2024–25 SwiftUI consensus (RESEARCH §State of the Art); testability lives in services, not view models. |
| Persistence | SwiftData `VersionedSchema` `SchemaV1`; CloudKit-safe `@Model` types from v1 | D-16..D-21: all attributes optional/defaulted, NO `@Attribute(.unique)`, relationships optional with inverses, no `.deny`. Append-only after any future CloudKit push, so correct-from-v1 is mandatory. App-generated `UUID` is the identity substitute for `.unique` (D-20). |
| Data model shape | Bleeding state + treatment/suppression state are FIRST-CLASS, independent records; cycle-day/phase is derived/optional/confidence-scored — never a required column, never `= 28` | D-19, the #1 foundational pitfall. Append-only timestamped event records, not mutable daily aggregates (D-18). |
| Sync | `SyncMode` feature flag → `ModelConfiguration(cloudKitDatabase:)`; `.none` (local-only) is the Phase-1 default; `.private(_:)` reserved for Phase 4 | D-10/D-21: `.none` needs no entitlement and no paid account, so the skeleton runs in the Simulator. The schema is CloudKit-safe now so the Phase-4 flip is safe. No iCloud/CloudKit/Background-Modes capability is added in Phase 1. Private DB only — no shared/public, and HealthKit-derived samples are never copied into synced storage (5.1.3 mitigation). |
| Consent / access control | Persisted `ConsentState` in `UserDefaults` (OUTSIDE the synced store), behind a `ConsentStore` protocol; `AppGate` routes on it | ONBD-01, D-02/D-04, GDPR Art. 9, ASVS V4. The flag must not round-trip through CloudKit, so it lives in `UserDefaults` (not a `@Model`). Withdrawable in Settings. Device-local consent is defensible for a withdrawable per-device control (RESEARCH Open Question 2). |
| Composition root / DI | `EndoAppApp` (@main) builds the `ModelContainer` + services in `AppDependencies` and injects them via the SwiftUI Environment | D-22: one wiring point; services (`Repository`, `ConsentStore`, terminology) are protocols so later phases (logging, sync, HealthKit, insights) inject and test in isolation. |
| Inclusive language | `TerminologyPreference` (bleeding-term enum, pronouns, display name) in `UserDefaults`; a neutral-default `Terminology` lookup all copy resolves through | ONBD-03, D-06/07/08: neutral defaults ("bleeding"/"cycle"), customization in Settings (not a forced onboarding wall). The single choke-point makes a gendered-copy audit testable. |
| Claims / disclaimer | A single `Disclaimer` constant + `DisclaimerBanner` view; a `ClaimsPolicy` constant listing allowed framing + forbidden medical-purpose verbs | ONBD-02, D-05, Pitfall 5: keeps the app on the wellness/self-tracking side of the MDR/DiGA line; the disclaimer is reused on insight surfaces later. |
| Privacy policy | Offline in-app `PrivacyPolicyView` rendering a `PrivacyPolicyText` constant; no hosting | ONBD-04, D-13/14: the offline screen is the source of truth; the hosted URL is a submission-time dependency (D-15, deferred). Names the independent developer as data controller + `sannahrush@googlemail.com`. |
| Privacy Manifest | `PrivacyInfo.xcprivacy` from v1: `NSPrivacyTracking` false, empty `NSPrivacyCollectedDataTypes`, `CA92.1` UserDefaults reason | Enforced since 2024; truthful no-tracking/no-collection posture; the dominant Information-Disclosure control alongside the zero-third-party-SDK rule. Revisit when sync flips on (Phase 4). |
| Dependencies | ZERO third-party application packages — all first-party Apple frameworks; XcodeGen is a build-time tool only | Privacy + maintenance posture (RESEARCH §Package Legitimacy Audit). No analytics/ad/crash SDKs touching health data — ever. |
| Testing | Swift Testing (`@Test`/`#expect`) against an in-memory `ModelContainer` (`isStoredInMemoryOnly: true`) + `xcodebuild` Simulator build | Every Phase-1 requirement is provable without a paid Apple account (RESEARCH §Validation Architecture). |
| Directory layout | Feature/responsibility folders under `Sources/EndoApp/` (App, Persistence, Consent, Terminology, Content, Features, Resources); tests under `Tests/EndoAppTests/` | RESEARCH §Recommended Project Structure. |

## Stack Touched in Phase 1

- [x] Project scaffold (XcodeGen → `.xcodeproj`, iOS 18 SwiftUI app + unit-test target, build green)
- [x] Routing — `AppGate` routes the whole app on `ConsentState` (onboarding vs main shell)
- [x] Database — a real local SwiftData write AND read-back of a `DayEntry` (the skeleton screen) + in-memory round-trip tests
- [x] UI — interactive consent action, "Log a day" write/read, Settings (withdraw + terminology), privacy-policy screen
- [x] Deployment — documented local full-stack run: `xcodegen generate` → `xcodebuild ... build`/`test` for an iOS 18 Simulator destination (no device / no Apple Developer account)

## Directory Layout (the contract)

```
endo-app/
├── project.yml                       # XcodeGen spec — COMMIT
├── .gitignore                        # *.xcodeproj regenerated, never committed
├── Sources/EndoApp/
│   ├── App/                          # EndoAppApp (@main, composition root), AppDependencies, AppGate
│   ├── Persistence/                  # SchemaV1, Models/*, ModelContainer+Config (SyncMode), Repository
│   ├── Consent/                      # ConsentState, ConsentStore (UserDefaults impl)
│   ├── Terminology/                  # TerminologyPreference, Terminology (neutral lookup)
│   ├── Content/                      # Disclaimer, PrivacyPolicyText, ClaimsPolicy
│   ├── Features/                     # Onboarding/, Privacy/, Settings/, Skeleton/
│   └── Resources/                    # Info.plist, PrivacyInfo.xcprivacy
└── Tests/EndoAppTests/               # SchemaRoundTrip, ConsentGate, Terminology, Disclaimer, PrivacyPolicy
```

## Out of Scope (Deferred to Later Slices)

These are explicitly NOT in the skeleton — listed so later phases do not re-litigate Phase 1's minimalism:

- **Turning on CloudKit sync** — `SyncMode.cloudKit`/`.private(_:)`, the iCloud + CloudKit capability, Background-Modes → Remote notifications, and the entitlement → **Phase 4** (the schema is already CloudKit-safe; only the flag flips). (D-10)
- **HealthKit import + its just-in-time consent UX** → **Phase 9**. HealthKit-derived samples are never copied into the synced store (D-03/D-21).
- **The insights engine** (snapshot contract, descriptive trends, cycle-phase mapping, honesty layer, correlations) → **Phases 5–8**, built as an isolated value-type-consuming Swift package against synthetic fixtures.
- **Real symptom / cycle / bleeding / lifestyle logging UI** → **Phases 2–3** (Phase 1 ships only the skeleton `DayEntry` round-trip; `BleedingEvent`/`TreatmentState` models exist now but their logging UI is Phase 2).
- **Apple Developer Program enrollment (~$99/yr)** and therefore physical-device runs, TestFlight, and the **App Store 5.1.3(ii) de-risk** (throwaway TestFlight build + pre-submission review note) → **gated follow-up**, NOT in Phase 1's executable scope (D-11). The architecture that makes that de-risk winnable (private DB only, no HealthKit data in synced storage, Privacy Manifest, consent copy, local-only fallback) IS in Phase 1.
- **Hosted privacy-policy URL** (same text as the in-app screen) → **App Store submission time** (D-15).
- **Final app name + real bundle id** → at branding finalization / enrollment; Phase 1 ships display name "Endo-App" + placeholder `com.endoapp.ios` (D-09).
- **Face ID / passcode app-lock** (shoulder-surfing mitigation) → a later UX phase, noted not built (RESEARCH §Security Domain).
- **Syncing consent state across devices** → open question; device-local for now (RESEARCH Open Question 2).

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions (CloudKit-safe schema, MV + protocol services, consent gate, terminology/claims choke-points, local-first behind the SyncMode flag):

- **Phase 2:** Core symptom logging (body map, notes, edit/delete) + the first-class bleeding & cycle model (suppressed/no-cycle mode, derived confidence-scored cycle-day).
- **Phase 3:** Lifestyle logging (food, activity, sleep, meds, stress, mood) + a fast daily check-in + fatigue-aware reminders.
- **Phase 4:** Flip `SyncMode` to `.cloudKit` (`.private`) — add the iCloud/CloudKit + Background-Modes capability; prove multi-device/offline/reinstall sync and true delete-purge on the already-CloudKit-safe schema.
- **Phase 5–7:** The isolated insights Swift package — snapshot contract + descriptive trends, then cycle-phase mapping + the honesty layer, then correlation/trigger discovery — all against synthetic fixtures.
- **Phase 8:** Wire persistence → engine off-main (Repository maps `@Model` rows to snapshots), cache `CachedInsight`, and surface a Swift Charts dashboard.
- **Phase 9:** Additive read-only HealthKit import (never copied into synced storage; fully usable when denied).
- **Phase 10:** On-device doctor-ready PDF report (source-labeled, "self-tracked, not diagnostic", system share sheet).
- **Phase 11:** Personalized, non-prescriptive recommendations routed through the honesty layer.
- **Phase 12:** Heuristic, non-diagnostic flare early-warning — built last, gated by the honesty layer.
