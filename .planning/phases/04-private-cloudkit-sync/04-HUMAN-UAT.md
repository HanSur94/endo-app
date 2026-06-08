---
status: pending
phase: 04-private-cloudkit-sync
source: [04-RESEARCH.md, 04-VALIDATION.md, 04-CONTEXT.md]
requirements: [DATA-02, DATA-03, DATA-04]
kind: human-uat
started: 2026-06-08
updated: 2026-06-08
---

# Phase 4 — HUMAN-UAT: Private CloudKit Sync (device-only)

> **This is a human-run protocol, NOT an automated test.** The four Phase-4 success
> criteria (DATA-02 multi-device sync, DATA-03 offline reconcile + reinstall restore,
> DATA-04 delete-purge) are *inherently* multi-device + real-iCloud + real-Apple-team
> and **cannot run in CI or XCUITest** (D-16). They are `human_needed`. Nothing below
> is faked as a Swift Testing / XCUITest case — running them is on you, on real hardware.

## Why these are manual (not automatable)

CloudKit sync needs all of the following at once, none of which exists in CI:

- **≥2 real iOS devices** (or 1 real device + 1 signed Simulator) **signed into the SAME iCloud account**.
- A **configured Apple Developer team** (a real `DEVELOPMENT_TEAM` Team ID).
- A **deployed CloudKit schema** in the `iCloud.com.endoapp.ios` container.
- Real network conditions you can toggle (airplane mode, reconnect).

The Simulator does not code-sign and CI has no iCloud account, so the build resolves
`.localOnly` and the Settings line reads **"iCloud sync: Off"** there — by design.

## What is already shipped (the code these steps activate & verify)

Phases 04-01 and 04-02 already landed everything *automatable*; this protocol activates
and verifies the real cloud path on top of it:

- **04-01:** the private-DB-only CloudKit entitlement (`iCloud.com.endoapp.ios` + CloudKit
  service + `aps-environment` + Background Modes `remote-notification`), runtime-gated so
  the unsigned Simulator suite stays GREEN with no Team ID (A1 held). The account-aware
  `SyncMode.resolve(_:)` reads an injectable `AccountAvailability` seam
  (`FileManager.default.ubiquityIdentityToken`, synchronous, never the async `accountStatus`,
  never logs the token — D-03/P-5), and the composition root resolves the mode **before**
  building the container, passing `cloudKitDatabase: .none` explicitly on every local path (P-2).
- **04-02:** the non-blocking Settings **"iCloud sync: On/Off"** line (`settings.sync.status`,
  no spinner/toggle/conflict UI — D-12), the private-iCloud privacy footer (D-13), and a
  complete local `deleteDay` that now purges Phase-3 `FactorReading` rows too — so no orphan
  survives to mirror to the private DB (the local half of DATA-04, locked by `EditDeleteTests`).

These steps below turn that inert-but-correct config into live sync and verify it on devices.

---

## Part A — One-time prerequisites (manual setup, D-08)

Do these **once** before running the scenarios. Source: 04-RESEARCH.md § CloudKit Schema
Deployment Steps + Pitfalls P-3 / P-4.

### A1. Set your Apple Developer Team

1. Open `project.yml`. Find the commented placeholder (around line 33):
   ```yaml
   # DEVELOPMENT_TEAM: ABCDE12345   # <-- USER: uncomment + set to activate real device sync (D-02)
   ```
2. **Uncomment it and set your real Team ID** (Apple Developer account → Membership → Team ID):
   ```yaml
   DEVELOPMENT_TEAM: <YOUR_TEAM_ID>
   ```
3. Confirm the bundle id `com.endoapp.ios` and the CloudKit container `iCloud.com.endoapp.ios`
   are registered in your Apple Developer account — **or** let Xcode auto-create the container
   on first signed build.
4. Regenerate the Xcode project:
   ```
   xcodegen generate
   ```

- [ ] **A1 done** — `DEVELOPMENT_TEAM` set, `xcodegen generate` run, project opens signed.

### A2. Deploy the CloudKit schema (Development)

5. Build & run on a **real device or a signed Simulator** with a signed-in iCloud account,
   under the **Development** environment (the default for Debug builds).
   `NSPersistentCloudKitContainer` auto-creates the `CD_`-prefixed record types in Development
   as records are written.
