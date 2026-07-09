---
phase: 19-app-shell-consent
plan: 04
subsystem: ui
tags: [android, compose, material3, onboarding, consent, gdpr, privacy-policy, copy-audit, robolectric, bloom]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 01
    provides: "BloomTheme + LocalBloomColors + BloomPrimaryButton + DisclaimerBanner + EyebrowLabel; Bloom typography (bloomTitle/bloomH2/bloomH3/bloomBody); BloomTabIcons; Robolectric+Compose JVM harness"
  - phase: 19-app-shell-consent
    plan: 02
    provides: "AppGate NotConsented -> OnboardingScreen(onConsent/onDecline) placeholder; ShellCopy (CONSENT_GRANT/CONSENT_DECLINE registered); AppGateConsentTest; CopyAuditRegistry.sources"
  - phase: 18-room-persistence
    plan: 03
    provides: "ConsentStore.grant()/withdraw() + 3-state ConsentState (the single grant site behind onConsent)"
provides:
  - "Bloom OnboardingScreen — the real informed-consent flow: rose hero heart + welcome title/body, petal DisclaimerBanner (Disclaimer.text), plum privacy link, consent section (bloomH2 heading + explainer + 'I consent' + 'Not now'); consent.grantButton/consent.declineButton testTags + onConsent/onDecline signature preserved (AppGate wiring unchanged)"
  - "PrivacyPolicyScreen — offline in-app privacy policy (renders PrivacyPolicyText.body), presented over onboarding, dismisses on predictive back"
  - "Disclaimer (single-source 'not medical advice'/'not a medical device' constant) + PrivacyPolicyText (Android-adapted, local-only, contactEmail) — EXEMPT from the curated CopyAudit scan, pinned by DisclaimerTest + PrivacyPolicyTest must-contain tests"
  - "OnboardingCopy — 6 curated onboarding chrome strings registered in CopyAuditRegistry.sources"
  - "BloomGlyphs (heartGlyph rose hero + lockGlyph plum privacy) — asset-light ImageVectors"
  - "OnboardingScreenTest — consent/decline/privacy flow proof on the JVM lane"
affects: [19-05, 20-tracking, 22-insights-explore]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Long-form legal constants (Disclaimer.text, PrivacyPolicyText.body) are single-sourced and DELIBERATELY EXEMPT from CopyAuditRegistry.sources (they legitimately contain forbidden tokens); guarded instead by dedicated must-contain tests — the iOS DisclaimerTests/PrivacyPolicyTests contract, ported"
    - "PrivacyPolicyText.body built from single-line paragraph blocks joined by \\n\\n so source-line wraps never leak into rendered copy as hard breaks; heading\\nparagraph blocks split at render for bloomH3/body"
    - "Offline policy is a full-screen overlay in the SAME composition (not a popup/dialog) so it composes deterministically over the gate and is queryable on the Robolectric JVM lane; dismisses via BackHandler (predictive back), no label-bearing close control (keeps the surface copy-clean)"
    - "Below-fold consent affordances in a scrollable onboarding require performScrollTo() before performClick() on the Robolectric compose lane"
    - "Curated onboarding chrome single-sourced: 'I consent'/'Not now' stay owned by ShellCopy (Plan 02); OnboardingCopy owns only the newly-introduced strings — no duplicate registration, no drift"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/Disclaimer.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/PrivacyPolicyText.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/OnboardingCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/onboarding/PrivacyPolicyScreen.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/DisclaimerTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/content/PrivacyPolicyTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/onboarding/OnboardingScreenTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/ui/onboarding/OnboardingScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomIcons.kt

key-decisions:
  - "Curated 'I consent'/'Not now' kept single-sourced in ShellCopy (Plan 02) rather than duplicated into OnboardingCopy as the plan's literal list suggested — OnboardingScreen references ShellCopy for the two buttons and OnboardingCopy for the 6 new strings; every string is still registered + scanned, with no drift-prone duplicate constants"
  - "PrivacyPolicyScreen is a same-composition full-screen overlay (not ModalBottomSheet) — deterministic under Robolectric and free of the experimental-sheet popup-window rendering that would make the 'policy content appears' assertion flaky"
  - "Privacy policy dismisses via predictive back only (no visible close label) — any visible close string would be user-facing copy requiring registration; the policy surface renders ONLY the exempt PrivacyPolicyText.body, keeping it copy-clean. A richer Settings-based navigation is Phase 20"
  - "Hero uses a filled heart ImageVector (Material Favorite path) tinted rose rather than the outline FavoriteBorder — simpler, reliably rendered, reads identically as the warm hero glyph"

