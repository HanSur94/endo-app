# Phase 20: Tracking Screens - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md + FEATURES.md; UI direction fixed by the shipped iOS tracking screens)

<domain>
## Phase Boundary

A user can log a full day — symptoms, pain map, bleeding, lifestyle factors, medications — as fast as on iOS, with nothing mandatory and absence kept honest (absence ≠ 0, never a phantom 0). Delivers ATRK-01..05: the fast Daily Check-In flow (fills the Phase-19 Log tab), the Day Log editor + the accessible BodyMap pain map (per-region TalkBack semantics, ≥48dp targets), medication management + treatment state, and a daily check-in reminder (inexact scheduling only, just-in-time POST_NOTIFICATIONS, reboot-surviving, reminder code physically separate from any flare code). Writes go through the Phase-18 consent-gated `EndoRepository`. This phase makes the Log tab real; Insights/Explore remain Phase 22.

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §4 (Phase 4 deliverables) + §5 (no-flare-notification invariant); Android UX norms in [.planning/research/FEATURES.md](../../research/FEATURES.md) (reminders/notifications, TalkBack); pins in [.planning/research/STACK.md](../../research/STACK.md). Reuse Phases 17–19. Do not re-open. Key decisions:

### Screens (fill the Log tab)
- **Daily Check-In**: port the iOS `DailyCheckInView` fast-logging flow (symptoms → the six factor sections → bleeding, NOTHING mandatory). "Same as yesterday" prefills sticky context ONLY (never symptoms/bleeding); an empty confirm records a checked-in adherence marker WITHOUT inventing data; logging auto-suppresses today's reminder. Reachable from a prominent Log-tab CTA (and later a reminder deep-link → the Phase-19 CheckInRouter already lands on the gate; wire the consented target here).
- **Day Log editor**: port `DayLogEditorView` — per-day symptom severities, bleeding, factors, notes — with the **BodyMap** pain map. The BodyMap is the accessible human-silhouette map (iOS shipped a `Path` silhouette with one ≥44pt hotspot per `BodyRegion`; on Android use a Compose `Canvas`/vector silhouette with one focusable ≥48dp hotspot per `BodyRegion`, each carrying a TalkBack contentDescription/semantics — the non-visual path is a first-class requirement, mirroring the iOS a11y bodymap work).
- **Severity control**: port `SeverityControl` (the discrete severity picker) with proper touch targets + TalkBack.
- **Medications / Settings**: medication add/archive + treatment state (suppressed/…); a Settings surface that also hosts the reminder toggle and the consent-withdrawal control (the Phase-19 temp withdraw affordance graduates here). Reminders + Settings live in a Settings screen reachable from the shell.

### Honesty (absence ≠ 0)
- Untouched optional sections read as "not logged" everywhere — never 0 — through full UI round trips (log → persist via EndoRepository → reload). A skipped symptom is nil, not severity 0; a logged severity 0 is a real 0. This is the UI-side carry of the engine/persistence honesty invariant and must be test-proven (round-trip test per screen).

### Reminders (inexact, no alarms)
- Daily check-in reminder uses INEXACT scheduling only — NEVER `SCHEDULE_EXACT_ALARM` (Play restricts it to alarm-clock apps; a health tracker requesting it invites review friction). Use `WorkManager` (or inexact `AlarmManager` per the plan) for the daily fire; reschedule on `BOOT_COMPLETED`. Request `POST_NOTIFICATIONS` JUST-IN-TIME when the user enables the reminder (Android 13+ runtime permission), gracefully handle denial. One notification channel. Tapping the notification deep-links to Daily Check-In via the CheckInRouter (landing on the gate first — consent-by-construction holds).
- CRITICAL invariant (from §5): the reminder/notification code is the ONLY notification surface and is PHYSICALLY SEPARATE from any flare/insights code. There are deliberately NO flare/alarm notifications. A structural test scans that flare-related sources contain no notification/alarm APIs (this guard may be seeded here or in Phase 22; ensure the reminder package is cleanly separable). Do NOT add POST_NOTIFICATIONS usage anywhere near flare logic.

