// Requirement: SYMP-02 — record pain location on an accessible body map (D-04, Pitfall 4).
//
// A human-silhouette pain map over the named `BodyRegion` set, matching the approved Pencil design
// "Endo-App — Body Map Pain Selection". A gender-neutral front/back silhouette renders as a single
// SwiftUI `Path`; one tappable, accessible hotspot per region sits anatomically over it. Per RESEARCH
// §"Discrete-region accessible body map hotspot" + Pitfall 4, the ACCESSIBILITY CONTRACT — not the
// exact geometry — is what's load-bearing and is shipped here:
//   - each region is ONE accessibility element with a plain-language label + a severity value
//     ("severity N of 10" / "not marked"), the `.isButton` trait, and a `bodymap.region.<rawValue>` id;
//   - hit targets are ≥44pt effective (a `.contentShape` over a ≥44pt frame even though the visible
//     dot is ~22pt);
//   - severity is conveyed by NUMBER (a centered badge) + tint, never color alone (Pitfall 4);
//   - a non-map LIST fallback (`bodymap.list.<rawValue>`) sets the SAME PainPoint so the feature is
//     fully operable without the visual map.
//
// Open Question 1 (RESEARCH): a single silhouette + a front/back toggle is the recommended start; the
// region MODEL + accessibility contract are identical regardless of vector geometry, so the silhouette
// geometry can iterate via screenshots later without any contract change.
//
// MV pattern (D-12): NO ViewModel. The view owns the draft `severities` dictionary and calls back to
// the editor, which writes through the Repository on Save. Copy posture (D-14): region labels come
// from the audited `BodyRegion.plainLabel`; no gendered/medical-purpose tokens.
import SwiftUI

