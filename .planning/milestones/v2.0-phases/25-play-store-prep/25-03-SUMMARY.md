---
phase: 25-play-store-prep
plan: 03
subsystem: release-docs
tags: [android, play-store, release-docs, data-safety, health-apps-declaration, privacy-policy, release-checklist, arel-03, arel-04, arel-05, docs]

# Dependency graph
requires:
  - phase: 25-play-store-prep
    plan: 01
    provides: "StoreListingCopy (title/short/full/what's-new + Disclaimer.text first paragraph) — pasted verbatim into PLAY-LISTING.md; corroborated by StoreListingCopyTest + CopyAuditTest"
  - phase: 25-play-store-prep
    plan: 02
    provides: "DataSafetyDeclaration (all-'No') + DataSafetyManifestCrossCheckTest + TargetSdkComplianceTest + PRIVACY_POLICY_URL + PrivacyPolicyUrlConsistencyTest — the corroboration cited by DATA-SAFETY.md / RELEASE-CHECKLIST.md"
  - phase: 19-app-shell-consent
    plan: 04
    provides: "PrivacyPolicyText.body + contactEmail (single-source in-app policy) — mirrored verbatim into PRIVACY-POLICY.md for hosting"
provides:
  - "docs/release/PLAY-LISTING.md — ready-to-paste title/short/full/what's-new (verbatim from StoreListingCopy), disclaimer as paragraph 1"
  - "docs/release/DATA-SAFETY.md — all-'No' Console answers + corroboration table (which green test proves each answer)"
  - "docs/release/HEALTH-APPS-DECLARATION.md — not-a-medical-device answers + per-Health-Connect-type justification table (READ_MENSTRUATION/SLEEP/EXERCISE/NUTRITION/HEALTH_DATA_HISTORY)"
  - "docs/release/PRIVACY-POLICY.md — verbatim mirror of PrivacyPolicyText.body for public hosting + hosting rules + PRIVACY_POLICY_URL"
  - "docs/release/RELEASE-CHECKLIST.md — ordered human-gated checklist (org-account/D-U-N-S FIRST), automatable-vs-human split, unchecked confirmation boxes, ASSUMPTIONS surfaced"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Release docs are a durable deliverable under docs/release/ (survives .planning/ archival at milestone completion); each cites the green in-repo test that corroborates it — no doc restates a claim the code contradicts"
    - "Honest human-gated checklist: every Console/account/device step is an UNCHECKED confirmation box; no committed boolean pretends a step is done (T-25-05); the org-account + D-U-N-S kickoff has a user-set ORG_ACCOUNT_VERIFIED status line, not a faked green state"
    - "Verbatim single-source mirror: PRIVACY-POLICY.md mirrors PrivacyPolicyText.body word-for-word (markdown soft-wrap only) so the hosted page is drift-free from the in-app source"

key-files:
  created:
    - docs/release/PLAY-LISTING.md
    - docs/release/DATA-SAFETY.md
    - docs/release/HEALTH-APPS-DECLARATION.md
    - docs/release/PRIVACY-POLICY.md
    - docs/release/RELEASE-CHECKLIST.md
  modified: []

key-decisions:
  - "docs/release/ chosen as the durable home (not .planning/) so the release doc set survives milestone archival; five separate docs (not one RELEASE.md) so each Console form has a single paste-ready target"
  - "Every automatable claim cites its green test by name (StoreListingCopyTest / CopyAuditTest / DataSafetyManifestCrossCheckTest / ManifestPermissionsTest / VersionCatalogAuditTests / TargetSdkComplianceTest / PrivacyPolicyUrlConsistencyTest); every Console/account/device step is an unchecked human box — the automatable-and-green half and the human/console-gated half are separated by an explicit PART A / PART B header in RELEASE-CHECKLIST.md"
  - "App-identity placeholders (applicationId=com.endoapp.app, versionName=0.1.0, versionCode=1, android:label=Endo) documented as a release-time human decision (ASSUMED A-identity) — NOT silently changed in code this phase"
  - "Org-account + D-U-N-S surfaced FIRST as a REQUIRED kickoff item with a user-set ORG_ACCOUNT_VERIFIED status line and a 'confirm classification live in Console' note (ASSUMED A3, safe posture: treat as required)"

