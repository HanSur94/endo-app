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
}
