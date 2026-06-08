// Requirement: DATA-01 + walking-skeleton end-to-end proof (post-consent only).
//
// This screen is reachable ONLY from `AppGate`'s `.consented` branch (T-01-03), so by construction
// no health record is written before consent. It proves the full local stack: tapping "Log a day"
// inserts a `DayEntry` via the model context, and a `@Query`-driven list reads it back — one write,
// one read, end to end against the local SwiftData store.
import SwiftUI
import SwiftData

struct WalkingSkeletonView: View {
    @Environment(\.modelContext) private var modelContext

    // Reads back what was written (the "read it back" half of the proof).
    @Query(sort: \SchemaV1.DayEntry.date, order: .reverse) private var entries: [SchemaV1.DayEntry]

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("\(entries.count) day(s) logged")
                    .font(.headline)
                    .accessibilityIdentifier("skeleton.countLabel")

                if let latest = entries.first, let note = latest.note {
                    Text("Latest: \(note)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Button("Log a day", action: logADay)
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("skeleton.logButton")

                List(entries) { entry in
                    Text(entry.date, style: .date)
                }
            }
            .padding()
            .navigationTitle("Endo-App")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    // Route to Settings (withdraw consent D-04 + terminology customization D-07).
                    NavigationLink {
                        SettingsView()
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                    .accessibilityIdentifier("skeleton.settingsButton")
                }
            }
        }
    }

    /// The "write one record" half of the walking-skeleton proof. Only ever called post-consent.
    private func logADay() {
        let entry = SchemaV1.DayEntry(note: "Logged at \(Date.now.formatted(date: .omitted, time: .shortened))")
        modelContext.insert(entry)
        try? modelContext.save()
    }
}
