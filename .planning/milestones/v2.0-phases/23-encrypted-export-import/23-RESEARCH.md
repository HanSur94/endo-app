# Phase 23: Encrypted Export/Import - Research

**Researched:** 2026-07-09
**Domain:** Passphrase-encrypted, zero-dependency local file export/import (PBKDF2 + AES-GCM via `javax.crypto`) written/read through the Storage Access Framework, encrypting the already-defined Phase-18 `ExportEnvelope` and reading params from the Phase-18 `CryptoHeader`.
**Confidence:** HIGH — the crypto spec is pinned (STACK.md, verified against developer.android.com), the format contract already exists in committed source (`ExportEnvelope`/`CryptoHeader`), and the repository/DAO round-trip surface is in place. This phase is mechanical assembly of verified pieces, not exploration.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Crypto (zero-dependency, `javax.crypto` only)**
- Use the PLATFORM `javax.crypto` ONLY — NO Tink, NO Argon2 native lib, and DO NOT use the deprecated `androidx.security:security-crypto`. Per STACK.md: KDF `PBKDF2WithHmacSHA256` with ≥600,000 iterations + a 16-byte random salt; encryption `AES/GCM/NoPadding` with a 12-byte random IV + 128-bit tag; 256-bit key. FRESH salt + IV per export (two exports of identical data must differ — a test proves it). The `CryptoHeader` (already a DTO from Phase 18) is written as a self-describing plaintext header (version, KDF params, salt, IV) so import reads the params from the file — never hard-codes them. The catalog audit must stay green (no new crypto dependency — `javax.crypto` is platform).

**Export format (implement the Phase-18 contract)**
- Serialize all user data to the versioned, platform-neutral `ExportEnvelope` (schema-version field, ISO-8601 dates, enum raw values matching the shared domain vocabulary, UUID identity, `CachedInsight` EXCLUDED) via kotlinx-serialization (already in the catalog). Then: envelope JSON → GZIP (optional per plan) → AES-GCM encrypt → prepend the plaintext `CryptoHeader` → write. Platform-neutral by design (a future iOS importer COULD read it) — no Android-isms in the JSON.

**Export flow (SAF, zero permissions)**
- Settings → "Export my data" → choose passphrase (strength/confirmation UX; explain it CANNOT be recovered — no backdoor) → serialize + encrypt + write through SAF (`ACTION_CREATE_DOCUMENT` / `openOutputStream(uri, "wt")` truncate mode) — user picks destination. ZERO storage permissions. Read-back verification: after writing, re-open + decrypt + validate BEFORE showing success. All on-device, no network.

**Import flow (SAF, idempotent)**
- "Import data" → SAF `ACTION_OPEN_DOCUMENT` → passphrase → read header → derive key → decrypt → validate schema version (reject unknown/newer clearly) → deserialize → IDEMPOTENT upsert by app-generated UUIDs (re-import changes nothing; existing rows updated in place). Consent-gated (import writes health data). Distinguish failures GENTLY: wrong passphrase (AEADBadTagException) vs corrupt/not-an-export (parse/header failure) vs unsupported version — each a calm, specific message, never a stack trace.

**Verification / honesty**
- A committed GOLDEN fixture pins the format (known envelope + known passphrase → known data). Read-back verification before success. Two-exports-differ test (fresh salt/nonce). Round-trip test (export → import → identical, idempotent on re-import). Wrong-passphrase and corrupt-file tests. All JVM-runnable (`javax.crypto` works on the JVM; SAF interactions are seam-injected).

