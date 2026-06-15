// EXPL-02 — the pure value type that holds the user's chosen Explore series + time window and enforces
// the 3–4 quantitative cap, the toggle semantics, and the suppression filter. App target (RESEARCH OQ2
// RESOLVED — NOT EndoInsights, to keep the pure package surface narrow). Composes the Phase-14 catalog
// (`ExploreSeries`/`ScaleKind`/`TrendWindow`/`ExploreSeriesCatalog`) UNCHANGED.
//
// PRIVACY (T-15-03, LOCKED): this is a plain value type with NO persistence. The consuming picker view
// (15-05) holds it in EPHEMERAL view state — persisting series keys would leak health-interest metadata
// outside the consent-gated store. There is intentionally NO persistent property-wrapper storage here;
// the plan's grep gate forbids any such persistence wrapper in this file.
import Foundation
import EndoInsights

/// The user's current Explore selection: which series are overlaid + the shared time window, plus the
/// cap/toggle policy (EXPL-02). A pure `Equatable` value type — testable WITHOUT rendering, so the
/// cap/suppression rules are provably correct independent of UI.
struct ExploreSelection: Equatable, Sendable {

    /// The chosen series. `private(set)` so the cap/toggle invariants can only be mutated through
    /// `toggle(_:)` — callers can never bypass the cap by inserting directly. `Set<ExploreSeries>`
    /// works because `ExploreSeries` is `Hashable` from `id` alone (ExploreSeries.swift:97-110).
    private(set) var series: Set<ExploreSeries> = []

    /// The shared time window for every lane (EXPL-06). Reuses the dashboard `TrendWindow` family.
    var window: TrendWindow = .last30

    /// "Up to four series at once" — the LOCKED copy. Only QUANTITATIVE series count toward this cap;
    /// presence/event lanes are a separate, uncapped allowance.
    static let quantitativeCap = 4

    /// How many currently-selected series are quantitative (presence/event series are excluded —
    /// `ScaleKind.isQuantitative` is `self != .presenceEvent`, ExploreSeries.swift:41).
    var quantitativeCount: Int {
        series.filter { $0.scaleKind.isQuantitative }.count
    }

    /// Whether `s` may be ADDED right now:
    ///   • a presence/event series is always addable (uncapped);
    ///   • an already-selected series is always addable (so a re-tap to remove is never blocked);
    ///   • otherwise a NEW quantitative series is addable only while below the cap.
    func canAddQuantitative(_ s: ExploreSeries) -> Bool {
        !s.scaleKind.isQuantitative
            || series.contains(s)
            || quantitativeCount < Self.quantitativeCap
    }

    /// Toggle `s`: remove it if present; otherwise insert it ONLY if addable. Adding a 5th distinct
    /// quantitative series at the cap is a no-op; event series are never blocked.
    mutating func toggle(_ s: ExploreSeries) {
        if series.contains(s) {
            series.remove(s)
        } else if canAddQuantitative(s) {
            series.insert(s)
        }
    }

    /// The CATALOG the picker should offer (NOT cap-model state — a pure catalog filter). For a
    /// suppressed user BOTH bleeding series (`bleeding.intensity` + `bleeding.event`) are removed, so a
    /// suppressed user is never even OFFERED them (T-15-04, defense-in-depth alongside the Phase-14
    /// accessor gate). When `suppressed == false` the full catalog is returned unchanged.
    static func offered(
        suppressed: Bool,
        catalog: [ExploreSeries] = ExploreSeriesCatalog.all
    ) -> [ExploreSeries] {
        catalog.filter { s in
            !(suppressed && (s.id == "bleeding.intensity" || s.id == "bleeding.event"))
        }
    }
}
