// Requirement: DATA-05 (Phase 9) — the four import domains and their per-domain HealthKit read-type
// sets (D-14/D-15/D-16). This is the ONLY app-side file in the Health boundary that needs the actual
// `HK*` read types; it imports HealthKit deliberately (the seam's HealthKit-facing edge).
//
// PRIVACY/SCOPE posture (D-14/D-15/D-16): the read set is the MINIMUM per domain —
//   - cycle      = menstrual flow ONLY (D-14; basal body temp / cervical mucus are explicitly deferred)
//   - sleep      = sleep analysis (D-15; nightly asleep hours + coarse quality)
//   - workouts   = the workout type (D-15; HKWorkout → ActivityType + minutes)
//   - nutrition  = dietary-energy PRESENCE as the "ate that day" proxy (D-16; NOT micronutrients)
//
// SWIFT 6 (RESEARCH Pitfall 2): `HealthDomain` is a plain `Sendable` value enum — it crosses the seam
// freely. The `HKObjectType`/`HKSampleType` values it vends are consumed INSIDE the isolation domain
// (the real reader in 09-02), never escaped to the rest of the app.
//
// SDK NOTE (RESEARCH A4): the typed initializers `HKCategoryType(.menstrualFlow)` /
// `HKQuantityType(.dietaryEnergyConsumed)` are the iOS 16+ non-failable forms — confirmed against the
// Xcode 26 / iOS 18 SDK at build time.
import Foundation
import HealthKit

/// The four additive HealthKit import domains (D-14/D-15/D-16). `String`-backed + `Sendable` so the
/// raw value can ride on `ImportedSample.domainRaw` (a flat, CloudKit-safe column) and the enum can
/// cross the `HealthDataReading` seam without touching any `HK*` type.
enum HealthDomain: String, CaseIterable, Sendable {
    case cycle
    case sleep
    case workouts
    case nutrition

    /// The MINIMUM `HKObjectType` read set for this domain (D-14/D-15/D-16). Used only to build the
    /// authorization request and the anchored query — never escapes the isolation domain.
    var readTypes: Set<HKObjectType> {
        switch self {
        case .cycle:
            // D-14: menstrual flow ONLY (defer basal body temp / cervical mucus).
            return [HKCategoryType(.menstrualFlow)]
        case .sleep:
            // D-15: sleep analysis → nightly asleep hours + a coarse SleepQuality.
            return [HKCategoryType(.sleepAnalysis)]
        case .workouts:
            // D-15: HKWorkout → ActivityType + minutes.
            return [HKObjectType.workoutType()]
        case .nutrition:
            // D-16: meal-event PRESENCE (dietary energy as the "ate that day" signal), NOT micronutrients.
            return [HKQuantityType(.dietaryEnergyConsumed)]
        }
    }

    /// The single `HKSampleType` this domain's anchored query reads. For every current domain the read
    /// set is a single sample type, so this is the one element of `readTypes` cast to `HKSampleType`.
    var sampleType: HKSampleType {
        switch self {
        case .cycle:     return HKCategoryType(.menstrualFlow)
        case .sleep:     return HKCategoryType(.sleepAnalysis)
        case .workouts:  return HKObjectType.workoutType()
        case .nutrition: return HKQuantityType(.dietaryEnergyConsumed)
        }
    }
}
