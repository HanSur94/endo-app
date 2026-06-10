# Feature Research

**Domain:** Chart interactivity (scrub/tooltip) + multi-series "explore" overlays + scatter views in consumer health/cycle-tracking apps (v1.1 milestone of Endo-App)
**Researched:** 2026-06-10
**Confidence:** MEDIUM — synthesized from competitor support docs (Bearable, Guava), Apple platform conventions (Health/Stocks, Swift Charts WWDC material), and dataviz literature (dual-axis critiques, Xiong et al. VIS 2019 on causality perception). No hands-on audit of competitor builds; UI specifics may have drifted.

> Scope note: this file covers ONLY the v1.1 features (interactive trend charts, Explore overlay screen, scatter view). The v1.0 feature landscape this milestone builds on lives in `.planning/milestones/` history and PROJECT.md Validated requirements. Do not re-derive v1.0 scope from this file.

## Benchmark Summary

| Product | Scrub/tooltip | Multi-series overlay | Scatter | Notable for us |
|---------|---------------|----------------------|---------|----------------|
| **Apple Health** | Touch-and-hold a chart → lollipop readout of day + exact value; drag to scrub; segmented time-range picker (W/M/6M/Y) | Only curated "Highlights" pair two related metrics; no free-form user overlay (a gap third-party apps like Health Hive exist to fill) | None | THE iOS gesture vocabulary users arrive with. Match it or feel broken. |
| **Bearable** | Tooltips on charts; less polished than Apple | Best-in-class: rotate to landscape on Insights → comparison graph; toggle Mood/Symptoms/Energy/Sleep/measurements via buttons below chart; **binary Factors shown as highlighted days, not lines** | No raw scatter — shows computed "% effect" of factors instead | Closest comparator for Explore. Its factor-day-highlight pattern validates an event-lane approach for binary tags. Its % effect numbers are exactly what our honesty policy forbids. |
| **Guava** | Tap calendar/chart entries for detail | Per-symptom detail has a "Compare" button to overlay other symptoms/metrics; toggles for frequency vs severity and time period | No raw scatter — correlation/insight cards instead | Validates "start from one metric, add comparisons" as an alternate entry path to a blank Explore canvas. |
| **Clue** | Basic chart taps | Cycle-length line of last 6 cycles vs ACOG range; Analysis tab tools — **no free-form overlay** | None | Mainstream cycle apps don't do user-driven overlays at all — Explore is genuinely beyond category norm. |
| **Flo** | Basic | Graphs & Reports: cycle duration charts only | None | Same — confirms Explore is a differentiator, not table stakes, for the cycle-app category. |

**Strategic read:** Scrub-to-read-value is iOS table stakes (Apple Health trained everyone). A user-driven multi-series Explore is a *differentiator* — only Bearable/Guava have anything close, and both hide honest raw data behind computed claims. A raw scatter view is rarer still and aligns unusually well with this app's honesty invariants: visualization research (Xiong et al., VIS 2019) found scatter plots elicit the *lowest* causality ratings of common chart types — the least causally-suggestive way to show an association.

## Feature Landscape

### Table Stakes (Users Expect These)

