package com.endoapp.app.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room [TypeConverter]s for this database.
 *
 * The entities deliberately store day identity as a plain `epochDay: Long`
 * (`LocalDate.toEpochDay()`) and timestamps as a plain `*Millis: Long` (`Instant.toEpochMilli()`),
 * with the `LocalDate`/`Instant` <-> `Long` conversion performed at the repository / SnapshotProjector
 * bridge (Plan 04) — NOT inside the entity columns. So no column currently REQUIRES a converter.
 *
 * These converters are provided-and-ready for any future column that stores a `LocalDate`/`Instant`
 * directly; Room ignores a registered converter whose target type no column uses, so wiring this
 * class into `@TypeConverters` is harmless today and avoids a churny build-file change later.
 *
 * Day bucketing itself (instant -> day) is NEVER done by epoch-millis division here — that
 * reintroduces the DST/timezone bug `Calendar.startOfDay` avoided on iOS (RESEARCH Pitfall 5). The
 * zone-aware `instant.atZone(zone).toLocalDate()` conversion lives at the bridge.
 */
class Converters {

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? =
        epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? =
        date?.toEpochDay()

    @TypeConverter
    fun epochMillisToInstant(millis: Long?): Instant? =
        millis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? =
        instant?.toEpochMilli()
}
