// SCAT-04 — the pure, app-target resolver that maps a `.correlation` Insight's PUBLIC `signalKey` to an
// OPTIONAL pair of quantitative scatter axes (or `nil` → the deep-link affordance is HIDDEN).
//
// LOAD-BEARING HONESTY CONTRACT (RESEARCH Pitfall 1 / threat T-16-01):
//   • The INPUT is the STRUCTURAL `signalKey` — a typed-by-convention machine key
//     (`correlation.<factor.key>.symptom.<SymptomType.rawValue>` or `correlation.<factor.key>.bleeding`),
//     NOT audited human prose. This resolver NEVER reads the Insight's headline or detail copy fields; it
//     re-derives nothing semantic from copy (the Phase-11 "direction from SIGNED effect, never parsed
//     copy" discipline forbids re-deriving SEMANTICS from prose — the signalKey is not prose). A reviewer
//     should NOT flag this as copy-parsing: it consumes the stable identity key only.
//   • The OUTPUT is a TYPED `(ExploreSeries, ExploreSeries)` value looked up in the catalog — the deep-link
//     carries this value; it does not re-derive axes downstream.
//
// DEFENSIVE PARSE (ASVS V5 / threat T-16-03): any malformed / unrecognized key yields `nil`, NEVER a crash
// or trap — same posture as the `-uitest-select-day` parser (AppDependencies). The key contains multiple
// dots (the factor.key is itself dotted, e.g. `stress.high`, `food.dairy`; the target too,
// `symptom.pelvicPain`), so parsing is by KNOWN SUFFIX (`bleeding` / `symptom.<known rawValue>`), NEVER a
// naive `.split(".")` that would mis-segment a multi-dot key.
//
// SUPPRESSION GATE (threat T-16-02): a `bleeding` target under `suppressed` resolves to `nil` — a
// suppressed user can never reach a bleeding axis via the deep-link (mirrors the Phase-14 accessor gate +
// the Phase-15 picker filter). Carried in unit tests.
//
// RESOLVED-Q1 (16-RESEARCH Open Questions): ONLY `stress.high → "stress"` and `mood.low → "mood"` map to a
// quantitative axis. `sleep.poor` (no quality→hours bridge in v1.1) and ALL presence-only factors
// (food / activity / medication / PRN / cyclePhase) resolve to `nil` → the affordance is HIDDEN.
import Foundation
import EndoInsights

/// Maps a `.correlation` `Insight.signalKey` to two quantitative scatter axes, or `nil` (HIDE affordance).
/// A pure value type — fully unit-testable WITHOUT rendering (mirrors the `ExploreSelection.offered`
/// pure-catalog-value-mapper posture).
struct ScatterDeepLink {

    /// The `correlation.` prefix every correlation signalKey carries (CorrelationEngine).
    private static let correlationPrefix = "correlation."
    /// The literal bleeding-target fragment (CorrelationEngine `PresenceTarget.keyFragment`).
    private static let bleedingFragment = "bleeding"

