// Requirement: SYMP-01 / SYMP-03 / CYCL-01 / LIFE-01 / LIFE-02 / LIFE-03 — the day-log editor
// (D-02..D-09).
//
// The viable logging surface for one calendar day: multi-select endo-symptom chips, each chosen
// symptom revealing a `SeverityControl` (0–10); a bleeding flow control (+ optional clots); a
// length-bounded free-text note; and (Plan 03-02) the six lifestyle FACTOR sections — food
// tags + free-text, activity type/duration/intensity, sleep hours/quality, per-day medication
// (taken + PRN), stress (reveal-on-opt-in SeverityControl), and overall day-mood. Save commits
// everything through the extended `Repository` and dismisses.
//
// MV pattern (D-12): NO ViewModel — the view reads/writes through a `SwiftDataRepository` built from
// the injected `modelContext`. It pre-loads any existing readings/bleeding/note/factors for `day` on
// appear, so re-opening a day shows current state — the edit affordance Plan 04 reuses.
//
// Copy posture (D-14/D-21): the bleeding noun resolves through `Terminology`; symptom/flow/factor
// labels come from the audited `plainLabel`s. No hard-coded "period"/gendered/medical-purpose verbs
// (no "trigger" in food copy — these are tags, not triggers).
//
// HONESTY (D-07/D-08, T-03-06): the stress + mood drafts are `Int?` and stay nil until the user
// opts in (stress is gated behind a "Log stress" reveal; mood until a value is tapped) — an
// untouched section reads "Not logged", never a fabricated 0.
//
// Severity/stress are clamped 0...10 both here (SeverityControl) and at the Repository seam; free
// text is bounded at the Repository seam (T-02-07 / T-03-01).
import SwiftUI
import SwiftData

struct DayLogEditorView: View {
    /// The calendar day this editor logs for.
    let day: Date

    @Environment(\.modelContext) private var modelContext
    @Environment(\.terminologyStore) private var terminologyStore
    @Environment(\.dismiss) private var dismiss
    /// The insights coordinator (08-02). After a successful save we call `dataChanged()` — the EXPLICIT,
    /// deterministic recompute seam (RESEARCH Open Question 3) — so newly-logged data debounces into one
    /// off-main recompute and the dashboard reflects it (D-01).
    @Environment(\.insightsCoordinator) private var insightsCoordinator

    // Local editable draft state (committed to the store only on Save).
    @State private var severities: [SymptomType: Int] = [:]   // selected symptom -> severity
    @State private var painSeverities: [BodyRegion: Int] = [:] // marked body region -> severity
    @State private var flow: FlowIntensity?
    @State private var clots = false
    @State private var note = ""
    @State private var didLoad = false
    @State private var showingDeleteConfirmation = false
    @State private var hasExistingLog = false

    // MARK: Lifestyle-factor drafts (Plan 03-02). nil/empty == "not logged" — absence is honest.
    @State private var foodTags: Set<FoodTag> = []        // multi-select food/drink tags (D-03)
    @State private var foodNote = ""                       // optional free-text "Anything else?"
    @State private var activityType: ActivityType?         // single-select; nil == not logged (D-04)
    @State private var activityMinutes: Double = 0         // duration; 0 + no type == not logged
    @State private var activityIntensity: ActivityIntensity?   // text-labelled; nil == not set
    @State private var sleepHours: Double = 0              // duration in hours; nil-ness via flag below
    @State private var sleepHoursSet = false               // distinguishes "0h logged" from "not logged"
    @State private var sleepQuality: SleepQuality?         // text-labelled; nil == not set
    @State private var savedMeds: [SchemaV1.Medication] = []   // the user's saved-med list (D-06)
    @State private var medsTaken: Set<UUID> = []           // which saved meds are marked taken today
    @State private var tookPRN = false                     // as-needed pain-relief marker
    @State private var loggingStress = false               // stress reveal opt-in (T-03-06)
    @State private var stress: Int?                         // 0...10; nil == not logged (D-07)
    @State private var mood: Int?                          // 1...5 valence; nil == not logged (D-08)

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
            foodSection
            activitySection
            sleepSection
            medicationSection
            stressSection
            moodSection
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

    // MARK: - Factor sections (Plan 03-02 — LIFE-01/02/03)

