# Roadmap: Endo-App

## Overview

v1.0 (iOS) built the foundations, manual logging, private CloudKit sync, the Foundation-only insights engine, the live dashboard, HealthKit import, the doctor-ready PDF, recommendations and the flare early-warning; v1.1 made the charts interactive and added Explore; v2.0 ported the whole app to native Android (Kotlin + Compose + Room + Health Connect, no `INTERNET` permission). **v2.1 makes the app read in English, German, French and Spanish on both Android and iOS, chosen inside the app.** Research reframed this as a *safety-system migration that happens to involve translation*: neither platform has a string catalog today, both insight engines build English sentences by concatenation and cache the rendered text, and the claims audit only understands English. The order therefore follows hard dependencies — per-language claims policy and glossary first, then a language-neutral insight-message contract, then per-platform locale plumbing and catalog migration (Android and iOS in parallel), then locale-aware formats and the localized PDF, then the DE/FR/ES translations behind live gates, and finally localized legal/store copy plus a human review gate. Translations ship inside the app (no network, no translation service), insight logic never depends on language, and native-speaker + legal review stays a human-gated release step that no test marks done.

## Milestones

- ✅ **v1.0 iOS MVP** — Phases 1–12 (39 plans), feature-complete 2026-06-10 — [roadmap](milestones/v1.0-ROADMAP.md) · [audit](milestones/v1.0-MILESTONE-AUDIT.md) · [requirements](milestones/v1.0-REQUIREMENTS.md)
- ✅ **v1.1 Interactive Charts & Explore** — Phases 13–16 (17 plans), feature-complete 2026-06-17 — [roadmap](milestones/v1.1-ROADMAP.md) · [audit](v1.1-MILESTONE-AUDIT.md) · [requirements](milestones/v1.1-REQUIREMENTS.md)
- ✅ **v2.0 Android Version (Port)** — Phases 17–25 (42 plans), feature-complete 2026-07-10 — [roadmap](milestones/v2.0-ROADMAP.md) · [audit](v2.0-MILESTONE-AUDIT.md) · [requirements](milestones/v2.0-REQUIREMENTS.md)
- 🚧 **v2.1 Multilingual** — Phases 26–33 (in progress) — [requirements](REQUIREMENTS.md) · [research](research/SUMMARY.md)

See [MILESTONES.md](MILESTONES.md) for shipped-version history. Release checklists carried forward unchanged (device/Console/human-gated): the iOS pre-release checklists in the archived v1.0/v1.1 roadmaps, and the Android Play checklist in `docs/release/RELEASE-CHECKLIST.md`. Phase 33 adds the per-language review items to these, it does not replace them.

## Phases

**Phase Numbering:** continuous across milestones — v2.0 ended at Phase 25, so v2.1 spans **Phases 26–33**. Decimal phases (e.g. 28.1) remain available for urgent insertions.

**Execution order:** 26 → 27 → (28 → 29 ∥ 30) → 31 → 32 → 33. Translation (32) waits for both catalog migrations (29, 30) and the formatting/PDF work (31), so every string is translated once, into its final key.

**Platforms & branches:**
- **Android** work lands on `claude/endo-android-version-e608a7` (this worktree).
- **iOS** work starts from `main` on its own branch — it does not ride on the Android branch.
- **Both-platform phases (26, 27, 31, 32, 33)** land in two halves, one per line. The shared `l10n/` files (repo root) must be byte-identical on both lines, and the Swift halves of Phases 26 and 27 must be on the iOS line before Phase 30 starts. The Android↔iOS catalog key-parity test stays advisory until both trees coexist; until then each platform runs the glossary and claims tests against the shared `l10n/` files.

**Pre-translation verification:** until Phase 32 adds real DE/FR/ES text, Phases 28–31 verify non-English behaviour with pseudo-locales (Android `en-XA`, the iOS double-length pseudolanguage) and seeded test strings; Phase 32 re-runs those checks with the real translations.