**Copy discipline**
- Every new user-facing string registered in CopyAudit in the same change; dodge ClaimsPolicy substrings (`should/use/her/start/treat/stop/take/here/where`). Neutral/non-diagnostic; the passphrase-cannot-be-recovered warning must be clear and honest.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.export` (the Phase-18 DTOs live there), whether to GZIP, the SAF seam design (injectable so crypto/format is JVM-testable without a real picker), the passphrase-strength UX, and whether export/import run as foreground coroutines (they do — NO WorkManager/notification; the reminders package stays the sole notification owner). Guided by STACK.md + PITFALLS.md.

### Deferred Ideas (OUT OF SCOPE)
- iOS exporter/importer for cross-platform handoff → NOT scheduled (format kept open; no iOS work here).
- Google Drive appDataFolder auto-sync → post-parity (ACNV-02), not this phase.
- The PDF report → Phase 24. Play Store prep → Phase 25.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| APRT-01 | Export all data as a passphrase-encrypted file (PBKDF2 + AES-GCM) to a SAF-picked destination — zero storage permissions | Crypto pipeline (`ExportCodec.encode`), the container byte layout, `DocumentIo` SAF write with `"wt"`, and the zero-permission manifest assertion — see Architecture Patterns, Code Examples, Security Domain |
| APRT-02 | Import: passphrase → decrypt → schema-version validation → idempotent upsert by UUID; wrong-passphrase and corrupt-file distinguished gently | `ExportCodec.decode`, the DTO→entity map, consent-gated bulk upsert (`OnConflictStrategy.REPLACE` on UUID PK), and the exception→message error taxonomy — see Entity↔DTO Mapping, Error Taxonomy, Validation Architecture |
| APRT-03 | Format proven by a committed golden fixture + read-back decrypt verification before any success; two exports of identical data differ (fresh salt/nonce) | Fresh `SecureRandom` salt+IV per export, read-back verify seam, and the committed golden `.enc` artifact — see Verification Patterns, Validation Architecture |
</phase_requirements>

## Summary

Everything hard about this phase is already decided or already built. The **format** (`ExportEnvelope` + 9 per-entity `@Serializable` DTOs) and the **crypto header contract** (`CryptoHeader` + `KdfParams`, defaults `PBKDF2WithHmacSHA256` / 600,000 / 256-bit / 16-byte salt / 12-byte IV) are committed source from Phase 18 (plan 06). The **round-trip surface** exists: every entity DAO exposes an `all*()` read (export source) and an `@Insert(onConflict = REPLACE)` upsert keyed on the UUID `id` primary key (idempotent import target), all behind the consent-gated `EndoRepository`. Phase 23 wires `javax.crypto` and the Storage Access Framework around this contract — no new dependency, no schema change, no format re-opening.

The whole crypto+format+DTO+round-trip surface is **pure-JVM testable** because `javax.crypto`, `SecureRandom`, and `kotlinx-serialization` all run on the plain JVM in the `:app` `test/` (JUnit4) lane — no Robolectric, no emulator. The only genuinely device-gated pieces are the real SAF document-picker chooser UX and the cross-provider `"wt"` truncate behavior. The key architectural move is a thin `DocumentIo` seam (real `SafDocumentIo` over `contentResolver` + a fake in-memory impl) so the picker never appears in a test, plus keeping the codec (`encode`/`decode`) a pure function over `ByteArray`.

**Primary recommendation:** Build a pure `ExportCodec` (envelope ⇄ bytes, javax.crypto + kotlinx-serialization, zero Android imports) framed by a 9-byte binary container header (`"ENDO"` magic + container-version byte + big-endian header-length) wrapping the length-prefixed `CryptoHeader` JSON, with GZIP applied unconditionally in container-v1. Inject file I/O through a `DocumentIo` interface. Run KDF off the main thread with an indeterminate progress indicator. Verify by re-reading and decrypting the just-written bytes before any success message. Pin the wire format with a committed golden `.enc` fixture that tests only ever decode.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Passphrase → key derivation (PBKDF2) | Crypto core (`ExportCrypto`, pure JVM) | — | Deterministic, testable, no Android; runs off-main via coroutine dispatcher |
| AES-GCM encrypt/decrypt + container framing | Crypto core (`ExportCodec`, pure JVM) | — | `javax.crypto` + `kotlinx-serialization` are JVM libraries; keep Android out for testability |
| Envelope ⇄ entity mapping (DTO ↔ Room) | Data mapping (`ExportMapper`, pure JVM) | — | Pure transform; `epochDay`↔`day`, `millis`↔ISO conversions belong in one tested place |
| Read all rows for export / idempotent bulk upsert | Persistence (`EndoRepository`, consent-gated) | Room DAOs | The consent gate + UUID-PK REPLACE upsert already own idempotency and the health-data access boundary |
| File read/write to a user-picked location | SAF adapter (`SafDocumentIo` over `ContentResolver`) | `DocumentIo` interface (fake for tests) | SAF is the zero-permission, user-chosen-destination Android API; behind an interface so the core stays JVM-testable |
| Passphrase entry, strength/confirm, progress, error surfaces | UI (Compose, Settings screen) | ViewModel/coroutine | Foreground coroutines (Dispatchers.Default for KDF); NO WorkManager, NO notification (reminders package owns notifications) |
| Consent enforcement on import (writing health data) | Persistence (`EndoRepository.checkConsented`) | — | Import writes special-category health data; the existing hard-throw gate is the access control |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `javax.crypto` / `java.security` | platform (API 26+) | PBKDF2 key derivation + AES-GCM authenticated encryption + `SecureRandom` | `[CITED: STACK.md; developer.android.com/reference/javax/crypto/SecretKeyFactory]` `PBKDF2WithHmacSHA256` available since API 26 (= minSdk 26); `AES/GCM/NoPadding` Conscrypt-backed on every supported device. Zero dependency — the locked posture. |
| `kotlinx-serialization-json` | 1.11.0 (catalog) | Serialize/deserialize `ExportEnvelope` + `CryptoHeader` JSON | `[CITED: STACK.md]` Already in the version catalog and already used for the Phase-18 DTOs and the insight cache. No second JSON stack. |
| `java.util.zip.GZIP*` | platform | Compress envelope JSON before encrypt | `[VERIFIED: platform]` JDK-standard, on Android since forever; no dependency. |
| `androidx.activity` result contracts (`ActivityResultContracts.CreateDocument` / `OpenDocument`) | via `activity-compose` (catalog) | Launch SAF picker from Compose | `[CITED: STACK.md]` `activity-compose` already in the catalog for the Compose host. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `android.content.ContentResolver` | platform | `openOutputStream(uri, "wt")` / `openInputStream(uri)` | The SAF read/write primitive behind `SafDocumentIo`. Zero permissions. |
| `kotlinx.coroutines` (`Dispatchers.Default`/`IO`) | via catalog | Run the ~600k-iteration KDF and stream I/O off the main thread | Every export/import — PBKDF2 on the main thread is an ANR (PITFALLS Performance Traps). |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `javax.crypto` PBKDF2+AES-GCM | Tink `tink-android` | Adds a dependency without adding security for one self-contained passphrase→AEAD file; rejected in STACK.md/CONTEXT. |
| PBKDF2WithHmacSHA256 | Argon2id (native lib) | Memory-hard and stronger on paper, but not in the Android platform → native dependency against the locked zero-dep posture. The self-describing header makes a future KDF swap non-breaking. |
| Length-prefixed JSON `CryptoHeader` | Fixed binary struct for the header | The `CryptoHeader`/`KdfParams` are already `@Serializable` (Phase-18 contract); reusing kotlinx-serialization keeps ONE source of truth and stays platform-neutral/upgradeable. A hand-rolled binary struct would duplicate the contract. |
| `androidx.security:security-crypto` `EncryptedFile` | — | DEPRECATED (April 2025) AND Keystore-bound keys defeat the purpose: the file must decrypt on a *different* device from the passphrase alone. Explicitly forbidden. |

**Installation:** None. Every dependency above is either platform (`javax.crypto`, GZIP, SAF, coroutines) or already in `android/gradle/libs.versions.toml` (kotlinx-serialization, activity-compose). The catalog-audit test stays green.

## Package Legitimacy Audit

**No external packages are installed this phase.** All crypto is `javax.crypto`/`java.security` (JDK/Android platform), compression is `java.util.zip` (platform), file access is SAF/`ContentResolver` (platform), and serialization reuses the already-present `kotlinx-serialization-json:1.11.0`. There is nothing to add to `libs.versions.toml`; the version-catalog audit (which fails if a forbidden coordinate appears) must remain green, and adding a crypto dependency would violate the locked zero-dependency invariant. Package-legitimacy verdict: **N/A — no new packages** (Disposition: none).

## Architecture Patterns

### System Architecture Diagram

```
EXPORT (foreground coroutine; NO WorkManager, NO notification)
  Settings "Export my data" tap
        │
        ▼
  Passphrase entry UI ──(strength + confirm + "cannot be recovered" warning)
        │  passphrase: CharArray
        ▼
  EndoRepository.exportSnapshot()  ──checkConsented() FIRST──▶ DAOs.all*()  (10 entities, CachedInsight excluded)
        │  List<entity> per type
        ▼
  ExportMapper.toEnvelope()   entity → DTO   (epochDay→yyyy-MM-dd, millis→ISO-8601)
        │  ExportEnvelope(schemaVersion=1, …)
        ▼
  ExportCodec.encode(envelope, passphrase)         [PURE JVM — no Android]
     kotlinx JSON → GZIP → AES-GCM(key = PBKDF2(pass, freshSalt, 600k)) with freshIV
     → frame: [ "ENDO" | containerVer | headerLen(BE) | CryptoHeader JSON | ciphertext+tag ]
        │  ByteArray
        ▼
  ACTION_CREATE_DOCUMENT picker → uri
        │
        ▼
  DocumentIo.write(uri, bytes)  ──contentResolver.openOutputStream(uri,"wt")──▶  file
        │
        ▼
  READ-BACK VERIFY: DocumentIo.read(uri) → ExportCodec.decode(bytes, passphrase) → envelope validates?
        │  yes ──▶ success surface ("backup ready")
        └─ no  ──▶ honest failure (write did not verify)

