// Requirement: SYMP-01 / D-02 — a reusable 0–10 severity control with quick presets.
//
// A small, reusable MV-pattern view (D-12: NO ViewModel) binding an `Int` severity in `0...10`.
// It offers a slider for fine control plus mild=3 / moderate=6 / severe=9 quick presets (D-02 —
// "keep daily logging fast"). The bound value is clamped to `0...10` here as defence-in-depth so a
// bad binding can never push an out-of-range value toward the store (mirrors the Repository clamp,
// T-02-08).
//
// Carries the `daylog.severitySlider` accessibility identifier the E2E drives.
import SwiftUI

struct SeverityControl: View {
    /// The bound severity, always kept in `0...10`.
    @Binding var severity: Int

    /// D-02 quick presets.
    private static let presets: [(label: String, value: Int)] =
        [("Mild", 3), ("Moderate", 6), ("Severe", 9)]

    private var clampedBinding: Binding<Double> {
        Binding(
            get: { Double(min(10, max(0, severity))) },
            set: { severity = min(10, max(0, Int($0.rounded()))) }
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Severity")
                    .font(.subheadline)
                    .foregroundStyle(Color.inkSecondary)
                Spacer()
                Text("\(min(10, max(0, severity)))/10")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(Color.plum)
            }

            Slider(value: clampedBinding, in: 0...10, step: 1) {
                Text("Severity")
            }
            .tint(Color.rose)
            .accessibilityIdentifier("daylog.severitySlider")
            .accessibilityValue("\(min(10, max(0, severity))) of 10")

            HStack(spacing: 8) {
                ForEach(Self.presets, id: \.value) { preset in
                    let isSelected = min(10, max(0, severity)) == preset.value
                    Button(preset.label) { severity = preset.value }
                        .buttonStyle(.plain)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(isSelected ? .white : Color.plum)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(isSelected ? Color.plum : Color.petal, in: Capsule())
                        .overlay(Capsule().stroke(Color.petalLine, lineWidth: 1))
                        .accessibilityIdentifier("daylog.severityPreset.\(preset.label.lowercased())")
                }
            }
        }
    }
}