6. **(Recommended — more reliable than lazy creation.)** Force-create the full schema up front
   by calling `try container.initializeCloudKitSchema()` once behind a `#if DEBUG` one-shot
   (NSPersistentCloudKitContainer API), then remove/disable it after the schema lands. This
   avoids waiting for lazy record-type creation on first sync.
7. In **CloudKit Console** (icloud.developer.apple.com) → container `iCloud.com.endoapp.ios`
   → **Development** environment, confirm the record types exist: `CD_DayEntry`,
   `CD_FactorReading`, and the other `CD_`-prefixed entities + their fields (CloudKit prefixes
   Core Data entity names with `CD_`).

- [ ] **A2 done** — `CD_DayEntry`, `CD_FactorReading`, etc. visible in CloudKit Console (Development).

### A3. Promote the schema Development → Production (mandatory before release — P-3)

8. In **CloudKit Console**, use **Deploy Schema Changes** to promote the schema from
   **Development → Production**.
   **This is mandatory before any App Store release.** Without it, production users' sync
   **fails silently** — the schema simply doesn't exist in Production (Pitfall P-3).
9. **Remember CloudKit's append-only rule:** once a schema is in **Production**, record types
   and fields can **only be additively extended** — never renamed, retyped, or deleted. The
   existing schema was designed append-only-safe, so this is fine going forward.

- [ ] **A3 done** — schema promoted to Production in CloudKit Console (required before App Store submission).

### A4. App Store review note (paste-ready — pre-empts a 5.1.3(ii) misread, P-4)

