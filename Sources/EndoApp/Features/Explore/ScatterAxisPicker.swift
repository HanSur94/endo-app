// Requirement: SCAT-01 — the Scatter mode's two-axis (X / Y) selector surface.
//
// In Scatter mode the control surface presents EXACTLY two axis selectors — an "X" and a "Y" — each
// choosing ONE series from the QUANTITATIVE-ONLY catalog (UI-SPEC §B). The options are
// `ExploreSelection.offered(suppressed:)` filtered to `scaleKind.isQuantitative`, so:
//   • presence/event series (food, clots, PRN, medication, bleeding-event) are NEVER offered as an axis —
//     a presence factor has no numeric value, so it cannot be a scatter axis (RESEARCH OQ3 / honesty 5);
//   • a SUPPRESSED user is offered NO `bleeding.intensity` axis (the offered() filter removes both bleeding
//     series, defense-in-depth alongside the Phase-14 accessor gate — T-16-07).
//
// Picking the SAME series for both axes is ALLOWED (a self-vs-self diagonal is honest, if uninteresting) —
// NO special-casing (UI-SPEC §B).
//
// PRIVACY (T-16-06, LOCKED): this view owns NO state. The X / Y bindings are owned by `ExploreView`'s
// EPHEMERAL view state — persisting an axis selection would leak health-interest metadata outside the
// consent-gated store. There is intentionally NO persistent storage wrapper in this file; the grep gate
// forbids any such wrapper here (the LOCKED privacy invariant, mirroring `ExploreView.selection`).
//
// New app-target file → run `xcodegen generate` (the EndoApp target globs Sources/EndoApp).
import SwiftUI
import EndoInsights

/// The Scatter mode's two-axis picker: an X selector and a Y selector, each a single-select `Menu` over
/// the QUANTITATIVE-ONLY offered catalog. Pure presentation over the injected `@Binding`s — owns NO state.
struct ScatterAxisPicker: View {

    /// The chosen X-axis series (ephemeral — owned by `ExploreView` view state, never persisted).
    @Binding var xSeries: ExploreSeries?

    /// The chosen Y-axis series (ephemeral — owned by `ExploreView` view state, never persisted).
    @Binding var ySeries: ExploreSeries?

    /// Whether the user's data is suppressed — drives the offered filter so a suppressed user is offered
    /// NO bleeding axis (`ExploreSelection.offered(suppressed:)` removes both bleeding series).
    let suppressed: Bool

    /// The QUANTITATIVE-ONLY axis options: the suppression-filtered offered catalog narrowed to the series
    /// that carry a numeric value. Presence/event series are excluded (they cannot be a scatter axis), and
    /// `bleeding.intensity` is absent under suppression (T-16-07). Built ONCE per render from the single
    /// source of truth (`ExploreSelection.offered`) — never a hand-maintained list.
    private var axisOptions: [ExploreSeries] {
        ExploreSelection.offered(suppressed: suppressed).filter { $0.scaleKind.isQuantitative }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {   // md between the two axis selectors
            axisSelector(axis: "X", binding: $xSeries, idStem: "explore.scatter.axisX")
            axisSelector(axis: "Y", binding: $ySeries, idStem: "explore.scatter.axisY")
        }
    }

    // MARK: - One axis selector (a single-select Menu over the quantitative-only options)

    /// One labelled axis selector. `axis` is the bare "X" / "Y" letter (the only literal allowed — an axis
    /// name, not chrome copy); every series label is the catalog `displayName` and the empty-state label is
    /// `ExploreCopy.scatterAxisPlaceholder` (audited, never a literal). Each option row carries a stable,
    /// value-INDEPENDENT id (`<idStem>.<series.id>`) so the E2E can target an option regardless of state;
    /// the control container carries `<idStem>`. A ≥44pt hit area satisfies the HIG touch floor.
    @ViewBuilder
    private func axisSelector(axis: String, binding: Binding<ExploreSeries?>, idStem: String) -> some View {
        let chosen = binding.wrappedValue
        Menu {
            ForEach(axisOptions) { series in
                Button {
                    binding.wrappedValue = series
                } label: {
                    // A check on the currently-chosen option (color-independent selected cue).
                    if chosen == series {
                        Label(series.displayName, systemImage: "checkmark")
                    } else {
                        Text(series.displayName)
                    }
                }
                // Stable, value-independent per-option id (the series id) — targetable regardless of state.
                .accessibilityIdentifier("\(idStem).\(series.id)")
            }
        } label: {
            HStack(spacing: 8) {   // sm axis-letter ↔ value gap
                Text(axis)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.inkSecondary)
                Text(chosen?.displayName ?? ExploreCopy.scatterAxisPlaceholder)
                    .font(.subheadline)
                    .foregroundStyle(chosen == nil ? Color.inkMuted : Color.ink)
                Spacer(minLength: 8)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2)
                    .foregroundStyle(Color.inkSecondary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)   // ≥44pt touch floor (HIG)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.surface)   // Bloom field surface
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.strokeSubtle, lineWidth: 1)   // Bloom hairline (DayLog/Settings field look)
            )
        }
        .accessibilityIdentifier(idStem)
        .accessibilityLabel(Text(axis))
        .accessibilityValue(Text(chosen?.displayName ?? ExploreCopy.scatterAxisPlaceholder))
    }
}
