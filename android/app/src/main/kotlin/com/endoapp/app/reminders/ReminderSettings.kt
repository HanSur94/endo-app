package com.endoapp.app.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The plain-value reminder preferences snapshot (NO Android types) — the reminder's entire persisted
 * state. Times are minutes-of-day (`0..1439`); quiet hours default to a sensible 22:00–07:00 window but
 * are OFF until the user turns them on.
 *
 * `enabled` defaults OFF (the reminder is opt-in, JIT-permissioned). This lives in its OWN DataStore,
 * OUTSIDE the Room health database (mirroring the consent DataStore) — reminder prefs are not health
 * data and must never be coupled to it or synced.
 */
data class ReminderPrefs(
    val enabled: Boolean = false,
    val times: List<Int> = emptyList(),
    val quietHoursEnabled: Boolean = false,
    val quietStart: Int = DEFAULT_QUIET_START,
    val quietEnd: Int = DEFAULT_QUIET_END,
) {
    companion object {
        /** 22:00 as minutes-of-day — the default quiet-hours window START. */
        const val DEFAULT_QUIET_START: Int = 22 * 60

        /** 07:00 as minutes-of-day — the default quiet-hours window END (crosses midnight). */
        const val DEFAULT_QUIET_END: Int = 7 * 60

        /** 20:00 as minutes-of-day — the single default reminder time seeded on first enable. */
        const val DEFAULT_TIME: Int = 20 * 60
    }
}

/**
 * The reminder-prefs store — a thin, testable facade over a Preferences [DataStore] held in its OWN
 * file ([FILE_NAME]), constructed OUTSIDE the health database (ARCHITECTURE Pattern 5 / threat T-18-08:
 * a preference that is not health data must never be coupled to the store it is unrelated to).
 *
 * Reads expose a cold [prefs] Flow (for the Settings UI) and a one-shot [current] snapshot (for the
 * worker at fire time). Writes are per-field so a Settings edit touches only what changed. Nothing here
 * imports a notification/alarm API — that stays in [ReminderNotifier]/[ReminderScheduler].
 */
class ReminderSettings(private val dataStore: DataStore<Preferences>) {

    /** The live reminder prefs (cold Flow) the Settings reminders section collects. */
    val prefs: Flow<ReminderPrefs> = dataStore.data.map(::toPrefs)

    /** A one-shot snapshot — the worker reads this at delivery time to decide whether to fire. */
    suspend fun current(): ReminderPrefs = toPrefs(dataStore.data.first())

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setTimes(times: List<Int>) {
        dataStore.edit { it[KEY_TIMES] = times.sorted().joinToString(",") }
    }

    suspend fun setQuietHours(enabled: Boolean, start: Int, end: Int) {
        dataStore.edit {
            it[KEY_QUIET_ENABLED] = enabled
            it[KEY_QUIET_START] = start
            it[KEY_QUIET_END] = end
        }
    }

    private fun toPrefs(p: Preferences): ReminderPrefs = ReminderPrefs(
        enabled = p[KEY_ENABLED] ?: false,
        times = (p[KEY_TIMES] ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .sorted(),
        quietHoursEnabled = p[KEY_QUIET_ENABLED] ?: false,
        quietStart = p[KEY_QUIET_START] ?: ReminderPrefs.DEFAULT_QUIET_START,
        quietEnd = p[KEY_QUIET_END] ?: ReminderPrefs.DEFAULT_QUIET_END,
    )

    companion object {
        /** The reminder-prefs DataStore file — SEPARATE from the consent store and the health DB. */
        const val FILE_NAME: String = "reminders"

        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_TIMES = stringPreferencesKey("times")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val KEY_QUIET_START = intPreferencesKey("quiet_start")
        private val KEY_QUIET_END = intPreferencesKey("quiet_end")
    }
}