IMPORT (foreground coroutine)
  Settings "Import data" tap → ACTION_OPEN_DOCUMENT picker → uri
        │
        ▼
  DocumentIo.read(uri) → ByteArray
        │
        ▼
  ExportCodec.decode(bytes, passphrase)            [PURE JVM]
     check "ENDO" magic + containerVer  ──fail──▶ NOT-AN-EXPORT message
     parse CryptoHeader (bounds-checked len) ──fail──▶ NOT-AN-EXPORT message
     check formatVersion known ──newer──▶ UNSUPPORTED message
     PBKDF2(pass, header.salt, header.iterations) → AES-GCM decrypt(header.iv)
        │  AEADBadTagException ──▶ WRONG-PASSPHRASE message
     GUNZIP → kotlinx JSON → ExportEnvelope
     check envelope.schemaVersion known ──newer──▶ UNSUPPORTED message
        │  ExportEnvelope
        ▼
  ExportMapper.toEntities()   DTO → entity
        │
        ▼
  EndoRepository.importEnvelope()  ──checkConsented() FIRST──▶ @Transaction { upsert REPLACE by UUID }
        │  re-import of same file ⇒ every row REPLACEs itself ⇒ no duplicates, no change
        ▼
  success surface
```

File-to-implementation mapping lives in the Component Responsibilities below, not the diagram.

### Recommended Project Structure
```
com/endoapp/app/export/
├── ExportEnvelope.kt        # EXISTS (Phase 18) — 9 @Serializable DTOs, DO NOT re-open
├── CryptoHeader.kt          # EXISTS (Phase 18) — CryptoHeader + KdfParams, DO NOT re-open
├── ExportCrypto.kt          # NEW — PBKDF2 derive + AES-GCM encrypt/decrypt (pure javax.crypto)
├── ExportCodec.kt           # NEW — envelope ⇄ ByteArray: JSON+GZIP+crypto+container framing (pure JVM)
├── ExportMapper.kt          # NEW — entity ⇄ DTO (epochDay↔day, millis↔ISO); the ONE conversion place
├── DocumentIo.kt            # NEW — interface { write(uri,bytes); read(uri) } + SafDocumentIo impl
├── ExportError.kt           # NEW — sealed failure taxonomy (NotAnExport/WrongPassphrase/Unsupported)
└── ExportImportViewModel.kt # NEW — foreground coroutines, progress state, error→message mapping
```
(Package layout is Claude's discretion per CONTEXT; this mirrors the existing `com.endoapp.app.export` home of the DTOs.)

### Component Responsibilities
| File | Owns |
|------|------|
| `ExportCrypto` | `PBKDF2WithHmacSHA256` derive → `SecretKeySpec("AES")`; `Cipher("AES/GCM/NoPadding")` + `GCMParameterSpec(128, iv)`; `SecureRandom` salt+IV; `PBEKeySpec.clearPassword()` hygiene. No Android, no I/O. |
| `ExportCodec` | Container framing (magic/version/length), kotlinx JSON, GZIP, and calling `ExportCrypto`. `encode(envelope, CharArray): ByteArray`, `decode(ByteArray, CharArray): DecodeResult`. Pure JVM. |
| `ExportMapper` | The 9 entity↔DTO conversions incl. `epochDay: Long`↔`day: String` and `*Millis: Long`↔ISO instant. Pure JVM. |
| `EndoRepository` (extend) | `exportSnapshot()` (gated read of all rows) + `importEnvelope()` (gated `@Transaction` bulk REPLACE upsert). |
| `DocumentIo` / `SafDocumentIo` | The ONLY Android file-I/O; `"wt"` write mode; fake impl for tests. |
| `ExportImportViewModel` | Orchestration, off-main dispatch, progress, and mapping `ExportError` → CopyAudit-registered strings. |

### Pattern 1: Container byte layout (self-describing, params-read-from-file)
**What:** A fixed 9-byte binary frame around the Phase-18 `CryptoHeader` JSON, then the GCM ciphertext.
**When to use:** Every export; every import parses it in this exact order.

```
Offset  Size       Field                Notes
0       4 bytes    magic = ASCII "ENDO" 0x45 0x4E 0x44 0x4F — reject a non-export in the first 4 bytes,
                                          BEFORE any parse or key derivation (cheap, greppable-proof)
4       1 byte     containerVersion=1   byte-layout version — distinct from header.formatVersion AND
                                          envelope.schemaVersion (three independent version knobs)
5       4 bytes    headerLength (uint32, BIG-ENDIAN)   length of the CryptoHeader JSON; bounds-check
                                          (reject > 64 KiB) before allocating — untrusted input (V5)
9       N bytes    CryptoHeader JSON     {"magic":"ENDOEXPORT","formatVersion":1,
                                          "kdf":{"alg":"PBKDF2WithHmacSHA256","iterations":600000,
                                                 "saltBase64":"…16 bytes…","keyBits":256},
                                          "ivBase64":"…12 bytes…"}   ← params READ FROM HERE, never constants
9+N     rest       AES-GCM ciphertext    GZIP(envelope JSON) encrypted; the 128-bit tag is appended by
                                          javax.crypto's doFinal (Cipher output = ciphertext‖tag)
