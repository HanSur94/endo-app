# Stack Research: v2.1 Multilingual (EN/DE/FR/ES and an in-app language picker)

**Domain:** Localizing an existing privacy-first health tracker on two native platforms (Android Compose + iOS SwiftUI)
**Researched:** 2026-10-31
**Confidence:** HIGH for the Android platform APIs and resource system. MEDIUM-HIGH for the iOS in-app override pattern (an early spike should prove it). MEDIUM for SPM/XcodeGen String Catalog tooling details.

> This replaces the v1 STACK.md, which is still in git history. It covers only what v2.1 adds. The existing stack (Kotlin 2.3.21 / AGP 9.2.0 / Compose BOM 2026.06.00 / compileSdk 36 / minSdk 26; Swift 6 / iOS 18 / XcodeGen) does not change.

## TL;DR Decisions

1. **Android: no new dependency.** On API 33+ use the framework `LocaleManager`. On API 26–32 use a small app-owned `AppLocaleController` that persists the tag and wraps `attachBaseContext`. AGP's `generateLocaleConfig` plus `localeFilters` handles the rest. AppCompat is the documented alternative (see Alternatives). It is not recommended here, because `MainActivity` and `PermissionsRationaleActivity` are `ComponentActivity` on a platform `Theme.Material` theme that deliberately has no AppCompat (`res/values/themes.xml`).
2. **Android copy moves from Kotlin constants to `res/values*/strings.xml` (+ `<plurals>`).** Only the resource system gives per-locale resolution, system per-app language settings, `pluralStringResource`, and translator-friendly files. The ClaimsPolicy audit then scans the XML files directly for every locale. That is stronger than the `CopyAuditRegistry` hand-registration: a string in `res/` is audited by construction.
3. **Both insight engines (`:insights` Kotlin, `EndoInsights` Swift) stay language-neutral.** Instead of finished English sentences, they emit a structured **message descriptor**: template key, typed args, and a pure English renderer kept inside the engine so the 157-case parity suite stays green. The app layer renders the descriptor through its platform catalog. This keeps `:insights` free of android imports, keeps `EndoInsights` free of resources, so there is no `Bundle.module` and no SwiftPM xcstrings build gap, and makes the cached `CachedInsight.payload` language-independent. The last point matters on iOS, where the cache syncs through CloudKit between devices that may use different languages.
4. **iOS: String Catalogs (`Localizable.xcstrings` + `InfoPlist.xcstrings`) in the app target only.** The in-app picker is the live source of truth and is applied by passing an explicit `Locale` through the app: `.environment(\.locale, …)` at the root, `LocalizedStringResource.locale` for non-SwiftUI strings, and an explicit locale on every formatter and in the PDF. It is mirrored to `AppleLanguages` so system-owned surfaces (HealthKit permission sheet strings, Settings) match after relaunch.
5. **Cross-platform sync uses repo-local files plus tests, not a SaaS.** One canonical `l10n/glossary.json` and `l10n/claims-policy.<lang>.json` are read by both platforms' audit tests. Keys use one snake_case scheme on both platforms, and a key/placeholder parity check catches drift. No Crowdin/Lokalise/Phrase: they add accounts and cost, and the native-speaker review is a human-gated step anyway.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Android resources: `res/values-{de,fr,es}/strings.xml` + `<plurals>`** | platform (AGP 9.2.0) | All `:app` UI copy for 4 locales | The only mechanism that integrates with per-app language, `stringResource`/`pluralStringResource` recomposition on config change, `Context.getString` outside Compose (WorkManager reminder notification, PDF), and Play's language detection. Kotlin `object FooCopy` constants cannot vary by locale without rebuilding a resource system by hand. **HIGH** |
| **AGP `androidResources { generateLocaleConfig = true }`** + `res/resources.properties` (`unqualifiedResLocale=en-US`) | AGP 8.1+ (have 9.2.0), compileSdk 33+ (have 36) | Auto-generates the `LocaleConfig` so Android 13+ lists the app's 4 languages under *Settings → App languages* | Official, zero-code, and kept in sync with the `values-*` dirs automatically. Do **not** also hand-write `res/xml/locale_config.xml`: the build fails if both exist. **HIGH** (developer.android.com/guide/topics/resources/app-languages) |
| **AGP `androidResources { localeFilters += listOf("en", "de", "fr", "es") }`** | AGP 8.8+ (replaces `defaultConfig.resourceConfigurations`) | Strips the ~80 locales that AndroidX/Compose/Health Connect libraries ship | Without it, library strings (e.g. Material3 a11y labels, the Health Connect client) appear in Thai or Japanese while the app is in English. It also shrinks the APK and keeps the generated locale list to exactly the 4 supported languages. **MEDIUM-HIGH**: verify the generated `LocaleConfig` in the merged manifest after the first build. |
| **Framework `android.app.LocaleManager`** (`applicationLocales`) | API 33+ | Set/get the per-app language on Android 13+ | It is the system's own per-app store, so the in-app picker and *Settings → App languages* share one source of truth. The system recreates activities and re-resolves resources for **every** context, Application included. **HIGH** |
| **App-owned `AppLocaleController`** (~60 LOC in `:app`, no library) | API 26–32 path | Persist the chosen tag (SharedPreferences, read synchronously) and apply it via `attachBaseContext` (`createConfigurationContext`) in both activities and `Application`, plus `Locale.setDefault` and `recreate()` | This reproduces what AppCompat does internally, for the two `ComponentActivity`s the app actually has, without adding AppCompat. On first launch after an OS upgrade to 33+, hand the stored tag to `LocaleManager` once. A synchronous read is needed because `attachBaseContext` runs before coroutines. Use plain SharedPreferences for this one key: DataStore is async, and AppCompat's `autoStoreLocales` makes the same blocking read. **HIGH** for the mechanism; mirrors official guidance. |
| **`androidx.core` `LocaleListCompat` / `ConfigurationCompat` / `ContextCompat.getContextForLanguage`** | already transitive via `activity-compose` 1.13.0 (core 1.13+) | Read the effective locale list on any API level; get a correctly localized context for non-Activity code (`ReminderNotifier`, `ReportPdfRenderer`) | Already on the classpath, so no new coordinate. Non-Activity contexts are where per-app language most often silently fails below API 33. **HIGH** |
| **iOS String Catalog `Localizable.xcstrings`** | Xcode 15+ (Xcode 26 toolchain in use) | All app UI copy + plural variations for en/de/fr/es | Apple's current format. It compiles to per-`lproj` `.strings`/`.stringsdict` at build time, supports plural/device variants, tracks stale/needs-review state per string, and is plain JSON, so a test can parse it. Lives in `Sources/EndoApp/Resources/`. **HIGH** |
| **iOS `InfoPlist.xcstrings`** | Xcode 15+ | Localize `NSHealthShareUsageDescription`, `CFBundleDisplayName` | The HealthKit permission sheet is rendered by the system from Info.plist. These strings follow the process language (`AppleLanguages`), not the in-app environment locale. This is why the picker also mirrors to `AppleLanguages`. **HIGH** |
| **`Info.plist` `CFBundleLocalizations` = [en, de, fr, es]** + `CFBundleDevelopmentRegion = en` | iOS 18 | Declare supported languages explicitly | iOS shows the per-app *Language* row in Settings only when the bundle declares more than one localization. Declaring it explicitly avoids depending on XcodeGen's `knownRegions` inference from catalogs. **HIGH** |
| **`LocalizedStringResource` with `.locale` set** + `String(localized:)` | iOS 16+ API (target is iOS 18) | Resolve non-SwiftUI strings (the `enum FooCopy { static let … }` types, notification bodies, PDF text, rendered insight descriptors) in the **in-app** language | Apple documents `LocalizedStringResource.locale` as the locale used *to look up* the string. That makes it the supported way to resolve a key in a language other than the process language without bundle swizzling. **MEDIUM-HIGH**: run a 1-day spike in Phase 1 proving de/fr/es lookup from `.xcstrings` under an English process language before converting ~700 strings. Fallback if the spike fails: `Bundle(path: Bundle.main.path(forResource: tag, ofType: "lproj"))` passed as the `bundle:` argument, which is the long-standing workaround. |
| **SwiftUI `.environment(\.locale, appLocale)`** at the `WindowGroup` root | iOS 18 | Language for `Text("literal")`/`LocalizedStringKey` lookups and every SwiftUI `.formatted()` / Swift Charts axis | SwiftUI's own localized views read the environment locale. An Apple engineer confirmed on the forums that APIs which don't read the SwiftUI environment (`String(localized:)`, UIKit, `DateFormatter`) are **not** affected, hence the explicit `LocalizedStringResource.locale` + formatter-locale rule above. **HIGH** |
| **Locale-aware formatting: `java.time` `DateTimeFormatter.ofLocalizedDate(FormatStyle.…).withLocale(l)`, `android.text.format.DateFormat.getBestDateTimePattern(l, "MMMd")`, `NumberFormat.getInstance(l)`; iOS `Date.FormatStyle`/`.formatted(…).locale(l)`, `DateFormatter.setLocalizedDateFormatFromTemplate`, `FloatingPointFormatStyle`** | platform | Dates, decimals (`3,5` in de/fr/es), and units in UI, insights and both PDF renderers | Replaces the hard-coded `Locale.US` / `ofPattern("MMM d")` / `String.format(Locale.ROOT, "%.1f")` call sites found in `ReportPdfRenderer.kt`, `ReportBuilderScreen.kt`, `LogHomeScreen.kt`, the chart files, `CorrelationEngine`/`TrendsEngine` (both platforms) and iOS `ReportPDFRenderer.swift`. Use skeleton/template patterns (`"MMMd"`, `"yMMMd"`), never fixed patterns: day/month order and separators differ per locale. Keep the `yyyyMMdd` file stamp and the `en_US_POSIX` machine formatters unlocalized. **HIGH** |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| *(none new on Android)* | — | — | `VersionCatalogAuditTests` should stay unchanged. kotlinx-serialization (already 1.11.0) is enough to parse `.xcstrings` JSON and `l10n/*.json` in parity tests. |
| *(none new on iOS)* | — | — | Foundation + SwiftUI cover everything. The `EndoInsights` `Package.swift` stays at **zero dependencies and zero resources**. |
| `androidx.appcompat:appcompat` | **1.7.1** (Jun 2025) if adopted; 1.8.0 (Aug 2026) only after `checkAarMetadata` passes on compileSdk 36 | Back-compat `AppCompatDelegate.setApplicationLocales` + `autoStoreLocales` | **Only** if the team prefers the documented path over the ~60-LOC controller. It requires both activities to become `AppCompatActivity` and the launch theme to become `Theme.AppCompat.DayNight.NoActionBar`; otherwise you get a crash or a silent no-op on API ≤ 32. Pin 1.7.1: the project already holds lifecycle at 2.10.0 because 2.11 needs compileSdk 37, and 1.8.0's transitive bumps were not verified. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Android Studio Translations Editor** | Side-by-side editing of `values-*/strings.xml`, missing-translation highlighting | Built in, no plugin needed. |
| **Android Lint `MissingTranslation` (error), `ExtraTranslation`, `PluralsCandidate`, `StringFormatInvalid`, `StringFormatMatches`** | Fail the build on untranslated keys / mismatched `%1$s` placeholders | Promote `MissingTranslation` to `error` in `lint {}`. The project already runs lint clean, so this becomes a release gate for all 4 locales. |
| **Pseudo-locales `en-XA` / `ar-XB`** (`buildTypes.debug { isPseudoLocalesEnabled = true }`) | Catch hard-coded strings and truncation (German runs ~30% longer) | Debug only. `localeFilters` must not strip them in debug. Add `en-XA` to the debug filter or leave it unfiltered there. |
| **Xcode String Catalog editor + "Export/Import Localizations" (`.xcloc`/XLIFF)** | Review state per string (`new`/`needs_review`/`translated`), hand the native-speaker reviewer an XLIFF | `xcodebuild -exportLocalizations` gives the reviewer a standard file without a SaaS account. |
| **Xcode scheme "App Language" / `-AppleLanguages (de)` launch argument; Android emulator per-app language** | Manual + UI-test runs per locale | XCUITest can pass `app.launchArguments += ["-AppleLanguages", "(de)"]`. |
| **Repo-local `l10n/` + parity tests** (Kotlin JVM test in `:insights` or `:app`, Swift Testing test in `EndoAppTests`) | One glossary, one per-language forbidden-term list, key + placeholder parity across platforms | See "Cross-platform sync" below. No generator. Native files stay the source; tests enforce agreement. |

