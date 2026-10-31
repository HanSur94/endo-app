# Store listing copy (source of truth for App Store Connect + Play Console)

Rules this copy obeys — the same ones the app's copy-audit tests enforce (`ClaimsPolicy`):
no diagnose/detect/treat/cure/prevent/trigger/causes, no future-tense promises ("will"),
no gendered default language. Wellness self-tracking, **not a medical device**.

Keep the consoles' text identical to the blocks below. If a reviewer asks for a change, change it
here first, then in both consoles.

---

## Identity

| Field | Value |
|---|---|
| Name (both stores) | **OpenEndo** |
| Bundle / package | `app.openendo.ios` / `app.openendo.android` |
| SKU (Apple) | `openendo-ios` |
| Category | Health & Fitness (secondary: Medical) |
| Age rating | 12+ (medical/treatment information, no objectionable content) |
| Price | Free, no in-app purchases |
| Privacy policy URL | https://hansur94.github.io/endo-app/privacy |
| Support URL | https://hansur94.github.io/endo-app/ |
| Marketing URL | https://github.com/HanSur94/endo-app |
| Contact | sannahrush@googlemail.com |

## Apple

**Subtitle (30):** Private endo symptom journal

**Promotional text (170):** Free, open source, no account. Log in under a minute and get charts that
describe your own patterns — on your device and in your private iCloud only.

**Description:**

```
OpenEndo is a free, privacy-first self-tracking journal for endometriosis. No account, no
subscription, no analytics. Your entries stay on your device and, if you turn on sync, in your own
private iCloud.

WHAT YOU CAN LOG
• Pain areas on a body map, with severity
• Bleeding, fatigue, mood, digestive discomfort
• Food and drink, activity, sleep
• Medication and free-text notes

The fast daily check-in takes under a minute and is prefilled with yesterday's medication, sleep and
activity — never with yesterday's symptoms. The detailed day log is there when you want more.

WHAT YOU GET BACK
• Trend charts you can scrub day by day, with honest gaps instead of invented zeros
• Your cycle phases placed next to your symptoms, tolerant of irregular or suppressed cycles
• A "possible association" card when two things move together in your own data — descriptive only,
  no fitted lines and no scores
• An early heads-up when your recent entries resemble harder weeks earlier in your own history
• A doctor-ready PDF summary with per-source labels and a "self-tracked, not diagnostic" header
• Optional read-only import from Apple Health, so you do not log the same thing twice

PRIVACY IS THE PRODUCT
Everything is stored on your device. Sync uses your private iCloud database; there is no server of
ours, no account to create, no third-party SDK and no advertising. You give explicit consent before
any health data is processed, and you can withdraw it in Settings.

WHAT OPENENDO IS NOT
OpenEndo is wellness self-tracking software. It is not a medical device and does not provide a
diagnosis or treatment, and it does not replace care from a clinician. Insights are descriptions of
patterns in your own data — bring them to an appointment.

OPEN SOURCE
MIT licensed. The full source, the design record and the issue tracker are on GitHub.
```

**Keywords (100):** endometriosis,symptom tracker,pain diary,cycle,chronic pain,health journal,privacy,offline

**App Review notes:**

```
No account or login is needed — the app opens straight into onboarding with a consent screen.
HealthKit is read-only and optional: it is requested from Settings → Apple Health import, and
declining leaves the app fully usable. Sync is CloudKit in the user's private database and needs an
iCloud account; without one the app runs local-only. To reach a populated state quickly, log one
fast check-in from the home screen. OpenEndo is wellness self-tracking, not a medical device.
```

**App Privacy answers:** if all data stays in the on-device store and the user's private CloudKit
database → *Data Not Collected*. Re-verify against Apple's CloudKit guidance before submitting.

## Google Play

**Short description (80):** Private endo symptom journal. On-device, no account, open source.

**Full description:** same body as the Apple description (Play strips the bullet glyphs fine).

**Data safety:** health & fitness data — collected: no; processed on-device; deletable in-app;
no sharing; no analytics. Health Connect permissions declared individually with the privacy URL.

**Content rating:** IARC questionnaire — health/medical topic, no ads, not directed at children.

## German (both stores, when localised)

**Untertitel:** Privates Endo-Tagebuch
**Kurzbeschreibung:** Privates Symptom-Tagebuch bei Endometriose. Nur auf dem Gerät, ohne Konto.
**Beschreibung:** Übersetzung des englischen Textes, gleiche Regeln — kein „diagnostiziert",
„erkennt", „behandelt", „löst aus"; keine Zukunftsversprechen; keine geschlechtsspezifische
Standardansprache.
