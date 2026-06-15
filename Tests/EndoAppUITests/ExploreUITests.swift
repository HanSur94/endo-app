// Requirement: EXPL-01..07 — the Phase 15 deterministic, gesture-free Explore-tab E2E.
//
// Clones the `ChartScrubUITests` / `InsightsDashboardUITests` harness: each test is ONE launch with
// `["-uitest-reset", "-uitest-seed-explore", "-uitest-select-day", <yyyy-MM-dd>]` (the suppression case
// swaps in `-uitest-seed-explore-suppressed`). The DEBUG-only, `-uitest-reset`-paired seam
// (AppDependencies → `\.uiTestScrubDay` → ExploreLanesView `.task(id:)`) sets a deterministic scrub
// selection once the lanes render, so the multi-series tooltip reads with ZERO gesture coordinates, ZERO
// sleeps (RESEARCH Pitfall 4). EVERY assertion rides a11y ids + `element.value` — never copy-string
// scraping of arbitrary chrome (the a11y ids are the stable contract).
//
// The `-uitest-seed-explore` fixture (AppDependencies.seedExploreFixture), all inside the last-7 window
// (the "all anomalies in last-7" rule so every range preset resolves the SAME state — RESEARCH Pitfall 6):
//   • `symptom.pelvicPain.severity` (a 0…10 quantitative lane): offset 3 is the GAP day (never logged →
//     "Not logged"), offset 2 is the logged-ZERO day (severity 0 → a real point), every other in-window
//     day is the dense severity-6 spine. offset 1 (today-1) is the seeded scrub day (severity 6).
//   • `sleep.hours` (a SECOND quantitative lane on a DIFFERENT scale): logged on the other in-window days
//     but a deliberate GAP on offset 1 — so the seeded scrub day shows sleep's per-series "Not logged"
//     line ALONGSIDE pelvicPain's real readout (the multi-series gap-honest proof, EXPL-05).
//   • `food.dairy` (a presence/EVENT lane): present on offset 1 — the scrub day also carries an event line
//     and the event lane renders ticks, never a 0/1 line (EXPL-04).
//
// The series ids drive the chip / lane / tooltip ids: e.g. `explore.chip.symptom.pelvicPain.severity`,
// `explore.lane.sleep.hours`, `explore.lane.food.dairy.event`,
// `explore.scrub.tooltip.symptom.pelvicPain.severity`, `…sleep.hours.notLogged`. Because those ids contain
// dots and a SwiftUI container/Section id bleeds onto descendants, sub-element ids are matched with the
// ENDSWITH-predicate + `firstMatch` pattern (the same id-bleed-robust workaround the dashboard suite uses).
import XCTest

