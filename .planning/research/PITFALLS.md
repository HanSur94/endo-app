# Pitfalls Research

**Domain:** Adding EN/DE/FR/ES localization and an in-app language picker to an existing privacy-first endometriosis tracker (Android Kotlin/Compose + iOS SwiftUI), under a wellness-not-medical-device claims policy
**Researched:** 2026-10-31
**Confidence:** HIGH for codebase-specific findings (verified by reading the code); HIGH for Android per-app-language and iOS localization mechanics (stable platform behavior since Android 13 / iOS 13, from training knowledge, not re-verified online this session); MEDIUM for the regulatory notes (MDR intended-purpose logic and German HWG are well established, but have counsel confirm them per store region)

## Codebase facts that shape every pitfall below

Checked in the `android/` tree on branch `claude/endo-android-version-e608a7`:

- **No Android string resources exist.** `app/src/main/res/values/` holds only `colors.xml` and `themes.xml` (0 `<string>` entries). All copy is hard-coded in Kotlin `*Copy.kt` objects (`ShellCopy`, `DayLogCopy`, `ExportImportCopy`, `StoreListingCopy`, ...) and in the engines. So localizing means extracting every string first, not just translating.
- **Engines build English sentences by concatenation.** Example: `CorrelationEngine.kt:382`: `"Higher ${targetLabel.lowercase()} severity possibly associated with logging $factorLabel"`. `Factor.displayLabel` returns hard-coded English (`"poor sleep"`, `"high stress"`, `"${phaseLabel(phase)} phase"`). `:insights` is pure JVM and cannot see Android resources. The same pattern exists in the iOS `EndoInsights` SPM package (Foundation-only).
- **Rendered insight text is persisted.** `EndoRepository.replaceCachedInsights` stores `payload = insightJson.encodeToString(insight)`, which includes the English `headline`/`detail`. `InsightFingerprint.make` is `count|latestDayEpoch|dismissed` and has **no locale component**, so after a language switch the cache stays valid and keeps serving English.
- **Policy lists are English-only substrings.** `ForbiddenTerms.causal/imperative/alarmist` (in `:insights`, drift-guarded against Swift) plus `ClaimsPolicy.forbiddenGenderedTokens` (`"she"`, `"her"`, ...), `forbiddenPrecisionTokens` (`"%"`, `"p="`, ...) and `allowedFraming` (English phrases). `HonestyGate` scans emitted insight copy at runtime, and `CopyAuditTest` scans the registered `AuditableCopy` catalog.
- **`MainActivity : ComponentActivity()`, minSdk 26, no AppCompat dependency, no `localeConfig`.** In-app language on API 26–32 needs the AppCompat backport.
- **Formatting is pinned to US.** `ReportPdfRenderer` uses `DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)` and `String.format(Locale.US, "%.1f h", ...)`. `CorrelationEngine` uses `Locale.ROOT` for `%.1f`.
- **Reminders** go through `CheckInReminderWorker` (a `CoroutineWorker` using `applicationContext`) and a `NotificationChannel` that `ReminderNotifier` creates once.
- **Tests:** 296 `onNodeWithTag` and 53 `onNodeWithText`/`onAllNodesWithText` lookups in Android tests. There is also a 157-case Kotlin↔Swift insights parity suite that compares engine output.
- **Export:** `ExportEnvelope` deliberately leaves category labels out (T-18-13). That was a good decision, and the localization work has to keep it.

---

## Critical Pitfalls

### Pitfall 1: The substring claims audit quietly stops protecting translated copy

**What goes wrong:**
The English substring lists run against DE/FR/ES text and match almost nothing, so the audit passes while translations carry forbidden meanings. Running the English lists against foreign text also produces false positives that push translators toward worse wording. Examples:
- **German compounds and inflection:** "verursacht" / "Ursache" / "ursächlich" share no substring with `cause`. The same goes for "Auslöser" (trigger), "Behandlung" / "behandeln" / "behandelt", "Diagnose" / "diagnostiziert", "heilen" / "Heilung", "lindert", "verschlimmert", "verhindert", "führt zu", "aufgrund", "wegen", "weil". Compounds like "Schmerzauslöser" and "Behandlungserfolg" hide the stem in the middle of a word.
- **French:** "provoque", "entraîne", "à cause de", "parce que", "dû à" / "due à", "en raison de", "guérir" / "guérison", "soigner", "traitement", "diagnostic", "aggrave", "améliore", "prévient".
- **Spanish:** "causa" / "provoca", "debido a", "porque", "por culpa de", "curar" / "cura", "tratamiento" / "tratar", "diagnóstico", "empeora", "mejora", "previene", "alivia".
- **Imperatives in other languages:** DE "solltest" / "sollten" / "musst" / "müssen", "vermeide", "nimm", "hör auf", "versuche". FR "vous devez" / "il faut", "évitez", "prenez", "arrêtez", "essayez". ES "debes" / "deberías", "evita", "toma", "deja de", "intenta".
- **Accent and normalization traps:** "guérir" stored as NFD (e + combining accent) does not match an NFC "guérir" pattern. `lowercase()` without a locale is fine for these four languages, but "ß" vs "SS" and "É" vs "é" folding must be handled on purpose.
- **False positives from the English lists:** English `use` sits inside German "Pause" (a break). English `her` sits inside "vorher", "daher", "bisher" and "hervor". English `will` is the German "will" ("wants"). English `cure` is the French noun "cure" (a spa or treatment course). English `try` and `cut` hit fragments too. The gendered token `sie` is impossible in German because formal "Sie" means "you". The FR/ES gendered tokens `elle` / `ella` sit inside other words.
- **Forbidden words in safe phrases:** French "entraînement" (training/workout, used in activity copy) contains "entraîne" (leads to). German "Behandlungsteam" (care team), a natural way to say "your clinician", contains "Behandlung". Spanish "tratar" means both "to treat" and "to try".

