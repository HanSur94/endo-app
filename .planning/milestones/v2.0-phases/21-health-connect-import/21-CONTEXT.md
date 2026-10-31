# Phase 21: Health Connect Import - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md §4 + FEATURES.md/PITFALLS.md; the HealthKit import is the iOS parity source)

<domain>
## Phase Boundary

Users who already track in Health Connect avoid double-logging — read-only import of menstruation, sleep, exercise, and nutrition, honestly gated at every availability tier, never blocking manual logging, and the app NEVER writes health data. Delivers AHC-01..04: three-state availability gating, BOTH permission-rationale declarations (Android 13- activity AND 14+ alias), the read-only client for the four mapped record types, `ImportedSample`-equivalent provenance rows, manual-wins/nil-honest merge, idempotent re-import, and the deliberate handling of the 30-day history window. Imported data flows through the SAME Phase-18 `EndoRepository`/`ImportedSample` + `SnapshotProjector` (manual-wins merge already ported). Adds a "Connect Health data" entry reachable from Settings/Log.

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §4 (Phase 5) + §5 (read-only invariant); Health Connect specifics + pitfalls in [.planning/research/FEATURES.md](../../research/FEATURES.md) + [.planning/research/PITFALLS.md](../../research/PITFALLS.md) + [.planning/research/STACK.md](../../research/STACK.md) (androidx.health.connect:connect-client 1.1.0). Reuse Phases 17–20. Do not re-open. Key decisions:

### Health Connect client (read-only)
- Dependency: `androidx.health.connect:connect-client` 1.1.0 (add to the catalog — first-party androidx, catalog audit stays green). Read the four mapped record types ONLY: menstruation flow (`MenstruationFlowRecord`/`MenstruationPeriodRecord`), sleep (`SleepSessionRecord`), exercise (`ExerciseSessionRecord`), nutrition energy (`NutritionRecord` dietary energy). Map to the iOS HealthDomain → `ImportedSample` fields (sleep = SUM of samples → coarse band; workout = longest-type per the Phase-18 `ImportedSignals` rules already ported; menstruation → flow; nutrition → ateThatDay). NO write permissions of ANY kind.
- The read-only invariant is auditable: the manifest declares ONLY `android.permission.health.READ_*` for the four types and ZERO `WRITE_*`. Extend the Phase-19/20 ManifestPermissionsTest to assert zero health-WRITE permissions and that only the four READ permissions are present.

### Three-state availability gating
- `HealthConnectClient.getSdkStatus()` → three states: SDK_AVAILABLE (Android 14+ built-in module, or 9–13 with the installable Health Connect app present), SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED (installable, prompt update/install), SDK_UNAVAILABLE (impossible — e.g. API 26–27 where the provider app itself needs Android 9+). Render an honest state for each; MANUAL LOGGING IS NEVER BLOCKED in any of them. Do NOT assume availability — gate per session.

### Dual permission-rationale declarations (hard Play requirement)
- Declare BOTH rationale surfaces or a whole OS generation gets a dead permission flow: (a) the Android 13- rationale Activity handling `ACTION_SHOW_PERMISSIONS_RATIONALE` (`androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE`), and (b) the Android 14+ activity-alias for `VIEW_PERMISSION_USAGE`/the health-permissions rationale intent. A manifest test asserts BOTH are present. Request permissions via the Health Connect permission contract (`PermissionController.createRequestPermissionResultContract`), never cache grants — call `getGrantedPermissions()` per session.

