package com.endoapp.app.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState

/**
 * The encrypted-import TARGET (APRT-02) — a single-[Transaction] bulk REPLACE-upsert over all 9
 * exported entity types (mirrors [DeleteDayDao]'s multi-table default-method pattern).
 *
 * ## Idempotent by the UUID primary key (T-23-06c)
 * Each insert is `@Insert(onConflict = OnConflictStrategy.REPLACE)` keyed on the `id: String` (UUID)
 * primary key, so re-importing the SAME envelope REPLACEs every row with byte-identical content — no
 * duplicates, no change. REPLACE (delete-then-insert under SQLite `ON CONFLICT REPLACE`) also resolves
 * the [ImportedSample] `hcRecordId` UNIQUE index conflict, so an imported sample sharing an
 * `hcRecordId` with an existing (differently-`id`'d) row is handled safely too.
 *
 * ## Atomic (T-23-06b)
 * [importAll] runs all 9 inserts inside ONE [Transaction] — all-or-nothing. A crash mid-import can
 * never leave a partial, half-imported dataset.
 *
 * ## Additive — Room stays v1
 * This DAO declares NO new `@Entity` and no schema-changing query; it only inserts over the existing
 * 10-entity v1 schema. `schemas/1.json` is unchanged. `CachedInsight` is deliberately NOT imported —
 * it is a derived cache the coordinator regenerates on the next recompute (excluded per the
 * [com.endoapp.app.export.ExportEnvelope] contract).
 */
@Dao
interface ImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayEntries(rows: List<DayEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptomReadings(rows: List<SymptomReading>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPainPoints(rows: List<PainPoint>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBleedingEvents(rows: List<BleedingEvent>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFactorReadings(rows: List<FactorReading>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(rows: List<Medication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatmentStates(rows: List<TreatmentState>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedSamples(rows: List<ImportedSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedInsights(rows: List<DismissedInsight>)

    /**
     * REPLACE-upsert every exported entity list in ONE transaction (atomic + idempotent-by-UUID). The
     * consent-gated [com.endoapp.app.data.EndoRepository.importEnvelope] is the only caller.
     */
    @Transaction
    suspend fun importAll(
        dayEntries: List<DayEntry>,
        symptomReadings: List<SymptomReading>,
        painPoints: List<PainPoint>,
        bleedingEvents: List<BleedingEvent>,
        factorReadings: List<FactorReading>,
        medications: List<Medication>,
        treatmentStates: List<TreatmentState>,
        importedSamples: List<ImportedSample>,
        dismissedInsights: List<DismissedInsight>,
    ) {
        insertDayEntries(dayEntries)
        insertSymptomReadings(symptomReadings)
        insertPainPoints(painPoints)
        insertBleedingEvents(bleedingEvents)
        insertFactorReadings(factorReadings)
        insertMedications(medications)
        insertTreatmentStates(treatmentStates)
        insertImportedSamples(importedSamples)
        insertDismissedInsights(dismissedInsights)
    }
}
