# Phase 4: Private CloudKit Sync - Research

**Researched:** 2026-06-08
**Domain:** SwiftData + private CloudKit (NSPersistentCloudKitContainer) sync on iOS 18, Swift 6, XcodeGen
**Confidence:** HIGH on the build/signing feasibility crux and schema constraints; MEDIUM on runtime account-state edge behavior (corroborated by Apple forums, not a single official doc).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Code-complete but runtime-gated. Add iCloud(CloudKit) + Background-Modes(remote-notification) capabilities/entitlements (project.yml + an entitlements file). Sync activates ONLY when an iCloud account is available at runtime AND a real Team ID is set. Default + tests + CI stay local-only; no signing required to build. User flips on real sync by setting `DEVELOPMENT_TEAM`.
- **D-02:** Keep container id **`iCloud.com.endoapp.ios`** (already in `ModelContainer+Config.swift`), flagged provisional. `DEVELOPMENT_TEAM` left a documented placeholder.
- **D-03:** Runtime local-vs-cloud detection. Make `SyncMode.current` dynamic: `.cloudKit` when an iCloud account is available (`FileManager.default.ubiquityIdentityToken != nil` and/or `CKContainer.accountStatus == .available`), else `.localOnly`. Never crash when signed out. Provide a test override to force `.localOnly`.
- **D-04:** Add Background Modes → remote-notification + the CloudKit entitlement. Inert without a team — acceptable.
- **D-05:** Rely on NSPersistentCloudKitContainer last-writer-wins. No custom merge logic.
- **D-06:** Offline reconcile via CloudKit built-in offline queue. No custom outbox.
- **D-07:** Reinstall restore is automatic. No restore code.
- **D-08:** CloudKit schema deployment is a manual dev step (Xcode "Initialize CloudKit Schema" / first-run record-type creation in Development, promotion to Production). Document the exact steps.
- **D-09:** Hard delete via the relationship-free model. Verify no orphaned records; reinstall-shows-nothing is a manual test.
- **D-10:** No tombstones (app-level); real deletion propagates.
- **D-11:** App stays fully functional local-only when signed out / iCloud disabled. No blocking, no error wall.
- **D-12:** Minimal sync status — a subtle "iCloud sync: On/Off" line in Settings. No spinners, no conflict UI.
- **D-13:** Privacy framing — reuse `ClaimsPolicy`/`Terminology`; short "syncs only to your private iCloud" note.
- **D-14:** Local data when sync turns on — automatic upload/mirror by NSPersistentCloudKitContainer. No migration code.
- **D-15:** Reuse the existing `SyncMode` / `makeModelContainer` seam.
- **D-16:** Testing reality — automatable (local-only build green, account-detection unit-tested via injected seam, structural grep of entitlements/project); the 4 DATA criteria are human_needed (multi-device). Produce a HUMAN-UAT plan.

### Claude's Discretion
- Exact account-detection API (`ubiquityIdentityToken` vs async `CKContainer.accountStatus`).
- The `SyncMode` test-override mechanism.
- The Settings sync-status wording.
- The privacy-note placement.

### Deferred Ideas (OUT OF SCOPE)
- HealthKit import → Phase 9.
- Partner/family sharing + CloudKit *shared* database → v2.
- The insights engine / charts / correlations → Phases 5–8.
- Any custom conflict-merge or outbox engine.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-02 | Data syncs across the user's own Apple devices via private iCloud (CloudKit), with no account/login | NSPersistentCloudKitContainer private DB via SwiftData `cloudKitDatabase: .private(_:)` (already wired); uses the user's implicit iCloud identity — no app account. Entitlements recipe in §Standard Stack / §Code Examples. |
| DATA-03 | Sync survives multi-device, offline, and reinstall without data loss | Built-in offline queue + sync-on-reconnect (D-06); automatic reinstall mirror-back (D-07); append-only schema makes LWW non-lossy for independent records (D-05). **CAVEAT P-1:** unsynced local edits are wiped on iCloud sign-out (iOS 18, by design). |
| DATA-04 | Deleting data removes it from the private CloudKit store too | Relationship-free hard deletes (`Repository.deleteDay` + per-record `context.delete`) mirror to the private DB as CloudKit tombstones that propagate the delete to other devices and to a clean reinstall. No `.deny`/cascade traps because there are no relationships. |
</phase_requirements>

## Summary

This phase is **~90% configuration + account-state handling + verification, ~10% code**, exactly as CONTEXT.md frames it. The schema (verified below) is already CloudKit-correct, and `makeModelContainer` already has the `.private(cloudKitContainerIdentifier)` branch. The real engineering risk is concentrated in **two places**, both resolved here:

1. **The build/signing feasibility crux (D-01/D-04).** *Confirmed: adding the CloudKit entitlement does NOT break the CI simulator build.* `xcodebuild ... -destination 'platform=iOS Simulator'` does not code-sign — entitlements and provisioning profiles are ignored for Simulator builds. The "requires a development team" error is a **device-build** error only. So the entitlements file can ship now; CI stays green with no `DEVELOPMENT_TEAM`. **However**, there is a second, sharper trap: when the CloudKit *entitlement is present* and the iCloud account is available, SwiftData can attempt to activate CloudKit even when you intend local-only — and a `cloudKitDatabase: .none` configuration paired with a live entitlement has been reported to crash or mis-activate. The runtime-gated design (D-03) must therefore be built so the **account-aware decision happens before container construction**, and the local-only path stays genuinely local.

