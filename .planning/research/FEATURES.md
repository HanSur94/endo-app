# Feature Research

**Domain:** Multilingual support (EN/DE/FR/ES) with in-app language selection, for an existing endometriosis self-tracking and insights app (Android Kotlin/Compose + iOS SwiftUI)
**Researched:** 2026-10-31
**Confidence:** MEDIUM overall. Platform mechanics are HIGH (official docs, corroborated). Competitor behaviour is MEDIUM (vendor help pages). Patient terminology and per-language claims lists are MEDIUM/LOW: drafted from patient-org and health-portal usage. A native speaker must review them before release, and that review is already a human-gated step in PROJECT.md.

> Scope: only what v2.1 adds. Everything in PROJECT.md "Validated" is treated as existing infrastructure that this work depends on.

## How good period/health apps handle language (observed behaviour)

| App | Where the picker lives | Default | Switch behaviour | Notes |
|-----|------------------------|---------|------------------|-------|
| **Clue** (27 languages) | **iOS:** system per-app language (Settings › Clue › Language). **Android:** follows the device language, with no in-app picker documented. | Device language | iOS relaunches the app. On Android some OEMs (Xiaomi) needed a reinstall. | Clue added no in-app picker. It relies on the OS. |
| **Flo** | **iOS:** in-app (Menu › Settings › Change Language). **Android:** device language only. | Device language | iOS needs a force-quit and reopen | A "restart required" picker is an old, clunky pattern that gets user complaints. |
| **Endo-App (DiGA)** | German-first, with only the website/FAQ in English | German | n/a | It is a medical device (Class I, PZN listed). Its copy can say "Therapie"; ours cannot. Its German content uses "Endo-Belly" / "Blähbauch". |
| **Bearable** | Could not verify (LOW) | n/a | n/a | n/a |

**Platform reality (HIGH):**
- **Android 13+** has system per-app language (`LocaleManager`, plus `locales_config.xml` and `android:localeConfig`, which also list the app under System Settings › App languages). **AndroidX AppCompat** `AppCompatDelegate.setApplicationLocales()` backports this to older API levels. It persists the choice (`autoStoreLocales`) and **recreates the Activity**, so there is no app restart. An empty list means "follow system". **Dependency:** the app is `ComponentActivity` with minSdk 26 and has no AppCompat. The backport for API 26–32 needs `androidx.appcompat` and `AppCompatActivity`, or an API-33-only path plus a manual fallback. This is a STACK/ARCHITECTURE decision.
- **iOS 13+** has system per-app language under Settings › App › Language. The app relaunches on change. Apple discourages custom in-app language choosers and recommends deep-linking to Settings (`UIApplication.openSettingsURLString`). The Language row is hidden when the user has only one preferred language. The workaround is the Info.plist key `UIPrefersShowingLanguageSettings = YES` (MEDIUM: forum-sourced, verify against the current SDK).

