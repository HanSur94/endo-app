// Requirement: INST-01..04 / D-08 / D-09 / D-11 / T-08-11 — the kind-agnostic insight card.
//
// Renders ONE `Insight` with the UI-SPEC §B fixed top→bottom structure (cite UI-SPEC
// §"Component & Interaction Inventory" B):
//   1. Headline   — `insight.headline` VERBATIM (already gate-audited Phase 6), `.headline`, `.primary`.
//   2. Confidence — a plain-language PILL: a text word ("Low/Medium/High — possible association") PLUS
//                   a per-level SF Symbol/shape. NEVER a number (D-11); color is NEVER the sole signal
//                   (text + shape carry the level for grayscale / Smart-Invert users — T-08-11).
//   3. Detail     — `insight.detail` VERBATIM, `.body`, `.primary`.
//   4. Supporting — for `.trend`: the embedded `InsightTrendChart`. For `.cyclePhase`/`.correlation`:
//                   a words-only evidence line ("Seen across {n} logged days." + the window label) —
//                   NEVER a coefficient/percentage. For `isInsufficiencyNotice`: the sparse state
//                   (no chart, no confidence pill).
//   5. Basis      — "Based on your logs." (`.caption`, `.secondary`).
//
// Dismiss (D-09) is a PREFERENCE, not destruction: a NON-`.destructive` (gray/secondary) affordance
// exposed via BOTH `.swipeActions` AND `.contextMenu` AND a VoiceOver custom action, all wired to an
// `onDismiss` closure THE SCREEN (08-04) provides (this view never imports the Repository).
//
// Kept KIND-AGNOSTIC so Phase 11 (.recommendation) / Phase 12 (.flare) slot in unchanged.
import SwiftUI
import EndoInsights

struct InsightCardView: View {

    /// The gated insight to render. Its `headline`/`detail` are shown verbatim.
    let insight: Insight
    /// The dismiss action the SCREEN wires (→ `Repository.dismissInsight(signalKey:)`). This view does
    /// not know about persistence — it only signals intent. Optional so previews/tests can omit it.
    var onDismiss: () -> Void = {}

    /// The stable, value-INDEPENDENT a11y id root — the dismissal key (survives recomputation, D-09).
    private var idRoot: String { "insights.card.\(insight.signalKey)" }

    /// Drives the confidence-explainer popover (the ⓘ next to the pill — in-app transparency for
    /// what Low/Medium/High mean; copy from the audited `InsightsCopy` surface).
    @State private var showsConfidenceInfo = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if insight.isInsufficiencyNotice {
                // Sparse honest state: no chart, no confidence pill — an explicit "not enough data".
                sparseState
            } else {
                fullCard
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // The whole card is one VoiceOver element reading headline + confidence + detail.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(idRoot)
        .accessibilityLabel(Text(voiceOverSummary))
        // Dismiss reachable WITHOUT the swipe gesture (VoiceOver custom action).
        .accessibilityAction(named: Text(InsightsCopy.dismissAction)) { onDismiss() }
        // Discoverable + one-handed: a trailing swipe action AND a long-press context menu.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) { dismissButton }
        .contextMenu { dismissButton }
    }

    // MARK: - Full card (non-sparse)

    @ViewBuilder
    private var fullCard: some View {
        // 1. Headline (verbatim, non-truncating so honest framing never hides).
        Text(insight.headline)
            .font(.headline)
            .foregroundStyle(.primary)
            .fixedSize(horizontal: false, vertical: true)

        // 2. Confidence pill — text word + per-level symbol (never a number, never color alone).
        confidencePill

        // 3. Detail (verbatim).
        Text(insight.detail)
            .font(.body)
            .foregroundStyle(.primary)
            .fixedSize(horizontal: false, vertical: true)

        // 4. Supporting data — kind-appropriate.
        supportingData

        // 5. Basis note.
        Text(InsightsCopy.basisNote)
            .font(.caption)
            .foregroundStyle(.secondary)
    }

    // MARK: - 2. Confidence pill (text + shape, color never the sole signal — T-08-11)

    /// One LOCKED SF Symbol set, one symbol per confidence level (filled-fraction conveys the level by
    /// SHAPE, not hue). Grayscale / Smart-Invert users distinguish the three by text + shape.
    private func confidenceSymbol(_ confidence: InsightConfidence) -> String {
        switch confidence {
        case .high:           return "circle.fill"
        case .medium:         return "circle.lefthalf.filled"
        case .low, .none:     return "circle"
        }
    }