    /// Food & drink (LIFE-01, D-03): multi-select tags + optional free-text. NOT a calorie diary,
    /// and NOT "triggers" — these are neutral category tags (copy avoids the forbidden "trigger" verb).
    private var foodSection: some View {
        Section {
            ForEach(FoodTag.allCases, id: \.self) { tag in
                let isSelected = foodTags.contains(tag)
                Button {
                    if isSelected { foodTags.remove(tag) } else { foodTags.insert(tag) }
                } label: {
                    HStack {
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                        Text(tag.plainLabel)
                            .foregroundStyle(.primary)
                        Spacer()
                    }
                }
                .accessibilityIdentifier("daylog.factor.food.\(tag.rawValue)")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
            TextField("Anything else?", text: $foodNote, axis: .vertical)
                .lineLimit(1...3)
                .accessibilityIdentifier("daylog.factor.food.freeText")

            if foodTags.isEmpty && foodNote.isEmpty {
                notLoggedPlaceholder
            }
        } header: {
            Text("Food & drink")
        } footer: {
            Text("Optional tags to spot patterns in your own data — not a calorie diary.")
        }
    }

    /// Activity (LIFE-02, D-04): single-select type + duration (min) + text-labelled intensity.
    /// Unlogged until a type is chosen.
    private var activitySection: some View {
        Section("Activity") {
            ForEach(ActivityType.allCases, id: \.self) { type in
                Button {
                    activityType = (activityType == type) ? nil : type
                } label: {
                    HStack {
                        Text(type.plainLabel)
                            .foregroundStyle(.primary)
                        Spacer()
                        if activityType == type {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                }
                .accessibilityIdentifier("daylog.factor.activity.type.\(type.rawValue)")
                .accessibilityAddTraits(activityType == type ? .isSelected : [])
            }

            if activityType != nil {
                Stepper(value: $activityMinutes, in: 0...600, step: 5) {
                    HStack {
                        Text("Duration")
                        Spacer()
                        Text("\(Int(activityMinutes)) min")
                            .font(.subheadline.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityIdentifier("daylog.factor.activity.duration")
                .accessibilityValue("\(Int(activityMinutes)) minutes")

                Picker("Intensity", selection: $activityIntensity) {
                    Text("—").tag(ActivityIntensity?.none)
                    ForEach(ActivityIntensity.allCases, id: \.self) { level in
                        Text(level.plainLabel).tag(ActivityIntensity?.some(level))
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("daylog.factor.activity.intensity")
            } else {
                notLoggedPlaceholder
            }
        }
    }

    /// Sleep (LIFE-03, D-05): hours (0.5h steps) + text-labelled quality. Unlogged until either set.
    private var sleepSection: some View {
        Section("Sleep") {
            Stepper(value: $sleepHours, in: 0...24, step: 0.5) {
                HStack {
                    Text("Hours")
                    Spacer()
                    Text(sleepHoursSet ? "\(sleepHours, specifier: "%.1f") h" : "—")
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("daylog.factor.sleep.hours")
            .accessibilityValue(sleepHoursSet ? "\(sleepHours, specifier: "%.1f") hours" : "not logged")
            .onChange(of: sleepHours) { _, _ in sleepHoursSet = true }

            Picker("Quality", selection: $sleepQuality) {
                Text("—").tag(SleepQuality?.none)
                ForEach(SleepQuality.allCases, id: \.self) { quality in
                    Text(quality.plainLabel).tag(SleepQuality?.some(quality))
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("daylog.factor.sleep.quality")

            if !sleepHoursSet && sleepQuality == nil {
                notLoggedPlaceholder
            }
        }
    }

    /// Medication (LIFE-03, D-06): per-day "taken today" toggles over the saved list + a PRN toggle.
    /// A med not toggled = not taken / not logged (never a clinical "skipped" claim).
    @ViewBuilder
    private var medicationSection: some View {
        Section {
            if savedMeds.isEmpty {
                Text("No saved medications yet. Add one in Settings to mark it taken here.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(savedMeds, id: \.id) { med in
                    Toggle(isOn: medTakenBinding(for: med.id)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(med.name ?? "Medication")
                                .foregroundStyle(.primary)
                            if let note = med.note, !note.isEmpty {
                                Text(note)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .accessibilityIdentifier("daylog.factor.medication.\(med.id.uuidString)")
                }
            }
            Toggle("As-needed pain relief", isOn: $tookPRN)
                .accessibilityIdentifier("daylog.factor.medication.prn")
        } header: {
            Text("Medication")
        }
    }

    /// Stress (LIFE-03, D-07): reveal-on-opt-in `SeverityControl`. `stress` stays nil until the user
    /// taps "Log stress", so an untouched section never implies "0 = no stress" (T-03-06).
    private var stressSection: some View {
        Section("Stress") {
            if loggingStress, stress != nil {
                SeverityControl(severity: stressBinding)
                    .accessibilityIdentifier("daylog.factor.stress")
                Button("Clear stress") {
                    loggingStress = false
                    stress = nil
                }
                .accessibilityIdentifier("daylog.factor.stress.clear")
            } else {
                Button {
                    loggingStress = true
                    stress = 5   // a deliberate mid value once the user opts to log
                } label: {
                    HStack {
                        Text("Log stress")
                            .foregroundStyle(.primary)
                        Spacer()
                        Text("Not logged")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityIdentifier("daylog.factor.stress.log")
            }
        }
    }

    /// Mood (LIFE-03, D-08): an overall 1–5 day-mood, text-labelled + SF Symbols, kept distinct from
    /// the Phase-2 "Low mood" acute symptom. `mood` stays nil until a value is tapped.
    private var moodSection: some View {
        Section {
            HStack(spacing: 8) {
                ForEach(Self.moodScale, id: \.value) { option in
                    let isSelected = mood == option.value
                    Button {
                        mood = isSelected ? nil : option.value
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: option.symbol)
                                .imageScale(.large)
                            Text(option.label)
                                .font(.caption)
                                .fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("daylog.factor.mood.\(option.value)")
                    .accessibilityLabel(option.label)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            // Keep the row addressable as `daylog.factor.mood` WITHOUT collapsing the per-value
            // buttons into one element (children stay individually tappable for VoiceOver + E2E).
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("daylog.factor.mood")

            if mood == nil {
                notLoggedPlaceholder
            }
        } header: {
            Text("Overall mood for the day")
        }
    }

    /// Shared "Not logged" placeholder for an untouched factor section (honesty constraint).
    private var notLoggedPlaceholder: some View {
        Text("Not logged")
            .font(.subheadline)
            .foregroundStyle(.secondary)
    }

    /// The 1–5 day-mood scale: value + plain text label + a non-color SF Symbol cue.
    private static let moodScale: [(value: Int, label: String, symbol: String)] = [
        (1, "Very low", "cloud.rain"),
        (2, "Low", "cloud"),
        (3, "Okay", "cloud.sun"),
        (4, "Good", "sun.max"),
        (5, "Great", "sparkles"),
    ]

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

    /// Per-med "taken today" toggle binding (D-06). Membership in `medsTaken` == taken.
    private func medTakenBinding(for id: UUID) -> Binding<Bool> {
        Binding(
            get: { medsTaken.contains(id) },
            set: { taken in
                if taken { medsTaken.insert(id) } else { medsTaken.remove(id) }
            }
        )
    }

    /// Stress 0...10 binding for the revealed `SeverityControl`. Reads through the `Int?` draft;
    /// writing keeps it non-nil (the section is only shown once the user has opted in).
    private var stressBinding: Binding<Int> {
        Binding(
            get: { min(10, max(0, stress ?? 0)) },
            set: { stress = min(10, max(0, $0)) }
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

        // Lifestyle factors (Plan 03-02): load the saved-med list + this day's FactorDay snapshot.
        let repo = repository
        savedMeds = (try? repo.savedMedications()) ?? []
        let factorDay = (try? repo.factorDays())?.first { cal.isDate($0.day, inSameDayAs: day) }
        if let factorDay {
            foodTags = Set(factorDay.foodTags)
            foodNote = factorDay.foodNote ?? ""
            activityType = factorDay.activityType
            if let minutes = factorDay.activityMinutes { activityMinutes = minutes }
            activityIntensity = factorDay.activityIntensity
            if let hours = factorDay.sleepHours {
                sleepHours = hours
                sleepHoursSet = true
            }
            sleepQuality = factorDay.sleepQuality
            medsTaken = Set(factorDay.medicationsTaken)
            tookPRN = factorDay.tookPRN
            if let loggedStress = factorDay.stress {
                stress = loggedStress
                loggingStress = true
            }
            mood = factorDay.mood
        }
        let hasFactorLog = factorDay != nil

        // The day is an EDIT (offer delete) when any record already exists for it.
        hasExistingLog = !dayReadings.isEmpty || !dayPoints.isEmpty
            || dayEvent != nil || dayEntry != nil || hasFactorLog
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
        // Lifestyle factors (Plan 03-02). Each setter is calendar-bucketed + consent-gated + bounded
        // at the Repository seam; nil-able drafts clear the day's reading so absence stays honest.
        try? repo.setFood(
            tags: Array(foodTags),
            freeText: foodNote.isEmpty ? nil : foodNote,
            on: day)
        try? repo.setActivity(
            type: activityType,
            minutes: activityType == nil ? nil : activityMinutes,
            intensity: activityType == nil ? nil : activityIntensity,
            on: day)
        try? repo.setSleep(
            hours: sleepHoursSet ? sleepHours : nil,
            quality: sleepQuality,
            on: day)
        for med in savedMeds {
            try? repo.setMedicationTaken(medID: med.id, taken: medsTaken.contains(med.id), on: day)
        }
        try? repo.setPRN(tookPRN, on: day)
        try? repo.setStress(loggingStress ? stress : nil, on: day)   // nil clears (D-07)
        try? repo.setMood(mood, on: day)                              // nil clears (D-08)

        // Note (bounded + empty -> nil at the seam). Always called so the day gets a `DayEntry`
        // anchor — even a symptom/bleeding-only day appears in the home `@Query(DayEntry)` history.
        try? repo.setNote(note, on: day)

        // Newly-logged data changed the insight inputs: kick the EXPLICIT, debounced recompute seam
        // (RESEARCH OQ3) so the dashboard reflects this save without a manual refresh (D-01).
        insightsCoordinator.dataChanged()

        dismiss()
    }

    /// Delete the whole day (SYMP-04): cascades symptoms / pain points / bleeding / DayEntry, then
    /// dismisses. SwiftData observation refreshes the home history + cycle summary immediately (D-06).
    private func deleteDay() {
        try? repository.deleteDay(day)
        dismiss()
    }
}
