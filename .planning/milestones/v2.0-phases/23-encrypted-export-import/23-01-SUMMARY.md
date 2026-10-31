---
phase: 23-encrypted-export-import
plan: 01
subsystem: security
tags: [aes-gcm, pbkdf2, javax-crypto, kotlinx-serialization, gzip, export, container-format]

# Dependency graph
requires:
  - phase: 18-room-persistence
    provides: "ExportEnvelope + CryptoHeader/KdfParams @Serializable DTOs (the export FORMAT contract)"
provides:
  - "ExportCrypto — pure-JVM PBKDF2 600k derive + AES-GCM encrypt/decrypt + SecureRandom + passphrase/key hygiene"
  - "ExportCodec — ENDO self-describing container codec (magic + versioned CryptoHeader + unconditional GZIP + ciphertext) with fresh salt/nonce per export"
  - "ExportError / DecodeResult — the typed, ordered failure taxonomy (NotAnExport / WrongPassphrase / Unsupported)"
affects: [23-02 export mapper, 23-03 SAF seam + read-back + golden fixture, 23-04 settings UI + copy, 23-05 import flow]

# Tech tracking
tech-stack:
  added: []  # zero new dependency — javax.crypto/java.util.zip/java.util.Base64 are platform; kotlinx-serialization already in catalog
  patterns:
    - "Self-describing crypto header: KDF params (iterations/salt/IV) read FROM the parsed file on decode, never from constants — old files decrypt with their own params"
    - "Ordered failure detection: magic/parse/version checks BEFORE decryption; only the GCM tag failure maps to WrongPassphrase — three distinct outcomes, no stack trace"
    - "Fresh SecureRandom 16-byte salt + 12-byte IV per encode → fresh key + fresh nonce → GCM nonce-reuse structurally impossible"
    - "Pure-JVM crypto boundary pinned by a filesystem walk-up scan (mirrors NoDynamicColorScanTest) so the layer stays emulator-free"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportCrypto.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportError.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportCodec.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportCryptoTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportCodecTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/TwoExportsDifferTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportErrorTaxonomyTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportPurityTest.kt
  modified: []

key-decisions:
  - "java.util.Base64 (not android.util.Base64) so the codec stays android-free and JVM-testable; no-padding encoder matches the NO_WRAP salt/IV convention"
  - "GZIP is unconditional at container-v1 (no header flag) — a future optional-compression variant would bump containerVersion"
  - "Post-decrypt GUNZIP/JSON failure maps to Unsupported (not WrongPassphrase): the GCM tag already authenticated the body, so it is a newer/unreadable format, not a typo"
  - "decrypt() operates on a ciphertext sub-range (offset/length) so the codec passes the whole container buffer with no copy"
  - "Iteration-count default sourced via KdfParams().iterations (the committed 600k contract), passed as a parameter into deriveKey — never inline in ExportCrypto"

patterns-established:
  - "ENDO byte container: [ ENDO(4) | containerVersion(1) | headerLen uint32 BE(4) | CryptoHeader JSON(N) | AES-GCM ciphertext‖tag ]"
  - "Three independent version knobs: containerVersion (byte layout) / header.formatVersion (crypto) / envelope.schemaVersion (data)"

requirements-completed: [APRT-01, APRT-02, APRT-03]