2. **The runtime account-state edge behavior.** Two non-obvious iOS 18 behaviors shape the plan: (a) `NSPersistentCloudKitContainer` **wipes the local store on iCloud sign-out** by design (privacy) — data reappears on sign-in *if it synced*, but **unsynced local edits are lost**; (b) the account-detection API choice matters — `ubiquityIdentityToken` is the synchronous, launch-safe, main-thread-safe check (returns `nil` when not signed in and even works offline), while `CKContainer.accountStatus` is async and reports `.available` even offline. Use `ubiquityIdentityToken` for the launch-time `SyncMode` decision.

**Primary recommendation:** Add a **hand-written, committed `EndoApp.entitlements`** file referencing `iCloud.com.endoapp.ios` + `aps-environment`, wire it via `CODE_SIGN_ENTITLEMENTS` in `project.yml` (Background Modes via Info.plist `UIBackgroundModes`), make `SyncMode.current` resolve from an **injectable `AccountAvailability` seam** that defaults to `ubiquityIdentityToken`, and keep tests/CI forcing `.localOnly`. Activate real sync = user sets `DEVELOPMENT_TEAM` + runs the documented CloudKit schema-deploy steps. The four DATA success criteria are inherently human-verified on ≥2 real devices.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Local-vs-cloud decision | App launch / composition root (`AppDependencies` → `SyncMode`) | — | Must be resolved synchronously before `ModelContainer` is built; not a UI concern. |
| Account detection | Foundation/CloudKit framework via an injected seam | App | `ubiquityIdentityToken` is a Foundation call; wrap it behind a protocol so tests force a deterministic answer. |
| Sync transport (mirror, offline queue, reconcile, restore) | CloudKit / NSPersistentCloudKitContainer (under SwiftData) | Database/Storage | All automatic — the app writes no CKRecord code (D-05/06/07/14). |
| Delete propagation | Database/Storage (SwiftData `context.delete`) | CloudKit | Hard deletes mirror to the private DB as tombstones; relationship-free model avoids cascade traps. |
| Entitlement / capability provisioning | Build config (project.yml + entitlements file + Info.plist) | — | Inert without a Team; gates activation. |
| Sync-status surfacing | Presentation (SettingsView) | App (account seam) | Read-only "On/Off" line; no blocking, no spinner (D-12). |
| Privacy framing | Content (`PrivacyPolicyText`) | Presentation | Reuse the existing copy choke-point (D-13). |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SwiftData | iOS 18+ | Persistence + private CloudKit mirroring via `ModelConfiguration(cloudKitDatabase:)` | Already in use; the `.private(_:)` branch is pre-wired. First-party, no CKRecord code. [CITED: developer.apple.com/documentation/swiftdata] |
| CloudKit (private DB) | iOS 18 SDK | Sync transport + account detection (`CKContainer.accountStatus`, `CKAccountStatus`) | First-party; user's implicit iCloud identity = no app account. [CITED: developer.apple.com/documentation/cloudkit/ckcontainer] |
| Foundation `FileManager.ubiquityIdentityToken` | iOS 18+ | Synchronous, launch-safe iCloud-availability check | Apple's own recommendation for *launch-time* availability checks (vs async `accountStatus`). [CITED: developer.apple.com/library/archive/qa/qa1935] |

**No new third-party packages.** This phase installs nothing — `## Package Legitimacy Audit` is therefore **N/A** (no external dependencies). Confirmed: the project uses zero SPM/CocoaPods dependencies; everything is first-party Apple frameworks.

