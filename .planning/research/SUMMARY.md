# Project Research Summary

**Project:** Endo-App — milestone v2.1 Multilingual
**Domain:** Localizing an existing privacy-first, claims-audited endometriosis tracker (Android Kotlin/Compose + iOS SwiftUI) into EN/DE/FR/ES with an in-app language picker
**Researched:** 2026-10-31
**Confidence:** MEDIUM-HIGH (HIGH on code-grounded integration points and Android platform APIs; MEDIUM on the iOS in-app override, which needs a spike; MEDIUM/LOW on per-language claims lists and glossary, pending native review)

> Replaces the v2.0 SUMMARY.md (preserved in git history). Detail lives in STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md.

## Executive Summary

v2.1 is not mainly a translation job — it is a **safety-system migration that happens to involve translation**. Neither platform has a string catalog today: Android copy sits in ~19 Kotlin `*Copy` objects with zero `strings.xml` entries; iOS uses `static let` English constants plus ~70 inline `Text("…")` calls. Both insight engines build English sentences by concatenation (`label.lowercase()`, `Locale.ROOT` numbers), and those rendered sentences are persisted in `CachedInsight.payload` (on iOS synced through CloudKit). The claims audit (`ForbiddenTerms` / `ClaimsPolicy` / `HonestyGate`) works on English substrings only, so DE/FR/ES text would pass it unchecked.

Recommended approach:
1. **Policy first** — shared glossary and curated per-language forbidden lists (with allow-lists for innocent host words) before any translated string lands.
2. **Engine contract** — both engines emit a language-neutral structured message (key + typed args) next to the unchanged English text; the app layer renders it from one shared message file. Keeps the 157-case parity suite and `HonestyGate` untouched and makes the cache language-neutral.
3. **Native catalogs** — all UI copy moves into `strings.xml` + `<plurals>` and `Localizable.xcstrings`; the catalog files become the audit registry by construction.
4. **Language as a value** — worker, PDF renderer, insight renderer and tests receive the language explicitly; nothing depends on ambient locale state.

No new dependencies on either platform; nothing touches the network (the no-`INTERNET` invariant holds).

Main risks: the audit silently passes translated claims (a translation turns "possibly associated" into a causal / imperative / "detects" claim — MDR/HWG exposure in Germany); the engine refactor breaking the parity anchor; mixed-language output after a switch (stale cache, reminder in system language, old channel name, PDF stuck in `Locale.US`); and the iOS in-app override not being a first-class API. Mitigations: per-language mutation tests (seeded bad sentences must fail), an additive message contract gated by `render(message, en) == headline` on all 157 fixtures, explicit-locale plumbing with per-language tests, and a 1-day iOS spike with a documented fallback. Native-speaker and legal review stays a human-gated release item that tests must never claim.

## Key Findings

### Recommended Stack

No new libraries. Android: platform resource system + AGP `generateLocaleConfig` / `localeFilters`. iOS: String Catalogs in the app target only. The pure engine modules (`:insights`, `EndoInsights`) stay resource-free and dependency-free. Cross-platform agreement via repo-local JSON + tests, not a translation SaaS.

**Core technologies:**
- **Android `res/values{,-de,-fr,-es}/strings.xml` + `<plurals>`** — the only mechanism with per-app-language integration, `pluralStringResource`, lint `MissingTranslation`/`StringFormatMatches`, and `Context.getString` for worker and PDF.
- **AGP `androidResources { generateLocaleConfig = true; localeFilters += [en,de,fr,es] }` + `resources.properties`** — lists the app under Android 13+ App languages and strips ~80 library locales. Do not also hand-write `locale_config.xml` (build fails if both exist).
- **Framework `LocaleManager` (API 33+) + app-owned `AppLocaleController` (API 26–32, ~60 LOC)** — per-app language without AppCompat (Resolved Conflict 1).
- **iOS `Localizable.xcstrings` + `InfoPlist.xcstrings` + `CFBundleLocalizations` = [en,de,fr,es]** — compiles to per-language `.lproj`, parseable as JSON in tests; the per-app Language row only appears with >1 declared localization.
- **iOS explicit-locale resolution** — root `.environment(\.locale)` + `LocalizedStringResource.locale` (or an explicit `lproj` `Bundle`) for non-SwiftUI strings + explicit formatter locales. Required because the environment locale does not reach `String(localized:)`, `DateFormatter`, Core Graphics PDF text, or notification bodies.
- **Locale-aware formatters with skeleton patterns** (`ofLocalizedDate`, `getBestDateTimePattern(l,"MMMd")`, `NumberFormat`, `Date.FormatStyle`) replace `Locale.US` / `ofPattern("MMM d")` / `String.format(Locale.ROOT,…)` at every display site; serialization stays `Locale.ROOT`/ISO.
- **Pseudo-locales** (`en-XA`, debug only) + Xcode double-length pseudolanguage — catch hard-coded strings and ~30% German expansion early.

