// EndoInsights — series catalog + scale taxonomy (Phase 14). Foundation only — NO SwiftData/SwiftUI/UIKit (SC1).
//
// THE keystone of Phase 14: the single source of truth for "what series exist" that both the Explore
// chips (EXPL-02) and the Scatter axis pickers (SCAT-01) read. Every downstream helper (extraction,
// scatter pairing, normalization) composes on top of this file.
//
// HONESTY (encoded in the type system, not runtime checks — RESEARCH §Summary):
//   • `ScaleKind` makes the quantitative-vs-presence distinction explicit, so a presence series can
//     NEVER be coerced into a 0/1 quantitative value (the structural move, Pitfall 1/3).
//   • `quantitativeValue(from:)` returns `nil` for an unlogged field (gap, absence ≠ 0) and a REAL
//     value for a logged literal 0 — via `snap.x.map(Double.init)`, NEVER a `> 0`/truthiness filter
//     (Pitfall 2). Bleeding-derived series gate on `!snap.suppressed` (no leak, Pitfall 4).
//   • `ExploreSeries` derives Equatable/Hashable/Identifiable from `id` ALONE — NO stored extraction
//     closure (closures aren't Hashable; Pitfall 3) — so Phase-15 `Set<ExploreSeries>` chip selection
//     (EXPL-02) compiles for free.
//
// The catalog is enumerated FROM the existing `CaseIterable` enum domains (`SymptomType`/`BodyRegion`/
// `FoodTag`) so it cannot drift from them by construction (Pattern 2). Day math is calendar-injected
// in the EXTRACTION helper (Plan 02); the accessors here are value-only — NEVER epoch arithmetic.
import Foundation

/// The explicit scale taxonomy for every catalogued series (RESEARCH Pattern 2). Modelling the kind
/// explicitly is the structural honesty move: a `.presenceEvent` series has no numeric value, so
/// "coerce a tag to 0/1" is unrepresentable on the quantitative path.
public enum ScaleKind: String, Sendable, Equatable, Hashable, CaseIterable {
    /// Symptom/pain severity, stress — a 0…10 ordinal.
    case ordinal0to10
    /// Day mood — a 1…5 ordinal.
    case ordinal1to5
    /// Sleep duration — continuous hours.
    case continuousHours
    /// Activity duration — continuous minutes.
    case continuousMinutes
    /// Bleeding intensity — `FlowIntensity` spotting…heavy mapped to a 1…4 ordinal rank.
    case ordinalBleeding
    /// Food tags, clots, PRN, medication-taken, bleeding-as-events — presence/occurrence only.
    case presenceEvent

    /// `true` for every kind EXCEPT `.presenceEvent`. Presence series route via `isPresent(in:)`
    /// (→ `[Date]` occurrence days downstream), never `quantitativeValue(from:)`.
    public var isQuantitative: Bool { self != .presenceEvent }
}

/// The stored discriminator the extraction helper (Plan 02/03) switches on (RESEARCH OQ1 RESOLVED).
///
/// A stored ENUM, NOT a stored closure — so `ExploreSeries` keeps a Hashable payload and the struct's
/// id-only conformances stay valid (Pitfall 3). Associated values carry the sub-series that varies
/// (per symptom type, per pain region, per food tag); the singleton fields carry none.
public enum ExploreSeriesField: Sendable, Equatable, Hashable {
    case symptomSeverity(SymptomType)
    case painSeverity(BodyRegion)
    case stress
    case mood
    case sleepHours
    case activityMinutes
    case bleedingIntensity
    case bleedingEvent
    case food(FoodTag)
    case clots
    case prn
    case medicationTaken
}

