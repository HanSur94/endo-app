---
phase: 21
slug: health-connect-import
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + an injectable `FakeHealthReader` for JVM import/merge/idempotency/availability tests; Robolectric + Compose for the connect/import UI; in-memory Room for provenance round-trips; JUnit for manifest/copy scans; instrumented lane device-gated |
| **Config file** | `android/app/build.gradle.kts` (connect-client 1.1.0 added); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~3–5 min |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` no-regression; CopyAudit green over new copy
- **Max feedback latency:** ~300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-01-xx | 01 | 1 | AHC-02 | T-21-01 | connect-client added; manifest READ-only (4 READ + history) + BOTH rationale decls; zero WRITE | unit | `./gradlew :app:test* --tests *ManifestPermissions* *HealthManifest*` | ❌ W0 | ⬜ pending |
| 21-01-xx | 01 | 1 | AHC-01 | — | getSdkStatus three-state gating; manual logging never blocked | unit | `./gradlew :app:test* --tests *Availability*` | ❌ W0 | ⬜ pending |
| 21-02-xx | 02 | 2 | AHC-03 | — | HealthReader seam: 4 record types → ImportedSample (device-zone bucketing, DST/travel vectors) | unit | `./gradlew :app:test* --tests *HealthConnectReader* *Bucketing* *Mapping*` | ❌ W0 | ⬜ pending |
| 21-03-xx | 03 | 3 | AHC-03 | T-21-02 | import→provenance rows (hcRecordId unique) → manual-wins/nil-honest merge; idempotent re-import; HC deletions propagate | unit(room) | `./gradlew :app:test* --tests *Import* *Idempoten* *Deletion*` | ❌ W0 | ⬜ pending |
| 21-04-xx | 04 | 4 | AHC-01, AHC-04 | — | connect→rationale→request→import UI; honest window label; provenance badges; denial + unavailable states gentle | unit(compose) | `./gradlew :app:test* --tests *Connect* *ImportScreen*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `androidx.health.connect:connect-client` 1.1.0 in the catalog + `:app` (first-party androidx — catalog audit stays green)
- [ ] `HealthReader` interface + `FakeHealthReader` (records for all 4 types incl. midnight-spanning + DST + >30-day-old) so import/merge/availability/denial are JVM-testable with no emulator
- [ ] `deleteImportedByHcRecordId` DAO + a `HealthImportService` seam threaded through the consent-gated repository
- [ ] Test scaffolds: HealthManifestTest (READ-only + dual rationale), AvailabilityGateTest, HealthConnectReaderMappingTest (+ bucketing vectors), ImportServiceTest (idempotency + deletion propagation), ConnectFlowTest (red until sources land)
- [ ] Register connect/rationale/import/window/provenance copy in CopyAuditRegistry (dodging substrings: should/use/her/start/treat/stop/take)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real HC permission dialog + granted read of real provider data | AHC-02/03 | Needs a device with the HC provider + granted permissions | On an Android 14+ device with data in Health Connect, run the connect flow, grant, confirm import + provenance badges |
| Availability states on real API 26–27 / 28–33 / 34+ devices | AHC-01 | True `getSdkStatus` differs by device/OS | Run on representative devices; confirm the honest state each |
| Record-field integer constants (flow/sleep-stage/exercise-type) | AHC-03 | The 7 flagged assumptions need a real record to confirm | Confirm against a real provider record at impl/QA |

*Import/merge/idempotency/deletion/availability-logic/manifest/copy all have automated JVM verification via the FakeHealthReader; only the real provider dialog + real record constants are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (connect-client, HealthReader/Fake, DAO, test scaffolds, copy)
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
