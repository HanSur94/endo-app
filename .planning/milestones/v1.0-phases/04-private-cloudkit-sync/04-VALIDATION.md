---
phase: 4
slug: private-cloudkit-sync
status: planned
nyquist_compliant: true
wave_0_complete: false  # A1 smoke is 04-01 Task 1; flips true on execution
created: 2026-06-08
---

# Phase 4 — Validation Strategy

> Per-phase validation contract. Derived from `04-RESEARCH.md` § Validation Architecture.
> **Reality:** the four DATA success criteria are inherently multi-device / real-iCloud and CANNOT run in CI — they are human_needed. The automatable scope is the seam logic, the local-only build staying green, and structural config correctness.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | XCTest (unit) + XCUITest (E2E) — existing |
| **Config file** | `project.yml` (XcodeGen) |
| **Quick run** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| **Full suite** | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | unit <30s · full ~3–6 min |

---

## Sampling Rate
- After every task commit: quick (unit) run for the touched target.
- After every wave: full suite.
- Before `/gsd-verify-work`: full suite green.

---

## Validation Architecture (layers)

| Layer | What it covers | Why here |
|-------|----------------|----------|
| **Wave-0 entitlement smoke** | Add `EndoApp.entitlements` + wire `CODE_SIGN_ENTITLEMENTS`; run the FULL existing suite and confirm it stays GREEN on the Simulator with NO `DEVELOPMENT_TEAM` | De-risks the feasibility crux (A1) before any other work — simulator builds don't code-sign, so this must pass |
| **Unit — AccountAvailability seam** | Injectable account-availability protocol → `SyncMode` resolves `.cloudKit` only when available; forced `.localOnly` in tests is deterministic | The only autonomously-testable slice of the sync decision |
| **Unit — local delete completeness (DATA-04 local half)** | `deleteDay` leaves zero residual records of any model on the local store (proxy for "no orphans to mirror") | Relationship-free hard-delete correctness is testable locally even though CloudKit purge is manual |
| **Structural (grep)** | entitlements keys present (icloud-container-identifiers, icloud-services=CloudKit, aps-environment, Background Modes remote-notification); container id `iCloud.com.endoapp.ios`; `SyncMode` reads the seam, not a hardcoded constant | Config correctness without a device |
| **Regression** | Full existing suite (Phases 1–3) stays green with sync code present but inert (local-only) | No behavioral regression for signed-out / CI |

---

## Per-Task Verification Map

> Populated during planning — one row per task. Wave 0 = the entitlement smoke + seam scaffolds.

| Task ID | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|-------------|-----------|-------------------|--------|
| 04-01 T1 (A1 smoke) | 1 | DATA-02 | smoke/structural | `xcodegen generate && xcodebuild test … (full suite)` + entitlement grep gates | ⬜ pending |
| 04-01 T2 (seam + SyncMode) | 1 | DATA-02/03 | unit (TDD) | `… -only-testing:EndoAppTests/SyncModeTests` (resolve + P-2 guard + CI-localOnly) | ⬜ pending |
| 04-01 T3 (composition root) | 1 | DATA-02 | unit+UI (regression) | full suite green local-only | ⬜ pending |
| 04-02 T1 (Settings line + privacy) | 2 | DATA-02 | unit (copy/privacy) | `… -only-testing:EndoAppTests/PrivacyPolicyTests,CopyAuditTests` + grep | ⬜ pending |
| 04-02 T2 (deleteDay completeness) | 2 | DATA-04 | unit (TDD) | `… -only-testing:EndoAppTests/EditDeleteTests` (FactorReading orphan) | ⬜ pending |
| 04-03 T1 (HUMAN-UAT doc) | 3 | DATA-02/03/04 | structural | `test -f 04-HUMAN-UAT.md` + content grep | ⬜ pending |
| 04-03 T2 (human-verify checkpoint) | 3 | DATA-02/03/04 | manual-only | device UAT (≥2 devices) — NOT CI-automatable | ⬜ device-gated |

---

## Wave 0 Requirements
- [ ] `EndoApp.entitlements` added + `CODE_SIGN_ENTITLEMENTS` wired in `project.yml`; full suite GREEN on Simulator with no team (the A1 de-risk).
- [ ] `AccountAvailability` protocol + test double (mirrors `ConsentStore`/`NotificationScheduler` seams).
- [ ] Unit-test stubs for account-aware `SyncMode` and local delete-completeness.

---

## Manual-Only Verifications (human_needed — the four DATA criteria)

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Data syncs across the user's own devices via private CloudKit, no account/login | DATA-02 | Needs ≥2 real devices + same iCloud account + a Developer team; CloudKit doesn't run in CI | Set `DEVELOPMENT_TEAM`, deploy CloudKit schema (Development), install on 2 devices signed into the same iCloud; log on A, confirm it appears on B |
| Offline edits later reconcile; last-writer-wins; no lost records | DATA-03 | Real network + 2 devices | Airplane-mode device A, edit a day; edit the same day on B; reconnect A; confirm both converge with no lost records |
| Fresh install restores full history from private iCloud | DATA-03 | Real device + iCloud | Delete + reinstall on a signed-in device; confirm full history restores |
| Deleting data purges the CloudKit private DB (reinstall shows nothing) | DATA-04 | Real device + iCloud | Delete a day; reinstall on a clean device; confirm the deleted day does NOT reappear |
| Signed-out user: app stays fully usable local-only | DATA-02 | Real iCloud sign-out | Sign out of iCloud; confirm the app still logs/reads locally with no crash; Settings shows "iCloud sync: Off" |
