---
phase: 12-flare-early-warning
verified: 2026-06-10T03:05:00Z
status: human_needed
score: 3/3 must-haves verified
overrides_applied: 0
human_verification:
  - test: "On an account with ≥21 days incl. a recent rising symptom pattern + a logged trigger, open Insights → Flare early-warning."
    expected: "The flare card reads calmly and uncertainty-first (\"your recent pattern looks similar to times before more difficult days — something to keep an eye on / discuss with your clinician\"), shows a plain-language confidence (never a number), carries pervasive \"not medical advice\" framing, has NO alarm visual (no red/orange/exclamation/bell/flame), and an insufficient account shows NO warning."
    why_human: "Calm / non-alarmist / uncertainty-first TONE is partly human judgment beyond the automated alarmist substring scan; needs a real populated longitudinal account. The flare E2E already ran GREEN on the simulator (screenshot /tmp/e2e/insights-12-flare.png, 367KB, 2026-06-10); this is the end-of-phase tone confirmation on that screenshot — the only deferred item per 12-VALIDATION.md."
---

# Phase 12: Flare Early-Warning Verification Report

**Phase Goal:** A heuristic, probabilistic, non-diagnostic flare early-warning derived from the user's personal history, fully gated by the honesty layer, withheld when confidence is low, presented uncertainty-first, delivered in-app ONLY (never a speculative push notification).
**Verified:** 2026-06-10T03:05:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria + PLAN must-haves)

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| SC1 | Heuristic, non-diagnostic flare warning from the user's OWN history, with confidence, uncertainty-first, composed behind the single HonestyGate | ✓ VERIFIED | `FlareEngine.flareWarning` (FlareEngine.swift:50) requires a rising-severity spine (line 64 — no rise → `[]`), `+1` notch per active trigger / near-higher cycle phase (boundedConfidence:264-270), CAP `.medium` never `.high` (line 268), words-only `points:[]` (line 299), composed (not re-derived) into LOCKED `InsightsEngine.insights` (InsightsEngine.swift:49) behind the single `HonestyGate.apply` (line 50). `minSample(.flare)==21` (HonestyGate.swift:41) gated against `rising.loggedDays` (FlareEngine.swift:72-73). `FlareEngineTests` (5/5) + `InsightsEngineTests.composition_surfacesFlare_overRisingFixture` GREEN via `swift test`. |
| SC2 | Withheld when insufficient (zero warning; cusp-only "not enough data"; no "all clear"); never a push | ✓ VERIFIED | Suppressed short-circuit (FlareEngine.swift:58 → `[]`); flat/no-rise → `[]` (line 64-66); under-evidence → at most one `isInsufficiencyNotice` (line 73-74). Tests `suppressedUser_yieldsZeroFlareWarning`, `flatNoRiseUser_yieldsZeroFlareWarning`, `underEvidenceRise_yieldsNoNonNoticeWarning`, `gate_withholdsFlare_forSuppressedAndFlat` all GREEN. `FlareNoNotificationTests` source-scans FlareEngine.swift + FlareCopy.swift for UNUserNotification/UNNotificationRequest/NotificationScheduler/scheduleReminder — manual scan confirms NONE; test is non-vacuous (asserts file set non-empty). |
| SC3 | Same gate/confidence/dismiss as other insights; calm `.flare` section first, omit-when-empty, reuses InsightCardView UNCHANGED, NO alarm visual; stable signalKey; pervasive not-medical-advice | ✓ VERIFIED | `.flare` section placed FIRST after pinned DisclaimerBanner (InsightsView.swift:67-70), uses the same `section(...)` omit-when-empty helper (line 128-140), reuses `InsightCardView` unchanged — `.flare` shares the calm words-only branch with cyclePhase/correlation/recommendation (InsightCardView.swift:128). Dismiss rides `coordinator.dismiss(signalKey:)` (InsightsView.swift:177). Stable `signalKey "flare.warning"` + `.allTime` window (FlareEngine.swift:295) survives recompute. NO alarm visual — `FlareNoAlarmVisualTests` source-scans InsightsView + InsightCardView for `.red/.orange/exclamationmark/bell/flame/.badge` (manual scan: NONE; test anchored on `insights.section.flare`, non-vacuous). |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| --- | --- | --- | --- |
| `EndoInsights/Sources/EndoInsights/FlareEngine.swift` | Foundation-only flare producer + FlareCopyText mirror + FlareDriftGuard | ✓ VERIFIED | `enum FlareEngine` w/ `static func flareWarning`; `FlareCopyText` byte-identical mirror; `FlareDriftGuard` public shim. Compiles (`swift build` ok). Imports Foundation only. |
| `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` | + FlareEngine.flareWarning before the gate, LOCKED signature | ✓ VERIFIED | Line 49 concatenates `FlareEngine.flareWarning(...)`, line 50 routes all candidates through `HonestyGate.apply`; signature unchanged (D-16). |
| `EndoInsights/Sources/EndoInsights/HonestyGate.swift` | minSample(.flare)==21 + isInsufficiencyNotice carve-out | ✓ VERIFIED | Line 41 `.flare → 21`; carve-out at lines 90/95/100. |
| `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` | alarmist list wired into violation(in:) | ✓ VERIFIED | `static let alarmist` (line 113); `violation(in:)` chains `alarmist.first` (line 127). "warning" deliberately excluded for header. |
| `Sources/EndoApp/Content/ClaimsPolicy.swift` | forbiddenAlarmist app mirror | ✓ VERIFIED | Lines 93-97 byte-set-identical to package alarmist. |
| `Sources/EndoApp/Features/Insights/FlareCopy.swift` | app-side audited copy surface (auditableStrings) | ✓ VERIFIED | `auditableStrings` (line 66) scanned by CopyAuditTests against full union; 6/6 strings byte-identical to package mirror. |
| `Sources/EndoApp/Features/Insights/InsightsView.swift` | calm .flare section first, identifier insights.section.flare | ✓ VERIFIED | Lines 67-70. |
| `EndoInsights/Sources/EndoInsights/Insight.swift` | kind .flare, isInsufficiencyNotice, stable signalKey id derivation | ✓ VERIFIED | `.flare` case (line 26), `isInsufficiencyNotice` (line 89), `DeterministicUUID` (signalKey-derived id). |

