// The UI-facing Health Connect seam (Phase 21, Plan 04). The connect/import screens render against
// THIS interface, never against a concrete Android/Health-Connect type, so the whole Bloom surface is
// provable off a fake with no emulator. The concrete production implementation — the one that resolves
// `getSdkStatus()`, reads `getGrantedPermissions()`, and drives the Plan-03 [HealthImportCoordinator]
// from the foreground — is built and wired at the composition root in Plan 05.
//
// SEPARATION INVARIANT (T-20-02d): this file imports NO notification / alarm / WorkManager API. Import
// is a foreground coroutine; the reminders package stays the app's SOLE notification owner. The
// Phase-20 NotificationSeparationTest scans this file and forbids any such import.
package com.endoapp.app.health

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The observable state of an import run — what the [com.endoapp.app.health.ui.HealthImportScreen]
 * renders. Deliberately small: the screen decides the honest window label from [grantedHistory], so the
 * state only needs to say WHERE in the run we are.
 */
sealed interface HealthImportState {

    /** Nothing has been requested yet — the screen shows the rationale-before-permission step. */
    data object Idle : HealthImportState

    /** The import coroutine is running — the screen shows the progress copy + the honest window label. */
    data object InProgress : HealthImportState

    /**
     * The import finished.
     *
     * @property hasData `true` ⇒ some rows were imported (the "connected, data shows a badge" copy +
     *   the provenance chip); `false` ⇒ the honest empty state (the common first-run outcome).
     * @property grantedPermissions the READ scopes the user allowed (for a truthful per-permission line,
     *   never a nag).
     */
    data class Done(
        val hasData: Boolean,
        val grantedPermissions: Set<String> = emptySet(),
    ) : HealthImportState

    /** The user declined at the system sheet — the screen shows the gentle denial banner, no re-prompt. */
    data object Denied : HealthImportState
}

/**
 * The single seam the connect entry + import screen drive. Mirrors the reminders `ReminderController`
 * posture — ONE object threads from the composition root through Settings to the UI, and a fake stands
 * in for it in the JVM Robolectric+Compose lane.
 *
 * Every method is `suspend` because the real implementation touches `getSdkStatus`, the permission
 * controller, and the import coordinator off the main thread. [importState] is the reactive surface the
 * import screen collects. Manual logging is NEVER gated behind any of these — the seam only decides
 * which honest Health Connect surface to render.
 */
interface HealthConnectController {

    /** The live import state the screen observes. */
    val importState: StateFlow<HealthImportState>

    /**
     * Resolve the three-state availability posture for THIS session (never cached — the user may
     * install/update the provider between foregrounds). Drives which surface the connect entry shows.
     */
    suspend fun availability(): HealthAvailability

    /**
     * `true` iff `READ_HEALTH_DATA_HISTORY` is granted → the screen shows the honest "full history"
     * window label; `false` → the "last 30 days" label (AHC-04). Read per session, never cached.
     */
    suspend fun grantedHistory(): Boolean

    /**
     * Run a foreground import via the Plan-03 [HealthImportCoordinator]; transitions [importState]
     * through [HealthImportState.InProgress] to a terminal [HealthImportState.Done] /
     * [HealthImportState.Denied]. Named `importNow` (not `import`) because `import` is a Kotlin keyword.
     */
    suspend fun importNow()

    /** Re-run the import (the "Refresh from Health Connect" action) — idempotent by `hcRecordId`. */
    suspend fun refresh()
}

