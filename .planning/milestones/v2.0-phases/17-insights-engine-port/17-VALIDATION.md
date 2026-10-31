---
phase: 17
slug: insights-engine-port
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-08
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.1.1 (`:insights` pure-Kotlin-JVM module) |
| **Config file** | `android/insights/build.gradle.kts` (test deps + `useJUnitPlatform()`); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :insights:test` |
| **Full suite command** | `cd android && ./gradlew :insights:test :insights:check` |
| **Estimated runtime** | ~30–60 seconds (pure JVM, no emulator) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :insights:test`
- **After every plan wave:** Run `./gradlew :insights:test :insights:check`
- **Before `/gsd-verify-work`:** Full suite must be green AND `PARITY.md` case count == 157
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 17-01-xx | 01 | 1 | AENG-07 | — | version catalog carries no analytics/auth/backend/net coordinate | unit | `./gradlew :insights:test --tests *CatalogAuditTest` | ❌ W0 | ⬜ pending |
| 17-01-xx | 01 | 1 | AENG-04 | — | no `android.`/`androidx.` import in `:insights` | unit | `./gradlew :insights:test --tests *BoundaryLintTest` | ❌ W0 | ⬜ pending |
| 17-02-xx | 02 | 2 | AENG-01 | — | enum raw values match Swift verbatim | unit | `./gradlew :insights:test --tests *EnumParity*` | ❌ W0 | ⬜ pending |
| 17-02-xx | 02 | 2 | AENG-05 | — | drift red when Kotlin enum/ClaimsPolicy diverges from Swift source | unit | `./gradlew :insights:test --tests *DriftTest` | ❌ W0 | ⬜ pending |
| 17-03-xx | 03 | 3 | AENG-02 | — | six engines behind one HonestyGate producer | unit | `./gradlew :insights:test --tests *EngineTest` | ❌ W0 | ⬜ pending |
| 17-03-xx | 03 | 3 | AENG-06 | — | NaN/±0.0/rounding-tie/tied-rank/DST results match Swift golden | unit | `./gradlew :insights:test --tests *Divergence*` | ❌ W0 | ⬜ pending |
| 17-04-xx | 04 | 4 | AENG-03 | — | ported suite reaches 157-case parity, all green | unit | `./gradlew :insights:test` + `PARITY.md` ledger | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `android/settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper — the buildable workspace (no framework pre-exists; Wave 0 installs it)
- [ ] `android/insights/build.gradle.kts` with `kotlin("jvm")` + JUnit Jupiter 6.1.1 test wiring
- [ ] `CatalogAuditTest`, `BoundaryLintTest` scaffolds (structural guards, red until sources land)
- [ ] Golden-value fixtures generated from the Swift engine for the tolerance-based statistical tests (lgamma/erf-derived p-values) — captured as committed resources

*Statistical parity is tolerance-based (no Kotlin stdlib `lgamma`/`erf`); goldens must be generated from the shipped Swift engine, not hand-computed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CI merge-gate green on a real push | AENG-07 | Requires the GitHub Actions runner, not reproducible in a unit test | Push branch; confirm the path-filtered `android/**` `:insights` job runs and passes |

*All engine behaviors have automated verification; only the CI-runner green is host-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Gradle workspace, JUnit wiring, Swift goldens)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
