---
phase: 19-app-shell-consent
plan: 05
subsystem: security
tags: [android, manifest, permissions, no-network, health-write, data-safety, gdpr, dom-parse, exit-gate]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 04
    provides: "Bloom onboarding/consent flow + registered copy + exempt-scanned legal constants; :app 97/97 baseline"
  - phase: 18-room-persistence
    plan: 06
    provides: "BackupRulesTest — the file-walk + DocumentBuilderFactory XML-parse pattern reused here"
  - phase: 18-room-persistence
    plan: nn
    provides: "VersionCatalogAuditTests — the scan-a-different-file precedent that lets forbidden tokens be named safely in the test source"
provides:
  - "ManifestPermissionsTest — the no-network / no-health-write manifest anchor (ASHL-04, T-19-02): DOM-parses <uses-permission> android:name attributes and asserts NO android.permission.INTERNET and NO android.permission.health.WRITE_* permission; comment-safe (element-attribute parse, never raw text) and red-capable (proven by inject-and-revert)"
  - "Phase 19 exit-gate verification — :insights 176/176, :app 99/99, assembleDebug (12.9 MB APK), lintDebug 0 errors, all ASHL-01..04 green together as one system"
affects: [20-tracking, 21-health-connect]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Manifest permission audit via DOM element-attribute inspection (DocumentBuilderFactory, isNamespaceAware=false, getAttribute(\"android:name\")) — comment-safe by construction: XML comments carry no attributes, so an explanatory comment naming INTERNET/WRITE_* can never false-trip the assertion (unlike a raw text grep)"
    - "Deterministic source-manifest guard + assumption-guarded merged-manifest transitive check: always asserts the committed source manifest (present on a clean checkout); if build/intermediates/**/merged_manifest holds a built manifest, that stronger transitive surface is ALSO asserted, but its absence is Assume.assumeTrue-skipped (never fails a clean checkout)"
    - "Forbidden permission names kept as literals in the TEST source (the VersionCatalogAuditTests precedent) — safe because the scan targets the manifest FILE, not the test's own source, so there is no self-invalidation trap"
    - "Red-capability of a guard test on an already-holding invariant is proven by inject-and-revert (temporary <uses-permission android:name=\"android.permission.INTERNET\"/> -> RED at the INTERNET assertion -> reverted -> GREEN, manifest diff empty)"

key-files:
  created:
    - android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt
  modified: []

key-decisions:
  - "Health WRITE is guarded via the Health Connect write-permission prefix `android.permission.health.WRITE_` (all HC writes, e.g. WRITE_HEART_RATE, live under it) rather than an enumerated allow/deny list — a forward-proof pattern that catches ANY health write a future dependency might add, matching the read-only posture (Health Connect READ arrives Phase 21)"
  - "Two test methods: a deterministic source-manifest assertion (the clean-checkout guard) and an assumption-guarded merged-manifest assertion (the stronger transitive check when a build has run) — both ran GREEN here because Plan-04's assembleDebug had already produced a merged debug manifest, so 2/2 executed with 0 skipped"
  - "Task 2 (phase exit gate) required NO production code change — the full suite was already green with the new test in place, so it is a verification-only gate with no commit of its own"

patterns-established:
  - "Comment-safe manifest permission audit: parse elements, inspect android:name attributes; never grep raw manifest text"
  - "Inject-and-revert as the documented red-capability proof for guard tests whose invariant already holds on the committed tree"

requirements-completed: [ASHL-04, ASHL-01]

coverage:
  - id: M1
    description: "The :app manifest declares NO android.permission.INTERNET — the no-network Data-safety anchor (ASHL-04, T-19-02), proven comment-safe (DOM element-attribute parse) and red-capable"
    requirement: "ASHL-04"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt (2 tests: source + merged)"
        status: pass
    human_judgment: false
  - id: M2
    description: "The :app manifest declares NO health WRITE permission (no android.permission.health.WRITE_* ) — the read-only-health posture anchor"
    requirement: "ASHL-04"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt (health-write prefix assertion, both methods)"
        status: pass
    human_judgment: false
  - id: M3
    description: "Consent-by-construction gate invariant (ASHL-01) still green alongside the manifest posture — health/tab surfaces unreachable pre-consent, withdraw re-gates, deep-link lands on gate"
    requirement: "ASHL-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt (5 tests, JVM Robolectric lane)"
        status: pass
    human_judgment: false
  - id: M4
    description: "Phase exit gate green as one system: :insights (no regression), :app unit, assembleDebug, lintDebug — ASHL-01..04 hold together"
    requirement: "ASHL-01, ASHL-04"
    verification:
      - kind: build
        ref: "cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug"
        status: pass
    human_judgment: false
  - id: M5
    description: "Device-gated remainder: Bloom pixel fidelity vs iOS (ASHL-02), predictive-back animation + edge-to-edge on a real Android 14+ device (ASHL-03), TalkBack sweep of onboarding/consent/tabs (ASHL-01)"
    verification: []
    human_judgment: true
    rationale: "Pixel/aesthetic parity, OS-mediated gesture/inset behavior, and screen-reader UX are experiential — not provable on the Robolectric JVM lane (per 19-VALIDATION Manual-Only table)."

# Metrics
duration: ~5min
completed: 2026-07-09
status: complete
---

# Phase 19 Plan 05: ManifestPermissionsTest + Phase Exit Gate Summary

**Landed `ManifestPermissionsTest` — the Data-safety form's strongest artifact (ASHL-04): a red-capable, comment-safe JVM test that DOM-parses the manifest's `<uses-permission>` `android:name` attributes and proves the `:app` manifest declares NEITHER `android.permission.INTERNET` NOR any `android.permission.health.WRITE_*` permission — then ran the phase exit gate green as one system (`:insights` 176/176, `:app` 99/99 incl. the new test, real 12.9 MB APK, `lintDebug` 0 errors), confirming ASHL-01..04 hold together.**

## Performance
- **Duration:** ~5 min
- **Started:** 2026-07-09T12:40:01Z
- **Completed:** 2026-07-09T12:44:40Z
- **Tasks:** 2 (1 test-adding, 1 verification-only exit gate)
- **Files:** 1 created, 0 production files modified

## Accomplishments
- **No-network / no-health-write manifest anchor (ASHL-04, T-19-02):** `ManifestPermissionsTest` parses `app/src/main/AndroidManifest.xml` with `DocumentBuilderFactory` (`isNamespaceAware=false`), collects every `<uses-permission>` element's `android:name` value, and asserts the set contains NEITHER `android.permission.INTERNET` NOR any name under the Health Connect write prefix `android.permission.health.WRITE_`. Because it reads element attributes, the manifest's explanatory comment — which literally names both `INTERNET` and `WRITE_*` — is structurally invisible and cannot false-trip it (the exact trap a raw grep would fall into).
- **Two-lane assertion:** a deterministic `source`-manifest test (the clean-checkout guard) plus an `Assume`-guarded `merged`-manifest test that, when a build has produced `build/intermediates/**/merged_manifest*/**/AndroidManifest.xml`, additionally asserts the stronger transitive surface (a permission a dependency might inject). Both ran GREEN here (2 tests, 0 skipped) because Plan-04's `assembleDebug` had already produced a merged debug manifest.
- **Red-capability proven by inject-and-revert:** temporarily added `<uses-permission android:name="android.permission.INTERNET" />` to the source manifest -> the source test went RED at the INTERNET assertion (`ManifestPermissionsTest.kt:114`) -> reverted -> GREEN again, with the manifest `git diff` confirmed empty (clean revert, no residue committed).
- **Forbidden tokens named safely in the test source:** the `INTERNET_PERMISSION` / `HEALTH_WRITE_PREFIX` literals live in the test (the `VersionCatalogAuditTests` precedent) — safe, since the scan targets the manifest file, not the test's own source.
- **Phase exit gate green across ASHL-01..04:** the full 19-VALIDATION suite command ran clean — `:insights:test` (176/176, no regression), `:app:testDebugUnitTest` (99/99, +2 over Plan-04's 97 for the new test), `:app:assembleDebug` (real 12.9 MB debug APK), `:app:lintDebug` (0 errors/fatals).

## Task Commits

1. **Task 1: ManifestPermissionsTest — no INTERNET, no health WRITE (comment-safe, red-capable)** — `03bfd08` (test)
2. **Task 2: Phase exit gate — full suite green across ASHL-01..04** — verification-only, no commit (the suite was already green with the Task-1 test in place; no production code change was required).

_No STATE.md / ROADMAP.md commit — the orchestrator owns state updates for this run (per objective)._

## Files Created/Modified
- `android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt` — the no-INTERNET + no-health-WRITE manifest guard (DOM element-attribute parse; source + merged lanes; red-capable).

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **99/99 passed, 0 failures** (+2 over the Plan-04 baseline of 97, for the new test).
  - `ManifestPermissionsTest` **2/2** — source-manifest guard + merged-manifest transitive check, both green (0 skipped; a merged debug manifest was present).
  - `AppGateConsentTest` **5/5** (ASHL-01 consent-by-construction gate, JVM Robolectric lane — see note below).
  - `ConsentedShellTest` **4/4** (ASHL-03 3-tab Bloom shell).
  - `OnboardingScreenTest` **5/5** (ASHL-01 informed-consent flow).
  - `CopyAuditTest` **7/7** (ASHL-02 registered phase-19 copy clean against the ClaimsPolicy substring gate).
  - `DisclaimerTest` **3/3** + `PrivacyPolicyTest` **10/10** (the exempt long-form legal constants pass their must-contain tests).
  - `BloomThemeTest` **2/2** (ASHL-02 Bloom tokens applied).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; `:insights` source untouched).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**, real 12.9 MB debug APK (`app/build/outputs/apk/debug/app-debug.apk`, 13,516,322 bytes).
- `:app:lintDebug` — **BUILD SUCCESSFUL**, **0 Error/Fatal** issues (`lint-results-debug.xml`).

### Red-capability evidence (inject-and-revert)
- With `<uses-permission android:name="android.permission.INTERNET" />` injected: `sourceManifestDeclaresNoInternetAndNoHealthWrite` **FAILED** — `AssertionError at ManifestPermissionsTest.kt:114` (the INTERNET assertion). Reverted; full `ManifestPermissionsTest` back to **2/2**; manifest `git diff` empty.

## ASHL-01..04 Coverage Map (as one green system)
| Requirement | Success criterion | Automated coverage | Status |
|-------------|-------------------|--------------------|--------|
| ASHL-01 | Consent-by-construction gate (unreachable pre-consent / withdraw re-gate / deep-link on gate) | `AppGateConsentTest` 5/5 + `OnboardingScreenTest` 5/5 (JVM) | GREEN |
| ASHL-02 | Bloom tokens + all UI copy registered & clean | `BloomThemeTest` 2/2 + `CopyAuditTest` 7/7 + `DisclaimerTest`/`PrivacyPolicyTest` must-contain | GREEN |
| ASHL-03 | 3-tab edge-to-edge shell + predictive-back-to-Log | `ConsentedShellTest` 4/4 (JVM) + Manual-Only device check (M5) | GREEN (JVM) / device-gated |
| ASHL-04 | No-INTERNET (and no health WRITE) manifest | `ManifestPermissionsTest` 2/2, red-capable | GREEN |

## Decisions Made
See `key-decisions` frontmatter. Headlines: (1) health WRITE guarded via the `android.permission.health.WRITE_` prefix (forward-proof against any future HC write) rather than an enumerated list; (2) a deterministic source-manifest test plus an assumption-guarded merged-manifest test (transitive check when a build exists); (3) Task 2 is a verification-only gate — no code change was needed because the suite was already green.

## Deviations from Plan
None — the plan executed exactly as written. The invariant already held on the committed manifest (zero permissions), so the guard test was GREEN on first run and red-capability was established by the documented inject-and-revert.

## Gate-lane note
- **ASHL-01 gate test lane:** `AppGateConsentTest` ran on the **JVM Robolectric lane** (`:app:testDebugUnitTest`), not the androidTest emulator fallback — it lives in `src/test` and executed with the rest of the unit suite. No androidTest fallback was needed.

## Manual-Only device checks surfaced (device-gated remainder, per 19-VALIDATION)
These are NOT automatable on the JVM lane and remain for on-device verification (coverage M5, `human_judgment`):
- **Bloom pixel/aesthetic fidelity vs the shipped iOS Bloom look** (ASHL-02) — compare a device/emulator screenshot to the iOS shell.
- **Predictive-back animation + edge-to-edge insets on a real Android 14+ device** (ASHL-03) — confirm no content under system bars and the predictive-back preview shows.
- **TalkBack sweep of onboarding/consent/tabs** (ASHL-01) — screen-reader focus order + labels across the gate and shell.

## Threat Model Compliance
- **T-19-02 (Information disclosure — manifest network/health-write permission, high):** mitigated — `ManifestPermissionsTest` proves the source (and, when built, merged) manifest declares no `android.permission.INTERNET` and no `android.permission.health.WRITE_*`; comment-safe (element-attribute parse) and red-capable (inject-and-revert). This is the Data-safety form's strongest artifact.
- **T-19-01 (Elevation of Privilege — phase-wide consent-by-construction, high):** mitigated — the exit gate re-ran `AppGateConsentTest` (5/5) so the gate invariant is confirmed green alongside the shell/onboarding/copy/manifest as one system.
- **T-19-SC (Tampering — package installs, low/accept):** honored — a test-only plan, no installs; the version catalog is unchanged and `VersionCatalogAuditTests` remains green in the `:insights` suite (176/176).

## Known Stubs
None introduced by this plan. (Carried from Plan 04 and unchanged: `OnboardingCopy.CONSENT_WRITE_ERROR` registered-but-unrendered until Phase 20 Settings; `PrivacyPolicyScreen` dismiss is predictive-back only — both intentional and out of scope here.)

## Next Phase Readiness
- Phase 19 is green as one system — ASHL-01..04 each map to a green automated test or an explicitly-surfaced Manual-Only device check.
- Phase 20 (tracking) owns the Settings entry (consent withdrawal + reminders surfacing `CONSENT_WRITE_ERROR`) and richer privacy-policy navigation.
- Phase 21 (Health Connect) will add READ-only health permissions — `ManifestPermissionsTest`'s `WRITE_` prefix guard already ensures any accidental WRITE permission then turns the suite red.

## Self-Check: PASSED
- The 1 created file exists on disk (verified below).
- The Task-1 commit `03bfd08` is present in git history (verified below).
- Real build/test evidence captured above (`:app` 99/99, `ManifestPermissionsTest` 2/2 + red-capable, `:insights` 176/176, `assembleDebug` 12.9 MB APK, `lintDebug` 0 errors).

---
*Phase: 19-app-shell-consent*
*Completed: 2026-07-09*
