// Requirement: RPRT-01 / RPRT-03 (Phase 10, D-06/D-07/D-09) — the on-screen report builder (Surface 1).
//
// Reached from the Settings "Export report" row (`settings.report.entry`). A `Form` (NOT a custom layout)
// pushed onto the shell's NavigationStack. The user picks a date range — a preset (Last 30 / Last 90 days)
// or a Custom range via two DatePickers — and taps "Generate PDF" to produce the report ON-DEVICE:
//   1. resolve the `DateInterval` via `ReportRangeMapping.interval` (A1),
//   2. assemble the PURE `ReportModel` from a range-filtered `SwiftDataRepository` + the LOCKED
//      `InsightsEngine` (injected as a closure so the gate is reused verbatim, never re-implemented),
//   3. render it to a LOCAL temp-file via the `@MainActor ReportPDFRenderer` (Plan 02),
//   4. push `ReportPreviewView` bound to that URL.
//
// HONESTY: the builder fabricates nothing — a sparse/empty range yields an honest, mostly-empty PDF (the
// model's per-section "Not enough data" lines), never a fake full report. A Custom range with To < From
// keeps Generate DISABLED with an inline guard message (`ReportRangeMapping.isValid`).
//
// PRIVACY (T-10-09 / V8): before each fresh generation we DELETE any prior temp PDF (the consumer
// `ReportPreviewView` also deletes on dismiss — both ends clean up the Art.9 throwaway file).
//
// NO networking anywhere in this module (RPRT-03) — asserted by ReportNoNetworkTests.
import SwiftUI
import EndoInsights

/// The on-screen report builder (Surface 1). Pure-SwiftUI `Form`; all fixed copy comes from `ReportCopy`;
/// accent is reserved for the Generate CTA + the selected preset segment (UI-SPEC §Color).
struct ReportBuilderView: View {
    @Environment(\.modelContext) private var modelContext

    /// TEST-ONLY: `-uitest-report-invalid-range` seeds the builder into an INVERTED custom range
    /// (To before From) so `ReportFlowUITests` can assert the invalid-range guard deterministically
    /// without driving the compact `DatePicker` calendar (brittle/locale-dependent in XCUITest).
    /// Inert in production — only consulted in the @State defaults below.
    private static var uitestInvalidRange: Bool {
        ProcessInfo.processInfo.arguments.contains("-uitest-report-invalid-range")
    }

    /// The chosen range preset (default: the 30-day preset — the most common clinician window).
    @State private var preset: RangePreset = ReportBuilderView.uitestInvalidRange ? .custom : .last30
    /// The custom-range bounds (only consulted when `preset == .custom`). Defaults to a ~30-day window;
    /// under the test seam, From = today and To = a week earlier → an intentionally invalid (To < From) range.
    @State private var customFrom: Date = ReportBuilderView.uitestInvalidRange
        ? .now
        : (Calendar.current.date(byAdding: .day, value: -30, to: .now) ?? .now)
    @State private var customTo: Date = ReportBuilderView.uitestInvalidRange
        ? (Calendar.current.date(byAdding: .day, value: -7, to: .now) ?? .now)
        : .now

    /// The generation lifecycle. `.idle` → tap Generate → `.generating` (non-blocking caption) →
    /// `.ready(url)` (push the preview) or `.failed` (honest error + retry). Never a fabricated result.
    @State private var phase: Phase = .idle
    /// Drives the navigation push to `ReportPreviewView` once a temp-file URL is ready.
    @State private var previewURL: URL?

    private enum Phase: Equatable {
        case idle
        case generating
        case failed
    }

    var body: some View {
        Form {
            presetSection
            if preset == .custom { customRangeSection }
            generateSection
        }
        .navigationTitle(ReportCopy.builderTitle)
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier("report.builder")
        // Push the preview when a URL is ready; clearing it (pop) lets the preview's onDisappear delete it.
        .navigationDestination(item: $previewURL) { url in
            ReportPreviewView(url: url)
        }
    }

    // MARK: - Sections

