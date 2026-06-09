// Requirement: DATA-05 (Phase 9, Plan 09-01 Task 2) — the pure HK-enum → app-value mapping tables
// (D-14/D-15/D-16). A stateless namespace of total, defensive functions: value-in/value-out, no store,
// no `HKHealthStore`. This is the ONLY genuinely bespoke logic in the import path (everything else —
// deltas, deletions, the resumable cursor — is HealthKit's own); the merge/recompute lands in 09-02.
//
// DEFENSIVE DECODE (threat T-09-02 / ASVS V5): every function is TOTAL and never crashes on unknown /
// garbage input, mirroring the app enums' own `init(raw:)` posture — unknown workout → `.other`,
// unknown/`none` flow → `nil` (NEVER fabricates a flow), unknown sleep stage → excluded. Absence is
// never coerced into a fabricated value (the SC3 honesty posture starts here, at the mapping edge).
//
// `import HealthKit` is required because the workout/sleep/flow inputs are `HK*` enums/raw values; the
// OUTPUTS are pure app value types, so nothing `HK*` escapes a call site.
import Foundation
import HealthKit

/// One asleep/in-bed/awake sleep segment as a plain `Sendable` value — lets `nightlyAsleepHours` be
/// unit-tested without an `HKCategorySample`. `value` is the raw `HKCategoryValueSleepAnalysis` value;
/// `duration` is the segment length in seconds.
struct SleepSegmentValue: Sendable, Equatable {
    let value: Int
    let duration: TimeInterval
}

/// Pure HK-enum → app-value mappings (D-14/D-15/D-16). Stateless namespace (no instances).
enum HealthSampleMapping {

    // MARK: - Workouts (D-15)

    /// Maps a `HKWorkoutActivityType` to the app's `ActivityType`. Common families are mapped
    /// explicitly; EVERYTHING else falls through to `.other` (D-15 explicit fallback, T-09-02).
    static func activityType(from hk: HKWorkoutActivityType) -> ActivityType {
        switch hk {
        case .walking, .hiking:
            return .walking
        case .running, .cycling, .swimming,
             .highIntensityIntervalTraining, .rowing:
            return .cardio
        case .traditionalStrengthTraining,
             .functionalStrengthTraining, .coreTraining:
            return .strength
        case .yoga, .flexibility, .pilates, .mindAndBody:
            return .yogaStretching
        default:
            // DEFENSIVE (T-09-02): any unmapped activity type → .other. Never crashes.
            return .other
        }
    }

    // MARK: - Sleep (D-15)

    /// The set of raw `HKCategoryValueSleepAnalysis` values that count as "asleep" (iOS 16+ groups the
    /// asleep stages under `allAsleepValues` — asleepUnspecified/asleepCore/asleepDeep/asleepREM).
    /// `inBed` and `awake` are deliberately EXCLUDED — they are not asleep.
    private static let asleepRawValues: Set<Int> =
        Set(HKCategoryValueSleepAnalysis.allAsleepValues.map { Int($0.rawValue) })

    /// Sums ONLY the asleep segments and returns total hours (seconds / 3600). `inBed`/`awake`/unknown
    /// segments are excluded; an empty input is 0. Total + defensive (T-09-02) — an unknown stage value
    /// is simply not counted, never a crash.
    static func nightlyAsleepHours(_ segments: [SleepSegmentValue]) -> Double {
        segments
            .filter { asleepRawValues.contains($0.value) }
            .reduce(0) { $0 + $1.duration } / 3600.0
    }

    /// Documented sleep-quality thresholds (Claude's discretion, D-15). Named so the tests pin exact
    /// boundaries. Bands: `[0, poorHoursCeiling)` → .poor; `[poorHoursCeiling, okayHoursCeiling)` →
    /// .okay; `[okayHoursCeiling, ∞)` → .good. A boundary value lands on the UPPER band (`>=` ceiling).
    static let poorHoursCeiling: Double = 5.0
    static let okayHoursCeiling: Double = 7.0

    /// Maps nightly asleep hours to the coarse app `SleepQuality` using the documented thresholds above.
    static func sleepQuality(forAsleepHours hours: Double) -> SleepQuality {
        if hours < poorHoursCeiling {
            return .poor
        } else if hours < okayHoursCeiling {
            return .okay
        } else {
            return .good
        }
    }

    // MARK: - Cycle / menstrual flow (D-14)

    /// Maps a raw `HKCategoryValueMenstrualFlow` value to a `FlowIntensity`. `.unspecified` (logged flow
    /// of unknown intensity) maps to the lightest bucket `.spotting`; `.none` (explicitly no flow) and
    /// any unrecognized/garbage value yield `nil` — NEVER a fabricated flow (D-14, T-09-02). Mirrors
    /// `FlowIntensity(raw:)`'s "unknown → nil" posture.
    static func flowIntensity(fromMenstrualFlowValue value: Int) -> FlowIntensity? {
        guard let flow = HKCategoryValueMenstrualFlow(rawValue: value) else { return nil }
        switch flow {
        case .unspecified: return .spotting
        case .light:       return .light
        case .medium:      return .medium
        case .heavy:       return .heavy
        case .none:        return nil   // explicit "no flow" — contribute nothing (never fabricate)
        @unknown default:  return nil   // DEFENSIVE: a future HK case → nil, never a fabricated flow
        }
    }

    // MARK: - Nutrition (D-16)

    /// Presence-only "ate that day" signal (D-16): `true` iff at least one dietary-energy sample existed
    /// that day. NOT a fabricated `FoodTag`, NOT a micronutrient claim — pure presence. No samples →
    /// `false` (absence, not a logged "didn't eat").
    static func ateThatDay(dietaryEnergySampleCount count: Int) -> Bool {
        count > 0
    }
}
