# Roadmap: Endo-App

## Overview

The Endo-App is a free, privacy-first iOS app that turns endometriosis self-tracking into genuine understanding. The v1.0 journey was dependency-driven: lock the irreversible foundations (CloudKit-safe append-only schema, a cycle/bleeding model that never assumes a 28-day cycle, GDPR explicit consent, a claims/inclusive-language policy) → build the manual logging surface → activate private CloudKit sync → build the isolated, Foundation-only insights engine against synthetic fixtures (trends, cycle-phase mapping, a cross-cutting honesty layer, correlations) → integrate it into a live dashboard → add HealthKit as an additive read-source → ship the doctor-ready PDF report → and finally compose the hardest insights on the proven engine: personalized recommendations and, built dead last and gated by the honesty layer, a heuristic flare early-warning.

## Milestones

| Version | Status | Phases | Shipped | Archive |
|---------|--------|--------|---------|---------|
| **v1.0** | ✅ Complete (feature-complete; release-gated human verifications pending) | 12 (39 plans) | 2026-06-10 | [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) · [audit](milestones/v1.0-MILESTONE-AUDIT.md) · [requirements](milestones/v1.0-REQUIREMENTS.md) |

See [MILESTONES.md](MILESTONES.md) for the shipped-version history + key accomplishments.

### v1.0 — Pre-release checklist (carried forward)

Code-complete; these are inherently device/release-gated (captured in the v1.0 audit + phase HUMAN-UAT/VALIDATION docs):

- **CloudKit:** deploy the schema (incl. the `CachedInsight` record type) Development → Production in the CloudKit Console before App Store release; set a real `DEVELOPMENT_TEAM`; finalize the placeholder bundle id (`com.endoapp.ios`) + display name.
- **On-device / multi-device human verifications:** DATA-02/03/04 multi-device CloudKit sync survival (2 iCloud devices, `04-HUMAN-UAT`), on-device notification delivery (P3), real-device HealthKit read + auth sheet (P9), PDF clinician-credibility + share sheet (P10), recommendation discussion-prompt tone (P11), flare calm/uncertainty-first tone (P12).
- **Polish backlog:** the dismiss optimistic-vs-recompute scope (Phase 8, cosmetic), the midnight-crossing sleep night-attribution (Phase 9 WR-02), the pre-existing `InsightsCoordinatorTests` debounce flake.

## Next milestone

Not yet defined. Run `/gsd-new-milestone` to scope the next version (questioning → research → requirements → roadmap). Candidate directions: realize the release checklist into a "ship to TestFlight / App Store" milestone, or pull from the v2 backlog already parked in the archived requirements (educational content, guided programs, n-of-1 experiments, on-device ML flare model, HealthKit write-back, partner sharing).