### Copy discipline (from Phases 18–19)
- Every new user-facing string (check-in, day-log, bodymap region labels, severity, meds, reminder, settings) registered in the CopyAudit `AuditableCopy`/`CopyAuditRegistry` IN THE SAME CHANGE; curated strings dodge ClaimsPolicy forbidden SUBSTRINGS (her⊂here/there, use, start, …); framing stays "wellness tool, not medical advice." BodyRegion labels must be neutral/non-gendered.

### Manifest / permissions
- Add ONLY `POST_NOTIFICATIONS` (runtime, Android 13+) and `RECEIVE_BOOT_COMPLETED` if reboot-reschedule needs it. Still NO INTERNET, no health WRITE, no `SCHEDULE_EXACT_ALARM` — the Phase-19 ManifestPermissionsTest must be extended to ALSO assert no INTERNET / no exact-alarm / no health-WRITE still holds after these additions (allow POST_NOTIFICATIONS + RECEIVE_BOOT_COMPLETED explicitly; forbid the rest).

### Claude's Discretion
- Exact Compose screen decomposition + package layout under `com.endoapp.app.ui.tracking` / `.settings` / `.reminders`, the BodyMap silhouette drawing approach (Canvas Path vs vector), the Daily Check-In step vs single-scroll layout, and WorkManager-vs-inexact-AlarmManager for the reminder — Claude's discretion guided by the iOS screens + FEATURES.md. Prefer WorkManager for the reminder (survives reboot, no exact-alarm need).
- Whether screens use ViewModels or plain state holders reading EndoRepository Flows — discretion; keep writes consent-gated through EndoRepository.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- iOS tracking: `Features/CheckIn/DailyCheckInView.swift`, `Features/DayLog/DayLogEditorView.swift`, `BodyMapView.swift`, `SeverityControl.swift`, `App/CheckInRouter.swift`.
- iOS BodyMap accessible silhouette (quick task 260615-erj): `Path` silhouette + one ≥44pt accessible hotspot per `BodyRegion`, SYMP-02 contract, `bodymap.*` a11y ids preserved.
- Medications/treatment: the Medication + TreatmentState models (Phase 18 entities exist), iOS meds UI.
- Reminders: `Reminders/NotificationScheduler.swift` (UNUserNotification) + `ReminderSettings.swift` — daily check-in reminders; deliberately NO flare/alarm notifications (iOS `FlareNoNotificationTests`).
- Settings/consent: `Features/Settings/SettingsView.swift`.

### Established from Phases 17–19 (reuse)
- `EndoRepository` (consent-gated; DAOs for all entities incl. Medication/TreatmentState; `daySnapshots()`), `ConsentStore`/`ConsentState`, `BloomTheme`/`LocalBloomColors`/`BloomComponents`/`BloomNavigationBar`, the 3-tab `ConsentedShell` with a Log-tab placeholder to replace, `CheckInRouter` (deep-link flag landing on the gate), the `CopyAudit`/`AuditableCopy` harness, `ManifestPermissionsTest`, `BloomGlyphs`/`BloomTabIcons`. Domain enums (SymptomType 11, BodyRegion 10, FoodTag, ActivityType, FlowIntensity, etc.) in `:insights`. Android SDK (platform 36) installed; `./gradlew :app:assembleDebug` builds real; Robolectric+Compose JVM test lane established.

### Integration points
- Fills the Phase-19 Log tab placeholder; adds the reminder deep-link target behind the gate. Phase 21 (Health Connect) adds imported-data provenance that flows through the SAME EndoRepository/day-log; Phase 22 reads the logged data for insights/charts. The reminder package must stay physically separate from Phase-22 flare code (no-flare-notification invariant).

</code_context>

<specifics>
## Specific Ideas

Two signature carry-overs: (1) the accessible BodyMap — one focusable ≥48dp hotspot per BodyRegion with TalkBack semantics (the non-visual path is required, not optional), and (2) the no-flare-notification separation — the reminder is the ONLY notification surface and must be physically isolated so the Phase-22 structural scan stays clean. Absence ≠ 0 proven by per-screen round-trip tests.

</specifics>

<deferred>
## Deferred Ideas

- Insights dashboard + Explore charts (Insights/Explore tabs) → Phase 22.
- Health Connect import UI + provenance → Phase 21.
- The flare warning + its no-notification structural scan proper → Phase 22 (this phase only ensures the reminder package is cleanly separable and adds no flare code).
- Encrypted export/import UI → Phase 23 (Settings may host the entry later).
</deferred>
