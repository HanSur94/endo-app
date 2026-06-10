---
phase: 10-doctor-ready-pdf-report
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - Sources/EndoApp/Features/Report/ReportModel.swift
  - Sources/EndoApp/Features/Report/ReportCopy.swift
  - Sources/EndoApp/Features/Report/ReportRangeMapping.swift
  - Sources/EndoApp/Features/Report/ReportPDFRenderer.swift
  - Sources/EndoApp/Features/Report/ReportBuilderView.swift
  - Sources/EndoApp/Features/Report/ReportPreviewView.swift
  - Sources/EndoApp/Features/Report/PDFKitView.swift
  - Sources/EndoApp/Features/Settings/SettingsView.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/Features/Insights/InsightTrendChart.swift
findings:
  critical: 1
  warning: 7
  info: 4
  total: 12
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Phase 10 builds the doctor-ready PDF report: a pure `ReportModel` (honesty/source boundary), a
`@MainActor ReportPDFRenderer`, the SwiftUI builder/preview surfaces, and the chart embed. The
privacy/no-upload invariant largely holds: there is no networking on the report path, the single
egress is the explicit `ShareLink`, and only error *types* are logged. The honesty contract
(absence ≠ 0, three-state cycle, sparse → "not enough data") is correctly modeled in `ReportModel`.

However there is a temp-file lifecycle bug that can delete the Art.9 PDF before the share completes
(BLOCKER), plus several correctness/honesty defects: an off-by-one on the `.last30`/`.last90` start
boundary that silently drops the first claimed day, a legend that omits the self-reported entry for
symptoms-only reports while still drawing the badge, a chart that *fabricates* per-day dates/values
rather than rendering the real series, and the assembly running on the main actor (contradicting the
stated off-main design). Details below.

## Critical Issues

### CR-01: Temp PDF is deleted on preview `onDisappear` while the system share sheet is still presenting — share can fail or share an empty file

**File:** `Sources/EndoApp/Features/Report/ReportPreviewView.swift:36-46`
**Issue:** `ShareLink(item: url)` presents the system share sheet, then the user picks a
destination (Mail, Files, AirDrop, Print). On iOS the share sheet is presented *over* the preview,
and `ShareLink` reads the file lazily/asynchronously when the activity actually runs — copying it to
Mail's draft, writing it to Files, etc. The preview's `.onDisappear` deletes the temp file:

```swift
.onDisappear {
    try? FileManager.default.removeItem(at: url)
}
```

`onDisappear` fires when the preview is covered/popped, which can happen *before* the chosen share
activity has finished reading the URL (e.g. the user backs out, or the activity copies
asynchronously, or — on some iOS versions — the presenting view resigns while the sheet is up).
Because the file is health data (GDPR Art.9) written to `temporaryDirectory`, a premature delete
means the share either fails silently or exports a zero-byte/half-written file. The builder *also*
deletes the prior file before each fresh generation (`ReportBuilderView.generate()`), so cleanup is
already guaranteed by the producer — the consumer's eager `onDisappear` delete is the unsafe half.
**Fix:** Do not delete on `onDisappear`. Either (a) rely solely on the builder's pre-generation
cleanup plus app-temp-dir reaping, or (b) tie deletion to the `ShareLink`'s completion, or (c) delay
deletion until the next generation. Minimal safe change:

```swift
// Remove the onDisappear delete entirely; the builder already removes any prior temp file
// before each fresh generation, so the file is not leaked, and the share can complete safely.
```

If a deterministic cleanup is required, gate it behind a flag set only once a *new* URL supersedes
this one, never on mere disappearance while the share sheet may still be live.

## Warnings

### WR-01: `.last30` / `.last90` start boundary is off-by-one — the first claimed day is silently excluded

**File:** `Sources/EndoApp/Features/Report/ReportRangeMapping.swift:47-52`,
`Sources/EndoApp/Features/Report/ReportModel.swift:273-275`
**Issue:** For the presets, `asOf` is `.now` (a wall-clock time, not start-of-day — see
`ReportBuilderView.swift:165`). `interval` then computes `start = asOf - 30 days` and `end = asOf`,
both carrying today's time-of-day. `dayInRange` admits a day when
`range.contains(day) || isDate(day, inSameDayAs: range.end)`. `DateInterval.contains` is half-open
`[start, end)`, and snapshots' `day` is **start-of-day (00:00)**. So the snapshot for the
earliest day (start-of-day 30 days ago) is `< range.start` (which is e.g. 14:00 thirty days ago) and
is *not* same-day as `range.end` → it is dropped. The report header nonetheless prints
`Covers {formatDate(range.start)} – …` with a date-only `.medium` style (`ReportPDFRenderer.swift:506-510`),
so it claims to cover a day whose logs were excluded — a coverage overstatement and an off-by-one.
**Fix:** Normalize the preset bounds to start-of-day before building the interval, e.g.:

```swift
case .last30:
    let endDay = calendar.startOfDay(for: asOf)
    let start = calendar.date(byAdding: .day, value: -30, to: endDay) ?? endDay
    return DateInterval(start: start, end: asOf)   // keep end at asOf so today's logs (inclusive-end) stay in
```

