package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * A single, day-anchored bleeding observation (port of iOS `BleedingEvent`, DATA-01 / D-19).
 *
 * First-class and independent of any "cycle" — there is deliberately NO cycleDay/cyclePhase column.
 * `flowRaw` stores the `FlowIntensity` raw verbatim. `clots` is NULLABLE: `null` = not recorded,
 * NOT a recorded `false` (absence ≠ fact). Non-null is spent only on `id`/`epochDay`.
 */
@Entity
data class BleedingEvent(
    @PrimaryKey val id: String,
    override val epochDay: Long,
    val flowRaw: String? = null,
    val note: String? = null,
    val clots: Boolean? = null,
) : DayScoped
