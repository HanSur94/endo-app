package com.endoapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.TreatmentState
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.domain.CycleMath
import com.endoapp.app.export.ExportImportViewModel
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.insights.InsightsCoordinator
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.report.ui.ReportBuilderViewModel
import com.endoapp.app.ui.onboarding.OnboardingScreen
import com.endoapp.app.ui.shell.ConsentedShell
import com.endoapp.app.ui.tracking.LogPersistence
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The consent-by-construction routing surface — the ENTIRE app is this plain three-way `when`-switch
 * on [ConsentState] (threat T-19-01, Elevation of Privilege). There is NO navigation library: the
 * health/tab composables ([ConsentedShell] and its tabs) are referenced ONLY inside the `Consented`
 * arm, so they are not constructible while `Unknown` or `NotConsented`. This is a structural
 * guarantee, not a runtime guard condition — pinned red-capably by `AppGateConsentTest`.
 *
 * - [ConsentState.Unknown] → splash (DataStore has not emitted yet): FAIL CLOSED — never flash the
 *   gated UI.
 * - [ConsentState.NotConsented] → onboarding/consent. The [router] is deliberately NOT observed here.
 * - [ConsentState.Consented] → the tab shell, the only place the [router] flag is read.
 */
@Composable
fun AppGate(
    consentStore: ConsentStore,
    router: CheckInRouter,
    repository: EndoRepository? = null,
    reminders: ReminderController? = null,
    healthConnect: HealthConnectController? = null,
    exportImport: ExportImportViewModel? = null,
    report: ReportBuilderViewModel? = null,
    insightsCoordinator: InsightsCoordinator? = null,
    exploreScatterRouter: ExploreScatterRouter = ExploreScatterRouter(),
    writeScope: CoroutineScope? = null,
) {
    val state by consentStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    suspend fun detached(block: suspend () -> Unit) = runDetached(writeScope, block)

    when (state) {
        ConsentState.Unknown -> SplashScreen()

        ConsentState.NotConsented -> OnboardingScreen(
            onConsent = { scope.launch { consentStore.grant() } },
            onDecline = { /* fail-closed no-op: nothing is stored, the user stays on the gate */ },
        )

        is ConsentState.Consented -> {
            // The persistence seams are BOUND HERE — inside the Consented arm ONLY — so the running
            // app genuinely persists/reloads a day through the consent-gated repository while the
            // consent-by-construction guarantee holds (the repository is referenced nowhere else).
            val today = LocalDate.now().toEpochDay()
            ConsentedShell(
                router = router,
                onWithdraw = { scope.launch { consentStore.withdraw() } },
                recentDays = repository?.observeDayEntries() ?: emptyFlow(),
                persistDay = { rows -> repository?.let { detached { LogPersistence.upsertDay(it, rows) } } },
                persistDayLog = { rows -> repository?.let { detached { LogPersistence.replaceDay(it, rows) } } },
                loadYesterday = { repository?.let { LogPersistence.loadDay(it, today - 1) } },
                loadDay = { day -> repository?.let { LogPersistence.loadDay(it, day) } },
                // The Home cycle card: the same pure CycleMath the report uses, over all flow days.
                loadCycleSummary = {
                    repository?.let { repo ->
                        CycleMath.summarize(
                            bleedingDays = repo.daySnapshots().filter { it.flow != null }.map { it.day },
                            isSuppressed = repo.latestTreatment()?.modeRaw == "suppressed",
                            today = LocalDate.now(),
                        )
                    }
                },
                deleteDay = { day -> repository?.let { detached { it.deleteDay(day) } } },
                // Settings med + treatment seams — bound HERE (Consented arm only). Add + soft-archive
                // both go through upsertMedication (the archive med already carries isArchived=true);
                // cycle-off writes a fresh timestamped TreatmentState (latest wins).
                medications = repository?.observeMedications() ?: emptyFlow(),
                addMedication = { med -> repository?.let { detached { it.upsertMedication(med) } } },
                archiveMedication = { med -> repository?.let { detached { it.upsertMedication(med) } } },
                loadSuppressed = { repository?.latestTreatment()?.modeRaw == "suppressed" },
                // The Explore descriptive-read seam — bound HERE (Consented arm ONLY), so the raw
                // day-snapshot surface the Explore lanes render is unconstructible pre-consent by
                // construction (consent-by-construction, T-19-01). Ephemeral selection never persists.
                exploreSnapshots = { repository?.daySnapshots() ?: emptyList() },
                setSuppressed = { on ->
                    repository?.upsertTreatment(
                        TreatmentState(
                            id = UUID.randomUUID().toString(),
                            startedAtMillis = System.currentTimeMillis(),
                            modeRaw = if (on) "suppressed" else "naturallyCycling",
                        ),
                    )
                },
                reminders = reminders,
                // The Health Connect connect/import seam — bound HERE (Consented arm ONLY), so the
                // connect/import surface is unconstructible pre-consent by construction (T-21-05a).
                healthConnect = healthConnect,
                // The encrypted export/import seam — bound HERE (Consented arm ONLY), mirroring the
                // healthConnect/insightsCoordinator posture, so the portability surface is
                // unconstructible pre-consent by construction (T-23-10). Referenced in NO other arm.
                exportImport = exportImport,
                // The PDF report builder seam — bound HERE (Consented arm ONLY), mirroring the
                // exportImport/healthConnect posture, so the report surface (which reads all health data
                // to assemble the PDF) is unconstructible pre-consent by construction (T-24-11).
                // Referenced in NO other arm.
                report = report,
                // The insights coordinator — bound HERE (Consented arm ONLY), mirroring the reminders /
                // healthConnect posture, so the dashboard's health-derived surface is unconstructible
                // pre-consent by construction (consent-by-construction, T-19-01).
                insightsCoordinator = insightsCoordinator,
                // The cross-tab scatter deep-link router — bound HERE (Consented arm ONLY), so the
                // dashboard→Explore scatter handoff is unconstructible pre-consent (T-19-01). Ephemeral,
                // carries only public catalog series ids, never persisted.
                exploreScatterRouter = exploreScatterRouter,
                today = today,
            )
        }
    }
}

/**
 * The `Unknown`-state splash — a full-bleed [BloomColors.canvas] surface with a `Loading` a11y label.
 * It exists so the gate never briefly composes the gated shell before consent is known (fail closed).
 */
@Composable
private fun SplashScreen() {
    val colors = LocalBloomColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .testTag("gate.splash")
            .semantics { contentDescription = "Loading" },
    )
}

/**
 * Screens fire their writes from a composition scope and dismiss immediately, which cancels that scope.
 * Run [block] in the process-lifetime [writeScope] instead, so leaving the screen can never cut a save
 * short (the Check-In used to persist only its DayEntry anchor); the caller merely awaits the result.
 * Without a [writeScope] (tests / previews) the block runs inline.
 */
internal suspend fun runDetached(writeScope: CoroutineScope?, block: suspend () -> Unit) {
    if (writeScope == null) block() else writeScope.async { block() }.await()
}
