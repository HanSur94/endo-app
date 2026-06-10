---
phase: 8
slug: insights-integration-coordinator-cached-insights-dashboard
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-09
---

# Phase 8 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Insights Integration — Coordinator, Cached Insights & Dashboard.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app → SwiftData store → CloudKit private DB | `CachedInsight` rows persist locally and sync to the user's PRIVATE iCloud DB (Phase 4). | Serialized gated `Insight` blob (category-level, non-causal) |
| consent gate → Repository | No health-derived read/write before explicit consent (ONBD-01). | Health-derived reads/writes |
| main actor → background `Task.detached` | `Sendable` value snapshots cross into the off-main pure engine; results cross back. | `DaySnapshot` / `[Insight]` value types |
| coordinator → Repository | All store reads/writes go through consent-gated Repository seams (08-01). | Cached insights, dismissed keys |
| launch args → in-memory store | `-uitest-seed-insights` injects a fixture; must never touch a real/CloudKit store. | Synthetic test fixture |
| engine output → SwiftUI views | Card/chart render the gate's `[Insight]` verbatim — no causal/numeric/gendered re-introduction. | Gated insight copy |
| user dismiss → store → CloudKit | A dismiss writes a `DismissedInsight` that syncs to the private DB and keeps the card gone. | Dismissal preference |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-08-01 | Information disclosure | `CachedInsight.payload` blob | mitigate | Blob encodes ONLY the gated `Insight` (`Repository.swift:603`), never a `DaySnapshot`/note prose; flat columns are non-causal `signalKey`/`kindRaw`/`windowRaw` (`CachedInsight.swift:40-47`); round-trip test asserts stored value is an `Insight` (`CachedInsightRoundTripTests.swift:56-60`). | closed |
| T-08-02 | Elevation of privilege / access control | `loadCachedInsights` / `replaceCachedInsights` | mitigate | Both call `assertConsented()` first (`Repository.swift:587,596`; def `:852`). | closed |
| T-08-03 | Tampering / data integrity | V4→V5 migration under CloudKit | mitigate | `v4toV5` is `.lightweight` only, never `.custom` (`MigrationPlan.swift:49-52`); `SchemaV5` additive — one new model (`SchemaV5.swift:33`). | closed |
| T-08-04 | Denial of service (crash) | corrupt/`nil` `payload` blob | accept→mitigate | `loadCachedInsights` `compactMap`-skips an undecodable blob (`Repository.swift:592`); `corruptPayload_isSkipped_notFatal` proves no crash (`CachedInsightRoundTripTests.swift:86-107`). | closed |
| T-08-05 | Information disclosure | coordinator off-main compute / logging | mitigate | Zero `print`/`os_log`/`Logger`/`NSLog`/`debugPrint`/`dump` in Insights feature + coordinator (grep clean); only `Sendable` values cross to `Task.detached` (`InsightsCoordinator.swift:141-143`). | closed |
| T-08-06 | Tampering / data integrity | `-uitest-seed-insights` fixture | mitigate | Seed gated `if seedInsights && uiTestReset` (`AppDependencies.swift:127`); store `inMemory: uiTestReset` (`:68`); writes through in-memory repo only (`:152-159`). | closed |
| T-08-07 | Repudiation | stale cache surfaces a dismissed insight | mitigate | Fingerprint includes `dismissed.sorted().joined` (`InsightFingerprint.swift:47`); dismiss → recompute re-reads `loadDismissed()` + feeds engine (`InsightsCoordinator.swift:117-142`); `replaceCachedInsights` deletes stale rows (`Repository.swift:599`). | closed |
| T-08-08 | Access control | coordinator reads before consent | mitigate | Coordinator reaches store only via injected seams over the consent-asserting `SwiftDataRepository` (`AppDependencies.swift:123-145`); dashboard built only in `.consented` (T-08-12). | closed |
| T-08-09 | Information disclosure / honesty | `InsightsCopy` chrome strings | mitigate | `auditableStrings` scanned for forbidden verbs, gendered tokens (incl. `"her"`), numeric confidence (`CopyAuditTests.swift:150-211`; `ClaimsPolicy.swift:36-45`); card renders engine `headline`/`detail` verbatim (`InsightCardView.swift:61,70`). | closed |
| T-08-10 | Information disclosure (honesty) | chart "absence" rendering | mitigate | `ChartSeriesGrouping` splits at gaps, never inserts `value:0` (`ChartSeriesGrouping.swift:57-62`); pure helper proven by `ChartSeriesGroupingTests` without rendering. | closed |
| T-08-11 | Tampering (a11y integrity) | color-only signaling | mitigate | Confidence = text + SF Symbol `circle`/`circle.lefthalf.filled`/`circle.fill` (`InsightCardView.swift:88-111`); chart = per-mark `.accessibilityValue` + axis labels (`InsightTrendChart.swift:93-120`); never hue alone. | closed |
| T-08-12 | Elevation of privilege / access control | `InsightsView` / `tab.insights` entry | mitigate | `ConsentedShell` `TabView` built ONLY in AppGate `.consented` (`AppGate.swift:33-64`); `.notConsented` renders only `OnboardingView()` (`:32`). | closed |
| T-08-13 | Information disclosure (honesty) | suppressed → phantom cycle phase | mitigate | `Section` rendered only `if !kindInsights.isEmpty` (`InsightsView.swift:108-120`); E2E asserts `insights.section.cyclePhase` non-existence (`InsightsDashboardUITests.swift:133-136`). | closed |
| T-08-14 | Repudiation | a dismissed card reappears | mitigate | Dismiss persists `DismissedInsight` + optimistic removal + recompute exclusion (`InsightsCoordinator.swift:117-122`, `Repository.swift:569-575`); save-seam trigger (`DayLogEditorView.swift:582`); E2E asserts stays-gone (`InsightsDashboardUITests.swift:142-196`). | closed |
| T-08-15 | Information disclosure | screenshots / logs containing health data | mitigate | E2E screenshots from in-memory seed gated behind `-uitest-reset` (`InsightsDashboardUITests.swift:93,128,143`); no health-prose logging anywhere (grep clean). | closed |
| T-08-SC | Tampering (supply chain) | SPM/npm/pip installs | accept | Zero new third-party packages — only local path-based `EndoInsights` (`project.yml:23-25`); `EndoInsights/Package.swift:21-25` declares zero deps. Swift Charts / Codable / SwiftData are first-party Apple frameworks. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-08-01 | T-08-04 | Corrupt/undecodable cache blob is non-fatal by design: `compactMap` skips it and the entry degrades to a recompute (never a crash). Residual risk = a single stale insight silently dropped until next recompute — acceptable for derived, rebuildable data. Backed by code + test evidence. | sannahrush@googlemail.com | 2026-06-09 |
| AR-08-02 | T-08-SC | No new third-party dependencies this phase; all frameworks are first-party Apple (Swift Charts, Codable, SwiftData). No package-legitimacy gate required. Verified against `project.yml` + `EndoInsights/Package.swift`. | sannahrush@googlemail.com | 2026-06-09 |

*Accepted risks do not resurface in future audit runs. Both are also code-evidenced as mitigated (accept→mitigate), so they close without residual implementation work.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-09 | 16 | 16 | 0 | gsd-security-auditor (verify-mitigations mode; register authored at plan time) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-09

---

## Notes (informational — not threat gaps)

These are functional/release items carried by the phase verification gate, **not** open threat mitigations:
- `CachedInsight` CloudKit record-type **Development → Production** deploy (release-time).
- On-device **cross-device cache sync** validation (needs two iCloud devices).
- D-16 human-verify of Swift Charts **visual quality** — reviewed live on the iOS Simulator during this session's UAT (`08-UAT.md` Test 5, pass); gate item closed.
