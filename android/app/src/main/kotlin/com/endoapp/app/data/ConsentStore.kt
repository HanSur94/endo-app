package com.endoapp.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The consent persistence seam (ONBD-01, D-22) the gate reads — the injectable/testable interface,
 * ported from the iOS `ConsentStore` protocol.
 *
 * The flag lives OUTSIDE the health data store (see [DataStoreConsentStore]); this interface is the
 * mockable seam the repository and (Phase 19) `AppGate` depend on. Keeping it an interface is what
 * lets the fast JVM `RepositoryConsentTest` drive consent with a plain settable [StateFlow], no
 * database and no DataStore file required.
 */
interface ConsentStore {

    /** The current consent state (ONBD-01). Seeded [ConsentState.Unknown] until the first read. */
    val state: StateFlow<ConsentState>

    /** Record explicit consent now (D-02). Persists the flag + a grant timestamp. */
    suspend fun grant()

    /** Withdraw consent (D-04), returning the app to the gated state; clears the flag + timestamp. */
    suspend fun withdraw()
}

/**
 * Preferences-DataStore-backed [ConsentStore]. The consent flag + grant timestamp live in a
 * `consent.preferences_pb` file, DELIBERATELY OUTSIDE the health data store — a consent record must
 * never round-trip through, or be coupled to, the very data it gates (ARCHITECTURE Pattern 5;
 * mirrors the iOS UserDefaults split, and threat T-18-08). This class imports NO persistence-DB type.
 *
 * `state` maps the stored flag to [ConsentState] and is shared as a [StateFlow] seeded
 * [ConsentState.Unknown] (`SharingStarted.Eagerly`) — the async gap before DataStore's first
 * emission is the third state, and it fails closed.
 *
 * Only the consent flag + timestamp are stored here; reminder preferences (Phase 20) are
 * deliberately absent.
 */
class DataStoreConsentStore(
    private val dataStore: DataStore<Preferences>,
    appScope: CoroutineScope,
) : ConsentStore {

    override val state: StateFlow<ConsentState> = dataStore.data
        .map { prefs -> prefs.toConsentState() }
        .stateIn(appScope, SharingStarted.Eagerly, ConsentState.Unknown)

    override suspend fun grant() {
        dataStore.edit { prefs ->
            prefs[CONSENTED] = true
            prefs[GRANTED_AT_MILLIS] = System.currentTimeMillis()
        }
    }

    override suspend fun withdraw() {
        dataStore.edit { prefs ->
            prefs.remove(CONSENTED)
            prefs.remove(GRANTED_AT_MILLIS)
        }
    }

    private fun Preferences.toConsentState(): ConsentState =
        if (this[CONSENTED] == true) {
            ConsentState.Consented(this[GRANTED_AT_MILLIS] ?: 0L)
        } else {
            ConsentState.NotConsented
        }

    companion object {
        /** DataStore preferences file name (`consent.preferences_pb`). */
        const val FILE_NAME = "consent"

        val CONSENTED = booleanPreferencesKey("consented")
        val GRANTED_AT_MILLIS = longPreferencesKey("consent_granted_at_millis")
    }
}
