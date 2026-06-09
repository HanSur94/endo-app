// Requirement: DATA-05 / D-03 (Phase 9, Plan 09-03) — the "from Apple Health" provenance badge.
//
// Rendered next to any field whose merged source (from the 09-02 `daySnapshotSources(on:)` read) is
// `.appleHealth`, so the user always knows a value came from Apple Health, never from their own logging.
//
// A11Y / NEVER-COLOR-ALONE (UI-SPEC, mirrors InsightCardView's confidence pill): the badge carries BOTH
// an SF Symbol AND the text label — color is never the sole carrier of meaning. Stable id
// `health.source.badge` so the E2E can assert it without copy-string matching.
import SwiftUI

/// A small "from Apple Health" pill (D-03). Text + SF Symbol, matching the existing capsule/pill style.
/// Shown next to an imported value so provenance is always visible.
struct HealthSourceBadge: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "heart.text.square")
            Text(HealthConnectCopy.sourceBadge)
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(.quaternary.opacity(0.5), in: Capsule())
        // The whole pill is one a11y element reading "from Apple Health" (symbol + text, never color-alone).
        .accessibilityElement(children: .combine)
        .accessibilityLabel(HealthConnectCopy.sourceBadge)
        .accessibilityIdentifier("health.source.badge")
    }
}
