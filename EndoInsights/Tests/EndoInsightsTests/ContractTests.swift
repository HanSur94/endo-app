// EndoInsights — contract guarantees the downstream plans (02, 03) and phases build against.
//
// Covers: the deterministic-id contract (Pitfall 4), DaySnapshot/Insight Equatable + nil-honesty,
// the package-local enum defensive decode (Option D), and the D-12 import-boundary lint
// (belt-and-suspenders on the SC1 compiler boundary).
import Testing
import Foundation
@testable import EndoInsights

@Suite("EndoInsights contract")
struct ContractTests {

    // MARK: Deterministic Insight.id (Pitfall 4)

    @Test func derivedId_isStableForSameInputs() {
        let a = Insight.derived(kind: .trend, window: .last30, signalKey: "symptom.pelvicPain.severity",
                                headline: "h", detail: "d", confidence: .medium, points: [])
        let b = Insight.derived(kind: .trend, window: .last30, signalKey: "symptom.pelvicPain.severity",
                                headline: "different copy", detail: "different", confidence: .low, points: [])
        // Same kind+window+signalKey ⇒ same id, regardless of copy/confidence/points.
        #expect(a.id == b.id)
    }

    @Test func derivedId_differsForDifferentSignals() {
        let a = Insight.derived(kind: .trend, window: .last30, signalKey: "symptom.pelvicPain.severity",
                                headline: "h", detail: "d", confidence: .medium, points: [])
        let bySignal = Insight.derived(kind: .trend, window: .last30, signalKey: "symptom.fatigue.severity",
                                       headline: "h", detail: "d", confidence: .medium, points: [])
        let byWindow = Insight.derived(kind: .trend, window: .last7, signalKey: "symptom.pelvicPain.severity",
                                       headline: "h", detail: "d", confidence: .medium, points: [])
        let byKind = Insight.derived(kind: .correlation, window: .last30, signalKey: "symptom.pelvicPain.severity",
                                     headline: "h", detail: "d", confidence: .medium, points: [])
        #expect(a.id != bySignal.id)
        #expect(a.id != byWindow.id)
        #expect(a.id != byKind.id)
    }

    @Test func derivedId_isWellFormedV5UUID() {
        let i = Insight.derived(kind: .trend, window: .allTime, signalKey: "factor.sleep.poor.frequency",
                                headline: "h", detail: "d", confidence: .high, points: [])
        // RFC-4122 version nibble == 5 (b[6] high nibble), variant bits 10xx (b[8] top two bits).
        let v = i.id.uuid.6 >> 4
        let variant = i.id.uuid.8 >> 6
        #expect(v == 5)
        #expect(variant == 0b10)
    }

    // MARK: Equatable + honesty (absence ≠ 0)

    @Test func insight_isEquatable_includingDeterministicId() {
        let a = Insight.derived(kind: .trend, window: .last90, signalKey: "k",
                                headline: "h", detail: "d", confidence: .low,
                                points: [InsightPoint(date: Date(timeIntervalSince1970: 0), value: 1)])
        let b = Insight.derived(kind: .trend, window: .last90, signalKey: "k",
                                headline: "h", detail: "d", confidence: .low,
                                points: [InsightPoint(date: Date(timeIntervalSince1970: 0), value: 1)])
        #expect(a == b)
    }

    @Test func daySnapshot_carriesNilHonestly_andIsEquatable() {
        let day = Date(timeIntervalSince1970: 1_735_689_600)
        func snap(stress: Int?) -> DaySnapshot {
            DaySnapshot(day: day, symptoms: [], pain: [], flow: nil, clots: false, foodTags: [],
                        activityType: nil, activityMinutes: nil, activityIntensity: nil,
                        sleepHours: nil, sleepQuality: nil, medicationsTaken: [], tookPRN: false,
                        stress: stress, mood: nil, hasNote: false, suppressed: false)
        }
        // nil (unlogged) and 0 (logged zero) are DISTINCT — absence ≠ 0.
        #expect(snap(stress: nil) != snap(stress: 0))
        #expect(snap(stress: 0).stress == 0)
        #expect(snap(stress: nil).stress == nil)
        #expect(snap(stress: 3) == snap(stress: 3))
    }

    @Test func confidence_isComparable() {
        #expect(InsightConfidence.none < .low)
        #expect(InsightConfidence.low < .medium)
        #expect(InsightConfidence.medium < .high)
    }

    // MARK: Package-local enum defensive decode (Option D)

    @Test func enums_decodeDefensively() {
        #expect(SymptomType(raw: "pelvicPain") == .pelvicPain)
        #expect(SymptomType(raw: "garbage") == .other)   // unknown → .other sink
        #expect(SymptomType(raw: nil) == .other)
        #expect(FoodTag(raw: "garbage") == .other)
        #expect(ActivityType(raw: nil) == .other)

        #expect(BodyRegion(raw: "lowerBack") == .lowerBack)
        #expect(BodyRegion(raw: "garbage") == nil)        // unknown → nil (not marked)
        #expect(FlowIntensity(raw: nil) == nil)
        #expect(ActivityIntensity(raw: "garbage") == nil)
        #expect(SleepQuality(raw: "good") == .good)
        #expect(SleepQuality(raw: nil) == nil)
    }

    // MARK: D-12 import-boundary lint (belt-and-suspenders on the SC1 compiler boundary)

    @Test func packageSourcesImportOnlyFoundation() throws {
        // Resolve Sources/EndoInsights relative to this test file:
        // .../EndoInsights/Tests/EndoInsightsTests/ContractTests.swift → .../EndoInsights/
        let packageRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // EndoInsightsTests/
            .deletingLastPathComponent()   // Tests/
            .deletingLastPathComponent()   // EndoInsights/ (package root)
        let sources = packageRoot.appendingPathComponent("Sources/EndoInsights")

        let files = try FileManager.default.contentsOfDirectory(at: sources, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "swift" }
        #expect(!files.isEmpty, "Boundary lint found no package sources to scan at \(sources.path)")

        let forbidden: Set<String> = ["SwiftData", "SwiftUI", "UIKit", "CoreData", "AppKit"]
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for rawLine in text.split(separator: "\n") {
                let line = rawLine.trimmingCharacters(in: .whitespaces)
                guard line.hasPrefix("import ") else { continue }
                let module = line
                    .replacingOccurrences(of: "import ", with: "")
                    .split(separator: " ").first.map(String.init) ?? ""
                #expect(!forbidden.contains(module),
                        "Forbidden import \(module) in \(file.lastPathComponent) — SC1 boundary violated")
            }
        }
    }
}
