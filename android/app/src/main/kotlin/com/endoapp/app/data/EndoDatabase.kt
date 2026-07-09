package com.endoapp.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.endoapp.app.data.daos.DayEntryDao
import com.endoapp.app.data.daos.DeleteDayDao
import com.endoapp.app.data.daos.InsightsDao
import com.endoapp.app.data.daos.MedicationDao
import com.endoapp.app.data.daos.SymptomDao
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.CachedInsight
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState

/**
 * The local Room database holding all user health data (10 entities).
 *
 * MIGRATION DISCIPLINE — `exportSchema = true` writes the versioned schema to
 * `android/app/schemas/<db>/1.json`, which is COMMITTED as a source artifact so `MigrationTestHelper`
 * always has a v1 baseline and future auto-migrations remain possible (RESEARCH Pitfall 3). The v1
 * schema is IRREVERSIBLE and born correct here.
 *
 * The builder deliberately does NOT call any destructive-migration fallback — a schema bump must
 * never silently wipe user health history (threat T-18-01). `DestructiveMigrationBanTest` structurally
 * enforces this by scanning these main sources.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        // Day-scoped (purged by the Plan-03 delete-day cascade)
        DayEntry::class,
        SymptomReading::class,
        PainPoint::class,
        BleedingEvent::class,
        FactorReading::class,
        ImportedSample::class,
        // Definitions / cache (survive a day delete)
        Medication::class,
        TreatmentState::class,
        CachedInsight::class,
        DismissedInsight::class,
    ],
)
@TypeConverters(Converters::class)
abstract class EndoDatabase : RoomDatabase() {

    abstract fun dayEntryDao(): DayEntryDao
    abstract fun symptomDao(): SymptomDao
    abstract fun medicationDao(): MedicationDao
    abstract fun insightsDao(): InsightsDao

    /** The GDPR Art. 17 delete-day cascade (single `@Transaction`, all 6 day-scoped types). */
    abstract fun deleteDayDao(): DeleteDayDao

    companion object {
        /** On-disk database file name (excluded from Android Auto Backup in Plan 06). */
        const val DB_NAME = "endo.db"

        /**
         * Build the database. NOTE: no destructive-migration fallback of any kind — every schema
         * bump from here on MUST provide a real migration; wiping health history is never acceptable.
         * (The exact banned method name lives ONLY in DestructiveMigrationBanTest, so this source
         * stays scannable.)
         */
        fun build(context: Context): EndoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EndoDatabase::class.java,
                DB_NAME,
            ).build()
    }
}
