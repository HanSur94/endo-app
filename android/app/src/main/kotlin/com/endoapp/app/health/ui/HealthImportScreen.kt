// The Health Connect import screen (Phase 21, Plan 04; AHC-03 / AHC-04) — the Bloom surface that
// composes the connect flow off the [HealthConnectController] seam: it presents the Plan-01
// [HealthRationaleScreen] as the rationale-BEFORE-permission step, then renders the import states from
// the registered [HealthConnectCopy] — progress + the HONEST window label (full history vs last 30
// days, per the granted-history flag), done-with-data (imported values carry a provenance badge),
// empty, and a gentle denial with NO re-prompt loop. Manual logging is unaffected in every state.
//
// SEPARATION INVARIANT (T-20-02d): this file imports NO notification / alarm / WorkManager API. Import
// is a plain foreground coroutine on the injected controller; the reminders package stays the app's
// SOLE notification owner.
package com.endoapp.app.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.health.HealthImportState
import kotlinx.coroutines.launch

/**
 * The connect→import Bloom screen. Composed off the [controller] seam so the whole flow is provable
 * against a fake:
 *
 * - [HealthImportState.Idle] → the Plan-01 [HealthRationaleScreen]; "Continue" invokes
 *   [onRequestPermissions] (the OS permission sheet — wired in Plan 05) and, on its result, launches
 *   [HealthConnectController.importNow]. "Not now" invokes [onClose] (no nag).
 * - [HealthImportState.InProgress] → the progress copy + the HONEST window label (AHC-04).
 * - [HealthImportState.Done] → the connected copy + a provenance chip (has data), or the honest empty
 *   copy (no data), plus a "Refresh from Health Connect" action.
 * - [HealthImportState.Denied] → the calm denial banner + the "connect anytime from Settings" hint,
 *   with NO re-request. Manual logging is unaffected throughout.
 *
 * @param onRequestPermissions the OS permission-request seam — takes a result callback receiving the
 *   granted set. Defaults to a no-op so the screen is testable; Plan 05 wires the real
 *   `PermissionController.createRequestPermissionResultContract()` launcher.
 * @param onClose the "Not now" dismiss.
 */
@Composable
fun HealthImportScreen(
    controller: HealthConnectController,
    modifier: Modifier = Modifier,
    onRequestPermissions: ((Set<String>) -> Unit) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state by controller.importState.collectAsState()

    when (val current = state) {
        HealthImportState.Idle -> HealthRationaleScreen(
            modifier = modifier,
            onContinue = {
                // The only path to the system sheet. On the result, run the foreground import.
                onRequestPermissions { _ -> scope.launch { controller.importNow() } }
            },
            onDismiss = onClose,
        )

        else -> ImportStatesPanel(
            controller = controller,
            state = current,
            modifier = modifier,
            onRefresh = { scope.launch { controller.refresh() } },
        )
    }
}

/**
 * The non-rationale states (progress / done / empty / denied). The window label reads
 * [HealthConnectController.grantedHistory] reactively (re-resolved whenever [state] changes) so it is
 * always the HONEST full-history-vs-30-day copy (AHC-04).
 */
@Composable
private fun ImportStatesPanel(
    controller: HealthConnectController,
    state: HealthImportState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val fullHistory by produceState(initialValue = false, state) { value = controller.grantedHistory() }
    val windowLabel =
        if (fullHistory) HealthConnectCopy.WINDOW_LABEL_HISTORY else HealthConnectCopy.WINDOW_LABEL_30_DAY

    Column(
        modifier = modifier
            .testTag("hc.import.root")
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = HealthConnectCopy.RATIONALE_HEADLINE,
            style = bloomH2,
            color = colors.ink,
            modifier = Modifier.testTag("hc.import.headline"),
        )

        when (state) {
            HealthImportState.InProgress -> {
                Column(
                    modifier = Modifier.bloomCard(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = colors.plum,
                            modifier = Modifier.size(20.dp).testTag("hc.import.spinner"),
                        )
                        Text(
                            text = HealthConnectCopy.IMPORT_IN_PROGRESS,
                            style = bloomBody,
                            color = colors.ink,
                            modifier = Modifier.testTag("hc.import.progress"),
                        )
                    }
                    Text(
                        text = windowLabel,
                        style = bloomBody,
                        color = colors.inkSecondary,
                        modifier = Modifier.testTag("hc.import.windowLabel"),
                    )
                }
            }

            is HealthImportState.Done -> {
                Column(
                    modifier = Modifier.bloomCard(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = windowLabel,
                        style = bloomBody,
                        color = colors.inkSecondary,
                        modifier = Modifier.testTag("hc.import.windowLabel"),
                    )
                    if (state.hasData) {
                        Text(
                            text = HealthConnectCopy.IMPORT_DONE,
                            style = bloomBody,
                            color = colors.ink,
                            modifier = Modifier.testTag("hc.import.done"),
                        )
                        // The provenance chip imported values carry (Plan 05 / Phase 22 attach it inline).
                        HealthSourceBadge()
                    } else {
                        Text(
                            text = HealthConnectCopy.IMPORT_EMPTY,
                            style = bloomBody,
                            color = colors.inkSecondary,
                            modifier = Modifier.testTag("hc.import.empty"),
                        )
                    }
                }

                // The manual re-import action (idempotent by hcRecordId).
                Text(
                    text = HealthConnectCopy.REFRESH,
                    style = bloomButtonLabel,
                    color = colors.plum,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable(onClick = onRefresh)
                        .testTag("hc.import.refresh")
                        .semantics { role = Role.Button; contentDescription = HealthConnectCopy.REFRESH },
                )
            }

            HealthImportState.Denied -> {
                // Gentle denial — manual logging unaffected, NO re-prompt loop, no refresh affordance.
                Column(
                    modifier = Modifier.bloomCard().testTag("hc.import.denied"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = HealthConnectCopy.DENIED_BANNER, style = bloomBody, color = colors.ink)
                    Text(
                        text = HealthConnectCopy.DENIED_REOPEN_HINT,
                        style = bloomBody,
                        color = colors.inkSecondary,
                    )
                }
            }

            HealthImportState.Idle -> Unit // handled by the caller (rationale screen)
        }
    }
}