- [ ] **Phase 26: Shared l10n Foundation** - One shared glossary and per-language (DE/FR/ES) claims policies, enforced on both platforms before any translation exists
- [ ] **Phase 27: Language-Neutral Insight Messages** - Both engines emit structured messages beside the unchanged English text; the insight cache becomes language-neutral
- [ ] **Phase 28: Android Language Switching** - In-app picker + Android 13+ App languages, live switching, persistence on Android 8–16, reminders in the chosen language
- [ ] **Phase 29: Android String Catalog** - Every Android string moves into `strings.xml` + plurals; the catalog becomes the audit registry; exports stay language-neutral
- [ ] **Phase 30: iOS Language Switching & String Catalog** - Override spike, in-app picker, String Catalogs, reminders rescheduled in the chosen language (parallel to 28–29)
- [ ] **Phase 31: Locale-Aware Formats & Localized PDF** - Dates/numbers in the chosen language with regional conventions; PDF report in the app language or a separate report language
- [ ] **Phase 32: German, French & Spanish Translations** - DE/FR/ES for all UI and insight sentences behind completeness, claims, glossary and cross-platform gates
- [ ] **Phase 33: Legal, Store & Release Gate** - Localized consent, privacy policy and store listings; per-language sweeps; named native-speaker + legal sign-off

## Phase Details

### Phase 26: Shared l10n Foundation
**Goal**: German, French and Spanish copy has a curated safety net before any translated string exists — one shared glossary plus a per-language forbidden-term policy that both platforms enforce, with the English audit left exactly as it is
**Platform**: Both — `l10n/` (repo root) + Android `ForbiddenTerms`/`ClaimsPolicy.forLanguage` on `claude/endo-android-version-e608a7`; the identical `l10n/` files + Swift `ClaimsPolicy.forLanguage` on the iOS branch from `main`
**Depends on**: Nothing (first v2.1 phase; extends the shipped English `ClaimsPolicy` / `ForbiddenTerms` / `HonestyGate` on both platforms)
**Requirements**: SAFE-01, SAFE-02, SAFE-03, SAFE-04
**Research**: needed — content research for the DE/FR/ES forbidden lists (diagnostic, causal, imperative, alarmist, gendered; German MDR/HWG framing) and host-word allow-lists; drafted lists stay low-confidence until the Phase 33 native review
**Success Criteria** (what must be TRUE):
  1. On both Android and iOS, a German, French or Spanish string containing a forbidden term for its language — diagnostic ("erkennt Schübe"), causal ("verursacht", "provoque"), imperative, alarmist or gendered — fails the build, proven by seeded bad sentences per language that turn the check red
  2. Harmless words that merely contain a forbidden substring ("Behandlungsteam", "entraînement", "vorher", "Pause") pass through each language's allow-list, and matching is Unicode-normalized (NFC) and case-folded per language so accents and capitals can neither dodge nor trip the check
  3. `l10n/glossary.json` names the approved term for every core concept in all four languages with a fixed register (DE "du", FR "vous", ES "tú"), and a test on each platform fails when a string uses a banned variant, including the wrong register
  4. The per-language check is wired to every DE/FR/ES string source the app will ship (the shared `l10n/` files now; UI catalogs and store copy register as they appear), so no later phase can add translated text that bypasses it
  5. The English claims audit, `HonestyGate`, and the enum/ClaimsPolicy drift guards pass with zero edits to the English lists or their tests
**Plans**: TBD

### Phase 27: Language-Neutral Insight Messages
**Goal**: Both insight engines describe every insight as a language-neutral message (template key + typed values) next to the unchanged English text, so any language can render it while the insights themselves never depend on language
**Platform**: Both — Kotlin `:insights` + the Android insight cache on `claude/endo-android-version-e608a7`; Swift `EndoInsights` + the iOS `CachedInsight` on the iOS branch from `main` (the Swift half must land there before Phase 30 starts)
**Depends on**: Phase 26 (`l10n/` layout; `l10n/insight-messages.json` is audited by the per-language policy)
**Requirements**: INSL-02, INSL-03
**Research**: needed — grammar-safe templates for German case and French/Spanish agreement (label-as-quoted-noun / `.inline` label variants vs ICU `select`), CLDR one/other plural selection that behaves identically on the JVM and Apple runtimes, and keeping 157-case parity on both engines through an additive refactor
**Success Criteria** (what must be TRUE):
  1. Every insight the engines can emit (trends, cycle phase, correlations, recommendations, flare, "not enough data yet" notices) carries a structured message, and rendering it in English reproduces today's headline and detail byte-for-byte on all 157 parity fixtures on both platforms — with the parity suite and `HonestyGate` expectations unchanged
  2. The engines take no language input: for the same data, which insights appear, their numbers and their confidence are identical whichever language renders them — a test renders one result set in English and in a test language and finds that only the wording differs
  3. The insight cache on both platforms stores the language-neutral message, never rendered text (fingerprint version bumped so pre-v2.1 caches recompute once), and a cached insight re-renders in another language with zero recompute and no new data
  4. `l10n/insight-messages.json` holds the English templates with named placeholders, plural selection follows CLDR rules on both runtimes (French 0 and 1 → "one"), and the English insight goldens are byte-identical between Android and iOS
