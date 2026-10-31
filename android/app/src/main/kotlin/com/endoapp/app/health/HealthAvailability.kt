// The three-state Health Connect availability gate (Phase 21, AHC-01). Never call `getOrCreate()`
// blindly — resolve `getSdkStatus()` first, EVERY foreground, and render an honest state. Manual
// logging is NEVER blocked by any state; the gate only decides which HC surface to show.
package com.endoapp.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

/**
 * The honest three-state posture of the Health Connect provider on this device (AHC-01):
 *
 * - [AVAILABLE] — the provider is present (Android 14+ built-in module, or 9–13 with the installable
 *   Health Connect app). The ONLY state in which the client may be constructed.
 * - [PROVIDER_UPDATE_REQUIRED] — the installable provider is absent or stale (API 28–33); prompt the
 *   user to install/update it from Google Play. Manual logging is unaffected.
 * - [UNAVAILABLE] — Health Connect cannot run on this device (e.g. API 26–27), OR the status is
 *   unrecognised (fail-closed default). Manual logging is the hero path; the import surface is
 *   softened, not shown as an error.
 */
enum class HealthAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNAVAILABLE,
    ;

    companion object {

        /**
         * The pure `Int → HealthAvailability` mapper over a `getSdkStatus()` result. JVM-testable
         * without a device — feed the [HealthConnectClient] status constants directly.
         *
         * Uses the VERIFIED current constant `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` (NOT the stale
         * `SDK_STATUS_UPDATE_REQUIRED`). FAIL-CLOSED: any status outside the two "usable-ish" constants
         * — including [HealthConnectClient.SDK_UNAVAILABLE] and any unrecognised integer — maps to
         * [UNAVAILABLE], so an unknown provider state can never be mistaken for a ready client.
         */
        fun availability(sdkStatus: Int): HealthAvailability = when (sdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> PROVIDER_UPDATE_REQUIRED
            else -> UNAVAILABLE
        }

        /**
         * The thin device wrapper: resolve the live `getSdkStatus(context)` and delegate to the pure
         * mapper. The result is NOT cached — callers re-resolve it per session (the user may
         * install/update the provider between foregrounds). Device-gated: exercised in manual
         * verification, not the JVM unit lane (which pins the pure mapper above).
         */
        fun availability(context: Context): HealthAvailability =
            availability(HealthConnectClient.getSdkStatus(context))
    }
}
