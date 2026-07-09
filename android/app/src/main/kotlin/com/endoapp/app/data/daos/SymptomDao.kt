package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading

/**
 * DAO for the five day-scoped event entities: [SymptomReading], [PainPoint], [BleedingEvent],
 * [FactorReading], and [ImportedSample].
 *
 * Provides, per entity: an id-keyed REPLACE upsert (idempotent, app-generated UUID identity), the
 * `WHERE epochDay = :epochDay` fetch the SnapshotProjector needs (Plan 04), the `all()` reads the
 * projector unions across days, and the `deleteForDay` leaves the Plan-03 cascade calls inside its
 * single `@Transaction`. All accessors are `suspend`; `allowMainThreadQueries()` is NEVER used.
 *
 * The five per-day deletes are grouped here deliberately: the cascade's completeness meta-test keys
 * off the [com.endoapp.app.data.DayScoped] marker, and all five day-scoped event types own a
 * `deleteForDay` here so the cascade can never silently fall behind the schema.
 */
@Dao
interface SymptomDao {

    // --- SymptomReading ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSymptom(reading: SymptomReading)

    @Query("SELECT * FROM SymptomReading WHERE epochDay = :epochDay")
    suspend fun symptomsForDay(epochDay: Long): List<SymptomReading>

    @Query("SELECT * FROM SymptomReading")
    suspend fun allSymptoms(): List<SymptomReading>

    @Query("DELETE FROM SymptomReading WHERE epochDay = :epochDay")
    suspend fun deleteSymptomsForDay(epochDay: Long)

    // --- PainPoint ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPain(point: PainPoint)

    @Query("SELECT * FROM PainPoint WHERE epochDay = :epochDay")
    suspend fun painForDay(epochDay: Long): List<PainPoint>

    @Query("SELECT * FROM PainPoint")
    suspend fun allPain(): List<PainPoint>

    @Query("DELETE FROM PainPoint WHERE epochDay = :epochDay")
    suspend fun deletePainForDay(epochDay: Long)

    // --- BleedingEvent ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBleeding(event: BleedingEvent)

    @Query("SELECT * FROM BleedingEvent WHERE epochDay = :epochDay")
    suspend fun bleedingForDay(epochDay: Long): List<BleedingEvent>

    @Query("SELECT * FROM BleedingEvent")
    suspend fun allBleeding(): List<BleedingEvent>

    @Query("DELETE FROM BleedingEvent WHERE epochDay = :epochDay")
    suspend fun deleteBleedingForDay(epochDay: Long)

    // --- FactorReading ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFactor(reading: FactorReading)

    @Query("SELECT * FROM FactorReading WHERE epochDay = :epochDay")
    suspend fun factorsForDay(epochDay: Long): List<FactorReading>

    @Query("SELECT * FROM FactorReading")
    suspend fun allFactors(): List<FactorReading>

    @Query("DELETE FROM FactorReading WHERE epochDay = :epochDay")
    suspend fun deleteFactorsForDay(epochDay: Long)

    // --- ImportedSample (day-scoped; INCLUDED in the cascade — closes the iOS orphan gap) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImported(sample: ImportedSample)

    @Query("SELECT * FROM ImportedSample WHERE epochDay = :epochDay")
    suspend fun importedForDay(epochDay: Long): List<ImportedSample>

    @Query("SELECT * FROM ImportedSample")
    suspend fun allImported(): List<ImportedSample>

    @Query("DELETE FROM ImportedSample WHERE epochDay = :epochDay")
    suspend fun deleteImportedForDay(epochDay: Long)

    /**
     * Propagate a Health Connect deletion: remove the imported row whose `hcRecordId` matches (the HC
     * `metadata.id`). A non-matching id is a no-op. This is the deletion half of the differential
     * changes-token sync (Plan 03) — an `UpsertionChange` re-imports idempotently by `hcRecordId`, a
     * `DeletionChange` removes here, so an HC deletion propagates to the day model on re-sync (T-21-02).
     */
    @Query("DELETE FROM ImportedSample WHERE hcRecordId = :hcRecordId")
    suspend fun deleteImportedByHcRecordId(hcRecordId: String)
}
