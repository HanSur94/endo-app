// Requirement: SYMP-02 — record pain location on a discrete, accessible body map (D-04, Pitfall 4).
//
// A discrete named-region pain map over `BodyRegion.allCases`. Per RESEARCH §"Discrete-region
// accessible body map hotspot" + Pitfall 4, the ACCESSIBILITY CONTRACT — not the exact geometry — is
// what's load-bearing and is shipped here:
//   - each region is an accessibility element with a plain-language label + a severity value
//     ("severity N of 10" / "not marked"), the `.isButton` trait, and a `bodymap.region.<rawValue>` id;
//   - hit targets are ≥44pt effective (a `.contentShape` over a ≥44pt frame);
//   - severity is conveyed by NUMBER + tint, never color alone (Pitfall 4);
//   - a non-map LIST fallback (`bodymap.list.<rawValue>`) sets the SAME PainPoint so the feature is
//     fully operable without the visual map.
//
// Open Question 1 (RESEARCH): a single front silhouette + a front/back toggle is the recommended
// start; the region MODEL + accessibility contract are identical regardless of vector geometry, so a
// simple flow-layout of labelled region tiles is shipped now and the silhouette geometry can iterate
// via screenshots later without any contract change.
//
// MV pattern (D-12): NO ViewModel. The view owns the draft `severities` dictionary and calls back to
// the editor, which writes through the Repository on Save. Copy posture (D-14): region labels come
// from the audited `BodyRegion.plainLabel`; no gendered/medical-purpose tokens.
import SwiftUI

struct BodyMapView: View {
    /// Draft per-region severities (region → 0...10). `nil`/absent = not marked. Owned by the editor.
    @Binding var severities: [BodyRegion: Int]

    /// Front silhouette vs. back silhouette (Open Question 1 — a front/back toggle over one map).
    @State private var side: Side = .front
    /// The region whose severity sheet is currently presented.
    @State private var editingRegion: BodyRegion?

    private enum Side: String, CaseIterable {
        case front, back
        var label: String { self == .front ? "Front" : "Back" }
    }

    /// Minimum accessible hit target (Pitfall 4: ≥44pt effective).
    private static let minTarget: CGFloat = 44

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Body side", selection: $side) {
                ForEach(Side.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("bodymap.sideToggle")

            // Discrete tappable region tiles (the visual map). A flow of fixed-size tiles keeps every
            // hit target ≥44pt and Dynamic-Type safe; geometry can become a silhouette later.
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], spacing: 8) {
                ForEach(regionsForSide, id: \.self) { region in
                    regionTile(region)
                }
            }

            // Non-map LIST fallback (Pitfall 4) — operable without the visual map, sets the SAME point.
            DisclosureGroup("All areas (list)") {
                ForEach(BodyRegion.allCases, id: \.self) { region in
                    Button {
                        editingRegion = region
                    } label: {
                        HStack {
                            Text(region.plainLabel).foregroundStyle(.primary)
                            Spacer()
                            Text(valueText(for: region))
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("bodymap.list.\(region.rawValue)")
                    .accessibilityLabel(Text(region.plainLabel))
                    .accessibilityValue(Text(accessibilityValue(for: region)))
                    .accessibilityAddTraits(.isButton)
                }
            }
            .accessibilityIdentifier("bodymap.listFallback")
        }
        .sheet(item: $editingRegion) { region in
            regionSeveritySheet(region)
        }
    }

    // MARK: - Region tile (the discrete hotspot)

    private func regionTile(_ region: BodyRegion) -> some View {
        let severity = severities[region]
        return Button {
            editingRegion = region
        } label: {
            VStack(spacing: 4) {
                Text(region.plainLabel)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                // Severity NOT color-only (Pitfall 4): show the number alongside the tint.
                Text(severity.map { "\($0)/10" } ?? "—")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(severity == nil ? .secondary : .primary)
            }
            .frame(maxWidth: .infinity, minHeight: Self.minTarget)
            .padding(8)
            .background(tint(for: severity), in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(severity == nil ? Color.secondary.opacity(0.3) : Color.accentColor,
                            lineWidth: severity == nil ? 1 : 2)
            )
        }
        .buttonStyle(.plain)
        .contentShape(RoundedRectangle(cornerRadius: 10))   // hit-test == the visible ≥44pt tile
        .accessibilityLabel(Text(region.plainLabel))
        .accessibilityValue(Text(accessibilityValue(for: region)))
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("bodymap.region.\(region.rawValue)")
    }

    // MARK: - Severity sheet

    private func regionSeveritySheet(_ region: BodyRegion) -> some View {
        NavigationStack {
            Form {
                Section(region.plainLabel) {
                    SeverityControl(severity: severitySheetBinding(for: region))
                }
                Section {
                    Button(role: .destructive) {
                        severities[region] = nil
                        editingRegion = nil
                    } label: {
                        Text("Clear this area")
                    }
                    .accessibilityIdentifier("bodymap.region.\(region.rawValue).clear")
                    .disabled(severities[region] == nil)
                }
            }
            .navigationTitle("Pain area")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { editingRegion = nil }
                        .accessibilityIdentifier("bodymap.region.\(region.rawValue).done")
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Bindings & formatting

    private var regionsForSide: [BodyRegion] {
        side == .front ? Self.frontRegions : Self.backRegions
    }

    /// Front-facing regions (the abdomen/pelvic/bladder cluster + referred shoulder).
    private static let frontRegions: [BodyRegion] = [
        .lowerAbdomenPelvis, .lowerQuadrantLeft, .lowerQuadrantRight,
        .bladder, .upperThighs, .chestDiaphragm, .shoulder,
    ]
    /// Back-facing regions.
    private static let backRegions: [BodyRegion] = [
        .lowerBack, .hips, .rectalBowel,
    ]

    private func severitySheetBinding(for region: BodyRegion) -> Binding<Int> {
        Binding(
            get: { severities[region] ?? 6 },                       // moderate default on first mark
            set: { severities[region] = min(10, max(0, $0)) }
        )
    }

    private func valueText(for region: BodyRegion) -> String {
        severities[region].map { "\($0)/10" } ?? "—"
    }

    private func accessibilityValue(for region: BodyRegion) -> String {
        severities[region].map { "severity \($0) of 10" } ?? "not marked"
    }

    /// Severity tint (paired ALWAYS with the numeric value — never color-only, Pitfall 4).
    private func tint(for severity: Int?) -> Color {
        guard let severity else { return Color(.secondarySystemBackground) }
        let intensity = Double(min(10, max(0, severity))) / 10.0
        return Color.accentColor.opacity(0.12 + 0.5 * intensity)
    }
}

// Allow a `BodyRegion` to drive `.sheet(item:)`.
extension BodyRegion: Identifiable {
    var id: String { rawValue }
}