requirements-completed: [AREL-03, AREL-04, AREL-05]

# Metrics
duration: ~12min
completed: 2026-07-10
status: complete
---

# Phase 25 Plan 03: Play Console Release Doc Set Summary

**Authored the five committed release docs under `docs/release/` — the ready-to-paste Play Console answers (store listing, Data safety all-"No", Health apps declaration) plus the public privacy-policy hosting mirror and the master human-gated RELEASE-CHECKLIST (org-account/D-U-N-S FIRST). Every automatable claim cites the green in-repo test that corroborates it; every Console/account/device step is an UNCHECKED human-confirmation box — no faked Console state anywhere (T-25-05). Phase gate re-run REAL and green: `:app` 460/460, `:insights` 176/176, 0 failures, real 16 MB debug APK, `:app:lintDebug` clean.**

## Performance
- **Duration:** ~12 min
- **Started:** 2026-07-10T01:57:17Z
- **Tasks:** 2
- **Files:** 5 (5 created, 0 modified — docs-only; no source change)

## Accomplishments
- **Ready-to-paste Console answers (AREL-03):**
  - `PLAY-LISTING.md` — title ("Endo: Endometriosis Tracker", 27 chars), short description (77 chars), the full description with `Disclaimer.text` as paragraph 1, and the what's-new — all copied VERBATIM from `StoreListingCopy.kt`, citing `StoreListingCopyTest` (field limits + first-paragraph disclaimer) and `CopyAuditTest` (substring gate) as corroboration.
  - `DATA-SAFETY.md` — the all-"No" answers with the corroboration table (each answer → the green test that proves it), the "delete my data → Yes, on-device" nuance, and the WHY (on-device-only ≠ collection per support.google.com/10787469; no INTERNET, no analytics, local-only + user-initiated encrypted export).
  - `HEALTH-APPS-DECLARATION.md` — the self-tracking/wellness (NOT a medical device) answers with the verified "does not diagnose, treat, cure, or prevent" wording, the read-only/on-device framing, and the per-Health-Connect-type justification table (READ_MENSTRUATION, READ_SLEEP, READ_EXERCISE, READ_NUTRITION, READ_HEALTH_DATA_HISTORY → the visible feature each maps to; reuses the Phase-21 rationale story so declaration/manifest/behavior tell ONE story).
- **Hosting mirror (AREL-03):** `PRIVACY-POLICY.md` mirrors `PrivacyPolicyText.body` VERBATIM (single source, no drift — markdown soft-wrap only, confirmed word-for-word intact across wraps), with the hosting rules (public, non-geofenced, non-PDF, non-editable HTTPS) and the note that the hosted URL must match `PRIVACY_POLICY_URL` and the in-app rationale surface.
- **Human-gated checklist (AREL-04/05):** `RELEASE-CHECKLIST.md` is split into PART A (AUTOMATABLE-AND-GREEN, `[x]` items each backed by a named green test) and PART B (HUMAN/CONSOLE/DEVICE-GATED, all `[ ]` unchecked): (1) org-account + D-U-N-S KICKOFF with a user-set `ORG_ACCOUNT_VERIFIED: no` status line and a confirm-classification note; (2) host privacy policy + set the URL; (3) submit the four Console forms; (4) pre-launch report on Android 13- and 14+ devices with the consent-gate sparse-crawl caveat; (5) re-verify Jan-2026 policy live; (6) finalize app identity (applicationId/versionName/versionCode/android:label placeholders — release-time decision); (7) app-signing keystore (user-owned). The RESEARCH ASSUMPTIONS (A1/A3/A4/A5/A6/A-identity) are surfaced as user-confirmation items, and a corroboration index maps each in-repo claim to its green test.

## Task Commits
1. **Task 1: ready-to-paste Console answers (PLAY-LISTING / DATA-SAFETY / HEALTH-APPS-DECLARATION)** — `a4f383b` (docs)
2. **Task 2: privacy-policy hosting mirror + human-gated RELEASE-CHECKLIST** — `2d5b666` (docs)