Missing these = the new surfaces feel broken or below iOS platform standard.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Touch-and-hold + drag scrub with lollipop tooltip** (vertical rule + date + exact value) | Apple Health/Stocks trained every iOS user; "tap chart, see number" is the platform reflex | **LOW–MED** | iOS 17+ `.chartXSelection` binding + `RuleMark` + `.annotation` is the canonical implementation; gesture recognition is handled by the framework. Applies to all existing dashboard trend charts. |
| **Tooltip readout pinned above the finger / chart top** | Finger occludes the point; Health/Stocks pin the readout at the top edge | **LOW** | Annotation position + collision handling near chart edges (first/last days) is the only fiddly part. |
| **Gap-honest tooltip** ("Not logged" on unlogged days, never 0) | This app's own v1.0 invariant (absence ≠ 0) extended to the new surface; a tooltip showing 0 on a gap day would silently break it | **LOW–MED** | Scrub selection must resolve to *logged days only* or explicitly render a "no entry" state. This is the one place generic Swift Charts tutorials will mislead — they interpolate to nearest point. |
| **Selection haptic on day change** | Health/Stocks do it; scrubbing feels dead without it | **LOW** | `UISelectionFeedbackGenerator` on day boundary crossings. |
| **Tooltip dismissal** (release-to-dismiss or tap-elsewhere) | Standard; a stuck tooltip reads as a bug | **LOW** | Decide persist-on-tap vs live-only-while-touching; Health uses live-while-touching. |
| **Series picker as toggle chips/buttons** on Explore | Bearable (buttons below chart) and Guava (Compare button) both converge on this; users expect add/remove per tap, not a settings form | **MED** | Needs a unified "series catalog" (every loggable metric with id, display name, unit, scale type, kind: continuous/ordinal/binary). This catalog is the real work of Explore. |
| **Distinct series colors + legend** | Basic legibility for any overlay | **LOW** | Color assignments must be stable per metric across sessions and color-blind-safe; reuse insight-dashboard palette where metrics already have colors. |
| **Shared time axis + existing time-range control on Explore** | Consistency with v1.0 dashboard (absolute dates, range presets) | **LOW–MED** | Reuse v1.0 axis/gap rendering. Do not introduce a second axis vocabulary. |
| **Empty/sparse states** ("pick at least one series", "not enough overlapping days for a scatter") | Explore with 0–1 selected series or a scatter with 2 shared days must not render nonsense | **LOW–MED** | Mirrors v1.0 withheld-when-insufficient discipline; copy goes through ClaimsPolicy scan. |
| **Scrub works on Explore overlay too** (one tooltip listing each visible series' value for that day) | Once charts are scrubbable, an inert Explore chart feels inconsistent | **MED** | Multi-series tooltip layout (n values + "not logged" per missing series) is a small design problem of its own. |
| **Accessibility parity** (VoiceOver summaries / Audio Graphs on new charts) | Platform expectation for charts; v1.0 charts presumably ship descriptors — new surfaces can't regress | **MED** | Swift Charts provides accessibility descriptors; multi-series + scatter need bespoke audio-graph descriptions. |

### Differentiators (Competitive Advantage)

Where v1.1 beats the category — all flow from the same honesty positioning as v1.0.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Honest mixed-scale overlay: aligned lanes / banded layout instead of dual-axis spaghetti** | Dataviz consensus: dual independent y-axes mislead (arbitrary crossovers, scale manipulation). Stacked per-scale lanes sharing one time axis keep every series in honest units — no competitor does this deliberately | **MED–HIGH** | E.g. severity (0–10) lane, hours lane, 1–5 lane vertically aligned on one x-axis; or one plot area with per-series labeled bands. This is the core design decision of Explore — prototype early. |
| **Binary tags as an event-marker lane** (tick strip under the plot), not fake 0/1 lines | Bearable's day-highlight pattern proves it; a 0/1 line series is visual noise and implies a scale that doesn't exist | **MED** | One row of date-aligned ticks per selected tag (e.g. "dairy" dots under the chart). Also the honest home for bleeding-intensity if treated as ordinal events. |
| **Raw scatter view, one point = one logged day** | The "show me the actual data" move no mainstream cycle app makes; research says scatter is the least causality-implying encoding — a perfect fit for the descriptive-only policy | **MED** | Day-pairing: only days where BOTH factors are logged become points (absence ≠ 0 again). Discrete scales (0–10 vs 1–5) collide on a grid — needs jitter or count-encoded point size to handle overplotting honestly. |
| **Tap a scatter point → see the exact day, jump to that day's log** | Turns an abstract cloud into "oh, that was the day I…" — uniquely useful for lay users; nobody in category does it | **MED** | Needs point→date identity and a navigation hook into the existing log/day view. |
| **Cross-link from gated correlation cards → prefilled Explore/scatter** ("see the days behind this") | The correlation engine says "possible association"; Explore lets the user inspect the raw evidence — deepens trust, exercises the differentiator | **LOW–MED** | Pure wiring: correlation card carries (factorA, factorB) → opens scatter preconfigured. High leverage per effort. |
| **Descriptive-only chart chrome by design** (no r-values, no trend lines, no "% impact") | Bearable sells "% effect on your mood" — statistically shaky on n≈30 self-tracked days; our restraint is the brand | **LOW** | Mostly a *negative* spec (see anti-features) plus one neutral caption pattern, ClaimsPolicy-scanned. |
| **Portrait-first Explore** | Bearable forces landscape rotation for its comparison graph — a known friction point | **LOW–MED** | Lanes layout works in portrait; landscape can be a later enhancement, not a prerequisite. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Dual independent y-axes** (two scales overlaid in one plot area) | "Just put sleep hours and pain on the same chart" — obvious-seeming ask | Crossover points are arbitrary; independent scales manufacture fake visual relationships; unclear which line owns which axis. Dataviz literature near-unanimously warns against it | Aligned per-scale lanes sharing the x-axis; or an explicitly labeled "shape view" normalization (below) |
| **Silent normalization to 0–100 "to fit everything on one axis"** | Makes the overlay look clean | Hides real units; a 6→7 severity move and a 5→8h sleep move become visually identical; violates the honest-axis invariant | If offering a normalized mode at all, label it loudly ("shapes, not values"), keep per-series real-unit readout in the scrub tooltip, and keep lanes as the default |
| **Trend/regression line + correlation coefficient on the scatter** | "The classic correlation look" includes a fitted line; users may ask for it | A fitted line is a visual causal/strength claim; numeric r is exactly the numeric-confidence framing v1.0 forbids; meaningless at sparse n | Raw points only; neutral caption ("each point is one logged day"); the *gated* correlation engine remains the only thing allowed to say "possible association" |
| **Smoothing / moving averages that bridge gaps** | Self-tracked data is jagged; smoothed lines look nicer | Interpolating across unlogged days fabricates data (absence ≠ 0); a moving average that spans gaps double-fabricates | Keep v1.0 gap breaks. If smoothing is ever added, compute only within contiguous logged runs and label it — defer entirely from v1.1 |
| **Unlimited series in one overlay** | "Why can I only add N?" | Overplotting spaghetti; tooltip becomes a wall; lane layout degrades; Bearable's own chart gets noisy fast | Cap at 3–4 quantitative series + a couple of event lanes; clear "remove one to add another" affordance |
| **On-the-fly correlation/insight badges inside Explore** ("these two look related!") | Feels helpful, "smart" | Bypasses every v1.0 honesty gate (sufficiency thresholds, suppression, claims policy); turns a descriptive surface into an ungated claims engine | Explore stays descriptive; the only bridge is the *inbound* link from already-gated correlation cards |
| **Causal/affirmative copy in new chrome** ("trigger found", "X affects Y") | Marketing-style copy is the category default (Bearable/Guava both use causal verbs) | Violates ClaimsPolicy and the app's positioning; legal/positioning risk for a wellness app | All v1.1 copy through existing ClaimsPolicy scans; descriptive verbs only ("logged together", "on days you logged X…") |
| **Pinch-zoom / free pan on the time axis** | Power-user expectation from finance apps | High implementation cost; `chartScrollableAxes` + selection gestures interact poorly; range presets already cover the need | Keep segmented range presets (consistent with dashboard); revisit only on user demand |
| **Binary tags rendered as 0/1 line series** | Simplest implementation of "add food tag to chart" | Implies a continuous scale that doesn't exist; lines between 0s and 1s are pure noise | Event-marker lane (tick strip), per Bearable's day-highlight precedent |

## Feature Dependencies

```
[Scrub/tooltip on dashboard charts]
    └──requires──> [v1.0 dashboard trend charts (Phase 8)]   ✓ exists
    └──requires──> [v1.0 gap rendering / absence≠0 model]    ✓ exists (must be honored by selection logic)

[Explore overlay screen]
    └──requires──> [Series catalog abstraction]  ← NEW: unified metadata for every loggable metric
                       └──requires──> [v1.0 logging models + ImportedSample merge]  ✓ exists
    └──requires──> [Scrub/tooltip mechanics]     (reuse; multi-series tooltip variant)
    └──requires──> [Event-lane rendering]        ← NEW: binary/ordinal lanes
    └──requires──> [ClaimsPolicy scan coverage for new copy]  ✓ exists, extend

[Scatter view]
    └──requires──> [Series catalog abstraction]  (same NEW dependency)
    └──requires──> [Day-pairing logic]           ← NEW: both-logged-day intersection
    └──enhances──> [v1.0 gated correlation cards]  via prefilled deep-link ("see the days behind this")

[Tap-point → day log navigation] ──enhances──> [Scatter view]
[Multi-series tooltip] ──requires──> [single-series tooltip] (build single first)

[On-the-fly explore insights] ──conflicts──> [v1.0 honesty gate architecture]  (anti-feature)
[Dual y-axes / silent normalization] ──conflicts──> [honest-axis invariant]    (anti-feature)
```

### Dependency Notes

- **Series catalog is the keystone:** Both Explore and Scatter need one registry describing each metric's unit, scale (0–10 ordinal, hours continuous, 1–5 ordinal, binary tag, bleeding ordinal), source (manual vs HealthKit-imported), and gap semantics. Build it once, first; both screens consume it.
- **Scrub before Explore:** The tooltip/selection mechanics built on the existing single-series dashboard charts are reused (and complicated) by the multi-series overlay — sequencing scrub first de-risks Explore.
- **Scatter is independent of the overlay chart** beyond the shared catalog + day-pairing — it can land in a separate phase/parallel track.
- **Correlation-card deep-link is post-scatter glue:** trivial once scatter accepts a (factorA, factorB) configuration; schedule it after scatter works.
- **ClaimsPolicy extension is cross-cutting:** every new copy string (tooltips, empty states, captions, series names in sentences) enters the existing forbidden-phrasing scans; treat as acceptance criteria on every v1.1 phase, not a separate feature.

## MVP Definition

### Launch With (v1.1)

- [ ] Scrub/tap-to-read on all existing dashboard trend charts (rule mark + lollipop, gap-honest "not logged", haptic) — the table-stakes core of the milestone
- [ ] Series catalog abstraction — prerequisite for everything else
- [ ] Explore screen: 2–4 quantitative series in aligned per-scale lanes on a shared time axis, toggle-chip picker, shared range presets, multi-series scrub tooltip
- [ ] Binary food tags (+ bleeding, if treated as events) as event-marker lanes in Explore
- [ ] Scatter view: factor vs factor, one point per both-logged day, overplotting handled (jitter or count-sized points), descriptive caption, empty-state gating
- [ ] ClaimsPolicy scan coverage over all new copy — invariant, not optional

### Add After Validation (v1.1.x)

- [ ] Tap-a-scatter-point → that day's log — once scatter ships and navigation hook is confirmed cheap
- [ ] Correlation card → prefilled scatter deep-link — once both ends exist; high leverage, low cost
- [ ] Saved/recent Explore configurations — if users demonstrably rebuild the same comparison repeatedly
- [ ] Landscape-optimized Explore layout — if portrait lanes feel cramped with 3–4 series in real use

### Future Consideration (v2+)

- [ ] Lag-shifted comparison ("factor day N vs symptom day N+1") — Bearable does timing-aware association internally; exposing a lag control is powerful but a UX + honesty-framing project of its own
- [ ] Explore chart export into the PDF report — wait until Explore configurations prove valuable
- [ ] Pinch-zoom/pan time axis — only on demonstrated demand; conflicts with selection gestures

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Scrub/tooltip on existing charts | HIGH | LOW–MED | P1 |
| Series catalog | (enabler) | MED | P1 |
| Explore overlay (lanes + chips + tooltip) | HIGH | HIGH | P1 |
| Event lanes for binary tags | MEDIUM | MED | P1 |
| Scatter view (raw points, gated empty states) | HIGH | MED | P1 |
| Multi-series scrub tooltip | MEDIUM | MED | P1 (with Explore) |
| Accessibility parity on new charts | MEDIUM | MED | P1 (non-negotiable platform bar) |
| Scatter point → day log | MEDIUM | LOW–MED | P2 |
| Correlation card → scatter deep-link | MEDIUM | LOW | P2 |
| Saved Explore configs | LOW–MED | MED | P3 |
| Lag-shifted comparison | MEDIUM | HIGH | P3 |
| Pinch-zoom/pan | LOW | HIGH | P3 |

## Competitor Feature Analysis

| Feature | Apple Health | Bearable | Guava | Our Approach |
|---------|--------------|----------|-------|--------------|
| Scrub/tooltip | Touch-and-hold lollipop, drag scrub, haptic — the reference implementation | Tooltips, less refined | Tap-for-detail | Match Apple Health's gesture vocabulary exactly, plus gap-honest "not logged" readouts they don't need (their data has no absence-≠-0 problem) |
| Multi-series overlay | Curated Highlights pairs only; no user control | Landscape comparison graph, button toggles, factor day-highlights | Per-symptom "Compare" button | User-driven like Bearable but portrait-first, lanes instead of one shared/normalized plot area, event lanes for binaries |
| Mixed scales | Avoided (pairs are curated to compatible units) | Single plot; scale handling undocumented/implicit | Undocumented | Explicit per-scale lanes; any normalized mode loudly labeled — the honesty differentiator |
| Scatter | None | None (shows computed "% effect" instead) | None (correlation cards instead) | Raw day-points scatter, no fit line, no coefficient — show evidence, let gated engine make the (hedged) claims |
| Causal framing | Neutral | "% effect of factor on mood" — affirmative numeric claims | "find what could be causing flare-ups" — softened causal | Strictly descriptive; ClaimsPolicy-scanned; "possible associations" language only via existing gated engine |

## Sources

- [Bearable — How to find correlations](https://bearable.app/support/howto/how-to-find-correlations/), [Visualising your health data in many forms](https://bearable.app/support/tips/visualise-your-health-data-in-many-forms/) — comparison graph mechanics, factor highlighting, data-sufficiency thresholds (MEDIUM confidence: vendor docs, cross-checked)
- [Guava — Tracking and Visualizing Symptoms](https://guavahealth.com/article/tracking-and-visualizing-symptoms-in-guava), [Ultimate Guide](https://guavahealth.com/article/guava-ultimate-guide) — Compare button, frequency/severity toggles (MEDIUM)
- [Apple Support — View your health data](https://support.apple.com/guide/iphone/view-your-health-data-iphe3d379c32/ios) + [Health Hive: Compare Metrics](https://apps.apple.com/us/app/health-hive-compare-metrics/id1613535038) — Health chart interaction model; third-party compare-gap evidence (MEDIUM; gesture specifics partly from platform knowledge, verify on device)
- Dual-axis critique: [PolicyViz](https://policyviz.com/2022/10/06/avoiding-the-dual-axis-chart/), [Flourish](https://flourish.studio/blog/dual-axis-charts/), [Data Revelations](https://www.datarevelations.com/be-careful-with-dual-axis-charts/), [Why Two Y-Axes (Y2Y)](https://uncharted.software/assets/WhyTwoYAxes_Y2Y.pdf) (MEDIUM, convergent)
- Causality perception: [Xiong et al., "Illusion of Causality in Visualized Data", IEEE VIS 2019](https://users.eecs.northwestern.edu/~jhullman/VIS_2019_Correlation_Causation_CR.pdf) — scatter plots elicit lowest causality ratings (MEDIUM)
- Swift Charts interactivity: [WWDC23 — Explore pie charts and interactivity in Swift Charts](https://developer.apple.com/videos/play/wwdc2023/10037/), [Swift with Majid — Mastering charts: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/) (MEDIUM; implementation detail belongs in STACK)
- Cycle-app baseline: [Clue — Cycle Phase Insights](https://helloclue.com/articles/about-clue/discover-cycle-phase-insights-understand-your-body-feel-empowered), [Flo — Analyzing your cycles and symptoms](https://help.flo.health/hc/en-us/articles/4407228784276-Analyzing-your-cycles-and-symptoms) (MEDIUM)

---
*Feature research for: Endo-App v1.1 — Interactive Charts & Explore*
*Researched: 2026-06-10*
