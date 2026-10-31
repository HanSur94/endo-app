# PRIVACY-POLICY.md — the text to HOST publicly

> **This is the content the public privacy-policy page must mirror.** It is the
> **verbatim** mirror of `PrivacyPolicyText.body` in
> `android/app/src/main/kotlin/com/endoapp/app/content/PrivacyPolicyText.kt` — the same
> text the in-app Health Connect permissions-rationale surface
> (`PermissionsRationaleActivity`) renders. **Single source, no drift** (RESEARCH
> Pitfall 3): if the in-app policy changes, change `PrivacyPolicyText.body` and re-sync
> this doc. Do not edit the wording here independently.

## Hosting requirements (VERIFIED Play policy)

The hosted page must be at an **active, publicly accessible, non-geofenced HTTPS URL**,
**not a PDF**, and **non-editable**, applying specifically to this app
[VERIFIED: support.google.com — store listing / User Data policy]. The **same** URL
must appear in (a) Play Console (store listing + Data safety), (b) the in-app rationale
surface, and (c) the hosted website.

- **Privacy policy URL:** `https://replace-me.github.io/endo-app/privacy-policy`
  — this is the current `PrivacyPolicyText.PRIVACY_POLICY_URL` value, a **PLACEHOLDER**
  (`replace-me` host). The user must host the page below and replace this constant with
  the real URL, entering the SAME URL in Console (see `RELEASE-CHECKLIST.md`, item 2).
  The URL's shape is guarded by `PrivacyPolicyUrlConsistencyTest` (well-formed https,
  non-PDF, the only https literal in the content package); only its hosted *value* is
  user-supplied.
- **Contact email:** `sannahrush@googlemail.com` (the `PrivacyPolicyText.contactEmail`
  single source — the data controller's contact).

---

## Privacy policy (mirror of `PrivacyPolicyText.body`, verbatim)

Your privacy is the whole point.

### Where your data lives

Everything you log in Endo-App is stored on this device only. There is no cloud copy
and no backup to any server we run. Your health data does not leave your phone unless
you export it yourself.

### No servers, no accounts

Endo-App has no servers of its own. There is no account to create and no login. Your
health data never travels to any backend we operate, because we do not operate one.

### No third-party sharing, no analytics

There is no third-party sharing of your data and no third-party analytics or
advertising. We do not embed tracking SDKs. We do not sell, rent, or share your
information with anyone.

### Who is responsible

This app is built by an independent developer, who is the data controller for the
purposes of data-protection law. You can get in touch at sannahrush@googlemail.com.

### Your control

You give explicit consent before any health data is processed, and you can withdraw
that consent at any time in Settings. Because your data lives only on this device, you
stay in control of it.

---

## Health Connect note for the hosted page

Play's Health apps / User Data policy requires that Health Connect data be **described
in the privacy policy**. This app reads Health Connect data **read-only** and processes
it **on the device only** — the "Where your data lives" and "No third-party sharing"
sections above already cover this. When you host the page, you may add a short sentence
naming the Health Connect data types read (menstruation, sleep, exercise, nutrition,
and health-data history) if you want the description to be explicit; keep it consistent
with `HEALTH-APPS-DECLARATION.md`.
