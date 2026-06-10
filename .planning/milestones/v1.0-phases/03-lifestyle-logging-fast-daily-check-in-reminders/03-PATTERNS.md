# Phase 3: Lifestyle Logging + Fast Daily Check-in & Reminders - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 22 (15 new / 7 modified)
**Analogs found:** 22 / 22 (every file mirrors a shipped Phase 1–2 pattern; only `NotificationScheduler` is a net-new subsystem, and even it mirrors the `Repository` protocol-seam)

> Platform: native iOS / SwiftUI, SwiftData (CloudKit-safe), MV + Repository. This phase is ~90% additive mirroring of shipped code. Below, every new/modified file names its closest analog file (absolute path) and the concrete excerpt the executor should copy. Excerpts are quoted verbatim from source — line numbers are the source-of-truth analog, not the target.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Persistence/Models/FactorReading.swift` | model (@Model) | CRUD / event-record | `Persistence/Models/SymptomReading.swift` | exact |
| `Persistence/Models/Medication.swift` | model (@Model) | CRUD / saved-definition | `Persistence/Models/SymptomReading.swift` | role-match |
| `Domain/FactorKind.swift` | domain enum | transform (raw↔typed) | `Domain/SymptomType.swift` | exact |
| `Domain/FoodTag.swift` | domain enum | transform | `Domain/SymptomType.swift` | exact |
| `Domain/ActivityType.swift` | domain enum | transform | `Domain/SymptomType.swift` | exact |
| `Domain/ActivityIntensity.swift` | domain enum | transform | `Domain/FlowIntensity.swift` | exact |
| `Domain/SleepQuality.swift` | domain enum | transform | `Domain/FlowIntensity.swift` | exact |
| `Domain/FactorDay.swift` | domain value snapshot | read / Sendable | `BleedingDay` in `Persistence/Repository.swift` (L21-26) | exact |
| `Persistence/SchemaV3.swift` | config (VersionedSchema) | schema | `Persistence/SchemaV2.swift` | exact |
| `Persistence/MigrationPlan.swift` (MODIFY) | config (migration) | schema | existing `v1toV2` stage (same file) | exact |
| `Persistence/ModelContainer+Config.swift` (MODIFY) | config | schema | same file, L38 (`Schema(versionedSchema:)`) | exact |
| `Persistence/Repository.swift` (MODIFY) | service (persistence seam) | CRUD + Sendable read | `upsertSymptom`/`removeSymptom`/`bleedingDays`/`setNote` (same file) | exact |
| `Reminders/NotificationScheduler.swift` | service (protocol + impl + mock) | event-driven | `Repository` protocol+impl seam in `Persistence/Repository.swift` | role-match (new subsystem) |
| `Reminders/ReminderSettings.swift` | store (UserDefaults) | config | `TerminologyStore`/consent-flag UserDefaults pattern | role-match |
| `Features/CheckIn/DailyCheckInView.swift` | view (MV, sheet) | request-response | `Features/DayLog/DayLogEditorView.swift` | exact |
| `Features/DayLog/DayLogEditorView.swift` (MODIFY) | view | request-response | self — `symptomSection`/`bleedingSection` (same file) | exact |
| `Features/Home/HomeView.swift` (MODIFY) | view | request-response | self — "Log today" Section (L38-45) | exact |
| `Features/Settings/SettingsView.swift` (MODIFY) | view | request-response | self — suppressed `Section`+`footer`+`confirmationDialog` | exact |
| `App/AppDependencies.swift` (MODIFY) | config (composition root) | wiring | self — `EnvironmentKey` injection pattern (L84-117) | exact |
| `App/EndoAppApp.swift` / `App/AppGate.swift` (MODIFY) | config (root + routing) | event-driven (deep-link) | self — `.environment()` wiring + `AppGate` switch | role-match |
| `Tests/EndoAppTests/FactorLoggingTests.swift` + `MedicationTests` + `FactorSnapshotTests` + `NotificationSchedulerTests` | test (unit) | — | `Tests/EndoAppTests/SymptomLoggingTests.swift` | exact |
| `Tests/EndoAppTests/SchemaMigrationTests.swift` (MODIFY) | test (unit) | — | self — V1→V2 case (same file) | exact |
| `Tests/EndoAppUITests/CheckInFlowUITests.swift` | test (E2E) | — | `Tests/EndoAppUITests/LoggingFlowUITests.swift` | exact |

---

## Pattern Assignments

### `Persistence/Models/FactorReading.swift` (model, CRUD/event-record)

**Analog:** `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Persistence/Models/SymptomReading.swift` (whole file, 44 lines)

Mirror the CloudKit-safe `@Model` template verbatim: nested under `extension SchemaV1`, app-gen `id: UUID` (NOT `.unique`), `occurredOn: Date = Date()` day-anchor, ALL stored props optional/defaulted, no relationships, no cycle fields. Keep the doc-comment block explaining the A1 nesting + honesty rule (absence ≠ 0). Add a column-mapping table doc-comment (RESEARCH Pattern 1) so the kind→column convention is unambiguous downstream.

Excerpt to copy (SymptomReading.swift L19-44) — substitute the payload columns:
```swift
extension SchemaV1 {
    @Model
    final class SymptomReading {
        var id: UUID = UUID()                 // app-generated, NOT .unique (CloudKit-safe)
        var occurredOn: Date = Date()         // calendar-day anchor, defaulted
        var typeRaw: String?
        var severity: Int?
        var note: String?