patterns-established:
  - "Exempt-scan pattern for honest legal prose: NOT in CopyAuditRegistry.sources + a dedicated must-contain test file"
  - "Informed-consent focus order encoded in the composable: hero -> disclaimer -> privacy link -> consent heading/explainer -> grant -> decline"

requirements-completed: [ASHL-01, ASHL-02]

coverage:
  - id: D1
    description: "Disclaimer single-source constant carries 'not medical advice' AND 'not a medical device' (ONBD-02), exempt from the curated substring scan"
    requirement: "ASHL-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/content/DisclaimerTest.kt (3 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "PrivacyPolicyText Android-adapted (on-device / no servers / no accounts / no sharing / no analytics + developer + contact email; NO iCloud/sync), exempt-scanned"
    requirement: "ASHL-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/content/PrivacyPolicyTest.kt (10 tests, incl. no-iCloud guard)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Bloom OnboardingScreen informed-consent flow — disclaimer + privacy reachable before consent; grant fires onConsent once; decline fires onDecline and grants nothing; privacy link opens the offline policy; testTags preserved"
    requirement: "ASHL-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/ui/onboarding/OnboardingScreenTest.kt (5 tests)"
        status: pass
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt (5 tests — grant/decline wiring unchanged)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Curated onboarding chrome (OnboardingCopy, 6 strings) registered in CopyAuditRegistry.sources and clean against the ClaimsPolicy substring gate"
    requirement: "ASHL-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt (7 tests, scanning OnboardingCopy)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Bloom visual/pixel fidelity of the onboarding/consent + privacy screens and the TalkBack focus-order sweep on a real device"
    verification: []
    human_judgment: true
    rationale: "Pixel parity to the shipped iOS Bloom look and the TalkBack focus order are the Manual-Only device checks per 19-VALIDATION — not provable on the Robolectric JVM lane."

# Metrics
duration: ~10min
completed: 2026-07-09
status: complete
---

# Phase 19 Plan 04: Bloom Onboarding + Consent + Offline Privacy Summary

**Enriched the Plan-02 placeholder into the faithful Bloom informed-consent flow — rose hero heart + welcome title/body, the petal DisclaimerBanner, a plum privacy link opening an offline PrivacyPolicyScreen, and the consent section ('I consent' / 'Not now') — over two single-sourced, exempt-scanned legal constants (Disclaimer + Android-adapted, local-only PrivacyPolicyText) pinned by their own must-contain tests, with the curated onboarding chrome registered and the whole flow proven on the JVM lane (`:app` 97/97, `:insights` 176/176, real APK).**

## Performance
- **Duration:** ~10 min
- **Started:** 2026-07-09T12:26:06Z
- **Completed:** 2026-07-09T12:35:39Z
- **Tasks:** 3
- **Files:** 10 (7 created, 3 modified)

## Accomplishments
- **Faithful Bloom onboarding/consent flow (ASHL-01/02):** `OnboardingScreen` now composes, in the GDPR informed-consent order, a rose hero heart + `bloomTitle` "Welcome to Endo-App" + welcome body; the petal `DisclaimerBanner(Disclaimer.text)` (`onboarding.disclaimer`); a plum lock privacy link (`onboarding.privacyLink`) opening the offline policy BEFORE consent; and the consent section — `bloomH2` "Ready when you are" + explainer + `BloomPrimaryButton` "I consent" (`consent.grantButton`) + low-emphasis "Not now" (`consent.declineButton`). The `onConsent`/`onDecline` signature + testTags are byte-for-byte preserved, so the Plan-02 `AppGate` grant (`consentStore.grant()`, the only grant site) / decline (fail-closed no-op) wiring is unchanged.
- **Offline `PrivacyPolicyScreen`:** a full-screen overlay over onboarding rendering the single-source `PrivacyPolicyText.body` on Bloom `surface`, scrollable, `bloomH2`/`bloomH3`/`body`, dismissing on predictive back.
- **Two single-sourced, exempt-scanned legal constants:** `Disclaimer.text` (ported verbatim from iOS — contains "not medical advice" AND "not a medical device") and `PrivacyPolicyText.body` (Android-adapted: on-device / no servers / no accounts / no third-party sharing / no analytics + developer=controller + `sannahrush@googlemail.com`; all iCloud/sync wording removed). Both are DELIBERATELY absent from `CopyAuditRegistry.sources` (they legitimately contain "diagnose/treat/cure/prevent") and instead pinned by `DisclaimerTest` (3) + `PrivacyPolicyTest` (10, incl. a no-iCloud Android-adaptation guard) — the iOS contract, mirrored.
- **Curated copy registered from birth:** `OnboardingCopy` (6 chrome strings — hero title, welcome body, privacy link, consent heading, consent explainer, consent-write error) added to `CopyAuditRegistry.sources` in the same change; `CopyAuditTest` now scans them (7/7 green). Each string was hand-verified against the full ClaimsPolicy forbidden-substring set (causal/imperative/alarmist/gendered/precision).
- **Asset-light glyphs:** `BloomGlyphs.heartGlyph` (rose hero) + `lockGlyph` (plum privacy) added to `BloomIcons.kt` as bundled `ImageVector`s — no `material-icons-extended`, no new dependency (threat T-19-SC honored).

