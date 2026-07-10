// The report builder HOST (Phase 24-05, AREL-01/02) — the thin Compose layer that binds the seam-driven
// [ReportBuilderScreen] to the [ReportBuilderViewModel] and, once a PDF is ready and the user taps Share,
// composes the Plan-04 [ReportShareHost] (FileProvider + ACTION_SEND chooser + cache cleanup). Mirrors
// the Phase-23 [com.endoapp.app.export.ui.ExportImportHost] posture (own the scope; drive the VM; launch
// the system Intent only on an explicit user action).
//
// The Share chooser is launched ONLY after the user taps Share on the ReadyToShare confirmation — never
// automatically — so the OS-owned chooser is the SOLE egress and the user picks the target (T-24-01).
//
// FOREGROUND-ONLY / NO NETWORK: this `ui/` file imports NO androidx.work / notification / alarm / networking
// API (the report package stays network-free — ReportNoNetworkTest; the reminders package stays the sole
// notification owner). It legitimately imports Compose (it IS the UI seam).
package com.endoapp.app.report.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Hosts [ReportBuilderScreen] over the [viewModel], and — when the produced PDF is shared — composes the
 * [ReportShareHost] for the current [ReportPhase.ReadyToShare] file.
 *
 * The picker/generate seams forward to the ViewModel; Generate runs as a foreground coroutine (the
 * assemble + render run off-main inside the VM, a spinner is driven by [ReportPhase.Generating]). Share
 * flips a local flag that composes [ReportShareHost] for the ready file; when the chooser returns the
 * cache PDF is deleted and the builder resets to the picker.
 *
 * @param viewModel the composition-root [ReportBuilderViewModel] (bound ONLY in AppGate's Consented arm).
 * @param onClose the back dismiss (pops the builder off the in-Log stack).
 */
@Composable
fun ReportBuilderHost(
    viewModel: ReportBuilderViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    // Set true when the user taps Share on the ReadyToShare confirmation → composes ReportShareHost once.
    var sharing by remember { mutableStateOf(false) }

    ReportBuilderScreen(
        state = viewModel.state,
        modifier = modifier,
        onSelectPreset = viewModel::selectPreset,
        onSetCustomRange = viewModel::setCustomRange,
        onGenerate = { scope.launch { viewModel.generate() } },
        onShare = { sharing = true },
        onClose = onClose,
    )

    // Launch the OS share chooser ONLY on an explicit Share tap, for the ready PDF. On return the host
    // deletes the cache PDF (T-24-10) and resets the builder to the picker.
    val phase = state.phase
    if (sharing && phase is ReportPhase.ReadyToShare) {
        ReportShareHost(
            pdfFile = phase.file,
            onShared = {
                sharing = false
                viewModel.reset()
            },
        )
    }
}
