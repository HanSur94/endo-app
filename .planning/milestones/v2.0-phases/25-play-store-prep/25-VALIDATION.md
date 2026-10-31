---
phase: 25
slug: play-store-prep
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-10
---

# Phase 25 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. This is the terminal release phase — a mix of automated green tests (the codebase-corroborated artifacts) and documented human/Play-Console-gated checklist items (never faked).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 pure-JVM (`:app` test/) for the store-copy claims audit, the Data-safety↔manifest cross-check, and the targetSdk/config assertions; no emulator; the human/Console-gated items are documented checklists, not tests |
| **Config file** | `android/app/build.gradle.kts` (targetSdk assertion source of truth); `android/gradle/libs.versions.toml` (no new dep) |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~4–6 min |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` no-regression; CopyAudit green (incl. store copy); catalog audit green
- **Max feedback latency:** ~360 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 25-01-xx | 01 | 1 | AREL-03 | — | StoreListingCopy registered + ClaimsPolicy-audited; disclaimer ("does not diagnose, treat, cure, or prevent") in the first paragraph of the full description; title≤30/short≤80/full≤4000 | unit | `./gradlew :app:test* --tests *StoreListing* *CopyAudit*` | ❌ W0 | ⬜ pending |
| 25-02-xx | 02 | 2 | AREL-03, AREL-05 | T-25-01 | DataSafetyDeclaration all-"No" cross-checked against no-INTERNET manifest + no-analytics catalog + local-only; targetSdk==36 + no-debuggable-release assertion; single PRIVACY_POLICY_URL constant | unit | `./gradlew :app:test* --tests *DataSafety* *TargetSdk* *PrivacyPolicyUrl*` | ❌ W0 | ⬜ pending |
| 25-03-xx | 03 | 3 | AREL-03, AREL-04, AREL-05 | — | committed ready-to-paste Console docs (Health-apps declaration, Data-safety answers, store listing, privacy policy, org-account/D-U-N-S + pre-launch checklist) — human-gated items clearly marked | doc | (documented checklist; the automated cross-checks live in 01/02) | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `StoreListingCopy` (title/short/full/what's-new) registered in CopyAuditRegistry; the full description's first paragraph reuses `Disclaimer.text` (single-source, carries Play's exact "does not diagnose, treat, cure, or prevent" wording)
- [ ] `DataSafetyDeclaration` machine-checkable artifact (all-"No" collected/shared) + a cross-check test asserting it against the manifest (no INTERNET), the catalog (no analytics/backend/net), and local-only storage
- [ ] `PRIVACY_POLICY_URL` single constant referenced by the in-app rationale/privacy screen + the store listing (consistency assertion)
- [ ] targetSdk/compileSdk == 36 + no-debuggable-release assertion (checkable in build.gradle.kts)
- [ ] Committed Console docs: `PLAY-LISTING.md`, `DATA-SAFETY.md`, `HEALTH-APPS-DECLARATION.md`, `PRIVACY-POLICY.md`, `RELEASE-CHECKLIST.md` (org-account/D-U-N-S + pre-launch clearly marked HUMAN-GATED)
- [ ] Test scaffolds: StoreListingCopyTest, DataSafetyCrossCheckTest, TargetSdkComplianceTest, PrivacyPolicyUrlConsistencyTest (red until sources land)

*The store-copy first-paragraph disclaimer, the Data-safety↔manifest truthfulness cross-check, and targetSdk 36 are Wave-0-visible automatable acceptances; the org-account + Console submission + pre-launch report are documented human-gated.*

---

## Manual-Only Verifications (Play Console / real-device / human — documented, NOT faked)

| Behavior | Requirement | Why Manual | Instructions |
|----------|-------------|------------|--------------|
| Organization developer account with D-U-N-S verified | AREL-04 | Google account-type verification; individual→org takes weeks — START AT KICKOFF | Verify/convert the Play developer account to organization + obtain D-U-N-S; confirm health-app classification in Console |
| Health apps declaration submitted in Play Console | AREL-03 | Console form submission | Paste the committed HEALTH-APPS-DECLARATION.md answers into the Console health declaration |
| Data safety form submitted (all "No") | AREL-03 | Console form submission | Paste the committed DATA-SAFETY.md answers; verify consistency with the (green) cross-check |
| Store listing uploaded + privacy policy URL hosted | AREL-03 | Console upload + hosting a public HTML page | Host a public privacy policy mirroring PrivacyPolicyText.body at PRIVACY_POLICY_URL; upload the listing copy |
| Pre-launch report passes on Android 13- and 14+ | AREL-05 | Runs on real devices in Console | Upload the release build; run the pre-launch report on both OS generations; confirm no blocking issues |
| Re-verify Jan-2026 policy details live | AREL-03 | Policy pages churn | Re-check the health-apps + target-API policy in the live Console at submission time |

*The automatable artifacts (store-copy audit, Data-safety↔manifest cross-check, targetSdk assertion, doc set) are green/committed in-repo; every Console/device/account step above is documented as a precise ready-to-execute checklist and performed by the user — none are faked.*

---

## Validation Sign-Off

- [ ] All automatable tasks have `<automated>` verify or Wave 0 dependencies; human-gated items documented as checklists
- [ ] Sampling continuity holds for the automatable tasks
- [ ] Wave 0 covers all MISSING references (store copy, Data-safety artifact, privacy URL, targetSdk assertion, Console docs)
- [ ] No watch-mode flags
- [ ] Feedback latency < 360s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
