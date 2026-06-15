// EndoInsights — shared Phase-14 series test harness (OWNED by Plan 14-01).
//
// This file is the single source of deterministic test fixtures for ALL FOUR Phase-14 plans
// (catalog/extraction/scatter/normalization). Later plans (14-02/03/04) only READ/CALL these helpers —
// they NEVER modify this file. Mirrors the `ChartScrubSelectionTests` harness verbatim: a UTC-fixed
// calendar + a `day(...)` builder + a `daySnapshot(...)` builder that seeds ONLY the fields a test
// cares about (everything else honestly nil/empty/false, so absence ≠ 0 holds in every fixture).
import Testing
import Foundation
@testable import EndoInsights

/// A deterministic, DST-agnostic calendar fixed to UTC so day math is reproducible across machines.
func utcCalendar() -> Calendar {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    return cal
}

/// Build a concrete `Date` from components in the (UTC-fixed) calendar — never `Date()` (determinism).
func day(_ year: Int, _ month: Int, _ d: Int, hour: Int = 12, minute: Int = 0,
        calendar: Calendar = utcCalendar()) -> Date {
    var comps = DateComponents()
    comps.year = year; comps.month = month; comps.day = d
    comps.hour = hour; comps.minute = minute
    return calendar.date(from: comps)!
}

/// Build a `DaySnapshot` seeding ONLY the fields a test cares about. Every unseeded field is honestly
/// `nil`/empty/`false` (absence ≠ 0), so a fixture never silently fabricates a logged signal.
func daySnapshot(
    day: Date,
    symptoms: [SymptomPoint] = [],
    pain: [PainPoint] = [],
    flow: FlowIntensity? = nil,
    clots: Bool = false,
    foodTags: [FoodTag] = [],
    activityType: ActivityType? = nil,
    activityMinutes: Double? = nil,
    activityIntensity: ActivityIntensity? = nil,
    sleepHours: Double? = nil,
    sleepQuality: SleepQuality? = nil,
    medicationsTaken: [UUID] = [],
    tookPRN: Bool = false,
    stress: Int? = nil,
    mood: Int? = nil,
    hasNote: Bool = false,
    suppressed: Bool = false
) -> DaySnapshot {
    DaySnapshot(
        day: day,
        symptoms: symptoms,
        pain: pain,
        flow: flow,
        clots: clots,
        foodTags: foodTags,
        activityType: activityType,
        activityMinutes: activityMinutes,
        activityIntensity: activityIntensity,
        sleepHours: sleepHours,
        sleepQuality: sleepQuality,
        medicationsTaken: medicationsTaken,
        tookPRN: tookPRN,
        stress: stress,
        mood: mood,
        hasNote: hasNote,
        suppressed: suppressed
    )
}