**Plans**: TBD

### Phase 28: Android Language Switching
**Goal**: An Android user picks the app language in Settings or in Android 13+ system App languages, and the app — insight cards and reminders included — switches at once and remembers the choice on every supported Android version, proven before the mass string migration
**Platform**: Android only — `claude/endo-android-version-e608a7`
**Depends on**: Phase 27 (insight cards render from the structured message)
**Parallel with**: Phase 30 (iOS)
**Requirements**: LANG-01, LANG-06
**Cross-platform**: also builds the Android halves of LANG-03, LANG-04, LANG-05 and TRNS-05 (success criteria 1–4 below). Those four are owned by Phase 30 and close there once both platforms verify — Phase 28's verification is the Android evidence
**Research**: not needed (official per-app language guide). Exit gate: the app-owned locale controller must pass API 30 cold-start and recreation checks; if it fails, adopt the decided fallback (AppCompat 1.7.1) and re-run the manifest permission test
**Success Criteria** (what must be TRUE):
  1. User opens Settings → Language and picks System default, English, Deutsch, Français or Español (each shown as its own endonym); the app re-renders in that locale at once, with no restart, and an in-progress check-in draft, day-editor entry or body-map selection is still there (the export passphrase field is deliberately cleared, never saved)
  2. On first launch the app follows the system language by base language (de-AT → German) and falls back to English for any unsupported system language
  3. The choice survives app restart, process death and device reboot on an API 30 emulator (in-app controller path) and an API 34+ emulator (`LocaleManager` path); on Android 13+ the app appears under system Settings → App languages, and that setting and the in-app picker always show the same choice
  4. The reminder notification's title, text and channel name appear in the currently chosen language, including the first reminder after a language change
  5. With English selected the app looks and behaves exactly as before, and the debug pseudo-locale plus the localization lint checks (missing translation, format mismatch) run as build errors
**Plans**: TBD
**UI hint**: yes

### Phase 29: Android String Catalog
**Goal**: Every word on every Android screen comes from the platform string catalog, so the claims audit sees all copy by construction and a missing or broken translation cannot slip through — while the English app stays identical
**Platform**: Android only — `claude/endo-android-version-e608a7`
**Depends on**: Phase 28
**Parallel with**: Phase 30 (iOS)
**Requirements**: TRNS-01, PORT-01
**Research**: not needed (`strings.xml` + `<plurals>` migration, lint)
**Success Criteria** (what must be TRUE):
  1. With the debug pseudo-locale selected, every Android screen — onboarding, consent, log, check-in, day editor, body map, insights, explore, report, export/import, settings, Health Connect — shows only pseudo-localized text, and a source-scan test fails on any new hard-coded UI string
  2. The copy audit reads the string catalogs directly for every locale, so a string outside the catalog cannot reach the screen, and a seeded missing key or placeholder mismatch in a locale fails the build
  3. Counts use catalog plural rules (no "1 days"), sentences are never assembled from fragments, and UI tests locate elements by tag or resource instead of English text, so they pass in any language
  4. An encrypted export made with the app in one language imports unchanged with the app in another, and the export contains only language-neutral values (enum raws, keys, ISO dates) — checked against the committed platform-neutral golden fixture, so an importer on the other platform would see no translated text
**Plans**: TBD
**UI hint**: yes