    private var confidencePill: some View {
        HStack(spacing: 6) {
            Label {
                Text(InsightsCopy.confidenceWord(insight.confidence))
                    .font(.subheadline)
            } icon: {
                Image(systemName: confidenceSymbol(insight.confidence))
            }
            .foregroundStyle(.secondary)
            .padding(.vertical, 4)
            .padding(.horizontal, 8)
            .background(.quaternary.opacity(0.5), in: Capsule())
            // The symbol is part of the pill's meaning — do NOT hide it from VoiceOver wholesale; the
            // pill's text already reads the level, so mark just the icon decorative to avoid a double-read.
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("\(idRoot).confidence")

            // The ⓘ explainer — what Low/Medium/High mean, explained IN the app (transparency, D-11
            // posture). `.borderless` keeps the tap target isolated inside the List row (the row tap /
            // swipe-to-dismiss must not fire it). Secondary tint — accent stays reserved (UI-SPEC §Color).
            Button {
                showsConfidenceInfo = true
            } label: {
                Image(systemName: "info.circle")
                    .font(.subheadline)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .accessibilityLabel(Text(InsightsCopy.confidenceInfoTitle))
            .accessibilityIdentifier("\(idRoot).confidence.info")
            .popover(isPresented: $showsConfidenceInfo, arrowEdge: .top) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(InsightsCopy.confidenceInfoTitle)
                        .font(.headline)
                    Text(InsightsCopy.confidenceInfoBody)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding()
                .frame(idealWidth: 320)
                // Stay a compact anchored bubble on iPhone (not a full sheet) — the explainer is a
                // glanceable aside, not a destination.
                .presentationCompactAdaptation(.popover)
                .accessibilityIdentifier("insights.confidence.info.popover")
            }
        }
    }

    // MARK: - 4. Supporting data (kind-appropriate; never a coefficient/percentage)

    @ViewBuilder
    private var supportingData: some View {
        switch insight.kind {
        case .trend:
            // The Swift Charts trend chart (honest gaps, absolute-date axis).
            InsightTrendChart(
                points: insight.points,
                window: insight.window,
                directionWord: directionWord,
                headline: insight.headline
            )
            .accessibilityIdentifier("\(idRoot).chart")

        case .cyclePhase, .correlation, .recommendation, .flare:
            // Words-only evidence — NEVER a coefficient/percentage/p-value.
            VStack(alignment: .leading, spacing: 4) {
                Text(InsightsCopy.supportingEvidence(loggedDays: insight.sampleSize))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(InsightsCopy.windowLabel(insight.window))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .accessibilityIdentifier("\(idRoot).evidence")
        }
    }

    /// Descriptive direction word from the trend's first→last logged value (words only; never
    /// "higher"/"lower"). Steady when flat or with fewer than two points.
    private var directionWord: String {
        let values = insight.points.sorted { $0.date < $1.date }.map(\.value)
        guard let first = values.first, let last = values.last, values.count >= 2 else {
            return InsightsCopy.directionSteady
        }
        if last > first { return InsightsCopy.directionUp }
        if last < first { return InsightsCopy.directionDown }
        return InsightsCopy.directionSteady
    }

    // MARK: - Sparse honest state (isInsufficiencyNotice == true)

    private var sparseState: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(InsightsCopy.sparseTitle)
                .font(.headline)
                .foregroundStyle(.secondary)
            Text(InsightsCopy.sparseSubtitle)
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityIdentifier("\(idRoot).insufficient")
    }

    // MARK: - Dismiss affordance (a PREFERENCE — non-destructive, gray/secondary, never red)

    private var dismissButton: some View {
        Button {
            onDismiss()
        } label: {
            Label(InsightsCopy.dismissAction, systemImage: "hand.thumbsdown")
        }
        // role is NOT .destructive — dismiss is a low-stakes, reversible preference (UI-SPEC Color note).
        .tint(.gray)
        .accessibilityIdentifier("\(idRoot).dismiss")
    }

    // MARK: - VoiceOver summary ("headline, {confidence} possible association, {detail}")

    private var voiceOverSummary: String {
        if insight.isInsufficiencyNotice {
            return "\(InsightsCopy.sparseTitle) \(InsightsCopy.sparseSubtitle)"
        }
        let confidence = InsightsCopy.confidenceWord(insight.confidence)
        return "\(insight.headline), \(confidence), \(insight.detail)"
    }
}