### Expected Features

**Must have (table stakes):**
- Default = system language, fallback English, base languages only (de-AT → de)
- Settings "Language" row with endonyms (English / Deutsch / Français / Español) + "System default"
- Switching keeps in-progress data (check-in draft, body-map state survive recreation)
- All ~700 UI strings per platform localized + localized insight sentences (the core value)
- Correct plurals/agreement (FR treats 0 as `one`); no fragment concatenation
- Locale-aware dates/decimals; week start from the **region**; engine math stays locale-free
- Notifications in the current language (Android resolves at fire time; iOS reschedules on change)
- Localized PDF report with explicit locale; user free text stays verbatim
- Localized onboarding, consent, privacy policy, Health rationale strings
- Per-language ClaimsPolicy/CopyAudit with completeness check; cross-locale export/import round-trip test
- Store listings ×4, audited, including keyword fields

**Should have (differentiators):**
- Separate **"Report language"** in the PDF builder — near-free once the renderer takes an explicit locale; highest value/cost, pull into v2.1
- Live switching on Android without restart; listing in Android 13+ system App languages
- Patient-friendly, non-gendered lay terminology per language (e.g. DE "Menschen mit Endometriose", "Blähbauch (Endo-Belly)")

**Defer (v2.x+):** localized store screenshots; clinician-term parentheses in the PDF; more languages (IT/PT/NL/PL), regional variants (de-CH, es-419, fr-CA), RTL.

**Anti-features:** runtime/machine translation (breaks no-INTERNET + ClaimsPolicy); translating user notes; persisting localized labels; per-locale insight logic; Bundle swizzling; forced first-launch language pick; literal translation of the English forbidden list.

### Architecture Approach

**Language-neutral core, localized edges.** Persisted data (enum raws, `signalKey`, UUIDs, ISO dates, `CachedInsight.payload`, export envelope) never contains a localized string. Engines emit structured `InsightMessage`s; the app layer resolves those and all UI chrome through an `L10n` façade over native catalogs plus one shared insight-message file. Every non-UI consumer gets the language as a parameter. Every word an insight can render comes from closed sets (templates × labels), so an **exhaustive per-language audit of every template × every label** is possible and complete.

**Major components:**
1. **`l10n/` (repo root)** — `glossary.json` (test-only contract), `insight-messages.json` (bundled at runtime in both apps), `claims-policy.{de,fr,es}.json` (forbidden + gendered + precision + allow-lists), `golden/insights.{en,de,fr,es}.txt`.
2. **`InsightMessage` / `InsightArg` / `InsightTemplate`** (pure, in `:insights` and `EndoInsights`) — structured message + deterministic placeholder renderer; per-language decimal separator from the JSON (not platform formatters) so cross-platform goldens stay byte-equal.
3. **`InsightMessageRenderer`** (app layer, both platforms) — renders for the active language; fail-closed per-language claims guard mirroring `HonestyGate`.
4. **`AppLanguage` store + applier** — `system|en|de|fr|es`; "System" is a stored value, not an empty one.
5. **Native catalogs as audit registry** — `CopyAuditTest` / `CopyAuditTests` parse XML/xcstrings directly; a literal-scan test catches `Text("…")` bypassing the catalog.
6. **Language picker** — `ui/settings/LanguageSection.kt`, `Features/Settings/LanguagePickerView.swift`.

### Critical Pitfalls

