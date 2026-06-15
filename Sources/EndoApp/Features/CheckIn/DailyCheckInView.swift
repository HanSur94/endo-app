// Requirement: CHCK-01 — the fast, single-sheet daily check-in (D-13/14/15/16/19b/21).
//
// A genuinely FAST way to record today: one scrollable, sectioned `Form` in a `.sheet` with smart
// defaults and NOTHING mandatory (D-15). Sections, in order: quick symptom chips → the six lifestyle
// factor sections (food / activity / sleep / medication / stress / mood) → bleeding (the Phase-2 flow
// control). Each section is independently skippable. Confirming with "Done for today" commits whatever
// was touched through the SAME `Repository` the detailed editor uses; an EMPTY confirm records only a
// lightweight `checkInMarker(on:)` adherence anchor WITHOUT inventing any symptom/factor data (D-15).
//
// "Same as yesterday" (D-14) prefills STICKY CONTEXT ONLY — meds taken, sleep (hours/quality),
// activity (type/minutes/intensity) — read from yesterday's `FactorDay`. It NEVER touches symptom or
// bleeding draft state: those are events, and carrying them forward fabricates data. Prefilled values
// are fully editable before saving.
//
// After a successful commit the sheet dismisses immediately and fires `scheduler.cancelToday()`
// (injected via `@Environment(\.notificationScheduler)`) as an independent task to auto-suppress
// today's reminder (D-19b). Suppression records a today-logged marker (CR-01) consulted at delivery —
// the repeating daily trigger is NOT removed, so it re-fires tomorrow on its own.
//
// MV pattern (D-12): NO ViewModel — the view reads/writes through a `SwiftDataRepository` built from
// the injected `modelContext`, mirroring `DayLogEditorView`. HONESTY (D-07/D-08, T-03-06): stress +
// mood drafts are `Int?` and stay nil until opted-in; an untouched section reads "Not logged".
//
// Copy posture (D-21): the bleeding noun resolves through `Terminology`; the gentle, non-coercive
// labels ("Daily check-in", "Done for today", "Same as yesterday", "Checked in for today.") route
// through `CheckInCopy` and are audited by `CopyAuditTests`.
import SwiftUI
import SwiftData

/// User-facing check-in copy. Kept as constants so `CopyAuditTests` can read the exact strings (D-21).
enum CheckInCopy {
    /// The Home CTA + sheet title.
    static let title = "Daily check-in"
    /// The gentle, non-coercive confirm label.
    static let confirm = "Done for today"
    /// The sticky-context prefill control.
    static let sameAsYesterday = "Same as yesterday"
    /// The sticky-context prefill subtitle — makes the honesty boundary explicit (D-14).
    static let sameAsYesterdaySubtitle =
        "Brings back your usual meds, sleep and activity. Symptoms and bleeding are never carried over."
    /// The empty-confirm adherence confirmation (no data invented, D-15).
    static let checkedIn = "Checked in for today."

    /// Everything `CopyAuditTests` audits for forbidden verbs / gendered tokens.
    static var auditableStrings: [String] {
        [title, confirm, sameAsYesterday, sameAsYesterdaySubtitle, checkedIn]
    }
}

