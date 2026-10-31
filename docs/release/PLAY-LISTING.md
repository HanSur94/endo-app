# PLAY-LISTING.md — Google Play store listing (ready-to-paste)

> **READY-TO-PASTE Console answers — NOT submitted state.** These are the exact
> strings the **user** pastes into Google Play Console → **Grow → Store presence →
> Main store listing**. The repo never holds submitted Console state (threat T-25-05).
> **Re-verify the exact field labels live in Console at submission** (ASSUMED A5 —
> Google does not publish the form wording; the *answers* below are canonical).

## Source of truth

Every string below is copied **verbatim** from
`android/app/src/main/kotlin/com/endoapp/app/content/StoreListingCopy.kt` — the
registered, auditable copy surface. Do **not** retype divergent copy here; if the
copy changes, change `StoreListingCopy.kt` and re-run its tests, then re-sync this doc.

**Corroborating green tests (Plan 25-01):**
- `StoreListingCopyTest` — pins the Play field-length limits (title ≤ 30, short ≤ 80,
  full ≤ 4000) and the first-paragraph disclaimer must-contain, and asserts
  `StoreListingCopy` is registered in `CopyAuditRegistry.sources`. (4/4 green.)
- `CopyAuditTest` — scans every audited store string against the same ClaimsPolicy
  substring gate as in-app copy (no causal / imperative / alarmist / gendered /
  false-precision language). (10/10 green.)

The **first paragraph** of the full description is `Disclaimer.text` verbatim (single
source of truth) — it carries Play's exact required non-medical-device wording. It is
deliberately exempt from the substring scan (it legitimately contains
"diagnose/treat/cure/prevent" as negations) and pinned instead by
`StoreListingCopyTest`'s first-paragraph must-contain.

---

## App title — paste into "App name" (≤ 30 characters; this value is 27)

```
Endo: Endometriosis Tracker
```

## Short description — paste into "Short description" (≤ 80 characters; this value is 77)

```
Private, on-device logging for symptoms, cycle, and daily factors. Yours only.
```

## Full description — paste into "Full description" (≤ 4,000 characters)

Paragraph 1 is the required not-a-medical-device disclaimer (`Disclaimer.text`
verbatim) and **must remain the first paragraph**. Paragraphs are separated by a
blank line.

```
Endo-App is an informational self-tracking tool. It is not medical advice and not a medical device. It does not diagnose, treat, cure, or prevent any condition. The patterns it surfaces come from your own data and are meant to help you understand your body and have better conversations with your clinician — not to replace professional care.

Endo-App helps you keep a private record of how you feel day to day — pain, bleeding, mood, sleep, food, and movement — so patterns in your own data become easier to see over time.

Everything lives on your phone. No account, no sign-in, no cloud, and no third-party tracking. Your health data leaves your phone only if you choose to export it yourself.

See gentle, plain-language views of your trends, cycle phases, and possible associations in your own data — always informational, worth discussing with your clinician.

Share a clean, printable report with your clinician when it helps.
```

## What's-new / release notes — paste into "What's new" for the first release

```
First release of Endo-App for Android: private, on-device symptom and cycle logging with plain-language insights.
```

---

## Why this copy is honest and policy-safe

- **Disclaimer first (VERIFIED: support.google.com/16679511).** Non-medical-device
  health apps must include "not a medical device and does not diagnose, treat, cure,
  or prevent any [medical] condition" in the description and remind users to consult a
  healthcare professional. Paragraph 1 carries this exact phrasing plus the "have
  better conversations with your clinician" reminder. (Official policy requires the
  disclaimer "in the description"; placing it *first* exceeds that with zero downside —
  a locked CONTEXT decision.)
- **No diagnostic / alarmist / gendered / imperative claims.** Every audited string
  passes the same `CopyAudit` substring gate as in-app copy — mechanically, not by
  review (`CopyAuditTest` 10/10 green).
- **Matches the codebase story.** "Everything lives on your phone … no third-party
  tracking" is corroborated by the no-INTERNET manifest and no-analytics catalog (see
  `DATA-SAFETY.md`).

## Notes for the human submitting

- The store **title** (`StoreListingCopy.title`) is independent of the app's
  `android:label` ("Endo") — finalizing `android:label` is a separate release-time
  decision (see `RELEASE-CHECKLIST.md`, ASSUMED A-identity).
- Graphics assets (icon, feature graphic, screenshots) are **not** covered by this doc
  set — they are a separate human deliverable in Console.
