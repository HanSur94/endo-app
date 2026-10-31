# Phase 25: Play Store Prep - Context

**Gathered:** 2026-07-10
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md §4/§5 + research/PITFALLS.md Play-policy findings)

<domain>
## Phase Boundary

The app clears Google Play's health-app gauntlet honestly and ships. Delivers AREL-03, AREL-04, AREL-05: complete + truthful Play release artifacts — the Health apps policy declaration, the Data safety form (no data collected, no data shared — all on-device), a privacy policy URL consistent across Console/rationale/website, store copy that passes the claims audit with the not-a-medical-device disclaimer in the FIRST paragraph; the organization-account (D-U-N-S) verification (a KICKOFF item); and targetSdk 36 compliance + a pre-launch report on an Android 13- and an Android 14+ device. This is the release phase — much of it is Play Console/human-gated; this phase produces the automatable artifacts + audits and clearly documents the human/console steps.

</domain>

<decisions>
## Implementation Decisions

Locked in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §4 (Phase 8) + §5 (invariants) + [.planning/research/PITFALLS.md](../../research/PITFALLS.md) (the Jan-2026 Play health-policy findings) + [.planning/research/FEATURES.md](../../research/FEATURES.md). Reuse Phases 17–24. Do not re-open. Key decisions:

### What is AUTOMATABLE here (build/test in this phase)
- **Store-listing copy + claims audit**: author the Play store listing copy (app title, short + full description, what's-new) as a registered, auditable copy surface, and extend the CopyAudit so the store copy passes the SAME ClaimsPolicy substring gate as in-app copy (no diagnostic/alarmist/gendered/imperative/false-precision language). The not-a-medical-device / "not medical advice, informational only" disclaimer MUST appear in the FIRST paragraph of the full description — a must-contain-in-first-paragraph test (mirrors the iOS claims discipline + the research "disclaimer in the first paragraph" Play requirement).
- **Data-safety ↔ manifest consistency**: a test/assertion proving the Data-safety declaration ("no data collected, no data shared") is corroborated by the manifest — NO INTERNET permission (the strongest artifact, already asserted since Phase 19), read-only health (no health WRITE, Phase 21), and no analytics/backend coordinate in the version catalog (the audit, since Phase 17). Encode the Data-safety answers as a committed machine-checkable artifact and cross-check it against the manifest/catalog so they can't drift.
- **targetSdk 36 compliance**: assert compileSdk/targetSdk == 36 (Play's API-36 requirement). Confirm the pre-launch-relevant config (no debuggable release, etc.) where checkable.
- **Health-apps declaration + Data-safety form + privacy-policy** as committed DOCUMENTS (the exact answers to give in Play Console), with the privacy-policy URL a single source referenced by the in-app rationale + the store listing (consistency assertion where possible).

### What is HUMAN / PLAY-CONSOLE-GATED (document, do NOT fake)
- **AREL-04 — organization developer account with D-U-N-S verification**: health-category apps CANNOT ship from an individual account (Jan-2026 policy), and individual→org conversion takes WEEKS. This is a KICKOFF item — surface it as a REQUIRED human action with clear instructions and a status the user confirms. Do NOT fabricate account state.
- **Play Console submission**: the actual Health apps declaration submission, the Data safety form entry, the store listing upload, uploading the privacy policy URL, and the pre-launch report run (AREL-05, on real Android 13- and 14+ devices) are Play Console / real-device actions. Produce the exact, ready-to-paste answers + a checklist; the user performs the Console steps. Also re-verify the Jan-2026 policy details against the live Play Console at submission time (the research flagged the secondary source).
- **App signing / release build config** (signing key, release build) — document; the user owns the keystore.

### Honesty (the whole point)
- Every Play declaration must be TRUE and corroborated by the codebase: no data collected/shared is TRUE because there is no INTERNET permission, no analytics, no backend, on-device-only storage (local-only v1 + user-initiated encrypted export). The not-a-medical-device positioning is consistent with all in-app copy (ClaimsPolicy). The store copy makes NO diagnostic claims. This phase's value is that the release artifacts are honest and codebase-corroborated, not marketing spin.

### Copy discipline
- Store copy registered + audited like in-app copy (dodge ClaimsPolicy substrings: should/use/her/start/treat/stop/take/here/where; no diagnostic/alarmist/gendered/imperative/false-precision). The first-paragraph disclaimer is a hard requirement.

### Claude's Discretion
- Exact artifact format (a `RELEASE.md` / `PLAY-LISTING.md` / `DATA-SAFETY.md` doc set + a Kotlin store-copy source registered in CopyAudit), the machine-checkable Data-safety↔manifest cross-check design, and how much of the pre-launch checklist to encode as tests vs docs — Claude's discretion. Keep every claim TRUE + codebase-corroborated; clearly separate automatable-and-green from human-gated.

</decisions>

<code_context>
## Existing Code Insights

### Established from Phases 17–24 (the corroborating evidence)
- NO INTERNET permission (Phase 19 ManifestPermissionsTest — the Data-safety "no data shared" anchor). Read-only Health Connect, zero health WRITE (Phase 21). Version-catalog audit: no analytics/auth/backend/networking coordinate (Phase 17). Local-only storage + user-initiated encrypted export (Phase 23, no cloud). ClaimsPolicy + CopyAudit over ALL in-app copy (Phase 18+), not-medical-advice framing (Phase 19 Disclaimer), no diagnostic claims anywhere. targetSdk 36 (Phase 18). Notification-free except the opt-in reminder.
- The CopyAudit harness (register store copy), the ManifestPermissionsTest (extend for the Data-safety cross-check), the version-catalog audit. Android SDK installed; `./gradlew :app:assembleDebug :app:lintDebug` builds real.

### Port source (read-only)
- iOS release artifacts: the v1.0/v1.1 audit docs + the App Store 5.1.3 review notes + PrivacyInfo.xcprivacy — the Android Data-safety form is the analog. iOS ClaimsPolicy / Disclaimer / PrivacyPolicyText framing carries over.

### Integration points
- This is the terminal phase — it consumes the invariants every prior phase established and packages the honest release. After this, the milestone lifecycle (audit → complete → cleanup) runs.

</code_context>

<specifics>
## Specific Ideas

The phase's signature: every Play declaration is TRUE and CODEBASE-CORROBORATED — "no data collected/shared" is provable from the no-INTERNET manifest + no-analytics catalog + local-only storage; the not-a-medical-device disclaimer sits in the first paragraph of the store copy (must-contain test) and matches all in-app ClaimsPolicy copy. The automatable artifacts (store-copy audit, Data-safety↔manifest cross-check, targetSdk assertion) are green; the human/console-gated items (org-account D-U-N-S, the actual Console submission, the pre-launch report on real devices) are documented as a precise ready-to-execute checklist, NOT faked.

</specifics>

<deferred>
## Deferred Ideas

- The actual Play Console submission + pre-launch report run (real devices) → human/console-gated, documented here as a checklist for the user.
- Post-parity Future Requirements (biometric app lock, recents-thumbnail hiding, Drive appDataFolder sync, widgets) → not this milestone.
- App signing keystore management → the user owns it (documented).
</deferred>