_No STATE.md / ROADMAP.md commit — per the objective, this run does NOT update STATE.md or ROADMAP.md (the orchestrator owns state)._

## Files Created/Modified
- `docs/release/PLAY-LISTING.md` (created) — ready-to-paste store listing, verbatim from `StoreListingCopy`.
- `docs/release/DATA-SAFETY.md` (created) — all-"No" answers + corroboration table.
- `docs/release/HEALTH-APPS-DECLARATION.md` (created) — declaration answers + per-HC-type justification table.
- `docs/release/PRIVACY-POLICY.md` (created) — verbatim hosting mirror of `PrivacyPolicyText.body`.
- `docs/release/RELEASE-CHECKLIST.md` (created) — master human-gated checklist, org-account/D-U-N-S first, unchecked boxes.

## Test Results (REAL, run on this host)
Docs-only plan touches no source; the automatable guards from Plans 01–02 stay green. Phase gate re-run (`:app:testDebugUnitTest` forced with `--rerun-tasks` to defeat caching):
- `:app:testDebugUnitTest` — **460/460 passed, 0 failures, 0 errors** (confirmed post `--rerun-tasks`, 32 tasks executed, BUILD SUCCESSFUL — genuinely green, not cached).
- `:insights:test` — **176/176 passed, 0 failures, 0 errors**.
- `:app:assembleDebug` — real **16 MB** `app-debug.apk` on disk.
- `:app:lintDebug` — **BUILD SUCCESSFUL**, no new findings.

## Deviations from Plan
None — plan executed exactly as written. Docs-only; no source-code change was needed (the code guards landed in Plans 01–02). The initial phase-gate run reported "60 up-to-date" (correct — a docs-only plan changes no Gradle test inputs); I forced `:app:testDebugUnitTest --rerun-tasks` to prove the suite is genuinely green rather than merely cached.

## Threat Model Compliance
- **T-25-05 (Repudiation / faked Console state, high):** mitigated — every Console/account/device step in `RELEASE-CHECKLIST.md` is an UNCHECKED `[ ]` confirmation box; the org-account + D-U-N-S kickoff carries a user-set `ORG_ACCOUNT_VERIFIED: no` status line (not a committed boolean pretending it is done); the PART A / PART B header separates the automatable-and-green half from the human/console-gated half so the two are never confused.
- **T-25-01 (Information disclosure — DATA-SAFETY / HEALTH-APPS-DECLARATION, high):** mitigated — each declaration answer cites the green in-repo cross-check that corroborates it (no-INTERNET manifest, no-analytics catalog, read-only health); no doc restates a claim the code contradicts.
- **T-25-SC (Tampering / package installs, low/accept):** honored — docs-only plan; zero package installs.

## Known Stubs
- **`PRIVACY_POLICY_URL` remains an intentional placeholder** (`https://replace-me.github.io/endo-app/privacy-policy`) — carried over from Plan 25-02, DELIBERATE and honest (the repo has no hosted page; fabricating a live URL would be dishonest). `PRIVACY-POLICY.md` and `RELEASE-CHECKLIST.md` item 2 document the required human hosting step + URL replacement before submission. No new stubs introduced by this plan.

## Self-Check: PASSED
- All five created files exist on disk (verified):
  - `docs/release/PLAY-LISTING.md` — FOUND
  - `docs/release/DATA-SAFETY.md` — FOUND
  - `docs/release/HEALTH-APPS-DECLARATION.md` — FOUND
  - `docs/release/PRIVACY-POLICY.md` — FOUND
  - `docs/release/RELEASE-CHECKLIST.md` — FOUND
- Both task commits present in git history: `a4f383b` (docs, Task 1), `2d5b666` (docs, Task 2).
- Both `<verify>` grep gates passed (TASK1 VERIFY: PASS, TASK2 VERIFY: PASS); `PRIVACY-POLICY.md` verbatim mirror confirmed word-for-word intact across markdown wraps.
- Real build/test evidence captured above (`:app` 460/460, `:insights` 176/176, real 16 MB APK, `:app:lintDebug` clean).

---
*Phase: 25-play-store-prep*
*Completed: 2026-07-10*
