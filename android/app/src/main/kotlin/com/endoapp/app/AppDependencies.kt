package com.endoapp.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.DataStoreConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.reminders.ReminderNotifier
import com.endoapp.app.reminders.ReminderScheduler
import com.endoapp.app.reminders.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
}
