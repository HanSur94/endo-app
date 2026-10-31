# Requirements: Endo-App

**Defined:** 2026-10-31
**Core Value:** Turn everyday self-tracking into understanding — correlations, cycle/trend patterns, flare warnings, and personalized recommendations that help people with endometriosis make sense of their condition.

## v2.1 Requirements — Multilingual

Languages: English (source), German, French, Spanish. Platforms: Android (`android/`) and iOS. Research: [research/SUMMARY.md](research/SUMMARY.md).

### Language selection

- [ ] **LANG-01**: User can pick the app language (System default / English / Deutsch / Français / Español, shown as endonyms) in Settings on Android
- [ ] **LANG-02**: User can pick the app language in Settings on iOS (in-app override; fallback to a Settings deep-link only if the Phase-30 spike fails — then this requirement is re-worded)
- [ ] **LANG-03**: On first launch the app follows the system language (base language, e.g. de-AT → German) and falls back to English for unsupported languages
- [ ] **LANG-04**: Switching language applies immediately without an app restart and without losing an in-progress check-in, day-editor or body-map entry
- [ ] **LANG-05**: The chosen language survives app restarts and device reboots on Android 8–16 and iOS 18+
- [ ] **LANG-06**: On Android 13+ the app appears under system Settings → App languages, and that setting and the in-app picker always show the same choice

### Claims safety & glossary

- [ ] **SAFE-01**: Every German, French and Spanish string the app can show is checked against a curated forbidden list for that language (diagnostic, causal, imperative, alarmist, gendered terms), and the build fails on a hit
- [ ] **SAFE-02**: Each language's check has an allow-list for harmless words that contain a forbidden substring (e.g. "Behandlungsteam", "entraînement"), and seeded bad sentences per language must make the check fail (red-capable)
- [ ] **SAFE-03**: One shared glossary defines the approved term per concept in all 4 languages (incl. register: DE "du", FR "vous", ES "tú"), and both platforms' tests fail when a translation uses a banned variant
- [ ] **SAFE-04**: The existing English claims audit, HonestyGate and drift guards keep passing unchanged

### Insight sentences

- [ ] **INSL-01**: User sees every insight sentence (trends, correlations, recommendations, flare hint, cycle phase) in the chosen language, with correct grammar, gender and plurals
- [ ] **INSL-02**: Insight results are identical in every language — only wording changes, never which insights appear, their numbers, or their confidence
- [ ] **INSL-03**: After switching language, previously computed insights show in the new language without logging new data
- [ ] **INSL-04**: Insight sentences for the same data are word-for-word identical on Android and iOS in each language

### UI translation

- [ ] **TRNS-01**: User sees every screen of the Android app (onboarding, consent, log, check-in, day editor, body map, insights, explore, report, export/import, settings, Health Connect) in the chosen language
- [ ] **TRNS-02**: User sees every screen of the iOS app in the chosen language, including the Apple Health permission rationale texts
- [ ] **TRNS-03**: No screen shows a leftover English string in DE/FR/ES — a missing translation fails the build instead of falling back silently
- [ ] **TRNS-04**: German, French and Spanish text fits every screen (chips, tabs, cards, buttons) at default and largest font size without clipping
- [ ] **TRNS-05**: Reminder notifications (title, text, channel name) appear in the currently chosen language, including after a language change

### Formats & report

- [ ] **FMT-01**: Dates, numbers and decimals display in the chosen language's format, with week start and 12/24-hour time following the device region
- [ ] **FMT-02**: User gets the PDF doctor report in the app language, with the "self-tracked, not diagnostic" notice fully visible in every language
- [ ] **FMT-03**: User can choose a separate report language in the report builder (e.g. app in German, report in English)
- [ ] **FMT-04**: Free text the user typed (notes, medication names) appears verbatim in every language and in the report, never translated

### Data portability

- [ ] **PORT-01**: An encrypted export made in one language imports unchanged on a device set to another language; the export file contains no translated text and stays language- and platform-neutral (iOS has no export/import today)

### Legal & store