## Integration Points (where the new stack touches existing code)

### Android `:app`
- **`content/*Copy.kt` (≈20 objects implementing `AuditableCopy`)** → each string becomes an `R.string.*`/`R.plurals.*` entry. The `*Copy` objects can stay as thin key holders (`@StringRes val title = R.string.insights_title`) so call sites barely change. `CopyAuditTest` is rewritten to parse `src/main/res/values*/strings.xml` (plain XML parsing in a JVM test, no Robolectric needed) and to scan each locale against **that locale's** ClaimsPolicy list. `StoreListingCopy`, `PrivacyPolicyText` and `DataSafetyDeclaration` hold long-form, non-UI text. Keep them as Kotlin/markdown per locale (e.g. `l10n/store/<lang>.md`), still audited, rather than forcing them into `strings.xml`.
- **`MainActivity`, `PermissionsRationaleActivity`, `Application`** → `attachBaseContext` override (API ≤ 32 path of `AppLocaleController`). Do **not** add `android:configChanges="locale"`. Let the activity recreate, and check that check-in drafts survive in `rememberSaveable`/ViewModel.
- **`reminders/ReminderNotifier.kt`** (WorkManager, Application context) → build the notification text from `ContextCompat.getContextForLanguage(appContext)` / the controller's wrapped context. Below API 33 this is the classic place where the reminder shows up in the system language.
- **`report/ReportPdfRenderer.kt` + `ReportBuilderScreen.kt`** → inject a `Locale` (and a localized `Context`/resources) into the renderer. Replace `Locale.US` patterns with localized/skeleton formatters and `"%.1f h"` with `NumberFormat` + a localized unit string (`R.string.unit_hours_short` with `%1$s`).
- **Settings screen** → picker with 5 options ("System default" + 4 endonyms: English, Deutsch, Français, Español). Endonyms are **not** translated. "System default" = `LocaleManager.applicationLocales = LocaleList.getEmptyLocaleList()` (or clear the stored tag below 33).

