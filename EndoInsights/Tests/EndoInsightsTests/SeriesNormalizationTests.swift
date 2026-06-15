// EndoInsights — the unit-pinned honesty invariants for series normalization (Phase 14, Plan 04).
//
// These tests lock the FIXED-honest-domain + real-unit-readout contract that Phase 15 composes on
// to make the deferred lanes-vs-labeled-normalization call WITHOUT silently misleading the user:
//   • Every ScaleKind has a FIXED domain (0…10, 1…5, 1…4, 0…24, 0…240) — never a per-visible-window
//     min/max (Pitfall 4; per-window is a Phase-15 presentation decision, out of scope here).
//   • Presence has NO numeric domain (nil) — an event lane is not a normalized value (Pitfall 1).
//   • normalize → denormalize round-trips to the original value within float tolerance.
//   • The readout ALWAYS reports the REAL value + unit, never the normalized [0,1] number (honesty;
//     T-14-08). The readout never contains the normalized form — asserted explicitly.
//
// Same UTC-fixed style as ChartScrubSelectionTests; though these value-only tests need no DaySnapshot,
// they share the Plan-01 SeriesTestSupport harness target for consistency.
import Testing
import Foundation
@testable import EndoInsights

@Suite("Series normalization (fixed honest domain; round-trip; real-unit readout)")
struct SeriesNormalizationTests {

    @Test func domainPerKindIsFixedAndHonest() {
        #expect(SeriesNormalization.domain(for: .ordinal0to10) == 0...10)
        #expect(SeriesNormalization.domain(for: .ordinal1to5) == 1...5)
        #expect(SeriesNormalization.domain(for: .ordinalBleeding) == 1...4)
        #expect(SeriesNormalization.domain(for: .continuousHours) == 0...24)
        #expect(SeriesNormalization.domain(for: .continuousMinutes) == 0...240)
    }

    @Test func presenceKindHasNoDomain() {
        #expect(SeriesNormalization.domain(for: .presenceEvent) == nil)
        #expect(SeriesNormalization.normalize(1, in: .presenceEvent) == nil)
        #expect(SeriesNormalization.denormalize(0.5, in: .presenceEvent) == nil)
    }

    @Test func normalizeDenormalizeRoundTrips() {
        let cases: [(Double, ScaleKind)] = [
            (7, .ordinal0to10),
            (3, .ordinal1to5),
            (2, .ordinalBleeding),
            (6.5, .continuousHours),
            (90, .continuousMinutes),
        ]
        for (value, kind) in cases {
            let unit = SeriesNormalization.normalize(value, in: kind)
            #expect(unit != nil, "expected a normalized value for \(kind)")
            let rt = SeriesNormalization.denormalize(unit!, in: kind)
            #expect(rt != nil, "expected a denormalized value for \(kind)")
            #expect(abs(rt! - value) < 1e-9, "round-trip drifted for \(value) in \(kind): \(String(describing: rt))")
        }
    }

    @Test func normalizeMapsToUnitInterval() {
        #expect(SeriesNormalization.normalize(0, in: .ordinal0to10) == 0)
        #expect(SeriesNormalization.normalize(10, in: .ordinal0to10) == 1)
        #expect(SeriesNormalization.normalize(5, in: .ordinal0to10) == 0.5)
    }

    /// An out-of-domain logged value CLAMPS to the unit interval rather than escaping it (WR-02): a
    /// user-logged 26h of sleep (above the 0…24 ceiling) pins to 1.0, a value below the floor pins to
    /// 0.0. Phase-15 overlay positioning relies on the [0,1] guarantee holding for ALL inputs.
    @Test func normalizeClampsOutOfDomainInputsToUnitInterval() {
        // Above the ceiling → exactly 1.0 (continuousHours domain is 0…24).
        #expect(SeriesNormalization.normalize(26, in: .continuousHours) == 1.0)
        #expect(SeriesNormalization.normalize(1000, in: .continuousMinutes) == 1.0)
        #expect(SeriesNormalization.normalize(11, in: .ordinal0to10) == 1.0)
        // Below the floor → exactly 0.0 (ordinal1to5 domain is 1…5; ordinalBleeding is 1…4).
        #expect(SeriesNormalization.normalize(0, in: .ordinal1to5) == 0.0)
        #expect(SeriesNormalization.normalize(-3, in: .ordinal0to10) == 0.0)
    }

    @Test func readoutReportsRealValueAndUnit_notNormalized() {
        let readout = SeriesNormalization.readout(7, kind: .ordinal0to10, unit: "of 10")
        #expect(readout == "7.0 of 10")
        // Honesty guard — the readout must NEVER surface the normalized number (0.7).
        #expect(!readout.contains("0.7"))

        #expect(SeriesNormalization.readout(6.5, kind: .continuousHours, unit: "h") == "6.5 h")
        // Presence carries an empty unit — readout is the bare real value, never the normalized form.
        #expect(SeriesNormalization.readout(1, kind: .presenceEvent, unit: "") == "1.0")
    }
}
