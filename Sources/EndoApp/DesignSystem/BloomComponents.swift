// Bloom design system — reusable components (quick task 260615-wld).
//
// RESTYLE-ONLY. Card surface, chip/pill, primary button, and eyebrow-label building blocks shared by
// every restyled screen. All asset-free, no new dependencies (global constraint 3).
import SwiftUI

// MARK: - Card

/// The Bloom card surface: white fill, 22pt corners, a 1pt subtle stroke, a soft plum-tinted shadow.
struct BloomCardModifier: ViewModifier {
    var padding: CGFloat = 18
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Color.strokeSubtle, lineWidth: 1)
            )
            .shadow(color: Color.plum.opacity(0.08), radius: 14, x: 0, y: 6)
    }
}

extension View {
    /// Wrap content in the standard Bloom card surface.
    func bloomCard(padding: CGFloat = 18) -> some View { modifier(BloomCardModifier(padding: padding)) }
}

// MARK: - Chip / Pill

/// A small reusable capsule chip: petal background, plum/ink text, an optional leading SF Symbol.
/// Purely presentational — pass a tint to recolor for emphasis (e.g. a gold "association" chip).
struct BloomChip: View {
    let text: String
    var systemImage: String? = nil
    var background: Color = .petal
    var foreground: Color = .plum
    var border: Color = .petalLine

    var body: some View {
        HStack(spacing: 5) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
            }
            Text(text)
                .font(.system(size: 12, weight: .semibold))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(background, in: Capsule())
        .overlay(Capsule().stroke(border, lineWidth: 1))
    }
}

/// Alias for `BloomChip` read as a "pill" at call sites that prefer the noun.
typealias BloomPill = BloomChip

// MARK: - Primary button

/// The plum primary CTA: full-width capsule, white label, ~52pt tall, dim-on-press.
struct BloomPrimaryButtonStyle: ButtonStyle {
    var fullWidth: Bool = true
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(height: 52)
            .padding(.horizontal, fullWidth ? 0 : 24)
            .background(Color.plum, in: Capsule())
            .opacity(configuration.isPressed ? 0.82 : 1.0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

extension View {
    /// Apply the Bloom plum primary button style.
    func bloomPrimaryButton(fullWidth: Bool = true) -> some View {
        buttonStyle(BloomPrimaryButtonStyle(fullWidth: fullWidth))
    }
}

// MARK: - Eyebrow label

/// The uppercase tracked eyebrow text wrapper.
struct EyebrowLabel: View {
    let text: String
    var tint: Color = .inkMuted
    var body: some View {
        Text(text).bloomEyebrow(tint: tint)
    }
}

#if DEBUG
#Preview("Bloom Components") {
    ScrollView {
        VStack(alignment: .leading, spacing: 20) {
            EyebrowLabel(text: "Today")

            VStack(alignment: .leading, spacing: 10) {
                Text("Card surface").bloomH3()
                Text("White fill, soft plum shadow, subtle stroke.")
                    .font(.subheadline)
                    .foregroundStyle(Color.inkSecondary)
                HStack {
                    BloomChip(text: "Not medical advice", systemImage: "info.circle")
                    BloomChip(text: "Gold accent", systemImage: "sparkles",
                              background: Color.gold.opacity(0.18), foreground: Color.ink, border: Color.gold.opacity(0.4))
                }
            }
            .bloomCard()

            Button("Primary action") {}
                .bloomPrimaryButton()
        }
        .padding(24)
    }
    .background(Color.canvas)
}
#endif