### Android `:insights` (pure JVM, boundary-linted)
- **No android imports, no `java.util.ResourceBundle` files.** Engines (`CorrelationEngine`, `TrendsEngine`, `RecommendationsEngine`, `FlareEngine`, `CyclePhaseMapper`, `ExploreSeries`) emit a `@Serializable` descriptor, e.g. `InsightText(templateId: String, args: List<InsightArg>)` with `InsightArg` = `Factor(enum)` | `Decimal(value, fractionDigits)` | `Count(int)` | `Day(LocalDate)` | `Window(TrendWindow)`. Numbers stay **raw**: the app formats them per locale, which also fixes `String.format(Locale.ROOT, "%.1f")`.
- Keep an `EnglishInsightRenderer` inside `:insights`, producing byte-identical current sentences, so the 157 parity cases and the `ForbiddenTerms` gate keep running unchanged on English. The `:app` renderer maps `templateId` → `R.string`/`R.plurals` (an exhaustive `when` over a sealed/enum template set, so a new template cannot ship unmapped).
- **`CachedInsight.payload`** (serialized `Insight` JSON) now stores the descriptor, which makes it language-neutral. Bump the insight fingerprint or format version so old English-text payloads are recomputed, not decoded. No Room schema change: `payload` is an opaque string.

### iOS app
- **`enum FooCopy { static let x = "…" }`** (~130 constants across `InsightsCopy`, `FlareCopy`, `RecommendationsCopy`, `ExploreCopy`, `ReportCopy`, `HealthConnectCopy`, `Disclaimer`, `PrivacyPolicyText`, …) → `static var x: String { L10n.string("insights_title") }` or `LocalizedStringResource` statics, resolved with the app locale. They must be computed (`var`/func), not stored `let`: a stored static is evaluated once and goes stale after a language switch.
- **~70 `Text("literal")` call sites** are already `LocalizedStringKey` and are extracted into the catalog automatically. Make sure their keys follow the shared key scheme (prefer `Text(LocalizedStringResource)` from the copy layer over literal-as-key, so keys match Android).
- **`AppLanguage` setting** (UserDefaults, local-only, not synced) → root `.environment(\.locale)`, an `@Observable` `L10n` the copy layer reads, plus `UserDefaults.standard.set([tag], forKey: "AppleLanguages")`, or `removeObject` for "System default", so the next cold launch and the HealthKit sheet agree. Also offer a row that opens `UIApplication.openSettingsURLString`, since Settings → Endo-App → Language is the same switch.
- **`ReportPDFRenderer.swift`** (`DateFormatter` ×2) → `formatter.locale = appLocale` + `setLocalizedDateFormatFromTemplate("yMMMd")`. Pass the locale into the renderer explicitly and render all PDF text through `L10n` (Core Graphics text never sees the SwiftUI environment).
- **`EndoInsights` package** → same descriptor pattern as Kotlin (`InsightText` + `EnglishInsightRenderer`), `Codable`. `CachedInsight.payload` (SwiftData, **CloudKit-synced**) becomes language-neutral. This is required, not optional: with rendered text, an iPad in German would show a cached English sentence produced on the iPhone.
- **`CopyAuditTests.swift`** → parse `Localizable.xcstrings` (JSON) and scan each language's `stringUnit.value` against that language's forbidden list, including every plural variation.

