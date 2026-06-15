// Bloom design system — color tokens (quick task 260615-wld).
//
// RESTYLE-ONLY foundation. These are the single source of truth for the "Bloom" Pencil palette.
// Tokens are ASSET-FREE: every color is built in code with `Color(red:green:blue:)` in the sRGB
// 0...1 space (converted from the spec hex). NO Assets.xcassets color sets are added — this keeps
// the palette greppable, diffable, and dependency-free (global constraint 3).
//
// Naming: each `static let` is named EXACTLY after the spec token, e.g. `Color.canvas`,
// `Color.surfaceSunken`, `Color.plumDeep`. Downstream views reference `Color.<token>` directly.
import SwiftUI

extension Color {
    /// Build an sRGB color from an 8-bit hex triple (0...255 per channel).
    fileprivate static func bloom(_ r: Double, _ g: Double, _ b: Double) -> Color {
        Color(.sRGB, red: r / 255.0, green: g / 255.0, blue: b / 255.0, opacity: 1.0)
    }

    // MARK: Surfaces

    /// #E9E0E6 — the warm app background canvas.
    static let canvas = Color.bloom(233, 224, 230)
    /// #FBF8FA — the lightest surface (sheets, raised areas).
    static let surface = Color.bloom(251, 248, 250)
    /// #F3ECF0 — a slightly sunken/recessed surface (wells, segmented tracks).
    static let surfaceSunken = Color.bloom(243, 236, 240)
    /// #FFFFFF — the card fill.
    static let card = Color.bloom(255, 255, 255)

    // MARK: Ink (text)

    /// #2A1020 — primary text ink (deep plum-black).
    static let ink = Color.bloom(42, 16, 32)
    /// #7A5E70 — secondary text ink.
    static let inkSecondary = Color.bloom(122, 94, 112)
    /// #A997A4 — muted/tertiary text ink (eyebrows, captions).
    static let inkMuted = Color.bloom(169, 151, 164)

    // MARK: Brand

    /// #EF6E8E — rose (gradient start, accents).
    static let rose = Color.bloom(239, 110, 142)
    /// #DC4F73 — a deeper rose.
    static let roseDeep = Color.bloom(220, 79, 115)
    /// #6E2153 — plum (primary CTA, brand).
    static let plum = Color.bloom(110, 33, 83)
    /// #4A1338 — a deeper plum (gradient end, pressed states).
    static let plumDeep = Color.bloom(74, 19, 56)
    /// #F7B544 — gold accent.
    static let gold = Color.bloom(247, 181, 68)
    /// #F7E3EA — petal (chip/active-pill tint).
    static let petal = Color.bloom(247, 227, 234)
    /// #EDD9E4 — petal line/border.
    static let petalLine = Color.bloom(237, 217, 228)

    // MARK: Strokes & supporting

    /// #E8DCE5 — subtle 1pt card/divider stroke.
    static let strokeSubtle = Color.bloom(232, 220, 229)
    /// #3DA5A0 — teal supporting accent.
    static let teal = Color.bloom(61, 165, 160)
    /// #5B6CB0 — indigo supporting accent.
    static let indigo = Color.bloom(91, 108, 176)
}

#if DEBUG
#Preview("Bloom Color Tokens") {
    let swatches: [(String, Color)] = [
        ("canvas", .canvas), ("surface", .surface), ("surfaceSunken", .surfaceSunken),
        ("card", .card), ("ink", .ink), ("inkSecondary", .inkSecondary),
        ("inkMuted", .inkMuted), ("rose", .rose), ("roseDeep", .roseDeep),
        ("plum", .plum), ("plumDeep", .plumDeep), ("gold", .gold),
        ("petal", .petal), ("petalLine", .petalLine), ("strokeSubtle", .strokeSubtle),
        ("teal", .teal), ("indigo", .indigo),
    ]
    return ScrollView {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 12) {
            ForEach(swatches, id: \.0) { name, color in
                VStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(color)
                        .frame(height: 56)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.strokeSubtle))
                    Text(name).font(.caption2)
                }
            }
        }
        .padding()
    }
    .background(Color.canvas)
}
#endif
