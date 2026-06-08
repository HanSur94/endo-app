// Requirement: D-04 (withdraw consent in Settings) + D-07 (optional terminology/identity
// customization) + ONBD-04 (privacy reachable from Settings).
//
// Reachable from the main shell. It lets the user:
//   (a) withdraw consent — with a plain explanation of what withdrawal means — which re-gates the
//       app (the `@Observable` ConsentStore flips `state`, so `AppGate` re-routes to onboarding);
//   (b) customize terminology (bleeding term), pronouns, and display name (D-07, optional, never a
//       forced wall);
//   (c) open the offline privacy policy.
//   (d) opt into gentle daily reminders (CHCK-02, D-17..21) and manage saved meds (D-06).
import SwiftUI
import UserNotifications

struct SettingsView: View {
    @Environment(\.consentStore) private var consent
    @Environment(\.terminologyStore) private var terminologyStore
    @Environment(\.modelContext) private var modelContext
    // The notification-scheduling seam (CHCK-02, D-17). Driven ONLY from the reminders enable/edit
    // handlers below — never at view init / onAppear, so permission stays just-in-time (D-20).
    @Environment(\.notificationScheduler) private var notificationScheduler

    @State private var showingWithdrawConfirmation = false

    // Local editable copies of the free-text fields, committed back to the store on change.
    @State private var displayName: String = ""
    @State private var pronouns: String = ""
    // Suppressed / no-cycle mode (CYCL-03, D-09) — reflects the latest TreatmentState.
    @State private var suppressed = false

    // MARK: - Reminders (CHCK-02) + manage-meds (D-06) state

    /// The UserDefaults-backed reminder config (CHCK-02). Default OFF (D-18). Editing it here re-arms
    /// the scheduler idempotently.
    @State private var reminders = ReminderSettings()
    /// The live notification-permission status, refreshed on appear / after the JIT request. Drives the
    /// denied-state UI (D-20). `nil` until first queried.
    @State private var authStatus: UNAuthorizationStatus = .notDetermined

    /// The active (non-archived) saved medications, loaded from the Repository (D-06).
    @State private var savedMeds: [SchemaV1.Medication] = []
    /// The add-medication sheet + its bounded free-text drafts.
    @State private var showingAddMed = false
    @State private var newMedName = ""
    @State private var newMedNote = ""
    /// The medication pending soft-archive confirmation (drives the destructive `.confirmationDialog`).
    @State private var medPendingRemoval: SchemaV1.Medication?