/// One catalogued series: a stable value-independent id + display/scale metadata + the extraction
/// discriminator. Equatable/Hashable/Identifiable derive from `id` ALONE (no stored closure) so a
/// Phase-15 `Set<ExploreSeries>` chip selection compiles (EXPL-02 / Pitfall 3).
public struct ExploreSeries: Sendable, Equatable, Hashable, Identifiable {
    /// Stable, value-independent key (mirrors `Insight.signalKey` dotted style): e.g.
    /// `"symptom.pelvicPain.severity"`, `"pain.lowerBack.severity"`, `"sleep.hours"`, `"stress"`,
    /// `"mood"`, `"bleeding.intensity"`, `"bleeding.event"`, `"food.dairy"`, `"clots"`, `"prn"`,
    /// `"medication.taken"`.
    public let id: String
    /// Chrome copy — Phase 15 routes display copy through its own audited catalog; this is the
    /// descriptive default.
    public let displayName: String
    /// Honest readout unit: `"of 10"`, `"of 5"`, `"h"`, `"min"`, or `""` (presence / bleeding rank).
    public let unit: String
    /// The explicit scale taxonomy entry.
    public let scaleKind: ScaleKind
    /// The closure-free extraction discriminator the accessors switch on.
    public let field: ExploreSeriesField

    public init(
        id: String,
        displayName: String,
        unit: String,
        scaleKind: ScaleKind,
        field: ExploreSeriesField
    ) {
        self.id = id
        self.displayName = displayName
        self.unit = unit
        self.scaleKind = scaleKind
        self.field = field
    }

    // MARK: id-only conformances (Pitfall 3 — NO stored closure participates)

    /// Two series are equal iff their stable ids match — no other stored field participates, so a
    /// future closure-typed property could never be (silently) ignored here.
    public static func == (lhs: ExploreSeries, rhs: ExploreSeries) -> Bool {
        lhs.id == rhs.id
    }

    /// Hash the stable id ONLY — collapses two series with identical ids and keeps `Set<ExploreSeries>`
    /// count == catalog count (proven by `catalogIdsAreUnique`).
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

// MARK: - Value accessors (Task 2 fills the real bodies; switches are exhaustive + compile-clean)

extension ExploreSeries {

    /// The series' quantitative value for a day, or `nil` when that field was NOT logged (gap,
    /// absence ≠ 0). A logged literal 0 returns a REAL `0.0` (Pitfall 2 — `snap.x.map(Double.init)`,
    /// NEVER a `> 0`/truthiness filter). Presence-kind fields return `nil` here — they route via
    /// `isPresent(in:)`, so a presence series can never be coerced to a number (Pitfall 1).
    /// Bleeding-derived series gate on `!snap.suppressed` first (no leak — Pitfall 4 / Task 2).
    public func quantitativeValue(from snap: DaySnapshot) -> Double? {
        switch field {
        case .symptomSeverity(let type):
            return snap.symptoms.first { $0.type == type }?.severity.map(Double.init)
        case .painSeverity(let region):
            return snap.pain.first { $0.region == region }?.severity.map(Double.init)
        case .stress:
            return snap.stress.map(Double.init)
        case .mood:
            return snap.mood.map(Double.init)
        case .sleepHours:
            return snap.sleepHours
        case .activityMinutes:
            return snap.activityMinutes
        case .bleedingIntensity:
            // Suppression gate FIRST — a suppressed user's bleeding must never produce a value
            // (Pitfall 4 / D-03). nil flow → gap; logged flow → its honest 1…4 ordinal rank.
            guard !snap.suppressed else { return nil }
            return snap.flow.map { Double(ExploreSeries.ordinalRank(of: $0)) }
        case .bleedingEvent, .food, .clots, .prn, .medicationTaken:
            // Presence-kind fields have no quantitative value — callers route via `isPresent(in:)`.
            return nil
        }
    }

    /// Whether the presence/event thing was LOGGED on a day. Quantitative fields return `false` here —
    /// they route via `quantitativeValue(from:)`. Bleeding-as-event gates on `!snap.suppressed`
    /// (Task 2). For non-presence fields this is always `false` (presence is undefined for them).
    public func isPresent(in snap: DaySnapshot) -> Bool {
        switch field {
        case .food(let tag):
            return snap.foodTags.contains(tag)
        case .clots:
            return snap.clots
        case .prn:
            return snap.tookPRN
        case .medicationTaken:
            return !snap.medicationsTaken.isEmpty
        case .bleedingEvent:
            // Suppression gate — a suppressed user's bleeding event must never surface (Pitfall 4).
            // Present iff NOT suppressed AND bleeding was logged that day.
            return !snap.suppressed && snap.flow != nil
        case .symptomSeverity, .painSeverity, .stress, .mood,
             .sleepHours, .activityMinutes, .bleedingIntensity:
            // Quantitative fields route via `quantitativeValue(from:)`, never presence.
            return false
        }
    }