/**
 * The production [HealthConnectController] — the concrete impl wired at the composition root (Plan 05)
 * over the availability gate (Plan 01), the real [HealthConnectReader] (Plan 02), and the
 * [HealthImportCoordinator] (Plan 03). Everything Health-Connect-specific enters through injected
 * seams so this class stays JVM-testable off a [FakeHealthReader] + an in-memory repository with NO
 * emulator (the flow test drives exactly this class).
 *
 * ## Availability-gated, nullable reader (T-21-05b)
 * [readerProvider] resolves availability EVERY call and returns a reader ONLY when the provider is
 * AVAILABLE — it is the sole owner of `HealthConnectClient.getOrCreate`, which is NEVER called blindly
 * (a blind construction crashes on API 26–33). When the reader is `null` the controller does no client
 * work and reports the honest empty terminal state, so an UNAVAILABLE/UPDATE_REQUIRED device never
 * crashes and never blocks manual logging.
 *
 * ## Per-session grant read (Pitfall 3 / T-21-03)
 * Neither [grantedHistory] nor the import path caches a grant flag: the reader's
 * `getGrantedPermissions()` is read afresh on every call (a grant can be revoked or unused-app
 * auto-reset). An empty granted set ⇒ the honest [HealthImportState.Denied] surface (no re-prompt); a
 * `SecurityException` mid-read (a race with revocation) is caught and surfaced as `Denied`, never a crash.
 *
 * ## Separation invariant (T-20-02d)
 * The import is a plain foreground coroutine on [dispatcher] — NO WorkManager, NO notification. This
 * file imports no such API; the reminders package stays the app's SOLE notification owner.
 *
 * @param availabilityProvider resolves the three-state posture per session (never cached).
 * @param readerProvider builds a [HealthReader] when AVAILABLE, else `null` (the availability-gated seam).
 * @param coordinatorFor builds a fresh [HealthImportCoordinator] over a resolved reader (repo + token
 *   store + consent are captured at the composition root).
 * @param hasImportedData the honest "the store holds imported rows" query (drives the empty-vs-data copy).
 * @param dispatcher the background dispatcher the import + grant reads run on.
 */
class RealHealthConnectController(
    private val availabilityProvider: suspend () -> HealthAvailability,
    private val readerProvider: suspend () -> HealthReader?,
    private val coordinatorFor: (HealthReader) -> HealthImportCoordinator,
    private val hasImportedData: suspend () -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HealthConnectController {

    private val _importState = MutableStateFlow<HealthImportState>(HealthImportState.Idle)
    override val importState: StateFlow<HealthImportState> = _importState.asStateFlow()

    override suspend fun availability(): HealthAvailability =
        withContext(dispatcher) { availabilityProvider() }

    override suspend fun grantedHistory(): Boolean = withContext(dispatcher) {
        // Read PER SESSION — never a cached flag. No reader (not AVAILABLE) ⇒ no history.
        val reader = readerProvider() ?: return@withContext false
        reader.grantedPermissions().contains(HealthConnectPermissions.HISTORY)
    }

    override suspend fun importNow() = runImport()

    override suspend fun refresh() = runImport()

    /**
     * Resolve the availability-gated reader, read the granted set per session, run the coordinator, and
     * transition [importState] to the honest terminal state. Does no client work when unavailable.
     */
    private suspend fun runImport() = withContext(dispatcher) {
        val reader = readerProvider()
        if (reader == null) {
            // availability != AVAILABLE — honest empty terminal, NO client work, NO crash (T-21-05b).
            _importState.value = HealthImportState.Done(hasData = false, grantedPermissions = emptySet())
            return@withContext
        }

        _importState.value = HealthImportState.InProgress
        try {
            // Per-session grant read (never cached). Nothing granted ⇒ the gentle denial (no re-prompt).
            val granted = reader.grantedPermissions()
            if (granted.isEmpty()) {
                _importState.value = HealthImportState.Denied
                return@withContext
            }

            val outcome = coordinatorFor(reader).importAll()
            _importState.value = HealthImportState.Done(
                hasData = hasImportedData(),
                grantedPermissions = outcome.grantedPermissions,
            )
        } catch (_: SecurityException) {
            // A grant revoked between the resolve and the read (Pitfall 3) — honest denial, never a crash.
            _importState.value = HealthImportState.Denied
        }
    }
}
