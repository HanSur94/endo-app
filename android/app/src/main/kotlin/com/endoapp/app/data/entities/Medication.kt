package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved medication DEFINITION (port of iOS `Medication`, LIFE-03 / CHCK-02).
 *
 * NOT day-scoped — it deliberately does NOT implement `DayScoped`, so a day delete never touches it.
 * The per-day "taken" log lives in a `.medication` [FactorReading] whose `valueRaw` is this med's `id`.
 * Soft-archive via `isArchived` (never hard-delete) so prior "taken" history keyed by `id` stays
 * valid. `isArchived` is NULLABLE (`null`/`false` = active). Non-null is spent only on
 * `id`/`createdAtMillis`.
 */
@Entity
data class Medication(
    @PrimaryKey val id: String,
    val name: String? = null,
    val note: String? = null,
    val createdAtMillis: Long,
    val isArchived: Boolean? = null,
)
