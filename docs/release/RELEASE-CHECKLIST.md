# RELEASE-CHECKLIST.md — Endo-App Android v2.0 Play release

> **HONESTY RULE (threat T-25-05):** every Console / account / device step below is an
> **UNCHECKED** human-confirmation box. Nothing in this repo pretends a Console,
> account, or device step is done. Checking a box here is a **human** action; no
> committed constant or test fakes a green Console state. The **AUTOMATABLE-AND-GREEN**
> half (verified by in-repo tests) is clearly separated from the **HUMAN / CONSOLE /
> DEVICE-GATED** half below.

---

## PART A — AUTOMATABLE-AND-GREEN (already done in-repo; verified by tests)

These are **complete** and mechanically corroborated by the codebase. No human action;
listed so the two halves are never confused. Re-run the gate any time with:
`cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

- [x] **Store listing copy** authored + claims-audited — `StoreListingCopyTest`,
  `CopyAuditTest` (Plan 25-01). Ready-to-paste in `PLAY-LISTING.md`.
- [x] **Not-a-medical-device disclaimer** in the first paragraph of the full
  description — `StoreListingCopyTest` first-paragraph must-contain (single-source
  `Disclaimer.text`).
- [x] **Data safety answers (all-"No")** bound to the codebase —
  `DataSafetyManifestCrossCheckTest` (no INTERNET, no analytics, read-only health);
  `ManifestPermissionsTest`; `VersionCatalogAuditTests` (Plan 25-02). Ready-to-paste in
  `DATA-SAFETY.md`.
- [x] **targetSdk 36 / compileSdk 36 / minSdk 26 + non-debuggable release** —
  `TargetSdkComplianceTest` (Plan 25-02).
- [x] **Single-source privacy-policy URL** (well-formed https / non-PDF / single
  literal) — `PrivacyPolicyUrlConsistencyTest` (Plan 25-02). NOTE: the *value* is a
  placeholder; hosting is a human step (Part B item 2).
- [x] **Health apps per-data-type justification** authored + consistent with the
  read-only manifest — `HEALTH-APPS-DECLARATION.md` + `ManifestPermissionsTest`.

---

## PART B — HUMAN / CONSOLE / DEVICE-GATED (NOT done by this phase)

Do these in order. **Item 1 is a KICKOFF item — start it first; it has weeks of latency.**

### 1. Organization developer account + D-U-N-S verification (KICKOFF — REQUIRED, AREL-04)

Health-category / "Medical" apps **cannot ship from an individual account**, and
individual→organization conversion takes **weeks** [VERIFIED: support.google.com/13634885].
Start this **before** everything else.

- [ ] Look up whether the developer entity already has a **D-U-N-S number** (free from
  Dun & Bradstreet); if not, **request one** (allow weeks).
- [ ] Create / convert the Play developer account to **organization type** using the
  D-U-N-S number.
- [ ] **Confirm the app's classification live in Console** — whether this
  wellness/self-tracking app is treated as a "Medical app" requiring the org account is
  not certain from public docs (ASSUMED A3). **Safe posture: treat org + D-U-N-S as
  REQUIRED and start now**; worst case you hold an org account you did not strictly
  need. Do NOT defer the D-U-N-S request on the hope it is unnecessary.
- [ ] Set the status line below (this is a human confirmation, not a committed boolean):

  ```
  ORG_ACCOUNT_VERIFIED: no      # user sets to "yes" once the org account + D-U-N-S is verified
  ```

### 2. Host the privacy policy + set `PRIVACY_POLICY_URL` (AREL-03)

- [ ] Host a **public, non-geofenced, non-PDF, non-editable HTTPS page** whose content
  mirrors `docs/release/PRIVACY-POLICY.md` (e.g. GitHub Pages).
- [ ] Replace the placeholder `PrivacyPolicyText.PRIVACY_POLICY_URL`
  (`https://replace-me.github.io/endo-app/privacy-policy`) with the real hosted URL, and
  enter the **SAME** URL in Play Console. Re-run
  `./gradlew :app:testDebugUnitTest --tests "*PrivacyPolicyUrlConsistencyTest"` after
  the edit.