### Cross-platform sync (one source of truth for terms and safety lists, native files for strings)
- `l10n/glossary.json`: canonical term per concept per language (e.g. *flare* → *Schub* / *poussée* / *brote*; *cycle phase*; *self-tracked, not diagnostic*). Both platforms' tests assert the glossary term appears where the English term appears, for keys tagged with that concept.
- `l10n/claims-policy.{en,de,fr,es}.json`: forbidden medical/causal/alarmist/imperative/gendered substrings per language. **Replaces the duplicated hard-coded lists** in `ClaimsPolicy.kt`, `ForbiddenTerms.kt` and `ClaimsPolicy.swift`/`ForbiddenTerms.swift`. Both test suites read the same file: Kotlin via a project-relative path, Swift via `#filePath`-relative path in tests.
- **Key scheme:** `area_screen_element` snake_case, identical on both platforms. Android resource names cannot contain dots, so dots are out on iOS too. Platform-only keys (`health_connect_*` vs `apple_health_*`) live under an explicit allowlist.
- **Parity test** (Kotlin JVM, kotlinx-serialization already present): diff Android `values/strings.xml` keys vs `Localizable.xcstrings` keys minus the allowlist, and check placeholder count/order per key per language. Android `%1$s`/`%1$d` ↔ iOS `%@`/`%lld` must map 1:1. **Caveat:** Android work is on `claude/endo-android-version-e608a7` and iOS starts from `main`, so this test only goes green once both trees coexist. Until then each platform runs its own glossary/claims tests against the shared `l10n/` files.

