// EndoApp (:app) — the intra-Log host (ATRK-01). Owns the in-Log `rememberSaveable` stack, renders
// the current route, pops on system back when nested, and observes the (non-sensitive)
// [CheckInRouter] flag — but ONLY here, inside the consented Log subtree (threat T-20-11), so a
// reminder deep-link lands on the consent gate first and can never construct health UI pre-consent.
//
// NO `NavHost`, NO navigation dependency. The CheckIn (Plan 02), DayLog (Plan 03), and Settings
// (Plan 04) arms all render their real screens now. This host stays free of any notification API — the
// [onDayLogged] seam is where Plan 05 wires the reminder auto-suppress consumer. Persistence + the
// Settings med/treatment writes are injected as seams (Check-In MERGES via [persistDay]; the Day Log
// editor REPLACES the whole day via [persistDayLog]) so the host imports no DAO.
package com.endoapp.app.ui.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.endoapp.app.CheckInRouter
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.export.ExportImportViewModel
import com.endoapp.app.export.ui.ExportImportHost
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.health.ui.HealthImportHost
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate

/**
 * The Log-tab host. [recentDays] is the live day history off the consent-gated repository;
 * [router] is observed here (and nowhere pre-consent) to push [TrackingRoute.CheckIn] on a
 * reminder deep-link; [onWithdraw] backs the graduated Settings withdraw-consent control (re-gates the
 * app); [onDayLogged] is the fire-and-forget seam Plan 05 wires to the reminder
 * auto-suppress consumer (kept here so this host imports no notification API).
 *
 * The Daily Check-In persistence is injected as seams so the host stays free of any DAO and the
 * check-in is testable off a fake repository: [persistDay] writes the mapped rows through the
 * consent-gated repository, [loadYesterday] reads yesterday's draft (the sticky-context source), and
 * [today] supplies the epoch-day the check-in records against. Their production wiring is threaded from
 * the composition root; the defaults keep the Phase-19 call sites (and the shell/gate tests) compiling.
 */
@Composable
fun LogTabHost(
    recentDays: Flow<List<DayEntry>>,
    router: CheckInRouter,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
    onDayLogged: () -> Unit = {},
    persistDay: suspend (DayLogRows) -> Unit = {},
    persistDayLog: suspend (DayLogRows) -> Unit = {},
    loadYesterday: suspend () -> DayDraft? = { null },
    loadDay: suspend (Long) -> DayDraft? = { null },
    deleteDay: suspend (Long) -> Unit = {},
    medications: Flow<List<Medication>> = emptyFlow(),
    addMedication: suspend (Medication) -> Unit = {},
    archiveMedication: suspend (Medication) -> Unit = {},
    loadSuppressed: suspend () -> Boolean = { false },
    setSuppressed: suspend (Boolean) -> Unit = {},
    reminders: ReminderController? = null,
    healthConnect: HealthConnectController? = null,
    exportImport: ExportImportViewModel? = null,
    today: Long = LocalDate.now().toEpochDay(),
) {
    val stack = rememberTrackingBackStack()
    val days by recentDays.collectAsState(initial = emptyList())

    // Observe the deep-link flag ONLY inside this consented subtree: on set, push CheckIn + reset.
    LaunchedEffect(router.pendingCheckIn) {
        if (router.pendingCheckIn) {
            stack.push(TrackingRoute.CheckIn)
            router.pendingCheckIn = false
        }
    }

    // System back pops the in-Log stack only when nested; at LogHome it defers to the Phase-19
    // predictive-back-to-Log / exit rule (this handler does not register).
    BackHandler(enabled = stack.canPop) { stack.pop() }

    Box(modifier = modifier.fillMaxSize()) {
        when (val route = stack.current) {
            TrackingRoute.LogHome -> LogHomeScreen(
                recentDays = days,
                onCheckIn = { stack.push(TrackingRoute.CheckIn) },
                onEditDay = { epochDay -> stack.push(TrackingRoute.DayLog(epochDay)) },
                onOpenSettings = { stack.push(TrackingRoute.Settings) },
            )
            TrackingRoute.CheckIn -> DailyCheckInScreen(
                today = today,
                loadYesterday = loadYesterday,
                persistDay = persistDay,
                onDayLogged = onDayLogged,
                onDismiss = { stack.pop() },
                modifier = Modifier.testTag("log.route.checkIn"),
            )
            is TrackingRoute.DayLog -> DayLogEditorScreen(
                epochDay = route.epochDay,
                loadDay = loadDay,
                persistDay = persistDayLog, // the editor REPLACES the whole day (honest deselect)
                deleteDay = deleteDay,
                onDayLogged = onDayLogged,
                onDismiss = { stack.pop() },
            )
            TrackingRoute.Settings -> SettingsScreen(
                onWithdraw = onWithdraw,
                onBack = { stack.pop() },
                reminders = reminders,
                healthConnect = healthConnect,
                onConnectHealth = { stack.push(TrackingRoute.HealthImport) },
                exportImport = exportImport,
                onOpenExportImport = { stack.push(TrackingRoute.ExportImport) },
                medications = medications,
                onAddMedication = addMedication,
                onArchiveMedication = archiveMedication,
                loadSuppressed = loadSuppressed,
                onSetSuppressed = setSuppressed,
            )
            // The Health Connect connect/import screen — reachable ONLY via the Settings connect entry,
            // which is itself rendered only when a controller is threaded (Consented arm). Guarded on a
            // non-null controller so an unwired build can never construct the import surface.
            TrackingRoute.HealthImport -> healthConnect?.let { controller ->
                HealthImportHost(controller = controller, onClose = { stack.pop() })
            }
            // The encrypted export/import screen — reachable ONLY via the Settings "Your data" entry,
            // itself rendered only when the seam is threaded (Consented arm). Guarded on a non-null
            // ViewModel so an unwired build can never construct the export/import surface (T-23-10).
            TrackingRoute.ExportImport -> exportImport?.let { viewModel ->
                ExportImportHost(viewModel = viewModel, onClose = { stack.pop() })
            }
        }
    }
}