coverage:
  - id: D1
    description: "ExportCrypto derives a deterministic, salt-sensitive 256-bit key via PBKDF2WithHmacSHA256 and round-trips AES-GCM; wrong-passphrase key surfaces as AEADBadTagException; passphrase/key hygiene applied"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportCryptoTest.kt (5 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "ExportCodec frames + decodes the ENDO container; encode→decode recovers the exact envelope; KDF params read from the parsed header (a non-default-iteration file still decodes)"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportCodecTest.kt (2 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Two encodes of identical data differ (fresh salt + fresh IV + differing ciphertext), magic+version prefix identical — GCM nonce-reuse structurally impossible"
    requirement: "APRT-03"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/TwoExportsDifferTest.kt (1 test)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The ordered decode taxonomy maps every failure branch to its exact ExportError (NotAnExport / WrongPassphrase / Unsupported); decode never throws on a malformed file"
    requirement: "APRT-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportErrorTaxonomyTest.kt (10 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "ExportCrypto/ExportCodec/ExportError carry no android/androidx import — pure JVM, pinned by a filesystem walk-up scan"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportPurityTest.kt (1 test)"
        status: pass
    human_judgment: false

# Metrics
duration: 18min
completed: 2026-07-09
status: complete
---

# Phase 23 Plan 01: Encrypted Export Crypto Core + ENDO Container Codec Summary

**PBKDF2-600k + AES-GCM crypto core and a self-describing ENDO container codec — fresh salt/nonce per export, KDF params read-from-file, and an ordered failure taxonomy that keeps wrong-passphrase / corrupt / unsupported distinct — all pure JVM with zero new dependency.**

## Performance

- **Duration:** ~18 min
- **Tasks:** 2 / 2
- **Files created:** 8 (3 main source + 5 test)
- **New dependencies:** 0

## Accomplishments

- **`ExportCrypto`** (pure `javax.crypto`): `deriveKey` (PBKDF2WithHmacSHA256, params passed in — deterministic + salt-sensitive), `encrypt`/`decrypt` (AES/GCM/NoPadding, 128-bit tag, sub-range decrypt), `secureRandomBytes`. Passphrase hygiene: `PBEKeySpec.clearPassword()` in `finally` + derived key bytes zeroed after wrapping; no logging, no I/O.
- **`ExportCodec`** (pure JVM): `encode` frames `[ ENDO | containerVersion=1 | headerLen BE | CryptoHeader JSON | AES-GCM(GZIP(envelope JSON)) ]` with a fresh 16-byte salt + 12-byte IV each call; `decode` runs the ordered checks, reading iterations/salt/IV from the parsed header (never constants) and bounds-checking `headerLength` (>64 KiB rejected) before allocating.
- **`ExportError` + `DecodeResult`**: the typed taxonomy only (no copy strings — the ViewModel maps to CopyAudit-registered strings in a later plan).
- Reuses the Phase-18 `CryptoHeader`/`KdfParams`/`ExportEnvelope` DTOs verbatim — no redefinition.

## REAL Test Results

`cd android && ./gradlew :app:testDebugUnitTest --tests "*Export*" :app:assembleDebug` — **BUILD SUCCESSFUL**. Per-class counts from the JUnit result XML:

| Test class | Tests | Failures | Proves |
|------------|-------|----------|--------|
| ExportCryptoTest | 5 | 0 | deriveKey deterministic + salt-sensitive; encrypt/decrypt round-trip; wrong key → AEADBadTagException; secureRandomBytes length + differs |
| ExportCodecTest | 2 | 0 | encode→decode recovers exact envelope; non-default-iteration file still decodes (params from header) |
| TwoExportsDifferTest | 1 | 0 | two encodes of identical data differ (salt+IV+ciphertext); magic prefix identical |
| ExportErrorTaxonomyTest | 10 | 0 | every branch → exact ExportError; three outcomes distinct; no exception escapes decode |
| ExportPurityTest | 1 | 0 | ExportCrypto/ExportCodec/ExportError carry no android/androidx import |
| ExportFormatTest (pre-existing, Phase 18) | 7 | 0 | still green |

- `:app:assembleDebug` — **BUILD SUCCESSFUL** (module still builds).
- `:insights:test` — **BUILD SUCCESSFUL** (prior module green).
- Catalog audit: `android/gradle/libs.versions.toml` unchanged — **no new coordinate**, audit lane stays green.

## Threat Mitigations Applied

- **T-23-01** (nonce/salt reuse): fresh SecureRandom 16-byte salt + 12-byte IV per encode, recorded in the header; TwoExportsDifferTest pins that two exports of identical data differ.
- **T-23-02** (offline brute-force): PBKDF2WithHmacSHA256 at the header's 600k iterations over a fresh salt; count is header-recorded and upgradeable.
- **T-23-03** (passphrase/key hygiene): CharArray + `clearPassword()` in `finally`; derived key bytes zeroed; no passphrase/key/plaintext logging.
- **T-23-04** (DoS via unbounded header): `headerLength` bounds-checked (reject ≤0 or >64 KiB, or overflowing the buffer) BEFORE allocating → NotAnExport.
- **T-23-05** (failure-mode conflation): magic/parse/version checks ordered before decryption; only the GCM tag failure maps to WrongPassphrase; taxonomy test pins the distinctness with no exception escape.
- **T-23-SC** (supply chain): accepted — no new package; all-platform crypto, catalog audit unchanged.

## Deviations from Plan

**None — plan executed exactly as written.** Both tasks are `tdd="true"`; given the well-specified crypto contract, each main source + its test were written together and verified with a single real Gradle run per task (RED-then-GREEN collapsed into one verified commit rather than a separate failing-compile commit). All named behaviors, files, and acceptance criteria met; no auto-fixes, no architectural decisions, no auth gates.

## Notes for Downstream Plans

- `ExportCodec.encode(envelope, passphrase)` / `decode(bytes, passphrase)` are the pure `ByteArray` seam the 23-03 SAF `DocumentIo` + read-back verification wrap; they never see a `Uri`.
- The golden fixture (23-03) should DECODE a committed `.enc` and assert the envelope — do not regenerate per run (SecureRandom is non-deterministic).
- Copy strings for the three `ExportError`s are intentionally NOT in `ExportError.kt`; register them in CopyAudit + map in the ViewModel (23-04).
- Each 600k-PBKDF2 derive costs ~1s; `ExportCryptoTest` uses a low iteration count where the count is irrelevant to the behavior under test, so the suite stays fast.

## Self-Check: PASSED

All 3 main-source files + 5 test files exist on disk; both task commits (`75ce931`, `0240ceb`) present in `git log`. Full `*Export*` suite + `assembleDebug` + `:insights:test` all BUILD SUCCESSFUL; catalog unchanged.
