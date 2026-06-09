// Requirement: DATA-05 (Phase 9, Plan 09-01 Task 2) — the pure HK-enum → app-value mapping tables
// (D-14/D-15/D-16). These are value-in/value-out tests: NO HKHealthStore, NO Simulator HealthKit, NO
// fixture seam. They pin the defensive-decode fallbacks (threat T-09-02) and the documented sleep
// thresholds (named constants on `HealthSampleMapping`), so an accidental table edit fails loudly.
//
// State: GREEN (Plan 09-01 Task 2, TDD). Written RED-first against the planned `HealthSampleMapping`
// namespace, then made green by the implementation.
import Testing
import Foundation
import HealthKit
@testable import EndoApp

@Suite("HK → app value mapping (D-14/D-15/D-16)")
struct HealthSampleMappingTests {

    // MARK: - Workout activity-type mapping (D-15) — explicit table + `.other` fallback

    @Test func walkingFamily_mapsToWalking() {
        #expect(HealthSampleMapping.activityType(from: .walking) == .walking)
        #expect(HealthSampleMapping.activityType(from: .hiking) == .walking)
    }

    @Test func cardioFamily_mapsToCardio() {
        #expect(HealthSampleMapping.activityType(from: .running) == .cardio)
        #expect(HealthSampleMapping.activityType(from: .cycling) == .cardio)
        #expect(HealthSampleMapping.activityType(from: .swimming) == .cardio)
        #expect(HealthSampleMapping.activityType(from: .highIntensityIntervalTraining) == .cardio)
        #expect(HealthSampleMapping.activityType(from: .rowing) == .cardio)
    }

    @Test func strengthFamily_mapsToStrength() {
        #expect(HealthSampleMapping.activityType(from: .traditionalStrengthTraining) == .strength)
        #expect(HealthSampleMapping.activityType(from: .functionalStrengthTraining) == .strength)
        #expect(HealthSampleMapping.activityType(from: .coreTraining) == .strength)
    }

    @Test func yogaFamily_mapsToYogaStretching() {
        #expect(HealthSampleMapping.activityType(from: .yoga) == .yogaStretching)
        #expect(HealthSampleMapping.activityType(from: .flexibility) == .yogaStretching)
        #expect(HealthSampleMapping.activityType(from: .pilates) == .yogaStretching)
        #expect(HealthSampleMapping.activityType(from: .mindAndBody) == .yogaStretching)
    }

    @Test func unknownWorkout_mapsToOther_neverCrashes() {
        // DEFENSIVE (T-09-02): any unmapped HKWorkoutActivityType → .other (explicit fallback, D-15).
        #expect(HealthSampleMapping.activityType(from: .americanFootball) == .other)
        #expect(HealthSampleMapping.activityType(from: .fishing) == .other)
        #expect(HealthSampleMapping.activityType(from: .other) == .other)
    }

    // MARK: - Nightly asleep-hours aggregation (D-15) — asleep-only, inBed/awake excluded

    @Test func nightlyAsleepHours_sumsOnlyAsleepSegments() {
        // Two asleep segments (core 3h + REM 1h) + one inBed (2h) + one awake (0.5h).
        let core = Int(HKCategoryValueSleepAnalysis.asleepCore.rawValue)
        let rem = Int(HKCategoryValueSleepAnalysis.asleepREM.rawValue)
        let inBed = Int(HKCategoryValueSleepAnalysis.inBed.rawValue)
        let awake = Int(HKCategoryValueSleepAnalysis.awake.rawValue)
        let segments = [
            SleepSegmentValue(value: core, duration: 3 * 3600),
            SleepSegmentValue(value: rem, duration: 1 * 3600),
            SleepSegmentValue(value: inBed, duration: 2 * 3600),   // excluded
            SleepSegmentValue(value: awake, duration: 0.5 * 3600), // excluded
        ]
        #expect(HealthSampleMapping.nightlyAsleepHours(segments) == 4.0)
    }

    @Test func nightlyAsleepHours_emptyInput_isZero() {
        #expect(HealthSampleMapping.nightlyAsleepHours([]) == 0)
    }

    @Test func nightlyAsleepHours_unknownStage_excluded_neverCrashes() {
        // DEFENSIVE (T-09-02): a garbage/unknown stage value is simply not counted (not asleep).
        let segments = [SleepSegmentValue(value: 9999, duration: 5 * 3600)]
        #expect(HealthSampleMapping.nightlyAsleepHours(segments) == 0)
    }

    // MARK: - Sleep-quality thresholds (D-15) — documented bands + both boundaries

    @Test func sleepQuality_poorBand_belowPoorCeiling() {
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: 0) == .poor)
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: 4.0) == .poor)
    }

    @Test func sleepQuality_poorCeilingBoundary_landsOnOkay() {
        // Boundary lands on the documented side: hours == poorHoursCeiling ⇒ .okay (>= ceiling is okay).
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: HealthSampleMapping.poorHoursCeiling) == .okay)
    }

    @Test func sleepQuality_okayBand_betweenCeilings() {
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: 6.0) == .okay)
    }

    @Test func sleepQuality_okayCeilingBoundary_landsOnGood() {
        // Boundary lands on the documented side: hours == okayHoursCeiling ⇒ .good (>= ceiling is good).
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: HealthSampleMapping.okayHoursCeiling) == .good)
    }

    @Test func sleepQuality_goodBand_atOrAboveOkayCeiling() {
        #expect(HealthSampleMapping.sleepQuality(forAsleepHours: 8.5) == .good)
    }

    // MARK: - Menstrual-flow mapping (D-14) — recognized values + nil for unknown (never fabricated)

    @Test func flowIntensity_recognizedValues_mapToFlowRaws() {
        // D-14: HKCategoryValueMenstrualFlow is the value enum for the `.menstrualFlow` category type.
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue:
            Int(HKCategoryValueMenstrualFlow.light.rawValue)) == .light)
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue:
            Int(HKCategoryValueMenstrualFlow.medium.rawValue)) == .medium)
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue:
            Int(HKCategoryValueMenstrualFlow.heavy.rawValue)) == .heavy)
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue:
            Int(HKCategoryValueMenstrualFlow.unspecified.rawValue)) == .spotting)
    }

    @Test func flowIntensity_noneAndUnknown_areNil_neverFabricated() {
        // DEFENSIVE (T-09-02): .none (no flow) and any garbage value ⇒ nil (never a fabricated flow).
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue:
            Int(HKCategoryValueMenstrualFlow.none.rawValue)) == nil)
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue: -1) == nil)
        #expect(HealthSampleMapping.flowIntensity(fromMenstrualFlowValue: 9999) == nil)
    }

    // MARK: - Nutrition presence (D-16) — presence-only "ate that day", never a fabricated FoodTag

    @Test func nutritionPresence_nonEmptySamples_isTrue() {
        #expect(HealthSampleMapping.ateThatDay(dietaryEnergySampleCount: 3) == true)
        #expect(HealthSampleMapping.ateThatDay(dietaryEnergySampleCount: 1) == true)
    }

    @Test func nutritionPresence_noSamples_isFalse_notFabricated() {
        // D-16: presence-only. No samples ⇒ false (NOT a fabricated FoodTag, NOT a micronutrient claim).
        #expect(HealthSampleMapping.ateThatDay(dietaryEnergySampleCount: 0) == false)
    }
}
