# Architecture Research

**Domain:** Localization (EN/DE/FR/ES) and an in-app language picker added to an existing dual-platform (Android Kotlin/Compose + iOS SwiftUI) privacy-first health tracker that enforces its claims policy in tests
**Researched:** 2026-10-31
**Confidence:** HIGH for the code-grounded integration points (read from the live sources). MEDIUM for platform locale API behavior (AppCompat backport on API < 33, SwiftUI lookup), which needs checking against current docs and on a device before it is locked.

> This replaces the v1 ARCHITECTURE.md, which is still in git history. Scope is limited to what v2.1 adds. The v1/v2.0 architecture stays as it is.

---

## 0. What the code looks like today (the ground truth this plan builds on)

| Concern | Android (`android/`) | iOS (`Sources/EndoApp`, `EndoInsights/`) |
|---|---|---|
| UI copy storage | ~19 `object FooCopy : AuditableCopy` in `app/.../content/*Copy.kt`. All English `const val`. **There are no `strings.xml` string resources** (`res/values` holds only colors and themes). There are also a few literals outside the Copy objects, e.g. `Text("Cancel")` in `RemindersSection.kt:254`. | A mix: `*Copy` enums/structs with `static let` English Strings (`InsightsCopy.swift`, `FlareCopy`, `RecommendationsCopy`, `ReportCopy`, `ExploreCopy`, `HealthConnectCopy`, `ReminderCopy` inside `NotificationScheduler.swift`), about 70 inline `Text("literal")` calls, and Domain-enum `plainLabel`. **There is no String Catalog / `.strings` file.** |
| Audit | `CopyAuditRegistry.sources` (explicit list) feeds `CopyAuditTest` against `ClaimsPolicy`. The causal, imperative and alarmist lists are **reused** from `:insights` `ForbiddenTerms`. | `Tests/EndoAppTests/CopyAuditTests.swift` scans `X.auditableStrings` and enum `plainLabel`s against `ClaimsPolicy.swift`. |
| Cross-platform drift guards | `insights/.../ClaimsPolicyDriftTests.kt` regex-parses `static let forbiddenVerbs/forbiddenImperatives/forbiddenAlarmist: [String] = [...]` out of the **live Swift file**. `EnumRawValueParityTests` does the same for enums. | (none; iOS is the source of truth) |
| Insight sentences | Generated **in the pure engines** as English strings (`TrendsEngine.kt:103` `"$label logged on $daysPresent of …"`, `CorrelationEngine.kt:382`, `FlareCopyText`, `RecommendationCopy`). Numbers come from `String.format(Locale.ROOT,"%.1f")`, and `label.lowercase()` is used for mid-sentence labels. `HonestyGate` runs `ForbiddenTerms.violation(headline/detail)` and fails closed. | The same templates in `EndoInsights/Sources/*Engine.swift` (byte-equal through the 157-case parity port). |
| Insight cache | Room `CachedInsight.payload` = the serialized `Insight` JSON **including the rendered English headline/detail**. The fingerprint is `count|latestDay|dismissed`. | SwiftData `CachedInsight.payload: Data` = the Codable `Insight` (the same English text). |
| Notifications | `ReminderNotifier` builds the notification **at fire time** inside the WorkManager worker from `ReminderCopy.*`. The channel name and description are set when the channel is created. | `NotificationScheduler` builds the `UNMutableNotificationContent` **at schedule time**, so the text is frozen into each pending request. |
| PDF | `ReportPdfRenderer` uses hard-coded `Locale.US` date/number formatters and `ReportCopy` consts, and draws `insight.headline` verbatim. | `ReportCopy` + CG renderer (same pattern). |
| Prefs | DataStore (consent, reminders, HC token). `MainActivity : ComponentActivity` (**not** AppCompat). minSdk 26. | `UserDefaults` (`TerminologyStore`). `TerminologyPreference.BleedingTerm.noun` **returns `rawValue`**, so the English word is the raw value. |
| Insight inputs | Closed sets only: Factor labels come from enums (food/activity/phase/sleep/stress/mood/medication-taken). **No free text reaches insight copy** (medications enter as UUIDs). | Same. |

That last row matters. Every word an insight can render comes from a finite set of templates and a finite set of labels, so a **static, exhaustive audit of every template × every label in every language is possible and complete**.

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  i18n/  (repo root, NEW, the single cross-platform source)                     │
│  ┌──────────────────┐ ┌───────────────────────┐ ┌──────────────────────────┐ │
│  │ glossary.json    │ │ insight-messages.json │ │ claims-policy.l10n.json  │ │
│  │ term → en/de/fr/es│ │ msgKey → en/de/fr/es  │ │ de/fr/es forbidden lists │ │
│  │ (+ do-not-use)   │ │ + label keys          │ │ (+ gendered, allowed)    │ │
│  └────────┬─────────┘ └──────────┬────────────┘ └────────────┬─────────────┘ │
│           │ test-time only       │ bundled + test           │ bundled + test │
└───────────┼──────────────────────┼──────────────────────────┼───────────────┘
            │                      │                          │
