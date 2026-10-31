---
phase: 22
slug: insights-explore-charts
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Robolectric + Compose UI test (JVM) for dashboard/Explore/semantics; JUnit for data-prep-render honesty + structural scans (no-alarm-visual, notification separation); in-memory Room for coordinator cache/dismiss; instrumented Compose device-gated for haptics/pixels |
| **Config file** | `android/app/build.gradle.kts`; `android/gradle/libs.versions.toml` (no new runtime dep unless the Vico escape hatch is triggered — default Canvas needs none) |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~4–6 min |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` no-regression; CopyAudit green; separation scans green
- **Max feedback latency:** ~360 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 22-01-xx | 01 | 1 | AINS-01 | — | InsightsCoordinator: off-main recompute, fingerprint cache load, dismissed excluded | unit(room) | `./gradlew :app:test* --tests *InsightsCoordinator*` | ❌ W0 | ⬜ pending |
| 22-02-xx | 02 | 2 | AINS-06, AINS-03 | — | trend Canvas honesty: gap=break (never 0/interpolate), logged-0=real 0, no dual-y; @Immutable inputs; draw-phase scrub | unit(compose) | `./gradlew :app:test* --tests *TrendChart* *ChartHonesty*` | ❌ W0 | ⬜ pending |
| 22-03-xx | 03 | 3 | AINS-07, AINS-03 | — | per-data-point TalkBack overlay from the gap-honest resolver; gap="not logged"; ≥48dp; focus order | unit(compose) | `./gradlew :app:test* --tests *ChartSemantics* *Scrub*` | ❌ W0 | ⬜ pending |
| 22-04-xx | 04 | 4 | AINS-01, AINS-02 | T-22-01 | dashboard: 5 sections (flare FIRST), plain-language confidence (never numeric), 3 states, pinned disclaimer, non-destructive dismiss; flare no-alarm-visual; insights pkg notification-free | unit(compose) | `./gradlew :app:test* --tests *Insights* *Flare* *Confidence* *InsightsSeparation*` | ❌ W0 | ⬜ pending |
| 22-05-xx | 05 | 5 | AINS-04, AINS-05 | — | Explore overlay lanes (per-scale, no shared/dual-y) + event lanes + count-encoded scatter (no fitted line/r-value) via deep-link; suppression hides bleeding | unit(compose) | `./gradlew :app:test* --tests *Explore* *Lane* *Scatter*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `InsightsCoordinator` (app-scoped state holder) over `SnapshotProjector`→`InsightsEngine`, `withContext(Dispatchers.Default)`, `CachedInsight` fingerprint cache + `DismissedInsight` exclusion (all Phase-17/18 seams exist) + a StateFlow out; test seed
- [ ] The Canvas chart primitives (trend/lane/event/scatter) reading the Phase-17 data-prep types with `@Immutable` inputs + a draw-phase scrub reader
- [ ] The per-data-point semantics overlay contract + a semantics-tree test harness
- [ ] Test scaffolds: InsightsCoordinatorTest, TrendChartHonestyTest, ChartSemanticsTest, InsightsDashboardTest, FlareNoAlarmVisualTest, InsightsSeparationTest, ExploreTest, ScatterTest (red until sources land)
- [ ] Register the ~45 new app-chrome strings in CopyAuditRegistry (dodging the flagged traps: higher→"trending", another→"new one", where/together/either); insight copy itself is engine-audited in :insights

*Chart-honesty (gaps≠0, no dual-y, no fitted-line, confidence-never-numeric), the a11y overlay, and the flare no-alarm-visual + notification-free invariants are Wave-0-visible acceptances.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Chart legibility + colorblind-safe marks + scrub feel/haptic on a device | AINS-03/04 | Pixel/gesture/haptic are experiential | Scrub trends + lanes on a device; confirm haptic on day change, legible marks, colorblind-safe distinction |
| TalkBack sweep of every chart data point + dashboard cards | AINS-07 | Screen-reader UX is experiential | Enable TalkBack; sweep trend/lane/scatter points + cards; confirm "not logged" on gaps, real-0 spoken, calm flare |
| Recompute smoothness (no jank) on a real dataset | AINS-01 | Perf feel is device-measured (Layout Inspector gates any Vico switch) | Load a large logged history; confirm instant cached load + non-blocking recompute; profile if janky |

*Data-prep + render honesty, per-point semantics presence, confidence-never-numeric, flare no-alarm-visual, dismiss non-destructiveness, suppression, and notification-separation all have automated JVM verification; only pixel/gesture/haptic/perf-feel are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (coordinator, Canvas primitives, semantics harness, test scaffolds, copy)
- [ ] No watch-mode flags
- [ ] Feedback latency < 360s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
