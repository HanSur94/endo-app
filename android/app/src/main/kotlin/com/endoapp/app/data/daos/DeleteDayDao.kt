package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.endoapp.app.data.DayScoped
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import kotlin.reflect.KClass

/**
 * The GDPR Art. 17 delete-day cascade (Plan 03) — one [Transaction] that purges EVERY day-scoped
 * row for a calendar day so a deleted day leaves ZERO local orphans.
 *
 * ## Born complete — includes ImportedSample
 * The iOS `Repository.deleteDay` (Repository.swift 282–295) deletes only SymptomReading, PainPoint,
 * BleedingEvent, FactorReading and DayEntry — it OMITS `ImportedSample`, a latent orphan of the same
 * class the iOS 04-02 fix closed for FactorReading. This Android port EXCEEDS iOS by including
 * `ImportedSample`; the omission is NOT copied.
 *
 * ## One transaction, not N repo calls
 * The whole cascade runs inside a single [Transaction] so a crash mid-cascade cannot leave a day
 * half-deleted (RESEARCH Pitfall 9 — the iOS bug was *coverage*, Room adds an *atomicity* trap).
 * The repository delegates to [deleteDay]; it never issues N sequential deletes of its own.
 *
 * ## CachedInsight is invalidated, not day-keyed
 * [clearCachedInsights] wipes the derived insight cache wholesale (the fingerprint recompute
 * rebuilds it). CachedInsight is NOT a day-scoped entity, so it is not part of [COVERED_DAY_SCOPED_ENTITIES];
 * it is cleared here purely because a day delete changes the underlying data the cache summarises.
 *
 * ## Single canonical coverage source
 * [COVERED_DAY_SCOPED_ENTITIES] declares exactly which [DayScoped] entities the cascade purges.
 * `CascadeCompletenessTest` derives the real set of [DayScoped] entities from the compiled entity
 * classes and asserts it equals this set — so dropping `ImportedSample` here, or adding a new
 * day-scoped entity without cascading it, turns the meta-test RED. The cascade can never silently
 * fall behind the schema.
 *
 * Definitions ([com.endoapp.app.data.entities.Medication], [com.endoapp.app.data.entities.TreatmentState])
 * and dismissals ([com.endoapp.app.data.entities.DismissedInsight]) are NOT day-scoped and MUST
 * survive a day delete — they are deliberately absent from every delete below.
 */
@Dao
interface DeleteDayDao {

    @Query("DELETE FROM DayEntry WHERE epochDay = :epochDay")
    suspend fun deleteDayEntries(epochDay: Long)

    @Query("DELETE FROM SymptomReading WHERE epochDay = :epochDay")
    suspend fun deleteSymptoms(epochDay: Long)

    @Query("DELETE FROM PainPoint WHERE epochDay = :epochDay")
    suspend fun deletePainPoints(epochDay: Long)

    @Query("DELETE FROM BleedingEvent WHERE epochDay = :epochDay")
    suspend fun deleteBleeding(epochDay: Long)

    @Query("DELETE FROM FactorReading WHERE epochDay = :epochDay")
    suspend fun deleteFactors(epochDay: Long)

    @Query("DELETE FROM ImportedSample WHERE epochDay = :epochDay")
    suspend fun deleteImported(epochDay: Long)

    /** Cache invalidation — wipes ALL cached insights; the fingerprint recompute rebuilds them. */
    @Query("DELETE FROM CachedInsight")
    suspend fun clearCachedInsights()

    /**
     * Purge every day-scoped row for [epochDay] (all 6 types incl. ImportedSample) and invalidate the
     * insight cache, atomically. Definitions and dismissals are untouched.
     */
    @Transaction
    suspend fun deleteDay(epochDay: Long) {
        deleteDayEntries(epochDay)
        deleteSymptoms(epochDay)
        deletePainPoints(epochDay)
        deleteBleeding(epochDay)
        deleteFactors(epochDay)
        deleteImported(epochDay)
        clearCachedInsights()
    }

    companion object {
        /**
         * The single canonical declaration of which [DayScoped] entities the cascade covers.
         * `CascadeCompletenessTest` pins this equal to the real set of [DayScoped] entities, so it
         * cannot silently diverge from the schema. Includes `ImportedSample` — the inclusion that
         * closes the latent iOS `deleteDay` omission.
         */
        val COVERED_DAY_SCOPED_ENTITIES: Set<KClass<out DayScoped>> = setOf(
            DayEntry::class,
            SymptomReading::class,
            PainPoint::class,
            BleedingEvent::class,
            FactorReading::class,
            ImportedSample::class,
        )
    }
}
