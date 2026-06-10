---
phase: 04-private-cloudkit-sync
plan: 03
subsystem: human-uat
tags: [cloudkit, human-uat, device-only, data-02, data-03, data-04, schema-deploy, p-1, app-store-review]

# Dependency graph
requires:
  - phase: 04-private-cloudkit-sync
    plan: 01
    provides: "The runtime-gated CloudKit entitlement + account-aware SyncMode + container wiring whose real cloud path this protocol activates and verifies"
  - phase: 04-private-cloudkit-sync
    plan: 02
    provides: "The Settings 'iCloud sync: On/Off' line + the complete orphan-free deleteDay (local half of DATA-04) the device delete-purge scenario builds on"
provides:
  - "04-HUMAN-UAT.md — the device-only verification protocol for DATA-02/03/04: 5 scenarios + one-time CloudKit setup (set DEVELOPMENT_TEAM, deploy schema Development->Production) + P-1 sign-out caveat + a paste-ready 5.1.3(ii) App Store review note + a result-recording table"
affects: [phase-verification-gate, app-store-submission]

# Tech tracking
tech-stack:
  added: []  # zero packages — documentation only, first-party Apple frameworks
  patterns:
    - "HUMAN-UAT doc mirrors the 03-HUMAN-UAT status-block format (total/passed/issues/pending) plus per-criterion PASS/FAIL boxes and a fillable result table"
    - "Device-only criteria documented honestly as human_needed (D-16) — NO fabricated XCUITest/@Test cases for multi-device sync"

key-files:
  created:
    - .planning/phases/04-private-cloudkit-sync/04-HUMAN-UAT.md
    - .planning/phases/04-private-cloudkit-sync/04-03-SUMMARY.md
  modified:
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "DATA-02/03/04 remain device-gated: marked 'code-complete + HUMAN-UAT pending' in REQUIREMENTS traceability rather than fully checked off. The automatable scope (config correctness, account-seam logic, local-only build GREEN, local delete completeness) is proven in CI; the device-only proof needs a real Team ID + >=2 devices + same iCloud account and is surfaced to the phase verification gate."
  - "No automated sync test fabricated (D-16). The negative acceptance gate (grep -Eq 'XCUIApplication|@Test' returns no match) is satisfied — the protocol is purely human-run."
  - "Blocking human-verify checkpoint auto-approved under AUTO_MODE (user_response='approved'); the actual device run + P-1 acknowledgement are deferred to the user at the phase verification gate."

patterns-established:
  - "Phase-4 device-only DATA criteria captured as an executable HUMAN-UAT protocol with setup prerequisites, per-criterion PASS/FAIL, and a result table — the honest record for criteria CI cannot reach."

requirements-completed: []  # DATA-02/03/04 stay device-gated (code-complete + HUMAN-UAT pending), not fully complete

# Metrics
duration: ~2min
completed: 2026-06-08
---

# Phase 4 Plan 03: Private CloudKit Sync HUMAN-UAT Summary

**Authored `04-HUMAN-UAT.md` — the single device-only protocol the user runs on >=2 real iCloud devices to verify the four Phase-4 DATA criteria CI cannot reach (multi-device sync, offline reconcile, reinstall restore, delete-purge), with the one-time CloudKit setup steps (set `DEVELOPMENT_TEAM`, deploy schema Development->Production), the honest P-1 sign-out data-loss caveat + acknowledgement, and a paste-ready 5.1.3(ii) App Store review note — no fabricated automated sync tests (D-16).**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-06-08T18:19:57Z
- **Completed:** 2026-06-08T18:21:23Z
- **Tasks:** 1 auto task completed + 1 blocking human-verify checkpoint (auto-approved under AUTO_MODE)
- **Files:** 2 created (04-HUMAN-UAT.md, this SUMMARY), 3 doc files updated (STATE, ROADMAP, REQUIREMENTS)

## Accomplishments

