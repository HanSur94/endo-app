# Pitfalls Research

**Domain:** v1.1 Interactive Charts & Explore — adding scrub/tap selection, multi-series overlay plots, and a factor-vs-factor scatter view to an existing, invariant-locked Swift Charts iOS app (Endo-App v1.0)
**Researched:** 2026-06-10
**Confidence:** HIGH for codebase-integration pitfalls (read directly from the v1.0 source: `InsightTrendChart.swift`, `ChartSeriesGrouping.swift`, `InsightsView.swift`, `ClaimsPolicy.swift`, `CopyAuditTests.swift`, `ReportPDFRenderer.swift`, `InsightsDashboardUITests.swift`); MEDIUM for Swift Charts platform behaviors (Apple Developer Forums + community write-ups, cross-checked against API docs); MEDIUM for visualization-honesty pitfalls (established vis literature applied to this app's specific invariants)

> Reading order for the roadmap author: the three pitfalls flagged **[INVARIANT-BREAKING]** are the ones that can silently violate v1.0's locked honesty invariants (absence ≠ 0, no causal language, no numeric confidence) *through the new chart surfaces* even though all existing tests stay green — because the existing guards don't cover the new code paths until they are explicitly extended. Treat extending the guard surface (ChartSeriesGrouping reuse, ClaimsPolicy registration, selection-to-logged-day mapping) as first-class requirements, not implementation details.

---

## Critical Pitfalls

### Pitfall 1: Selection tooltip reads a value on an unlogged day (interpolated/phantom value)  **[INVARIANT-BREAKING]**

**What goes wrong:**
`chartXSelection(value: $selectedDate)` (and any `chartOverlay` + `ChartProxy.value(atX:)` fallback) returns a **continuous Date for any x-position in the plot area** — including positions inside a gap where the user never logged. A naive lollipop implementation looks up "the value at the selected x" by interpolating between neighboring points, or snaps to the nearest point without a tolerance and shows its value under a *different day's* rule mark. Either way the tooltip fabricates data for an unlogged day — the exact "absence ≠ 0" violation v1.0's gap-broken lines were built to prevent, now reintroduced through the interaction layer.

**Why it happens:**
The selection API is position-based, not data-based. Every tutorial implementation (`swiftwithmajid.com` selection pattern, WWDC23 interactivity session) demonstrates "snap to nearest point" with no notion of "this x has no data, show nothing." The chart *looks* honest (line still breaks at gaps) while the tooltip lies.

**How to avoid:**
- Resolve selection through a **pure, unit-testable mapper** (sibling to `ChartSeriesGrouping`): `selectedDate → exact logged calendar day or nil`. Snap the raw selection Date to `calendar.startOfDay`, then require an **exact logged-day match** (or at most ±0 days). No match → no lollipop, no tooltip, optionally a quiet "no entry on <date>" affordance.
- For multi-series (Explore): the mapper returns per-series `value?` — a series with no entry that day renders "not logged" (or is omitted from the tooltip), **never 0** and never a nearest-neighbor substitute.
- Unit-test the mapper with the same gap fixtures `ChartSeriesGroupingTests` uses: selection inside a gap → nil; selection on a logged severity-0 day → 0 (the literal value, distinct from a gap).

**Warning signs:**
- Tooltip code calls `proxy.value(atX:)` and then indexes into the raw points array with `min(by: distance)` and no day-equality check.
- A demo scrub across a sparse 90-day window shows a continuously moving value with no dead zones.
- Tooltip shows a value for a date that doesn't appear in the seeded fixture.

**Phase to address:**
The scrub/tap phase (first interactive phase) — the mapper is the foundation every later surface (Explore overlay, scatter) reuses.

---

### Pitfall 2: Explore overlay reimplements line drawing without `ChartSeriesGrouping` (connects across gaps)  **[INVARIANT-BREAKING]**

**What goes wrong:**
The Explore overlay is a *new* chart view plotting multiple heterogeneous series (severity, sleep hours, stress, mood, binary food tags). The natural first implementation is `ForEach(series) { ForEach(points) { LineMark(x:, y:, series: .value("Series", series.name)) } }` — one Swift Charts `series` value **per data series**. That is exactly v1.0's "Pitfall 1": Swift Charts connects every `LineMark` sharing a `series` value regardless of date gaps, so each Explore line interpolates straight through unlogged days. The dashboard chart stays honest; the new screen fabricates data. All existing tests pass, because `ChartSeriesGrouping` is only wired into `InsightTrendChart`.

**Why it happens:**
Multi-series charts need the `series:` dimension for legend/color grouping, and the per-run gap-break trick *also* uses `series:` — the two uses collide. Developers resolve the collision in favor of the legend (one series value per data series) and lose the gap breaks.

**How to avoid:**
- Compose the two: `series: .value("Run", "\(seriesID)-\(run.id)")` — distinct per (data-series × contiguous-run), with `.foregroundStyle(by: .value("Series", seriesName))` carrying the legend/color grouping independently of the line-break discriminator.
- Generalize `ChartSeriesGrouping` (or wrap it) to run **per series** over the heterogeneous Explore data; keep it pure Foundation so the multi-series gap guarantee is provable in unit tests without rendering, exactly like v1.0.
- Acceptance criterion for the Explore phase: a seeded fixture with a deliberate mid-window gap in each series renders broken lines (assert via the pure grouping output; verify visually via the existing `/tmp/e2e` screenshot checkpoint).

**Warning signs:**
- A new chart view in `Features/Explore/` contains `LineMark` but no import/reference of `ChartSeriesGrouping`.
- `series: .value(...)` keyed only by series name.
- Screenshot of sparse seeded data shows continuous lines.

**Phase to address:**
Explore overlay phase — make "per-series gap runs" an explicit plan task, not an assumed carry-over.

---

### Pitfall 3: New Explore/scatter chrome copy ships outside the ClaimsPolicy audit (silent scan gap + substring landmines)  **[INVARIANT-BREAKING]**

**What goes wrong:**
Two failure modes, one root cause:
1. **Scan gap:** v1.0's copy audits work per-surface — each copy enum (`InsightsCopy`, `ReportCopy`, `FlareCopy`, …) exposes `auditableStrings`, and `CopyAuditTests` registers each surface explicitly. A new `ExploreCopy` that nobody registers is **never scanned** — forbidden verbs ("trigger", "causes", "will", "detect", "treat"), gendered tokens, numeric-confidence patterns all sail through. Tests stay green; the invariant is dead on the new screen.
2. **Substring landmines:** the scans are case-insensitive **substring** scans, and Explore vocabulary is unusually collision-rich against the gendered token `"her"`: **"together"** ("view your series **together**"), **"whether"**, **"other"** (note v1.0 deliberately labels `FactorKind.other` as "Something else"), **"higher"/"highest"** (the documented `"her" ⊂ "higher"` trap — and chart copy *wants* to say "higher"), **"weather"**. Against `forbiddenVerbs`: **"treatment"** ⊃ "treat" (plausible near the Medication series), **"detected"** ⊃ "detect". If the imperative scan is extended to Explore copy: **"because"** ⊃ "use".

**Why it happens:**
The enforcement is opt-in per copy surface, and the collision set is non-obvious — "together" reads completely innocent until the substring scan flags it (or worse, doesn't, because the surface was never registered).

**How to avoid:**
- Make "create `ExploreCopy` with `auditableStrings` + register it in `CopyAuditTests` under **all applicable scans** (forbidden verbs, gendered tokens, numeric confidence — and decide explicitly whether imperative/alarmist apply)" a named task in the *first* Explore plan, before any copy is written.
- Curate Explore vocabulary up front against the known collisions: prefer "in one view"/"combined"/"side by side" over "together"; "if"/"when" over "whether"; "more"/"greater" over "higher"; "another factor" over "other"; never "treatment"/"trigger foods".
- Scatter-specific copy must stay in `allowedFraming` territory: "possible association", never "relationship between X and Y *shows*…", never correlation coefficients (see Pitfall 5).

**Warning signs:**
- A PR adds user-facing strings in `Features/Explore/` views directly (string literals in view bodies) instead of a copy enum.
- `CopyAuditTests.swift` has no `allExploreCopy` section.
- Draft copy contains "together", "whether", "higher", "other", "treatment".

**Phase to address:**
Explore overlay phase, first plan — the copy enum + audit registration is scaffolding, created before features. Same for scatter copy.

---

### Pitfall 4: Mixed-scale overlay normalizes silently (distorted shapes, phantom percentages, fake dual axis)

**What goes wrong:**
The overlay must combine 0–10 severity, sleep hours (~0–12), and binary tags on one y-axis. Swift Charts has **no secondary y-axis** (confirmed limitation; community workarounds are chart-stacking hacks). The tempting fix is min-max normalization of every series to 0–1 over the visible window. Three distortions follow:
- **Window-dependent shape:** per-window min-max rescaling means the *same* series visibly changes shape when the user switches 30d → 90d (a flat sleep series stretched to full height looks volatile); amplitude comparisons between series become meaningless but look meaningful.
- **Phantom percentages:** a normalized axis labeled 0–100% puts **percentages into the UI** — colliding with the locked "no numeric confidence/percentages" posture and inviting users to read "stress is at 80%".
- **Fake dual axis:** overlaying two `Chart`s with different `chartYScale` domains to simulate dual axes invents a visual correlation that is an artifact of two arbitrary scale choices — a classic dishonest-vis pattern, fatal in an app whose brand is honesty.

**Why it happens:**
"One overlay plot" reads as "one shared y-axis," and normalization is the only way to force heterogeneous units onto it. The distortion is invisible to the developer who only tests one window size with dense data.

**How to avoid:**
- Prefer **aligned small multiples**: stacked per-series mini-charts sharing one x-axis (and one shared scrub line), each with a **fixed honest domain per unit type** — severity locked 0…10 (matching `SeverityControl` and the dashboard), sleep locked to a fixed hour domain, binary tags as an occurrence lane (Pitfall 6). This satisfies "combine multiple series in one plot on a shared time axis" without a shared y-scale lie. Swift Charts handles shared-x stacking + one shared selection state well.
- If a true single-panel overlay is kept as an option, make the transformation **explicit and labeled in-plot** (e.g., per-series axis hidden, "scaled to fit — compare timing, not heights" caption), never show a numeric normalized axis, and never use percentages.
- Fixed domains per unit type, never per visible window — same rationale as v1.0's fixed 0…10 ("windows are visually comparable").

**Warning signs:**
- Any `(value - min) / (max - min)` computed over `points.filter { xDomain.contains($0.date) }`.
- A y-axis labeled with %, or with no label at all on a multi-unit overlay.
- Two `Chart` views in a `ZStack`.

**Phase to address:**
Explore overlay phase — the small-multiples vs. single-panel decision is the phase's central design decision; research/decide it before building.

---

### Pitfall 5: Scatter view drifts into statistical claims (trend line, r-values) and overplots discrete 0–10 data

**What goes wrong:**
Two distinct failures on the scatter screen:
- **Statistical-claim drift:** the "classic correlation look" begs for a fitted trend line, and a trend line begs for "r = 0.62" — which violates *two* locked invariants at once (no numeric confidence/percentages/p-values; descriptive-not-causal framing). Even the axis arrangement carries an implicit claim: X = "factor", Y = "symptom" reads as cause → effect.
- **Overplotting:** both axes are frequently discrete 0–10 integers → only 121 possible positions. With 90 logged days, identical day-pairs stack **exactly** on the same pixel; a default `PointMark` scatter shows maybe 25 visible dots for 90 days, and the visual density is a lie (the strongest pattern — many days at (7,2) — is invisible). The standard fix, random jitter, is *itself* an honesty problem here: it fabricates non-integer values the user never logged.

**Why it happens:**
Every scatter tutorial adds a regression line; discreteness of severity data is unusual (most scatter examples use continuous data) so overplotting surprises late, with real dense data.

**How to avoid:**
- **No fitted line, no correlation coefficient, no shaded confidence band — by acceptance criterion.** The scatter stays descriptive: dots + the existing qualitative correlation language ("possible association", reuse the gated correlation engine's qualitative tiers if any annotation is wanted).
- Aggregate identical pairs honestly instead of jittering: one mark per occupied (x, y) cell with **size and/or opacity encoding the day count** (`symbolSize(by: .value("Days", count))`), and the tap/selection tooltip reading "4 days: severity 7, stress 2 of 10" — count is a factual tally, not a statistic, so it's invariant-safe. (Verify the exact tooltip phrasing against ClaimsPolicy like all chrome copy.)
- Pre-aggregate in a **pure helper** (day-pairs → cells with counts), unit-tested: only days where *both* factors were logged become points (a day with one factor missing is absent, never plotted at 0 — Pitfall 1's invariant again, on the pairing logic).
- Neutral axis framing: user picks both axes freely; copy never says X "affects" Y.

**Warning signs:**
- Any linear-regression math in the scatter view model; any string containing "r =", "%", "p ".
- Scatter of a 90-day dense fixture shows < ~40 % as many dots as days, with no size/opacity variation.
- Jitter/random offset code anywhere near the scatter.
- Pairing code that substitutes 0 (or last-known value) when one factor is missing on a day.

**Phase to address:**
Scatter phase — aggregation helper + no-stats acceptance criteria defined in the plan; ClaimsPolicy registration per Pitfall 3.

---

### Pitfall 6: Binary tags (food tags, bleeding) drawn as continuous 0/1 lines

**What goes wrong:**
Food tags and similar presence/absence data, fed through the same LineMark pipeline as severity, render as a line bouncing between 0 and 1 — implying a continuous quantity that "drops to zero" on non-tag days. Worse, on unlogged days the absence-vs-false distinction collapses: "didn't log food" and "logged food, no dairy tag" both read as 0. That's a double honesty violation: fake continuity *and* absence-as-zero.

**Why it happens:**
A generic `SeriesData { date, value: Double }` model is the easy unification for the Explore picker; binary data gets coerced to 0/1 Doubles and inherits the line renderer.

**How to avoid:**
- Model series **kind** in the Explore data layer (quantitative 0–10 / quantitative hours / binary-occurrence), and give binary series a dedicated renderer: an occurrence lane of `PointMark`/`RectangleMark` ticks on days where the tag was *present*, no mark otherwise — with the text summary distinguishing "no entry" days from "logged, tag absent" days where the data supports it.
- The series picker groups by kind so users understand what each lane is.
- Selection tooltip for a binary series says "dairy: tagged" / "dairy: not tagged" / "not logged" — three states, never 1/0.

**Warning signs:**
- `Double(tagPresent ? 1 : 0)` anywhere in Explore data mapping.
- A y-axis showing 0…1 for a tag series.
- Tooltip showing "dairy: 0".

**Phase to address:**
Explore overlay phase — the series-kind model is part of the Explore data layer design, decided with Pitfall 4's layout decision.

---

### Pitfall 7: Scrub gesture fights the dashboard `List` scroll (and iOS 18+ regressions make it worse)

**What goes wrong:**
The v1.0 dashboard renders trend charts inside a grouped `List` (`InsightsView.swift:47`). A horizontal scrub drag and the List's vertical scroll compete: depending on OS version, either the scrub never receives the drag (List wins), or the chart's gesture blocks vertical scrolling when the user touches a chart first ("my dashboard won't scroll past the charts"). This is a moving target: developer-forum threads document iOS 18 breaking tap+scroll coexistence that worked on iOS 17, and iOS 26 betas breaking vertical scroll initiated on gesture-bearing charts. Anything tuned on one OS version can regress on the next.

**Why it happens:**
`chartXSelection` installs its own gesture; SwiftUI gesture arbitration between a chart's drag and an enclosing scroll container is undocumented and has changed across iOS 17 → 18 → 26 betas. Devs test on one simulator version and ship.

**How to avoid:**
- **Dashboard (in-List) charts: tap/touch-and-hold to select, not free drag-scrub.** `chartXSelection`'s built-in behavior (tap sets selection; vertical movement cedes to scroll) is the safest in a List; avoid adding custom `DragGesture`/`simultaneousGesture` overlays there entirely.
- **Explore screen: lay out so the chart isn't inside a scrolling container** (fixed chart region; pickers/legend scroll separately, or chart pinned above a scrolling config area). Free scrubbing is reserved for this screen.
- Manually verify scroll-vs-scrub on every OS version in the support matrix (and at each new SDK), as an explicit human-verify checkpoint like v1.0's chart-quality checkpoint.
- Don't fight arbitration with `highPriorityGesture` on the dashboard — it "fixes" scrub by breaking scrolling, the worse trade.

**Warning signs:**
- A `DragGesture` or `simultaneousGesture` modifier on the in-List dashboard chart.
- QA note "Insights screen sticks when I swipe up over a chart".
- Scrub works in the isolated preview but not in the running app (List swallowing the drag).

**Phase to address:**
Scrub/tap phase (dashboard interaction model) + Explore phase (screen layout). Flag for **re-verification at every OS update** in release checklists.

---

### Pitfall 8: Adding selection to `InsightTrendChart` breaks its other consumer — the PDF report

**What goes wrong:**
`InsightTrendChart` has **two consumers**: the live dashboard and `ReportPDFRenderer`, which constructs a *fresh* instance (`inkColor: .black`) and renders it to vector via `@MainActor ImageRenderer` for the doctor PDF. Bolting selection state, lollipop overlays, `chartXSelection`, or gesture modifiers directly onto `InsightTrendChart` means the PDF path silently inherits them: best case dead weight, worst case a stray selection rule mark / overlay chrome baked into the exported PDF, or `ImageRenderer` output changes that invalidate the verified single-ink print rendering. The PDF was human-verified in v1.0; nothing automatically re-checks it.

**Why it happens:**
The obvious place to add interactivity is the existing chart view; the PDF consumer is three files away and only fails visibly when someone exports a report.

**How to avoid:**
- Keep `InsightTrendChart` the pure, render-only chart. Add interactivity **additively** via a wrapper (`InteractiveTrendChart` composing the base chart + selection overlay) or a default-off flag — mirroring exactly how `inkColor` was added in Phase 10 ("byte-identical on screen, NOTHING else about the chart changes").
- Also check the animation seam: selection-state changes must not retrigger `.animation(..., value: points)` chart-wide animation on every scrub frame; keep selection state out of the inputs that drive that animation.
- Add "export a report PDF and eyeball the chart page" to the phase's verification, since the renderer shares the component.

**Warning signs:**
- `@State`/`@Binding` selection properties appearing inside `InsightTrendChart.swift`.
- `ReportPDFRenderer` needing *any* change in the scrub phase diff.
- Lollipop visible in a generated PDF.

**Phase to address:**
Scrub/tap phase — make "PDF renderer untouched + report re-verified" an explicit acceptance criterion.

---

### Pitfall 9: Gesture-driven XCUITests for scrub are nondeterministic — and there's no seam yet

**What goes wrong:**
XCUITest scrub simulation (`press(forDuration:thenDragTo:)`, coordinate drags) is notoriously flaky: coordinate math depends on device size, plot-area insets, and animation timing; off-by-a-few-points drags select a neighboring day; CI simulators drop drag frames. Tests written this way pass locally and flake forever in CI — undermining the v1.0 test culture (deterministic a11y-ids only, seeded fixtures, `waitForExistence`). Worse, the *honesty-critical* assertions (gap selection shows nothing; tooltip never shows phantom values) end up living only in flaky gesture tests, so they get skipped or deleted.

**Why it happens:**
The interaction is inherently positional, and the team's existing E2E vocabulary (ids + taps) doesn't cover continuous gestures, so devs reach for raw coordinates.

**How to avoid:**
- **Put the honesty logic in pure units, not E2E:** the selection-to-logged-day mapper (Pitfall 1), per-series tooltip resolution, and scatter aggregation are all pure helpers — unit-test the invariants there exhaustively.
- **Add a selection seam for E2E**, following the established `-uitest-*` pattern: e.g., a DEBUG-gated `-uitest-select-day <ISO date>` launch argument (or a hidden test-only control) that sets the selection state programmatically; the E2E then asserts the *rendered result* via stable a11y ids (`explore.tooltip`, `insights.chart.selection`) and `accessibilityValue` — "selection on 2026-06-01 shows severity 6 of 10", "selection on a gap day shows no tooltip" — with zero gestures.
- Keep at most **one** smoke-level real-gesture test (single tap at plot center on a dense fixture), and even that asserts via a11y id, never pixel position.
- Keep injecting `asOf` (the chart already takes it) so window domains are deterministic under seeds.

**Warning signs:**
- `XCUICoordinate`, `withVelocity`, or `normalizedOffset` math in new UI tests.
- A retry loop or `sleep` around a drag in a test.
- Honesty assertions (gap → no tooltip) existing *only* in UI tests.

**Phase to address:**
Scrub/tap phase establishes the seam + a11y-id contract; Explore and scatter phases reuse it. Seam must be DEBUG/`-uitest`-gated like the existing reset/seed args.

---

### Pitfall 10: Explore overlay becomes unreadable / inaccessible at full power (too many series, color-only, no VoiceOver path)

**What goes wrong:**
"Pick multiple logged series" with no cap yields 6+ overlaid lines: indistinguishable hues (especially for the ~5% of users with color-vision deficiency), an unscannable legend of lay-hostile labels, and — because v1.0's per-mark accessibility model (`accessibilityLabel`/`accessibilityValue` per point) multiplies across series — a VoiceOver swipe-order soup of hundreds of elements. The scrub interaction itself has no VoiceOver equivalent: a VoiceOver user can't drag-scrub, so "read the exact day + value" silently becomes sighted-only, violating the locked "charts never the sole carrier of meaning" invariant.

**Why it happens:**
The feature spec says "any logged series"; nobody writes the cap. Accessibility of *interactions* (vs. static charts, which v1.0 solved) is a separate problem that the existing per-mark labels don't cover.

**How to avoid:**
- **Cap concurrent series (3–4)** in the picker, with copy explaining why ("up to 4 at once so the chart stays readable" — ClaimsPolicy-audited).
- Differentiate series by **color + symbol shape + direct/legend label** (`.symbol(by:)` alongside `foregroundStyleScale`) — color never the sole signal, per the locked invariant; verify in a grayscale screenshot.
- VoiceOver path: keep the per-screen **text summary** pattern (extend `InsightsCopy.chartSummary`-style summaries to Explore), rely on Swift Charts' Audio Graph for series reading, and expose the selected day's values as an accessible element (`accessibilityValue` on the tooltip view) plus an accessible alternative to scrubbing (e.g., adjustable element / day stepper) so the "exact day + value" capability isn't gesture-only.
- Reduce Motion: selection rule-mark/tooltip transitions follow the existing `reduceMotion` pattern.

**Warning signs:**
- Series picker UI with no selection limit.
- Legend distinguishable only by hue (check a grayscale screenshot).
- No way to reach the tooltip's content via VoiceOver in an accessibility audit pass.

**Phase to address:**
Explore overlay phase (cap + shape encoding from the first plan); accessibility-of-selection belongs to the scrub/tap phase and carries forward.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Add selection state directly inside `InsightTrendChart` | One file, fast | PDF renderer inherits interaction chrome; verified report rendering invalidated (Pitfall 8) | Never — wrap instead |
| Generic `{date, Double}` series model for all Explore data | One model, simple picker | Binary tags become fake continuous lines; absence/false collapse (Pitfall 6) | Never for binary kinds |
| Min-max normalize per visible window to share one y-axis | Single-panel overlay "works" | Shapes mutate across windows; phantom % axis; dishonest amplitude comparison (Pitfall 4) | Never silently; only with explicit in-plot labeling and no numeric normalized axis |
| Nearest-point snapping without day-equality check | Tooltip always shows something | Phantom values on gap days — invariant violation (Pitfall 1) | Never |
| Gesture-coordinate XCUITests for scrub | Tests the "real thing" | Permanent CI flake; honesty assertions rot (Pitfall 9) | One smoke test max; all invariants in units + seam-driven E2E |
| Skip `xcodegen generate` docs/check for the new `Features/Explore/` files | — | New files not in the project; builds break confusingly for the next session | Never — every plan adding files ends with `xcodegen generate` |
| Reuse dashboard's `InsightsCopy` for Explore strings | No new enum | Explore copy entangled with dashboard scans; per-surface invariant assertions (e.g., "possible association" presence) muddied | Never — per-surface enums are the established pattern |

## Integration Gotchas

Connections to the existing v1.0 system (the "external services" of this milestone).

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `InsightTrendChart` (shared with PDF) | Modify in place for selection | Additive wrapper/flag, PDF path untouched, report re-verified (Pitfall 8) |
| `ChartSeriesGrouping` | Not reused in Explore; or reused with `series:` keyed only by series name | Per-(series × run) `series:` values + `.foregroundStyle(by:)` for legend (Pitfall 2) |
| `ClaimsPolicy` / `CopyAuditTests` | New copy never registered; collision words ("together", "whether", "higher", "other", "treatment") | `ExploreCopy.auditableStrings` + explicit registration under each applicable scan, copy curated against the collision list (Pitfall 3) |
| `-uitest-*` launch-arg harness | Raw gesture E2E; or selection seam compiled into release | DEBUG-gated `-uitest-select-day` style seam + a11y-id assertions, mirroring `-uitest-seed-insights` (Pitfall 9) |
| XcodeGen (`project.yml`) | New Explore feature folder/files added without regenerating | `xcodegen generate` after any file add; keep it in plan checklists |
| Swift 6 strict concurrency | Selection state / chart proxies passed across actor boundaries; new helpers made `@MainActor` unnecessarily | Keep new mappers/aggregators pure + `Sendable` value types (the `ChartSeriesGrouping` pattern); UI state stays `@State` in the view |
| Correlation engine / honesty gate | Scatter screen computes its own stats or bypasses the gate's qualitative framing | Scatter is descriptive-only; any association annotation reuses the gated engine's qualitative output, never new numbers (Pitfall 5) |
| Existing dashboard a11y contract (`insights.card.*` ids) | Selection overlay restructures the accessibility tree, breaking `InsightsDashboardUITests` lookups | Add new ids additively (`insights.chart.selection`, `explore.*`); run the existing dashboard E2E suite as a regression gate in the scrub phase |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Selection state invalidates the whole `List`/screen on every scrub frame | Scrub feels sticky; Instruments shows full dashboard re-render per pixel of drag | Keep selection `@State` local to the chart wrapper; derive tooltip content cheaply; never store selection in a shared `Observable` the List observes | Immediately noticeable with 2–3 cards on screen |
| Recomputing `ChartSeriesGrouping.runs` / Explore aggregation in `body` per frame | CPU spike while scrubbing; battery drain | Compute runs/aggregates once per data change (cached property / `onChange`), not in `body`; they're pure, so memoize trivially | ~4 series × 90 days with selection active |
| Per-point `ForEach` + per-mark modifiers at Explore scale | Chart build time grows; selection lag (community-reported `chartXSelection` slowness scales with mark count) | Keep marks lean on Explore (per-mark a11y labels are fine at ≤ 4 × 120 points; avoid additional per-point overlays); consider `LinePlot`/`PointPlot` (iOS 18+) only if measured | Hundreds-to-thousands of marks; 4 series × all-time windows |
| Animating the chart on selection change | Lollipop "swims"; dropped frames | Scope `.animation(..., value: points)` to data changes only; selection rule mark moves without implicit animation (and respects Reduce Motion) | Any scrub on iOS hardware older than ~2 generations |
| All-time window with dense data + selection | Long-time users (1–2 yrs ≈ 400–700 points/series) see chart setup + scrub lag | Day-level data is bounded (365 pts/yr/series) — fine for v1.1; if all-time × 4 series lags, decimate *visually only* (never the tooltip's data lookup) | ≥ ~2k total marks on screen |

## Security Mistakes

Domain-specific (health-data + compliance posture) rather than classic appsec.

| Mistake | Risk | Prevention |
|---------|------|------------|
| `-uitest-select-day` / new seed seams compiled into Release | Hidden state-manipulation surface in a shipping health app | Gate all new seams exactly like existing `-uitest-reset` (DEBUG-only), and extend any existing source-guard tests to the new args |
| Scatter/Explore surfaces leak numeric statistics (r, %, p) into UI or report | Crosses the wellness-vs-medical-device positioning line the whole app is built on | No-stats acceptance criteria (Pitfall 5) + numeric-confidence scan registered on `ExploreCopy` |
| New screens' screenshots in `/tmp/e2e` from real-device runs containing personal data | Health data at rest outside the app sandbox | Keep the screenshot helper simulator/seed-fixture-only (current pattern); never run it against personal installs |
| Explore tooltips rendered into any share/export path without the report's framing | Health data shared without "self-tracked, not diagnostic" labeling | v1.1 adds no new export; if screenshots-to-share ever lands, route through the report framing rules |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Unlimited series in the overlay | Spaghetti chart; user concludes "insights are noise" | Cap at 3–4 with friendly copy (Pitfall 10) |
| Color-only series identity | CVD users can't tell sleep from stress | Color + symbol shape + labeled legend; grayscale screenshot check |
| Tooltip shows values during gaps | User believes they logged data they didn't; trust in "honest gaps" erodes | Exact logged-day mapping; explicit "no entry" state (Pitfall 1) |
| Scatter axes labeled so X reads as cause | Lay users read "stress causes pain" off the plot | Neutral picker ("compare two factors"), association-only copy, no trend line (Pitfall 5) |
| Lay users can't decode a normalized shared axis | "Why is sleep at 7?" confusion; misread magnitudes | Small multiples with native units per lane (Pitfall 4) |
| Scrub-only value reading | VoiceOver / motor-impaired users locked out of the headline v1.1 feature | Adjustable-element or stepper alternative + audio graph + text summary (Pitfall 10) |
| Dashboard scroll trapped by chart gestures | "The app is broken" — worst possible perception for a polish milestone | Tap-to-select on dashboard; free scrub only on the dedicated Explore layout (Pitfall 7) |
| Empty/sparse Explore selections (e.g., two factors never logged on the same day → empty scatter) | Blank screen reads as a bug | Honest empty states reusing the v1.0 "not enough data" pattern, with the day-pair requirement explained |

## "Looks Done But Isn't" Checklist

- [ ] **Scrub tooltip:** works on dense data — verify it shows *nothing* (or "no entry") when selecting inside a gap, and shows `0` (not nothing) on a logged severity-0 day.
- [ ] **Explore overlay:** lines look right on the dense demo seed — verify gap breaks **per series** with a sparse fixture, and that a binary tag series renders as occurrence marks, not a 0/1 line.
- [ ] **ExploreCopy:** strings read fine — verify the enum is registered in `CopyAuditTests` (the scan that doesn't run is the one that fails you) and grep drafts for "together/whether/higher/other/treatment".
- [ ] **Scatter:** dots render — verify stacked identical day-pairs are count-encoded (seed 10 days at the same (x, y) and confirm one sized mark, tooltip says "10 days"), and that days missing one factor are absent.
- [ ] **Dashboard regression:** scrub added — verify the existing `InsightsDashboardUITests` suite still passes unchanged, the List still scrolls when the swipe starts on a chart, and a freshly exported PDF report chart is pixel-equivalent to v1.0.
- [ ] **Accessibility:** per-mark labels exist — verify a VoiceOver user can actually obtain "exact day + value" without the drag gesture, and series remain distinguishable in grayscale.
- [ ] **Project file:** builds locally — verify `xcodegen generate` was run after adding `Features/Explore/` files and the generated project is committed per repo convention.
- [ ] **Determinism:** new E2Es pass once — verify they use seeds + `asOf` injection + a11y ids only (no coordinates, no sleeps) and pass 10× consecutively.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Phantom tooltip values shipped (P1) | MEDIUM | Insert the pure day-mapper behind the existing tooltip; add gap unit tests; no data migration needed |
| Explore lines connect across gaps (P2) | LOW–MEDIUM | Re-key `series:` to (series × run) using `ChartSeriesGrouping`; visual-only fix, no model change |
| Unregistered copy surface discovered (P3) | LOW (if caught pre-release) | Register `auditableStrings`, fix flagged strings; HIGH if violating copy already shipped (reputational, App Store update) |
| Normalized-overlay confusion shipped (P4) | HIGH | Layout redesign to small multiples — a UI rework, which is why the decision belongs in phase research, not recovery |
| Scatter with trend line / stats shipped (P5) | MEDIUM | Remove stats, add count-encoding; messaging cost: users may ask where "the line" went |
| Dashboard scroll broken by gestures (P7) | LOW–MEDIUM | Revert to tap-only on dashboard; ship hotfix; add the OS-matrix manual check |
| PDF report chart corrupted (P8) | LOW (if caught by re-verify) / MEDIUM (if shipped) | Split interactive wrapper from base chart; regenerate + re-verify report |
| Flaky scrub E2Es in CI (P9) | LOW | Replace with seam-driven tests; keep one smoke gesture test or none |

## Pitfall-to-Phase Mapping

Suggested phase shapes for the roadmapper: **A = scrub/tap on existing trend charts**, **B = Explore overlay screen**, **C = scatter view**. A before B/C (the selection mapper, a11y-of-selection pattern, and E2E seam built in A are reused by both).

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| P1 phantom selection values | A (mapper is foundational) | Unit tests: gap → nil, severity-0 day → 0; seam-driven E2E: gap day shows no tooltip |
| P2 Explore gap-connecting | B | Unit tests on per-series runs; sparse-seed screenshot shows broken lines per series |
| P3 copy scan gap + collisions | B (and C for scatter copy) | `CopyAuditTests` registers `ExploreCopy` (+ scatter copy) and passes; grep for collision words in review |
| P4 silent normalization / fake dual axis | B (design decision up front — flag for phase research) | Design decision recorded; no `%` axis; window-switch screenshot shows stable series shapes |
| P5 scatter stats drift + overplotting | C | No regression/“r =”/% in code or copy; stacked-pair fixture renders count-encoded mark with honest tooltip |
| P6 binary tags as lines | B | Series-kind model unit tests; tag series renders occurrence lane in screenshot |
| P7 scroll/scrub conflict | A (dashboard model), B (Explore layout) | Manual OS-matrix check: List scrolls from chart touch; scrub works on Explore; existing dashboard E2E green |
| P8 PDF/report breakage | A | `ReportPDFRenderer` untouched in diff; exported PDF visually re-verified |
| P9 flaky gesture E2E | A (seam + id contract) | New E2Es contain no coordinate gestures; 10× consecutive green runs |
| P10 unreadable/inaccessible overlay | A (selection a11y), B (cap + shapes) | Series cap enforced in picker; grayscale screenshot check; VoiceOver walkthrough reaches day + value without drag |

## Sources

- Codebase (HIGH confidence, read 2026-06-10): `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Features/Insights/InsightTrendChart.swift` (gap-break + per-mark a11y + `inkColor` PDF seam + animation pattern), `ChartSeriesGrouping.swift` (pure run-grouping invariant), `InsightsView.swift` (charts inside a grouped `List`), `Content/ClaimsPolicy.swift` (substring-scan semantics, documented `"her" ⊂ "higher"` / `"use" ⊂ "because"` traps), `Tests/EndoAppTests/CopyAuditTests.swift` (per-surface `auditableStrings` registration model), `Features/Report/ReportPDFRenderer.swift` (fresh-chart `ImageRenderer` reuse), `Tests/EndoAppUITests/InsightsDashboardUITests.swift` (`-uitest-reset`/`-uitest-seed-insights` seams, a11y-id-only assertion contract), `Domain/FactorKind.swift` (series kinds, "Something else" labeling).
- Swift Charts selection performance with large mark counts (MEDIUM): [Apple Developer Forums — Swift Charts performance with many data points](https://developer.apple.com/forums/thread/740314), [poor performance thread](https://developer.apple.com/forums/thread/735687), [slow navigation thread](https://developer.apple.com/forums/thread/710597), [community report on 2000+ point scrubbing workarounds](https://www.threads.com/@mykola.harmash/post/DHv9vOVypqg/video-swift-charts-are-great-but-performance-is-definitely-an-issue-took-some-hoop-jum).
- Chart gesture vs. scroll-container conflicts and OS-version regressions (MEDIUM): [Apple Developer Forums — iOS 26 beta scroll/gesture regression on charts](https://developer.apple.com/forums/thread/794212), [Daniel Saidi — complex gestures in a SwiftUI ScrollView](https://danielsaidi.com/blog/2022/11/16/using-complex-gestures-in-a-scroll-view), [WWDC23 — Explore pie charts and interactivity in Swift Charts](https://developer.apple.com/videos/play/wwdc2023/10037/).
- Selection / ChartProxy coordinate-space patterns (MEDIUM): [Swift with Majid — Mastering charts: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/), [Mastering charts: Interactions](https://swiftwithmajid.com/2023/02/06/mastering-charts-in-swiftui-interactions/), [Apple docs — ChartProxy](https://developer.apple.com/documentation/charts/chartproxy), [plotFrame](https://developer.apple.com/documentation/charts/chartproxy/plotframe).
- No native secondary y-axis in Swift Charts; workaround landscape (MEDIUM): [Apple Developer Forums — secondary Y axis](https://developer.apple.com/forums/thread/742659), [Paul Peelen — multi data-type chart](https://paulpeelen.com/MultiTypeChart), [Apple docs — Customizing axes](https://developer.apple.com/documentation/charts/customizing-axes-in-swift-charts), [Hacking with Swift forum — Y axis scale](https://www.hackingwithswift.com/forums/swiftui/swiftui-charts-y-axis-scale/26935).
- Visualization-honesty pitfalls (overplotting of discrete data, jitter fabrication, dual-axis distortion, normalization artifacts): established visualization practice applied to this app's locked invariants (MEDIUM — cross-checked reasoning, no single citable source).

---
*Pitfalls research for: Endo-App v1.1 Interactive Charts & Explore*
*Researched: 2026-06-10*