1. **English substring audit silently stops protecting DE/FR/ES** — matches nothing foreign ("verursacht" ⊅ `cause`) and false-positives ("her" ⊂ "vorher", "use" ⊂ "Pause", "sie" = formal you). **Avoid:** curated per-language lists, NFC + locale folding, per-language host-word allow-lists ("entraînement", "Behandlungsteam"), red-capable mutation tests per language — **before** any translation lands.
2. **Translation turns hedged copy into claims** ("führt zu", "erkennt Schübe", imperative "Besprich das mit deiner Ärztin"). **Avoid:** glossary with approved/forbidden renderings + fixed register, per-string context for drafts, draft-then-audit merges, back-translation spot checks, named native-speaker + legal sign-off as release gate.
3. **Concatenated engine sentences are untranslatable and rendered text is cached.** **Avoid:** structured message contract, whole-sentence templates with named placeholders, labels in grammatically invariant slots ("label-as-quoted-noun"), no `lowercase()` in engines, cache stores the message + fingerprint version bump.
4. **Android ≤ API 32 and outside activities** — choice lost on cold start, WorkManager `applicationContext` in system language, stale channel name, `Locale.getDefault()` wrong. **Avoid:** synchronous persisted tag, `attachBaseContext` wrapping, localized context for worker/PDF, channel re-creation on change, locale from `Configuration` (never `Locale.getDefault()`), API 30 emulator check plus 34+.
5. **Expansion and formatting break the PDF and compliance text** — longer DE/FR overflows fixed coordinates and can clip the "self-tracked, not diagnostic" banner; "language = region" gives a German resident who picks English MDY dates and Sunday week start. **Avoid:** measured-height pagination, reserved disclaimer block, 4-language worst-case PDF test, display locale = chosen language + system region.

## Resolved Conflicts

### 1. Android switching — no AppCompat (STACK) vs AppCompat (FEATURES, ARCHITECTURE)
**Recommendation: no AppCompat.** Framework `LocaleManager` on API 33+; small app-owned `AppLocaleController` on API 26–32 that persists the tag in SharedPreferences (read synchronously), wraps `attachBaseContext` in both activities and `Application`, calls `recreate()`, and hands the stored tag to `LocaleManager` once after an OS upgrade to 33+.

**Why:** (a) AppCompat forces `AppCompatActivity` + a `Theme.AppCompat.*` parent; both activities are `ComponentActivity` on a deliberate platform `Theme.Material` theme — getting it wrong crashes on launch or silently no-ops on ≤32. (b) AppCompat leaves WorkManager `applicationContext` and the PDF unlocalized below 33, so explicit localized contexts are needed anyway. (c) Zero new dependencies; `VersionCatalogAuditTests` unchanged. (d) Same UX: live switching via recreation on all API levels; system App-languages listing via `generateLocaleConfig`.

**Persistence:** SharedPreferences for this one key (not DataStore) because `attachBaseContext` runs before coroutines. On 33+ `LocaleManager` is the source of truth and the stored tag is reconciled at startup.

**Fallback trigger:** if the controller fails API 30 cold-start/recreation checks in the Android plumbing phase, adopt AppCompat **1.7.1** (not 1.8.0): `AppCompatActivity` + `Theme.AppCompat.DayNight.NoActionBar` + `AppLocalesMetadataHolderService` with `autoStoreLocales`; re-run `ManifestPermissionsTest`. **Status: decided, with fallback.**

### 2. iOS — Settings deep-link (FEATURES, PITFALLS) vs app-managed in-app override (STACK, ARCHITECTURE)
**Recommendation: app-managed in-app override, gated by a 1-day spike at the start of the iOS plumbing phase.** An `AppLanguageStore` (UserDefaults, local-only, never CloudKit-synced) drives root `.environment(\.locale)`, an `L10n` resolver for all non-SwiftUI copy (`LocalizedStringResource.locale`, fallback explicit `Bundle(path: …lproj)`), explicit formatter/PDF locales, and `NotificationScheduler.rescheduleAll()` on change. A secondary "Open iOS Settings" row remains.

**Why:** the milestone says "chosen inside the app" on both platforms; the PDF report-language differentiator and correctly localized PDF/notification text need explicit-locale lookup regardless; once all copy goes through `L10n` (the catalog migration does that anyway) the marginal cost is small; no swizzling, no reliance on `AppleLanguages` for in-app text.

**Spike pass criteria:** de/fr/es resolve from compiled `.xcstrings` under an English process language via both `LocalizedStringResource.locale` and the lproj fallback; `DatePicker` and Swift Charts axes follow `.environment(\.locale)` on device; switching re-renders the root without losing a check-in draft.

**Fallback (both lookup paths fail):** Settings deep-link only (`UIApplication.openSettingsURLString` + `UIPrefersShowingLanguageSettings` if needed) and re-word the iOS requirement.