**Opinionated recommendation:** Settings › "Language" on both platforms, showing the current language and a "System default" option.
- **Android:** a true in-app list (System default / English / Deutsch / Français / Español) via AppCompat per-app locales. It switches live through Activity recreation.
- **iOS:** an in-app row that names the current language and opens the system per-app language page. Do **not** override `AppleLanguages` or hot-swap `Bundle`s. This still meets "chosen inside the app": the entry point is in the app, the OS does the switch, and the app relaunches without data loss.

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes / Dependencies |
|---------|--------------|------------|----------------------|
| **Default = system language, with fallback to English** | Every reference app does this. Users never want to pick on first launch. | LOW | An unsupported system locale (e.g. `it`) falls back to English. Regional variants (`de-AT`, `de-CH`, `es-MX`, `fr-CA`) resolve to the base language. Use base-language resources only, no regional variants in v2.1. |
| **Language row in Settings**, listing each language by its own name ("Deutsch", not "German"), plus "System default" | Users must be able to find a way back if the device language is wrong | LOW (Android), LOW (iOS deep-link) | Depends on the existing Settings screen (`SettingsCopy`). Endonyms also mean the row stays readable while the app is in the wrong language. |
| **Switch without losing in-progress data** | A check-in half done when the language changes must survive | MEDIUM | Android recreation needs state in ViewModel/`rememberSaveable`. The v2.0 emulator pass fixed check-in persistence, so re-verify the day editor and body-map state across recreation. iOS relaunch needs state restoration or autosaved drafts. |
| **All UI copy localized** (~700 strings per platform) | A half-translated health app reads as untrustworthy | HIGH (volume, not difficulty) | Android has **no `strings.xml`**: copy lives in Kotlin `*Copy.kt` objects (`content/`). It must move to resources, or to a locale-aware copy layer, while keeping `AuditableCopy`/ClaimsPolicy able to scan every string in every language. iOS has no String Catalog yet, so `Localizable.xcstrings` would be added. |
| **Insight sentences localized** (trends, correlations, recommendations, flare) | Insights are the core value. English insights in a German UI break the product promise. | HIGH | The `:insights` module (pure JVM, mirrored in Swift `EndoInsights`) **builds English sentences inside the engine** (`TrendsEngine`, `CorrelationEngine`, `RecommendationsEngine`; `Locale.ROOT` number formatting). It must emit **structured results (template key plus typed args)**, rendered per locale in the app layer. Templates should be whole sentences per language, never concatenated fragments, because German word order and FR/ES gender agreement break concatenation. The 157-case parity suite and the HonestyGate must keep passing. |
| **Plurals and grammatical agreement** | "1 Tage" / "2 day" errors look broken | MEDIUM | Android `<plurals>` / ICU, iOS String Catalog plural variants. FR/ES add gender agreement. See the TerminologyPreference row. |
| **Locale-aware dates, numbers, units in the app** | A German user expects `31.10.2026`, `7,5 h` and a Monday week start | MEDIUM | Several places hardcode `Locale.US` (`ReportPdfRenderer`, `ReportBuilderScreen`) and `Locale.ROOT` in the insights. Use `DateTimeFormatter.ofLocalizedDate`/skeletons and `Date.FormatStyle` instead of fixed patterns. All four languages use metric, so no unit conversion is needed. Only the formatting (decimal comma) and the symbols (`h`, `min`, `Std.`) change. |
| **First day of week follows locale/region** | DE/FR/ES calendars start on Monday. A Sunday start feels American and wrong. | LOW–MEDIUM | Affects the check-in calendar, weekly trend buckets in charts/Explore, and the PDF. Take it from **region** (`WeekFields.of(locale)`, `Calendar.current.firstWeekday`), not language. An en-GB user also expects Monday. **Insight math must not change with locale.** Keep engine bucketing locale-independent or pinned, otherwise the same data yields different insights per language (a parity and honesty risk). |
| **Notifications in the current language** | A German UI with English reminders feels broken | LOW (Android), MEDIUM (iOS) | Android `ReminderNotifier` builds text at fire time, so resolving via a locale-aware `Context` is enough. iOS `UNNotificationContent` is frozen at schedule time, so pending reminders must be **rescheduled on language change and at launch**. Depends on Reminders. The no-flare-push invariant is unaffected. |
| **PDF doctor report localized**, including dates and numbers | The report is shown to a local clinician | MEDIUM | `ReportPdfRenderer` runs outside the UI, so it must take an **explicit locale** rather than the ambient one. The "self-tracked, not diagnostic" framing must be translated and audited. Longer DE/FR strings break fixed-width vector layout. Budget about 30% expansion and test pagination per language. The v1.1 "byte-identical PDF" guard on iOS needs re-baselining per locale. |
| **Onboarding, consent and in-app privacy policy localized** | GDPR Art. 9 consent must be understandable, and consent given in a language the user can't read is weak | MEDIUM | `OnboardingCopy`, `PrivacyPolicyText` (both platforms), `Disclaimer`. Legal text needs a human/legal review per language, not just a translation review. Decide which version is authoritative: German for the home market, or a statement that all versions are equivalent. |
| **Store listings in 4 languages** (Play + App Store), with localized screenshots | Store search and conversion are per locale. German users filter on German listings. | MEDIUM | `StoreListingCopy` is already ClaimsPolicy-audited (incl. store copy), so extend the audit per language. Screenshots per locale can be generated from the emulator/simulator. Play Data Safety and App Privacy answers don't change. Hosted privacy-policy URLs are needed per language (release-checklist item). |
| **Health Connect / HealthKit rationale strings localized** | The permission sheet appears in the system language next to our rationale | LOW | iOS `NSHealthShareUsageDescription` goes through `InfoPlist.xcstrings`. Android `HealthConnectCopy` plus the rationale activity. The system sheet itself is localized by the OS. |
| **User free text stays untouched** | Notes and medication names are the user's own words | LOW | Never translate, transliterate or re-case `note`, `Medication.name` or similar fields. Display them as-is in any UI language and in the PDF. Label them in the report as "your notes (verbatim)". A language-agnostic ClaimsPolicy must **not** scan user text. |
| **Export/import works across languages** | The user exports in German and imports on a phone set to Spanish (also the iOS↔Android handoff idea) | LOW, if kept that way | Entities store **locale-independent raw keys** (`typeRaw`) and ISO dates. That is correct. Invariant to add: **no localized string is ever persisted or exported**, and numbers are serialized with `Locale.ROOT`. Add a round-trip test (export under `de`, import under `es`, assert equality). Export *file names* and any human-readable header can be localized, but the parser must never depend on them. |
| **ClaimsPolicy / CopyAudit per language** | A translated health app can slip into medical-device claims that the English audit cannot see | HIGH | Separate curated forbidden lists per language, **not translations of the English list**. See "Claims boundary by language". The audit must also fail if any key lacks a translation (completeness check), to prevent a silent English fallback in release. |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes / Dependencies |
|---------|-------------------|------------|----------------------|
| **Separate "Report language" in the PDF builder** (defaults to app language) | Endo patients are often expats or travel for specialists, e.g. an English-speaking user seeing a German gynaecologist. None of Clue, Flo or the DiGA offer this. Strong fit with the doctor-report value. | LOW–MEDIUM (once the renderer takes an explicit locale, which it needs anyway) | A one-row picker in `ReportBuilderScreen`. Also offer clinician-register terms in the report with the lay term in parentheses (e.g. "Dyspareunie (Schmerzen beim Sex)"). Free text stays verbatim with a note "Notes are in the patient's own words". Highest value-to-cost item in the milestone. |
| **Live switching on Android, no restart** | Flo and Clue need a restart or reinstall, so this one is noticeably smoother | LOW (comes with AppCompat per-app locales) | Needs the state-survival work from Table Stakes. |
| **Listed in Android 13+ system "App languages"** | Users can change it from where they expect, consistent with iOS | LOW | `locales_config.xml` plus manifest attribute. Must stay in sync with the in-app list. |
| **Patient-friendly, inclusive terminology per language** (not literal translation) | Most trackers translate clinically or use "women" by default. Endo patients feel seen by lay terms like "Endo-Belly". | MEDIUM (glossary work plus native review) | One shared cross-platform glossary (milestone goal). Neutral, non-gendered phrasing: in DE "Menschen mit Endometriose", not "Patientinnen"/"Frauen", consistent with ONBD-03 and the app-only gendered-token list. |
| **Honest translation status** ("translations reviewed by a native speaker: yes/pending") | Transparency fits the honesty positioning | LOW | Optional. Only ship a language publicly after its review gate passes. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Runtime / machine translation** (on-device MT or a translation API) | "Cover more languages for free" | An API breaks the no-`INTERNET` invariant. MT output bypasses the curated ClaimsPolicy, so a correct English insight can become a causal or diagnostic claim. | Bundled, human-reviewed strings only. Add languages one at a time behind the audit. |
| **Translating user free text / notes in the PDF** | "My doctor doesn't read English" | Alters the patient's words in a medical context, needs MT, and creates liability for mistranslated symptoms | Report language chooser for app-generated text. Notes stay verbatim and are labelled as such. |
| **Persisting localized labels** (e.g. storing "Blähbauch" in the DB or export) | Seems simpler to display | Breaks import across languages, breaks insight grouping (the same factor gets two keys), and breaks parity | Persist raw enum keys and localize only at render time |
| **Per-locale insight logic** (thresholds or week bucketing changing with locale) | Falls out of using `Locale.current` in the engine | The same data gives different correlations and flare output per language, which breaks the parity suite and is dishonest | The engine stays locale-free (ISO weeks or a pinned calendar). Only the presentation layer is localized. |
| **iOS in-app override via `AppleLanguages` / custom Bundle swizzling** | Matches the Android UX exactly | Unsupported by Apple, leaves the system UI, HealthKit sheet and date formatters inconsistent, and is fragile across iOS releases | An in-app row that deep-links to system per-app language settings |
| **Forcing a language pick on first launch** | "Make sure users choose" | Adds friction to an already-heavy onboarding with consent. The system language is right for nearly everyone. | System default, with the picker in Settings and optionally a subtle link on the first onboarding screen |
| **Regional variants in v2.1** (de-CH "ss", es-MX, fr-CA) | Completeness | Multiplies review and audit cost for little gain | Base languages only, using neutral pan-regional wording (e.g. ES avoids Spain-only slang, prefers "regla"/"período" over "periodo" regionalisms). Revisit later. |
| **Literal translation of the English forbidden-term list** | Quick way to get a per-language audit | It both misses and over-matches. "will" maps to German "wird", which appears in nearly every sentence. French "prévenir" also means "to notify". Spanish "se trata de" means "it's about". | Curated per-language lists with documented collisions (the existing SUBSTRING SHARP EDGE note) |

