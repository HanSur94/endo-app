// Requirement: SYMP-01 / SYMP-03 / CYCL-01 — the fast day-log editor (D-02, D-03, D-05, D-07).
//
// The thinnest viable logging surface for one calendar day: multi-select endo-symptom chips, each
// chosen symptom revealing a `SeverityControl` (0–10); a bleeding flow control (+ optional clots);
// and a length-bounded free-text note. Save commits everything through the extended `Repository`
// (Task 1) and dismisses.
//
// MV pattern (D-12): NO ViewModel — the view reads/writes through a `SwiftDataRepository` built from
// the injected `modelContext`. It pre-loads any existing readings/bleeding/note for `day` on appear,
// so re-opening a day shows current state — the edit affordance Plan 04 reuses.
//
// Copy posture (D-14): the bleeding noun resolves through `Terminology`; symptom/flow labels come
// from the audited `plainLabel`s. No hard-coded "period"/gendered/medical-purpose verbs.
//
// Severity is clamped 0...10 both here (SeverityControl) and at the Repository seam (T-02-08); the
// note is bounded at the Repository seam (T-02-07). No health-value logging in this write path (V7).
import SwiftUI
import SwiftData

struct DayLogEditorView: View {
    /// The calendar day this editor logs for.
    let day: Date

    @Environment(\.modelContext) private var modelContext
    @Environment(\.terminologyStore) private var terminologyStore
    @Environment(\.dismiss) private var dismiss

    // Local editable draft state (committed to the store only on Save).
    @State private var severities: [SymptomType: Int] = [:]   // selected symptom -> severity
    @State private var painSeverities: [BodyRegion: Int] = [:] // marked body region -> severity
    @State private var flow: FlowIntensity?
    @State private var clots = false
    @State private var note = ""
    @State private var didLoad = false
    @State private var showingDeleteConfirmation = false
    @State private var hasExistingLog = false

    private var terminology: Terminology {
        Terminology(preference: terminologyStore.preference)
    }

    private var repository: SwiftDataRepository {
        SwiftDataRepository(context: modelContext)
    }

