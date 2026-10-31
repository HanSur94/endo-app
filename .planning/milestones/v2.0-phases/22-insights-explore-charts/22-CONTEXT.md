# Phase 22: Insights Dashboard, Explore & Charts - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md §4/§5; UI direction fixed by the shipped iOS Insights + Explore screens; the engine + chart data-prep are ALREADY PORTED in Phase 17)

<domain>
## Phase Boundary

The Insights dashboard and Explore surfaces give honest, readable, TalkBack-accessible views of everything logged — under the LOCKED chart-honesty rules. Fills the Phase-19 Insights + Explore tabs. Delivers AINS-01..07: the gated insight-card dashboard (trends, cycle-phase, possible-associations, recommendations, flare — plain-language confidence, honest empty states, pinned not-medical-advice disclaimer, non-destructive dismiss), flare surfaced IN-APP ONLY (no notifications/alarms), scrub/tap trend charts (gap days read "not logged", severity-0 is a real 0), Explore overlay lanes + event-marker lanes on a shared time axis, the descriptive-only count-encoded scatter (no fitted line/r-value) reached from a possible-association card, the chart-honesty rules pinned by tests on the data-prep layer, and per-data-point TalkBack overlay semantics. This is the payoff phase — it makes the effort of logging worth it.

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §4 (Phase 6) + §5 (no-flare-notification + chart-honesty invariants); Compose chart guidance in [.planning/research/FEATURES.md](../../research/FEATURES.md) (Canvas a11y, the HIGH-complexity TalkBack overlay) + [.planning/research/PITFALLS.md](../../research/PITFALLS.md) (recomposition storms, @Immutable, Canvas-vs-Vico). Reuse Phases 17–21. Do not re-open. Key decisions:

### Reuse the ported engine + chart data-prep (do NOT rebuild)
- Phase 17 ALREADY ported: `InsightsEngine` (the one public producer behind HonestyGate composing Trends/CyclePhase/Correlation/Recommendations/Flare), all insight types + confidence/window/point, and the chart DATA-PREP layer: `ExploreSeries`/`ExploreSeriesCatalog`, `ExploreSeriesExtraction`, `ScatterPairing`, `SeriesNormalization`, `ChartScrubSelection`. This phase RENDERS them; the honesty rules already live in that data-prep code (gap≠0, no dual-y, no normalization, no fitted line). Phase 18's `SnapshotProjector` + `daySnapshots()` feed the engine from Room (manual + imported). This phase adds an `InsightsCoordinator` (the iOS analog) that runs the engine OFF the main thread (`withContext(Dispatchers.Default)`), fingerprint-caches (CachedInsight, ALREADY an entity), loads cache instantly, feeds dismissed keys (DismissedInsight, ALREADY an entity + repo CRUD).

### Chart rendering (Compose Canvas)
- Render with Compose `Canvas` (the locked decision; Vico is the recorded escape hatch, evaluated mid-phase ONLY with Layout Inspector evidence if Canvas layout cost balloons — do NOT reach for Vico preemptively). Trend line chart with honest gap breaks (a gap day is a BREAK in the line, never a zero, never interpolation), a rule-mark + lollipop tooltip on scrub, a day-change haptic. Explore overlay lanes (aligned per-scale, NO shared/dual y-axis — separate lanes) + event-marker lanes on a shared time axis; multi-series scrub tooltip. The scatter is descriptive-only count-encoded PointMarks — NO fitted line, NO r-value, NO trend line — reached via a "See the days behind this" deep-link from a "Possible association" card.
- CHART-HONESTY RULES (LOCKED, pin by tests on the data-prep layer AND assert in the render layer where applicable): no dual y-axis, no silent normalization, gaps stay gaps (never zeros, never interpolation), scatter has no fitted line/r-value, confidence shown as a plain-language pill (text + SF-Symbol-equivalent icon, NEVER a number, NEVER color alone), suppression hides bleeding/cycle series.
- PERFORMANCE (research pitfall): chart inputs are `@Immutable`/stable holders; scrub state read in the DRAW phase only (not composition) via `drawWithCache`/derived state; avoid recomposition storms. This is what keeps Canvas viable and the Vico escape hatch unnecessary.

### Accessibility (the HIGH-complexity item — first-class requirement)
- Compose Canvas is INVISIBLE to TalkBack and Android has NO Audio Graph / AXChartDescriptor equivalent. So build per-data-point TalkBack overlay SEMANTICS: focusable semantics nodes (one per rendered data point / lane point) reading from the SAME gap-honest ChartScrubSelection resolver as the visual scrub — a gap day announces "not logged", a logged 0 announces a real 0. This is bespoke work (no library shortcut), pinned by a semantics-tree test. Mirror the iOS non-gesture VoiceOver path. Touch/scrub targets ≥48dp; the tooltip and dismiss controls are TalkBack-labeled.

