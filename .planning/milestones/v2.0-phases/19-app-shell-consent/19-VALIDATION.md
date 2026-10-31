---
phase: 19
slug: app-shell-consent
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Robolectric + Compose UI test (`createComposeRule`) on the JVM for the gate/shell invariants; JUnit 4 for manifest/copy scans; instrumented `createAndroidComposeRule` on the main-only CI emulator lane |
| **Config file** | `android/app/build.gradle.kts` (Compose BOM + activity-compose + material3 + robolectric + compose-ui-test wiring); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~2–4 min (Robolectric warms slowly first run) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` still 176; CopyAudit green over the newly-registered UI copy
- **Max feedback latency:** ~240 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 19-01-xx | 01 | 1 | ASHL-02 | — | Bloom tokens/typography/components render; BloomTheme applied | unit | `./gradlew :app:test* --tests *BloomTheme* *DesignSystem*` | ❌ W0 | ⬜ pending |
| 19-02-xx | 02 | 2 | ASHL-01 | T-19-01 | health/tab surfaces unreachable pre-consent; withdraw re-gates; deep link lands on gate | unit(robolectric+compose) | `./gradlew :app:test* --tests *AppGate* *ConsentGate*` | ❌ W0 | ⬜ pending |
| 19-03-xx | 03 | 3 | ASHL-03 | — | 3-tab NavigationBar shell; edge-to-edge insets; predictive back; tab switch | unit(compose) | `./gradlew :app:test* --tests *Shell* *TabBar*` | ❌ W0 | ⬜ pending |
| 19-04-xx | 04 | 3 | ASHL-01, ASHL-02 | — | onboarding + consent screens; explicit affirmative accept; decline path; Bloom-styled | unit(compose) | `./gradlew :app:test* --tests *Onboarding* *Consent*` | ❌ W0 | ⬜ pending |
| 19-05-xx | 05 | 4 | ASHL-04, ASHL-01 | T-19-02 | no INTERNET permission in merged manifest; new UI copy registered + passes ClaimsPolicy substring gate | unit | `./gradlew :app:test* --tests *ManifestPermissions* *CopyAudit*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] Compose BOM 2026.06.00 + activity-compose + material3 + Robolectric + compose-ui-test-junit4 added to the catalog and `:app` (version-catalog audit stays green — these are first-party androidx)
- [ ] `MainActivity` (single activity, `enableEdgeToEdge`, `setContent { BloomTheme { AppGate() } }`) + a Robolectric/Compose test harness
- [ ] `BloomThemeTest`, `AppGateConsentTest`, `ManifestPermissionsTest`, `CopyAuditTest`-extension scaffolds (red until sources land)
- [ ] Register the phase's ~16 curated UI strings in the Phase-18 CopyAuditRegistry (each dodges ClaimsPolicy forbidden SUBSTRINGS — `her`⊂`here`/`there`, `use`, `start`, etc.)

*The consent-by-construction invariant (no health/tab composable constructible pre-consent) is a Wave-0-visible acceptance in AppGateConsentTest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Bloom visual fidelity vs iOS (color/typography/tab-capsule feel) | ASHL-02 | Pixel/aesthetic judgement not automatable | Compare a screenshot to the iOS Bloom shell on a device/emulator |
| Predictive-back animation + edge-to-edge on a real device | ASHL-03 | Gesture/inset behavior is OS-mediated | Run on an Android 14+ emulator; confirm no content under system bars, predictive-back preview shows |
| TalkBack sweep of onboarding/consent/tabs | ASHL-01 | Screen-reader UX is experiential | Enable TalkBack on-device; sweep the gate + shell |

*Gate logic, manifest permissions, copy audit, and token application have automated JVM verification; only pixel-fidelity + on-device gesture/TalkBack feel are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Compose deps, MainActivity, test scaffolds, copy registration)
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