## Claims boundary by language

The existing English policy forbids causal and medical-purpose verbs and connectives, imperatives, and alarmist terms. Below are draft per-language equivalents for the curated lists. **Confidence: MEDIUM.** A native speaker review is needed, plus ideally a German-market MDR-aware reviewer for DE, since German is the home market of the DiGA comparator and the most scrutinized.

| Concept | DE (avoid) | FR (avoid) | ES (avoid) | Safe framing |
|---------|-----------|-----------|-----------|--------------|
| Diagnose / detect | diagnostizieren, Diagnose, feststellen, nachweisen | diagnostiquer, diagnostic, détecter, dépister | diagnosticar, diagnóstico, detectar | DE "fällt auf", "zeigt sich in deinen Einträgen"; FR "on observe", "vos données montrent"; ES "se observa", "tus registros muestran" |
| Treat / cure / prevent / therapy | behandeln, Behandlung, Therapie, heilen, vorbeugen, verhindern, lindern | traiter, traitement, thérapie, guérir, soulager, prévenir* | tratar*, tratamiento, terapia, curar, prevenir, aliviar | Medication logging is "Medikamente eintragen" / "noter vos médicaments" / "registrar medicamentos", never "Therapie" |
| Cause / trigger | verursacht, Ursache, auslösen, Auslöser, Trigger (common German loanword), führt zu, wegen, weil, aufgrund, verschlimmert, verbessert | cause, causer, à cause de, déclencheur, déclenche, provoque, entraîne, parce que, en raison de, aggrave, améliore | causa, causar, provoca, desencadena, desencadenante, porque, debido a, empeora, mejora | "tritt häufiger zusammen auf mit" / "apparaît souvent en même temps que" / "suele coincidir con"; "mögliche Zusammenhänge" / "association possible" / "posible asociación" |
| Prediction certainty (EN "will") | wird … haben/bekommen as a prediction, voraussagen, Prognose | vous allez avoir, prédire, prévision | vas a tener, predecir, pronóstico | "könnte", "möglicherweise" / "pourrait", "possiblement" / "podría", "posiblemente". Use a phrase-level list, not a bare "wird"/"va" substring. |
| Alarmist | gefährlich, Gefahr, Notfall, dringend, Alarm, Warnung (for the flare card) | danger, dangereux, urgence, urgent, alerte, alarme | peligro, peligroso, urgencia, urgente, alerta, alarma | Flare card: DE "Hinweis auf einen möglichen Schub", FR "signe d'une possible poussée", ES "indicio de un posible brote" |
| Imperatives (medical) | "nimm", "setze … ab", "du solltest" | "prenez", "arrêtez", "vous devez" | "toma", "deja de", "debes" | "Vielleicht möchtest du das mit deiner Ärztin/deinem Arzt besprechen" / "Vous pourriez en parler à votre médecin" / "Podrías comentarlo con tu médico" |

