package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * A single, day-anchored pain point on the body map (port of iOS `PainPoint`, SYMP-02).
 *
 * `regionRaw` stores the `BodyRegion` raw verbatim (unknown → dropped at the bridge, never crashed).
 * `severity` is NULLABLE: `null` = unspecified, NEVER "0 = healthy" (absence ≠ 0). Non-null is spent
 * only on `id`/`epochDay`.
 */
@Entity
data class PainPoint(
    @PrimaryKey val id: String,
    override val epochDay: Long,
    val regionRaw: String? = null,
    val severity: Int? = null,
) : DayScoped