    /// The honest ordinal rank of a `FlowIntensity` for the `bleeding.intensity` quantitative series
    /// (RESEARCH A1): spotting=1, light=2, medium=3, heavy=4 — domain 1…4 (`ScaleKind.ordinalBleeding`).
    static func ordinalRank(of flow: FlowIntensity) -> Int {
        switch flow {
        case .spotting: return 1
        case .light:    return 2
        case .medium:   return 3
        case .heavy:    return 4
        }
    }
}

/// The single source of truth for "what series exist" (Pattern 2 / EXPL-02 + SCAT-01). Built by
/// enumerating the existing `CaseIterable` enum domains (`SymptomType`/`BodyRegion`/`FoodTag`) so it
/// stays in sync with them BY CONSTRUCTION — no hand-maintained case list to drift. Bleeding appears
/// as BOTH a quantitative ordinal series (`bleeding.intensity`) AND a presence event series
/// (`bleeding.event`), each suppression-gated downstream (RESEARCH OQ2 RESOLVED).
public enum ExploreSeriesCatalog {

    /// Every loggable series, in a stable order: per-symptom severity (11) → per-pain-region severity
    /// (10) → per-food-tag presence (9) → the singleton fields. Ids are unique, dotted, and
    /// value-independent (proven by `catalogIdsAreUnique`).
    public static let all: [ExploreSeries] = {
        var series: [ExploreSeries] = []

        // Per-symptom-type severity — a DISTINCT selectable series each (NOT a lumped "symptoms").
        for type in SymptomType.allCases {
            series.append(ExploreSeries(
                id: "symptom.\(type.rawValue).severity",
                displayName: "\(type.rawValue) severity",
                unit: "of 10",
                scaleKind: .ordinal0to10,
                field: .symptomSeverity(type)
            ))
        }

        // Per-pain-region severity — a DISTINCT selectable series each.
        for region in BodyRegion.allCases {
            series.append(ExploreSeries(
                id: "pain.\(region.rawValue).severity",
                displayName: "\(region.rawValue) pain severity",
                unit: "of 10",
                scaleKind: .ordinal0to10,
                field: .painSeverity(region)
            ))
        }

        // Per-food-tag presence/occurrence.
        for tag in FoodTag.allCases {
            series.append(ExploreSeries(
                id: "food.\(tag.rawValue)",
                displayName: "\(tag.rawValue)",
                unit: "",
                scaleKind: .presenceEvent,
                field: .food(tag)
            ))
        }

        // Singleton fields.
        series.append(ExploreSeries(
            id: "sleep.hours", displayName: "Sleep", unit: "h",
            scaleKind: .continuousHours, field: .sleepHours))
        series.append(ExploreSeries(
            id: "activity.minutes", displayName: "Activity", unit: "min",
            scaleKind: .continuousMinutes, field: .activityMinutes))
        series.append(ExploreSeries(
            id: "stress", displayName: "Stress", unit: "of 10",
            scaleKind: .ordinal0to10, field: .stress))
        series.append(ExploreSeries(
            id: "mood", displayName: "Mood", unit: "of 5",
            scaleKind: .ordinal1to5, field: .mood))
        series.append(ExploreSeries(
            id: "bleeding.intensity", displayName: "Bleeding intensity", unit: "",
            scaleKind: .ordinalBleeding, field: .bleedingIntensity))
        series.append(ExploreSeries(
            id: "bleeding.event", displayName: "Bleeding", unit: "",
            scaleKind: .presenceEvent, field: .bleedingEvent))
        series.append(ExploreSeries(
            id: "clots", displayName: "Clots", unit: "",
            scaleKind: .presenceEvent, field: .clots))
        series.append(ExploreSeries(
            id: "prn", displayName: "PRN pain relief", unit: "",
            scaleKind: .presenceEvent, field: .prn))
        series.append(ExploreSeries(
            id: "medication.taken", displayName: "Medication taken", unit: "",
            scaleKind: .presenceEvent, field: .medicationTaken))

        return series
    }()
}
