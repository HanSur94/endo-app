---
phase: "26"
slug: "shared-l10n-foundation"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-31"
---

# Phase 26 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Derived from 26-RESEARCH.md § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android `:insights`: JUnit Jupiter 6.1.1 · Android `:app`: JUnit 4.13.2 · iOS: Swift Testing in `EndoAppTests` |
| **Config file** | `android/insights/build.gradle.kts`, `android/app/build.gradle.kts`, `project.yml` (iOS branch) |
| **Quick run command** | Android: `cd android && ./gradlew --offline :insights:test --tests 'com.endoapp.insights.l10n.*' :app:testDebugUnitTest --tests 'com.endoapp.app.l10n.*'` · iOS: `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/LocalizedPolicySelfTests -only-testing:EndoAppTests/GlossaryConformanceTests -only-testing:EndoAppTests/L10nAuditTests -only-testing:EndoAppTests/BundledPolicyParityTests -only-testing:EndoAppTests/LocalizedPolicySchemaTests` |
| **Full suite command** | Android: `cd android && ./gradlew --offline :insights:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` · iOS: `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | quick ~40–90 s (Android) · full ~4–6 min (Android), ~8–12 min (iOS) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command for the platform the task touched
- **After every plan wave:** Run the full suite for that platform
- **Before `/gsd-verify-work`:** Both full suites green, SAFE-04 diff guard empty, cross-branch `l10n/` diff empty
- **Max feedback latency:** 90 seconds (quick run)

---

## Per-Task Verification Map

Task IDs are filled by the planner; requirement → test mapping is fixed here.

| Requirement | Behavior | Threat Ref | Test Type | Automated Command | File Exists | Status |
|-------------|----------|------------|-----------|-------------------|-------------|--------|
| SAFE-01 | Every `mustFail` sentence (de/fr/es) yields exactly the expected `{category, term}` | T-26 policy-bypass | unit (both) | `./gradlew :insights:test --tests '*LocalizedPolicySelfTests*'` / `-only-testing:EndoAppTests/LocalizedPolicySelfTests` | ❌ W0 | ⬜ pending |
| SAFE-01 | All 5 categories non-empty per language; `mustFail` covers each category | T-26 silent-empty | unit (both) | `*LocalizedPolicySchemaTests*` | ❌ W0 | ⬜ pending |
| SAFE-01 / SC4 | Every registered DE/FR/ES string passes; synthetic bad source makes the audit fail | T-26 unscanned-source | unit (both) | `./gradlew :app:testDebugUnitTest --tests '*L10nAuditTest*'` / `L10nAuditTests` | ❌ W0 | ⬜ pending |
| SC4 | Unknown localized file anywhere in repo → fail; seeded temp-dir case proves it | T-26 unscanned-source | unit (both) | `*L10nTripwireTest*` (Android) / inside `L10nAuditTests` (iOS) | ❌ W0 | ⬜ pending |
| SAFE-02 | `mustPass` host words (Behandlungsteam, entraînement, vorher, Pause, …) pass; NFD/ẞ/NBSP/’ fixtures fold as expected | — | unit (both) | `*LocalizedPolicySelfTests*` | ❌ W0 | ⬜ pending |
| SAFE-02 | Allow hygiene: not a rule term; `collidesWith` really collides; reason non-empty; terms pre-folded | T-26 allow-smuggle | unit (both) | `*LocalizedPolicySchemaTests*` | ❌ W0 | ⬜ pending |
| SAFE-03 | Glossary: 4 languages per concept; approved passes policy + register; approved ∩ banned = ∅ | — | unit (both) | `./gradlew :app:testDebugUnitTest --tests '*GlossaryConformanceTest*'` / `GlossaryConformanceTests` | ❌ W0 | ⬜ pending |
| SAFE-03 | Register self-test: DE "Sie", FR "tu", ES "usted" produce the expected failure | — | unit (both) | same | ❌ W0 | ⬜ pending |
| SAFE-01/02 | Bundled/classpath policy bytes == repo `l10n/` bytes | T-26 stale-copy | unit (both) | `*BundledPolicyParityTests*` | ❌ W0 | ⬜ pending |
| SAFE-04 | English suites unchanged and green | — | regression (both) | Android + iOS full suites; `git diff --exit-code <base> -- <EN file list>` | ✅ existing | ⬜ pending |
| SAFE-04 | `ClaimsPolicy.forLanguage(x)` is the same instance as `ForbiddenTerms.forLanguage(x)` | — | unit (Android) | `*ClaimsPolicyLocalizedReuseTest*` | ❌ W0 | ⬜ pending |
| Cross-branch | `l10n/` byte-identical on Android and iOS branches | T-26 branch-divergence | scripted gate | `git diff --quiet claude/endo-android-version-e608a7 <ios-branch> -- l10n && echo IDENTICAL` | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `l10n/claims-policy.{de,fr,es}.json`, `l10n/glossary.json`, `l10n/README.md` — data + schema doc
- [ ] `android/insights/build.gradle.kts` — `Sync` resources task, `inputs.dir`, `endo.repoRoot` system property
- [ ] `android/app/build.gradle.kts` — `testOptions.unitTests.all { systemProperty; inputs.dir }`
- [ ] `.github/workflows/android.yml` — add `'l10n/**'` to push + pull_request paths
- [ ] `project.yml` (iOS branch) — `l10n` resources source entry, then `xcodegen generate`
- [ ] All test files in the map above (new; no existing test edited)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Red-proof per platform | SAFE-01 | Proves the audit is wired, not vacuous, end-to-end | Temporarily add "erkennt Schübe" to a glossary DE approved form → run audit → must be red → revert |
| Native-speaker / legal accuracy of DE/FR/ES lists | SAFE-01..03 | Content quality cannot be automated | Deferred to Phase 33 (LEGL-03); `reviewStatus` stays `draft-unreviewed` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
