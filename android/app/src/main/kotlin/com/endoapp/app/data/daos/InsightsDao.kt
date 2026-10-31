package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endoapp.app.data.entities.CachedInsight
import com.endoapp.app.data.entities.DismissedInsight
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the two insight-related entities: [CachedInsight] (derived cache, REPLACE-ALL on recompute)
 * and [DismissedInsight] (append-only category dismissals).
 *
 * Neither is day-scoped. The cache is invalidated wholesale on recompute ([clearCached] then
 * [insertCached]), never day-keyed. Dismissals are append-only and keyed by `signalKey`
 * (duplicates collapse on read), so a day delete never removes one. Every accessor is `suspend`/
 * `Flow`; `allowMainThreadQueries()` is NEVER used.
 */
@Dao
interface InsightsDao {

    // --- CachedInsight (derived cache; REPLACE-ALL on recompute) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCached(insights: List<CachedInsight>)

    @Query("DELETE FROM CachedInsight")
    suspend fun clearCached()

    @Query("SELECT * FROM CachedInsight")
    suspend fun allCached(): List<CachedInsight>

    /** Live gated-insight cache for the dashboard (Phase 22). */
    @Query("SELECT * FROM CachedInsight")
    fun observeCached(): Flow<List<CachedInsight>>

    // --- DismissedInsight (append-only; keyed by signalKey) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissed(dismissed: DismissedInsight)

    @Query("SELECT * FROM DismissedInsight")
    suspend fun allDismissed(): List<DismissedInsight>

    @Query("SELECT signalKey FROM DismissedInsight WHERE signalKey IS NOT NULL")
    suspend fun dismissedSignalKeys(): List<String>
}
