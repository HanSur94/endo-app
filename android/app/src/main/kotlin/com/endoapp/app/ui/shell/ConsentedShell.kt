package com.endoapp.app.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.endoapp.app.CheckInRouter
import com.endoapp.app.ExploreScatterRouter
import com.endoapp.app.content.PlaceholderCopy
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.export.ExportImportViewModel
import com.endoapp.app.report.ui.ReportBuilderViewModel
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.insights.InsightsCoordinator
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.designsystem.BloomNavigationBar
import com.endoapp.app.designsystem.BloomTabIcons
import com.endoapp.app.ui.explore.ExploreScreen
import com.endoapp.app.ui.insights.InsightsScreen
import com.endoapp.app.ui.tracking.DayDraft
import com.endoapp.insights.DaySnapshot
import com.endoapp.app.ui.tracking.DayLogRows
import com.endoapp.app.ui.tracking.LogTabHost
import java.time.LocalDate

/**
 * The consented 3-tab shell — composed ONLY inside [com.endoapp.app.AppGate]'s `Consented` arm, so
 * the tab surface is unconstructible pre-consent by construction (ASHL-01 / threat T-19-01).
 *
 * A Material 3 [Scaffold] with the [BloomNavigationBar] as its `bottomBar`, over a [ConsentedTab]
 * `rememberSaveable` selection (NOT a `NavHost` — no navigation library). Each tab renders a Bloom
 * [TabPlaceholder] card marking the phase that fills it (Log → 20, Insights/Explore → 22).
 *
 * **Edge-to-edge (mandatory at targetSdk 36):** `Scaffold` consumes the system-bar `WindowInsets`
 * and hands the body `innerPadding`; the `bottomBar` `NavigationBar` handles the `navigationBars`
 * inset itself. The placeholder body pads from `innerPadding`, so content never draws under the
 * status/nav bars.
 *
 * **Predictive back:** [androidx.activity.compose.PredictiveBackHandler] is enabled ONLY when a
 * non-Log tab is selected — a back gesture animates the return to the start tab (Log). On Log the
 * handler is disabled, so back falls through to the system default (exit the app). Back is never
 * intercepted generally (predictive back stays default-on).
 *
 * The [router] deep-link flag is observed ONLY inside the consented Log subtree — now by
 * [LogTabHost], not here — so a notification/deep link lands on the gate while unconsented, and the
 * consented host owns the push to Daily Check-In.
 */
