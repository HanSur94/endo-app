// Requirement: EXPL-02 (chip picker + cap + suppression filter) / EXPL-06 (range presets) — the
// Explore screen's control surface.
//
// Two controls, top → bottom:
//   1. The TOGGLE-CHIP picker over the SUPPRESSION-FILTERED catalog (`ExploreSelection.offered(suppressed:)`)
//      — a suppressed user is offered NO `bleeding.intensity`/`bleeding.event` chip (T-15-11, the UI half
//      of the Phase-14 accessor gate). Tapping an unselected chip toggles it IN; tapping a selected chip
//      toggles it OUT (the remove-to-add affordance). At the 4-quantitative cap, every UNSELECTED
//      quantitative chip is DISABLED and the `ExploreCopy.capReached` helper shows; event chips stay
//      enabled (uncapped — a separate allowance).
//   2. The RANGE-preset control: a segmented `Picker` over `TrendWindow.allCases` labelled via
//      `ExploreCopy.windowLabel`, bound to `selection.window` (EXPL-06 — the dashboard vocabulary).
//
// COLOR-INDEPENDENCE (MANDATORY a11y, UI-SPEC §Color): selected vs unselected differ by FILL + a
// checkmark/`plus.circle` symbol + font WEIGHT — NEVER hue alone. A grayscale / Smart-Invert user must
// tell selected from unselected by shape + weight. The accent tint is a REDUNDANT cue, not the only one.
//
// PRIVACY (T-15-12, LOCKED): the selection lives in the SCREEN's ephemeral `@State` and is passed here by
// `@Binding`. There is intentionally NO persistent storage wrapper in this file — persisting series keys
// would leak health-interest metadata outside the consent-gated store. The grep gate forbids any such
// wrapper here.
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import EndoInsights

/// The Explore control surface: the suppression-filtered toggle-chip series picker (capped,
/// color-independent) + the `TrendWindow` segmented range control. Pure presentation over the injected
/// `@Binding` selection — owns NO state of its own.
struct SeriesPickerView: View {

    /// The screen's ephemeral selection (series Set + window), mutated through `ExploreSelection.toggle`
    /// (the cap/suppression invariants can only change through the value type's own API).
    @Binding var selection: ExploreSelection

    /// Whether the user's data is suppressed — drives the catalog filter so a suppressed user is offered
    /// NO bleeding chip (`ExploreSelection.offered(suppressed:)`).
    let suppressed: Bool

    /// The suppression-filtered chip catalog. Built ONCE per render from `ExploreSelection.offered` — a
    /// suppressed user never even SEES a bleeding chip (T-15-11, defense-in-depth).
    private var offeredSeries: [ExploreSeries] {
        ExploreSelection.offered(suppressed: suppressed)
    }