### Phase 30: iOS Language Switching & String Catalog
**Goal**: An iOS user picks the app language inside the app (or, if the spike fails, through a one-tap iOS Settings link) and every screen, insight card and reminder follows it, with all iOS copy moved into String Catalogs that the per-language audit reads
**Platform**: iOS only — a branch from `main` (needs the Swift halves of Phases 26 and 27 on that line)
**Depends on**: Phase 27 (Swift message contract on the iOS line)
**Parallel with**: Phases 28–29 (Android)
**Requirements**: LANG-02, LANG-03, LANG-04, LANG-05, TRNS-02, TRNS-05
**Cross-platform**: LANG-03, LANG-04, LANG-05 and TRNS-05 cover both platforms. Phase 30 builds the iOS half and closes them; its verification must confirm the Android half already passed in Phase 28's verification
**Research**: needed — 1-day spike first: `LocalizedStringResource.locale` vs an explicit `.lproj` bundle with compiled `.xcstrings`; whether to mirror the choice to `AppleLanguages` (default: do not); XcodeGen 2.45.4 + xcstrings / `knownRegions`
**Success Criteria** (what must be TRUE):
  1. The spike result is recorded before any string moves: either the in-app override passes (DE/FR/ES resolve from compiled catalogs under an English system language, date pickers and chart axes follow the chosen locale, a switch keeps a check-in draft), or the Settings deep-link fallback is adopted and LANG-02 (plus the iOS clause of LANG-04) is re-worded
  2. User picks System default, English, Deutsch, Français or Español in Settings and the app re-renders at once without losing an in-progress check-in or day-editor entry; the choice survives relaunch and reboot, first launch follows the system base language with English fallback, and pending reminders are rescheduled in the new language
  3. With the double-length pseudolanguage, every iOS screen — including the Apple Health permission rationale texts — shows only catalog-sourced text; no inline `Text("…")` literal or English `static let` constant reaches the UI (a literal-scan test fails on new ones), and the custom bleeding-term preference uses whole-sentence variants instead of splicing a noun into a sentence
  4. The copy audit parses `Localizable.xcstrings` and `InfoPlist.xcstrings` per language, CI fails on catalog entries left `new`/`stale` or missing a translation, and UI tests run with `-AppLanguage` and accessibility identifiers rather than English text
  5. Insight cards render from the structured message and re-render already-computed insights after a language switch without new data; with English selected the iOS app looks exactly as before
**Plans**: TBD
**UI hint**: yes

### Phase 31: Locale-Aware Formats & Localized PDF
**Goal**: Dates, numbers and the doctor report read naturally in the user's language while respecting their region, and the report can be produced in a different language than the app without ever clipping the not-diagnostic notice
**Platform**: Both — Android branch and iOS branch (the two halves can run in parallel)
**Depends on**: Phase 29 (Android catalogs), Phase 30 (iOS plumbing + catalogs)
**Requirements**: FMT-01, FMT-02, FMT-03, FMT-04
**Research**: not needed (platform formatters; PDF pagination is code-local)
**Success Criteria** (what must be TRUE):
  1. Dates, numbers and decimals on every screen use the chosen language's format (e.g. "3,5" and "12. März" in German), while week start and 12/24-hour time follow the device region and setting — a German resident who picks English still gets Monday-first weeks and 24-hour time — and insight math and day bucketing never change with the setting
  2. User gets the PDF doctor report in the app language on both platforms, and the "self-tracked, not diagnostic" notice is fully visible in every language — proven by a worst-case longest-language report test with measured-height pagination and a reserved disclaimer block
  3. User picks a separate report language in the report builder (e.g. app in German, report in English), and the whole report — headings, insight sentences, dates and numbers — follows that choice
  4. Notes and medication names the user typed appear verbatim in every app language and in the report — never translated or case-changed
  5. A source-scan test bans fixed US-locale or fixed-pattern display formatting outside export serialization and tests, while exported data keeps its locale-independent ISO formats
**Plans**: TBD
**UI hint**: yes

### Phase 32: German, French & Spanish Translations
**Goal**: A user who picks Deutsch, Français or Español sees every screen and every insight sentence in that language — complete, claims-safe, glossary-consistent, identical across platforms, and fitting on screen
**Platform**: Both — Android `values-{de,fr,es}` on the Android branch, iOS catalog translations on the iOS branch, shared `l10n/insight-messages.json` on both
**Depends on**: Phase 31 (every UI, notification and report string exists under its final key on both platforms)
**Requirements**: INSL-01, INSL-04, TRNS-03, TRNS-04
**Research**: not flagged — drafts follow the Phase 26 glossary and policy with per-string context; native review is the Phase 33 gate
**Success Criteria** (what must be TRUE):
  1. User switches to Deutsch, Français or Español and every screen on both platforms shows that language; a missing key, a placeholder mismatch or an untranslated entry in any of the three fails the build instead of falling back to English
  2. Every insight sentence (trends, correlations, recommendations, flare hint, cycle phase) reads in the chosen language with correct grammar, gender and plurals, and every template × label combination passes that language's claims scan and the glossary check
  3. For the same fixture data, the German, French and Spanish insight goldens are byte-identical between Android and iOS
  4. German, French and Spanish text fits every chip, tab, card and button at default and largest font size without clipping, checked by a screenshot pass on both platforms
  5. Back-translation spot checks of the highest-risk strings (insight sentences, disclaimers, consent, flare hint) are recorded, and every translation stays marked as an unreviewed draft until the Phase 33 sign-off
