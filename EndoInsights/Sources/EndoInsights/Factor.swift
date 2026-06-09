// EndoInsights — the three-state factor model the CorrelationEngine binarizes a day into
// (Phase 7, Plan 02 / D-04..D-13/D-16 / SC1 / V7).
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLint is the proof). Mirrors
// `DaySnapshot`'s absence≠0 honesty contract (lines 9–12, 91–94) and `TrendsEngine.factorTrends`
// (lines 177–206, the existing `sleepQuality == .poor` / `stress` / `mood` binarization).
//
// THE CORE HONESTY INVARIANT (Pitfall 2): every factor is exactly one of three states on a day —
// `.present` / `.absent` / `.unobserved`. `.unobserved` is EXCLUDED from BOTH the present and absent
// groups (a `nil` severity/stress/mood/sleepQuality is "not logged", NEVER a 0). The engine never
// coerces a missing signal to a value; it drops the day from that factor's denominator.
//
// The cycle-phase factor's window logic stays in `CyclePhaseMapper` (reused verbatim by the engine,
// D-04/D-17); `Factor` only accepts the per-day `CyclePhase?` label the engine supplies.
import Foundation

/// A day's state for a given factor (D-04..D-13). `.unobserved` is excluded from both groups (absence≠0).
enum FactorState: Equatable {
    case present
    case absent
    case unobserved
}

/// A bounded factor a symptom/bleeding target can be correlated against (D-09). The associated values
/// carry the parametric families (food tag, activity type, cycle phase) so enumeration stays exhaustive
/// and deterministic. Each case maps to a stable `key` — the `signalKey` fragment the Insight is built on.
enum Factor: Equatable {
    case food(FoodTag)
    case activityPresent
    case activity(ActivityType)
    case sleepPoor
    case stressHigh
    case moodLow
    case medicationTaken
    case prnTaken
    case cyclePhase(CyclePhase)

    // MARK: - Named threshold constants — the ONLY judgement calls (07-RESEARCH §1, A2/A5).
    // [ASSUMED] These are asserted against their locked values by `CorrelationEngineTests`; there are
    // NO buried numeric literals in the binarization below (the `TrendsEngine.minLoggedDays` precedent).

    /// High-stress present when `stress >= 7` on the 0…10 scale (top ~third). [ASSUMED — A2]
    static let highStressThreshold = 7
    /// Low-mood present when `mood <= 2` on the 1…5 valence scale (bottom two). [ASSUMED — A2]
    static let lowMoodThreshold = 2
    /// Each of the two correlation groups (present/absent, W/O) needs at least this many observations
    /// before the engine will test the pair (mirrors `TrendsEngine.minLoggedDays`). [ASSUMED — A5]
    static let perGroupMinimum = 3

    /// The stable `signalKey` fragment for this factor (07-RESEARCH §7 scheme). Sortable for determinism.
    var key: String {
        switch self {
        case .food(let tag):       return "food.\(tag.rawValue)"
        case .activityPresent:     return "activity.present"
        case .activity(let type):  return "activity.\(type.rawValue)"
        case .sleepPoor:           return "sleep.poor"
        case .stressHigh:          return "stress.high"
        case .moodLow:             return "mood.low"
        case .medicationTaken:     return "medication.taken"
        case .prnTaken:            return "prn.taken"
        case .cyclePhase(let p):   return "cyclePhase.\(p.rawValue)"
        }
    }

    /// A human-readable label for the (hand-written, non-causal) Insight copy.
    var displayLabel: String {
        switch self {
        case .food(let tag):       return foodLabel(tag)
        case .activityPresent:     return "activity"
        case .activity(let type):  return activityLabel(type)
        case .sleepPoor:           return "poor sleep"
        case .stressHigh:          return "high stress"
        case .moodLow:             return "low mood"
        case .medicationTaken:     return "medication"
        case .prnTaken:            return "as-needed pain relief"
        case .cyclePhase(let p):   return "\(phaseLabel(p)) phase"
        }
    }

    // MARK: - Three-state binarization (07-RESEARCH §1 table EXACTLY). nil is NEVER coerced to 0.