- [ ] **LEGL-01**: Consent screen and privacy policy are available in all 4 languages, and the app records which consent text version and language the user accepted
- [ ] **LEGL-02**: Play Store and App Store listings (title, descriptions, keywords, release notes) exist in all 4 languages and pass the same per-language claims check
- [ ] **LEGL-03**: The release checklist requires a named native-speaker review and a legal review per language before release; no test or doc marks this done automatically

## Future Requirements

- Localized store screenshots per language
- Clinician terms in parentheses in the PDF report (e.g. "Schmerzen beim Sex (Dyspareunie)")
- More languages (Italian, Portuguese, Dutch, Polish) and regional variants (de-CH, es-419, fr-CA)
- Right-to-left languages
- Removing the legacy English `headline`/`detail` fields from the insight engines once all surfaces render from messages

## Out of Scope

| Feature | Reason |
|---------|--------|
| Runtime / machine / cloud translation | Breaks the no-INTERNET invariant and bypasses the claims audit |
| Translating user-entered notes | User's own words stay verbatim; translating them would also put them through a network service |
| Language-dependent insight logic | Same data must give the same insights in every language (honesty + parity) |
| Bundle swizzling / private API language hacks on iOS | Fragile, App Review risk |
| Forced language selection on first launch | Friction; system language is the right default |
| Literal translation of the English forbidden list | Produces both misses and false positives; lists are curated per language |

## Open Decisions

- **Binding legal language** for privacy policy and consent (all equal vs German vs English) — needs legal advice; decided in Phase 33.
- **iOS in-app override vs Settings deep-link** — decided by the Phase-30 spike (see LANG-02).

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LANG-01 | Phase 28 | Pending |
| LANG-02 | Phase 30 | Pending |
| LANG-03 | Phase 30 | Pending |
| LANG-04 | Phase 30 | Pending |
| LANG-05 | Phase 30 | Pending |
| LANG-06 | Phase 28 | Pending |
| SAFE-01 | Phase 26 | Android done (26-02), iOS pending (26-04/05) |
| SAFE-02 | Phase 26 | Android done (26-02), iOS pending (26-04/05) |
| SAFE-03 | Phase 26 | Android done (26-03), iOS pending (26-04/05) |
| SAFE-04 | Phase 26 | Android done (26-02), iOS pending (26-04/05) |
| INSL-01 | Phase 32 | Pending |
| INSL-02 | Phase 27 | Pending |
| INSL-03 | Phase 27 | Pending |
| INSL-04 | Phase 32 | Pending |
| TRNS-01 | Phase 29 | Pending |
| TRNS-02 | Phase 30 | Pending |
| TRNS-03 | Phase 32 | Pending |
| TRNS-04 | Phase 32 | Pending |
| TRNS-05 | Phase 30 | Pending |
| FMT-01 | Phase 31 | Pending |
| FMT-02 | Phase 31 | Pending |
| FMT-03 | Phase 31 | Pending |
| FMT-04 | Phase 31 | Pending |
| PORT-01 | Phase 29 | Pending |
| LEGL-01 | Phase 33 | Pending |
| LEGL-02 | Phase 33 | Pending |
| LEGL-03 | Phase 33 | Pending |

**Coverage:**
- v2.1 requirements: 27 total
- Mapped to phases: 27 ✓
- Unmapped: 0

**Traceability notes:**
- **Cross-platform ownership:** LANG-03, LANG-04, LANG-05 and TRNS-05 cover both platforms. Phase 28 builds the Android half and Phase 30 (owner) builds the iOS half. They close on Phase 30 completion, which must confirm the Android half already passed Phase 28 verification. If Phase 30 ever completes before Phase 28 verifies, re-open them.
- **PORT-01:** re-worded 2026-10-31 — iOS has no encrypted export/import, so the requirement covers Android cross-language import plus format neutrality (checked against the committed golden fixture). An iOS importer is out of scope.
- **LANG-02 / LANG-04:** if the Phase 30 spike adopts the Settings deep-link fallback, LANG-02 and the iOS clause of LANG-04 are re-worded in Phase 30.

---
*Requirements defined: 2026-10-31*
*Last updated: 2026-10-31 after v2.1 roadmap creation (traceability filled, 27/27 mapped)*
