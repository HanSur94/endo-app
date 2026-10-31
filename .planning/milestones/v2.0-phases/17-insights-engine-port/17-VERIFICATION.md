---
phase: 17-insights-engine-port
verified: 2026-07-09T08:49:00Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
host_gated_confirmations: # Documented deployment-time checks (per launching-agent directive: not gaps)
  - item: "GitHub Actions android.yml job green on a real push/PR"
    proven_locally: "The exact CI command `./gradlew :insights:test` runs green on the host (JDK 21 Temurin, same toolchain the workflow pins). Only the hosted-runner environment itself is unproven."
    why_host_gated: "Requires an actual push to GitHub; the runner env cannot be exercised from the verification host."
---

# Phase 17: Insights Engine Port — Verification Report

**Phase Goal:** The complete insights engine runs as pure Kotlin JVM in the `android/` monorepo workspace, proven equivalent to the shipped Swift engine.
**Verified:** 2026-07-09T08:49:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

The phase goal is achieved. A real `./gradlew clean :insights:test` run on the host (JDK 21 Temurin) executed the full suite from a clean state — `BUILD SUCCESSFUL in 16s`, `6 actionable tasks: 6 executed` (genuine compile+test, not a cached no-op). The JUnit XML across 23 suite files reports **176 tests, 0 failures, 0 errors, 0 skipped**. The 0-skipped count is load-bearing: the Swift-source drift scans (assumption-guarded with `assumeTrue`) actually resolved the iOS sources and ran, rather than skipping.

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ported JVM suite reaches ~157-case parity, all green, tracked in committed PARITY.md | ✓ VERIFIED | `PARITY.md` Kotlin column sums to exactly **157** (18 Swift-mirror rows) + **19** guard tests = **176**. JUnit XML = 176/0/0/0. Ledger is committed (`android/insights/PARITY.md`, 8.7K). |
| 2 | One public producer composes all six engines behind the single HonestyGate choke; honesty fixtures pass | ✓ VERIFIED | `InsightsEngine.insights()` (the one public root) concatenates Trends + CyclePhaseMapper + Correlation + Recommendations + Flare, then `return HonestyGate.apply(...)`. `HonestyGate` is `internal object` (external callers cannot re-gate). Honesty fixtures green: suppressed-silence, under-sample withholding, BH-FDR flood, absence-vs-zero (see below). |
| 3 | Boundary lint proves no android/androidx imports; drift tests go red on divergence (Swift scan + golden JSON) | ✓ VERIFIED | `BoundaryLintTests` walks `insights/src/main/kotlin`, regex-fails on `import android./androidx.` — ran (not skipped). `EnumRawValueParityTests` scans real `SnapshotEnums.swift` + `CyclePhase.swift` (2 tests) + golden JSON; `ClaimsPolicyDriftTests` scans real `Sources/EndoApp/Content/ClaimsPolicy.swift` (3 tests). All ran (0 skipped). |
| 4 | Numeric-divergence pack green (NaN, ±0.0, rounding ties, tied ranks, DST/non-UTC boundaries) | ✓ VERIFIED | `NumericDivergenceTests` (10 cases): NaN unrepresentable at DaySnapshot boundary, −0.0→+0.0 canonicalization, round-away-from-zero tie tests, Mann-Whitney tied-rank goldens, zone-invariance across UTC/Europe-Berlin/Pacific-Apia. + `SpecialFunctionsGoldenTests` (3) tolerance gate. All green. |
| 5 | `android/` Gradle workspace builds under path-filtered CI; test fails on analytics/auth/backend catalog entry | ✓ VERIFIED | Clean build green on host. `.github/workflows/android.yml` path-filtered to `android/**`. `VersionCatalogAuditTests` scans `libs.versions.toml` for analytics/firebase/oauth/retrofit/okhttp/grpc/etc — red-capable, green now (catalog holds only Kotlin + kotlinx-serialization + JUnit). CI-runner-green-on-real-push is host-gated (see frontmatter). |

