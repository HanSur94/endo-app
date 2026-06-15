# Roadmap: Endo-App

## Overview

The Endo-App is a free, privacy-first iOS app that turns endometriosis self-tracking into genuine understanding. The v1.0 journey was dependency-driven: lock the irreversible foundations (CloudKit-safe append-only schema, a cycle/bleeding model that never assumes a 28-day cycle, GDPR explicit consent, a claims/inclusive-language policy) → build the manual logging surface → activate private CloudKit sync → build the isolated, Foundation-only insights engine against synthetic fixtures (trends, cycle-phase mapping, a cross-cutting honesty layer, correlations) → integrate it into a live dashboard → add HealthKit as an additive read-source → ship the doctor-ready PDF report → and finally compose the hardest insights on the proven engine: personalized recommendations and, built dead last and gated by the honesty layer, a heuristic flare early-warning.

## Milestones

| Version | Status | Phases | Shipped | Archive |
|---------|--------|--------|---------|---------|
| **v1.0** | ✅ Complete (feature-complete; release-gated human verifications pending) | 12 (39 plans) | 2026-06-10 | [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) · [audit](milestones/v1.0-MILESTONE-AUDIT.md) · [requirements](milestones/v1.0-REQUIREMENTS.md) |
| **v1.1** | 🔄 In progress | 4 (Phases 13–16) | — | — |

See [MILESTONES.md](MILESTONES.md) for the shipped-version history + key accomplishments.

### v1.0 — Pre-release checklist (carried forward)

Code-complete; these are inherently device/release-gated (captured in the v1.0 audit + phase HUMAN-UAT/VALIDATION docs):

- **CloudKit:** deploy the schema (incl. the `CachedInsight` record type) Development → Production in the CloudKit Console before App Store release; set a real `DEVELOPMENT_TEAM`; finalize the placeholder bundle id (`com.endoapp.ios`) + display name.
- **On-device / multi-device human verifications:** DATA-02/03/04 multi-device CloudKit sync survival (2 iCloud devices, `04-HUMAN-UAT`), on-device notification delivery (P3), real-device HealthKit read + auth sheet (P9), PDF clinician-credibility + share sheet (P10), recommendation discussion-prompt tone (P11), flare calm/uncertainty-first tone (P12).
- **Polish backlog:** the dismiss optimistic-vs-recompute scope (Phase 8, cosmetic), the midnight-crossing sleep night-attribution (Phase 9 WR-02), the pre-existing `InsightsCoordinatorTests` debounce flake.

## Current Milestone: v1.1 — Interactive Charts & Explore

**Goal:** Turn the static insight charts into interactive surfaces and add an Explore area where users combine any logged data series into one plot.

v1.1 adds three chart surfaces to the locked v1.0 honesty stack: scrub/tap-to-read on the dashboard trend charts, a new Explore tab overlaying multiple logged series on one shared time axis, and a factor-vs-factor scatter view. The build order is research-driven (all four research files converged independently): **scrubbing first** — it builds the reusable selected-date → logged-day-or-nil resolver, the selection accessibility pattern, and the deterministic `-uitest-select-day` E2E seam that both later surfaces reuse; **then the pure series catalog + extraction mappers** — the keystone dependency Explore and scatter both consume, landed as Foundation-only unit-tested helpers before any pixel exists (the v1.0 `ChartSeriesGrouping` playbook); **then the Explore tab** — by then pure composition, but containing the milestone's central mixed-scale lanes design decision; **finally the scatter view** + the correlation-card deep-link. Plain Swift Charts on the existing iOS 18 stack covers everything: zero new dependencies, zero schema changes, zero engine changes — Explore reads `Repository.daySnapshots()` directly and deliberately bypasses the InsightsEngine/HonestyGate, which gate claims, not raw data.

All v1.0 honesty invariants carry over: unlogged days stay gaps (absence ≠ 0), real units only (no dual y-axis, no silent normalization), binary tags as event lanes (never 0/1 lines), and combined plots stay descriptive — never causal framing.