/// Brand palette for the pain map — now thin aliases onto the shared Bloom design tokens
/// (`Color.rose`/`plum`/`petal`/`surfaceSunken`/`strokeSubtle`/`petalLine`/`roseDeep`) so the silhouette
/// card matches the rest of the reskin. Visual-only; the SYMP-02 accessibility contract is unchanged.
private enum BodyMapPalette {
    static let rose = Color.rose                 // #EF6E8E
    static let plum = Color.plum                 // #6E2153
    static let mauveFill = Color.petal           // petal tint for the silhouette fill
    static let mauveStroke = Color.petalLine     // petal line for the silhouette stroke
    static let roseStroke = Color.roseDeep       // deeper rose for the empty-hotspot ring
    static let cardBackground = Color.surfaceSunken  // #F3ECF0
    static let pillBackground = Color.petal      // #F7E3EA
}

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

    /// The silhouette's authoring view box (200 × 440). Hotspots are normalized into this space.
    fileprivate static let viewBoxWidth: CGFloat = 200
    fileprivate static let viewBoxHeight: CGFloat = 440

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Body side", selection: $side) {
                ForEach(Side.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("bodymap.sideToggle")

            // The human-silhouette map. A single `Path` fills the available width preserving the
            // 200:440 aspect ratio (never clipped), with one accessible hotspot per region placed
            // anatomically over it. Front and back share the SAME silhouette; only the hotspot set
            // differs (`regionsForSide`).
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(BodyMapPalette.cardBackground)
                GeometryReader { proxy in
                    ZStack {
                        BodySilhouette()
                            .fill(BodyMapPalette.mauveFill)
                        BodySilhouette()
                            .stroke(BodyMapPalette.mauveStroke, lineWidth: 1.3)

                        ForEach(regionsForSide, id: \.self) { region in
                            hotspot(region, in: proxy.size)
                        }
                    }
                }
                .aspectRatio(200.0/440.0, contentMode: .fit)
                .padding(12)
            }
            .frame(maxWidth: .infinity, minHeight: 360)
            .accessibilityElement(children: .contain)

            // Marked-areas list with severity mini-bars (mock parity). Petal pills echoing what's on the
            // body — label from the audited `plainLabel`, value from the existing `valueText`, plus a
            // small rose mini-bar derived from the EXISTING severity value (no new copy, no new data).
            if !markedRegions.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(markedRegions, id: \.self) { region in
                        HStack(spacing: 8) {
                            Text(region.plainLabel)
                                .font(.subheadline.weight(.semibold))
                            severityMiniBar(for: region)
                            Spacer(minLength: 4)
                            Text(valueText(for: region))
                                .font(.subheadline.monospacedDigit())
                        }
                        .foregroundStyle(BodyMapPalette.plum)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(BodyMapPalette.pillBackground, in: Capsule())
                    }
                }
                .accessibilityIdentifier("bodymap.markedSummary")
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

    // MARK: - Hotspot (the discrete accessible region marker on the silhouette)

    /// One accessible element per region. The visible dot is ~22pt, but a 44pt frame +
    /// `.contentShape(Circle())` makes the EFFECTIVE tap area ≥44pt (Pitfall 4). The element CENTER
    /// lands on the region's anatomical point so the center-tapping XCUITest hits the right spot.
    private func hotspot(_ region: BodyRegion, in size: CGSize) -> some View {
        let severity = severities[region]
        let unit = Self.hotspotCenter(for: region)
        let center = CGPoint(x: unit.x * size.width, y: unit.y * size.height)

        return Button {
            editingRegion = region
        } label: {
            ZStack {
                if let severity {
                    // Rose severity-tinted halo conveys intensity by tint…
                    Circle()
                        .fill(tint(for: severity))
                        .frame(width: 26, height: 26)
                        .overlay(Circle().stroke(BodyMapPalette.plum, lineWidth: 1.5))
                    // …a solid plum badge gives the number a legible (high-contrast) ground (mock parity)…
                    Circle()
                        .fill(BodyMapPalette.plum)
                        .frame(width: 17, height: 17)
                    // …and the number itself (severity NOT color-only, Pitfall 4).
                    Text("\(severity)")
                        .font(.caption2.bold().monospacedDigit())
                        .foregroundStyle(.white)
                } else {
                    Circle()
                        .fill(BodyMapPalette.rose.opacity(0.14))
                        .frame(width: 22, height: 22)
                        .overlay(Circle().stroke(BodyMapPalette.roseStroke, lineWidth: 1))
                }
            }
            .frame(width: Self.minTarget, height: Self.minTarget)  // ≥44pt effective hit area
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .position(x: center.x, y: center.y)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("bodymap.region.\(region.rawValue)")
        .accessibilityLabel(Text(region.plainLabel))
        .accessibilityValue(Text(accessibilityValue(for: region)))
    }

    /// Normalized (0...1) anatomical center of each region in the 200×440 silhouette view box.
    /// EXHAUSTIVE over all ten cases (the compiler proves every region is placed). Exactly ONE element
    /// per region — `.shoulder`/`.upperThighs`/`.hips` show two dots in the design but have ONE case,
    /// so they render ONE dot at the single center below.
    static func hotspotCenter(for region: BodyRegion) -> UnitPoint {
        switch region {
        // FRONT
        case .shoulder:            return UnitPoint(x: 0.500, y: 0.155)
        case .chestDiaphragm:      return UnitPoint(x: 0.500, y: 0.239)
        case .lowerAbdomenPelvis:  return UnitPoint(x: 0.500, y: 0.398)
        case .lowerQuadrantLeft:   return UnitPoint(x: 0.380, y: 0.420)
        case .lowerQuadrantRight:  return UnitPoint(x: 0.620, y: 0.420)
        case .bladder:             return UnitPoint(x: 0.500, y: 0.482)
        case .upperThighs:         return UnitPoint(x: 0.500, y: 0.595)
        // BACK
        case .lowerBack:           return UnitPoint(x: 0.500, y: 0.364)
        case .hips:                return UnitPoint(x: 0.500, y: 0.445)
        case .rectalBowel:         return UnitPoint(x: 0.500, y: 0.518)
        }
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

    /// Every marked region (both sides), in stable `allCases` order — drives the summary pills.
    private var markedRegions: [BodyRegion] {
        BodyRegion.allCases.filter { severities[$0] != nil }
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

    /// A small decorative severity mini-bar derived from the EXISTING severity value (rose fill ramped
    /// 0...10). Purely presentational — the numeric `valueText` next to it always carries the meaning,
    /// so this is `accessibilityHidden` (never color/length-only, Pitfall 4).
    @ViewBuilder
    private func severityMiniBar(for region: BodyRegion) -> some View {
        let severity = min(10, max(0, severities[region] ?? 0))
        let fraction = Double(severity) / 10.0
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(BodyMapPalette.plum.opacity(0.12))
                Capsule()
                    .fill(BodyMapPalette.rose)
                    .frame(width: max(4, proxy.size.width * fraction))
            }
        }
        .frame(width: 56, height: 6)
        .accessibilityHidden(true)
    }

    private func valueText(for region: BodyRegion) -> String {
        severities[region].map { "\($0)/10" } ?? "—"
    }

    private func accessibilityValue(for region: BodyRegion) -> String {
        severities[region].map { "severity \($0) of 10" } ?? "not marked"
    }

    /// Severity tint (paired ALWAYS with the numeric value — never color-only, Pitfall 4). Brand rose,
    /// opacity ramped by severity so a higher number reads as a deeper dot. Only called for a marked
    /// region, so it takes a non-optional severity.
    private func tint(for severity: Int) -> Color {
        let intensity = Double(min(10, max(0, severity))) / 10.0
        return BodyMapPalette.rose.opacity(0.45 + 0.5 * intensity)
    }
}