    var body: some View {
        Form {
            symptomSection
            bodyMapSection
            bleedingSection
            noteSection
            deleteSection
        }
        .navigationTitle(day.formatted(date: .abbreviated, time: .omitted))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save", action: save)
                    .accessibilityIdentifier("daylog.saveButton")
            }
        }
        .confirmationDialog(
            "Delete this day's log?",
            isPresented: $showingDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive, action: deleteDay)
                .accessibilityIdentifier("daylog.deleteConfirm")
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the symptoms, pain areas, bleeding, and note logged for this day. "
                 + "It cannot be undone.")
        }
        .onAppear(perform: loadExistingState)
    }

    // MARK: - Sections

    private var symptomSection: some View {
        Section("Symptoms") {
            ForEach(SymptomType.allCases, id: \.self) { type in
                let isSelected = severities[type] != nil
                VStack(alignment: .leading, spacing: 8) {
                    Button {
                        toggle(type)
                    } label: {
                        HStack {
                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                            Text(type.plainLabel)
                                .foregroundStyle(.primary)
                            Spacer()
                        }
                    }
                    .accessibilityIdentifier("daylog.symptom.\(type.rawValue)")
                    .accessibilityAddTraits(isSelected ? .isSelected : [])

                    if isSelected {
                        SeverityControl(severity: severityBinding(for: type))
                            .padding(.leading, 28)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    private var bodyMapSection: some View {
        Section("Pain areas") {
            BodyMapView(severities: $painSeverities)
                .accessibilityIdentifier("daylog.bodyMap")
        }
    }

    @ViewBuilder
    private var deleteSection: some View {
        // Only offer delete for a day that already has logged data (an edit of a past/existing day).
        if hasExistingLog {
            Section {
                Button(role: .destructive) {
                    showingDeleteConfirmation = true
                } label: {
                    Text("Delete this day")
                }
                .accessibilityIdentifier("daylog.deleteButton")
            }
        }
    }

    private var bleedingSection: some View {
        Section(terminology.bleedingNoun.capitalized) {
            ForEach(FlowIntensity.allCases, id: \.self) { intensity in
                Button {
                    flow = (flow == intensity) ? nil : intensity
                } label: {
                    HStack {
                        Text(intensity.plainLabel)
                            .foregroundStyle(.primary)
                        Spacer()
                        if flow == intensity {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                }
                .accessibilityIdentifier("daylog.bleeding.\(intensity.rawValue)")
                .accessibilityAddTraits(flow == intensity ? .isSelected : [])
            }
            Toggle("Clots", isOn: $clots)
                .accessibilityIdentifier("daylog.clotsToggle")
                .disabled(flow == nil)
        }
    }

    private var noteSection: some View {
        Section("Note") {
            TextField("How was your day?", text: $note, axis: .vertical)
                .lineLimit(1...4)
                .accessibilityIdentifier("daylog.note")
        }
    }

    // MARK: - Bindings & actions

    private func severityBinding(for type: SymptomType) -> Binding<Int> {
        Binding(
            get: { severities[type] ?? 6 },   // moderate default when a symptom is first selected
            set: { severities[type] = min(10, max(0, $0)) }
        )
    }

    private func toggle(_ type: SymptomType) {
        if severities[type] == nil {
            severities[type] = 6   // moderate default (D-02), refined via the SeverityControl
        } else {
            severities[type] = nil
        }
    }

    /// Pre-load any existing state for `day` so re-opening shows current values (Plan 04 edit host).
    private func loadExistingState() {
        guard !didLoad else { return }
        didLoad = true
        let cal = Calendar.current

        let readings = (try? modelContext.fetch(FetchDescriptor<SchemaV1.SymptomReading>())) ?? []
        let dayReadings = readings.filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
        for reading in dayReadings {
            if let raw = reading.typeRaw, let value = SymptomType(rawValue: raw) {
                severities[value] = reading.severity ?? 6
            }
        }

        let points = (try? modelContext.fetch(FetchDescriptor<SchemaV1.PainPoint>())) ?? []
        let dayPoints = points.filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
        for point in dayPoints {
            if let region = BodyRegion(raw: point.regionRaw) {
                painSeverities[region] = point.severity ?? 6
            }
        }

        let events = (try? modelContext.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())) ?? []
        let dayEvent = events.first { cal.isDate($0.occurredAt, inSameDayAs: day) }
        if let dayEvent {
            flow = FlowIntensity(raw: dayEvent.flowRaw)
            clots = dayEvent.clots ?? false
        }

        let entries = (try? modelContext.fetch(FetchDescriptor<SchemaV1.DayEntry>())) ?? []
        let dayEntry = entries.first { cal.isDate($0.date, inSameDayAs: day) }
        if let dayEntry {
            note = dayEntry.note ?? ""
        }

        // The day is an EDIT (offer delete) when any record already exists for it.
        hasExistingLog = !dayReadings.isEmpty || !dayPoints.isEmpty
            || dayEvent != nil || dayEntry != nil
    }

    /// Commit the draft through the Repository (Task 1) and dismiss.
    private func save() {
        let repo = repository
        // Symptoms: upsert selected, remove deselected.
        for type in SymptomType.allCases {
            if let severity = severities[type] {
                try? repo.upsertSymptom(type: type, severity: severity, on: day)
            } else {
                try? repo.removeSymptom(type: type, on: day)
            }
        }
        // Pain points: a marked region writes a PainPoint; an unmarked region (nil) is removed.
        for region in BodyRegion.allCases {
            if let severity = painSeverities[region] {
                try? repo.setPainPoint(region: region, severity: severity, on: day)
            } else {
                try? repo.removePainPoint(region: region, on: day)
            }
        }
        // Bleeding (+ clots): nil flow clears the day's bleeding.
        try? repo.setBleeding(intensity: flow, clots: flow == nil ? false : clots, on: day)
        // Note (bounded + empty -> nil at the seam). Always called so the day gets a `DayEntry`
        // anchor — even a symptom/bleeding-only day appears in the home `@Query(DayEntry)` history.
        try? repo.setNote(note, on: day)

        dismiss()
    }

    /// Delete the whole day (SYMP-04): cascades symptoms / pain points / bleeding / DayEntry, then
    /// dismisses. SwiftData observation refreshes the home history + cycle summary immediately (D-06).
    private func deleteDay() {
        try? repository.deleteDay(day)
        dismiss()
    }
}