        init(id: UUID = UUID(), occurredOn: Date = .now,
             typeRaw: String? = nil, severity: Int? = nil, note: String? = nil) {
            self.id = id; self.occurredOn = occurredOn
            self.typeRaw = typeRaw; self.severity = severity; self.note = note
        }
    }
}
```
For `FactorReading`, the payload columns per RESEARCH Pattern 1 / CONTEXT D-01 are: `kindRaw: String?`, `valueRaw: String?`, `amount: Double?`, `level: Int?`, `note: String?`. Kind→column mapping locked in the model doc-comment (food→valueRaw=FoodTag.rawValue, many-per-day; activity→valueRaw=type, amount=minutes, level=intensity 0/1/2; sleep→valueRaw=quality, amount=hours; medication→valueRaw=med-id|"prn"; stress→level 0...10; mood→level 1...5).

---

### `Persistence/Models/Medication.swift` (model, saved-definition)

**Analog:** `SymptomReading.swift` (same CloudKit-safe conventions). This is a *definition* entity, NOT an append-only event — the per-day "taken" log is a `.medication` `FactorReading` whose `valueRaw` is the med's `id.uuidString`.

Same nesting + optional/defaulted rules. RESEARCH Pattern 2 shape: `id: UUID`, `name: String?`, `note: String?`, `createdAt: Date = Date()`, `isArchived: Bool? = nil`. **Soft-archive (`isArchived`), never hard-delete** (RESEARCH Assumption A3 + UI-SPEC copy: "Days you already logged it as taken are not changed"). Bound `name`/`note` at the Repository seam with the `maxNoteLength` prefix-clamp (see Shared Patterns → Input bounding).

---

### `Domain/FactorKind.swift`, `FoodTag.swift`, `ActivityType.swift` (domain enums, default→`.other`)

**Analog:** `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Domain/SymptomType.swift` (whole file)

Foundation-only (NO `import SwiftData`/`SwiftUI`), `String, CaseIterable, Sendable`, a `plainLabel` audited by `CopyAuditTests`, and a defensive `init(raw:)` that maps unknown→`.other`. Excerpt (SymptomType.swift L17-57):
```swift
enum SymptomType: String, CaseIterable, Sendable {
    case pelvicPain
    // …
    case other

