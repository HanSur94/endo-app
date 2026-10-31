# Phase 25: Play Store Prep - Research

**Researched:** 2026-07-10
**Domain:** Google Play release compliance for a privacy-first, local-only Android health app (Health apps declaration, Data safety form, store listing + medical disclaimer, privacy policy, targetSdk/pre-launch) — the FINAL phase of the v2.0 Android port
**Confidence:** MEDIUM-HIGH (all load-bearing Play policy claims re-verified against official `support.google.com` / `developer.android.com` this session; two specifics — the API-36/Aug-2026 target-SDK deadline and the "first paragraph" disclaimer placement — could not be confirmed on the official pages and are flagged ASSUMED with a safe fallback)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**AUTOMATABLE here (build/test in this phase):**
- **Store-listing copy + claims audit** — author the Play store listing copy (title, short + full description, what's-new) as a registered, auditable copy surface; extend CopyAudit so store copy passes the SAME ClaimsPolicy substring gate as in-app copy. The not-a-medical-device / "not medical advice, informational only" disclaimer MUST appear in the FIRST paragraph of the full description — a must-contain-in-first-paragraph test.
- **Data-safety ↔ manifest consistency** — a test proving the Data-safety declaration ("no data collected, no data shared") is corroborated by the manifest: NO INTERNET permission (Phase 19), read-only health / no health WRITE (Phase 21), no analytics/backend coordinate in the version catalog (Phase 17). Encode the Data-safety answers as a committed machine-checkable artifact cross-checked against the manifest/catalog so they can't drift.
- **targetSdk 36 compliance** — assert compileSdk/targetSdk == 36; confirm pre-launch-relevant config (no debuggable release) where checkable.
- **Health-apps declaration + Data-safety form + privacy-policy** as committed DOCUMENTS (the exact Console answers), with the privacy-policy URL a single source referenced by the in-app rationale + store listing (consistency assertion where possible).

**HUMAN / PLAY-CONSOLE-GATED (document, do NOT fake):**
- **AREL-04 — organization developer account with D-U-N-S verification** — health-category apps cannot ship from an individual account; individual→org conversion takes WEEKS. KICKOFF item. Surface as a REQUIRED human action with a status the user confirms. Do NOT fabricate account state.
- **Play Console submission** — the actual Health apps declaration submission, Data safety form entry, store listing upload, privacy policy URL entry, and the pre-launch report run (AREL-05, on real Android 13- and 14+ devices) are Console / real-device actions. Produce exact ready-to-paste answers + a checklist; the user performs the Console steps. Re-verify the Jan-2026 policy details against the live Console at submission time.
- **App signing / release build config** — the user owns the keystore; document.

**Honesty (the whole point):** Every Play declaration must be TRUE and corroborated by the codebase. No data collected/shared is TRUE because there is no INTERNET permission, no analytics, no backend, on-device-only storage. The not-a-medical-device positioning is consistent with all in-app copy (ClaimsPolicy). Store copy makes NO diagnostic claims.

**Copy discipline:** Store copy registered + audited like in-app copy (dodge ClaimsPolicy substrings: should/use/her/start/treat/stop/take/here/where; no diagnostic/alarmist/gendered/imperative/false-precision). The first-paragraph disclaimer is a hard requirement.

### Claude's Discretion
- Exact artifact format (a `RELEASE.md` / `PLAY-LISTING.md` / `DATA-SAFETY.md` doc set + a Kotlin store-copy source registered in CopyAudit), the machine-checkable Data-safety↔manifest cross-check design, and how much of the pre-launch checklist to encode as tests vs docs. Keep every claim TRUE + codebase-corroborated; clearly separate automatable-and-green from human-gated.

### Deferred Ideas (OUT OF SCOPE)
- The actual Play Console submission + pre-launch report run (real devices) — human/console-gated, documented here as a checklist.
- Post-parity Future Requirements (biometric app lock, recents-thumbnail hiding, Drive appDataFolder sync, widgets) — not this milestone.
- App signing keystore management — the user owns it (documented).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AREL-03 | Play release artifacts complete + truthful — Health apps declaration, Data safety form (no data collected/shared), privacy policy URL consistent everywhere, store copy passes the claims audit with the disclaimer in the first paragraph | §Standard Stack (reuse CopyAudit/Manifest/Catalog harnesses); §Architecture Patterns (StoreListingCopy source + must-contain test; DataSafetyDeclaration ↔ manifest cross-check; PRIVACY_POLICY_URL single source); §Health apps policy; §Data safety form; §Store listing + disclaimer; §Privacy policy |
| AREL-04 | Play developer account verified organization-type (D-U-N-S) at milestone kickoff — health apps cannot ship from individual accounts | §Health apps policy (org-account + D-U-N-S — VERIFIED official for Medical apps); §Automatable-vs-Human split (this is a documented human/kickoff gate, confirmed green here) |
| AREL-05 | targetSdk 36 compliance and a pre-launch report pass on both an Android 13- and an Android 14+ device | §targetSdk + pre-launch (targetSdk 36 assertion automatable; pre-launch on real devices human-gated); §Environment Availability |
</phase_requirements>

## Summary

This is a **release-packaging + audit** phase, not a feature phase. Its value is that every Google Play declaration the user submits is TRUE and mechanically corroborated by the codebase, and that the automatable half is green in the repo while the human/Console/real-device half is documented as a precise, non-faked checklist. Almost all of the code-side machinery already exists from Phases 17–24: `CopyAudit` + `ClaimsPolicy`/`ForbiddenTerms` (the substring gate), `ManifestPermissionsTest` (no-INTERNET / no-health-WRITE / read-only anchor), `VersionCatalogAuditTests` (no analytics/auth/backend coordinate), `Disclaimer`/`PrivacyPolicyText` (single-source framing), and `BackupRulesTest`. This phase composes them into release artifacts and adds four small new test surfaces plus a committed doc set.

Re-verifying the Jan-2026 Play health-policy findings (which the prior research flagged as partly secondary-source) against official Google pages this session **confirmed the load-bearing claims**: health apps that access health data are in scope of the Health Content and Services policy even when they make no medical claims [VERIFIED: support.google.com/16679511]; non-medical-device apps must carry the disclaimer "not a medical device and does not diagnose, treat, cure, or prevent any [medical] condition" in the store description [VERIFIED: support.google.com/16679511]; **Medical apps require an organization account with a D-U-N-S number** [VERIFIED: support.google.com/13634885]; and on-device-only access/processing is explicitly NOT "collection" for the Data safety form [VERIFIED: support.google.com/10787469]. A truthful all-"No" Data safety form is therefore correct for this app, and the no-INTERNET manifest is its strongest corroborating artifact.

Two details did **not** confirm on the official pages and are flagged: the official target-SDK page still documents the **API-35 / Aug-31-2025** requirement (not an API-36 / 2026 deadline) — but because the requirement is a *floor* ("target API level N **or higher**"), the repo's existing `targetSdk = 36` is compliant regardless of whether the 2026 floor is 35 or 36, so no code change is needed and the risk is nil. And the "**first paragraph**" placement of the disclaimer is a secondary-source specific; the official policy requires it "in the app description" without naming the first paragraph — so putting it first *exceeds* the requirement with zero downside.

**Primary recommendation:** Add a registered `StoreListingCopy` object (audited + first-paragraph disclaimer reused verbatim from `Disclaimer.text`), a committed machine-checkable `DataSafetyDeclaration` cross-checked against the manifest/catalog, a `targetSdk == 36` assertion, and a single `PRIVACY_POLICY_URL` constant — plus a `RELEASE.md` doc set with the exact ready-to-paste Console answers and a human/device checklist for the org-account, submission, and pre-launch steps. Fake nothing that lives in the Console.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Store-listing copy authoring + claims audit | Build/test artifacts (`:app` content + test) | — | Copy is a registered `AuditableCopy` source; the audit is a JVM unit test, no runtime surface |
| Data-safety declaration corroboration | Build/test artifacts + Manifest | Version catalog | The declaration is a committed constant cross-checked against the merged manifest + `libs.versions.toml` — both static artifacts |
| targetSdk / release build config | Gradle build config (`app/build.gradle.kts`) | — | Compile-time properties; asserted by scanning the build script / BuildConfig |
| Privacy policy content (offline) | `:app` content (`PrivacyPolicyText`) | Rationale Activity | Already on-device; this phase adds the single URL constant + hosted-page consistency doc |
| Health apps declaration + Data safety form + store listing submission | Play Console (human) | — | Console-only; repo holds the ready-to-paste answers, never the submitted state |
| Org account + D-U-N-S verification | Play Console / Dun & Bradstreet (human, kickoff) | — | Account-level identity verification, weeks of latency; cannot be faked or automated |
| Pre-launch report (AREL-05) | Google test lab, real devices (human) | — | Runs on Google-hosted physical Android 13- and 14+ devices after upload |

## Standard Stack

This phase installs **no new external packages**. It reuses the harnesses established in Phases 17–24 and adds Kotlin source + tests + docs only.

### Core (reused, already in the repo)
| Component | Location | Purpose | Why Standard |
|-----------|----------|---------|--------------|
| `CopyAudit` / `CopyAuditRegistry` / `AuditableCopy` | `app/.../content/` + `app/src/test/.../CopyAuditTest.kt` | The substring gate every registered user-facing string passes | The load-bearing copy-discipline mechanism (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`); store copy plugs straight in [VERIFIED: codebase] |
| `ClaimsPolicy` + `:insights` `ForbiddenTerms` | `app/.../content/ClaimsPolicy.kt` | causal/imperative/alarmist/gendered/precision substring lists, drift-guarded single source | Reused not forked; Phase 17 `ClaimsPolicyDriftTests` keeps it faithful to the Swift source [VERIFIED: codebase] |
| `Disclaimer.text` | `app/.../content/Disclaimer.kt` | The ONE "not medical advice / not a medical device… does not diagnose, treat, cure, or prevent any condition" string, exempt from the substring scan, pinned by must-contain | Reuse verbatim as the store description's first paragraph → drift-proof + satisfies Play's required wording [VERIFIED: codebase] |
| `PrivacyPolicyText` | `app/.../content/PrivacyPolicyText.kt` | Offline local-only privacy policy body (on-device, no servers, no accounts, no analytics) | The content the hosted URL + rationale screen must match [VERIFIED: codebase] |
| `ManifestPermissionsTest` | `app/src/test/.../ManifestPermissionsTest.kt` | DOM-parses the manifest; asserts NO INTERNET, NO health WRITE, the read-only allow-list | The Data-safety form's strongest artifact; extend/reuse for the cross-check [VERIFIED: codebase] |
| `VersionCatalogAuditTests` | `insights/src/test/.../VersionCatalogAuditTests.kt` | Fails the build if any analytics/auth/networking coordinate appears in `libs.versions.toml` | Corroborates "no third-party data collection" [VERIFIED: codebase] |
| `BackupRulesTest` + `@xml/data_extraction_rules` / `@xml/full_backup_content` | `app/src/main/res/xml/` | Excludes the health DB from Auto Backup | Supports the privacy posture; already green (Phase 18) [VERIFIED: codebase] |

### Supporting (new, this phase — all first-party Kotlin/test, zero deps)
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `StoreListingCopy : AuditableCopy` | Title / short / full-description body / what's-new, registered into `CopyAuditRegistry.sources` | Store copy must pass the SAME gate as in-app copy |
| `StoreListingCopyTest` | Length assertions (title ≤30, short ≤80, full ≤4000) + first-paragraph disclaimer must-contain | Encodes Play's field limits + the disclaimer-placement invariant |
| `DataSafetyDeclaration` (committed constant) | Machine-checkable Data-safety answers (`collectsData=false`, `sharesData=false`, `dataTypes=[]`) | The single source the cross-check + the `DATA-SAFETY.md` doc both read |
| `DataSafetyManifestCrossCheckTest` | If declaration claims no collection/sharing → assert no INTERNET, no analytics coordinate, no health WRITE | Makes the form + manifest unable to drift apart |
| `TargetSdkComplianceTest` | Assert `targetSdk == 36` (and `compileSdk == 36`, `minSdk == 26`); assert no `isDebuggable = true` in release | AREL-05 automatable half |
| `PRIVACY_POLICY_URL` constant + `PrivacyPolicyUrlConsistencyTest` | One committed URL string, referenced everywhere a URL is shown; asserted single-source | Play requires the same policy URL in Console + rationale + website |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Reusing `Disclaimer.text` as the first paragraph | A separate hand-typed store disclaimer string | Rejected — a second copy drifts; reuse is single-source and already carries Play's exact required phrasing |
| `TargetSdkComplianceTest` scanning `build.gradle.kts` text | Asserting `Build.VERSION` at runtime via Robolectric | Robolectric reports the *emulated* SDK, not `targetSdk`; a static scan of the build script (or a generated `BuildConfig`/committed constant) is the honest, red-capable check |
| Committed docs + ready-to-paste answers | An automated Console API submission | Rejected by the honesty rule — the Console state is human-owned; the Play Developer API does not cover the Health apps declaration / Data safety form submission for this flow |

**Installation:** None. `git`-tracked Kotlin + tests + `.md` docs only.

**Version verification:** No package versions to verify — the phase adds no `libs.versions.toml` coordinate. The `VersionCatalogAuditTests` guard remains the audit surface and must stay green (it forbids any analytics/auth/backend coordinate ever appearing).

## Package Legitimacy Audit

> This phase installs **no external packages**. It adds committed docs, a Kotlin store-copy source, a committed `DataSafetyDeclaration` constant, and unit tests — all first-party, all in-repo.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| _(none — no new dependency)_ | — | — | — | — | OK | N/A |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

The zero-dependency posture is itself a compliance artifact: `VersionCatalogAuditTests` (Phase 17) already fails the build if any analytics/auth/networking coordinate appears, which is exactly what corroborates the truthful Data safety form.

## Architecture Patterns

### System Architecture Diagram

```
                        ┌───────────────────────────────────────────────┐
                        │  IN-REPO (automatable, green tests)            │
                        │                                               │
  Disclaimer.text ──────┼──► StoreListingCopy ──► CopyAuditTest         │
  (single source)       │        │ (registered)   (substring gate)      │
                        │        └──► StoreListingCopyTest               │
                        │             (len ≤30/80/4000 + first-para      │
                        │              disclaimer must-contain)          │
                        │                                               │
  DataSafetyDeclaration ┼──► DataSafetyManifestCrossCheckTest ◄──┐      │
  (collects=false,      │                                        │      │
   shares=false)        │     AndroidManifest.xml ───────────────┤      │
                        │     (no INTERNET / no WRITE / read-only)│      │
                        │     libs.versions.toml ────────────────┘      │
                        │     (no analytics/auth/backend)               │
                        │                                               │
  app/build.gradle.kts ─┼──► TargetSdkComplianceTest (==36, no debuggable)│
                        │                                               │
  PRIVACY_POLICY_URL ───┼──► PrivacyPolicyUrlConsistencyTest            │
  (one constant)        │     + PrivacyPolicyText (offline body)        │
                        └───────────────────┬───────────────────────────┘
                                            │ ready-to-paste answers
                                            ▼
                        ┌───────────────────────────────────────────────┐
                        │  HUMAN / PLAY CONSOLE (documented checklist)   │
                        │  RELEASE.md · PLAY-LISTING.md · DATA-SAFETY.md │
                        │  HEALTH-DECLARATION.md · PRELAUNCH-CHECKLIST.md│
                        │                                               │
                        │  • Org account + D-U-N-S (kickoff, confirm)   │
                        │  • Health apps declaration (paste answers)     │
                        │  • Data safety form (paste all-"No")          │
                        │  • Store listing upload (paste copy)          │
                        │  • Privacy policy URL entry (host web page)    │
                        │  • Pre-launch report on Android 13- & 14+     │
                        │  • App signing keystore (user-owned)          │
                        └───────────────────────────────────────────────┘
```

### Recommended Project Structure
```
android/app/src/main/kotlin/com/endoapp/app/content/
├── StoreListingCopy.kt        # AuditableCopy: title/short/full/whatsNew + reuse Disclaimer.text as para 1
├── DataSafetyDeclaration.kt   # committed machine-checkable answers (collects=false, shares=false, [])
└── (PrivacyPolicyText.kt)     # ADD: const PRIVACY_POLICY_URL single source

android/app/src/test/kotlin/com/endoapp/app/content/
├── StoreListingCopyTest.kt            # lengths + first-paragraph disclaimer must-contain (+ registered-in-catalog)
├── DataSafetyManifestCrossCheckTest.kt# declaration ⇔ manifest/catalog, red-capable
├── TargetSdkComplianceTest.kt         # targetSdk/compileSdk==36, minSdk==26, no debuggable release
└── PrivacyPolicyUrlConsistencyTest.kt # single-source URL referenced everywhere

.planning/phases/25-play-store-prep/   (or android/release/ — Claude's discretion)
├── RELEASE.md                 # master checklist: automatable-green vs human-gated, in order
├── PLAY-LISTING.md            # exact title/short/full/what's-new to paste
├── DATA-SAFETY.md             # the exact all-"No" answers + the corroboration table
├── HEALTH-DECLARATION.md      # the Health apps declaration answers + per-HC-type justifications
└── PRELAUNCH-CHECKLIST.md     # AREL-05 device matrix + AREL-04 org-account confirmation
```

### Pattern 1: Reuse the single-source disclaimer as the store description's first paragraph
**What:** The store full-description's first paragraph IS `Disclaimer.text` (verbatim), which already reads "…not medical advice and not a medical device. It does not diagnose, treat, cure, or prevent any condition…".
**When to use:** Always — it satisfies Play's required non-medical-device wording [VERIFIED: support.google.com/16679511], keeps the claim single-source (no drift), and the substring-scan exemption pattern already exists for exactly this string.
```kotlin
// StoreListingCopy.kt — the first paragraph is the exempt disclaimer; the rest is audited.
object StoreListingCopy : AuditableCopy {
    // EXEMPT from the substring scan (contains "treat","cure","diagnose","prevent" as negations),
    // pinned instead by StoreListingCopyTest.firstParagraph must-contain — mirrors Disclaimer/ReportCopy.FOOTER.
    val fullDescriptionFirstParagraph: String = Disclaimer.text

    // AUDITED body paragraphs (registered) — must pass CopyAudit's substring gate.
    val fullDescriptionBody: List<String> = listOf(/* audited paragraphs, see §Store copy */)

    val title: String = "…"       // ≤30
    val shortDescription: String = "…"  // ≤80
    val whatsNew: String = "…"    // audited

    val fullDescription: String
        get() = (listOf(fullDescriptionFirstParagraph) + fullDescriptionBody).joinToString("\n\n")

    // Registered strings EXCLUDE the exempt disclaimer paragraph (it is pinned by its own must-contain).
    override val auditableStrings: List<String> =
        listOf(title, shortDescription, whatsNew) + fullDescriptionBody
}
```

### Pattern 2: Data-safety declaration cross-checked against the manifest so they can't drift
**What:** A committed constant encodes the exact form answers; a test asserts the answers stay consistent with the merged manifest + catalog.
**When to use:** The Data safety form is the single most rejection-prone artifact for a "nothing leaves the device" app; this makes the truthful answer mechanically defended.
```kotlin
object DataSafetyDeclaration {
    const val collectsData = false          // "collected" = transmitted off-device [VERIFIED: support.google.com/10787469]
    const val sharesData = false
    val dataTypesCollected: List<String> = emptyList()
    val dataTypesShared: List<String> = emptyList()
}

// DataSafetyManifestCrossCheckTest (JUnit4, :app testDebugUnitTest) — red-capable:
// IF collectsData==false && sharesData==false THEN:
//   assert manifest has NO android.permission.INTERNET   (reuse ManifestPermissionsTest's DOM parse)
//   assert libs.versions.toml has NO analytics/auth/backend coordinate (reuse VersionCatalogAudit scan)
//   assert manifest has NO android.permission.health.WRITE_*
// Injecting INTERNET, or flipping collectsData=true while the manifest has no network path, turns it red.
```

### Anti-Patterns to Avoid
- **Over-declaring on-device data as "collected":** Play defines *collected* as transmitted off-device; on-device-only access is explicitly NOT collection [VERIFIED: support.google.com/10787469]. Answering "Yes" would put a false "shares health data" label on a privacy-first app and invite review questions. Answer all-"No".
- **A second, hand-typed medical disclaimer:** drifts from `Disclaimer.text`. Reuse the single source.
- **Faking Console state** (org-account verified, form submitted, pre-launch passed) in the repo: forbidden by the honesty rule — these are documented human confirmations, not committed booleans that pretend to be green.
- **`ACTION_SEND` / SAF export scaring the form to "Yes":** user-initiated sharing to a user-chosen destination is NOT collection/sharing [CITED: support.google.com/10787469 on-device + user-initiated]. Keep the form all-"No".
- **Putting the disclaimer below the fold:** even though "first paragraph" is a secondary-source specific, buried disclaimers are a known rejection trigger; place it first.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Store-copy claims checking | A new substring scanner for store copy | Register `StoreListingCopy` in `CopyAuditRegistry` | The gate already exists, is drift-guarded, and is the enforcement mechanism |
| "No data leaves device" proof | A prose assertion in the doc | `ManifestPermissionsTest` (no INTERNET) + `VersionCatalogAuditTests` (no analytics) | Mechanical, red-capable corroboration is what defeats Play's automated form-vs-reality diff |
| Disclaimer wording | A fresh sentence per surface | `Disclaimer.text` single source | Play requires exact non-medical-device wording; reuse guarantees it and prevents drift |
| Privacy policy content | Re-writing for the website | `PrivacyPolicyText.body` as the canonical content | The hosted page must match the in-app/rationale content; one source avoids inconsistency rejection |
| Backup exclusion | New rules | `@xml/data_extraction_rules` + `BackupRulesTest` (Phase 18) | Already green; just cite it in the artifacts |

**Key insight:** The compliance story is already *built* as invariants across Phases 17–24. This phase's job is to *compose and cite* them into release artifacts and add four small guards — not to re-implement any of the enforcement.

## Google Play Health apps policy (AREL-03 / AREL-04)

**Who must declare — in scope?** YES. "If your app is not primarily a health app, but has health-related features and accesses health data, it is still in scope of the Health App policy." A self-tracking wellness tracker that reads Health Connect and makes no medical claims IS in scope and must complete the Health apps declaration, post a publicly accessible privacy policy, and avoid unnecessary permissions [VERIFIED: support.google.com/16679511]. All developers with a published app must complete the Health apps declaration regardless [VERIFIED: support.google.com/14738291].

**The declaration form.** The official help page describes the *process* (declare which health features from predefined categories → answer follow-up questions per feature → expandable detail sections) but does not publish exact field labels; the precise fields must be read live in Console at submission time [CITED: support.google.com/14738291]. For this app the answers are: it is a self-tracking/wellness app; it is NOT a medical device; it accesses Health Connect **read-only** for menstruation, sleep, exercise, and nutrition, plus history. Each requested type must be provably essential to a visible feature — reviewers compare the declaration, the manifest permissions, and observed behavior [CITED: PITFALLS Pitfall 2; corroborated by support.google.com/16679511 "avoid unnecessary permissions"].

**Per-Health-Connect-data-type justification** (reuse the in-app dual-rationale copy from Phase 21 — same story, do not diverge):

| HC read permission | Visible feature the reviewer can find | Justification |
|--------------------|---------------------------------------|---------------|
| `READ_MENSTRUATION` | Cycle logging + cycle-phase views | Primary function: menstrual cycle tracking + phase mapping |
| `READ_SLEEP` | Sleep as a factor in trend/association views | Correlate sleep with symptoms in the user's own data |
| `READ_EXERCISE` | Exercise as a factor | Correlate activity with symptoms |
| `READ_NUTRITION` | Food/nutrition factor logging | Correlate diet factors with symptoms (name the food-logging feature explicitly — `NutritionRecord` invites the essentiality question) |
| `READ_HEALTH_DATA_HISTORY` | Longitudinal cycle history import | The app's whole value is longitudinal; without it the import silently truncates to ~30 days |

**Organization account + D-U-N-S (AREL-04).** VERIFIED official: an organization account is mandatory for several app categories including "Medical apps and Human Subjects Research apps"; to create an organization account you must have a D-U-N-S number (a unique nine-digit Dun & Bradstreet identifier, free to request), used by Google Play to verify identity [VERIFIED: support.google.com/13634885].

> **Honesty flag on classification [ASSUMED]:** The official enumerated list names "**Medical apps** and Human Subjects Research apps." Whether *this* app — explicitly a wellness/self-tracking tool that is NOT a medical device — is classified by Play as a "Medical app" is **not certain** from public docs. The prior research's secondary source stated all health-category apps require org accounts; the official page is narrower. **Safe posture (recommended): treat the org account + D-U-N-S as REQUIRED and start it at milestone kickoff** — because (a) the app reads special-category health data and completes the Health apps declaration, (b) individual→org recovery takes weeks and would gate the ship date, and (c) worst case the user holds an org account they did not strictly need. Confirm the exact classification live in Console; do not defer the D-U-N-S request on the hope it is unnecessary.

**This phase's role for AREL-04:** it is a **human/kickoff gate**, confirmed (not created) here. Provide a checklist doc with the D-U-N-S lookup/request steps and a status line the user confirms (`ORG_ACCOUNT_VERIFIED: yes/no`). Do NOT commit a fabricated "verified" state.

## Data safety form (AREL-03) — the exact all-"No" answers

**Rule:** "Collected" = data transmitted off the device; on-device-only access/processing does NOT need to be disclosed as collected; ephemeral off-device processing is also exempt [VERIFIED: support.google.com/10787469, developer.android.com/privacy-and-security/declare-data-use]. This app never transmits off-device (no INTERNET permission), so the truthful answer is all-"No".

**Ready-to-paste answers:**

| Data safety question | Answer | Corroboration in repo |
|----------------------|--------|-----------------------|
| Does your app collect or share any of the required user data types? | **No** | `ManifestPermissionsTest` (no INTERNET) + `VersionCatalogAuditTests` (no analytics/backend) |
| Is all of the user data collected by your app encrypted in transit? | N/A — no data leaves the device (nothing in transit) | no INTERNET permission |
| Do you provide a way for users to request that their data be deleted? | Yes — delete-day cascade + withdraw-consent purge are on-device (Phases 18–20); note the local-only nature | delete-day `@Transaction` + consent withdrawal |
| Data types collected | **none** | `DataSafetyDeclaration.dataTypesCollected = emptyList()` |
| Data types shared | **none** | `DataSafetyDeclaration.dataTypesShared = emptyList()` |

**How on-device-only is declared:** by answering "No" to the top-level collect/share question — Play's own guidance is that locally-processed, never-transmitted data is not "collected," so there is no data-type list to fill [VERIFIED: support.google.com/10787469]. The **no-INTERNET manifest is the single strongest corroborating artifact** — Play runs automated form-vs-reality checks, and an app with no network permission and no analytics coordinate has nothing to contradict an all-"No" form.

**Note on Health Connect + user-initiated export:** reading Health Connect is local IPC (not off-device transmission); the encrypted SAF export and PDF `ACTION_SEND` share are user-initiated to a user-chosen destination — neither counts as collection/sharing. Do not let them push the form to "Yes" [CITED: support.google.com/10787469]. Health Connect data must still be described in the privacy policy per the User Data policy.

## Store listing + disclaimer (AREL-03)

**Field limits** [VERIFIED: support.google.com/13393723, /9859152]:
- App title: **≤ 30 characters**
- Short description: **≤ 80 characters**
- Full description: **≤ 4,000 characters**

**Required disclaimer.** Non-medical-device health apps must include "not a medical device and does not diagnose, treat, cure, or prevent any [medical] condition" in the app description, and remind users to consult a healthcare professional [VERIFIED: support.google.com/16679511]. `Disclaimer.text` already carries this exact phrasing plus the "have better conversations with your clinician" reminder — reuse it verbatim as the first paragraph.

> **[ASSUMED] "first paragraph" placement:** The official policy requires the disclaimer "in the app description" but does **not** specify the first paragraph; the "first paragraph" requirement comes from the secondary source (myappmonitor). Placing it first exceeds the requirement with zero downside and is a locked CONTEXT decision — do it, and encode a must-contain-in-first-paragraph test, but know the *strict* official requirement is only "present in the description."

**Forbidden claims:** no diagnosis/treatment/cure/prevention claims, no alarmist/certainty language, no diagnostic framing [VERIFIED: support.google.com/16679511 + ClaimsPolicy]. The store copy (except the exempt disclaimer paragraph) must pass the same `CopyAudit` substring gate — dodging `should/use/her/start/treat/stop/take/here/where`, gendered tokens, and false-precision.

**Substring traps to watch when authoring** (from `ClaimsPolicy`/MEMORY): `"her"` ⊂ here/where/other/together/rather; `"use"` ⊂ user/uses/used/**because**/cause; `"take"` ⊂ mistake; avoid `should/start/stop/treat`. So: no "users" (say "you"/"people"), no "because", no "other/together/where/here".

**Recommended ClaimsPolicy-safe store copy** (draft — every string still goes through the real `CopyAuditTest`; hand-verified against the substring lists below):

- **Title (≤30):** `Endo: Endometriosis Tracker` (27 chars) — no forbidden substring.
- **Short description (≤80):** `Private, on-device logging for symptoms, cycle, and daily factors. Yours only.` (77 chars) — no forbidden substring.
- **Full description — paragraph 1 (EXEMPT, `Disclaimer.text` verbatim):**
  > "Endo-App is an informational self-tracking tool. It is not medical advice and not a medical device. It does not diagnose, treat, cure, or prevent any condition. The patterns it surfaces come from your own data and are meant to help you understand your body and have better conversations with your clinician — not to replace professional care."
- **Full description — audited body paragraphs (must pass the gate):**
  1. `Endo-App helps you keep a private record of how you feel day to day — pain, bleeding, mood, sleep, food, and movement — so patterns in your own data become easier to see over time.`
  2. `Everything lives on your phone. No account, no sign-in, no cloud, and no third-party tracking. Your health data leaves your phone only if you choose to export it yourself.`
  3. `See gentle, plain-language views of your trends, cycle phases, and possible associations in your own data — always informational, worth discussing with your clinician.`
  4. `Share a clean, printable report with your clinician when it helps.`
- **What's-new (audited):** `First release of Endo-App for Android: private, on-device symptom and cycle logging with plain-language insights.`

*(All draft strings were checked by hand against causal/imperative/alarmist/gendered/precision + the `her/use/take/should/start/stop/treat/here/where` traps and are clean; the `CopyAuditTest` catalog scan is the actual gate at execution time.)*

### 3b. Health Connect store-listing specifics
Beyond the in-app dual-rationale already built in Phase 21, Health-Connect-specific listing requirements are: (1) the Health apps declaration's **per-data-type justification** (table above) — reuse the Phase-21 rationale story so the declaration, manifest, and app behavior tell one story [CITED: PITFALLS Pitfall 2]; (2) the **permissions-rationale screen** linked from the Health Connect permission dialog must render the same privacy-policy content as the store/website — already wired to `PermissionsRationaleActivity` rendering `PrivacyPolicyText`, and reachable via BOTH declared surfaces (Android 13- `ACTION_SHOW_PERMISSIONS_RATIONALE` activity + Android 14+ `VIEW_PERMISSION_USAGE` alias) [VERIFIED: codebase manifest + 21-05-SUMMARY]. No new store-listing artifact is required beyond the declaration answers and the URL consistency.

## Privacy policy (AREL-03)

**Requirement:** apps requesting sensitive/health permissions must link a privacy policy on the store listing AND within the app; it must be on an **active, publicly accessible, non-geofenced URL — no PDFs — and non-editable**, applying specifically to this app and covering user privacy [VERIFIED: support.google.com — store listing / User Data policy].

**What the policy must state** (for an on-device health app): data is stored on-device only; no servers; no accounts; no third-party sharing or analytics; Health Connect data is read-only and described; the developer is the data controller with a contact; the user gives explicit consent and can withdraw it. `PrivacyPolicyText.body` already states all of this [VERIFIED: codebase].

**Consistency (the hard requirement):** the privacy-policy URL and content must be identical across (a) Play Console, (b) the in-app rationale surface (`PermissionsRationaleActivity` → `PrivacyPolicyText`), and (c) the hosted website. 

**This phase's split:**
- **Automatable:** add ONE `const val PRIVACY_POLICY_URL` (single source), referenced everywhere a URL is displayed; `PrivacyPolicyUrlConsistencyTest` asserts there is exactly one source and it is non-blank/well-formed. Optionally assert the hosted-content *intent* (that `PrivacyPolicyText.body` is the canonical text the page must mirror) as a documented contract.
- **Human-gated:** actually hosting the web page (matching `PrivacyPolicyText.body`) at that URL and entering it in Console. A hermetic test cannot fetch the live URL (no network in tests / no INTERNET in the app), so the page-content match is a documented human check.

> **[ASSUMED]** The repo currently has NO privacy-policy URL constant (only the offline `PrivacyPolicyText.body` + `contactEmail`). This phase must ADD it and decide the hosting location (e.g. a GitHub Pages page) — a human step. Flag `PRIVACY_POLICY_URL` as a value the user must supply before submission.

## targetSdk + pre-launch (AREL-05)

**Target API level.** VERIFIED official as of this session: the current documented requirement is **API 35 (Android 15) or higher for new apps and updates, deadline Aug 31 2025** [VERIFIED: developer.android.com/google/play/requirements/target-sdk]. The official page has **not** been updated to document an API-36 / Aug-2026 requirement.

> **[ASSUMED] API-36 / 2026 deadline:** The prior research (CLAUDE.md/STACK) states "targetSdk 36 (Play deadline 2026-08-31)". This follows Google's well-established annual cadence (within ~1 year of a major release the floor rises by one; Android 16 = API 36, released 2025 → an ~Aug-2026 floor of 36 is the natural continuation) but is **not confirmed on the official page today**. **This carries essentially zero risk:** the requirement is a FLOOR ("target API level N **or higher**"), and the repo already sets `targetSdk = 36` and `compileSdk = 36` [VERIFIED: codebase app/build.gradle.kts]. `targetSdk 36` satisfies *both* a 35-floor and a 36-floor, so no code change is needed regardless of which the 2026 deadline turns out to be. Re-verify the exact deadline in Console at submission time; do not lower `targetSdk`.

**Automatable (this phase):**
- `TargetSdkComplianceTest` — assert `targetSdk == 36`, `compileSdk == 36`, `minSdk == 26` (static scan of `app/build.gradle.kts`, red-capable).
- Release build config — assert no `isDebuggable = true` in the release build type. The repo currently declares no explicit `buildTypes` block, so release defaults to non-debuggable (compliant); a test can assert the build script never sets `isDebuggable = true` for release. `isMinifyEnabled`/R8 is optional and NOT required for compliance — document if enabled (keep-rules for Room/serialization would then matter).

**Pre-launch report — what it checks** [VERIFIED: support.google.com/9842757, /9844487]: after uploading an app bundle (or saving to a track), Google installs it on **real devices in their test lab** and automatically launches + crawls for several minutes (tap/type/swipe). It flags: errors (crashes, ANRs, defective libraries, unsupported APIs), warnings (slow startup, sign-in/crawl issues, memory), accessibility (content labels, touch-target size, color contrast), and **known security vulnerabilities in third-party libraries** (with names/descriptions). AREL-05 wants a pass on both an Android 13- and an Android 14+ device — the lab device set covers this; you review the per-device results in Console.

**Human/device-gated (this phase documents, does NOT fake):** uploading the bundle, running the pre-launch report, and reviewing it on the Android 13- and 14+ devices; the real Health Connect permission dialog resolving on-device; app signing (user-owned keystore).

> **Pre-launch + consent gate caveat** [CITED: PITFALLS UX pitfalls]: the robo-crawler will land on the consent gate (correct by design) and may not proceed past it, producing sparse crawl screenshots. This is expected, not a failure — keep the gate; optionally supply pre-launch credentials/deep-link config; do not panic-read the empty crawl as a defect.

## The automatable-vs-human split (this phase)

**AUTOMATABLE — build + test green in the repo (do this):**
- `StoreListingCopy` registered in `CopyAuditRegistry` → passes the substring gate.
- `StoreListingCopyTest`: title ≤30, short ≤80, full ≤4000, and first-paragraph disclaimer must-contain (`not a medical device` + `does not diagnose, treat, cure, or prevent`).
- `DataSafetyDeclaration` committed constant + `DataSafetyManifestCrossCheckTest` (declaration ⇔ no-INTERNET / no-analytics / no-health-WRITE, red-capable).
- `TargetSdkComplianceTest` (==36) + no-debuggable-release assertion.
- `PRIVACY_POLICY_URL` single-source constant + `PrivacyPolicyUrlConsistencyTest`.
- Committed docs with the exact ready-to-paste Console answers.

**HUMAN / PLAY-CONSOLE / REAL-DEVICE — document, never fake:**
- Org account + D-U-N-S verification (AREL-04) — kickoff item, confirmed here via a status line the user sets.
- Health apps declaration submission (paste the committed answers).
- Data safety form entry (paste the all-"No" answers).
- Store listing upload (paste the committed copy).
- Privacy policy: host the web page (matching `PrivacyPolicyText.body`) + enter the URL in Console.
- Pre-launch report run + review on Android 13- and 14+ devices (AREL-05).
- App signing / keystore (user-owned).
- Live re-verification of Jan-2026 policy details in Console at submission (the org-account classification + exact form fields).

**Do NOT** commit any constant that pretends a Console/device step is done (no `orgAccountVerified = true`, no "pre-launch passed" flag). The docs carry human-confirmation checkboxes, not fake green state.

## Runtime State Inventory

> Not a rename/refactor phase. Included briefly because release finalization touches identity fields.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None new — no data migration in this phase | None |
| Live service config | Play Console listing/declaration/data-safety/privacy-URL — all live in Console, NOT git | Human: enter from committed docs |
| OS-registered state | None | None |
| Secrets/env vars | App signing keystore (user-owned, NOT in repo) | Human: manage keystore; never commit |
| Build artifacts / identity | `applicationId = "com.endoapp.app"`, `versionName = "0.1.0"`, `versionCode = 1`, `android:label="Endo"` are placeholders [VERIFIED: codebase] | Finalize identity at release (Claude's discretion whether to bump in this phase or leave to the user); the store title (`StoreListingCopy.title`) is separate from `android:label` |

**Nothing found in category:** Stored data / OS-registered state — None (verified: no schema change, no OS registration in this phase).

## Common Pitfalls

### Pitfall 1: Treating the health gauntlet as a week-of-submission form-fill
**What goes wrong:** the app is code-complete but stalls for weeks in rejection loops ("Inaccurate Health Apps Declaration", missing disclaimer) or, worst, the account is individual and cannot host a health app.
**Why it happens:** iOS compliance was App Review + usage strings; Play is heavier for health — declaration + Data safety + HC access + listing copy all must agree with the manifest and each other.
**How to avoid:** org account + D-U-N-S started at KICKOFF (confirmed green here); the declaration answers, store description, and per-type justifications all reuse the Phase-21 rationale story; disclaimer first (or at least present).
**Warning signs:** developer account shows "individual"; any manifest health permission without a screencast-able feature; disclaimer below the fold.

### Pitfall 2: Data safety form vs reality mismatch
**What goes wrong:** over-declaring on-device data as "collected" (scary false labels) OR under-corroborating "no collection" while something phones home.
**Why it happens:** the form's "collect/share/ephemeral" vocabulary doesn't match engineering intuition.
**How to avoid:** answer all-"No" (correct for on-device-only [VERIFIED: support.google.com/10787469]); let `DataSafetyManifestCrossCheckTest` + the no-INTERNET manifest be the mechanical proof.
**Warning signs:** any `libs.versions.toml` line you can't explain; a pre-launch report showing network calls (there should be none).

### Pitfall 3: Privacy-policy URL inconsistency / wrong format
**What goes wrong:** the URL 404s, differs between Console and the rationale activity, or is a PDF → rejection.
**How to avoid:** ONE `PRIVACY_POLICY_URL` constant; hosted page content mirrors `PrivacyPolicyText.body`; public non-geofenced HTML, not a PDF [VERIFIED: support.google.com store-listing/User Data].
**Warning signs:** the rationale screen content and the hosted page diverge; the URL is a Google Doc/PDF export.

### Pitfall 4: Lowering targetSdk or reading the wrong deadline
**What goes wrong:** someone "matches the documented requirement" (API 35) and lowers `targetSdk`, or panics that 36 isn't documented for 2026.
**How to avoid:** keep `targetSdk = 36`; it satisfies any floor ≤36. Re-verify the exact 2026 date in Console but never lower.

### Pitfall 5: The disclaimer sentence trips the substring gate
**What goes wrong:** registering the disclaimer paragraph into the audited catalog turns `CopyAuditTest` red (it legitimately contains "treat/cure/diagnose/prevent").
**How to avoid:** exempt the disclaimer paragraph from the catalog (as `Disclaimer.text` and `ReportCopy.FOOTER` already are) and pin it with the first-paragraph must-contain test instead [VERIFIED: codebase pattern].

## Code Examples

### First-paragraph disclaimer must-contain test (mirrors DisclaimerTest / reportCopy_carriesTheSelfTrackedNotDiagnosticFraming)
```kotlin
// StoreListingCopyTest.kt (JUnit4, :app testDebugUnitTest)
@Test fun fullDescription_firstParagraph_carriesTheNotMedicalDeviceDisclaimer() {
    val firstParagraph = StoreListingCopy.fullDescription.substringBefore("\n\n").lowercase()
    assertTrue("Disclaimer must be in the FIRST paragraph.",
        firstParagraph.contains("not a medical device"))
    assertTrue(firstParagraph.contains("does not diagnose, treat, cure, or prevent"))
}

@Test fun storeListingCopy_isRegisteredInTheAuditedCatalog() {
    assertTrue(CopyAuditRegistry.sources.contains(StoreListingCopy))
}

@Test fun storeListing_fieldLengthsWithinPlayLimits() {
    assertTrue(StoreListingCopy.title.length <= 30)
    assertTrue(StoreListingCopy.shortDescription.length <= 80)
    assertTrue(StoreListingCopy.fullDescription.length <= 4000)
}
```

### targetSdk assertion (static build-script scan, red-capable)
```kotlin
// TargetSdkComplianceTest.kt — walk up to app/build.gradle.kts, assert the pins.
private fun buildScript(): File { /* walk up until app/build.gradle.kts, like VersionCatalogAuditTests */ }
@Test fun targetAndCompileSdkAre36_minSdk26() {
    val text = buildScript().readText()
    assertTrue(Regex("""targetSdk\s*=\s*36""").containsMatchIn(text))
    assertTrue(Regex("""compileSdk\s*=\s*36""").containsMatchIn(text))
    assertTrue(Regex("""minSdk\s*=\s*26""").containsMatchIn(text))
    assertFalse("Release must not be debuggable.",
        Regex("""isDebuggable\s*=\s*true""").containsMatchIn(text))
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Health compliance = App Review + usage strings (iOS habit) | Health apps declaration + Data safety + HC access + listing rules must all agree | 2024–2026 Play policy waves | The store-facing surface is heavier; corroboration by manifest/catalog is the winning strategy |
| Individual dev account for any app | Org account + D-U-N-S for Medical/health-category apps | 2024→2026 enforcement | Weeks of latency; must start at kickoff [VERIFIED: support.google.com/13634885] |
| targetSdk 34/35 | targetSdk floor rises annually (35 documented; 36 the 2026 continuation) | Aug 2025 (35); 2026 (36, unconfirmed on the page) | Repo at 36 is safe regardless |
| Privacy policy as a PDF/Doc | Active, public, non-geofenced HTML URL, non-editable | current | Must host a real web page |

**Deprecated/outdated:**
- The PITFALLS.md disclaimer wording "not a medical device and does not diagnose, treat, or prevent any condition" **omits "cure"** — the official required phrasing includes **cure**: "does not diagnose, treat, **cure**, or prevent" [VERIFIED: support.google.com/16679511]. `Disclaimer.text` already includes "cure" — correct; use it.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | targetSdk-36 / Aug-31-2026 is the 2026 Play target floor | targetSdk + pre-launch | LOW/none — `targetSdk 36` satisfies any floor ≤36; re-verify date in Console, never lower |
| A2 | The disclaimer must be in the FIRST paragraph specifically | Store listing + disclaimer | LOW — official requires it "in the description"; first-paragraph exceeds it (CONTEXT-locked anyway) |
| A3 | This wellness/self-tracking app is classified such that org account + D-U-N-S is required | Health apps policy | MEDIUM — official list names "Medical apps"; safe posture is to require it (weeks of latency if wrong-and-late). Confirm classification in Console |
| A4 | `PRIVACY_POLICY_URL` value + hosted page are supplied by the user | Privacy policy | MEDIUM — the repo has no URL today; submission blocks until the user hosts the page and supplies the URL |
| A5 | Exact Health apps declaration + Data safety field labels match the ready-to-paste answers | Health apps policy / Data safety | LOW-MEDIUM — the *process* is verified; exact field wording must be read live in Console (Google doesn't publish them) |
| A6 | The Play test-lab device set covers both an Android 13- and an Android 14+ device for the pre-launch report | targetSdk + pre-launch | LOW — the lab covers a broad matrix; the user confirms the two required generations in the report |

**If this table is empty:** it is not — these six items need user confirmation or live-Console verification before the release artifacts become locked decisions.

## Open Questions

1. **What is the exact 2026 target-API floor and its date?**
   - Known: 35 / Aug-31-2025 is documented; 36 is the natural continuation.
   - Unclear: the official page isn't updated yet.
   - Recommendation: keep `targetSdk 36` (compliant either way); re-check the date in Console at submission.
2. **Is this app classified as a "Medical app" for the org-account requirement?**
   - Known: org account + D-U-N-S is required for Medical apps.
   - Unclear: whether a non-medical-device wellness tracker is a "Medical app" in Play's taxonomy.
   - Recommendation: treat as required, start D-U-N-S at kickoff, confirm classification in Console.
3. **What is the privacy-policy URL and where is it hosted?**
   - Recommendation: user supplies a public HTML page mirroring `PrivacyPolicyText.body`; add `PRIVACY_POLICY_URL` as the single source.
4. **Finalize app identity now or defer?**
   - `applicationId`, `versionName/Code`, `android:label` are placeholders. Recommendation: Claude's discretion — either finalize in this phase or document as a release-time human step; the store *title* is independent of `android:label`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK platform 36 + JDK 21 toolchain | Build + all in-repo tests | ✓ | compileSdk 36 / JDK 21 | — (proven building through Phase 24) |
| Gradle `:app:testDebugUnitTest` lane | Store-copy audit, cross-check, targetSdk, URL tests | ✓ | AGP 9.2.0 | — |
| Google Play Console (org account) | AREL-04, all Console submissions | ✗ (human-owned; unknown state) | — | None — org account + D-U-N-S is a hard human gate |
| D-U-N-S number | Org account verification | ✗ (unknown) | — | Request free from Dun & Bradstreet (weeks) |
| Public HTTPS host for privacy policy | AREL-03 privacy URL | ✗ (must be created) | — | GitHub Pages / any static host mirroring `PrivacyPolicyText.body` |
| Real Android 13- and 14+ devices (Google test lab) | AREL-05 pre-launch | ✓ (Google-hosted lab) | — | The lab provides the devices; user reviews results |

**Missing dependencies with no fallback:**
- Play Console **organization account + D-U-N-S** — blocks the entire submission; must be resolved at kickoff (AREL-04).
- A hosted **privacy-policy URL** — blocks submission; user must create the page + supply the URL.

**Missing dependencies with fallback:**
- Privacy-policy hosting → any public static host (GitHub Pages) mirroring the committed `PrivacyPolicyText.body`.

## Validation Architecture

> `workflow.nyquist_validation` is enabled (config.json). Section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 in `:app` (`testDebugUnitTest`, AGP-native, Robolectric where a context is needed); JUnit Jupiter 6.1.1 in `:insights` |
| Config file | `android/app/build.gradle.kts` (`testOptions.unitTests.isIncludeAndroidResources = true`) |
| Quick run command | `cd android && ./gradlew :app:testDebugUnitTest` |
| Full suite command | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AREL-03 | Store copy passes the ClaimsPolicy substring gate | unit | `./gradlew :app:testDebugUnitTest --tests "*CopyAuditTest"` | ❌ Wave 0 (register `StoreListingCopy`) |
| AREL-03 | Title ≤30 / short ≤80 / full ≤4000 + disclaimer in FIRST paragraph | unit | `./gradlew :app:testDebugUnitTest --tests "*StoreListingCopyTest"` | ❌ Wave 0 |
| AREL-03 | Data-safety "no collect/no share" ⇔ manifest (no INTERNET / no analytics / no health WRITE) | unit | `./gradlew :app:testDebugUnitTest --tests "*DataSafetyManifestCrossCheckTest"` | ❌ Wave 0 |
| AREL-03 | Privacy-policy URL single-source + non-blank/well-formed | unit | `./gradlew :app:testDebugUnitTest --tests "*PrivacyPolicyUrlConsistencyTest"` | ❌ Wave 0 |
| AREL-03 | Privacy-policy content states on-device/no-servers/no-analytics (pinned) | unit | `./gradlew :app:testDebugUnitTest --tests "*PrivacyPolicyTest"` | ✅ (extend if needed) |
| AREL-03 | No-INTERNET / no-health-WRITE manifest anchor still green | unit | `./gradlew :app:testDebugUnitTest --tests "*ManifestPermissionsTest"` | ✅ |
| AREL-03 | No analytics/auth/backend coordinate in catalog | unit | `./gradlew :insights:test --tests "*VersionCatalogAuditTests"` | ✅ |
| AREL-05 | targetSdk/compileSdk == 36, minSdk 26, no debuggable release | unit | `./gradlew :app:testDebugUnitTest --tests "*TargetSdkComplianceTest"` | ❌ Wave 0 |
| AREL-04 | Org account + D-U-N-S verified | **manual/human** (kickoff) | — documented `RELEASE.md` checkbox, user confirms | Doc |
| AREL-03 | Health apps declaration / Data safety / listing / privacy URL submitted in Console | **manual/human** | — ready-to-paste docs; user submits | Doc |
| AREL-05 | Pre-launch report passes on Android 13- and 14+ devices | **manual/device** | — user runs + reviews in Console | Doc |

### Sampling Rate
- **Per task commit:** `cd android && ./gradlew :app:testDebugUnitTest` (the new guards + `CopyAuditTest`/`ManifestPermissionsTest`).
- **Per wave merge:** `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- **Phase gate:** full suite green + the human-gated checklist docs committed (with unchecked human boxes) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `StoreListingCopy.kt` — the registered store-copy source (covers AREL-03) + registration into `CopyAuditRegistry.sources`
- [ ] `StoreListingCopyTest.kt` — lengths + first-paragraph disclaimer must-contain (AREL-03)
- [ ] `DataSafetyDeclaration.kt` + `DataSafetyManifestCrossCheckTest.kt` — the machine-checkable declaration ⇔ manifest/catalog cross-check (AREL-03)
- [ ] `TargetSdkComplianceTest.kt` — targetSdk 36 + no-debuggable-release (AREL-05)
- [ ] `PRIVACY_POLICY_URL` constant + `PrivacyPolicyUrlConsistencyTest.kt` (AREL-03)
- [ ] `RELEASE.md` / `PLAY-LISTING.md` / `DATA-SAFETY.md` / `HEALTH-DECLARATION.md` / `PRELAUNCH-CHECKLIST.md` — committed human-gated docs
- Framework install: none needed (JUnit4/Robolectric already wired).

## Security Domain

> `security_enforcement` enabled, ASVS level 1. This phase adds no new runtime code paths; the "security" surface is the truthful, codebase-corroborated declaration of the app's zero-egress posture. Most controls are inherited from Phases 17–24 and re-verified here.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture/Docs | yes | Release artifacts document the zero-egress design; declarations corroborated by tests |
| V2 Authentication | no | No accounts, no auth (the whole posture) |
| V3 Session Management | no | No sessions/backend |
| V4 Access Control | no (inherited) | Consent-by-construction gate (Phase 19) unchanged |
| V5 Input Validation | minimal | Store/declaration strings are static constants; the substring gate validates copy |
| V6 Cryptography | no new | Export crypto lives in Phase 23; unchanged here |
| V9/V14 Communications/Config | yes | No INTERNET permission; non-debuggable release; backup exclusion — asserted by tests |

### Known Threat Patterns for {Play release of a local-only health app}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| False/over-broad Data safety declaration | Repudiation / Info disclosure | All-"No" form + `DataSafetyManifestCrossCheckTest` binding it to the no-INTERNET/no-analytics manifest [VERIFIED: codebase + support.google.com/10787469] |
| Health data silently leaving the device | Information disclosure | No INTERNET permission (`ManifestPermissionsTest`); no analytics coordinate (`VersionCatalogAuditTests`); backup exclusion (`BackupRulesTest`) |
| Diagnostic/medical-claim copy triggering medical-device regulation | Elevation (regulatory) | `CopyAudit` substring gate over store copy + the required not-a-medical-device disclaimer first [VERIFIED: support.google.com/16679511] |
| Debuggable release build | Tampering | `TargetSdkComplianceTest` asserts no `isDebuggable = true` in release |
| Excessive HC permission vs feature | Elevation of privilege | Per-data-type justification maps 1:1 to a visible feature; read-only manifest (Phase 21) |

## Sources

### Primary (HIGH confidence)
- Shipped codebase (read directly this session): `ManifestPermissionsTest.kt`, `VersionCatalogAuditTests.kt`, `CopyAuditTest.kt`, `ClaimsPolicy.kt`, `AuditableCopy.kt`, `Disclaimer.kt`, `PrivacyPolicyText.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, phase summaries 17-08 / 19-05 / 21-05
- [Choose a developer account type — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en) — org account + D-U-N-S required for Medical apps
- [Provide information for Google Play's Data safety section — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) — on-device-only = not "collected"
- [Declare your app's data use | Android Developers](https://developer.android.com/privacy-and-security/declare-data-use)
- [Meet Google Play's target API level requirement | Android Developers](https://developer.android.com/google/play/requirements/target-sdk) — API-35 / Aug-31-2025 (current documented floor)

### Secondary (MEDIUM confidence)
- [Health Content and Services — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en) — in-scope for wellness apps reading health data; required non-medical-device disclaimer wording; publicly accessible privacy policy
- [Provide information for the Health apps declaration form — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en) — all apps must declare; process (not exact fields)
- [Best practices for your store listing — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13393723?hl=en) + [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) — title ≤30 / short ≤80 / full ≤4000
- [Use a pre-launch report to identify issues](https://support.google.com/googleplay/android-developer/answer/9842757?hl=en) + [Understand your pre-launch report](https://support.google.com/googleplay/android-developer/answer/9844487?hl=en) — real-device crawl, checks, security/accessibility
- Privacy-policy URL: public/non-geofenced/non-PDF/non-editable — Play store-listing + User Data policy pages

### Tertiary (LOW confidence — validate at submission)
- Prior `.planning/research/PITFALLS.md` + myappmonitor Jan-2026 recap — org-account-for-all-health, first-paragraph disclaimer, per-type essentiality (the org-account-for-all and first-paragraph specifics are the ones re-flagged as not confirmed official; the essentiality + declaration-consistency claims are corroborated by official pages)

## Metadata

**Confidence breakdown:**
- Standard stack (reused harnesses): HIGH — all code read directly this session; nothing new to install.
- Health apps policy + Data safety + disclaimer + store limits: HIGH for the claims re-verified on official pages; MEDIUM for the exact Console field labels (Google doesn't publish them).
- Org-account classification for this specific app: MEDIUM — official for "Medical apps"; the wellness-tracker classification is [ASSUMED], safe-posture required.
- targetSdk 36 / 2026 deadline: HIGH that `targetSdk 36` is compliant (floor semantics); LOW that the exact 2026 date/floor is officially documented (it isn't yet) — zero practical risk.
- Automatable-vs-human split: HIGH — grounded in the honesty rule + verified Console-only nature of submissions.

**Research date:** 2026-07-10
**Valid until:** 2026-08-10 for the Play-policy specifics (fast-moving — re-verify the target-SDK 2026 deadline, the org-account classification, and the exact form fields live in Console at submission time); 90 days for the codebase-reuse findings.
