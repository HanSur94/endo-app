# Phase 4: Private CloudKit Sync - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas presented as proposal tables; user accepted all recommendations.

<domain>
## Phase Boundary

Turn the local-only SwiftData store into **private iCloud (CloudKit) sync** across the user's own devices — surviving offline edits and reinstall-from-iCloud without data loss, and truly purging the CloudKit private DB on delete — **with no account/login**. The schema (Phases 1–3) was deliberately built CloudKit-safe (append-only, relationship-free, optional/defaulted, app-generated UUIDs, no `.unique`) and the container already has a pre-wired `SyncMode.private(...)` branch, so this phase is **mostly configuration + account-state handling + verification**, not new data modeling.

In scope: DATA-02 (sync across devices, no account), DATA-03 (offline reconcile, reinstall restore, last-writer-wins on append-only, no lost records), DATA-04 (deletes purge the private DB).

Out of scope (later/never): HealthKit import (Phase 9); partner/family sharing & CloudKit *shared* database (v2 — requires moving off private-DB-only); the insights engine (Phases 5–8); any custom sync engine.

**Hard reality:** real sync (multi-device, offline reconcile, reinstall restore, delete-purge) **cannot be verified in CI/XCUITest** — it needs a real iCloud account + ≥2 devices and a configured Apple Developer team. This phase is automatable up to "configures + builds + launches green local-only"; the 4 success criteria are inherently **human_needed**.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — CloudKit activation & signing (the feasibility decision)
- **D-01:** **Code-complete but runtime-gated.** Add the iCloud (CloudKit) + Background-Modes (remote-notification) capabilities/entitlements (project.yml + an entitlements file). But sync activates ONLY when an iCloud account is available at runtime AND the entitlement is live (a real Team ID is set). Default + tests + CI stay **local-only**, so the autonomous build/tests stay green and **no signing is required to build**. The user flips on real sync by setting `DEVELOPMENT_TEAM`.
- **D-02:** Keep the pre-reserved container id **`iCloud.com.endoapp.ios`** (already in `ModelContainer+Config.swift`), flagged provisional (finalize at Apple Developer enrollment — consistent with the D-09 placeholder posture). `DEVELOPMENT_TEAM` left as a documented placeholder the user fills in.
- **D-03:** **Runtime local-vs-cloud detection.** Make `SyncMode.current` dynamic: use `.cloudKit` when an iCloud account is available (`FileManager.default.ubiquityIdentityToken != nil` and/or `CKContainer.accountStatus == .available`), else `.localOnly`. **Never crash when signed out.** Provide a test override to force `.localOnly` deterministically.
- **D-04:** Add **Background Modes → remote-notification** + the CloudKit entitlement (needed for silent push-driven sync). Inert without a team — acceptable.

### Area 2 — Conflict resolution & integrity (DATA-03)
- **D-05:** Rely on **NSPersistentCloudKitContainer last-writer-wins** (the SwiftData+CloudKit default). The append-only event model means most "conflicts" are independent records that both survive; only mutable fields (`DayEntry.note`, edited severities, taken toggles) are LWW. **No custom merge logic.**
- **D-06:** Offline reconcile via CloudKit's **built-in offline queue + sync-on-reconnect**. No custom outbox.
- **D-07:** Reinstall restore is **automatic** (CloudKit mirrors the private DB back on fresh sign-in). No restore code.
- **D-08:** **CloudKit schema deployment is a manual dev step** (Xcode "Initialize CloudKit Schema" from the Development environment, or first-run auto-creates record types in Development). The schema is already CloudKit-safe. Document the exact steps for the user.

### Area 3 — Delete propagation (DATA-04)
- **D-09:** **Hard delete**, relying on the relationship-free model: `deleteDay` (and individual record removals) delete each record directly; with **no relationships** (day-linkage by calendar date) there are no `.deny`/cascade traps, so CloudKit mirrors each delete to the private DB. Verify no orphaned records remain after a day-delete; the reinstall-shows-nothing check is a manual test.
- **D-10:** **No tombstones** — real deletion propagates; tombstones would be privacy debt.

### Area 4 — Account-state UX & error handling
- **D-11:** App stays **fully functional local-only** when there's no iCloud account / iCloud disabled. No blocking, no error wall — preserves the free / no-account promise.
- **D-12:** **Minimal sync status** — a subtle "iCloud sync: On/Off" line in Settings (no spinners, no conflict UI, no dashboard; the phase has no UI hint).
- **D-13:** **Privacy framing** — reuse `ClaimsPolicy`/`Terminology`; a short "syncs only to your private iCloud — no third party, no account" note (Settings and/or the privacy-policy text). Reinforces the privacy posture.
- **D-14:** **Local data when sync turns on** — automatic upload/mirror of existing local records to the private DB by NSPersistentCloudKitContainer. No migration code.