### Key Link Verification

| From | To | Via | Status | Details |
| --- | --- | --- | --- | --- |
| InsightsEngine.insights | FlareEngine.flareWarning | concatenated before HonestyGate.apply | ✓ WIRED | InsightsEngine.swift:49→50 |
| FlareEngine.flareWarning | TrendsEngine/CorrelationEngine/CyclePhaseMapper | compose gated signals, never re-derive | ✓ WIRED | TrendsEngine.bucket/direction (114-141), CorrelationEngine.scoredCandidates (158), CyclePhaseMapper.phaseWindows (254) |
| FlareEngine.makeFlareInsight | Insight.derived | kind:.flare, window:.allTime, signalKey:flare.warning, sampleSize:rising.loggedDays | ✓ WIRED | FlareEngine.swift:292-301 |
| FlareDriftGuard | FlareCopyText / ForbiddenTerms.alarmist | public test-support shim | ✓ WIRED | FlareEngine.swift:379-404; FlareCopyDriftTests asserts byte/set identity |
| ForbiddenTerms.violation | ForbiddenTerms.alarmist | third .first chain | ✓ WIRED | ForbiddenTerms.swift:127 |
| InsightsView.body | section(.flare) | first section after disclaimer, before Trends | ✓ WIRED | InsightsView.swift:67-71 |
| testFlare_rendersFirst E2E | insights.section.flare | seeded rising history renders flare before trends | ✓ WIRED | InsightsDashboardUITests.swift:240-267; seed `seedFlareWarning` (AppDependencies.swift:343) inserts genuine 30-day rising spine |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| --- | --- | --- | --- | --- |
| InsightsView `.flare` section | `coordinator.insights.filter{.flare}` | InsightsCoordinator → InsightsEngine.insights → FlareEngine.flareWarning over real DaySnapshots | ✓ Yes — composed from TrendsEngine/CorrelationEngine/CyclePhaseMapper over the user's logged history; E2E seed proves real rising data flows end-to-end and renders a card | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| --- | --- | --- | --- |
| Package compiles | `swift build` (EndoInsights) | "ok (build complete)" | ✓ PASS |
| Rising→one bounded ≤.medium; suppressed/flat/under-evidence→zero | `swift test --filter FlareEngineTests` | 5 tests, 0 failures | ✓ PASS |
| Flare composed behind gate; suppressed/flat withheld; Foundation-only boundary | `swift test --filter "InsightsEngineTests\|BoundaryLintTests"` | 15 tests, 0 failures (incl. composition_surfacesFlare, gate_withholdsFlare, packageSourcesImportOnlyFoundation) | ✓ PASS |
| No-push source scan (manual mirror of FlareNoNotificationTests) | grep notification APIs in flare module | NONE | ✓ PASS |
| No-alarm-visual source scan (manual mirror of FlareNoAlarmVisualTests) | grep .red/.orange/exclamationmark/bell/flame in InsightsView+InsightCardView | NONE | ✓ PASS |
| App↔package copy byte-identity (manual mirror of FlareCopyDriftTests) | compare 6 FlareCopy vs FlareCopyText strings | 6/6 identical | ✓ PASS |
| App-target XCUITest/CopyAudit suites | `xcodebuild test` | ? not run (require simulator) — source-verified non-vacuous; flare E2E already ran GREEN per SUMMARY screenshot | ? SKIP (covered by human-verify) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| --- | --- | --- | --- | --- |
| INST-06 | 12-01, 12-02, 12-03 | User receives a heuristic, non-diagnostic flare early-warning with confidence (built last, gated by the honesty layer) | ✓ SATISFIED | All 3 SCs verified; FlareEngine composed behind HonestyGate; calm omit-when-empty .flare section first; no push; no alarm visual. REQUIREMENTS.md line 135 maps INST-06 → Phase 12 only; no orphaned IDs. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| --- | --- | --- | --- | --- |
| (none) | — | No TBD/FIXME/XXX/PLACEHOLDER/HACK in any Phase-12 source file | — | Clean. `return []` occurrences in FlareEngine are the intended honest-silence withholding (not stubs) — proven by passing absence tests. |

