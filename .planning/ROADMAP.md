# Roadmap: Endo-App

## Overview

The Endo-App is a free, privacy-first iOS app that turns endometriosis self-tracking into genuine understanding. v1.0 built the foundations → manual logging → private CloudKit sync → the Foundation-only insights engine (trends, cycle-phase, correlations, the honesty layer) → live dashboard → HealthKit read-source → doctor-ready PDF report → recommendations → flare early-warning. v1.1 turned the static insight charts interactive (scrub-to-read) and added an Explore area where users combine any logged data series — overlay lanes + a descriptive-only factor-vs-factor scatter reachable from a "Possible association" card — all on the same locked honesty stack (absence ≠ 0, no dual-y/no normalization, descriptive-only, no causal framing).

## Milestones

| Version | Status | Phases | Shipped | Archive |
|---------|--------|--------|---------|---------|
| **v1.0** | ✅ Complete (feature-complete; release-gated human verifications pending) | 12 (39 plans) | 2026-06-10 | [roadmap](milestones/v1.0-ROADMAP.md) · [audit](milestones/v1.0-MILESTONE-AUDIT.md) · [requirements](milestones/v1.0-REQUIREMENTS.md) |
| **v1.1** | ✅ Complete (feature-complete; device/host-gated verifications pending) | 4 (17 plans) | 2026-06-17 | [roadmap](milestones/v1.1-ROADMAP.md) · [audit](v1.1-MILESTONE-AUDIT.md) · [requirements](milestones/v1.1-REQUIREMENTS.md) |

See [MILESTONES.md](MILESTONES.md) for the shipped-version history + key accomplishments.

### v1.0 — Pre-release checklist (carried forward)

Code-complete; inherently device/release-gated (captured in the v1.0 audit + phase HUMAN-UAT/VALIDATION docs):

- **CloudKit:** deploy the schema (incl. the `CachedInsight` record type) Development → Production in the CloudKit Console before App Store release; set a real `DEVELOPMENT_TEAM`; finalize the placeholder bundle id (`com.endoapp.ios`) + display name.
- **On-device / multi-device human verifications:** DATA-02/03/04 multi-device CloudKit sync survival (2 iCloud devices, `04-HUMAN-UAT`), on-device notification delivery (P3), real-device HealthKit read + auth sheet (P9), PDF clinician-credibility + share sheet (P10), recommendation discussion-prompt tone (P11), flare calm/uncertainty-first tone (P12).
- **Polish backlog:** the dismiss optimistic-vs-recompute scope (Phase 8, cosmetic), the midnight-crossing sleep night-attribution (Phase 9 WR-02), the pre-existing `InsightsCoordinatorTests` debounce flake.

### v1.1 — Pre-release checklist (carried forward)

All v1.1 automated criteria are green; these are device/host-gated (per each phase's VALIDATION Manual-Only table + the v1.1 audit):

- **Device feel/legibility:** trend-chart haptic + scroll-vs-scrub feel + exported-PDF visual identity + VoiceOver Audio Graph (P13); lane alignment + multi-series scrub feel + multi-lane VoiceOver + colorblind-safe lane distinction (P15); scatter count-encoding legibility + descriptive-only tone + colorblind-safe scatter distinction (P16).
- **Host-flaky re-run:** `ReportFlowUITests/testGeneratePDF_fromPopulatedRange_showsPreview` could not run to a clean result on this (post-midnight, degraded) simulator host — the PDF render path is provably untouched (`git log v1.0..HEAD -- ReportPDFRenderer.swift` empty), so re-run it on a clean host / CI.

## Next milestone

Not yet defined. Run `/gsd-new-milestone` to scope the next version (questioning → research → requirements → roadmap). Candidate directions already parked: realize the v1.0/v1.1 pre-release checklists into a "ship to TestFlight / App Store" milestone; or pull from the v1.1.x/v2 backlog in the archived requirements (tap-a-scatter-point → day log [EXPL-08], saved Explore configs [EXPL-09], landscape Explore [EXPL-10], lag-shifted comparison [EXPL-11], Explore chart export into the PDF report [EXPL-12]; the `CHART-05` scroll/pan + `BODY-01` silhouette body-map candidates); or the parked v2 themes (education, guided programs, n-of-1 experiments, ML flare model, HealthKit write-back, partner sharing).