### 30-day history window (deliberate decision — DUE THIS PHASE)
- Health Connect defaults to reading only ~30 days of pre-grant history unless `READ_HEALTH_DATA_HISTORY` is requested. DECISION for this phase (per research recommendation, pick and implement one, with an honest UI label either way): request `READ_HEALTH_DATA_HISTORY` with a clear rationale so a longitudinal cycle history imports; OR, if that permission adds Play-review friction disproportionate to value, honestly LABEL the import window ("importing the last 30 days") on the import screen so the user is never misled. Default recommendation: request `READ_HEALTH_DATA_HISTORY` (the app's value is longitudinal patterns) AND label the window honestly. The planner/executor confirms current semantics against docs at implementation time.

### Import service + provenance (manual-wins / nil-honest)
- Port the iOS import path: read HC records → map to `ImportedSample` provenance rows (unique-indexed by the HC record id — the Phase-18 `ImportedSample.hcRecordId` unique index exists) → merge into the day model with MANUAL-WINS (a user's manual entry always beats imported) and NIL-HONEST (absent → nil, never 0). Idempotent re-import (re-importing the same records changes nothing — dedupe by hcRecordId). Incremental sync via a changes token if available; otherwise a bounded re-read. Deletions in HC propagate (a removed HC record removes its ImportedSample on re-sync). All through the consent-gated `EndoRepository`.

### UX (connect + import states)
- A "Connect Health data" entry (Settings and/or Log) → a rationale-BEFORE-permission screen (explain what's read + why, the read-only/on-device framing) → the HC permission request → an import screen showing progress + the honest window label + "from Health Connect" provenance labels on imported data. Graceful denial: if the user denies, the reminder... no — if the user denies HC read, manual logging is unaffected and the screen explains gently (no nag). Availability-unavailable states explained honestly (install/update prompt where applicable).

### Copy discipline
- Every new user-facing string (connect, rationale, import states, provenance labels, window label) registered in CopyAudit in the same change; dodge ClaimsPolicy forbidden SUBSTRINGS (should/use/her/start/treat/stop/take — watch "start"/"use" especially in HC copy); neutral/non-diagnostic; "informational, on-device, read-only" framing.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.health`, whether the import runs in a coroutine/WorkManager one-shot vs foreground, the connect-screen decomposition, and the incremental-sync token strategy — Claude's discretion guided by the iOS HealthImportService + FEATURES.md/PITFALLS.md. Keep it read-only and consent-gated.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- iOS Health: `Health/HealthDomain.swift` (the 4 domains → HK types), `HealthSampleMapping.swift` (HK sample → ImportedSample: flow, sleep hours/quality, activity type/minutes, ateThatDay), `HealthImportService.swift` (import + merge), `HealthAnchorStore.swift` (anchored incremental queries), `SystemHealthDataReading.swift`/`HealthDataReading.swift`/`FixtureHealthDataReading.swift` (injectable seam + fixture), `HealthConnectSection.swift`/`HealthRationaleView.swift`/`HealthSourceBadge.swift`/`HealthConnectCopy.swift`.
- iOS read types: menstrualFlow, sleepAnalysis, workouts, dietaryEnergyConsumed. iOS is READ-ONLY (no write/share entitlement) — carry that exactly.

### Established from Phases 17–20 (reuse — DO NOT rebuild)
- Phase 18 already ported `ImportedSample` (Room entity with `hcRecordId` unique index), `ImportedSignals` (sleep-SUM→band, workout-longest-type), `HealthDomain` (app-local, 4 raws), and the manual-wins/nil-honest merge in `SnapshotProjector`. This phase produces the ImportedSample ROWS (from HC) that feed that already-built merge.
- `EndoRepository` (consent-gated), the injectable-seam test pattern (mirror iOS FixtureHealthDataReading → a fake HC reader for JVM tests), `ManifestPermissionsTest` (extend for READ health perms + assert no WRITE), CopyAudit harness, Bloom UI + Settings screen (add the connect entry), the injectable-seam threading pattern (AppDependencies → LogTabHost/Settings). Android SDK installed; `./gradlew :app:...` builds real; Robolectric+Compose JVM lane.

### Integration points
- Feeds the SAME EndoRepository/ImportedSample/SnapshotProjector → Phase 22 reads imported+manual data uniformly for insights/charts. The connect entry sits in Settings (Phase 20) / Log.

</code_context>

<specifics>
## Specific Ideas

Read-only is the signature invariant — the manifest itself is the auditable artifact (only READ_* health perms, zero WRITE_*). The dual-rationale-declaration and the 30-day-history decision are the two Play-critical items. Reuse the Phase-18 ImportedSample/ImportedSignals/merge rather than rebuilding — this phase is the HC CLIENT that produces the rows. Use an injectable fake HC reader (mirror iOS FixtureHealthDataReading) so import/merge/idempotency/denial are JVM-testable without an emulator or a real HC provider.

</specifics>

<deferred>
## Deferred Ideas

- Insights/charts over the imported data → Phase 22.
- Encrypted export includes imported rows via the Phase-18 format contract → Phase 23 (implementation).
- Any real-device HC provider behavior (actual permission dialog, real provider data) → device-gated manual verification.
</deferred>