    /// The factor's state on a day. `.unobserved` for a `nil` context scale / `nil` sleepQuality / a truly
    /// empty day for the "any-log" factors; `.absent` for a day-with-any-log that simply lacks the signal
    /// (A7). The cycle-phase factor needs the engine-supplied per-day label (see `state(of:on:phase:)`).
    static func state(of factor: Factor, on day: DaySnapshot) -> FactorState {
        switch factor {
        case .food(let tag):
            // Empty foodTags on a day-with-any-log → absent; a truly empty day → unobserved (A7).
            if day.foodTags.contains(tag) { return .present }
            return hasAnyLog(day) ? .absent : .unobserved

        case .activityPresent:
            if day.activityType != nil { return .present }
            return hasAnyLog(day) ? .absent : .unobserved

        case .activity(let type):
            if day.activityType == type { return .present }
            if day.activityType != nil { return .absent }     // a different activity logged → absent
            return hasAnyLog(day) ? .absent : .unobserved

        case .sleepPoor:
            guard let quality = day.sleepQuality else { return .unobserved }   // nil sleep → unobserved
            return quality == .poor ? .present : .absent

        case .stressHigh:
            guard let stress = day.stress else { return .unobserved }          // nil stress → unobserved
            return stress >= highStressThreshold ? .present : .absent

        case .moodLow:
            guard let mood = day.mood else { return .unobserved }              // nil mood → unobserved
            return mood <= lowMoodThreshold ? .present : .absent

        case .medicationTaken:
            if !day.medicationsTaken.isEmpty { return .present }
            return hasAnyLog(day) ? .absent : .unobserved

        case .prnTaken:
            if day.tookPRN { return .present }
            return hasAnyLog(day) ? .absent : .unobserved

        case .cyclePhase:
            // The cycle-phase factor cannot be evaluated without the engine-supplied label; the engine
            // calls `state(of:on:phase:)`. Without a phase context this factor is unobserved by default.
            return .unobserved
        }
    }

    /// Cycle-phase-aware state. `phase` is the engine-supplied `CyclePhaseMapper` label for this day (or
    /// `nil` if the day is outside any estimated window / the user is suppressed → no windows). For a
    /// `.cyclePhase(target)` factor: `.present` iff `phase == target`, `.absent` iff the day is labelled a
    /// DIFFERENT phase, `.unobserved` iff the day has no phase label (outside windows OR suppressed → D-04
    /// by construction). For every non-phase factor this delegates to the phase-free `state(of:on:)`.
    static func state(of factor: Factor, on day: DaySnapshot, phase: CyclePhase?) -> FactorState {
        guard case .cyclePhase(let target) = factor else {
            return state(of: factor, on: day)
        }
        guard let phase else { return .unobserved }   // no window label → unobserved (suppressed → D-04)
        return phase == target ? .present : .absent
    }

    // MARK: - "Day has any log" — the absent-vs-unobserved decision (mirror TrendsEngine's tracked-day).

    /// True if the day carries ANY logged signal (so an empty food/med/activity is honestly `.absent`,
    /// not `.unobserved`). Mirrors how `TrendsEngine` decides a day exists in the stream.
    static func hasAnyLog(_ day: DaySnapshot) -> Bool {
        !day.symptoms.isEmpty
            || !day.pain.isEmpty
            || day.flow != nil
            || day.clots
            || !day.foodTags.isEmpty
            || day.activityType != nil
            || day.activityMinutes != nil
            || day.activityIntensity != nil
            || day.sleepHours != nil
            || day.sleepQuality != nil
            || !day.medicationsTaken.isEmpty
            || day.tookPRN
            || day.stress != nil
            || day.mood != nil
            || day.hasNote
    }

    // MARK: - Labels (package-local; mirrors CyclePhaseMapper.displayName / phaseName).

    private func foodLabel(_ tag: FoodTag) -> String {
        switch tag {
        case .dairy:            return "dairy"
        case .glutenWheat:      return "gluten / wheat"
        case .caffeine:         return "caffeine"
        case .alcohol:          return "alcohol"
        case .highSugar:        return "high-sugar food"
        case .highFODMAP:       return "high-FODMAP food"
        case .redProcessedMeat: return "red / processed meat"
        case .largeMeal:        return "a large meal"
        case .other:            return "other food"
        }
    }

    private func activityLabel(_ type: ActivityType) -> String {
        switch type {
        case .walking:        return "walking"
        case .cardio:         return "cardio"
        case .strength:       return "strength training"
        case .yogaStretching: return "yoga / stretching"
        case .other:          return "other activity"
        }
    }

    private func phaseLabel(_ phase: CyclePhase) -> String {
        switch phase {
        case .menstrual:  return "menstrual"
        case .follicular: return "follicular"
        case .ovulatory:  return "ovulatory"
        case .luteal:     return "luteal"
        }
    }
}
