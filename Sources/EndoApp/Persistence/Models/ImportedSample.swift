// Requirement: DATA-05 / D-01 (Phase 9) — durable, CloudKit-safe persistence of ONE imported Apple
// Health sample, separate and additive so it NEVER mutates or overwrites a manual-logging row (D-01).
//
// PRIVACY BOUNDARY (security threat T-09-01): the columns encode ONLY category-level imported health
// values — the menstrual-flow raw, nightly sleep hours + a coarse quality raw, an activity type raw +
// minutes, and an "ate that day" presence bool. NEVER free-text / note prose, NEVER a micronutrient
// breakdown (same posture as CachedInsight). When CloudKit is active (Phase 4) only these category-level
// fields cross into the user's PRIVATE iCloud DB (GDPR Art.9-adjacent).
//
// CloudKit-safe @Model (mirrors CachedInsight / DismissedInsight EXACTLY, D-01 / Pitfall 4):
//   - Every stored property is optional OR defaulted (no non-optional, default-less fields).
//   - NO `@Attribute(.unique)` — identity is the app-generated `id` UUID (CloudKit-safe).
//   - No relationships — strictly additive, avoids CloudKit relationship traps.
//
// DEDUPE (Pitfall 5): `hkSampleUUID` stores HealthKit's stable per-sample `HKSample.uuid` (as a String)
// so the import service (09-02) can UPSERT-by-key — re-pulls and cross-device CloudKit mirroring of the
// same sample collapse harmlessly instead of inserting duplicate rows. Honoring HK deletions
// (HKDeletedObject) removes the matching row by this key.
//
// A1 nesting decision: nested under `extension SchemaV1 { @Model … }` exactly like every other model.
// SwiftData keys entity identity on the class/entity NAME ("ImportedSample"), not the enum namespace, so
// listing this same class additively under SchemaV6.models is a lightweight-compatible change.
// SchemaMigrationTests (V5→V6) is the gate that proves it.
import Foundation
import SwiftData

extension SchemaV1 {

    /// One CloudKit-safe imported Apple Health sample (D-01). Flat, all-optional/defaulted value columns
    /// (one set per domain payload) keyed for dedupe by `hkSampleUUID`. Populated at RUNTIME by the
    /// import service (09-02) — never by migration (the V5→V6 stage is purely additive).
    @Model
    final class ImportedSample {
        /// App-generated stable identifier. NOT `.unique` (CloudKit-safe).
        var id: UUID = UUID()
        /// HealthKit's stable per-sample `HKSample.uuid` as a String — the DEDUPE key (Pitfall 5).
        /// Optional per the CloudKit all-optional/defaulted rule; the upsert guard treats nil as "no key".
        var hkSampleUUID: String? = nil
        /// The start-of-day bucket (`Calendar.startOfDay`) this sample belongs to — the merge key.
        var day: Date = Date()
        /// `HealthDomain.rawValue` (cycle/sleep/workouts/nutrition). Optional (CloudKit rule).
        var domainRaw: String? = nil

        // Flat value columns covering every ImportedPayload case. Only the columns relevant to a row's
        // domain are populated; the rest stay nil. All optional (CloudKit-safe).

        /// Cycle (D-14): `FlowIntensity` raw string.
        var flowRaw: String? = nil
        /// Sleep (D-15): nightly asleep hours.
        var sleepHours: Double? = nil
        /// Sleep (D-15): coarse `SleepQuality` raw string.
        var sleepQualityRaw: String? = nil
        /// Workouts (D-15): mapped `ActivityType` raw string.
        var activityTypeRaw: String? = nil
        /// Workouts (D-15): workout minutes.
        var activityMinutes: Double? = nil
        /// Nutrition (D-16): presence-only "ate that day" signal. Optional (CloudKit rule).
        var ateThatDay: Bool? = nil

        /// When this row was imported. Defaulted (CloudKit rule).
        var importedAt: Date = Date()

        init(id: UUID = UUID(), hkSampleUUID: String? = nil, day: Date = Date(),
             domainRaw: String? = nil, flowRaw: String? = nil,
             sleepHours: Double? = nil, sleepQualityRaw: String? = nil,
             activityTypeRaw: String? = nil, activityMinutes: Double? = nil,
             ateThatDay: Bool? = nil, importedAt: Date = .now) {
            self.id = id
            self.hkSampleUUID = hkSampleUUID
            self.day = day
            self.domainRaw = domainRaw
            self.flowRaw = flowRaw
            self.sleepHours = sleepHours
            self.sleepQualityRaw = sleepQualityRaw
            self.activityTypeRaw = activityTypeRaw
            self.activityMinutes = activityMinutes
            self.ateThatDay = ateThatDay
            self.importedAt = importedAt
        }
    }
}