    var plainLabel: String {
        switch self {
        case .pelvicPain: return "Pelvic / abdominal pain"
        // …
        case .other: return "Something else"
        }
    }

    init(raw: String?) {                     // unknown/nil → .other, never crashes (T-02-03)
        if let raw, let value = SymptomType(rawValue: raw) { self = value }
        else { self = .other }
    }
}
```
Taxonomy (RESEARCH §Food-Trigger Taxonomy): `FoodTag` = dairy, glutenWheat, caffeine, alcohol, highSugar, highFODMAP, redProcessedMeat, largeMeal, other. `ActivityType` = walking, cardio, strength, yogaStretching, other. `FactorKind` = food, activity, sleep, medication, stress, mood. **Copy posture:** user-facing labels must avoid the verb "trigger" (`ClaimsPolicy.forbiddenVerbs`) — use "Food & drink" / "Tags".

---

### `Domain/ActivityIntensity.swift`, `SleepQuality.swift` (domain enums, default→`nil`)

**Analog:** `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Domain/FlowIntensity.swift` (whole file)

Same Foundation-only enum, but defensive decode returns **`nil`** (failable `init?(raw:)`) for these (an unset section is "not logged"). Excerpt (FlowIntensity.swift L12-33):
```swift
enum FlowIntensity: String, CaseIterable, Sendable {
    case spotting, light, medium, heavy
    var plainLabel: String { /* … */ }
    init?(raw: String?) {
        guard let raw, let value = FlowIntensity(rawValue: raw) else { return nil }
        self = value
    }
}
```
`ActivityIntensity` = low, medium, high (text-labelled, never color-only per UI-SPEC). `SleepQuality` = poor, okay, good.

---

### `Domain/FactorDay.swift` (Sendable value snapshot) — THE most important forward-looking deliverable (D-11)

**Analog:** `BleedingDay` struct in `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Persistence/Repository.swift` (L21-26)

A `Sendable, Equatable` value type carrying NO `@Model` reference, so Phases 5–8 consume factors without a `ModelContext`. Analog:
```swift
struct BleedingDay: Sendable, Equatable {
    var day: Date          // start-of-day
    var hadFlow: Bool
}
```
`FactorDay` shape (RESEARCH Pattern 4): `day: Date` (start-of-day), `foodTags: [FoodTag]`, `foodNote: String?`, `activityType: ActivityType?`, `activityMinutes: Double?`, `activityIntensity: ActivityIntensity?`, `sleepHours: Double?`, `sleepQuality: SleepQuality?`, `medicationsTaken: [UUID]`, `tookPRN: Bool`, `stress: Int?` (nil = NOT logged), `mood: Int?` (nil = NOT logged). **Honesty contract:** unlogged stress/mood stay `nil`, never coerced to 0 — unit-tested in `FactorSnapshotTests`. (Place under `Domain/` per RESEARCH structure, or alongside `BleedingDay` in Repository.swift — planner's call; the value type is what matters.)

---

### `Persistence/SchemaV3.swift` (config, additive VersionedSchema)

**Analog:** `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Persistence/SchemaV2.swift` (whole file)

Copy verbatim, bump version, append the two NEW `SchemaV1.<Name>` classes to `models`. Analog (SchemaV2.swift L19-32):
```swift
enum SchemaV2: VersionedSchema {
    static let versionIdentifier = Schema.Version(2, 0, 0)
    static var models: [any PersistentModel.Type] {
        [SchemaV1.DayEntry.self, SchemaV1.BleedingEvent.self, SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self, SchemaV1.PainPoint.self]
    }
}
```
SchemaV3: `Schema.Version(3, 0, 0)`, same list + `SchemaV1.FactorReading.self`, `SchemaV1.Medication.self`. Keep the A1 entity-identity doc-comment block. **Anti-pattern:** never define the new models at top-level (must stay under `extension SchemaV1`).

---

### `Persistence/MigrationPlan.swift` (MODIFY — add v2toV3 lightweight stage)

**Analog:** the existing `v1toV2` stage in the SAME file (L24-27).

Existing pattern:
```swift
static var schemas: [any VersionedSchema.Type] { [SchemaV1.self, SchemaV2.self] }
static var stages: [MigrationStage] { [v1toV2] }
static let v1toV2 = MigrationStage.lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)
```
Add `SchemaV3.self` to `schemas`, `v2toV3` to `stages`, and:
```swift
static let v2toV3 = MigrationStage.lightweight(fromVersion: SchemaV2.self, toVersion: SchemaV3.self)
```
**Anti-pattern (documented forever-rule in this file L4-9):** NEVER `.custom` — its `willMigrate`/`didMigrate` hooks don't fire under CloudKit (Phase 4). Additive + lightweight only.

---

### `Persistence/ModelContainer+Config.swift` (MODIFY — bump to SchemaV3)

**Analog:** same file, L38. Change one line:
```swift
let schema = Schema(versionedSchema: SchemaV2.self)   // → SchemaV3.self
```
Leave the `SyncMode`/`cloudKitDatabase`/`url:` test-seam machinery untouched (still `.none` local-only this phase — Phase 4 flips CloudKit).

---

### `Persistence/Repository.swift` (MODIFY — factor + med CRUD + FactorDay reads)

**Analog:** `upsertSymptom`/`removeSymptom`/`setBleeding`/`setNote`/`bleedingDays`/`symptomReadings(on:)` in the SAME file.

**CRUD pattern** to mirror (Repository.swift L104-117) — `assertConsented()` → clamp → `Calendar`-bucketed upsert → `save()`:
```swift
func upsertSymptom(type: SymptomType, severity: Int, on day: Date) throws {
    assertConsented()
    let clamped = Self.clampSeverity(severity)                 // min(10, max(0, value))
    let readings = try symptomReadings(on: day)
    if let existing = readings.first(where: { $0.typeRaw == type.rawValue }) {
        existing.severity = clamped
    } else {
        context.insert(SchemaV1.SymptomReading(
            occurredOn: Calendar.current.startOfDay(for: day),
            typeRaw: type.rawValue, severity: clamped))
    }
    try context.save()
}
```
**Day-bucket helper pattern** (L237-241) — `Calendar.isDate(_:inSameDayAs:)`, never epoch math:
```swift
func symptomReadings(on day: Date) throws -> [SchemaV1.SymptomReading] {
    let cal = Calendar.current
    return try context.fetch(FetchDescriptor<SchemaV1.SymptomReading>())
        .filter { cal.isDate($0.occurredOn, inSameDayAs: day) }
}
```
**Sendable snapshot pattern** (`bleedingDays()`, L201-213) — fetch @Model rows, bucket by start-of-day, decode raws defensively, return SwiftData-free value types ascending:
```swift
func bleedingDays() throws -> [BleedingDay] {
    assertConsented()
    let cal = Calendar.current
    let events = try context.fetch(FetchDescriptor<SchemaV1.BleedingEvent>())
    var byDay: [Date: Bool] = [:]
    for event in events where FlowIntensity(raw: event.flowRaw) != nil {
        byDay[cal.startOfDay(for: event.occurredAt)] = true
    }
    return byDay.keys.sorted().map { BleedingDay(day: $0, hadFlow: byDay[$0] ?? false) }
}
```
New additive methods (RESEARCH §phase_requirements): `setFood(tags:freeText:on:)`, `setActivity(type:minutes:intensity:on:)`, `setSleep(hours:quality:on:)`, `setMedicationTaken(medID:taken:on:)` / `setPRN(_:on:)`, `setStress(_:on:)` (clamp 0...10, nil clears), `setMood(_:on:)` (clamp 1...5, nil clears), `checkInMarker(on:)` (ensures a `DayEntry` anchor without inventing data — D-15), saved-med CRUD (`addMedication`/`archiveMedication`/`savedMedications()`), and `factorDays() -> [FactorDay]`. Every method opens with `assertConsented()` and ends with `try context.save()`. **`nil` clears** mirrors `setBleeding`'s `guard let intensity else { delete… }` (L150-158) so stress/mood honor absence ≠ 0.

---

### `Reminders/NotificationScheduler.swift` (service, protocol + impl + mock) — new subsystem

**Analog:** the `Repository` protocol + `SwiftDataRepository` impl seam in `Persistence/Repository.swift` (L34-83). This is the only net-new subsystem; it mirrors the protocol-seam discipline so the fatigue logic is unit-testable (XCUITest can't assert system notifications).

Protocol-seam shape (RESEARCH Pattern 5):
```swift
@MainActor
protocol NotificationScheduler {
    func requestAuthorization() async -> Bool                  // JIT, only on first enable (D-20)
    func authorizationStatus() async -> UNAuthorizationStatus  // drives denied-state UI (D-20)
    func reschedule(times: [DateComponents], quietHours: ClosedRange<DateComponents>?) async
    func cancelToday() async                                   // auto-suppress once logged (D-19b)
    func cancelAll() async
}
```
Concrete `SystemNotificationScheduler` wraps `UNUserNotificationCenter.current()`, keys each daily reminder by a STABLE identifier (`"endo.reminder.\(i)"`), uses `UNCalendarNotificationTrigger(dateMatching:repeats: true)`. **Extract PURE decision functions** `isInQuietHours(_:_:)` and `shouldFireToday(lastLoggedDay:now:)` and unit-test THOSE (`NotificationSchedulerTests`); the UN calls are the thin untested shell. Provide a `MockNotificationScheduler` test-double struct conforming to the protocol (no install). Reminder copy via `Terminology`/`ClaimsPolicy` — gentle, non-coercive (UI-SPEC body: "Whenever you have a moment, here's your daily check-in.").

---

### `Reminders/ReminderSettings.swift` (store, UserDefaults)

**Analog:** the `TerminologyStore`/consent-flag UserDefaults pattern (referenced in `AppDependencies.swift` L19-22, `SettingsView.swift` free-text commit). Reminder times/quiet-hours/enabled-flag are **device prefs, NOT health data** — keep them out of the synced store (RESEARCH Assumption A2). New keys e.g. `reminders.enabled`, `reminders.times`, `reminders.quietStart/End`. Default `enabled = false` (D-18 opt-in default-off).

---

### `Features/CheckIn/DailyCheckInView.swift` (view, MV sheet) — CHCK-01

**Analog:** `/Users/hannessuhr/Projects/endo-app/Sources/EndoApp/Features/DayLog/DayLogEditorView.swift` (whole file)

Copy the MV structure verbatim: `@Environment(\.modelContext)`, a computed `SwiftDataRepository(context: modelContext)`, local `@State` draft, `loadExistingState()` on appear, commit-on-confirm. Use `Form` with skippable `Section`s. Confirm button id `checkin.confirmButton`, label **"Done for today"**. "Same as yesterday" (id `checkin.sameAsYesterday`) reads yesterday's `FactorDay` and prefills **STICKY CONTEXT ONLY** (meds/sleep/activity) — NEVER symptoms/bleeding (D-14). Empty confirm calls `repo.checkInMarker(on:)` (D-15).

Repository-access + commit pattern (DayLogEditorView.swift L42-44, L221-245):
```swift
private var repository: SwiftDataRepository { SwiftDataRepository(context: modelContext) }
// …
private func save() {
    let repo = repository
    for type in SymptomType.allCases {
        if let severity = severities[type] { try? repo.upsertSymptom(type: type, severity: severity, on: day) }
        else { try? repo.removeSymptom(type: type, on: day) }
    }
    try? repo.setBleeding(intensity: flow, clots: flow == nil ? false : clots, on: day)
    try? repo.setNote(note, on: day)
    dismiss()
}
```

---

### `Features/DayLog/DayLogEditorView.swift` (MODIFY — add factor Sections) — D-09

**Analog:** the SAME file's `symptomSection` (L79-106) and `bleedingSection` (L130-153).

Multi-select tappable-row pattern (food tags) — `circle`→`checkmark.circle.fill`, accent tint, `.isSelected` trait, a11y id `daylog.factor.*` (L79-104):
```swift
ForEach(SymptomType.allCases, id: \.self) { type in
    let isSelected = severities[type] != nil
    Button { toggle(type) } label: {
        HStack {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            Text(type.plainLabel).foregroundStyle(.primary)
            Spacer()
        }
    }
    .accessibilityIdentifier("daylog.symptom.\(type.rawValue)")
    .accessibilityAddTraits(isSelected ? .isSelected : [])
    if isSelected { SeverityControl(severity: severityBinding(for: type)).padding(.leading, 28) }
}
```
Single-select trailing-`checkmark` pattern (activity type, sleep quality) — mirror `bleedingSection` (L130-148). **Stress reveal** (UI-SPEC + RESEARCH §Code Examples): a "Log stress" row that reveals `SeverityControl` only after opt-in; `stress: Int?` stays `nil` until tapped (absence ≠ 0). Reuse `SeverityControl` verbatim for stress (D-07). Activity intensity / mood as `Picker(.segmented)` with TEXT labels (never color-only). Add factor draft `@State` and extend `loadExistingState()`/`save()` for the new sections.

---

### `Features/Home/HomeView.swift` (MODIFY — add "Daily check-in" CTA) — D-16

**Analog:** the SAME file's "Log today" Section (L38-45) and `.sheet(item:)` (L83-87).

CTA pattern:
```swift
Section {
    Button { editingDay = Calendar.current.startOfDay(for: .now) } label: {
        Label("Log today", systemImage: "square.and.pencil")
    }
    .accessibilityIdentifier("home.logTodayButton")
}
```
Add a prominent accent-tinted "Daily check-in" Section/Button (SF Symbol `checkmark.circle`) above/beside "Log today" that presents `DailyCheckInView` in a `.sheet`. Mirror the `Date: Identifiable` `.sheet(item:)` presentation (L83-87, L134-136). The reminder deep-link sets the same presentation state.

---

### `Features/Settings/SettingsView.swift` (MODIFY — reminders + manage-meds Sections) — CHCK-02/D-06

**Analog:** the SAME file's suppressed `Section`+`footer` (L42-53), `confirmationDialog` (L89-104), and bounded-free-text commit (L130-137).

`Section`+`footer` + `Toggle`-binding pattern for the reminders master toggle (default OFF):
```swift
Section {
    Toggle(isOn: suppressedBinding) { Text("Cycle tracking is off") }
        .accessibilityIdentifier("settings.suppressedToggle")
} footer: { Text("Turn this on if …") }
```
Master toggle id `settings.reminders.masterToggle`; on enable call the JIT `scheduler.requestAuthorization()` (RESEARCH §Code Examples `enableReminders()`); render denied-state line + Settings.app button when not granted (D-20). Reminder-time `DatePicker(.hourAndMinute)` rows (`settings.reminders.addTime`/`.timeRow.<i>`), quiet-hours `DatePicker`s (`settings.reminders.quietHoursStart/End`). Manage-meds Section (`settings.meds.add`) for saved-med CRUD, bounding name/note via the `maxNoteLength`-prefix-clamp (L130-137 pattern) and a `.confirmationDialog` remove (copy from UI-SPEC: "Remove this medication?"). Reuse the `confirmationDialog` + `.destructive`/`.cancel` structure verbatim.

---

### `App/AppDependencies.swift` (MODIFY — inject scheduler, bump schema) + `EndoAppApp.swift` / `AppGate.swift` (deep-link routing)

**Analog:** the SAME `AppDependencies.swift` `EnvironmentKey` injection pattern (L84-117) and `EndoAppApp.swift` `.environment()` wiring (L18-21).

`SchemaV3` rides in automatically via `makeModelContainer` (already changed in `ModelContainer+Config.swift`). Add a `NotificationScheduler` property to `AppDependencies` and expose it via an `EnvironmentKey` exactly like `consentStore`/`terminologyStore` — note the **`MainActor.assumeIsolated` default** the scheduler (being `@MainActor`) requires:
```swift
private struct ConsentStoreKey: EnvironmentKey {
    static var defaultValue: any ConsentStore { MainActor.assumeIsolated { UserDefaultsConsentStore() } }
}
extension EnvironmentValues {
    var consentStore: any ConsentStore {
        get { self[ConsentStoreKey.self] } set { self[ConsentStoreKey.self] = newValue }
    }
}
```
Inject in `EndoAppApp.body` alongside the existing `.environment(\.consentStore, …)`. **Deep-link (D-16):** set the `UNUserNotificationCenterDelegate` early in the composition root; on tap read `userInfo["deepLink"] == "checkin"`, hop to `@MainActor` (mirror the `MainActor.assumeIsolated` discipline), and set an `@Observable` routing flag that `AppGate`'s `.consented` branch (the only branch that builds health UI — `AppGate.swift` L25-27) reads to present `DailyCheckInView`.

---

### Tests

**Unit analog:** `/Users/hannessuhr/Projects/endo-app/Tests/EndoAppTests/SymptomLoggingTests.swift` — Swift Testing `@Suite`/`@Test`/`#expect`, in-memory `makeModelContainer(inMemory: true)`, round-trip + honesty (`severity == nil`) assertions. Mirror for `FactorLoggingTests` (LIFE-01/02/03 round-trips), `MedicationTests` (saved-med CRUD + soft-archive history preservation), `FactorSnapshotTests` (`factorDays()`: unlogged stress ⇒ nil, logged 0 ⇒ 0). Key honesty assertion to copy (SymptomLoggingTests.swift L44-45):
```swift
#expect(fetched.first?.severity == nil,
        "Absent severity must persist as nil — never coerced to 0/healthy (D-02).")
```

