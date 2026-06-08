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
    /// Presents the fast `DailyCheckInView` sheet (CHCK-01). Driven by the prominent Home CTA AND, via
    /// the injected `@Observable CheckInRouter`, by the reminder notification deep-link (D-16).
    @State private var presentingCheckIn = false

    /// The deep-link router (D-16). The notification delegate flips `router.presentCheckIn`; this view
    /// mirrors it into local `.sheet` state so the check-in sheet is presented INSIDE the consented
    /// branch only (AppGate builds HomeView solely in `.consented`).
    @Environment(\.checkInRouter) private var router

    private var terminology: Terminology {
        Terminology(preference: terminologyStore.preference)
    }

    var body: some View {
        NavigationStack {
            List {
                // Cycle summary (CYCL-02/03/04): suppressed -> off-state line; sparse -> "not enough
                // data yet"; else cycle-day + next-period RANGE + confidence. Recomputes via @Query.
                Section {
                    CycleSummaryView()
                }

                // Prominent, accent-tinted fast "Daily check-in" CTA (CHCK-01 / D-16). Above the
                // detailed "Log today" editor — a few taps to record the day, or just check in.
                Section {
                    Button {
                        presentingCheckIn = true
                    } label: {
                        Label(CheckInCopy.title, systemImage: "checkmark.circle")
                            .font(.headline)
                            .foregroundStyle(Color.accentColor)
                    }
                    .accessibilityIdentifier("home.dailyCheckInButton")
                }

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
            // The fast daily check-in sheet (CHCK-01). Presented by the Home CTA or the deep-link.
            .sheet(isPresented: $presentingCheckIn) {
                NavigationStack {
                    DailyCheckInView()
                }
            }
            // Deep-link (D-16): when the notification delegate flips the router flag, present the sheet
            // (and clear the flag so a later tap re-triggers). Mirrored INSIDE the consented branch only.
            .onChange(of: router.presentCheckIn) { _, shouldPresent in
                if shouldPresent {
                    presentingCheckIn = true
                    router.presentCheckIn = false
                }
            }
            .onAppear {
                // Honor a deep-link that arrived before HomeView was on screen (tap-to-launch).
                if router.presentCheckIn {
                    presentingCheckIn = true
                    router.presentCheckIn = false
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
    ///
    /// WR-07: a fetch error is SURFACED, not swallowed to `[]` — silently mapping a store error to an
    /// empty result would misreport a day that actually has logged symptoms/bleeding as "Note only",
    /// misrepresenting the user's own health data. On failure we say so explicitly instead.
    private func summaryLine(for date: Date) -> String {
        let cal = Calendar.current
        let readings: [SchemaV1.SymptomReading]
        let events: [SchemaV1.BleedingEvent]
        do {
            readings = try modelContext.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
            events = try modelContext.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
        } catch {
            // Don't fabricate "Note only" from a failed read — make the failure visible so a transient
            // store error never masquerades as "no data logged".
            return "Couldn't load this day"
        }
        let symptomCount = readings.filter { cal.isDate($0.occurredOn, inSameDayAs: date) }.count

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