    var body: some View {
        // No own NavigationStack — pushed onto the shell's stack (HomeView's NavigationStack).
        Form {
                // (b) Terminology customization (D-07).
                Section("How Endo-App refers to things") {
                    Picker("Bleeding", selection: bleedingTermBinding) {
                        ForEach(TerminologyPreference.BleedingTerm.allCases, id: \.self) { term in
                            Text(term.noun.capitalized).tag(term)
                        }
                    }
                    TextField("Pronouns (optional)", text: $pronouns)
                        .textInputAutocapitalization(.never)
                    TextField("Display name (optional)", text: $displayName)
                }

                remindersSection

                manageMedsSection

                // Suppressed / no-cycle mode (CYCL-03, D-09). When on, the home cycle summary shows
                // NOTHING cycle-related — the app stays fully useful for symptom + bleeding logging.
                Section {
                    Toggle(isOn: suppressedBinding) {
                        Text("Cycle tracking is off")
                    }
                    .accessibilityIdentifier("settings.suppressedToggle")
                } footer: {
                    Text(
                        "Turn this on if you don't have a cycle right now — for example on continuous "
                        + "treatment. Endo-App stops showing any cycle estimate, and you can still log "
                        + "symptoms and bleeding as usual."
                    )
                }

                // (c) Privacy reachable from Settings (ONBD-04).
                Section {
                    NavigationLink {
                        PrivacyPolicyView()
                    } label: {
                        Label("Privacy", systemImage: "lock.shield")
                    }
                }

                // (a) Withdraw consent (D-04).
                Section {
                    Button(role: .destructive) {
                        showingWithdrawConfirmation = true
                    } label: {
                        Text("Withdraw consent")
                    }
                    .accessibilityIdentifier("settings.withdrawButton")
                } footer: {
                    Text(
                        "Withdrawing your consent returns the app to the welcome screen and stops "
                        + "any further processing of your health data. Anything you have already "
                        + "logged stays on your device until you choose to delete it; you can give "
                        + "consent again at any time."
                    )
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                displayName = terminologyStore.preference.displayName ?? ""
                pronouns = terminologyStore.preference.pronouns ?? ""
                suppressed = (try? SwiftDataRepository(context: modelContext).isSuppressed()) ?? false
                reloadMeds()
                // Reading the CURRENT status is a passive query (no prompt) — only `requestAuthorization`
                // shows the system alert, and that is gated behind the enable handler (D-20).
                Task { authStatus = await notificationScheduler.authorizationStatus() }
            }
            .onChange(of: displayName) { _, newValue in commitFreeText(displayName: newValue, pronouns: pronouns) }
            .onChange(of: pronouns) { _, newValue in commitFreeText(displayName: displayName, pronouns: newValue) }
            .confirmationDialog(
                "Withdraw consent?",
                isPresented: $showingWithdrawConfirmation,
                titleVisibility: .visible
            ) {
                Button("Withdraw consent", role: .destructive) {
                    // Re-gates the app (D-04): AppGate re-routes to onboarding on the state change.
                    consent.withdraw()
                }
                Button("Keep consent", role: .cancel) {}
            } message: {
                Text(
                    "You'll return to the welcome screen and the app will stop processing your "
                    + "health data. Your existing entries remain on your device until you delete them."
                )
            }
            // Add-medication entry (D-06). Free-text bounded at the Repository seam on commit.
            .sheet(isPresented: $showingAddMed) { addMedSheet }
            // Destructive remove-med confirmation (D-06). Soft-archive only — never a hard delete.
            .confirmationDialog(
                "Remove this medication?",
                isPresented: Binding(
                    get: { medPendingRemoval != nil },
                    set: { if !$0 { medPendingRemoval = nil } }
                ),
                titleVisibility: .visible,
                presenting: medPendingRemoval
            ) { med in
                Button("Remove", role: .destructive) { removeMed(med) }
                Button("Cancel", role: .cancel) { medPendingRemoval = nil }
            } message: { _ in
                Text(
                    "This stops it from appearing in your daily check-in. "
                    + "Days you already logged it as taken are not changed."
                )
            }
    }

    // MARK: - Reminders section (CHCK-02, D-17..21)

    @ViewBuilder
    private var remindersSection: some View {
        Section {
            Toggle(isOn: reminderEnabledBinding) {
                Text("Daily reminders")
            }
            .accessibilityIdentifier("settings.reminders.masterToggle")

            // Only when enabled AND permitted do we expose the time/quiet-hours editors.
            if reminders.enabled && authStatus != .denied {
                ForEach(Array(reminders.times.enumerated()), id: \.offset) { index, _ in
                    DatePicker(
                        "Reminder time",
                        selection: reminderTimeBinding(index),
                        displayedComponents: .hourAndMinute
                    )
                    .accessibilityIdentifier("settings.reminders.timeRow.\(index)")
                }
                .onDelete { offsets in
                    reminders.times.remove(atOffsets: offsets)
                    rearmReminders()
                }

                Button {
                    addReminderTime()
                } label: {
                    Label("Add a time", systemImage: "plus.circle")
                }
                .accessibilityIdentifier("settings.reminders.addTime")

                DatePicker(
                    "Quiet hours start",
                    selection: quietHoursStartBinding,
                    displayedComponents: .hourAndMinute
                )
                .accessibilityIdentifier("settings.reminders.quietHoursStart")

                DatePicker(
                    "Quiet hours end",
                    selection: quietHoursEndBinding,
                    displayedComponents: .hourAndMinute
                )
                .accessibilityIdentifier("settings.reminders.quietHoursEnd")
            }

            // Denied-state (D-20): non-nagging line + a path to Settings.app. The app stays usable.
            if authStatus == .denied {
                Text("Notifications are turned off for Endo-App. You can turn them on anytime in Settings.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("settings.reminders.deniedState")
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .accessibilityIdentifier("settings.reminders.openSettings")
            }
        } header: {
            Text("Reminders")
        } footer: {
            Text(
                "Reminders are off. If you'd like a gentle nudge to check in, you can turn them on "
                + "below — they always respect your quiet hours."
            )
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Manage-meds section (D-06 / LIFE-03)

    @ViewBuilder
    private var manageMedsSection: some View {
        Section {
            if savedMeds.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("No medications saved yet.")
                        .font(.headline)
                    Text(
                        "Add the medications you want to keep track of in Settings, and they'll "
                        + "appear here to tick off each day."
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                ForEach(savedMeds, id: \.id) { med in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(med.name ?? "Medication")
                            if let note = med.note, !note.isEmpty {
                                Text(note)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button {
                            medPendingRemoval = med
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                        .tint(.red)
                        .accessibilityLabel("Remove")
                        .accessibilityIdentifier("settings.meds.remove.\(med.id.uuidString)")
                    }
                    .accessibilityIdentifier("settings.meds.row.\(med.id.uuidString)")
                }
            }

            Button {
                newMedName = ""
                newMedNote = ""
                showingAddMed = true
            } label: {
                Label("Add a medication", systemImage: "plus.circle")
            }
            .accessibilityIdentifier("settings.meds.add")
        } header: {
            Text("Manage meds")
        }
    }

    @ViewBuilder
    private var addMedSheet: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $newMedName)
                TextField("Type or note (optional)", text: $newMedNote, axis: .vertical)
            }
            .navigationTitle("Add a medication")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showingAddMed = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { commitNewMed() }
                        .disabled(newMedName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    // MARK: - Bindings / persistence

    /// Writes the suppressed / no-cycle mode through the Repository (CYCL-03). A new timestamped
    /// `TreatmentState` is appended; `isSuppressed()` reads the latest.
    private var suppressedBinding: Binding<Bool> {
        Binding(
            get: { suppressed },
            set: { newValue in
                suppressed = newValue
                try? SwiftDataRepository(context: modelContext).setSuppressed(newValue)
            }
        )
    }

    private var bleedingTermBinding: Binding<TerminologyPreference.BleedingTerm> {
        Binding(
            get: { terminologyStore.preference.bleedingTerm },
            set: { terminologyStore.preference.bleedingTerm = $0 }
        )
    }

    /// Bounds free-text length (threat T-01-05) and writes back to the store. Empty strings are
    /// stored as `nil` so "unset" is distinguishable from "blank".
    private func commitFreeText(displayName: String, pronouns: String) {
        var pref = terminologyStore.preference
        let boundedName = String(displayName.prefix(TerminologyStore.maxDisplayNameLength))
        let boundedPronouns = String(pronouns.prefix(TerminologyStore.maxDisplayNameLength))
        pref.displayName = boundedName.isEmpty ? nil : boundedName
        pref.pronouns = boundedPronouns.isEmpty ? nil : boundedPronouns
        terminologyStore.preference = pref
    }

    // MARK: - Reminders bindings + handlers (CHCK-02)

    /// The master-toggle binding. Flipping ON runs the just-in-time enable flow (D-20); flipping OFF
    /// cancels every pending reminder.
    private var reminderEnabledBinding: Binding<Bool> {
        Binding(
            get: { reminders.enabled },
            set: { newValue in
                if newValue {
                    enableReminders()
                } else {
                    reminders.enabled = false
                    Task { await notificationScheduler.cancelAll() }
                }
            }
        )
    }

    /// The just-in-time enable flow (D-20, mirrors RESEARCH `enableReminders()`): request permission
    /// ONLY here (never at launch). If granted, seed a single evening default time when none exist and
    /// re-arm; if denied, surface the denied state. `requestAuthorization` lives exclusively in this
    /// handler so permission stays just-in-time.
    private func enableReminders() {
        Task {
            let granted = await notificationScheduler.requestAuthorization()
            authStatus = await notificationScheduler.authorizationStatus()
            if granted {
                reminders.enabled = true
                if reminders.times.isEmpty {
                    reminders.times = [ReminderSettings.defaultEveningTime]
                }
                await notificationScheduler.reschedule(
                    times: reminders.times, quietHours: reminders.quietHours)
            } else {
                // Denied — keep the config OFF and let the denied-state UI guide the user to Settings.app.
                reminders.enabled = false
            }
        }
    }

    /// Re-arm the scheduler idempotently after any times / quiet-hours edit (D-19a). No-op when off.
    private func rearmReminders() {
        guard reminders.enabled else { return }
        Task {
            await notificationScheduler.reschedule(
                times: reminders.times, quietHours: reminders.quietHours)
        }
    }

    /// Append a new reminder time (defaults to the evening suggestion) and re-arm.
    private func addReminderTime() {
        reminders.times.append(ReminderSettings.defaultEveningTime)
        rearmReminders()
    }

    /// A binding editing the reminder time at `index` as a `Date`, persisting hour/minute back and
    /// re-arming on change.
    private func reminderTimeBinding(_ index: Int) -> Binding<Date> {
        Binding(
            get: {
                guard reminders.times.indices.contains(index) else { return Date() }
                return Calendar.current.date(
                    from: reminders.times[index]) ?? Date()
            },
            set: { newDate in
                guard reminders.times.indices.contains(index) else { return }
                reminders.times[index] = Calendar.current.dateComponents(
                    [.hour, .minute], from: newDate)
                rearmReminders()
            }
        )
    }

    /// Quiet-hours start binding. Editing it creates/updates the window (paired with the current end,
    /// or the start itself if no end yet) and re-arms.
    private var quietHoursStartBinding: Binding<Date> {
        Binding(
            get: { Self.date(from: reminders.quietHours?.start) },
            set: { newDate in
                let start = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                let end = reminders.quietHours?.end ?? start
                reminders.quietHours = QuietHours(start: start, end: end)
                rearmReminders()
            }
        )
    }

    /// Quiet-hours end binding (symmetric with start).
    private var quietHoursEndBinding: Binding<Date> {
        Binding(
            get: { Self.date(from: reminders.quietHours?.end) },
            set: { newDate in
                let end = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                let start = reminders.quietHours?.start ?? end
                reminders.quietHours = QuietHours(start: start, end: end)
                rearmReminders()
            }
        )
    }

    /// Render an hour/minute `DateComponents` as today's `Date` for a `DatePicker` (falls back to now).
    private static func date(from components: DateComponents?) -> Date {
        guard let components else { return Date() }
        return Calendar.current.date(from: components) ?? Date()
    }

    // MARK: - Manage-meds handlers (D-06)

    /// Load the active (non-archived) saved meds from the Repository.
    private func reloadMeds() {
        savedMeds = (try? SwiftDataRepository(context: modelContext).savedMedications()) ?? []
    }

    /// Commit a new med. Free-text is bounded at the Repository seam (`maxNoteLength` prefix-clamp,
    /// threat T-03-10) — clamp here too so a pathological paste never reaches persistence unbounded.
    private func commitNewMed() {
        let name = String(newMedName.prefix(SwiftDataRepository.maxNoteLength))
        let note = String(newMedNote.prefix(SwiftDataRepository.maxNoteLength))
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else { return }
        let trimmedNote = note.trimmingCharacters(in: .whitespaces)
        try? SwiftDataRepository(context: modelContext).addMedication(
            name: trimmedName, note: trimmedNote.isEmpty ? nil : trimmedNote)
        showingAddMed = false
        reloadMeds()
    }

    /// Soft-archive a saved med (D-06) — never a hard delete. Prior "taken" history keyed by its id
    /// stays valid.
    private func removeMed(_ med: SchemaV1.Medication) {
        try? SwiftDataRepository(context: modelContext).archiveMedication(med.id)
        medPendingRemoval = nil
        reloadMeds()
    }
}
