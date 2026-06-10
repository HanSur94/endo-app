# Phase 4: Private CloudKit Sync - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 9 (2 new source, 1 new entitlements, 4 modified source, 1 modified config, 1+ tests)
**Analogs found:** 9 / 9 (every file has a strong in-repo analog — this phase is "activate the seam", not "invent a pattern")

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Sources/EndoApp/Persistence/AccountAvailability.swift` | service (injectable seam) | request-response (sync Bool query) | `Sources/EndoApp/Reminders/NotificationScheduler.swift` (protocol + System impl + Mock); `Sources/EndoApp/Consent/ConsentStore.swift` (protocol + concrete + injected default) | exact (seam triad) |
| `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (MODIFY) | config / factory | request-response | self (the `.private(_:)` branch already wired, lines 37–65) | exact (in-place activation) |
| `Sources/EndoApp/Resources/EndoApp.entitlements` (NEW) | config | N/A | `Sources/EndoApp/Resources/Info.plist` (plist DOCTYPE/structure) + RESEARCH §Code Examples entitlements recipe | role-match (plist authoring) |
| `project.yml` (MODIFY) | config | N/A | self (`targets.EndoApp.settings` block, lines 19–28) | exact |
| `Sources/EndoApp/Resources/Info.plist` (MODIFY) | config | N/A | self (existing `<key>/<array>` blocks, e.g. UIRequiredDeviceCapabilities lines 38–41) | exact |
| `Sources/EndoApp/App/AppDependencies.swift` (MODIFY) | composition root | request-response | self (existing service construction + EnvironmentKey triads, lines 31–145) | exact |
| `Sources/EndoApp/App/EndoAppApp.swift` (MODIFY, if seam injected to Environment) | composition root | — | self (`.environment(\.notificationScheduler, …)` lines, body) | exact |
| `Sources/EndoApp/Features/Settings/SettingsView.swift` (MODIFY) | component (view) | request-response (read seam) | self (existing `Section { … } footer: { … }` blocks, e.g. lines 82–106) | exact |
| `Sources/EndoApp/Content/PrivacyPolicyText.swift` (MODIFY/verify) | content/copy | — | self (the iCloud note already at lines 23–26) | exact — likely no change needed |
| `Tests/EndoAppTests/SyncModeTests.swift` (NEW) | test | — | `Tests/EndoAppTests/NotificationSchedulerTests.swift` (`@Suite`/`@Test`/`#expect` + Mock injection) | exact |
| `Tests/EndoAppTests/EditDeleteTests.swift` (MODIFY/extend) | test | — | self (`delete_removesDay_andCascadesChildren`, lines 39–61) | exact |

---

## Pattern Assignments

### `Sources/EndoApp/Persistence/AccountAvailability.swift` (NEW — service seam, request-response)

**Analog:** `Sources/EndoApp/Reminders/NotificationScheduler.swift` (protocol + `System…` impl + `Mock…` test double) and `Sources/EndoApp/Consent/ConsentStore.swift` (protocol + concrete + injected default).

**Protocol-seam pattern to mirror** (`NotificationScheduler.swift` lines 31–50, 154–236 — protocol / System impl / Mock triad):
```swift
// Header comment requirement banner is the house style (see every file's line 1).
protocol NotificationScheduler { … }            // the seam
struct SystemNotificationScheduler: NotificationScheduler { … }   // thin system shell
final class MockNotificationScheduler: NotificationScheduler { … } // records calls
```

**Concrete shape for this file** (RESEARCH Pattern 1, §Code Examples lines 171–186). Note: `AccountAvailability` returns a synchronous `Bool` and is `Sendable` (NOT `@MainActor` — unlike `ConsentStore`/`NotificationScheduler` it is read at container-build time on whatever actor the composition root runs):
```swift
protocol AccountAvailability: Sendable {
    /// Synchronous, launch-safe. true when an iCloud account is signed in.
    var isICloudAvailable: Bool { get }
}

struct SystemAccountAvailability: AccountAvailability {
    var isICloudAvailable: Bool { FileManager.default.ubiquityIdentityToken != nil }
}

/// Test seam: forces a deterministic answer (CI/tests force false → .localOnly).
struct StubAccountAvailability: AccountAvailability {
    let isICloudAvailable: Bool
}
```

