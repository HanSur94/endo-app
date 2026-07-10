---
phase: 25-play-store-prep
plan: 01
subsystem: content
tags: [android, play-store, copy-audit, claims-policy, disclaimer, store-listing, arel-03, kotlin, junit4]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 04
    provides: "Disclaimer.text single-source 'not medical advice / not a medical device' constant (carries 'does not diagnose, treat, cure, or prevent any condition'); the exempt-scan + must-contain idiom (Disclaimer/PrivacyPolicyText exempt from CopyAuditRegistry.sources, pinned by dedicated must-contain tests)"
  - phase: 18-room-persistence
    plan: 05
    provides: "AuditableCopy interface + CopyAuditRegistry.sources catalog + CopyAuditTest substring gate (ClaimsPolicy causal/imperative/alarmist/gendered/false-precision)"
  - phase: 24-pdf-report
    plan: 02
    provides: "ReportCopy — the EXEMPT-string precedent (FOOTER exempt from auditableStrings, pinned by reportCopy_carriesTheSelfTrackedNotDiagnosticFraming)"
provides:
  - "StoreListingCopy — the registered, auditable Google Play store copy (title ≤30, short ≤80, what's-new, 4 audited body paragraphs); fullDescription = Disclaimer.text (exempt paragraph 1) + audited body joined by blank lines"
  - "StoreListingCopy registered in CopyAuditRegistry.sources — store copy now passes the SAME ClaimsPolicy substring gate as in-app copy"
  - "StoreListingCopyTest — Play field-length limits + first-paragraph disclaimer must-contain + catalog-registration guards"