so `range.start` aligns to a snapshot day key and the first claimed day is actually included.

### WR-02: Source legend omits the "Self-reported" line for a symptoms-only report even though the symptoms section draws the badge

**File:** `Sources/EndoApp/Features/Report/ReportModel.swift:245-254` (vs. `330-335`)
**Issue:** The symptoms section's source is aggregated from symptom presence and correctly yields
`.selfReported` (line 330-335), so the renderer draws "● Self-reported" beside the Symptoms heading
(`ReportPDFRenderer.drawSectionHeading`). But the section-7 legend's `hasSelfReported` is computed
*only* from `allFieldSources = [flow, sleep, activity, food]` (line 245, 251-254) — symptoms are
excluded. A user who logged *only* symptoms in the range gets `daysLogged > 0`, a "Self-reported"
badge on section 3, yet `legend.hasSelfReported == false`, so `drawLegend` suppresses the
"● Self-reported — you logged this in Endo-App." explanation. The clinician sees a badge the legend
never defines. **Fix:** Fold symptom provenance into the legend/summary aggregation, e.g. include
`inRange.map { $0.symptoms.isEmpty ? nil : ValueSource.manual }` in `allFieldSources` (or derive the
legend from the per-section sources actually rendered, including `symptoms.source`).

### WR-03: The chart embeds fabricated per-day dates and interpolated severities, not the real logged series

**File:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift:321-337`
**Issue:** `chartPoints(for:)` does not use the real logged points. It synthesizes
`row.daysLogged` points spaced every 2 days back from `today` (`value: -(count-1-i)*2`) and
*interpolates* severity linearly between `minSeverity` and `maxSeverity`
(`Double(minSeverity) + (max-min) * i/(count-1)`). The plotted dates are invented (every-other-day
from today, regardless of when the symptom was actually logged) and the plotted severities are a
fabricated linear ramp the user never recorded. This directly violates the honesty contract the file
header claims ("no fabricated gap fill", "never fabricating values between logged days") and the
`InsightTrendChart` invariant ("an unlogged day is NEVER value:0; honest gaps"). A clinician reading
the embedded chart sees a smooth trend that is not the data. The comment even concedes "the honest
evidence count is `row.daysLogged`, surfaced verbatim in the table" — i.e. the chart itself is
acknowledged as not-honest. **Fix:** Plot the *actual* per-day symptom points from the in-range
snapshots (carry the real `[(date, severity)]` series into `SymptomRow`/`SymptomsSection` so the
renderer feeds genuine dates+values to `InsightTrendChart`), or omit the chart entirely and keep the
honest table. Do not synthesize dates/values.

### WR-04: Report assembly runs on the main actor, contradicting the "heavy work off-main" design and blocking the UI on large ranges

**File:** `Sources/EndoApp/Features/Report/ReportBuilderView.swift:167-183`
**Issue:** The comment claims "Heavy assemble work runs first; only the brief render touches the main
actor (RESEARCH Pitfall 4)." But `generate()` is a method on a SwiftUI `View` (main-actor isolated),
and the unstructured `Task { … }` **inherits** the enclosing main-actor isolation, so
`try ReportModel.assemble(...)` executes on the main actor. `assemble` performs a full
`repository.daySnapshots()` fetch plus one `daySnapshotSources(on:)` store round-trip **per in-range
day** (`ReportModel.swift:234`) — for a 90-day populated range that is ~90 synchronous SwiftData
reads on the main thread, freezing the UI while the "Generating…" caption claims non-blocking
progress. The `await MainActor.run { … }` around the render is then a redundant same-actor hop.
**Fix:** Run the pure assembly off-main, e.g. capture a `Sendable` repository seam and use
`await Task.detached { try ReportModel.assemble(...) }.value` (or an `actor`/`nonisolated` boundary),
then hop to `MainActor` only for `renderToTempFile`. Note `SwiftDataRepository`/`ModelContext` is not
`Sendable`, so this requires the same dedicated-context/box pattern used in `AppDependencies`.

### WR-05: `renderToTempFile` swallows write failure and returns a URL to a missing/partial file — preview and share then fail with no error surfaced

**File:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift:87-98`
**Issue:** On a write failure the renderer logs the error type and *still returns the URL* to a file
that was never written. The caller (`ReportBuilderView.generate()`) treats any returned URL as
success (`previewURL = url; phase = .idle`), pushing `ReportPreviewView` whose `PDFDocument(url:)`
returns nil → a blank preview, and `ShareLink` shares a non-existent file — with the honest `.failed`
error state never shown. **Fix:** Make `renderToTempFile` return `URL?` (or throw) and have
`generate()` route a nil/failure to `phase = .failed`:

```swift
static func renderToTempFile(model: ReportModel) -> URL? {
    let data = render(model: model)
    let url = …
    do { try data.write(to: url, options: .atomic) } catch {
        logger.error("PDF temp-file write failed: \(type(of: error))")
        return nil
    }
    return url
}
```

