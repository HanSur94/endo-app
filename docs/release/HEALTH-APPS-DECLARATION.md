# HEALTH-APPS-DECLARATION.md — Google Play Health apps declaration (ready-to-paste)

> **READY-TO-PASTE Console answers — NOT submitted state.** These are the answers the
> **user** enters in Google Play Console → **App content → Health apps declaration**.
> The repo never holds the submitted Console state (threat T-25-05). **Re-verify the
> exact field labels live in Console at submission** (ASSUMED A5) — Google does not
> publish the form wording; the *answers* below are canonical and code-corroborated.

## Are we in scope? — Yes

"If your app is not primarily a health app, but has health-related features and
accesses health data, it is still in scope of the Health App policy." A self-tracking
wellness tracker that reads Health Connect and makes no medical claims **is in scope**
and must complete the Health apps declaration, post a publicly accessible privacy
policy, and avoid unnecessary permissions
[VERIFIED: support.google.com/16679511]. All developers with a published app must
complete the Health apps declaration regardless [VERIFIED: support.google.com/14738291].

## App classification answers

| Declaration point | Answer |
|-------------------|--------|
| App category | **Self-tracking / wellness tracker** (symptoms, cycle, daily factors) |
| Is this a medical device? | **No** — informational self-tracking only |
| Does it diagnose, treat, cure, or prevent any condition? | **No** (the exact disclaimer wording — see below) |
| Health Connect access | **READ-ONLY** — zero health WRITE permissions |
| Data handling | **On-device only** — no servers, no accounts, no third-party sharing/analytics |
| Privacy policy | Public HTTPS URL = `PRIVACY_POLICY_URL` (see `PRIVACY-POLICY.md`) |

**Not-a-medical-device wording (VERIFIED: support.google.com/16679511), reused verbatim
from `Disclaimer.text`:**

> Endo-App is an informational self-tracking tool. It is not medical advice and not a
> medical device. It does not diagnose, treat, cure, or prevent any condition. The
> patterns it surfaces come from your own data and are meant to help you understand
> your body and have better conversations with your clinician — not to replace
> professional care.

## Per-Health-Connect-data-type justification

Each read permission maps to a **visible feature a reviewer can find** in the app. This
reuses the Phase-21 in-app dual-rationale story verbatim, so the **declaration, the
manifest, and the app behavior tell ONE story** [CITED: PITFALLS Pitfall 2].

| Health Connect read permission | Visible feature the reviewer can find | Justification |
|--------------------------------|----------------------------------------|---------------|
| `READ_MENSTRUATION` | Cycle logging + cycle-phase views | Primary function: menstrual cycle tracking + phase mapping |
| `READ_SLEEP` | Sleep as a factor in trend / association views | Correlate sleep with symptoms in the user's own data |
| `READ_EXERCISE` | Exercise as a factor in trend / association views | Correlate activity with symptoms in the user's own data |
| `READ_NUTRITION` | Food / nutrition factor logging | Correlate diet factors with symptoms (the food-logging feature is the explicit consumer of `NutritionRecord`) |
| `READ_HEALTH_DATA_HISTORY` | Longitudinal cycle-history import | The app's whole value is longitudinal; without it the import silently truncates to ~30 days |

**Corroboration in repo:** the manifest declares exactly these read permissions and
**no** `android.permission.health.WRITE_*` — the read-only posture is pinned by
`ManifestPermissionsTest` (Phase 19) and cross-checked by
`DataSafetyManifestCrossCheckTest` (Plan 25-02). The permissions-rationale screen
(`PermissionsRationaleActivity` → `PrivacyPolicyText`) is reachable via both declared
surfaces — the Android 13- `ACTION_SHOW_PERMISSIONS_RATIONALE` activity and the
Android 14+ `VIEW_PERMISSION_USAGE` alias — and renders the same privacy content as the
store listing and hosted website [VERIFIED: codebase manifest + 21-05-SUMMARY].

## Framing to keep consistent across every surface

- **Read-only + on-device.** Health data is read from Health Connect via local IPC and
  processed on the device; it does not leave the phone unless the user exports it
  themselves.
- **Not a medical device.** The "does not diagnose, treat, cure, or prevent any
  condition" wording is the single-source `Disclaimer.text`, and it also appears in the
  first paragraph of the store full description (`PLAY-LISTING.md`) and in the in-app
  onboarding — one story, no drift.

## Notes for the human submitting

- **ASSUMED A3 — classification / org account:** whether this wellness tracker is
  formally a "Medical app" (which would require an organization account + D-U-N-S) is
  not certain from public docs. **Confirm the exact classification live in Console**,
  and — per the safe posture in `RELEASE-CHECKLIST.md` — start the org-account + D-U-N-S
  process at kickoff regardless (weeks of latency if wrong-and-late).
- **ASSUMED A5 — field labels:** the exact declaration field wording must be read live
  in Console; paste the answers above into the matching fields.