**Open sub-decision (spike):** mirror the choice to `AppleLanguages` so system-owned sheets (HealthKit permission text from `InfoPlist.xcstrings`) match after relaunch? Default if unresolved: **do not write `AppleLanguages`**; phrase rationale strings so a mismatch is not confusing. UI tests use `-AppLanguage de`, not `-AppleLanguages`.

### 3. Shared directory — `l10n/` (STACK) vs `i18n/` (ARCHITECTURE)
**Recommendation: `l10n/` everywhere** — repo root, Kotlin package `com.endoapp.app.l10n`, iOS `Sources/EndoApp/L10n/`. Layout: `l10n/glossary.json`, `l10n/insight-messages.json`, `l10n/claims-policy.{de,fr,es}.json` (one file per language so a reviewer signs off per file), `l10n/golden/insights.{en,de,fr,es}.txt`, `l10n/README.md` (translator rules). **EN lists stay in code unchanged** — `ClaimsPolicyDriftTests` regex-parses the Swift `static let forbiddenVerbs: [String] = [` declarations. **Status: decided.**

### 4. Engine contract — replace English (STACK) vs keep English + `InsightMessage` (ARCHITECTURE)
**Recommendation: additive for v2.1.** Engines keep byte-identical English `headline`/`detail` and also attach `message: InsightMessage(headlineKey, detailKey, args)`. `HonestyGate` keeps scanning English; the 157 parity fixtures stay green; a new gate asserts `render(message, en) == headline` on every fixture on both platforms. After migration the UI and PDF **render only from `message`**; English fields become parity anchor + legacy fallback, never displayed; a cached payload without `message` is a cache miss. Removing the English fields is a later-milestone cleanup. The additive form never puts the correctness anchor in a red state mid-refactor.

**Where insight templates are translated:** the **shared `l10n/insight-messages.json`** (not per-platform catalogs) — insight sentences must be byte-identical across platforms. Rules: named `{placeholders}` with identical sets across the 4 languages; no case transforms/inflection in code (`.inline` label variants where needed); deterministic number formatting (JDK CLDR ≠ Apple ICU); fingerprint version prefix (`v2|…`) so pre-v2.1 payloads recompute once.

**Gap:** `InsightTemplate` needs at least `one/other` plural selection with CLDR rules (FR 0 and 1 → `one`); specify in the engine-contract phase. **Status: decided.**

### Other alignments
- **UI catalog keys:** snake_case `area_screen_element` on both platforms (Android resource names cannot contain dots); dotted keys fine inside `insight-messages.json`.
- **Cross-platform agreement for UI chrome:** enforced through the mandatory glossary conformance test on both platforms; the Android-vs-iOS key-parity test is advisory with an allowlist (`health_connect_*` vs `apple_health_*`) and only green once both branches coexist.
- **Long-form legal/store text** (privacy policy, store listing, data safety) stays as per-locale documents (e.g. `l10n/store/<lang>.md` / fastlane metadata), still registered for the per-language audit.

## Implications for Roadmap

v2.0 ended at Phase 25 → v2.1 starts at **Phase 26**. Critical path: **26 → 27 → (28 → 29 ∥ 30) → 31 → 32 → 33.** Translation waits for both catalog migrations so strings are translated once, into final keys.

### Phase 26: Shared l10n foundation (glossary + per-language claims policy)
**Rationale:** everything later is audited against it; the audit must exist before translations (Pitfalls 1, 2).
**Delivers:** `l10n/glossary.json` (concepts × 4 languages, banned variants, register: DE du, ES tú, FR open); `l10n/claims-policy.{de,fr,es}.json` (forbidden/gendered/precision lists, allow-lists, per-locale `allowedFraming`); `ForbiddenTerms.forLanguage` / `ClaimsPolicy.forLanguage` on both platforms with NFC + locale-folded matching; red-capable mutation tests per language; drift test.

### Phase 27: Engine structured-message contract (Android `:insights` + iOS `EndoInsights`)
**Rationale:** insight translation is impossible downstream of English prose; riskiest refactor (touches the parity anchor) — early and additive.
**Delivers:** `InsightMessage`/`InsightArg`/`InsightTemplate` (with plurals) on both platforms; every engine emits `message`; EN `insight-messages.json` extracted from today's templates; `render(message,en)==headline` on all 157 fixtures; EN goldens; cache stores `message` + fingerprint bump. Swift half lands on the iOS line (`main`).