struct DailyCheckInView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.terminologyStore) private var terminologyStore
    @Environment(\.notificationScheduler) private var notificationScheduler
    @Environment(\.dismiss) private var dismiss

    /// Today (start-of-day) — the single day this fast check-in records.
    private let today = Calendar.current.startOfDay(for: .now)

    // MARK: Symptom + bleeding event drafts (NEVER prefilled by "Same as yesterday" — D-14).
    @State private var severities: [SymptomType: Int] = [:]
    @State private var flow: FlowIntensity?
    @State private var clots = false

    // MARK: Sticky-context factor drafts (prefillable from yesterday — D-14).
    @State private var foodTags: Set<FoodTag> = []
    @State private var foodNote = ""
    @State private var activityType: ActivityType?
    @State private var activityMinutes: Double = 0
    @State private var activityIntensity: ActivityIntensity?
    @State private var sleepHours: Double = 0
    @State private var sleepHoursSet = false
    @State private var sleepQuality: SleepQuality?
    @State private var savedMeds: [SchemaV1.Medication] = []
    @State private var medsTaken: Set<UUID> = []
    @State private var tookPRN = false
    @State private var loggingStress = false
    @State private var stress: Int?
    @State private var mood: Int?

    @State private var didLoad = false
    @State private var checkedInConfirmation = false   // shows the inline "Checked in for today." note

    private var terminology: Terminology {
        Terminology(preference: terminologyStore.preference)
    }

    private var repository: SwiftDataRepository {
        SwiftDataRepository(context: modelContext)
    }

    var body: some View {
        Form {
            sameAsYesterdaySection
            symptomSection
            foodSection
            activitySection
            sleepSection
            medicationSection
            stressSection
            moodSection
            bleedingSection
            if checkedInConfirmation { confirmationSection }
        }
        .scrollContentBackground(.hidden)
        .background(Color.canvas)
        .accessibilityIdentifier("checkin.sheet")
        .navigationTitle(CheckInCopy.title)
        .navigationBarTitleDisplayMode(.inline)
        .tint(Color.plum)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: confirm) {
                    Text(CheckInCopy.confirm).fontWeight(.semibold)
                }
                .foregroundStyle(Color.plum)
                .accessibilityIdentifier("checkin.confirmButton")
            }
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
                    .foregroundStyle(Color.inkSecondary)
            }
        }
        .onAppear(perform: loadExistingState)
    }

    // MARK: - Sections

    /// "Same as yesterday" (D-14): prefills STICKY CONTEXT ONLY. The handler reads yesterday's
    /// `FactorDay` and writes ONLY meds/sleep/activity drafts — it NEVER touches `severities`, `flow`,
    /// or `clots`, because symptoms and bleeding are events that must not be carried forward.
    private var sameAsYesterdaySection: some View {
        Section {
            Button(action: applySameAsYesterday) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: "arrow.uturn.backward.circle")
                            .foregroundStyle(Color.plum)
                        Text(CheckInCopy.sameAsYesterday)
                            .foregroundStyle(.primary)
                    }
                    Text(CheckInCopy.sameAsYesterdaySubtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .accessibilityIdentifier("checkin.sameAsYesterday")
        }
    }

    /// Quick multi-select symptom chips (skippable). Mirrors the editor's symptom pattern but kept
    /// fast — a moderate default severity when first selected, refinable via the revealed control.
    private var symptomSection: some View {
        Section {
            ForEach(SymptomType.allCases, id: \.self) { type in
                let isSelected = severities[type] != nil
                VStack(alignment: .leading, spacing: 8) {
                    Button {
                        toggleSymptom(type)
                    } label: {
                        HStack {
                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(isSelected ? Color.plum : Color.secondary)
                            Text(type.plainLabel).foregroundStyle(.primary)
                            Spacer()
                        }
                    }
                    .accessibilityIdentifier("checkin.symptom.\(type.rawValue)")
                    .accessibilityAddTraits(isSelected ? .isSelected : [])

                    if isSelected {
                        SeverityControl(severity: severityBinding(for: type))
                            .padding(.leading, 28)
                    }
                }
                .padding(.vertical, 2)
            }
        } header: {
            Text("Symptoms").accessibilityIdentifier("checkin.section.symptoms")
        }
    }

    /// Food & drink (LIFE-01, D-03): multi-select tags + optional free-text. Neutral category tags,
    /// never "triggers" (the forbidden verb is kept out of the copy).
    private var foodSection: some View {
        Section {
            ForEach(FoodTag.allCases, id: \.self) { tag in
                let isSelected = foodTags.contains(tag)
                Button {
                    if isSelected { foodTags.remove(tag) } else { foodTags.insert(tag) }
                } label: {
                    HStack {
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(isSelected ? Color.plum : Color.secondary)
                        Text(tag.plainLabel).foregroundStyle(.primary)
                        Spacer()
                    }
                }
                .accessibilityIdentifier("checkin.food.\(tag.rawValue)")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
            TextField("Anything else?", text: $foodNote, axis: .vertical)
                .lineLimit(1...3)
                .accessibilityIdentifier("checkin.food.freeText")

            if foodTags.isEmpty && foodNote.isEmpty { notLoggedPlaceholder }
        } header: {
            Text("Food & drink").accessibilityIdentifier("checkin.section.food")
        }
    }

    /// Activity (LIFE-02, D-04): single-select type + duration + text-labelled intensity.
    private var activitySection: some View {
        Section {
            ForEach(ActivityType.allCases, id: \.self) { type in
                Button {
                    activityType = (activityType == type) ? nil : type
                } label: {
                    HStack {
                        Text(type.plainLabel).foregroundStyle(.primary)
                        Spacer()
                        if activityType == type {
                            Image(systemName: "checkmark").foregroundStyle(Color.plum)
                        }
                    }
                }
                .accessibilityIdentifier("checkin.activity.type.\(type.rawValue)")
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
                .accessibilityIdentifier("checkin.activity.duration")
                .accessibilityValue("\(Int(activityMinutes)) minutes")

                Picker("Intensity", selection: $activityIntensity) {
                    Text("—").tag(ActivityIntensity?.none)
                    ForEach(ActivityIntensity.allCases, id: \.self) { level in
                        Text(level.plainLabel).tag(ActivityIntensity?.some(level))
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("checkin.activity.intensity")
            } else {
                notLoggedPlaceholder
            }
        } header: {
            Text("Activity").accessibilityIdentifier("checkin.section.activity")
        }
    }

    /// Sleep (LIFE-03, D-05): hours (0.5h steps) + text-labelled quality.
    private var sleepSection: some View {
        Section {
            Stepper(value: $sleepHours, in: 0...24, step: 0.5) {
                HStack {
                    Text("Hours")
                    Spacer()
                    Text(sleepHoursSet ? "\(sleepHours, specifier: "%.1f") h" : "—")
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("checkin.sleep.hours")
            .accessibilityValue(sleepHoursSet ? "\(sleepHours, specifier: "%.1f") hours" : "not logged")
            .onChange(of: sleepHours) { _, _ in sleepHoursSet = true }

            Picker("Quality", selection: $sleepQuality) {
                Text("—").tag(SleepQuality?.none)
                ForEach(SleepQuality.allCases, id: \.self) { quality in
                    Text(quality.plainLabel).tag(SleepQuality?.some(quality))
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("checkin.sleep.quality")

            if !sleepHoursSet && sleepQuality == nil { notLoggedPlaceholder }
        } header: {
            Text("Sleep").accessibilityIdentifier("checkin.section.sleep")
        }
    }

    /// Medication (LIFE-03, D-06): per-day "taken today" toggles over the saved list + a PRN toggle.
    @ViewBuilder
    private var medicationSection: some View {
        Section {
            if savedMeds.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("No medications saved yet.")
                        .font(.headline)
                    Text("Add the medications you want to keep track of in Settings, and they'll "
                         + "appear here to tick off each day.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                ForEach(savedMeds, id: \.id) { med in
                    Toggle(isOn: medTakenBinding(for: med.id)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(med.name ?? "Medication").foregroundStyle(.primary)
                            if let note = med.note, !note.isEmpty {
                                Text(note).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .accessibilityIdentifier("checkin.medication.\(med.id.uuidString)")
                }
            }
            Toggle("As-needed pain relief", isOn: $tookPRN)
                .accessibilityIdentifier("checkin.medication.prn")
        } header: {
            Text("Medication").accessibilityIdentifier("checkin.section.medication")
        }
    }

    /// Stress (LIFE-03, D-07): reveal-on-opt-in `SeverityControl`. `stress` stays nil until opt-in.
    private var stressSection: some View {
        Section {
            if loggingStress, stress != nil {
                SeverityControl(severity: stressBinding)
                    .accessibilityIdentifier("checkin.stress")
                Button("Clear stress") {
                    loggingStress = false
                    stress = nil
                }
                .accessibilityIdentifier("checkin.stress.clear")
            } else {
                Button {
                    loggingStress = true
                    stress = 5
                } label: {
                    HStack {
                        Text("Log stress").foregroundStyle(.primary)
                        Spacer()
                        Text("Not logged").font(.subheadline).foregroundStyle(.secondary)
                    }
                }
                .accessibilityIdentifier("checkin.stress.log")
            }
        } header: {
            Text("Stress").accessibilityIdentifier("checkin.section.stress")
        }
    }

    /// Mood (LIFE-03, D-08): an overall 1–5 day-mood, text-labelled + SF Symbols. Stays nil until tapped.
    private var moodSection: some View {
        Section {
            HStack(spacing: 8) {
                ForEach(Self.moodScale, id: \.value) { option in
                    let isSelected = mood == option.value
                    Button {
                        mood = isSelected ? nil : option.value
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: option.symbol).imageScale(.large)
                            Text(option.label)
                                .font(.caption)
                                .fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundStyle(isSelected ? Color.plum : Color.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("checkin.mood.\(option.value)")
                    .accessibilityLabel(option.label)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("checkin.mood")

            if mood == nil { notLoggedPlaceholder }
        } header: {
            Text("Overall mood for the day").accessibilityIdentifier("checkin.section.mood")
        }
    }

    /// Bleeding (reuses the Phase-2 flow control). An EVENT — never prefilled by "Same as yesterday".
    private var bleedingSection: some View {
        Section {
            ForEach(FlowIntensity.allCases, id: \.self) { intensity in
                Button {
                    flow = (flow == intensity) ? nil : intensity
                } label: {
                    HStack {
                        Text(intensity.plainLabel).foregroundStyle(.primary)
                        Spacer()
                        if flow == intensity {
                            Image(systemName: "checkmark").foregroundStyle(Color.plum)
                        }
                    }
                }
                .accessibilityIdentifier("checkin.bleeding.\(intensity.rawValue)")
                .accessibilityAddTraits(flow == intensity ? .isSelected : [])
            }
            Toggle("Clots", isOn: $clots)
                .accessibilityIdentifier("checkin.clotsToggle")
                .disabled(flow == nil)
        } header: {
            Text(terminology.bleedingNoun.capitalized)
                .accessibilityIdentifier("checkin.section.bleeding")
        }
    }

    /// The inline "Checked in for today." adherence confirmation (D-15) shown after an empty confirm.
    private var confirmationSection: some View {
        Section {
            Label(CheckInCopy.checkedIn, systemImage: "checkmark.circle.fill")
                .foregroundStyle(Color.plum)
                .accessibilityIdentifier("checkin.checkedInConfirmation")
        }
    }

    /// Shared "Not logged" placeholder for an untouched factor section (honesty constraint).
    private var notLoggedPlaceholder: some View {
        Text("Not logged").font(.subheadline).foregroundStyle(.secondary)
    }

    private static let moodScale: [(value: Int, label: String, symbol: String)] = [
        (1, "Very low", "cloud.rain"),
        (2, "Low", "cloud"),
        (3, "Okay", "cloud.sun"),
        (4, "Good", "sun.max"),
        (5, "Great", "sparkles"),
    ]

    // MARK: - Bindings

    private func severityBinding(for type: SymptomType) -> Binding<Int> {
        Binding(
            get: { severities[type] ?? 6 },
            set: { severities[type] = min(10, max(0, $0)) }
        )
    }

    private func medTakenBinding(for id: UUID) -> Binding<Bool> {
        Binding(
            get: { medsTaken.contains(id) },
            set: { taken in
                if taken { medsTaken.insert(id) } else { medsTaken.remove(id) }
            }
        )
    }

    private var stressBinding: Binding<Int> {
        Binding(
            get: { min(10, max(0, stress ?? 0)) },
            set: { stress = min(10, max(0, $0)) }
        )
    }

    // MARK: - Actions

    private func toggleSymptom(_ type: SymptomType) {
        if severities[type] == nil {
            severities[type] = 6
        } else {
            severities[type] = nil
        }
    }

    /// Prefill STICKY CONTEXT ONLY from yesterday's `FactorDay` (D-14). Meds / sleep / activity only —
    /// this handler MUST NOT touch `severities`, `flow`, or `clots`: carrying symptoms/bleeding forward
    /// would fabricate events (T-03-15). Prefilled values stay fully editable before confirming.
    private func applySameAsYesterday() {
        let cal = Calendar.current
        guard let yesterday = cal.date(byAdding: .day, value: -1, to: today) else { return }
        guard let snapshot = (try? repository.factorDays())?
            .first(where: { cal.isDate($0.day, inSameDayAs: yesterday) }) else { return }

        // WR-04: prefill must FULLY REPLACE the sticky-context draft with yesterday's snapshot — never
        // blend yesterday's values with whatever `loadExistingState` left behind. Every sticky field is
        // assigned from the snapshot, using the snapshot's nil AS nil, so the result equals yesterday's
        // sticky context exactly.
        // Meds taken (sticky context).
        medsTaken = Set(snapshot.medicationsTaken)
        tookPRN = snapshot.tookPRN
        // Sleep (sticky context) — reset hours+set-flag from the snapshot atomically.
        if let hours = snapshot.sleepHours {
            sleepHours = hours
            sleepHoursSet = true
        } else {
            sleepHours = 0
            sleepHoursSet = false
        }
        sleepQuality = snapshot.sleepQuality
        // Activity (sticky context) — reset type/minutes/intensity from the snapshot atomically.
        activityType = snapshot.activityType
        activityMinutes = snapshot.activityMinutes ?? 0
        activityIntensity = snapshot.activityIntensity

        // NOTE: deliberately NOT prefilling foodTags/foodNote — food is a per-day event, not sticky
        // context; and NOTHING touches symptoms or bleeding (D-14).
    }

    /// Pre-load today's existing state + the saved-med list so a re-opened check-in shows current
    /// values (mirrors `DayLogEditorView.loadExistingState`).
    private func loadExistingState() {
        guard !didLoad else { return }
        didLoad = true
        let cal = Calendar.current
        let repo = repository

        let readings = (try? modelContext.fetch(FetchDescriptor<SchemaV1.SymptomReading>())) ?? []
        for reading in readings where cal.isDate(reading.occurredOn, inSameDayAs: today) {
            if let raw = reading.typeRaw, let value = SymptomType(rawValue: raw) {
                severities[value] = reading.severity ?? 6
            }
        }

        let events = (try? modelContext.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())) ?? []
        if let dayEvent = events.first(where: { cal.isDate($0.occurredAt, inSameDayAs: today) }) {
            flow = FlowIntensity(raw: dayEvent.flowRaw)
            clots = dayEvent.clots ?? false
        }

        savedMeds = (try? repo.savedMedications()) ?? []
        if let factorDay = (try? repo.factorDays())?
            .first(where: { cal.isDate($0.day, inSameDayAs: today) }) {
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
    }

    /// Whether the user touched ANYTHING worth committing as data (drives the empty-confirm path, D-15).
    private var hasAnyInput: Bool {
        !severities.isEmpty
            || flow != nil
            || !foodTags.isEmpty || !foodNote.isEmpty
            || activityType != nil
            || sleepHoursSet || sleepQuality != nil
            || !medsTaken.isEmpty || tookPRN
            || (loggingStress && stress != nil)
            || mood != nil
    }

    /// "Done for today" (D-13/15/19b): commit each touched section through the SAME Repository; on an
    /// EMPTY confirm record only the lightweight `checkInMarker(on:)` adherence anchor (no invented
    /// data, D-15) and surface the "Checked in for today." note. After a successful commit, auto-suppress
    /// today's reminder via `scheduler.cancelToday()` (D-19b), then dismiss.
    private func confirm() {
        let repo = repository

        if hasAnyInput {
            // Symptoms (events): upsert selected, remove deselected.
            for type in SymptomType.allCases {
                if let severity = severities[type] {
                    try? repo.upsertSymptom(type: type, severity: severity, on: today)
                } else {
                    try? repo.removeSymptom(type: type, on: today)
                }
            }
            // Bleeding (event): nil flow clears the day's bleeding.
            try? repo.setBleeding(intensity: flow, clots: flow == nil ? false : clots, on: today)
            // Lifestyle factors — same setters as the detailed editor; nil-able drafts clear honestly.
            try? repo.setFood(
                tags: Array(foodTags),
                freeText: foodNote.isEmpty ? nil : foodNote,
                on: today)
            try? repo.setActivity(
                type: activityType,
                minutes: activityType == nil ? nil : activityMinutes,
                intensity: activityType == nil ? nil : activityIntensity,
                on: today)
            try? repo.setSleep(
                hours: sleepHoursSet ? sleepHours : nil,
                quality: sleepQuality,
                on: today)
            for med in savedMeds {
                try? repo.setMedicationTaken(medID: med.id, taken: medsTaken.contains(med.id), on: today)
            }
            try? repo.setPRN(tookPRN, on: today)
            try? repo.setStress(loggingStress ? stress : nil, on: today)
            try? repo.setMood(mood, on: today)
            // Always anchor a DayEntry so the day appears in the Home @Query(DayEntry) history.
            try? repo.checkInMarker(on: today)
            autoSuppressAndDismiss()
        } else {
            // Empty confirm (D-15): record ONLY the adherence marker — invent NO symptom/factor data.
            try? repo.checkInMarker(on: today)
            checkedInConfirmation = true
            autoSuppressAndDismiss()
        }
    }

    /// Auto-suppress today's reminder (D-19b) and dismiss. WR-06: dismissal is NOT gated on the
    /// best-effort notification side effect — dismiss immediately and fire the suppression as a fully
    /// independent task so a slow/hanging notification-center call never delays the sheet closing.
    /// `cancelToday()` records the today-logged marker; the repeating registration stays intact and
    /// re-fires tomorrow (CR-01).
    private func autoSuppressAndDismiss() {
        let scheduler = notificationScheduler
        Task { await scheduler.cancelToday() }
        dismiss()
    }
}
