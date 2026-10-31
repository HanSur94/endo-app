package com.endoapp.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.health.connect.client.HealthConnectClient
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.DataStoreConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.export.ExportImportViewModel
import com.endoapp.app.export.SafDocumentIo
import com.endoapp.app.health.ChangesTokenStore
import com.endoapp.app.health.HealthAvailability
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.health.HealthConnectReader
import com.endoapp.app.health.HealthImportCoordinator
import com.endoapp.app.health.RealHealthConnectController
import com.endoapp.app.insights.InsightsCoordinator
import com.endoapp.app.report.ReportPdfRenderer
import com.endoapp.app.report.ui.ReportBuilderViewModel
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.reminders.ReminderNotifier
import com.endoapp.app.reminders.ReminderScheduler
import com.endoapp.app.reminders.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.LocalDate
import java.util.UUID

/**
 * The manual composition root — the Android mirror of the iOS `AppDependencies`. A plain class (NO DI
 * framework, NO ViewModel): it owns the process-lifetime [applicationScope] and constructs the
 * long-lived collaborators the shell reads.
 *
 * The consent flag lives in its OWN Preferences DataStore (`consent.preferences_pb`), built here
 * OUTSIDE the Room health database (ARCHITECTURE Pattern 5 / threat T-18-08) — a consent record must
 * never be coupled to the data it gates. [consentStore] is the Phase-18 [DataStoreConsentStore] over
 * that file; [checkInRouter] is the non-sensitive deep-link seam the shell consumes.
 */
class AppDependencies(context: Context) {

    /** Process-lifetime scope backing DataStore + the consent StateFlow (mirrors the iOS app scope). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val consentDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = {
            context.applicationContext.preferencesDataStoreFile(DataStoreConsentStore.FILE_NAME)
        },
    )

    /** The 3-state consent seam the [AppGate] routes on (Phase-18 DataStore-backed). */
    val consentStore: ConsentStore = DataStoreConsentStore(consentDataStore, applicationScope)

    /** The deep-link / notification entry flag, observed only inside the consented shell. */
    val checkInRouter: CheckInRouter = CheckInRouter()

    /**
     * The cross-tab scatter deep-link router (SCAT-04) — the `.correlation` card affordance sets its
     * ephemeral [ExploreScatterRouter.pendingScatter] and the Explore Scatter mode consumes it. A plain
     * seam like [checkInRouter]; observed ONLY inside [AppGate]'s Consented arm. Carries only public catalog
     * series ids — never health data, never persisted.
     */
    val exploreScatterRouter: ExploreScatterRouter = ExploreScatterRouter()

    /**
     * The on-device Room health database (Phase-18). Built lazily (no query runs until first access),
     * so constructing it here does no disk I/O on the main thread.
     */
    val database: EndoDatabase = EndoDatabase.build(context)

    /**
     * The consent-gated facade over the health store — the ONLY path the UI reads/writes health data
     * through. Every method fails closed pre-consent (Phase-18 [EndoRepository.checkConsented]); it is
     * threaded into the shell ONLY inside [AppGate]'s `Consented` arm, so a logged day genuinely
     * persists and reloads in the running app while consent-by-construction is preserved.
     */
    val repository: EndoRepository = EndoRepository.from(consentStore, database)

    // --- Insights coordinator (Phase 22 Plan 01) ---
    //
    // The app-scoped, off-main insight recompute + fingerprint-cache brain (the iOS `InsightsCoordinator`
    // analog). Constructed over the live [repository] seams; every dependency is an injected closure so
    // it stays unit-testable with no store or clock. It is NOT threaded into the shell here — Plan 04
    // threads [InsightsCoordinator.state] into the Insights tab. Imports NO notification/alarm/WorkManager
    // API, so the NotificationSeparation invariant (CONTEXT §5) stays green.

    /**
     * The dashboard's insight data path (AINS-01): reads Room-projected snapshots + dismissed keys, runs
     * the Phase-17 engine OFF the main thread (`withContext(Dispatchers.Default)`), fingerprint-caches
     * (replace-all), loads the cache instantly, and excludes dismissed keys — publishing a [StateFlow].
     * `persist` is wrapped best-effort so a cache-write failure never drops fresh insights; `writeDismissed`
     * appends a NON-destructive [DismissedInsight] (never deletes logged data).
     */
    val insightsCoordinator: InsightsCoordinator = InsightsCoordinator(
        loadSnapshots = repository::daySnapshots,
        loadDismissed = repository::dismissedSignalKeys,
        loadCache = repository::loadCachedInsights,
        persist = { insights, computedAtMillis, fingerprint ->
            runCatching { repository.replaceCachedInsights(insights, computedAtMillis, fingerprint) }
        },
        writeDismissed = { signalKey ->
            repository.dismissInsight(
                DismissedInsight(
                    id = UUID.randomUUID().toString(),
                    signalKey = signalKey,
                    dismissedAtMillis = System.currentTimeMillis(),
                ),
            )
        },
        now = { LocalDate.now() },
        nowMillis = System::currentTimeMillis,
        scope = applicationScope,
    )

    // --- Daily check-in reminder (Phase-20 Plan 05) ---
    //
    // The reminder prefs live in their OWN Preferences DataStore (built here OUTSIDE the Room health
    // DB, mirroring the consent store) — reminder prefs are not health data. [reminderScheduler] and
    // [reminderNotifier] take only a Context, so NO androidx.work / notification import leaks into this
    // composition root (the no-flare-notification separation invariant, CONTEXT §5): every scheduling
    // and notification API stays inside com.endoapp.app.reminders.