### Deferred Items (not Phase-12 gaps)

| # | Item | Note |
| --- | --- | --- |
| 1 | `InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute()` intermittent flake | Pre-existing Phase-8 async-debounce timing flake; passes in isolation; unrelated to flare files (never touch InsightsCoordinator). Documented in deferred-items.md (not a Phase-12 file). NOT a Phase-12 gap. |

### Human Verification Required

**1. Flare warning reads calm / uncertainty-first / non-alarmist on a real history (INST-06)**

- **Test:** On an account with ≥21 days incl. a recent rising symptom pattern + a logged trigger, open Insights → Flare early-warning. Also confirm an insufficient account shows NO warning.
- **Expected:** Reads calmly ("your recent pattern looks similar to times before more difficult days — something to keep an eye on and discuss with your clinician"), plain-language confidence (never a number), pervasive "not medical advice", NO alarm visual; insufficient account shows NO flare section.
- **Why human:** Calm/non-alarmist/uncertainty-first TONE is partly human judgment beyond the automated alarmist substring scan. The flare E2E already ran GREEN (screenshot `/tmp/e2e/insights-12-flare.png`, 367KB, 2026-06-10); this is the end-of-phase tone confirmation on that screenshot — the single deferred item per 12-VALIDATION.md.

### Gaps Summary

No gaps. All three Success Criteria (SC1 heuristic composed-behind-gate warning, SC2 withheld-when-insufficient + never-a-push, SC3 same gate/confidence/dismiss + calm-first section + no alarm visual) are verified against the actual source AND behaviorally proven by 20 passing package tests (`swift build` + `swift test`). The single requirement INST-06 is fully accounted for across all three plans with no orphaned IDs. The app-target XCUITest/CopyAudit/Drift/No-Notification/No-Alarm suites require the simulator and were not re-run here, but were source-verified as non-vacuous and the flare E2E already ran GREEN per the SUMMARY. The only open item is the deliberately deferred end-of-phase human tone check on the existing E2E screenshot — hence `human_needed`, not `passed`.

---

_Verified: 2026-06-10T03:05:00Z_
_Verifier: Claude (gsd-verifier)_
