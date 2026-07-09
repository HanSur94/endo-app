package com.endoapp.app.health

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM guard for the three-state Health Connect availability gate (AHC-01, threat T-21-01b).
 *
 * `getSdkStatus()` returns one of three honest states; the pure `Int → HealthAvailability` mapper is
 * JVM-testable WITHOUT an emulator or a real provider — feed the three [HealthConnectClient] status
 * constants directly and assert the resulting enum. The `availability(context)` wrapper (which calls
 * the real `getSdkStatus`) is device-gated manual verification; only the pure mapper is unit-pinned.
 *
 * The gate NEVER blocks manual logging — it only decides which HC surface to show. That invariant is
 * enforced by callers (Plan 04); this test pins the mapping honesty: an AVAILABLE state is the ONLY
 * one that lets the client be constructed, and any unrecognised status falls DEFENSIVELY to
 * UNAVAILABLE (never AVAILABLE — a fail-closed posture, so an unknown provider state can never be
 * mistaken for a granted, ready client).
 *
 * Red-capable: mapping SDK_UNAVAILABLE → AVAILABLE, or letting an unknown status fall to AVAILABLE,
 * turns this red.
 */
class HealthAvailabilityTest {

    @Test
    fun sdkAvailableMapsToAvailable() {
        assertEquals(
            HealthAvailability.AVAILABLE,
            HealthAvailability.availability(HealthConnectClient.SDK_AVAILABLE),
        )
    }

    @Test
    fun providerUpdateRequiredMapsToProviderUpdateRequired() {
        assertEquals(
            HealthAvailability.PROVIDER_UPDATE_REQUIRED,
            HealthAvailability.availability(
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            ),
        )
    }

    @Test
    fun sdkUnavailableMapsToUnavailable() {
        assertEquals(
            HealthAvailability.UNAVAILABLE,
            HealthAvailability.availability(HealthConnectClient.SDK_UNAVAILABLE),
        )
    }

    @Test
    fun unknownStatusFallsDefensivelyToUnavailable() {
        // A status integer outside the three documented constants must NEVER read as AVAILABLE —
        // the gate is fail-closed so an unrecognised provider state cannot open a "ready" surface.
        val unknownStatuses = listOf(-1, 0, 99, Int.MAX_VALUE, Int.MIN_VALUE)
        for (status in unknownStatuses) {
            assertEquals(
                "Unknown SDK status $status must map defensively to UNAVAILABLE.",
                HealthAvailability.UNAVAILABLE,
                HealthAvailability.availability(status),
            )
        }
    }
}