**House-style notes the analog establishes:**
- File begins with a `// Requirement: DATA-02/03 — …` banner comment (every source file does — see ConsentStore line 1, NotificationScheduler line 1).
- `import Foundation` for the `FileManager` call (mirrors ConsentStore line 9).
- Security discipline (RESEARCH §Security V12): NEVER log the `ubiquityIdentityToken`; mirror the `type(of: error)`-only logging discipline in `AppDependencies.swift` line 56.
- Anti-pattern guard (RESEARCH P-5): use `ubiquityIdentityToken` (sync), NOT `CKContainer.accountStatus` (async) for the gate.

---

### `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (MODIFY — config/factory)

**Analog:** itself — the `.private(cloudKitContainerIdentifier)` branch is already wired (lines 43–45). Phase 1 deliberately left `SyncMode.current` hardcoded to `.localOnly` (line 20).

**Existing branch to make account-aware** (current lines 14–45):
```swift
enum SyncMode {
    case localOnly
    case cloudKit
    static var current: SyncMode { .localOnly }   // <-- Phase-1 placeholder to replace
}
private let cloudKitContainerIdentifier = "iCloud.com.endoapp.ios"   // <-- KEEP (D-02)

let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
    (SyncMode.current == .cloudKit) ? .private(cloudKitContainerIdentifier) : .none
```

**Change to apply** (RESEARCH Pattern 2 + §Code Examples lines 275–303): replace the static `current` with a `resolve(_ availability:)` that consults the seam, and thread `syncMode` through `makeModelContainer` as a **caller-supplied parameter** (resolved BEFORE container construction, per P-2):
```swift
enum SyncMode {
    case localOnly
    case cloudKit
    static func resolve(_ availability: AccountAvailability) -> SyncMode {
        availability.isICloudAvailable ? .cloudKit : .localOnly
    }
}

func makeModelContainer(
    syncMode: SyncMode = .localOnly,   // resolved by the caller (composition root); default keeps tests local
    inMemory: Bool = false,
    url: URL? = nil
) throws -> ModelContainer {
    let cloudKitDatabase: ModelConfiguration.CloudKitDatabase =
        (syncMode == .cloudKit) ? .private(cloudKitContainerIdentifier) : .none
    …
}
```

**Critical preservation (verified against current file):**
- KEEP the `url:` overload local-only (lines 47–51) — used by `SchemaMigrationTests`. Default `syncMode: .localOnly` means every existing test caller (e.g. `EditDeleteTests` line 25 `makeModelContainer(inMemory: true)`) compiles unchanged and stays local.
- KEEP `Schema(versionedSchema: SchemaV3.self)` + `migrationPlan: EndoMigrationPlan.self` (lines 39, 60–64) untouched.
- P-2 guard: the local-only path must build a genuinely `.none` store even with the entitlement present — assert this in a new test (see SyncModeTests below).

---

### `Sources/EndoApp/Resources/EndoApp.entitlements` (NEW — config plist)

**Analog:** `Sources/EndoApp/Resources/Info.plist` for the plist DOCTYPE/`<plist version="1.0"><dict>` envelope (lines 8–10, 48–49); content from RESEARCH §Code Examples lines 331–349.

**Exact content to write** (committed source; XcodeGen wires it via `CODE_SIGN_ENTITLEMENTS`):
```xml
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
> Container id `iCloud.com.endoapp.ios` MUST match `cloudKitContainerIdentifier` in `ModelContainer+Config.swift` line 25 (D-02). Inert on Simulator/no-team builds. Use the Write tool (no heredoc).

---

### `project.yml` (MODIFY — config)

**Analog:** itself — the `targets.EndoApp.settings` block (lines 19–28). Note the Phase-1 comment at lines 9–10 reserving CloudKit for Phase 4 — UPDATE/remove that comment.

**Existing settings block** (lines 25–28):
```yaml
    settings:
      PRODUCT_BUNDLE_IDENTIFIER: com.endoapp.ios   # D-09 placeholder, flagged to finalize
      INFOPLIST_FILE: Sources/EndoApp/Resources/Info.plist
      GENERATE_INFOPLIST_FILE: NO