### Phase 28: Android locale plumbing + picker
**Rationale:** prove API 26–32, worker and recreation before the mass migration.
**Delivers:** `AppLocaleController` + `LocaleManager`; `generateLocaleConfig` + `localeFilters` + `resources.properties`; Settings `LanguageSection` (endonyms + System default); localized-context helper for worker and PDF; channel re-creation on change; `InsightMessageRenderer` with `InsightCard` rendering from the message; debug pseudo-locale; lint localization checks as errors; state survival for check-in draft and body map (export passphrase deliberately not saved). **Exit:** EN-only app looks identical; API 30 + API 34 checks pass.

### Phase 29: Android catalog migration + audit-by-construction
**Rationale:** largest mechanical change; must finish before translation.
**Delivers:** every `*Copy` const → `values/strings.xml` + `<plurals>` (Copy objects become `@StringRes` holders); stray literals removed + literal-scan test; `CopyAuditTest` parses XML per locale with completeness + placeholder parity; the 53 `onNodeWithText` tests move to tags/resource lookups; Robolectric locale-sweep scaffold; cross-locale export round-trip test.

### Phase 30: iOS locale plumbing + catalog migration (spike first; from `main`, parallel to 28–29)
**Rationale:** iOS lives on another branch; settle the override decision before extracting strings.
**Delivers:** 1-day spike (Resolved Conflict 2); `AppLanguageStore` + `L10n`; `Localizable.xcstrings` + `InfoPlist.xcstrings`; `CFBundleLocalizations` / `knownRegions` in `project.yml`; `static let` copy → computed; inline `Text("…")` → `L10n`; `BleedingTerm.noun` → localized with per-term sentence variants; `rescheduleAll` on language change; `CopyAuditTests` parsing xcstrings per language; xcstrings state CI check (no `new`/`stale`); UI tests by `accessibilityIdentifier` with `-AppLanguage`.

### Phase 31: Locale-aware formatting + localized PDF (both platforms)
**Rationale:** needs 28/30 plumbing; PDF carries the highest expansion/compliance risk.
**Delivers:** display locale = chosen language + system region; skeleton date formats + `NumberFormat`/`FormatStyle`; `WeekFields`/`firstWeekday` from region in UI (engine bucketing stays pinned); 24h setting respected; `Locale.US` source-scan ban outside `export/` and tests; PDF renderers take an explicit language with measured-height pagination and a reserved disclaimer block; the **separate Report language** picker; 4-language worst-case PDF test; per-locale re-baselining of the iOS byte-identical PDF guard.

### Phase 32: DE/FR/ES translations (UI + insight messages)
**Rationale:** translate once, into final keys, behind live gates.
**Delivers:** Claude-drafted `values-{de,fr,es}`, xcstrings and `insight-messages.json` translations (glossary + policy + per-string context); hard gates live — completeness, placeholder parity, claims scan, exhaustive template × label scan, glossary conformance; DE/FR/ES insight goldens byte-equal across platforms; back-translation spot checks; pseudo-locale/DE screenshot pass at max font scale (`FlowRow` for chips, short tab labels).

### Phase 33: Legal/store copy + per-language verification + release gate
**Rationale:** consent, privacy and store text carry GDPR and MDR/HWG exposure; ship only after review.
**Delivers:** localized consent, privacy policy, Health rationale; `consentTextVersion` + language in the consent record (both platforms); per-locale store listings, keywords, release notes, audited (fastlane metadata + iOS equivalent); per-language smoke sweeps + "switch language, walk every tab" check incl. next reminder and channel name; release-checklist entry for **named** native-speaker + legal review per language — human-gated, never marked done by tests.

### Phase Ordering Rationale
- **Policy → contract → plumbing → migration → formatting → translation → legal** follows hard dependencies.
- **28–29 (Android) and 30 (iOS) run in parallel** because of the branch split; shared `l10n/` files and the Swift half of 27 must reach `main` first. Cross-platform key parity only turns green once the trees coexist; until then each platform runs glossary and claims tests against the shared files.
- Formatting/PDF after plumbing because the explicit-locale parameter is created there; the report-language differentiator falls out nearly free.

### Research Flags
Needs `/gsd-plan-phase --research-phase`:
- **Phase 27** — grammar-safe templates for DE case and FR/ES agreement (`.inline` variants vs ICU `select`), plurals in `InsightTemplate`, keeping 157-case parity across both engines.
- **Phase 30** — iOS override spike (`LocalizedStringResource.locale` vs lproj bundle with `.xcstrings`), the `AppleLanguages` mirror, XcodeGen 2.45.4 + xcstrings/`knownRegions`.
- **Phases 26 / 33** — per-language claims lists and legal framing (DE MDR/HWG, French register): content research + human reviewer.