**Migration analog:** `/Users/hannessuhr/Projects/endo-app/Tests/EndoAppTests/SchemaMigrationTests.swift` (MODIFY) — ADD a `v2Store_survivesLightweightMigrationToV3_withNoDataLoss` case mirroring the existing on-disk-write→reopen-at-newer-schema→assert-survival flow (L18-73): open at V2 on disk, write V2 rows, close, reopen via `makeModelContainer(url:)` (now V3), `#expect` all V2 rows survive AND `FactorReading`/`Medication` are queryable (empty).

**E2E analog:** `/Users/hannessuhr/Projects/endo-app/Tests/EndoAppUITests/LoggingFlowUITests.swift` — `CheckInFlowUITests` mirrors the `-uitest-reset` launch arg, consent grant, a11y-id lookups, `waitForExistence`, scroll-into-view loops, and the `saveScreenshot` helper (copy verbatim, L212-228). New a11y ids: `checkin.sheet`, `checkin.sameAsYesterday`, `checkin.section.<factor>`, `checkin.confirmButton`. Flow: consent → Home `checkin.*` CTA → log a factor → "Done for today" → day appears.

**Copy audit (MODIFY):** add new factor/check-in/reminder copy to `Tests/EndoAppTests/CopyAuditTests.swift` so labels pass `ClaimsPolicy.forbiddenVerbs` (notably NOT "trigger"/"causes"/"will") + `forbiddenGenderedTokens`.