```

**Add** (RESEARCH §Code Examples lines 306–329) — `CODE_SIGN_ENTITLEMENTS`, `CODE_SIGN_STYLE`, commented `DEVELOPMENT_TEAM` placeholder:
```yaml
    settings:
      PRODUCT_BUNDLE_IDENTIFIER: com.endoapp.ios
      INFOPLIST_FILE: Sources/EndoApp/Resources/Info.plist
      GENERATE_INFOPLIST_FILE: NO
      CODE_SIGN_ENTITLEMENTS: Sources/EndoApp/Resources/EndoApp.entitlements
      CODE_SIGN_STYLE: Automatic
      # DEVELOPMENT_TEAM: ABCDE12345   # <-- USER: uncomment + set to activate real device sync (D-02)
```
> Simulator builds are NOT code-signed → CI stays green with no `DEVELOPMENT_TEAM` (RESEARCH crux / Assumption A1 — the Wave-0 smoke test must confirm this empirically). After editing, run `xcodegen generate`.

---

### `Sources/EndoApp/Resources/Info.plist` (MODIFY — config)

**Analog:** itself — existing `<key>…</key><array><string>…</string></array>` blocks (e.g. `UIRequiredDeviceCapabilities` lines 38–41; `UISupportedInterfaceOrientations` lines 42–47).

**Add** (RESEARCH §Code Examples lines 351–358, D-04):
```xml
	<key>UIBackgroundModes</key>
	<array>
		<string>remote-notification</string>
	</array>
```
> Harmless without a team. Update the file's header comment (lines 2–7) which currently says "CloudKit sync is Phase 4" / "No HealthKit … needed in Phase 1".

---

### `Sources/EndoApp/App/AppDependencies.swift` (MODIFY — composition root)

**Analog:** itself — the existing service construction (lines 31–75) and the three EnvironmentKey triads (lines 94–145).

**Service construction pattern to mirror** (lines 71–74):
```swift
self.consentStore = UserDefaultsConsentStore()
self.terminologyStore = TerminologyStore()
self.notificationScheduler = SystemNotificationScheduler()
self.checkInRouter = CheckInRouter()
```

**Change to apply:** add an `accountAvailability` stored property + construct it, then **resolve `SyncMode` and pass it into `makeModelContainer` BEFORE the container build** (RESEARCH Pattern 2). The container build currently sits at lines 42–44:
```swift
// add property (mirrors `let notificationScheduler:` at line 26):
let accountAvailability: any AccountAvailability