- [ ] Verify the hosted page content matches `PrivacyPolicyText.body` (a hermetic test
  cannot fetch a live URL — this is a documented human check).

### 3. Submit the Console forms (paste from the Task-1 docs) (AREL-03)

- [ ] **Health apps declaration** — paste from `HEALTH-APPS-DECLARATION.md`.
- [ ] **Data safety form** (all-"No") — paste from `DATA-SAFETY.md`.
- [ ] **Store listing** (title / short / full / what's-new) — paste from
  `PLAY-LISTING.md`; confirm the disclaimer is the first paragraph.
- [ ] **Privacy policy URL** — enter the hosted URL from item 2 (same everywhere).

### 4. Pre-launch report on real devices (AREL-05)

- [ ] Upload the app bundle (see item 7 for signing) and run the **pre-launch report**.
- [ ] Review results on an **Android 13- device** and an **Android 14+ device** (the
  Google test-lab device set covers both generations — ASSUMED A6; confirm the two
  required generations appear in the report).
- [ ] **Consent-gate caveat:** the robo-crawler will land on the consent gate (correct
  by design) and may not proceed past it, producing **sparse crawl screenshots**. This
  is **expected, not a defect** — keep the gate. Optionally supply pre-launch
  credentials / deep-link config; do not panic-read the empty crawl as a failure.

### 5. Re-verify the Jan-2026 Play policy live (AREL-03/04)

- [ ] At submission time, re-verify the current Play health-policy details live in
  Console — the org-account classification and the **exact form field labels** (ASSUMED
  A5; Google does not publish the field wording). The *answers* in the Task-1 docs are
  canonical; only confirm the labels/flow have not changed.

### 6. Finalize app identity (ASSUMED A-identity — release-time human decision)

These are **placeholders** in `android/app/build.gradle.kts` /
`AndroidManifest.xml` [VERIFIED: codebase]. **Do NOT silently change code for these in
this phase** — finalize at release. The store **title** (`StoreListingCopy.title`) is
independent of `android:label`.

- [ ] `applicationId` — currently `"com.endoapp.app"` (placeholder; final package name
  is permanent once published — decide deliberately).
- [ ] `versionName` — currently `"0.1.0"` (set the public release version).
- [ ] `versionCode` — currently `1` (must increase with every upload).
- [ ] `android:label` — currently `"Endo"` (the on-device app name; separate from the
  store title `"Endo: Endometriosis Tracker"`).

### 7. App signing keystore (user-owned; never committed)

- [ ] Create / manage the **release signing keystore** (or enroll in Play App Signing).
  The user owns the keystore; it is **never** committed to the repo. Build a **release**
  bundle (`:app:bundleRelease`) signed with it for upload in item 4.

---

## Assumptions needing user confirmation (from RESEARCH)

- [ ] **A1 — target-API date.** `targetSdk 36` satisfies any Play floor ≤ 36; re-verify
  the current target-API date live in Console, **never lower** the value.
- [ ] **A3 — medical-app classification.** Confirm live in Console whether this app is
  classified such that the org account + D-U-N-S is required (safe posture: required —
  item 1).
- [ ] **A4 — privacy-policy URL.** The hosted URL is user-supplied; the placeholder must
  be replaced before submission (item 2).
- [ ] **A-identity — app identity.** `applicationId` / `versionName` / `versionCode` /
  `android:label` finalized as a release-time decision (item 6).
- [ ] **A5 — exact form labels.** Read live in Console at submission (item 5).
- [ ] **A6 — pre-launch device matrix.** Confirm the report covers an Android 13- and an
  Android 14+ device (item 4).

---

## Corroboration index (which green test backs each in-repo claim)

| Claim | Green test |
|-------|-----------|
| Store copy audited + disclaimer first | `StoreListingCopyTest`, `CopyAuditTest` |
| Data safety all-"No" matches the code | `DataSafetyManifestCrossCheckTest`, `ManifestPermissionsTest`, `VersionCatalogAuditTests` |
| targetSdk/compileSdk 36, minSdk 26, non-debuggable release | `TargetSdkComplianceTest` |
| Single-source, well-formed privacy URL | `PrivacyPolicyUrlConsistencyTest` |
| Read-only Health Connect (no health WRITE) | `ManifestPermissionsTest` |
