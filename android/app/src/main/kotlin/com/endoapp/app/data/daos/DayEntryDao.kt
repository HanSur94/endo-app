package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endoapp.app.data.entities.DayEntry
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the per-day anchor record ([DayEntry]).
 *
 * Every accessor is `suspend` (one-shot) or `Flow` (live UI) — `allowMainThreadQueries()` is NEVER
 * used. Upsert is id-keyed (REPLACE) so re-writing the same day's note is idempotent, matching the
 * app-generated-UUID identity that keeps Phase-23 export/import round-trips stable.
 *
 * `deleteForDay` is a leaf the Plan-03 delete-day cascade calls inside its single `@Transaction`;
 * it is NOT invoked as a standalone repository operation.
 */
@Dao
interface DayEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DayEntry)

    @Query("SELECT * FROM DayEntry WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): List<DayEntry>

    @Query("SELECT * FROM DayEntry")
    suspend fun all(): List<DayEntry>

    /** Live day history for the Home screen (Phase 19), newest first. */
    @Query("SELECT * FROM DayEntry ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DayEntry>>

    @Query("DELETE FROM DayEntry WHERE epochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)
}