```

- **Endianness discipline:** the length prefix is big-endian (`ByteBuffer.order(ByteOrder.BIG_ENDIAN)`). The Base64 salt/IV inside the JSON are byte-order-neutral.
- **Recommend JSON header over a hand-rolled binary struct:** `CryptoHeader`/`KdfParams` are already `@Serializable` (Phase-18 contract) — reuse them so there is a single source of truth, the params stay upgradeable (`iterations` can rise), and the format stays platform-neutral. The salt (`kdf.saltBase64`) and IV (`ivBase64`) live in the header JSON, so no separate binary salt-len/iv-len fields are needed.
- **Two-level magic:** the leading ASCII `"ENDO"` bytes reject a wrong file cheaply; the in-JSON `magic:"ENDOEXPORT"` is a second confirmation after parse. Both map to the not-an-export message.

### Pattern 2: GZIP unconditionally in container-v1
**What:** `envelope JSON → GZIPOutputStream → encrypt`; import always `decrypt → GZIPInputStream → JSON`.
**When to use:** Always at container-version 1 (no header flag needed — import inflates unconditionally).
- Health-history JSON (repeated field names, ISO dates, enum raws) is highly compressible → smaller files, less to encrypt/write.
- **Compression-oracle (CRIME/BREACH) does NOT apply:** those attacks need an attacker who can inject chosen plaintext into the same compression context AND observe ciphertext length across many adaptive requests. This is a single static offline file with no attacker-chosen prefix and no repeated oracle. Compress-then-encrypt is safe here.
- Making GZIP an inherent property of container-v1 avoids adding a `compressed` flag the Phase-18 `CryptoHeader` contract does not carry. A future optional-compression variant would bump `containerVersion`.

### Pattern 3: The `DocumentIo` seam (JVM-testable crypto without a picker)
**What:** All Android file I/O behind an interface; the codec is a pure `ByteArray` function.
**When to use:** Everywhere — this is what makes APRT-01/02/03 JVM-testable.

```kotlin
interface DocumentIo {
    suspend fun write(uri: Uri, bytes: ByteArray)   // real: openOutputStream(uri, "wt")
    suspend fun read(uri: Uri): ByteArray            // real: openInputStream(uri)
}
```
- Real `SafDocumentIo(private val resolver: ContentResolver)` uses `"wt"` and buffered streams on `Dispatchers.IO`.
- Fake `InMemoryDocumentIo` backs a `MutableMap<Uri, ByteArray>` (or the tests operate on `ExportCodec` directly with raw `ByteArray`, never constructing a `Uri`). Either way the picker and `ContentResolver` never appear in a `test/` case.
- Because `ExportCodec.encode/decode` are pure `ByteArray` functions, the round-trip, wrong-passphrase, corrupt, version, two-exports-differ, and golden tests all run in the JUnit4 `:app` `test/` lane with no Robolectric.

### Anti-Patterns to Avoid
- **Hard-coding KDF params on import:** always read `iterations`/`salt`/`iv` from the parsed `CryptoHeader`. Constants strand old files after a param bump (defeats the whole self-describing-header point).
- **`openOutputStream(uri)` with no mode:** may leave stale trailing bytes when a shorter file overwrites a longer one → GCM tag failure on import → the app tells the user their backup is corrupt when the app corrupted it. Always `"wt"`.
- **Success toast before read-back verify:** never claim a backup succeeded on an unverified write.
- **Passphrase as `String`:** immutable, lingers in memory. Use `CharArray` and `PBEKeySpec.clearPassword()`; zero the derived key bytes after wrapping.
- **`ByteArray(12)` zero/deterministic IV or fixed salt:** catastrophic GCM nonce reuse across a user's backups. Fresh `SecureRandom` salt+IV per export, structurally.
- **Trusting `headerLength` unbounded:** a corrupt/malicious file claiming a 2 GB header OOMs — bounds-check before allocating.
- **WorkManager or a notification for export/import:** locked out — foreground coroutines only; the reminders package is the sole notification owner.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Authenticated encryption | A custom encrypt-then-MAC or raw AES-CBC | `Cipher("AES/GCM/NoPadding")` + `GCMParameterSpec(128, iv)` | GCM gives confidentiality + integrity in one primitive; the tag IS the wrong-passphrase/tamper signal. |
| Password key derivation | A custom SHA-256 loop / low-iteration hash | `SecretKeyFactory("PBKDF2WithHmacSHA256")` + `PBEKeySpec(pass, salt, 600_000, 256)` | Offline brute-force is *the* threat for a file parked in the user's Drive; OWASP-current iterations, platform-verified. |
| Randomness | `Random()` / `System.nanoTime()` seeds | `SecureRandom` | Salt/IV must be cryptographically random; a predictable IV reintroduces nonce reuse. |
| Header/format serialization | A bespoke binary parser for the params | `kotlinx-serialization` over the existing `CryptoHeader`/`KdfParams` | The contract is already `@Serializable`; reuse keeps one source of truth and stays platform-neutral. |
| File access to a user location | Storage permissions + raw `File` paths | SAF `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` + `ContentResolver` | Zero permissions, user-chosen destination, works with Drive/local/Samsung providers. |
| Idempotent import | Manual "does this id exist?" select-then-insert | `@Insert(onConflict = OnConflictStrategy.REPLACE)` on the UUID PK | Already the DAO contract; REPLACE also resolves the `ImportedSample.hcRecordId` unique-index conflict. |

**Key insight:** In this domain the platform already provides every hard primitive correctly; the risk is *composition* mistakes (nonce reuse, unverified writes, hard-coded params, unbounded input), not missing libraries. The research value is the exact composition + the test net, not new code to invent.

## Runtime State Inventory

Not applicable — this is a **greenfield feature** (a new Android capability, not a rename/refactor/migration). No stored data keys, live service config, OS-registered state, secret names, or build artifacts are being renamed or migrated. The one persistence touch-point (adding `exportSnapshot()`/`importEnvelope()` to `EndoRepository`) is additive and introduces no schema change — Room schema stays at v1, so `schemas/1.json` and `MigrationTestHelper` are untouched. Verified by: the format contract (`ExportEnvelope`/`CryptoHeader`) and all 10 entities already exist and are unchanged by this phase.

## Common Pitfalls

### Pitfall 1: GCM nonce/salt reuse (PITFALLS 12, Security Mistakes)
**What goes wrong:** Reusing a GCM IV under the same key is catastrophic (auth-subkey recovery, plaintext XOR leak). With passphrase-derived keys, a fixed salt makes the *key* repeat across exports, so the IV must never repeat across all exports ever.
**Why it happens:** Crypto examples optimize for brevity and hardcode a zero IV.
**How to avoid:** Fresh 16-byte salt AND fresh 12-byte IV from `SecureRandom` per export, both stored in the header. Fresh salt → fresh key → nonce-reuse risk structurally collapses.
**Warning signs:** `ByteArray(12)` IV; salt derived from anything deterministic; two exports of identical data whose ciphertext matches.

### Pitfall 2: SAF write leaves stale trailing bytes (PITFALLS 13)
**What goes wrong:** `openOutputStream(uri)` default mode may not truncate; a shorter new ciphertext over a longer old file leaves stale bytes → GCM tag failure on import → user told their backup is corrupt.
**Why it happens:** Providers (Drive vs local vs Samsung) implement modes inconsistently; the emulator local provider hides it.
**How to avoid:** Always `openOutputStream(uri, "wt")`; date-stamp default filenames so `ACTION_CREATE_DOCUMENT` "(1)" siblings don't arise; read-back-verify before success. Import: sniff the header magic, never trust filename/extension.
**Warning signs:** `openOutputStream` with no mode; export tests that only ever write a fresh URI.

### Pitfall 3: Wrong-passphrase vs corrupt-file conflated (PITFALLS UX)
**What goes wrong:** A GCM tag failure shown as "corrupt" makes a user with a typo think their data is gone.
**Why it happens:** All decrypt failures collapse to one catch block.
**How to avoid:** Order the checks so magic/parse/version failures are caught *before* decryption; only `AEADBadTagException`/`BadPaddingException` at `doFinal` maps to wrong-passphrase. See Error Taxonomy.
**Warning signs:** A single `catch (e: Exception)` around the whole import.

### Pitfall 4: PBKDF2 on the main thread (PITFALLS Performance Traps)
**What goes wrong:** 600k iterations ≈ hundreds of ms to a couple seconds on a low-end API-26 device → ANR.
**Why it happens:** The synchronous `SecretKeyFactory` call is easy to leave on the caller's thread.
**How to avoid:** Derive on `Dispatchers.Default`; show an indeterminate progress indicator and disable the button while deriving. `[ASSUMED]` — measure on a real low-end device; keep 600k (the golden fixture and header default pin it).
**Warning signs:** No progress UI; UI frozen on export tap.

### Pitfall 5: Single `doFinal` OOM on large history (PITFALLS tech debt)
**What goes wrong:** `cipher.doFinal(wholeJson)` on a huge byte array can OOM on 2 GB devices as history grows over years.
**Why it happens:** The simple path buffers everything.
**How to avoid:** Fine at v1 sizes (JSON is a few MB at most, and GZIP shrinks it) with a size-guard note; the streaming upgrade path is `CipherInputStream`/`CipherOutputStream` if files exceed a few MB.
**Warning signs:** No size guard; memory spikes on the longest-history profile.

### Pitfall 6: Unbounded `headerLength` from an untrusted file (V5 input validation)
**What goes wrong:** A corrupt/malicious file claims a 2 GB header length; naive `ByteArray(headerLength)` OOMs.
**How to avoid:** Bounds-check `headerLength` (reject `<= 0` or `> 64 KiB`) before allocating; treat as not-an-export.
**Warning signs:** `ByteArray(len)` with `len` read straight from the file.

## Code Examples

### Key derivation (PBKDF2) with hygiene
```kotlin
// ExportCrypto.kt — pure javax.crypto, no Android imports
private const val KDF_ALG = "PBKDF2WithHmacSHA256"   // available since API 26 = minSdk

fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): SecretKey {
    val spec = PBEKeySpec(passphrase, salt, iterations, keyBits)   // keyBits = 256 (BITS, not bytes)
    try {
        val factory = SecretKeyFactory.getInstance(KDF_ALG)
        val keyBytes = factory.generateSecret(spec).encoded          // raw derived bytes
        return SecretKeySpec(keyBytes, "AES").also { keyBytes.fill(0) }
    } finally {
        spec.clearPassword()   // zero the passphrase copy the spec held
    }
}
```
Note: `passphrase` is a `CharArray` (not `String`). The Compose `TextField` vends a `String`; convert to `CharArray` at the boundary and avoid logging it — perfect zeroing is limited by the String the TextField holds, so treat "no leakage to logs/crash reports" as the practical bar. `[ASSUMED — CharArray-from-Compose hygiene; planner to confirm the passphrase-state approach]`

### Encrypt + frame the container
```kotlin
// ExportCodec.encode — pure JVM
fun encode(envelope: ExportEnvelope, passphrase: CharArray): ByteArray {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val iv   = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val iterations = KdfParams().iterations   // 600_000 default from the contract

    val plain = ByteArrayOutputStream().use { bos ->
        GZIPOutputStream(bos).use { it.write(Json.encodeToString(envelope).toByteArray(Charsets.UTF_8)) }
        bos.toByteArray()
    }

    val key = ExportCrypto.deriveKey(passphrase, salt, iterations, 256)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(plain)                       // ciphertext‖128-bit tag

    val header = CryptoHeader(
        kdf = KdfParams(iterations = iterations, saltBase64 = Base64.encodeToString(salt, NO_WRAP)),
        ivBase64 = Base64.encodeToString(iv, NO_WRAP),
    )
    val headerJson = Json.encodeToString(header).toByteArray(Charsets.UTF_8)

    return ByteArrayOutputStream().use { out ->
        out.write("ENDO".toByteArray(Charsets.US_ASCII))          // 4-byte magic
        out.write(1)                                              // 1-byte containerVersion
        out.write(ByteBuffer.allocate(4).order(BIG_ENDIAN).putInt(headerJson.size).array())
        out.write(headerJson)
        out.write(ciphertext)
        out.toByteArray()
    }
}
```
(`Base64`/`NO_WRAP` = `android.util.Base64`; in the pure-JVM test lane use `java.util.Base64` or a thin injected encoder so the codec stays JVM-testable. `[ASSUMED — Base64 provider; planner picks java.util.Base64 to keep encode/decode Android-free]`)

### Decrypt with ordered failure detection
```kotlin
// ExportCodec.decode — the ORDER is the error taxonomy
fun decode(bytes: ByteArray, passphrase: CharArray): DecodeResult {
    if (bytes.size < 9 || !bytes.copyOf(4).contentEquals("ENDO".toByteArray(US_ASCII)))
        return DecodeResult.Failure(ExportError.NotAnExport)          // pre-parse, pre-KDF
    val containerVersion = bytes[4].toInt() and 0xFF
    if (containerVersion > 1) return DecodeResult.Failure(ExportError.Unsupported)
    val headerLen = ByteBuffer.wrap(bytes, 5, 4).order(BIG_ENDIAN).int
    if (headerLen !in 1..65_536 || 9 + headerLen > bytes.size)
        return DecodeResult.Failure(ExportError.NotAnExport)          // bounds-check (V5)

    val header = runCatching {
        Json.decodeFromString<CryptoHeader>(String(bytes, 9, headerLen, UTF_8))
    }.getOrElse { return DecodeResult.Failure(ExportError.NotAnExport) }
    if (header.magic != "ENDOEXPORT") return DecodeResult.Failure(ExportError.NotAnExport)
    if (header.formatVersion > 1)      return DecodeResult.Failure(ExportError.Unsupported)

    val key = ExportCrypto.deriveKey(passphrase, Base64.decode(header.kdf.saltBase64),
                                     header.kdf.iterations, header.kdf.keyBits)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(header.ivBase64)))
    }
    val plain = try {
        cipher.doFinal(bytes, 9 + headerLen, bytes.size - 9 - headerLen)
    } catch (e: AEADBadTagException) {                                // or BadPaddingException
        return DecodeResult.Failure(ExportError.WrongPassphrase)      // wrong pass OR tampered/truncated
    }
    val json = GZIPInputStream(plain.inputStream()).bufferedReader(UTF_8).readText()
    val envelope = runCatching { Json.decodeFromString<ExportEnvelope>(json) }
        .getOrElse { return DecodeResult.Failure(ExportError.Unsupported) } // GCM authenticated ⇒ not a typo
    if (envelope.schemaVersion > 1) return DecodeResult.Failure(ExportError.Unsupported)
    return DecodeResult.Success(envelope)
}
```
Post-decrypt, the GCM tag has already authenticated the plaintext, so a subsequent GZIP/JSON failure is not a wrong passphrase — it is an unreadable/newer body → unsupported.

### SAF write with truncate mode
```kotlin
// SafDocumentIo — the ONLY Android file I/O
override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
        ?: throw IOException("no output stream")           // "wt" = write + TRUNCATE (no stale tail)
}
override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("no input stream")
}
```
Launch the picker from Compose with `ActivityResultContracts.CreateDocument("application/octet-stream")` (suggest `endo-export-2026-07-09.enc`) and `ActivityResultContracts.OpenDocument(arrayOf("*/*"))`. No storage permission is declared or needed.

### Entity ⇄ DTO conversion (the one place the units convert)
```kotlin
// ExportMapper — epochDay:Long ↔ day:String ; *Millis:Long ↔ ISO instant
fun SymptomReading.toDto() = SymptomReadingDto(
    id = id, day = LocalDate.ofEpochDay(epochDay).toString(),      // "2026-07-09"
    typeRaw = typeRaw, severity = severity, note = note,
)
fun SymptomReadingDto.toEntity() = SymptomReading(
    id = id, epochDay = LocalDate.parse(day).toEpochDay(),
    typeRaw = typeRaw, severity = severity, note = note,
)
fun Medication.toDto() = MedicationDto(
    id = id, name = name, note = note,
    createdAt = Instant.ofEpochMilli(createdAtMillis).toString(),  // "2026-07-09T12:34:56Z"
    isArchived = isArchived,
)
fun MedicationDto.toEntity() = Medication(
    id = id, name = name, note = note,
    createdAtMillis = Instant.parse(createdAt).toEpochMilli(), isArchived = isArchived,
)
```

## Entity ↔ ExportEnvelope DTO Mapping

10 Room entities; `CachedInsight` **excluded** (derived cache, recomputable) → 9 exported types, matching `ExportEnvelope`'s 9 arrays. Every upsert is `@Insert(onConflict = OnConflictStrategy.REPLACE)` on the `id: String` (UUID) primary key → idempotent import.

| Entity | DTO | Envelope field | Export read (source) | Import write (target) | Conversion notes |
|--------|-----|----------------|----------------------|-----------------------|------------------|
| `DayEntry` | `DayEntryDto` | `dayEntries` | `DayEntryDao.all()` | `dayEntryDao.upsert` | `epochDay`↔`day` (yyyy-MM-dd) |
| `SymptomReading` | `SymptomReadingDto` | `symptomReadings` | `SymptomDao.allSymptoms()` | `symptomDao.upsertSymptom` | `epochDay`↔`day`; `severity` nullable (absence≠0) |
| `PainPoint` | `PainPointDto` | `painPoints` | `SymptomDao.allPain()` | `symptomDao.upsertPain` | `epochDay`↔`day` |
| `BleedingEvent` | `BleedingEventDto` | `bleedingEvents` | `SymptomDao.allBleeding()` | `symptomDao.upsertBleeding` | `clots` nullable (null≠false) |
| `FactorReading` | `FactorReadingDto` | `factorReadings` | `SymptomDao.allFactors()` | `symptomDao.upsertFactor` | `epochDay`↔`day`; all payload cols nullable |
| `ImportedSample` | `ImportedSampleDto` | `importedSamples` | `SymptomDao.allImported()` | `symptomDao.upsertImported` | `epochDay`↔`day`; `importedAtMillis`↔`importedAt` (ISO); `hcRecordId` UNIQUE index — REPLACE resolves an index conflict too |
| `Medication` | `MedicationDto` | `medications` | `MedicationDao.allMedications()` | `medicationDao.upsertMedication` | `createdAtMillis`↔`createdAt` (ISO); NOT day-scoped |
| `TreatmentState` | `TreatmentStateDto` | `treatmentStates` | `MedicationDao.allTreatments()` | `medicationDao.upsertTreatment` | `startedAtMillis`↔`startedAt` (ISO); NOT day-scoped |
| `DismissedInsight` | `DismissedInsightDto` | `dismissedInsights` | `InsightsDao.allDismissed()` | `insightsDao.insertDismissed` | `dismissedAtMillis`↔`dismissedAt` (ISO); NOT day-scoped |
| `CachedInsight` | — | — (EXCLUDED) | — | — | Derived cache; regenerated on import; excluded per contract (threat T-18-13) |

**Repository additions needed (currently private/absent):** `SymptomDao.allSymptoms/allPain/allBleeding/allFactors`, `DayEntryDao.all`, `SymptomDao.allImported` are used *inside* `daySnapshots()` but not exposed publicly; `MedicationDao.allTreatments()` and `InsightsDao.allDismissed()` exist on the DAO but not on the repository (only `dismissedSignalKeys()`/`latestTreatment()` are). Phase 23 adds two consent-gated methods:
- `suspend fun exportSnapshot(): ExportEnvelope` — `checkConsented()` first, read every `all*()`, map to DTOs.
- `suspend fun importEnvelope(envelope: ExportEnvelope)` — `checkConsented()` first, then a single `@Transaction` bulk upsert (map DTOs → entities, REPLACE each). Wrapping in one `@Transaction` makes import atomic (all-or-nothing) rather than partial on a mid-way crash.

**Consent gate on import:** import writes special-category health data, so it MUST go through the consent-gated repository (`checkConsented()` throws pre-consent, fail-closed on `Unknown`). A pre-consent import test asserts the throw.

**Idempotency:** re-importing the same file REPLACEs every row with byte-identical content keyed by the same UUID PK → no duplicates, no change. Because `OnConflictStrategy.REPLACE` also resolves the `ImportedSample.hcRecordId` UNIQUE index conflict (delete-then-insert), importing a file whose imported rows share an `hcRecordId` with an existing (differently-`id`'d) row is safe too. `[CITED: Room @Insert REPLACE / SQLite ON CONFLICT REPLACE semantics]`

## Error Taxonomy

Three GENTLE, distinct messages (CONTEXT). Copy is CANDIDATE only — the planner must register each in CopyAudit and confirm it dodges the ClaimsPolicy substrings `should/use/her/start/treat/stop/take/here/where`.

| Condition / exception | Detected at (order matters) | `ExportError` | Candidate user message `[ASSUMED — CopyAudit gate]` |
|-----------------------|-----------------------------|---------------|------------------------------------------------------|
| First 4 bytes ≠ `"ENDO"` | pre-parse, pre-KDF | `NotAnExport` | "This doesn't look like an Endo export." |
| `headerLength` out of bounds / truncated frame | pre-parse | `NotAnExport` | "This doesn't look like an Endo export." |
| `CryptoHeader` JSON parse fails (`SerializationException`) | header parse | `NotAnExport` | "This doesn't look like an Endo export." |
| `header.magic ≠ "ENDOEXPORT"` | post-parse | `NotAnExport` | "This doesn't look like an Endo export." |
| `containerVersion` / `formatVersion` > known | version check | `Unsupported` | "This export was made by a newer version of the app." |
| `AEADBadTagException` / `BadPaddingException` at `doFinal` | decrypt | `WrongPassphrase` | "That passphrase didn't match." |
| GZIP/JSON of envelope fails AFTER tag verify | post-decrypt | `Unsupported` | "This export was made by a newer version of the app." |
| `envelope.schemaVersion` > known | post-deserialize | `Unsupported` | "This export was made by a newer version of the app." |

- `AEADBadTagException` fires on a wrong passphrase OR a tampered/truncated ciphertext (e.g., SAF stale-tail from a missing `"wt"`). Both surface as the calm wrong-passphrase message; because our own exports write with `"wt"` and read-back-verify, our files never self-corrupt.
- **Never** surface a stack trace, exception class name, or file content; the ViewModel maps `ExportError` → a registered string only.

## Verification Patterns (APRT-03)

1. **Read-back verification before success:** after `DocumentIo.write(uri, bytes)`, immediately `DocumentIo.read(uri)` and `ExportCodec.decode(readBytes, passphrase)`; only show success if it returns `Success` with a valid envelope. This catches SAF truncation and provider quirks — the only honest success signal.
2. **Fresh salt/nonce per export:** 16-byte salt + 12-byte IV from `SecureRandom` every export. Test: encode the same envelope twice; assert the two byte arrays differ, and specifically that `kdf.saltBase64` and `ivBase64` differ and the ciphertext regions differ (the leading `"ENDO"`+version+length prefix is identical; everything after the header differs).
3. **Committed golden fixture:** a fixed `.enc` artifact + a known passphrase constant → a known `ExportEnvelope`. Because `SecureRandom` is non-deterministic, the golden is **generated once** (with whatever random salt/IV that run produced) and **committed as a fixed captured artifact** — never regenerated per run. A one-off dev generator (`@Ignore`d test or a small `main`) writes it; the committed test only ever DECODES it and asserts the decoded envelope equals the known data. Any future change that breaks reading the v1 wire format fails this test → the format cannot silently drift. Store at e.g. `android/app/src/test/resources/golden/endo-export-golden-v1.enc` (a raw resource, not a string literal).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `androidx.security:security-crypto` `EncryptedFile`/`EncryptedSharedPreferences` | Platform `javax.crypto` PBKDF2 + AES-GCM with a passphrase-derived key | Deprecated April 2025 (1.1.0-alpha07) | Keystore-bound keys can't decrypt on a new device from a passphrase alone — they defeat backup/migration. Passphrase-derived is the correct tool. |
| PBKDF2 ~10k iterations (old tutorials) | ~600,000 iterations (OWASP-current for PBKDF2-HMAC-SHA256) | OWASP guidance ~2023+ | 10k makes an offline-parked file crackable; 600k is the pinned spec. |
| Raw-image/opaque export with no header | Self-describing versioned header (magic + KDF params + IV) | — | Lets `iterations`/KDF evolve without breaking old files and lets import distinguish wrong-passphrase / corrupt / newer-version. |

**Deprecated/outdated:**
- `EncryptedFile` — deprecated + wrong tool here (see above).
- Any Android crypto tutorial using `PBKDF2WithHmacSHA1` or a default (SHA-1) `SecretKeyFactory` — use `PBKDF2WithHmacSHA256`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 600k PBKDF2-HMAC-SHA256 costs ~hundreds of ms to ~2 s on a low-end API-26 device; an indeterminate progress indicator suffices | Pitfalls 4, Standard Stack | If far slower on real hardware, export/import feels stalled; measure on a real device. Do NOT lower iterations below the pinned/golden-pinned 600k without regenerating the golden and bumping a version. |
| A2 | GZIP applied unconditionally in container-v1 (no header flag) is safe (compression-oracle N/A for a static offline file) and correct given the `CryptoHeader` contract carries no `compressed` flag | Pattern 2 | If a stakeholder wants optional/no compression, it needs a container-version bump — cheap but a format decision to confirm. |
| A3 | The candidate error/success/warning copy strings pass CopyAudit and dodge the forbidden substrings | Error Taxonomy, Validation | If a string trips a substring, it must be reworded and re-registered before the phase is green (memory: copy-audit substring gate). |
| A4 | Cross-provider `"wt"` truncate + `ACTION_CREATE_DOCUMENT` "(1)" sibling behavior matches the documented quirks | Pitfalls 2 | Real Drive/Samsung behavior may vary; the read-back verify + date-stamped filenames are the mitigations, exercised on-device. |
| A5 | Keeping `ExportCodec` Android-free (use `java.util.Base64`, inject any Android-only bit) lets the full crypto+format suite run in the `:app` `test/` JUnit4 lane with no Robolectric | DocumentIo seam, Validation | If some Android-only API leaks into the codec, those tests need Robolectric — avoidable with the seam. |

**If a decision is load-bearing (iteration count, compression, copy), the planner should confirm before locking.**

## Open Questions

1. **Passphrase state type in Compose**
   - What we know: `CharArray` + `clearPassword()` is the hygiene target; the crypto layer takes `CharArray`.
   - What's unclear: Compose `TextField` vends an immutable `String`; perfect zeroing is limited.
   - Recommendation: hold the field as `String` in UI state but convert to `CharArray` at the codec boundary, clear it after derive, and never log it. Treat "no leakage to logs/crash reports" as the practical bar. (Claude's discretion — passphrase UX.)
2. **Where the export/import entry lands in Settings**
   - What we know: it joins the Phase-20 reminder/consent controls and the Phase-21 Health Connect entry.
   - Recommendation: a dedicated "Your data" / portability section; keep it notification-free.
3. **Progress granularity**
   - What we know: KDF is the dominant cost; the operation is a few seconds worst case.
   - Recommendation: an indeterminate spinner + disabled button is enough (no determinate progress needed). Confirm against A1's real-device timing.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `javax.crypto` / `java.security` | PBKDF2 + AES-GCM + SecureRandom | ✓ (platform, API 26+) | platform | — (none needed) |
| `PBKDF2WithHmacSHA256` `SecretKeyFactory` | Key derivation | ✓ | since API 26 = minSdk | — |
| `AES/GCM/NoPadding` (Conscrypt) | Encryption | ✓ | platform | — |
| `java.util.zip.GZIP*` | Compression | ✓ | platform | Skip GZIP (format flag/version) — not needed |
| SAF `ContentResolver` / `ActivityResultContracts` | File read/write | ✓ | platform + activity-compose (catalog) | — |
| `kotlinx-serialization-json` | Envelope/header JSON | ✓ | 1.11.0 (catalog) | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none required.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (`junit:junit:4.13.2`) in the `:app` `test/` (Robolectric-free JVM) lane; androidx.test (`androidx.test.ext:junit:1.3.0` + Espresso 3.7.0) in `androidTest/` for the real SAF picker |
| Config file | `android/app/build.gradle.kts` (AGP unit-test + androidTest) — no new config |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*Export*"` |
| Full suite command | `./gradlew :app:testDebugUnitTest :app:assembleDebug` |