    /// Resolve a `.correlation` `signalKey` to an `(x, y)` pair of quantitative `ExploreSeries`, or `nil`.
    ///
    /// Returns a non-nil pair ONLY when BOTH the factor and the target map to a catalogued series AND both
    /// of those series are `.isQuantitative`. Every other input — a presence-only factor, `sleep.poor`, a
    /// suppressed bleeding target, or any malformed/unrecognized key — yields `nil` (the affordance is
    /// hidden).
    ///
    /// - Parameters:
    ///   - signalKey: the STRUCTURAL public key (never audited prose).
    ///   - suppressed: the Phase-14 bleeding-suppression flag; a `bleeding` target under suppression → nil.
    ///   - catalog: the series catalog to look ids up in (default the shipped catalog; injectable for tests).
    static func resolve(
        signalKey: String,
        suppressed: Bool,
        catalog: [ExploreSeries] = ExploreSeriesCatalog.all
    ) -> (x: ExploreSeries, y: ExploreSeries)? {
        // 1. Must be a correlation key; strip the prefix. Anything else → nil.
        guard signalKey.hasPrefix(correlationPrefix) else { return nil }
        let body = String(signalKey.dropFirst(correlationPrefix.count))
        guard !body.isEmpty else { return nil }

        // 2. Detect the TARGET by KNOWN suffix (never a naive split — the factor.key is itself dotted).
        //    The body is `<factor.key>.<target>`. The target is either the literal `bleeding`, or
        //    `symptom.<rawValue>` for a known SymptomType rawValue.
        guard let (factorKey, target) = splitFactorAndTarget(body) else { return nil }

        // 3. factor.key → optional quantitative X series id (RESOLVED-Q1: only stress.high / mood.low map).
        guard let xID = quantitativeFactorAxisID(for: factorKey) else { return nil }

        // 4. target → optional quantitative Y series id (bleeding gated by suppression).
        guard let yID = quantitativeTargetAxisID(for: target, suppressed: suppressed) else { return nil }

        // 5. Look BOTH ids up in the catalog (never construct an ExploreSeries) — a catalog edit can't drift.
        guard
            let x = catalog.first(where: { $0.id == xID }),
            let y = catalog.first(where: { $0.id == yID }),
            x.scaleKind.isQuantitative,
            y.scaleKind.isQuantitative
        else { return nil }

        return (x: x, y: y)
    }

    // MARK: - Parsing (by known suffix — defensive, multi-dot safe)

    /// A parsed target: either the bleeding axis, or a symptom severity axis for a known SymptomType.
    private enum Target: Equatable {
        case bleeding
        case symptom(rawValue: String)
    }

    /// Split the prefix-stripped body `<factor.key>.<target>` into `(factorKey, target)` by recognizing the
    /// target as a KNOWN suffix. Returns `nil` if the tail is neither `bleeding` nor `symptom.<known raw>`,
    /// or if no non-empty factor.key precedes it.
    private static func splitFactorAndTarget(_ body: String) -> (factorKey: String, target: Target)? {
        // Bleeding target: the body ends in `.bleeding`.
        let bleedingSuffix = "." + bleedingFragment
        if body.hasSuffix(bleedingSuffix) {
            let factorKey = String(body.dropLast(bleedingSuffix.count))
            guard !factorKey.isEmpty else { return nil }
            return (factorKey, .bleeding)
        }

        // Symptom target: the body ends in `.symptom.<rawValue>` for a KNOWN SymptomType rawValue.
        // Enumerate from `SymptomType.allCases` so a multi-dot factor.key can never mis-segment.
        for type in SymptomType.allCases {
            let symptomSuffix = ".symptom.\(type.rawValue)"
            if body.hasSuffix(symptomSuffix) {
                let factorKey = String(body.dropLast(symptomSuffix.count))
                guard !factorKey.isEmpty else { return nil }
                return (factorKey, .symptom(rawValue: type.rawValue))
            }
        }

        return nil
    }

    // MARK: - Factor / target → quantitative axis id (RESOLVED-Q1)

    /// Map a `factor.key` to its quantitative axis catalog id, or `nil` (presence-only / sleep-quality /
    /// unrecognized → HIDE affordance). ONLY `stress.high` and `mood.low` map in v1.1.
    private static func quantitativeFactorAxisID(for factorKey: String) -> String? {
        switch factorKey {
        case "stress.high": return "stress"
        case "mood.low":    return "mood"
        // sleep.poor (no quality→hours bridge), food.*, activity.*, medication.taken, prn.taken,
        // cyclePhase.* — all presence/categorical, no quantitative axis (RESOLVED-Q1).
        default:            return nil
        }
    }

    /// Map a parsed target to its quantitative axis catalog id. A `bleeding` target under suppression → nil.
    private static func quantitativeTargetAxisID(for target: Target, suppressed: Bool) -> String? {
        switch target {
        case .symptom(let rawValue):
            return "symptom.\(rawValue).severity"
        case .bleeding:
            return suppressed ? nil : "bleeding.intensity"
        }
    }
}