**Why it happens:**
The audit was designed for one language. It catches English substrings and relies on curated copy to avoid the innocent host words. Teams localize the strings and forget that the safety net is language-bound. The drift guard (`ClaimsPolicyDriftTests`) then keeps both platforms green on English-only lists.

**How to avoid:**
- Make the policy **per-locale data**: `ForbiddenTerms.forLocale(lang)` with causal, imperative, alarmist and gendered lists for each of EN/DE/FR/ES. Keep it in `:insights` as the single Kotlin source and mirror it in Swift. Extend the drift test to compare per language (byte-equal on both platforms).
- Match on **NFC-normalized, locale-folded text** (`Normalizer.normalize(s, NFC)` then `lowercase(Locale.ROOT)`; also fold "ß"→"ss" for DE). For DE, match **stems inside words** (substring is correct there, since compounds hide stems). For FR/ES, use word-boundary or stem patterns so elision forms like "l'entraîne" are still caught.
- Give each language an **allow-list of safe host words**: whole tokens exempted after a hit, e.g. FR "entraînement", DE "Pause". Never exempt by weakening the stem.
- Run **every language's audit against every catalog**, not only English, and register every translated string as `AuditableCopy`.
- Make `allowedFraming` per-locale. The checks that require disclaimers to contain "not medical advice" need the German equivalent ("keine medizinische Beratung", "kein Medizinprodukt"), which a lawyer or native speaker must approve.
- Write **mutation tests**: a seeded set of known-bad sentences per language (e.g. DE "Gluten verursacht deine Schmerzen") must fail the gate, and a seeded set of known-good sentences must pass. This shows the per-language lists actually bite.
- Re-check the gendered rule per language. DE: forbid "Frau" / "Frauen" / "weiblich", allow "Sie". FR/ES: the real risk is **gendered adjective agreement** ("fatiguée", "cansada") that assumes the user is a woman. Prefer constructions that avoid agreement ("Fatigue : niveau ...", "Nivel de cansancio") over inclusive punctuation ("fatigué·e", "cansado/a"), which screen readers handle poorly.
- Make `forbiddenPrecisionTokens` locale-aware: FR writes "15 %" with a narrow no-break space, and decimals use commas ("r = 0,4").

**Warning signs:**
- A DE/FR/ES file passes `CopyAuditTest` on its first run.
- Zero hits per language, even on the seeded bad sentences.
- Translators asking "why was 'Pause' rejected?"
- `allowedFraming` assertions skipped or `@Ignore`d for non-English locales.

**Phase to address:**
The **first** phase, a claims-policy-per-locale foundation, before any translated string lands. The audit must exist before translations are added, not after.

---

### Pitfall 2: Machine or LLM translation turns hedged descriptive copy into medical, causal or imperative claims

**What goes wrong:**
English "possibly associated with" becomes DE "hängt mit ... zusammen" or "führt zu", FR "est lié à" / "provoque", ES "está relacionado con" / "causa". The hedge is lost and the sentence becomes a causal claim. Other examples:
- "Flare early warning" becomes "Krankheitsschub-Warnung" or "alerte de crise", which sounds alarmist or diagnostic.
- "Worth discussing with your clinician" becomes "Besprich das mit deiner Ärztin" or "Parlez-en à votre médecin". Both are **imperatives**, which breaks the D-04 discussion-prompt rule.
- "Self-tracking" becomes "Selbstdiagnose".
- "Pain relief" becomes "Schmerzbehandlung", "traitement de la douleur" or "tratamiento del dolor", all treatment claims.
- The three confidence words (low/medium/high) become "sicher" / "certain" / "seguro", which claims certainty.

Because the EU MDR judges intended purpose from the claims made, a German string that says the app "erkennt" (detects) flares can count as a medical-device claim in Germany even if the English is clean.

**Why it happens:**
Fluent translation optimizes for natural phrasing, and natural German or French health writing is assertive and imperative. Translators (human or LLM) don't see `ClaimsPolicy`, don't know D-04/D-08/D-11, and translate each string on its own without the full sentence context.

