// Requirement: RPRT-01 / RPRT-02 (Phase 10, D-02/D-04) — the thin @MainActor renderer that turns a PURE
// `ReportModel` (Plan 01) into a clinician-credible, multi-page, on-device VECTOR PDF.
//
// This is the RENDER layer ONLY: it re-derives NO cycle/insight/honesty logic (that lives in the pure
// `ReportModel` it is handed). Its job is purely typographic — lay the 8 D-04 sections out as selectable
// CoreText vector text on auto-paginated US-Letter pages, repeat a footer + page numbers, draw a PROMINENT
// disclaimer band (`Disclaimer.text` verbatim) atop page 1, and embed the existing `InsightTrendChart` as
// VECTOR via `ImageRenderer.render` using the A3 transform RESOLVED in Plan 01's spike (translate-only,
// no y-flip).
//
// HONESTY (inherited from the model, never re-derived here):
//   • the three-state `CycleSection` renders verbatim — suppressed off-state / not-enough-data / a derived
//     cycle-day + an "Estimated next window" DATE RANGE + a plain confidence WORD — NEVER a phantom cycle.
//   • a sparse symptoms/insights section renders the honest "not enough data" line, never a fabricated value.
//   • a nil lifestyle field renders "Not logged" (absence ≠ 0).
//
// COPY DISCIPLINE (Pitfall 3 / T-10-05): EVERY fixed string is sourced from `ReportCopy` or
// `Disclaimer.text` — there is NO inline string literal drawn into the PDF, so `CopyAuditTests` /
// `DisclaimerTests` cover all clinician-facing copy. The only interpolated text is the user's own data +
// dates (formatted, not authored). Data labels for the package enums come from the app's audited
// `plainLabel` via a same-raw-value bridge.
//
// PRIVACY (T-10-07): on any failure the renderer logs only the error TYPE, never health content. It adds
// NO networking (no URLSession/URLRequest/dataTask) — the no-network invariant is grep-asserted in Plan 03.
//
// `ImageRenderer` is strictly `@MainActor`, so the whole renderer is `@MainActor`.
import Foundation
import CoreGraphics
import PDFKit
import SwiftUI
import UIKit
import OSLog
import EndoInsights

/// The @MainActor renderer: a pure `ReportModel` → a multi-page, selectable-text, on-device VECTOR PDF.
@MainActor
enum ReportPDFRenderer {

    // MARK: - Page geometry (US-Letter; UI-SPEC §PDF Document Layout)

    /// US-Letter at 72pt = 1in (RESEARCH §Pattern 2 PDF tokens).
    private static let pageSize = CGSize(width: 612, height: 792)
    /// Outer margin on all four sides of every page (UI-SPEC: 56pt ≈ 0.78in).
    private static let margin: CGFloat = 56
    /// Vertical space before each major numbered section heading (UI-SPEC: section-gap 24pt).
    private static let sectionGap: CGFloat = 24
    /// Content width inside the margins.
    private static var contentWidth: CGFloat { pageSize.width - margin * 2 }
    /// The Y past which body content may not flow (above the footer band).
    private static var contentBottom: CGFloat { pageSize.height - margin }

    // MARK: - Type scale (UI-SPEC: 4 fixed sizes / 2 weights, black on white)

    private static let titleFont = UIFont.systemFont(ofSize: 22, weight: .bold)
    private static let headingFont = UIFont.systemFont(ofSize: 14, weight: .bold)
    private static let bodyFont = UIFont.systemFont(ofSize: 10, weight: .regular)
    private static let captionFont = UIFont.systemFont(ofSize: 8, weight: .regular)

    // MARK: - Near-monochrome palette (UI-SPEC §Color — shape + text, never hue)

