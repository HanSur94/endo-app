---
phase: 24
slug: pdf-report
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-10
---

# Phase 24 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 pure-JVM (`:app` test/) for the pure ReportModel + the RecordingReportCanvas draw-instruction spy (proves drawText-not-drawBitmap + exact copy — no emulator); Robolectric for FileProvider/share + composition; in-memory Room for the range→daySnapshots read; instrumented for real %PDF bytes + real share chooser (device-gated) |
| **Config file** | `android/app/build.gradle.kts` (no new dep — PdfDocument + FileProvider(androidx.core) are platform/first-party); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~4–6 min |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` no-regression; CopyAudit green; catalog audit green (no new dep)
- **Max feedback latency:** ~360 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 24-01-xx | 01 | 1 | AREL-01 | — | pure ReportModel: absence≠0, per-section "not enough data", per-source labels; range→window (preset+custom invalid-range guard); disclaimer must-contain | unit(room) | `./gradlew :app:test* --tests *ReportModel* *ReportRange* *daySnapshotSources*` | ❌ W0 | ⬜ pending |
| 24-02-xx | 02 | 2 | AREL-01 | — | ReportCanvas draw-seam + ReportPdfRenderer: drawText (vector, not drawBitmap for text) + exact copy + page-count/"Page n of m"; chart via ChartPrimitives bridge single-ink honest breaks | unit | `./gradlew :app:test* --tests *ReportRender* *ReportCanvas* *ReportChart*` | ❌ W0 | ⬜ pending |
| 24-03-xx | 03 | 3 | AREL-02 | T-24-01 | FileProvider + ACTION_SEND share (user picks target, no silent upload); cache cleaned; no-network source scan; no INTERNET | unit(robolectric) | `./gradlew :app:test* --tests *ReportShare* *ReportNoNetwork* *ReportManifest*` | ❌ W0 | ⬜ pending |
| 24-04-xx | 04 | 4 | AREL-01, AREL-02 | — | Settings "Export report" UI: range picker + preview/confirm + share; report copy registered + audited; consent-gated | unit(compose) | `./gradlew :app:test* --tests *ReportScreen* *CopyAudit*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] Consent-gated `daySnapshotSources(range)` read on EndoRepository (the flagged A3 provenance gap — per-day ValueSource map, store-derived) — additive, no schema change
- [ ] `ReportCanvas` interface + `RecordingReportCanvas` spy (JVM draw-instruction capture) so vector-text + exact-copy is provable without an emulator
- [ ] `ChartPrimitives` PDF bridge (CanvasDrawScope over the PDF page canvas, or a raw-Canvas geometry adapter) drawing the SAME Phase-22 honest break geometry in single-ink black
- [ ] FileProvider `<provider exported=false>` + `res/xml/file_paths.xml` (`<cache-path name="reports">`)
- [ ] Test scaffolds: ReportModelTest, ReportRangeTest, ReportRenderTest (drawText-not-bitmap + copy + page-count), ReportChartHonestyTest, ReportShareTest, ReportNoNetworkTest, ReportManifestTest, ReportScreenTest (red until sources land)
- [ ] Register ReportCopy (title, section headers, disclaimer band, source labels, range labels, share/preview chrome, invalid-range message) in CopyAuditRegistry (substring-safe); disclaimer must-contain self-tracked-not-diagnostic

*Vector-selectable-text (drawText not drawBitmap), absence≠0 + per-source labels, disclaimer-present, and no-network/no-silent-upload are Wave-0-visible acceptances.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Clinician-credibility: real PDF visual layout + selectable text on print/zoom | AREL-01 | Visual/print quality is experiential; real %PDF bytes need a device | Generate on a device, open in a PDF viewer, select text, print/zoom; confirm crisp vector + selectable |
| Real share chooser + no silent upload | AREL-02 | The system chooser + target apps are OS-mediated | Share on a device; confirm the chooser appears, no auto-send, cache cleaned after |

*The pure model, draw-instruction (vector-text/copy/page-count), per-source labels, disclaimer, no-network scan, and share-intent construction all have automated JVM/Robolectric verification; only real %PDF visual credibility + the real chooser are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (daySnapshotSources, ReportCanvas spy, chart bridge, FileProvider, test scaffolds, copy)
- [ ] No watch-mode flags
- [ ] Feedback latency < 360s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
