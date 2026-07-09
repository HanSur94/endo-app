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

import kotlinx.coroutines.flow.StateFlow

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
