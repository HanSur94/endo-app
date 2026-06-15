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

    /// The bleeding-suppression flag (SCAT-04). The SCREEN supplies the latest snapshot's `suppressed`; it
    /// gates the scatter deep-link resolver so a suppressed user never reaches a bleeding axis. Defaults to
    /// `false` so previews/tests compile without it.
    var suppressed: Bool = false

    /// The scatter deep-link action the SCREEN wires (SCAT-04, the `onDismiss` precedent): tapping the
    /// `.correlation` card's "See the days behind this" affordance hands the SCREEN the resolved typed
    /// `(x, y)` axes; the screen sets the router payload + selects the Explore tab. This view carries the
    /// resolver's TYPED value — it NEVER re-derives axes from `headline`/`detail` copy (T-16-14). Optional
    /// so previews/tests can omit it.
    var onOpenScatter: (PendingScatter) -> Void = { _ in }

    /// The stable, value-INDEPENDENT a11y id root — the dismissal key (survives recomputation, D-09).
    private var idRoot: String { "insights.card.\(insight.signalKey)" }

    /// The resolved scatter axes for THIS card, computed ONCE (SCAT-04). A non-`.correlation` card never
    /// resolves; a `.correlation` card whose `signalKey` maps to two quantitative axes (and whose bleeding
    /// target survives suppression) yields the typed pair the affordance carries — otherwise `nil` and the
    /// affordance is HIDDEN. Keys off the STRUCTURAL `signalKey` only (never audited prose, T-16-14).
    private var scatterAxes: (x: ExploreSeries, y: ExploreSeries)? {
        insight.kind == .correlation
            ? ScatterDeepLink.resolve(signalKey: insight.signalKey, suppressed: suppressed)
            : nil
    }

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
        .bloomCard()
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
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
            .bloomH3()
            .fixedSize(horizontal: false, vertical: true)

        // 2. Confidence pill — text word + per-level symbol (never a number, never color alone).
        confidencePill

        // 3. Detail (verbatim).
        Text(insight.detail)
            .font(.body)
            .foregroundStyle(Color.ink)
            .fixedSize(horizontal: false, vertical: true)

        // 4. Supporting data — kind-appropriate.
        supportingData

        // 5. Basis note.
        Text(InsightsCopy.basisNote)
            .font(.caption)
            .foregroundStyle(Color.inkSecondary)
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
            .font(.subheadline)
            .foregroundStyle(Color.plum)
            .padding(.vertical, 5)
            .padding(.horizontal, 10)
            .background(Color.petal, in: Capsule())
            .overlay(Capsule().stroke(Color.petalLine, lineWidth: 1))
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
                headline: insight.headline,
                inkColor: Color.plum,                        // ← Bloom: tint the LIVE chart on-brand
                allowsSelection: true,                       // ← Phase 13: the dashboard switch-on
                selectionA11yIDRoot: "\(idRoot).chart"       //   tooltip a11y ids hang off this
            )
            .accessibilityIdentifier("\(idRoot).chart")

        case .cyclePhase, .correlation, .recommendation, .flare:
            VStack(alignment: .leading, spacing: 4) {
                // Words-only evidence — NEVER a coefficient/percentage/p-value. The `.evidence` id lives on
                // THIS text-only block (NOT the outer VStack) so it never masks the scatter-link button's
                // own id: a container `.accessibilityIdentifier` propagates to a lone interactive descendant,
                // which would expose the button AS `.evidence` (the bug this scoping fixes).
                VStack(alignment: .leading, spacing: 4) {
                    Text(InsightsCopy.supportingEvidence(loggedDays: insight.sampleSize))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(InsightsCopy.windowLabel(insight.window))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .accessibilityIdentifier("\(idRoot).evidence")

                // SCAT-04: the "See the days behind this" scatter deep-link affordance — shown ONLY when the
                // resolver returns a quantitative `(x, y)` pair (a `.correlation` card whose BOTH factors map
                // to a quantitative axis, bleeding gated by suppression). A presence-only / sleep-quality /
                // suppressed-bleeding correlation resolves to `nil` → NO affordance (HIDDEN, not disabled).
                // The button carries the resolver's TYPED axes — it NEVER reads `insight.headline`/`detail`
                // (T-16-14). Accent-tinted, ≥44pt hit area, value-independent a11y id `\(idRoot).scatterLink`.
                if let axes = scatterAxes {
                    Button {
                        onOpenScatter(PendingScatter(x: axes.x, y: axes.y))
                    } label: {
                        Label(ExploreCopy.scatterLinkLabel, systemImage: "arrow.up.forward.square")
                            .font(.subheadline)
                    }
                    .buttonStyle(.borderless)
                    .tint(.accentColor)
                    // ≥44pt touch floor (HIG) — the borderless label is text-height, so pad the hit area.
                    .frame(minHeight: 44, alignment: .leading)
                    .accessibilityIdentifier("\(idRoot).scatterLink")
                }
            }
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