**Plans**: TBD
**UI hint**: yes

### Phase 33: Legal, Store & Release Gate
**Goal**: Consent, privacy policy and store listings exist in all four languages under the same claims checks, every language is walked end-to-end on both platforms, and release stays blocked until named humans have reviewed each language
**Platform**: Both + docs — Android branch, iOS branch, store metadata, `docs/release/`
**Depends on**: Phase 32
**Requirements**: LEGL-01, LEGL-02, LEGL-03
**Research**: needed — the binding legal language for consent and privacy policy (all equal vs German vs English) needs counsel; German MDR/HWG and per-market GDPR framing for store copy
**Success Criteria** (what must be TRUE):
  1. User reads the consent screen and privacy policy in any of the four languages, and the stored consent record shows which consent-text version and language they accepted (both platforms); a later language change does not rewrite that record
  2. Play Store and App Store listings — title, descriptions, keywords, release notes — exist in all four languages and pass the same per-language claims check as the app copy, with the not-medical-advice framing in the first paragraph
  3. A per-language smoke sweep on both platforms switches the language and walks every tab, the next reminder and the notification channel name, and finds no mixed-language output
  4. The release checklist requires a named native-speaker reviewer and a legal review per language, records the binding-language decision, and no test or document marks either review done automatically
**Plans**: TBD
**UI hint**: yes

## Coverage Notes

- **Cross-platform requirements split across the parallel phases.** LANG-03, LANG-04, LANG-05 and TRNS-05 apply to both platforms; their Android half is built in Phase 28 and their iOS half in Phase 30, which run in parallel. Each is **owned by Phase 30** for traceability (completing a phase auto-closes the REQ-IDs on its Requirements line). Phase 30 was chosen because (a) it carries the iOS plumbing *and* the full iOS catalog migration, so it is expected to finish after Phase 28 and closing there means both platforms are done, and (b) its spike may re-word the iOS clause of LANG-04, so the owning phase is the one that can change it. Phase 28 lists them under **Cross-platform** and carries the Android half as success criteria. If Phase 30 ever completes before Phase 28 verifies, re-open these four until it does.
- **PORT-01 "on the other platform."** iOS has no encrypted export/import today (no exporter/importer in `Sources/` or `EndoInsights/`), and an iOS↔Android handoff is out of scope. Phase 29 covers the clause by proving the export carries no translated text and stays platform- and language-neutral against the committed golden fixture. Building an iOS importer is not in this milestone. PORT-01 was re-worded accordingly (2026-10-31).
- **Spike-dependent wording.** If the Phase 30 spike adopts the Settings deep-link fallback, LANG-02 and the iOS clause of LANG-04 ("applies immediately without an app restart") are re-worded in Phase 30 — iOS relaunches the app when its language changes in system Settings.
- **French register** is fixed to "vous" by SAFE-03 (research left it open); Phase 26 applies it rather than re-deciding it.

## Progress

**Execution Order:** 26 → 27 → (28 → 29 ∥ 30) → 31 → 32 → 33. Phases 28–29 (Android branch) and Phase 30 (iOS branch) run in parallel. Within the both-platform phases (31, 32), the Android and iOS halves can also run in parallel.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–12 (archived) | v1.0 | 39/39 | Complete | 2026-06-10 |
| 13–16 (archived) | v1.1 | 17/17 | Complete | 2026-06-17 |
| 17–25 (archived) | v2.0 | 42/42 | Complete | 2026-07-10 |
| 26. Shared l10n Foundation | v2.1 | 0/TBD | Not started | - |
| 27. Language-Neutral Insight Messages | v2.1 | 0/TBD | Not started | - |
| 28. Android Language Switching | v2.1 | 0/TBD | Not started | - |
| 29. Android String Catalog | v2.1 | 0/TBD | Not started | - |
| 30. iOS Language Switching & String Catalog | v2.1 | 0/TBD | Not started | - |
| 31. Locale-Aware Formats & Localized PDF | v2.1 | 0/TBD | Not started | - |
| 32. German, French & Spanish Translations | v2.1 | 0/TBD | Not started | - |
| 33. Legal, Store & Release Gate | v2.1 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-10-31 — v2.1 Multilingual, Phases 26–33, 27/27 requirements mapped*
