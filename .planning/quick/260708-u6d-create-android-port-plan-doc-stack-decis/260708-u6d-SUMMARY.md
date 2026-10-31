---
phase: quick-260708-u6d
plan: 1
subsystem: planning-research
tags: [android, port-plan, research-doc, docs-only]
dependency_graph:
  requires: []
  provides:
    - ".planning/research/ANDROID-PORT-PLAN.md — decision-complete Android port plan consumable by /gsd-new-milestone"
  affects:
    - "future Android milestone scoping (/gsd-new-milestone)"
tech-stack:
  added: []
  patterns:
    - "Decision-complete research doc: every row states Choice + Rationale + Alternatives, with Confidence tags on the two revisitable calls (Canvas-vs-Vico, Drive appDataFolder deferral)"
key-files:
  created:
    - ".planning/research/ANDROID-PORT-PLAN.md"
  modified: []
decisions:
  - "Android stack locked: Kotlin 2.x + coroutines/Flow, Jetpack Compose + Material 3 themed by a Bloom port, Room (10 entities mirroring SwiftData models, app-generated UUIDs kept for export identity), Health Connect READ-ONLY with the 4-record-type mapping, custom Compose Canvas chart layer (Vico = named escape hatch), PdfDocument + FileProvider/ACTION_SEND, minSdk 26, manual DI composition root, JUnit/kotlin.test + Room instrumentation + Compose UI tests, Gradle Kotlin DSL + version catalog"
  - "Sync v1 = local-only + user-initiated encrypted export/import (versioned platform-neutral JSON, passphrase-derived AES-GCM key, Storage Access Framework); Auto Backup restricted to prefs with the health DB excluded via backup rules; Drive appDataFolder deferred to v2-on-demand"
  - "iOS<->Android live cross-platform sync explicitly OUT OF SCOPE: private CloudKit is unreachable from Android and the no-backend constraint forbids a bridge; only contemplated path is a one-time handoff via the export format IF iOS ever ships an exporter"
  - "Monorepo: android/ directory in this repo; two modules — pure Kotlin JVM :insights (stdlib-only, boundary-lint-tested) and :app; dependency rule :app -> :insights only"
  - "Port order locked, insights-first: 8 phases from :insights engine port (with test case-count parity vs the Swift suite) through persistence (born-complete deleteDay cascade — the FactorReading GDPR Art. 17 lesson), consent shell, tracking, Health Connect, Explore/charts (locked honesty rules), PDF report, Play Store prep (Health apps policy + Data safety form)"
  - "All seven non-negotiable invariants mapped iOS mechanism -> Android mechanism, enforced by tests not convention (ClaimsPolicy/CopyAudit port, HonestyGate verbatim port, AppGate composable, flare notification structural scan, write-permission-free manifest as auditable artifact, no analytics SDKs, no accounts)"
metrics:
  duration: "~3 min"
  completed: "2026-07-08"
status: complete
---

# Phase quick-260708-u6d Plan 1: Android Port Plan Doc Summary

**One-liner:** Decision-complete `.planning/research/ANDROID-PORT-PLAN.md` — Kotlin/Compose/Room/Health-Connect stack, local-only-plus-encrypted-export sync with cross-platform sync explicitly out of scope, `android/` monorepo with a pure `:insights` JVM module, an insights-first 8-phase port order, and all seven non-negotiable invariants mapped to named Android test mechanisms.

## What Was Built

One new research document (220 lines), sibling of STACK.md / ARCHITECTURE.md / PITFALLS.md:

- **Section 1 — Stack Decisions:** 10-row decision table (Choice | Rationale | Alternatives) covering language, UI, persistence, health integration, charts, PDF, SDK levels, DI, testing, build — plus nuance notes (Health Connect runtime gating and per-permission grant-state difference vs HealthKit, UUID identity discipline despite Room's relaxed constraints, the Canvas-vs-Vico MEDIUM-confidence tag) and a "What NOT to use" list (no backend SDKs, no analytics, no auth, no third-party charts by default, no cloud/LLM inference).
- **Section 2 — Sync & Backup Strategy:** v1 = local-only + user-initiated encrypted export/import (versioned platform-neutral JSON, AES-GCM with KDF-derived passphrase key, SAF, doubles as GDPR portability + device migration). Auto Backup and Drive appDataFolder each evaluated with honest verdicts. Exact-heading subsection `### Explicitly Out of Scope: iOS-Android Cross-Device Sync` with the CloudKit/no-backend rationale.
- **Section 3 — Repository & Module Layout:** `android/` in this repo (monorepo rationale: shared planning, shared copy-policy vocabulary, one tracker; iOS untouched), directory listing, `:insights` pure-JVM module definition with the stdlib-only boundary lint rule, `:app` package sketch (data/health/insightsbridge/ui/designsystem/report), one-way dependency rule.
- **Section 4 — Phased Port Roadmap:** 8 ordered phases, each with Goal / Key deliverables / Exit criteria; insights-first rationale stated; the deleteDay-cascade GDPR lesson and the locked chart honesty rules carried into the relevant phases; note that phases 3–7 mirror the validated iOS v1.0 order.
- **Section 5 — Non-Negotiable Invariants:** 7-row table (Invariant | iOS mechanism | Android mechanism) covering claims-policy copy gating, honesty gate, consent-by-construction, no flare alarms, read-only health import, no analytics, no accounts — plus the tests-not-convention enforcement philosophy paragraph.
- **`## How to Consume This Doc` footer:** 5 bullets telling /gsd-new-milestone that Section 4 = milestone phases, Sections 1–3 are locked imports, Section 5 rows become cross-phase requirements/tests, and exactly two calls (Canvas-vs-Vico, Drive appDataFolder) are consciously revisitable.

## Verification

- Task 1 automated greps: `SECTIONS-1-3-OK` (file exists; `## 1./2./3.` headings; "Explicitly Out of Scope"; "Health Connect"; "minSdk 26").
- Task 2 automated greps: `DOC-COMPLETE` (`## 4./5.` + footer headings; exactly 5 `^## N.` sections; HonestyGate; Data safety; FactorReading).
- `git status` clean after commits — zero source-code changes; only the new doc committed.
- Consistency re-read: Section 2 back-references Section 1's UUID decision; footer's "exactly two revisitable calls" matches the two MEDIUM-confidence tags; no hedging on locked calls.

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None — docs-only deliverable; no code surfaces created.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | fa86ba3 | docs(quick-260708-u6d): add Android port plan Sections 1-3 (stack, sync, module layout) |
| 2 | fe20612 | docs(quick-260708-u6d): add phased port roadmap, invariants mapping, and consumption footer |

## Self-Check: PASSED

- ANDROID-PORT-PLAN.md exists; SUMMARY.md exists; commits fa86ba3 and fe20612 present; no file deletions in either commit.