**How to avoid:**
- Build a **shared cross-platform glossary** (one file, read by both platforms' audits) with three parts: approved renderings for every policy-loaded concept (possible association, flare, confidence low/medium/high, clinician, pain relief, not medical advice, not a medical device, self-tracking, early warning), **forbidden alternatives** for each, and the **register** decision (DE "du" vs "Sie", FR "tu" vs "vous", ES "tú" vs "usted"; pick one and enforce it).
- Give Claude the glossary, the claims policy and a context note for **every string** (where it appears, the full sentence, its placeholders). Draft-then-audit: no translated string merges until the per-locale audit (Pitfall 1) passes.
- Rephrase recommendations as non-imperative statements in each language, e.g. DE "Das könnte ein Thema für dein nächstes Arztgespräch sein", FR "Cela pourrait être un sujet à aborder avec votre médecin", ES "Podría ser un tema para comentar con tu profesional de salud".
- Keep the **native-speaker review as an explicit, human-gated release item**, with a claims checklist the reviewer signs per language. Don't mark it done from Claude's own self-check.
- Do a **back-translation spot check** (DE→EN by a separate pass) on every insight template and disclaimer. The back-translation must keep "possibly" and "descriptive only".

**Warning signs:**
- Translated recommendation strings end in "!" or start with a verb.
- No glossary entry exists for "possible association".
- The same English term is rendered two different ways on Android and iOS.
- The reviewer checklist is checked off with no named reviewer.

**Phase to address:**
The **glossary and translation phase** (after the policy foundation, before UI extraction is finished). Review sign-off sits in the **release phase**.

---

### Pitfall 3: Engine sentences built by concatenating fragments cannot be translated grammatically

**What goes wrong:**
`"Higher ${targetLabel.lowercase()} severity possibly associated with logging $factorLabel"` assumes English word order, English noun case and English capitalization:
- **German** capitalizes nouns, so `lowercase()` produces "schmerz", which is wrong. German needs the right case after prepositions ("an Tagen mit **schlechtem** Schlaf", dative, which changes the adjective ending) and puts the verb last in subordinate clauses.
- **French and Spanish** need gender and number agreement between article, adjective and noun ("**la** fatigue", "**le** sommeil", "**un** stress **élevé**", "**el** estrés **alto**") and place adjectives after the noun.
- `"${phaseLabel(phase)} phase"` becomes "Lutealphase" in German (one compound word), "phase lutéale" in French (reversed order) and "fase lútea" in Spanish.
- **Plurals:** "across N observed days". French treats 0 and 1 as singular ("0 jour"). German, French and Spanish all need the plural rules from ICU/CLDR, not `if (n == 1)`.
- `capitalizedLikeSwift()` capitalizes the first letter, which breaks when a sentence starts with a placeholder.

If `:insights` returns finished English strings, the only fix is to translate them later, and that ends with string-matching on English to substitute words.

**Why it happens:**
The engines were made the correctness anchor and kept pure JVM / Foundation-only, so they emit display strings directly. Concatenation feels cheap until a second language arrives.

**How to avoid:**
- Change the engine contract so `Insight` carries a **structured message**, not prose: `messageKey` (e.g. `correlation.severity.higher`) plus typed arguments (`target: SymptomType`, `factor: Factor`, `pairedDays: Int`, `liftBucket`, `confidence`). The **app layer renders** it with full-sentence templates per language. Translators then see whole sentences, never fragments.
- Write **one complete template per (kind × variant)**. Where a factor's grammar changes the sentence, use ICU `select` on a grammatical-gender or case attribute stored with the factor's localized label (`{factor_gender, select, fem {...} masc {...}}`). Alternatively, rephrase so the factor is never inflected (DE "an Tagen mit Eintrag: **Schlechter Schlaf**"). This "label-as-quoted-noun" pattern is the most robust choice for four languages.
- Use **plural resources** (Android `pluralStringResource` / ICU `MessageFormat`; iOS String Catalog plural variants) for every count.
- Keep the **parity suite comparing structured output** (key + args), not English text, so Kotlin↔Swift parity survives localization. Add a separate per-locale render test.
- The HonestyGate claims scan must run on the **rendered** sentence in every locale, not on the key. Render all four languages in tests and audit each.

**Warning signs:**
- `lowercase()`, `capitalize` or string templates with `$label` in `:insights` / `EndoInsights` after the refactor.
- A translator asks "what comes before this fragment?"
- The parity suite starts failing as soon as any string changes.
- German output with lowercase nouns.

**Phase to address:**
A dedicated **engine message-contract phase** on both platforms. It sits after the policy foundation and before engine translation, and it is the riskiest refactor of the milestone, since it touches the parity anchor.

---

### Pitfall 4: Rendered text leaks into persisted data, caches and export files

**What goes wrong:**
- **Stale-language cache:** `CachedInsight.payload` stores English `headline`/`detail`. Because the fingerprint has no locale, users who switch to German keep seeing English insights (or a mix) until they log new data.
- **Mixed-language exports:** If any new code writes display labels into Room rows (e.g. a symptom name, a "phase" string, a dismissed-insight label) or into `ExportEnvelope`, an export made in German and imported on an iPhone set to English shows German labels. Worse, the importer may fail to map them back to enums.
- **Locale-formatted numbers or dates** in JSON or CSV: `String.format("%.1f")` with the default locale writes "2,5" in DE and corrupts parsing.
- **Translated user content:** Custom food tags and notes typed by the user must never be translated or normalized.

**Why it happens:**
Localization changes the meaning of "a string" from a constant to locale-dependent output. Persisted English strings were harmless while English was the only language.

**How to avoid:**
- Persist **only stable keys and raw values** (`signalKey`, `kind.raw`, enum raws, numbers, ISO-8601 dates) and render at display time. Change `CachedInsight` to store the structured message from Pitfall 3, or add the **app language tag to the fingerprint** so a switch forces a recompute. Prefer the structured message: it also keeps the PDF consistent.
- Add a **schema invariant test**: export in DE, import, and compare to an export in EN. They must be byte-identical (except timestamps). Scan the export JSON for any string in the translation catalogs; finding one is a failure.
- Keep `Locale.ROOT` / ISO formatting for everything serialized. Add a lint or test that bans `String.format` without an explicit `Locale`, and bans `DateTimeFormatter.ofPattern` without one, in `export/`, `data/` and `:insights`.
- If the Room schema changes, use an explicit migration and a migration test. Never drop the cache table destructively in a way that touches user rows.

**Warning signs:**
- English insights after switching to German.
- `payload` JSON containing "possibly associated".
- An import test only ever run in en-US.

**Phase to address:**
The **engine message-contract phase**, for the cache and payload shape. The **export/import invariant** belongs in the Android localization-infrastructure phase, and the same test is needed on iOS.

---

### Pitfall 5: Android per-app language with `ComponentActivity` on minSdk 26

**What goes wrong:**
- `LocaleManager` (the platform per-app language API) only exists on **API 33+**. On API 26–32, `AppCompatDelegate.setApplicationLocales()` only works when the host is an **`AppCompatActivity`**. That requires the `appcompat` dependency **and** a `Theme.AppCompat` (or MaterialComponents/Material3 bridge) descendant theme. A plain Compose theme crashes with "You need to use a Theme.AppCompat theme".
- Without `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService" ... autoStoreLocales=true>` (or your own persistence), the choice is **forgotten on restart** on API < 33.
- Without `android:localeConfig` (generated by `androidResources { generateLocaleConfig = true }` plus `resources.properties` `unqualifiedResLocale=en-US`), the app doesn't appear in **Android 13+ system per-app language settings**, and the in-app picker and system setting can disagree.
- Calling `setApplicationLocales` **recreates the activity**. Compose state in plain `remember` is lost, such as a half-filled check-in (the persistence bug fixed in the 2026-10-31 emulator pass is exactly this kind of state). Dialogs and bottom sheets close, and scroll position resets. Declaring `configChanges="locale|layoutDirection"` to avoid recreation means Compose won't re-read resources by itself.
- `Locale.getDefault()` and `applicationContext.resources` **do not reliably follow the app locale on API < 33**, so code that formats via `Locale.getDefault()` or the app context shows the system language.

**Why it happens:**
The v2.0 port chose `ComponentActivity` (correct for pure Compose), and the localization docs assume AppCompat. Emulator testing usually happens on API 34+, where `LocaleManager` hides all of this.

**How to avoid:**
- Decide early: **switch `MainActivity` (and the other `<activity>` entries in the manifest) to `AppCompatActivity`** with a minimal `Theme.AppCompat.DayNight.NoActionBar` parent theme, enable `autoStoreLocales`, and add `generateLocaleConfig`. Use **`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`** everywhere, which delegates to `LocaleManager` on 33+. An empty list means "follow system" (the required default).
- Read the effective locale from **`LocalConfiguration.current.locales[0]`** in Compose and `context.resources.configuration.locales[0]` elsewhere, never `Locale.getDefault()`.
- Audit every screen for state that must survive recreation: `rememberSaveable` / ViewModel `SavedStateHandle` for draft check-ins and form inputs. Deliberately do **not** save the export passphrase (security). Instead, document that a language switch clears it.
- Put the picker where recreation is harmless (Settings), apply on confirm, and show the new language at once.
- Test on an **API 26–32 emulator** as well as 34+.

**Warning signs:**
- The language reverts after a cold start on API 30.
- The app is missing from system "App languages" on Android 14.
- A crash on launch after adding appcompat.
- A user loses a draft entry when switching language.

**Phase to address:**
The **Android localization infrastructure phase** (resource extraction plus picker plumbing), before mass translation.

---

### Pitfall 6: Background work, notifications and system-owned UI show the wrong language

**What goes wrong:**
- `CheckInReminderWorker` runs with `applicationContext`. On API < 33 under AppCompat, that context keeps the **system** locale, so the reminder title and body arrive in English while the app is German. The same happens if the text was resolved when the reminder was **scheduled** and stored in `inputData`.
- The `NotificationChannel` name and description are set once at creation and shown in system settings. They stay in the old language unless the channel is re-created (calling `createNotificationChannel` with the same ID updates name and description) after a language change.
- Reminder times: a hard-coded "h:mm a" shows 12h in Germany, or 24h for US users. You need `DateFormat.is24HourFormat(context)` on Android and `.formatted(date: .omitted, time: .shortened)` on iOS.
- Some UI belongs to the system and follows the **system** language no matter what the app picks: the Health Connect permission UI, HealthKit's authorization sheet, the share sheet, document pickers, date and time pickers on some versions, and iOS `NSHealthShareUsageDescription`, which comes from `InfoPlist.strings` / an Info.plist String Catalog in the **device** language.

**Why it happens:**
Workers and notifications live outside the activity's configuration. Developers test with "switch language → look at the screen", never "switch language → wait for tomorrow's reminder".

**How to avoid:**
- In the worker, resolve strings from a context that follows the app locale. On API < 33, build it with `createConfigurationContext(config.setLocales(AppCompatDelegate.getApplicationLocales()))`. Resolve at **fire time**, never at schedule time, and store only keys or IDs in `inputData`.
- On every language change (and on app start), re-create the channel with the localized name and description.
- Localize `NSHealthShareUsageDescription` and other Info.plist strings and the Health Connect rationale screen in all four languages. **Document** that system-owned sheets follow the device language, and phrase the app's own rationale so a mismatch is not confusing.
- Robolectric test: set the app locale to DE, run the worker with `TestListenableWorkerBuilder`, and assert the posted notification's German title via `ShadowNotificationManager`.

**Warning signs:**
- English notification text in a German build.
- System notification settings listing an English channel name.
- Locale code in the worker that uses `Locale.getDefault()`.

**Phase to address:**
The **Android infrastructure phase** (worker and channel) and the **iOS infrastructure phase** (Info.plist strings). The reminder time format is part of the **locale formatting phase**.

---

### Pitfall 7: iOS has no supported in-app language override, and the obvious hacks break

**What goes wrong:**
- Apple's supported path is **per-app language in Settings**. It only appears when the app bundle declares more than one localization (`knownRegions` / `CFBundleLocalizations`, with `developmentRegion: en` set in XcodeGen's `project.yml`). In-app overrides use one of these hacks, each with a cost:
  - (a) Writing `AppleLanguages` to `UserDefaults` only takes effect after **relaunch**, and confuses users.
  - (b) Swizzling `Bundle.main` to an `.lproj` is fragile, isn't applied to `Bundle.module` (SPM), and is unsupported.
  - (c) `.environment(\.locale, ...)` in SwiftUI **changes formatting only**. `Text("key")` / `String(localized:)` still look up strings from the process's preferred language, so you get German dates inside English sentences.