## Task Commits

1. **Task 1: long-form Disclaimer + Android-adapted PrivacyPolicyText + exempt must-contain tests** — `0d301d3` (feat)
2. **Task 2: Bloom OnboardingScreen + PrivacyPolicyScreen + BloomGlyphs + OnboardingCopy registration** — `7766ab5` (feat)
3. **Task 3: OnboardingScreenTest — consent / decline / privacy flow** — `0dd1126` (test)

_No STATE.md / ROADMAP.md commit — the orchestrator owns state updates for this run (per objective)._

## Files Created/Modified
- `content/Disclaimer.kt` — single-source "not medical advice / not a medical device" constant (exempt-scanned).
- `content/PrivacyPolicyText.kt` — Android-adapted offline policy body + `contactEmail` (exempt-scanned, no iCloud).
- `content/OnboardingCopy.kt` — 6 curated onboarding chrome strings, registered.
- `content/AuditableCopy.kt` — registered `OnboardingCopy` in `CopyAuditRegistry.sources`.
- `ui/onboarding/OnboardingScreen.kt` — reworked into the full Bloom informed-consent flow.
- `ui/onboarding/PrivacyPolicyScreen.kt` — offline privacy policy overlay.
- `designsystem/BloomIcons.kt` — added `BloomGlyphs` (heart + lock).
- `test/content/DisclaimerTest.kt`, `test/content/PrivacyPolicyTest.kt`, `test/ui/onboarding/OnboardingScreenTest.kt` — the must-contain + flow tests.

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **97/97 passed, 0 failures** (+22 over the Plan-03 baseline).
  - `DisclaimerTest` **3/3**, `PrivacyPolicyTest` **10/10** (incl. the no-iCloud Android-adaptation guard).
  - `OnboardingScreenTest` **5/5**: disclaimer + privacy reachable before consent; grant fires `onConsent` once; decline fires `onDecline` and grants nothing; privacy link opens the offline policy; required testTags present.
  - `CopyAuditTest` **7/7** (now scanning the 6 registered `OnboardingCopy` strings — all clean against the ClaimsPolicy substring gate).
  - `AppGateConsentTest` **5/5** — the consent-by-construction invariant still green with the enriched onboarding (grant/decline wiring unchanged).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**, real 12.9 MB debug APK.

## Decisions Made
See `key-decisions` frontmatter. Headlines: (1) `I consent`/`Not now` stay single-sourced in `ShellCopy` (OnboardingScreen references them; OnboardingCopy owns only the 6 new strings) — a minor deviation from the plan's literal OnboardingCopy list that avoids drift-prone duplicate constants while keeping full audit coverage; (2) the privacy policy is a same-composition full-screen overlay (deterministic under Robolectric) dismissing via predictive back only (no label-bearing close control, keeping the surface copy-clean); (3) filled-heart hero glyph over outline for reliable render.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Below-fold consent affordances need `performScrollTo()` on the JVM test lane**
- **Found during:** Task 3 (`OnboardingScreenTest.decline_firesOnDecline_andGrantsNothing` first run RED — `declineCount` stayed 0).
- **Issue:** The enriched onboarding is a long vertical scroll; the "Not now" decline sits below the Robolectric default viewport, so `performClick()` never reached its `onClick`.
- **Fix:** call `performScrollTo().performClick()` on the grant + decline nodes (both below-fold) so the semantics action lands. No production-code change.
- **Files modified:** `OnboardingScreenTest.kt`.
- **Verification:** `OnboardingScreenTest` 5/5 green.
- **Committed in:** `0dd1126` (Task 3 commit).

