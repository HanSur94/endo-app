// Requirement: SCAT-01..04 — the Phase 16 deterministic, gesture-free Scatter-view + deep-link E2E.
//
// Clones the `ExploreUITests` / `InsightsDashboardUITests` harness: each test is ONE launch with
// `-uitest-reset` (the in-memory, fresh-consent store) + a seed arg, and EVERY assertion rides a
// value-independent a11y id — never copy-string scraping of arbitrary chrome. There are NO scrub/drag
// gesture coordinates: the scatter renders from the ephemeral X/Y `@State` the axis-picker Menus set
// (SCAT-01) and the deep-link router prefills (SCAT-04), so the whole flow is driven by id taps.
//
// SEEDS (all DEBUG + `-uitest-reset`-paired → in-memory only; AppDependencies):
//   • `-uitest-seed-scatter-link` (Plan 16-04): a STRONG `stress.high → pelvicPain` QUANTITATIVE
//     correlation (22 days, stress 8 / pain 7 vs stress 2 / pain 2 by parity). The engine gates ONE
//     `.correlation` whose signalKey is `correlation.stress.high.symptom.pelvicPain`; the `ScatterDeepLink`
//     resolver maps it to two quantitative axes (stress 0…10, pelvicPain severity 0…10), so the card's
//     "See the days behind this" scatter-link affordance APPEARS (the POSITIVE deep-link case). The
//     repeated `(8,7)` / `(2,2)` day-pairs also give the scatter a DENSE both-logged cell (count-encoding).
//   • `-uitest-seed-recommendations` (Plan 11-01): a STRONG dairy→pelvicPain correlation. Dairy is
//     presence-only, so the resolver returns nil and the card's scatter-link is HIDDEN — the NEGATIVE,
//     affordance-absent case. Card signalKey: `correlation.food.dairy.symptom.pelvicPain`.
//   • `-uitest-seed-explore` / `-uitest-seed-explore-suppressed` (Plan 15-06): the multi-series Explore
//     fixture (pelvicPain severity + sleep hours + a food event) used for the in-Explore scatter render,
//     count, caption, sparse, and the suppressed-no-bleeding-axis cases.
//
// The a11y ids (16-UI-SPEC §A–E, as implemented):
//   `explore.mode` / `explore.mode.scatter`, `explore.scatter.axisX` / `axisX.<series.id>` /
//   `axisY` / `axisY.<series.id>`, `explore.scatter.plot` / `.caption` / `.summary` / `.empty` /
//   `.sparse`, `insights.card.<signalKey>.scatterLink`, `tab.explore` / `tab.insights`.
//
// Because those ids contain dots and a SwiftUI container/Section id bleeds onto descendants, sub-element
// ids are matched with the EXACT-id equality predicate + `firstMatch` (the same id-bleed-robust workaround
// the Explore + dashboard suites use). The axis-picker Menu OPTION ids
// (`explore.scatter.axisX.<series.id>`) live inside a Menu, so we tap the selector first, then the option.
//
// RESEARCH Pitfall 5 — FLAKY HOST RUNNER. The iPhone-16 simulator can crash the runner mid-launch and
// surface a bare "TEST FAILED" with NO "Test Case … failed" line (a runner crash, NOT an XCTAssert
// failure). On that, re-run the affected method ALONE:
//   xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp \
//     -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/dd \
//     -only-testing:EndoAppUITests/ScatterUITests/<methodName>
// A method that passes in any one clean run is green; a real assertion failure repeats deterministically.
import XCTest

