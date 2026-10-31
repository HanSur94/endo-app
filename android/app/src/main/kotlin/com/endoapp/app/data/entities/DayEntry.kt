package com.endoapp.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.endoapp.app.data.DayScoped

/**
 * The per-day anchor record (port of iOS `DayEntry`). Holds the day's optional free-text note.
 *
 * Day-scoped: purged by the delete-day cascade. Only `id` and `epochDay` spend non-null — the note
 * is nullable (absent ≠ empty). The note's PROSE never crosses the SnapshotProjector bridge; only
 * its presence (`hasNote`) does (Plan 04).
 */
@Entity
data class DayEntry(
    @PrimaryKey val id: String,
    override val epochDay: Long,
    val note: String? = null,
) : DayScoped