@Composable
fun ConsentedShell(
    router: CheckInRouter,
    onWithdraw: () -> Unit,
    recentDays: Flow<List<DayEntry>> = emptyFlow(),
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
    exploreSnapshots: suspend () -> List<DaySnapshot> = { emptyList() },
    reminders: ReminderController? = null,
    healthConnect: HealthConnectController? = null,
    exportImport: ExportImportViewModel? = null,
    report: ReportBuilderViewModel? = null,
    insightsCoordinator: InsightsCoordinator? = null,
    exploreScatterRouter: ExploreScatterRouter = ExploreScatterRouter(),
    today: Long = LocalDate.now().toEpochDay(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(ConsentedTab.Log) }

    // Predictive-back-to-Log: intercept back ONLY off the start tab, so a back gesture from
    // Insights/Explore animates the return to Log, while back from Log falls through to the system
    // default (exit). The decision is the pure [tabBackTarget] — the same seam the shell test pins.
    TabBackHandler(enabled = tabBackTarget(selectedTab) != null) {
        tabBackTarget(selectedTab)?.let { selectedTab = it }
    }

    Scaffold(
        bottomBar = {
            BloomNavigationBar(
                tabs = ConsentedTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                testTag = { it.testTag },
                label = { it.label },
                icon = { iconFor(it) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                ConsentedTab.Log -> LogTabHost(
                    recentDays = recentDays,
                    router = router,
                    onWithdraw = onWithdraw,
                    onDayLogged = onDayLogged,
                    persistDay = persistDay,
                    persistDayLog = persistDayLog,
                    loadYesterday = loadYesterday,
                    loadDay = loadDay,
                    deleteDay = deleteDay,
                    medications = medications,
                    addMedication = addMedication,
                    archiveMedication = archiveMedication,
                    loadSuppressed = loadSuppressed,
                    setSuppressed = setSuppressed,
                    reminders = reminders,
                    healthConnect = healthConnect,
                    exportImport = exportImport,
                    report = report,
                    today = today,
                )
                ConsentedTab.Insights ->
                    // The Phase-22 gated dashboard (AINS-01) replaces the placeholder once the coordinator
                    // is wired. Falls back to the placeholder only when unwired (e.g. the shell unit test),
                    // so the coordinator-absent path never crashes.
                    if (insightsCoordinator != null) {
                        InsightsScreen(
                            coordinator = insightsCoordinator,
                            loadSuppressed = loadSuppressed,
                            // Set the router payload FIRST, THEN switch to Explore — the set-before-select
                            // sequence (no race): Explore consumes the typed axes on appear/change and clears
                            // it. The axes are TYPED (never re-derived from copy downstream).
                            onOpenScatter = { pending ->
                                exploreScatterRouter.pendingScatter = pending
                                selectedTab = ConsentedTab.Explore
                            },
                        )
                    } else {
                        TabPlaceholder(
                            eyebrow = PlaceholderCopy.INSIGHTS_EYEBROW,
                            heading = PlaceholderCopy.INSIGHTS_HEADING,
                            body = PlaceholderCopy.INSIGHTS_BODY,
                            testTag = "tab.insights.body",
                        )
                    }
                ConsentedTab.Explore ->
                    // The Phase-22 Explore surface (AINS-04) replaces the placeholder — the descriptive
                    // per-scale overlay + event lanes over the consent-gated `daySnapshots()` seam, still
                    // suppression-gated (hides bleeding). Composed ONLY here inside the Consented arm.
                    ExploreScreen(
                        loadSnapshots = exploreSnapshots,
                        loadSuppressed = loadSuppressed,
                        today = LocalDate.ofEpochDay(today),
                        // The cross-tab scatter deep-link payload — consumed on appear/change → Scatter mode
                        // prefilled with the typed axes, then cleared (re-trigger safe).
                        scatterRouter = exploreScatterRouter,
                    )
            }
        }
    }
}

/** The bundled Bloom glyph for each tab (design-system icons resolved in the shell layer). */
private fun iconFor(tab: ConsentedTab): ImageVector = when (tab) {
    ConsentedTab.Log -> BloomTabIcons.logGlyph
    ConsentedTab.Insights -> BloomTabIcons.insightsGlyph
    ConsentedTab.Explore -> BloomTabIcons.exploreGlyph
}

/**
 * The pure tab-back decision — the M3 norm "back from a secondary tab returns to the start tab":
 * from Insights/Explore, back targets [ConsentedTab.Log]; from Log it returns `null` (do NOT
 * intercept — back falls through to exit the app). Extracted as a pure function so the shell test
 * pins the behavior deterministically on the JVM without simulating the device back gesture.
 */
internal fun tabBackTarget(current: ConsentedTab): ConsentedTab? =
    if (current == ConsentedTab.Log) null else ConsentedTab.Log

/**
 * A thin, predictive-back-friendly wrapper over [PredictiveBackHandler]: when [enabled], a back
 * gesture's progress flow is collected (the system renders the back-preview animation) and, on
 * commit, [onBack] runs; a cancelled gesture leaves the current tab untouched. When disabled the
 * handler does not register, so back stays default-on (predictive-back-to-exit on the start tab).
 */
@Composable
private fun TabBackHandler(enabled: Boolean, onBack: () -> Unit) {
    PredictiveBackHandler(enabled = enabled) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { /* back-preview frames animated by the system */ }
            onBack()
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            // Gesture cancelled — remain on the current tab. Rethrow to honor structured concurrency.
            throw cancelled
        }
    }
}
