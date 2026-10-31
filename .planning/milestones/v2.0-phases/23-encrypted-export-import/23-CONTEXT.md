# Phase 23: Encrypted Export/Import - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md §2 + STACK.md crypto spec; the Phase-18 export FORMAT contract is the target)

<domain>
## Phase Boundary

A user can move or back up their entire history through ONE deliberate, passphrase-encrypted export/import surface — data portability with zero permissions and zero cloud. Delivers APRT-01..03: export all data as a passphrase-encrypted file to a SAF-picked destination (zero storage permissions), import an export file (passphrase → decrypt → schema-version validation → idempotent upsert by UUID), distinguish wrong-passphrase vs corrupt-file gently, and prove the format with a committed golden fixture + read-back verification + fresh-salt/nonce-per-export. This is the local-only-v1 answer to GDPR Art. 20 portability + device migration + user-controlled backup — one consent surface, no opaque background copies. Adds an export/import entry to the Phase-20 Settings screen.

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §2 (local-only + encrypted export/import) + [.planning/research/STACK.md](../../research/STACK.md) (the zero-dependency crypto spec) + [.planning/research/PITFALLS.md](../../research/PITFALLS.md) (AES-GCM nonce, KDF params, SAF quirks). The Phase-18 export FORMAT contract (ExportEnvelope + CryptoHeader DTOs) is ALREADY DEFINED — this phase implements the crypto + SAF around it. Do not re-open. Key decisions:

### Crypto (zero-dependency, javax.crypto only)
- Use the PLATFORM `javax.crypto` ONLY — NO Tink, NO Argon2 native lib, and DO NOT use the deprecated `androidx.security:security-crypto`. Per the STACK.md spec: key derivation `PBKDF2WithHmacSHA256` with ≥600,000 iterations + a 16-byte random salt; encryption `AES/GCM/NoPadding` with a 12-byte random IV + 128-bit tag; 256-bit key. FRESH salt + IV per export (two exports of identical data must differ — a test proves it). The `CryptoHeader` (already a DTO from Phase 18) is written as a self-describing plaintext header (version, KDF params, salt, IV) so import reads the params from the file — never hard-codes them. The catalog audit must stay green (no new crypto dependency added — javax.crypto is platform).

### Export format (implement the Phase-18 contract)
- Serialize all user data to the versioned, platform-neutral `ExportEnvelope` (schema-version field, ISO-8601 dates, enum raw values matching the shared domain vocabulary, UUID identity, `CachedInsight` EXCLUDED — derived cache, not user data) via kotlinx-serialization (already in the catalog). Then: envelope JSON → GZIP (optional per plan) → AES-GCM encrypt → prepend the plaintext CryptoHeader → write. The format is platform-neutral by design (a future iOS importer COULD read it) — no Android-isms in the JSON.

### Export flow (SAF, zero permissions)
- Export: user taps "Export my data" in Settings → chooses a passphrase (with a strength/confirmation UX; explain it CANNOT be recovered — no backdoor) → the app serializes + encrypts + writes through the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `openOutputStream(uri, "wt")` truncate mode) — the user picks the destination (local file, their own Drive, wherever). ZERO storage permissions. Read-back verification: after writing, re-open + decrypt + validate BEFORE showing success (never claim success on an unverified write). All on-device, no network.

### Import flow (SAF, idempotent)
- Import: user taps "Import data" → SAF `ACTION_OPEN_DOCUMENT` file pick → passphrase → read header → derive key → decrypt → validate schema version (reject unknown/newer with a clear message) → deserialize → IDEMPOTENT upsert by the app-generated UUIDs (re-importing the same file changes nothing; existing rows updated in place, not duplicated). Consent-gated (import writes health data — requires consent). Distinguish failure modes GENTLY: wrong passphrase (AEADBadTagException → "that passphrase didn't match") vs corrupt/not-an-export file (parse/header failure → "this doesn't look like an Endo export") vs unsupported version — each a calm, specific message, never a stack trace.

### Verification / honesty
- A committed GOLDEN fixture pins the format (a known envelope + a known passphrase → decrypts to the known data) so the format can't silently drift. Read-back verification before success. Two-exports-differ test (fresh salt/nonce). Round-trip test (export → import → identical data, idempotent on re-import). Wrong-passphrase and corrupt-file tests. All JVM-runnable (javax.crypto works on the JVM/Robolectric — no emulator needed for the crypto; SAF interactions are seam-injected).

### Copy discipline
- Every new user-facing string (export/import entries, passphrase UX, the cannot-recover warning, success/failure messages, the three gentle error messages) registered in CopyAudit in the same change; dodge ClaimsPolicy substrings (should/use/her/start/treat/stop/take/here/where). Neutral/non-diagnostic; the passphrase-cannot-be-recovered warning must be clear and honest.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.export` (the Phase-18 DTOs live there), whether to GZIP, the SAF seam design (injectable so the crypto/format is JVM-testable without a real document picker), the passphrase-strength UX, and whether export/import run as foreground coroutines (they do — NO WorkManager/notification; the reminders package stays the sole notification owner). Claude's discretion guided by STACK.md + PITFALLS.md.

</decisions>

<code_context>
## Existing Code Insights

### Established from Phases 17–22 (reuse — DO NOT rebuild)
- Phase 18 ALREADY DEFINED the format contract: `ExportEnvelope` + per-entity `@Serializable` DTOs + `CryptoHeader`/`KdfParams` spec (in `com.endoapp.app.export`), CachedInsight excluded, schema-versioned, round-trip tested. This phase implements encrypt/decrypt/SAF around it + the entity↔DTO mapping to/from Room.
- `EndoRepository` (consent-gated; DAOs for all 10 entities; upsert-by-UUID capability) — the source for export + the target for idempotent import. The Phase-18 delete-day cascade + entity model.
- kotlinx-serialization-json 1.11.0 (catalog), the CopyAudit harness, the Phase-20 Settings screen (add the export/import entries), the composition-root injectable-seam threading pattern, the version-catalog audit + boundary-lint. Android SDK installed; `./gradlew :app:...` builds real; Robolectric+Compose JVM lane.

### Port source (read-only)
- iOS has no encrypted-export equivalent (iOS uses CloudKit sync) — this is a NEW Android capability per §2, not an iOS parity port. The FORMAT was designed platform-neutral in Phase 18 so a future iOS exporter could interoperate (not scheduled).

### Integration points
- Reads from + writes to the Phase-18 EndoRepository/entities. The Settings entry sits alongside the Phase-20 reminder/consent controls + the Phase-21 Health Connect entry. Feeds device-migration + backup + GDPR Art. 20 portability. Must stay notification-free (reminders package is the sole notification owner).

</code_context>

<specifics>
## Specific Ideas

Signature invariants: (1) zero-dependency crypto (javax.crypto only, no security-crypto, no native Argon2) with a self-describing header so params are read-from-file; (2) fresh salt+IV per export (two exports differ) + read-back verification before success (never claim an unverified write); (3) idempotent import by UUID (re-import changes nothing) + gentle distinct error messages (wrong passphrase vs corrupt vs unsupported version); (4) a committed golden fixture so the platform-neutral format can't silently drift. All crypto/format JVM-testable; SAF seam-injected.

</specifics>

<deferred>
## Deferred Ideas

- iOS exporter/importer for cross-platform handoff → NOT scheduled (the format is written to keep that door open; no iOS work here).
- Google Drive appDataFolder auto-sync → post-parity (Future Requirement ACNV-02), not this phase.
- The PDF report → Phase 24. Play Store prep → Phase 25.
</deferred>