Guideline 5.1.3(ii) ("may not store personal health information in iCloud") is frequently
*misread* by reviewers. Its intent targets dumping **HealthKit-derived** data into general /
Apple-readable iCloud. Private-CloudKit-DB sync of the app's **own user-entered** data is the
accepted pattern (it is how Apple's own Health app syncs). Paste this into App Store Connect
review notes to pre-empt a literal-reading rejection:

> Endo-App syncs the user's own manually-entered tracking data **only** to that user's
> **private CloudKit database** (Apple-account-scoped, end-to-end protected, never a
> public or shared database, never visible to the developer). No HealthKit-derived data
> is synced. The app is fully usable **local-only** when the user is signed out of iCloud —
> sync is an optional convenience, not a requirement. There is no app account, login, or
> third-party backend.

- [ ] **A4 noted** — review note saved for App Store submission.

---

## Part B — Device-only verification scenarios (DATA-02 / 03 / 04)

Run each on **≥2 devices signed into the SAME iCloud account** (call them **Device A** and
**Device B**). Tick PASS or FAIL and record details in the result table (Part D).

> Source: 04-RESEARCH.md § Manual-Only (the 5 numbered scenarios) + 04-VALIDATION.md
> § Manual-Only Verifications. **No login is performed on either device — that is the point
> of DATA-02 (sync with no account).**

### Scenario 1 — DATA-02: data syncs across devices, no account/login

1. On **Device A**, log a symptom (e.g. a Daily check-in entry for today).
2. Wait a few minutes (CloudKit silent push can take a moment).
3. On **Device B**, open the app and confirm the **same entry appears**.
4. Confirm you performed **no login / no account sign-in** in the app on either device —
   only the device's iCloud account is used.

- [ ] **PASS** — entry logged on A appears on B within minutes, no app login performed.
- [ ] **FAIL** — (record what happened in Part D)

### Scenario 2 — DATA-03a: offline reconcile, last-writer-wins, no lost records

> Rides NSPersistentCloudKitContainer's **built-in offline queue + sync-on-reconnect**
> (D-06) + append-only-schema LWW (D-05). There is **no custom outbox** — this verifies the
> framework default reconciles without losing records.

1. Put **Device A** in **airplane mode**. While offline, log new entries on A **and** edit an
   existing field/day on A.
2. On **Device B** (online), independently edit a *different* field or a *different* day.
3. Re-enable network on **Device A**.
4. After both devices settle, confirm **both sets of changes survive** — A's offline entries
   upload and appear on B, and B's online edits appear on A. **No record is lost.** For the one
   field edited on *both* sides, last-writer-wins is acceptable (one value persists, no crash,
   no data corruption).

- [ ] **PASS** — offline A-edits and online B-edits both converge; independent records all survive.
- [ ] **FAIL** — (record what was lost in Part D)

### Scenario 3 — DATA-03b: reinstall restores full history automatically

> Reinstall restore is **automatic** (D-07) — CloudKit mirrors the private DB back on fresh
> sign-in. There is **no restore UI and no restore code** to exercise; you are verifying the
> framework does this for free.

1. **Delete** the app from a signed-in device (e.g. Device A).
2. **Reinstall** the app and open it (the device is still signed into the same iCloud account).
3. Confirm the **full prior history restores automatically** from the private iCloud DB — no
   restore screen, no manual import.

- [ ] **PASS** — full history restores automatically after delete + reinstall, no restore UI.
- [ ] **FAIL** — (record what was missing in Part D)

### Scenario 4 — DATA-04: deleting data purges the private CloudKit DB

> The **local** half of this (a complete, orphan-free `deleteDay`) is already proven in CI by
> `EditDeleteTests` (04-02). This scenario verifies the **cloud** half: the delete truly
> propagates and the private-DB copy is **gone**, not just hidden locally (GDPR Art. 17 /
> T-04-09).

1. On **Device A**, **delete a day** that exists on both devices.
2. Confirm on **Device B** the deleted day **disappears**.
3. Now **reinstall** the app on a **clean device** (or Device A after delete+reinstall) signed
   into the same iCloud account.
4. Confirm the deleted day **does NOT reappear** — the private-DB copy was truly removed, not
   merely hidden on the original device.

- [ ] **PASS** — delete on A removes it on B; a clean reinstall does NOT resurrect the deleted day.
- [ ] **FAIL** — (record reappearance in Part D)

### Scenario 5 — sign-out grace: app stays fully usable local-only (D-11)

> Verifies the **no-account promise**: a signed-out user loses no functionality.

1. **Sign out of iCloud** (or disable iCloud for the app in Settings.app) on a device.
2. Confirm the app **stays fully usable** — you can still log and read entries **local-only**,
   with **no crash, no error wall, no blocking screen**.
3. Confirm the Settings line now reads **"iCloud sync: Off"** (`settings.sync.status`).

- [ ] **PASS** — app fully usable local-only after iCloud sign-out; Settings shows "iCloud sync: Off".
- [ ] **FAIL** — (record any crash/block in Part D)

---

## Part C — P-1 caveat: iCloud sign-out can wipe unsynced edits (acknowledge for v1)

> **This is accepted framework behavior for v1 — documented honestly, not a bug to fix here.**
> Source: 04-RESEARCH.md Pitfall P-1 (`developer.apple.com/forums/thread/811294`) + D-11.

On **iOS 18, by design**, when a user signs out of iCloud (or disables iCloud for the app)
while `.cloudKit` sync is active, `NSPersistentCloudKitContainer` **deletes the local store**
for privacy. Synced data reappears on sign-in; but records saved locally and **not yet
uploaded** (offline, rate-throttled, app killed) are **permanently lost**.

Scope and honest framing:

- A user who **never signs into iCloud** is **unaffected** — they stay `.localOnly`, a plain
  local store, no mirroring, no wipe.
- The risk window is **only** for users who had sync on and then sign out **with unsynced edits**.
- We **do NOT** detect-and-rescue this in code this phase — a custom `CKSyncEngine` is out of
  scope and deferred to v2 (flagged as an Open Question). The privacy/sync copy frames it softly
  ("your data lives in your private iCloud; turning iCloud off removes the synced copy from this
  device").

- [ ] **Acknowledged** — I accept the P-1 sign-out data-loss trade-off for v1 (custom sync engine deferred).

---

## Part D — Result-recording table

Fill one row per criterion as you run the scenarios on real hardware.

| Criterion | Scenario | Date | Device A | Device B | Pass/Fail | Notes |
|-----------|----------|------|----------|----------|-----------|-------|
| DATA-02 | 1 — sync across devices, no login | | | | | |
| DATA-03 | 2 — offline reconcile / LWW, no lost records | | | | | |
| DATA-03 | 3 — reinstall restores full history | | | | | |
| DATA-04 | 4 — delete purges cloud (reinstall shows nothing) | | | | | |
| DATA-02 | 5 — sign-out grace (fully usable local-only) | | | | | |

---

## Status summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

**Current state:** Code-complete + HUMAN-UAT pending. The automatable scope of Phase 4
(config correctness, account-seam logic, local-only build staying GREEN, local delete
completeness) is already proven green in CI. DATA-02 / DATA-03 / DATA-04 remain **device-gated**
until you complete Part A setup and run Part B on ≥2 real devices.

## Gaps

- Device-only DATA-02/03/04 verification not yet run (requires real Team ID + ≥2 devices + same iCloud account).
