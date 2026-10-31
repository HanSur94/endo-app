# Phase 18: Room Persistence - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (data-layer phase — decisions pre-locked in ANDROID-PORT-PLAN.md §3 + ARCHITECTURE.md)

<domain>
## Phase Boundary

Persist all user data locally in Room with the iOS data-lifecycle guarantees — consent-gated, delete-complete, migration-disciplined, backup-excluded — from day one. Add the `insightsbridge/SnapshotProjector` (Room rows → `:insights` DaySnapshot), the Kotlin ClaimsPolicy port + CopyAudit JVM harness (before any UI copy), and the versioned encrypted-export FORMAT contract (implementation lands in Phase 23). Delivers APER-01..07. No Compose UI, no screens — this is the `:app` module's data layer plus its bridge to `:insights`.

</domain>

<decisions>
## Implementation Decisions

Architecture is LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §3, [.planning/research/ARCHITECTURE.md](../../research/ARCHITECTURE.md) (Room layering, cascade, bridge, drift, DataStore consent), [.planning/research/STACK.md](../../research/STACK.md) (Room 2.8.4, KSP 2.3.9, DataStore 1.2.0, kotlinx-serialization 1.11.0), and [.planning/research/PITFALLS.md](../../research/PITFALLS.md). Do not re-open. Key locked decisions:

### Module & build
- Create the `:app` module (Android Application, `com.android.application` + built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android` under AGP 9). This phase adds only the data layer; the Compose UI shell arrives in Phase 19.
- `:app` depends on `:insights`, never the reverse. Add Room 2.8.4 + KSP 2.3.9 (never kapt; no `room-ktx`), DataStore 1.2.0 to the version catalog. compileSdk 36 / minSdk 26 / targetSdk 36. The version-catalog audit test (from Phase 17) must still pass — no analytics/auth/backend/networking coordinate.

### Room schema (10 entities, iOS model names)
- Mirror the 10 SwiftData models by name: DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, Medication, TreatmentState, ImportedSample, CachedInsight, DismissedInsight. App-generated UUID string primary keys; `epochDay: Long` (or ISO date) day identity; enum values stored as raw strings matching the `:insights`/domain raw values (reuse the ported enums; `FactorKind` lives here in `:app`, NOT in `:insights` — carry its 7 raw values verbatim, cross-checked against the Phase-17 golden-JSON header).
- Room v1 migration discipline is IRREVERSIBLE and must be born correct: `exportSchema = true`, committed `android/app/schemas/`, a `MigrationTestHelper` scaffold, and `fallbackToDestructiveMigration` structurally BANNED (it would wipe user health history — a test asserts it is never called).
- CloudKit's constraints do NOT apply on Android — Room permits non-null columns and unique indices. Still keep app-generated UUID identity so export/import round-trips are stable (Phase 23).

### Consent gating (consent-by-construction, data layer half)
- Consent flag + reminder prefs in DataStore, OUTSIDE the health database (mirrors the iOS UserDefaults split). `ConsentStore` exposes the flag.
- `EndoRepository` is a consent-gated facade — every health read/write asserts consent (throws / returns empty pre-consent), mirroring the iOS repository `assertConsented`. The UI-level gate lands in Phase 19; this phase makes the repository itself refuse pre-consent.

### Delete-day cascade (iOS GDPR Art. 17 lesson — born complete)
- Deleting a day purges EVERY day-scoped row type in ONE `@Transaction`, while non-day-scoped definitions (Medication, and TreatmentState per its semantics) survive. On iOS an orphaned `FactorReading` after `deleteDay` was a real GDPR bug (fixed in iOS 04-02) — the Android cascade must be complete from the first commit. Enforce with (a) a cascade test proving all day-scoped rows are purged, (b) a survival test for definitions, and (c) a reflection/meta-test that fails if a new day-scoped entity is added without being included in the cascade (so the cascade can't silently fall behind the schema).

### insightsbridge / SnapshotProjector
- `insightsbridge/SnapshotProjector` maps fetched Room rows → `:insights` `DaySnapshot` values as a PURE function (row lists in, DaySnapshot out) — the ONLY place platform persistence types and pure domain types meet. Runs as JVM unit tests (no emulator). Free text (notes) never crosses the bridge — only `hasNote`. Port the iOS `daySnapshots()` mapping + its parity test suite (the ~24-case enum/mapping suite).

### ClaimsPolicy + CopyAudit harness (before UI copy)
- Port the Kotlin `ClaimsPolicy` (sharing the SAME substring lists as the Swift `Content/ClaimsPolicy.swift`; the Phase-17 drift test already guards the `:insights` copy — here add the `:app` ClaimsPolicy + a `CopyAudit` JVM test scanning a registered copy catalog). Rule carried verbatim: every user-facing string is registered for audit IN THE SAME CHANGE that introduces it. Landing the harness now (before Phase 19 UI copy) means the first screen's copy is audited from birth.

### Export FORMAT contract (implementation deferred to Phase 23)
- Define the versioned, platform-neutral export JSON schema now (schema-version field, ISO-8601 dates, enum raw values matching the shared domain vocabulary, UUID identity) plus the crypto header spec (PBKDF2 params, AES-GCM, salt/nonce layout) as a DATA CONTRACT + kotlinx-serialization DTOs. Do NOT implement encrypt/decrypt/SAF here — that is Phase 23. This phase produces the format definition so Phase 23 is mechanical and the identity columns above are justified.

### Backup exclusion
- Exclude the Room health database from Android Auto Backup via backup rules (`dataExtractionRules` / `fullBackupContent`); only the consent flag / prefs may back up. Assert with a test that the DB file/path is in the exclusion set. No `INTERNET` permission (the manifest test lands in Phase 19 with the manifest; keep `:app`'s manifest minimal here).

### Claude's Discretion
- Exact DAO/entity/repository package layout under `com.endoapp.app.data`, TypeConverters, and Flow-vs-suspend accessor choices — at Claude's discretion, guided by ARCHITECTURE.md. Prefer suspend DAOs + Flow observation.
- Whether `:app` unit tests use JUnit 4 (AGP-native) for Android-touching tests and JUnit 5 for pure-JVM bridge tests, or Robolectric where a Context is needed — Claude's discretion; keep the pure `SnapshotProjector`/ClaimsPolicy tests as fast JVM tests.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- SwiftData models: `Sources/EndoApp/Persistence/Models/*.swift` (the 10 @Model types) + `SchemaV1..V6.swift` + `MigrationPlan.swift` + `ModelContainer+Config.swift`.
- The projection seam: `Sources/EndoApp/Persistence/*` `daySnapshots()` (the ONLY SwiftData→DaySnapshot mapping) + its parity test (7-enum drift trap).
- Delete-day cascade + the fixed orphan bug: iOS phase 04-02 (`deleteDay` now purges FactorReading; Medication survives).
- `Content/ClaimsPolicy.swift` (forbidden-substring lists) + `Tests/EndoAppTests/CopyAuditTests.swift`.
- Consent: `Consent/ConsentState.swift` + `ConsentStore.swift` (consent flag outside the synced store).

### Established from Phase 17 (reuse)
- `:insights` module exposes `DaySnapshot`, the domain enums (with `raw` strings), `Factor` sealed interface, `Insight`, `DeterministicUUID`, and the drift-test pattern (Swift-source scan + golden JSON). The `:app` bridge targets these.
- `android/gradle/libs.versions.toml` version catalog + the version-catalog audit test + boundary-lint pattern. `android/settings.gradle.kts` currently includes `:insights` only — add `:app`.
- Swift 6.3.3 + JDK 21 are on the host; `cd android && ./gradlew` works.

### Integration points
- Phase 19 (UI shell) consumes `ConsentStore` + `EndoRepository`; Phase 23 (export/import) consumes the format contract + UUID identity; Phase 22 (insights UI) consumes `SnapshotProjector` → `:insights`.

</code_context>

<specifics>
## Specific Ideas

The delete-day cascade completeness meta-test is the signature carry-over from the iOS GDPR lesson — make it structural (fails when a new day-scoped entity is added but not cascaded), not just a fixed-list assertion. Mirror the iOS `daySnapshots()` parity suite case-for-case so the bridge is provably faithful.

</specifics>

<deferred>
## Deferred Ideas

- Encrypted export/import IMPLEMENTATION (encrypt/decrypt/SAF/passphrase UX) → Phase 23. This phase defines only the format + crypto-header contract.
- The `AppGate` consent-by-construction UI + manifest no-INTERNET test → Phase 19 (this phase gates the repository, not the composables).

</deferred>
