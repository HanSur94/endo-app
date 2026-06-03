# Phase 1: Foundations — Schema, Privacy Posture & Claims Policy - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-03
**Phase:** 1-Foundations — Schema, Privacy Posture & Claims Policy
**Areas discussed:** All four presented; user delegated all to Claude ("no preference")

---

## Gray areas presented

| Area | Options offered | Selected |
|------|-----------------|----------|
| Onboarding & consent feel | Warm/supportive vs minimal; blanket vs granular consent | Delegated |
| Inclusive language & terminology | Neutral defaults only / user-customizable / full identity setup | Delegated |
| App identity & App Store de-risk | Name/bundle ID to scaffold; Apple Developer account now vs defer | Delegated |
| Privacy policy & publisher | Data-controller identity; hosted URL vs in-app screen | Delegated |

**User's choice:** "No preference" — delegated all four areas to Claude's discretion, consistent with YOLO mode and a "just set it up" posture.

**Notes:** User had already made the major scope/privacy decisions during project initialization (free, on-device + private iCloud, wellness-not-medical-device, inclusive "for everyone"). Phase 1 details were left to research-backed defaults.

---

## Claude's Discretion

All four areas were decided by Claude using the project research. See CONTEXT.md `<decisions>` for the full set (D-01…D-22). Headline calls:
- Warm, short onboarding; one up-front on-device + iCloud consent; just-in-time HealthKit consent later; withdrawable.
- Non-gendered language by default + optional terminology/pronoun/display-name preference in Settings.
- Provisional name "Endo-App" + placeholder bundle ID; build local-first behind a CloudKit sync flag; App Store 5.1.3 de-risk gated on Apple Developer enrollment.
- Independent developer as data controller; in-app privacy screen as source of truth; hosted URL deferred to submission.
- Locked CloudKit-safe SwiftData schema rules (append-only, all-optional, no `.unique`, bleeding/treatment first-class, cycle-day derived/optional).

## Deferred Ideas

- HealthKit consent UX → Phase 9
- Hosted privacy-policy URL → App Store submission time
- Final app name + bundle ID → when branding finalized / Apple Developer team enrolled
- Enabling CloudKit sync (vs local-first default) → Phase 4
- **External dependency:** Apple Developer Program enrollment (~$99/yr) + Xcode + xcodegen install required before execution/build.