**2. [Rule 1 - Bug] Privacy-policy body rendered source-line wraps as hard breaks**
- **Found during:** Task 1 (self-review of the first `PrivacyPolicyText.body` draft — a trimIndent raw string with mid-paragraph source wraps).
- **Issue:** A multi-line raw string turns every source newline into a rendered `\n`, so paragraphs would break mid-sentence in the `Text` composable.
- **Fix:** build the body from single-line paragraph blocks joined by `\n\n` (headings kept as an explicit `heading\nparagraph` pair), so paragraphs flow continuously and only intended breaks render.
- **Files modified:** `PrivacyPolicyText.kt`.
- **Verification:** `PrivacyPolicyTest` 10/10 green; body concepts asserted present.
- **Committed in:** `0d301d3` (Task 1 commit).

---

**Total deviations:** 2 auto-fixed (1 blocking test-harness, 1 rendering bug) + 1 documented single-source design choice (`I consent`/`Not now` not duplicated into OnboardingCopy).
**Impact on plan:** No scope creep. All artifacts, testTags, and acceptance criteria delivered; the copy single-sourcing keeps audit coverage complete without duplicate constants.

## Issues Encountered
None beyond the two auto-fixed items above.

## Known Stubs
- **`OnboardingCopy.CONSENT_WRITE_ERROR` is registered but not yet rendered.** Like `ShellCopy.WITHDRAW` (Plan 02), the consent-write failure copy is owned + audited from birth, but the visible error surface is not wired this phase — `AppGate`'s `onConsent` is fire-and-forget over `consentStore.grant()`. The string is ready for the error path Phase 20's Settings/consent UI will surface. Not a blocker: the plan's deliverable is the informed-consent flow + grant site, which is complete and test-proven.
- **PrivacyPolicyScreen dismiss is predictive-back only** (no visible close control) — intentional, to keep the policy surface rendering only the exempt `PrivacyPolicyText.body`; a richer Settings-based navigation is Phase 20.

## Threat Model Compliance
- **T-19-02 (Information disclosure — onboarding/consent/privacy copy, high):** mitigated — `OnboardingCopy` registered in `CopyAuditRegistry.sources` and scanned by `CopyAuditTest` against the ClaimsPolicy substring set (7/7); the two long-form legal constants are single-sourced and pinned by `DisclaimerTest`/`PrivacyPolicyTest` while exempt from the curated scan (honest legal phrasing not false-flagged).
- **T-19-01c (Repudiation — consent grant/decline, high):** mitigated — consent is an explicit affirmative `BloomPrimaryButton` calling the single `consentStore.grant()` site (via the preserved `onConsent`); decline is a fail-closed no-op storing nothing; disclaimer + privacy are reachable before the action (informed consent); the policy states data stays on-device and consent is withdrawable. `OnboardingScreenTest` pins grant-once / decline-grants-nothing / privacy-before-consent.
- **T-19-SC (Tampering — package installs, low/accept):** honored — no new installs; the hero/lock glyphs are bundled `ImageVector`s, and the info glyph is the existing bundled one. No third-party dependency added.

## Next Phase Readiness
- Plan 05 (phase closeout / manifest test) can proceed — the onboarding/consent surface is complete and gated by construction.
- Phase 20 (tracking) owns: the Settings entry (consent withdrawal + reminders) that will surface `CONSENT_WRITE_ERROR` and give the privacy policy a richer navigation entry.
- **Manual device checks (per 19-VALIDATION):** Bloom pixel fidelity of the onboarding/consent + privacy screens and the TalkBack focus-order sweep — not provable on the JVM lane (coverage D5, human_judgment).

## Self-Check: PASSED
- All 7 created files + 3 modified files exist on disk (verified).
- All 3 task commits present in git history: `0d301d3`, `7766ab5`, `0dd1126`.
- Real build/test evidence captured above (`:app` 97/97, `DisclaimerTest` 3/3, `PrivacyPolicyTest` 10/10, `OnboardingScreenTest` 5/5, `CopyAuditTest` 7/7, `AppGateConsentTest` 5/5, `:insights` 176/176, `assembleDebug` 12.9 MB APK).

---
*Phase: 19-app-shell-consent*
*Completed: 2026-07-09*
