# Phase 1: Foundations — Schema, Privacy Posture & Claims Policy - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase locks the **irreversible foundations** so the rest of the build is conventional Apple-platform engineering:
- A CloudKit-safe, append-only **SwiftData schema** with bleeding/treatment state first-class and cycle-day derived/optional (DATA-01).
- An explicit, withdrawable **GDPR consent** flow gating any health-data processing (ONBD-01).
- A clear **"not medical advice / not a medical device"** framing (ONBD-02).
- **Inclusive, non-assumptive language** + optional terminology customization (ONBD-03).
- An in-app **privacy policy** (ONBD-04).
- Project scaffold + an early **App Store 5.1.3(ii)** review de-risk plan (with local-only fallback).

Clarifying HOW to implement what's scoped — not adding new capabilities. Logging UI (Phase 2), sync (Phase 4), HealthKit (Phase 9), and the insights engine (Phases 5–8) are out of scope here.

</domain>

<decisions>
## Implementation Decisions

The user delegated all four discussion areas ("no preference") — the decisions below are research-backed defaults, locked for planning. The user can revisit any of them later (most are surfaced in Settings).

### Onboarding & Consent
- **D-01:** First-run is **warm, plain-language, and short** — validating in tone (many users are newly diagnosed, in pain, or fatigued), never clinical. Any education is optional/skippable.
- **D-02:** A single clear **up-front consent** to process health data **on-device + in private iCloud** is the baseline gate to use the app. No health data is processed until the user opts in (ONBD-01).
- **D-03:** [informational] Consent for **HealthKit** and any future optional processing is **just-in-time / purpose-specific** (requested in context, e.g. Phase 9), NOT bundled into the launch consent. Deferred to Phase 9 — not a Phase 1 deliverable (a deferral boundary).
- **D-04:** Consent is **withdrawable in Settings**, with a plain explanation of what withdrawal means for existing data.
- **D-05:** "Not medical advice / not a medical device" framing appears in onboarding AND is surfaced alongside insights in later phases (ONBD-02).

### Inclusive Language & Terminology
- **D-06:** **Non-gendered, non-assumptive language by default** everywhere — the app never assumes the user is a woman / uses "she" (ONBD-03).
- **D-07:** An **optional, lightweight** terminology + pronoun + display-name preference lives in **Settings** (e.g., choose how the app refers to bleeding: "period" / "bleeding" / "menstruation"). NOT a forced identity wall at onboarding.
- **D-08:** Default terminology is neutral ("bleeding" / "cycle") until the user customizes it.

### App Identity & App Store De-risk
- **D-09:** Scaffold with provisional display name **"Endo-App"** and a **placeholder bundle ID** (e.g. `com.endoapp.ios`) — both flagged to finalize when the user picks a real name and enrolls an Apple Developer team.
- **D-10:** Build **local-first with CloudKit sync behind a feature flag**, so development is never blocked on an Apple Developer account or App Review (matches the research-mandated local-only fallback).
- **D-11:** [informational] The App Store **5.1.3(ii) de-risk** (success criterion 5: throwaway TestFlight build + pre-submission review note for private-CloudKit health storage) is **planned but GATED** on the user enrolling in the Apple Developer Program. It is NOT a blocker for Phase 1 schema/consent work, and not a Phase 1 plan deliverable. → see External Dependencies.
- **D-12:** **Min deployment target iOS 18.** Scaffold a real `.xcodeproj` via **XcodeGen** (`project.yml`) — never an SPM executable target (GSD iOS rule).

