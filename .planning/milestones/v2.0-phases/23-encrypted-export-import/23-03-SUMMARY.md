---
phase: 23-encrypted-export-import
plan: 03
subsystem: security
tags: [saf, documentio, read-back-verify, golden-fixture, manifest-permissions, wt-truncate]

# Dependency graph
requires:
  - phase: 23-encrypted-export-import
    plan: 01
    provides: "ExportCodec.encode/decode (pure ByteArray) + DecodeResult/ExportError taxonomy"
  - phase: 18-room-persistence
    provides: "ExportEnvelope + per-entity @Serializable DTOs (the golden envelope is built from these)"
provides:
  - "DocumentIo — the thin SAF seam interface (write/read a Uri) that keeps the codec Uri-free"
  - "SafDocumentIo — the real ContentResolver impl using openOutputStream(uri, \"wt\") truncate + openInputStream, on Dispatchers.IO"
  - "verifyWrite — read-back-verify-before-success (read → decode → true only on Success)"
  - "FakeDocumentIo — in-memory DocumentIo double (truncate knob) so the whole path is pure-JVM testable"
  - "endo-export-golden-v1.enc — committed golden fixture pinning the v1 wire format (decode-only drift guard)"
affects: [23-04 settings UI + copy, 23-05 import flow]

# Tech tracking
tech-stack:
  added: []  # zero new dependency — ContentResolver/Uri are platform, kotlinx.coroutines already on the main classpath, golden I/O is JVM resources
  patterns:
    - "SAF seam: the ONLY android.* file I/O; the codec (23-01) stays a pure ByteArray function behind DocumentIo — the seam is exempt from the ExportPurityTest pure-core scan"
    - "openOutputStream(uri, \"wt\") truncate mode is load-bearing: a shorter ciphertext over a longer old file leaves no stale tail (Pitfall 2 / T-23-07)"
    - "Read-back verify before success: never claim a write succeeded until the just-written bytes re-decode to DecodeResult.Success"
    - "Generate-once golden: SecureRandom is non-deterministic, so the .enc is a captured artifact committed to test resources — the generator stays @Ignore'd, the test only ever DECODES"
    - "DOM-parse (never grep) the manifest for the permission deny-list so an explanatory comment can't false-trip (the Phase-18-06 lesson)"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/export/DocumentIo.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/SafDocumentIo.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ReadBackVerify.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/FakeDocumentIo.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ReadBackVerifyTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportManifestTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/GoldenFixtureGenerator.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/GoldenFixtureTest.kt
    - android/app/src/test/resources/golden/endo-export-golden-v1.enc
    - android/app/src/androidTest/kotlin/com/endoapp/app/export/SafDocumentIoTest.kt
  modified: []

key-decisions:
  - "verifyWrite does the read+decode only (the write happens at the call site) — matching the plan's signature; a read IOException is itself a 'did not verify' → false, never a thrown crash"
  - "FakeDocumentIo keys an in-memory MutableMap<Uri, ByteArray> and defensively copies both ways; a truncateOnWrite/dropLastBytes knob simulates SAF's stale-tail so ReadBackVerifyTest can prove the false-success block"
  - "ExportManifestTest is a NEW dedicated export-scoped guard (explicit storage-permission deny-list + non-vacuity), complementary to the pre-existing ManifestPermissionsTest exact-allow-list — both DOM-parse the same source manifest"
  - "The golden envelope (GoldenFixture.knownEnvelope) is a fixed small set spanning DayEntry/Symptom/Bleeding/Medication DTOs incl. a null-severity (null-health) row; exportedAt is a fixed constant so only salt/IV are non-deterministic"
  - "SafDocumentIoTest exercises the resolver 'wt' truncate over a real cache-dir file Uri (Uri.fromFile) — no interactive picker needed; compiles JVM-side, device/CI-gated run (DeleteDayCascadeTest precedent)"

patterns-established:
  - "DocumentIo/SafDocumentIo: the injectable SAF seam the 23-04/05 ViewModel wires; export = ExportCodec.encode → DocumentIo.write → verifyWrite; import = DocumentIo.read → ExportCodec.decode"
  - "Committed golden .enc + @Ignore'd generate-once producer + decode-only test — the reusable 'freeze a platform-neutral format' pattern"

requirements-completed: [APRT-01, APRT-03]