init() {
    let uiTestReset = ProcessInfo.processInfo.arguments.contains("-uitest-reset")
    // …existing reset…
    // Resolve account availability FIRST. uiTestReset (and tests) force local-only deterministically.
    let availability: any AccountAvailability =
        uiTestReset ? StubAccountAvailability(isICloudAvailable: false) : SystemAccountAvailability()
    self.accountAvailability = availability
    let syncMode = SyncMode.resolve(availability)

    do {
        self.modelContainer = try makeModelContainer(syncMode: syncMode, inMemory: uiTestReset)
    } catch { … }   // KEEP the existing 3-tier recovery (lines 44–70) verbatim
```

**EnvironmentKey triad to mirror** (lines 128–145, the NotificationScheduler key) — add a `\.accountAvailability` key so `SettingsView` can read it. Default value pattern (note: `AccountAvailability` is `Sendable`, NOT `@MainActor`, so the default does NOT need `MainActor.assumeIsolated` — simpler than the NotificationScheduler key):
```swift
private struct AccountAvailabilityKey: EnvironmentKey {
    static let defaultValue: any AccountAvailability = SystemAccountAvailability()
}
extension EnvironmentValues {
    var accountAvailability: any AccountAvailability {
        get { self[AccountAvailabilityKey.self] }
        set { self[AccountAvailabilityKey.self] = newValue }
    }
}
```

**Critical preservation:** KEEP the 3-tier recovery (discard-unreadable-store → on-disk retry → in-memory, lines 44–70) and its `type(of: error)`-only logging (line 56) unchanged.

---

### `Sources/EndoApp/App/EndoAppApp.swift` (MODIFY — composition root, only if seam is Environment-injected)

**Analog:** itself — the `.environment(\.…, dependencies.…)` chain in `body` (`AppGate()` modifiers). Add one line mirroring the existing ones:
```swift
.environment(\.accountAvailability, dependencies.accountAvailability)
```

---

### `Sources/EndoApp/Features/Settings/SettingsView.swift` (MODIFY — component)

**Analog:** itself — the `Section { … } header/footer` blocks (e.g. the Privacy section lines 82–89; the reminders footer pattern lines 229–236).

**Environment read pattern to mirror** (lines 15–20):
```swift
@Environment(\.accountAvailability) private var accountAvailability
```

**Section to add** (RESEARCH §Code Examples lines 360–375, D-12 — non-blocking, no spinner, reads the seam):
```swift
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
> Place it near the Privacy section (lines 82–89). The `.accessibilityIdentifier` + multi-line `Text("…" + "…")` footer copy match the house style throughout this file (e.g. lines 73, 100–105). Copy is audited by `CopyAuditTests` — mirror the gentle, non-coercive tone.

---

### `Sources/EndoApp/Content/PrivacyPolicyText.swift` (VERIFY — likely no change)

**Analog:** itself. The private-iCloud sync note already exists at lines 23–26 ("If you turn on sync, your data is kept only in your own private iCloud account…"). D-13's requirement is already satisfied. **Action: verify only.** If adding the soft P-1 sign-out note (RESEARCH P-1: "turning iCloud off removes the synced copy from this device"), extend the "Where your data lives" paragraph (lines 23–26) in the same multi-line `"""` style — but `PrivacyPolicyTests` asserts required phrases, so check that suite (line numbers) before editing.

---

### `Tests/EndoAppTests/SyncModeTests.swift` (NEW — test)

**Analog:** `Tests/EndoAppTests/NotificationSchedulerTests.swift` — `@Suite`/`@Test`/`#expect`, `@testable import EndoApp`, Mock/Stub injection (lines 1–40).

**Structure to mirror** (NotificationSchedulerTests lines 5–40):
```swift
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("DATA-02 account-aware SyncMode")
struct SyncModeTests {
    @Test func resolve_availableICloud_returnsCloudKit() {
        #expect(SyncMode.resolve(StubAccountAvailability(isICloudAvailable: true)) == .cloudKit)
    }
    @Test func resolve_noICloud_returnsLocalOnly() {
        #expect(SyncMode.resolve(StubAccountAvailability(isICloudAvailable: false)) == .localOnly)
    }
    // P-2 guard: local-only builds a real container even though the entitlement file ships in the target.
    @Test func localOnly_buildsContainer() throws {
        let container = try makeModelContainer(syncMode: .localOnly, inMemory: true)
        _ = ModelContext(container)   // proves the .none store is usable
    }
    // CI reality: the real seam with no signed-in account resolves .localOnly.
    @Test func systemAvailability_inCI_resolvesLocalOnly() {
        // CI/Simulator has no iCloud account → ubiquityIdentityToken == nil → .localOnly
        #expect(SyncMode.resolve(SystemAccountAvailability()) == .localOnly)
    }
}
```
> Container-build test mirrors `EditDeleteTests` line 25/41 usage of `makeModelContainer(inMemory: true)` + `ModelContext(container)`.

### `Tests/EndoAppTests/EditDeleteTests.swift` (EXTEND — test)

**Analog:** itself — `delete_removesDay_andCascadesChildren` (lines 39–61) already asserts "no orphaned records of any type after deleteDay" by fetching each model type and `#expect(... .isEmpty)`. This IS the automatable half of DATA-04 (local delete-completeness, pre-sync). Add any new record types introduced since (verify against `SchemaV3`) using the same `ctx.fetch(FetchDescriptor<SchemaV1.X>()).isEmpty` assertion pattern (lines 53–60).

---

## Shared Patterns

### Injectable seam (protocol + System impl + Stub/Mock + EnvironmentKey)
**Source:** `Sources/EndoApp/Reminders/NotificationScheduler.swift` (lines 31–50, 154–236) and `Sources/EndoApp/Consent/ConsentStore.swift` (lines 16–62).
**Apply to:** `AccountAvailability.swift`.
**Key shape:** one protocol, one `System…` concrete reading the real API, one `Stub…`/`Mock…` test double, plus an `EnvironmentKey` + `EnvironmentValues` extension in `AppDependencies.swift`. NOTE the actor distinction — `AccountAvailability` is `Sendable` (synchronous, off-main), unlike the `@MainActor` ConsentStore/NotificationScheduler.

### Composition-root resolution-before-construction
**Source:** `Sources/EndoApp/App/AppDependencies.swift` (init lines 31–75) + `EndoAppApp.swift` (`.environment(…)` chain).
**Apply to:** `AppDependencies.swift` (resolve `SyncMode` before `makeModelContainer`), `EndoAppApp.swift` (inject the seam).
**Why (P-2):** the account-aware decision MUST happen before the container is built; the local-only path must build a genuinely `.none` store.

### Logging discipline (security V12)
**Source:** `AppDependencies.swift` line 56 — `print("… (\(type(of: error)))")` logs only the error TYPE.
**Apply to:** any new sync/account error handling. NEVER log health values or the `ubiquityIdentityToken`.

### Settings Section + footer copy
**Source:** `SettingsView.swift` (Privacy section lines 82–89; footer pattern lines 99–106, 229–236).
**Apply to:** the new "iCloud sync: On/Off" section. Use `.accessibilityIdentifier` and the multi-line `Text("…" + "…")` style; keep copy gentle (audited by `CopyAuditTests`).

### Swift Testing suite
**Source:** `Tests/EndoAppTests/NotificationSchedulerTests.swift` (lines 5–40), `EditDeleteTests.swift` (lines 6–61).
**Apply to:** `SyncModeTests.swift`. `@Suite("REQ — …")` / `@Test func …` / `#expect(…)` / `@testable import EndoApp`; build containers via `makeModelContainer(inMemory: true)`.

### Config plist authoring
**Source:** `Sources/EndoApp/Resources/Info.plist` (DOCTYPE + `<plist><dict>` envelope, `<key>/<array>/<string>` blocks).
**Apply to:** `EndoApp.entitlements` (new) and the Info.plist `UIBackgroundModes` addition.

## No Analog Found

None. Every file maps to a strong in-repo analog. The closest thing to "new" is `AccountAvailability.swift`, but it is a direct clone of the established seam triad (`NotificationScheduler`/`ConsentStore`), so even it copies an existing pattern rather than inventing one.

## Manual / Out-of-Code (no file, documented for the planner)

These are NOT files to create — they are the human-only items the planner must surface as a HUMAN-UAT plan (D-16, RESEARCH §Schema Deployment + §Manual-Only):
- CloudKit schema deploy Development→Production (D-08) — CloudKit Console, not git.
- `DEVELOPMENT_TEAM` Team-ID fill-in (D-02) — user-supplied placeholder in `project.yml`.
- The 4 DATA criteria (multi-device sync / offline reconcile / reinstall restore / delete-purge) — require ≥2 real devices + real iCloud.
- Optional `initializeCloudKitSchema()` `#if DEBUG` one-shot — mirrors the existing `-uitest-reset` launch-arg pattern in `AppDependencies.swift` line 37 if the planner chooses to include it (RESEARCH Open Question 2).

## Metadata

**Analog search scope:** `Sources/EndoApp/{Persistence,App,Consent,Reminders,Features/Settings,Content,Resources}`, `Tests/EndoAppTests/`, `project.yml`.
**Files scanned:** 11 read in full (ModelContainer+Config, AppDependencies, ConsentStore, NotificationScheduler, EndoAppApp, SettingsView, PrivacyPolicyText, Info.plist, project.yml, EditDeleteTests, NotificationSchedulerTests).
**Pattern extraction date:** 2026-06-08