**Requirement coverage:** 15/15 v1.1 requirements mapped (CHART-01..04 → Phase 13, EXPL-01..07 → Phase 15, SCAT-01..04 → Phase 16; Phase 14 is the foundational pure layer both UI phases consume).

**Cross-cutting acceptance criteria (every phase, from REQUIREMENTS.md):**

- All new chrome copy goes through the existing ClaimsPolicy scans AND is registered in `CopyAuditTests` (registration is opt-in per surface — an unregistered copy enum silently escapes the scans)
- Charts are never the sole carrier of meaning (text summary parity); series colors are colorblind-safe and never the only signal
- New files are added via `project.yml`/`xcodegen generate` (the `.xcodeproj` is generated)
- The PDF report render path (`ReportPDFRenderer` → `InsightTrendChart`) stays untouched by interactivity changes and is re-verified

## Phases

- [x] **Phase 13: Trend-Chart Scrubbing** - Scrub/tap any dashboard trend chart to read the exact day + value, gap-honest and accessible, with the PDF path untouched
- [x] **Phase 14: Series Catalog & Pure Extraction Layer** - Pure, unit-tested Foundation helpers (series catalog, extraction, scatter pairing) that lock the honesty invariants before any Explore pixel exists (completed 2026-06-15)
- [ ] **Phase 15: Explore Tab & Overlay Lanes** - A consent-gated Explore tab where users overlay multiple logged series as honest per-scale lanes + event lanes on one shared time axis
- [ ] **Phase 16: Scatter View & Correlation Deep-Link** - A descriptive-only factor-vs-factor scatter, reachable directly from a "Possible association" card

## Phase Details

### Phase 13: Trend-Chart Scrubbing

**Goal**: Users can read the exact day and value behind any dashboard trend chart by touch — gap-honest, haptic, and accessible — while the PDF report render path stays byte-identical
**Depends on**: Nothing in v1.1 (builds on the v1.0 Phase 8 `InsightTrendChart` / insights dashboard)
**Requirements**: CHART-01, CHART-02, CHART-03, CHART-04
**Success Criteria** (what must be TRUE):

  1. User can touch-and-hold/drag (scrub) on any dashboard trend chart and read the exact date + logged value via a rule-mark + lollipop tooltip
  2. Scrubbing onto an unlogged day shows an explicit "not logged" state — never a zero, never an interpolated value (a logged severity-0 still reads as 0)
  3. Day changes during a scrub give a selection haptic; the tooltip lives while touching and dismisses on release
  4. A VoiceOver user can read per-day values without the drag gesture (non-gesture accessibility alternative)
  5. The exported PDF report renders visually unchanged — interactivity never reaches the `ReportPDFRenderer` → `InsightTrendChart` path (additive defaulted `allowsSelection` param; PDF passes `false`)

**Plans**: 3 plans

Plans:

- [x] 13-01-PLAN.md — Pure `ChartScrubSelection` resolver in EndoInsights + invariant tests + audited tooltip copy (`scrubNotLogged`/`scrubSeverity` registered in `auditableStrings`)
- [x] 13-02-PLAN.md — Additive `allowsSelection` layer on `InsightTrendChart` (lollipop chrome, day-change haptic, `AXChartDescriptor`) + `InsightCardView` switch-on
- [x] 13-03-PLAN.md — `-uitest-select-day` seam + `-uitest-seed-scrub` fixture + `ChartScrubUITests` E2E + PDF zero-diff/regression gates

