# Phase 10: Doctor-Ready PDF Report - Research

**Researched:** 2026-06-09
**Domain:** On-device vector PDF generation (PDFKit + Core Graphics + SwiftUI `ImageRenderer`) for a clinician-credible, source-labeled, date-ranged health report — SwiftUI / SwiftData / Swift 6.0 / iOS 18 / Xcode 26.5
**Confidence:** HIGH

## Summary

Phase 10 renders existing logged + imported data into a clinician-credible PDF for a user-chosen date range. Every required framework is first-party Apple and already in the project's adopted stack (CLAUDE.md STACK table rates PDFKit + Core Graphics + `ImageRenderer` at HIGH confidence). There is **no new dependency and no networking** — the codebase contains zero `URLSession`/`URLRequest`/`dataTask` references in `Sources/EndoApp` `[VERIFIED: grep Sources/EndoApp]`, so the "no silent upload" invariant (RPRT-03) is preserved by *not* introducing a network path, and is grep-assertable as a regression guard.

The load-bearing architectural decision is to split the phase into a **pure, testable data-assembly model** (`ReportModel` + section structs, assembled from range-scoped Repository reads, independent of any rendering) and a **thin renderer** (`UIGraphicsPDFRenderer` drawing `NSAttributedString`/CoreText for selectable vector text, embedding charts via `ImageRenderer`'s vector `render { size, ctx in ... }` → `CGContext` path). This makes the honesty contract (absence≠0, no phantom cycle, sparse→"not enough data", source labels) unit-testable without visual diffing, and reduces the renderer's testing burden to a smoke test (N pages, non-empty) plus `PDFDocument.string` text-extraction asserting the disclaimer + source-legend strings are present.

A critical scoping finding: the `EndoInsights.InsightsEngine.insights(from:asOf:dismissed:calendar:)` API is **window-based** (it computes over fixed 7/30/90/all-time windows internally, anchored on an injected `asOf`) — it does **NOT** accept an arbitrary `[startDate, endDate]` range `[VERIFIED: read EndoInsights/Sources/EndoInsights/InsightsEngine.swift]`. The report therefore scopes by (a) **filtering the `[DaySnapshot]` array** to the chosen range before handing it to the engine, and (b) reusing the existing `Repository` reads (`daySnapshots()`, `bleedingDays()`, `factorDays()`, `daySnapshotSources(on:)`, `isSuppressed()`) and filtering their results by day. No engine change is needed (and the engine is LOCKED — D-13/D-16 from prior phases).

**Primary recommendation:** Build a pure `ReportModel`/section-assembly layer (range-scoped, honesty-preserving, source-labeled, copy through a new auditable `ReportCopy` enum), then a thin `UIGraphicsPDFRenderer`-based renderer that draws selectable CoreText vector text and embeds `InsightTrendChart` as vector via `ImageRenderer.render`; preview with PDFKit `PDFView` (UIViewRepresentable); share via SwiftUI `ShareLink` bound to a temp-file `URL`. Test the model directly (honesty + labels), the copy via extended `CopyAuditTests`, and the PDF via a page-count smoke test + `PDFDocument.string` substring assertions.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Area 1 — PDF generation engine**
- **D-01:** PDFKit + Core Graphics (`UIGraphicsPDFRenderer` / `CGContext` PDF) — vector, on-device, ZERO third-party deps (privacy-first; matches CLAUDE.md "prefer the CG/PDF path over raster"). NO TPPDF.
- **D-02:** Embed charts as **vector** via SwiftUI `ImageRenderer` rendered into the PDF `CGContext` (not raster snapshots), reusing the Phase-8 `InsightTrendChart` / chart views where useful.
- **D-03:** Standard page size (US-Letter or A4), multi-page with auto-pagination + page numbers. The entire report path makes **no network calls** (verifiable: no URLSession/upload in the report module).

**Area 2 — Report content & structure**
- **D-04:** Section order: (1) Header — app name, generated-on date, selected date range, and a prominent "self-tracked, not diagnostic" disclaimer (RPRT-02); (2) Summary/coverage for the range; (3) Symptoms & pain (trend table + embedded chart); (4) Cycle & bleeding (honest cycle summary — no phantom cycle, suppressed/irregular handled); (5) Lifestyle factors (sleep/activity/food/stress/mood); (6) Insights (the gated Trends / cycle-phase / associations for the range, honesty preserved); (7) Source legend; (8) Footer — disclaimer + page numbers.
- **D-05:** Reuse the existing Repository reads + `EndoInsights` engine **scoped to the date range**; all user-facing copy routes through `ClaimsPolicy` (non-causal, not diagnostic). Honesty preserved: sparse/suppressed data shows "not enough data," never fabricated; **absence ≠ 0**.

**Area 3 — Date range selection UX**
- **D-06:** A report-builder screen reached from Settings (an "Export report / Doctor report" row): presets (Last 30 days, Last 90 days, Custom range via two DatePickers) → "Generate PDF".
- **D-07:** After generation, present a PDFKit `PDFView` preview with a Share action.

**Area 4 — Source labeling & share/privacy**
- **D-08:** Each data point/section is labeled "Self-reported" or "From Apple Health" using the Phase-9 `daySnapshotSources` provenance + `HealthSourceBadge`-style tags; mixed-source clearly indicated; a legend explains the labels (RPRT-02).
- **D-09:** Share via SwiftUI `ShareLink` / `UIActivityViewController` with the local PDF file URL (temp/Documents). NO automatic cloud upload — the PDF is shared only on explicit user action (RPRT-03). No analytics/network in the report path.

### Claude's Discretion
Exact PDF layout metrics, fonts/typography, table vs chart per section, the ImageRenderer→PDF bridging details, the report module's file structure, and where (if anywhere besides Settings) the entry point also appears are left to research/planning + the UI-SPEC.

### Deferred Ideas (OUT OF SCOPE)
- Cloud/email export, scheduled reports, report templates/customization — out of scope for v1.
- Editing or annotating the report — out of scope.
- Any cloud/network export or upload; emailing/sending on the user's behalf; clinical interpretation/diagnosis; editing the report.
- Insight/engine logic is unchanged — Phase 10 only RENDERS existing data for a date range.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **RPRT-01** | User can generate a doctor-ready PDF report for a chosen date range, produced entirely on-device | `UIGraphicsPDFRenderer` (CoreGraphics, on-device, vector, selectable text) draws a multi-page document; a pure `ReportModel` assembles range-scoped data from the existing `Repository` reads + `EndoInsights` engine (window-based, `asOf`-anchored). See §Architecture Patterns, §Standard Stack, §Pattern 2/3. |
| **RPRT-02** | Report labels each data source (self-reported vs Apple Health) and carries "self-tracked, not diagnostic" framing | Per-field provenance via the Phase-9 `Repository.daySnapshotSources(on:)` → `ValueSource` (`.manual`/`.appleHealth`/`nil`); glyph+word labels (never color); prominent boxed `Disclaimer.text` (single source of truth) on the header; source legend section. All copy through a new auditable `ReportCopy` enum scanned by `CopyAuditTests`/`ClaimsPolicy`. See §Pattern 4, §Common Pitfalls 1/3, §Validation Architecture. |
| **RPRT-03** | User can share/export the PDF via the system share sheet with no automatic cloud upload | SwiftUI `ShareLink(item: fileURL)` (or `UIActivityViewController`) bound to a local temp-file `URL`; the report module imports no `URLSession` (grep-assertable negative invariant — already true for the whole app). Explicit-tap share only. See §Pattern 5, §Security Domain. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Range-scoped data assembly (`ReportModel`) | App / pure value layer | Persistence (Repository reads) | A pure, store-independent model is the unit-testable honesty boundary; mirrors the Phase-5 `daySnapshots()` projection posture. |
| Honesty enforcement (absence≠0, no phantom cycle, sparse→"not enough data") | App / pure value layer (`ReportModel` + reuse `CycleMath`) | — | Same logic as `CycleSummaryView`/`TrendsEngine`; assembled, not re-derived, so it can be asserted without rendering. |
| Source labeling (self-reported vs Apple Health) | Persistence (`daySnapshotSources`) | App / model | Provenance already lives in the Phase-9 seam; the model surfaces it per section/value. |
| Insight selection for the range | EndoInsights package (pure engine) | App (filters snapshots to range) | Engine is LOCKED + window-based; the app filters `[DaySnapshot]` to the range and calls the existing API. |
| PDF rendering (vector text + pagination + page numbers) | App / CoreGraphics (`UIGraphicsPDFRenderer`) | — | CoreGraphics has no SwiftUI semantic styles; the renderer owns fixed-point typography (UI-SPEC Surface 2). |
| Chart→vector embedding | App / SwiftUI `ImageRenderer` (@MainActor) → `CGContext` | Charts (`InsightTrendChart`) | `ImageRenderer` is `@MainActor`; render the existing chart view off the live tree into the PDF context. |
| Builder + preview UI | App / SwiftUI (`Form`, `PDFView` via `UIViewRepresentable`) | — | Matches the existing first-party SwiftUI language (Settings `Form` idiom). |
| Share (no upload) | App / SwiftUI `ShareLink` + temp-file `URL` | — | System share sheet, explicit-action only; no network path introduced. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **PDFKit** (`PDFDocument`, `PDFView`) | iOS 18+ | Render the PDF on-screen (preview) + text-extraction in tests (`PDFDocument.string`) | First-party; the canonical iOS PDF preview/inspect path. CLAUDE.md HIGH. `[CITED: developer.apple.com/documentation/pdfkit]` |
| **Core Graphics** (`UIGraphicsPDFRenderer`, `CGContext`) | iOS 18+ | Generate the multi-page vector PDF (selectable text via CoreText/`NSAttributedString`) | First-party; vector + small file + selectable text (vs raster). CLAUDE.md "prefer the CG/PDF path over raster" (D-01). `[CITED: developer.apple.com/documentation/uikit/uigraphicspdfrenderer]` |
| **SwiftUI `ImageRenderer`** | iOS 16+ (available iOS 18) | Bridge a SwiftUI chart view into the PDF `CGContext` as **vector** | `render { size, ctx in ... }` draws into any `CGContext` (incl. a PDF page) — vector, not raster. `@MainActor`. CLAUDE.md HIGH (D-02). `[VERIFIED: WebSearch + Apple docs; render→CGContext PDF pattern confirmed]` |
| **Swift Charts** (`InsightTrendChart`) | iOS 18+ | The existing chart view embedded into the PDF | Already built (Phase 8); reused via `ImageRenderer`. `[VERIFIED: read Sources/EndoApp/Features/Insights/InsightTrendChart.swift]` |
| **EndoInsights** (local SPM, Foundation-only) | in-repo | Range-scoped gated insights for section 6 | The single LOCKED public producer; window-based `asOf` API. `[VERIFIED: read InsightsEngine.swift]` |
| **SwiftData `Repository`** | in-repo | Range-scoped reads (snapshots, bleeding, factors, sources, suppressed) | Existing reads; filtered by day for the range. `[VERIFIED: read Sources/EndoApp/Persistence/Repository.swift]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **SwiftUI `ShareLink`** | iOS 16+ | System share sheet bound to a local file `URL` (RPRT-03) | Primary share path; explicit-tap only. `[CITED: developer.apple.com/documentation/swiftui/sharelink]` |
| **`UIActivityViewController`** (via `UIViewControllerRepresentable`) | iOS 18+ | Fallback share path if `ShareLink` ergonomics are insufficient | Only if `ShareLink` cannot express a needed activity exclusion; `ShareLink` is preferred (D-09). `[CITED: developer.apple.com/documentation/uikit/uiactivityviewcontroller]` |
| **CoreText / `NSAttributedString`** | iOS 18+ | Selectable vector text drawing + line/paragraph layout in the PDF | All body/heading/table text (so the PDF is searchable/screen-reader-friendly — UI-SPEC Accessibility). `[CITED: developer.apple.com/documentation/uikit/nsattributedstring]` |
| **`FileManager` temp dir** | system | Write the PDF to `FileManager.default.temporaryDirectory` | The throwaway temp file the user shares/discards (D-09); cleaned up after share/dismiss. `[CITED: developer.apple.com/documentation/foundation/filemanager]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `UIGraphicsPDFRenderer` (Core Graphics) | **TPPDF** (3rd-party auto-pagination/tables) | Adds a dependency — explicitly rejected by D-01 ("NO TPPDF") + CLAUDE.md near-zero-deps. Hand-rolled pagination math is the cost (see §Common Pitfalls 2). |
| `ImageRenderer.render` (vector) into `CGContext` | `ImageRenderer.uiImage` → draw raster `UIImage` into the page | Raster is blurry on print/zoom, larger file, not selectable — explicitly rejected by CLAUDE.md "What NOT to use" + D-02. |
| Reusing `InsightTrendChart` for the chart | A bespoke print-only chart view | Reuse is preferred (D-02); but the live chart uses `Color.accentColor` — the print spec wants single-ink black (UI-SPEC §Color). A thin print-variant or an injected ink color may be needed (see Open Question 1). |
| `ShareLink` | `UIActivityViewController` | `ShareLink` is the modern SwiftUI-native path (D-09 lists it first); the UIKit controller is the fallback only. |

**Installation:**
```bash
# NONE. All frameworks are first-party and already linked (UIKit/SwiftUI/PDFKit/Charts) or in-repo
# (EndoInsights, Repository). No SPM/CocoaPods/registry change. project.yml is UNCHANGED.
```

**Version verification:** No external packages to verify — this phase adds zero dependencies. PDFKit, Core Graphics, `ImageRenderer`, Swift Charts, PDFView, `ShareLink`, `UIActivityViewController`, and `NSAttributedString` are all part of the iOS 18 SDK (deployment target `18.0`, `[VERIFIED: read project.yml]`). No `npm`/`pip`/`cargo` analog applies.

## Package Legitimacy Audit

> **Not applicable.** Phase 10 installs **zero** external packages. All capabilities are delivered by first-party Apple frameworks (PDFKit, Core Graphics / UIKit `UIGraphicsPDFRenderer`, SwiftUI `ImageRenderer`/`ShareLink`, Swift Charts, CoreText) and the in-repo `EndoInsights` SPM package + `Repository`. `project.yml` `packages:` is unchanged (only `EndoInsights`, path-local). There is no third-party registry surface, no slopsquatting vector, and no `postinstall` concern.

**Packages removed due to [SLOP] verdict:** none (no external packages).
**Packages flagged as suspicious [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
                        ┌─────────────────────────────────────────────┐
  User taps             │  Settings  ─►  "Export report" row (D-06)    │
  "Export report"  ───► │  (Form Section, near Privacy)                │
                        └───────────────────────┬─────────────────────┘
                                                 │ NavigationLink
                                                 ▼
                        ┌─────────────────────────────────────────────┐
                        │  ReportBuilderView (Form)                    │
                        │   • preset Picker: 30d / 90d / Custom        │
                        │   • two DatePickers (From/To) when Custom    │
                        │   • "Generate PDF" (disabled until valid)    │
                        └───────────────────────┬─────────────────────┘
                                                 │ chosen DateInterval
                                                 ▼
        ┌──────────────────────── PURE / TESTABLE LAYER ───────────────────────┐
        │  ReportModel.assemble(range:repository:engine:asOf:)                  │
        │                                                                        │
        │   Repository (consent-gated reads, filtered to [start,end]):          │
        │     daySnapshots()  ──filter day∈range──►  [DaySnapshot]              │
        │     bleedingDays()  ──filter──►  cycle/bleeding section               │
        │     factorDays()    ──filter──►  lifestyle section                    │
        │     daySnapshotSources(on:) per day ──►  per-section ValueSource       │
        │     isSuppressed()  ──►  cycle three-state (no phantom)               │
        │                                                                        │
        │   EndoInsights.InsightsEngine.insights(                               │
        │       from: snapshotsInRange, asOf: range.end, dismissed:)            │
        │       └─ window-based; range achieved by FILTERING snapshots          │
        │                                                                        │
        │   → ReportModel { header, summary, symptoms, cycle, lifestyle,        │
        │                   insights, sourceLegend }  (each section honest:     │
        │                   absence≠0, sparse→notEnoughData, suppressed→off)    │
        └───────────────────────────────┬──────────────────────────────────────┘
                                         │ (pure value model — no rendering)
                                         ▼
        ┌──────────────────── THIN RENDER LAYER (@MainActor) ──────────────────┐
        │  ReportPDFRenderer.render(model:) -> URL                              │
        │   UIGraphicsPDFRenderer(bounds: US-Letter)                            │
        │     for each section:                                                 │
        │       draw NSAttributedString (CoreText, SELECTABLE vector text)      │
        │       draw tables (rules + cells), source tags (glyph+word)           │
        │       embed chart:  ImageRenderer(content: InsightTrendChart(...))    │
        │         .render { size, ctx in ctx(pdfCGContext) }  ← VECTOR (D-02)   │
        │       auto-paginate (track Y; new page + repeated footer/page-num)    │
        │   write Data ►  FileManager.temporaryDirectory/<name>.pdf             │
        └───────────────────────────────┬──────────────────────────────────────┘
                                         │ file URL
                                         ▼
                        ┌─────────────────────────────────────────────┐
                        │  ReportPreviewView                           │
                        │   • PDFView (UIViewRepresentable) shows doc   │
                        │   • toolbar ShareLink(item: fileURL) (D-09)   │
                        │     └─ system share sheet — NO upload         │
                        │   • dismiss ► delete temp file                │
                        └─────────────────────────────────────────────┘

   ✗ NO URLSession / network anywhere on this path (grep-assertable invariant)
```

### Recommended Project Structure
```
Sources/EndoApp/Features/Report/
├── ReportModel.swift          # PURE assembly: range-scoped sections, honesty, source labels (TESTABLE)
├── ReportCopy.swift           # auditable fixed-string enum (ClaimsPolicy choke-point, like InsightsCopy)
├── ReportPDFRenderer.swift    # @MainActor thin renderer: UIGraphicsPDFRenderer + CoreText + ImageRenderer
├── ReportBuilderView.swift    # Form: presets + custom DatePickers + Generate (Surface 1)
├── ReportPreviewView.swift    # PDFView (UIViewRepresentable) + ShareLink (Surface 1)
└── PDFKitView.swift           # tiny UIViewRepresentable wrapper around PDFView
```
> Rationale: a dedicated `Features/Report/` module (mirrors `Features/Insights/`). The renderer is app-side (PDFKit/UIKit/SwiftUI live in the app, NOT the Foundation-only `EndoInsights` package — CONTEXT.md Integration Points). The pure model is the unit-test surface; the renderer is smoke-tested.

### Pattern 1: Pure range-scoped data assembly (the testable seam)
**What:** A pure value-type `ReportModel` assembled from range-filtered Repository reads + the engine, with NO rendering. Mirrors the established `Repository.daySnapshots()` projection posture (the ONE place `@Model` rows become value types).
**When to use:** Always — this is the honesty + label boundary that unit tests assert against, decoupling "is the report correct/honest?" from "does it draw right?".
**Example:**
```swift
// Source: pattern derived from Repository.daySnapshots() + CycleSummaryView three-state logic
struct ReportModel: Equatable, Sendable {
    let range: DateInterval
    let generatedOn: Date
    let header: HeaderSection           // app title + range + Disclaimer.text
    let summary: SummarySection         // days logged, sources present, honest coverage line
    let symptoms: SymptomsSection        // trend table + chart points; sparse → notEnoughData
    let cycle: CycleSection              // .suppressed / .notEnoughData / .derived (NO phantom)
    let lifestyle: LifestyleSection      // per factor: value OR "Not logged" (absence ≠ 0)
    let insights: [Insight]              // engine output for the range (already gated)
    let legend: SourceLegend             // self-reported / from Apple Health / mixed

    static func assemble(
        range: DateInterval,
        repository: Repository,
        engine: (_ snapshots: [DaySnapshot], _ asOf: Date) -> [Insight],
        asOf: Date
    ) throws -> ReportModel {
        let cal = Calendar.current
        let all = try repository.daySnapshots()
        // RANGE SCOPING: the engine is window-based, so we FILTER snapshots to the range,
        // then anchor the engine at range.end (the engine's windows are relative to asOf).
        let inRange = all.filter { range.contains($0.day) || cal.isDate($0.day, inSameDayAs: range.end) }
        let insights = engine(inRange, range.end)
        // ... assemble each section honestly from inRange + bleedingDays()/factorDays()/sources ...
    }
}
```

### Pattern 2: Multi-page vector PDF with selectable text + auto-pagination
**What:** `UIGraphicsPDFRenderer` produces `Data`; each page draws `NSAttributedString` (CoreText → selectable vector text), tracking a running Y cursor and starting a new page (with repeated footer/page number) when content overflows the bottom margin.
**When to use:** The whole document body (D-03 multi-page + page numbers; D-01 vector + selectable).
**Example:**
```swift
// Source: developer.apple.com/documentation/uikit/uigraphicspdfrenderer (CITED)
@MainActor
func renderPDF(model: ReportModel) -> Data {
    let pageRect = CGRect(x: 0, y: 0, width: 612, height: 792) // US-Letter, 72pt = 1in
    let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
    var pageNumber = 0
    return renderer.pdfData { ctx in
        func newPage() {
            ctx.beginPage()
            pageNumber += 1
            drawFooter(pageNumber: pageNumber, in: ctx.cgContext, pageRect: pageRect) // D-04 §8
        }
        newPage()
        var y: CGFloat = 56 // page-margin (UI-SPEC PDF token)
        // For each section: measure its attributed text height; if y + h > 792 - margin → newPage(); reset y.
        // Draw with NSAttributedString.draw(in:) so text stays SELECTABLE vector (not rasterized).
    }
}
```
> Note: `ctx.cgContext` is the live PDF `CGContext` — the same context the `ImageRenderer` bridge (Pattern 3) draws the chart into, so the chart lands on the current page in vector form.

### Pattern 3: Embedding a SwiftUI chart as VECTOR via `ImageRenderer`
**What:** `ImageRenderer(content:)` wraps the existing `InsightTrendChart`; its `render { size, renderInContext in renderInContext(pdfCGContext) }` closure draws the chart's vector content directly into the PDF page's `CGContext` (NOT a raster `uiImage`).
**When to use:** Section 3 (Symptoms & pain) chart embedding (D-02).
**Example:**
```swift
// Source: appcoda.com/swiftui-imagerenderer-pdf + Apple ImageRenderer docs (VERIFIED render→CGContext)
@MainActor
func drawChart(_ chart: InsightTrendChart, at origin: CGPoint, size: CGSize, into ctx: CGContext) {
    let renderer = ImageRenderer(content: chart.frame(width: size.width, height: size.height))
    renderer.render { _, renderInContext in
        ctx.saveGState()
        ctx.translateBy(x: origin.x, y: origin.y)
        renderInContext(ctx)   // ← draws SwiftUI/Charts content as VECTOR into the PDF CGContext
        ctx.restoreGState()
    }
}
```
**MainActor / Swift-6 note:** `ImageRenderer` is strictly `@MainActor` and ALL interaction (and the SwiftUI view it renders) must occur on the main thread `[VERIFIED: Apple docs "strictly annotated @MainActor … must occur on the main thread"]`. Therefore the **renderer is `@MainActor`**, the chart is rendered **off the live view tree** (a fresh `InsightTrendChart(...)` constructed with the range's `points`/`asOf`, not the on-screen instance), and the *pure* `ReportModel` assembly stays off the renderer entirely so the heavy data work can run before the main-actor render hop. The image is small (one chart), so the main-actor render is brief; show a non-blocking "Generating your report…" affordance (UI-SPEC) rather than freezing.

### Pattern 4: Per-section source labeling from the Phase-9 provenance seam
**What:** `Repository.daySnapshotSources(on:)` returns a `DaySnapshotSource` with per-field `ValueSource?` (`.manual` / `.appleHealth` / `nil`). The model aggregates these across the range per section into "Self-reported", "From Apple Health", or "Mixed", rendered as **glyph + word (never color)**.
**When to use:** Every data section + the legend (RPRT-02 / D-08).
**Example:**
```swift
// Source: read Repository.daySnapshotSources / ValueSource (VERIFIED)
enum SectionSource: Equatable { case selfReported, appleHealth, mixed, none }
func aggregateSource(_ sources: [ValueSource?]) -> SectionSource {
    let present = sources.compactMap { $0 }
    if present.isEmpty { return .none }                       // no badge — SC3 honesty
    let manual = present.contains(.manual), health = present.contains(.appleHealth)
    if manual && health { return .mixed }
    return manual ? .selfReported : .appleHealth
}
```
> The label is `glyph + word`: open-circle glyph + "Self-reported"; heart-square glyph + "From Apple Health"; "Mixed". A clinician printing grayscale MUST distinguish them by shape+word — never hue (UI-SPEC §Color).

### Pattern 5: Share with NO upload (local temp file + ShareLink)
**What:** Write the PDF `Data` to `FileManager.default.temporaryDirectory.appending(path: "EndoReport-<range>.pdf")`, then `ShareLink(item: url)` in the preview toolbar. No network call is ever made.
**When to use:** The share action (RPRT-03 / D-09).
**Example:**
```swift
// Source: developer.apple.com/documentation/swiftui/sharelink (CITED)
let url = FileManager.default.temporaryDirectory.appending(path: fileName)
try data.write(to: url, options: .atomic)
// in the preview toolbar:
ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
```
> The "no upload" guarantee is an *absence* invariant: the report module imports no `URLSession`/`URLRequest`/`dataTask`. This is already true for the entire `Sources/EndoApp` tree `[VERIFIED: grep]`; a test/grep gate keeps it true (see §Validation Architecture).

### Anti-Patterns to Avoid
- **Raster chart snapshots (`ImageRenderer.uiImage` → draw `UIImage`):** blurry on print, large file, not selectable — rejected by D-02 + CLAUDE.md. Use the `render`→`CGContext` vector path (Pattern 3).
- **Re-deriving cycle/insight logic in the renderer:** never recompute cycle phases or trends in the PDF layer. Reuse `CycleMath` / the engine in the *pure model* so the no-phantom-cycle + honesty contract is the same auditable logic as the live app.
- **Hard-coded copy strings in the renderer:** all fixed report text must live in `ReportCopy` (the `ClaimsPolicy` choke-point), never inline — otherwise the copy audit cannot catch a forbidden/gendered/causal token.
- **Passing an arbitrary `[start,end]` to the engine:** the engine has NO range parameter; filter the `[DaySnapshot]` array instead and anchor `asOf` at `range.end` (Pattern 1).
- **`value: 0` for an unlogged day/factor:** absence ≠ 0. An unlogged factor reads "Not logged"; a sparse section reads "Not enough data in this range." (mirror `CycleSummaryCopy.notEnoughData` / `TrendsEngine`).
- **A single hard date for cycle estimates:** mirror `CycleSummaryView` — emit a date RANGE + a plain confidence word, never "predicted", never a single date.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PDF page/context lifecycle | A manual `CGPDFContext` + consumer dance | `UIGraphicsPDFRenderer.pdfData { ctx in ... }` | Handles page begin/end, metadata, `Data` output; far fewer footguns than raw `CGDataConsumer`/`CGContext`. (Raw CG is only needed inside the `ImageRenderer` bridge.) |
| Selectable text layout | Manual glyph positioning | `NSAttributedString.draw(in:)` / CoreText | Gives selectable, searchable, screen-reader-friendly vector text + line wrapping for free (UI-SPEC Accessibility). |
| Chart drawing for print | A bespoke CoreGraphics chart | Reuse `InsightTrendChart` via `ImageRenderer` (D-02) | The honest-gaps/absolute-date/a11y logic already exists and is tested (Phase 8). |
| Cycle estimate / phases | New cycle math in the report | `CycleMath` / engine in the pure model | The no-phantom-cycle three-state contract is already proven; re-deriving risks drift. |
| Insight gating for the range | New gating in the report | `InsightsEngine.insights(...)` on the filtered snapshots | The HonestyGate (confidence floor, min-sample, non-causal) is LOCKED and must not be bypassed. |
| Source provenance | A new "where did this come from" computation | `Repository.daySnapshotSources(on:)` | The merge is the single source of truth for value AND source (Phase 9). |
| Share sheet | A custom share UI | `ShareLink(item: url)` | System sheet, explicit-action, no upload; SwiftUI-native. |
| PDF preview | A custom renderer view | PDFKit `PDFView` (UIViewRepresentable) | First-party, selectable-text, zoom/scroll for free (D-07). |
| Text-extraction for tests | Parsing raw PDF bytes | `PDFDocument(url:).string` | First-party full-text extraction — the test reads back the disclaimer/legend strings. |

**Key insight:** This phase is almost entirely *composition of existing first-party + in-repo pieces*. The only genuinely new code is (a) the pure range-scoped assembly model, (b) the CoreText pagination/table drawing, and (c) the `ImageRenderer→CGContext` bridge. Everything else (cycle math, insight gating, provenance, charts, disclaimer, copy-audit infrastructure) is reused — which is exactly the honesty-preservation strategy.

## Runtime State Inventory

> **Not applicable — this is a greenfield, additive feature phase (not a rename/refactor/migration).** Phase 10 adds a new `Features/Report/` module and a Settings entry; it introduces **no schema change** (no new `@Model`, no `SchemaV7`, no migration), stores **no new persisted data** (the PDF is a throwaway temp file), registers no OS-level state, and adds no secret/env var. The only filesystem artifact is the transient `temporaryDirectory` PDF, which is created and deleted within the share/dismiss flow (see §Security Domain).
>
> - **Stored data:** None — no new `@Model`/schema/migration. Verified: the report only READS existing models via the Repository; CONTEXT.md "Insight/engine logic is unchanged."
> - **Live service config:** None — no external services touched.
> - **OS-registered state:** None — no Task Scheduler/launchd/notification registration.
> - **Secrets/env vars:** None.
> - **Build artifacts:** None new — `project.yml` is unchanged (no new target/package); the file lives in the existing `EndoApp` target's `Sources/EndoApp` glob.

## Common Pitfalls

### Pitfall 1: Source label drifts from the value it labels
**What goes wrong:** The PDF shows a value sourced from manual logging but labels it "From Apple Health" (or vice-versa), or shows a badge for an absent field.
**Why it happens:** Computing provenance separately from the value, instead of from the same merge gate. The Phase-9 code documents this exact trap: imported activity wins ONLY when there's no manual activity AND an imported TYPE is present — minutes-only must NOT claim `.appleHealth`.
**How to avoid:** Source every section's label from `Repository.daySnapshotSources(on:)` (which mirrors the single merge gate), never from a re-computation. A field with neither source reports `nil` → **no badge** (SC3 honesty extends to provenance).
**Warning signs:** A "From Apple Health" tag on a field the user only ever typed; a badge on an empty section.

### Pitfall 2: Manual pagination orphans/clips content
**What goes wrong:** A section heading lands at the very bottom of a page with its body on the next; or a table row is split/clipped at the page boundary.
**Why it happens:** `UIGraphicsPDFRenderer` does NOT auto-flow text across pages — you must measure and break manually (the cost of rejecting TPPDF, D-01).
**How to avoid:** Track a running Y cursor; before drawing a block, measure its height (`NSAttributedString.boundingRect(with:options:context:)`); if `y + height > pageHeight - margin`, start a new page. Never orphan a heading: if the heading fits but its first block doesn't, carry both to the next page (UI-SPEC Pagination rule).
**Warning signs:** Headings stranded at page bottoms; text clipped at the margin; the page-count smoke test returns fewer pages than content implies.

### Pitfall 3: Copy bypasses the ClaimsPolicy audit
**What goes wrong:** A PDF string says "this may trigger your pain" or contains "her"/"women" or "predicted", silently violating the non-causal/non-gendered/non-diagnostic posture (RPRT-02).
**Why it happens:** Strings drawn inline in the renderer aren't in any auditable surface, so `CopyAuditTests` never scans them.
**How to avoid:** Centralize EVERY fixed report string in `ReportCopy` (mirror `InsightsCopy`/`CycleSummaryCopy`) and extend `CopyAuditTests` to scan `ReportCopy.auditableStrings` against `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens`. Watch the documented substring traps: "trigger"/"causes"/"will"/"detect" are forbidden verbs; "her" hides in "there"/"here"/"higher"/"lower"; rephrase like prior phases did. The header disclaimer reuses `Disclaimer.text` verbatim (already audited) — never retype it.
**Warning signs:** `CopyAuditTests` not referencing `ReportCopy`; a string literal in `ReportPDFRenderer.swift`.

### Pitfall 4: ImageRenderer used off the main actor (or rendering the live view)
**What goes wrong:** A crash, a blank chart, or a Swift-6 actor-isolation error; or the on-screen chart's state leaks into the print artifact.
**Why it happens:** `ImageRenderer` is strictly `@MainActor`; constructing/rendering it off-main is illegal. Reusing the *live* `InsightTrendChart` instance couples print output to screen state (accent color, animation, Dynamic Type).
**How to avoid:** Mark the renderer `@MainActor`; construct a FRESH `InsightTrendChart(points:window:directionWord:headline:asOf: range.end)` for print; do the pure `ReportModel` assembly BEFORE the main-actor render hop so only the brief draw runs on-main. Consider an injected single-ink color for print (Open Question 1).
**Warning signs:** `@Sendable`/isolation warnings around the renderer; the PDF chart in `Color.accentColor` instead of print black.

### Pitfall 5: Temp PDF leaks health data on disk
**What goes wrong:** The generated PDF (which contains special-category health data) lingers in `temporaryDirectory` after the user dismisses the preview, readable by anything with file access.
**Why it happens:** No cleanup after share/dismiss; or writing to a backed-up/longer-lived location.
**How to avoid:** Write to `FileManager.default.temporaryDirectory` (OS may purge, but don't rely on it); delete the file on preview dismiss and on a fresh generation. Do NOT write to `Documents` (iCloud-backed) for the throwaway artifact. Exclude from backup if a longer-lived location is ever used. (See §Security Domain.)
**Warning signs:** Multiple stale `EndoReport-*.pdf` files in tmp; the file surviving a dismiss.

## Code Examples

### Reading the disclaimer (single source of truth) into the PDF header
```swift
// Source: read Sources/EndoApp/Content/Disclaimer.swift (VERIFIED)
// The prominent header band renders Disclaimer.text VERBATIM (RPRT-02) — never a retyped copy.
let disclaimer = Disclaimer.text  // "...not medical advice and not a medical device...does not diagnose, treat, cure, or prevent..."
```

### Range-scoped insight selection (engine is window-based, not range-based)
```swift
// Source: read EndoInsights/Sources/EndoInsights/InsightsEngine.swift (VERIFIED)
// The engine computes 7/30/90/all-time windows internally from `asOf`. To scope to [start,end]:
let snapshotsInRange = allSnapshots.filter { range.contains($0.day) }
let dismissed = try repository.dismissedSignalKeys()
let insights = InsightsEngine.insights(from: snapshotsInRange, asOf: range.end, dismissed: dismissed)
// NOTE: windows still measure back from range.end; for a 30-day range the .last30 window == the range.
// The report selects the windows that fit the chosen range (e.g. a 30-day range → .last30 / .last7).
```

### PDFDocument text-extraction for tests (no visual diffing)
```swift
// Source: developer.apple.com/documentation/pdfkit/pdfdocument/string (CITED)
let doc = PDFDocument(url: generatedURL)!
let text = doc.string ?? ""
#expect(text.contains("not medical advice"))      // disclaimer present (RPRT-02)
#expect(text.contains("Self-reported"))            // source legend present (RPRT-02)
#expect(text.contains("From Apple Health"))
#expect(doc.pageCount >= 1)                          // non-empty smoke (RPRT-01)
```

### Builder range presets + invalid-range guard
```swift
// Source: SettingsView Form idiom (VERIFIED) + UI-SPEC Surface 1
enum RangePreset { case last30, last90, custom }
// Generate is .disabled(to < from) for the custom path (mirrors the add-med .disabled guard, UI-SPEC).
func interval(for preset: RangePreset, from: Date, to: Date, asOf: Date) -> DateInterval {
    let cal = Calendar.current
    switch preset {
    case .last30: return DateInterval(start: cal.date(byAdding: .day, value: -30, to: asOf)!, end: asOf)
    case .last90: return DateInterval(start: cal.date(byAdding: .day, value: -90, to: asOf)!, end: asOf)
    case .custom: return DateInterval(start: cal.startOfDay(for: from), end: cal.startOfDay(for: to))
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ChartRenderer` (early SwiftUI Charts) | `ImageRenderer` for chart export | Xcode 14 beta 3 (2022) | `ChartRenderer` was removed; `ImageRenderer` is THE chart→image/PDF path. `[VERIFIED: WebSearch]` |
| Raster `UIImage` PDF pages | `ImageRenderer.render`→`CGContext` vector | iOS 16+ | Vector charts in PDFs: crisp on print/zoom, small file, selectable surrounding text. |
| `UIActivityViewController` only | SwiftUI `ShareLink` | iOS 16+ | Declarative, SwiftUI-native share; D-09 lists it first. |
| Hand-rolled `CGPDFContext` | `UIGraphicsPDFRenderer` | iOS 10+ | Cleaner page lifecycle + `Data` output; the standard modern path. |

**Deprecated/outdated:**
- `ChartRenderer`: removed — use `ImageRenderer`.
- `UIGraphicsBeginPDFContextToData` (the old global C-style API): superseded by `UIGraphicsPDFRenderer` (object-based, scoped pages).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A 30-day range maps cleanly onto the engine's `.last30` window when `asOf == range.end`; for a *custom* range that isn't 7/30/90 days, the engine emits the standard windows measured back from `range.end` and the report selects/labels the window(s) that fit the range. | §Code Examples (range-scoped selection), §Pattern 1 | A custom range like "Mar 1–Apr 15" (46 days) has no exact engine window — the report must decide which windows to show (e.g. `.last30` + `.last90`) and label them honestly, OR present only descriptive section tables for the exact range and use the engine only for the standard windows. Planner must decide the window-to-range mapping; mis-mapping could overstate coverage. **Needs a planning decision.** |
| A2 | US-Letter (612×792) is the chosen page size; A4 (595×842) is acceptable per D-03. | §Pattern 2 | Wrong default page size = clipped margins for the other locale. Low risk (cosmetic; pick one, US-Letter assumed for a US-default clinician artifact). The UI-SPEC PDF tokens (56pt margin etc.) assume one size. |
| A3 | `ImageRenderer.render`'s `renderInContext` closure, when handed the live PDF `CGContext`, lands vector chart content on the current PDF page with correct coordinate translation. | §Pattern 3 | If the coordinate system / page transform needs extra handling (PDF origin is bottom-left in raw CG, but `UIGraphicsPDFRenderer` flips to top-left UIKit coords), the chart may draw mirrored/offset. Mitigate with a render smoke test that extracts the page and checks bounds; verified pattern exists but exact transform is environment-specific. **Verify in a Wave-0 spike.** |
| A4 | Reusing `InsightTrendChart` for print is acceptable despite its `Color.accentColor` ink (UI-SPEC wants single-ink black for print). | §Alternatives, §Pitfall 4 | If a print-black variant is required, `InsightTrendChart` may need an injected ink color (small additive change) or a thin print wrapper. Cosmetic but contradicts the UI-SPEC monochrome-print intent if ignored. |

**Note:** A1 and A3 are the two that genuinely affect the plan structure (window-to-range mapping; the chart coordinate transform) and should be resolved via a small Wave-0 spike or an explicit planner decision.

## Open Questions

1. **Custom-range → engine-window mapping.**
   - What we know: the engine is window-based (7/30/90/all-time, anchored at `asOf`); presets 30d/90d map exactly when `asOf == range.end`.
   - What's unclear: how to populate section 6 (Insights) for an arbitrary custom range (e.g. 46 days) that has no exact window.
   - Recommendation: For section 6, show the engine windows that are ⊆ the chosen range (e.g. a 46-day range → `.last30` insights labeled "last 30 days"), and rely on the descriptive section tables (3/4/5) for the exact range. Make this an explicit planner decision; the descriptive tables are computed directly from the range-filtered reads (not the engine) so they always honor the exact range.

2. **Print-ink color for the embedded chart.**
   - What we know: `InsightTrendChart` uses `Color.accentColor`; the UI-SPEC PDF palette is single-ink black.
   - What's unclear: whether to inject a print color into the existing chart or render a print wrapper.
   - Recommendation: add an optional `inkColor: Color = .accentColor` parameter to `InsightTrendChart` (additive, default-unchanged for the live screen) and pass `.black` from the renderer. Keep the accompanying text summary so the chart is never the sole carrier of meaning.

3. **Chart `CGContext` coordinate transform (UIKit top-left vs CG bottom-left).**
   - What we know: `UIGraphicsPDFRenderer` exposes a UIKit-flipped `cgContext`; `ImageRenderer.render` draws in the context it's given.
   - What's unclear: whether an extra `translate`/`scaleBy(y:-1)` is needed for the chart to land upright.
   - Recommendation: resolve in a tiny Wave-0 render spike (generate a 1-chart PDF, open it, eyeball + `PDFDocument` bounds). Cheap to verify, expensive to guess wrong.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PDFKit | Preview + text-extraction tests | ✓ (iOS 18 SDK) | iOS 18+ | — |
| Core Graphics / `UIGraphicsPDFRenderer` | PDF generation | ✓ (iOS 18 SDK) | iOS 18+ | — |
| SwiftUI `ImageRenderer` | Chart→vector embedding | ✓ (iOS 16+) | iOS 18+ | raster `uiImage` (rejected by D-02 — not a real fallback) |
| Swift Charts (`InsightTrendChart`) | Embedded chart | ✓ (in-repo, Phase 8) | iOS 18+ | text-only table (chart is supplementary; section still works) |
| `EndoInsights` engine | Section 6 insights | ✓ (in-repo SPM) | — | descriptive tables only (sections 3–5 don't need the engine) |
| `Repository` reads | All section data | ✓ (in-repo) | — | — |
| `ShareLink` / `UIActivityViewController` | Share (no upload) | ✓ (iOS 16+) | iOS 18+ | — |

**Missing dependencies with no fallback:** None — all are first-party (iOS 18 SDK) or in-repo and already linked.
**Missing dependencies with fallback:** None blocking. (The only "fallback" — raster chart — is explicitly rejected by D-02; the vector path is required.)

## Validation Architecture

> `workflow.nyquist_validation` is `true` `[VERIFIED: read .planning/config.json]` — this section is included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`) for unit + XCUITest for E2E (both already in the scheme) |
| Config file | `project.yml` scheme `testTargets`: `EndoAppTests`, `EndoAppUITests`, `package: EndoInsights/EndoInsightsTests` `[VERIFIED: read project.yml]` |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/ReportModelTests` |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| RPRT-01 | A range produces a non-empty multi-page PDF, on-device | unit (render smoke) | `… -only-testing:EndoAppTests/ReportRenderTests/generatesNonEmptyPDF` | ❌ Wave 0 |
| RPRT-01 | `ReportModel.assemble` scopes data to the chosen `DateInterval` (days outside range excluded) | unit | `… -only-testing:EndoAppTests/ReportModelTests/scopesToRange` | ❌ Wave 0 |
| RPRT-01 | Sparse range → every section reads "Not enough data in this range" (never fabricated) | unit | `… -only-testing:EndoAppTests/ReportModelTests/sparseRangeIsHonest` | ❌ Wave 0 |
| RPRT-01 | Suppressed user → cycle section is the off-state, NO phantom cycle/phase | unit | `… -only-testing:EndoAppTests/ReportModelTests/suppressedNoPhantomCycle` | ❌ Wave 0 |
| RPRT-01 | Unlogged factor reads "Not logged", never 0 (absence ≠ 0) | unit | `… -only-testing:EndoAppTests/ReportModelTests/absenceIsNotZero` | ❌ Wave 0 |
| RPRT-02 | Source labels derive from `daySnapshotSources` (self-reported / from Apple Health / mixed / none) | unit | `… -only-testing:EndoAppTests/ReportModelTests/sourceLabelsMatchProvenance` | ❌ Wave 0 |
| RPRT-02 | The generated PDF's extracted text contains `Disclaimer.text` + both source labels | unit (text-extraction) | `… -only-testing:EndoAppTests/ReportRenderTests/pdfTextContainsDisclaimerAndLegend` | ❌ Wave 0 |
| RPRT-02 | `ReportCopy` passes ClaimsPolicy (no forbidden verb / gendered token / numeric over-claim) | unit | `… -only-testing:EndoAppTests/CopyAuditTests/reportCopy*` (extend existing) | ❌ Wave 0 (extend `CopyAuditTests.swift`) |
| RPRT-03 | The report module imports no `URLSession`/network (no-upload invariant) | unit (source/grep scan) | `… -only-testing:EndoAppTests/ReportNoNetworkTests/reportPathHasNoNetworkImport` | ❌ Wave 0 |
| RPRT-03 | End-to-end: Settings → Export report → preset → Generate → preview shows PDF → Share present | E2E (XCUITest) | `… -only-testing:EndoAppUITests/ReportFlowUITests` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `ReportModelTests` (pure, fast) + the relevant `CopyAuditTests` subset.
- **Per wave merge:** full `EndoAppTests` target + `ReportFlowUITests`.
- **Phase gate:** full suite green (`EndoAppTests` + `EndoAppUITests` + `EndoInsights/EndoInsightsTests`) before `/gsd-verify-work`. (Note: prior phases recorded the full `-only-testing:EndoAppTests` run STALLING in this env >15 min — use targeted + combined runs as verification, per the Phase-8 precedent in STATE.md.)

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/ReportModelTests.swift` — covers RPRT-01/02 (range scoping, sparse-honesty, suppressed-no-phantom, absence≠0, source labels). PURE — no store/render needed (assemble over an in-memory Repository or injected reads).
- [ ] `Tests/EndoAppTests/ReportRenderTests.swift` — covers RPRT-01/02 (non-empty page-count smoke + `PDFDocument.string` substring asserts). `@MainActor`.
- [ ] `Tests/EndoAppTests/ReportNoNetworkTests.swift` — covers RPRT-03 (source-scan: no `URLSession`/`URLRequest`/`dataTask` in `Features/Report/`). Mirror the existing `BoundaryLintTests` `#filePath` source-scan pattern.
- [ ] `Tests/EndoAppUITests/ReportFlowUITests.swift` — covers RPRT-01/03 E2E (builder → generate → preview → share present). Gate the data behind the existing `-uitest-reset` + a `-uitest-seed-insights`-style fixture.
- [ ] Extend `Tests/EndoAppTests/CopyAuditTests.swift` — add `ReportCopy.auditableStrings` scans (RPRT-02). (File exists; add a suite/section.)
- [ ] Framework install: none — Swift Testing + XCUITest already in the scheme.

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high` `[VERIFIED: read .planning/config.json]`. ASVS L1; block on high.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No auth in this app (no accounts — uses iCloud identity implicitly). |
| V3 Session Management | no | No sessions. |
| V4 Access Control | yes (consent gate) | The report path is reachable ONLY from the consented shell (Settings lives under `AppGate.consented`); `Repository` reads are consent-gated (`assertConsented()`). No new gate needed — reuse the existing one. |
| V5 Input Validation | yes | Date-range inputs: guard `to >= from` (UI-SPEC invalid-range guard); bound any free-text echoed into the PDF (notes are already length-bounded at the Repository seam). |
| V6 Cryptography | no | No new crypto; the PDF is plaintext health data handled per data-handling below (never hand-roll crypto). |
| V8 Data Protection | yes (the core risk) | Health data in a shareable file — see threat table. Temp file in `temporaryDirectory`, deleted on dismiss; never `Documents`/iCloud-backed; share is explicit-action only; NO network/upload (grep invariant). |
| V12 Files & Resources | yes | Write to `temporaryDirectory` with `.atomic`; delete after share/dismiss; do not include in backup. |

### Known Threat Patterns for {SwiftUI/PDFKit on-device health report}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Generated PDF (special-category health data) lingers on disk after dismiss | Information Disclosure | Write to `FileManager.temporaryDirectory`; delete on preview-dismiss + before each regeneration; never `Documents`/iCloud. (Pitfall 5.) |
| Silent/auto upload of the PDF to a server or analytics | Information Disclosure / Repudiation | NO network path in the report module — grep-assertable (`ReportNoNetworkTests`). Share is explicit `ShareLink` tap only (RPRT-03/D-09). Already true app-wide (no `URLSession` in `Sources/EndoApp`). |
| Source label misrepresents provenance (claims "Apple Health" for self-reported, or vice-versa) | Tampering / Spoofing | Derive labels from the single merge gate (`daySnapshotSources`), never a re-computation (Pitfall 1). `nil` source → no badge. |
| Fabricated/over-claimed content (phantom cycle, "0" for absence, causal/diagnostic copy) | Tampering (integrity of a clinician-facing artifact) | Honesty contract in the pure model (absence≠0, three-state cycle, sparse→notEnoughData) + `ReportCopy` through `ClaimsPolicy` + the prominent `Disclaimer.text` band. |
| Health data echoed into logs during generation/error | Information Disclosure | Log only error TYPE, never content (the established Phase-8/9 posture: `print("…\(type(of: error))…")`). |
| Unbounded free-text (a pasted note) bloats/breaks the PDF | Denial of Service | Notes are already length-bounded at the Repository seam; the renderer truncates/wraps via CoreText. |

**Security summary:** The dominant threat is V8 Data Protection — a shareable file containing GDPR Art. 9 special-category data. The mitigations are all *absence/cleanup* controls (no network, temp-only, delete-on-dismiss, explicit-share) plus the integrity controls (provenance fidelity, honesty, audited copy). No new attack surface beyond the temp file is introduced. The planner should add an explicit temp-file-cleanup task and the `ReportNoNetworkTests` invariant.

## Sources

### Primary (HIGH confidence)
- **Codebase reads** (the highest-fidelity source here — every reuse target is real code):
  - `Sources/EndoApp/Persistence/Repository.swift` — `daySnapshots()`, `bleedingDays()`, `factorDays()`, `daySnapshotSources(on:)`, `isSuppressed()`, consent-gating.
  - `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` — the LOCKED window-based `insights(from:asOf:dismissed:calendar:)` API (no range param).
  - `Sources/EndoApp/Content/Disclaimer.swift` — `Disclaimer.text` single source of truth.
  - `Sources/EndoApp/Content/ClaimsPolicy.swift` + `Tests/EndoAppTests/CopyAuditTests.swift` — the copy-audit choke-point to extend.
  - `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` + `InsightsCopy.swift` — the chart to embed + copy-enum pattern.
  - `Sources/EndoApp/Features/Home/CycleSummaryView.swift` — the no-phantom-cycle three-state pattern.
  - `Sources/EndoApp/Features/Settings/SettingsView.swift` + `App/AppGate.swift` + `App/AppDependencies.swift` — entry point, consent shell, injection pattern.
  - `Sources/EndoApp/Health/ImportedSampleValue.swift` — `ValueSource`/`DaySnapshotSource` provenance types.
  - `project.yml`, `.planning/config.json` — targets, test scheme, validation/security flags.
- developer.apple.com/documentation/{pdfkit, uikit/uigraphicspdfrenderer, swiftui/imagerenderer, swiftui/sharelink, uikit/nsattributedstring} — framework capabilities (CITED).

### Secondary (MEDIUM confidence)
- WebSearch (corroborated against Apple docs): `ImageRenderer` is `@MainActor`, generates vector PDF, `render { size, renderInContext }` draws into any `CGContext`; `ChartRenderer` removed in favor of `ImageRenderer`.
- appcoda.com/swiftui-imagerenderer-pdf — the concrete `render`→`CGContext` PDF page pattern (`beginPDFPage`/`renderer(ctx)`/`endPDFPage`).

### Tertiary (LOW confidence)
- None relied upon for load-bearing claims. (The two genuine unknowns — A1 window mapping, A3 chart coordinate transform — are flagged for a Wave-0 spike rather than asserted.)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all first-party, already in the project's adopted stack (CLAUDE.md HIGH ratings) and most already linked/used in-repo.
- Architecture (pure model + thin renderer + reuse): HIGH — directly mirrors the proven `daySnapshots()` projection + `CycleSummaryView`/`InsightsCopy` patterns; reuse-over-rebuild minimizes new logic.
- ImageRenderer→vector-PDF bridge: MEDIUM-HIGH — the API and pattern are verified; the exact coordinate transform on the `UIGraphicsPDFRenderer` context (A3) warrants a tiny Wave-0 spike.
- Pitfalls: HIGH — derived from the codebase's own documented traps (provenance/value drift, copy-audit substring traps, off-main `@MainActor`) + the well-known manual-pagination cost of rejecting TPPDF.

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (30 days — stable first-party frameworks; no fast-moving dependency).