## Installation

```kotlin
// android/app/build.gradle.kts — inside android { }
androidResources {
    generateLocaleConfig = true                          // AGP 8.1+ (project: 9.2.0)
    localeFilters += listOf("en", "de", "fr", "es")      // AGP 8.8+; strips library locales
}
buildTypes { getByName("debug") { isPseudoLocalesEnabled = true } }
lint { error += "MissingTranslation" }
// NO new dependencies. (Only if choosing the AppCompat alternative:
//   appcompat = "1.7.1" -> implementation(libs.appcompat), plus theme + activity changes.)
```

```properties
# android/app/src/main/res/resources.properties
unqualifiedResLocale=en-US
```

```text
android/app/src/main/res/values/strings.xml        (en, source)
android/app/src/main/res/values-de/strings.xml
android/app/src/main/res/values-fr/strings.xml
android/app/src/main/res/values-es/strings.xml

Sources/EndoApp/Resources/Localizable.xcstrings     (sourceLanguage: en; de, fr, es)
Sources/EndoApp/Resources/InfoPlist.xcstrings
Sources/EndoApp/Resources/Info.plist  -> CFBundleLocalizations = [en, de, fr, es]

l10n/glossary.json
l10n/claims-policy.{en,de,fr,es}.json
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| App-owned `AppLocaleController` (API ≤ 32) + framework `LocaleManager` (33+) | **AppCompat 1.7.1 `AppCompatDelegate.setApplicationLocales` + `autoStoreLocales`** | If the team values the documented, Google-maintained path over a dependency-free one. Cost: a new coordinate with a large transitive set (fragment, emoji2, drawerlayout, …), `AppCompatActivity` + an AppCompat theme for both activities, and still a manual localized context for WorkManager/PDF. AppCompat explicitly needs an `AppCompatActivity` context on API ≤ 32; a `ComponentActivity` silently does nothing. |
| `strings.xml` for Android UI copy | Keep Kotlin `object` copy, branching on a `Locale` param | Only for long-form documents (privacy policy, store listing, data-safety text) that are not UI strings and that you want reviewed as whole documents. Not for UI: you lose system integration, plurals, lint, and translator tooling. |
| Language-neutral descriptors from the engines | Engines take a `Locale` and render localized sentences themselves (Kotlin per-language template tables in `:insights`; `Bundle.module` catalog in `EndoInsights`) | If the descriptor refactor threatens the parity fixtures more than expected. Downsides: two translation stores per platform, cached payloads become language-bound (a real bug with CloudKit sync), and on iOS you inherit the SwiftPM String Catalog gaps (`swift build`/`swift test` do not compile `.xcstrings`; lookups need `bundle: .module` or Xcode 26's `#bundle`). |
| In-app picker with explicit locale threading on iOS | **Settings-only** (row deep-links to `UIApplication.openSettingsURLString`; iOS relaunches the app in the chosen language) | If the `LocalizedStringResource.locale` spike fails *and* the lproj-bundle fallback is judged too fragile. It is zero-risk and every API just works, but it is not an "in-app" choice, so the requirement would need re-wording. |
| Repo-local `l10n/` files + tests | Crowdin / Lokalise / Phrase / Tolgee (SaaS TMS) | Only with a growing volunteer-translator community. Today the drafter is Claude and the reviewer is one native speaker per language, so a TMS adds accounts and cost for no gain. |
| Native catalogs as sources, tests enforce parity | A single master file (JSON/YAML) generating both `strings.xml` and `.xcstrings` | Reconsider at >2 more languages. Generation fights each IDE's editor (Xcode rewrites `.xcstrings`) and the platforms differ in copy (Health Connect vs Apple Health). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `Resources.updateConfiguration(...)` / mutating `Locale.setDefault` alone on Android | Deprecated since API 25, and it does not survive config changes or reach other contexts | `LocaleManager` (33+) / `createConfigurationContext` in `attachBaseContext` (≤ 32) |
| Hand-written `res/xml/locale_config.xml` **together with** `generateLocaleConfig = true` | The build fails when both exist | `generateLocaleConfig` + `resources.properties` only |
| `android:configChanges="locale|layoutDirection"` on the activities | Suppresses recreation, so View-based system UI and some resources stay stale. Compose would recompose, but any `remember`-cached formatter/string goes stale silently | Let the activity recreate; keep state in `rememberSaveable`/ViewModel |
| Top-level `private val FORMAT = DateTimeFormatter.ofPattern("MMM d")` (current pattern in 6 files) | Captures `Locale.getDefault()` once at class-load and uses an English-ordered pattern | Build formatters from the current `LocalConfiguration` locale (`remember(locale)`) with skeleton patterns |
| `String.format(Locale.ROOT/US, "%.1f")` for user-visible numbers (both engines + PDF) | Prints `3.5` where de/fr/es readers expect `3,5` | Engines emit raw `Double`; the app formats with `NumberFormat`/`FloatingPointFormatStyle` in the active locale |
| Concatenating sentence fragments (`"$prefix $factor $suffix"`, `bleedingFragment`, `cycleDayPrefix`) | Word order and gender agreement differ in DE/FR/ES; fragments are untranslatable | Whole-sentence templates with positional placeholders (`%1$s`), one per variant |
| Hand-rolled `if (n == 1)` plural logic | French uses `one` for **0 and 1**. CLDR also gives fr/es a `many` category for large round numbers | `<plurals>` + `pluralStringResource`; `.xcstrings` plural variations (`one`/`many`/`other`) |
| `.environment(\.locale)` alone on iOS | Does not reach `String(localized:)`, stored `static let` copy, `DateFormatter`, Core Graphics PDF text, or notification bodies | `.environment(\.locale)` **plus** `LocalizedStringResource.locale` **plus** explicit formatter locales |
| Bundle swizzling (`object_setClass(Bundle.main, …)`) on iOS | Private-behaviour hack, fragile across iOS releases, a review risk | `LocalizedStringResource.locale`; fallback an explicit `lproj` `Bundle` passed as `bundle:` |
| Machine-translation SDKs / APIs (Google ML Kit Translate, DeepL, cloud LLMs) at runtime | Breaks the **no-`INTERNET`** invariant (Android) and privacy-first positioning; on-device models add ~30 MB per language and are not safety-reviewed | Translations drafted offline, shipped inside the app, native-speaker reviewed |
| Localizing inside `EndoInsights` / `:insights` | Puts resources into the boundary-linted pure modules, duplicates the translation store, and makes cached payloads language-bound | Descriptors from the engines, rendering in the app layer |

