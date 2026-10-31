// EndoInsights (:insights) — unit-pinned honesty invariants for chart scrubbing.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` (7 cases).
// These tests carry the phase's honesty contract:
//   • A raw scrub day on a logged day resolves to EXACTLY that day's point — never a neighbor, never an
//     interpolation.
//   • A raw scrub day on an unlogged day resolves to (day, null) — the explicit gap, never a fabricated
//     zero.
//   • A logged severity-0 day resolves to a REAL point with value 0 — provably distinct from null
//     (absence ≠ 0).
//
// Day identity is `java.time.LocalDate` (the injected Swift `Calendar` + wall-clock `hour`/`minute` are
// DROPPED — a LocalDate is already a zone-free calendar day, so `startOfDay` and DST/23:59 bucketing are
// no-ops by construction). Tests 5 and 6 are preserved as day-identity pins under the LocalDate model:
// the same "never roll into a neighbor" invariant they encoded in Swift now holds by construction, and
// the tests assert it explicitly on a late-in-day / DST-transition calendar date.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChartScrubSelectionTests {

    private fun day(year: Int, month: Int, d: Int): LocalDate = LocalDate.of(year, month, d)

    private fun point(year: Int, month: Int, d: Int, value: Double): InsightPoint =
        InsightPoint(date = day(year, month, d), value = value)

    // Test 1 (null input): no raw date → no selection, no chrome.
    @Test
    fun nilRawDate_resolvesToNil() {
        val points = listOf(point(2026, 6, 1, 3.0), point(2026, 6, 2, 4.0))
        val resolution = ChartScrubSelection.resolve(null, points)
        assertNull(resolution)
    }

    // Test 2 (exact logged-day match): a raw date on a logged day returns THAT day's point, value
    // preserved exactly — never a neighbor.
    @Test
    fun loggedDay_resolvesToExactPoint() {
        val target = point(2026, 6, 5, 6.0)
        val points = listOf(point(2026, 6, 4, 3.0), target, point(2026, 6, 6, 8.0))
        val raw = day(2026, 6, 5)
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertEquals(raw, resolution?.day)
        assertEquals(target, resolution?.point)
        assertEquals(6.0, resolution?.point?.value)
    }

    // Test 3 (gap day): a raw date inside a gap between logged days returns the correct day with
    // point == null — NEVER the nearest logged point, NEVER a fabricated value.
    @Test
    fun gapDay_resolvesToNilPoint_neverNearestNeighbor() {
        // Logged Jun 1 and Jun 4; Jun 2–3 are an unlogged gap.
        val points = listOf(point(2026, 6, 1, 3.0), point(2026, 6, 4, 7.0))
        val raw = day(2026, 6, 2)
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertNotNull(resolution, "The day under the finger is real — the gap must still resolve.")
        assertEquals(raw, resolution?.day)
        assertNull(resolution?.point, "A gap day must resolve to an explicit null point — never snap to a neighbor.")
    }

    // Test 4 (logged severity-0): a day logged with value 0 is a REAL point reading 0 — NOT null
    // (absence ≠ 0).
    @Test
    fun loggedSeverityZeroDay_resolvesToRealZeroPoint_notNil() {
        val zeroDay = point(2026, 6, 3, 0.0)
        val points = listOf(point(2026, 6, 2, 5.0), zeroDay)
        val raw = day(2026, 6, 3)
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertNotNull(resolution?.point, "A logged severity-0 day is a REAL point — it must never read as a gap.")
        assertEquals(0.0, resolution?.point?.value)
    }

    // Test 5 (late-in-day identity): a LocalDate is a whole calendar day with no wall-clock component,
    // so a "late evening" gesture resolves to that SAME day — the neighbor is never rolled into. Under
    // LocalDate this holds by construction (the Swift 23:59 startOfDay-bucketing case).
    @Test
    fun lateEveningRawDate_matchesSameDay() {
        val target = point(2026, 6, 5, 4.0)
        val points = listOf(target, point(2026, 6, 6, 9.0))
        val raw = day(2026, 6, 5) // any time on Jun 5 is the same LocalDate
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertEquals(raw, resolution?.day)
        assertEquals(target, resolution?.point, "Jun 5 belongs to the SAME calendar day — never roll into the neighbor.")
    }

    // Test 6 (DST-transition day identity): the 2026-03-29 Europe/Berlin spring-forward day is a normal
    // LocalDate — zone-free by construction, so it resolves to ITS OWN point with no epoch/86400 math.
    @Test
    fun dstSpringForwardDay_resolvesCorrectly() {
        val points = listOf(
            point(2026, 3, 28, 5.0),
            point(2026, 3, 29, 6.0), // the spring-forward day (02:00 → 03:00 in Europe/Berlin)
            point(2026, 3, 30, 7.0),
        )
        val raw = day(2026, 3, 29)
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertEquals(raw, resolution?.day)
        assertEquals(6.0, resolution?.point?.value, "The DST transition day must resolve to ITS OWN point, never a neighbor.")
    }

    // Test 7 (unsorted input): points supplied out of order still resolve correctly — the resolver must
    // not assume producer sort order.
    @Test
    fun unsortedInput_resolvesCorrectly() {
        val target = point(2026, 6, 10, 2.0)
        val points = listOf(point(2026, 6, 12, 8.0), target, point(2026, 6, 8, 5.0))
        val raw = day(2026, 6, 10)
        val resolution = ChartScrubSelection.resolve(raw, points)
        assertEquals(target, resolution?.point)
    }
}
