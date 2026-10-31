package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * The ONE unified, day-anchored lifestyle-factor record (port of iOS `FactorReading`, LIFE-01/02/03).
 *
 * One model discriminated by `kindRaw` (`FactorKind`: food/activity/sleep/medication/stress/mood/
 * other), with generic payload columns per the kind→column mapping:
 *   food       -> valueRaw = FoodTag.raw (one row per tag); note = free-text (one extra .food row)
 *   activity   -> valueRaw = ActivityType.raw; amount = minutes; level = intensity 0/1/2
 *   sleep      -> valueRaw = SleepQuality.raw (optional); amount = hours
 *   medication -> valueRaw = saved-med id.uuidString, or "prn"
 *   stress     -> level = 0…10
 *   mood       -> level = 1…5
 *
 * HONESTY: every payload column is NULLABLE — absence ≠ 0. A day with no `.stress` row has NO stress
 * value; a `.stress` row with `level == 0` means stress was logged as zero. Never coerce nil → 0.
 * Non-null is spent only on `id`/`epochDay`.
 */
@Entity
data class FactorReading(
    @PrimaryKey val id: String,
    override val epochDay: Long,
    val kindRaw: String? = null,
    val valueRaw: String? = null,
    val amount: Double? = null,
    val level: Int? = null,
    val note: String? = null,
) : DayScoped
