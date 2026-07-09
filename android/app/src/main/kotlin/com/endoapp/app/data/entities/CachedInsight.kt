package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One durable cache row per gated `Insight` (port of iOS `CachedInsight`, D-02/D-14).
 *
 * PRIVACY BOUNDARY: `payload` carries ONLY the engine's gated `Insight` (serialized JSON) — NEVER a
 * `DaySnapshot`, NEVER note prose. The flat `signalKey`/`kindRaw`/`windowRaw` columns are the same
 * non-identifying category labels DismissedInsight stores.
 *
 * NOT day-scoped — it does NOT implement `DayScoped`. It is a derived cache with REPLACE-ALL
 * semantics on recompute (invalidated by `fingerprint`, not by day), so it is not part of the
 * day-keyed cascade; the repository clears it wholesale on recompute. `payload` maps the iOS
 * `Data?` to a nullable `String?` JSON column (kotlinx-serialization at the boundary). Non-null is
 * spent only on `id`/`computedAtMillis`.
 */
@Entity
data class CachedInsight(
    @PrimaryKey val id: String,
    val signalKey: String? = null,
    val kindRaw: String? = null,
    val windowRaw: String? = null,
    val payload: String? = null,
    val computedAtMillis: Long,
    val fingerprint: String? = null,
)
