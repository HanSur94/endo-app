# Phase 10: Doctor-Ready PDF Report - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; recommendations auto-accepted.

<domain>
## Phase Boundary

Generate a **clinician-credible, on-device vector PDF** for a user-chosen date range that **labels each data point's source** (self-reported vs Apple Health), carries clear **"self-tracked, not diagnostic"** framing, and is **shared via the system share sheet with no silent upload**.

In scope: RPRT-01/02/03. A date-range report-builder UI, an on-device PDF renderer (PDFKit + Core Graphics, zero third-party deps) that reuses the existing engine reads + chart views, per-source labeling via the Phase-9 provenance seam, and explicit-action share.

Out of scope: any cloud/network export or upload; emailing/sending on the user's behalf; clinical interpretation/diagnosis; editing the report. Insight/engine logic is unchanged — Phase 10 only RENDERS existing data for a date range.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — PDF generation engine
- **D-01:** **PDFKit + Core Graphics** (`UIGraphicsPDFRenderer` / `CGContext` PDF) — vector, on-device, **ZERO third-party deps** (privacy-first; matches CLAUDE.md "prefer the CG/PDF path over raster"). NO TPPDF.
- **D-02:** Embed charts as **vector** via SwiftUI **`ImageRenderer`** rendered into the PDF `CGContext` (not raster snapshots), reusing the Phase-8 `InsightTrendChart` / chart views where useful.
- **D-03:** Standard page size (US-Letter or A4), multi-page with auto-pagination + page numbers. The entire report path makes **no network calls** (verifiable: no URLSession/upload in the report module).

### Area 2 — Report content & structure
- **D-04:** Section order: (1) **Header** — app name, generated-on date, selected date range, and a prominent **"self-tracked, not diagnostic"** disclaimer (RPRT-02); (2) **Summary/coverage** for the range; (3) **Symptoms & pain** (trend table + embedded chart); (4) **Cycle & bleeding** (honest cycle summary — no phantom cycle, suppressed/irregular handled); (5) **Lifestyle factors** (sleep/activity/food/stress/mood); (6) **Insights** (the gated Trends / cycle-phase / associations for the range, honesty preserved); (7) **Source legend**; (8) **Footer** — disclaimer + page numbers.
- **D-05:** Reuse the existing Repository reads + `EndoInsights` engine **scoped to the date range**; all user-facing copy routes through **`ClaimsPolicy`** (non-causal, not diagnostic). Honesty preserved: sparse/suppressed data shows "not enough data," never fabricated; **absence ≠ 0**.

### Area 3 — Date range selection UX
- **D-06:** A **report-builder screen** reached from **Settings** (an "Export report / Doctor report" row): presets (**Last 30 days**, **Last 90 days**, **Custom range** via two DatePickers) → **"Generate PDF"**.
- **D-07:** After generation, present a **PDFKit `PDFView` preview** with a **Share** action.

### Area 4 — Source labeling & share/privacy
- **D-08:** Each data point/section is labeled **"Self-reported"** or **"From Apple Health"** using the **Phase-9 `daySnapshotSources` provenance** + `HealthSourceBadge`-style tags; mixed-source clearly indicated; a **legend** explains the labels (RPRT-02).
- **D-09:** Share via SwiftUI **`ShareLink`** / `UIActivityViewController` with the **local PDF file URL** (temp/Documents). **NO automatic cloud upload** — the PDF is shared only on explicit user action (RPRT-03). No analytics/network in the report path.

### Claude's Discretion
- Exact PDF layout metrics, fonts/typography, table vs chart per section, the ImageRenderer→PDF bridging details, the report module's file structure, and where (if anywhere besides Settings) the entry point also appears are left to research/planning + the UI-SPEC.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Engine + Repository reads** (`EndoInsights` + `Repository.daySnapshots()`/`factorDays`/`bleedingDays`/cycle reads) — scope to the date range for report data.
- **Chart views** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift`, `InsightCardView.swift` — render into the PDF via `ImageRenderer`.
- **Provenance seam** (Phase 9) `Repository.daySnapshotSources(on:)` + `Sources/EndoApp/Features/Health/HealthSourceBadge.swift` — drives the per-source labels (RPRT-02).
- **Disclaimer + copy choke-point** — the existing `DisclaimerBanner` (Insights) + `ClaimsPolicy` (forbidden verbs / gendered tokens / non-causal) gate all report copy.
- **`CycleSummaryView`** honesty pattern (no phantom cycle, suppressed/not-enough-data) — mirror in the report's cycle section.
- **Settings sections pattern** `Sources/EndoApp/Features/Settings/SettingsView.swift` — add the report entry row.

### Established Patterns
- Privacy-first, zero/near-zero third-party deps; on-device only; ClaimsPolicy choke-point for all copy; absence≠0 honesty; consent-gated health UI.

### Integration Points
- New report module (app-side; NOT in the Foundation-only `EndoInsights` package — PDFKit/UIKit/SwiftUI live in the app).
- Settings entry → report-builder → renderer → PDFKit preview → ShareLink.
</code_context>

<specifics>
## Specific Ideas
- "Self-tracked, not diagnostic" framing must be PROMINENT (header) and pass ClaimsPolicy (RPRT-02).
- No silent upload — the PDF is a local file shared only via explicit ShareLink/share-sheet action (RPRT-03).
- Honesty: a sparse/suppressed range must read as "not enough data," never a fabricated normal report.
</specifics>

<deferred>
## Deferred Ideas
- Cloud/email export, scheduled reports, report templates/customization — out of scope for v1.
- Editing or annotating the report — out of scope.
</deferred>

<canonical_refs>
## Canonical References
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 10 reqs: RPRT-01/02/03
- `.planning/ROADMAP.md` — Phase 10 goal + 3 success criteria
- `.planning/phases/09-healthkit-import/09-CONTEXT.md` — the `daySnapshotSources` provenance seam (RPRT-02 source labels)
- `.planning/phases/08-insights-integration-coordinator-cached-insights-dashboard/08-CONTEXT.md` — chart views to reuse via ImageRenderer
- CLAUDE.md — stack notes (PDFKit + ImageRenderer preferred over TPPDF/raster; near-zero deps)
- `Sources/EndoApp/Persistence/Repository.swift`, `Sources/EndoApp/Features/Insights/InsightTrendChart.swift`, `Sources/EndoApp/Features/Settings/SettingsView.swift`
</canonical_refs>