┌───────────┴──────────┐  ┌────────┴─────────────────┐  ┌──────┴────────────────┐
│ PURE ENGINE LAYER    │  │ APP LAYER (per platform) │  │ AUDIT LAYER (tests)   │
│ :insights / EndoIns. │  │                          │  │                       │
│ engines → Insight{   │→ │ InsightMessageRenderer   │  │ CopyAudit per locale  │
│  headline(EN legacy) │  │  (table + AppLanguage)   │  │ Key-completeness      │
│  detail  (EN legacy) │  │ AppLanguage store/applier│  │ Placeholder parity    │
│  message: key+args } │  │ L10n façade → native     │  │ Glossary conformance  │
│ InsightTemplate      │  │  catalog (strings.xml /  │  │ Cross-platform drift  │
│  (pure renderer fn)  │  │  Localizable.xcstrings)  │  │ Insight goldens ×4    │
│ HonestyGate (EN, as  │  │ → Compose/SwiftUI, PDF,  │  │                       │
│  today)              │  │   notifications          │  │                       │
└──────────────────────┘  └──────────────────────────┘  └───────────────────────┘
            Persisted data: raw values, signalKeys, UUIDs, ISO dates. No language anywhere.
```

### Component Responsibilities

| Component | Responsibility | Implementation | New / Modified |
|---|---|---|---|
| `i18n/glossary.json` | One canonical term list (e.g. "possible association", "flare early-warning", "check-in", "endo belly", bleeding/period/menstruation) with the approved DE/FR/ES rendering, plus per-term "never translate as" variants | Hand-edited JSON. Read **only by tests** on both platforms | NEW |
| `i18n/insight-messages.json` | Every insight template key and every insight label key (symptom/factor/window/direction/phase) in the 4 languages | JSON map `key → {en,de,fr,es}` with named `{placeholders}`. **Bundled at runtime** into both apps | NEW |
| `i18n/claims-policy.l10n.json` | DE/FR/ES forbidden causal/imperative/alarmist lists, gendered tokens, precision tokens, allowed framing | JSON. Bundled (runtime fail-closed guard) and read by tests | NEW |
| `InsightMessage` (+ `InsightArg`) | A structured, language-neutral description of an insight sentence | Kotlin `@Serializable` data class / Swift `Codable` struct in the pure packages | NEW (in `:insights` / `EndoInsights`) |
| `Insight.message` | Carries the structured message beside the legacy EN `headline`/`detail` | A new optional field, with no change to `id` derivation | MODIFIED `Insight.kt` / `Insight.swift` + every engine |
| `InsightTemplate.render(template, args, labels, numberStyle)` | Pure placeholder substitution plus deterministic number formatting | Pure Kotlin / Foundation-only Swift, **no platform locale APIs** | NEW (pure package) |
| `InsightMessageRenderer` | Loads `insight-messages.json` for the active language, resolves label args, calls `InsightTemplate.render`, runs the per-language runtime claims guard | App layer, one per platform | NEW |
| `AppLanguage` | The chosen language: `system` \| `en` \| `de` \| `fr` \| `es` → resolved `Locale` | Android: DataStore mirror + `AppCompatDelegate` applier. iOS: `@Observable` store over `UserDefaults` | NEW |
| `L10n` façade | Every UI string lookup goes through it with an explicit or current language | Android: `@StringRes` accessors + `Context`. iOS: `L10n.tr(key)` over the per-language `.lproj` bundle | NEW |
| Native catalogs | UI chrome strings | Android `res/values{,-de,-fr,-es}/strings.xml`. iOS `Sources/EndoApp/Resources/Localizable.xcstrings` | NEW |
| `*Copy` objects | Change from string holders to typed key accessors (call sites keep their shape) | Android `const val X = "…"` → `val X = R.string.x`. iOS `static let x = "…"` → `static var x: String { L10n.tr("x") }` | MODIFIED (all ~19 / ~8) |
| `CopyAuditTest` / `CopyAuditTests` | Per-language scans + completeness, with the catalog as the registry | Pure-JVM XML parse (like `ManifestPermissionsTest`) / JSON parse of `.xcstrings` | MODIFIED (major) |
| `ClaimsPolicy` / `ForbiddenTerms` | EN lists unchanged. Gains `forLanguage(lang)` backed by the l10n JSON | Kotlin + Swift | MODIFIED (additive) |
| Language picker UI | Settings row + sheet | `ui/settings/LanguageSection.kt`, `Features/Settings/LanguagePickerView.swift` | NEW |

---

## Recommended Project Structure

```
i18n/                                   # NEW — cross-platform single source (no build step)
├── glossary.json                       # canonical terms × 4 langs (+ banned variants)
├── insight-messages.json               # insight templates + label keys × 4 langs (runtime-bundled)
├── claims-policy.l10n.json             # de/fr/es forbidden + gendered + precision + allowed lists
├── golden/                             # NEW — rendered fixture sentences, both platforms must match
│   └── insights.{en,de,fr,es}.txt
└── README.md                           # translator rules (no inflection by code, no lowercase())