    /// Whether the quantitative cap is currently reached (so the helper shows + unselected quantitative
    /// chips disable). Event chips are uncapped and stay enabled.
    private var capReached: Bool {
        selection.quantitativeCount >= ExploreSelection.quantitativeCap
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {   // md between the picker block and the range control
            // 1. The toggle-chip picker — wraps in a flowing layout over the suppression-filtered catalog.
            FlowLayout(spacing: 8) {   // sm chip-to-chip gap
                ForEach(offeredSeries) { series in
                    chip(series)
                }
            }
            // Container boundary: the `explore.picker` id bleeds down and MASKS each chip's
            // `explore.chip.<series.id>` id (every chip Button would otherwise resolve to `explore.picker`
            // in the a11y tree), making the per-chip ids untargetable by the E2E. `.contain` shields the
            // descendant chip ids so they survive — the SAME id-bleed fix `ExploreLanesView` applies to its
            // tooltip proxy ids (the Phase-13 discovery).
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("explore.picker")

            // The cap-reached helper line (the remove-to-add affordance) — shown ONLY at the cap.
            if capReached {
                Text(ExploreCopy.capReached)
                    .font(.body)
                    .foregroundStyle(Color.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("explore.picker.capHint")
            }

            // 2. The range-preset control — segmented Picker over TrendWindow, dashboard vocabulary (EXPL-06).
            Picker("Range", selection: $selection.window) {
                ForEach(TrendWindow.allCases, id: \.self) { window in
                    Text(ExploreCopy.windowLabel(window))
                        .tag(window)
                        .accessibilityIdentifier("explore.range.\(window.rawValue)")
                }
            }
            .pickerStyle(.segmented)
            .tint(Color.plum)   // Bloom: active range segment reads plum
            .accessibilityIdentifier("explore.range")
        }
    }

    // MARK: - One toggle chip (color-independent selected state)

    /// One toggle chip for `series`. Selected vs unselected differ by FILL + symbol + WEIGHT (never hue):
    ///   • selected   → accent fill, `checkmark.circle.fill`, `.semibold` + a low-stakes `xmark` remove glyph
    ///   • unselected → secondary surface, `plus.circle`, `.regular`
    /// At the cap an UNSELECTED quantitative chip is `.disabled(true)` (event chips stay enabled).
    @ViewBuilder
    private func chip(_ series: ExploreSeries) -> some View {
        let isSelected = selection.series.contains(series)
        // Reuse the value type's own rule: an unselected quantitative chip is addable only below the cap;
        // event series and already-selected series are always addable (so a re-tap to remove is never blocked).
        let canAct = selection.canAddQuantitative(series)
        // Disable ONLY an unselected, non-addable chip — i.e. a new quantitative series at the cap.
        let isDisabled = !isSelected && !canAct

        Button {
            selection.toggle(series)
        } label: {
            HStack(spacing: 4) {   // xs icon↔label gap
                // The selected/unselected SHAPE signal (color-independent): a fill check vs an add plus.
                Image(systemName: isSelected ? "checkmark.circle.fill" : "plus.circle")
                Text(series.displayName)
                    .fontWeight(isSelected ? .semibold : .regular)   // the WEIGHT signal
                // The low-stakes remove glyph on a selected chip — `xmark`, NEVER destructive red.
                if isSelected {
                    Image(systemName: "xmark")
                        .font(.caption2)
                        .accessibilityIdentifier("explore.chip.\(series.id).remove")
                }
            }
            .font(.subheadline)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)   // ≥44pt touch floor (HIG)
            .background(
                Capsule().fill(isSelected
                    ? Color.petal                              // selected FILL (Bloom active-pill tint)
                    : Color.surface)                           // unselected outlined add-chip surface
            )
            .overlay(
                Capsule().strokeBorder(isSelected ? Color.plum : Color.strokeSubtle, lineWidth: 1)
            )
            .foregroundStyle(isSelected ? Color.plum : Color.ink)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        // Stable, value-INDEPENDENT id (the series id) so the E2E can target a chip regardless of state.
        .accessibilityIdentifier("explore.chip.\(series.id)")
        // a11y label/hint: removing is a preference (`ExploreCopy.removeSeries`), adding invites
        // (`ExploreCopy.addToTimeline`) — both audited, never literals.
        .accessibilityLabel(Text(series.displayName))
        .accessibilityValue(Text(isSelected ? ExploreCopy.removeSeries : ""))
        .accessibilityHint(Text(isSelected ? ExploreCopy.removeSeries : ExploreCopy.addToTimeline))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - FlowLayout (a wrapping chip layout; iOS 16+ `Layout` protocol — system, no dependency)

/// A simple wrapping layout: lays subviews left→right, wrapping to the next row when the proposed width
/// is exceeded. Used for the chip cluster so chips reflow at large Dynamic Type without clipping. Pure
/// system `Layout` (iOS 16+) — no third-party dependency.
///
/// NOTE (IN-02, intentional v1 deferral): this ignores the `Layout` cache seam and recomputes the row
/// layout each pass. For the small chip count here that is perf-neutral; a cache is deferred — flagged so
/// a future reader knows it is a known choice, not an oversight.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let rows = rowsLayout(maxWidth: maxWidth, subviews: subviews)
        let height = rows.totalHeight(spacing: spacing)
        // Width: the widest row (or the proposed width when finite).
        let width = proposal.width ?? rows.maxRowWidth(spacing: spacing)
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = rowsLayout(maxWidth: bounds.width, subviews: subviews)
        var y = bounds.minY
        for row in rows.rows {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size)
                )
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    // MARK: row computation

    private struct Row {
        var indices: [Int] = []
        var height: CGFloat = 0
        var width: CGFloat = 0
    }

    private struct Rows {
        var rows: [Row] = []
        func totalHeight(spacing: CGFloat) -> CGFloat {
            guard !rows.isEmpty else { return 0 }
            return rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(rows.count - 1)
        }
        func maxRowWidth(spacing: CGFloat) -> CGFloat {
            rows.map(\.width).max() ?? 0
        }
    }

    private func rowsLayout(maxWidth: CGFloat, subviews: Subviews) -> Rows {
        var rows = Rows()
        var current = Row()
        var x: CGFloat = 0
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            // Wrap when this subview would overflow the row (and the row already has something).
            if !current.indices.isEmpty && x + size.width > maxWidth {
                rows.rows.append(current)
                current = Row()
                x = 0
            }
            current.indices.append(index)
            current.height = max(current.height, size.height)
            x += size.width + spacing
            current.width = x - spacing
        }
        if !current.indices.isEmpty { rows.rows.append(current) }
        return rows
    }
}