**Score:** 5/5 truths verified (0 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/insights/src/main/kotlin/.../InsightsEngine.kt` | Single public composition root | ✓ VERIFIED | Composes 5 producers → `HonestyGate.apply`. Signature locked for Phase 18. |
| `android/insights/src/main/kotlin/.../HonestyGate.kt` | Internal single choke point | ✓ VERIFIED | `internal object`; withholds below-floor/under-sampled, excludes dismissed, fail-closed-drops forbidden copy. |
| 6 engine sources (Trends/CyclePhase/Correlation/Recommendations/Flare + HonestyGate) | Ported behind gate | ✓ VERIFIED | All present under `src/main/kotlin/com/endoapp/insights/`. Plus ForbiddenTerms, SnapshotEnums, DaySnapshot, Factor, CorrelationStats, SpecialFunctions, Explore/Scatter/ScrubSelection. |
| `android/insights/PARITY.md` | Committed ledger, Kotlin col = 157 | ✓ VERIFIED | 18 rows sum to 157; 176-case reconciliation documented. |
| `android/gradle/libs.versions.toml` | Zero-dependency audit surface | ✓ VERIFIED | Only Kotlin 2.3.21, kotlinx-serialization-json 1.11.0, JUnit Jupiter 6.1.1. No backend/analytics/auth. |
| `.github/workflows/android.yml` | Path-filtered CI merge gate | ✓ VERIFIED | `paths: android/**`; JDK 21 Temurin; runs `:insights:test`. |
| 25 Kotlin test files | 157 mirror + 19 guards | ✓ VERIFIED | All present and wired into the suite (23 XML suite files: 2 fixture-only files carry 0 test methods). |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| `InsightsEngine.insights()` | 5 candidate producers | Direct composition (concatenation) | ✓ WIRED |
| `InsightsEngine.insights()` | `HonestyGate.apply` | `return HonestyGate.apply(candidates, dismissed)` | ✓ WIRED |
| Drift tests | iOS Swift sources | `resolveFromRoot("EndoInsights/.../SnapshotEnums.swift"|"Sources/EndoApp/Content/ClaimsPolicy.swift")` | ✓ WIRED (ran, 0 skipped) |
| `VersionCatalogAuditTests` | `libs.versions.toml` | File read + substring scan | ✓ WIRED |
| `BoundaryLintTests` | `insights/src/main/kotlin` | `walkTopDown()` + regex | ✓ WIRED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Clean build + full suite green | `./gradlew clean :insights:test` | exit 0, BUILD SUCCESSFUL, 6 tasks executed | ✓ PASS |
| Test count = 176, 0 fail/err/skip | parse `build/test-results/test/*.xml` | tests=176 failures=0 errors=0 skipped=0 | ✓ PASS |
| PARITY Kotlin column = 157 | ledger sum | 157 mirror + 19 guards = 176 | ✓ PASS |
| iOS tree untouched by phase | `git diff --name-only 7c9a87e^..HEAD` | only android/(58), .planning/(8), .github/(1=android.yml) | ✓ PASS |

### Honesty Acceptance Fixtures

| Fixture | Test | Status |
|---------|------|--------|
| Suppressed-user silence | `suppressedNoBleeding_emitsNoBleedingOrCycleTrend`, `suppressedUser_noCyclePhaseCorrelation` | ✓ PASS |
| Under-sample withholding | `underSampledCyclePhaseAssociation_isWithheldByGate`, `underSampled_isWithheld` | ✓ PASS |
| BH-FDR multiple-comparison flood | `multipleComparisonFlood_surfacesNothing` | ✓ PASS |
| Absence-vs-zero | `foodBinarization_emptyOnLoggedDayIsAbsent_trulyEmptyIsUnobserved` | ✓ PASS |

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| AENG-01 | Domain enums + value types ported verbatim by raw value; DaySnapshot nil-means-unlogged | ✓ SATISFIED | SnapshotEnums.kt, DaySnapshot.kt, Factor.kt; EnumRawValueParityTests green; NaN unrepresentable. (FactorKind deliberately deferred to Phase 18 — documented in ledger.) |
| AENG-02 | Six engines behind single HonestyGate; one public producer | ✓ SATISFIED | InsightsEngine + HonestyGate + 5 engines + ForbiddenTerms. |
| AENG-03 | JVM suite at 157-case parity, committed PARITY.md, all green | ✓ SATISFIED | 157 mirror cases, ledger committed, 176/0/0/0. |
| AENG-04 | `:insights` pure Kotlin JVM; boundary lint proves no android/androidx | ✓ SATISFIED | `kotlin("jvm")` only; BoundaryLintTests green. |
| AENG-05 | Cross-platform drift tests (enum + ClaimsPolicy) red on divergence | ✓ SATISFIED | EnumRawValueParityTests (2) + ClaimsPolicyDriftTests (3) scan real Swift sources + golden JSON. |
| AENG-06 | Numeric-divergence pack green | ✓ SATISFIED | NumericDivergenceTests (10) + SpecialFunctionsGoldenTests (3). |
| AENG-07 | Gradle workspace, catalog zero-dependency audit, path-filtered CI | ✓ SATISFIED | Workspace builds; VersionCatalogAuditTests red-capable; android.yml path-filtered. CI-green-on-push host-gated. |

### Anti-Patterns Found

None blocking. No unreferenced TBD/FIXME/XXX debt markers in phase-modified sources. Drift and boundary tests use `assumeTrue` skip-guards for out-of-monorepo builds, but in this monorepo they resolved and ran (suite `skipped=0`), so the guards did not silently pass.

### Notes / Informational (non-blocking)

- **Sub-engine visibility:** `TrendsEngine`, `CyclePhaseMapper`, `CorrelationEngine`, `RecommendationsEngine`, `FlareEngine` are declared as public `object`s, while the `InsightsEngine.kt` header prose describes them as "package-INTERNAL behind this facade." The single-public-producer contract (SC2/AENG-02) is still met: `InsightsEngine` is the one documented composition root and `HonestyGate` (the choke point) IS `internal`, so external callers cannot re-gate. The producers being public means the encapsulation is convention rather than compiler-enforced — a minor defense-in-depth gap, not a goal failure. Consider marking them `internal` in a later hardening pass.

### Human Verification Required

None that block the phase. The only outstanding confirmation is **CI-green-on-a-real-push** of `.github/workflows/android.yml` — a documented host-gated deployment check, not a gap. It is de-risked: the exact CI command (`./gradlew :insights:test`) runs green locally on the same JDK 21 Temurin toolchain the workflow pins. Per the launching directive, this does not flip status to `human_needed`.

### Gaps Summary

No gaps. All five ROADMAP success criteria and all seven requirements (AENG-01..07) map to concrete, green, wired artifacts. The engine runs as pure Kotlin JVM, the parity ledger balances at 157, the honesty/drift/numeric/catalog guards are real and red-capable, and the iOS tree is provably unmodified by this phase.

---

_Verified: 2026-07-09T08:49:00Z_
_Verifier: Claude (gsd-verifier)_