## Stack Patterns by Variant

**If the iOS `LocalizedStringResource.locale` spike fails:**
- Use `Bundle(path: Bundle.main.path(forResource: tag, ofType: "lproj")!)` as the `bundle:` for `String(localized:bundle:)` in the `L10n` resolver. Keep `.environment(\.locale)` for SwiftUI.
- Because `.xcstrings` compiles to real per-language `.lproj` folders in the app bundle, explicit-bundle lookup is reliable on every iOS version.

**If minSdk is ever raised to 33:**
- Delete the API ≤ 32 branch of `AppLocaleController`. `LocaleManager` alone is then sufficient.
- Because the system handles persistence, recreation, and all contexts natively from Android 13.

**If RTL languages (ar/he) are added later:**
- Enable `android:supportsRtl="true"` checks, use start/end instead of left/right throughout Compose, and test the `ar-XB` pseudo-locale. Swift Charts and the body map need mirroring review.
- Because none of the four v2.1 languages are RTL, this is deliberately out of scope now.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `generateLocaleConfig` | AGP ≥ 8.1, compileSdk ≥ 33 | Project: AGP 9.2.0 / compileSdk 36. OK. |
| `androidResources.localeFilters` | AGP ≥ 8.8 | Replaces the deprecated `defaultConfig.resourceConfigurations`. |
| `LocaleManager` | API ≥ 33 | Gate with `Build.VERSION.SDK_INT >= TIRAMISU`; minSdk 26 needs the fallback path. |
| `pluralStringResource` | Compose UI (BOM 2026.06.00) | Stable in `androidx.compose.ui:ui`. No extra artifact. |
| AppCompat 1.7.1 *(alternative only)* | minSdk 21+, Activity 1.8+ | 1.8.0 raised its default minSdk to 23 and was not verified against compileSdk 36. Run `checkAarMetadata` before adopting. |
| String Catalogs | Xcode 15+; `.xcstrings` in **app target** | In SwiftPM targets, `swift build`/`swift test` don't compile catalogs (Xcode-only), which is one more reason to keep `EndoInsights` resource-free. |
| `#bundle` macro | Xcode 26 | Not needed under this plan. Relevant only if a package ever owns strings. |
| XcodeGen 2.45.4 + `.xcstrings` | Treated as a resource file | Declare `CFBundleLocalizations` in Info.plist explicitly rather than relying on `knownRegions` inference. Verify `knownRegions` in the generated project after `xcodegen generate`. **MEDIUM** |
| JVM unit tests vs device for date output | JDK CLDR vs Android ICU data | Localized patterns can differ slightly (e.g. a trailing period after German abbreviated months). Assert structure/locale selection in JVM tests, not exact formatted strings across runtimes. |