### Architecture & testing
- **D-15:** Reuse the existing **`SyncMode` / `makeModelContainer` seam** (Phase 1 D-10). The container already has the `.private(cloudKitContainerIdentifier)` branch; this phase makes `SyncMode.current` account-aware, adds the entitlements, the account-state handling, the sync-status line, and the privacy note.
- **D-16:** **Testing reality.** Automatable (must stay green): container builds + launches local-only with no regression; the account-detection logic is unit-tested via an injected account-availability seam; the entitlements/project config is structurally correct (grep-able); the full existing suite stays green. The 4 DATA success criteria are **human_needed** (manual multi-device) — produce a HUMAN-UAT plan.

### Claude's Discretion
- Exact account-detection API (`ubiquityIdentityToken` vs async `CKContainer.accountStatus`), the `SyncMode` test-override mechanism, the Settings sync-status wording, and the privacy-note placement are left to research/planning.
</decisions>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 4 reqs: DATA-02, DATA-03, DATA-04
- `.planning/ROADMAP.md` — Phase 4 goal + 4 success criteria

### Research
- `.planning/research/ARCHITECTURE.md` — SwiftData/CloudKit private-DB sync, schema constraints
- `.planning/research/PITFALLS.md` — CloudKit-safety rules, signing/entitlement gotchas

### Existing source (the seam this phase activates)
- `Sources/EndoApp/Persistence/ModelContainer+Config.swift` — **the `SyncMode` flag + `.private(cloudKitContainerIdentifier)` branch to make account-aware**
- `project.yml` — XcodeGen spec to add capabilities + entitlements + a `DEVELOPMENT_TEAM` placeholder (note the Phase-1 comment reserving CloudKit for Phase 4)
- `Sources/EndoApp/Resources/Info.plist` — Background Modes (remote-notification) if needed
- `Sources/EndoApp/Persistence/{SchemaV3.swift,Models/*.swift,Repository.swift}` — confirm CloudKit-safety + the relationship-free `deleteDay`
- `Sources/EndoApp/App/AppDependencies.swift` — composition root building the container
- `Sources/EndoApp/Features/Settings/SettingsView.swift` — add the "iCloud sync: On/Off" line
- `Sources/EndoApp/Content/{PrivacyPolicyText,ClaimsPolicy}.swift` — privacy note + copy choke-point
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`SyncMode` / `makeModelContainer(...)`** — the `.private(cloudKitContainerIdentifier)` branch is already wired; container id `iCloud.com.endoapp.ios` is reserved. This phase makes `SyncMode.current` dynamic (account-aware) and supplies the entitlement.
- **Relationship-free, append-only, CloudKit-safe schema** (all 3 phases) — no `.unique`, no relationships, optional/defaulted, app-gen UUIDs — exactly what CloudKit mirroring + LWW + delete-propagation need.
- **`Repository.deleteDay`** — already deletes every child record directly (no cascade traps).
- **`SettingsView`**, **`PrivacyPolicyText`**, **`ClaimsPolicy`/`Terminology`** — for the sync-status line, privacy note, and copy choke-point.
- Build/test: `xcodegen generate && xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 16'` — MUST stay green local-only.

### Established Patterns
- MV + Repository; Swift 6 strict concurrency; feature-flag-via-enum (`SyncMode`) decided in code/UserDefaults, never in the synced store.

### Integration Points
- `ModelContainer+Config.swift` → make `SyncMode.current` account-aware (+ test override).
- `project.yml` → add iCloud (CloudKit) + Background-Modes capabilities, an entitlements file, a `DEVELOPMENT_TEAM` placeholder. Regenerate with `xcodegen`.
- `SettingsView` → "iCloud sync: On/Off" status line.
- `PrivacyPolicyText` → private-iCloud sync note.
</code_context>

<specifics>
## Specific Ideas
- The entire point of Phase 1's `SyncMode` design was this phase — keep it **minimal**: config + account-awareness + graceful degradation + a status line, not a sync engine.
- **The build MUST stay green local-only** (no signing required for CI). Real sync is activated by the user's Team ID and verified on real devices.
- Preserve the **no-account promise**: signed-out users lose nothing — the app is fully usable local-only.
</specifics>

<deferred>
## Deferred Ideas
- HealthKit import → Phase 9.
- Partner/family sharing + CloudKit *shared* database → v2 (requires leaving private-DB-only).
- The insights engine / charts / correlations → Phases 5–8.
- Any custom conflict-merge or outbox engine → not needed (framework defaults suffice for append-only data).
</deferred>

---
*Phase: 4 — Private CloudKit Sync*
*Context gathered: 2026-06-08 (smart discuss, autonomous mode)*