\* Collisions to document in the per-language SHARP EDGE notes: FR *prévenir* also means "notify" (reminder copy), ES *tratar* sits inside "se trata de", DE *weil*/*wird* are high-frequency. These need word-boundary or phrase matching, or curated copy that avoids them.

**Also cross the line in any language:** a symptom score labelled as a "Befund"/"résultat"/"resultado clínico", a "Risiko" score, "Endometriose-Test", "Schweregrad deiner Endometriose", or stage language ("Stadium", "stade", "estadio"). Wellness wording such as Tagebuch/journal/diario, Muster/tendances/patrones and Zusammenhänge/associations/asociaciones is safe.

## Patient-friendly symptom terminology (draft glossary seed)

| Concept | DE (UI) | FR (UI) | ES (UI) | Clinical term (PDF, in parentheses) |
|---------|---------|---------|---------|------------------------|
| Endo belly | Blähbauch (Endo-Belly) | ventre gonflé (endo belly) / ballonnements | hinchazón abdominal (endo belly) | DE Meteorismus; FR/ES distension abdominale/distensión abdominal |
| Painful sex | Schmerzen beim oder nach dem Sex | douleurs pendant ou après les rapports | dolor durante o después de las relaciones sexuales | Dyspareunie / dyspareunie / dispareunia |
| Period pain | Regelschmerzen / Periodenschmerzen | règles douloureuses | dolor menstrual | Dysmenorrhoe / dysménorrhée / dismenorrea |
| Painful bowel movements | Schmerzen beim Stuhlgang | douleurs en allant à la selle | dolor al ir al baño (defecar) | Dyschezie / dyschésie / disquecia |
| Painful urination | Schmerzen beim Wasserlassen | douleurs en urinant | dolor al orinar | Dysurie / dysurie / disuria |
| Pelvic / lower-abdominal pain | Unterleibsschmerzen | douleurs pelviennes (bas-ventre) | dolor pélvico (bajo vientre) | n/a |
| Fatigue | Erschöpfung | fatigue | cansancio / fatiga | Fatigue |
| Brain fog | Konzentrationsprobleme (Brain Fog) | brouillard mental | niebla mental | n/a |
| Spotting | Schmierblutung | spotting / petits saignements | manchado | n/a |
| Flare | Schub | poussée | brote | n/a |
| Bleeding / period / menstruation (TerminologyPreference) | Blutung / Periode / Menstruation (all feminine) | saignements (m. pl.) / règles (f. pl.) / menstruation (f.) | sangrado (m.) / regla (f.) / menstruación (f.) | n/a |

**Register:** use informal address (DE "du", ES "tú"), matching Clue, Flo and the German Endo-App. FR is less settled. "vous" is the safer health-app default (MEDIUM, so decide in the glossary phase). **TerminologyPreference dependency:** the existing bleeding/period/menstruation choice is currently substituted as a bare noun (`noun = rawValue`). That works in DE (all feminine) but breaks FR/ES articles and agreement ("le/les/la", "el/la", adjectives). Sentences containing the term need **per-term full-sentence variants** (or ICU `select`), not noun interpolation. The pronoun field is free text and stays as entered.

## Feature Dependencies

```
Locale-free insights engine (structured keys + args)
    └──requires──> refactor of :insights / EndoInsights sentence builders (parity suite must stay green)
                       └──enables──> localized insight sentences
                                         └──requires──> per-language ClaimsPolicy / HonestyGate lists

String extraction (Android *Copy.kt → resources; iOS → String Catalog)
    └──requires──> AuditableCopy / CopyAudit able to enumerate every locale
    └──enables──> all UI / onboarding / privacy / store copy translations

Shared cross-platform glossary ──required by──> every translation task (do it first)

Locale-aware formatting helpers (dates, numbers, week start)
    └──required by──> app UI, charts/Explore, PDF report

In-app language picker
    ├──requires (Android)──> AppCompat per-app locales (ComponentActivity → AppCompatActivity) + locales_config
    ├──requires (iOS)──> UIPrefersShowingLanguageSettings + Settings deep-link + state restoration
    └──requires──> state survives recreation/relaunch (day editor, body map, check-in draft)

PDF renderer takes explicit locale ──enables──> separate "Report language" (differentiator)
Reminders ──requires (iOS)──> reschedule pending notifications on language change
Export/import ──requires──> invariant: no localized strings persisted; cross-locale round-trip test
TerminologyPreference ──conflicts──> noun interpolation in FR/ES (needs sentence variants)
Machine translation ──conflicts──> no-INTERNET invariant + ClaimsPolicy
```

### Dependency Notes

- **The glossary comes first:** all string work and both platforms consume it. Doing translations before it exists guarantees Android and iOS diverge.
- **The engine refactor comes before insight translation:** the engine currently produces English text. Localizing downstream of the engine would mean parsing English sentences.
- **Per-language claims audit before shipping any language:** the audit plus a completeness check is the release gate, together with the human native review.
- **Explicit-locale PDF renderer:** required anyway, because rendering is off the UI path. It makes the report-language differentiator nearly free.

## MVP Definition

### Launch With (v2.1)

- [ ] Shared glossary (EN/DE/FR/ES) incl. symptom terms, register and flare wording, because every translation depends on it
- [ ] String extraction on both platforms plus a per-locale CopyAudit with completeness check, because this is the safety gate
- [ ] Locale-free engine output (keys + args) and localized insight templates, because insights are the core value
- [ ] Per-language ClaimsPolicy lists (curated, collision-documented), because the regulatory boundary must hold in every language
- [ ] Language picker: Android in-app via AppCompat, iOS in-app row to system setting; default = system with EN fallback
- [ ] Locale-aware dates, numbers and week start in the UI, charts and PDF (engine math pinned)
- [ ] Localized notifications (iOS reschedule on change)
- [ ] Localized PDF report (explicit locale) with free text verbatim
- [ ] Localized onboarding, consent, privacy policy and Health rationale strings
- [ ] Export/import cross-locale round-trip test
- [ ] Store listing copy ×4 (audited)

### Add After Validation (v2.x)

- [ ] Separate PDF "Report language" picker. It is cheap, so pull it into v2.1 if the renderer refactor lands early.
- [ ] Localized store screenshots (can follow the copy)
- [ ] Clinician-term parentheses in the PDF

### Future Consideration

- [ ] More languages (IT, PT, NL, PL) once the per-language audit pipeline is proven
- [ ] Regional variants (de-CH, es-419, fr-CA)
- [ ] RTL languages (AR/HE), which would need a layout and chart mirroring audit

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Glossary | HIGH (indirect) | LOW | P1 |
| UI string extraction + translation | HIGH | HIGH | P1 |
| Localized insight sentences (engine refactor) | HIGH | HIGH | P1 |
| Per-language ClaimsPolicy + completeness audit | HIGH (safety) | MEDIUM | P1 |
| Language picker + system default | HIGH | LOW–MEDIUM | P1 |
| Locale formatting + week start | MEDIUM | MEDIUM | P1 |
| Localized notifications | MEDIUM | LOW–MEDIUM | P1 |
| Localized PDF report | HIGH | MEDIUM | P1 |
| Onboarding/consent/privacy localized | HIGH (legal) | MEDIUM | P1 |
| Export cross-locale invariant/test | MEDIUM | LOW | P1 |
| Store listings ×4 | MEDIUM | LOW | P1 |
| Separate report language | MEDIUM–HIGH | LOW | P2 |
| Android system "App languages" listing | LOW–MEDIUM | LOW | P2 (comes with the picker) |
| Localized screenshots | MEDIUM | MEDIUM | P2 |

## Competitor Feature Analysis

| Feature | Clue | Flo | Endo-App (DiGA) | Our Approach |
|---------|------|-----|-----------------|--------------|
| Language choice | OS-driven (iOS per-app setting) | In-app on iOS (restart), OS on Android | German-first | Android: live in-app. iOS: in-app row to OS setting. |
| Languages | 27 | Many | DE (+ EN web) | EN/DE/FR/ES, each audited |
| Report in other language | No | n/a | n/a | Optional separate report language |
| Medical framing | Wellness | Wellness | Medical device ("Therapie" allowed) | Wellness. Per-language claims audit so translations never borrow DiGA vocabulary. |
| Endo-specific lay terms | Generic | Generic | "Endo-Belly / Blähbauch" | Shared lay glossary, clinical terms only in the PDF |

## Sources

- [Android: Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages) (HIGH)
- [Zendesk Android SDK app-language notes](https://developer.zendesk.com/documentation/zendesk-web-widget-sdks/sdks/android/app_language/), [DEV: Compose multilingual](https://dev.to/sagar0_0/add-multilingual-support-multiple-languages-to-your-android-app-3ihf), [Kotlin Slack on Compose recreation](https://slack-chats.kotlinlang.org/t/2442867/reading-through-the-per-app-language-https-developer-android) (MEDIUM)
- [WWDC19 403 Creating Great Localized Experiences](https://developer.apple.com/wwdc19/403), [WWDC notes](https://wwdcnotes.com/notes/wwdc19/403) (HIGH)
- [Apple Developer Forums 721302: language setting hidden / UIPrefersShowingLanguageSettings](https://developer.apple.com/forums/thread/721302) (MEDIUM)
- [Clue: How to change the language](https://support.helloclue.com/hc/en-us/articles/4404372946580-How-can-I-change-the-language-in-the-app), [Clue DE article](https://helloclue.com/de/artikel/nutzung-von-clue/wie-man-die-sprache-in-clue-aendert) (MEDIUM)
- [Flo: How can I change the language](https://help.flo.health/hc/en-us/articles/360015114832-How-can-I-change-the-language-of-Flo) (MEDIUM)
- [Endo-App: Endo-Belly or Bloated Belly](https://endometriose.app/en/endometriosis-symptom-endobelly-or-bloated-belly/), [Endo-App Gebrauchsanweisung](https://diga.endometriose.app/wp-content/uploads/2024/03/Gebrauchsanweisung_Endo_App.pdf), [Verordnungshilfe](https://endometriose.app/wp-content/uploads/2024/01/Verordnungshilfe.pdf) (MEDIUM)
- [sante.fr: La dyspareunie](https://www.sante.fr/endometriose/la-dyspareunie-douleurs-lors-des-rapports-sexuels), [sante.fr: symptômes](https://www.sante.fr/node/1263779), [EndoFrance](https://www.endofrance.org/wp-content/uploads/2022/07/Endometriose-et-Vie-Affective-et-Sexuelle.pdf) (MEDIUM)
- [Endo Belly narrative review (PMC10671958)](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10671958/) (HIGH for concept)
- Codebase inspection: `android/app/src/main/kotlin/com/endoapp/app/content/*Copy.kt`, `report/ReportPdfRenderer.kt` (`Locale.US`), `insights/*Engine.kt` (`Locale.ROOT`, English sentence building), `insights/ForbiddenTerms.kt`, `reminders/ReminderNotifier.kt`, `MainActivity.kt` (`ComponentActivity`, minSdk 26), `Sources/EndoApp/Terminology/TerminologyPreference.swift`
- ES/DE claims lists and glossary: drafted (LOW–MEDIUM). Native-speaker review is required.

---
*Feature research for: multilingual (EN/DE/FR/ES) endometriosis tracker, v2.1*
*Researched: 2026-10-31*
