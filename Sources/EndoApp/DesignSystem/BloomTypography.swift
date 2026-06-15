// Bloom design system — typography (quick task 260615-wld).
//
// RESTYLE-ONLY. Editorial headings use the SYSTEM serif via `.fontDesign(.serif)` (no bundled font
// files, global constraint 3); body/UI text uses the default system sans. An uppercase tracked
// "eyebrow" style labels sections. Everything is exposed as `ViewModifier`s plus convenience
// `View` extensions (`.bloomDisplay()`, `.bloomTitle()`, `.bloomH2()`, `.bloomH3()`, `.bloomEyebrow()`).
import SwiftUI

// MARK: - Serif heading modifiers

/// The largest editorial serif heading (~30pt semibold) — screen titles.
struct BloomDisplayModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 30, weight: .semibold))
            .fontDesign(.serif)
            .foregroundStyle(Color.ink)
    }
}

/// A serif title (~28pt semibold).
struct BloomTitleModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 28, weight: .semibold))
            .fontDesign(.serif)
            .foregroundStyle(Color.ink)
    }
}

/// A serif section heading (~22pt semibold).
struct BloomH2Modifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 22, weight: .semibold))
            .fontDesign(.serif)
            .foregroundStyle(Color.ink)
    }
}

/// A serif sub-heading (~19pt semibold).
struct BloomH3Modifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 19, weight: .semibold))
            .fontDesign(.serif)
            .foregroundStyle(Color.ink)
    }
}

/// The uppercase tracked eyebrow label (~12pt bold, kerned, muted).
struct BloomEyebrowModifier: ViewModifier {
    var tint: Color = .inkMuted
    func body(content: Content) -> some View {
        content
            .font(.system(size: 12, weight: .bold))
            .kerning(1.4)
            .textCase(.uppercase)
            .foregroundStyle(tint)
    }
}

// MARK: - View conveniences

extension View {
    func bloomDisplay() -> some View { modifier(BloomDisplayModifier()) }
    func bloomTitle() -> some View { modifier(BloomTitleModifier()) }
    func bloomH2() -> some View { modifier(BloomH2Modifier()) }
    func bloomH3() -> some View { modifier(BloomH3Modifier()) }
    func bloomEyebrow(tint: Color = .inkMuted) -> some View {
        modifier(BloomEyebrowModifier(tint: tint))
    }
}

#if DEBUG
#Preview("Bloom Typography") {
    VStack(alignment: .leading, spacing: 16) {
        Text("GOOD MORNING").bloomEyebrow()
        Text("Display heading").bloomDisplay()
        Text("Title heading").bloomTitle()
        Text("Section heading").bloomH2()
        Text("Sub heading").bloomH3()
        Text("Body copy uses the default system sans and stays highly legible at any Dynamic Type size.")
            .font(.body)
            .foregroundStyle(Color.inkSecondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(24)
    .background(Color.canvas)
}
#endif