- **Task 1 — `04-HUMAN-UAT.md` authored.** A human-run protocol (NOT automated tests), structured as:
  - **Why-manual / what's-shipped header:** states DATA-02/03/04 are `human_needed` (>=2 real devices + same iCloud account + a Developer team; not CI-automatable, D-16), and recaps that 04-01 (runtime-gated entitlement + account-aware `SyncMode` + `cloudKitDatabase: .none` on local paths, P-2) and 04-02 (non-blocking Settings On/Off line + orphan-free `deleteDay`) already shipped the inert-but-correct config these steps activate.
  - **Part A — one-time prerequisites (D-08):** A1 set `DEVELOPMENT_TEAM` in `project.yml` (uncomment the placeholder at ~line 33) + confirm/auto-create `com.endoapp.ios` / `iCloud.com.endoapp.ios` + `xcodegen generate`; A2 run signed under Development and let `NSPersistentCloudKitContainer` create the `CD_`-prefixed record types (or force them via `initializeCloudKitSchema()`) + verify in CloudKit Console; A3 promote schema Development->Production (mandatory before release, Pitfall P-3) + the append-only rule; A4 a paste-ready 5.1.3(ii) review note (Pitfall P-4). Each step has a checkbox.
  - **Part B — the 5 device scenarios, each with exact steps + a PASS/FAIL box:** (1) DATA-02 sync across devices with no login; (2) DATA-03a offline reconcile / LWW / no lost records via the framework offline queue (D-05/D-06); (3) DATA-03b automatic reinstall restore (D-07, no restore UI); (4) DATA-04 delete-purge (delete on A disappears on B; clean reinstall does NOT resurrect it, T-04-09); (5) sign-out grace — app fully usable local-only, Settings reads "iCloud sync: Off" (D-11).
  - **Part C — P-1 caveat + acknowledgement box:** documents honestly that iOS 18 wipes the local store on iCloud sign-out and **unsynced** edits are permanently lost (by design); a never-signed-in user is unaffected (`.localOnly`); accepted for v1, custom `CKSyncEngine` deferred (T-04-11 accept).
  - **Part D — result-recording table:** one row per criterion (Date / Device A / Device B / Pass-Fail / Notes), blank for the user.
  - **Status block** mirroring the 03-HUMAN-UAT format (total/passed/pending) + a Gaps section.
- **Task 2 — blocking human-verify checkpoint:** auto-approved per the AUTO_MODE directive (user_response = "approved"). The real device run + the P-1 acknowledgement are deferred to the user at the phase verification gate (this is the human-gated remainder; the automatable scope is already GREEN).

## Verification Evidence

- **Plan acceptance gates (all pass):** `test -f 04-HUMAN-UAT.md` OK; `grep -q` for `DATA-02`, `DATA-03`, `DATA-04`, `DEVELOPMENT_TEAM` OK; `grep -qi` for `Production`, `CloudKit Console`, `sign-out`/`sign out` OK.
- **Negative gate (D-16 honesty):** `grep -Eq 'XCUIApplication|@Test' 04-HUMAN-UAT.md` returns NON-zero (no match) — no fabricated automated sync test.
- **No code/test files touched** — documentation-only plan, so the build/test ritual does not apply (per the sequential_execution note). No regression risk to the 84/84 suite from 04-02.

## Deviations from Plan

None — plan executed exactly as written. The blocking human-verify checkpoint was auto-approved under the active AUTO_MODE directive (the plan's `gate="blocking"` is honored at the phase verification gate, where the actual device UAT is surfaced to the user).

## Threat Model Disposition

- **T-04-09 (GDPR Art. 17 / cloud purge):** the Part B Scenario 4 delete-then-clean-reinstall-shows-nothing step verifies the deleted record is truly gone from the private DB, not just locally hidden. Mitigation captured (device-run pending). Mitigate.
- **T-04-10 (5.1.3(ii) misread / public-DB drift):** Part A4 supplies a paste-ready review note affirming private-database-only sync + local-only fallback + no HealthKit data synced this phase. Mitigate.
- **T-04-11 (P-1 iCloud sign-out wipe):** Part C documents the data-loss window honestly and the checkpoint requires explicit user acknowledgement before the v1 trade-off stands. Accept.
- **T-04-SC (package installs):** zero package-manager installs this plan (documentation only). slopcheck N/A. Accept.

## Deferred Items (recorded per AUTO_MODE handling)

These are the human-gated remainder, surfaced to the user at the phase verification gate / in `04-HUMAN-UAT.md`:

- **Device run of DATA-02/03/04** on >=2 real devices signed into the same iCloud account (Part B scenarios 1-5).
- **Part A manual setup:** set `DEVELOPMENT_TEAM`, register/auto-create the container, deploy the CloudKit schema Development->Production.
- **P-1 sign-out data-loss acknowledgement** (Part C) — explicit user acceptance of the v1 trade-off.
- **A4 review note** to paste into App Store Connect before submission.

## Known Stubs

None. The document is a complete, executable protocol; the only "blanks" are the user-fillable result table (by design — it is filled during the device run).

## Notes for Downstream Plans / Phase Gate

- **Phase verification gate:** DATA-02/03/04 are `code-complete + HUMAN-UAT pending`. Do not mark them fully done until the user runs Part B and records PASS. The automatable scope (config, account seam, local-only GREEN, local delete completeness) is already proven across 04-01/04-02.
- **Before App Store submission:** Part A3 (promote schema to Production) and Part A4 (review note) are mandatory release-checklist items.

## Self-Check: PASSED

- Created file `.planning/phases/04-private-cloudkit-sync/04-HUMAN-UAT.md` exists (262 lines, all grep gates pass).
- Created file `.planning/phases/04-private-cloudkit-sync/04-03-SUMMARY.md` exists.
- Task 1 commit `43fd2fb` present in git history (`docs(04-03): author 04-HUMAN-UAT ...`).
