// Requirement: DATA-05 / D-12 / T-09-08 (Phase 9) — the import orchestration contract, isolated from
// the real store via SPY closures so the assertions are deterministic and store-free. Proves:
//   (a) a successful import fires the Phase-8 recompute seam EXACTLY ONCE (D-12),
//   (b) an empty/denied import calls through WITHOUT crashing and persists nothing (SC3),
//   (c) the consent guard is honored — the service persists NOTHING when consent is false (T-09-08).
import Testing
import Foundation
import HealthKit
@testable import EndoApp

@Suite("DATA-05 import service: recompute-once, empty-safe, consent-gated")
struct HealthImportServiceTests {

    private func sleepValue(day: Date = Date()) -> ImportedSampleValue {
        ImportedSampleValue(hkUUID: UUID(), day: day, domain: .sleep,
                            payload: .sleep(hours: 7, quality: .good))
    }

    /// A call-counting spy box (final class so the @Sendable closures can mutate it across the actor).
    private final class Spy: @unchecked Sendable {
        var recomputeCalls = 0
        var persistCalls = 0
        var persistedValueCount = 0
    }

    private func makeService(
        reader: FixtureHealthDataReading,
        spy: Spy,
        consent: @escaping @Sendable () -> Bool = { true }
    ) -> HealthImportService {
        HealthImportService(
            reader: reader,
            anchorStore: HealthAnchorStore(kv: InMemoryAnchorStore()),
            persist: { values, _ in
                spy.persistCalls += 1
                spy.persistedValueCount += values.count
            },
            recompute: { spy.recomputeCalls += 1 },
            consentGuard: consent)
    }

    // MARK: - D-12: recompute fires exactly once after a successful import

    @Test func successfulImport_firesRecomputeExactlyOnce() async throws {
        let spy = Spy()
        let reader = FixtureHealthDataReading(.seed(values: [sleepValue()], deleted: [], delta: .none))
        let service = makeService(reader: reader, spy: spy)

        await service.importNew(for: .sleep)

        #expect(spy.recomputeCalls == 1)         // D-12 — the existing seam fires ONCE
        #expect(spy.persistCalls == 1)
        #expect(spy.persistedValueCount == 1)
    }

    // MARK: - SC3: empty/denied import is crash-free and persists nothing of substance

    @Test func emptyImport_callsThroughWithoutCrashingAndPersistsNothing() async throws {
        let spy = Spy()
        let reader = FixtureHealthDataReading(.empty)
        let service = makeService(reader: reader, spy: spy)

        await service.importNew(for: .sleep)   // must not crash

        #expect(spy.persistedValueCount == 0)  // nothing of substance persisted (SC3)
        #expect(spy.recomputeCalls == 1)        // still fires the seam (fingerprint short-circuits downstream)
    }

    // MARK: - T-09-08: consent guard blocks all processing

    @Test func consentDenied_persistsNothingAndDoesNotRecompute() async throws {
        let spy = Spy()
        let reader = FixtureHealthDataReading(.seed(values: [sleepValue()], deleted: [], delta: .none))
        let service = makeService(reader: reader, spy: spy, consent: { false })

        await service.importNew(for: .sleep)

        #expect(spy.persistCalls == 0)      // T-09-08 — never process HK data pre-consent
        #expect(spy.recomputeCalls == 0)
    }

    @Test func requestAuthorization_isNoOpWhenConsentDenied() async throws {
        let spy = Spy()
        let reader = FixtureHealthDataReading(.empty)
        let service = makeService(reader: reader, spy: spy, consent: { false })

        try await service.requestAuthorization(for: .sleep)

        #expect(await reader.authorizationCountForTests() == 0)   // gated out pre-consent
    }
}