- **SPM resources:** `EndoInsights` is a Foundation-only package. If it ships localized templates, `Package.swift` needs `defaultLocalization: "en"` and `resources: [.process(...)]`, and lookups must use `bundle: .module`. Otherwise strings silently fall back to the key or to English. A process-wide override never reaches `Bundle.module`.
- **String Catalogs and XcodeGen:** `.xcstrings` must be in the target's resources, and stale or missing states only show inside Xcode. A CLI build with missing translations still succeeds and falls back to English.
- **XCUITests** that look up `app.staticTexts["Log today"]` break as soon as the test device is non-English, and pass misleadingly when the device stays English.

**Why it happens:**
The milestone asks for an "in-app picker" on both platforms. On Android that is a first-class API. On iOS it isn't, and the gap is easy to fill with a hack.

**How to avoid:**
- Decide in the iOS infrastructure phase. **Recommended:** the iOS in-app "Language" row shows the current language and opens `UIApplication.openSettingsURLString` (Apple's per-app language page). This is the platform-correct way to meet "chosen inside the app" and avoids every hack.
- If a true in-app switch is required, implement **one** explicit `LocalizationProvider` (chosen language → `Bundle(path: lproj)` for both `.main` and `.module`, plus a `Locale` for formatters) and route *every* lookup through it. Inject the same locale through `.environment(\.locale)` so formatting matches. Re-render the root view on change. Document this as a deliberate deviation.
- **Better still:** don't localize inside `EndoInsights` at all. Have it emit structured messages (Pitfall 3) and localize in the app target, so the package stays Foundation-only with zero resources.
- Drive UI tests with `-AppleLanguages (de) -AppleLocale de_DE` launch arguments, and find elements by `accessibilityIdentifier`, not by label.

**Warning signs:**
- German dates next to English words.
- The app missing from iOS Settings' language list.
- Any `Bundle` swizzle or `object_setClass` in the codebase.
- XCUITests passing only on English simulators.

**Phase to address:**
The **iOS infrastructure phase** (it starts from `main`, so decide this before extracting strings).

---

### Pitfall 8: Text expansion breaks chips, cards, tab bars and the PDF

**What goes wrong:**
German runs about 30% longer, French and Spanish about 15–25% longer, and individual words can be much longer than their English counterparts ("Schmerzintensität", "Verdauungsbeschwerden", "Zyklusphase", "Datenschutzerklärung"). Fixed-width chips, segmented controls, the bottom navigation bar, and body-map labels clip or wrap badly. Chart axis labels collide.

The **vector PDF** is the worst case. `ReportPdfRenderer` lays out text at fixed coordinates, so longer strings overflow columns, overlap the "self-tracked, not diagnostic" banner, or push content off the page. Clipping that banner is a compliance issue, not just cosmetic. With large accessibility font sizes, the expansion compounds.

**Why it happens:**
Layouts were tuned against English. Compose `Text` truncates silently with `maxLines = 1` / `Ellipsis`. PDF code doesn't paginate on measured height.

**How to avoid:**
- Enable **pseudo-localization** early: Android `pseudoLocalesEnabled = true` (en-XA expands about 30% and adds accents; ar-XB checks RTL hygiene, even though RTL isn't in scope). On iOS, use the scheme option "Double-Length Pseudolanguage". Screenshot every screen at the largest font scale in DE.
- Chips should use `FlowRow` and wrap, never truncate safety-relevant text. Bottom navigation labels need short glossary-approved terms per language.
- PDF: measure with `StaticLayout` / `CTFramesetter` and **paginate on measured height**. Give the disclaimer its own reserved, height-measured block. Add a test that renders the PDF in all four languages with a worst-case fixture (longest labels, max entries) and asserts no text box exceeds its frame, the disclaimer is present on every page, and the extracted text contains the per-locale disclaimer.
- Add a **translation length budget**: flag any translation more than 1.6× the English length for UI-constrained keys (chip, tab, button).

**Warning signs:**
- "…" in German screenshots.
- The PDF test only rendering en-US.
- Testers on a tablet see clipped bottom-nav labels.

**Phase to address:**
The **UI translation/layout phase** (pseudo-locale on from the infrastructure phase), and the **PDF localization phase**.

---

### Pitfall 9: Locale formatting fixed to US, or a language choice that wrongly changes the region

**What goes wrong:**
- `DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)` and `String.format(Locale.US, "%.1f h")` keep German users on "Oct 31, 2026" and "2.5 h". A doctor in Germany expects "31.10.2026" and "2,5 Std.". French uses "2,5 h", and Spanish "2,5 h" with "31/10/2026".
- **Opposite mistake:** applying the bare language tag "en" when a German resident picks English switches them to MDY dates, a Sunday week start and 12h time. In that case only the language should change, not the region conventions.
- Explicit `ofPattern` strings bake in field order. Calendar and cycle grids with Sunday hard-coded ignore `WeekFields.of(locale).firstDayOfWeek` (Monday in DE/FR/ES).
- Unit labels ("h", "min", "kcal") and decimal separators in chart axes, scrub tooltips and accessibility descriptions (`ChartSemanticsOverlay`, `AXChartDescriptor`) stay English.

**Why it happens:**
US-pinned formatting was a deliberate choice for determinism in tests and the PDF, and it isn't revisited when languages are added.

**How to avoid:**
- Split **serialization locale** (`Locale.ROOT`, ISO-8601, kept) from **display locale** (the effective app locale). Ban `Locale.US` in display code with a source-scan test.
- Use `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(displayLocale)` and `NumberFormat.getInstance(displayLocale)` on Android, and `Date.FormatStyle` / `.formatted(.number.locale(...))` on iOS.
- Build the display locale as **chosen language + system region** (e.g. `en-DE` via `Locale.Builder().setLanguage(chosen).setRegion(systemRegion)`, or BCP-47 `en-DE` in `setApplicationLocales`), so date/week/hour conventions follow where the user lives. Respect the system 24h setting separately.
- Golden tests per locale for the PDF date line, a trend tooltip, a reminder time and a week grid.

**Warning signs:**
- A grep showing `Locale.US` outside `export/` and tests.
- A German PDF with "Oct".
- A week grid starting on Sunday for a DE user.

**Phase to address:**
The **locale formatting phase**, covering app and PDF on both platforms.

---

### Pitfall 10: Tests that find UI by English text, and a Robolectric default locale that hides bugs

**What goes wrong:**
53 `onNodeWithText` lookups (and the iOS XCUITest label lookups) go one of two ways. Either they break as strings move to resources, or, worse, they keep passing because the tests never run in another locale. Robolectric defaults to `en-US`, so a German string with a mismatched format argument (`%1$s` vs `%1$d`, a missing placeholder) or a missing plural form only crashes in production on German devices.

Copy tests like `DisclaimerTest`, `PrivacyPolicyTest` and `StoreListingCopyTest` assert English phrases and would need four copies.

**Why it happens:**
Text-based lookups are convenient. Locale coverage in tests is opt-in.

**How to avoid:**
- Move behavioral UI tests to **`testTag`** / `accessibilityIdentifier` (most already use tags). Keep text assertions only where the text itself is under test, and resolve the expected value from resources (`composeRule.activity.getString(R.string.x)`), not from a literal.
- Add a **parameterized locale sweep**: Robolectric `@Config(qualifiers = "de")` (and fr, es, en-rXA) runs the screen smoke tests and an "every string resolves and formats with sample args" test. On iOS, use a test plan with one configuration per language.
- Turn on Android lint `MissingTranslation`, `StringFormatMatches`, `StringFormatInvalid`, `PluralsCandidate` and `ExtraTranslation` as **errors**. On iOS, add a CI check that fails on String Catalog entries in `new`, `needs_review` or `stale` state for shipping languages.
- Copy tests become parameterized over locales and assert the **per-locale** disclaimer and framing from the glossary.

**Warning signs:**
- The test count stays the same after adding three languages.
- A crash report from a de-DE device on `IllegalFormatConversionException`.
- `onNodeWithText("...")` with English literals in new tests.

**Phase to address:**
The **Android/iOS infrastructure phases** (lint, locale sweep scaffold), extended in every later phase.

---

### Pitfall 11: Consent, privacy policy and store-listing claims are inaccurate in some languages

**What goes wrong:**
- **Consent:** GDPR Art. 9(2)(a) explicit consent must be *informed*, and Art. 12 requires clear and plain language the data subject understands. A consent screen machine-translated into DE/FR/ES that differs in substance from the English one (data categories, export, withdrawal) makes the consent arguably invalid in that language. `ConsentStore` records only a flag and a timestamp, not which **text version and language** were shown.
- **Privacy policy:** In-app privacy copy and the hosted policy (still a release-checklist item) must exist in each shipped language **with identical substance**. A translated policy that over-promises (e.g. "verschlüsselt" when the in-app store is not encrypted, only the export is) is a misrepresentation.
- **Store listings:** EU MDR intended purpose is judged from **all** claims, including store text in every language. A German Play or App Store description saying "erkennt Schübe frühzeitig" (detects flares early) or "hilft bei der Behandlung" makes it look like a medical device in Germany. German **Heilmittelwerbegesetz (HWG)** and **UWG** also restrict health advertising claims. `StoreListingCopyTest` audits only English. Apple's and Google's health-app review also checks the listing text in each locale. Keyword fields (App Store "Keywords", per locale) are often forgotten and can contain "Diagnose" / "Therapie".

**Why it happens:**
Legal and store copy is treated as marketing, translated last and outside the audited catalog.

**How to avoid:**
- Register consent text, privacy copy, the Health Connect/HealthKit rationale and **all store-listing fields** (title, subtitle, short and full description, keywords, promotional text, release notes) per locale in the `AuditableCopy` catalog, so the per-locale ClaimsPolicy scan runs on them.
- Add a `consentTextVersion` (+ language) to the consent record. If the substance of the consent text changes, re-prompt. A pure translation of the same version doesn't require re-consent if the content is verifiably equivalent.
- Get a **native-speaker, legally literate review** of consent, the privacy policy and DE store copy (the home market, with HWG exposure) as a release-blocking human gate. Record the reviewer and date in the release checklist.
- Don't ship a language in the store until its in-app consent and privacy texts are reviewed. Partial localization is worse than none for these screens.

**Warning signs:**
- The store-listing fastlane/metadata folder has DE files that never went through `StoreListingCopyTest`.
- The consent record has no version.
- The DE privacy policy was "translated by Claude, reviewed: TBD" at submission.

**Phase to address:**
The **legal/store copy phase**, then the **release phase** (human review gate). The consent-version field belongs in the Android infrastructure phase, plus a mirror on iOS.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep `*Copy.kt` objects and add `when (lang)` branches instead of `strings.xml` | No resource extraction | No lint `MissingTranslation`, no plurals, no system locale resolution, no pseudo-locale; drift between languages invisible | Never for UI; acceptable only for audit test fixtures |
| Translate the engine's finished English sentence with a lookup map | Engines untouched, parity suite stays green | Breaks as soon as any label or number changes; grammar wrong; impossible in FR/ES | Never |
| Run the English ForbiddenTerms on all languages | Zero work | False sense of safety (Pitfall 1); the main claims risk of this milestone | Never |
| Bare language tags (`de`) instead of language + system region | Simple | Wrong week start, date order and hour cycle for expats | Only if region conventions are explicitly accepted to follow language (document it) |
| `configChanges="locale"` to dodge activity recreation | No lost state | Compose won't refresh resources; half-switched UI | Never; fix state saving instead |
| iOS `AppleLanguages` + "please restart" | Feels like an in-app picker | Confusing, unsupported, doesn't reach widgets/extensions | Only as a stopgap; prefer the Settings deep-link |
| Ship DE/FR/ES store listings before the native review | Earlier global reach | MDR intended-purpose / HWG exposure in Germany | Never for DE; never for any language with unreviewed consent text |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `:insights` ↔ `:app` (and `EndoInsights` ↔ app) | Engine emits prose; app displays it | Engine emits key + typed args; app renders per locale; HonestyGate scans the rendered result in every locale |
| Kotlin↔Swift parity suite (157 cases) | Compares English sentences, so it breaks on localization or forces English-only engines | Compare structured messages; add per-locale render goldens separately |
| Room `CachedInsight` cache | Locale-free fingerprint serves stale-language text | Store the structured message, or include the language tag in the fingerprint |
| Export/import (shared Android/iOS format) | Labels or locale-formatted numbers sneak in | Keys/raws/ISO only; cross-locale round-trip test; scan the export for catalog strings |
| WorkManager reminders | `applicationContext` uses system locale on API < 33; text resolved at schedule time | Locale-configured context at fire time; re-create the channel on language change |
| Health Connect / HealthKit permission UI | Expect it in the app language | It follows the device language; localize your own rationale and Info.plist strings |
| `ReportPdfRenderer` (vector PDF) | Fixed coordinates, `Locale.US` | Measured layout + pagination, display-locale formatting, per-locale disclaimer test |
| Play Console / App Store Connect metadata | Translated outside the audit | Store fields registered as per-locale `AuditableCopy`; keyword fields included |
| AppCompat on a pure-Compose app | Non-AppCompat theme crashes; no `autoStoreLocales` | `AppCompatActivity` + `Theme.AppCompat.*` parent + `AppLocalesMetadataHolderService` + `generateLocaleConfig` |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Full insights recompute on every recomposition after making the fingerprint locale-aware | Jank on the Insights tab | Recompute only on language-change events; keep rendering (cheap) separate from computing (expensive) | Users with 1–2 years of daily logs |
| Creating `DateTimeFormatter` / `NumberFormat` / `NumberFormatter` per cell or per chart point | Scroll jank in charts and PDF build slowness | Cache formatters per locale (remembered in Compose; static per locale on iOS) | Charts with 365+ points; long PDF ranges |
| Activity recreation reloading Room data and re-running engines | Multi-second blank screen after a switch | ViewModel survives configuration change; the cache is keyed correctly | Every switch |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Adding a translation SDK, ML Kit translate or a "download languages" feature | Breaks the no-`INTERNET` invariant and sends health copy off-device | All translations bundled; the existing no-INTERNET manifest test stays; dependency allow-list review |
| Persisting the export passphrase in `rememberSaveable` to survive a language switch | Passphrase in the saved-state bundle (can end up on disk) | Never save it; clear it and tell the user |
| Putting the chosen language or locale in a crash or diagnostics payload alongside health context | Behavioral signal about a stigmatized condition | The zero-analytics catalog stays; the language preference is local only |
| Translating "encrypted" or "anonymous" loosely in privacy copy | Misrepresentation under GDPR Art. 12/13 | Glossary entries for privacy terms; legal review |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Picker only lists languages by their English names ("German") | Users who don't read English can't find their language | List each language by its own name ("Deutsch", "Français", "Español", "English"), plus "System default" first |
| Inconsistent register (du/Sie, tu/vous) across screens or platforms | Feels machine-translated and untrustworthy for a sensitive health topic | Register fixed in the glossary and checked in review |
| Gendered address in FR/ES ("Êtes-vous fatiguée ?") | Excludes non-female users (D-06/08) | Agreement-free constructions |
| Mixed-language screens after a switch (cached insights, notifications, channel names) | Looks broken, erodes trust | Pitfalls 4 and 6 fixes, plus a "switch language and walk every tab" manual check |
| PDF only in the UI language | A German user seeing a doctor abroad, or the reverse, can't share a readable report | Optional "report language" selector in the report builder (default: app language) |
| Clinical terms that are too technical or too casual ("Dysmenorrhoe" vs "Regelschmerzen") | Patients don't recognize their symptoms | Glossary uses patient-language terms, checked against German/French/Spanish endometriosis patient-organization wording |

## "Looks Done But Isn't" Checklist

- [ ] **Per-locale ClaimsPolicy:** seeded bad DE/FR/ES sentences **fail** the gate, and seeded host words ("Pause", "entraînement") pass. Check the mutation test output, not just a green suite.
- [ ] **Engine sentences:** German output has capitalized nouns and correct case after "mit". French and Spanish agree in gender. Plurals are right for 0, 1 and 2 days.
- [ ] **Language switch:** Insights, the PDF, the next reminder notification and the notification-channel name all change language without new data being logged.
- [ ] **Persistence:** the cross-locale export round-trip is byte-identical, and there are no catalog strings in the export JSON or `CachedInsight.payload`.
- [ ] **Android API 26–32:** the choice survives a cold start, and the app appears in system App languages on 13+.
- [ ] **iOS:** the app appears in Settings' per-app language list, `Bundle.module` strings (if any) localize, and XCUITests pass with `-AppleLanguages (de)`.
- [ ] **Formatting:** a German user who picks English still gets DD.MM dates, Monday week start and 24h time.
- [ ] **PDF:** the disclaimer appears on every page in all four languages with the worst-case fixture, and no text overflows.
- [ ] **Lint/CI:** `MissingTranslation` / `StringFormatMatches` are errors, and the String Catalog has no stale or new entries for shipping languages.
- [ ] **Store and legal:** every store field per locale went through the audit, `consentTextVersion` is recorded, and the native-speaker and legal review is signed with a name and date.
- [ ] **Invariants:** the no-INTERNET manifest test and zero-analytics catalog are unchanged, with no new network-capable dependency.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Translated claim slipped into a released build or listing | MEDIUM | Hotfix the string plus an audit-list entry and a regression sentence; update the store listing at once (no binary needed for listings); log it in the release checklist |
| Stale-language cached insights in the field | LOW | Bump the fingerprint scheme (include locale) so the next launch invalidates the cache; no user data touched |
| Labels leaked into export files | HIGH | The importer must accept both key and legacy-label forms (a reverse map per locale) and the export version must be bumped; this is why it must be prevented up front |
| Concatenated engine strings shipped in DE | HIGH | The engine contract refactor after the fact, plus parity-suite rework; do it first instead |
| Language lost on restart (API < 33) | LOW | Add `AppLocalesMetadataHolderService` with `autoStoreLocales`, or persist in DataStore and re-apply in `Application.onCreate` |
| Consent text changed in substance in one language | MEDIUM | Bump `consentTextVersion`; re-prompt affected users at next launch |

## Pitfall-to-Phase Mapping

Phase names are suggestions for the roadmapper. Ordering matters: policy → contract → infrastructure → translation → formatting/PDF → legal/store → release.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. Substring audit fails in DE/FR/ES | Phase A: Per-locale ClaimsPolicy + shared glossary (first) | Mutation tests per language; drift test compares per-locale lists Kotlin↔Swift |
| 2. Translation introduces medical/causal/imperative claims | Phase A (glossary) + Phase D (translation) + Release (human review) | Per-locale audit green on every catalog; back-translation spot checks; signed reviewer checklist |
| 3. Concatenated engine sentences | Phase B: Engine structured-message contract (Android + iOS) | No string templates or `lowercase()` in engines; parity on structured output; per-locale render goldens |
| 4. Rendered text in cache/export | Phase B (cache shape) + Phase C (export invariant) | Cross-locale export round-trip test; switch-language cache test |
| 5. Android per-app-language plumbing | Phase C: Android l10n infrastructure (resources, AppCompat, localeConfig, picker) | API 30 + API 34 emulator checks; Robolectric state-survival test |
| 6. Workers, notifications, system UI | Phase C (Android) + Phase C' (iOS Info.plist) | Robolectric worker notification test in DE; channel name updated |
| 7. iOS in-app override and SPM bundles | Phase C': iOS l10n infrastructure | Settings per-app language visible; XCUITest plan per language |
| 8. Text expansion (UI + PDF) | Phase D: UI translation + layout; Phase E: PDF | Pseudo-locale screenshot pass; PDF overflow test in 4 languages |
| 9. Date/number/week/hour formatting | Phase E: Locale formatting (app + PDF, both platforms) | `Locale.US` source-scan test; golden formats per locale; language+region case |
| 10. Text-based tests, en-US-only test runs | Phase C/C' (scaffold), every later phase | Locale-sweep suites; lint errors enabled; String Catalog CI check |
| 11. Consent, privacy and store-claim accuracy | Phase F: Legal/store copy; Release gate | Store fields in `AuditableCopy` per locale; `consentTextVersion` stored; named legal review |

## Sources

- Codebase inspection (this worktree): `android/insights/.../ForbiddenTerms.kt`, `android/app/.../content/ClaimsPolicy.kt`, `CorrelationEngine.kt` (lines 375–428), `Factor.kt`, `EndoRepository.replaceCachedInsights`, `InsightFingerprint.kt`, `ReportPdfRenderer.kt` (lines 77–79, 427–436), `MainActivity.kt`, `CheckInReminderWorker.kt`, `ReminderNotifier.kt`, `ExportEnvelope.kt`, `app/build.gradle.kts` (minSdk 26), `res/values/`, `project.yml`, and a test lookup count
- Android Developers: per-app language preferences (`AppCompatDelegate.setApplicationLocales`, `LocaleManager` API 33+, `AppLocalesMetadataHolderService` / `autoStoreLocales`, `generateLocaleConfig`), pseudo-locales, lint localization checks, `pluralStringResource`. Training knowledge, HIGH confidence, not re-fetched this session
- Apple Developer: String Catalogs, per-app language in Settings (iOS 13+), SwiftPM `defaultLocalization` / `Bundle.module`, Xcode pseudolanguages, `-AppleLanguages` launch arguments. Training knowledge, HIGH confidence
- Unicode CLDR plural rules (French 0/1 singular); ICU MessageFormat `select` / `plural`
- EU MDR 2017/745 Art. 2(12) (intended purpose from labelling, instructions and promotional material); German Heilmittelwerbegesetz / UWG on health advertising; GDPR Art. 9(2)(a), 12 and 13. MEDIUM confidence, so confirm with counsel per market
- Project context: `.planning/PROJECT.md` (v2.1 goals, no-INTERNET invariant, Claude drafts + human native review)

---
*Pitfalls research for: multilingual (EN/DE/FR/ES) + in-app language selection in a privacy-first, claims-audited health tracker*
*Researched: 2026-10-31*
