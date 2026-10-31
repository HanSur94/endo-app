package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * A single, day-anchored symptom reading (port of iOS `SymptomReading`, SYMP-01).
 *
 * `typeRaw` stores the `SymptomType` raw string verbatim (decoded defensively at the bridge, never
 * fabricated as OTHER). `severity` is NULLABLE: `null` = unspecified, NEVER "0 = healthy"
 * (absence is not zero — no non-null default, no nil coalescing). Non-null is spent only on
 * `id`/`epochDay`.
 */
@Entity
data class SymptomReading(
    @PrimaryKey val id: String,
    override val epochDay: Long,
    val typeRaw: String? = null,
    val severity: Int? = null,
    val note: String? = null,
) : DayScoped
