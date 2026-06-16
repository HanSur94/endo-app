// Requirement: D-13 — the real home screen, replacing `WalkingSkeletonView`.
//
// Reachable ONLY from `AppGate`'s `.consented` branch (T-01-03 / T-02-06 carried): by construction
// no logging UI is built before consent. It shows:
//   - a reverse-chronological day history list (`@Query(sort: \DayEntry.date, order: .reverse)`),
//     rows id `home.dayRow.<index>`, each a brief symptom/bleeding summary line;
//   - a prominent "Log today" affordance (`home.logTodayButton`) presenting `DayLogEditorView` for today;
//   - the gear -> `SettingsView` toolbar route (`home.settingsButton`, carried from the skeleton);
//   - the cycle-summary slot (`home.cycleSummary`).
//
// VISUAL (quick task 260615-wld): restyled to the Bloom design system — a warm canvas, a rose→plum
// gradient cycle card with a decorative progress ring, a plum primary check-in CTA, a tinted quick-log
// tile row, an honest "Not medical advice"-tagged insight teaser, and light surface history rows. This
// is a RESTYLE: every @Query/@State/@Environment, the editingDay/presentingCheckIn sheets, the deep-link
// router wiring, delete/summaryLine/hasAppleHealthSource, and EVERY accessibility identifier are unchanged.
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
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header

                    // Cycle summary (CYCL-02/03/04): the rose→plum gradient card with a decorative ring.
                    cycleCard

                    // Prominent fast "Daily check-in" CTA (CHCK-01 / D-16) — the plum primary button.
                    checkInCard

                    // Quick-log tile row — the "Log today" affordance (home.logTodayButton) lives here.
                    quickLogTiles

                    // Honest insight teaser carrying the "Not medical advice" tag.
                    insightTeaser

                    historySection
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .background(Color.canvas)
            .scrollContentBackground(.hidden)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        SettingsView()
                    } label: {
                        Image(systemName: "gearshape")
                            .foregroundStyle(Color.plum)
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

    // MARK: - Sections (visual)

    /// "Good morning" eyebrow + a serif date heading. ("Good morning" passes ClaimsPolicy — no forbidden
    /// verb/gendered/imperative/alarmist substring.)
    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            EyebrowLabel(text: "Good morning")
            Text(Date.now, format: .dateTime.weekday(.wide).month(.wide).day())
                .bloomTitle()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The rose→plum gradient cycle card wrapping `CycleSummaryView`, with a decorative progress ring
    /// and an "Estimate from your cycles" chip. The ring is purely decorative — no fabricated value.
    private var cycleCard: some View {
        ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: 14) {
                EyebrowLabel(text: "Your cycle", tint: .white.opacity(0.85))
                CycleSummaryView()
                    .environment(\.colorScheme, .dark)   // light text on the gradient (children stay)
                BloomChip(
                    text: "Estimate from your cycles",
                    systemImage: "calendar",
                    background: .white.opacity(0.18),
                    foreground: .white,
                    border: .white.opacity(0.3)
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)

            // Decorative ring — neutral when no value is available, never a fabricated reading.
            Circle()
                .trim(from: 0, to: 0.62)
                .stroke(Color.white.opacity(0.65), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .frame(width: 44, height: 44)
                .rotationEffect(.degrees(-90))
                .padding(20)
                .accessibilityHidden(true)
        }
        .background(
            LinearGradient(
                colors: [Color.rose, Color.plum],
                startPoint: .topLeading, endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
        .shadow(color: Color.plum.opacity(0.22), radius: 18, x: 0, y: 10)
    }

    /// The daily check-in card with the plum primary CTA (home.dailyCheckInButton unchanged).
    private var checkInCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Take a moment for today").bloomH3()
            Text("A quick check-in keeps your patterns rich and personal.")
                .font(.subheadline)
                .foregroundStyle(Color.inkSecondary)
            Button {
                presentingCheckIn = true
            } label: {
                Label(CheckInCopy.title, systemImage: "checkmark.circle")
            }
            .bloomPrimaryButton()
            .accessibilityIdentifier("home.dailyCheckInButton")
        }
        .bloomCard()
    }

    /// A row of tinted quick-log tiles. The "Log today" affordance (home.logTodayButton) is one tile and
    /// keeps its exact id + action (editingDay = startOfDay).
    private var quickLogTiles: some View {
        HStack(spacing: 12) {
            Button {
                editingDay = Calendar.current.startOfDay(for: .now)
            } label: {
                quickTile(symbol: "square.and.pencil", label: "Log today", tint: .plum)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("home.logTodayButton")

            Button {
                presentingCheckIn = true
            } label: {
                quickTile(symbol: "checkmark.circle", label: "Check in", tint: .rose)
            }
            .buttonStyle(.plain)
            .accessibilityHidden(true)   // mirrors home.dailyCheckInButton; not a new contract id
        }
    }

    private func quickTile(symbol: String, label: String, tint: Color) -> some View {
        VStack(spacing: 8) {
            ZStack {
                Circle().fill(tint.opacity(0.14)).frame(width: 52, height: 52)
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(tint)
            }
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.ink)
        }
        .frame(maxWidth: .infinity)
        .bloomCard(padding: 14)
    }

    /// An honest insight teaser — presentational chrome carrying the allowed-framing "Not medical advice"
    /// tag, pointing toward the Insights tab. No fabricated insight content.
    private var insightTeaser: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                EyebrowLabel(text: "Insights")
                Spacer()
                BloomChip(text: "Not medical advice", systemImage: "info.circle")
            }
            Text("Your patterns build over time")
                .bloomH3()
            Text("As you log, the Insights tab surfaces trends and possible associations in your own data.")
                .font(.subheadline)
                .foregroundStyle(Color.inkSecondary)
        }
        .bloomCard()
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            EyebrowLabel(text: "History")
            if days.isEmpty {
                Text("No days logged yet. Tap \u{201C}Log today\u{201D} to start.")
                    .font(.subheadline)
                    .foregroundStyle(Color.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .bloomCard()
            } else {
                ForEach(Array(days.enumerated()), id: \.element.id) { index, day in
                    Button {
                        editingDay = Calendar.current.startOfDay(for: day.date)
                    } label: {
                        dayRow(day)
                    }
                    .buttonStyle(.plain)
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
                .foregroundStyle(Color.ink)
            Text(summaryLine(for: day.date))
                .font(.subheadline)
                .foregroundStyle(Color.inkSecondary)
            // Provenance (D-03): when ANY field for this day was filled from an imported sample, show the
            // "from Apple Health" badge so the user knows the value came from Apple Health, not their own
            // logging. Manual-wins means a day with only manual data shows NO badge; a denied/empty import
            // contributes no `.appleHealth` source, so no badge is fabricated (SC3 honesty for provenance).
            if hasAppleHealthSource(for: day.date) {
                HealthSourceBadge()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.strokeSubtle, lineWidth: 1)
        )
    }

    /// Whether any merged field for `date` was sourced from Apple Health (D-03). Reads the SEPARATE
    /// app-side `daySnapshotSources(on:)` provenance (09-02) — NOT a HealthKit read-status probe. A store
    /// error degrades to `false` (no badge) rather than crashing or fabricating provenance.
    private func hasAppleHealthSource(for date: Date) -> Bool {
        guard let sources = try? SwiftDataRepository(context: modelContext).daySnapshotSources(on: date)
        else { return false }
        return [sources.flow, sources.sleep, sources.activity, sources.food].contains(.appleHealth)
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
