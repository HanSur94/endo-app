package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.TreatmentState
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the two non-day-scoped definition entities: [Medication] (soft-archived, never
 * hard-deleted) and [TreatmentState] (the standing suppression state).
 *
 * Neither is day-scoped, so this DAO has NO `deleteForDay` — a day delete must never touch a saved
 * medication or the treatment state. `observeActive` filters out soft-archived meds for the saved
 * list; `latestTreatment` feeds the `suppressed` stamp the bridge applies to every DaySnapshot.
 * Every accessor is `suspend`/`Flow`; `allowMainThreadQueries()` is NEVER used.
 */
@Dao
interface MedicationDao {

    // --- Medication (definition; soft-archive, never hard-delete) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(medication: Medication)

    /** Live saved-medication list (Phase 19), active (non-archived) only, newest first. */
    @Query("SELECT * FROM Medication WHERE isArchived IS NULL OR isArchived = 0 ORDER BY createdAtMillis DESC")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM Medication")
    suspend fun allMedications(): List<Medication>

    // --- TreatmentState (standing state; latest wins) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTreatment(state: TreatmentState)

    /** The current treatment/suppression state — the most recently started row. */
    @Query("SELECT * FROM TreatmentState ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun latestTreatment(): TreatmentState?

    @Query("SELECT * FROM TreatmentState")
    suspend fun allTreatments(): List<TreatmentState>
}