android/
├── insights/src/main/kotlin/.../InsightMessage.kt     # NEW  key + typed args (pure)
├── insights/src/main/kotlin/.../InsightTemplate.kt    # NEW  pure renderer + NumberStyle
├── insights/src/main/kotlin/.../*Engine.kt            # MOD  also emit message
├── app/build.gradle.kts                # MOD  assets.srcDir("../../i18n"), appcompat dep,
│                                       #      androidResources { generateLocaleConfig = true }
├── app/src/main/res/values/strings.xml          # NEW  EN source (~700)
├── app/src/main/res/values-{de,fr,es}/strings.xml
├── app/src/main/res/resources.properties        # NEW  unqualifiedResLocale=en (for generateLocaleConfig)
├── app/src/main/kotlin/.../i18n/
│   ├── AppLanguage.kt                  # NEW  enum + resolve()
│   ├── LanguageStore.kt                # NEW  DataStore mirror; applier → AppCompatDelegate
│   ├── LocalizedContext.kt             # NEW  ctx.forLanguage(lang) for worker/PDF
│   ├── InsightMessageRenderer.kt       # NEW
│   └── LocaleFormats.kt                # NEW  date/number formatters for the resolved Locale
├── app/src/main/kotlin/.../content/*Copy.kt     # MOD  consts → @StringRes
└── app/src/main/kotlin/.../ui/settings/LanguageSection.kt   # NEW

Sources/EndoApp/
├── Resources/Localizable.xcstrings     # NEW  EN source + de/fr/es
├── I18n/AppLanguage.swift              # NEW  store + resolved bundle/locale
├── I18n/L10n.swift                     # NEW  tr(key, lang) via lproj bundle
├── I18n/InsightMessageRenderer.swift   # NEW
├── Content/ClaimsPolicy.swift          # MOD  additive; EN static-let arrays untouched (drift guard parses them)
├── Features/*/…Copy.swift              # MOD  static let → computed via L10n
└── Features/Settings/LanguagePickerView.swift  # NEW
EndoInsights/Sources/EndoInsights/InsightMessage.swift, InsightTemplate.swift   # NEW
project.yml                             # MOD  resources: i18n/*.json; knownRegions/CFBundleLocalizations
```

### Structure Rationale

- **`i18n/` at repo root, used directly with no codegen.** Both apps bundle the same `insight-messages.json` file (Android `assets.srcDir`, XcodeGen `resources`). Insight sentences are the one copy surface that *must* be byte-identical across platforms (the 157-case parity already guarantees this for EN), so they get one physical source. Generating it into two native catalogs would only add a sync step that can drift.
- **UI chrome goes in native catalogs, not the shared JSON.** The ~700 strings per platform have different key sets and layouts. Native catalogs give Android Lint `MissingTranslation`, Xcode catalog state tracking, plural handling, and, on Android 13+, the system per-app-language page. Cross-platform consistency for chrome is enforced through the **glossary conformance test**, not through shared keys.
- **The glossary is test-only data.** It never ships. It is the contract both platforms' catalogs are checked against.

---

## Architectural Patterns

### Pattern 1: Structured insight messages next to the legacy EN strings (the engines stay pure)

**What:** Each engine keeps producing exactly the English `headline`/`detail` it produces today, so `HonestyGate`, the 157-case parity suite and the EN goldens stay untouched. It **also** attaches an `InsightMessage(headlineKey, detailKey, args)`. The app layer renders the message in the active language.

**Why not pass a string table into the engine:** `HonestyGate` scans the engine output. If the engine rendered German, the gate would need German lists inside the pure package, the 157 parity fixtures would multiply by 4, and engine determinism would depend on app-provided data. A structured message keeps the engines language-free and the gate as it is today.

**Why not drop `headline`/`detail` immediately:** they are the parity anchor. A test asserts `render(message, EN) == headline` for every fixture, which proves the structured path is complete. Removing the legacy fields can come in a later milestone.

**Trade-offs:** each insight carries some redundant data, and every engine template has to be changed once. In exchange, the EN behavior and its proof stay exactly as they are.

```kotlin
// :insights (pure)
@Serializable sealed interface InsightArg {
    @Serializable data class Count(val n: Int) : InsightArg
    @Serializable data class Decimal1(val v: Double) : InsightArg          // one-decimal mean
    @Serializable data class Label(val key: String) : InsightArg            // "label.symptom.pelvicPain"
}
@Serializable data class InsightMessage(
    val headlineKey: String, val detailKey: String,
    val args: Map<String, InsightArg>,                                       // named, not positional
)
// TrendsEngine: headline = legacy EN (unchanged), message = InsightMessage(
//   "trend.symptom.frequency.headline", "trend.symptom.frequency.detail",
//   mapOf("label" to Label("label.symptom.${type.raw}"), "present" to Count(daysPresent),
//         "total" to Count(daysWithAnyLog), "window" to Label("window.${window.raw}.inPhrase")))
```

Rules this pattern imposes on templates, enforced by a test over `insight-messages.json`:

- **No case transforms in code.** Delete `label.lowercase()` and `capitalizedLikeSwift()`, because German nouns are always capitalized. If a label appears both sentence-initially and mid-sentence, it gets two keys (`label.x` and `label.x.inline`).
- **No inflection by code.** Templates are written so labels sit in grammatically invariant slots (after a colon, as a subject, or in a fixed "an {present} von {total} Tagen" frame). Direction, window and phase are whole-phrase labels, not fragments to glue together.
- **Named placeholders only**, so word order can change per language. The placeholder set must be identical across the 4 languages (completeness test).
- **Deterministic numbers:** `InsightTemplate` formats `Decimal1` by hand with a per-language decimal separator taken from the JSON (`"."` for en, `","` for de/fr/es) and no grouping. Do **not** use `NumberFormat`/`NumberFormatter` here. JDK CLDR and Apple ICU differ (for example French grouping U+202F vs U+00A0), which would break the cross-platform insight goldens. UI chrome and the PDF *can* use the platform formatters.

### Pattern 2: The catalog becomes the audit registry (enforced by construction)

**What:** Today, being audited depends on someone remembering to call `CopyAuditRegistry.sources += FooCopy` ("the load-bearing rule"). Once every user-facing string lives in `strings.xml` / `Localizable.xcstrings`, **the catalog file is the registry**. The audit parses the files directly, so a string cannot reach the UI without being scanned. `AuditableCopy`/`CopyAuditRegistry` stay only for strings that are not in a catalog (store listing, privacy policy text, `DataSafetyDeclaration`) or retire if those move to per-locale files.

**The gap to close at the same time:** literals that bypass the catalog (`Text("Cancel")`, `Text("←")`). Add a source-scan test (the same file-walk style as `BoundaryLintTests`) that fails on `Text("…letters…")` in `ui/**`. On iOS, fail on `Text("…")` without a matching catalog key, and on `Text(verbatim:)`/`Text(String)` fed from a non-`L10n` source.

**Audit tests per locale `L ∈ {en,de,fr,es}`:**

1. **Completeness:** key set(`values-L`) == key set(`values`). On iOS, every key has `localizations[L].stringUnit.state == "translated"`. The same check runs on `insight-messages.json`.
2. **Placeholder parity:** the `%1$s`/`%d` sequence (Android), `%@`/`%lld` (iOS) and `{name}` set (insight JSON) match the EN source exactly.
3. **Claims scan:** every value is scanned against `ClaimsPolicy.forLanguage(L)` (causal, imperative, alarmist, gendered, precision). Matching is case-insensitive with **locale-aware lowercasing** (`lowercase(Locale(L))`) and **NFC normalization** (é, ß, ñ).
4. **Insight exhaustive scan:** every template is rendered with **every** label key in each label slot and representative numbers, then scanned. This is complete because the inputs are closed sets (§0).
5. **Glossary conformance:** for each glossary term, if the EN source string contains the EN term, then the L string must contain the approved L term and none of its banned variants. This runs on both platforms against the same `glossary.json`.
6. **Red-capable self-test per language:** a deliberately forbidden DE/FR/ES string fed through the same helper must fail, so the suite cannot stay green on an empty list.

**Per-language forbidden lists** live in `i18n/claims-policy.l10n.json` and are exposed as `ForbiddenTerms.forLanguage(lang)` / `ClaimsPolicy.forLanguage(_:)`. The EN lists **stay in code, unchanged**. `ClaimsPolicyDriftTests` regex-parses `static let forbiddenVerbs: [String] = [` out of the Swift file, so do not restructure those declarations. Add one drift test per platform asserting that the JSON's `en` block (if present) equals the code lists, or leave EN out of the JSON altogether.

The substring-trap problem gets worse in other languages. EN already rewords around "her" ⊂ "higher". For example, DE "nimm" (imperative "take") ⊂ "zunimmt" (increases), and FR "doit" ⊂ "endroit". Each language list therefore needs a **per-language allow-list of known-innocent host words** (a whitelist checked before flagging), or word-boundary matching for that language. Write that into the JSON schema now. Getting it wrong would push Claude-drafted translations into unnatural rewording.

### Pattern 3: The language is a value passed in, not ambient global state

**What:** Every non-Compose consumer (worker, PDF renderer, insight renderer, tests) receives an explicit `AppLanguage`/`Locale`. Only the UI reads it ambiently. That keeps notifications and the PDF correct regardless of process or activity state, and lets tests pick the language without touching device config.

---

## Data Flow

### Language choice: storage, startup, live change

```
                    ┌─────────── Android ───────────┐   ┌──────────── iOS ────────────┐
User picks "de" →   LanguageStore.set(DE)             AppLanguageStore.language = .de
                     ├ DataStore key "app.language"     ├ UserDefaults "app.language"
                     ├ AppCompatDelegate               ├ @Observable change → root
                     │  .setApplicationLocales("de")   │   view re-renders (L10n reads
                     │  → activity recreated (API all) │   current bundle) + .environment
                     ├ re-create notification channel  │   (\.locale, resolved)
                     │  (same id, new name/desc)       ├ NotificationScheduler.reschedule
                     └ (insights: nothing; rendered    │   All() (content frozen at schedule)
                        at display from message)       └ (insights: nothing; render-time)
Startup:            App.onCreate: read store → apply  EndoAppApp.init: store loads → L10n
                    (API 33+: LocaleManager is truth;  current bundle; "system" =
                     reconcile store ← system value)   Bundle.main.preferredLocalizations
```

- **Android (MEDIUM):** use AppCompat's `AppCompatDelegate.setApplicationLocales` + `androidx.appcompat.app.AppLocalesMetadataHolderService` with `autoStoreLocales=true` + `generateLocaleConfig` (AGP 8.1+). This puts the app on Android 13+'s system per-app language page and backports to minSdk 26. **Required change:** `MainActivity : ComponentActivity` → `AppCompatActivity`, because the backport only applies to AppCompat activities, plus the `appcompat` dependency. **Also keep a DataStore mirror.** On API < 33 the application `Context` used by a cold-started WorkManager worker is *not* re-localized by AppCompat, and `getApplicationLocales()` may be empty before an activity has run. So the worker and the PDF read `LanguageStore` and build `context.createConfigurationContext(config.apply { setLocales(LocaleList(locale)) })`. On API 33+, if the user changes the language in system Settings, `LocaleManager.getApplicationLocales()` wins and the mirror is updated at startup.
- **iOS (MEDIUM):** Apple offers no runtime API to switch an app's language (the per-app language lives in the Settings app and needs a relaunch). Writing `AppleLanguages` to UserDefaults is a hack that requires a restart, so avoid it. Instead, the app manages the language itself: `L10n.tr(key)` resolves through `Bundle(path: main.path(forResource: lang, ofType: "lproj"))` (the xcstrings compile into per-language `.lproj` tables), and `.environment(\.locale, resolved)` handles `Text(date, format:)` and number formatting. Inline `Text("literal")` must become `Text(L10n.tr("key"))` (verbatim String), so lookup never depends on implicit `LocalizedStringKey` resolution. That makes the in-app choice authoritative and live without a relaunch. Offer "Open iOS Settings" only as a secondary link.
- **"Follow system" is a stored value (`system`), not an empty value**, so the picker can tell "explicitly English" apart from "default".

### Insight rendering (display time)

```
engines → Insight{headline(EN), detail(EN), message} → HonestyGate (EN scan, unchanged)
   → CachedInsight.payload (JSON incl. message; language-NEUTRAL)
   → InsightsCoordinator → UI / PDF
        → InsightMessageRenderer(lang).render(message)
             → labels from insight-messages.json[lang]
             → InsightTemplate.render (pure, deterministic numbers)
             → ClaimsPolicy.forLanguage(lang).violation(text)? → DROP (fail-closed, mirror of HonestyGate; log in debug)
             → fallback: message == null (legacy cache) → treat as cache MISS → recompute
```

- **Cache:** because the payload stores the message and not the rendered text, a language switch needs **no recompute** and the fingerprint stays language-free. Payloads written before v2.1 lack `message`. Bump the fingerprint with a version prefix (`"v2|count|latest|dismissed"`) so the first launch after the update recomputes once. On iOS the payload is a `Codable` blob, so adding an optional field needs **no SwiftData schema migration**. Synced CloudKit records rendered on devices with different languages are harmless.
- **Dismissals** stay keyed on `signalKey`, and `Insight.id` stays derived from `kind.raw|window.raw|signalKey`. Both are language-neutral already, so neither changes.

### Notifications

- **Android:** `CheckInReminderWorker` → `ReminderNotifier.post(context.forLanguage(LanguageStore.current()))`. Strings resolve at fire time, so a language change needs no rescheduling, only a channel re-create (same channel id, so the user's channel settings survive).
- **iOS:** on language change, call `NotificationScheduler.rescheduleAll()`, because pending `UNNotificationRequest` content is frozen. Add a test that a language change triggers a reschedule.
- The notification copy must stay generic in every language (no symptom or flare words, because the lock screen is semi-public). It is covered by the per-language claims scan like any other key.

### PDF report

- `ReportPdfRenderer(…, language: AppLanguage)` takes the language explicitly. Replace `Locale.US` formatters with `LocaleFormats.date(locale)` (`DateTimeFormatter.ofLocalizedDate(MEDIUM).withLocale`) and locale number/unit formatting. Keep the filename stamp `yyyyMMdd` on `Locale.ROOT`. Resolve copy through `context.forLanguage(lang).getString(...)`. Insights go through the same renderer, so the PDF and the screen can never disagree.
- Because the language is a parameter, a **per-report language override** (a German patient with an English-speaking clinician) costs nothing architecturally. FEATURES decides whether to expose it.
- Text measurement/wrapping: DE/FR strings run ~20–35% longer than EN, and the CG/Canvas layout must paginate rather than truncate. Add per-language PDF render tests (page count > 0, no clipped block).

### Tests (Compose / SwiftUI)

- Android Robolectric Compose tests: the default stays EN. Add `@Config(qualifiers = "de")` (or run with an `AppCompatDelegate` locale) smoke tests per language for each screen. Assertions switch from English literals to `context.getString(R.string.x)` or `testTag`s. Most tests already use tags such as `log.cycleSummary.nextWindow`, and tags stay language-neutral.
- iOS: unit tests call `L10n.tr(key, .de)`. UI tests launch with `-AppLanguage de` (a launch argument read by `AppLanguageStore`), not with `-AppleLanguages`.

---

## Persisted data that MUST stay language-neutral

| Data | Where | Rule |
|---|---|---|
| Enum raw values (`SymptomType.raw`, `FlowIntensity`, `BodyRegion`, `FoodTag`, `ActivityType`, `FactorKind`, `CyclePhase`, `InsightKind`, `TrendWindow`) | Room/SwiftData, export, `signalKey`, `DeterministicUUID` | Never derive display from `raw`. Display = `L10n("label.symptom.<raw>")`. `EnumRawValueParityTests` unchanged |
| `TerminologyPreference.BleedingTerm` (iOS) | UserDefaults | **Bug-in-waiting:** `noun` returns `rawValue`. Keep the raw value as the stored choice and map `noun` → `L10n("term.bleeding.<raw>")`. Note that DE/FR/ES need gendered articles/cases, so sentences using the term need per-term template variants, not substitution. Android has no Terminology port, so nothing changes there |
| `signalKey`, `Insight.id`, `DismissedInsight` | caches, export | Already neutral. Unchanged |
| `CachedInsight.payload` | Room/SwiftData | Store `message` (neutral). Rendered text is a legacy field only, never shown directly after v2.1 |
| Export envelope (`ExportEnvelope`, `schemaVersion = 1`) | encrypted file | Unchanged. Raw values + ISO-8601 dates only. **Do not add the language** (it is a device preference, and adding it means a format bump). A DE export must import byte-equivalently on an EN device (add a round-trip test across languages) |
| Medication names, notes | user text | Never translated, never audited (user content) |
| Language choice itself | DataStore `app.language` / UserDefaults `app.language` | Device preference. On Android it backs up with DataStore prefs (fine). Not synced via CloudKit (per-device choice) |

---

## Anti-Patterns

### Anti-Pattern 1: Translating inside the pure engines
**What people do:** pass a `Map<String,String>` into `TrendsEngine` so it emits German directly.
**Why it's wrong:** it multiplies the 157-case parity matrix, forces `HonestyGate` to ship per-language lists inside the pure module, and makes the cached payload language-dependent, so every language switch needs a recompute.
**Do this instead:** keep the structured `InsightMessage` and render in the app layer (Pattern 1).

### Anti-Pattern 2: Platform number/date formatters inside insight sentences
**What people do:** `NumberFormat.getInstance(Locale.FRENCH)` / `NumberFormatter` in the insight renderer.
**Why it's wrong:** JDK CLDR and Apple ICU differ on grouping and space characters, so the cross-platform insight goldens break.
**Do this instead:** use a deterministic `Decimal1` formatter with the decimal separator taken from the JSON. Use platform formatters only for UI chrome dates and the PDF.

### Anti-Pattern 3: `static let` localized strings on iOS / `const val` on Android
**What people do:** `static let title = String(localized: "…")`.
**Why it's wrong:** it is evaluated once, so the language is frozen for the process. `const val` cannot hold a localized value at all.
**Do this instead:** computed `static var` via `L10n`, and `@StringRes` ids on Android.

### Anti-Pattern 4: Gluing sentence fragments and case-transforming labels
**What people do:** carry `"$label severity averaged…"` + `label.lowercase()` + `directionPhrase` concatenation over into other languages.
**Why it's wrong:** German capitalization, gender agreement and French elision ("de" → "d'") make fragment-gluing ungrammatical or, worse, change the meaning.
**Do this instead:** whole-sentence templates with named placeholders and inline label variants.

### Anti-Pattern 5: Relying on the system/`AppleLanguages` for the in-app choice on iOS
**Why it's wrong:** it needs a relaunch, it is undocumented, and the system can override it.
**Do this instead:** app-managed `AppLanguageStore` + `L10n` bundle resolution (MEDIUM; verify `.environment(\.locale)` covers system controls such as `DatePicker` on device).

### Anti-Pattern 6: Weakening the substring gate to make translations pass
**What people do:** delete a DE forbidden term because it collides with an innocent word.
**Do this instead:** keep the term and add the innocent host word to that language's allow-list in `claims-policy.l10n.json`. Allow-list entries need native-reviewer sign-off.

---

## Integration Points

### External services
None. The translations ship inside the app bundle, there is no network, and the no-`INTERNET` manifest test must stay green. AppCompat adds no permission (re-run `ManifestPermissionsTest`, since `AppLocalesMetadataHolderService` is a non-exported service).

### Internal boundaries

| Boundary | Communication | Notes |
|---|---|---|
| `:insights` ↔ `:app` | `Insight.message` (data) | `:insights` stays android-free (`BoundaryLintTests`). `InsightTemplate` has no `java.util.Locale` dependency (pass the separator in) |
| `EndoInsights` ↔ `EndoApp` | Same, via `Codable` | Package remains Foundation-only with zero deps. **Do not** add SPM resources to the package. The JSON is bundled in the app |
| `i18n/*.json` ↔ Android | `sourceSets["main"].assets.srcDir("$rootDir/../i18n")` (runtime) + `resolveFromRoot` (tests) | Same pattern as the existing Swift-source drift guards. Skip with `assumeTrue` outside the monorepo |
| `i18n/*.json` ↔ iOS | XcodeGen `resources` + test-bundle path | `project.yml` must also declare `knownRegions`/`CFBundleLocalizations: [en, de, fr, es]` so the App Store and the Settings app list the languages |
| `LanguageStore` ↔ `reminders/` | `ReminderNotifier` takes a localized `Context` | Keeps the "all WorkManager APIs in `reminders/`" separation invariant: the language store is read *by* the worker, and the worker does not move |
| `LanguageStore` ↔ `report/` | Explicit `language` param | Same object for screen and PDF |
| Android ↔ iOS catalogs | Tests only: glossary conformance + `insight-messages.json` shared | A Kotlin test can additionally parse `Localizable.xcstrings` (it is JSON) and assert that shared glossary terms match, extending the existing cross-platform drift style |
| Store listing | `StoreListingCopy.kt` → per-locale `fastlane/metadata/android/{en-US,de-DE,fr-FR,es-ES}/` and an iOS equivalent | Store copy is currently audited in `CopyAuditTest`, so extend the audit to the per-locale files |

---

## Suggested Build Order (dependency-driven)

1. **Shared foundation (no UI change):** create `i18n/` with the JSON schemas, `glossary.json` seeded from the EN terms, `claims-policy.l10n.json` (DE/FR/ES lists + allow-lists) and `ClaimsPolicy.forLanguage`/`ForbiddenTerms.forLanguage` on both platforms, plus red-capable self-tests. *Everything later is audited against this, so it comes first.*
2. **Engine structured messages (both packages, parity-locked):** `InsightMessage`/`InsightArg`/`InsightTemplate` in `:insights` and `EndoInsights`. Every engine emits `message`, and EN `insight-messages.json` is extracted from the current templates. Gate: `render(message, en) == headline` for all 157 fixtures on both platforms, plus EN goldens in `i18n/golden/`. *This depends only on step 1 and keeps `HonestyGate` and parity untouched.*
3. **Android language plumbing:** `AppLanguage`/`LanguageStore`, AppCompat migration (`MainActivity`), `generateLocaleConfig`, `forLanguage(context)`, the settings picker, and `InsightMessageRenderer` + fingerprint version bump (switching `InsightCard` and the PDF to render from the message). The EN-only app should look identical after this step.
4. **Android catalog migration:** move every `*Copy` const into `values/strings.xml` and the Copy objects to `@StringRes`. Remove stray literals and add the literal-scan test. Rewrite `CopyAuditTest` so the catalog is the registry. Notifications and the PDF get a localized `Context` and locale formatters. *This is the largest mechanical change. Do it before translating so the translations land in their final structure.*
5. **iOS language plumbing + catalog migration:** `AppLanguageStore`, `L10n`, `Localizable.xcstrings`, `*Copy` → computed, inline `Text("…")` → `L10n`, `BleedingTerm.noun` → localized, `NotificationScheduler.rescheduleAll` on change, PDF locale, `project.yml` regions, and the `CopyAuditTests` rewrite. *This can run in parallel with step 4 once step 2 lands (different branch: iOS starts from `main`).*
6. **Translations DE/FR/ES:** fill `values-*/strings.xml`, xcstrings and `insight-messages.json`. The full per-language audit, completeness, placeholder-parity, glossary-conformance and exhaustive insight scans go live as hard gates. Write the DE/FR/ES insight goldens and assert that both platforms render them byte-equal.
7. **Per-language verification + store copy:** Robolectric/XCUITest smoke tests per language, PDF per-language render tests (pagination with longer strings), the export round-trip across languages, per-locale store listing files + audit. Native-speaker review is a **human-gated release checklist item** (it is not automated, and the tests must not claim it happened).

Critical path: **1 → 2 → (3→4 ∥ 5) → 6 → 7.** Translation (6) waits for the catalog migrations (4 and 5), so strings are translated once, in their final keys.

---

## Files that change vs. new

**Modified (Android):** `insights/.../Insight.kt`, `TrendsEngine.kt`, `CorrelationEngine.kt`, `CyclePhaseMapper.kt`, `RecommendationsEngine.kt`, `FlareEngine.kt`, `Factor.kt` (labels → keys), `ForbiddenTerms.kt` (additive); `app/build.gradle.kts`, `AndroidManifest.xml` (AppLocales service), `MainActivity.kt` (AppCompatActivity), `App.kt`/`AppDependencies.kt` (LanguageStore wiring), all `content/*Copy.kt`, `content/AuditableCopy.kt` (shrinks or retires), `content/ClaimsPolicy.kt`, `insights/InsightsCoordinator.kt` + `InsightFingerprint.kt` (version prefix), `ui/insights/InsightCard.kt`, `report/ReportPdfRenderer.kt` + `report/ui/ReportBuilderScreen.kt` (formatters), `ui/charts/*` + `ui/tracking/LogHomeScreen.kt` + `ui/explore/ExploreScreen.kt` (hard-coded `ofPattern("MMM d")` → localized skeletons), `reminders/ReminderNotifier.kt` + `CheckInReminderWorker.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/RemindersSection.kt` (literal), `test/.../CopyAuditTest.kt`, `insights/test/.../ClaimsPolicyDriftTests.kt` (extend), every Compose test that matches on English text.

**Modified (iOS):** `EndoInsights/.../Insight.swift` + all `*Engine.swift` + `ForbiddenTerms.swift`; `Content/ClaimsPolicy.swift` (additive only), `Terminology/*`, all `*Copy.swift`, views with inline `Text("…")`, `Reminders/NotificationScheduler.swift`, `Features/Report/*`, `Persistence/Models/CachedInsight.swift` (payload decode tolerance), the insights coordinator, `App/EndoAppApp.swift`, `project.yml`, `Resources/Info.plist`, `Tests/EndoAppTests/CopyAuditTests.swift` (+ `HealthConnectCopyAuditTests`, `FlareNoAlarmVisualTests`).

**New:** `i18n/{glossary,insight-messages,claims-policy.l10n}.json`, `i18n/golden/*`; `InsightMessage`/`InsightTemplate` (×2 platforms); Android `i18n/` package (AppLanguage, LanguageStore, LocalizedContext, InsightMessageRenderer, LocaleFormats), `res/values{,-de,-fr,-es}/strings.xml`, `res/resources.properties`, `ui/settings/LanguageSection.kt`; iOS `I18n/` (AppLanguage, L10n, InsightMessageRenderer), `Localizable.xcstrings`, `LanguagePickerView.swift`; tests: per-language audit, completeness, placeholder parity, glossary conformance, insight exhaustive scan, cross-platform golden, literal-scan, export cross-language round-trip, language-change → notification reschedule/channel re-create.

---

## Scaling Considerations

| Scale | Adjustment |
|---|---|
| 4 languages (v2.1) | The JSON + native catalogs described above. Exhaustive insight scan cost ≈ templates × labels × 4, which is trivial |
| 5–10 languages | Same architecture. Add plural/ICU support to `InsightTemplate` before adding any language whose count-frame trick fails (e.g. Polish/Russian plural classes) |
| RTL (ar/he) | Not in scope. It would need Compose/SwiftUI layout mirroring audits and PDF bidi. The architecture does not block it |

---

## Sources

- Live code: `android/app/src/main/kotlin/com/endoapp/app/content/{AuditableCopy,ClaimsPolicy,InsightsCopy,ReminderCopy}.kt`, `android/insights/src/main/kotlin/com/endoapp/insights/{Insight,ForbiddenTerms,TrendsEngine,CorrelationEngine,FlareEngine,Factor,HonestyGate}.kt`, `insights/src/test/.../ClaimsPolicyDriftTests.kt`, `data/entities/CachedInsight.kt`, `insights/InsightFingerprint.kt`, `reminders/ReminderNotifier.kt`, `report/ReportPdfRenderer.kt`, `export/ExportEnvelope.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`; iOS `Sources/EndoApp/{Terminology,Reminders/NotificationScheduler.swift,Persistence/Models/CachedInsight.swift,Features/Insights/InsightsCopy.swift}`, `Tests/EndoAppTests/CopyAuditTests.swift`, `project.yml`. HIGH
- [Android Developers Blog: Per-app language preferences, part 1](https://android-developers.googleblog.com/2022/11/per-app-language-preferences-part-1.html): `AppCompatDelegate.setApplicationLocales`, backport, `AppLocalesMetadataHolderService` `autoStoreLocales`, `localeConfig`, SharedPreferences migration. MEDIUM (2022; verify against current AppCompat docs)
- [Zendesk Android SDK: App language](https://developer.zendesk.com/documentation/zendesk-web-widget-sdks/sdks/android/app_language/): corroborates `setApplicationLocales` + `autoStoreLocales` behavior. MEDIUM
- From prior knowledge, needs verifying: AGP 8.1+ `generateLocaleConfig`; iOS has no public runtime per-app language API (Settings-app per-app language since iOS 13); compiled String Catalogs produce `.lproj` tables usable via `Bundle(path:)`. MEDIUM/LOW

---
*Architecture research for: multilingual (EN/DE/FR/ES) + in-app language selection on an existing Android + iOS endometriosis tracker*
*Researched: 2026-10-31*
