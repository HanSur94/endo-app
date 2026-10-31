# DATA-SAFETY.md — Google Play Data safety form (ready-to-paste, all-"No")

> **READY-TO-PASTE Console answers — NOT submitted state.** These are the exact
> answers the **user** enters in Google Play Console → **App content → Data safety**.
> The repo never holds the submitted Console state (threat T-25-05). **Re-verify the
> exact field labels live in Console at submission** (ASSUMED A5); the *answers* below
> are canonical and code-corroborated.

## Source of truth

The answers below are read from
`android/app/src/main/kotlin/com/endoapp/app/content/DataSafetyDeclaration.kt`
(`collectsData = false`, `sharesData = false`, `dataTypesCollected = emptyList()`,
`dataTypesShared = emptyList()`).

**Corroborating green tests (Plan 25-02 and prior):**
- `DataSafetyManifestCrossCheckTest` — a **conditional binding**: given the all-"No"
  declaration it asserts (a) no `android.permission.INTERNET` in the manifest,
  (b) no analytics / auth / backend / networking coordinate in `libs.versions.toml`,
  (c) no `android.permission.health.WRITE_*`, and both type lists empty. Red-capable
  in both directions (proven by inject-and-revert) — the form can never silently
  contradict the code.
- `ManifestPermissionsTest` (Phase 19) — the no-INTERNET / read-only-health anchor.
- `VersionCatalogAuditTests` (Phase 17) — no analytics / auth / backend coordinate.

---

## The exact answers to paste

| Data safety question | Answer | Corroboration in repo (green test) |
|----------------------|--------|-------------------------------------|
| Does your app collect or share any of the required user data types? | **No** | `ManifestPermissionsTest` (no INTERNET) + `VersionCatalogAuditTests` (no analytics/backend) + `DataSafetyManifestCrossCheckTest` |
| Is all of the user data collected by your app encrypted in transit? | **N/A** — no data leaves the device (nothing is in transit) | no INTERNET permission (`ManifestPermissionsTest`) |
| Do you provide a way for users to request that their data be deleted? | **Yes** — on-device delete-day cascade + withdraw-consent purge (note the local-only nature) | delete-day `@Transaction` + consent-withdrawal purge (Phases 18–20) |
| Data types collected | **none** | `DataSafetyDeclaration.dataTypesCollected = emptyList()` |
| Data types shared | **none** | `DataSafetyDeclaration.dataTypesShared = emptyList()` |

Because the top-level collect/share answer is **No**, Play does not ask you to
enumerate any data types — there is no data-type list to fill.

---

## Why all-"No" is TRUTHFUL (not marketing spin)

Play's own guidance is explicit: data that is **processed locally and never
transmitted off the device is NOT "collected" and NOT "shared"**
[VERIFIED: support.google.com/googleplay/android-developer/answer/10787469;
developer.android.com/privacy-and-security/declare-data-use]. Endo-App accesses and
processes everything **on-device only**, so the top-level answer is truthfully "No"
and there is no data-type list to enumerate.

The codebase corroborates this and **cannot drift from it**:

- **No `android.permission.INTERNET`.** Nothing can transmit off-device — the single
  strongest artifact, guarded since Phase 19 (`ManifestPermissionsTest`). Play runs
  automated form-vs-reality checks; an app with no network permission has nothing to
  contradict an all-"No" form.
- **No analytics / auth / backend / networking coordinate** in
  `gradle/libs.versions.toml` — nothing can phone home (guarded since Phase 17,
  `VersionCatalogAuditTests`).
- **No health WRITE permission** — Health Connect access is read-only (Phase 21).
- **Local-only storage.** The only egress is a **user-initiated, user-encrypted
  export** the user performs themselves (Phase 23) — a user-initiated share to a
  user-chosen destination is not "collection" or "sharing" in the Data-safety sense.

### On Health Connect and user-initiated export (do NOT push these to "Yes")

- Reading Health Connect is **local IPC**, not off-device transmission.
- The encrypted SAF export and the PDF `ACTION_SEND` share are **user-initiated** to a
  **user-chosen** destination — neither counts as collection or sharing
  [CITED: support.google.com/10787469].
- Health Connect data must still be **described in the privacy policy** (it is — see
  `PRIVACY-POLICY.md` and the in-app rationale surface).

---

## Notes for the human submitting

- The "delete my data" answer is **Yes** but the mechanism is entirely **on-device**
  (there is no server-side account to delete from). If Console asks for a URL/process,
  describe the in-app delete-day and withdraw-consent flows; there is no web form
  because there is no backend.
- If a future change ever adds a network path, analytics SDK, or health WRITE,
  `DataSafetyManifestCrossCheckTest` goes **RED** — do not update this form to "Yes"
  by hand without first reconciling the code.