### Privacy Policy & Publisher
- **D-13:** The **independent developer is the data controller** (personal/indie project); the policy names the developer + a contact email (placeholder: the user's email).
- **D-14:** A plain-language **in-app privacy screen is the source of truth** — works offline, no hosting dependency. It states: data lives on-device + in the user's private iCloud, there are no servers, and there is zero third-party sharing or analytics (ONBD-04).
- **D-15:** A **hosted privacy-policy URL** (same text) is needed at App Store submission time — flagged as a submission-time dependency, not now.

### Data Schema (locked by research — planner MUST follow)
- **D-16:** SwiftData with a **`VersionedSchema` from v1** (migration path exists before it's ever needed).
- **D-17:** **All attributes optional or defaulted; NO `@Attribute(.unique)`; all relationships optional with inverses; no `.deny` delete rules** — CloudKit makes the production schema effectively immutable/append-only.
- **D-18:** Model health data as **append-only, timestamped event records**, not mutable daily aggregates (CloudKit conflict resolution is last-writer-wins and uncustomizable).
- **D-19:** **Bleeding state and treatment/suppression state are FIRST-CLASS and independent of any "cycle."** Cycle-day/phase is a **derived, optional, confidence-scored** value — never assume a 28-day cycle (the #1 foundational pitfall).
- **D-20:** Use **app-generated UUIDs** as stable identifiers for idempotent upserts (since `.unique` is unavailable under CloudKit).
- **D-21:** **Private CloudKit database only** (`ModelConfiguration(cloudKitDatabase: .private)`). No shared/public DB. HealthKit-derived samples are NOT copied into synced storage (5.1.3 mitigation).
- **D-22:** Establish a **composition root + service protocols** now (Repository, sync, consent) so the insights engine and other services can be injected and tested in isolation in later phases.

### Claude's Discretion
- Exact onboarding screen copy, visual design, and the precise SwiftData field list are left to UI design + planning — gray *within* the locked decisions above.
- Final choice of placeholder bundle ID string and project folder layout is Claude's, provided it follows D-09 and D-12.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project & Requirements
- `.planning/PROJECT.md` — product definition, constraints, locked key decisions
- `.planning/REQUIREMENTS.md` — Phase 1 requirements: ONBD-01, ONBD-02, ONBD-03, ONBD-04, DATA-01

### Research (load before planning)
- `.planning/research/SUMMARY.md` — synthesized stack/architecture/pitfalls + recommended phasing
- `.planning/research/STACK.md` — SwiftData + private CloudKit, min iOS 18, no third-party deps
- `.planning/research/ARCHITECTURE.md` — MV + Repository, CloudKit/HealthKit schema constraints, composition root
- `.planning/research/PITFALLS.md` — the 4 FOUNDATIONAL pitfalls this phase must neutralize (cycle assumption, CloudKit rigidity, App Store 5.1.3(ii), GDPR consent + non-causal language)

### GSD platform reference
- `~/.claude/get-shit-done/references/ios-scaffold.md` — the XcodeGen (`project.yml` → `.xcodeproj`) scaffold pattern required for iOS apps (GSD anti-pattern #28)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — this is the first phase of a greenfield project (no Swift code yet).

### Established Patterns
- To be established here: XcodeGen `.xcodeproj`, SwiftUI **MV** (no per-screen ViewModels), a thin **Repository/service** layer behind protocols, and a future **Insights** Swift package boundary (created later but the seam is reserved now).

### Integration Points
- The composition root (D-22) is the integration seam every later phase plugs into (logging, sync, HealthKit import, insights, reporting).

</code_context>

<specifics>
## Specific Ideas

- Reference product is the official German **Endo-App (DiGA)**, but this app deliberately diverges: free, global, tracking-and-insights-first, wellness-not-medical-device.
- The privacy story is a feature: "your data never leaves your device and your private iCloud; no servers, no analytics, no sharing." The in-app privacy screen should say this plainly.

</specifics>

<deferred>
## Deferred Ideas

- **HealthKit consent UX** → Phase 9 (just-in-time consent, per D-03).
- **Hosted privacy-policy URL** → App Store submission time (per D-15).
- **Final app name + bundle ID** → when the user finalizes branding / enrolls an Apple Developer team (per D-09).
- **Turning on CloudKit sync** (vs the local-first default) → Phase 4, behind the feature flag established here (per D-10).

## External Dependencies (user action — not blocking Phase 1 planning)

- **Apple Developer Program enrollment (~$99/yr)** is required for: running on a physical device, TestFlight, and therefore the App Store **5.1.3(ii) de-risk** (D-11). Until enrolled, development proceeds local-first in the Simulator. The user should enrol before the de-risk step and before any release.

</deferred>

---

*Phase: 1-Foundations — Schema, Privacy Posture & Claims Policy*
*Context gathered: 2026-06-03*
