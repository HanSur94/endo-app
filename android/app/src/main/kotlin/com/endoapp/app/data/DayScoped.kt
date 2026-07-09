package com.endoapp.app.data

/**
 * Marker for entities anchored to a single calendar day.
 *
 * This is the single canonical source the delete-day cascade (Plan 03) and its completeness
 * meta-test key off: an entity that implements [DayScoped] MUST be purged when its day is deleted
 * (GDPR Art. 17 — the iOS `deleteDay` orphan bug closed for FactorReading, and here extended to
 * ImportedSample). The reflection meta-test reflects over [com.endoapp.app.data.EndoDatabase]'s
 * entity list, computes the day-scoped set from this marker, and fails if a day-scoped entity is
 * ever added without being cascaded — so the cascade can never silently fall behind the schema.
 *
 * Non-day-scoped rows (Medication, TreatmentState, CachedInsight, DismissedInsight) deliberately do
 * NOT implement this marker: definitions and dismissals survive a day delete; the cache is
 * invalidated on its own fingerprint, not by day.
 */
interface DayScoped {
    /** The calendar day this row belongs to, as `LocalDate.toEpochDay()`. */
    val epochDay: Long
}