coverage:
  - id: S1
    description: "A healthy write read-back-decodes to Success (verifyWrite true); a truncated/short write does NOT verify (verifyWrite false) — the honest signal that blocks a false success on an unverified write"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ReadBackVerifyTest.kt (2 tests)"
        status: pass
    human_judgment: false
  - id: S2
    description: "The :app source manifest declares NONE of READ_/WRITE_/MANAGE_EXTERNAL_STORAGE and NO INTERNET (DOM-parsed, comment-safe, with a non-vacuity guard)"
    requirement: "APRT-03"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportManifestTest.kt (1 test)"
        status: pass
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/ManifestPermissionsTest.kt (5 tests, exact allow-list — still green)"
        status: pass
    human_judgment: false
  - id: S3
    description: "The committed endo-export-golden-v1.enc decodes with the KNOWN passphrase constant to the KNOWN ExportEnvelope — the platform-neutral v1 wire format cannot silently drift; the generator stays @Ignore'd (1 skipped)"
    requirement: "APRT-03"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/GoldenFixtureTest.kt (1 test)"
        status: pass
    human_judgment: false
  - id: S4
    description: "SafDocumentIo write uses openOutputStream(uri, \"wt\") truncate; short-over-long leaves no stale tail (device-gated proof compiles in the androidTest lane)"
    requirement: "APRT-01"
    verification:
      - kind: build
        ref: ":app:compileDebugAndroidTestKotlin (SafDocumentIoTest compiles)"
        status: pass
      - kind: instrumented
        ref: "android/app/src/androidTest/kotlin/com/endoapp/app/export/SafDocumentIoTest.kt (device/CI-gated run)"
        status: deferred
    human_judgment: true

# Metrics
duration: 22min
completed: 2026-07-10
status: complete
---

# Phase 23 Plan 03: DocumentIo SAF Seam + Read-Back Verify + Committed Golden Fixture Summary

**The thin `DocumentIo` SAF seam (real `SafDocumentIo` with the load-bearing `openOutputStream(uri, "wt")` truncate + an in-memory `FakeDocumentIo` double), the read-back-verify-before-success step that turns "wrote a file" into "wrote a file I confirmed is decryptable", a committed generate-once golden `.enc` that pins the platform-neutral v1 wire format against silent drift, and a DOM-parsed manifest guard proving zero storage permissions and no INTERNET. Zero new dependency; the crypto/codec (23-01) stays a pure `ByteArray` function behind the seam.**

## Performance

- **Duration:** ~22 min
- **Tasks:** 2 / 2
- **Files created:** 10 (3 main source + 6 test + 1 committed binary fixture)
- **New dependencies:** 0

## Accomplishments

- **`DocumentIo`** (interface) + **`SafDocumentIo`** (real): `write` = `openOutputStream(uri, "wt")?.use { write; flush } ?: throw IOException`; `read` = `openInputStream(uri)?.use { readBytes() } ?: throw IOException` — both on `Dispatchers.IO`. The `"wt"` (write + TRUNCATE) mode is the primary T-23-07 mitigation: a shorter ciphertext over a longer prior file leaves no stale trailing bytes. The seam is the ONLY android.* file I/O — the codec never sees a `Uri`.
- **`ReadBackVerify.verifyWrite(io, uri, passphrase)`**: reads the just-written URI back, runs `ExportCodec.decode`, returns `true` ONLY on `DecodeResult.Success`. A read `IOException` is itself a "did not verify" → `false`. This is the second, independent guard beside `"wt"`.
- **`FakeDocumentIo`** (test double): in-memory `MutableMap<Uri, ByteArray>` with defensive copies + a `truncateOnWrite`/`dropLastBytes` knob so the whole export→write→read→import path runs pure-JVM (Robolectric only for the `Uri` type) with no `ContentResolver` and no document picker.
- **`ExportManifestTest`**: DOM-parses the source `AndroidManifest.xml`, collects each `<uses-permission>` `android:name`, and asserts the set contains none of `READ_/WRITE_/MANAGE_EXTERNAL_STORAGE` and no `INTERNET`, with a non-vacuity guard (manifest parses + declares ≥1 permission). Parsing (not grepping) keeps an explanatory comment from false-tripping it. Complements the pre-existing `ManifestPermissionsTest` exact-allow-list (which also stays green).
- **`GoldenFixture` + `GoldenFixtureGenerator`**: a `KNOWN_PASSPHRASE` constant + a fixed `knownEnvelope()` (DayEntry/Symptom/Bleeding/Medication DTOs incl. a null-severity health row). The `@Ignore`d generator was un-ignored and run ONCE to capture `endo-export-golden-v1.enc` (359 bytes), which was committed; the generator is re-`@Ignore`d so CI never regenerates (and thus never overwrites) it.
- **`GoldenFixtureTest`**: loads the committed `.enc` from the test-resource classpath and asserts `decode` → `Success` with `envelope == knownEnvelope()` — decode-ONLY, so any future v1-format drift turns it red.
- **`SafDocumentIoTest`** (androidTest): writes a 4096-byte array then a 64-byte array to the same `Uri` via `SafDocumentIo`, asserts the read-back is exactly the 64 short bytes (no stale tail). Compiles JVM-side; the emulator run is device/CI-gated.

## REAL Test Results

`cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :insights:test` — **BUILD SUCCESSFUL**. Whole `:app` unit lane: **356 tests, 0 failures, 0 errors, 1 skipped** (the `@Ignore`d generator). Per-class counts from the JUnit result XML:

| Test class | Tests | Skipped | Failures | Proves |
|------------|-------|---------|----------|--------|
| ReadBackVerifyTest | 2 | 0 | 0 | healthy write → verifyWrite true; truncated write → verifyWrite false (false-success blocked) |
| ExportManifestTest | 1 | 0 | 0 | no storage permission + no INTERNET (DOM-parsed, non-vacuity guarded) |
| GoldenFixtureTest | 1 | 0 | 0 | committed `.enc` decodes with the known passphrase to the known envelope (format pinned) |
| GoldenFixtureGenerator | 1 | 1 | 0 | `@Ignore`d generate-once producer — skipped in CI (never regenerates the committed artifact) |
| ManifestPermissionsTest (pre-existing) | 5 | 0 | 0 | exact allow-list still green (no storage perm slipped into the allow-list) |
| Prior 23-01/23-02 export suite (Codec/Crypto/ErrorTaxonomy/Format/Mapper/Purity/Idempotency/RoundTrip/TwoExportsDiffer) | 39 | 0 | 0 | still green |

- **`:app:testDebugUnitTest`** — BUILD SUCCESSFUL (356/0/0, 1 skipped).
- **`:app:assembleDebug`** — BUILD SUCCESSFUL (APK builds with the seam + Room).
- **`:app:compileDebugAndroidTestKotlin`** — BUILD SUCCESSFUL (SafDocumentIoTest compiles in the androidTest lane).
- **`:insights:test`** — BUILD SUCCESSFUL (prior module green).
- Grep confirms `openOutputStream(uri, "wt")` in `SafDocumentIo.kt`; `git ls-files` confirms `endo-export-golden-v1.enc` (Bin 359 bytes) is committed.
- Catalog: `android/gradle/libs.versions.toml` unchanged — no new coordinate.

## Threat Mitigations Applied

- **T-23-07** (Tampering — SAF stale-tail / read-back): `openOutputStream(uri, "wt")` truncate (no stale tail) + `verifyWrite` (decode the just-written bytes) before any success; `ReadBackVerifyTest` pins the truncated-write → false path; `SafDocumentIoTest` pins short-over-long on a real provider (device-gated).
- **T-23-08** (Information disclosure — exported file format): the ciphertext body (GZIP-then-AES-GCM from 23-01) carries no data-type names; the golden fixture confirms the on-disk shape is a fixed opaque blob, not greppable health labels.
- **T-23-08b** (Information disclosure — golden artifact): accepted — the committed `.enc` encrypts only synthetic KNOWN-envelope test data under a PUBLIC test passphrase constant; no real user data is committed.
- **T-23-SC** (Supply chain): accepted — no new package; SAF is `ContentResolver`/`Uri` (platform), golden I/O is JVM resources; catalog audit stays green.

## Deviations from Plan

**None — plan executed exactly as written.** Both `type="auto"` tasks landed all named files, behaviors, and acceptance criteria. The golden fixture was produced by the documented un-ignore → run-once → re-`@Ignore` capture flow. One clarification worth noting (not a deviation): the plan describes an `ExportManifestTest` and the repo already had a `ManifestPermissionsTest` with an exact-allow-list; I added the dedicated export-scoped `ExportManifestTest` (explicit storage deny-list + non-vacuity) as planned and left the pre-existing test intact — both are green. The pre-existing `.planning/STATE.md` working-tree change was left untouched (out of scope — this plan does not update STATE.md/ROADMAP.md).

## Notes for Downstream Plans

- **23-04/05** wire the ViewModel over the seam: export = `EndoRepository.exportSnapshot()` → `ExportMapper.toEnvelope` → `ExportCodec.encode` → `SafDocumentIo.write(uri, bytes)` → **`verifyWrite(io, uri, passphrase)` gate** before showing success; import = `SafDocumentIo.read(uri)` → `ExportCodec.decode`.
- Inject `DocumentIo` at the composition root (real `SafDocumentIo(contentResolver)` in prod; `FakeDocumentIo` in JVM tests) — do NOT construct `SafDocumentIo` inside the ViewModel.
- The `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` picker launchers (23-05) supply the `Uri`; the neutral date-stamped filename is chosen there.
- `GoldenFixture.knownEnvelope()` / `KNOWN_PASSPHRASE` are the reusable format-pin references; if the v1 format is ever intentionally changed, bump to a `-v2` golden rather than regenerating `-v1`.

## Threat Flags

None — no new network endpoint, auth path, storage permission, or schema change introduced; the manifest is proven storage-permission-free and INTERNET-free.

## Known Stubs

None — `verifyWrite`, `SafDocumentIo`, and the golden fixture are fully wired; the seam awaits only the 23-04/05 ViewModel/UI call sites (planned, not stubbed here).

## Self-Check: PASSED

All 3 main-source + 6 test files + the committed `endo-export-golden-v1.enc` exist on disk and are git-tracked (`.enc` = Bin 359 bytes in commit 87fd033). Both task commits present in `git log`: `539fcfe` (feat — seam + verify + manifest guard), `87fd033` (test — golden fixture + drift guard + device-gated SAF truncate). Full `:app` unit suite (356/0/0, 1 skipped) + `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` + `:insights:test` all BUILD SUCCESSFUL; `"wt"` grep confirmed; version catalog unchanged.
