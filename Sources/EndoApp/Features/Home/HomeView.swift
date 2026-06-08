// Requirement: D-13 — the real home screen, replacing `WalkingSkeletonView`.
//
// Reachable ONLY from `AppGate`'s `.consented` branch (T-01-03 / T-02-06 carried): by construction
// no logging UI is built before consent. It shows:
//   - a reverse-chronological day history list (`@Query(sort: \DayEntry.date, order: .reverse)`),
//     rows id `home.dayRow.<index>`, each a brief symptom/bleeding summary line;
//   - a prominent "Log today" button (`home.logTodayButton`) presenting `DayLogEditorView` for today;
//   - the gear -> `SettingsView` toolbar route (`home.settingsButton`, carried from the skeleton);
//   - a reserved, empty cycle-summary slot (`home.cycleSummary`) Plan 04 fills.
//
// MV pattern (D-12): the view reads `@Query` and presents the editor; no ViewModel.
// Copy posture (D-14): the bleeding noun resolves through `Terminology`; no hard-coded gendered copy.
import SwiftUI
import SwiftData

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.terminologyStore) private var terminologyStore

    // Reverse-chronological day history (D-13). DayEntry is the per-day anchor the editor writes.
    @Query(sort: \SchemaV1.DayEntry.date, order: .reverse) private var days: [SchemaV1.DayEntry]

    @State private var editingDay: Date?

    private var terminology: Terminology {
        Terminology(preference: terminologyStore.preference)
    }

    var body: some View {
        NavigationStack {
            List {
                // Reserved cycle-summary slot — Plan 04 fills it (suppressed / derived-cycle copy).
                Section {
                    Color.clear
                        .frame(height: 0)
                        .accessibilityHidden(true)
                        .accessibilityIdentifier("home.cycleSummary")
                }
                .listRowBackground(Color.clear)

                Section {
                    Button {
                        editingDay = Calendar.current.startOfDay(for: .now)
                    } label: {
                        Label("Log today", systemImage: "square.and.pencil")
                    }
                    .accessibilityIdentifier("home.logTodayButton")
                }

                Section("History") {
                    if days.isEmpty {
                        Text("No days logged yet. Tap \u{201C}Log today\u{201D} to start.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                            Button {
                                editingDay = Calendar.current.startOfDay(for: day.date)
                            } label: {
                                dayRow(day)
                            }
                            .accessibilityIdentifier("home.dayRow.\(index)")
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    delete(day.date)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                                .accessibilityIdentifier("home.dayRow.\(index).delete")
                            }
                        }
                    }
                }
            }
            .navigationTitle("Endo-App")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        SettingsView()
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                    .accessibilityIdentifier("home.settingsButton")
                }
            }
            .sheet(item: $editingDay) { day in
                NavigationStack {
                    DayLogEditorView(day: day)
                }
            }
        }
    }

    // MARK: - Actions

    /// Delete a whole day's log (SYMP-04 / D-06). The `@Query` re-renders the list AND any derived
    /// cycle summary immediately — no manual refresh.
    private func delete(_ date: Date) {
        try? SwiftDataRepository(context: modelContext).deleteDay(date)
    }

    // MARK: - Row

    private func dayRow(_ day: SchemaV1.DayEntry) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(day.date, style: .date)
                .font(.headline)
                .foregroundStyle(.primary)
            Text(summaryLine(for: day.date))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    /// A brief, neutral summary of the day's logged data (copy via `Terminology`).
    private func summaryLine(for date: Date) -> String {
        let cal = Calendar.current
        let readings = (try? modelContext.fetch(FetchDescriptor<SchemaV1.SymptomReading>())) ?? []
        let symptomCount = readings.filter { cal.isDate($0.occurredOn, inSameDayAs: date) }.count

        let events = (try? modelContext.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())) ?? []
        let bleeding = events.first { cal.isDate($0.occurredAt, inSameDayAs: date) }
            .flatMap { FlowIntensity(raw: $0.flowRaw) }

        var parts: [String] = []
        if symptomCount > 0 {
            parts.append("\(symptomCount) symptom\(symptomCount == 1 ? "" : "s")")
        }
        if let bleeding {
            parts.append("\(terminology.bleedingNoun): \(bleeding.plainLabel.lowercased())")
        }
        return parts.isEmpty ? "Note only" : parts.joined(separator: " \u{00B7} ")
    }
}

// Allow a `Date` to drive `.sheet(item:)`.
extension Date: @retroactive Identifiable {
    public var id: TimeInterval { timeIntervalSince1970 }
}
