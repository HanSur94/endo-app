// SCAT-04 unit suite — proves the pure `ScatterDeepLink` resolver maps every quantitative-factor
// correlation signalKey to a typed axis pair, and returns `nil` for every presence / sleep-quality /
// suppressed-bleeding / malformed case. WITHOUT rendering. Swift Testing (`@Suite`/`@Test`/`#expect`;
// mirrors the `ExploreSelectionTests` shape), app target, separate from the XCUITest target.
//
// HONESTY: the resolver INPUT is the STRUCTURAL `signalKey` (a machine key), NEVER audited prose — these
// tests feed it `correlation.<factor.key>.symptom.<rawValue>` / `correlation.<factor.key>.bleeding` keys
// exactly as `CorrelationEngine` emits them. Every expected `ExploreSeries` is asserted by its catalog id
// + `.isQuantitative`, so the suite can never silently drift from the real catalog.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Scatter deep-link resolver (SCAT-04 signalKey → optional quantitative axis pair)")
struct ScatterDeepLinkTests {

    // MARK: - Positive mappings (quantitative factor × quantitative target → typed pair)

    @Test("stress.high → pelvicPain resolves to (stress, symptom.pelvicPain.severity), both quantitative")
    func stressHighToSymptomSeverity() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.stress.high.symptom.pelvicPain", suppressed: false)
        #expect(pair != nil)
        #expect(pair?.x.id == "stress")
        #expect(pair?.y.id == "symptom.pelvicPain.severity")
        #expect(pair?.x.scaleKind.isQuantitative == true)
        #expect(pair?.y.scaleKind.isQuantitative == true)
    }

    @Test("mood.low → fatigue resolves to (mood, symptom.fatigue.severity)")
    func moodLowToSymptomSeverity() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.mood.low.symptom.fatigue", suppressed: false)
        #expect(pair?.x.id == "mood")
        #expect(pair?.y.id == "symptom.fatigue.severity")
        #expect(pair?.x.scaleKind.isQuantitative == true)
        #expect(pair?.y.scaleKind.isQuantitative == true)
    }

    @Test("stress.high → bleeding resolves to (stress, bleeding.intensity) when NOT suppressed")
    func stressHighToBleedingUnsuppressed() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.stress.high.bleeding", suppressed: false)
        #expect(pair?.x.id == "stress")
        #expect(pair?.y.id == "bleeding.intensity")
        #expect(pair?.y.scaleKind.isQuantitative == true)
    }

    @Test("mood.low → bleeding resolves to (mood, bleeding.intensity) when NOT suppressed")
    func moodLowToBleedingUnsuppressed() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.mood.low.bleeding", suppressed: false)
        #expect(pair?.x.id == "mood")
        #expect(pair?.y.id == "bleeding.intensity")
    }

    @Test("every SymptomType target resolves under stress.high (catalog parity)")
    func everySymptomTargetResolves() {
        // Qualify EndoInsights.SymptomType: the app target also defines a `SymptomType` (same rawValues),
        // so the unqualified name is ambiguous under `@testable import EndoApp`. The resolver + catalog key
        // on the EndoInsights enum, so iterate it.
        for type in EndoInsights.SymptomType.allCases {
            let pair = ScatterDeepLink.resolve(
                signalKey: "correlation.stress.high.symptom.\(type.rawValue)", suppressed: false)
            #expect(pair?.x.id == "stress",
                    "stress.high → symptom.\(type.rawValue) should resolve X=stress")
            #expect(pair?.y.id == "symptom.\(type.rawValue).severity",
                    "stress.high → symptom.\(type.rawValue) should resolve Y=symptom.\(type.rawValue).severity")
        }
    }

    // MARK: - Suppression gate (a bleeding target under suppression → nil; threat T-16-02)

    @Test("stress.high → bleeding resolves to nil under suppression")
    func bleedingTargetSuppressedIsNil() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.stress.high.bleeding", suppressed: true)
        #expect(pair == nil)
    }

    @Test("a symptom target is UNAFFECTED by suppression (only bleeding is gated)")
    func symptomTargetSurvivesSuppression() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.stress.high.symptom.pelvicPain", suppressed: true)
        #expect(pair?.x.id == "stress")
        #expect(pair?.y.id == "symptom.pelvicPain.severity")
    }

    // MARK: - Presence-only / sleep-quality factors → nil (HIDE affordance; RESOLVED-Q1)

    @Test("sleep.poor → nil (no quality→hours bridge in v1.1)")
    func sleepPoorIsNil() {
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.sleep.poor.symptom.pelvicPain", suppressed: false) == nil)
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.sleep.poor.bleeding", suppressed: false) == nil)
    }

    @Test("a food (presence-only) factor → nil")
    func foodFactorIsNil() {
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.food.dairy.symptom.pelvicPain", suppressed: false) == nil)
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.food.caffeine.symptom.fatigue", suppressed: false) == nil)
    }

    @Test("activity / medication / prn / cyclePhase presence factors → nil")
    func otherPresenceFactorsAreNil() {
        let presenceKeys = [
            "correlation.activity.present.symptom.pelvicPain",
            "correlation.activity.cardio.symptom.pelvicPain",
            "correlation.medication.taken.symptom.pelvicPain",
            "correlation.prn.taken.symptom.pelvicPain",
            "correlation.cyclePhase.menstrual.symptom.pelvicPain",
            "correlation.cyclePhase.luteal.bleeding",
        ]
        for key in presenceKeys {
            #expect(ScatterDeepLink.resolve(signalKey: key, suppressed: false) == nil,
                    "presence factor key \"\(key)\" must resolve to nil (affordance hidden)")
        }
    }

    // MARK: - Malformed / unrecognized keys → nil, never a crash (defensive parse; threat T-16-03)

    @Test("a non-correlation key → nil")
    func nonCorrelationKeyIsNil() {
        #expect(ScatterDeepLink.resolve(signalKey: "flare.pelvicPain", suppressed: false) == nil)
        #expect(ScatterDeepLink.resolve(signalKey: "cyclePhase.menstrual", suppressed: false) == nil)
    }

    @Test("garbage / empty / structurally-broken keys → nil without crashing")
    func garbageKeysAreNil() {
        let garbage = [
            "",
            "garbage",
            "correlation.",
            "correlation.stress.high",                       // no target fragment
            "correlation.stress.high.symptom.",              // empty rawValue
            "correlation.stress.high.symptom.notARealSymptom", // unknown rawValue
            "correlation..bleeding",                          // empty factor.key
            "correlation.bleeding",                           // bleeding with no factor.key
            "correlation.stress.high.heartrate",             // unknown target fragment
            "....",
        ]
        for key in garbage {
            #expect(ScatterDeepLink.resolve(signalKey: key, suppressed: false) == nil,
                    "malformed key \"\(key)\" must resolve to nil, never crash")
        }
    }

    @Test("an unknown quantitative-looking factor still resolves to nil (only stress.high/mood.low map)")
    func unknownFactorIsNil() {
        // A target that DOES map, but a factor that does NOT — proves the factor gate is the binding one.
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.stress.low.symptom.pelvicPain", suppressed: false) == nil)
        #expect(ScatterDeepLink.resolve(
            signalKey: "correlation.mood.high.symptom.pelvicPain", suppressed: false) == nil)
    }

    // MARK: - Catalog-lookup invariant (a non-nil result is ALWAYS a real catalogued series)

    @Test("a non-nil pair's series both exist in the shipped catalog")
    func resolvedSeriesAreCatalogued() {
        let pair = ScatterDeepLink.resolve(
            signalKey: "correlation.stress.high.symptom.pelvicPain", suppressed: false)
        let x = try? #require(pair?.x)
        let y = try? #require(pair?.y)
        #expect(ExploreSeriesCatalog.all.contains(where: { $0.id == x?.id }))
        #expect(ExploreSeriesCatalog.all.contains(where: { $0.id == y?.id }))
    }
}
