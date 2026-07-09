package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's treatment / suppression mode as a first-class, timestamped record (port of iOS
 * `TreatmentState`, D-19).
 *
 * NOT day-scoped — it does NOT implement `DayScoped`; suppression is a standing state, not a per-day
 * signal, and survives a day delete. `modeRaw` stores the raw mode verbatim
 * ("naturallyCycling" | "suppressed" | "unknown"); the latest row's mode stamps `suppressed` onto
 * every DaySnapshot at the bridge. Non-null is spent only on `id`/`startedAtMillis`.
 */
@Entity
data class TreatmentState(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    val modeRaw: String? = null,
)
