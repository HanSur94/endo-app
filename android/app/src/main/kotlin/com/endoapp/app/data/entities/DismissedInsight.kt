package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable record that the user dismissed the insight identified by `signalKey` (port of iOS
 * `DismissedInsight`, INST-04 / D-09).
 *
 * PRIVACY BOUNDARY: carries ONLY a non-identifying, engine-generated category `signalKey` + a
 * timestamp — no prose, no severities.
 *
 * NOT day-scoped — it does NOT implement `DayScoped`. Dismissals are keyed by `signalKey`, not by
 * day, and are append-only (duplicates collapse on read via a `Set`), so a day delete never removes
 * a dismissal. Non-null is spent only on `id`/`dismissedAtMillis`.
 */
@Entity
data class DismissedInsight(
    @PrimaryKey val id: String,
    val signalKey: String? = null,
    val dismissedAtMillis: Long,
)