Standard patterns (skip research): **28** (official app-languages guide; only the ≤32 controller needs an emulator check), **29** (`strings.xml` migration + lint), **31** (platform formatters; PDF pagination is code-local).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH (Android) / MEDIUM-HIGH (iOS) | Android APIs/AGP flags from official docs, match the codebase (AGP 9.2.0, compileSdk 36). iOS override documented but unspiked with `.xcstrings`; XcodeGen catalog details MEDIUM. |
| Features | MEDIUM | Platform mechanics HIGH; competitor behaviour from vendor help pages (MEDIUM); terminology/claims lists drafted (MEDIUM/LOW) pending native review. |
| Architecture | HIGH | Grounded in live code (no `strings.xml`, persisted English payload, closed-set insight inputs, drift guard regex on Swift declarations). |
| Pitfalls | HIGH (code-specific) / MEDIUM (regulatory) | Platform pitfalls well established; MDR intended-purpose / HWG / GDPR need counsel per market. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address
- **iOS spike outcome** (Phase 30) — in-app override vs Settings deep-link; `AppleLanguages` mirror; re-word the iOS requirement if fallback taken.
- **Android controller on API 26–32** (Phase 28) — verify cold start, recreation, worker localization on API 30; fallback AppCompat 1.7.1.
- **French register (tu vs vous)** (Phase 26) — FEATURES leans "vous" for health apps (ES tú, DE du); human decision.
- **Authoritative legal language** (Phase 33) — German-authoritative or all-equivalent; counsel.
- **Plurals in shared insight templates** (Phase 27) — minimal CLDR plural selection so FR 0/1 and `many` are deterministic on both runtimes.
- **`TerminologyPreference` in FR/ES** (Phase 30) — noun substitution breaks articles/agreement; per-term sentence variants (no Android port, so nothing changes there).
- **`localeFilters` vs pseudo-locales** (Phase 28) — keep `en-XA` in debug; verify merged-manifest `LocaleConfig` after the first build.
- **JVM vs device date output** (Phases 29, 31) — JDK CLDR ≠ Android ICU; assert locale selection/structure in JVM tests, not exact strings across runtimes.
- **Native-speaker + legal review** — human-gated release step per language; cannot be automated, tests must not claim it.

## Sources

### Primary (HIGH)
- developer.android.com/guide/topics/resources/app-languages — `generateLocaleConfig`, `resources.properties`, `LocaleManager` (33+), AppCompat back-compat constraints, `autoStoreLocales`
- developer.android.com/jetpack/androidx/releases/appcompat — 1.7.1 vs 1.8.0, `ContextCompat.getContextForLanguage`
- developer.apple.com/documentation/foundation/localizedstringresource/locale — lookup-locale contract
- developer.apple.com/forums/thread/828990 — environment locale doesn't reach non-SwiftUI APIs
- WWDC19 403 (localized experiences) — Apple guidance on per-app language
- Codebase inspection (this worktree): `content/*Copy.kt`, `ForbiddenTerms.kt`, `ClaimsPolicy.kt/.swift`, `ClaimsPolicyDriftTests.kt`, engines, `CachedInsight`, `InsightFingerprint`, `ReminderNotifier`, `ReportPdfRenderer`, `ExportEnvelope`, `MainActivity`, `themes.xml`, `build.gradle.kts`, `project.yml`, `TerminologyPreference.swift`

### Secondary (MEDIUM)
- Android Developers Blog, per-app language part 1 (2022); Zendesk Android SDK app-language notes; WordPress-Android `localeFilters` migration
- Apple forums 721302 (`UIPrefersShowingLanguageSettings`); SPM String Catalog articles (elegantchaos, danielsaidi); `#bundle` pitch / WWDC25 notes
- Clue and Flo help pages (language switching); Endo-App DiGA materials; sante.fr / EndoFrance terminology
- EU MDR 2017/745 Art. 2(12), German HWG/UWG, GDPR Art. 9/12/13 (confirm with counsel)

### Tertiary (LOW)
- Drafted DE/FR/ES forbidden-term lists and patient-terminology glossary seed — native-speaker review required
- Bearable language behaviour — not verified

---
*Research completed: 2026-10-31*
*Ready for roadmap: yes*