**UI hint**: yes
**Notes**: Selection resolves through a pure `ChartScrubSelection` mapper requiring an exact logged-day match — the reusable resolver Phases 15–16 consume (research Pitfall 1: position-based selection APIs interpolate by default). Tooltip/"not logged" copy registers in `CopyAuditTests` before it is written. Ships the DEBUG-gated `-uitest-select-day` E2E seam (deterministic, no gesture flake). Tap-to-select on the in-List dashboard (scrub-vs-scroll arbitration; free scrub is reserved for Explore's fixed-chart layout). `xcodegen generate` after file adds. Manual checkpoint: exported PDF visually re-verified; reference Apple Health scrub interaction verified on device during UAT.

### Phase 14: Series Catalog & Pure Extraction Layer

**Goal**: Every data need of Explore and scatter is served by pure, Foundation-only, unit-tested helpers that lock the honesty invariants in code before any Explore pixel exists
**Depends on**: Nothing in v1.1 (pure layer over the existing `DaySnapshot`/`InsightPoint` contracts; sequenced after Phase 13 per the research build order)
**Requirements**: None mapped directly — foundational keystone consumed by Phases 15–16 (unblocks EXPL-02, EXPL-03, EXPL-04, EXPL-05, EXPL-07, SCAT-01, SCAT-02)
**Success Criteria** (what must be TRUE):

  1. A unified `ExploreSeries` catalog covers every logged series (symptoms, sleep hours, activity minutes, stress, mood, bleeding, food tags) with name/unit/scale/kind metadata (ordinal/continuous/presence) per `DaySnapshot` field
  2. Series extraction emits no point for an unlogged day (nil → gap) and a real point for a logged literal 0 (absence ≠ 0) — proven by gap fixtures
  3. Presence-kind series (food tags, bleeding-as-events) extract as occurrence days, never coerced into 0/1 values — the series-kind model makes the coercion unrepresentable
  4. Scatter pairing returns only days where BOTH factors were logged, with identical day-pairs counted (aggregated) rather than duplicated
  5. All helpers are Foundation-only (boundary lint stays green) and emit the existing `InsightPoint`, so the shipped gap-breaking `ChartSeriesGrouping` is reused unchanged

**Plans**: 4 plans (1 keystone wave + 3 parallel composers)

- [x] 14-01-PLAN.md — ExploreSeries catalog + ScaleKind taxonomy + field-discriminator accessors + shared test harness (wave 1, keystone)
- [x] 14-02-PLAN.md — ExploreSeriesExtraction: two-return-type honesty move ([InsightPoint] quantitative / [Date] presence) + suppression gate (wave 2)
- [x] 14-03-PLAN.md — ScatterPairing: both-logged-only intersection + count-encoded ScatterPoint, no jitter/imputation (wave 2)
- [x] 14-04-PLAN.md — SeriesNormalization: fixed honest per-scale domain + real-unit readout round-trip (wave 2)

**Notes**: No UI in this phase — verification is the EndoInsights Swift Testing suite (the v1.0 `ChartSeriesGrouping` playbook: honesty properties become unit tests before rendering exists). Deliverables per research: `ExploreSeries` catalog, `ExploreSeriesExtraction`, `ScatterPairing`, `SeriesNormalization` (round-tripping real-unit readouts). New EndoInsights package files need no `project.yml` change; any app-target files go through `xcodegen generate`.

### Phase 15: Explore Tab & Overlay Lanes

**Goal**: Users combine any logged series into one honest overlay — aligned per-scale lanes and event-marker lanes sharing one time axis and one scrub state — from a new consent-gated Explore tab
**Depends on**: Phase 13 (scrub resolver, selection a11y pattern, `-uitest-select-day` seam), Phase 14 (series catalog + extraction)
**Requirements**: EXPL-01, EXPL-02, EXPL-03, EXPL-04, EXPL-05, EXPL-06, EXPL-07
**Success Criteria** (what must be TRUE):

  1. A consented user can open Explore as a third tab; the tab exists only inside `ConsentedShell`, so it is unreachable before consent (consent-by-construction)
  2. User can toggle series on/off via chips from the unified series catalog, capped at 3–4 quantitative series with a clear remove-to-add affordance
  3. Selected quantitative series render as aligned per-scale lanes sharing ONE time axis, each in its real unit (no dual y-axis, no silent normalization); binary food tags and bleeding render as event-marker lanes (date-aligned ticks), never fake 0/1 lines
  4. User can scrub the Explore chart and read every visible series' value — or a per-series "not logged" — for the selected day
  5. User can switch the time range with the familiar dashboard presets (absolute dates, gap breaks), and Explore shows honest empty ("pick a series") and sparse ("not enough data") states, with unlogged days staying gaps in every lane

**Plans**: 6 plans

Plans:

- [x] 15-01-PLAN.md — Lane-alignment prototype (A2 central-risk): 3 sibling lanes + shared RuleMark, screenshot, record the inset mechanism
- [x] 15-02-PLAN.md — ExploreSelection pure value type + ExploreSelectionTests (3–4 quantitative cap, toggle, suppression filter)
- [x] 15-03-PLAN.md — ExploreCopy audited enum + CopyAuditTests registration (empty/sparse/cap copy, windowLabel, "Not logged" pinned)
- [x] 15-04-PLAN.md — Lane stack: ExploreLaneChart + EventLaneChart + ExploreLanesView (shared X domain + shared scrub + single multi-series tooltip + a11y proxy)
- [ ] 15-05-PLAN.md — SeriesPickerView + range presets + ExploreView (empty/sparse states) + third Explore tab in ConsentedShell
- [ ] 15-06-PLAN.md — -uitest-seed-explore seam + ExploreUITests E2E (EXPL-01..07) + regression gates (Report/PDF untouched, dashboard, copy)

**UI hint**: yes
**Research**: true — contains the milestone's central mixed-scale design decision (aligned per-scale lanes vs a loudly-labeled normalized panel — prototype/decide before building; recovery cost is a full UI rework), the multi-series tooltip layout, and the bleeding-series-under-suppression product call (architecture supports either; record the decision in the phase plan)
**Notes**: `ExploreCopy` is created with `auditableStrings` and registered in `CopyAuditTests` BEFORE any copy is written (an unregistered copy enum silently escapes the scans; curate against substring collisions — "together", "whether", "higher", "other", "treatment"). Lanes re-key `series:` per (data-series × contiguous-run) via `ChartSeriesGrouping` so no lane interpolates across gaps, with `.foregroundStyle(by:)` carrying legend/color independently. Series colors colorblind-safe + grayscale/sparse-fixture screenshot checks; text summary parity. Series-selection state is view-local and ephemeral (persisting series keys would leak health-interest metadata outside the consent-gated store). Seeded E2E variant; `xcodegen generate` for new app files; PDF path untouched; scroll-vs-scrub OS-matrix check.

### Phase 16: Scatter View & Correlation Deep-Link

**Goal**: Users see the raw days behind any two factors — an honest, descriptive-only scatter where each point is one both-logged day — reachable directly from a "Possible association" card
**Depends on**: Phase 14 (`ScatterPairing`), Phase 15 (Explore screen, series picker, `ExploreCopy` infrastructure)
**Requirements**: SCAT-01, SCAT-02, SCAT-03, SCAT-04
**Success Criteria** (what must be TRUE):

  1. User can switch Explore to a scatter view plotting one factor against another, where each point is one day on which BOTH factors were logged
  2. Identical day-pairs stack honestly via count-encoded point size/opacity — never jitter that fabricates non-integer values
  3. The scatter stays descriptive-only: no fitted line, no correlation number, a neutral caption ("Based on N days where both were logged"), and an honest sparse state when too few both-logged days exist
  4. User can open a prefilled scatter from a "Possible association" card via a "see the days behind this" affordance

**Plans**: TBD
**UI hint**: yes
**Notes**: The scatter's chrome is the most causality-sensitive copy of the milestone — every caption/empty-state string goes through the audited `ExploreCopy` choke point with descriptive-only framing (no r-values, no "% impact", no trend line — by acceptance criterion and ClaimsPolicy). Text summary parity for the scatter; E2E via the Phase 13 selection seam; `xcodegen generate`; PDF path untouched. Sets up the v1.1.x tail (tap-point → day log) without building it.

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 13. Trend-Chart Scrubbing | 3/3 | Complete | 2026-06-15 |
| 14. Series Catalog & Pure Extraction Layer | 4/4 | Complete   | 2026-06-15 |
| 15. Explore Tab & Overlay Lanes | 4/6 | In Progress|  |
| 16. Scatter View & Correlation Deep-Link | 0/TBD | Not started | - |

---
*Roadmap updated: 2026-06-10 — Phase 13 planned (3 plans, 3 waves)*