## Sources

- https://developer.android.com/guide/topics/resources/app-languages: `generateLocaleConfig` (AGP 8.1+, compileSdk 33+), `resources.properties`, manual-config conflict, `LocaleManager` (33+), AppCompat 1.6+ back-compat needs an `AppCompatActivity` context on ≤ 32, `autoStoreLocales` blocking read, Compose note (HIGH)
- https://developer.android.com/jetpack/androidx/releases/appcompat: 1.7.1 (2025-06-04, Activity 1.8.0), 1.8.0 (2026-08-12, minSdk default 23), 1.7.0-alpha03 non-Activity locale APIs (`ContextCompat.getContextForLanguage`, `LocaleManagerCompat`) (HIGH)
- WordPress-Android PR #22655 / /e/OS commits: `resourceConfigurations` → `androidResources.localeFilters` in AGP 8.8; a reported `generateLocaleConfig` + Application-context string-resolution issue, which is why non-Activity code takes an explicitly localized context (MEDIUM)
- https://developer.apple.com/documentation/foundation/localizedstringresource/locale: `locale` is the lookup locale; set it before passing to `String(localized:)` (HIGH for the documented contract; MEDIUM until spiked with `.xcstrings`)
- https://developer.apple.com/forums/thread/828990: overriding the environment locale doesn't affect APIs that don't read the SwiftUI environment (HIGH)
- https://justdoswift.substack.com/p/switching-app-language-at-runtime, https://appcoda.com/swiftui-language-switch/: in-app language switching patterns and the `AppleLanguages`-needs-relaunch limitation (MEDIUM)
- https://elegantchaos.com/2026/02/12/string-catalogues.html, https://zenn.dev/zunda_pixel/articles/be92fefdb93f76, https://developer.apple.com/forums/thread/737806, https://danielsaidi.com/blog/2025/12/02/a-better-way-to-localize-swift-packages-with-xcode-string-catalogs: SPM catalogs need `defaultLocalization` + `.process`, `bundle: .module`, and `swift build` doesn't compile catalogs (MEDIUM)
- https://forums.swift.org/t/pitch-introduce-bundle-macro/79288, https://wwdcnotes.com/documentation/wwdc25-225-codealong-explore-localization-with-xcode/: `#bundle` (Xcode 26) (MEDIUM)
- Codebase inspection (this worktree): `android/app/build.gradle.kts`, `gradle/libs.versions.toml`, `res/values/themes.xml` (platform theme, no AppCompat), `MainActivity`/`PermissionsRationaleActivity : ComponentActivity`, `content/*Copy.kt`, `CachedInsight.kt`/`.swift` (`payload` = serialized Insight), `Locale.US`/`Locale.ROOT`/`ofPattern` call sites, `project.yml` (XcodeGen, iOS 18, zero-dep `EndoInsights`), `Sources/EndoApp/**/*Copy.swift` static-let copy (HIGH)

---
*Stack research for: v2.1 Multilingual (EN/DE/FR/ES, in-app language selection, Android + iOS)*
*Researched: 2026-10-31*
