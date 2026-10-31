package com.endoapp.app

import com.endoapp.insights.DaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A trivial JVM smoke test for the :app module.
 *
 * It exists to prove two structural facts before Plan 02+ add real entities/DAOs:
 *   1. The :app JUnit 4 unit-test source set compiles and runs (`:app:testDebugUnitTest`).
 *   2. The one-way `:app -> :insights` dependency resolves at test-compile time — this test
 *      references a public `:insights` type ([DaySnapshot]) built via its `of` factory.
 *
 * No Room, no Android Context — a pure JVM assertion, so it runs fast on every push/PR.
 */
class ModuleSmokeTest {

    @Test
    fun insightsTypeIsReachableFromApp() {
        val day = LocalDate.of(2026, 1, 1)
        val snapshot = DaySnapshot.of(day = day)
        assertEquals(day, snapshot.day)
    }
}
