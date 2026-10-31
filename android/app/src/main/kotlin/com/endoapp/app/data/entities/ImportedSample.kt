package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * One imported Health Connect / Apple Health sample (port of iOS `ImportedSample`, DATA-05).
 *
 * PRIVACY BOUNDARY: only category-level imported values — never free-text/prose, never a
 * micronutrient breakdown. Additive: never mutates a manual-logging row.
 *
 * DEDUPE: `hcRecordId` renames the iOS `hkSampleUUID` for Phase 21 (Health Connect `metadata.id`),
 * carrying the same dedupe semantics, and gets a UNIQUE index (Room's freedom, unavailable under
 * CloudKit on iOS). The column is NULLABLE — many rows with a null key coexist (SQLite treats NULLs
 * as distinct under a unique index); Phase 21 fills it, and the service still upserts by `id` in code.
 *
 * Day-scoped: purged by the delete-day cascade. NOTE — this is where the Android port EXCEEDS iOS:
 * the iOS `deleteDay` omitted ImportedSample (a latent orphan); implementing [DayScoped] forces its
 * inclusion via the cascade completeness meta-test (Plan 03). All health-value columns are NULLABLE
 * (absence ≠ 0); non-null is spent only on `id`/`epochDay`/`importedAtMillis`.
 */
@Entity(indices = [Index(value = ["hcRecordId"], unique = true)])
data class ImportedSample(
    @PrimaryKey val id: String,
    val hcRecordId: String? = null,
    override val epochDay: Long,
    val domainRaw: String? = null,
    val flowRaw: String? = null,
    val sleepHours: Double? = null,
    val sleepQualityRaw: String? = null,
    val activityTypeRaw: String? = null,
    val activityMinutes: Double? = null,
    val ateThatDay: Boolean? = null,
    val importedAtMillis: Long,
) : DayScoped
