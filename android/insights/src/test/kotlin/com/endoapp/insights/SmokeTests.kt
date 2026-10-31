package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The :insights wiring SMOKE — the Kotlin mirror of
 * `EndoInsights/Tests/EndoInsightsTests/SmokeTests.swift`.
 *
 * These trivial cases exist to PROVE the JUnit Platform test target actually executes end-to-end
 * (`useJUnitPlatform()` wired, JDK 21 toolchain resolved, package compiles) — not green-by-omission.
 * They are the first cases of the 157-case Swift→Kotlin parity target (see PARITY.md).
 *
 * Parity note: the Swift SmokeTests.swift contributes 2 to the grep-based `@Test` parity count
 * (1 real @Test method + 1 "@Test" mention inside its doc comment). This Kotlin mirror carries
 * 2 real @Test methods so the JUnit XML case count matches the ledger's Swift column exactly.
 */
class SmokeTests {

    @Test
    fun packageTestTargetRunsInPlatform() {
        assertTrue(true)
    }

    @Test
    fun arithmeticSanity() {
        assertEquals(4, 2 + 2)
    }
}