---

## Shared Patterns

### Consent gate (V4 access control) — apply to EVERY new Repository method
**Source:** `Persistence/Repository.swift` `assertConsented()` (L266-269) + `AppGate.swift` (L25-27).
```swift
private func assertConsented() {
    assert(consentGuard(), "Repository accessed before consent — health-data processing is gated by AppGate (ONBD-01).")
}
```
Every new factor/med method opens with `assertConsented()`; the check-in/factor UI is reachable only inside `AppGate`'s `.consented` branch.

### Input bounding (V5) — apply to all free-text + numeric scales
**Source:** `Repository.swift` `maxNoteLength = 280` prefix-clamp (L98-102, L173-188) + `clampSeverity` (L234) + `SettingsView.swift` (L130-137).
```swift
static let maxNoteLength = 280
private static func clampSeverity(_ value: Int) -> Int { min(10, max(0, value)) }
// free-text: String(text.prefix(Self.maxNoteLength)); empty -> nil
```
Apply to factor free-text + med name/note (prefix-clamp), stress (clamp 0...10), mood (clamp 1...5).

### Calendar day-bucketing (never epoch math)
**Source:** `Repository.swift` (L201-213, L237-259) — `Calendar.current.startOfDay(for:)` for keys, `isDate(_:inSameDayAs:)` for filtering. Use for all factor reads/writes and `FactorDay` bucketing.

