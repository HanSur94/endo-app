// EndoInsights (:insights) — unit-pinned honesty invariants for series normalization.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift` (6 cases).
// These tests lock the FIXED-honest-domain + real-unit-readout contract:
//   • Every ScaleKind has a FIXED domain (0…10, 1…5, 1…4, 0…24, 0…240) — never a per-visible-window
//     min/max.
//   • Presence has NO numeric domain (null) — an event lane is not a normalized value.
//   • normalize → denormalize round-trips to the original value within float tolerance.
//   • The readout ALWAYS reports the REAL value + unit, never the normalized [0,1] number (honesty).
//     The readout never contains the normalized form — asserted explicitly.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SeriesNormalizationTests {

    @Test
    fun domainPerKindIsFixedAndHonest() {
        assertEquals(0.0..10.0, SeriesNormalization.domain(for_ = ScaleKind.ORDINAL_0_TO_10))
        assertEquals(1.0..5.0, SeriesNormalization.domain(for_ = ScaleKind.ORDINAL_1_TO_5))
        assertEquals(1.0..4.0, SeriesNormalization.domain(for_ = ScaleKind.ORDINAL_BLEEDING))
        assertEquals(0.0..24.0, SeriesNormalization.domain(for_ = ScaleKind.CONTINUOUS_HOURS))
        assertEquals(0.0..240.0, SeriesNormalization.domain(for_ = ScaleKind.CONTINUOUS_MINUTES))
    }

    @Test
    fun presenceKindHasNoDomain() {
        assertNull(SeriesNormalization.domain(for_ = ScaleKind.PRESENCE_EVENT))
        assertNull(SeriesNormalization.normalize(1.0, in_ = ScaleKind.PRESENCE_EVENT))
        assertNull(SeriesNormalization.denormalize(0.5, in_ = ScaleKind.PRESENCE_EVENT))
    }

    @Test
    fun normalizeDenormalizeRoundTrips() {
        val cases = listOf(
            7.0 to ScaleKind.ORDINAL_0_TO_10,
            3.0 to ScaleKind.ORDINAL_1_TO_5,
            2.0 to ScaleKind.ORDINAL_BLEEDING,
            6.5 to ScaleKind.CONTINUOUS_HOURS,
            90.0 to ScaleKind.CONTINUOUS_MINUTES,
        )
        for ((value, kind) in cases) {
            val unit = SeriesNormalization.normalize(value, in_ = kind)
            assertNotNull(unit, "expected a normalized value for $kind")
            val rt = SeriesNormalization.denormalize(unit!!, in_ = kind)
            assertNotNull(rt, "expected a denormalized value for $kind")
            assertTrue(abs(rt!! - value) < 1e-9, "round-trip drifted for $value in $kind: $rt")
        }
    }

    @Test
    fun normalizeMapsToUnitInterval() {
        assertEquals(0.0, SeriesNormalization.normalize(0.0, in_ = ScaleKind.ORDINAL_0_TO_10))
        assertEquals(1.0, SeriesNormalization.normalize(10.0, in_ = ScaleKind.ORDINAL_0_TO_10))
        assertEquals(0.5, SeriesNormalization.normalize(5.0, in_ = ScaleKind.ORDINAL_0_TO_10))
    }

    /** An out-of-domain logged value CLAMPS to the unit interval rather than escaping it: a user-logged
     *  26h of sleep (above the 0…24 ceiling) pins to 1.0, a value below the floor pins to 0.0. */
    @Test
    fun normalizeClampsOutOfDomainInputsToUnitInterval() {
        // Above the ceiling → exactly 1.0 (CONTINUOUS_HOURS domain is 0…24).
        assertEquals(1.0, SeriesNormalization.normalize(26.0, in_ = ScaleKind.CONTINUOUS_HOURS))
        assertEquals(1.0, SeriesNormalization.normalize(1000.0, in_ = ScaleKind.CONTINUOUS_MINUTES))
        assertEquals(1.0, SeriesNormalization.normalize(11.0, in_ = ScaleKind.ORDINAL_0_TO_10))
        // Below the floor → exactly 0.0 (ORDINAL_1_TO_5 domain is 1…5; ORDINAL_BLEEDING is 1…4).
        assertEquals(0.0, SeriesNormalization.normalize(0.0, in_ = ScaleKind.ORDINAL_1_TO_5))
        assertEquals(0.0, SeriesNormalization.normalize(-3.0, in_ = ScaleKind.ORDINAL_0_TO_10))
    }

    @Test
    fun readoutReportsRealValueAndUnit_notNormalized() {
        // A whole-number ordinal renders WITHOUT a trailing ".0".
        val readout = SeriesNormalization.readout(7.0, kind = ScaleKind.ORDINAL_0_TO_10, unit = "of 10")
        assertEquals("7 of 10", readout)
        // Honesty guard, coupled to the REAL normalized number rather than a hardcoded "0.7": compute
        // normalize(7) and assert its string form is genuinely absent from the readout.
        val normalized = SeriesNormalization.normalize(7.0, in_ = ScaleKind.ORDINAL_0_TO_10)!!
        assertEquals(0.7, normalized)
        assertFalse(readout.contains("$normalized"))

        assertEquals("6.5 h", SeriesNormalization.readout(6.5, kind = ScaleKind.CONTINUOUS_HOURS, unit = "h"))
        // Presence carries an empty unit — readout is the bare real value, never the normalized form.
        assertEquals("1", SeriesNormalization.readout(1.0, kind = ScaleKind.PRESENCE_EVENT, unit = ""))
    }
}