@MainActor
final class ScatterUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Series ids (must match ExploreSeriesCatalog + the resolver mapping)

    private let stressSeriesID = "stress"                       // quantitative 0…10 (the deep-link X axis)
    private let painSeriesID = "symptom.pelvicPain.severity"    // quantitative 0…10 (the deep-link Y axis)

    /// The POSITIVE card's signalKey (the gated stress.high→pelvicPain correlation).
    private let positiveSignalKey = "correlation.stress.high.symptom.pelvicPain"
    /// The NEGATIVE card's signalKey (the dairy→pelvicPain correlation — dairy is presence-only).
    private let negativeSignalKey = "correlation.food.dairy.symptom.pelvicPain"

    // MARK: - id-bleed-robust query (EXACT-id equality + firstMatch — the Explore/dashboard pattern)

    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        let predicate = NSPredicate(format: "identifier == %@", identifier)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// Scroll the Insights dashboard until `identifier` is realized in the a11y tree, then return it. The
    /// correlation card sits below the fold on a populated dashboard, and SwiftUI's `List` lazily realizes
    /// off-screen rows — so a bare `waitForExistence` never instantiates the cell. Mirrors the swipe-up
    /// reveal loop `InsightsDashboardUITests.testRecommendation_rendersAfterCorrelation` uses.
    @discardableResult
    private func revealOnDashboard(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        let target = element(identifier, in: app)
        let dashboard = app.descendants(matching: .any)["insights.screen"].firstMatch
        var revealed = target.waitForExistence(timeout: 15)
        var attempts = 0
        while !revealed && attempts < 8 {
            if dashboard.exists { dashboard.swipeUp() } else { app.swipeUp() }
            revealed = target.waitForExistence(timeout: 3)
            attempts += 1
        }
        return target
    }

    // MARK: - Harnesses (cloned from ExploreUITests / InsightsDashboardUITests)

    /// Launch, grant consent, and open the Explore tab. Returns the launched app on `explore.screen`.
    @discardableResult
    private func launchAndOpenExplore(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        var exploreTab = app.tabBars.buttons["tab.explore"]
        if !exploreTab.waitForExistence(timeout: 10) {
            exploreTab = app.descendants(matching: .any)["tab.explore"].firstMatch
        }
        XCTAssertTrue(exploreTab.waitForExistence(timeout: 10),
                      "A top-level Explore tab (tab.explore) must exist in the consented shell.")
        exploreTab.tap()

        XCTAssertTrue(element("explore.screen", in: app).waitForExistence(timeout: 10),
                      "The Explore screen (explore.screen) must appear after tapping the tab.")
        return app
    }

    /// Launch, grant consent, and open the Insights tab. Returns the launched app on `insights.screen`.
    @discardableResult
    private func launchAndOpenInsights(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()

        let grantButton = app.buttons["consent.grantButton"]
        XCTAssertTrue(grantButton.waitForExistence(timeout: 10),
                      "Consent grant button must exist on a fresh -uitest-reset launch.")
        grantButton.tap()

        XCTAssertTrue(app.buttons["home.logTodayButton"].waitForExistence(timeout: 10),
                      "After consent the Log (Home) tab must be present.")
        var insightsTab = app.tabBars.buttons["tab.insights"]
        if !insightsTab.waitForExistence(timeout: 10) {
            insightsTab = app.descendants(matching: .any)["tab.insights"].firstMatch
        }
        XCTAssertTrue(insightsTab.waitForExistence(timeout: 10),
                      "A top-level Insights tab (tab.insights) must exist in the consented shell.")
        insightsTab.tap()

        XCTAssertTrue(element("insights.screen", in: app).waitForExistence(timeout: 10),
                      "The Insights screen (insights.screen) must appear after tapping the tab.")
        return app
    }

    /// Switch the Explore screen into Scatter mode (the segmented `explore.mode` Picker → `.scatter`).
    private func switchToScatterMode(in app: XCUIApplication) {
        let scatterSegment = element("explore.mode.scatter", in: app)
        XCTAssertTrue(scatterSegment.waitForExistence(timeout: 10),
                      "The Scatter mode segment (explore.mode.scatter) must exist on the Explore screen.")
        scatterSegment.tap()
    }

    /// The catalog `displayName` for each axis series id — the LABEL the Menu option carries. SwiftUI
    /// `Menu` content renders in a separate presentation context where a per-`Button`
    /// `.accessibilityIdentifier` does NOT reliably propagate to the queryable XCUIElement, but the
    /// option's LABEL (its `displayName` text) IS reliably queryable. So the E2E targets a Menu option by
    /// label, not by a synthetic id (the picker UX stays a clean native `Menu`). Kept in lockstep with
    /// `ExploreSeriesCatalog` (`displayName: "\(type.rawValue) severity"` for symptoms; singletons literal).
    private func axisDisplayName(for seriesID: String) -> String {
        switch seriesID {
        case "stress":                       return "Stress"
        case "mood":                         return "Mood"
        case "sleep.hours":                  return "Sleep"
        case "symptom.pelvicPain.severity":  return "pelvicPain severity"
        default:                             return seriesID
        }
    }


    // MARK: - 1a. SCAT-01 — Scatter mode + two quantitative axes → the plot renders (seam-driven)

    func testScatterModeRendersWithTwoAxes() throws {
        // The scatter-link seed logs 22 both-logged stress↔pelvicPain days — well above the sparse floor, so
        // the plot renders. The `-uitest-scatter-axes` seam PRESETS the two quantitative axes (the SAME
        // gesture-free prefill path the deep-link uses), so the scatter renders without driving the native
        // `Menu` axis picker (SCAT-01). (Mixed-scale legibility is the device/manual checkpoint.)
        let app = launchAndOpenExplore(
            ["-uitest-reset", "-uitest-seed-scatter-link",
             "-uitest-scatter-axes", "\(stressSeriesID),\(painSeriesID)"])

        // The count-encoded PointMark scatter renders over the both-logged days (SCAT-01).
        XCTAssertTrue(element("explore.scatter.plot", in: app).waitForExistence(timeout: 10),
                      "With two quantitative axes preset the scatter plot (explore.scatter.plot) must render.")
        XCTAssertFalse(element("explore.scatter.empty", in: app).exists,
                       "With both axes chosen the empty state must be gone.")
    }

    // MARK: - 1b. SCAT-03 — the honest empty state before any axis is chosen

    func testScatterEmptyStateBeforeAxes() throws {
        // Switch to Scatter mode WITHOUT presetting axes → the honest "pick two factors" empty state, NEVER a
        // placeholder plot. No menu driving — a pure mode-toggle + id assertion.
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore"])
        switchToScatterMode(in: app)
        XCTAssertTrue(element("explore.scatter.empty", in: app).waitForExistence(timeout: 10),
                      "With no axes chosen, Scatter mode must show the empty state (explore.scatter.empty).")
        XCTAssertFalse(element("explore.scatter.plot", in: app).exists,
                       "No plot may render before both axes are chosen (no placeholder cloud).")
    }

    // MARK: - 2. SCAT-02 — count-encoded stacking: a dense both-logged cell + worded count parity

    func testScatterCountEncodedDenseCell() throws {
        // The scatter-link seed repeats `(stress 8, pain 7)` (11×) and `(stress 2, pain 2)` (11×), so the
        // scatter has TWO dense cells. Count is encoded by mark SIZE (no jitter) AND restated in the worded
        // a11y summary ("…N days when both were logged") — the structural, value-independent count parity.
        let app = launchAndOpenExplore(
            ["-uitest-reset", "-uitest-seed-scatter-link",
             "-uitest-scatter-axes", "\(stressSeriesID),\(painSeriesID)"])

        XCTAssertTrue(element("explore.scatter.plot", in: app).waitForExistence(timeout: 10),
                      "The dense stress↔pain scatter (explore.scatter.plot) must render (SCAT-02).")

        // The worded summary carries the both-logged DAY count (22 from this seed) — the honest, non-hue
        // count signal a VoiceOver / grayscale user reads. "22" proves the dense cells were counted, not
        // jittered into 22 separate fractional points.
        let summary = element("explore.scatter.summary", in: app)
        XCTAssertTrue(summary.waitForExistence(timeout: 10),
                      "The worded text-summary (explore.scatter.summary) must accompany the plot (SCAT-02/03).")
        let summaryText = (summary.label.isEmpty ? (summary.value as? String ?? "") : summary.label)
        XCTAssertTrue(summaryText.contains("22"),
                      "The summary must state the both-logged day count '22' (count-encoded, not jittered) — "
                      + "got: '\(summaryText)'.")
    }

    // MARK: - 3a. SCAT-03 — neutral caption present, NO fitted-line / r-value / % chrome

    func testScatterCaptionIsNeutral() throws {
        let app = launchAndOpenExplore(
            ["-uitest-reset", "-uitest-seed-scatter-link",
             "-uitest-scatter-axes", "\(stressSeriesID),\(painSeriesID)"])

        let caption = element("explore.scatter.caption", in: app)
        XCTAssertTrue(caption.waitForExistence(timeout: 10),
                      "The neutral caption (explore.scatter.caption) must be present beneath the plot (SCAT-03).")
        let captionText = caption.label.isEmpty ? (caption.value as? String ?? "") : caption.label
        // The LOCKED neutral form: "Based on {N} days when both were logged." — a factual tally only.
        XCTAssertTrue(captionText.localizedCaseInsensitiveContains("when both were logged"),
                      "The caption must be the neutral both-logged form (got: '\(captionText)').")
        // Honesty contract: NO statistic chrome leaks into the caption — no r-value, no %, no p-value.
        XCTAssertFalse(captionText.contains("%"),
                       "The neutral caption must carry NO percentage (got: '\(captionText)').")
        XCTAssertFalse(captionText.localizedCaseInsensitiveContains("r ="),
                       "The neutral caption must carry NO r-value (got: '\(captionText)').")
        XCTAssertFalse(captionText.localizedCaseInsensitiveContains("correlation"),
                       "The descriptive scatter caption must NOT restate 'correlation' (got: '\(captionText)').")
    }

    // MARK: - 3b. SCAT-03 — honest sparse state when too few overlapping days

    func testSparseStateWhenTooFewDays() throws {
        // Choose X = pelvicPain severity (logged) and Y = activity-derived… instead, choose a quantitative
        // pair where the BOTH-logged overlap is below the sparse threshold (minScatterDays == 5). In the
        // Explore seed, sleep is a deliberate GAP on the scrub day and logged only on a few in-window days,
        // and pelvicPain has its own gap — their both-logged overlap is small. We assert the honest sparse
        // state appears, NEVER a near-empty plot read as "no association".
        // Stress is NOT logged at all by the Explore seed → ANY pair with stress has zero both-logged days
        // → the honest sparse state (a quantitative axis with no overlap), never a fabricated point. The seam
        // presets (pelvicPain, stress) so no menu driving is needed.
        let app = launchAndOpenExplore(
            ["-uitest-reset", "-uitest-seed-explore",
             "-uitest-scatter-axes", "\(painSeriesID),\(stressSeriesID)"])

        XCTAssertTrue(element("explore.scatter.sparse", in: app).waitForExistence(timeout: 10),
                      "Two axes with too few BOTH-logged days must show the sparse state (explore.scatter.sparse).")
        XCTAssertFalse(element("explore.scatter.plot", in: app).exists,
                       "The sparse state must render NO plot (never a near-empty cloud read as 'no association').")
    }

    // MARK: - 4. SCAT-01 — a suppressed user is offered NO bleeding axis (proven by ABSENCE)

    func testSuppressedUserHasNoBleedingAxis() throws {
        // The suppressed Explore seed flips the latest TreatmentState to "suppressed"; the quantitative axis
        // filter then offers NO bleeding.intensity option (T-16-07 — defense-in-depth alongside the Phase-14
        // accessor gate). Proven by the option's ABSENCE in BOTH axis selectors.
        let app = launchAndOpenExplore(["-uitest-reset", "-uitest-seed-explore-suppressed"])
        switchToScatterMode(in: app)

        // Open the X axis Menu. Its OPTIONS are queried by LABEL (the catalog `displayName`) — a per-option
        // `.accessibilityIdentifier` does NOT reliably propagate through a SwiftUI `Menu`, but the option's
        // visible label does. A single menu open keeps the interaction deterministic.
        let axisX = element("explore.scatter.axisX", in: app)
        XCTAssertTrue(axisX.waitForExistence(timeout: 10),
                      "The X axis selector must render for the suppressed user.")
        axisX.tap()
        let painOption = app.buttons.matching(
            NSPredicate(format: "label == %@", axisDisplayName(for: painSeriesID))).firstMatch
        XCTAssertTrue(painOption.waitForExistence(timeout: 10),
                      "A non-bleeding quantitative option (pelvicPain severity) must still be offered "
                      + "(the axis menu is populated, not blank).")
        // …but the bleeding axis option is ABSENT (the suppression filter removed it). "Bleeding intensity"
        // is the bleeding.intensity displayName; it must not appear as a selectable Menu option.
        let bleedingOption = app.buttons.matching(NSPredicate(format: "label == %@", "Bleeding intensity"))
        XCTAssertEqual(bleedingOption.count, 0,
                       "A suppressed user must NOT be offered bleeding.intensity as an axis (T-16-07).")
    }

    // MARK: - 5. SCAT-04 POSITIVE — the correlation card deep-link prefills the scatter

    func testCorrelationCardDeepLinkPrefillsScatter() throws {
        // The scatter-link seed gates a `.correlation` card whose signalKey resolves to two quantitative
        // axes, so the card shows the "See the days behind this" scatter-link. Tapping it sets the router
        // payload + switches to the Explore tab in Scatter mode, prefilled with stress (X) + pelvicPain (Y).
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-scatter-link"])

        // The correlation card sits below the fold — scroll its evidence line into view first (proving the
        // card rendered), then assert the scatter-link affordance hangs off the same revealed card.
        let evidence = revealOnDashboard("insights.card.\(positiveSignalKey).evidence", in: app)
        XCTAssertTrue(evidence.exists,
                      "The stress→pain correlation card must render (insights.card.\(positiveSignalKey).evidence).")
        // The POSITIVE card's scatter-link affordance must exist (the resolver mapped the signalKey). It
        // carries its OWN id now that the `.evidence` id is scoped to the text-only block (it no longer
        // masks this borderless button's id).
        let scatterLink = element("insights.card.\(positiveSignalKey).scatterLink", in: app)
        XCTAssertTrue(scatterLink.waitForExistence(timeout: 5),
                      "The stress→pain correlation card must show the scatter-link "
                      + "(insights.card.\(positiveSignalKey).scatterLink) — the POSITIVE deep-link case (SCAT-04).")
        scatterLink.tap()

        // The deep-link lands on the Explore screen in Scatter mode with the plot prefilled + rendered.
        XCTAssertTrue(element("explore.screen", in: app).waitForExistence(timeout: 10),
                      "Tapping the scatter-link must land on the Explore screen (explore.screen).")
        // The mode segment is now Scatter (the router set mode = .scatter); its segment is present.
        XCTAssertTrue(element("explore.mode.scatter", in: app).waitForExistence(timeout: 10),
                      "The deep-link must put Explore in Scatter mode (explore.mode.scatter present).")
        // The prefilled stress↔pain scatter renders WITHOUT the user touching either axis picker.
        XCTAssertTrue(element("explore.scatter.plot", in: app).waitForExistence(timeout: 10),
                      "The deep-link must PREFILL the two axes so the scatter plot renders immediately "
                      + "(explore.scatter.plot) — the SCAT-04 prefill proof.")
        // The empty state must be gone (the axes were prefilled, not left unchosen).
        XCTAssertFalse(element("explore.scatter.empty", in: app).exists,
                       "The prefilled scatter must NOT show the 'pick two factors' empty state.")
    }

    // MARK: - 6. SCAT-04 NEGATIVE — a presence-only (dairy) correlation card has NO scatter-link

    func testPresenceFactorCardHasNoScatterLink() throws {
        // The recommendations seed gates a dairy→pelvicPain `.correlation` card. Dairy is presence-only, so
        // the resolver returns nil and the scatter-link is HIDDEN — proven by the affordance's ABSENCE.
        let app = launchAndOpenInsights(["-uitest-reset", "-uitest-seed-recommendations"])

        // The dairy correlation card itself must render (so the absence below is a HIDDEN affordance, not a
        // missing card). The card-root id is masked by descendant cells, so detect a stable sub-element
        // (the evidence line) that hangs off the same idRoot.
        let dairyEvidence = revealOnDashboard("insights.card.\(negativeSignalKey).evidence", in: app)
        XCTAssertTrue(dairyEvidence.exists,
                      "The dairy→pain correlation card must render (insights.card.\(negativeSignalKey).evidence) "
                      + "— so the missing scatter-link below is a HIDDEN affordance, not an absent card.")
        // The scatter-link must NOT exist on the presence-only card (the resolver returned nil).
        XCTAssertFalse(element("insights.card.\(negativeSignalKey).scatterLink", in: app).exists,
                       "A presence-only (dairy) correlation card must have NO scatter-link "
                       + "(insights.card.\(negativeSignalKey).scatterLink absent) — the NEGATIVE case (SCAT-04).")
    }
}