`javax.crypto`, `SecureRandom`, GZIP, and kotlinx-serialization all run on the plain JVM, so the entire crypto+format+DTO+round-trip surface is tested in `test/` with NO Robolectric and NO emulator — provided `ExportCodec`/`ExportCrypto`/`ExportMapper` stay Android-free and file I/O is behind `DocumentIo`.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| APRT-01 | Round-trip: `encode` then `decode` recovers the exact envelope | unit (JVM) | `pytest`-equivalent → `./gradlew :app:testDebugUnitTest --tests "*ExportCodecTest"` | ❌ Wave 0 |
| APRT-01 | Zero storage permissions declared (SAF needs none) | unit (manifest scan) | `./gradlew :app:testDebugUnitTest --tests "*ExportManifestTest"` | ❌ Wave 0 |
| APRT-01 | `DocumentIo` write uses `"wt"`; short-over-long leaves no stale tail | androidTest (real provider) | `./gradlew :app:connectedDebugAndroidTest --tests "*SafDocumentIoTest"` | ❌ Wave 0 (device-gated) |
| APRT-02 | Import: schema-version validation + idempotent upsert by UUID (import twice ⇒ identical rows) | unit (in-memory/fake repo) | `./gradlew :app:testDebugUnitTest --tests "*ImportIdempotencyTest"` | ❌ Wave 0 |
| APRT-02 | Wrong passphrase → `WrongPassphrase` (AEADBadTagException mapped) | unit | `./gradlew :app:testDebugUnitTest --tests "*ExportErrorTaxonomyTest"` | ❌ Wave 0 |
| APRT-02 | Corrupt/not-an-export (bad magic, truncated, bad JSON) → `NotAnExport` | unit | same `*ExportErrorTaxonomyTest` | ❌ Wave 0 |
| APRT-02 | Unknown/newer container/format/schema version → `Unsupported` | unit | same `*ExportErrorTaxonomyTest` | ❌ Wave 0 |
| APRT-02 | Import pre-consent throws (consent gate) | unit (fake ConsentStore) | `./gradlew :app:testDebugUnitTest --tests "*ImportConsentTest"` | ❌ Wave 0 (reuse RepositoryConsentTest fakes) |
| APRT-03 | Committed golden `.enc` + known passphrase decodes to known envelope | unit (resource fixture) | `./gradlew :app:testDebugUnitTest --tests "*GoldenFixtureTest"` | ❌ Wave 0 (fixture must be generated + committed) |
| APRT-03 | Read-back verify catches a truncated/failed write before success | unit (fake DocumentIo that truncates) | `./gradlew :app:testDebugUnitTest --tests "*ReadBackVerifyTest"` | ❌ Wave 0 |
| APRT-03 | Two exports of identical data differ (fresh salt/IV) | unit | `./gradlew :app:testDebugUnitTest --tests "*TwoExportsDifferTest"` | ❌ Wave 0 |
| APRT-01/02 | Real SAF picker chooser (CREATE/OPEN) + 600k-KDF progress on a low-end device | manual / androidTest | manual UAT + `connectedDebugAndroidTest` | ❌ device-gated |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*Export*"` (the pure crypto/format/DTO suite — seconds).
- **Per wave merge:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` (full JVM suite + APK).
- **Phase gate:** full JVM suite green + the golden-fixture test green before `/gsd-verify-work`; the device-gated SAF/androidTest run on the CI emulator/device lane.

### Wave 0 Gaps
- [ ] `ExportCrypto.kt` + `ExportCodec.kt` + `ExportMapper.kt` — the pure-JVM core under test.
- [ ] `DocumentIo` interface + `InMemoryDocumentIo` fake (test double so the picker/ContentResolver never appear in `test/`).
- [ ] `android/app/src/test/resources/golden/endo-export-golden-v1.enc` — generated ONCE and committed (with a committed known-passphrase constant + expected-envelope builder).
- [ ] A one-off golden generator (`@Ignore`d test or `main`) — kept in the tree, never run in CI.
- [ ] Reuse the existing fake `ConsentStore` (settable `MutableStateFlow`) and relaxed fake DAOs from `RepositoryConsentTest` for the import-consent + idempotency tests.
- [ ] `ExportManifestTest` — assert no `READ_/WRITE_EXTERNAL_STORAGE`, no `MANAGE_EXTERNAL_STORAGE`, and (still) no `INTERNET`.
- [ ] `SafDocumentIoTest` (androidTest) — the only genuinely device/provider-gated case (short-over-long `"wt"` truncate).

**Device-gated (not JVM):** the real `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` chooser UX, cross-provider `"wt"` truncate behavior (Drive/local/Samsung), and the real KDF-progress timing on a low-end API-26 device. Everything else is JVM.

## Security Domain

`security_enforcement: true`, ASVS level 1, block-on: high.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts; the passphrase is a file-encryption secret, not a login credential |
| V3 Session Management | no | No sessions |
| V4 Access Control | yes | Import writes health data behind `EndoRepository.checkConsented()` (hard throw, fail-closed on `Unknown`) |
| V5 Input Validation | yes | The import file is UNTRUSTED input: magic check, bounds-checked `headerLength` (reject `>64 KiB`), GCM authenticates before the plaintext is trusted, `ignoreUnknownKeys` defensive deserialize, schema-version gate |
| V6 Cryptography | yes | Platform `AES/GCM/NoPadding` (authenticated) + `PBKDF2WithHmacSHA256` 600k; fresh `SecureRandom` salt+IV per export; params read-from-file; never hand-roll; passphrase as `CharArray` + `clearPassword()` + key-byte zeroing |
| V7 Errors & Logging | yes | Map exceptions to calm messages; never log the passphrase, plaintext, or a stack trace to the user |

### Known Threat Patterns for javax.crypto passphrase-encrypted file export
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| GCM nonce/salt reuse across backups | Tampering / Info disclosure | Fresh 16-byte salt + 12-byte IV per export from `SecureRandom`, both in the header; two-exports-differ test |
| Offline brute-force of a file parked in cloud storage | Info disclosure | `PBKDF2WithHmacSHA256` ≥600k iterations (OWASP-current), params recorded in-header for future increase |
| SAF stale-tail tamper / silent write corruption | Tampering | `openOutputStream(uri, "wt")` truncate + GCM tag on import + read-back verify before success |
| Unbounded header length from a crafted file | Denial of Service | Bounds-check `headerLength` before allocating; treat out-of-range as not-an-export |
| Passphrase lingering in memory / logs | Info disclosure | `CharArray` + `PBEKeySpec.clearPassword()`; zero derived key bytes; never log the passphrase or plaintext |
| Keystore-bound key making backups undecryptable on a new device | (feature defeat) | Passphrase-derived key ONLY (no `EncryptedFile`/Keystore); honest "passphrase loss = data loss" UX |
| Import writing health data without consent | Elevation of Privilege | Consent-gated `importEnvelope()`; pre-consent import test asserts the throw |
| Exported file greppable as health data | Info disclosure | Ciphertext body carries no data-type names; header carries only crypto params; neutral date-stamped filename |

## Sources

### Primary (HIGH confidence)
- `android/app/src/main/kotlin/com/endoapp/app/export/ExportEnvelope.kt` + `CryptoHeader.kt` (committed Phase-18 source) — the exact DTO/header contract this phase implements crypto around.
- `android/app/src/main/kotlin/com/endoapp/app/data/{EndoRepository,daos/*,entities/*}.kt` — the `all*()` export reads and `@Insert(REPLACE)` UUID-PK upserts (idempotent import target) + the consent gate.
- `.planning/phases/18-room-persistence/18-06-SUMMARY.md`, `18-04-SUMMARY.md`, `18-03-SUMMARY.md` — the format/contract, projector, cascade, and consent invariants.

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` — the pinned crypto spec (PBKDF2WithHmacSHA256 600k available at API 26, AES/GCM/NoPadding Conscrypt-backed, security-crypto deprecated), cross-checked against developer.android.com and OWASP.
- `.planning/research/PITFALLS.md` (Pitfalls 12–13, Security Mistakes, Performance Traps, "Looks Done But Isn't") — nonce/KDF/format-version, SAF `"wt"` quirk, read-back verify, golden fixture, KDF-off-main.
- `.planning/ROADMAP.md` §Phase 23 + `.planning/REQUIREMENTS.md` APRT-01..03 — goal, success criteria, requirement IDs.

### Tertiary (LOW confidence)
- Real-device 600k-KDF timing (A1), cross-provider `"wt"`/"(1)" behavior (A4) — documented but not measured this session; mitigated by read-back verify + on-device androidTest.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every primitive is platform or already in the catalog; spec pinned + verified in STACK.md; zero new packages.
- Architecture: HIGH — the format/DTO/repository contract already exists in committed source; this is composition.
- Pitfalls: HIGH — enumerated and mapped in PITFALLS.md with tests attached to each.
- Real-device timing / SAF provider behavior: MEDIUM-LOW — device-gated, mitigated by read-back verify and androidTest.

**Research date:** 2026-07-09
**Valid until:** 2026-08-09 (stable — platform APIs + a committed local contract; the only moving part is real-device KDF timing, which only tightens the progress-UX decision).