    /// Section 1 — the range presets (segmented Picker; the selected segment is tinted accent per UI-SPEC).
    private var presetSection: some View {
        Section {
            Picker(ReportCopy.builderTitle, selection: $preset) {
                Text(ReportCopy.presetLast30).tag(RangePreset.last30)
                Text(ReportCopy.presetLast90).tag(RangePreset.last90)
                Text(ReportCopy.presetCustom).tag(RangePreset.custom)
            }
            .pickerStyle(.segmented)
            .tint(Color.accentColor)   // accent reserved for the selected preset segment (UI-SPEC §Color)
            .accessibilityIdentifier("report.range.preset")
        }
    }

    /// Section 2 — the conditional Custom-range DatePickers + the inline invalid-range guard message.
    private var customRangeSection: some View {
        Section {
            DatePicker(ReportCopy.rangeFromLabel, selection: $customFrom, displayedComponents: .date)
                .accessibilityIdentifier("report.range.from")
            DatePicker(ReportCopy.rangeToLabel, selection: $customTo, displayedComponents: .date)
                .accessibilityIdentifier("report.range.to")
            if !isRangeValid {
                // Inline honest guard: To precedes From → Generate stays disabled (no fabricated range).
                Text(ReportCopy.invalidRange)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("report.range.invalid")
            }
        }
    }

    /// Section 3 — the prominent Generate CTA (accent), a non-blocking generating caption, or the honest
    /// error state with a retry. The screen is NEVER blocked while generating (UI-SPEC §States).
    @ViewBuilder
    private var generateSection: some View {
        Section {
            switch phase {
            case .generating:
                HStack(spacing: 12) {
                    ProgressView()
                    Text(ReportCopy.generatingCaption)
                        .foregroundStyle(.secondary)
                }
                .accessibilityIdentifier("report.generating")
            case .failed:
                VStack(alignment: .leading, spacing: 8) {
                    Text(ReportCopy.errorHeading).font(.headline)
                    Text(ReportCopy.errorBody)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Button(ReportCopy.errorRetry) { generate() }
                        .foregroundStyle(Color.accentColor)
                        .accessibilityIdentifier("report.generate.retry")
                }
            case .idle:
                Button(ReportCopy.generateCTA) { generate() }
                    .foregroundStyle(Color.accentColor)   // accent reserved for the Generate CTA (UI-SPEC)
                    .disabled(!isRangeValid)
                    .accessibilityIdentifier("report.generate")
            }
        }
    }

    // MARK: - Range validity

    /// The invalid-range guard (drives the Generate-disabled state). Only the Custom path can be invalid.
    private var isRangeValid: Bool {
        ReportRangeMapping.isValid(preset: preset, from: customFrom, to: customTo)
    }

    // MARK: - Generation

    /// Generate the PDF on-device (RPRT-01): resolve the range → assemble the pure model → render to a
    /// temp file → push the preview. Heavy assemble work runs first; only the brief render touches the main
    /// actor (RESEARCH Pitfall 4). Deletes any prior temp file first (T-10-09 / V8). Honest failure state.
    private func generate() {
        guard isRangeValid else { return }
        // Delete any prior temp PDF before a fresh generation (belt + braces with the preview's cleanup).
        if let prior = previewURL {
            try? FileManager.default.removeItem(at: prior)
            previewURL = nil
        }
        phase = .generating

        // The repository + interval are captured on the main actor (modelContext is main-actor-bound).
        let repo = SwiftDataRepository(context: modelContext)
        let range = ReportRangeMapping.interval(
            for: preset, from: customFrom, to: customTo, asOf: .now)

        Task {
            do {
                // Assemble the PURE model (range-filtered reads + the LOCKED engine reused verbatim as a
                // closure; `dismissed` comes from the store). `assemble` is pure value-in/value-out.
                let model = try ReportModel.assemble(
                    range: range,
                    repository: repo,
                    engine: { snapshots, asOf in
                        InsightsEngine.insights(
                            from: snapshots,
                            asOf: asOf,
                            dismissed: (try? repo.dismissedSignalKeys()) ?? [])
                    },
                    asOf: range.end)

                // The render is @MainActor (ImageRenderer requirement) — hop for the brief draw only.
                let url = await MainActor.run { ReportPDFRenderer.renderToTempFile(model: model) }
                previewURL = url
                phase = .idle   // reset the CTA; the preview is now pushed via navigationDestination
            } catch {
                // Honest failure (never a fabricated PDF). Only the error TYPE matters; no health content.
                phase = .failed
            }
        }
    }
}