### Dashboard behavior (iOS parity)
- Insights tab: Trends / Cycle-phase / Possible-associations / Recommendations / Flare sections of gated cards; each card a kind-agnostic Bloom card with a plain-language confidence pill; the three honest states (enough data → cards, sparse → "not enough data yet", suppressed → cycle/bleeding sections omitted); a pinned not-medical-advice disclaimer; non-destructive dismiss (writes DismissedInsight, excluded next recompute — never deletes data). Instant cached load + non-blocking recompute; recompute triggers on foreground + save + dismiss. The flare section is placed FIRST (time-sensitivity by POSITION only — NO alarm color/badge/glyph) and surfaces IN-APP ONLY.
- Explore tab: the consent-gated series catalog → overlay lanes + event lanes + the scatter; honest empty/sparse states; suppression hides bleeding.

### No-flare-notification invariant (§5 — carried from Phase 20)
- Flare surfaces IN-APP ONLY. There are deliberately NO flare/alarm notifications. The insights/flare code must import NO notification/alarm API — extend the Phase-20/21 NotificationSeparation/HealthSeparation scan to cover the insights package (the reminders package remains the SOLE notification owner). Add a no-alarm-VISUAL source scan too (the flare card must not use alarm color/badge/glyph — calm by construction).

### Copy discipline
- Every new user-facing string (section headers, card chrome, confidence-pill labels, empty states, disclaimer, Explore/scatter labels, a11y descriptions) registered in CopyAudit in the same change; the insight COPY itself is engine-generated and already audited in `:insights` (InsightsCopy/RecommendationsCopy/FlareCopyText — ForbiddenTerms-clean); the app chrome copy dodges ClaimsPolicy substrings (should/use/her/start/treat/stop/take). Confidence is NEVER a number; framing "informational, not medical advice."

### Claude's Discretion
- Exact Compose package layout under `com.endoapp.app.ui.insights` / `.explore` / `.charts`, the Canvas drawing decomposition, the InsightsCoordinator state-holder shape (plain class vs ViewModel — prefer a plain app-scoped class per ARCHITECTURE.md), the haptic trigger, and the scrub gesture handling — Claude's discretion guided by the iOS screens + PITFALLS.md performance guidance. Keep the honesty rules and the a11y overlay non-negotiable.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- iOS Insights: `Features/Insights/InsightsView.swift`, `InsightCardView.swift`, `InsightsCoordinator.swift`, `InsightTrendChart.swift`, `ScatterDeepLink.swift`; `App/ExploreScatterRouter.swift`.
- iOS Explore: `Features/Explore/ExploreView.swift`, `ExploreLanesView.swift`, `ExploreLaneChart.swift`, `EventLaneChart.swift`, `ScatterPlotView.swift`, `ScatterAxisPicker.swift`, `SeriesPickerView.swift`.
- iOS chart/coordinator behavior: fingerprint cache, Task.detached recompute, foreground/save/dismiss triggers, the `-uitest-seed-insights` seed; the honest line breaks; the plain-language confidence pill; the calm flare card + no-alarm-visual guard.

### Established from Phases 17–21 (reuse — DO NOT rebuild)
- Phase 17 `:insights`: `InsightsEngine` (one public producer, HonestyGate), insight types, and the chart data-prep (ExploreSeries/Extraction/ScatterPairing/Normalization/ChartScrubSelection) with the honesty rules already tested. Phase 18: `SnapshotProjector`/`daySnapshots()`, `CachedInsight` + `DismissedInsight` entities + consent-gated CRUD (replace-all cache, append-only dismissed). Phase 19–20: BloomTheme/components/cards, the 3-tab shell with Insights+Explore placeholders to fill, CopyAudit, the composition root (AppDependencies) + injectable-seam threading. Phase 20/21: the NotificationSeparation/HealthSeparation scans to extend. Android SDK installed; `./gradlew :app:...` builds real; Robolectric+Compose JVM lane.

### Integration points
- Reads logged (Phase 20) + imported (Phase 21) data uniformly via SnapshotProjector → InsightsEngine. Fills the Phase-19 Insights + Explore tabs. Phase 24 (PDF report) reuses this phase's Canvas chart drawing code for the vector PDF chart. The insights package must stay notification-free (§5).

</code_context>

<specifics>
## Specific Ideas

Three signature carry-overs: (1) the chart-honesty rules — gaps are gaps (line breaks, never zeros/interpolation), no dual-y, no normalization, scatter has no fitted line/r-value, confidence is plain-language never a number; (2) the per-data-point TalkBack overlay semantics reading from the SAME gap-honest resolver as the visual scrub (the HIGH-complexity bespoke a11y work — no library shortcut); (3) the calm flare — in-app only, placed first by position, NO alarm color/badge/glyph, and the insights package imports no notification API (extend the separation scan). Reuse the ported engine + data-prep; this phase renders + coordinates + makes accessible.

</specifics>

<deferred>
## Deferred Ideas

- The vector PDF report (reuses this phase's chart drawing) → Phase 24.
- Encrypted export/import → Phase 23.
- Any device-gated visual/haptic/TalkBack-sweep feel → documented manual verification.
- The Canvas→Vico escape hatch is evaluated mid-phase ONLY on Layout Inspector evidence; default is Canvas.
</deferred>
