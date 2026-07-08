# Feature Research

**Domain:** Android port of a privacy-first endometriosis tracking app (feature set locked by iOS v1.1 parity — this document covers only the Android-specific delivery of those features)
**Researched:** 2026-07-08
**Confidence:** MEDIUM (verified against developer.android.com / Play Console Help pages surfaced in search; provider-tier per `classify-confidence`: websearch-verified = MEDIUM)

> **Scope note:** WHAT to build is fixed by parity (see PROJECT.md Validated + ANDROID-PORT-PLAN.md Section 4). This file answers HOW those features behave natively on Android — the platform norms Android users expect, what is table stakes vs. optional, and what to deliberately not do.

## Feature Landscape

### Table Stakes (Users Expect These)

Platform behaviors Android users (and Google Play review) assume exist. Missing these = the port feels like a bad iOS transplant or fails review.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Health Connect rationale surface (manifest-declared)** | Hard Play requirement, not UX polish: an Activity handling `ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13-) **plus** an activity-alias handling `VIEW_PERMISSION_USAGE` + category `HEALTH_PERMISSIONS` (Android 14+), reachable from the privacy-policy link on Health Connect's own permission screen. Must show the same privacy policy as the store listing. | LOW | Phase 5 builds it; Phase 8 declares it. Missing/mismatched = rejection under the Health apps policy (Jan 2026 enforcement added per-data-type justification). |
| **Health Connect availability gating** | HC is a framework module on Android 14+, but a separate Play-installed app on 13 and below. `getSdkStatus()` returns SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED — the import surface must render all three honestly (offer the Play deep link to install/update, never block manual logging). | LOW–MEDIUM | Already a locked decision (plan Section 1 note). minSdk 26 guarantees the "separate app" path exists on a large slice of devices. |
| **"Health Connect can be empty" expectation-setting** | HC is a broker, not a data source. Data exists only if a provider app (Samsung Health, Fitbit, etc.) wrote it. A permission grant followed by zero records is the *common* first-run outcome — the import UI needs an honest "nothing to import yet, here's why" state, which happens to match the app's absence ≠ 0 ethos exactly. | LOW | Reuse the iOS nil-honest empty-state copy patterns; register all new strings in the copy audit. |
| **Graceful handling of the ~30-day history read window** | By default HC only lets an app read records from up to ~30 days before its first permission grant; older history needs the separate `READ_HEALTH_DATA_HISTORY` permission (its own toggle on the HC screen, its own Play justification). Users who grant import expect their *history* to appear — the UX must either request history read with rationale or say plainly why older data is absent. | MEDIUM | Decision needed in Phase 5: request history permission (one more toggle, one more Play justification) vs. honest "imports start from ~30 days back" copy. Confidence: MEDIUM — verify the exact window against current HC docs during Phase 5. |
| **POST_NOTIFICATIONS just-in-time runtime permission** | Runtime permission since Android 13. Norm: request only at the moment the user turns reminders ON in settings — never at app launch. Denial must leave reminders visibly off with calm copy, not a nag loop. | LOW | Mirrors the iOS contextual-rationale-before-prompt pattern already shipped. Plan Phase 4 already words it this way. |
| **Notification channel for reminders** | Android users expect per-channel control (silence "Daily reminder" in system settings without killing all app notifications). One channel, sensible defaults, respect user overrides. | LOW | Channel name/description are user-facing copy → copy audit. Keep the channel *only* for reminders — the flare no-notification structural scan stays clean because flare code never touches notification APIs. |
| **Inexact scheduling for the daily reminder** | `SCHEDULE_EXACT_ALARM` is denied by default since Android 14 and Play restricts its justification to alarm-clock/calendar-class apps — a symptom tracker will not qualify. A daily check-in reminder tolerates minutes of drift: use `AlarmManager.setAndAllowWhileIdle`/`setWindow` (inexact) or WorkManager periodic work. | LOW–MEDIUM | Also handle `BOOT_COMPLETED` re-scheduling (alarms die on reboot; WorkManager survives it — a point in WorkManager's favor). See Anti-Features for the exact-alarm trap. |
| **SAF-based export/import (zero storage permissions)** | `ActivityResultContracts.CreateDocument` / `OpenDocument` is the modern norm: user picks the destination (local, their own Drive, anywhere), app declares **no** storage permissions. Suggest a filename + MIME type, stream via ContentResolver, handle cancellation as a non-event. | LOW–MEDIUM | Already the locked mechanism for the encrypted export/import (plan Section 2). The permission-free posture is itself a Data-safety-form talking point. |
| **Auto Backup exclusion for the health DB** | Without `dataExtractionRules`/`fullBackupContent` rules, Android silently copies app data into the user's Google-account-tied backup — an opaque cloud copy of special-category health data the user never consented to. Excluding the Room DB (keeping only prefs/consent flag) is table stakes for the privacy posture and must be testable. | LOW | Locked decision (plan Section 2 alternatives table). Land the rules in Phase 2 alongside Room, with a rules-file assertion test. |
| **Edge-to-edge from day one** | Mandatory at targetSdk 36 (`windowOptOutEdgeToEdgeEnforcement` deprecated/disabled) and Play's target-API deadline for 36 is Aug 31 2026 — i.e., before or around this port ships. Compose makes it cheap if built in from the shell phase: `Scaffold` insets, `systemBarsPadding`, IME-union padding on sheets. | LOW (if Phase 3) / MEDIUM (retrofit) | Build the Bloom shell edge-to-edge in Phase 3. Retrofitting insets across finished screens is the expensive path — don't take it. |
| **Predictive back compatibility** | Predictive back animations are on by default at targetSdk 36; `onBackPressed`/`KEYCODE_BACK` interception no longer fires. Norm: don't intercept back at all except where truly needed (e.g., unsaved Daily Check-In sheet), and there use the supported `BackHandler`/`PredictiveBackHandler` APIs so the back-preview animation works. | LOW–MEDIUM | Navigation-Compose handles the common cases. Test back-to-home and cross-destination previews on an Android 16 device in Phase 3. |
| **Material 3 `NavigationBar` app shell** | The direct analog of the iOS 3-tab bar; M3 recommends 3–5 destinations, so the 3-tab shell ports cleanly. Android norms on top: selected-state indicator pill, per-tab state preservation (`saveState`/`restoreState` back-stack pattern), reselect-tab returns to tab root, system back from a non-start tab does NOT walk tab history (unlike some iOS conventions) — back from a secondary tab conventionally returns to the start tab or exits. | MEDIUM | Bloom-theme the M3 NavigationBar rather than hand-building iOS tab chrome. The consent `AppGate` composes *around* the shell exactly as on iOS — deep links and notification taps land on the gate, never inside. |
| **TalkBack access to every chart (overlay semantics)** | Compose `Canvas` drawing — including `drawText` axis labels — is completely invisible to TalkBack. There is **no Android equivalent of iOS Audio Graphs / `AXChartDescriptor`**. The norm for accessible custom charts: a chart-level `Modifier.semantics { contentDescription }` summary node plus focusable invisible overlay nodes per data point/day so TalkBack can step through values — which is precisely the Android translation of the v1.1 "non-gesture VoiceOver path" requirement. | HIGH | This is real, bespoke work in Phase 6 (and the strongest hidden argument for the owned-Canvas decision: a library wouldn't give honesty-aware descriptions anyway). Gap days must announce "not logged", severity-0 must announce zero — the honesty contract extends into the semantics tree and should be pinned by a semantics-tree test. |
| **BodyMap TalkBack semantics** | Same Canvas-invisibility problem: each of the 10 body regions needs a focusable semantics node with role, name, and selected/severity state (`stateDescription`), plus 48dp minimum touch targets. | MEDIUM | Phase 4. iOS shipped the VoiceOver equivalent, so the per-region description copy already exists — port and register it for audit. |
| **PDF share via `FileProvider` + `ACTION_SEND`** | The Android norm for handing a file to the share sheet: write the PDF to an app-private cache subdir, expose through a manifest-declared `FileProvider`, share with `FLAG_GRANT_READ_URI_PERMISSION` via `Intent.createChooser`. No storage permission; the grant is scoped to the user's chosen target — this *is* the "no silent upload" property, mechanically. | LOW–MEDIUM | Phase 7 (already locked). Two Android extras users expect: (1) also offer "Save as file" via SAF `CreateDocument` (Android users look for save-to-Files as a peer of share), (2) clean up cached PDFs so old reports don't linger readable in cache. The exit criterion "no file readable by other apps beyond the explicit share target" maps to FileProvider-only exposure + cache cleanup. |
| **Truthful Data safety form ("no data collected, no data shared")** | Health-app users on Play read the Data safety section; a genuinely all-"No" form is rare in this category and is the storefront proof of the architecture. Must stay consistent with the manifest (zero write health permissions, no INTERNET-dependent SDKs) and the privacy policy. | LOW (but exacting) | Phase 8. The version catalog + manifest are the audit artifacts; the Jan-2026 Health apps declaration wants per-HC-data-type justifications — write them from the four read types' user-facing features. |

### Differentiators (Competitive Advantage)

Android-specific touches that are not required for parity but land well in this category. None are launch-blocking.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Optional biometric/PIN app lock (`androidx.biometric` BiometricPrompt)** | Common and increasingly *expected* in period/health trackers (Clue, Flo ship it). For a stigmatized condition, "my phone can be borrowed without my symptom history being open" is real user value. Opt-in, with device-credential fallback, gating app open — never gating data deletion. | MEDIUM | `androidx.biometric` is first-party-adjacent (fits the dependency posture). Interacts with consent gate ordering: lock screen sits *outside* AppGate. Post-parity candidate — if user-facing demand shows, this borders on table stakes for the category. |
| **Recents-thumbnail privacy (without blocking screenshots)** | `Activity.setRecentsScreenshotEnabled(false)` (API 33+) hides the app-switcher preview of symptom screens while still letting users screenshot their *own* data for their doctor — the privacy win of FLAG_SECURE without its user-hostility. | LOW | Elegant middle path in the FLAG_SECURE debate (see Anti-Features). Verify exact API surface/behavior on target devices during implementation. Below API 33, accept the thumbnail or pair with the app lock. |
| **Per-permission grant awareness used *gently*** | Health Connect (unlike HealthKit) tells the app which read permissions are granted. Locked product stance: same graceful-denial UX as iOS — but the extra signal can quietly skip pointless re-reads and make the import status screen more truthful ("Sleep: connected · Nutrition: not connected") without ever nagging. | LOW | Already anticipated in plan Section 1 notes. A small honesty *upgrade* over iOS, aligned with the brand. |
| **Direct-share / share-sheet niceties for the PDF report** | Correct MIME type + a preview title makes the report land cleanly in the user's mail/messaging target — small polish that reads as native quality. | LOW | Part of Phase 7 acceptance polish, not a separate feature. |
| **App-shortcut ("Log today") static shortcut** | Long-press launcher shortcut straight to the Daily Check-In — the lightweight Android cousin of a widget, near-zero maintenance. Must still land on the consent gate when unconsented. | LOW | Post-parity nicety. A full Glance widget is deferred (see below). |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Exact-alarm reminders (`SCHEDULE_EXACT_ALARM`)** | "Remind me at exactly 20:00" | Denied by default since Android 14; Play policy restricts the permission's justification to alarm-clock/calendar-class apps — a health tracker requesting it invites review friction; battery cost; users must flip a special system toggle. | Inexact `AlarmManager` window or WorkManager periodic work; a check-in reminder drifting a few minutes is imperceptible. |
| **Blanket `FLAG_SECURE` (screenshot blocking app-wide)** | "Health data must never be screenshotted" — feels privacy-serious | User-hostile for *this* app: users legitimately screenshot their own charts to show clinicians; blocks casting/accessibility screen tools; research on reproductive-health apps shows the real privacy failures are third-party sharing/tracking (which this app has none of), not screenshots. Blanket FLAG_SECURE protects the app *from its own user*. | Recents-thumbnail hiding (API 33+) + optional biometric app lock. If any single screen ever warrants FLAG_SECURE, scope it per-window, never app-wide. |
| **Background Health Connect sync (foreground service / `READ_HEALTH_DATA_IN_BACKGROUND`)** | "Import should just always be up to date" | Extra sensitive permission with its own Play justification; foreground-service notification noise; iOS parity imports on app-open and that shipped fine. | Refresh on app-open / import-screen visit, exactly like iOS. Revisit only on real user demand. |
| **Material You dynamic color theming** | Android users increasingly expect wallpaper-derived theming | Bloom token colors are brand *and* chart-semantic (mark colors carry meaning in the honesty rendering); dynamic color would recolor them arbitrarily and break cross-platform product identity. | Static Bloom Material 3 color scheme; explicitly do not call `dynamicColorScheme()`. Light/dark variants of Bloom tokens instead. |
| **Relying on Auto Backup for health data** | Zero-code backup "for free" | Opaque, Google-account-tied copy of special-category data with no deliberate consent moment; size-capped; already rejected in the plan. | Backup rules exclude the DB; the encrypted SAF export is the one deliberate, user-controlled backup surface. |
| **Notification content that reveals health state** | Richer reminders "feel smarter" | Lock-screen notifications are semi-public; any symptom/flare wording in a notification leaks condition info to shoulder-surfers — and flare push is *already forbidden* by the carried-over invariant. | Generic, audited reminder copy ("Time for your daily check-in"); reminder code physically separate from flare code so the structural no-flare-notification scan stays clean. |
| **Google Fit APIs as an HC fallback** | Older devices coverage | Deprecated; HC is the only sanctioned broker going forward; adds a dead-end integration. | HC-only, with the Android 13- "install Health Connect from Play" path. |
| **In-app "sync to my Drive" convenience button (appDataFolder)** | Users ask for effortless backup | Requires Google Sign-In/OAuth — collides with the no-accounts posture; already deferred by locked decision. | SAF export → user can pick their own Drive as destination themselves, no OAuth, no account surface. |

## Feature Dependencies

```
HC rationale Activity/alias (manifest)
    └──required-by──> HC permission request (Phase 5)
                          └──required-by──> Play Health apps declaration (Phase 8)