// MARK: - Silhouette shape

/// The gender-neutral human silhouette, authored in a 200×440 view box and scaled into whatever rect
/// SwiftUI hands it. Front and back share this single path. The geometry is the faithful transcription
/// of the approved Pencil design's SVG `d` (arcs → `addArc`/`addRelativeArc`, cubics → `addCurve`).
private struct BodySilhouette: Shape {
    func path(in rect: CGRect) -> Path {
        // Scale the 200×440 authoring path into the supplied rect, preserving the caller-supplied
        // aspect ratio (the container uses `.aspectRatio(200.0/440.0, .fit)`).
        let transform = CGAffineTransform(
            scaleX: rect.width / BodyMapView.viewBoxWidth,
            y: rect.height / BodyMapView.viewBoxHeight
        )
        return Self.bodyPath.applying(transform)
    }

    /// The static silhouette outline in 200×440 space. Built once; identical for front and back.
    static let bodyPath: Path = {
        var p = Path()

        // Head: two half-arcs forming an ellipse (rx 21, ry 23) centered at (100, 26).
        //   M 79 26 a 21 23 0 1 0 42 0  a 21 23 0 1 0 -42 0  Z
        // The first relative arc sweeps from (79,26) to (121,26); the second back to (79,26).
        // Modeled as an ellipse in a 42×46 bounding box (the exact closed head outline).
        p.addEllipse(in: CGRect(x: 79, y: 3, width: 42, height: 46))   // addEllipse opens its own subpath

        // Torso + arms + legs: the cubic-bezier body outline.
        //   M 92 46 C ... Z   (transcribed cubic-for-cubic from the SVG `d`).
        p.move(to: CGPoint(x: 92, y: 46))
        p.addCurve(to: CGPoint(x: 78, y: 62),
                   control1: CGPoint(x: 92, y: 54), control2: CGPoint(x: 88, y: 59))
        p.addCurve(to: CGPoint(x: 47, y: 92),
                   control1: CGPoint(x: 60, y: 67), control2: CGPoint(x: 50, y: 76))
        p.addCurve(to: CGPoint(x: 42, y: 144),
                   control1: CGPoint(x: 45, y: 104), control2: CGPoint(x: 44, y: 122))
        p.addCurve(to: CGPoint(x: 36, y: 204),
                   control1: CGPoint(x: 40, y: 164), control2: CGPoint(x: 38, y: 186))
        p.addCurve(to: CGPoint(x: 45, y: 220),
                   control1: CGPoint(x: 35, y: 214), control2: CGPoint(x: 39, y: 220))
        p.addCurve(to: CGPoint(x: 55, y: 204),
                   control1: CGPoint(x: 51, y: 220), control2: CGPoint(x: 54, y: 214))
        p.addCurve(to: CGPoint(x: 62, y: 146),
                   control1: CGPoint(x: 57, y: 186), control2: CGPoint(x: 60, y: 164))
        p.addCurve(to: CGPoint(x: 65, y: 108),
                   control1: CGPoint(x: 63, y: 132), control2: CGPoint(x: 64, y: 120))
        p.addCurve(to: CGPoint(x: 70, y: 100),
                   control1: CGPoint(x: 66, y: 102), control2: CGPoint(x: 68, y: 98))
        p.addCurve(to: CGPoint(x: 70, y: 150),
                   control1: CGPoint(x: 69, y: 118), control2: CGPoint(x: 68, y: 134))
        p.addCurve(to: CGPoint(x: 65, y: 190),
                   control1: CGPoint(x: 71, y: 162), control2: CGPoint(x: 67, y: 174))
        p.addCurve(to: CGPoint(x: 66, y: 236),
                   control1: CGPoint(x: 63, y: 206), control2: CGPoint(x: 64, y: 220))
        p.addCurve(to: CGPoint(x: 72, y: 310),
                   control1: CGPoint(x: 68, y: 260), control2: CGPoint(x: 70, y: 286))
        p.addCurve(to: CGPoint(x: 75, y: 372),
                   control1: CGPoint(x: 73, y: 330), control2: CGPoint(x: 74, y: 352))
        p.addCurve(to: CGPoint(x: 75, y: 416),
                   control1: CGPoint(x: 76, y: 392), control2: CGPoint(x: 76, y: 406))
        p.addCurve(to: CGPoint(x: 85, y: 428),
                   control1: CGPoint(x: 74, y: 424), control2: CGPoint(x: 78, y: 428))
        p.addCurve(to: CGPoint(x: 95, y: 416),
                   control1: CGPoint(x: 92, y: 428), control2: CGPoint(x: 95, y: 424))
        p.addCurve(to: CGPoint(x: 98, y: 272),
                   control1: CGPoint(x: 95, y: 400), control2: CGPoint(x: 96, y: 380))
        p.addCurve(to: CGPoint(x: 100, y: 248),
                   control1: CGPoint(x: 99, y: 260), control2: CGPoint(x: 100, y: 252))
        p.addCurve(to: CGPoint(x: 102, y: 272),
                   control1: CGPoint(x: 100, y: 252), control2: CGPoint(x: 101, y: 260))
        p.addCurve(to: CGPoint(x: 104, y: 360),
                   control1: CGPoint(x: 103, y: 300), control2: CGPoint(x: 104, y: 330))
        p.addCurve(to: CGPoint(x: 105, y: 416),
                   control1: CGPoint(x: 104, y: 380), control2: CGPoint(x: 105, y: 400))
        p.addCurve(to: CGPoint(x: 115, y: 428),
                   control1: CGPoint(x: 105, y: 424), control2: CGPoint(x: 108, y: 428))
        p.addCurve(to: CGPoint(x: 125, y: 416),
                   control1: CGPoint(x: 122, y: 428), control2: CGPoint(x: 126, y: 424))
        p.addCurve(to: CGPoint(x: 125, y: 372),
                   control1: CGPoint(x: 124, y: 406), control2: CGPoint(x: 124, y: 392))
        p.addCurve(to: CGPoint(x: 128, y: 310),
                   control1: CGPoint(x: 126, y: 352), control2: CGPoint(x: 127, y: 330))
        p.addCurve(to: CGPoint(x: 134, y: 236),
                   control1: CGPoint(x: 130, y: 286), control2: CGPoint(x: 132, y: 260))
        p.addCurve(to: CGPoint(x: 135, y: 190),
                   control1: CGPoint(x: 136, y: 220), control2: CGPoint(x: 137, y: 206))
        p.addCurve(to: CGPoint(x: 130, y: 150),
                   control1: CGPoint(x: 133, y: 174), control2: CGPoint(x: 129, y: 162))
        p.addCurve(to: CGPoint(x: 130, y: 100),
                   control1: CGPoint(x: 132, y: 134), control2: CGPoint(x: 131, y: 118))
        p.addCurve(to: CGPoint(x: 135, y: 108),
                   control1: CGPoint(x: 132, y: 98), control2: CGPoint(x: 134, y: 102))
        p.addCurve(to: CGPoint(x: 138, y: 146),
                   control1: CGPoint(x: 136, y: 120), control2: CGPoint(x: 137, y: 132))
        p.addCurve(to: CGPoint(x: 145, y: 204),
                   control1: CGPoint(x: 140, y: 164), control2: CGPoint(x: 143, y: 186))
        p.addCurve(to: CGPoint(x: 155, y: 220),
                   control1: CGPoint(x: 146, y: 214), control2: CGPoint(x: 149, y: 220))
        p.addCurve(to: CGPoint(x: 164, y: 204),
                   control1: CGPoint(x: 161, y: 220), control2: CGPoint(x: 165, y: 214))
        p.addCurve(to: CGPoint(x: 158, y: 144),
                   control1: CGPoint(x: 162, y: 186), control2: CGPoint(x: 160, y: 164))
        p.addCurve(to: CGPoint(x: 153, y: 92),
                   control1: CGPoint(x: 156, y: 122), control2: CGPoint(x: 155, y: 104))
        p.addCurve(to: CGPoint(x: 122, y: 62),
                   control1: CGPoint(x: 150, y: 76), control2: CGPoint(x: 140, y: 67))
        p.addCurve(to: CGPoint(x: 108, y: 46),
                   control1: CGPoint(x: 112, y: 59), control2: CGPoint(x: 108, y: 54))
        p.closeSubpath()

        return p
    }()
}

// Allow a `BodyRegion` to drive `.sheet(item:)`.
extension BodyRegion: Identifiable {
    var id: String { rawValue }
}