### Supporting (build-config, not runtime)
| Artifact | Purpose | When to Use |
|----------|---------|-------------|
| `EndoApp.entitlements` (new, committed) | Declares `com.apple.developer.icloud-container-identifiers`, `com.apple.developer.icloud-services = CloudKit`, `aps-environment` | Always present; inert on Simulator/no-team builds. |
| `UIBackgroundModes` in Info.plist | `remote-notification` for silent-push-driven sync (D-04) | Always present; harmless without a team. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written entitlements file + `CODE_SIGN_ENTITLEMENTS` in `project.yml settings` | XcodeGen `entitlements:` key (auto-generates the file) | The `entitlements:` generator sets `CODE_SIGN_ENTITLEMENTS` for **every configuration** and round-trips through XcodeGen. A hand-written committed file is clearer to audit, grep, and reason about, and avoids surprises. **Recommended: hand-written.** [VERIFIED: XcodeGen ProjectSpec.md] |
| `ubiquityIdentityToken` for the launch decision | `CKContainer.accountStatus` (async) | `accountStatus` is async (can't gate synchronous container construction cleanly) and returns `.available` even offline. `ubiquityIdentityToken` is synchronous, main-thread-safe, `nil` when signed out. **Use `ubiquityIdentityToken` for the gate; optionally surface `accountStatus` for the Settings line.** [CITED: QA1935] |
| NSPersistentCloudKitContainer (via SwiftData) | `CKSyncEngine` (manual) | Manual sync = far more code, custom conflict resolution, custom identity semantics. Out of scope (deferred). Only relevant if the sign-out-wipe behavior (P-1) becomes unacceptable — note it, don't build it. [CITED: developer.apple.com/documentation/cloudkit/cksyncengine] |

**Installation:** No package install. Build/test unchanged: `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'`.

## Package Legitimacy Audit

**N/A — this phase installs zero external packages.** All capability is first-party Apple frameworks (SwiftData, CloudKit, Foundation) already linked. No npm/PyPI/SPM additions. slopcheck not applicable.

## Architecture Patterns

### System Architecture Diagram

```
LAUNCH
  │
  ▼
AppDependencies.init()
  │  resolves ──► AccountAvailability seam (injectable)
  │                  │ default: FileManager.default.ubiquityIdentityToken != nil
  │                  │ test override: forced false  ──► deterministic .localOnly
  │                  ▼
  │            SyncMode.current  ──►  .cloudKit  (token present + entitlement live)
  │                                    .localOnly (token nil OR test override)
  ▼
makeModelContainer(syncMode:)
  │
  ├── .localOnly  ──► ModelConfiguration(cloudKitDatabase: .none)   [CI / signed-out / tests]
  │
  └── .cloudKit   ──► ModelConfiguration(cloudKitDatabase: .private("iCloud.com.endoapp.ios"))
                        │
                        ▼
              NSPersistentCloudKitContainer (automatic, under SwiftData)
                        │
        ┌───────────────┼────────────────────────────┐
        ▼               ▼                            ▼
   write/delete    offline queue              remote-notification push
   local SQLite    (auto reconcile)           (Background Modes)
        │               │                            │
        └──────────────►├◄───────────────────────────┘
                        ▼
            user's PRIVATE iCloud CloudKit DB
                        │  mirror / tombstones (deletes)
        ┌───────────────┴───────────────┐
        ▼                               ▼
   other device                  clean reinstall
   (eventual consistency)        (auto restore from cloud)

UI READ PATH (non-blocking, D-12):
SettingsView ──reads──► AccountAvailability seam ──► "iCloud sync: On/Off" line
PrivacyPolicyText ──► "syncs only to your private iCloud" note (already present)
```

### Recommended Structure (changes only)
```
Sources/EndoApp/
├── Persistence/
│   ├── ModelContainer+Config.swift   # MODIFY: SyncMode.current account-aware + makeModelContainer(syncMode:)
│   └── AccountAvailability.swift      # NEW: protocol + ubiquityIdentityToken impl + test stub
├── Resources/
│   ├── Info.plist                     # MODIFY: add UIBackgroundModes → remote-notification
│   └── EndoApp.entitlements           # NEW (committed): iCloud container + CloudKit + aps-environment
├── App/
│   └── AppDependencies.swift          # MODIFY: resolve SyncMode via the seam; pass to makeModelContainer
├── Features/Settings/
│   └── SettingsView.swift             # MODIFY: add the "iCloud sync: On/Off" status line
└── Content/
    └── PrivacyPolicyText.swift        # OPTIONAL tweak: the private-iCloud note already exists (lines 23-26)

project.yml                            # MODIFY: CODE_SIGN_ENTITLEMENTS + DEVELOPMENT_TEAM placeholder
```

### Pattern 1: Injectable account-availability seam (the testability keystone)
**What:** A protocol returning a synchronous `Bool` (and optionally an async richer status for the UI). The default impl reads `ubiquityIdentityToken`; tests inject a stub forcing `false`. `SyncMode.current` consults this seam instead of calling Foundation directly.
**When to use:** Everywhere `SyncMode` is resolved and wherever the Settings line reads sync state.
**Why:** Mirrors the existing `ConsentStore` / `NotificationScheduler` injection patterns in `AppDependencies`. Makes `SyncMode.current == .localOnly` *deterministic* in CI without an iCloud account, satisfying D-03 + D-16.

```swift
// Source: pattern mirrors existing AppDependencies.swift EnvironmentKey seams
protocol AccountAvailability: Sendable {
    /// Synchronous, launch-safe. true when an iCloud account is signed in.
    var isICloudAvailable: Bool { get }
}

struct SystemAccountAvailability: AccountAvailability {
    var isICloudAvailable: Bool { FileManager.default.ubiquityIdentityToken != nil }
}

/// Test seam: forces a deterministic answer (CI forces false → .localOnly).
struct StubAccountAvailability: AccountAvailability {
    let isICloudAvailable: Bool
}
```

### Pattern 2: Resolve `SyncMode` BEFORE building the container
**What:** `SyncMode` becomes a value computed from the seam, not a hardcoded `.localOnly`. The composition root passes it explicitly into `makeModelContainer(syncMode:)`.
**Why:** Avoids the reported failure mode where a live CloudKit entitlement + a `.none` configuration mis-activate or crash. The decision is made once, up front, and the local-only branch builds a genuinely local store. Keeps the existing `inMemory`/`url` overloads (used by tests) on the `.localOnly` path.

### Anti-Patterns to Avoid
- **Calling `CKContainer.accountStatus` synchronously to gate container construction.** It's async; blocking on it at launch stalls the main actor. Use `ubiquityIdentityToken` for the gate.
- **Leaving `SyncMode.current` hardcoded to `.localOnly` and only flipping it via a compile flag.** Defeats D-03 (runtime detection) and the "graceful when signed out" promise.
- **Adding `.unique`, non-optional/non-defaulted properties, or relationships** to satisfy any new sync need. The schema is already correct; do not regress it.
- **Storing the `SyncMode` flag or any account state inside the synced store.** It lives in code/UserDefaults (D-15) — never in CloudKit.
- **Treating "delete only locally" as sufficient.** The phase's whole point (DATA-04) is that `context.delete` + `save()` propagates to the private DB. Do not add a soft-delete/tombstone field (D-10).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cross-device sync | A custom sync engine / REST backend | NSPersistentCloudKitContainer via SwiftData `cloudKitDatabase: .private(_:)` | Apple-hosted, free, no account; the schema is already compatible. |
| Offline reconcile | A custom outbox/queue | CloudKit's built-in offline queue + sync-on-reconnect (D-06) | Automatic; a hand-rolled outbox would fight the framework. |
| Reinstall restore | Restore-from-backup code | Automatic mirror-back on fresh sign-in (D-07) | CloudKit re-populates the private DB; nothing to write. |
| Conflict merge | Custom merge logic | Framework last-writer-wins (D-05) | Append-only model means most "conflicts" are independent records that both survive. |
| Delete propagation | Tombstone fields in the model | Hard `context.delete` → CloudKit tombstone (D-09/D-10) | CloudKit emits its own tombstone for the deleted record; an app-level tombstone is privacy debt and redundant. |
| iCloud-availability check | Reachability/network polling | `FileManager.ubiquityIdentityToken` (sync) | Apple's recommended launch-time check; works offline. |
| Entitlements file generation | Hand-editing the `.xcodeproj` (it's gitignored, regenerated) | A committed `.entitlements` file referenced from `project.yml` | XcodeGen regenerates the project; the entitlements file must be a committed source, not project-embedded. |

**Key insight:** Every one of the four DATA criteria is satisfied by *framework defaults on a correct schema*. The phase adds **zero sync algorithm code** — it adds configuration (entitlements/capabilities), one account-aware decision, one Settings line, and a privacy note.

## Runtime State Inventory

This is a config/code-activation phase, **not** a rename/refactor/migration. Most categories are N/A, but the sync activation introduces real runtime-state considerations worth recording:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Local SwiftData store (`default.store` in Application Support). On first `.cloudKit` activation with a real team, NSPersistentCloudKitContainer **mirrors existing local records up** to the private DB automatically (D-14). | None — automatic. Verify on device that pre-existing local logs appear on a second device. |
| Live service config | CloudKit **schema must be deployed** Development→Production in CloudKit Console before App Store release, or real-user sync fails silently (D-08). This config lives in Apple's console, NOT in git. | Manual: document the deploy/promote steps (below). |
| OS-registered state | `aps-environment` (push) + Background Modes `remote-notification` register the app for silent CloudKit push at install time on a real device. Inert on Simulator/no-team. | None in CI; activates with a real team. |
| Secrets/env vars | `DEVELOPMENT_TEAM` is a documented placeholder the user fills in (D-02). No secret value committed. | Document the placeholder; user supplies their Team ID. |
| Build artifacts | The generated `*.xcodeproj` is gitignored and regenerated by `xcodegen`. The new `EndoApp.entitlements` is a **committed source** that XcodeGen wires via `CODE_SIGN_ENTITLEMENTS`. | After editing `project.yml`, run `xcodegen generate` so the entitlements reference lands in the regenerated project. |

**Verified:** No external datastore, no n8n/Windows Task Scheduler/SOPS analogues — this is a self-contained iOS app. The only "live service config not in git" is the CloudKit Console schema deployment (D-08), captured above.

## CloudKit Schema Deployment Steps (D-08 — document for the user)

These are **manual, one-time, real-device/real-team** steps. They cannot run in CI and are part of the HUMAN-UAT plan, not an automatable task.

1. **Set your Team.** In `project.yml`, set `DEVELOPMENT_TEAM: <YOUR_TEAM_ID>` (and confirm the bundle id `com.endoapp.ios` / container `iCloud.com.endoapp.ios` are registered in your Apple Developer account, or let Xcode auto-create the container). Run `xcodegen generate`.
2. **Run on a real device or signed Simulator** with a signed-in iCloud account, under the **Development** environment (the default for debug builds). SwiftData/NSPersistentCloudKitContainer auto-creates the CloudKit record types in Development on first sync as records are written.
3. **(Recommended, more reliable) Initialize the schema explicitly.** In a debug-only code path, call `try container.initializeCloudKitSchema()` once (NSPersistentCloudKitContainer API) to force-create all record types from the model up front, rather than waiting for lazy creation. [CITED: developer.apple.com/documentation/coredata/nspersistentcloudkitcontainer/initializecloudkitschema] Alternatively, in Xcode use the CloudKit schema tooling exposed for the container.
4. **Verify in CloudKit Console** (icloud.developer.apple.com): open the container `iCloud.com.endoapp.ios`, **Development** environment, confirm all record types (`CD_DayEntry`, `CD_FactorReading`, etc. — CloudKit prefixes entity names with `CD_`) and fields exist.
5. **Promote to Production.** In CloudKit Console, **Deploy Schema Changes** from Development → Production. *This is mandatory before App Store release* — without it, production users' sync silently fails (the schema doesn't exist in Production). [CITED: ARCHITECTURE.md / STACK.md — "deploy schema from Development → Production before release or sync silently fails"]
6. **Remember CloudKit's append-only rule:** once in Production the schema cannot be renamed/retyped/deleted — only additively extended. The existing schema is already append-only-safe.

## Common Pitfalls

### Pitfall P-1: iCloud sign-out wipes the local store (iOS 18, by design) — unsynced edits can be lost
**What goes wrong:** When the user signs out of iCloud (or disables iCloud for the app in Settings) while `.cloudKit` is active, NSPersistentCloudKitContainer **deletes the local store** for privacy. Data reappears on sign-in *if it was synced*. But records saved locally and **not yet uploaded** (rate-throttling, offline, app killed) are **permanently lost**.
**Why it happens:** Apple's stated design: "Turning off iCloud for an app is equivalent to signing out of the account." [CITED: developer.apple.com/forums/thread/811294]
**How to avoid / mitigate for this phase:**
- This is *framework behavior we accept* for v1 (custom `CKSyncEngine` is deferred). Document it honestly.
- The "no-account promise" (D-11) means a user who **never** signs in stays `.localOnly` and is never affected — their store is plain local, no mirroring, no wipe.
- The risk window is only for users who *had* sync on and then sign out with unsynced edits. Surface this softly in the privacy/sync copy ("your data lives in your private iCloud; turning iCloud off removes the synced copy from this device").
- **Do NOT** try to detect-and-rescue this in code this phase — that's a custom-sync-engine problem (out of scope). Flag it as an Open Question for v2.
**Warning signs:** Tester reports "I turned off iCloud and lost today's entry."

### Pitfall P-2: Live CloudKit entitlement + `cloudKitDatabase: .none` can mis-activate or crash
**What goes wrong:** With the CloudKit entitlement present and an available iCloud account, SwiftData has been reported to attempt CloudKit activation even with `cloudKitDatabase: .none`, throwing `configurationSchemaNotFoundInContainerSchema` or "CloudKit integration requires that all relationships be optional."
**Why it happens:** The presence of the entitlement changes SwiftData's defaulting behavior in some configurations. [CITED: developer.apple.com/forums/thread/746507, /769329]
**How to avoid:** The schema here is relationship-free and fully optional/defaulted, so the "relationships must be optional" failure cannot trigger. For the `.none` path, keep it genuinely local and resolve `SyncMode` **before** container construction (Pattern 2). Verify in CI (which has no iCloud account → token nil → `.localOnly` → `.none`) that the container builds. Add a unit test asserting `makeModelContainer(syncMode: .localOnly)` succeeds even though the entitlement file exists in the target.
**Warning signs:** CI suddenly red after adding the entitlement, with a SwiftData configuration error.

### Pitfall P-3: Forgetting to promote the schema to Production
**What goes wrong:** Sync works in development/TestFlight (Development environment) but silently fails for App Store users (Production schema absent).
**How to avoid:** The schema-deploy steps above make promotion step 5 mandatory. Add it to the release checklist.
**Warning signs:** "Works for me in TestFlight, but App Store users say sync doesn't work."

### Pitfall P-4: App Store Guideline 5.1.3(ii) "may not store personal health information in iCloud" misread
**What goes wrong:** A reviewer reads the guideline literally and rejects. The *intent* targets dumping HealthKit-derived data into general/Apple-readable iCloud — **private CloudKit DB sync of the app's own user-entered data is the accepted pattern** (it's how Apple's Health app syncs). [CITED: PITFALLS.md Pitfall 2; developer.apple.com/app-store/review/guidelines]
**How to avoid:** Use the **private** database only (already the case). Keep the explicit-consent + privacy-policy framing (already present). Have a clean review note describing the private-encrypted design and a local-only fallback (which this architecture already provides). No HealthKit data is synced this phase (Phase 9).
**Warning signs:** Review rejection citing 5.1.3.

### Pitfall P-5: `ubiquityIdentityToken` vs `accountStatus` confusion
**What goes wrong:** Using async `accountStatus` to gate synchronous container construction, or treating `accountStatus == .available` (which is true even offline) as "ready to sync."
**How to avoid:** Gate `SyncMode` on `ubiquityIdentityToken != nil` (sync, launch-safe, nil when signed out). Optionally read `accountStatus` asynchronously for a richer Settings line, but never block on it.

## Code Examples

### Account-aware `SyncMode` + container (the core change)
```swift
// Source: synthesizes existing ModelContainer+Config.swift + AppDependencies seam pattern
enum SyncMode {
    case localOnly
    case cloudKit

    /// Resolve from an injected availability seam. CI/tests pass a stub forcing .localOnly.
    static func resolve(_ availability: AccountAvailability) -> SyncMode {
        availability.isICloudAvailable ? .cloudKit : .localOnly
    }
}

func makeModelContainer(
    syncMode: SyncMode = .localOnly,   // resolved by the caller (composition root)
    inMemory: Bool = false,
    url: URL? = nil
) throws -> ModelContainer {
    let schema = Schema(versionedSchema: SchemaV3.self)
    let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
        (syncMode == .cloudKit) ? .private("iCloud.com.endoapp.ios") : .none

    let configuration: ModelConfiguration
    if let url {
        configuration = ModelConfiguration(schema: schema, url: url)   // test/migration seam, always local
    } else {
        configuration = ModelConfiguration(
            schema: schema, isStoredInMemoryOnly: inMemory, cloudKitDatabase: cloudKitDatabase)
    }
    return try ModelContainer(for: schema, migrationPlan: EndoMigrationPlan.self,
                              configurations: configuration)
}
```

### `project.yml` entitlements recipe (the feasibility crux — CI stays green)
```yaml
# Source: XcodeGen ProjectSpec.md (CODE_SIGN_ENTITLEMENTS) + Apple Simulator-no-signing fact
targets:
  EndoApp:
    type: application
    platform: iOS
    sources:
      - Sources/EndoApp
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.endoapp.ios
        INFOPLIST_FILE: Sources/EndoApp/Resources/Info.plist
        GENERATE_INFOPLIST_FILE: NO
        # The entitlements file ships now. On the iOS Simulator (CI), builds are NOT code-signed,
        # so this setting is IGNORED and no DEVELOPMENT_TEAM is needed → CI stays green local-only.
        CODE_SIGN_ENTITLEMENTS: Sources/EndoApp/Resources/EndoApp.entitlements
        # DEVELOPMENT_TEAM intentionally UNSET (D-02). The user fills in their Team ID to activate
        # real device sync. Leave CODE_SIGN_STYLE Automatic so Xcode manages the iCloud container
        # provisioning once a team is present.
        CODE_SIGN_STYLE: Automatic
        # DEVELOPMENT_TEAM: ABCDE12345   # <-- USER: uncomment + set to activate device sync
```
> **Why a hand-written `CODE_SIGN_ENTITLEMENTS` and not the XcodeGen `entitlements:` generator?** Both set the setting for all configs; the simulator-no-signing fact makes either safe for CI. A committed, hand-written file is easier to grep-verify in tests and audit for the exact keys. Either works — recommend hand-written.

### `EndoApp.entitlements` (new, committed)
```xml
<!-- Source: Apple iCloud + CloudKit + push entitlement keys -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.icloud-container-identifiers</key>
  <array><string>iCloud.com.endoapp.ios</string></array>
  <key>com.apple.developer.icloud-services</key>
  <array><string>CloudKit</string></array>
  <key>com.apple.developer.ubiquity-kvstore-identifier</key>
  <string>$(TeamIdentifierPrefix)$(CFBundleIdentifier)</string>
  <key>aps-environment</key>
  <string>development</string>
</dict>
</plist>
```
> `aps-environment` = `development` for dev/TestFlight; the release/distribution build resolves to `production` via the provisioning profile. Inert without a team.

### Info.plist Background Modes addition (D-04)
```xml
<!-- Add to Sources/EndoApp/Resources/Info.plist -->
<key>UIBackgroundModes</key>
<array>
  <string>remote-notification</string>
</array>
```

### Settings sync-status line (D-12, non-blocking)
```swift
// Source: pattern mirrors existing SettingsView sections; reads the injected seam, no spinner
Section {
    HStack {
        Label("iCloud sync", systemImage: "icloud")
        Spacer()
        Text(accountAvailability.isICloudAvailable ? "On" : "Off")
            .foregroundStyle(.secondary)
            .accessibilityIdentifier("settings.sync.status")
    }
} footer: {
    Text("Endo-App syncs only to your own private iCloud — no third party, no account. "
       + "When iCloud is off, everything stays on this device.")
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Core Data + NSPersistentCloudKitContainer hand-config | SwiftData `ModelConfiguration(cloudKitDatabase: .private(_:))` | iOS 17→18 maturity | No CKRecord/container plumbing; the `.private(_:)` branch is already wired. |
| Local store survives iCloud sign-out | **iOS 18: local store wiped on sign-out (privacy)** | iOS 18 | P-1 — unsynced edits at risk; accepted for v1, flagged for v2. [CITED: forums/811294] |
| `accountStatus` everywhere | `ubiquityIdentityToken` for launch-time checks | longstanding Apple guidance | Synchronous gate; correct offline behavior. [CITED: QA1935] |

**Deprecated/outdated:** Nothing in the existing seam is outdated. The `SyncMode.current` static needs to become account-aware (it was a deliberate Phase-1 placeholder).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The `CODE_SIGN_ENTITLEMENTS` simulator-safe behavior holds for `xcodebuild test -destination 'platform=iOS Simulator'` exactly as for device-vs-simulator generally. | §Code Examples / feasibility crux | If wrong, CI goes red on entitlement presence. **Mitigation: this is the FIRST thing the plan must verify — add a Wave-0 smoke task that runs the existing test command after adding the entitlement, before any other work.** High-confidence (Apple docs + multiple sources), but verify empirically. |
| A2 | NSPersistentCloudKitContainer auto-mirrors pre-existing local records up on first `.cloudKit` activation (D-14) with no migration code. | §Runtime State Inventory | If wrong, existing local logs wouldn't reach the cloud — but this is the documented framework behavior and a human-UAT item anyway. |
| A3 | CloudKit prefixes SwiftData entity names with `CD_` in the Console (e.g. `CD_DayEntry`). | §Schema Deployment step 4 | Cosmetic only — affects what the user looks for in Console, not behavior. |
| A4 | `aps-environment = development` in the committed entitlements is correct for dev/TestFlight and the profile flips it to `production` for release. | §Code Examples | If wrong, push-driven sync is slower in release; sync still works via polling. Low risk. |

## Open Questions

1. **P-1 sign-out data-loss window — acceptable for v1?**
   - What we know: iOS 18 wipes the local store on iCloud sign-out; unsynced edits are lost (by design).
   - What's unclear: whether the product wants any mitigation (e.g., an export-before-signout nudge).
   - Recommendation: Accept for v1 (custom sync engine is deferred). Document softly in copy. Revisit in v2 if testers hit it. **This is a decision for discuss/planning, not research.**

2. **`initializeCloudKitSchema()` debug call — include a guarded code path, or rely on lazy creation + Console?**
   - What we know: explicit init is more reliable for first-time schema creation; lazy creation also works.
   - Recommendation: Include a `#if DEBUG`, launch-arg-guarded one-shot call (mirrors the existing `-uitest-reset` pattern in `AppDependencies`) so the user can deterministically initialize the Dev schema. Planner's call.

3. **Settings line: `ubiquityIdentityToken` (sync, "is signed in") vs `accountStatus` (async, richer)?**
   - Recommendation: Use the synchronous seam for the On/Off line (D-12 wants minimal, no spinner). Don't over-engineer.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Xcode | Build/test | ✓ | 26.5 | — |
| xcodegen | project.yml → .xcodeproj | ✓ | 2.45.4 | — |
| xcodebuild | CI test command | ✓ | 26.5 | — |
| iOS Simulator (iPhone 16) | `xcodebuild test` destination | ✓ (assumed; Xcode 26.5 ships it) | iOS 18+ | iPhone 16 family alt |
| Apple Developer Team / `DEVELOPMENT_TEAM` | **Real device sync only** | ✗ (intentionally absent, D-02) | — | **Local-only path — the entire CI/automatable scope runs without it.** This is the design. |
| ≥2 real iOS devices + signed-in iCloud | The 4 DATA human-UAT criteria | ✗ (user-provided) | — | None — inherently human_needed (D-16). |

**Missing dependencies with no fallback:** Real devices + team for the 4 DATA criteria — *by design*, these are the HUMAN-UAT items.
**Missing dependencies with fallback:** `DEVELOPMENT_TEAM` — the local-only path is the fallback and is the full automatable scope.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Suite`/`@Test`/`#expect`) for logic; XCUITest for UI flows (separate target) |
| Config file | none — XcodeGen-generated targets `EndoAppTests` (unit) + `EndoAppUITests` (UI) |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-02 | `SyncMode.resolve(stub:true)` → `.cloudKit`; `resolve(stub:false)` → `.localOnly` | unit | `... -only-testing:EndoAppTests/SyncModeTests` | ❌ Wave 0 |
| DATA-02 | `makeModelContainer(syncMode:.localOnly)` builds successfully **even with the entitlement present** (P-2 guard) | unit | `... -only-testing:EndoAppTests/SyncModeTests/localOnly_buildsContainer` | ❌ Wave 0 |
| DATA-02 | CI default (no iCloud account) resolves `.localOnly` via the real `SystemAccountAvailability` | unit | same suite | ❌ Wave 0 |
| DATA-02/03/04 | Real multi-device sync, offline reconcile, reinstall restore, delete-purge | **manual-only** | — (≥2 devices, real iCloud, team) | N/A — HUMAN-UAT |
| DATA-04 | `deleteDay` leaves no orphaned records of any type for that day (local correctness, pre-sync) | unit | `... -only-testing:EndoAppTests/EditDeleteTests` (extend) | ⚠️ extend existing |
| (structural) | Entitlements file present + contains the 3 required keys; Info.plist has `UIBackgroundModes` | unit (grep/Bundle) or CI script | `grep` assertion or a `@Test` reading the bundled plist | ❌ Wave 0 |
| (regression) | Full existing suite stays green (no regression from the seam change) | unit+UI | full suite command | ✅ existing |

### Sampling Rate
- **Per task commit:** `-only-testing:EndoAppTests` quick run.
- **Per wave merge:** full suite (`xcodegen generate && xcodebuild test ...`).
- **Phase gate:** full suite green local-only, **then** the HUMAN-UAT plan executed by the user before DATA-02/03/04 are marked complete.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/SyncModeTests.swift` — covers DATA-02 (resolve logic + local-only-builds-with-entitlement P-2 guard).
- [ ] `Sources/EndoApp/Persistence/AccountAvailability.swift` — the protocol + `SystemAccountAvailability` + `StubAccountAvailability` (production seam the tests inject).
- [ ] Structural test (or CI grep) asserting `EndoApp.entitlements` contains `icloud-container-identifiers`/`icloud-services`/`aps-environment` and Info.plist has `remote-notification`.
- [ ] **Wave-0 FIRST task (de-risk A1):** add the entitlements file + `CODE_SIGN_ENTITLEMENTS`, run the existing full suite, confirm green — *before* any further work. If red, the whole plan pivots to per-config gating.
- [ ] Extend `EditDeleteTests` with a "no orphaned records after deleteDay" assertion (local delete-completeness, the automatable half of DATA-04).
- [ ] Framework install: none — Swift Testing + XCUITest already present.

### Manual-Only (HUMAN-UAT plan — the 4 DATA criteria, D-16)
Prerequisites: user sets `DEVELOPMENT_TEAM`, registers/auto-creates the container, runs the schema-deploy steps (Development), signs both devices into the **same** iCloud account.
1. **DATA-02 (sync across devices, no account):** Log a symptom on Device A. Within minutes, confirm it appears on Device B. No login was performed on either device.
2. **DATA-03a (offline reconcile):** Airplane-mode Device A, log entries, re-enable network → entries upload and appear on B. Make independent edits on A (offline) and B (online) to *different* fields/days → both survive (append-only/LWW), no record lost.
3. **DATA-03b (reinstall restore):** Delete the app from Device A, reinstall, sign in → prior logs restore from iCloud automatically (no restore UI).
4. **DATA-04 (delete purges cloud):** Delete a day on Device A → confirm it disappears on Device B; then reinstall on a clean device → the deleted day does **not** reappear (the private-DB copy was truly removed).
5. **Sign-out grace (D-11, P-1):** Sign out of iCloud → app stays fully usable local-only, no crash, no error wall. (Note P-1: unsynced edits may be wiped — expected.)

## Security Domain

`security_enforcement: true`, ASVS level 1.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No app account/login by design (the whole point); uses the user's implicit iCloud identity. |
| V3 Session Management | no | No sessions. |
| V4 Access Control | yes (minimal) | Data lives in the user's **private** CloudKit DB only — Apple-account-scoped, end-to-end protected, never public/shared. Consent gate (ONBD-01) already enforced upstream in `AppGate`/`Repository`. |
| V5 Input Validation | yes (already done) | Severity clamps + note-length bounds already at the Repository seam; this phase adds no new user input. |
| V6 Cryptography | no (delegated) | CloudKit private DB encryption is Apple-managed; do not hand-roll. |
| V9 Communications | yes (delegated) | All transport is CloudKit/APNs over TLS, Apple-managed. No app-controlled network. |
| V12 Logging | yes (regression guard) | **Do not log health values or account tokens.** The existing recovery code logs only `type(of: error)` — preserve that discipline for any new sync error handling. |

### Known Threat Patterns for SwiftData + CloudKit private sync
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health data leaking to a non-private/Apple-readable store | Information Disclosure | Use the **private** database only (already configured); never public/shared (deferred to v2 explicitly). |
| App Store 5.1.3(ii) rejection (health-in-iCloud) | (policy) | Private DB + consent + privacy policy already in place; clean review note; local-only fallback exists. |
| Logging health values / `ubiquityIdentityToken` to console or crash reports | Information Disclosure | Log only error *types*; never log record contents or the token. Mirror the existing `print("...\(type(of: error))")` pattern. |
| Storing the sync flag / account state in the synced store | Tampering | `SyncMode` lives in code/UserDefaults, never CloudKit (D-15). |
| Incomplete delete leaving cloud copy (DATA-04) | (privacy/GDPR Art. 17) | Hard delete propagates to the private DB; verified by the reinstall-shows-nothing UAT (DATA-04). |

## Sources

### Primary (HIGH confidence)
- developer.apple.com/documentation/swiftdata — `ModelConfiguration(cloudKitDatabase:)`, private CloudKit mirroring — CITED.
- developer.apple.com/documentation/cloudkit/ckcontainer (accountStatus, CKAccountStatus) — CITED.
- developer.apple.com/library/archive/qa/qa1935 — `ubiquityIdentityToken` is the recommended launch-time iCloud-availability check — CITED.
- GitHub XcodeGen ProjectSpec.md — `CODE_SIGN_ENTITLEMENTS`, `entitlements:`, per-config `settings`/`configs` — VERIFIED (read directly).
- developer.apple.com/library/archive/technotes/tn2415 — entitlements/provisioning; Simulator builds not signed — CITED.
- .planning/research/ARCHITECTURE.md + PITFALLS.md + STACK.md (in CLAUDE.md) — schema rules, deploy-to-Production requirement, 5.1.3(ii) framing — VERIFIED (read).
- Codebase: `ModelContainer+Config.swift`, `SchemaV3.swift`, `Repository.swift`, `FactorReading.swift`, `AppDependencies.swift`, `SettingsView.swift`, `PrivacyPolicyText.swift`, `project.yml`, `Info.plist` — VERIFIED (read).

### Secondary (MEDIUM confidence)
- developer.apple.com/forums/thread/811294 — iOS 18 NSPersistentCloudKitContainer wipes local store on iCloud sign-out (by design); unsynced edits lost — corroborated by multiple posts.
- developer.apple.com/forums/thread/746507, /769329 — live CloudKit entitlement + `cloudKitDatabase: .none` mis-activation/crash (P-2).
- Apple QA1935 + forums — `accountStatus` returns `.available` even offline; `ubiquityIdentityToken` for launch checks.
- itwenty / topolog / fatbobman — CloudKit tombstone delete propagation; initializeCloudKitSchema.

### Tertiary (LOW confidence — verify empirically)
- General "Simulator builds are not code-signed; entitlements ignored" — widely stated, must be confirmed by the Wave-0 smoke task (A1).

## Metadata

**Confidence breakdown:**
- Build/signing feasibility (the crux): HIGH — Apple docs + XcodeGen spec + multiple sources agree Simulator builds aren't signed. Still gated behind a mandatory Wave-0 empirical check (A1).
- Schema CloudKit-safety: HIGH — read the actual models; all optional/defaulted, no `.unique`, no relationships, app-gen UUIDs.
- Account-detection API choice: HIGH — Apple's own guidance (QA1935).
- Runtime sign-out behavior (P-1): MEDIUM — Apple-forum-sourced, consistent, but not a single crisp official doc.
- Delete propagation: MEDIUM-HIGH — CloudKit tombstone mechanism documented; relationship-free model removes the cascade risk.

**Research date:** 2026-06-08
**Valid until:** ~2026-07-08 (stable iOS 18 APIs; re-check if Xcode/iOS 26 changes the sign-out-wipe or `.none`-with-entitlement behavior).
