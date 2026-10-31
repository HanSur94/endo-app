---
phase: 23
slug: encrypted-export-import
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 pure-JVM (`:app` test/) for crypto/codec/mapper/round-trip via the `DocumentIo` fake — no emulator (javax.crypto runs on the JVM); in-memory Room for import idempotency; Robolectric+Compose for the Settings export/import UI; instrumented SAF picker device-gated |
| **Config file** | `android/app/build.gradle.kts` (no new dep — javax.crypto + java.util.zip are platform); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~3–5 min (600k PBKDF2 adds ~1s per crypto test) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` no-regression; CopyAudit green; catalog audit green (no new crypto dep)
- **Max feedback latency:** ~300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 23-01-xx | 01 | 1 | APRT-03 | T-23-01 | ExportCrypto (PBKDF2 600k + AES-GCM) + ExportCodec (magic/version/header/GZIP); fresh salt+IV per export; params read-from-file | unit | `./gradlew :app:test* --tests *ExportCrypto* *ExportCodec* *TwoExportsDiffer*` | ❌ W0 | ⬜ pending |
| 23-02-xx | 02 | 2 | APRT-01, APRT-02 | — | entity↔ExportEnvelope DTO map; consent-gated exportSnapshot; idempotent @Transaction importEnvelope by UUID | unit(room) | `./gradlew :app:test* --tests *ExportMapper* *ImportIdempoten* *RoundTrip*` | ❌ W0 | ⬜ pending |
| 23-03-xx | 03 | 3 | APRT-02, APRT-03 | T-23-02 | DocumentIo SAF seam; read-back verify before success; error taxonomy (wrong-pass vs corrupt vs version); committed golden fixture | unit | `./gradlew :app:test* --tests *DocumentIo* *ReadBack* *ErrorTaxonomy* *Golden*` | ❌ W0 | ⬜ pending |
| 23-04-xx | 04 | 4 | APRT-01, APRT-02 | — | Settings export/import UI: passphrase UX + cannot-recover warning; gentle error messages; foreground (no WorkManager/notification) | unit(compose) | `./gradlew :app:test* --tests *ExportImportScreen* *CopyAudit* *ExportSeparation*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `ExportCrypto` (javax.crypto PBKDF2 600k + AES/GCM/NoPadding) + `ExportCodec` (ENDO magic + versioned header + GZIP) — pure, no Android type
- [ ] `DocumentIo` interface + `SafDocumentIo` (real) + `FakeDocumentIo` (in-memory byte-array) so crypto+format+round-trip run pure-JVM
- [ ] `exportSnapshot()` (consent-gated read) + `@Transaction importEnvelope()` (idempotent upsert by UUID) on EndoRepository — additive, no schema change
- [ ] A committed golden fixture (captured bytes + known passphrase → known envelope) checked into test resources
- [ ] Test scaffolds: ExportCryptoTest, ExportCodecTest, TwoExportsDifferTest, ExportMapperTest, ImportIdempotencyTest, RoundTripTest, ReadBackVerifyTest, ErrorTaxonomyTest, GoldenFixtureTest, ExportImportScreenTest (red until sources land)
- [ ] Register export/import + passphrase + cannot-recover + 3 gentle error strings in CopyAuditRegistry (dodging substrings)

*Fresh-salt/nonce-per-export, read-back-before-success, idempotent-by-UUID, and the golden-format-pin are Wave-0-visible acceptances.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real SAF create/open document picker + cross-provider write | APRT-01/02 | The system picker + real Drive/Files providers are OS-mediated | On a device, export to Drive + a local file, re-import both; confirm success + no stale bytes ("wt") |
| 600k-PBKDF2 wall-clock on a low-end API-26 device | APRT-01 | KDF cost is device-CPU-bound | Time a real export on an old device; confirm the spinner covers the stall acceptably |

*Crypto correctness, format, round-trip, idempotency, error taxonomy, read-back, and the golden pin all have automated pure-JVM verification via FakeDocumentIo; only the real picker + KDF wall-clock are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (crypto/codec, DocumentIo seam, repo methods, golden fixture, test scaffolds, copy)
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