### Defensive enum decode (T-02-03) — never crash on corrupt raws
**Source:** `SymptomType.init(raw:)` (→`.other`) / `FlowIntensity.init?(raw:)` (→`nil`). All factor enums decode unknown raws to `.other`/`nil`, never force-unwrap.

### MV repository access (no ViewModel, D-12)
**Source:** `DayLogEditorView.swift` L42-44 — a computed `SwiftDataRepository(context: modelContext)` from the injected `@Environment(\.modelContext)`; draft `@State`, commit on action.

### Environment-key injection (composition root)
**Source:** `AppDependencies.swift` L84-117 — `private struct …Key: EnvironmentKey { static var defaultValue }` (with `MainActor.assumeIsolated` for `@MainActor` services) + `extension EnvironmentValues`. Used to inject `NotificationScheduler`.

### Copy choke-point (D-21)
**Source:** `Content/ClaimsPolicy.swift` (L27-50) `forbiddenVerbs` (incl. "trigger", "causes", "will") + `forbiddenGenderedTokens`; bleeding noun via `Terminology`. All Phase 3 copy routes through these and is audited by `CopyAuditTests`. Reminder copy additionally must be gentle/non-coercive — no streaks/guilt.

### Screenshot helper (E2E)
**Source:** `LoggingFlowUITests.swift` L212-228 — copy `saveScreenshot(_:named:)` verbatim into `CheckInFlowUITests` (writes `/tmp/e2e/<name>.png` + attaches to result bundle).

---

## No Analog Found

None. Every file maps to a shipped Phase 1–2 analog. The single net-new subsystem (`NotificationScheduler` + `UNUserNotificationCenter` API) has no prior in-codebase notification code, but its *structure* mirrors the `Repository` protocol-seam, and its API surface is verified against Apple docs in RESEARCH Pattern 5 (use that as the implementation reference, with the protocol-seam analog for the testable shape).

---

## Metadata

**Analog search scope:** `Sources/EndoApp/{Persistence,Persistence/Models,Domain,Features/{DayLog,Home,Settings},App,Content,Terminology}/`, `Tests/EndoAppTests/`, `Tests/EndoAppUITests/`
**Files scanned (read in full):** SymptomReading, SchemaV2, MigrationPlan, ModelContainer+Config, SymptomType, FlowIntensity, Repository, SeverityControl, DayLogEditorView, AppDependencies, HomeView, SettingsView, EndoAppApp, AppGate, ClaimsPolicy, SchemaMigrationTests, SymptomLoggingTests, LoggingFlowUITests
**Pattern extraction date:** 2026-06-08
