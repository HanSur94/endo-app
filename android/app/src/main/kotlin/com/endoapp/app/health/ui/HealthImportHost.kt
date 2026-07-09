// The Health Connect import HOST (Phase 21, Plan 05) — the thin Compose layer that wires the REAL
// system permission request onto the Plan-04 [HealthImportScreen]. It is the SOLE place the Health
// Connect permission contract is launched: the OS owns the permission UI, so a custom dialog could
// never grant HC permissions (RESEARCH "Don't Hand-Roll").
//
// FOREGROUND-ONLY TRIGGER (T-20-02d): the import is a plain foreground coroutine kicked off on the
// permission result, on a returning-user connect-screen visit (already granted), and on the manual
// "Refresh" action — NO WorkManager, NO notification, NO background read permission. This file imports
// no notification/alarm/WorkManager API; the reminders package stays the app's SOLE notification owner.
package com.endoapp.app.health.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import com.endoapp.app.health.HealthAvailability
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.health.HealthConnectPermissions

/**
 * Hosts [HealthImportScreen] with the real `PermissionController` request contract.
 *
 * The Plan-04 screen exposes `onRequestPermissions` as a `((Set<String>) -> Unit) -> Unit` seam so it
 * stays provable off a fake; here we bind it to a real
 * [PermissionController.createRequestPermissionResultContract] launcher. Because an
 * `ActivityResultLauncher`'s result callback is fixed at `remember` time, the pending per-request
 * result callback is stashed and invoked when the launcher returns the granted set — then the screen
 * runs the foreground import via the [controller] (which itself reads `getGrantedPermissions()` per
 * session, never cached).
 *
 * On a returning-user visit (the provider is AVAILABLE and history is already granted) the import is
 * kicked off immediately so fresh data lands without a re-tap — while the rationale-BEFORE-permission
 * gate is preserved for first-time users (an ungranted visit shows the rationale screen).
 *
 * @param controller the composition-root [HealthConnectController] (bound only in the Consented arm).
 * @param onClose the "Not now" / back dismiss.
 */
@Composable
fun HealthImportHost(
    controller: HealthConnectController,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    // The result callback for the in-flight permission request (set just before launch, cleared on result).
    val pending = remember { PendingPermissionResult() }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted: Set<String> ->
        // The OS returned the granted set — hand it to the screen's result callback (which runs the
        // foreground import). Read is per session; nothing is cached here.
        pending.callback?.invoke(granted)
        pending.callback = null
    }

    // Returning-user auto-refresh: an AVAILABLE provider with history already granted imports on visit
    // (foreground coroutine on the controller). A not-yet-granted visit falls through to the rationale.
    LaunchedEffect(controller) {
        if (controller.availability() == HealthAvailability.AVAILABLE && controller.grantedHistory()) {
            controller.importNow()
        }
    }

    HealthImportScreen(
        controller = controller,
        modifier = modifier,
        onRequestPermissions = { onResult ->
            pending.callback = onResult
            launcher.launch(HealthConnectPermissions.REQUESTED)
        },
        onClose = onClose,
    )
}

/** A tiny mutable holder for the in-flight permission-request result callback (not recomposed). */
private class PendingPermissionResult {
    var callback: ((Set<String>) -> Unit)? = null
}