    private val reminderDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = {
            context.applicationContext.preferencesDataStoreFile(ReminderSettings.FILE_NAME)
        },
    )

    /** The reminder-prefs store (enabled / times / quiet hours) — read by the worker + the Settings UI. */
    val reminderSettings: ReminderSettings = ReminderSettings(reminderDataStore)

    /** Posts the single generic notification through the one channel — read by the reminder worker. */
    val reminderNotifier: ReminderNotifier = ReminderNotifier(context.applicationContext)

    /** Schedules the INEXACT periodic reminder via WorkManager (resolved from Context internally). */
    private val reminderScheduler: ReminderScheduler = ReminderScheduler(context.applicationContext)

    /** The reminder facade the Settings reminders section drives (enable/disable/time/quiet hours). */
    val reminderController: ReminderController =
        ReminderController(reminderSettings, reminderScheduler, reminderNotifier)

    // --- Health Connect import (Phase 21 Plan 05) ---
    //
    // The read-only Health Connect stack, mirroring the reminder stack's posture: the changes-token
    // cursor lives in its OWN Preferences DataStore (built here OUTSIDE the Room health DB — device
    // runtime state, not health data). The reader is availability-gated + NULLABLE: `getOrCreate` is
    // called ONLY when getSdkStatus == AVAILABLE (a blind construction crashes on API 26–33; T-21-05b),
    // so an UNAVAILABLE device never crashes and the connect entry stays inert. Import is a FOREGROUND
    // coroutine (Plan 05 triggers it from the connect screen + a manual refresh) — NO androidx.work,
    // NO notification import leaks here; the reminders package stays the SOLE notification owner.

    /** The application context, captured for the per-session availability + client resolution. */
    private val appContext: Context = context.applicationContext

    private val changesTokenDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = {
            appContext.preferencesDataStoreFile(ChangesTokenStore.FILE_NAME)
        },
    )

    /** The per-domain changes-token cursor store — SEPARATE from the health DB, consent, and reminders. */
    private val changesTokenStore: ChangesTokenStore = ChangesTokenStore(changesTokenDataStore)

    /**
     * The Health Connect connect/import facade the Settings connect entry drives — bound into the shell
     * ONLY inside [AppGate]'s `Consented` arm, so the connect/import surface is unconstructible
     * pre-consent (T-21-05a). Its reader is resolved per session behind the availability gate: null
     * unless the provider is AVAILABLE, so the composition root does NO Health Connect client work on an
     * unavailable device.
     */
    val healthConnectController: HealthConnectController = RealHealthConnectController(
        availabilityProvider = { HealthAvailability.availability(appContext) },
        readerProvider = {
            // getOrCreate ONLY when AVAILABLE — never blindly (T-21-05b). Null ⇒ the controller does
            // no client work and reports the honest empty state.
            if (HealthAvailability.availability(appContext) == HealthAvailability.AVAILABLE) {
                HealthConnectReader(
                    client = HealthConnectClient.getOrCreate(appContext),
                    ownPackageName = appContext.packageName,
                )
            } else {
                null
            }
        },
        coordinatorFor = { reader ->
            HealthImportCoordinator(reader, repository, changesTokenStore, consentStore)
        },
        hasImportedData = { repository.hasImportedData() },
    )

    // --- Encrypted export/import (Phase 23 Plan 05) ---
    //
    // The foreground-coroutine export/import brain, constructed over the consent-gated repository's
    // snapshot/import seams (23-02) + the real SAF DocumentIo (23-03; `openOutputStream(uri, "wt")` +
    // read-back). Threaded into the shell ONLY inside [AppGate]'s `Consented` arm (T-23-10), so the
    // portability surface is unconstructible pre-consent. The codec's ~600k KDF runs off-main inside the
    // ViewModel; this composition root imports NO androidx.work / notification API (the separation
    // invariant) — the reminders package stays the SOLE notification owner. Zero storage permission (SAF).

    /**
     * The export/import ViewModel the Settings "Your data" surface drives — bound into the shell ONLY
     * inside [AppGate]'s `Consented` arm, so export (which reads all health data) and import (which
     * writes it) are both unconstructible pre-consent by construction (T-23-10).
     */
    val exportImportViewModel: ExportImportViewModel = ExportImportViewModel(
        exportSnapshot = repository::exportSnapshot,
        importEnvelope = repository::importEnvelope,
        documentIo = SafDocumentIo(appContext.contentResolver),
    )

    // --- PDF report builder (Phase 24 Plan 05) ---
    //
    // The doctor-ready report builder brain, constructed over the consent-gated repository's projection
    // reads (24-01 daySnapshots / daySnapshotSources, latest-treatment suppressed, dismissed keys), the
    // LOCKED :insights engine, and the Plan-03 ReportPdfRenderer writing the vector PDF into the app-private
    // cacheDir/reports/ (the Plan-04 ReportShareHost then shares it via FileProvider + ACTION_SEND).
    // Threaded into the shell ONLY inside AppGate's Consented arm (T-24-11), so the report surface — which
    // reads all health data to assemble the PDF — is unconstructible pre-consent. This composition root
    // imports NO androidx.work / notification / networking API (the report path stays network-free — the
    // reminders package stays the SOLE notification owner). Zero new dependency (PdfDocument is first-party).

    /**
     * The report builder ViewModel the Settings "Export report" surface drives — bound into the shell ONLY
     * inside [AppGate]'s `Consented` arm, so the report generation (which reads every health snapshot) is
     * unconstructible pre-consent by construction (T-24-11).
     */
    val reportBuilderViewModel: ReportBuilderViewModel = ReportBuilderViewModel(
        loadSnapshots = repository::daySnapshots,
        loadSources = repository::daySnapshotSources,
        loadSuppressed = { repository.latestTreatment()?.modeRaw == "suppressed" },
        loadDismissed = repository::dismissedSignalKeys,
        render = { model -> ReportPdfRenderer.render(model, appContext.cacheDir) },
    )
}
