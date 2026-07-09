// The per-domain Health Connect changes-token cursor store (Phase 21, AHC-03 / T-21-02e / -02f).
//
// DEVICE-RUNTIME STATE, NOT HEALTH DATA: the changes token is an opaque provider cursor that lets the
// next sync resume incrementally instead of re-reading everything. It lives in its OWN Preferences
// DataStore file — OUTSIDE the Room health database (mirroring the consent + reminder DataStore split,
// ARCHITECTURE Pattern 5 / threat T-18-08), so it is never coupled to, synced with, or exported
// alongside the health data it merely indexes. This is the Android analog of the iOS `HealthAnchorStore`.
//
// SEAM INVARIANT: this file imports NO Room / entity / DB type (grep-clean like `ConsentStore` /
// `ReminderSettings`). The only app type it touches is the pure [HealthDomain] enum (a key selector).
package com.endoapp.app.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.endoapp.app.insightsbridge.HealthDomain
import kotlinx.coroutines.flow.first

/**
 * A thin, testable facade over a Preferences [DataStore] holding ONE opaque changes-token per
 * [HealthDomain]. The [HealthImportCoordinator] reads the token before a differential read and, only
 * AFTER a successful persist, advances it — so a mid-sync failure never loses data (the next run
 * re-reads from the last good cursor). On token expiry the coordinator [clear]s the domain and the
 * next read seeds a fresh cursor.
 *
 * Constructed OUTSIDE the health database (its own [FILE_NAME] file); nothing here imports a
 * Room/entity type.
 */
class ChangesTokenStore(private val dataStore: DataStore<Preferences>) {

    /** The persisted cursor for [domain], or `null` when unseeded (⇒ a seed read mints a fresh one). */
    suspend fun token(domain: HealthDomain): String? =
        dataStore.data.first()[keyFor(domain)]

    /**
     * Persist (or, when [token] is `null`, remove) the cursor for [domain]. Called by the coordinator
     * ONLY after the domain's upserts + deletions have been committed — the token never advances ahead
     * of the data it indexes.
     */
    suspend fun setToken(domain: HealthDomain, token: String?) {
        dataStore.edit { prefs ->
            if (token == null) prefs.remove(keyFor(domain)) else prefs[keyFor(domain)] = token
        }
    }

    /** Drop [domain]'s cursor (token expiry) so the next read seeds a fresh one — never crash. */
    suspend fun clear(domain: HealthDomain) {
        dataStore.edit { it.remove(keyFor(domain)) }
    }

    private fun keyFor(domain: HealthDomain) = stringPreferencesKey("changes_token_${domain.raw}")

    companion object {
        /** The changes-token DataStore file — SEPARATE from the consent store, reminders, and health DB. */
        const val FILE_NAME: String = "health_changes_tokens"
    }
}