affects: [25-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Store listing copy is a registered AuditableCopy source (like in-app copy) — the most public claim surface passes the SAME substring gate, mechanically not by review"
    - "Exempt-scan + must-contain idiom extended to the store description's first paragraph: Disclaimer.text is reused VERBATIM (single-source, drift-proof), excluded from auditableStrings (it legitimately contains diagnose/treat/cure/prevent negations), and pinned by a first-paragraph substringBefore(\"\\n\\n\") must-contain — mirrors Disclaimer / ReportCopy.FOOTER"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/StoreListingCopy.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/StoreListingCopyTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt

key-decisions:
  - "Store copy uses the exact RESEARCH §'Recommended ClaimsPolicy-safe store copy' draft strings verbatim (title 'Endo: Endometriosis Tracker' 27 chars, the ≤80 short description, the four audited body paragraphs, the what's-new line) — no invented marketing copy, no weakening to a 'v1'/'placeholder'"
  - "First paragraph of fullDescription IS Disclaimer.text verbatim (single source of truth) — a second hand-typed disclaimer would drift (RESEARCH Anti-Patterns); it is exempt from the scan but pinned FIRST by the must-contain test"

requirements-completed: [AREL-03]

# Metrics
duration: ~9min
completed: 2026-07-10
status: complete
---

# Phase 25 Plan 01: StoreListingCopy — Registered, Auditable Play Store Copy Summary

**Authored the Google Play store listing copy (title ≤30, short ≤80, what's-new, four audited body paragraphs) as a registered `StoreListingCopy : AuditableCopy` source that passes the SAME ClaimsPolicy substring gate as every in-app string — with the not-a-medical-device disclaimer reused verbatim from `Disclaimer.text` as the FIRST paragraph of the full description, exempt from the scan but pinned by a first-paragraph must-contain test. Proven on the JVM lane: `StoreListingCopyTest` 4/4, `CopyAuditTest` 10/10, `:app` 456/456, `:insights` 176/176, real 16 MB debug APK, `:app:lintDebug` clean.**

## Performance
- **Duration:** ~9 min
- **Started:** 2026-07-10T01:27:06Z
- **Completed:** 2026-07-10T01:36:10Z
- **Tasks:** 2
- **Files:** 3 (2 created, 1 modified)

## Accomplishments
- **Registered, auditable store copy (AREL-03, T-25-02):** `StoreListingCopy : AuditableCopy` holds the four Play fields — `title` ("Endo: Endometriosis Tracker", 27 chars), `shortDescription` (77 chars), `whatsNew`, and `fullDescriptionBody` (four audited paragraphs). `auditableStrings = listOf(title, shortDescription, whatsNew) + fullDescriptionBody`, and it is appended to `CopyAuditRegistry.sources` IN THE SAME CHANGE — so the store copy now passes the identical ClaimsPolicy causal/imperative/alarmist/gendered/false-precision substring gate every in-app string passes.
- **Disclaimer-first, single-sourced:** `fullDescription` is computed as `(listOf(Disclaimer.text) + fullDescriptionBody).joinToString("\n\n")`, so the first paragraph is literally `Disclaimer.text` verbatim — which already carries Play's exact required wording ("not a medical device", "does not diagnose, treat, cure, or prevent any condition") plus the "have better conversations with your clinician" reminder (VERIFIED: support.google.com/16679511). No second, drift-prone hand-typed disclaimer.
- **Exempt-yet-pinned idiom extended to the store surface:** the disclaimer paragraph legitimately contains "diagnose/treat/cure/prevent" as negations, so it is DELIBERATELY excluded from `auditableStrings` (mirroring `Disclaimer` / `ReportCopy.FOOTER`) and pinned instead by `StoreListingCopyTest`'s first-paragraph `substringBefore("\n\n")` must-contain — this pins the disclaimer to the FIRST paragraph, not merely "present".
- **Field-limit + registration guards:** `StoreListingCopyTest` (JUnit4, pure string assertions, no Robolectric) asserts title ≤30, short ≤80, full ≤4000 (VERIFIED: support.google.com/13393723, /9859152), the first-paragraph disclaimer wording, `CopyAuditRegistry.sources.contains(StoreListingCopy)`, and non-empty/non-blank body paragraphs.

## Task Commits
1. **Task 1: StoreListingCopy — registered store copy with Disclaimer.text as paragraph 1** — `21f7ed0` (feat)
2. **Task 2: StoreListingCopyTest — field limits + first-paragraph disclaimer + registration** — `b967893` (test)

_No STATE.md / ROADMAP.md commit — per the objective, this run does NOT update STATE.md or ROADMAP.md (the orchestrator owns state)._

## Files Created/Modified
- `content/StoreListingCopy.kt` (created) — the registered, auditable Play store copy; first paragraph = `Disclaimer.text` verbatim, exempt from `auditableStrings`.
- `content/StoreListingCopyTest.kt` (created) — Play field-length + first-paragraph disclaimer must-contain + catalog-registration guards (4 tests).
- `content/AuditableCopy.kt` (modified) — appended `StoreListingCopy` to `CopyAuditRegistry.sources`.

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest --tests "*StoreListingCopyTest" --tests "*CopyAuditTest"` — **GREEN.**
  - `StoreListingCopyTest` — **4/4 passed, 0 failures**: field limits (≤30/≤80/≤4000); first paragraph carries "not a medical device" + "does not diagnose, treat, cure, or prevent"; `StoreListingCopy` registered in `CopyAuditRegistry.sources`; body non-empty/non-blank.
  - `CopyAuditTest` — **10/10 passed, 0 failures**: the newly-registered title / short description / what's-new + four body paragraphs carry NO causal/imperative/alarmist/gendered/false-precision token.
- **No-regression wave gate** `:insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`:
  - `:app:testDebugUnitTest` — **456/456 passed, 0 failures** (+4 over the prior baseline from `StoreListingCopyTest`).
  - `:insights:test` — **176/176 passed, 0 failures** (untouched; no regression).
  - `:app:assembleDebug` — **BUILD SUCCESSFUL**, real 16.0 MB debug APK.
  - `:app:lintDebug` — **BUILD SUCCESSFUL**, no new lint findings.

## Deviations from Plan
None — plan executed exactly as written. The exact RESEARCH §"Recommended ClaimsPolicy-safe store copy" draft strings passed the real `CopyAuditTest` substring gate on the first run (no reword needed), so no string had to be synonym-swapped.

## Threat Model Compliance
- **T-25-02 (Elevation / regulatory — diagnostic/medical-claim store copy, high):** mitigated — `StoreListingCopy` is registered in `CopyAuditRegistry.sources` and passes the SAME ClaimsPolicy substring gate as in-app copy (`CopyAuditTest` 10/10, no causal/imperative/alarmist/gendered/false-precision token in title/short/what's-new/body); the required not-a-medical-device disclaimer is reused verbatim from the single-source `Disclaimer.text` as the FIRST paragraph, pinned by `StoreListingCopyTest`'s first-paragraph must-contain (no drift, exceeds Play's "present in description" requirement).
- **T-25-SC (Tampering / package installs, low/accept):** honored — zero package installs this plan; no new version-catalog coordinate.

## Known Stubs
None. Every string is the final, ready-to-paste store copy (RESEARCH-drafted, gate-verified). These become the source for `PLAY-LISTING.md` in Plan 03.

## Self-Check: PASSED
- Both created files + the modified file exist on disk (verified):
  - `android/app/src/main/kotlin/com/endoapp/app/content/StoreListingCopy.kt` — FOUND
  - `android/app/src/test/kotlin/com/endoapp/app/content/StoreListingCopyTest.kt` — FOUND
  - `android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt` — FOUND (StoreListingCopy registered)
- Both task commits present in git history: `21f7ed0` (feat), `b967893` (test).
- Real build/test evidence captured above (`StoreListingCopyTest` 4/4, `CopyAuditTest` 10/10, `:app` 456/456, `:insights` 176/176, `assembleDebug` 16.0 MB APK, `lintDebug` clean).

---
*Phase: 25-play-store-prep*
*Completed: 2026-07-10*
