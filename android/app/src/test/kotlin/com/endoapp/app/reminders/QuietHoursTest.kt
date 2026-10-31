package com.endoapp.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM proof of [QuietHours.isInQuietHours] — the reminder's quiet-window predicate — including the
 * MIDNIGHT-CROSSING case (22:00–07:00), the same-day case, and the boundaries (start inclusive, end
 * exclusive). No Android types, no clock: value-in / value-out.
 */
class QuietHoursTest {

    private val start2200 = 22 * 60 // 1320
    private val end0700 = 7 * 60 // 420

    @Test
    fun midnightCrossing_isQuietLateNight() {
        // 23:30 is inside 22:00–07:00.
        assertTrue(QuietHours.isInQuietHours(23 * 60 + 30, start2200, end0700))
    }

    @Test
    fun midnightCrossing_isQuietEarlyMorning() {
        // 03:00 is inside 22:00–07:00 (past midnight).
        assertTrue(QuietHours.isInQuietHours(3 * 60, start2200, end0700))
    }

    @Test
    fun midnightCrossing_isNotQuietMidday() {
        // 12:00 is OUTSIDE 22:00–07:00.
        assertFalse(QuietHours.isInQuietHours(12 * 60, start2200, end0700))
    }

    @Test
    fun midnightCrossing_startIsInclusive() {
        // Exactly 22:00 is quiet (start inclusive).
        assertTrue(QuietHours.isInQuietHours(start2200, start2200, end0700))
    }

    @Test
    fun midnightCrossing_endIsExclusive() {
        // Exactly 07:00 is NOT quiet (end exclusive).
        assertFalse(QuietHours.isInQuietHours(end0700, start2200, end0700))
    }

    @Test
    fun sameDayWindow_inAndOut() {
        // A same-day 13:00–14:00 window.
        val start = 13 * 60
        val end = 14 * 60
        assertTrue(QuietHours.isInQuietHours(13 * 60 + 30, start, end)) // inside
        assertTrue(QuietHours.isInQuietHours(start, start, end)) // start inclusive
        assertFalse(QuietHours.isInQuietHours(end, start, end)) // end exclusive
        assertFalse(QuietHours.isInQuietHours(12 * 60, start, end)) // before
        assertFalse(QuietHours.isInQuietHours(15 * 60, start, end)) // after
    }

    @Test
    fun zeroLengthWindow_neverQuiet() {
        // A degenerate window must never silence every minute.
        assertFalse(QuietHours.isInQuietHours(0, 8 * 60, 8 * 60))
        assertFalse(QuietHours.isInQuietHours(8 * 60, 8 * 60, 8 * 60))
        assertFalse(QuietHours.isInQuietHours(12 * 60, 8 * 60, 8 * 60))
    }
}
