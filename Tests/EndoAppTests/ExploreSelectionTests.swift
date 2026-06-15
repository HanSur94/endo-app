// EXPL-02 unit suite — proves the pure `ExploreSelection` cap/toggle/suppression-filter rules WITHOUT
// rendering. Swift Testing (`@Suite`/`@Test`/`#expect`; mirrors the shape in CopyAuditTests.swift:6-12),
// app target, kept separate from the XCUITest target (Swift Testing and XCTest don't interoperate).
//
// HONESTY: every `ExploreSeries` fixture is pulled from the SHIPPED `ExploreSeriesCatalog.all` by id —
// never hand-constructed — so this suite can never silently drift from the real catalog.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Explore selection (EXPL-02 cap + toggle + suppression filter)")
struct ExploreSelectionTests {

    // MARK: catalog fixtures (pulled from the real catalog — never hand-constructed)

    /// Look a series up in the shipped catalog by id; fail loudly if the catalog ever drops it.
    private func series(_ id: String) -> ExploreSeries {
        guard let s = ExploreSeriesCatalog.all.first(where: { $0.id == id }) else {
            Issue.record("Catalog is missing series id \"\(id)\" — fixture drift.")
            // Unreachable in a healthy catalog; satisfies the non-Optional return.
            return ExploreSeriesCatalog.all[0]
        }
        return s
    }

    // Five DISTINCT quantitative series so we can probe the 4-cap with a genuine 5th.
    private var sleep: ExploreSeries { series("sleep.hours") }            // .continuousHours
    private var activity: ExploreSeries { series("activity.minutes") }    // .continuousMinutes
    private var stress: ExploreSeries { series("stress") }                // .ordinal0to10
    private var mood: ExploreSeries { series("mood") }                    // .ordinal1to5
    private var bleedingIntensity: ExploreSeries { series("bleeding.intensity") } // .ordinalBleeding (quantitative)

    // Event / presence series (uncapped).
    private var bleedingEvent: ExploreSeries { series("bleeding.event") } // .presenceEvent
    private var clots: ExploreSeries { series("clots") }                  // .presenceEvent

    // MARK: fixtures are genuinely quantitative / presence (guards the cap semantics)

    @Test func quantitativeFixturesAreQuantitative() {
        for s in [sleep, activity, stress, mood, bleedingIntensity] {
            #expect(s.scaleKind.isQuantitative, "\(s.id) must be a quantitative series for these tests.")
        }
    }

    @Test func eventFixturesAreNotQuantitative() {
        for s in [bleedingEvent, clots] {
            #expect(!s.scaleKind.isQuantitative, "\(s.id) must be a presence/event series for these tests.")
        }
    }

    // MARK: cap — blocks the 5th distinct quantitative

    @Test func capBlocksFifthDistinctQuantitative() {
        var sel = ExploreSelection()
        sel.toggle(sleep)
        sel.toggle(activity)
        sel.toggle(stress)
        sel.toggle(mood)
        #expect(sel.quantitativeCount == 4)
        #expect(sel.series.count == 4)

        // A NEW (not-yet-selected) quantitative series is blocked at the cap.
        #expect(sel.canAddQuantitative(bleedingIntensity) == false)
        sel.toggle(bleedingIntensity)
        #expect(sel.quantitativeCount == 4, "Adding a 5th quantitative at the cap must be a no-op.")
        #expect(!sel.series.contains(bleedingIntensity))
    }

    // MARK: cap — never blocks an already-selected series (re-tap to remove must always work)

    @Test func capNeverBlocksAlreadySelectedSeries() {
        var sel = ExploreSelection()
        sel.toggle(sleep)
        sel.toggle(activity)
        sel.toggle(stress)
        sel.toggle(mood)
        #expect(sel.quantitativeCount == 4)

        // An ALREADY-selected quantitative series is addable even at the cap (so re-tap → remove works).
        #expect(sel.canAddQuantitative(mood) == true)
        sel.toggle(mood) // removes
        #expect(!sel.series.contains(mood))
        #expect(sel.quantitativeCount == 3)
    }

    // MARK: cap — presence/event series are NEVER capped

    @Test func eventSeriesAreUncapped() {
        var sel = ExploreSelection()
        sel.toggle(sleep)
        sel.toggle(activity)
        sel.toggle(stress)
        sel.toggle(mood)
        #expect(sel.quantitativeCount == 4)

        // Event series add beyond the quantitative cap and never count toward it.
        #expect(sel.canAddQuantitative(bleedingEvent) == true)
        sel.toggle(bleedingEvent)
        sel.toggle(clots)
        #expect(sel.series.contains(bleedingEvent))
        #expect(sel.series.contains(clots))
        #expect(sel.quantitativeCount == 4, "Event series must not count toward the quantitative cap.")
        #expect(sel.series.count == 6)
    }

    // MARK: toggle — adds then removes on the second tap

    @Test func toggleAddsThenRemoves() {
        var sel = ExploreSelection()
        #expect(!sel.series.contains(sleep))

        sel.toggle(sleep)
        #expect(sel.series.contains(sleep))

        sel.toggle(sleep)
        #expect(!sel.series.contains(sleep))
    }

    // MARK: cap — remove-to-add (free a slot, then the previously-blocked series adds)

    @Test func removeToAddFreesACapSlot() {
        var sel = ExploreSelection()
        sel.toggle(sleep)
        sel.toggle(activity)
        sel.toggle(stress)
        sel.toggle(mood)
        #expect(sel.canAddQuantitative(bleedingIntensity) == false)

        sel.toggle(mood) // remove one → frees a slot
        #expect(sel.canAddQuantitative(bleedingIntensity) == true)
        sel.toggle(bleedingIntensity) // now adds
        #expect(sel.series.contains(bleedingIntensity))
        #expect(sel.quantitativeCount == 4)
    }

    // MARK: suppression filter — offered() hides BOTH bleeding series when suppressed

    @Test func suppressionFiltersBleeding() {
        let offeredWhenSuppressed = ExploreSelection.offered(suppressed: true)
        #expect(!offeredWhenSuppressed.contains { $0.id == "bleeding.intensity" })
        #expect(!offeredWhenSuppressed.contains { $0.id == "bleeding.event" })

        let offeredWhenVisible = ExploreSelection.offered(suppressed: false)
        #expect(offeredWhenVisible.contains { $0.id == "bleeding.intensity" })
        #expect(offeredWhenVisible.contains { $0.id == "bleeding.event" })

        // Defense in depth: ONLY the two bleeding ids are removed — everything else stays offered.
        #expect(offeredWhenVisible.count == ExploreSeriesCatalog.all.count)
        #expect(offeredWhenSuppressed.count == ExploreSeriesCatalog.all.count - 2)
    }
}
