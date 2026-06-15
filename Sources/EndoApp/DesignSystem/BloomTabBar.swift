// Bloom design system — floating capsule tab bar (quick task 260615-wld).
//
// RESTYLE-ONLY, presentational. This file provides BOTH:
//   1. A fully-custom `BloomTabBar` floating-capsule view (petal active pill, plum icon+label), AND
//   2. `BloomTabBarAppearance` — a UITabBarAppearance helper so Task 4 can instead keep the stock
//      `TabView` (preserving the `tab.insights` XCUITest query on the real tab-item label) while still
//      wearing the Bloom palette. Task 4 chooses the appearance path so the accessibility id placement
//      on the system tab item is untouched.
//
// NOT wired into AppGate here — Task 1 is additive only.
import SwiftUI
import UIKit

/// A presentational tab descriptor for the custom `BloomTabBar`.
struct BloomTabItem: Identifiable {
    let id: Int
    let title: String
    let systemImage: String
    /// Optional accessibility identifier for the tab button (e.g. "tab.insights").
    var accessibilityID: String? = nil
}

/// A floating capsule tab bar: a white ~97%-opacity capsule with a soft shadow; the active tab is a
/// `Color.petal` pill with a plum icon+label, inactive tabs render in `Color.inkMuted`.
///
/// `selection` binds to the active tab id. Designed to drop into a `ZStack`/`safeAreaInset` over the
/// content without changing which views are built.
struct BloomTabBar: View {
    let items: [BloomTabItem]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(items) { item in
                let isActive = item.id == selection
                Button {
                    selection = item.id
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: item.systemImage)
                            .font(.system(size: 15, weight: .semibold))
                        Text(item.title)
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(isActive ? Color.plum : Color.inkMuted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background {
                        if isActive {
                            Capsule().fill(Color.petal)
                        }
                    }
                }
                .buttonStyle(.plain)
                .modifier(OptionalA11yID(id: item.accessibilityID))
            }
        }
        .padding(6)
        .background(Color.card.opacity(0.97), in: Capsule())
        .overlay(Capsule().stroke(Color.strokeSubtle, lineWidth: 1))
        .shadow(color: Color.plum.opacity(0.12), radius: 16, x: 0, y: 8)
    }
}

/// Applies an `.accessibilityIdentifier` only when a non-nil id is supplied.
private struct OptionalA11yID: ViewModifier {
    let id: String?
    func body(content: Content) -> some View {
        if let id {
            content.accessibilityIdentifier(id)
        } else {
            content
        }
    }
}

/// A UITabBarAppearance builder so a stock `TabView` can wear the Bloom palette while keeping its
/// native tab-item accessibility ids (the Task-4 chosen path). Apply to `UITabBar.appearance()` or a
/// scoped proxy, and pair with `.tint(Color.plum)` on the `TabView`.
enum BloomTabBarAppearance {
    /// A petal-tinted, soft tab-bar appearance with plum selected items and muted unselected items.
    static func make() -> UITabBarAppearance {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Color.surface)
        appearance.shadowColor = UIColor(Color.strokeSubtle)

        let selected = UIColor(Color.plum)
        let unselected = UIColor(Color.inkMuted)
        for item in [appearance.stackedLayoutAppearance,
                     appearance.inlineLayoutAppearance,
                     appearance.compactInlineLayoutAppearance] {
            item.selected.iconColor = selected
            item.selected.titleTextAttributes = [.foregroundColor: selected]
            item.normal.iconColor = unselected
            item.normal.titleTextAttributes = [.foregroundColor: unselected]
        }
        return appearance
    }
}

#if DEBUG
#Preview("Bloom Tab Bar") {
    struct PreviewHost: View {
        @State private var selection = 0
        var body: some View {
            ZStack(alignment: .bottom) {
                Color.canvas.ignoresSafeArea()
                BloomTabBar(
                    items: [
                        BloomTabItem(id: 0, title: "Log", systemImage: "square.and.pencil"),
                        BloomTabItem(id: 1, title: "Insights", systemImage: "chart.line.uptrend.xyaxis",
                                     accessibilityID: "tab.insights"),
                    ],
                    selection: $selection
                )
                .padding(.bottom, 24)
            }
        }
    }
    return PreviewHost()
}
#endif