### WR-06: Two-pass pagination renders the `ImageRenderer` chart twice and can drift page count vs. draw

**File:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift:79-83, 303-317`
**Issue:** `render(model:)` runs `layoutPass` twice — once to count pages, once to draw. `drawChart`
guards its actual `ImageRenderer.render` work behind `if let ctx = cursor.context`, so the count pass
*skips* the chart draw but still advances `cursor.y` by `chartBox.height` (line 318) — good, heights
match. However the count pass reserves `chartBox.height` via `ensureRoom` (line 293) using only the
fixed 180pt box, while the draw pass reserves the same box, so page breaks are consistent *for the
chart*. The real fragility is `ensureRoom` for variable-height text: `measure()` depends on
`UIFont`/CoreText layout which is deterministic, so this is likely stable, but `drawChart`'s
`points.map(\.date).max() ?? .now` (line 300) reads `.now` — a *non-deterministic* input — inside the
draw pass only. If `chartPoints` ever returns a different count between passes due to wall-clock
movement across midnight, the two passes diverge and "Page n of m" can be wrong or content can land
on the wrong page. **Fix:** Compute `asOf`/`today` once (from the model, e.g. `model.range.end`) and
thread it into `chartPoints`/the chart so both passes are byte-identical; never read `.now` inside a
layout pass.

### WR-07: Cycle section source-tag suppression hardcodes `.none`, hiding bleeding provenance even when flow was self-reported/imported

**File:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift:139, 147, 151`
**Issue:** The Cycle (4), Patterns (6), and Legend (7) headings are drawn with `source: .none`,
unconditionally suppressing any badge. For Cycle this is a provenance gap: the cycle section is
derived from bleeding/flow days, and `daySnapshotSources` tracks `flow` provenance
(`Repository.swift:619`), which can be `.appleHealth` (imported menstrual flow) or `.manual`. The
report therefore never tells the clinician whether the cycle estimate rests on self-reported vs.
imported bleeding — an RPRT-02 source-labeling gap for the one section most likely to carry imported
data. **Fix:** Carry a `source: SectionSource` on `CycleSection`/the model (aggregated from the
in-range `flow` sources) and pass it to `drawSectionHeading` instead of `.none`, consistent with the
symptoms/lifestyle sections.

## Info

### IN-01: Stale "Task 1 stub" header comment — `assemble` is fully implemented but the doc says it throws `notImplemented`

**File:** `Sources/EndoApp/Features/Report/ReportModel.swift:28-30, 175-181, 195-196`
**Issue:** The file header still reads "this file defines ONLY the CONTRACT (types + a stubbed
`assemble` that throws). Task 2 implements `assemble`…", `AssemblyError.notImplemented` is documented
as the stub's throw, and `assemble`'s doc-comment says "STUBBED in Task 1 — throws
`AssemblyError.notImplemented`." The code is fully implemented and never throws `notImplemented`
(it can only rethrow repository errors). The misleading comments and the now-dead `AssemblyError`
enum should be removed/updated. **Fix:** Rewrite the header/doc to describe the implemented assembly;
delete `AssemblyError` if it is genuinely unused (verify no test references it).

### IN-02: `Confidence.none` is silently mapped to "Confidence: low" in the PDF

**File:** `Sources/EndoApp/Features/Report/ReportCopy.swift:99-106`
**Issue:** `cycleConfidenceWord` maps both `.low` and `.none` to "Confidence: low". A `.derived`
cycle with `.none` confidence (which `CycleMath` can produce) is presented as "low" rather than its
own honest word. This is a minor honesty rounding — `.none` arguably should not reach the derived
branch at all, or should read distinctly. **Fix:** Confirm `.derived` is only reached with
confidence ≥ `.low` in `CycleMath`, or give `.none` its own copy.

### IN-03: `PDFKitView.updateUIView` will not reload when the document failed to load initially

**File:** `Sources/EndoApp/Features/Report/PDFKitView.swift:26-31`
**Issue:** `updateUIView` reloads only when `view.document?.documentURL != url`. If the initial
`PDFDocument(url:)` returned `nil` (e.g. the file was not yet written), `view.document` stays nil and
`documentURL` is nil ≠ url forever, so a later update *would* retry — acceptable — but if it loaded a
*different* nil-document state the guard is brittle. Low impact given the URL is stable per
generation. **Fix:** Reload when `view.document == nil || view.document?.documentURL != url`.

### IN-04: `directionWord` / `chartPoints` recompute a direction the model already knows nothing about

**File:** `Sources/EndoApp/Features/Report/ReportPDFRenderer.swift:339-348`
**Issue:** `directionWord` derives up/down/steady from the *fabricated* chart points (see WR-03), so
the chart caption's trend word is computed from synthesized data, not the real series. Once WR-03 is
fixed to use real points this becomes correct; flagging so it is addressed together. **Fix:** Derive
the direction from the real logged series (or reuse the engine's direction word) rather than the
synthesized points.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