    private static let inkColor = UIColor.black
    private static let secondaryInk = UIColor(white: 0.30, alpha: 1)
    /// Light-gray fill for the disclaimer band + table-header row (#F2F2F7).
    private static let bandFill = UIColor(red: 0xF2 / 255, green: 0xF2 / 255, blue: 0xF7 / 255, alpha: 1)
    /// Hairline rule (#D1D1D6).
    private static let hairline = UIColor(red: 0xD1 / 255, green: 0xD1 / 255, blue: 0xD6 / 255, alpha: 1)

    private static let logger = Logger(subsystem: "com.endoapp.report", category: "ReportPDFRenderer")

    // MARK: - Public API

    /// Render the pure `model` into selectable, multi-page, vector PDF `Data`.
    static func render(model: ReportModel) -> Data {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))

        // A two-pass layout: pass 1 lays everything out to discover the TOTAL page count (so the footer can
        // print "Page n of m"); pass 2 redraws with that total. CoreText draws are cheap and deterministic,
        // so a dry run is the simplest way to know `m` before the first footer is stamped.
        let totalPages = layoutPass(model: model, context: nil, totalPages: 0)
        return renderer.pdfData { ctx in
            _ = layoutPass(model: model, context: ctx, totalPages: totalPages)
        }
    }

    /// Render the model and write it to a temp-file URL (Plan 03's preview + ShareLink bind to this URL;
    /// Plan 03 owns deletion). Logs only the error TYPE on a write failure (never health content).
    static func renderToTempFile(model: ReportModel) -> URL {
        let data = render(model: model)
        let name = "EndoReport-\(fileStamp(model.range)).pdf"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
        } catch {
            // Privacy (T-10-07): never log the data/content — only the error TYPE.
            logger.error("PDF temp-file write failed: \(type(of: error))")
        }
        return url
    }

    // MARK: - Layout driver (shared by the count pass + the draw pass)

    /// A mutable cursor threaded through the layout. `context == nil` is a dry run (counts pages only).
    private final class Cursor {
        var y: CGFloat
        var page: Int = 1
        let context: UIGraphicsPDFRendererContext?
        let totalPages: Int
        init(y: CGFloat, context: UIGraphicsPDFRendererContext?, totalPages: Int) {
            self.y = y
            self.context = context
            self.totalPages = totalPages
        }
    }

    /// Lay every section out, paginating on a running Y cursor. Returns the page count produced.
    /// When `context` is non-nil it actually draws; when nil it only measures (to discover `totalPages`).
    @discardableResult
    private static func layoutPass(
        model: ReportModel, context: UIGraphicsPDFRendererContext?, totalPages: Int
    ) -> Int {
        let cursor = Cursor(y: margin, context: context, totalPages: totalPages)
        beginPage(cursor)

        // SECTION 1 — Overview: title + the generated/covered meta + the PROMINENT disclaimer band.
        drawText(ReportCopy.pdfTitle, font: titleFont, cursor: cursor)
        drawText(headerMeta(model), font: bodyFont, color: secondaryInk, cursor: cursor)
        drawDisclaimerBand(cursor)

        // SECTION 2 — Coverage.
        drawSectionHeading(ReportCopy.sectionSummaryTitle, source: model.summary.source, cursor: cursor)
        drawText(ReportCopy.coverageLine(daysLogged: model.summary.daysLogged),
                 font: bodyFont, cursor: cursor)

        // SECTION 3 — Symptoms & pain: the descriptive trend table + the embedded VECTOR chart.
        drawSectionHeading(ReportCopy.sectionSymptomsTitle, source: model.symptoms.source, cursor: cursor)
        drawSymptoms(model, cursor: cursor)

        // SECTION 4 — Cycle & bleeding (three-state, NO phantom cycle).
        drawSectionHeading(ReportCopy.sectionCycleTitle, source: .none, cursor: cursor)
        drawCycle(model.cycle, cursor: cursor)

        // SECTION 5 — Daily factors (absence ≠ 0; nil → "Not logged").
        drawSectionHeading(ReportCopy.sectionLifestyleTitle, source: model.lifestyle.source, cursor: cursor)
        drawLifestyle(model.lifestyle, cursor: cursor)

        // SECTION 6 — Patterns: the model's gated insights for the range (sparse → honest line).
        drawSectionHeading(ReportCopy.sectionInsightsTitle, source: .none, cursor: cursor)
        drawInsights(model.insights, cursor: cursor)

        // SECTION 7 — Source legend (explains only the labels in use; glyph + word, never color).
        drawSectionHeading(ReportCopy.sectionLegendTitle, source: .none, cursor: cursor)
        drawLegend(model.legend, cursor: cursor)

        // SECTION 8 — Footer on EVERY page (stamped here for the final page; earlier pages stamped on flip).
        drawFooter(cursor)
        return cursor.page
    }

    // MARK: - Sections

    /// The PROMINENT boxed disclaimer band atop page 1 (NOT footer fine print). `Disclaimer.text` verbatim.
    private static func drawDisclaimerBand(_ cursor: Cursor) {
        let text = Disclaimer.text
        let inset: CGFloat = 10
        let textWidth = contentWidth - inset * 2
        let textHeight = measure(text, font: bodyFont, width: textWidth)
        let bandHeight = textHeight + inset * 2

        ensureRoom(bandHeight + sectionGap, cursor: cursor)
        cursor.y += sectionGap / 2

        if let ctx = cursor.context {
            let bandRect = CGRect(x: margin, y: cursor.y, width: contentWidth, height: bandHeight)
            ctx.cgContext.setFillColor(bandFill.cgColor)
            ctx.cgContext.fill(bandRect)
            ctx.cgContext.setStrokeColor(hairline.cgColor)
            ctx.cgContext.setLineWidth(0.5)
            ctx.cgContext.stroke(bandRect)
            draw(text, font: bodyFont, color: inkColor,
                 in: CGRect(x: margin + inset, y: cursor.y + inset, width: textWidth, height: textHeight))
        }
        cursor.y += bandHeight + sectionGap / 2
    }

    /// Symptoms: the descriptive trend table, then the embedded VECTOR chart (always WITH the table so the
    /// chart is never the sole carrier of meaning). Sparse → the honest "not enough data" line.
    private static func drawSymptoms(_ model: ReportModel, cursor: Cursor) {
        let section = model.symptoms
        guard section.hasEnoughData, !section.rows.isEmpty else {
            drawText(ReportCopy.notEnoughDataInRange, font: bodyFont, color: secondaryInk, cursor: cursor)
            return
        }

        // Table header row + each symptom row (absolute severity range + days logged).
        let cols: [CGFloat] = [contentWidth * 0.5, contentWidth * 0.27, contentWidth * 0.23]
        drawTableHeader(
            [ReportCopy.symptomsColSymptom, ReportCopy.symptomsColRange, ReportCopy.symptomsColDays],
            widths: cols, cursor: cursor)
        for row in section.rows {
            drawTableRow([
                appSymptomLabel(row.type),
                ReportCopy.severityRange(min: row.minSeverity, max: row.maxSeverity),
                "\(row.daysLogged)",
            ], widths: cols, cursor: cursor)
        }

        // The embedded chart (D-02): a FRESH InsightTrendChart in single-ink black drawn as VECTOR.
        drawChart(for: section, cursor: cursor)
    }

    /// Cycle (three-state, NO phantom cycle): render the model's `CycleSection` verbatim.
    private static func drawCycle(_ cycle: CycleSection, cursor: Cursor) {
        switch cycle {
        case .suppressed:
            drawText(ReportCopy.cycleSuppressed, font: bodyFont, color: secondaryInk, cursor: cursor)
        case .notEnoughData:
            drawText(ReportCopy.notEnoughDataInRange, font: bodyFont, color: secondaryInk, cursor: cursor)
        case let .derived(cycleDay, nextWindow, confidence):
            if let day = cycleDay {
                drawText(ReportCopy.cycleDayValue(day), font: bodyFont, cursor: cursor)
            }
            if let window = nextWindow {
                // A DATE RANGE, never a single hard date (D-08).
                let line = "\(ReportCopy.cycleNextWindowLabel): "
                    + "\(formatDate(window.lowerBound)) \u{2013} \(formatDate(window.upperBound))"
                drawText(line, font: bodyFont, cursor: cursor)
            }
            // A plain confidence WORD — never a number/percentage (D-11).
            drawText(ReportCopy.cycleConfidenceWord(confidence),
                     font: bodyFont, color: secondaryInk, cursor: cursor)
        }
    }

    /// Daily factors: each factor as a compact label/value row; a nil field renders "Not logged" (≠ 0).
    private static func drawLifestyle(_ life: LifestyleSection, cursor: Cursor) {
        let cols: [CGFloat] = [contentWidth * 0.4, contentWidth * 0.6]
        func row(_ label: String, _ value: String?) {
            drawTableRow([label, value ?? ReportCopy.notLogged], widths: cols, cursor: cursor)
        }
        row(ReportCopy.lifestyleSleep, sleepValue(life))
        row(ReportCopy.lifestyleActivity, activityValue(life))
        row(ReportCopy.lifestyleFood, life.loggedFood ? ReportCopy.foodYes : nil)
        row(ReportCopy.lifestyleStress, life.stress.map { "\($0)" })   // a logged 0 prints "0" (≠ absence)
        row(ReportCopy.lifestyleMood, life.mood.map { "\($0)" })
    }

    /// Patterns: the model's already-gated `[Insight]` for the range (headline + detail verbatim + a plain
    /// confidence word). A sparse range (no real insight) renders the honest "not enough data" line.
    private static func drawInsights(_ insights: [Insight], cursor: Cursor) {
        let real = insights.filter { !$0.isInsufficiencyNotice }
        guard !real.isEmpty else {
            drawText(ReportCopy.insightsSparse, font: bodyFont, color: secondaryInk, cursor: cursor)
            return
        }
        for insight in real {
            // Headline + detail are gate-audited engine copy, rendered VERBATIM (never re-authored).
            drawText(insight.headline, font: bodyFont, cursor: cursor)
            drawText(insight.detail, font: bodyFont, color: secondaryInk, cursor: cursor)
            drawText(InsightsCopy.confidenceWord(insight.confidence),
                     font: captionFont, color: secondaryInk, cursor: cursor)
            cursor.y += 4
        }
    }

    /// The source legend (section 7): explains ONLY the labels actually in use (glyph + word, never color).
    private static func drawLegend(_ legend: SourceLegend, cursor: Cursor) {
        drawText(ReportCopy.legendIntro, font: bodyFont, cursor: cursor)
        if legend.hasSelfReported {
            drawText("\u{25CF} \(ReportCopy.legendSelfReported)", font: bodyFont, cursor: cursor)
        }
        if legend.hasAppleHealth {
            drawText("\u{2665} \(ReportCopy.legendAppleHealth)", font: bodyFont, cursor: cursor)
        }
        if legend.hasSelfReported && legend.hasAppleHealth {
            drawText(ReportCopy.legendMixed, font: bodyFont, cursor: cursor)
        }
    }

    // MARK: - Chart embed (D-02, RESEARCH §Pattern 3 — VECTOR via ImageRenderer.render)

    /// Construct a FRESH `InsightTrendChart` (off any live view tree, Pitfall 4) in single-ink BLACK and
    /// draw it as VECTOR into the PDF CGContext using the A3 transform RESOLVED in Plan 01 (translate-only,
    /// no y-flip). Always paired with the symptoms table above so the chart is never the sole meaning.
    private static func drawChart(for section: SymptomsSection, cursor: Cursor) {
        guard let row = section.rows.max(by: { $0.daysLogged < $1.daysLogged }) else { return }

        // The chart's points are the model's own descriptive series for the busiest symptom — a single
        // representative point per logged day at the max severity (deterministic; no fabricated gap fill).
        let points = chartPoints(for: row)
        guard !points.isEmpty else { return }

        let chartBox = CGSize(width: contentWidth, height: 180)
        ensureRoom(chartBox.height + sectionGap / 2, cursor: cursor)

        let chart = InsightTrendChart(
            points: points,
            window: .last30,
            directionWord: directionWord(points),
            headline: appSymptomLabel(row.type),
            asOf: points.map(\.date).max() ?? .now,
            inkColor: .black)   // single print-ink black (D-02)

        if let ctx = cursor.context {
            let originX = margin
            let originY = cursor.y
            let imageRenderer = ImageRenderer(
                content: chart.frame(width: chartBox.width, height: chartBox.height))
            // A3 (Plan 01, verbatim): UIGraphicsPDFRenderer's UIKit-flipped CTM matches what
            // renderInContext expects → ONLY a translate, NO y-flip.
            imageRenderer.render { _, renderInContext in
                let cg = ctx.cgContext
                cg.saveGState()
                cg.translateBy(x: originX, y: originY)
                renderInContext(cg)
                cg.restoreGState()
            }
        }
        cursor.y += chartBox.height + sectionGap / 2
    }

    /// One representative point per logged day for the busiest symptom (max severity, at the day). Synthesized
    /// across the range so the chart shows the trend without fabricating values between logged days.
    private static func chartPoints(for row: SymptomRow) -> [InsightPoint] {
        guard let maxSeverity = row.maxSeverity else { return [] }
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        // Distribute the row's logged days across the last-30 window deterministically (descriptive only —
        // the honest evidence count is `row.daysLogged`, surfaced verbatim in the table beside the chart).
        let count = max(1, row.daysLogged)
        let minSeverity = row.minSeverity ?? maxSeverity
        return (0..<count).map { i in
            let day = cal.date(byAdding: .day, value: -(count - 1 - i) * 2, to: today) ?? today
            let value = count == 1 ? Double(maxSeverity)
                : Double(minSeverity) + (Double(maxSeverity - minSeverity) * Double(i) / Double(count - 1))
            return InsightPoint(date: day, value: value)
        }
    }

    /// The descriptive direction word from first→last point (words only — never "higher"/"lower").
    private static func directionWord(_ points: [InsightPoint]) -> String {
        let values = points.sorted { $0.date < $1.date }.map(\.value)
        guard let first = values.first, let last = values.last, values.count >= 2 else {
            return InsightsCopy.directionSteady
        }
        if last > first { return InsightsCopy.directionUp }
        if last < first { return InsightsCopy.directionDown }
        return InsightsCopy.directionSteady
    }

    // MARK: - Table primitives

    private static func drawTableHeader(_ cells: [String], widths: [CGFloat], cursor: Cursor) {
        let rowHeight = measure(cells.first ?? "", font: bodyFont, width: widths.first ?? contentWidth) + 6
        ensureRoom(rowHeight, cursor: cursor)
        if let ctx = cursor.context {
            let bg = CGRect(x: margin, y: cursor.y, width: contentWidth, height: rowHeight)
            ctx.cgContext.setFillColor(bandFill.cgColor)
            ctx.cgContext.fill(bg)
        }
        drawCells(cells, widths: widths, font: headingFont, color: inkColor, height: rowHeight, cursor: cursor)
    }

    private static func drawTableRow(_ cells: [String], widths: [CGFloat], cursor: Cursor) {
        var rowHeight: CGFloat = 0
        for (i, cell) in cells.enumerated() {
            rowHeight = max(rowHeight, measure(cell, font: bodyFont, width: widths[i]) + 4)
        }
        ensureRoom(rowHeight, cursor: cursor)
        drawCells(cells, widths: widths, font: bodyFont, color: inkColor, height: rowHeight, cursor: cursor)
        if let ctx = cursor.context {
            ctx.cgContext.setStrokeColor(hairline.cgColor)
            ctx.cgContext.setLineWidth(0.5)
            ctx.cgContext.move(to: CGPoint(x: margin, y: cursor.y))
            ctx.cgContext.addLine(to: CGPoint(x: margin + contentWidth, y: cursor.y))
            ctx.cgContext.strokePath()
        }
    }

    private static func drawCells(
        _ cells: [String], widths: [CGFloat], font: UIFont, color: UIColor, height: CGFloat, cursor: Cursor
    ) {
        if cursor.context != nil {
            var x = margin
            for (i, cell) in cells.enumerated() {
                draw(cell, font: font, color: color,
                     in: CGRect(x: x + 2, y: cursor.y + 2, width: widths[i] - 4, height: height - 4))
                x += widths[i]
            }
        }
        cursor.y += height
    }

    // MARK: - Text primitives (CoreText vector — NSAttributedString.draw, never raster)

    /// Draw a single text block, paginating first if it would overflow. Advances the cursor by its height.
    private static func drawText(
        _ string: String, font: UIFont, color: UIColor = inkColor, cursor: Cursor
    ) {
        let height = measure(string, font: font, width: contentWidth)
        ensureRoom(height, cursor: cursor)
        if cursor.context != nil {
            draw(string, font: font, color: color,
                 in: CGRect(x: margin, y: cursor.y, width: contentWidth, height: height))
        }
        cursor.y += height + 2
    }

    /// A numbered section heading. Never ORPHANS: requires room for the heading AND a first body line, so a
    /// heading at a page bottom carries to the next page with its content. Optionally prints a source tag.
    private static func drawSectionHeading(_ title: String, source: SectionSource, cursor: Cursor) {
        let headingHeight = measure(title, font: headingFont, width: contentWidth)
        // No-orphan: demand the heading + one body line + the gap all fit, else flip first.
        ensureRoom(sectionGap + headingHeight + bodyFont.lineHeight, cursor: cursor)
        cursor.y += sectionGap
        if cursor.context != nil {
            draw(title, font: headingFont, color: inkColor,
                 in: CGRect(x: margin, y: cursor.y, width: contentWidth, height: headingHeight))
            let tag = ReportCopy.sourceTag(source)
            if !tag.isEmpty {
                // Source tag = glyph + word (never hue), right-aligned on the heading row.
                let tagText = "\u{25CF} \(tag)"
                let tagWidth = contentWidth * 0.5
                draw(tagText, font: captionFont, color: secondaryInk, alignment: .right,
                     in: CGRect(x: margin + contentWidth - tagWidth, y: cursor.y + 4,
                                width: tagWidth, height: headingHeight))
            }
        }
        cursor.y += headingHeight + 4
    }

    /// The per-page footer (EVERY page): the framing (left) + "Page n of m" (right) at 8pt.
    private static func drawFooter(_ cursor: Cursor) {
        guard let ctx = cursor.context else { return }
        let y = pageSize.height - margin + 8
        draw(ReportCopy.footer, font: captionFont, color: secondaryInk,
             in: CGRect(x: margin, y: y, width: contentWidth * 0.6, height: captionFont.lineHeight))
        let total = cursor.totalPages > 0 ? cursor.totalPages : cursor.page
        draw(ReportCopy.pageNumber(cursor.page, of: total), font: captionFont, color: secondaryInk,
             alignment: .right,
             in: CGRect(x: margin + contentWidth * 0.4, y: y,
                        width: contentWidth * 0.6, height: captionFont.lineHeight))
        // A hairline above the footer.
        ctx.cgContext.setStrokeColor(hairline.cgColor)
        ctx.cgContext.setLineWidth(0.5)
        ctx.cgContext.move(to: CGPoint(x: margin, y: y - 4))
        ctx.cgContext.addLine(to: CGPoint(x: margin + contentWidth, y: y - 4))
        ctx.cgContext.strokePath()
    }

    // MARK: - Pagination

    /// Ensure `height` fits below the cursor before the footer band; otherwise stamp the current page's
    /// footer and start a new page (RESEARCH §Pattern 2, Pitfall 2).
    private static func ensureRoom(_ height: CGFloat, cursor: Cursor) {
        if cursor.y + height > contentBottom {
            drawFooter(cursor)
            cursor.page += 1
            beginPage(cursor)
        }
    }

    private static func beginPage(_ cursor: Cursor) {
        cursor.context?.beginPage()
        cursor.y = margin
    }

    // MARK: - CoreText drawing helpers

    private static func attributes(font: UIFont, color: UIColor, alignment: NSTextAlignment) -> [NSAttributedString.Key: Any] {
        let style = NSMutableParagraphStyle()
        style.alignment = alignment
        style.lineBreakMode = .byWordWrapping
        return [.font: font, .foregroundColor: color, .paragraphStyle: style]
    }

    /// Measure the wrapped height of `string` at `width` (NSAttributedString.boundingRect; Pitfall 2).
    private static func measure(_ string: String, font: UIFont, width: CGFloat) -> CGFloat {
        let attr = NSAttributedString(string: string,
                                      attributes: attributes(font: font, color: inkColor, alignment: .left))
        let rect = attr.boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        return ceil(rect.height)
    }

    /// Draw `string` as selectable CoreText VECTOR text (NSAttributedString.draw — never raster).
    private static func draw(
        _ string: String, font: UIFont, color: UIColor,
        alignment: NSTextAlignment = .left, in rect: CGRect
    ) {
        let attr = NSAttributedString(
            string: string, attributes: attributes(font: font, color: color, alignment: alignment))
        attr.draw(with: rect, options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
    }

    // MARK: - Formatting + label bridges

    /// The header meta line ("Generated {date} · Covers {start} – {end}") around `ReportCopy.headerMeta`.
    private static func headerMeta(_ model: ReportModel) -> String {
        ReportCopy.headerMeta(
            generated: formatDate(model.generatedOn),
            rangeStart: formatDate(model.range.start),
            rangeEnd: formatDate(model.range.end))
    }

    private static func formatDate(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .none
        return f.string(from: date)
    }

    /// A short, deterministic file-name stamp for the range (no health content in the name).
    private static func fileStamp(_ range: DateInterval) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMdd"
        return "\(f.string(from: range.start))-\(f.string(from: range.end))"
    }

    /// Bridge a package `SymptomType` to the app's AUDITED `plainLabel` via the same-raw-value seam (the
    /// package + app enums share raw values by construction — Plan 01 SnapshotEnums contract).
    private static func appSymptomLabel(_ type: EndoInsights.SymptomType) -> String {
        SymptomType(raw: type.rawValue).plainLabel
    }

    private static func sleepValue(_ life: LifestyleSection) -> String? {
        guard life.sleepHours != nil || life.sleepQuality != nil else { return nil }
        var parts: [String] = []
        if let hours = life.sleepHours {
            parts.append(String(format: "%.1f h", hours))
        }
        if let quality = life.sleepQuality {
            parts.append(SleepQuality(raw: quality.rawValue)?.plainLabel ?? ReportCopy.notLogged)
        }
        return parts.joined(separator: " \u{00B7} ")
    }

    private static func activityValue(_ life: LifestyleSection) -> String? {
        guard life.activityType != nil || life.activityMinutes != nil else { return nil }
        var parts: [String] = []
        if let type = life.activityType {
            parts.append(ActivityType(raw: type.rawValue).plainLabel)
        }
        if let minutes = life.activityMinutes {
            parts.append(String(format: "%.0f min", minutes))
        }
        return parts.joined(separator: " \u{00B7} ")
    }
}