Backup rules (exclude DB) ──lands-with──> Room schema (Phase 2)

Edge-to-edge insets + predictive back ──land-with──> Compose shell (Phase 3)
    └──retrofit-cost──> every later screen if skipped

POST_NOTIFICATIONS request ──triggered-by──> reminders toggle (Phase 4)
    └──requires──> notification channel (Phase 4)
    └──kept-separate-from──> flare code (no-flare-notification scan)

Chart TalkBack overlay semantics ──requires──> Canvas chart layer + scrub resolver (Phase 6)
    └──reuses──> gap-honest day resolution (ported ChartScrubSelection logic)

FileProvider + ACTION_SEND ──requires──> PdfDocument report (Phase 7)
SAF "Save as file" for PDF ──reuses──> SAF plumbing from export/import

Biometric app lock (post-parity) ──sits-outside──> consent AppGate (Phase 3 structure)
Recents-thumbnail hiding ──independent──> (any phase after shell)
```

### Dependency Notes

- **Rationale surface before any HC request:** the HC permission screen itself links to it; building the permission flow without it means re-review later. Cheap to do first in Phase 5.
- **Edge-to-edge/predictive back are shell-phase decisions:** both are effectively free when the Phase 3 scaffold is built for them and a cross-cutting retrofit if deferred — the single biggest "do it early" item in this file.
- **Chart a11y depends on the scrub data layer:** the overlay semantics nodes should read from the same gap-honest day-resolution logic as the visual scrub tooltip, so "not logged" vs. "0" is answered once, in one tested place.
- **App lock vs. consent gate:** lock authenticates the *person*, consent gates the *feature*; lock wraps the whole UI including the gate. Keeping them structurally distinct avoids ever composing health UI pre-consent to "show it behind the lock".

## MVP Definition

### Launch With (v1 parity — maps onto the 8 locked phases)

- [ ] All **Table Stakes** rows above — each lands inside its indicated phase, with its enforcing test, per the plan's invariant philosophy
- [ ] Edge-to-edge + predictive-back-clean shell (Phase 3) — Play target-API reality, cheapest now
- [ ] HC rationale surface + availability gating + empty-broker honest states (Phase 5)
- [ ] Inexact daily reminder + just-in-time POST_NOTIFICATIONS + reboot rescheduling (Phase 4)
- [ ] Chart + BodyMap TalkBack semantics with honesty-preserving announcements (Phases 4/6)
- [ ] FileProvider share + SAF save for the PDF; cache cleanup (Phase 7)
- [ ] Backup-rules DB exclusion with assertion test (Phase 2)
- [ ] Truthful Data safety form + Health apps declaration + per-data-type justifications (Phase 8)

### Add After Validation (v1.x)

- [ ] **Biometric/PIN app lock** — add when any user signal asks for it; category norm pressure makes this the first post-parity item
- [ ] **Recents-thumbnail hiding (API 33+)** — small, high-signal privacy polish
- [ ] **Per-permission import status display** — the gentle HC grant-state upgrade
- [ ] **"Log today" static app shortcut** — launcher-level quick entry
- [ ] **`READ_HEALTH_DATA_HISTORY`** — if Phase 5 ships with the ~30-day window and users report missing history

### Future Consideration (v2+)

- [ ] Glance home-screen widget — real maintenance surface; shortcut covers most of the value
- [ ] Google Drive `appDataFolder` sync — the plan's recorded revisitable call; only on real multi-device demand
- [ ] Wear OS anything — out of scope entirely

## Feature Prioritization Matrix

| Feature (Android-specific) | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| HC rationale surface + availability gating | HIGH (gatekeeper) | LOW | P1 |
| Edge-to-edge + predictive back in shell | HIGH (platform-mandated) | LOW now / HIGH later | P1 |
| Inexact reminder + POST_NOTIFICATIONS flow | HIGH | LOW | P1 |
| Backup-rules DB exclusion | HIGH (privacy invariant) | LOW | P1 |
| Chart TalkBack overlay semantics | HIGH | HIGH | P1 |
| FileProvider PDF share + SAF save | HIGH | MEDIUM | P1 |
| Data safety form + health declaration | HIGH (ship gate) | LOW | P1 |
| HC history-read decision (window vs. permission) | MEDIUM | MEDIUM | P2 |
| Biometric app lock | MEDIUM–HIGH | MEDIUM | P2 |
| Recents-thumbnail hiding | MEDIUM | LOW | P2 |
| Per-permission import status | LOW–MEDIUM | LOW | P2 |
| App shortcut | LOW | LOW | P3 |
| Widget / Drive appData / Wear | LOW (v1) | HIGH | P3 |

**Priority key:** P1 = required for the parity launch · P2 = post-parity, add on signal · P3 = deferred by decision

## Competitor Feature Analysis

How comparable Android period/health trackers handle the platform-specific surfaces (category norms, not feature envy):

| Surface | Category norm (Clue / Flo / period trackers) | Our Approach |
|---------|--------------|--------------|
| App lock | Optional PIN/biometric lock is standard in the category | Post-parity P2; opt-in, outside the consent gate |
| Health Connect | Read *and often write*; some sync to own backends | Read-only, four types, no backend — the differentiation is the *absence* of collection, made legible via the Data safety form |
| Reminders | Daily inexact notifications, channel-based | Same; plus audited generic copy and the no-flare-push invariant |
| Screenshot policy | Almost none use blanket FLAG_SECURE | None app-wide; recents-thumbnail hiding as polish |
| Data export | Often account-based or PDF-only | Encrypted SAF export (portability + backup + migration in one consent surface) — a genuine differentiator |

## Sources

- Play/Health Connect policy & rationale flow — [Play Console: Android Health Permissions FAQ](https://support.google.com/googleplay/android-developer/answer/12991134), [Publish your health app](https://developer.android.com/health-and-fitness/health-connect/publish), [HC get-started (availability, rationale intents)](https://developer.android.com/health-and-fitness/health-connect/get-started), [HC codelab](https://developer.android.com/codelabs/health-connect), [Health apps Jan 2026 requirements recap](https://myappmonitor.com/blog/google-play-health-apps-update-2026-requirements) — MEDIUM
- Reminders/alarms — [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), [Exact alarms denied by default (Android 14)](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) — MEDIUM
- Edge-to-edge & predictive back — [Android 16 behavior changes (targetSdk 36)](https://developer.android.com/about/versions/16/behavior-changes-16), migration write-ups (Halodoc, Medium) — MEDIUM
- Compose chart accessibility — [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [Eevis Panula: accessible graphs in Compose](https://eevis.codes/blog/2023-07-24/more-accessible-graphs-with-jetpack-compose-part-1-adding-content/) — MEDIUM (official + specialist a11y engineer, corroborating)
- FLAG_SECURE / privacy norms — [Secure sensitive activities](https://developer.android.com/security/fraud-prevention/activities), [Play FLAG_SECURE guidance](https://support.google.com/googleplay/android-developer/answer/14638385), reproductive-health app privacy studies (JMIR/PMC) — LOW–MEDIUM (the "debate" framing is synthesis; the APIs are official)
- Locked project context — `.planning/PROJECT.md`, `.planning/research/ANDROID-PORT-PLAN.md` — HIGH (internal source of truth)

**Verify during implementation (flagged, not assumed):** exact HC history-read window and `READ_HEALTH_DATA_HISTORY` semantics (Phase 5); `setRecentsScreenshotEnabled` behavior on target OEMs; current Play target-API deadline at build time.

---
*Feature research for: Endo-App v2.0 Android port (platform-specific delivery of the iOS-parity feature set)*
*Researched: 2026-07-08*
