---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 04-03-PLAN.md (Phase 4 Wave 3 — HUMAN-UAT authored; all Phase-4 plans executed)
last_updated: "2026-06-08T19:22:07.818Z"
last_activity: 2026-06-08 -- Completed 04-03 (04-HUMAN-UAT device-only protocol authored)
progress:
  total_phases: 12
  completed_phases: 3
  total_plans: 14
  completed_plans: 14
  percent: 31
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-03)

**Core value:** Turn everyday self-tracking into understanding — correlations, cycle/trend patterns, flare warnings, and personalized recommendations that help people with endometriosis make sense of their condition.
**Current focus:** Phase 4 — Private CloudKit Sync

## Current Position

Phase: 4 (Private CloudKit Sync) — EXECUTING
Plan: 3 of 3 (all plans executed; phase verification gate next)
Status: Ready to execute
Last activity: 2026-06-08 -- Completed 04-03 (04-HUMAN-UAT device-only protocol authored)

Progress: [███░░░░░░░] 31%

## Performance Metrics

**Velocity:**

- Total plans completed: 2
- Average duration: ~16 min
- Total execution time: ~0.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 2 | ~32 min | ~16 min |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 1 P03 | ~16min | 3 tasks | 17 files |
| Phase 2 P01 | 35 | 3 tasks | 19 files |
| Phase 02 P02 | ~12min | 2 tasks | 13 files |
| Phase 02 P03 | ~6min | 2 tasks | 3 files |
| Phase 02 P04 | ~22min | 2 tasks | 10 files |
| Phase 03 P01 | ~13min | 3 tasks | 20 files |
| Phase 03 P02 | ~16min | 2 tasks | 2 files |
| Phase 03 P03 | ~25min | 2 tasks | 3 files |
| Phase 03 P04 | ~30min | 3 tasks | 9 files |
| Phase 04 P01 | ~22min | 3 tasks | 9 files |
| Phase 04 P02 | ~7min | 2 tasks | 3 files |
| Phase 04 P03 | ~2min | 1 task + checkpoint | 1 doc |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: All four FOUNDATIONAL pitfalls (cycle/bleeding model, CloudKit append-only schema, App Store 5.1.3(ii) line, GDPR consent + non-causal language) concentrated in Phase 1 — irreversible after launch.
- [Roadmap]: Insights engine is a standalone, value-type-consuming Swift package built against synthetic fixtures (Phases 5–7) BEFORE integration with real data (Phase 8).
- [Roadmap]: HealthKit is additive read-only (Phase 9, after pipeline works on manual data); doctor PDF is late (Phase 10); flare prediction (INST-06) is built LAST (Phase 12), gated by the honesty layer.
- [01-01]: SWIFT_VERSION 6.0 needs no Wave-0 pin (RESEARCH A3 resolved) — built cleanly under Xcode 26.5. Simulator destination for Phase 1 = `platform=iOS Simulator,name=iPhone 16` (deployment target stays iOS 18). EndoAppTests needs `GENERATE_INFOPLIST_FILE: YES` to code-sign.
- [01-02]: `@Model` types nested under `SchemaV1` (referenced as `SchemaV1.DayEntry` etc.); `VersionedSchema.versionIdentifier` must be `static let` and `ConsentStore` must be `@MainActor` under the Swift 6 language mode. Consent flag lives in `UserDefaults` (outside the synced store). Repository surface: `add(_:)` / `allDayEntries()`. Consent injected via `\.consentStore` EnvironmentKey from `AppDependencies`.
- [Phase ?]: [01-03]: Terminology copy choke-point (bleedingNoun/cycleNoun) — all bleeding/cycle copy resolves through it; cycle noun stays neutral 'cycle' (D-08). ClaimsPolicy encodes forbidden verbs + gendered tokens as testable data. TerminologyStore injected via terminologyStore EnvironmentKey. Withdraw consent re-gates AppGate via the @Observable ConsentStore state flip. Phase 1 complete — all six requirement suites GREEN.
- [Phase ?]: [02-01]: A1 closed — SwiftData entity identity is the class name; additively listing the same SchemaV1.* classes under a new SchemaV2 VersionedSchema is lightweight-migration-safe (proven by an on-disk V1->V2 round-trip). MigrationStage.lightweight only (never .custom). New CloudKit-safe models SymptomReading/PainPoint + BleedingEvent.clots. AppDependencies recovers from an unmigratable store instead of crashing.
- [Phase ?]: [02-04]: Accessible discrete-region body map + no-phantom-cycle CycleSummaryView (suppressed/not-enough-data/cycle-day+date RANGE+plain confidence). @Query must be consumed in body to register a SwiftData dependency. Confidence words avoid 'her' substring. All 8 Phase-2 reqs GREEN; full E2E incl. suppressed-mode passes.
- [03-02]: Lifestyle factor logging UI slice landed — six factor Sections in DayLogEditorView (food tags+free-text / activity type+duration+intensity / sleep hours+quality / per-day med taken+PRN / stress / day-mood) wired to the Plan 01 setters. HONESTY held in the UI: stress/mood drafts are Int? with a reveal-on-opt-in 'Log stress' row, so an untouched section reads 'Not logged', never 0 (T-03-06). UI-quirk learnings for downstream factor UI: a SeverityControl's outer accessibilityIdentifier supersedes its inner daylog.severitySlider id (drive stress via daylog.factor.stress.log/.clear instead); a `.segmented` Picker needs an explicit '—'.tag(.none) to represent nil; a container accessibilityIdentifier on a row of buttons must be paired with `.accessibilityElement(children: .contain)` or it collapses the children (the one auto-fix this plan). LIFE-01/02/03 proven end-to-end by testFactorLogging_selectsAndPersists (select + nil!=0 reveal + persistence). Med-taken E2E deferred to Plan 03-04 (no saved meds until 03-03 Settings manage-meds).
- [03-03]: Reminders + manage-meds Settings slice landed (CHCK-02 / LIFE-03). New ReminderSettings is a UserDefaults device-pref store (enabled default OFF, times, quiet-hours as minutes-of-day Ints) — never in the synced store (A2/T-03-12). NotificationScheduler is now injected via EnvironmentValues.notificationScheduler + NotificationSchedulerKey (MainActor.assumeIsolated default), mirroring ConsentStoreKey; the actual .environment(...) injection at the App root is DEFERRED to Plan 04 (deep-link delegate owns the App-root files). JIT discipline: requestAuthorization lives ONLY in enableReminders() (master-toggle ON path); onAppear does a passive authorizationStatus() read for the denied-state UI. Settings Manage-meds add bounds free-text via SwiftDataRepository.maxNoteLength and removes via soft-archive behind .confirmationDialog. Copy note: reused the landed ReminderCopy constants ("Whenever you have a moment, your daily check-in is ready.") instead of the UI-SPEC literal "here's…" which fails the 'her' gendered-token audit. On-device notification DELIVERY + system permission prompt + denied-state + quiet-hours suppression + soft-archive UI are DEFERRED human-verify (auto-approved under AUTO_MODE) — surfaced to the phase verification gate (03-VALIDATION Manual-Only rows).
- [03-04]: Phase 3 COMPLETE. Fast DailyCheckInView sheet (symptoms -> 6 factor sections -> bleeding, nothing mandatory) reachable from a prominent Home CTA (home.dailyCheckInButton) or a reminder notification deep-link. KEY a11y learning carried forward: a `checkin.section.<factor>` id on the `Section` ITSELF collapses child controls into one element — put the section id on the section HEADER `Text` instead (the Plan-02 mood-row collapse, now generalized). Deep-link = an @Observable CheckInRouter injected via EnvironmentKey; the nonisolated UNUserNotificationCenterDelegate flips it on the MainActor (capture a local `let router` to dodge Swift 6 sending-self), and HomeView (built ONLY in AppGate .consented) presents the sheet — so a deep-link can never surface health UI before consent (T-03-14). 'Same as yesterday' prefills sticky context only (meds/sleep/activity); empty confirm writes checkInMarker only; confirm calls scheduler.cancelToday() (D-19b). The .environment(\.notificationScheduler/\.checkInRouter) root injection (deferred by 03-03) is wired here. Integration catch: 03-03's new Settings sections pushed the withdraw button below the fold, silently breaking ConsentFlowUITests on the full suite (03-03 never ran the full UI suite) — fixed with a scroll-into-view. CHCK-01/02 + LIFE-03 proven E2E (incl. mandatory medication-taken persistence); full Phase-3 suite GREEN.
- [04-01]: Private CloudKit ACTIVATED (runtime-gated). A1 HELD — the CloudKit entitlement (private-DB-only iCloud.com.endoapp.ios + CloudKit service + aps-environment=development) does NOT break the unsigned Simulator build; full suite GREEN with no DEVELOPMENT_TEAM, so no pivot to per-configuration gating. SyncMode is now account-aware: SyncMode.resolve(_:) consults an injectable AccountAvailability seam (Sendable, NOT @MainActor; SystemAccountAvailability reads FileManager.default.ubiquityIdentityToken synchronously — D-03/P-5, never the async accountStatus, never logs the token). makeModelContainer takes syncMode: SyncMode = .localOnly as its FIRST param; the composition root (AppDependencies) RESOLVES the mode from the seam BEFORE building the container (P-2) and injects \\.accountAvailability for SettingsView (04-02). KEY P-2 CATCH: once the entitlement ships in the target, ModelConfiguration(schema:url:) (the url: overload + SchemaMigrationTests' direct constructions) silently INFER .automatic CloudKit and mis-activate sync on local-only stores — must pass cloudKitDatabase: .none EXPLICITLY on every local path (dropped 14 CloudKit mirroring errors → 0). DEVELOPMENT_TEAM stays a COMMENTED placeholder in project.yml (D-02 user-setup). The 4 real multi-device DATA criteria are device-only → routed to 04-03 HUMAN-UAT.
- [04-03]: Phase-4 plans all executed. 04-HUMAN-UAT.md authored — the device-only protocol for the four DATA criteria CI cannot reach: 5 scenarios (DATA-02 sync-no-login / DATA-03a offline-reconcile-LWW / DATA-03b reinstall-restore / DATA-04 delete-purge / sign-out grace), each with PASS/FAIL boxes; one-time prerequisites (set DEVELOPMENT_TEAM in project.yml, deploy CloudKit schema Development→Production, P-3); a paste-ready 5.1.3(ii) review note (P-4); and the P-1 sign-out data-loss caveat documented honestly + an acknowledgement box (accepted for v1, custom CKSyncEngine deferred). NO fabricated automated sync tests (D-16) — the negative grep gate (XCUIApplication/@Test) is satisfied. DATA-02/03/04 stay 'code-complete + HUMAN-UAT pending' in REQUIREMENTS traceability (not fully checked) — the automatable scope is GREEN; the device run + P-1 acknowledgement are surfaced to the user at the phase verification gate. Blocking human-verify checkpoint auto-approved under AUTO_MODE. Docs-only plan → no build/test ritual, no regression to the 84/84 suite.
- [04-02]: Sync state surfaced + DATA-04 local half closed. SettingsView reads \\.accountAvailability (injected by 04-01) for a subtle, NON-blocking "iCloud sync: On/Off" line (settings.sync.status) — no spinner/toggle/conflict UI (D-12), and never surfaces the token/account id (T-04-07); the footer states the private-iCloud posture (D-13). PrivacyPolicyText was VERIFY-ONLY (left unchanged): the private-iCloud note + every PrivacyPolicyTests phrase already present; the optional P-1 sign-out sentence was NOT added (avoids needless regression risk against the phrase asserts). KEY DATA-04 FIX (TDD RED→GREEN): deleteDay previously cascaded symptom/pain/bleeding/dayEntry but ORPHANED the Phase-3 FactorReading rows — a real bug, since an orphan would still mirror to the private CloudKit DB after a delete (GDPR Art.17 / T-04-06). RED test logged a FactorReading + asserted zero remain after deleteDay (failed); fix adds `for reading in try factorReadings(on: day) { context.delete(reading) }` (hard delete, D-09/D-10). CRITICAL GUARD (T-04-08): a day-delete must NOT over-delete non-day-scoped definitions — a guard test proves a saved Medication definition survives (TreatmentState likewise untouched). Full suite GREEN (84/84). The device-only cloud-purge proof (reinstall shows nothing) stays routed to 04-03 HUMAN-UAT.
- [03-01]: Phase-3 data spine landed — unified FactorReading @Model (kindRaw + valueRaw/amount/level/note columns) discriminates all six factor kinds; Sendable FactorDay snapshot is the insights seam with nil!=0 honesty (missing stress/mood row -> nil; logged level-0 -> 0). Additive SchemaV3 + lightweight v2toV3 migration (A1 closed for Phase-3 models). Medication is a soft-archive definition; per-day "taken" is a .medication FactorReading keyed by med-id. KEY DEVIATION: quiet-hours uses a QuietHours{start,end} value type, NOT ClosedRange<DateComponents> — a midnight-crossing window (start>end) traps ClosedRange's invariant. Reminder copy must avoid the 'her' substring ('here's' fails the gendered-token audit). NotificationScheduler fatigue logic is pure free functions (isInQuietHours/shouldFireToday); UN calls are the thin shell. Local notifications need NO entitlement (project.yml unchanged).

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: App Store 5.1.3(ii) private-CloudKit-health-storage interpretation is reviewer-dependent — the one genuine go/no-go. De-risk via throwaway TestFlight + pre-submission review note; keep a local-only fallback. (Research flag — HIGH impact.)
- [Phase 7]: Correlation statistical rigor (min-sample, multiple-comparison correction, confounding thresholds) are unsettled design decisions — prototype against synthetic + early real data.
- [Phase 12]: Flare prediction is genuinely hard, noisy, and brushes the MDR line — warrants dedicated research before building.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Phase 4 device-UAT | Run DATA-02/03/04 on ≥2 real devices (same iCloud account) per 04-HUMAN-UAT Part B | Pending user (phase gate) | 04-03 |
| Phase 4 setup | Set DEVELOPMENT_TEAM + deploy CloudKit schema Development→Production (04-HUMAN-UAT Part A) | Pending user (before release) | 04-03 |
| Phase 4 caveat | Explicit P-1 iCloud sign-out data-loss acknowledgement for v1 | Auto-approved under AUTO_MODE; user-confirm at phase gate | 04-03 |
| Phase 4 review | Paste 5.1.3(ii) review note into App Store Connect (04-HUMAN-UAT A4) | Pending (before submission) | 04-03 |

## Session Continuity

Last session: 2026-06-08T18:21:23Z
Stopped at: Completed 04-03-PLAN.md (Phase 4 Wave 3 — HUMAN-UAT authored; all Phase-4 plans executed)
Resume file: None