@MainActor
final class ExploreUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Series ids the seed fixture populates (must match ExploreSeriesCatalog + seedExploreFixture)

    private let painSeriesID = "symptom.pelvicPain.severity"   // quantitative, 0…10
    private let sleepSeriesID = "sleep.hours"                  // quantitative, a different scale (hours)
    private let dairySeriesID = "food.dairy"                   // presence/event

    // MARK: - Harness (cloned from ChartScrubUITests.launchAndOpenInsights)

    /// Launch, grant consent, and open the Explore tab. Returns the launched app on the Explore screen.
    /// EXPL-01: the Explore tab is the consent-gated third tab — it appears only after consent.
    @discardableResult
    private func launchAndOpenExplore(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        // Grant consent (the consented shell — and therefore the Explore tab — is built ONLY in the
        // consented branch; the tab is structurally unreachable before opt-in, T-15-10 / EXPL-01).
        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        // Confirm we landed on the consented shell, then switch to the Explore tab. The tab-bar button
        // carries the `tab.explore` id (set on the tab-item Label). Query the tab bar first, then any
        // descendant, so the lookup is robust across XCUITest's tab-bar element model.
        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        var exploreTab = app.tabBars.buttons["tab.explore"]
        if !exploreTab.waitForExistence(timeout: 10) {
            exploreTab = app.descendants(matching: .any)["tab.explore"].firstMatch
        }
        XCTAssertTrue(exploreTab.waitForExistence(timeout: 10),
                      "A top-level Explore tab (tab.explore) must exist in the consented shell (EXPL-01).")
        exploreTab.tap()

        XCTAssertTrue(app.descendants(matching: .any)["explore.screen"].waitForExistence(timeout: 10),
                      "The Explore screen (explore.screen) must appear after tapping the tab (EXPL-01).")
        return app
    }

    /// The `yyyy-MM-dd` launch-arg string for `today - daysAgo`, computed IMMEDIATELY before
    /// `app.launch()` (same midnight-race class as every existing today-relative seed — accepted).
    private func dayArg(daysAgo: Int) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let day = Calendar.current.date(byAdding: .day, value: -daysAgo,
                                        to: Calendar.current.startOfDay(for: Date()))!
        return formatter.string(from: day)
    }

    // MARK: - id-bleed-robust queries (ENDSWITH + firstMatch — the dashboard-suite pattern)

    /// A chip by its series id (`explore.chip.<series.id>`). The chip is a top-level interactive Button,
    /// so the exact-id query resolves it directly.
    private func chip(_ seriesID: String, in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier == %@", "explore.chip.\(seriesID)")
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// A sub-element id (lane / tooltip line) matched by an EXACT-id ENDSWITH-safe query. A SwiftUI
    /// container id can bleed onto descendants, so we match the full id via `firstMatch` on a precise
    /// equality predicate (robust to the multiple-match that a plain subscript would throw on).
    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier == %@", identifier)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// Toggle the three seeded series (pain + sleep + dairy) into the selection so the lanes render and the
    /// `.task(id: uiTestScrubDay)` seam fires its deterministic scrub. Each chip is a Button; tapping it
    /// toggles the series IN.
    private func selectSeededSeries(in app: XCUIApplication) {
        for id in [painSeriesID, sleepSeriesID, dairySeriesID] {
            let c = chip(id, in: app)
            XCTAssertTrue(c.waitForExistence(timeout: 10),
                          "The seeded chip explore.chip.\(id) must be offered in the picker (EXPL-02).")
            c.tap()
        }
    }

    // MARK: - 1. EXPL-01 — the Explore tab appears after consent and shows the screen

    func testExplore_tabAppearsAfterConsent_showsScreen() throws {
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        // The picker is part of the screen — its presence confirms the Explore surface rendered.
        XCTAssertTrue(element("explore.picker", in: app).waitForExistence(timeout: 10),
                      "The Explore screen must render its series picker (explore.picker) — EXPL-01/EXPL-02.")
        // The persistent not-medical-advice disclaimer is always visible on the Explore screen.
        XCTAssertTrue(element("explore.disclaimer", in: app).exists,
                      "The not-medical-advice disclaimer must be visible on the Explore screen.")
    }

    // MARK: - 2. EXPL-07 — empty state ("pick a series") before any selection

    func testExplore_emptyState_beforeAnySelection() throws {
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        // With NO chip selected (the default on first open), the warm "pick a series" empty state shows in
        // place of the lanes — never a fake/placeholder lane (EXPL-07).
        XCTAssertTrue(element("explore.empty", in: app).waitForExistence(timeout: 10),
                      "With nothing selected the Explore screen must show the empty state (explore.empty).")
        // No lanes render in the empty state.
        XCTAssertFalse(element("explore.lanes", in: app).exists,
                       "The empty state must render NO lanes (no placeholder/empty axes).")
    }

    // MARK: - 3. EXPL-02 — chips toggle, the cap disables a further quantitative chip, capHint shows

    func testExplore_chipsToggle_capDisablesFurtherQuantitative() throws {
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])

        // Toggle ONE quantitative chip IN → its lane appears (a chip toggle adds a lane, EXPL-02/EXPL-03).
        let painChip = chip(painSeriesID, in: app)
        XCTAssertTrue(painChip.waitForExistence(timeout: 10), "The pelvicPain chip must be offered.")
        painChip.tap()
        XCTAssertTrue(element("explore.lane.\(painSeriesID)", in: app).waitForExistence(timeout: 10),
                      "Toggling a quantitative chip ON must add its lane (explore.lane.\(painSeriesID)).")

        // Toggle it OFF again → the empty state returns (toggle removes the lane).
        chip(painSeriesID, in: app).tap()
        XCTAssertTrue(element("explore.empty", in: app).waitForExistence(timeout: 10),
                      "Toggling the only series OFF must return the empty state (the toggle removed the lane).")

        // Now fill the 4-quantitative cap: pelvicPain severity + sleep + stress + mood are four distinct
        // quantitative series in the catalog. After four are selected, a FIFTH unselected quantitative chip
        // (activity.minutes) must be DISABLED and the cap-hint must show (EXPL-02).
        for id in [painSeriesID, sleepSeriesID, "stress", "mood"] {
            let c = chip(id, in: app)
            XCTAssertTrue(c.waitForExistence(timeout: 10), "The quantitative chip \(id) must be offered.")
            c.tap()
        }
        XCTAssertTrue(element("explore.picker.capHint", in: app).waitForExistence(timeout: 10),
                      "At the 4-quantitative cap the remove-to-add helper (explore.picker.capHint) must show.")
        let fifthQuant = chip("activity.minutes", in: app)
        XCTAssertTrue(fifthQuant.waitForExistence(timeout: 10),
                      "A fifth unselected quantitative chip (activity.minutes) must still exist in the picker.")
        XCTAssertFalse(fifthQuant.isEnabled,
                       "At the cap a fifth unselected quantitative chip must be DISABLED (the cap rule).")
    }

    // MARK: - 4. EXPL-02 — a suppressed user is offered NO bleeding chip (proven by ABSENCE)

    func testExplore_suppressed_hidesBleedingChips() throws {
        // The suppressed seed flips the latest TreatmentState to "suppressed"; the picker filter then offers
        // NO bleeding chip at all (T-15-11 — the UI half of the Phase-14 accessor gate). Proven by absence.
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore-suppressed",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        XCTAssertTrue(element("explore.picker", in: app).waitForExistence(timeout: 10),
                      "The picker must render for the suppressed user.")
        // A non-bleeding chip (the pelvicPain severity series) IS still offered — proving the picker rendered
        // its chips and the bleeding absence below is a FILTER, not a blank picker.
        XCTAssertTrue(chip(painSeriesID, in: app).waitForExistence(timeout: 10),
                      "A non-bleeding chip must still be offered to the suppressed user (the picker is populated).")
        // The bleeding chips must NOT exist (the suppression filter removed both).
        XCTAssertFalse(chip("bleeding.intensity", in: app).exists,
                       "A suppressed user must NOT be offered the bleeding.intensity chip (T-15-11).")
        XCTAssertFalse(chip("bleeding.event", in: app).exists,
                       "A suppressed user must NOT be offered the bleeding.event chip (T-15-11).")
    }

    // MARK: - 5. EXPL-03/04 — per-scale quantitative lanes + an event lane render

    func testExplore_lanesRender_perScaleQuantitative_andEvent() throws {
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        selectSeededSeries(in: app)

        // EXPL-03: each selected QUANTITATIVE series gets its own per-scale lane.
        XCTAssertTrue(element("explore.lane.\(painSeriesID)", in: app).waitForExistence(timeout: 10),
                      "The pelvicPain quantitative lane (explore.lane.\(painSeriesID)) must render (EXPL-03).")
        XCTAssertTrue(element("explore.lane.\(sleepSeriesID)", in: app).waitForExistence(timeout: 10),
                      "The sleep quantitative lane on a DIFFERENT scale (explore.lane.\(sleepSeriesID)) must "
                      + "render — proving per-scale lanes, not one shared axis (EXPL-03).")
        // EXPL-04: the event series renders as an EVENT lane (…​.event), never a quantitative line.
        XCTAssertTrue(element("explore.lane.\(dairySeriesID).event", in: app).waitForExistence(timeout: 10),
                      "The food.dairy series must render as an EVENT lane (explore.lane.\(dairySeriesID).event) "
                      + "— ticks, never a 0/1 line (EXPL-04).")
    }

    // MARK: - 6. EXPL-05 — the seam-set scrub day: real-unit readout + a per-series gap "Not logged"

    func testExplore_scrubTooltip_readsRealValue_andGapNotLogged() throws {
        // The seed's scrub day is offset 1 (today-1): pelvicPain LOGGED severity 6, sleep a GAP, dairy present.
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        selectSeededSeries(in: app)

        // The lanes must render so the `.task(id: uiTestScrubDay)` seam fires its deterministic selection.
        XCTAssertTrue(element("explore.lanes", in: app).waitForExistence(timeout: 10),
                      "The lane stack (explore.lanes) must render so the scrub seam can fire (EXPL-05).")

        // The shared multi-series tooltip element appears (the seam set a selection — no gesture).
        XCTAssertTrue(element("explore.scrub.tooltip", in: app).waitForExistence(timeout: 10),
                      "The seam-selected day must render the shared multi-series tooltip (explore.scrub.tooltip).")

        // EXPL-05a — pelvicPain LOGGED on the scrub day: its per-series line carries the real-unit readout
        // (a LOGGED series uses the plain `explore.scrub.tooltip.<id>` marker, NOT the .notLogged suffix).
        let painLine = element("explore.scrub.tooltip.\(painSeriesID)", in: app)
        XCTAssertTrue(painLine.waitForExistence(timeout: 10),
                      "The LOGGED pelvicPain series must carry a value line (explore.scrub.tooltip.\(painSeriesID)).")
        let painReadout = painLine.value as? String ?? ""
        XCTAssertTrue(painReadout.contains("6 of 10"),
                      "The pelvicPain readout must read its real-unit value '6 of 10' (got: '\(painReadout)').")
        XCTAssertTrue(painReadout.contains("pelvicPain") || painReadout.contains("severity"),
                      "The pelvicPain readout must name the series (got: '\(painReadout)').")

        // EXPL-05b — sleep is a GAP on the scrub day: its per-series line carries the explicit .notLogged
        // marker and reads "Not logged", NEVER a fabricated value (absence ≠ 0).
        let sleepGapMarker = element("explore.scrub.tooltip.\(sleepSeriesID).notLogged", in: app)
        XCTAssertTrue(sleepGapMarker.waitForExistence(timeout: 10),
                      "The GAP sleep series must carry the explicit .notLogged marker "
                      + "(explore.scrub.tooltip.\(sleepSeriesID).notLogged) — the gap is information (EXPL-05).")
        let sleepReadout = sleepGapMarker.value as? String ?? ""
        XCTAssertTrue(sleepReadout.contains("Not logged"),
                      "A gap day's per-series sleep line must say 'Not logged' (got: '\(sleepReadout)').")
        XCTAssertFalse(sleepReadout.contains(" h"),
                       "A gap sleep day must never read an hours value (got: '\(sleepReadout)').")

        // The LOGGED pelvicPain series must NOT carry the gap marker (the inverse honesty check).
        XCTAssertFalse(element("explore.scrub.tooltip.\(painSeriesID).notLogged", in: app).exists,
                       "A LOGGED series must NOT show the .notLogged gap marker (absence ≠ a logged value).")
    }

    // MARK: - 7. EXPL-06 — switching the range preset re-renders the lanes

    func testExplore_rangePreset_switchesWindow() throws {
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        selectSeededSeries(in: app)
        XCTAssertTrue(element("explore.lane.\(painSeriesID)", in: app).waitForExistence(timeout: 10),
                      "The lane must render before switching the range (EXPL-06).")

        // Switch from the default (last 30) to the last-7 preset. The seeded fixture is all inside the last-7
        // window, so the lanes still render after the switch (the re-anchored shared X domain).
        let last7 = element("explore.range.last7", in: app)
        XCTAssertTrue(last7.waitForExistence(timeout: 10),
                      "The last-7 range preset (explore.range.last7) must exist (the dashboard vocabulary).")
        last7.tap()
        XCTAssertTrue(element("explore.lane.\(painSeriesID)", in: app).waitForExistence(timeout: 10),
                      "After switching to the last-7 preset the lanes must still render (EXPL-06).")
    }

    // MARK: - 8. EXPL-07 — sparse state ("not enough data") for a too-sparse selection

    func testExplore_sparseState_forTooSparseSelection() throws {
        // Select ONLY the activity series — the seed logs NO activity at all, so the selection has fewer than
        // the sparse threshold of logged points in the window → the honest "not enough data" state shows,
        // NEVER a blank lane read as "0" (EXPL-07).
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore",
                                        "-uitest-select-day", dayArg(daysAgo: 1)])
        let activityChip = chip("activity.minutes", in: app)
        XCTAssertTrue(activityChip.waitForExistence(timeout: 10),
                      "The activity chip must be offered so we can select an unlogged (sparse) series.")
        activityChip.tap()
        XCTAssertTrue(element("explore.sparse", in: app).waitForExistence(timeout: 10),
                      "A selection with too few logged days must show the sparse state (explore.sparse) — "
                      + "an honest signal, never a blank lane read as 0 (EXPL-07).")
        XCTAssertFalse(element("explore.lanes", in: app).exists,
                       "The sparse state must render NO lanes (no fabricated/empty axes).")
    }
}
